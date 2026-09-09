package cn.iocoder.yudao.module.pms.platform.domain.businessview;

import tools.jackson.databind.JsonNode;

import static cn.iocoder.yudao.module.pms.platform.domain.businessview.BusinessViewRules.*;

/**
 * PM-03 / F-PLT-003 / SDS08: immutable registration configuration, not a trusted component
 * declaration. Provider keys and supported actions do not grant object permissions.
 */
public record BusinessViewDescriptor(
        long tenantId,
        String entityType,
        String ownerContext,
        String viewKey,
        long revisionNo,
        ViewSource viewSource,
        String componentKey,
        String componentVersion,
        Long dynamicFormRevisionId,
        JsonNode contextSchema,
        JsonNode supportedActions,
        String queryProviderKey,
        String commandProviderKey,
        String permissionProviderKey) {

    public enum ViewSource { PAGE, DYNAMIC_FORM }

    public BusinessViewDescriptor {
        require(tenantId >= 0, "tenantId: must be nonnegative");
        entityType = key(entityType, "entityType");
        ownerContext = key(ownerContext, "ownerContext");
        viewKey = key(viewKey, "viewKey");
        require(revisionNo > 0, "revisionNo: must be positive");
        require(viewSource != null, "viewSource: required");
        componentKey = key(componentKey, "componentKey");
        componentVersion = BusinessViewRules.componentVersion(componentVersion);
        if (viewSource == ViewSource.PAGE) {
            require(dynamicFormRevisionId == null, "PAGE: dynamicFormRevisionId forbidden");
        } else {
            require(dynamicFormRevisionId != null && dynamicFormRevisionId > 0,
                    "DYNAMIC_FORM: positive dynamicFormRevisionId required");
        }
        contextSchema = schema(contextSchema);
        supportedActions = actions(supportedActions, "supportedActions");
        queryProviderKey = key(queryProviderKey, "queryProviderKey");
        commandProviderKey = key(commandProviderKey, "commandProviderKey");
        permissionProviderKey = key(permissionProviderKey, "permissionProviderKey");
    }

    @Override
    public JsonNode contextSchema() {
        return contextSchema.deepCopy();
    }

    @Override
    public JsonNode supportedActions() {
        return supportedActions.deepCopy();
    }

    public BusinessViewDescriptor withRevision(long newRevisionNo) {
        return new BusinessViewDescriptor(tenantId, entityType, ownerContext, viewKey, newRevisionNo,
                viewSource, componentKey, componentVersion, dynamicFormRevisionId, contextSchema,
                supportedActions, queryProviderKey, commandProviderKey, permissionProviderKey);
    }
}
