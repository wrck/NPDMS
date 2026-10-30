package cn.iocoder.yudao.module.pms.cutover.api.approval;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRoleApprovalSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CutoverApprovalReadinessAccessApiImpl implements CutoverApprovalReadinessAccessApi {
    private final CutoverTaskMapper tasks;
    private final CutoverApprovalInstanceMapper instances;
    private final CutoverApprovalNodeMapper nodes;
    private final CutoverFrozenDeviceMapper devices;
    private final CutoverApprovalRoleCandidatePort roles;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void requireCurrent(Query query) {
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        require(query != null && !TenantContextHolder.isIgnore() && query.tenantId() != null
                && Objects.equals(query.tenantId(), TenantContextHolder.getTenantId()) && actor != null && actor > 0);
        var task = tasks.selectForUpdate(new CutoverTaskRowQuery(query.tenantId(), query.taskId()));
        require(task != null && Objects.equals(task.getTenantId(), query.tenantId())
                && "PROJECT_EVENT".equals(task.getIntakeSourceType()) && "P5".equals(task.getCurrentStage())
                && "APPROVING".equals(task.getTaskStatus()) && Objects.equals(task.getVersion(), query.taskVersion())
                && Objects.equals(task.getProjectId(), query.projectId())
                && Objects.equals(task.getImplementationReadinessSnapshotId(), query.snapshotId())
                && Objects.equals(task.getImplementationReadinessSnapshotVersion(), query.snapshotVersion())
                && "READY".equals(task.getReadinessDecisionCode()));
        var current = instances.selectCurrentByTask(new ApprovalTaskQuery(query.tenantId(), query.taskId()));
        require(current != null && Objects.equals(current.getId(), query.approvalInstanceId()));
        var root = instances.selectByIdForUpdate(new ApprovalInstanceLockQuery(query.tenantId(), query.approvalInstanceId(), null, null));
        require(root != null && Objects.equals(root.getTenantId(), query.tenantId())
                && Objects.equals(root.getTaskId(), query.taskId()) && Objects.equals(root.getProjectId(), query.projectId())
                && Objects.equals(root.getVersion(), query.approvalVersion()) && Objects.equals(root.getCurrentNodeNo(), query.nodeNo()));
        var node = nodes.selectByInstanceAndNodeForUpdate(new ApprovalNodeLockQuery(query.tenantId(), root.getId(), query.nodeNo()));
        require(CutoverRoleApprovalSnapshot.currentRoleTodo(root, node));
        var actorQuery = new CutoverApprovalRoleCandidatePort.ActorQuery(query.tenantId(), actor);
        roles.requireActiveUser(actorQuery);
        require(CutoverRoleApprovalSnapshot.from(node).currentActor(actorQuery, roles.lockAndInspectActor(actorQuery)) != null
                && (node.getCurrentApproverUserId() == null || Objects.equals(node.getCurrentApproverUserId(), actor)));
        var ids = devices.byTask(new CutoverTaskDeviceListQuery(query.tenantId(), query.taskId())).stream()
                .map(value -> value.getDeviceId()).sorted().toList();
        require(!ids.isEmpty() && ids.equals(query.deviceIds().stream().distinct().sorted().toList())
                && ids.size() == query.deviceIds().size());
    }

    private static void require(boolean allowed) {
        if (!allowed) throw new SecurityException("当前审批节点不具备绑定快照的内部重验资格");
    }
}
