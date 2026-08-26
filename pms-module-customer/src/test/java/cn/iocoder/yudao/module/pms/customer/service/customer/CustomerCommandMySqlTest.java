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
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CustomerCommandMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CustomerCommandMySqlTest {

    private static final String CODE_PREFIX = "IT-FCUS001-";

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private CustomerMasterMapper customerMasterMapper;

    private long baseId;
    private String code;

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
        baseId = 887_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 10;
        code = CODE_PREFIX + baseId;
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM cus_customer_external_mapping WHERE customer_id BETWEEN ? AND ?",
                baseId, baseId + 9);
        jdbcTemplate.update("DELETE FROM cus_customer_field_history WHERE customer_id BETWEEN ? AND ?",
                baseId, baseId + 9);
        jdbcTemplate.update("DELETE FROM cus_customer_master WHERE id BETWEEN ? AND ?", baseId, baseId + 9);
    }

    @Test
    void tenantCodeIsUniqueButOtherTenantMayReuseCode() {
        insertCustomer(baseId, 1L, code, false);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertCustomer(baseId + 1, 1L, code, false));
        insertCustomer(baseId + 2, 2L, code, false);

        assertEquals(2L, countCustomersByCode());
    }

    @Test
    void softDeleteDoesNotReleaseCustomerCode() {
        insertCustomer(baseId, 1L, code, true);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertCustomer(baseId + 1, 1L, code, false));
    }

    @Test
    void currentCrmMappingIsUniqueWithinTenant() {
        insertCustomer(baseId, 1L, code, false);
        insertCustomer(baseId + 1, 1L, code + "-B", false);
        insertMapping(baseId, baseId, 1L, "CRM-" + baseId);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertMapping(baseId + 1, baseId + 1, 1L, "CRM-" + baseId));
    }

    @Test
    void concurrentUpdatesFromSameVersionHaveOneSuccess() throws Exception {
        insertCustomer(baseId, 1L, code, false);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> futures = List.of(
                    executor.submit(() -> updateAfter(start, "备注-A")),
                    executor.submit(() -> updateAfter(start, "备注-B")));
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }

            assertEquals(1, results.stream().filter(result -> result == 1).count());
            assertEquals(1, results.stream().filter(result -> result == 0).count());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT version FROM cus_customer_master WHERE id = ?", Integer.class, baseId));
        }
    }

    private int updateAfter(CountDownLatch start, String remark) throws Exception {
        start.await();
        return customerMasterMapper.updatePlatformFieldsByVersion(new CustomerPlatformUpdate(
                1L, baseId, null, null, remark, false, false, true, 0L));
    }

    private void insertCustomer(long id, long tenantId, String customerCode, boolean deleted) {
        jdbcTemplate.update("INSERT INTO cus_customer_master "
                        + "(id,code,name,lifecycle_status,source_type,sync_status,data_as_of,version,deleted,tenant_id) "
                        + "VALUES (?,?,?,'ENABLED','PLATFORM_CREATED','NOT_APPLICABLE',CURRENT_TIMESTAMP,0,?,?)",
                id, customerCode, CODE_PREFIX + "客户", deleted, tenantId);
    }

    private void insertMapping(long id, long customerId, long tenantId, String sourceKey) {
        jdbcTemplate.update("INSERT INTO cus_customer_external_mapping "
                        + "(id,customer_id,source_system,source_key,source_version,effective_from,deleted,tenant_id) "
                        + "VALUES (?,?,'CRM',?,'v1',CURRENT_TIMESTAMP,b'0',?)",
                id, customerId, sourceKey, tenantId);
    }

    private long countCustomersByCode() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cus_customer_master WHERE code = ?", Long.class, code);
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
