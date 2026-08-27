package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

@Component
@RequiredArgsConstructor
public class DynamicFormActionProjection {

    public static final String TEMPLATE_QUERY = "pms:dynamic-form-template:query";
    public static final String TEMPLATE_MANAGE = "pms:dynamic-form-template:manage";
    public static final String TEMPLATE_PUBLISH = "pms:dynamic-form-template:publish";
    public static final String INSTANCE_QUERY = "pms:dynamic-form-instance:query";
    public static final String INSTANCE_CREATE = "pms:dynamic-form-instance:create";
    public static final String INSTANCE_UPDATE = "pms:dynamic-form-instance:update";

    private final PermissionApi permissionApi;

    public Set<String> templateActions(Long actorId, String availability,
                                       boolean hasDraft, boolean hasPublished) {
        try {
            Set<String> actions = new LinkedHashSet<>();
            if (has(actorId, TEMPLATE_MANAGE)) {
                actions.add("PATCH_TEMPLATE");
                if (hasDraft) {
                    actions.add("PATCH_REVISION");
                } else if (hasPublished) {
                    actions.add("CREATE_REVISION");
                }
            }
            if (has(actorId, TEMPLATE_PUBLISH)) {
                if (hasDraft) {
                    actions.add("PUBLISH_REVISION");
                }
                if (hasPublished) {
                    actions.add("ENABLED".equals(availability) ? "DISABLE" : "ENABLE");
                }
            }
            return Set.copyOf(actions);
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    public Set<String> revisionActions(Long actorId, String status) {
        try {
            if (!"DRAFT".equals(status)) {
                return Set.of();
            }
            Set<String> actions = new LinkedHashSet<>();
            if (has(actorId, TEMPLATE_MANAGE)) {
                actions.add("PATCH_REVISION");
            }
            if (has(actorId, TEMPLATE_PUBLISH)) {
                actions.add("PUBLISH_REVISION");
            }
            return Set.copyOf(actions);
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    public Set<String> selectionActions(Long actorId, String availability, boolean currentPublished) {
        try {
            return "ENABLED".equals(availability) && currentPublished && has(actorId, INSTANCE_CREATE)
                    ? Set.of("CREATE_INSTANCE") : Set.of();
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    public Set<String> instanceActions(Long actorId, Long createdBy) {
        try {
            return actorId != null && actorId.equals(createdBy) && has(actorId, INSTANCE_UPDATE)
                    ? Set.of("PATCH_INSTANCE") : Set.of();
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    public void require(Long actorId, String permission) {
        if (actorId == null || !has(actorId, permission)) {
            throw exception(FORBIDDEN);
        }
    }

    private boolean has(Long actorId, String permission) {
        return actorId != null && permissionApi.hasAnyPermissions(actorId, permission);
    }
}
