package cn.iocoder.yudao.module.pms.project.service.projecttree;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.project.service.projecttree.command.MoveProjectSubtreeCommand;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectTreeMoveConcurrencyMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectTreeMoveConcurrencyMySqlTest {
    private static final String KEY_PREFIX = "it-tree-move-";

    @Resource ProjectTreeProjectionService service;
    @Resource ProjectTreeScopeService scopeService;
    @Resource JdbcTemplate jdbcTemplate;
    @Resource TransactionTemplate transactionTemplate;
    private long baseId;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
        String database = environment.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = environment.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(environment, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(environment, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.project");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        baseId = 881_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 10;
        insertProject(baseId, null, baseId, "/", 0, 0);
        insertProject(baseId + 1, baseId, baseId, "/" + baseId + "/", 1, 1);
        insertProject(baseId + 2, baseId, baseId, "/" + baseId + "/", 1, 2);
        insertProject(baseId + 3, baseId, baseId, "/" + baseId + "/", 1, 3);
        jdbcTemplate.update("INSERT INTO proj_project_tree_version "
                        + "(id,root_project_id,tree_version,status,change_batch_id,node_count,path_count,version,tenant_id) "
                        + "VALUES (?,?,7,'ACTIVE',?,4,7,0,0)", baseId, baseId, KEY_PREFIX + baseId);
        long pathId = baseId;
        insertPath(pathId++, baseId, baseId, 0);
        for (long childId = baseId + 1; childId <= baseId + 3; childId++) {
            insertPath(pathId++, baseId, childId, 1);
            insertPath(pathId++, childId, childId, 0);
        }
        for (long projectId = baseId; projectId <= baseId + 3; projectId++) {
            jdbcTemplate.update("INSERT INTO proj_project_member_assignment "
                            + "(id,project_id,user_id,member_role,status,version,tenant_id) "
                            + "VALUES (?,?,?,'SERVICE_MANAGER_L1','ACTIVE',0,0)",
                    projectId, projectId, 9_900_006L);
        }
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update("DELETE FROM plt_outbox_event WHERE aggregate_key = ?", String.valueOf(baseId + 1));
            jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE aggregate_key = ?", String.valueOf(baseId + 1));
            jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE idempotency_key LIKE ?",
                    KEY_PREFIX + baseId + "%");
            jdbcTemplate.update("DELETE FROM proj_project_tree_change WHERE project_id = ?", baseId + 1);
            jdbcTemplate.update("DELETE FROM proj_project_member_assignment WHERE project_id BETWEEN ? AND ?",
                    baseId, baseId + 3);
            jdbcTemplate.update("DELETE FROM proj_project_tree_path WHERE root_project_id = ?", baseId);
            jdbcTemplate.update("DELETE FROM proj_project_tree_version WHERE root_project_id = ?", baseId);
            jdbcTemplate.update("DELETE FROM proj_project WHERE id BETWEEN ? AND ?", baseId, baseId + 3);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void concurrentMovesFromSameVersionHaveOneSuccessFact() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> moveAfter(start, baseId + 2, "a")),
                    executor.submit(() -> moveAfter(start, baseId + 3, "b")));
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) outcomes.add(future.get());

            assertEquals(1, outcomes.stream().filter(
                    ProjectTreeProjectionService.MoveProjectSubtreeResult.class::isInstance).count());
            Throwable failure = (Throwable) outcomes.stream().filter(Throwable.class::isInstance)
                    .findFirst().orElseThrow();
            assertInstanceOf(ServiceException.class, failure, () -> failureChain(failure));
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM proj_project_tree_change WHERE project_id = ?", Long.class, baseId + 1));
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM plt_outbox_event WHERE aggregate_key = ? AND event_type = 'ProjectTreeChanged'",
                    Long.class, String.valueOf(baseId + 1)));
        }
    }

    @Test
    void lockedScopeSerializesTreePublishAndExposesNewVersion() throws Exception {
        CountDownLatch scopeLocked = new CountDownLatch(1);
        CountDownLatch releaseScope = new CountDownLatch(1);
        CountDownLatch moveStarted = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Long> lockedVersion = executor.submit(() -> {
                TenantContextHolder.setTenantId(0L);
                try {
                    return transactionTemplate.execute(status -> {
                        var scope = scopeService.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                                0L, 9_900_006L, baseId + 1, "PROJECT_MANAGE", 7L));
                        scopeLocked.countDown();
                        await(releaseScope);
                        return scope.treeVersion();
                    });
                } finally {
                    TenantContextHolder.clear();
                }
            });
            if (!scopeLocked.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("范围版本锁未建立");
            }
            Future<ProjectTreeProjectionService.MoveProjectSubtreeResult> publish = executor.submit(() -> {
                TenantContextHolder.setTenantId(0L);
                moveStarted.countDown();
                try {
                    return service.move(new MoveProjectSubtreeCommand(baseId + 1, baseId + 2, 7L,
                                    "范围锁并发发布", KEY_PREFIX + baseId + "scope", "a".repeat(64)),
                            new ProjectTreeProjectionService.Actor(0L, 9_900_006L,
                                    KEY_PREFIX + baseId + "scope"));
                } finally {
                    TenantContextHolder.clear();
                }
            });
            if (!moveStarted.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("树发布事务未启动");
            }
            assertThrows(TimeoutException.class, () -> publish.get(500, TimeUnit.MILLISECONDS));

            releaseScope.countDown();
            assertEquals(7L, lockedVersion.get(10, TimeUnit.SECONDS));
            assertEquals(8L, publish.get(10, TimeUnit.SECONDS).treeVersion());

            TenantContextHolder.setTenantId(0L);
            Long currentVersion = transactionTemplate.execute(status -> scopeService.lockAndRevalidate(
                    new ProjectScopeRevalidationQuery(
                            0L, 9_900_006L, baseId + 1, "PROJECT_MANAGE", 7L)).treeVersion());
            assertEquals(8L, currentVersion);
        } finally {
            releaseScope.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待并发事务超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待并发事务被中断", exception);
        }
    }

    private Object moveAfter(CountDownLatch start, long targetParentId, String suffix) {
        try {
            start.await();
            TenantContextHolder.setTenantId(0L);
            return service.move(new MoveProjectSubtreeCommand(baseId + 1, targetParentId, 7L,
                            "并发移动", KEY_PREFIX + baseId + suffix, suffix.repeat(64)),
                    new ProjectTreeProjectionService.Actor(0L, 9_900_006L,
                            KEY_PREFIX + baseId + suffix));
        } catch (Throwable failure) {
            return failure;
        } finally {
            TenantContextHolder.clear();
        }
    }

    private static String failureChain(Throwable failure) {
        StringBuilder summary = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (!summary.isEmpty()) {
                summary.append(" <- ");
            }
            summary.append(current.getClass().getSimpleName()).append(": ").append(current.getMessage());
        }
        return summary.toString();
    }

    private void insertProject(long id, Long parentId, long rootId, String path, int depth, int sequence) {
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,parent_id,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,version,tenant_id) VALUES (?,?,?,?,?,?,?,?,?,?, 'S0',0,0)",
                id, KEY_PREFIX + id, rootId, sequence, KEY_PREFIX + id, parentId, rootId, path, depth, sequence);
    }

    private void insertPath(long id, long ancestorId, long descendantId, int distance) {
        jdbcTemplate.update("INSERT INTO proj_project_tree_path "
                        + "(id,tree_version,root_project_id,ancestor_project_id,descendant_project_id,distance,version,tenant_id) "
                        + "VALUES (?,7,?,?,?,?,0,0)", id, baseId, ancestorId, descendantId, distance);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual",
            "cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class, PlatformCommandExecutionApiImpl.class,
            ProjectTreeProjectionService.class, ProjectTreeMetrics.class, ProjectTreeScopeService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
        @Bean AuthorizationGrantApi authorizationGrantApi() {
            return org.mockito.Mockito.mock(AuthorizationGrantApi.class);
        }
    }
}
