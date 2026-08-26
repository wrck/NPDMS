package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanChangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeVersionUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanVersionUpdate;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_STATUS_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_VERSION_NOT_MATCH;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.DURATION_CHANGE_BPM_ASSOCIATION_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.DURATION_CHANGE_FILE_ARTIFACT_UNAVAILABLE;

/** 将匹配的BPM终态原子投影为SOL工期事实。 */
@Service
@RequiredArgsConstructor
public class DurationChangeBpmResultService {

    private static final String OPERATION = "DURATION_CHANGE_BPM_RESULT";

    private final ConstructionPlanMapper planMapper;
    private final ConstructionPlanRevisionMapper revisionMapper;
    private final ConstructionPlanChangeMapper changeMapper;
    private final DurationChangeBpmAuthorizationGuard authorizationGuard;
    private final OperationAuditApi operationAuditApi;

    @Transactional(rollbackFor = Exception.class)
    public void handle(String processInstanceId, Integer status, String reason) {
        TerminalResult result = TerminalResult.from(status);
        if (result == null) {
            return;
        }
        if (result == TerminalResult.REJECT && (reason == null || reason.isBlank())) {
            throw exception(CONSTRUCTION_PLAN_STATUS_INVALID);
        }
        var authorization = authorizationGuard.authorize(processInstanceId, result);
        ConstructionPlanDO plan = planMapper.selectForUpdate(new ConstructionPlanLockQuery(
                authorization.tenantId(), authorization.planId()));
        ConstructionPlanChangeDO change = changeMapper.selectForUpdate(new ConstructionPlanChangeLockQuery(
                authorization.tenantId(), authorization.planId(), authorization.changeId()));
        if (plan == null || change == null
                || !Objects.equals(change.getProcessInstanceId(), processInstanceId)
                || !Objects.equals(plan.getProjectId(), authorization.projectId())) {
            throw exception(DURATION_CHANGE_BPM_ASSOCIATION_INVALID);
        }
        if (!ConstructionPlanChangeDO.STATUS_PENDING_APPROVAL.equals(change.getStatusCode())) {
            if (isTerminal(change.getStatusCode())) {
                return;
            }
            throw exception(CONSTRUCTION_PLAN_STATUS_INVALID);
        }
        if (!Objects.equals(plan.getPendingChangeId(), change.getId())) {
            throw exception(DURATION_CHANGE_BPM_ASSOCIATION_INVALID);
        }
        ConstructionPlanRevisionDO candidate = revisionMapper.selectForUpdate(
                new ConstructionPlanRevisionLockQuery(authorization.tenantId(), plan.getId(),
                        change.getCandidateRevisionId()));
        if (candidate == null || candidate.getFrozenAt() == null
                || !Objects.equals(candidate.getSourceChangeId(), change.getId())) {
            throw exception(DURATION_CHANGE_BPM_ASSOCIATION_INVALID);
        }
        if (Boolean.TRUE.equals(change.getCustomerEvidenceRequired())) {
            throw exception(DURATION_CHANGE_FILE_ARTIFACT_UNAVAILABLE);
        }

        Long currentBefore = plan.getCurrentDurationRevisionId();
        String recalculationBefore = plan.getPlanRecalculationStatusCode();
        Long recalculationSourceBefore = plan.getPlanRecalculationSourceRevisionId();
        if (result == TerminalResult.APPROVE
                && !Objects.equals(currentBefore, change.getBaseRevisionId())) {
            throw exception(CONSTRUCTION_PLAN_VERSION_NOT_MATCH);
        }
        Long currentAfter = result == TerminalResult.APPROVE
                ? change.getCandidateRevisionId() : currentBefore;
        String recalculationAfter = result == TerminalResult.APPROVE
                ? ConstructionPlanDO.RECALCULATION_PENDING : recalculationBefore;
        Long recalculationSourceAfter = result == TerminalResult.APPROVE
                ? change.getCandidateRevisionId() : recalculationSourceBefore;
        String changeStatusAfter = result.changeStatus();
        LocalDateTime decidedAt = LocalDateTime.now();

        if (changeMapper.updateVersionIfMatch(new ConstructionPlanChangeVersionUpdate(
                authorization.tenantId(), plan.getId(), change.getId(), change.getVersion(),
                changeStatusAfter, change.getReasonTypeCode(), change.getReasonDetail(),
                change.getCustomerEvidenceRequired(), change.getCustomerEvidenceFileId(),
                change.getCustomerEvidenceFileVersion(), change.getProcessDefinitionKey(),
                change.getProcessInstanceId(), change.getSubmittedAt(), change.getApproverUserId(),
                result == TerminalResult.APPROVE ? decidedAt : null, reason)) != 1) {
            throw exception(CONSTRUCTION_PLAN_VERSION_NOT_MATCH);
        }
        if (planMapper.updateVersionIfMatch(new ConstructionPlanVersionUpdate(
                authorization.tenantId(), plan.getId(), plan.getVersion(), currentAfter, null,
                recalculationAfter, recalculationSourceAfter,
                String.valueOf(authorization.actorId()))) != 1) {
            throw exception(CONSTRUCTION_PLAN_VERSION_NOT_MATCH);
        }
        recordSuccess(processInstanceId, reason, result, authorization, plan, change,
                currentBefore, currentAfter, recalculationBefore, recalculationAfter,
                recalculationSourceBefore, recalculationSourceAfter, decidedAt);
    }

    private void recordSuccess(String processInstanceId, String reason, TerminalResult result,
                               DurationChangeBpmAuthorizationGuard.AuthorizationContext authorization,
                               ConstructionPlanDO plan, ConstructionPlanChangeDO change,
                               Long currentBefore, Long currentAfter,
                               String recalculationBefore, String recalculationAfter,
                               Long recalculationSourceBefore, Long recalculationSourceAfter,
                               LocalDateTime decidedAt) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", authorization.projectId());
        detail.put("projectVersion", authorization.projectVersion());
        detail.put("planId", plan.getId());
        detail.put("changeId", change.getId());
        detail.put("baseRevisionId", change.getBaseRevisionId());
        detail.put("candidateRevisionId", change.getCandidateRevisionId());
        detail.put("processInstanceId", processInstanceId);
        detail.put("bpmResult", result.name());
        detail.put("changeStatusBefore", ConstructionPlanChangeDO.STATUS_PENDING_APPROVAL);
        detail.put("changeStatusAfter", result.changeStatus());
        detail.put("currentRevisionIdBefore", currentBefore);
        detail.put("currentRevisionIdAfter", currentAfter);
        detail.put("pendingChangeIdBefore", change.getId());
        detail.put("pendingChangeIdAfter", "NONE");
        detail.put("planRecalculationStatusBefore", recalculationBefore);
        detail.put("planRecalculationStatusAfter", recalculationAfter);
        detail.put("planRecalculationSourceRevisionIdBefore", auditValue(recalculationSourceBefore));
        detail.put("planRecalculationSourceRevisionIdAfter", auditValue(recalculationSourceAfter));
        detail.put("opinion", reason == null ? "NONE" : reason);
        detail.put("decidedAt", decidedAt.toString());
        detail.put("changeVersionAfter", change.getVersion() + 1);
        detail.put("planVersionAfter", plan.getVersion() + 1);
        operationAuditApi.record(authorization.tenantId(), authorization.actorId(),
                "bpm:" + processInstanceId + ":" + result.name(), OPERATION,
                "ConstructionPlanChange", String.valueOf(change.getId()), "SUCCESS",
                Map.copyOf(detail));
    }

    private Object auditValue(Object value) {
        return value == null ? "NONE" : value;
    }

    private boolean isTerminal(String status) {
        return ConstructionPlanChangeDO.STATUS_APPROVED.equals(status)
                || ConstructionPlanChangeDO.STATUS_REJECTED.equals(status)
                || ConstructionPlanChangeDO.STATUS_WITHDRAWN.equals(status);
    }

    enum TerminalResult {
        APPROVE(ConstructionPlanChangeDO.STATUS_APPROVED),
        REJECT(ConstructionPlanChangeDO.STATUS_REJECTED),
        CANCEL(ConstructionPlanChangeDO.STATUS_WITHDRAWN);

        private final String changeStatus;

        TerminalResult(String changeStatus) {
            this.changeStatus = changeStatus;
        }

        String changeStatus() {
            return changeStatus;
        }

        static TerminalResult from(Integer status) {
            if (Objects.equals(status, BpmProcessInstanceStatusEnum.APPROVE.getStatus())) return APPROVE;
            if (Objects.equals(status, BpmProcessInstanceStatusEnum.REJECT.getStatus())) return REJECT;
            if (Objects.equals(status, BpmProcessInstanceStatusEnum.CANCEL.getStatus())) return CANCEL;
            return null;
        }
    }

}
