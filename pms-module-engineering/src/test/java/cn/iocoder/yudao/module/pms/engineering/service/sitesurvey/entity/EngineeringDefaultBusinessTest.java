package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.business.ArrivalBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.business.InstallationBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.business.ConfigurationBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.business.JointTestBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.business.ArrivalBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.business.InstallationBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.business.ConfigurationBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.business.JointTestBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.service.arrival.business.ArrivalBusinessService;
import cn.iocoder.yudao.module.pms.engineering.service.installation.business.InstallationBusinessService;
import cn.iocoder.yudao.module.pms.engineering.service.configuration.business.ConfigurationBusinessService;
import cn.iocoder.yudao.module.pms.engineering.service.jointtest.business.JointTestBusinessService;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.business.TrainingBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.business.TrainingBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.service.training.TrainingBusinessService;
import cn.iocoder.yudao.module.pms.engineering.service.solution.business.SolutionBusinessService;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.business.SolutionBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.business.SolutionBusinessMapper;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.ProjectBusinessScopeAccess;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Fresh H2 data, actual inherited transactions/CRUD/ledger. Native external ports are test doubles. */
@SuppressWarnings({"rawtypes", "unchecked"})
class EngineeringDefaultBusinessTest {
    private final SiteSurveySpringPersistenceTest fixture = new SiteSurveySpringPersistenceTest();
    private final Map<String, DefaultProjectBusinessService> businesses = new LinkedHashMap<>();

    @BeforeEach void open() throws Exception {
        assertFalse(SiteSurveySpringPersistenceTest.mysqlOptIn(), "This test only owns a fresh in-memory database");
        fixture.open();
        var ctx = fixture.ctx;
        var callers = ctx.getBean(BusinessCallerContext.class);
        var scopes = ctx.getBean(ProjectScopeApi.class);
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of(20L));
        ctx.getBeanFactory().registerSingleton("engineeringDefaults", new BusinessDefaults(callers,
                new BusinessPermissions(ctx.getBean(PermissionApi.class), callers), new ProjectBusinessScopeAccess(scopes),
                ctx.getBean(jakarta.validation.Validator.class), new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class)),
                ctx.getBean(OperationExecutionStore.class), ctx.getBean(OperationAuditApi.class), ctx.getBean(BusinessEventPort.class),
                List.of(), () -> mock(DefaultBusinessDeliveryApi.class)));
        var sf = ctx.getBean(SqlSessionFactory.class);
        var sessions = new SqlSessionTemplate(sf);
        for (var type : List.of(ArrivalBusinessDO.class, InstallationBusinessDO.class, ConfigurationBusinessDO.class, JointTestBusinessDO.class, TrainingBusinessDO.class, SolutionBusinessDO.class))
            SiteSurveySpringPersistenceTest.Config.schema(fixture.jdbc, type);
        for (var type : List.of(ArrivalBusinessMapper.class, InstallationBusinessMapper.class, ConfigurationBusinessMapper.class, JointTestBusinessMapper.class, TrainingBusinessMapper.class, SolutionBusinessMapper.class)) {
            sf.getConfiguration().addMapper(type);
            ctx.getBeanFactory().registerSingleton(type.getSimpleName(), sessions.getMapper(type));
        }
        for (var name : List.of("arrival/ArrivalMapper", "installation/InstallationMapper", "configuration/ConfigurationMapper", "jointtest/JointTestMapper", "training/TrainingMapper", "solution/SolutionMapper")) {
            String xml = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/mapper/" + name + ".xml")).replace("b'0'", "0").replace("b'1'", "1");
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(new java.io.StringReader(xml), sf.getConfiguration(), name, sf.getConfiguration().getSqlFragments()).parse();
            Class type = Class.forName("cn.iocoder.yudao.module.pms.engineering.dal.mysql." + name.replace('/', '.'));
            ctx.getBeanFactory().registerSingleton(type.getSimpleName(), sessions.getMapper(type));
        }
        registerMock(cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi.class);
        var installationAddresses=registerMock(cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationAddressCompletionApi.class);
        when(installationAddresses.lockAndInspect(any())).thenReturn(new cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationAddressCompletionApi.Fact(1,1));
        registerMock(cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi.class);
        registerMock(cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigLogRecordApi.class);
        registerMock(cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigurationLogImportApi.class);
        registerMock(cn.iocoder.yudao.module.pms.engineering.service.training.TrainingService.class);
        registerMock(cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionTieredReviewService.class);
        registerMock(cn.iocoder.yudao.module.pms.engineering.service.solution.SolutionCustomerDocumentService.class);
        registerMock(cn.iocoder.yudao.module.pms.engineering.service.training.TrainingPrintService.class);
        var users = registerMock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
        when(users.getUser(anyLong())).thenReturn(new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO().setId(9L).setNickname("培训工程师"));
        registerMock(cn.iocoder.yudao.module.pms.engineering.service.arrival.ArrivalDeliveryRegistration.class);
        var attachments = registerMock(cn.iocoder.yudao.module.pms.engineering.service.attachment.NativeAttachmentRegistration.class);
        when(attachments.register(any(), any())).thenReturn(List.of());
        doCallRealMethod().when(attachments).requireLegacyUnchanged(any(), any());
        ctx.registerBean(cn.iocoder.yudao.module.pms.engineering.service.arrival.ArrivalServiceImpl.class);
        ctx.registerBean(cn.iocoder.yudao.module.pms.engineering.service.installation.InstallationServiceImpl.class);
        ctx.registerBean(cn.iocoder.yudao.module.pms.engineering.service.configuration.ConfigurationServiceImpl.class);
        ctx.registerBean(cn.iocoder.yudao.module.pms.engineering.service.jointtest.JointTestServiceImpl.class);
        for (var type : List.of(ArrivalBusinessService.class, InstallationBusinessService.class, ConfigurationBusinessService.class, JointTestBusinessService.class, TrainingBusinessService.class, SolutionBusinessService.class)) {
            ctx.registerBean(type);
            var service = ctx.getBean(type);
            businesses.put(service.entityType(), service);
        }
    }

    @AfterEach void close() { fixture.close(); }

    private <T> T registerMock(Class<T> type) {
        T value = mock(type, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
        fixture.ctx.getBeanFactory().registerSingleton(type.getSimpleName(), value);
        return value;
    }

    private Map<String, Object> values(String kind) {
        var values = new LinkedHashMap<String, Object>();
        values.put("projectId", 20L);
        if (!Set.of("training","solution").contains(kind)) values.put("equipmentId", 30L);
        values.put("remark", "original");
        if (kind.equals("arrival")) values.put("arrivalTime", "2026-10-12T10:00:00");
        if (kind.equals("installation")) values.put("installLocation", "机房 A");
        if (kind.equals("jointTest")) values.put("testCase", "链路连通");
        if (kind.equals("training")) { values.put("name", "现场培训"); values.put("trainingTypes", "PRODUCT_OPS"); values.put("trainingTime", "2026-10-12"); }
        if (kind.equals("solution")) values.put("name", "实施方案");
        return values;
    }

    @Test void terminalRecordsRemainReadableButRejectInheritedSaveAndDelete() {
        assertAll(businesses.entrySet().stream().map(entry -> (org.junit.jupiter.api.function.Executable) () -> {
            var kind = entry.getKey(); var service = entry.getValue();
            var receipt = service.createForm(values(kind), kind + "-create");
            long id = receipt.entityRef().entityId();
            var table = Map.of("arrival", "imp_eng_arrival", "installation", "imp_eng_installation", "configuration", "imp_eng_configuration", "jointTest", "imp_eng_joint_test", "training", "imp_eng_training", "solution", "sol_eng_solution").get(kind);
            // Existing persisted terminal state is the boundary under review; no lifecycle is simulated here.
            fixture.jdbc.update("UPDATE " + table + " SET status=? WHERE id=?", kind.equals("arrival") ? 1 : 2, id);
            assertNotNull(service.get(id));
            assertThrows(RuntimeException.class, () -> service.updateForm(id, Map.of("remark", "forbidden"), receipt.newConcurrencyBasis(), kind + "-edit"));
            assertThrows(RuntimeException.class, () -> service.delete(id, receipt.newConcurrencyBasis(), kind + "-delete"));
            assertEquals("original", fixture.jdbc.queryForObject("SELECT remark FROM " + table + " WHERE id=?", String.class, id));
        }));
    }

    @Test void ordinaryCrudRetainsConcurrencyAuthorizationAndFieldWhitelist() {
        for (var entry : businesses.entrySet()) {
            var service = entry.getValue(); var kind = entry.getKey();
            var first = service.createForm(values(kind), kind + "-create");
            long id = first.entityRef().entityId();
            assertEquals(first, service.createForm(values(kind), kind + "-create"));
            assertThrows(BusinessContractException.class, () -> service.input(Map.of("status", 2)));
            assertThrows(BusinessContractException.class, () -> service.input(Map.of("tenantId", 2)));
            var saved = service.updateForm(id, Map.of("remark", "updated"), first.newConcurrencyBasis(), kind + "-save");
            assertThrows(BusinessContractException.class, () -> service.updateForm(id, Map.of("remark", "stale"), first.newConcurrencyBasis(), kind + "-stale"));
            when(fixture.ctx.getBean(PermissionApi.class).hasAnyPermissions(eq(9L), any(String[].class))).thenReturn(false);
            assertThrows(BusinessContractException.class, () -> service.updateForm(id, Map.of("remark", "denied"), saved.newConcurrencyBasis(), kind + "-denied"));
            when(fixture.ctx.getBean(PermissionApi.class).hasAnyPermissions(eq(9L), any(String[].class))).thenReturn(true);
            assertEquals(saved, service.receipt("save", kind + "-save"));
        }
    }

    @Test void signOnSaveReturnsTheActualPersistedBasisAndReplaysOnlyOnce() {
        var service = (ArrivalBusinessService) businesses.get("arrival");
        var input = values("arrival"); input.put("attachmentUrl", "existing-arrival-document");
        var created = service.createForm(input, "signed-create");
        var row = service.get(created.entityRef().entityId());
        assertEquals(1, row.getStatus());
        assertEquals(row.getVersion(), created.newConcurrencyBasis());
        assertEquals(created, service.createForm(input, "signed-create"));
        verify(fixture.ctx.getBean(cn.iocoder.yudao.module.pms.engineering.service.arrival.ArrivalDeliveryRegistration.class), times(1)).registerSigned(any());
        var pending = service.createForm(values("arrival"), "pending-create");
        var saved = service.updateForm(pending.entityRef().entityId(), Map.of("attachmentUrl", "existing-sign-document"), pending.newConcurrencyBasis(), "sign-on-update");
        assertEquals(service.get(pending.entityRef().entityId()).getVersion(), saved.newConcurrencyBasis());
        assertEquals(saved, service.updateForm(pending.entityRef().entityId(), Map.of("attachmentUrl", "existing-sign-document"), pending.newConcurrencyBasis(), "sign-on-update"));
    }

    @Test void importedConfigurationCannotBeRewrittenOrDeletedThroughDefaultCrud() {
        var service = (ConfigurationBusinessService) businesses.get("configuration");
        var created = service.createForm(values("configuration"), "imported-create");
        long id = created.entityRef().entityId();
        fixture.jdbc.update("UPDATE imp_eng_configuration SET imported_log_file_id=71, imported_log_batch_id=72 WHERE id=?", id);
        assertThrows(BusinessContractException.class, () -> service.input(Map.of("importedLogFileId", 99)));
        assertThrows(BusinessContractException.class, () -> service.updateForm(id, Map.of("remark", "replace source"), created.newConcurrencyBasis(), "imported-edit"));
        assertThrows(BusinessContractException.class, () -> service.delete(id, created.newConcurrencyBasis(), "imported-delete"));
        assertEquals(71L, service.get(id).getImportedLogFileId());
    }

    private cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Observation observation(String kind,long id) {
        var service=businesses.get(kind);
        return service.runtimeObservation(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Query(
                1L,20L,service.runtimeDefinition().type(),id),false);
    }

    @Test void templateCompletionDistinguishesSavedDraftsFromNativeTerminalOutcomes() {
        var codes=Map.of("arrival","ARRIVAL_SIGNED","configuration","CONFIGURATION_COMPLETED","jointTest","JOINT_TEST_PASSED","training","TRAINING_CONFIRMED");
        var tables=Map.of("arrival","imp_eng_arrival","configuration","imp_eng_configuration","jointTest","imp_eng_joint_test","training","imp_eng_training");
        for(var kind:codes.keySet()) {
            var service=businesses.get(kind);long id=service.createForm(values(kind),kind+"-facts").entityRef().entityId();
            assertTrue(service.runtimeDefinition().factLabels().containsKey(codes.get(kind)),kind);
            assertEquals(false,observation(kind,id).facts().get(codes.get(kind)),kind+" draft");
            int completed=kind.equals("arrival")?1:2;
            fixture.jdbc.update("UPDATE "+tables.get(kind)+" SET status=? WHERE id=?",completed,id);
            assertEquals(true,observation(kind,id).facts().get(codes.get(kind)),kind+" completed");
            fixture.jdbc.update("UPDATE "+tables.get(kind)+" SET status=? WHERE id=?",kind.equals("arrival")?2:3,id);
            assertEquals(false,observation(kind,id).facts().get(codes.get(kind)),kind+" abnormal or void");
        }
    }

    @Test void nativeInstallationCompletionAndJointFailureKeepTheirOriginalRules() {
        var installation = (InstallationBusinessService) businesses.get("installation");
        var created = installation.createForm(values("installation"), "install-create");
        long id = created.entityRef().entityId();
        assertThrows(RuntimeException.class, () -> installation.complete(id, created.newConcurrencyBasis(), "complete-before-start"));
        var started = installation.start(id, created.newConcurrencyBasis(), "install-start");
        var completed = installation.complete(id, started.newConcurrencyBasis(), "install-complete");
        assertEquals(2, installation.get(id).getStatus());
        assertNotNull(installation.get(id).getEffectiveFrom());
        assertEquals(completed, installation.complete(id, started.newConcurrencyBasis(), "install-complete"));
        verify(fixture.ctx.getBean(cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi.class), times(1)).effectEquipmentLocation(any());
        assertEquals(true,observation("installation",id).facts().get("INSTALLATION_COMPLETED"));
        var addresses=fixture.ctx.getBean(cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationAddressCompletionApi.class);
        when(addresses.lockAndInspect(any())).thenReturn(new cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationAddressCompletionApi.Fact(3,2));
        assertEquals(false,observation("installation",id).facts().get("INSTALLATION_COMPLETED"));
        assertFalse(observation("installation",id).handlingCompleted());

        var joint = (JointTestBusinessService) businesses.get("jointTest");
        var draft = joint.createForm(values("jointTest"), "joint-create");
        long jointId = draft.entityRef().entityId();
        var testing = joint.start(jointId, draft.newConcurrencyBasis(), "joint-start");
        assertThrows(RuntimeException.class, () -> joint.fail(jointId, testing.newConcurrencyBasis(), "empty-failure", " "));
        var failed = joint.fail(jointId, testing.newConcurrencyBasis(), "joint-fail", "链路中断");
        assertEquals(3, joint.get(jointId).getStatus()); assertEquals("链路中断", joint.get(jointId).getExceptionRecord());
        assertThrows(RuntimeException.class, () -> joint.updateForm(jointId, Map.of("result", "静默通过"), failed.newConcurrencyBasis(), "rewrite-failure"));
        assertThrows(RuntimeException.class, () -> joint.pass(jointId, failed.newConcurrencyBasis(), "pass-after-failure"));
    }

    @Test void installationBatchUsesRealRowsAndReplaysTheWholeAtomicResult() {
        var service=(InstallationBusinessService)businesses.get("installation");
        var values=values("installation");values.remove("equipmentId");
        var first=service.createBatch(values,List.of(30L,31L),"installation-batch-create");
        assertEquals(2,first.ids().size());assertEquals(2,new HashSet<>(first.ids()).size());
        for(var id:first.ids()) {
            var saved=service.get(id);
            assertEquals(2,saved.getStatus());assertEquals(9L,saved.getInstallerUserId());
            assertNotNull(saved.getEffectiveFrom());assertEquals("original",saved.getRemark());
        }
        assertEquals(service.get(first.ids().getFirst()).getVersion(),first.receipt().newConcurrencyBasis());
        assertEquals(first,service.createBatch(values,List.of(30L,31L),"installation-batch-create"));
        assertThrows(BusinessContractException.class,()->service.createBatch(values,List.of(30L,32L),"installation-batch-create"));
        assertEquals(2,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));
        verify(fixture.ctx.getBean(cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi.class),times(2)).effectEquipmentLocation(any());
    }

    @Test void failedSecondInstallationRollsBackEarlierRowsAndBatchReceipts() {
        var service=(InstallationBusinessService)businesses.get("installation");
        var port=fixture.ctx.getBean(cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi.class);
        doNothing().doThrow(new IllegalStateException("second device effect failed")).when(port).effectEquipmentLocation(any());
        var values=values("installation");values.remove("equipmentId");
        assertThrows(RuntimeException.class,()->service.createBatch(values,List.of(30L,31L),"installation-batch-failed"));
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));
        assertNull(service.receipt("create","installation-batch-failed"));
        reset(port);
        assertEquals(2,service.createBatch(values,List.of(30L,31L),"installation-batch-failed").ids().size());
        assertEquals(2,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));
    }

    @Test void installationBatchRejectsDuplicateDevicesAndUnauthorizedProjectsBeforeCreation() {
        var service=(InstallationBusinessService)businesses.get("installation");
        var values=values("installation");values.remove("equipmentId");
        assertThrows(BusinessContractException.class,()->service.createBatch(values,List.of(30L,30L),"installation-batch-duplicate"));
        values.put("projectId",999L);
        assertThrows(RuntimeException.class,()->service.createBatch(values,List.of(30L),"installation-batch-foreign"));
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation",Integer.class));
        verify(fixture.ctx.getBean(cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi.class),never()).validateSelection(any(),any());
    }

    @Test void trainingKeepsItsNativeTypeRulesAndCustomerOnlyConfirmationFields() {
        var service = (TrainingBusinessService) businesses.get("training");
        var invalid = values("training"); invalid.put("trainingTypes", "INVENTED_TYPE");
        assertThrows(RuntimeException.class, () -> service.createForm(invalid, "invalid-training"));
        var created = service.createForm(values("training"), "training-create");
        var row = service.get(created.entityRef().entityId());
        assertEquals(0, row.getStatus()); assertEquals("培训工程师", row.getTrainerName());
        assertThrows(BusinessContractException.class, () -> service.input(Map.of("signConfirmerName", "伪造客户")));
        assertThrows(BusinessContractException.class, () -> service.input(Map.of("signTokenDigest", "forged")));
        assertFalse(service.readableValues(row).containsKey("signTokenDigest"));
    }

    @Test void trainingIssueReplayDoesNotReissueOrJournalTheCustomerToken() {
        var service = (TrainingBusinessService) businesses.get("training");
        var created = service.createForm(values("training"), "training-create");
        long id = created.entityRef().entityId();
        var nativeService = fixture.ctx.getBean(cn.iocoder.yudao.module.pms.engineering.service.training.TrainingService.class);
        when(nativeService.issueTraining(id)).thenAnswer(call -> {
            fixture.jdbc.update("UPDATE imp_eng_training SET status=1,version=version+1 WHERE id=?", id);
            return new cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingIssueRespVO(id,
                    "test-only-link-secret", "/training-records/test-only-link-secret", java.time.LocalDateTime.now().plusDays(7), "record.html", "客户确认链接");
        });
        var first = service.issue(id, created.newConcurrencyBasis(), "training-issue");
        assertNotNull(first.issuance());
        var replay = service.issue(id, created.newConcurrencyBasis(), "training-issue");
        assertEquals(first.receipt(), replay.receipt()); assertNull(replay.issuance());
        verify(nativeService, times(1)).issueTraining(id);
        assertFalse(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(service.receipt("issue", "training-issue")).contains("test-only-link-secret"));
    }

    @Test void solutionSubmissionUsesTheNativeReviewAndCannotForgeApprovalOrDocumentOwnership() {
        var service=(SolutionBusinessService)businesses.get("solution");
        var first=service.createForm(values("solution"),"solution-create");long id=first.entityRef().entityId();
        assertEquals("IMPLEMENTATION",service.get(id).getSolutionType());
        assertThrows(BusinessContractException.class,()->service.input(Map.of("reviewLevel",0)));
        assertThrows(BusinessContractException.class,()->service.input(Map.of("baselineVersion",100)));
        var documents=fixture.ctx.getBean(cn.iocoder.yudao.module.pms.engineering.service.solution.SolutionCustomerDocumentService.class);
        doThrow(new IllegalArgumentException("foreign document")).when(documents).validatePointer(eq(id),anyString());
        assertThrows(RuntimeException.class,()->service.updateForm(id,Map.of("remark","{\"customerPlanUrl\":\"/api/v1/pms/solutions/999/customer-files/7\"}"),first.newConcurrencyBasis(),"foreign-document"));
        var reviews=fixture.ctx.getBean(cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionTieredReviewService.class);
        when(reviews.start(any())).thenAnswer(call->{fixture.jdbc.update("UPDATE sol_eng_solution SET status=2,version=version+2 WHERE id=?",id);return null;});
        var pending=service.submitReview(id,first.newConcurrencyBasis(),"solution-submit","published-definition");
        assertEquals(2L,pending.newConcurrencyBasis());assertEquals(cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.ReceiptOutcome.APPROVAL_PENDING,pending.outcome());
        assertEquals(pending,service.submitReview(id,first.newConcurrencyBasis(),"solution-submit","published-definition"));verify(reviews,times(1)).start(any());
        assertThrows(RuntimeException.class,()->service.updateForm(id,Map.of("background","replace reviewed body"),pending.newConcurrencyBasis(),"in-review-edit"));
    }
}
