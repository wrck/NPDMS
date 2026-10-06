package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkServiceImpl;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ExecutionBackendCapability;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.BusinessModelController;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.PermissionBusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import cn.iocoder.yudao.module.system.api.permission.*;
import cn.iocoder.yudao.module.system.dal.mysql.permission.*;
import cn.iocoder.yudao.module.system.service.permission.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real system permission services/mappers and method security, inside the owned marked MySQL schema.
 * Project scope remains the enclosing persistence fixture's test double; this is not a full application login test. */
final class SiteSurveyOrdinaryRoleMysqlAcceptance {
    static void verify(SiteSurveySpringPersistenceTest f) throws Exception {
        if (!SiteSurveySpringPersistenceTest.mysqlOptIn()) throw new IllegalStateException("OWNED_MYSQL_REQUIRED");
        var ds=f.ctx.getBean(DataSource.class);
        try(var c=ds.getConnection()) {
            ScriptUtils.executeSqlScript(c,new ClassPathResource("site-survey-isolated-mysql/V1__isolated_system_permission_tables.sql"));
        }
        var jdbc=f.jdbc;
        for(Object[] menu:List.of(new Object[]{100L,"pms:sol-site-survey:query",0},new Object[]{101L,"pms:sol-site-survey:create",0},
                new Object[]{102L,"pms:sol-site-survey:update",0},new Object[]{103L,"pms:sol-site-survey:delete",0},
                new Object[]{104L,"pms:requirement-analysis:manage",0},new Object[]{106L,"pms:sol-site-survey:create",1},
                new Object[]{993109100501L,"",0},new Object[]{993109100511L,"pms:business-model:query",0})) {
            jdbc.update("INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,status,visible,keep_alive,always_show,creator,updater) VALUES (?,?,?, ?,0,?,'','',?,1,1,1,'survey-it','survey-it')",
                    menu[0],"isolated-menu-"+menu[0],menu[1],((Long)menu[0])==993109100501L?1:((Long)menu[0])==993109100511L?2:3,
                    ((Long)menu[0])==993109100511L?993109100501L:0L,menu[2]);
        }
        for(long role:List.of(900L,901L,902L,903L,904L,905L,906L)) {
            jdbc.update("INSERT INTO system_role(id,name,code,sort,status,type,data_scope,tenant_id,creator,updater) VALUES (?,?,?,0,?,2,5,?,'survey-it','survey-it')",
                    role,"isolated-role-"+role,"survey_ordinary_"+role,role==906?1:0,role==905?2:1);
        }
        grant(f,900,100,1);grant(f,900,101,1);grant(f,901,100,1);grant(f,902,100,1);grant(f,902,102,1);
        grant(f,903,104,1);grant(f,904,100,1);grant(f,904,106,1);grant(f,905,101,1);grant(f,906,101,1);
        jdbc.update("INSERT INTO system_user_role(user_id,role_id,tenant_id,creator,updater) VALUES (9,900,1,'survey-it','survey-it'),(10,901,1,'survey-it','survey-it')");
        migrate(ds);
        long operate=jdbc.queryForObject("SELECT id FROM system_menu WHERE permission='pms:business-model:operate' AND deleted=b'0'",Long.class);
        assertEquals(970000000000099224L,operate);
        assertEquals(1,mapped(f,900,operate));assertEquals(0,mapped(f,901,operate));assertEquals(1,mapped(f,903,operate));
        assertEquals(0,mapped(f,904,operate));assertEquals(0,mapped(f,905,operate));assertEquals(0,mapped(f,906,operate));
        assertEquals(1,mapped(f,901,993109100511L));assertEquals(1,mapped(f,901,993109100501L));
        assertEquals(0,mapped(f,903,100L),"RA manager cannot acquire a native survey query permission");
        long grantsBefore=f.count("system_role_menu");migrate(ds);assertEquals(grantsBefore,f.count("system_role_menu"));
        jdbc.update("UPDATE system_role_menu SET deleted=b'1' WHERE role_id=902 AND menu_id=? AND tenant_id=1",operate);
        migrate(ds);assertEquals(0,mapped(f,902,operate),"explicit routing revocation must not be reactivated");

        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);
        var conf=new MybatisConfiguration();conf.setMapUnderscoreToCamelCase(true);
        var tenant=new TenantProperties();tenant.setIgnoreTables(Set.of("system_menu"));
        var interceptors=new MybatisPlusInterceptor();interceptors.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(tenant)));conf.addInterceptor(interceptors);
        factory.setConfiguration(conf);var sessions=factory.getObject();
        for(Class<?> mapper:List.of(RoleMapper.class,MenuMapper.class,RoleMenuMapper.class,UserRoleMapper.class)) sessions.getConfiguration().addMapper(mapper);
        var template=new SqlSessionTemplate(sessions);
        var roles=new RoleServiceImpl();var menus=new MenuServiceImpl();var permissionService=new PermissionServiceImpl();var api=new PermissionApiImpl();
        ReflectionTestUtils.setField(roles,"roleMapper",template.getMapper(RoleMapper.class));ReflectionTestUtils.setField(roles,"permissionService",permissionService);
        ReflectionTestUtils.setField(menus,"menuMapper",template.getMapper(MenuMapper.class));ReflectionTestUtils.setField(menus,"permissionService",permissionService);
        ReflectionTestUtils.setField(permissionService,"roleMenuMapper",template.getMapper(RoleMenuMapper.class));
        ReflectionTestUtils.setField(permissionService,"userRoleMapper",template.getMapper(UserRoleMapper.class));
        ReflectionTestUtils.setField(permissionService,"roleService",roles);ReflectionTestUtils.setField(permissionService,"menuService",menus);
        ReflectionTestUtils.setField(api,"permissionService",permissionService);
        try(var auth=new GenericApplicationContext()) {
            auth.setParent(f.ctx);
            auth.getBeanFactory().registerSingleton("ordinaryRoleService",roles);auth.getBeanFactory().registerSingleton("ordinaryMenuService",menus);
            auth.getBeanFactory().registerSingleton("ordinaryPermissionService",permissionService);auth.getBeanFactory().registerSingleton("ordinaryPermissionApi",api);
            auth.registerBean(SpringUtil.class);auth.refresh();
            when(f.ctx.getBean(PermissionApi.class).hasAnyPermissions(anyLong(),any(String[].class))).thenAnswer(call ->
                    api.hasAnyPermissions((Long)call.getRawArguments()[0],(String[])call.getRawArguments()[1]));
            assertFalse(roles.hasAnySuperAdmin(Set.of(900L,901L)));
            assertFalse(SecurityFrameworkUtils.skipPermissionCheck());
            assertTrue(api.hasAnyPermissions(9L,"pms:business-model:operate"));assertFalse(api.hasAnyPermissions(10L,"pms:business-model:operate"));
            try(var secured=new AnnotationConfigApplicationContext()) {
                secured.setParent(auth);secured.register(MethodSecurity.class);
                secured.registerBean("ss",SecurityFrameworkServiceImpl.class,()->new SecurityFrameworkServiceImpl(api));
                secured.registerBean(SiteSurveyBusinessPermissionPolicy.class,()->new SiteSurveyBusinessPermissionPolicy(api));
                secured.registerBean(PermissionBusinessAccessGuard.class,()->new PermissionBusinessAccessGuard(api,secured.getBeanProvider(BusinessOwnerPermissionPolicy.class)));
                secured.registerBean(BusinessModelController.class,()->new BusinessModelController(f.ctx.getBean(BusinessModelCatalog.class),secured.getBean(PermissionBusinessAccessGuard.class),mock(BusinessEntityAccessPort.class),f.ctx.getBean(BusinessOperationDispatcher.class),secured.getBeanProvider(ExecutionBackendCapability.class)));
                secured.refresh();
                var controller=secured.getBean(BusinessModelController.class);
                var metadata=controller.detail("SOL","siteSurvey").getData();
                assertTrue(metadata.operations().stream().filter(o->o.code().equals("create")).findFirst().orElseThrow().executable());
                assertFalse(metadata.operations().stream().filter(o->o.code().equals("save")).findFirst().orElseThrow().executable());
                var request=new BusinessModelController.OperationExecuteReqVO();request.setIdempotencyKey("ordinary-create");request.setEntryKind(OperationEntryKind.INDEPENDENT);request.setInput(Map.of("values",Map.of("projectId",20L,"name","ordinary survey","location","onsite")));
                var receipt=controller.execute("SOL","siteSurvey","create",null,request).getData();
                assertNotNull(receipt.entityRef());assertEquals(1,f.count("plt_idempotency_record"));
                var before=f.counts();assertEquals(receipt,controller.execute("SOL","siteSurvey","create",null,request).getData());assertEquals(before,f.counts());
                login(10);assertFalse(SecurityFrameworkUtils.skipPermissionCheck());
                var queryOnly=controller.detail("SOL","siteSurvey").getData();assertTrue(queryOnly.operations().stream().noneMatch(BusinessModelController.OperationVO::executable));
                assertThrows(AccessDeniedException.class,()->controller.execute("SOL","siteSurvey","create",null,request));assertEquals(before,f.counts());
                grant(f,901,operate,1);assertTrue(api.hasAnyPermissions(10L,"pms:business-model:operate"));
                assertTrue(controller.detail("SOL","siteSurvey").getData().operations().stream().noneMatch(BusinessModelController.OperationVO::executable));
                assertThrows(RuntimeException.class,()->controller.execute("SOL","siteSurvey","create",null,request));assertEquals(before,f.counts());
                login(9);jdbc.update("UPDATE system_role_menu SET deleted=b'1' WHERE role_id=900 AND menu_id=101 AND tenant_id=1");
                assertTrue(api.hasAnyPermissions(9L,"pms:business-model:operate"));assertFalse(api.hasAnyPermissions(9L,"pms:sol-site-survey:create"));
                assertThrows(RuntimeException.class,()->controller.execute("SOL","siteSurvey","create",null,request));assertEquals(before,f.counts());
            }
        } finally {var spring=new SpringUtil();spring.setApplicationContext(f.ctx);spring.postProcessBeanFactory(f.ctx.getBeanFactory());login(9);}
    }
    @Configuration(proxyBeanMethods=false) @EnableMethodSecurity(proxyTargetClass=true) static class MethodSecurity {}
    private static void login(long id) {SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(id).setTenantId(1L).setUserType(2),new MockHttpServletRequest());}
    private static void grant(SiteSurveySpringPersistenceTest f,long role,long menu,long tenant) {
        f.jdbc.update("INSERT INTO system_role_menu(role_id,menu_id,tenant_id,creator,updater) VALUES (?,?,?,'survey-it','survey-it')",role,menu,tenant);
    }
    private static long mapped(SiteSurveySpringPersistenceTest f,long role,long menu) {
        return f.jdbc.queryForObject("SELECT COUNT(*) FROM system_role_menu WHERE role_id=? AND menu_id=? AND tenant_id=1 AND deleted=b'0'",Long.class,role,menu);
    }
    private static void migrate(DataSource ds) throws Exception {
        try(var c=ds.getConnection()) {ScriptUtils.executeSqlScript(c,new ClassPathResource("site-survey-isolated-mysql/V391__unified_business_entry_permission_mapping.sql"));}
    }
}
