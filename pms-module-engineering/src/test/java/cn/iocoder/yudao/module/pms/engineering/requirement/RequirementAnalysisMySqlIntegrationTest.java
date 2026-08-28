package cn.iocoder.yudao.module.pms.engineering.requirement;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.RequirementAnalysisSectionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisSectionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisCompleteUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisContentUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisEffectiveClearUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisProjectQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionPatchUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionRowQuery;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = RequirementAnalysisMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RequirementAnalysisMySqlIntegrationTest {

    private static final long TENANT_ID = 0L;
    private static final long ACTOR_ID = 9_900_003L;
    private static final AtomicLong PROJECT_SEQUENCE = new AtomicLong(8_300_000_000L);

    @Resource PreparationMapper preparationMapper;
    @Resource RequirementAnalysisRootMapper rootMapper;
    @Resource RequirementAnalysisSectionMapper sectionMapper;
    @Resource JdbcTemplate jdbcTemplate;
    @Resource TransactionTemplate transactionTemplate;

    private long projectId;

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
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        projectId = PROJECT_SEQUENCE.incrementAndGet();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE s FROM sol_requirement_analysis_section s "
                + "JOIN sol_preparation p ON p.tenant_id=s.tenant_id AND p.id=s.preparation_id "
                + "WHERE p.tenant_id=? AND p.project_id=?", TENANT_ID, projectId);
        jdbcTemplate.update("DELETE FROM sol_preparation WHERE tenant_id=? AND project_id=? "
                + "AND source_preparation_id IS NOT NULL", TENANT_ID, projectId);
        jdbcTemplate.update("DELETE FROM sol_preparation WHERE tenant_id=? AND project_id=?",
                TENANT_ID, projectId);
        TenantContextHolder.clear();
    }

    @Test
    void enforcesOneDraftAndOneEffectiveVersionPerProject() {
        insertRoot(root(1, "DRAFT", 1, null));

        assertThrows(DataIntegrityViolationException.class,
                () -> insertRoot(root(2, "DRAFT", 1, null)));

        insertRoot(root(2, "COMPLETED", null, 1));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertRoot(root(3, "COMPLETED", null, 1)));

        assertEquals(1, countMarker("draft_marker"));
        assertEquals(1, countMarker("effective_marker"));
    }

    @Test
    void sectionAndRootCasEachAdvanceOnlyOnce() {
        PreparationDO draft = insertRoot(root(1, "DRAFT", 1, null));
        RequirementAnalysisSectionDO section = insertSection(section(draft.getId(), "CORE_BACKGROUND"));

        int sectionWon = sectionMapper.patchIfMatch(new RequirementAnalysisSectionPatchUpdate(
                TENANT_ID, draft.getId(), section.getId(), 0,
                true, "{\"text\":\"updated\"}", false, null, String.valueOf(ACTOR_ID)));
        int sectionStale = sectionMapper.patchIfMatch(new RequirementAnalysisSectionPatchUpdate(
                TENANT_ID, draft.getId(), section.getId(), 0,
                true, "{\"text\":\"stale\"}", false, null, String.valueOf(ACTOR_ID)));
        int rootWon = rootMapper.incrementContentIfMatch(new RequirementAnalysisContentUpdate(
                TENANT_ID, draft.getId(), 0, 0, String.valueOf(ACTOR_ID)));
        int rootStale = rootMapper.incrementContentIfMatch(new RequirementAnalysisContentUpdate(
                TENANT_ID, draft.getId(), 0, 0, String.valueOf(ACTOR_ID)));

        RequirementAnalysisSectionDO storedSection = sectionMapper.selectById(
                new RequirementAnalysisSectionRowQuery(TENANT_ID, draft.getId(), section.getId()));
        PreparationDO storedRoot = rootMapper.selectById(new RequirementAnalysisRowQuery(TENANT_ID, draft.getId()));
        assertEquals(1, sectionWon);
        assertEquals(0, sectionStale);
        assertEquals(1, rootWon);
        assertEquals(0, rootStale);
        assertEquals(1, storedSection.getVersion());
        assertTrue(storedSection.getValueSnapshot().contains("updated"));
        assertEquals(1, storedRoot.getVersion());
        assertEquals(1, storedRoot.getContentVersion());
    }

    @Test
    void completionSwitchesEffectivePointerWithoutMutatingHistoricalContent() {
        PreparationDO historical = insertRoot(root(1, "COMPLETED", null, 1));
        RequirementAnalysisSectionDO historicalSection = insertSection(
                section(historical.getId(), "CORE_BACKGROUND"));
        RequirementAnalysisSectionDO frozenHistoricalSection = sectionMapper.selectById(
                new RequirementAnalysisSectionRowQuery(TENANT_ID, historical.getId(), historicalSection.getId()));
        PreparationDO draft = insertRoot(root(2, "DRAFT", 1, null));
        insertSection(section(draft.getId(), "CORE_BACKGROUND"));
        LocalDateTime completedAt = LocalDateTime.now().withNano(123_000_000);

        transactionTemplate.executeWithoutResult(status -> {
            PreparationDO lockedHistorical = rootMapper.selectEffectiveForUpdate(
                    new RequirementAnalysisProjectQuery(TENANT_ID, projectId));
            PreparationDO lockedDraft = rootMapper.selectDraftForUpdate(
                    new RequirementAnalysisProjectQuery(TENANT_ID, projectId));
            assertEquals(1, rootMapper.clearEffectiveIfMatch(new RequirementAnalysisEffectiveClearUpdate(
                    TENANT_ID, lockedHistorical.getId(), lockedHistorical.getVersion(), String.valueOf(ACTOR_ID))));
            assertEquals(1, rootMapper.completeDraftIfMatch(new RequirementAnalysisCompleteUpdate(
                    TENANT_ID, lockedDraft.getId(), lockedDraft.getVersion(), lockedDraft.getContentVersion(),
                    ACTOR_ID, completedAt, String.valueOf(ACTOR_ID))));
        });

        PreparationDO storedHistorical = rootMapper.selectById(
                new RequirementAnalysisRowQuery(TENANT_ID, historical.getId()));
        RequirementAnalysisSectionDO storedHistoricalSection = sectionMapper.selectById(
                new RequirementAnalysisSectionRowQuery(TENANT_ID, historical.getId(), historicalSection.getId()));
        PreparationDO effective = rootMapper.selectEffective(new RequirementAnalysisProjectQuery(TENANT_ID, projectId));
        assertNull(storedHistorical.getEffectiveMarker());
        assertEquals("COMPLETED", storedHistorical.getStatusCode());
        assertEquals(historical.getCompletedBy(), storedHistorical.getCompletedBy());
        assertEquals(historical.getCompletedAt(), storedHistorical.getCompletedAt());
        assertEquals(frozenHistoricalSection.getValueSnapshot(), storedHistoricalSection.getValueSnapshot());
        assertEquals(frozenHistoricalSection.getAttachmentReferenceSnapshot(),
                storedHistoricalSection.getAttachmentReferenceSnapshot());
        assertEquals(draft.getId(), effective.getId());
        assertEquals("COMPLETED", effective.getStatusCode());
        assertNull(effective.getDraftMarker());
        assertEquals(1, effective.getEffectiveMarker());
        assertEquals(ACTOR_ID, effective.getCompletedBy());
        assertEquals(completedAt, effective.getCompletedAt());
    }

    @Test
    void failedCompletionRollsBackEffectivePointerChange() {
        PreparationDO historical = insertRoot(root(1, "COMPLETED", null, 1));
        PreparationDO draft = insertRoot(root(2, "DRAFT", 1, null));

        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            assertEquals(1, rootMapper.clearEffectiveIfMatch(new RequirementAnalysisEffectiveClearUpdate(
                    TENANT_ID, historical.getId(), 0, String.valueOf(ACTOR_ID))));
            int completed = rootMapper.completeDraftIfMatch(new RequirementAnalysisCompleteUpdate(
                    TENANT_ID, draft.getId(), 99, 0, ACTOR_ID, LocalDateTime.now(), String.valueOf(ACTOR_ID)));
            if (completed != 1) {
                throw new IllegalStateException("stale draft must roll back the whole effective switch");
            }
        }));

        PreparationDO stillEffective = rootMapper.selectEffective(
                new RequirementAnalysisProjectQuery(TENANT_ID, projectId));
        PreparationDO stillDraft = rootMapper.selectDraft(new RequirementAnalysisProjectQuery(TENANT_ID, projectId));
        assertEquals(historical.getId(), stillEffective.getId());
        assertEquals(0, stillEffective.getVersion());
        assertEquals(draft.getId(), stillDraft.getId());
        assertEquals("DRAFT", stillDraft.getStatusCode());
        assertEquals(0, stillDraft.getVersion());
    }

    @Test
    void concurrentCompletionHasExactlyOneWinner() throws Exception {
        PreparationDO draft = insertRoot(root(1, "DRAFT", 1, null));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> attempts = List.of(pool.submit(() -> completeAfter(start, draft)),
                    pool.submit(() -> completeAfter(start, draft)));
            start.countDown();
            int winners = attempts.get(0).get(10, TimeUnit.SECONDS)
                    + attempts.get(1).get(10, TimeUnit.SECONDS);

            assertEquals(1, winners);
            PreparationDO effective = rootMapper.selectEffective(
                    new RequirementAnalysisProjectQuery(TENANT_ID, projectId));
            assertNotNull(effective);
            assertEquals(draft.getId(), effective.getId());
            assertEquals(1, effective.getVersion());
            assertNull(rootMapper.selectDraft(new RequirementAnalysisProjectQuery(TENANT_ID, projectId)));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private int completeAfter(CountDownLatch start, PreparationDO draft) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        Integer result = transactionTemplate.execute(status -> rootMapper.completeDraftIfMatch(
                new RequirementAnalysisCompleteUpdate(TENANT_ID, draft.getId(), 0, 0,
                        ACTOR_ID, LocalDateTime.now(), String.valueOf(ACTOR_ID))));
        return result == null ? 0 : result;
    }

    private PreparationDO root(int businessVersion, String status, Integer draftMarker, Integer effectiveMarker) {
        PreparationDO row = new PreparationDO();
        row.setProjectId(projectId);
        row.setPreparationTypeCode("PRE_04_REQUIREMENT_ANALYSIS");
        row.setBusinessVersion(businessVersion);
        row.setCurrentMarker(null);
        row.setDraftMarker(draftMarker);
        row.setEffectiveMarker(effectiveMarker);
        row.setTemplateId(401L);
        row.setTemplateRevisionId(1L);
        row.setTemplateSnapshot("{\"templateCode\":\"IT_PRE04\"}");
        row.setFixedFormCatalogVersion(1);
        row.setStatusCode(status);
        row.setReadinessStatusCode("NOT_READY");
        row.setInputVersion(0);
        row.setReadinessVersion(0);
        row.setSnapshotCurrent(false);
        row.setContentVersion(0);
        row.setCompletedBy("COMPLETED".equals(status) ? ACTOR_ID : null);
        row.setCompletedAt("COMPLETED".equals(status) ? LocalDateTime.now().withNano(0) : null);
        row.setVersion(0);
        row.setCreator(String.valueOf(ACTOR_ID));
        row.setUpdater(String.valueOf(ACTOR_ID));
        row.setTenantId(TENANT_ID);
        return row;
    }

    private PreparationDO insertRoot(PreparationDO row) {
        assertEquals(1, preparationMapper.insert(row));
        assertNotNull(row.getId());
        return row;
    }

    private RequirementAnalysisSectionDO section(Long preparationId, String code) {
        RequirementAnalysisSectionDO row = new RequirementAnalysisSectionDO();
        row.setPreparationId(preparationId);
        row.setSectionCode(code);
        row.setSectionName("项目背景");
        row.setSectionKindCode("CORE");
        row.setFieldTypeCode("RICH_TEXT");
        row.setRequiredFlag(true);
        row.setSortOrder(10);
        row.setSchemaSnapshot("{\"type\":\"richText\"}");
        row.setValueSnapshot("{\"text\":\"original\"}");
        row.setAttachmentReferenceSnapshot("[]");
        row.setVersion(0);
        row.setCreator(String.valueOf(ACTOR_ID));
        row.setUpdater(String.valueOf(ACTOR_ID));
        row.setTenantId(TENANT_ID);
        return row;
    }

    private RequirementAnalysisSectionDO insertSection(RequirementAnalysisSectionDO row) {
        assertEquals(1, sectionMapper.insert(row));
        assertNotNull(row.getId());
        return row;
    }

    private int countMarker(String marker) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_preparation WHERE tenant_id=? "
                        + "AND project_id=? AND preparation_type_code='PRE_04_REQUIREMENT_ANALYSIS' AND "
                        + marker + "=1", Integer.class, TENANT_ID, projectId);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing environment variable: " + name);
        }
        return value;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan("cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class})
    static class TestApplication {

        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }

        @Bean TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner = new TenantLineInnerInterceptor(
                    new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }
    }
}
