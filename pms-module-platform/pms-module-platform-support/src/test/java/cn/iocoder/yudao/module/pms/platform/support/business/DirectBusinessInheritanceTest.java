package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real generic Spring injection and inherited HTTP methods; Mapper/storage are unit doubles, not SQL acceptance. */
class DirectBusinessInheritanceTest {
    @Data @EqualsAndHashCode(callSuper=true) @TableName("it_direct_note")
    @ProjectBusinessModel(ownerModule="IT",entityType="directNote",stableCode="IT_DIRECT_NOTE",name="Note",permissionPrefix="it:direct-note")
    public static class Note extends BaseProjectBusinessEntity {
        @BusinessModelField(name="Title") @NotBlank private String title;
        @com.fasterxml.jackson.annotation.JsonIgnore private String internalSecret;
        @BusinessModelField @com.baomidou.mybatisplus.annotation.TableField(exist=false) private List<Child> children;
    }
    @Data @EqualsAndHashCode(callSuper=true) @TableName("it_direct_other")
    @ProjectBusinessModel(ownerModule="IT",entityType="directOther",stableCode="IT_DIRECT_OTHER",name="Other",permissionPrefix="it:direct-other")
    public static class Other extends BaseProjectBusinessEntity {
        @BusinessModelField @NotBlank private String description;
    }
    @TableName("it_direct_special")
    @ProjectBusinessModel(ownerModule="IT",entityType="directSpecial",stableCode="IT_DIRECT_SPECIAL",name="Special",permissionPrefix="it:direct-special")
    public static class Special extends Note { }
    @Data public static class Child { @com.fasterxml.jackson.annotation.JsonIgnore private Long internalId; private String value; }
    interface NoteMapper extends BusinessMapper<Note> { }
    interface OtherMapper extends BusinessMapper<Other> { }
    interface SpecialMapper extends BusinessMapper<Special> { }
    public static class NoteService extends DefaultProjectBusinessService<NoteMapper,Note> { }
    public static class OtherService extends DefaultProjectBusinessService<OtherMapper,Other> { }
    public static class WrongQueryService extends DefaultProjectBusinessService<NoteMapper,Note> {
        @Override @SuppressWarnings({"rawtypes","unchecked"}) protected cn.iocoder.yudao.framework.common.pojo.PageResult<Note> selectPage(BusinessReadQuery query) {
            var wrong=new Other();wrong.setId(11L);wrong.setTenantId(7L);wrong.setProjectId(99L);
            return new cn.iocoder.yudao.framework.common.pojo.PageResult((List)List.of(wrong),1L);
        }
    }
    public static class SpecialService extends DefaultProjectBusinessService<SpecialMapper,Special> {
        @Override protected Long generatedId(){return 33L;}
        @Override protected long initialVersion(){return 1L;}
        @Override protected void beforeCreate(Special entity) { entity.setTitle(entity.getTitle().toUpperCase(Locale.ROOT)); }
    }
    @RestController @RequestMapping("/it/direct-notes")
    public static class NoteController extends ProjectBusinessController<NoteService,Note> { }
    @RestController @RequestMapping("/it/direct-others")
    public static class OtherController extends ProjectBusinessController<OtherService,Other> { }
    static class Transactions extends AbstractPlatformTransactionManager {
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction,TransactionDefinition definition) { }
        protected void doCommit(DefaultTransactionStatus status) { }
        protected void doRollback(DefaultTransactionStatus status) { }
    }
    static class Journal implements OperationExecutionStore {
        final Map<OperationExecutionKey,StoredExecution> entries=new HashMap<>();
        public boolean reserve(OperationExecutionKey key,String digest) { return entries.putIfAbsent(key,new StoredExecution(digest,"IN_PROGRESS",null))==null; }
        public Optional<StoredExecution> findExisting(OperationExecutionKey key) { return Optional.ofNullable(entries.get(key)); }
        public void complete(OperationExecutionKey key,String type,String resource,BusinessOperationReceipt receipt) {
            entries.put(key,new StoredExecution(entries.get(key).requestDigest(),"COMPLETED",receipt));
        }
    }
    AnnotationConfigApplicationContext context;
    NoteMapper notes;OtherMapper others;SpecialMapper specials;
    BusinessAccessGuard permissions;BusinessScopeAccess projects;DefaultBusinessDeliveryApi deliveries;
    final Map<Long,Note> noteRows=new HashMap<>();final Map<Long,Other> otherRows=new HashMap<>();final Map<Long,Special> specialRows=new HashMap<>();
    @BeforeEach void setup() {
        notes=mock(NoteMapper.class);others=mock(OtherMapper.class);specials=mock(SpecialMapper.class);
        permissions=mock(BusinessAccessGuard.class);projects=mock(BusinessScopeAccess.class);deliveries=mock(DefaultBusinessDeliveryApi.class);
        when(projects.policyRef()).thenReturn("project");when(projects.readableScopeIds(any())).thenReturn(Set.of(99L));
        when(notes.insert(any(Note.class))).thenAnswer(call->{Note row=call.getArgument(0);row.setId(11L);noteRows.put(11L,row);return 1;});
        when(others.insert(any(Other.class))).thenAnswer(call->{Other row=call.getArgument(0);row.setId(22L);otherRows.put(22L,row);return 1;});
        when(specials.insert(any(Special.class))).thenAnswer(call->{Special row=call.getArgument(0);row.setId(33L);specialRows.put(33L,row);return 1;});
        when(notes.selectById(any())).thenAnswer(call->noteRows.get(call.getArgument(0)));
        when(others.selectById(any())).thenAnswer(call->otherRows.get(call.getArgument(0)));
        when(specials.selectById(any())).thenAnswer(call->specialRows.get(call.getArgument(0)));
        context=new AnnotationConfigApplicationContext();
        var defaults=new BusinessDefaults(()->new AbstractBusinessApplicationService.ResolvedCaller(7L,42L,null),permissions,projects,
                Validation.buildDefaultValidatorFactory().getValidator(),new TransactionTemplate(new Transactions()),new Journal(),
                mock(OperationAuditApi.class),mock(BusinessEventPort.class),List.of(mock(BusinessDeletionGuard.class)),()->deliveries);
        context.registerBean(BusinessDefaults.class,()->defaults);
        context.registerBean(NoteMapper.class,()->notes);context.registerBean(OtherMapper.class,()->others);context.registerBean(SpecialMapper.class,()->specials);
        context.register(NoteService.class,OtherService.class,SpecialService.class,NoteController.class,OtherController.class);context.refresh();
    }
    @AfterEach void close() { if(context!=null)context.close(); }
    @Test void twoEmptyBusinessClassesInheritWithoutCatalogContributorOrDispatcher() {
        var first=context.getBean(NoteService.class);var second=context.getBean(OtherService.class);
        var a=first.create(first.input(Map.of("projectId",99,"title","First")),"first-create");
        var b=second.create(second.input(Map.of("projectId",99,"description","Second")),"second-create");
        assertEquals(11L,a.entityRef().entityId());assertEquals(22L,b.entityRef().entityId());
        assertEquals("First",first.get(11L).getTitle());assertEquals("Second",second.get(22L).getDescription());
        assertTrue(context.getBeansOfType(BusinessModelCatalog.class).isEmpty());
        assertTrue(context.getBeansOfType(BusinessModelContributor.class).isEmpty());
        assertTrue(context.getBeansOfType(BusinessOperationDispatcher.class).isEmpty());
        assertTrue(context.getBeansOfType(DefaultBusinessApplicationService.class).isEmpty());
        assertEquals(0,NoteService.class.getDeclaredMethods().length);assertEquals(0,NoteController.class.getDeclaredMethods().length);
        verify(projects,atLeastOnce()).requireWritable(99L,new EntityActor(7L,42L,null),true);
    }
    @Test void defaultRuntimeMapperUsesConcreteEntityColumnsAndExactScope(){
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"runtime"),Note.class);
        doCallRealMethod().when(notes).selectRuntimeCandidates(any());
        doAnswer(call->{
            var page=call.getArgument(0,com.baomidou.mybatisplus.core.metadata.IPage.class);
            var wrapper=call.getArgument(1,com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
            assertTrue(wrapper.getSqlSegment().contains("tenant_id"));assertTrue(wrapper.getSqlSegment().contains("project_id"));
            assertTrue(wrapper.getSqlSegment().contains("ORDER BY id ASC"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(7L));assertTrue(wrapper.getParamNameValuePairs().containsValue(99L));assertTrue(wrapper.getParamNameValuePairs().containsValue(10L));
            page.setRecords(List.of());return page;
        }).when(notes).selectPage(any(),any());
        assertTrue(notes.selectRuntimeCandidates(new BusinessMapper.RuntimeCandidates(7L,99L,10L,20)).isEmpty());
        assertThrows(IllegalArgumentException.class,()->notes.selectRuntimeCandidates(new BusinessMapper.RuntimeCandidates(7L,99L,-1L,20)));
    }
    @Test void runtimeFactsAreInheritedAndNeverExposePrivateBodyOrForeignProject(){
        var service=context.getBean(NoteService.class);service.create(service.input(Map.of("projectId",99,"title","Private")),"runtime-create");
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(7L);
        try{
            var type=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Type("IT","directNote");
            var query=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Query(7L,99L,type,11L);
            var result=service.runtimeObservation(query,false);assertEquals(Map.of("BUSINESS_RECORD_SAVED",true),result.facts());assertTrue(result.handlingCompleted());
            assertThrows(RuntimeException.class,()->service.runtimeObservation(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Query(7L,100L,type,11L),false));
            assertThrows(RuntimeException.class,()->service.runtimeObservation(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.Query(8L,99L,type,11L),false));
            assertThrows(RuntimeException.class,()->service.runtimeObservation(query,true));
            assertThrows(RuntimeException.class,()->service.runtimeActions(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.UserContext(7L,43L,99L,type)));
            var events=org.mockito.ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventRecord.class);
            verify(context.getBean(BusinessDefaults.class).events()).append(events.capture());assertEquals(99L,events.getValue().projectChange().projectId());
        }finally{cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();}
    }
    @Test void twoEmptyBusinessServicesShareConfigurationWithoutPerEntityAdapters(){
        var api=mock(cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.class);
        var configurations=new HashMap<cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Identity,cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration>();
        when(api.read(any(),any())).thenAnswer(call->configurations.getOrDefault(call.getArgument(0),new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration(0,List.of())));
        when(api.save(any(),any(),anyLong(),any())).thenAnswer(call->{var result=new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Configuration(1,call.getArgument(3));configurations.put(call.getArgument(0),result);return result;});
        context.getBeanFactory().registerSingleton("fieldConfigurations",api);
        var first=context.getBean(NoteService.class);var second=context.getBean(OtherService.class);
        first.saveFieldConfiguration(0,List.of(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Field("title","Note title",0,true,false,true)));
        second.saveFieldConfiguration(0,List.of(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.Field("description","Other text",0,true,true,false)));
        assertEquals(2,configurations.size());
        assertEquals("Note title",first.model().fields().stream().filter(field->field.code().equals("title")).findFirst().orElseThrow().name());
        assertEquals("Other text",second.model().fields().stream().filter(field->field.code().equals("description")).findFirst().orElseThrow().name());
        assertEquals(0,NoteService.class.getDeclaredMethods().length);assertEquals(0,OtherService.class.getDeclaredMethods().length);
    }
    @Test void emptyBusinessPublishesInheritedListConfiguration() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(context.getBean(NoteService.class).model());
        var fields=json.get("fields");
        var title=java.util.stream.StreamSupport.stream(fields.spliterator(),false).filter(field->field.get("code").asText().equals("title")).findFirst().orElseThrow();
        assertTrue(title.path("listVisible").asBoolean(),"Default columns must be configured in the inherited model");
        assertTrue(title.path("searchable").asBoolean());
        assertTrue(title.path("sortable").asBoolean());
    }
    @Test void emptyBusinessInheritsPublicFieldProviderWithoutAdapter() {
        var service=context.getBean(NoteService.class);
        service.create(service.input(Map.of("projectId",99,"title","Fields")),"fields-create");
        var provider=assertInstanceOf(EntityFieldProvider.class,service);
        var target=EntityDataRef.current(new EntityRef(7L,"IT","directNote",11L));
        assertEquals("Fields",provider.read(target,new EntityActor(7L,42L,null)).get("title").value());
        assertFalse(provider.read(target,new EntityActor(7L,42L,null)).containsKey("internalSecret"));
        assertThrows(RuntimeException.class,()->provider.read(target,new EntityActor(7L,999L,null)));
    }
    @Test void emptyBusinessAlsoInheritsPublishedFormCompatibilityPolicy(){
        var provider=assertInstanceOf(cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessObjectPolicyProvider.class,context.getBean(NoteService.class));
        var key=new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey("IT","directNote");
        var field=new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor("title","input",false,true,"any",null,128,null,List.of());
        var query=new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionPolicyQuery(7L,42L,key,1L,2L,1,3,"directNote",cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction.REVISION_FROZEN_USE,List.of(field));
        assertEquals(key,provider.providerKey());assertTrue(provider.inspectRevisionCompatibility(query).allowed());
        var invalid=new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionPolicyQuery(7L,42L,key,1L,2L,1,3,"anotherOwner",query.action(),List.of(field));
        assertFalse(provider.inspectRevisionCompatibility(invalid).allowed());
    }
    @Test void inheritedControllerCallsItsOwnTypedServiceAndMapper() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(context.getBean(NoteController.class)).build();
        mvc.perform(post("/it/direct-notes").contentType("application/json")
                .content("{\"idempotencyKey\":\"http-create\",\"values\":{\"projectId\":99,\"title\":\"HTTP\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.entityRef.entityId").value(11));
        mvc.perform(get("/it/direct-notes/11")).andExpect(status().isOk()).andExpect(jsonPath("$.data.fieldValues.title").value("HTTP"))
                .andExpect(jsonPath("$.data.fieldValues.internalSecret").doesNotExist());
        verify(notes).insert(any(Note.class));verifyNoInteractions(others,specials);
    }
    @Test void businessDifferenceOverridesOnlyTheTypedHook() {
        var service=context.getBean(SpecialService.class);
        service.create(service.input(Map.of("projectId",99,"title","lower")),"special");
        assertEquals("LOWER",specialRows.get(33L).getTitle());assertEquals(1L,specialRows.get(33L).getVersion());
        assertEquals("IT_DIRECT_SPECIAL",service.definition().stableCode());verify(specials).insert(any(Special.class));
    }
    @Test void defaultPermissionFailurePreventsAnyMapperWrite() {
        var service=context.getBean(NoteService.class);
        doThrow(new BusinessContractException("ACCESS_DENIED","denied")).when(permissions).requireWritable(any(),any(),eq("operation:create"));
        assertThrows(BusinessContractException.class,()->service.create(service.input(Map.of("projectId",99,"title","denied")),"denied"));
        verify(notes,never()).insert(any(Note.class));
    }
    @Test void sameIntentReplaysWithoutAnotherInsertAndChangedInputIsRejected() {
        var service=context.getBean(NoteService.class);var input=service.input(Map.of("projectId",99,"title","same"));
        var first=service.create(input,"same-key");assertEquals(first,service.create(input,"same-key"));verify(notes,times(1)).insert(any(Note.class));
        assertEquals("IDEMPOTENCY_DIGEST_CONFLICT",assertThrows(BusinessContractException.class,()->service.create(service.input(Map.of("projectId",99,"title","changed")),"same-key")).getErrorCode());
    }
    @Test void uploadCapabilityIsInheritedAndUsesTheCommonFourKeyApi() {
        var service=context.getBean(NoteService.class);service.create(service.input(Map.of("projectId",99,"title","file")),"file-owner");
        var file=new DefaultBusinessDeliveryApi.UploadFile("note.txt","text/plain",1L,()->new java.io.ByteArrayInputStream(new byte[]{1}));
        service.uploadDelivery(11L,"REPORT",file,"upload-key");
        verify(deliveries).upload(new DefaultBusinessDeliveryApi.Scope(99L,"IT_DIRECT_NOTE","11","REPORT"),file,"upload-key");
    }
    @Test void childFieldsAreExposedWithoutPretendingToBeParentSqlColumns() {
        var service=context.getBean(NoteService.class);
        assertTrue(service.definition().fields().stream().anyMatch(field->field.code().equals("children")));
        assertFalse(cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector.businessFields(Note.class).stream().anyMatch(field->field.code().equals("children")));
        var input=service.input(Map.of("children",List.of(Map.of("value","business","internalId",987))));
        assertEquals("business",input.getChildren().getFirst().getValue());assertNull(input.getChildren().getFirst().getInternalId());
    }
    @Test void internalCopiesRetainJsonIgnoredIdentityAndDetachNestedBusinessValues() {
        var source=new Note();source.setId(11L);source.setTenantId(7L);source.setProjectId(99L);source.setVersion(2L);source.setInternalSecret("internal");
        var child=new Child();child.setInternalId(91L);child.setValue("original");source.setChildren(new ArrayList<>(List.of(child)));
        Note copied=org.springframework.test.util.ReflectionTestUtils.invokeMethod(context.getBean(NoteService.class),"copy",source);
        assertEquals("internal",copied.getInternalSecret());assertEquals(91L,copied.getChildren().getFirst().getInternalId());
        assertEquals(11L,copied.getId());assertEquals(99L,copied.getProjectId());assertEquals(2L,copied.getVersion());
        copied.getChildren().getFirst().setValue("changed");assertEquals("original",source.getChildren().getFirst().getValue());
    }
    @Test void hiddenAndControlFieldsCannotEnterThroughGenericInput() {
        var service=context.getBean(NoteService.class);
        assertThrows(BusinessContractException.class,()->service.input(Map.of("projectId",99,"title","x","tenantId",8)));
        assertThrows(BusinessContractException.class,()->service.input(Map.of("projectId",99,"title","x","internalSecret","hidden")));
    }    @Test void aComplexQueryCannotReturnAnotherBusinessEntityClass() {
        context.registerBean(WrongQueryService.class);
        assertThrows(BusinessContractException.class,()->context.getBean(WrongQueryService.class).page(new BusinessPageQuery()));
    }

}
