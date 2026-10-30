package cn.iocoder.yudao.module.bpm.api.solutionreview;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** SOL owns the frozen solution; BPM owns definitions, candidates, decisions and history. */
public interface SolutionReviewBpmApi {
    String DEFINITION_KEY = "PMS_DELIVERY_SOLUTION_REVIEW";
    String ENGINEERING_REVIEW_PERMISSION = "pms:sol-solution:major-review";
    String ENGINEERING_REVIEW_ROLE = "engineeManager";
    Definition definition(Long tenantId);
    Started start(Start command);
    Result result(Long tenantId, String instanceId);
    /** Current role todo or the actor's own completed decision grants only this process's frozen source read. */
    ReadAccess readAccess(ReadQuery query);
    record ReadQuery(Long tenantId, Long actorId, String businessKey) { }
    record ReadAccess(String processInstanceId, String taskId, String nodeKey, String businessKey, boolean historical) {
        public ReadAccess(String processInstanceId, String taskId, String nodeKey, String businessKey) {
            this(processInstanceId, taskId, nodeKey, businessKey, false);
        }
    }

    record Node(String key, String name, String responsibility) { }
    record Definition(String id, String key, List<Node> nodes) { }
    /** Candidates are resolved by BPM from node responsibility (project service manager, engineering review grant), not supplied by the caller. */
    record Start(Long tenantId, Long actorId, Long projectId, String businessKey,
                 String definitionId, int reviewLevel) { }
    record Started(String instanceId, String definitionId, Map<String, Long> candidates) { }
    record Review(String nodeKey, Long userId, String decision, String reason, LocalDateTime time) { }
    record Result(String instanceId, String definitionId, String businessKey, String status, List<Review> reviews) { }
}
