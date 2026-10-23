package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverAssessmentDeviceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverAssessmentDeviceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverFrozenDeviceMapper;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverTaskSnapshotStoreTest {
    private static CutoverAssessmentDO assessment(String status) {
        var row = new CutoverAssessmentDO();
        row.setId(10L); row.setTenantId(1L); row.setCutoverTaskId(20L); row.setAssessmentStatus(status);
        return row;
    }
    @Test
    void refreshWritesCurrentDeviceVersionOnlyToDraftAssessment() {
        var devices = mock(CutoverAssessmentDeviceMapper.class);
        var assessments = mock(CutoverAssessmentMapper.class);
        var store = new CutoverTaskSnapshotStore(mock(CutoverFrozenDeviceMapper.class), devices, assessments, mock(CutoverReadinessPort.class));
        var assessment = assessment("DRAFT");
        var creation = new CutoverTaskDeviceScopeDO();
        creation.setId(30L); creation.setTenantId(1L); creation.setCutoverTaskId(20L); creation.setDeviceId(40L);
        creation.setProjectId(50L); creation.setSerialNumberSnapshot("SN-40"); creation.setProjectAssignmentVersion(1L);
        store.replaceAssessmentDevices(CutoverTaskSnapshotStore.DeviceWrite.builder().assessment(assessment)
                .devices(List.of(creation)).currentDevices(List.of(new CutoverDeviceScopePort.DeviceFact(40L, "SN-40", 50L, 2L)))
                .productTypes(Map.of()).actorId(8L).time(LocalDateTime.of(2026, 10, 20, 0, 0)).build());
        var saved = ArgumentCaptor.forClass(CutoverAssessmentDeviceDO.class);
        verify(devices).insert(saved.capture());
        assertEquals(2L, saved.getValue().getProjectAssignmentVersion());
        assertEquals(10L, saved.getValue().getAssessmentId());
        assertEquals(30L, saved.getValue().getTaskDeviceScopeId());
        assertEquals(1L, creation.getProjectAssignmentVersion());
    }

    @Test
    void submittedAssessmentRefusesReplacementBeforeAnyDeleteOrInsert() {
        var devices = mock(CutoverAssessmentDeviceMapper.class);
        var assessments = mock(CutoverAssessmentMapper.class);
        var store = new CutoverTaskSnapshotStore(mock(CutoverFrozenDeviceMapper.class), devices, assessments, mock(CutoverReadinessPort.class));
        var assessment = assessment("SUBMITTED");
        assertThrows(IllegalArgumentException.class, () -> store.replaceAssessmentDevices(
                CutoverTaskSnapshotStore.DeviceWrite.builder().assessment(assessment).devices(List.of()).productTypes(Map.of()).build()));
        verifyNoInteractions(devices, assessments);
    }
}
