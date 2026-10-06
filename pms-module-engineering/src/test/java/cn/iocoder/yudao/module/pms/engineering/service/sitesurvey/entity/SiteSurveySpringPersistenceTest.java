package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntitySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.*;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.*;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxAppended;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.*;
import cn.iocoder.yudao.module.pms.platform.service.command.*;
import cn.iocoder.yudao.module.pms.platform.service.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.revision.InheritedRevisionAdapterFactory;
import cn.iocoder.yudao.module.pms.project.api.scope.*;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.ProjectBusinessResultRecordingApi;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectCodeQueryApi;
import cn.iocoder.yudao.module.pms.project.api.deadline.ProjectEndDateApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.mybatis.spring.SqlSessionTemplate;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.io.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Own random in-memory database; production transactions, mappers, extensions, child rows, ledger/audit/outbox.
 * External permission/project/deadline/location/form-layout and result-recording facts are test doubles.
 * This does not certify production MySQL DDL, browser HTTP authorization or an execution runtime replacement. */
class SiteSurveySpringPersistenceTest {
    AnnotationConfigApplicationContext ctx;
    JdbcTemplate jdbc;
    SiteSurveyEntityCommands commands;
    long definition;
    @BeforeEach void open() {
        TenantContextHolder.setTenantId(1L);login();
        ctx=new AnnotationConfigApplicationContext(Config.class);
        jdbc=ctx.getBean(JdbcTemplate.class);commands=ctx.getBean(SiteSurveyEntityCommands.class);
        definition=ctx.getBean(EntityExtensionApi.class).publishDefinition(1L,"SOL","SITE_SURVEY",
                List.of(new EntityExtensionApi.Definition("extra_flag","Flag",EntityField.Type.BOOLEAN,false,null,List.of())),new EntityActor(1L,9L,"survey-it")).id();
    }
    @AfterEach void close() {if(ctx!=null){if(jdbc!=null && !mysqlOptIn())jdbc.execute("SHUTDOWN");ctx.close();}org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    SiteSurveyEntitySaveReqVO content(String name) {
        var dto=new SiteSurveyEntitySaveReqVO();dto.setProjectId(20L);dto.setName(name);dto.setLocation("机房");
        dto.setExtensionDefinitionRevisionId(definition);dto.setExtensionValues(Map.of("extra_flag",false));
        dto.setBusinessValues(Map.of("cabinetReady",false,"powerTypes",List.of("AC"),"selectedMaterials",List.of(Map.of("projectId",20,"sn","SN-1","reason","接口"))));
        return dto;
    }
    BusinessOperationReceipt create() {return commands.executeReceipt("create",null,null,content("survey"),null,"create",OperationEntryKind.INDEPENDENT);}
    BusinessOperationReceipt save(BusinessOperationReceipt r,String key,String name) {return commands.executeReceipt("save",r.entityRef().entityId(),r.newConcurrencyBasis(),content(name),null,key,OperationEntryKind.INDEPENDENT);}
    BusinessOperationReceipt action(String code,BusinessOperationReceipt r,String key) {return commands.executeReceipt(code,r.entityRef().entityId(),r.newConcurrencyBasis(),null,null,key,OperationEntryKind.INDEPENDENT);}
    long count(String table) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    Map<String,Long> counts() {var m=new LinkedHashMap<String,Long>();for(String t:List.of("sol_site_survey","sol_site_survey_condition","sol_site_survey_material","plt_entity_extension_value","plt_idempotency_record","plt_operation_audit","plt_outbox_event"))m.put(t,count(t));return m;}
    @Test void realSpringDiscoversOwnerAndSavesFixedExtensionsAndChildrenWithoutTemplates() {
        assertTrue(AopUtils.isCglibProxy(ctx.getBean(SiteSurveyEntityDomainCommands.class)));
        assertTrue(AopUtils.isAopProxy(ctx.getBean(EntityExtensionApi.class)));
        var r=create();assertEquals(1,r.newConcurrencyBasis());assertEquals(1,count("sol_site_survey"));
        assertEquals(1,count("sol_site_survey_condition"));assertEquals(1,count("sol_site_survey_material"));assertEquals(1,count("plt_entity_extension_value"));
        assertFalse(jdbc.queryForObject("SELECT cabinet_ready FROM sol_site_survey",Boolean.class));
        assertEquals("AC",jdbc.queryForObject("SELECT condition_code FROM sol_site_survey_condition",String.class));
        assertEquals("SN-1",jdbc.queryForObject("SELECT sn FROM sol_site_survey_material",String.class));
        verifyNoInteractions(ctx.getBean(DynamicFormBusinessInstanceApi.class));
        var values=ctx.getBean(EntityExtensionApi.class).read(EntityDataRef.current(r.entityRef()),new EntityActor(1L,9L,"read"));
        assertEquals(Map.of("extra_flag",false),values.fields());assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM plt_idempotency_record",String.class));
    }
    @Test void commonReadKeepsActualProjectMetadataBodyChildrenExtensionsAndBasis() {
        var r=create();var reader=ctx.getBean(ProviderBusinessEntityContentReader.class);
        var data=reader.read(EntityDataRef.current(r.entityRef()),new EntityActor(1L,9L,"read"));
        assertEquals(r.entityRef(),data.ref());assertEquals(r.newConcurrencyBasis(),data.concurrencyBasis());
        assertEquals(20L,data.fieldValues().get("projectId"));assertEquals("survey",data.fieldValues().get("name"));
        assertEquals(0,data.fieldValues().get("status"));assertEquals(List.of("AC"),data.fieldValues().get("powerTypes"));
        assertEquals(false,data.fieldValues().get("extra_flag"));assertFalse(data.fieldValues().containsKey("outsourceRequestId"));
        assertEquals(1,((List<?>)data.fieldValues().get("selectedMaterials")).size());
        var observed=reader.read(EntityDataRef.current(r.entityRef()),new EntityActor(1L,0L,EntityActor.SYSTEM_OBSERVER));
        assertEquals(data.fieldValues(),observed.fieldValues());
    }
    @Test void replayIgnoresNestedObjectKeyOrderAndEntryPresentation() {
        var r=create();var original=content("edited");var values=new LinkedHashMap<String,Object>();
        values.put("cabinetReady",false);values.put("powerTypes",List.of("AC"));values.put("selectedMaterials",List.of(Map.of("projectId",20,"sn","SN-1","reason","接口")));
        original.setBusinessValues(values);
        var saved=commands.executeReceipt("save",r.entityRef().entityId(),r.newConcurrencyBasis(),original,null,"same",OperationEntryKind.INDEPENDENT);
        var before=counts();var reordered=new LinkedHashMap<String,Object>();reordered.put("selectedMaterials",values.get("selectedMaterials"));reordered.put("powerTypes",values.get("powerTypes"));reordered.put("cabinetReady",false);original.setBusinessValues(reordered);
        assertEquals(saved,commands.executeReceipt("save",r.entityRef().entityId(),r.newConcurrencyBasis(),original,null,"same",OperationEntryKind.PROJECT_NODE));assertEquals(before,counts());
    }
    @Test void sixOperationsKeepOriginalStatesAndDeleteReportsActualNewVersion() {
        var draft=create();var saved=save(draft,"save","edited");assertEquals(2,saved.newConcurrencyBasis());
        var confirmed=action("confirm",saved,"confirm");assertEquals("1",SiteSurveyBusinessApplicationService.result(confirmed).state());
        assertEquals(ReceiptOutcome.EFFECTED,confirmed.outcome());
        var archived=action("archive",confirmed,"archive");assertEquals("3",SiteSurveyBusinessApplicationService.result(archived).state());
        var other=commands.executeReceipt("create",null,null,content("second"),null,"other",OperationEntryKind.INDEPENDENT);
        var rejected=action("reject",other,"reject");assertEquals("2",SiteSurveyBusinessApplicationService.result(rejected).state());
        assertThrows(RuntimeException.class,()->action("confirm",rejected,"invalid"));
        var third=commands.executeReceipt("create",null,null,content("third"),null,"third",OperationEntryKind.INDEPENDENT);
        var deleted=action("delete",third,"delete");assertEquals(third.newConcurrencyBasis()+1,deleted.newConcurrencyBasis());
        assertTrue(SiteSurveyBusinessApplicationService.result(deleted).deleted());
        assertNull(ctx.getBean(SiteSurveyEntityMapper.class).selectById(third.entityRef().entityId()));
    }
    @Test void saveConfirmAndDeleteReplayReturnOriginalReceiptsAfterStateChanged() {
        var original=create();var saved=save(original,"save","edited");var afterSave=counts();
        assertEquals(saved,save(original,"save","edited"));assertEquals(afterSave,counts());
        var confirmed=action("confirm",saved,"confirm");var afterConfirm=counts();
        assertEquals(confirmed,action("confirm",saved,"confirm"));assertEquals(afterConfirm,counts());
        var draft=commands.executeReceipt("create",null,null,content("delete"),null,"draft",OperationEntryKind.INDEPENDENT);
        var deleted=action("delete",draft,"delete");var afterDelete=counts();
        assertEquals(deleted,action("delete",draft,"delete"));assertEquals(afterDelete,counts());
    }
    @Test void changedIntentRevokedPermissionsAndScopeCannotReadStoredReceipt() {
        var r=create();save(r,"save","edited");var before=counts();int attempts=ctx.getBean(ReservationProbe.class).attempts;
        assertThrows(RuntimeException.class,()->save(r,"save","different"));assertEquals(before,counts());
        when(ctx.getBean(PermissionApi.class).hasAnyPermissions(eq(9L),any(String[].class))).thenReturn(false);
        assertThrows(RuntimeException.class,()->save(r,"save","edited"));assertEquals(before,counts());
        assertEquals(attempts+1,ctx.getBean(ReservationProbe.class).attempts,"revocation must precede the reservation attempt");
        when(ctx.getBean(PermissionApi.class).hasAnyPermissions(eq(9L),any(String[].class))).thenReturn(true);
        when(ctx.getBean(ProjectScopeApi.class).resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class,()->save(r,"save","edited"));assertEquals(before,counts());
        assertEquals(attempts+1,ctx.getBean(ReservationProbe.class).attempts);
    }
    @Test void invalidExtensionsRollBackFixedColumnsLedgerAndAlreadyAppendedDomainOutbox() {
        var r=create();var before=counts();var input=content("must-rollback");input.setExtensionValues(Map.of("extra_flag","invalid"));
        assertThrows(RuntimeException.class,()->commands.executeReceipt("save",r.entityRef().entityId(),r.newConcurrencyBasis(),input,null,"bad-extension",OperationEntryKind.INDEPENDENT));
        assertEquals(before,counts());assertEquals("survey",jdbc.queryForObject("SELECT name FROM sol_site_survey",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT version FROM sol_site_survey",Long.class));
    }
    @Test void publicOutboxFailureRollsBackFixedExtensionsChildrenReceiptAuditAndBothEventFamilies() {
        var r=create();var before=counts();var reached=new AtomicBoolean();
        ctx.addApplicationListener(event->{if(event instanceof org.springframework.context.PayloadApplicationEvent<?> payload && payload.getPayload() instanceof PlatformOutboxAppended outbox && "pms.business.changed".equals(outbox.message().eventType())) {reached.set(true);throw new IllegalStateException("AFTER_PUBLIC_OUTBOX_INSERT");}});
        var input=content("must-rollback");input.setBusinessValues(Map.of("powerTypes",List.of("DC")));input.setExtensionValues(Map.of("extra_flag",true));
        assertThrows(RuntimeException.class,()->commands.executeReceipt("save",r.entityRef().entityId(),r.newConcurrencyBasis(),input,null,"failed-event",OperationEntryKind.INDEPENDENT));
        assertTrue(reached.get());assertEquals(before,counts());
        assertEquals("survey",jdbc.queryForObject("SELECT name FROM sol_site_survey",String.class));assertEquals("AC",jdbc.queryForObject("SELECT condition_code FROM sol_site_survey_condition",String.class));
        assertEquals("SN-1",jdbc.queryForObject("SELECT sn FROM sol_site_survey_material",String.class));
        assertEquals(Map.of("extra_flag",false),ctx.getBean(EntityExtensionApi.class).read(EntityDataRef.current(r.entityRef()),new EntityActor(1L,9L,"read")).fields());
    }
    @Test void staleBasisAndForgedProjectRejectWithoutPersistentEffects() {
        var r=create();save(r,"save","edited");var before=counts();
        assertThrows(RuntimeException.class,()->save(r,"stale","old"));assertEquals(before,counts());
        var forged=content("forged");forged.setProjectId(21L);int attempts=ctx.getBean(ReservationProbe.class).attempts;
        assertThrows(RuntimeException.class,()->commands.executeReceipt("save",r.entityRef().entityId(),2L,forged,null,"forged",OperationEntryKind.INDEPENDENT));
        assertEquals(attempts,ctx.getBean(ReservationProbe.class).attempts);assertEquals(before,counts());
    }
    @Test void concurrentSavesWithOneBasisHaveOneWinner() throws Exception {
        var r=create();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {var jobs=new ArrayList<Future<Boolean>>();for(int n=0;n<2;n++){final int k=n;jobs.add(pool.submit(()->{TenantContextHolder.setTenantId(1L);login();try{start.await();save(r,"race-"+k,"raced-"+k);return true;}catch(RuntimeException e){return false;}finally{TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}}));}
            start.countDown();int winners=0;for(var job:jobs)if(job.get(20,TimeUnit.SECONDS))winners++;assertEquals(1,winners);
            assertEquals(2,count("plt_idempotency_record"));assertEquals(2,jdbc.queryForObject("SELECT version FROM sol_site_survey",Long.class));
            assertEquals(1,count("sol_site_survey_condition"));assertEquals(1,count("sol_site_survey_material"));
        }finally{pool.shutdownNow();}
    }
    @Test void taskAndStageFramesRejectAnotherOperationObjectOrNodeBeforeAnyLedgerSQL() {
        var r=create();var before=counts();int attempts=ctx.getBean(ReservationProbe.class).attempts;
        for(boolean stage:new boolean[]{false,true}) {
            ProjectBusinessExecutionSelection selection=stage ? new ProjectBusinessExecutionSelection(null,new ProjectStageExecutionContext(20L,1L,30L,1,40L,1,50L,60L,1,1,true))
                    : new ProjectBusinessExecutionSelection(new ProjectTaskExecutionContext(20L,1L,30L,1,40L,1,50L,60L,1,1,70L,1,true,null),null);
            for(String fault:List.of("operation","object","node")) {
                var frame=new ProjectVerifiedOperationScope.Frame(1L,9L,20L,"SOL","SITE_SURVEY",fault.equals("operation") ? "SOL.SITE_SURVEY.CONFIRM" : "SOL.SITE_SURVEY.UPDATE",1,fault.equals("object") ? "999999" : r.entityRef().entityId().toString(),selection);
                try(var ignored=ProjectVerifiedOperationScope.open(frame)) {
                    assertThrows(IllegalStateException.class,()->commands.executeReceipt("save",r.entityRef().entityId(),r.newConcurrencyBasis(),content("wrong"),fault.equals("node") ? null : selection,"frame-"+stage+fault,OperationEntryKind.PROJECT_NODE));
                }
                assertEquals(before,counts());assertEquals(attempts,ctx.getBean(ReservationProbe.class).attempts);
            }
        }
    }
    @Test void nativeCompatibilityEntryUsesOneLedgerAndRejectsKeyedActionWithoutPinnedBasis() {
        var service=ctx.getBean(SiteSurveyEntityService.class);var http=new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(http));
        http.addHeader("Idempotency-Key","native-create");var id=service.createSiteSurveyEntity(content("native"));var before=counts();
        assertEquals(id,service.createSiteSurveyEntity(content("native")));assertEquals(before,counts());
        http.removeHeader("Idempotency-Key");http.addHeader("Idempotency-Key","native-confirm");
        assertThrows(RuntimeException.class,()->service.confirmSiteSurveyEntity(id,null));assertEquals(before,counts());
        http.addHeader("X-Expected-Version","1");service.confirmSiteSurveyEntity(id,null);var confirmed=counts();
        service.confirmSiteSurveyEntity(id,null);assertEquals(confirmed,counts());assertEquals(1,jdbc.queryForObject("SELECT status FROM sol_site_survey WHERE id=?",Integer.class,id));
        assertEquals(2,count("plt_idempotency_record"),"native entry must not keep a second native writer/ledger");
    }
    @Test void adapterCarriesExactReceiptAndOuterKeyAcrossAllSixCommandsInBothNodeKinds() {
        var adapter=ctx.getBean(SiteSurveyOperationCommandAdapter.class);
        for(boolean stage:new boolean[]{false,true}) {
            ProjectBusinessExecutionSelection selection=stage ? new ProjectBusinessExecutionSelection(null,new ProjectStageExecutionContext(20L,1L,30L,1,40L,1,50L,60L,1,1,true))
                    : new ProjectBusinessExecutionSelection(new ProjectTaskExecutionContext(20L,1L,30L,1,40L,1,50L,60L,1,1,70L,1,true,null),null);
            String kind=stage ? "STAGE" : "TASK",prefix=kind+"-";
            var created=invoke(adapter,"CREATE",null,null,content(prefix+"draft"),selection,prefix+"create",kind);
            var saved=invoke(adapter,"UPDATE",created.objectId(),created.objectVersion(),content(prefix+"edited"),selection,prefix+"save",kind);
            var confirmed=invoke(adapter,"CONFIRM",saved.objectId(),saved.objectVersion(),null,selection,prefix+"confirm",kind);
            var archived=invoke(adapter,"ARCHIVE",confirmed.objectId(),confirmed.objectVersion(),null,selection,prefix+"archive",kind);
            assertEquals("SURVEY_ARCHIVED",archived.resultCode());
            var rejectedDraft=invoke(adapter,"CREATE",null,null,content(prefix+"rejected"),selection,prefix+"rejected-create",kind);
            assertEquals("SURVEY_REJECTED",invoke(adapter,"REJECT",rejectedDraft.objectId(),rejectedDraft.objectVersion(),null,selection,prefix+"reject",kind).resultCode());
            var deletedDraft=invoke(adapter,"CREATE",null,null,content(prefix+"deleted"),selection,prefix+"deleted-create",kind);
            var deleted=invoke(adapter,"DELETE",deletedDraft.objectId(),deletedDraft.objectVersion(),null,selection,prefix+"delete",kind);var before=counts();
            assertEquals(deleted.response(),invoke(adapter,"DELETE",deletedDraft.objectId(),deletedDraft.objectVersion(),null,selection,prefix+"delete",kind).response());assertEquals(before,counts());
            assertEquals(deletedDraft.objectVersion()+1,deleted.objectVersion());
        }
        assertEquals(16,count("plt_idempotency_record"));
    }
    ProjectOperationResult invoke(SiteSurveyOperationCommandAdapter adapter,String action,String id,Long version,SiteSurveyEntitySaveReqVO input,ProjectBusinessExecutionSelection selection,String key,String kind) {
        String code="SOL.SITE_SURVEY."+action;
        var body=input==null ? cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree("{}") : cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(input));
        if(body.isObject())((tools.jackson.databind.node.ObjectNode)body).remove("execution");
        try(var frame=ProjectVerifiedOperationScope.open(new ProjectVerifiedOperationScope.Frame(1L,9L,20L,"SOL","SITE_SURVEY",code,1,id,selection))) {
            var result=adapter.invoke(code,new ProjectOperationCommand(20L,kind,30L,selection,id,version,null,body,key));
            assertEquals("SOL",result.ownerContext());assertEquals("SITE_SURVEY",result.objectType());
            assertTrue(result.response().has("operationReceipt"));var receipt=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(result.response().get("operationReceipt").toString(),BusinessOperationReceipt.class);
            assertEquals(result.objectId(),receipt.entityRef().entityId().toString());assertEquals(result.objectVersion(),receipt.newConcurrencyBasis());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record WHERE idempotency_key=? AND scope_code='biz:SOL/siteSurvey'",Integer.class,key));
            return result;
        }
    }
    static boolean mysqlOptIn() {return "true".equals(System.getProperty("npdms.survey.mysql.optIn"));}
    static String requiredEnv(String key) {String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException("MISSING_ISOLATED_TEST_ENV "+key);return value;}
    static void login() {cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(new cn.iocoder.yudao.framework.security.core.LoginUser().setId(9L).setTenantId(1L).setUserType(2),new org.springframework.mock.web.MockHttpServletRequest());}
    private static <T> T mock(Class<T> type) {return org.mockito.Mockito.mock(type,org.mockito.Mockito.withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));}
    @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(type=org.apache.ibatis.executor.Executor.class,method="update",args={org.apache.ibatis.mapping.MappedStatement.class,Object.class}))
    static class ReservationProbe implements org.apache.ibatis.plugin.Interceptor {
        int attempts;
        public Object intercept(org.apache.ibatis.plugin.Invocation call) throws Throwable {if(((org.apache.ibatis.mapping.MappedStatement)call.getArgs()[0]).getId().endsWith(".insertIfAbsent"))attempts++;return call.proceed();}
    }
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import({SiteSurveyEntityDomainCommands.class,SiteSurveyBusinessApplicationService.class,SiteSurveyEntityCommands.class,SiteSurveyEntityServiceImpl.class,SiteSurveyOperationCommandAdapter.class,
            SiteSurveyEntityWriteAccess.class,SiteSurveyEntityProvider.class,SiteSurveyEntityFormService.class,SiteSurveyDetails.class,
            EngineeringRecordCodeGenerator.class,EntityExtensionService.class,EntityProviderRegistry.class,PlatformOperationExecutionStore.class,
            OperationAuditApiImpl.class,PlatformTransactionalOutboxWriter.class,OutboxBusinessEventPort.class,EngineeringRuleReevaluationEvents.class,ProviderBusinessEntityContentReader.class})
    static class Config {
        @Bean DataSource dataSource() {
            if(mysqlOptIn()) {
                String id=requiredEnv("SURVEY_ISOLATED_ENV_ID"),schema=requiredEnv("SURVEY_ISOLATED_MYSQL_SCHEMA"),port=requiredEnv("SURVEY_ISOLATED_MYSQL_PORT");
                if(!id.matches("[0-9a-f]{32}")||!schema.equals("survey_it_"+id)||!port.matches("[0-9]{4,5}")||Integer.parseInt(port)<1024||Integer.parseInt(port)>65535||Set.of("13306","23316","24306","25306","25406").contains(port))throw new IllegalStateException("ISOLATED_MYSQL_SCOPE_INVALID");
                return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+port+"/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8","survey_it",requiredEnv("SURVEY_ISOLATED_DB_PASSWORD"));
            }
            return new DriverManagerDataSource("jdbc:h2:mem:survey_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE","sa","");
        }
        @Bean JdbcTemplate jdbc(DataSource ds) {
            var jdbc=new JdbcTemplate(ds);
            if(mysqlOptIn()) {
                String schema=requiredEnv("SURVEY_ISOLATED_MYSQL_SCHEMA"),id=requiredEnv("SURVEY_ISOLATED_ENV_ID");
                if(!schema.equals(jdbc.queryForObject("SELECT DATABASE()",String.class))
                        ||!id.equals(jdbc.queryForObject("SELECT environment_id FROM survey_test_environment",String.class))
                        ||!requiredEnv("SURVEY_ISOLATED_SERVER_UUID").equals(jdbc.queryForObject("SELECT @@server_uuid",String.class)))throw new IllegalStateException("ISOLATED_MYSQL_OWNERSHIP_MISMATCH");
                for(String table:List.of("plt_entity_extension_value","plt_entity_form_binding","plt_entity_extension_definition","sol_site_survey_condition","sol_site_survey_material","sol_site_survey","plt_idempotency_record","plt_operation_audit","plt_outbox_event"))jdbc.execute("DROP TABLE IF EXISTS "+table);
                try(var connection=ds.getConnection()) {
                    for(String file:List.of("V63_platform_tables.sql","V248_extension_tables.sql","V249__site_survey_typed_business_fields.sql"))
                        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new ClassPathResource("site-survey-isolated-mysql/"+file));
                }catch(java.sql.SQLException error){throw new IllegalStateException("ISOLATED_MYSQL_DDL_FAILED",error);}
                return jdbc;
            }
            for(Class<?> type:List.of(SiteSurveyEntityDO.class,SiteSurveyConditionDO.class,SiteSurveyMaterialDO.class,EntityExtensionDefinitionDO.class,EntityExtensionValueDO.class,EntityFormBindingDO.class,PlatformIdempotencyRecordDO.class,PlatformOperationAuditDO.class,PlatformOutboxEventDO.class))schema(jdbc,type);
            jdbc.execute("CREATE UNIQUE INDEX ledger_key ON plt_idempotency_record(tenant_id,scope_code,actor_id,idempotency_key)");
            jdbc.execute("CREATE UNIQUE INDEX extension_key ON plt_entity_extension_value(tenant_id,owner_module,entity_type,entity_id,revision_id)");
            jdbc.execute("CREATE UNIQUE INDEX code_key ON sol_site_survey(project_id,code)");return jdbc;
        }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) {return new DataSourceTransactionManager(ds);}
        @Bean ReservationProbe reservationProbe() {return new ReservationProbe();}
        @Bean SqlSessionFactory sessions(DataSource ds,JdbcTemplate jdbc,ReservationProbe probe) throws Exception {
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);
            var global=new com.baomidou.mybatisplus.core.config.GlobalConfig();global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());
            var properties=new com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties();properties.setGlobalConfig(global);
            new cn.iocoder.yudao.module.pms.engineering.config.SiteSurveyPersistenceConfiguration().siteSurveyAssignedIdentity().customize(properties);
            factory.setGlobalConfig(properties.getGlobalConfig());
            var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addInterceptor(probe);
            var optimistic=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();optimistic.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor());config.addInterceptor(optimistic);factory.setConfiguration(config);
            var resources=new ArrayList<Resource>();for(String file:List.of("src/main/resources/mapper/sitesurvey/entity/SiteSurveyEntityMapper.xml","src/main/resources/mapper/sitesurvey/entity/SiteSurveyDetailMapper.xml","../pms-module-platform/src/main/resources/mapper/entity/EntityCapabilityMapper.xml","../pms-module-platform/src/main/resources/mapper/command/PlatformIdempotencyRecordMapper.xml")){
                if(mysqlOptIn())resources.add(new FileSystemResource(file));else {String xml=Files.readString(Path.of(file)).replace("b'0'","0").replace("b'1'","1");resources.add(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8),file));}}
            factory.setMapperLocations(resources.toArray(Resource[]::new));var sf=factory.getObject();sf.getConfiguration().addMapper(PlatformOperationAuditMapper.class);sf.getConfiguration().addMapper(PlatformOutboxEventMapper.class);return sf;
        }
        @Bean SiteSurveyEntityMapper siteSurveyEntityMapper(SqlSessionFactory sf) {return new SqlSessionTemplate(sf).getMapper(SiteSurveyEntityMapper.class);}
        @Bean SiteSurveyDetailMapper siteSurveyDetailMapper(SqlSessionFactory sf) {return new SqlSessionTemplate(sf).getMapper(SiteSurveyDetailMapper.class);}
        @Bean EntityCapabilityMapper capabilities(SqlSessionFactory sf) {return new SqlSessionTemplate(sf).getMapper(EntityCapabilityMapper.class);}
        @Bean PlatformIdempotencyRecordMapper ledger(SqlSessionFactory sf) {return new SqlSessionTemplate(sf).getMapper(PlatformIdempotencyRecordMapper.class);}
        @Bean PlatformOperationAuditMapper audits(SqlSessionFactory sf) {return new SqlSessionTemplate(sf).getMapper(PlatformOperationAuditMapper.class);}
        @Bean PlatformOutboxEventMapper outbox(SqlSessionFactory sf) {return new SqlSessionTemplate(sf).getMapper(PlatformOutboxEventMapper.class);}
        @Bean PermissionApi permissions() {var p=mock(PermissionApi.class);when(p.hasAnyPermissions(eq(9L),any(String[].class))).thenReturn(true);return p;}
        @Bean ProjectScopeApi projectScopes() {var p=mock(ProjectScopeApi.class);var scope=new ProjectScopeResult(20L,2L,Set.of(20L),Set.of());when(p.resolveCurrent(any())).thenReturn(scope);when(p.lockAndRevalidate(any())).thenReturn(scope);return p;}
        @Bean ProjectCodeQueryApi codes() {var p=mock(ProjectCodeQueryApi.class);when(p.getProjectCode(20L)).thenReturn("IT20");return p;}
        @Bean ProjectBusinessExecutionApi execution() {return mock(ProjectBusinessExecutionApi.class);}
        @Bean ProjectEndDateApi deadlines() {return mock(ProjectEndDateApi.class);}
        @Bean EngineeringLocationFactService locationFactService() {return mock(EngineeringLocationFactService.class);}
        @Bean DynamicFormBusinessInstanceApi formRevisions() {return mock(DynamicFormBusinessInstanceApi.class);}
        @Bean EntityFormApi forms() {return mock(EntityFormApi.class);}
        @Bean ProjectBusinessResultRecordingApi results() {return mock(ProjectBusinessResultRecordingApi.class);}
        @Bean InheritedRevisionAdapterFactory inherited() {return mock(InheritedRevisionAdapterFactory.class);}
        @Bean BusinessAccessGuard accessGuard() {return mock(BusinessAccessGuard.class);}
        @Bean BusinessEntityPersistenceRegistry persistence(ObjectProvider<BusinessModelContributor> contributors) {return new BusinessEntityPersistenceRegistry(contributors);}
        @Bean BusinessEntityIdentityResolver identities(org.springframework.beans.factory.ListableBeanFactory beans) {return new BusinessEntityIdentityResolver(beans);}
        @Bean BusinessCallerContext caller() {return ()->new AbstractBusinessApplicationService.ResolvedCaller(TenantContextHolder.getRequiredTenantId(),cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),"survey-it");}
        @Bean jakarta.validation.Validator validator() {return jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();}
        @Bean BusinessModelDescriptor descriptor() {return new BusinessModelDescriptor("SOL","siteSurvey","SOL_SITE_SURVEY",1,BusinessModelKind.AGGREGATE_ROOT,"工勘",null,SiteSurveyEntityProvider.publicFields().stream().map(f->new BusinessFieldDescriptor(f.code(),f.code(),f.type(),f.required(),true,false,null)).toList(),List.of(),SiteSurveyBusinessApplicationService.operations(),List.of(new BusinessCapabilityBinding(BusinessCapabilityType.DYNAMIC_FORM,null,true)),"sol_site_survey");}
        @Bean BusinessModelCatalog catalog(BusinessModelDescriptor descriptor) {var c=mock(BusinessModelCatalog.class);when(c.require("SOL","siteSurvey")).thenReturn(descriptor);return c;}
        @Bean BusinessModelContributor contributor(BusinessModelDescriptor descriptor,SiteSurveyEntityMapper mapper) {return ()->List.of(new BusinessModelDeclaration(descriptor,SiteSurveyEntityDO.class,mapper,null));}
        @Bean BusinessOperationDispatcher dispatcher(BusinessEntityPersistenceRegistry p,SiteSurveyBusinessApplicationService owner,ObjectProvider<AbstractBusinessApplicationService<?>> services) {return new BusinessOperationDispatcher(p,owner,services.orderedStream().toList());}
        static void schema(JdbcTemplate jdbc,Class<?> type) {
            var columns=new LinkedHashMap<String,String>();for(Class<?> c=type;c!=Object.class;c=c.getSuperclass())for(Field f:c.getDeclaredFields()){
                if(Modifier.isStatic(f.getModifiers())||f.isSynthetic())continue;var a=f.getAnnotation(TableField.class);if(a!=null&&!a.exist())continue;
                String name=a!=null&&!a.value().isBlank()?a.value():f.getName().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
                Class<?> t=f.getType();String sql=t==Long.class||t==Integer.class||t==long.class||t==int.class?"BIGINT":t==Boolean.class||t==boolean.class?"BOOLEAN":t==java.time.LocalDateTime.class?"TIMESTAMP":t==java.time.LocalDate.class?"DATE":"VARCHAR(100000)";
                if(name.equals("id"))sql="BIGINT AUTO_INCREMENT PRIMARY KEY";else if(name.equals("deleted"))sql="BOOLEAN DEFAULT FALSE";else if(name.equals("create_time")||name.equals("update_time"))sql="TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
                columns.putIfAbsent(name,name+" "+sql);
            }
            jdbc.execute("CREATE TABLE "+type.getAnnotation(TableName.class).value()+" ("+String.join(",",columns.values())+")");
        }
    }
}
