package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityTaskCandidateQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityTaskObjectQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * REQ-PROJ-004: read-only node references to existing SOL surveys, not PRE02 preparation readiness.
 * The original controller/service remains the only business command entry; no implicit creation.
 */
@Service
@RequiredArgsConstructor
public class SiteSurveyTaskBusinessObjectProvider implements TaskBusinessObjectProvider, cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider {

    private final SiteSurveyEntityMapper mapper;
    private final ProjectScopeApi projectScopeApi;
    private final PermissionApi permissionApi;
    private final cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi executions;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockCompletionFact(CompletionContext context, String objectId) {
        if (context == null || context.tenantId() == null || context.execution() == null
                || !Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())) throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        var execution = executions.lockAndRevalidate(context.execution());
        return lockedResult(objectQuery(context.tenantId(), execution.projectId(), objectId), execution.executionId());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockStageCompletionFact(StageCompletionContext context, String objectId) {
        if (context == null || context.tenantId() == null || context.execution() == null
                || !Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())) throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        var execution = executions.lockAndRevalidateStage(context.execution());
        return lockedResult(objectQuery(context.tenantId(), execution.projectId(), objectId), execution.executionId());
    }

    private CompletionFact lockedResult(SiteSurveyEntityTaskObjectQuery query, Long executionId) {
        var row = requireObject(mapper.selectTaskObjectForUpdate(query), query);
        if (!Objects.equals(row.getTenantId(), query.tenantId()) || !Objects.equals(row.getProjectId(), query.projectId())
                || Boolean.TRUE.equals(row.getDeleted()))
            throw exception(SITE_SURVEY_NOT_EXISTS);
        if (row.getVersion() == null || row.getVersion() < 0) throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        Integer status = row.getStatus();
        if (status == null || status < 0 || status > 3) throw exception(SITE_SURVEY_STATUS_INVALID);
        // Current-round association is validated by PROJ; SOL supplies the same real result for either node kind.
        boolean confirmed = status == 1 || status == 3;
        boolean archived = status == 3;
        return new CompletionFact(row.getId().toString(), "SOL_SITE_SURVEY_RESULT:" + row.getVersion() + ":" + status
                + ":execution:" + executionId, confirmed,
                Map.of("SURVEY_CONFIRMED", confirmed, "SURVEY_ARCHIVED", archived));
    }

    @Override
    public List<AssociationCandidate> associationCandidates(AssociationContext context, String afterObjectId, int pageSize) {
        if (context == null || !Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())
                || pageSize < 1 || pageSize > 100) throw exception(FORBIDDEN);
        Long afterId = afterObjectId == null ? null : Long.valueOf(afterObjectId);
        return mapper.selectAssociationPage(new cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityAssociationPageQuery(
                context.tenantId(), context.projectId(), afterId, pageSize)).stream()
                .map(row -> new AssociationCandidate(row.getId().toString(), "SOL_SITE_SURVEY:v1:" + row.getVersion() + ":" + row.getStatus()))
                .toList();
    }

    @Override
    public String ownerContext() { return "SOL"; }

    @Override
    public String objectType() { return "SITE_SURVEY"; }

    @Override
    public Set<String> completionFactCodes() { return Set.of("SURVEY_CONFIRMED", "SURVEY_ARCHIVED"); }

    @Override
    public boolean supportsStageCompletionFacts() { return true; }

    @Override
    public Map<String, String> completionFactLabels() {
        return Map.of("SURVEY_CONFIRMED", "工勘记录已确认（不等同实施就绪）", "SURVEY_ARCHIVED", "工勘记录已归档");
    }

    @Override
    public cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider.Result inspectStage(
            cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider.Context context) {
        if (context == null || context.stageId() == null || context.stageId() <= 0
                || !Set.of("REFERENCE_EXISTING", "CREATE_ON_FIRST_ACTION", "READ_ONLY_AGGREGATE").contains(context.instanceResolutionStrategy()))
            throw exception(FORBIDDEN);
        requireProjectQuery(context.tenantId(), context.actorId(), context.projectId());
        Set<String> actions = new LinkedHashSet<>(Set.of("QUERY"));
        var execution = context.execution();
        if (!"READ_ONLY_AGGREGATE".equals(context.instanceResolutionStrategy()) && execution != null && execution.writable()
                && Objects.equals(context.projectId(), execution.projectId()) && Objects.equals(context.stageId(), execution.stageId())
                && permissionApi.hasAnyPermissions(context.actorId(), "pms:project-task:execute")
                && hasScope(context.tenantId(), context.actorId(), context.projectId(), ProjectScopeApi.ACTION_EDIT)
                && hasScope(context.tenantId(), context.actorId(), context.projectId(), ProjectScopeApi.ACTION_MANAGE)) {
            if (permissionApi.hasAnyPermissions(context.actorId(), "pms:eng-site-survey:create")) actions.add("CREATE");
            if (permissionApi.hasAnyPermissions(context.actorId(), "pms:eng-site-survey:update")) actions.addAll(Set.of("UPDATE", "CONFIRM", "REJECT", "ARCHIVE"));
            if (permissionApi.hasAnyPermissions(context.actorId(), "pms:eng-site-survey:delete")) actions.add("DELETE");
        }
        return new cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider.Result(actions);
    }

    @Override
    public Set<String> inspectContext(TaskBusinessObjectProvider.Context context) {
        requireQuery(context);
        Set<String> actions = new LinkedHashSet<>(Set.of("QUERY"));
        var permissions = writePermissions(context);
        actions.addAll(permissions);
        if (permissions.contains("UPDATE")) actions.addAll(Set.of("CONFIRM", "REJECT", "ARCHIVE"));
        return Set.copyOf(actions);
    }

    @Override
    public List<BusinessObjectFact> candidates(TaskBusinessObjectProvider.Context context) {
        requireQuery(context);
        Set<String> permissions = writePermissions(context);
        // Public interface has no cursor yet: deterministic first 100, never an unbounded list.
        return mapper.selectTaskCandidates(new SiteSurveyEntityTaskCandidateQuery(
                        context.tenantId(), context.projectId(), 100)).stream()
                .map(row -> toFact(context, row, permissions)).toList();
    }

    @Override
    public BusinessObjectFact inspect(TaskBusinessObjectProvider.Context context, String objectId) {
        requireQuery(context);
        SiteSurveyEntityTaskObjectQuery query = objectQuery(context, objectId);
        return toFact(context, requireObject(mapper.selectTaskObject(query), query), writePermissions(context));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public BusinessObjectFact lockAndRevalidate(TaskBusinessObjectProvider.Context context, String objectId, String expectedVersion) {
        requireQuery(context);
        SiteSurveyEntityTaskObjectQuery query = objectQuery(context, objectId);
        BusinessObjectFact fact = toFact(context, requireObject(mapper.selectTaskObjectForUpdate(query), query),
                writePermissions(context));
        // Both optimistic row version AND state are part of the opaque fact identity.
        if (expectedVersion == null || !expectedVersion.equals(fact.factVersion())) {
            throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        }
        return fact;
    }

    private void requireQuery(TaskBusinessObjectProvider.Context context) {
        if (context == null || context.taskId() == null || context.taskId() <= 0) throw exception(FORBIDDEN);
        requireProjectQuery(context.tenantId(), context.actorId(), context.projectId());
    }

    private void requireProjectQuery(Long tenantId, Long actorId, Long projectId) {
        if (tenantId == null || tenantId < 0 || actorId == null || actorId <= 0 || projectId == null || projectId <= 0
                || !Objects.equals(tenantId, TenantContextHolder.getTenantId())
                || !Objects.equals(actorId, SecurityFrameworkUtils.getLoginUserId())) {
            throw exception(FORBIDDEN);
        }
        if (!hasScope(tenantId, actorId, projectId, ProjectScopeApi.ACTION_VIEW)
                || !permissionApi.hasAnyPermissions(actorId, "pms:eng-site-survey:query")) {
            throw exception(FORBIDDEN);
        }
    }

    private boolean hasScope(TaskBusinessObjectProvider.Context context, String action) {
        return hasScope(context.tenantId(), context.actorId(), context.projectId(), action);
    }

    private boolean hasScope(Long tenantId, Long actorId, Long projectId, String action) {
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                tenantId, actorId, projectId, action));
        return scope != null && scope.fullProjectIds() != null
                && scope.fullProjectIds().contains(projectId);
    }

    private Set<String> writePermissions(TaskBusinessObjectProvider.Context context) {
        if (!hasScope(context, ProjectScopeApi.ACTION_MANAGE)) return Set.of();
        Set<String> permissions = new LinkedHashSet<>();
        if (permissionApi.hasAnyPermissions(context.actorId(), "pms:eng-site-survey:create")) {
            permissions.add("CREATE");
        }
        if (permissionApi.hasAnyPermissions(context.actorId(), "pms:eng-site-survey:update")) {
            permissions.add("UPDATE");
        }
        if (permissionApi.hasAnyPermissions(context.actorId(), "pms:eng-site-survey:delete")) {
            permissions.add("DELETE");
        }
        return permissions;
    }

    private BusinessObjectFact toFact(TaskBusinessObjectProvider.Context context, SiteSurveyEntityDO row, Set<String> permissions) {
        if (row == null || row.getId() == null || row.getId() <= 0 || Boolean.TRUE.equals(row.getDeleted())
                || !Objects.equals(context.tenantId(), row.getTenantId())
                || !Objects.equals(context.projectId(), row.getProjectId())) {
            throw exception(SITE_SURVEY_NOT_EXISTS);
        }
        if (row.getVersion() == null || row.getVersion() < 0) throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        Integer status = row.getStatus();
        if (status == null || status < 0 || status > 3) throw exception(SITE_SURVEY_STATUS_INVALID);
        Set<String> actions = new LinkedHashSet<>();
        actions.add("QUERY");
        if (permissions.contains("CREATE")) actions.add("CREATE");
        if (status == 0 && permissions.contains("DELETE") && row.getOutsourceRequestId() == null) actions.add("DELETE");
        if (permissions.contains("UPDATE")) {
            // Relationships do not mutate this entity, including its immutable archived history.
            actions.add("LINK");
            actions.add("UNLINK");
            if (status == 0) actions.addAll(Set.of("UPDATE", "CONFIRM", "REJECT"));
            if (status == 1) actions.add("ARCHIVE");
        }
        return new BusinessObjectFact(row.getId().toString(), row.getName(),
                "SOL_SITE_SURVEY:v1:" + row.getVersion() + ":" + status,
                actions, Map.of("SURVEY_CONFIRMED", status == 1 || status == 3,
                        "SURVEY_ARCHIVED", status == 3),
                // No genuine PLT file source exists on this aggregate. Text/URL are not artifacts.
                List.of());
    }

    private SiteSurveyEntityDO requireObject(SiteSurveyEntityDO row, SiteSurveyEntityTaskObjectQuery query) {
        if (row == null || !Objects.equals(row.getId(), query.objectId())) {
            throw exception(SITE_SURVEY_NOT_EXISTS);
        }
        return row;
    }

    private SiteSurveyEntityTaskObjectQuery objectQuery(TaskBusinessObjectProvider.Context context, String objectId) {
        return objectQuery(context.tenantId(), context.projectId(), objectId);
    }

    private SiteSurveyEntityTaskObjectQuery objectQuery(Long tenantId, Long projectId, String objectId) {
        try {
            if (objectId == null || !objectId.matches("[1-9][0-9]*")) throw exception(SITE_SURVEY_NOT_EXISTS);
            return new SiteSurveyEntityTaskObjectQuery(tenantId, projectId, Long.valueOf(objectId));
        } catch (NumberFormatException invalid) {
            throw exception(SITE_SURVEY_NOT_EXISTS);
        }
    }
}
