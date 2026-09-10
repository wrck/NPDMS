package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure.NormalClosureApplicationDO;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.normalclosure.NormalClosureErrors.failure;

/** Verify concrete engine identities; a generic process status or HTTP success is never approval evidence. */
public final class NormalClosureBpmEvidence {
    public static final String SM = "serviceManagerReview";
    public static final String MATERIAL = "materialReview";
    private NormalClosureBpmEvidence() {}

    public static void requireStarted(BpmNormalClosureApi.Definition definition, BpmNormalClosureApi.Started started,
                                      Long serviceManager, Long reviewer) {
        if (definition == null || started == null || blank(started.instanceId()) || blank(started.actualDefinitionId())
                || !NormalClosurePolicy.PROCESS_KEY.equals(definition.key())
                || !Objects.equals(definition.actualDefinitionId(), started.actualDefinitionId())
                || definition.nodes().size() != 2
                || !definition.nodes().stream().map(BpmNormalClosureApi.Node::taskDefinitionKey).toList().equals(List.of(SM, MATERIAL))
                || !started.nodes().equals(List.of(new BpmNormalClosureApi.AssignedNode(SM, serviceManager),
                new BpmNormalClosureApi.AssignedNode(MATERIAL, reviewer)))) throw failure("CLOSURE_BPM_DEFINITION_MISMATCH");
    }

    public static List<BpmNormalClosureApi.Review> requireResult(NormalClosureApplicationDO application,
                                                               BpmNormalClosureApi.Result result) {
        if (result == null || !Objects.equals(application.getProcessInstanceId(), result.instanceId())
                || !Objects.equals(application.getProcessDefinitionId(), result.actualDefinitionId())
                || !Objects.equals(application.getBusinessKey(), result.businessKey()))
            throw failure("CLOSURE_BPM_RESULT_IDENTITY_MISMATCH");
        var frozen = JsonUtils.parseObject(application.getProcessEvidence(), BpmNormalClosureApi.Started.class);
        if (frozen == null || !Objects.equals(frozen.instanceId(), result.instanceId())
                || !Objects.equals(frozen.actualDefinitionId(), result.actualDefinitionId())
                || !frozen.nodes().equals(List.of(new BpmNormalClosureApi.AssignedNode(SM, application.getServiceManagerUserId()),
                new BpmNormalClosureApi.AssignedNode(MATERIAL, application.getReviewerUserId()))))
            throw failure("CLOSURE_BPM_CANDIDATE_EVIDENCE_MISMATCH");
        Set<String> taskIds = new HashSet<>();
        Set<String> nodes = new HashSet<>();
        for (var review : result.reviews()) {
            Long expected = SM.equals(review.taskDefinitionKey()) ? application.getServiceManagerUserId()
                    : MATERIAL.equals(review.taskDefinitionKey()) ? application.getReviewerUserId() : null;
            if (blank(review.taskId()) || expected == null || !Objects.equals(expected, review.assigneeUserId())
                    || review.completedAt() == null || !taskIds.add(review.taskId()) || !nodes.add(review.taskDefinitionKey())
                    || !Set.of("APPROVE", "REJECT", "CANCEL").contains(review.decision()))
                throw failure("CLOSURE_BPM_REVIEW_INVALID");
        }
        if ("APPROVE".equals(result.status())) {
            if (result.reviews().size() != 2 || !nodes.equals(Set.of(SM, MATERIAL))
                    || result.reviews().stream().anyMatch(r -> !"APPROVE".equals(r.decision())))
                throw failure("CLOSURE_BPM_TWO_APPROVALS_REQUIRED");
            var sm = result.reviews().stream().filter(r -> SM.equals(r.taskDefinitionKey())).findFirst().orElseThrow();
            var material = result.reviews().stream().filter(r -> MATERIAL.equals(r.taskDefinitionKey())).findFirst().orElseThrow();
            if (material.completedAt().isBefore(sm.completedAt())) throw failure("CLOSURE_BPM_REVIEW_ORDER_INVALID");
        } else if (!Set.of("REJECT", "CANCEL", "RUNNING").contains(result.status())) {
            throw failure("CLOSURE_BPM_RESULT_UNAVAILABLE");
        }
        return result.reviews();
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
