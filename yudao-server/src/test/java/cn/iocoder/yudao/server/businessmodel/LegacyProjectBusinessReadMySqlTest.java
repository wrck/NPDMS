package cn.iocoder.yudao.server.businessmodel;

import cn.iocoder.yudao.module.pms.engineering.model.EngineeringBusinessModelContributor;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.model.CommerceBusinessModelContributor;
import cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeQueryService;
import cn.iocoder.yudao.module.pms.commerce.service.scope.DeliveryScopeProjectVersionService;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.iocoder.yudao.module.pms.engineering.service.attachment.*;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApiImpl;
import cn.iocoder.yudao.module.system.dal.mysql.permission.*;
import cn.iocoder.yudao.module.system.service.permission.*;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real production declarations, Spring bindings, system permission services and MySQL mappers.
 * ProjectScope API responses are deterministic; role caches and full login are not exercised. */
@EnabledIfSystemProperty(named = "npdms.imp-read.mysql", matches = "true")
class LegacyProjectBusinessReadMySqlTest {
    static final String URL = "jdbc:mysql://127.0.0.1:27601/imp_project_reads_verify?useSSL=false&allowPublicKeyRetrieval=true";
    enum Model {
        CONFIGURATION("IMP", "configuration", "pms:imp-configuration:query", "code", ConfigurationDO.class, ConfigurationMapper.class),
        JOINT_TEST("IMP", "jointTest", "pms:imp-joint-test:query", "code", JointTestDO.class, JointTestMapper.class),
        INSTALLATION("IMP", "installation", "pms:imp-installation:query", "code", InstallationDO.class, InstallationMapper.class),
        DELIVERY_SCOPE("COM", "deliveryScope", "pms:commerce:scope:query", "orderNo", DeliveryScopeDO.class, DeliveryScopeMapper.class);
        final String owner, type, permission, detailField;
        final Class<? extends BaseBusinessEntity> entityClass;
        final Class<?> mapperClass;
        Model(String owner, String type, String permission, String detailField,
                Class<? extends BaseBusinessEntity> entityClass, Class<?> mapperClass) {
            this.owner=owner; this.type=type; this.permission=permission; this.detailField=detailField;
            this.entityClass=entityClass; this.mapperClass=mapperClass;
        }
    }
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    ConfigurationMapper configurations;
    JointTestMapper jointTests;
    final Map<Class<?>, Object> realMappers=new HashMap<>();
    BusinessEntityAccessPort access;
    BusinessModelCatalog catalog;
    PermissionApi permissions;
    ProjectScopeApi projectApi;
    NativeAttachmentAccess nativeFiles;
    OperationExecutionStore ledger;
    BusinessEventPort events;
    OperationAuditApi audits;
    Set<Long> visibleProjects;

    @BeforeEach void start() throws Exception {
        assertEquals(URL, System.getProperty("npdms.imp-read.jdbcUrl"), "Exclusive tmpfs Compose database required");
        var source=new DriverManagerDataSource(URL, "root", ""); jdbc=new JdbcTemplate(source);
        var configuration=new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        for(var model:Model.values()) configuration.addMapper(model.mapperClass);
        configuration.addMapper(DeliveryScopeDetailMapper.class);
        for(var type:List.of(RoleMapper.class,MenuMapper.class,RoleMenuMapper.class,UserRoleMapper.class)) configuration.addMapper(type);
        var plugins=new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration); factory.setPlugins(plugins);
        var resolver=new PathMatchingResourcePatternResolver();
        var xml=new ArrayList<org.springframework.core.io.Resource>();
        xml.addAll(List.of(resolver.getResources("classpath*:mapper/configuration/ConfigurationMapper.xml")));
        xml.addAll(List.of(resolver.getResources("classpath*:mapper/jointtest/JointTestMapper.xml")));
        xml.addAll(List.of(resolver.getResources("classpath*:mapper/installation/InstallationMapper.xml")));
        xml.addAll(List.of(resolver.getResources("classpath*:mapper/scope/DeliveryScopeMapper.xml")));
        xml.addAll(List.of(resolver.getResources("classpath*:mapper/scope/DeliveryScopeDetailMapper.xml")));
        factory.setMapperLocations(xml.toArray(org.springframework.core.io.Resource[]::new));
        var sessions=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        configurations=sessions.getMapper(ConfigurationMapper.class); jointTests=sessions.getMapper(JointTestMapper.class);
        for(var model:Model.values()) realMappers.put(model.mapperClass,sessions.getMapper(model.mapperClass));
        realMappers.put(DeliveryScopeDetailMapper.class,sessions.getMapper(DeliveryScopeDetailMapper.class));
        for(var model:Model.values()) schema(model.entityClass);
        schema(DeliveryScopeDetailDO.class);
        initializePermissionSchema();
        var constructor=EngineeringBusinessModelContributor.class.getConstructors()[0];
        Object[] dependencies=Arrays.stream(constructor.getParameterTypes()).map(type ->
                realMappers.containsKey(type) ? realMappers.get(type) : mock(type)).toArray();
        var contributor=(EngineeringBusinessModelContributor)constructor.newInstance(dependencies);
        var commerceConstructor=CommerceBusinessModelContributor.class.getConstructors()[0];
        var commerce=(CommerceBusinessModelContributor)commerceConstructor.newInstance(Arrays.stream(commerceConstructor.getParameterTypes())
                .map(type->realMappers.containsKey(type)?realMappers.get(type):mock(type)).toArray());
        var roleService=new RoleServiceImpl(); ReflectionTestUtils.setField(roleService,"roleMapper",sessions.getMapper(RoleMapper.class));
        var menuService=new MenuServiceImpl(); ReflectionTestUtils.setField(menuService,"menuMapper",sessions.getMapper(MenuMapper.class));
        var permissionService=new PermissionServiceImpl(); ReflectionTestUtils.setField(permissionService,"roleService",roleService);
        ReflectionTestUtils.setField(permissionService,"menuService",menuService);
        ReflectionTestUtils.setField(permissionService,"userRoleMapper",sessions.getMapper(UserRoleMapper.class));
        ReflectionTestUtils.setField(permissionService,"roleMenuMapper",sessions.getMapper(RoleMenuMapper.class));
        var permissionApi=new PermissionApiImpl(); ReflectionTestUtils.setField(permissionApi,"permissionService",permissionService);
        permissions=permissionApi; projectApi=mock(ProjectScopeApi.class); visibleProjects=Set.of(100L);
        when(projectApi.resolveAllCurrent(any())).thenAnswer(call -> {
            var query=call.getArgument(0, ProjectAllScopeQuery.class);
            assertEquals(ProjectScopeApi.ACTION_VIEW, query.actionCode());
            return visibleProjects;
        });
        when(projectApi.resolveCurrent(any())).thenAnswer(call -> {
            var query=call.getArgument(0, ProjectCurrentScopeQuery.class);
            assertEquals(ProjectScopeApi.ACTION_VIEW, query.actionCode());
            return new ProjectScopeResult(query.anchorProjectId(), 1L,
                    visibleProjects!=null && visibleProjects.contains(query.anchorProjectId()) ? Set.of(query.anchorProjectId()) : Set.of(), Set.of());
        });
        context=new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("roles",roleService);
        context.getBeanFactory().registerSingleton("menus",menuService);
        context.getBeanFactory().registerSingleton("permissions",permissionService);
        context.registerBean("engineeringDeclarations",BusinessModelContributor.class, () -> contributor);
        context.registerBean("commerceDeclarations",BusinessModelContributor.class, () -> commerce);
        context.registerBean(BusinessModelCatalog.class, () -> new BusinessModelRegistry(context.getBeanProvider(BusinessModelContributor.class)));
        context.registerBean(BusinessEntityPersistenceRegistry.class, () -> new BusinessEntityPersistenceRegistry(context.getBeanProvider(BusinessModelContributor.class)));
        context.registerBean(BusinessAccessGuard.class, () -> new PermissionBusinessAccessGuard(permissions));
        context.registerBean(ProjectBusinessScopeAccess.class, () -> new ProjectBusinessScopeAccess(projectApi));
        context.registerBean(BusinessCallerContext.class, TenantCallerContext::new);
        ledger=mock(OperationExecutionStore.class); events=mock(BusinessEventPort.class); audits=mock(OperationAuditApi.class);
        context.registerBean(OperationExecutionStore.class, () -> ledger);
        context.registerBean(BusinessEventPort.class, () -> events);
        context.registerBean(OperationAuditApi.class, () -> audits);
        context.registerBean(org.springframework.transaction.PlatformTransactionManager.class, () -> new DataSourceTransactionManager(source));
        context.register(LegacyProjectReadBindings.class, BusinessModelAccessConfiguration.class); context.refresh();
        var spring=new SpringUtil(); spring.setApplicationContext(context); spring.postProcessBeanFactory(context.getBeanFactory());
        access=context.getBean(BusinessEntityAccessPort.class); catalog=context.getBean(BusinessModelCatalog.class);
        var ownersConstructor=NativeAttachmentOwners.class.getConstructors()[0];
        Object[] ownerDependencies=Arrays.stream(ownersConstructor.getParameterTypes()).map(type ->
                type==ConfigurationMapper.class ? configurations : type==JointTestMapper.class ? jointTests : mock(type)).toArray();
        nativeFiles=new NativeAttachmentAccess((NativeAttachmentOwners)ownersConstructor.newInstance(ownerDependencies),
                permissions, projectApi, mock(ProjectAcceptanceContextApi.class));
        login(7L,17L);
        for(var model:Model.values()) {
            insert(model,10L,7L,100L,false); insert(model,11L,7L,100L,false);
            insert(model,20L,7L,200L,false); insert(model,30L,8L,100L,false);
            insert(model,40L,7L,100L,true); insert(model,50L,7L,null,false);
        }
    }

    @AfterEach void close() {
        if(context!=null) context.close(); SecurityContextHolder.clearContext(); TenantContextHolder.clear();
    }
    void login(Long tenant, Long user) {
        TenantContextHolder.setTenantId(tenant);
        var principal=new LoginUser().setId(user).setTenantId(tenant).setUserType(2);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
    }
    EntityActor actor() { return new EntityActor(TenantContextHolder.getRequiredTenantId(),
            ((LoginUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId(),"imp-read-regression"); }
    EntityDataRef target(Model model,long id) { return EntityDataRef.current(new EntityRef(actor().tenantId(),model.owner,model.type,id)); }
    BusinessEntityPageQuery page(Model model,int size,String cursor) { return new BusinessEntityPageQuery("list",model.owner,model.type,List.of(),size,cursor); }
    List<Long> ids(BusinessEntitySlice slice) { return slice.members().stream().map(row->row.ref().entityId()).toList(); }
    void reject(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code,assertThrows(BusinessContractException.class,action).getErrorCode());
    }

    @ParameterizedTest @EnumSource(Model.class) void ownProjectReadsUseActualRowsAndBoundedCursor(Model model) {
        assertEquals(List.of(10L,11L),ids(access.query(page(model,10,null),actor())));
        var first=access.query(page(model,1,null),actor()); assertEquals(List.of(10L),ids(first)); assertEquals("10",first.nextCursor());
        assertEquals(List.of(11L),ids(access.query(page(model,1,first.nextCursor()),actor())));
        var row=access.read(target(model,10),actor(),"detail"); assertTrue(row.available()); assertEquals("CODE-10",row.fieldValues().get(model.detailField));
        reject("ENTITY_SCOPE_DENIED",()->access.read(target(model,20),actor(),"detail"));
        reject("ENTITY_NOT_FOUND",()->access.read(target(model,40),actor(),"detail"));
        reject("SCOPE_ID_INVALID",()->access.read(target(model,50),actor(),"detail"));
        var filter=new BusinessEntityPageQuery("list",model.owner,model.type,
                List.of(new BusinessFieldFilter("projectId",BusinessFieldFilter.Operator.EQ,List.of(200L))),10,null);
        if(model==Model.DELIVERY_SCOPE) assertTrue(access.query(filter,actor()).members().isEmpty());
        else {
            assertFalse(row.fieldValues().containsKey("projectId"),"Ownership stays server-side; contributor projection is unchanged");
            reject("FIELD_NOT_OPEN",()->access.query(filter,actor()));
        }
    }

    @ParameterizedTest @EnumSource(Model.class) void emptyOrMissingProjectScopeNeverExpandsReads(Model model) {
        for(var scope:Arrays.asList(Set.<Long>of(),null)) {
            visibleProjects=scope;
            assertTrue(access.query(page(model,10,null),actor()).members().isEmpty());
            reject("ENTITY_SCOPE_DENIED",()->access.read(target(model,10),actor(),"detail"));
        }
    }

    @ParameterizedTest @EnumSource(Model.class) void tenantAndAuthenticatedActorStayRequired(Model model) {
        reject("ENTITY_NOT_FOUND",()->access.read(target(model,30),actor(),"detail"));
        login(8L,17L); assertEquals(List.of(30L),ids(access.query(page(model,10,null),actor())));
        reject("ENTITY_NOT_FOUND",()->access.read(target(model,10),actor(),"detail"));
        assertThrows(IllegalArgumentException.class,()->access.read(EntityDataRef.current(new EntityRef(7L,model.owner,model.type,10L)),actor(),"detail"));
        login(7L,17L);
        reject("ACCESS_DENIED",()->access.query(page(model,10,null),new EntityActor(7L,23L,"spoofed-user")));
        TenantContextHolder.setTenantId(8L); reject("ACCESS_DENIED",()->access.query(page(model,10,null),new EntityActor(8L,17L,"spoofed-tenant")));
    }

    @ParameterizedTest @EnumSource(Model.class) void nativeQueryPermissionIsIndependentOfProjectScope(Model model) {
        login(7L,18L); reject("ACCESS_DENIED",()->access.query(page(model,10,null),actor()));
        reject("ACCESS_DENIED",()->access.read(target(model,10),actor(),"detail"));
        login(7L,17L);
        jdbc.update("DELETE FROM system_role_menu WHERE tenant_id=7 AND role_id=702 AND menu_id=(SELECT id FROM system_menu WHERE permission=?)",model.permission);
        reject("ACCESS_DENIED",()->access.query(page(model,10,null),actor()));
        var other=model==Model.CONFIGURATION?Model.JOINT_TEST:Model.CONFIGURATION;
        assertEquals(List.of(10L,11L),ids(access.query(page(other,10,null),actor())));
    }

    @ParameterizedTest @EnumSource(Model.class) void queryOnlyReadBindingNeverAdmitsBusinessWrites(Model model) {
        assertTrue(access.read(target(model,10),actor(),"detail").available());
        var descriptor=catalog.require(model.owner,model.type); assertTrue(descriptor.operations().isEmpty()); assertNull(descriptor.scopeBinding());
        var dispatcher=context.getBean(BusinessOperationDispatcher.class);
        for(long user:List.of(17L,23L)) {
            login(7L,user);
            for(String operation:List.of("CREATE","UPDATE","DELETE","COMPLETE")) {
                var request=new BusinessOperationRequest(operation,1,operation.equals("CREATE")?null:target(model,10),
                        model.owner,model.type,Map.of(model.detailField,"must-not-write"),"imp-read-"+operation,0L,OperationEntryKind.INDEPENDENT,null);
                reject("OPERATION_NOT_DECLARED",()->dispatcher.dispatch(request));
                reject("OPERATION_PERMISSION_NOT_DECLARED",()->context.getBean(BusinessAccessGuard.class).requireWritable(descriptor,actor(),"operation:"+operation));
            }
        }
        login(7L,17L);
        assertEquals("CODE-10",access.read(target(model,10),actor(),"detail").fieldValues().get(model.detailField));
        assertEquals(0L,jdbc.queryForObject("SELECT version FROM "+TableInfoHelper.getTableInfo(model.entityClass).getTableName()+" WHERE id=10",Long.class));
        verifyNoInteractions(ledger,events,audits); verify(projectApi,never()).lockAndRevalidate(any());
    }

    @ParameterizedTest @EnumSource(value=Model.class,names={"CONFIGURATION","JOINT_TEST"}) void queryOnlyNativeFileWritesRemainDenied(Model model) {
        var kind=NativeAttachmentKind.find(model.owner,model.type);
        assertTrue(nativeFiles.require(kind,7L,17L,"10",kind.getPurpose(),FileActionCodes.READ,false,null).allowed());
        for(String action:List.of(FileActionCodes.UPLOAD,FileActionCodes.REPLACE,FileActionCodes.DETACH))
            reject("NATIVE_ATTACHMENT_ACCESS_DENIED",()->nativeFiles.require(kind,7L,17L,"10",kind.getPurpose(),action,false,null));
        assertEquals("CODE-10",access.read(target(model,10),actor(),"detail").fieldValues().get(model.detailField));
        assertEquals(0L,jdbc.queryForObject("SELECT version FROM "+TableInfoHelper.getTableInfo(model.entityClass).getTableName()+" WHERE id=10",Long.class));
        verifyNoInteractions(ledger,events,audits); verify(projectApi,never()).lockAndRevalidate(any());
    }

    @ParameterizedTest @EnumSource(Model.class) void movingOrRevokingCurrentOwnerClosesPreviouslyReadableRows(Model model) {
        assertTrue(access.read(target(model,10),actor(),"detail").available());
        jdbc.update("UPDATE "+TableInfoHelper.getTableInfo(model.entityClass).getTableName()+" SET project_id=200 WHERE id=10");
        assertEquals(List.of(11L),ids(access.query(page(model,10,null),actor())));
        reject("ENTITY_SCOPE_DENIED",()->access.read(target(model,10),actor(),"detail"));
        visibleProjects=Set.of();
        reject("ENTITY_SCOPE_DENIED",()->access.read(target(model,11),actor(),"detail"));
        assertTrue(access.query(page(model,10,null),actor()).members().isEmpty());
    }

    @Test void projectBindingsDoNotAdmitUnresolvedArrivalAcceptance() {
        login(7L,23L);
        reject("SCOPE_POLICY_NOT_DECLARED",()->access.query(new BusinessEntityPageQuery("list","IMP","arrivalAcceptance",List.of(),10,null),actor()));
    }

    @Test void deliveryScopeNativeProjectQueryAndHistoryRemainConsistentWithCommonRead() {
        var service=new CommerceDeliveryScopeQueryService(projectApi,(DeliveryScopeMapper)realMappers.get(DeliveryScopeMapper.class),
                (DeliveryScopeDetailMapper)realMappers.get(DeliveryScopeDetailMapper.class),mock(DeliveryScopeProjectVersionService.class));
        assertTrue(permissions.hasAnyPermissions(17L,Model.DELIVERY_SCOPE.permission));
        assertEquals(List.of(10L),service.page(7L,17L,null,null,false,0,10).getList().stream().map(view->view.scope().getId()).sorted().toList());
        assertEquals(List.of(10L,11L),service.page(7L,17L,null,null,true,0,10).getList().stream().map(view->view.scope().getId()).sorted().toList());
        assertEquals(List.of(10L,11L),ids(access.query(page(Model.DELIVERY_SCOPE,10,null),actor())));
        assertTrue(service.page(7L,17L,200L,null,true,0,10).getList().isEmpty());
    }

    void insert(Model model,Long id,Long tenant,Long project,boolean deleted) {
        BaseBusinessEntity row;
        if(model==Model.CONFIGURATION) { var value=new ConfigurationDO(); value.setProjectId(project); value.setCode("CODE-"+id); value.setStatus(0); row=value; }
        else if(model==Model.JOINT_TEST) { var value=new JointTestDO(); value.setProjectId(project); value.setCode("CODE-"+id); value.setStatus(0); row=value; }
        else if(model==Model.INSTALLATION) { var value=new InstallationDO(); value.setProjectId(project); value.setCode("CODE-"+id); value.setStatus(0); row=value; }
        else { var value=new DeliveryScopeDO(); value.setProjectId(project); value.setOrderNo("CODE-"+id); value.setOrderLineId(id); value.setAllocatedQty(BigDecimal.ONE); value.setScopeStatus("ACTIVE");
            value.setAllocationVersion(1L); value.setEffectiveFrom(LocalDateTime.of(2026,1,1,0,0));
            if(id==11L) { value.setEffectiveTo(LocalDateTime.of(2026,2,1,0,0)); value.setScopeStatus("RELEASED"); } row=value; }
        row.setId(id); row.setTenantId(tenant); row.setVersion(0L); row.setDeleted(deleted);
        // Model tables only: no original migration or historical data is altered.
        TenantContextHolder.setTenantId(tenant);
        try { @SuppressWarnings("unchecked") var mapper=(BaseMapper<BaseBusinessEntity>)realMappers.get(model.mapperClass); assertEquals(1,mapper.insert(row)); }
        finally { TenantContextHolder.setTenantId(7L); }
    }
    void schema(Class<?> type) {
        var table=TableInfoHelper.getTableInfo(type); var columns=new ArrayList<String>(); columns.add("id BIGINT PRIMARY KEY");
        for(var field:table.getFieldList()) {
            Class<?> kind=field.getPropertyType(); String sql=kind==Long.class || kind==Integer.class?"BIGINT":kind==Boolean.class?"BOOLEAN":kind==LocalDateTime.class?"DATETIME(3)":kind==BigDecimal.class?"DECIMAL(20,6)":"LONGTEXT";
            columns.add("`"+field.getColumn()+"` "+sql+(field.getColumn().equals("deleted")?" DEFAULT FALSE":""));
        }
        jdbc.execute("DROP TABLE IF EXISTS "+table.getTableName());
        jdbc.execute("CREATE TABLE "+table.getTableName()+" ("+String.join(",",columns)+")");
    }
    void initializePermissionSchema() throws Exception {
        // Original V1 DDL read verbatim; no original migration file is edited or executed.
        String ddl=Files.readString(Path.of("../sql/migrations/V1__yudao_platform.sql"));
        for(String table:List.of("system_role","system_menu","system_role_menu","system_user_role")) {
            var match=Pattern.compile("CREATE TABLE(?: IF NOT EXISTS)? `"+table+"`.*?;",Pattern.DOTALL).matcher(ddl);
            assertTrue(match.find(),table); jdbc.execute("DROP TABLE IF EXISTS "+table); jdbc.execute(match.group());
        }
        for(long tenant:List.of(7L,8L)) {
            jdbc.update("INSERT INTO system_role(id,name,code,sort,status,type,tenant_id) VALUES(?,?,?,1,0,2,?),(?,?,?,2,0,2,?)",
                    tenant*100+1,"IMP writer","imp_fixture_writer",tenant,tenant*100+2,"IMP query only","imp_fixture_reader",tenant);
            jdbc.update("INSERT INTO system_user_role(user_id,role_id,tenant_id) VALUES(23,?,?),(17,?,?)",tenant*100+1,tenant,tenant*100+2,tenant);
        }
        var codes=List.of("pms:imp-configuration:query","pms:imp-joint-test:query","pms:business-model:operate",
                "pms:imp-configuration:create","pms:imp-configuration:update","pms:imp-configuration:delete",
                "pms:imp-joint-test:create","pms:imp-joint-test:update","pms:imp-joint-test:delete","pms:imp-installation:query",
                "pms:imp-installation:create","pms:imp-installation:update","pms:imp-installation:delete",
                "pms:commerce:scope:query","pms:commerce:scope:assign","pms:commerce:scope:adjust","pms:commerce:scope:release","pms:arrival-acceptance:query");
        for(int i=0;i<codes.size();i++) {
            long id=995106200001L+i;
            String permission=codes.get(i);
            jdbc.update("INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,status) VALUES(?,?,?,3,0,0,'',0)",id,"Project read test action",permission);
            for(long tenant:List.of(7L,8L)) {
                jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(?,?,?)",tenant*100+1,id,tenant);
                if(Arrays.stream(Model.values()).anyMatch(model->model.permission.equals(permission)))
                    jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(?,?,?)",tenant*100+2,id,tenant);
            }
        }
    }
}
