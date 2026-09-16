package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectOperationRuleEvaluator.Evaluation;
import tools.jackson.databind.JsonNode;
import java.util.List;

/** A versioned observation only. Commands must independently reauthorize and revalidate. */
public record ProjectOperationCapabilities(Node node, ProjectBusinessExecutionSelection execution,
        List<Action> actions, Presentation presentation, String ownerFactVersion, String reason) {
    public ProjectOperationCapabilities { actions = List.copyOf(actions); }
    public record Node(Long projectId, String kind, Long id, String code, String name, String status) { }
    public record Presentation(JsonNode registration, String status, String reason) { }
    public record Action(String operationCode, int operationVersion, String label, boolean ownerPermitted,
            boolean executionPermitted, boolean runtimeAvailable, Evaluation pre, Evaluation post,
            boolean allowed, String reason) { }

    public static Action combine(String code, int version, String label, boolean owner, boolean execution,
            boolean runtime, Evaluation pre, Evaluation post, String ownerError) {
        Evaluation observedPre = pre == null ? Evaluation.unknown("OPERATION_PRECONDITION_NOT_EVALUATED") : pre;
        boolean allowed = ownerError == null && owner && execution && runtime && observedPre.permits();
        String reason = ownerError != null ? ownerError : !owner ? "OWNER_OPERATION_FORBIDDEN"
                : !execution ? "NODE_EXECUTION_FORBIDDEN" : !observedPre.permits()
                ? "UNKNOWN".equals(observedPre.outcome())
                    ? observedPre.reason() == null || observedPre.reason().isBlank() ? "OPERATION_RULE_UNKNOWN" : observedPre.reason()
                    : "OPERATION_PRECONDITION_NOT_MATCHED"
                : !runtime ? "OPERATION_RUNTIME_NOT_INSTALLED" : null;
        return new Action(code, version, label, owner, execution, runtime, observedPre, post,
                allowed, reason);
    }
}
