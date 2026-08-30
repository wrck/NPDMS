package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
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

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CommerceAuthorityIngestMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CommerceAuthorityIngestMySqlTest {

    private static final long TENANT_ID = 990_003L;

    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private CommerceAuthorityIngestService service;

    private String suffix;

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
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.commerce");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        suffix = Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits(), 36);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE tenant_id=? AND aggregate_type='CommerceAuthorityBatch'", TENANT_ID);
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE tenant_id=? AND scope_code='COM:AUTHORITY:INGEST'", TENANT_ID);
        jdbcTemplate.update("DELETE FROM com_delivery_scope_detail WHERE tenant_id=?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM com_delivery_scope WHERE tenant_id=?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM com_delivery_scope_project_version WHERE tenant_id=?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM com_sales_order_contract_relation WHERE tenant_id=?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM com_order_line WHERE tenant_id=?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM com_sales_order WHERE tenant_id=?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM com_contract WHERE tenant_id=?", TENANT_ID);
        TenantContextHolder.clear();
    }

    @Test
    void atomicBatchSupportsEventAndObjectReplay() {
        CommerceAuthorityBatchCommand initial = fullBatch("EV-1-" + suffix, "V1", null, "10");

        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, service.ingest(initial).decision());
        assertEquals(1, count("com_contract"));
        assertEquals(1, count("com_sales_order"));
        assertEquals(1, count("com_order_line"));
        assertEquals(1, count("com_sales_order_contract_relation"));
        assertEquals(CommerceAuthorityBatchResult.Decision.EVENT_REPLAYED, service.ingest(initial).decision());

        CommerceAuthorityBatchCommand objectReplay = fullBatch("EV-2-" + suffix, "V1", "IGNORED", "10");
        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED_NO_CHANGE,
                service.ingest(objectReplay).decision());
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? "
                + "AND aggregate_type='CommerceAuthorityBatch'", Integer.class, TENANT_ID));
    }

    @Test
    void predecessorConflictRollsBackEarlierObjectAndPlatformReservation() {
        service.ingest(fullBatch("EV-BASE-" + suffix, "V1", null, "10"));
        String eventId = "EV-ROLLBACK-" + suffix;
        CommerceAuthorityBatchCommand bad = new CommerceAuthorityBatchCommand(TENANT_ID, eventId,
                "B-ROLLBACK-" + suffix, "ERP", "WM-2",
                List.of(contract("A-NEW-" + suffix, null, "V1"),
                        contract("C-" + suffix, "V0", "V2")),
                List.of(), List.of(), List.of(), time(), "CORR-ROLLBACK-" + suffix);

        CommerceAuthorityIngestException error = assertThrows(CommerceAuthorityIngestException.class,
                () -> service.ingest(bad));

        assertEquals(CommerceAuthorityIngestException.Code.SOURCE_VERSION_CONFLICT, error.getCode());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM com_contract WHERE tenant_id=? "
                + "AND source_key=?", Integer.class, TENANT_ID, "A-NEW-" + suffix));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? "
                + "AND idempotency_key=?", Integer.class, TENANT_ID, eventId));
    }

    @Test
    void quantityDecreaseCreatesConflictHistoryAndProjectWatermark() {
        service.ingest(fullBatch("EV-BASE-" + suffix, "V1", null, "10"));
        long lineId = jdbcTemplate.queryForObject("SELECT id FROM com_order_line WHERE tenant_id=? AND source_key=?",
                Long.class, TENANT_ID, "L-" + suffix);
        long scopeId = 990_100_000_000L + Math.abs(suffix.hashCode());
        long detailId = scopeId + 1;
        long projectId = scopeId + 2;
        jdbcTemplate.update("INSERT INTO com_delivery_scope "
                        + "(id,order_line_id,project_id,allocated_qty,scope_status,allocation_version,source_evidence,"
                        + "effective_from,version,creator,create_time,updater,update_time,deleted,tenant_id) "
                        + "VALUES (?,?,?,8,'ACTIVE',1,'BASE',NOW(3),0,'0',NOW(3),'0',NOW(3),b'0',?)",
                scopeId, lineId, projectId, TENANT_ID);
        jdbcTemplate.update("INSERT INTO com_delivery_scope_detail "
                        + "(id,delivery_scope_id,allocated_qty,unit_code,product_code,location_text,"
                        + "location_resolution_status,detail_status,version,creator,create_time,updater,update_time,deleted,tenant_id) "
                        + "VALUES (?,?,8,'PCS','ITEM-1','待权威解析','UNRESOLVED','ACTIVE',0,'0',NOW(3),'0',NOW(3),b'0',?)",
                detailId, scopeId, TENANT_ID);
        jdbcTemplate.update("INSERT INTO com_delivery_scope_project_version "
                        + "(id,project_id,scope_version,payload_version,last_change_type,version,creator,create_time,"
                        + "updater,update_time,deleted,tenant_id) VALUES (?,?,4,4,'ASSIGN',0,'0',NOW(3),'0',NOW(3),b'0',?)",
                scopeId + 3, projectId, TENANT_ID);

        CommerceAuthorityBatchCommand decrease = new CommerceAuthorityBatchCommand(TENANT_ID,
                "EV-DEC-" + suffix, "B-DEC-" + suffix, "ERP", "WM-2", List.of(), List.of(),
                List.of(line("L-" + suffix, "V1", "V2", "5")), List.of(), time(), "CORR-DEC-" + suffix);
        assertEquals(CommerceAuthorityBatchResult.Decision.ACCEPTED, service.ingest(decrease).decision());

        assertEquals(List.of("RELEASED", "CONFLICT"), jdbcTemplate.queryForList(
                "SELECT scope_status FROM com_delivery_scope WHERE tenant_id=? AND order_line_id=? ORDER BY allocation_version",
                String.class, TENANT_ID, lineId));
        assertEquals(5L, jdbcTemplate.queryForObject("SELECT scope_version FROM com_delivery_scope_project_version "
                + "WHERE tenant_id=? AND project_id=?", Long.class, TENANT_ID, projectId));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM com_delivery_scope_detail d JOIN "
                + "com_delivery_scope s ON s.id=d.delivery_scope_id AND s.tenant_id=d.tenant_id "
                + "WHERE d.tenant_id=? AND s.scope_status='CONFLICT'", Integer.class, TENANT_ID));
    }

    @Test
    void concurrentDifferentSuccessorsAllowOnlyOnePredecessorCas() throws Exception {
        service.ingest(fullBatch("EV-BASE-" + suffix, "V1", null, "10"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> updateContract("V2", ready, start));
            Future<Boolean> second = executor.submit(() -> updateContract("V3", ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(15, TimeUnit.SECONDS) ? 1 : 0));
            String version = jdbcTemplate.queryForObject("SELECT source_version FROM com_contract WHERE tenant_id=? "
                    + "AND source_key=?", String.class, TENANT_ID, "C-" + suffix);
            assertTrue(List.of("V2", "V3").contains(version));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private boolean updateContract(String version, CountDownLatch ready, CountDownLatch start) throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
            CommerceAuthorityBatchCommand command = new CommerceAuthorityBatchCommand(TENANT_ID,
                    "EV-" + version + "-" + suffix, "B-" + version + "-" + suffix, "ERP", "WM-" + version,
                    List.of(contract("C-" + suffix, "V1", version)), List.of(), List.of(), List.of(),
                    time(), "CORR-" + version + "-" + suffix);
            try {
                service.ingest(command);
                return true;
            } catch (CommerceAuthorityIngestException ex) {
                if (ex.getCode() == CommerceAuthorityIngestException.Code.SOURCE_VERSION_CONFLICT) return false;
                throw ex;
            }
        } finally {
            TenantContextHolder.clear();
        }
    }

    private CommerceAuthorityBatchCommand fullBatch(String eventId, String version,
                                                    String ignoredPrevious, String quantity) {
        String previous = "V1".equals(version) ? null : ignoredPrevious;
        return new CommerceAuthorityBatchCommand(TENANT_ID, eventId, "B-" + eventId, "ERP", "WM-1",
                List.of(contract("C-" + suffix, previous, version)),
                List.of(order("O-" + suffix, previous, version)),
                List.of(line("L-" + suffix, previous, version, quantity)),
                List.of(new CommerceOrderContractRelationFact("O-" + suffix, "C-" + suffix,
                        previous, version, time(), null)), time(), "CORR-" + eventId);
    }

    private CommerceContractFact contract(String key, String previous, String version) {
        return new CommerceContractFact(key, previous, version, "ACME", "CN-" + key,
                "CU-1", "Customer", new BigDecimal("100.00"), "CNY",
                CommerceSourceLifecycleStatus.ACTIVE, time());
    }

    private CommerceSalesOrderFact order(String key, String previous, String version) {
        return new CommerceSalesOrderFact(key, previous, version, "ACME", "ON-" + suffix,
                "NORMAL", "CU-1", "Customer", new BigDecimal("100.0"), "CNY",
                CommerceSourceLifecycleStatus.ACTIVE, time());
    }

    private CommerceOrderLineFact line(String key, String previous, String version, String quantity) {
        return new CommerceOrderLineFact(key, previous, version, "O-" + suffix, "10", "ITEM-1", null,
                new BigDecimal(quantity), "PCS", CommerceSourceLifecycleStatus.ACTIVE, time());
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=?",
                Integer.class, TENANT_ID);
    }

    private LocalDateTime time() {
        return LocalDateTime.of(2026, 8, 30, 12, 0, 0, 123_000_000);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            CommerceAuthorityIngestService.class, AuthorityPayloadCanonicalizer.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
    static class TestApplication {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
