package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** The catalog identity is explicit; the existing Owner identity is not guessed by casing. */
@Component
@RequiredArgsConstructor
public class SiteSurveyBusinessScopePolicy implements BusinessEntityScopePolicy {
    private final SiteSurveyEntityProvider owner;
    private final ProjectScopeApi scopes;
    @Override public boolean supports(String module, String type) {
        return "SOL".equals(module) && "siteSurvey".equals(type);
    }
    @Override public void requireReadable(EntityRef entity, EntityActor actor) {
        actor.requireTenant(entity);
        if (!actor.isSystemObserver()) owner.requireReadable(EntityDataRef.current(
                new EntityRef(entity.tenantId(), "SOL", "SITE_SURVEY", entity.entityId())), actor);
    }
    @Override public List<BusinessFieldFilter> queryScope(EntityActor actor) {
        if (actor.isSystemObserver()) return List.of();
        Set<Long> ids=scopes.resolveAllCurrent(new ProjectAllScopeQuery(actor.tenantId(), actor.userId(), ProjectScopeApi.ACTION_VIEW));
        // Missing or empty scope stays empty, never becomes an unrestricted tenant query.
        return List.of(new BusinessFieldFilter("projectId",BusinessFieldFilter.Operator.IN,
                ids == null ? List.of() : new ArrayList<Object>(ids)));
    }
}
