package cn.iocoder.yudao.module.pms.customer.service.query;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.VisibleCustomerDetailQuery;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.VisibleCustomerPageQuery;
import cn.iocoder.yudao.module.pms.customer.service.customer.CustomerPlatformUpdate;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeSlice;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CustomerScopeSqlMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CustomerScopeSqlMySqlTest {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private CustomerMasterMapper customerMasterMapper;

    private long baseId;

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
        baseId = 887_200_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 10;
        insertCustomer(baseId, "D-A", "M-A", "S-A", "E-A", "I-A");
        insertCustomer(baseId + 1, "D-A", "M-B", "S-B", "E-B", "I-B");
        insertCustomer(baseId + 2, "D-B", "M-A", "S-B", "E-B", "I-B");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM cus_customer_master WHERE id BETWEEN ? AND ?", baseId, baseId + 9);
    }

    @Test
    void keepsDimensionsAndedWithinEachSliceAndSlicesOred() {
        var query = new VisibleCustomerPageQuery(
                1L, null, null, null, null, null, null, null, null, null, false,
                List.of(
                        slice("D-A", "M-A", "S-A", "E-A", "I-A"),
                        slice("D-B", "M-A", "S-B", "E-B", "I-B")),
                new PageParam());

        var result = customerMasterMapper.selectVisiblePage(query);

        assertEquals(2L, result.getTotal());
        assertEquals(Set.of(baseId, baseId + 2), result.getList().stream()
                .map(CustomerMasterDO::getId)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void detailUsesTheSameUnflattenedSliceSemantics() {
        List<CustomerScopeSlice> slices = List.of(
                slice("D-A", "M-A", "S-A", "E-A", "I-A"),
                slice("D-B", "M-A", "S-B", "E-B", "I-B"));

        assertNull(customerMasterMapper.selectVisibleById(
                new VisibleCustomerDetailQuery(1L, baseId + 1, false, slices)));
        assertEquals(baseId + 2, customerMasterMapper.selectVisibleById(
                new VisibleCustomerDetailQuery(1L, baseId + 2, false, slices)).getId());
    }

    @Test
    void classificationCasUpdateRequiresCurrentVersion() {
        int updated = customerMasterMapper.updatePlatformFieldsByVersion(new CustomerPlatformUpdate(
                1L, baseId, null, null, null,
                "D-C", "办事处C", "M-C", "市场C", "S-C", "系统C",
                "E-C", "拓展C", "I-C", "行业C",
                false, false, false, true, 0L));
        int stale = customerMasterMapper.updatePlatformFieldsByVersion(new CustomerPlatformUpdate(
                1L, baseId, null, null, null,
                "D-D", "办事处D", "M-D", "市场D", "S-D", "系统D",
                "E-D", "拓展D", "I-D", "行业D",
                false, false, false, true, 0L));

        assertEquals(1, updated);
        assertEquals(0, stale);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT department_code, market_code, system_code, expend_code, industry_code, version "
                        + "FROM cus_customer_master WHERE id = ?", baseId);
        assertEquals("D-C", row.get("department_code"));
        assertEquals("M-C", row.get("market_code"));
        assertEquals("S-C", row.get("system_code"));
        assertEquals("E-C", row.get("expend_code"));
        assertEquals("I-C", row.get("industry_code"));
        assertEquals(1, ((Number) row.get("version")).intValue());
    }

    @Test
    void deletedFilterReturnsOnlyAuthorizedDeletedCustomers() {
        jdbcTemplate.update("UPDATE cus_customer_master SET lifecycle_status = 'DELETED', deleted = b'1' WHERE id = ?",
                baseId);
        var query = new VisibleCustomerPageQuery(
                1L, null, null, null, null, null, null, null, "DELETED", null, false,
                List.of(slice("D-A", "M-A", "S-A", "E-A", "I-A")), new PageParam());

        var result = customerMasterMapper.selectVisiblePage(query);

        assertEquals(1L, result.getTotal());
        assertEquals(baseId, result.getList().getFirst().getId());
    }

    private CustomerScopeSlice slice(
            String departmentCode,
            String marketCode,
            String systemCode,
            String expendCode,
            String industryCode) {
        return new CustomerScopeSlice(
                Set.of(departmentCode), Set.of(marketCode), Set.of(systemCode),
                Set.of(expendCode), Set.of(industryCode));
    }

    private void insertCustomer(
            long id,
            String departmentCode,
            String marketCode,
            String systemCode,
            String expendCode,
            String industryCode) {
        jdbcTemplate.update("INSERT INTO cus_customer_master "
                        + "(id,code,name,lifecycle_status,source_type,sync_status,data_as_of,version,deleted,tenant_id,"
                        + "department_code,market_code,system_code,expend_code,industry_code) "
                        + "VALUES (?,?,?,'ENABLED','PLATFORM_CREATED','NOT_APPLICABLE',CURRENT_TIMESTAMP,0,b'0',1,?,?,?,?,?)",
                id, "IT-FCUS001-SCOPE-" + id, "客户" + id,
                departmentCode, marketCode, systemCode, expendCode, industryCode);
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
