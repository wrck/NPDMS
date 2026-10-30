package cn.iocoder.yudao.module.pms.cutover.service.approval.domain;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort;
import java.util.Objects;

/** 提交/领取时固定分配；只对未领取的共享节点解析当前角色。 */
public final class CutoverApprovalEligibilityPolicy {
    private final CutoverApprovalRoleCandidatePort actors;
    public CutoverApprovalEligibilityPolicy(CutoverApprovalRoleCandidatePort actors) { this.actors = actors; }
    public static boolean handledBy(CutoverApprovalNodeDO node, long actorId) {
        return node != null && node.getDecisionAt() != null
                && java.util.Set.of("APPROVED", "REJECTED").contains(node.getStatusCode())
                && Objects.equals(node.getDecisionActorUserId() == null ? node.getCurrentApproverUserId() : node.getDecisionActorUserId(), actorId);
    }
    public boolean eligible(CutoverApprovalInstanceDO root, CutoverApprovalNodeDO node, long actorId) {
        if (node == null || !Objects.equals(root.getTenantId(), node.getTenantId())) return false;
        var query = new CutoverApprovalRoleCandidatePort.ActorQuery(root.getTenantId(), actorId);
        if (node.getCurrentApproverUserId() != null) {
            if (!Objects.equals(node.getCurrentApproverUserId(), actorId)) return false;
            actors.requireActiveUser(query);
            return true;
        }
        var role = CutoverRoleApprovalSnapshot.from(node);
        return role != null && role.byRole() && role.currentActor(query, actors.inspectActor(query)) != null;
    }
}
