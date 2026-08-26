package cn.iocoder.yudao.module.pms.engineering.constructionplan;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCancelReqVO;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApiImpl;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRejectReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;
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
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.DurationChangeApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.ConstructionPlanApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.ConstructionPlanChangeFilePolicyProvider;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.SubmitDurationChangeCommand;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.file.FileArtifactApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApiImpl;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApiImpl;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeMetrics;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeProjectionService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
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
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.flowable.spring.boot.ProcessEngineAutoConfiguration;
import org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
@SpringBootTest(classes = DurationChangeBpmMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DurationChangeBpmMySqlIntegrationTest {

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
    @Resource SwitchingProjectScopeApi projectScopeApi;
    @Resource SwitchingProjectParticipantFactApi participantFactApi;
    @Resource DictDataApi dictDataApi;
    @Resource ConfigApi configApi;
    @Resource DurationChangeApplicationService durationChangeService;
    @Resource BpmModelService modelService;
    @Resource BpmProcessDefinitionService processDefinitionService;
    @Resource ProjectTreeProjectionService treeProjectionService;
    @Resource TransactionTemplate transactionTemplate;

    private long projectId;
    private long planId;
    private long baseRevisionId;
    private long candidateRevisionId;
    private long changeId;
    private long rootProjectId;
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
        rootProjectId = planId + 20;
        bpmnModel = model();
        deploymentId = repositoryService.createDeployment()
                .name(PROCESS_KEY)
                .tenantId("0")
                .addBpmnModel(PROCESS_KEY + ".bpmn20.xml", bpmnModel)
                .deploy().getId();
        participantFactApi.useMock();
        projectScopeApi.useMock();
        reset(permissionApi, projectScopeApi.mock(), participantFactApi.mock(), dictDataApi, configApi,
                modelService, processDefinitionService);
        when(modelService.getBpmnModelByDefinitionId(anyString())).thenReturn(bpmnModel);
        when(processDefinitionService.getProcessDefinitionInfo(anyString()))
                .thenReturn(new BpmProcessDefinitionInfoDO()
                        .setModelType(BpmModelTypeEnum.BPMN.getType())
                        .setAllowCancelRunningProcess(true));
        when(processDefinitionService.canUserStartProcessDefinition(any(), anyLong())).thenReturn(true);
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
        jdbcTemplate.update("DELETE FROM proj_project_member_assignment WHERE tenant_id=0 AND project_id IN (?,?)",
                projectId, rootProjectId);
        jdbcTemplate.update("DELETE FROM proj_project_tree_path WHERE tenant_id=0 AND root_project_id=?",
                rootProjectId);
        jdbcTemplate.update("DELETE FROM proj_project_tree_version WHERE tenant_id=0 AND root_project_id=?",
                rootProjectId);
        jdbcTemplate.update("DELETE FROM proj_project WHERE tenant_id=0 AND id=?", projectId);
        jdbcTemplate.update("DELETE FROM proj_project WHERE tenant_id=0 AND id=?", rootProjectId);
        jdbcTemplate.update("DELETE FROM plt_file_reference WHERE tenant_id=0 AND object_id=?",
                String.valueOf(changeId));
        jdbcTemplate.update("DELETE FROM plt_file_version WHERE tenant_id=0 AND artifact_id=?", planId + 5);
        jdbcTemplate.update("DELETE FROM plt_file_artifact WHERE tenant_id=0 AND id=?", planId + 5);
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

    @Test
    void submitCreatesRealBpmWithStandardProjectVariableAndFrozenPendingFacts() {
        prepareRealSubmission();
        login(APPLICANT);

        var response = durationChangeService.submit(new SubmitDurationChangeCommand(
                        planId, changeId, 0, 3, "task9-submit-" + changeId, "a".repeat(64)),
                new ConstructionPlanApplicationService.Actor(0L, APPLICANT,
                        "task9-submit-" + changeId));
        processInstanceId = response.getProcessInstanceId();

        assertNotNull(runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult());
        assertEquals(projectId, ((Number) runtimeService.getVariable(
                processInstanceId, "projectId")).longValue());
        assertEquals(ConstructionPlanChangeDO.STATUS_PENDING_APPROVAL,
                value("SELECT status_code FROM sol_construction_plan_change WHERE id=?", changeId));
        assertEquals(changeId, number("SELECT pending_change_id FROM sol_construction_plan WHERE id=?", planId));
        assertNotNull(value("SELECT frozen_at FROM sol_construction_plan_revision WHERE id=?",
                candidateRevisionId));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND operation_code='DURATION_CHANGE_SUBMIT' "
                + "AND aggregate_key=? AND result_code='SUCCESS'", Long.class, String.valueOf(changeId)));
    }

    @Test
    void realProjectFactsRejectStaleVersionThenAllowSubmission() {
        prepareRealSubmission();
        insertRealProjectFacts(4);
        participantFactApi.useReal();
        login(APPLICANT);

        assertThrows(RuntimeException.class, () -> durationChangeService.submit(
                new SubmitDurationChangeCommand(planId, changeId, 0, 3,
                        "task9-real-project-stale-" + changeId, "a".repeat(64)),
                new ConstructionPlanApplicationService.Actor(0L, APPLICANT,
                        "task9-real-project-stale-" + changeId)));

        assertEquals(ConstructionPlanChangeDO.STATUS_DRAFT,
                value("SELECT status_code FROM sol_construction_plan_change WHERE id=?", changeId));
        assertNull(value("SELECT pending_change_id FROM sol_construction_plan WHERE id=?", planId));
        assertEquals(0, runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(PROCESS_KEY).variableValueEquals("projectId", projectId).count());
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND operation_code='DURATION_CHANGE_SUBMIT' "
                + "AND aggregate_key=? AND result_code='SUCCESS'", Long.class, String.valueOf(changeId)));

        jdbcTemplate.update("UPDATE proj_project SET version=3 WHERE tenant_id=0 AND id=?", projectId);
        var response = durationChangeService.submit(new SubmitDurationChangeCommand(
                        planId, changeId, 0, 3,
                        "task9-real-project-success-" + changeId, "b".repeat(64)),
                new ConstructionPlanApplicationService.Actor(0L, APPLICANT,
                        "task9-real-project-success-" + changeId));
        processInstanceId = response.getProcessInstanceId();

        assertNotNull(runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult());
        assertEquals(ConstructionPlanChangeDO.STATUS_PENDING_APPROVAL,
                value("SELECT status_code FROM sol_construction_plan_change WHERE id=?", changeId));
        assertEquals(changeId, number("SELECT pending_change_id FROM sol_construction_plan WHERE id=?", planId));
    }

    @ParameterizedTest
    @EnumSource(Command.class)
    void requiredEvidenceSubmissionAndTerminalResultFreezeRealPltFacts(Command command) {
        prepareRequiredEvidenceSubmission();
        login(APPLICANT);

        var response = durationChangeService.submit(new SubmitDurationChangeCommand(
                        planId, changeId, 0, 3,
                        "task6-file-submit-" + changeId, "c".repeat(64)),
                new ConstructionPlanApplicationService.Actor(0L, APPLICANT,
                        "task6-file-submit-" + changeId));
        processInstanceId = response.getProcessInstanceId();

        assertEquals(4L, number("SELECT customer_evidence_artifact_version "
                + "FROM sol_construction_plan_change WHERE id=?", changeId));
        assertEquals(5L, number("SELECT customer_evidence_reference_version "
                + "FROM sol_construction_plan_change WHERE id=?", changeId));
        assertEquals(6L, number("SELECT customer_evidence_availability_version "
                + "FROM sol_construction_plan_change WHERE id=?", changeId));
        assertEquals(7L, number("SELECT customer_evidence_scope_version "
                + "FROM sol_construction_plan_change WHERE id=?", changeId));

        invoke(command);

        assertEquals(command.changeStatus(),
                value("SELECT status_code FROM sol_construction_plan_change WHERE id=?", changeId));
        assertEquals(command == Command.APPROVE ? candidateRevisionId : baseRevisionId,
                number("SELECT current_duration_revision_id FROM sol_construction_plan WHERE id=?", planId));
    }

    @Test
    void changedReferenceFactRollsBackBpmAndSolTerminalResult() {
        prepareRequiredEvidenceSubmission();
        login(APPLICANT);
        var response = durationChangeService.submit(new SubmitDurationChangeCommand(
                        planId, changeId, 0, 3,
                        "task6-file-conflict-" + changeId, "d".repeat(64)),
                new ConstructionPlanApplicationService.Actor(0L, APPLICANT,
                        "task6-file-conflict-" + changeId));
        processInstanceId = response.getProcessInstanceId();
        jdbcTemplate.update("UPDATE plt_file_reference SET version=version+1 WHERE tenant_id=0 "
                + "AND object_id=?", String.valueOf(changeId));

        login(APPROVER);
        String taskId = flowableTaskService.createTaskQuery().processInstanceId(processInstanceId)
                .singleResult().getId();
        assertThrows(RuntimeException.class, () -> bpmTaskService.approveTask(APPROVER,
                new BpmTaskApproveReqVO().setId(taskId).setReason("并发换版")));

        assertNotNull(runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult());
        assertEquals(ConstructionPlanChangeDO.STATUS_PENDING_APPROVAL,
                value("SELECT status_code FROM sol_construction_plan_change WHERE id=?", changeId));
        assertEquals(baseRevisionId,
                number("SELECT current_duration_revision_id FROM sol_construction_plan WHERE id=?", planId));
        assertEquals(changeId, number("SELECT pending_change_id FROM sol_construction_plan WHERE id=?", planId));
    }

    @Test
    void concurrentTreePublishMakesFrozenScopeVersionConflictWithoutTerminalSideEffects() throws Exception {
        prepareRequiredEvidenceSubmission();
        insertRealProjectTreeFacts();
        projectScopeApi.useReal();
        participantFactApi.useReal();
        login(APPLICANT);
        var response = durationChangeService.submit(new SubmitDurationChangeCommand(
                        planId, changeId, 0, 3,
                        "task6-scope-submit-" + changeId, "f".repeat(64)),
                new ConstructionPlanApplicationService.Actor(0L, APPLICANT,
                        "task6-scope-submit-" + changeId));
        processInstanceId = response.getProcessInstanceId();

        CountDownLatch rootLocked = new CountDownLatch(1);
        CountDownLatch publishAllowed = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> publish = executor.submit(() -> {
                TenantContextHolder.setTenantId(0L);
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.queryForObject("SELECT id FROM proj_project WHERE tenant_id=0 AND id=? FOR UPDATE",
                                Long.class, rootProjectId);
                        rootLocked.countDown();
                        await(publishAllowed);
                        treeProjectionService.publish(rootProjectId, 8L,
                                "task6-scope-publish-" + changeId);
                    });
                } finally {
                    TenantContextHolder.clear();
                }
            });
            if (!rootLocked.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("树发布未取得根项目锁");
            }
            Future<?> terminal = executor.submit(() -> {
                invoke(Command.APPROVE);
                return null;
            });
            assertThrows(TimeoutException.class, () -> terminal.get(500, TimeUnit.MILLISECONDS));

            publishAllowed.countDown();
            publish.get(10, TimeUnit.SECONDS);
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> terminal.get(10, TimeUnit.SECONDS));
        } finally {
            publishAllowed.countDown();
        }

        assertEquals(8L, number("SELECT MAX(tree_version) FROM proj_project_tree_version "
                + "WHERE tenant_id=0 AND root_project_id=? AND status='ACTIVE'", rootProjectId));
        assertNotNull(runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult());
        assertEquals(ConstructionPlanChangeDO.STATUS_PENDING_APPROVAL,
                value("SELECT status_code FROM sol_construction_plan_change WHERE id=?", changeId));
        assertEquals(baseRevisionId,
                number("SELECT current_duration_revision_id FROM sol_construction_plan WHERE id=?", planId));
        assertEquals(changeId, number("SELECT pending_change_id FROM sol_construction_plan WHERE id=?", planId));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND operation_code='DURATION_CHANGE_BPM_RESULT' "
                + "AND aggregate_key=? AND result_code='SUCCESS'", Long.class, String.valueOf(changeId)));
    }

    @Test
    void concurrentSubmissionsAllowOnlyOnePendingApproval() throws Exception {
        prepareRealSubmission();

        List<Boolean> outcomes = runConcurrently(
                () -> submit("task9-submit-a-" + changeId),
                () -> submit("task9-submit-b-" + changeId));

        assertEquals(1L, outcomes.stream().filter(Boolean::booleanValue).count());
        List<ProcessInstance> activeInstances = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(PROCESS_KEY).variableValueEquals("projectId", projectId).list();
        assertEquals(1, activeInstances.size());
        processInstanceId = activeInstances.getFirst().getId();
        assertEquals(ConstructionPlanChangeDO.STATUS_PENDING_APPROVAL,
                value("SELECT status_code FROM sol_construction_plan_change WHERE id=?", changeId));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND operation_code='DURATION_CHANGE_SUBMIT' "
                + "AND aggregate_key=? AND result_code='SUCCESS'", Long.class, String.valueOf(changeId)));
    }

    @Test
    void concurrentTerminalCommandsAllowOnlyOneWinner() throws Exception {
        stubAuthorization(Failure.NONE, Command.APPROVE);
        String taskId = flowableTaskService.createTaskQuery().processInstanceId(processInstanceId)
                .singleResult().getId();

        List<Boolean> outcomes = runConcurrently(
                () -> approve(taskId),
                () -> approve(taskId));

        assertEquals(1L, outcomes.stream().filter(Boolean::booleanValue).count());
        assertNull(runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult());
        assertEquals(ConstructionPlanChangeDO.STATUS_APPROVED,
                value("SELECT status_code FROM sol_construction_plan_change WHERE id=?", changeId));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND operation_code='DURATION_CHANGE_BPM_RESULT' "
                + "AND aggregate_key=? AND result_code='SUCCESS'", Long.class, String.valueOf(changeId)));
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

    private void prepareRealSubmission() {
        resetPendingFactsToDraft();
        stubAuthorization(Failure.NONE, Command.CANCEL);
        when(participantFactApi.mock().inspect(any())).thenAnswer(invocation -> {
            ProjectParticipantFactQuery query = invocation.getArgument(0);
            Long userId = query.subjectUserId() == null ? APPROVER : query.subjectUserId();
            Set<String> roles = userId == APPLICANT
                    ? Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)
                    : Set.of(ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1);
            return new ProjectParticipantFact(projectId, userId, roles,
                    "PRIMARY", "ACTIVE", "S1", 3, 3L);
        });
        when(participantFactApi.mock().lockAndRevalidate(any())).thenAnswer(invocation -> {
            var query = invocation.getArgument(0, ProjectParticipantFactRevalidationQuery.class);
            long userId = query.userId();
            Set<String> roles = userId == APPLICANT
                    ? Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)
                    : Set.of(ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1);
            return new ProjectParticipantFact(projectId, userId, roles, "PRIMARY",
                    "ACTIVE", "S1", 3, 3L);
        });
        DictDataRespDTO internalReason = new DictDataRespDTO();
        internalReason.setDictType("pms_duration_change_reason_type");
        internalReason.setValue("INTERNAL_ADJUSTMENT");
        internalReason.setStatus(CommonStatusEnum.ENABLE.getStatus());
        DictDataRespDTO customerReason = new DictDataRespDTO();
        customerReason.setDictType("pms_duration_change_reason_type");
        customerReason.setValue("CUSTOMER_DELAY");
        customerReason.setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(dictDataApi.getDictDataList("pms_duration_change_reason_type"))
                .thenReturn(List.of(internalReason, customerReason));
        when(configApi.getConfigValueByKey(
                "pms.sol.duration-change.customer-evidence-required-reason-codes"))
                .thenReturn("CUSTOMER_DELAY");
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(PROCESS_KEY).processDefinitionTenantId("0")
                .latestVersion().singleResult();
        when(processDefinitionService.getActiveProcessDefinition(PROCESS_KEY)).thenReturn(definition);
        when(processDefinitionService.getProcessDefinition(definition.getId())).thenReturn(definition);
        when(processDefinitionService.getProcessDefinitionBpmnModel(definition.getId())).thenReturn(bpmnModel);
    }

    private void prepareRequiredEvidenceSubmission() {
        prepareRealSubmission();
        long artifactId = planId + 5;
        long fileVersionId = planId + 6;
        long referenceId = planId + 7;
        long infraFileId = planId + 8;
        jdbcTemplate.update("UPDATE sol_construction_plan_change SET reason_type_code='CUSTOMER_DELAY', "
                        + "customer_evidence_file_id=?, customer_evidence_file_version=2, "
                        + "customer_evidence_reference_key='customer-delay' WHERE id=?",
                artifactId, changeId);
        jdbcTemplate.update("INSERT INTO plt_file_artifact "
                        + "(id,name,category_code,owner_context,lifecycle_status_code,version,creator,updater,tenant_id) "
                        + "VALUES (?,'客户延期依据.pdf','CUSTOMER_DELAY_EVIDENCE','SOL','ACTIVE',4,'9','9',0)",
                artifactId);
        jdbcTemplate.update("INSERT INTO plt_file_version "
                        + "(id,artifact_id,version_no,infra_file_id,availability_version,sha256,size_bytes,"
                        + "declared_media_type,detected_media_type,scan_status_code,scan_provider_code,"
                        + "scan_provider_version,availability_status_code,created_by,created_at,tenant_id) "
                        + "VALUES (?,?,2,?,6,?,128,'application/pdf','application/pdf','PASSED',"
                        + "'CLAMAV','1','AVAILABLE',9,NOW(3),0)",
                fileVersionId, artifactId, infraFileId, "e".repeat(64));
        jdbcTemplate.update("INSERT INTO plt_file_reference "
                        + "(id,owner_context,object_type,object_id,purpose_code,reference_key,artifact_id,"
                        + "file_version_no,sensitivity_code,status_code,scope_version,version,creator,updater,tenant_id) "
                        + "VALUES (?,'SOL','CONSTRUCTION_PLAN_CHANGE',?,'CUSTOMER_DELAY_EVIDENCE',"
                        + "'customer-delay',?,2,'INTERNAL','ACTIVE',7,5,'9','9',0)",
                referenceId, String.valueOf(changeId), artifactId);
    }

    private void submit(String key) {
        login(APPLICANT);
        try {
            durationChangeService.submit(new SubmitDurationChangeCommand(
                            planId, changeId, 0, 3, key, "a".repeat(64)),
                    new ConstructionPlanApplicationService.Actor(0L, APPLICANT, key));
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    private void approve(String taskId) {
        login(APPROVER);
        try {
            bpmTaskService.approveTask(APPROVER,
                    new BpmTaskApproveReqVO().setId(taskId).setReason("并发同意"));
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    private List<Boolean> runConcurrently(CheckedAction first, CheckedAction second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> firstTask = concurrentTask(ready, start, first);
            Callable<Boolean> secondTask = concurrentTask(ready, start, second);
            Future<Boolean> firstResult = executor.submit(firstTask);
            Future<Boolean> secondResult = executor.submit(secondTask);
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发任务未就绪");
            }
            start.countDown();
            return List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Callable<Boolean> concurrentTask(CountDownLatch ready, CountDownLatch start,
                                                     CheckedAction action) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                action.run();
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待并发事务超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待并发事务被中断", exception);
        }
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
        participantFactApi.useMock();
        projectScopeApi.useMock();
        reset(permissionApi, projectScopeApi.mock(), participantFactApi.mock());
        when(permissionApi.hasAnyPermissions(anyLong(), anyString())).thenReturn(failure != Failure.PERMISSION);
        ProjectScopeResult scope = failure == Failure.SCOPE
                ? new ProjectScopeResult(projectId, 7L, Set.of(), Set.of())
                : new ProjectScopeResult(projectId, 7L, Set.of(projectId), Set.of());
        when(projectScopeApi.mock().resolveCurrent(any())).thenReturn(scope);
        when(projectScopeApi.mock().lockAndRevalidate(any())).thenReturn(scope);
        long actor = command == Command.CANCEL ? APPLICANT : APPROVER;
        Set<String> roles = command == Command.CANCEL
                ? Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)
                : Set.of(ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1);
        ProjectParticipantFact fact = new ProjectParticipantFact(projectId, actor, roles, "PRIMARY",
                "ACTIVE", "S1", 3, 3L);
        when(participantFactApi.mock().inspect(any())).thenReturn(failure == Failure.ROLE ? null : fact);
        when(participantFactApi.mock().lockAndRevalidate(any())).thenReturn(fact);
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

    private void resetPendingFactsToDraft() {
        runtimeService.setVariable(processInstanceId,
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS, 99);
        runtimeService.deleteProcessInstance(processInstanceId, "prepare real submit");
        processInstanceId = null;
        jdbcTemplate.update("UPDATE sol_construction_plan_revision SET frozen_at=NULL WHERE id=?",
                candidateRevisionId);
        jdbcTemplate.update("UPDATE sol_construction_plan_change SET status_code='DRAFT', "
                        + "reason_type_code='INTERNAL_ADJUSTMENT', customer_evidence_required=b'0', "
                        + "process_definition_key=NULL, process_instance_id=NULL, submitted_at=NULL, "
                        + "approver_user_id=NULL, version=0 WHERE id=?", changeId);
        jdbcTemplate.update("UPDATE sol_construction_plan SET pending_change_id=NULL, version=0 WHERE id=?",
                planId);
    }

    private void insertRealProjectFacts(int version) {
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,manager_id,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,'S0','ACTIVE','S1','ASSIGNED',0,0,?,0)",
                projectId, "FSOL001-T9-" + projectId, projectId, 0,
                "F-SOL-001 Task9 " + projectId, APPLICANT, projectId, "/", 0, 0, version);
        jdbcTemplate.update("INSERT INTO proj_project_member_assignment "
                        + "(project_id,user_id,member_role,assignment_type,responsibility,effective_from,effective_to,"
                        + "status,version,tenant_id) VALUES (?,?,?,'PRIMARY',?,NOW(3),NULL,'ACTIVE',0,0)",
                projectId, APPROVER, ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1,
                ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1);
    }

    private void insertRealProjectTreeFacts() {
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,manager_id,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,'S0','ACTIVE','S1','ASSIGNED',0,0,3,0)",
                rootProjectId, "FSOL001-T6-ROOT-" + rootProjectId, rootProjectId, 0,
                "F-SOL-001 Task6 Root " + rootProjectId, APPLICANT, rootProjectId, "/", 0, 0);
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id,project_code,code_root_id,project_sequence,project_name,manager_id,parent_id,root_id,tree_path,"
                        + "tree_depth,tree_sort,status,lifecycle_status,current_stage,assignment_status,"
                        + "task_tree_version,task_progress_version,version,tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,'S0','ACTIVE','S1','ASSIGNED',0,0,3,0)",
                projectId, "FSOL001-T6-CHILD-" + projectId, rootProjectId, 1,
                "F-SOL-001 Task6 Child " + projectId, APPLICANT, rootProjectId, rootProjectId,
                "/" + rootProjectId + "/", 1, 1);
        jdbcTemplate.update("INSERT INTO proj_project_tree_version "
                        + "(id,root_project_id,tree_version,status,change_batch_id,node_count,path_count,version,tenant_id) "
                        + "VALUES (?,?,7,'ACTIVE',?,2,3,0,0)",
                rootProjectId, rootProjectId, "task6-scope-v7-" + changeId);
        jdbcTemplate.update("INSERT INTO proj_project_tree_path "
                        + "(id,tree_version,root_project_id,ancestor_project_id,descendant_project_id,distance,version,tenant_id) "
                        + "VALUES (?,7,?,?,?,?,0,0),(?,7,?,?,?,?,0,0),(?,7,?,?,?,?,0,0)",
                planId + 30, rootProjectId, rootProjectId, rootProjectId, 0,
                planId + 31, rootProjectId, rootProjectId, projectId, 1,
                planId + 32, rootProjectId, projectId, projectId, 0);
        jdbcTemplate.update("INSERT INTO proj_project_member_assignment "
                        + "(project_id,user_id,member_role,assignment_type,responsibility,effective_from,effective_to,"
                        + "status,version,tenant_id) VALUES (?,?,?,'PRIMARY',?,NOW(3),NULL,'ACTIVE',0,0)",
                projectId, APPLICANT, ProjectParticipantFactApi.ROLE_PROJECT_MANAGER,
                ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);
        jdbcTemplate.update("INSERT INTO proj_project_member_assignment "
                        + "(project_id,user_id,member_role,assignment_type,responsibility,effective_from,effective_to,"
                        + "status,version,tenant_id) VALUES (?,?,?,'PRIMARY',?,NOW(3),NULL,'ACTIVE',0,0)",
                projectId, APPROVER, ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1,
                ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1);
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

    @FunctionalInterface
    interface CheckedAction {
        void run();
    }

    static final class SwitchingProjectScopeApi implements ProjectScopeApi {
        private final ProjectScopeApi real;
        private final ProjectScopeApi mock = org.mockito.Mockito.mock(ProjectScopeApi.class);
        private volatile boolean useReal;

        SwitchingProjectScopeApi(ProjectScopeApi real) {
            this.real = real;
        }

        ProjectScopeApi mock() { return mock; }
        void useMock() { useReal = false; }
        void useReal() { useReal = true; }

        @Override
        public ProjectScopeResult resolve(ProjectScopeQuery query) {
            return (useReal ? real : mock).resolve(query);
        }

        @Override
        public ProjectScopeResult resolveCurrent(ProjectCurrentScopeQuery query) {
            return (useReal ? real : mock).resolveCurrent(query);
        }

        @Override
        public ProjectScopeResult lockAndRevalidate(ProjectScopeRevalidationQuery query) {
            return (useReal ? real : mock).lockAndRevalidate(query);
        }
    }

    static final class SwitchingProjectParticipantFactApi implements ProjectParticipantFactApi {
        private final ProjectParticipantFactApi real;
        private final ProjectParticipantFactApi mock = org.mockito.Mockito.mock(ProjectParticipantFactApi.class);
        private volatile boolean useReal;

        SwitchingProjectParticipantFactApi(ProjectParticipantFactApi real) {
            this.real = real;
        }

        ProjectParticipantFactApi mock() {
            return mock;
        }

        void useMock() {
            useReal = false;
        }

        void useReal() {
            useReal = true;
        }

        @Override
        public ProjectParticipantFact inspect(ProjectParticipantFactQuery query) {
            return (useReal ? real : mock).inspect(query);
        }

        @Override
        public ProjectParticipantFact lockAndRevalidate(ProjectParticipantFactRevalidationQuery query) {
            return (useReal ? real : mock).lockAndRevalidate(query);
        }
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({ProcessEngineAutoConfiguration.class,
            ProcessEngineServicesAutoConfiguration.class})
    @MapperScan({"cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.file",
            "cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual",
            "cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            BpmTaskServiceImpl.class, BpmProcessInstanceServiceImpl.class, BpmProcessInstanceApiImpl.class,
            BpmProcessInstanceEventListener.class, PlatformCommandExecutionApiImpl.class,
            OperationAuditApiImpl.class, FileBusinessObjectPolicyRegistry.class,
            FileArtifactApiImpl.class, ConstructionPlanChangeFilePolicyProvider.class,
            DurationChangeApplicationService.class,
            DurationChangeProperties.class, DurationChangeBpmListener.class,
            DurationChangeBpmAuthorizationGuard.class, DurationChangeBpmResultService.class,
            ProjectTreeScopeService.class, ProjectTreeProjectionService.class, ProjectTreeMetrics.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PermissionApi permissionApi() { return mock(PermissionApi.class); }
        @Bean ProjectScopeApiImpl realProjectScopeApi(ProjectTreeScopeService scopeService) {
            return new ProjectScopeApiImpl(scopeService);
        }
        @Bean @Primary
        SwitchingProjectScopeApi projectScopeApi(ProjectScopeApiImpl realProjectScopeApi) {
            return new SwitchingProjectScopeApi(realProjectScopeApi);
        }
        @Bean
        SwitchingProjectParticipantFactApi participantFactApi(ProjectMasterMapper projectMapper,
                                                               ProjectMemberAssignmentMapper memberMapper) {
            return new SwitchingProjectParticipantFactApi(
                    new ProjectParticipantFactApiImpl(projectMapper, memberMapper));
        }
        @Bean DictDataApi dictDataApi() { return mock(DictDataApi.class); }
        @Bean AuthorizationGrantApi authorizationGrantApi() { return mock(AuthorizationGrantApi.class); }
        @Bean io.micrometer.core.instrument.MeterRegistry meterRegistry() {
            return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        }
        @Bean ConfigApi configApi() { return mock(ConfigApi.class); }
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
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
