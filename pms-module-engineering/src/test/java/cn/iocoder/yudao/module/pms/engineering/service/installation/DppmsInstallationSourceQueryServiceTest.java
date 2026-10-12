package cn.iocoder.yudao.module.pms.engineering.service.installation;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationSourceQueryApi.Query;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import org.junit.jupiter.api.*;
import java.util.*;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class DppmsInstallationSourceQueryServiceTest {
    InstallationMapper mapper=mock(InstallationMapper.class);ProjectScopeApi scope=mock(ProjectScopeApi.class);DeviceQueryApi devices=mock(DeviceQueryApi.class);
    DppmsInstallationSourceQueryService service=new DppmsInstallationSourceQueryService(mapper,scope,devices);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    InstallationDO row(){var r=new InstallationDO();r.setId(700L);r.setTenantId(1L);r.setProjectId(101L);r.setEquipmentId(500L);r.setCode("DPPMS-301");r.setStatus(2);r.setInstallLocation("原地址");r.setAddressId(900L);r.setAddressVersion(0L);r.setEffectiveFrom(LocalDateTime.of(2020,1,2,3,4,5));r.setCreateTime(r.getEffectiveFrom());r.setUpdater(null);r.setVersion(1L);return r;}
    @Test void formalBusinessRecordIsTheOnlyHistoryAndAuditSource() {
        var r=row();when(scope.resolveAllCurrent(any())).thenReturn(Set.of(101L));when(mapper.selectFormalHistoryPage(any())).thenReturn(new PageResult<>(List.of(r),1L));
        var found=service.pageSourceRecords(new Query(1L,1L,101L,500L,1,20)).getList().getFirst();
        assertEquals(700L,found.id());assertEquals(700L,found.historicalInstallation().id());assertEquals(2,found.historicalInstallation().status());
        assertEquals(500L,found.historicalInstallation().currentEquipmentId());assertEquals(r.getEffectiveFrom(),found.historicalInstallation().effectiveFrom());
        assertNull(found.values().sourceUpdatedBy());assertEquals(r.getCreateTime(),found.values().sourceCreatedAt());verify(mapper,never()).updateById(any(InstallationDO.class));
    }
    @Test void earlierClosedHistoryDoesNotClaimCurrentInstallation() {
        var r=row();r.setEffectiveTo(r.getEffectiveFrom().plusYears(1));when(scope.resolveAllCurrent(any())).thenReturn(Set.of(101L));
        when(mapper.selectFormalHistoryPage(any())).thenReturn(new PageResult<>(List.of(r),1L));
        assertNull(service.pageSourceRecords(new Query(1L,1L,101L,500L,1,20)).getList().getFirst().historicalInstallation().currentEquipmentId());
    }
    @Test void emptyOrExcludedScopeAndWrongTenantNeverBroadenRead() {
        when(scope.resolveAllCurrent(any())).thenReturn(Set.of());assertTrue(service.pageSourceRecords(new Query(1L,1L,101L,500L,1,20)).getList().isEmpty());
        when(scope.resolveAllCurrent(any())).thenReturn(Set.of(102L));assertTrue(service.pageSourceRecords(new Query(1L,1L,101L,500L,1,20)).getList().isEmpty());
        assertThrows(IllegalArgumentException.class,()->service.pageSourceRecords(new Query(2L,1L,101L,500L,1,20)));verifyNoInteractions(mapper,devices);
    }
    @Test void ownerResultOutsideScopeOrDeviceIsRejected() {
        var r=row();when(scope.resolveAllCurrent(any())).thenReturn(Set.of(101L));when(mapper.selectFormalHistoryPage(any())).thenReturn(new PageResult<>(List.of(r),1L));
        r.setProjectId(102L);assertThrows(IllegalStateException.class,()->service.pageSourceRecords(new Query(1L,1L,101L,500L,1,20)));
        r.setProjectId(101L);r.setEquipmentId(501L);assertThrows(IllegalStateException.class,()->service.pageSourceRecords(new Query(1L,1L,101L,500L,1,20)));verifyNoInteractions(devices);
    }
}
