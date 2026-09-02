package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.event.AcceptanceReportVersionChangedMessage;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.PendingArchiveSourceQuery;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = Facc001ApplicationMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class Facc001ApplicationMySqlIntegrationTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ID = 992_004_800_001L;
    private static final String KEY_PREFIX = "facc001-it-";

    @Resource AcceptanceReportCommandService service;
    @Resource AcceptanceReportSourceProjectionService projectionService;
    @Resource AcceptanceReportArchiveCompensationService archiveService;
    @Resource AcceptanceReportArchiveCompensationJob archiveJob;
    @Resource JdbcTemplate jdbcTemplate;
    @MockitoBean FileArtifactApi fileArtifactApi;
    @MockitoBean ProjectScopeApi projectScopeApi;
    @MockitoSpyBean ProjectDeliverableSourceVersionMapper sourceMapper;

    private long projectId;
    private long activityId;
    private AcceptanceReportCommands.Actor actor;

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
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        long seed = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        projectId = 979_004_000_000L + seed * 10L;
        activityId = projectId + 1;
        actor = new AcceptanceReportCommands.Actor(TENANT_ID, USER_ID, KEY_PREFIX + activityId);
        jdbcTemplate.update("INSERT INTO acc_acceptance "
                        + "(id,project_id,project_task_id,execution_contract_id,acceptance_type,activity_status,"
                        + "current_report_version_id,version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?,?,?, 'PRELIMINARY','PENDING',NULL,0,'facc001_it','facc001_it',b'0',?)",
                activityId, projectId, projectId + 2, projectId + 3, TENANT_ID);
        jdbcTemplate.update("INSERT INTO acc_project_deliverable "
                        + "(id,project_id,deliverable_code,name,stage_code,required,status,current_source_version_id,archive_status,"
                        + "version,creator,updater,deleted,tenant_id) "
                        + "VALUES (?,?, 'D-INITIAL-REPORT','初验报告','S5',b'1','PENDING',NULL,NULL,0,'facc001_it','facc001_it',b'0',?)",
                projectId + 4, projectId, TENANT_ID);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(projectId, 1L, Set.of(projectId), Set.of()));
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update("DELETE FROM plt_outbox_event WHERE aggregate_key=?", String.valueOf(activityId));
            jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE aggregate_key=?", String.valueOf(activityId));
            jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE idempotency_key LIKE ?",
                    KEY_PREFIX + activityId + "%");
            jdbcTemplate.update("DELETE FROM acc_acceptance_report_attachment WHERE report_version_id IN "
                    + "(SELECT id FROM acc_acceptance_report_version WHERE acceptance_id=?)", activityId);
            jdbcTemplate.update("DELETE FROM acc_project_deliverable_source_attachment WHERE deliverable_source_version_id IN "
                    + "(SELECT id FROM acc_project_deliverable_source_version WHERE deliverable_id=?)", projectId + 4);
            jdbcTemplate.update("DELETE FROM acc_project_deliverable_source_version WHERE deliverable_id=?", projectId + 4);
            jdbcTemplate.update("DELETE FROM acc_project_deliverable WHERE id=?", projectId + 4);
            jdbcTemplate.update("DELETE FROM acc_acceptance_report_version WHERE acceptance_id=?", activityId);
            jdbcTemplate.update("DELETE FROM acc_acceptance WHERE id=?", activityId);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void publishReplaceReplayAndRevokePreserveImmutableHistory() {
        var first = service.createDraft(new AcceptanceReportCommands.CreateDraftCommand(
                activityId, 0, completeContent("初验V1")), actor);
        stubAttachment(first.reportVersionId());
        var published = service.publish(publish(first, null, 0, "-publish-v1", "a"), actor);
        var replayed = service.publish(publish(first, null, 0, "-publish-v1", "a"), actor);
        assertTrue(replayed.replayed());
        assertEquals(published.reportVersionId(), replayed.reportVersionId());

        var second = service.createDraft(new AcceptanceReportCommands.CreateDraftCommand(
                activityId, 1, completeContent("初验V2")), actor);
        stubAttachment(second.reportVersionId());
        service.publish(publish(second, first.reportVersionId(), 1, "-publish-v2", "b"), actor);
        service.revoke(new AcceptanceReportCommands.RevokeCommand(activityId, 2,
                second.reportVersionId(), second.reportVersionNo(), key("-revoke-v2"), "c".repeat(64)), actor);

        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT report_version_no,report_status,current_marker FROM acc_acceptance_report_version "
                        + "WHERE acceptance_id=? ORDER BY report_version_no", activityId);
        assertEquals(2, history.size());
        assertEquals("SUPERSEDED", history.get(0).get("report_status"));
        assertEquals("REVOKED", history.get(1).get("report_status"));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM acc_acceptance_report_version WHERE acceptance_id=? AND current_marker=1",
                Long.class, activityId));
        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM acc_acceptance_report_attachment WHERE report_version_id IN "
                        + "(SELECT id FROM acc_acceptance_report_version WHERE acceptance_id=?)", Long.class, activityId));
        assertEquals(3L, eventCount("AcceptanceReportVersionChanged"));
        assertEquals(3L, eventCount("ClosureGateRecheckRequested"));
        assertEquals(3L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE aggregate_key=?", Long.class,
                String.valueOf(activityId)));
    }

    @Test
    void incompletePublishRollsBackIdempotencyAndOutbox() {
        var draft = service.createDraft(new AcceptanceReportCommands.CreateDraftCommand(
                activityId, 0, new AcceptanceReportCommands.DraftContent(null, null, null, null)), actor);
        assertThrows(RuntimeException.class, () -> service.publish(
                publish(draft, null, 0, "-incomplete", "d"), actor));
        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "SELECT report_status FROM acc_acceptance_report_version WHERE id=?", String.class,
                draft.reportVersionId()));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_idempotency_record WHERE idempotency_key=?", Long.class,
                key("-incomplete")));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_outbox_event WHERE aggregate_key=?", Long.class,
                String.valueOf(activityId)));
    }

    @Test
    void supersededPendingSourceRemainsArchivableWithoutOverwritingCurrentRootSummary() {
        var first = service.createDraft(new AcceptanceReportCommands.CreateDraftCommand(
                activityId, 0, completeContent("初验V1")), actor);
        FileArtifactVersionFact firstFile = stubAttachment(first.reportVersionId());
        service.publish(publish(first, null, 0, "-archive-v1", "f"), actor);
        projectionService.project(versionEvent("EFFECTIVE", first.reportVersionId(), null, 1, firstFile));

        var second = service.createDraft(new AcceptanceReportCommands.CreateDraftCommand(
                activityId, 1, completeContent("初验V2")), actor);
        FileArtifactVersionFact secondFile = stubAttachment(second.reportVersionId());
        service.publish(publish(second, first.reportVersionId(), 1, "-archive-v2", "a"), actor);
        projectionService.project(versionEvent("REPLACED", second.reportVersionId(),
                first.reportVersionId(), 2, secondFile));

        Long firstSourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM acc_project_deliverable_source_version WHERE deliverable_id=? AND source_object_id=?",
                Long.class, projectId + 4, first.reportVersionId());
        assertEquals("SUPERSEDED/PENDING_COMPENSATION", jdbcTemplate.queryForObject(
                "SELECT CONCAT(relation_status,'/',archive_status) FROM acc_project_deliverable_source_version WHERE id=?",
                String.class, firstSourceId));
        assertTrue(sourceMapper.selectPendingArchive(new PendingArchiveSourceQuery(TENANT_ID, 1000)).stream()
                .anyMatch(source -> firstSourceId.equals(source.getId())));

        archiveService.archive(TENANT_ID, firstSourceId);

        assertEquals("SUPERSEDED/ARCHIVED", jdbcTemplate.queryForObject(
                "SELECT CONCAT(relation_status,'/',archive_status) FROM acc_project_deliverable_source_version WHERE id=?",
                String.class, firstSourceId));
        assertEquals("PENDING_COMPENSATION", jdbcTemplate.queryForObject(
                "SELECT archive_status FROM acc_project_deliverable WHERE id=?", String.class, projectId + 4));
        assertEquals(second.reportVersionId(), jdbcTemplate.queryForObject(
                "SELECT source_object_id FROM acc_project_deliverable_source_version WHERE id=(SELECT current_source_version_id FROM acc_project_deliverable WHERE id=?)",
                Long.class, projectId + 4));
        var commandCaptor = forClass(cn.iocoder.yudao.module.pms.platform.api.file.dto.ArchiveFileReferenceSetsCommand.class);
        verify(fileArtifactApi).archiveReferenceSets(commandCaptor.capture());
        assertEquals(String.valueOf(first.reportVersionId()), commandCaptor.getValue().archiveSetKey().objectId());
    }

    @Test
    void archiveProviderFailureKeepsSourcePendingAndNextJobRunRetriesSuccessfully() {
        var report = service.createDraft(new AcceptanceReportCommands.CreateDraftCommand(
                activityId, 0, completeContent("初验重试")), actor);
        FileArtifactVersionFact file = stubAttachment(report.reportVersionId());
        service.publish(publish(report, null, 0, "-archive-retry", "b"), actor);
        projectionService.project(versionEvent("EFFECTIVE", report.reportVersionId(), null, 1, file));
        Long sourceId = jdbcTemplate.queryForObject(
                "SELECT id FROM acc_project_deliverable_source_version WHERE source_object_id=?",
                Long.class, report.reportVersionId());
        ProjectDeliverableSourceVersionDO source = sourceMapper.selectById(sourceId);
        doReturn(List.of(source)).when(sourceMapper).selectPendingArchive(any());
        when(fileArtifactApi.archiveReferenceSets(any()))
                .thenThrow(new IllegalStateException("provider unavailable"))
                .thenReturn(null);

        archiveJob.execute("");
        assertEquals("PENDING_COMPENSATION/1", jdbcTemplate.queryForObject(
                "SELECT CONCAT(archive_status,'/',archive_retry_count) FROM acc_project_deliverable_source_version WHERE source_object_id=?",
                String.class, report.reportVersionId()));

        archiveJob.execute("");
        assertEquals("ARCHIVED/1", jdbcTemplate.queryForObject(
                "SELECT CONCAT(archive_status,'/',archive_retry_count) FROM acc_project_deliverable_source_version WHERE source_object_id=?",
                String.class, report.reportVersionId()));
    }

    private AcceptanceReportCommands.PublishCommand publish(AcceptanceReportCommands.ReportResult report,
                                                              Long currentId, int activityVersion,
                                                              String keySuffix, String digestSeed) {
        return new AcceptanceReportCommands.PublishCommand(activityId, report.reportVersionId(), activityVersion,
                report.reportVersionNo(), currentId, key(keySuffix), digestSeed.repeat(64));
    }

    private AcceptanceReportCommands.DraftContent completeContent(String text) {
        return new AcceptanceReportCommands.DraftContent(LocalDateTime.of(2026, 8, 30, 10, 0),
                "PASS", text, "验收人");
    }

    private FileArtifactVersionFact stubAttachment(Long reportVersionId) {
        String referenceKey = UUID.nameUUIDFromBytes(String.valueOf(reportVersionId).getBytes()).toString();
        FileArtifactVersionFact file = new FileArtifactVersionFact(reportVersionId + 100, 1, referenceKey,
                "ACCEPTANCE_REPORT_ATTACHMENT", "report.pdf", 1024L, "application/pdf", "e".repeat(64),
                "AVAILABLE", "ACTIVE", new FileFactVersion(1, 1, 1), 1L);
        doAnswer(invocation -> {
            var query = (cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery)
                    invocation.getArgument(0);
            return List.of(new FileReferenceSetFact(query.collectionKeys().getFirst(), 1L, List.of(file)));
        }).when(fileArtifactApi).inspectReferenceSets(any());
        doAnswer(invocation -> {
            var query = (cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionRevalidationQuery)
                    invocation.getArgument(0);
            var expected = query.collections().getFirst();
            return List.of(new FileReferenceSetFact(expected.key(), expected.expectedScopeVersion(),
                    expected.expectedActiveFacts()));
        }).when(fileArtifactApi).lockAndRevalidateReferenceSets(any());
        return file;
    }

    private AcceptanceReportVersionChangedMessage versionEvent(String changeType, Long currentId,
                                                                 Long previousId, int versionNo,
                                                                 FileArtifactVersionFact file) {
        return new AcceptanceReportVersionChangedMessage(UUID.randomUUID().toString(), TENANT_ID, changeType,
                activityId, projectId, "PRELIMINARY", USER_ID, currentId, previousId, versionNo, List.of(file));
    }

    private long eventCount(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_outbox_event WHERE aggregate_key=? AND event_type=?",
                Long.class, String.valueOf(activityId), eventType);
    }

    private String key(String suffix) {
        return KEY_PREFIX + activityId + suffix;
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance",
            "cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class, PlatformCommandExecutionApiImpl.class,
            PlatformTransactionalOutboxWriter.class, AcceptanceReportCommandService.class,
            AcceptanceReportSourceProjectionService.class, AcceptanceReportArchiveCompensationService.class,
            AcceptanceReportArchiveCompensationJob.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
    }
}
