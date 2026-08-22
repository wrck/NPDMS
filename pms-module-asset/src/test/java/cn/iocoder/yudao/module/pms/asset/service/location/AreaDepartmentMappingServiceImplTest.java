package cn.iocoder.yudao.module.pms.asset.service.location;

import cn.iocoder.yudao.module.pms.asset.api.location.dto.AreaDepartmentMappingRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.asset.controller.admin.location.vo.AreaDepartmentMappingSaveReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.AreaDepartmentMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.AreaDepartmentMappingMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.AddressMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.SiteMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AreaDepartmentMappingServiceImplTest {

    @Mock private AreaDepartmentMappingMapper mapper;
    @Mock private DeptApi deptApi;
    @Mock private AddressMapper addressMapper;
    @Mock private SiteMapper siteMapper;

    private AreaDepartmentMappingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AreaDepartmentMappingServiceImpl(mapper, deptApi);
    }

    @Test
    void shouldResolveExactAreaOnly() {
        AreaDepartmentMappingDO mapping = mapping();
        when(mapper.selectCurrent(eq("330106"), eq("DISTRICT"), eq("SERVICE_OFFICE"), eq(0),
                any(LocalDateTime.class))).thenReturn(mapping);
        DeptRespDTO dept = new DeptRespDTO();
        dept.setCode("DEPT-HZ-01");
        dept.setName("杭州办事处");
        dept.setStatus(0);
        when(deptApi.getDeptByCode("DEPT-HZ-01")).thenReturn(dept);

        AreaDepartmentMappingRespDTO result = service.resolve("330106", "DISTRICT");
        assertEquals("DEPT-HZ-01", result.departmentCode());
        assertNull(service.resolve("330100", "CITY"));
        verify(mapper).selectCurrent(eq("330100"), eq("CITY"), eq("SERVICE_OFFICE"), eq(0),
                any(LocalDateTime.class));
    }

    @Test
    void shouldReturnNullForDisabledDepartment() {
        when(mapper.selectCurrent(eq("330106"), eq("DISTRICT"), eq("SERVICE_OFFICE"), eq(0),
                any(LocalDateTime.class))).thenReturn(mapping());
        DeptRespDTO dept = new DeptRespDTO();
        dept.setStatus(1);
        when(deptApi.getDeptByCode("DEPT-HZ-01")).thenReturn(dept);
        assertNull(service.resolve("330106", "DISTRICT"));
    }

    @Test
    void shouldRejectOverlappingActiveMapping() {
        DeptRespDTO dept = new DeptRespDTO();
        dept.setStatus(0);
        when(deptApi.getDeptByCode("DEPT-HZ-01")).thenReturn(dept);
        AreaDepartmentMappingDO existing = mapping();
        existing.setEffectiveTo(LocalDateTime.now().plusDays(10));
        existing.setStatus(0);
        when(mapper.selectListByArea("330106", "DISTRICT", "SERVICE_OFFICE"))
                .thenReturn(List.of(existing));
        AssetLocationAdminServiceImpl adminService = new AssetLocationAdminServiceImpl(
                addressMapper, siteMapper, mapper, deptApi);
        AreaDepartmentMappingSaveReqVO reqVO = new AreaDepartmentMappingSaveReqVO();
        reqVO.setAreaCode("330106");
        reqVO.setAreaLevel("DISTRICT");
        reqVO.setDepartmentCode("DEPT-HZ-01");
        reqVO.setEffectiveFrom(LocalDateTime.now());
        reqVO.setStatus(0);

        assertThrows(ServiceException.class, () -> adminService.saveMapping(reqVO));
        verify(mapper, never()).insert(any(AreaDepartmentMappingDO.class));
    }

    private AreaDepartmentMappingDO mapping() {
        AreaDepartmentMappingDO mapping = new AreaDepartmentMappingDO();
        mapping.setId(1L);
        mapping.setAreaCode("330106");
        mapping.setAreaLevel("DISTRICT");
        mapping.setMappingType("SERVICE_OFFICE");
        mapping.setDepartmentCode("DEPT-HZ-01");
        mapping.setEffectiveFrom(LocalDateTime.now().minusDays(1));
        mapping.setVersion(0);
        return mapping;
    }

}
