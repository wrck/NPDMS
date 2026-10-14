package cn.iocoder.yudao.module.pms.cutover.service.taskv2;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverReadinessPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;

/** EXE-06 项目来源在执行前复核冻结前序事实；独立任务不使用此门槛。 */
@Service
@RequiredArgsConstructor
public class CutoverExecutionReadinessPolicy {
    private final CutoverReadinessPort readiness;
    private final CutoverTaskSnapshotStore snapshots;

    public void requireCurrent(CutoverTaskDO task) {
        if (!"PROJECT_EVENT".equals(task.getIntakeSourceType())) return;
        var expected = snapshots.readiness(task);
        if (expected == null || !"READY".equals(expected.decision()) || !Objects.equals(task.getProjectId(), expected.projectId()))
            throw new IllegalArgumentException("项目割接缺少有效的前序就绪快照");
        var current = readiness.lockAndRevalidate(expected);
        if (current == null || !"READY".equals(current.decision())
                || !Objects.equals(expected.snapshotId(), current.snapshotId())
                || expected.snapshotVersion() != current.snapshotVersion()
                || !Objects.equals(expected.deviceIds(), current.deviceIds()))
            throw new IllegalArgumentException("批准方案、设备或前序里程碑已变化，请按项目变更流程重新校验后办理");
    }
}
