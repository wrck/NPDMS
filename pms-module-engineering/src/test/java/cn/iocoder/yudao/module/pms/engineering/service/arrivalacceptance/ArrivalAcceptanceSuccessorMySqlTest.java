package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ArrivalAcceptanceSuccessorMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ArrivalAcceptanceSuccessorMySqlTest {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Resource
    private PlatformCommandExecutionApi commandExecutionApi;

    private long projectId;
    private long rootId;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
        String database = environment.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = environment.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=UTF-8&nullCatalogMeansCurrent=true");
        registry.add("spring.datasource.username", () -> required(environment, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(environment, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.engineering");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        long suffix = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        projectId = 979_500_000_000L + suffix;
        rootId = 979_600_000_000L + suffix * 10;
        insertAcceptance(rootId, null, null, 1);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE tenant_id=0 "
                + "AND aggregate_type='ArrivalAcceptanceGate' AND aggregate_key=?", String.valueOf(rootId));
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE tenant_id=0 "
                + "AND idempotency_key IN (?,?)", "success-" + rootId, "rollback-" + rootId);
        jdbcTemplate.update("DELETE d FROM imp_arrival_difference d JOIN imp_arrival_acceptance a "
                + "ON a.tenant_id=d.tenant_id AND a.id=d.arrival_acceptance_id "
                + "WHERE a.tenant_id=0 AND a.project_id=?", projectId);
        jdbcTemplate.update("DELETE l FROM imp_arrival_line l JOIN imp_arrival_acceptance a "
                + "ON a.tenant_id=l.tenant_id AND a.id=l.arrival_acceptance_id "
                + "WHERE a.tenant_id=0 AND a.project_id=?", projectId);
        jdbcTemplate.update("DELETE FROM imp_arrival_acceptance WHERE tenant_id=0 AND project_id=? "
                + "AND predecessor_acceptance_id IS NOT NULL", projectId);
        jdbcTemplate.update("DELETE FROM imp_arrival_acceptance WHERE tenant_id=0 AND project_id=?", projectId);
        TenantContextHolder.clear();
    }

    @Test
    void concurrentSuccessorsAllowOnlyOneDirectChild() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> insertConcurrent(rootId + 1, ready, start));
            Future<Boolean> second = executor.submit(() -> insertConcurrent(rootId + 2, ready, start));
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("workers not ready");
            start.countDown();

            int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM imp_arrival_acceptance WHERE tenant_id=0 "
                            + "AND predecessor_acceptance_id=?", Integer.class, rootId));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedCommandTransactionRollsBackInsertedSuccessor() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            insertAcceptance(rootId + 1, rootId, "CORRECTION", null);
            throw new IllegalStateException("simulate later command failure");
        }));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM imp_arrival_acceptance WHERE tenant_id=0 "
                        + "AND predecessor_acceptance_id=?", Integer.class, rootId));
    }

    @Test
    void productionPlatformNewPersistsCompletedIdempotencyAndAudit() {
        String key = "success-" + rootId;
        String correlationId = "corr-" + rootId;

        PlatformCommandExecutionApi.ExecutionResult<String> result = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(
                        0L, "IMP:ARRIVAL_GATE_SUCCESS", 0L, key), "a".repeat(64), String.class,
                () -> "OK", response -> new PlatformCommandExecutionApi.SuccessFacts(
                        "ARRIVAL_GATE_SUCCESS", "ArrivalAcceptanceGate", String.valueOf(rootId),
                        correlationId, "{\"result\":\"OK\"}", List.of()));

        assertEquals(PlatformCommandExecutionApi.Decision.NEW, result.decision());
        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "SELECT status FROM plt_idempotency_record WHERE tenant_id=0 AND idempotency_key=?",
                String.class, key));
        assertEquals(correlationId, jdbcTemplate.queryForObject(
                "SELECT correlation_id FROM plt_operation_audit WHERE tenant_id=0 "
                        + "AND aggregate_type='ArrivalAcceptanceGate' AND aggregate_key=?",
                String.class, String.valueOf(rootId)));
    }

    @Test
    void invalidSuccessFactsRollBackSuccessorAndPlatformReservationTogether() {
        String key = "rollback-" + rootId;

        assertThrows(IllegalArgumentException.class, () -> commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(
                        0L, "IMP:ARRIVAL_GATE_ROLLBACK", 0L, key), "b".repeat(64), String.class,
                () -> {
                    long successorId = rootId + 1;
                    long lineId = rootId + 2;
                    insertAcceptance(successorId, rootId, "EXEMPTION_INVALIDATION", null);
                    jdbcTemplate.update("INSERT INTO imp_arrival_line "
                                    + "(id,arrival_acceptance_id,line_no,line_revision,scope_type,device_id,"
                                    + "device_assignment_version,expected_quantity,accepted_quantity,unit,status,"
                                    + "current_marker,version,tenant_id) "
                                    + "VALUES (?,?,1,1,'DEVICE',11,7,1,1,'SET','ACCEPTED',1,0,0)",
                            lineId, successorId);
                    jdbcTemplate.update("INSERT INTO imp_arrival_difference "
                                    + "(id,arrival_acceptance_id,arrival_line_id,difference_no,revision_no,"
                                    + "difference_type,resolution_status,reason,scope_snapshot,project_fact_version,"
                                    + "fact_impact_type,current_marker,version,tenant_id) "
                                    + "VALUES (?,?,?,1,1,'EVIDENCE_INCOMPLETE','OPEN','rollback fact',"
                                    + "JSON_OBJECT('scopeType','DEVICE','deviceId',11),77,"
                                    + "'EXEMPTION_INVALIDATION',1,0,0)",
                            rootId + 3, successorId, lineId);
                    return "OK";
                }, response -> new PlatformCommandExecutionApi.SuccessFacts(
                        "ARRIVAL_GATE_ROLLBACK", "ArrivalAcceptanceGate", String.valueOf(rootId),
                        null, "{\"result\":\"OK\"}", List.of())));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM imp_arrival_acceptance WHERE tenant_id=0 "
                        + "AND predecessor_acceptance_id=?", Integer.class, rootId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM imp_arrival_line WHERE tenant_id=0 AND id=?",
                Integer.class, rootId + 2));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM imp_arrival_difference WHERE tenant_id=0 "
                        + "AND project_fact_version=77", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=0 AND idempotency_key=?",
                Integer.class, key));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=0 "
                        + "AND aggregate_type='ArrivalAcceptanceGate' AND aggregate_key=?",
                Integer.class, String.valueOf(rootId)));
    }

    private boolean insertConcurrent(long id, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
        try {
            insertAcceptance(id, rootId, "SUPPLEMENT", null);
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    private void insertAcceptance(long id, Long predecessorId, String successorReason, Integer rootMarker) {
        jdbcTemplate.update("INSERT INTO imp_arrival_acceptance "
                        + "(id,project_id,batch_code,batch_root_marker,logistics_no,arrived_at,signer_snapshot,status,"
                        + "project_version,project_participant_fact_version,project_scope_version,"
                        + "delivery_scope_version,expected_scope_snapshot,scope_watermark,"
                        + "migration_resolution_status,predecessor_acceptance_id,successor_reason,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,NOW(),JSON_OBJECT('name','test'),'DRAFT',1,1,1,1,"
                        + "JSON_OBJECT(),JSON_OBJECT(),'NOT_APPLICABLE',?,?,0,0)",
                id, projectId, "B-" + rootId, rootMarker, "L-" + rootId, predecessorId, successorReason);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
    static class TestApplication {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
