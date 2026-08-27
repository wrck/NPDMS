package cn.iocoder.yudao.module.pms.engineering.requirement;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.RequirementAnalysisSectionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisSectionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisProjectQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionListQuery;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisCommandService;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisFilePolicyProvider;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisQueryService;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionItem;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ExistingFileReferenceTarget;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.platform.service.file.ExistingFileVersionAttachmentService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileArtifactApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = RequirementAnalysisApplicationMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RequirementAnalysisApplicationMySqlIntegrationTest {

    private static final long TENANT_ID = 0L;
    private static final long ACTOR_ID = 9_900_013L;
    private static final AtomicLong PROJECT_SEQUENCE = new AtomicLong(8_330_000_000L);
    @Resource RequirementAnalysisCommandService commandService;
    @Resource FileArtifactApi fileArtifactApi;
    @Resource RequirementAnalysisRootMapper rootMapper;
    @Resource RequirementAnalysisSectionMapper sectionMapper;
    @Resource FileArtifactMapper artifactMapper;
    @Resource FileVersionMapper versionMapper;
    @Resource FileReferenceMapper referenceMapper;
    @Resource JdbcTemplate jdbcTemplate;
    @Resource TransactionTemplate transactionTemplate;
    @Resource PermissionApi permissionApi;
    @Resource ProjectScopeApi projectScopeApi;
    @Resource ProjectParticipantFactApi participantFactApi;
    @Resource ProjectWorkBindingFactApi workBindingFactApi;
    @MockitoSpyBean FileEventFactory fileEventFactory;

    private final List<Long> artifactIds = new ArrayList<>();
    private long projectId;

    @Test
    void v101KeepsLegacyMenusButRemovesEveryNonSuperAdminGrant() {
        Long menuCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM system_menu "
                + "WHERE deleted=b'0' AND (id=19010 OR parent_id=19010 "
                + "OR permission LIKE 'pms:eng-requirement:%')", Long.class);
        Long nonSuperAdminGrantCount = jdbcTemplate.queryForObject("SELECT COUNT(*) "
                + "FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id "
                + "AND r.tenant_id=rm.tenant_id AND r.deleted=b'0' "
                + "JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=b'0' "
                + "WHERE rm.deleted=b'0' AND r.code<>'super_admin' "
                + "AND (m.id=19010 OR m.parent_id=19010 "
                + "OR m.permission LIKE 'pms:eng-requirement:%')", Long.class);

        assertEquals(4L, menuCount);
        assertEquals(0L, nonSuperAdminGrantCount);
    }

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
        projectId = PROJECT_SEQUENCE.incrementAndGet();
        TenantContextHolder.setTenantId(TENANT_ID);
        login();
        reset(permissionApi, projectScopeApi, participantFactApi, workBindingFactApi, fileEventFactory);
        when(permissionApi.hasAnyPermissions(ACTOR_ID, RequirementAnalysisQueryService.PERMISSION_MANAGE))
                .thenReturn(true);
        when(permissionApi.hasAnyPermissions(ACTOR_ID, RequirementAnalysisQueryService.PERMISSION_QUERY))
                .thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(participantFactApi.inspect(any())).thenReturn(manager());
        when(participantFactApi.lockAndRevalidate(any())).thenReturn(manager());
        when(workBindingFactApi.inspect(any())).thenReturn(binding());
        when(workBindingFactApi.lockAndRevalidate(any())).thenReturn(binding());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE r FROM plt_file_reference r JOIN sol_requirement_analysis_section s "
                + "ON r.tenant_id=s.tenant_id AND CAST(r.object_id AS UNSIGNED)=s.id "
                + "JOIN sol_preparation p ON p.tenant_id=s.tenant_id AND p.id=s.preparation_id "
                + "WHERE p.tenant_id=? AND p.project_id=? AND r.object_type='REQUIREMENT_ANALYSIS_SECTION'",
                TENANT_ID, projectId);
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE tenant_id=? AND actor_id=? "
                + "AND correlation_id LIKE ?", TENANT_ID, ACTOR_ID, correlationPrefix() + "%");
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE tenant_id=? AND actor_id=? "
                + "AND scope_code LIKE 'REQUIREMENT_ANALYSIS_%'", TENANT_ID, ACTOR_ID);
        jdbcTemplate.update("DELETE FROM sol_requirement_analysis_section WHERE tenant_id=? "
                + "AND source_section_id IS NOT NULL AND preparation_id IN "
                + "(SELECT id FROM sol_preparation WHERE tenant_id=? AND project_id=?)",
                TENANT_ID, TENANT_ID, projectId);
        jdbcTemplate.update("DELETE FROM sol_requirement_analysis_section WHERE tenant_id=? AND preparation_id IN "
                        + "(SELECT id FROM sol_preparation WHERE tenant_id=? AND project_id=?)",
                TENANT_ID, TENANT_ID, projectId);
        jdbcTemplate.update("DELETE FROM sol_preparation WHERE tenant_id=? AND project_id=? "
                + "AND source_preparation_id IS NOT NULL", TENANT_ID, projectId);
        jdbcTemplate.update("DELETE FROM sol_preparation WHERE tenant_id=? AND project_id=?", TENANT_ID, projectId);
        for (Long artifactId : artifactIds) {
            jdbcTemplate.update("DELETE FROM plt_outbox_event WHERE tenant_id=? AND aggregate_type='FileArtifact' "
                    + "AND aggregate_key=?", TENANT_ID, String.valueOf(artifactId));
            jdbcTemplate.update("DELETE FROM plt_file_reference WHERE tenant_id=? AND artifact_id=?",
                    TENANT_ID, artifactId);
            jdbcTemplate.update("DELETE FROM plt_file_version WHERE tenant_id=? AND artifact_id=?",
                    TENANT_ID, artifactId);
            jdbcTemplate.update("DELETE FROM plt_file_artifact WHERE tenant_id=? AND id=?", TENANT_ID, artifactId);
        }
        artifactIds.clear();
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void applicationLifecyclePersistsAuditAndExactlyOnceAttachmentEvents() {
        String initialKey = operationKey("initial");
        RequirementAnalysisCommandService.CommandResult initial = commandService.createInitial(
                new RequirementAnalysisCommandService.CreateCommand(projectId, 3, initialKey),
                actor("initial"));
        List<RequirementAnalysisSectionDO> sourceSections = sections(initial.preparationId());
        fillRequiredValues(sourceSections);
        List<SourceAttachment> sources = List.of(
                attachSource(sourceSections.get(0)),
                attachSource(sourceSections.get(1)));

        String completeKey = operationKey("complete");
        RequirementAnalysisCommandService.CommandResult completed = commandService.complete(
                new RequirementAnalysisCommandService.CompleteCommand(
                        initial.preparationId(), 0, 0, 3, completeKey), actor("complete"));
        String revisionKey = operationKey("revision");
        long eventsBefore = attachedEvents();
        RequirementAnalysisCommandService.CreateRevisionCommand revisionCommand =
                new RequirementAnalysisCommandService.CreateRevisionCommand(
                        completed.preparationId(), completed.version(), completed.contentVersion(), 3, revisionKey);
        RequirementAnalysisCommandService.CommandResult draft = commandService.createRevision(
                revisionCommand, actor("revision"));

        assertEquals(eventsBefore + 2, attachedEvents());
        assertEquals(2, referenceCount(draft.preparationId()));
        assertAudit(initialKey, "REQUIREMENT_ANALYSIS_INITIALIZE", null, initial.preparationId(), null, null,
                null, "DRAFT", null, 1, null, 0, null, 0);
        assertAudit(completeKey, "REQUIREMENT_ANALYSIS_COMPLETE", completed.preparationId(), null,
                null, completed.preparationId(), "DRAFT", "COMPLETED", 1, 1, 0, 0, 0, 1);
        assertAudit(revisionKey, "REQUIREMENT_ANALYSIS_CREATE_DRAFT", null, draft.preparationId(),
                completed.preparationId(), completed.preparationId(), "COMPLETED", "DRAFT",
                1, 2, 0, 0, 1, 0);

        RequirementAnalysisCommandService.CommandResult replay = commandService.createRevision(
                revisionCommand, actor("revision-replay"));
        assertEquals(draft, replay);
        assertEquals(eventsBefore + 2, attachedEvents());
        assertEquals(1L, successAuditCount(revisionKey));

        List<AttachExistingFileVersionItem> replayItems = exactReplayItems(sources, draft.preparationId());
        transactionTemplate.executeWithoutResult(ignored -> fileArtifactApi.attachExistingVersions(
                new AttachExistingFileVersionsCommand(operationKey("plt-replay"), replayItems)));
        assertEquals(eventsBefore + 2, attachedEvents());
        assertEquals(2, referenceCount(draft.preparationId()));
    }

    @Test
    void failedSecondAttachmentRollsBackSolReferencesSuccessFactsAndOutbox() {
        RequirementAnalysisCommandService.CommandResult initial = commandService.createInitial(
                new RequirementAnalysisCommandService.CreateCommand(projectId, 3, operationKey("failure-initial")),
                actor("failure-initial"));
        List<RequirementAnalysisSectionDO> sourceSections = sections(initial.preparationId());
        fillRequiredValues(sourceSections);
        attachSource(sourceSections.get(0));
        attachSource(sourceSections.get(1));
        RequirementAnalysisCommandService.CommandResult completed = commandService.complete(
                new RequirementAnalysisCommandService.CompleteCommand(
                        initial.preparationId(), 0, 0, 3, operationKey("failure-complete")),
                actor("failure-complete"));
        long rootsBefore = rootCount();
        long sectionsBefore = sectionCount();
        long referencesBefore = referenceCountForProject();
        long eventsBefore = attachedEvents();
        String failedKey = operationKey("failure-revision");
        AtomicLong eventSequence = new AtomicLong();
        doAnswer(invocation -> {
            if (eventSequence.incrementAndGet() == 2) {
                throw new IllegalStateException("TEST_SECOND_ATTACHMENT_EVENT_FAILURE");
            }
            return invocation.callRealMethod();
        }).when(fileEventFactory).referenceAttached(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any());

        assertThrows(RuntimeException.class, () -> commandService.createRevision(
                new RequirementAnalysisCommandService.CreateRevisionCommand(
                        completed.preparationId(), completed.version(), completed.contentVersion(), 3, failedKey),
                actor("failure-revision")));

        assertEquals(rootsBefore, rootCount());
        assertEquals(sectionsBefore, sectionCount());
        assertEquals(referencesBefore, referenceCountForProject());
        assertEquals(eventsBefore, attachedEvents());
        assertNull(rootMapper.selectDraft(new RequirementAnalysisProjectQuery(TENANT_ID, projectId)));
        assertEquals(0L, idempotencyCount(failedKey));
        assertEquals(0L, successAuditCount(failedKey));
    }

    @Test
    void concurrentApplicationCompletionHasSingleWinner() throws Exception {
        RequirementAnalysisCommandService.CommandResult initial = commandService.createInitial(
                new RequirementAnalysisCommandService.CreateCommand(projectId, 3, operationKey("race-initial")),
                actor("race-initial"));
        fillRequiredValues(sections(initial.preparationId()));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = List.of(
                    pool.submit(() -> completeAfter(start, initial, "race-a")),
                    pool.submit(() -> completeAfter(start, initial, "race-b")));
            start.countDown();
            int winners = (attempts.get(0).get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (attempts.get(1).get(20, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, winners);
            PreparationDO effective = rootMapper.selectEffective(
                    new RequirementAnalysisProjectQuery(TENANT_ID, projectId));
            assertNotNull(effective);
            assertEquals(initial.preparationId(), effective.getId());
            assertEquals("COMPLETED", effective.getStatusCode());
            assertNull(rootMapper.selectDraft(new RequirementAnalysisProjectQuery(TENANT_ID, projectId)));
            assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                    + "WHERE tenant_id=? AND actor_id=? AND operation_code='REQUIREMENT_ANALYSIS_COMPLETE' "
                    + "AND result_code='SUCCESS' AND correlation_id LIKE ?", Long.class,
                    TENANT_ID, ACTOR_ID, correlationPrefix() + "race-%"));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private boolean completeAfter(CountDownLatch start, RequirementAnalysisCommandService.CommandResult initial,
                                  String suffix) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        TenantContextHolder.setTenantId(TENANT_ID);
        login();
        try {
            commandService.complete(new RequirementAnalysisCommandService.CompleteCommand(
                    initial.preparationId(), 0, 0, 3, operationKey(suffix)), actor(suffix));
            return true;
        } catch (RuntimeException failure) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    private void assertAudit(String operationId, String operationCode,
                             Long draftBefore, Long draftAfter, Long effectiveBefore, Long effectiveAfter,
                             String statusBefore, String statusAfter,
                             Integer businessBefore, Integer businessAfter,
                             Integer contentBefore, Integer contentAfter,
                             Integer aggregateBefore, Integer aggregateAfter) {
        String snapshot = jdbcTemplate.queryForObject("SELECT detail_snapshot FROM plt_operation_audit "
                + "WHERE tenant_id=? AND actor_id=? AND operation_code=? AND result_code='SUCCESS' "
                + "AND idempotency_key_digest=SHA2(?,256)", String.class,
                TENANT_ID, ACTOR_ID, operationCode, operationId);
        Map<?, ?> detail = JsonUtils.parseObject(snapshot, Map.class);
        assertNotNull(detail);
        assertEquals(operationId, detail.get("operationId"));
        assertEquals(draftBefore, number(detail.get("draftPreparationIdBefore")));
        assertEquals(draftAfter, number(detail.get("draftPreparationIdAfter")));
        assertEquals(effectiveBefore, number(detail.get("effectivePreparationIdBefore")));
        assertEquals(effectiveAfter, number(detail.get("effectivePreparationIdAfter")));
        assertEquals(statusBefore, detail.get("statusBefore"));
        assertEquals(statusAfter, detail.get("statusAfter"));
        assertEquals(businessBefore, integer(detail.get("businessVersionBefore")));
        assertEquals(businessAfter, integer(detail.get("businessVersionAfter")));
        assertEquals(contentBefore, integer(detail.get("contentVersionBefore")));
        assertEquals(contentAfter, integer(detail.get("contentVersionAfter")));
        assertEquals(aggregateBefore, integer(detail.get("aggregateVersionBefore")));
        assertEquals(aggregateAfter, integer(detail.get("aggregateVersionAfter")));
        assertTrue(detail.containsKey("sections"));
        assertFalse(snapshot.contains("filled requirement content"));
    }

    private List<AttachExistingFileVersionItem> exactReplayItems(
            List<SourceAttachment> sources, Long draftPreparationId) {
        Map<String, RequirementAnalysisSectionDO> targets = sections(draftPreparationId).stream()
                .collect(java.util.stream.Collectors.toMap(RequirementAnalysisSectionDO::getSectionCode, row -> row));
        List<AttachExistingFileVersionItem> items = new ArrayList<>();
        for (SourceAttachment source : sources) {
            RequirementAnalysisSectionDO target = targets.get(source.section().getSectionCode());
            List<RequirementAnalysisQueryService.AttachmentFact> targetFacts = JsonUtils.parseArray(
                    target.getAttachmentReferenceSnapshot(), RequirementAnalysisQueryService.AttachmentFact.class);
            RequirementAnalysisQueryService.AttachmentFact targetFact = targetFacts.stream()
                    .filter(fact -> fact.artifactId().equals(source.artifactId())).findFirst().orElseThrow();
            items.add(new AttachExistingFileVersionItem(
                    new FileArtifactVersionRevalidationQuery(source.artifactId(), 1, "SOL",
                            "REQUIREMENT_ANALYSIS_SECTION", String.valueOf(source.section().getId()),
                            "SECTION_ATTACHMENT", source.referenceKey(), "READ",
                            new FileFactVersion(1, 0, 0), 7L),
                    new ExistingFileReferenceTarget("SOL", "REQUIREMENT_ANALYSIS_SECTION",
                            String.valueOf(target.getId()), "SECTION_ATTACHMENT", targetFact.referenceKey(), 7L)));
        }
        return items;
    }

    private SourceAttachment attachSource(RequirementAnalysisSectionDO section) {
        LocalDateTime now = LocalDateTime.now();
        FileArtifactDO artifact = new FileArtifactDO();
        artifact.setName("requirement-" + section.getSectionCode() + ".png");
        artifact.setCategoryCode("REQUIREMENT_ANALYSIS_ATTACHMENT");
        artifact.setOwnerContext("SOL");
        artifact.setLifecycleStatusCode("ACTIVE");
        artifact.setVersion(1);
        artifact.setCreator(String.valueOf(ACTOR_ID));
        artifact.setUpdater(String.valueOf(ACTOR_ID));
        artifact.setCreateTime(now);
        artifact.setUpdateTime(now);
        artifact.setTenantId(TENANT_ID);
        assertEquals(1, artifactMapper.insert(artifact));
        artifactIds.add(artifact.getId());

        FileVersionDO version = new FileVersionDO();
        version.setTenantId(TENANT_ID);
        version.setArtifactId(artifact.getId());
        version.setVersionNo(1);
        version.setInfraFileId(artifact.getId() + 9_000_000L);
        version.setAvailabilityVersion(0);
        version.setSha256("a".repeat(64));
        version.setSizeBytes(128L);
        version.setDeclaredMediaType("image/png");
        version.setDetectedMediaType("image/png");
        version.setScanStatusCode("SKIPPED");
        version.setAvailabilityStatusCode("AVAILABLE");
        version.setCreatedBy(ACTOR_ID);
        version.setCreatedAt(now);
        assertEquals(1, versionMapper.insert(version));

        String referenceKey = UUID.randomUUID().toString();
        FileReferenceDO reference = new FileReferenceDO();
        reference.setTenantId(TENANT_ID);
        reference.setOwnerContext("SOL");
        reference.setObjectType("REQUIREMENT_ANALYSIS_SECTION");
        reference.setObjectId(String.valueOf(section.getId()));
        reference.setPurposeCode("SECTION_ATTACHMENT");
        reference.setReferenceKey(referenceKey);
        reference.setArtifactId(artifact.getId());
        reference.setFileVersionNo(1);
        reference.setSensitivityCode("INTERNAL");
        reference.setStatusCode("ACTIVE");
        reference.setScopeVersion(7L);
        reference.setVersion(0);
        reference.setCreator(String.valueOf(ACTOR_ID));
        reference.setUpdater(String.valueOf(ACTOR_ID));
        reference.setCreateTime(now);
        reference.setUpdateTime(now);
        assertEquals(1, referenceMapper.insert(reference));

        var fact = new RequirementAnalysisQueryService.AttachmentFact(artifact.getId(), 1, referenceKey,
                new FileFactVersion(1, 0, 0), 7L);
        jdbcTemplate.update("UPDATE sol_requirement_analysis_section SET attachment_reference_snapshot=? "
                        + "WHERE tenant_id=? AND id=?", JsonUtils.toJsonString(List.of(fact)), TENANT_ID,
                section.getId());
        section.setAttachmentReferenceSnapshot(JsonUtils.toJsonString(List.of(fact)));
        return new SourceAttachment(section, artifact.getId(), referenceKey);
    }

    private void fillRequiredValues(List<RequirementAnalysisSectionDO> rows) {
        rows.stream().filter(RequirementAnalysisSectionDO::getRequiredFlag).forEach(row ->
                jdbcTemplate.update("UPDATE sol_requirement_analysis_section SET value_snapshot=? "
                                + "WHERE tenant_id=? AND id=?", "\"filled requirement content\"", TENANT_ID,
                        row.getId()));
    }

    private List<RequirementAnalysisSectionDO> sections(Long preparationId) {
        return sectionMapper.selectList(new RequirementAnalysisSectionListQuery(TENANT_ID, preparationId));
    }

    private long referenceCount(Long preparationId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_file_reference r "
                + "JOIN sol_requirement_analysis_section s ON r.tenant_id=s.tenant_id "
                + "AND CAST(r.object_id AS UNSIGNED)=s.id WHERE s.tenant_id=? AND s.preparation_id=? "
                + "AND r.object_type='REQUIREMENT_ANALYSIS_SECTION' AND r.status_code='ACTIVE'",
                Long.class, TENANT_ID, preparationId);
    }

    private long referenceCountForProject() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_file_reference r "
                + "JOIN sol_requirement_analysis_section s ON r.tenant_id=s.tenant_id "
                + "AND CAST(r.object_id AS UNSIGNED)=s.id JOIN sol_preparation p "
                + "ON p.tenant_id=s.tenant_id AND p.id=s.preparation_id "
                + "WHERE p.tenant_id=? AND p.project_id=? AND r.object_type='REQUIREMENT_ANALYSIS_SECTION'",
                Long.class, TENANT_ID, projectId);
    }

    private long attachedEvents() {
        if (artifactIds.isEmpty()) return 0L;
        String placeholders = String.join(",", java.util.Collections.nCopies(artifactIds.size(), "?"));
        List<Object> arguments = new ArrayList<>();
        arguments.add(TENANT_ID);
        arguments.addAll(artifactIds.stream().map(String::valueOf).toList());
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=? "
                + "AND event_type='FileReferenceAttached' AND aggregate_key IN (" + placeholders + ")",
                Long.class, arguments.toArray());
    }

    private long rootCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_preparation WHERE tenant_id=? "
                + "AND project_id=? AND preparation_type_code='PRE_04_REQUIREMENT_ANALYSIS'",
                Long.class, TENANT_ID, projectId);
    }

    private long sectionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_requirement_analysis_section s "
                + "JOIN sol_preparation p ON p.tenant_id=s.tenant_id AND p.id=s.preparation_id "
                + "WHERE p.tenant_id=? AND p.project_id=?", Long.class, TENANT_ID, projectId);
    }

    private long idempotencyCount(String key) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? "
                + "AND actor_id=? AND idempotency_key=?", Long.class, TENANT_ID, ACTOR_ID, key);
    }

    private long successAuditCount(String operationId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? "
                + "AND actor_id=? AND result_code='SUCCESS' AND idempotency_key_digest=SHA2(?,256)",
                Long.class, TENANT_ID, ACTOR_ID, operationId);
    }

    private RequirementAnalysisCommandService.Actor actor(String suffix) {
        return new RequirementAnalysisCommandService.Actor(TENANT_ID, ACTOR_ID,
                correlationPrefix() + suffix);
    }

    private String operationKey(String suffix) {
        return "fsol003-it-" + projectId + "-" + suffix;
    }

    private String correlationPrefix() {
        return "F-SOL003-IT-" + projectId + "-";
    }

    private ProjectScopeResult scope() {
        return new ProjectScopeResult(projectId, 7L, Set.of(projectId), Set.of());
    }

    private ProjectParticipantFact manager() {
        return new ProjectParticipantFact(projectId, ACTOR_ID,
                Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                "PRIMARY", "ACTIVE", "S1", 3, 11L);
    }

    private ProjectWorkBindingFact binding() {
        return new ProjectWorkBindingFact(projectId, 3, 201L, 1, 301L, 1, 401L, 1,
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.workBindingTypeCode(),
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetContextCode(),
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetObjectType(),
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetObjectKey(),
                null, null, null, null, 17L, 1,
                "{\"schemaVersion\":1,\"catalogCode\":\"PRE_04_REQUIREMENT_ANALYSIS\","
                        + "\"catalogVersion\":1,\"extensionItems\":[]}");
    }

    private static Long number(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer integer(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static void login() {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(ACTOR_ID).setUserType(2),
                new MockHttpServletRequest());
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("missing environment variable: " + name);
        return value;
    }

    private record SourceAttachment(RequirementAnalysisSectionDO section, Long artifactId, String referenceKey) {
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.file",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            RequirementAnalysisCommandService.class, RequirementAnalysisFilePolicyProvider.class,
            FileBusinessObjectPolicyRegistry.class, ExistingFileVersionAttachmentService.class,
            FileArtifactApiImpl.class, FileEventFactory.class, PlatformTransactionalOutboxWriter.class,
            PlatformCommandExecutionApiImpl.class, OperationAuditApiImpl.class})
    static class TestApplication {

        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }

        @Bean PermissionApi permissionApi() {
            return mock(PermissionApi.class);
        }

        @Bean ProjectScopeApi projectScopeApi() {
            return mock(ProjectScopeApi.class);
        }

        @Bean ProjectParticipantFactApi participantFactApi() {
            return mock(ProjectParticipantFactApi.class);
        }

        @Bean ProjectWorkBindingFactApi projectWorkBindingFactApi() {
            return mock(ProjectWorkBindingFactApi.class);
        }

        @Bean TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner = new TenantLineInnerInterceptor(
                    new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }
    }
}
