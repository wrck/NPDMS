package cn.iocoder.yudao.module.pms.cutover.service.approval.domain;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.ProjectCutoverServiceManagerPort;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException.Code.OWNER_DATA_CORRUPTED;

/** Shared current-approver qualification used by P5 detail, myTodos and dashboard projection. */
public final class CutoverApprovalEligibilityPolicy {
    private final ProjectCutoverServiceManagerPort serviceManagerPort;
    private final CutoverApprovalRoleCandidatePort roleCandidatePort;
    private final CutoverApprovalProjectScopePort projectScopePort;
    private final Supplier<LocalDateTime> currentTime;

    public CutoverApprovalEligibilityPolicy(ProjectCutoverServiceManagerPort serviceManagerPort,
                                             CutoverApprovalRoleCandidatePort roleCandidatePort,
                                             CutoverApprovalProjectScopePort projectScopePort,
                                             Supplier<LocalDateTime> currentTime) {
        this.serviceManagerPort = serviceManagerPort;
        this.roleCandidatePort = roleCandidatePort;
        this.projectScopePort = projectScopePort;
        this.currentTime = currentTime;
    }

    public boolean eligible(CutoverApprovalInstanceDO root, CutoverApprovalNodeDO node, long actorId) {
        if (node == null || !Objects.equals(root.getTenantId(), node.getTenantId())) return false;
        boolean byRole = CutoverRoleApprovalSnapshot.isRole(node);
        if (!byRole && !Objects.equals(node.getCurrentApproverUserId(), actorId)) return false;
        return switch (node.getNodeCode()) {
            case "INITIATOR" -> projectScopePort.inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                .tenantId(root.getTenantId())
                .projectId(root.getProjectId())
                .subjectUserId(actorId)
                .requiredAction("ACTION_EDIT")
                .build()).allowed();
            case "SERVICE_MANAGER" -> {
                var fact = serviceManagerPort.inspectCurrent(root.getTenantId(), root.getProjectId(), currentTime.get());
                yield fact.outcome() == ProjectCutoverServiceManagerPort.Outcome.FOUND
                        && Objects.equals(fact.userId(), actorId);
            }
            case "SECOND_LINE", "RND" -> {
                var role = CutoverRoleApprovalSnapshot.from(node);
                var query = new CutoverApprovalRoleCandidatePort.ActorQuery(root.getTenantId(), actorId);
                if (role == null || role.currentActor(query, roleCandidatePort.inspectActor(query)) == null) yield false;
                yield role.byRole() || projectScopePort.inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                        .tenantId(root.getTenantId()).projectId(root.getProjectId()).subjectUserId(actorId)
                        .requiredAction("ACTION_VIEW").build()).allowed();
            }
            default -> throw new CutoverApprovalApplicationException(OWNER_DATA_CORRUPTED, "未知审批节点");
        };
    }
}
