package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
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
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
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
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.CreateCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.SaveCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.SubmitCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceProductTypePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.result.CutoverAssessmentCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.result.CutoverTaskCommandResult;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CUT-01~06 已实现内核在测试作用域受控Owner事实下的单任务正向全链。 */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CutoverFullFlowPositiveLoopMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CutoverFullFlowPositiveLoopMySqlTest {

    private static final long ACTOR_ID = 8L;
    private static final long PROJECT_ID = 100L;
    private static final long DEVICE_ID = 301L;
    private static final long PROJECT_SCOPE_VERSION = 30L;

    @Resource JdbcTemplate jdbc;
    @Resource CutoverConfigurationRevisionMapper configurationMapper;
    @Resource CutoverChecklistItemDefinitionRevisionMapper definitionMapper;
    @Resource CutoverChecklistBindingRuleRevisionMapper ruleMapper;
    @Resource CutoverTaskApplicationService taskService;
    @Resource CutoverChecklistApplicationService checklistService;
    @Resource CutoverPlanApplicationService planService;
    @Resource CutoverApprovalApplicationService approvalService;
    @Resource CutoverClosureApplicationService closureService;
    @Resource CutoverApprovalPositiveLoopMySqlTest.ControlledOwners owners;
    @Resource CutoverApprovalPositiveLoopMySqlTest.CurrentActor actor;

    private long tenantId;
    private long configurationId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        Map<String, String> env = System.getenv();
        String database = env.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = env.getOrDefault("NPDMS_MYSQL_PORT", "13306");
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
        insertConfiguration();
    }

    @AfterEach
    void tearDown() {
        for (String table : List.of("cut_cutover_collection_evidence", "cut_cutover_closure_attachment",
                "cut_cutover_closure", "cut_approval_review_item", "cut_approval_notification",
                "cut_approval_reassignment", "cut_approval_node", "cut_approval_instance", "cut_step",
                "cut_cutover_support_arrangement", "cut_plan_revision", "cut_cutover_checklist_item_result",
                "cut_cutover_checklist_item", "cut_cutover_checklist", "cut_assessment",
                "cut_task_stage_history", "cut_task_device_scope", "plt_outbox_event", "plt_operation_audit",
                "plt_idempotency_record", "cut_task", "cut_cutover_checklist_binding_rule_revision",
                "cut_cutover_checklist_item_definition_revision", "cut_cutover_configuration_revision")) {
            jdbc.update("DELETE FROM " + table + " WHERE tenant_id=?", tenantId);
        }
        TenantContextHolder.clear();
    }

    @Test
    void completesOneTaskFromP1ThroughP6ArchiveWithControlledOwnerFacts() {
        CutoverTaskCommandResult created = taskService.create(createCommand());
        CutoverAssessmentCommandResult assessment = taskService.saveAssessment(new SaveCutoverAssessmentCommand(
                tenantId, ACTOR_ID, created.taskId(), created.version(), 0,
                new CutoverAssessmentAnswers("HIGH", "MEDIUM", "LOW", true), "A", "corr-assessment-save"));
        CutoverTaskCommandResult p3 = taskService.submitAssessment(new SubmitCutoverAssessmentCommand(
                tenantId, ACTOR_ID, created.taskId(), assessment.taskVersion(), assessment.assessmentRowVersion(),
                "assessment-submit", "corr-assessment-submit"));

        ChecklistCommandResult generated = checklistService.generate(new GenerateChecklistCommand(
                tenantId, ACTOR_ID, created.taskId(), p3.version(), assessment.assessmentVersion(),
                PROJECT_SCOPE_VERSION, Map.of(), "checklist-generate", "corr-checklist-generate"));
        ChecklistCommandResult savedChecklist = checklistService.save(new SaveChecklistCommand(
                tenantId, ACTOR_ID, created.taskId(), p3.version(), generated.checklistId(),
                generated.checklistFactVersion(), PROJECT_SCOPE_VERSION,
                List.of(new SaveChecklistCommand.DirectAnswer("PRECHECK-READY", "{\"value\":\"YES\"}"))));
        ChecklistCommandResult p4 = checklistService.submit(new SubmitChecklistCommand(
                tenantId, ACTOR_ID, created.taskId(), p3.version(), assessment.assessmentVersion(),
                generated.checklistId(), savedChecklist.checklistFactVersion(), PROJECT_SCOPE_VERSION,
                "checklist-submit", "corr-checklist-submit"));

        preparePlanSource(created.taskId(), assessment, p4);
        CutoverPlanCommandResult plan = planService.createDraft(new CreateCutoverPlanDraftCommand(
                tenantId, ACTOR_ID, created.taskId(), p4.taskVersion(), "ONLINE_TEMPLATE_STANDARD", null, null,
                "plan-create", "corr-plan-create"));
        CutoverPlanCommandResult savedPlan = planService.saveDraft(new SaveCutoverPlanDraftCommand(
                tenantId, ACTOR_ID, created.taskId(), p4.taskVersion(), plan.planVersion(), standardContent(),
                "plan-save", "corr-plan-save"));
        SubmitCutoverPlanResult p5 = planService.submit(new SubmitCutoverPlanCommand(
                tenantId, ACTOR_ID, created.taskId(), p4.taskVersion(), savedPlan.planVersion(),
                "plan-submit", "corr-plan-submit"));

        approveAll(created.taskId(), p5.taskVersion(), p5.approvalInstanceId());
        int p6TaskVersion = number("SELECT version FROM cut_task WHERE tenant_id=? AND id=?", tenantId, created.taskId());
        CutoverClosureCommandResult draft = closureService.save(new SaveCutoverClosureCommand(
                tenantId, ACTOR_ID, created.taskId(), p6TaskVersion, null,
                new SaveCutoverClosureCommand.ClosureContent(true, null, true, null, true, null,
                        false, null, null, null, null, closureAttachments()),
                "closure-save", "corr-closure-save"));
        SubmitCutoverClosureCommand submit = new SubmitCutoverClosureCommand(
                tenantId, ACTOR_ID, created.taskId(), p6TaskVersion, draft.closureId(), draft.closureVersion(),
                "SUCCESS", "closure-submit", "corr-closure-submit");
        CutoverClosureCommandResult archived = closureService.submit(submit);
        CutoverClosureCommandResult replayed = closureService.submit(submit);

        assertEquals("SUBMITTED", archived.closureStatus());
        assertTrue(replayed.replayed());
        assertEquals("P6", text("SELECT current_stage FROM cut_task WHERE tenant_id=? AND id=?",
                tenantId, created.taskId()));
        assertEquals("ARCHIVED", text("SELECT task_status FROM cut_task WHERE tenant_id=? AND id=?",
                tenantId, created.taskId()));
        assertEquals("SUBMITTED", text("SELECT status_code FROM cut_cutover_closure WHERE tenant_id=? AND id=?",
                tenantId, draft.closureId()));
        assertEquals(0, number("SELECT COUNT(*) FROM cut_task_device_scope WHERE tenant_id=? "
                + "AND cutover_task_id=? AND active_marker=1", tenantId, created.taskId()));
        for (String trigger : List.of("P1_ACCEPTED", "P2_ASSESSMENT_SUBMITTED", "P3_CHECKLIST_SUBMITTED",
                "P4_PLAN_SUBMITTED", "P5_APPROVAL_APPROVED", "P6_CLOSURE_SUBMITTED")) {
            assertEquals(1, number("SELECT COUNT(*) FROM cut_task_stage_history WHERE tenant_id=? "
                    + "AND cutover_task_id=? AND trigger_type=?", tenantId, created.taskId(), trigger));
        }
        assertEquals(1, number("SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=? "
                + "AND event_type='CutoverApproved'", tenantId));
        assertEquals(1, number("SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=? "
                + "AND event_type='CutoverCompleted'", tenantId));
        assertEquals(1, number("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? "
                + "AND correlation_id='corr-closure-submit'", tenantId));
    }

    private CreateCutoverTaskCommand createCommand() {
        return new CreateCutoverTaskCommand(tenantId, ACTOR_ID, "task-create", "corr-task-create",
                "SELF_CREATED", PROJECT_ID, List.of("SN-1"), "CFG-1", "核心网割接",
                "P1至P6受控正向闭环", "NETWORK_CUTOVER", "DUAL",
                LocalDateTime.of(2026, 9, 3, 10, 0), null, null, null,
                new CreateCutoverTaskCommand.ExpectedCreateContext(projectFact(tenantId), List.of(deviceFact()),
                        customerFact(), readinessFact()));
    }

    private void preparePlanSource(long taskId, CutoverAssessmentCommandResult assessment,
                                   ChecklistCommandResult checklist) {
        List<CutoverPlanSourcePort.TemplateSectionSnapshot> sections = CutoverPlanRules.STANDARD_SECTIONS.stream()
                .map(code -> new CutoverPlanSourcePort.TemplateSectionSnapshot(code, code,
                        CutoverPlanRules.STANDARD_SECTIONS.indexOf(code) + 1,
                        List.of("NETWORK_CUTOVER"), List.of("A"), true))
                .toList();
        owners.taskId = taskId;
        owners.projectId = PROJECT_ID;
        owners.facts = new CutoverPlanSourcePort.SourceFacts(new CutoverPlanSourcePort.SourceSnapshot(
                1, taskId, checklist.taskVersion(), assessment.assessmentId(), assessment.assessmentVersion(), "A",
                checklist.checklistId(), checklist.checklistVersion(), PROJECT_ID, 6, PROJECT_SCOPE_VERSION,
                List.of(new CutoverPlanSourcePort.DeviceSnapshot(DEVICE_ID, "SN-1", 9L,
                        "ROUTER", "type-v1")),
                configurationId, "CFG-1", 1, sections, List.of()), List.of());
    }

    private void approveAll(long taskId, int expectedTaskVersion, long approvalInstanceId) {
        List<Map<String, Object>> nodes = jdbc.queryForList("SELECT node_no,node_code,current_approver_user_id "
                + "FROM cut_approval_node WHERE tenant_id=? AND approval_instance_id=? ORDER BY node_no",
                tenantId, approvalInstanceId);
        assertEquals(4, nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            Map<String, Object> node = nodes.get(index);
            actor.use(((Number) node.get("current_approver_user_id")).longValue());
            String nodeCode = String.valueOf(node.get("node_code"));
            approvalService.approve(new ApproveCutoverApprovalCommand(tenantId, taskId, expectedTaskVersion,
                    index, yesItems(), "SERVICE_MANAGER".equals(nodeCode)
                    ? new AssessmentReviewInput("CONFIRMED", null) : null,
                    "节点审批通过", "approval-" + (index + 1), "corr-approval-" + (index + 1)));
        }
        actor.use(ACTOR_ID);
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
        device.put("deviceTypeCode", "ROUTER");
        device.put("deviceTypeSourceVersion", "type-v1");
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
        configuration.setStatusCode("PUBLISHED");
        configuration.setEffectiveFrom(LocalDateTime.of(2026, 8, 1, 0, 0));
        configuration.setDictionarySnapshot("{}");
        configuration.setDimensionDefinitionSnapshot("[]");
        configuration.setPlanTemplateSectionSnapshot("[]");
        configuration.setValidationResultSnapshot("[]");
        configuration.setPublishedBy(ACTOR_ID);
        configuration.setPublishedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        configuration.setVersion(0);
        configuration.setCreator(String.valueOf(ACTOR_ID));
        configuration.setUpdater(String.valueOf(ACTOR_ID));
        assertEquals(1, configurationMapper.insert(configuration));

        CutoverChecklistItemDefinitionRevisionDO definition = new CutoverChecklistItemDefinitionRevisionDO();
        definition.setId(configurationId + 1);
        definition.setTenantId(tenantId);
        definition.setConfigurationRevisionId(configurationId);
        definition.setStableItemKey("PRECHECK-READY");
        definition.setItemDefinitionVersion(1);
        definition.setItemTypeCode("BUSINESS_SURVEY");
        definition.setItemName("割接前置确认");
        definition.setInterfaceFormatCode("INPUT");
        definition.setInterfaceSchema("{\"type\":\"string\"}");
        definition.setFeedbackFormatCode("TEXT");
        definition.setRequiredFlag(true);
        definition.setWorkModeCode("DIRECT");
        definition.setStatusCode("ENABLED");
        definition.setSortOrder(10);
        definition.setVersion(0);
        definition.setCreator(String.valueOf(ACTOR_ID));
        definition.setUpdater(String.valueOf(ACTOR_ID));
        assertEquals(1, definitionMapper.insert(definition));

        CutoverChecklistBindingRuleRevisionDO rule = new CutoverChecklistBindingRuleRevisionDO();
        rule.setId(configurationId + 101);
        rule.setTenantId(tenantId);
        rule.setConfigurationRevisionId(configurationId);
        rule.setStableRuleKey("RULE-PRECHECK-READY");
        rule.setItemDefinitionId(definition.getId());
        rule.setItemDefinitionVersion(1);
        rule.setDimensionConditionSnapshot("{\"CUTOVER_TYPE\":[\"NETWORK_CUTOVER\"],"
                + "\"DEVICE_TYPE\":[\"ROUTER\"]}");
        rule.setPriority(100);
        rule.setRequiredResult(true);
        rule.setStatusCode("ENABLED");
        rule.setVersion(0);
        rule.setCreator(String.valueOf(ACTOR_ID));
        rule.setUpdater(String.valueOf(ACTOR_ID));
        assertEquals(1, ruleMapper.insert(rule));
    }

    private int number(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String text(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private static CutoverProjectContextPort.ProjectContextFact projectFact(long tenantId) {
        return new CutoverProjectContextPort.ProjectContextFact(tenantId, PROJECT_ID, 6,
                "PRJ-1", "核心网割接项目", 99L, "CUS-1", "客户",
                88L, "OFF-1", "交付部", PROJECT_SCOPE_VERSION);
    }

    private static CutoverDeviceScopePort.DeviceFact deviceFact() {
        return new CutoverDeviceScopePort.DeviceFact(DEVICE_ID, "SN-1", PROJECT_ID, 9L);
    }

    private static CutoverCustomerLevelPort.CustomerLevelFact customerFact() {
        return new CutoverCustomerLevelPort.CustomerLevelFact("AVAILABLE", 99L, "CUS-1", "客户",
                500L, "GOLD", 2L, LocalDateTime.of(2026, 8, 1, 0, 0), null);
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
    @MapperScan({"cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            JacksonAutoConfiguration.class, YudaoJacksonAutoConfiguration.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
    static class TestApplication extends CutoverApprovalPositiveLoopMySqlTest.TestApplication {

        @Bean CutoverProjectContextPort projectContextPort() {
            return new CutoverProjectContextPort() {
                @Override public ProjectContextFact inspect(Long tenantId, Long projectId,
                                                             long expectedProjectScopeVersion) {
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
                    scopes, projects, deviceScopes, productTypes, customerLevels, readiness, platform, clock);
        }

        @Bean CutoverChecklistConfigurationQueryService checklistConfiguration(
                CutoverConfigurationRevisionMapper revisions,
                CutoverChecklistItemDefinitionRevisionMapper definitions,
                CutoverChecklistBindingRuleRevisionMapper rules) {
            return new CutoverChecklistConfigurationQueryService(revisions, definitions, rules);
        }

        @Bean CutoverNavigationDecisionQueryService checklistNavigation(
                CutoverTaskMapper tasks, CutoverConfigurationRevisionMapper revisions) {
            return new CutoverNavigationDecisionQueryService(tasks, revisions,
                    new CutoverNavigationDecisionPolicy());
        }

        @Bean CutoverChecklistFilePort checklistFilePort() {
            return (tenantId, actorId, projectId, checklistItemId, expectedScopeVersion, handle) ->
                    new CutoverChecklistFilePort.FileFact(handle.artifactId(), handle.versionNo(),
                            handle.referenceKey(), handle.fileFactVersion(), handle.scopeVersion(), "a".repeat(64));
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
                    return new FileFact(value.artifactId(), value.versionNo(), value.referenceKey(),
                            value.fileFactVersion(), value.scopeVersion(), value.sha256());
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
                    devices, history, scopes, files, collections, platform, clock);
        }
    }
}
