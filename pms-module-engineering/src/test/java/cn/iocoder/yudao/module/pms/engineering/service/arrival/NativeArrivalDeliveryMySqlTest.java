package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.infra.api.file.dto.*;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.deliverablechecklist.*;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptance.AcceptanceResultDeliveryAccess;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.delivery.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.PermissionBusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.service.command.*;
import cn.iocoder.yudao.module.pms.platform.service.delivery.*;
import cn.iocoder.yudao.module.pms.platform.service.file.*;
import cn.iocoder.yudao.module.pms.platform.service.file.command.*;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.*;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.*;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import org.mybatis.spring.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Production native/file/material services on an exclusive model-derived MySQL schema.
 * Permission/ProjectScope and technical storage are deterministic ports; this is not a full application/login or migration acceptance. */
@EnabledIfSystemProperty(named = "native.delivery.mysql", matches = "true")
class NativeArrivalDeliveryMySqlTest {
    @Configuration @EnableTransactionManagement(proxyTargetClass = true) static class Transactions {}
    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity(proxyTargetClass = true)
    static class SecuredControllers {}
    static final String URL = "jdbc:mysql://127.0.0.1:28461/native_delivery_verify?useSSL=false&allowPublicKeyRetrieval=true";
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    PermissionApi permissions = mock(PermissionApi.class);
    ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    ProjectAcceptanceContextApi projects = mock(ProjectAcceptanceContextApi.class);
    FileStorageReceiptApi storage = mock(FileStorageReceiptApi.class);
    Map<String, byte[]> stored = new HashMap<>();

    @BeforeEach void start() throws Exception {
        assertEquals(URL, System.getProperty("native.delivery.jdbcUrl"), "Exclusive tmpfs Compose database required");
        var source = new DriverManagerDataSource(URL, "root", "");
        jdbc = new JdbcTemplate(source);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        var global = new GlobalConfig(); global.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO));
        global.setMetaObjectHandler(new DefaultDBFieldHandler()); GlobalConfigUtils.setGlobalConfig(configuration, global);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        var mapperTypes = List.of(ArrivalMapper.class, DeliverableChecklistMapper.class, FileArtifactMapper.class, FileVersionMapper.class, FileReferenceMapper.class,
                FileUploadSessionMapper.class, FileArchiveRecordMapper.class, PlatformIdempotencyRecordMapper.class,
                PlatformOperationAuditMapper.class, PlatformOutboxEventMapper.class, DeliveryMaterialMapper.class,
                DeliveryRequirementMapper.class, DeliverySubmissionMapper.class, DeliveryFulfillmentMapper.class,
                DeliveryTypeMapper.class, DeliveryCapabilityConfigMapper.class);
        mapperTypes.forEach(configuration::addMapper);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
        factory.setGlobalConfig(global); factory.setPlugins(interceptor);
        var resources = new ArrayList<org.springframework.core.io.Resource>();
        var resolver = new PathMatchingResourcePatternResolver();
        for (String directory : List.of("file", "command", "delivery", "arrival", "deliverablechecklist"))
            resources.addAll(List.of(resolver.getResources("classpath*:mapper/" + directory + "/*.xml")));
        factory.setMapperLocations(resources.toArray(org.springframework.core.io.Resource[]::new));
        var sessions = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        for (var type : List.of(ArrivalDO.class, DeliverableChecklistDO.class, FileArtifactDO.class, FileVersionDO.class, FileReferenceDO.class,
                FileUploadSessionDO.class, FileArchiveRecordDO.class, PlatformIdempotencyRecordDO.class,
                PlatformOperationAuditDO.class, PlatformOutboxEventDO.class, DeliveryMaterialDO.class,
                DeliveryRequirementDO.class, DeliverySubmissionDO.class, DeliveryFulfillmentDO.class,
                DeliveryTypeDO.class, DeliveryCapabilityConfigDO.class)) schema(type);
        jdbc.execute("CREATE UNIQUE INDEX ledger_scope ON plt_idempotency_record(tenant_id,scope_code,actor_id,idempotency_key)");
        jdbc.execute("CREATE UNIQUE INDEX source_identity ON plt_delivery_material(tenant_id,source_identity_key)");
        jdbc.execute("CREATE UNIQUE INDEX file_slot ON plt_file_reference(tenant_id,owner_context,object_type,object_id,purpose_code,reference_key)");
        context = new AnnotationConfigApplicationContext(); context.register(Transactions.class);
        context.getBeanFactory().registerSingleton("dataSource", source);
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(source));
        for (var type : mapperTypes) context.getBeanFactory().registerSingleton(type.getSimpleName(), sessions.getMapper(type));
        context.getBeanFactory().registerSingleton("permissions", permissions);
        context.getBeanFactory().registerSingleton("scopes", scopes);
        context.getBeanFactory().registerSingleton("projects", projects);
        context.getBeanFactory().registerSingleton("storage", storage);
        when(permissions.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(scope()); when(scopes.lockAndRevalidate(any())).thenReturn(scope());
        var active = new ProjectAcceptanceContextApi.Context(20L, 20L, 0L, 3L, "ACTIVE");
        when(projects.inspect(any())).thenReturn(active); when(projects.lock(any(), any(), any())).thenReturn(active);
        when(storage.store(any())).thenAnswer(call -> { var command = call.getArgument(0, FileStorageStoreCommand.class);
            stored.put(command.storageOperationId(), command.validatedContent());
            return new FileStorageReceipt(command.storageOperationId(), (long) stored.size(), command.name(), command.mediaType(), command.validatedContent().length); });
        var arrivalMapper = sessions.getMapper(ArrivalMapper.class);
        BusinessModelContributor contributor = () -> List.of(new BusinessModelDeclaration(new BusinessModelDescriptor("IMP", "arrival",
                "IMP_ARRIVAL", 1, BusinessModelKind.AGGREGATE_ROOT, "到货签收", "pms:imp-arrival:query",
                List.of(new BusinessFieldDescriptor("projectId", "项目", EntityField.Type.NUMBER, true, true, false, null)),
                List.of(), List.of(), List.of(), "imp_eng_arrival"), ArrivalDO.class, arrivalMapper, null),
                new BusinessModelDeclaration(new BusinessModelDescriptor("ACC", "deliverableChecklist", "ACC_DELIVERABLE_CHECKLIST", 1,
                        BusinessModelKind.AGGREGATE_ROOT, "核对清单", "pms:acc-deliverable-checklist:query",
                        List.of(new BusinessFieldDescriptor("projectId", "项目", EntityField.Type.NUMBER, true, true, false, null)),
                        List.of(), List.of(), List.of(), "acc_deliverable_checklist"), DeliverableChecklistDO.class, sessions.getMapper(DeliverableChecklistMapper.class), null));
        context.getBeanFactory().registerSingleton("contributor", contributor);
        context.registerBean(BusinessEntityPersistenceRegistry.class, () -> new BusinessEntityPersistenceRegistry(context.getBeanProvider(BusinessModelContributor.class)));
        context.register(ArrivalNativeDeliveryAccess.class);
        context.registerBean(AcceptanceResultDeliveryAccess.class, () -> new AcceptanceResultDeliveryAccess(sessions.getMapper(DeliverableChecklistMapper.class),
                mock(cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper.class),
                mock(cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper.class), permissions, scopes));
        context.register(ChecklistAttachmentFilePolicy.class, ChecklistAttachmentSources.class, ChecklistAttachmentRegistration.class,
                DeliverableChecklistDeliveryAccess.class, DeliverableChecklistServiceImpl.class);
        context.getBeanFactory().registerSingleton("acceptanceRecordCodeGenerator",
                mock(cn.iocoder.yudao.module.pms.acceptance.service.AcceptanceRecordCodeGenerator.class));
        context.registerBean(DeliveryOwnerAccess.class, () -> new DeliveryOwnerAccess(List.of(context.getBean(ArrivalNativeDeliveryAccess.class), context.getBean(AcceptanceResultDeliveryAccess.class)), context.getBean(BusinessEntityPersistenceRegistry.class),
                new PermissionBusinessAccessGuard(permissions), context.getBeanProvider(EntityFieldProvider.class), scopes, projects));
        context.register(ArrivalFilePolicyProvider.class, ArrivalDocumentSources.class, ArrivalDeliveryEvidenceProvider.class,
                ArrivalDeliveryRegistration.class, ArrivalServiceImpl.class, PlatformCommandExecutionApiImpl.class,
                PlatformTransactionalOutboxWriter.class, OperationAuditApiImpl.class, DeliveryCatalogService.class,
                DeliveryMaterialService.class, PlatformDeliveryMaterialApiImpl.class, DeliveryFulfillmentService.class,
                DeliveryMaterialWithdrawalService.class, cn.iocoder.yudao.module.pms.platform.service.businessmodel.TenantCallerContext.class,
                DeliveryDocumentOriginResolver.class, DeliveryEventPublisher.class, DeliveryRequirementService.class);
        context.getBeanFactory().registerSingleton("engineeringRecordCodeGenerator", mock(EngineeringRecordCodeGenerator.class));
        context.registerBean(cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi.class,
                () -> mock(cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi.class));
        context.registerBean(FileBusinessObjectPolicyRegistry.class, () -> new FileBusinessObjectPolicyRegistry(List.of(context.getBean(ArrivalFilePolicyProvider.class), context.getBean(ChecklistAttachmentFilePolicy.class))));
        context.registerBean(BoundedMultipartReader.class, BoundedMultipartReader::new);
        context.registerBean(FileContentPolicyService.class, () -> new FileContentPolicyService(context.getBean(BoundedMultipartReader.class), List.of(), false));
        context.registerBean(FileEventFactory.class, FileEventFactory::new);
        context.registerBean(FileUploadApplicationService.class, () -> new FileUploadApplicationService(sessions.getMapper(FileUploadSessionMapper.class),
                sessions.getMapper(FileArtifactMapper.class), sessions.getMapper(FileVersionMapper.class), sessions.getMapper(FileReferenceMapper.class),
                context.getBean(FileBusinessObjectPolicyRegistry.class), context.getBean(BoundedMultipartReader.class), context.getBean(FileContentPolicyService.class),
                storage, context.getBean(FileEventFactory.class), context.getBean(PlatformCommandExecutionApiImpl.class),
                context.getBean(OperationAuditApiImpl.class), Duration.ofMinutes(15)));
        context.registerBean(FileEvidenceService.class, () -> new FileEvidenceService(sessions.getMapper(FileArtifactMapper.class), sessions.getMapper(FileVersionMapper.class), sessions.getMapper(FileReferenceMapper.class)));
        context.registerBean(FileArtifactApiImpl.class, () -> new FileArtifactApiImpl(context.getBean(FileBusinessObjectPolicyRegistry.class), sessions.getMapper(FileArtifactMapper.class),
                sessions.getMapper(FileVersionMapper.class), sessions.getMapper(FileReferenceMapper.class), null, sessions.getMapper(FileArchiveRecordMapper.class), permissions, null, null, null));
        var security = mock(SecurityFrameworkService.class); when(security.hasPermission(anyString())).thenReturn(true);
        context.getBeanFactory().registerSingleton("ss", security);
        context.registerBean(FileQueryService.class, () -> new FileQueryService(context.getBean(FileBusinessObjectPolicyRegistry.class),
                sessions.getMapper(FileArtifactMapper.class), sessions.getMapper(FileVersionMapper.class), sessions.getMapper(FileReferenceMapper.class), security));
        context.registerBean(FileLifecycleApplicationService.class, () -> new FileLifecycleApplicationService(context.getBean(PlatformCommandExecutionApiImpl.class),
                context.getBean(OperationAuditApiImpl.class), context.getBean(FileBusinessObjectPolicyRegistry.class), security, sessions.getMapper(FileArtifactMapper.class),
                sessions.getMapper(FileVersionMapper.class), sessions.getMapper(FileReferenceMapper.class), sessions.getMapper(FileUploadSessionMapper.class), sessions.getMapper(FileArchiveRecordMapper.class), context.getBean(FileEventFactory.class)));
        // Public Java CAS has separate explicit permission/Owner tests; accidental use here stays denied.
        context.registerBean(cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi.class,
                () -> mock(cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi.class));
        context.refresh(); var spring = new SpringUtil(); spring.setApplicationContext(context); spring.postProcessBeanFactory(context.getBeanFactory());
        tx = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class)); login(7L);
        var row = new ArrivalDO(); row.setId(9L); row.setProjectId(20L); row.setTenantId(7L); row.setVersion(0L); row.setStatus(0); row.setCode("ARR-9");
        row.setArrivalTime(LocalDateTime.of(2026, 10, 6, 8, 0)); row.setQuantity(1); row.setRemark("Persisted receipt");
        arrivalMapper.insert(row);
        var checklist = new DeliverableChecklistDO(); checklist.setId(19L); checklist.setTenantId(7L); checklist.setProjectId(20L);
        checklist.setStatus(0); checklist.setVersion(0L); checklist.setCode("CHECK-19"); checklist.setName("原生核对清单"); checklist.setRemark("Persisted checklist");
        sessions.getMapper(DeliverableChecklistMapper.class).insert(checklist);
        context.getBean(DeliveryCatalogService.class).createType(ChecklistAttachmentSources.SOURCE, "核对清单附件", "CHECKLIST_ATTACHMENT", List.of("txt"), 5242880L, "exclusive test");
        context.getBean(DeliveryCatalogService.class).createType(ArrivalDocumentSources.SOURCE_CODE, "签收单", "ARRIVAL_SIGN_DOCUMENT", List.of("txt"), 52428800L, "exclusive test");
        // V395's existing native business result type; schema setup here is not migration acceptance.
        var receipt = new DeliveryTypeDO(); receipt.setTypeCode("RECEIPT"); receipt.setName("签收单"); receipt.setCategory("交付资料");
        receipt.setAllowedMediaJson("[]"); receipt.setMaxSizeBytes(52428800L); receipt.setEnabled(true); receipt.setVersion(0);
        context.getBean(DeliveryTypeMapper.class).insert(receipt);
    }

    @AfterEach void close() { TenantContextHolder.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); if (context != null) context.close(); }
    static ProjectScopeResult scope() { return new ProjectScopeResult(20L, 3L, Set.of(20L), Set.of()); }
    void login(Long tenant) { TenantContextHolder.setTenantId(tenant); SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(17L).setTenantId(tenant), new MockHttpServletRequest()); }
    FileUploadCompleted upload(String key, String mode, Long artifact, Integer expected, String content) {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var service = context.getBean(FileUploadApplicationService.class);
        var init = service.initialize(new FileUploadInitializeCommand(7L, 17L, key + ":init", mode, artifact, expected, "IMP", "ARRIVAL", "9",
                ArrivalFilePolicyProvider.PURPOSE_CODE, ArrivalFilePolicyProvider.REFERENCE_KEY, "receipt.txt", "ARRIVAL_SIGN_DOCUMENT", (long) bytes.length, "text/plain", null));
        return service.complete(new FileUploadCompleteCommand(7L, 17L, key + ":complete", init.artifactId(), init.sessionId(), new MockMultipartFile("file", "receipt.txt", "text/plain", bytes), null));
    }
    void save() {
        var row = context.getBean(ArrivalMapper.class).selectById(9L);
        var request = new ArrivalSaveReqVO(); request.setId(9L); request.setProjectId(20L); request.setVersion(row.getVersion().intValue());
        request.setArrivalTime(row.getArrivalTime());
        context.getBean(ArrivalServiceImpl.class).updateArrival(request);
    }
    DeliveryRequirementDO requirement() {
        var row = new DeliveryRequirementDO(); row.setOwnerModule("IMP"); row.setEntityType("arrival"); row.setEntityId(9L);
        row.setProjectId(20L); row.setTypeCode(ArrivalDocumentSources.SOURCE_CODE); row.setRequirementKind("CATALOG"); row.setRequired(true);
        row.setMinimumQuantity(1); row.setCountingUnit("MATERIAL"); row.setStatus("OPEN"); context.getBean(DeliveryRequirementMapper.class).insert(row); return row;
    }
    @Test void realUploadOwnerSaveAndUnifiedQueriesShareOneMaterialAndWithdrawalRemovesCompletion() {
        var uploaded = upload("first", "CREATE_ARTIFACT", null, null, "Actual signed receipt data\n");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM plt_delivery_material", Integer.class));
        save(); save(); var api = context.getBean(PlatformDeliveryMaterialApi.class); var material = api.listByEntity("IMP", "arrival", 9L).getFirst();
        assertEquals(uploaded.artifactId(), material.fileArtifactId()); assertEquals(uploaded.sha256(), material.fileSha256());
        assertEquals(material.id(), api.listByProject(20L).getFirst().id()); assertNull(material.requirementId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM plt_delivery_material", Integer.class));
        var requirement = requirement(); var completion = context.getBean(DeliveryRequirementService.class);
        assertTrue(completion.evaluateCompletion(requirement.getId()).satisfied());
        context.getBean(DeliveryMaterialService.class).withdraw(material.id());
        assertFalse(completion.evaluateCompletion(requirement.getId()).satisfied());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version", Integer.class));
        assertEquals(1, stored.size());
    }
    @Test void referenceReplacementAndDetachCannotReuseEarlierCompletionEvidence() {
        var first = upload("old", "CREATE_ARTIFACT", null, null, "Original receipt\n"); save();
        var requirement = requirement(); var completion = context.getBean(DeliveryRequirementService.class);
        assertTrue(completion.evaluateCompletion(requirement.getId()).satisfied());
        var replacement = upload("new", "ADD_VERSION", first.artifactId(), 0, "Replaced receipt\n"); save();
        assertEquals(2, replacement.versionNo()); assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version", Integer.class));
        assertThrows(RuntimeException.class, () -> completion.evaluateCompletion(requirement.getId()));
        var oldMaterial = context.getBean(PlatformDeliveryMaterialApi.class).listByEntity("IMP", "arrival", 9L).stream()
                .filter(material -> material.fileVersionNo() == 1).findFirst().orElseThrow();
        context.getBean(DeliveryMaterialService.class).withdraw(oldMaterial.id());
        assertTrue(completion.evaluateCompletion(requirement.getId()).satisfied());
        context.getBean(FileLifecycleApplicationService.class).detach(new DetachFileReferenceCommand(7L, 17L, "detach", replacement.referenceId(), 1,
                "IMP", "ARRIVAL", "9", ArrivalFilePolicyProvider.PURPOSE_CODE, ArrivalFilePolicyProvider.REFERENCE_KEY, "correction"));
        assertThrows(RuntimeException.class, () -> completion.evaluateCompletion(requirement.getId()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM plt_delivery_material", Integer.class));
    }
    @Test void registrationFailureRollsBackOwnerSaveAndCrossTenantAndSignedWritesStayDenied() {
        upload("rollback", "CREATE_ARTIFACT", null, null, "Receipt requiring registration\n");
        jdbc.execute("ALTER TABLE plt_delivery_material ADD CONSTRAINT reject_material CHECK(entity_id<>9)");
        assertThrows(RuntimeException.class, this::save); assertEquals(0L, context.getBean(ArrivalMapper.class).selectById(9L).getVersion());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM plt_delivery_material", Integer.class));
        jdbc.execute("ALTER TABLE plt_delivery_material DROP CHECK reject_material"); save();
        login(8L); assertThrows(RuntimeException.class, this::save); login(7L);
        context.getBean(ArrivalServiceImpl.class).signArrival(9L);
        var material = context.getBean(PlatformDeliveryMaterialApi.class).listByEntity("IMP", "arrival", 9L).stream()
                .filter(item -> item.fileArtifactId() != null).findFirst().orElseThrow();
        assertThrows(RuntimeException.class, () -> context.getBean(DeliveryMaterialService.class).withdraw(material.id()));
        assertThrows(RuntimeException.class, () -> upload("signed", "ADD_VERSION", 1L, 0, "attempt\n"));
        assertThrows(RuntimeException.class, () -> context.getBean(ArrivalServiceImpl.class).deleteArrival(9L));
        assertEquals(1, context.getBean(ArrivalMapper.class).selectById(9L).getStatus());
    }
    @Test void nativeChecklistSubmissionFreezesActualFilesAndRejectsPublicWithdrawal() {
        byte[] bytes = "Actual checklist evidence\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var uploads = context.getBean(FileUploadApplicationService.class);
        var initialized = uploads.initialize(new FileUploadInitializeCommand(7L,17L,"checklist:init","CREATE_ARTIFACT",null,null,
                "ACC","DELIVERABLE_CHECKLIST","19","CHECKLIST_ATTACHMENT","actual-checklist-slot","checklist.txt","CHECKLIST_ATTACHMENT",(long)bytes.length,"text/plain",null));
        uploads.complete(new FileUploadCompleteCommand(7L,17L,"checklist:complete",initialized.artifactId(),initialized.sessionId(),new MockMultipartFile("file","checklist.txt","text/plain",bytes),null));
        context.getBean(DeliverableChecklistServiceImpl.class).submitDeliverableChecklist(19L);
        var material = context.getBean(PlatformDeliveryMaterialApi.class).listByEntity("ACC","deliverableChecklist",19L).getFirst();
        assertEquals(ChecklistAttachmentSources.SOURCE,material.typeCode()); assertNull(material.requirementId());
        assertEquals(1,context.getBean(DeliverableChecklistMapper.class).selectById(19L).getStatus());
        assertThrows(RuntimeException.class,()->context.getBean(DeliveryMaterialService.class).withdraw(material.id()));
        assertThrows(RuntimeException.class,()->uploads.initialize(new FileUploadInitializeCommand(7L,17L,"checklist:late","CREATE_ARTIFACT",null,null,
                "ACC","DELIVERABLE_CHECKLIST","19","CHECKLIST_ATTACHMENT","late-slot","checklist.txt","CHECKLIST_ATTACHMENT",(long)bytes.length,"text/plain",null)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version",Integer.class));
    }
    /** Browser runs the production SFCs and controllers with fixture authentication and deterministic external ports. */
    @Test @EnabledIfSystemProperty(named = "native.delivery.browser", matches = "true")
    void chromiumNativeUploadCollectAndFailedCollectionRetry() throws Exception {
        requirement();
        var checklistRequirement = new DeliveryRequirementDO(); checklistRequirement.setOwnerModule("ACC"); checklistRequirement.setEntityType("deliverableChecklist"); checklistRequirement.setEntityId(19L);
        checklistRequirement.setProjectId(20L); checklistRequirement.setTypeCode(ChecklistAttachmentSources.SOURCE); checklistRequirement.setRequirementKind("CATALOG");
        checklistRequirement.setRequired(true); checklistRequirement.setMinimumQuantity(1); checklistRequirement.setCountingUnit("MATERIAL"); checklistRequirement.setStatus("OPEN"); context.getBean(DeliveryRequirementMapper.class).insert(checklistRequirement);
        try (var secured = new AnnotationConfigApplicationContext()) {
            secured.setParent(context); secured.register(SecuredControllers.class,
                    cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.ArrivalController.class,
                    cn.iocoder.yudao.module.pms.acceptance.controller.admin.deliverablechecklist.DeliverableChecklistController.class,
                    cn.iocoder.yudao.module.pms.platform.controller.admin.delivery.DeliveryController.class,
                    cn.iocoder.yudao.module.pms.platform.controller.admin.file.FileArtifactController.class);
            secured.getBeanFactory().registerSingleton("tickets", mock(FileAccessTicketService.class)); secured.refresh();
            var json = tools.jackson.databind.json.JsonMapper.builder()
                    .addModule(new cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration().timestampSupportModuleBean()).build();
            var http = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                    secured.getBean(cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.ArrivalController.class),
                    secured.getBean(cn.iocoder.yudao.module.pms.acceptance.controller.admin.deliverablechecklist.DeliverableChecklistController.class),
                    secured.getBean(cn.iocoder.yudao.module.pms.platform.controller.admin.delivery.DeliveryController.class),
                    secured.getBean(cn.iocoder.yudao.module.pms.platform.controller.admin.file.FileArtifactController.class))
                    .setMessageConverters(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(json))
                    .setControllerAdvice(new cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.BusinessModelContractAdvice(),
                            new cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler("native-delivery-fixture",
                                    mock(cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi.class))).build();
            var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 28462), 0);
            server.createContext("/", exchange -> {
                try {
                    login(7L); String path = exchange.getRequestURI().getPath(); byte[] content; int status = 200;
                    if (path.equals("/fixture/reject-material")) {
                        jdbc.execute("ALTER TABLE plt_delivery_material ADD CONSTRAINT reject_material CHECK(entity_id<>9 OR file_version_no=1)"); content = "{}".getBytes();
                    } else if (path.equals("/fixture/allow-material")) {
                        jdbc.execute("ALTER TABLE plt_delivery_material DROP CHECK reject_material"); content = "{}".getBytes();
                    } else if (path.equals("/fixture/reject-checklist")) {
                        jdbc.execute("ALTER TABLE plt_delivery_material ADD CONSTRAINT reject_checklist CHECK(entity_id<>19)"); content = "{}".getBytes();
                    } else if (path.equals("/fixture/allow-checklist")) {
                        jdbc.execute("ALTER TABLE plt_delivery_material DROP CHECK reject_checklist"); content = "{}".getBytes();
                    } else if (path.equals("/fixture/checklist-evidence")) {
                        var evidence = new LinkedHashMap<String,Object>(); var api = context.getBean(PlatformDeliveryMaterialApi.class);
                        evidence.put("ownerMaterials",api.listByEntity("ACC","deliverableChecklist",19L)); evidence.put("projectMaterials",api.listByProject(20L));
                        var checklist = context.getBean(DeliverableChecklistMapper.class).selectById(19L);
                        evidence.put("ownerVersion",checklist.getVersion()); evidence.put("ownerRemark",checklist.getRemark());
                        evidence.put("fileVersions",jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version v JOIN plt_file_artifact a ON a.id=v.artifact_id WHERE a.owner_context='ACC'",Long.class));
                        content = json.writeValueAsBytes(evidence);
                    } else if (path.equals("/fixture/evidence")) {
                        var evidence = new LinkedHashMap<String, Object>();
                        for (String table : List.of("plt_file_artifact", "plt_file_version", "plt_file_reference", "plt_file_upload_session", "plt_delivery_material"))
                            evidence.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
                        var api = context.getBean(PlatformDeliveryMaterialApi.class);
                        evidence.put("ownerMaterials", api.listByEntity("IMP", "arrival", 9L)); evidence.put("projectMaterials", api.listByProject(20L));
                        evidence.put("ownerVersion", context.getBean(ArrivalMapper.class).selectById(9L).getVersion());
                        evidence.put("ownerRemark", context.getBean(ArrivalMapper.class).selectById(9L).getRemark());
                        content = json.writeValueAsBytes(evidence);
                    } else {
                        String target = exchange.getRequestURI().toString();
                        byte[] body = exchange.getRequestBody().readAllBytes();
                        var request = browserRequest(exchange.getRequestMethod(), target, exchange.getRequestHeaders().getFirst("Content-Type"), body);
                        exchange.getRequestHeaders().forEach((name, values) -> values.forEach(value -> request.header(name, value)));
                        var response = http.perform(request).andReturn().getResponse(); status = response.getStatus(); content = response.getContentAsByteArray();
                    }
                    exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
                    exchange.sendResponseHeaders(status, content.length); exchange.getResponseBody().write(content);
                } catch (Exception error) {
                    error.printStackTrace(); byte[] failure = json.writeValueAsBytes(Map.of("code", 500, "msg", error.getClass().getSimpleName()));
                    exchange.sendResponseHeaders(500, failure.length); exchange.getResponseBody().write(failure);
                } finally { exchange.close(); TenantContextHolder.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            });
            server.start();
            try {
                var repository = java.nio.file.Path.of("").toAbsolutePath();
                while (repository != null && !java.nio.file.Files.isDirectory(repository.resolve("scripts/tests"))) repository = repository.getParent();
                var script = Objects.requireNonNull(repository, "Repository scripts directory required").resolve("scripts/tests/run_native_arrival_delivery_browser.py");
                var output = repository.resolve(".run/native-delivery-20261006/browser-process.log");
                var process = new ProcessBuilder("python3", script.toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
                if (!process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Browser exceeded 120 seconds"); }
                System.out.println(java.nio.file.Files.readString(output));
                assertEquals(0, process.exitValue(), "See browser evidence and process output");
                var checklistScript = repository.resolve("scripts/tests/run_native_checklist_delivery_browser.py");
                var checklistOutput = repository.resolve(".run/native-delivery-20261006/checklist-browser-process.log");
                var checklistProcess = new ProcessBuilder("python3", checklistScript.toString()).redirectErrorStream(true).redirectOutput(checklistOutput.toFile()).start();
                if (!checklistProcess.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) { checklistProcess.destroyForcibly(); fail("Checklist browser exceeded 120 seconds"); }
                System.out.println(java.nio.file.Files.readString(checklistOutput));
                assertEquals(0, checklistProcess.exitValue(), "See checklist browser evidence");
            } finally { server.stop(0); }
        }
    }
    /** Transport-only multipart parsing for the text fixture; business validation remains in production services. */
    static org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<?> browserRequest(String method, String target, String mediaType, byte[] body) {
        if (mediaType != null && mediaType.startsWith("multipart/form-data")) {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(target);
            String boundary = mediaType.substring(mediaType.indexOf("boundary=") + 9).replace("\"", "");
            String multipart = new String(body, java.nio.charset.StandardCharsets.ISO_8859_1);
            for (String part : multipart.split(java.util.regex.Pattern.quote("--" + boundary))) {
                int split = part.indexOf("\r\n\r\n"); if (split < 0) continue;
                String headers = part.substring(0, split), value = part.substring(split + 4);
                if (value.endsWith("\r\n")) value = value.substring(0, value.length() - 2);
                var name = java.util.regex.Pattern.compile("name=\"([^\"]+)\"").matcher(headers); if (!name.find()) continue;
                var file = java.util.regex.Pattern.compile("filename=\"([^\"]+)\"").matcher(headers);
                if (file.find()) request.file(new MockMultipartFile(name.group(1), file.group(1), "text/plain", value.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
                else request.param(name.group(1), value);
            }
            return request;
        }
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), target);
        if (body.length > 0) request.contentType(Objects.requireNonNullElse(mediaType, "application/json")).content(body);
        return request;
    }
    void schema(Class<?> type) {
        String table = type.getAnnotation(TableName.class).value(); var columns = new LinkedHashMap<String, String>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) for (Field field : current.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            var annotation = field.getAnnotation(TableField.class); if (annotation != null && !annotation.exist()) continue;
            String name = annotation != null && !annotation.value().isBlank() ? annotation.value() : field.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            Class<?> kind = field.getType(); String sql = kind == Long.class || kind == Integer.class ? "BIGINT" : kind == Boolean.class ? "BOOLEAN" : kind == LocalDateTime.class ? "DATETIME(3)" : "LONGTEXT";
            if (Set.of("scope_code", "idempotency_key", "source_identity_key", "owner_context", "object_type", "object_id", "purpose_code", "reference_key").contains(name)) sql = "VARCHAR(100)";
            if (name.equals("id")) sql = "BIGINT AUTO_INCREMENT PRIMARY KEY";
            else if (name.equals("deleted")) sql = "BOOLEAN DEFAULT FALSE";
            else if (name.equals("create_time") || name.equals("update_time")) sql = "DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)";
            columns.putIfAbsent(name, "`" + name + "` " + sql);
        }
        jdbc.execute("DROP TABLE IF EXISTS " + table); jdbc.execute("CREATE TABLE " + table + " (" + String.join(",", columns.values()) + ")");
    }
}
