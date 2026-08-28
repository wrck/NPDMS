package cn.iocoder.yudao.module.pms.customer.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.VisibleCustomerPageQuery;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeSlice;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerVisibleScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerQueryServiceTest {

    @Mock
    private CustomerMasterMapper customerMasterMapper;
    @InjectMocks
    private CustomerQueryService service;

    @Test
    void emptyVisibleScopeReturnsEmptyPageWithoutQueryingDatabase() {
        CustomerPageCriteria criteria = new CustomerPageCriteria(1L, null, null, null, null, null,
                null, null, null, null, new PageParam().setPageNo(1).setPageSize(20));

        PageResult<CustomerMasterDO> result = service.page(
                criteria, new CustomerVisibleScope(false, List.of()));

        assertEquals(0L, result.getTotal());
        verify(customerMasterMapper, never()).selectVisiblePage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passesIndependentPermissionSlicesAndUserFiltersToMapper() {
        CustomerScopeSlice first = new CustomerScopeSlice(Set.of("D01"), Set.of("M01"),
                Set.of("S01"), Set.of(), Set.of("I01", "I02"));
        CustomerScopeSlice second = new CustomerScopeSlice(Set.of("D02"), Set.of("M02"),
                Set.of("S02"), Set.of("E02"), Set.of("I03"));
        CustomerPageCriteria criteria = new CustomerPageCriteria(1L, "C-", "客户", "D01", "M01",
                "S01", null, "I01", "ENABLED", "CRM_SYNCED",
                new PageParam().setPageNo(1).setPageSize(20));
        when(customerMasterMapper.selectVisiblePage(org.mockito.ArgumentMatchers.any()))
                .thenReturn(PageResult.empty());

        service.page(criteria, new CustomerVisibleScope(false, List.of(first, second)));

        ArgumentCaptor<VisibleCustomerPageQuery> captor = ArgumentCaptor.forClass(VisibleCustomerPageQuery.class);
        verify(customerMasterMapper).selectVisiblePage(captor.capture());
        assertEquals(List.of(first, second), captor.getValue().scopeSlices());
        assertEquals("D01", captor.getValue().departmentCode());
        assertEquals("I01", captor.getValue().industryCode());
    }

    @Test
    void detailUsesSameVisibleScopeAndCustomerId() {
        CustomerScopeSlice slice = new CustomerScopeSlice(Set.of("D01"), Set.of("M01"),
                Set.of("S01"), Set.of("E01"), Set.of("I01"));
        CustomerMasterDO expected = new CustomerMasterDO();
        expected.setId(100L);
        when(customerMasterMapper.selectVisibleById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(expected);

        CustomerMasterDO result = service.get(1L, 100L,
                new CustomerVisibleScope(false, List.of(slice)));

        assertEquals(expected, result);
        var captor = ArgumentCaptor.forClass(
                cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.VisibleCustomerDetailQuery.class);
        verify(customerMasterMapper).selectVisibleById(captor.capture());
        assertEquals(100L, captor.getValue().customerId());
        assertEquals(List.of(slice), captor.getValue().scopeSlices());
    }

    @Test
    void emptyVisibleScopeDoesNotQueryDetail() {
        CustomerMasterDO result = service.get(1L, 100L,
                new CustomerVisibleScope(false, List.of()));

        assertEquals(null, result);
        verify(customerMasterMapper, never()).selectVisibleById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void allScopeQueriesWithoutPermissionSlices() {
        CustomerPageCriteria criteria = new CustomerPageCriteria(1L, null, null, null, null, null,
                null, null, null, null, new PageParam().setPageNo(1).setPageSize(20));
        when(customerMasterMapper.selectVisiblePage(org.mockito.ArgumentMatchers.any()))
                .thenReturn(PageResult.empty());

        service.page(criteria, new CustomerVisibleScope(true, List.of()));

        ArgumentCaptor<VisibleCustomerPageQuery> captor = ArgumentCaptor.forClass(VisibleCustomerPageQuery.class);
        verify(customerMasterMapper).selectVisiblePage(captor.capture());
        assertEquals(true, captor.getValue().allScope());
        assertEquals(List.of(), captor.getValue().scopeSlices());
    }
}
