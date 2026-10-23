package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.hutool.core.util.IdUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistRevisionService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.ReviseCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.*;
import cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandExecutor;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.*;
import java.util.function.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 使用真实 Mapper、约束和 Spring 事务验证跨评估/清单/方案回退；外部 Owner 由测试事实替代。 */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CutoverTaskRevisionMySqlTest.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CutoverTaskRevisionMySqlTest {
    @Resource JdbcTemplate jdbc;
    @Resource CutoverTaskRevisionService revisions;
    @Resource CutoverConfigurationService configurationService;
    @Resource cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper configurationMapper;
    @MockitoBean CutoverChecklistRevisionService checklistCopies;
    @MockitoBean CutoverPlanApplicationService plans;
    @MockitoBean CutoverApprovalApplicationService approvals;
    @MockitoBean cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy configurationPolicy;
    @MockitoBean DictDataApi dictionaries;
    @MockitoBean CutoverCommandExecutor configurationCommands;
    private long taskId, assessmentId, checklistId, planId;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        var env = System.getenv(); var database = Objects.requireNonNull(env.get("NPDMS_DB_NAME"));
        if (!database.startsWith("npdms_cut_issues_")) throw new IllegalStateException("必须使用本次独立割接验证库");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + env.get("NPDMS_MYSQL_PORT") + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> env.get("NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> env.get("NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.cutover");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void fixture() {
        TenantContextHolder.setTenantId(1L); reset(checklistCopies);
        taskId = IdUtil.getSnowflakeNextId(); assessmentId = IdUtil.getSnowflakeNextId();
        checklistId = IdUtil.getSnowflakeNextId(); planId = IdUtil.getSnowflakeNextId();
        jdbc.update("""
                INSERT INTO cut_task (id,tenant_id,project_id,task_no,task_name,background,cutover_type_code,task_origin_code,intake_source_type_code,current_stage_code,task_status_code,owner_user_id,project_scope_version,manual_grade_code,current_assessment_id,configuration_revision_id,configuration_code,configuration_revision_no,project_version,customer_fact_status_code,readiness_decision_code,version,creator,updater,create_time,update_time)
                VALUES (?,1,100,?,'修订事务验证','现场调整','CONFIGURATION_CHANGE','NEW_PLATFORM','SELF_CREATED','P4','PLAN_DRAFTING',8,7,'A',?,970000000000442001,'CUTOVER_STANDARD_V1',1,1,'NOT_APPLICABLE','NOT_APPLICABLE',4,'cutover-issues-test','cutover-issues-test',NOW(3),NOW(3))
                """, taskId, "CUT-REV-" + taskId, assessmentId);
        jdbc.update("""
                INSERT INTO cut_assessment (id,tenant_id,task_id,assessment_version,assessment_status_code,questionnaire_template_code,questionnaire_template_version,manual_grade_code,simple_flow_flag,submit_user_id,submit_time,current_marker,business_importance_level,operation_complexity_level,hidden_risk_level,spare_part_applied_flag,intake_source_type_code,readiness_decision_code,project_id,project_version,project_scope_version,version,creator,updater,create_time,update_time)
                VALUES (?,1,?,1,'SUBMITTED','CUT_P2_MANUAL_ASSESSMENT',1,'A',b'0',8,NOW(3),1,'HIGH','MEDIUM','LOW',b'1','SELF_CREATED','NOT_APPLICABLE',100,1,7,0,'cutover-issues-test','cutover-issues-test',NOW(3),NOW(3))
                """, assessmentId, taskId);
        jdbc.update("""
                INSERT INTO cut_cutover_checklist (id,tenant_id,task_id,assessment_id,assessment_version,checklist_version,status_code,input_snapshot_hash,submit_user_id,submit_time,source_task_version,project_scope_version,configuration_revision_id,configuration_code,configuration_revision_no,version,creator,updater,create_time,update_time)
                VALUES (?,1,?,?,1,1,'SUBMITTED',REPEAT('a',64),8,NOW(3),4,7,970000000000442001,'CUTOVER_STANDARD_V1',1,0,'cutover-issues-test','cutover-issues-test',NOW(3),NOW(3))
                """, checklistId, taskId, assessmentId);
        jdbc.update("""
                INSERT INTO cut_plan_revision (id,tenant_id,task_id,revision_no,origin_code,edit_mode_code,grade_code,assessment_id,assessment_version,checklist_id,checklist_version,configuration_revision_id,configuration_code,configuration_revision_no,status_code,current_marker,source_task_version,source_project_id,source_project_version,source_project_scope_version,source_cutover_type_code,version,creator,updater,create_time,update_time)
                VALUES (?,1,?,1,'NEW_PLATFORM','ONLINE_TEMPLATE_STANDARD','A',?,1,?,1,970000000000442001,'CUTOVER_STANDARD_V1',1,'DRAFT',1,4,100,1,7,'CONFIGURATION_CHANGE',0,'cutover-issues-test','cutover-issues-test',NOW(3),NOW(3))
                """, planId, taskId, assessmentId, checklistId);
    }

    @AfterEach void clearContext() { TenantContextHolder.clear(); }

    @Test
    void revisesP2AtomicallyAndPreservesSubmittedFacts() {
        var result = revisions.revise(command("P2"));
        assertEquals("P2", result.currentStage());
        assertEquals("INVALIDATED", value("SELECT assessment_status_code FROM cut_assessment WHERE id=?", assessmentId));
        assertEquals("HIGH", value("SELECT business_importance_level FROM cut_assessment WHERE id=?", assessmentId));
        assertEquals("INVALIDATED", value("SELECT status_code FROM cut_cutover_checklist WHERE id=?", checklistId));
        assertEquals("INVALIDATED", value("SELECT status_code FROM cut_plan_revision WHERE id=?", planId));
        assertNull(value("SELECT manual_grade_code FROM cut_task WHERE id=?", taskId));
        assertEquals("DRAFT", value("SELECT assessment_status_code FROM cut_assessment WHERE task_id=? AND current_marker=1", taskId));
        assertEquals("2", value("SELECT assessment_version FROM cut_assessment WHERE task_id=? AND current_marker=1", taskId));
        assertEquals("1", value("SELECT COUNT(*) FROM cut_task_stage_history WHERE task_id=? AND trigger_type='TASK_REVISION'", taskId));
    }

    @Test
    void rollsBackTaskAndDownstreamInvalidationIfChecklistCopyFails() {
        when(checklistCopies.copyDraft(any())).thenThrow(new IllegalStateException("file owner denied revision reference"));
        assertThrows(IllegalStateException.class, () -> revisions.revise(command("P3")));
        assertEquals("P4", value("SELECT current_stage_code FROM cut_task WHERE id=?", taskId));
        assertEquals("4", value("SELECT version FROM cut_task WHERE id=?", taskId));
        assertEquals("SUBMITTED", value("SELECT status_code FROM cut_cutover_checklist WHERE id=?", checklistId));
        assertEquals("DRAFT", value("SELECT status_code FROM cut_plan_revision WHERE id=?", planId));
        assertEquals("0", value("SELECT COUNT(*) FROM cut_task_stage_history WHERE task_id=?", taskId));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void adoptsPublishedConfigurationWithoutRewritingHistoricalConfigurationReferences() {
        long configurationId = IdUtil.getSnowflakeNextId();
        int revisionNo = jdbc.queryForObject("SELECT MAX(revision_no)+1 FROM cut_cutover_configuration_revision WHERE configuration_code='CUTOVER_STANDARD_V1'", Integer.class);
        // 独立库测试事务模拟正式发布的旧版本停用；整个测试结束回滚，保留匹配矩阵的原始基线。
        jdbc.update("UPDATE cut_cutover_configuration_revision SET status_code='DISABLED',effective_end_time=NOW(3),disable_user_id=8,disable_time=NOW(3) WHERE id=970000000000442001");
        jdbc.update("""
                INSERT INTO cut_cutover_configuration_revision
                (id,tenant_id,configuration_code,configuration_name,revision_no,status_code,effective_start_time,publish_user_id,publish_time,navigation_target_code,validation_valid_flag,current_validation_no,validate_time,validate_user_id,version,creator,updater,create_time,update_time)
                SELECT ?,tenant_id,configuration_code,'独立库修订配置验证',?,'PUBLISHED',NOW(3),8,NOW(3),navigation_target_code,b'1',1,NOW(3),8,0,'cutover-issues-test','cutover-issues-test',NOW(3),NOW(3)
                FROM cut_cutover_configuration_revision WHERE id=970000000000442001
                """, configurationId, revisionNo);
        var selected = configurationMapper.selectForUpdate(new cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query.CutoverConfigurationStorageQuery(1L, configurationId));
        assertNotNull(selected); assertEquals(1L, selected.getTenantId()); assertEquals("PUBLISHED", selected.getStatusCode());
        assertEquals("CUTOVER_STANDARD_V1", selected.getConfigurationCode());
        assertNotNull(selected.getEffectiveFrom());
        assertFalse(selected.getEffectiveFrom().isAfter(java.time.LocalDateTime.now()), () -> "Future configuration start " + selected.getEffectiveFrom() + " versus " + java.time.LocalDateTime.now());
        assertNull(selected.getEffectiveTo());
        var command = ReviseCutoverTaskCommand.builder().tenantId(1L).actorId(8L).taskId(taskId).expectedTaskVersion(4L)
                .expectedProjectScopeVersion(7L).targetStage("P3").configurationRevisionId(configurationId).reason("采用新发布配置")
                .idempotencyKey("revision-config-" + taskId).correlationId("revision-config-" + taskId).build();
        assertEquals("P3", revisions.revise(command).currentStage());
        assertEquals(String.valueOf(configurationId), value("SELECT configuration_revision_id FROM cut_task WHERE id=?", taskId));
        assertEquals("970000000000442001", value("SELECT configuration_revision_id FROM cut_cutover_checklist WHERE id=?", checklistId));
        assertEquals("970000000000442001", value("SELECT configuration_revision_id FROM cut_plan_revision WHERE id=?", planId));
        assertEquals("INVALIDATED", value("SELECT status_code FROM cut_cutover_checklist WHERE id=?", checklistId));
        assertEquals("0", value("SELECT COUNT(*) FROM cut_cutover_checklist WHERE task_id=? AND current_marker=1", taskId));
        verifyNoInteractions(checklistCopies);
    }

    @Test
    void correctedDraftPassesFormalConfigurationValidationUsingItsSourceDictionaryValues() {
        when(dictionaries.getDictDataList(anyString())).thenAnswer(invocation -> jdbc.query("""
                SELECT dict_type,value_code,label FROM cut_configuration_dictionary_value
                WHERE configuration_revision_id=970000000000478001 AND dict_type=? AND deleted=b'0'
                """, (row, number) -> {
            var value = new DictDataRespDTO(); value.setDictType(row.getString(1)); value.setValue(row.getString(2));
            value.setLabel(row.getString(3)); value.setStatus(0); return value;
        }, invocation.getArgument(0, String.class)));
        var validation = configurationService.validate(970000000000478001L);
        assertTrue(validation.isValid(), () -> cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(validation.getErrors()));
        assertEquals("DRAFT", value("SELECT status_code FROM cut_cutover_configuration_revision WHERE id=?", 970000000000478001L));
    }

    private String value(String sql, long id) { return jdbc.queryForObject(sql, String.class, id); }
    private ReviseCutoverTaskCommand command(String target) {
        return ReviseCutoverTaskCommand.builder().tenantId(1L).actorId(8L).taskId(taskId).expectedTaskVersion(4L)
                .expectedProjectScopeVersion(7L).targetStage(target).reason("验证前序修订事务")
                .idempotencyKey("revision-" + taskId).correlationId("revision-" + taskId).build();
    }

    @SpringBootConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan("cn.iocoder.yudao.module.pms.cutover.dal.mysql")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class, CutoverTaskRevisionService.class,
            CutoverConfigurationServiceImpl.class, CutoverConfigurationMetadataStore.class,
            CutoverFieldDefinitionStore.class, CutoverExternalMappingStore.class})
    static class Application {
        @Bean JdbcTemplate jdbcTemplate(javax.sql.DataSource datasource) { return new JdbcTemplate(datasource); }
        @Bean CutoverProjectScopePort scopes() {
            var scopes = mock(CutoverProjectScopePort.class);
            var fact = new CutoverProjectScopePort.ProjectScopeFact(100L, 7L, true);
            when(scopes.inspect(8L, 100L, "ACTION_EDIT")).thenReturn(fact);
            when(scopes.lockAndRevalidate(any())).thenReturn(fact); return scopes;
        }
        @Bean PlatformCommandExecutionApi platformCommands() {
            return new PlatformCommandExecutionApi() {
                public <T> ExecutionResult<T> execute(IdempotencyScope scope, String digest, Class<T> type, Supplier<T> operation, Function<T, SuccessFacts> facts) {
                    return new ExecutionResult<>(Decision.NEW, operation.get());
                }
            };
        }
    }
}
