package cn.iocoder.yudao.module.bpm.api.normalclosure;

import java.time.LocalDateTime;
import java.util.List;

/** Public, additive BPM boundary for the approved two-manual-review NORMAL template. */
public interface BpmNormalClosureApi {

    String PROCESS_DEFINITION_KEY = "PMS_MINIMAL_NORMAL_CLOSURE";

    Definition inspectDefinition(Long tenantId, String key);

    /** Joins the caller's business transaction; the caller owns project scope and candidate authorization. */
    Started start(StartCommand command);

    /** Reads real engine history. APPROVE is only returned for an ended, fully reviewed instance. */
    Result inspectResult(Long tenantId, String processInstanceId);

    record StartCommand(Long tenantId, Long actorId, String businessKey, String processDefinitionKey,
                        Long serviceManagerUserId, Long materialReviewerUserId, Long projectId) { }

    record Node(String taskDefinitionKey, String name, String candidateRole) { }

    record Definition(String actualDefinitionId, String key, List<Node> nodes) {
        public Definition { nodes = List.copyOf(nodes); }
    }

    record AssignedNode(String taskDefinitionKey, Long candidateUserId) { }

    record Started(String instanceId, String actualDefinitionId, List<AssignedNode> nodes) {
        public Started { nodes = List.copyOf(nodes); }
    }

    /** status uses the existing BPM enum names: RUNNING, APPROVE, REJECT, CANCEL, NOT_START. */
    record Result(String instanceId, String actualDefinitionId, String businessKey, String status,
                  List<Review> reviews) {
        public Result { reviews = List.copyOf(reviews); }
    }

    record Review(String taskId, String taskDefinitionKey, Long assigneeUserId, String decision,
                  String reason, LocalDateTime completedAt) { }
}
