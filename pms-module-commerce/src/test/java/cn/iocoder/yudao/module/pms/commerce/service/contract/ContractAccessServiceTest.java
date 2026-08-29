package cn.iocoder.yudao.module.pms.commerce.service.contract;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractCompanyScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractAccessServiceTest {

    @Mock private OrganizationScopeApi organizationScopeApi;
    @Mock private ContractMapper contractMapper;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderLineMapper lineMapper;
    @Mock private OperationAuditApi operationAuditApi;
    private ContractAccessService service;

    @BeforeEach
    void setUp() {
        service = new ContractAccessService(
                organizationScopeApi, contractMapper, orderMapper, lineMapper, operationAuditApi);
    }

    @Test
    void shouldUseExactDistinctCompanyCodesForContractList() {
        when(organizationScopeApi.getActiveScopes(7L)).thenReturn(List.of(
                scope(1L, "C01", 2), scope(2L, "C01", 3), scope(3L, "c01", 1), scope(4L, " ", 1)));
        when(contractMapper.selectByCompanyScope(any())).thenReturn(List.of(new ContractDO()));

        assertEquals(1, service.listContracts(1L, 7L, "trace-1", null, null, 0, 20).size());

        ArgumentCaptor<ContractCompanyScopeQuery> captor = ArgumentCaptor.forClass(ContractCompanyScopeQuery.class);
        verify(contractMapper).selectByCompanyScope(captor.capture());
        assertEquals(List.of("C01", "c01"), captor.getValue().companyCodes());
    }

    @Test
    void shouldReturnExactContractPageTotalWithSingleOwnerRead() {
        when(organizationScopeApi.getActiveScopes(7L)).thenReturn(List.of(scope(1L, "C01", 2)));
        when(contractMapper.selectCountByCompanyScope(any())).thenReturn(23L);
        when(contractMapper.selectByCompanyScope(any())).thenReturn(List.of(new ContractDO()));

        var page = service.pageContracts(1L, 7L, "trace-page", null, null, 20, 20);

        assertEquals(23L, page.getTotal());
        assertEquals(1, page.getList().size());
        verify(organizationScopeApi, times(1)).getActiveScopes(7L);
    }

    @Test
    void shouldReturnEmptyWithoutCallingMapperWhenScopeIsEmptyOrOwnerUnavailable() {
        when(organizationScopeApi.getActiveScopes(7L)).thenReturn(List.of());
        assertTrue(service.listContracts(1L, 7L, "trace-1", null, null, 0, 20).isEmpty());
        verifyNoInteractions(contractMapper);

        reset(contractMapper, organizationScopeApi);
        when(organizationScopeApi.getActiveScopes(7L)).thenThrow(new IllegalStateException("down"));
        assertTrue(service.listContracts(1L, 7L, "trace-2", null, null, 0, 20).isEmpty());
        verifyNoInteractions(contractMapper);
        verify(operationAuditApi).record(eq(1L), eq(7L), eq("trace-2"),
                eq("COM_CONTRACT_AUTHORIZATION"), eq("ContractDirectory"), eq("7"),
                eq("OWNER_UNAVAILABLE"), anyMap());
    }

    @Test
    void shouldHideContractExistenceWhenCurrentCompanyScopeDoesNotMatch() {
        when(organizationScopeApi.getActiveScopes(7L)).thenReturn(List.of(scope(1L, "C01", 1)));
        when(contractMapper.selectDetailByCompanyScope(any())).thenReturn(null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.getContract(1L, 7L, "trace-3", 99L));

        assertEquals("COMMERCE_RESOURCE_NOT_ACCESSIBLE", error.getMessage());
    }

    static UserCompanyDepartmentScopeRespDTO scope(Long id, String companyCode, Integer version) {
        UserCompanyDepartmentScopeRespDTO value = new UserCompanyDepartmentScopeRespDTO();
        value.setId(id);
        value.setCompanyCode(companyCode);
        value.setVersion(version);
        return value;
    }
}
