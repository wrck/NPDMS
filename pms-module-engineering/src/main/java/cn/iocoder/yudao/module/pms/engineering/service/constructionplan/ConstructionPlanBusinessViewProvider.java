package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** PRE-01 / PM-03: registration of the existing duration page, not independent plan creation. */
@Component
@RequiredArgsConstructor
public class ConstructionPlanBusinessViewProvider implements BusinessViewComponentProvider {

    private final PermissionApi permissionApi;

    @Override
    public BusinessViewComponentProvider.Component component() {
        return new BusinessViewComponentProvider.Component("CONSTRUCTION_PLAN", "PLN", ViewSource.PAGE,
                "PLN_CONSTRUCTION_PLAN", "1",
                JsonUtils.parseTree("""
                        {"type":"object","required":["projectId"],"properties":{
                          "projectId":{"oneOf":[{"type":"integer","minimum":1},
                            {"type":"string","pattern":"^[1-9][0-9]*$"}]}}}
                        """),
                JsonUtils.parseTree("[\"QUERY\",\"CREATE\",\"UPDATE\",\"LINK\",\"UNLINK\"]"),
                "PLN_CONSTRUCTION_PLAN_QUERY", "PLN_CONSTRUCTION_PLAN_COMMAND", "PLN_CONSTRUCTION_PLAN_PERMISSION", "项目工期");
    }

    @Override
    public java.util.Set<String> pagePaths() { return java.util.Set.of("/pms/delivery-business/duration"); }

    @Override
    public boolean canConfigure(Context context, ConfigurationAction action) {
        if (!trusted(context) || action == null) return false;
        return switch (action) {
            case QUERY -> permissionApi.hasAnyPermissions(context.actorId(), "pms:project-template:query");
            case MANAGE -> permissionApi.hasAnyPermissions(context.actorId(),
                    "pms:project-template:create", "pms:project-template:update");
            case PUBLISH -> permissionApi.hasAnyPermissions(context.actorId(), "pms:project-template:publish");
            case DISABLE -> permissionApi.hasAnyPermissions(context.actorId(), "pms:project-template:disable");
        };
    }

    @Override
    public Dependencies validateConfiguration(Context context, Long dynamicFormRevisionId, ValidationMode mode) {
        if (!trusted(context) || mode == null || dynamicFormRevisionId != null) {
            throw new IllegalArgumentException("工期页面配置上下文无效或混入动态表单修订");
        }
        // No instance dependency: PAGE registration cannot create a project duration.
        return new Dependencies(false);
    }

    private boolean trusted(Context context) {
        return context != null && context.tenantId() != null && context.tenantId() >= 0
                && context.actorId() != null && context.actorId() > 0
                && Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())
                && Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId());
    }
}
