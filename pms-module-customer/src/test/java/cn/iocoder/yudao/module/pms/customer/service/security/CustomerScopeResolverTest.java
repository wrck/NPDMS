package cn.iocoder.yudao.module.pms.customer.service.security;

import cn.iocoder.yudao.module.pms.customer.dal.dataobject.security.CustomerScopeSliceDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.security.CustomerScopeSliceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerScopeResolverTest {

    @Mock
    private CustomerScopeSliceMapper scopeSliceMapper;
    @InjectMocks
    private CustomerScopeResolver resolver;

    @Test
    void keepsIndependentSlicesWithoutCrossProductExpansion() {
        when(scopeSliceMapper.selectEffective(anyQuery())).thenReturn(List.of(
                slice(10L, "D01,D02", "M01", "S01", "E01", "I01,I02"),
                slice(11L, "D03", "M02", "S02", "E02", "I03")));

        CustomerVisibleScope scope = resolver.resolve(new CustomerScopeRequest(
                1L, 100L, Set.of(200L), Set.of("service_manager"), false));

        assertFalse(scope.all());
        assertEquals(2, scope.slices().size());
        assertEquals(Set.of("D01", "D02"), scope.slices().get(0).departmentCodes());
        assertEquals(Set.of("I01", "I02"), scope.slices().get(0).industryCodes());
        assertEquals(Set.of("D03"), scope.slices().get(1).departmentCodes());
        assertEquals(Set.of("M02"), scope.slices().get(1).marketCodes());
    }

    @Test
    void selectedDimensionWithEmptyValuesMakesSliceUnavailable() {
        when(scopeSliceMapper.selectEffective(anyQuery())).thenReturn(List.of(
                slice(10L, "", "M01", "S01", "E01", "I01")));

        CustomerVisibleScope scope = resolver.resolve(new CustomerScopeRequest(
                1L, 100L, Set.of(), Set.of("service_manager"), false));

        assertFalse(scope.all());
        assertTrue(scope.slices().isEmpty());
    }

    @Test
    void administratorWithoutExplicitSlicesGetsAllScope() {
        when(scopeSliceMapper.selectEffective(anyQuery())).thenReturn(List.of());

        CustomerVisibleScope scope = resolver.resolve(new CustomerScopeRequest(
                1L, 100L, Set.of(), Set.of("crm_admin"), false));

        assertTrue(scope.all());
        assertTrue(scope.slices().isEmpty());
    }

    @Test
    void businessRoleWithoutExplicitSlicesGetsNoScope() {
        when(scopeSliceMapper.selectEffective(anyQuery())).thenReturn(List.of());

        CustomerVisibleScope scope = resolver.resolve(new CustomerScopeRequest(
                1L, 100L, Set.of(), Set.of("service_manager"), false));

        assertFalse(scope.all());
        assertTrue(scope.slices().isEmpty());
    }

    @Test
    void explicitAdministratorSliceOverridesDefaultAll() {
        when(scopeSliceMapper.selectEffective(anyQuery())).thenReturn(List.of(
                slice(10L, "D01", "*", "*", "*", "*")));

        CustomerVisibleScope scope = resolver.resolve(new CustomerScopeRequest(
                1L, 100L, Set.of(), Set.of("tenant_admin"), false));

        assertFalse(scope.all());
        assertEquals(Set.of("D01"), scope.slices().getFirst().departmentCodes());
        assertTrue(scope.slices().getFirst().marketCodes().isEmpty());
    }

    private CustomerScopeSliceDO slice(Long id, String departments, String markets,
                                       String systems, String expends, String industries) {
        CustomerScopeSliceDO slice = new CustomerScopeSliceDO();
        slice.setId(id);
        slice.setDepartmentMode("*".equals(departments) ? "ALL" : "SELECTED");
        slice.setDepartmentCodes(departments);
        slice.setMarketMode("*".equals(markets) ? "ALL" : "SELECTED");
        slice.setMarketCodes(markets);
        slice.setSystemMode("*".equals(systems) ? "ALL" : "SELECTED");
        slice.setSystemCodes(systems);
        slice.setExpendMode("*".equals(expends) ? "ALL" : "SELECTED");
        slice.setExpendCodes(expends);
        slice.setIndustryMode("*".equals(industries) ? "ALL" : "SELECTED");
        slice.setIndustryCodes(industries);
        return slice;
    }

    private CustomerScopeSliceQuery anyQuery() {
        return org.mockito.ArgumentMatchers.any(CustomerScopeSliceQuery.class);
    }
}
