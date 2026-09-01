package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureAttachmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverCollectionEvidenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalTaskQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureAttachmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverCollectionEvidenceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanRevisionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.AttachmentInput;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.ClosureContent;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.AttachmentPurpose;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort.FileFactVersion;
import cn.iocoder.yudao.module.pms.cutover.service.closure.view.CutoverClosureView;
import cn.iocoder.yudao.module.pms.cutover.service.closure.view.CutoverClosureView.CollectionEvidenceView;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverOwnerFactException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationException.Code.NOT_FOUND;
import static cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationException.Code.OWNER_DATA_CORRUPTED;
import static cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationException.Code.OWNER_PROVIDER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationException.Reason.*;

/** F-CUT-006 Task 3只读详情内核；生产依赖接通前不注册Bean。 */
public class CutoverClosureQueryService {
    private final CutoverTaskMapper taskMapper;
    private final CutoverApprovalInstanceMapper approvalMapper;
    private final CutoverPlanRevisionMapper planMapper;
    private final CutoverClosureMapper closureMapper;
    private final CutoverClosureAttachmentMapper attachmentMapper;
    private final CutoverCollectionEvidenceMapper evidenceMapper;
    private final CutoverProjectScopePort projectScopePort;

    public CutoverClosureQueryService(CutoverTaskMapper taskMapper,
                                      CutoverApprovalInstanceMapper approvalMapper,
                                      CutoverPlanRevisionMapper planMapper,
                                      CutoverClosureMapper closureMapper,
                                      CutoverClosureAttachmentMapper attachmentMapper,
                                      CutoverCollectionEvidenceMapper evidenceMapper,
                                      CutoverProjectScopePort projectScopePort) {
        this.taskMapper = taskMapper;
        this.approvalMapper = approvalMapper;
        this.planMapper = planMapper;
        this.closureMapper = closureMapper;
        this.attachmentMapper = attachmentMapper;
        this.evidenceMapper = evidenceMapper;
        this.projectScopePort = projectScopePort;
    }

    public CutoverClosureView detail(Long tenantId, Long actorId, Long taskId, ClosureAccess access) {
        if (tenantId == null || tenantId <= 0 || actorId == null || actorId <= 0 || taskId == null || taskId <= 0
                || access == null) throw notFound();
        CutoverTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getTenantId(), tenantId)) throw notFound();
        CutoverProjectScopePort.ProjectScopeFact viewScope = inspectScope(actorId, task.getProjectId(), "ACTION_VIEW");
        if (!viewScope.allowed()) throw notFound();

        CutoverClosureDO closure = closureMapper.selectByTask(new CutoverClosureRowQuery(tenantId, taskId));
        CutoverPlanRevisionDO plan;
        CutoverApprovalInstanceDO approval;
        if (closure == null) {
            plan = planMapper.selectCurrent(new CutoverPlanRevisionQuery(tenantId, taskId, null));
            approval = approvalMapper.selectCurrentByTask(new ApprovalTaskQuery(tenantId, taskId));
        } else {
            plan = planMapper.selectById(closure.getPlanRevisionId());
            approval = approvalMapper.selectById(closure.getApprovalInstanceId());
        }
        requireSource(task, closure, plan, approval);
        List<CutoverClosureAttachmentDO> attachments = closure == null ? List.of()
                : attachmentMapper.selectListByClosure(new CutoverClosureChildrenQuery(tenantId, closure.getId()));
        List<CutoverCollectionEvidenceDO> evidence = closure == null ? List.of()
                : evidenceMapper.selectListByClosure(new CutoverClosureChildrenQuery(tenantId, closure.getId()));
        boolean editAllowed = inspectEdit(actorId, task.getProjectId());
        List<String> actions = allowedActions(task, closure, attachments, evidence, access, editAllowed, actorId);
        return new CutoverClosureView(taskId, task.getCurrentStage(), task.getTaskStatus(), task.getVersion(),
                closure == null ? null : closure.getId(), closure == null ? null : closure.getVersion(),
                closure == null ? null : closure.getStatusCode(), approval.getId(), approval.getVersion(),
                plan.getId(), plan.getRevisionNo(), plan.getVersion(),
                closure == null ? null : content(closure, attachments), evidence.stream()
                .map(value -> evidence(value, attachments)).toList(),
                closure == null ? null : closure.getResultRef(), closure == null ? null : closure.getSubmittedBy(),
                closure == null ? null : closure.getSubmittedAt(), closure == null ? null : closure.getArchivedAt(),
                actions);
    }

    private List<String> allowedActions(CutoverTaskDO task, CutoverClosureDO closure,
                                        List<CutoverClosureAttachmentDO> attachments,
                                        List<CutoverCollectionEvidenceDO> evidence,
                                        ClosureAccess access, boolean editAllowed, Long actorId) {
        List<String> result = new ArrayList<>();
        boolean ownerP6 = "NEW_PLATFORM".equals(task.getTaskOrigin()) && "P6".equals(task.getCurrentStage())
                && "CLOSURE_IN_PROGRESS".equals(task.getTaskStatus())
                && Objects.equals(task.getOwnerUserId(), actorId) && editAllowed;
        if (ownerP6 && closure == null && access.save()) result.add("CREATE_CLOSURE");
        if (ownerP6 && closure != null && "DRAFT".equals(closure.getStatusCode())) {
            if (access.save()) result.add("SAVE_CLOSURE");
            if (access.requestCollection()) result.add("REQUEST_COLLECTION");
            if (access.save() && evidence.stream().anyMatch(value -> "DISPATCH_FAILED".equals(value.getEvidenceTypeCode())
                    || "CALLBACK_FAILED".equals(value.getEvidenceTypeCode()))) result.add("LINK_MANUAL_RESULT");
            if (access.submit() && complete(closure, attachments, evidence)) result.add("SUBMIT_CLOSURE");
        }
        return List.copyOf(result);
    }

    private static boolean complete(CutoverClosureDO closure, List<CutoverClosureAttachmentDO> attachments,
                                    List<CutoverCollectionEvidenceDO> evidence) {
        try {
            ClosureContent content = content(closure, attachments);
            CutoverClosureRules.validateDraftContent(content);
            if (content.preCheckNormal() == null || content.executionNormal() == null || content.testNormal() == null
                    || content.rollbackOccurred() == null) return false;
            long checklist = attachments.stream().filter(value ->
                    "POST_COLLECTION_CHECKLIST".equals(value.getPurposeCode())).count();
            long commitment = attachments.stream().filter(value ->
                    "IMPLEMENTATION_COMMITMENT".equals(value.getPurposeCode())).count();
            if (checklist != 1 || commitment != 1) return false;
            return evidence.stream().filter(value -> "DISPATCH_ACCEPTED".equals(value.getEvidenceTypeCode()))
                    .allMatch(dispatch -> evidence.stream().anyMatch(callback ->
                            Objects.equals(callback.getCollectionTaskId(), dispatch.getCollectionTaskId())
                                    && List.of("CALLBACK_SUCCEEDED", "CALLBACK_FAILED")
                                    .contains(callback.getEvidenceTypeCode())));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static ClosureContent content(CutoverClosureDO closure,
                                          List<CutoverClosureAttachmentDO> attachments) {
        return new ClosureContent(closure.getPreCheckNormal(), closure.getPreCheckDetail(),
                closure.getExecutionNormal(), closure.getExecutionDetail(), closure.getTestNormal(),
                closure.getTestDetail(), closure.getRollbackOccurred(), closure.getRollbackSuccessful(),
                closure.getRollbackReason(), closure.getLegacyItems(), closure.getFinalResultCode(),
                attachments.stream().filter(value -> !"MANUAL_COLLECTION_RESULT".equals(value.getPurposeCode()))
                        .map(CutoverClosureQueryService::attachment).toList());
    }

    private static AttachmentInput attachment(CutoverClosureAttachmentDO row) {
        FileFactVersion version;
        try {
            version = JsonUtils.parseObject(row.getFileFactVersion(), FileFactVersion.class);
        } catch (RuntimeException ex) {
            throw failure(OWNER_DATA_CORRUPTED, OWNER_FACT_CORRUPTED, "PLT", "闭环文件版本损坏");
        }
        return new AttachmentInput(AttachmentPurpose.valueOf(row.getPurposeCode()), row.getArtifactId(),
                row.getFileVersionNo(), row.getReferenceKey(), version, row.getFileScopeVersion(), row.getFileHash());
    }

    private CollectionEvidenceView evidence(CutoverCollectionEvidenceDO row,
                                            List<CutoverClosureAttachmentDO> attachments) {
        AttachmentInput manualFile = null;
        if (row.getManualAttachmentId() != null) {
            CutoverClosureAttachmentDO attachment = attachments.stream()
                    .filter(value -> Objects.equals(value.getId(), row.getManualAttachmentId()))
                    .findFirst().orElseThrow(() -> failure(OWNER_DATA_CORRUPTED,
                            OWNER_FACT_CORRUPTED, "PLT", "人工采集附件事实损坏"));
            manualFile = attachment(attachment);
        }
        return new CollectionEvidenceView(row.getId(), row.getDeviceId(), row.getCollectionStageCode(),
                row.getEvidenceTypeCode(), row.getCollectionTaskId(), row.getCallbackEventId(), row.getResultRef(),
                row.getResultVersion(), row.getOriginalFailedCollectionTaskId(), manualFile,
                row.getOccurredAt());
    }

    private boolean inspectEdit(Long actorId, Long projectId) {
        return inspectScope(actorId, projectId, "ACTION_EDIT").allowed();
    }

    private CutoverProjectScopePort.ProjectScopeFact inspectScope(Long actorId, Long projectId, String action) {
        try {
            CutoverProjectScopePort.ProjectScopeFact fact = projectScopePort.inspect(actorId, projectId, action);
            if (fact == null || !Objects.equals(fact.projectId(), projectId)) {
                throw failure(OWNER_DATA_CORRUPTED, OWNER_FACT_CORRUPTED,
                        "PROJ", "项目范围事实身份损坏");
            }
            return fact;
        } catch (CutoverOwnerFactException ex) {
            throw switch (ex.code()) {
                case PROVIDER_UNAVAILABLE -> failure(OWNER_PROVIDER_UNAVAILABLE,
                        PROJECT_SCOPE_PROVIDER_UNAVAILABLE, "PROJ", "项目范围Provider不可用");
                case DATA_SCOPE_FORBIDDEN -> notFound();
                case INVALID_FACT, STALE -> failure(OWNER_DATA_CORRUPTED,
                        OWNER_FACT_CORRUPTED, "PROJ", "项目范围事实损坏");
            };
        } catch (CutoverClosureApplicationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw failure(OWNER_DATA_CORRUPTED, OWNER_FACT_CORRUPTED,
                    "PROJ", "项目范围事实损坏");
        }
    }

    private static void requireSource(CutoverTaskDO task, CutoverClosureDO closure,
                                      CutoverPlanRevisionDO plan, CutoverApprovalInstanceDO approval) {
        if (plan == null || approval == null || !"SUBMITTED".equals(plan.getStatusCode())
                || !"APPROVED".equals(approval.getStatusCode())
                || !Objects.equals(plan.getId(), approval.getPlanRevisionId())
                || !Objects.equals(plan.getApprovalInstanceId(), approval.getId())
                || plan.getApprovalVersion() == null || approval.getVersion() == null
                || plan.getApprovalVersion() > approval.getVersion()
                || !Objects.equals(plan.getCutoverTaskId(), task.getId())) {
            throw failure(OWNER_DATA_CORRUPTED, OWNER_FACT_CORRUPTED,
                    "CUT", "P6审批方案事实损坏");
        }
        if (closure != null && (!Objects.equals(closure.getPlanRevisionId(), plan.getId())
                || !Objects.equals(closure.getTaskId(), task.getId())
                || !Objects.equals(closure.getProjectId(), task.getProjectId())
                || !Objects.equals(closure.getTaskVersionAtP6(), task.getVersion())
                || !Objects.equals(closure.getApprovalInstanceId(), approval.getId())
                || !Objects.equals(closure.getApprovalVersion(), approval.getVersion())
                || !Objects.equals(closure.getPlanRevisionNo(), plan.getRevisionNo())
                || !Objects.equals(closure.getPlanVersion(), plan.getVersion())
                || !Objects.equals(closure.getDeviceScopeWatermark(), task.getDeviceScopeWatermark()))) {
            throw failure(OWNER_DATA_CORRUPTED, OWNER_FACT_CORRUPTED,
                    "CUT", "闭环冻结来源损坏");
        }
    }

    private static CutoverClosureApplicationException notFound() {
        return failure(NOT_FOUND, TASK_OR_CLOSURE_NOT_VISIBLE, null, "任务或闭环不可见");
    }

    private static CutoverClosureApplicationException failure(CutoverClosureApplicationException.Code code,
                                                               CutoverClosureApplicationException.Reason reason,
                                                               String ownerContext, String message) {
        return new CutoverClosureApplicationException(code, reason, ownerContext, null, null, message);
    }

    public record ClosureAccess(boolean save, boolean requestCollection, boolean submit) {
    }
}
