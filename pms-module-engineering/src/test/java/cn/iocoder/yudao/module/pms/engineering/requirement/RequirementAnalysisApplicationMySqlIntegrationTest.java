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
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisProjectQuery;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisDynamicFormCommandService;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisDynamicFormPolicyProvider;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisQueryService;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.*;
import cn.iocoder.yudao.module.pms.platform.service.command.*;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.service.file.*;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchCandidate;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatcher;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = RequirementAnalysisApplicationMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RequirementAnalysisApplicationMySqlIntegrationTest {

    private static final long TENANT = 1L;
    private static final long ACTOR = 9_900_013L;
    private static final long TEMPLATE = 992_203_010_001L;
    private static final long REVISION = 992_203_020_001L;
    private static final AtomicLong PROJECTS = new AtomicLong(8_340_000_000L);

    @Resource RequirementAnalysisDynamicFormCommandService commands;
    @Resource RequirementAnalysisRootMapper roots;
    @Resource FileArtifactMapper artifacts;
    @Resource FileVersionMapper versions;
    @Resource FileReferenceMapper references;
    @Resource JdbcTemplate jdbc;
    @Resource PermissionApi permissions;
    @Resource ProjectScopeApi scopes;
    @Resource ProjectParticipantFactApi participants;
    @Resource ProjectWorkBindingFactApi bindings;
    @MockitoSpyBean FileEventFactory events;

    private final List<Long> artifactIds = new ArrayList<>();
    private long projectId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        Map<String, String> env = System.getenv();
        String database = env.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = env.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=UTF-8&nullCatalogMeansCurrent=true");
        registry.add("spring.datasource.username", () -> required(env, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(env, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        projectId = PROJECTS.incrementAndGet();
        TenantContextHolder.setTenantId(TENANT);
        login();
        reset(permissions, scopes, participants, bindings, events);
        when(permissions.hasAnyPermissions(ACTOR, RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(permissions.hasAnyPermissions(ACTOR, RequirementAnalysisQueryService.PERMISSION_QUERY,
                RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(scope());
        when(scopes.lockAndRevalidate(any())).thenReturn(scope());
        when(participants.inspect(any())).thenReturn(manager());
        when(participants.lockAndRevalidate(any())).thenReturn(manager());
        when(bindings.inspect(any())).thenReturn(binding());
        when(bindings.lockAndRevalidate(any())).thenReturn(binding());
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE r FROM plt_file_reference r JOIN plt_dynamic_form_instance i "
                + "ON r.tenant_id=i.tenant_id AND CAST(r.object_id AS UNSIGNED)=i.id "
                + "JOIN sol_preparation p ON p.tenant_id=i.tenant_id AND p.dynamic_form_instance_id=i.id "
                + "WHERE p.tenant_id=? AND p.project_id=? AND r.object_type='DYNAMIC_FORM_INSTANCE'", TENANT, projectId);
        jdbc.update("DELETE i FROM plt_dynamic_form_instance i JOIN sol_preparation p "
                + "ON p.tenant_id=i.tenant_id AND p.dynamic_form_instance_id=i.id "
                + "WHERE p.tenant_id=? AND p.project_id=?", TENANT, projectId);
        jdbc.update("UPDATE sol_preparation SET source_preparation_id=NULL "
                + "WHERE tenant_id=? AND project_id=?", TENANT, projectId);
        jdbc.update("DELETE FROM sol_preparation WHERE tenant_id=? AND project_id=?", TENANT, projectId);
        jdbc.update("DELETE FROM plt_operation_audit WHERE tenant_id=? AND actor_id=? AND correlation_id LIKE ?",
                TENANT, ACTOR, correlationPrefix() + "%");
        jdbc.update("DELETE FROM plt_idempotency_record WHERE tenant_id=? AND actor_id=? "
                + "AND scope_code LIKE 'REQUIREMENT_ANALYSIS_%'", TENANT, ACTOR);
        for (Long artifactId : artifactIds) {
            jdbc.update("DELETE FROM plt_outbox_event WHERE tenant_id=? AND aggregate_type='FileArtifact' "
                    + "AND aggregate_key=?", TENANT, String.valueOf(artifactId));
            jdbc.update("DELETE FROM plt_file_reference WHERE tenant_id=? AND artifact_id=?", TENANT, artifactId);
            jdbc.update("DELETE FROM plt_file_version WHERE tenant_id=? AND artifact_id=?", TENANT, artifactId);
            jdbc.update("DELETE FROM plt_file_artifact WHERE tenant_id=? AND id=?", TENANT, artifactId);
        }
        artifactIds.clear();
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void cloneCreatesNEventsAndSameIntentReplayAddsNothing() {
        var completed = completedWithTwoAttachments("success");
        long before = attachedEvents();
        String key = operationKey("clone");
        var command = new RequirementAnalysisDynamicFormCommandService.CreateRevisionCommand(
                completed.preparationId(), completed.dynamicFormInstanceId(), completed.solVersion(),
                completed.dynamicFormInstanceVersion(), key);

        var draft = commands.createRevision(command, actor("clone"));
        assertEquals(before + 2, attachedEvents());
        assertEquals(2L, activeReferences(draft.dynamicFormInstanceId()));
        assertEquals(1L, successAudits(key));
        assertCreateDraftAudit(key, completed, draft);

        assertEquals(draft, commands.createRevision(command, actor("clone-replay")));
        assertEquals(before + 2, attachedEvents());
        assertEquals(2L, activeReferences(draft.dynamicFormInstanceId()));
        assertEquals(1L, successAudits(key));
    }

    @Test
    void secondEventFailureRollsBackSolInstanceReferencesIdempotencyAuditAndOutbox() {
        var completed = completedWithTwoAttachments("failure");
        long rootsBefore = rootCount(), instancesBefore = instanceCount();
        long referencesBefore = referenceCount(), eventsBefore = attachedEvents();
        String key = operationKey("failed-clone");
        AtomicLong sequence = new AtomicLong();
        doAnswer(invocation -> {
            if (sequence.incrementAndGet() == 2) throw new IllegalStateException("TEST_SECOND_EVENT_FAILURE");
            return invocation.callRealMethod();
        }).when(events).referenceAttached(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertThrows(RuntimeException.class, () -> commands.createRevision(
                new RequirementAnalysisDynamicFormCommandService.CreateRevisionCommand(
                        completed.preparationId(), completed.dynamicFormInstanceId(), completed.solVersion(),
                        completed.dynamicFormInstanceVersion(), key), actor("failed-clone")));

        assertEquals(rootsBefore, rootCount());
        assertEquals(instancesBefore, instanceCount());
        assertEquals(referencesBefore, referenceCount());
        assertEquals(eventsBefore, attachedEvents());
        assertNull(roots.selectDraft(new RequirementAnalysisProjectQuery(TENANT, projectId)));
        assertEquals(0L, idempotencies(key));
        assertEquals(0L, successAudits(key));
    }

    @Test
    void concurrentApplicationCompletionHasOneWinner() throws Exception {
        var draft = initializedAndPatched("race");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = List.of(
                    pool.submit(() -> completeAfter(start, draft, "race-a")),
                    pool.submit(() -> completeAfter(start, draft, "race-b")));
            start.countDown();
            int winners = (attempts.get(0).get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (attempts.get(1).get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, winners);
            PreparationDO effective = roots.selectEffective(new RequirementAnalysisProjectQuery(TENANT, projectId));
            assertNotNull(effective);
            assertEquals(draft.preparationId(), effective.getId());
            assertEquals("COMPLETED", effective.getStatusCode());
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                    + "WHERE tenant_id=? AND actor_id=? AND operation_code='REQUIREMENT_ANALYSIS_COMPLETE' "
                    + "AND result_code='SUCCESS' AND correlation_id LIKE ?", Long.class,
                    TENANT, ACTOR, correlationPrefix() + "race-%"));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void v104RejectsNullInstanceIdForDraftPre04Root() {
        var draft = commands.createInitial(
                new RequirementAnalysisDynamicFormCommandService.CreateCommand(projectId, operationKey("v104")),
                actor("v104"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sol_preparation WHERE tenant_id=? "
                + "AND preparation_type_code='PRE_04_REQUIREMENT_ANALYSIS' "
                + "AND dynamic_form_instance_id IS NULL", Long.class, TENANT));
        DataAccessException failure = assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE sol_preparation SET dynamic_form_instance_id=NULL WHERE tenant_id=? AND id=?",
                TENANT, draft.preparationId()));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("chk_sol_preparation_pre04_markers"));
        assertNotNull(jdbc.queryForObject("SELECT dynamic_form_instance_id FROM sol_preparation "
                + "WHERE tenant_id=? AND id=?", Long.class, TENANT, draft.preparationId()));
    }

    @Test
    void v105KeepsFixedFourDimensionNoMatchBehavior() {
        List<TemplateMatchCandidate> candidates = jdbc.query("SELECT t.id, t.code, t.name, t.match_priority, "
                        + "r.id revision_id, r.revision_no, r.signing_method, r.project_category, "
                        + "r.implementation_method, r.major_project_level FROM proj_project_template t "
                        + "JOIN proj_project_template_revision r ON r.tenant_id=t.tenant_id "
                        + "AND r.template_id=t.id AND r.status='PUBLISHED' AND r.deleted=b'0' "
                        + "WHERE t.tenant_id=? AND t.status='ACTIVE' AND t.deleted=b'0' "
                        + "AND r.revision_no=(SELECT MAX(latest.revision_no) "
                        + "FROM proj_project_template_revision latest WHERE latest.tenant_id=r.tenant_id "
                        + "AND latest.template_id=r.template_id AND latest.status='PUBLISHED' "
                        + "AND latest.deleted=b'0') ORDER BY t.match_priority, t.id",
                (rs, rowNum) -> {
                    TemplateMatchCandidate candidate = new TemplateMatchCandidate();
                    candidate.setTemplateId(rs.getLong("id"));
                    candidate.setCode(rs.getString("code"));
                    candidate.setName(rs.getString("name"));
                    candidate.setMatchPriority(rs.getInt("match_priority"));
                    candidate.setTemplateRevisionId(rs.getLong("revision_id"));
                    candidate.setLatestRevisionNo(rs.getInt("revision_no"));
                    candidate.setSigningMethod(rs.getString("signing_method"));
                    candidate.setProjectCategory(rs.getString("project_category"));
                    candidate.setImplementationMethod(rs.getString("implementation_method"));
                    candidate.setMajorProjectLevel(rs.getString("major_project_level"));
                    return candidate;
                }, TENANT);

        TemplateMatchResult result = TemplateMatcher.match(candidates,
                "PUBLIC_TENDER", "ENGINEERING", "REMOTE", "NATIONAL");
        assertEquals(TemplateMatchResult.Outcome.NO_MATCH, result.getOutcome());
        assertEquals(0, result.getCandidates().size());
        assertNull(result.getMatched());
    }

    private RequirementAnalysisDynamicFormCommandService.CommandResult initializedAndPatched(String suffix) {
        var initial = commands.createInitial(new RequirementAnalysisDynamicFormCommandService.CreateCommand(
                projectId, operationKey(suffix + "-initial")), actor(suffix + "-initial"));
        Map<String, Object> values = Map.of("PROJECT_BACKGROUND", "<p>项目背景</p>",
                "PROJECT_OBJECTIVE", "<p>项目目标</p>", "NETWORK_TOPOLOGY", "<p>网络拓扑</p>");
        return commands.patch(new RequirementAnalysisDynamicFormCommandService.PatchCommand(
                initial.preparationId(), initial.solVersion(), initial.dynamicFormInstanceVersion(), values,
                operationKey(suffix + "-patch")), actor(suffix + "-patch"));
    }

    private RequirementAnalysisDynamicFormCommandService.CommandResult completedWithTwoAttachments(String suffix) {
        var draft = initializedAndPatched(suffix);
        attach(draft.dynamicFormInstanceId(), "PROJECT_BACKGROUND__ATTACHMENTS");
        attach(draft.dynamicFormInstanceId(), "PROJECT_OBJECTIVE__ATTACHMENTS");
        var completed = commands.complete(new RequirementAnalysisDynamicFormCommandService.CompleteCommand(
                draft.preparationId(), draft.solVersion(), draft.dynamicFormInstanceVersion(),
                operationKey(suffix + "-complete")), actor(suffix + "-complete"));
        verify(participants, atLeast(2)).lockAndRevalidate(argThat(query -> "S1".equals(query.requiredCurrentStage())));
        return completed;
    }

    private boolean completeAfter(CountDownLatch start,
                                  RequirementAnalysisDynamicFormCommandService.CommandResult draft,
                                  String suffix) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        TenantContextHolder.setTenantId(TENANT);
        login();
        try {
            commands.complete(new RequirementAnalysisDynamicFormCommandService.CompleteCommand(
                    draft.preparationId(), draft.solVersion(), draft.dynamicFormInstanceVersion(),
                    operationKey(suffix)), actor(suffix));
            return true;
        } catch (RuntimeException failure) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    private void attach(Long instanceId, String field) {
        LocalDateTime now = LocalDateTime.now();
        FileArtifactDO artifact = new FileArtifactDO();
        artifact.setName(field + ".png"); artifact.setCategoryCode("DYNAMIC_FORM_ATTACHMENT");
        artifact.setOwnerContext("PLATFORM"); artifact.setLifecycleStatusCode("ACTIVE"); artifact.setVersion(1);
        artifact.setCreator(String.valueOf(ACTOR)); artifact.setUpdater(String.valueOf(ACTOR));
        artifact.setCreateTime(now); artifact.setUpdateTime(now); artifact.setTenantId(TENANT);
        assertEquals(1, artifacts.insert(artifact)); artifactIds.add(artifact.getId());

        FileVersionDO version = new FileVersionDO();
        version.setTenantId(TENANT); version.setArtifactId(artifact.getId()); version.setVersionNo(1);
        version.setInfraFileId(artifact.getId() + 9_000_000L); version.setAvailabilityVersion(0);
        version.setSha256("a".repeat(64)); version.setSizeBytes(128L); version.setDeclaredMediaType("image/png");
        version.setDetectedMediaType("image/png"); version.setScanStatusCode("SKIPPED");
        version.setAvailabilityStatusCode("AVAILABLE"); version.setCreatedBy(ACTOR); version.setCreatedAt(now);
        assertEquals(1, versions.insert(version));

        FileReferenceDO reference = new FileReferenceDO();
        reference.setTenantId(TENANT); reference.setOwnerContext("PLATFORM");
        reference.setObjectType("DYNAMIC_FORM_INSTANCE"); reference.setObjectId(String.valueOf(instanceId));
        reference.setPurposeCode("FORM_FIELD_ATTACHMENT/" + field);
        reference.setReferenceKey(UUID.randomUUID().toString()); reference.setArtifactId(artifact.getId());
        reference.setFileVersionNo(1); reference.setSensitivityCode("INTERNAL"); reference.setStatusCode("ACTIVE");
        reference.setScopeVersion(7L); reference.setVersion(0); reference.setCreator(String.valueOf(ACTOR));
        reference.setUpdater(String.valueOf(ACTOR)); reference.setCreateTime(now); reference.setUpdateTime(now);
        assertEquals(1, references.insert(reference));
    }

    private long rootCount() { return count("SELECT COUNT(*) FROM sol_preparation WHERE tenant_id=? "
            + "AND project_id=? AND preparation_type_code='PRE_04_REQUIREMENT_ANALYSIS'"); }
    private long instanceCount() { return count("SELECT COUNT(*) FROM plt_dynamic_form_instance i "
            + "JOIN sol_preparation p ON p.tenant_id=i.tenant_id AND p.dynamic_form_instance_id=i.id "
            + "WHERE p.tenant_id=? AND p.project_id=?"); }
    private long referenceCount() { return count("SELECT COUNT(*) FROM plt_file_reference r "
            + "JOIN plt_dynamic_form_instance i ON i.tenant_id=r.tenant_id AND CAST(r.object_id AS UNSIGNED)=i.id "
            + "JOIN sol_preparation p ON p.tenant_id=i.tenant_id AND p.dynamic_form_instance_id=i.id "
            + "WHERE p.tenant_id=? AND p.project_id=? AND r.object_type='DYNAMIC_FORM_INSTANCE'"); }
    private long count(String sql) { return jdbc.queryForObject(sql, Long.class, TENANT, projectId); }
    private long activeReferences(Long instanceId) { return jdbc.queryForObject("SELECT COUNT(*) "
            + "FROM plt_file_reference WHERE tenant_id=? AND object_type='DYNAMIC_FORM_INSTANCE' "
            + "AND object_id=? AND status_code='ACTIVE'", Long.class, TENANT, String.valueOf(instanceId)); }
    private long attachedEvents() {
        if (artifactIds.isEmpty()) return 0;
        String marks = String.join(",", Collections.nCopies(artifactIds.size(), "?"));
        List<Object> args = new ArrayList<>(); args.add(TENANT);
        args.addAll(artifactIds.stream().map(String::valueOf).toList());
        return jdbc.queryForObject("SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=? "
                + "AND event_type='FileReferenceAttached' AND aggregate_key IN (" + marks + ")",
                Long.class, args.toArray());
    }
    private long idempotencies(String key) { return jdbc.queryForObject("SELECT COUNT(*) FROM "
            + "plt_idempotency_record WHERE tenant_id=? AND actor_id=? AND idempotency_key=?",
            Long.class, TENANT, ACTOR, key); }
    private long successAudits(String key) { return jdbc.queryForObject("SELECT COUNT(*) FROM "
            + "plt_operation_audit WHERE tenant_id=? AND actor_id=? AND result_code='SUCCESS' "
            + "AND idempotency_key_digest=SHA2(?,256)", Long.class, TENANT, ACTOR, key); }

    @SuppressWarnings("unchecked")
    private void assertCreateDraftAudit(String key,
                                        RequirementAnalysisDynamicFormCommandService.CommandResult source,
                                        RequirementAnalysisDynamicFormCommandService.CommandResult draft) {
        String snapshot = jdbc.queryForObject("SELECT detail_snapshot FROM plt_operation_audit "
                        + "WHERE tenant_id=? AND actor_id=? AND result_code='SUCCESS' "
                        + "AND idempotency_key_digest=SHA2(?,256)", String.class, TENANT, ACTOR, key);
        Map<String, Object> detail = JsonUtils.parseObject(snapshot, Map.class);
        assertEquals(key, detail.get("operationId"));
        assertEquals("CREATE_DRAFT", detail.get("action"));
        assertEquals(projectId, number(detail.get("projectId")));
        assertEquals(draft.preparationId(), number(detail.get("preparationId")));
        assertEquals(draft.businessVersion().longValue(), number(detail.get("businessVersion")));
        assertEquals(draft.dynamicFormInstanceId(), number(detail.get("dynamicFormInstanceId")));
        assertEquals(TEMPLATE, number(detail.get("dynamicFormTemplateId")));
        assertEquals(REVISION, number(detail.get("dynamicFormTemplateRevisionId")));
        assertNull(detail.get("draftPreparationIdBefore"));
        assertEquals(draft.preparationId(), number(detail.get("draftPreparationIdAfter")));
        assertEquals(source.preparationId(), number(detail.get("effectivePreparationIdBefore")));
        assertEquals(source.preparationId(), number(detail.get("effectivePreparationIdAfter")));
        assertNull(detail.get("statusBefore"));
        assertEquals("DRAFT", detail.get("statusAfter"));
        assertNull(detail.get("solVersionBefore"));
        assertEquals(draft.solVersion().longValue(), number(detail.get("solVersionAfter")));
        assertNull(detail.get("contentVersionBefore"));
        assertEquals(draft.contentVersion().longValue(), number(detail.get("contentVersionAfter")));
        assertNull(detail.get("instanceVersionBefore"));
        assertEquals(draft.dynamicFormInstanceVersion().longValue(), number(detail.get("instanceVersionAfter")));
        assertFalse(((List<?>) detail.get("changedFieldKeys")).isEmpty());
        assertEquals(2, ((List<?>) detail.get("controlledFileSummary")).size());
        assertEquals(ACTOR, number(detail.get("actorId")));
        assertNotNull(detail.get("occurredAt"));
        assertFalse(snapshot.contains("<p>项目背景</p>"));
        assertFalse(snapshot.contains("<p>项目目标</p>"));
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private RequirementAnalysisDynamicFormCommandService.Actor actor(String suffix) {
        return new RequirementAnalysisDynamicFormCommandService.Actor(TENANT, ACTOR, correlationPrefix() + suffix);
    }
    private String operationKey(String suffix) { return "fsol003-dynamic-it-" + projectId + "-" + suffix; }
    private String correlationPrefix() { return "F-SOL003-DYNAMIC-IT-" + projectId + "-"; }
    private ProjectScopeResult scope() { return new ProjectScopeResult(projectId, 7L, Set.of(projectId), Set.of()); }
    private ProjectParticipantFact manager() { return new ProjectParticipantFact(projectId, ACTOR,
            Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), "PRIMARY", "ACTIVE", "S1", 3, 11L); }
    private ProjectWorkBindingFact binding() {
        String snapshot = "{\"schemaVersion\":2,\"dynamicFormTemplateId\":" + TEMPLATE
                + ",\"dynamicFormTemplateRevisionId\":" + REVISION
                + ",\"dynamicFormRevisionNo\":1,\"dynamicFormRevisionFactVersion\":1}";
        return new ProjectWorkBindingFact(projectId, 3, 201L, 1, 301L, 1, 401L, 1,
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.workBindingTypeCode(),
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetContextCode(),
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetObjectType(),
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetObjectKey(),
                null, null, null, null, 17L, 1, snapshot, TEMPLATE, REVISION, 1, 1);
    }
    private static void login() { SecurityFrameworkUtils.setLoginUser(
            new LoginUser().setId(ACTOR).setUserType(2), new MockHttpServletRequest()); }
    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("missing environment variable: " + name);
        return value;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan({"cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.file",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            RequirementAnalysisDynamicFormCommandService.class, RequirementAnalysisDynamicFormPolicyProvider.class,
            DynamicFormBusinessInstanceApiImpl.class, DynamicFormBusinessInstanceService.class,
            DynamicFormBusinessObjectPolicyProviderRegistry.class, DynamicFormSchemaService.class,
            DynamicFormFilePolicyProvider.class, FileBusinessObjectPolicyRegistry.class,
            ExistingFileVersionAttachmentService.class, FileArtifactApiImpl.class, FileEventFactory.class,
            PlatformTransactionalOutboxWriter.class, PlatformCommandExecutionApiImpl.class, OperationAuditApiImpl.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
        @Bean PermissionApi permissionApi() { return mock(PermissionApi.class); }
        @Bean ProjectScopeApi projectScopeApi() { return mock(ProjectScopeApi.class); }
        @Bean ProjectParticipantFactApi participantFactApi() { return mock(ProjectParticipantFactApi.class); }
        @Bean ProjectWorkBindingFactApi workBindingFactApi() { return mock(ProjectWorkBindingFactApi.class); }
        @Bean TenantLineInnerInterceptor tenantLineInnerInterceptor(MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner = new TenantLineInnerInterceptor(
                    new TenantDatabaseInterceptor(new TenantProperties()));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }
    }
}
