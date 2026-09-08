package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.*;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationErrors.*;

/** PM-03: resolves publication closure in the existing template, not a second template model. */
@Service @RequiredArgsConstructor
public class TemplateDefinitionReferenceAssembler {
    private final DeliveryDefinitionResolver resolver;

    public void resolve(TemplateDefinitionContent content, boolean lock) {
        List<DeliveryDefinitionReference> roots = new ArrayList<>();
        if (content.getStages() == null || content.getTasks() == null || content.getMilestones() == null
                || content.getDeliverables() == null || content.getGates() == null || content.getTransitions() == null)
            throw exception(INVALID, "template element arrays required");
        for (var stage : content.getStages()) {
            if (stage == null) throw exception(INVALID, "stage");
            root(roots, stage.getDefinitionRevisionId()); root(roots, stage.getWorkBindingRevisionId());
            root(roots, stage.getPermissionPolicyRevisionId()); root(roots, stage.getCompletionRuleRevisionId());
        }
        for (var task : content.getTasks()) {
            if (task == null) throw exception(INVALID, "task");
            root(roots, task.getDefinitionRevisionId()); optional(roots, task.getWorkBindingRevisionId());
            optional(roots, task.getPermissionPolicyRevisionId()); optional(roots, task.getCompletionRuleRevisionId());
        }
        for (var row : content.getMilestones()) { if (row == null) throw exception(INVALID, "milestone"); root(roots, row.getDefinitionRevisionId()); }
        for (var row : content.getDeliverables()) { if (row == null) throw exception(INVALID, "deliverable"); root(roots, row.getDefinitionRevisionId()); }
        for (var row : content.getGates()) { if (row == null) throw exception(INVALID, "gate"); root(roots, row.getDefinitionRevisionId()); }
        for (var edge : content.getTransitions()) {
            if (edge == null || edge.getRevisionNo() == null || edge.getRevisionNo() <= 0) throw exception(INVALID, "transition revisionNo");
            optional(roots, edge.getConditionRuleRevisionId());
        }
        Map<Long, Snapshot> closure = resolver.resolve(roots, null, lock);
        validateRuleTargets(content, closure.values());
        for (var stage : content.getStages()) {
            Revision definition = resolver.require(closure, stage.getDefinitionRevisionId(), DeliveryDefinitionKind.STAGE);
            if (!Objects.equals(stage.getStageCode(), definition.payload().path("stageCode").asText())) throw exception(REFERENCE_INVALID, "stageCode override");
            Revision binding = binding(closure, stage.getWorkBindingRevisionId(), "STAGE_NATIVE");
            resolver.require(closure, stage.getPermissionPolicyRevisionId(), DeliveryDefinitionKind.PERMISSION_POLICY);
            resolver.require(closure, stage.getCompletionRuleRevisionId(), DeliveryDefinitionKind.COMPLETION_RULE);
            enforceOwner(definition, binding, closure);
        }
        for (var task : content.getTasks()) {
            Revision definition = resolver.require(closure, task.getDefinitionRevisionId(), DeliveryDefinitionKind.TASK);
            task.setWorkBindingRevisionId(slot(definition, "workBinding", task.getWorkBindingRevisionId()));
            task.setPermissionPolicyRevisionId(slot(definition, "permissionPolicy", task.getPermissionPolicyRevisionId()));
            task.setCompletionRuleRevisionId(slot(definition, "completionRule", task.getCompletionRuleRevisionId()));
            Revision binding = binding(closure, task.getWorkBindingRevisionId(), "TASK_NATIVE");
            Revision permission = resolver.require(closure, task.getPermissionPolicyRevisionId(), DeliveryDefinitionKind.PERMISSION_POLICY);
            Revision completion = resolver.require(closure, task.getCompletionRuleRevisionId(), DeliveryDefinitionKind.COMPLETION_RULE);
            enforceOwner(definition, binding, closure);
            applyTask(task, definition, binding, permission, completion, closure.get(binding.id()));
        }
        for (var row : content.getMilestones()) resolver.require(closure, row.getDefinitionRevisionId(), DeliveryDefinitionKind.MILESTONE);
        for (var row : content.getDeliverables()) {
            JsonNode payload = resolver.require(closure, row.getDefinitionRevisionId(), DeliveryDefinitionKind.DELIVERABLE).payload();
            if (!Objects.equals(row.getTaskCode() == null ? "STAGE" : "TASK", payload.path("scope").asText())
                    || !Objects.equals(row.getRequired(), payload.path("required").asBoolean()))
                throw exception(REFERENCE_INVALID, "deliverable scope/required override");
        }
        for (var row : content.getGates()) {
            JsonNode payload = resolver.require(closure, row.getDefinitionRevisionId(), DeliveryDefinitionKind.GATE).payload();
            if (!Objects.equals(row.getGateType(), payload.path("gateType").asText())) throw exception(REFERENCE_INVALID, "gateType override");
            List<TemplateDefinitionContent.GateRef> refs = new ArrayList<>();
            for (JsonNode ref : payload.path("references")) {
                var target = new TemplateDefinitionContent.GateRef(); target.setRefType(ref.path("refType").asText());
                target.setRefCode(ref.path("refCode").asText()); refs.add(target);
            }
            if (row.getReferences() != null && !row.getReferences().isEmpty()
                    && !sameGateReferences(row.getReferences(), refs)) throw exception(REFERENCE_INVALID, "gate references override");
            row.setReferences(refs);
        }
        for (var edge : content.getTransitions()) if (edge.getConditionRuleRevisionId() != null)
            resolver.require(closure, edge.getConditionRuleRevisionId(), DeliveryDefinitionKind.COMPLETION_RULE);
        // Snapshot is regenerated from authoritative rows, never copied from submitted JSON.
        content.setDefinitionSnapshot(JsonUtils.parseObject(JsonUtils.toJsonString(closure.values()), JsonNode.class));
    }

    static boolean sameGateReferences(List<TemplateDefinitionContent.GateRef> actual,
                                      List<TemplateDefinitionContent.GateRef> expected) {
        if (actual == null || expected == null || actual.size() != expected.size()
                || actual.stream().anyMatch(Objects::isNull) || expected.stream().anyMatch(Objects::isNull)) return false;
        var order = Comparator.comparing(TemplateDefinitionContent.GateRef::getRefType,
                        Comparator.nullsFirst(String::compareTo))
                .thenComparing(TemplateDefinitionContent.GateRef::getRefCode, Comparator.nullsFirst(String::compareTo))
                .thenComparing(TemplateDefinitionContent.GateRef::getRefVersion, Comparator.nullsFirst(String::compareTo));
        return actual.stream().sorted(order).toList().equals(expected.stream().sorted(order).toList());
    }

    /** PM-03: configured facts must belong to this template, including nested branch/confirmation rules. */
    void validateRuleTargets(TemplateDefinitionContent content, Collection<Snapshot> closure) {
        Map<String, Set<String>> targets = Map.of(
                "TASK", new HashSet<>(), "MILESTONE", new HashSet<>(),
                "DELIVERABLE", new HashSet<>(), "STATE", new HashSet<>());
        for (var task : content.getTasks()) targets.get("TASK").add(task.getTaskCode());
        for (var milestone : content.getMilestones()) targets.get("MILESTONE").add(milestone.getMilestoneCode());
        for (var deliverable : content.getDeliverables()) targets.get("DELIVERABLE").add(deliverable.getDeliverableCode());
        for (var stage : content.getStages()) targets.get("STATE").add(stage.getStageCode() + "_COMPLETED");
        for (Snapshot snapshot : closure) {
            Revision definition = snapshot.definition();
            if (definition.definitionKind() == DeliveryDefinitionKind.COMPLETION_RULE)
                validateRuleTarget(definition.payload(), targets, "definitions[" + definition.id() + "].payload");
            else if (definition.definitionKind() == DeliveryDefinitionKind.DELIVERABLE)
                validateRuleTarget(definition.payload().path("confirmationRule"), targets,
                        "definitions[" + definition.id() + "].payload.confirmationRule");
        }
    }

    private void validateRuleTarget(JsonNode rule, Map<String, Set<String>> targets, String path) {
        if (rule.has("operator")) {
            int index = 0;
            for (JsonNode child : rule.path("rules")) validateRuleTarget(child, targets, path + ".rules[" + index++ + "]");
            return;
        }
        String predicate = rule.path("predicate").asText();
        Set<String> configuredTargets = targets.get(predicate);
        // Native predicates use the consuming node; BPM facts retain their existing Owner validation.
        if (configuredTargets == null) return;
        JsonNode refCode = rule.path("parameters").path("refCode");
        if (!refCode.isTextual() || !configuredTargets.contains(refCode.asText()))
            throw exception(REFERENCE_INVALID, path + ".parameters.refCode: " + predicate
                    + " target is not configured in this template: " + refCode.asText());
    }

    private void applyTask(TemplateDefinitionContent.TaskDef task, Revision definition, Revision binding,
                           Revision permission, Revision completion, Snapshot bindingSnapshot) {
        JsonNode payload = binding.payload(); String type = payload.path("bindingType").asText();
        sameOrAbsent(task.getWorkBindingTypeCode(), type, "bindingType"); task.setWorkBindingTypeCode(type);
        sameOrAbsent(task.getTargetContextCode(), nullable(payload, "targetContextCode"), "Owner");
        sameOrAbsent(task.getTargetObjectType(), nullable(payload, "targetObjectType"), "objectType");
        sameOrAbsent(task.getTargetObjectKey(), nullable(payload, "targetObjectKey"), "objectKey");
        task.setTargetContextCode(nullable(payload, "targetContextCode")); task.setTargetObjectType(nullable(payload, "targetObjectType"));
        task.setTargetObjectKey(nullable(payload, "targetObjectKey"));
        if (bindingSnapshot.businessView() != null) {
            var view = bindingSnapshot.businessView(); sameOrAbsent(task.getComponentKey(), view.componentKey(), "componentKey");
            task.setComponentKey(view.componentKey());
            if ("DYNAMIC_FORM".equals(type)) {
                if (task.getDynamicFormRevisionId() != null && !Objects.equals(task.getDynamicFormRevisionId(), view.dynamicFormRevisionId()))
                    throw exception(REFERENCE_INVALID, "dynamicFormRevisionId override");
                task.setDynamicFormRevisionId(view.dynamicFormRevisionId());
            }
        }
        // Preserve specialized PRE-02/PRE-04 parameters and their existing Owner validation.
        // Other slots must not smuggle Owner/Provider/field-policy overrides through legacy JSON.
        boolean specialized = PreparationWorkBindingSchema.isPreparationBinding(task)
                || RequirementAnalysisWorkBindingSchema.isRequirementAnalysisBinding(task);
        if (!specialized && task.getBindingConfig() != null
                && !payload.equals(JsonUtils.parseObject(task.getBindingConfig(), JsonNode.class)))
            throw exception(REFERENCE_INVALID, "bindingConfig override");
        if (task.getBindingConfig() == null) task.setBindingConfig(JsonUtils.toJsonString(payload));
        if (task.getPermissionPolicyRef() == null) task.setPermissionPolicyRef(permission.definitionCode());
        else if (!task.getPermissionPolicyRef().equals(permission.definitionCode())) throw exception(REFERENCE_INVALID, "permission policy override");
        String ruleType = completion.payload().has("operator") ? completion.payload().path("operator").asText()
                : completion.payload().path("predicate").asText();
        JsonNode ruleConfig = completion.payload().has("operator") ? completion.payload() : completion.payload().path("parameters");
        sameOrAbsent(task.getCompletionRuleTypeCode(), ruleType, "completionRuleTypeCode");
        if (task.getCompletionRuleConfig() != null
                && !ruleConfig.equals(JsonUtils.parseObject(task.getCompletionRuleConfig(), JsonNode.class)))
            throw exception(REFERENCE_INVALID, "completionRuleConfig override");
        task.setCompletionRuleTypeCode(ruleType);
        task.setCompletionRuleConfig(JsonUtils.toJsonString(ruleConfig));
        task.setDefinitionVersion(Math.toIntExact(definition.revisionNo()));
    }
    private Revision binding(Map<Long, Snapshot> closure, Long id, String nativeType) {
        Revision binding = resolver.require(closure, id, DeliveryDefinitionKind.WORK_BINDING);
        String type = binding.payload().path("bindingType").asText();
        if (type.endsWith("_NATIVE") && !type.equals(nativeType)) throw exception(REFERENCE_INVALID, "native binding owner mismatch");
        return binding;
    }
    private void enforceOwner(Revision definition, Revision selectedBinding, Map<Long, Snapshot> closure) {
        Revision declared = resolver.require(closure, slot(definition, "workBinding", null), DeliveryDefinitionKind.WORK_BINDING);
        for (String field : List.of("bindingType", "targetContextCode", "targetObjectType", "businessViewRevisionId"))
            if (!Objects.equals(declared.payload().path(field), selectedBinding.payload().path(field)))
                throw exception(REFERENCE_INVALID, "template slot cannot override Owner: " + field);
    }
    private Long slot(Revision definition, String field, Long override) {
        if (override != null) return override;
        String key = definition.payload().path(field).asText();
        return definition.references().stream().filter(ref -> ref.referenceKey().equals(key))
                .map(DeliveryDefinitionReference::targetRevisionId).findFirst().orElseThrow(() -> exception(REFERENCE_INVALID, field));
    }
    private String nullable(JsonNode payload, String key) { return payload.hasNonNull(key) ? payload.path(key).asText() : null; }
    private void sameOrAbsent(String supplied, String required, String field) {
        if (supplied != null && !Objects.equals(supplied, required)) throw exception(REFERENCE_INVALID, field + " override");
    }
    private void optional(List<DeliveryDefinitionReference> roots, Long id) { if (id != null) root(roots, id); }
    private void root(List<DeliveryDefinitionReference> roots, Long id) {
        if (id == null || id <= 0) throw exception(REFERENCE_INVALID, "exact definitionRevisionId required");
        roots.add(new DeliveryDefinitionReference("slot" + roots.size(), id));
    }
}
