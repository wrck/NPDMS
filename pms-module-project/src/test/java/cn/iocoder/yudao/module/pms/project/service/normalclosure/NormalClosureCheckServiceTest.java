package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure.ClosureProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.ProjectTaskBusinessLinkMapper;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectResultEvidenceGuard;
import cn.iocoder.yudao.module.pms.project.service.projectclosureguard.ProjectClosureGuardResult;
import cn.iocoder.yudao.module.pms.project.service.projectclosureguard.ProjectClosureGuardService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphResolver;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessLinkedFacts;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskBusinessCompletionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NormalClosureCheckServiceTest {
    private final ClosureProjectMapper closureProjects = mock(ClosureProjectMapper.class);
    private final ProjectTaskExecutionContractMapper contracts = mock(ProjectTaskExecutionContractMapper.class);
    private final ProjectTaskBusinessLinkMapper links = mock(ProjectTaskBusinessLinkMapper.class);
    private final ProjectRuntimeGraphResolver graphs = mock(ProjectRuntimeGraphResolver.class);
    private final ProjectTaskBusinessService business = mock(ProjectTaskBusinessService.class);
    private final TaskBusinessCompletionEvaluator evaluator = mock(TaskBusinessCompletionEvaluator.class);
    private final ProjectClosureGuardService descendantGuard = mock(ProjectClosureGuardService.class);
    private final NormalClosureResultEvidence resultEvidence = mock(NormalClosureResultEvidence.class);
    private final ProjectMasterDO project = new ProjectMasterDO();
    private NormalClosureCheckService checks;

    @BeforeEach
    void setUp() {
        checks = new NormalClosureCheckService(closureProjects, mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.class),
                contracts, links, graphs, mock(ProjectStageGateProviderRegistry.class), business, evaluator,
                descendantGuard, mock(NormalClosureAccess.class), resultEvidence);
        project.setId(9L); project.setTenantId(7L); project.setVersion(3L); project.setLifecycleStatus("ACTIVE");
        project.setClosurePolicySnapshot("{}"); project.setTaskTreeVersion(2L); project.setTaskProgressVersion(4L);
        var task = new ProjectTaskInstanceDO();
        task.setId(21L); task.setTenantId(7L); task.setProjectId(9L); task.setStatus("DONE"); task.setVersion(2);
        var contract = new ProjectTaskExecutionContractDO();
        contract.setId(31L); contract.setTenantId(7L); contract.setProjectTaskId(21L); contract.setContractVersion(1);
        contract.setWorkBindingTypeCode("BUSINESS_OBJECT"); contract.setCompletionRuleSnapshot("{}"); contract.setDefinitionSnapshot("{}");
        var stage = new ProjectStageInstanceDO();
        stage.setId(11L); stage.setVersion(1); stage.setGraphVersion(1L); stage.setDefinitionRevisionId(2L); stage.setCode("S6");
        var graph = new ProjectRuntimeGraphResolver.Resolution(stage, null,
                new StageTransitionTargetResolver.Result(StageTransitionTargetResolver.Status.TERMINAL, null, null, List.of(), List.of()),
                StageTransitionTargetResolver.ConditionStatus.SATISFIED, List.of(), List.of());
        var facts = new TaskBusinessLinkedFacts("owner-v1", List.of());

        when(descendantGuard.evaluate(any(), anyLong(), any())).thenReturn(new ProjectClosureGuardResult(true, 15L, List.of(), List.of()));
        when(closureProjects.selectTasksForUpdate(any())).thenReturn(List.of(task));
        when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        when(links.selectActiveForUpdate(any())).thenReturn(List.of());
        when(resultEvidence.lockRounds(project)).thenReturn(List.of());
        when(graphs.resolveForClosure(project)).thenReturn(graph);
        when(graphs.lockClosureGates(project)).thenReturn(new ProjectRuntimeGraphResolver.ClosureGates(List.of(), List.of()));
        when(business.inspectLinkedFactsSnapshot(any(), any(), any(), any())).thenReturn(facts);
        when(business.lockAndRevalidateLinkedFacts(any(), any(), any(), any(), any())).thenReturn(facts);
        when(evaluator.evaluate(eq(contract), eq("owner-v1"), eq(facts.links())))
                .thenReturn(new TaskBusinessCompletionEvaluator.Result(true, List.of(), Map.of()));
        when(resultEvidence.revalidate(eq(project), eq(task), eq(contract), any()))
                .thenReturn(new ProjectResultEvidenceGuard.Proof(true, "NO_RESULT_SUBSCRIPTION", List.of()));
    }

    @Test
    void usesApplicantForDescendantGuardAndReviewerForOwnerFactInspectionAndLocking() {
        checks.evaluateLocked(project, 15L, 103L, 1L, "closure-1");

        verify(descendantGuard).evaluate(9L, 15L, new ProjectClosureGuardService.Actor(7L, 103L, "closure-1"));
        verify(business).inspectLinkedFactsSnapshot(21L, 7L, 1L, "closure-1");
        verify(business).lockAndRevalidateLinkedFacts(21L, 7L, 1L, "closure-1", "owner-v1");
    }

    @Test
    void originalEntryUsesApplicantForBothProjectAndOwnerFactChecks() {
        checks.evaluateLocked(project, 15L, 103L, "closure-1");

        verify(descendantGuard).evaluate(9L, 15L, new ProjectClosureGuardService.Actor(7L, 103L, "closure-1"));
        verify(business).inspectLinkedFactsSnapshot(21L, 7L, 103L, "closure-1");
        verify(business).lockAndRevalidateLinkedFacts(21L, 7L, 103L, "closure-1", "owner-v1");
    }
}
