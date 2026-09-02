package cn.iocoder.yudao.module.pms.cutover.service.approval.domain;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.ProjectCutoverServiceManagerPort;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
        if (!Objects.equals(node.getCurrentApproverUserId(), actorId)) return false;
        return switch (node.getNodeCode()) {
            case "INITIATOR" -> projectScopePort.inspect(root.getTenantId(), root.getProjectId(), actorId,
                    "ACTION_EDIT").allowed();
            case "SERVICE_MANAGER" -> {
                var fact = serviceManagerPort.inspectCurrent(root.getTenantId(), root.getProjectId(), currentTime.get());
                yield fact.outcome() == ProjectCutoverServiceManagerPort.Outcome.FOUND
                        && Objects.equals(fact.userId(), actorId);
            }
            case "SECOND_LINE", "RND" -> {
                String group = "SECOND_LINE".equals(node.getNodeCode())
                        ? "CUT_SECOND_LINE_APPROVER" : "CUT_RND_APPROVER";
                List<Long> allowed = new ArrayList<>();
                for (var candidate : roleCandidatePort.inspectCandidates(root.getTenantId(), group).candidates()) {
                    var scope = projectScopePort.inspect(root.getTenantId(), root.getProjectId(),
                            candidate.adminUserId(), "ACTION_VIEW");
                    if (scope.allowed()) allowed.add(candidate.adminUserId());
                }
                yield allowed.size() == 1 && allowed.getFirst() == actorId;
            }
            default -> throw new CutoverApprovalApplicationException(OWNER_DATA_CORRUPTED, "未知审批节点");
        };
    }
}
