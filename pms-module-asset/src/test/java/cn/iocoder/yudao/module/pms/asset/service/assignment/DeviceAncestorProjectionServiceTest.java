package cn.iocoder.yudao.module.pms.asset.service.assignment;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceAncestorProjectionOperationDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectAncestorDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAncestorProjectionWatermarkQuery;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.RebuildDeviceAncestorProjectionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAncestorProjectionServiceTest {

    @Mock private DeviceAssignmentMapper assignmentMapper;
    private DeviceAncestorProjectionService service;

    @BeforeEach
    void setUp() {
        service = new DeviceAncestorProjectionService(assignmentMapper);
    }

    @Test
    void shouldReplaceProjectionWithTreeAndAssignmentWatermark() {
        when(assignmentMapper.existsAncestorProjectionOperation(1L, "event-8")).thenReturn(false);

        boolean applied = service.rebuild(command("event-8", "op-8"));

        assertTrue(applied);
        verify(assignmentMapper).selectProjectionDeviceForUpdate(1L, "SN-8");
        verify(assignmentMapper).deleteDeviceAncestors(1L, "SN-8");
        verify(assignmentMapper, times(3)).insertProjectAncestor(any(DeviceProjectAncestorDO.class));
        ArgumentCaptor<DeviceAncestorProjectionOperationDO> operationCaptor =
                ArgumentCaptor.forClass(DeviceAncestorProjectionOperationDO.class);
        verify(assignmentMapper).insertAncestorProjectionOperation(operationCaptor.capture());
        String operation = operationCaptor.getValue().toString();
        assertTrue(operation.contains("eventId=event-8"));
        assertTrue(operation.contains("operationId=op-8"));
    }

    @Test
    void shouldIgnoreRepeatedEvent() {
        when(assignmentMapper.existsAncestorProjectionOperation(1L, "event-8")).thenReturn(true);

        boolean applied = service.rebuild(command("event-8", "op-8"));

        assertFalse(applied);
        verify(assignmentMapper, never()).deleteDeviceAncestors(any(), any());
        verify(assignmentMapper, never()).insertProjectAncestor(any(DeviceProjectAncestorDO.class));
    }

    @Test
    void shouldPersistWatermarkWhenRootProjectHasNoAncestors() {
        when(assignmentMapper.existsAncestorProjectionOperation(1L, "event-root")).thenReturn(false);

        boolean applied = service.rebuild(new RebuildDeviceAncestorProjectionCommand(
                1L, "SN-ROOT", 100L, List.of(),
                7L, 4L, "event-root", "op-root"));

        assertTrue(applied);
        verify(assignmentMapper).deleteDeviceAncestors(1L, "SN-ROOT");
        verify(assignmentMapper, never()).insertProjectAncestor(any(DeviceProjectAncestorDO.class));
        verify(assignmentMapper).insertAncestorProjectionOperation(any());
    }

    @Test
    void shouldIgnoreOlderAssignmentWatermark() {
        when(assignmentMapper.existsAncestorProjectionOperation(1L, "event-old")).thenReturn(false);
        DeviceAncestorProjectionOperationDO latest = new DeviceAncestorProjectionOperationDO();
        latest.setAssignmentVersion(4L);
        latest.setTreeVersion(7L);
        when(assignmentMapper.selectLatestAncestorProjectionOperation(
                new DeviceAncestorProjectionWatermarkQuery(1L, "SN-8")))
                .thenReturn(latest);

        boolean applied = service.rebuild(new RebuildDeviceAncestorProjectionCommand(
                1L, "SN-8", 150L, List.of(100L),
                8L, 3L, "event-old", "op-old"));

        assertFalse(applied);
        verify(assignmentMapper, never()).deleteDeviceAncestors(any(), any());
        verify(assignmentMapper, never()).insertAncestorProjectionOperation(any());
    }

    @Test
    void shouldIgnoreOlderTreeWatermarkForSameAssignment() {
        when(assignmentMapper.existsAncestorProjectionOperation(1L, "event-old-tree")).thenReturn(false);
        DeviceAncestorProjectionOperationDO latest = new DeviceAncestorProjectionOperationDO();
        latest.setAssignmentVersion(4L);
        latest.setTreeVersion(8L);
        when(assignmentMapper.selectLatestAncestorProjectionOperation(
                new DeviceAncestorProjectionWatermarkQuery(1L, "SN-8")))
                .thenReturn(latest);

        boolean applied = service.rebuild(new RebuildDeviceAncestorProjectionCommand(
                1L, "SN-8", 150L, List.of(100L),
                7L, 4L, "event-old-tree", "op-old-tree"));

        assertFalse(applied);
        verify(assignmentMapper, never()).deleteDeviceAncestors(any(), any());
        verify(assignmentMapper, never()).insertAncestorProjectionOperation(any());
    }

    private RebuildDeviceAncestorProjectionCommand command(String eventId, String operationId) {
        return new RebuildDeviceAncestorProjectionCommand(
                1L, "SN-8", 200L, List.of(100L, 150L, 200L),
                7L, 3L, eventId, operationId);
    }
}
