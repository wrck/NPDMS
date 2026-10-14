package cn.iocoder.yudao.module.pms.cutover.service.approval.domain;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort;

import java.util.List;
import java.util.Objects;

/** 节点只固定实际角色引用；成员资格始终由SYSTEM核验当前办理人。 */
public record CutoverRuntimeRoleApprovalSnapshot(long roleGroupId, String roleGroupCode,
                                                String candidateGroupKey, String approvalMode) {
    public boolean byRole() { return "ROLE".equals(approvalMode); }
    public boolean tenantRole() { return byRole(); }

    public static boolean currentRoleTodo(CutoverApprovalInstanceDO root, CutoverApprovalNodeDO node) {
        return root != null && isRole(node) && Objects.equals(root.getTenantId(), node.getTenantId())
                && Objects.equals(root.getId(), node.getApprovalInstanceId())
                && "PENDING".equals(root.getStatusCode()) && root.getHoldReasonCode() == null
                && root.getReplacementApprovalInstanceId() == null
                && "PENDING".equals(node.getStatusCode())
                && node.getNodeNo() != null && Objects.equals(node.getNodeNo(), root.getCurrentNodeNo());
    }

    public CutoverApprovalRoleCandidatePort.Candidate currentActor(
            CutoverApprovalRoleCandidatePort.ActorQuery query,
            List<CutoverApprovalRoleCandidatePort.ActorMembership> memberships) {
        return memberships.stream().filter(member -> member.role().tenantId() == query.tenantId()
                        && member.role().roleId() == roleGroupId && member.role().roleGroupCode().equals(roleGroupCode)
                        && member.candidate().adminUserId() == query.actorId())
                .map(CutoverApprovalRoleCandidatePort.ActorMembership::candidate).findFirst().orElse(null);
    }

    public static CutoverRuntimeRoleApprovalSnapshot from(CutoverApprovalNodeDO node) {
        if (node == null || !List.of("SECOND_LINE", "RND").contains(node.getNodeCode())) return null;
        if (node.getCandidateRoleId() == null || node.getCandidateRoleCode() == null
                || !Objects.equals(node.getCandidateGroupKey(), "ROLE:" + node.getCandidateRoleId()))
            throw new IllegalArgumentException("审批节点角色引用缺失或损坏");
        return new CutoverRuntimeRoleApprovalSnapshot(node.getCandidateRoleId(), node.getCandidateRoleCode(),
                node.getCandidateGroupKey(), node.getApprovalModeCode());
    }

    public static boolean isRole(CutoverApprovalNodeDO node) {
        if (node == null || !"ROLE".equals(node.getApprovalModeCode())) return false;
        var snapshot = from(node);
        return snapshot != null && snapshot.byRole();
    }

    public static String roleCode(CutoverApprovalNodeDO node) {
        return isRole(node) ? node.getCandidateRoleCode() : null;
    }
}
