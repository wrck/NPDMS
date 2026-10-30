package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.json.databind.NumberSerializer;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.*;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.*;
import cn.iocoder.yudao.module.pms.asset.api.migration.*;
import cn.iocoder.yudao.module.pms.commerce.api.migration.*;
import cn.iocoder.yudao.module.pms.engineering.api.installation.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.controller.admin.legacydetail.ProjectLegacyEntityMigrationController;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.bind.annotation.PostMapping;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectLegacyDetailCorrectionServiceTest {
    private static final String CHECKSUM="a".repeat(64);
    private static final LocalDateTime SOURCE_AT=LocalDateTime.of(2020,1,2,3,4,5,123_000_000);
    private final LegacyDetailCatalog catalog=new LegacyDetailCatalog();
    private final ProjectManualCreationService projects=mock(ProjectManualCreationService.class);
    private final ProjectLegacyDetailMapper mapper=mock(ProjectLegacyDetailMapper.class);
    private final ProjectPartyMapper parties=mock(ProjectPartyMapper.class);
    private final DppmsCommerceEntityResolveApi commerce=mock(DppmsCommerceEntityResolveApi.class);
    private final DppmsAssetEntityImportApi assets=mock(DppmsAssetEntityImportApi.class);
    private final DppmsAssetSourceHistoryApi history=mock(DppmsAssetSourceHistoryApi.class);
    private final DppmsWarrantySourceRecordsApi warranty=mock(DppmsWarrantySourceRecordsApi.class);
    private final DppmsInstallationAddressImportApi installations=mock(DppmsInstallationAddressImportApi.class);
    private final ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    private final PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    private final ProjectAssociatedDataService associated=mock(ProjectAssociatedDataService.class);
    private final DppmsCommerceDetailImportApi details=mock(DppmsCommerceDetailImportApi.class);
    private final DppmsInstallationSourceQueryApi installationSources=mock(DppmsInstallationSourceQueryApi.class);
    private final DeviceQueryApi devices=mock(DeviceQueryApi.class);
    private final ProjectDeviceSelectionApi selection=mock(ProjectDeviceSelectionApi.class);
    private final ProjectLegacyEntityMigrationService service=new ProjectLegacyEntityMigrationService(projects,mapper,catalog,
            parties,commerce,assets,history,warranty,installations,evidence,commands,associated,details,installationSources,devices,selection,new ProjectFactoryRecordImportService(evidence));
    private final ProjectAccessActor actor=new ProjectAccessActor(1L,11L);
    private final List<IdempotencyScope> scopes=new ArrayList<>();
    private final List<SuccessFacts> audits=new ArrayList<>();
    private ProjectLegacySourceDO source;
    private ProjectLegacySnapshotDO snapshot;
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void cleanup(){TenantContextHolder.clear();}

    @Test void wrongTenantAndDeniedManagementStopBeforeSourceReceiptAndOwners() {
        assertFailure("LEGACY_ACTOR_INVALID",()->service.correctCarrierNames(100L,request(),new ProjectAccessActor(2L,11L)));
        doThrow(new IllegalArgumentException("DENIED")).when(projects).getProjectForManage(100L,actor);
        assertFailure("DENIED",()->service.correctInstallationCandidates(100L,installationRequest("500"),actor));
        verifyNoInteractions(mapper,commands,details,installationSources,devices,selection,installations);
    }
    @ParameterizedTest @ValueSource(strings={"head","checksum","counts","nullKey","rowTenant","rowTable","rowSnapshot","duplicateKey"})
    void fullFrozenSourceGuardCannotBeBypassedByNarrowAction(String changed) {
        var pack=packageRow("P1");var records=new ArrayList<>(List.of(pack,logistics("L1","PACK-P1")));
        if(changed.equals("duplicateKey"))records.add(packageRow("P1"));
        prepare(records);
        switch(changed) {
            case "head"->source.setCurrentSnapshotId(21L);
            case "checksum"->snapshot.setChecksum("b".repeat(64));
            case "counts"->snapshot.setDomainCountsJson(JsonUtils.toJsonString(Map.of("shipment-packages",1)));
            case "nullKey"->{
                var values=JsonUtils.parseObject(pack.getPayloadJson(),new TypeReference<Map<String,String>>(){});
                values.remove("emsCompany");pack.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(values));
            }
            case "rowTenant"->pack.setTenantId(2L);
            case "rowTable"->pack.setSourceTable("UNRELATED");
            case "rowSnapshot"->pack.setSnapshotId(21L);
            default->{}
        }
        assertThrows(RuntimeException.class,()->service.correctCarrierNames(100L,request(),actor));
        verifyNoInteractions(commands,details,installationSources,devices,selection,installations);
        noHistoryOrOtherOwnerWrites();
    }
    @Test void carrierCommandOnlyContainsNameAndCodeAndLeavesOtherDomainListsEmpty() {
        prepare(List.of(packageRow("P1"),logistics("L1","PACK-P1"),installation("500")));
        recordCommands();when(details.importDetails(any())).thenReturn(List.of(contractTarget(),carrierTarget("P1",91L)));
        var result=service.correctCarrierNames(100L,request(),actor);
        assertEquals("NEW",result.decision());assertEquals(1,result.report().sourceCount());
        var capture=org.mockito.ArgumentCaptor.forClass(DppmsCommerceDetailImportApi.Command.class);
        verify(details).importDetails(capture.capture());var command=capture.getValue();
        assertTrue(command.orders().isEmpty());assertTrue(command.lines().isEmpty());assertTrue(command.plans().isEmpty());
        assertTrue(command.executions().isEmpty());assertTrue(command.identities().packages().isEmpty());
        assertEquals("C1",command.identities().contractSourceKey());assertEquals("SOURCE-CONTRACT",command.identities().contractNo());
        assertEquals(List.of(new DppmsCommerceDetailImportApi.Package("P1","PACK-P1",null,null,null,null,null,"Carrier Name",null,"2")),command.packages());
        assertEquals("PROJ:DPPMS:CARRIER_CORRECTION",scopes.getFirst().scopeCode());
        assertEquals("DPPMS-CARRIER-CORRECTION:10:20:V1",scopes.getFirst().key());
        verifyNoInteractions(installationSources,devices,selection,installations);
        noHistoryOrOtherOwnerWrites();
    }
    @ParameterizedTest @ValueSource(strings={"missing","duplicate","nullName","wrongContract","empty"})
    void ambiguousLogisticsOrContractCannotBecomeCarrierSuccess(String changed) {
        var records=new ArrayList<ProjectLegacyRecordDO>();if(!changed.equals("empty"))records.add(packageRow("P1"));
        if(!changed.equals("missing"))records.add(logistics("L1","PACK-P1"));
        if(changed.equals("duplicate"))records.add(logistics("L2","PACK-P1"));
        if(changed.equals("nullName"))records.set(1,record("shipment-logistics","L1",Map.of("packId","PACK-P1")));
        prepare(records);if(changed.equals("wrongContract"))source.setSourceContractNo("OTHER-CONTRACT");
        assertThrows(RuntimeException.class,()->service.correctCarrierNames(100L,request(),actor));
        verifyNoInteractions(commands,details);noHistoryOrOtherOwnerWrites();
    }
    @ParameterizedTest @ValueSource(strings={"retained","missing","duplicate","wrongDomain","wrongRole","foreignKey"})
    void carrierOwnerMustReturnEveryUniqueCanonicalPackageAndExactContract(String changed) {
        prepare(List.of(packageRow("P1"),logistics("L1","PACK-P1")));recordCommands();
        var outcomes=new ArrayList<>(List.of(contractTarget(),carrierTarget("P1",91L)));
        switch(changed) {
            case "retained"->outcomes.set(1,NativeEntityMigrationRow.retained("shipment-packages","P1","CONFLICT"));
            case "missing"->outcomes.remove(1);
            case "duplicate"->outcomes.add(carrierTarget("P1",91L));
            case "wrongDomain"->outcomes.set(1,NativeEntityMigrationRow.mapped("software-versions","P1",carrier(91L)));
            case "wrongRole"->outcomes.set(1,NativeEntityMigrationRow.mapped("shipment-packages","P1",new ExternalTargetMapping("COM","ShipmentPackage","com_shipment_package",91L,"SNAPSHOT",0)));
            case "foreignKey"->outcomes.set(1,carrierTarget("OTHER",91L));
        }
        when(details.importDetails(any())).thenReturn(outcomes);
        assertThrows(RuntimeException.class,()->service.correctCarrierNames(100L,request(),actor));
        assertTrue(audits.isEmpty());noHistoryOrOtherOwnerWrites();
    }
    @Test void carrierReplayIsIndependentOfV4AndAnotherOperatorDoesNotCallOwnerAgain() {
        prepare(List.of(packageRow("P1"),logistics("L1","PACK-P1")));recordCommands();
        when(details.importDetails(any())).thenReturn(List.of(contractTarget(),carrierTarget("P1",91L)));
        var first=service.correctCarrierNames(100L,request(),actor);
        var replay=service.correctCarrierNames(100L,request(),new ProjectAccessActor(1L,12L));
        assertEquals("REPLAY_COMPLETED",replay.decision());assertEquals(first.report(),replay.report());
        assertEquals(scopes.get(0),scopes.get(1));assertEquals(0L,scopes.getFirst().actorId());
        assertEquals(1,audits.size());verify(details,times(1)).importDetails(any());noHistoryOrOtherOwnerWrites();
    }
    @Test void withdrawnInstallationCandidatePathCannotInvokeOwnersOrCreateReceipt() {
        assertThrows(IllegalArgumentException.class,()->service.correctInstallationCandidates(100L,installationRequest("500"),actor));
        verifyNoInteractions(mapper,commands,installationSources,selection,devices,installations);
    }
    @ParameterizedTest @ValueSource(strings={"CONFLICT","IN_PROGRESS"})
    void commandConflictCannotCallEitherOwnerOrBecomeSuccess(String decision) {
        prepare(List.of(packageRow("P1"),logistics("L1","PACK-P1"),installation("500")));
        when(commands.execute(any(),anyString(),eq(CorrectionReport.class),any(),any())).thenReturn(new ExecutionResult<>(Decision.valueOf(decision),null));
        assertFailure("DPPMS_CARRIER_CORRECTION_"+decision,()->service.correctCarrierNames(100L,request(),actor));
        assertThrows(IllegalArgumentException.class,()->service.correctInstallationCandidates(100L,installationRequest("500"),actor));
        verifyNoInteractions(details,installationSources,selection,devices,installations);assertTrue(audits.isEmpty());
    }
    @Test void routesKeepFullPermissionsNoPayloadLoggingAndRollbackBoundaries() throws Exception {
        for(var entry:Map.of("correctCarrierNames","/carrier-corrections","correctInstallationCandidates","/installation-candidate-corrections").entrySet()) {
            Class<?> requestType=entry.getKey().equals("correctCarrierNames")?Request.class:InstallationCandidateCorrectionRequest.class;
            var route=ProjectLegacyEntityMigrationController.class.getMethod(entry.getKey(),Long.class,requestType);
            assertArrayEquals(new String[]{entry.getValue()},route.getAnnotation(PostMapping.class).value());
            assertEquals("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update') && @ss.hasPermission('pms:device:assign')",route.getAnnotation(PreAuthorize.class).value());
            assertFalse(route.getAnnotation(ApiAccessLog.class).requestEnable());assertFalse(route.getAnnotation(ApiAccessLog.class).responseEnable());
            var method=ProjectLegacyEntityMigrationService.class.getMethod(entry.getKey(),Long.class,requestType,ProjectAccessActor.class);
            assertTrue(Arrays.asList(method.getAnnotation(Transactional.class).rollbackFor()).contains(Exception.class));
        }
        assertEquals(List.of("sourceId","snapshotId","checksum","sourceKeys"),Arrays.stream(InstallationCandidateCorrectionRequest.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
    }

    private void prepare(List<ProjectLegacyRecordDO> facts) { prepare(facts,10L,20L); }
    private void prepare(List<ProjectLegacyRecordDO> facts,Long sourceId,Long snapshotId) {
        var records=new ArrayList<>(facts);records.add(record("project","101",Map.of()));
        records.add(record("contracts","C1",Map.of("contractNo","SOURCE-CONTRACT")));
        records.forEach(row->row.setSnapshotId(snapshotId));
        source=new ProjectLegacySourceDO();source.setId(sourceId);source.setTenantId(1L);source.setProjectId(100L);
        source.setSourceProjectKey("101");source.setSourceContractNo("SOURCE-CONTRACT");source.setCurrentSnapshotId(snapshotId);
        snapshot=new ProjectLegacySnapshotDO();snapshot.setId(snapshotId);snapshot.setTenantId(1L);snapshot.setSourceId(sourceId);
        snapshot.setChecksum(CHECKSUM);snapshot.setRecordCount(records.size());snapshot.setSourceReadAt(LocalDateTime.of(2026,10,9,8,0));
        var counts=new LinkedHashMap<String,Integer>();catalog.domains().forEach(d->counts.put(d.code(),0));
        records.forEach(row->counts.compute(row.getDomainCode(),(domain,count)->count+1));snapshot.setDomainCountsJson(JsonUtils.toJsonString(counts));
        when(mapper.selectProjectSources(any())).thenReturn(List.of(source));when(mapper.selectSourceForUpdate(any())).thenReturn(source);
        when(mapper.selectSnapshot(any())).thenReturn(snapshot);
        doAnswer(call->{var query=call.getArgument(0,ProjectLegacyDetailMapper.RecordsPageQuery.class);
            return records.stream().filter(row->query.domainCode().equals(row.getDomainCode())).skip(query.offset()).limit(query.limit()).toList();
        }).when(mapper).selectRecords(any());
    }
    private ProjectLegacyRecordDO packageRow(String key){return record("shipment-packages",key,Map.of("packlist_id","PACK-"+key,"emsCompany","2"));}
    private ProjectLegacyRecordDO logistics(String key,String number){return record("shipment-logistics",key,Map.of("packId",number,"emsCompany","Carrier Name"));}
    private ProjectLegacyRecordDO installation(String key){return record("installations",key,Map.of("id",key,"projectId","101","barcode","SN1","installAddress"," SOURCE ADDRESS ","itemCode"," RAW-ITEM ","itemName","","emsCompany","2","contractNo","SOURCE-CONTRACT","packdate","2020-01-02 03:04:05.123"));}
    private ProjectLegacyRecordDO record(String domain,String key,Map<String,String> overrides) {
        var row=new ProjectLegacyRecordDO();row.setId(Math.abs((long)(domain+key).hashCode())+1);row.setTenantId(1L);row.setSnapshotId(20L);
        row.setDomainCode(domain);row.setSourceTable(catalog.require(domain).sourceTable());row.setSourceKey(key);row.setPayloadJson(payload(domain,overrides));return row;
    }
    private String payload(String domain,Map<String,String> overrides) {
        var values=new LinkedHashMap<String,String>();catalog.require(domain).fields().forEach(field->values.put(field.key(),null));
        values.putAll(overrides);return LegacyDetailNormalizer.sourcePayloadJson(values);
    }
    private DppmsInstallationAddressImportApi.Details originalDetails(){return new DppmsInstallationAddressImportApi.Details(" RAW-ITEM ",null,"",null,null,"2",SOURCE_AT,"SOURCE-CONTRACT",null,null,null,null,null,null,null,null,null);}
    private DppmsInstallationSourceQueryApi.SourceRecord sourceFact(String key){return new DppmsInstallationSourceQueryApi.SourceRecord(901L,100L,801L,key,"SN1","101",Long.valueOf(key),101L," SOURCE ADDRESS ",701L,0L,"UNRESOLVED",originalDetails());}
    private DppmsInstallationAddressImportApi.Address expectedAddress(String key){return new DppmsInstallationAddressImportApi.Address(key,801L," SOURCE ADDRESS ",originalDetails(),"101","SN1",Long.valueOf(key),101L);}
    private void readyInstallation(String key) {
        when(installationSources.pageSourceRecords(any())).thenReturn(new PageResult<>(List.of(sourceFact(key)),1L));
        when(selection.validateSelection(anyLong(),anyList())).thenReturn(List.of(new SelectedProjectDevice(801L,"SN1",null,null,null,null)));
        when(devices.getDevice(801L)).thenReturn(device(1L,100L,"SN1"));
        when(installations.importAddresses(any())).thenReturn(List.of(installationTarget(key,601L)));
    }
    private static DeviceSummaryDTO device(Long tenant,Long project,String sn){return new DeviceSummaryDTO(801L,tenant,sn,null,null,null,null,null,null,null,project,0L,null,null,null,null,null,null,null,null,null,null,null,null);}
    private static Request request(){return new Request(10L,20L,CHECKSUM);}
    private static InstallationCandidateCorrectionRequest installationRequest(String...keys){return new InstallationCandidateCorrectionRequest(10L,20L,CHECKSUM,Arrays.asList(keys));}
    private static ExternalTargetMapping carrier(Long id){return new ExternalTargetMapping("COM","ShipmentPackage","com_shipment_package",id,"BUSINESS_ENTITY",0);}
    private static NativeEntityMigrationRow carrierTarget(String key,Long id){return NativeEntityMigrationRow.mapped("shipment-packages",key,carrier(id));}
    private static NativeEntityMigrationRow contractTarget(){return NativeEntityMigrationRow.mapped("contracts","C1",new ExternalTargetMapping("COM","Contract","com_contract",20L,"BUSINESS_ENTITY",0));}
    private static ExternalTargetMapping factTarget(Long id){return new ExternalTargetMapping("IMP","InstallationAddressSourceFact","imp_installation_address_source_fact",id,"INSTALLATION_ADDRESS_SOURCE",0);}
    private static ExternalTargetMapping addressTarget(Long id){return new ExternalTargetMapping("AST","Address","ast_address",id,"ADDRESS",1);}
    private static ExternalTargetMapping historicalTarget(Long id){return new ExternalTargetMapping("IMP","Installation","imp_eng_installation",id,"HISTORICAL_INSTALLATION",2);}
    private static NativeEntityMigrationRow installationTarget(String key,Long id){return NativeEntityMigrationRow.mapped("installations",key,factTarget(901L),addressTarget(701L),historicalTarget(id));}
    private void noHistoryOrOtherOwnerWrites() {
        verifyNoInteractions(parties,commerce,assets,history,warranty,evidence,associated);
        verify(mapper,never()).insertSource(any());verify(mapper,never()).appendSnapshot(any());
        verify(mapper,never()).appendRecord(any());verify(mapper,never()).updateHead(any());
    }
    @SuppressWarnings("unchecked") private void recordCommands() {
        record Stored(String digest,CorrectionReport report){}
        var completed=new HashMap<IdempotencyScope,Stored>();
        when(commands.execute(any(),anyString(),eq(CorrectionReport.class),any(),any())).thenAnswer(call->{
            var scope=call.getArgument(0,IdempotencyScope.class);var digest=call.getArgument(1,String.class);scopes.add(scope);
            var old=completed.get(scope);
            if(old!=null)return new ExecutionResult<>(old.digest().equals(digest)?Decision.REPLAY_COMPLETED:Decision.CONFLICT,old.report());
            var operation=(Supplier<CorrectionReport>)call.getArgument(3);var success=(Function<CorrectionReport,SuccessFacts>)call.getArgument(4);
            var report=operation.get();audits.add(success.apply(report));completed.put(scope,new Stored(digest,report));
            return new ExecutionResult<>(Decision.NEW,report);
        });
    }
    private static void assertFailure(String code,org.junit.jupiter.api.function.Executable action) {
        var failure=assertThrows(RuntimeException.class,action);assertTrue(failure.getMessage().contains(code),failure::getMessage);
    }
}
