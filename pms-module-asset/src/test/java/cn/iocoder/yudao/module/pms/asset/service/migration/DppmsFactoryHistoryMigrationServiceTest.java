package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsFactoryHistoryMigrationApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceFactoryVersionRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceFactoryVersionRecordMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsFactoryHistoryMigrationServiceTest {
    DeviceFactoryVersionRecordMapper records=mock(DeviceFactoryVersionRecordMapper.class);
    PermissionApi permissions=mock(PermissionApi.class);AdminUserApi users=mock(AdminUserApi.class);
    DppmsFactoryHistoryMigrationService service=new DppmsFactoryHistoryMigrationService(records,permissions,users);
    Values values=new Values(null,"v1",null,"",null);
    Entry entry(int ordinal,Long target){return new Entry("a".repeat(64)+":"+ordinal,values,target);}
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);when(records.insert(any(DeviceFactoryVersionRecordDO.class))).thenReturn(1);}
    @AfterEach void close(){TenantContextHolder.clear();}
    @Test void wholeSourceDuplicatesAndNullsArePreservedWithoutProjectOrDeviceAssignment() {
        var command=new Command(1L,2L,List.of(entry(1,null),entry(2,null)));
        var preview=service.preview(command);assertEquals(2,preview.size());verify(records,never()).insert(any(DeviceFactoryVersionRecordDO.class));
        var result=service.apply(command);assertEquals(2,result.size());assertNotEquals(result.getFirst().targetId(),result.getLast().targetId());
        var captor=org.mockito.ArgumentCaptor.forClass(DeviceFactoryVersionRecordDO.class);verify(records,times(2)).insert(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(r->r.getProjectId()==null && r.getDeviceSn()==null && "".equals(r.getBootVersion())));
    }
    @Test void anyChangedDeletedOrProjectScopedTargetBlocksAllNewWrites() {
        var existing=new DeviceFactoryVersionRecordDO();existing.setId(10L);existing.setTenantId(1L);
        existing.setConpVersion("v1");existing.setBootVersion("");existing.setDeleted(false);
        when(records.selectGlobalRecordForUpdate(any())).thenReturn(existing);
        assertEquals("UNCHANGED",service.apply(new Command(1L,2L,List.of(entry(1,10L)))).getFirst().action());
        existing.setDeleted(true);
        assertThrows(IllegalArgumentException.class,()->service.apply(new Command(1L,2L,List.of(entry(1,null),entry(2,10L)))));
        existing.setDeleted(false);existing.setProjectId(22L);
        assertThrows(IllegalArgumentException.class,()->service.apply(new Command(1L,2L,List.of(entry(1,10L)))));
        existing.setProjectId(null);existing.setConpVersion("changed");
        assertThrows(IllegalArgumentException.class,()->service.apply(new Command(1L,2L,List.of(entry(1,10L)))));
        verify(records,never()).insert(any(DeviceFactoryVersionRecordDO.class));verify(records,never()).updateById(any(DeviceFactoryVersionRecordDO.class));
    }
    @Test void tenantPermissionAndRepeatedSourceIdentityAreRejected() {
        assertThrows(IllegalArgumentException.class,()->service.apply(new Command(2L,2L,List.of(entry(1,null)))));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.apply(new Command(1L,3L,List.of(entry(1,null)))));
        assertThrows(IllegalArgumentException.class,()->service.apply(new Command(1L,2L,List.of(entry(1,null),entry(1,null)))));
        verify(records,never()).insert(any(DeviceFactoryVersionRecordDO.class));
    }
}
