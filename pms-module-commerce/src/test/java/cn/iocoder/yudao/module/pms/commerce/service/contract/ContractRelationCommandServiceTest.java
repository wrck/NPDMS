package cn.iocoder.yudao.module.pms.commerce.service.contract;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectContractRelationMapper;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractRelationCommandServiceTest {

    @Mock private OrganizationScopeApi organizationScopeApi;
    @Mock private ContractMapper contractMapper;
    @Mock private ProjectContractRelationMapper relationMapper;
    @Mock private OperationAuditApi operationAuditApi;
    private ContractRelationCommandService service;

    @BeforeEach
    void setUp() {
        service = new ContractRelationCommandService(
                organizationScopeApi, contractMapper, relationMapper, operationAuditApi);
    }

    @Test
    void shouldRevalidateAndAuditAllMatchingScopeVersionsInStableOrder() {
        UserCompanyDepartmentScopeRespDTO second = ContractAccessServiceTest.scope(20L, "C01", 3);
        UserCompanyDepartmentScopeRespDTO first = ContractAccessServiceTest.scope(10L, "C01", 2);
        when(organizationScopeApi.getActiveScopes(7L)).thenReturn(List.of(second, first));
        ContractDO contract = new ContractDO();
        contract.setId(99L);
        contract.setCompanyCode("C01");
        when(contractMapper.selectByIdForUpdate(any())).thenReturn(contract);
        doAnswer(invocation -> { ((ProjectContractRelationDO) invocation.getArgument(0)).setId(88L); return 1; })
                .when(relationMapper).insert(any(ProjectContractRelationDO.class));

        ContractRelationResult result = service.relate(new ContractRelationCommand(
                1L, 7L, 99L, 100L, "RELATED", "op-1", "业务依据"));

        assertFalse(result.replayed());
        assertEquals(88L, result.relationId());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, ?>> audit = ArgumentCaptor.forClass(Map.class);
        verify(operationAuditApi).record(eq(1L), eq(7L), eq("op-1"),
                eq("COM_PROJECT_CONTRACT_RELATE"), eq("ProjectContractRelation"), eq("88"),
                eq("SUCCESS"), audit.capture());
        assertEquals(List.of(Map.of("scopeId", 10L, "version", 2),
                Map.of("scopeId", 20L, "version", 3)), audit.getValue().get("authorizationSnapshot"));
    }

    @Test
    void shouldWriteNothingWhenOwnerIsUnavailable() {
        when(organizationScopeApi.getActiveScopes(7L)).thenThrow(new IllegalStateException("down"));

        assertThrows(IllegalStateException.class, () -> service.relate(new ContractRelationCommand(
                1L, 7L, 99L, 100L, "RELATED", "op-1", "业务依据")));

        verifyNoInteractions(contractMapper, relationMapper, operationAuditApi);
    }
}
