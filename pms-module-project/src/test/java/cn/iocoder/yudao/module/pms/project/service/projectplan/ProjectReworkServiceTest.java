package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectStageExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphFreezer;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.*;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskProgressService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectReworkServiceTest {
    final ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final ProjectMasterMapper projectRows=mock(ProjectMasterMapper.class);
    final ProjectTaskRuntimeMapper tasks=mock(ProjectTaskRuntimeMapper.class);
    final ProjectPlanVersionMapper plans=mock(ProjectPlanVersionMapper.class);
    final ProjectNodeExecutionMapper executions=mock(ProjectNodeExecutionMapper.class);
    final ProjectRuntimeGraphMapper graph=mock(ProjectRuntimeGraphMapper.class);
    final ProjectTaskExecutionContractMapper contracts=mock(ProjectTaskExecutionContractMapper.class);
    final ProjectStageExecutionContractMapper stageContracts=mock(ProjectStageExecutionContractMapper.class);
    final ProjectPlanProjectionMapper projections=mock(ProjectPlanProjectionMapper.class);
    final ProjectTaskAssignmentMapper assignments=mock(ProjectTaskAssignmentMapper.class);
    final TaskStateMachineMapper states=mock(TaskStateMachineMapper.class);
    final ProjectStageInstanceMapper stages=mock(ProjectStageInstanceMapper.class);
    final ProjectReworkMapper changes=mock(ProjectReworkMapper.class);
    final ProjectTaskProgressService progress=mock(ProjectTaskProgressService.class);
    final PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    final ProjectReworkService service=new ProjectReworkService(scopes,permissions,projectRows,tasks,plans,executions,graph,
            contracts,stageContracts,projections,new TaskExecutionContractFactory(),new ProjectRuntimeGraphFreezer(graph,stageContracts),
            assignments,states,stages,changes,new ProjectReworkPlanner(new ProjectPlanImpactAnalyzer()),progress,commands,
            mock(cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleTimerScheduler.class));
    ProjectMasterDO project;
    ProjectNodeExecutionDO previous;
    List<ProjectNodeExecutionDO> rounds;
    PlatformCommandExecutionApi.SuccessFacts facts;
    ProjectPlanVersionDO effective;
    TemplateExecutionSnapshot snapshot;
    ProjectTaskExecutionContractDO oldContract;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        var currentStages = mock(ProjectCurrentStageService.class);
        when(currentStages.synchronize(100L)).thenReturn(4);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "currentStages", currentStages);
        TenantContextHolder.setTenantId(1L);
        when(permissions.hasAnyPermissions(9L,ProjectReworkService.PERMISSION)).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L,1L,Set.of(100L),Set.of()));
        project=new ProjectMasterDO(); project.setId(100L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE");
        project.setActivePlanVersionId(50L); project.setVersion(3); project.setTaskProgressVersion(0L);
        when(projectRows.selectById(100L)).thenReturn(project); when(tasks.selectProjectForCommandForUpdate(any())).thenReturn(project);
        snapshot=ProjectReworkPlannerTest.snapshot();
        var rule=JsonUtils.parseTree("{\"predicate\":\"TASK_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}");
        snapshot.getStages().getFirst().setCompletionRule(rule);
        for (var node : snapshot.getTasks()) {
            var binding=new TemplateExecutionSnapshot.BindingContract(); binding.setType("TASK_NATIVE"); node.setBinding(binding);
            var permission=new TemplateExecutionSnapshot.PermissionContract(); permission.setPolicyRef("CURRENT_PLAN_POLICY"); node.setPermission(permission);
            node.setCompletionRule(rule);
        }
        effective=new ProjectPlanVersionDO(); effective.setId(50L); effective.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        when(plans.selectEffective(any())).thenReturn(effective);
        previous=ProjectReworkPlannerTest.round("t1","TASK",2L,"DONE"); previous.setResultSnapshot("immutable-result");
        previous.setSubmittedAt(java.time.LocalDateTime.of(2026,9,14,9,0)); previous.setSubmissionNote("old evidence"); previous.setContractId(31L);
        rounds=List.of(ProjectReworkPlannerTest.round("s","STAGE",1L,"ACTIVE"),previous,ProjectReworkPlannerTest.round("t2","TASK",3L,"DONE"));
        when(executions.selectCurrent(any())).thenReturn(rounds); when(executions.selectCurrentForUpdate(any())).thenReturn(rounds);
        var task=new ProjectTaskInstanceDO(); task.setId(2L); task.setStageCode("PREP"); task.setStatus("DONE"); task.setVersion(5); task.setStateMachineRevisionId(61L);
        when(graph.selectTasks(any())).thenReturn(List.of(task)); when(graph.selectTasksForUpdate(any())).thenReturn(List.of(task));
        oldContract=new ProjectTaskExecutionContractDO(); oldContract.setId(31L); oldContract.setSourceNodeKey("t1");
        oldContract.setContractVersion(2); oldContract.setVersion(4); oldContract.setApprovalInstanceId(77L);
        oldContract.setPermissionPolicyRef("OLD_POLICY");
        when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(oldContract);
        when(contracts.insert(any(ProjectTaskExecutionContractDO.class))).thenReturn(1);
        when(stageContracts.insert(any(ProjectStageExecutionContractDO.class))).thenReturn(1);
        when(projections.closeTaskContract(any())).thenReturn(1); when(projections.closeStageContract(any())).thenReturn(1);
        var assigned=new TaskStateTransitionDO(); assigned.setToStatusCode("PENDING_START"); when(states.requireTransition(any())).thenReturn(assigned);
        when(changes.resetTaskProjection(any())).thenReturn(1); when(changes.retireEndedExecution(any())).thenReturn(1);
        when(changes.advanceProjectVersion(any())).thenReturn(1); when(executions.insert(any(ProjectNodeExecutionDO.class))).thenReturn(1);
        when(commands.execute(any(),any(),any(),any(),any())).thenAnswer(call -> {
            var result=((Supplier<ProjectReworkService.Result>) call.getArgument(3)).get();
            facts=((Function<ProjectReworkService.Result,PlatformCommandExecutionApi.SuccessFacts>)call.getArgument(4)).apply(result);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,result);
        });
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void previewIsReadOnlyAndOnlySelectedEndedTaskGetsANewEmptyExecution() {
        var preview=service.preview(100L,List.of("t1"),9L);
        assertTrue(preview.plan().applicable()); assertEquals(1,preview.plan().targets().size());
        verifyNoInteractions(changes,commands);
        var result=service.apply(command(3,2L),9L,"intent");
        assertEquals(4,result.projectVersion()); assertEquals(2,result.executions().getFirst().roundNo());
        var created=ArgumentCaptor.forClass(ProjectNodeExecutionDO.class); verify(executions).insert(created.capture());
        assertEquals(50L,created.getValue().getPlanVersionId()); assertEquals(2L,created.getValue().getNodeInstanceId());
        assertEquals("PENDING",created.getValue().getStatus()); assertNull(created.getValue().getSubmittedAt());
        assertNull(created.getValue().getSubmissionNote()); assertNull(created.getValue().getResultSnapshot());
        var frozen=ArgumentCaptor.forClass(ProjectTaskExecutionContractDO.class); verify(contracts).insert(frozen.capture());
        assertEquals(frozen.getValue().getId(),created.getValue().getContractId());
        assertNotEquals(31L,created.getValue().getContractId()); assertEquals(3,frozen.getValue().getContractVersion());
        assertEquals("CURRENT_PLAN_POLICY",frozen.getValue().getPermissionPolicyRef()); assertNull(frozen.getValue().getApprovalInstanceId());
        assertEquals(77L,oldContract.getApprovalInstanceId()); assertEquals("OLD_POLICY",oldContract.getPermissionPolicyRef());
        assertNull(oldContract.getEffectiveTo()); // Persistence closes the validity interval, never overwrites the old payload.
        verify(projections).closeTaskContract(argThat(row -> row.tenantId()==1L && row.projectId()==100L
                && row.nodeInstanceId()==2L && row.contractId()==31L && row.expectedVersion()==4));
        assertEquals("immutable-result",previous.getResultSnapshot()); assertEquals("DONE",previous.getStatus());
        verify(changes).resetTaskProjection(argThat(reset -> reset.taskId()==2L && "PENDING_ASSIGN".equals(reset.initialStatus())));
        verifyNoInteractions(stages);
        assertEquals("ProjectRuleReevaluationRequested",facts.businessEvents().getFirst().eventType());
        assertTrue(facts.detailSnapshot().contains("返工原因"));
    }

    @Test void currentAssignmentIsRetainedButNoOldCompletionStateIsCopied() {
        when(assignments.selectCurrentForUpdate(any())).thenReturn(new ProjectTaskAssignmentDO());
        service.apply(command(3,2L),9L,"assigned");
        verify(changes).resetTaskProjection(argThat(reset -> "PENDING_START".equals(reset.initialStatus())));
    }

    @Test void gateImpactPreviewDoesNotExpandTheAppliedReworkOrOverwriteUnselectedResults() {
        var gate = new TemplateExecutionSnapshot.GateContract(); gate.setNodeKey("g"); gate.setCode("SURVEY_READY");
        gate.setStageCode("PREP"); gate.setConditionRuleKey("survey-gate"); snapshot.getGates().add(gate);
        snapshot.getRulePrograms().put("survey-gate", new cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler()
                .compile(JsonUtils.parseTree("{\"predicate\":\"TASK\",\"parameters\":{\"refCode\":\"SURVEY\"}}")));
        snapshot.getTasks().get(1).setGateRef("SURVEY_READY");
        effective.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        rounds.get(2).setResultSnapshot("unselected-analysis-result");
        String history = JsonUtils.toJsonString(rounds.get(2));
        var preview = service.preview(100L, List.of("t1"), 9L);
        assertEquals(Set.of("g", "t2"), preview.plan().affectedNodeKeys());
        verifyNoInteractions(changes, commands);
        var result = service.apply(command(3, 2L), 9L, "source-only");
        assertEquals(List.of("t1"), result.executions().stream().map(ProjectReworkService.NewExecution::nodeKey).toList());
        verify(changes).retireEndedExecution(argThat(change -> change.executionId().equals(2L)));
        verify(executions, times(1)).insert(any(ProjectNodeExecutionDO.class));
        assertEquals(history, JsonUtils.toJsonString(rounds.get(2)));
        assertEquals("immutable-result", previous.getResultSnapshot());
    }

    @Test void requiredEndedParentGetsOneNewRoundAndCannotBeOmittedFromPreviewTokens() {
        rounds.getFirst().setStatus("DONE");
        assertThrows(RuntimeException.class,()->service.apply(command(3,2L),9L,"missing-parent-token"));
        verifyNoInteractions(changes);
        var stage=new ProjectStageInstanceDO(); stage.setId(1L); stage.setCode("PREP"); stage.setStatus("DONE"); stage.setVersion(7); stage.setGraphVersion(1L);
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(stage));
        var binding=new cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO();
        binding.setId(101L); binding.setStageId(1L); binding.setSourceNodeKey("s"); binding.setGraphVersion(1L);
        binding.setBindingVersion(3); binding.setVersion(2); binding.setPermissionSnapshot("{\"policyRef\":\"OLD_STAGE\"}");
        rounds.getFirst().setContractId(101L);
        when(graph.selectContracts(any())).thenReturn(List.of(binding)); when(stages.updateStatusIfMatch(any())).thenReturn(1);
        var request=new ProjectReworkService.Apply(100L,50L,3,List.of("t1"),List.of(
                new ProjectReworkService.ExpectedExecution("s",1L,1),new ProjectReworkService.ExpectedExecution("t1",2L,1)),"必要阶段返工");
        var result=service.apply(request,9L,"with-parent");
        assertEquals(2,result.executions().size());
        verify(stages).updateStatusIfMatch(argThat(update -> update.stageId()==1L && "PENDING".equals(update.targetStatus())));
        var created=ArgumentCaptor.forClass(ProjectNodeExecutionDO.class); verify(executions,times(2)).insert(created.capture());
        var frozen=ArgumentCaptor.forClass(ProjectStageExecutionContractDO.class); verify(stageContracts).insert(frozen.capture());
        assertTrue(created.getAllValues().stream().anyMatch(row -> "STAGE".equals(row.getNodeKind()) && Objects.equals(row.getContractId(),frozen.getValue().getId())));
        assertNotEquals(101L,frozen.getValue().getId()); assertEquals(4,frozen.getValue().getBindingVersion());
        assertEquals("STAGE_NATIVE",frozen.getValue().getBindingType()); assertEquals("null",frozen.getValue().getPermissionSnapshot());
        assertEquals(JsonUtils.toJsonString(snapshot),frozen.getValue().getDefinitionSnapshot());
        assertEquals("{\"policyRef\":\"OLD_STAGE\"}",binding.getPermissionSnapshot());
        verify(projections).closeStageContract(argThat(row -> row.nodeInstanceId()==1L && row.contractId()==101L && row.expectedVersion()==2));
        assertEquals("DONE",rounds.get(2).getStatus()); assertEquals(1,rounds.get(2).getCurrentMarker());
    }

    @Test void stalePreviewClosedProjectOrRunningSelectionCannotMutateState() {
        assertThrows(RuntimeException.class,()->service.apply(command(2,2L),9L,"old-project"));
        assertThrows(RuntimeException.class,()->service.apply(command(3,999L),9L,"old-execution"));
        previous.setStatus("ACTIVE");
        assertThrows(RuntimeException.class,()->service.apply(command(3,2L),9L,"running"));
        project.setLifecycleStatus("NORMAL_CLOSED");
        assertThrows(RuntimeException.class,()->service.apply(command(3,2L),9L,"closed"));
        verifyNoInteractions(changes);
    }

    @Test void replayDoesNotCreateAnotherRoundAndRevokedPermissionCannotReadReplay() {
        var original=new ProjectReworkService.Result(100L,50L,4,List.of(new ProjectReworkService.NewExecution("t1",2L,88L,2)));
        doReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,original))
                .when(commands).execute(any(),any(),any(),any(),any());
        assertEquals(original,service.apply(command(3,2L),9L,"replay")); verifyNoInteractions(changes);
        when(permissions.hasAnyPermissions(9L,ProjectReworkService.PERMISSION)).thenReturn(false);
        assertThrows(RuntimeException.class,()->service.apply(command(3,2L),9L,"replay"));
        verify(commands,times(1)).execute(any(),any(),any(),any(),any());
    }

    @Test void emptyProjectScopeFailsBeforeReadingRuntime() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class,()->service.get(100L,9L));
        verifyNoInteractions(projectRows,changes,commands);
    }

    @Test void aCompletedRoundFromAnOlderPlanUsesTheEffectivePlansBusinessBinding() {
        previous.setPlanVersionId(40L);
        var binding=snapshot.getTasks().getFirst().getBinding(); binding.setType("BUSINESS_OBJECT");
        binding.setTargetContextCode("SOL"); binding.setTargetObjectType("RequirementAnalysis"); binding.setTargetObjectKey("project");
        binding.setParameters(JsonUtils.parseTree("{\"dynamicFormRevisionId\":92}"));
        effective.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        String history=JsonUtils.toJsonString(previous);
        service.apply(command(3,2L),9L,"after-plan-change");
        verify(contracts).insert(argThat((ProjectTaskExecutionContractDO row) -> "BUSINESS_OBJECT".equals(row.getWorkBindingTypeCode())
                && "SOL".equals(row.getTargetContextCode()) && "RequirementAnalysis".equals(row.getTargetObjectType())
                && row.getBindingParameterSnapshot().contains("92") && row.getApprovalInstanceId()==null));
        assertEquals(history,JsonUtils.toJsonString(previous));
        verify(executions).insert(argThat((ProjectNodeExecutionDO row) -> row.getPlanVersionId()==50L && row.getRoundNo()==2));
    }

    @Test void reworkKeepsTheEffectiveApprovalDefinitionPinButNeverTheOldApprovalInstance() {
        var binding = snapshot.getTasks().getFirst().getBinding();
        binding.setType("APPROVAL"); binding.setApprovalDefinitionKey("review");
        binding.setParameters(JsonUtils.parseTree("{\"processDefinitionId\":\"review:2:202\"}"));
        snapshot.getTasks().getFirst().setCompletionRule(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}"));
        effective.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        String old = JsonUtils.toJsonString(oldContract);
        service.apply(command(3, 2L), 9L, "approval-round");
        verify(contracts).insert(argThat((ProjectTaskExecutionContractDO row) -> "APPROVAL".equals(row.getWorkBindingTypeCode())
                && "review".equals(JsonUtils.parseTree(row.getBindingParameterSnapshot()).path("approvalDefinitionKey").asText())
                && "review:2:202".equals(JsonUtils.parseTree(row.getBindingParameterSnapshot()).path("processDefinitionId").asText())
                && row.getApprovalInstanceId() == null));
        assertEquals(old, JsonUtils.toJsonString(oldContract));
        verify(executions).insert(argThat((ProjectNodeExecutionDO row) -> row.getRoundNo() == 2 && row.getResultSnapshot() == null));
    }

    @Test void mismatchedOrConcurrentlyClosedContractCannotCreateANewRound() {
        oldContract.setId(999L);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.apply(command(3,2L),9L,"wrong-contract"));
        verifyNoInteractions(projections,changes); verify(contracts,never()).insert(any(ProjectTaskExecutionContractDO.class));
        oldContract.setId(31L); when(projections.closeTaskContract(any())).thenReturn(0);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.apply(command(3,2L),9L,"stale-contract"));
        verify(projections).closeTaskContract(any());
        verifyNoInteractions(changes); verify(executions,never()).insert(any(ProjectNodeExecutionDO.class));
        verify(contracts,never()).insert(any(ProjectTaskExecutionContractDO.class));
    }

    @Test void failedNewContractInsertCannotResetTaskOrPublishSuccessFacts() {
        when(contracts.insert(any(ProjectTaskExecutionContractDO.class))).thenReturn(0);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.apply(command(3,2L),9L,"insert-failure"));
        verify(contracts).insert(any(ProjectTaskExecutionContractDO.class));
        verifyNoInteractions(changes); verify(executions,never()).insert(any(ProjectNodeExecutionDO.class)); assertNull(facts);
    }

    private ProjectReworkService.Apply command(int projectVersion,long executionId) {
        return new ProjectReworkService.Apply(100L,50L,projectVersion,List.of("t1"),
                List.of(new ProjectReworkService.ExpectedExecution("t1",executionId,1)),"返工原因");
    }
}
