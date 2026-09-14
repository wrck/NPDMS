package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectStageGateProcessContextResolverTest {
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    final ProjectGateReferenceInstanceMapper refs = mock(ProjectGateReferenceInstanceMapper.class);
    final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    final ProjectStageGateProcessContextResolver resolver = new ProjectStageGateProcessContextResolver(plans, graph, refs, executions);
    ProjectMasterDO project;
    ProjectPlanVersionDO plan;
    ProjectStageInstanceDO selectedStage;
    ProjectGateInstanceDO gate;
    ProjectGateReferenceInstanceDO reference;
    ProjectNodeExecutionDO round;
    TemplateExecutionSnapshot snapshot;

    @BeforeEach void setup() {
        project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L); project.setVersion(4);
        project.setLifecycleStatus("ACTIVE"); project.setActivePlanVersionId(51L); project.setCurrentStage("OTHER");
        selectedStage = stage(11L, "PREP");
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(stage(12L, "OTHER"), selectedStage));
        gate = new ProjectGateInstanceDO(); gate.setId(21L); gate.setTenantId(7L); gate.setProjectId(9L);
        gate.setGateCode("GATE"); gate.setGateType("EXIT"); gate.setStageCode("PREP");
        when(graph.selectGatesForUpdate(any())).thenReturn(List.of(gate));
        reference = new ProjectGateReferenceInstanceDO(); reference.setId(22L); reference.setTenantId(7L);
        reference.setGateId(21L); reference.setRefType("APPROVAL"); reference.setRefCode("approval"); reference.setRefVersion("def-1");
        when(refs.selectOrderedForUpdate(any())).thenReturn(List.of(reference));
        snapshot = new TemplateExecutionSnapshot();
        var frozenStage = new TemplateExecutionSnapshot.StageContract(); frozenStage.setNodeKey("stage:prep"); frozenStage.setCode("PREP");
        snapshot.getStages().add(frozenStage);
        var frozenGate = new TemplateExecutionSnapshot.GateContract(); frozenGate.setCode("GATE"); frozenGate.setStageCode("PREP"); frozenGate.setGateType("EXIT");
        var frozenRef = new TemplateExecutionSnapshot.GateReference(); frozenRef.setRefType("APPROVAL"); frozenRef.setRefCode("approval"); frozenRef.setRefVersion("def-1");
        frozenGate.getReferences().add(frozenRef); snapshot.getGates().add(frozenGate);
        plan = new ProjectPlanVersionDO(); plan.setId(51L);
        when(plans.selectEffective(any())).thenAnswer(call -> { plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot)); return plan; });
        round = new ProjectNodeExecutionDO(); round.setId(61L); round.setTenantId(7L); round.setProjectId(9L);
        round.setPlanVersionId(51L); round.setNodeInstanceId(11L); round.setNodeKind("STAGE"); round.setNodeKey("stage:prep");
        round.setContractId(71L); round.setCurrentMarker(1); round.setStatus("ACTIVE");
        round.setVersion(0);
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round));
        when(executions.selectCurrentStageContextForUpdate(any())).thenAnswer(call -> context(selectedStage.getStatus(), round.getStatus()));
    }

    @Test void resolvesTheSelectedActiveStageAmongParallelStagesWithoutEvaluatingRules() {
        var result = resolver.resolve(project, 22L);
        assertSame(gate, result.gate()); assertSame(reference, result.reference());
        assertEquals("OTHER", project.getCurrentStage());
        verify(executions).selectCurrentStageContextForUpdate(argThat(query -> query.projectId().equals(9L)
                && query.tenantId().equals(7L) && query.stageId().equals(11L) && query.executionContractId().equals(71L)));
    }

    @Test void missingPinIsRejectedEvenWhenRuntimeAndSnapshotBothLackIt() {
        reference.setRefVersion(null);
        snapshot.getGates().getFirst().getReferences().getFirst().setRefVersion(null);
        assertThrows(RuntimeException.class, () -> resolver.resolve(project, 22L));
    }

    @Test void pendingEntryGateCanRunBeforeAdmissionWithoutActivatingItsStage() {
        gate.setGateType("ENTRY"); snapshot.getGates().getFirst().setGateType("ENTRY");
        selectedStage.setStatus("PENDING"); round.setStatus("PENDING");
        assertSame(reference, resolver.resolve(project, 22L).reference());
        resolver.recordStarted(resolver.resolve(project, 22L), 81L);
        verify(executions, never()).beginStageHandlingIfCurrent(any());
        assertEquals("PENDING", selectedStage.getStatus()); assertEquals("PENDING", round.getStatus());
        verify(executions, never()).activateIfPending(any());
    }

    @ParameterizedTest @ValueSource(strings = {"DONE", "TERMINATED", "PENDING"})
    void endedOrNotAdmittedExitGateCannotStartNewWork(String status) {
        selectedStage.setStatus(status); round.setStatus(status);
        assertThrows(RuntimeException.class, () -> resolver.resolve(project, 22L));
    }

    @ParameterizedTest @ValueSource(strings = {"projectClosed", "plan", "referenceTenant", "gateProject", "definition", "referenceVersion",
            "missingReference", "roundPlan", "roundNode", "retired", "contract", "execution"})
    void rejectsReferencesOutsideTheEffectivePlanOrExecution(String defect) {
        switch (defect) {
            case "projectClosed" -> project.setLifecycleStatus("NORMAL_CLOSED");
            case "plan" -> plan.setId(52L);
            case "referenceTenant" -> reference.setTenantId(8L);
            case "gateProject" -> gate.setProjectId(10L);
            case "definition" -> snapshot.getGates().clear();
            case "referenceVersion" -> reference.setRefVersion("def-2");
            case "missingReference" -> when(refs.selectOrderedForUpdate(any())).thenReturn(List.of());
            case "roundPlan" -> round.setPlanVersionId(50L);
            case "roundNode" -> round.setNodeKey("another-stage");
            case "retired" -> round.setCurrentMarker(null);
            case "contract" -> when(executions.selectCurrentStageContextForUpdate(any())).thenReturn(null);
            case "execution" -> round.setId(62L);
        }
        assertThrows(RuntimeException.class, () -> resolver.resolve(project, 22L));
    }

    private ProjectStageExecutionRecord context(String stageStatus, String executionStatus) {
        return new ProjectStageExecutionRecord(9L, 4, "ACTIVE", 11L, 0, stageStatus, 71L, 1, 51L, 61L, 0, 2, executionStatus);
    }

    @Test void successfulProcessStartRecordsFirstHandlingAndRejectsAStaleWrite() {
        var context = resolver.resolve(project, 22L);
        when(executions.beginStageHandlingIfCurrent(any())).thenReturn(1);
        resolver.recordStarted(context, 81L);
        verify(executions).beginStageHandlingIfCurrent(argThat(q -> q.executionId().equals(61L)
                && q.planVersionId().equals(51L) && q.contractId().equals(71L) && q.expectedVersion().equals(0) && q.actorId().equals(81L)));
        round.setStartedAt(java.time.LocalDateTime.now());
        resolver.recordStarted(context, 81L);
        verify(executions, times(1)).beginStageHandlingIfCurrent(any());
        round.setStartedAt(null);
        when(executions.beginStageHandlingIfCurrent(any())).thenReturn(0);
        assertThrows(RuntimeException.class, () -> resolver.recordStarted(context, 81L));
    }

    private ProjectStageInstanceDO stage(Long id, String code) {
        var stage = new ProjectStageInstanceDO(); stage.setId(id); stage.setTenantId(7L); stage.setProjectId(9L);
        stage.setStageCode(code); stage.setStatus("ACTIVE"); return stage;
    }
}
