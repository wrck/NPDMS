package cn.iocoder.yudao.module.pms.engineering.service.businessview;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** REQ-PROJ-004 / F-PLT-003: code-owned page declaration, separate from PRE02 preparation. */
@Component
@RequiredArgsConstructor
public class SiteSurveyBusinessViewProvider implements BusinessViewComponentProvider {

    private final PermissionApi permissionApi;

    @Override
    public BusinessViewComponentProvider.Component component() {
        return new BusinessViewComponentProvider.Component("SITE_SURVEY", "SOL", ViewSource.PAGE,
                "SOL_SITE_SURVEY", "1",
                JsonUtils.parseTree("""
                        {"type":"object","required":["projectId"],"properties":{
                          "projectId":{"oneOf":[{"type":"integer","minimum":1},
                            {"type":"string","pattern":"^[1-9][0-9]*$"}]}}}
                        """),
                JsonUtils.parseTree("[\"QUERY\",\"CREATE\",\"UPDATE\",\"CONFIRM\",\"REJECT\",\"ARCHIVE\",\"LINK\",\"UNLINK\"]"),
                "SOL_SITE_SURVEY_QUERY", "SOL_SITE_SURVEY_COMMAND", "SOL_SITE_SURVEY_PERMISSION", "现场工勘（原业务页面）");
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
            throw new IllegalArgumentException("工勘页面配置上下文无效或混入动态表单修订");
        }
        // No instance/CRUD dependency: PAGE registration cannot initialize a project survey.
        return new Dependencies(false);
    }

    private boolean trusted(Context context) {
        return context != null && context.tenantId() != null && context.tenantId() >= 0
                && context.actorId() != null && context.actorId() > 0
                && Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())
                && Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId());
    }
}
