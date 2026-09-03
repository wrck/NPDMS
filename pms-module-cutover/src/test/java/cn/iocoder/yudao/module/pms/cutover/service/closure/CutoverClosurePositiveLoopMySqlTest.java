package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.HandleClosureCollectionCallbackCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.RequestClosureCollectionCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SubmitCutoverClosureCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.AttachmentPurpose;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.CollectionStage;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.SavedCredential;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort.FileFactVersion;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CutoverClosureApplicationMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CutoverClosurePositiveLoopMySqlTest {

    @Resource JdbcTemplate jdbc;
    @Resource CutoverTaskMapper taskMapper;
    @Resource CutoverPlanRevisionMapper planMapper;
    @Resource CutoverApprovalInstanceMapper approvalMapper;
    @Resource CutoverClosureApplicationService service;

    private long tenantId;
    private long taskId;
    private long planId;
    private long approvalId;
    private long projectId;
    private long deviceId;

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
        tenantId = 995_100_000_000L + suffix;
        taskId = 995_200_000_000L + suffix;
        planId = 995_300_000_000L + suffix;
        approvalId = 995_400_000_000L + suffix;
        projectId = 995_500_000_000L + suffix;
        deviceId = 995_600_000_000L + suffix;
        TenantContextHolder.setTenantId(tenantId);
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 8, 0);

        CutoverTaskDO task = new CutoverTaskDO();
        task.setId(taskId); task.setTenantId(tenantId); task.setProjectId(projectId);
        task.setTaskNo("CUT-P6-LOOP-" + suffix); task.setTaskName("P6关闭正向闭环");
        task.setBackground("Task 9真实MySQL正向链"); task.setCutoverType("NETWORK_CUTOVER");
        task.setNetworkMode("DUAL"); task.setScheduledTime(now); task.setTaskOrigin("NEW_PLATFORM");
        task.setIntakeSourceType("SELF_CREATED"); task.setCurrentStage("P6");
        task.setTaskStatus("CLOSURE_IN_PROGRESS"); task.setOwnerUserId(8L); task.setCustomerId(99L);
        task.setImplementationReadinessSnapshotId(7L); task.setImplementationReadinessSnapshotVersion(1L);
        task.setProjectScopeVersion(30L); task.setProjectContextSnapshot("{}");
        task.setDeviceScopeWatermark("{\"devices\":[301]}"); task.setCustomerContextSnapshot("{}");
        task.setReadinessContextSnapshot("{}"); task.setManualGrade("A"); task.setCurrentAssessmentId(701L);
        task.setConfigurationRevisionId(401L); task.setConfigurationCode("CFG-1");
        task.setConfigurationRevisionNo(1); task.setVersion(7); task.setCreator("8"); task.setUpdater("8");
        assertEquals(1, taskMapper.insert(task));

        CutoverPlanRevisionDO plan = new CutoverPlanRevisionDO();
        plan.setId(planId); plan.setTenantId(tenantId); plan.setCutoverTaskId(taskId); plan.setRevisionNo(1);
        plan.setOriginCode("NEW_PLATFORM"); plan.setEditModeCode("ONLINE_TEMPLATE_STANDARD"); plan.setGradeCode("A");
        plan.setAssessmentId(701L); plan.setAssessmentVersion(1); plan.setChecklistId(702L); plan.setChecklistVersion(1);
        plan.setConfigurationRevisionId(401L); plan.setConfigurationCode("CFG-1"); plan.setConfigurationRevisionNo(1);
        plan.setTemplateSectionSnapshot("[]"); plan.setSourceSnapshot("{}"); plan.setContentSnapshot("{}");
        plan.setStatusCode("SUBMITTED"); plan.setCurrentMarker(1); plan.setSubmittedBy(8L); plan.setSubmittedAt(now);
        plan.setApprovalInstanceId(approvalId); plan.setApprovalVersion(4); plan.setVersion(6);
        plan.setCreator("8"); plan.setUpdater("8");
        assertEquals(1, planMapper.insert(plan));

        CutoverApprovalInstanceDO approval = new CutoverApprovalInstanceDO();
        approval.setId(approvalId); approval.setTenantId(tenantId); approval.setTaskId(taskId);
        approval.setProjectId(projectId); approval.setPlanRevisionId(planId); approval.setPlanRevisionNo(1);
        approval.setAssessmentId(701L); approval.setAssessmentVersion(1); approval.setChecklistId(702L);
        approval.setChecklistVersion(1); approval.setGradeCode("A"); approval.setInitiatorUserId(8L);
        approval.setInitiatorProjectScopeVersion(30L); approval.setSourceSnapshotVersion(1);
        approval.setSourceSnapshot("{}"); approval.setRouteSnapshot("{}"); approval.setStatusCode("APPROVED");
        approval.setDecisionAt(now); approval.setVersion(4); approval.setCreator("8"); approval.setUpdater("8");
        assertEquals(1, approvalMapper.insert(approval));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM cut_cutover_collection_evidence WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_cutover_closure_attachment WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_cutover_closure WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task_device_scope WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task_stage_history WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_approval_instance WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_plan_revision WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM plt_operation_audit WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM plt_outbox_event WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM plt_idempotency_record WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task WHERE tenant_id=?", tenantId);
        TenantContextHolder.clear();
    }

    @Test
    void completesP6DraftCollectionFilesAndSuccessArchiveInOneRealStack() {
        service.save(new SaveCutoverClosureCommand(tenantId, 8L, taskId, 7, null,
                new SaveCutoverClosureCommand.ClosureContent(true, null, true, null, true, null,
                        false, null, null, null, null, attachments()),
                "loop-create", "corr-loop-create"));
        Long closureId = jdbc.queryForObject(
                "SELECT id FROM cut_cutover_closure WHERE tenant_id=? AND task_id=?", Long.class, tenantId, taskId);
        jdbc.update("""
                INSERT INTO cut_task_device_scope
                  (id,tenant_id,cutover_task_id,project_id,device_id,serial_number_snapshot,
                   project_assignment_version,active_marker,version,creator,create_time,updater,update_time,deleted)
                VALUES (?,?,?,?,?,'SN-P6-LOOP',1,1,0,'8',NOW(3),'8',NOW(3),b'0')
                """, 996_000_000_000L + Math.floorMod(deviceId, 1_000_000L), tenantId, taskId, projectId, deviceId);

        service.requestCollection(new RequestClosureCollectionCommand(tenantId, 8L, taskId, 7, closureId, 0,
                deviceId, CollectionStage.POST_COLLECTION, new SavedCredential(71L, 3L),
                "post-check", 2L, "loop-collect", "corr-loop-collect"));
        String collectionTaskId = jdbc.queryForObject("""
                SELECT collection_task_id FROM cut_cutover_collection_evidence
                 WHERE tenant_id=? AND closure_id=? AND evidence_type_code='DISPATCH_ACCEPTED'
                """, String.class, tenantId, closureId);
        service.handleCollectionCallback(new HandleClosureCollectionCallbackCommand(tenantId, taskId, closureId,
                deviceId, CollectionStage.POST_COLLECTION, "loop-callback", collectionTaskId, true,
                "loop-result", "v1", LocalDateTime.of(2026, 9, 2, 8, 1), "corr-loop-callback"));

        SubmitCutoverClosureCommand submit = new SubmitCutoverClosureCommand(tenantId, 8L, taskId, 7, closureId, 2,
                "SUCCESS", "loop-submit", "corr-loop-submit");
        service.submit(submit);
        service.submit(submit);

        assertEquals("ARCHIVED", text("SELECT task_status FROM cut_task WHERE tenant_id=? AND id=?", tenantId, taskId));
        assertEquals("SUBMITTED", text("SELECT status_code FROM cut_cutover_closure WHERE tenant_id=? AND id=?", tenantId, closureId));
        assertEquals("CUTOVER_CLOSURE:" + closureId + ":3",
                text("SELECT result_ref FROM cut_cutover_closure WHERE tenant_id=? AND id=?", tenantId, closureId));
        assertEquals(2, number("SELECT COUNT(*) FROM cut_cutover_closure_attachment WHERE tenant_id=?", tenantId));
        assertEquals(2, number("SELECT COUNT(*) FROM cut_cutover_collection_evidence WHERE tenant_id=?", tenantId));
        assertEquals(0, number("SELECT COUNT(*) FROM cut_task_device_scope WHERE tenant_id=? AND active_marker=1", tenantId));
        assertEquals(1, number("SELECT COUNT(*) FROM cut_task_stage_history WHERE tenant_id=? AND trigger_type='P6_CLOSURE_SUBMITTED'", tenantId));
        assertEquals(1, number("SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=? AND event_type='CutoverCompleted'", tenantId));
        assertEquals(1, number("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? AND correlation_id='corr-loop-submit'", tenantId));
    }

    private static List<SaveCutoverClosureCommand.AttachmentInput> attachments() {
        return List.of(file(AttachmentPurpose.POST_COLLECTION_CHECKLIST, 501L, "loop-checklist"),
                file(AttachmentPurpose.IMPLEMENTATION_COMMITMENT, 502L, "loop-commitment"));
    }

    private static SaveCutoverClosureCommand.AttachmentInput file(AttachmentPurpose purpose, long artifactId,
                                                                   String referenceKey) {
        return new SaveCutoverClosureCommand.AttachmentInput(purpose, artifactId, 1, referenceKey,
                new FileFactVersion(1, 2, 3), 4L, "a".repeat(64));
    }

    private int number(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String text(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
