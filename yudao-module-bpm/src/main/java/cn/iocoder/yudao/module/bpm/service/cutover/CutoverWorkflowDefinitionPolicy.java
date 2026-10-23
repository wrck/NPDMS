package cn.iocoder.yudao.module.bpm.service.cutover;

import org.flowable.bpmn.model.*;
import java.util.*;

/** 业务路由和人工评审策略必须同时匹配；保留节点名称和图形布局的编辑空间。 */
final class CutoverWorkflowDefinitionPolicy {
    private CutoverWorkflowDefinitionPolicy() { }
    private record Edge(String from, String to, String condition) { }
    private static final Map<String, Edge> EDGES = Map.of(
            "f0", new Edge("start", "INITIATOR", null), "f1", new Edge("INITIATOR", "SERVICE_MANAGER", null),
            "f2", new Edge("SERVICE_MANAGER", "SECOND_LINE_REQUIRED", null),
            "f3", new Edge("SECOND_LINE_REQUIRED", "SECOND_LINE", "${cutoverGrade == 'A' || cutoverGrade == 'B'}"),
            "f4", new Edge("SECOND_LINE_REQUIRED", "end", "${cutoverGrade == 'C' || cutoverGrade == 'D'}"),
            "f5", new Edge("SECOND_LINE", "RND_REQUIRED", null),
            "f6", new Edge("RND_REQUIRED", "RND", "${cutoverGrade == 'A'}"),
            "f7", new Edge("RND_REQUIRED", "end", "${cutoverGrade == 'B'}"), "f8", new Edge("RND", "end", null));
    static void validate(BpmnModel model) {
        if (model == null || model.getProcesses().size() != 1) throw invalid();
        var process = model.getMainProcess();
        if (process.getFlowElements().size() != 17 || process.getExecutionListeners().size() != 1) throw invalid();
        if (!(process.getFlowElement("SECOND_LINE") instanceof UserTask secondLine)
                || !(process.getFlowElement("RND") instanceof UserTask research)
                || !secondLine.getExtensionElements().containsKey("cutoverRoleGroup")
                || !research.getExtensionElements().containsKey("cutoverRoleGroup")) throw invalid();
        var listener = process.getExecutionListeners().getFirst();
        if (!"start".equals(listener.getEvent()) || !"${cutoverBpmGuard.freeze(execution)}".equals(listener.getImplementation())) throw invalid();
        if (!(process.getFlowElement("start") instanceof StartEvent) || !(process.getFlowElement("end") instanceof EndEvent)
                || !(process.getFlowElement("SECOND_LINE_REQUIRED") instanceof ExclusiveGateway)
                || !(process.getFlowElement("RND_REQUIRED") instanceof ExclusiveGateway)) throw invalid();
        for (var entry : EDGES.entrySet()) {
            if (!(process.getFlowElement(entry.getKey()) instanceof SequenceFlow flow)
                    || !entry.getValue().equals(new Edge(flow.getSourceRef(), flow.getTargetRef(), blank(flow.getConditionExpression())))) throw invalid();
        }
        for (String code : List.of("INITIATOR", "SERVICE_MANAGER", "SECOND_LINE", "RND")) {
            if (!(process.getFlowElement(code) instanceof UserTask node) || node.getLoopCharacteristics() != null
                    || blank(node.getSkipExpression()) != null || !Objects.equals(node.getAssignee(), "${cutoverUser_" + code + "}")) throw invalid();
            var role = node.getExtensionElements().get("cutoverRoleGroup");
            if (role != null) {
                String expectedRole = "SECOND_LINE".equals(code) ? "probSupporter" : "RND".equals(code) ? "probRD" : null;
                if (expectedRole == null || role.size() != 1 || !Objects.equals(expectedRole, role.getFirst().getElementText())
                        || !node.getCandidateUsers().isEmpty()
                        || !node.getCandidateGroups().equals(List.of("${cutoverGroup_" + code + "}"))) throw invalid();
            } else if (!node.getCandidateUsers().isEmpty() || !node.getCandidateGroups().isEmpty()) throw invalid();
            var policies = Map.of("candidateStrategy", "60", "candidateParam", "${cutoverUser_" + code + "}",
                    "approveType", "1", "assignStartUserHandlerType", "1", "assignEmptyHandlerType", "3",
                    "rejectHandlerType", "1", "signEnable", "false", "reasonRequire", "true");
            for (var policy : policies.entrySet()) {
                var values = node.getExtensionElements().get(policy.getKey());
                if (values == null || values.size() != 1 || !policy.getValue().equals(values.getFirst().getElementText())) throw invalid();
            }
            if (node.getTaskListeners().size() != 4 || !node.getExecutionListeners().isEmpty()) throw invalid();
            for (String event : List.of("create", "complete", "delete", "assignment")) {
                if (node.getTaskListeners().stream().filter(item -> event.equals(item.getEvent())
                        && "${cutoverBpmGuard.validate(task)}".equals(item.getImplementation())).count() != 1) throw invalid();
            }
        }
    }
    static boolean supportsRoles(BpmnModel model) {
        return model != null && model.getProcesses().size() == 1
                && List.of("SECOND_LINE", "RND").stream().allMatch(code ->
                model.getMainProcess().getFlowElement(code) instanceof UserTask node
                        && node.getCandidateUsers().isEmpty()
                        && node.getCandidateGroups().equals(List.of("${cutoverGroup_" + code + "}")));
    }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("割接审批路由或人工评审策略与正式定义不一致"); }
}
