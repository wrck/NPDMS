package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationSaveReqVO;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistBindingRuleRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistItemDefinitionRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemResultMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureAttachmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverCollectionEvidenceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistBindingRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistItemDefinitionRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.ApproveCutoverApprovalCommand;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.AssessmentReviewInput;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.ReviewItemInput;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistConfigurationQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistMatcher;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverNavigationDecisionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverNavigationDecisionQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.GenerateChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SubmitChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverCollectionPort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SubmitCutoverClosureCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.AttachmentPurpose;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.closure.result.CutoverClosureCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.CreateCutoverPlanDraftCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.SaveCutoverPlanDraftCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.SubmitCutoverPlanCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanRules;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.CutoverPlanCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.SubmitCutoverPlanResult;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.CreateCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.SaveCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.SubmitCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceProductTypePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.result.CutoverAssessmentCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.task.result.CutoverTaskCommandResult;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
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
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CUT-01~06 已实现内核在测试作用域受控Owner事实下的单任务正向全链。 */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CutoverFullFlowMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CutoverFullFlowMySqlTest {

    private static final long ACTOR_ID = 8L;
    private static final long PROJECT_ID = 100L;
    private static final long DEVICE_ID = 301L;
    private static final long PROJECT_SCOPE_VERSION = 30L;

    @Resource cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper runtimeNodes;
    @Resource cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper taskRows;
    @Resource cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskListMapper workbenchRows;
    @Resource cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.CutoverDashboardCandidateMapper dashboardRows;
    @Resource org.springframework.transaction.PlatformTransactionManager transactions;
    @Resource cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationMetadataStore metadata;
    @Resource cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldDefinitionStore fields;
    @Resource JdbcTemplate jdbc;
    @Resource CutoverConfigurationRevisionMapper configurationMapper;
    @Resource CutoverChecklistItemDefinitionRevisionMapper definitionMapper;
    @Resource CutoverChecklistBindingRuleRevisionMapper ruleMapper;
    @Resource CutoverTaskApplicationService taskService;
    @Resource CutoverChecklistApplicationService checklistService;
    @Resource CutoverPlanApplicationService planService;
    @Resource CutoverApprovalApplicationService approvalService;
    @Resource CutoverClosureApplicationService closureService;
    @Resource CutoverClosureMapper closureMapper;
    @Resource cn.iocoder.yudao.module.pms.cutover.api.result.CutoverResultApi resultApi;
    @Resource CutoverApprovalPositiveLoopMySqlTest.ControlledOwners owners;
    @Resource CutoverApprovalPositiveLoopMySqlTest.CurrentActor actor;

    private long tenantId;
    private long configurationId;
    private Long roleQueryInterferenceId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        Map<String, String> env = System.getenv();
        String database = required(env, "NPDMS_DB_NAME");
        if (!Set.of("npdms_cut_structured_20261014_r6", "npdms_cut_consolidation_20261014_r2").contains(database)) {
            throw new IllegalStateException("Requires the isolated CUT validation schema");
        }
        String port = env.getOrDefault("NPDMS_MYSQL_PORT", "24306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(env, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(env, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.cutover");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        long suffix = Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000L);
        tenantId = 994_100_000_000L + suffix;
        configurationId = 994_200_000_000L + suffix;
        TenantContextHolder.setTenantId(tenantId);
        actor.use(ACTOR_ID);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(ACTOR_ID).setTenantId(tenantId),
                new MockHttpServletRequest());
        new org.springframework.transaction.support.TransactionTemplate(transactions)
                .executeWithoutResult(status -> insertConfiguration());
    }

    @AfterEach
    void tearDown() {
        // 独占随机租户保留于专用验证库；删除已提交历史会违反不可变来源保护。
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"A,SUCCESS", "B,SUCCESS", "C,FAILED", "D,SUCCESS"})
    void completesTaskThroughArchiveWithControlledOwnerFacts(String grade, String finalResult) {
        CutoverTaskCommandResult created = taskService.create(createCommand());
        CutoverAssessmentCommandResult assessment = taskService.saveAssessment(new SaveCutoverAssessmentCommand(
                tenantId, ACTOR_ID, created.taskId(), created.version(), 0L,
                new CutoverAssessmentAnswers("HIGH", "MEDIUM", "LOW", true), grade, "assessment-save", "corr-assessment-save"));
        CutoverTaskCommandResult p3 = taskService.submitAssessment(new SubmitCutoverAssessmentCommand(
                tenantId, ACTOR_ID, created.taskId(), assessment.taskVersion(), assessment.assessmentRowVersion(),
                "assessment-submit", "corr-assessment-submit"));

        ChecklistCommandResult p4;
        if ("D".equals(grade)) {
            p4 = new ChecklistCommandResult(created.taskId(), null, null, null, "SKIPPED", "P4", p3.version(), false);
        } else {
        ChecklistCommandResult generated = checklistService.generate(new GenerateChecklistCommand(tenantId, ACTOR_ID, created.taskId(), p3.version(), assessment.assessmentVersion(), PROJECT_SCOPE_VERSION, List.of("FW"), Map.of(), "checklist-generate", "corr-checklist-generate"));
        ChecklistCommandResult savedChecklist = checklistService.save(new SaveChecklistCommand(tenantId,
                ACTOR_ID,
                created.taskId(),
                p3.version(),
                generated.checklistId(),
                generated.checklistFactVersion(),
                PROJECT_SCOPE_VERSION,
                List.of(new SaveChecklistCommand.DirectAnswer("PRECHECK-READY", "{\"value\":\"YES\"}")),
                cn.iocoder.yudao.module.pms.cutover.support.CutoverTestCommands.intent()));
        p4 = checklistService.submit(new SubmitChecklistCommand(
                tenantId, ACTOR_ID, created.taskId(), p3.version(), assessment.assessmentVersion(),
                generated.checklistId(), savedChecklist.checklistFactVersion(), PROJECT_SCOPE_VERSION,
                "checklist-submit", "corr-checklist-submit"));

        }

        preparePlanSource(created.taskId(), assessment, p4, grade);
        CutoverPlanCommandResult plan = planService.createDraft(new CreateCutoverPlanDraftCommand(
                tenantId, ACTOR_ID, created.taskId(), p4.taskVersion(), "D".equals(grade) ? "ONLINE_TEMPLATE_SIMPLE_D" : "ONLINE_TEMPLATE_STANDARD", null, null,
                "plan-create", "corr-plan-create"));
        CutoverPlanCommandResult savedPlan = "A".equals(grade)
                ? exerciseSupportDraft(new SupportDraftFixture(created.taskId(), p4.taskVersion(), plan))
                : planService.saveDraft(new SaveCutoverPlanDraftCommand(
                tenantId, ACTOR_ID, created.taskId(), p4.taskVersion(), plan.planVersion(), "D".equals(grade) ? simpleContent() : standardContent(),
                "plan-save", "corr-plan-save"));
        SubmitCutoverPlanResult p5 = planService.submit(new SubmitCutoverPlanCommand(
                tenantId, ACTOR_ID, created.taskId(), p4.taskVersion(), savedPlan.planVersion(),
                "plan-submit", "corr-plan-submit"));

        String frozenDescription = text("SELECT project_description FROM cut_plan_revision WHERE tenant_id=? AND id=?",
                tenantId, savedPlan.planRevisionId());
        var frozenPlanChange = assertThrows(org.springframework.jdbc.UncategorizedSQLException.class, () -> jdbc.update(
                "UPDATE cut_plan_revision SET project_description='unauthorized frozen plan change' WHERE tenant_id=? AND id=?",
                tenantId, savedPlan.planRevisionId()));
        assertEquals(1644, frozenPlanChange.getSQLException().getErrorCode());
        assertTrue(frozenPlanChange.getSQLException().getMessage().contains("CUT_IMMUTABLE_HISTORY: cut_plan_revision"));
        assertEquals(frozenDescription, text("SELECT project_description FROM cut_plan_revision WHERE tenant_id=? AND id=?",
                tenantId, savedPlan.planRevisionId()));

        if ("A".equals(grade)) {
            assertEquals(5, number("SELECT COUNT(*) FROM cut_cutover_support_arrangement WHERE tenant_id=? AND plan_revision_id=?",
                    tenantId, savedPlan.planRevisionId()));
            var protectedChange = assertThrows(org.springframework.jdbc.UncategorizedSQLException.class, () -> jdbc.update(
                    "UPDATE cut_cutover_support_arrangement SET sort_order=sort_order+1 WHERE tenant_id=? AND plan_revision_id=?",
                    tenantId, savedPlan.planRevisionId()));
            assertEquals(1644, protectedChange.getSQLException().getErrorCode());
            assertTrue(protectedChange.getSQLException().getMessage().contains("CUT_IMMUTABLE_HISTORY"));
        }

        approveAll(created.taskId(), p5.taskVersion(), p5.approvalInstanceId(), grade);
        long p6TaskVersion = number("SELECT version FROM cut_task WHERE tenant_id=? AND id=?", tenantId, created.taskId());
        String remainingItems = "平台验收遗留项：保留复核说明及后续安排。";
        CutoverClosureCommandResult draft = closureService.save(new SaveCutoverClosureCommand(
                tenantId, ACTOR_ID, created.taskId(), p6TaskVersion, null,
                new SaveCutoverClosureCommand.ClosureContent(true, null, true, null, true, null,
                        false, null, null, remainingItems, null, closureAttachments()),
                "closure-save", "corr-closure-save"));
        var closureQuery = new cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureRowQuery(
                tenantId, created.taskId());
        var reopenedDraft = closureMapper.selectByTask(closureQuery);
        assertEquals(remainingItems, reopenedDraft.getLegacyItems());
        CutoverClosureCommandResult resaved = closureService.save(new SaveCutoverClosureCommand(
                tenantId, ACTOR_ID, created.taskId(), p6TaskVersion, reopenedDraft.getVersion(),
                new SaveCutoverClosureCommand.ClosureContent(true, null, true, null, true, null,
                        false, null, null, reopenedDraft.getLegacyItems(), null, closureAttachments()),
                "closure-resave", "corr-closure-resave"));
        assertEquals(remainingItems, closureMapper.selectByTask(closureQuery).getLegacyItems());
        SubmitCutoverClosureCommand submit = new SubmitCutoverClosureCommand(
                tenantId, ACTOR_ID, created.taskId(), p6TaskVersion, resaved.closureId(), resaved.closureVersion(),
                finalResult, "closure-submit", "corr-closure-submit");
        CutoverClosureCommandResult archived = closureService.submit(submit);
        CutoverClosureCommandResult replayed = closureService.submit(submit);

        assertEquals("SUBMITTED", archived.closureStatus());
        assertTrue(replayed.replayed());
        assertEquals("P6", text("SELECT current_stage_code FROM cut_task WHERE tenant_id=? AND id=?",
                tenantId, created.taskId()));
        assertEquals("ARCHIVED", text("SELECT task_status_code FROM cut_task WHERE tenant_id=? AND id=?",
                tenantId, created.taskId()));
        assertEquals("SUBMITTED", text("SELECT status_code FROM cut_cutover_closure WHERE tenant_id=? AND id=?",
                tenantId, draft.closureId()));
        assertEquals(remainingItems, closureMapper.selectByTask(closureQuery).getLegacyItems());
        assertEquals(0, number("SELECT COUNT(*) FROM cut_task_device_scope WHERE tenant_id=? "
                + "AND task_id=? AND active_marker=1", tenantId, created.taskId()));
        for (String trigger : List.of("P1_ACCEPTED", "P2_ASSESSMENT_SUBMITTED", "P3_CHECKLIST_SUBMITTED",
                "P4_PLAN_SUBMITTED", "P5_APPROVAL_APPROVED", "P6_CLOSURE_SUBMITTED")) {
            assertEquals("D".equals(grade) && "P3_CHECKLIST_SUBMITTED".equals(trigger) ? 0 : 1, number("SELECT COUNT(*) FROM cut_task_stage_history WHERE tenant_id=? "
                    + "AND task_id=? AND trigger_type=?", tenantId, created.taskId(), trigger));
        }
        assertEquals(1, number("SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=? "
                + "AND event_type='CutoverApproved'", tenantId));
        assertEquals("SUCCESS".equals(finalResult) ? 1 : 0, number("SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=? "
                + "AND event_type='CutoverCompleted'", tenantId));
        assertEquals(1, number("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? "
                + "AND correlation_id='corr-closure-submit'", tenantId));
        var results = resultApi.successes(new cn.iocoder.yudao.module.pms.cutover.api.result.CutoverResultApi.ProjectQuery(tenantId, PROJECT_ID));
        assertEquals("SUCCESS".equals(finalResult) ? 1 : 0, results.size());
        if (!results.isEmpty()) {
            assertEquals(created.taskId(), results.getFirst().taskId());
            assertEquals(List.of(DEVICE_ID), results.getFirst().devices().stream()
                    .map(cn.iocoder.yudao.module.pms.cutover.api.result.CutoverResultApi.Device::deviceId).toList());
        }
    }

    private CreateCutoverTaskCommand createCommand() {
        return new CreateCutoverTaskCommand(tenantId, ACTOR_ID, "task-create", "corr-task-create",
                "SELF_CREATED", PROJECT_ID, List.of("SN-1"), "CFG-1", "核心网割接",
                "P1至P6受控正向闭环", "NETWORK_TOPOLOGY_CHANGE", "DUAL",
                LocalDateTime.of(2026, 9, 3, 10, 0), null, null, null,
                new CreateCutoverTaskCommand.ExpectedCreateContext(projectFact(tenantId), List.of(deviceFact()),
                        customerFact(), readinessFact()));
    }

    private void preparePlanSource(long taskId, CutoverAssessmentCommandResult assessment,
                                   ChecklistCommandResult checklist, String grade) {
        List<CutoverPlanSourcePort.TemplateSectionSnapshot> sections = metadata.sectionViews(configurationId).stream()
                .filter(section -> section.getLevelCodes().contains(grade))
                .map(section -> new CutoverPlanSourcePort.TemplateSectionSnapshot(section.getStableSectionKey(), section.getTitle(),
                        section.getSortOrder(), section.getCutoverTypeCodes(), section.getLevelCodes(), section.getRequired())).toList();
        owners.taskId = taskId;
        owners.projectId = PROJECT_ID;
        owners.facts = new CutoverPlanSourcePort.SourceFacts(new CutoverPlanSourcePort.SourceSnapshot(
                1, taskId, checklist.taskVersion(), assessment.assessmentId(), assessment.assessmentVersion(), grade,
                checklist.checklistId(), checklist.checklistVersion(), PROJECT_ID, 6, PROJECT_SCOPE_VERSION,
                List.of(new CutoverPlanSourcePort.DeviceSnapshot(DEVICE_ID, "SN-1", 9L,
                        null, null)),
                configurationId, "CFG-1", 1, sections, List.of()), List.of());
    }

    @jakarta.annotation.Resource private cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort currentRoles;

    private void approveAll(long taskId, Long expectedTaskVersion, long approvalInstanceId, String grade) {
        List<Map<String, Object>> nodes = jdbc.queryForList("SELECT id,node_no,node_code,current_approver_user_id "
                + "FROM cut_approval_node WHERE tenant_id=? AND approval_instance_id=? ORDER BY node_no",
                tenantId, approvalInstanceId);
        assertEquals("A".equals(grade) ? 4 : "B".equals(grade) ? 3 : 2, nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            Map<String, Object> node = nodes.get(index);
            String nodeCode = String.valueOf(node.get("node_code"));
            long nodeId = ((Number) node.get("id")).longValue();
            var actual = runtimeNodes.selectById(nodeId);
            var role = cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRoleApprovalSnapshot.from(actual);
            boolean byRole = role != null && role.byRole();
            long selectedActor;
            if (byRole) {
                assertNull(actual.getCurrentApproverUserId());
                assertEquals("PENDING", actual.getStatusCode());
                assertEquals("ROLE", role.approvalMode());
                assertEquals("SECOND_LINE".equals(nodeCode) ? "probSupporter" : "probRD", role.roleGroupCode());
                assertNull(actual.getCandidateFactSnapshot());
                assertRoleHomeTaskBoundary(taskId);
                assertRejectedNodeUpdate(new RejectedNodeUpdate(nodeId, "current_approver_user_id=202",
                        3819, "chk_cut_approval_node_approver"));
                assertRejectedNodeUpdate(new RejectedNodeUpdate(nodeId, "candidate_role_code='super_admin'",
                        1644, "CUT_IMMUTABLE_HISTORY: cut_approval_node"));
                var eligibleUsers = currentRoles.inspectCandidates(new cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort.RoleQuery(tenantId, role.roleGroupCode())).candidates().stream()
                        .filter(value -> value.roleGroupId() == role.roleGroupId()).map(cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort.Candidate::adminUserId).toList();
                assertFalse(eligibleUsers.isEmpty());
                for (Long candidate : eligibleUsers) assertRoleTodo(nodeId, candidate, true);
                assertRoleReferenceFiltering(actual);
                try {
                    jdbc.update("UPDATE cut_task SET deleted=1 WHERE tenant_id=? AND id=?", tenantId, taskId);
                    for (Long candidate : eligibleUsers) assertRoleTodo(nodeId, candidate, false);
                } finally {
                    jdbc.update("UPDATE cut_task SET deleted=0 WHERE tenant_id=? AND id=?", tenantId, taskId);
                }
                int currentNodeNo = jdbc.queryForObject("SELECT current_node_no FROM cut_approval_instance WHERE tenant_id=? AND id=?",
                        Integer.class, tenantId, approvalInstanceId);
                try {
                    jdbc.update("UPDATE cut_approval_instance SET current_node_no=1 WHERE tenant_id=? AND id=?", tenantId, approvalInstanceId);
                    for (Long candidate : eligibleUsers) assertRoleTodo(nodeId, candidate, false);
                } finally {
                    jdbc.update("UPDATE cut_approval_instance SET current_node_no=? WHERE tenant_id=? AND id=?",
                            currentNodeNo, tenantId, approvalInstanceId);
                }
                assertRoleTodo(nodeId, 999999L, false);
                var reassignment = new cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalReassignmentPageQuery(tenantId, 0, 100);
                assertTrue(runtimeNodes.selectReassignmentPage(reassignment).stream().noneMatch(value -> value.getId().equals(nodeId)));
                assertTrue(runtimeNodes.selectReassignmentProjectionPage(reassignment).stream().noneMatch(value -> value.getNodeId().equals(nodeId)));
                selectedActor = eligibleUsers.getFirst();
            } else {
                selectedActor = ((Number) node.get("current_approver_user_id")).longValue();
                assertRejectedNodeUpdate(new RejectedNodeUpdate(nodeId, "current_approver_user_id=NULL",
                        3819, "chk_cut_approval_node_approver"));
            }
            actor.use(selectedActor);
            approvalService.approve(new ApproveCutoverApprovalCommand(tenantId,
                taskId,
                approvalInstanceId,
                expectedTaskVersion,
                (long) index,
                yesItems(),
                "SERVICE_MANAGER".equals(nodeCode)
                    ? new AssessmentReviewInput("CONFIRMED", null) : null,
                "节点审批通过",
                "approval-" + (index + 1),
                "corr-approval-" + (index + 1)));
            if (byRole) {
                var decided = runtimeNodes.selectById(nodeId);
                assertEquals(selectedActor, decided.getCurrentApproverUserId());
                assertRejectedNodeUpdate(new RejectedNodeUpdate(nodeId, "current_approver_user_id=NULL",
                        1644, "CUT_IMMUTABLE_HISTORY: cut_approval_node"));
                var frozen = cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRoleApprovalSnapshot.from(decided);
                assertEquals("ROLE", frozen.approvalMode());
                assertNull(decided.getCandidateFactSnapshot());
                assertEquals(role, frozen);
            }
        }
        actor.use(ACTOR_ID);
    }

    private void assertRoleHomeTaskBoundary(long taskId) {
        var original = taskRows.selectById(taskId);
        if (roleQueryInterferenceId == null) {
            roleQueryInterferenceId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
            var interference = new cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO();
            org.springframework.beans.BeanUtils.copyProperties(original, interference);
            interference.setId(roleQueryInterferenceId);
            interference.setTaskNo("ROLE-QUERY-" + roleQueryInterferenceId);
            interference.setCreator("cutover-role-query-fixture");
            interference.setUpdater("cutover-role-query-fixture");
            assertEquals(1, taskRows.insert(interference));
        }
        long otherId = roleQueryInterferenceId;
        var ids = java.util.Set.of(taskId, otherId);
        assertHomeRows(java.util.Set.of(), java.util.Set.of(taskId), ids, java.util.Set.of(taskId));
        assertHomeRows(java.util.Set.of(), java.util.Set.of(), ids, java.util.Set.of());
        assertHomeRows(java.util.Set.of(original.getProjectId()), java.util.Set.of(), ids, ids);
        // 保留同租户隔离干扰任务，验证库的不可变历史不作删除清理。
    }

    private void assertHomeRows(java.util.Set<Long> projects, java.util.Set<Long> approvals,
                                java.util.Set<Long> ids, java.util.Set<Long> expected) {
        var pagination = new cn.iocoder.yudao.framework.common.pojo.PageParam();
        pagination.setPageNo(1); pagination.setPageSize(100);
        var workbench = cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskPageQuery.builder()
                .tenantId(tenantId).visibleProjectIds(projects).approvalTaskIds(approvals).taskIds(ids).pageParam(pagination).build();
        assertEquals(expected, workbenchRows.selectTaskPage(workbench).getList().stream()
                .map(cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO::getId).collect(java.util.stream.Collectors.toSet()));
        var dashboard = new cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.query.CutoverDashboardCandidateQuery(
                tenantId, projects, 0L, 100, approvals);
        assertEquals(expected, dashboardRows.selectBatch(dashboard).stream()
                .map(cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.CutoverDashboardCandidateRow::getTaskId).collect(java.util.stream.Collectors.toSet()));
    }

    private record RejectedNodeUpdate(long nodeId, String assignment, int errorCode, String errorMessage) { }

    private void assertRejectedNodeUpdate(RejectedNodeUpdate command) {
        long nodeId = command.nodeId();
        var before = jdbc.queryForMap("SELECT * FROM cut_approval_node WHERE tenant_id=? AND id=?", tenantId, nodeId);
        var failure = assertThrows(org.springframework.jdbc.UncategorizedSQLException.class,
                () -> jdbc.update("UPDATE cut_approval_node SET " + command.assignment() + " WHERE tenant_id=? AND id=?", tenantId, nodeId));
        assertEquals(command.errorCode(), failure.getSQLException().getErrorCode());
        assertTrue(failure.getSQLException().getMessage().contains(command.errorMessage()));
        assertEquals(before, jdbc.queryForMap("SELECT * FROM cut_approval_node WHERE tenant_id=? AND id=?", tenantId, nodeId));
    }

    private void assertRoleReferenceFiltering(cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO node) {
        long actorId = currentRoles.inspectCandidates(new cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort.RoleQuery(
                tenantId, node.getCandidateRoleCode())).candidates().getFirst().adminUserId();
        var query = cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalTodoPageQuery.builder()
                .tenantId(tenantId).currentUserId(actorId).offset(0).pageSize(100).candidateRoleIds(java.util.Set.of(node.getCandidateRoleId() + 100000)).build();
        assertTrue(runtimeNodes.selectTodoPage(query).stream().noneMatch(value -> value.getId().equals(node.getId())));
        assertTrue(runtimeNodes.selectTodoProjectionPage(query).stream().noneMatch(value -> value.getNodeId().equals(node.getId())));
        assertEquals(0L, runtimeNodes.countTodos(query));
    }

    private void assertRoleTodo(long nodeId, long userId, boolean expected) {
        var query = cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalTodoPageQuery.builder().tenantId(tenantId).currentUserId(userId).offset(0).pageSize(100).candidateRoleIds(currentRoles.inspectActor(new cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort.ActorQuery(tenantId, userId)).stream()
                .map(value -> value.role().roleId()).collect(java.util.stream.Collectors.toSet())).build();
        var direct = runtimeNodes.selectTodoPage(query);
        var projected = runtimeNodes.selectTodoProjectionPage(query);
        assertEquals(expected, direct.stream().anyMatch(value -> value.getId().equals(nodeId)));
        assertEquals(expected, projected.stream().anyMatch(value -> value.getNodeId().equals(nodeId)));
        assertEquals(direct.size(), runtimeNodes.countTodos(query));
        if (!expected) assertEquals(0L, runtimeNodes.countTodos(query));
    }

    private tools.jackson.databind.node.ObjectNode simpleContent() {
        var value = JsonUtils.getObjectMapper().createObjectNode(); value.put("editMode", "ONLINE_TEMPLATE_SIMPLE_D");
        var steps = value.putArray("steps");
        for (String section : List.of("OPERATION", "ROLLBACK")) steps.addObject().put("sectionCode", section).put("stepNo", 1).put("content", "执行" + section);
        return value;
    }

    private tools.jackson.databind.node.ObjectNode standardContent() {
        var content = JsonUtils.getObjectMapper().createObjectNode();
        content.put("editMode", "ONLINE_TEMPLATE_STANDARD");
        var overview = content.putObject("overview");
        overview.put("projectDescription", "P1至P6受控正向闭环");
        overview.putArray("scheduleTable").addObject().put("sequenceNo", 1)
                .put("plannedAt", 1_788_192_000_000L).put("content", "实施割接");
        overview.putNull("preTopologyFile");
        overview.putNull("postTopologyFile");
        var device = overview.putArray("deviceSummary").addObject();
        device.put("deviceId", DEVICE_ID);
        device.put("serialNumber", "SN-1");
        device.put("projectAssignmentVersion", 9L);
        device.putNull("deviceTypeCode");
        device.putNull("deviceTypeSourceVersion");
        overview.putNull("networkConfigurationFile");
        var steps = content.putArray("steps");
        CutoverPlanRules.STANDARD_SECTIONS.forEach(section -> steps.addObject().put("sectionCode", section)
                .put("stepNo", 1).put("content", section + "执行内容"));
        content.putArray("riskMitigations");
        var supports = content.putArray("supportArrangements");
        CutoverPlanRules.SUPPORT_ROLES.forEach(role -> supports.addObject().putNull("arrangementId")
                .put("roleCode", role).put("personName", role + "负责人")
                .put("dutyDescription", role + "保障").put("phone", "13800000000")
                .put("arrivalTime", 1_788_192_000_000L));
        return content;
    }

    private record SupportDraftFixture(long taskId, long taskVersion, CutoverPlanCommandResult plan) { }

    private CutoverPlanCommandResult exerciseSupportDraft(SupportDraftFixture fixture) {
        var body = standardContent();
        var rows = (tools.jackson.databind.node.ArrayNode) body.get("supportArrangements");
        rows.addObject().putNull("arrangementId").put("roleCode", "DP_FIRST_LINE")
                .put("personName", "待补完整的第二位工程师").put("dutyDescription", "").put("phone", "").put("arrivalTime", 0);
        var partial = planService.saveDraft(new SaveCutoverPlanDraftCommand(tenantId, ACTOR_ID, fixture.taskId(),
                fixture.taskVersion(), fixture.plan().planVersion(), body, "support-partial", "support-partial"));
        var stored = jdbc.queryForList("SELECT id, role_code, person_name, duty_description, phone, arrive_time FROM cut_cutover_support_arrangement "
                + "WHERE tenant_id=? AND plan_revision_id=? ORDER BY sort_order,id", tenantId, partial.planRevisionId());
        assertEquals(5, stored.size());
        assertEquals("待补完整的第二位工程师", stored.getLast().get("person_name"));
        assertEquals("", stored.getLast().get("duty_description"));
        assertEquals("", stored.getLast().get("phone"));
        assertNull(stored.getLast().get("arrive_time"));
        assertThrows(cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationException.class,
                () -> planService.submit(new SubmitCutoverPlanCommand(tenantId, ACTOR_ID, fixture.taskId(), fixture.taskVersion(),
                        partial.planVersion(), "support-incomplete-submit", "support-incomplete-submit")));
        var incomplete = assertThrows(org.springframework.jdbc.UncategorizedSQLException.class, () -> jdbc.update(
                "UPDATE cut_plan_revision SET status_code='SUBMITTED' WHERE tenant_id=? AND id=?", tenantId, partial.planRevisionId()));
        assertEquals(1644, incomplete.getSQLException().getErrorCode());
        assertTrue(incomplete.getSQLException().getMessage().contains("CUT_SUPPORT_INCOMPLETE"));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status_code FROM cut_plan_revision WHERE tenant_id=? AND id=?",
                String.class, tenantId, partial.planRevisionId()));
        for (int index = 0; index < stored.size(); index++) {
            ((tools.jackson.databind.node.ObjectNode) rows.get(index)).put("arrangementId", stored.get(index).get("id").toString());
        }
        var extra = (tools.jackson.databind.node.ObjectNode) rows.get(4);
        extra.put("arrangementId", Long.toString(Long.MAX_VALUE));
        assertThrows(cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationException.class,
                () -> planService.saveDraft(new SaveCutoverPlanDraftCommand(tenantId, ACTOR_ID, fixture.taskId(),
                        fixture.taskVersion(), partial.planVersion(), body, "support-foreign-id", "support-foreign-id")));
        assertEquals(stored, jdbc.queryForList("SELECT id, role_code, person_name, duty_description, phone, arrive_time FROM cut_cutover_support_arrangement "
                + "WHERE tenant_id=? AND plan_revision_id=? ORDER BY sort_order,id", tenantId, partial.planRevisionId()));
        extra.put("arrangementId", stored.getLast().get("id").toString());
        extra.put("roleCode", "DP_RND").put("dutyDescription", "第二位研发保障").put("phone", "00000000000")
                .put("arrivalTime", 1_788_192_000_000L);
        var revised = planService.saveDraft(new SaveCutoverPlanDraftCommand(tenantId, ACTOR_ID, fixture.taskId(),
                fixture.taskVersion(), partial.planVersion(), body, "support-role-change", "support-role-change"));
        assertEquals(2, number("SELECT COUNT(*) FROM cut_cutover_support_arrangement WHERE tenant_id=? AND plan_revision_id=? AND role_code='DP_RND'",
                tenantId, revised.planRevisionId()));
        long removedId = ((Number) stored.getLast().get("id")).longValue();
        assertEquals("DP_RND", jdbc.queryForObject("SELECT role_code FROM cut_cutover_support_arrangement WHERE tenant_id=? AND id=?",
                String.class, tenantId, removedId));
        rows.remove(4);
        rows.add(extra.deepCopy().putNull("arrangementId").put("roleCode", "DP_SECOND_LINE").put("personName", "新增二线工程师"));
        var saved = planService.saveDraft(new SaveCutoverPlanDraftCommand(tenantId, ACTOR_ID, fixture.taskId(),
                fixture.taskVersion(), revised.planVersion(), body, "support-delete-add", "support-delete-add"));
        assertEquals(0, number("SELECT COUNT(*) FROM cut_cutover_support_arrangement WHERE tenant_id=? AND id=?", tenantId, removedId));
        assertEquals(2, number("SELECT COUNT(*) FROM cut_cutover_support_arrangement WHERE tenant_id=? AND plan_revision_id=? AND role_code='DP_SECOND_LINE'",
                tenantId, saved.planRevisionId()));
        return saved;
    }

    private static List<ReviewItemInput> yesItems() {
        return CutoverApprovalRules.REVIEW_ITEM_CODES.stream()
                .map(code -> new ReviewItemInput(code, "YES", null)).toList();
    }

    private static List<SaveCutoverClosureCommand.AttachmentInput> closureAttachments() {
        return List.of(closureFile(AttachmentPurpose.POST_COLLECTION_CHECKLIST, 501L, "closure-checklist"),
                closureFile(AttachmentPurpose.IMPLEMENTATION_COMMITMENT, 502L, "closure-commitment"));
    }

    private static SaveCutoverClosureCommand.AttachmentInput closureFile(AttachmentPurpose purpose,
                                                                          long artifactId, String referenceKey) {
        return new SaveCutoverClosureCommand.AttachmentInput(purpose, artifactId, 1, referenceKey,
                new CutoverClosureFilePort.FileFactVersion(1, 2, 3), 4L, "a".repeat(64));
    }

    private void insertConfiguration() {
        CutoverConfigurationRevisionDO configuration = new CutoverConfigurationRevisionDO();
        configuration.setId(configurationId);
        configuration.setTenantId(tenantId);
        configuration.setConfigurationCode("CFG-1");
        configuration.setConfigurationName("CUT全链受控配置");
        configuration.setRevisionNo(1);
        configuration.setStatusCode("DRAFT");
        configuration.setEffectiveFrom(LocalDateTime.of(2026, 8, 1, 0, 0));

        configuration.setVersion(0L);
        configuration.setCreator(String.valueOf(ACTOR_ID));
        configuration.setUpdater(String.valueOf(ACTOR_ID));
        assertEquals(1, configurationMapper.insert(configuration));
        var request = new cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationSaveReqVO();
        request.setDictionarySnapshot(Map.of(
                "pms_device_type", List.of(Map.of("value", "FW", "label", "防火墙")),
                "pms_cutover_network_mode", List.of(Map.of("value", "DUAL", "label", "双机")),
                "pms_cutover_grade", List.of("A", "B", "C", "D").stream()
                        .map(value -> Map.of("value", value, "label", value)).toList(),
                "pms_cutover_task_type", List.of(Map.of("value", "NETWORK_TOPOLOGY_CHANGE", "label", "网络拓扑变更"))));
        var deviceDimension = new cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationSaveReqVO.DimensionVO();
        deviceDimension.setCode("DEVICE_TYPE"); deviceDimension.setName("设备类型"); deviceDimension.setDataType("STRING");
        deviceDimension.setValueSource("DICT:pms_device_type"); deviceDimension.setOwner("SYSTEM"); deviceDimension.setContextPath("task.deviceType");
        var cutoverDimension = new cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationSaveReqVO.DimensionVO();
        cutoverDimension.setCode("CUTOVER_TYPE"); cutoverDimension.setName("割接类型"); cutoverDimension.setDataType("STRING");
        cutoverDimension.setValueSource("DICT:pms_cutover_task_type"); cutoverDimension.setOwner("CUT"); cutoverDimension.setContextPath("task.cutoverType");
        var networkDimension = new cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationSaveReqVO.DimensionVO();
        networkDimension.setCode("NETWORK_MODE"); networkDimension.setName("组网方式"); networkDimension.setDataType("STRING");
        networkDimension.setValueSource("DICT:pms_cutover_network_mode"); networkDimension.setOwner("CUT"); networkDimension.setContextPath("task.networkMode");
        var gradeDimension = new cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationSaveReqVO.DimensionVO();
        gradeDimension.setCode("CUTOVER_LEVEL"); gradeDimension.setName("割接等级"); gradeDimension.setDataType("STRING");
        gradeDimension.setValueSource("DICT:pms_cutover_grade"); gradeDimension.setOwner("CUT"); gradeDimension.setContextPath("task.manualGrade");
        request.setDimensions(List.of(cutoverDimension, deviceDimension, networkDimension, gradeDimension));
        request.setPlanTemplateSections(CutoverPlanRules.STANDARD_SECTIONS.stream().map(code -> {
            var section = new cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationSaveReqVO.PlanTemplateSectionVO();
            section.setStableSectionKey(code); section.setTitle(code); section.setRequired(true);
            section.setSortOrder(CutoverPlanRules.STANDARD_SECTIONS.indexOf(code) + 1);
            section.setCutoverTypeCodes(List.of("NETWORK_TOPOLOGY_CHANGE"));
            section.setLevelCodes(List.of("OPERATION", "ROLLBACK").contains(code) ? List.of("A", "B", "C", "D") : List.of("A", "B", "C"));
            return section;
        }).toList());
        metadata.replaceDraft(new cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationMetadataStore.DraftReplacement(
                configuration, Set.of(), request));


        CutoverChecklistItemDefinitionRevisionDO definition = new CutoverChecklistItemDefinitionRevisionDO();
        definition.setId(configurationId + 1);
        definition.setTenantId(tenantId);
        definition.setConfigurationRevisionId(configurationId);
        definition.setStableItemKey("PRECHECK-READY");
        definition.setItemDefinitionVersion(1);
        definition.setItemTypeCode("BUSINESS_SURVEY");
        definition.setItemName("割接前置确认");
        definition.setInterfaceFormatCode("INPUT");
        definition.setFeedbackFormatCode("TEXT");
        definition.setRequiredFlag(true);
        definition.setWorkModeCode("DIRECT");
        definition.setStatusCode("ENABLED");
        definition.setSortOrder(10);
        definition.setVersion(0);
        definition.setCreator(String.valueOf(ACTOR_ID));
        definition.setUpdater(String.valueOf(ACTOR_ID));
        assertEquals(1, definitionMapper.insert(definition));
        fields.insert(cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldSchemaCodec.Write.builder()
                .tenantId(tenantId).itemDefinitionRevisionId(definition.getId()).itemTypeCode(definition.getItemTypeCode())
                .businessCategoryCode(definition.getBusinessCategoryCode()).schema(Map.of("type", "string")).build());


        CutoverChecklistBindingRuleRevisionDO rule = new CutoverChecklistBindingRuleRevisionDO();
        rule.setId(configurationId + 101);
        rule.setTenantId(tenantId);
        rule.setConfigurationRevisionId(configurationId);
        rule.setStableRuleKey("RULE-PRECHECK-READY");
        rule.setItemDefinitionId(definition.getId());
        rule.setItemDefinitionVersion(1);
        rule.setPriority(100);
        rule.setRequiredResult(true);
        rule.setStatusCode("ENABLED");
        rule.setVersion(0);
        rule.setCreator(String.valueOf(ACTOR_ID));
        rule.setUpdater(String.valueOf(ACTOR_ID));
        assertEquals(1, ruleMapper.insert(rule));
        metadata.insertConditions(new cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationMetadataStore.ConditionWrite(
                configurationId, rule.getId(), Map.of("CUTOVER_TYPE", List.of("NETWORK_TOPOLOGY_CHANGE"), "DEVICE_TYPE", List.of("FW"))));
        configuration.setStatusCode("PUBLISHED"); configuration.setPublishedBy(ACTOR_ID);
        configuration.setPublishedAt(LocalDateTime.of(2026, 8, 1, 0, 0)); configuration.setValidationValidFlag(true);
        assertEquals(1, configurationMapper.updateById(configuration));

    }

    private int number(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String text(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private static CutoverProjectContextPort.ProjectContextFact projectFact(long tenantId) {
        return CutoverProjectContextPort.ProjectContextFact.builder()
                .tenantId(tenantId)
                .projectId(PROJECT_ID)
                .projectVersion(6)
                .projectCode("PRJ-1")
                .projectName("核心网割接项目")
                .customerId(null)
                .customerCode(null)
                .customerName(null)
                .departmentId(88L)
                .departmentCode("OFF-1")
                .departmentName("交付部")
                .projectScopeVersion(PROJECT_SCOPE_VERSION)
                .build();
    }

    private static CutoverDeviceScopePort.DeviceFact deviceFact() {
        return new CutoverDeviceScopePort.DeviceFact(DEVICE_ID, "SN-1", PROJECT_ID, 9L);
    }

    private static CutoverCustomerLevelPort.CustomerLevelFact customerFact() {
        return new CutoverCustomerLevelPort.CustomerLevelFact("UNAVAILABLE", null, null, null,
                null, null, 0L, null, null);
    }

    private static CutoverReadinessPort.ReadinessFact readinessFact() {
        return new CutoverReadinessPort.ReadinessFact(700L, 1L, "READY", PROJECT_ID,
                List.of(DEVICE_ID), "ready-watermark", List.of());
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    @SpringBootConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan({"cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage", "cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.collection",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            JacksonAutoConfiguration.class, YudaoJacksonAutoConfiguration.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationMetadataStore.class,
            cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldDefinitionStore.class,
            cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverExternalMappingStore.class,
            cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskSnapshotStore.class,
            cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistMetadataStore.class,
            cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverCustomResultStore.class,
            cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverExternalBulletinStore.class,
            cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistResultStore.class,
            cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanStorage.class,
            cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalStorage.class,
            cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureStorage.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class,
            cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandExecutor.class,
            cn.iocoder.yudao.module.pms.cutover.api.result.CutoverResultApiImpl.class})
    static class TestApplication extends CutoverFlowTestConfiguration {

        @Bean com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor optimisticLocker(
                com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor interceptor) {
            var optimisticLocker = new com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor();
            interceptor.addInnerInterceptor(optimisticLocker);
            return optimisticLocker;
        }

        @Bean CutoverProjectContextPort projectContextPort() {
            return new CutoverProjectContextPort() {
                @Override public ProjectContextFact inspect(CutoverProjectContextPort.Inspection query) {
                    Long tenantId = query.tenantId();
                    return projectFact(tenantId);
                }
                @Override public ProjectContextFact lockAndRevalidate(ProjectContextFact expected) {
                    return expected;
                }
            };
        }

        @Bean CutoverDeviceScopePort deviceScopePort() {
            return new CutoverDeviceScopePort() {
                @Override public List<DeviceFact> resolveBySerials(List<String> serialNumbers) {
                    return List.of(deviceFact());
                }
                @Override public List<DeviceFact> lockAndRevalidate(Long projectId,
                                                                    List<DeviceFact> expectedDevices) {
                    return List.copyOf(expectedDevices);
                }
            };
        }

        @Bean CutoverDeviceProductTypePort productTypePort() {
            return (actorId, deviceIds) -> List.of(new CutoverDeviceProductTypePort.ProductTypeFact(
                    DEVICE_ID, "ROUTER", true, "type-v1", "RESOLVED", "FRESH",
                    LocalDateTime.of(2026, 9, 1, 0, 0), false));
        }

        @Bean CutoverCustomerLevelPort customerLevelPort() {
            return new CutoverCustomerLevelPort() {
                @Override public CustomerLevelFact inspect(Long customerId) { return customerFact(); }
                @Override public CustomerLevelFact lockAndRevalidate(CustomerLevelFact expected) { return expected; }
            };
        }

        @Bean CutoverReadinessPort readinessPort() {
            return new CutoverReadinessPort() {
                @Override public ReadinessFact inspect(Long projectId, List<Long> deviceIds) {
                    return readinessFact();
                }
                @Override public ReadinessFact lockAndRevalidate(ReadinessFact expected) { return expected; }
                @Override public ReadinessFact readSnapshot(SnapshotReference reference) {
                    ReadinessFact frozen = readinessFact();
                    Long tenant = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
                    if (reference == null || !java.util.Objects.equals(reference.tenantId(), tenant)
                            || !java.util.Objects.equals(reference.projectId(), frozen.projectId())
                            || !java.util.Objects.equals(reference.snapshotId(), frozen.snapshotId())
                            || !java.util.Objects.equals(reference.snapshotVersion(), frozen.snapshotVersion())
                            || !java.util.Objects.equals(reference.deviceIds(), frozen.deviceIds())) {
                        throw new IllegalArgumentException("Readiness reference does not identify this fixture snapshot");
                    }
                    return frozen;
                }
            };
        }

        @Bean CutoverTaskApplicationService taskService(CutoverTaskMapper tasks,
                CutoverTaskDeviceScopeMapper devices, CutoverTaskStageHistoryMapper history,
                CutoverAssessmentMapper assessments, CutoverConfigurationRevisionMapper configurations,
                CutoverApprovalPositiveLoopMySqlTest.ControlledOwners scopes,
                CutoverProjectContextPort projects, CutoverDeviceScopePort deviceScopes,
                CutoverDeviceProductTypePort productTypes, CutoverCustomerLevelPort customerLevels,
                CutoverReadinessPort readiness, PlatformCommandExecutionApi platform, java.time.Clock clock) {
            return new CutoverTaskApplicationService(tasks, devices, history, assessments, configurations,
                    scopes, projects, deviceScopes, productTypes, customerLevels, readiness, platform, clock, org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy.class));
        }

        @Bean CutoverChecklistConfigurationQueryService checklistConfiguration(
                CutoverConfigurationRevisionMapper revisions,
                CutoverChecklistItemDefinitionRevisionMapper definitions,
                CutoverChecklistBindingRuleRevisionMapper rules,
                cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationMetadataStore metadata,
                cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverFieldDefinitionStore fields) {
            return new CutoverChecklistConfigurationQueryService(revisions, definitions, rules, metadata, fields);
        }

        @Bean CutoverNavigationDecisionQueryService checklistNavigation(
                CutoverTaskMapper tasks, CutoverConfigurationRevisionMapper revisions) {
            return new CutoverNavigationDecisionQueryService(tasks, revisions,
                    new CutoverNavigationDecisionPolicy());
        }

        @Bean CutoverChecklistFilePort checklistFilePort() {
            return query -> {
                var handle = query.handle();
                return CutoverChecklistFilePort.FileFact.builder()
                        .artifactId(handle.artifactId())
                        .versionNo(handle.versionNo())
                        .referenceKey(handle.referenceKey())
                        .fileFactVersion(handle.fileFactVersion())
                        .scopeVersion(handle.scopeVersion())
                        .sha256("a".repeat(64))
                        .build();
            };
        }

        @Bean CutoverCollectionPort checklistCollectionPort() {
            return new CutoverCollectionPort() {
                @Override public RequestReceipt request(Request request) { throw new UnsupportedOperationException(); }
                @Override public CollectionFact inspect(Inspection inspection) { throw new UnsupportedOperationException(); }
            };
        }

        @Bean CutoverChecklistApplicationService checklistService(CutoverTaskMapper tasks,
                CutoverTaskDeviceScopeMapper devices, CutoverAssessmentMapper assessments,
                CutoverTaskStageHistoryMapper history, CutoverChecklistMapper checklists,
                CutoverChecklistItemMapper items, CutoverChecklistItemResultMapper results,
                CutoverChecklistConfigurationQueryService configuration,
                CutoverApprovalPositiveLoopMySqlTest.ControlledOwners scopes,
                CutoverCollectionPort collection, CutoverChecklistFilePort files,
                PlatformCommandExecutionApi platform, CutoverNavigationDecisionQueryService navigation,
                java.time.Clock clock) {
            return new CutoverChecklistApplicationService(tasks, devices, assessments, history, checklists, items,
                    results, configuration, new CutoverChecklistMatcher(), scopes, collection, files, platform,
                    navigation, clock);
        }

        @Bean CutoverClosureFilePort closureFilePort() {
            return new CutoverClosureFilePort() {
                @Override public FileFact inspect(FileExpectation expectation) { return closureFact(expectation); }
                @Override public FileFact lockAndRevalidate(FileExpectation expectation) {
                    return closureFact(expectation);
                }
                private FileFact closureFact(FileExpectation value) {
                    return FileFact.builder()
                            .artifactId(value.artifactId())
                            .versionNo(value.versionNo())
                            .referenceKey(value.referenceKey())
                            .fileFactVersion(value.fileFactVersion())
                            .scopeVersion(value.scopeVersion())
                            .sha256(value.sha256())
                            .build();
                }
            };
        }

        @Bean CutoverClosureCollectionPort closureCollectionPort() {
            return new CutoverClosureCollectionPort() {
                @Override public DispatchFact request(CollectionRequest request) {
                    throw new UnsupportedOperationException();
                }
                @Override public DispatchLookup inspectByIntent(CollectionIntentIdentity identity) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Bean CutoverClosureApplicationService closureService(CutoverTaskMapper tasks,
                CutoverApprovalInstanceMapper approvals, CutoverPlanRevisionMapper plans,
                CutoverClosureMapper closures, CutoverClosureAttachmentMapper attachments,
                CutoverCollectionEvidenceMapper evidence, CutoverTaskDeviceScopeMapper devices,
                CutoverTaskStageHistoryMapper history,
                CutoverApprovalPositiveLoopMySqlTest.ControlledOwners scopes,
                CutoverClosureFilePort files, CutoverClosureCollectionPort collections,
                PlatformCommandExecutionApi platform, java.time.Clock clock) {
            return new CutoverClosureApplicationService(tasks, approvals, plans, closures, attachments, evidence,
                    devices, history, scopes, files, platform, clock, org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.service.task.CutoverExecutionReadinessPolicy.class));
        }
    }
}
