package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

@Service
@RequiredArgsConstructor
public class InspectionOnlineExecutionService {
    public static final String PRECHECK_TTL_KEY = "pms.inspection.precheck-valid-seconds";
    private final InspectionAccessService access;
    private final InspectionOnlineAuthorization authorization;
    private final InspectionExecutionMapper executions;
    private final InspectionAttemptMapper attempts;
    private final InspectionTaskMapper taskMapper;
    private final InspectionTaskDeviceMapper devices;
    private final InspectionTaskService tasks;
    private final InspectionExecutionService execution;
    private final CollectionApplicationApi collections;
    private final CollectionLogAccessApi logs;
    private final InspectionCommandExecutor commands;
    private final ConfigApi config;
    private final PlatformTransactionManager transactions;

    public record Connection(String host, Integer port, String protocol, String username, Long credentialId) { }
    public record Reserve(InspectionRequestContext context, Long taskId, Long deviceId, String purpose,
                          Long precheckExecutionId, Connection connection) { }
    public record Reserved(InspectionViews.TaskSummary task, InspectionViews.ExecutionView execution) { }
    public record Dispatch(InspectionRequestContext context, Long taskId, Long executionId, char[] password) {
        @Override public String toString() { return "Dispatch[credential material omitted]"; }
    }
    public record Refresh(InspectionRequestContext context, Long taskId, Long executionId) { }
    public record Cancel(InspectionRequestContext context, Long taskId, Long executionId, String reason) { }
    private record Sync(Refresh command, CollectionApplicationApi.Execution remote) { }
    private record Bind(InspectionTaskDO task, InspectionExecutionDO row, CollectionApplicationApi.Execution remote) { }
    private record CommandInput(Long version, Long executionId, String reason) { }

    @Transactional(rollbackFor = Exception.class)
    public Reserved reserve(Reserve command) {
        var context = command.context();
        var task = access.task(new InspectionAccessService.TaskAccess(context, command.taskId(), "pms:inspection-task:execute", true, false));
        require(Set.of("PRECHECK_PENDING", "EXECUTING").contains(task.getStatusCode()), "请先开始在线执行尝试");
        require(command.purpose() != null && Set.of("PRECHECK", "COLLECT").contains(command.purpose()), "在线执行用途无效");
        var attempt = attempts.selectById(task.getCurrentAttemptId());
        require(attempt != null && Objects.equals(attempt.getTenantId(), task.getTenantId()) && "ONLINE".equals(attempt.getMode())
                && "ACTIVE".equals(attempt.getStatusCode()), "当前尝试不是在线巡检");
        var rows = new InspectionTaskRows(task.getTenantId(), task.getId());
        require(devices.selectForTask(rows).stream().anyMatch(d -> Objects.equals(d.getDeviceId(), command.deviceId())), "设备不属于本巡检任务");
        require(executions.selectForTask(rows).stream().noneMatch(r -> Objects.equals(r.getAttemptId(), attempt.getId())
                && Objects.equals(r.getDeviceId(), command.deviceId()) && Set.of("PENDING", "RUNNING", "COLLECTED", "CANCEL_REQUESTED").contains(r.getStatusCode())),
                "该设备仍有预检、采集或解析未结束，请先处理原执行记录");
        validateConnection(command.connection());
        var row = new InspectionExecutionDO(); row.setTenantId(task.getTenantId()); row.setVersion(1L);
        row.setTaskId(task.getId()); row.setAttemptId(attempt.getId()); row.setDeviceId(command.deviceId());
        row.setPurposeCode(command.purpose()); row.setStatusCode("PENDING"); row.setSubmittedBy(context.actorId()); row.setSubmittedAt(LocalDateTime.now());
        row.setCollectionRequestKey(UUID.randomUUID().toString()); row.setCredentialSnapshotRecorded(false);
        var connection = command.connection(); row.setProtocol(connection.protocol()); row.setCredentialId(connection.credentialId());
        if (connection.credentialId() == null) {
            row.setHost(connection.host().trim().toLowerCase(Locale.ROOT)); row.setPort(connection.port()); row.setUsername(connection.username().trim());
        }
        row.setRuleDigest(authorization.frozen(new InspectionOnlineAuthorization.Target(task.getTenantId(), task.getId(), attempt.getId(), command.deviceId())).digest());
        if ("PRECHECK".equals(command.purpose())) row.setPrecheckTtlSeconds(precheckTtl());
        else {
            require("EXECUTING".equals(task.getStatusCode()), "请先完成全部设备预检");
            require(command.precheckExecutionId() != null && command.precheckExecutionId() > 0, "请选择已通过的预检记录");
            row.setPrecheckExecutionId(command.precheckExecutionId());
            authorization.requirePrecheck(new InspectionOnlineAuthorization.Precheck(row,
                    executions.selectById(command.precheckExecutionId()), null, LocalDateTime.now()));
        }
        executions.insert(row);
        tasks.save(new InspectionTaskService.Save(context, task, "RESERVE_" + command.purpose(), task.getStatusCode(),
                "登记独立" + ("PRECHECK".equals(command.purpose()) ? "预检" : "正式采集") + "请求" + row.getId()));
        return new Reserved(InspectionTaskService.summary(task), view(row));
    }

    /** Local reservation is already committed. Never hold the SRV transaction across remote dispatch. */
    public Reserved dispatch(Dispatch command) {
        try {
            var bound = readable(new Refresh(command.context(), command.taskId(), command.executionId()));
            access.permission(command.context(), "pms:inspection-task:execute");
            require(Objects.equals(bound.execution().getSubmittedBy(), command.context().actorId()), "请由登记该请求的人员重新输入认证信息");
            var source = source(command.context(), command.executionId());
            var remote = collections.findByRequestKey(source, bound.execution().getCollectionRequestKey());
            if (remote == null) {
                require(Objects.equals(bound.execution().getVersion(), command.context().requireVersion()), "执行记录已更新，请刷新或恢复原请求");
                var row = bound.execution(); var request = new CollectionExecutionRequest();
                request.setRequestKey(row.getCollectionRequestKey()); request.setExpectedVersion(row.getVersion()); request.setDeviceId(row.getDeviceId());
                request.setProtocol(row.getProtocol()); request.setCredentialId(row.getCredentialId()); request.setHost(row.getHost());
                request.setPort(row.getPort()); request.setUsername(row.getUsername()); request.setPassword(command.password());
                remote = collections.submit(source, request);
            }
            var selected = remote;
            return new TransactionTemplate(transactions).execute(tx -> {
                var task = taskMapper.selectForUpdate(new InspectionTaskRows(command.context().tenantId(), command.taskId()));
                var row = executions.selectById(command.executionId()); bind(new Bind(task, row, selected));
                return new Reserved(InspectionTaskService.summary(task), view(row));
            });
        } finally { if (command.password() != null) Arrays.fill(command.password(), '\0'); }
    }

    public InspectionViews.TaskSummary refresh(Refresh command) {
        var bound = readable(command); access.permission(command.context(), "pms:inspection-task:execute");
        var source = source(command.context(), command.executionId());
        var remote = collections.findByRequestKey(source, bound.execution().getCollectionRequestKey());
        // Result delivery commits facts separately and is itself idempotent; do this before taking the task write lock.
        if (remote != null && remote.task().fileVersionId() != null && remote.task().resultVersion() != null
                && Set.of("RESULT_AVAILABLE", "COMPLETED", "FAILED", "CANCELLED").contains(remote.task().status())) {
            collections.consume(source, remote.id());
            remote = collections.findByRequestKey(source, bound.execution().getCollectionRequestKey());
        }
        var state = new Sync(command, remote);
        return commands.execute(InspectionCommandExecutor.Write.<InspectionViews.TaskSummary>builder().context(command.context())
                .operation("ONLINE_REFRESH").scope(command.taskId().toString())
                .input(new CommandInput(command.context().requireVersion(), command.executionId(), null)).responseType(InspectionViews.TaskSummary.class)
                .action(() -> synchronize(state)).resourceKey(r -> r.getId().toString()).build());
    }

    public InspectionViews.TaskSummary cancel(Cancel command) {
        InspectionTaskService.validateText(command.reason(), 2000, "取消原因");
        var refresh = new Refresh(command.context(), command.taskId(), command.executionId());
        var bound = readable(refresh); access.permission(command.context(), "pms:inspection-task:execute");
        commands.execute(InspectionCommandExecutor.Write.<Reserved>builder().context(command.context()).operation("ONLINE_CANCEL_REQUEST")
                .scope(command.taskId().toString()).input(new CommandInput(command.context().requireVersion(), command.executionId(), command.reason()))
                .responseType(Reserved.class).action(() -> {
                    var task = access.task(new InspectionAccessService.TaskAccess(command.context(), command.taskId(), "pms:inspection-task:execute", true, false));
                    var row = current(task, command.executionId());
                    if ("CANCEL_REQUESTED".equals(row.getStatusCode())) return new Reserved(InspectionTaskService.summary(task), view(row));
                    require(Set.of("PENDING", "RUNNING", "COLLECTED").contains(row.getStatusCode()), "该执行记录已结束");
                    row.setStatusCode("CANCEL_REQUESTED"); row.setFailureMessage(command.reason()); executions.updateById(row);
                    tasks.save(new InspectionTaskService.Save(command.context(), task, "REQUEST_ONLINE_CANCEL", task.getStatusCode(), command.reason()));
                    return new Reserved(InspectionTaskService.summary(task), view(row));
                }).resourceKey(r -> r.execution().getId().toString()).build());
        var source = source(command.context(), command.executionId());
        var remote = collections.findByRequestKey(source, bound.execution().getCollectionRequestKey());
        if (remote != null && Set.of("CREATED", "AUTHORIZED", "DISPATCHED", "EXECUTING", "CALLBACK_PROCESSING").contains(remote.task().status())) {
            collections.cancel(source, remote.id());
            remote = collections.findByRequestKey(source, bound.execution().getCollectionRequestKey());
            require(remote != null && Set.of("CANCELLED", "FAILED", "COMPLETED", "RESULT_AVAILABLE").contains(remote.task().status()),
                    "远端取消尚未确认，请刷新原执行记录");
        }
        var settled = remote;
        return new TransactionTemplate(transactions).execute(tx -> {
                    var task = taskMapper.selectForUpdate(new InspectionTaskRows(command.context().tenantId(), command.taskId()));
                    var row = current(task, command.executionId());
                    if (!"CANCEL_REQUESTED".equals(row.getStatusCode())) return InspectionTaskService.summary(task);
                    if (settled != null) bind(new Bind(task, row, settled));
                    row.setStatusCode("CANCELLED"); row.setFailureCode(settled == null ? "CANCELLED_BEFORE_DISPATCH" : "CONSUMPTION_CANCELLED");
                    row.setFailureMessage(command.reason()); row.setCompletedAt(LocalDateTime.now()); executions.updateById(row);
                    tasks.save(new InspectionTaskService.Save(command.context(), task, "CANCEL_ONLINE", task.getStatusCode(), command.reason()));
                    return InspectionTaskService.summary(task);
                });
    }

    public String download(Refresh query) {
        var bound = readable(query);
        var remote = collections.findByRequestKey(source(query.context(), query.executionId()), bound.execution().getCollectionRequestKey());
        require(remote != null && remote.task().fileVersionId() != null, "平台采集日志尚不可用");
        return collections.download(source(query.context(), query.executionId()), remote.id());
    }

    private InspectionViews.TaskSummary synchronize(Sync input) {
        var command = input.command();
        var task = access.task(new InspectionAccessService.TaskAccess(command.context(), command.taskId(), "pms:inspection-task:execute", true, false));
        require(Set.of("PRECHECK_PENDING", "EXECUTING").contains(task.getStatusCode()), "当前阶段不再接收新的在线执行结果");
        var row = current(task, command.executionId()); var remote = input.remote();
        if (remote == null) {
            if ("CANCEL_REQUESTED".equals(row.getStatusCode())) {
                row.setStatusCode("CANCELLED"); row.setFailureCode("CANCELLED_BEFORE_DISPATCH"); row.setCompletedAt(LocalDateTime.now());
                executions.updateById(row); tasks.save(new InspectionTaskService.Save(command.context(), task, "CONFIRM_ONLINE_CANCEL", task.getStatusCode(), row.getFailureMessage()));
            }
            return InspectionTaskService.summary(task);
        }
        bind(new Bind(task, row, remote));
        if (Set.of("CANCELLED", "COMPLETE", "PARTIAL", "FAILED").contains(row.getStatusCode())) return InspectionTaskService.summary(task);
        if ("PASSED".equals(row.getStatusCode())) {
            require(Objects.equals(row.getCollectionResultVersion().longValue(), remote.task().resultVersion()),
                    "预检采集结果版本已变化，原通过记录保留，请创建新预检");
            return advancePrechecks(command.context(), task);
        }
        if ("CANCEL_REQUESTED".equals(row.getStatusCode())) {
            require(Set.of("CANCELLED", "FAILED", "COMPLETED", "RESULT_AVAILABLE").contains(remote.task().status()), "取消尚未确认，请使用原取消操作重试");
            row.setStatusCode("CANCELLED"); row.setFailureCode("CONSUMPTION_CANCELLED"); row.setCompletedAt(LocalDateTime.now());
            executions.updateById(row); tasks.save(new InspectionTaskService.Save(command.context(), task, "CONFIRM_ONLINE_CANCEL", task.getStatusCode(), row.getFailureMessage()));
            return InspectionTaskService.summary(task);
        }
        if (row.getCollectionResultVersion() != null && !Objects.equals(row.getCollectionResultVersion().longValue(), remote.task().resultVersion())) {
            fail(row, "COLLECTION_RESULT_CHANGED", "平台采集已产生其他结果版本，原版本保留，请创建新的执行请求");
        } else if (remote.task().fileVersionId() == null) {
            if (Set.of("FAILED", "CANCELLED", "SECURITY_EXCEPTION").contains(remote.task().status()))
                fail(row, "COLLECTION_" + remote.task().status(), "平台采集未成功，原失败事实保留，可新建请求或离线尝试");
            else row.setStatusCode("PENDING_DISPATCH".equals(remote.task().technicalStage()) ? "PENDING" : "RUNNING");
        } else {
            require(row.getCollectionResultVersion() != null && row.getCollectionFileVersionId() != null
                    && Objects.equals(row.getCollectionFileVersionId(), remote.task().fileVersionId()), "采集结果仍在绑定中，请稍后刷新");
            if ("COLLECT".equals(row.getPurposeCode()) && row.getArtifactId() != null)
                return execution.refresh(new InspectionExecutionService.Refresh(command.context(), task.getId(), row.getId()));
            if ("PRECHECK".equals(row.getPurposeCode()) && !Set.of("SUCCESS", "SUCCEEDED").contains(row.getCollectionStatus()))
                fail(row, "PRECHECK_FAILED", "设备连接预检未成功，请查看平台日志并重新预检");
            else {
                byte[] raw = logs.read(new CollectionLogAccessApi.ContentQuery(task.getTenantId(), command.context().actorId(),
                        row.getCollectionTaskId(), row.getCollectionResultVersion().longValue(), row.getCollectionFileVersionId()));
                try {
                    var document = JsonUtils.parseTree(new String(raw, StandardCharsets.UTF_8));
                    require(document != null && ("npdms-" + task.getTenantId()).equals(document.path("namespace").asText())
                            && row.getCollectionTaskId().equals(document.path("externalRequestId").asText())
                            && task.getProjectId().toString().equals(document.path("projectKey").asText())
                            && row.getDeviceId().toString().equals(document.path("deviceKey").asText())
                            && Objects.equals(remote.task().templateHash(), document.path("script").path("sha256").asText()),
                            "采集日志与任务、设备或命令快照不一致");
                    try {
                        row.setDeviceStartedAt(LocalDateTime.ofInstant(Instant.parse(document.path("startedAt").asText()), ZoneId.systemDefault()));
                        row.setDeviceCompletedAt(LocalDateTime.ofInstant(Instant.parse(document.path("completedAt").asText()), ZoneId.systemDefault()));
                    } catch (java.time.format.DateTimeParseException missing) {
                        if ("PRECHECK".equals(row.getPurposeCode())) throw new InspectionException(InspectionException.Reason.INVALID_REQUEST, "预检缺少真实完成时间，请重新预检");
                    }
                    if ("PRECHECK".equals(row.getPurposeCode())) {
                        require("PASSED".equals(document.path("parsedFacts").path("connectionPrecheck").asText())
                                && "SUCCEEDED".equals(document.path("status").asText())
                                && !document.path("truncated").asBoolean() && row.getDeviceStartedAt() != null
                                && row.getDeviceCompletedAt() != null && !row.getDeviceCompletedAt().isBefore(row.getDeviceStartedAt())
                                && !row.getDeviceCompletedAt().isAfter(LocalDateTime.now()), "预检完成证据无效，请重新预检");
                        row.setStatusCode("PASSED"); row.setCompletedAt(LocalDateTime.now());
                        row.setPrecheckExpiresAt(row.getDeviceCompletedAt().plusSeconds(row.getPrecheckTtlSeconds())); executions.updateById(row);
                        return advancePrechecks(command.context(), task);
                    }
                    executions.updateById(row);
                    return execution.ingestCollection(new InspectionExecutionService.Ingest(command.context(), task.getId(), row.getId(), raw));
                } finally { Arrays.fill(raw, (byte) 0); }
            }
        }
        executions.updateById(row);
        tasks.save(new InspectionTaskService.Save(command.context(), task, "REFRESH_ONLINE", task.getStatusCode(), "读取在线采集状态：" + row.getStatusCode()));
        return InspectionTaskService.summary(task);
    }

    private InspectionViews.TaskSummary advancePrechecks(InspectionRequestContext context, InspectionTaskDO task) {
        var rows = new InspectionTaskRows(task.getTenantId(), task.getId()); var now = LocalDateTime.now();
        var passed = executions.selectForTask(rows).stream().filter(r -> Objects.equals(r.getAttemptId(), task.getCurrentAttemptId())
                && Objects.equals(r.getSubmittedBy(), context.actorId()) && "PRECHECK".equals(r.getPurposeCode()) && "PASSED".equals(r.getStatusCode())
                && r.getPrecheckExpiresAt() != null && r.getPrecheckExpiresAt().isAfter(now)).filter(authorization::hasSelectedResult)
                .map(InspectionExecutionDO::getDeviceId).collect(java.util.stream.Collectors.toSet());
        boolean complete = devices.selectForTask(rows).stream().allMatch(d -> passed.contains(d.getDeviceId()));
        if (complete && "PRECHECK_PENDING".equals(task.getStatusCode())) tasks.transition(new InspectionTaskService.Change(context, task,
                new InspectionTaskStateMachine.Transition(task.getStatusCode(), InspectionTaskStateMachine.Action.PRECHECK_PASSED,
                        false, false, false, 0, false, false), "全部设备的独立连接预检通过"));
        else tasks.save(new InspectionTaskService.Save(context, task, "REFRESH_PRECHECK", task.getStatusCode(), "读取独立预检结果及实际有效期"));
        return InspectionTaskService.summary(task);
    }

    private InspectionOnlineAuthorization.Bound readable(Refresh query) {
        var bound = authorization.authorize(CollectionSourceAdapter.AuthorizationQuery.builder().tenantId(query.context().tenantId())
                .actorId(query.context().actorId()).objectId(query.executionId()).access(CollectionSourceAdapter.Access.READ).build());
        require(Objects.equals(query.taskId(), bound.task().getId()), "在线执行记录不属于本巡检任务"); return bound;
    }
    private InspectionExecutionDO current(InspectionTaskDO task, Long id) {
        var row = executions.selectById(id);
        require(row != null && Objects.equals(row.getTenantId(), task.getTenantId()) && Objects.equals(row.getTaskId(), task.getId())
                && Objects.equals(row.getAttemptId(), task.getCurrentAttemptId()) && Set.of("PRECHECK", "COLLECT").contains(row.getPurposeCode()),
                "在线执行记录不属于当前尝试"); return row;
    }
    private void bind(Bind value) {
        var task = value.task(); var row = value.row(); var remote = value.remote(); var fact = remote.task();
        require(task != null && row != null && Objects.equals(task.getTenantId(), row.getTenantId()) && Objects.equals(task.getId(), row.getTaskId())
                && Objects.equals(row.getCollectionRequestKey(), fact.idempotencyKey()) && "SRV".equals(fact.sourceContext())
                && "INSPECTION_EXECUTION".equals(fact.sourceObjectType()) && row.getId().toString().equals(fact.sourceObjectId())
                && task.getProjectId().toString().equals(fact.projectId()) && row.getDeviceId().toString().equals(fact.deviceId())
                && Objects.equals(row.getSubmittedBy(), remote.actorId()), "平台采集与巡检执行的来源绑定不一致");
        require(row.getCollectionExecutionId() == null || Objects.equals(row.getCollectionExecutionId(), remote.id()), "平台执行身份冲突");
        require(row.getCollectionTaskId() == null || Objects.equals(row.getCollectionTaskId(), fact.platformTaskId()), "平台采集身份冲突");
        if (row.getCollectionExecutionId() == null) {
            row.setCollectionExecutionId(remote.id()); row.setCollectionTaskId(fact.platformTaskId());
            require(executions.updateById(row) == 1, "在线执行记录并发更新");
        }
    }
    private static void fail(InspectionExecutionDO row, String code, String message) {
        row.setStatusCode("FAILED"); row.setFailureCode(code); row.setFailureMessage(message); row.setCompletedAt(LocalDateTime.now());
    }
    private static CollectionApplicationApi.SourceQuery source(InspectionRequestContext context, Long executionId) {
        return CollectionApplicationApi.SourceQuery.builder().entry("inspection").objectId(executionId).actorId(context.actorId()).build();
    }
    private static InspectionViews.ExecutionView view(InspectionExecutionDO row) { return BeanUtils.toBean(row, InspectionViews.ExecutionView.class); }
    private int precheckTtl() {
        String value = config.getConfigValueByKey(PRECHECK_TTL_KEY);
        if (value == null || value.isBlank()) return 900;
        try { int seconds = Integer.parseInt(value); require(seconds > 0, "巡检预检有效期配置须为正整数秒"); return seconds; }
        catch (NumberFormatException invalid) { throw new InspectionException(InspectionException.Reason.INVALID_REQUEST, "巡检预检有效期配置无效"); }
    }
    private static void validateConnection(Connection connection) {
        require(connection != null && connection.protocol() != null && Set.of("SSH", "TELNET").contains(connection.protocol()), "请选择SSH或Telnet协议");
        if (connection.credentialId() != null) { require(connection.credentialId() > 0, "请选择有效保存连接"); return; }
        require(connection.host() != null && connection.host().trim().matches("[a-zA-Z0-9.:-]{1,253}") && connection.port() != null
                && connection.port() > 0 && connection.port() <= 65535 && connection.username() != null && !connection.username().isBlank()
                && connection.username().trim().length() <= 128, "请填写设备地址、端口和用户名");
    }
}
