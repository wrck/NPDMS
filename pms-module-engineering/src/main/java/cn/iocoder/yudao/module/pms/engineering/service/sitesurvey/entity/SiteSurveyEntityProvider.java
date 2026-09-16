package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityTaskObjectQuery;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/** Ordinary entity integration only: site surveys do not implement or register a version provider. */
@Component
@RequiredArgsConstructor
public class SiteSurveyEntityProvider implements EntityFieldProvider {
    public static final EntityFieldMapping<SiteSurveyEntityDO> FIELDS = new EntityFieldMapping<>(SiteSurveyEntityDO.class);
    private final SiteSurveyEntityMapper mapper;
    private final SiteSurveyDetails details;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;

    @Override public String ownerModule() { return "SOL"; }
    @Override public String entityType() { return "SITE_SURVEY"; }
    @Override public List<EntityField> fields() { return FIELDS.fields(); }

    @Override
    public Map<String, EntityFieldValue> read(EntityDataRef target, EntityActor actor) {
        var row = authorized(target, actor, false);
        details.load(row);
        Map<String, EntityFieldValue> values = new LinkedHashMap<>();
        FIELDS.read(row).forEach((code, value) -> values.put(code, EntityFieldValue.known(value)));
        return values;
    }

    @Override public void requireReadable(EntityDataRef target, EntityActor actor) { authorized(target, actor, false); }

    @Override
    public void lockForWrite(EntityDataRef target, EntityActor actor, Integer expectedVersion) {
        var observed = authorized(target, actor, true);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(), actor.userId(), observed.getProjectId(), ProjectScopeApi.ACTION_MANAGE));
        scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(), actor.userId(), observed.getProjectId(),
                ProjectScopeApi.ACTION_MANAGE, scope.treeVersion()));
        var row = mapper.selectTaskObjectForUpdate(new SiteSurveyEntityTaskObjectQuery(actor.tenantId(), observed.getProjectId(), observed.getId()));
        if (row == null || !Objects.equals(row.getVersion(), expectedVersion)) throw exception(SITE_SURVEY_VERSION_NOT_MATCH);
        if (!Integer.valueOf(0).equals(row.getStatus())) throw exception(SITE_SURVEY_FORM_INVALID);
    }

    private SiteSurveyEntityDO authorized(EntityDataRef target, EntityActor actor, boolean write) {
        actor.requireTenant(target.entity());
        if (target.isRevision() || !ownerModule().equals(target.entity().ownerModule()) || !entityType().equals(target.entity().entityType())) {
            throw exception(SITE_SURVEY_FORM_INVALID);
        }
        var row = mapper.selectById(target.entity().entityId());
        if (row == null || !actor.tenantId().equals(row.getTenantId())) throw exception(SITE_SURVEY_FORM_INVALID);
        boolean allowed = write ? permissions.hasAnyPermissions(actor.userId(), "pms:eng-site-survey:create", "pms:eng-site-survey:update")
                : permissions.hasAnyPermissions(actor.userId(), "pms:eng-site-survey:query");
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(), actor.userId(), row.getProjectId(),
                write ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_VIEW));
        if (!allowed || scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(row.getProjectId())) throw exception(FORBIDDEN);
        return row;
    }
}
