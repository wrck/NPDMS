package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverClosureDeviceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverClosureDeviceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverClosureStorageTest {
    private final CutoverClosureDeviceMapper devices = mock(CutoverClosureDeviceMapper.class);
    private final CutoverTaskDeviceScopeMapper active = mock(CutoverTaskDeviceScopeMapper.class);
    private final CutoverFrozenDeviceMapper taskDevices = mock(CutoverFrozenDeviceMapper.class);
    private final CutoverClosureStorage store = new CutoverClosureStorage(devices, active, taskDevices);
    private final List<CutoverClosureDeviceDO> frozen = new ArrayList<>();
    private final CutoverTaskDO task = new CutoverTaskDO();
    private final CutoverClosureDO closure = new CutoverClosureDO();
    private final CutoverTaskDeviceScopeDO device = new CutoverTaskDeviceScopeDO();
    private final CutoverClosureStorage.Source source = new CutoverClosureStorage.Source(closure, task);
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 13, 8, 0);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        task.setId(10L); task.setTenantId(1L); task.setProjectId(20L); task.setVersion(3L);
        closure.setId(30L); closure.setTenantId(1L); closure.setTaskId(10L); closure.setProjectId(20L);
        device.setId(40L); device.setTenantId(1L); device.setCutoverTaskId(10L); device.setProjectId(20L);
        device.setDeviceId(50L); device.setSerialNumberSnapshot("SN-50"); device.setProjectAssignmentVersion(4L); device.setActiveMarker(1);
        when(active.selectActiveByTaskForUpdate(any())).thenReturn(List.of(device));
        when(taskDevices.byTask(any())).thenReturn(List.of(device));
        when(devices.selectListByParents(any())).thenAnswer(invocation -> List.copyOf(frozen));
        when(devices.insert(any(CutoverClosureDeviceDO.class))).thenAnswer(invocation -> {
            frozen.add(invocation.getArgument(0)); return 1;
        });
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void freezingSurvivesArchiveReleaseAndRetainsOriginalAssignmentFacts() {
        store.freeze(new CutoverClosureStorage.Freeze(source, 60L, now));
        var row = frozen.getFirst();
        assertEquals(30L, row.getClosureId()); assertEquals("SN-50", row.getSn());
        assertEquals(4L, row.getProjectAssignmentVersion()); assertEquals(60L, row.getCreateUserId());
        assertEquals(now, row.getCreateTime());
        device.setActiveMarker(null); task.setTaskStatus("ARCHIVED");
        assertDoesNotThrow(() -> store.requireSame(source));
        assertEquals(List.of(row), store.frozen(closure));
    }

    @Test void changedAssignmentOrSerialNumberCannotUseTheFrozenClosure() {
        store.freeze(new CutoverClosureStorage.Freeze(source, 60L, now));
        device.setProjectAssignmentVersion(5L);
        var failure = assertThrows(CutoverClosureApplicationException.class, () -> store.requireSame(source));
        assertEquals(CutoverClosureApplicationException.Reason.DEVICE_SCOPE_STALE, failure.reason());
        assertEquals(4L, frozen.getFirst().getProjectAssignmentVersion());
        device.setProjectAssignmentVersion(4L); device.setSerialNumberSnapshot("REPLACED");
        assertThrows(CutoverClosureApplicationException.class, () -> store.requireSame(source));
    }

    @Test void missingRowsDoNotFallBackToLegacyWatermarks() {
        closure.setDeviceScopeWatermark("same-legacy"); task.setDeviceScopeWatermark("same-legacy");
        assertThrows(CutoverClosureApplicationException.class, () -> store.requireSame(source));
        when(active.selectActiveByTaskForUpdate(any())).thenReturn(List.of());
        assertThrows(CutoverClosureApplicationException.class, () -> store.freeze(new CutoverClosureStorage.Freeze(source, 60L, now)));
        verify(devices, never()).insert(any(CutoverClosureDeviceDO.class));
    }

    @Test void foreignTenantOrProjectRowsAreRejectedBeforeAWrite() {
        device.setTenantId(2L);
        assertThrows(CutoverClosureApplicationException.class, () -> store.freeze(new CutoverClosureStorage.Freeze(source, 60L, now)));
        device.setTenantId(1L); device.setProjectId(21L);
        assertThrows(CutoverClosureApplicationException.class, () -> store.freeze(new CutoverClosureStorage.Freeze(source, 60L, now)));
        verify(devices, never()).insert(any(CutoverClosureDeviceDO.class));
        TenantContextHolder.setTenantId(2L);
        assertThrows(CutoverClosureApplicationException.class, () -> store.frozen(closure));
    }
}
