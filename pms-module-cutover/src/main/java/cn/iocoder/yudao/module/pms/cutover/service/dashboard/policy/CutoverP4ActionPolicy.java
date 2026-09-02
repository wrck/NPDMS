package cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy;

import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.ActionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardCandidate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Pure P4 plan action policy shared by plan detail and dashboard projection. */
public final class CutoverP4ActionPolicy {

    public Set<String> allowedActions(CutoverDashboardCandidate candidate, ActionFacts facts,
                                      PermissionFacts permissions) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        boolean ownerP4 = ownedNewPlatformTask(candidate) && "P4".equals(candidate.currentStage())
                && "PLAN_DRAFTING".equals(candidate.taskStatus());
        if (ownerP4 && facts.editAllowed() && facts.planStatus() == null && permissions.createPlan()) {
            actions.add("CREATE_DRAFT");
        }
        if (ownerP4 && facts.editAllowed() && "DRAFT".equals(facts.planStatus())
                && permissions.savePlan() && facts.sourceComparable()) actions.add("SAVE_DRAFT");
        if (ownerP4 && facts.editAllowed() && "DRAFT".equals(facts.planStatus())
                && permissions.submitPlan() && facts.sourceComparable() && facts.contentComplete()) {
            actions.add("SUBMIT_PLAN");
        }
        if (ownerP4 && facts.editAllowed() && facts.planStatus() != null
                && permissions.savePlan() && facts.revisable()) actions.add("REVISE_PLAN");
        boolean ownerP6 = ownedNewPlatformTask(candidate) && "P6".equals(candidate.currentStage())
                && "CLOSURE_IN_PROGRESS".equals(candidate.taskStatus());
        if (ownerP6 && facts.editAllowed() && "SUBMITTED".equals(facts.planStatus())
                && Integer.valueOf(1).equals(facts.planCurrentMarker())
                && "APPROVED".equals(facts.approvalStatus()) && permissions.updateApprovedContacts()) {
            actions.add("UPDATE_APPROVED_CONTACTS");
        }
        return Collections.unmodifiableSet(actions);
    }

    private static boolean ownedNewPlatformTask(CutoverDashboardCandidate candidate) {
        return candidate != null && "NEW_PLATFORM".equals(candidate.taskOrigin())
                && candidate.actorId() != null && candidate.actorId().equals(candidate.ownerUserId());
    }
}
