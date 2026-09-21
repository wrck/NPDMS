package cn.iocoder.yudao.module.pms.project.api.deliverable;

import tools.jackson.databind.JsonNode;

/** ACC consumes the project's frozen plan, never the latest editable template definition. */
public interface ProjectDeliverableRuleApi {
    Context read(Long projectId, String deliverableCode);
    /** Codes explicitly configured for this document source in the effective frozen plan. */
    java.util.Set<String> documentTargets(Long projectId, String sourceCode);
    /** Caller transaction; project root, project and runtime graph precede ACC/file locks. */
    Context lock(Long projectId, String deliverableCode);
    Decision evaluate(Long projectId, String deliverableCode);

    record Context(Long projectId, Long planVersionId, String lifecycleStatus, Long managerId,
                   String stageCode, String taskCode, JsonNode configuration) { }
    record Decision(boolean satisfied, String reason, String evidence) { }
}
