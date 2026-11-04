package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetEntityImportApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsWarrantySourceRecordsApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceDetailImportApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationAddressImportApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import cn.iocoder.yudao.module.pms.project.controller.admin.legacydetail.ProjectLegacyEntityMigrationController;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailCatalog;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailNormalizer;
import cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.bind.annotation.PostMapping;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectLegacyFactoryCorrectionServiceTest {
    private static final String CHECKSUM = "a".repeat(64);
    private final LegacyDetailCatalog catalog = new LegacyDetailCatalog();
    private final ProjectManualCreationService projects = mock(ProjectManualCreationService.class);
    private final ProjectLegacyDetailMapper mapper = mock(ProjectLegacyDetailMapper.class);
    private final ProjectPartyMapper parties = mock(ProjectPartyMapper.class);
    private final DppmsCommerceEntityResolveApi commerce = mock(DppmsCommerceEntityResolveApi.class);
    private final DppmsAssetEntityImportApi assets = mock(DppmsAssetEntityImportApi.class);
    private final DppmsAssetSourceHistoryApi history = mock(DppmsAssetSourceHistoryApi.class);
    private final DppmsWarrantySourceRecordsApi warranty = mock(DppmsWarrantySourceRecordsApi.class);
    private final DppmsInstallationAddressImportApi installations = mock(DppmsInstallationAddressImportApi.class);
    private final ProjectLegacyNativeEvidenceService evidence = mock(ProjectLegacyNativeEvidenceService.class);
    private final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    private final ProjectAssociatedDataService associated = mock(ProjectAssociatedDataService.class);
    private final DppmsCommerceDetailImportApi commerceDetails = mock(DppmsCommerceDetailImportApi.class);
    private final ProjectLegacyEntityMigrationService service = new ProjectLegacyEntityMigrationService(projects, mapper,
            catalog, parties, commerce, assets, history, warranty, installations, evidence, commands, associated, commerceDetails, mock(cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationSourceQueryApi.class),
            mock(cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi.class),
            mock(cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi.class),new ProjectFactoryRecordImportService(evidence));
    private final ProjectAccessActor actor = new ProjectAccessActor(1L, 11L);
    private final List<SuccessFacts> audits = new ArrayList<>();
    private final List<IdempotencyScope> scopes = new ArrayList<>();
    private final List<String> digests = new ArrayList<>();
    private ProjectLegacySourceDO source;
    private ProjectLegacySnapshotDO snapshot;

    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void wrongTenantAndInvalidActorCannotReadSourceOrInvokeOwners() {
        assertFailure("LEGACY_ACTOR_INVALID", () -> service.correctFactoryVersions(100L, request(), new ProjectAccessActor(2L, 11L)));
        assertFailure("LEGACY_ACTOR_INVALID", () -> service.correctFactoryVersions(100L, request(), new ProjectAccessActor(1L, 0L)));
        verifyNoInteractions(projects, mapper, commands, assets);
        verifyNoOtherOwnerOrHistoryWrites();
    }

    @Test void deniedProjectManagementStopsBeforeSourceReadAndCommandReceipt() {
        doThrow(new IllegalArgumentException("DENIED")).when(projects).getProjectForManage(100L, actor);
        assertFailure("DENIED", () -> service.correctFactoryVersions(100L, request(), actor));
        verifyNoInteractions(mapper, commands, assets);
        verifyNoOtherOwnerOrHistoryWrites();
    }

    @Test void sourceFromAnotherProjectIsNotAccessible() {
        when(mapper.selectProjectSources(any())).thenReturn(List.of());
        assertFailure("LEGACY_SOURCE_INACCESSIBLE", () -> service.correctFactoryVersions(100L, request(), actor));
        verify(mapper, never()).selectSourceForUpdate(any());
        verifyNoInteractions(commands, assets);
    }

    @ParameterizedTest
    @ValueSource(strings={"sourceTenant", "sourceProject", "sourceId", "snapshotTenant", "snapshotSource"})
    void changedLockedSourceOrForeignSnapshotCannotInvokeOwner(String changedField) {
        prepare(List.of(factory("F1", "SN1")));
        switch (changedField) {
            case "sourceTenant" -> source.setTenantId(2L);
            case "sourceProject" -> source.setProjectId(101L);
            case "sourceId" -> {
                var locked = new ProjectLegacySourceDO();
                locked.setId(12L); locked.setTenantId(1L); locked.setProjectId(100L);
                locked.setSourceProjectKey("SOURCE101"); locked.setCurrentSnapshotId(20L);
                snapshot.setSourceId(12L); when(mapper.selectSourceForUpdate(any())).thenReturn(locked);
            }
            case "snapshotTenant" -> snapshot.setTenantId(2L);
            case "snapshotSource" -> snapshot.setSourceId(12L);
            default -> fail("Unknown test case");
        }
        assertThrows(RuntimeException.class, () -> service.correctFactoryVersions(100L, request(), actor));
        verifyNoInteractions(commands, assets);
        verifyNoOtherOwnerOrHistoryWrites();
    }

    @Test void staleHeadAndChecksumMismatchAreRejectedBeforeCommandOrOwner() {
        prepare(List.of(factory("F1", "SN1")));
        source.setCurrentSnapshotId(21L);
        assertFailure("DPPMS_NATIVE_HEAD_CHANGED", () -> service.correctFactoryVersions(100L, request(), actor));
        source.setCurrentSnapshotId(20L);
        assertFailure("DPPMS_NATIVE_CHECKSUM_CONFLICT", () -> service.correctFactoryVersions(100L,
                new Request(10L,20L,"b".repeat(64)), actor));
        verifyNoInteractions(commands, assets);
    }

    @Test void incompleteDomainCoverageIsNotAcceptedForFactoryCorrection() {
        prepare(List.of(factory("F1", "SN1")));
        snapshot.setDomainCountsJson(JsonUtils.toJsonString(Map.of("factory-versions",1)));
        assertFailure("DPPMS_NATIVE_FULL_SOURCE_REQUIRED", () -> service.correctFactoryVersions(100L, request(), actor));
        verifyNoInteractions(commands, assets);
    }

    @Test void omittedOriginalNullFieldIsRejectedBeforeAnyOwner() {
        var factory = factory("F1", "SN1"); prepare(List.of(factory));
        var fields = JsonUtils.parseObject(factory.getPayloadJson(),new TypeReference<Map<String,String>>(){});
        fields.remove("pcb"); factory.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(fields));
        assertFailure("DPPMS_NATIVE_FULL_SOURCE_REQUIRED", () -> service.correctFactoryVersions(100L, request(), actor));
        verifyNoInteractions(commands, assets);
    }

    @ParameterizedTest
    @ValueSource(strings={"rowTenant", "rowSnapshot", "rowTable", "serialNumber"})
    void foreignFactoryRecordAndMissingDeviceIdentityAreRejected(String changedField) {
        var row = factory("F1", "SN1"); prepare(List.of(row));
        switch (changedField) {
            case "rowTenant" -> row.setTenantId(2L);
            case "rowSnapshot" -> row.setSnapshotId(21L);
            case "rowTable" -> row.setSourceTable("another_table");
            case "serialNumber" -> {
                var fields = JsonUtils.parseObject(row.getPayloadJson(),new TypeReference<Map<String,String>>(){});
                fields.put("serial_number",null); row.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(fields));
            }
            default -> fail("Unknown test case");
        }
        assertFailure("DPPMS_FACTORY_CORRECTION_SOURCE_IDENTITY_INVALID", () -> service.correctFactoryVersions(100L, request(), actor));
        verifyNoInteractions(commands, assets);
    }

    @Test void emptyFactoryProjectDoesNotCreateReceiptOrRunOtherDomains() {
        prepare(List.of());
        assertFailure("DPPMS_FACTORY_CORRECTION_EMPTY_SOURCE", () -> service.correctFactoryVersions(100L, request(), actor));
        verifyNoInteractions(commands, assets);
        verifyNoOtherOwnerOrHistoryWrites();
    }

    @Test void onlyFrozenFiveOriginalFieldsReachFactoryOwnerAndOriginalNullIsPreserved() {
        var factory = factory("F1", "SN1");
        prepare(List.of(factory, record("shipment-facts","UNUSED",Map.of())));
        recordCommands(); when(assets.importEntities(any())).thenReturn(List.of(mapped("F1",801L)));
        var result = service.correctFactoryVersions(100L, request(), actor);
        assertEquals("NEW", result.decision());
        assertEquals(1, result.report().sourceCount());
        assertEquals(List.of(new FactoryCorrectionTarget("F1",target(801L))), result.report().targets());
        var capture = org.mockito.ArgumentCaptor.forClass(DppmsAssetEntityImportApi.Command.class);
        verify(assets).importEntities(capture.capture());
        var command = capture.getValue();
        assertEquals(1L,command.tenantId()); assertEquals(11L,command.actorId()); assertEquals(100L,command.projectId());
        assertEquals("SOURCE-CONTRACT",command.contractNo()); assertEquals(20L,command.snapshotId());
        assertEquals(CHECKSUM,command.sourceVersion()); assertEquals(snapshot.getSourceReadAt(),command.extractedAt());
        assertTrue(command.shipments().isEmpty()); assertTrue(command.software().isEmpty()); assertTrue(command.warranties().isEmpty());
        assertEquals(List.of(new DppmsAssetEntityImportApi.FactoryVersion("F1","SN1"," CONP-RAW ","BOOT-RAW",null,null)),
                command.factoryVersions());
        assertEquals(List.of("sourceId","snapshotId","checksum","softwareHistoryDefinitionId"),Arrays.stream(Request.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
        assertEquals("PROJ:DPPMS:FACTORY_CORRECTION",scopes.getFirst().scopeCode());
        assertEquals("DPPMS-FACTORY-CORRECTION:10:20:V1",scopes.getFirst().key());
        assertEquals("PROJ_DPPMS_FACTORY_CORRECT",audits.getFirst().operationCode());
        var detail = JsonUtils.parseObject(audits.getFirst().detailSnapshot(),new TypeReference<Map<String,Object>>(){});
        assertEquals("11",detail.get("operatorUserId").toString());
        verifyNoOtherOwnerOrHistoryWrites();
    }

    @Test void differentAuthorizedOperatorReplaysIndependentReceiptWithoutOwnerCallOrSecondAudit() {
        prepare(List.of(factory("F1","SN1"))); recordCommands();
        when(assets.importEntities(any())).thenReturn(List.of(mapped("F1",801L)));
        var first = service.correctFactoryVersions(100L,request(),actor);
        var second = service.correctFactoryVersions(100L,request(),new ProjectAccessActor(1L,12L));
        assertEquals("NEW",first.decision()); assertEquals("REPLAY_COMPLETED",second.decision());
        assertEquals(first.report(),second.report());
        assertEquals(scopes.get(0),scopes.get(1)); assertEquals(0L,scopes.getFirst().actorId());
        assertEquals(digests.get(0),digests.get(1));
        assertEquals(1,audits.size()); verify(assets,times(1)).importEntities(any());
        verify(projects).getProjectForManage(100L,new ProjectAccessActor(1L,12L));
        verifyNoOtherOwnerOrHistoryWrites();
    }

    @ParameterizedTest
    @ValueSource(strings={"CONFLICT","IN_PROGRESS"})
    void commandConflictOrConcurrentExecutionCannotReportFactorySuccess(String decision) {
        prepare(List.of(factory("F1","SN1")));
        when(commands.execute(any(),anyString(),eq(FactoryCorrectionReport.class),any(),any()))
                .thenReturn(new ExecutionResult<>(Decision.valueOf(decision),null));
        assertFailure("DPPMS_FACTORY_CORRECTION_"+decision,() -> service.correctFactoryVersions(100L,request(),actor));
        verifyNoInteractions(assets); assertTrue(audits.isEmpty());
    }

    @Test void retainedRowAfterMappedRowBlocksWholeOperationBeforeSuccessReceipt() {
        prepare(List.of(factory("F1","SN1"),factory("F2","SN2"))); recordCommands();
        when(assets.importEntities(any())).thenReturn(List.of(mapped("F1",801L),
                NativeEntityMigrationRow.retained("factory-versions","F2","DEVICE_NOT_RECONCILED")));
        var transactions = mock(PlatformTransactionManager.class); var transaction = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(transaction);
        var proxied = transactionalService(transactions);
        assertFailure("DPPMS_FACTORY_CORRECTION_BLOCKED:DEVICE_NOT_RECONCILED",() -> proxied.correctFactoryVersions(100L,request(),actor));
        verify(transactions).rollback(transaction); verify(transactions,never()).commit(any());
        assertTrue(audits.isEmpty()); verify(assets).importEntities(any());
        verifyNoOtherOwnerOrHistoryWrites();
    }

    @ParameterizedTest
    @MethodSource("invalidOwnerResults")
    void ownerCannotClaimSuccessUsingWrongSourceOrNonCanonicalFactoryTarget(List<NativeEntityMigrationRow> outcomes) {
        prepare(List.of(factory("F1","SN1"))); recordCommands();
        when(assets.importEntities(any())).thenReturn(outcomes);
        assertFailure("DPPMS_FACTORY_CORRECTION_OWNER_RESULT_INVALID",() -> service.correctFactoryVersions(100L,request(),actor));
        assertTrue(audits.isEmpty()); verifyNoOtherOwnerOrHistoryWrites();
    }

    @Test void duplicateCanonicalTargetIdentityAcrossSourcesBlocksWholeOperation() {
        prepare(List.of(factory("F1","SN1"),factory("F2","SN2"))); recordCommands();
        when(assets.importEntities(any())).thenReturn(List.of(mapped("F1",801L),mapped("F2",801L)));
        assertFailure("DPPMS_FACTORY_CORRECTION_OWNER_RESULT_INVALID",() -> service.correctFactoryVersions(100L,request(),actor));
        assertTrue(audits.isEmpty());
    }

    @Test void duplicateSourceKeyOrDeviceIdentityFailsBeforeOwner() {
        prepare(List.of(factory("F1","SN1"),factory("F1","SN2")));
        assertFailure("DPPMS_FACTORY_CORRECTION_SOURCE_IDENTITY_INVALID",() -> service.correctFactoryVersions(100L,request(),actor));
        prepare(List.of(factory("F1","SN1"),factory("F2","SN1")));
        assertFailure("DPPMS_FACTORY_CORRECTION_SOURCE_IDENTITY_INVALID",() -> service.correctFactoryVersions(100L,request(),actor));
        verifyNoInteractions(commands,assets);
    }

    @Test void ownerFailurePropagatesWithoutCompletedReceiptAndEntryRequiresRollbackForException() throws Exception {
        prepare(List.of(factory("F1","SN1"))); recordCommands();
        var ownerFailure = new IllegalStateException("OWNER_WRITE_FAILED");
        when(assets.importEntities(any())).thenThrow(ownerFailure);
        var transactions = mock(PlatformTransactionManager.class); var transaction = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(transaction);
        var proxied = transactionalService(transactions);
        assertSame(ownerFailure,assertThrows(IllegalStateException.class,() -> proxied.correctFactoryVersions(100L,request(),actor)));
        verify(transactions).rollback(transaction); verify(transactions,never()).commit(any());
        assertTrue(audits.isEmpty());
        var transactionAttribute = ProjectLegacyEntityMigrationService.class.getMethod("correctFactoryVersions",Long.class,Request.class,ProjectAccessActor.class)
                .getAnnotation(Transactional.class);
        assertNotNull(transactionAttribute); assertTrue(Arrays.asList(transactionAttribute.rollbackFor()).contains(Exception.class));
        verifyNoOtherOwnerOrHistoryWrites();
    }

    @Test void correctionHttpRouteHasAllThreePermissionsAndSuppressesPayloadLogging() throws Exception {
        var method = ProjectLegacyEntityMigrationController.class.getMethod("correctFactoryVersions",Long.class,Request.class);
        assertArrayEquals(new String[]{"/factory-corrections"},method.getAnnotation(PostMapping.class).value());
        assertEquals("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update') && @ss.hasPermission('pms:device:assign')",
                method.getAnnotation(PreAuthorize.class).value());
        var accessLog = method.getAnnotation(ApiAccessLog.class);
        assertFalse(accessLog.requestEnable()); assertFalse(accessLog.responseEnable());
    }

    private void prepare(List<ProjectLegacyRecordDO> facts) {
        var records = new ArrayList<>(facts);
        records.add(record("project","SOURCE101",Map.of()));
        source = new ProjectLegacySourceDO(); source.setId(10L); source.setTenantId(1L); source.setProjectId(100L);
        source.setSourceProjectKey("SOURCE101"); source.setSourceContractNo("SOURCE-CONTRACT"); source.setCurrentSnapshotId(20L);
        snapshot = new ProjectLegacySnapshotDO(); snapshot.setId(20L); snapshot.setTenantId(1L); snapshot.setSourceId(10L);
        snapshot.setChecksum(CHECKSUM); snapshot.setRecordCount(records.size()); snapshot.setSourceReadAt(LocalDateTime.of(2026,10,9,8,0));
        var counts = new LinkedHashMap<String,Integer>(); catalog.domains().forEach(d -> counts.put(d.code(),0));
        records.forEach(row -> counts.compute(row.getDomainCode(),(domain,count) -> count+1));
        snapshot.setDomainCountsJson(JsonUtils.toJsonString(counts));
        when(mapper.selectProjectSources(any())).thenReturn(List.of(source));
        when(mapper.selectSourceForUpdate(any())).thenReturn(source); when(mapper.selectSnapshot(any())).thenReturn(snapshot);
        doAnswer(call -> {
            var query = call.getArgument(0,ProjectLegacyDetailMapper.RecordsPageQuery.class);
            return records.stream().filter(row -> query.domainCode().equals(row.getDomainCode()))
                    .skip(query.offset()).limit(query.limit()).toList();
        }).when(mapper).selectRecords(any());
    }

    private ProjectLegacyRecordDO factory(String key,String serialNumber) {
        return record("factory-versions",key,Map.of("serial_number",serialNumber,"conp"," CONP-RAW ","boot","BOOT-RAW"));
    }

    private ProjectLegacyRecordDO record(String domain,String key,Map<String,String> overrides) {
        var row = new ProjectLegacyRecordDO(); row.setId((long)key.hashCode()); row.setTenantId(1L); row.setSnapshotId(20L);
        row.setDomainCode(domain); row.setSourceTable(catalog.require(domain).sourceTable()); row.setSourceKey(key);
        var fields = new LinkedHashMap<String,String>(); catalog.require(domain).fields().forEach(field -> fields.put(field.key(),null));
        fields.putAll(overrides); row.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(fields)); return row;
    }

    @SuppressWarnings("unchecked")
    private void recordCommands() {
        record Stored(String digest,FactoryCorrectionReport report) {}
        var completed = new HashMap<IdempotencyScope,Stored>();
        when(commands.execute(any(),anyString(),eq(FactoryCorrectionReport.class),any(),any())).thenAnswer(call -> {
            var scope = call.getArgument(0,IdempotencyScope.class); String digest = call.getArgument(1,String.class);
            scopes.add(scope); digests.add(digest);
            var previous = completed.get(scope);
            if (previous != null) return new ExecutionResult<>(previous.digest().equals(digest) ? Decision.REPLAY_COMPLETED : Decision.CONFLICT,
                    previous.digest().equals(digest) ? previous.report() : null);
            var operation = (Supplier<FactoryCorrectionReport>)call.getArgument(3);
            var success = (Function<FactoryCorrectionReport,SuccessFacts>)call.getArgument(4);
            var report = operation.get();
            audits.add(success.apply(report)); completed.put(scope,new Stored(digest,report));
            return new ExecutionResult<>(Decision.NEW,report);
        });
    }

    private ProjectLegacyEntityMigrationService transactionalService(PlatformTransactionManager transactions) {
        var proxy = new ProxyFactory(service); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        return (ProjectLegacyEntityMigrationService)proxy.getProxy();
    }

    private void verifyNoOtherOwnerOrHistoryWrites() {
        verifyNoInteractions(parties,commerce,history,warranty,installations,evidence,associated,commerceDetails);
        verify(mapper,never()).insertSource(any()); verify(mapper,never()).appendSnapshot(any());
        verify(mapper,never()).appendRecord(any()); verify(mapper,never()).updateHead(any());
    }

    private static Request request() { return new Request(10L,20L,CHECKSUM); }
    private static ExternalTargetMapping target(Long id) {
        return new ExternalTargetMapping("AST","DeviceFactoryVersion","ast_device_factory_version",id,"FACTORY_VERSION",0);
    }
    private static NativeEntityMigrationRow mapped(String key,Long id) {
        return NativeEntityMigrationRow.mapped("factory-versions",key,target(id));
    }
    private static void assertFailure(String code,org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(RuntimeException.class,action); assertTrue(failure.getMessage().contains(code),failure::getMessage);
    }
    private static Stream<List<NativeEntityMigrationRow>> invalidOwnerResults() {
        return Stream.of(
                List.of(), Arrays.asList((NativeEntityMigrationRow)null),
                List.of(NativeEntityMigrationRow.mapped("software-versions","F1",target(801L))),
                List.of(mapped("FOREIGN",801L)),
                List.of(NativeEntityMigrationRow.mapped("factory-versions","F1",new ExternalTargetMapping("COM","DeviceFactoryVersion","ast_device_factory_version",801L,"FACTORY_VERSION",0))),
                List.of(NativeEntityMigrationRow.mapped("factory-versions","F1",new ExternalTargetMapping("AST","DeviceFactoryVersionRecord","ast_device_factory_version",801L,"FACTORY_VERSION",0))),
                List.of(NativeEntityMigrationRow.mapped("factory-versions","F1",new ExternalTargetMapping("AST","DeviceFactoryVersion","ast_device_factory_version_record",801L,"FACTORY_VERSION",0))),
                List.of(NativeEntityMigrationRow.mapped("factory-versions","F1",new ExternalTargetMapping("AST","DeviceFactoryVersion","ast_device_factory_version",801L,"HISTORY",0))),
                List.of(NativeEntityMigrationRow.mapped("factory-versions","F1",new ExternalTargetMapping("AST","DeviceFactoryVersion","ast_device_factory_version",801L,"FACTORY_VERSION",1))),
                List.of(NativeEntityMigrationRow.mapped("factory-versions","F1",target(801L),target(802L)))
        );
    }
}
