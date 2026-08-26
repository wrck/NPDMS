package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangePageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeProcessQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeVersionUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanVersionUpdate;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ConstructionPlanMapperTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConstructionPlanMapperTest {

    @Resource
    private ConstructionPlanMapper planMapper;
    @Resource
    private ConstructionPlanRevisionMapper revisionMapper;
    @Resource
    private ConstructionPlanChangeMapper changeMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private TransactionTemplate transactionTemplate;

    private long projectId;
    private Long planId;

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
        long seed = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        projectId = 977_000_000_000L + seed;
        ConstructionPlanDO plan = new ConstructionPlanDO();
        plan.setProjectId(projectId);
        plan.setPlanRecalculationStatusCode(ConstructionPlanDO.RECALCULATION_PENDING);
        plan.setVersion(0);
        plan.setCreator("fsol001-task3-test");
        plan.setUpdater("fsol001-task3-test");
        plan.setDeleted(false);
        plan.setTenantId(0L);
        assertEquals(1, planMapper.insert(plan));
        planId = plan.getId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("UPDATE sol_construction_plan SET current_duration_revision_id=NULL, "
                + "pending_change_id=NULL, plan_recalculation_source_revision_id=NULL WHERE id=?", planId);
        jdbcTemplate.update("DELETE FROM sol_construction_plan_change WHERE tenant_id=0 AND plan_id=?", planId);
        jdbcTemplate.update("DELETE FROM sol_construction_plan_revision WHERE tenant_id=0 AND plan_id=?", planId);
        jdbcTemplate.update("DELETE FROM sol_construction_plan WHERE tenant_id=0 AND id=?", planId);
    }

    @Test
    void shouldKeepOneRootAndCasCompletePlanPointers() {
        ConstructionPlanRevisionDO revision = insertRevision(1, LocalDate.of(2026, 8, 1), 3);

        assertEquals(planId, planMapper.selectByProjectId(0L, projectId).getId());
        assertNull(planMapper.selectByProjectId(1L, projectId));
        assertEquals(1, planMapper.updateVersionIfMatch(new ConstructionPlanVersionUpdate(
                0L, planId, 0, revision.getId(), null,
                ConstructionPlanDO.RECALCULATION_PENDING, revision.getId(), "fsol001-task3-test")));
        assertEquals(0, planMapper.updateVersionIfMatch(new ConstructionPlanVersionUpdate(
                0L, planId, 0, revision.getId(), null,
                ConstructionPlanDO.RECALCULATION_PENDING, revision.getId(), "fsol001-task3-test")));

        ConstructionPlanDO locked = transactionTemplate.execute(status ->
                planMapper.selectForUpdate(new ConstructionPlanLockQuery(0L, planId)));
        assertEquals(revision.getId(), locked.getCurrentDurationRevisionId());
        assertEquals(revision.getId(), locked.getPlanRecalculationSourceRevisionId());
        assertEquals(1, locked.getVersion());

        ConstructionPlanDO duplicate = new ConstructionPlanDO();
        duplicate.setProjectId(projectId);
        duplicate.setPlanRecalculationStatusCode(ConstructionPlanDO.RECALCULATION_PENDING);
        duplicate.setVersion(0);
        duplicate.setDeleted(true);
        duplicate.setTenantId(0L);
        assertThrows(DataIntegrityViolationException.class, () -> planMapper.insert(duplicate));
    }

    @Test
    void shouldLockLatestRevisionAndPageByRevisionNumberAndId() {
        ConstructionPlanRevisionDO first = insertRevision(1, LocalDate.of(2026, 8, 1), 3);
        ConstructionPlanRevisionDO second = insertRevision(2, LocalDate.of(2026, 8, 4), 2);
        ConstructionPlanRevisionDO third = insertRevision(3, LocalDate.of(2026, 8, 6), 4);

        ConstructionPlanRevisionDO latest = transactionTemplate.execute(status ->
                revisionMapper.selectLatestForUpdate(new ConstructionPlanLockQuery(0L, planId)));
        assertEquals(third.getId(), latest.getId());
        assertEquals(second.getId(), transactionTemplate.execute(status -> revisionMapper.selectForUpdate(
                new ConstructionPlanRevisionLockQuery(0L, planId, second.getId()))).getId());

        var page1 = revisionMapper.selectPage(new ConstructionPlanRevisionPageQuery(
                0L, planId, null, null, 2));
        assertEquals(java.util.List.of(third.getId(), second.getId()),
                page1.stream().map(ConstructionPlanRevisionDO::getId).toList());
        var page2 = revisionMapper.selectPage(new ConstructionPlanRevisionPageQuery(
                0L, planId, second.getRevisionNo(), second.getId(), 2));
        assertEquals(java.util.List.of(first.getId()),
                page2.stream().map(ConstructionPlanRevisionDO::getId).toList());
        assertTrue(revisionMapper.selectPage(new ConstructionPlanRevisionPageQuery(
                0L, planId, second.getRevisionNo(), null, 2)).isEmpty());

        ConstructionPlanRevisionDO duplicate = revision(3, LocalDate.of(2026, 9, 1), 1);
        assertThrows(DataIntegrityViolationException.class, () -> revisionMapper.insert(duplicate));
    }

    @Test
    void shouldKeepUniqueCandidateAndProcessAndCasChange() {
        ConstructionPlanRevisionDO base = insertRevision(1, LocalDate.of(2026, 8, 1), 3);
        ConstructionPlanRevisionDO candidate = insertRevision(2, LocalDate.of(2026, 8, 4), 4);
        ConstructionPlanChangeDO change = insertChange(base.getId(), candidate.getId(),
                LocalDateTime.of(2026, 8, 10, 9, 0));

        assertEquals(change.getId(), transactionTemplate.execute(status -> changeMapper.selectForUpdate(
                new ConstructionPlanChangeLockQuery(0L, planId, change.getId()))).getId());
        assertEquals(1, changeMapper.updateVersionIfMatch(changeUpdate(
                change, 0, "PENDING_APPROVAL", "process-" + change.getId())));
        assertEquals(0, changeMapper.updateVersionIfMatch(changeUpdate(
                change, 0, "PENDING_APPROVAL", "process-" + change.getId())));
        assertEquals(change.getId(), changeMapper.selectByProcessInstanceId(
                new ConstructionPlanChangeProcessQuery(0L, "process-" + change.getId())).getId());
        assertNull(changeMapper.selectByProcessInstanceId(
                new ConstructionPlanChangeProcessQuery(1L, "process-" + change.getId())));

        ConstructionPlanChangeDO duplicateCandidate = change(base.getId(), candidate.getId(),
                LocalDateTime.of(2026, 8, 10, 10, 0));
        assertThrows(DataIntegrityViolationException.class, () -> changeMapper.insert(duplicateCandidate));

        ConstructionPlanRevisionDO anotherCandidate = insertRevision(3, LocalDate.of(2026, 8, 8), 2);
        ConstructionPlanChangeDO another = insertChange(base.getId(), anotherCandidate.getId(),
                LocalDateTime.of(2026, 8, 10, 11, 0));
        assertThrows(DataIntegrityViolationException.class, () -> changeMapper.updateVersionIfMatch(
                changeUpdate(another, 0, "PENDING_APPROVAL", "process-" + change.getId())));
    }

    @Test
    void shouldPageChangesByCreatedTimeAndId() {
        ConstructionPlanRevisionDO base = insertRevision(1, LocalDate.of(2026, 8, 1), 3);
        ConstructionPlanRevisionDO candidate1 = insertRevision(2, LocalDate.of(2026, 8, 4), 2);
        ConstructionPlanRevisionDO candidate2 = insertRevision(3, LocalDate.of(2026, 8, 6), 2);
        ConstructionPlanRevisionDO candidate3 = insertRevision(4, LocalDate.of(2026, 8, 8), 2);
        LocalDateTime firstTime = LocalDateTime.of(2026, 8, 10, 9, 0);
        LocalDateTime sameLaterTime = LocalDateTime.of(2026, 8, 11, 9, 0);
        ConstructionPlanChangeDO first = insertChange(base.getId(), candidate1.getId(), firstTime);
        ConstructionPlanChangeDO second = insertChange(base.getId(), candidate2.getId(), sameLaterTime);
        ConstructionPlanChangeDO third = insertChange(base.getId(), candidate3.getId(), sameLaterTime);

        var page1 = changeMapper.selectPage(new ConstructionPlanChangePageQuery(
                0L, planId, null, null, 2));
        assertEquals(java.util.List.of(third.getId(), second.getId()),
                page1.stream().map(ConstructionPlanChangeDO::getId).toList());
        var page2 = changeMapper.selectPage(new ConstructionPlanChangePageQuery(
                0L, planId, second.getCreatedAt(), second.getId(), 2));
        assertEquals(java.util.List.of(first.getId()),
                page2.stream().map(ConstructionPlanChangeDO::getId).toList());
        assertTrue(changeMapper.selectPage(new ConstructionPlanChangePageQuery(
                0L, planId, second.getCreatedAt(), null, 2)).isEmpty());
    }

    private ConstructionPlanRevisionDO insertRevision(int revisionNo, LocalDate startDate, int durationDays) {
        ConstructionPlanRevisionDO revision = revision(revisionNo, startDate, durationDays);
        assertEquals(1, revisionMapper.insert(revision));
        assertNotNull(revision.getId());
        return revision;
    }

    private ConstructionPlanRevisionDO revision(int revisionNo, LocalDate startDate, int durationDays) {
        ConstructionPlanRevisionDO revision = new ConstructionPlanRevisionDO();
        revision.setPlanId(planId);
        revision.setRevisionNo(revisionNo);
        revision.setCalculationBasisCode(ConstructionPlanRevisionDO.BASIS_DURATION_FROM_START);
        revision.setStartDate(startDate);
        revision.setEndDate(startDate.plusDays(durationDays - 1L));
        revision.setDurationDays(durationDays);
        revision.setCreatedBy(8_000_001L);
        revision.setCreatedAt(LocalDateTime.now());
        revision.setVersion(0);
        revision.setTenantId(0L);
        return revision;
    }

    private ConstructionPlanChangeDO insertChange(Long baseRevisionId, Long candidateRevisionId,
                                                   LocalDateTime createdAt) {
        ConstructionPlanChangeDO change = change(baseRevisionId, candidateRevisionId, createdAt);
        assertEquals(1, changeMapper.insert(change));
        assertNotNull(change.getId());
        return change;
    }

    private ConstructionPlanChangeDO change(Long baseRevisionId, Long candidateRevisionId,
                                             LocalDateTime createdAt) {
        ConstructionPlanChangeDO change = new ConstructionPlanChangeDO();
        change.setPlanId(planId);
        change.setBaseRevisionId(baseRevisionId);
        change.setCandidateRevisionId(candidateRevisionId);
        change.setStatusCode(ConstructionPlanChangeDO.STATUS_DRAFT);
        change.setReasonTypeCode("CUSTOMER_DELAY");
        change.setReasonDetail("Task 3持久化验证");
        change.setCustomerEvidenceRequired(false);
        change.setApplicantUserId(8_000_001L);
        change.setCreatedAt(createdAt);
        change.setVersion(0);
        change.setTenantId(0L);
        return change;
    }

    private ConstructionPlanChangeVersionUpdate changeUpdate(ConstructionPlanChangeDO change,
                                                               int expectedVersion, String status,
                                                               String processInstanceId) {
        return new ConstructionPlanChangeVersionUpdate(
                0L, planId, change.getId(), expectedVersion, status,
                change.getReasonTypeCode(), change.getReasonDetail(), false,
                null, null, "pms-sol-duration-change", processInstanceId,
                LocalDateTime.now(), 8_100_001L, null, null);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量：" + name);
        }
        return value;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class})
    static class TestApplication {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

}
