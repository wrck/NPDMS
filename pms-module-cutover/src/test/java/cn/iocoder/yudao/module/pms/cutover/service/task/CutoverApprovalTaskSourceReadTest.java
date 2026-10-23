package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverFrozenDeviceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverAssessmentDeviceMapper;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class CutoverApprovalTaskSourceReadTest {
    @Test void approvalReadUsesCapturedCutReferenceWithoutGrantingImpProjectAccess() {
        var devices = mock(CutoverFrozenDeviceMapper.class);
        var readiness = mock(CutoverReadinessPort.class);
        var store = new CutoverTaskSnapshotStore(devices, mock(CutoverAssessmentDeviceMapper.class), mock(CutoverAssessmentMapper.class), readiness);
        var task = new CutoverTaskDO(); task.setTenantId(1L); task.setId(10L); task.setProjectId(30L);
        task.setImplementationReadinessSnapshotId(50L); task.setImplementationReadinessSnapshotVersion(0L); task.setReadinessDecisionCode("READY");
        var device = new CutoverTaskDeviceScopeDO(); device.setDeviceId(40L);
        when(devices.byTask(any())).thenReturn(List.of(device));
        var reference = store.readinessReference(task);
        assertEquals(50L, reference.snapshotId()); assertEquals(0L, reference.snapshotVersion());
        assertEquals("READY", reference.decision()); assertEquals(List.of(40L), reference.deviceIds());
        assertNull(reference.sourceWatermark()); assertNull(reference.unmetCodes());
        verifyNoInteractions(readiness);
        when(readiness.readSnapshot(any())).thenThrow(new SecurityException("ordinary project access denied"));
        assertThrows(SecurityException.class, () -> store.readiness(task));
    }
}
