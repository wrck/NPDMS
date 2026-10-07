package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.*;
import cn.iocoder.yudao.module.pms.platform.service.command.*;
import cn.iocoder.yudao.module.pms.platform.support.access.*;
import cn.iocoder.yudao.module.pms.platform.support.model.DeclaredBusinessModelDiagnostics;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApiImpl;
import cn.iocoder.yudao.module.system.dal.mysql.permission.*;
import cn.iocoder.yudao.module.system.service.permission.*;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Host module integration: real MySQL, system permission services and production transactional persistence.
 * The project API boundary supplies synthetic scope facts; ProjectBusinessScopeAccess is the production policy.
 * Never connects to an application/shared database. The harness intentionally uses the default service; optional extensions contain only business differences.
 */
@EnabledIfSystemProperty(named = "npdms.declared.exclusive", matches = "true")
class DeclaredBusinessRuntimePersistenceTest {
    private Runtime runtime;
    private JdbcTemplate jdbc;
    private static final long WRITER = 880001L, READER = 880002L;

    @BeforeEach void start() throws Exception {
        runtime = new Runtime(); jdbc = runtime.jdbc;
        initializeSchema();
        login(7L, WRITER);
    }
    @AfterEach void close() {
        SecurityContextHolder.clearContext(); TenantContextHolder.clear();
        if (runtime != null) runtime.close();
    }
    private void initializeSchema() throws Exception {
        // Exact DDL from repository migrations, without production seed accounts or data.
        String platform = Files.readString(Path.of("../sql/migrations/V63__fproj001_v18_atomic_project_creation.sql"));
        String system = Files.readString(Path.of("../sql/migrations/V1__yudao_platform.sql"));
        for (String name : List.of("plt_idempotency_record", "plt_operation_audit", "plt_outbox_event",
                "system_role", "system_menu", "system_role_menu", "system_user_role")) {
            jdbc.execute("DROP TABLE IF EXISTS " + name);
            var match = Pattern.compile("CREATE TABLE(?: IF NOT EXISTS)? `" + name + "`.*?;", Pattern.DOTALL)
                    .matcher(name.startsWith("plt_") ? platform : system);
            assertTrue(match.find(), "Authoritative DDL missing: " + name);
            jdbc.execute(match.group());
        }
        jdbc.execute("DROP TABLE IF EXISTS it_declared_note");
        jdbc.execute(new String(new ClassPathResource("declared/declared-note-schema.sql").getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        for (long tenant : List.of(7L, 8L)) {
            jdbc.update("INSERT INTO system_role (id,name,code,sort,status,type,tenant_id) VALUES (?,?,?,?,0,2,?)", tenant * 100 + 1, "IT writer", "it_writer", 1, tenant);
            jdbc.update("INSERT INTO system_role (id,name,code,sort,status,type,tenant_id) VALUES (?,?,?,?,0,2,?)", tenant * 100 + 2, "IT reader", "it_reader", 2, tenant);
            jdbc.update("INSERT INTO system_user_role (user_id,role_id,tenant_id) VALUES (?,?,?),(?,?,?)", WRITER, tenant * 100 + 1, tenant, READER, tenant * 100 + 2, tenant);
        }
        var permissions = List.of("pms:business-model:operate", "it:note:query", "it:note:create", "it:note:update", "it:note:uppercase", "pms:business-model:query");
        for (int i = 0; i < permissions.size(); i++) {
            long menuId = 980001L + i;
            jdbc.update("INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,status) VALUES (?,?,?,3,0,0,'',0)", menuId, "IT action", permissions.get(i));
            for (long tenant : List.of(7L, 8L)) {
                jdbc.update("INSERT INTO system_role_menu (role_id,menu_id,tenant_id) VALUES (?,?,?)", tenant * 100 + 1, menuId, tenant);
                if (i < 2 || i == 5) jdbc.update("INSERT INTO system_role_menu (role_id,menu_id,tenant_id) VALUES (?,?,?)", tenant * 100 + 2, menuId, tenant);
            }
        }
    }
    private static void login(long tenant, long user) {
        TenantContextHolder.setTenantId(tenant);
        var principal = new LoginUser(); principal.setId(user); principal.setTenantId(tenant); principal.setUserType(2);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
    private EntityActor actor() { return new EntityActor(TenantContextHolder.getRequiredTenantId(),
            ((LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId(), "it"); }
    private BusinessOperationRequest create(String key, Map<String,Object> input) {
        return new BusinessOperationRequest("create", 1, null, "IT", "declaredNote", input, key, null, OperationEntryKind.INDEPENDENT, null);
    }
    private BusinessOperationRequest save(BusinessOperationReceipt original, String key, Long version, Map<String,Object> input) {
        return new BusinessOperationRequest("save", 1, EntityDataRef.current(original.entityRef()), null, null,
                input, key, version, OperationEntryKind.INDEPENDENT, null);
    }
    private BusinessOperationReceipt create(String key) { return runtime.dispatcher.dispatch(create(key, Map.of("projectRef", 99L, "title", key))); }
    private void reject(String error, Runnable action) {
        assertEquals(error, assertThrows(BusinessContractException.class, action::run).getErrorCode());
    }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private void counts(long expected) {
        for (String table : List.of("it_declared_note", "plt_idempotency_record", "plt_operation_audit", "plt_outbox_event")) assertEquals(expected, count(table), table);
    }

    @Test void thirdModelNeedsOnlyDeclarationDoMapperAndSchema() {
        assertTrue(DeclaredBusinessModelDiagnostics.inspect(runtime.declaration).isEmpty());
        var created = runtime.dispatcher.dispatch(create("third", Map.of("projectRef", "99", "title", "Third", "internalMemo", "private", "amount", "12.50", "tags", List.of("a","b"))));
        var detail = runtime.access.read(EntityDataRef.current(created.entityRef()), actor(), "detail");
        assertEquals(new BigDecimal("12.50"), detail.fieldValues().get("amount"));
        assertEquals(List.of("a","b"), detail.fieldValues().get("tags"));
        assertFalse(detail.fieldValues().containsKey("internalMemo"));
        assertFalse(detail.fieldValues().containsKey("tenantId"));
        assertFalse(detail.fieldValues().containsKey("version"));
        var page = runtime.access.query(new BusinessEntityPageQuery("list", "IT", "declaredNote", List.of(), 10, null), actor());
        assertEquals(1, page.members().size());
        assertFalse(page.members().getFirst().fieldValues().containsKey("internalMemo"));
        counts(1);
    }
    @Test void queryAndPublicOperatePermissionsDoNotAuthorizeWrites() {
        create("baseline"); login(7, READER);
        assertEquals(1, runtime.access.query(new BusinessEntityPageQuery("list","IT","declaredNote",List.of(),10,null),actor()).members().size());
        reject("ACCESS_DENIED", () -> create("denied")); counts(1);
        login(7, WRITER);
        jdbc.update("DELETE FROM system_role_menu WHERE role_id=701 AND menu_id=980001");
        reject("ACCESS_DENIED", () -> create("no-public-operate")); counts(1);
    }
    @Test void permissionIsRecheckedBeforeReplayingCommittedResult() {
        var request=create("revoked",Map.of("projectRef",99L,"title","revoked"));
        runtime.dispatcher.dispatch(request);
        jdbc.update("DELETE FROM system_role_menu WHERE role_id=701 AND menu_id=980003");
        reject("ACCESS_DENIED", () -> runtime.dispatcher.dispatch(request)); counts(1);
    }
    @Test void scopeUsesProjectRefAndRejectsCrossProjectCreatesAndUpdates() {
        reject("ENTITY_SCOPE_DENIED", () -> runtime.dispatcher.dispatch(create("forbidden", Map.of("projectRef",100L,"title","x"))));
        var created=create("scoped");
        reject("ENTITY_SCOPE_DENIED", () -> runtime.dispatcher.dispatch(save(created,"move",0L,Map.of("projectRef",100L))));
        when(runtime.projectApi.resolveAllCurrent(any())).thenReturn(Set.of());
        doReturn(new ProjectScopeResult(99L,1L,Set.of(),Set.of())).when(runtime.projectApi).resolveCurrent(any());
        assertTrue(runtime.access.query(new BusinessEntityPageQuery("list","IT","declaredNote",List.of(),10,null),actor()).members().isEmpty());
        reject("ENTITY_SCOPE_DENIED", () -> runtime.access.read(EntityDataRef.current(created.entityRef()),actor(),"detail"));
        reject("ENTITY_SCOPE_DENIED", () -> runtime.dispatcher.dispatch(save(created,"scope-denied",0L,Map.of("title","new"))));
        reject("ENTITY_SCOPE_DENIED", () -> create("empty-scope")); counts(1);
    }
    @Test void declaredWritableOwnershipCanMoveBetweenAuthorizedScopes() {
        var created=create("move-allowed");
        runtime.dispatcher.dispatch(save(created,"move-allowed-save",0L,Map.of("projectRef",101L)));
        assertEquals(101L,jdbc.queryForObject("SELECT project_ref FROM it_declared_note",Long.class));
    }
    @Test void ownershipChangeLocksTreeRootsInGlobalOrder() {
        doAnswer(call -> {
            var query=call.getArgument(0,ProjectCurrentScopeQuery.class);
            long root=query.anchorProjectId()==99L?200L:100L;
            return new ProjectScopeResult(root,1L,Set.of(query.anchorProjectId()),Set.of());
        }).when(runtime.projectApi).resolveCurrent(any());
        doAnswer(call -> {
            var query=call.getArgument(0,ProjectScopeRevalidationQuery.class);
            long root=query.anchorProjectId()==99L?200L:100L;
            return new ProjectScopeResult(root,1L,Set.of(query.anchorProjectId()),Set.of());
        }).when(runtime.projectApi).lockAndRevalidate(any());
        var created=create("ordered-roots");
        clearInvocations(runtime.projectApi);
        runtime.dispatcher.dispatch(save(created,"ordered-roots-save",0L,Map.of("projectRef",101L)));
        var locked=org.mockito.ArgumentCaptor.forClass(ProjectScopeRevalidationQuery.class);
        verify(runtime.projectApi,times(2)).lockAndRevalidate(locked.capture());
        assertEquals(List.of(101L,99L),locked.getAllValues().stream().map(ProjectScopeRevalidationQuery::anchorProjectId).toList());
    }
    @Test void tenantIsolationAppliesToDetailPageWritesAndReceiptKeys() {
        var created=create("same-key"); login(8, WRITER);
        assertTrue(runtime.access.query(new BusinessEntityPageQuery("list","IT","declaredNote",List.of(),10,null),actor()).members().isEmpty());
        var foreign = new EntityRef(8L,"IT","declaredNote",created.entityRef().entityId());
        assertFalse(runtime.access.read(EntityDataRef.current(foreign),actor(),"detail").available());
        assertThrows(RuntimeException.class, () -> runtime.dispatcher.dispatch(save(created,"foreign",0L,Map.of("title","wrong"))));
        var own=create("same-key"); assertNotEquals(created.entityRef().entityId(), own.entityRef().entityId()); counts(2);
    }
    @Test void userWithNoRolesCannotReadOrWrite() {
        login(7,880099L);
        reject("ACCESS_DENIED", () -> create("no-roles"));
        reject("ACCESS_DENIED", () -> runtime.access.query(new BusinessEntityPageQuery("list","IT","declaredNote",List.of(),10,null),actor()));
        counts(0);
    }
    @Test void createPermissionDoesNotGrantUpdatePermission() {
        var created=create("create-only");
        jdbc.update("DELETE FROM system_role_menu WHERE role_id=701 AND menu_id=980004");
        reject("ACCESS_DENIED", () -> runtime.dispatcher.dispatch(save(created,"update-denied",0L,Map.of("title","x"))));
        create("still-create-allowed");counts(2);
    }
    @Test void projectScopeRevisionChangeBeforeWriteRollsBackReservation() {
        doReturn(new ProjectScopeResult(99L,2L,Set.of(99L),Set.of())).when(runtime.projectApi).lockAndRevalidate(any());
        reject("ENTITY_SCOPE_DENIED", () -> create("stale-scope"));counts(0);
    }
    @Test void viewScopeDoesNotGrantManageScope() {
        doAnswer(call -> {
            var query=call.getArgument(0,ProjectCurrentScopeQuery.class);
            return new ProjectScopeResult(99L,1L,ProjectScopeApi.ACTION_VIEW.equals(query.actionCode())?Set.of(99L):Set.of(),Set.of());
        }).when(runtime.projectApi).resolveCurrent(any());
        reject("ENTITY_SCOPE_DENIED", () -> create("view-only"));counts(0);
    }
    @Test void missingOperationAndScopeDeclarationsFailClosed() {
        var model=runtime.declaration.descriptor();
        var missing=new BusinessModelDescriptor(model.ownerModule(),model.entityType(),model.stableCode(),1,model.kind(),model.title(),model.authorizationPolicyRef(),
                model.fields(),model.relations(),List.of(new BusinessOperationDescriptor("create",1,"Create",BusinessOperationDescriptor.StandardOperationKind.CREATE)),model.capabilities(),null);
        var diagnostics=DeclaredBusinessModelDiagnostics.inspect(new BusinessModelDeclaration(missing,DeclaredNoteDO.class,runtime.declaration.mapper(),null));
        assertTrue(diagnostics.stream().anyMatch(issue -> "SCOPE_POLICY_NOT_DECLARED".equals(issue.errorCode())));
        assertTrue(diagnostics.stream().anyMatch(issue -> "OPERATION_PERMISSION_NOT_DECLARED".equals(issue.errorCode())));
        reject("OPERATION_PERMISSION_NOT_DECLARED", () -> runtime.guard.requireWritable(missing,actor(),"operation:create"));
        reject("SCOPE_POLICY_NOT_DECLARED", () -> new DeclaredBusinessScopeSupport(List.of()).queryFilters(missing,actor()));counts(0);
    }
    @Test void declaredScopeWithoutPolicyImplementationDoesNotBecomeTenantWide() {
        reject("SCOPE_POLICY_UNAVAILABLE", () -> new DeclaredBusinessScopeSupport(List.of()).queryFilters(runtime.declaration.descriptor(),actor()));counts(0);
    }
    @Test void casAlsoWorksWithoutOptionalOptimisticLockInterceptor() throws Exception {
        var created=create("no-interceptor");
        runtime.close();runtime=new Runtime(false);jdbc=runtime.jdbc;
        var saved=runtime.dispatcher.dispatch(save(created,"no-interceptor-save",0L,Map.of("title","saved")));
        assertEquals(1L,saved.newConcurrencyBasis());
        assertEquals(1L,jdbc.queryForObject("SELECT version FROM it_declared_note",Long.class));
        reject("CONCURRENCY_CONFLICT", () -> runtime.dispatcher.dispatch(save(created,"stale-again",0L,Map.of("title","stale"))));
    }
    @Test void requiredTypeReadonlyAndControlFieldsAreValidatedOnServer() {
        reject("FIELD_REQUIRED", () -> runtime.dispatcher.dispatch(create("missing",Map.of("projectRef",99L))));
        reject("FIELD_REQUIRED", () -> runtime.dispatcher.dispatch(create("blank",Map.of("projectRef",99L,"title"," "))));
        reject("FIELD_VALUE_INVALID", () -> runtime.dispatcher.dispatch(create("fraction",Map.of("projectRef",99.5,"title","x"))));
        reject("FIELD_VALUE_INVALID", () -> runtime.dispatcher.dispatch(create("list",Map.of("projectRef",99L,"title","x","tags",List.of(1)))));
        reject("FIELD_NOT_WRITABLE", () -> runtime.dispatcher.dispatch(create("readonly",Map.of("projectRef",99L,"title","x","referenceCode","tampered"))));
        reject("FIELD_NOT_WRITABLE", () -> runtime.dispatcher.dispatch(create("control",Map.of("projectRef",99L,"title","x","tenantId",8L)))); counts(0);
    }
    @Test void omittedRequiredValueIsPreservedAndExplicitNullIsRejected() {
        var created=create("required");
        runtime.dispatcher.dispatch(save(created,"omit",0L,Map.of("amount","4.25")));
        assertEquals("required",jdbc.queryForObject("SELECT title FROM it_declared_note",String.class));
        var clear=new LinkedHashMap<String,Object>(); clear.put("title",null);
        reject("FIELD_REQUIRED", () -> runtime.dispatcher.dispatch(save(created,"clear",1L,clear)));
        reject("CONCURRENCY_BASIS_REQUIRED", () -> runtime.dispatcher.dispatch(save(created,"no-version",null,Map.of("title","x"))));
        assertEquals(1, count("it_declared_note")); assertEquals(2,count("plt_idempotency_record"));
        assertEquals(2,count("plt_operation_audit")); assertEquals(2,count("plt_outbox_event"));
    }
    @Test void optionalExplicitNullIsPersisted() {
        var created=runtime.dispatcher.dispatch(create("nullable",Map.of("projectRef",99L,"title","x","amount","1.00")));
        var clear=new LinkedHashMap<String,Object>();clear.put("amount",null);
        runtime.dispatcher.dispatch(save(created,"nullable-clear",0L,clear));
        assertNull(jdbc.queryForObject("SELECT amount FROM it_declared_note",BigDecimal.class));
    }
    @Test void twoTransactionsWithSameVersionHaveExactlyOneWinner() throws Exception {
        var created=create("concurrent"); var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            List<Future<String>> results=new ArrayList<>();
            for(int i=0;i<2;i++) { int number=i;results.add(workers.submit(() -> {
                login(7,WRITER);ready.countDown();assertTrue(go.await(10,TimeUnit.SECONDS));
                try { runtime.dispatcher.dispatch(save(created,"writer-"+number,0L,Map.of("title","writer-"+number))); return "SAVED"; }
                catch(BusinessContractException conflict) { return conflict.getErrorCode(); }
                finally { SecurityContextHolder.clearContext();TenantContextHolder.clear(); }
            })); }
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();
            var outcomes=List.of(results.get(0).get(15,TimeUnit.SECONDS),results.get(1).get(15,TimeUnit.SECONDS));
            assertEquals(1,outcomes.stream().filter("SAVED"::equals).count());
            assertEquals(1,outcomes.stream().filter("CONCURRENCY_CONFLICT"::equals).count());
        }
        assertEquals(1L,jdbc.queryForObject("SELECT version FROM it_declared_note",Long.class));
        assertEquals(2,count("plt_idempotency_record"));assertEquals(2,count("plt_operation_audit"));assertEquals(2,count("plt_outbox_event"));
    }
    @Test void sameKeyAcrossConcurrentTransactionsExecutesOnce() throws Exception {
        var request=create("concurrent-replay",Map.of("projectRef",99L,"title","replay"));
        var go=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            Callable<BusinessOperationReceipt> work=() -> { login(7,WRITER); assertTrue(go.await(10,TimeUnit.SECONDS));
                try { return runtime.dispatcher.dispatch(request); }
                finally { SecurityContextHolder.clearContext();TenantContextHolder.clear(); }};
            var first=workers.submit(work);var second=workers.submit(work);go.countDown();
            assertEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
        }
        counts(1);
    }
    @Test void receiptSurvivesNewApplicationContextAndEntryContextChanges() throws Exception {
        var request=create("restart",Map.of("projectRef",99L,"title","restart"));
        var original=runtime.dispatcher.dispatch(request);
        runtime.close();runtime=new Runtime();jdbc=runtime.jdbc;
        var replay=new BusinessOperationRequest(request.operationCode(),1,null,"IT","declaredNote",request.input(),"restart",null,OperationEntryKind.INDEPENDENT,"another-entry");
        assertEquals(original,runtime.dispatcher.dispatch(replay)); counts(1);
        reject("IDEMPOTENCY_DIGEST_CONFLICT", () -> runtime.dispatcher.dispatch(create("restart",Map.of("projectRef",99L,"title","different")))); counts(1);
    }
    @Test void auditDatabaseFailureRollsBackSubjectLedgerAndEvents() { rollbackFailure("plt_operation_audit"); }
    @Test void outboxDatabaseFailureRollsBackSubjectLedgerAndAudit() { rollbackFailure("plt_outbox_event"); }
    private void rollbackFailure(String table) {
        var existing=create("existing");
        jdbc.execute("CREATE TRIGGER it_force_failure BEFORE INSERT ON "+table+" FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='intentional test failure'");
        assertThrows(RuntimeException.class, () -> create("rollback-create"));
        assertThrows(RuntimeException.class, () -> runtime.dispatcher.dispatch(save(existing,"rollback-update",0L,Map.of("title","should rollback"))));
        counts(1); assertEquals("existing",jdbc.queryForObject("SELECT title FROM it_declared_note",String.class));
        assertEquals(0L,jdbc.queryForObject("SELECT version FROM it_declared_note",Long.class));
        jdbc.execute("DROP TRIGGER it_force_failure");
        create("rollback-create");counts(2);
    }
    @Test void unreadableFieldCannotBeUsedAsClientFilter() {
        create("filter"); reject("FIELD_NOT_OPEN", () -> runtime.access.query(new BusinessEntityPageQuery("list","IT","declaredNote",
                List.of(new BusinessFieldFilter("internalMemo",BusinessFieldFilter.Operator.EQ,List.of("private"))),10,null),actor()));
    }
    @Test void invalidPaginationDoesNotProduceUnboundedReads() {
        reject("PAGE_SIZE_INVALID", () -> runtime.access.query(new BusinessEntityPageQuery("list","IT","declaredNote",List.of(),0,null),actor()));
        reject("PAGE_SIZE_INVALID", () -> runtime.access.query(new BusinessEntityPageQuery("list","IT","declaredNote",List.of(),201,null),actor()));
    }

    @Test void genericApiCrudAndThinExtensionShareProductionSafetyAndReceipt() throws Exception {
        runtime.close(); runtime=new Runtime(true, true); jdbc=runtime.jdbc;
        var request=new cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.BusinessModelController.OperationExecuteReqVO();
        request.setIdempotencyKey("api-create"); request.setInput(Map.of("projectRef",99L,"title","mixed"));
        var created=runtime.controller.execute("IT","declaredNote","create",null,request).getData();
        request.setIdempotencyKey("api-custom");request.setInput(Map.of());request.setConcurrencyBasis(0L);
        var result=runtime.controller.execute("IT","declaredNote","uppercase",created.entityRef().entityId(),request).getData();
        assertEquals(1L,result.newConcurrencyBasis());
        assertEquals("MIXED",runtime.controller.data("IT","declaredNote",created.entityRef().entityId(),null).getData().fieldValues().get("title"));
        assertEquals(result,runtime.controller.execute("IT","declaredNote","uppercase",created.entityRef().entityId(),request).getData());
        assertEquals(1,count("it_declared_note"));
        for(String table:List.of("plt_idempotency_record","plt_operation_audit","plt_outbox_event")) assertEquals(2,count(table),table);
        request.setIdempotencyKey("api-denied");request.setConcurrencyBasis(1L);
        reject("ENTITY_SCOPE_DENIED",()->runtime.controller.execute("IT","declaredNote","unsafeMove",created.entityRef().entityId(),request));
        reject("FIELD_NOT_WRITABLE",()->runtime.controller.execute("IT","declaredNote","unsafeField",created.entityRef().entityId(),request));
        request.setConcurrencyBasis(null);
        reject("CONCURRENCY_BASIS_REQUIRED",()->runtime.controller.execute("IT","declaredNote","uppercase",created.entityRef().entityId(),request));
        request.setConcurrencyBasis(0L);
        reject("CONCURRENCY_CONFLICT",()->runtime.controller.execute("IT","declaredNote","uppercase",created.entityRef().entityId(),request));
        request.setConcurrencyBasis(1L);
        jdbc.update("DELETE FROM system_role_menu WHERE role_id=701 AND menu_id=980005");
        reject("ACCESS_DENIED",()->runtime.controller.execute("IT","declaredNote","uppercase",created.entityRef().entityId(),request));
        login(7,READER);
        reject("ACCESS_DENIED",()->runtime.controller.execute("IT","declaredNote","uppercase",created.entityRef().entityId(),request));
        assertEquals(2,count("plt_idempotency_record"));
    }

    /** Exclusive browser fixture: real generic controller/services/SQL, synthetic identity only. */
    @Test @EnabledIfSystemProperty(named="npdms.declared.browser", matches="true")
    void browserUsesGenericApiAndMySql() throws Exception {
        runtime.close();runtime=new Runtime(true,true);jdbc=runtime.jdbc;
        var completed=new java.util.concurrent.CountDownLatch(1);
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",27462),0);
        server.createContext("/", exchange -> {
            login(7,WRITER);
            try {
                Object result;
                String path=exchange.getRequestURI().getPath();
                var params=new java.util.HashMap<String,String>();
                String query=exchange.getRequestURI().getQuery();
                if(query!=null) for(String part:query.split("&")) { String[] pair=part.split("=",2);params.put(pair[0],pair[1]); }
                if(path.equals("/finish")) { completed.countDown();result=Map.of("code",0); }
                else if(path.endsWith("/page")) {
                    var request=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.BusinessModelController.PageQueryReqVO.class);
                    result=runtime.controller.page("IT","declaredNote",request);
                } else if(path.endsWith("/data")) result=runtime.controller.data("IT","declaredNote",Long.valueOf(params.get("id")),null);
                else if(path.contains("/operations/")) {
                    var request=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.BusinessModelController.OperationExecuteReqVO.class);
                    result=runtime.controller.execute("IT","declaredNote",path.substring(path.lastIndexOf('/')+1),params.containsKey("entityId")?Long.valueOf(params.get("entityId")):null,request);
                } else result=runtime.controller.detail("IT","declaredNote");
                byte[] bytes=cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(result).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
            } catch(Exception failure) {
                byte[] bytes=cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(Map.of("code",failure instanceof BusinessContractException contract?contract.getErrorCode():"TEST_FAILURE","msg",failure.getMessage())).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400,bytes.length);exchange.getResponseBody().write(bytes);
            } finally { exchange.close();SecurityContextHolder.clearContext();TenantContextHolder.clear(); }
        });
        server.start();
        try {
            assertTrue(completed.await(8,java.util.concurrent.TimeUnit.MINUTES),"Browser did not finish");
            login(7,WRITER);
            assertEquals("BROWSER SAVED",jdbc.queryForObject("SELECT title FROM it_declared_note",String.class));
            assertNull(jdbc.queryForObject("SELECT amount FROM it_declared_note",BigDecimal.class));
            assertEquals("private memo",jdbc.queryForObject("SELECT internal_memo FROM it_declared_note",String.class));
            assertEquals(1,count("it_declared_note"));
            for(String table:List.of("plt_idempotency_record","plt_operation_audit","plt_outbox_event")) assertEquals(3,count(table),table);
        } finally { server.stop(0); }
    }

    @Test void createReplayUsesReceiptObjectsCurrentOwnershipRatherThanOriginalProject() {
        var originalRequest=create("ownership-replay",Map.of("projectRef",99L,"title","replay"));
        var original=runtime.dispatcher.dispatch(originalRequest);
        runtime.dispatcher.dispatch(save(original,"ownership-move",0L,Map.of("projectRef",101L)));
        doAnswer(call -> {
            var query=call.getArgument(0,ProjectCurrentScopeQuery.class);
            return new ProjectScopeResult(query.anchorProjectId(),1L,query.anchorProjectId()==99L?Set.of(99L):Set.of(),Set.of());
        }).when(runtime.projectApi).resolveCurrent(any());
        reject("ENTITY_SCOPE_DENIED",()->runtime.dispatcher.dispatch(originalRequest));
        assertEquals(2,count("plt_idempotency_record"));
        doAnswer(call -> {
            var query=call.getArgument(0,ProjectCurrentScopeQuery.class);
            return new ProjectScopeResult(query.anchorProjectId(),1L,query.anchorProjectId()==101L?Set.of(101L):Set.of(),Set.of());
        }).when(runtime.projectApi).resolveCurrent(any());
        assertEquals(original,runtime.dispatcher.dispatch(originalRequest));
        assertEquals(1L,jdbc.queryForObject("SELECT version FROM it_declared_note",Long.class));
        for(String table:List.of("plt_idempotency_record","plt_operation_audit","plt_outbox_event")) assertEquals(2,count(table),table);
    }

    @Test void ownerReadCompatibilityRestrictsLegacyListsAndDetailsWithoutGrantingWrites() {
        var visible=create("legacy-visible");
        var hidden=runtime.dispatcher.dispatch(create("legacy-hidden",Map.of("projectRef",101L,"title","hidden")));
        var m=runtime.declaration.descriptor();
        var legacy=new BusinessModelDeclaration(new BusinessModelDescriptor(m.ownerModule(),m.entityType(),m.stableCode(),m.contractVersion(),m.kind(),m.title(),m.authorizationPolicyRef(),m.fields(),m.relations(),m.operations(),m.capabilities(),m.viewCode(),null),DeclaredNoteDO.class,runtime.declaration.mapper(),null);
        try(var context=new GenericApplicationContext()) {
            context.getBeanFactory().registerSingleton("legacyDeclaration",(BusinessModelContributor)()->List.of(legacy));context.refresh();
            var catalog=new BusinessModelRegistry(context.getBeanProvider(BusinessModelContributor.class));
            var persistence=new BusinessEntityPersistenceRegistry(context.getBeanProvider(BusinessModelContributor.class));
            var projects=runtime.context.getBean(ProjectBusinessScopeAccess.class);
            var compatibility=new OwnerProjectReadScopePolicy(Set.of("IT/declaredNote"),"projectRef",catalog,persistence,projects);
            var access=new DefaultBusinessEntityAccess(catalog,persistence,runtime.guard,null,List.of(compatibility),List.of(),new DeclaredBusinessScopeSupport(List.of(projects)));
            doReturn(Set.of(99L)).when(runtime.projectApi).resolveAllCurrent(any());
            doAnswer(call->{var q=call.getArgument(0,ProjectCurrentScopeQuery.class);return new ProjectScopeResult(q.anchorProjectId(),1L,q.anchorProjectId()==99L?Set.of(99L):Set.of(),Set.of());}).when(runtime.projectApi).resolveCurrent(any());
            login(7,READER);
            var slice=access.query(new BusinessEntityPageQuery("list","IT","declaredNote",List.of(),10,null),actor());
            assertEquals(List.of(visible.entityRef().entityId()),slice.members().stream().map(row->row.ref().entityId()).toList());
            assertTrue(access.read(EntityDataRef.current(visible.entityRef()),actor(),"detail").available());
            reject("ENTITY_SCOPE_DENIED",()->access.read(EntityDataRef.current(hidden.entityRef()),actor(),"detail"));
            reject("ACCESS_DENIED",()->create("legacy-cannot-write"));
            doReturn(Set.of()).when(runtime.projectApi).resolveAllCurrent(any());
            assertTrue(access.query(new BusinessEntityPageQuery("list","IT","declaredNote",List.of(),10,null),actor()).members().isEmpty());
            login(8,READER);
            assertTrue(access.query(new BusinessEntityPageQuery("list","IT","declaredNote",List.of(),10,null),actor()).members().isEmpty());
        }
    }

    @Test void parentReadBindingUsesAuthorizedParentRowsForListsAndDetails() {
        var visible=create("parent-visible");
        var hidden=runtime.dispatcher.dispatch(create("parent-hidden",Map.of("projectRef",101L,"title","hidden")));
        jdbc.update("INSERT INTO it_declared_note (id,project_ref,title,version,tenant_id) VALUES (8801001,?,'child-visible',0,7),(8801002,?,'child-hidden',0,7)",visible.entityRef().entityId(),hidden.entityRef().entityId());
        var m=runtime.declaration.descriptor();
        var parent=new BusinessModelDeclaration(new BusinessModelDescriptor(m.ownerModule(),m.entityType(),m.stableCode(),m.contractVersion(),m.kind(),m.title(),m.authorizationPolicyRef(),m.fields(),m.relations(),m.operations(),m.capabilities(),null,null),DeclaredNoteDO.class,runtime.declaration.mapper(),null);
        var child=new BusinessModelDeclaration(new BusinessModelDescriptor("IT","declaredChild","IT_DECLARED_CHILD",1,m.kind(),"Child",m.authorizationPolicyRef(),m.fields(),List.of(),List.of(),List.of(),null,null),DeclaredNoteDO.class,runtime.declaration.mapper(),null);
        try(var context=new GenericApplicationContext()) {
            context.getBeanFactory().registerSingleton("declarations",(BusinessModelContributor)()->List.of(parent,child));context.refresh();
            var catalog=new BusinessModelRegistry(context.getBeanProvider(BusinessModelContributor.class));
            var persistence=new BusinessEntityPersistenceRegistry(context.getBeanProvider(BusinessModelContributor.class));
            var projects=runtime.context.getBean(ProjectBusinessScopeAccess.class);
            var roots=new OwnerProjectReadScopePolicy(Set.of("IT/declaredNote"),"projectRef",catalog,persistence,projects);
            var children=new OwnerParentReadScopePolicy(Set.of("IT/declaredChild"),"projectRef","IT","declaredNote",catalog,persistence,context.getBeanProvider(BusinessEntityAccessPort.class));
            var access=new DefaultBusinessEntityAccess(catalog,persistence,runtime.guard,null,List.of(roots,children),List.of(),new DeclaredBusinessScopeSupport(List.of(projects)));
            context.getBeanFactory().registerSingleton("access",access);
            doReturn(Set.of(99L)).when(runtime.projectApi).resolveAllCurrent(any());
            doAnswer(call->{var q=call.getArgument(0,ProjectCurrentScopeQuery.class);return new ProjectScopeResult(q.anchorProjectId(),1L,q.anchorProjectId()==99L?Set.of(99L):Set.of(),Set.of());}).when(runtime.projectApi).resolveCurrent(any());
            login(7,READER);
            var slice=access.query(new BusinessEntityPageQuery("list","IT","declaredChild",List.of(),10,null),actor());
            assertEquals(List.of(8801001L),slice.members().stream().map(row->row.ref().entityId()).toList());
            assertTrue(access.read(EntityDataRef.current(new EntityRef(7L,"IT","declaredChild",8801001L)),actor(),"detail").available());
            reject("ENTITY_SCOPE_DENIED",()->access.read(EntityDataRef.current(new EntityRef(7L,"IT","declaredChild",8801002L)),actor(),"detail"));
            doReturn(Set.of()).when(runtime.projectApi).resolveAllCurrent(any());
            assertTrue(access.query(new BusinessEntityPageQuery("list","IT","declaredChild",List.of(),10,null),actor()).members().isEmpty());
        }
    }

    @Test void plainDoReplayRegistersItsInlineMappingWithoutPoisoningOtherMappers() throws Exception {
        runtime.close(); runtime=new Runtime(true,0,true); jdbc=runtime.jdbc;
        var request=create("plain-map",Map.of("projectRef",99L,"title","plain"));
        var original=runtime.dispatcher.dispatch(request);
        assertEquals(original,runtime.dispatcher.dispatch(request));
        // This executes the real permission mappers after lazy current-row statement registration.
        assertEquals(1,runtime.access.query(new BusinessEntityPageQuery("list","IT","declaredNote",List.of(),10,null),actor()).members().size());
        assertEquals("plain",runtime.access.read(EntityDataRef.current(original.entityRef()),actor(),"detail").fieldValues().get("title"));
        counts(1);
    }

    @Test void replayCanJoinCallerTransactionForDefaultEmptyAndDifferentialExtensions() throws Exception {
        for (int extension:List.of(0,1,2)) {
            runtime.close(); runtime=new Runtime(true,extension,false); jdbc=runtime.jdbc;
            initializeSchema();login(7,WRITER);
            var request=create("joined-"+extension,Map.of("projectRef",99L,"title","joined"));
            var original=runtime.dispatcher.dispatch(request);
            var transaction=new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(runtime.source));
            var replay=transaction.execute(status->runtime.dispatcher.dispatch(request));
            assertEquals(original,replay);counts(1);
            if(extension==2) {
                var uppercase=new BusinessOperationRequest("uppercase",1,EntityDataRef.current(original.entityRef()),null,null,
                        Map.of(),"joined-uppercase",0L,OperationEntryKind.INDEPENDENT,null);
                var changed=runtime.dispatcher.dispatch(uppercase);
                assertEquals(changed,transaction.execute(status->runtime.dispatcher.dispatch(uppercase)));
                assertEquals("JOINED",runtime.access.read(EntityDataRef.current(changed.entityRef()),actor(),"detail").fieldValues().get("title"));
                assertEquals(1,count("it_declared_note"));
                for(String table:List.of("plt_idempotency_record","plt_operation_audit","plt_outbox_event")) assertEquals(2,count(table));
            }
        }
    }

    @Test void realFailureStillRollsBackAllWorkInJoinedCallerTransaction() {
        var transaction=new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(runtime.source));
        reject("ENTITY_SCOPE_DENIED",()->transaction.execute(status->{
            create("outer-first");
            return runtime.dispatcher.dispatch(create("outer-denied",Map.of("projectRef",100L,"title","denied")));
        }));
        counts(0);
    }

    @cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntityService(ownerModule="IT",entityType="declaredNote")
    static class EmptyDeclaredNoteService extends ExtensibleBusinessApplicationService {
        EmptyDeclaredNoteService(DefaultBusinessApplicationService defaults) { super(defaults); }
    }

    static void initializeExclusiveSchema(Runtime runtime) throws Exception {
        var fixture=new DeclaredBusinessRuntimePersistenceTest();fixture.runtime=runtime;fixture.jdbc=runtime.jdbc;
        fixture.initializeSchema();
    }

    static class Runtime implements AutoCloseable {
        final GenericApplicationContext context=new GenericApplicationContext();
        final JdbcTemplate jdbc;
        final HikariDataSource source;
        final ProjectScopeApi projectApi=mock(ProjectScopeApi.class);
        final BusinessModelDeclaration declaration;
        final BusinessOperationDispatcher dispatcher;
        final DefaultBusinessEntityAccess access;
        final PermissionBusinessAccessGuard guard;
        final org.mybatis.spring.SqlSessionTemplate sessions;
        final cn.iocoder.yudao.module.pms.platform.service.entity.EntityProviderRegistry entityProviders;
        final EntityExtensionApi extensions;
        final EntityFormApi forms;
        final cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.BusinessModelController controller;
        Runtime() throws Exception { this(true, false); }
        Runtime(boolean optimisticLock) throws Exception { this(optimisticLock, false); }
        Runtime(boolean optimisticLock, boolean extension) throws Exception { this(optimisticLock,extension?2:0,false); }
        Runtime(boolean optimisticLock,int extension,boolean plain) throws Exception {this(optimisticLock,extension,plain,false);}
        Runtime(boolean optimisticLock,int extension,boolean plain,boolean capabilities) throws Exception {this(optimisticLock,extension,plain,capabilities,null,false);}
        Runtime(boolean optimisticLock, int extension, boolean plain, boolean capabilities,String alias,boolean delivery) throws Exception {
            this(optimisticLock,extension,plain,capabilities,alias,delivery,false);
        }
        Runtime(boolean optimisticLock,int extension,boolean plain,boolean capabilities,String alias,boolean delivery,boolean secondDeliveryModel) throws Exception {
            String url=System.getProperty("npdms.declared.jdbcUrl","jdbc:mysql://127.0.0.1:27461/npdms_declared_framework?useSSL=false&allowPublicKeyRetrieval=true");
            if(!url.startsWith("jdbc:mysql://127.0.0.1:27461/npdms_declared_framework?")) throw new IllegalStateException("Exclusive test database required");
            source=new HikariDataSource();source.setJdbcUrl(url);source.setUsername("root");source.setPassword("");
            source.setMaximumPoolSize(4);source.setMinimumIdle(0);source.setConnectionTimeout(5000);
            source.setPoolName("it-declared-"+UUID.randomUUID());jdbc=new JdbcTemplate(source);
            var transactionManager=new DataSourceTransactionManager(source);
            var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
            var global=new GlobalConfig();global.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO));global.setMetaObjectHandler(new DefaultDBFieldHandler());
            com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(config,global);
            for(var mapper:List.of(DeclaredNoteMapper.class,PlainDeclaredNoteMapper.class,PlatformIdempotencyRecordMapper.class,PlatformOperationAuditMapper.class,PlatformOutboxEventMapper.class,
                    RoleMapper.class,MenuMapper.class,RoleMenuMapper.class,UserRoleMapper.class,
                    cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.EntityCapabilityMapper.class,
                    cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateMapper.class,
                    cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper.class,
                    cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.PlatformDynamicFormInstanceMapper.class)) config.addMapper(mapper);
            if(secondDeliveryModel) config.addMapper(DefaultDeliverySecondMapper.class);
            var interceptor=new MybatisPlusInterceptor();interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
            if (optimisticLock) interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(config);factory.setGlobalConfig(global);factory.setPlugins(interceptor);
            factory.setMapperLocations(new ClassPathResource("mapper/command/PlatformIdempotencyRecordMapper.xml"),
                    new ClassPathResource("mapper/entity/EntityCapabilityMapper.xml"),new ClassPathResource("mapper/dynamicform/DynamicFormTemplateMapper.xml"),
                    new ClassPathResource("mapper/dynamicform/DynamicFormTemplateRevisionMapper.xml"));
            sessions=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
            var roles=new RoleServiceImpl();ReflectionTestUtils.setField(roles,"roleMapper",sessions.getMapper(RoleMapper.class));
            var menus=new MenuServiceImpl();ReflectionTestUtils.setField(menus,"menuMapper",sessions.getMapper(MenuMapper.class));
            var permissions=new PermissionServiceImpl();ReflectionTestUtils.setField(permissions,"roleService",roles);ReflectionTestUtils.setField(permissions,"menuService",menus);
            ReflectionTestUtils.setField(permissions,"userRoleMapper",sessions.getMapper(UserRoleMapper.class));ReflectionTestUtils.setField(permissions,"roleMenuMapper",sessions.getMapper(RoleMenuMapper.class));
            var api=new PermissionApiImpl();ReflectionTestUtils.setField(api,"permissionService",permissions);
            context.getBeanFactory().registerSingleton("roles",roles);context.getBeanFactory().registerSingleton("menus",menus);context.getBeanFactory().registerSingleton("permissions",permissions);
            BusinessModelContributor contributor=new DeclaredNoteDeclaration(sessions.getMapper(DeclaredNoteMapper.class));
            if(plain || capabilities || alias!=null || delivery) {
                var original=contributor.declarations().getFirst().descriptor();
                var descriptor=new BusinessModelDescriptor(original.ownerModule(),original.entityType(),original.stableCode(),original.contractVersion(),original.kind(),original.title(),original.authorizationPolicyRef(),
                        original.fields().stream().filter(field->!plain || !field.code().equals("tags")).toList(),original.relations(),original.operations(),
                        capabilities?List.of(new BusinessCapabilityBinding(BusinessCapabilityType.DYNAMIC_FORM,null,true)):delivery?List.of(new BusinessCapabilityBinding(BusinessCapabilityType.DELIVERY,null,true)):original.capabilities(),original.viewCode(),original.scopeBinding());
                contributor=()->List.of(new BusinessModelDeclaration(descriptor,plain?PlainDeclaredNoteDO.class:DeclaredNoteDO.class,
                        plain?sessions.getMapper(PlainDeclaredNoteMapper.class):sessions.getMapper(DeclaredNoteMapper.class),null,alias));
            }
            context.getBeanFactory().registerSingleton("declaration",contributor);
            if(secondDeliveryModel) context.getBeanFactory().registerSingleton("secondDeliveryDeclaration",
                    new DefaultDeliverySecondDeclaration(sessions.getMapper(DefaultDeliverySecondMapper.class)));
            context.getBeanFactory().registerSingleton("declaredIdentities",new BusinessEntityIdentityResolver(context));
            context.refresh();var spring=new SpringUtil();spring.setApplicationContext(context);spring.postProcessBeanFactory(context.getBeanFactory());
            declaration=contributor.declarations().getFirst();var contributors=context.getBeanProvider(BusinessModelContributor.class);
            var catalog=new BusinessModelRegistry(contributors);var persistence=new BusinessEntityPersistenceRegistry(contributors);
            guard=new PermissionBusinessAccessGuard(api);
            when(projectApi.resolveAllCurrent(any())).thenReturn(Set.of(99L,101L));
            when(projectApi.resolveCurrent(any())).thenAnswer(call -> { var query=call.getArgument(0,ProjectCurrentScopeQuery.class);
                return new ProjectScopeResult(query.anchorProjectId(),1L,Set.of(99L,101L).contains(query.anchorProjectId())?Set.of(query.anchorProjectId()):Set.of(),Set.of()); });
            when(projectApi.lockAndRevalidate(any())).thenAnswer(call -> { var query=call.getArgument(0,ProjectScopeRevalidationQuery.class);
                return new ProjectScopeResult(query.anchorProjectId(),1L,Set.of(99L,101L).contains(query.anchorProjectId())?Set.of(query.anchorProjectId()):Set.of(),Set.of()); });
            context.getBeanFactory().registerSingleton("projectScopePolicy",new ProjectBusinessScopeAccess(projectApi));
            var configuration=new BusinessModelAccessConfiguration();
            var scopes=configuration.declaredBusinessScopes(context.getBeanProvider(BusinessScopeAccess.class));
            var ledger=transactionProxy(new PlatformOperationExecutionStore(sessions.getMapper(PlatformIdempotencyRecordMapper.class)),transactionManager);
            var audit=new OperationAuditApiImpl(sessions.getMapper(PlatformOperationAuditMapper.class));
            var outbox=transactionProxy(new PlatformTransactionalOutboxWriter(sessions.getMapper(PlatformOutboxEventMapper.class),context),transactionManager);
            if(capabilities) {
                context.getBeanFactory().registerSingleton("audit",audit);
                var defaults=new cn.iocoder.yudao.module.pms.platform.support.capability.DeclaredBusinessCapabilityAdapterFactory(persistence,
                        context.getBeanProvider(BusinessEntityAccessPort.class),context.getBeanProvider(BusinessOperationDispatcher.class),new TenantCallerContext(),
                        context.getBeanProvider(BusinessEntityIdentityResolver.class));
                context.getBeanFactory().registerSingleton("declaredCapabilities",defaults);
                var inherited=new cn.iocoder.yudao.module.pms.platform.support.revision.InheritedRevisionAdapterFactory(persistence,
                        context.getBeanProvider(BusinessAccessGuard.class),context.getBeanProvider(EntityExtensionApi.class),context.getBeanProvider(cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi.class));
                entityProviders=new cn.iocoder.yudao.module.pms.platform.service.entity.EntityProviderRegistry(context.getBeanProvider(EntityFieldProvider.class),context.getBeanProvider(EntityVersionProvider.class),
                        inherited,context.getBeanProvider(BusinessEntityIdentityResolver.class),context.getBeanProvider(cn.iocoder.yudao.module.pms.platform.support.capability.DeclaredBusinessCapabilityAdapterFactory.class));
                var mapper=sessions.getMapper(cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.EntityCapabilityMapper.class);
                extensions=transactionProxy(new cn.iocoder.yudao.module.pms.platform.service.entity.EntityExtensionService(mapper,entityProviders,api,audit),transactionManager);
                context.getBeanFactory().registerSingleton("extensions",extensions);
                var policies=new cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormBusinessObjectPolicyProviderRegistry(List.of(),context.getBeanProvider(cn.iocoder.yudao.module.pms.platform.support.capability.DeclaredBusinessCapabilityAdapterFactory.class));
                var schema=new cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormSchemaService();
                var business=new cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormBusinessInstanceService(policies,
                        sessions.getMapper(cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateMapper.class),
                        sessions.getMapper(cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper.class),
                        sessions.getMapper(cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.PlatformDynamicFormInstanceMapper.class),schema,null);
                var businessApi=transactionProxy(new cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormBusinessInstanceApiImpl(business),transactionManager);
                forms=transactionProxy(new cn.iocoder.yudao.module.pms.platform.service.entity.EntityFormService(mapper,entityProviders,extensions,
                        sessions.getMapper(cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper.class),schema,businessApi,audit),transactionManager);
            } else {entityProviders=null;extensions=null;forms=null;}
            var service=configuration.defaultBusinessApplicationService(new TenantCallerContext(),catalog,persistence,guard,ledger,
                    new OutboxBusinessEventPort(outbox),audit,context.getBeanProvider(org.springframework.transaction.support.TransactionOperations.class),
                    transactionManager,context.getBeanProvider(EntityExtensionApi.class),scopes);
            if(extension==1) context.getBeanFactory().registerSingleton("noteService",new EmptyDeclaredNoteService(service));
            if(extension==2) context.getBeanFactory().registerSingleton("noteService",new DeclaredNoteService(service));
            dispatcher=configuration.businessOperationDispatcher(persistence,service,context.<AbstractBusinessApplicationService<?>>getBeanProvider(org.springframework.core.ResolvableType.forClass(AbstractBusinessApplicationService.class)));
            access=(DefaultBusinessEntityAccess)configuration.businessEntityAccessPort(catalog,persistence,guard,context.getBeanProvider(EntityExtensionApi.class),
                    context.getBeanProvider(BusinessEntityScopePolicy.class),context.getBeanProvider(BusinessEntityContentReader.class),scopes);
            context.getBeanFactory().registerSingleton("businessAccess",access);
            context.getBeanFactory().registerSingleton("businessOperations",dispatcher);
            controller=new cn.iocoder.yudao.module.pms.platform.controller.admin.businessmodel.BusinessModelController(catalog,guard,access,dispatcher,context.getBeanProvider(cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ExecutionBackendCapability.class));
        }
        @SuppressWarnings("unchecked") private static <T> T transactionProxy(T target,DataSourceTransactionManager manager) {
            var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
            return (T)proxy.getProxy();
        }
        @Override public void close() { context.close();source.close(); }
    }
}
