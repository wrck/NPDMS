package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectGateRuleServiceTest {
    static RuleEngineTestFixture engine;
    @BeforeAll static void start() { engine = new RuleEngineTestFixture(); }
    @AfterAll static void stop() { engine.close(); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    final ProjectTaskRuntimeMapper projects = mock(ProjectTaskRuntimeMapper.class);
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    final ProjectGateInstanceMapper gates = mock(ProjectGateInstanceMapper.class);
    final ProjectGateReferenceInstanceMapper refs = mock(ProjectGateReferenceInstanceMapper.class);
    final OperationAuditApi audit = mock(OperationAuditApi.class);
    final ProjectStageGateFactProviderApi owner = mock(ProjectStageGateFactProviderApi.class);
    ProjectGateRuleService service;
    ProjectGateInstanceDO gate;
    ProjectPlanVersionDO plan;
    ProjectMasterDO project;
    TemplateExecutionSnapshot snapshot;
    ProjectGateReferenceInstanceDO taskRef, processRef;
    ProjectStageGateOutcome taskOutcome = ProjectStageGateOutcome.SATISFIED;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        when(owner.providerKeys()).thenReturn(Set.of("PROJ_TASK", "BPM_PROCESS"));
        var compiler = new ProjectRuleCompiler();
        service = new ProjectGateRuleService(projects,plans,graph,gates,refs,
                new ProjectRuntimeRuleEvaluator(new ProjectStageGateProviderRegistry(List.of(owner)),compiler,engine.evaluator(),mock(ProjectDecisionTableService.class),mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessFactSourceService.class)),audit);
        project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L); project.setActivePlanVersionId(51L); project.setLifecycleStatus("ACTIVE");
        when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project);
        gate = new ProjectGateInstanceDO(); gate.setId(21L); gate.setTenantId(7L); gate.setProjectId(9L); gate.setGateCode("READY");
        gate.setStageCode("PREP"); gate.setGateType("ENTRY"); gate.setVersion(3); gate.setStatus("PENDING");
        when(graph.selectGatesForUpdate(any())).thenReturn(List.of(gate));
        var stage = new ProjectStageInstanceDO(); stage.setId(11L); stage.setStageCode("PREP"); stage.setStatus("ACTIVE");
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(stage));
        taskRef = reference(31L,"TASK","T1"); processRef = reference(32L,"PROCESS","APPROVE");
        when(refs.selectOrderedForUpdate(any())).thenReturn(List.of(taskRef,processRef));
        snapshot = new TemplateExecutionSnapshot();
        var definition = new TemplateExecutionSnapshot.GateContract(); definition.setNodeKey("gate:ready"); definition.setCode("READY");
        definition.setStageCode("PREP"); definition.setGateType("ENTRY"); definition.setConditionRuleKey("$gate:gate:ready");
        for (var row : List.of(taskRef,processRef)) {
            var ref = new TemplateExecutionSnapshot.GateReference(); ref.setRefType(row.getRefType()); ref.setRefCode(row.getRefCode()); ref.setRefVersion(row.getRefVersion());
            definition.getReferences().add(ref);
        }
        snapshot.getGates().add(definition);
        snapshot.getRulePrograms().put(definition.getConditionRuleKey(),compiler.compile(JsonUtils.parseTree("""
            {"operator":"ALL","rules":[{"predicate":"TASK","parameters":{"refCode":"T1"}},
              {"predicate":"PROCESS","parameters":{"refCode":"APPROVE"}}]}
            """)));
        plan = new ProjectPlanVersionDO(); plan.setId(51L);
        when(plans.selectEffective(any())).thenAnswer(call -> { plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot)); return plan; });
        when(gates.updateStatusIfMatch(any())).thenReturn(1);
        when(owner.lockAndRevalidate(any())).thenAnswer(call -> {
            ProjectStageGateFactQuery q = call.getArgument(0);
            return new ProjectStageGateFact("TASK".equals(q.refType()) ? "PROJ_TASK" : "BPM_PROCESS",q.refType(),q.refCode(),
                    "private-owner-value","private-fact-value","TASK".equals(q.refType()) ? taskOutcome : ProjectStageGateOutcome.SATISFIED,null);
        });
    }

    @Test void evaluatesNativeLiteFlowAndUsesOnlyTheActualGatesFrozenApprovalReference() {
        var unrelated = new ProjectGateInstanceDO(); unrelated.setId(22L); unrelated.setGateCode("OTHER"); unrelated.setStageCode("PREP");
        when(graph.selectGatesForUpdate(any())).thenReturn(List.of(gate,unrelated));
        var result = service.evaluate(9L,"READY",1L,"gate-test");
        assertTrue(result.evaluation().matched()); assertEquals("READY:PASSED:4",result.gateSnapshot());
        assertEquals(2,result.evaluation().conditions().size()); assertFalse(result.evaluation().steps().isEmpty());
        verify(owner).lockAndRevalidate(argThat(q -> "PROCESS".equals(q.refType()) && q.gateId()==21L && q.gateReferenceId()==32L));
        verify(gates).updateStatusIfMatch(argThat(q -> q.expectedVersion()==3 && "PENDING".equals(q.expectedStatus()) && "PASSED".equals(q.targetStatus())));
        var detail = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq(7L),eq(1L),eq("gate-test"),eq("PROJECT_GATE_RULE_EVALUATED"),eq("ProjectGate"),eq("21"),eq("SUCCESS"),detail.capture());
        assertFalse(JsonUtils.toJsonString(detail.getValue()).contains("private-"));
        assertEquals("PENDING",gate.getStatus()); assertEquals(3,gate.getVersion());
    }

    @Test void unknownOrUnsatisfiedFactsInvalidateOldPassAndCannotBeMistakenForSuccess() {
        gate.setStatus("PASSED"); taskOutcome = ProjectStageGateOutcome.DEPENDENCY_UNAVAILABLE;
        var unknown = service.evaluate(9L,"READY",1L,"unknown");
        assertEquals(RuleEvaluation.Outcome.UNKNOWN,unknown.evaluation().outcome());
        assertEquals("READY:PENDING:4",unknown.gateSnapshot());
        taskOutcome = ProjectStageGateOutcome.UNSATISFIED;
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED,service.evaluate(9L,"READY",1L,"false").evaluation().outcome());
        verify(gates,times(2)).updateStatusIfMatch(argThat(q -> "PENDING".equals(q.targetStatus())));
    }

    @Test void repeatedSatisfiedEvaluationDoesNotRewriteStatusVersionOrAudit() {
        gate.setStatus("PASSED");
        assertTrue(service.evaluate(9L,"READY",1L,"repeat").evaluation().matched());
        verify(gates,never()).updateStatusIfMatch(any()); verifyNoInteractions(audit);
        verify(owner,times(2)).lockAndRevalidate(any()); // Still checks live facts, not the cached pass.
    }

    @Test void changedReferenceRevisionAndMissingFrozenProgramFailClosedWithoutReadingOwner() {
        gate.setStatus("PASSED"); processRef.setRefVersion("2");
        assertEquals(RuleEvaluation.Outcome.UNKNOWN,service.evaluate(9L,"READY",1L,"revision").evaluation().outcome());
        processRef.setRefVersion("1"); snapshot.getRulePrograms().clear();
        assertEquals(RuleEvaluation.Outcome.UNKNOWN,service.evaluate(9L,"READY",1L,"program").evaluation().outcome());
        verify(owner,never()).lockAndRevalidate(any());
        verify(gates,times(2)).updateStatusIfMatch(argThat(q -> "PENDING".equals(q.targetStatus())));
    }

    @Test void staleStatusWriteAndAuditFailurePropagateForTransactionRollback() {
        when(gates.updateStatusIfMatch(any())).thenReturn(0);
        assertThrows(IllegalStateException.class,() -> service.evaluate(9L,"READY",1L,"stale"));
        verifyNoInteractions(audit);
        when(gates.updateStatusIfMatch(any())).thenReturn(1);
        doThrow(new IllegalStateException("audit failed")).when(audit).record(any(),any(),any(),any(),anyString(),anyString(),anyString(),any());
        assertThrows(IllegalStateException.class,() -> service.evaluate(9L,"READY",1L,"audit"));
    }

    @Test void closedProjectAndForeignTenantDoNotChangeGateHistory() {
        project.setLifecycleStatus("CLOSED");
        assertFalse(service.evaluate(9L,"READY",1L,"closed").evaluation().matched());
        project.setLifecycleStatus("ACTIVE"); project.setTenantId(8L);
        assertFalse(service.evaluate(9L,"READY",1L,"foreign").evaluation().matched());
        verifyNoInteractions(graph,gates,refs,plans,audit);
    }

    private ProjectGateReferenceInstanceDO reference(Long id,String type,String code) {
        var ref = new ProjectGateReferenceInstanceDO(); ref.setId(id); ref.setTenantId(7L); ref.setGateId(21L);
        ref.setRefType(type); ref.setRefCode(code); ref.setRefVersion("1"); ref.setVersion(0); return ref;
    }
}
