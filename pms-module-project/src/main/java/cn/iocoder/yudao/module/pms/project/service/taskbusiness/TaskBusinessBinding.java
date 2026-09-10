package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import tools.jackson.databind.JsonNode;
import java.util.Set;
import static cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessErrors.failure;

record TaskBusinessBinding(String ownerContext, String objectType, String componentKey,
                           Long businessViewRevisionId, String instanceResolutionStrategy) {
    static TaskBusinessBinding parse(ProjectTaskExecutionContractDO contract) {
        if (contract == null) throw failure("CONTRACT_NOT_FOUND");
        JsonNode root;
        try { root = JsonUtils.parseTree(contract.getBindingParameterSnapshot()); }
        catch (RuntimeException ex) { throw failure("BINDING_SNAPSHOT_INVALID"); }
        if (root == null || !root.isObject()) throw failure("BINDING_SNAPSHOT_INVALID");
        agree(root, "targetContextCode", contract.getTargetContextCode());
        agree(root, "targetObjectType", contract.getTargetObjectType());
        agree(root, "componentKey", contract.getComponentKey());
        agree(root, "bindingType", contract.getWorkBindingTypeCode());
        if (root.hasNonNull("contextMapping")) {
            JsonNode mapping = root.get("contextMapping");
            if (!mapping.isObject()) throw failure("BINDING_SNAPSHOT_INVALID");
            for (var entry : mapping.properties()) {
                if (!entry.getValue().isTextual()) throw failure("BINDING_SNAPSHOT_INVALID");
            }
        }
        JsonNode view = root.path("businessViewRevisionId");
        Long revision = null;
        if (!view.isMissingNode()) {
            if (view.isIntegralNumber() && view.canConvertToLong() && view.asLong() > 0) {
                revision = view.asLong();
            } else if (view.isTextual() && view.asText().matches("[1-9][0-9]*")) {
                try {
                    // Framework Long JSON values may be decimal strings; never round through double.
                    revision = Long.valueOf(view.asText());
                } catch (NumberFormatException ex) {
                    throw failure("BINDING_SNAPSHOT_INVALID");
                }
            } else {
                throw failure("BINDING_SNAPSHOT_INVALID");
            }
        }
        String strategy = null;
        if (root.hasNonNull("instanceResolutionStrategy")) {
            JsonNode value = root.get("instanceResolutionStrategy");
            if (!value.isTextual() || !Set.of("REFERENCE_EXISTING", "CREATE_ON_ENTER",
                    "CREATE_ON_FIRST_ACTION", "READ_ONLY_AGGREGATE").contains(value.asText()))
                throw failure("BINDING_SNAPSHOT_INVALID");
            strategy = value.asText();
        }
        return new TaskBusinessBinding(contract.getTargetContextCode(), contract.getTargetObjectType(),
                contract.getComponentKey(), revision, strategy);
    }
    String unavailableReason() {
        if (ownerContext == null || ownerContext.isBlank() || objectType == null || objectType.isBlank())
            return "OWNER_NOT_FROZEN";
        if (businessViewRevisionId == null) return "VIEW_NOT_FROZEN";
        if (componentKey == null || componentKey.isBlank()) return "COMPONENT_NOT_FROZEN";
        if (instanceResolutionStrategy == null) return "INSTANCE_STRATEGY_NOT_FROZEN";
        return null;
    }
    private static void agree(JsonNode root, String key, String frozen) {
        if (root.hasNonNull(key) && (!root.get(key).isTextual() || !root.get(key).asText().equals(frozen)))
            throw failure("BINDING_SNAPSHOT_CONFLICT");
    }
}
