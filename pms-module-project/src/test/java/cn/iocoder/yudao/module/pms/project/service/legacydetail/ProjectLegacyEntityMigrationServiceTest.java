package cn.iocoder.yudao.module.pms.project.service.legacydetail;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetEntityImportApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationAddressImportApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailCatalog;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailNormalizer;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ProjectLegacyEntityMigrationServiceTest {
    ProjectManualCreationService projects=mock(ProjectManualCreationService.class);
    ProjectLegacyDetailMapper mapper=mock(ProjectLegacyDetailMapper.class);
    ProjectPartyMapper parties=mock(ProjectPartyMapper.class);
    DppmsCommerceEntityResolveApi commerce=mock(DppmsCommerceEntityResolveApi.class);
    DppmsAssetEntityImportApi assets=mock(DppmsAssetEntityImportApi.class);
    DppmsAssetSourceHistoryApi sourceHistory=mock(DppmsAssetSourceHistoryApi.class);
    DppmsInstallationAddressImportApi installations=mock(DppmsInstallationAddressImportApi.class);
    cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceDetailImportApi details=mock(cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceDetailImportApi.class);
    ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    ProjectAssociatedDataService associated=mock(ProjectAssociatedDataService.class);
    ProjectLegacyEntityMigrationService service=new ProjectLegacyEntityMigrationService(projects,mapper,new LegacyDetailCatalog(),parties,
            commerce,assets,sourceHistory,mock(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsWarrantySourceRecordsApi.class),installations,evidence,commands,
            associated,details, mock(cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationSourceQueryApi.class),
            mock(cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi.class),
            mock(cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi.class),new ProjectFactoryRecordImportService(evidence));
    ProjectAccessActor actor=new ProjectAccessActor(1L,1L);
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); when(associated.importRecords(any(),anyMap(),any())).thenReturn(new ProjectAssociatedDataService.ImportResult(List.of(),Map.of())); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void wrongTenantAndDeniedManagementStopAllPersistence() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(RuntimeException.class,()->service.migrate(100L,new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor));
        verifyNoInteractions(mapper,parties,commerce,assets,commands);
        TenantContextHolder.setTenantId(1L);doThrow(new IllegalArgumentException("DENIED")).when(projects).getProjectForManage(100L,actor);
        assertThrows(RuntimeException.class,()->service.migrate(100L,new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor));
        verifyNoInteractions(mapper,parties,commerce,assets,commands);
    }
    @Test void staleHeadCannotPromoteAnOlderSnapshot() {
        var source=new ProjectLegacySourceDO();source.setId(10L);source.setProjectId(100L);source.setSourceProjectKey("101");source.setCurrentSnapshotId(21L);
        var snapshot=new ProjectLegacySnapshotDO();snapshot.setId(20L);snapshot.setSourceId(10L);
        when(mapper.selectProjectSources(any())).thenReturn(List.of(source));when(mapper.selectSourceForUpdate(any())).thenReturn(source);
        when(mapper.selectSnapshot(any())).thenReturn(snapshot);
        assertThrows(RuntimeException.class,()->service.migrate(100L,new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor));
        verifyNoInteractions(parties,commerce,assets,mock(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.class),mock(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsWarrantySourceRecordsApi.class),installations,evidence,commands,
            mock(ProjectAssociatedDataService.class),details, mock(cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationSourceQueryApi.class),
            mock(cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi.class),
            mock(cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi.class));
    }
    @Test void foreignSourceCannotReadNativeEvidence() {
        assertThrows(RuntimeException.class,()->service.status(100L,99L,null,actor));
        verifyNoInteractions(evidence,commerce,assets);
    }
    @Test void oldSharedDefinitionReferencesRemainDistinctAndDatesKeepDatePrecision() {
        assertNotEquals(ProjectLegacyNativeEvidenceService.sourcePk("101",1L,"SHARED"),ProjectLegacyNativeEvidenceService.sourcePk("102",1L,"SHARED"));
        assertNotEquals(ProjectLegacyNativeEvidenceService.sourcePk("101",1L,"SHARED"),ProjectLegacyNativeEvidenceService.sourcePk("101",2L,"SHARED"));
        assertEquals(java.time.LocalDateTime.of(2024,1,2,0,0),ProjectLegacyEntityMigrationService.time("2024-01-02"));
        assertEquals(java.time.LocalDateTime.of(2023,3,29,8,38,51),ProjectLegacyEntityMigrationService.time("2023-03-29 08:38:51.000"));
        assertThrows(ArithmeticException.class,()->ProjectLegacyEntityMigrationService.integer("36.5"));
    }
    @Test void incompleteOldDomainCoverageCannotInvokeAnyBusinessOwner() {
        var row=record("contracts","301",Map.of("contractNo","SYNTHETIC"));
        var snapshot=prepareSnapshot(List.of(row));
        snapshot.setDomainCountsJson(JsonUtils.toJsonString(Map.of("contracts",1)));
        var failure=assertThrows(RuntimeException.class,()->service.migrate(100L,
                new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor));
        assertTrue(failure.getMessage().contains("DPPMS_NATIVE_FULL_SOURCE_REQUIRED"));
        verifyNoInteractions(commands,parties,commerce,assets,installations,details,evidence);
    }
    @Test void incompleteOriginalRecordIncludingAnOmittedNullCannotInvokeAnyBusinessOwner() {
        var row=record("contracts","301",Map.of("contractNo","SYNTHETIC"));
        prepareSnapshot(List.of(row));
        var values=JsonUtils.parseObject(row.getPayloadJson(),new tools.jackson.core.type.TypeReference<Map<String,String>>(){});
        String nullField=values.keySet().stream().filter(field->values.get(field)==null).findFirst().orElseThrow();
        values.remove(nullField);row.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(values));
        var failure=assertThrows(RuntimeException.class,()->service.migrate(100L,
                new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor));
        assertTrue(failure.getMessage().contains("DPPMS_NATIVE_FULL_SOURCE_REQUIRED"));
        verifyNoInteractions(commands,parties,commerce,assets,installations,details,evidence);
    }
    @Test void oldPartialSnapshotsRemainReadableAsHistoricalStatus() {
        var row=record("contracts","301",Map.of("contractNo","SYNTHETIC"));
        var snapshot=prepareSnapshot(List.of(row));snapshot.setDomainCountsJson(null);
        row.setPayloadJson(JsonUtils.toJsonString(Map.of("contractNo","SYNTHETIC")));
        var report=service.status(100L,10L,20L,actor);
        assertEquals(1,report.sourceCount());assertEquals(1,report.pendingCount());
        verifyNoInteractions(commands,parties,commerce,assets,installations,details);
    }
    private ProjectLegacySnapshotDO prepareSnapshot(List<ProjectLegacyRecordDO> records) {
        var source=new ProjectLegacySourceDO();source.setId(10L);source.setProjectId(100L);source.setSourceProjectKey("101");
        source.setSourceContractNo("SYNTHETIC");source.setCurrentSnapshotId(20L);
        var snapshot=new ProjectLegacySnapshotDO();snapshot.setId(20L);snapshot.setSourceId(10L);
        snapshot.setRecordCount(records.size());snapshot.setChecksum("a".repeat(64));
        var counts=new LinkedHashMap<String,Integer>();new LegacyDetailCatalog().domains().forEach(d->counts.put(d.code(),0));
        records.forEach(row->counts.compute(row.getDomainCode(),(domain,count)->count+1));
        snapshot.setDomainCountsJson(JsonUtils.toJsonString(counts));
        when(mapper.selectProjectSources(any())).thenReturn(List.of(source));when(mapper.selectSourceForUpdate(any())).thenReturn(source);
        when(mapper.selectSnapshot(any())).thenReturn(snapshot);
        when(mapper.selectRecords(any())).thenAnswer(call->{
            var query=call.getArgument(0,ProjectLegacyDetailMapper.RecordsPageQuery.class);
            return records.stream().filter(row->query.domainCode().equals(row.getDomainCode())).toList();
        });
        return snapshot;
    }
    @Test void repeatedDeviceInSeparatePackagesKeepsBothProductReferences() {
        var source=new ProjectLegacySourceDO();source.setId(10L);source.setProjectId(100L);source.setSourceProjectKey("101");
        source.setSourceContractNo("SYNTHETIC");source.setCurrentSnapshotId(20L);
        var snapshot=new ProjectLegacySnapshotDO();snapshot.setId(20L);snapshot.setSourceId(10L);snapshot.setRecordCount(5);
        snapshot.setChecksum("a".repeat(64));
        var counts=new LinkedHashMap<String,Integer>();new LegacyDetailCatalog().domains().forEach(d->counts.put(d.code(),0));
        counts.put("contracts",1);counts.put("shipment-facts",2);counts.put("shipment-products",2);
        snapshot.setDomainCountsJson(JsonUtils.toJsonString(counts));
        when(mapper.selectProjectSources(any())).thenReturn(List.of(source));when(mapper.selectSourceForUpdate(any())).thenReturn(source);
        when(mapper.selectSnapshot(any())).thenReturn(snapshot);
        var contract=record("contracts","301",Map.of("contractNo","SYNTHETIC"));
        var firstFact=record("shipment-facts","401",Map.of("barcode","SAME-SN","pack_id","PACK-A"));
        var secondFact=record("shipment-facts","402",Map.of("barcode","SAME-SN","pack_id","PACK-B"));
        var firstProduct=record("shipment-products","501",Map.of("barcode","SAME-SN","packId","PACK-A","contract_code","SYNTHETIC"));
        var secondProduct=record("shipment-products","502",Map.of("barcode","SAME-SN","packId","PACK-B","contract_code","SYNTHETIC"));
        when(mapper.selectRecords(any())).thenAnswer(invocation -> {
            var q=invocation.getArgument(0,ProjectLegacyDetailMapper.RecordsPageQuery.class);
            return switch (q.domainCode()) {
                case "contracts" -> List.of(contract);
                case "shipment-facts" -> List.of(firstFact,secondFact);
                case "shipment-products" -> List.of(firstProduct,secondProduct);
                default -> List.of();
            };
        });
        var firstTarget=new ExternalTargetMapping("AST","DeviceShipment","ast_device_shipment",601L,"SHIPMENT",0);
        var secondTarget=new ExternalTargetMapping("AST","DeviceShipment","ast_device_shipment",602L,"SHIPMENT",0);
        when(assets.importEntities(any())).thenAnswer(invocation -> {
            var command=invocation.getArgument(0,DppmsAssetEntityImportApi.Command.class);
            assertEquals(List.of("PACK-A","PACK-B"),command.shipments().stream().map(DppmsAssetEntityImportApi.Shipment::packageKey).toList());
            return List.of(NativeEntityMigrationRow.mapped("shipment-facts","401",firstTarget),
                    NativeEntityMigrationRow.mapped("shipment-facts","402",secondTarget));
        });
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.Report.class),any(),any()))
                .thenAnswer(invocation -> new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        invocation.getArgument(3,Supplier.class).get()));
        var result=service.migrate(100L,new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor);
        assertEquals(4,result.report().mappedCount());
        verify(evidence).reconcile(eq(source),eq(snapshot),eq("shipment-products"),eq(List.of(firstProduct,secondProduct)),argThat(outcomes ->
                outcomes.get("shipment-products:501").targets().equals(List.of(firstTarget))
                        && outcomes.get("shipment-products:502").targets().equals(List.of(secondTarget))));
    }
    @Test void factoryFactsPreserveEveryRecordWithoutAutomaticallyOverwritingTheUniqueMaster() {
        var contract=record("contracts","301",Map.of("contractNo","SYNTHETIC"));
        var first=record("factory-versions","source-factory-A",Map.of("serial_number","Factory-Sn-A",
                "conp"," CONP-A ","boot","BOOT-A","cpld","CPLD-A","pcb","PCB-A"));
        var second=record("factory-versions","source-factory-B",Map.of("serial_number","Factory-Sn-B"));
        var snapshot=prepareSnapshot(List.of(contract,first,second));
        first.setTenantId(1L);first.setSnapshotId(20L);first.setSourceTable("fb_soft_version");
        second.setTenantId(1L);second.setSnapshotId(20L);second.setSourceTable("fb_soft_version");
        var firstTarget=new ExternalTargetMapping("AST","DeviceFactoryVersionRecord","ast_device_factory_version_record",901L,"FACTORY_VERSION_SOURCE",0);
        var secondTarget=new ExternalTargetMapping("AST","DeviceFactoryVersionRecord","ast_device_factory_version_record",902L,"FACTORY_VERSION_SOURCE",0);
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(Map.of("source-factory-A",new ProjectLegacyNativeEvidenceService.RowStatus("NOT_MIGRATED",null,null,List.of()),
                "source-factory-B",new ProjectLegacyNativeEvidenceService.RowStatus("NOT_MIGRATED",null,null,List.of())));
        when(assets.importEntities(any())).thenAnswer(invocation -> {
            assertTrue(invocation.getArgument(0,DppmsAssetEntityImportApi.Command.class).factoryVersions().isEmpty());return List.of();
        });
        when(sourceHistory.importSourceHistory(any())).thenAnswer(invocation -> {
            var command=invocation.getArgument(0,DppmsAssetSourceHistoryApi.Command.class);
            assertEquals(2,command.factories().size());assertEquals(" CONP-A ",command.factories().getFirst().conp());
            assertNull(command.factories().getLast().boot());
            return List.of(NativeEntityMigrationRow.mapped("factory-versions","source-factory-A",firstTarget),
                    NativeEntityMigrationRow.mapped("factory-versions","source-factory-B",secondTarget));
        });
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.Report.class),any(),any()))
                .thenAnswer(invocation -> new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        invocation.getArgument(3,Supplier.class).get()));
        var result=service.migrate(100L,new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor);
        var factorySummary=result.report().domains().stream().filter(domain -> "factory-versions".equals(domain.domain())).findFirst().orElseThrow();
        assertEquals(2,factorySummary.mappedCount());
        assertEquals(List.of("ast_device_factory_version_record"),factorySummary.targetTables());
        verify(assets).importEntities(any());verify(sourceHistory).importSourceHistory(any());
        verify(evidence).reconcile(any(),eq(snapshot),eq("factory-versions"),eq(List.of(first,second)),argThat(outcomes ->
                outcomes.get("factory-versions:source-factory-A").targets().equals(List.of(firstTarget))
                        && outcomes.get("factory-versions:source-factory-B").targets().equals(List.of(secondTarget))));
    }
    @Test void derivedWarrantyIsReportedWithoutIndependentOwnerWritesOrInventedTargets() {
        var contract=record("contracts","301",Map.of("contractNo","SYNTHETIC"));
        var warranty=record("warranties","cached-result",Map.of("barcode","SN","warrantyMonth","36.5","diff","123"));
        prepareSnapshot(List.of(contract,warranty));
        when(assets.importEntities(any())).thenAnswer(call->{
            assertTrue(call.getArgument(0,DppmsAssetEntityImportApi.Command.class).warranties().isEmpty());return List.of();
        });
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.Report.class),any(),any()))
                .thenAnswer(call->new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        call.getArgument(3,Supplier.class).get()));
        var result=service.migrate(100L,new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor);
        var domain=result.report().domains().stream().filter(row->"warranties".equals(row.domain())).findFirst().orElseThrow();
        assertEquals(1,domain.retainedCount());assertEquals(0,domain.mappedCount());assertTrue(domain.targetTables().isEmpty());
        assertEquals(Map.of(LegacyNativeReasons.text("DERIVED_WARRANTY_NOT_INDEPENDENTLY_MIGRATED"),1),domain.retentionReasons());
        verify(evidence).reconcile(any(),any(),eq("warranties"),eq(List.of(warranty)),argThat(outcomes->
                outcomes.get("warranties:cached-result").targets().isEmpty()
                        && "DERIVED_WARRANTY_NOT_INDEPENDENTLY_MIGRATED".equals(outcomes.get("warranties:cached-result").reasonCode())));
    }
    @ParameterizedTest
    @ValueSource(strings={"TARGET_IDENTITY_CONFLICT","FACTORY_SOURCE_CONTENT_CHANGED","MAPPED_RECORD_MISSING"})
    void rejectedFactoryRecordOwnerCannotFallBackToTheUniqueMaster(String reason) {
        var contract=record("contracts","301",Map.of("contractNo","SYNTHETIC"));
        var factory=record("factory-versions","source-factory-A",Map.of("serial_number","Factory-Sn-A"));
        prepareSnapshot(List.of(contract,factory));factory.setTenantId(1L);factory.setSnapshotId(20L);factory.setSourceTable("fb_soft_version");
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(Map.of("source-factory-A",new ProjectLegacyNativeEvidenceService.RowStatus("NOT_MIGRATED",null,null,List.of())));
        when(assets.importEntities(any())).thenAnswer(invocation -> {
            assertTrue(invocation.getArgument(0,DppmsAssetEntityImportApi.Command.class).factoryVersions().isEmpty());return List.of();
        });
        when(sourceHistory.importSourceHistory(any())).thenThrow(new IllegalArgumentException(reason));
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.Report.class),any(),any()))
                .thenAnswer(invocation -> new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        invocation.getArgument(3,Supplier.class).get()));
        var failure=assertThrows(RuntimeException.class,()->service.migrate(100L,
                new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor));
        assertEquals(reason,failure.getMessage());verify(sourceHistory).importSourceHistory(any());
        verify(evidence,never()).reconcile(any(),any(),eq("factory-versions"),anyList(),anyMap());
    }
    @Test void executionNamesFollowExistingCanonicalMapping() {
        var contract=record("contracts","301",Map.of("contractNo","SYNTHETIC"));
        var execution=record("executions","401",Map.of("channelName","  channel  ","customerProjectName"," 项目\n"));
        var snapshot=prepareSnapshot(List.of(contract,execution));
        snapshot.setBatchKey("DPPMS-DETAIL-RAW-NATIVE-V4-FULL-SOURCE-FIELDS-V1");
        when(details.importDetails(any())).thenAnswer(invocation -> {
            var command=invocation.getArgument(0,cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceDetailImportApi.Command.class);
            var row=command.executions().getFirst();
            assertEquals("  channel  ",row.channelName());
            assertEquals(" 项目\n",row.customerProjectName());
            return List.of(NativeEntityMigrationRow.mapped("executions","401",new ExternalTargetMapping("COM","ExecutionOrder","com_crm_execution_order",501L,"EXECUTION",0)));
        });
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.Report.class),any(),any()))
            .thenAnswer(invocation -> {
                var scope=invocation.getArgument(0,PlatformCommandExecutionApi.IdempotencyScope.class);
                assertTrue(scope.toString().contains(":V4"));
                return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                    invocation.getArgument(3,Supplier.class).get());
            });
        var result=service.migrate(100L,new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor);
        assertEquals(1,result.report().mappedCount());
    }
    private void prepareHeaderCorrection() {
        var header=record("project","101",Map.of("projectId","101","column013","实际最终用户"));
        header.setTenantId(1L);header.setSnapshotId(20L);header.setSourceTable("pm_project_header");
        var snapshot=prepareSnapshot(List.of(header));snapshot.setTenantId(1L);
        snapshot.setBatchKey("DPPMS-DETAIL-RAW-NATIVE-V4-FULL-SOURCE-FIELDS-V1");
        mapper.selectProjectSources(new ProjectLegacyDetailMapper.ProjectSourcesQuery(1L,100L)).getFirst().setTenantId(1L);
        var target=new ExternalTargetMapping("PROJ","Project","proj_project",100L,"PROJECT_ENTITY",0);
        var party=new ExternalTargetMapping("PROJ","ProjectParty","proj_project_party",200L,"PARTY",0);
        when(associated.importRecords(any(),anyMap(),any())).thenReturn(new ProjectAssociatedDataService.ImportResult(List.of(NativeEntityMigrationRow.mapped("project","101",target,party)),Map.of()));
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.CorrectionReport.class),any(),any()))
                .thenAnswer(call->new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        call.getArgument(3,Supplier.class).get()));
    }
    @Test void headerCorrectionUsesOnlyApprovedHeaderOwnerAndKeepsOldReceiptSeparate() {
        prepareHeaderCorrection();
        var result=service.correctProjectHeader(100L,new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor);
        assertEquals("project",result.report().domain());assertEquals(2,result.report().targets().getFirst().targets().size());
        verify(associated).importRecords(eq(100L),argThat(rows->rows.get("project").size()==1
                && rows.entrySet().stream().filter(e->!e.getKey().equals("project")).allMatch(e->e.getValue().isEmpty())),eq(actor));
        verify(evidence).reconcile(any(),any(),eq("project"),anyList(),anyMap(),eq("PROJECT_HEADER_V1"));
        verify(commands).execute(argThat(scope->scope.scopeCode().equals("PROJ:DPPMS:PROJECT_HEADER_CORRECTION")
                && scope.key().endsWith(":V1")),anyString(),eq(ProjectLegacyEntityMigrationService.CorrectionReport.class),any(),any());
        verifyNoInteractions(parties,commerce,assets,sourceHistory,installations,details);
    }
    @Test void staleSnapshotAndFailedOwnerProofCannotCreateCorrectionReceipt() {
        prepareHeaderCorrection();
        assertThrows(RuntimeException.class,()->service.correctProjectHeader(100L,
                new ProjectLegacyEntityMigrationService.Request(10L,20L,"b".repeat(64)),actor));
        verifyNoInteractions(associated,commands,evidence);
        when(associated.importRecords(any(),anyMap(),any())).thenThrow(new IllegalStateException("FORMAL_PROJECT_CHANGED"));
        assertThrows(IllegalStateException.class,()->service.correctProjectHeader(100L,
                new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor));
        verifyNoInteractions(commands,evidence);
    }
    @Test void wrongTargetOrMissingFinalCustomerCannotBeReportedAsCarried() {
        prepareHeaderCorrection();
        when(associated.importRecords(any(),anyMap(),any())).thenReturn(new ProjectAssociatedDataService.ImportResult(List.of(NativeEntityMigrationRow.mapped("project","101",
                new ExternalTargetMapping("PROJ","Project","proj_project",101L,"PROJECT_ENTITY",0))),Map.of()));
        assertThrows(RuntimeException.class,()->service.correctProjectHeader(100L,
                new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),actor));
        verifyNoInteractions(commands,evidence);
    }
    private static ProjectLegacyRecordDO record(String domain,String key,Map<String,String> values) {
        var row=new ProjectLegacyRecordDO();row.setDomainCode(domain);row.setSourceKey(key);
        var full=new LinkedHashMap<String,String>();new LegacyDetailCatalog().require(domain).fields().forEach(f->full.put(f.key(),null));
        full.putAll(values);row.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(full));return row;
    }
}
