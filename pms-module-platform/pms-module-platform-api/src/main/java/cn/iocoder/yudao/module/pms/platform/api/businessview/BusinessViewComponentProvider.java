package cn.iocoder.yudao.module.pms.platform.api.businessview;

import tools.jackson.databind.JsonNode;

/**
 * PM-03 / F-PLT-003: implemented as a Spring bean by the deployed component's actual Owner.
 * This is a configuration capability, NOT an object authorization or a generic command router.
 * The Owner's query/command/permission keys must name its existing capabilities; a declaration
 * alone must never be used as evidence of a remote Provider being online.
 */
public interface BusinessViewComponentProvider {

    Component component();

    /** Recheck existing Owner configuration permissions for the authenticated context. */
    boolean canConfigure(Context context, ConfigurationAction action);

    /**
     * Read actual dependencies, without reading instance values or creating objects.
     * LOCK_FOR_PUBLISH joins the caller transaction, after registration identity/revision locks.
     * An unavailable or unknown dependency must fail closed, never return an optimistic default.
     */
    Dependencies validateConfiguration(Context context, Long dynamicFormRevisionId, ValidationMode mode);

    enum ViewSource { PAGE, DYNAMIC_FORM }
    enum ConfigurationAction { QUERY, MANAGE, PUBLISH, DISABLE }
    enum ValidationMode { INSPECT, LOCK_FOR_PUBLISH }
    record Context(Long tenantId, Long actorId) { }
    record Dependencies(boolean dynamicFormAvailable) { }

    /** Independent, code-owned directory declaration; never construct from a registration body. */
    record Component(String entityType, String ownerContext, ViewSource viewSource,
                     String componentKey, String componentVersion, JsonNode contextSchema,
                     JsonNode supportedActions, String queryProviderKey, String commandProviderKey,
                     String permissionProviderKey, String displayName) {
        public Component {
            contextSchema = contextSchema == null ? null : contextSchema.deepCopy();
            supportedActions = supportedActions == null ? null : supportedActions.deepCopy();
        }
        @Override public JsonNode contextSchema() { return contextSchema == null ? null : contextSchema.deepCopy(); }
        @Override public JsonNode supportedActions() { return supportedActions == null ? null : supportedActions.deepCopy(); }
    }
}
