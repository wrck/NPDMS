package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.module.pms.engineering.service.attachment.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationMapper;
import cn.iocoder.yudao.module.pms.engineering.service.configuration.ConfigurationServiceImpl;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestMapper;
import cn.iocoder.yudao.module.pms.engineering.service.jointtest.JointTestServiceImpl;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.externalprocurement.ExternalProcurementDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.externalprocurement.ExternalProcurementMapper;
import cn.iocoder.yudao.module.pms.engineering.service.externalprocurement.ExternalProcurementServiceImpl;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo.ExternalProcurementSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.outsource.OutsourceRequestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.outsource.OutsourceRequestMapper;
import cn.iocoder.yudao.module.pms.engineering.service.outsource.OutsourceRequestServiceImpl;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo.OutsourceRequestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialrequisition.MaterialRequisitionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialrequisition.MaterialRequisitionMapper;
import cn.iocoder.yudao.module.pms.engineering.service.materialrequisition.MaterialRequisitionServiceImpl;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo.MaterialRequisitionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.MaterialExchangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.MaterialExchangeMapper;
import cn.iocoder.yudao.module.pms.engineering.service.materialexchange.MaterialExchangeServiceImpl;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangeSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.MaterialExchangeSerialMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.MaterialExchangeSerialDO;
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
class NativeAttachmentDeliveryMySqlTest {
    @Configuration @EnableTransactionManagement(proxyTargetClass = true) static class Transactions {}
    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity(proxyTargetClass = true)
    static class SecuredControllers {}
    static final String URL = "jdbc:mysql://127.0.0.1:28471/native_delivery_verify?useSSL=false&allowPublicKeyRetrieval=true";
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    PermissionApi permissions = mock(PermissionApi.class);
    ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    ProjectAcceptanceContextApi projects = mock(ProjectAcceptanceContextApi.class);
    FileStorageReceiptApi storage = mock(FileStorageReceiptApi.class);
    Map<String, byte[]> stored = new HashMap<>();

    @BeforeEach void start() throws Exception {
        String selectedUrl=testJdbcUrl();
        assertEquals(selectedUrl, System.getProperty("native.delivery.jdbcUrl"), "Exclusive tmpfs Compose database required");
        var source = new DriverManagerDataSource(selectedUrl, "root", "");
        jdbc = new JdbcTemplate(source);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        var global = new GlobalConfig(); global.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO));
        global.setMetaObjectHandler(new DefaultDBFieldHandler()); GlobalConfigUtils.setGlobalConfig(configuration, global);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        var mapperTypes = new ArrayList<Class<?>>(List.of(ConfigurationMapper.class, JointTestMapper.class, ExternalProcurementMapper.class, OutsourceRequestMapper.class, MaterialRequisitionMapper.class, MaterialExchangeMapper.class, MaterialExchangeSerialMapper.class, ArrivalMapper.class, DeliverableChecklistMapper.class, FileArtifactMapper.class, FileVersionMapper.class, FileReferenceMapper.class,
                FileUploadSessionMapper.class, FileArchiveRecordMapper.class, PlatformIdempotencyRecordMapper.class,
                PlatformOperationAuditMapper.class, PlatformOutboxEventMapper.class, DeliveryMaterialMapper.class,
                DeliveryRequirementMapper.class, DeliverySubmissionMapper.class, DeliveryFulfillmentMapper.class,
                DeliveryTypeMapper.class, DeliveryCapabilityConfigMapper.class));
        mapperTypes.addAll(extraMapperTypes());
        mapperTypes.forEach(configuration::addMapper);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
        factory.setGlobalConfig(global); factory.setPlugins(interceptor);
        var resources = new ArrayList<org.springframework.core.io.Resource>();
        var resolver = new PathMatchingResourcePatternResolver();
        for (String directory : List.of("file", "command", "delivery", "arrival", "deliverablechecklist", "configuration", "jointtest", "externalprocurement", "outsource", "materialrequisition", "materialexchange"))
            resources.addAll(List.of(resolver.getResources("classpath*:mapper/" + directory + "/*.xml")));
        for(String path:extraMapperPaths())resources.addAll(List.of(resolver.getResources("classpath*:mapper/"+path)));
        factory.setMapperLocations(resources.toArray(org.springframework.core.io.Resource[]::new));
        var sessions = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        for (var type : List.of(ConfigurationDO.class, JointTestDO.class, ExternalProcurementDO.class, OutsourceRequestDO.class, MaterialRequisitionDO.class, MaterialExchangeDO.class, MaterialExchangeSerialDO.class, ArrivalDO.class, DeliverableChecklistDO.class, FileArtifactDO.class, FileVersionDO.class, FileReferenceDO.class,
                FileUploadSessionDO.class, FileArchiveRecordDO.class, PlatformIdempotencyRecordDO.class,
                PlatformOperationAuditDO.class, PlatformOutboxEventDO.class, DeliveryMaterialDO.class,
                DeliveryRequirementDO.class, DeliverySubmissionDO.class, DeliveryFulfillmentDO.class,
                DeliveryTypeDO.class, DeliveryCapabilityConfigDO.class)) schema(type);
        for(var type:extraSchemaTypes())schema(type);
        jdbc.execute("CREATE UNIQUE INDEX ledger_scope ON plt_idempotency_record(tenant_id,scope_code,actor_id,idempotency_key)");
        jdbc.execute("CREATE UNIQUE INDEX source_identity ON plt_delivery_material(tenant_id,source_identity_key)");
        jdbc.execute("DROP TABLE IF EXISTS native_device_archive_fixture");
        jdbc.execute("CREATE TABLE native_device_archive_fixture(file_locator VARCHAR(300),file_hash VARCHAR(100))");
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
        BusinessModelContributor contributor = () -> java.util.stream.Stream.concat(List.of(new BusinessModelDeclaration(new BusinessModelDescriptor("IMP", "arrival",
                "IMP_ARRIVAL", 1, BusinessModelKind.AGGREGATE_ROOT, "到货签收", "pms:imp-arrival:query",
                List.of(new BusinessFieldDescriptor("projectId", "项目", EntityField.Type.NUMBER, true, true, false, null)),
                List.of(), List.of(), List.of(), "imp_eng_arrival"), ArrivalDO.class, arrivalMapper, null),
                new BusinessModelDeclaration(new BusinessModelDescriptor("ACC", "deliverableChecklist", "ACC_DELIVERABLE_CHECKLIST", 1,
                        BusinessModelKind.AGGREGATE_ROOT, "核对清单", "pms:acc-deliverable-checklist:query",
                        List.of(new BusinessFieldDescriptor("projectId", "项目", EntityField.Type.NUMBER, true, true, false, null)),
                        List.of(), List.of(), List.of(), "acc_deliverable_checklist"), DeliverableChecklistDO.class, sessions.getMapper(DeliverableChecklistMapper.class), null),
new BusinessModelDeclaration(new BusinessModelDescriptor("IMP","configuration","NATIVE_CONFIGURATION",1,BusinessModelKind.AGGREGATE_ROOT,"原生附件","pms:"+NativeAttachmentKind.CONFIGURATION.getPermission().substring(4)+":query",List.of(new BusinessFieldDescriptor("projectId","项目",EntityField.Type.NUMBER,true,true,false,null)),List.of(),List.of(),List.of(),"imp_eng_configuration"),ConfigurationDO.class,sessions.getMapper(ConfigurationMapper.class),null),
new BusinessModelDeclaration(new BusinessModelDescriptor("IMP","jointTest","NATIVE_JOINT_TEST",1,BusinessModelKind.AGGREGATE_ROOT,"原生附件","pms:"+NativeAttachmentKind.JOINT_TEST.getPermission().substring(4)+":query",List.of(new BusinessFieldDescriptor("projectId","项目",EntityField.Type.NUMBER,true,true,false,null)),List.of(),List.of(),List.of(),"imp_eng_joint_test"),JointTestDO.class,sessions.getMapper(JointTestMapper.class),null),
new BusinessModelDeclaration(new BusinessModelDescriptor("IMP","externalProcurement","NATIVE_EXTERNAL_PROCUREMENT",1,BusinessModelKind.AGGREGATE_ROOT,"原生附件","pms:"+NativeAttachmentKind.EXTERNAL_PROCUREMENT.getPermission().substring(4)+":query",List.of(new BusinessFieldDescriptor("projectId","项目",EntityField.Type.NUMBER,true,true,false,null)),List.of(),List.of(),List.of(),"imp_eng_external_procurement"),ExternalProcurementDO.class,sessions.getMapper(ExternalProcurementMapper.class),null),
new BusinessModelDeclaration(new BusinessModelDescriptor("RES","outsourceRequest","NATIVE_OUTSOURCE",1,BusinessModelKind.AGGREGATE_ROOT,"原生附件","pms:"+NativeAttachmentKind.OUTSOURCE.getPermission().substring(4)+":query",List.of(new BusinessFieldDescriptor("projectId","项目",EntityField.Type.NUMBER,true,true,false,null)),List.of(),List.of(),List.of(),"res_outsource_request"),OutsourceRequestDO.class,sessions.getMapper(OutsourceRequestMapper.class),null),
new BusinessModelDeclaration(new BusinessModelDescriptor("IMP","materialRequisition","NATIVE_MATERIAL_REQUISITION",1,BusinessModelKind.AGGREGATE_ROOT,"原生附件","pms:"+NativeAttachmentKind.MATERIAL_REQUISITION.getPermission().substring(4)+":query",List.of(new BusinessFieldDescriptor("projectId","项目",EntityField.Type.NUMBER,true,true,false,null)),List.of(),List.of(),List.of(),"imp_eng_material_requisition"),MaterialRequisitionDO.class,sessions.getMapper(MaterialRequisitionMapper.class),null),
new BusinessModelDeclaration(new BusinessModelDescriptor("IMP","materialExchange","NATIVE_MATERIAL_EXCHANGE",1,BusinessModelKind.AGGREGATE_ROOT,"原生附件","pms:"+NativeAttachmentKind.MATERIAL_EXCHANGE.getPermission().substring(4)+":query",List.of(new BusinessFieldDescriptor("projectId","项目",EntityField.Type.NUMBER,true,true,false,null)),List.of(),List.of(),List.of(),"imp_eng_material_exchange"),MaterialExchangeDO.class,sessions.getMapper(MaterialExchangeMapper.class),null)).stream(),extraDeclarations(sessions).stream()).toList();
        context.getBeanFactory().registerSingleton("contributor", contributor);
        context.registerBean(BusinessEntityPersistenceRegistry.class, () -> new BusinessEntityPersistenceRegistry(context.getBeanProvider(BusinessModelContributor.class)));
        context.register(ArrivalNativeDeliveryAccess.class,NativeAttachmentOwners.class,NativeAttachmentAccess.class,
                NativeAttachmentPolicyConfiguration.class,NativeAttachmentSources.class,NativeAttachmentRegistration.class,
                IMPNativeAttachmentDeliveryAccess.class,RESNativeAttachmentDeliveryAccess.class,ConfigurationServiceImpl.class, JointTestServiceImpl.class, ExternalProcurementServiceImpl.class, OutsourceRequestServiceImpl.class, MaterialRequisitionServiceImpl.class, MaterialExchangeServiceImpl.class);
        var deviceLogs=mock(cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigLogRecordApi.class);
        when(deviceLogs.recordConfigLog(any())).thenAnswer(call->{var command=call.getArgument(0,cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceConfigLogRecordCommand.class);
            jdbc.update("INSERT INTO native_device_archive_fixture VALUES(?,?)",command.fileUrl(),command.fileHash());return 1L;});
        context.getBeanFactory().registerSingleton("deviceConfigLogRecordApi",deviceLogs);
        context.registerBean(cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.SiteSurveyEntityService.class,
                ()->mock(cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.SiteSurveyEntityService.class));
        var scopeFacts=mock(cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeLineFactApi.class);
        when(scopeFacts.validateSelection(anyLong(),anyList())).thenReturn(List.of());
        context.getBeanFactory().registerSingleton("scopeLineFactApi",scopeFacts);
        context.registerBean(cn.iocoder.yudao.module.pms.asset.api.product.AssetProductOfficialApi.class,
                ()->mock(cn.iocoder.yudao.module.pms.asset.api.product.AssetProductOfficialApi.class));
        context.registerBean(AcceptanceResultDeliveryAccess.class, () -> new AcceptanceResultDeliveryAccess(sessions.getMapper(DeliverableChecklistMapper.class),
                mock(cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper.class),
                mock(cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper.class), permissions, scopes));
        context.register(ChecklistAttachmentFilePolicy.class, ChecklistAttachmentSources.class, ChecklistAttachmentRegistration.class,
                DeliverableChecklistDeliveryAccess.class, DeliverableChecklistServiceImpl.class);
        context.getBeanFactory().registerSingleton("acceptanceRecordCodeGenerator",
                mock(cn.iocoder.yudao.module.pms.acceptance.service.AcceptanceRecordCodeGenerator.class));
        context.registerBean(DeliveryOwnerAccess.class, () -> new DeliveryOwnerAccess(new ArrayList<>(context.getBeansOfType(DeliveryMaterialUploadPolicyValidator.class).values()), context.getBean(BusinessEntityPersistenceRegistry.class),
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
        context.registerBean(FileBusinessObjectPolicyRegistry.class, () -> new FileBusinessObjectPolicyRegistry(context.getBeansOfType(FileBusinessObjectPolicyProvider.class).values().stream().toList()));
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
        registerExtraBeans(sessions);
        context.refresh(); var spring = new SpringUtil(); spring.setApplicationContext(context); spring.postProcessBeanFactory(context.getBeanFactory());
        tx = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class)); login(7L);
        var row = new ArrivalDO(); row.setId(9L); row.setProjectId(20L); row.setTenantId(7L); row.setVersion(0L); row.setStatus(0); row.setCode("ARR-9");
        row.setArrivalTime(LocalDateTime.of(2026, 10, 6, 8, 0)); row.setQuantity(1); row.setRemark("Persisted receipt");
        arrivalMapper.insert(row);
        for(var kind:NativeAttachmentKind.values())insertNative(kind);
        initializeExtraOwners(sessions);
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

    String testJdbcUrl(){return URL;}
    List<Class<?>> extraMapperTypes(){return List.of();}
    List<Class<?>> extraSchemaTypes(){return List.of();}
    List<String> extraMapperPaths(){return List.of();}
    List<BusinessModelDeclaration> extraDeclarations(SqlSessionTemplate sessions){return List.of();}
    void registerExtraBeans(SqlSessionTemplate sessions){}
    void initializeExtraOwners(SqlSessionTemplate sessions){}

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
    long nativeId(NativeAttachmentKind kind){return 30L+kind.ordinal();}
    void insertNative(NativeAttachmentKind kind){
        long id=nativeId(kind);
        switch(kind){
            case CONFIGURATION -> {var row=new ConfigurationDO();row.setId(id);row.setTenantId(7L);row.setProjectId(20L);row.setStatus(0);row.setVersion(0L);row.setRemark("Persisted native Owner");row.setCode("NATIVE-"+id);row.setEquipmentId(8L);context.getBean(ConfigurationMapper.class).insert(row);}
            case JOINT_TEST -> {var row=new JointTestDO();row.setTestCase("原生联调用例");row.setId(id);row.setTenantId(7L);row.setProjectId(20L);row.setStatus(0);row.setVersion(0L);row.setRemark("Persisted native Owner");row.setCode("NATIVE-"+id);context.getBean(JointTestMapper.class).insert(row);}
            case EXTERNAL_PROCUREMENT -> {var row=new ExternalProcurementDO();row.setName("原生申请");row.setProductName("真实物料");row.setQuantity(java.math.BigDecimal.ONE);row.setApplicantUserId(17L);row.setApplyTime(LocalDateTime.of(2026,10,6,8,0));row.setId(id);row.setTenantId(7L);row.setProjectId(20L);row.setStatus(0);row.setVersion(0L);row.setRemark("Persisted native Owner");row.setCode("NATIVE-"+id);context.getBean(ExternalProcurementMapper.class).insert(row);}
            case OUTSOURCE -> {var row=new OutsourceRequestDO();row.setName("原生外包");row.setApplicantUserId(17L);row.setApplyTime(LocalDateTime.of(2026,10,6,8,0));row.setId(id);row.setTenantId(7L);row.setProjectId(20L);row.setStatus(0);row.setVersion(0L);row.setRemark("Persisted native Owner");row.setCode("NATIVE-"+id);row.setTriggerSource("MANUAL");row.setWorkContent("actual work");context.getBean(OutsourceRequestMapper.class).insert(row);}
            case MATERIAL_REQUISITION -> {var row=new MaterialRequisitionDO();row.setName("原生申请");row.setProductName("真实物料");row.setQuantity(java.math.BigDecimal.ONE);row.setApplicantUserId(17L);row.setApplyTime(LocalDateTime.of(2026,10,6,8,0));row.setId(id);row.setTenantId(7L);row.setProjectId(20L);row.setStatus(0);row.setVersion(0L);row.setRemark("Persisted native Owner");row.setCode("NATIVE-"+id);row.setEquipmentId(8L);context.getBean(MaterialRequisitionMapper.class).insert(row);}
            case MATERIAL_EXCHANGE -> {var row=new MaterialExchangeDO();row.setId(id);row.setTenantId(7L);row.setProjectId(20L);row.setStatus(0);row.setVersion(0L);row.setRemark("Persisted native Owner");row.setCode("NATIVE-"+id);row.setQuantity(java.math.BigDecimal.ONE);row.setReason("actual reason");row.setName("actual exchange");row.setApplicantUserId(17L);context.getBean(MaterialExchangeMapper.class).insert(row);}
        }
    }
    void updateNative(NativeAttachmentKind kind,String remark){
        long id=nativeId(kind);
        switch(kind){
            case CONFIGURATION -> {var row=context.getBean(ConfigurationMapper.class).selectById(id);var request=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(row,ConfigurationSaveReqVO.class);request.setRemark(remark);context.getBean(ConfigurationServiceImpl.class).updateConfiguration(request);}
            case JOINT_TEST -> {var row=context.getBean(JointTestMapper.class).selectById(id);var request=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(row,JointTestSaveReqVO.class);request.setRemark(remark);context.getBean(JointTestServiceImpl.class).updateJointTest(request);}
            case EXTERNAL_PROCUREMENT -> {var row=context.getBean(ExternalProcurementMapper.class).selectById(id);var request=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(row,ExternalProcurementSaveReqVO.class);request.setRemark(remark);context.getBean(ExternalProcurementServiceImpl.class).updateExternalProcurement(request);}
            case OUTSOURCE -> {var row=context.getBean(OutsourceRequestMapper.class).selectById(id);var request=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(row,OutsourceRequestSaveReqVO.class);request.setRemark(remark);context.getBean(OutsourceRequestServiceImpl.class).updateOutsourceRequest(request);}
            case MATERIAL_REQUISITION -> {var row=context.getBean(MaterialRequisitionMapper.class).selectById(id);var request=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(row,MaterialRequisitionSaveReqVO.class);request.setRemark(remark);context.getBean(MaterialRequisitionServiceImpl.class).updateMaterialRequisition(request);}
            case MATERIAL_EXCHANGE -> {var row=context.getBean(MaterialExchangeMapper.class).selectById(id);var request=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(row,MaterialExchangeSaveReqVO.class);request.setRemark(remark);context.getBean(MaterialExchangeServiceImpl.class).updateMaterialExchange(request);}
        }
    }
    void freezeNative(NativeAttachmentKind kind){
        long id=nativeId(kind);
        switch(kind){
            case CONFIGURATION -> {context.getBean(ConfigurationServiceImpl.class).startConfiguration(id);context.getBean(ConfigurationServiceImpl.class).completeConfiguration(id);}
            case JOINT_TEST -> {context.getBean(JointTestServiceImpl.class).start(id);context.getBean(JointTestServiceImpl.class).pass(id);}
            case EXTERNAL_PROCUREMENT -> context.getBean(ExternalProcurementServiceImpl.class).submitExternalProcurement(id);
            case OUTSOURCE -> context.getBean(OutsourceRequestServiceImpl.class).submitOutsourceRequest(id);
            case MATERIAL_REQUISITION -> context.getBean(MaterialRequisitionServiceImpl.class).submitMaterialRequisition(id);
            case MATERIAL_EXCHANGE -> context.getBean(MaterialExchangeServiceImpl.class).submitMaterialExchange(id);
        }
    }
    FileUploadCompleted nativeUpload(NativeAttachmentKind kind,String slot){
        byte[] bytes=("Native "+kind+" actual file\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var uploads=context.getBean(FileUploadApplicationService.class);
        var initialized=uploads.initialize(new FileUploadInitializeCommand(7L,17L,slot+":init","CREATE_ARTIFACT",null,null,
                kind.getModule(),kind.getEntityType(),String.valueOf(nativeId(kind)),kind.getPurpose(),slot,"native.txt",kind.getPurpose(),(long)bytes.length,"text/plain",null));
        return uploads.complete(new FileUploadCompleteCommand(7L,17L,slot+":complete",initialized.artifactId(),initialized.sessionId(),new MockMultipartFile("file","native.txt","text/plain",bytes),null));
    }
    java.util.List<PlatformDeliveryMaterialApi.DeliveryMaterialView> nativeMaterials(NativeAttachmentKind kind){return context.getBean(PlatformDeliveryMaterialApi.class).listByEntity(kind.getModule(),kind.getEntityType(),nativeId(kind));}
    void nativeRequirement(NativeAttachmentKind kind){
        context.getBean(DeliveryCatalogService.class).createType(kind.sourceCode(),kind.getTitle(),kind.getPurpose(),List.of("txt"),5242880L,"exclusive fixture");
        var requirement=new DeliveryRequirementDO();requirement.setOwnerModule(kind.getModule());requirement.setEntityType(kind.getEntityType());requirement.setEntityId(nativeId(kind));
        requirement.setProjectId(20L);requirement.setTypeCode(kind.sourceCode());requirement.setRequirementKind("CATALOG");requirement.setRequired(true);requirement.setMinimumQuantity(1);requirement.setCountingUnit("MATERIAL");requirement.setStatus("OPEN");
        context.getBean(DeliveryRequirementMapper.class).insert(requirement);
    }
    boolean nativeComplete(NativeAttachmentKind kind){return context.getBean(DeliveryRequirementService.class).evaluateCompletion(jdbc.queryForObject("SELECT id FROM plt_delivery_requirement WHERE owner_module=? AND entity_type=? AND entity_id=?",Long.class,kind.getModule(),kind.getEntityType(),nativeId(kind))).satisfied();}
    @ParameterizedTest @EnumSource(NativeAttachmentKind.class)
    void nativeSaveCollectsExactlyOneActualMaterialAndCommonWithdrawalInvalidates(NativeAttachmentKind kind){
        nativeRequirement(kind);var uploaded=nativeUpload(kind,"actual");assertTrue(nativeMaterials(kind).isEmpty());
        updateNative(kind,"saved-native");var material=nativeMaterials(kind).getFirst();
        assertEquals(kind.sourceCode(),material.typeCode());assertEquals(uploaded.artifactId(),material.fileArtifactId());assertNull(material.requirementId());assertTrue(nativeComplete(kind));
        updateNative(kind,"second-save");assertEquals(1,nativeMaterials(kind).size());assertEquals(material.id(),nativeMaterials(kind).getFirst().id());
        assertTrue(context.getBean(PlatformDeliveryMaterialApi.class).listByProject(20L).stream().anyMatch(row->row.id().equals(material.id())));
        context.getBean(DeliveryMaterialService.class).withdraw(material.id());assertFalse(nativeComplete(kind));
        assertEquals("WITHDRAWN",nativeMaterials(kind).getFirst().status());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plt_delivery_submission",Integer.class));
    }
    @ParameterizedTest @EnumSource(NativeAttachmentKind.class)
    void nativeRegistrationFailureRollsBackOwnerAndRetryUsesCompletedFile(NativeAttachmentKind kind){
        nativeUpload(kind,"retry");jdbc.execute("ALTER TABLE plt_delivery_material ADD CONSTRAINT reject_native CHECK(source_entity_id<>"+nativeId(kind)+")");
        assertThrows(RuntimeException.class,()->updateNative(kind,"must-rollback"));assertTrue(nativeMaterials(kind).isEmpty());
        var owner=context.getBean(NativeAttachmentOwners.class).find(kind,7L,nativeId(kind),false);assertEquals(0,owner.status());
        assertEquals(0L,jdbc.queryForObject("SELECT version FROM "+nativeTable(kind)+" WHERE id=?",Long.class,nativeId(kind)));
        jdbc.execute("ALTER TABLE plt_delivery_material DROP CHECK reject_native");updateNative(kind,"retry-saved");assertEquals(1,nativeMaterials(kind).size());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version",Integer.class));
    }
    @ParameterizedTest @EnumSource(NativeAttachmentKind.class)
    void nativeFinalActionFreezesUploadsAndWithdrawalWhileFilesStayReadable(NativeAttachmentKind kind){
        nativeUpload(kind,"frozen");freezeNative(kind);var material=nativeMaterials(kind).getFirst();
        assertThrows(RuntimeException.class,()->nativeUpload(kind,"late"));assertThrows(RuntimeException.class,()->context.getBean(DeliveryMaterialService.class).withdraw(material.id()));
        assertTrue(context.getBean(NativeAttachmentAccess.class).require(kind,7L,17L,String.valueOf(nativeId(kind)),kind.getPurpose(),FileActionCodes.READ,false,null).allowed());
    }
    @ParameterizedTest @EnumSource(NativeAttachmentKind.class)
    void nativeFinalActionCannotCollectWithOnlyViewProjectScope(NativeAttachmentKind kind){
        nativeUpload(kind,"view-only-final");
        when(scopes.resolveCurrent(any())).thenAnswer(call->{
            var query=call.getArgument(0,ProjectCurrentScopeQuery.class);
            return ProjectScopeApi.ACTION_MANAGE.equals(query.actionCode())
                    ?new ProjectScopeResult(20L,3L,Set.of(),Set.of()):scope();
        });
        assertThrows(RuntimeException.class,()->freezeNative(kind));
        assertTrue(nativeMaterials(kind).isEmpty());
        var owner=context.getBean(NativeAttachmentOwners.class).find(kind,7L,nativeId(kind),false);
        assertEquals(kind==NativeAttachmentKind.CONFIGURATION||kind==NativeAttachmentKind.JOINT_TEST?1:0,owner.status());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version",Integer.class));
    }
    @ParameterizedTest @EnumSource(NativeAttachmentKind.class)
    void nativeFinalActionCannotCollectWithOnlyQueryPermission(NativeAttachmentKind kind){
        nativeUpload(kind,"query-only-final");
        when(permissions.hasAnyPermissions(17L,kind.getPermission()+":update")).thenReturn(false);
        assertThrows(RuntimeException.class,()->freezeNative(kind));
        assertTrue(nativeMaterials(kind).isEmpty());
        var owner=context.getBean(NativeAttachmentOwners.class).find(kind,7L,nativeId(kind),false);
        assertEquals(kind==NativeAttachmentKind.CONFIGURATION||kind==NativeAttachmentKind.JOINT_TEST?1:0,owner.status());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version",Integer.class));
    }
    @ParameterizedTest @EnumSource(NativeAttachmentKind.class)
    void nativeTenantPermissionScopeAndProjectRevocationFailClosed(NativeAttachmentKind kind){
        var access=context.getBean(NativeAttachmentAccess.class);String id=String.valueOf(nativeId(kind));
        when(permissions.hasAnyPermissions(17L,kind.getPermission()+":update")).thenReturn(false);
        assertThrows(RuntimeException.class,()->nativeUpload(kind,"no-update"));
        when(permissions.hasAnyPermissions(17L,kind.getPermission()+":update")).thenReturn(true);
        login(8L);assertThrows(RuntimeException.class,()->access.require(kind,8L,17L,id,kind.getPurpose(),FileActionCodes.READ,false,null));login(7L);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(20L),Set.of(20L)));
        assertThrows(RuntimeException.class,()->nativeUpload(kind,"placeholder"));when(scopes.resolveCurrent(any())).thenReturn(scope());
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L,4L,Set.of(20L),Set.of()));
        assertThrows(RuntimeException.class,()->tx.execute(ignored->access.require(kind,7L,17L,id,kind.getPurpose(),FileActionCodes.UPLOAD,true,3L)));
        when(scopes.lockAndRevalidate(any())).thenReturn(scope());when(projects.lock(any(),any(),any())).thenReturn(new ProjectAcceptanceContextApi.Context(20L,20L,0L,3L,"ARCHIVED"));
        assertThrows(RuntimeException.class,()->tx.execute(ignored->access.require(kind,7L,17L,id,kind.getPurpose(),FileActionCodes.UPLOAD,true,3L)));
    }
    String nativeTable(NativeAttachmentKind kind){return switch(kind){case CONFIGURATION->"imp_eng_configuration";case JOINT_TEST->"imp_eng_joint_test";case EXTERNAL_PROCUREMENT->"imp_eng_external_procurement";case OUTSOURCE->"res_outsource_request";case MATERIAL_REQUISITION->"imp_eng_material_requisition";case MATERIAL_EXCHANGE->"imp_eng_material_exchange";};}
    @ParameterizedTest @EnumSource(NativeAttachmentKind.class)
    void nativeReplacementAndDetachCannotReuseFrozenOldMaterialEvidence(NativeAttachmentKind kind) {
        nativeRequirement(kind);var first=nativeUpload(kind,"replace-slot");updateNative(kind,"first");
        byte[] bytes="Actual replacement file\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var uploads=context.getBean(FileUploadApplicationService.class);
        var initialized=uploads.initialize(new FileUploadInitializeCommand(7L,17L,"replace:init","ADD_VERSION",first.artifactId(),0,
                kind.getModule(),kind.getEntityType(),String.valueOf(nativeId(kind)),kind.getPurpose(),"replace-slot","native.txt",kind.getPurpose(),(long)bytes.length,"text/plain",null));
        var replacement=uploads.complete(new FileUploadCompleteCommand(7L,17L,"replace:complete",initialized.artifactId(),initialized.sessionId(),new MockMultipartFile("file","native.txt","text/plain",bytes),null));
        updateNative(kind,"replacement");assertEquals(2,nativeMaterials(kind).size());assertThrows(RuntimeException.class,()->nativeComplete(kind));
        assertNull(tx.execute(ignored->context.getBean(FileEvidenceApi.class).inspectDocumentByArtifact(7L,first.artifactId(),1)),"Old locator must not become the current file version");
        var old=nativeMaterials(kind).stream().filter(row->row.fileVersionNo()==1).findFirst().orElseThrow();
        context.getBean(DeliveryMaterialService.class).withdraw(old.id());assertTrue(nativeComplete(kind));
        context.getBean(FileLifecycleApplicationService.class).detach(new DetachFileReferenceCommand(7L,17L,"detach-native",replacement.referenceId(),1,
                kind.getModule(),kind.getEntityType(),String.valueOf(nativeId(kind)),kind.getPurpose(),"replace-slot","correction"));
        assertThrows(RuntimeException.class,()->nativeComplete(kind));
        assertFalse(tx.execute(ignored->context.getBean(FileEvidenceApi.class).inspectDocumentByArtifact(7L,replacement.artifactId(),2)).available());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_version",Integer.class));
    }
    @ParameterizedTest @EnumSource(NativeAttachmentKind.class)
    void nativeRawUrlCannotBypassCommonFileRegistration(NativeAttachmentKind kind) {
        long id=nativeId(kind);
        switch(kind){
            case CONFIGURATION -> {var row=context.getBean(ConfigurationMapper.class).selectById(id);var request=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(row,ConfigurationSaveReqVO.class);request.setConfigLogUrl("https://anonymous.example/unregistered.txt");assertThrows(RuntimeException.class,()->context.getBean(ConfigurationServiceImpl.class).updateConfiguration(request));request.setId(null);assertThrows(RuntimeException.class,()->context.getBean(ConfigurationServiceImpl.class).createConfiguration(request));}
            case JOINT_TEST -> {var row=context.getBean(JointTestMapper.class).selectById(id);var request=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(row,JointTestSaveReqVO.class);request.setEvidenceUrl("https://anonymous.example/unregistered.txt");assertThrows(RuntimeException.class,()->context.getBean(JointTestServiceImpl.class).updateJointTest(request));request.setId(null);assertThrows(RuntimeException.class,()->context.getBean(JointTestServiceImpl.class).createJointTest(request));}
            case EXTERNAL_PROCUREMENT -> {var row=context.getBean(ExternalProcurementMapper.class).selectById(id);var request=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(row,ExternalProcurementSaveReqVO.class);request.setAttachmentFiles("https://anonymous.example/unregistered.txt");assertThrows(RuntimeException.class,()->context.getBean(ExternalProcurementServiceImpl.class).updateExternalProcurement(request));request.setId(null);assertThrows(RuntimeException.class,()->context.getBean(ExternalProcurementServiceImpl.class).createExternalProcurement(request));}
            case OUTSOURCE -> {var row=context.getBean(OutsourceRequestMapper.class).selectById(id);var request=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(row,OutsourceRequestSaveReqVO.class);request.setAttachmentFiles("https://anonymous.example/unregistered.txt");assertThrows(RuntimeException.class,()->context.getBean(OutsourceRequestServiceImpl.class).updateOutsourceRequest(request));request.setId(null);assertThrows(RuntimeException.class,()->context.getBean(OutsourceRequestServiceImpl.class).createOutsourceRequest(request));}
            case MATERIAL_REQUISITION -> {var row=context.getBean(MaterialRequisitionMapper.class).selectById(id);var request=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(row,MaterialRequisitionSaveReqVO.class);request.setAttachmentFiles("https://anonymous.example/unregistered.txt");assertThrows(RuntimeException.class,()->context.getBean(MaterialRequisitionServiceImpl.class).updateMaterialRequisition(request));request.setId(null);assertThrows(RuntimeException.class,()->context.getBean(MaterialRequisitionServiceImpl.class).createMaterialRequisition(request));}
            case MATERIAL_EXCHANGE -> {var row=context.getBean(MaterialExchangeMapper.class).selectById(id);var request=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(row,MaterialExchangeSaveReqVO.class);request.setReasonFiles("https://anonymous.example/unregistered.txt");assertThrows(RuntimeException.class,()->context.getBean(MaterialExchangeServiceImpl.class).updateMaterialExchange(request));request.setId(null);assertThrows(RuntimeException.class,()->context.getBean(MaterialExchangeServiceImpl.class).createMaterialExchange(request));}
        }
        assertTrue(nativeMaterials(kind).isEmpty());assertEquals(0L,jdbc.queryForObject("SELECT version FROM "+nativeTable(kind)+" WHERE id=?",Long.class,id));
    }
    @Test void nativeUploadSizeAndDisguisedExecutableStayRejected() {
        var kind=NativeAttachmentKind.CONFIGURATION;var uploads=context.getBean(FileUploadApplicationService.class);
        assertThrows(RuntimeException.class,()->uploads.initialize(new FileUploadInitializeCommand(7L,17L,"oversize","CREATE_ARTIFACT",null,null,
                "IMP","configuration","30",kind.getPurpose(),"oversize","native.txt",kind.getPurpose(),5242881L,"text/plain",null)));
        byte[] executable=new byte[256];executable[0]='M';executable[1]='Z';
        var initialized=uploads.initialize(new FileUploadInitializeCommand(7L,17L,"cfg-mime","CREATE_ARTIFACT",null,null,
                "IMP","configuration","30",kind.getPurpose(),"cfg-mime","native.cfg",kind.getPurpose(),(long)executable.length,"application/octet-stream",null));
        assertThrows(RuntimeException.class,()->uploads.complete(new FileUploadCompleteCommand(7L,17L,"cfg-mime:complete",initialized.artifactId(),initialized.sessionId(),new MockMultipartFile("file","native.cfg","application/octet-stream",executable),null)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plt_file_artifact",Integer.class));assertTrue(nativeMaterials(kind).isEmpty());
    }

    @ParameterizedTest @EnumSource(NativeAttachmentKind.class)
    void nativeUnknownMimeTextCompletesAndRegistersWithItsActualOwner(NativeAttachmentKind kind) {
        var uploads=context.getBean(FileUploadApplicationService.class);
        byte[] content="hostname switch-1\ninterface ethernet1\n description 中文\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String[] names={"native.log","native.cfg","native.conf"};String[] mime={null,"application/octet-stream",""};
        for(int i=0;i<names.length;i++) {
            String slot="unknown-"+i;
            var initialized=uploads.initialize(new FileUploadInitializeCommand(7L,17L,slot+":init","CREATE_ARTIFACT",null,null,
                    kind.getModule(),kind.getEntityType(),String.valueOf(nativeId(kind)),kind.getPurpose(),slot,names[i],kind.getPurpose(),(long)content.length,mime[i],null));
            var complete=uploads.complete(new FileUploadCompleteCommand(7L,17L,slot+":complete",initialized.artifactId(),initialized.sessionId(),new MockMultipartFile("file",names[i],mime[i],content),null));
            assertEquals("text/plain",jdbc.queryForObject("SELECT detected_media_type FROM plt_file_version WHERE artifact_id=?",String.class,complete.artifactId()));
        }
        updateNative(kind,"text evidence");assertEquals(3,nativeMaterials(kind).size());
        for(var material:nativeMaterials(kind)){assertEquals(kind.getModule(),material.ownerModule());assertEquals(kind.getEntityType(),material.entityType());assertEquals(nativeId(kind),material.entityId());}
    }

    @Test void nativeConfigurationArchiveIsIdempotentAndDevicePortFailureRollsBackOwnerAndMaterial() {
        nativeUpload(NativeAttachmentKind.CONFIGURATION,"config-first");updateNative(NativeAttachmentKind.CONFIGURATION,"first");
        String locator=jdbc.queryForObject("SELECT file_locator FROM native_device_archive_fixture",String.class);
        var parsed=cn.iocoder.yudao.module.pms.asset.api.device.dto.NativeConfigurationFileLocator.parse(locator);
        assertEquals(30L,parsed.configurationId());assertEquals(nativeMaterials(NativeAttachmentKind.CONFIGURATION).getFirst().id(),parsed.materialId());
        updateNative(NativeAttachmentKind.CONFIGURATION,"retry");assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM native_device_archive_fixture",Integer.class));
        nativeUpload(NativeAttachmentKind.CONFIGURATION,"config-second");
        var logs=context.getBean(cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigLogRecordApi.class);
        doAnswer(call->{jdbc.update("INSERT INTO native_device_archive_fixture VALUES('rollback','rollback')");throw new IllegalStateException("device archive unavailable");}).when(logs).recordConfigLog(any());
        long version=jdbc.queryForObject("SELECT version FROM imp_eng_configuration WHERE id=30",Long.class);
        assertThrows(RuntimeException.class,()->updateNative(NativeAttachmentKind.CONFIGURATION,"rollback"));
        assertEquals(version,jdbc.queryForObject("SELECT version FROM imp_eng_configuration WHERE id=30",Long.class));
        assertNull(jdbc.queryForObject("SELECT config_log_url FROM imp_eng_configuration WHERE id=30",String.class));
        assertEquals(1,nativeMaterials(NativeAttachmentKind.CONFIGURATION).size());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM native_device_archive_fixture",Integer.class));
    }

    @Test @EnabledIfSystemProperty(named="native.delivery.browser",matches="true")
    void nativeChromiumSixPagesUploadCollectRetryWithdraw() throws Exception {
        for(var kind:NativeAttachmentKind.values())nativeRequirement(kind);
        try(var secured=new AnnotationConfigApplicationContext()){
            secured.setParent(context);secured.register(SecuredControllers.class,cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.ConfigurationController.class, cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.JointTestController.class, cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.ExternalProcurementController.class, cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.OutsourceRequestController.class, cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.MaterialRequisitionController.class, cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.MaterialExchangeController.class, cn.iocoder.yudao.module.pms.platform.controller.admin.delivery.DeliveryController.class, cn.iocoder.yudao.module.pms.platform.controller.admin.file.FileArtifactController.class);
            secured.getBeanFactory().registerSingleton("tickets",mock(FileAccessTicketService.class));secured.refresh();
            var json=tools.jackson.databind.json.JsonMapper.builder().addModule(new cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration().timestampSupportModuleBean()).build();
            var http=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(secured.getBean(cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.ConfigurationController.class), secured.getBean(cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.JointTestController.class), secured.getBean(cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.ExternalProcurementController.class), secured.getBean(cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.OutsourceRequestController.class), secured.getBean(cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.MaterialRequisitionController.class), secured.getBean(cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.MaterialExchangeController.class), secured.getBean(cn.iocoder.yudao.module.pms.platform.controller.admin.delivery.DeliveryController.class), secured.getBean(cn.iocoder.yudao.module.pms.platform.controller.admin.file.FileArtifactController.class))
                .setMessageConverters(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(json))
                .setControllerAdvice(new cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.BusinessModelContractAdvice(),new cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler("native-stage2",mock(cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi.class))).build();
            var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",28472),0);
            server.createContext("/",exchange->{
                try{
                    login(7L);String path=exchange.getRequestURI().getPath();byte[] content;int status=200;
                    if(path.startsWith("/fixture/")){
                        var parts=path.split("/");var kind=NativeAttachmentKind.valueOf(parts[3]);
                        if("reject".equals(parts[2])){jdbc.execute("ALTER TABLE plt_delivery_material ADD CONSTRAINT reject_native CHECK(source_entity_id<>"+nativeId(kind)+")");content="{}".getBytes();}
                        else if("allow".equals(parts[2])){jdbc.execute("ALTER TABLE plt_delivery_material DROP CHECK reject_native");content="{}".getBytes();}
                        else{
                            var evidence=new LinkedHashMap<String,Object>();evidence.put("materials",nativeMaterials(kind));
                            evidence.put("projectMaterials",context.getBean(PlatformDeliveryMaterialApi.class).listByProject(20L));
                            evidence.put("version",jdbc.queryForObject("SELECT version FROM "+nativeTable(kind)+" WHERE id=?",Long.class,nativeId(kind)));
                            evidence.put("remark",jdbc.queryForObject("SELECT remark FROM "+nativeTable(kind)+" WHERE id=?",String.class,nativeId(kind)));
                            evidence.put("files",jdbc.queryForObject("SELECT COUNT(DISTINCT a.id) FROM plt_file_artifact a JOIN plt_file_reference r ON r.artifact_id=a.id WHERE r.owner_context=? AND r.object_type=? AND r.object_id=?",Long.class,kind.getModule(),kind.getEntityType(),String.valueOf(nativeId(kind))));
                            evidence.put("completed",nativeComplete(kind));content=json.writeValueAsBytes(evidence);
                        }
                    }else{
                        var request=browserRequest(exchange.getRequestMethod(),exchange.getRequestURI().toString(),exchange.getRequestHeaders().getFirst("Content-Type"),exchange.getRequestBody().readAllBytes());
                        exchange.getRequestHeaders().forEach((name,values)->values.forEach(value->request.header(name,value)));
                        var response=http.perform(request).andReturn().getResponse();status=response.getStatus();content=response.getContentAsByteArray();
                    }
                    exchange.getResponseHeaders().set("Content-Type","application/json;charset=UTF-8");exchange.sendResponseHeaders(status,content.length);exchange.getResponseBody().write(content);
                }catch(Exception error){error.printStackTrace();byte[] failure=json.writeValueAsBytes(Map.of("code",500,"msg",error.getClass().getSimpleName()));exchange.sendResponseHeaders(500,failure.length);exchange.getResponseBody().write(failure);}
                finally{exchange.close();TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
            });server.start();
            try{
                var repository=java.nio.file.Path.of("").toAbsolutePath();while(repository!=null&&!java.nio.file.Files.isDirectory(repository.resolve("scripts/tests")))repository=repository.getParent();
                var output=repository.resolve(".run/native-delivery-stage2-20261006/browser-process.log");
                var process=new ProcessBuilder("python3",repository.resolve("scripts/tests/run_native_stage2_delivery_browser.py").toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
                if(!process.waitFor(180,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();fail("Native browser timeout");}
                System.out.println(java.nio.file.Files.readString(output));assertEquals(0,process.exitValue(),"See stage2 browser evidence");
            }finally{server.stop(0);}
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
