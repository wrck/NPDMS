package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.SatisfactionResultFactApi;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionResultFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.PendingArchiveSourceTypeQuery;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.event.SatisfactionResultVersionChangedMessage;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = SatisfactionResultSourceProjectionMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SatisfactionResultSourceProjectionMySqlIntegrationTest {
    private static final long TENANT_ID = 0L;
    private static final long ACTOR_ID = 992_004_800_001L;

    @Resource SatisfactionResultSourceProjectionService service;
    @Resource JdbcTemplate jdbcTemplate;
    @Resource ProjectDeliverableSourceVersionMapper sourceMapper;
    @MockitoBean ProjectWorkBindingFactApi workBindingFactApi;
    @MockitoBean SatisfactionResultFactApi resultFactApi;

    private long projectId;
    private long rootId;
    private long sourceId;

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
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        long seed = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        projectId = 979_005_000_000L + seed * 10L;
        rootId = projectId + 1;
        sourceId = projectId + 2;
        insertRoot(sourceId);
        insertSource(sourceId, 101L, "CURRENT");
        when(workBindingFactApi.lockAndRevalidateSatisfactionTask(any())).thenReturn(
                new ProjectSatisfactionTaskFact(projectId, projectId + 9, "T-SAT-SURVEY", 7,
                        "AFTER_INITIAL_ACCEPTANCE", 30L, 31L, 1, "RULE-1",
                        new BigDecimal("4.00"), ACTOR_ID));
        when(resultFactApi.lockAndRevalidate(any())).thenReturn(invalidatedFact(101L));
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update("DELETE FROM acc_project_deliverable_source_attachment "
                    + "WHERE deliverable_source_version_id IN (SELECT id FROM acc_project_deliverable_source_version "
                    + "WHERE deliverable_id=?)", rootId);
            jdbcTemplate.update("DELETE FROM acc_project_deliverable_source_version WHERE deliverable_id=?", rootId);
            jdbcTemplate.update("DELETE FROM acc_project_deliverable WHERE id=?", rootId);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void invalidatedCurrentSourceClearsRootPointerInMySql() {
        service.project(invalidatedEvent(101L));

        assertEquals("REVOKED", sourceStatus(sourceId));
        assertNull(currentSourceId());
        assertEquals("INVALID", rootArchiveStatus());
    }

    @Test
    void invalidatingOldSourceDoesNotClearNewCurrentSource() {
        long newerSourceId = projectId + 3;
        jdbcTemplate.update("UPDATE acc_project_deliverable_source_version SET relation_status='SUPERSEDED' WHERE id=?",
                sourceId);
        insertSource(newerSourceId, 102L, "CURRENT");
        jdbcTemplate.update("UPDATE acc_project_deliverable SET current_source_version_id=? WHERE id=?",
                newerSourceId, rootId);

        service.project(invalidatedEvent(101L));

        assertEquals("REVOKED", sourceStatus(sourceId));
        assertEquals(newerSourceId, currentSourceId());
    }

    @Test
    void repeatedInvalidatedEventIsIdempotent() {
        SatisfactionResultVersionChangedMessage event = invalidatedEvent(101L);
        service.project(event);
        service.project(event);

        assertEquals("REVOKED", sourceStatus(sourceId));
        assertNull(currentSourceId());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT version FROM acc_project_deliverable WHERE id=?", Integer.class, rootId));
    }

    @Test
    void recordedFilesUseGlobalSourceSequenceAndReplayWithoutDuplicates() {
        long resultId = 102L;
        when(resultFactApi.lockAndRevalidate(any())).thenReturn(effectiveFact(resultId));
        SatisfactionResultVersionChangedMessage event = recordedEvent(resultId);

        service.project(event);
        service.project(event);

        Long recordedSourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM acc_project_deliverable_source_version WHERE deliverable_id=? "
                        + "AND source_object_id=? AND source_version=2", Long.class, rootId, resultId);
        assertEquals(List.of(1, 2, 3), jdbcTemplate.queryForList(
                "SELECT attachment_sequence FROM acc_project_deliverable_source_attachment "
                        + "WHERE deliverable_source_version_id=? ORDER BY attachment_sequence",
                Integer.class, recordedSourceId));
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM acc_project_deliverable_source_attachment "
                        + "WHERE deliverable_source_version_id=?", Integer.class, recordedSourceId));
        assertEquals(true, sourceMapper.selectPendingArchiveBySourceType(new PendingArchiveSourceTypeQuery(
                        TENANT_ID, "SatisfactionResult", Set.of("CURRENT", "SUPERSEDED", "REVOKED"), 20))
                .stream().anyMatch(row -> recordedSourceId.equals(row.getId())));
    }

    private void insertRoot(long currentSourceId) {
        jdbcTemplate.update("INSERT INTO acc_project_deliverable "
                        + "(id,project_id,deliverable_code,name,stage_code,task_code,required,status,"
                        + "current_source_version_id,archive_status,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,'D-SAT-REPORT','满意度报告','S5','T-SAT-SURVEY',b'1','PENDING',?,"
                        + "'PENDING_COMPENSATION',0,'facc002_it','facc002_it',b'0',?)",
                rootId, projectId, currentSourceId, TENANT_ID);
    }

    private void insertSource(long id, long resultId, String relationStatus) {
        jdbcTemplate.update("INSERT INTO acc_project_deliverable_source_version "
                        + "(id,deliverable_id,source_requirement_id,source_object_type,source_object_id,source_version,"
                        + "relation_status,archive_status,archive_retry_count,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,'ACC-04@V1','SatisfactionResult',?,1,?,'PENDING_COMPENSATION',0,"
                        + "'facc002_it','facc002_it',b'0',?)",
                id, rootId, resultId, relationStatus, TENANT_ID);
    }

    private SatisfactionResultVersionChangedMessage invalidatedEvent(long resultId) {
        return new SatisfactionResultVersionChangedMessage("invalidate-" + resultId, "INVALIDATED", TENANT_ID,
                projectId, projectId + 9, 7, "T-SAT-SURVEY", "SAT-10", 1, projectId + 8,
                projectId + 7, projectId + 6, resultId, 1, 1, 31L, "RULE-1",
                new BigDecimal("4.00"), "ACC", "AcceptanceActivity", "100", 1L,
                true, "INVALIDATED", ACTOR_ID, "OWNER_INVALIDATED", ACTOR_ID,
                LocalDateTime.of(2026, 8, 30, 12, 0), List.of());
    }

    private SatisfactionResultVersionChangedMessage recordedEvent(long resultId) {
        return new SatisfactionResultVersionChangedMessage("recorded-" + resultId, "RECORDED", TENANT_ID,
                projectId, projectId + 9, 7, "T-SAT-SURVEY", "SAT-10", 1, projectId + 8,
                projectId + 7, projectId + 6, resultId, 2, 0, 31L, "RULE-1",
                new BigDecimal("4.00"), "ACC", "AcceptanceActivity", "100", 1L,
                true, "EFFECTIVE", ACTOR_ID, null, null, null, List.of(
                file("RESULT_DOCUMENT", 1, 1, 201L, "result-doc"),
                file("SIGNATURE", 1, 2, 202L, "signature"),
                file("ATTACHMENT", 1, 3, 203L, "attachment")));
    }

    private SatisfactionResultVersionChangedMessage.FileFact file(String role, int sequence, int sourceSequence,
                                                                    long artifactId, String referenceKey) {
        return new SatisfactionResultVersionChangedMessage.FileFact(role, sequence, sourceSequence, artifactId, 1,
                referenceKey, 1, 0, 0, 3L, "a".repeat(64));
    }

    private SatisfactionResultFact invalidatedFact(long resultId) {
        return new SatisfactionResultFact("FOUND", "SAT-10", projectId + 8, 1, projectId + 7,
                projectId + 6, resultId, 1, 31L, "RULE-1", new BigDecimal("4.00"),
                "ACC", "AcceptanceActivity", "100", 1L, true, "INVALIDATED",
                "PENDING_COMPENSATION", 1);
    }

    private SatisfactionResultFact effectiveFact(long resultId) {
        return new SatisfactionResultFact("FOUND", "SAT-10", projectId + 8, 1, projectId + 7,
                projectId + 6, resultId, 2, 31L, "RULE-1", new BigDecimal("4.00"),
                "ACC", "AcceptanceActivity", "100", 1L, true, "EFFECTIVE",
                "PENDING_COMPENSATION", 0);
    }

    private String sourceStatus(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT relation_status FROM acc_project_deliverable_source_version WHERE id=?", String.class, id);
    }

    private Long currentSourceId() {
        return jdbcTemplate.queryForObject(
                "SELECT current_source_version_id FROM acc_project_deliverable WHERE id=?", Long.class, rootId);
    }

    private String rootArchiveStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT archive_status FROM acc_project_deliverable WHERE id=?", String.class, rootId);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance",
            "cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            SatisfactionResultSourceProjectionService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
    }
}
