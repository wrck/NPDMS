package cn.iocoder.yudao.module.pms.project.service.businessview;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** ACC-03 / PM-03: registration of the existing report page, not independent acceptance creation. */
@Component
@RequiredArgsConstructor
public class AcceptanceBusinessViewProvider implements BusinessViewComponentProvider {

    private final PermissionApi permissionApi;

    @Override
    public BusinessViewComponentProvider.Component component() {
        return new BusinessViewComponentProvider.Component("ACCEPTANCE", "ACC", ViewSource.PAGE,
                "ACC_ACCEPTANCE_REPORT", "1",
                JsonUtils.parseTree("""
                        {"type":"object","required":["projectId"],"properties":{
                          "projectId":{"oneOf":[{"type":"integer","minimum":1},
                            {"type":"string","pattern":"^[1-9][0-9]*$"}]}}}
                        """),
                JsonUtils.parseTree("[\"QUERY\",\"MANAGE\",\"UPDATE\",\"REVOKE\",\"LINK\",\"UNLINK\"]"),
                "ACC_ACCEPTANCE_REPORT_QUERY", "ACC_ACCEPTANCE_REPORT_COMMAND", "ACC_ACCEPTANCE_REPORT_PERMISSION");
    }

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
            throw new IllegalArgumentException("验收报告页面配置上下文无效或混入动态表单修订");
        }
        return new Dependencies(false);
    }

    private boolean trusted(Context context) {
        return context != null && context.tenantId() != null && context.tenantId() >= 0
                && context.actorId() != null && context.actorId() > 0
                && Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())
                && Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId());
    }
}
