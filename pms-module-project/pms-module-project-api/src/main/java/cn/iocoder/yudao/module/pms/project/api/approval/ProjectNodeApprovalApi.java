package cn.iocoder.yudao.module.pms.project.api.approval;

import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** BPM owns approval state; project commands own node state. No Flowable types cross this boundary. */
public interface ProjectNodeApprovalApi {
    String BUSINESS_KEY_PREFIX = "PROJECT_TASK_APPROVAL:";
    String VAR_TENANT = "pmsTaskTenantId";
    String VAR_PROJECT = "pmsTaskProjectId";
    String VAR_TASK = "pmsTaskId";
    String VAR_EXECUTION = "pmsTaskExecutionId";
    String VAR_CONTRACT = "pmsTaskContractId";
    String VAR_DEFINITION = "pmsTaskProcessDefinitionId";
    String VAR_ACTOR = "pmsTaskActorId";
    String VAR_ATTEMPT = "pmsTaskApprovalAttempt";
    String VAR_OPERATION = "pmsTaskApprovalOperation";

    /** Called after task authorization/admission, in the same transaction holding the project/current-round lock.
     * The project command owns payload idempotency. The same operation returns its original process;
     * a new operation may start another attempt only after rejection/cancellation, within the same round.
     * Form variables and selected approvers are validated by the original BPM creation service.
     */
    Fact start(Start command);

    /** Stage producer supplies the actual round start under the project lock; no synthetic child task. */
    Fact startStage(StageStart command);

    /** Current-round Owner evidence only. Missing, ambiguous or invalid evidence is UNKNOWN, never approved. */
    Fact inspect(Scope scope);

    enum NodeKind {
        TASK(BUSINESS_KEY_PREFIX, "pmsTask"), STAGE("PROJECT_STAGE_APPROVAL:", "pmsStage");
        private final String businessPrefix;
        private final String variablePrefix;
        NodeKind(String businessPrefix, String variablePrefix) { this.businessPrefix = businessPrefix; this.variablePrefix = variablePrefix; }
        public String businessKey(Long executionId) { return businessPrefix + executionId; }
        public boolean owns(String businessKey) { return businessKey != null && businessKey.startsWith(businessPrefix); }
        public String variable(String suffix) { return variablePrefix + suffix; }
        public boolean reserved(String key) { return key.startsWith(variablePrefix); }
    }
    record Scope(NodeKind kind, Long tenantId, Long projectId, Long nodeId, Long executionId, Long contractId,
                 String definitionKey, String definitionId, LocalDateTime startedAt) {
        public String businessKey() { return kind.businessKey(executionId); }
        public Map<String, Object> identity() {
            return Map.of(kind.variable("TenantId"), tenantId, kind.variable("ProjectId"), projectId,
                    kind.variable("Id"), nodeId, kind.variable("ExecutionId"), executionId,
                    kind.variable("ContractId"), contractId, kind.variable("ProcessDefinitionId"), definitionId);
        }
    }
    record Start(Scope scope, ProjectTaskExecutionContext execution, Long actorId,
                 String operationId, Map<String, Object> variables, Map<String, List<Long>> selectedApprovers) { }
    record StageStart(Scope scope, ProjectStageExecutionContext execution, Long actorId,
                      String operationId, Map<String, Object> variables, Map<String, List<Long>> selectedApprovers) { }
    record Submission(Map<String, Object> variables, Map<String, List<Long>> selectedApprovers) { }
    /** Routing metadata only. Original BPM endpoints retain authority over forms and visible approval details. */
    record View(String definitionKey, String definitionId, Long executionId, Fact current) { }
    enum Outcome { SATISFIED, NOT_SATISFIED, UNKNOWN }
    record Fact(Outcome outcome, String status, String processInstanceId, String definitionId, String reason) {
        public static Fact unknown(String reason) { return new Fact(Outcome.UNKNOWN, "UNKNOWN", null, null, reason); }
    }
}
