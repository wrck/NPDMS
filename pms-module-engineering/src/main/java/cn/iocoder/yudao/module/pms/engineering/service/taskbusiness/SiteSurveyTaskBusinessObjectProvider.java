package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.SiteSurveyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.SiteSurveyMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyTaskCandidateQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query.SiteSurveyTaskObjectQuery;
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
 * REQ-PROJ-004: read-only task references to existing SOL surveys, not PRE02 preparation readiness.
 * The original controller/service remains the only business command entry; no implicit creation.
 */
@Service
@RequiredArgsConstructor
public class SiteSurveyTaskBusinessObjectProvider implements TaskBusinessObjectProvider {

    private final SiteSurveyMapper mapper;
    private final ProjectScopeApi projectScopeApi;
    private final PermissionApi permissionApi;

    @Override
    public String ownerContext() { return "SOL"; }

    @Override
    public String objectType() { return "SITE_SURVEY"; }

    @Override
    public Set<String> completionFactCodes() { return Set.of("SURVEY_CONFIRMED", "SURVEY_ARCHIVED"); }

    @Override
    public Set<String> inspectContext(Context context) {
        requireQuery(context);
        if (hasScope(context, ProjectScopeApi.ACTION_MANAGE)
                && permissionApi.hasAnyPermissions(context.actorId(), "pms:eng-site-survey:create")) {
            return Set.of("QUERY", "CREATE");
        }
        return Set.of("QUERY");
    }

    @Override
    public List<BusinessObjectFact> candidates(Context context) {
        requireQuery(context);
        Set<String> permissions = writePermissions(context);
        // Public interface has no cursor yet: deterministic first 100, never an unbounded list.
        return mapper.selectTaskCandidates(new SiteSurveyTaskCandidateQuery(
                        context.tenantId(), context.projectId(), 100)).stream()
                .map(row -> toFact(context, row, permissions)).toList();
    }

    @Override
    public BusinessObjectFact inspect(Context context, String objectId) {
        requireQuery(context);
        SiteSurveyTaskObjectQuery query = objectQuery(context, objectId);
        return toFact(context, requireObject(mapper.selectTaskObject(query), query), writePermissions(context));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public BusinessObjectFact lockAndRevalidate(Context context, String objectId, String expectedVersion) {
        requireQuery(context);
        SiteSurveyTaskObjectQuery query = objectQuery(context, objectId);
        BusinessObjectFact fact = toFact(context, requireObject(mapper.selectTaskObjectForUpdate(query), query),
                writePermissions(context));
        // Both optimistic row version AND state are part of the opaque fact identity.
        if (expectedVersion == null || !expectedVersion.equals(fact.factVersion())) {
            throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        }
        return fact;
    }

    private void requireQuery(Context context) {
        if (context == null || context.tenantId() == null || context.tenantId() < 0
                || context.actorId() == null || context.actorId() <= 0
                || context.projectId() == null || context.projectId() <= 0
                || context.taskId() == null || context.taskId() <= 0
                || !Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())
                || !Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId())) {
            throw exception(FORBIDDEN);
        }
        if (!hasScope(context, ProjectScopeApi.ACTION_VIEW)
                || !permissionApi.hasAnyPermissions(context.actorId(), "pms:eng-site-survey:query")) {
            throw exception(FORBIDDEN);
        }
    }

    private boolean hasScope(Context context, String action) {
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                context.tenantId(), context.actorId(), context.projectId(), action));
        return scope != null && scope.fullProjectIds() != null
                && scope.fullProjectIds().contains(context.projectId());
    }

    private Set<String> writePermissions(Context context) {
        if (!hasScope(context, ProjectScopeApi.ACTION_MANAGE)) return Set.of();
        Set<String> permissions = new LinkedHashSet<>();
        if (permissionApi.hasAnyPermissions(context.actorId(), "pms:eng-site-survey:create")) {
            permissions.add("CREATE");
        }
        if (permissionApi.hasAnyPermissions(context.actorId(), "pms:eng-site-survey:update")) {
            permissions.add("UPDATE");
        }
        return permissions;
    }

    private BusinessObjectFact toFact(Context context, SiteSurveyDO row, Set<String> permissions) {
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

    private SiteSurveyDO requireObject(SiteSurveyDO row, SiteSurveyTaskObjectQuery query) {
        if (row == null || !Objects.equals(row.getId(), query.objectId())) {
            throw exception(SITE_SURVEY_NOT_EXISTS);
        }
        return row;
    }

    private SiteSurveyTaskObjectQuery objectQuery(Context context, String objectId) {
        try {
            if (objectId == null || !objectId.matches("[1-9][0-9]*")) throw exception(SITE_SURVEY_NOT_EXISTS);
            return new SiteSurveyTaskObjectQuery(context.tenantId(), context.projectId(), Long.valueOf(objectId));
        } catch (NumberFormatException invalid) {
            throw exception(SITE_SURVEY_NOT_EXISTS);
        }
    }
}
