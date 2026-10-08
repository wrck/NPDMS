package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalAttemptDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformIdempotencyRecordMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.BusinessDeletionProtectionMapper;
import cn.iocoder.yudao.module.pms.platform.service.business.DirectBusinessOwners;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.*;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import cn.iocoder.yudao.module.system.api.permission.PermissionApiImpl;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL and inherited HTTP CRUD. Project/lifecycle/storage facts remain the existing deterministic fixture ports. */
@EnabledIfSystemProperty(named="npdms.declared.exclusive",matches="true")
class DirectBusinessCrudMySqlTest {
    @Data @EqualsAndHashCode(callSuper=true) @TableName("it_direct_note")
    @ProjectBusinessModel(ownerModule="IT",entityType="directNote",stableCode="IT_DIRECT_NOTE",name="Direct note",permissionPrefix="it:direct-note")
    public static class Note extends BaseProjectBusinessEntity {
        @BusinessModelField @NotBlank private String title;
        @BusinessModelField private String remark;
    }
    @Data @EqualsAndHashCode(callSuper=true) @TableName("it_direct_other")
    @ProjectBusinessModel(ownerModule="IT",entityType="directOther",stableCode="IT_DIRECT_OTHER",name="Direct other",permissionPrefix="it:direct-other")
    public static class Other extends BaseProjectBusinessEntity { @BusinessModelField @NotBlank private String description; }
    @TableName("it_direct_special")
    @ProjectBusinessModel(ownerModule="IT",entityType="directSpecial",stableCode="IT_DIRECT_SPECIAL",name="Direct special",permissionPrefix="it:direct-special")
    public static class Special extends Note { }
    public interface NoteMapper extends BusinessMapper<Note> { }
    public interface OtherMapper extends BusinessMapper<Other> { }
    public interface SpecialMapper extends BusinessMapper<Special> {
        List<Special> selectDisplayPage(@Param("query") BusinessReadQuery query);
        long countDisplayPage(@Param("query") BusinessReadQuery query);
    }
    public static class NoteService extends DefaultProjectBusinessService<NoteMapper,Note> { }
    public static class OtherService extends DefaultProjectBusinessService<OtherMapper,Other> { }
    public static class SpecialService extends DefaultProjectBusinessService<SpecialMapper,Special> {
        @Override protected void beforeCreate(Special entity) { entity.setTitle(entity.getTitle().toUpperCase(Locale.ROOT)); }
        @Override protected PageResult<Special> selectPage(BusinessReadQuery query) { return new PageResult<>(mapper.selectDisplayPage(query),mapper.countDisplayPage(query)); }
        @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix) {
            return List.of(new BusinessOperationDescriptor("mark",1,"Mark",BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,prefix+":mark"));
        }
        public BusinessOperationReceipt mark(Long id,Long version,String key) { return change("mark",id,version,key,Map.of(),row->row.setTitle(row.getTitle()+"!")); }
    }
    @RestController @RequestMapping("/api/v1/pms/it-direct-notes")
    public static class NoteController extends ProjectBusinessController<NoteService,Note> { }
    @RestController @RequestMapping("/api/v1/pms/it-direct-others")
    public static class OtherController extends ProjectBusinessController<OtherService,Other> { }
    @RestController @RequestMapping("/api/v1/pms/it-direct-specials")
    public static class SpecialController extends ProjectBusinessController<SpecialService,Special> {
        @PostMapping("/{id}/mark") public CommonResult<BusinessOperationReceipt> mark(@PathVariable Long id,@RequestBody WriteBody input) {
            return CommonResult.success(service.mark(id,input.version(),input.idempotencyKey()));
        }
    }
    DefaultBusinessDeliveryMySqlTest fixture;
    NoteService notes;OtherService others;SpecialService specials;MockMvc mvc;
    jakarta.validation.ValidatorFactory validators;
    @BeforeEach void start() throws Exception {
        fixture=new DefaultBusinessDeliveryMySqlTest();fixture.start();var runtime=fixture.runtime;var context=fixture.context;
        for(var type:List.of(Note.class,Other.class,Special.class,ApprovalAttemptDO.class))fixture.schema(type);
        var configuration=runtime.sessions.getConfiguration();
        for(var mapper:List.of(NoteMapper.class,OtherMapper.class,SpecialMapper.class,BusinessDeletionProtectionMapper.class)) {
            if(!configuration.hasMapper(mapper))configuration.addMapper(mapper);
            context.getBeanFactory().registerSingleton(mapper.getName(),runtime.sessions.getMapper(mapper));
        }
        var xml=new ClassPathResource("direct-business/DirectSpecialMapper.xml");
        try(var input=xml.getInputStream()){new XMLMapperBuilder(input,configuration,xml.toString(),configuration.getSqlFragments()).parse();}
        var permissions=new PermissionApiImpl();ReflectionTestUtils.setField(permissions,"permissionService",runtime.context.getBean(cn.iocoder.yudao.module.system.service.permission.PermissionServiceImpl.class));
        long menu=982000;
        for(String prefix:List.of("it:direct-note","it:direct-other","it:direct-special"))for(String action:List.of("query","create","update","delete","mark")) {
            runtime.jdbc.update("INSERT INTO system_menu(id,name,permission,type,sort,parent_id,status) VALUES(?,?,?,3,0,0,0)",++menu,"Direct business",prefix+":"+action);
            runtime.jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(701,?,7)",menu);
            if(action.equals("query"))runtime.jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(702,?,7)",menu);
        }
        validators=Validation.buildDefaultValidatorFactory();var caller=context.getBean(BusinessCallerContext.class);
        var ports=new BusinessDefaults(caller,new BusinessPermissions(permissions,caller),new ProjectBusinessScopeAccess(runtime.projectApi),validators.getValidator(),fixture.tx,
                new PlatformOperationExecutionStore(runtime.sessions.getMapper(PlatformIdempotencyRecordMapper.class)),context.getBean(OperationAuditApi.class),
                new OutboxBusinessEventPort(context.getBean(PlatformTransactionalOutboxWriter.class)),
                List.of(new PlatformBusinessDeletionGuard(runtime.sessions.getMapper(BusinessDeletionProtectionMapper.class))),()->fixture.deliveries);
        context.getBeanFactory().registerSingleton("directDefaults",ports);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory.class);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.service.file.ExistingFileVersionAttachmentService.class);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.service.file.FileArtifactApiImpl.class,()->new cn.iocoder.yudao.module.pms.platform.service.file.FileArtifactApiImpl(
                context.getBean(cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry.class),
                context.getBean(cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper.class),context.getBean(cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper.class),
                context.getBean(cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper.class),context.getBean(cn.iocoder.yudao.module.pms.platform.service.file.ExistingFileVersionAttachmentService.class),null,permissions,null,null,null));
        context.registerBean(NoteService.class);context.registerBean(OtherService.class);context.registerBean(SpecialService.class);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.controller.admin.business.ProjectBusinessDeliveryController.class);
        context.registerBean(NoteController.class);context.registerBean(OtherController.class);context.registerBean(SpecialController.class);
        var owners=new DirectBusinessOwners(context.getBeanProvider(ResolvableType.forClass(ProjectBusinessService.class)),caller,runtime.projectApi,fixture.projects);
        context.getBeanFactory().registerSingleton("directBusinessOwners",owners);
        notes=context.getBean(NoteService.class);others=context.getBean(OtherService.class);specials=context.getBean(SpecialService.class);
        installRealFormCapabilities(permissions);
        mvc=MockMvcBuilders.standaloneSetup(context.getBean(NoteController.class),context.getBean(OtherController.class),context.getBean(SpecialController.class),context.getBean(cn.iocoder.yudao.module.pms.platform.controller.admin.business.ProjectBusinessDeliveryController.class)).build();
        // New direct business APIs require their own permissions, not the old model workbench gate.
        runtime.jdbc.update("DELETE rm FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id WHERE rm.role_id=701 AND m.permission IN ('pms:business-model:operate','pms:business-model:query')");
        assertTrue(context.getBean(BusinessModelCatalog.class).find("IT","directNote").isEmpty());
        assertTrue(context.getBean(BusinessModelCatalog.class).find("IT","directOther").isEmpty());
    }
    /** The inherited page always reads /form: exercise production services, not empty API stubs. */
    private void installRealFormCapabilities(PermissionApiImpl permissions) throws Exception {
        var runtime=fixture.runtime;var context=fixture.context;
        String ddl=java.nio.file.Files.readString(java.nio.file.Path.of("../sql/migrations/V248__entity_capabilities_and_requirement_revision.sql"));
        for(String table:List.of("plt_entity_extension_value","plt_entity_form_binding","plt_entity_extension_definition"))runtime.jdbc.execute("DROP TABLE IF EXISTS "+table);
        for(String table:List.of("plt_entity_extension_definition","plt_entity_extension_value","plt_entity_form_binding")) {
            var match=java.util.regex.Pattern.compile("CREATE TABLE "+table+" .*?;",java.util.regex.Pattern.DOTALL).matcher(ddl);
            assertTrue(match.find(),"Authoritative capability DDL missing: "+table);runtime.jdbc.execute(match.group());
        }
        for(var mapper:List.of(cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.EntityCapabilityMapper.class,
                cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateMapper.class,
                cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper.class,
                cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.PlatformDynamicFormInstanceMapper.class))
            context.getBeanFactory().registerSingleton(mapper.getSimpleName(),runtime.sessions.getMapper(mapper));
        context.getBeanFactory().registerSingleton("directPermissions",permissions);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.support.revision.InheritedRevisionAdapterFactory.class);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.service.entity.EntityProviderRegistry.class);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.service.entity.EntityExtensionService.class);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormSchemaService.class);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormBusinessObjectPolicyProviderRegistry.class);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormBusinessInstanceService.class);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormBusinessInstanceApiImpl.class);
        context.registerBean(cn.iocoder.yudao.module.pms.platform.service.entity.EntityFormService.class);
    }
    @Test void inheritedFormUsesRealExtensionStorageAndRejectsStaleWrites() throws Exception {
        long id=createHttp("/api/v1/pms/it-direct-notes","{\"projectId\":99,\"title\":\"Form\"}","form-note");
        mvc.perform(get("/api/v1/pms/it-direct-notes/"+id+"/form"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.extensions.version").value(0));
        var runtime=fixture.runtime;
        runtime.jdbc.update("INSERT INTO system_menu(id,name,permission,type,sort,parent_id,status) VALUES(982999,'Extension definition','pms:dynamic-form-template:manage',3,0,0,0)");
        runtime.jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(701,982999,7)");
        var api=fixture.context.getBean(cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi.class);
        var definition=api.publishDefinition(7L,"IT","directNote",List.of(new cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi.Definition("flag","Flag",cn.iocoder.yudao.module.pms.platform.api.entity.EntityField.Type.BOOLEAN,false,null,List.of())),new cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor(7L,880001L,"form-test"));
        notes.saveForm(id,Map.of("$extensions",Map.of("definitionRevisionId",definition.id(),"expectedVersion",0,"values",Map.of("flag",true))),0L,"form-save");
        assertEquals(true,notes.form(id).extensions().fields().get("flag"));
        assertThrows(RuntimeException.class,()->notes.saveForm(id,Map.of("$extensions",Map.of("definitionRevisionId",definition.id(),"expectedVersion",0,"values",Map.of("flag",false))),1L,"stale-form"));
        assertEquals(true,notes.form(id).extensions().fields().get("flag"));
        assertEquals(1,runtime.jdbc.queryForObject("SELECT COUNT(*) FROM plt_entity_extension_value",Integer.class));
    }
    @AfterEach void close(){if(fixture!=null)fixture.close();if(validators!=null)validators.close();}
    long createHttp(String route,String values,String key) throws Exception {
        var response=mvc.perform(post(route).contentType("application/json").content("{\"idempotencyKey\":\""+key+"\",\"values\":"+values+"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonUtils.getObjectMapper().readTree(response).path("data").path("entityRef").path("entityId").asLong();
    }
    @Test void twoEmptyBusinessesRunInheritedHttpCrudAndActualSqlWithoutCatalogEnrollment() throws Exception {
        long first=createHttp("/api/v1/pms/it-direct-notes","{\"projectId\":99,\"title\":\"First\",\"remark\":\"clear me\"}","direct-first");
        long second=createHttp("/api/v1/pms/it-direct-others","{\"projectId\":99,\"description\":\"Second\"}","direct-second");
        mvc.perform(get("/api/v1/pms/it-direct-notes/"+first)).andExpect(jsonPath("$.data.fieldValues.title").value("First"));
        assertEquals("Second",others.get(second).getDescription());
        mvc.perform(put("/api/v1/pms/it-direct-notes/"+first).contentType("application/json")
                .content("{\"idempotencyKey\":\"update\",\"version\":0,\"values\":{\"title\":\"Updated\",\"remark\":null}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.newConcurrencyBasis").value(1));
        assertNull(notes.get(first).getRemark());assertEquals("Updated",notes.get(first).getTitle());
        assertEquals("CONCURRENCY_CONFLICT",assertThrows(BusinessContractException.class,()->notes.update(first,notes.input(Map.of("title","stale")),Set.of("title"),0L,"stale")).getErrorCode());
        mvc.perform(post("/api/v1/pms/it-direct-notes/page").contentType("application/json").content("{\"pageNo\":1,\"pageSize\":20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.list[0].fieldValues.title").value("Updated"));
        var deleted=notes.delete(first,1L,"delete");assertEquals(ReceiptOutcome.DELETED,deleted.outcome());
        assertEquals(deleted,notes.receipt("delete","delete"));assertEquals(deleted,notes.delete(first,1L,"delete"));
        assertThrows(BusinessContractException.class,()->notes.get(first));
        assertEquals(1,fixture.runtime.jdbc.queryForObject("SELECT COUNT(*) FROM it_direct_note WHERE id=? AND deleted=1",Integer.class,first));
    }
    @Test void inheritedDeliveriesUseTheSameMaterialTableAndProtectReferences() throws Exception {
        long first=createHttp("/api/v1/pms/it-direct-notes","{\"projectId\":99,\"title\":\"First\"}","file-first");
        long second=createHttp("/api/v1/pms/it-direct-others","{\"projectId\":99,\"description\":\"Second\"}","file-second");
        mvc.perform(multipart("/api/v1/pms/it-direct-notes/"+first+"/deliverables").file(fixture.file("direct upload\n"))
                .param("projectId","99").param("businessType","IT_DIRECT_NOTE").param("businessEntityKey",Long.toString(first)).param("deliverableType","REPORT").header("Idempotency-Key","direct-upload"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.businessType").value("IT_DIRECT_NOTE"));
        var data=new DefaultBusinessDeliveryApi.UploadFile("other.txt","text/plain",6L,()->new java.io.ByteArrayInputStream("other\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        others.uploadDelivery(second,"REPORT",data,"other-upload");
        mvc.perform(get("/api/v1/pms/business-deliverables").param("projectId","99").param("deliverableType","REPORT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        assertEquals(2,fixture.count("plt_delivery_material"));assertTrue(notes.deliveryCompletion(first,"REPORT").completed());
        assertTrue(others.deliveryCompletion(second,"REPORT").completed());assertEquals(2,fixture.deliveries.list(99L,"REPORT",null,null,1,20).getTotal());
        assertEquals("DELETE_REFERENCED_ENTITY",assertThrows(BusinessContractException.class,()->notes.delete(first,0L,"protected-delete")).getErrorCode());
        var material=notes.deliveries(first,"REPORT",1,20).getList().getFirst();
        DefaultBusinessDeliveryMySqlTest.login(7,880002);
        mvc.perform(get("/api/v1/pms/business-deliverables").param("projectId","99").param("deliverableType","REPORT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        assertThrows(Exception.class,()->mvc.perform(delete("/api/v1/pms/business-deliverables/"+material.id()).param("version","0")));
        DefaultBusinessDeliveryMySqlTest.login(8,880001);
        assertThrows(Exception.class,()->mvc.perform(get("/api/v1/pms/business-deliverables/"+material.id())));
        DefaultBusinessDeliveryMySqlTest.login(7,880001);
        assertThrows(BusinessContractException.class,()->notes.uploadDelivery(first,new DefaultBusinessDeliveryApi.Scope(101L,"IT_DIRECT_NOTE",Long.toString(first),"REPORT"),data,"forged"));
        notes.deleteDelivery(first,Long.valueOf(material.id()),material.version());assertFalse(notes.deliveryCompletion(first,"REPORT").completed());
        assertEquals(ReceiptOutcome.DELETED,notes.delete(first,0L,"delete-after-material").outcome());
    }
    @Test void sharedCopyReusesImmutableFileVersionWithDistinctExactReferencesAndMaterialIdentity() throws Exception {
        long source=createHttp("/api/v1/pms/it-direct-notes","{\"projectId\":99,\"title\":\"source\"}","copy-source");
        long target=createHttp("/api/v1/pms/it-direct-notes","{\"projectId\":99,\"title\":\"target\"}","copy-target");
        var data=new DefaultBusinessDeliveryApi.UploadFile("copy.txt","text/plain",5L,()->new java.io.ByteArrayInputStream("copy\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var original=notes.uploadDelivery(source,"REPORT",data,"copy-upload");
        var copied=notes.copyDeliveries(source,target,"copy-files");assertEquals(1,copied.size());var row=copied.getFirst();
        assertEquals(original.fileArtifactId(),row.fileArtifactId());assertEquals(original.fileVersionNo(),row.fileVersionNo());
        assertNotEquals(original.fileReferenceId(),row.fileReferenceId());assertEquals(Long.toString(target),row.businessEntityKey());assertEquals("ASSOCIATED",row.sourceKind());
        assertEquals(2,fixture.count("plt_delivery_material"));assertEquals(1,fixture.count("plt_file_version"));
        assertEquals(copied,notes.copyDeliveries(source,target,"copy-files"));assertEquals(2,fixture.count("plt_delivery_material"));
        assertTrue(notes.deliveryCompletion(target,"REPORT").completed());
        var document=notes.deliveryFile(target,Long.valueOf(row.id()));assertTrue(document.objectId().endsWith(":"+target));
        assertThrows(RuntimeException.class,()->notes.copyDeliveries(source,source,"same"));
        DefaultBusinessDeliveryMySqlTest.login(7,880002);
        assertThrows(RuntimeException.class,()->notes.copyDeliveries(source,target,"reader-copy"));
        assertEquals(2,fixture.count("plt_delivery_material"));
        DefaultBusinessDeliveryMySqlTest.login(7,880001);
    }
    @Test void specialBusinessOverridesOnlyItsHookAndXmlAndAddsItsOwnApi() throws Exception {
        long id=createHttp("/api/v1/pms/it-direct-specials","{\"projectId\":99,\"title\":\"lower\"}","special-create");
        assertEquals("LOWER",specials.get(id).getTitle());
        mvc.perform(post("/api/v1/pms/it-direct-specials/"+id+"/mark").contentType("application/json")
                .content("{\"idempotencyKey\":\"mark\",\"version\":0,\"values\":{}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.operationCode").value("mark"));
        assertEquals("LOWER!",specials.get(id).getTitle());
        assertEquals("SQL:LOWER!",specials.page(new BusinessPageQuery()).getList().getFirst().getTitle());
        DefaultBusinessDeliveryMySqlTest.login(7,880002);
        assertThrows(BusinessContractException.class,()->specials.mark(id,1L,"reader-mark"));
        assertEquals("LOWER!",specials.get(id).getTitle());
    }
}
