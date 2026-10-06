package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessOwnerPermissionPolicy;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RequirementAnalysisBusinessPermissionPolicy implements BusinessOwnerPermissionPolicy {
    private final PermissionApi permissions;
    @Override public String ownerModule() { return "SOL"; }
    @Override public String entityType() { return "requirementAnalysis"; }
    @Override public boolean readable(EntityActor actor) {
        return actor != null && (actor.isSystemObserver() || permissions.hasAnyPermissions(actor.userId(),
                "pms:requirement-analysis:query", "pms:requirement-analysis:manage"));
    }
    @Override public boolean executable(EntityActor actor, String code, int version) {
        return actor != null && !actor.isSystemObserver() && version == 1 && Set.of("create","save","complete","copy").contains(code)
                && permissions.hasAnyPermissions(actor.userId(), "pms:requirement-analysis:manage");
    }
}
