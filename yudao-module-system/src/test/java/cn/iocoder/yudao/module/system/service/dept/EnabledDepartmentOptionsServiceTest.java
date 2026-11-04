package cn.iocoder.yudao.module.system.service.dept;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DepartmentOptionsReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Import(EnabledDepartmentOptionsService.class)
class EnabledDepartmentOptionsServiceTest extends BaseDbUnitTest {
    @Resource private EnabledDepartmentOptionsService service;
    @Resource private DeptMapper mapper;

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void unionsPrefixesAndExcludesDisabledDeletedAndOtherTenants() {
        seed("1601", 0, 1L, false);
        seed("3101", 0, 1L, false);
        seed("3201", 0, 1L, false);
        seed("1602", 1, 1L, false);
        seed("1603", 0, 1L, true);
        seed("1604", 0, 2L, false);
        TenantContextHolder.setTenantId(1L);
        var request = new DepartmentOptionsReqVO();
        request.setCodePrefixes(List.of("16*", "31", "16"));
        assertEquals(List.of("1601", "3101"), codes(request));
        request.setCodePrefixes(List.of("99*"));
        assertTrue(codes(request).isEmpty());
        request.setCodePrefixes(null);
        assertEquals(List.of("1601", "3101", "3201"), codes(request));
        request.setCodePrefixes(List.of());
        assertEquals(List.of("1601", "3101", "3201"), codes(request));
        TenantContextHolder.clear();
        assertTrue(codes(request).isEmpty());
    }

    @Test void rejectsEmptyOrEmbeddedWildcardAndTreatsSqlWildcardsLiterally() {
        seed("1601", 0, 1L, false);
        TenantContextHolder.setTenantId(1L);
        var request = new DepartmentOptionsReqVO();
        for (String prefix : List.of("", " ", "*", "1*6")) {
            request.setCodePrefixes(List.of(prefix));
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> codes(request));
        }
        for (String prefix : List.of("16%", "16_")) {
            request.setCodePrefixes(List.of(prefix));
            assertTrue(codes(request).isEmpty());
        }
    }

    private List<String> codes(DepartmentOptionsReqVO request) {
        return service.getOptions(request).stream().map(DeptDO::getCode).toList();
    }

    private void seed(String code, int status, Long tenantId, boolean deleted) {
        var department = new DeptDO();
        department.setCode(code);
        department.setName(code);
        department.setStatus(status);
        department.setTenantId(tenantId);
        department.setDeleted(deleted);
        mapper.insert(department);
    }
}
