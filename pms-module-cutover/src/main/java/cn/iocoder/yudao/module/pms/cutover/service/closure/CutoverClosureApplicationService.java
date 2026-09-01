package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureAttachmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceLockQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalTaskQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureAttachmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureDraftUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanRevisionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.AttachmentInput;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.ClosureContent;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.closure.result.CutoverClosureCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationException.Code.*;

/** F-CUT-006 Task 3应用内核；跨模块Provider接通前不注册生产Bean。 */
public class CutoverClosureApplicationService {
    private static final Snowflake ID_GENERATOR = IdUtil.getSnowflake();
    private static final String ACTION_EDIT = "ACTION_EDIT";

    private final CutoverTaskMapper taskMapper;
    private final CutoverApprovalInstanceMapper approvalMapper;
    private final CutoverPlanRevisionMapper planMapper;
    private final CutoverClosureMapper closureMapper;
    private final CutoverClosureAttachmentMapper attachmentMapper;
    private final CutoverProjectScopePort projectScopePort;
    private final CutoverClosureFilePort filePort;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final Clock clock;

    public CutoverClosureApplicationService(CutoverTaskMapper taskMapper,
                                            CutoverApprovalInstanceMapper approvalMapper,
                                            CutoverPlanRevisionMapper planMapper,
                                            CutoverClosureMapper closureMapper,
                                            CutoverClosureAttachmentMapper attachmentMapper,
                                            CutoverProjectScopePort projectScopePort,
                                            CutoverClosureFilePort filePort,
                                            PlatformCommandExecutionApi commandExecutionApi, Clock clock) {
        this.taskMapper = taskMapper;
        this.approvalMapper = approvalMapper;
        this.planMapper = planMapper;
        this.closureMapper = closureMapper;
        this.attachmentMapper = attachmentMapper;
        this.projectScopePort = projectScopePort;
        this.filePort = filePort;
        this.commandExecutionApi = commandExecutionApi;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public CutoverClosureCommandResult save(SaveCutoverClosureCommand command) {
        requireCommand(command);
        PlatformCommandExecutionApi.ExecutionResult<CutoverClosureCommandResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "CUT:CLOSURE_SAVE:" + command.taskId(), command.actorId(), command.idempotencyKey()),
                sha256(JsonUtils.toJsonString(digest(command))), CutoverClosureCommandResult.class,
                () -> saveNew(command), result -> successFacts(result, command.correlationId()));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw failure(IDEMPOTENCY_CONFLICT, "幂等键载荷冲突");
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null) {
            throw failure(IDEMPOTENCY_IN_PROGRESS, "闭环保存命令处理中");
        }
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().replayedCopy() : execution.response();
    }

    private CutoverClosureCommandResult saveNew(SaveCutoverClosureCommand command) {
        CutoverTaskDO inspectedTask = requireTask(taskMapper.selectById(command.taskId()), command);
        CutoverPlanRevisionDO inspectedPlan = requireApprovedPlan(planMapper.selectCurrent(
                new CutoverPlanRevisionQuery(command.tenantId(), command.taskId(), null)));
        CutoverApprovalInstanceDO inspectedApproval = requireApprovedApproval(
                approvalMapper.selectCurrentByTask(new ApprovalTaskQuery(command.tenantId(), command.taskId())),
                inspectedTask, inspectedPlan);
        CutoverClosureDO inspectedClosure = closureMapper.selectByTask(
                new CutoverClosureRowQuery(command.tenantId(), command.taskId()));
        boolean creating = inspectedClosure == null;
        requireExpectedClosureVersion(command, inspectedClosure);
        long closureId = creating ? nextId() : inspectedClosure.getId();
        List<InspectedAttachment> inspectedFiles = inspectFiles(command, inspectedTask, closureId);

        CutoverTaskDO task = requireTask(taskMapper.selectForUpdate(
                new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command);
        CutoverPlanRevisionDO plan = requireApprovedPlan(planMapper.selectByIdForUpdate(
                new CutoverPlanRevisionQuery(command.tenantId(), command.taskId(), inspectedPlan.getId())));
        CutoverApprovalInstanceDO approval = requireApprovedApproval(approvalMapper.selectByIdForUpdate(
                new ApprovalInstanceLockQuery(command.tenantId(), inspectedApproval.getId(),
                        command.taskId(), plan.getId())), task, plan);
        CutoverClosureDO closure = closureMapper.selectByTaskForUpdate(
                new CutoverClosureRowQuery(command.tenantId(), command.taskId()));
        requireExpectedClosureVersion(command, closure);
        if (closure != null) {
            requireFrozenSource(closure, task, approval, plan);
            attachmentMapper.selectListByClosureForUpdate(
                    new CutoverClosureChildrenQuery(command.tenantId(), closure.getId()));
        }
        lockProjectScope(command, task);
        List<CutoverClosureFilePort.FileFact> lockedFiles = lockFiles(command, task, closureId, inspectedFiles);
        return closure == null
                ? createClosure(command, task, approval, plan, closureId, lockedFiles)
                : updateClosure(command, task, closure, lockedFiles);
    }

    private CutoverClosureCommandResult createClosure(SaveCutoverClosureCommand command, CutoverTaskDO task,
                                                       CutoverApprovalInstanceDO approval,
                                                       CutoverPlanRevisionDO plan, long closureId,
                                                       List<CutoverClosureFilePort.FileFact> files) {
        LocalDateTime now = LocalDateTime.now(clock);
        CutoverClosureDO row = new CutoverClosureDO();
        row.setId(closureId); row.setTenantId(command.tenantId()); row.setTaskId(task.getId());
        row.setProjectId(task.getProjectId()); row.setApprovalInstanceId(approval.getId());
        row.setApprovalVersion(approval.getVersion()); row.setPlanRevisionId(plan.getId());
        row.setPlanRevisionNo(plan.getRevisionNo()); row.setPlanVersion(plan.getVersion());
        row.setTaskVersionAtP6(task.getVersion()); row.setDeviceScopeWatermark(task.getDeviceScopeWatermark());
        row.setStatusCode("DRAFT"); setContent(row, command.content()); row.setVersion(0);
        row.setCreator(String.valueOf(command.actorId())); row.setUpdater(String.valueOf(command.actorId()));
        row.setCreateTime(now); row.setUpdateTime(now); row.setDeleted(false);
        if (closureMapper.insert(row) != 1) throw failure(STATE_CONFLICT, "闭环草稿创建失败");
        insertAttachments(command, closureId, files, now);
        return result(task, closureId, 0);
    }

    private CutoverClosureCommandResult updateClosure(SaveCutoverClosureCommand command, CutoverTaskDO task,
                                                       CutoverClosureDO closure,
                                                       List<CutoverClosureFilePort.FileFact> files) {
        if (!"DRAFT".equals(closure.getStatusCode())) throw failure(STATE_CONFLICT, "闭环已归档");
        LocalDateTime now = LocalDateTime.now(clock);
        ClosureContent content = command.content();
        if (closureMapper.updateDraftIfMatch(new CutoverClosureDraftUpdate(command.tenantId(), closure.getId(),
                command.expectedClosureVersion(), content.preCheckNormal(), content.preCheckDetail(),
                content.executionNormal(), content.executionDetail(), content.testNormal(), content.testDetail(),
                content.rollbackOccurred(), content.rollbackSuccessful(), content.rollbackReason(),
                content.legacyItems(), content.finalResult(), String.valueOf(command.actorId()), now)) != 1) {
            throw failure(CLOSURE_VERSION_STALE, "闭环版本已变化");
        }
        CutoverClosureChildrenQuery children = new CutoverClosureChildrenQuery(command.tenantId(), closure.getId());
        attachmentMapper.deleteDraftRows(children);
        insertAttachments(command, closure.getId(), files, now);
        return result(task, closure.getId(), command.expectedClosureVersion() + 1);
    }

    private void insertAttachments(SaveCutoverClosureCommand command, long closureId,
                                   List<CutoverClosureFilePort.FileFact> files, LocalDateTime now) {
        List<AttachmentInput> inputs = sortedAttachments(command.content().attachments());
        for (int index = 0; index < inputs.size(); index++) {
            AttachmentInput input = inputs.get(index);
            CutoverClosureFilePort.FileFact file = files.get(index);
            CutoverClosureAttachmentDO row = new CutoverClosureAttachmentDO();
            row.setId(nextId()); row.setTenantId(command.tenantId()); row.setClosureId(closureId);
            row.setPurposeCode(input.purposeCode().name()); row.setReferenceKey(file.referenceKey());
            row.setArtifactId(file.artifactId()); row.setFileVersionNo(file.versionNo());
            row.setFileFactVersion(JsonUtils.toJsonString(file.fileFactVersion()));
            row.setFileScopeVersion(file.scopeVersion()); row.setFileHash(file.sha256()); row.setVersion(0);
            row.setCreator(String.valueOf(command.actorId())); row.setUpdater(String.valueOf(command.actorId()));
            row.setCreateTime(now); row.setUpdateTime(now); row.setDeleted(false);
            if (attachmentMapper.insert(row) != 1) throw failure(STATE_CONFLICT, "闭环附件写入失败");
        }
    }

    private List<InspectedAttachment> inspectFiles(SaveCutoverClosureCommand command, CutoverTaskDO task,
                                                    long closureId) {
        return sortedAttachments(command.content().attachments()).stream().map(input -> {
            CutoverClosureFilePort.FileExpectation expectation = expectation(command, task, closureId, input);
            try {
                CutoverClosureFilePort.FileFact fact = filePort.inspect(expectation);
                requireSameFile(expectation, fact);
                return new InspectedAttachment(expectation, fact);
            } catch (CutoverClosureOwnerFactException ex) {
                throw ownerFailure(ex);
            }
        }).toList();
    }

    private List<CutoverClosureFilePort.FileFact> lockFiles(SaveCutoverClosureCommand command, CutoverTaskDO task,
                                                             long closureId,
                                                             List<InspectedAttachment> inspected) {
        return inspected.stream().map(value -> {
            try {
                CutoverClosureFilePort.FileFact current = filePort.lockAndRevalidate(value.expectation());
                requireSameFile(value.expectation(), value.fact());
                requireSameFile(value.expectation(), current);
                return current;
            } catch (CutoverClosureOwnerFactException ex) {
                throw ownerFailure(ex);
            }
        }).toList();
    }

    private static CutoverClosureFilePort.FileExpectation expectation(SaveCutoverClosureCommand command,
                                                                       CutoverTaskDO task, long closureId,
                                                                       AttachmentInput input) {
        return new CutoverClosureFilePort.FileExpectation(command.tenantId(), command.actorId(), task.getProjectId(),
                closureId, input.purposeCode(), input.artifactId(), input.versionNo(), input.referenceKey(),
                input.fileFactVersion(), input.scopeVersion(), input.sha256());
    }

    private void lockProjectScope(SaveCutoverClosureCommand command, CutoverTaskDO task) {
        try {
            CutoverProjectScopePort.ProjectScopeFact fact = projectScopePort.lockAndRevalidate(command.actorId(),
                    task.getProjectId(), ACTION_EDIT, task.getProjectScopeVersion());
            if (fact == null || !fact.allowed() || !Objects.equals(fact.projectId(), task.getProjectId())) {
                throw failure(NOT_FOUND, "任务不可见");
            }
            if (fact.projectScopeVersion() != task.getProjectScopeVersion()) {
                throw failure(SOURCE_STALE, "项目范围已变化");
            }
        } catch (CutoverClosureApplicationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw failure(OWNER_PROVIDER_UNAVAILABLE, "项目范围Provider不可用");
        }
    }

    private static void requireSameFile(CutoverClosureFilePort.FileExpectation expected,
                                        CutoverClosureFilePort.FileFact actual) {
        if (actual == null || !Objects.equals(expected.artifactId(), actual.artifactId())
                || !Objects.equals(expected.versionNo(), actual.versionNo())
                || !Objects.equals(expected.referenceKey(), actual.referenceKey())
                || !Objects.equals(expected.fileFactVersion(), actual.fileFactVersion())
                || !Objects.equals(expected.scopeVersion(), actual.scopeVersion())
                || !Objects.equals(expected.sha256(), actual.sha256())) {
            throw failure(FILE_INVALID, "PLT文件事实不匹配");
        }
    }

    private static void requireFrozenSource(CutoverClosureDO closure, CutoverTaskDO task,
                                            CutoverApprovalInstanceDO approval, CutoverPlanRevisionDO plan) {
        if (!Objects.equals(closure.getTaskId(), task.getId())
                || !Objects.equals(closure.getProjectId(), task.getProjectId())
                || !Objects.equals(closure.getTaskVersionAtP6(), task.getVersion())
                || !Objects.equals(closure.getApprovalInstanceId(), approval.getId())
                || !Objects.equals(closure.getApprovalVersion(), approval.getVersion())
                || !Objects.equals(closure.getPlanRevisionId(), plan.getId())
                || !Objects.equals(closure.getPlanRevisionNo(), plan.getRevisionNo())
                || !Objects.equals(closure.getPlanVersion(), plan.getVersion())
                || !Objects.equals(closure.getDeviceScopeWatermark(), task.getDeviceScopeWatermark())) {
            throw failure(SOURCE_STALE, "P6冻结来源已变化");
        }
    }

    private static CutoverTaskDO requireTask(CutoverTaskDO task, SaveCutoverClosureCommand command) {
        if (task == null || !Objects.equals(task.getTenantId(), command.tenantId())) throw failure(NOT_FOUND, "任务不存在");
        if (!"NEW_PLATFORM".equals(task.getTaskOrigin()) || !"P6".equals(task.getCurrentStage())
                || !"CLOSURE_IN_PROGRESS".equals(task.getTaskStatus())) throw failure(STATE_CONFLICT, "任务不在P6闭环中");
        if (!Objects.equals(task.getOwnerUserId(), command.actorId())) throw failure(NOT_FOUND, "任务不可见");
        if (!Objects.equals(task.getVersion(), command.expectedTaskVersion())) throw failure(TASK_VERSION_STALE, "任务版本已变化");
        if (task.getProjectScopeVersion() == null || task.getProjectScopeVersion() < 0
                || task.getDeviceScopeWatermark() == null || task.getDeviceScopeWatermark().isBlank()) {
            throw failure(OWNER_DATA_CORRUPTED, "任务P6来源水位损坏");
        }
        return task;
    }

    private static CutoverPlanRevisionDO requireApprovedPlan(CutoverPlanRevisionDO plan) {
        if (plan == null || !"SUBMITTED".equals(plan.getStatusCode()) || !Objects.equals(plan.getCurrentMarker(), 1)
                || plan.getApprovalInstanceId() == null || plan.getApprovalVersion() == null) {
            throw failure(SOURCE_STALE, "已批准方案不存在");
        }
        return plan;
    }

    private static CutoverApprovalInstanceDO requireApprovedApproval(CutoverApprovalInstanceDO approval,
                                                                      CutoverTaskDO task,
                                                                      CutoverPlanRevisionDO plan) {
        if (approval == null || !"APPROVED".equals(approval.getStatusCode())
                || !Objects.equals(approval.getTaskId(), task.getId())
                || !Objects.equals(approval.getProjectId(), task.getProjectId())
                || !Objects.equals(approval.getPlanRevisionId(), plan.getId())
                || !Objects.equals(approval.getPlanRevisionNo(), plan.getRevisionNo())
                || !Objects.equals(plan.getApprovalInstanceId(), approval.getId())
                || !Objects.equals(plan.getApprovalVersion(), approval.getVersion())) {
            throw failure(SOURCE_STALE, "审批或方案事实已变化");
        }
        return approval;
    }

    private static void requireExpectedClosureVersion(SaveCutoverClosureCommand command, CutoverClosureDO closure) {
        if (closure == null) {
            if (command.expectedClosureVersion() != null) throw failure(CLOSURE_VERSION_STALE, "闭环尚未创建");
            return;
        }
        if (command.expectedClosureVersion() == null
                || !Objects.equals(command.expectedClosureVersion(), closure.getVersion())) {
            throw failure(CLOSURE_VERSION_STALE, "闭环版本已变化");
        }
        if (!"DRAFT".equals(closure.getStatusCode())) throw failure(STATE_CONFLICT, "闭环已归档");
    }

    private static void setContent(CutoverClosureDO row, ClosureContent content) {
        row.setPreCheckNormal(content.preCheckNormal()); row.setPreCheckDetail(content.preCheckDetail());
        row.setExecutionNormal(content.executionNormal()); row.setExecutionDetail(content.executionDetail());
        row.setTestNormal(content.testNormal()); row.setTestDetail(content.testDetail());
        row.setRollbackOccurred(content.rollbackOccurred()); row.setRollbackSuccessful(content.rollbackSuccessful());
        row.setRollbackReason(content.rollbackReason()); row.setLegacyItems(content.legacyItems());
        row.setFinalResultCode(content.finalResult());
    }

    private static void requireCommand(SaveCutoverClosureCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() <= 0
                || command.actorId() == null || command.actorId() <= 0
                || command.taskId() == null || command.taskId() <= 0
                || command.expectedTaskVersion() == null || command.expectedTaskVersion() < 0
                || command.expectedClosureVersion() != null && command.expectedClosureVersion() < 0
                || !validText(command.idempotencyKey(), 128) || !validText(command.correlationId(), 128)) {
            throw failure(INVALID_REQUEST, "闭环保存命令非法");
        }
        try {
            CutoverClosureRules.validateDraftContent(command.content());
        } catch (CutoverClosureOwnerFactException ex) {
            throw failure(INVALID_REQUEST, ex.getMessage());
        }
    }

    private static List<AttachmentInput> sortedAttachments(List<AttachmentInput> attachments) {
        return attachments.stream().sorted(Comparator.comparing((AttachmentInput value) -> value.purposeCode().name())
                .thenComparing(AttachmentInput::referenceKey)).toList();
    }

    private static Map<String, Object> digest(SaveCutoverClosureCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("taskId", command.taskId()); value.put("taskVersion", command.expectedTaskVersion());
        value.put("closureVersion", command.expectedClosureVersion()); value.put("content", new ClosureContent(
                command.content().preCheckNormal(), command.content().preCheckDetail(),
                command.content().executionNormal(), command.content().executionDetail(),
                command.content().testNormal(), command.content().testDetail(),
                command.content().rollbackOccurred(), command.content().rollbackSuccessful(),
                command.content().rollbackReason(), command.content().legacyItems(), command.content().finalResult(),
                sortedAttachments(command.content().attachments())));
        return value;
    }

    private static PlatformCommandExecutionApi.SuccessFacts successFacts(CutoverClosureCommandResult result,
                                                                          String correlationId) {
        return new PlatformCommandExecutionApi.SuccessFacts("CUTOVER_CLOSURE_SAVE", "CutoverClosure",
                String.valueOf(result.closureId()), correlationId, JsonUtils.toJsonString(result), List.of());
    }

    private static CutoverClosureCommandResult result(CutoverTaskDO task, long closureId, int closureVersion) {
        return new CutoverClosureCommandResult(task.getId(), task.getVersion(), closureId,
                closureVersion, "DRAFT", false);
    }

    private static CutoverClosureApplicationException ownerFailure(CutoverClosureOwnerFactException ex) {
        return switch (ex.code()) {
            case PROVIDER_UNAVAILABLE -> failure(OWNER_PROVIDER_UNAVAILABLE, ex.getMessage());
            case FILE_INVALID, SOURCE_STALE -> failure(FILE_INVALID, ex.getMessage());
            case INVALID_REQUEST -> failure(INVALID_REQUEST, ex.getMessage());
            default -> failure(OWNER_DATA_CORRUPTED, ex.getMessage());
        };
    }

    private static boolean validText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= maxLength;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static long nextId() {
        return ID_GENERATOR.nextId();
    }

    private static CutoverClosureApplicationException failure(CutoverClosureApplicationException.Code code,
                                                               String message) {
        return new CutoverClosureApplicationException(code, message);
    }

    private record InspectedAttachment(CutoverClosureFilePort.FileExpectation expectation,
                                       CutoverClosureFilePort.FileFact fact) {
    }
}
