package cn.iocoder.yudao.module.pms.asset.service.migration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMigrationIdentityMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.*;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class DppmsAssetSourceHistoryServiceTest {
    @Mock DeviceSoftwareVersionRecordMapper observations;
    @Mock DeviceSoftwareChangeBatchMapper batches;
    @Mock DeviceFactoryVersionRecordMapper factories;
    @Mock DeviceMigrationIdentityMapper identities;
    @Mock ProjectDeviceAssignmentGuardApi guard;
    @Mock DeviceAccessScopeService scope;
    @Mock cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi historicalUsers;
    @Mock cn.iocoder.yudao.module.pms.asset.service.version.SoftwareVersionLegacyExtensionService legacyExtensions;
    @Mock cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi extensions;
    DppmsAssetSourceHistoryService service;
    LocalDateTime at=LocalDateTime.of(2024,1,2,3,4,5);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        service=new DppmsAssetSourceHistoryService(observations,batches,factories,identities,guard,scope,historicalUsers,legacyExtensions,extensions);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    SoftwareObservation observation() {
        return new SoftwareObservation(301L,10L,302L,"CONTRACT","ITEM","source-sn","NEW","TYPE","SERIES","MARK","OLD",0,
                "CPLD","CPLD-OLD",0,"BOOT","BOOT-OLD",0,"PCB","PCB-OLD",0,null,0,at,"creator",at,"updater","{\"value\":null}").withMapping(null,900L,99L);
    }
    Command command() {
        return new Command(1L,1L,100L,"10","v4",at,List.of(observation()),List.of(),List.of());
    }
    @Test void combinedPreviewValidatesParentsAndExtensionsWithoutAnyWrite() {
        when(guard.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));
        when(historicalUsers.getHistoricalIdentitiesByUsername(1L,Set.of("creator"))).thenReturn(List.of(new cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.HistoricalIdentity(11L,"creator")));
        when(historicalUsers.getHistoricalIdentitiesByUsername(1L,Set.of("updater"))).thenReturn(List.of(new cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.HistoricalIdentity(12L,"updater")));
        var source=observation().withMapping(null,null,99L);
        var batch=new SoftwareBatch(302L,10L,"v1",null,1,null,at,null,null);
        var result=service.previewSourceHistory(new Command(1L,1L,100L,"10","preview",at,List.of(source),List.of(batch),List.of()));
        assertEquals(2,result.size());assertTrue(result.stream().allMatch(r->r.targetId()<0));
        verify(observations,never()).insertHistorical(any());verify(batches,never()).insertHistorical(any());
        verify(legacyExtensions,never()).initialize(anyLong(),anyLong(),anyLong(),anyLong(),any());
        verify(legacyExtensions).preview(argThat(q->q.recordId()<0 && q.definitionId()==99L && q.values().keySet().size()==8));
    }
    @Test void previewCannotForgeAVirtualParentOrSkipMissingParent() {
        when(guard.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));
        var forged=observation().withMapping(null,-302L,99L);
        assertThrows(IllegalArgumentException.class,()->service.previewSourceHistory(new Command(1L,1L,100L,"10","preview",at,List.of(forged),List.of(),List.of())));
        var missing=observation().withMapping(null,null,99L);
        assertThrows(IllegalArgumentException.class,()->service.previewSourceHistory(new Command(1L,1L,100L,"10","preview",at,List.of(missing),List.of(),List.of())));
        verify(observations,never()).insertHistorical(any());verify(batches,never()).insertHistorical(any());
    }
    @Test void batchUsesFormalFieldsAndPreservesNullAuditWithHistoricalDateFallback() {
        when(guard.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));
        when(batches.insertHistorical(any())).thenReturn(1);
        var source=new SoftwareBatch(302L,10L,"v1","",1,null,at,null,null);
        var result=service.importSourceHistory(new Command(1L,1L,100L,"10","revision",at,List.of(),List.of(source),List.of()));
        var capture=ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceSoftwareBatchHistoricalWrite.class);verify(batches).insertHistorical(capture.capture());
        var row=capture.getValue().values();assertNull(row.getCreator());assertNull(row.getUpdater());
        assertEquals(at,row.getCreateTime());assertEquals(at,row.getUpdateTime());assertEquals("",row.getChangeRemark());
        assertEquals("ast_device_software_change_batch",result.getFirst().targets().getFirst().targetTable());
        verifyNoInteractions(historicalUsers,observations,factories,identities);
    }
    @Test void batchReplayRequiresTheExistingBindingAndAllOfficialFieldsUnchanged() {
        when(guard.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));
        var row=new DeviceSoftwareChangeBatchDO();row.setId(700L);row.setTenantId(1L);row.setProjectId(100L);
        row.setChangeVersion("v1");row.setChangeRemark("");row.setLatest(1);row.setCreateTime(at);row.setUpdateTime(at);row.setDeleted(false);
        when(batches.selectRecordForUpdate(any())).thenReturn(row);
        var source=new SoftwareBatch(302L,10L,"v1","",1,null,at,null,null,700L);
        var input=new Command(1L,1L,100L,"10","revision",at,List.of(),List.of(source),List.of());
        assertEquals(700L,service.importSourceHistory(input).getFirst().targets().getFirst().targetId());
        verify(batches,never()).insertHistorical(any());row.setLatest(0);
        assertThrows(IllegalArgumentException.class,()->service.importSourceHistory(input));
    }
    @Test void batchActorsUseExactSystemIdentityAndMissingCreateDateIsBlocked() {
        when(guard.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));
        when(historicalUsers.getHistoricalIdentitiesByUsername(1L,Set.of("c001"))).thenReturn(List.of(new cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.HistoricalIdentity(8L,"c001")));
        when(batches.insertHistorical(any())).thenReturn(1);
        var source=new SoftwareBatch(302L,10L,null,null,0,"c001",at,"original-service",null);
        service.importSourceHistory(new Command(1L,1L,100L,"10","revision",at,List.of(),List.of(source),List.of()));
        verify(batches).insertHistorical(argThat(r->"8".equals(r.values().getCreator()) && "original-service".equals(r.values().getUpdater())));
        var invalid=new SoftwareBatch(303L,10L,null,null,0,null,null,null,null);
        assertThrows(IllegalArgumentException.class,()->service.importSourceHistory(new Command(1L,1L,100L,"10","revision",at,List.of(),List.of(invalid),List.of())));
        verify(batches,times(1)).insertHistorical(any());
    }
    void allowedDevice(Long project) {
        when(guard.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));
        var device=new DeviceDO();device.setId(500L);device.setSn("SOURCE-SN");device.setProjectId(project);
        when(identities.selectIdentityForUpdate(any())).thenReturn(device);
        when(batches.selectRecordForUpdate(any())).thenReturn(new DeviceSoftwareChangeBatchDO());
        when(historicalUsers.getHistoricalIdentitiesByUsername(1L,Set.of("creator"))).thenReturn(List.of(new cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.HistoricalIdentity(11L,"creator")));
        when(historicalUsers.getHistoricalIdentitiesByUsername(1L,Set.of("updater"))).thenReturn(List.of(new cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.HistoricalIdentity(12L,"updater")));
    }
    @Test void foreignTenantFailsBeforeOwnerLookups() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,()->service.importSourceHistory(command()));
        verifyNoInteractions(guard,identities,observations,batches,factories);
    }
    @Test void projectWritePermissionIsRequiredBeforeSourceIdentityLocks() {
        assertThrows(IllegalArgumentException.class,()->service.importSourceHistory(command()));
        verifyNoInteractions(identities,observations,batches,factories);
    }
    @Test void historicalProjectAssociationDoesNotOverwritePresentDeviceOwner() {
        allowedDevice(101L);when(observations.insertHistorical(any())).thenReturn(1);
        var result=service.importSourceHistory(command());
        verify(observations).insertHistorical(argThat(r->Objects.equals(r.values().getDeviceId(),500L) && Objects.equals(r.values().getProjectId(),100L)));
        assertEquals("ast_device_software_version_record",result.getFirst().targets().getFirst().targetTable());
    }
    @Test void originalBackupsAndFlagsUseFormalExtensionsWhileAuditAndVersionsUseNamedFields() {
        allowedDevice(100L);when(observations.insertHistorical(any())).thenReturn(1);
        var result=service.importSourceHistory(command());
        var capture=ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceSoftwareRecordHistoricalWrite.class);verify(observations).insertHistorical(capture.capture());
        var row=capture.getValue().values();assertEquals("source-sn",row.getDeviceSn());assertEquals("NEW",row.getConpVersion());
        assertEquals("BOOT",row.getBootVersion());assertEquals(0,row.getDataState());assertNull(row.getExecuteTime());
        assertEquals(900L,row.getChangeBatchId());assertEquals("11",row.getCreator());assertEquals("12",row.getUpdater());
        assertEquals(at,row.getCreateTime());assertEquals(at,row.getRecordedAt());assertNull(row.getPreviousRecordId());
        verify(legacyExtensions).initialize(eq(1L),eq(1L),eq(row.getId()),eq(99L),argThat(v->v.size()==8 && "OLD".equals(v.get("conp_bak")) && Integer.valueOf(0).equals(v.get("boot_change"))));
        assertEquals("SoftwareVersionRecord",result.getFirst().targets().getFirst().targetObjectType());
    }
    @Test void mappedHistoryReplayComparesEveryFormalFieldWithoutReplacingAuditOrCurrentValues() {
        allowedDevice(100L);when(observations.insertHistorical(any())).thenReturn(1);
        service.importSourceHistory(command());
        var capture=ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceSoftwareRecordHistoricalWrite.class);verify(observations).insertHistorical(capture.capture());
        var existing=capture.getValue().values();when(observations.selectForUpdate(any())).thenReturn(existing);
        var mapped=new Command(1L,1L,100L,"10","v4",at,List.of(observation().withMapping(existing.getId(),900L,99L)),List.of(),List.of());
        assertEquals(existing.getId(),service.importSourceHistory(mapped).getFirst().targets().getFirst().targetId());
        existing.setUpdater("subsequent-editor");assertThrows(IllegalArgumentException.class,()->service.importSourceHistory(mapped));
        verify(observations,times(1)).insertHistorical(any());verify(observations,never()).updateById(any(DeviceSoftwareVersionRecordDO.class));
    }
    @Test void missingMappedHistoryCannotBeRecreatedOrRevived() {
        allowedDevice(100L);
        var input=new Command(1L,1L,100L,"10","v4",at,List.of(observation().withMapping(700L,900L,99L)),List.of(),List.of());
        assertThrows(IllegalArgumentException.class,()->service.importSourceHistory(input));
        verify(observations,never()).insertHistorical(any());verifyNoInteractions(legacyExtensions);
    }
    @Test void sourceFactoryFactsDoNotNeedAMesBaselineOrInventedEventTime() {
        when(guard.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));when(factories.insert(any(DeviceFactoryVersionRecordDO.class))).thenReturn(1);
        var input=new Command(1L,1L,100L,"10","v4",at,List.of(),List.of(),List.of(new FactoryVersion("source-content-key:2","source-sn","C","B","D","P")));
        var result=service.importSourceHistory(input);
        var capture=ArgumentCaptor.forClass(DeviceFactoryVersionRecordDO.class);verify(factories).insert(capture.capture());
        assertEquals("source-sn",capture.getValue().getDeviceSn());assertEquals("C",capture.getValue().getConpVersion());
        assertEquals("source-content-key:2",result.getFirst().sourceKey());
        assertEquals("ast_device_factory_version_record",result.getFirst().targets().getFirst().targetTable());
        verifyNoInteractions(observations,batches,identities);
    }
    @Test void mappedFactoryRecordReplaysWithoutAuditChangesOrCurrentDeviceLookup() {
        when(guard.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));
        var row=new DeviceFactoryVersionRecordDO();row.setId(700L);row.setTenantId(1L);row.setProjectId(100L);
        row.setDeviceSn("source-sn");row.setConpVersion("C");row.setBootVersion("");row.setCpldVersion(null);row.setPcbVersion("P");
        row.setCreator("historic");row.setCreateTime(at);row.setUpdater("");row.setUpdateTime(at);
        when(factories.selectByIdForUpdate(any())).thenReturn(row);
        var input=new Command(1L,1L,100L,"10","v4",at,List.of(),List.of(),List.of(new FactoryVersion("key:1","source-sn","C","",null,"P",700L)));
        assertEquals(700L,service.importSourceHistory(input).getFirst().targets().getFirst().targetId());
        assertEquals("historic",row.getCreator());assertEquals(at,row.getUpdateTime());
        verifyNoInteractions(identities,observations,batches);verify(factories,never()).insert(any(DeviceFactoryVersionRecordDO.class));
        row.setBootVersion(null);assertThrows(IllegalArgumentException.class,()->service.importSourceHistory(input));
    }
    @Test void deletedForeignOrMissingMappedFactoryRecordCannotBeRecreated() {
        when(guard.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));
        var input=new Command(1L,1L,100L,"10","v4",at,List.of(),List.of(),List.of(new FactoryVersion("key:1","source-sn",null,null,null,null,700L)));
        assertThrows(IllegalArgumentException.class,()->service.importSourceHistory(input));
        var row=new DeviceFactoryVersionRecordDO();row.setId(700L);row.setTenantId(2L);row.setProjectId(100L);row.setDeviceSn("source-sn");
        when(factories.selectByIdForUpdate(any())).thenReturn(row);assertThrows(IllegalArgumentException.class,()->service.importSourceHistory(input));
        row.setTenantId(1L);row.setProjectId(101L);assertThrows(IllegalArgumentException.class,()->service.importSourceHistory(input));
        row.setProjectId(100L);row.setDeleted(true);assertThrows(IllegalArgumentException.class,()->service.importSourceHistory(input));
        verify(factories,never()).insert(any(DeviceFactoryVersionRecordDO.class));verifyNoInteractions(identities);
    }
    @Test void missingVisibleProjectScopeReturnsEmptyWithoutExecutingAssetQueries() {
        when(scope.visibleProjectIds(1L,1L)).thenReturn(Set.of());
        assertEquals(0L,service.pageObservations(new Query(1L,1L,100L,null,1,20)).getTotal());
        assertEquals(0L,service.pageFactories(new Query(1L,1L,100L,null,1,20)).getTotal());
        verifyNoInteractions(observations,factories,batches);
    }
    @Test void excessivePageSizeFailsBeforeAnyReadQuery() {
        assertThrows(IllegalArgumentException.class,()->service.pageBatches(new Query(1L,1L,100L,null,1,101)));
        verifyNoInteractions(scope,observations,factories,batches);
    }
}
