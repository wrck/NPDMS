package cn.iocoder.yudao.module.pms.project.api.approval;

import java.util.List;
import java.util.Map;

/** Exact-definition creation shared by the task and gate adapters, implemented by the original BPM module.
 * Callers hold the project/execution lock and own business identity, round checks and command idempotency.
 * BPM retains start permission, selected-approver validation, system variables, numbering and naming.
 */
public interface ProjectApprovalProcessCreationApi {
    String create(Command command);

    record Command(Long tenantId, Long actorId, String definitionKey, String definitionId, String businessKey,
                   Map<String, Object> variables, Map<String, List<Long>> selectedApprovers) { }
}
