package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistBindingRuleRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverChecklistItemDefinitionRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistItemResultMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistBindingRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverChecklistItemDefinitionRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.GenerateChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SaveChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SelectManualResultCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.command.SubmitChecklistCommand;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverCollectionPort;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.result.ChecklistItemCommandResult;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CutoverChecklistPositiveLoopMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CutoverChecklistPositiveLoopMySqlTest {

    private static final long ACTOR_ID = 8L;
    private static final long PROJECT_ID = 10L;
    private static final long DEVICE_ID = 400L;

    @Resource JdbcTemplate jdbc;
    @Resource CutoverTaskMapper taskMapper;
    @Resource CutoverTaskDeviceScopeMapper deviceMapper;
    @Resource CutoverAssessmentMapper assessmentMapper;
    @Resource CutoverConfigurationRevisionMapper configurationMapper;
    @Resource CutoverChecklistItemDefinitionRevisionMapper definitionMapper;
    @Resource CutoverChecklistBindingRuleRevisionMapper ruleMapper;
    @Resource CutoverChecklistApplicationService service;

    private long tenantId;
    private long taskId;
    private long assessmentId;
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
        tenantId = 997_100_000_000L + suffix;
        taskId = 997_200_000_000L + suffix;
        assessmentId = 997_300_000_000L + suffix;
        configurationId = 997_400_000_000L + suffix;
        TenantContextHolder.setTenantId(tenantId);
        insertConfiguration();
        insertP3Task();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM cut_cutover_checklist_item_result WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_cutover_checklist_item WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_cutover_checklist WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task_stage_history WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task_device_scope WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_assessment WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM plt_outbox_event WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM plt_operation_audit WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM plt_idempotency_record WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_cutover_checklist_binding_rule_revision WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_cutover_checklist_item_definition_revision WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_cutover_configuration_revision WHERE tenant_id=?", tenantId);
        TenantContextHolder.clear();
    }

    @Test
    void completesP3GenerateDirectManualAndSubmitIntoP4WithRealTransactionAndMyBatis() {
        ChecklistCommandResult generated = service.generate(new GenerateChecklistCommand(
                tenantId, ACTOR_ID, taskId, 2, 1, 7L, Map.of(), "generate-loop", "corr-generate-loop"));
        ChecklistCommandResult saved = service.save(new SaveChecklistCommand(
                tenantId, ACTOR_ID, taskId, 2, generated.checklistId(), 0, 7L,
                List.of(new SaveChecklistCommand.DirectAnswer("SYS-IP", "{\"value\":\"10.0.0.1\"}"))));
        CutoverChecklistFilePort.FileFactVersion fileVersion =
                new CutoverChecklistFilePort.FileFactVersion(3, 4, 5);
        ChecklistItemCommandResult manual = service.selectManual(new SelectManualResultCommand(
                tenantId, ACTOR_ID, taskId, 2, generated.checklistId(), saved.checklistFactVersion(), 7L,
                "SYS-EVIDENCE", new CutoverChecklistFilePort.FileHandle(90L, 2, "ref-90", fileVersion, 7L),
                "现场回执"));
        SubmitChecklistCommand submit = new SubmitChecklistCommand(tenantId, ACTOR_ID, taskId, 2, 1,
                generated.checklistId(), manual.checklistVersion(), 7L, "submit-loop", "corr-submit-loop");

        ChecklistCommandResult submitted = service.submit(submit);
        ChecklistCommandResult replayed = service.submit(submit);

        assertEquals("SUBMITTED", submitted.checklistStatus());
        assertEquals("P4", submitted.taskStage());
        assertTrue(replayed.replayed());
        assertEquals("PLAN_DRAFTING", text("SELECT task_status FROM cut_task WHERE tenant_id=? AND id=?", tenantId, taskId));
        assertEquals(3, number("SELECT version FROM cut_task WHERE tenant_id=? AND id=?", tenantId, taskId));
        assertEquals(2, number("SELECT COUNT(*) FROM cut_cutover_checklist_item WHERE tenant_id=? AND checklist_id=?", tenantId, generated.checklistId()));
        assertEquals(2, number("SELECT COUNT(*) FROM cut_cutover_checklist_item_result r "
                + "JOIN cut_cutover_checklist_item i ON i.id=r.checklist_item_id AND i.tenant_id=r.tenant_id "
                + "WHERE r.tenant_id=? AND i.checklist_id=? AND r.selection_ended_at IS NULL",
                tenantId, generated.checklistId()));
        assertEquals(1, number("SELECT COUNT(*) FROM cut_cutover_checklist_item_result WHERE tenant_id=? AND result_source_code='DIRECT'", tenantId));
        assertEquals(1, number("SELECT COUNT(*) FROM cut_cutover_checklist_item_result WHERE tenant_id=? AND result_source_code='MANUAL' AND manual_evidence_file_reference='ref-90'", tenantId));
        assertEquals(1, number("SELECT COUNT(*) FROM cut_task_stage_history WHERE tenant_id=? AND trigger_type='P3_CHECKLIST_SUBMITTED'", tenantId));
        assertEquals(0, number("SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=? AND event_type='CutoverChecklistItemResultLinked'", tenantId));
        assertEquals(2, number("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? AND status='COMPLETED'", tenantId));
        assertEquals(2, number("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=?", tenantId));
    }

    private void insertConfiguration() {
        CutoverConfigurationRevisionDO revision = new CutoverConfigurationRevisionDO();
        revision.setId(configurationId); revision.setTenantId(tenantId); revision.setConfigurationCode("CUT-CONFIG");
        revision.setConfigurationName("CUT-003受控正向配置"); revision.setRevisionNo(1);
        revision.setStatusCode("PUBLISHED"); revision.setEffectiveFrom(LocalDateTime.of(2026, 8, 1, 0, 0));
        revision.setDictionarySnapshot("{}"); revision.setDimensionDefinitionSnapshot("[]");
        revision.setPlanTemplateSectionSnapshot("[]"); revision.setValidationResultSnapshot("[]");
        revision.setPublishedBy(ACTOR_ID); revision.setPublishedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        revision.setVersion(0); revision.setCreator("8"); revision.setUpdater("8");
        assertEquals(1, configurationMapper.insert(revision));

        insertDefinition(configurationId + 1, "SYS-IP", "管理地址", "DIRECT", 10);
        insertDefinition(configurationId + 2, "SYS-EVIDENCE", "人工证据", "MANUAL", 20);
        insertRule(configurationId + 101, configurationId + 1, "RULE-IP", 100);
        insertRule(configurationId + 102, configurationId + 2, "RULE-EVIDENCE", 90);
    }

    private void insertDefinition(long id, String key, String name, String mode, int sort) {
        CutoverChecklistItemDefinitionRevisionDO row = new CutoverChecklistItemDefinitionRevisionDO();
        row.setId(id); row.setTenantId(tenantId); row.setConfigurationRevisionId(configurationId);
        row.setStableItemKey(key); row.setItemDefinitionVersion(1); row.setItemTypeCode("BUSINESS_SURVEY");
        row.setItemName(name); row.setInterfaceFormatCode("INPUT"); row.setInterfaceSchema("{\"type\":\"string\"}");
        row.setFeedbackFormatCode("TEXT"); row.setRequiredFlag(true); row.setWorkModeCode(mode);
        row.setStatusCode("ENABLED"); row.setSortOrder(sort); row.setVersion(0); row.setCreator("8"); row.setUpdater("8");
        assertEquals(1, definitionMapper.insert(row));
    }

    private void insertRule(long id, long definitionId, String key, int priority) {
        CutoverChecklistBindingRuleRevisionDO row = new CutoverChecklistBindingRuleRevisionDO();
        row.setId(id); row.setTenantId(tenantId); row.setConfigurationRevisionId(configurationId);
        row.setStableRuleKey(key); row.setItemDefinitionId(definitionId); row.setItemDefinitionVersion(1);
        row.setDimensionConditionSnapshot("{\"CUTOVER_TYPE\":[\"配置变更\"],\"DEVICE_TYPE\":[\"ROUTER\"]}");
        row.setPriority(priority); row.setRequiredResult(true); row.setStatusCode("ENABLED"); row.setVersion(0);
        row.setCreator("8"); row.setUpdater("8");
        assertEquals(1, ruleMapper.insert(row));
    }

    private void insertP3Task() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 2, 0);
        CutoverTaskDO task = new CutoverTaskDO();
        task.setId(taskId); task.setTenantId(tenantId); task.setProjectId(PROJECT_ID); task.setTaskNo("CUT-P3-" + taskId);
        task.setTaskName("P3受控正向闭环"); task.setBackground("CUT-003真实MySQL闭环");
        task.setCutoverType("配置变更"); task.setNetworkMode("普通双机"); task.setScheduledTime(now);
        task.setTaskOrigin("NEW_PLATFORM"); task.setIntakeSourceType("SELF_CREATED");
        task.setConfigurationRevisionId(configurationId); task.setConfigurationCode("CUT-CONFIG");
        task.setConfigurationRevisionNo(1); task.setCurrentStage("P3"); task.setTaskStatus("SURVEYING");
        task.setOwnerUserId(ACTOR_ID); task.setCustomerId(200L); task.setImplementationReadinessSnapshotId(600L);
        task.setImplementationReadinessSnapshotVersion(4L); task.setProjectScopeVersion(7L);
        task.setProjectContextSnapshot("{}"); task.setDeviceScopeWatermark("{}");
        task.setCustomerContextSnapshot("{}"); task.setReadinessContextSnapshot("{}");
        task.setManualGrade("A"); task.setCurrentAssessmentId(assessmentId); task.setVersion(2);
        task.setCreator("8"); task.setUpdater("8"); task.setCreateTime(now); task.setUpdateTime(now);
        assertEquals(1, taskMapper.insert(task));

        CutoverTaskDeviceScopeDO device = new CutoverTaskDeviceScopeDO();
        device.setId(taskId + 1); device.setTenantId(tenantId); device.setCutoverTaskId(taskId);
        device.setProjectId(PROJECT_ID); device.setDeviceId(DEVICE_ID); device.setSerialNumberSnapshot("SN-400");
        device.setProjectAssignmentVersion(9L); device.setDeviceTypeCodeSnapshot("ROUTER");
        device.setDeviceTypeSourceVersionSnapshot("pt-v1"); device.setActiveMarker(1); device.setVersion(0);
        device.setCreator("8"); device.setUpdater("8");
        assertEquals(1, deviceMapper.insert(device));

        CutoverAssessmentDO assessment = new CutoverAssessmentDO();
        assessment.setId(assessmentId); assessment.setTenantId(tenantId); assessment.setCutoverTaskId(taskId);
        assessment.setAssessmentVersion(1); assessment.setAssessmentStatus("SUBMITTED");
        assessment.setQuestionnaireTemplateCode("CUT_P2_MANUAL_ASSESSMENT");
        assessment.setQuestionnaireTemplateVersion(1L);
        assessment.setAnswerSnapshot("{\"businessImportanceLevel\":\"HIGH\","
                + "\"operationComplexityLevel\":\"MEDIUM\",\"hiddenRiskLevel\":\"LOW\","
                + "\"sparePartApplied\":true}");
        assessment.setContextSnapshot("{\"implementationReadiness\":{\"decision\":\"READY\","
                + "\"unmetCodes\":[]},\"customerServiceLevel\":{\"status\":\"AVAILABLE\","
                + "\"serviceLevelCode\":\"GOLD\"}}");
        assessment.setManualGrade("A"); assessment.setSimpleFlow(false);
        assessment.setSubmittedBy(ACTOR_ID); assessment.setSubmittedAt(now); assessment.setCurrentMarker(1);
        assessment.setVersion(1); assessment.setCreator("8"); assessment.setUpdater("8");
        assertEquals(1, assessmentMapper.insert(assessment));
    }

    private int number(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    private String text(String sql, Object... args) { return jdbc.queryForObject(sql, String.class, args); }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    @SpringBootConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan({"cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-08-31T02:00:00Z"), ZoneOffset.UTC); }
        @Bean CutoverProjectScopePort projectScopePort() {
            return new CutoverProjectScopePort() {
                @Override public ProjectScopeFact inspect(Long actorId, Long projectId, String action) {
                    return new ProjectScopeFact(projectId, 7L, true);
                }
                @Override public ProjectScopeFact lockAndRevalidate(Long actorId, Long projectId, String action,
                                                                     long expectedProjectScopeVersion) {
                    return new ProjectScopeFact(projectId, 7L, true);
                }
                @Override public Set<Long> resolveAllCurrent(Long actorId, String action) { return Set.of(PROJECT_ID); }
            };
        }
        @Bean CutoverChecklistFilePort filePort() {
            return (tenantId, actorId, projectId, checklistItemId, expectedScopeVersion, handle) ->
                    new CutoverChecklistFilePort.FileFact(handle.artifactId(), handle.versionNo(),
                            handle.referenceKey(), handle.fileFactVersion(), handle.scopeVersion(), "a".repeat(64));
        }
        @Bean CutoverCollectionPort collectionPort() {
            return new CutoverCollectionPort() {
                @Override public RequestReceipt request(Request request) { throw new UnsupportedOperationException(); }
                @Override public CollectionFact inspect(Inspection inspection) { throw new UnsupportedOperationException(); }
            };
        }
        @Bean CutoverChecklistConfigurationQueryService configurationService(
                CutoverConfigurationRevisionMapper revisionMapper,
                CutoverChecklistItemDefinitionRevisionMapper itemMapper,
                CutoverChecklistBindingRuleRevisionMapper ruleMapper) {
            return new CutoverChecklistConfigurationQueryService(revisionMapper, itemMapper, ruleMapper);
        }
        @Bean CutoverChecklistApplicationService service(CutoverTaskMapper taskMapper,
                                                          CutoverTaskDeviceScopeMapper deviceMapper,
                                                          CutoverAssessmentMapper assessmentMapper,
                                                          CutoverTaskStageHistoryMapper historyMapper,
                                                          CutoverChecklistMapper checklistMapper,
                                                          CutoverChecklistItemMapper itemMapper,
                                                          CutoverChecklistItemResultMapper resultMapper,
                                                          CutoverChecklistConfigurationQueryService configurationService,
                                                          CutoverProjectScopePort projectScopePort,
                                                          CutoverCollectionPort collectionPort,
                                                          CutoverChecklistFilePort filePort,
                                                          PlatformCommandExecutionApi platform, Clock clock) {
            return new CutoverChecklistApplicationService(taskMapper, deviceMapper, assessmentMapper, historyMapper,
                    checklistMapper, itemMapper, resultMapper, configurationService, new CutoverChecklistMatcher(),
                    projectScopePort, collectionPort, filePort, platform, clock);
        }
    }
}
