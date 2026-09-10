package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure.NormalClosureApplicationDO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NormalClosureBpmEvidenceTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 10, 10, 0);
    static Started started() { return new Started("instance-1", "definition-7", List.of(
            new AssignedNode("serviceManagerReview", 12L), new AssignedNode("materialReview", 23L))); }
    static NormalClosureApplicationDO application() {
        var app = new NormalClosureApplicationDO(); app.setProcessInstanceId("instance-1"); app.setProcessDefinitionId("definition-7");
        app.setBusinessKey("PROJECT_NORMAL_CLOSURE:8"); app.setServiceManagerUserId(12L); app.setReviewerUserId(23L);
        app.setProcessEvidence(JsonUtils.toJsonString(started())); return app;
    }
    static List<Review> approved() { return List.of(new Review("t1", "serviceManagerReview", 12L, "APPROVE", null, TIME),
            new Review("t2", "materialReview", 23L, "APPROVE", null, TIME.plusMinutes(1))); }
    static Result result(String status, List<Review> reviews) {
        return new Result("instance-1", "definition-7", "PROJECT_NORMAL_CLOSURE:8", status, reviews);
    }
    @Test void acceptsExactlyTwoActualFrozenCandidateHistories() {
        assertEquals(approved(), NormalClosureBpmEvidence.requireResult(application(), result("APPROVE", approved())));
    }
    @Test void rejectsGenericPassAndMissingReviewFacts() {
        assertThrows(RuntimeException.class, () -> NormalClosureBpmEvidence.requireResult(application(), result("PASSED", approved())));
        assertThrows(RuntimeException.class, () -> NormalClosureBpmEvidence.requireResult(application(), result("APPROVE", approved().subList(0, 1))));
        assertThrows(RuntimeException.class, () -> NormalClosureBpmEvidence.requireResult(application(), result("APPROVE", List.of())));
    }
    @Test void rejectsSpoofedDefinitionBusinessKeyAndCandidate() {
        assertThrows(RuntimeException.class, () -> NormalClosureBpmEvidence.requireResult(application(),
                new Result("instance-1", "new-definition", "PROJECT_NORMAL_CLOSURE:8", "APPROVE", approved())));
        assertThrows(RuntimeException.class, () -> NormalClosureBpmEvidence.requireResult(application(),
                new Result("instance-1", "definition-7", "PROJECT_NORMAL_CLOSURE:9", "APPROVE", approved())));
        var wrong = List.of(approved().getFirst(), new Review("t2", "materialReview", 1L, "APPROVE", null, TIME.plusMinutes(1)));
        assertThrows(RuntimeException.class, () -> NormalClosureBpmEvidence.requireResult(application(), result("APPROVE", wrong)));
    }
    @Test void preservesRealRejectionWithoutApprovingProject() {
        var rejected = List.of(new Review("t1", "serviceManagerReview", 12L, "REJECT", "返工", TIME));
        assertEquals(rejected, NormalClosureBpmEvidence.requireResult(application(), result("REJECT", rejected)));
    }
    @Test void rejectsStartWithDifferentDeployedIdentityOrCandidates() {
        var definition = new Definition("definition-7", NormalClosurePolicy.PROCESS_KEY,
                List.of(new Node("serviceManagerReview", "SM", "SERVICE_MANAGER"), new Node("materialReview", "材料", "MATERIAL_REVIEWER")));
        assertDoesNotThrow(() -> NormalClosureBpmEvidence.requireStarted(definition, started(), 12L, 23L));
        assertThrows(RuntimeException.class, () -> NormalClosureBpmEvidence.requireStarted(definition, started(), 12L, 1L));
        assertThrows(RuntimeException.class, () -> NormalClosureBpmEvidence.requireStarted(definition,
                new Started("instance-1", "definition-8", started().nodes()), 12L, 23L));
    }
}
