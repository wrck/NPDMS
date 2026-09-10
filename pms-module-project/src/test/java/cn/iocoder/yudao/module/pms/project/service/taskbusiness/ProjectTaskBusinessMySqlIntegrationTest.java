package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOperationAuditDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOperationAuditMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformIdempotencyRecordMapper;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.ProjectTaskBusinessLinkMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query.TaskBusinessLinksQuery;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService.*;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskBusinessCompletionEvaluator;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskWorkbenchActor;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PM-03 / PM-11, F-PROJ-007: relationship foundation integration, not Owner-domain acceptance.
 * Real MySQL, production MyBatis XML, TaskBusinessAccess, Service, completion evaluator and
 * PlatformCommandExecutionApi transactions/audits. Only PermissionApi and ProjectScopeApi are
 * authorization stubs (one fixture actor/project); this does NOT validate the real IAM/scope engine.
 * BusinessViewQueryApi is unused in these command tests. TestSourceOwner is a test-only Owner,
 * reading/locking dedicated pms_eng_site_survey fixture rows, NOT the PRE production provider.
 * No production bean, schema or migration is replaced. No default connection to npdms/npdms_test.
 */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectTaskBusinessMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@Execution(ExecutionMode.SAME_THREAD)
class ProjectTaskBusinessMySqlIntegrationTest {
    private static final String DATABASE = "npdms_template_core_20260910";
    @Resource ProjectTaskBusinessService service;
    @Resource ProjectTaskBusinessLinkMapper links;
    @Resource ProjectTaskExecutionContractMapper contracts;
    @Resource TaskBusinessCompletionEvaluator evaluator;
    @Resource TaskBusinessObjectProvider sourceOwner;
    @Resource JdbcTemplate jdbc;
    @Resource DataSource dataSource;
    @Resource PermissionApi permissions;
    @Resource ProjectScopeApi scopes;
    @Resource OwnerProbe probe;
    @Resource AuditFault auditFault;

    private long projectId, taskId, contractId, ownerId, actorId;
    private String prefix;
    private boolean committedFixtures;
    private final List<Long> ownerIds = new ArrayList<>();
    private final List<Long> projectIds = new ArrayList<>();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        Map<String, String> env = System.getenv();
        if (!DATABASE.equals(required(env, "NPDMS_DB_NAME")))
            throw new IllegalStateException("Only the dedicated template-core database is allowed");
        String port = env.getOrDefault("NPDMS_DB_PORT", env.get("NPDMS_MYSQL_PORT"));
        if (!"23316".equals(port)) throw new IllegalStateException("Only isolated MySQL port 23316 is allowed");
        properties.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + DATABASE
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        properties.add("spring.datasource.username", () -> required(env, "NPDMS_DB_USER"));
        properties.add("spring.datasource.password", () -> required(env, "NPDMS_DB_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        properties.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        properties.add("spring.datasource.druid.max-active", () -> "8");
        properties.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.project");
        properties.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
        properties.add("mybatis-plus.configuration.local-cache-scope", () -> "STATEMENT");
    }

    @BeforeEach
    void fixture() {
        assertEquals(DATABASE, jdbc.queryForObject("SELECT DATABASE()", String.class));
        TenantContextHolder.setTenantId(0L);
        long base = 986_000_000_000_000L + Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 10_000_000_000L) * 100;
        projectId = base; taskId = base + 1; contractId = base + 2; ownerId = base + 3; actorId = base + 4;
        prefix = "tbmysql-" + UUID.randomUUID();
        committedFixtures = false;
        ownerIds.clear(); projectIds.clear(); probe.reset(); auditFault.enabled = false;
        reset(permissions, scopes);
        when(permissions.hasAnyPermissions(any(), any())).thenAnswer(call -> Objects.equals(actorId, call.getArgument(0)));
        when(scopes.resolveCurrent(any())).thenAnswer(call -> {
            cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery query = call.getArgument(0);
            return new ProjectScopeResult(projectId, 1L,
                    query.tenantId() == 0L && Objects.equals(query.subjectUserId(), actorId)
                            ? Set.of(projectId) : Set.of(), Set.of());
        });
        probe.actorId = actorId;
        insertProject(projectId);
        long revision = jdbc.queryForObject("SELECT id FROM proj_task_state_machine_revision "
                + "WHERE tenant_id=0 AND status='PUBLISHED' ORDER BY revision_no DESC LIMIT 1", Long.class);
        jdbc.update("INSERT INTO proj_project_task (id,project_id,task_code,name,root_task_id,tree_depth,"
                        + "state_machine_revision_id,stage_code,sort_order,status,version,tenant_id,creator,updater) "
                        + "VALUES (?,?,?,?,?,0,?,'S1',0,'IN_PROGRESS',0,0,?,?)",
                taskId, projectId, prefix, "Task business MySQL fixture", taskId, revision, prefix, prefix);
        jdbc.update("INSERT INTO proj_project_task_execution_contract "
                        + "(id,project_task_id,work_binding_type_code,target_context_code,target_object_type,component_key,"
                        + "binding_parameter_snapshot,permission_policy_ref,completion_rule_type_code,completion_rule_snapshot,"
                        + "source_definition_version,contract_version,effective_from,version,tenant_id,creator,updater) "
                        + "VALUES (?,?,'BUSINESS_COMPONENT','PRE','SiteSurvey','survey-list',?,"
                        + "'PROJECT_TASK_NATIVE_DEFAULT','BUSINESS_FACT',?,1,1,?,0,0,?,?)",
                contractId, taskId,
                "{\"businessViewRevisionId\":40,\"instanceResolutionStrategy\":\"REFERENCE_EXISTING\"}",
                "{\"factCode\":\"COMPLETED\",\"quantifier\":\"ALL\"}", LocalDateTime.now(), prefix, prefix);
        jdbc.update("INSERT INTO proj_project_member_assignment "
                        + "(id,project_id,user_id,member_role,status,version,tenant_id,creator,updater) "
                        + "VALUES (?,?,?,'PROJECT_MANAGER','ACTIVE',0,0,?,?)", base + 5, projectId, actorId, prefix, prefix);
        insertOwner(ownerId, 0L, projectId);
    }

    @AfterEach
    void cleanup() {
        probe.release.countDown();
        auditFault.enabled = false;
        try {
            if (committedFixtures) {
                // Only rows owned by the unique fixture actor/task and exact created IDs; no broad deletes.
                deleteIds("proj_task_business_link", jdbc.queryForList(
                        "SELECT id FROM proj_task_business_link WHERE tenant_id=0 AND task_id=?", Long.class, taskId));
                deleteIds("plt_operation_audit", jdbc.queryForList(
                        "SELECT id FROM plt_operation_audit WHERE actor_id=?", Long.class, actorId));
                deleteIds("plt_idempotency_record", jdbc.queryForList(
                        "SELECT id FROM plt_idempotency_record WHERE actor_id=?", Long.class, actorId));
                deleteIds("pms_eng_site_survey", ownerIds);
                jdbc.update("DELETE FROM proj_project_task_execution_contract WHERE id=? AND creator=?", contractId, prefix);
                jdbc.update("DELETE FROM proj_project_member_assignment WHERE id=? AND creator=?", projectId + 5, prefix);
                deleteSelfReferencingFixtureTask();
                deleteIds("proj_project", projectIds);
                assertEquals(0L, linkCount());
                assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record WHERE actor_id=?", Long.class, actorId));
            }
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void sameKeyReplayReturnsOriginalResultWithoutDuplicateRowsOrAudit() {
        var command = link(ownerId, 0, "replay");
        var first = service.link(command, 0L, actorId, prefix);
        assertEquals(first, service.link(command, 0L, actorId, prefix));
        assertEquals(1L, linkCount()); assertEquals(1, taskVersion());
        assertEquals(1L, successCount()); assertEquals(1L, reservationCount());
        assertEquals(1, probe.locks.get());
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status FROM plt_idempotency_record WHERE actor_id=?", String.class, actorId));
        assertEquals(first.linkId().toString(), jdbc.queryForObject(
                "SELECT resource_key FROM plt_idempotency_record WHERE actor_id=?", String.class, actorId));
    }

    @Test
    void sameKeyDifferentPayloadConflictsAndPreservesFirstResult() {
        var original = link(ownerId, 0, "conflict");
        var result = service.link(original, 0L, actorId, prefix);
        var conflict = assertThrows(ServiceException.class, () -> service.link(
                new LinkCommand(taskId, Long.toString(ownerId + 1), 0, 1, original.idempotencyKey()), 0L, actorId, prefix));
        assertEquals(PMS_IDEMPOTENCY_KEY_CONFLICT.getCode(), conflict.getCode());
        assertEquals(result, service.link(original, 0L, actorId, prefix));
        assertEquals(1L, linkCount()); assertEquals(1L, successCount()); assertEquals(1L, rejectedCount());
        assertEquals(1L, reservationCount()); assertEquals(1, taskVersion());
    }

    @Test
    void unlinkKeepsOriginalIntervalAndRelinkAppendsNewHistory() {
        var first = service.link(link(ownerId, 0, "link"), 0L, actorId, prefix);
        Map<String, Object> original = jdbc.queryForMap("SELECT * FROM proj_task_business_link WHERE id=?", first.linkId());
        var unlink = new UnlinkCommand(taskId, first.linkId(), 1, 1, prefix + "-unlink");
        var closed = service.unlink(unlink, 0L, actorId, prefix);
        assertFalse(closed.active()); assertEquals(closed, service.unlink(unlink, 0L, actorId, prefix));
        assertTrue(links.selectActive(query(0L, projectId)).isEmpty());
        var second = service.link(link(ownerId, 2, "relink"), 0L, actorId, prefix);
        assertNotEquals(first.linkId(), second.linkId());
        Map<String, Object> history = jdbc.queryForMap("SELECT * FROM proj_task_business_link WHERE id=?", first.linkId());
        for (String field : List.of("linked_at", "linked_by", "fact_version", "execution_contract_id", "contract_version", "creator", "create_time"))
            assertEquals(original.get(field), history.get(field), field);
        assertNotNull(history.get("unlinked_at")); assertEquals(actorId, ((Number) history.get("unlinked_by")).longValue());
        assertNull(history.get("active_marker")); assertEquals(1, ((Number) history.get("version")).intValue());
        assertEquals(2L, linkCount()); assertEquals(3, taskVersion()); assertEquals(3L, successCount());
        assertEquals(List.of(second.linkId()), links.selectActive(query(0L, projectId)).stream().map(row -> row.getId()).toList());
        assertEquals(0, ownerVersion());
    }

    @Test
    void crossTenantTaskAndXmlScopeRejectWithoutLeakingLinks() {
        service.link(link(ownerId, 0, "local"), 0L, actorId, prefix);
        TenantContextHolder.setTenantId(1L);
        try {
            var error = assertThrows(ServiceException.class,
                    () -> service.link(link(ownerId, 1, "foreign"), 1L, actorId, prefix));
            assertEquals(PROJECT_TASK_SCOPE_FORBIDDEN.getCode(), error.getCode());
            assertTrue(links.selectActive(query(1L, projectId)).isEmpty());
            assertNull(links.selectCurrentContract(query(1L, projectId)));
        } finally { TenantContextHolder.setTenantId(0L); }
        assertTrue(links.selectActive(query(0L, projectId + 99)).isEmpty());
        assertNull(links.selectCurrentContract(query(0L, projectId + 99)));
        assertEquals(1L, linkCount()); assertEquals(1L, reservationCount());
    }

    @Test
    void crossProjectOwnerReferenceIsRejectedAndReservationRollsBack() {
        insertProject(projectId + 20);
        insertOwner(ownerId + 20, 0L, projectId + 20);
        commitFixtures();
        assertThrows(SecurityException.class, () -> service.link(link(ownerId + 20, 0, "foreign-project"), 0L, actorId, prefix));
        assertCleanFailure(); assertEquals(1L, rejectedCount());
    }

    @Test
    void crossTenantOwnerReferenceIsRejectedAndReservationRollsBack() {
        insertOwner(ownerId + 20, 1L, projectId);
        commitFixtures();
        assertThrows(SecurityException.class, () -> service.link(link(ownerId + 20, 0, "foreign-tenant"), 0L, actorId, prefix));
        assertCleanFailure(); assertEquals(1L, rejectedCount());
    }

    @Test
    void ownerVersionChangedByAnotherConnectionBetweenInspectAndLockRejects() throws Exception {
        commitFixtures();
        try (var executor = Executors.newSingleThreadExecutor()) {
            probe.afterInspect = () -> {
                Future<Integer> changed = executor.submit(() -> jdbc.update(
                        "UPDATE pms_eng_site_survey SET version=version+1 WHERE id=? AND creator=?", ownerId, prefix));
                assertEquals(1, get(changed));
            };
            var failure = assertThrows(ServiceException.class,
                    () -> service.link(link(ownerId, 0, "owner-version"), 0L, actorId, prefix));
            assertEquals("TASK_BUSINESS_FACT_VERSION_CONFLICT", failure.getMessage());
        }
        assertCleanFailure(); assertEquals(1, ownerVersion()); assertEquals(1L, rejectedCount());
    }

    @Test
    void ownerFailureRollsBackReservationAndDoesNotWriteRelationship() {
        commitFixtures(); probe.failAfterLock = true;
        assertEquals("TEST_OWNER_FAILURE", assertThrows(IllegalStateException.class,
                () -> service.link(link(ownerId, 0, "owner-failure"), 0L, actorId, prefix)).getMessage());
        assertCleanFailure(); assertEquals(0, ownerVersion()); assertEquals(1L, rejectedCount());
        probe.failAfterLock = false;
        assertTrue(service.link(link(ownerId, 0, "owner-failure"), 0L, actorId, prefix).active());
        assertEquals(1L, successCount()); assertEquals(1L, reservationCount());
    }

    @Test
    void auditInsertFailureRollsBackLinkTaskCasSuccessAuditAndReservation() {
        commitFixtures(); auditFault.enabled = true;
        assertEquals("TEST_SUCCESS_AUDIT_FAILURE", assertThrows(IllegalStateException.class,
                () -> service.link(link(ownerId, 0, "audit-failure"), 0L, actorId, prefix)).getMessage());
        assertCleanFailure(); assertEquals(0, ownerVersion()); assertEquals(1L, rejectedCount());
        auditFault.enabled = false;
        assertTrue(service.link(link(ownerId, 0, "audit-failure"), 0L, actorId, prefix).active());
        assertEquals(1L, linkCount()); assertEquals(1L, successCount()); assertEquals(1L, reservationCount());
    }

    @Test
    void emptyBusinessGroupCannotCompleteEvenWhenAllQuantifierWouldBeVacuouslyTrue() {
        String expected = snapshot();
        var contract = contracts.selectById(contractId);
        var command = new TaskActionCommand(taskId, 0, "COMPLETE", "test", contractId, 1,
                null, null, null, null, expected, prefix, "a".repeat(64));
        var result = evaluator.evaluateLocked(command, contract, new TaskWorkbenchActor(0L, actorId, prefix));
        assertFalse(result.satisfied()); assertTrue(result.unmetItems().contains("BUSINESS_LINK_GROUP_EMPTY"));
        assertEquals("IN_PROGRESS", jdbc.queryForObject("SELECT status FROM proj_project_task WHERE id=?", String.class, taskId));
        assertEquals(0, taskVersion()); assertEquals(0L, successCount()); assertEquals(0, probe.locks.get());
    }

    @Test
    void linkAdditionAndRemovalBothInvalidateExpectedAggregateVersion() {
        insertOwner(ownerId + 10, 0L, projectId);
        service.link(link(ownerId, 0, "first"), 0L, actorId, prefix);
        String one = snapshot();
        var second = service.link(link(ownerId + 10, 1, "second"), 0L, actorId, prefix);
        String two = snapshot(); assertNotEquals(one, two);
        assertAggregateConflict(one);
        assertEquals(two, service.lockAndRevalidateLinkedFacts(taskId, 0L, actorId, prefix, two).factVersion());
        service.unlink(new UnlinkCommand(taskId, second.linkId(), 2, 1, prefix + "-unlink"), 0L, actorId, prefix);
        String remaining = snapshot(); assertNotEquals(two, remaining);
        assertAggregateConflict(two);
        assertEquals(remaining, service.lockAndRevalidateLinkedFacts(taskId, 0L, actorId, prefix, remaining).factVersion());
        assertEquals(2L, linkCount()); assertEquals(1, links.selectActive(query(0L, projectId)).size());
    }

    @Test
    void ownerMandatoryPropagationRejectsOutsideTransaction() {
        commitFixtures();
        assertThrows(IllegalTransactionStateException.class,
                () -> sourceOwner.lockAndRevalidate(ownerContext(), Long.toString(ownerId), "v0"));
        assertEquals(0, probe.locks.get()); assertCleanFailure();
    }

    @Test
    void concurrentSameIntentReplaysOnceAndOwnerRowLockContendsOnDistinctMysqlConnections() throws Exception {
        concurrentIntent(true);
    }

    @Test
    void concurrentDifferentKeysForSameObjectCannotCreateTwoActiveRelationships() throws Exception {
        concurrentIntent(false);
    }

    private void concurrentIntent(boolean sameKey) throws Exception {
        commitFixtures(); probe.holdAfterLock = true;
        LinkCommand command = link(ownerId, 0, "concurrent");
        probe.reservationStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<LinkCommandResult> first = executor.submit(() -> asTenant(() -> service.link(command, 0L, actorId, prefix)));
            assertTrue(probe.locked.await(15, TimeUnit.SECONDS), "Owner did not acquire its real row lock");
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                long contender;
                try (var row = statement.executeQuery("SELECT CONNECTION_ID()")) { row.next(); contender = row.getLong(1); }
                assertNotEquals(probe.connectionId.get(), contender);
                connection.setAutoCommit(false);
                try {
                    SQLException conflict = assertThrows(SQLException.class, () -> statement.executeQuery(
                            "SELECT id FROM pms_eng_site_survey WHERE id=" + ownerId + " FOR UPDATE NOWAIT"));
                    assertEquals(3572, conflict.getErrorCode(), "MySQL NOWAIT must report the Owner row is locked");
                } finally { connection.rollback(); }
            }
            // Observe the second reservation inside the real platform transaction, without wrapping
            // access.read in an artificial repeatable-read transaction before the reservation.
            probe.observeReservation = true;
            Future<LinkCommandResult> second = executor.submit(() -> asTenant(() ->
                    service.link(sameKey ? command : link(ownerId, 0, "concurrent-other"), 0L, actorId, prefix)));
            assertTrue(probe.reservationStarted.await(10, TimeUnit.SECONDS));
            assertNotEquals(probe.connectionId.get(), probe.reservationConnectionId.get());
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS),
                    "Second command must wait while first transaction owns its locks");
            probe.release.countDown();
            var result = first.get(20, TimeUnit.SECONDS);
            if (sameKey) assertEquals(result, second.get(20, TimeUnit.SECONDS));
            else {
                ExecutionException conflict = assertThrows(ExecutionException.class, () -> second.get(20, TimeUnit.SECONDS));
                assertInstanceOf(ServiceException.class, conflict.getCause());
                assertEquals(PROJECT_TASK_VERSION_CONFLICT.getCode(), ((ServiceException) conflict.getCause()).getCode());
            }
            assertEquals(1L, linkCount()); assertEquals(1, taskVersion()); assertEquals(1L, successCount());
            assertEquals(1L, reservationCount()); assertEquals(1, probe.locks.get());
            // After commit the same Owner row must be lockable, proving the lock was not left on a detached transaction.
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                try (var row = statement.executeQuery("SELECT id FROM pms_eng_site_survey WHERE id=" + ownerId + " FOR UPDATE NOWAIT")) {
                    assertTrue(row.next()); assertEquals(ownerId, row.getLong(1));
                } finally { connection.rollback(); }
            }
        } finally {
            probe.release.countDown(); executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Workers must stop before fixture cleanup");
            }
        }
    }

    private void insertProject(long id) {
        jdbc.update("INSERT INTO proj_project (id,project_code,code_root_id,project_sequence,project_name,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id,creator,updater) "
                        + "VALUES (?,?,?,0,?,?,'/',0,0,'S0','ACTIVE','S0','UNASSIGNED',0,0,0,0,?,?)",
                id, "TB-" + id, id, prefix, id, prefix, prefix);
        projectIds.add(id);
    }
    private void insertOwner(long id, long tenantId, long project) {
        jdbc.update("INSERT INTO pms_eng_site_survey (id,project_id,code,name,status,version,tenant_id,creator,updater) "
                + "VALUES (?,?,?, ?,1,0,?,?,?)", id, project, "TB-" + id, prefix, tenantId, prefix, prefix);
        ownerIds.add(id);
    }
    private void commitFixtures() {
        TestTransaction.flagForCommit(); TestTransaction.end(); committedFixtures = true;
    }
    private void deleteSelfReferencingFixtureTask() {
        // MySQL RESTRICT blocks deleting even this fixture's self-referencing root row.
        // Keep the exception local to this cleanup connection and one ID, after checking no child references it.
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_task WHERE id<>? "
                + "AND (parent_task_id=? OR root_task_id=?)", Long.class, taskId, taskId, taskId));
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET SESSION FOREIGN_KEY_CHECKS=0");
                try (var delete = connection.prepareStatement("DELETE FROM proj_project_task WHERE id=? AND tenant_id=0 AND creator=?")) {
                    delete.setLong(1, taskId); delete.setString(2, prefix);
                    assertEquals(1, delete.executeUpdate());
                } finally { statement.execute("SET SESSION FOREIGN_KEY_CHECKS=1"); }
            }
            return null;
        });
    }
    private void deleteIds(String table, List<Long> ids) {
        // Table names are private constants from cleanup, never request-controlled.
        for (Long id : ids) jdbc.update("DELETE FROM " + table + " WHERE id=?", id);
    }
    private LinkCommand link(long objectId, int version, String suffix) {
        return new LinkCommand(taskId, Long.toString(objectId), version, 1, prefix + "-" + suffix);
    }
    private TaskBusinessLinksQuery query(long tenant, long project) { return new TaskBusinessLinksQuery(tenant, project, taskId); }
    private TaskBusinessObjectProvider.Context ownerContext() { return new TaskBusinessObjectProvider.Context(0L, actorId, projectId, taskId, prefix); }
    private String snapshot() { return service.inspectLinkedFactsSnapshot(taskId, 0L, actorId, prefix).factVersion(); }
    private void assertAggregateConflict(String version) {
        assertEquals("TASK_BUSINESS_FACT_VERSION_CONFLICT", assertThrows(ServiceException.class,
                () -> service.lockAndRevalidateLinkedFacts(taskId, 0L, actorId, prefix, version)).getMessage());
    }
    private int taskVersion() { return jdbc.queryForObject("SELECT version FROM proj_project_task WHERE id=?", Integer.class, taskId); }
    private int ownerVersion() { return jdbc.queryForObject("SELECT version FROM pms_eng_site_survey WHERE id=?", Integer.class, ownerId); }
    private long linkCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM proj_task_business_link WHERE task_id=?", Long.class, taskId); }
    private long reservationCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record WHERE actor_id=?", Long.class, actorId); }
    private long successCount() { return auditCount("SUCCESS"); }
    private long rejectedCount() { return auditCount("REJECTED"); }
    private long auditCount(String result) { return jdbc.queryForObject(
            "SELECT COUNT(*) FROM plt_operation_audit WHERE actor_id=? AND result_code=?", Long.class, actorId, result); }
    private void assertCleanFailure() {
        assertEquals(0L, linkCount()); assertEquals(0L, reservationCount()); assertEquals(0L, successCount()); assertEquals(0, taskVersion());
    }
    private static <T> T asTenant(Callable<T> action) throws Exception {
        TenantContextHolder.setTenantId(0L);
        try { return action.call(); } finally { TenantContextHolder.clear(); }
    }
    private static <T> T get(Future<T> future) {
        try { return future.get(10, TimeUnit.SECONDS); }
        catch (Exception ex) { throw new AssertionError("Independent Owner update failed", ex); }
    }
    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Required environment variable: " + key);
        return value;
    }

    /** Test fixture Owner: real scoped MySQL reads and row locks, never a mock mapper or production provider. */
    public static class TestSourceOwner implements TaskBusinessObjectProvider {
        private final JdbcTemplate jdbc;
        private final OwnerProbe probe;
        TestSourceOwner(JdbcTemplate jdbc, OwnerProbe probe) { this.jdbc = jdbc; this.probe = probe; }
        public String ownerContext() { return "PRE"; }
        public String objectType() { return "SiteSurvey"; }
        public BusinessObjectFact inspect(Context context, String objectId) {
            BusinessObjectFact fact = read(context, objectId, false);
            Runnable callback = probe.afterInspect;
            probe.afterInspect = null;
            if (callback != null) callback.run();
            return fact;
        }
        @Override
        @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
        public BusinessObjectFact lockAndRevalidate(Context context, String objectId, String expectedVersion) {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            BusinessObjectFact fact = read(context, objectId, true);
            probe.connectionId.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
            probe.locks.incrementAndGet();
            if (!fact.factVersion().equals(expectedVersion)) throw TaskBusinessErrors.failure("FACT_VERSION_CONFLICT");
            if (probe.failAfterLock) throw new IllegalStateException("TEST_OWNER_FAILURE");
            if (probe.holdAfterLock) {
                probe.locked.countDown();
                try { if (!probe.release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Owner lock release timed out"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
            }
            return fact;
        }
        private BusinessObjectFact read(Context context, String objectId, boolean lock) {
            if (!Objects.equals(context.actorId(), probe.actorId)) throw new SecurityException("Test Owner actor forbidden");
            // Fixture-only SQL is deliberately confined here; production relationship queries remain MyBatis XML.
            String sql = "SELECT id,name,version,status FROM pms_eng_site_survey WHERE id=? AND tenant_id=? "
                    + "AND project_id=? AND deleted=b'0'" + (lock ? " FOR UPDATE" : "");
            var facts = jdbc.query(sql, (rs, row) -> new BusinessObjectFact(Long.toString(rs.getLong("id")),
                    rs.getString("name"), "v" + rs.getInt("version"), Set.of("QUERY", "LINK", "UNLINK"),
                    Map.of("COMPLETED", rs.getInt("status") == 1), List.of()),
                    Long.valueOf(objectId), context.tenantId(), context.projectId());
            if (facts.size() != 1) throw new SecurityException("Test Owner object not in tenant/project scope");
            return facts.getFirst();
        }
    }

    static class OwnerProbe {
        volatile Long actorId;
        volatile boolean failAfterLock, holdAfterLock, observeReservation;
        volatile Runnable afterInspect;
        volatile CountDownLatch locked, release, reservationStarted;
        final AtomicInteger locks = new AtomicInteger();
        final AtomicLong connectionId = new AtomicLong();
        final AtomicLong reservationConnectionId = new AtomicLong();
        void reset() {
            failAfterLock = false; holdAfterLock = false; observeReservation = false;
            afterInspect = null; locks.set(0); connectionId.set(0); reservationConnectionId.set(0);
            locked = new CountDownLatch(1); release = new CountDownLatch(1); reservationStarted = new CountDownLatch(1);
        }
    }
    static class AuditFault { volatile boolean enabled; }

    @SpringBootConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan({"cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual",
            "cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench",
            "cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class, OperationAuditApiImpl.class,
            TaskBusinessAccess.class, TaskBusinessProviderRegistry.class, ProjectTaskBusinessService.class,
            TaskBusinessCompletionEvaluator.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PermissionApi permissionApi() { return mock(PermissionApi.class); }
        @Bean ProjectScopeApi projectScopeApi() { return mock(ProjectScopeApi.class); }
        @Bean BusinessViewQueryApi businessViewQueryApi() { return mock(BusinessViewQueryApi.class); }
        @Bean OwnerProbe ownerProbe() { return new OwnerProbe(); }
        @Bean TestSourceOwner sourceOwner(JdbcTemplate jdbc, OwnerProbe probe) { return new TestSourceOwner(jdbc, probe); }
        @Bean AuditFault auditFault() { return new AuditFault(); }
        @Bean TenantLineInnerInterceptor tenantInterceptor(MybatisPlusInterceptor interceptor) {
            var tenant = new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties()));
            interceptor.addInnerInterceptor(tenant);
            return tenant;
        }
        @Bean @Primary
        PlatformIdempotencyRecordMapper observedIdempotencyMapper(
                @Qualifier("platformIdempotencyRecordMapper") PlatformIdempotencyRecordMapper delegate,
                OwnerProbe probe, JdbcTemplate jdbc) {
            return (PlatformIdempotencyRecordMapper) Proxy.newProxyInstance(PlatformIdempotencyRecordMapper.class.getClassLoader(),
                    new Class<?>[]{PlatformIdempotencyRecordMapper.class}, (proxy, method, arguments) -> {
                        try {
                            if (probe.observeReservation && "insertIfAbsent".equals(method.getName())) {
                                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                                probe.reservationConnectionId.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                                probe.reservationStarted.countDown();
                            }
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException ex) { throw ex.getCause(); }
                    });
        }
        @Bean @Primary
        PlatformOperationAuditMapper faultingAuditMapper(
                @Qualifier("platformOperationAuditMapper") PlatformOperationAuditMapper delegate, AuditFault fault) {
            return (PlatformOperationAuditMapper) Proxy.newProxyInstance(PlatformOperationAuditMapper.class.getClassLoader(),
                    new Class<?>[]{PlatformOperationAuditMapper.class}, (proxy, method, arguments) -> {
                        try {
                            Object result = method.invoke(delegate, arguments);
                            if (fault.enabled && "insert".equals(method.getName()) && arguments != null
                                    && arguments[0] instanceof PlatformOperationAuditDO audit && "SUCCESS".equals(audit.getResultCode()))
                                throw new IllegalStateException("TEST_SUCCESS_AUDIT_FAILURE");
                            return result;
                        } catch (InvocationTargetException ex) { throw ex.getCause(); }
                    });
        }
    }
}
