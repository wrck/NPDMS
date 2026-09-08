package cn.iocoder.yudao.module.pms.platform.api.businessview;

import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.Set;

/** PM-03: exact registration metadata only, never an Owner object permission grant. */
public record BusinessViewRevision(Long id, String entityType, String viewKey, Long revisionNo,
        String ownerContext, BusinessViewComponentProvider.ViewSource viewSource,
        String componentKey, String componentVersion, Long dynamicFormRevisionId,
        JsonNode contextSchema, JsonNode supportedActions, String queryProviderKey,
        String commandProviderKey, String permissionProviderKey, LocalDateTime publishedAt,
        LocalDateTime disabledAt, Integer version, String status, Set<String> allowedActions) {
    public BusinessViewRevision {
        contextSchema = contextSchema.deepCopy();
        supportedActions = supportedActions.deepCopy();
        allowedActions = Set.copyOf(allowedActions);
    }
    @Override public JsonNode contextSchema() { return contextSchema.deepCopy(); }
    @Override public JsonNode supportedActions() { return supportedActions.deepCopy(); }
}
