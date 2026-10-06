package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkServiceImpl;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.web.TenantContextWebFilter;
import cn.iocoder.yudao.framework.tenant.core.security.TenantSecurityWebFilter;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.TenantCallerContext;
import cn.iocoder.yudao.module.system.api.permission.PermissionApiImpl;
import cn.iocoder.yudao.module.system.api.oauth2.OAuth2TokenApiImpl;
import cn.iocoder.yudao.module.system.controller.admin.auth.AuthController;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthServiceImpl;
import cn.iocoder.yudao.module.system.service.user.AdminUserServiceImpl;
import cn.iocoder.yudao.module.system.service.oauth2.*;
import cn.iocoder.yudao.module.system.service.logger.LoginLogService;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.*;
import cn.iocoder.yudao.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Production username/password, SQL token validation, token/tenant filters and method permissions. */
@EnabledIfSystemProperty(named="npdms.declared.exclusive",matches="true")
class DeclaredLoginRuntimePersistenceTest {
    DeclaredBusinessRuntimePersistenceTest.Runtime runtime;
    AnnotationConfigApplicationContext secured;MockMvc http;
    String password;
    org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory redis;
    OAuth2AccessTokenRedisDAO tokenCache;
    @Configuration(proxyBeanMethods=false) @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity @EnableMethodSecurity(proxyTargetClass=true) @EnableTransactionManagement
    static class SecurityConfig {
        @Bean org.springframework.security.web.SecurityFilterChain security(org.springframework.security.config.annotation.web.builders.HttpSecurity http,
                TokenAuthenticationFilter tokens,TenantSecurityWebFilter tenants) throws Exception {
            return http.csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
                .sessionManagement(session->session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests->requests.requestMatchers("/admin-api/system/auth/login").permitAll().anyRequest().authenticated())
                .exceptionHandling(errors->errors.authenticationEntryPoint(new cn.iocoder.yudao.framework.security.core.handler.AuthenticationEntryPointImpl())
                    .accessDeniedHandler(new cn.iocoder.yudao.framework.security.core.handler.AccessDeniedHandlerImpl()))
                .addFilterBefore(tokens,org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenants,TokenAuthenticationFilter.class).build();
        }
        @Bean(name="ss") SecurityFrameworkServiceImpl permissions(DeclaredBusinessRuntimePersistenceTest.Runtime runtime){
            var api=new PermissionApiImpl();ReflectionTestUtils.setField(api,"permissionService",runtime.context.getBean(cn.iocoder.yudao.module.system.service.permission.PermissionServiceImpl.class));
            return new SecurityFrameworkServiceImpl(api);
        }
        @Bean PlatformTransactionManager transactions(DeclaredBusinessRuntimePersistenceTest.Runtime runtime){return new DataSourceTransactionManager(runtime.source);}
        @Bean BusinessModelController business(DeclaredBusinessRuntimePersistenceTest.Runtime runtime){return runtime.controller;}
        @Bean BusinessOperationRecoveryController receipts(DeclaredBusinessRuntimePersistenceTest.Runtime runtime){return new BusinessOperationRecoveryController(runtime.dispatcher);}
    }
    @BeforeEach void start() throws Exception {
        boolean browser=Boolean.getBoolean("npdms.declared.browserLogin");
        runtime=browser?new DeclaredBusinessRuntimePersistenceTest.Runtime(true,0,false,true):new DeclaredBusinessRuntimePersistenceTest.Runtime();
        DeclaredBusinessRuntimePersistenceTest.initializeExclusiveSchema(runtime);
        if(browser) DeclaredCapabilitiesRuntimePersistenceTest.initializeCapabilities(runtime);
        var config=runtime.sessions.getConfiguration();for(var mapper:List.of(AdminUserMapper.class,OAuth2ClientMapper.class,OAuth2AccessTokenMapper.class,OAuth2RefreshTokenMapper.class))config.addMapper(mapper);
        String ddl=Files.readString(Path.of("../sql/migrations/V1__yudao_platform.sql"));
        for(String table:List.of("system_users","system_oauth2_client","system_oauth2_access_token","system_oauth2_refresh_token")){
            runtime.jdbc.execute("DROP TABLE IF EXISTS "+table);
            var statement=Pattern.compile("CREATE TABLE `"+table+"`.*?^[)].*?;",Pattern.DOTALL|Pattern.MULTILINE).matcher(ddl);assertTrue(statement.find());runtime.jdbc.execute(statement.group());
        }
        // Random credentials live only in this test's memory; SQL receives a BCrypt digest.
        password=UUID.randomUUID().toString().replace("-","").substring(0,12);
        var encoder=new BCryptPasswordEncoder();String digest=encoder.encode(password);
        for(long user:List.of(880001L,880002L,880003L,880004L))runtime.jdbc.update("INSERT INTO system_users(id,username,password,nickname,status,tenant_id,creator) VALUES(?,?,?,?,0,7,'it_declared')",user,"ituser"+user,digest,"IT user");
        runtime.jdbc.update("UPDATE system_users SET status=1 WHERE id=880004");
        runtime.jdbc.update("INSERT INTO system_oauth2_client(client_id,secret,name,logo,status,access_token_validity_seconds,refresh_token_validity_seconds,redirect_uris,authorized_grant_types) VALUES('default','','IT login','',0,1800,3600,'[]','[\"password\"]')");
        var users=new AdminUserServiceImpl();ReflectionTestUtils.setField(users,"userMapper",runtime.sessions.getMapper(AdminUserMapper.class));ReflectionTestUtils.setField(users,"passwordEncoder",encoder);
        var clients=new OAuth2ClientServiceImpl();ReflectionTestUtils.setField(clients,"oauth2ClientMapper",runtime.sessions.getMapper(OAuth2ClientMapper.class));runtime.context.getBeanFactory().registerSingleton("oauthClients",clients);
        var tokens=new OAuth2TokenServiceImpl();ReflectionTestUtils.setField(tokens,"oauth2AccessTokenMapper",runtime.sessions.getMapper(OAuth2AccessTokenMapper.class));ReflectionTestUtils.setField(tokens,"oauth2RefreshTokenMapper",runtime.sessions.getMapper(OAuth2RefreshTokenMapper.class));
        ReflectionTestUtils.setField(tokens,"oauth2ClientService",clients);ReflectionTestUtils.setField(tokens,"adminUserService",users);
        redis=new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory("127.0.0.1",27464);redis.afterPropertiesSet();redis.start();
        tokenCache=new OAuth2AccessTokenRedisDAO();ReflectionTestUtils.setField(tokenCache,"stringRedisTemplate",new org.springframework.data.redis.core.StringRedisTemplate(redis));
        ReflectionTestUtils.setField(tokens,"oauth2AccessTokenRedisDAO",tokenCache);
        var auth=new AdminAuthServiceImpl();auth.setCaptchaEnable(false);ReflectionTestUtils.setField(auth,"userService",users);ReflectionTestUtils.setField(auth,"oauth2TokenService",tokens);ReflectionTestUtils.setField(auth,"loginLogService",mock(LoginLogService.class));
        ReflectionTestUtils.setField(auth,"validator",jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());
        var authController=new AuthController();ReflectionTestUtils.setField(authController,"authService",auth);
        var tokenApi=new OAuth2TokenApiImpl();ReflectionTestUtils.setField(tokenApi,"oauth2TokenService",tokens);
        var properties=new WebProperties();new WebFrameworkUtils(properties);
        var errors=new GlobalExceptionHandler("declared-it",mock(cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi.class));
        var security=new SecurityProperties();assertFalse(security.getMockEnable());
        var tenantRules=new TenantProperties();var tenants=mock(TenantFrameworkService.class);
        doAnswer(call->{if(!Long.valueOf(7).equals(call.getArgument(0)))throw new IllegalArgumentException("Unknown isolated tenant");return null;}).when(tenants).validTenant(any());
        secured=new AnnotationConfigApplicationContext();secured.registerBean("runtime",DeclaredBusinessRuntimePersistenceTest.Runtime.class,()->runtime);
        secured.getBeanFactory().registerSingleton("tokenFilter",new TokenAuthenticationFilter(security,errors,tokenApi));
        secured.getBeanFactory().registerSingleton("tenantFilter",new TenantSecurityWebFilter(properties,tenantRules,Set.of(),errors,tenants));
        if(browser) secured.registerBean("genericForm",BusinessEntityFormController.class,()->new BusinessEntityFormController(runtime.forms,runtime.extensions,
            runtime.context.getBean(cn.iocoder.yudao.module.pms.platform.support.capability.DeclaredBusinessCapabilityAdapterFactory.class),new TenantCallerContext()));
        secured.register(SecurityConfig.class);secured.refresh();
        ReflectionTestUtils.setField(authController,"userService",users);
        ReflectionTestUtils.setField(authController,"roleService",runtime.context.getBean(cn.iocoder.yudao.module.system.service.permission.RoleServiceImpl.class));
        ReflectionTestUtils.setField(authController,"menuService",runtime.context.getBean(cn.iocoder.yudao.module.system.service.permission.MenuServiceImpl.class));
        ReflectionTestUtils.setField(authController,"permissionService",runtime.context.getBean(cn.iocoder.yudao.module.system.service.permission.PermissionServiceImpl.class));
        ReflectionTestUtils.setField(authController,"securityProperties",security);
        var controllers=new ArrayList<Object>(List.of(authController,secured.getBean(BusinessModelController.class),secured.getBean(BusinessOperationRecoveryController.class)));
        if(browser) controllers.add(secured.getBean(BusinessEntityFormController.class));
        var productionJson=tools.jackson.databind.json.JsonMapper.builder()
            .addModule(new cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration().timestampSupportModuleBean()).build();
        http=MockMvcBuilders.standaloneSetup(controllers.toArray())
            .setMessageConverters(new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(productionJson))
            .setCustomHandlerMapping(()->{var mapping=new org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping();
                mapping.setPathPrefixes(Map.of("/admin-api",type->type.equals(AuthController.class)));return mapping;})
            .setControllerAdvice(new BusinessModelContractAdvice(),errors)
            .addFilters(new TenantContextWebFilter(),(jakarta.servlet.Filter)secured.getBean("springSecurityFilterChain")).build();
        SecurityContextHolder.clearContext();TenantContextHolder.clear();
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();TenantContextHolder.clear();if(secured!=null)secured.close();if(runtime!=null)runtime.close();if(redis!=null)redis.destroy();password=null;}
    tools.jackson.databind.JsonNode request(String method,String path,String token,long tenant,Object body) throws Exception {
        var request=MockMvcRequestBuilders.request(HttpMethod.valueOf(method),path).servletPath(path.split("[?]",2)[0]).header("tenant-id",tenant);
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(JsonUtils.toJsonString(body));
        try{return JsonUtils.parseTree(http.perform(request).andReturn().getResponse().getContentAsString());}
        finally{SecurityContextHolder.clearContext();TenantContextHolder.clear();}
    }
    String login(long user) throws Exception {
        var response=request("POST","/admin-api/system/auth/login",null,7,Map.of("username","ituser"+user,"password",password));
        assertEquals(0,response.path("code").asInt(),()->"Login failed: "+response.path("msg").asString());return response.path("data").path("accessToken").asString();
    }
    private Map<String,Object> create(String key,long project){return Map.of("idempotencyKey",key,"input",Map.of("projectRef",project,"title","Login chain"));}
    @Test void realLoginRejectsBadPasswordDisabledUserAndUnauthenticatedMethods() throws Exception {
        assertNotEquals(0,request("POST","/admin-api/system/auth/login",null,7,Map.of("username","ituser880001","password","invalid" )).path("code").asInt());
        assertNotEquals(0,request("POST","/admin-api/system/auth/login",null,7,Map.of("username","ituser880004","password",password)).path("code").asInt());
        assertNotEquals(0,request("GET","/api/v1/pms/business-models/IT/declaredNote",null,7,null).path("code").asInt());
        assertEquals(0,runtime.jdbc.queryForObject("SELECT COUNT(*) FROM system_oauth2_access_token",Integer.class));
    }
    @Test void productionTokenFilterAndMethodPermissionsAllowWriterDenyReaderAndNoRole() throws Exception {
        String reader=login(880002),writer=login(880001),noRole=login(880003);
        assertNotNull(tokenCache.get(writer));
        assertEquals(0,request("GET","/api/v1/pms/business-models/IT/declaredNote",reader,7,null).path("code").asInt());
        assertNotEquals(0,request("POST","/api/v1/pms/business-models/IT/declaredNote/operations/create",reader,7,create("reader",99)).path("code").asInt());
        assertNotEquals(0,request("GET","/api/v1/pms/business-models/IT/declaredNote",noRole,7,null).path("code").asInt());
        var created=request("POST","/api/v1/pms/business-models/IT/declaredNote/operations/create",writer,7,create("writer",99));assertEquals(0,created.path("code").asInt());
        long id=created.path("data").path("entityRef").path("entityId").asLong();
        assertFalse(request("GET","/api/v1/pms/business-models/IT/declaredNote/data?id="+id,reader,7,null).path("data").path("fieldValues").has("internalMemo"));
        assertNotEquals(0,request("GET","/api/v1/pms/business-models/IT/declaredNote",writer,8,null).path("code").asInt(),"Cache-hit token must not use another tenant's role rows");
        tokenCache.delete(writer);
        assertNotEquals(0,request("GET","/api/v1/pms/business-models/IT/declaredNote",writer,8,null).path("code").asInt(),"Cold-cache token must not cross tenants");
        assertNotEquals(0,request("POST","/api/v1/pms/business-models/IT/declaredNote/operations/create",writer,7,create("outside",100)).path("code").asInt());
        assertEquals(1,runtime.jdbc.queryForObject("SELECT COUNT(*) FROM it_declared_note",Integer.class));
    }
    @Test void receiptRecoveryUsesAuthenticatedQueryWithoutAnotherBusinessWrite() throws Exception {
        String writer=login(880001);var payload=create("refresh-query",99);
        var result=request("POST","/api/v1/pms/business-models/IT/declaredNote/operations/create",writer,7,payload);assertEquals(0,result.path("code").asInt());
        var recovered=request("GET","/api/v1/pms/business-models/IT/declaredNote/operations/create/receipt?operationVersion=1&idempotencyKey=refresh-query",writer,7,null);
        assertEquals(result.path("data"),recovered.path("data"));
        assertEquals(1,runtime.jdbc.queryForObject("SELECT COUNT(*) FROM it_declared_note",Integer.class));
        for(String table:List.of("plt_idempotency_record","plt_operation_audit","plt_outbox_event"))assertEquals(1,runtime.jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
    }
    /** Real browser transport delegates every business request to the production security filter chain. */
    @Test @EnabledIfSystemProperty(named="npdms.declared.browserLogin",matches="true")
    void browserRealLoginFormAndRefreshRecovery() throws Exception {
        runtime.jdbc.update("UPDATE plt_dynamic_form_template_revision SET form_rules_json=? WHERE id=900101",
            "[{\"type\":\"input\",\"field\":\"heading\",\"title\":\"Heading\"},{\"type\":\"input\",\"field\":\"detail\",\"title\":\"Detail\",\"validate\":[{\"required\":true,\"message\":\"Detail required\"}]},{\"type\":\"checkbox\",\"field\":\"flags\",\"title\":\"Flags\",\"options\":[{\"label\":\"A\",\"value\":\"A\"},{\"label\":\"B\",\"value\":\"B\"}]}]");
        var completed=new java.util.concurrent.CountDownLatch(1);
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",27462),0);
        server.createContext("/",exchange->{
            try {
                String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();
                byte[] content;
                if(path.equals("/fixture/credentials")) content=JsonUtils.toJsonString(Map.of("username","ituser880001","password",password)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                else {
                    String cookie=exchange.getRequestHeaders().getFirst("Cookie"),token=null;
                    if(cookie!=null) for(String part:cookie.split(";")) {var pair=part.trim().split("=",2);if(pair.length==2 && pair[0].equals("it_access"))token=pair[1];}
                    String target=path.equals("/finish") || path.equals("/fixture/counts")?"/api/v1/pms/business-models/IT/declaredNote":exchange.getRequestURI().toString();
                    var req=MockMvcRequestBuilders.request(HttpMethod.valueOf(method),target).servletPath(target.split("[?]",2)[0])
                        .header("tenant-id",Objects.requireNonNullElse(exchange.getRequestHeaders().getFirst("tenant-id"),"7"));
                    if(token!=null) req.header("Authorization","Bearer "+token);
                    byte[] body=exchange.getRequestBody().readAllBytes();if(body.length>0)req.contentType(MediaType.APPLICATION_JSON).content(body);
                    var response=http.perform(req).andReturn().getResponse();content=response.getContentAsByteArray();
                    var result=JsonUtils.parseTree(new String(content,java.nio.charset.StandardCharsets.UTF_8));
                    if(path.equals("/admin-api/system/auth/login") && result.path("code").asInt()==0) {
                        exchange.getResponseHeaders().add("Set-Cookie","it_access="+result.path("data").path("accessToken").asString()+"; HttpOnly; SameSite=Strict; Path=/");
                        content=JsonUtils.toJsonString(Map.of("code",0,"data",Map.of("authenticated",true))).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    }
                    if(path.equals("/fixture/counts") && result.path("code").asInt()==0) {
                        Map<String,Object> counts=new LinkedHashMap<>();for(String table:List.of("it_declared_note","plt_idempotency_record","plt_operation_audit","plt_outbox_event","plt_entity_extension_definition","plt_entity_extension_value","plt_entity_form_binding"))counts.put(table,runtime.jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class));
                        counts.put("title",runtime.jdbc.queryForObject("SELECT title FROM it_declared_note",String.class));
                        content=JsonUtils.toJsonString(Map.of("code",0,"data",counts)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    }
                    if(path.equals("/finish") && result.path("code").asInt()==0) completed.countDown();
                }
                exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");exchange.sendResponseHeaders(200,content.length);exchange.getResponseBody().write(content);
            } catch(Exception failure) {
                byte[] error=JsonUtils.toJsonString(Map.of("code",500,"msg",failure.getClass().getSimpleName())).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500,error.length);exchange.getResponseBody().write(error);
            } finally {exchange.close();SecurityContextHolder.clearContext();TenantContextHolder.clear();}
        });
        server.start();
        try {
            assertTrue(completed.await(8,java.util.concurrent.TimeUnit.MINUTES),"Authenticated browser did not finish");
            assertEquals("Browser form saved",runtime.jdbc.queryForObject("SELECT title FROM it_declared_note",String.class));
            assertEquals(1,runtime.jdbc.queryForObject("SELECT COUNT(*) FROM it_declared_note",Integer.class));
            for(String table:List.of("plt_idempotency_record","plt_outbox_event"))assertEquals(2,runtime.jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class),table);
            assertEquals(4,runtime.jdbc.queryForObject("SELECT COUNT(*) FROM plt_operation_audit",Integer.class));
            assertEquals(1,runtime.jdbc.queryForObject("SELECT COUNT(*) FROM plt_entity_extension_value",Integer.class));
        } finally {server.stop(0);}
    }

}
