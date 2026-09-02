package cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy;

import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.ActionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardCandidate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Pure P5 approval action policy shared by approval detail and dashboard projection. */
public final class CutoverP5ActionPolicy {

    public Set<String> allowedActions(CutoverDashboardCandidate candidate, ActionFacts facts,
                                      PermissionFacts permissions) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        if (candidate != null && "PENDING".equals(facts.approvalStatus())
                && facts.approvalHoldReason() == null && "PENDING".equals(facts.approvalNodeStatus())
                && facts.approvalEligible()
                && candidate.actorId() != null && candidate.actorId().equals(facts.currentApproverUserId())) {
            if (permissions.approve()) actions.add("APPROVE");
            if (permissions.reject()) actions.add("REJECT");
        }
        return Collections.unmodifiableSet(actions);
    }
}
