package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import tools.jackson.databind.JsonNode;

/**
 * Compatibility-only V2 Designer -> legacy DTO projection.
 * This adapter never writes legacy element rows and must not be used as runtime truth.
 */
public final class TemplateDesignerLegacyAdapter {
    private TemplateDesignerLegacyAdapter() { }

    public static TemplateDefinitionContent toLegacy(TemplateDesignerDocument designer) {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        if (designer.getMatch() != null) {
            content.setSigningMethod(designer.getMatch().getSigningMethod());
            content.setProjectCategory(designer.getMatch().getProjectCategory());
            content.setImplementationMethod(designer.getMatch().getImplementationMethod());
            content.setMajorProjectLevel(designer.getMatch().getMajorProjectLevel());
        }
        content.setProcessDefinitionKey(designer.getProcessDefinitionKey());
        content.setProcessDefinitionVersion(null);
        content.setClosurePolicy(designer.getClosurePolicy() == null ? null
                : new TemplateDefinitionContent.ClosurePolicy(designer.getClosurePolicy()));
        content.setDefinitionSnapshot(designer.getSourceEvidence());

        for (TemplateDesignerDocument.StageNode source : designer.getStages()) {
            if (source == null) continue;
            TemplateDefinitionContent.StageDef target = new TemplateDefinitionContent.StageDef();
            target.setStageCode(source.getCode()); target.setName(source.getName()); target.setSortOrder(source.getSortOrder());
            target.setEntryCriteria(source.getEntryCriteria()); target.setExitCriteria(source.getExitCriteria());
            target.setStart(source.getStart()); target.setTerminal(source.getTerminal());
            if (source.getSource() != null) {
                target.setDefinitionRevisionId(source.getSource().getDefinitionRevisionId());
                target.setWorkBindingRevisionId(source.getSource().getWorkBindingRevisionId());
                target.setPermissionPolicyRevisionId(source.getSource().getPermissionPolicyRevisionId());
                target.setCompletionRuleRevisionId(source.getSource().getCompletionRuleRevisionId());
            }
            content.getStages().add(target);
        }
        for (TemplateDesignerDocument.TaskNode source : designer.getTasks()) {
            if (source == null) continue;
            TemplateDefinitionContent.TaskDef target = new TemplateDefinitionContent.TaskDef();
            target.setTaskCode(source.getCode()); target.setName(source.getName()); target.setParentTaskCode(source.getParentTaskCode());
            target.setStageCode(source.getStageCode()); target.setPriority(source.getPriority()); target.setSortOrder(source.getSortOrder());
            target.setEstimatedHours(source.getEstimatedHours()); target.setSatisfactionTiming(source.getSatisfactionTiming());
            target.setDescription(source.getDescription()); target.setGateRef(source.getGateRef());
            if (source.getSource() != null) {
                target.setDefinitionRevisionId(source.getSource().getDefinitionRevisionId());
                target.setWorkBindingRevisionId(source.getSource().getWorkBindingRevisionId());
                target.setPermissionPolicyRevisionId(source.getSource().getPermissionPolicyRevisionId());
                target.setCompletionRuleRevisionId(source.getSource().getCompletionRuleRevisionId());
            }
            binding(target, source.getWorkBinding());
            if (source.getPermission() != null) target.setPermissionPolicyRef(source.getPermission().getPolicyRef());
            rule(target, source.getCompletionRule());
            target.setDefinitionVersion(1);
            content.getTasks().add(target);
        }
        for (TemplateDesignerDocument.MilestoneNode source : designer.getMilestones()) {
            if (source == null) continue;
            TemplateDefinitionContent.MilestoneDef target = new TemplateDefinitionContent.MilestoneDef();
            target.setMilestoneCode(source.getCode()); target.setName(source.getName()); target.setStageCode(source.getStageCode());
            target.setTiming(source.getTiming()); target.setCriteria(source.getCriteria());
            if (source.getSource() != null) target.setDefinitionRevisionId(source.getSource().getDefinitionRevisionId());
            content.getMilestones().add(target);
        }
        for (TemplateDesignerDocument.DeliverableNode source : designer.getDeliverables()) {
            if (source == null) continue;
            TemplateDefinitionContent.DeliverableDef target = new TemplateDefinitionContent.DeliverableDef();
            target.setDeliverableCode(source.getCode()); target.setName(source.getName()); target.setStageCode(source.getStageCode());
            target.setTaskCode(source.getTaskCode()); target.setRequired(source.getRequired());
            if (source.getSource() != null) target.setDefinitionRevisionId(source.getSource().getDefinitionRevisionId());
            content.getDeliverables().add(target);
        }
        for (TemplateDesignerDocument.GateNode source : designer.getGates()) {
            if (source == null) continue;
            TemplateDefinitionContent.GateDef target = new TemplateDefinitionContent.GateDef();
            target.setGateCode(source.getCode()); target.setName(source.getName()); target.setGateType(source.getGateType());
            target.setStageCode(source.getStageCode()); target.setDescription(source.getDescription());
            if (source.getSource() != null) target.setDefinitionRevisionId(source.getSource().getDefinitionRevisionId());
            if (source.getReferences() != null) for (TemplateDesignerDocument.GateReference sourceRef : source.getReferences()) {
                if (sourceRef == null) continue;
                TemplateDefinitionContent.GateRef ref = new TemplateDefinitionContent.GateRef();
                ref.setRefType(sourceRef.getRefType()); ref.setRefCode(sourceRef.getRefCode()); ref.setRefVersion(sourceRef.getRefVersion());
                target.getReferences().add(ref);
            }
            content.getGates().add(target);
        }
        for (TemplateDesignerDocument.TransitionNode source : designer.getTransitions()) {
            if (source == null) continue;
            TemplateDefinitionContent.TransitionDef target = new TemplateDefinitionContent.TransitionDef();
            target.setTransitionCode(source.getCode()); target.setFromStageCode(source.getFromStageCode()); target.setToStageCode(source.getToStageCode());
            target.setPriority(source.getPriority()); target.setDefaultBranch(source.getDefaultBranch());
            if (source.getSource() != null) {
                target.setId(source.getSource().getTransitionId());
                target.setRevisionNo(source.getSource().getTransitionRevisionNo());
                target.setConditionRuleRevisionId(source.getSource().getCompletionRuleRevisionId());
            }
            if (target.getRevisionNo() == null) target.setRevisionNo(1L);
            content.getTransitions().add(target);
        }
        return content;
    }

    private static void binding(TemplateDefinitionContent.TaskDef target, TemplateDesignerDocument.WorkBindingSpec binding) {
        if (binding == null) return;
        target.setWorkBindingTypeCode(binding.getType()); target.setTargetContextCode(binding.getTargetContextCode());
        target.setTargetObjectType(binding.getTargetObjectType()); target.setTargetObjectKey(binding.getTargetObjectKey());
        target.setComponentKey(binding.getComponentKey()); target.setDynamicFormRevisionId(binding.getDynamicFormRevisionId());
        target.setApprovalDefinitionKey(binding.getApprovalDefinitionKey());
        target.setBindingConfig(binding.getParameters() == null ? "{}" : JsonUtils.toJsonString(binding.getParameters()));
    }

    private static void rule(TemplateDefinitionContent.TaskDef target, TemplateDesignerDocument.RuleSpec rule) {
        if (rule == null || rule.getExpression() == null) return;
        JsonNode expression = rule.getExpression();
        if (expression.has("operator")) {
            target.setCompletionRuleTypeCode(expression.path("operator").asText());
            target.setCompletionRuleConfig(JsonUtils.toJsonString(expression));
        } else {
            target.setCompletionRuleTypeCode(expression.path("predicate").asText());
            target.setCompletionRuleConfig(JsonUtils.toJsonString(expression.path("parameters")));
        }
    }
}
