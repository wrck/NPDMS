package cn.iocoder.yudao.module.pms.asset.service.version;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.*;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class DeviceSoftwareVersionMaintenanceServiceTest {
    final DeviceMapper devices=mock(DeviceMapper.class);
    final DeviceNetworkVersionMapper currents=mock(DeviceNetworkVersionMapper.class);
    final DeviceSoftwareVersionRecordMapper records=mock(DeviceSoftwareVersionRecordMapper.class);
    final SoftwareVersionWriteMapper writes=mock(SoftwareVersionWriteMapper.class);
    final ProjectScopeApi projects=mock(ProjectScopeApi.class);
    final DeviceAccessScopeService access=mock(DeviceAccessScopeService.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final EntityExtensionApi extensions=mock(EntityExtensionApi.class);
    final DeviceSoftwareVersionMaintenanceService service=new DeviceSoftwareVersionMaintenanceService(devices,currents,records,writes,projects,access,permissions,extensions);
    final List<DeviceSoftwareVersionRecordDO> inserted=new ArrayList<>();
    final SoftwareVersionValues next=new SoftwareVersionValues("NEW",null,null,null,"B","C","P",false,null,null);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(permissions.hasAnyPermissions(7L,"pms:device:update")).thenReturn(true);
        var scope=new ProjectScopeResult(10L,3L,Set.of(10L),Set.of());
        when(projects.resolveCurrent(any())).thenReturn(scope);when(projects.lockAndRevalidate(any())).thenReturn(scope);
        var device=new DeviceDO();device.setId(8L);device.setSn("SN");device.setTenantId(1L);device.setProjectId(10L);device.setVersion(4L);
        when(writes.selectDeviceForUpdate(any())).thenReturn(device);
        when(writes.replaceCurrent(any())).thenReturn(1);when(writes.updateDeviceProjection(any())).thenReturn(1);
        when(currents.insert(any(DeviceNetworkVersionDO.class))).thenReturn(1);
        var ids=new AtomicLong(100L);
        when(records.insert(any(DeviceSoftwareVersionRecordDO.class))).thenAnswer(call->{ var row=call.getArgument(0,DeviceSoftwareVersionRecordDO.class);row.setId(ids.incrementAndGet());inserted.add(row);return 1; });
    }
    @AfterEach void clear() {TenantContextHolder.clear();}
    DeviceNetworkVersionDO current() {
        var row=new DeviceNetworkVersionDO();row.setId(3L);row.setVersion(2);row.setTenantId(1L);row.setDeviceSn("SN");
        row.setConpVersion("OLD");row.setBootVersion("");row.setCpldVersion(null);row.setPcbVersion("P");row.setSourceSystem("DEVICE_LOG");
        row.setEffectiveFrom(LocalDateTime.of(2024,1,2,3,4,5,123000000));return row;
    }
    void register(int expected,String key) {service.register(1L,7L,8L,10L,expected,key,next);}
    @Test void existingCurrentBecomesNamedBaselineAndNextRecordReferencesIt() {
        var current=current();when(writes.selectCurrentForUpdate(1L,"SN")).thenReturn(current);
        register(2,"req");assertEquals(2,inserted.size());
        assertEquals("CURRENT_BASELINE",inserted.getFirst().getDataSource());assertEquals(SoftwareVersionValues.of(current),SoftwareVersionValues.of(inserted.getFirst()));
        assertEquals(current.getEffectiveFrom(),inserted.getFirst().getRecordedAt());
        assertEquals(inserted.getFirst().getId(),inserted.getLast().getPreviousRecordId());
        verify(writes).replaceCurrent(argThat(row->row.getCreator()==null && row.getCreateTime()==null && row.getVersion()==2
                && row.getReleaseDate()==null && "7".equals(row.getUpdater())));
    }
    @Test void existingNullEffectiveDateIsPreservedOnTheBaseline() {
        var current=current();current.setEffectiveFrom(null);when(writes.selectCurrentForUpdate(1L,"SN")).thenReturn(current);
        register(2,"req");assertNull(inserted.getFirst().getRecordedAt());assertNotNull(inserted.getFirst().getCreateTime());
    }
    @Test void noCurrentCreatesOneRecordWithoutInventingAPreviousEmptyVersion() {
        register(0,"req");assertEquals(1,inserted.size());assertNull(inserted.getFirst().getPreviousRecordId());verify(currents).insert(any(DeviceNetworkVersionDO.class));
    }
    @Test void subsequentRegistrationUsesTheActualMatchingPredecessorWithoutAnotherBaseline() {
        var current=current();var before=new DeviceSoftwareVersionRecordDO();before.setId(22L);before.setTenantId(1L);before.setDeviceId(8L);SoftwareVersionValues.of(current).copyTo(before);
        when(writes.selectCurrentForUpdate(1L,"SN")).thenReturn(current);when(records.selectCurrentPredecessor(any())).thenReturn(before);
        register(2,"req");assertEquals(1,inserted.size());assertEquals(22L,inserted.getFirst().getPreviousRecordId());
    }
    @Test void staleVersionIsRejectedBeforeAnyHistoryOrProjectionWrite() {
        when(writes.selectCurrentForUpdate(1L,"SN")).thenReturn(current());assertThrows(RuntimeException.class,()->register(1,"req"));
        assertTrue(inserted.isEmpty());verify(writes,never()).replaceCurrent(any());verify(writes,never()).updateDeviceProjection(any());
    }
    @Test void foreignTenantAndProjectPermissionFailureCannotReachADeviceWrite() {
        TenantContextHolder.setTenantId(2L);assertThrows(RuntimeException.class,()->register(0,"req"));verifyNoInteractions(writes,records);
        TenantContextHolder.setTenantId(1L);when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,3L,Set.of(),Set.of(10L)));
        assertThrows(RuntimeException.class,()->register(0,"req"));verifyNoInteractions(writes,records);
    }
    @Test void changedProjectTreeOrDeviceOwnershipRejectsBeforeWritingHistory() {
        when(projects.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L,4L,Set.of(10L),Set.of()));
        assertThrows(RuntimeException.class,()->register(0,"req"));assertTrue(inserted.isEmpty());verifyNoInteractions(writes);
    }
    @Test void sameRequestReturnsOriginalReceiptWithoutWritingButDifferentContentIsRejected() {
        var replay=new DeviceSoftwareVersionRecordDO();replay.setId(99L);replay.setDeviceId(8L);replay.setProjectId(10L);replay.setCreator("7");replay.setDataSource("PLATFORM_MANUAL");next.copyTo(replay);
        when(records.selectByTenantAndRequest(1L,"req")).thenReturn(replay);
        assertEquals(new DeviceSoftwareVersionMaintenanceService.Registration(99L,true),service.register(1L,7L,8L,10L,0,"req",next));
        replay.setBootVersion("DIFFERENT");assertThrows(RuntimeException.class,()->register(0,"req"));
        assertTrue(inserted.isEmpty());verify(writes,never()).replaceCurrent(any());
    }
    @Test void projectionFailureIsExposedToTheTransactionalCaller() {
        when(writes.updateDeviceProjection(any())).thenReturn(0);assertThrows(RuntimeException.class,()->register(0,"req"));
    }
    @Test void oldLogCannotRegressNewerManualValueAndMissingParsedFieldsPreserveCurrentValues() {
        var current=current();current.setSourceSystem("PLATFORM_MANUAL");
        assertFalse(SoftwareVersionLogPolicy.mayAdopt(current,current.getEffectiveFrom().minusSeconds(1)));
        assertTrue(SoftwareVersionLogPolicy.mayAdopt(current,current.getEffectiveFrom().plusSeconds(1)));
        var merged=SoftwareVersionLogPolicy.merge(current,"LOG-NEW",null,null,null);
        assertEquals("",merged.bootVersion());assertNull(merged.cpldVersion());assertEquals("P",merged.pcbVersion());assertEquals("LOG-NEW",merged.conpVersion());
    }
}
