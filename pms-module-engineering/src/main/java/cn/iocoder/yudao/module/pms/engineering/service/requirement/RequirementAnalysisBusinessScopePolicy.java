package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Public list scope; direct and revision reads retain the native Provider's stronger Owner policy. */
@Component
@RequiredArgsConstructor
public class RequirementAnalysisBusinessScopePolicy implements BusinessEntityScopePolicy {
    private final RequirementAnalysisEntityProvider owner;
    private final ProjectScopeApi scopes;
    public boolean supports(String module,String type) { return "SOL".equals(module) && "requirementAnalysis".equals(type); }
    public void requireReadable(EntityRef ref,EntityActor actor) {
        owner.requireReadable(EntityDataRef.current(new EntityRef(ref.tenantId(),"SOL","REQUIREMENT_ANALYSIS",ref.entityId())),actor);
    }
    public List<BusinessFieldFilter> queryScope(EntityActor actor) {
        if (actor.isSystemObserver()) return List.of();
        var ids=scopes.resolveAllCurrent(new ProjectAllScopeQuery(actor.tenantId(),actor.userId(),ProjectScopeApi.ACTION_VIEW));
        return List.of(new BusinessFieldFilter("projectId",BusinessFieldFilter.Operator.IN,ids == null ? List.of() : new ArrayList<Object>(ids)));
    }
}
