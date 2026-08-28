package cn.iocoder.yudao.module.pms.customer.service.security;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.classification.CustomerMarketRelationDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.classification.CustomerMarketRelationMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_CLASSIFICATION_INVALID;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_SCOPE_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerClassificationAccessServiceTest {

    @Mock
    private DeptApi deptApi;
    @Mock
    private CustomerMarketRelationMapper marketRelationMapper;
    @InjectMocks
    private CustomerClassificationAccessService service;

    @Test
    void validatesDepartmentAndExactFourLevelCombination() {
        DeptRespDTO department = new DeptRespDTO();
        department.setCode("D01");
        department.setName("华东办事处");
        department.setStatus(0);
        when(deptApi.getDeptByCode("D01")).thenReturn(department);
        CustomerMarketRelationDO relation = relation();
        when(marketRelationMapper.selectActive(org.mockito.ArgumentMatchers.any())).thenReturn(relation);

        CustomerClassificationSnapshot result = service.validate(1L,
                new CustomerClassificationInput("D01", "M01", "S01", "E01", "I01"),
                new CustomerVisibleScope(false, List.of(slice())));

        assertEquals("华东办事处", result.departmentName());
        assertEquals("子行业一", result.industryName());
    }

    @Test
    void rejectsUnknownFourLevelCombination() {
        DeptRespDTO department = new DeptRespDTO();
        department.setCode("D01");
        department.setStatus(0);
        when(deptApi.getDeptByCode("D01")).thenReturn(department);
        when(marketRelationMapper.selectActive(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.validate(1L,
                new CustomerClassificationInput("D01", "M01", "S01", "E01", "I01"),
                new CustomerVisibleScope(true, List.of())));

        assertEquals(CUSTOMER_CLASSIFICATION_INVALID.getCode(), exception.getCode());
    }

    @Test
    void rejectsCombinationOutsideEveryIndependentSlice() {
        DeptRespDTO department = new DeptRespDTO();
        department.setCode("D01");
        department.setStatus(0);
        when(deptApi.getDeptByCode("D01")).thenReturn(department);
        when(marketRelationMapper.selectActive(org.mockito.ArgumentMatchers.any())).thenReturn(relation());
        CustomerScopeSlice first = new CustomerScopeSlice(Set.of("D01"), Set.of("M01"),
                Set.of("S02"), Set.of(), Set.of());
        CustomerScopeSlice second = new CustomerScopeSlice(Set.of("D02"), Set.of("M02"),
                Set.of("S01"), Set.of(), Set.of());

        ServiceException exception = assertThrows(ServiceException.class, () -> service.validate(1L,
                new CustomerClassificationInput("D01", "M01", "S01", "E01", "I01"),
                new CustomerVisibleScope(false, List.of(first, second))));

        assertEquals(CUSTOMER_SCOPE_DENIED.getCode(), exception.getCode());
    }

    private CustomerScopeSlice slice() {
        return new CustomerScopeSlice(Set.of("D01", "D02"), Set.of("M01"), Set.of("S01"),
                Set.of("E01"), Set.of("I01", "I02"));
    }

    private CustomerMarketRelationDO relation() {
        CustomerMarketRelationDO relation = new CustomerMarketRelationDO();
        relation.setTenantId(1L);
        relation.setMarketCode("M01");
        relation.setMarketName("市场一部");
        relation.setSystemCode("S01");
        relation.setSystemName("系统一部");
        relation.setExpendCode("E01");
        relation.setExpendName("拓展一部");
        relation.setIndustryCode("I01");
        relation.setIndustryName("子行业一");
        return relation;
    }
}
