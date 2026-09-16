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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class ProjectTaskLifecycleServiceTest {
    private final cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService stageAdmission =
            org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService.class);

    @Mock ProjectTaskRuntimeMapper taskMapper;
    @Mock ProjectTaskExecutionContractMapper contractMapper;
    @Mock ProjectTaskAssignmentMapper assignmentMapper;
    @Mock ProjectMemberAssignmentMapper memberMapper;
    @Mock ProjectTaskCompletionEvaluationMapper evaluationMapper;
    @Mock TaskStateMachineMapper stateMachineMapper;
    @Mock TaskNativeBindingHostProvider nativeProvider;
    @Mock PlatformCommandExecutionApi commandExecutionApi;
    @Mock OperationAuditApi operationAuditApi;
    @Mock ProjectTaskProgressService progressService;
    @Mock PermissionApi permissionApi;
    @Mock AcceptanceActivityCompletionFactApi acceptanceActivityCompletionFactApi;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectTaskPlanCompletionService businessEvaluator;
    @Mock TaskBusinessBindingHostProvider businessProvider;
    @Mock ProjectTaskApprovalService taskApprovals;
    @Mock cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService treeScopes;

    private ProjectTaskLifecycleService service;
    private PlatformCommandExecutionApi.SuccessFacts successFacts;

    @BeforeEach
    void setUp() {
        service = new ProjectTaskLifecycleService(taskMapper, contractMapper, assignmentMapper, memberMapper,
                evaluationMapper,
                stateMachineMapper, nativeProvider, commandExecutionApi, operationAuditApi, progressService,
                permissionApi, acceptanceActivityCompletionFactApi, projectScopeApi, businessEvaluator, businessProvider, treeScopes);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "stageAdmissionService", stageAdmission);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "timers", org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleTimerScheduler.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "taskApprovals", taskApprovals);
        var nodeExecutions = org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper.class);
        org.mockito.Mockito.lenient().when(nodeExecutions.recordTaskTransition(any())).thenReturn(1);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "nodeExecutions", nodeExecutions);
        org.mockito.Mockito.lenient().when(stageAdmission.taskMayStart(any(), any(), any())).thenReturn(true);
        lenient().when(businessEvaluator.evaluate(any(), any(), any(), any(), any())).thenReturn(planned(true,java.util.List.of(),java.util.Map.of()));
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PENDING_ASSIGN", "PENDING_START"})
    void admittedTaskStartsUsingFrozenMachineWithoutGrantingBusinessPermissions(String status) {
        var project = new ProjectMasterDO(); project.setId(100L); project.setTenantId(0L);
        project.setTaskTreeVersion(1L);
        var task = new ProjectTaskInstanceDO(); task.setId(11L); task.setStatus(status);
        task.setVersion(3); task.setStateMachineRevisionId(81L);
        var contract = new ProjectTaskExecutionContractDO(); contract.setId(91L);
        var transition = new TaskStateTransitionDO(); transition.setToStatusCode("IN_PROGRESS");
        when(stateMachineMapper.requireTransition(any())).thenReturn(transition);
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);
        assertTrue(service.startAdmittedTask(project, task, contract, "automatic-start"));
        verify(stateMachineMapper).requireTransition(argThat(q -> "PENDING_START".equals(q.fromStatusCode())
                && "START".equals(q.actionCode())));
        verify(taskMapper).updateLifecycleIfMatch(argThat(q -> q.initializeActualStartTime()
                && "IN_PROGRESS".equals(q.nextStatus())));
        org.mockito.Mockito.verifyNoInteractions(permissionApi, taskApprovals, businessProvider);
        task.setStatus("IN_PROGRESS");
        assertFalse(service.startAdmittedTask(project, task, contract, "repeat"));
        verify(taskMapper, org.mockito.Mockito.times(1)).updateLifecycleIfMatch(any());
    }

    @Test void anUnsatisfiedTaskAdmissionCannotBeBypassedByTheLifecycleEndpoint() {
        allowAction("PENDING_START", "START", "IN_PROGRESS");
        when(stageAdmission.taskMayStart(any(), any(), any())).thenReturn(false);
        assertThrows(RuntimeException.class, () -> service.act(command("start", 3, null, null), actor()));
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
    }

    @Test void approvalStartsAfterTheTaskRoundTransitionAndBeforeCommandSuccess() {
        allowAction("PENDING_START", "START", "IN_PROGRESS");
        var binding = contractMapper.selectCurrentByTaskIdForUpdate(null);
        binding.setWorkBindingTypeCode("APPROVAL");
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);
        assertEquals("IN_PROGRESS", service.act(command("start",3,91L,2),actor()).status());
        var order = org.mockito.Mockito.inOrder(taskMapper,taskApprovals);
        order.verify(taskMapper).updateLifecycleIfMatch(any());
        order.verify(taskApprovals).start(0L,100L,11L,binding,9L,"START:key-start",null);
        assertEquals("PROJECT_TASK_START",successFacts.operationCode());
    }
    @Test void runningApprovalCannotBeAbandonedByClosingTheTask() {
        allowAction("IN_PROGRESS", "CANCEL", "CLOSED");
        var binding = contractMapper.selectCurrentByTaskIdForUpdate(null);
        binding.setWorkBindingTypeCode("APPROVAL");
        org.mockito.Mockito.doThrow(new IllegalStateException("running approval")).when(taskApprovals)
                .requireMayCancel(0L,100L,11L,binding);
        assertThrows(IllegalStateException.class, () -> service.act(command("cancel",3,null,null),actor()));
        verify(taskMapper,never()).updateLifecycleIfMatch(any());
    }

    @Test void approvalSubmissionDoesNotRestartTaskOrWriteAnotherExecutionRound() {
        var binding = approvalHandling();
        var submission = new cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Submission(
                java.util.Map.of("comment","private-form"),java.util.Map.of("review",java.util.List.of(8L)));
        var receipt = new cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Fact(
                cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Outcome.NOT_SATISFIED,
                "RUNNING","attempt-2","review:1",null);
        when(taskApprovals.start(0L,100L,11L,binding,9L,"APPROVAL:retry",submission)).thenReturn(receipt);
        var command = new TaskActionCommand(11L,3,"approval",null,91L,2,null,null,null,null,null,"retry","a".repeat(64),submission);
        var result = service.act(command,actor());
        assertEquals("IN_PROGRESS",result.status()); assertEquals(3,result.taskVersion());
        assertEquals("PROJECT_TASK_APPROVAL",successFacts.operationCode());
        verify(taskMapper,never()).updateLifecycleIfMatch(any());
        org.mockito.Mockito.verifyNoInteractions(stateMachineMapper,stageAdmission);
        var rounds = (cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper)
                org.springframework.test.util.ReflectionTestUtils.getField(service,"nodeExecutions");
        verify(rounds,never()).recordTaskTransition(any());
        assertFalse(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(successFacts).contains("private-form"));
    }
    @Test void currentTaskExecutionPermissionIsStillRequiredForAnotherApprovalAttempt() {
        approvalHandling();
        when(nativeProvider.inspect(any())).thenReturn(TaskBindingInspection.failed("APPROVAL","FORBIDDEN"));
        assertThrows(RuntimeException.class,() -> service.act(command("approval",3,91L,2),actor()));
        org.mockito.Mockito.verifyNoInteractions(taskApprovals);
        verify(taskMapper,never()).updateLifecycleIfMatch(any());
    }
    @Test void anApprovalFormFromAnOldBindingCannotBeSubmittedToTheCurrentTask() {
        approvalHandling();
        assertThrows(RuntimeException.class,() -> service.act(command("approval",3,90L,1),actor()));
        org.mockito.Mockito.verifyNoInteractions(taskApprovals);
    }
    @Test void approvalEngineErrorsAreNotCopiedWithFormValuesIntoRejectionAudit() {
        approvalHandling();
        when(taskApprovals.start(any(),any(),any(),any(),any(),any(),any())).thenThrow(new IllegalStateException("private-form-value"));
        assertThrows(IllegalStateException.class,() -> service.act(command("approval",3,91L,2),actor()));
        verify(operationAuditApi).record(eq(0L),eq(9L),any(),eq("PROJECT_TASK_ACTION"),eq("ProjectTask"),eq("11"),eq("REJECTED"),
                argThat(detail -> "TASK_APPROVAL_ACTION_FAILED".equals(detail.get("failureCode"))));
    }
    private ProjectTaskExecutionContractDO approvalHandling() {
        allowAction("IN_PROGRESS","START","IN_PROGRESS");
        org.mockito.Mockito.reset(stateMachineMapper);
        org.mockito.Mockito.reset(assignmentMapper);
        var assignment = new ProjectTaskAssignmentDO(); assignment.setAssigneeUserId(9L);
        lenient().when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(assignment);
        var binding = contractMapper.selectCurrentByTaskIdForUpdate(null); binding.setWorkBindingTypeCode("APPROVAL");
        lenient().when(nativeProvider.inspect(any())).thenReturn(new TaskBindingInspection("APPROVAL",Set.of("APPROVAL"),"3:2:1",null));
        return binding;
    }

    @Test void ownerPermissionDenialPrecedesRuleFactReads() {
        allowAction("PENDING_START", "START", "IN_PROGRESS");
        when(nativeProvider.inspect(any())).thenReturn(TaskBindingInspection.failed("TASK_NATIVE", "FORBIDDEN"));
        assertThrows(RuntimeException.class, () -> service.act(command("start", 3, null, null), actor()));
        org.mockito.Mockito.verifyNoInteractions(stageAdmission);
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
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
    void superAdminMayOperateWithoutCreatingAnAssignment() {
        allowAction("PENDING_START", "START", "IN_PROGRESS");
        when(treeScopes.isTenantSuperAdmin(0L, 9L)).thenReturn(true);
        org.mockito.Mockito.reset(assignmentMapper);
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);
        assertEquals("IN_PROGRESS", service.act(command("start", 3, null, null), actor()).status());
        verify(memberMapper, never()).selectActiveByUserForUpdate(any());
        verify(assignmentMapper, never()).insertAssignment(any());
    }

    @Test
    void unassignedTeamMemberCanStartWithoutManufacturingAnAssignment() {
        allowAction("PENDING_ASSIGN", "START", "IN_PROGRESS");
        when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(null);
        var member = new ProjectMemberAssignmentDO(); member.setProjectId(100L); member.setMemberRole("TEAM_MEMBER");
        when(memberMapper.selectActiveByUserForUpdate(any())).thenReturn(java.util.List.of(member));
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);
        assertEquals("IN_PROGRESS",service.act(command("start",3,null,null),actor()).status());
        verify(stateMachineMapper).requireTransition(org.mockito.ArgumentMatchers.argThat(q ->
                q.revisionId().equals(81L) && q.fromStatusCode().equals("PENDING_START") && q.actionCode().equals("START")));
        var update = ArgumentCaptor.forClass(TaskLifecycleStateUpdate.class);
        verify(taskMapper).updateLifecycleIfMatch(update.capture());
        assertEquals("PENDING_ASSIGN", update.getValue().expectedStatus());
        assertTrue(successFacts.detailSnapshot().contains("\"transitionFromStatus\":\"PENDING_START\""));
        verify(assignmentMapper,never()).insertAssignment(any());
    }

    @Test void directStartFailsIfFrozenStartDefinitionIsMissing() {
        allowAction("PENDING_ASSIGN", "START", "IN_PROGRESS");
        when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(null);
        when(stateMachineMapper.requireTransition(any())).thenThrow(new IllegalArgumentException("missing"));
        assertThrows(RuntimeException.class, () -> service.act(command("start",3,null,null),actor()));
        verify(stateMachineMapper).requireTransition(argThat(query -> "PENDING_START".equals(query.fromStatusCode())));
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
        verify(assignmentMapper, never()).insertAssignment(any());
    }

    @Test void newlyDesignatedPendingTaskDoesNotUseUnassignedStartRule() {
        allowAction("PENDING_ASSIGN", "START", "IN_PROGRESS");
        when(stateMachineMapper.requireTransition(any())).thenAnswer(invocation -> {
            var q = invocation.getArgument(0, cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateTransitionQuery.class);
            assertEquals("PENDING_ASSIGN", q.fromStatusCode());
            throw new IllegalArgumentException("no direct assigned transition");
        });
        assertThrows(RuntimeException.class, () -> service.act(command("start",3,null,null),actor()));
        verify(stateMachineMapper).requireTransition(argThat(query -> "PENDING_ASSIGN".equals(query.fromStatusCode())));
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
    }

    @Test void aProjectManagerCannotExecuteForAnotherDesignatedPerson() {
        allowAction("PENDING_START", "START", "IN_PROGRESS");
        var assignment = new ProjectTaskAssignmentDO(); assignment.setAssigneeUserId(22L);
        when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(assignment);
        var member = new ProjectMemberAssignmentDO(); member.setProjectId(100L); member.setMemberRole("PROJECT_MANAGER");
        when(memberMapper.selectActiveByUserForUpdate(any())).thenReturn(java.util.List.of(member));
        assertThrows(RuntimeException.class,()->service.act(command("start",3,null,null),actor()));
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
    }

    @Test
    void superAdminStillNeedsOwnerCompletionFacts() {
        allowBusinessAction("BUSINESS_OBJECT");
        when(treeScopes.isTenantSuperAdmin(0L, 9L)).thenReturn(true);
        when(businessEvaluator.evaluate(any(), any(), any(), any(), any())).thenReturn(
                planned(false, java.util.List.of("OWNER_NOT_COMPLETE"), businessEvidence()));
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
    void unfinishedStartedDescendantPersistsEvaluationWithoutAdvancingTask() {
        allowAction("PENDING_ACCEPT", "COMPLETE", "DONE");
        when(taskMapper.selectUnfinishedStartedDescendantIdsForUpdate(any())).thenReturn(java.util.List.of(12L));
        when(evaluationMapper.insertEvaluation(any())).thenReturn(1);

        TaskCommandResult result = service.act(command("complete", 3, 91L, 2), actor());

        assertEquals("PENDING_ACCEPT", result.status());
        assertEquals(3, result.taskVersion());
        verify(evaluationMapper).insertEvaluation(any());
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
        assertEquals(null, successFacts.eventType());
        assertTrue(successFacts.detailSnapshot().contains("UNFINISHED_STARTED_DESCENDANT"));
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
        var evidence = new java.util.LinkedHashMap<String, Object>(businessEvidence());
        var gate = planned(true, java.util.List.of(), java.util.Map.of()).completion();
        evidence.put("gate", gate);
        evidence.put("gateSnapshot", "SURVEY_READY:PASSED:4");
        when(businessEvaluator.evaluate(any(), any(), any(), any(), any())).thenReturn(
                planned(true, java.util.List.of(), evidence));
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
        var executionMapper = (cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper)
                org.springframework.test.util.ReflectionTestUtils.getField(service, "nodeExecutions");
        var transition = ArgumentCaptor.forClass(
                cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper.TaskTransition.class);
        verify(executionMapper).recordTaskTransition(transition.capture());
        var recorded = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(transition.getValue().evidence());
        assertEquals(gate, cn.iocoder.yudao.framework.common.util.json.JsonUtils.convertObject(recorded.path("gate"),
                cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation.class));
        assertEquals("SURVEY_READY:PASSED:4", recorded.path("gateSnapshot").asText());
        verify(nativeProvider, never()).inspect(any());
        verify(acceptanceActivityCompletionFactApi, never()).lockAndComplete(any());
        var order = org.mockito.Mockito.inOrder(contractMapper, businessEvaluator, evaluationMapper, taskMapper);
        order.verify(contractMapper).selectCurrentByTaskIdForUpdate(any());
        order.verify(businessEvaluator).evaluate(any(), any(), any(), any(), any());
        order.verify(evaluationMapper).insertEvaluation(any());
        order.verify(taskMapper).updateLifecycleIfMatch(any());
    }

    @Test
    void emptyBusinessGroupPersistsFailureAndNeverUpdatesProgressOrOutbox() {
        allowBusinessAction("BUSINESS_OBJECT");
        when(businessEvaluator.evaluate(any(), any(), any(), any(), any())).thenReturn(
                planned(false, java.util.List.of("BUSINESS_LINK_GROUP_EMPTY"), businessEvidence()));
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
        when(businessEvaluator.evaluate(any(), any(), any(), any(), any())).thenReturn(
                planned(false, java.util.List.of("GATE_NOT_PASSED"), businessEvidence()));
        when(taskMapper.selectUnfinishedStartedDescendantIdsForUpdate(any())).thenReturn(java.util.List.of(12L));
        when(evaluationMapper.insertEvaluation(any())).thenReturn(1);

        assertEquals("PENDING_ACCEPT", service.act(businessCommand(), actor()).status());

        for (String code : java.util.List.of("UNFINISHED_STARTED_DESCENDANT", "GATE_NOT_PASSED")) {
            assertTrue(successFacts.detailSnapshot().contains(code));
        }
        assertEquals(null, successFacts.eventType());
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
    }

    @Test
    void staleLockedBusinessVersionAbortsBeforeAnySuccessWrites() {
        allowBusinessAction("BUSINESS_OBJECT");
        when(businessEvaluator.evaluate(any(), any(), any(), any(), any())).thenThrow(
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

    @Test void unknownPlanConditionsAreRecordedAsUnknownAndCannotAdvanceTask() {
        allowAction("PENDING_ACCEPT", "COMPLETE", "DONE");
        var unknown = new cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation("plan:51",
                cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation.Outcome.UNKNOWN,"FACT_UNAVAILABLE",
                java.util.List.of(),java.util.List.of(),java.util.List.of());
        when(businessEvaluator.evaluate(any(),any(),any(),any(),any())).thenReturn(new ProjectTaskPlanCompletionService.Result(
                unknown,unknown,java.util.Map.of("completion",unknown,"exit",unknown)));
        when(evaluationMapper.insertEvaluation(any())).thenReturn(1);
        assertEquals("PENDING_ACCEPT",service.act(command("complete",3,91L,2),actor()).status());
        verify(evaluationMapper).insertEvaluation(org.mockito.ArgumentMatchers.argThat(value -> "UNKNOWN".equals(value.getEvaluationResultCode())));
        verify(taskMapper,never()).updateLifecycleIfMatch(any());
    }

    private ProjectTaskPlanCompletionService.Result planned(boolean matched, java.util.List<String> unmet, java.util.Map<String,Object> evidence) {
        var evaluation = new cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation("plan:51",
                matched ? cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation.Outcome.MATCHED
                        : cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation.Outcome.NOT_MATCHED,
                unmet.isEmpty() ? null : unmet.getFirst(),java.util.List.of(),java.util.List.of(),java.util.List.of());
        return new ProjectTaskPlanCompletionService.Result(evaluation,evaluation,evidence);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"IN_PROGRESS,BUSINESS_OBJECT", "PENDING_ACCEPT,BUSINESS_OBJECT", "PENDING_ACCEPT,TASK_NATIVE", "IN_PROGRESS,APPROVAL"})
    @SuppressWarnings("unchecked")
    void submittedOrBusinessResultCompletesThroughFrozenStateMachineOnceWithoutUserActions(String status, String bindingType) {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(0L);
        try {
            var task = automaticTask(status);
            contractMapper.selectCurrentByTaskIdForUpdate(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentTaskExecutionContractLockQuery(0L,11L))
                    .setWorkBindingTypeCode(bindingType);
            var transition = new TaskStateTransitionDO(); transition.setToStatusCode("DONE");
            when(stateMachineMapper.requireTransition(any())).thenAnswer(call -> {
                var query = (cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateTransitionQuery) call.getArgument(0);
                if ("SUBMIT".equals(query.actionCode())) {
                    var submitted = new TaskStateTransitionDO(); submitted.setToStatusCode("PENDING_ACCEPT"); return submitted;
                }
                return transition;
            });
            when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);
            when(evaluationMapper.insertEvaluation(any())).thenReturn(1);
            when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(call -> {
                var scope = (PlatformCommandExecutionApi.IdempotencyScope) call.getArgument(0);
                assertEquals(0L, scope.actorId()); assertEquals("rule-complete:61", scope.key());
                var result = ((Supplier<TaskCommandResult>) call.getArgument(3)).get();
                successFacts = ((Function<TaskCommandResult, PlatformCommandExecutionApi.SuccessFacts>) call.getArgument(4)).apply(result);
                return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, result);
            });
            assertTrue(service.completeFromBusinessResult(100L, 11L, "outbox").completed());
            verify(taskMapper).updateLifecycleIfMatch(org.mockito.ArgumentMatchers.argThat(update ->
                    status.equals(update.expectedStatus()) && "DONE".equals(update.nextStatus())
                            && update.expectedVersion()==3 && update.progress()==100 && update.setActualEndTime()));
            assertEquals("TaskCompleted", successFacts.eventType());
            var executionMapper = (cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper)
                    org.springframework.test.util.ReflectionTestUtils.getField(service, "nodeExecutions");
            verify(executionMapper).recordTaskTransition(argThat(update ->
                    cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(update.evidence())
                            .path("businessFacts").path("results").path(0).path("facts").path("SURVEY_CONFIRMED").asBoolean()));
            assertEquals(1, successFacts.businessEvents().size());
            org.mockito.Mockito.verifyNoInteractions(permissionApi, nativeProvider, businessProvider, acceptanceActivityCompletionFactApi);
            task.setStatus("DONE");
            assertFalse(service.completeFromBusinessResult(100L, 11L, "duplicate-event").completed());
            verify(taskMapper, org.mockito.Mockito.times(1)).updateLifecycleIfMatch(any());
        } finally { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
    }

    @Test void automaticCompletionNeverDiscardsStartedChildWork() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(0L);
        try {
            automaticTask("PENDING_ACCEPT");
            when(taskMapper.selectUnfinishedStartedDescendantIdsForUpdate(any())).thenReturn(java.util.List.of(12L));
            assertFalse(service.completeFromBusinessResult(100L, 11L, "outbox").completed());
            verify(taskMapper, never()).updateLifecycleIfMatch(any());
            verify(commandExecutionApi, never()).execute(any(), any(), any(), any(), any());
        } finally { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
    }

    private ProjectTaskInstanceDO automaticTask(String status) {
        var task = new ProjectTaskInstanceDO(); task.setId(11L); task.setProjectId(100L); task.setTenantId(0L);
        task.setName("需求分析"); task.setStageCode("PREP_WORK"); task.setStatus(status); task.setVersion(3); task.setStateMachineRevisionId(81L);
        var project = new ProjectMasterDO(); project.setId(100L); project.setTenantId(0L); project.setLifecycleStatus("ACTIVE");
        project.setTaskProgressVersion(0L); project.setTaskTreeVersion(4L); project.setActivePlanVersionId(51L);
        var contract = new ProjectTaskExecutionContractDO(); contract.setId(91L); contract.setTenantId(0L);
        contract.setProjectTaskId(11L); contract.setContractVersion(2); contract.setWorkBindingTypeCode("BUSINESS_OBJECT");
        contract.setTargetContextCode("SOL"); contract.setTargetObjectType("REQUIREMENT_ANALYSIS");
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(taskMapper.selectTaskForAssignmentForUpdate(any())).thenReturn(task);
        when(contractMapper.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        var evidence = new java.util.LinkedHashMap<String,Object>(businessEvidence()); evidence.put("executionId",61L); evidence.put("planVersionId",51L);
        evidence.put("businessFacts", new cn.iocoder.yudao.module.pms.project.domain.rule.BusinessFactEvidence(61L, 51L,
                java.util.List.of(new cn.iocoder.yudao.module.pms.project.domain.rule.BusinessFactEvidence.Result(1L, "survey-1", "survey:revision:7",
                        java.util.Map.of("SURVEY_CONFIRMED", true)))));
        var evaluated = planned(true,java.util.List.of(),evidence);
        evidence.put("completion",evaluated.completion()); evidence.put("exit",evaluated.exit());
        when(businessEvaluator.evaluateAutomatically(project, task, contract)).thenReturn(evaluated);
        return task;
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
        contract.setCompletionRuleTypeCode("TASK_NATIVE_STATUS");
        contract.setCompletionRuleSnapshot("{\"requiredStatus\":\"DONE\"}");
        contract.setContractVersion(2);
        when(contractMapper.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        if ("COMPLETE".equals(action)) {
            lenient().when(taskMapper.selectUnfinishedStartedDescendantIdsForUpdate(any()))
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
