package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanRevisionRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanVersionUpdate;
import cn.iocoder.yudao.module.pms.engineering.domain.constructionplan.DurationRules;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.CreateInitialDurationCommand;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_ARGUMENT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_PROJECT_FACT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_STATUS_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_VERSION_NOT_MATCH;

@Service
@RequiredArgsConstructor
public class ConstructionPlanApplicationService {

    public static final String PERMISSION_MANAGE = "pms:construction-plan:duration-manage";
    private static final String SCOPE_INITIAL_CREATE = "POST:/api/v1/pms/construction-plans";
    private static final String ACTIVE = "ACTIVE";
    private static final String INITIAL_STAGE = "S1";

    private final ConstructionPlanMapper planMapper;
    private final ConstructionPlanRevisionMapper revisionMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;
    private final OperationAuditApi operationAuditApi;
    private final TransactionTemplate transactionTemplate;

    public ConstructionPlanRespVO createInitial(CreateInitialDurationCommand command, Actor actor) {
        try {
            return transactionTemplate.execute(status -> createInitialInTransaction(command, actor));
        } catch (RuntimeException ex) {
            auditRejected(command, actor, ex);
            throw ex;
        }
    }

    private ConstructionPlanRespVO createInitialInTransaction(
            CreateInitialDurationCommand command, Actor actor) {
        validate(command, actor);
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
        assertProjectScope(actor, command.projectId(), ProjectScopeApi.ACTION_MANAGE);
        participantFactApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(
                command.projectId(), actor.actorId(), command.expectedProjectVersion(), ACTIVE,
                INITIAL_STAGE, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));

        DurationRules.ResolvedDuration duration;
        try {
            duration = DurationRules.resolve(command.calculationBasisCode(), command.startDate(),
                    command.endDate(), command.durationDays());
        } catch (IllegalArgumentException ex) {
            throw exception(CONSTRUCTION_PLAN_ARGUMENT_INVALID);
        }
        var execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), SCOPE_INITIAL_CREATE, actor.actorId(), command.idempotencyKey()),
                command.requestDigest(), ConstructionPlanRespVO.class,
                () -> createOnce(command.projectId(), duration, actor),
                response -> successFacts(command, response, actor));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw exception(CONSTRUCTION_PLAN_STATUS_INVALID);
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw exception(CONSTRUCTION_PLAN_STATUS_INVALID);
        }
        return execution.response();
    }

    private void auditRejected(CreateInitialDurationCommand command, Actor actor, RuntimeException failure) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.correlationId() == null || actor.correlationId().isBlank()) {
            return;
        }
        var detail = new LinkedHashMap<String, Object>();
        if (command != null) {
            if (command.projectId() != null) detail.put("projectId", command.projectId());
            if (command.expectedProjectVersion() != null) {
                detail.put("expectedProjectVersion", command.expectedProjectVersion());
            }
            if (command.calculationBasisCode() != null) {
                detail.put("calculationBasis", command.calculationBasisCode());
            }
        }
        detail.put("failureCode", failureCode(failure));
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(),
                "CONSTRUCTION_PLAN_INITIAL_DURATION_CREATE", "ConstructionPlan",
                command == null || command.projectId() == null ? "UNKNOWN" : String.valueOf(command.projectId()),
                "REJECTED", Map.copyOf(detail));
    }

    private String failureCode(RuntimeException failure) {
        if (failure instanceof cn.iocoder.yudao.framework.common.exception.ServiceException serviceException) {
            return String.valueOf(serviceException.getCode());
        }
        if (failure instanceof IllegalStateException && failure.getMessage() != null
                && failure.getMessage().startsWith("CONSTRUCTION_PLAN_")) {
            return failure.getMessage();
        }
        return "CONSTRUCTION_PLAN_COMMAND_FAILED";
    }

    private ConstructionPlanRespVO createOnce(Long projectId, DurationRules.ResolvedDuration duration,
                                               Actor actor) {
        if (planMapper.selectByProjectId(actor.tenantId(), projectId) != null) {
            throw exception(CONSTRUCTION_PLAN_STATUS_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        ConstructionPlanDO plan = new ConstructionPlanDO();
        plan.setTenantId(actor.tenantId());
        plan.setProjectId(projectId);
        plan.setPlanRecalculationStatusCode(ConstructionPlanDO.RECALCULATION_PENDING);
        plan.setVersion(0);
        plan.setCreator(String.valueOf(actor.actorId()));
        plan.setUpdater(String.valueOf(actor.actorId()));
        if (planMapper.insert(plan) != 1 || plan.getId() == null) {
            throw new IllegalStateException("项目工期根创建失败");
        }

        ConstructionPlanRevisionDO revision = new ConstructionPlanRevisionDO();
        revision.setTenantId(actor.tenantId());
        revision.setPlanId(plan.getId());
        revision.setRevisionNo(1);
        revision.setCalculationBasisCode(duration.calculationBasisCode());
        revision.setStartDate(duration.startDate());
        revision.setEndDate(duration.endDate());
        revision.setDurationDays(duration.durationDays());
        revision.setFrozenAt(now);
        revision.setEffectiveAt(now);
        revision.setCreatedBy(actor.actorId());
        revision.setCreatedAt(now);
        revision.setVersion(0);
        if (revisionMapper.insert(revision) != 1 || revision.getId() == null) {
            throw new IllegalStateException("项目工期首个版本创建失败");
        }
        if (planMapper.updateVersionIfMatch(new ConstructionPlanVersionUpdate(
                actor.tenantId(), plan.getId(), 0, revision.getId(), null,
                ConstructionPlanDO.RECALCULATION_PENDING, revision.getId(),
                String.valueOf(actor.actorId()))) != 1) {
            throw exception(CONSTRUCTION_PLAN_VERSION_NOT_MATCH);
        }
        return response(plan, revision);
    }

    private ConstructionPlanRespVO response(ConstructionPlanDO plan, ConstructionPlanRevisionDO revision) {
        ConstructionPlanRevisionRespVO revisionResponse = new ConstructionPlanRevisionRespVO();
        revisionResponse.setRevisionId(revision.getId());
        revisionResponse.setRevisionNo(revision.getRevisionNo());
        revisionResponse.setCalculationBasis(revision.getCalculationBasisCode());
        revisionResponse.setStartDate(revision.getStartDate());
        revisionResponse.setEndDate(revision.getEndDate());
        revisionResponse.setDurationDays(revision.getDurationDays());
        revisionResponse.setFrozenAt(revision.getFrozenAt());
        revisionResponse.setEffectiveAt(revision.getEffectiveAt());
        revisionResponse.setCreatedBy(revision.getCreatedBy());
        revisionResponse.setCreatedAt(revision.getCreatedAt());
        revisionResponse.setVersion(revision.getVersion());
        revisionResponse.setCurrent(true);

        ConstructionPlanRespVO response = new ConstructionPlanRespVO();
        response.setPlanId(plan.getId());
        response.setProjectId(plan.getProjectId());
        response.setCurrentRevision(revisionResponse);
        response.setPlanRecalculationStatus(ConstructionPlanDO.RECALCULATION_PENDING);
        response.setPlanRecalculationSourceRevisionId(revision.getId());
        response.setPlanVersion(1);
        response.setAllowedActions(List.of("CREATE_CHANGE"));
        return response;
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            CreateInitialDurationCommand command, ConstructionPlanRespVO response, Actor actor) {
        var detail = new LinkedHashMap<String, Object>();
        detail.put("projectId", command.projectId());
        detail.put("planId", response.getPlanId());
        detail.put("revisionId", response.getCurrentRevision().getRevisionId());
        detail.put("revisionNo", response.getCurrentRevision().getRevisionNo());
        detail.put("currentRevisionBefore", null);
        detail.put("currentRevisionAfter", response.getCurrentRevision().getRevisionId());
        detail.put("planRecalculationStatusBefore", null);
        detail.put("planRecalculationStatusAfter", response.getPlanRecalculationStatus());
        detail.put("projectVersion", command.expectedProjectVersion());
        return new PlatformCommandExecutionApi.SuccessFacts(
                "CONSTRUCTION_PLAN_INITIAL_DURATION_CREATE", "ConstructionPlan",
                String.valueOf(response.getPlanId()), actor.correlationId(),
                JsonUtils.toJsonString(detail), null, null);
    }

    private void assertProjectScope(Actor actor, Long projectId, String action) {
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, action));
        if (!scope.fullProjectIds().contains(projectId)) {
            throw exception(CONSTRUCTION_PLAN_PROJECT_FACT_INVALID);
        }
    }

    private void validate(CreateInitialDurationCommand command, Actor actor) {
        if (command == null || command.projectId() == null || command.projectId() <= 0
                || command.expectedProjectVersion() == null || command.expectedProjectVersion() < 0
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.requestDigest() == null || !command.requestDigest().matches("[0-9a-f]{64}")
                || actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0
                || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw exception(CONSTRUCTION_PLAN_ARGUMENT_INVALID);
        }
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {
    }
}
