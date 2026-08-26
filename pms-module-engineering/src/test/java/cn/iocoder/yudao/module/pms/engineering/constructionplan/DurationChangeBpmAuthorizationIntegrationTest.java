package cn.iocoder.yudao.module.pms.engineering.constructionplan;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCancelReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRejectReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.event.BpmProcessInstanceEventPublisher;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.BpmProcessInstanceEventListener;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.message.BpmMessageService;
import cn.iocoder.yudao.module.bpm.service.comment.BpmCommentService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceCopyService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceServiceImpl;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskServiceImpl;
import cn.iocoder.yudao.module.bpm.service.definition.BpmFormService;
import cn.iocoder.yudao.module.bpm.dal.redis.BpmProcessIdRedisDAO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.DurationChangeBpmAuthorizationGuard;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.DurationChangeBpmListener;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.DurationChangeBpmResultService;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.DurationChangeProperties;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.flowable.spring.boot.ProcessEngineAutoConfiguration;
import org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = DurationChangeBpmAuthorizationIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DurationChangeBpmAuthorizationIntegrationTest {

    private static final String PROCESS_KEY = "pms-sol-duration-change-it";
    private static final long APPLICANT = 9L;
    private static final long APPROVER = 19L;

    @Resource BpmTaskService bpmTaskService;
    @Resource BpmProcessInstanceService processInstanceService;
    @Resource RepositoryService repositoryService;
    @Resource RuntimeService runtimeService;
    @Resource TaskService flowableTaskService;
    @Resource IdentityService identityService;
    @Resource JdbcTemplate jdbcTemplate;
    @Resource PermissionApi permissionApi;
    @Resource ProjectScopeApi projectScopeApi;
    @Resource ProjectParticipantFactApi participantFactApi;
    @Resource BpmModelService modelService;
    @Resource BpmProcessDefinitionService processDefinitionService;

    private long projectId;
    private long planId;
    private long baseRevisionId;
    private long candidateRevisionId;
    private long changeId;
    private String deploymentId;
    private String processInstanceId;
    private BpmnModel bpmnModel;

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
        registry.add("spring.main.web-application-type", () -> "none");
        registry.add("flowable.database-schema-update", () -> "true");
        registry.add("flowable.async-executor-activate", () -> "false");
        registry.add("flowable.idm.enabled", () -> "false");
        registry.add("flowable.cmmn.enabled", () -> "false");
        registry.add("flowable.dmn.enabled", () -> "false");
        registry.add("flowable.app.enabled", () -> "false");
        registry.add("flowable.eventregistry.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.engineering");
        registry.add("yudao.tenant.enable", () -> "false");
        registry.add("pms.sol.duration-change.process-definition-key", () -> PROCESS_KEY);
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        long seed = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        planId = 978_100_000_000L + seed * 10;
        projectId = planId + 1;
        baseRevisionId = planId + 2;
        candidateRevisionId = planId + 3;
        changeId = planId + 4;
        bpmnModel = model();
        deploymentId = repositoryService.createDeployment()
                .name(PROCESS_KEY)
                .tenantId("0")
                .addBpmnModel(PROCESS_KEY + ".bpmn20.xml", bpmnModel)
                .deploy().getId();
        reset(permissionApi, projectScopeApi, participantFactApi, modelService, processDefinitionService);
        when(modelService.getBpmnModelByDefinitionId(anyString())).thenReturn(bpmnModel);
        when(processDefinitionService.getProcessDefinitionInfo(anyString()))
                .thenReturn(new BpmProcessDefinitionInfoDO().setAllowCancelRunningProcess(true));
        stubAuthorization(Failure.NONE, Command.APPROVE);
        createPendingFacts();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        if (processInstanceId != null && runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult() != null) {
            runtimeService.setVariable(processInstanceId,
                    BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS, 99);
            runtimeService.deleteProcessInstance(processInstanceId, "test-cleanup");
        }
        if (deploymentId != null) {
            repositoryService.deleteDeployment(deploymentId, true);
        }
        jdbcTemplate.update("UPDATE sol_construction_plan SET current_duration_revision_id=NULL, "
                + "pending_change_id=NULL, plan_recalculation_source_revision_id=NULL WHERE tenant_id=0 AND id=?", planId);
        jdbcTemplate.update("DELETE FROM sol_construction_plan_change WHERE tenant_id=0 AND plan_id=?", planId);
        jdbcTemplate.update("DELETE FROM sol_construction_plan_revision WHERE tenant_id=0 AND plan_id=?", planId);
        jdbcTemplate.update("DELETE FROM sol_construction_plan WHERE tenant_id=0 AND id=?", planId);
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE tenant_id=0 "
                + "AND operation_code='DURATION_CHANGE_BPM_RESULT' AND aggregate_key=?", String.valueOf(changeId));
    }

    @ParameterizedTest
    @EnumSource(Command.class)
    void legalTerminalCommandCommitsBpmSolPointersAndOneAudit(Command command) {
        stubAuthorization(Failure.NONE, command);

        invoke(command);

        assertNull(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult());
        assertEquals(command.changeStatus(), value("SELECT status_code FROM sol_construction_plan_change WHERE id=?", changeId));
        assertEquals(command == Command.APPROVE ? candidateRevisionId : baseRevisionId,
                number("SELECT current_duration_revision_id FROM sol_construction_plan WHERE id=?", planId));
        assertNull(value("SELECT pending_change_id FROM sol_construction_plan WHERE id=?", planId));
        assertEquals(command == Command.APPROVE ? ConstructionPlanDO.RECALCULATION_PENDING : "RECALCULATED",
                value("SELECT plan_recalculation_status_code FROM sol_construction_plan WHERE id=?", planId));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND operation_code='DURATION_CHANGE_BPM_RESULT' "
                + "AND aggregate_key=? AND result_code='SUCCESS'", Long.class, String.valueOf(changeId)));
    }

    @ParameterizedTest
    @EnumSource(Command.class)
    void missingSolPermissionRollsBackBpmAndSol(Command command) {
        assertRejectedWithoutSideEffects(command, Failure.PERMISSION);
    }

    @ParameterizedTest
    @EnumSource(Command.class)
    void missingProjectManageRollsBackBpmAndSol(Command command) {
        assertRejectedWithoutSideEffects(command, Failure.SCOPE);
    }

    @ParameterizedTest
    @EnumSource(Command.class)
    void staleCurrentRoleRollsBackBpmAndSol(Command command) {
        assertRejectedWithoutSideEffects(command, Failure.ROLE);
    }

    private void assertRejectedWithoutSideEffects(Command command, Failure failure) {
        stubAuthorization(failure, command);

        assertThrows(RuntimeException.class, () -> invoke(command));

        assertNotNull(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult());
        assertNotNull(flowableTaskService.createTaskQuery().processInstanceId(processInstanceId).singleResult());
        assertEquals(ConstructionPlanChangeDO.STATUS_PENDING_APPROVAL,
                value("SELECT status_code FROM sol_construction_plan_change WHERE id=?", changeId));
        assertEquals(baseRevisionId,
                number("SELECT current_duration_revision_id FROM sol_construction_plan WHERE id=?", planId));
        assertEquals(changeId, number("SELECT pending_change_id FROM sol_construction_plan WHERE id=?", planId));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND operation_code='DURATION_CHANGE_BPM_RESULT' "
                + "AND aggregate_key=? AND result_code='SUCCESS'", Long.class, String.valueOf(changeId)));
    }

    private void invoke(Command command) {
        long actor = command == Command.CANCEL ? APPLICANT : APPROVER;
        login(actor);
        String taskId = flowableTaskService.createTaskQuery().processInstanceId(processInstanceId)
                .singleResult().getId();
        if (command == Command.APPROVE) {
            bpmTaskService.approveTask(actor, new BpmTaskApproveReqVO().setId(taskId).setReason("同意"));
        } else if (command == Command.REJECT) {
            bpmTaskService.rejectTask(actor, new BpmTaskRejectReqVO().setId(taskId).setReason("驳回"));
        } else {
            processInstanceService.cancelProcessInstanceByStartUser(actor,
                    new BpmProcessInstanceCancelReqVO().setId(processInstanceId).setReason("撤回"));
        }
    }

    private void stubAuthorization(Failure failure, Command command) {
        reset(permissionApi, projectScopeApi, participantFactApi);
        when(permissionApi.hasAnyPermissions(anyLong(), anyString())).thenReturn(failure != Failure.PERMISSION);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(failure == Failure.SCOPE
                ? new ProjectScopeResult(projectId, 7L, Set.of(), Set.of())
                : new ProjectScopeResult(projectId, 7L, Set.of(projectId), Set.of()));
        long actor = command == Command.CANCEL ? APPLICANT : APPROVER;
        Set<String> roles = command == Command.CANCEL
                ? Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)
                : Set.of(ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1);
        ProjectParticipantFact fact = new ProjectParticipantFact(projectId, actor, roles, "PRIMARY",
                "ACTIVE", "S1", 3, 3L);
        when(participantFactApi.inspect(any())).thenReturn(failure == Failure.ROLE ? null : fact);
        when(participantFactApi.lockAndRevalidate(any())).thenReturn(fact);
    }

    private void createPendingFacts() {
        identityService.setAuthenticatedUserId(String.valueOf(APPLICANT));
        ProcessInstance instance = runtimeService.startProcessInstanceByKeyAndTenantId(
                PROCESS_KEY, "duration:" + changeId,
                Map.of(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS, 1,
                        "projectId", projectId, "approverUserId", APPROVER), "0");
        identityService.setAuthenticatedUserId(null);
        processInstanceId = instance.getId();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO sol_construction_plan "
                        + "(id,project_id,plan_recalculation_status_code,version,creator,updater,tenant_id) "
                        + "VALUES (?,?, 'RECALCULATED',0,'9','9',0)", planId, projectId);
        jdbcTemplate.update("INSERT INTO sol_construction_plan_revision "
                        + "(id,plan_id,revision_no,calculation_basis_code,start_date,end_date,duration_days,"
                        + "frozen_at,effective_at,created_by,created_at,version,tenant_id) "
                        + "VALUES (?,?,1,'DATE_RANGE','2026-09-01','2026-09-05',5,?,?,9,?,0,0)",
                baseRevisionId, planId, now, now, now);
        jdbcTemplate.update("INSERT INTO sol_construction_plan_revision "
                        + "(id,plan_id,revision_no,calculation_basis_code,start_date,end_date,duration_days,"
                        + "frozen_at,created_by,created_at,version,tenant_id) "
                        + "VALUES (?,?,2,'DATE_RANGE','2026-09-01','2026-09-10',10,?,9,?,0,0)",
                candidateRevisionId, planId, now, now);
        jdbcTemplate.update("INSERT INTO sol_construction_plan_change "
                        + "(id,plan_id,base_revision_id,candidate_revision_id,status_code,reason_type_code,"
                        + "reason_detail,customer_evidence_required,process_definition_key,process_instance_id,"
                        + "submitted_at,applicant_user_id,approver_user_id,created_at,version,tenant_id) "
                        + "VALUES (?,?,?,?, 'PENDING_APPROVAL','CUSTOMER_DELAY','客户延期',b'0',?,?,?,?,?,?,0,0)",
                changeId, planId, baseRevisionId, candidateRevisionId, PROCESS_KEY, processInstanceId,
                now, APPLICANT, APPROVER, now);
        jdbcTemplate.update("UPDATE sol_construction_plan_revision SET source_change_id=? WHERE id=?",
                changeId, candidateRevisionId);
        jdbcTemplate.update("UPDATE sol_construction_plan SET current_duration_revision_id=?, pending_change_id=? "
                + "WHERE id=?", baseRevisionId, changeId, planId);
    }

    private Object value(String sql, long id) {
        return jdbcTemplate.queryForObject(sql, Object.class, id);
    }

    private long number(String sql, long id) {
        return ((Number) value(sql, id)).longValue();
    }

    private void login(long userId) {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(userId).setUserType(2),
                new MockHttpServletRequest());
        TenantContextHolder.setTenantId(0L);
    }

    private static BpmnModel model() {
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId(PROCESS_KEY);
        process.setName("项目工期变更审批集成测试");
        StartEvent start = new StartEvent();
        start.setId("start");
        UserTask approve = new UserTask();
        approve.setId("serviceManagerApprove");
        approve.setName("服务经理审批");
        approve.setAssignee(String.valueOf(APPROVER));
        EndEvent end = new EndEvent();
        end.setId("end");
        process.addFlowElement(start);
        process.addFlowElement(approve);
        process.addFlowElement(end);
        process.addFlowElement(new SequenceFlow("start", "serviceManagerApprove"));
        process.addFlowElement(new SequenceFlow("serviceManagerApprove", "end"));
        model.addProcess(process);
        return model;
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    enum Command {
        APPROVE(ConstructionPlanChangeDO.STATUS_APPROVED),
        REJECT(ConstructionPlanChangeDO.STATUS_REJECTED),
        CANCEL(ConstructionPlanChangeDO.STATUS_WITHDRAWN);

        private final String changeStatus;

        Command(String changeStatus) {
            this.changeStatus = changeStatus;
        }

        String changeStatus() {
            return changeStatus;
        }
    }

    enum Failure { NONE, PERMISSION, SCOPE, ROLE }

    @SpringBootConfiguration
    @ImportAutoConfiguration({ProcessEngineAutoConfiguration.class,
            ProcessEngineServicesAutoConfiguration.class})
    @MapperScan({"cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            BpmTaskServiceImpl.class, BpmProcessInstanceServiceImpl.class,
            BpmProcessInstanceEventListener.class, OperationAuditApiImpl.class,
            DurationChangeProperties.class, DurationChangeBpmListener.class,
            DurationChangeBpmAuthorizationGuard.class, DurationChangeBpmResultService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PermissionApi permissionApi() { return mock(PermissionApi.class); }
        @Bean ProjectScopeApi projectScopeApi() { return mock(ProjectScopeApi.class); }
        @Bean ProjectParticipantFactApi participantFactApi() { return mock(ProjectParticipantFactApi.class); }
        @Bean BpmModelService bpmModelService() { return mock(BpmModelService.class); }
        @Bean BpmProcessDefinitionService bpmProcessDefinitionService() {
            return mock(BpmProcessDefinitionService.class);
        }
        @Bean BpmProcessInstanceCopyService bpmProcessInstanceCopyService() {
            return mock(BpmProcessInstanceCopyService.class);
        }
        @Bean BpmCommentService bpmCommentService() { return mock(BpmCommentService.class); }
        @Bean BpmMessageService bpmMessageService() { return mock(BpmMessageService.class); }
        @Bean BpmFormService bpmFormService() { return mock(BpmFormService.class); }
        @Bean AdminUserApi adminUserApi() { return mock(AdminUserApi.class); }
        @Bean DeptApi deptApi() { return mock(DeptApi.class); }
        @Bean BpmTaskCandidateInvoker bpmTaskCandidateInvoker() { return mock(BpmTaskCandidateInvoker.class); }
        @Bean BpmProcessIdRedisDAO bpmProcessIdRedisDAO() { return mock(BpmProcessIdRedisDAO.class); }
        @Bean StringRedisTemplate stringRedisTemplate() { return mock(StringRedisTemplate.class); }
        @Bean BpmProcessInstanceEventPublisher bpmProcessInstanceEventPublisher(
                ApplicationEventPublisher publisher) {
            return new BpmProcessInstanceEventPublisher(publisher);
        }
        @Bean EngineConfigurationConfigurer<SpringProcessEngineConfiguration> processListenerConfigurer(
                BpmProcessInstanceEventListener listener) {
            return configuration -> configuration.setEventListeners(java.util.List.of(listener));
        }
    }
}
