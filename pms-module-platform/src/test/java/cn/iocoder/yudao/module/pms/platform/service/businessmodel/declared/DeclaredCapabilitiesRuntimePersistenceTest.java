package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormSchemaService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** The same DO, pure mapper and declaration exercise actual capability services and MySQL transactions. */
@EnabledIfSystemProperty(named="npdms.declared.exclusive",matches="true")
class DeclaredCapabilitiesRuntimePersistenceTest {
    private DeclaredBusinessRuntimePersistenceTest.Runtime runtime;
    private TransactionTemplate transaction;
    @BeforeEach void start() throws Exception {
        runtime=new DeclaredBusinessRuntimePersistenceTest.Runtime(true,0,false,true);
        DeclaredBusinessRuntimePersistenceTest.initializeExclusiveSchema(runtime);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(runtime.source));
        login(7L,880001L);
        initializeCapabilities(runtime);
    }
    static void initializeCapabilities(DeclaredBusinessRuntimePersistenceTest.Runtime runtime) throws Exception {
        var capabilities=Files.readString(Path.of("../sql/migrations/V248__entity_capabilities_and_requirement_revision.sql"));
        var templates=Files.readString(Path.of("../sql/migrations/V102__fplt002_dynamic_form.sql"));
        for(String table:List.of("plt_entity_extension_value","plt_entity_form_binding","plt_entity_extension_definition","plt_dynamic_form_template_revision","plt_dynamic_form_template")) runtime.jdbc.execute("DROP TABLE IF EXISTS "+table);
        for(String table:List.of("plt_entity_extension_definition","plt_entity_extension_value","plt_entity_form_binding","plt_dynamic_form_template","plt_dynamic_form_template_revision")) {
            var text=table.startsWith("plt_entity_")?capabilities:templates;
            var ddl=Pattern.compile("CREATE TABLE `?"+table+"`? [(].*?^[)].*?;",Pattern.DOTALL|Pattern.MULTILINE).matcher(text);
            assertTrue(ddl.find(),table);runtime.jdbc.execute(ddl.group());
        }
        runtime.jdbc.update("INSERT INTO plt_dynamic_form_template(id,tenant_id,template_code,template_name,category_code,availability_code,current_published_revision_id,creator) VALUES(900100,7,'IT_DECLARED_FORM','IT form','IT','ENABLED',900101,'it_declared')");
        runtime.jdbc.update("INSERT INTO plt_dynamic_form_template_revision(id,tenant_id,template_id,revision_no,status_code,form_conf_json,form_rules_json,engine_code,designer_version,renderer_version,creator) VALUES(900101,7,900100,1,'PUBLISHED','{}',?,?,?,?, 'it_declared')",
                "[{\"type\":\"input\",\"field\":\"title\"},{\"type\":\"checkbox\",\"field\":\"flags\",\"options\":[{\"value\":\"A\"},{\"value\":\"B\"}]},{\"type\":\"group\",\"field\":\"devices\",\"props\":{\"rule\":[{\"type\":\"input\",\"field\":\"serial\"}]}}]",
                DynamicFormSchemaService.ENGINE_CODE,DynamicFormSchemaService.DESIGNER_VERSION,DynamicFormSchemaService.RENDERER_VERSION);
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();TenantContextHolder.clear();if(runtime!=null)runtime.close();}
    private void login(long tenant,long user){TenantContextHolder.setTenantId(tenant);var principal=new LoginUser();principal.setTenantId(tenant);principal.setId(user);principal.setUserType(2);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));}
    private EntityActor actor(){return new EntityActor(TenantContextHolder.getRequiredTenantId(),((LoginUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId(),"it");}
    private BusinessOperationRequest create(String key,Map<String,Object> extra){var input=new LinkedHashMap<String,Object>(extra);input.put("projectRef",99L);input.putIfAbsent("title",key);return new BusinessOperationRequest("create",1,null,"IT","declaredNote",input,key,null,OperationEntryKind.INDEPENDENT,null);}
    private EntityDataRef create(String key){return EntityDataRef.current(runtime.dispatcher.dispatch(create(key,Map.of("internalMemo","hidden"))).entityRef());}
    private EntityFormApi.Binding bind(EntityDataRef target){return transaction.execute(status->runtime.forms.bind(new EntityFormApi.Bind(target,actor(),0L,0,900101L,null,Map.of("title","title"),true)));}
    private BusinessOperationRequest save(EntityDataRef target,String key,Long version,Long definition,int valueVersion,Map<String,Object> values){return new BusinessOperationRequest("save",1,target,null,null,Map.of("title","Changed","$extensions",Map.of("definitionRevisionId",definition,"expectedVersion",valueVersion,"values",values)),key,version,OperationEntryKind.INDEPENDENT,null);}
    private long count(String table){return runtime.jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    private void reject(String code,Runnable action){assertEquals(code,assertThrows(BusinessContractException.class,action::run).getErrorCode());}

    @Test void noHistoryFormBindingAndTypedExtensionSaveNeedNoEntityProvider(){
        var target=create("form");var binding=bind(target);
        assertEquals(Map.of("title","title","flags","flags","devices","devices"),binding.fieldBindings());
        var values=Map.<String,Object>of("flags",List.of("A"),"devices",List.of(Map.of("serial","IT-1")));
        var request=save(target,"save-form",0L,binding.extensionDefinitionRevisionId(),0,values);
        var receipt=runtime.dispatcher.dispatch(request);assertEquals(1L,receipt.newConcurrencyBasis());
        assertEquals(receipt,runtime.dispatcher.dispatch(request));
        var detail=runtime.access.read(target,actor(),"detail");assertEquals("Changed",detail.fieldValues().get("title"));assertEquals(values.get("devices"),detail.fieldValues().get("devices"));assertFalse(detail.fieldValues().containsKey("internalMemo"));
        var page=runtime.access.query(new BusinessEntityPageQuery("list","IT","declaredNote",List.of(),20,null),actor());assertEquals(List.of("A"),page.members().getFirst().fieldValues().get("flags"));
        var fixed=runtime.entityProviders.fields(target.entity()).read(target,actor());assertFalse(fixed.get("internalMemo").readable());assertNull(fixed.get("internalMemo").value());
        assertEquals(1,count("plt_entity_extension_value"));assertEquals(1,count("plt_entity_extension_definition"));assertEquals(1,count("plt_entity_form_binding"));assertEquals(2,count("plt_idempotency_record"));assertEquals(2,count("plt_outbox_event"));assertEquals(4,count("plt_operation_audit"));
        assertThrows(RuntimeException.class,()->runtime.entityProviders.versions(target.entity()));
        reject("REVISION_UNSUPPORTED",()->runtime.entityProviders.requireReadable(new EntityDataRef(target.entity(),77L),actor()));
    }
    @Test void directCapabilityWritesRequireDeclaredUpdatePermissionCurrentVersionAndScope(){
        var target=create("denials");login(7,880002);
        reject("ACCESS_DENIED",()->bind(target));assertEquals(0,count("plt_entity_form_binding"));
        login(7,880001);
        reject("CONCURRENCY_CONFLICT",()->transaction.execute(status->runtime.forms.bind(new EntityFormApi.Bind(target,actor(),2L,0,900101L,null,Map.of(),true))));
        doReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(99L,1L,Set.of(),Set.of())).when(runtime.projectApi).resolveCurrent(any());
        reject("ENTITY_SCOPE_DENIED",()->bind(target));assertEquals(0,count("plt_entity_extension_definition"));
        login(8,880001);assertThrows(IllegalArgumentException.class,()->bind(target));
    }
    @Test void createOnlyPermissionCanSaveInitialExtensionsButCannotChangeExistingExtensions(){
        var original=create("definition-source");var binding=bind(original);
        runtime.jdbc.update("DELETE FROM system_role_menu WHERE role_id=701 AND menu_id IN (980002,980004)");
        var payload=Map.<String,Object>of("$extensions",Map.of("definitionRevisionId",binding.extensionDefinitionRevisionId(),"expectedVersion",0,"values",Map.of("flags",List.of("B"))));
        var request=create("create-with-extension",payload);var receipt=runtime.dispatcher.dispatch(request);assertEquals(receipt,runtime.dispatcher.dispatch(request));
        var target=EntityDataRef.current(receipt.entityRef());login(7,880002);assertEquals(List.of("B"),runtime.extensions.read(target,actor()).fields().get("flags"));login(7,880001);
        reject("ACCESS_DENIED",()->transaction.execute(status->runtime.extensions.save(new EntityExtensionApi.Save(target,actor(),0L,1,binding.extensionDefinitionRevisionId(),Map.of("flags",List.of("A"))))));
        assertEquals(1,count("plt_entity_extension_value"));assertEquals(2,count("it_declared_note"));
    }
    @Test void productionSerializedSnowflakeDefinitionIdIsExactAndMalformedIdsFailClosed() {
        var target=create("snowflake");var binding=bind(target);
        assertTrue(binding.extensionDefinitionRevisionId()>9007199254740991L);
        for(String invalid:List.of(" 1","1.5","1e3","9223372036854775808","-1")) {
            var request=new BusinessOperationRequest("save",1,target,null,null,
                Map.of("$extensions",Map.of("definitionRevisionId",invalid,"expectedVersion",0,"values",Map.of("flags",List.of("A")))),
                "invalid-id-"+invalid,0L,OperationEntryKind.INDEPENDENT,null);
            reject("OPERATION_INPUT_INVALID",()->runtime.dispatcher.dispatch(request));
        }
        var exact=new BusinessOperationRequest("save",1,target,null,null,
            Map.of("$extensions",Map.of("definitionRevisionId",binding.extensionDefinitionRevisionId().toString(),"expectedVersion",0,"values",Map.of("flags",List.of("A")))),
            "exact-id",0L,OperationEntryKind.INDEPENDENT,null);
        var receipt=runtime.dispatcher.dispatch(exact);assertEquals(1L,receipt.newConcurrencyBasis());
        assertEquals(binding.extensionDefinitionRevisionId(),runtime.extensions.read(target,actor()).definitionRevisionId());
        assertEquals(2,count("plt_idempotency_record"));assertEquals(2,count("plt_outbox_event"));
    }

    @Test void invalidExtensionAndStaleValueVersionDoNotCommitFixedFieldsLedgerOrEvents(){
        var target=create("invalid");var binding=bind(target);long audits=count("plt_operation_audit");
        assertThrows(RuntimeException.class,()->runtime.dispatcher.dispatch(save(target,"bad-flags",0L,binding.extensionDefinitionRevisionId(),0,Map.of("flags",List.of("C")))));
        assertThrows(RuntimeException.class,()->runtime.dispatcher.dispatch(save(target,"bad-row",0L,binding.extensionDefinitionRevisionId(),0,Map.of("devices",List.of("wrong")))));
        assertThrows(RuntimeException.class,()->runtime.dispatcher.dispatch(save(target,"stale-values",0L,binding.extensionDefinitionRevisionId(),2,Map.of("flags",List.of("A")))));
        assertEquals("invalid",runtime.access.read(target,actor(),"detail").fieldValues().get("title"));assertEquals(0,count("plt_entity_extension_value"));assertEquals(1,count("plt_idempotency_record"));assertEquals(1,count("plt_outbox_event"));assertEquals(audits,count("plt_operation_audit"));
    }
    @Test void ordinaryRequiredExtensionOmissionPreservesAndExplicitClearRollsBack(){
        var target=create("required-extension");
        runtime.jdbc.update("UPDATE plt_dynamic_form_template_revision SET form_rules_json=? WHERE id=900101",
                "[{\"type\":\"input\",\"field\":\"title\"},{\"type\":\"input\",\"field\":\"detail\",\"validate\":[{\"required\":true}]},{\"type\":\"input\",\"field\":\"memo\"}]");
        var binding=bind(target);var definition=binding.extensionDefinitionRevisionId();
        assertThrows(RuntimeException.class,()->runtime.dispatcher.dispatch(save(target,"required-missing",0L,definition,0,Map.of("memo","x"))));
        runtime.dispatcher.dispatch(save(target,"required-first",0L,definition,0,Map.of("detail","keep","memo","first")));
        runtime.dispatcher.dispatch(save(target,"required-omit",1L,definition,1,Map.of("memo","second")));
        assertEquals(Map.of("detail","keep","memo","second"),runtime.extensions.read(target,actor()).fields());
        long audits=count("plt_operation_audit");var clear=new LinkedHashMap<String,Object>();clear.put("detail",null);
        assertThrows(RuntimeException.class,()->runtime.dispatcher.dispatch(save(target,"required-clear",2L,definition,2,clear)));
        assertEquals("keep",runtime.extensions.read(target,actor()).fields().get("detail"));
        assertEquals(2L,runtime.access.read(target,actor(),"detail").concurrencyBasis());
        assertEquals(3,count("plt_idempotency_record"));assertEquals(3,count("plt_outbox_event"));assertEquals(audits,count("plt_operation_audit"));
    }
    @Test void fixedOnlySaveCannotBypassRequiredBoundExtension(){
        var target=create("required-bound");
        runtime.jdbc.update("UPDATE plt_dynamic_form_template_revision SET form_rules_json=? WHERE id=900101",
            "[{\"type\":\"input\",\"field\":\"title\"},{\"type\":\"input\",\"field\":\"detail\",\"validate\":[{\"required\":true}]}]");
        bind(target);
        var request=new BusinessOperationRequest("save",1,target,null,null,Map.of("title","Bypass"),"bound-bypass",0L,OperationEntryKind.INDEPENDENT,null);
        assertThrows(RuntimeException.class,()->runtime.dispatcher.dispatch(request));
        assertEquals("required-bound",runtime.access.read(target,actor(),"detail").fieldValues().get("title"));
        assertEquals(1,count("plt_idempotency_record"));assertEquals(1,count("plt_outbox_event"));
    }
    @Test void publicFormReadUsesActualScopeAndBindingApi(){
        var target=create("public-form");bind(target);
        var controller=new cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.BusinessEntityFormController(runtime.forms,runtime.extensions,
            runtime.context.getBean(cn.iocoder.yudao.module.pms.platform.support.capability.DeclaredBusinessCapabilityAdapterFactory.class),
            new cn.iocoder.yudao.module.pms.platform.service.businessmodel.TenantCallerContext());
        var form=controller.read("IT","declaredNote",target.entity().entityId()).getData();
        assertEquals(900101L,form.layout().binding().formRevisionId());assertEquals(2,form.definitions().size());
        doReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(99L,1L,Set.of(),Set.of())).when(runtime.projectApi).resolveCurrent(any());
        reject("ENTITY_SCOPE_DENIED",()->controller.read("IT","declaredNote",target.entity().entityId()));
    }
    @Test void auditFailureRollsBackBothFixedAndExtensionContent(){rollback("plt_operation_audit");}
    @Test void outboxFailureRollsBackBothFixedAndExtensionContent(){rollback("plt_outbox_event");}
    private void rollback(String table){
        var target=create("atomic");var binding=bind(target);long audits=count("plt_operation_audit");
        runtime.jdbc.execute("ALTER TABLE "+table+" RENAME COLUMN "+(table.equals("plt_operation_audit")?"operation_code":"event_type")+" TO it_broken_column");
        assertThrows(RuntimeException.class,()->runtime.dispatcher.dispatch(save(target,"rollback",0L,binding.extensionDefinitionRevisionId(),0,Map.of("flags",List.of("A")))));
        assertEquals("atomic",runtime.access.read(target,actor(),"detail").fieldValues().get("title"));assertEquals(0,count("plt_entity_extension_value"));assertEquals(1,count("plt_idempotency_record"));assertEquals(1,count("plt_outbox_event"));assertEquals(audits,count("plt_operation_audit"));
    }
}
