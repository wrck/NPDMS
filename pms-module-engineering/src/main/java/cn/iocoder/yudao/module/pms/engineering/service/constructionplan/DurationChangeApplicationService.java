package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanChangeRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanRevisionRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanChangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeDraftUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionDraftUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionLockQuery;
import cn.iocoder.yudao.module.pms.engineering.domain.constructionplan.DurationRules;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.CreateDurationChangeCommand;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.PatchDurationChangeCommand;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.patch.DurationChangePatch;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_ARGUMENT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_PROJECT_FACT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_STATUS_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_VERSION_NOT_MATCH;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.DURATION_CHANGE_NOT_EXISTS;

@Service
@RequiredArgsConstructor
public class DurationChangeApplicationService {

    private static final String CREATE_SCOPE = "POST:/api/v1/pms/construction-plans/{id}/changes";
    private static final String CREATE_OPERATION = "DURATION_CHANGE_DRAFT_CREATE";
    private static final String PATCH_OPERATION = "DURATION_CHANGE_DRAFT_PATCH";
    private static final Set<String> DURATION_FIELDS = Set.of(
            "calculationBasis", "startDate", "endDate", "durationDays");

    private final ConstructionPlanMapper planMapper;
    private final ConstructionPlanRevisionMapper revisionMapper;
    private final ConstructionPlanChangeMapper changeMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final TransactionTemplate transactionTemplate;

    public ConstructionPlanChangeRespVO createDraft(
            CreateDurationChangeCommand command, ConstructionPlanApplicationService.Actor actor) {
        try {
            return transactionTemplate.execute(status -> createDraftInTransaction(command, actor));
        } catch (RuntimeException failure) {
            auditRejected(CREATE_OPERATION, command == null ? null : command.planId(), null,
                    command == null ? null : command.expectedPlanVersion(), actor, failure);
            throw failure;
        }
    }

    public ConstructionPlanChangeRespVO patchDraft(
            PatchDurationChangeCommand command, ConstructionPlanApplicationService.Actor actor) {
        try {
            return transactionTemplate.execute(status -> patchDraftInTransaction(command, actor));
        } catch (RuntimeException failure) {
            auditRejected(PATCH_OPERATION, command == null ? null : command.planId(),
                    command == null ? null : command.changeId(),
                    null, actor, failure);
            throw failure;
        }
    }

    private ConstructionPlanChangeRespVO createDraftInTransaction(
            CreateDurationChangeCommand command, ConstructionPlanApplicationService.Actor actor) {
        validateCreate(command, actor);
        requireManageAndProject(command.planId(), command.expectedProjectVersion(), actor);
        ConstructionPlanDO plan = lockPlan(command.planId(), command.expectedPlanVersion(), actor.tenantId());
        DurationRules.ResolvedDuration duration = resolveDuration(command.calculationBasis(),
                command.startDate(), command.endDate(), command.durationDays());
        String reasonType = requiredCode(command.reasonType());
        String reasonDetail = normalizeDetail(command.reasonDetail());
        Evidence evidence = evidence(command.customerEvidenceFileId(), command.customerEvidenceFileVersion());

        var execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), CREATE_SCOPE, actor.actorId(), command.idempotencyKey()),
                command.requestDigest(), ConstructionPlanChangeRespVO.class,
                () -> createOnce(plan, duration, reasonType, reasonDetail, evidence, actor),
                response -> createSuccessFacts(plan, response, actor));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw exception(CONSTRUCTION_PLAN_STATUS_INVALID);
        }
        return execution.response();
    }

    private ConstructionPlanChangeRespVO createOnce(
            ConstructionPlanDO plan, DurationRules.ResolvedDuration duration,
            String reasonType, String reasonDetail, Evidence evidence,
            ConstructionPlanApplicationService.Actor actor) {
        ConstructionPlanRevisionDO latest = revisionMapper.selectLatestForUpdate(
                new ConstructionPlanLockQuery(actor.tenantId(), plan.getId()));
        if (latest == null || plan.getCurrentDurationRevisionId() == null) {
            throw exception(CONSTRUCTION_PLAN_STATUS_INVALID);
        }
        long changeId = IdWorker.getId();
        ConstructionPlanRevisionDO candidate = new ConstructionPlanRevisionDO();
        candidate.setId(IdWorker.getId());
        candidate.setTenantId(actor.tenantId());
        candidate.setPlanId(plan.getId());
        candidate.setRevisionNo(latest.getRevisionNo() + 1);
        candidate.setCalculationBasisCode(duration.calculationBasisCode());
        candidate.setStartDate(duration.startDate());
        candidate.setEndDate(duration.endDate());
        candidate.setDurationDays(duration.durationDays());
        candidate.setSourceChangeId(changeId);
        candidate.setCreatedBy(actor.actorId());
        candidate.setCreatedAt(LocalDateTime.now());
        candidate.setVersion(0);
        if (revisionMapper.insert(candidate) != 1) {
            throw new IllegalStateException("DURATION_CHANGE_CANDIDATE_WRITE_FAILED");
        }

        ConstructionPlanChangeDO change = new ConstructionPlanChangeDO();
        change.setId(changeId);
        change.setTenantId(actor.tenantId());
        change.setPlanId(plan.getId());
        change.setBaseRevisionId(plan.getCurrentDurationRevisionId());
        change.setCandidateRevisionId(candidate.getId());
        change.setStatusCode(ConstructionPlanChangeDO.STATUS_DRAFT);
        change.setReasonTypeCode(reasonType);
        change.setReasonDetail(reasonDetail);
        change.setCustomerEvidenceRequired(false);
        change.setCustomerEvidenceFileId(evidence.fileId());
        change.setCustomerEvidenceFileVersion(evidence.fileVersion());
        change.setApplicantUserId(actor.actorId());
        change.setCreatedAt(candidate.getCreatedAt());
        change.setVersion(0);
        if (changeMapper.insert(change) != 1) {
            throw new IllegalStateException("DURATION_CHANGE_DRAFT_WRITE_FAILED");
        }
        return response(change, candidate);
    }

    private ConstructionPlanChangeRespVO patchDraftInTransaction(
            PatchDurationChangeCommand command, ConstructionPlanApplicationService.Actor actor) {
        validatePatch(command, actor);
        requireManageAndProject(command.planId(), command.expectedProjectVersion(), actor);
        ConstructionPlanDO plan = lockPlan(command.planId(), actor.tenantId());
        ConstructionPlanChangeDO change = changeMapper.selectForUpdate(new ConstructionPlanChangeLockQuery(
                actor.tenantId(), plan.getId(), command.changeId()));
        if (change == null) throw exception(DURATION_CHANGE_NOT_EXISTS);
        if (!ConstructionPlanChangeDO.STATUS_DRAFT.equals(change.getStatusCode())
                || !change.getVersion().equals(command.expectedChangeVersion())
                || !change.getBaseRevisionId().equals(plan.getCurrentDurationRevisionId())) {
            throw exception(CONSTRUCTION_PLAN_VERSION_NOT_MATCH);
        }
        ConstructionPlanRevisionDO candidate = revisionMapper.selectForUpdate(
                new ConstructionPlanRevisionLockQuery(actor.tenantId(), plan.getId(),
                        change.getCandidateRevisionId()));
        if (candidate == null || candidate.getFrozenAt() != null
                || !change.getId().equals(candidate.getSourceChangeId())) {
            throw exception(CONSTRUCTION_PLAN_STATUS_INVALID);
        }
        Map<String, Object> before = auditSnapshot(change, candidate);

        DurationChangePatch patch = command.patch();
        boolean durationChanged = patch.submittedFields().stream().anyMatch(DURATION_FIELDS::contains);
        if (durationChanged) {
            DurationRules.ResolvedDuration duration = resolveMergedDuration(candidate, patch);
            if (revisionMapper.updateDraftIfMatch(new ConstructionPlanRevisionDraftUpdate(
                    actor.tenantId(), plan.getId(), candidate.getId(), candidate.getVersion(),
                    duration.calculationBasisCode(), duration.startDate(), duration.endDate(),
                    duration.durationDays(), change.getId())) != 1) {
                throw exception(CONSTRUCTION_PLAN_VERSION_NOT_MATCH);
            }
            candidate.setCalculationBasisCode(duration.calculationBasisCode());
            candidate.setStartDate(duration.startDate());
            candidate.setEndDate(duration.endDate());
            candidate.setDurationDays(duration.durationDays());
            candidate.setVersion(candidate.getVersion() + 1);
        }

        String reasonType = patch.submittedFields().contains("reasonType")
                ? requiredCode(patch.reasonType()) : change.getReasonTypeCode();
        String reasonDetail = patch.submittedFields().contains("reasonDetail")
                ? normalizeDetail(patch.reasonDetail()) : change.getReasonDetail();
        Evidence evidence = mergedEvidence(change, patch);
        Set<String> changeFields = changeFields(patch);
        if (changeMapper.updateDraftIfMatch(new ConstructionPlanChangeDraftUpdate(
                actor.tenantId(), plan.getId(), change.getId(), change.getVersion(),
                reasonType, reasonDetail, evidence.fileId(), evidence.fileVersion(), changeFields)) != 1) {
            throw exception(CONSTRUCTION_PLAN_VERSION_NOT_MATCH);
        }
        change.setReasonTypeCode(reasonType);
        change.setReasonDetail(reasonDetail);
        change.setCustomerEvidenceFileId(evidence.fileId());
        change.setCustomerEvidenceFileVersion(evidence.fileVersion());
        change.setVersion(change.getVersion() + 1);
        recordPatchSuccess(plan, change, patch, before, auditSnapshot(change, candidate), actor);
        return response(change, candidate);
    }

    private ConstructionPlanDO lockPlan(Long planId, Integer expectedVersion, Long tenantId) {
        ConstructionPlanDO plan = lockPlan(planId, tenantId);
        if (!plan.getVersion().equals(expectedVersion)) throw exception(CONSTRUCTION_PLAN_VERSION_NOT_MATCH);
        return plan;
    }

    private ConstructionPlanDO lockPlan(Long planId, Long tenantId) {
        ConstructionPlanDO plan = planMapper.selectForUpdate(new ConstructionPlanLockQuery(tenantId, planId));
        if (plan == null) throw exception(CONSTRUCTION_PLAN_STATUS_INVALID);
        return plan;
    }

    private void requireManageAndProject(Long planId, Integer projectVersion,
                                         ConstructionPlanApplicationService.Actor actor) {
        if (!permissionApi.hasAnyPermissions(actor.actorId(), ConstructionPlanApplicationService.PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
        ConstructionPlanDO plan = planMapper.selectById(new ConstructionPlanLockQuery(actor.tenantId(), planId));
        if (plan == null) throw exception(CONSTRUCTION_PLAN_STATUS_INVALID);
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), plan.getProjectId(), ProjectScopeApi.ACTION_MANAGE));
        if (!scope.fullProjectIds().contains(plan.getProjectId())) {
            throw exception(CONSTRUCTION_PLAN_PROJECT_FACT_INVALID);
        }
        participantFactApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(
                plan.getProjectId(), actor.actorId(), projectVersion, "ACTIVE", null,
                Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));
    }

    private Evidence mergedEvidence(ConstructionPlanChangeDO change, DurationChangePatch patch) {
        boolean idSubmitted = patch.submittedFields().contains("customerEvidenceFileId");
        boolean versionSubmitted = patch.submittedFields().contains("customerEvidenceFileVersion");
        if (!idSubmitted && !versionSubmitted) {
            return new Evidence(change.getCustomerEvidenceFileId(), change.getCustomerEvidenceFileVersion());
        }
        if ((idSubmitted && patch.customerEvidenceFileId() == null)
                || (versionSubmitted && patch.customerEvidenceFileVersion() == null)) {
            return new Evidence(null, null);
        }
        return evidence(idSubmitted ? patch.customerEvidenceFileId() : change.getCustomerEvidenceFileId(),
                versionSubmitted ? patch.customerEvidenceFileVersion()
                        : change.getCustomerEvidenceFileVersion());
    }

    private Set<String> changeFields(DurationChangePatch patch) {
        var fields = new java.util.HashSet<String>();
        if (patch.submittedFields().contains("reasonType")) fields.add("reasonType");
        if (patch.submittedFields().contains("reasonDetail")) fields.add("reasonDetail");
        if (patch.submittedFields().contains("customerEvidenceFileId")
                || patch.submittedFields().contains("customerEvidenceFileVersion")) {
            fields.add("customerEvidence");
        }
        return Set.copyOf(fields);
    }

    private <T> T value(DurationChangePatch patch, String field, T submitted, T existing) {
        return patch.submittedFields().contains(field) ? submitted : existing;
    }

    private DurationRules.ResolvedDuration resolveMergedDuration(
            ConstructionPlanRevisionDO candidate, DurationChangePatch patch) {
        String basis = value(patch, "calculationBasis", patch.calculationBasis(),
                candidate.getCalculationBasisCode());
        java.time.LocalDate start = value(patch, "startDate", patch.startDate(), candidate.getStartDate());
        java.time.LocalDate end = value(patch, "endDate", patch.endDate(), candidate.getEndDate());
        Integer days = value(patch, "durationDays", patch.durationDays(), candidate.getDurationDays());
        if (DurationRules.DURATION_FROM_START.equals(basis)
                && !patch.submittedFields().contains("endDate")) {
            end = null;
        }
        if (DurationRules.DATE_RANGE.equals(basis)
                && !patch.submittedFields().contains("durationDays")) {
            days = null;
        }
        return resolveDuration(basis, start, end, days);
    }

    private DurationRules.ResolvedDuration resolveDuration(
            String basis, java.time.LocalDate start, java.time.LocalDate end, Integer days) {
        try {
            return DurationRules.resolve(basis, start, end, days);
        } catch (IllegalArgumentException failure) {
            throw exception(CONSTRUCTION_PLAN_ARGUMENT_INVALID);
        }
    }

    private String requiredCode(String value) {
        if (value == null || value.isBlank()) throw exception(CONSTRUCTION_PLAN_ARGUMENT_INVALID);
        return value.trim();
    }

    private String normalizeDetail(String value) {
        return value == null ? "" : value.trim();
    }

    private Evidence evidence(Long fileId, Integer fileVersion) {
        if (fileId == null && fileVersion == null) return new Evidence(null, null);
        if (fileId == null || fileId <= 0 || fileVersion == null || fileVersion <= 0) {
            throw exception(CONSTRUCTION_PLAN_ARGUMENT_INVALID);
        }
        return new Evidence(fileId, fileVersion);
    }

    private void validateCreate(CreateDurationChangeCommand command,
                                ConstructionPlanApplicationService.Actor actor) {
        validateActor(actor);
        if (command == null || command.planId() == null || command.planId() <= 0
                || invalidVersion(command.expectedPlanVersion())
                || invalidVersion(command.expectedProjectVersion())
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.requestDigest() == null || !command.requestDigest().matches("[0-9a-f]{64}")) {
            throw exception(CONSTRUCTION_PLAN_ARGUMENT_INVALID);
        }
    }

    private void validatePatch(PatchDurationChangeCommand command,
                               ConstructionPlanApplicationService.Actor actor) {
        validateActor(actor);
        if (command == null || command.planId() == null || command.planId() <= 0
                || command.changeId() == null || command.changeId() <= 0
                || invalidVersion(command.expectedChangeVersion())
                || invalidVersion(command.expectedProjectVersion())
                || command.patch() == null || command.patch().submittedFields().isEmpty()) {
            throw exception(CONSTRUCTION_PLAN_ARGUMENT_INVALID);
        }
    }

    private void validateActor(ConstructionPlanApplicationService.Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0
                || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw exception(CONSTRUCTION_PLAN_ARGUMENT_INVALID);
        }
    }

    private boolean invalidVersion(Integer value) { return value == null || value < 0; }

    private ConstructionPlanChangeRespVO response(
            ConstructionPlanChangeDO change, ConstructionPlanRevisionDO candidate) {
        ConstructionPlanChangeRespVO response = new ConstructionPlanChangeRespVO();
        response.setChangeId(change.getId());
        response.setBaseRevisionId(change.getBaseRevisionId());
        response.setCandidateRevisionId(change.getCandidateRevisionId());
        response.setCandidateRevision(revisionResponse(candidate));
        response.setStatus(change.getStatusCode());
        response.setReasonType(change.getReasonTypeCode());
        response.setReasonDetail(change.getReasonDetail());
        response.setCustomerEvidenceRequired(change.getCustomerEvidenceRequired());
        response.setCustomerEvidenceFileId(change.getCustomerEvidenceFileId());
        response.setCustomerEvidenceFileVersion(change.getCustomerEvidenceFileVersion());
        response.setApplicantUserId(change.getApplicantUserId());
        response.setCreatedAt(change.getCreatedAt());
        response.setVersion(change.getVersion());
        return response;
    }

    private ConstructionPlanRevisionRespVO revisionResponse(ConstructionPlanRevisionDO row) {
        ConstructionPlanRevisionRespVO response = new ConstructionPlanRevisionRespVO();
        response.setRevisionId(row.getId());
        response.setRevisionNo(row.getRevisionNo());
        response.setCalculationBasis(row.getCalculationBasisCode());
        response.setStartDate(row.getStartDate());
        response.setEndDate(row.getEndDate());
        response.setDurationDays(row.getDurationDays());
        response.setSourceChangeId(row.getSourceChangeId());
        response.setCreatedBy(row.getCreatedBy());
        response.setCreatedAt(row.getCreatedAt());
        response.setVersion(row.getVersion());
        response.setCurrent(false);
        return response;
    }

    private PlatformCommandExecutionApi.SuccessFacts createSuccessFacts(
            ConstructionPlanDO plan, ConstructionPlanChangeRespVO response,
            ConstructionPlanApplicationService.Actor actor) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", plan.getProjectId());
        detail.put("planId", plan.getId());
        detail.put("baseRevisionId", response.getBaseRevisionId());
        detail.put("candidateRevisionId", response.getCandidateRevisionId());
        detail.put("changeId", response.getChangeId());
        detail.put("statusBefore", "NONE");
        detail.put("statusAfter", response.getStatus());
        detail.put("currentRevisionIdBefore", plan.getCurrentDurationRevisionId());
        detail.put("currentRevisionIdAfter", plan.getCurrentDurationRevisionId());
        detail.put("pendingChangeIdBefore", auditValue(plan.getPendingChangeId()));
        detail.put("pendingChangeIdAfter", auditValue(plan.getPendingChangeId()));
        detail.put("reasonType", response.getReasonType());
        detail.put("reasonDetail", response.getReasonDetail());
        detail.put("customerEvidenceFileId", auditValue(response.getCustomerEvidenceFileId()));
        detail.put("customerEvidenceFileVersion", auditValue(response.getCustomerEvidenceFileVersion()));
        detail.put("candidateRevision", revisionAuditSnapshot(response.getCandidateRevision()));
        detail.put("planVersion", plan.getVersion());
        return new PlatformCommandExecutionApi.SuccessFacts(CREATE_OPERATION, "ConstructionPlanChange",
                String.valueOf(response.getChangeId()), actor.correlationId(),
                JsonUtils.toJsonString(detail), null, null);
    }

    private void recordPatchSuccess(ConstructionPlanDO plan, ConstructionPlanChangeDO change,
                                    DurationChangePatch patch, Map<String, Object> before,
                                    Map<String, Object> after,
                                    ConstructionPlanApplicationService.Actor actor) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", plan.getProjectId());
        detail.put("planId", plan.getId());
        detail.put("changeId", change.getId());
        detail.put("candidateRevisionId", change.getCandidateRevisionId());
        detail.put("submittedFields", patch.submittedFields());
        detail.put("before", before);
        detail.put("after", after);
        detail.put("changeVersionAfter", change.getVersion());
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(),
                PATCH_OPERATION, "ConstructionPlanChange", String.valueOf(change.getId()), "SUCCESS",
                detail);
    }

    private Map<String, Object> auditSnapshot(ConstructionPlanChangeDO change,
                                               ConstructionPlanRevisionDO candidate) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", change.getStatusCode());
        snapshot.put("reasonType", change.getReasonTypeCode());
        snapshot.put("reasonDetail", change.getReasonDetail());
        snapshot.put("customerEvidenceFileId", auditValue(change.getCustomerEvidenceFileId()));
        snapshot.put("customerEvidenceFileVersion", auditValue(change.getCustomerEvidenceFileVersion()));
        snapshot.put("candidateRevision", revisionAuditSnapshot(revisionResponse(candidate)));
        return snapshot;
    }

    private Object auditValue(Object value) {
        return value == null ? "NONE" : value;
    }

    private Map<String, Object> revisionAuditSnapshot(ConstructionPlanRevisionRespVO revision) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("revisionId", revision.getRevisionId());
        snapshot.put("revisionNo", revision.getRevisionNo());
        snapshot.put("calculationBasis", revision.getCalculationBasis());
        snapshot.put("startDate", revision.getStartDate());
        snapshot.put("endDate", revision.getEndDate());
        snapshot.put("durationDays", revision.getDurationDays());
        snapshot.put("sourceChangeId", revision.getSourceChangeId());
        snapshot.put("version", revision.getVersion());
        return snapshot;
    }

    private void auditRejected(String operation, Long planId, Long changeId, Integer expectedPlanVersion,
                               ConstructionPlanApplicationService.Actor actor, RuntimeException failure) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.correlationId() == null || actor.correlationId().isBlank()) return;
        Map<String, Object> detail = new LinkedHashMap<>();
        if (planId != null) detail.put("planId", planId);
        if (changeId != null) detail.put("changeId", changeId);
        if (expectedPlanVersion != null) detail.put("expectedPlanVersion", expectedPlanVersion);
        detail.put("failureCode", failureCode(failure));
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(), operation,
                "ConstructionPlanChange", changeId == null ? "UNKNOWN" : String.valueOf(changeId),
                "REJECTED", Map.copyOf(detail));
    }

    private String failureCode(RuntimeException failure) {
        if (failure instanceof ServiceException serviceException) return String.valueOf(serviceException.getCode());
        if (failure.getMessage() != null && failure.getMessage().startsWith("DURATION_CHANGE_")) {
            return failure.getMessage();
        }
        return "DURATION_CHANGE_COMMAND_FAILED";
    }

    private record Evidence(Long fileId, Integer fileVersion) {
    }
}
