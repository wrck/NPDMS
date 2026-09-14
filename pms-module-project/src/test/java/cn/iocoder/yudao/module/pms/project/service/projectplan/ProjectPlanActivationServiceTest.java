package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateMachineRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleReevaluation;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskProgressService;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectPlanActivationServiceTest {
    final ProjectPlanDraftService drafts = mock(ProjectPlanDraftService.class);
    final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    final ProjectPlanStageTaskInstaller nodes = mock(ProjectPlanStageTaskInstaller.class);
    final ProjectPlanMilestoneInstaller milestones = mock(ProjectPlanMilestoneInstaller.class);
    final ProjectPlanGateInstaller gates = mock(ProjectPlanGateInstaller.class);
    final ProjectDeliverableInitializationApplicationService deliverables = mock(ProjectDeliverableInitializationApplicationService.class);
    final ProjectPlanActivationPersistence persistence = mock(ProjectPlanActivationPersistence.class);
    final TaskStateMachineMapper machines = mock(TaskStateMachineMapper.class);
    final ProjectTaskProgressService progress = mock(ProjectTaskProgressService.class);
    final ProjectPlanActivationService service = new ProjectPlanActivationService(drafts,commands,nodes,milestones,gates,deliverables,persistence,machines,progress);
    final ProjectPlanScopeQuery scope = new ProjectPlanScopeQuery(7L,9L);
    ProjectPlanDraftService.Preview preview;
    ProjectPlanDraftService.Prepared prepared;
    PlatformCommandExecutionApi.SuccessFacts facts;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        preview = preview(4,List.of(),List.of());
        when(drafts.authorize(9L,1L)).thenReturn(scope);
        prepare(preview);
        when(nodes.install(any())).thenReturn(new ProjectPlanStageTaskInstaller.Installation(List.of(),List.of(),false));
        when(commands.execute(any(),anyString(),eq(ProjectPlanActivationService.Applied.class),any(),any())).thenAnswer(call -> {
            var result = ((Supplier<ProjectPlanActivationService.Applied>)call.getArgument(3)).get();
            facts = ((Function<ProjectPlanActivationService.Applied,PlatformCommandExecutionApi.SuccessFacts>)call.getArgument(4)).apply(result);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,result);
        });
    }

    @Test void appliesExactlyThePreviewedVersionAndEmitsSafeAuditAndDedicatedReevaluation() {
        var result = apply(preview);
        assertEquals(new ProjectPlanActivationService.Applied(9L,52L,2,5),result);
        var order = inOrder(drafts,nodes,deliverables,milestones,gates,persistence);
        order.verify(drafts).authorize(9L,1L);
        order.verify(drafts).prepare(9L,52L,3,1L,true);
        order.verify(nodes).install(argThat(request -> request.newPlanId()==52L && request.newTaskStateMachineRevisionId()==null));
        order.verify(deliverables).applyPlanChanges(any());
        order.verify(milestones).install(scope,prepared.milestones(),1L);
        order.verify(gates).install(scope,prepared.gates(),1L,"PROJECT_PLAN_APPLY:9:apply-key");
        order.verify(persistence).activate(argThat(write -> write.tenantId()==7L && write.projectId()==9L
                && write.oldPlanVersionId()==51L && write.draftId()==52L && write.expectedDraftVersion()==3
                && write.expectedProjectVersion()==4 && write.executionSnapshot().equals(JsonUtils.toJsonString(prepared.after()))),eq(List.of()),eq(List.of()));
        verifyNoInteractions(machines,progress);
        assertEquals("PROJECT_PLAN_APPLY",facts.operationCode());
        assertEquals(1,facts.businessEvents().size());
        assertEquals(ProjectRuleReevaluation.EVENT_TYPE,facts.businessEvents().getFirst().eventType());
        assertFalse(facts.detailSnapshot().contains("private-rule-value"));
        assertFalse(facts.businessEvents().getFirst().eventPayload().contains("private-rule-value"));
        assertEquals(51L,prepared.project().getActivePlanVersionId()); // Installation never rewrites the old in-memory snapshot.
    }

    @Test void runtimeAdvanceAfterPreviewRejectsBeforeAnyInstallation() {
        prepare(preview(5,List.of(),List.of()));
        assertThrows(RuntimeException.class,()->apply(preview));
        verifyNoInteractions(nodes,deliverables,milestones,gates,persistence,machines,progress);
        assertNull(facts);
    }

    @Test void nodeVersionAdvanceRejectsEvenWhenProjectAndDraftVersionsDidNotChange() {
        var old = nodeChange(2);
        preview = preview(4,List.of(old),List.of());
        prepare(preview(4,List.of(nodeChange(3)),List.of()));
        assertThrows(RuntimeException.class,()->apply(preview));
        verifyNoInteractions(nodes,persistence);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"INVALID_RULE", "RULE_BUSINESS_BINDING_UNAVAILABLE"})
    void publicationIssueCannotBeBypassedByEchoingAnInvalidPreview(String code) {
        preview = preview(4,List.of(),List.of(new Issue("tasks[0].completionRuleKey",code,"规则不可执行")));
        prepare(preview);
        assertThrows(RuntimeException.class,()->apply(preview));
        verifyNoInteractions(nodes,deliverables,milestones,gates,persistence);
    }

    @Test void newTasksResolvePublishedStateMachineAndRecomputeProgressAfterActivation() {
        preview = preview(4,List.of(new ProjectPlanExecutionPlanner.Change("task:new","TASK","新任务",
                ProjectPlanExecutionPlanner.Action.CREATE,null,null,null,null,null,null,null,"NEW")),List.of());
        prepare(preview);
        var machine = new TaskStateMachineRevisionDO(); machine.setId(73L);
        when(machines.selectCurrentPublished(any())).thenReturn(machine);
        when(nodes.install(any())).thenReturn(new ProjectPlanStageTaskInstaller.Installation(List.of(),List.of(),true));
        apply(preview);
        var order = inOrder(machines,nodes,persistence,progress);
        order.verify(machines).selectCurrentPublished(argThat(query -> query.getTenantId()==7L && query.getEffectiveAt()!=null));
        order.verify(nodes).install(argThat(request -> request.newTaskStateMachineRevisionId()==73L));
        order.verify(persistence).activate(any(),anyList(),anyList());
        order.verify(progress).recompute(eq(7L),eq(9L),eq(8L),any());
    }

    @Test void missingPublishedTaskMachineRejectsBeforeInstallation() {
        preview = preview(4,List.of(new ProjectPlanExecutionPlanner.Change("task:new","TASK","新任务",
                ProjectPlanExecutionPlanner.Action.CREATE,null,null,null,null,null,null,null,"NEW")),List.of());
        prepare(preview);
        assertThrows(RuntimeException.class,()->apply(preview));
        verifyNoInteractions(nodes,persistence);
    }

    @Test void ownerFailureStopsVersionActivationAndSuccessFacts() {
        doThrow(new IllegalStateException("OWNER_VERSION_CONFLICT")).when(deliverables).applyPlanChanges(any());
        assertThrows(IllegalStateException.class,()->apply(preview));
        verifyNoInteractions(milestones,gates,persistence,progress);
        assertNull(facts);
    }

    @Test void completedReplayReturnsOriginalResultWithoutInstallingAgain() {
        var saved = new ProjectPlanActivationService.Applied(9L,52L,2,5);
        when(commands.execute(any(),anyString(),eq(ProjectPlanActivationService.Applied.class),any(),any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,saved));
        assertEquals(saved,apply(preview));
        verify(drafts,never()).prepare(any(),any(),any(),any(),anyBoolean());
        verifyNoInteractions(nodes,deliverables,milestones,gates,persistence,progress);
        verify(drafts).authorize(9L,1L);
    }

    @Test void deniedPermissionAndInvalidDraftIdentityCannotReserveACommand() {
        assertThrows(RuntimeException.class,()->service.apply(new ProjectPlanActivationService.Apply(9L,99L,preview),1L,"apply-key"));
        doThrow(new IllegalStateException("FORBIDDEN")).when(drafts).authorize(9L,1L);
        assertThrows(IllegalStateException.class,()->apply(preview));
        verifyNoInteractions(commands,nodes,persistence);
    }

    private ProjectPlanActivationService.Applied apply(ProjectPlanDraftService.Preview observed) {
        return service.apply(new ProjectPlanActivationService.Apply(9L,52L,observed),1L,"apply-key");
    }
    private ProjectPlanDraftService.Preview preview(int projectVersion,List<ProjectPlanExecutionPlanner.Change> changes,List<Issue> issues) {
        return new ProjectPlanDraftService.Preview(51L,52L,3,projectVersion,List.of(),List.of(),changes,List.of(),List.of(),List.of(),issues);
    }
    private ProjectPlanExecutionPlanner.Change nodeChange(int version) {
        return new ProjectPlanExecutionPlanner.Change("task:one","TASK","现场工勘",ProjectPlanExecutionPlanner.Action.REBASE_CURRENT,
                20L,70L,version,4,2,51L,"SURVEY","SURVEY");
    }
    private void prepare(ProjectPlanDraftService.Preview current) {
        var project = new ProjectMasterDO(); project.setTenantId(7L); project.setId(9L); project.setVersion(4);
        project.setActivePlanVersionId(51L); project.setTaskProgressVersion(8L);
        var effective = new ProjectPlanVersionDO(); effective.setId(51L);
        var draft = new ProjectPlanVersionDO(); draft.setId(52L); draft.setVersion(3); draft.setRevisionNo(2);
        var after = new TemplateExecutionSnapshot(); after.setClosureRuleKey("private-rule-value");
        prepared = new ProjectPlanDraftService.Prepared(current,project,effective,draft,new TemplateExecutionSnapshot(),after,
                List.of(),List.of(),List.of(),new ProjectPlanExecutionPlanner.Plan(current.executionChanges(),List.of()),
                new ProjectPlanDeliverablePlanner.Plan(List.of(),List.of(),List.of()),
                new ProjectPlanMilestoneInstaller.Plan(List.of(),List.of(),List.of()),new ProjectPlanGateInstaller.Plan(List.of(),List.of(),List.of()));
        when(drafts.prepare(9L,52L,3,1L,true)).thenReturn(prepared);
    }
}
