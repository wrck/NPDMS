package cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy;

import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.ActionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardCandidate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Pure P6 closure action policy shared by closure detail and dashboard projection. */
public final class CutoverP6ActionPolicy {

    public Set<String> allowedActions(CutoverDashboardCandidate candidate, ActionFacts facts,
                                      PermissionFacts permissions) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        boolean ownerP6 = candidate != null && "NEW_PLATFORM".equals(candidate.taskOrigin())
                && "P6".equals(candidate.currentStage()) && "CLOSURE_IN_PROGRESS".equals(candidate.taskStatus())
                && candidate.actorId() != null && candidate.actorId().equals(candidate.ownerUserId())
                && facts.editAllowed();
        if (ownerP6 && facts.closureStatus() == null && permissions.saveClosure()) {
            actions.add("CREATE_CLOSURE");
        }
        if (ownerP6 && "DRAFT".equals(facts.closureStatus())) {
            if (permissions.saveClosure()) actions.add("SAVE_CLOSURE");
            if (permissions.requestClosureCollection()) actions.add("REQUEST_COLLECTION");
            if (permissions.saveClosure() && facts.failedCollectionPresent()) actions.add("LINK_MANUAL_RESULT");
            if (permissions.submitClosure() && facts.closureComplete()) actions.add("SUBMIT_CLOSURE");
        }
        return Collections.unmodifiableSet(actions);
    }
}
