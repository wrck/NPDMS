package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.producttype.inspection.InspectionAssetProductTypeApi;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRulePublishUpdate;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleManagePermissionGuard;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleContentDigestService;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleSecurityReviewPermissionGuard;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;
import static org.mockito.ArgumentMatchers.any;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = InspectionRulePublicationTransactionMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InspectionRulePublicationTransactionMySqlIntegrationTest {

    private static final long TENANT_ID = 0L;
    private static final long ACTOR_ID = 9L;
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 9, 2, 18, 0);

    @Resource
    private InspectionRulePublicationTransactionService service;
    @Resource
    private InspectionRuleRevisionService revisionService;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private PublicationWriteFault writeFault;
    @Resource
    private InspectionAssetProductTypeApi assetProductTypeApi;
    @Resource
    private DictDataApi dictDataApi;
    @Resource
    private InspectionRuleContentDigestService contentDigestService;

    private long ruleId;
    private long currentRevisionId;
    private long firstDraftId;
    private long secondDraftId;
    private String testPrefix;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
        String database = environment.getOrDefault("NPDMS_DB_NAME", "");
        if (!"npdms_test".equals(database)) {
            throw new IllegalStateException(
                    "InspectionRulePublicationTransactionMySqlIntegrationTest requires NPDMS_DB_NAME=npdms_test");
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
        ruleId = IdWorker.getId();
        currentRevisionId = IdWorker.getId();
        firstDraftId = IdWorker.getId();
        secondDraftId = IdWorker.getId();
        insertRule();
        insertRevision(currentRevisionId, 1, "PUBLISHED", 4, true);
        insertRevision(firstDraftId, 2, "DRAFT", 3, false);
        insertRevision(secondDraftId, 3, "DRAFT", 3, false);
        insertProductType(firstDraftId, "A", "草稿A");
        insertProductType(secondDraftId, "A", "草稿A");
    }

    @AfterEach
    void tearDown() {
        writeFault.clear();
        reset(assetProductTypeApi, dictDataApi);
        jdbcTemplate.update("DELETE FROM srv_inspection_rule_security_review WHERE tenant_id=? "
                + "AND revision_id IN (SELECT id FROM srv_inspection_rule_revision WHERE tenant_id=? AND rule_id=?)",
                TENANT_ID, TENANT_ID, ruleId);
        jdbcTemplate.update("DELETE FROM srv_inspection_rule_command_revision WHERE tenant_id=? "
                + "AND revision_id IN (SELECT id FROM srv_inspection_rule_revision WHERE tenant_id=? AND rule_id=?)",
                TENANT_ID, TENANT_ID, ruleId);
        jdbcTemplate.update("DELETE FROM srv_inspection_rule_product_type_revision WHERE tenant_id=? "
                + "AND revision_id IN (SELECT id FROM srv_inspection_rule_revision WHERE tenant_id=? AND rule_id=?)",
                TENANT_ID, TENANT_ID, ruleId);
        jdbcTemplate.update("DELETE FROM srv_inspection_rule_revision WHERE tenant_id=? AND rule_id=?",
                TENANT_ID, ruleId);
        jdbcTemplate.update("DELETE FROM srv_inspection_rule WHERE tenant_id=? AND id=?", TENANT_ID, ruleId);
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void replacesCurrentRevisionAndRefreshesAuthoritativeSnapshotsInOneCommit() {
        InspectionRulePublicationTransactionService.PublishResult result = service.publishVerified(
                command(firstDraftId, currentRevisionId));

        assertEquals(firstDraftId, result.revisionId());
        assertEquals(currentRevisionId, result.disabledRevisionId());
        assertRevision(currentRevisionId, "DISABLED", 5);
        assertRevision(firstDraftId, "PUBLISHED", 4);
        Map<String, Object> published = jdbcTemplate.queryForMap(
                "SELECT category_name_snapshot, severity_name_snapshot FROM srv_inspection_rule_revision "
                        + "WHERE tenant_id=? AND id=?", TENANT_ID, firstDraftId);
        assertEquals("权威分类", published.get("category_name_snapshot"));
        assertEquals("权威严重度", published.get("severity_name_snapshot"));
        assertEquals("权威产品A", jdbcTemplate.queryForObject(
                "SELECT product_type_name_snapshot FROM srv_inspection_rule_product_type_revision "
                        + "WHERE tenant_id=? AND revision_id=? AND product_type_code='A'",
                String.class, TENANT_ID, firstDraftId));
        assertEquals(1L, publishedCount());
    }

    @Test
    void publishWriteFailureRollsBackOldDisableAndAllSnapshotChanges() {
        writeFault.publishAfterWrite = true;

        assertThrows(IllegalStateException.class,
                () -> service.publishVerified(command(firstDraftId, currentRevisionId)));

        writeFault.clear();
        assertRevision(currentRevisionId, "PUBLISHED", 4);
        assertRevision(firstDraftId, "DRAFT", 3);
        assertEquals("草稿分类", jdbcTemplate.queryForObject(
                "SELECT category_name_snapshot FROM srv_inspection_rule_revision WHERE tenant_id=? AND id=?",
                String.class, TENANT_ID, firstDraftId));
        assertEquals("草稿A", jdbcTemplate.queryForObject(
                "SELECT product_type_name_snapshot FROM srv_inspection_rule_product_type_revision "
                        + "WHERE tenant_id=? AND revision_id=? AND product_type_code='A'",
                String.class, TENANT_ID, firstDraftId));
        assertEquals(1L, publishedCount());
    }

    @Test
    void latestExactDigestReviewControlsPublicationAndReviewClosesAfterPublish() {
        insertCommand(firstDraftId);
        when(dictDataApi.getDictDataList("pms_inspection_rule_category"))
                .thenReturn(List.of(dictData("pms_inspection_rule_category", "BASIC", "权威分类")));
        when(dictDataApi.getDictDataList("pms_inspection_rule_severity"))
                .thenReturn(List.of(dictData("pms_inspection_rule_severity", "GENERAL", "权威严重度")));
        when(assetProductTypeApi.getByCodes(any())).thenReturn(List.of(new ProductTypeCodeResult(
                "A", true, true, "权威产品A", "CRM", "v1", "SYNCED",
                PUBLISHED_AT.minusDays(1), false)));
        String digest = contentDigestService.digest(new InspectionRuleContentDigestService.ReviewContent(
                List.of(new InspectionRuleContentDigestService.CommandContent(
                        "show cpu", 1, 30, false)),
                "^CPU: [0-9]+$"));

        insertReview(IdWorker.getId(), firstDraftId, "0".repeat(64), "PASSED", PUBLISHED_AT.minusMinutes(2));
        assertThrows(ServiceException.class, () -> service.publishApproved(approvedCommand(firstDraftId)));
        assertRevision(currentRevisionId, "PUBLISHED", 4);
        assertRevision(firstDraftId, "DRAFT", 3);

        long passedId = IdWorker.getId();
        insertReview(passedId, firstDraftId, digest, "PASSED", PUBLISHED_AT.minusMinutes(1));
        insertReview(passedId + 1, firstDraftId, digest, "REJECTED", PUBLISHED_AT.minusMinutes(1));
        assertThrows(ServiceException.class, () -> service.publishApproved(approvedCommand(firstDraftId)));
        assertRevision(currentRevisionId, "PUBLISHED", 4);
        assertRevision(firstDraftId, "DRAFT", 3);

        InspectionRulePublicationTransactionService.SecurityReviewResult review =
                service.recordSecurityReview(new InspectionRulePublicationTransactionService.SecurityReviewCommand(
                        TENANT_ID, firstDraftId, 3, "PASSED",
                        new InspectionRuleSecurityReviewPermissionGuard.ReviewAuthorization(
                                ACTOR_ID, "pms:inspection-rule:security-review", "RBAC_PERMISSION", null),
                        PUBLISHED_AT));
        assertEquals(digest, review.contentDigest());
        assertEquals(null, jdbcTemplate.queryForObject(
                "SELECT authorization_source_id FROM srv_inspection_rule_security_review "
                        + "WHERE tenant_id=? AND review_reference=?",
                String.class, TENANT_ID, review.reviewReference()));

        InspectionRulePublicationTransactionService.ApprovedPublishResult published =
                service.publishApproved(approvedCommand(firstDraftId));
        assertEquals(review.reviewReference(), published.reviewReference());
        assertRevision(currentRevisionId, "DISABLED", 5);
        assertRevision(firstDraftId, "PUBLISHED", 4);
        assertThrows(ServiceException.class, () -> service.recordSecurityReview(
                new InspectionRulePublicationTransactionService.SecurityReviewCommand(
                        TENANT_ID, firstDraftId, 4, "REJECTED",
                        new InspectionRuleSecurityReviewPermissionGuard.ReviewAuthorization(
                                ACTOR_ID, "pms:inspection-rule:security-review", "RBAC_PERMISSION", null),
                        PUBLISHED_AT.plusSeconds(1))));
    }

    @Test
    void concurrentDraftsVerifiedAgainstSameCurrentRevisionAllowAtMostOneCommit() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> futures = List.of(
                    executor.submit(() -> invokePublish(firstDraftId, ready, start)),
                    executor.submit(() -> invokePublish(secondDraftId, ready, start)));
            ready.await();
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                Throwable failure = future.get();
                if (failure != null) {
                    failures.add(failure);
                }
            }

            assertEquals(1, failures.size());
            assertInstanceOf(ServiceException.class, failures.get(0));
            assertEquals(1_013_002_007, ((ServiceException) failures.get(0)).getCode());
            assertEquals(1L, publishedCount());
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM srv_inspection_rule_revision WHERE tenant_id=? AND rule_id=? "
                            + "AND id IN (?, ?) AND status_code='PUBLISHED'",
                    Long.class, TENANT_ID, ruleId, firstDraftId, secondDraftId));
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM srv_inspection_rule_revision WHERE tenant_id=? AND rule_id=? "
                            + "AND id IN (?, ?) AND status_code='DRAFT'",
                    Long.class, TENANT_ID, ruleId, firstDraftId, secondDraftId));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSaveAndPublishUseOneAggregateSnapshotWithoutMixedChildren() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> publish = executor.submit(() -> invokePublish(firstDraftId, ready, start));
            Future<Throwable> save = executor.submit(() -> invokeSave(firstDraftId, ready, start));
            ready.await();
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : List.of(publish, save)) {
                Throwable failure = future.get();
                if (failure != null) {
                    failures.add(failure);
                }
            }

            assertEquals(1, failures.size());
            assertInstanceOf(ServiceException.class, failures.get(0));
            assertTrue(List.of(1_013_002_006, 1_013_002_007)
                    .contains(((ServiceException) failures.get(0)).getCode()));
            Map<String, Object> revision = jdbcTemplate.queryForMap(
                    "SELECT status_code, version, category_name_snapshot FROM srv_inspection_rule_revision "
                            + "WHERE tenant_id=? AND id=?", TENANT_ID, firstDraftId);
            String productName = jdbcTemplate.queryForObject(
                    "SELECT product_type_name_snapshot FROM srv_inspection_rule_product_type_revision "
                            + "WHERE tenant_id=? AND revision_id=? AND product_type_code='A'",
                    String.class, TENANT_ID, firstDraftId);
            if ("PUBLISHED".equals(revision.get("status_code"))) {
                assertEquals(4, ((Number) revision.get("version")).intValue());
                assertEquals("权威分类", revision.get("category_name_snapshot"));
                assertEquals("权威产品A", productName);
            } else {
                assertEquals("DRAFT", revision.get("status_code"));
                assertEquals(4, ((Number) revision.get("version")).intValue());
                assertEquals("保存分类", revision.get("category_name_snapshot"));
                assertEquals("保存产品A", productName);
                assertRevision(currentRevisionId, "PUBLISHED", 4);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable invokePublish(long revisionId, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            start.await();
            service.publishVerified(command(revisionId, currentRevisionId));
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private Throwable invokeSave(long revisionId, CountDownLatch ready, CountDownLatch start) {
        setRequestContext();
        try {
            ready.countDown();
            start.await();
            revisionService.saveDraft(new InspectionRuleRevisionService.SaveDraftCommand(
                    revisionId, 3, "保存CPU", "保存描述", "BASIC", "保存分类",
                    "GENERAL", "保存严重度", 11, "^SAVE$", "NUMBER", "≤",
                    new BigDecimal("70"), "%",
                    List.of(new InspectionRuleRevisionService.CommandDraft(
                            "CMD-SAVE", "save command", 1, 30, true)),
                    List.of(new InspectionRuleRevisionService.ProductTypeDraft("A", "保存产品A"))));
            return null;
        } catch (Throwable failure) {
            return failure;
        } finally {
            TenantContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private static void setRequestContext() {
        TenantContextHolder.setTenantId(TENANT_ID);
        SecurityFrameworkUtils.setLoginUser(
                new LoginUser().setId(ACTOR_ID).setUserType(2),
                new MockHttpServletRequest());
    }

    private InspectionRulePublicationTransactionService.PublishCommand command(
            long revisionId,
            Long expectedPublishedRevisionId) {
        return new InspectionRulePublicationTransactionService.PublishCommand(
                TENANT_ID,
                revisionId,
                3,
                expectedPublishedRevisionId,
                "权威分类",
                "权威严重度",
                new LinkedHashMap<>(Map.of("A", "权威产品A")),
                ACTOR_ID,
                PUBLISHED_AT);
    }

    private InspectionRulePublicationTransactionService.ApprovedPublishCommand approvedCommand(long revisionId) {
        return new InspectionRulePublicationTransactionService.ApprovedPublishCommand(
                TENANT_ID, revisionId, 3, currentRevisionId, ACTOR_ID, PUBLISHED_AT);
    }

    private void insertRule() {
        jdbcTemplate.update("INSERT INTO srv_inspection_rule "
                        + "(id, detection_id, rule_name, version, creator, updater, tenant_id) VALUES (?, ?, ?, 0, 'it', 'it', ?)",
                ruleId, "IT-PUB-" + testPrefix, "发布事务-" + testPrefix, TENANT_ID);
    }

    private void insertRevision(long revisionId, int revisionNo, String status, int version, boolean published) {
        jdbcTemplate.update("INSERT INTO srv_inspection_rule_revision "
                        + "(id, rule_id, revision_no, status_code, rule_name_snapshot, inspection_item, description, "
                        + "category_code, category_name_snapshot, severity_code, severity_name_snapshot, sort_order, "
                        + "expected_result_regex, threshold_data_type, threshold_operator, threshold_value, threshold_unit, "
                        + "published_by, published_at, version, creator, updater, tenant_id) "
                        + "VALUES (?, ?, ?, ?, ?, 'CPU利用率', '检查CPU利用率', 'BASIC', '草稿分类', 'GENERAL', "
                        + "'草稿严重度', 10, '^CPU: [0-9]+$', 'NUMBER', '≤', 80, '%', ?, ?, ?, 'it', 'it', ?)",
                revisionId, ruleId, revisionNo, status, "发布事务-" + testPrefix,
                published ? ACTOR_ID : null, published ? PUBLISHED_AT.minusDays(1) : null, version, TENANT_ID);
    }

    private void insertProductType(long revisionId, String code, String name) {
        jdbcTemplate.update("INSERT INTO srv_inspection_rule_product_type_revision "
                        + "(id, revision_id, product_type_code, product_type_name_snapshot, version, creator, updater, tenant_id) "
                        + "VALUES (?, ?, ?, ?, 0, 'it', 'it', ?)",
                IdWorker.getId(), revisionId, code, name, TENANT_ID);
    }

    private void insertCommand(long revisionId) {
        jdbcTemplate.update("INSERT INTO srv_inspection_rule_command_revision "
                        + "(id, revision_id, stable_command_key, command_content, execution_order, timeout_seconds, "
                        + "continue_on_timeout, version, creator, updater, tenant_id) "
                        + "VALUES (?, ?, 'cpu', 'show cpu', 1, 30, b'0', 0, 'it', 'it', ?)",
                IdWorker.getId(), revisionId, TENANT_ID);
    }

    private void insertReview(
            long reviewId,
            long revisionId,
            String contentDigest,
            String conclusion,
            LocalDateTime reviewedAt) {
        jdbcTemplate.update("INSERT INTO srv_inspection_rule_security_review "
                        + "(id, review_reference, revision_id, content_digest, reviewed_by, permission_code, "
                        + "authorization_type, authorization_source_id, conclusion_code, reviewed_at, version, "
                        + "creator, updater, tenant_id) "
                        + "VALUES (?, ?, ?, ?, ?, 'pms:inspection-rule:security-review', 'RBAC_PERMISSION', NULL, "
                        + "?, ?, 0, 'it', 'it', ?)",
                reviewId, "IT-REVIEW-" + reviewId, revisionId, contentDigest, ACTOR_ID,
                conclusion, reviewedAt, TENANT_ID);
    }

    private static DictDataRespDTO dictData(String dictType, String value, String label) {
        DictDataRespDTO data = new DictDataRespDTO();
        data.setDictType(dictType);
        data.setValue(value);
        data.setLabel(label);
        data.setStatus(0);
        return data;
    }

    private void assertRevision(long revisionId, String status, int version) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status_code, version FROM srv_inspection_rule_revision WHERE tenant_id=? AND id=?",
                TENANT_ID, revisionId);
        assertEquals(status, row.get("status_code"));
        assertEquals(version, ((Number) row.get("version")).intValue());
    }

    private long publishedCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM srv_inspection_rule_revision WHERE tenant_id=? AND rule_id=? "
                        + "AND status_code='PUBLISHED'",
                Long.class, TENANT_ID, ruleId);
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
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            InspectionRuleManagePermissionGuard.class,
            InspectionRuleContentDigestService.class,
            InspectionRuleRevisionServiceImpl.class,
            InspectionRulePublicationTransactionService.class})
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
            when(permissionApi.hasAnyPermissions(
                    org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(true);
            return permissionApi;
        }

        @Bean
        PublicationWriteFault publicationWriteFault() {
            return new PublicationWriteFault();
        }

        @Bean
        @Primary
        InspectionRuleRevisionMapper faultingRevisionMapper(
                @Qualifier("inspectionRuleRevisionMapper") InspectionRuleRevisionMapper delegate,
                PublicationWriteFault fault) {
            return proxy(InspectionRuleRevisionMapper.class, delegate, (method, arguments) ->
                    fault.publishAfterWrite
                            && "publishDraftIfMatch".equals(method)
                            && arguments != null
                            && arguments.length == 1
                            && arguments[0] instanceof InspectionRulePublishUpdate,
                    "INSPECTION_RULE_PUBLISH_WRITE_FAILED_TEST");
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

    static final class PublicationWriteFault {
        volatile boolean publishAfterWrite;

        void clear() {
            publishAfterWrite = false;
        }
    }
}
