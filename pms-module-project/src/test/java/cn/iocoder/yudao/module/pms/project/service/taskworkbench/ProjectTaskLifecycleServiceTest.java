package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskCompletionEvaluationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskLifecycleStateUpdate;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.AcceptanceActivityCompletionFactApi;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto.AcceptanceActivityCompletionFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTaskLifecycleServiceTest {

    @Mock ProjectTaskRuntimeMapper taskMapper;
    @Mock ProjectTaskExecutionContractMapper contractMapper;
    @Mock ProjectTaskAssignmentMapper assignmentMapper;
    @Mock ProjectMemberAssignmentMapper memberMapper;
    @Mock ProjectTaskCompletionEvaluationMapper evaluationMapper;
    @Mock ProjectGateInstanceMapper gateMapper;
    @Mock TaskStateMachineMapper stateMachineMapper;
    @Mock TaskNativeBindingHostProvider nativeProvider;
    @Mock PlatformCommandExecutionApi commandExecutionApi;
    @Mock OperationAuditApi operationAuditApi;
    @Mock ProjectTaskProgressService progressService;
    @Mock PermissionApi permissionApi;
    @Mock AcceptanceActivityCompletionFactApi acceptanceActivityCompletionFactApi;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock TaskBusinessCompletionEvaluator businessEvaluator;
    @Mock TaskBusinessBindingHostProvider businessProvider;
    @Mock cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService treeScopes;

    private ProjectTaskLifecycleService service;
    private PlatformCommandExecutionApi.SuccessFacts successFacts;

    @BeforeEach
    void setUp() {
        service = new ProjectTaskLifecycleService(taskMapper, contractMapper, assignmentMapper, memberMapper,
                evaluationMapper, gateMapper,
                stateMachineMapper, nativeProvider, commandExecutionApi, operationAuditApi, progressService,
                permissionApi, acceptanceActivityCompletionFactApi, projectScopeApi, businessEvaluator, businessProvider, treeScopes);
    }

    @Test
    void startUsesFrozenTransitionAndInitializesActualStartTime() {
        allowAction("PENDING_START", "START", "IN_PROGRESS");
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        TaskCommandResult result = service.act(command("start", 3, null, null), actor());

        assertEquals("IN_PROGRESS", result.status());
        ArgumentCaptor<TaskLifecycleStateUpdate> update = ArgumentCaptor.forClass(TaskLifecycleStateUpdate.class);
        verify(taskMapper).updateLifecycleIfMatch(update.capture());
        assertTrue(update.getValue().initializeActualStartTime());
        assertFalse(update.getValue().setActualEndTime());
        assertEquals("PROJECT_TASK_START", successFacts.operationCode());
    }

    @Test
    void submitFreezesProgressAtNinetyNine() {
        allowAction("IN_PROGRESS", "SUBMIT", "PENDING_ACCEPT");
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        TaskCommandResult result = service.act(command("submit", 3, null, null), actor());

        assertEquals("PENDING_ACCEPT", result.status());
        ArgumentCaptor<TaskLifecycleStateUpdate> update = ArgumentCaptor.forClass(TaskLifecycleStateUpdate.class);
        verify(taskMapper).updateLifecycleIfMatch(update.capture());
        assertEquals(99, update.getValue().progress());
    }

    @Test
    void superAdminMayOperateForAnActualExecutorButDoesNotCreateAnAssignment() {
        allowAction("PENDING_START", "START", "IN_PROGRESS");
        when(treeScopes.isTenantSuperAdmin(0L, 9L)).thenReturn(true);
        var assignment = new ProjectTaskAssignmentDO(); assignment.setAssigneeUserId(22L);
        when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(assignment);
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);
        assertEquals("IN_PROGRESS", service.act(command("start", 3, null, null), actor()).status());
        verify(memberMapper, never()).selectActiveByUserForUpdate(any());
    }

    @Test
    void superAdminStillCannotStartWithoutAnActualExecutor() {
        allowAction("PENDING_START", "START", "IN_PROGRESS");
        when(treeScopes.isTenantSuperAdmin(0L, 9L)).thenReturn(true);
        when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.act(command("start", 3, null, null), actor()));
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
    }

    @Test
    void superAdminStillNeedsOwnerCompletionFacts() {
        allowBusinessAction("BUSINESS_OBJECT");
        when(treeScopes.isTenantSuperAdmin(0L, 9L)).thenReturn(true);
        when(businessEvaluator.evaluateLocked(any(), any(), any())).thenReturn(
                new TaskBusinessCompletionEvaluator.Result(false, java.util.List.of("OWNER_NOT_COMPLETE"), businessEvidence()));
        when(evaluationMapper.insertEvaluation(any())).thenReturn(1);
        assertEquals("PENDING_ACCEPT", service.act(businessCommand(), actor()).status());
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
        verify(memberMapper, never()).selectActiveByUserForUpdate(any());
    }

    @Test
    void cancelWritesActualEndAndPreservesProgress() {
        allowAction("IN_PROGRESS", "CANCEL", "CLOSED");
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        TaskCommandResult result = service.act(command("cancel", 3, null, null), actor());

        assertEquals("CLOSED", result.status());
        ArgumentCaptor<TaskLifecycleStateUpdate> update = ArgumentCaptor.forClass(TaskLifecycleStateUpdate.class);
        verify(taskMapper).updateLifecycleIfMatch(update.capture());
        assertTrue(update.getValue().setActualEndTime());
        assertEquals(null, update.getValue().progress());
    }

    @Test
    void completeAppendsSatisfiedEvaluationAdvancesDoneAndPublishesEvent() {
        allowAction("PENDING_ACCEPT", "COMPLETE", "DONE");
        when(evaluationMapper.insertEvaluation(any())).thenReturn(1);
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        TaskCommandResult result = service.act(command("complete", 3, 91L, 2), actor());

        assertEquals("DONE", result.status());
        verify(evaluationMapper).insertEvaluation(any());
        assertEquals("TaskCompleted", successFacts.eventType());
        assertTrue(successFacts.eventPayload().contains("completionEvaluationId"));
    }

    @Test
    void unmetDescendantPersistsEvaluationWithoutAdvancingTask() {
        allowAction("PENDING_ACCEPT", "COMPLETE", "DONE");
        when(taskMapper.selectNonTerminalDescendantIdsForUpdate(any())).thenReturn(java.util.List.of(12L));
        when(evaluationMapper.insertEvaluation(any())).thenReturn(1);

        TaskCommandResult result = service.act(command("complete", 3, 91L, 2), actor());

        assertEquals("PENDING_ACCEPT", result.status());
        assertEquals(3, result.taskVersion());
        verify(evaluationMapper).insertEvaluation(any());
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
        assertEquals(null, successFacts.eventType());
        assertTrue(successFacts.detailSnapshot().contains("NON_TERMINAL_DESCENDANT"));
    }

    @Test
    void startRejectsWhenLockedCurrentAssignmentNoLongerBelongsToActor() {
        allowAction("PENDING_START", "START", "IN_PROGRESS");
        ProjectTaskAssignmentDO reassigned = new ProjectTaskAssignmentDO();
        reassigned.setAssigneeUserId(10L);
        when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(reassigned);

        assertThrows(RuntimeException.class, () -> service.act(command("start", 3, null, null), actor()));

        verify(taskMapper, never()).updateLifecycleIfMatch(any());
    }

    @Test
    void completeRejectsWhenLockedCurrentProjectManagerMembershipIsGone() {
        allowAction("PENDING_ACCEPT", "COMPLETE", "DONE");
        when(memberMapper.selectActiveByUserForUpdate(any())).thenReturn(java.util.List.of());

        assertThrows(RuntimeException.class, () -> service.act(command("complete", 3, 91L, 2), actor()));

        verify(evaluationMapper, never()).insertEvaluation(any());
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
    }

    @Test
    void acceptanceCompletionRejectsBeforeProviderWhenExecutePermissionMissing() {
        allowAcceptanceAction();
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:execute")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.act(acceptanceCommand(), actor()));

        verify(acceptanceActivityCompletionFactApi, never()).lockAndComplete(any());
        verify(evaluationMapper, never()).insertEvaluation(any());
    }

    @Test
    void acceptanceCompletionRejectsBeforeProviderWhenReportPermissionMissing() {
        allowAcceptanceAction();
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:execute")).thenReturn(true);
        when(permissionApi.hasAnyPermissions(9L, "pms:acceptance:report:complete")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.act(acceptanceCommand(), actor()));

        verify(acceptanceActivityCompletionFactApi, never()).lockAndComplete(any());
        verify(evaluationMapper, never()).insertEvaluation(any());
    }

    @Test
    void acceptanceCompletionUsesOwnerFactAfterBothPermissionsPass() {
        allowAcceptanceAction();
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:execute")).thenReturn(true);
        when(permissionApi.hasAnyPermissions(9L, "pms:acceptance:report:complete")).thenReturn(true);
        when(acceptanceActivityCompletionFactApi.lockAndComplete(any())).thenReturn(
                new AcceptanceActivityCompletionFact("COMPLETED", 51L, 1, 61L, 1));
        when(evaluationMapper.insertEvaluation(any())).thenReturn(1);
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        TaskCommandResult result = service.act(acceptanceCommand(), actor());

        assertEquals("DONE", result.status());
        verify(acceptanceActivityCompletionFactApi).lockAndComplete(any());
        verify(evaluationMapper).insertEvaluation(any());
    }

    @Test
    void acceptanceStartUsesProjectStateMachineWithoutCallingBindingProviders() {
        allowAcceptanceAction("PENDING_START", "START", "IN_PROGRESS");
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        TaskCommandResult result = service.act(command("start", 3, null, null), actor());

        assertEquals("IN_PROGRESS", result.status());
        verify(nativeProvider, never()).inspect(any());
        verify(acceptanceActivityCompletionFactApi, never()).lockAndComplete(any());
    }

    @Test
    void acceptanceSubmitUsesProjectStateMachineWithoutCallingBindingProviders() {
        allowAcceptanceAction("IN_PROGRESS", "SUBMIT", "PENDING_ACCEPT");
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        TaskCommandResult result = service.act(command("submit", 3, null, null), actor());

        assertEquals("PENDING_ACCEPT", result.status());
        verify(nativeProvider, never()).inspect(any());
        verify(acceptanceActivityCompletionFactApi, never()).lockAndComplete(any());
    }

    @Test
    void acceptanceCompletionAllowsCurrentAssigneeWithManageScope() {
        allowAcceptanceAction();
        when(memberMapper.selectActiveByUserForUpdate(any())).thenReturn(java.util.List.of());
        ProjectTaskAssignmentDO assignment = new ProjectTaskAssignmentDO();
        assignment.setAssigneeUserId(9L);
        when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(assignment);
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:execute")).thenReturn(true);
        when(permissionApi.hasAnyPermissions(9L, "pms:acceptance:report:complete")).thenReturn(true);
        when(acceptanceActivityCompletionFactApi.lockAndComplete(any())).thenReturn(
                new AcceptanceActivityCompletionFact("COMPLETED", 51L, 1, 61L, 1));
        when(evaluationMapper.insertEvaluation(any())).thenReturn(1);
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        TaskCommandResult result = service.act(acceptanceCommand(), actor());

        assertEquals("DONE", result.status());
        verify(acceptanceActivityCompletionFactApi).lockAndComplete(any());
    }

    @Test
    void businessCompletionFreezesOwnerEvidenceWithoutInventingNativeLongFact() {
        allowBusinessAction("BUSINESS_COMPONENT");
        var evidence = businessEvidence();
        when(businessEvaluator.evaluateLocked(any(), any(), any())).thenReturn(
                new TaskBusinessCompletionEvaluator.Result(true, java.util.List.of(), evidence));
        when(evaluationMapper.insertEvaluation(any())).thenReturn(1);
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        var result = service.act(businessCommand(), actor());

        assertEquals("DONE", result.status());
        var evaluation = ArgumentCaptor.forClass(
                cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskCompletionEvaluationDO.class);
        verify(evaluationMapper).insertEvaluation(evaluation.capture());
        assertEquals("SOL", evaluation.getValue().getFactContextCode());
        assertEquals("SITE_SURVEY", evaluation.getValue().getFactObjectType());
        assertEquals(null, evaluation.getValue().getFactObjectKey());
        assertEquals(null, evaluation.getValue().getFactVersion());
        assertTrue(evaluation.getValue().getBusinessFactsJson().contains("survey:revision:7"));
        var payload = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(successFacts.eventPayload(),
                cn.iocoder.yudao.module.pms.project.service.taskworkbench.event.TaskCompletedMessage.Payload.class);
        assertEquals(null, payload.factVersion());
        assertEquals("a".repeat(64), payload.businessFacts().get("aggregateFactVersion"));
        verify(nativeProvider, never()).inspect(any());
        verify(acceptanceActivityCompletionFactApi, never()).lockAndComplete(any());
        var order = org.mockito.Mockito.inOrder(contractMapper, businessEvaluator, evaluationMapper, taskMapper);
        order.verify(contractMapper).selectCurrentByTaskIdForUpdate(any());
        order.verify(businessEvaluator).evaluateLocked(any(), any(), any());
        order.verify(evaluationMapper).insertEvaluation(any());
        order.verify(taskMapper).updateLifecycleIfMatch(any());
    }

    @Test
    void emptyBusinessGroupPersistsFailureAndNeverUpdatesProgressOrOutbox() {
        allowBusinessAction("BUSINESS_OBJECT");
        when(businessEvaluator.evaluateLocked(any(), any(), any())).thenReturn(
                new TaskBusinessCompletionEvaluator.Result(false, java.util.List.of("BUSINESS_LINK_GROUP_EMPTY"), businessEvidence()));
        when(evaluationMapper.insertEvaluation(any())).thenReturn(1);

        var result = service.act(businessCommand(), actor());

        assertEquals("PENDING_ACCEPT", result.status());
        assertEquals(3, result.taskVersion());
        assertEquals(null, successFacts.eventType());
        assertTrue(successFacts.detailSnapshot().contains("BUSINESS_LINK_GROUP_EMPTY"));
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
        org.mockito.Mockito.verifyNoInteractions(progressService, nativeProvider, acceptanceActivityCompletionFactApi);
    }

    @Test
    void satisfiedBusinessFactsStillCannotBypassTaskDependenciesOrGate() {
        var contract = allowBusinessAction("BUSINESS_OBJECT");
        contract.setGateRef("gate-required");
        when(businessEvaluator.evaluateLocked(any(), any(), any())).thenReturn(
                new TaskBusinessCompletionEvaluator.Result(true, java.util.List.of(), businessEvidence()));
        when(taskMapper.selectNonTerminalDescendantIdsForUpdate(any())).thenReturn(java.util.List.of(12L));
        when(taskMapper.selectNonTerminalPredecessorIdsForUpdate(any())).thenReturn(java.util.List.of(13L));
        when(evaluationMapper.insertEvaluation(any())).thenReturn(1);

        assertEquals("PENDING_ACCEPT", service.act(businessCommand(), actor()).status());

        for (String code : java.util.List.of("NON_TERMINAL_DESCENDANT", "NON_TERMINAL_PREDECESSOR", "GATE_NOT_PASSED")) {
            assertTrue(successFacts.detailSnapshot().contains(code));
        }
        assertEquals(null, successFacts.eventType());
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
    }

    @Test
    void staleLockedBusinessVersionAbortsBeforeAnySuccessWrites() {
        allowBusinessAction("BUSINESS_OBJECT");
        when(businessEvaluator.evaluateLocked(any(), any(), any())).thenThrow(
                new IllegalStateException("TASK_BUSINESS_FACT_VERSION_CONFLICT"));

        assertThrows(IllegalStateException.class, () -> service.act(businessCommand(), actor()));

        verify(evaluationMapper, never()).insertEvaluation(any());
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
        org.mockito.Mockito.verifyNoInteractions(progressService);
        assertEquals(null, successFacts);
    }

    @Test
    void businessContextDenialCannotFallBackToNativeCompletion() {
        allowBusinessAction("BUSINESS_COMPONENT");
        when(businessProvider.inspect(any())).thenReturn(TaskBindingInspection.failed("BUSINESS_COMPONENT", "OWNER_CONTEXT_FORBIDDEN"));

        assertThrows(RuntimeException.class, () -> service.act(businessCommand(), actor()));

        org.mockito.Mockito.verifyNoInteractions(businessEvaluator, nativeProvider, acceptanceActivityCompletionFactApi);
        verify(evaluationMapper, never()).insertEvaluation(any());
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
    }

    private ProjectTaskExecutionContractDO allowBusinessAction(String type) {
        allowAction("PENDING_ACCEPT", "COMPLETE", "DONE");
        var contract = new ProjectTaskExecutionContractDO();
        contract.setId(91L); contract.setTenantId(0L); contract.setProjectTaskId(11L);
        contract.setContractVersion(2); contract.setWorkBindingTypeCode(type);
        contract.setTargetContextCode("SOL"); contract.setTargetObjectType("SITE_SURVEY");
        when(contractMapper.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        when(businessProvider.inspect(any())).thenReturn(new TaskBindingInspection(type, Set.of("COMPLETE"), "a".repeat(64), null));
        return contract;
    }

    private TaskActionCommand businessCommand() {
        return new TaskActionCommand(11L, 3, "complete", null, 91L, 2, null, null,
                null, null, "a".repeat(64), "key-business", "b".repeat(64));
    }

    private java.util.Map<String, Object> businessEvidence() {
        return java.util.Map.of("aggregateFactVersion", "a".repeat(64), "ownerContext", "SOL", "objectType", "SITE_SURVEY",
                "criteria", java.util.List.of(java.util.Map.of("criterion", "SURVEY_CONFIRMED", "satisfied", true)),
                "links", java.util.List.of(java.util.Map.of("linkId", 1L, "objectId", "survey-1", "factVersion", "survey:revision:7")));
    }

    @SuppressWarnings("unchecked")
    private void allowAction(String status, String action, String target) {
        ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
        task.setId(11L);
        task.setProjectId(100L);
        task.setTenantId(0L);
        task.setName("任务");
        task.setStageCode("S1");
        task.setStatus(status);
        task.setVersion(3);
        task.setStateMachineRevisionId(81L);
        when(taskMapper.selectTask(any())).thenReturn(task);
        when(taskMapper.selectTaskForAssignmentForUpdate(any())).thenReturn(task);
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(100L);
        project.setTenantId(0L);
        project.setLifecycleStatus("ACTIVE");
        project.setTaskTreeVersion(4L);
        project.setTaskProgressVersion(0L);
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);
        TaskStateTransitionDO transition = new TaskStateTransitionDO();
        transition.setFromStatusCode(status);
        transition.setActionCode(action);
        transition.setToStatusCode(target);
        when(stateMachineMapper.requireTransition(any())).thenReturn(transition);
        lenient().when(nativeProvider.inspect(any())).thenReturn(new TaskBindingInspection(
                "TASK_NATIVE", Set.of(action), "3:2:1", null));
        ProjectTaskExecutionContractDO contract = new ProjectTaskExecutionContractDO();
        contract.setId(91L);
        contract.setTenantId(0L);
        contract.setProjectTaskId(11L);
        contract.setWorkBindingTypeCode("TASK_NATIVE");
        contract.setCompletionRuleSnapshot("{\"requiredStatus\":\"DONE\"}");
        contract.setContractVersion(2);
        when(contractMapper.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        if ("COMPLETE".equals(action)) {
            lenient().when(taskMapper.selectNonTerminalDescendantIdsForUpdate(any()))
                    .thenReturn(java.util.List.of());
            lenient().when(taskMapper.selectNonTerminalPredecessorIdsForUpdate(any()))
                    .thenReturn(java.util.List.of());
        }
        if (Set.of("START", "SUBMIT").contains(action)) {
            ProjectTaskAssignmentDO assignment = new ProjectTaskAssignmentDO();
            assignment.setAssigneeUserId(9L);
            when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(assignment);
        } else {
            ProjectMemberAssignmentDO member = new ProjectMemberAssignmentDO();
            member.setMemberRole("PROJECT_MANAGER");
            lenient().when(memberMapper.selectActiveByUserForUpdate(any())).thenReturn(java.util.List.of(member));
        }
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<TaskCommandResult> operation = invocation.getArgument(3);
            Function<TaskCommandResult, PlatformCommandExecutionApi.SuccessFacts> factsFactory =
                    invocation.getArgument(4);
            TaskCommandResult result = operation.get();
            successFacts = factsFactory.apply(result);
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, result);
        });
    }

    private TaskActionCommand command(String action, int version, Long contractId, Integer contractVersion) {
        return new TaskActionCommand(11L, version, action, action.equals("cancel") ? "取消" : null,
                contractId, contractVersion, null, null, "key-" + action, "a".repeat(64));
    }

    private void allowAcceptanceAction() {
        allowAcceptanceAction("PENDING_ACCEPT", "COMPLETE", "DONE");
    }

    private void allowAcceptanceAction(String status, String action, String target) {
        allowAction(status, action, target);
        ProjectTaskExecutionContractDO contract = new ProjectTaskExecutionContractDO();
        contract.setId(91L);
        contract.setTenantId(0L);
        contract.setProjectTaskId(11L);
        contract.setWorkBindingTypeCode("BUSINESS_OBJECT");
        contract.setTargetContextCode("ACC");
        contract.setTargetObjectType("AcceptanceActivity");
        contract.setTargetObjectKey("51");
        contract.setContractVersion(2);
        when(contractMapper.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        lenient().when(permissionApi.hasAnyPermissions(9L, "pms:project-task:execute")).thenReturn(true);
        lenient().when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(100L, 1L, Set.of(100L), Set.of()));
    }

    private TaskActionCommand acceptanceCommand() {
        return new TaskActionCommand(11L, 3, "complete", null, 91L, 2,
                "51", null, 0, 1, "key-acceptance", "b".repeat(64));
    }

    private TaskWorkbenchActor actor() {
        return new TaskWorkbenchActor(0L, 9L, "corr-1");
    }
}
