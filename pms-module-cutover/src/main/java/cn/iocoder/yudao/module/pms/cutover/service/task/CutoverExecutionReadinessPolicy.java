package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;

/** EXE-06 项目来源在执行前复核冻结前序事实；独立任务不使用此门槛。 */
@Service
@RequiredArgsConstructor
public class CutoverExecutionReadinessPolicy {
    private final CutoverReadinessPort readiness;
    private final CutoverTaskSnapshotStore snapshots;

    public void requireCurrentForApproval(ApprovalRevalidation command) {
        var task = command.task();
        if (!"PROJECT_EVENT".equals(task.getIntakeSourceType())) return;
        var expected = snapshots.readinessReference(task);
        if (expected == null || !"READY".equals(expected.decision()) || !Objects.equals(task.getProjectId(), expected.projectId()))
            throw new IllegalArgumentException("项目割接缺少有效的前序就绪快照");
        var current = readiness.lockAndRevalidateForApproval(new cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalReadinessAccessApi.Query(
                task.getTenantId(), task.getId(), task.getVersion(), command.approvalInstanceId(), command.approvalVersion(), command.nodeNo(),
                task.getProjectId(), expected.snapshotId(), expected.snapshotVersion(), expected.deviceIds()));
        requireSame(expected, current);
    }

    public record ApprovalRevalidation(CutoverTaskDO task, Long approvalInstanceId, Long approvalVersion, Integer nodeNo) { }

    public void requireCurrent(CutoverTaskDO task) {
        if (!"PROJECT_EVENT".equals(task.getIntakeSourceType())) return;
        var expected = snapshots.readiness(task);
        if (expected == null || !"READY".equals(expected.decision()) || !Objects.equals(task.getProjectId(), expected.projectId()))
            throw new IllegalArgumentException("项目割接缺少有效的前序就绪快照");
        var current = readiness.lockAndRevalidate(expected);
        requireSame(expected, current);
    }

    private static void requireSame(CutoverReadinessPort.ReadinessFact expected, CutoverReadinessPort.ReadinessFact current) {
        if (current == null || !"READY".equals(current.decision())
                || !Objects.equals(expected.projectId(), current.projectId())
                || !Objects.equals(expected.snapshotId(), current.snapshotId())
                || expected.snapshotVersion() != current.snapshotVersion()
                || !Objects.equals(expected.deviceIds(), current.deviceIds()))
            throw new IllegalArgumentException("批准方案、设备或前序里程碑已变化，请按项目变更流程重新校验后办理");
    }
}
