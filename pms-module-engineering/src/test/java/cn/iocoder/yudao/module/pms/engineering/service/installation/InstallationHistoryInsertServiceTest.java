package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.*;
import cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationHistoryInsertApi.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.*;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstallationHistoryInsertServiceTest {
    final InstallationMapper records=mock(InstallationMapper.class);
    final InstallationHistoryInsertMapper inserts=mock(InstallationHistoryInsertMapper.class);
    final ProjectDeviceSelectionApi devices=mock(ProjectDeviceSelectionApi.class);
    final AssetLocationApi locations=mock(AssetLocationApi.class);
    final EngineeringRecordCodeGenerator codes=mock(EngineeringRecordCodeGenerator.class);
    final SystemUserMigrationApi historicalUsers=mock(SystemUserMigrationApi.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final AdminUserApi users=mock(AdminUserApi.class);
    final InstallationHistoryInsertService service=new InstallationHistoryInsertService(records,inserts,devices,locations,codes,historicalUsers,permissions,users);
    final LocalDateTime created=LocalDateTime.of(2020,1,2,3,4,5);
    Command command(){return new Command(1L,2L,30L,List.of(row("40","原地址",created)));}
    Row row(String id,String address,LocalDateTime time) {
        var raw=new LinkedHashMap<String,String>();
        cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS.forEach(n->raw.put(n,null));
        raw.put("id",id);raw.put("projectId","300");raw.put("barcode","SN");raw.put("installAddress",address);
        raw.put("transferFlag","-1");raw.put("createTime",time.toString());raw.put("createBy","old-user");
        return new Row(id,50L,raw);
    }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);
        when(historicalUsers.getHistoricalIdentitiesByUsername(1L,Set.of("old-user"))).thenReturn(List.of(new SystemUserMigrationApi.HistoricalIdentity(70L,"old-user")));
        when(devices.validateSelection(any(ProjectDeviceSelectionApi.SelectionQuery.class))).thenReturn(List.of(new SelectedProjectDevice(50L,"SN",null,null,null,null)));
        when(codes.next(eq(30L),eq("AZ"),eq(records),any(String[].class))).thenReturn("P-AZ-001");
        when(inserts.insertHistorical(any())).thenReturn(1);
        when(locations.maintain(any())).thenReturn(new LocationReferenceDTO("RESOLVED",80L,0L,null,null,null,null,"原地址"));
        when(locations.getAddress(80L,0L)).thenReturn(new AddressRespDTO(80L,null,null,null,null,null,null,null,null,null,"原地址",null,null,0,0L));
    }
    @AfterEach void close(){TenantContextHolder.clear();}
    @Test void previewDoesNotInsertRecordsOrCreateAddresses() {
        assertEquals(1,service.preview(command()).size());verifyNoInteractions(inserts,locations);
    }
    @Test void insertUsesApprovedHistoricalValuesAndFillsOnlyMissingCurrentLocation() {
        var result=service.insert(command());assertEquals(1,result.size());
        var captured=org.mockito.ArgumentCaptor.forClass(InstallationDO.class);verify(inserts).insertHistorical(captured.capture());
        var row=captured.getValue();assertEquals("70",row.getCreator());assertNull(row.getUpdater());
        assertEquals(created,row.getCreateTime());assertEquals(created,row.getUpdateTime());assertEquals(2,row.getStatus());
        assertNull(row.getInstallerUserId());assertNull(row.getSiteId());assertEquals("UNRESOLVED",row.getLocationResolutionStatus());
        assertEquals("P-AZ-001",row.getCode());assertNull(row.getEffectiveTo());verify(locations).effectEquipmentLocationIfAbsent(any());
    }
    @Test void newerCurrentInstallationSurvivesAndOlderCurrentRejectsBeforeWrites() {
        var current=new InstallationDO();current.setId(90L);current.setEffectiveFrom(created.plusDays(1));
        when(records.selectCurrentRecordForUpdate(any())).thenReturn(current);
        assertEquals(current.getEffectiveFrom(),service.insert(command()).getFirst().effectiveTo());
        verify(locations,never()).effectEquipmentLocationIfAbsent(any());
        clearInvocations(inserts,locations);current.setEffectiveFrom(created.minusDays(1));
        assertThrows(IllegalArgumentException.class,()->service.insert(command()));verifyNoInteractions(inserts,locations);
    }
    @Test void tenantPermissionAndSourceIdentityFailuresCannotInsert() {
        var c=command();assertThrows(IllegalArgumentException.class,()->service.insert(new Command(9L,2L,30L,c.rows())));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.insert(new Command(1L,3L,30L,c.rows())));
        when(devices.validateSelection(any(ProjectDeviceSelectionApi.SelectionQuery.class))).thenReturn(List.of(new SelectedProjectDevice(50L,"OTHER",null,null,null,null)));
        assertThrows(IllegalArgumentException.class,()->service.insert(command()));verifyNoInteractions(inserts,locations);
    }
    @Test void multipleHistoricalRowsForOneDeviceCreateOnlyOneCurrentLocation() {
        var older=row("41","更早地址",created.minusDays(2));
        var preview=service.preview(new Command(1L,2L,30L,List.of(older,command().rows().getFirst())));
        assertEquals("40",preview.getFirst().sourceKey());assertNull(preview.getFirst().effectiveTo());
        assertEquals(created,preview.getLast().effectiveTo());verifyNoInteractions(inserts,locations);
    }
    @Test void sameLatestTimeAndTransferRowsFailBeforeAnyAddressOrRecordWrite() {
        assertThrows(IllegalArgumentException.class,()->service.insert(new Command(1L,2L,30L,List.of(row("40","原地址",created),row("41","原地址",created)))));
        var transfer=row("42","原地址",created);transfer.originalFields().put("transferProjectId","900");
        assertThrows(IllegalArgumentException.class,()->service.insert(new Command(1L,2L,30L,List.of(transfer))));
        verifyNoInteractions(inserts,locations);
    }
    @Test void boundHistoryCanOnlyBeReadBackUnchangedAndCannotBeReinsertedOrRevived() {
        var record=new InstallationDO();record.setId(80L);record.setTenantId(1L);record.setProjectId(30L);
        record.setEquipmentId(50L);record.setCode("P-AZ-001");record.setDeleted(false);record.setInstallLocation("原地址");
        var baseline=InstallationHistoricalCarryRules.formalJson(record);
        when(inserts.selectBoundRecordForUpdate(any())).thenReturn(record);
        var verification=new Verification(1L,2L,30L,List.of(new Existing("40",80L,50L,baseline)));
        assertEquals(80L,service.verify(verification).getFirst().installationId());
        record.setInstallLocation("changed");assertThrows(IllegalArgumentException.class,()->service.verify(verification));
        record.setInstallLocation("原地址");record.setDeleted(true);
        assertThrows(IllegalArgumentException.class,()->service.verify(verification));
        verify(inserts,never()).insertHistorical(any());verifyNoInteractions(locations);
    }
}
