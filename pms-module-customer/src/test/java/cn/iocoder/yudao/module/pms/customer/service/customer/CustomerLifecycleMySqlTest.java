package cn.iocoder.yudao.module.pms.customer.service.customer;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CustomerLifecycleMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CustomerLifecycleMySqlTest {

    private static final String CODE_PREFIX = "IT-FCUS001-LIFE-";

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private CustomerMasterMapper customerMasterMapper;

    private long customerId;

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
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.customer");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        customerId = 887_100_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM cus_customer_master WHERE id = ?", customerId);
    }

    @Test
    void disableUpdatesActiveRowWithoutSoftDeletingIt() {
        insertCustomer("ENABLED", false, 0);

        int updated = customerMasterMapper.updateLifecycleByVersion(new CustomerLifecycleUpdate(
                1L, customerId, "ENABLED", "DISABLED", false, false, 0L));

        assertEquals(1, updated);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT lifecycle_status, deleted, version FROM cus_customer_master WHERE id = ?", customerId);
        assertEquals("DISABLED", row.get("lifecycle_status"));
        assertEquals(false, row.get("deleted"));
        assertEquals(1, ((Number) row.get("version")).intValue());
    }

    @Test
    void concurrentDeletesFromSameVersionHaveOneSuccess() throws Exception {
        insertCustomer("ENABLED", false, 0);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> futures = List.of(
                    executor.submit(() -> deleteAfter(start)),
                    executor.submit(() -> deleteAfter(start)));
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }

            assertEquals(1, results.stream().filter(result -> result == 1).count());
            assertEquals(1, results.stream().filter(result -> result == 0).count());
        }
    }

    @Test
    void restorePreservesOriginalIdentity() {
        insertCustomer("DELETED", true, 2);

        int updated = customerMasterMapper.updateLifecycleByVersion(new CustomerLifecycleUpdate(
                1L, customerId, "DELETED", "ENABLED", true, false, 2L));

        assertEquals(1, updated);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, code, lifecycle_status, deleted, version FROM cus_customer_master WHERE id = ?", customerId);
        assertEquals(customerId, ((Number) row.get("id")).longValue());
        assertEquals(CODE_PREFIX + customerId, row.get("code"));
        assertEquals("ENABLED", row.get("lifecycle_status"));
        assertEquals(false, row.get("deleted"));
        assertEquals(3, ((Number) row.get("version")).intValue());
    }

    private int deleteAfter(CountDownLatch start) throws Exception {
        start.await();
        return customerMasterMapper.updateLifecycleByVersion(new CustomerLifecycleUpdate(
                1L, customerId, "ENABLED", "DELETED", false, true, 0L));
    }

    private void insertCustomer(String status, boolean deleted, int version) {
        jdbcTemplate.update("INSERT INTO cus_customer_master "
                        + "(id,code,name,lifecycle_status,source_type,sync_status,data_as_of,version,deleted,tenant_id) "
                        + "VALUES (?,?,?,?,'PLATFORM_CREATED','NOT_APPLICABLE',CURRENT_TIMESTAMP,?,?,1)",
                customerId, CODE_PREFIX + customerId, CODE_PREFIX + "客户", status, version, deleted);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量：" + name);
        }
        return value;
    }

    @SpringBootConfiguration
    @MapperScan("cn.iocoder.yudao.module.pms.customer.dal.mysql.customer")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class})
    static class TestApplication {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
