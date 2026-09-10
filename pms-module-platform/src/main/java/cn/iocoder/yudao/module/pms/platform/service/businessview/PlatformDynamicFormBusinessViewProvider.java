package cn.iocoder.yudao.module.pms.platform.service.businessview;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.*;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormActionProjection;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormCommands;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormQueryService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * PM-03: deployed PLATFORM adapter for the existing manual dynamic-form instance capability.
 * Keys identify DynamicFormQueryService/getInstance, DynamicFormCommandService/patchInstance,
 * and DynamicFormActionProjection. No generic business-Owner API and no instance creation.
 */
@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class PlatformDynamicFormBusinessViewProvider implements BusinessViewComponentProvider {
    private final BusinessViewAccess access;
    private final PermissionApi permissionApi;
    private final DynamicFormQueryService queryService;
    private final DynamicFormTemplateMapper templateMapper;
    private final DynamicFormTemplateRevisionMapper revisionMapper;

    @Override
    public Component component() {
        return new Component("DYNAMIC_FORM_INSTANCE", "PLATFORM", ViewSource.DYNAMIC_FORM,
                "PLATFORM_DYNAMIC_FORM", "1",
                JsonUtils.parseTree("{\"type\":\"object\",\"properties\":{\"instanceId\":{\"oneOf\":[{\"type\":\"integer\",\"minimum\":1},{\"type\":\"string\",\"pattern\":\"^[1-9][0-9]*$\"}]}},\"required\":[\"instanceId\"]}"),
                JsonUtils.parseTree("[\"QUERY_INSTANCE\",\"PATCH_INSTANCE\"]"),
                "PLATFORM_DYNAMIC_FORM_QUERY", "PLATFORM_DYNAMIC_FORM_COMMAND", "PLATFORM_DYNAMIC_FORM_PERMISSION", "动态表单");
    }

    @Override
    public boolean canConfigure(Context context, ConfigurationAction action) {
        requireTrusted(context);
        String permission = switch (action) {
            case QUERY -> DynamicFormActionProjection.TEMPLATE_QUERY;
            case MANAGE -> DynamicFormActionProjection.TEMPLATE_MANAGE;
            case PUBLISH, DISABLE -> DynamicFormActionProjection.TEMPLATE_PUBLISH;
        };
        return permissionApi.hasAnyPermissions(context.actorId(), permission);
    }

    @Override
    public Dependencies validateConfiguration(Context context, Long revisionId, ValidationMode mode) {
        requireTrusted(context);
        if (revisionId == null || revisionId <= 0 || mode == null) return new Dependencies(false);
        // getRevision rechecks existing Owner query permission with the genuine authenticated actor.
        var inspected = queryService.getRevision(new DynamicFormCommands.Actor(context.tenantId(), context.actorId()), revisionId);
        var template = mode == ValidationMode.LOCK_FOR_PUBLISH
                ? templateMapper.selectForUpdate(new DynamicFormTemplateLockQuery(context.tenantId(), inspected.templateId()))
                : templateMapper.selectByRow(new DynamicFormTemplateRowQuery(context.tenantId(), inspected.templateId()));
        var revision = mode == ValidationMode.LOCK_FOR_PUBLISH
                ? revisionMapper.selectForUpdate(new DynamicFormRevisionLockQuery(context.tenantId(), inspected.templateId(), revisionId))
                : revisionMapper.selectByRow(new DynamicFormRevisionRowQuery(context.tenantId(), revisionId));
        boolean available = template != null && revision != null
                && Objects.equals(template.getTenantId(), context.tenantId())
                && Objects.equals(revision.getTenantId(), context.tenantId())
                && Objects.equals(revision.getId(), revisionId)
                && Objects.equals(revision.getTemplateId(), template.getId())
                && "ENABLED".equals(template.getAvailabilityCode())
                && "PUBLISHED".equals(revision.getStatusCode()) && revision.getPublishedAt() != null;
        return new Dependencies(available);
    }

    record Dependency(Long templateId, Long revisionId) { }

    Dependency resolveDependency(Context context, Long revisionId) {
        requireTrusted(context);
        if (revisionId == null || revisionId <= 0) throw exception(BusinessViewErrors.UNAVAILABLE);
        var revision = queryService.getRevision(new DynamicFormCommands.Actor(context.tenantId(), context.actorId()), revisionId);
        return new Dependency(revision.templateId(), revisionId);
    }

    void lockDependencies(Context context, java.util.Collection<Dependency> dependencies) {
        requireTrusted(context);
        var ordered = dependencies.stream().distinct().sorted(java.util.Comparator.comparing(Dependency::templateId)
                .thenComparing(Dependency::revisionId)).toList();
        for (Long templateId : ordered.stream().map(Dependency::templateId).distinct().toList()) {
            var template = templateMapper.selectForUpdate(new DynamicFormTemplateLockQuery(context.tenantId(), templateId));
            if (template == null || !Objects.equals(template.getTenantId(), context.tenantId())
                    || !"ENABLED".equals(template.getAvailabilityCode())) throw exception(BusinessViewErrors.UNAVAILABLE);
        }
        for (Dependency dependency : ordered) {
            var revision = revisionMapper.selectForUpdate(new DynamicFormRevisionLockQuery(context.tenantId(), dependency.templateId(), dependency.revisionId()));
            if (revision == null || !Objects.equals(revision.getTenantId(), context.tenantId())
                    || !Objects.equals(revision.getTemplateId(), dependency.templateId())
                    || !"PUBLISHED".equals(revision.getStatusCode()) || revision.getPublishedAt() == null)
                throw exception(BusinessViewErrors.UNAVAILABLE);
        }
    }

    private void requireTrusted(Context context) {
        if (!access.context().equals(context)) throw exception(FORBIDDEN);
    }
}
