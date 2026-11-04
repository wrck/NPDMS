package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.*;
import cn.iocoder.yudao.module.pms.commerce.api.migration.*;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationAddressImportApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailCatalog;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailNormalizer;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectLegacyShipmentSourceIntegrityTest {
    final ProjectLegacyDetailMapper mapper=mock(ProjectLegacyDetailMapper.class);
    final DppmsAssetEntityImportApi assets=mock(DppmsAssetEntityImportApi.class);
    final DppmsInstallationAddressImportApi installation=mock(DppmsInstallationAddressImportApi.class);
    final ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    final PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    final Map<String,List<ProjectLegacyRecordDO>> facts=new HashMap<>();
    final ProjectLegacySourceDO source=new ProjectLegacySourceDO();
    final ProjectLegacySnapshotDO snapshot=new ProjectLegacySnapshotDO();
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);
        source.setId(10L);source.setProjectId(100L);source.setSourceProjectKey("101");
        source.setSourceContractNo("SYNTHETIC");source.setCurrentSnapshotId(20L);
        snapshot.setId(20L);snapshot.setSourceId(10L);snapshot.setChecksum("a".repeat(64));
        snapshot.setSourceReadAt(java.time.LocalDateTime.of(2026,10,8,8,0));
        facts.put("contracts",List.of(record("contracts","301",Map.of("contractNo","SYNTHETIC"))));
        facts.put("shipment-packages",List.of(record("shipment-packages","PACK",Map.of("packlist_id","PACK"))));
        facts.put("shipment-facts",List.of(record("shipment-facts","401",Map.of(
            "barcode","Original-SN","pack_id","PACK","item","SKU","item2","SECONDARY","isRMA","1","rmaBarcode","RMA-SN"))));
        facts.put("shipment-materials",List.of(record("shipment-materials","601",Map.of(
            "id","601","item","SKU","describe_","原产品","itemname","原型号"))));
        when(mapper.selectProjectSources(any())).thenReturn(List.of(source));
        when(mapper.selectSourceForUpdate(any())).thenReturn(source);
        when(mapper.selectSnapshot(any())).thenReturn(snapshot);
        when(mapper.selectRecords(any())).thenAnswer(call->facts.getOrDefault(
            call.getArgument(0,ProjectLegacyDetailMapper.RecordsPageQuery.class).domainCode(),List.of()));
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.Report.class),any(),any()))
            .thenAnswer(call->new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                call.getArgument(3,Supplier.class).get()));
        when(assets.importEntities(any())).thenAnswer(call->List.of(NativeEntityMigrationRow.mapped("shipment-facts","401",
            new ExternalTargetMapping("AST","DeviceShipment","ast_device_shipment",701L,"SHIPMENT",0),
            new ExternalTargetMapping("AST","Device","ast_device",702L,"DEVICE",0))));
        // Commerce package receipt is a required business fact.
        var commerce=mock(DppmsCommerceDetailImportApi.class);
        when(commerce.importDetails(any())).thenReturn(List.of(NativeEntityMigrationRow.mapped("shipment-packages","PACK",
            new ExternalTargetMapping("COM","ShipmentPackage","com_shipment_package",703L,"PACKAGE",0))));
        var associated=mock(ProjectAssociatedDataService.class);
        when(associated.importRecords(any(),anyMap(),any())).thenReturn(new ProjectAssociatedDataService.ImportResult(List.of(),Map.of()));
        fixtureService=new ProjectLegacyEntityMigrationService(mock(ProjectManualCreationService.class),mapper,new LegacyDetailCatalog(),
            mock(ProjectPartyMapper.class),mock(DppmsCommerceEntityResolveApi.class),assets,mock(DppmsAssetSourceHistoryApi.class),
            mock(DppmsWarrantySourceRecordsApi.class),installation,evidence,commands,associated,commerce, mock(cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationSourceQueryApi.class),
            mock(cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi.class),
            mock(cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi.class),new ProjectFactoryRecordImportService(evidence));
    }
    ProjectLegacyEntityMigrationService fixtureService;
    @AfterEach void clear(){TenantContextHolder.clear();}
    ProjectLegacyEntityMigrationService.Result migrate(){
        snapshot.setRecordCount(facts.values().stream().mapToInt(List::size).sum());
        var counts=new LinkedHashMap<String,Integer>();new LegacyDetailCatalog().domains().forEach(d->counts.put(d.code(),0));
        facts.forEach((domain,rows)->counts.put(domain,rows.size()));snapshot.setDomainCountsJson(JsonUtils.toJsonString(counts));
        return fixtureService.migrate(100L,new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),new ProjectAccessActor(1L,1L));
    }
    @Test void rmaExcludedFromTheLegacyViewStillReachesTheShipmentBusinessOwner(){
        migrate();
        verify(assets).importEntities(argThat(c->{
            var s=c.shipments().getFirst();
            return "Original-SN".equals(s.deviceSn())&&"SKU".equals(s.productCode())
                &&"原型号".equals(s.productDetails().productModel())
                &&"原产品".equals(s.productDetails().productName())
                &&"RMA-SN".equals(s.details().rmaRelatedSn())&&Integer.valueOf(1).equals(s.details().isRma())
                &&"SECONDARY".equals(s.details().secondaryItem());
        }));
    }
    @Test void caseInsensitiveIdentityAndOldTransferStateDoNotDropOriginalInstallationFields(){
        facts.put("installations",List.of(record("installations","801",Map.of(
            "id","801","projectId","101","barcode","original-sn","transferFlag","1",
            "installAddress","","chProjectId","102","transferProjectId","101","contractNo","OLD"))));
        when(installation.importAddresses(any())).thenReturn(List.of(NativeEntityMigrationRow.mapped("installations","801",
            new ExternalTargetMapping("IMP","InstallationAddressSourceFact","imp_installation_address_source_fact",802L,"SOURCE_ADDRESS",0))));
        migrate();
        verify(installation).importAddresses(argThat(c->{
            var a=c.addresses().getFirst();
            return Long.valueOf(702L).equals(a.deviceId())&&Long.valueOf(801L).equals(a.sourceId())
                &&"101".equals(a.sourceProjectKey())&&"original-sn".equals(a.sourceDeviceSn())
                &&"".equals(a.address())&&Integer.valueOf(1).equals(a.details().sourceTransferFlag())
                &&"102".equals(a.details().changedProjectKey());
        }));
    }
    @Test void sharedMaterialRetainsEveryShipmentWithStableUniquePlatformPositions(){
        var first=record("shipment-facts","401",Map.of(
            "barcode","Original-SN","pack_id","PACK","item","SKU"));
        var second=record("shipment-facts","402",Map.of(
            "barcode","Original-SN","pack_id","PACK","item","SKU"));
        facts.put("shipment-facts",List.of(second,first));
        var firstTarget=new ExternalTargetMapping("AST","DeviceShipment","ast_device_shipment",701L,"SHIPMENT",0);
        var secondTarget=new ExternalTargetMapping("AST","DeviceShipment","ast_device_shipment",704L,"SHIPMENT",0);
        var duplicate=assertThrows(PlatformMigrationEvidenceException.class,()->new AppendExternalMappingCommand(
            1L,900L,901L,SourceReconciliationType.MAPPED,List.of(secondTarget,firstTarget),
            "material-multishipment","material-correlation"));
        assertEquals(PlatformMigrationEvidenceException.Code.INVALID_REQUEST,duplicate.getCode());
        assertEquals("duplicate targetRole and targetSequence",duplicate.getMessage());
        when(assets.importEntities(any())).thenReturn(List.of(
            NativeEntityMigrationRow.mapped("shipment-facts","402",secondTarget,
                new ExternalTargetMapping("AST","Device","ast_device",702L,"DEVICE",0)),
            NativeEntityMigrationRow.mapped("shipment-facts","401",firstTarget,
                new ExternalTargetMapping("AST","Device","ast_device",702L,"DEVICE",0))));
        List<AppendExternalMappingCommand> mappings=new ArrayList<>();
        doAnswer(call->{
            if("shipment-materials".equals(call.getArgument(2,String.class))){
                Map<String,NativeEntityMigrationRow> outcomes=call.getArgument(4);
                var outcome=outcomes.get(ProjectLegacyNativeEvidenceService.identity("shipment-materials","601"));
                mappings.add(new AppendExternalMappingCommand(1L,900L,901L,SourceReconciliationType.MAPPED,
                    outcome.targets(),"material-multishipment","material-correlation"));
            }
            return null;
        }).when(evidence).reconcile(any(),any(),anyString(),anyList(),anyMap());
        migrate();
        assertEquals(1,mappings.size());
        var targets=mappings.getFirst().targets();
        assertEquals(List.of(701L,704L),targets.stream().map(ExternalTargetMapping::targetId).toList());
        assertEquals(List.of(0,1),targets.stream().map(ExternalTargetMapping::targetSequence).toList());
        assertTrue(targets.stream().allMatch(target->"SHIPMENT".equals(target.targetRole())
            &&"AST".equals(target.targetContext())&&"DeviceShipment".equals(target.targetObjectType())
            &&"ast_device_shipment".equals(target.targetTable())));
    }
    @Test void unresolvedRequiredShipmentAbortsBeforeAnySuccessEvidence(){
        when(assets.importEntities(any())).thenReturn(List.of(NativeEntityMigrationRow.retained(
            "shipment-facts","401","SHIPMENT_SOURCE_IDENTITY_UNRESOLVED")));
        assertThrows(RuntimeException.class,this::migrate);
        verifyNoInteractions(evidence);
    }
    static ProjectLegacyRecordDO record(String domain,String key,Map<String,String> values){
        var r=new ProjectLegacyRecordDO();r.setDomainCode(domain);r.setSourceKey(key);
        var full=new LinkedHashMap<String,String>();new LegacyDetailCatalog().require(domain).fields().forEach(f->full.put(f.key(),null));
        full.putAll(values);r.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(full));return r;
    }
}
