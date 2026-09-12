package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stable semantic hash for execution schema v2.
 *
 * <p>The hash intentionally excludes legacy source/provenance ids and publication-row metadata.
 * It must remain stable for execution schema v2 so an already-published snapshot can be verified
 * without recompiling its DesignerDocument with a newer compiler.</p>
 */
final class TemplateExecutionSnapshotHasher {

    private TemplateExecutionSnapshotHasher() {
    }

    static String hash(TemplateExecutionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "execution snapshot");
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(semanticSnapshot(snapshot)));
    }

    private static Map<String, Object> semanticSnapshot(TemplateExecutionSnapshot snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("executionSchemaVersion", snapshot.getExecutionSchemaVersion());
        root.put("compilerVersion", snapshot.getCompilerVersion());
        root.put("match", snapshot.getMatch());
        root.put("processDefinitionKey", snapshot.getProcessDefinitionKey());
        root.put("closurePolicy", snapshot.getClosurePolicy());
        root.put("stages", snapshot.getStages().stream()
                .sorted(Comparator.comparing(TemplateExecutionSnapshot.StageContract::getNodeKey))
                .map(TemplateExecutionSnapshotHasher::semanticStage).toList());
        root.put("tasks", snapshot.getTasks().stream()
                .sorted(Comparator.comparing(TemplateExecutionSnapshot.TaskContract::getNodeKey))
                .map(TemplateExecutionSnapshotHasher::semanticTask).toList());
        root.put("milestones", snapshot.getMilestones().stream()
                .sorted(Comparator.comparing(TemplateExecutionSnapshot.MilestoneContract::getNodeKey))
                .map(TemplateExecutionSnapshotHasher::semanticMilestone).toList());
        root.put("deliverables", snapshot.getDeliverables().stream()
                .sorted(Comparator.comparing(TemplateExecutionSnapshot.DeliverableContract::getNodeKey))
                .map(TemplateExecutionSnapshotHasher::semanticDeliverable).toList());
        root.put("gates", snapshot.getGates().stream()
                .sorted(Comparator.comparing(TemplateExecutionSnapshot.GateContract::getNodeKey))
                .map(TemplateExecutionSnapshotHasher::semanticGate).toList());
        root.put("transitions", snapshot.getTransitions().stream()
                .sorted(Comparator.comparing(TemplateExecutionSnapshot.TransitionContract::getEdgeKey))
                .map(TemplateExecutionSnapshotHasher::semanticTransition).toList());
        return root;
    }

    private static Map<String, Object> semanticStage(TemplateExecutionSnapshot.StageContract row) {
        Map<String, Object> map = baseNode(row.getNodeKey(), row.getCode(), row.getName());
        map.put("sortOrder", row.getSortOrder());
        map.put("entryCriteria", row.getEntryCriteria());
        map.put("exitCriteria", row.getExitCriteria());
        map.put("start", row.getStart());
        map.put("terminal", row.getTerminal());
        map.put("binding", semanticBinding(row.getBinding()));
        map.put("permission", semanticPermission(row.getPermission()));
        map.put("completionRule", row.getCompletionRule());
        return map;
    }

    private static Map<String, Object> semanticTask(TemplateExecutionSnapshot.TaskContract row) {
        Map<String, Object> map = baseNode(row.getNodeKey(), row.getCode(), row.getName());
        map.put("parentTaskCode", row.getParentTaskCode());
        map.put("stageCode", row.getStageCode());
        map.put("priority", row.getPriority());
        map.put("sortOrder", row.getSortOrder());
        map.put("estimatedHours", row.getEstimatedHours());
        map.put("satisfactionTiming", row.getSatisfactionTiming());
        map.put("description", row.getDescription());
        map.put("binding", semanticBinding(row.getBinding()));
        map.put("permission", semanticPermission(row.getPermission()));
        map.put("completionRule", row.getCompletionRule());
        map.put("gateRef", row.getGateRef());
        return map;
    }

    private static Map<String, Object> semanticBinding(TemplateExecutionSnapshot.BindingContract row) {
        if (row == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", row.getType());
        map.put("targetContextCode", row.getTargetContextCode());
        map.put("targetObjectType", row.getTargetObjectType());
        map.put("targetObjectKey", row.getTargetObjectKey());
        map.put("componentKey", row.getComponentKey());
        map.put("dynamicFormRevisionId", row.getDynamicFormRevisionId());
        map.put("approvalDefinitionKey", row.getApprovalDefinitionKey());
        map.put("parameters", row.getParameters());
        map.put("businessViewSnapshot", row.getBusinessViewSnapshot());
        return map;
    }

    private static Map<String, Object> semanticPermission(TemplateExecutionSnapshot.PermissionContract row) {
        if (row == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("policyRef", row.getPolicyRef());
        map.put("policySnapshot", row.getPolicySnapshot());
        return map;
    }

    private static Map<String, Object> semanticTransition(TemplateExecutionSnapshot.TransitionContract row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("edgeKey", row.getEdgeKey());
        map.put("code", row.getCode());
        map.put("fromStageCode", row.getFromStageCode());
        map.put("toStageCode", row.getToStageCode());
        map.put("conditionRule", row.getConditionRule());
        map.put("priority", row.getPriority());
        map.put("defaultBranch", row.getDefaultBranch());
        return map;
    }

    private static Map<String, Object> semanticMilestone(TemplateExecutionSnapshot.MilestoneContract row) {
        Map<String, Object> map = baseNode(row.getNodeKey(), row.getCode(), row.getName());
        map.put("stageCode", row.getStageCode());
        map.put("timing", row.getTiming());
        map.put("criteria", row.getCriteria());
        map.put("configuration", row.getConfiguration());
        return map;
    }

    private static Map<String, Object> semanticDeliverable(TemplateExecutionSnapshot.DeliverableContract row) {
        Map<String, Object> map = baseNode(row.getNodeKey(), row.getCode(), row.getName());
        map.put("stageCode", row.getStageCode());
        map.put("taskCode", row.getTaskCode());
        map.put("required", row.getRequired());
        map.put("configuration", row.getConfiguration());
        return map;
    }

    private static Map<String, Object> semanticGate(TemplateExecutionSnapshot.GateContract row) {
        Map<String, Object> map = baseNode(row.getNodeKey(), row.getCode(), row.getName());
        map.put("gateType", row.getGateType());
        map.put("stageCode", row.getStageCode());
        map.put("description", row.getDescription());
        map.put("references", row.getReferences());
        return map;
    }

    private static Map<String, Object> baseNode(String key, String code, String name) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("nodeKey", key);
        map.put("code", code);
        map.put("name", name);
        return map;
    }
}
