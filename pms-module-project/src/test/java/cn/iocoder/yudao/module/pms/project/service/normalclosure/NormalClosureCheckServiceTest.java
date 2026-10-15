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
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper plans = mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper.class);
    private final ProjectMasterDO project = new ProjectMasterDO();
    private NormalClosureCheckService checks;
    private ProjectTaskInstanceDO task;
    private ProjectTaskExecutionContractDO contract;

    @BeforeEach
    void setUp() {
        checks = new NormalClosureCheckService(closureProjects, mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.class),
                contracts, links, graphs, mock(ProjectStageGateProviderRegistry.class), business, evaluator,
                mock(cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator.class),
                descendantGuard, mock(NormalClosureAccess.class), resultEvidence,
                new NormalClosurePolicyResolver(plans));
        project.setId(9L); project.setTenantId(7L); project.setVersion(3L); project.setLifecycleStatus("ACTIVE");
        project.setClosurePolicySnapshot("{}"); project.setTaskTreeVersion(2L); project.setTaskProgressVersion(4L);
        task = new ProjectTaskInstanceDO();
        task.setId(21L); task.setTenantId(7L); task.setProjectId(9L); task.setStatus("DONE"); task.setVersion(2);
        contract = new ProjectTaskExecutionContractDO();
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
        when(evaluator.evaluate(eq(contract), eq("owner-v1"), eq(facts.links()), any()))
                .thenReturn(new TaskBusinessCompletionEvaluator.Result(true, List.of(), Map.of()));
        when(resultEvidence.revalidate(eq(project), eq(task), eq(contract), any()))
                .thenReturn(new ProjectResultEvidenceGuard.Proof(true, "NO_RESULT_SUBSCRIPTION", List.of()));
    }

    @Test
    void replacingEffectivePlanInvalidatesEvidenceEvenWhenPolicyIsIdentical() {
        var plan = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO();
        plan.setId(51L); plan.setTenantId(7L); plan.setProjectId(9L); plan.setStatus("EFFECTIVE"); plan.setRevisionNo(2);
        plan.setExecutionSnapshot("{\"executionSchemaVersion\":2,\"closurePolicy\":{}}");
        project.setActivePlanVersionId(51L); when(plans.selectById(51L)).thenReturn(plan);
        var before = checks.evaluateLocked(project, 15L, 103L, "plan-before");
        plan.setId(52L); plan.setRevisionNo(3); project.setActivePlanVersionId(52L);
        when(plans.selectById(52L)).thenReturn(plan);
        var after = checks.evaluateLocked(project, 15L, 103L, "plan-after");
        org.junit.jupiter.api.Assertions.assertEquals(before.policyJson(), after.policyJson());
        org.junit.jupiter.api.Assertions.assertNotEquals(before.sourceDigest(), after.sourceDigest());
    }

    @Test
    void optionalInProgressTaskSkipsFailingOwnerAndSubscriptionRevalidation() {
        task.setRequiredTask(false); task.setStatus("IN_PROGRESS"); task.setActualStartTime(java.time.LocalDateTime.now());
        when(business.inspectLinkedFactsSnapshot(any(), any(), any(), any())).thenThrow(new IllegalStateException("owner unavailable"));
        var evaluation = checks.evaluateLocked(project, 15L, 103L, "optional-owner");
        org.junit.jupiter.api.Assertions.assertTrue(evaluation.passed(), evaluation.evidence());
        org.mockito.Mockito.verifyNoInteractions(business, evaluator);
        org.mockito.Mockito.verify(resultEvidence, org.mockito.Mockito.never()).revalidate(any(), any(), any(), any());
        org.junit.jupiter.api.Assertions.assertEquals("IN_PROGRESS", task.getStatus());
    }

    @Test
    void absentOrTrueRequiredInProgressTaskStillFailsAllTasksDone() {
        task.setStatus("IN_PROGRESS");
        for (Boolean required : new Boolean[]{null, true}) {
            task.setRequiredTask(required);
            var evaluation = checks.evaluateLocked(project, 15L, 103L, "required-task");
            org.junit.jupiter.api.Assertions.assertFalse(evaluation.passed());
            org.junit.jupiter.api.Assertions.assertTrue(evaluation.checks().stream().anyMatch(check -> "ALL_TASKS_DONE".equals(check.code()) && !check.passed()));
        }
    }

    @Test
    void completedRequiredTaskStillRejectsUnsatisfiedOwnerFact() {
        task.setRequiredTask(true);
        when(evaluator.evaluate(eq(contract), eq("owner-v1"), any(), any()))
                .thenReturn(new TaskBusinessCompletionEvaluator.Result(false, List.of("OWNER_FACT_MISSING"), Map.of()));
        var evaluation = checks.evaluateLocked(project, 15L, 103L, "required-owner");
        org.junit.jupiter.api.Assertions.assertFalse(evaluation.passed());
        org.junit.jupiter.api.Assertions.assertTrue(evaluation.checks().stream().anyMatch(check -> "TASK_BUSINESS_FACTS".equals(check.code()) && !check.passed()));
        verify(business).lockAndRevalidateLinkedFacts(21L, 7L, 103L, "required-owner", "owner-v1");
    }

    @Test
    void completedRequiredEndUserTaskRevalidatesCurrentMembersBeforeClosureWithoutRewritingTask() {
        var endUsers = mock(cn.iocoder.yudao.module.pms.project.service.projectmember.ProjectEndUserCompletionService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(checks, "endUsers", endUsers);
        task.setRequiredTask(true);
        contract.setWorkBindingTypeCode("PAGE");
        contract.setBindingParameterSnapshot("{\"routePath\":\"/pms/project-management/project-master-detail\"}");
        contract.setCompletionRuleSnapshot("{\"predicate\":\"PROJECT_END_USER_EXISTS\",\"parameters\":{}}");
        var query = new cn.iocoder.yudao.module.pms.project.service.projectmember.ProjectEndUserCompletionService.Query(7L, 9L);
        when(endUsers.lockAndInspect(query)).thenReturn(
                new cn.iocoder.yudao.module.pms.project.service.projectmember.ProjectEndUserCompletionService.Fact(9L, List.of()),
                new cn.iocoder.yudao.module.pms.project.service.projectmember.ProjectEndUserCompletionService.Fact(9L, List.of(
                        new cn.iocoder.yudao.module.pms.project.service.projectmember.ProjectEndUserCompletionService.MemberFact(41L, 1))));

        var removed = checks.evaluateLocked(project, 15L, 103L, "end-user-removed");
        org.junit.jupiter.api.Assertions.assertFalse(removed.passed());
        org.junit.jupiter.api.Assertions.assertTrue(removed.checks().stream()
                .anyMatch(check -> "TASK_END_USER_FACTS".equals(check.code()) && !check.passed()));
        var present = checks.evaluateLocked(project, 15L, 103L, "end-user-present");
        org.junit.jupiter.api.Assertions.assertTrue(present.checks().stream()
                .anyMatch(check -> "TASK_END_USER_FACTS".equals(check.code()) && check.passed()));
        org.mockito.Mockito.verify(endUsers, org.mockito.Mockito.times(2)).lockAndInspect(query);
        org.junit.jupiter.api.Assertions.assertEquals("DONE", task.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(2, task.getVersion());
        org.mockito.Mockito.verifyNoInteractions(business, evaluator);
    }

    @Test
    void legacyMemberPageNativeParametersRemainValidWithoutAutomaticEndUserCheck() {
        contract.setWorkBindingTypeCode("PAGE");
        contract.setBindingParameterSnapshot("{\"routePath\":\"/pms/project-management/project-master-detail\"}");
        contract.setCompletionRuleTypeCode("TASK_NATIVE_STATUS");
        contract.setCompletionRuleSnapshot("{\"requiredStatus\":\"DONE\"}");
        var evaluation = checks.evaluateLocked(project, 15L, 103L, "legacy-member-page");
        org.junit.jupiter.api.Assertions.assertTrue(evaluation.passed(), evaluation.evidence());
        org.junit.jupiter.api.Assertions.assertTrue(evaluation.checks().stream()
                .noneMatch(check -> "TASK_END_USER_FACTS".equals(check.code())));
        org.junit.jupiter.api.Assertions.assertEquals("{\"requiredStatus\":\"DONE\"}", contract.getCompletionRuleSnapshot());
    }

    @Test
    void parameterOnlyAutomaticEndUserRuleStillRejectsMissingMemberFact() {
        var endUsers = mock(cn.iocoder.yudao.module.pms.project.service.projectmember.ProjectEndUserCompletionService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(checks, "endUsers", endUsers);
        contract.setWorkBindingTypeCode("PAGE");
        contract.setBindingParameterSnapshot("{\"routePath\":\"/pms/project-management/project-master-detail\"}");
        contract.setCompletionRuleTypeCode("PROJECT_END_USER_EXISTS");
        contract.setCompletionRuleSnapshot("{}");
        when(endUsers.lockAndInspect(any())).thenReturn(
                new cn.iocoder.yudao.module.pms.project.service.projectmember.ProjectEndUserCompletionService.Fact(9L, List.of()));
        var evaluation = checks.evaluateLocked(project, 15L, 103L, "legacy-end-user");
        org.junit.jupiter.api.Assertions.assertFalse(evaluation.passed());
        org.junit.jupiter.api.Assertions.assertTrue(evaluation.checks().stream()
                .anyMatch(check -> "TASK_END_USER_FACTS".equals(check.code()) && !check.passed()));
        org.junit.jupiter.api.Assertions.assertEquals("DONE", task.getStatus());
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
