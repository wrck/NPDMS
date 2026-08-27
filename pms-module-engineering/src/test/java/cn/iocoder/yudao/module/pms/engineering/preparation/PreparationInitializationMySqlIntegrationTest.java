package cn.iocoder.yudao.module.pms.engineering.preparation;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.PreparationInitializationApi;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.dto.PreparationInitializationCommand;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.DynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.FixedSurveyFormCatalogProvider;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationInitializationService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationItemApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationFilePolicyProvider;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationReviewService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationReadinessService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationSourceProviderRegistry;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReviewCommand;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReadinessCommand;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PatchPreparationItemCommand;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformIdempotencyRecordMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.FileArtifactApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.infra.api.config.ConfigApiImpl;
import cn.iocoder.yudao.module.infra.dal.mysql.config.ConfigMapper;
import cn.iocoder.yudao.module.infra.service.config.ConfigServiceImpl;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectOrganizationFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApiImpl;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectWorkBindingFactMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
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
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = PreparationInitializationMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PreparationInitializationMySqlIntegrationTest {

    @Resource PreparationInitializationService service;
    @Resource PreparationItemApplicationService itemService;
    @Resource PreparationReviewService reviewService;
    @Resource PreparationReadinessService readinessService;
    @Resource JdbcTemplate jdbcTemplate;
    @Resource TransactionTemplate transactionTemplate;
    @Resource PermissionApi permissionApi;
    @Resource ProjectScopeApi projectScopeApi;

    private long projectId;
    private long taskId;
    private long contractId;
    private long stateMachineRevisionId;
    private long templateDefinitionId;
    private int sourceDefinitionVersion;
    private String bindingSnapshot;
    private String idempotencyKey;

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
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(9L).setUserType(2),
                new MockHttpServletRequest());
        long seed = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        projectId = 978_000_000_000L + seed * 10L;
        taskId = projectId + 1;
        contractId = projectId + 2;
        idempotencyKey = "PRE02_INIT:" + projectId + ":" + contractId + ":1";
        stateMachineRevisionId = jdbcTemplate.queryForObject(
                "SELECT id FROM proj_task_state_machine_revision WHERE tenant_id=0 "
                        + "AND status='PUBLISHED' ORDER BY revision_no DESC LIMIT 1", Long.class);
        Map<String, Object> definition = jdbcTemplate.queryForMap(
                "SELECT d.id,d.definition_version,d.binding_config "
                        + "FROM proj_project_template_task_definition d "
                        + "JOIN proj_project_template_revision r ON r.tenant_id=d.tenant_id "
                        + "AND r.id=d.template_revision_id "
                        + "WHERE d.tenant_id=0 AND r.status='DRAFT' "
                        + "AND d.target_context_code='SOL' "
                        + "AND d.target_object_type='SITE_SURVEY_PREPARATION' "
                        + "AND d.target_object_key='PRE_02_SITE_SURVEY' ORDER BY d.id LIMIT 1");
        templateDefinitionId = ((Number) definition.get("id")).longValue();
        sourceDefinitionVersion = ((Number) definition.get("definition_version")).intValue();
        bindingSnapshot = definition.get("binding_config").toString();
        when(permissionApi.hasAnyPermissions(org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(true);
        var scope = new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(
                projectId, 1L, Set.of(projectId), Set.of());
        when(projectScopeApi.resolveCurrent(org.mockito.ArgumentMatchers.any())).thenReturn(scope);
        when(projectScopeApi.lockAndRevalidate(org.mockito.ArgumentMatchers.any())).thenReturn(scope);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                statement.executeUpdate("DELETE FROM sol_preparation_readiness_snapshot WHERE tenant_id=0 "
                        + "AND preparation_id IN (SELECT id FROM sol_preparation WHERE project_id=" + projectId + ")");
                statement.executeUpdate("DELETE FROM sol_preparation_item_waiver WHERE tenant_id=0 "
                        + "AND preparation_id IN (SELECT id FROM sol_preparation WHERE project_id=" + projectId + ")");
                statement.executeUpdate("DELETE FROM sol_preparation_source_reference WHERE tenant_id=0 "
                        + "AND preparation_id IN (SELECT id FROM sol_preparation WHERE project_id=" + projectId + ")");
                statement.executeUpdate("DELETE FROM sol_dynamic_form_instance WHERE tenant_id=0 "
                        + "AND preparation_id IN (SELECT id FROM sol_preparation WHERE project_id=" + projectId + ")");
                statement.executeUpdate("DELETE FROM sol_preparation_item WHERE tenant_id=0 "
                        + "AND preparation_id IN (SELECT id FROM sol_preparation WHERE project_id=" + projectId + ")");
                statement.executeUpdate("DELETE FROM sol_preparation WHERE tenant_id=0 AND project_id=" + projectId);
                statement.executeUpdate("DELETE FROM proj_project_task_execution_contract WHERE tenant_id=0 "
                        + "AND project_task_id=" + taskId);
                statement.executeUpdate("DELETE FROM proj_project_task WHERE tenant_id=0 AND project_id=" + projectId);
                statement.executeUpdate("DELETE FROM proj_project WHERE tenant_id=0 AND id=" + projectId);
                statement.executeUpdate("DELETE FROM plt_file_reference WHERE tenant_id=0 "
                        + "AND artifact_id=" + (projectId + 3));
                statement.executeUpdate("DELETE FROM plt_file_version WHERE tenant_id=0 "
                        + "AND artifact_id=" + (projectId + 3));
                statement.executeUpdate("DELETE FROM plt_file_artifact WHERE tenant_id=0 "
                        + "AND id=" + (projectId + 3));
                statement.executeUpdate("DELETE FROM plt_operation_audit WHERE tenant_id=0 "
                        + "AND correlation_id IN ('PRE02-IT-" + projectId + "','PRE02-REVIEW-" + projectId
                        + "','PRE02-READY-" + projectId + "','PRE02-PATCH-" + projectId + "')");
                statement.executeUpdate("DELETE FROM plt_idempotency_record WHERE tenant_id=0 "
                        + "AND actor_id=9 AND (idempotency_key='" + idempotencyKey + "' "
                        + "OR idempotency_key LIKE '%" + projectId + "%')");
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                }
            }
            return null;
        });
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void outerProjectTransactionCommitsProjectSolAndPlatformFactsTogether() {
        var result = transactionTemplate.execute(status -> {
            insertProjectTaskAndContract();
            return service.initialize(command());
        });

        assertEquals(projectId, result.projectId());
        assertEquals(1L, count("proj_project", "id", projectId));
        assertEquals(1L, count("proj_project_task", "id", taskId));
        assertEquals(1L, count("proj_project_task_execution_contract", "id", contractId));
        assertEquals(1L, count("sol_preparation", "project_id", projectId));
        assertEquals(5L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_preparation_item i "
                + "JOIN sol_preparation p ON p.tenant_id=i.tenant_id AND p.id=i.preparation_id "
                + "WHERE p.tenant_id=0 AND p.project_id=?", Long.class, projectId));
        assertEquals(5L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_dynamic_form_instance f "
                + "JOIN sol_preparation p ON p.tenant_id=f.tenant_id AND p.id=f.preparation_id "
                + "WHERE p.tenant_id=0 AND p.project_id=?", Long.class, projectId));
        assertEquals(1L, completedIdempotencyCount());
        assertEquals(1L, successAuditCount());
    }

    @Test
    void failureAfterSolWriteRollsBackProjectSolAndPlatformFactsTogether() {
        assertThrows(ForcedRollback.class, () -> transactionTemplate.executeWithoutResult(status -> {
            insertProjectTaskAndContract();
            service.initialize(command());
            throw new ForcedRollback();
        }));

        assertEquals(0L, count("proj_project", "id", projectId));
        assertEquals(0L, count("proj_project_task", "id", taskId));
        assertEquals(0L, count("proj_project_task_execution_contract", "id", contractId));
        assertEquals(0L, count("sol_preparation", "project_id", projectId));
        assertEquals(0L, completedIdempotencyCount());
        assertEquals(0L, successAuditCount());
    }

    @Test
    void submitConfirmAndReturnKeepOneCurrentBusinessVersion() {
        transactionTemplate.executeWithoutResult(status -> {
            insertProjectTaskAndContract();
            service.initialize(command());
        });
        Long preparationId = jdbcTemplate.queryForObject("SELECT id FROM sol_preparation "
                + "WHERE tenant_id=0 AND project_id=? AND current_marker=1", Long.class, projectId);
        jdbcTemplate.update("UPDATE sol_preparation_item SET assignee_user_id=9,site_result_code='READY',"
                + "evidence_policy_snapshot='{\"required\":false}',"
                + "source_policy_snapshot='{\"requirementCode\":\"NONE\"}' "
                + "WHERE tenant_id=0 AND preparation_id=?",
                preparationId);
        Long evidenceItemId = jdbcTemplate.queryForObject("SELECT id FROM sol_preparation_item "
                + "WHERE tenant_id=0 AND preparation_id=? ORDER BY sort_order,id LIMIT 1", Long.class,
                preparationId);
        insertFileFacts(evidenceItemId);
        jdbcTemplate.update("UPDATE sol_preparation_item SET evidence_policy_snapshot='{\"required\":true}',"
                        + "evidence_reference_snapshot=? WHERE tenant_id=0 AND preparation_id=? AND id=?",
                "[{\"artifactId\":" + (projectId + 3) + ",\"versionNo\":2,\"referenceKey\":\"SITE\","
                        + "\"fileFactVersion\":{\"artifactVersion\":1,\"referenceVersion\":2,"
                        + "\"availabilityVersion\":3},\"scopeVersion\":1}]",
                preparationId, evidenceItemId);
        jdbcTemplate.update("UPDATE sol_dynamic_form_instance SET value_snapshot='{\"siteCondition\":\"正常\"}' "
                + "WHERE tenant_id=0 AND preparation_id=?", preparationId);
        var actor = new PreparationItemApplicationService.Actor(0L, 9L, "PRE02-REVIEW-" + projectId);

        var submitted = reviewService.execute(new PreparationReviewCommand(PreparationReviewCommand.SUBMIT,
                preparationId, null, 0, null, 4, null, "REVIEW-SUBMIT-" + projectId), actor);
        assertEquals("PENDING_CONFIRMATION", submitted.statusCode());
        List<Long> itemIds = jdbcTemplate.queryForList("SELECT id FROM sol_preparation_item "
                + "WHERE tenant_id=0 AND preparation_id=? ORDER BY sort_order,id", Long.class, preparationId);
        int preparationVersion = 1;
        for (int index = 0; index < itemIds.size(); index++) {
            var confirmed = reviewService.execute(new PreparationReviewCommand(PreparationReviewCommand.CONFIRM,
                    preparationId, itemIds.get(index), preparationVersion, 0, 4, null,
                    "REVIEW-CONFIRM-" + projectId + "-" + index), actor);
            preparationVersion = confirmed.preparationVersion();
        }
        assertEquals("CONFIRMED", jdbcTemplate.queryForObject("SELECT status_code FROM sol_preparation "
                + "WHERE tenant_id=0 AND id=?", String.class, preparationId));

        var returned = reviewService.execute(new PreparationReviewCommand(PreparationReviewCommand.RETURN,
                preparationId, itemIds.getFirst(), preparationVersion, 1, 4, "补充现场资料",
                "REVIEW-RETURN-" + projectId), actor);

        assertEquals("DRAFT", returned.statusCode());
        assertEquals(2, returned.businessVersion());
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_preparation "
                + "WHERE tenant_id=0 AND project_id=? AND current_marker=1", Long.class, projectId));
        assertEquals("RETURNED", jdbcTemplate.queryForObject("SELECT status_code FROM sol_preparation "
                + "WHERE tenant_id=0 AND id=?", String.class, preparationId));
        assertEquals("PENDING", jdbcTemplate.queryForObject("SELECT confirmation_status_code "
                + "FROM sol_preparation_item WHERE tenant_id=0 AND preparation_id=? AND source_item_id=?",
                String.class, returned.currentPreparationId(), itemIds.getFirst()));
        assertEquals((long) itemIds.size() - 1, jdbcTemplate.queryForObject("SELECT COUNT(*) "
                + "FROM sol_preparation_item WHERE tenant_id=0 AND preparation_id=? "
                + "AND confirmation_status_code='CONFIRMED'", Long.class, returned.currentPreparationId()));
        assertEquals("DRAFT", jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(detail_snapshot,"
                + "'$.preparationBefore.status')) FROM plt_operation_audit WHERE tenant_id=0 "
                + "AND correlation_id=? AND operation_code='PREPARATION_SUBMIT' AND result_code='SUCCESS'",
                String.class, actor.correlationId()));
        assertEquals("PENDING_CONFIRMATION", jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT("
                + "detail_snapshot,'$.preparationAfter.status')) FROM plt_operation_audit WHERE tenant_id=0 "
                + "AND correlation_id=? AND operation_code='PREPARATION_SUBMIT' AND result_code='SUCCESS'",
                String.class, actor.correlationId()));
        assertEquals(5, jdbcTemplate.queryForObject("SELECT JSON_LENGTH(JSON_EXTRACT(detail_snapshot,'$.copyFacts')) "
                + "FROM plt_operation_audit WHERE tenant_id=0 AND correlation_id=? "
                + "AND operation_code='PREPARATION_RETURN' AND result_code='SUCCESS'", Integer.class,
                actor.correlationId()));
    }

    @Test
    void patchFreezesRealPltFileFactAndRejectsStaleVersionWithoutWrites() {
        transactionTemplate.executeWithoutResult(status -> {
            insertProjectTaskAndContract();
            service.initialize(command());
        });
        Long preparationId = jdbcTemplate.queryForObject("SELECT id FROM sol_preparation "
                + "WHERE tenant_id=0 AND project_id=? AND current_marker=1", Long.class, projectId);
        Map<String, Object> item = jdbcTemplate.queryForMap("SELECT i.id,i.version item_version,"
                + "f.version form_version FROM sol_preparation_item i JOIN sol_dynamic_form_instance f "
                + "ON f.tenant_id=i.tenant_id AND f.preparation_id=i.preparation_id AND f.item_id=i.id "
                + "WHERE i.tenant_id=0 AND i.preparation_id=? ORDER BY i.sort_order,i.id LIMIT 1", preparationId);
        Long itemId = ((Number) item.get("id")).longValue();
        jdbcTemplate.update("UPDATE sol_preparation_item SET assignee_user_id=9 "
                + "WHERE tenant_id=0 AND preparation_id=? AND id=?", preparationId, itemId);
        insertFileFacts(itemId);
        var actor = new PreparationItemApplicationService.Actor(0L, 9L, "PRE02-PATCH-" + projectId);
        PatchPreparationItemCommand command = new PatchPreparationItemCommand(preparationId, itemId,
                ((Number) item.get("item_version")).intValue(), 0, 0, 0,
                ((Number) item.get("form_version")).intValue(), 4,
                Set.of("siteResultCode", "evidenceReferences"), null, null, null, null,
                "READY", null, null, List.of(new PatchPreparationItemCommand.EvidenceReference(
                projectId + 3, 2, "SITE", new FileFactVersion(1, 2, 3), 1L)));

        var patched = itemService.patch(command, actor);

        assertEquals(1, patched.getPreparationVersion());
        assertEquals("READY", jdbcTemplate.queryForObject("SELECT site_result_code "
                + "FROM sol_preparation_item WHERE tenant_id=0 AND id=?", String.class, itemId));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT JSON_EXTRACT(evidence_reference_snapshot,"
                + "'$[0].fileFactVersion.availabilityVersion') FROM sol_preparation_item "
                + "WHERE tenant_id=0 AND id=?", Integer.class, itemId));
        jdbcTemplate.update("UPDATE plt_file_version SET availability_version=4 "
                + "WHERE tenant_id=0 AND artifact_id=? AND version_no=2", projectId + 3);
        PatchPreparationItemCommand stale = new PatchPreparationItemCommand(preparationId, itemId,
                patched.getItemVersion(), patched.getPreparationVersion(), patched.getInputVersion(), 0,
                patched.getFormVersion(), 4, Set.of("evidenceReferences"), null, null, null, null,
                null, null, null, command.evidenceReferences());

        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> itemService.patch(stale, actor));
        assertEquals(patched.getPreparationVersion(), jdbcTemplate.queryForObject(
                "SELECT version FROM sol_preparation WHERE tenant_id=0 AND id=?", Integer.class, preparationId));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND correlation_id=? AND operation_code='PREPARATION_ITEM_PATCH' "
                + "AND result_code='SUCCESS'", Long.class, actor.correlationId()));
    }

    @Test
    void confirmedNoSourcePreparationEvaluatesReadyAndSameVectorReplaysSnapshot() {
        PreparedReadiness prepared = prepareConfirmedNoSource();

        var first = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                prepared.preparationVersion(), 4, "READY-EVALUATE-" + projectId + "-1"), prepared.actor());
        var replay = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                first.readiness().preparationVersion(), 4, "READY-EVALUATE-" + projectId + "-2"), prepared.actor());

        assertEquals("READY", first.readiness().readinessStatus(),
                () -> "blockers=" + first.readiness().blockerCodes());
        assertEquals(true, first.readiness().snapshotCurrent());
        assertEquals(false, first.replayed());
        assertEquals(true, replay.replayed());
        assertEquals(first.readiness().latestSnapshotId(), replay.readiness().latestSnapshotId());
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_preparation_readiness_snapshot "
                + "WHERE tenant_id=0 AND preparation_id=?", Long.class, prepared.preparationId()));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT readiness_version FROM sol_preparation "
                + "WHERE tenant_id=0 AND id=?", Integer.class, prepared.preparationId()));
        assertEquals("READY", jdbcTemplate.queryForObject("SELECT result_code FROM sol_preparation_readiness_snapshot "
                + "WHERE tenant_id=0 AND preparation_id=?", String.class, prepared.preparationId()));
    }

    @Test
    void concurrentEvaluateHasOneWinnerAndOneSnapshot() throws Exception {
        PreparedReadiness prepared = prepareConfirmedNoSource();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> evaluateConcurrently(prepared, "A", ready, start));
            var second = executor.submit(() -> evaluateConcurrently(prepared, "B", ready, start));
            assertEquals(true, ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<String> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1L, outcomes.stream().filter("READY"::equals).count());
            assertEquals(1L, outcomes.stream().filter("VERSION_CONFLICT"::equals).count());
        }
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_preparation_readiness_snapshot "
                + "WHERE tenant_id=0 AND preparation_id=?", Long.class, prepared.preparationId()));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT readiness_version FROM sol_preparation "
                + "WHERE tenant_id=0 AND id=?", Integer.class, prepared.preparationId()));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND aggregate_type='Preparation' AND aggregate_key=? "
                + "AND operation_code='PREPARATION_EVALUATE_READINESS' AND result_code='SUCCESS'",
                Long.class, String.valueOf(prepared.preparationId())));
    }

    private PreparedReadiness prepareConfirmedNoSource() {
        transactionTemplate.executeWithoutResult(status -> {
            insertProjectTaskAndContract();
            service.initialize(command());
        });
        Long preparationId = jdbcTemplate.queryForObject("SELECT id FROM sol_preparation "
                + "WHERE tenant_id=0 AND project_id=? AND current_marker=1", Long.class, projectId);
        jdbcTemplate.update("UPDATE sol_preparation_item SET assignee_user_id=9,site_result_code='READY',"
                + "evidence_policy_snapshot='{\"required\":false}',"
                + "source_policy_snapshot='{\"requirementCode\":\"NONE\"}' "
                + "WHERE tenant_id=0 AND preparation_id=?", preparationId);
        jdbcTemplate.update("UPDATE sol_dynamic_form_instance SET value_snapshot='{\"siteCondition\":\"正常\"}' "
                + "WHERE tenant_id=0 AND preparation_id=?", preparationId);
        assertSourcePolicies(preparationId, List.of("NONE"));
        var actor = new PreparationItemApplicationService.Actor(0L, 9L, "PRE02-READY-" + projectId);
        reviewService.execute(new PreparationReviewCommand(PreparationReviewCommand.SUBMIT,
                preparationId, null, 0, null, 4, null, "READY-SUBMIT-" + projectId), actor);
        assertSourcePolicies(preparationId, List.of("NONE"));
        List<Long> itemIds = jdbcTemplate.queryForList("SELECT id FROM sol_preparation_item "
                + "WHERE tenant_id=0 AND preparation_id=? ORDER BY sort_order,id", Long.class, preparationId);
        int preparationVersion = 1;
        for (int index = 0; index < itemIds.size(); index++) {
            preparationVersion = reviewService.execute(new PreparationReviewCommand(PreparationReviewCommand.CONFIRM,
                    preparationId, itemIds.get(index), preparationVersion, 0, 4, null,
                    "READY-CONFIRM-" + projectId + "-" + index), actor).preparationVersion();
        }
        assertSourcePolicies(preparationId, List.of("NONE"));
        return new PreparedReadiness(preparationId, preparationVersion, actor);
    }

    private String evaluateConcurrently(PreparedReadiness prepared, String suffix,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent start timeout");
        try {
            var result = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                    prepared.preparationVersion(), 4, "READY-CONCURRENT-" + projectId + "-" + suffix),
                    new PreparationItemApplicationService.Actor(0L, 9L,
                            "PRE02-READY-CONCURRENT-" + projectId + "-" + suffix));
            return result.readiness().readinessStatus();
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException failure) {
            if (failure.getCode() == cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants
                    .PREPARATION_READINESS_VERSION_CONFLICT.getCode()) return "VERSION_CONFLICT";
            throw failure;
        }
    }

    private void insertProjectTaskAndContract() {
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'S1','ACTIVE','S1','UNASSIGNED',0,0,4,0)",
                projectId, "FSOL2-T4-" + projectId, projectId, 0,
                "F-SOL-002 Task4 " + projectId, projectId, "/", 0, 0);
        jdbcTemplate.update("INSERT INTO proj_project_task "
                        + "(id,project_id,task_code,name,root_task_id,tree_depth,state_machine_revision_id,"
                        + "stage_code,sort_order,source_definition_id,status,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,0,?,'S1',0,?,'PENDING_ASSIGN',2,0)",
                taskId, projectId, "PRE02-" + taskId, "PRE-02", taskId,
                stateMachineRevisionId, templateDefinitionId);
        jdbcTemplate.update("INSERT INTO proj_project_task_execution_contract "
                        + "(id,project_task_id,template_task_definition_id,work_binding_type_code,"
                        + "target_context_code,target_object_type,target_object_key,binding_parameter_snapshot,"
                        + "permission_policy_ref,completion_rule_type_code,completion_rule_snapshot,"
                        + "source_definition_version,contract_version,effective_from,effective_to,version,tenant_id) "
                        + "VALUES (?,?,?,'BUSINESS_OBJECT','SOL','SITE_SURVEY_PREPARATION',"
                        + "'PRE_02_SITE_SURVEY',?,'PRE_02_SITE_SURVEY_DEFAULT','BUSINESS_OBJECT_STATUS',"
                        + "'{\"requiredStatus\":\"DONE\"}',?,1,NOW(3),NULL,0,0)",
                contractId, taskId, templateDefinitionId, bindingSnapshot, sourceDefinitionVersion);
    }

    private void insertFileFacts(Long itemId) {
        long artifactId = projectId + 3;
        jdbcTemplate.update("INSERT INTO plt_file_artifact "
                        + "(id,name,category_code,owner_context,lifecycle_status_code,version,tenant_id) "
                        + "VALUES (?,'site.pdf','SITE_SURVEY_EVIDENCE','SOL','ACTIVE',1,0)", artifactId);
        jdbcTemplate.update("INSERT INTO plt_file_version "
                        + "(id,artifact_id,version_no,infra_file_id,availability_version,sha256,size_bytes,"
                        + "declared_media_type,detected_media_type,scan_status_code,availability_status_code,"
                        + "created_by,created_at,tenant_id) VALUES (?,?,2,?,3,?,100,'application/pdf',"
                        + "'application/pdf','PASSED','AVAILABLE',9,NOW(3),0)",
                artifactId + 1, artifactId, artifactId + 2, "a".repeat(64));
        jdbcTemplate.update("INSERT INTO plt_file_reference "
                        + "(id,owner_context,object_type,object_id,purpose_code,reference_key,artifact_id,"
                        + "file_version_no,sensitivity_code,status_code,scope_version,version,tenant_id) "
                        + "VALUES (?,'SOL','SITE_SURVEY_ITEM',?,'SITE_SURVEY_EVIDENCE','SITE',?,2,"
                        + "'INTERNAL','ACTIVE',1,2,0)", artifactId + 3, String.valueOf(itemId), artifactId);
    }

    private void assertSourcePolicies(Long preparationId, List<String> expected) {
        assertEquals(expected, jdbcTemplate.queryForList("SELECT DISTINCT JSON_UNQUOTE(JSON_EXTRACT("
                + "source_policy_snapshot,'$.requirementCode')) FROM sol_preparation_item "
                + "WHERE tenant_id=0 AND preparation_id=? ORDER BY 1", String.class, preparationId));
    }

    private record PreparedReadiness(Long preparationId, Integer preparationVersion,
                                     PreparationItemApplicationService.Actor actor) {
    }

    private PreparationInitializationCommand command() {
        return new PreparationInitializationCommand(projectId, taskId, contractId,
                4, 2, 1, PreparationInitializationApi.TRIGGER_PROJECT_CREATION,
                idempotencyKey, "PRE02-IT-" + projectId, 9L);
    }

    private long count(String table, String column, long value) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE tenant_id=0 AND " + column + "=?", Long.class, value);
    }

    private long completedIdempotencyCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record "
                + "WHERE tenant_id=0 AND scope_code='PREPARATION_INITIALIZE' AND actor_id=9 "
                + "AND idempotency_key=? AND status='COMPLETED'", Long.class, idempotencyKey);
    }

    private long successAuditCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND operation_code='PREPARATION_INITIALIZE' "
                + "AND correlation_id=? AND result_code='SUCCESS'", Long.class,
                "PRE02-IT-" + projectId);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    static class ForcedRollback extends RuntimeException {
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan(basePackageClasses = {PreparationMapper.class, DynamicFormInstanceMapper.class,
            ProjectMasterMapper.class, ProjectWorkBindingFactMapper.class,
            PlatformIdempotencyRecordMapper.class, ConfigMapper.class,
            FileArtifactMapper.class, FileVersionMapper.class, FileReferenceMapper.class})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PreparationInitializationService.class, PreparationItemApplicationService.class,
            PreparationReviewService.class,
            PreparationReadinessService.class,
            PreparationFilePolicyProvider.class, FileBusinessObjectPolicyRegistry.class,
            FileArtifactApiImpl.class,
            FixedSurveyFormCatalogProvider.class, ConfigApiImpl.class, ConfigServiceImpl.class,
            ProjectWorkBindingFactApiImpl.class,
            PlatformCommandExecutionApiImpl.class, OperationAuditApiImpl.class})
    static class TestApplication {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean ProjectParticipantFactApi participantFactApi() {
            return mock(ProjectParticipantFactApi.class);
        }

        @Bean ProjectScopeApi projectScopeApi() {
            return mock(ProjectScopeApi.class);
        }

        @Bean PermissionApi permissionApi() {
            return mock(PermissionApi.class);
        }

        @Bean ProjectOrganizationFactApi organizationFactApi() { return mock(ProjectOrganizationFactApi.class); }

        @Bean AdminUserApi adminUserApi() { return mock(AdminUserApi.class); }

        @Bean OrganizationScopeApi organizationScopeApi() { return mock(OrganizationScopeApi.class); }

        @Bean PreparationSourceProviderRegistry sourceProviderRegistry() {
            return new PreparationSourceProviderRegistry(List.of());
        }

    }
}
