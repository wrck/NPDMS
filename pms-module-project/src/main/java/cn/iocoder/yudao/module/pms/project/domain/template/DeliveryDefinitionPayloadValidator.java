package cn.iocoder.yudao.module.pms.project.domain.template;

import tools.jackson.databind.JsonNode;
import java.util.*;

/** PM-03 / SDS10: schema v1; no script or client-selected Provider implementation. */
public final class DeliveryDefinitionPayloadValidator {
    private DeliveryDefinitionPayloadValidator() { }
    public static final Set<String> PREDICATES = Set.of("TASK_NATIVE_STATUS", "STAGE_NATIVE_STATUS",
            "TASK", "MILESTONE", "DELIVERABLE", "STATE", "APPROVAL", "PROCESS");
    public static final Set<String> BINDING_TYPES = Set.of("STAGE_NATIVE", "TASK_NATIVE", "BUSINESS_OBJECT",
            "BUSINESS_COMPONENT", "DYNAMIC_FORM", "APPROVAL", "COMPOSITE");

    public static void validate(DeliveryDefinitionKind kind, Integer schemaVersion, JsonNode payload,
                                List<DeliveryDefinitionReference> references) {
        require(kind != null && Integer.valueOf(1).equals(schemaVersion), "definitionKind/schemaVersion");
        object(payload, "payload");
        require(references != null, "references");
        Set<String> keys = new HashSet<>();
        for (DeliveryDefinitionReference ref : references) {
            require(ref != null && code(ref.referenceKey()) && ref.targetRevisionId() != null
                    && ref.targetRevisionId() > 0 && keys.add(ref.referenceKey()), "references: duplicate/invalid slot");
        }
        switch (kind) {
            case STAGE, TASK -> {
                fields(payload, kind == DeliveryDefinitionKind.STAGE
                        ? Set.of("name", "stageCode", "start", "terminal", "workBinding", "permissionPolicy", "completionRule")
                        : Set.of("name", "workBinding", "permissionPolicy", "completionRule"));
                text(payload, "name");
                for (String slot : List.of("workBinding", "permissionPolicy", "completionRule")) {
                    text(payload, slot);
                    require(keys.contains(payload.path(slot).asText()), slot + ": missing reference");
                }
                if (kind == DeliveryDefinitionKind.STAGE) {
                    require(payload.path("stageCode").isTextual() && payload.path("stageCode").asText().matches("S[0-6]"), "stageCode");
                    bool(payload, "start"); bool(payload, "terminal");
                }
            }
            case WORK_BINDING -> {
                fields(payload, Set.of("bindingType", "instanceResolutionStrategy", "businessViewRevisionId",
                        "targetContextCode", "targetObjectType", "targetObjectKey", "contextMapping"));
                require(BINDING_TYPES.contains(text(payload, "bindingType")), "bindingType");
                require(Set.of("REFERENCE_EXISTING", "CREATE_ON_ENTER", "CREATE_ON_FIRST_ACTION", "READ_ONLY_AGGREGATE")
                        .contains(text(payload, "instanceResolutionStrategy")), "instanceResolutionStrategy");
                object(payload.path("contextMapping"), "contextMapping");
                for (var entry : payload.path("contextMapping").properties()) {
                    require(code(entry.getKey()) && entry.getValue().isTextual()
                            && code(entry.getValue().asText()), "contextMapping: controlled context key required");
                }
                if (payload.path("bindingType").asText().endsWith("_NATIVE")) {
                    for (String key : List.of("businessViewRevisionId", "targetContextCode", "targetObjectType", "targetObjectKey"))
                        require(!payload.hasNonNull(key), "native binding forbids " + key);
                    require(payload.path("contextMapping").isEmpty(), "native contextMapping must be empty");
                } else {
                    positive(payload, "businessViewRevisionId");
                    for (String key : List.of("targetContextCode", "targetObjectType", "targetObjectKey"))
                        require(code(text(payload, key)), key);
                }
            }
            case COMPLETION_RULE -> rule(payload);
            case PERMISSION_POLICY -> {
                fields(payload, Set.of("requiredActions")); strings(payload.path("requiredActions"), "requiredActions");
            }
            case DELIVERABLE -> {
                fields(payload, Set.of("scope", "deliverableType", "required", "minimumQuantity", "allowedSources", "outputType", "confirmationRule"));
                require(Set.of("STAGE", "TASK").contains(text(payload, "scope")), "scope");
                text(payload, "deliverableType"); text(payload, "outputType"); bool(payload, "required");
                JsonNode quantity = payload.path("minimumQuantity");
                require(quantity.isIntegralNumber() && quantity.canConvertToLong() && quantity.asLong() >= 0
                        && (!payload.path("required").asBoolean() || quantity.asLong() > 0), "minimumQuantity");
                strings(payload.path("allowedSources"), "allowedSources"); rule(payload.path("confirmationRule"));
            }
            case GATE -> {
                fields(payload, Set.of("gateType", "references"));
                require(Set.of("ENTRY", "EXIT").contains(text(payload, "gateType")), "gateType");
                JsonNode refs = payload.path("references"); require(refs.isArray() && !refs.isEmpty(), "gate references");
                Set<String> seen = new HashSet<>();
                for (JsonNode ref : refs) {
                    fields(ref, Set.of("refType", "refCode"));
                    require(Set.of("TASK", "MILESTONE", "DELIVERABLE", "STATE", "APPROVAL", "PROCESS")
                            .contains(text(ref, "refType")), "refType");
                    String refCode = text(ref, "refCode");
                    require(code(refCode) && seen.add(ref.path("refType").asText() + ":" + refCode), "gate duplicate/reference code");
                    if ("STATE".equals(ref.path("refType").asText())) require(refCode.matches("S[0-6]_COMPLETED"), "state refCode");
                }
            }
            case MILESTONE -> {
                fields(payload, Set.of("name", "criteria")); text(payload, "name"); text(payload, "criteria");
            }
        }
    }

    public static void rule(JsonNode rule) {
        object(rule, "rule");
        if (rule.has("operator")) {
            fields(rule, Set.of("operator", "rules"));
            require(Set.of("ALL", "ANY").contains(text(rule, "operator")), "operator");
            JsonNode children = rule.path("rules"); require(children.isArray() && !children.isEmpty(), "rules");
            for (JsonNode child : children) rule(child);
        } else {
            fields(rule, Set.of("predicate", "parameters"));
            String predicate = text(rule, "predicate"); require(PREDICATES.contains(predicate), "unregistered predicate");
            JsonNode parameters = rule.path("parameters");
            if (predicate.endsWith("_NATIVE_STATUS")) {
                fields(parameters, Set.of("requiredStatus"));
                require("DONE".equals(text(parameters, "requiredStatus")), "requiredStatus");
            } else {
                fields(parameters, Set.of("refCode")); String refCode = text(parameters, "refCode");
                require(code(refCode), "refCode");
                if ("STATE".equals(predicate)) require(refCode.matches("S[0-6]_COMPLETED"), "state refCode");
            }
        }
    }

    public static boolean code(String value) { return value != null && value.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}"); }
    private static void fields(JsonNode node, Set<String> allowed) {
        object(node, "object");
        for (String key : node.propertyNames()) require(allowed.contains(key), "unknown field: " + key);
    }
    private static void object(JsonNode node, String name) { require(node != null && node.isObject(), name + ": object required"); }
    private static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        require(value.isTextual() && !value.asText().isBlank(), name + ": text required"); return value.asText();
    }
    private static void bool(JsonNode node, String name) { require(node.path(name).isBoolean(), name + ": boolean required"); }
    private static void positive(JsonNode node, String name) {
        JsonNode value = node.path(name); require(value.isIntegralNumber() && value.canConvertToLong() && value.asLong() > 0, name);
    }
    private static void strings(JsonNode node, String name) {
        require(node.isArray() && !node.isEmpty(), name + ": nonempty array required"); Set<String> seen = new HashSet<>();
        for (JsonNode value : node) require(value.isTextual() && code(value.asText()) && seen.add(value.asText()), name);
    }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
}
