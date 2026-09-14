package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskTreePathMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphFreezer;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectPlanStageTaskInstallerTest {
    final ProjectPlanProjectionMapper projections = mock(ProjectPlanProjectionMapper.class);
    final ProjectStageInstanceMapper stages = mock(ProjectStageInstanceMapper.class);
    final ProjectTaskInstanceMapper tasks = mock(ProjectTaskInstanceMapper.class);
    final ProjectTaskTreePathMapper paths = mock(ProjectTaskTreePathMapper.class);
    final ProjectTaskExecutionContractMapper contracts = mock(ProjectTaskExecutionContractMapper.class);
    final ProjectStageExecutionContractMapper stageContracts = mock(ProjectStageExecutionContractMapper.class);
    final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    final TaskExecutionContractFactory taskFactory = new TaskExecutionContractFactory();
    final ProjectRuntimeGraphFreezer stageFactory = new ProjectRuntimeGraphFreezer(graph,stageContracts);
    final cn.iocoder.yudao.module.pms.project.api.satisfaction.SatisfactionQuestionnaireTemplateApi satisfactionTemplates = mock(cn.iocoder.yudao.module.pms.project.api.satisfaction.SatisfactionQuestionnaireTemplateApi.class);
    final ProjectPlanStageTaskInstaller installer = new ProjectPlanStageTaskInstaller(projections,stages,tasks,paths,contracts,stageContracts,graph,executions,taskFactory,stageFactory,satisfactionTemplates);
    final LocalDateTime now = LocalDateTime.of(2026,9,14,15,0);
    TemplateExecutionSnapshot before;
    ProjectInstantiation actual;
    ProjectMasterDO project;
    List<ProjectNodeExecutionDO> rounds;
    ProjectTaskExecutionContractDO taskContract;

    @BeforeEach void setup() {
        before = ProjectPlanImpactAnalyzerTest.snapshot();
        before.getStages().forEach(stage -> stage.setCompletionRule(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}")));
        before.getTasks().getFirst().setPermission(new TemplateExecutionSnapshot.PermissionContract());
        before.getTasks().getFirst().getPermission().setPolicyRef("PROJECT_TASK_NATIVE_DEFAULT");
        before.getTasks().getFirst().setCompletionRule(JsonUtils.parseTree("{\"predicate\":\"TASK_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}"));
        actual = TemplateInstantiator.instantiate(before.toRuntimeContent(),9L,73L,()->20L);
        actual.getStages().forEach(row -> row.setSortOrder(0));
        actual.getTasks().getFirst().setSortOrder(0); actual.getTasks().getFirst().setPriority(2);
        actual.getStages().get(0).setId(11L); actual.getStages().get(0).setStatus("ACTIVE");
        actual.getStages().get(1).setId(12L); actual.getStages().get(1).setStatus("DONE");
        actual.getTasks().getFirst().setStatus("IN_PROGRESS"); actual.getTasks().getFirst().setVersion(5);
        actual.getTasks().getFirst().setActualStartTime(now.minusDays(1)); actual.getTasks().getFirst().setDescription("运行中实际说明");
        project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(1L); project.setActivePlanVersionId(50L); project.setTaskTreeVersion(3L);
        var frozenStages = new ArrayList<ProjectStageExecutionContractDO>();
        for (int i=0;i<2;i++) {
            var contract = stageFactory.createContract(1L,9L,before.getStages().get(i),actual.getStages().get(i),JsonUtils.toJsonString(before),now.minusDays(2));
            contract.setId(41L+i); frozenStages.add(contract);
        }
        when(graph.selectContracts(any())).thenReturn(frozenStages);
        taskContract = taskFactory.create(20L,null,before.toRuntimeContent().getTasks().getFirst(),now.minusDays(2)); taskContract.setId(30L); taskContract.setTenantId(1L);
        when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(taskContract);
        rounds = new ArrayList<>(List.of(round("stage:a","STAGE",11L,41L,"ACTIVE"),round("stage:b","STAGE",12L,42L,"DONE"),round("task:one","TASK",20L,30L,"ACTIVE")));
        rounds.getLast().setStartedAt(now.minusDays(1));
        when(stages.insert(any(ProjectStageInstanceDO.class))).thenReturn(1); when(tasks.insert(any(ProjectTaskInstanceDO.class))).thenReturn(1);
        when(paths.insert(any(ProjectTaskTreePathDO.class))).thenReturn(1); when(executions.insert(any(ProjectNodeExecutionDO.class))).thenReturn(1);
        when(executions.selectNextRoundNo(any())).thenReturn(1);
        AtomicLong nextContract = new AtomicLong(100);
        when(contracts.insert(any(ProjectTaskExecutionContractDO.class))).thenAnswer(call -> { ((ProjectTaskExecutionContractDO)call.getArgument(0)).setId(nextContract.incrementAndGet()); return 1; });
        when(stageContracts.insert(any(ProjectStageExecutionContractDO.class))).thenAnswer(call -> { ((ProjectStageExecutionContractDO)call.getArgument(0)).setId(nextContract.incrementAndGet()); return 1; });
        when(projections.updateStageDefinition(any())).thenReturn(1); when(projections.updateTaskDefinition(any())).thenReturn(1);
        when(projections.stageCodeForRename(any())).thenReturn(1); when(projections.taskCodeForRename(any())).thenReturn(1);
        when(projections.advanceTaskTreeVersion(any())).thenReturn(1); when(projections.retireUnstartedTask(any())).thenReturn(1);
        when(projections.retireUnstartedStage(any())).thenReturn(1); when(projections.closeTaskContract(any())).thenReturn(1); when(projections.closeStageContract(any())).thenReturn(1);
    }
    @Test void addsCustomStageAndNestedTasksWhilePreservingExistingIdentitiesWorkAndContracts() {
        var after = copy();
        var stage = new TemplateExecutionSnapshot.StageContract(); stage.setNodeKey("stage:c"); stage.setCode("CUSTOM"); stage.setName("自定义交付"); stage.setCompletionRule(before.getStages().getFirst().getCompletionRule()); after.getStages().add(stage);
        for (int i=2;i<=3;i++) {
            var task = JsonUtils.parseObject(JsonUtils.toJsonString(before.getTasks().getFirst()),TemplateExecutionSnapshot.TaskContract.class);
            task.setNodeKey("task:"+i); task.setCode("T"+i); task.setStageCode("CUSTOM"); task.setParentTaskCode(i==3 ? "T2" : null); after.getTasks().add(task);
        }
        String original = JsonUtils.toJsonString(rounds);
        var result = install(after);
        assertTrue(result.tasksChanged()); assertEquals(2,result.continuing().size()); assertTrue(result.removed().isEmpty());
        assertTrue(result.continuing().stream().anyMatch(row -> row.executionId()==120L && row.newContractId()==30L));
        var added = ArgumentCaptor.forClass(ProjectTaskInstanceDO.class); verify(tasks,times(2)).insert(added.capture());
        var child = added.getAllValues().stream().filter(row -> "T3".equals(row.getTaskCode())).findFirst().orElseThrow();
        var parent = added.getAllValues().stream().filter(row -> "T2".equals(row.getTaskCode())).findFirst().orElseThrow();
        assertEquals(parent.getId(),child.getParentTaskId()); assertEquals(parent.getId(),child.getRootTaskId()); assertEquals(1,child.getTreeDepth());
        assertEquals("PENDING_ASSIGN",child.getStatus()); assertEquals(73L,child.getStateMachineRevisionId());
        verify(executions,times(3)).insert(any(ProjectNodeExecutionDO.class)); verify(projections,never()).updateTaskDefinition(any());
        verify(projections,never()).closeTaskContract(any()); verify(projections,never()).closeStageContract(any());
        assertEquals(original,JsonUtils.toJsonString(rounds)); assertEquals("运行中实际说明",actual.getTasks().getFirst().getDescription());
    }
    @Test void pureRuleChangeOnlyCarriesUnfinishedRoundsWithoutRebuildingTheTaskTree() {
        var after = copy(); after.setClosureRuleKey("new-rule");
        var result = install(after);
        assertFalse(result.tasksChanged()); assertEquals(2,result.continuing().size());
        verifyNoInteractions(paths); verify(projections,never()).deleteCurrentTaskPaths(any());
        verify(tasks,never()).insert(any(ProjectTaskInstanceDO.class)); verify(contracts,never()).insert(any(ProjectTaskExecutionContractDO.class));
        assertTrue(result.continuing().stream().noneMatch(row -> row.executionId()==112L));
    }
    @Test void stageRenameWithoutNewTasksNeedsNoPublishedStateMachine() {
        var after = copy(); after.getStages().getFirst().setName("工前准备（项目调整）");
        var result = install(after,null);
        assertFalse(result.tasksChanged()); assertEquals(2,result.continuing().size());
        verify(projections).updateStageDefinition(argThat(update -> "工前准备（项目调整）".equals(update.definition().getName())));
        verify(tasks,never()).insert(any(ProjectTaskInstanceDO.class));
        verifyNoInteractions(paths);
    }
    @Test void taskRenameRetainsItsOwnFrozenStateMachineEvenWhenNewVersionIsProvided() {
        var after = copy(); after.getTasks().getFirst().setName("调整后的任务名称");
        install(after,99L);
        verify(projections).updateTaskDefinition(argThat(update -> update.definition().getStateMachineRevisionId()==73L));
        assertEquals(73L,actual.getTasks().getFirst().getStateMachineRevisionId());
    }
    @Test void stageOnlyPlanNeedsNoTaskStateMachine() {
        before.setTasks(List.of()); actual.getTasks().clear();
        rounds.removeIf(round -> "TASK".equals(round.getNodeKind()));
        var after = copy(); after.getStages().getFirst().setName("仅阶段办理");
        assertFalse(install(after,null).tasksChanged());
        verifyNoInteractions(tasks,contracts,paths);
    }
    @Test void newTaskWithoutStateMachineFailsBeforeAnyProjectionWrite() {
        assertThrows(IllegalArgumentException.class,()->install(withNewSatisfactionTask(),null));
        verifyNoInteractions(projections,stages,tasks,paths,executions);
    }
    @Test void codeRenameKeepsInstanceAndExecutionIdsAndDoesNotResetRuntimeFields() {
        var after = copy(); after.getStages().getFirst().setCode("RENAMED");
        after.getTasks().getFirst().setStageCode("RENAMED"); after.getTasks().getFirst().setCode("RENAMED_TASK");
        var result = install(after);
        var order = inOrder(projections); order.verify(projections).stageCodeForRename(any()); order.verify(projections).taskCodeForRename(any());
        order.verify(projections).updateStageDefinition(argThat(row -> row.id()==11L && "RENAMED".equals(row.definition().getStageCode())));
        order.verify(projections).updateTaskDefinition(argThat(row -> row.id()==20L && row.expectedVersion()==5 && "运行中实际说明".equals(row.definition().getDescription())));
        assertEquals(120L,result.continuing().stream().filter(row -> row.expectedContractId()==30L).findFirst().orElseThrow().executionId());
        assertEquals("IN_PROGRESS",actual.getTasks().getFirst().getStatus()); assertNotNull(actual.getTasks().getFirst().getActualStartTime());
    }
    @Test void removesOnlyAnUnstartedBranchAndRetainsItsArchivedExecutionForTheVersionTransaction() {
        actual.getTasks().getFirst().setStatus("PENDING_ASSIGN"); actual.getTasks().getFirst().setActualStartTime(null);
        rounds.getLast().setStatus("PENDING"); rounds.getLast().setStartedAt(null);
        var after = copy(); after.setTasks(List.of()); after.setStages(List.of(after.getStages().get(1)));
        var result = install(after);
        assertEquals(2,result.removed().size()); assertTrue(result.tasksChanged());
        var order = inOrder(projections); order.verify(projections).retireUnstartedTask(any()); order.verify(projections).closeTaskContract(any());
        order.verify(projections).retireUnstartedStage(any()); order.verify(projections).closeStageContract(any());
        assertEquals(1,rounds.getLast().getCurrentMarker()); // Final version persistence owns retiring the pointer.
        verify(executions,never()).retireUnstartedIfCurrent(any());
    }
    @Test void missingCurrentBindingFailsInsteadOfRegeneratingAnExistingNodesIdentity() {
        when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(null);
        assertThrows(RuntimeException.class,()->install(copy()));
        verify(contracts,never()).insert(any(ProjectTaskExecutionContractDO.class));
    }
    @Test void preservesRunningApprovalReferenceWhenOnlyItsPermissionContractChanges() {
        var node = before.getTasks().getFirst(); node.getBinding().setType("APPROVAL"); node.getBinding().setApprovalDefinitionKey("existing-process");
        node.setCompletionRule(before.getStages().getFirst().getCompletionRule());
        taskContract = taskFactory.create(20L,null,before.toRuntimeContent().getTasks().getFirst(),now.minusDays(2));
        taskContract.setId(30L); taskContract.setTenantId(1L); taskContract.setApprovalInstanceId(99L);
        when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(taskContract);
        var after = copy(); after.getTasks().getFirst().getPermission().setPolicyRef("changed-policy");
        var result = install(after);
        verify(contracts).insert(argThat((ProjectTaskExecutionContractDO row) -> row.getApprovalInstanceId()==99L && row.getContractVersion()==2));
        assertEquals(30L,result.continuing().stream().filter(row -> row.executionId()==120L).findFirst().orElseThrow().expectedContractId());
        assertEquals(99L,taskContract.getApprovalInstanceId());
    }
    @Test void assetProvenanceAloneDoesNotReplaceTheRuntimeBindingIdentity() {
        var after = copy(); after.getTasks().getFirst().getPermission().setSourceRevisionId(999L);
        after.getTasks().getFirst().getBinding().setSourceRevisionId(888L);
        var result = install(after);
        verify(contracts,never()).insert(any(ProjectTaskExecutionContractDO.class));
        verify(projections,never()).closeTaskContract(any());
        assertTrue(result.continuing().stream().anyMatch(row -> row.executionId()==120L && row.newContractId()==30L));
    }
    @Test void newlyAddedSatisfactionTaskFreezesTheOwnerQuestionnaireContract() {
        var after = withNewSatisfactionTask();
        when(satisfactionTemplates.resolvePublished(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTemplateFact(
                "FOUND",81L,82L,3,"rule-v2",new java.math.BigDecimal("90")));
        install(after);
        verify(tasks).insert(argThat((ProjectTaskInstanceDO row) -> "SATISFACTION".equals(row.getTaskCode())
                && row.getAccSatisfactionTemplateId()==81L && row.getTemplateRevisionId()==82L
                && row.getTemplateVersion()==3 && "rule-v2".equals(row.getSatisfactionRuleVersion())
                && new java.math.BigDecimal("90").equals(row.getSatisfactionThreshold())));
        verify(satisfactionTemplates).resolvePublished(argThat(query -> query.tenantId()==1L
                && "AFTER_INITIAL_ACCEPTANCE".equals(query.applicableTimingCode())));
    }
    @Test void unchangedSatisfactionBindingKeepsFrozenQuestionnaireWhenTaskIsRenamed() {
        before.getTasks().getFirst().setSatisfactionTiming("AFTER_INITIAL_ACCEPTANCE");
        var row = actual.getTasks().getFirst(); row.setSatisfactionTiming("AFTER_INITIAL_ACCEPTANCE");
        row.setAccSatisfactionTemplateId(81L); row.setTemplateRevisionId(82L); row.setTemplateVersion(3);
        row.setSatisfactionRuleVersion("frozen-v2"); row.setSatisfactionThreshold(new java.math.BigDecimal("90"));
        var after = copy(); after.getTasks().getFirst().setCode("RENAMED");
        install(after);
        verify(projections).updateTaskDefinition(argThat(update -> update.definition().getTemplateRevisionId()==82L
                && "frozen-v2".equals(update.definition().getSatisfactionRuleVersion())));
        verifyNoInteractions(satisfactionTemplates);
    }
    @Test void unavailableQuestionnairePreventsNewTaskAndExecutionCreation() {
        assertThrows(IllegalStateException.class,()->install(withNewSatisfactionTask()));
        verify(tasks,never()).insert(any(ProjectTaskInstanceDO.class));
        verify(executions,never()).insert(any(ProjectNodeExecutionDO.class));
    }
    private TemplateExecutionSnapshot withNewSatisfactionTask() {
        var after = copy();
        var node = JsonUtils.parseObject(JsonUtils.toJsonString(after.getTasks().getFirst()),TemplateExecutionSnapshot.TaskContract.class);
        node.setNodeKey("task:satisfaction"); node.setCode("SATISFACTION"); node.setSatisfactionTiming("AFTER_INITIAL_ACCEPTANCE");
        after.getTasks().add(node);
        return after;
    }
    TemplateExecutionSnapshot copy() { return JsonUtils.parseObject(JsonUtils.toJsonString(before),TemplateExecutionSnapshot.class); }
    @Test void planInstallPreservesRuntimeParentAndRebuildsPathsUsingItsRenamedCode() {
        addRuntimeParent();
        var after=copy(); after.getTasks().get(1).setCode("RENAMED_PARENT");
        install(after);
        var updates=ArgumentCaptor.forClass(ProjectPlanProjectionMapper.TaskDefinitionUpdate.class);
        verify(projections,times(2)).updateTaskDefinition(updates.capture());
        var child=updates.getAllValues().stream().filter(row -> row.id()==20L).findFirst().orElseThrow().definition();
        assertEquals(21L,child.getParentTaskId()); assertEquals(21L,child.getRootTaskId());
        assertEquals(1,child.getTreeDepth()); assertEquals("RENAMED_PARENT",child.getParentTaskCode());
        var inserted=ArgumentCaptor.forClass(ProjectTaskTreePathDO.class);
        verify(paths,times(3)).insert(inserted.capture());
        assertTrue(inserted.getAllValues().stream().anyMatch(row -> row.getAncestorTaskId()==21L
                && row.getDescendantTaskId()==20L && row.getDistance()==1));
        assertEquals(21L,actual.getTasks().getFirst().getParentTaskId());
        assertEquals("IN_PROGRESS",actual.getTasks().getFirst().getStatus());
    }
    @Test void previewDetectsMergedHierarchyCycleAndInstallerRejectsItBeforeAnyWrite() {
        addRuntimeParent();
        var after=copy(); after.getTasks().get(1).setParentTaskCode("T1");
        var preview=new ProjectPlanExecutionPlanner().plan(50L,before,after,rounds,actual.getStages(),actual.getTasks());
        assertTrue(preview.issues().stream().anyMatch(issue -> "TASK_HIERARCHY_CONFLICT".equals(issue.code())));
        // Even an incorrectly omitted preview issue cannot cause partial installation writes.
        assertThrows(RuntimeException.class,() -> installer.install(new ProjectPlanStageTaskInstaller.Request(
                project,51L,73L,before,after,new ProjectPlanExecutionPlanner.Plan(List.of(),List.of()),
                rounds,actual.getStages(),actual.getTasks(),1L,now)));
        verifyNoInteractions(projections,stages,tasks,paths,contracts,stageContracts,executions);
    }
    private void addRuntimeParent() {
        var parent=JsonUtils.parseObject(JsonUtils.toJsonString(before.getTasks().getFirst()),TemplateExecutionSnapshot.TaskContract.class);
        parent.setNodeKey("task:parent"); parent.setCode("T2");
        before.setTasks(new ArrayList<>(before.getTasks())); before.getTasks().add(parent);
        var row=JsonUtils.parseObject(JsonUtils.toJsonString(actual.getTasks().getFirst()),ProjectTaskInstanceDO.class);
        row.setId(21L); row.setTaskCode("T2"); row.setParentTaskId(null); row.setRootTaskId(21L); row.setTreeDepth(0);
        actual.getTasks().add(row);
        actual.getTasks().getFirst().setParentTaskId(21L); actual.getTasks().getFirst().setRootTaskId(21L);
        actual.getTasks().getFirst().setTreeDepth(1); actual.getTasks().getFirst().setParentTaskCode("T2");
        var contract=taskFactory.create(21L,null,before.toRuntimeContent().getTasks().get(1),now.minusDays(2));
        contract.setId(31L); contract.setTenantId(1L);
        when(contracts.selectCurrentByTaskIdForUpdate(argThat(query -> query.projectTaskId()==21L))).thenReturn(contract);
        rounds.add(round("task:parent","TASK",21L,31L,"ACTIVE"));
    }
    ProjectPlanStageTaskInstaller.Installation install(TemplateExecutionSnapshot after) {
        return install(after,73L);
    }
    ProjectPlanStageTaskInstaller.Installation install(TemplateExecutionSnapshot after,Long newTaskStateMachineId) {
        var plan = new ProjectPlanExecutionPlanner().plan(50L,before,after,rounds,actual.getStages(),actual.getTasks()); assertTrue(plan.issues().isEmpty(),plan.issues().toString());
        return installer.install(new ProjectPlanStageTaskInstaller.Request(project,51L,newTaskStateMachineId,before,after,plan,rounds,actual.getStages(),actual.getTasks(),1L,now));
    }
    ProjectNodeExecutionDO round(String key,String kind,long id,long contract,String status) {
        var row = new ProjectNodeExecutionDO(); row.setId(id+100); row.setNodeInstanceId(id); row.setNodeKey(key); row.setNodeKind(kind); row.setContractId(contract);
        row.setPlanVersionId(50L); row.setCurrentMarker(1); row.setVersion(0); row.setRoundNo(1); row.setStatus(status);
        if ("DONE".equals(status)) { row.setEndedAt(now.minusDays(1)); row.setResultSnapshot("{\"result\":true}"); }
        return row;
    }
}
