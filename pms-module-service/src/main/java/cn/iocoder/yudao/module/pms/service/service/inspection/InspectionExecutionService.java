package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogSemanticFacts;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceLogParsingApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionResultEvaluator;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine;
import cn.iocoder.yudao.module.pms.service.service.inspection.log.*;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleQueryService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

@Service
@RequiredArgsConstructor
public class InspectionExecutionService {
    private final InspectionAccessService access;
    private final InspectionTaskService tasks;
    private final InspectionAttemptMapper attempts;
    private final InspectionTaskDeviceMapper devices;
    private final InspectionTaskRuleSnapshotMapper snapshots;
    private final InspectionExecutionMapper executions;
    private final InspectionParsePartMapper parts;
    private final InspectionDetectionResultMapper results;
    private final InspectionRuleQueryService rules;
    private final DeviceLogParsingApi parser;
    private final NativeGeneratedFileApi generatedFiles;
    private final FileEvidenceApi fileEvidence;
    private final AuthenticatedFileContentApi fileContent;
    private final InspectionResultEvaluator evaluator = new InspectionResultEvaluator();

    @Builder public record Upload(InspectionRequestContext context, Long taskId, Long attemptId, Long deviceId,
                                   String fileName, byte[] content, String commandHint) { }
    public record Refresh(InspectionRequestContext context, Long taskId, Long executionId) { }
    public record Finish(InspectionRequestContext context, Long taskId) { }
    public record Abandon(InspectionRequestContext context, Long taskId, Long executionId, String reason) { }
    public record CommandList(InspectionRequestContext context, Long taskId, Long deviceId) { }
    public record Ingest(InspectionRequestContext context, Long taskId, Long executionId, byte[] collectionLog) { }
    public record CommandView(Long ruleRevisionId, String detectionId, String inspectionItem, Integer revisionNo,
                              String stableCommandKey, String content, Integer executionOrder, Integer timeoutSeconds,
                              Boolean continueOnTimeout, String expectedRegex, String expectedThreshold, Integer sequenceNo) { }
    private record Scope(InspectionRequestContext context, InspectionTaskDO task, InspectionAttemptDO attempt,
                         InspectionTaskDeviceDO device) { }
    private record Normalized(Scope scope, InspectionExecutionDO execution, List<InspectionParserDocuments.Block> blocks,
                              Set<String> serials, String fileName) { }

    public List<CommandView> commands(CommandList query) {
        var task = access.task(new InspectionAccessService.TaskAccess(query.context(), query.taskId(),
                "pms:inspection-task:query", false, false));
        require(task.getCurrentAttemptId() != null, "请先开始执行并冻结规则版本");
        List<CommandView> ordered = new ArrayList<>();
        for (var row : frozen(new InspectionTaskRows(query.context().tenantId(), task.getId())).stream()
                .filter(r -> Objects.equals(r.getAttemptId(), task.getCurrentAttemptId()) && Objects.equals(r.getDeviceId(), query.deviceId()))
                .sorted(Comparator.comparing(InspectionTaskRuleSnapshotDO::getId)).toList()) {
            var rule = rules.detail(row.getRuleRevisionId());
            for (var command : rule.getCommands().stream().sorted(Comparator.comparingInt(c -> c.executionOrder())).toList()) {
                for (var line : command.commandContent().replace('\r', '\n').lines().map(String::strip).filter(s -> !s.isEmpty()).toList())
                    ordered.add(new CommandView(rule.getRevisionId(), rule.getDetectionId(), rule.getInspectionItem(), rule.getRevisionNo(),
                            command.stableCommandKey(), line, command.executionOrder(), command.timeoutSeconds(), command.continueOnTimeout(),
                            rule.getExpectedResultRegex(), rule.getThresholdOperator() + rule.getThresholdValue() + rule.getThresholdUnit(), ordered.size() + 1));
            }
        }
        return List.copyOf(ordered);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary upload(Upload command) {
        var task = writable(command.context(), command.taskId());
        var scope = scope(new Scope(command.context(), task, attempts.selectById(command.attemptId()),
                device(new InspectionTaskRows(task.getTenantId(), task.getId()), command.deviceId())));
        require("OFFLINE".equals(scope.attempt().getMode()), "当前尝试不是离线巡检，请显式新建离线执行尝试");
        require(command.content() != null && command.content().length > 0 && command.content().length <= 52_428_800,
                "日志文件不能为空且不得超过50MB");
        require(command.fileName() != null && command.fileName().length() <= 300, "日志文件名过长");
        String originalDigest = DigestUtil.sha256Hex(command.content());
        var existing = executions.selectForTask(new InspectionTaskRows(task.getTenantId(), task.getId())).stream()
                .filter(row -> Objects.equals(row.getAttemptId(), scope.attempt().getId()) && Objects.equals(row.getDeviceId(), command.deviceId())
                        && !"CANCELLED".equals(row.getStatusCode())
                        && Objects.equals(row.getOriginalFileSha256(), originalDigest)
                        && Objects.equals(row.getCommandHint(), command.commandHint())).findFirst();
        if (existing.isPresent()) return InspectionTaskService.summary(task);
        List<InspectionLogArchiveReader.LogEntry> entries;
        try { entries = new InspectionLogArchiveReader().read(new InspectionLogArchiveReader.Input(command.fileName(), command.content())); }
        catch (IllegalArgumentException invalid) { throw new InspectionException(InspectionException.Reason.FILE_INVALID, invalid.getMessage()); }
        var availableCommands = commands(new CommandList(command.context(), task.getId(), command.deviceId()));
        if (command.commandHint() != null && !command.commandHint().isBlank()) {
            require(availableCommands.stream().anyMatch(c -> InspectionParserDocuments.normalizeCommand(c.content())
                    .equals(InspectionParserDocuments.normalizeCommand(command.commandHint()))), "命令提示必须来自本设备冻结的巡检命令");
        }
        List<InspectionParserDocuments.Block> blocks = new ArrayList<>(); Set<String> serials = new LinkedHashSet<>();
        try {
            for (var entry : entries) {
                var normalized = parser.normalize(new DeviceLogParsingApi.LogInput(task.getProjectId().toString(), entry.text(), command.commandHint()));
                require(normalized != null && normalized.commandBlocks() != null, "日志脱敏服务没有返回可用内容");
                if (normalized.serialNumbers() != null) normalized.serialNumbers().stream().filter(s -> s != null && !s.isBlank()).forEach(serials::add);
                for (var block : normalized.commandBlocks()) blocks.add(InspectionParserDocuments.block(block, blocks.size() + 1));
                require(blocks.size() <= 4096, "日志命令块超过本次上传的资源限制，请按设备拆分文件");
            }
        } catch (DeviceLogParsingApi.InvalidInputException invalid) {
            throw new InspectionException(InspectionException.Reason.FILE_INVALID, "日志格式无效或超过解析限制，请检查文件后重新上传");
        } catch (DeviceLogParsingApi.ParsingException unavailable) {
            throw new InspectionException(InspectionException.Reason.DEPENDENCY_UNAVAILABLE, "日志脱敏服务不可用，原文件未存储，请稍后重试");
        }
        require(!blocks.isEmpty(), "日志没有可读取的命令输出");
        var execution = new InspectionExecutionDO(); execution.setTenantId(task.getTenantId()); execution.setVersion(1L);
        execution.setTaskId(task.getId()); execution.setAttemptId(scope.attempt().getId()); execution.setDeviceId(command.deviceId());
        execution.setPurposeCode("OFFLINE"); execution.setStatusCode("PENDING"); execution.setOriginalFileSha256(originalDigest);
        execution.setCommandHint(command.commandHint());
        execution.setSubmittedBy(command.context().actorId()); execution.setSubmittedAt(LocalDateTime.now());
        execution.setRecognizedSerial(serials.size() == 1 ? serials.iterator().next() : null);
        executions.insert(execution);
        storeNormalized(new Normalized(scope, execution, blocks, serials, command.fileName()));
        tasks.save(new InspectionTaskService.Save(command.context(), task, "OFFLINE_LOG_UPLOAD", task.getStatusCode(),
                "登记离线日志，执行记录" + execution.getId()));
        return InspectionTaskService.summary(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary ingestCollection(Ingest command) {
        var task = writable(command.context(), command.taskId());
        var row = executions.selectById(command.executionId());
        require(row != null && Objects.equals(row.getTenantId(), task.getTenantId()) && Objects.equals(row.getTaskId(), task.getId())
                && Objects.equals(row.getAttemptId(), task.getCurrentAttemptId()) && "COLLECT".equals(row.getPurposeCode())
                && row.getCollectionResultVersion() != null && row.getCollectionFileVersionId() != null,
                "采集结果未与当前巡检尝试绑定");
        if (row.getArtifactId() != null) return InspectionTaskService.summary(task);
        require(command.collectionLog() != null && command.collectionLog().length > 0 && command.collectionLog().length <= 20 * 1024 * 1024,
                "采集日志内容无效");
        var scope = scope(new Scope(command.context(), task, attempts.selectById(row.getAttemptId()),
                device(new InspectionTaskRows(task.getTenantId(), task.getId()), row.getDeviceId())));
        require("ONLINE".equals(scope.attempt().getMode()), "采集结果不是在线巡检尝试");
        DeviceLogParsingApi.NormalizedLog normalized;
        try { normalized = parser.normalize(new DeviceLogParsingApi.LogInput(task.getProjectId().toString(),
                InspectionParserDocuments.collectionInput(command.collectionLog()), null)); }
        catch (DeviceLogParsingApi.InvalidInputException invalid) {
            throw new InspectionException(InspectionException.Reason.FILE_INVALID, "采集日志内容无效或超过解析限制，请核对采集输出");
        }
        catch (DeviceLogParsingApi.ParsingException unavailable) {
            throw new InspectionException(InspectionException.Reason.DEPENDENCY_UNAVAILABLE, "采集日志解析服务暂不可用，可稍后继续刷新");
        }
        require(normalized != null && normalized.commandBlocks() != null && !normalized.commandBlocks().isEmpty(), "采集日志缺少命令输出");
        List<InspectionParserDocuments.Block> blocks = new ArrayList<>();
        normalized.commandBlocks().stream().sorted(Comparator.comparingInt(DeviceLogParsingApi.LogBlock::commandIndex))
                .forEach(block -> blocks.add(InspectionParserDocuments.block(block, block.commandIndex())));
        require(blocks.size() <= 4096, "采集命令块超过解析资源限制");
        Set<String> serials = new LinkedHashSet<>();
        if (normalized.serialNumbers() != null) normalized.serialNumbers().stream().filter(s -> s != null && !s.isBlank()).forEach(serials::add);
        row.setOriginalFileSha256(DigestUtil.sha256Hex(command.collectionLog())); row.setStatusCode("PENDING");
        row.setRecognizedSerial(serials.size() == 1 ? serials.iterator().next() : null);
        storeNormalized(new Normalized(scope, row, blocks, serials, "collection-" + row.getCollectionTaskId() + ".json"));
        tasks.save(new InspectionTaskService.Save(command.context(), task, "COLLECTION_LOG_IMPORTED", task.getStatusCode(),
                "登记已绑定的在线采集日志，执行记录" + row.getId()));
        return InspectionTaskService.summary(task);
    }

    private void storeNormalized(Normalized input) {
        var scope = input.scope(); var task = scope.task(); var execution = input.execution();
        var blocks = input.blocks(); var serials = input.serials();
        byte[] safeContent = InspectionParserDocuments.encode(blocks);
        require(safeContent.length <= 52_428_800, "脱敏日志内容超过50MB，请拆分后上传");
        // Only DAC-redacted content crosses the platform storage boundary. The original bytes never enter audit/idempotency data.
        var stored = generatedFiles.create(new NativeGeneratedFileCommand(task.getTenantId(), scope.context().actorId(),
                "inspection-log-" + execution.getId(), "SRV", "INSPECTION_TASK", task.getId(), task.getVersion(),
                "LOG", "INSPECTION_LOG", "inspection-" + execution.getId() + ".json", "application/json", safeContent));
        var reference = fileEvidence.inspectDocument(task.getTenantId(), stored.referenceId());
        require(reference != null && reference.available(), "日志文件引用不可用");
        execution.setArtifactId(stored.artifactId()); execution.setFileVersionNo(stored.versionNo());
        execution.setFileReferenceKey(reference.referenceKey()); execution.setFileSha256(stored.sha256());
        execution.setFileName(input.fileName());
        if (serials.size() > 1 || !serials.isEmpty() && !serials.contains(scope.device().getSerialNumber())) {
            execution.setStatusCode("FAILED"); execution.setFailureCode("DEVICE_SERIAL_MISMATCH");
            execution.setFailureMessage("日志中识别的设备序列号与所选设备不一致，请核对来源后重新执行或上传");
            execution.setCompletedAt(LocalDateTime.now());
        } else {
            int partNo = 0;
            for (var segment : InspectionParserDocuments.parts(blocks)) {
                var row = new InspectionParsePartDO(); row.setTenantId(task.getTenantId()); row.setVersion(1L);
                row.setTaskId(task.getId()); row.setExecutionId(execution.getId()); row.setPartNo(++partNo);
                row.setFirstCommandIndex(segment.firstIndex()); row.setLastCommandIndex(segment.lastIndex());
                row.setRequestId(UUID.randomUUID().toString()); row.setStatusCode(segment.oversized() ? "FAILED" : "PENDING");
                if (segment.oversized()) { row.setFailureCode("COMMAND_OUTPUT_TOO_LARGE"); row.setCompletedAt(LocalDateTime.now()); }
                parts.insert(row);
            }
        }
        executions.updateById(execution);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary refresh(Refresh command) {
        var task = writable(command.context(), command.taskId());
        var execution = executions.selectById(command.executionId());
        require(execution != null && Objects.equals(execution.getTenantId(), task.getTenantId())
                && Objects.equals(execution.getTaskId(), task.getId()) && Objects.equals(execution.getAttemptId(), task.getCurrentAttemptId()),
                "执行记录不属于当前尝试");
        if (Set.of("COMPLETE", "PARTIAL", "FAILED", "CANCELLED").contains(execution.getStatusCode())) return InspectionTaskService.summary(task);
        require(Set.of("OFFLINE", "COLLECT").contains(execution.getPurposeCode()) && execution.getArtifactId() != null,
                "请先登记离线日志或接收在线采集日志");
        var document = InspectionParserDocuments.decode(fileContent.read(new AuthenticatedFileContentApi.ReadCommand(
                new FileArtifactVersionQuery(execution.getArtifactId(), execution.getFileVersionNo(), "SRV", "INSPECTION_TASK",
                        task.getId().toString(), "LOG", execution.getFileReferenceKey(), FileActionCodes.DOWNLOAD), 52_428_800L)));
        var jobs = parts.selectForExecution(new InspectionExecutionRows(task.getTenantId(), execution.getId()));
        require(!jobs.isEmpty(), "执行记录缺少解析分片");
        Map<Integer, InspectionResultEvaluator.CommandOutput> outputs = new HashMap<>();
        Set<String> parserSerials = new HashSet<>();
        int submitted = 0; boolean unavailable = false;
        for (var job : jobs) {
            boolean alreadySucceeded = "SUCCEEDED".equals(job.getStatusCode());
            var selected = document.commandBlocks().stream().filter(b -> b.commandIndex() >= job.getFirstCommandIndex()
                    && b.commandIndex() <= job.getLastCommandIndex()).toList();
            if ("FAILED".equals(job.getStatusCode())) { failedOutputs(selected, outputs); continue; }
            try {
                var remote = parser.find(job.getRequestId(), task.getProjectId().toString(), null);
                if (remote == null && "PENDING".equals(job.getStatusCode()) && submitted++ < 16) {
                    remote = parser.submit(new DeviceLogParsingApi.Submission(job.getRequestId(), task.getProjectId().toString(), null,
                            new String(InspectionParserDocuments.encode(selected), StandardCharsets.UTF_8), null));
                }
                if (remote == null) {
                    if (alreadySucceeded) { unavailable = true; continue; }
                    if (!"PENDING".equals(job.getStatusCode())) {
                        job.setStatusCode("FAILED"); job.setFailureCode("PARSER_RESULT_UNAVAILABLE");
                        job.setCompletedAt(LocalDateTime.now()); parts.updateById(job); failedOutputs(selected, outputs);
                    }
                    continue;
                }
                require(job.getParserTaskId() == null || job.getParserTaskId().equals(remote.taskId()), "解析任务身份发生变化，已拒绝消费结果");
                require(!alreadySucceeded || "SUCCEEDED".equals(remote.state()) && Objects.equals(job.getParserRelease(), remote.releaseVersion()),
                        "已完成解析的状态或发布版本发生变化，原记录保留，已拒绝消费结果");
                job.setParserTaskId(remote.taskId()); job.setParserRelease(remote.releaseVersion());
                if ("SUCCEEDED".equals(remote.state())) {
                    require(remote.result() != null, "解析任务成功但缺少结果证据");
                    if (!alreadySucceeded) { job.setStatusCode("SUCCEEDED"); job.setCompletedAt(LocalDateTime.now()); }
                    var facts = DeviceLogSemanticFacts.from(remote.result());
                    if (facts.serialNumber() != null) parserSerials.add(facts.serialNumber());
                    if (facts.conpVersion() != null) execution.setSoftwareVersion(facts.conpVersion());
                    var units = JsonUtils.parseTree(JsonUtils.toJsonString(remote.result())).path("semanticResult").path("genericContent").path("units");
                    for (var block : selected) outputs.put(block.commandIndex(), InspectionParserDocuments.output(
                            new InspectionParserDocuments.Observation(block, units)));
                } else if (Set.of("FAILED", "CANCELLED", "REJECTED").contains(remote.state())) {
                    job.setStatusCode("FAILED"); job.setFailureCode("PARSER_" + remote.state());
                    job.setCompletedAt(LocalDateTime.now()); failedOutputs(selected, outputs);
                } else job.setStatusCode("RUNNING");
                if (!alreadySucceeded) parts.updateById(job);
            } catch (DeviceLogParsingApi.ParsingException dependency) { unavailable = true; }
        }
        execution.setParserRelease(jobs.stream().map(InspectionParsePartDO::getParserRelease).filter(Objects::nonNull)
                .distinct().collect(Collectors.joining(",")));
        boolean terminal = jobs.stream().allMatch(j -> Set.of("SUCCEEDED", "FAILED").contains(j.getStatusCode()));
        var device = device(new InspectionTaskRows(task.getTenantId(), task.getId()), execution.getDeviceId());
        if (parserSerials.size() > 1 || !parserSerials.isEmpty() && !parserSerials.contains(device.getSerialNumber())) {
            execution.setStatusCode("FAILED"); execution.setFailureCode("DEVICE_SERIAL_MISMATCH");
            execution.setFailureMessage("解析结果设备序列号不匹配，拒绝用于检测判定"); execution.setCompletedAt(LocalDateTime.now());
        } else if (terminal && !unavailable) {
            if (execution.getRecognizedSerial() == null && parserSerials.size() == 1) execution.setRecognizedSerial(parserSerials.iterator().next());
            evaluate(new EvaluationInput(task, execution, Map.copyOf(outputs)));
            execution.setStatusCode(jobs.stream().anyMatch(j -> "FAILED".equals(j.getStatusCode())) ? "PARTIAL" : "COMPLETE");
            execution.setCompletedAt(LocalDateTime.now());
        } else execution.setStatusCode("RUNNING");
        executions.updateById(execution);
        tasks.save(new InspectionTaskService.Save(command.context(), task, "REFRESH_LOG_PARSE", task.getStatusCode(),
                unavailable ? "解析服务暂不可用，已保留请求身份，可继续刷新" : "读取巡检日志解析任务结果"));
        return InspectionTaskService.summary(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary abandon(Abandon command) {
        var task = writable(command.context(), command.taskId());
        InspectionTaskService.validateText(command.reason(), 2000, "停止采用原因");
        var row = executions.selectById(command.executionId());
        require(row != null && Objects.equals(row.getTenantId(), task.getTenantId()) && Objects.equals(row.getTaskId(), task.getId())
                && Objects.equals(row.getAttemptId(), task.getCurrentAttemptId())
                && Set.of("OFFLINE", "COLLECT").contains(row.getPurposeCode()) && row.getArtifactId() != null,
                "日志记录不属于当前尝试或尚未结束采集");
        require(Set.of("PENDING", "RUNNING").contains(row.getStatusCode()), "该日志已结束，无需停止采用");
        // 本地停止采用解析结果；DAC的既有任务及原分片状态保留，不声称远端取消成功。
        row.setStatusCode("CANCELLED"); row.setFailureCode("CONSUMPTION_CANCELLED");
        row.setFailureMessage(command.reason()); row.setCompletedAt(LocalDateTime.now()); executions.updateById(row);
        tasks.save(new InspectionTaskService.Save(command.context(), task, "ABANDON_LOG_PARSE", task.getStatusCode(),
                "停止采用日志" + row.getId() + "：" + command.reason()));
        return InspectionTaskService.summary(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionViews.TaskSummary finish(Finish command) {
        var task = writable(command.context(), command.taskId());
        var frozen = frozen(new InspectionTaskRows(task.getTenantId(), task.getId())).stream()
                .filter(r -> Objects.equals(r.getAttemptId(), task.getCurrentAttemptId())).toList();
        var current = results.selectCurrent(new InspectionAttemptRows(task.getTenantId(), task.getCurrentAttemptId()));
        boolean complete = !frozen.isEmpty() && current.size() == frozen.size()
                && current.stream().allMatch(r -> Set.of("PASS", "ABNORMAL").contains(r.getOutcome()))
                && executions.selectForTask(new InspectionTaskRows(task.getTenantId(), task.getId())).stream()
                .noneMatch(e -> Objects.equals(e.getAttemptId(), task.getCurrentAttemptId()) && Set.of("PENDING", "RUNNING", "COLLECTED", "CANCEL_REQUESTED").contains(e.getStatusCode()));
        tasks.transition(new InspectionTaskService.Change(command.context(), task, new InspectionTaskStateMachine.Transition(task.getStatusCode(),
                InspectionTaskStateMachine.Action.EXECUTION_COMPLETE, complete, false, false, 0, false, false), "完成执行并进入报告阶段"));
        var attempt = attempts.selectById(task.getCurrentAttemptId()); attempt.setStatusCode("COMPLETED");
        attempt.setFinishedAt(LocalDateTime.now()); attempts.updateById(attempt);
        return InspectionTaskService.summary(task);
    }

    private record EvaluationInput(InspectionTaskDO task, InspectionExecutionDO execution, Map<Integer, InspectionResultEvaluator.CommandOutput> outputs) { }
    private void evaluate(EvaluationInput input) {
        var execution = input.execution();
        var current = results.selectCurrent(new InspectionAttemptRows(execution.getTenantId(), execution.getAttemptId()));
        int commandIndex = 0;
        for (var snapshot : frozen(new InspectionTaskRows(execution.getTenantId(), execution.getTaskId())).stream()
                .sorted(Comparator.comparing(InspectionTaskRuleSnapshotDO::getId)).toList()) {
            if (!Objects.equals(snapshot.getAttemptId(), execution.getAttemptId()) || !Objects.equals(snapshot.getDeviceId(), execution.getDeviceId())) continue;
            var rule = rules.detail(snapshot.getRuleRevisionId()); List<InspectionResultEvaluator.CommandOutput> selected = new ArrayList<>();
            boolean anyMatch = false;
            for (var commandText : rule.getCommands().stream().sorted(Comparator.comparingInt(c -> c.executionOrder()))
                    .flatMap(c -> c.commandContent().replace('\r', '\n').lines()).map(String::strip).filter(s -> !s.isEmpty()).toList()) {
                commandIndex++;
                var indexed = input.outputs().get(commandIndex);
                var candidates = "COLLECT".equals(execution.getPurposeCode()) ? (indexed == null ? List.<InspectionResultEvaluator.CommandOutput>of() : List.of(indexed))
                        : input.outputs().values();
                var matching = candidates.stream().filter(o -> InspectionParserDocuments.normalizeCommand(o.commandText())
                        .equals(InspectionParserDocuments.normalizeCommand(commandText))).distinct().toList();
                anyMatch |= !matching.isEmpty();
                if (matching.size() == 1) selected.add(matching.getFirst());
                else selected.add(new InspectionResultEvaluator.CommandOutput(commandText, "SUCCEEDED",
                        matching.isEmpty() ? "EMPTY" : "LIMITED", "", List.of(), false));
            }
            if (!anyMatch && current.stream().anyMatch(r -> Objects.equals(r.getRuleSnapshotId(), snapshot.getId()))) continue;
            var value = evaluator.evaluate(InspectionResultEvaluator.Input.builder().expectedRegex(rule.getExpectedResultRegex())
                    .thresholdOperator(rule.getThresholdOperator()).thresholdValue(rule.getThresholdValue()).thresholdUnit(rule.getThresholdUnit())
                    .commands(selected).build());
            if ("COLLECT".equals(execution.getPurposeCode()) && !Set.of("SUCCESS", "SUCCEEDED").contains(execution.getCollectionStatus()))
                value = new InspectionResultEvaluator.Evaluation("ERROR", null, null, null, "COLLECTION_NOT_SUCCEEDED",
                        "平台采集未成功，保留原始输出用于核查，请重新执行或显式创建离线尝试");
            if (value.measuredValue() != null) {
                var number = value.measuredValue().stripTrailingZeros();
                if (number.scale() > 10 || number.precision() - number.scale() > 20)
                    value = new InspectionResultEvaluator.Evaluation("UNJUDGED", null, value.measuredUnit(), value.regexMatched(),
                            "VALUE_STORAGE_LIMIT", "数值超出精确结果存储范围，未舍入或判为通过");
            }
            var row = new InspectionDetectionResultDO(); row.setTenantId(execution.getTenantId()); row.setVersion(1L);
            row.setTaskId(execution.getTaskId()); row.setAttemptId(execution.getAttemptId()); row.setExecutionId(execution.getId());
            row.setRuleSnapshotId(snapshot.getId()); row.setDeviceId(execution.getDeviceId()); row.setRuleRevisionId(rule.getRevisionId());
            row.setOutcome(value.outcome()); row.setMeasuredValue(value.measuredValue()); row.setMeasuredUnit(value.measuredUnit());
            row.setRegexMatched(value.regexMatched()); row.setDetailCode(value.detailCode()); row.setDetailMessage(value.detailMessage());
            String summary = selected.stream().map(InspectionResultEvaluator.CommandOutput::output).filter(Objects::nonNull).collect(Collectors.joining("\n"));
            row.setOutputSummary(summary.length() <= 8000 ? summary : summary.substring(0, 8000) + "\n[展示摘要已截断，完整证据见冻结日志]");
            results.insert(row);
        }
    }
    private static void failedOutputs(List<InspectionParserDocuments.Block> selected, Map<Integer, InspectionResultEvaluator.CommandOutput> outputs) {
        for (var block : selected) outputs.put(block.commandIndex(), new InspectionResultEvaluator.CommandOutput(
                block.commandText(), "FAILED", "LIMITED", block.stdout(), List.of(), true));
    }
    private InspectionTaskDO writable(InspectionRequestContext context, Long taskId) {
        var task = access.task(new InspectionAccessService.TaskAccess(context, taskId, "pms:inspection-task:execute", true, false));
        require("EXECUTING".equals(task.getStatusCode()), "任务当前不在执行阶段"); return task;
    }
    private InspectionTaskDeviceDO device(InspectionTaskRows query, Long deviceId) {
        return devices.selectForTask(query).stream().filter(d -> Objects.equals(d.getDeviceId(), deviceId)).findFirst()
                .orElseThrow(() -> new InspectionException(InspectionException.Reason.INVALID_REQUEST, "设备不属于本巡检任务"));
    }
    private Scope scope(Scope scope) {
        var attempt = scope.attempt(); var task = scope.task();
        require(attempt != null && Objects.equals(attempt.getTenantId(), task.getTenantId()) && Objects.equals(attempt.getTaskId(), task.getId())
                && Objects.equals(attempt.getId(), task.getCurrentAttemptId()) && "ACTIVE".equals(attempt.getStatusCode()), "执行尝试已变化，请刷新");
        return scope;
    }
    private List<InspectionTaskRuleSnapshotDO> frozen(InspectionTaskRows query) { return snapshots.selectForTask(query); }
}
