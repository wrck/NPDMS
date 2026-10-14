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

/** 项目当前范围的割接覆盖只读视图，由 IMP 提供完成事实。 */
@Component
@RequiredArgsConstructor
public class CutoverCoverageBusinessViewProvider implements BusinessViewComponentProvider {
    private final PermissionApi permissions;
    @Override public BusinessViewComponentProvider.Component component() {
        return new BusinessViewComponentProvider.Component("CUTOVER_COVERAGE", "IMP", ViewSource.PAGE,
                "IMP_CUTOVER_COVERAGE", "1",
                JsonUtils.parseTree("""
                        {"type":"object","required":["projectId"],"properties":{
                        "projectId":{"oneOf":[{"type":"integer","minimum":1},{"type":"string","pattern":"^[1-9][0-9]*$"}]}}}
                        """), JsonUtils.parseTree("[\"QUERY\"]"),
                "IMP_CUTOVER_COVERAGE_QUERY", "IMP_CUTOVER_COVERAGE_COMMAND", "IMP_CUTOVER_COVERAGE_PERMISSION", "项目割接范围覆盖");
    }
    @Override public Set<String> pagePaths() { return Set.of("/pms/cutover/project-coverage"); }
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
