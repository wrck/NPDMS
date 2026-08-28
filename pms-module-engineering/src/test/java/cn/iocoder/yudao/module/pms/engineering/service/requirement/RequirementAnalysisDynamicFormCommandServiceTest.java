package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequirementAnalysisDynamicFormCommandServiceTest {
    @Mock RequirementAnalysisRootMapper rootMapper;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectParticipantFactApi participantFactApi;
    @Mock ProjectWorkBindingFactApi workBindingFactApi;
    @Mock DynamicFormBusinessInstanceApi dynamicFormApi;
    @Mock RequirementAnalysisDynamicFormPolicyProvider policyProvider;
    @Mock PermissionApi permissionApi;
    @Mock PlatformCommandExecutionApi commandExecutionApi;
    @Mock OperationAuditApi operationAuditApi;
    @Mock TransactionTemplate transactionTemplate;

    @Test
    void patchCarriesIndependentPltAndSolCasAndIncrementsOnlySolRootAfterPltSuccess() {
        RequirementAnalysisDynamicFormCommandService service = service();
        PreparationDO root = draft();
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(participantFactApi.inspect(any())).thenReturn(manager());
        when(participantFactApi.lockAndRevalidate(any())).thenReturn(manager());
        when(rootMapper.selectById(any())).thenReturn(root);
        when(rootMapper.selectForUpdate(any())).thenReturn(root);
        when(dynamicFormApi.patchInstanceValues(any())).thenReturn(form(8));
        when(rootMapper.incrementDynamicContentIfMatch(any())).thenReturn(1);

        var result = service.patch(new RequirementAnalysisDynamicFormCommandService.PatchCommand(
                501L, 3, 7, Map.of("requiresCutover", false, "machineCount", 0), "op-1"),
                new RequirementAnalysisDynamicFormCommandService.Actor(0L, 9L, "corr-1"));

        ArgumentCaptor<DynamicFormInstancePatchCommand> patch =
                ArgumentCaptor.forClass(DynamicFormInstancePatchCommand.class);
        verify(dynamicFormApi).patchInstanceValues(patch.capture());
        assertEquals(7, patch.getValue().expectedInstanceVersion());
        assertEquals(false, patch.getValue().partialValues().get("requiresCutover"));
        assertEquals(0, patch.getValue().partialValues().get("machineCount"));
        assertEquals(4, result.solVersion());
        assertEquals(8, result.dynamicFormInstanceVersion());
        verify(rootMapper).incrementDynamicContentIfMatch(argThat(update -> update.expectedVersion() == 3));
        verify(operationAuditApi).record(eq(0L), eq(9L), eq("corr-1"),
                eq("REQUIREMENT_ANALYSIS_PATCH"), eq("RequirementAnalysis"), eq("501"), eq("SUCCESS"), any());
    }

    private RequirementAnalysisDynamicFormCommandService service() {
        return new RequirementAnalysisDynamicFormCommandService(rootMapper, projectScopeApi, participantFactApi,
                workBindingFactApi, dynamicFormApi, policyProvider, permissionApi, commandExecutionApi,
                operationAuditApi, transactionTemplate);
    }

    private PreparationDO draft() {
        PreparationDO row = new PreparationDO();
        row.setId(501L);
        row.setTenantId(0L);
        row.setProjectId(100L);
        row.setStatusCode("DRAFT");
        row.setDraftMarker(1);
        row.setBusinessVersion(1);
        row.setDynamicFormInstanceId(9001L);
        row.setVersion(3);
        row.setContentVersion(2);
        return row;
    }

    private ProjectScopeResult scope() {
        return new ProjectScopeResult(100L, 6L, Set.of(100L), Set.of());
    }

    private ProjectParticipantFact manager() {
        return new ProjectParticipantFact(100L, 9L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                "PRIMARY", "ACTIVE", "S1", 5, 8L);
    }

    private DynamicFormInstanceFact form(int version) {
        DynamicFormProviderKey provider = new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS");
        return new DynamicFormInstanceFact(0L, provider,
                new DynamicFormOwnerKey("SOL", "REQUIREMENT_ANALYSIS", "501"), 9001L,
                10L, 11L, 1, 2, "FORM_CREATE_ELEMENT_PLUS", "3.4.0", "3.2.38",
                "{}", "[]", List.of(), Map.of(), new DynamicFormValidationFact("VALID", List.of()),
                List.of(), version, DynamicFormBusinessAction.PATCH,
                new DynamicFormPolicyFact(DynamicFormBusinessAction.PATCH, true, null, 3L, "DRAFT"));
    }
}
