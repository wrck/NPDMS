package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureAttachmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverCollectionEvidenceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.HandleClosureCollectionCallbackCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.LinkClosureManualResultCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.RequestClosureCollectionCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.AttachmentInput;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.ClosureContent;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.AttachmentPurpose;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.CollectionStage;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.DispatchOutcome;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.SavedCredential;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.CollectionIntentIdentity;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort.CollectionRequest;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort.FileFactVersion;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CutoverClosureApplicationMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CutoverClosureApplicationMySqlTest {
    @Resource JdbcTemplate jdbc;
    @Resource CutoverTaskMapper taskMapper;
    @Resource CutoverPlanRevisionMapper planMapper;
    @Resource CutoverApprovalInstanceMapper approvalMapper;
    @Resource CutoverClosureApplicationService service;
    @Resource CutoverClosureControlledPorts.Collections collections;

    long tenantId;
    long taskId;
    long planId;
    long approvalId;
    long projectId;
    long deviceId;

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
        collections.nextDispatch(DispatchOutcome.ACCEPTED, null);
        long suffix = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        tenantId = 991_100_000_000L + suffix;
        taskId = 991_200_000_000L + suffix;
        planId = 991_300_000_000L + suffix;
        approvalId = 991_400_000_000L + suffix;
        projectId = 991_500_000_000L + suffix;
        deviceId = 991_600_000_000L + suffix;
        TenantContextHolder.setTenantId(tenantId);
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 8, 0);
        CutoverTaskDO task = new CutoverTaskDO(); task.setId(taskId); task.setTenantId(tenantId);
        task.setProjectId(projectId); task.setTaskNo("CUT-P6-" + suffix);
        task.setTaskName("P6闭环"); task.setBackground("P6正向链"); task.setCutoverType("NETWORK_CUTOVER");
        task.setNetworkMode("DUAL"); task.setScheduledTime(now); task.setTaskOrigin("NEW_PLATFORM");
        task.setIntakeSourceType("SELF_CREATED"); task.setCurrentStage("P6"); task.setTaskStatus("CLOSURE_IN_PROGRESS");
        task.setOwnerUserId(8L); task.setCustomerId(99L); task.setImplementationReadinessSnapshotId(7L);
        task.setImplementationReadinessSnapshotVersion(1L); task.setProjectScopeVersion(30L);
        task.setProjectContextSnapshot("{}"); task.setDeviceScopeWatermark("{\"devices\":[301]}");
        task.setCustomerContextSnapshot("{}"); task.setReadinessContextSnapshot("{}"); task.setManualGrade("A");
        task.setCurrentAssessmentId(701L); task.setConfigurationRevisionId(401L); task.setConfigurationCode("CFG-1");
        task.setConfigurationRevisionNo(1); task.setVersion(7); task.setCreator("8"); task.setUpdater("8");
        assertEquals(1, taskMapper.insert(task));

        CutoverPlanRevisionDO plan = new CutoverPlanRevisionDO(); plan.setId(planId); plan.setTenantId(tenantId);
        plan.setCutoverTaskId(taskId); plan.setRevisionNo(1); plan.setOriginCode("NEW_PLATFORM");
        plan.setEditModeCode("ONLINE_TEMPLATE_STANDARD"); plan.setGradeCode("A"); plan.setAssessmentId(701L);
        plan.setAssessmentVersion(1); plan.setChecklistId(702L); plan.setChecklistVersion(1);
        plan.setConfigurationRevisionId(401L); plan.setConfigurationCode("CFG-1"); plan.setConfigurationRevisionNo(1);
        plan.setTemplateSectionSnapshot("[]"); plan.setSourceSnapshot("{}"); plan.setContentSnapshot("{}");
        plan.setStatusCode("SUBMITTED"); plan.setCurrentMarker(1); plan.setSubmittedBy(8L); plan.setSubmittedAt(now);
        plan.setApprovalInstanceId(approvalId); plan.setApprovalVersion(4); plan.setVersion(6);
        plan.setCreator("8"); plan.setUpdater("8"); assertEquals(1, planMapper.insert(plan));

        CutoverApprovalInstanceDO approval = new CutoverApprovalInstanceDO(); approval.setId(approvalId);
        approval.setTenantId(tenantId); approval.setTaskId(taskId); approval.setProjectId(task.getProjectId());
        approval.setPlanRevisionId(planId); approval.setPlanRevisionNo(1); approval.setAssessmentId(701L);
        approval.setAssessmentVersion(1); approval.setChecklistId(702L); approval.setChecklistVersion(1);
        approval.setGradeCode("A"); approval.setInitiatorUserId(8L); approval.setInitiatorProjectScopeVersion(30L);
        approval.setSourceSnapshotVersion(1); approval.setSourceSnapshot("{}"); approval.setRouteSnapshot("{}");
        approval.setStatusCode("APPROVED"); approval.setDecisionAt(now); approval.setVersion(4);
        approval.setCreator("8"); approval.setUpdater("8"); assertEquals(1, approvalMapper.insert(approval));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM cut_cutover_collection_evidence WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_cutover_closure_attachment WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_cutover_closure WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task_device_scope WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_approval_instance WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_plan_revision WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM plt_operation_audit WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM plt_idempotency_record WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task WHERE tenant_id=?", tenantId);
        TenantContextHolder.clear();
    }

    @Test
    void createSaveReplayPersistsRootAttachmentsAndPlatformFactsAtomically() {
        SaveCutoverClosureCommand create = command(null, "create-p6", "first", oneAttachment());
        service.save(create);
        service.save(create);
        Long closureId = jdbc.queryForObject(
                "SELECT id FROM cut_cutover_closure WHERE tenant_id=? AND task_id=?", Long.class, tenantId, taskId);
        jdbc.update("""
                INSERT INTO cut_cutover_closure_attachment
                  (id,tenant_id,closure_id,purpose_code,reference_key,artifact_id,file_version_no,file_fact_version,
                   file_scope_version,file_hash,version,creator,create_time,updater,update_time,deleted)
                VALUES (?,?,?,?,?,?,?,?,?,?,0,'8',NOW(3),'8',NOW(3),b'0')
                """, 980001L, tenantId, closureId, "MANUAL_COLLECTION_RESULT", "manual-ref", 599L, 1,
                "{\"artifactVersion\":1,\"referenceVersion\":2,\"availabilityVersion\":3}", 4L,
                "b".repeat(64));
        service.save(command(0, "save-p6", "second", twoAttachments()));

        assertEquals(1, count("SELECT COUNT(*) FROM cut_cutover_closure WHERE tenant_id=? AND task_id=?", tenantId, taskId));
        assertEquals(1, count("SELECT version FROM cut_cutover_closure WHERE tenant_id=? AND task_id=?", tenantId, taskId));
        assertEquals(3, count("SELECT COUNT(*) FROM cut_cutover_closure_attachment WHERE tenant_id=?", tenantId));
        assertEquals(1, count("SELECT COUNT(*) FROM cut_cutover_closure_attachment WHERE tenant_id=? AND purpose_code='MANUAL_COLLECTION_RESULT'", tenantId));
        assertEquals(2, count("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? AND status='COMPLETED'", tenantId));
        assertEquals(2, count("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=?", tenantId));
    }

    @Test
    void collectionCallbackAndManualFallbackPersistPositiveClosureFacts() {
        Long closureId = prepareDraftWithDevice("create-collection");

        service.requestCollection(new RequestClosureCollectionCommand(tenantId, 8L, taskId, 7, closureId, 0,
                deviceId, CollectionStage.POST_COLLECTION, new SavedCredential(71L, 3L),
                "post-check", 2L, "collect-ok", "corr-collect-ok"));
        String acceptedTaskId = jdbc.queryForObject("""
                SELECT collection_task_id FROM cut_cutover_collection_evidence
                 WHERE tenant_id=? AND closure_id=? AND evidence_type_code='DISPATCH_ACCEPTED'
                """, String.class, tenantId, closureId);
        service.handleCollectionCallback(new HandleClosureCollectionCallbackCommand(tenantId, taskId, closureId,
                deviceId, CollectionStage.POST_COLLECTION, "callback-ok", acceptedTaskId, true,
                "result-ref", "result-v1", LocalDateTime.of(2026, 9, 2, 8, 1), "corr-callback-ok"));
        service.handleCollectionCallback(new HandleClosureCollectionCallbackCommand(tenantId, taskId, closureId,
                deviceId, CollectionStage.POST_COLLECTION, "callback-ok", acceptedTaskId, true,
                "result-ref", "result-v1", LocalDateTime.of(2026, 9, 2, 8, 1), "corr-callback-replay"));

        collections.nextDispatch(DispatchOutcome.FAILED, "OWNER_REJECTED");
        service.requestCollection(new RequestClosureCollectionCommand(tenantId, 8L, taskId, 7, closureId, 2,
                deviceId, CollectionStage.TEST, new SavedCredential(71L, 3L),
                "test-check", 2L, "collect-failed", "corr-collect-failed"));
        String failedTaskId = jdbc.queryForObject("""
                SELECT collection_task_id FROM cut_cutover_collection_evidence
                 WHERE tenant_id=? AND closure_id=? AND evidence_type_code='DISPATCH_FAILED'
                """, String.class, tenantId, closureId);
        service.linkManualResult(new LinkClosureManualResultCommand(tenantId, 8L, taskId, 7, closureId, 3,
                failedTaskId, deviceId, CollectionStage.TEST,
                file(AttachmentPurpose.MANUAL_COLLECTION_RESULT, 503L, "ref-manual"),
                "manual-result", "corr-manual-result"));
        service.linkManualResult(new LinkClosureManualResultCommand(tenantId, 8L, taskId, 7, closureId, 3,
                failedTaskId, deviceId, CollectionStage.TEST,
                file(AttachmentPurpose.MANUAL_COLLECTION_RESULT, 503L, "ref-manual"),
                "manual-result", "corr-manual-replay"));

        assertEquals(4, count("SELECT version FROM cut_cutover_closure WHERE tenant_id=? AND id=?", tenantId, closureId));
        assertEquals(4, count("SELECT COUNT(*) FROM cut_cutover_collection_evidence WHERE tenant_id=?", tenantId));
        assertEquals(1, count("SELECT COUNT(*) FROM cut_cutover_collection_evidence WHERE tenant_id=? AND evidence_type_code='CALLBACK_SUCCEEDED'", tenantId));
        assertEquals(1, count("SELECT COUNT(*) FROM cut_cutover_collection_evidence WHERE tenant_id=? AND evidence_type_code='MANUAL_UPLOAD' AND original_failed_collection_task_id=?", tenantId, failedTaskId));
        assertEquals(1, count("SELECT COUNT(*) FROM cut_cutover_closure_attachment WHERE tenant_id=? AND purpose_code='MANUAL_COLLECTION_RESULT'", tenantId));
        assertEquals(5, count("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=?", tenantId));
    }

    @Test
    void retriesSameIntentAfterLocalProjectionFailureWithoutCreatingSecondExternalTask() {
        Long closureId = prepareDraftWithDevice("create-recovery");
        CollectionIntentIdentity identity = new CollectionIntentIdentity(tenantId, taskId, closureId, deviceId,
                CollectionStage.PRE_CHECK, "collect-recovery");
        CollectionRequest externalRequest = new CollectionRequest(identity, 8L, projectId,
                new SavedCredential(71L, 3L), "pre-check", 2L, "corr-collect-recovery");
        var externalFact = collections.request(externalRequest);
        jdbc.update("""
                INSERT INTO cut_cutover_collection_evidence
                  (id,tenant_id,closure_id,task_id,project_id,device_id,collection_stage_code,evidence_type_code,
                   collection_task_id,occurred_at,recorded_by,creator,create_time,deleted)
                VALUES (?,?,?,?,?,?,'PRE_CHECK','DISPATCH_FAILED',?,NOW(3),8,'8',NOW(3),b'0')
                """, 993_000_000_000L + Math.floorMod(deviceId, 1_000_000L), tenantId, closureId, taskId,
                projectId, deviceId, externalFact.collectionTaskId());
        RequestClosureCollectionCommand command = new RequestClosureCollectionCommand(tenantId, 8L, taskId, 7,
                closureId, 0, deviceId, CollectionStage.PRE_CHECK, new SavedCredential(71L, 3L),
                "pre-check", 2L, "collect-recovery", "corr-collect-recovery");

        assertThrows(RuntimeException.class, () -> service.requestCollection(command));
        assertEquals(0, count("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? AND idempotency_key='collect-recovery'", tenantId));
        assertEquals(0, count("SELECT version FROM cut_cutover_closure WHERE tenant_id=? AND id=?", tenantId, closureId));
        jdbc.update("DELETE FROM cut_cutover_collection_evidence WHERE tenant_id=? AND collection_task_id=?",
                tenantId, externalFact.collectionTaskId());

        service.requestCollection(command);

        assertEquals(1, count("SELECT COUNT(*) FROM cut_cutover_collection_evidence WHERE tenant_id=? AND collection_task_id=? AND evidence_type_code='DISPATCH_ACCEPTED'", tenantId, externalFact.collectionTaskId()));
        assertEquals(1, count("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? AND idempotency_key='collect-recovery' AND status='COMPLETED'", tenantId));
        assertEquals(1, count("SELECT version FROM cut_cutover_closure WHERE tenant_id=? AND id=?", tenantId, closureId));
    }

    private Long prepareDraftWithDevice(String idempotencyKey) {
        service.save(command(null, idempotencyKey, "draft", oneAttachment()));
        Long closureId = jdbc.queryForObject(
                "SELECT id FROM cut_cutover_closure WHERE tenant_id=? AND task_id=?", Long.class, tenantId, taskId);
        jdbc.update("""
                INSERT INTO cut_task_device_scope
                  (id,tenant_id,cutover_task_id,project_id,device_id,serial_number_snapshot,
                   project_assignment_version,active_marker,version,creator,create_time,updater,update_time,deleted)
                VALUES (?,?,?,?,?,'SN-P6-1',1,1,0,'8',NOW(3),'8',NOW(3),b'0')
                """, 992_000_000_000L + Math.floorMod(deviceId, 1_000_000L), tenantId, taskId, projectId, deviceId);
        return closureId;
    }

    private SaveCutoverClosureCommand command(Integer version, String key, String legacy,
                                               List<AttachmentInput> attachments) {
        return new SaveCutoverClosureCommand(tenantId, 8L, taskId, 7, version,
                new ClosureContent(true, null, true, null, true, null, false, null, null,
                        legacy, null, attachments), key, "corr-" + key);
    }

    private static List<AttachmentInput> oneAttachment() {
        return List.of(file(AttachmentPurpose.POST_COLLECTION_CHECKLIST, 501L, "ref-check"));
    }

    private static List<AttachmentInput> twoAttachments() {
        return List.of(file(AttachmentPurpose.POST_COLLECTION_CHECKLIST, 501L, "ref-check"),
                file(AttachmentPurpose.IMPLEMENTATION_COMMITMENT, 502L, "ref-commit"));
    }

    private static AttachmentInput file(AttachmentPurpose purpose, Long artifactId, String reference) {
        return new AttachmentInput(purpose, artifactId, 1, reference,
                new FileFactVersion(1, 2, 3), 4L, "a".repeat(64));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    @SpringBootConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan({"cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC); }
        @Bean CutoverProjectScopePort projectScopePort() {
            return new CutoverProjectScopePort() {
                @Override public ProjectScopeFact inspect(Long actorId, Long projectId, String action) {
                    return new ProjectScopeFact(projectId, 30L, true);
                }
                @Override public ProjectScopeFact lockAndRevalidate(Long actorId, Long projectId, String action,
                                                                     long expectedProjectScopeVersion) {
                    return new ProjectScopeFact(projectId, 30L, true);
                }
                @Override public java.util.Set<Long> resolveAllCurrent(Long actorId, String action) {
                    return java.util.Set.of();
                }
            };
        }
        @Bean CutoverClosureFilePort filePort() { return new CutoverClosureControlledPorts.Files(); }
        @Bean CutoverClosureControlledPorts.Collections collectionPort(Clock clock) {
            return new CutoverClosureControlledPorts.Collections(clock);
        }
        @Bean CutoverClosureApplicationService service(CutoverTaskMapper taskMapper,
                                                        CutoverApprovalInstanceMapper approvalMapper,
                                                        CutoverPlanRevisionMapper planMapper,
                                                        CutoverClosureMapper closureMapper,
                                                        CutoverClosureAttachmentMapper attachmentMapper,
                                                        CutoverCollectionEvidenceMapper evidenceMapper,
                                                        CutoverTaskDeviceScopeMapper deviceScopeMapper,
                                                        CutoverProjectScopePort projectScopePort,
                                                        CutoverClosureFilePort filePort,
                                                        CutoverClosureCollectionPort collectionPort,
                                                        PlatformCommandExecutionApi platform, Clock clock) {
            return new CutoverClosureApplicationService(taskMapper, approvalMapper, planMapper, closureMapper,
                    attachmentMapper, evidenceMapper, deviceScopeMapper, projectScopePort, filePort,
                    collectionPort, platform, clock);
        }
    }
}
