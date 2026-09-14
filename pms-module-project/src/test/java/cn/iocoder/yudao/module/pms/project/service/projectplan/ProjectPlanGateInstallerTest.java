package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateRunningProcess;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanProjectionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TemplateInstantiator;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectPlanGateInstallerTest {
    final ProjectRuntimeGraphMapper graph=mock(ProjectRuntimeGraphMapper.class);
    final ProjectGateInstanceMapper gates=mock(ProjectGateInstanceMapper.class);
    final ProjectGateReferenceInstanceMapper references=mock(ProjectGateReferenceInstanceMapper.class);
    final ProjectPlanProjectionMapper projections=mock(ProjectPlanProjectionMapper.class);
    final OperationAuditApi audit=mock(OperationAuditApi.class);
    final ProjectStageGateProcessOwnerApi processes=mock(ProjectStageGateProcessOwnerApi.class);
    final ProjectPlanGateInstaller installer=new ProjectPlanGateInstaller(graph,gates,references,projections,audit,processes);
    final ProjectPlanScopeQuery scope=new ProjectPlanScopeQuery(1L,9L);
    final TemplateExecutionSnapshot before=new TemplateExecutionSnapshot();
    final ProjectGateInstanceDO actual;
    final List<ProjectGateReferenceInstanceDO> refs;

    ProjectPlanGateInstallerTest() {
        var node=new TemplateExecutionSnapshot.GateContract(); node.setNodeKey("gate:one"); node.setCode("G1");
        node.setName("delivery gate"); node.setStageCode("PREP"); node.setGateType("EXIT");
        var task=new TemplateExecutionSnapshot.GateReference(); task.setRefType("TASK"); task.setRefCode("T1");
        var process=new TemplateExecutionSnapshot.GateReference(); process.setRefType("PROCESS"); process.setRefCode("P1"); process.setRefVersion("1");
        node.setReferences(List.of(task,process)); before.getGates().add(node);
        var instantiated=TemplateInstantiator.instantiateGates(before.toRuntimeContent(),9L);
        actual=instantiated.getGates().getFirst(); actual.setId(20L); actual.setTenantId(1L); actual.setVersion(3); actual.setStatus("PASSED"); actual.setSourceDefinitionId(88L);
        refs=instantiated.getGateReferences();
        for (int i=0;i<refs.size();i++) {var ref=refs.get(i); ref.setId(30L+i); ref.setTenantId(1L); ref.setGateId(20L); ref.setVersion(2);}
        when(graph.selectGatesForUpdate(any())).thenReturn(List.of(actual)); when(references.selectOrderedForUpdate(any())).thenReturn(refs);
        when(projections.gateCodeForRename(any())).thenReturn(1); when(projections.updateGateDefinition(any())).thenReturn(1);
        when(projections.retirePendingGate(any())).thenReturn(1); when(projections.retireGateReference(any())).thenReturn(1);
        when(gates.insert(any(ProjectGateInstanceDO.class))).thenReturn(1); when(references.insert(any(ProjectGateReferenceInstanceDO.class))).thenReturn(1);
        when(gates.updateStatusIfMatch(any())).thenReturn(1);
        when(processes.inspectRunning(any())).thenReturn(List.of());
    }

    @Test void activeProcessPreventsReferenceRemovalUntilOwnerConfirmsItEnded() {
        var after=copy(); after.getGates().getFirst().getReferences().get(1).setRefVersion("2");
        when(processes.inspectRunning(any())).thenReturn(List.of(new ProjectStageGateRunningProcess("pi",31L,"PREP")));
        var blocked=installer.inspect(scope,before,after);
        assertEquals("RUNNING_GATE_PROCESS_CHANGE_FORBIDDEN",blocked.issues().getFirst().code());
        assertThrows(RuntimeException.class,()->installer.install(scope,blocked,7L,"blocked"));
        verifyNoInteractions(gates,projections,audit);
        when(processes.inspectRunning(any())).thenReturn(List.of());
        var allowed=installer.inspect(scope,before,after);
        assertTrue(allowed.issues().isEmpty()); installer.install(scope,allowed,7L,"ended");
        verify(projections).retireGateReference(argThat(q -> q.referenceId().equals(31L)));
    }

    @Test void pendingGateIsNotDeletableWhileItsProcessIsRunning() {
        actual.setStatus("PENDING"); var after=copy(); after.getGates().clear();
        when(processes.inspectRunning(any())).thenReturn(List.of(new ProjectStageGateRunningProcess("pi",31L,"PREP")));
        assertEquals("RUNNING_GATE_PROCESS_CHANGE_FORBIDDEN",installer.inspect(scope,before,after).issues().getFirst().code());
    }

    @Test void unknownProcessActivityBlocksOnlyChangesToProcessIdentity() {
        when(processes.inspectRunning(any())).thenThrow(new IllegalStateException("unavailable"));
        var renamed=copy(); renamed.getGates().getFirst().setName("new name");
        assertTrue(installer.inspect(scope,before,renamed).issues().isEmpty());
        verify(processes,never()).inspectRunning(any());
        var after=copy(); after.getGates().getFirst().getReferences().get(1).setRefCode("P2");
        assertEquals("GATE_PROCESS_ACTIVITY_UNAVAILABLE",installer.inspect(scope,before,after).issues().getFirst().code());
    }

    @Test void movingGateRetiresReferenceIdentitiesInsteadOfReassigningOldProcessHistory() {
        var after=copy(); after.getGates().getFirst().setStageCode("DELIVERY");
        var allowed=installer.inspect(scope,before,after);
        assertTrue(allowed.issues().isEmpty());
        assertEquals(2,allowed.writes().getFirst().retired().size()); assertEquals(2,allowed.writes().getFirst().added().size());
        when(processes.inspectRunning(any())).thenReturn(List.of(new ProjectStageGateRunningProcess("pi",31L,"PREP")));
        assertFalse(installer.inspect(scope,before,after).issues().isEmpty());
    }

    @Test void replacingAStageWithTheSameCodeStillProtectsItsRunningGateReference() {
        var stage = new TemplateExecutionSnapshot.StageContract(); stage.setNodeKey("old-stage"); stage.setCode("PREP");
        before.getStages().add(stage);
        var after=copy(); after.getStages().getFirst().setNodeKey("new-stage");
        when(processes.inspectRunning(any())).thenReturn(List.of(new ProjectStageGateRunningProcess("pi",31L,"PREP")));
        assertEquals("RUNNING_GATE_PROCESS_CHANGE_FORBIDDEN",installer.inspect(scope,before,after).issues().getFirst().code());
    }
    @Test void unchangedGateKeepsResultAndReferenceIdentitiesWithoutWrites() {
        var plan=installer.inspect(scope,before,copy()); installer.install(scope,plan,7L,"plan-1");
        assertTrue(plan.issues().isEmpty()); assertTrue(plan.changes().isEmpty());
        verifyNoInteractions(gates,projections,audit); verify(references,never()).insert(any(ProjectGateReferenceInstanceDO.class));
    }
    @Test void displayRenameDoesNotInvalidateTheExistingPassedJudgment() {
        var after=copy(); after.getGates().getFirst().setCode("RENAMED"); after.getGates().getFirst().setName("new name");
        var plan=installer.inspect(scope,before,after); assertFalse(plan.changes().getFirst().reevaluationRequired());
        installer.install(scope,plan,7L,"plan-2");
        verify(projections).gateCodeForRename(any()); verify(projections).updateGateDefinition(any());
        verify(gates,never()).updateStatusIfMatch(any()); verify(projections,never()).retireGateReference(any()); verifyNoInteractions(audit);
    }
    @Test void changedReferencesInvalidateOldResultAndOnlyRetireChangedReferences() {
        var after=copy(); after.getGates().getFirst().getReferences().getFirst().setRefCode("T2");
        String original=JsonUtils.toJsonString(refs), oldGate=JsonUtils.toJsonString(actual);
        var plan=installer.inspect(scope,before,after); assertTrue(plan.changes().getFirst().reevaluationRequired());
        installer.install(scope,plan,7L,"plan-3");
        verify(projections).retireGateReference(argThat(q -> q.referenceId()==30L && q.gateId()==20L && q.expectedVersion()==2));
        verify(references).insert(argThat((ProjectGateReferenceInstanceDO row) -> "T2".equals(row.getRefCode()) && row.getGateId()==20L && row.getVersion()==0));
        verify(gates).updateStatusIfMatch(argThat(q -> q.gateId()==20L && q.expectedVersion()==4 && q.expectedStatus().equals("PASSED") && q.targetStatus().equals("PENDING")));
        verify(audit).record(eq(1L),eq(7L),eq("plan-3"),eq("PROJECT_GATE_PLAN_RESULT_INVALIDATED"),eq("ProjectGate"),eq("20"),eq("SUCCESS"),argThat(detail -> detail.get("previousStatus").equals("PASSED") && detail.get("previousVersion").equals(3)));
        assertEquals(original,JsonUtils.toJsonString(refs)); assertEquals(oldGate,JsonUtils.toJsonString(actual));
    }
    @Test void processRevisionChangeGetsANewReferenceAndDoesNotReusePreviousApprovalIdentity() {
        var after=copy(); after.getGates().getFirst().getReferences().get(1).setRefVersion("2");
        var plan=installer.inspect(scope,before,after); installer.install(scope,plan,7L,"plan-4");
        verify(projections).retireGateReference(argThat(q -> q.referenceId()==31L));
        verify(references).insert(argThat((ProjectGateReferenceInstanceDO ref) -> ref.getId()==null && "P1".equals(ref.getRefCode()) && "2".equals(ref.getRefVersion())));
        assertEquals("1",refs.get(1).getRefVersion());
    }
    @Test void cannotDeleteEvaluatedGateButPendingReplacementGetsIndependentGateIdentity() {
        var after=copy(); after.getGates().getFirst().setNodeKey("gate:replacement");
        var protectedPlan=installer.inspect(scope,before,after);
        assertEquals("EVALUATED_GATE_DELETE_FORBIDDEN",protectedPlan.issues().getFirst().code());
        assertThrows(RuntimeException.class,() -> installer.install(scope,protectedPlan,7L,"invalid")); verifyNoInteractions(projections,gates,audit);
        actual.setStatus("PENDING"); var plan=installer.inspect(scope,before,after); installer.install(scope,plan,7L,"plan-5");
        var inserted=org.mockito.ArgumentCaptor.forClass(ProjectGateInstanceDO.class); verify(gates).insert(inserted.capture());
        assertNotEquals(20L,inserted.getValue().getId()); assertEquals("PENDING",inserted.getValue().getStatus());
        verify(references,times(2)).insert(argThat((ProjectGateReferenceInstanceDO row) -> row.getGateId().equals(inserted.getValue().getId())));
    }
    @Test void staleReferenceCannotSilentlyInvalidateOrAuditASuccessfulChange() {
        var after=copy(); after.getGates().getFirst().getReferences().getFirst().setRefCode("T2");
        when(projections.retireGateReference(any())).thenReturn(0);
        assertThrows(RuntimeException.class,() -> installer.install(scope,installer.inspect(scope,before,after),7L,"stale"));
        verify(gates,never()).updateStatusIfMatch(any()); verifyNoInteractions(audit);
    }
    @Test void emptyOrForeignGateReferencesCannotExpandTheLookupScope() {
        when(graph.selectGatesForUpdate(any())).thenReturn(List.of());
        assertEquals("GATE_RUNTIME_MISSING",installer.inspect(scope,before,copy()).issues().getFirst().code());
        verify(references,never()).selectOrderedForUpdate(any());
        refs.getFirst().setTenantId(2L);
        assertEquals("GATE_REFERENCE_RUNTIME_CONFLICT",installer.plan(scope,before,copy(),List.of(actual),refs).issues().getFirst().code());
    }
    private TemplateExecutionSnapshot copy() {return JsonUtils.parseObject(JsonUtils.toJsonString(before),TemplateExecutionSnapshot.class);}
}
