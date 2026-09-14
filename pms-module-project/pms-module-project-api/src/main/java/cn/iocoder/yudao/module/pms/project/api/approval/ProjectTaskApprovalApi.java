package cn.iocoder.yudao.module.pms.project.api.approval;

import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** BPM owns approval state; project commands own task state. No Flowable types cross this boundary. */
public interface ProjectTaskApprovalApi {
    String BUSINESS_KEY_PREFIX = "PROJECT_TASK_APPROVAL:";
    String VAR_TENANT = "pmsTaskTenantId";
    String VAR_PROJECT = "pmsTaskProjectId";
    String VAR_TASK = "pmsTaskId";
    String VAR_EXECUTION = "pmsTaskExecutionId";
    String VAR_CONTRACT = "pmsTaskContractId";
    String VAR_DEFINITION = "pmsTaskProcessDefinitionId";
    String VAR_ACTOR = "pmsTaskActorId";

    /** Called after task authorization/admission, in the same transaction holding the project/current-round lock.
     * One process per execution round; retries return that process, including its terminal result.
     * Form variables and selected approvers are validated by the original BPM creation service.
     */
    Fact start(Start command);

    /** Current-round Owner evidence only. Missing, ambiguous or invalid evidence is UNKNOWN, never approved. */
    Fact inspect(Scope scope);

    record Scope(Long tenantId, Long projectId, Long taskId, Long executionId, Long contractId,
                 String definitionKey, String definitionId, LocalDateTime startedAt) {
        public String businessKey() { return BUSINESS_KEY_PREFIX + executionId; }
    }
    record Start(Scope scope, ProjectTaskExecutionContext execution, Long actorId,
                 Map<String, Object> variables, Map<String, List<Long>> selectedApprovers) { }
    enum Outcome { SATISFIED, NOT_SATISFIED, UNKNOWN }
    record Fact(Outcome outcome, String status, String processInstanceId, String definitionId, String reason) {
        public static Fact unknown(String reason) { return new Fact(Outcome.UNKNOWN, "UNKNOWN", null, null, reason); }
    }
}
