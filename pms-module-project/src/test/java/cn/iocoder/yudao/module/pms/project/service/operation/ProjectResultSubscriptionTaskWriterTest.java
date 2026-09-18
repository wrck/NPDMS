package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.AcceptanceActivityCompletionFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Validity;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.*;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectGateRuleService;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.*;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.*;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 实际订阅扫描、证据Guard、计划判定与Task正式Writer；外围仓储与规则执行使用显式替身。 */
class ProjectResultSubscriptionTaskWriterTest {
    private ResultEvidenceScanFixture f;
    private ProjectTaskLifecycleService writer;
    private ProjectTaskInstanceDO task;
    private ProjectTaskExecutionContractDO binding;
    private final ProjectTaskRuntimeMapper tasks=mock(ProjectTaskRuntimeMapper.class);
    private final ProjectTaskExecutionContractMapper contracts=mock(ProjectTaskExecutionContractMapper.class);
    private final ProjectTaskCompletionEvaluationMapper evaluations=mock(ProjectTaskCompletionEvaluationMapper.class);
    private final ProjectTaskBusinessService business=mock(ProjectTaskBusinessService.class);
    private final ProjectRuleEvaluationService rules=mock(ProjectRuleEvaluationService.class);
    private final PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    private final List<PlatformCommandExecutionApi.SuccessFacts> success=new ArrayList<>();

    @BeforeEach void before() throws Exception {
        f=new ResultEvidenceScanFixture();var snapshot=ResultSubscriptionTaskFixture.snapshot();
        binding=ResultSubscriptionTaskFixture.contract(snapshot);
        f.recovery.plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        when(f.recovery.plans.selectEffective(any())).thenReturn(f.recovery.plan);
        f.recovery.project.setTaskTreeVersion(1L);f.recovery.project.setTaskProgressVersion(1L);
        f.recovery.round.setNodeKind("TASK");f.recovery.round.setNodeKey("task");f.recovery.round.setVersion(0);f.recovery.round.setRoundNo(1);
        f.recovery.jdbc.update("UPDATE proj_result_subscription SET node_kind='TASK',node_key='task'");
        task=new ProjectTaskInstanceDO().setId(4L).setProjectId(3L).setCode("T1").setStageCode("S1").setName("结果任务").setStatus("IN_PROGRESS").setVersion(1).setStateMachineRevisionId(9L);
        var graph=mock(ProjectRuntimeGraphMapper.class);
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(new ProjectStageInstanceDO().setId(5L).setCode("S1").setStatus("ACTIVE")));
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of(task));when(graph.selectGatesForUpdate(any())).thenReturn(List.of());
        when(rules.evaluate(anyString(),any(),any())).thenAnswer(call->new RuleEvaluation(call.getArgument(0),RuleEvaluation.Outcome.MATCHED,null,List.of(),List.of(),List.of()));
        var planService=new ProjectTaskPlanCompletionService(f.recovery.plans,f.recovery.rounds,graph,mock(ProjectGateReferenceInstanceMapper.class),mock(ProjectRuntimeRuleEvaluator.class),rules,new ProjectRuleCompiler(),business,mock(ProjectNodeExecutionApi.class),mock(ProjectGateRuleService.class));
        var guard=f.recovery.proxy(new ProjectResultEvidenceGuard(f.recovery.subscriptions,f.evidence,new ProjectResultEvidenceConsistency(f.recovery.sources,f.recovery.journal),f.recovery.outbox));
        @SuppressWarnings("unchecked") ObjectProvider<ProjectResultEvidenceGuard> provider=mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(guard);ReflectionTestUtils.setField(planService,"resultEvidence",provider);
        when(tasks.selectProjectForCommandForUpdate(any())).thenReturn(f.recovery.project);
        when(tasks.selectTaskForAssignmentForUpdate(any())).thenReturn(task);when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(binding);
        when(tasks.selectUnfinishedStartedDescendantIdsForUpdate(any())).thenReturn(List.of());
        when(tasks.updateLifecycleIfMatch(any())).thenReturn(1);when(evaluations.insertEvaluation(any())).thenReturn(1);
        when(f.recovery.rounds.recordTaskTransition(any())).thenReturn(1);
        var transitions=mock(TaskStateMachineMapper.class);
        when(transitions.requireTransition(any())).thenAnswer(call->{
            var query=call.getArgument(0,cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateTransitionQuery.class);
            var transition=new TaskStateTransitionDO();transition.setToStatusCode("SUBMIT".equals(query.actionCode())?"PENDING_ACCEPT":"DONE");return transition;
        });
        when(commands.execute(any(),any(),eq(TaskCommandResult.class),any(),any())).thenAnswer(call->{
            TaskCommandResult result=call.<Supplier<TaskCommandResult>>getArgument(3).get();
            success.add(call.<Function<TaskCommandResult,PlatformCommandExecutionApi.SuccessFacts>>getArgument(4).apply(result));
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,result);
        });
        var service=new ProjectTaskLifecycleService(tasks,contracts,mock(ProjectTaskAssignmentMapper.class),mock(ProjectMemberAssignmentMapper.class),evaluations,transitions,
            mock(TaskNativeBindingHostProvider.class),commands,mock(OperationAuditApi.class),mock(ProjectTaskProgressService.class),mock(PermissionApi.class),
            mock(AcceptanceActivityCompletionFactApi.class),mock(ProjectScopeApi.class),f.recovery.proxy(planService),mock(TaskBusinessBindingHostProvider.class),mock(ProjectTreeScopeService.class));
        ReflectionTestUtils.setField(service,"nodeExecutions",f.recovery.rounds);ReflectionTestUtils.setField(service,"timers",mock(ProjectRuleTimerScheduler.class));
        writer=f.recovery.proxy(service);
    }
    @AfterEach void after(){f.close();}
    private ProjectTaskLifecycleService.AutomaticResult complete(){return writer.completeFromBusinessResult(3L,4L,"result-evidence");}

    @Test void pureSubscriptionCompletesThroughTheOriginalWriterWithoutManualSubmissionOrOwnerCommands() {
        assertFalse(complete().completed());verifyNoInteractions(commands);verify(tasks,never()).updateLifecycleIfMatch(any());
        f.seed(1,"object","r1",null,Validity.CURRENT);f.tick();assertTrue(complete().completed());
        assertNull(f.recovery.round.getSubmittedAt());assertEquals(1,success.size());
        var recorded=ArgumentCaptor.forClass(ProjectNodeExecutionMapper.TaskTransition.class);
        verify(f.recovery.rounds).recordTaskTransition(recorded.capture());
        var history=JsonUtils.parseTree(recorded.getValue().evidence());
        assertEquals("COMPLETE",history.get("action").asText());
        assertEquals(f.scan().getId().longValue(),history.get("subscriptionEvidence").get(0).get("scanId").longValue());
        assertTrue(success.getFirst().eventType().equals("TaskCompleted"));verifyNoInteractions(business);
    }
    @Test void evaluatedOutboxNotificationReachesTheRealFormalWriter() {
        f.seed(1,"object","r1",null,Validity.CURRENT);f.tick();
        var admission=mock(ProjectTaskAdmissionService.class);
        when(admission.activateEligible(any(),any(),any())).thenReturn(new ProjectTaskAdmissionService.Result(false,false));
        var processor=f.recovery.proxy(new ProjectResultEvidenceProcessor(f.recovery.contexts,f.evidence,admission,
                mock(ProjectStageAdmissionService.class),mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService.class),
                writer,mock(cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectStageCompletionService.class),f.recovery.outbox,mock(OperationAuditApi.class)));
        var event=ResultEvidenceEvaluatedEvent.create(ResultSubscriptionWakeup.create(f.recovery.row()),f.scan());
        assertEquals("ADVANCED",processor.process(event));
        verify(tasks).updateLifecycleIfMatch(any());
        assertEquals(1,success.size());verifyNoInteractions(business);
    }

    @Test void staleEvidenceRulesAndStartedChildrenRemainMandatoryCompletionGuards() {
        f.seed(1,"object","r1",null,Validity.CURRENT);f.tick();f.recovery.committed=8;
        assertFalse(complete().completed());f.recovery.committed=7;
        when(tasks.selectUnfinishedStartedDescendantIdsForUpdate(any())).thenReturn(List.of(6L));assertFalse(complete().completed());
        when(tasks.selectUnfinishedStartedDescendantIdsForUpdate(any())).thenReturn(List.of());
        when(rules.evaluate(anyString(),any(),any())).thenAnswer(call->new RuleEvaluation(call.getArgument(0),RuleEvaluation.Outcome.NOT_MATCHED,"RULE_FALSE",List.of(),List.of(),List.of()));
        assertFalse(complete().completed());verify(tasks,never()).updateLifecycleIfMatch(any());verifyNoInteractions(commands,business);
    }
    @Test void closedProjectAndAlreadyFinishedTaskAreNotReopenedByAResult() {
        f.seed(1,"object","r1",null,Validity.CURRENT);f.tick();f.recovery.project.setLifecycleStatus("CLOSED");
        assertFalse(complete().completed());f.recovery.project.setLifecycleStatus("ACTIVE");task.setStatus("DONE");assertFalse(complete().completed());
        verifyNoInteractions(commands,business);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"permission", "target", "parameters", "version"})
    void invalidSubscriptionProjectionCannotUseTheAutomaticWriter(String damage) {
        switch (damage) {
            case "permission" -> binding.setPermissionSnapshot("{\"policySnapshot\":{\"requiredActions\":[\"COMPLETE\"]}}");
            case "target" -> binding.setTargetObjectKey("untrusted-owner");
            case "parameters" -> binding.setBindingParameterSnapshot("{}");
            case "version" -> binding.setSourceDefinitionVersion(2);
            default -> throw new AssertionError(damage);
        }
        assertThrows(RuntimeException.class, this::complete);
        verifyNoInteractions(commands, business);
        verify(tasks, never()).updateLifecycleIfMatch(any());
    }

    @Test void failingTheFormalRoundCompareAndSetDoesNotReturnACompletionReceipt() {
        f.seed(1,"object","r1",null,Validity.CURRENT);f.tick();when(f.recovery.rounds.recordTaskTransition(any())).thenReturn(0);
        assertThrows(RuntimeException.class,this::complete);assertTrue(success.isEmpty());
    }
}
