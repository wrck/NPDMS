package cn.iocoder.yudao.module.pms.engineering.preparation;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.PreparationInitializationApi;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.dto.PreparationInitializationCommand;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.SiteSurveyReadinessQuery;
import cn.iocoder.yudao.module.pms.engineering.api.source.PreparationSourceFactProvider;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFact;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFactQuery;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.DynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.FixedSurveyFormCatalogProvider;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationInitializationService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationItemApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationFilePolicyProvider;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationReviewService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationReadinessService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationSourceProviderRegistry;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationSourceService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationWaiverService;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReviewCommand;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReadinessCommand;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PatchPreparationItemCommand;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformIdempotencyRecordMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @Resource PreparationSourceService sourceService;
    @Resource PreparationWaiverService waiverService;
    @Resource PreparationFilePolicyProvider filePolicyProvider;
    @Resource TestPreparationSourceProvider sourceProvider;
    @Resource JdbcTemplate jdbcTemplate;
    @Resource TransactionTemplate transactionTemplate;
    @Resource PermissionApi permissionApi;
    @Resource ProjectScopeApi projectScopeApi;
    @Resource ProjectParticipantFactApi participantFactApi;

    private long projectId;
    private long taskId;
    private long contractId;
    private long stateMachineRevisionId;
    private long templateDefinitionId;
    private int sourceDefinitionVersion;
    private String bindingSnapshot;
    private String idempotencyKey;
    private boolean approverRoleUnavailable;
    private long projectScopeVersion;

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
        org.mockito.Mockito.reset(permissionApi, projectScopeApi, participantFactApi);
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
        when(permissionApi.hasAnyPermissions(org.mockito.ArgumentMatchers.eq(8L),
                org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(true);
        projectScopeVersion = 1L;
        when(projectScopeApi.resolveCurrent(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation ->
                new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(
                        projectId, projectScopeVersion, Set.of(projectId), Set.of()));
        when(projectScopeApi.lockAndRevalidate(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation ->
                new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(
                        projectId, projectScopeVersion, Set.of(projectId), Set.of()));
        when(participantFactApi.lockAndRevalidate(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            var query = (cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery)
                    invocation.getArgument(0);
            if (approverRoleUnavailable && Long.valueOf(8L).equals(query.userId())) {
                throw new IllegalStateException("APPROVER_ROLE_CHANGED");
            }
            return new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact(
                    query.projectId(), query.userId(), query.requiredRoleCodes(), "PRIMARY", "ACTIVE", "S1",
                    query.expectedProjectVersion(), 1L);
        });
        approverRoleUnavailable = false;
        sourceProvider.reset();
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
        assertEquals(true, filePolicyProvider.inspect(new FileBusinessObjectPolicyQuery(0L, 9L,
                PreparationFilePolicyProvider.OWNER_CONTEXT, PreparationFilePolicyProvider.OBJECT_TYPE,
                String.valueOf(itemIds.getFirst()), PreparationFilePolicyProvider.PURPOSE_CODE, "SITE",
                FileActionCodes.REPLACE)).allowed());
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

    @Test
    void readinessInspectCannotReadOtherTenantAndRemainsReadOnly() {
        PreparedReadiness prepared = prepareConfirmedNoSource();
        long snapshotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sol_preparation_readiness_snapshot WHERE preparation_id=?",
                Long.class, prepared.preparationId());
        long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE aggregate_key=?",
                Long.class, String.valueOf(prepared.preparationId()));
        int preparationVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM sol_preparation WHERE tenant_id=0 AND id=?",
                Integer.class, prepared.preparationId());

        var failure = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> readinessService.inspect(new SiteSurveyReadinessQuery(projectId, prepared.preparationId()),
                        1L, 9L));

        assertEquals(cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants
                .PREPARATION_NOT_EXISTS.getCode(), failure.getCode());
        assertEquals(preparationVersion, jdbcTemplate.queryForObject(
                "SELECT version FROM sol_preparation WHERE tenant_id=0 AND id=?",
                Integer.class, prepared.preparationId()));
        assertEquals(snapshotCount, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sol_preparation_readiness_snapshot WHERE preparation_id=?",
                Long.class, prepared.preparationId()));
        assertEquals(auditCount, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE aggregate_key=?",
                Long.class, String.valueOf(prepared.preparationId())));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sol_preparation WHERE tenant_id=1 AND id=?",
                Long.class, prepared.preparationId()));
    }

    @Test
    void changedSourceFactExpiresReadySnapshotAndEvaluateAppendsNotReady() {
        OaSourceDraft draft = prepareOaSourceDraft();
        Long preparationId = draft.preparationId();
        Long oaItemId = draft.itemId();
        var actor = draft.actor();
        var refreshed = sourceService.refresh(new PreparationSourceService.SourceRefreshCommand(
                preparationId, oaItemId, 0, 0, 0, 0, null, 4,
                "OA", "REQUEST", "OA-" + projectId, "REF-" + projectId,
                "SOURCE-REFRESH-" + projectId), actor);
        assertEquals("SYNCED", refreshed.syncStatus());
        assertEquals("F1", refreshed.sourceFactVersion());
        assertEquals("W1", refreshed.sourceWatermark());

        int preparationVersion = reviewService.execute(new PreparationReviewCommand(
                PreparationReviewCommand.SUBMIT, preparationId, null, refreshed.preparationVersion(),
                null, 4, null, "SOURCE-SUBMIT-" + projectId), actor).preparationVersion();
        List<Long> itemIds = jdbcTemplate.queryForList("SELECT id FROM sol_preparation_item "
                + "WHERE tenant_id=0 AND preparation_id=? ORDER BY sort_order,id", Long.class, preparationId);
        for (int index = 0; index < itemIds.size(); index++) {
            preparationVersion = reviewService.execute(new PreparationReviewCommand(
                    PreparationReviewCommand.CONFIRM, preparationId, itemIds.get(index), preparationVersion,
                    0, 4, null, "SOURCE-CONFIRM-" + projectId + "-" + index), actor).preparationVersion();
        }
        var ready = readinessService.evaluate(new PreparationReadinessCommand(
                preparationId, preparationVersion, 4, "SOURCE-READY-" + projectId), actor);
        assertEquals("READY", ready.readiness().readinessStatus(),
                () -> "blockers=" + ready.readiness().blockerCodes());
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) "
                + "FROM sol_preparation_readiness_snapshot WHERE tenant_id=0 AND preparation_id=?",
                Long.class, preparationId));

        sourceProvider.changeTo("F2", "W2");
        int readyVersion = ready.readiness().preparationVersion();
        long auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND aggregate_key=?", Long.class, String.valueOf(preparationId));
        var inspected = readinessService.inspect(new SiteSurveyReadinessQuery(projectId, preparationId), 0L, 9L);
        assertEquals("NOT_READY", inspected.readinessStatus());
        assertEquals(false, inspected.snapshotCurrent());
        assertEquals(List.of("SOURCE_FACT_CHANGED"), inspected.blockerCodes());
        assertEquals(readyVersion, currentPreparationVersion(preparationId));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) "
                + "FROM sol_preparation_readiness_snapshot WHERE tenant_id=0 AND preparation_id=?",
                Long.class, preparationId));
        assertEquals(auditCount, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND aggregate_key=?", Long.class, String.valueOf(preparationId)));

        var notReady = readinessService.evaluate(new PreparationReadinessCommand(
                preparationId, readyVersion, 4, "SOURCE-CHANGED-" + projectId), actor);
        assertEquals("NOT_READY", notReady.readiness().readinessStatus());
        assertEquals(2L, jdbcTemplate.queryForObject("SELECT COUNT(*) "
                + "FROM sol_preparation_readiness_snapshot WHERE tenant_id=0 AND preparation_id=?",
                Long.class, preparationId));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) "
                + "FROM sol_preparation_readiness_snapshot WHERE tenant_id=0 AND preparation_id=? "
                + "AND result_code='NOT_READY'", Long.class, preparationId));
    }

    @Test
    void providerFailuresPersistErrorAndPreserveLastSuccess() {
        OaSourceDraft draft = prepareOaSourceDraft();
        sourceProvider.fail();

        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> sourceService.refresh(sourceCommand(draft, 0, 0, null,
                        "SOURCE-FIRST-FAIL-" + projectId), draft.actor()));
        Map<String, Object> firstFailure = sourceRow(draft.preparationId(), draft.itemId());
        assertEquals("ERROR", firstFailure.get("sync_status_code"));
        assertNull(firstFailure.get("normalized_result_code"));
        assertNull(firstFailure.get("last_success_result_code"));
        assertEquals(0, ((Number) firstFailure.get("version")).intValue());
        assertEquals(1, currentPreparationVersion(draft.preparationId()));

        sourceProvider.reset();
        var recovered = sourceService.refresh(sourceCommand(draft, 1, 1, 0,
                "SOURCE-RECOVER-" + projectId), draft.actor());
        assertEquals("SYNCED", recovered.syncStatus());
        assertEquals("F1", recovered.sourceFactVersion());
        assertEquals("W1", recovered.sourceWatermark());

        sourceProvider.fail();
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> sourceService.refresh(sourceCommand(draft, 2, 2, 1,
                        "SOURCE-LATER-FAIL-" + projectId), draft.actor()));
        Map<String, Object> laterFailure = sourceRow(draft.preparationId(), draft.itemId());
        assertEquals("ERROR", laterFailure.get("sync_status_code"));
        assertNull(laterFailure.get("normalized_result_code"));
        assertNull(laterFailure.get("source_fact_version"));
        assertNull(laterFailure.get("source_watermark"));
        assertEquals("APPROVED", laterFailure.get("last_success_result_code"));
        assertEquals("F1", laterFailure.get("last_success_fact_version"));
        assertEquals("W1", laterFailure.get("last_success_watermark"));
        assertEquals(2, ((Number) laterFailure.get("version")).intValue());
        assertEquals(3, currentPreparationVersion(draft.preparationId()));
        assertEquals(2L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND aggregate_type='PreparationSource' AND aggregate_key=? "
                + "AND operation_code='PREPARATION_SOURCE_REFRESH' AND result_code='REJECTED'",
                Long.class, String.valueOf(((Number) laterFailure.get("id")).longValue())));
    }

    @Test
    void approvedWaiverExpiresReadySnapshotWithoutInspectWritesAndAppendsOneNotReadySnapshot() throws Exception {
        WaiverPreparation prepared = prepareConfirmedOaWithoutSource();
        var manager = prepared.manager();
        var initial = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                prepared.preparationVersion(), 4, "WAIVER-INITIAL-" + projectId), manager);
        assertEquals("NOT_READY", initial.readiness().readinessStatus());
        assertEquals(List.of("SOURCE_PROVIDER_UNAVAILABLE"), initial.readiness().blockerCodes());

        LocalDateTime validUntil = LocalDateTime.now().plusSeconds(4);
        var created = createWaiver(prepared, validUntil, "WAIVER-CREATE-" + projectId);
        var submitted = waiverAction("SUBMIT", prepared, created.waiverId(), created.waiverVersion(), manager,
                "WAIVER-SUBMIT-" + projectId);
        var approver = new PreparationItemApplicationService.Actor(0L, 8L, "PRE02-WAIVER-APPROVE-" + projectId);
        waiverAction("APPROVE", prepared, submitted.waiverId(), submitted.waiverVersion(), approver,
                "WAIVER-APPROVE-" + projectId);

        PreparationVersions approvedVersions = preparationVersions(prepared.preparationId());
        var ready = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                approvedVersions.preparationVersion(), 4, "WAIVER-READY-" + projectId), manager);
        assertEquals("READY", ready.readiness().readinessStatus(),
                () -> "blockers=" + ready.readiness().blockerCodes());

        long waitMillis = Math.max(0L, Duration.between(LocalDateTime.now(),
                validUntil.plusNanos(200_000_000L)).toMillis());
        Thread.sleep(waitMillis);
        int readyPreparationVersion = currentPreparationVersion(prepared.preparationId());
        long snapshotCount = readinessSnapshotCount(prepared.preparationId());
        long auditCount = preparationAuditCount(prepared.preparationId());

        var inspected = readinessService.inspect(new SiteSurveyReadinessQuery(projectId,
                prepared.preparationId()), 0L, 9L);
        assertEquals("NOT_READY", inspected.readinessStatus());
        assertEquals(false, inspected.snapshotCurrent());
        assertEquals(List.of("SOURCE_PROVIDER_UNAVAILABLE"), inspected.blockerCodes());
        assertEquals(readyPreparationVersion, currentPreparationVersion(prepared.preparationId()));
        assertEquals(snapshotCount, readinessSnapshotCount(prepared.preparationId()));
        assertEquals(auditCount, preparationAuditCount(prepared.preparationId()));

        var expired = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                readyPreparationVersion, 4, "WAIVER-EXPIRED-" + projectId), manager);
        assertEquals("NOT_READY", expired.readiness().readinessStatus());
        assertEquals(snapshotCount + 1, readinessSnapshotCount(prepared.preparationId()));
        int expiredVersion = expired.readiness().preparationVersion();
        int expiredReadinessVersion = expired.readiness().readinessVersion();

        var replay = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                expiredVersion, 4, "WAIVER-EXPIRED-REPLAY-" + projectId), manager);
        assertEquals("NOT_READY", replay.readiness().readinessStatus());
        assertEquals(expiredReadinessVersion, replay.readiness().readinessVersion());
        assertEquals(snapshotCount + 1, readinessSnapshotCount(prepared.preparationId()));
    }

    @Test
    void pendingWaiverWithdrawalNeverMakesBlockerReady() {
        WaiverPreparation prepared = prepareConfirmedOaWithoutSource();
        var manager = prepared.manager();
        var initial = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                prepared.preparationVersion(), 4, "WITHDRAW-INITIAL-" + projectId), manager);
        assertEquals("NOT_READY", initial.readiness().readinessStatus());

        var created = createWaiver(prepared, LocalDateTime.now().plusMinutes(5),
                "WITHDRAW-CREATE-" + projectId);
        var submitted = waiverAction("SUBMIT", prepared, created.waiverId(), created.waiverVersion(), manager,
                "WITHDRAW-SUBMIT-" + projectId);
        var withdrawn = waiverAction("WITHDRAW", prepared, submitted.waiverId(), submitted.waiverVersion(), manager,
                "WITHDRAW-ACTION-" + projectId);
        assertEquals("WITHDRAWN", withdrawn.status());

        PreparationVersions versions = preparationVersions(prepared.preparationId());
        var evaluated = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                versions.preparationVersion(), 4, "WITHDRAW-EVALUATE-" + projectId), manager);
        assertEquals("NOT_READY", evaluated.readiness().readinessStatus());
        assertEquals(List.of("SOURCE_PROVIDER_UNAVAILABLE"), evaluated.readiness().blockerCodes());
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_preparation_readiness_snapshot "
                + "WHERE tenant_id=0 AND preparation_id=? AND result_code='READY'",
                Long.class, prepared.preparationId()));
    }

    @Test
    void approverRoleChangeBeforeDecisionKeepsWaiverPendingAndBlockerNotReady() {
        WaiverPreparation prepared = prepareConfirmedOaWithoutSource();
        var manager = prepared.manager();
        var initial = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                prepared.preparationVersion(), 4, "ROLE-INITIAL-" + projectId), manager);
        assertEquals("NOT_READY", initial.readiness().readinessStatus());
        var created = createWaiver(prepared, LocalDateTime.now().plusMinutes(5),
                "ROLE-CREATE-" + projectId);
        var submitted = waiverAction("SUBMIT", prepared, created.waiverId(), created.waiverVersion(), manager,
                "ROLE-SUBMIT-" + projectId);
        PreparationVersions beforeDecision = preparationVersions(prepared.preparationId());

        approverRoleUnavailable = true;
        var approver = new PreparationItemApplicationService.Actor(0L, 8L, "PRE02-ROLE-APPROVE-" + projectId);
        assertThrows(IllegalStateException.class, () -> waiverAction("APPROVE", prepared,
                submitted.waiverId(), submitted.waiverVersion(), approver, "ROLE-APPROVE-" + projectId));

        assertEquals("PENDING_APPROVAL", jdbcTemplate.queryForObject("SELECT status_code "
                + "FROM sol_preparation_item_waiver WHERE tenant_id=0 AND id=?",
                String.class, submitted.waiverId()));
        assertEquals(beforeDecision, preparationVersions(prepared.preparationId()));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_preparation_item_waiver "
                + "WHERE tenant_id=0 AND id=? AND status_code='APPROVED'", Long.class, submitted.waiverId()));

        var evaluated = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                beforeDecision.preparationVersion(), 4, "ROLE-EVALUATE-" + projectId), manager);
        assertEquals("NOT_READY", evaluated.readiness().readinessStatus());
        assertEquals(List.of("SOURCE_PROVIDER_UNAVAILABLE"), evaluated.readiness().blockerCodes());
    }

    @Test
    void changedFileFactExpiresReadySnapshotWithoutInspectWritesAndAppendsNotReady() {
        PreparedReadiness prepared = prepareConfirmedWithFile();
        var ready = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                prepared.preparationVersion(), 4, "FILE-READY-" + projectId), prepared.actor());
        assertEquals("READY", ready.readiness().readinessStatus());

        jdbcTemplate.update("UPDATE plt_file_version SET availability_version=4 "
                + "WHERE tenant_id=0 AND artifact_id=? AND version_no=2", projectId + 3);
        int readyPreparationVersion = ready.readiness().preparationVersion();
        long snapshotCount = readinessSnapshotCount(prepared.preparationId());
        long auditCount = preparationAuditCount(prepared.preparationId());

        var inspected = readinessService.inspect(new SiteSurveyReadinessQuery(projectId,
                prepared.preparationId()), 0L, 9L);
        assertEquals("NOT_READY", inspected.readinessStatus());
        assertEquals(false, inspected.snapshotCurrent());
        assertEquals(List.of("FILE_FACT_CHANGED"), inspected.blockerCodes());
        assertEquals(readyPreparationVersion, currentPreparationVersion(prepared.preparationId()));
        assertEquals(snapshotCount, readinessSnapshotCount(prepared.preparationId()));
        assertEquals(auditCount, preparationAuditCount(prepared.preparationId()));

        var notReady = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                readyPreparationVersion, 4, "FILE-CHANGED-" + projectId), prepared.actor());
        assertEquals("NOT_READY", notReady.readiness().readinessStatus());
        assertEquals(List.of("FILE_FACT_CHANGED"), notReady.readiness().blockerCodes());
        assertEquals(snapshotCount + 1, readinessSnapshotCount(prepared.preparationId()));
    }

    @Test
    void changedProjectScopeExpiresReadySnapshotAndExplicitEvaluateFreezesNewScope() {
        PreparedReadiness prepared = prepareConfirmedNoSource();
        var ready = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                prepared.preparationVersion(), 4, "SCOPE-READY-" + projectId), prepared.actor());
        assertEquals("READY", ready.readiness().readinessStatus());
        assertEquals(1L, ready.readiness().projectScopeVersion());

        projectScopeVersion = 2L;
        int readyPreparationVersion = ready.readiness().preparationVersion();
        long snapshotCount = readinessSnapshotCount(prepared.preparationId());
        long auditCount = preparationAuditCount(prepared.preparationId());
        var inspected = readinessService.inspect(new SiteSurveyReadinessQuery(projectId,
                prepared.preparationId()), 0L, 9L);
        assertEquals("NOT_READY", inspected.readinessStatus());
        assertEquals(false, inspected.snapshotCurrent());
        assertEquals(2L, inspected.projectScopeVersion());
        assertEquals(List.of(), inspected.blockerCodes());
        assertEquals(readyPreparationVersion, currentPreparationVersion(prepared.preparationId()));
        assertEquals(snapshotCount, readinessSnapshotCount(prepared.preparationId()));
        assertEquals(auditCount, preparationAuditCount(prepared.preparationId()));

        var refreshed = readinessService.evaluate(new PreparationReadinessCommand(prepared.preparationId(),
                readyPreparationVersion, 4, "SCOPE-REFRESH-" + projectId), prepared.actor());
        assertEquals("READY", refreshed.readiness().readinessStatus());
        assertEquals(true, refreshed.readiness().snapshotCurrent());
        assertEquals(2L, refreshed.readiness().projectScopeVersion());
        assertEquals(false, refreshed.replayed());
        assertEquals(snapshotCount + 1, readinessSnapshotCount(prepared.preparationId()));
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

    private PreparedReadiness prepareConfirmedWithFile() {
        transactionTemplate.executeWithoutResult(status -> {
            insertProjectTaskAndContract();
            service.initialize(command());
        });
        Long preparationId = jdbcTemplate.queryForObject("SELECT id FROM sol_preparation "
                + "WHERE tenant_id=0 AND project_id=? AND current_marker=1", Long.class, projectId);
        Long itemId = jdbcTemplate.queryForObject("SELECT id FROM sol_preparation_item "
                + "WHERE tenant_id=0 AND preparation_id=? ORDER BY sort_order,id LIMIT 1",
                Long.class, preparationId);
        jdbcTemplate.update("UPDATE sol_preparation_item SET assignee_user_id=9,site_result_code='READY',"
                + "evidence_policy_snapshot='{\"required\":false}',"
                + "source_policy_snapshot='{\"requirementCode\":\"NONE\"}' "
                + "WHERE tenant_id=0 AND preparation_id=?", preparationId);
        insertFileFacts(itemId);
        jdbcTemplate.update("UPDATE sol_preparation_item SET evidence_policy_snapshot='{\"required\":true}',"
                        + "evidence_reference_snapshot=? WHERE tenant_id=0 AND id=?",
                "[{\"artifactId\":" + (projectId + 3) + ",\"versionNo\":2,\"referenceKey\":\"SITE\","
                        + "\"fileFactVersion\":{\"artifactVersion\":1,\"referenceVersion\":2,"
                        + "\"availabilityVersion\":3},\"scopeVersion\":1}]", itemId);
        jdbcTemplate.update("UPDATE sol_dynamic_form_instance SET value_snapshot='{\"siteCondition\":\"正常\"}' "
                + "WHERE tenant_id=0 AND preparation_id=?", preparationId);
        var actor = new PreparationItemApplicationService.Actor(0L, 9L, "PRE02-FILE-" + projectId);
        int preparationVersion = reviewService.execute(new PreparationReviewCommand(
                PreparationReviewCommand.SUBMIT, preparationId, null, 0,
                null, 4, null, "FILE-SUBMIT-" + projectId), actor).preparationVersion();
        List<Long> itemIds = jdbcTemplate.queryForList("SELECT id FROM sol_preparation_item "
                + "WHERE tenant_id=0 AND preparation_id=? ORDER BY sort_order,id", Long.class, preparationId);
        for (int index = 0; index < itemIds.size(); index++) {
            preparationVersion = reviewService.execute(new PreparationReviewCommand(
                    PreparationReviewCommand.CONFIRM, preparationId, itemIds.get(index), preparationVersion,
                    0, 4, null, "FILE-CONFIRM-" + projectId + "-" + index), actor).preparationVersion();
        }
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

    private int currentPreparationVersion(Long preparationId) {
        return jdbcTemplate.queryForObject("SELECT version FROM sol_preparation WHERE tenant_id=0 AND id=?",
                Integer.class, preparationId);
    }

    private OaSourceDraft prepareOaSourceDraft() {
        transactionTemplate.executeWithoutResult(status -> {
            insertProjectTaskAndContract();
            service.initialize(command());
        });
        Long preparationId = jdbcTemplate.queryForObject("SELECT id FROM sol_preparation "
                + "WHERE tenant_id=0 AND project_id=? AND current_marker=1", Long.class, projectId);
        Long itemId = jdbcTemplate.queryForObject("SELECT id FROM sol_preparation_item "
                + "WHERE tenant_id=0 AND preparation_id=? ORDER BY sort_order,id LIMIT 1", Long.class, preparationId);
        jdbcTemplate.update("UPDATE sol_preparation_item SET assignee_user_id=9,site_result_code='READY',"
                + "evidence_policy_snapshot='{\"required\":false}',"
                + "source_policy_snapshot='{\"requirementCode\":\"NONE\"}' "
                + "WHERE tenant_id=0 AND preparation_id=?", preparationId);
        jdbcTemplate.update("UPDATE sol_preparation_item SET source_policy_snapshot="
                + "'{\"requirementCode\":\"OA_REQUIRED\"}' WHERE tenant_id=0 AND id=?", itemId);
        jdbcTemplate.update("UPDATE sol_dynamic_form_instance SET value_snapshot="
                + "'{\"siteCondition\":\"正常\"}' WHERE tenant_id=0 AND preparation_id=?", preparationId);
        return new OaSourceDraft(preparationId, itemId,
                new PreparationItemApplicationService.Actor(0L, 9L, "PRE02-SOURCE-" + projectId));
    }

    private WaiverPreparation prepareConfirmedOaWithoutSource() {
        OaSourceDraft draft = prepareOaSourceDraft();
        jdbcTemplate.update("UPDATE sol_preparation_item SET waiver_policy_snapshot="
                + "'{\"allowed\":true,\"approvalRoleCode\":\"SERVICE_MANAGER_L1\"}' "
                + "WHERE tenant_id=0 AND id=?", draft.itemId());
        int preparationVersion = reviewService.execute(new PreparationReviewCommand(
                PreparationReviewCommand.SUBMIT, draft.preparationId(), null, 0,
                null, 4, null, "WAIVER-PREPARE-SUBMIT-" + projectId), draft.actor()).preparationVersion();
        List<Long> itemIds = jdbcTemplate.queryForList("SELECT id FROM sol_preparation_item "
                + "WHERE tenant_id=0 AND preparation_id=? ORDER BY sort_order,id", Long.class,
                draft.preparationId());
        for (int index = 0; index < itemIds.size(); index++) {
            preparationVersion = reviewService.execute(new PreparationReviewCommand(
                    PreparationReviewCommand.CONFIRM, draft.preparationId(), itemIds.get(index), preparationVersion,
                    0, 4, null, "WAIVER-PREPARE-CONFIRM-" + projectId + "-" + index),
                    draft.actor()).preparationVersion();
        }
        return new WaiverPreparation(draft.preparationId(), draft.itemId(), preparationVersion, draft.actor());
    }

    private PreparationWaiverService.WaiverResult createWaiver(WaiverPreparation prepared,
            LocalDateTime validUntil, String key) {
        PreparationVersions versions = preparationVersions(prepared.preparationId());
        return waiverService.execute(new PreparationWaiverService.WaiverCommand("CREATE",
                prepared.preparationId(), prepared.itemId(), null, versions.preparationVersion(),
                versions.inputVersion(), versions.readinessVersion(), currentItemVersion(prepared.itemId()),
                null, 4, List.of("SOURCE_PROVIDER_UNAVAILABLE"), "OA来源暂不可用", "就绪依据暂缺",
                "上线前补齐OA事实", LocalDateTime.now().minusMinutes(1), validUntil, null, key),
                prepared.manager());
    }

    private PreparationWaiverService.WaiverResult waiverAction(String action, WaiverPreparation prepared,
            Long waiverId, Integer waiverVersion, PreparationItemApplicationService.Actor actor, String key) {
        PreparationVersions versions = preparationVersions(prepared.preparationId());
        return waiverService.execute(new PreparationWaiverService.WaiverCommand(action,
                prepared.preparationId(), prepared.itemId(), waiverId, versions.preparationVersion(),
                versions.inputVersion(), versions.readinessVersion(), currentItemVersion(prepared.itemId()),
                waiverVersion, 4, List.of(), null, null, null, null, null,
                "APPROVE".equals(action) ? "同意" : null, key), actor);
    }

    private PreparationVersions preparationVersions(Long preparationId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT version,input_version,readiness_version "
                + "FROM sol_preparation WHERE tenant_id=0 AND id=?", preparationId);
        return new PreparationVersions(((Number) row.get("version")).intValue(),
                ((Number) row.get("input_version")).intValue(),
                ((Number) row.get("readiness_version")).intValue());
    }

    private int currentItemVersion(Long itemId) {
        return jdbcTemplate.queryForObject("SELECT version FROM sol_preparation_item WHERE tenant_id=0 AND id=?",
                Integer.class, itemId);
    }

    private long readinessSnapshotCount(Long preparationId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_preparation_readiness_snapshot "
                + "WHERE tenant_id=0 AND preparation_id=?", Long.class, preparationId);
    }

    private long preparationAuditCount(Long preparationId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND aggregate_key=?", Long.class, String.valueOf(preparationId));
    }

    private PreparationSourceService.SourceRefreshCommand sourceCommand(OaSourceDraft draft,
            int preparationVersion, int inputVersion, Integer sourceVersion, String key) {
        return new PreparationSourceService.SourceRefreshCommand(draft.preparationId(), draft.itemId(),
                preparationVersion, inputVersion, 0, 0, sourceVersion, 4,
                "OA", "REQUEST", "OA-" + projectId, "REF-" + projectId, key);
    }

    private Map<String, Object> sourceRow(Long preparationId, Long itemId) {
        return jdbcTemplate.queryForMap("SELECT id,sync_status_code,normalized_result_code,source_fact_version,"
                + "source_watermark,last_success_result_code,last_success_fact_version,last_success_watermark,version "
                + "FROM sol_preparation_source_reference WHERE tenant_id=0 AND preparation_id=? AND item_id=?",
                preparationId, itemId);
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

    private record OaSourceDraft(Long preparationId, Long itemId,
                                 PreparationItemApplicationService.Actor actor) {
    }

    private record WaiverPreparation(Long preparationId, Long itemId, Integer preparationVersion,
                                     PreparationItemApplicationService.Actor manager) {
    }

    private record PreparationVersions(Integer preparationVersion, Integer inputVersion,
                                       Integer readinessVersion) {
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
            PreparationSourceService.class,
            PreparationWaiverService.class,
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

        @Bean TestPreparationSourceProvider testPreparationSourceProvider() {
            return new TestPreparationSourceProvider();
        }

        @Bean PreparationSourceProviderRegistry sourceProviderRegistry(TestPreparationSourceProvider provider) {
            return new PreparationSourceProviderRegistry(List.of(provider));
        }

    }

    static class TestPreparationSourceProvider implements PreparationSourceFactProvider {
        private String factVersion;
        private String watermark;
        private boolean unavailable;

        void reset() {
            factVersion = "F1";
            watermark = "W1";
            unavailable = false;
        }

        void changeTo(String nextFactVersion, String nextWatermark) {
            factVersion = nextFactVersion;
            watermark = nextWatermark;
        }

        void fail() {
            unavailable = true;
        }

        @Override
        public String sourceTypeCode() {
            return "OA";
        }

        @Override
        public PreparationSourceFact inspect(PreparationSourceFactQuery query) {
            return fact(query.projectId(), query.itemId(), query.sourceObjectType(), query.sourceObjectId(),
                    query.sourceReferenceKey());
        }

        @Override
        public PreparationSourceFact lockAndRevalidate(PreparationSourceFactRevalidationQuery query) {
            return fact(query.projectId(), query.itemId(), query.sourceObjectType(), query.sourceObjectId(),
                    query.sourceReferenceKey());
        }

        private PreparationSourceFact fact(Long projectId, Long itemId, String objectType, String objectId,
                String referenceKey) {
            if (unavailable) {
                throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                        cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants
                                .PREPARATION_SOURCE_UNAVAILABLE);
            }
            return new PreparationSourceFact(projectId, itemId, "OA", objectType, objectId, referenceKey,
                    "APPROVED", factVersion, watermark, true);
        }
    }
}
