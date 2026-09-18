package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.*;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRulePublicationValidator;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectPlanDraftServiceTest {
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final ProjectMasterMapper projectRows = mock(ProjectMasterMapper.class);
    final ProjectTaskRuntimeMapper projects = mock(ProjectTaskRuntimeMapper.class);
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    final TemplateCompiler compiler = mock(TemplateCompiler.class);
    final TemplateDesignerDependencyValidator dependencies = mock(TemplateDesignerDependencyValidator.class);
    final ProjectRulePublicationValidator ruleValidator = mock(ProjectRulePublicationValidator.class);
    final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    final cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService deliverables = mock(cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService.class);
    final ProjectPlanProjectionMapper milestoneProjections = mock(ProjectPlanProjectionMapper.class);
    final ProjectPlanMilestoneInstaller milestoneInstaller = new ProjectPlanMilestoneInstaller(milestoneProjections,
            mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMilestoneInstanceMapper.class));
    final ProjectPlanGateInstaller gates = mock(ProjectPlanGateInstaller.class);
    final ProjectPlanDraftService service = new ProjectPlanDraftService(scopes,permissions,projectRows,projects,plans,executions,graph,compiler,dependencies,ruleValidator,new ProjectPlanImpactAnalyzer(),new ProjectPlanExecutionPlanner(),commands,deliverables,new ProjectPlanDeliverablePlanner(),milestoneInstaller,gates);
    ProjectMasterDO project;
    ProjectPlanVersionDO effective, draft;
    PlatformCommandExecutionApi.SuccessFacts recorded;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        TenantContextHolder.setTenantId(7L);
        when(gates.inspect(any(),any(),any())).thenReturn(new ProjectPlanGateInstaller.Plan(List.of(),List.of(),List.of()));
        when(deliverables.inspectPlanDefinitions(9L)).thenReturn(new cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService.DeliverablePlanState(List.of(),Set.of()));
        when(permissions.hasAnyPermissions(1L,ProjectPlanDraftService.MANAGE_PERMISSION)).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(9L),Set.of()));
        project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L); project.setActivePlanVersionId(51L); project.setVersion(4); project.setLifecycleStatus("ACTIVE");
        when(projectRows.selectById(9L)).thenReturn(project); when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project);
        effective = plan(51L,"EFFECTIVE",1); effective.setSourceTemplateRevisionId(888L);
        draft = plan(52L,"DRAFT",2); draft.setBasePlanVersionId(51L);
        when(plans.selectEffective(any())).thenReturn(effective); when(plans.selectDraft(any())).thenReturn(draft);
        when(commands.execute(any(),anyString(),eq(ProjectPlanDraftService.Definition.class),any(),any())).thenAnswer(call -> {
            var result = ((Supplier<ProjectPlanDraftService.Definition>)call.getArgument(3)).get();
            recorded = ((Function<ProjectPlanDraftService.Definition,PlatformCommandExecutionApi.SuccessFacts>)call.getArgument(4)).apply(result);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,result);
        });
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void createsOnlyAProjectDraftFromExactEffectivePlanWithoutInitializingRuntime() {
        when(plans.selectDraft(any())).thenReturn(null); when(plans.selectNextRevisionNo(any())).thenReturn(2);
        when(plans.insert(any(ProjectPlanVersionDO.class))).thenAnswer(call -> { ProjectPlanVersionDO row=call.getArgument(0); row.setId(52L); return 1; });
        var result = service.create(new ProjectPlanDraftService.Create(9L,51L),1L,"create");
        assertEquals(52L,result.id()); assertEquals(51L,result.basePlanVersionId()); assertEquals(2,result.revisionNo());
        verify(plans).insert(argThat((ProjectPlanVersionDO p) -> p.getExecutionSnapshot()==null && p.getEffectiveAt()==null && p.getSourceTemplateRevisionId().equals(888L)));
        verifyNoInteractions(executions,graph,compiler,dependencies,ruleValidator);
        assertTrue(recorded.businessEvents().isEmpty()); assertEquals(51L,project.getActivePlanVersionId()); assertEquals(4,project.getVersion());
    }
    @Test void savesDraftWithCasAndSafeAuditButDoesNotPublishOrEvaluateRules() {
        when(plans.saveDraftIfCurrent(any())).thenReturn(1);
        var designer = new TemplateDesignerDocument(); designer.setProcessDefinitionKey("private-draft-value");
        var result = service.save(new ProjectPlanDraftService.Save(9L,52L,0,designer),1L,"save");
        assertEquals(1,result.version()); assertFalse(recorded.detailSnapshot().contains("private-draft-value")); assertTrue(recorded.businessEvents().isEmpty());
        verify(plans).saveDraftIfCurrent(argThat(q -> q.tenantId().equals(7L) && q.projectId().equals(9L) && q.expectedVersion()==0 && q.basePlanVersionId().equals(51L)));
        verifyNoInteractions(executions,graph,compiler,dependencies,ruleValidator);
        assertFalse(effective.getDesignerDocument().contains("private-draft-value"));
    }
    @Test void staleDraftOrChangedBaseCannotOverwriteParallelChanges() {
        assertThrows(RuntimeException.class,() -> service.save(new ProjectPlanDraftService.Save(9L,52L,8,new TemplateDesignerDocument()),1L,"stale"));
        project.setActivePlanVersionId(99L);
        assertThrows(RuntimeException.class,() -> service.save(new ProjectPlanDraftService.Save(9L,52L,0,new TemplateDesignerDocument()),1L,"base"));
        verify(plans,never()).saveDraftIfCurrent(any());
    }
    @Test void deniedManagePermissionOrProjectScopePrecedesAnyDraftRead() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class,() -> service.get(9L,1L));
        assertThrows(RuntimeException.class,() -> service.create(new ProjectPlanDraftService.Create(9L,51L),1L,"create"));
        verifyNoInteractions(plans,projects,projectRows,commands);
    }
    @Test void closedProjectDraftCannotBeSavedOrCreated() {
        project.setLifecycleStatus("NORMAL_CLOSED");
        assertFalse(service.get(9L,1L).editable());
        assertThrows(RuntimeException.class,() -> service.save(new ProjectPlanDraftService.Save(9L,52L,0,new TemplateDesignerDocument()),1L,"save"));
        verify(plans,never()).saveDraftIfCurrent(any());
    }
    @Test void previewUsesSavedVersionAndSharedValidatorsWithoutEmittingEventsOrAdvancingState() {
        var snapshot=ProjectPlanImpactAnalyzerTest.snapshot(); effective.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        var runtime = new ProjectPlanExecutionPlannerTest();
        runtime.rounds.forEach(round -> round.setPlanVersionId(51L));
        when(executions.selectCurrentForUpdate(any())).thenReturn(runtime.rounds);
        when(graph.selectStagesForUpdate(any())).thenReturn(runtime.stages);
        when(graph.selectTasksForUpdate(any())).thenReturn(runtime.tasks);
        when(compiler.compileVersioned(any())).thenReturn(new TemplateCompiler.Compilation(ProjectPlanImpactAnalyzerTest.copy(snapshot),null,List.of()));
        when(dependencies.validateProjectChanges(any(),any(),eq(false))).thenReturn(List.of()); when(ruleValidator.validate(any())).thenReturn(List.of());
        var result=service.preview(9L,52L,0,1L);
        assertTrue(result.changes().isEmpty()); assertTrue(result.issues().isEmpty()); assertEquals(4,result.projectVersion());
        assertEquals(3,result.executionChanges().size());
        assertTrue(result.executionChanges().stream().allMatch(change -> change.action() == ProjectPlanExecutionPlanner.Action.REBASE_CURRENT));
        verify(dependencies).validateProjectChanges(any(),any(),eq(false)); verify(ruleValidator).validate(any());
        verifyNoInteractions(commands); verify(plans,never()).saveDraftIfCurrent(any());
        assertEquals("ACTIVE",project.getLifecycleStatus()); assertEquals(51L,project.getActivePlanVersionId());
    }
    @Test void previewExposesMissingRuntimeInsteadOfPromisingSuccessfulActivation() {
        var snapshot = ProjectPlanImpactAnalyzerTest.snapshot(); effective.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        when(compiler.compileVersioned(any())).thenReturn(new TemplateCompiler.Compilation(snapshot,null,List.of()));
        var result = service.preview(9L,52L,0,1L);
        assertEquals(3,result.issues().stream().filter(issue -> issue.code().equals("CURRENT_EXECUTION_MISSING")).count());
        assertTrue(result.executionChanges().isEmpty());
        verifyNoInteractions(commands);
    }
    @Test void previewSurfacesOwnerDeletionProtectionAndInstanceVersionWithoutWritingBusinessData() {
        var snapshot = ProjectPlanImpactAnalyzerTest.snapshot();
        var node = new TemplateExecutionSnapshot.DeliverableContract(); node.setNodeKey("deliverable:one");
        node.setCode("CUSTOM-D"); node.setName("delivery"); node.setStageCode("A"); node.setRequired(true);
        snapshot.setDeliverables(List.of(node)); effective.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        var after = ProjectPlanImpactAnalyzerTest.copy(snapshot); after.setDeliverables(List.of());
        when(compiler.compileVersioned(any())).thenReturn(new TemplateCompiler.Compilation(after,null,List.of()));
        var row = new cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService.DeliverableView(
                100L,9L,"CUSTOM-D","delivery","A",null,true,null,"PENDING",8);
        when(deliverables.inspectPlanDefinitions(9L)).thenReturn(new cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService.DeliverablePlanState(List.of(row),Set.of()));
        var result = service.preview(9L,52L,0,1L);
        assertTrue(result.issues().stream().anyMatch(issue -> "DELIVERABLE_HANDLING_HISTORY_PROTECTED".equals(issue.code())));
        assertEquals(100L,result.deliverableChanges().getFirst().instanceId());
        assertEquals(8,result.deliverableChanges().getFirst().expectedVersion());
        verify(deliverables,never()).applyPlanChanges(any()); verifyNoInteractions(commands);
    }
    @Test void previewProtectsAchievedMilestoneAndIncludesItsActualVersion() {
        var snapshot = ProjectPlanImpactAnalyzerTest.snapshot();
        var node = new TemplateExecutionSnapshot.MilestoneContract(); node.setNodeKey("milestone:one");
        node.setCode("M1"); node.setName("已达成里程碑"); node.setStageCode("A"); snapshot.setMilestones(List.of(node));
        effective.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        var after = ProjectPlanImpactAnalyzerTest.copy(snapshot); after.setMilestones(List.of());
        when(compiler.compileVersioned(any())).thenReturn(new TemplateCompiler.Compilation(after,null,List.of()));
        var row = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMilestoneInstanceDO();
        row.setId(200L); row.setProjectId(9L); row.setTenantId(7L); row.setMilestoneCode("M1"); row.setStatus("ACHIEVED"); row.setVersion(6);
        when(milestoneProjections.selectMilestonesForUpdate(any())).thenReturn(List.of(row));
        var result = service.preview(9L,52L,0,1L);
        assertTrue(result.issues().stream().anyMatch(issue -> "ACHIEVED_MILESTONE_DELETE_FORBIDDEN".equals(issue.code())));
        assertEquals(200L,result.milestoneChanges().getFirst().instanceId()); assertEquals(6,result.milestoneChanges().getFirst().expectedVersion());
        verify(milestoneProjections,never()).retirePendingMilestone(any()); verifyNoInteractions(commands);
    }
    @Test void previewExplainsGateInvalidationBeforeAnyResultIsChanged() {
        var snapshot = ProjectPlanImpactAnalyzerTest.snapshot();
        var node = new TemplateExecutionSnapshot.GateContract(); node.setNodeKey("gate:one"); node.setCode("G1"); node.setName("业务门禁");
        snapshot.setGates(List.of(node)); effective.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        when(compiler.compileVersioned(any())).thenReturn(new TemplateCompiler.Compilation(snapshot,null,List.of()));
        var change = new ProjectPlanGateInstaller.Change("gate:one","UPDATE",300L,7,"G1","G1",true);
        when(gates.inspect(any(),any(),any())).thenReturn(new ProjectPlanGateInstaller.Plan(List.of(change),
                List.of(new ProjectPlanGateInstaller.Write(change,null,"PASSED",List.of(),List.of())),List.of()));
        var result = service.preview(9L,52L,0,1L);
        assertEquals(300L,result.gateChanges().getFirst().instanceId()); assertTrue(result.gateChanges().getFirst().reevaluationRequired());
        var displayed = result.changes().stream().filter(row -> "gate:one".equals(row.nodeKey())).findFirst().orElseThrow();
        assertTrue(displayed.started()); assertTrue(displayed.effects().stream().anyMatch(effect -> effect.contains("不能沿用旧通过结果")));
        verify(gates,never()).install(any(),any(),any(),any()); verifyNoInteractions(commands);
    }
    @Test void previewRetainsBindingFailureLocationWithoutSavingOrAdvancingThePlan() {
        var failure = new cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue(
                "stages[0].completionRuleKey.rule", "RULE_BUSINESS_BINDING_UNAVAILABLE", "业务绑定不匹配");
        var snapshot = ProjectPlanImpactAnalyzerTest.snapshot();
        effective.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        var runtime = new ProjectPlanExecutionPlannerTest();
        runtime.rounds.forEach(round -> round.setPlanVersionId(51L));
        when(executions.selectCurrentForUpdate(any())).thenReturn(runtime.rounds);
        when(graph.selectStagesForUpdate(any())).thenReturn(runtime.stages);
        when(graph.selectTasksForUpdate(any())).thenReturn(runtime.tasks);
        when(compiler.compileVersioned(any())).thenReturn(new TemplateCompiler.Compilation(snapshot,null,List.of()));
        when(ruleValidator.validate(any())).thenReturn(List.of(failure));
        var result = service.preview(9L,52L,0,1L);
        assertTrue(result.issues().contains(failure));
        verifyNoInteractions(commands);
        verify(plans,never()).saveDraftIfCurrent(any());
        assertEquals(51L,project.getActivePlanVersionId());
    }

    private ProjectPlanVersionDO plan(Long id,String status,int revision) {
        var plan=new ProjectPlanVersionDO(); plan.setId(id); plan.setProjectId(9L); plan.setTenantId(7L); plan.setStatus(status); plan.setRevisionNo(revision); plan.setVersion(0);
        plan.setDesignerDocument(JsonUtils.toJsonString(new TemplateDesignerDocument())); return plan;
    }
}
