package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessOwnerPermissionPolicy;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SiteSurveyBusinessPermissionPolicy implements BusinessOwnerPermissionPolicy {
    private final PermissionApi permissions;
    @Override public String ownerModule() { return "SOL"; }
    @Override public String entityType() { return "siteSurvey"; }
    @Override public boolean readable(EntityActor actor) {
        return actor != null && (actor.isSystemObserver()
                || permissions.hasAnyPermissions(actor.userId(), "pms:sol-site-survey:query"));
    }
    @Override public boolean executable(EntityActor actor, String code, int version) {
        return actor != null && !actor.isSystemObserver()
                && SiteSurveyBusinessApplicationService.operations().stream().anyMatch(op -> op.code().equals(code) && op.version() == version)
                && permissions.hasAnyPermissions(actor.userId(), SiteSurveyBusinessApplicationService.permission(code));
    }
}
