package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessScopeAccess;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Project scope policy is shared across models; it knows no business entity type or property name. */
@Component @RequiredArgsConstructor
public class ProjectBusinessScopeAccess implements BusinessScopeAccess {
    private final ProjectScopeApi projects;
    @Override public String policyRef() { return "project"; }
    @Override public Set<Long> readableScopeIds(EntityActor actor) {
        var ids=projects.resolveAllCurrent(new ProjectAllScopeQuery(actor.tenantId(),actor.userId(),ProjectScopeApi.ACTION_VIEW));
        return ids==null?Set.of():Set.copyOf(ids);
    }
    @Override public void requireReadable(Long id, EntityActor actor) {
        observe(id, actor, ProjectScopeApi.ACTION_VIEW);
    }
    @Override public void requireWritable(Long id, EntityActor actor, boolean lock) {
        requireWritableScopes(Set.of(id), actor, lock);
    }
    @Override public void requireWritableScopes(Set<Long> ids, EntityActor actor, boolean lock) {
        List<ScopeObservation> observations = ids.stream().map(id -> new ScopeObservation(id,
                observe(id, actor, ProjectScopeApi.ACTION_MANAGE))).toList();
        if (!lock) return;
        // Tree-root locks are shared by different anchors: sorting anchor IDs alone is insufficient.
        observations.stream().sorted(Comparator.comparing((ScopeObservation item) -> item.observed().rootProjectId())
                .thenComparing(ScopeObservation::id)).forEach(item -> {
            var observed = item.observed();
            var current = projects.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(),
                    actor.userId(), item.id(), ProjectScopeApi.ACTION_MANAGE, observed.treeVersion()));
            if (current == null || current.fullProjectIds() == null || !current.fullProjectIds().contains(item.id())
                    || !Objects.equals(observed.rootProjectId(), current.rootProjectId())
                    || !Objects.equals(observed.treeVersion(), current.treeVersion())) deny();
        });
    }
    private ProjectScopeResult observe(Long id, EntityActor actor, String action) {
        var observed = projects.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(), actor.userId(), id, action));
        if (observed == null || observed.rootProjectId() == null || observed.treeVersion() == null
                || observed.fullProjectIds() == null || !observed.fullProjectIds().contains(id)) deny();
        return observed;
    }
    private record ScopeObservation(Long id, ProjectScopeResult observed) {
    }
    private static void deny() { throw new BusinessContractException("ENTITY_SCOPE_DENIED","对象不在当前授权范围或范围已变化"); }
}
