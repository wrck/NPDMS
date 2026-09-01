package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureAttachmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverCollectionEvidenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceLockQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalTaskQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureAttachmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverCollectionEvidenceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureDraftUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureVersionUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanRevisionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskDeviceListQuery;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.HandleClosureCollectionCallbackCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.LinkClosureManualResultCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.RequestClosureCollectionCommand;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.AttachmentInput;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.ClosureContent;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.CollectionIntentIdentity;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.CollectionRequest;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.DispatchFact;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.SavedCredential;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.TransientCredential;
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
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationException.Code.*;

/** F-CUT-006 Task 3/4应用内核；跨模块Provider接通前不注册生产Bean。 */
public class CutoverClosureApplicationService {
    private static final Snowflake ID_GENERATOR = IdUtil.getSnowflake();
    private static final String ACTION_EDIT = "ACTION_EDIT";

    private final CutoverTaskMapper taskMapper;
    private final CutoverApprovalInstanceMapper approvalMapper;
    private final CutoverPlanRevisionMapper planMapper;
    private final CutoverClosureMapper closureMapper;
    private final CutoverClosureAttachmentMapper attachmentMapper;
    private final CutoverCollectionEvidenceMapper evidenceMapper;
    private final CutoverTaskDeviceScopeMapper deviceScopeMapper;
    private final CutoverProjectScopePort projectScopePort;
    private final CutoverClosureFilePort filePort;
    private final CutoverClosureCollectionPort collectionPort;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final Clock clock;

    public CutoverClosureApplicationService(CutoverTaskMapper taskMapper,
                                            CutoverApprovalInstanceMapper approvalMapper,
                                            CutoverPlanRevisionMapper planMapper,
                                            CutoverClosureMapper closureMapper,
                                            CutoverClosureAttachmentMapper attachmentMapper,
                                            CutoverCollectionEvidenceMapper evidenceMapper,
                                            CutoverTaskDeviceScopeMapper deviceScopeMapper,
                                            CutoverProjectScopePort projectScopePort,
                                            CutoverClosureFilePort filePort,
                                            CutoverClosureCollectionPort collectionPort,
                                            PlatformCommandExecutionApi commandExecutionApi, Clock clock) {
        this.taskMapper = taskMapper;
        this.approvalMapper = approvalMapper;
        this.planMapper = planMapper;
        this.closureMapper = closureMapper;
        this.attachmentMapper = attachmentMapper;
        this.evidenceMapper = evidenceMapper;
        this.deviceScopeMapper = deviceScopeMapper;
        this.projectScopePort = projectScopePort;
        this.filePort = filePort;
        this.collectionPort = collectionPort;
        this.commandExecutionApi = commandExecutionApi;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public CutoverClosureCommandResult requestCollection(RequestClosureCollectionCommand command) {
        requireCollectionCommand(command);
        CollectionIntentIdentity identity = new CollectionIntentIdentity(command.tenantId(), command.taskId(),
                command.closureId(), command.deviceId(), command.collectionStage(), command.idempotencyKey());
        return executeCommand(command.tenantId(), "CUT:CLOSURE_COLLECTION_REQUEST:" + command.taskId(),
                command.actorId(), command.idempotencyKey(), collectionCommandDigest(command), command.correlationId(),
                "CUTOVER_CLOSURE_COLLECTION_REQUEST", () -> requestCollectionNew(command, identity));
    }

    @Transactional(rollbackFor = Exception.class)
    public CutoverClosureCommandResult handleCollectionCallback(HandleClosureCollectionCallbackCommand command) {
        requireCallbackCommand(command);
        return executeCommand(command.tenantId(), "CUT:CLOSURE_COLLECTION_CALLBACK", 0L,
                command.callbackEventId(), sha256(JsonUtils.toJsonString(command)), command.correlationId(),
                "CUTOVER_CLOSURE_COLLECTION_CALLBACK", () -> callbackNew(command));
    }

    @Transactional(rollbackFor = Exception.class)
    public CutoverClosureCommandResult linkManualResult(LinkClosureManualResultCommand command) {
        requireManualCommand(command);
        return executeCommand(command.tenantId(), "CUT:CLOSURE_MANUAL_RESULT:" + command.taskId(),
                command.actorId(), command.idempotencyKey(), sha256(JsonUtils.toJsonString(command)),
                command.correlationId(), "CUTOVER_CLOSURE_MANUAL_RESULT", () -> manualResultNew(command));
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

    private CutoverClosureCommandResult requestCollectionNew(RequestClosureCollectionCommand command,
                                                              CollectionIntentIdentity identity) {
        CutoverTaskDO task = requireP6Task(taskMapper.selectForUpdate(
                new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command.tenantId(),
                command.actorId(), command.expectedTaskVersion());
        CutoverClosureDO closure = requireDraftClosure(command.tenantId(), command.taskId(), command.closureId(),
                command.expectedClosureVersion());
        evidenceMapper.selectListByClosureForUpdate(new CutoverClosureChildrenQuery(command.tenantId(), closure.getId()));
        requireActiveDevice(command.tenantId(), task, command.deviceId());
        lockProjectScope(command.actorId(), task);
        CollectionRequest request = new CollectionRequest(identity, command.actorId(), task.getProjectId(),
                command.authentication(), command.templateCode(), command.templateVersion(), command.correlationId());
        DispatchFact fact;
        try {
            var lookup = collectionPort.inspectByIntent(identity);
            if (lookup.status() == CutoverClosureCollectionPort.LookupStatus.UNKNOWN) {
                throw failure(OWNER_PROVIDER_UNAVAILABLE, "采集Provider状态未知");
            }
            fact = lookup.status() == CutoverClosureCollectionPort.LookupStatus.FOUND
                    ? lookup.fact() : collectionPort.request(request);
        } catch (CutoverClosureOwnerFactException ex) {
            throw ownerFailure(ex);
        }
        if (!Objects.equals(fact.requestDigest(), request.requestDigest())) {
            throw failure(IDEMPOTENCY_CONFLICT, "采集意图载荷冲突");
        }
        insertEvidence(command.tenantId(), closure, task, command.deviceId(), command.collectionStage().name(),
                fact.outcome() == CutoverClosureCollectionPort.DispatchOutcome.ACCEPTED
                        ? "DISPATCH_ACCEPTED" : "DISPATCH_FAILED",
                fact.collectionTaskId(), null, null, null, null, null, fact.occurredAt(), command.actorId());
        advanceClosure(command.tenantId(), closure, command.actorId());
        return result(task, closure.getId(), closure.getVersion() + 1);
    }

    private CutoverClosureCommandResult callbackNew(HandleClosureCollectionCallbackCommand command) {
        CutoverTaskDO task = taskMapper.selectForUpdate(new CutoverTaskRowQuery(command.tenantId(), command.taskId()));
        if (task == null || !Objects.equals(task.getTenantId(), command.tenantId())) throw failure(NOT_FOUND, "任务不存在");
        CutoverClosureDO closure = requireDraftClosure(command.tenantId(), command.taskId(), command.closureId(), null);
        List<CutoverCollectionEvidenceDO> evidence = evidenceMapper.selectListByClosureForUpdate(
                new CutoverClosureChildrenQuery(command.tenantId(), closure.getId()));
        boolean dispatchExists = evidence.stream().anyMatch(value ->
                "DISPATCH_ACCEPTED".equals(value.getEvidenceTypeCode())
                        && Objects.equals(value.getCollectionTaskId(), command.collectionTaskId())
                        && Objects.equals(value.getDeviceId(), command.deviceId())
                        && Objects.equals(value.getCollectionStageCode(), command.collectionStage().name()));
        if (!dispatchExists) throw failure(STATE_CONFLICT, "采集下发事实不匹配");
        insertEvidence(command.tenantId(), closure, task, command.deviceId(), command.collectionStage().name(),
                command.succeeded() ? "CALLBACK_SUCCEEDED" : "CALLBACK_FAILED", command.collectionTaskId(),
                command.callbackEventId(), command.resultRef(), command.resultVersion(), null, null,
                command.occurredAt(), 0L);
        advanceClosure(command.tenantId(), closure, 0L);
        return result(task, closure.getId(), closure.getVersion() + 1);
    }

    private CutoverClosureCommandResult manualResultNew(LinkClosureManualResultCommand command) {
        CutoverTaskDO task = requireP6Task(taskMapper.selectForUpdate(
                new CutoverTaskRowQuery(command.tenantId(), command.taskId())), command.tenantId(),
                command.actorId(), command.expectedTaskVersion());
        CutoverClosureDO closure = requireDraftClosure(command.tenantId(), command.taskId(), command.closureId(),
                command.expectedClosureVersion());
        List<CutoverCollectionEvidenceDO> evidence = evidenceMapper.selectListByClosureForUpdate(
                new CutoverClosureChildrenQuery(command.tenantId(), closure.getId()));
        CutoverCollectionEvidenceDO failed = evidence.stream().filter(value ->
                        Objects.equals(value.getCollectionTaskId(), command.failedCollectionTaskId())
                                && List.of("DISPATCH_FAILED", "CALLBACK_FAILED").contains(value.getEvidenceTypeCode()))
                .findFirst().orElseThrow(() -> failure(STATE_CONFLICT, "失败采集事实不存在"));
        lockProjectScope(command.actorId(), task);
        AttachmentInput input = command.attachment();
        CutoverClosureFilePort.FileExpectation expectation = expectation(command.tenantId(), command.actorId(),
                task.getProjectId(), closure.getId(), input);
        CutoverClosureFilePort.FileFact file;
        try {
            file = filePort.lockAndRevalidate(expectation);
            requireSameFile(expectation, file);
        } catch (CutoverClosureOwnerFactException ex) {
            throw ownerFailure(ex);
        }
        long attachmentId = insertAttachment(command.tenantId(), command.actorId(), closure.getId(), input, file,
                LocalDateTime.now(clock));
        insertEvidence(command.tenantId(), closure, task, failed.getDeviceId(), failed.getCollectionStageCode(),
                "MANUAL_UPLOAD", command.failedCollectionTaskId(), null, null, null,
                command.failedCollectionTaskId(), attachmentId, LocalDateTime.now(clock), command.actorId());
        advanceClosure(command.tenantId(), closure, command.actorId());
        return result(task, closure.getId(), closure.getVersion() + 1);
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
                content.legacyItems(), String.valueOf(command.actorId()), now)) != 1) {
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
            insertAttachment(command.tenantId(), command.actorId(), closureId, inputs.get(index), files.get(index), now);
        }
    }

    private long insertAttachment(Long tenantId, Long actorId, long closureId, AttachmentInput input,
                                  CutoverClosureFilePort.FileFact file, LocalDateTime now) {
        CutoverClosureAttachmentDO row = new CutoverClosureAttachmentDO();
        row.setId(nextId()); row.setTenantId(tenantId); row.setClosureId(closureId);
        row.setPurposeCode(input.purposeCode().name()); row.setReferenceKey(file.referenceKey());
        row.setArtifactId(file.artifactId()); row.setFileVersionNo(file.versionNo());
        row.setFileFactVersion(JsonUtils.toJsonString(file.fileFactVersion()));
        row.setFileScopeVersion(file.scopeVersion()); row.setFileHash(file.sha256()); row.setVersion(0);
        row.setCreator(String.valueOf(actorId)); row.setUpdater(String.valueOf(actorId));
        row.setCreateTime(now); row.setUpdateTime(now); row.setDeleted(false);
        if (attachmentMapper.insert(row) != 1) throw failure(STATE_CONFLICT, "闭环附件写入失败");
        return row.getId();
    }

    private void insertEvidence(Long tenantId, CutoverClosureDO closure, CutoverTaskDO task, Long deviceId,
                                String stage, String type, String collectionTaskId, String callbackEventId,
                                String resultRef, String resultVersion, String originalFailedTaskId,
                                Long manualAttachmentId, LocalDateTime occurredAt, Long actorId) {
        CutoverCollectionEvidenceDO row = new CutoverCollectionEvidenceDO();
        row.setId(nextId()); row.setTenantId(tenantId); row.setClosureId(closure.getId()); row.setTaskId(task.getId());
        row.setProjectId(task.getProjectId()); row.setDeviceId(deviceId); row.setCollectionStageCode(stage);
        row.setEvidenceTypeCode(type); row.setCollectionTaskId(collectionTaskId);
        row.setCallbackEventId(callbackEventId); row.setResultRef(resultRef); row.setResultVersion(resultVersion);
        row.setOriginalFailedCollectionTaskId(originalFailedTaskId); row.setManualAttachmentId(manualAttachmentId);
        row.setOccurredAt(occurredAt); row.setRecordedBy(actorId); row.setCreator(String.valueOf(actorId));
        row.setCreateTime(LocalDateTime.now(clock)); row.setDeleted(false);
        if (evidenceMapper.insert(row) != 1) throw failure(STATE_CONFLICT, "采集证据写入失败");
    }

    private void advanceClosure(Long tenantId, CutoverClosureDO closure, Long actorId) {
        if (closureMapper.advanceDraftVersionIfMatch(new CutoverClosureVersionUpdate(tenantId, closure.getId(),
                closure.getVersion(), String.valueOf(actorId), LocalDateTime.now(clock))) != 1) {
            throw failure(CLOSURE_VERSION_STALE, "闭环版本已变化");
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
        return expectation(command.tenantId(), command.actorId(), task.getProjectId(), closureId, input);
    }

    private static CutoverClosureFilePort.FileExpectation expectation(Long tenantId, Long actorId, Long projectId,
                                                                       long closureId, AttachmentInput input) {
        return new CutoverClosureFilePort.FileExpectation(tenantId, actorId, projectId,
                closureId, input.purposeCode(), input.artifactId(), input.versionNo(), input.referenceKey(),
                input.fileFactVersion(), input.scopeVersion(), input.sha256());
    }

    private void lockProjectScope(SaveCutoverClosureCommand command, CutoverTaskDO task) {
        lockProjectScope(command.actorId(), task);
    }

    private void lockProjectScope(Long actorId, CutoverTaskDO task) {
        try {
            CutoverProjectScopePort.ProjectScopeFact fact = projectScopePort.lockAndRevalidate(actorId,
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

    private CutoverClosureDO requireDraftClosure(Long tenantId, Long taskId, Long closureId,
                                                  Integer expectedVersion) {
        CutoverClosureDO closure = closureMapper.selectByTaskForUpdate(new CutoverClosureRowQuery(tenantId, taskId));
        if (closure == null || !Objects.equals(closure.getId(), closureId)) throw failure(NOT_FOUND, "闭环不存在");
        if (!"DRAFT".equals(closure.getStatusCode())) throw failure(STATE_CONFLICT, "闭环已归档");
        if (expectedVersion != null && !Objects.equals(closure.getVersion(), expectedVersion)) {
            throw failure(CLOSURE_VERSION_STALE, "闭环版本已变化");
        }
        return closure;
    }

    private CutoverTaskDO requireP6Task(CutoverTaskDO task, Long tenantId, Long actorId, Integer expectedVersion) {
        if (task == null || !Objects.equals(task.getTenantId(), tenantId)) throw failure(NOT_FOUND, "任务不存在");
        if (!"NEW_PLATFORM".equals(task.getTaskOrigin()) || !"P6".equals(task.getCurrentStage())
                || !"CLOSURE_IN_PROGRESS".equals(task.getTaskStatus())) throw failure(STATE_CONFLICT, "任务不在P6闭环中");
        if (!Objects.equals(task.getOwnerUserId(), actorId)) throw failure(NOT_FOUND, "任务不可见");
        if (!Objects.equals(task.getVersion(), expectedVersion)) throw failure(TASK_VERSION_STALE, "任务版本已变化");
        return task;
    }

    private void requireActiveDevice(Long tenantId, CutoverTaskDO task, Long deviceId) {
        List<CutoverTaskDeviceScopeDO> devices = deviceScopeMapper.selectActiveByTaskForUpdate(
                new CutoverTaskDeviceListQuery(tenantId, task.getId()));
        if (devices.stream().noneMatch(value -> Objects.equals(value.getDeviceId(), deviceId)
                && Objects.equals(value.getProjectId(), task.getProjectId()))) {
            throw failure(SOURCE_STALE, "设备不在任务冻结范围");
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
        row.setFinalResultCode(null);
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

    private static void requireCollectionCommand(RequestClosureCollectionCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() <= 0
                || command.actorId() == null || command.actorId() <= 0 || command.taskId() == null || command.taskId() <= 0
                || command.expectedTaskVersion() == null || command.expectedTaskVersion() < 0
                || command.closureId() == null || command.closureId() <= 0
                || command.expectedClosureVersion() == null || command.expectedClosureVersion() < 0
                || command.deviceId() == null || command.deviceId() <= 0 || command.collectionStage() == null
                || command.authentication() == null || command.templateVersion() == null || command.templateVersion() < 0
                || !validText(command.templateCode(), 64) || !validText(command.idempotencyKey(), 128)
                || !validText(command.correlationId(), 128)) {
            throw failure(INVALID_REQUEST, "采集请求非法");
        }
    }

    private static void requireCallbackCommand(HandleClosureCollectionCallbackCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() <= 0
                || command.taskId() == null || command.taskId() <= 0 || command.closureId() == null || command.closureId() <= 0
                || command.deviceId() == null || command.deviceId() <= 0 || command.collectionStage() == null
                || !validText(command.callbackEventId(), 128) || !validText(command.collectionTaskId(), 128)
                || !validText(command.resultRef(), 256) || !validText(command.resultVersion(), 128)
                || command.occurredAt() == null || !validText(command.correlationId(), 128)) {
            throw failure(INVALID_REQUEST, "采集回调非法");
        }
    }

    private static void requireManualCommand(LinkClosureManualResultCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() <= 0
                || command.actorId() == null || command.actorId() <= 0 || command.taskId() == null || command.taskId() <= 0
                || command.expectedTaskVersion() == null || command.expectedTaskVersion() < 0
                || command.closureId() == null || command.closureId() <= 0
                || command.expectedClosureVersion() == null || command.expectedClosureVersion() < 0
                || !validText(command.failedCollectionTaskId(), 128) || command.attachment() == null
                || command.attachment().purposeCode() != CutoverClosureRules.AttachmentPurpose.MANUAL_COLLECTION_RESULT
                || !validText(command.idempotencyKey(), 128) || !validText(command.correlationId(), 128)) {
            throw failure(INVALID_REQUEST, "人工采集结果非法");
        }
        try {
            AttachmentInput input = command.attachment();
            CutoverClosureRules.positive(input.artifactId(), "artifactId");
            CutoverClosureRules.positive(input.versionNo(), "versionNo");
            CutoverClosureRules.normalizedText(input.referenceKey(), 128, "referenceKey");
            CutoverClosureRules.requireValue(input.fileFactVersion(), "fileFactVersion");
            CutoverClosureRules.nonNegative(input.scopeVersion(), "scopeVersion");
            CutoverClosureRules.sha256(input.sha256());
        } catch (CutoverClosureOwnerFactException ex) {
            throw failure(INVALID_REQUEST, ex.getMessage());
        }
    }

    private CutoverClosureCommandResult executeCommand(Long tenantId, String scopeCode, Long actorId,
                                                        String idempotencyKey, String digest, String correlationId,
                                                        String action, Supplier<CutoverClosureCommandResult> operation) {
        PlatformCommandExecutionApi.ExecutionResult<CutoverClosureCommandResult> execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(tenantId, scopeCode, actorId, idempotencyKey),
                digest, CutoverClosureCommandResult.class, operation,
                result -> new PlatformCommandExecutionApi.SuccessFacts(action, "CutoverClosure",
                        String.valueOf(result.closureId()), correlationId, JsonUtils.toJsonString(result), List.of()));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw failure(IDEMPOTENCY_CONFLICT, "幂等键载荷冲突");
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null) {
            throw failure(IDEMPOTENCY_IN_PROGRESS, "闭环命令处理中");
        }
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().replayedCopy() : execution.response();
    }

    private static String collectionCommandDigest(RequestClosureCollectionCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("taskId", command.taskId()); value.put("taskVersion", command.expectedTaskVersion());
        value.put("closureId", command.closureId()); value.put("closureVersion", command.expectedClosureVersion());
        value.put("deviceId", command.deviceId()); value.put("collectionStage", command.collectionStage());
        value.put("templateCode", command.templateCode()); value.put("templateVersion", command.templateVersion());
        value.put("authenticationMode", command.authentication().mode());
        if (command.authentication() instanceof SavedCredential saved) {
            value.put("credentialId", saved.credentialId()); value.put("credentialVersion", saved.credentialVersion());
        } else if (command.authentication() instanceof TransientCredential transientCredential) {
            value.put("loginName", transientCredential.loginName());
            value.put("saveAsCredential", transientCredential.saveAsCredential());
        }
        return sha256(JsonUtils.toJsonString(value));
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
            case COLLECTION_INVALID -> failure(COLLECTION_INVALID, ex.getMessage());
            case IDEMPOTENCY_CONFLICT -> failure(IDEMPOTENCY_CONFLICT, ex.getMessage());
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
