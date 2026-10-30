package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;

/** Register the existing Owner workspace; registration never executes its business commands. */
@Component
@RequiredArgsConstructor
public class InstallationTaskBusinessViewProvider implements BusinessViewComponentProvider {
    private final PermissionApi permissions;
    @Override public BusinessViewComponentProvider.Component component() {
        return new BusinessViewComponentProvider.Component("INSTALLATION", "IMP", ViewSource.PAGE,
                "IMP_PROJECT_INSTALLATION", "1",
                JsonUtils.parseTree("""
                        {"type":"object","required":["projectId"],"properties":{
                        "projectId":{"oneOf":[{"type":"integer","minimum":1},{"type":"string","pattern":"^[1-9][0-9]*$"}]}}}
                        """), JsonUtils.parseTree("[\"QUERY\"]"),
                "IMP_PROJECT_INSTALLATION_QUERY", "IMP_PROJECT_INSTALLATION_COMMAND", "IMP_PROJECT_INSTALLATION_PERMISSION", "硬件实施");
    }
    @Override public Set<String> pagePaths() { return Set.of("/pms/engineering/execution/imp-installation"); }
    @Override public boolean canConfigure(Context context, ConfigurationAction action) {
        if (!trusted(context) || action == null) return false;
        return switch(action) {
            case QUERY -> permissions.hasAnyPermissions(context.actorId(), "pms:project-template:query");
            case MANAGE -> permissions.hasAnyPermissions(context.actorId(), "pms:project-template:create", "pms:project-template:update");
            case PUBLISH -> permissions.hasAnyPermissions(context.actorId(), "pms:project-template:publish");
            case DISABLE -> permissions.hasAnyPermissions(context.actorId(), "pms:project-template:disable");
        };
    }
    @Override public Dependencies validateConfiguration(Context context, Long dynamicFormRevisionId, ValidationMode mode) {
        if (!trusted(context) || mode == null || dynamicFormRevisionId != null) throw new IllegalArgumentException("业务页面配置上下文无效");
        return new Dependencies(false);
    }
    private boolean trusted(Context context) {
        return context != null && context.tenantId() != null && context.actorId() != null
                && Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())
                && Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId());
    }
}
