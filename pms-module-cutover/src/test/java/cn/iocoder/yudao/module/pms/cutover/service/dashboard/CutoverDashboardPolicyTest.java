package cn.iocoder.yudao.module.pms.cutover.service.dashboard;

import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.ActionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardCandidate;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy.CutoverP2P3ActionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy.CutoverP4ActionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy.CutoverP5ActionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy.CutoverP6ActionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardActionFactPort.BatchQuery;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardActionFactPort.CandidateNeed;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardOwnerFactException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CutoverDashboardPolicyTest {

    @Test
    void projectsApprovedP2AndP3Actions() {
        CutoverP2P3ActionPolicy policy = new CutoverP2P3ActionPolicy();
        assertThat(policy.allowedActions(candidate(1L, "P2", "GRADE_CONFIRMING", "A"),
                ActionFacts.p2p3("DRAFT", true, null, true, true),
                PermissionFacts.p2p3(true, true, false, false, false)))
                .containsExactly("SAVE_ASSESSMENT", "SUBMIT_ASSESSMENT");
        assertThat(policy.allowedActions(candidate(2L, "P3", "SURVEYING", "B"),
                ActionFacts.p2p3(null, false, "DRAFT", false, false),
                PermissionFacts.p2p3(false, false, true, true, true)))
                .containsExactly("SAVE_CHECKLIST", "REQUEST_COLLECTION", "SUBMIT_CHECKLIST");
    }

    @Test
    void projectsApprovedP4Actions() {
        CutoverP4ActionPolicy policy = new CutoverP4ActionPolicy();
        assertThat(policy.allowedActions(candidate(3L, "P4", "PLAN_DRAFTING", "A"),
                ActionFacts.p4("DRAFT", 1, null, true, true, true, false),
                PermissionFacts.p4(true, true, true)))
                .containsExactly("SAVE_DRAFT", "SUBMIT_PLAN");
        assertThat(policy.allowedActions(candidate(4L, "P4", "PLAN_DRAFTING", "A"),
                ActionFacts.p4("SUBMITTED", 1, "REJECTED", true, false, false, true),
                PermissionFacts.p4(false, true, false)))
                .containsExactly("REVISE_PLAN");
    }

    @Test
    void projectsApprovedP5Actions() {
        CutoverDashboardCandidate candidate = candidate(5L, "P5", "APPROVING", "A");
        assertThat(new CutoverP5ActionPolicy().allowedActions(candidate,
                ActionFacts.p5("PENDING", "PENDING", null, 9L, true), PermissionFacts.p5()))
                .containsExactly("APPROVE", "REJECT");
    }

    @Test
    void projectsApprovedP6Actions() {
        assertThat(new CutoverP6ActionPolicy().allowedActions(
                candidate(6L, "P6", "CLOSURE_IN_PROGRESS", "A"),
                ActionFacts.p6("DRAFT", true, true, true), PermissionFacts.p6(true, true, true)))
                .containsExactly("SAVE_CLOSURE", "REQUEST_COLLECTION", "LINK_MANUAL_RESULT", "SUBMIT_CLOSURE");
    }

    @Test
    void controlledPortReturnsWholeNormalBatchInTaskOrder() {
        CutoverDashboardActionFacts first = new CutoverDashboardActionFacts(10L,
                ActionFacts.p2p3("DRAFT", true, null, true, true));
        CutoverDashboardActionFacts second = new CutoverDashboardActionFacts(20L,
                ActionFacts.p6("DRAFT", true, true, false));
        ControlledCutoverDashboardActionFactPort port =
                new ControlledCutoverDashboardActionFactPort(List.of(first, second));

        List<CutoverDashboardActionFacts> result = port.inspectBatch(new BatchQuery(1L, 9L, List.of(
                new CandidateNeed(10L, 100L, 1, "P2", 1000L, 1),
                new CandidateNeed(20L, 200L, 2, "P6", 2000L, 3))));

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void preservesApprovedOwnerFailureIdentityAndCause() {
        RuntimeException cause = new RuntimeException("owner unavailable");
        CutoverDashboardOwnerFactException failure = new CutoverDashboardOwnerFactException(
                "OWNER_PROVIDER_UNAVAILABLE", "PROJ_OR_SYSTEM_PROVIDER_UNAVAILABLE", "SYSTEM", cause);

        assertThat(failure.category()).isEqualTo("OWNER_PROVIDER_UNAVAILABLE");
        assertThat(failure.reasonCode()).isEqualTo("PROJ_OR_SYSTEM_PROVIDER_UNAVAILABLE");
        assertThat(failure.ownerContext()).isEqualTo("SYSTEM");
        assertThat(failure).hasCause(cause);
    }

    private static CutoverDashboardCandidate candidate(Long taskId, String stage, String status, String grade) {
        return new CutoverDashboardCandidate(taskId, "NEW_PLATFORM", stage, status, 9L, 9L, grade);
    }
}
