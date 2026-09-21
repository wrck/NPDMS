package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.service.normalclosure.NormalClosureResultEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NormalClosureResultEvidenceTest {
    private final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    private final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    private final ProjectResultEvidenceGuard evidence = mock(ProjectResultEvidenceGuard.class);
    private final NormalClosureResultEvidence service = new NormalClosureResultEvidence(executions, plans, evidence);
    private final ProjectMasterDO project = new ProjectMasterDO();
    private final ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
    private final ProjectPlanVersionDO plan = new ProjectPlanVersionDO();
    private final ProjectNodeExecutionDO round = new ProjectNodeExecutionDO();
    private ProjectTaskExecutionContractDO contract;

    @BeforeEach void before() {
        var snapshot = ResultSubscriptionTaskFixture.snapshot();
        contract = ResultSubscriptionTaskFixture.contract(snapshot);
        project.setId(3L);project.setTenantId(1L);project.setActivePlanVersionId(10L);
        task.setId(4L);task.setTenantId(1L);task.setProjectId(3L);task.setCode("T1");task.setStatus("DONE");
        plan.setId(10L);plan.setTenantId(1L);plan.setProjectId(3L);plan.setStatus("EFFECTIVE");
        plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        round.setId(20L);round.setTenantId(1L);round.setProjectId(3L);round.setPlanVersionId(10L);
        round.setNodeKind("TASK");round.setNodeKey("task");round.setNodeInstanceId(4L);round.setContractId(30L);
        round.setCurrentMarker(1);round.setStatus("DONE");round.setEndedAt(LocalDateTime.of(2026,9,20,12,0));
        when(plans.selectById(10L)).thenReturn(plan);
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round));
        when(evidence.revalidateCompleted(any(),same(plan),same(round)))
                .thenReturn(new ProjectResultEvidenceGuard.Proof(true,"COMPLETION_RESULT_EVIDENCE_VALID",List.of()));
    }

    @Test void aPureResultTaskUsesItsActualCompletedExecutionInsteadOfTheNativeContractCheck() {
        assertTrue(service.revalidate(project,task,contract,service.lockRounds(project)).ready());
        verify(evidence).revalidateCompleted(any(),same(plan),same(round));
    }

    @Test void additionalSubscriptionsOnANativeTaskAreAlsoRevalidatedAtClosure() {
        var snapshot = ResultSubscriptionTaskFixture.snapshot();
        var binding = new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.BindingContract();
        binding.setType("TASK_NATIVE"); binding.setParameters(JsonUtils.parseTree("{}"));
        snapshot.getTasks().getFirst().setBinding(binding);
        var permission = new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.PermissionContract();
        permission.setPolicySnapshot(JsonUtils.parseTree("{\"requiredActions\":[]}"));
        snapshot.getTasks().getFirst().setPermission(permission);
        plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        contract.setWorkBindingTypeCode("TASK_NATIVE");
        var revoked = new ProjectResultEvidenceGuard.Proof(false, "COMPLETION_RESULT_EVIDENCE_INVALIDATED", List.of());
        when(evidence.revalidateCompleted(any(), same(plan), same(round))).thenReturn(revoked);
        assertEquals(revoked, service.revalidate(project, task, contract, List.of(round)));
    }

    @Test void aLaterPlanDoesNotReplaceTheFrozenPlanOfACompletedTask() {
        project.setActivePlanVersionId(11L);plan.setStatus("SUPERSEDED");
        assertTrue(service.revalidate(project,task,contract,List.of(round)).ready());
        verify(plans).selectById(10L);verify(plans,never()).selectById(11L);
    }

    @ParameterizedTest @ValueSource(strings={"tenant","project","draft","effective-plan"})
    void unavailableOrUnrelatedPlansCannotSupplyClosureEvidence(String damage) {
        switch(damage) {
            case "tenant" -> plan.setTenantId(2L);
            case "project" -> plan.setProjectId(9L);
            case "draft" -> plan.setStatus("DRAFT");
            case "effective-plan" -> project.setActivePlanVersionId(11L);
        }
        assertFalse(service.revalidate(project,task,contract,List.of(round)).ready());
        verifyNoInteractions(evidence);
    }

    @Test void anotherExecutionContractOrNodeCannotBeBorrowed() {
        round.setContractId(31L);
        assertEquals("TASK_RESULT_EXECUTION_UNAVAILABLE",service.revalidate(project,task,contract,List.of(round)).reason());
        round.setContractId(30L);round.setNodeKey("different-task");
        assertFalse(service.revalidate(project,task,contract,List.of(round)).ready());
        verifyNoInteractions(evidence);
    }

    @Test void aDoneStatusAloneDoesNotHideMissingExecutionEvidence() {
        assertFalse(service.revalidate(project,task,contract,List.of()).ready());
        var unavailable = new ProjectResultEvidenceGuard.Proof(false,"RESULT_EVIDENCE_CHANGED",List.of());
        when(evidence.revalidateCompleted(any(),same(plan),same(round))).thenReturn(unavailable);
        assertEquals(unavailable,service.revalidate(project,task,contract,List.of(round)));
    }
}
