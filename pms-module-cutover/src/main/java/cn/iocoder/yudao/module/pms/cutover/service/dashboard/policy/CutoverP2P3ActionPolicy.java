package cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy;

import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.ActionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardCandidate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Pure P2/P3 action policy shared by task detail and dashboard projection. */
public final class CutoverP2P3ActionPolicy {

    public Set<String> allowedActions(CutoverDashboardCandidate candidate, ActionFacts facts,
                                      PermissionFacts permissions) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        if (!ownedNewPlatformTask(candidate)) {
            return Collections.unmodifiableSet(actions);
        }
        if ("P3".equals(candidate.currentStage()) && "SURVEYING".equals(candidate.taskStatus())
                && Set.of("A", "B", "C").contains(candidate.manualGrade())) {
            if (facts.checklistStatus() == null) {
                if (permissions.saveChecklist()) actions.add("GENERATE_CHECKLIST");
                return Collections.unmodifiableSet(actions);
            }
            if (!"DRAFT".equals(facts.checklistStatus())) {
                return Collections.unmodifiableSet(actions);
            }
            if (permissions.saveChecklist()) actions.add("SAVE_CHECKLIST");
            if (permissions.requestChecklistCollection()) actions.add("REQUEST_COLLECTION");
            if (permissions.submitChecklist()) actions.add("SUBMIT_CHECKLIST");
            return Collections.unmodifiableSet(actions);
        }
        if (!"P2".equals(candidate.currentStage()) || !"GRADE_CONFIRMING".equals(candidate.taskStatus())) {
            return Collections.unmodifiableSet(actions);
        }
        boolean submitted = "SUBMITTED".equals(facts.assessmentStatus());
        if (permissions.saveAssessment() && facts.editAllowed() && !submitted) {
            actions.add("SAVE_ASSESSMENT");
        }
        if (permissions.submitAssessment() && "DRAFT".equals(facts.assessmentStatus())
                && facts.assessmentGradePresent() && facts.p2SubmitAllowed()) {
            actions.add("SUBMIT_ASSESSMENT");
        }
        return Collections.unmodifiableSet(actions);
    }

    private static boolean ownedNewPlatformTask(CutoverDashboardCandidate candidate) {
        return candidate != null && "NEW_PLATFORM".equals(candidate.taskOrigin())
                && candidate.actorId() != null && candidate.actorId().equals(candidate.ownerUserId());
    }
}
