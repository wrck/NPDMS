package cn.iocoder.yudao.module.pms.commerce.service.contract;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectContractRelationMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractCompanyScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderCompanyScopeQuery;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractAccessServiceTest {

    @Mock private OrganizationScopeApi organizationScopeApi;
    @Mock private ProjectScopeApi projectScopeApi;
    @Mock private ContractMapper contractMapper;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderLineMapper lineMapper;
    @Mock private ProjectContractRelationMapper projectRelationMapper;
    @Mock private OperationAuditApi operationAuditApi;
    private ContractAccessService service;

    @BeforeEach
    void setUp() {
        service = new ContractAccessService(organizationScopeApi, projectScopeApi, contractMapper,
                orderMapper, lineMapper, projectRelationMapper, operationAuditApi);
    }

    @Test
    void shouldUseCompanyScopeAndAllLockedContractFilters() {
        when(organizationScopeApi.getActiveScopes(7L)).thenReturn(List.of(
                scope(1L, "C01", 2), scope(2L, "C01", 3), scope(3L, "c01", 1)));
        when(contractMapper.selectCountByCompanyScope(any())).thenReturn(1L);
        when(contractMapper.selectByCompanyScope(any())).thenReturn(List.of(new ContractDO()));

        var page = service.pageContracts(1L, 7L, "trace", new ContractAccessService.ContractSearch(
                "C01", "CT-", "FRAME", "客户", "ERP", "ENABLED", 0, 20));

        assertEquals(1L, page.getTotal());
        ArgumentCaptor<ContractCompanyScopeQuery> captor = ArgumentCaptor.forClass(ContractCompanyScopeQuery.class);
        verify(contractMapper).selectByCompanyScope(captor.capture());
        assertEquals(List.of("C01", "c01"), captor.getValue().companyCodes());
        assertEquals("FRAME", captor.getValue().contractType());
        assertEquals("客户", captor.getValue().customerKeyword());
        assertEquals("ERP", captor.getValue().sourceSystem());
    }

    @Test
    void shouldReturnContractDetailAggregateAfterCompanyCheck() {
        ContractDO contract = new ContractDO();
        contract.setId(99L);
        when(organizationScopeApi.getActiveScopes(7L)).thenReturn(List.of(scope(1L, "C01", 1)));
        when(contractMapper.selectDetailByCompanyScope(any())).thenReturn(contract);
        when(orderMapper.selectRelatedByContract(any())).thenReturn(List.of(new SalesOrderDO()));
        when(projectRelationMapper.selectCurrentByContract(any())).thenReturn(List.of(new ProjectContractRelationDO()));

        ContractAccessService.ContractDetail detail = service.getContractDetail(1L, 7L, "trace", 99L);

        assertSame(contract, detail.contract());
        assertEquals(1, detail.relatedOrders().size());
        assertEquals(1, detail.projectRelations().size());
    }

    @Test
    void shouldAllowOrderReadByProjectScopeWithoutCompanyScope() {
        when(organizationScopeApi.getActiveScopes(8L)).thenReturn(List.of());
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(Set.of(501L, 502L));
        when(orderMapper.selectCountByCompanyScope(any())).thenReturn(1L);
        when(orderMapper.selectByCompanyScope(any())).thenReturn(List.of(new SalesOrderDO()));

        var page = service.pageSalesOrders(1L, 8L, "trace", new ContractAccessService.SalesOrderSearch(
                null, "SO-", "NORMAL", "客户", "ENABLED", 0, 20));

        assertEquals(1L, page.getTotal());
        ArgumentCaptor<SalesOrderCompanyScopeQuery> captor =
                ArgumentCaptor.forClass(SalesOrderCompanyScopeQuery.class);
        verify(orderMapper).selectByCompanyScope(captor.capture());
        assertTrue(captor.getValue().companyCodes().isEmpty());
        assertEquals(List.of(501L, 502L), captor.getValue().projectIds());
    }

    @Test
    void shouldAllowContractReadByProjectScopeWithoutCompanyScope() {
        when(organizationScopeApi.getActiveScopes(8L)).thenReturn(List.of());
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(Set.of(501L));
        when(contractMapper.selectCountByCompanyScope(any())).thenReturn(1L);
        when(contractMapper.selectByCompanyScope(any())).thenReturn(List.of(new ContractDO()));

        var page = service.pageContracts(1L, 8L, "trace", new ContractAccessService.ContractSearch(
                null, null, null, null, null, null, 0, 20));

        assertEquals(1L, page.getTotal());
        ArgumentCaptor<ContractCompanyScopeQuery> captor = ArgumentCaptor.forClass(ContractCompanyScopeQuery.class);
        verify(contractMapper).selectByCompanyScope(captor.capture());
        assertTrue(captor.getValue().companyCodes().isEmpty());
        assertEquals(List.of(501L), captor.getValue().projectIds());
    }

    @Test
    void shouldAllowOrderReadByCompanyScopeWhenProjectScopeIsUnavailable() {
        when(organizationScopeApi.getActiveScopes(7L)).thenReturn(List.of(scope(1L, "C01", 1)));
        when(projectScopeApi.resolveAllCurrent(any())).thenThrow(new IllegalStateException("down"));
        when(orderMapper.selectCountByCompanyScope(any())).thenReturn(1L);
        when(orderMapper.selectByCompanyScope(any())).thenReturn(List.of(new SalesOrderDO()));

        service.pageSalesOrders(1L, 7L, "trace", new ContractAccessService.SalesOrderSearch(
                null, null, null, null, null, 0, 20));

        ArgumentCaptor<SalesOrderCompanyScopeQuery> captor =
                ArgumentCaptor.forClass(SalesOrderCompanyScopeQuery.class);
        verify(orderMapper).selectByCompanyScope(captor.capture());
        assertEquals(List.of("C01"), captor.getValue().companyCodes());
        assertTrue(captor.getValue().projectIds().isEmpty());
    }

    @Test
    void shouldReturnEmptyWithoutEitherOwnerScope() {
        when(organizationScopeApi.getActiveScopes(7L)).thenReturn(List.of());
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(Set.of());

        assertTrue(service.pageSalesOrders(1L, 7L, "trace", new ContractAccessService.SalesOrderSearch(
                null, null, null, null, null, 0, 20)).getList().isEmpty());
        verifyNoInteractions(orderMapper);
    }

    static UserCompanyDepartmentScopeRespDTO scope(Long id, String companyCode, Integer version) {
        UserCompanyDepartmentScopeRespDTO value = new UserCompanyDepartmentScopeRespDTO();
        value.setId(id);
        value.setCompanyCode(companyCode);
        value.setVersion(version);
        return value;
    }
}
