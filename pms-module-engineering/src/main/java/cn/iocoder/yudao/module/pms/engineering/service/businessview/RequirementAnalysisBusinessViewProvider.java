package cn.iocoder.yudao.module.pms.engineering.service.businessview;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class RequirementAnalysisBusinessViewProvider implements BusinessViewComponentProvider {

    private final PermissionApi permissionApi;

    @Override
    public BusinessViewComponentProvider.Component component() {
        return new BusinessViewComponentProvider.Component(
                "REQUIREMENT_ANALYSIS", "SOL", ViewSource.PAGE,
                "PROJ_REQUIREMENT_ANALYSIS", "1",
                JsonUtils.parseTree("""
                        {"type":"object","required":["project"],"properties":{
                          "project":{"type":"object","required":["id","version"],"properties":{
                            "id":{"oneOf":[{"type":"integer","minimum":1},{"type":"string","pattern":"^[1-9][0-9]*$"}]},
                            "version":{"type":"integer","minimum":0}}}}}
                        """),
                JsonUtils.parseTree("[\"CREATE_INITIAL_DRAFT\",\"PATCH_FORM\",\"COMPLETE\",\"CREATE_DRAFT\"]"),
                "SOL_REQUIREMENT_ANALYSIS_QUERY", "SOL_REQUIREMENT_ANALYSIS_COMMAND",
                "SOL_REQUIREMENT_ANALYSIS_PERMISSION");
    }

    @Override
    public boolean canConfigure(Context context, ConfigurationAction action) {
        if (!trusted(context) || action == null) return false;
        String permission = switch (action) {
            case QUERY -> "pms:project-template:query";
            case MANAGE -> "pms:project-template:update";
            case PUBLISH -> "pms:project-template:publish";
            case DISABLE -> "pms:project-template:disable";
        };
        return permissionApi.hasAnyPermissions(context.actorId(), permission);
    }

    @Override
    public Dependencies validateConfiguration(Context context, Long dynamicFormRevisionId,
                                               ValidationMode mode) {
        if (!trusted(context) || mode == null || dynamicFormRevisionId != null) {
            throw new IllegalArgumentException("页面视图上下文无效或混入动态表单修订");
        }
        // 页面内的业务查询和写入仍调用SOL原有API，配置检查不读取项目或表单实例。
        return new Dependencies(false);
    }

    private boolean trusted(Context context) {
        return context != null && context.tenantId() != null && context.tenantId() >= 0
                && context.actorId() != null && context.actorId() > 0
                && Objects.equals(context.tenantId(), TenantContextHolder.getTenantId());
    }
}
