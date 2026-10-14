package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverExecutionReadinessPolicyTest {
    @Test void independentSourcesNeverUseImplementationAsAnExecutionGate() {
        var port = mock(CutoverReadinessPort.class);
        var snapshots = mock(CutoverTaskSnapshotStore.class);
        var policy = new CutoverExecutionReadinessPolicy(port, snapshots);
        for (String source : List.of("SELF_CREATED", "ITR")) { var task = new CutoverTaskDO(); task.setIntakeSourceType(source); policy.requireCurrent(task); }
        verifyNoInteractions(port, snapshots);
    }
    @Test void projectExecutionRejectsAReopenedOrChangedPredecessor() {
        var port = mock(CutoverReadinessPort.class);
        var snapshots = mock(CutoverTaskSnapshotStore.class);
        var task = task(); var expected = ready();
        when(snapshots.readiness(task)).thenReturn(expected);
        when(port.lockAndRevalidate(expected)).thenReturn(new CutoverReadinessPort.ReadinessFact(100L, 0L, "STALE", 20L, List.of(30L), null, List.of("EXE_03_NOT_COMPLETED")));
        assertThatThrownBy(() -> new CutoverExecutionReadinessPolicy(port, snapshots).requireCurrent(task)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已变化");
    }
    @Test void projectExecutionAcceptsTheSameReadySnapshot() {
        var port = mock(CutoverReadinessPort.class);
        var snapshots = mock(CutoverTaskSnapshotStore.class);
        var task = task(); var expected = ready();
        when(snapshots.readiness(task)).thenReturn(expected);
        when(port.lockAndRevalidate(expected)).thenReturn(expected);
        assertThatCode(() -> new CutoverExecutionReadinessPolicy(port, snapshots).requireCurrent(task)).doesNotThrowAnyException();
    }
    @Test void projectExecutionRejectsMissingFrozenSnapshot() {
        var port = mock(CutoverReadinessPort.class);
        var snapshots = mock(CutoverTaskSnapshotStore.class);
        assertThatThrownBy(() -> new CutoverExecutionReadinessPolicy(port, snapshots).requireCurrent(task()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("缺少");
        verifyNoInteractions(port);
    }
    private static CutoverReadinessPort.ReadinessFact ready() {
        return new CutoverReadinessPort.ReadinessFact(100L, 0L, "READY", 20L, List.of(30L), null, List.of());
    }
    private static CutoverTaskDO task() {
        var task = new CutoverTaskDO(); task.setIntakeSourceType("PROJECT_EVENT"); task.setProjectId(20L);
        task.setImplementationReadinessSnapshotId(100L); task.setImplementationReadinessSnapshotVersion(0L);
        task.setReadinessDecisionCode("READY");
        return task;
    }
}
