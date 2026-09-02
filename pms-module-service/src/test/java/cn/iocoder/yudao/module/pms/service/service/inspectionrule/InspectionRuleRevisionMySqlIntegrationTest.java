package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.producttype.inspection.InspectionAssetProductTypeApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleProductTypeRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleProductTypeRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleManagePermissionGuard;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = InspectionRuleRevisionMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InspectionRuleRevisionMySqlIntegrationTest {

    private static final long TENANT_ID = 0L;

    @Resource
    private InspectionRuleRevisionService service;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private WriteFault writeFault;

    private long ruleId;
    private long revisionId;
    private String testPrefix;
    private String detectionId;
    private String ruleName;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
        String database = environment.getOrDefault("NPDMS_DB_NAME", "");
        if (!"npdms_test".equals(database)) {
            throw new IllegalStateException("InspectionRuleRevisionMySqlIntegrationTest requires NPDMS_DB_NAME=npdms_test");
        }
        String port = environment.getOrDefault("NPDMS_MYSQL_PORT", "23316");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(environment, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(environment, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.service");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setUp() {
        setRequestContext();
        writeFault.clear();
        testPrefix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        detectionId = "IT-DET-" + testPrefix;
        ruleName = "集成巡检规则-" + testPrefix;
        InspectionRuleRevisionService.DraftResult created = service.createDraft(
                new InspectionRuleRevisionService.CreateDraftCommand(detectionId, ruleName));
        ruleId = created.ruleId();
        revisionId = created.revisionId();
    }

    @AfterEach
    void tearDown() {
        try {
            writeFault.clear();
            jdbcTemplate.update("DELETE c FROM srv_inspection_rule_command_revision c "
                    + "JOIN srv_inspection_rule_revision r ON r.id=c.revision_id "
                    + "JOIN srv_inspection_rule i ON i.id=r.rule_id "
                    + "WHERE i.tenant_id=? AND (i.detection_id LIKE ? OR i.rule_name LIKE ?)",
                    TENANT_ID, "%" + testPrefix + "%", "%" + testPrefix + "%");
            jdbcTemplate.update("DELETE p FROM srv_inspection_rule_product_type_revision p "
                    + "JOIN srv_inspection_rule_revision r ON r.id=p.revision_id "
                    + "JOIN srv_inspection_rule i ON i.id=r.rule_id "
                    + "WHERE i.tenant_id=? AND (i.detection_id LIKE ? OR i.rule_name LIKE ?)",
                    TENANT_ID, "%" + testPrefix + "%", "%" + testPrefix + "%");
            jdbcTemplate.update("DELETE s FROM srv_inspection_rule_security_review s "
                    + "JOIN srv_inspection_rule_revision r ON r.id=s.revision_id "
                    + "JOIN srv_inspection_rule i ON i.id=r.rule_id "
                    + "WHERE i.tenant_id=? AND (i.detection_id LIKE ? OR i.rule_name LIKE ?)",
                    TENANT_ID, "%" + testPrefix + "%", "%" + testPrefix + "%");
            jdbcTemplate.update("DELETE r FROM srv_inspection_rule_revision r "
                    + "JOIN srv_inspection_rule i ON i.id=r.rule_id "
                    + "WHERE i.tenant_id=? AND (i.detection_id LIKE ? OR i.rule_name LIKE ?)",
                    TENANT_ID, "%" + testPrefix + "%", "%" + testPrefix + "%");
            jdbcTemplate.update("DELETE FROM srv_inspection_rule WHERE tenant_id=? "
                    + "AND (detection_id LIKE ? OR rule_name LIKE ?)",
                    TENANT_ID, "%" + testPrefix + "%", "%" + testPrefix + "%");
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void concurrentSameRuleNameCreatesAtMostOneStableIdentityAndRevision() throws Exception {
        String sharedName = "并发同名-" + testPrefix;
        List<Throwable> failures = runConcurrentCreates(
                new InspectionRuleRevisionService.CreateDraftCommand("IT-NAME-A-" + testPrefix, sharedName),
                new InspectionRuleRevisionService.CreateDraftCommand("IT-NAME-B-" + testPrefix, sharedName));

        assertEquals(1, failures.size());
        assertEquals(1_013_002_004, ((ServiceException) failures.get(0)).getCode());
        assertEquals(1L, count("SELECT COUNT(*) FROM srv_inspection_rule WHERE tenant_id=? AND rule_name=?", sharedName));
        assertEquals(1L, count("SELECT COUNT(*) FROM srv_inspection_rule_revision r "
                + "JOIN srv_inspection_rule i ON i.id=r.rule_id WHERE i.tenant_id=? AND i.rule_name=?", sharedName));
    }

    @Test
    void concurrentSameDetectionIdCreatesAtMostOneStableIdentityAndRevision() throws Exception {
        String sharedDetectionId = "IT-SAME-DET-" + testPrefix;
        List<Throwable> failures = runConcurrentCreates(
                new InspectionRuleRevisionService.CreateDraftCommand(sharedDetectionId, "并发检测A-" + testPrefix),
                new InspectionRuleRevisionService.CreateDraftCommand(sharedDetectionId, "并发检测B-" + testPrefix));

        assertEquals(1, failures.size());
        assertEquals(1_013_002_003, ((ServiceException) failures.get(0)).getCode());
        assertEquals(1L, count("SELECT COUNT(*) FROM srv_inspection_rule WHERE tenant_id=? AND detection_id=?",
                sharedDetectionId));
        assertEquals(1L, count("SELECT COUNT(*) FROM srv_inspection_rule_revision r "
                + "JOIN srv_inspection_rule i ON i.id=r.rule_id WHERE i.tenant_id=? AND i.detection_id=?",
                sharedDetectionId));
    }

    @Test
    void createRevisionFailureRollsBackStableIdentity() {
        String failedDetectionId = "IT-CREATE-FAIL-" + testPrefix;
        String failedRuleName = "创建revision失败-" + testPrefix;
        writeFault.revisionInsert = true;

        assertThrows(IllegalStateException.class, () -> service.createDraft(
                new InspectionRuleRevisionService.CreateDraftCommand(failedDetectionId, failedRuleName)));

        writeFault.clear();
        assertEquals(0L, count("SELECT COUNT(*) FROM srv_inspection_rule WHERE tenant_id=? AND detection_id=?",
                failedDetectionId));
        assertEquals(0L, count("SELECT COUNT(*) FROM srv_inspection_rule_revision r "
                + "JOIN srv_inspection_rule i ON i.id=r.rule_id WHERE i.tenant_id=? AND i.detection_id=?",
                failedDetectionId));
    }

    @Test
    void saveDraftProductTypeFailureRollsBackCompleteRevisionAndBothChildCollections() {
        service.saveDraft(saveCommand(0, "原命令", "OLD", "旧产品"));
        Map<String, Object> beforeRevision = revisionRow();
        writeFault.productTypeInsert = true;

        assertThrows(IllegalStateException.class,
                () -> service.saveDraft(saveCommand(1, "新命令", "NEW", "新产品")));

        writeFault.clear();
        assertEquals(beforeRevision, revisionRow());
        assertEquals(List.of("原命令"), jdbcTemplate.queryForList(
                "SELECT command_content FROM srv_inspection_rule_command_revision "
                        + "WHERE tenant_id=? AND revision_id=? ORDER BY execution_order",
                String.class, TENANT_ID, revisionId));
        assertEquals(List.of("OLD"), jdbcTemplate.queryForList(
                "SELECT product_type_code FROM srv_inspection_rule_product_type_revision "
                        + "WHERE tenant_id=? AND revision_id=? ORDER BY product_type_code",
                String.class, TENANT_ID, revisionId));
    }

    @Test
    void copyProductTypeFailureRollsBackNewRevisionAndCopiedChildren() {
        service.saveDraft(saveCommand(0, "原命令", "OLD", "旧产品"));
        jdbcTemplate.update("UPDATE srv_inspection_rule_revision SET status_code='PUBLISHED', "
                        + "published_by=?, published_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND id=?",
                9L, TENANT_ID, revisionId);
        long revisionsBefore = countByRule("srv_inspection_rule_revision");
        long commandsBefore = countByRule("srv_inspection_rule_command_revision");
        long productTypesBefore = countByRule("srv_inspection_rule_product_type_revision");
        writeFault.productTypeInsert = true;

        assertThrows(IllegalStateException.class, () -> service.copyRevision(revisionId));

        writeFault.clear();
        assertEquals(revisionsBefore, countByRule("srv_inspection_rule_revision"));
        assertEquals(commandsBefore, countByRule("srv_inspection_rule_command_revision"));
        assertEquals(productTypesBefore, countByRule("srv_inspection_rule_product_type_revision"));
    }

    @Test
    void validationPerformsZeroWrites() {
        service.saveDraft(saveCommand(0, "原命令", "OLD", "旧产品"));
        Map<String, Object> beforeRevision = revisionRow();
        long revisionsBefore = countByRule("srv_inspection_rule_revision");
        long commandsBefore = countByRule("srv_inspection_rule_command_revision");
        long productTypesBefore = countByRule("srv_inspection_rule_product_type_revision");
        long reviewsBefore = countByRule("srv_inspection_rule_security_review");

        service.validateRevision(revisionId);

        assertEquals(beforeRevision, revisionRow());
        assertEquals(revisionsBefore, countByRule("srv_inspection_rule_revision"));
        assertEquals(commandsBefore, countByRule("srv_inspection_rule_command_revision"));
        assertEquals(productTypesBefore, countByRule("srv_inspection_rule_product_type_revision"));
        assertEquals(reviewsBefore, countByRule("srv_inspection_rule_security_review"));
    }

    @Test
    void staleIfMatchLeavesPersistedChildrenUnchanged() {
        service.saveDraft(saveCommand(0, "原命令", "OLD", "旧产品"));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.saveDraft(saveCommand(0, "陈旧命令", "STALE", "陈旧产品")));

        assertEquals(1_013_002_007, failure.getCode());
        assertEquals("原命令", jdbcTemplate.queryForObject(
                "SELECT command_content FROM srv_inspection_rule_command_revision WHERE tenant_id=? AND revision_id=?",
                String.class, TENANT_ID, revisionId));
        assertEquals("OLD", jdbcTemplate.queryForObject(
                "SELECT product_type_code FROM srv_inspection_rule_product_type_revision WHERE tenant_id=? AND revision_id=?",
                String.class, TENANT_ID, revisionId));
    }

    @Test
    void duplicateStableIdentityDoesNotCreateOrphanRevision() {
        ServiceException failure = assertThrows(ServiceException.class, () -> service.createDraft(
                new InspectionRuleRevisionService.CreateDraftCommand(detectionId, ruleName + "-重复")));

        assertEquals(1_013_002_003, failure.getCode());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM srv_inspection_rule_revision WHERE tenant_id=? AND rule_id=?",
                Long.class, TENANT_ID, ruleId));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM srv_inspection_rule WHERE tenant_id=? AND rule_name=?",
                Long.class, TENANT_ID, ruleName + "-重复"));
    }

    private List<Throwable> runConcurrentCreates(
            InspectionRuleRevisionService.CreateDraftCommand first,
            InspectionRuleRevisionService.CreateDraftCommand second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> futures = List.of(
                    executor.submit(() -> invokeCreate(first, ready, start)),
                    executor.submit(() -> invokeCreate(second, ready, start)));
            ready.await();
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                Throwable failure = future.get();
                if (failure != null) {
                    failures.add(failure);
                }
            }
            assertTrue(failures.stream().allMatch(ServiceException.class::isInstance));
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable invokeCreate(
            InspectionRuleRevisionService.CreateDraftCommand command,
            CountDownLatch ready,
            CountDownLatch start) {
        setRequestContext();
        try {
            ready.countDown();
            start.await();
            service.createDraft(command);
            return null;
        } catch (Throwable failure) {
            return failure;
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void setRequestContext() {
        TenantContextHolder.setTenantId(TENANT_ID);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(9L).setUserType(2), new MockHttpServletRequest());
    }

    private Map<String, Object> revisionRow() {
        return jdbcTemplate.queryForMap("SELECT inspection_item, description, category_code, category_name_snapshot, "
                        + "severity_code, severity_name_snapshot, sort_order, expected_result_regex, threshold_data_type, "
                        + "threshold_operator, threshold_value, threshold_unit, version "
                        + "FROM srv_inspection_rule_revision WHERE tenant_id=? AND id=?",
                TENANT_ID, revisionId);
    }

    private long countByRule(String table) {
        if ("srv_inspection_rule_revision".equals(table)) {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM srv_inspection_rule_revision WHERE tenant_id=? AND rule_id=?",
                    Long.class, TENANT_ID, ruleId);
        }
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " c "
                        + "JOIN srv_inspection_rule_revision r ON r.id=c.revision_id "
                        + "WHERE r.tenant_id=? AND r.rule_id=?",
                Long.class, TENANT_ID, ruleId);
    }

    private long count(String sql, String value) {
        return jdbcTemplate.queryForObject(sql, Long.class, TENANT_ID, value);
    }

    private InspectionRuleRevisionService.SaveDraftCommand saveCommand(
            int expectedVersion,
            String commandContent,
            String productTypeCode,
            String productTypeName) {
        return new InspectionRuleRevisionService.SaveDraftCommand(
                revisionId, expectedVersion, "CPU利用率", "检查CPU利用率", "BASIC", "基础检测",
                "GENERAL", "一般", 10, "^CPU: [0-9]+$", "NUMBER", "≤", new BigDecimal("80"), "%",
                List.of(new InspectionRuleRevisionService.CommandDraft(
                        "CMD-CPU", commandContent, 1, 30, true)),
                List.of(new InspectionRuleRevisionService.ProductTypeDraft(productTypeCode, productTypeName)));
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量：" + name);
        }
        return value;
    }

    @SpringBootConfiguration
    @MapperScan("cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class, InspectionRuleManagePermissionGuard.class,
            InspectionRuleRevisionServiceImpl.class})
    static class TestApplication {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        InspectionAssetProductTypeApi inspectionAssetProductTypeApi() {
            return mock(InspectionAssetProductTypeApi.class);
        }

        @Bean
        DictDataApi dictDataApi() {
            return mock(DictDataApi.class);
        }

        @Bean
        PermissionApi permissionApi() {
            PermissionApi permissionApi = mock(PermissionApi.class);
            org.mockito.Mockito.when(permissionApi.hasAnyPermissions(
                    org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(String[].class)))
                    .thenReturn(true);
            return permissionApi;
        }

        @Bean
        WriteFault writeFault() {
            return new WriteFault();
        }

        @Bean
        @Primary
        InspectionRuleRevisionMapper faultingRevisionMapper(
                @Qualifier("inspectionRuleRevisionMapper") InspectionRuleRevisionMapper delegate,
                WriteFault fault) {
            return proxy(InspectionRuleRevisionMapper.class, delegate, (method, arguments) ->
                    fault.revisionInsert && "insert".equals(method)
                            && arguments != null && arguments.length == 1
                            && arguments[0] instanceof InspectionRuleRevisionDO,
                    "INSPECTION_RULE_REVISION_WRITE_FAILED_TEST");
        }

        @Bean
        @Primary
        InspectionRuleProductTypeRevisionMapper faultingProductTypeMapper(
                @Qualifier("inspectionRuleProductTypeRevisionMapper") InspectionRuleProductTypeRevisionMapper delegate,
                WriteFault fault) {
            return proxy(InspectionRuleProductTypeRevisionMapper.class, delegate, (method, arguments) ->
                    fault.productTypeInsert && "insert".equals(method)
                            && arguments != null && arguments.length == 1
                            && arguments[0] instanceof InspectionRuleProductTypeRevisionDO,
                    "INSPECTION_RULE_PRODUCT_TYPE_WRITE_FAILED_TEST");
        }

        private static <T> T proxy(
                Class<T> type,
                T delegate,
                FaultPredicate predicate,
                String failureMessage) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (proxy, method, arguments) -> {
                        try {
                            Object result = method.invoke(delegate, arguments);
                            if (predicate.shouldFail(method.getName(), arguments)) {
                                throw new IllegalStateException(failureMessage);
                            }
                            return result;
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    }));
        }
    }

    @FunctionalInterface
    interface FaultPredicate {
        boolean shouldFail(String method, Object[] arguments);
    }

    static final class WriteFault {
        volatile boolean revisionInsert;
        volatile boolean productTypeInsert;

        void clear() {
            revisionInsert = false;
            productTypeInsert = false;
        }
    }
}
