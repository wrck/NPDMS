package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.*;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.*;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxAppended;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.PlatformOperationExecutionStore;
import cn.iocoder.yudao.module.pms.platform.service.command.*;
import cn.iocoder.yudao.module.pms.platform.service.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.revision.InheritedRevisionAdapterFactory;
import cn.iocoder.yudao.module.pms.project.api.scope.*;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.participant.*;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.*;
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

/** Isolated H2 acceptance. Real Spring transactions and production mappers/services; not MySQL certification. */
class RequirementAnalysisSpringPersistenceTest {
    AnnotationConfigApplicationContext ctx;
    JdbcTemplate jdbc;
    RequirementAnalysisEntityCommands commands;
    final EntityActor actor=new EntityActor(1L,9L,"ra-spring-it");
    long definition;
    @BeforeEach void open() {
        TenantContextHolder.setTenantId(1L);login();
        ctx=new AnnotationConfigApplicationContext(Config.class);
        jdbc=ctx.getBean(JdbcTemplate.class);commands=ctx.getBean(RequirementAnalysisEntityCommands.class);
        definition=ctx.getBean(EntityExtensionApi.class).publishDefinition(1L,"SOL","REQUIREMENT_ANALYSIS",
                List.of(new EntityExtensionApi.Definition("CUSTOM_FLAG","Flag",EntityField.Type.BOOLEAN,false,null,List.of())),actor).id();
    }
    @AfterEach void close() { if(ctx!=null){if(jdbc!=null && !mysqlOptIn())jdbc.execute("SHUTDOWN");ctx.close();}TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    EntityVersionProvider.Revision create() {return commands.create(new RequirementAnalysisEntityCommands.Create(20L,null),actor,"create");}
    EntityVersionProvider.Revision save(EntityVersionProvider.Revision r,String key,Map<String,Object> fields,int ev,Map<String,Object> ext) {
        return commands.save(r.ref(),r.version(),new RequirementAnalysisEntityCommands.Patch(fields,definition,ev,ext,null),actor,key);
    }
    long count(String t) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+t,Long.class);}
    Map<String,Long> counts() {var m=new LinkedHashMap<String,Long>();for(String t:List.of("sol_requirement_analysis","sol_requirement_analysis_revision","plt_entity_extension_value","plt_idempotency_record","plt_operation_audit","plt_outbox_event"))m.put(t,count(t));return m;}
    Map<String,Object> required() {var m=new LinkedHashMap<String,Object>();for(var f:RequirementAnalysisEntityProvider.FIELDS.fields())if(f.required())m.put(f.code(),"<p>required</p>");return m;}
    @Test void springDiscoversRealOwnerAndTransactionalProviders() {
        assertTrue(AopUtils.isCglibProxy(ctx.getBean(RequirementAnalysisDomainCommands.class)));
        assertTrue(AopUtils.isCglibProxy(ctx.getBean(RequirementAnalysisEntityProvider.class)));
        assertTrue(AopUtils.isAopProxy(ctx.getBean(EntityExtensionApi.class)));
        var r=create();assertEquals(1,count("sol_requirement_analysis_revision"));
        assertEquals(1,count("plt_idempotency_record"));assertEquals(2,count("plt_operation_audit")-1);
        assertEquals("DRAFT",jdbc.queryForObject("SELECT revision_state FROM sol_requirement_analysis_revision",String.class));
    }
    @Test void replayConflictAndRevokedPermissionPreservePersistedFacts() {
        var r=create();var saved=save(r,"save",Map.of("projectBackground","<p>body</p>"),0,Map.of("CUSTOM_FLAG",false));
        var before=counts();assertEquals(saved,save(r,"save",Map.of("projectBackground","<p>body</p>"),0,Map.of("CUSTOM_FLAG",false)));assertEquals(before,counts());
        assertThrows(RuntimeException.class,()->save(r,"save",Map.of("projectBackground","different"),0,Map.of("CUSTOM_FLAG",false)));assertEquals(before,counts());
        when(ctx.getBean(PermissionApi.class).hasAnyPermissions(eq(9L),any(String[].class))).thenReturn(false);
        assertThrows(RuntimeException.class,()->save(r,"save",Map.of("projectBackground","<p>body</p>"),0,Map.of("CUSTOM_FLAG",false)));assertEquals(before,counts());
    }
    @Test void extensionAndBodyFailuresRollBackAllTables() {
        var r=create();var before=counts();
        assertThrows(RuntimeException.class,()->save(r,"bad-extension",Map.of("projectBackground","body"),0,Map.of("CUSTOM_FLAG","notBoolean")));assertEquals(before,counts());
        assertThrows(RuntimeException.class,()->save(r,"bad-body",Map.of("NONEXISTENT_FIELD","body"),0,Map.of("CUSTOM_FLAG",false)));assertEquals(before,counts());
        assertEquals(1,jdbc.queryForObject("SELECT version FROM sol_requirement_analysis_revision",Integer.class));
    }
    @Test void eventFailureRollsBackFreezeActivationExtensionsLedgerAuditAndOutbox() {
        var r=save(create(),"ready",required(),0,Map.of("CUSTOM_FLAG",false));var before=counts();
        var reachedOutbox=new AtomicBoolean();ctx.addApplicationListener(event->{if(event instanceof org.springframework.context.PayloadApplicationEvent<?> payload && payload.getPayload() instanceof PlatformOutboxAppended){reachedOutbox.set(true);throw new IllegalStateException("INJECT_AFTER_OUTBOX_INSERT");}});
        assertThrows(RuntimeException.class,()->commands.complete(r.ref(),r.version(),new RequirementAnalysisEntityCommands.Action(null,null),actor,"complete-failed"));
        assertTrue(reachedOutbox.get(),"failure must be injected after a real Outbox insert");assertEquals(before,counts());assertEquals("DRAFT",jdbc.queryForObject("SELECT revision_state FROM sol_requirement_analysis_revision",String.class));
        assertEquals(r.version(),jdbc.queryForObject("SELECT version FROM sol_requirement_analysis_revision",Integer.class));
    }
    @Test void completeFrozenReplayAndStaleSaveDoNotProduceAdditionalEvents() {
        var r=save(create(),"ready",required(),0,Map.of("CUSTOM_FLAG",false));
        var complete=commands.complete(r.ref(),r.version(),new RequirementAnalysisEntityCommands.Action(null,null),actor,"complete");
        assertTrue(complete.effective());assertEquals(1,count("sol_requirement_analysis"));assertEquals(2,count("plt_outbox_event"));
        var before=counts();assertEquals(complete,commands.complete(r.ref(),r.version(),new RequirementAnalysisEntityCommands.Action(null,null),actor,"complete"));assertEquals(before,counts());
        assertThrows(RuntimeException.class,()->save(r,"stale-save",Map.of(),1,Map.of("CUSTOM_FLAG",true)));assertEquals(before,counts());
    }
    @Test void concurrentSavesWithOneVersionHaveOneWinner() throws Exception {
        var r=create();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var jobs=new ArrayList<Future<Boolean>>();for(int n=0;n<2;n++){final int k=n;jobs.add(pool.submit(()->{TenantContextHolder.setTenantId(1L);login();try{start.await();save(r,"race-"+k,Map.of("projectBackground","body"+k),0,Map.of("CUSTOM_FLAG",false));return true;}catch(RuntimeException e){return false;}finally{TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}}));}
            start.countDown();int winners=0;for(var job:jobs)if(job.get(20,TimeUnit.SECONDS))winners++;assertEquals(1,winners);
            assertEquals(2,count("plt_idempotency_record"));assertEquals(1,count("plt_entity_extension_value"));assertEquals(2,jdbc.queryForObject("SELECT version FROM sol_requirement_analysis_revision",Integer.class));
        } finally {pool.shutdownNow();}
    }

    @Test void concurrentSaveAndCompleteWithOneVersionHaveOneWinner() throws Exception {
        var r=save(create(),"ready",required(),0,Map.of("CUSTOM_FLAG",false));var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> saving=()->{TenantContextHolder.setTenantId(1L);login();try{start.await();save(r,"race-save",Map.of("projectBackground","raced"),1,Map.of("CUSTOM_FLAG",true));return true;}catch(RuntimeException e){return false;}finally{TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}};
            Callable<Boolean> completing=()->{TenantContextHolder.setTenantId(1L);login();try{start.await();commands.complete(r.ref(),r.version(),new RequirementAnalysisEntityCommands.Action(null,null),actor,"race-complete");return true;}catch(RuntimeException e){return false;}finally{TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}};
            var first=pool.submit(saving);var second=pool.submit(completing);start.countDown();boolean saved=first.get(20,TimeUnit.SECONDS),completed=second.get(20,TimeUnit.SECONDS);assertNotEquals(saved,completed);
            assertEquals(3,count("plt_idempotency_record"));assertEquals(completed?1:0,count("sol_requirement_analysis"));assertEquals(completed?2:0,count("plt_outbox_event"));
        } finally {pool.shutdownNow();}
    }
    @Test void originalPlatformApiReadsTheSamePersistentNativeReceipt() {
        var r=create();var before=counts();String digest=jdbc.queryForObject("SELECT request_digest FROM plt_idempotency_record WHERE idempotency_key='create'",String.class);
        var replay=ctx.getBean(PlatformCommandExecutionApi.class).execute(new PlatformCommandExecutionApi.IdempotencyScope(1L,"RA_ENTITY_CREATE",9L,"create"),digest,EntityVersionProvider.Revision.class,
                ()->{throw new AssertionError("legacy replay must not execute domain");},x->{throw new AssertionError("legacy replay must not record facts");});
        assertEquals(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,replay.decision());assertEquals(r,replay.response());assertEquals(before,counts());
    }
    @Test void mismatchedControlledOperationObjectOrNodeRejectsBeforeLedgerReservation() {
        var r=create();var before=counts();int attempts=ctx.getBean(ReservationProbe.class).attempts;
        var task=new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(20L,1L,30L,1,40L,1,50L,60L,1,1,70L,1,true,null);
        var selection=new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection(task,null);
        for(String fault:List.of("operation","object","node")) {
            var frame=new ProjectVerifiedOperationScope.Frame(1L,9L,20L,"SOL","REQUIREMENT_ANALYSIS",fault.equals("operation")?"SOL.REQUIREMENT_ANALYSIS.COMPLETE":"SOL.REQUIREMENT_ANALYSIS.SAVE",1,fault.equals("object")?"999999":r.ref().revisionId().toString(),selection);
            try(var ignored=ProjectVerifiedOperationScope.open(frame)) {
                var requested=fault.equals("node")?new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection(new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(20L,1L,31L,1,40L,1,50L,60L,1,1,70L,1,true,null),null):selection;
                assertThrows(IllegalStateException.class,()->commands.save(r.ref(),r.version(),new RequirementAnalysisEntityCommands.Patch(Map.of(),null,0,null,requested),actor,"frame-"+fault));
            }
            assertEquals(before,counts());assertEquals(attempts,ctx.getBean(ReservationProbe.class).attempts,"frame rejection must precede any ledger SQL");
        }
    }

    static void login() {
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(
            new cn.iocoder.yudao.framework.security.core.LoginUser().setId(9L).setUserType(2),new org.springframework.mock.web.MockHttpServletRequest());
    }
    static boolean mysqlOptIn() {return "true".equals(System.getProperty("npdms.ra.mysql.optIn"));}
    static String requiredEnv(String name) {String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalStateException("Missing isolated test environment: "+name);return value;}
    private static <T> T mock(Class<T> type) {return org.mockito.Mockito.mock(type,org.mockito.Mockito.withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));}
    @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(type=org.apache.ibatis.executor.Executor.class,method="update",args={org.apache.ibatis.mapping.MappedStatement.class,Object.class}))
    static class ReservationProbe implements org.apache.ibatis.plugin.Interceptor {
        int attempts;
        public Object intercept(org.apache.ibatis.plugin.Invocation invocation) throws Throwable {
            if(((org.apache.ibatis.mapping.MappedStatement)invocation.getArgs()[0]).getId().endsWith(".insertIfAbsent"))attempts++;
            return invocation.proceed();
        }
    }
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @Import({RequirementAnalysisAccess.class,RequirementAnalysisExecutionAccess.class,RequirementAnalysisEntityProvider.class,
            RequirementAnalysisDomainCommands.class,RequirementAnalysisBusinessApplicationService.class,RequirementAnalysisEntityCommands.class,
            EntityExtensionService.class,EntityVersionService.class,EntityProviderRegistry.class,PlatformOperationExecutionStore.class,
            PlatformCommandExecutionApiImpl.class,OperationAuditApiImpl.class,PlatformTransactionalOutboxWriter.class,
            EngineeringRuleReevaluationEvents.class,EngineeringOperationResultSource.class})
    static class Config {
        @Bean DataSource dataSource() {
            if(mysqlOptIn()) {
                String id=requiredEnv("RA_ISOLATED_ENV_ID"),schema=requiredEnv("RA_ISOLATED_MYSQL_SCHEMA"),port=requiredEnv("RA_ISOLATED_MYSQL_PORT");
                if(!id.matches("[0-9a-f]{32}")||!schema.equals("ra_it_"+id)||!port.matches("[0-9]{4,5}")||Integer.parseInt(port)<1024||Integer.parseInt(port)>65535||port.equals("23316"))throw new IllegalStateException("ISOLATED_MYSQL_SCOPE_INVALID");
                return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:"+port+"/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8", "ra_it", requiredEnv("RA_ISOLATED_DB_PASSWORD"));
            }
            var ds=new DriverManagerDataSource("jdbc:h2:mem:ra_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE", "sa", "");return ds;}
        @Bean JdbcTemplate jdbc(DataSource ds) {var jdbc=new JdbcTemplate(ds);
            if(mysqlOptIn()) {
                // Check own random schema and the separately-created container marker BEFORE any destructive SQL.
                String schema=requiredEnv("RA_ISOLATED_MYSQL_SCHEMA"),id=requiredEnv("RA_ISOLATED_ENV_ID");
                if(!schema.equals(jdbc.queryForObject("SELECT DATABASE()",String.class))
                        ||!id.equals(jdbc.queryForObject("SELECT environment_id FROM ra_test_environment",String.class))
                        ||!requiredEnv("RA_ISOLATED_SERVER_UUID").equals(jdbc.queryForObject("SELECT @@server_uuid",String.class)))throw new IllegalStateException("ISOLATED_MYSQL_OWNERSHIP_MISMATCH");
                for(String table:List.of("plt_entity_extension_value","plt_entity_form_binding","plt_entity_extension_definition","sol_requirement_analysis_revision","sol_requirement_analysis","plt_idempotency_record","plt_operation_audit","plt_outbox_event"))jdbc.execute("DROP TABLE IF EXISTS "+table);
                try(var connection=ds.getConnection()) {
                    for(String file:List.of("V63_platform_tables.sql","V248__entity_capabilities_and_requirement_revision.sql","V321__requirement_analysis_business_fields.sql"))
                        org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new ClassPathResource("ra-isolated-mysql/"+file));
                }catch(java.sql.SQLException error){throw new IllegalStateException("ISOLATED_MYSQL_DDL_FAILED",error);}
                return jdbc;
            }
            for(Class<?> type:List.of(RequirementAnalysisDO.class,RequirementAnalysisRevisionDO.class,EntityExtensionDefinitionDO.class,EntityExtensionValueDO.class,EntityFormBindingDO.class,PlatformIdempotencyRecordDO.class,PlatformOperationAuditDO.class,PlatformOutboxEventDO.class))schema(jdbc,type);jdbc.execute("CREATE UNIQUE INDEX ledger_key ON plt_idempotency_record(tenant_id,scope_code,actor_id,idempotency_key)");jdbc.execute("CREATE UNIQUE INDEX extension_key ON plt_entity_extension_value(tenant_id,owner_module,entity_type,entity_id,revision_id)");jdbc.execute("CREATE UNIQUE INDEX draft_key ON sol_requirement_analysis_revision(tenant_id,project_id,draft_marker)");return jdbc;}
        @Bean PlatformTransactionManager transactionManager(DataSource ds) {return new DataSourceTransactionManager(ds);}
        @Bean ReservationProbe reservationProbe() {return new ReservationProbe();}
        @Bean SqlSessionFactory sessions(DataSource ds,JdbcTemplate jdbc,ReservationProbe probe) throws Exception {
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);
            var global=new com.baomidou.mybatisplus.core.config.GlobalConfig();global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());factory.setGlobalConfig(global);var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addInterceptor(probe);factory.setConfiguration(config);
            var resources=new ArrayList<Resource>();for(String file:List.of("src/main/resources/mapper/requirement/RequirementAnalysisMapper.xml","../pms-module-platform/src/main/resources/mapper/entity/EntityCapabilityMapper.xml","../pms-module-platform/src/main/resources/mapper/command/PlatformIdempotencyRecordMapper.xml")){
                // H2 cannot parse MySQL binary literals. Only this test's loaded XML bytes use numeric equivalents.
                if(mysqlOptIn()) resources.add(new FileSystemResource(file));
                else {String xml=Files.readString(Path.of(file)).replace("b'0'","0").replace("b'1'","1");resources.add(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8),file));}}
            factory.setMapperLocations(resources.toArray(Resource[]::new));var sf=factory.getObject();sf.getConfiguration().addMapper(PlatformOperationAuditMapper.class);sf.getConfiguration().addMapper(PlatformOutboxEventMapper.class);return sf;
        }
        @Bean RequirementAnalysisMapper requirements(SqlSessionFactory sf) {return new SqlSessionTemplate(sf).getMapper(RequirementAnalysisMapper.class);}
        @Bean EntityCapabilityMapper capabilities(SqlSessionFactory sf) {return new SqlSessionTemplate(sf).getMapper(EntityCapabilityMapper.class);}
        @Bean PlatformIdempotencyRecordMapper ledger(SqlSessionFactory sf) {return new SqlSessionTemplate(sf).getMapper(PlatformIdempotencyRecordMapper.class);}
        @Bean PlatformOperationAuditMapper audits(SqlSessionFactory sf) {return new SqlSessionTemplate(sf).getMapper(PlatformOperationAuditMapper.class);}
        @Bean PlatformOutboxEventMapper outbox(SqlSessionFactory sf) {return new SqlSessionTemplate(sf).getMapper(PlatformOutboxEventMapper.class);}
        @Bean PermissionApi permissions() {var p=mock(PermissionApi.class);when(p.hasAnyPermissions(eq(9L),any(String[].class))).thenReturn(true);return p;}
        @Bean ProjectScopeApi scopes() {var p=mock(ProjectScopeApi.class);var s=new ProjectScopeResult(20L,2L,Set.of(20L),Set.of());when(p.resolveCurrent(any())).thenReturn(s);when(p.lockAndRevalidate(any())).thenReturn(s);return p;}
        @Bean ProjectParticipantFactApi participants() {var p=mock(ProjectParticipantFactApi.class);var f=new ProjectParticipantFact(20L,9L,Set.of("PROJECT_MANAGER"),"PRIMARY","ACTIVE","S1",2L,2L);when(p.inspect(any())).thenReturn(f);when(p.lockAndRevalidate(any())).thenReturn(f);return p;}
        @Bean ProjectNodeExecutionApi nodes() {return mock(ProjectNodeExecutionApi.class);}
        @Bean ProjectWorkBindingFactApi bindings() {return mock(ProjectWorkBindingFactApi.class);}
        @Bean ProjectBusinessExecutionApi guard() {return mock(ProjectBusinessExecutionApi.class);}
        @Bean EntityFormApi forms() {return mock(EntityFormApi.class);}
        @Bean RequirementAnalysisRevisionFiles files() {return mock(RequirementAnalysisRevisionFiles.class);}
        @Bean ProjectBusinessResultRecordingApi results() {return mock(ProjectBusinessResultRecordingApi.class);}
        @Bean InheritedRevisionAdapterFactory inherited() {return mock(InheritedRevisionAdapterFactory.class);}
        @Bean BusinessAccessGuard accessGuard() {return mock(BusinessAccessGuard.class);}
        @Bean BusinessEventPort events() {return mock(BusinessEventPort.class);}
        @Bean BusinessEntityPersistenceRegistry persistence() {return mock(BusinessEntityPersistenceRegistry.class);}
        @Bean BusinessCallerContext caller() {return ()->new AbstractBusinessApplicationService.ResolvedCaller(1L,9L,"ra-spring-it");}
        @Bean BusinessModelCatalog catalog() {var c=mock(BusinessModelCatalog.class);var ops=List.of("create","save","complete","copy").stream().map(code->new BusinessOperationDescriptor(code,1,code,BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND)).toList();when(c.require("SOL","requirementAnalysis")).thenReturn(new BusinessModelDescriptor("SOL","requirementAnalysis","SOL_REQUIREMENT_ANALYSIS",1,BusinessModelKind.AGGREGATE_ROOT,"RA",null,List.of(),List.of(),ops,List.of(),"sol_requirement_analysis"));return c;}
        @Bean BusinessOperationDispatcher dispatcher(BusinessEntityPersistenceRegistry p,RequirementAnalysisBusinessApplicationService ra,ObjectProvider<AbstractBusinessApplicationService<?>> owners) {return new BusinessOperationDispatcher(p,ra,owners.orderedStream().toList());}
        static void schema(JdbcTemplate jdbc,Class<?> type) {
            var columns=new LinkedHashMap<String,String>();for(Class<?> c=type;c!=Object.class;c=c.getSuperclass())for(Field f:c.getDeclaredFields()){
                if(Modifier.isStatic(f.getModifiers())||f.isSynthetic())continue;var a=f.getAnnotation(TableField.class);if(a!=null&&!a.exist())continue;
                String name=a!=null&&!a.value().isBlank()?a.value():f.getName().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
                Class<?> t=f.getType();String sql=t==Long.class||t==Integer.class||t==long.class||t==int.class?"BIGINT":t==Boolean.class||t==boolean.class?"BOOLEAN":t==java.time.LocalDateTime.class?"TIMESTAMP":"VARCHAR(100000)";
                if(name.equals("id"))sql="BIGINT AUTO_INCREMENT PRIMARY KEY";else if(name.equals("deleted"))sql="BOOLEAN DEFAULT FALSE";else if(name.equals("create_time")||name.equals("update_time"))sql="TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
                columns.putIfAbsent(name,name+" "+sql);
            }
            jdbc.execute("CREATE TABLE "+type.getAnnotation(TableName.class).value()+" ("+String.join(",",columns.values())+")");
        }
    }
}
