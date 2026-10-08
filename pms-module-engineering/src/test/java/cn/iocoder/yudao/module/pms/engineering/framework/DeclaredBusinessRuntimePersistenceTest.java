package cn.iocoder.yudao.module.pms.engineering.framework;

import cn.iocoder.yudao.module.pms.platform.service.businessmodel.*;
import cn.iocoder.yudao.module.pms.platform.support.access.DeclaredBusinessScopeSupport;
import cn.iocoder.yudao.module.system.api.permission.PermissionApiImpl;
import cn.iocoder.yudao.module.system.service.permission.*;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.*;
import cn.iocoder.yudao.module.system.dal.mysql.permission.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApiImpl;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.authorization.AuthorizationGrantDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.AuthorizationGrantMapper;
import cn.iocoder.yudao.module.pms.platform.service.authorization.AuthorizationGrantService;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.*;
import cn.iocoder.yudao.module.pms.platform.service.command.*;
import cn.iocoder.yudao.module.pms.platform.support.access.DefaultBusinessEntityAccess;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.*;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated random H2; real production mappers, transaction, ledger, audit, outbox and permission guard.
 * Real permission services, project scope services and tenant/CAS interceptor. No HTTP/MySQL certification. */
class DeclaredBusinessRuntimePersistenceTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    BusinessOperationDispatcher dispatcher;
    @BeforeEach void open() {
        login();
        context=new AnnotationConfigApplicationContext(Config.class);
        jdbc=context.getBean(JdbcTemplate.class); dispatcher=context.getBean(BusinessOperationDispatcher.class);
    }
    @AfterEach void close() { if(context!=null) { if(jdbc!=null) jdbc.execute("SHUTDOWN"); context.close(); } SecurityContextHolder.clearContext(); TenantContextHolder.clear(); }
    private static void login() {
        TenantContextHolder.setTenantId(7L);
        var principal = new LoginUser(); principal.setId(9L); principal.setTenantId(7L); principal.setUserType(2);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
    BusinessOperationRequest create(String key,Map<String,Object> values) {
        return new BusinessOperationRequest("create",1,null,"TEST","fieldNote",values,key,null,OperationEntryKind.INDEPENDENT,"framework-test");
    }
    BusinessOperationReceipt create() { return dispatcher.dispatch(create("create",Map.of("title","first","projectRef",20L))); }
    BusinessOperationRequest save(BusinessOperationReceipt receipt,Long basis,String key) {
        return new BusinessOperationRequest("save",1,EntityDataRef.current(receipt.entityRef()),null,null,Map.of("title","edited"),key,basis,OperationEntryKind.INDEPENDENT,"framework-test");
    }
    Map<String,Long> counts() {
        var result=new LinkedHashMap<String,Long>();
        for(String table:List.of("test_field_note","plt_idempotency_record","plt_operation_audit","plt_outbox_event"))
            result.put(table,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class));
        return result;
    }
    @Test void unreadableFieldDoesNotLeakThroughDefaultReadOrPage() {
        var receipt=create();
        var access=context.getBean(DefaultBusinessEntityAccess.class);
        var data=access.read(EntityDataRef.current(receipt.entityRef()),new EntityActor(7L,9L,"read"),"detail");
        assertFalse(data.fieldValues().containsKey("privateMemo"));
        assertEquals("first",data.fieldValues().get("title"));
        var page=access.query(new BusinessEntityPageQuery("page","TEST","fieldNote",List.of(),20,null),new EntityActor(7L,9L,"page"));
        assertEquals(1,page.members().size());
        assertFalse(page.members().getFirst().fieldValues().containsKey("privateMemo"));
    }
    @Test void updateMustCarryClientVersionBeforeAnyPersistentEffects() {
        var receipt=create(); var before=counts();
        assertThrows(RuntimeException.class,()->dispatcher.dispatch(save(receipt,null,"missing-basis")));
        assertEquals(before,counts());
        assertEquals("first",jdbc.queryForObject("SELECT title FROM test_field_note",String.class));
    }
    @Test void requiredInputIsValidatedOnServerBeforeInsert() {
        var before=counts();
        assertThrows(RuntimeException.class,()->dispatcher.dispatch(create("missing-title",Map.of("projectRef",20L))));
        assertEquals(before,counts());
    }
    @Test void queryPermissionPlusPublicOperateCannotCreate() {
        jdbc.update("DELETE FROM system_role_menu WHERE menu_id=102");
        var before=counts();
        assertThrows(RuntimeException.class,()->create());
        assertEquals(before,counts());
    }
    @Test void sameIntentUsesProductionPersistentLedgerAuditAndOutboxOnce() {
        var request=create("stable-key",Map.of("title","persisted","projectRef",20L));
        var first=dispatcher.dispatch(request); var before=counts();
        assertEquals(first,dispatcher.dispatch(request)); assertEquals(before,counts());
        assertEquals(Map.of("test_field_note",1L,"plt_idempotency_record",1L,"plt_operation_audit",1L,"plt_outbox_event",1L),counts());
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM plt_idempotency_record",String.class));
        assertEquals("pms.business.changed",jdbc.queryForObject("SELECT event_type FROM plt_outbox_event",String.class));
    }
    @Test void outboxFailureRollsBackEntityLedgerAndAudit() {
        var before=counts();
        context.addApplicationListener(event->{ if(event instanceof org.springframework.context.PayloadApplicationEvent<?> payload
            && payload.getPayload() instanceof cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxAppended)
            throw new IllegalStateException("AFTER_REAL_OUTBOX_INSERT"); });
        assertThrows(RuntimeException.class,()->create()); assertEquals(before,counts());
    }
    @Test void queryPermissionPlusPublicOperateCannotSaveOrReadOriginalWriteReceipt() {
        var receipt=create(); var before=counts();
        jdbc.update("DELETE FROM system_role_menu WHERE menu_id=102");
        assertThrows(RuntimeException.class,()->dispatcher.dispatch(save(receipt,0L,"query-only-save")));
        assertThrows(RuntimeException.class,()->create());assertEquals(before,counts());
    }
    @Test void patchOmissionPreservesRequiredValueButExplicitClearFails() {
        var receipt=create();var target=EntityDataRef.current(receipt.entityRef());
        var unchanged=dispatcher.dispatch(new BusinessOperationRequest("save",1,target,null,null,Map.of(),"empty-patch",0L,OperationEntryKind.INDEPENDENT,"patch"));
        assertEquals("first",jdbc.queryForObject("SELECT title FROM test_field_note",String.class));var before=counts();
        for(String clear:List.of("","   ")) assertThrows(RuntimeException.class,()->dispatcher.dispatch(new BusinessOperationRequest("save",1,target,null,null,Map.of("title",clear),"clear-"+clear.length(),unchanged.newConcurrencyBasis(),OperationEntryKind.INDEPENDENT,"patch")));
        var nullValue=new HashMap<String,Object>();nullValue.put("title",null);
        assertThrows(RuntimeException.class,()->dispatcher.dispatch(new BusinessOperationRequest("save",1,target,null,null,nullValue,"clear-null",unchanged.newConcurrencyBasis(),OperationEntryKind.INDEPENDENT,"patch")));
        assertEquals(before,counts());
    }
    @Test void declaredProjectRefScopesCreateUpdateAndRejectsOtherTenantProject() {
        var receipt=create();assertEquals(20L,jdbc.queryForObject("SELECT project_ref FROM test_field_note",Long.class));var before=counts();
        assertThrows(RuntimeException.class,()->dispatcher.dispatch(create("foreign-project",Map.of("title","foreign","projectRef",30L))));
        assertThrows(RuntimeException.class,()->dispatcher.dispatch(new BusinessOperationRequest("save",1,EntityDataRef.current(receipt.entityRef()),null,null,Map.of("projectRef",21L),"reparent",0L,OperationEntryKind.INDEPENDENT,"scope")));
        assertEquals(before,counts());
    }
    @Test void emptyScopeDoesNotExpandPageAndRevocationRejectsWriteReplay() {
        var receipt=create();var before=counts();jdbc.update("DELETE FROM proj_project_member_assignment WHERE user_id=9");
        var access=context.getBean(DefaultBusinessEntityAccess.class);
        assertTrue(access.query(new BusinessEntityPageQuery("page","TEST","fieldNote",List.of(),20,null),new EntityActor(7L,9L,"page")).members().isEmpty());
        assertThrows(RuntimeException.class,()->access.read(EntityDataRef.current(receipt.entityRef()),new EntityActor(7L,9L,"read"),"detail"));
        assertThrows(RuntimeException.class,()->create());assertEquals(before,counts());
    }
    @Test void tenantInterceptorAndIdentityRejectForeignEntity() {
        var receipt=create();var before=counts();TenantContextHolder.setTenantId(8L);
        try {assertNull(context.getBean(FieldNoteMapper.class).selectById(receipt.entityRef().entityId()));
            assertThrows(RuntimeException.class,()->dispatcher.dispatch(save(receipt,0L,"foreign-entity")));
        } finally {TenantContextHolder.setTenantId(7L);}assertEquals(before,counts());
    }
    @Test void concurrentTransactionsWithOneVersionHaveExactlyOneWinner() throws Exception {
        var receipt=create();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {var jobs=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++){int index=i;jobs.add(pool.submit(()->{login();try {start.await();dispatcher.dispatch(save(receipt,0L,"race-"+index));return true;}catch(RuntimeException failed){return false;}finally{SecurityContextHolder.clearContext();TenantContextHolder.clear();}}));}
            start.countDown();int winners=0;for(var job:jobs)if(job.get(20,TimeUnit.SECONDS))winners++;assertEquals(1,winners);
            assertEquals(1L,jdbc.queryForObject("SELECT version FROM test_field_note",Long.class));
            assertEquals(2L,counts().get("plt_idempotency_record"));assertEquals(2L,counts().get("plt_operation_audit"));assertEquals(2L,counts().get("plt_outbox_event"));
        } finally {pool.shutdownNow();}
    }
    @Test void auditInsertFailureRollsBackBodyAndReservedLedger() {
        var before=counts();jdbc.execute("ALTER TABLE plt_operation_audit ADD CONSTRAINT fail_success_audit CHECK(result_code <> 'SUCCESS')");
        assertThrows(RuntimeException.class,()->create());assertEquals(before,counts());
    }
    @Test void changedIntentUnderOriginalKeyIsRejectedWithoutEffects() {
        create();var before=counts();assertThrows(RuntimeException.class,()->dispatcher.dispatch(create("create",Map.of("title","changed","projectRef",20L))));assertEquals(before,counts());
    }
    @TableName("test_field_note")
    public static class FieldNote extends BaseBusinessEntity {
        private String title;
        private Long projectRef;
        private String privateMemo="internal-only";
        public String getTitle(){return title;} public void setTitle(String value){title=value;}
        public Long getProjectRef(){return projectRef;} public void setProjectRef(Long value){projectRef=value;}
        public String getPrivateMemo(){return privateMemo;} public void setPrivateMemo(String value){privateMemo=value;}
    }
    public interface FieldNoteMapper extends BaseMapper<FieldNote> {}
    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    @Import({PlatformOperationExecutionStore.class,OperationAuditApiImpl.class,PlatformTransactionalOutboxWriter.class,OutboxBusinessEventPort.class,PermissionBusinessAccessGuard.class,
        PermissionApiImpl.class,PermissionServiceImpl.class,RoleServiceImpl.class,MenuServiceImpl.class,PermissionAssociationService.class,
        ProjectScopeApiImpl.class,ProjectTreeScopeService.class,ProjectBusinessScopeAccess.class,AuthorizationGrantService.class,PlatformCommandExecutionApiImpl.class})
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:framework_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");}
        @Bean JdbcTemplate jdbc(DataSource source) {
            var jdbc=new JdbcTemplate(source);
            for(Class<?> type:List.of(FieldNote.class,PlatformIdempotencyRecordDO.class,PlatformOperationAuditDO.class,PlatformOutboxEventDO.class,
                RoleDO.class,MenuDO.class,RoleMenuDO.class,UserRoleDO.class,ProjectMasterDO.class,ProjectMemberAssignmentDO.class,ProjectTreePathDO.class,ProjectTreeVersionDO.class,AuthorizationGrantDO.class)) schema(jdbc,type);
            jdbc.execute("CREATE UNIQUE INDEX ledger_key ON plt_idempotency_record(tenant_id,scope_code,actor_id,idempotency_key)");
            jdbc.update("INSERT INTO system_role(id,tenant_id,name,code,status,type) VALUES(1,7,'Tester','framework_tester',0,2)");
            jdbc.update("INSERT INTO system_user_role(user_id,role_id) VALUES(9,1)");
            int id=101;for(String code:List.of("test:field-note:query","test:field-note:write","pms:business-model:operate")) {
                jdbc.update("INSERT INTO system_menu(id,name,permission,status,type,parent_id) VALUES(?,? ,?,0,3,0)",id,code,code);
                jdbc.update("INSERT INTO system_role_menu(tenant_id,role_id,menu_id) VALUES(7,1,?)",id++);
            }
            for(long project:List.of(20L,21L,30L)) {
                long tenant=project==30L?8L:7L;
                jdbc.update("INSERT INTO proj_project(id,tenant_id,project_name,root_id,creator) VALUES(?,?,?,?,?)",project,tenant,"Project "+project,project,"999");
                jdbc.update("INSERT INTO proj_project_tree_version(root_project_id,tenant_id,tree_version,status,version) VALUES(?,?,1,'ACTIVE',0)",project,tenant);
                jdbc.update("INSERT INTO proj_project_tree_path(root_project_id,ancestor_project_id,descendant_project_id,tree_version,distance,tenant_id,version) VALUES(?,?,?,1,0,?,0)",project,project,project,tenant);
            }
            jdbc.update("INSERT INTO proj_project_member_assignment(tenant_id,project_id,user_id,member_role,effective_from,status,version) VALUES(7,20,9,'PROJECT_MANAGER',CURRENT_TIMESTAMP,'ACTIVE',0)");
            return jdbc;
        }
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean SqlSessionFactory sessions(DataSource source,JdbcTemplate jdbc) throws Exception {
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);
            var global=new com.baomidou.mybatisplus.core.config.GlobalConfig();
            global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());factory.setGlobalConfig(global);
            var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
            var optimistic=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
            var tenantProperties=new cn.iocoder.yudao.framework.tenant.config.TenantProperties();
            tenantProperties.setIgnoreTables(Set.of("system_user_role"));
            optimistic.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(tenantProperties)));
            optimistic.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor());
            optimistic.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.H2));config.addInterceptor(optimistic);
            factory.setConfiguration(config);
            var resources=new ArrayList<org.springframework.core.io.Resource>();
            for(String file:List.of("mapper/command/PlatformIdempotencyRecordMapper.xml","mapper/authorization/AuthorizationGrantMapper.xml",
                    "mapper/projectmanual/ProjectMasterMapper.xml","mapper/projectmanual/ProjectMemberAssignmentMapper.xml","mapper/projecttree/ProjectTreeVersionMapper.xml")) {
                var resource=new ClassPathResource(file);
                String xml=new String(resource.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("b'0'","0").replace("b'1'","1");
                resources.add(new org.springframework.core.io.ByteArrayResource(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8),file));
            }
            factory.setMapperLocations(resources.toArray(org.springframework.core.io.Resource[]::new));
            var result=factory.getObject();
            for(Class<?> mapper:List.of(FieldNoteMapper.class,PlatformOperationAuditMapper.class,PlatformOutboxEventMapper.class,RoleMapper.class,MenuMapper.class,RoleMenuMapper.class,UserRoleMapper.class,ProjectTreePathMapper.class)) result.getConfiguration().addMapper(mapper);
            return result;
        }
        @Bean FieldNoteMapper notes(SqlSessionFactory factory){return new SqlSessionTemplate(factory).getMapper(FieldNoteMapper.class);}
        @Bean PlatformIdempotencyRecordMapper ledger(SqlSessionFactory factory){return new SqlSessionTemplate(factory).getMapper(PlatformIdempotencyRecordMapper.class);}
        @Bean PlatformOperationAuditMapper audit(SqlSessionFactory factory){return new SqlSessionTemplate(factory).getMapper(PlatformOperationAuditMapper.class);}
        @Bean PlatformOutboxEventMapper outbox(SqlSessionFactory factory){return new SqlSessionTemplate(factory).getMapper(PlatformOutboxEventMapper.class);}
        @Bean static cn.hutool.extra.spring.SpringUtil springUtil(){return new cn.hutool.extra.spring.SpringUtil();}
        @Bean RoleMapper roles(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(RoleMapper.class);}
        @Bean MenuMapper menus(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(MenuMapper.class);}
        @Bean RoleMenuMapper roleMenus(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(RoleMenuMapper.class);}
        @Bean UserRoleMapper userRoles(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(UserRoleMapper.class);}
        @Bean ProjectMasterMapper projects(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(ProjectMasterMapper.class);}
        @Bean ProjectMemberAssignmentMapper members(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(ProjectMemberAssignmentMapper.class);}
        @Bean ProjectTreePathMapper paths(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(ProjectTreePathMapper.class);}
        @Bean ProjectTreeVersionMapper trees(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(ProjectTreeVersionMapper.class);}
        @Bean AuthorizationGrantMapper grants(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(AuthorizationGrantMapper.class);}
        // These unrelated ports throw on use; permission checks must execute the actual permission/role/menu services and SQL.
        static <T> T unused(Class<T> type){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{if(method.getDeclaringClass()==Object.class)return method.getName().equals("toString")?"unused "+type.getSimpleName():method.getName().equals("hashCode")?System.identityHashCode(proxy):proxy==args[0];throw new UnsupportedOperationException("Unexpected external port "+type.getName()+"."+method.getName());}));}
        @Bean cn.iocoder.yudao.module.system.service.dept.DeptService departments(){return unused(cn.iocoder.yudao.module.system.service.dept.DeptService.class);}
        @Bean cn.iocoder.yudao.module.system.service.user.AdminUserService users(){return unused(cn.iocoder.yudao.module.system.service.user.AdminUserService.class);}
        @Bean cn.iocoder.yudao.module.system.service.tenant.TenantService tenants(){return unused(cn.iocoder.yudao.module.system.service.tenant.TenantService.class);}
        @Bean DeclaredBusinessScopeSupport scopes(ObjectProvider<BusinessScopeAccess> ports){return new DeclaredBusinessScopeSupport(ports.orderedStream().toList());}
        @Bean BusinessModelContributor declaration(FieldNoteMapper mapper) {
            var descriptor=new BusinessModelDescriptor("TEST","fieldNote","TEST_FIELD_NOTE",1,BusinessModelKind.AGGREGATE_ROOT,"Field note","test:field-note:query",
                List.of(new BusinessFieldDescriptor("title","Title",EntityField.Type.TEXT,true,true,true,null),
                    new BusinessFieldDescriptor("projectRef","Project",EntityField.Type.NUMBER,true,true,true,null),
                    new BusinessFieldDescriptor("privateMemo","Private",EntityField.Type.TEXT,false,false,false,null)),List.of(),
                List.of(new BusinessOperationDescriptor("create",1,"Create",BusinessOperationDescriptor.StandardOperationKind.CREATE,"test:field-note:write"),
                    new BusinessOperationDescriptor("save",1,"Save",BusinessOperationDescriptor.StandardOperationKind.UPDATE,"test:field-note:write")),List.of(),null,new BusinessScopeBinding("project","projectRef"));
            return ()->List.of(new BusinessModelDeclaration(descriptor,FieldNote.class,mapper,null));
        }
        @Bean BusinessModelCatalog catalog(ObjectProvider<BusinessModelContributor> declarations){return new BusinessModelRegistry(declarations);}
        @Bean BusinessEntityPersistenceRegistry persistence(ObjectProvider<BusinessModelContributor> declarations){return new BusinessEntityPersistenceRegistry(declarations);}
        @Bean DefaultBusinessApplicationService service(BusinessModelCatalog catalog,BusinessEntityPersistenceRegistry persistence,BusinessAccessGuard guard,OperationExecutionStore store,BusinessEventPort events,OperationAuditApi audit,PlatformTransactionManager manager,DeclaredBusinessScopeSupport scopes){
            return new DefaultBusinessApplicationService(()->new AbstractBusinessApplicationService.ResolvedCaller(TenantContextHolder.getRequiredTenantId(),9L,"framework-test"),catalog,persistence,guard,store,events,audit,new TransactionTemplate(manager),null,scopes);
        }
        @Bean BusinessOperationDispatcher dispatcher(BusinessEntityPersistenceRegistry persistence,DefaultBusinessApplicationService service){return new BusinessOperationDispatcher(persistence,service);}
        @Bean DefaultBusinessEntityAccess access(BusinessModelCatalog catalog,BusinessEntityPersistenceRegistry persistence,BusinessAccessGuard guard,DeclaredBusinessScopeSupport scopes){return new DefaultBusinessEntityAccess(catalog,persistence,guard,null,List.of(),List.of(),scopes);}
        static void schema(JdbcTemplate jdbc,Class<?> type) {
            var columns=new LinkedHashMap<String,String>();
            for(Class<?> current=type;current!=Object.class;current=current.getSuperclass()) for(Field field:current.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers())||field.isSynthetic())continue;
                var annotation=field.getAnnotation(TableField.class); if(annotation!=null&&!annotation.exist())continue;
                String name=annotation!=null&&!annotation.value().isBlank()?annotation.value():field.getName().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
                Class<?> kind=field.getType(); String sql=kind==Long.class||kind==Integer.class?"BIGINT":kind==Boolean.class?"BOOLEAN":kind==java.time.LocalDateTime.class?"TIMESTAMP":"VARCHAR(100000)";
                if(name.equals("id"))sql="BIGINT AUTO_INCREMENT PRIMARY KEY";else if(name.equals("deleted"))sql="BOOLEAN DEFAULT FALSE";else if(name.equals("create_time")||name.equals("update_time"))sql="TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
                columns.putIfAbsent(name,name+" "+sql);
            }
            jdbc.execute("CREATE TABLE "+type.getAnnotation(TableName.class).value()+" ("+String.join(",",columns.values())+")");
        }
    }
}
