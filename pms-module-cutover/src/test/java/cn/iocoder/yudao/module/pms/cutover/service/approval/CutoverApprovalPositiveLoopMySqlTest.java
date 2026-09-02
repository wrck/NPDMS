package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApiImpl;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactTransactionExecutor;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.CutoverApprovalNotificationProviderExecutor;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.CutoverApprovalNotificationService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external.CutoverExternalApprovalNotificationPort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external.CutoverExternalApprovalNotificationService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external.CutoverExternalApprovalNotificationTransactionExecutor;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external.ExternalApprovalNotificationRequest;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external.ExternalApprovalNotificationResult;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.*;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanRules;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.CutoverPlanCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.SubmitCutoverPlanResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CutoverApprovalPositiveLoopMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CutoverApprovalPositiveLoopMySqlTest {

    @Resource JdbcTemplate jdbc;
    @Resource CutoverTaskMapper taskMapper;
    @Resource CutoverAssessmentMapper assessmentMapper;
    @Resource CutoverChecklistMapper checklistMapper;
    @Resource CutoverPlanApplicationService planService;
    @Resource CutoverApprovalApplicationService approvalService;
    @Resource CutoverApprovalNotificationService notificationService;
    @Resource CutoverExternalApprovalNotificationService externalNotificationService;
    @Resource ControlledExternalNotificationPort externalNotificationPort;
    @Resource ControlledOwners owners;
    @Resource CurrentActor actor;

    long tenantId;
    long taskId;

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
        long suffix = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        tenantId = 995_100_000_000L + suffix;
        taskId = 995_200_000_000L + suffix;
        TenantContextHolder.setTenantId(tenantId);
        actor.use(8L);
        externalNotificationPort.reset();
    }

    @Test
    void externalDueRowsAreClaimedOnceAcrossConcurrentWorkers() throws Exception {
        SubmittedRoute route = submit("D", "external-concurrent");
        externalNotificationPort.blockFirstDelivery();
        LocalDateTime dueAt = LocalDateTime.of(2026, 9, 2, 0, 0);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<CutoverExternalApprovalNotificationService.DeliveryResult> first = workers.submit(() -> {
                TenantContextHolder.setTenantId(tenantId);
                try {
                    return externalNotificationService.deliverDue(tenantId, dueAt, 100);
                } finally {
                    TenantContextHolder.clear();
                }
            });
            assertTrue(externalNotificationPort.awaitFirstDelivery());
            Future<CutoverExternalApprovalNotificationService.DeliveryResult> second = workers.submit(() -> {
                TenantContextHolder.setTenantId(tenantId);
                try {
                    return externalNotificationService.deliverDue(tenantId, dueAt, 100);
                } finally {
                    TenantContextHolder.clear();
                }
            });
            CutoverExternalApprovalNotificationService.DeliveryResult skipped = second.get(10, TimeUnit.SECONDS);
            assertEquals(0, skipped.accepted());
            assertEquals(0, skipped.deliveryUnknown());
            assertEquals(0, skipped.retryScheduled());
            externalNotificationPort.releaseFirstDelivery();
            CutoverExternalApprovalNotificationService.DeliveryResult delivered = first.get(10, TimeUnit.SECONDS);
            assertEquals(3, delivered.accepted());
            assertEquals(3, externalNotificationPort.calls());
            assertEquals(3, count("SELECT COUNT(*) FROM cut_approval_notification WHERE tenant_id=? " +
                    "AND approval_instance_id=? AND channel_code IN ('SMS','EMAIL','DINGTALK') " +
                    "AND status_code='ACCEPTED'", tenantId, route.approvalInstanceId()));
        } finally {
            externalNotificationPort.releaseFirstDelivery();
            workers.shutdownNow();
        }
    }

    @AfterEach
    void tearDown() {
        for (String table : List.of("cut_approval_review_item", "cut_approval_notification",
                "cut_approval_reassignment", "cut_approval_node", "cut_approval_instance", "cut_step",
                "cut_cutover_support_arrangement", "cut_plan_revision", "cut_cutover_checklist_item_result",
                "cut_cutover_checklist_item", "cut_cutover_checklist", "cut_assessment",
                "cut_task_stage_history", "plt_outbox_event", "plt_operation_audit", "plt_idempotency_record",
                "cut_task")) {
            jdbc.update("DELETE FROM " + table + " WHERE tenant_id=?", tenantId);
        }
        TenantContextHolder.clear();
    }

    @ParameterizedTest
    @CsvSource({"A,4", "B,3", "C,2", "D,2"})
    void allGradesReachP6WithRealCutPlatformAndMysql(String grade, int expectedNodes) {
        SubmittedRoute route = submit(grade, "positive-" + grade);
        approveAll(route, expectedNodes);

        assertEquals(1, count("SELECT COUNT(*) FROM cut_task WHERE tenant_id=? AND id=? " +
                "AND current_stage='P6' AND task_status='CLOSURE_IN_PROGRESS' AND version=6", tenantId, taskId));
        assertEquals(expectedNodes, count("SELECT COUNT(*) FROM cut_approval_node WHERE tenant_id=? " +
                "AND approval_instance_id=? AND status_code='APPROVED'", tenantId, route.approvalInstanceId()));
        assertEquals(expectedNodes * 5, count("SELECT COUNT(*) FROM cut_approval_review_item WHERE tenant_id=? " +
                "AND approval_instance_id=?", tenantId, route.approvalInstanceId()));
        assertEquals(1, count("SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=? " +
                "AND event_type='CutoverApproved'", tenantId));
        assertTrue(count("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? AND status='COMPLETED'",
                tenantId) >= expectedNodes + 3);
        assertEquals(expectedNodes * 4, count("SELECT COUNT(*) FROM cut_approval_notification WHERE tenant_id=? " +
                "AND approval_instance_id=? AND correlation_id IS NOT NULL", tenantId, route.approvalInstanceId()));
        assertEquals(expectedNodes, count("SELECT COUNT(*) FROM (SELECT approval_node_id " +
                "FROM cut_approval_notification WHERE tenant_id=? AND approval_instance_id=? " +
                "GROUP BY approval_node_id HAVING COUNT(*)=4 AND COUNT(DISTINCT correlation_id)=1) grouped_notifications",
                tenantId, route.approvalInstanceId()));

        var delivery = notificationService.deliverDue(tenantId,
                LocalDateTime.of(2026, 9, 2, 0, 0), 50);
        assertEquals(expectedNodes, delivery.sent());
        assertEquals(0, delivery.retried());
        assertEquals(expectedNodes, count("SELECT COUNT(*) FROM cut_approval_notification WHERE tenant_id=? " +
                "AND approval_instance_id=? AND status_code='SENT'", tenantId, route.approvalInstanceId()));
        var externalDelivery = externalNotificationService.deliverDue(tenantId,
                LocalDateTime.of(2026, 9, 2, 0, 0), 100);
        assertEquals(expectedNodes * 3, externalDelivery.accepted());
        assertEquals(0, externalDelivery.deliveryUnknown());
        assertEquals(0, externalDelivery.retryScheduled());
        assertEquals(expectedNodes * 3, count("SELECT COUNT(*) FROM cut_approval_notification WHERE tenant_id=? " +
                "AND approval_instance_id=? AND channel_code IN ('SMS','EMAIL','DINGTALK') " +
                "AND status_code='ACCEPTED' AND provider_reference_id IS NOT NULL", tenantId,
                route.approvalInstanceId()));
        if (List.of("A", "B").contains(grade)) {
            assertEquals(202L, jdbc.queryForObject("SELECT current_approver_user_id FROM cut_approval_node " +
                    "WHERE tenant_id=? AND approval_instance_id=? AND node_code='SECOND_LINE'", Long.class,
                    tenantId, route.approvalInstanceId()));
        }
    }

    @Test
    void rejectedRevisionIsImmutableAndReplacementStartsNewApproval() {
        SubmittedRoute first = submit("D", "reject");
        actor.use(8L);
        approvalService.reject(new RejectCutoverApprovalCommand(tenantId, taskId, 5, 0,
                noItems(), null, "回退步骤需修订", "reject-d", "corr-reject-d"));
        String frozen = jdbc.queryForObject("SELECT source_snapshot FROM cut_approval_instance WHERE tenant_id=? AND id=?",
                String.class, tenantId, first.approvalInstanceId());

        owners.advanceTaskVersion(6);
        CutoverPlanCommandResult replacement = planService.revise(new ReviseCutoverPlanCommand(
                tenantId, 8L, taskId, 6, first.planRevisionId(), "APPROVAL_REJECTED",
                "revise-d", "corr-revise-d"));
        var content = simpleContent();
        CutoverPlanCommandResult saved = planService.saveDraft(new SaveCutoverPlanDraftCommand(
                tenantId, 8L, taskId, 6, replacement.planVersion(), 30L, content,
                "save-d-r2", "corr-save-d-r2"));
        SubmitCutoverPlanResult second = planService.submit(new SubmitCutoverPlanCommand(
                tenantId, 8L, taskId, 6, saved.planVersion(), "submit-d-r2", "corr-submit-d-r2"));

        assertNotEquals(first.approvalInstanceId(), second.approvalInstanceId());
        assertEquals(frozen, jdbc.queryForObject("SELECT source_snapshot FROM cut_approval_instance " +
                "WHERE tenant_id=? AND id=?", String.class, tenantId, first.approvalInstanceId()));
        assertEquals(5, count("SELECT COUNT(*) FROM cut_approval_review_item WHERE tenant_id=? " +
                "AND approval_instance_id=?", tenantId, first.approvalInstanceId()));
        assertEquals(1, count("SELECT COUNT(*) FROM cut_approval_instance WHERE tenant_id=? AND id=? " +
                "AND status_code='REJECTED' AND replacement_approval_instance_id=?", tenantId,
                first.approvalInstanceId(), second.approvalInstanceId()));
        assertEquals(1, count("SELECT COUNT(*) FROM cut_plan_revision WHERE tenant_id=? AND id=? " +
                "AND source_plan_revision_id=? AND revision_reason_code='APPROVAL_REJECTED'", tenantId,
                second.planRevisionId(), first.planRevisionId()));
    }

    @Test
    void fullFileUploadReachesP6WithoutTemplateChildrenInFrozenContent() {
        insertP4Facts("A");
        owners.reset(taskId, projectId(), assessmentId(), checklistId("A"), "A");
        CutoverPlanFilePort.FileFact file = owners.fileFact();
        CutoverPlanCommandResult created = planService.createDraft(new CreateCutoverPlanDraftCommand(
                tenantId, 8L, taskId, 4, 30L, "FULL_FILE_UPLOAD", file, true,
                "create-upload", "corr-create-upload"));
        SubmitCutoverPlanResult submitted = planService.submit(new SubmitCutoverPlanCommand(
                tenantId, 8L, taskId, 4, created.planVersion(), "submit-upload", "corr-submit-upload"));
        SubmittedRoute route = new SubmittedRoute(submitted.planRevisionId(), submitted.approvalInstanceId());
        approveAll(route, 4);

        String frozen = jdbc.queryForObject("SELECT source_snapshot FROM cut_approval_instance " +
                "WHERE tenant_id=? AND id=?", String.class, tenantId, route.approvalInstanceId());
        var content = JsonUtils.parseTree(frozen).path("plan").path("content");
        assertEquals("FULL_FILE_UPLOAD", content.path("editMode").asText());
        assertTrue(content.path("ownershipConfirmed").asBoolean());
        assertEquals(file.referenceKey(), content.path("fileArtifactFact").path("referenceKey").asText());
        assertFalse(content.has("steps"));
        assertFalse(content.has("supportArrangements"));
        assertEquals(1, count("SELECT COUNT(*) FROM cut_task WHERE tenant_id=? AND id=? " +
                "AND current_stage='P6' AND task_status='CLOSURE_IN_PROGRESS'", tenantId, taskId));
    }

    SubmittedRoute submit(String grade, String key) {
        return submit(grade, key, LocalDateTime.of(2026, 9, 3, 10, 0));
    }

    SubmittedRoute submit(String grade, String key, LocalDateTime scheduledTime) {
        insertP4Facts(grade, scheduledTime);
        owners.reset(taskId, projectId(), assessmentId(), checklistId(grade), grade);
        CutoverPlanCommandResult created = planService.createDraft(new CreateCutoverPlanDraftCommand(
                tenantId, 8L, taskId, 4, 30L, "D".equals(grade)
                ? "ONLINE_TEMPLATE_SIMPLE_D" : "ONLINE_TEMPLATE_STANDARD", null, null,
                "create-" + key, "corr-create-" + key));
        CutoverPlanCommandResult saved = planService.saveDraft(new SaveCutoverPlanDraftCommand(
                tenantId, 8L, taskId, 4, created.planVersion(), 30L,
                "D".equals(grade) ? simpleContent() : standardContent(),
                "save-" + key, "corr-save-" + key));
        String submitCorrelationId = "positive-A".equals(key) ? "A".repeat(128)
                : "positive-B".equals(key) ? "B" : "corr-submit-" + key;
        SubmitCutoverPlanResult result = planService.submit(new SubmitCutoverPlanCommand(
                tenantId, 8L, taskId, 4, saved.planVersion(), "submit-" + key, submitCorrelationId));
        assertEquals("P5", result.taskStage());
        if (List.of("A", "B").contains(grade)) {
            LocalDateTime submittedAt = jdbc.queryForObject("SELECT submitted_at FROM cut_plan_revision " +
                    "WHERE tenant_id=? AND id=?", LocalDateTime.class, tenantId, result.planRevisionId());
            String snapshot = jdbc.queryForObject("SELECT lead_time_snapshot FROM cut_approval_instance " +
                    "WHERE tenant_id=? AND id=?", String.class, tenantId, result.approvalInstanceId());
            assertEquals(submittedAt.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(),
                    new CutoverLeadTimeSnapshotCodec().decode(snapshot).planSubmittedAt());
        }
        return new SubmittedRoute(result.planRevisionId(), result.approvalInstanceId());
    }

    void approveAll(SubmittedRoute route, int expectedNodes) {
        List<Map<String, Object>> nodes = jdbc.queryForList("SELECT node_no,node_code,current_approver_user_id " +
                "FROM cut_approval_node WHERE tenant_id=? AND approval_instance_id=? ORDER BY node_no",
                tenantId, route.approvalInstanceId());
        assertEquals(expectedNodes, nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            Map<String, Object> node = nodes.get(index);
            actor.use(((Number) node.get("current_approver_user_id")).longValue());
            String code = String.valueOf(node.get("node_code"));
            approvalService.approve(new ApproveCutoverApprovalCommand(tenantId, taskId, 5, index,
                    yesItems(), "SERVICE_MANAGER".equals(code)
                    ? new AssessmentReviewInput("CONFIRMED", null) : null,
                    "节点审批通过", "approve-" + route.approvalInstanceId() + "-" + (index + 1),
                    "corr-approve-" + route.approvalInstanceId() + "-" + (index + 1)));
        }
    }

    private void insertP4Facts(String grade) {
        insertP4Facts(grade, LocalDateTime.of(2026, 9, 3, 10, 0));
    }

    private void insertP4Facts(String grade, LocalDateTime scheduledTime) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 8, 0);
        CutoverTaskDO task = new CutoverTaskDO();
        task.setId(taskId); task.setTenantId(tenantId); task.setProjectId(projectId()); task.setTaskNo("CUT-" + taskId);
        task.setTaskName(grade + "级审批闭环"); task.setBackground("受控Owner事实正向闭环");
        task.setCutoverType("NETWORK_TOPOLOGY_CHANGE"); task.setNetworkMode("DUAL");
        task.setScheduledTime(scheduledTime); task.setTaskOrigin("NEW_PLATFORM");
        task.setIntakeSourceType("SELF_CREATED"); task.setCurrentStage("P4"); task.setTaskStatus("PLAN_DRAFTING");
        task.setOwnerUserId(8L); task.setCustomerId(99L); task.setImplementationReadinessSnapshotId(7L);
        task.setImplementationReadinessSnapshotVersion(1L); task.setProjectScopeVersion(30L);
        task.setProjectContextSnapshot(JsonUtils.toJsonString(new CutoverProjectContextPort.ProjectContextFact(
                tenantId, projectId(), 6, "PRJ-1", "割接项目", 99L, "CUS-1", "客户",
                88L, "OFF-1", "交付部", 30L)));
        task.setDeviceScopeWatermark("{}"); task.setCustomerContextSnapshot("{}");
        task.setReadinessContextSnapshot("{}"); task.setManualGrade(grade); task.setConfigurationRevisionId(401L);
        task.setConfigurationCode("CFG-1"); task.setConfigurationRevisionNo(1); task.setVersion(4);
        task.setCreator("8"); task.setUpdater("8"); task.setCreateTime(now); task.setUpdateTime(now);
        assertEquals(1, taskMapper.insert(task));

        CutoverAssessmentDO assessment = new CutoverAssessmentDO();
        assessment.setId(assessmentId()); assessment.setTenantId(tenantId); assessment.setCutoverTaskId(taskId);
        assessment.setAssessmentVersion(2); assessment.setAssessmentStatus("SUBMITTED");
        assessment.setQuestionnaireTemplateCode("CUT_P2_MANUAL_ASSESSMENT");
        assessment.setQuestionnaireTemplateVersion(1L);
        assessment.setAnswerSnapshot(JsonUtils.toJsonString(new CutoverAssessmentAnswers("HIGH", "MEDIUM", "LOW", true)));
        assessment.setContextSnapshot("{\"implementationReadiness\":{\"decision\":\"READY\",\"unmetCodes\":[]},"
                + "\"customerServiceLevel\":{\"status\":\"AVAILABLE\",\"serviceLevelCode\":\"GOLD\"}}");
        assessment.setManualGrade(grade); assessment.setSimpleFlow("D".equals(grade)); assessment.setSubmittedBy(8L);
        assessment.setSubmittedAt(now); assessment.setCurrentMarker(1); assessment.setVersion(0);
        assessment.setCreator("8"); assessment.setUpdater("8"); assessment.setCreateTime(now); assessment.setUpdateTime(now);
        assertEquals(1, assessmentMapper.insert(assessment));

        if (!"D".equals(grade)) {
            CutoverChecklistDO checklist = new CutoverChecklistDO();
            checklist.setId(checklistId(grade)); checklist.setTenantId(tenantId); checklist.setCutoverTaskId(taskId);
            checklist.setAssessmentId(assessmentId()); checklist.setAssessmentVersion(2); checklist.setChecklistVersion(3);
            checklist.setStatusCode("SUBMITTED"); checklist.setInputSnapshot("{}");
            checklist.setInputSnapshotHash("a".repeat(64)); checklist.setConfigRevisionSnapshot("{}");
            checklist.setMatchTrace("{}"); checklist.setConfigGapSnapshot("{}"); checklist.setSubmittedBy(8L);
            checklist.setSubmittedAt(now); checklist.setVersion(0);
            checklist.setCreator("8"); checklist.setUpdater("8"); checklist.setCreateTime(now); checklist.setUpdateTime(now);
            assertEquals(1, checklistMapper.insert(checklist));
        }
    }

    private tools.jackson.databind.node.ObjectNode standardContent() {
        var content = JsonUtils.getObjectMapper().createObjectNode(); content.put("editMode", "ONLINE_TEMPLATE_STANDARD");
        var overview = content.putObject("overview"); overview.put("projectDescription", "项目说明");
        overview.putArray("scheduleTable").addObject().put("sequenceNo", 1).put("plannedAt", 1_788_192_000_000L)
                .put("content", "实施计划"); overview.putNull("preTopologyFile"); overview.putNull("postTopologyFile");
        var device = overview.putArray("deviceSummary").addObject(); device.put("deviceId", 301L);
        device.put("serialNumber", "SN-1"); device.put("projectAssignmentVersion", 9L);
        device.put("deviceTypeCode", "ROUTER"); device.put("deviceTypeSourceVersion", "type-v1");
        overview.putNull("networkConfigurationFile");
        var steps = content.putArray("steps");
        CutoverPlanRules.STANDARD_SECTIONS.forEach(section -> steps.addObject().put("sectionCode", section)
                .put("stepNo", 1).put("content", section + "执行内容"));
        content.putArray("riskMitigations"); var supports = content.putArray("supportArrangements");
        CutoverPlanRules.SUPPORT_ROLES.forEach(role -> supports.addObject().putNull("arrangementId")
                .put("roleCode", role).put("personName", role + "负责人").put("dutyDescription", role + "保障")
                .put("phone", "13800000000").put("arrivalTime", 1_788_192_000_000L));
        return content;
    }

    private tools.jackson.databind.node.ObjectNode simpleContent() {
        var content = JsonUtils.getObjectMapper().createObjectNode(); content.put("editMode", "ONLINE_TEMPLATE_SIMPLE_D");
        content.putArray("steps").addObject().put("sectionCode", "OPERATION").put("stepNo", 1).put("content", "执行割接");
        content.withArray("steps").addObject().put("sectionCode", "ROLLBACK").put("stepNo", 1).put("content", "执行回退");
        return content;
    }

    private static List<ReviewItemInput> yesItems() {
        return CutoverApprovalRules.REVIEW_ITEM_CODES.stream().map(code -> new ReviewItemInput(code, "YES", null)).toList();
    }

    private static List<ReviewItemInput> noItems() {
        List<ReviewItemInput> rows = new ArrayList<>(yesItems());
        rows.set(0, new ReviewItemInput("PREPARATION", "NO", "准备不足")); return rows;
    }

    private long projectId() { return taskId + 100; }
    private long assessmentId() { return taskId + 200; }
    private Long checklistId(String grade) { return "D".equals(grade) ? null : taskId + 300; }
    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    private static String required(Map<String, String> env, String key) {
        String value = env.get(key); if (value == null || value.isBlank()) throw new IllegalStateException(key + " is required");
        return value;
    }

    record SubmittedRoute(long planRevisionId, long approvalInstanceId) { }

    static final class CurrentActor {
        private final AtomicLong value = new AtomicLong(8L);
        long current() { return value.get(); }
        void use(long actorId) { value.set(actorId); }
    }

    static final class ControlledOwners implements CutoverProjectScopePort, CutoverPlanSourcePort, CutoverPlanFilePort {
        long taskId; long projectId; SourceFacts facts;
        void reset(long taskId, long projectId, long assessmentId, Long checklistId, String grade) {
            this.taskId = taskId; this.projectId = projectId;
            List<String> sectionCodes = "D".equals(grade) ? CutoverPlanRules.SIMPLE_SECTIONS : CutoverPlanRules.STANDARD_SECTIONS;
            List<TemplateSectionSnapshot> sections = sectionCodes.stream().map(key -> new TemplateSectionSnapshot(
                    key, key, sectionCodes.indexOf(key) + 1, List.of("NETWORK_CUTOVER"), List.of(grade), true)).toList();
            facts = new SourceFacts(new SourceSnapshot(1, taskId, 4, assessmentId, 2, grade, checklistId,
                    checklistId == null ? null : 3, projectId, 6, 30L,
                    List.of(new DeviceSnapshot(301L, "SN-1", 9L, "ROUTER", "type-v1")),
                    401L, "CFG-1", 1, sections, List.of()), List.of());
        }
        void advanceTaskVersion(int version) {
            SourceSnapshot s = facts.snapshot(); facts = new SourceFacts(new SourceSnapshot(s.snapshotVersion(), s.taskId(),
                    version, s.assessmentId(), s.assessmentVersion(), s.grade(), s.checklistId(), s.checklistVersion(),
                    s.projectId(), s.projectVersion(), s.projectScopeVersion(), s.devices(), s.configurationRevisionId(),
                    s.configurationCode(), s.configurationRevisionNo(), s.templateSections(), s.failedRiskFacts()), List.of());
        }
        @Override public ProjectScopeFact inspect(Long actorId, Long projectId, String action) { return new ProjectScopeFact(projectId, 30L, true); }
        @Override public ProjectScopeFact lockAndRevalidate(Long actorId, Long projectId, String action, long expected) { return new ProjectScopeFact(projectId, 30L, true); }
        @Override public Set<Long> resolveAllCurrent(Long actorId, String action) { return Set.of(projectId); }
        @Override public SourceFacts inspect(Long tenantId, Long actorId, Long taskId) { return facts; }
        @Override public SourceFacts lockAndRevalidate(Long tenantId, Long actorId, SourceFacts expected) { return facts; }
        FileFact fileFact() {
            return new FileFact(501L, 1, "cut-plan-upload-1", new FileFactVersion(1, 1, 1),
                    1L, "a".repeat(64));
        }
        @Override public FileFact inspect(Long tenantId, Long actorId, Long projectId, FileHandle handle) {
            return fileFact();
        }
        @Override public FileFact lockAndRevalidate(Long tenantId, Long actorId, Long projectId, FileHandle handle) {
            return fileFact();
        }
        @Override public FileFact downloadDraft(Long tenantId, Long actorId, Long projectId, Long planRevisionId) {
            return fileFact();
        }
    }

    @SpringBootConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan({"cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC); }
        @Bean CurrentActor actor() { return new CurrentActor(); }
        @Bean ControlledOwners owners() { return new ControlledOwners(); }
        @Bean ProjectCutoverServiceManagerPort manager() { return CutoverApprovalControlledPorts.serviceManager(301L); }
        @Bean CutoverApprovalRoleCandidatePort candidates() { return CutoverApprovalControlledPorts.roleCandidates(); }
        @Bean CutoverApprovalProjectScopePort approvalScope() {
            return new CutoverApprovalProjectScopePort() {
                public ProjectScopeFact inspect(long tenant, long project, long user, String action) {
                    boolean allowed = (user == 8L && "ACTION_EDIT".equals(action))
                            || (user == 202L && "ACTION_VIEW".equals(action));
                    return new ProjectScopeFact(tenant, project, user, action, allowed, 11L);
                }
                public ProjectScopeRevalidation lockAndRevalidate(ProjectScopeFact expected) {
                    return new ProjectScopeRevalidation(Revalidation.VALID, expected);
                }
            };
        }
        @Bean CutoverApprovalSourceSnapshotCodec approvalCodec() { return new CutoverApprovalSourceSnapshotCodec(); }
        @Bean CutoverApprovalSourceAssembler sourceAssembler(CutoverTaskMapper tasks, CutoverAssessmentMapper assessments,
                CutoverChecklistMapper checklists, CutoverChecklistItemMapper items,
                CutoverChecklistItemResultMapper results, CutoverPlanRevisionMapper plans,
                CutoverPlanStepMapper steps, CutoverSupportArrangementMapper supports,
                CutoverApprovalSourceSnapshotCodec codec) {
            return new CutoverApprovalSourceAssembler(tasks, assessments, checklists, items, results, plans,
                    steps, supports, codec);
        }
        @Bean CutoverApprovalApplicationService approvalService(CutoverApprovalSourceAssembler assembler,
                CutoverApprovalInstanceMapper instances, CutoverApprovalNodeMapper nodes,
                CutoverApprovalNotificationMapper notifications, CutoverApprovalReviewItemMapper reviews,
                CutoverApprovalReassignmentMapper reassignments, CutoverTaskMapper tasks,
                CutoverTaskStageHistoryMapper history, ProjectCutoverServiceManagerPort manager,
                CutoverApprovalRoleCandidatePort candidates, CutoverApprovalProjectScopePort scope,
                PlatformCommandExecutionApi platform, CurrentActor actor, Clock clock) {
            return new CutoverApprovalApplicationService(assembler, instances, nodes, notifications, reviews,
                    reassignments, tasks, history, manager, candidates, scope, platform, actor::current, clock);
        }
        @Bean CutoverApprovalFactTransactionExecutor approvalTransactions(CutoverApprovalApplicationService service,
                CutoverApprovalInstanceMapper instances) { return new CutoverApprovalFactTransactionExecutor(service, instances); }
        @Bean CutoverApprovalFactApi approvalApi(CutoverApprovalFactTransactionExecutor executor) {
            return new CutoverApprovalFactApiImpl(executor);
        }
        @Bean CutoverPlanApplicationService planService(CutoverTaskMapper tasks, CutoverPlanRevisionMapper plans,
                CutoverPlanStepMapper steps, CutoverSupportArrangementMapper supports,
                CutoverTaskStageHistoryMapper history, ControlledOwners owners, CutoverApprovalFactApi approvals,
                PlatformCommandExecutionApi platform, Clock clock) {
            return new CutoverPlanApplicationService(tasks, plans, steps, supports, owners, owners, owners,
                    new CutoverPlanContentCodec(), platform, approvals, history, clock);
        }
        @Bean NotifyMessageSendApi notifyApi() {
            return new NotifyMessageSendApi() {
                private final AtomicLong ids = new AtomicLong(1_000);
                public Long sendSingleMessageToAdmin(NotifySendSingleToUserReqDTO reqDTO) { return ids.incrementAndGet(); }
                public Long sendSingleMessageToMember(NotifySendSingleToUserReqDTO reqDTO) { return ids.incrementAndGet(); }
            };
        }
        @Bean CutoverApprovalNotificationProviderExecutor notificationProvider(NotifyMessageSendApi api) {
            return new CutoverApprovalNotificationProviderExecutor(api);
        }
        @Bean CutoverApprovalNotificationService notificationService(CutoverApprovalNotificationMapper notifications,
                CutoverApprovalInstanceMapper instances, CutoverApprovalNodeMapper nodes, CutoverTaskMapper tasks,
                CutoverApprovalNotificationProviderExecutor provider) {
            return new CutoverApprovalNotificationService(notifications, instances, nodes, tasks, provider);
        }
        @Bean ControlledExternalNotificationPort externalNotificationPort() {
            return new ControlledExternalNotificationPort();
        }
        @Bean CutoverExternalApprovalNotificationTransactionExecutor externalNotificationTransactions(
                CutoverApprovalNotificationMapper notifications, CutoverApprovalInstanceMapper instances,
                CutoverApprovalNodeMapper nodes, CutoverTaskMapper tasks,
                CutoverExternalApprovalNotificationPort port) {
            return new CutoverExternalApprovalNotificationTransactionExecutor(
                    notifications, instances, nodes, tasks, port);
        }
        @Bean CutoverExternalApprovalNotificationService externalNotificationService(
                CutoverExternalApprovalNotificationTransactionExecutor executor) {
            return new CutoverExternalApprovalNotificationService(executor);
        }
    }

    static final class ControlledExternalNotificationPort implements CutoverExternalApprovalNotificationPort {
        private enum Mode { ALL_ACCEPTED, CONTROLLED_CHANNEL_RESULTS }

        private final AtomicLong references = new AtomicLong(2_000);
        private final AtomicInteger calls = new AtomicInteger();
        private final Map<String, AtomicInteger> deliveryAttempts = new ConcurrentHashMap<>();
        private final List<ExternalApprovalNotificationRequest> requests = new CopyOnWriteArrayList<>();
        private volatile Mode mode = Mode.ALL_ACCEPTED;
        private volatile CountDownLatch firstDeliveryStarted = new CountDownLatch(0);
        private volatile CountDownLatch firstDeliveryReleased = new CountDownLatch(0);

        void reset() {
            calls.set(0);
            deliveryAttempts.clear();
            requests.clear();
            mode = Mode.ALL_ACCEPTED;
            firstDeliveryStarted = new CountDownLatch(0);
            firstDeliveryReleased = new CountDownLatch(0);
        }

        void useControlledChannelResults() {
            mode = Mode.CONTROLLED_CHANNEL_RESULTS;
        }

        List<ExternalApprovalNotificationRequest> requests() {
            return List.copyOf(requests);
        }

        void blockFirstDelivery() {
            firstDeliveryStarted = new CountDownLatch(1);
            firstDeliveryReleased = new CountDownLatch(1);
        }

        boolean awaitFirstDelivery() throws InterruptedException {
            return firstDeliveryStarted.await(10, TimeUnit.SECONDS);
        }

        void releaseFirstDelivery() {
            firstDeliveryReleased.countDown();
        }

        int calls() {
            return calls.get();
        }

        @Override
        public ExternalApprovalNotificationResult send(ExternalApprovalNotificationRequest request) {
            int call = calls.incrementAndGet();
            requests.add(request);
            if (call == 1 && firstDeliveryStarted.getCount() > 0) {
                firstDeliveryStarted.countDown();
                try {
                    if (!firstDeliveryReleased.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("controlled external delivery release timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("controlled external delivery interrupted", exception);
                }
            }
            if (mode == Mode.CONTROLLED_CHANNEL_RESULTS) {
                int attempt = deliveryAttempts.computeIfAbsent(request.deliveryKey(), ignored -> new AtomicInteger())
                        .incrementAndGet();
                if ("EMAIL".equals(request.channel()) && attempt == 1) {
                    return new ExternalApprovalNotificationResult.ExplicitFailure("CONTROLLED_RETRY");
                }
                if ("DINGTALK".equals(request.channel())) {
                    return new ExternalApprovalNotificationResult.DeliveryUnknown(
                            "controlled-unknown-" + references.incrementAndGet());
                }
            }
            return new ExternalApprovalNotificationResult.Accepted(
                    "controlled-" + references.incrementAndGet(), LocalDateTime.of(2026, 9, 2, 0, 0));
        }
    }
}
