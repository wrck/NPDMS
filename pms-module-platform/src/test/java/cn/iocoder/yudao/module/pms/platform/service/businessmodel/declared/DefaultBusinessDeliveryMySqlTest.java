package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkServiceImpl;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.infra.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Scope;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.*;
import cn.iocoder.yudao.module.pms.platform.service.command.*;
import cn.iocoder.yudao.module.pms.platform.service.delivery.*;
import cn.iocoder.yudao.module.pms.platform.service.file.*;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.*;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApiImpl;
import com.baomidou.mybatisplus.annotation.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual default service + role/menu permission mappers + file/material MySQL. Project/storage are deterministic ports. */
@EnabledIfSystemProperty(named="npdms.declared.exclusive",matches="true")
class DefaultBusinessDeliveryMySqlTest {
    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity(proxyTargetClass=true)
    static class Infrastructure { }
    DeclaredBusinessRuntimePersistenceTest.Runtime runtime;
    AnnotationConfigApplicationContext context;
    DefaultBusinessDeliveryService deliveries;
    DefaultBusinessDeliveryFilePolicy filePolicy;
    ProjectAcceptanceContextApi projects=mock(ProjectAcceptanceContextApi.class);
    FileStorageReceiptApi storage=mock(FileStorageReceiptApi.class);
    TransactionTemplate tx;
    EntityRef first,second;
    Map<String,byte[]> stored=new HashMap<>();
    @BeforeEach void start() throws Exception {
        runtime=new DeclaredBusinessRuntimePersistenceTest.Runtime(true,0,false,false,null,false,true);
        DeclaredBusinessRuntimePersistenceTest.initializeExclusiveSchema(runtime);
        schema(DefaultDeliverySecondDO.class);
        for(Class<?> type:List.of(FileArtifactDO.class,FileVersionDO.class,FileReferenceDO.class,FileUploadSessionDO.class,
                DeliveryMaterialDO.class,DeliveryRequirementDO.class,DeliveryFulfillmentDO.class,DeliverySubmissionDO.class)) schema(type);
        runtime.jdbc.execute("CREATE UNIQUE INDEX delivery_identity ON plt_delivery_material(tenant_id,source_identity_key)");
        runtime.jdbc.execute("CREATE UNIQUE INDEX file_slot ON plt_file_reference(tenant_id,owner_context,object_type,object_id,purpose_code,reference_key)");
        for(int i=0;i<3;i++) {
            long menu=981001+i;var permission=List.of("it:second:query","it:second:create","it:second:update").get(i);
            runtime.jdbc.update("INSERT INTO system_menu(id,name,permission,type,sort,parent_id,status) VALUES(?, ?, ?,3,0,0,0)",menu,"Second",permission);
            runtime.jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(701,?,7)",menu);
        }
        var config=runtime.sessions.getConfiguration();
        var mapperTypes=List.of(FileArtifactMapper.class,FileVersionMapper.class,FileReferenceMapper.class,FileUploadSessionMapper.class,
                DeliveryMaterialMapper.class,DeliveryRequirementMapper.class,DeliveryFulfillmentMapper.class,DeliverySubmissionMapper.class);
        mapperTypes.forEach(config::addMapper);
        var resolver=new PathMatchingResourcePatternResolver();
        for(String directory:List.of("file","delivery")) for(var resource:resolver.getResources("classpath*:mapper/"+directory+"/*.xml")) {
            try(var input=resource.getInputStream()){new XMLMapperBuilder(input,config,resource.toString(),config.getSqlFragments()).parse();}
        }
        context=new AnnotationConfigApplicationContext();context.register(Infrastructure.class);
        context.getBeanFactory().registerSingleton("dataSource",runtime.source);
        context.registerBean(DataSourceTransactionManager.class,()->new DataSourceTransactionManager(runtime.source));
        for(var type:mapperTypes) context.getBeanFactory().registerSingleton(type.getSimpleName(),runtime.sessions.getMapper(type));
        for(var type:List.of(cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformIdempotencyRecordMapper.class,
                cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOperationAuditMapper.class,
                cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOutboxEventMapper.class))
            context.getBeanFactory().registerSingleton(type.getSimpleName(),runtime.sessions.getMapper(type));
        var api=new PermissionApiImpl();org.springframework.test.util.ReflectionTestUtils.setField(api,"permissionService",runtime.context.getBean(cn.iocoder.yudao.module.system.service.permission.PermissionServiceImpl.class));
        context.getBeanFactory().registerSingleton("ss",new SecurityFrameworkServiceImpl(api));
        context.getBeanFactory().registerSingleton("callers",new TenantCallerContext());
        context.getBeanFactory().registerSingleton("scopes",runtime.projectApi);
        context.getBeanFactory().registerSingleton("projects",projects);
        context.getBeanFactory().registerSingleton("storage",storage);
        var contributors=runtime.context.getBeanProvider(BusinessModelContributor.class);
        var catalog=new BusinessModelRegistry(contributors);var persistence=new BusinessEntityPersistenceRegistry(contributors);
        context.getBeanFactory().registerSingleton("catalog",catalog);
        context.getBeanFactory().registerSingleton("guard",runtime.guard);
        context.getBeanFactory().registerSingleton("persistence",persistence);
        context.getBeanFactory().registerSingleton("bridge",new DeclaredBusinessDeliveryBridge(persistence,runtime.dispatcher,runtime.access,runtime.projectApi,projects));
        when(projects.inspect(any())).thenAnswer(call->{var query=call.getArgument(0,ProjectAcceptanceContextApi.Query.class);return new ProjectAcceptanceContextApi.Context(query.projectId(),query.projectId(),0L,1L,"ACTIVE");});
        when(projects.lock(any(),any(),any())).thenAnswer(call->{var query=call.getArgument(0,ProjectAcceptanceContextApi.Query.class);return new ProjectAcceptanceContextApi.Context(query.projectId(),query.projectId(),0L,1L,"ACTIVE");});
        when(storage.store(any())).thenAnswer(call->{var command=call.getArgument(0,FileStorageStoreCommand.class);stored.put(command.storageOperationId(),command.validatedContent());
            return new FileStorageReceipt(command.storageOperationId(),(long)stored.size(),command.name(),command.mediaType(),command.validatedContent().length);});
        context.register(PlatformCommandExecutionApiImpl.class,PlatformTransactionalOutboxWriter.class,OperationAuditApiImpl.class,
                DefaultBusinessDeliveryFilePolicy.class,DefaultBusinessDeliveryService.class,DefaultBusinessDeliveryController.class,FileEvidenceService.class);
        context.registerBean(FileBusinessObjectPolicyRegistry.class,()->new FileBusinessObjectPolicyRegistry(List.of(context.getBean(DefaultBusinessDeliveryFilePolicy.class))));
        context.registerBean(BoundedMultipartReader.class,BoundedMultipartReader::new);
        context.registerBean(FileContentPolicyService.class,()->new FileContentPolicyService(context.getBean(BoundedMultipartReader.class),List.of(),false));
        context.registerBean(FileUploadApplicationService.class,()->new FileUploadApplicationService(context.getBean(FileUploadSessionMapper.class),context.getBean(FileArtifactMapper.class),
                context.getBean(FileVersionMapper.class),context.getBean(FileReferenceMapper.class),context.getBean(FileBusinessObjectPolicyRegistry.class),context.getBean(BoundedMultipartReader.class),
                context.getBean(FileContentPolicyService.class),storage,new FileEventFactory(),context.getBean(PlatformCommandExecutionApiImpl.class),context.getBean(OperationAuditApiImpl.class),Duration.ofMinutes(15)));
        context.refresh();deliveries=context.getBean(DefaultBusinessDeliveryService.class);filePolicy=context.getBean(DefaultBusinessDeliveryFilePolicy.class);
        runtime.context.getBeanFactory().registerSingleton("defaultBusinessDelivery",deliveries);
        tx=new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));login(7,880001);
        first=create("declaredNote",99,"first");second=create("secondDelivery",99,"second");
        assertTrue(catalog.require("IT","declaredNote").capabilities().isEmpty());assertTrue(catalog.require("IT","secondDelivery").capabilities().isEmpty());
        assertEquals(0,runtime.context.getBeanNamesForType(cn.iocoder.yudao.module.pms.platform.support.service.AbstractBusinessApplicationService.class).length);
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();TenantContextHolder.clear();if(context!=null)context.close();if(runtime!=null)runtime.close();}
    EntityRef create(String type,long project,String title) {
        return runtime.dispatcher.dispatch(new BusinessOperationRequest("create",1,null,"IT",type,Map.of("projectRef",project,"title",title),UUID.randomUUID().toString(),null,OperationEntryKind.INDEPENDENT,null)).entityRef();
    }
    static void login(long tenant,long user){TenantContextHolder.setTenantId(tenant);var principal=new LoginUser();principal.setId(user);principal.setTenantId(tenant);principal.setUserType(2);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));}
    Scope scope(EntityRef ref,String type){return new Scope(99L,ref.entityType().equals("declaredNote")?"IT_DECLARED_NOTE":"IT_SECOND_DELIVERY",ref.entityId().toString(),type);}
    MockMultipartFile file(String content){return new MockMultipartFile("file","evidence.txt","text/plain",content.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi.Record upload(EntityRef ref,String type,String content){return deliveries.upload(scope(ref,type),file(content),UUID.randomUUID().toString());}
    long count(String table){return runtime.jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    @Test void twoOrdinaryBusinessesUseOneCrudAndLatestCompletionWithoutAdapters() {
        assertFalse(deliveries.completion(scope(first,"REPORT")).completed());
        var one=upload(first,"REPORT","first upload\n");var two=upload(second,"REPORT","second upload\n");
        assertEquals(2,count("plt_delivery_material"));assertEquals(2,count("plt_file_version"));
        assertEquals(0,count("plt_delivery_requirement"));assertEquals(0,count("plt_delivery_submission"));
        assertEquals(one,deliveries.completion(scope(first,"REPORT")).latest());assertEquals(two,deliveries.completion(scope(second,"REPORT")).latest());
        assertEquals(Set.of(one.id(),two.id()),new HashSet<>(deliveries.list(99L,"REPORT",null,null,1,20).getList().stream().map(record->record.id()).toList()));
        assertEquals(List.of(one),deliveries.list(99L,"REPORT","IT_DECLARED_NOTE",first.entityId().toString(),1,20).getList());
        assertFalse(deliveries.completion(scope(first,"PHOTO")).completed());
        var renamed=deliveries.edit(Long.valueOf(one.id()),one.version(),"Edited title");assertEquals(one.uploadedAt(),renamed.uploadedAt());assertEquals(1L,renamed.version());
        deliveries.delete(Long.valueOf(one.id()),renamed.version());assertFalse(deliveries.completion(scope(first,"REPORT")).completed());
        assertEquals(1,count("plt_file_version")-1);assertEquals(2,count("plt_delivery_material"));
        var newer=upload(first,"REPORT","reupload\n");assertEquals(newer.id(),deliveries.completion(scope(first,"REPORT")).latest().id());
        var newest=upload(first,"REPORT","newest\n");runtime.jdbc.update("UPDATE plt_delivery_material SET create_time=? WHERE id IN (?,?)",newer.uploadedAt(),newer.id(),newest.id());
        assertEquals(newest.id(),deliveries.completion(scope(first,"REPORT")).latest().id());
        assertFalse(deliveries.completion(scope(first,"report")).completed(),"Four-field equality must remain exact under the MySQL CI collation");
        var lower=upload(first,"report","lowercase type\n");
        assertEquals(lower.id(),deliveries.completion(scope(first,"report")).latest().id());
        assertEquals(newest.id(),deliveries.completion(scope(first,"REPORT")).latest().id());
        assertEquals("first",runtime.jdbc.queryForObject("SELECT title FROM it_declared_note WHERE id=?",String.class,first.entityId()));
        assertEquals(0L,runtime.jdbc.queryForObject("SELECT version FROM it_declared_note WHERE id=?",Long.class,first.entityId()));
    }
    @Test void retriesPreserveOneActualRecordAndDifferentBytesCannotReplaySuccess() {
        String key="same-upload";var scope=scope(second,"REPORT");
        var first=deliveries.upload(scope,file("same\n"),key);assertEquals(first,deliveries.upload(scope,file("same\n"),key));
        assertThrows(RuntimeException.class,()->deliveries.upload(scope,file("changed\n"),key));
        assertEquals(1,count("plt_delivery_material"));assertEquals(1,count("plt_file_version"));
        deliveries.delete(Long.valueOf(first.id()),first.version());
        assertThrows(BusinessContractException.class,()->deliveries.upload(scope,file("same\n"),key));
        assertFalse(deliveries.completion(scope).completed());assertEquals(1,count("plt_delivery_material"));
    }
    @Test void historyReadsExistingMaterialRowsWithoutSnapshotsAndNeverCompletesWithdrawnUploads() {
        var older=upload(first,"REPORT","older\n");var latest=upload(first,"REPORT","latest\n");
        assertEquals(2,deliveries.history(scope(first,"REPORT"),1,20).getTotal());
        deliveries.delete(Long.valueOf(latest.id()),latest.version());
        var history=deliveries.history(scope(first,"REPORT"),1,20);assertEquals(2,history.getTotal());
        assertEquals("WITHDRAWN",history.getList().stream().filter(row->row.id().equals(latest.id())).findFirst().orElseThrow().status());
        assertFalse(history.getList().stream().filter(row->row.id().equals(latest.id())).findFirst().orElseThrow().editable());
        assertEquals(older.id(),deliveries.completion(scope(first,"REPORT")).latest().id());
        assertThrows(RuntimeException.class,()->deliveries.file(Long.valueOf(latest.id())));
        deliveries.delete(Long.valueOf(older.id()),older.version());assertFalse(deliveries.completion(scope(first,"REPORT")).completed());
        assertEquals(2,count("plt_delivery_material"));assertEquals(0,deliveries.history(scope(second,"REPORT"),1,20).getTotal());
    }
    @Test void failedOrPendingOrUnavailableFilesDoNotCompleteAndLatestEffectiveFallbackIsStable() {
        assertThrows(RuntimeException.class,()->deliveries.upload(scope(first,"REPORT"),new MockMultipartFile("file","bad.txt","text/plain",new byte[]{0,1,2,3}),"bad"));
        assertEquals(0,count("plt_delivery_material"));assertEquals(0,count("plt_file_version"));assertFalse(deliveries.completion(scope(first,"REPORT")).completed());
        var old=upload(first,"REPORT","old\n");var newer=upload(first,"REPORT","new\n");
        runtime.jdbc.update("UPDATE plt_file_version SET availability_status_code='UNAVAILABLE' WHERE artifact_id=?",newer.fileArtifactId());
        assertEquals(old.id(),deliveries.completion(scope(first,"REPORT")).latest().id());
        runtime.jdbc.update("UPDATE plt_file_reference SET status_code='WITHDRAWN' WHERE id=?",old.fileReferenceId());
        assertFalse(deliveries.completion(scope(first,"REPORT")).completed());
    }
    @Test void forgedProjectEntityTypeTenantAndManageScopeAreDeniedWithoutWrites() {
        assertThrows(RuntimeException.class,()->deliveries.upload(new Scope(101L,"IT_DECLARED_NOTE",first.entityId().toString(),"REPORT"),file("forged\n"),"forged"));
        assertThrows(RuntimeException.class,()->deliveries.upload(new Scope(99L,"UNKNOWN",first.entityId().toString(),"REPORT"),file("x"),"unknown"));
        assertThrows(RuntimeException.class,()->deliveries.upload(new Scope(99L,"IT_SECOND_DELIVERY","999999","REPORT"),file("x"),"wrong-entity"));
        assertEquals(0,count("plt_delivery_material"));login(8,880001);
        assertThrows(RuntimeException.class,()->deliveries.upload(scope(first,"REPORT"),file("tenant"),"tenant"));login(7,880001);
        doAnswer(call->{var query=call.getArgument(0,ProjectCurrentScopeQuery.class);return new ProjectScopeResult(99L,1L,ProjectScopeApi.ACTION_VIEW.equals(query.actionCode())?Set.of(99L):Set.of(),Set.of());}).when(runtime.projectApi).resolveCurrent(any());
        assertThrows(RuntimeException.class,()->upload(first,"REPORT","view only"));assertFalse(deliveries.completion(scope(first,"REPORT")).completed());
        assertEquals(0,count("plt_file_upload_session"));
    }
    @Test void defaultBusinessPermissionsAreRecheckedForUploadsEditsDeletesAndProjectCollection() {
        var firstRecord=upload(first,"REPORT","first\n");var secondRecord=upload(second,"REPORT","second\n");
        login(7,880002);assertEquals(List.of(firstRecord),deliveries.list(99L,"REPORT",null,null,1,20).getList());
        assertThrows(RuntimeException.class,()->deliveries.get(Long.valueOf(secondRecord.id())));
        assertThrows(RuntimeException.class,()->deliveries.history(scope(second,"REPORT"),1,20));
        assertEquals(List.of(firstRecord.id()),deliveries.history(scope(first,"REPORT"),1,20).getList().stream().map(row->row.id()).toList());
        assertTrue(deliveries.history(scope(first,"REPORT"),1,20).getList().stream().noneMatch(row->row.editable()));
        assertThrows(RuntimeException.class,()->upload(first,"REPORT","reader write"));
        assertThrows(RuntimeException.class,()->deliveries.edit(Long.valueOf(firstRecord.id()),0L,"reader"));
        assertThrows(RuntimeException.class,()->deliveries.delete(Long.valueOf(firstRecord.id()),0L));login(7,880001);
        runtime.jdbc.update("DELETE FROM system_role_menu WHERE role_id=701 AND menu_id=981003");
        assertThrows(RuntimeException.class,()->upload(second,"REPORT","revoked"));
        assertEquals(2,count("plt_delivery_material"));
    }
    @Test void archivedRequirementsAnyHistoricalUseAndSubmissionsProtectEditAndDelete() {
        for(String protection:List.of("archive","requirement","use","submission")) {
            var row=upload(first,"REPORT",protection+"\n");
            switch(protection) {
                case "archive"->runtime.jdbc.update("UPDATE plt_delivery_material SET archive_status='ARCHIVED' WHERE id=?",row.id());
                case "requirement"->runtime.jdbc.update("UPDATE plt_delivery_material SET requirement_id=99 WHERE id=?",row.id());
                case "use"->runtime.jdbc.update("INSERT INTO plt_delivery_fulfillment(tenant_id,requirement_id,material_id,status,deleted) VALUES(7,99,?,'WITHDRAWN',0)",row.id());
                case "submission"->runtime.jdbc.update("INSERT INTO plt_delivery_submission(tenant_id,material_ids_json,status,deleted) VALUES(7,?,'WITHDRAWN',0)","["+row.id()+"]");
            }
            assertThrows(RuntimeException.class,()->deliveries.edit(Long.valueOf(row.id()),0L,"changed"),protection);
            assertThrows(RuntimeException.class,()->deliveries.delete(Long.valueOf(row.id()),0L),protection);
            assertEquals(0L,deliveries.get(Long.valueOf(row.id())).version());
        }
    }
    @Test void staleCasScopeChangeClosedProjectAndMovedOwnerCannotMutateOrReuseFile() {
        var row=upload(first,"REPORT","protected\n");deliveries.edit(Long.valueOf(row.id()),0L,"edited");
        assertThrows(RuntimeException.class,()->deliveries.delete(Long.valueOf(row.id()),0L));
        doReturn(new ProjectAcceptanceContextApi.Context(99L,99L,0L,1L,"CLOSED")).when(projects).inspect(any());
        doReturn(new ProjectAcceptanceContextApi.Context(99L,99L,0L,1L,"CLOSED")).when(projects).lock(any(),any(),any());
        assertThrows(RuntimeException.class,()->deliveries.delete(Long.valueOf(row.id()),1L));
        doReturn(new ProjectAcceptanceContextApi.Context(99L,99L,0L,1L,"ACTIVE")).when(projects).inspect(any());
        doReturn(new ProjectAcceptanceContextApi.Context(99L,99L,0L,1L,"ACTIVE")).when(projects).lock(any(),any(),any());
        doReturn(new ProjectScopeResult(99L,2L,Set.of(99L),Set.of())).when(runtime.projectApi).lockAndRevalidate(any());
        assertThrows(RuntimeException.class,()->upload(first,"REPORT","tree changed"));
        doReturn(new ProjectScopeResult(99L,1L,Set.of(99L),Set.of())).when(runtime.projectApi).lockAndRevalidate(any());
        var file=deliveries.file(Long.valueOf(row.id()));runtime.jdbc.update("UPDATE it_declared_note SET project_ref=101 WHERE id=?",first.entityId());
        assertThrows(RuntimeException.class,()->deliveries.get(Long.valueOf(row.id())));
        assertThrows(RuntimeException.class,()->filePolicy.inspect(new FileBusinessObjectPolicyQuery(7L,880001L,"PLT",DefaultBusinessDeliveryService.FILE_OBJECT_TYPE,file.objectId(),file.purposeCode(),file.referenceKey(),FileActionCodes.READ)));
    }
    @Test void publicMethodPermissionsAndPendingUploadCannotReportComplete() {
        var controller=context.getBean(DefaultBusinessDeliveryController.class);
        runtime.jdbc.update("DELETE FROM system_role_menu WHERE role_id=701 AND menu_id=980006");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.list(99L,"REPORT",null,null,1,20));
        runtime.jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(701,980006,7)");
        var initialized=context.getBean(FileUploadApplicationService.class).initialize(new cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitializeCommand(
                7L,880001L,"pending-initialize","CREATE_ARTIFACT",null,null,"PLT",DefaultBusinessDeliveryService.FILE_OBJECT_TYPE,
                "99:IT:declaredNote:"+first.entityId(),"REPORT","pending-slot","pending.txt","DOCUMENT",10L,"text/plain",null));
        assertNotNull(initialized.sessionId());assertEquals(1,count("plt_file_upload_session"));assertFalse(deliveries.completion(scope(first,"REPORT")).completed());
        runtime.jdbc.update("DELETE FROM system_role_menu WHERE role_id=701 AND menu_id=980001");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.upload(99L,"IT_DECLARED_NOTE",first.entityId().toString(),"REPORT",file("blocked"),"blocked"));
        assertEquals(0,count("plt_delivery_material"));assertEquals(0,count("plt_file_version"));
    }
    void schema(Class<?> type) {
        String table=type.getAnnotation(TableName.class).value();var columns=new LinkedHashMap<String,String>();
        for(Class<?> current=type;current!=null && current!=Object.class;current=current.getSuperclass()) for(Field field:current.getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers()) || field.isSynthetic())continue;var annotation=field.getAnnotation(TableField.class);if(annotation!=null && !annotation.exist())continue;
            String name=annotation!=null && !annotation.value().isBlank()?annotation.value():field.getName().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
            Class<?> kind=field.getType();String sql=kind==Long.class || kind==Integer.class?"BIGINT":kind==Boolean.class?"BOOLEAN":kind==LocalDateTime.class?"DATETIME(3)":"LONGTEXT";
            if(Set.of("source_identity_key","owner_context","object_type","object_id","purpose_code","reference_key").contains(name))sql="VARCHAR(128)";
            if(name.equals("id"))sql="BIGINT AUTO_INCREMENT PRIMARY KEY";else if(name.equals("deleted"))sql="BOOLEAN DEFAULT FALSE";
            else if(name.equals("create_time") || name.equals("update_time"))sql="DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)";
            columns.putIfAbsent(name,"`"+name+"` "+sql);
        }
        runtime.jdbc.execute("DROP TABLE IF EXISTS "+table);runtime.jdbc.execute("CREATE TABLE "+table+" ("+String.join(",",columns.values())+")");
    }
}
