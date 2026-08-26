package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanChangePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanChangeRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanCursorPageRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanRevisionPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanRevisionRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanChangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangePageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionPageQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_ARGUMENT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.DURATION_CHANGE_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.engineering.service.constructionplan.ConstructionPlanApplicationService.PERMISSION_MANAGE;

@Service
@RequiredArgsConstructor
public class ConstructionPlanQueryService {

    public static final String PERMISSION_QUERY = "pms:construction-plan:query";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ConstructionPlanMapper planMapper;
    private final ConstructionPlanRevisionMapper revisionMapper;
    private final ConstructionPlanChangeMapper changeMapper;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;

    public ConstructionPlanRespVO getById(Long planId, Actor actor) {
        requireQueryPermission(actor);
        ConstructionPlanDO plan = planMapper.selectById(new ConstructionPlanLockQuery(
                actor.tenantId(), requirePositive(planId)));
        requirePlan(plan);
        assertProjectScope(actor, plan.getProjectId(), ProjectScopeApi.ACTION_VIEW);
        return toPlanResponse(plan, actor);
    }

    public ConstructionPlanRespVO getByProjectId(Long projectId, Actor actor) {
        requireQueryPermission(actor);
        long checkedProjectId = requirePositive(projectId);
        assertProjectScope(actor, checkedProjectId, ProjectScopeApi.ACTION_VIEW);
        ConstructionPlanDO plan = planMapper.selectByProjectId(actor.tenantId(), checkedProjectId);
        return plan == null ? null : toPlanResponse(plan, actor);
    }

    public ConstructionPlanCursorPageRespVO<ConstructionPlanRevisionRespVO> getRevisions(
            Long planId, ConstructionPlanRevisionPageReqVO request, Actor actor) {
        ConstructionPlanDO plan = requireVisiblePlan(planId, actor);
        RevisionCursor cursor = parseRevisionCursor(request == null ? null : request.getCursor());
        int pageSize = pageSize(request == null ? null : request.getPageSize());
        List<ConstructionPlanRevisionDO> fetched = revisionMapper.selectPage(
                new ConstructionPlanRevisionPageQuery(actor.tenantId(), plan.getId(),
                        cursor.revisionNo(), cursor.id(), pageSize + 1));
        boolean hasMore = fetched.size() > pageSize;
        List<ConstructionPlanRevisionDO> page = hasMore ? fetched.subList(0, pageSize) : fetched;
        List<ConstructionPlanRevisionRespVO> items = page.stream()
                .map(item -> toRevision(item, plan.getCurrentDurationRevisionId())).toList();
        String next = hasMore ? revisionCursor(page.get(page.size() - 1)) : null;
        return new ConstructionPlanCursorPageRespVO<>(items, next, hasMore);
    }

    public ConstructionPlanCursorPageRespVO<ConstructionPlanChangeRespVO> getChanges(
            Long planId, ConstructionPlanChangePageReqVO request, Actor actor) {
        ConstructionPlanDO plan = requireVisiblePlan(planId, actor);
        ChangeCursor cursor = parseChangeCursor(request == null ? null : request.getCursor());
        int pageSize = pageSize(request == null ? null : request.getPageSize());
        List<ConstructionPlanChangeDO> fetched = changeMapper.selectPage(
                new ConstructionPlanChangePageQuery(actor.tenantId(), plan.getId(),
                        cursor.createdAt(), cursor.id(), pageSize + 1));
        boolean hasMore = fetched.size() > pageSize;
        List<ConstructionPlanChangeDO> page = hasMore ? fetched.subList(0, pageSize) : fetched;
        List<ConstructionPlanChangeRespVO> items = page.stream().map(this::toChange).toList();
        String next = hasMore ? changeCursor(page.get(page.size() - 1)) : null;
        return new ConstructionPlanCursorPageRespVO<>(items, next, hasMore);
    }

    public ConstructionPlanChangeRespVO getChange(
            Long planId, Long changeId, Actor actor) {
        ConstructionPlanDO plan = requireVisiblePlan(planId, actor);
        ConstructionPlanChangeDO change = changeMapper.selectById(new ConstructionPlanChangeLockQuery(
                actor.tenantId(), plan.getId(), requirePositive(changeId)));
        if (change == null) throw exception(DURATION_CHANGE_NOT_EXISTS);
        ConstructionPlanRevisionDO candidate = revisionMapper.selectById(
                new ConstructionPlanRevisionLockQuery(actor.tenantId(), plan.getId(),
                        change.getCandidateRevisionId()));
        if (candidate == null) throw exception(CONSTRUCTION_PLAN_NOT_EXISTS);
        ConstructionPlanChangeRespVO response = toChange(change);
        response.setCandidateRevision(toRevision(candidate, plan.getCurrentDurationRevisionId()));
        return response;
    }

    private ConstructionPlanDO requireVisiblePlan(Long planId, Actor actor) {
        requireQueryPermission(actor);
        ConstructionPlanDO plan = planMapper.selectById(new ConstructionPlanLockQuery(
                actor.tenantId(), requirePositive(planId)));
        requirePlan(plan);
        assertProjectScope(actor, plan.getProjectId(), ProjectScopeApi.ACTION_VIEW);
        return plan;
    }

    private ConstructionPlanRespVO toPlanResponse(ConstructionPlanDO plan, Actor actor) {
        ConstructionPlanRevisionDO current = revisionMapper.selectById(new ConstructionPlanRevisionLockQuery(
                actor.tenantId(), plan.getId(), plan.getCurrentDurationRevisionId()));
        if (current == null) {
            throw exception(CONSTRUCTION_PLAN_NOT_EXISTS);
        }
        ConstructionPlanChangeDO pending = plan.getPendingChangeId() == null ? null
                : changeMapper.selectById(new ConstructionPlanChangeLockQuery(
                        actor.tenantId(), plan.getId(), plan.getPendingChangeId()));
        if (plan.getPendingChangeId() != null && pending == null) {
            throw exception(CONSTRUCTION_PLAN_NOT_EXISTS);
        }
        ConstructionPlanRevisionDO pendingCandidate = pending == null ? null
                : revisionMapper.selectById(new ConstructionPlanRevisionLockQuery(
                        actor.tenantId(), plan.getId(), pending.getCandidateRevisionId()));
        if (pending != null && pendingCandidate == null) {
            throw exception(CONSTRUCTION_PLAN_NOT_EXISTS);
        }
        ConstructionPlanRespVO response = new ConstructionPlanRespVO();
        response.setPlanId(plan.getId());
        response.setProjectId(plan.getProjectId());
        response.setCurrentRevision(toRevision(current, plan.getCurrentDurationRevisionId()));
        if (pending != null) {
            ConstructionPlanChangeRespVO pendingSummary = toChange(pending);
            pendingSummary.setCandidateRevision(toRevision(
                    pendingCandidate, plan.getCurrentDurationRevisionId()));
            response.setPendingChangeSummary(pendingSummary);
        }
        response.setPlanRecalculationStatus(plan.getPlanRecalculationStatusCode());
        response.setPlanRecalculationSourceRevisionId(plan.getPlanRecalculationSourceRevisionId());
        response.setPlanVersion(plan.getVersion());
        response.setAllowedActions(allowedActions(plan, actor));
        return response;
    }

    private List<String> allowedActions(ConstructionPlanDO plan, Actor actor) {
        if (plan.getPendingChangeId() != null
                || !permissionApi.hasAnyPermissions(actor.actorId(), PERMISSION_MANAGE)) {
            return List.of();
        }
        try {
            assertProjectScope(actor, plan.getProjectId(), ProjectScopeApi.ACTION_MANAGE);
            var fact = participantFactApi.inspect(new ProjectParticipantFactQuery(
                    plan.getProjectId(), actor.actorId(),
                    Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), LocalDateTime.now()));
            if (!"ACTIVE".equals(fact.lifecycleStatus())) {
                return List.of();
            }
            return List.of("CREATE_CHANGE");
        } catch (ServiceException ex) {
            return List.of();
        }
    }

    private ConstructionPlanRevisionRespVO toRevision(ConstructionPlanRevisionDO row, Long currentId) {
        ConstructionPlanRevisionRespVO response = new ConstructionPlanRevisionRespVO();
        response.setRevisionId(row.getId());
        response.setRevisionNo(row.getRevisionNo());
        response.setCalculationBasis(row.getCalculationBasisCode());
        response.setStartDate(row.getStartDate());
        response.setEndDate(row.getEndDate());
        response.setDurationDays(row.getDurationDays());
        response.setSourceChangeId(row.getSourceChangeId());
        response.setFrozenAt(row.getFrozenAt());
        response.setEffectiveAt(row.getEffectiveAt());
        response.setCreatedBy(row.getCreatedBy());
        response.setCreatedAt(row.getCreatedAt());
        response.setVersion(row.getVersion());
        response.setCurrent(row.getId().equals(currentId));
        return response;
    }

    private ConstructionPlanChangeRespVO toChange(ConstructionPlanChangeDO row) {
        ConstructionPlanChangeRespVO response = new ConstructionPlanChangeRespVO();
        response.setChangeId(row.getId());
        response.setBaseRevisionId(row.getBaseRevisionId());
        response.setCandidateRevisionId(row.getCandidateRevisionId());
        response.setStatus(row.getStatusCode());
        response.setReasonType(row.getReasonTypeCode());
        response.setReasonDetail(row.getReasonDetail());
        response.setCustomerEvidenceRequired(row.getCustomerEvidenceRequired());
        response.setCustomerEvidenceFileId(row.getCustomerEvidenceFileId());
        response.setCustomerEvidenceFileVersion(row.getCustomerEvidenceFileVersion());
        response.setCustomerEvidenceReferenceKey(row.getCustomerEvidenceReferenceKey());
        response.setProcessDefinitionKey(row.getProcessDefinitionKey());
        response.setProcessInstanceId(row.getProcessInstanceId());
        response.setSubmittedAt(row.getSubmittedAt());
        response.setApplicantUserId(row.getApplicantUserId());
        response.setApproverUserId(row.getApproverUserId());
        response.setApprovedAt(row.getApprovedAt());
        response.setApprovalOpinion(row.getApprovalOpinion());
        response.setCreatedAt(row.getCreatedAt());
        response.setVersion(row.getVersion());
        return response;
    }

    private void requireQueryPermission(Actor actor) {
        requireActor(actor);
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PERMISSION_QUERY, PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
    }

    private void assertProjectScope(Actor actor, Long projectId, String action) {
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, action));
        if (!scope.fullProjectIds().contains(projectId)) {
            throw exception(FORBIDDEN);
        }
    }

    private void requirePlan(ConstructionPlanDO plan) {
        if (plan == null) {
            throw exception(CONSTRUCTION_PLAN_NOT_EXISTS);
        }
    }

    private void requireActor(Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0) {
            throw exception(FORBIDDEN);
        }
    }

    private long requirePositive(Long value) {
        if (value == null || value <= 0) {
            throw exception(CONSTRUCTION_PLAN_ARGUMENT_INVALID);
        }
        return value;
    }

    private int pageSize(Integer requested) {
        if (requested == null) return DEFAULT_PAGE_SIZE;
        if (requested < 1 || requested > MAX_PAGE_SIZE) {
            throw exception(CONSTRUCTION_PLAN_ARGUMENT_INVALID);
        }
        return requested;
    }

    private RevisionCursor parseRevisionCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return new RevisionCursor(null, null);
        String[] parts = cursor.split(":", -1);
        try {
            if (parts.length != 2) throw new NumberFormatException();
            int revisionNo = Integer.parseInt(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (revisionNo <= 0 || id <= 0) throw new NumberFormatException();
            return new RevisionCursor(revisionNo, id);
        } catch (NumberFormatException ex) {
            throw exception(CONSTRUCTION_PLAN_ARGUMENT_INVALID);
        }
    }

    private ChangeCursor parseChangeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return new ChangeCursor(null, null);
        int separator = cursor.lastIndexOf('|');
        try {
            if (separator <= 0 || separator == cursor.length() - 1) throw new IllegalArgumentException();
            LocalDateTime createdAt = LocalDateTime.parse(cursor.substring(0, separator));
            long id = Long.parseLong(cursor.substring(separator + 1));
            if (id <= 0) throw new IllegalArgumentException();
            return new ChangeCursor(createdAt, id);
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw exception(CONSTRUCTION_PLAN_ARGUMENT_INVALID);
        }
    }

    private String revisionCursor(ConstructionPlanRevisionDO row) {
        return row.getRevisionNo() + ":" + row.getId();
    }

    private String changeCursor(ConstructionPlanChangeDO row) {
        return row.getCreatedAt() + "|" + row.getId();
    }

    private record RevisionCursor(Integer revisionNo, Long id) {
    }

    private record ChangeCursor(LocalDateTime createdAt, Long id) {
    }

    public record Actor(Long tenantId, Long actorId) {
    }
}
