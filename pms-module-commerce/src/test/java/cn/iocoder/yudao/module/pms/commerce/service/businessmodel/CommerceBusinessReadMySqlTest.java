package cn.iocoder.yudao.module.pms.commerce.service.businessmodel;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.ContractController;
import cn.iocoder.yudao.module.pms.commerce.controller.admin.order.OrderController;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.OrderContractRelationAuthorityMapper;
import cn.iocoder.yudao.module.pms.commerce.model.CommerceBusinessModelContributor;
import cn.iocoder.yudao.module.pms.commerce.service.contract.*;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.*;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.*;
import cn.iocoder.yudao.module.system.service.permission.*;
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
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual MySQL/XML, native commerce services/controllers and system permission services.
 * OrganizationScope/ProjectScope answers are controlled ports; full login and role caches are not exercised. */
@EnabledIfSystemProperty(named="npdms.com-read.mysql", matches="true")
class CommerceBusinessReadMySqlTest {
    static final String URL="jdbc:mysql://127.0.0.1:27611/commerce_domain_reads_verify?useSSL=false&allowPublicKeyRetrieval=true";
    enum Model { CONTRACT("contract","contractNo","com_contract"), ORDER("salesOrder","orderNo","com_sales_order");
        final String type,code,table; Model(String type,String code,String table){this.type=type;this.code=code;this.table=table;} }
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    ContractMapper contracts;
    SalesOrderMapper orders;
    BusinessEntityAccessPort access;
    ContractAccessService nativeAccess;
    ContractController contractController;
    OrderController orderController;
    PermissionApi permissions;
    OrganizationScopeApi organizationApi;
    ProjectScopeApi projectApi;
    List<UserCompanyDepartmentScopeRespDTO> grants;
    Set<Long> projects;
    OperationExecutionStore ledger;
    BusinessEventPort events;
    OperationAuditApi audit;
    final List<String> dataQueries=new ArrayList<>();

    @BeforeEach void start() throws Exception {
        assertEquals(URL,System.getProperty("npdms.com-read.jdbcUrl"));
        var source=new DriverManagerDataSource(URL,"root",""); jdbc=new JdbcTemplate(source);
        var configuration=new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        for(var type:List.of(ContractMapper.class,SalesOrderMapper.class,ProjectContractRelationMapper.class,
                OrderContractRelationAuthorityMapper.class,RoleMapper.class,MenuMapper.class,RoleMenuMapper.class,UserRoleMapper.class)) configuration.addMapper(type);
        var plugins=new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(configuration);factory.setPlugins(plugins,new QueryCounter(dataQueries));
        var resolver=new PathMatchingResourcePatternResolver();var xml=new ArrayList<org.springframework.core.io.Resource>();
        for(String name:List.of("contract/ContractMapper","contract/ProjectContractRelationMapper","order/SalesOrderMapper"))
            xml.addAll(List.of(resolver.getResources("classpath*:mapper/"+name+".xml")));
        factory.setMapperLocations(xml.toArray(org.springframework.core.io.Resource[]::new));
        var sessions=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        contracts=sessions.getMapper(ContractMapper.class);orders=sessions.getMapper(SalesOrderMapper.class);
        for(var type:List.of(ContractDO.class,SalesOrderDO.class,ProjectContractRelationDO.class,SalesOrderContractRelationDO.class)) schema(type);
        permissionSchema();
        var roles=new RoleServiceImpl();ReflectionTestUtils.setField(roles,"roleMapper",sessions.getMapper(RoleMapper.class));
        var menus=new MenuServiceImpl();ReflectionTestUtils.setField(menus,"menuMapper",sessions.getMapper(MenuMapper.class));
        var permissionService=new PermissionServiceImpl();ReflectionTestUtils.setField(permissionService,"roleService",roles);
        ReflectionTestUtils.setField(permissionService,"menuService",menus);
        ReflectionTestUtils.setField(permissionService,"userRoleMapper",sessions.getMapper(UserRoleMapper.class));
        ReflectionTestUtils.setField(permissionService,"roleMenuMapper",sessions.getMapper(RoleMenuMapper.class));
        var permissionApi=new PermissionApiImpl();ReflectionTestUtils.setField(permissionApi,"permissionService",permissionService);permissions=permissionApi;
        grants=List.of(grant("A","D1"));projects=Set.of();
        organizationApi=mock(OrganizationScopeApi.class);when(organizationApi.getActiveScopes(anyLong())).thenAnswer(call->grants);
        projectApi=mock(ProjectScopeApi.class);when(projectApi.resolveAllCurrent(any())).thenAnswer(call->{
            var query=call.getArgument(0,ProjectAllScopeQuery.class);assertEquals(ProjectScopeApi.ACTION_VIEW,query.actionCode());return projects;});
        audit=mock(OperationAuditApi.class);events=mock(BusinessEventPort.class);ledger=mock(OperationExecutionStore.class);
        nativeAccess=new ContractAccessService(organizationApi,projectApi,contracts,orders,mock(SalesOrderLineMapper.class),
                sessions.getMapper(ProjectContractRelationMapper.class),mock(cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper.class),audit);
        var contributor=new CommerceBusinessModelContributor(contracts,orders,mock(cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper.class),
                mock(cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.AuthorityCandidateMapper.class),mock(cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper.class));
        context=new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("roles",roles);context.getBeanFactory().registerSingleton("menus",menus);
        context.getBeanFactory().registerSingleton("permissions",permissionService);
        context.getBeanFactory().registerSingleton("permissionApi",permissions);
        context.getBeanFactory().registerSingleton("nativeAccess",nativeAccess);
        context.registerBean(ContractMapper.class,()->contracts);
        context.registerBean(SalesOrderMapper.class,()->orders);
        context.registerBean(BusinessModelContributor.class,()->contributor);
        context.registerBean(BusinessModelCatalog.class,()->new BusinessModelRegistry(context.getBeanProvider(BusinessModelContributor.class)));
        context.registerBean(BusinessEntityPersistenceRegistry.class,()->new BusinessEntityPersistenceRegistry(context.getBeanProvider(BusinessModelContributor.class)));
        context.registerBean(BusinessAccessGuard.class,()->new PermissionBusinessAccessGuard(permissions));
        context.registerBean(BusinessCallerContext.class,TenantCallerContext::new);
        context.registerBean(OperationExecutionStore.class,()->ledger);context.registerBean(BusinessEventPort.class,()->events);
        context.registerBean(OperationAuditApi.class,()->audit);
        context.registerBean(org.springframework.transaction.PlatformTransactionManager.class,()->new DataSourceTransactionManager(source));
        // This scan is empty on the pre-change snapshot; it discovers the production bridge after implementation.
        context.scan("cn.iocoder.yudao.module.pms.commerce.service.businessmodel");
        context.register(BusinessModelAccessConfiguration.class);context.refresh();
        var spring=new SpringUtil();spring.setApplicationContext(context);spring.postProcessBeanFactory(context.getBeanFactory());
        access=context.getBean(BusinessEntityAccessPort.class);
        contractController=new ContractController(nativeAccess,mock(ContractRelationCommandService.class),permissions,new StandardEnvironment());
        orderController=new OrderController(nativeAccess,new StandardEnvironment());
        login(7L,17L);
        for(long id:List.of(10L,11L,20L,21L,30L,40L,50L,60L)) insert(id,id==30L?8L:7L,id==21L?"a":Set.of(20L,40L,50L).contains(id)?"B":"A",id==60L);
        jdbc.update("UPDATE com_contract SET department_code='D2' WHERE id=11");
        jdbc.update("INSERT INTO com_project_contract_relation(id,tenant_id,project_id,contract_id,status,deleted,version) VALUES(400,7,100,40,'ACTIVE',0,0),(500,7,100,50,'INACTIVE',0,0)");
        jdbc.update("INSERT INTO com_order_contract_relation(id,tenant_id,order_id,contract_id,deleted) VALUES(400,7,40,40,0),(500,7,50,50,0)");
    }
    @AfterEach void close(){if(context!=null)context.close();SecurityContextHolder.clearContext();TenantContextHolder.clear();}
    void login(Long tenant,Long user){TenantContextHolder.setTenantId(tenant);var principal=new LoginUser().setId(user).setTenantId(tenant).setUserType(2);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));}
    EntityActor actor(){return new EntityActor(TenantContextHolder.getRequiredTenantId(),((LoginUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId(),"commerce-read-regression");}
    EntityDataRef target(Model m,long id){return EntityDataRef.current(new EntityRef(actor().tenantId(),"COM",m.type,id));}
    BusinessEntityPageQuery page(Model m,int size,String cursor){return new BusinessEntityPageQuery("list","COM",m.type,List.of(),size,cursor);}
    List<Long> ids(BusinessEntitySlice slice){return slice.members().stream().map(row->row.ref().entityId()).toList();}
    List<Long> nativeIds(Model m){return nativePage(m).getList().stream().map(BaseBusinessEntity::getId).toList();}
    PageResult<? extends BaseBusinessEntity> nativePage(Model m){return m==Model.CONTRACT?
        nativeAccess.pageContracts(actor().tenantId(),actor().userId(),"native",new ContractAccessService.ContractSearch(null,null,null,null,null,null,0,200)):
        nativeAccess.pageSalesOrders(actor().tenantId(),actor().userId(),"native",new ContractAccessService.SalesOrderSearch(null,null,null,null,null,0,200));}
    void reject(String code,org.junit.jupiter.api.function.Executable action){assertEquals(code,assertThrows(BusinessContractException.class,action).getErrorCode());}

    @Test void nativeFixtureProvesCompanyCaseDepartmentUnionAndSensitiveContractProjection(){
        assertEquals(List.of(10L,11L),nativeIds(Model.CONTRACT));assertEquals(List.of(10L,11L),nativeIds(Model.ORDER));
        assertNull(contractController.get(10L).getData().contract().contractAmount());
        assertNull(contractController.get(10L).getData().contract().customerName());
        assertEquals("CUSTOMER-10",orderController.pageOrders(null,null,null,null,null,1,20).getData().getList().getFirst().customerName());
        projects=Set.of(100L);assertEquals(List.of(10L,11L,40L),nativeIds(Model.CONTRACT));assertEquals(List.of(10L,11L,40L),nativeIds(Model.ORDER));
    }
    @ParameterizedTest @EnumSource(Model.class) void companyListDetailCursorAndNativeProjectionStayConsistent(Model m){
        var slice=access.query(page(m,10,null),actor());assertEquals(nativeIds(m),ids(slice));
        for(var row:slice.members())assertEquals(row,access.read(target(m,row.ref().entityId()),actor(),"detail"));
        var first=access.query(page(m,1,null),actor());assertEquals(List.of(10L),ids(first));assertNotNull(first.nextCursor());
        assertEquals(List.of(11L),ids(access.query(page(m,1,first.nextCursor()),actor())));
        reject("ENTITY_SCOPE_DENIED",()->access.read(target(m,20),actor(),"detail"));
        reject("ENTITY_SCOPE_DENIED",()->access.read(target(m,21),actor(),"detail"));
        assertFalse(slice.members().getFirst().fieldValues().containsKey("authorityStatus"));
        assertFalse(slice.members().getFirst().fieldValues().containsKey("sourceLifecycleStatus"));
    }
    @ParameterizedTest @EnumSource(Model.class) void projectRelationshipUsesNativeUnionAndCurrentValidity(Model m){
        grants=List.of();projects=Set.of(100L);assertEquals(nativeIds(m),ids(access.query(page(m,20,null),actor())));
        assertEquals(List.of(40L),ids(access.query(page(m,20,null),actor())));assertTrue(access.read(target(m,40),actor(),"detail").available());
        reject("ENTITY_SCOPE_DENIED",()->access.read(target(m,50),actor(),"detail"));
        jdbc.update("UPDATE com_project_contract_relation SET effective_to=CURRENT_TIMESTAMP WHERE id=400");
        assertTrue(access.query(page(m,20,null),actor()).members().isEmpty());reject("ENTITY_SCOPE_DENIED",()->access.read(target(m,40),actor(),"detail"));
    }
    @ParameterizedTest @EnumSource(Model.class) void revokedMalformedAndUnavailableOrganizationScopeFailClosed(Model m){
        assertTrue(access.read(target(m,10),actor(),"detail").available());
        for(var value:Arrays.asList(List.<UserCompanyDepartmentScopeRespDTO>of(),List.of(new UserCompanyDepartmentScopeRespDTO()),null)){
            grants=value;assertTrue(access.query(page(m,10,null),actor()).members().isEmpty());reject("ENTITY_SCOPE_DENIED",()->access.read(target(m,10),actor(),"detail"));}
        when(organizationApi.getActiveScopes(anyLong())).thenThrow(new IllegalStateException("scope owner unavailable"));
        assertTrue(access.query(page(m,10,null),actor()).members().isEmpty());
        verify(audit,atLeastOnce()).record(eq(7L),eq(17L),anyString(),eq("COM_CONTRACT_AUTHORIZATION"),anyString(),anyString(),eq("OWNER_UNAVAILABLE"),anyMap());
    }
    @ParameterizedTest @EnumSource(Model.class) void tenantAndAuthenticatedActorCannotBeSubstituted(Model m){
        reject("ENTITY_SCOPE_DENIED",()->access.read(target(m,30),actor(),"detail"));
        login(8L,17L);assertEquals(List.of(30L),ids(access.query(page(m,20,null),actor())));reject("ENTITY_SCOPE_DENIED",()->access.read(target(m,10),actor(),"detail"));
        assertThrows(IllegalArgumentException.class,()->access.read(EntityDataRef.current(new EntityRef(7L,"COM",m.type,10L)),actor(),"detail"));
        login(7L,17L);reject("ACCESS_DENIED",()->access.query(page(m,10,null),new EntityActor(7L,23L,"spoof")));
        TenantContextHolder.setTenantId(8L);reject("ACCESS_DENIED",()->access.query(page(m,10,null),new EntityActor(8L,17L,"spoof")));
    }
    @ParameterizedTest @EnumSource(Model.class) void nativePermissionRevocationClosesBothListAndDetail(Model m){
        login(7L,18L);reject("ACCESS_DENIED",()->access.query(page(m,10,null),actor()));reject("ACCESS_DENIED",()->access.read(target(m,10),actor(),"detail"));
        login(7L,17L);assertTrue(access.read(target(m,10),actor(),"detail").available());
        jdbc.update("DELETE FROM system_role_menu WHERE tenant_id=7 AND role_id=702");
        reject("ACCESS_DENIED",()->access.query(page(m,10,null),actor()));reject("ACCESS_DENIED",()->access.read(target(m,10),actor(),"detail"));
    }
    @ParameterizedTest @EnumSource(Model.class) void movingCurrentCompanyClosesOldOwnerWithoutCachedPositiveAccess(Model m){
        assertTrue(access.read(target(m,10),actor(),"detail").available());jdbc.update("UPDATE "+m.table+" SET company_code='B' WHERE id=10");
        assertEquals(List.of(11L),ids(access.query(page(m,20,null),actor())));reject("ENTITY_SCOPE_DENIED",()->access.read(target(m,10),actor(),"detail"));
        grants=List.of(grant("B","D9"));assertEquals(nativeIds(m),ids(access.query(page(m,20,null),actor())));
    }
    @ParameterizedTest @EnumSource(Model.class) void filtersOnlyObserveNativePublicProjectionAndCannotWidenOwnerScope(Model m){
        var filter=new BusinessFieldFilter("companyCode",BusinessFieldFilter.Operator.EQ,List.of("B"));
        assertTrue(access.query(new BusinessEntityPageQuery("list","COM",m.type,List.of(filter),10,null),actor()).members().isEmpty());
        filter=new BusinessFieldFilter(m.code,BusinessFieldFilter.Operator.IN,List.of());
        assertTrue(access.query(new BusinessEntityPageQuery("list","COM",m.type,List.of(filter),10,null),actor()).members().isEmpty());
        var hidden=new BusinessFieldFilter(m==Model.CONTRACT?"contractAmount":"orderAmount",BusinessFieldFilter.Operator.EQ,List.of(BigDecimal.TEN));
        reject("FIELD_NOT_OPEN",()->access.query(new BusinessEntityPageQuery("list","COM",m.type,List.of(hidden),10,null),actor()));
    }
    @ParameterizedTest @EnumSource(Model.class) void readBridgeDoesNotAdmitAnyBusinessWriteEvenForNativeWriters(Model m){
        assertTrue(access.read(target(m,10),actor(),"detail").available());var descriptor=context.getBean(BusinessModelCatalog.class).require("COM",m.type);
        assertTrue(descriptor.operations().isEmpty());assertNull(descriptor.scopeBinding());
        for(long user:List.of(17L,23L)){login(7L,user);for(String operation:List.of("CREATE","UPDATE","DELETE","COMPLETE")){
            reject("OPERATION_NOT_DECLARED",()->context.getBean(BusinessOperationDispatcher.class).dispatch(new BusinessOperationRequest(operation,1,
                operation.equals("CREATE")?null:target(m,10),"COM",m.type,Map.of(m.code,"must-not-write"),"com-read-"+operation,0L,OperationEntryKind.INDEPENDENT,null)));
            reject("OPERATION_PERMISSION_NOT_DECLARED",()->context.getBean(BusinessAccessGuard.class).requireWritable(descriptor,actor(),"operation:"+operation));}}
        assertEquals(0L,jdbc.queryForObject("SELECT version FROM "+m.table+" WHERE id=10",Long.class));
        assertEquals("CODE-10",jdbc.queryForObject("SELECT "+(m==Model.CONTRACT?"contract_no":"order_no")+" FROM "+m.table+" WHERE id=10",String.class));
        verifyNoInteractions(ledger,events,audit);verify(projectApi,never()).lockAndRevalidate(any());
    }
    @Test void contractSensitiveFieldsMatchNativeDetailAndRevokeImmediately(){
        login(7L,23L);var data=access.read(target(Model.CONTRACT,10),actor(),"detail");var nativeData=contractController.get(10L).getData().contract();
        assertEquals(nativeData.contractAmount(),data.fieldValues().get("contractAmount"));assertEquals(new BigDecimal("10.000000"),data.fieldValues().get("contractAmount"));
        assertEquals(nativeData.customerName(),data.fieldValues().get("customerName"));assertEquals(nativeData.contractType(),data.fieldValues().get("contractType"));
        assertEquals(nativeData.currencyCode(),data.fieldValues().get("currencyCode"));
        jdbc.update("DELETE FROM system_role_menu WHERE tenant_id=7 AND role_id=701 AND menu_id=(SELECT id FROM system_menu WHERE permission='pms:commerce:contract:sensitive-read')");
        var masked=access.read(target(Model.CONTRACT,10),actor(),"detail");assertNull(masked.fieldValues().get("contractAmount"));assertNull(masked.fieldValues().get("customerName"));
        assertNull(masked.fieldValues().get("contractType"));assertNull(masked.fieldValues().get("currencyCode"));assertTrue(masked.available());
        assertEquals(masked,access.query(page(Model.CONTRACT,1,null),actor()).members().getFirst());
    }
    @Test void independentOrderProjectionNeverAddsMoneyEvenForSensitiveReader(){
        login(7L,23L);var data=access.read(target(Model.ORDER,10),actor(),"detail");var nativeData=orderController.pageOrders(null,null,null,null,null,1,20).getData().getList().getFirst();
        assertEquals(nativeData.customerName(),data.fieldValues().get("customerName"));
        for(String code:List.of("orderAmount","currencyCode","sourceRecordKey","sourceProjectName","salesType","orderCreateTime","customerRequiredTime")) assertFalse(data.fieldValues().containsKey(code),code);
    }
    @Test void changingOrderContractRelationshipClosesProjectVisibility(){
        grants=List.of();projects=Set.of(100L);assertTrue(access.read(target(Model.ORDER,40),actor(),"detail").available());
        jdbc.update("UPDATE com_order_contract_relation SET contract_id=20 WHERE id=400");
        assertTrue(access.query(page(Model.ORDER,20,null),actor()).members().isEmpty());reject("ENTITY_SCOPE_DENIED",()->access.read(target(Model.ORDER,40),actor(),"detail"));
    }
    @Test void boundedNativePagesDoNotTruncateAtTwoHundredAndCursorRechecksAuthorization(){
        for(long id=1000;id<1205;id++)insert(id,7L,"A",false);
        Set<Long> collected=new LinkedHashSet<>();String cursor=null;do{var slice=access.query(page(Model.ORDER,200,cursor),actor());assertTrue(slice.members().size()<=200);
            for(var row:slice.members())assertTrue(collected.add(row.ref().entityId()));cursor=slice.nextCursor();}while(cursor!=null);
        assertEquals(207,collected.size());assertTrue(collected.contains(1204L));
        var first=access.query(page(Model.ORDER,1,null),actor());grants=List.of();
        assertTrue(access.query(page(Model.ORDER,1,first.nextCursor()),actor()).members().isEmpty());
        reject("PAGE_SIZE_INVALID",()->access.query(page(Model.ORDER,201,null),actor()));
        reject("CURSOR_INVALID",()->access.query(page(Model.ORDER,10,"not-native-cursor"),actor()));
    }
    @Test void currentCommerceReaderDoesNotSubstituteCurrentRowsForRevisionIdentity(){
        for(var m:Model.values())reject("REVISION_UNSUPPORTED",()->access.read(EntityDataRef.revision(new RevisionRef(target(m,10).entity(),999L)),actor(),"detail"));
    }
    @Test void duplicateContentOriginsFailClosedForListAndDetail(){
        var duplicate=mock(BusinessEntityContentReader.class);when(duplicate.supports("COM","contract")).thenReturn(true);
        var original=context.getBean(BusinessEntityContentReader.class);
        ReflectionTestUtils.setField(access,"contentReaders",List.of(original,duplicate));
        reject("CONTENT_READER_CONFLICT",()->access.read(target(Model.CONTRACT,10),actor(),"detail"));
        reject("CONTENT_READER_CONFLICT",()->access.query(page(Model.CONTRACT,10,null),actor()));
        verify(duplicate,never()).read(any(),any());verify(duplicate,never()).query(any(),any());
    }
    @Test void existingReadOnlyContentProviderDoesNotOpenAnUnscopedGenericList(){
        var legacy=mock(BusinessEntityContentReader.class);when(legacy.supports("COM","contract")).thenReturn(true);
        var fallback=new cn.iocoder.yudao.module.pms.platform.support.access.DefaultBusinessEntityAccess(
            context.getBean(BusinessModelCatalog.class),context.getBean(BusinessEntityPersistenceRegistry.class),
            context.getBean(BusinessAccessGuard.class),null,List.of(),List.of(legacy));
        reject("SCOPE_POLICY_NOT_DECLARED",()->fallback.query(page(Model.CONTRACT,10,null),actor()));
        verify(legacy,never()).read(any(),any());verify(legacy,never()).query(any(),any());
    }
    @Test void sensitiveNumericFiltersUseTheSameProjectedValueWithoutScaleArtifacts(){
        login(7L,23L);
        var amount=new BusinessFieldFilter("contractAmount",BusinessFieldFilter.Operator.EQ,List.of(10));
        var query=new BusinessEntityPageQuery("list","COM","contract",List.of(amount),10,null);
        assertEquals(List.of(10L,11L),ids(access.query(query,actor())));
        var unknown=new BusinessFieldFilter("authorityStatus",BusinessFieldFilter.Operator.EQ,List.of("CONFIRMED"));
        reject("FIELD_NOT_OPEN",()->access.query(new BusinessEntityPageQuery("list","COM","contract",List.of(unknown),10,null),actor()));
        login(7L,17L);reject("FIELD_NOT_OPEN",()->access.query(query,actor()));
    }
    @Test void ambiguousLargeOrderDirectoryUsesOneScopedDetailLookup(){
        bulkOrders(10000,"SHARED",false);
        dataQueries.clear();long started=System.nanoTime();
        assertTrue(access.read(target(Model.ORDER,19999),actor(),"detail").available());
        long statements=dataQueries.stream().filter(id->id.contains("SalesOrderMapper.")).count();
        System.out.println("COM_READ_METRIC detail rows=10000 statements="+statements+" elapsedMs="+(System.nanoTime()-started)/1_000_000);
        assertTrue(statements<=2,"Detail must not walk the authorized directory: "+statements);
    }
    @Test void sparseLargeFilterHasBoundedWorkAndResumableCursorWithoutOmissions(){
        bulkOrders(10000,"BULK-",true);
        var filter=new BusinessFieldFilter("customerName",BusinessFieldFilter.Operator.EQ,List.of("RARE"));
        Set<Long> found=new LinkedHashSet<>();Set<String> cursors=new HashSet<>();String cursor=null;int requests=0;
        long started=System.nanoTime();
        do {
            dataQueries.clear();var slice=access.query(new BusinessEntityPageQuery("list","COM","salesOrder",List.of(filter),1,cursor),actor());
            long statements=dataQueries.stream().filter(id->id.contains("SalesOrderMapper.")).count();
            assertTrue(statements<=2,"One sparse request must not scan an unbounded directory: "+statements);
            for(var row:slice.members())assertTrue(found.add(row.ref().entityId()),"Duplicate cursor row");
            cursor=slice.nextCursor();if(cursor!=null)assertTrue(cursors.add(cursor),"Cursor did not advance");
            assertTrue(++requests<=100,"Large fixture traversal did not terminate");
        } while(cursor!=null);
        System.out.println("COM_READ_METRIC sparse rows=10000 requests="+requests+" elapsedMs="+(System.nanoTime()-started)/1_000_000);
        assertEquals(Set.of(19999L),found);
    }
    @ParameterizedTest @EnumSource(Model.class) void deletingPriorRowDoesNotSkipTheNextAuthorizedCursorRow(Model m){
        var first=access.query(page(m,1,null),actor());assertEquals(List.of(10L),ids(first));
        jdbc.update("UPDATE "+m.table+" SET deleted=1 WHERE id=10");
        assertEquals(List.of(11L),ids(access.query(page(m,1,first.nextCursor()),actor())),"Offset cursor must not omit the remaining authorized row");
    }
    @ParameterizedTest @EnumSource(Model.class) void hiddenForeignAndNonexistentIdsShareTheSameDenial(Model m){
        for(long id:List.of(20L,30L,999999L))reject("ENTITY_SCOPE_DENIED",()->access.read(target(m,id),actor(),"detail"));
    }
    @ParameterizedTest @EnumSource(Model.class) void keyCursorUsesNativeCollationAndUniqueIdForTiedCodes(Model m){
        insert(1001L,7L,"A",false);insert(1002L,7L,"C",false);grants=List.of(grant("A","D1"),grant("C","D1"));
        String column=m==Model.CONTRACT?"contract_no":"order_no";
        jdbc.update("UPDATE "+m.table+" SET "+column+"='KÉY' WHERE id=1001");
        jdbc.update("UPDATE "+m.table+" SET "+column+"='key' WHERE id=1002");
        var selected=new ArrayList<Long>();String cursor=null;do{var slice=access.query(page(m,1,cursor),actor());selected.addAll(ids(slice));cursor=slice.nextCursor();}while(cursor!=null);
        assertEquals(List.of(10L,11L,1001L,1002L),selected);
        var first=access.query(page(m,1,null),actor());var other=m==Model.CONTRACT?Model.ORDER:Model.CONTRACT;
        reject("CURSOR_INVALID",()->access.query(page(other,1,first.nextCursor()),actor()));
    }
    void bulkOrders(int count,String code,boolean unique){
        var batch=new ArrayList<Object[]>();for(int i=0;i<count;i++)batch.add(new Object[]{10000L+i,unique?code+String.format("%05d",i):i==count-1?code:"AAA-"+code+String.format("%05d",i),i==count-1?"RARE":"COMMON"});
        jdbc.batchUpdate("INSERT INTO com_sales_order(id,tenant_id,company_code,order_no,customer_name,status,deleted,version) VALUES(?,7,'A',?,?,'ENABLED',0,0)",batch);
    }
    @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(type=org.apache.ibatis.executor.Executor.class,method="query",
            args={org.apache.ibatis.mapping.MappedStatement.class,Object.class,org.apache.ibatis.session.RowBounds.class,org.apache.ibatis.session.ResultHandler.class}))
    static class QueryCounter implements org.apache.ibatis.plugin.Interceptor {
        final List<String> queries;QueryCounter(List<String> queries){this.queries=queries;}
        public Object intercept(org.apache.ibatis.plugin.Invocation invocation)throws Throwable{
            queries.add(((org.apache.ibatis.mapping.MappedStatement)invocation.getArgs()[0]).getId());return invocation.proceed();}
    }

    UserCompanyDepartmentScopeRespDTO grant(String company,String department){var result=new UserCompanyDepartmentScopeRespDTO();result.setId(701L);result.setVersion(1);
        result.setCompanyId(1L);result.setCompanyCode(company);result.setDepartmentId(11L);result.setDepartmentCode(department);return result;}
    void insert(long id,long tenant,String company,boolean deleted){TenantContextHolder.setTenantId(tenant);try{
        var c=new ContractDO();c.setId(id);c.setTenantId(tenant);c.setVersion(0L);c.setDeleted(deleted);c.setCompanyCode(company);c.setCompanyName("Company "+company);
        c.setContractNo("CODE-"+id);c.setContractName("CONTRACT-"+id);c.setContractType("TYPE-PRIVATE");c.setCustomerCode("CUSTOMER-CODE-"+id);c.setCustomerName("CUSTOMER-"+id);
        c.setContractAmount(BigDecimal.TEN);c.setCurrencyCode("CNY");c.setStatus("ENABLED");c.setAuthorityStatus("CONFIRMED");c.setSourceLifecycleStatus("ACTIVE");c.setDepartmentCode("D1");contracts.insert(c);
        var o=new SalesOrderDO();o.setId(id);o.setTenantId(tenant);o.setVersion(0L);o.setDeleted(deleted);o.setCompanyCode(company);o.setCompanyName("Company "+company);
        o.setOrderNo("CODE-"+id);o.setOrderType("NORMAL");o.setCustomerCode("CUSTOMER-CODE-"+id);o.setCustomerName("CUSTOMER-"+id);o.setOrderAmount(BigDecimal.TEN);o.setCurrencyCode("CNY");
        o.setSourceSystem("ERP");o.setSourceVersion("v1");o.setSourceRecordKey("private-source-"+id);o.setStatus("ENABLED");orders.insert(o);
    }finally{TenantContextHolder.setTenantId(7L);}}
    void schema(Class<?> type){var table=TableInfoHelper.getTableInfo(type);var columns=new ArrayList<String>();columns.add("id BIGINT PRIMARY KEY");
        for(var field:table.getFieldList()){Class<?> kind=field.getPropertyType();String sql=kind==Long.class||kind==Integer.class?"BIGINT":kind==Boolean.class?"BOOLEAN":kind==LocalDateTime.class?"DATETIME(3)":kind==BigDecimal.class?"DECIMAL(20,6)":"LONGTEXT";
            columns.add("`"+field.getColumn()+"` "+sql+(field.getColumn().equals("deleted")?" DEFAULT FALSE":""));}
        jdbc.execute("DROP TABLE IF EXISTS "+table.getTableName());jdbc.execute("CREATE TABLE "+table.getTableName()+" ("+String.join(",",columns)+")");}
    void permissionSchema() throws Exception {String ddl=Files.readString(Path.of("../sql/migrations/V1__yudao_platform.sql"));
        for(String table:List.of("system_role","system_menu","system_role_menu","system_user_role")){var match=Pattern.compile("CREATE TABLE(?: IF NOT EXISTS)? `"+table+"`.*?;",Pattern.DOTALL).matcher(ddl);
            assertTrue(match.find());jdbc.execute("DROP TABLE IF EXISTS "+table);jdbc.execute(match.group());}
        for(long tenant:List.of(7L,8L)){jdbc.update("INSERT INTO system_role(id,name,code,sort,status,type,tenant_id) VALUES(?,?,?,1,0,2,?),(?,?,?,2,0,2,?)",
            tenant*100+1,"Commerce sensitive writer","com_fixture_writer",tenant,tenant*100+2,"Commerce query reader","com_fixture_reader",tenant);
            jdbc.update("INSERT INTO system_user_role(user_id,role_id,tenant_id) VALUES(23,?,?),(17,?,?)",tenant*100+1,tenant,tenant*100+2,tenant);}
        var codes=List.of("pms:commerce:contract:query","pms:commerce:contract:sensitive-read","pms:business-model:operate","pms:commerce:contract:relate","pms:commerce:authority:reconcile");
        for(int i=0;i<codes.size();i++){long id=995106300001L+i;jdbc.update("INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,status) VALUES(?,?,?,3,0,0,'',0)",id,"Commerce read fixture",codes.get(i));
            for(long tenant:List.of(7L,8L)){jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(?,?,?)",tenant*100+1,id,tenant);
                if(i==0)jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(?,?,?)",tenant*100+2,id,tenant);}}
    }
}
