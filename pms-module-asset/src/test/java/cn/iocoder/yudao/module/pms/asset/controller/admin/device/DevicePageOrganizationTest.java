package cn.iocoder.yudao.module.pms.asset.controller.admin.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DevicePageReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.DeviceListProjection;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceQueryService;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceListNameService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DevicePageOrganizationTest {
    @Mock DeviceQueryService queries;
    @Mock DeviceListNameService names;
    @InjectMocks DeviceController controller;

    @Test void returnsStoredOrganizationIncludingMissingValuesWithoutAnotherLookup() throws Exception {
        try(var mocks=MockitoAnnotations.openMocks(this)) {
            TenantContextHolder.setTenantId(1L);
            try {
                var stored=mock(DeviceListProjection.class);
                when(stored.deviceId()).thenReturn(1L);
                when(stored.companyId()).thenReturn(11L);
                when(stored.companyName()).thenReturn("Stored Company");
                when(stored.departmentId()).thenReturn(22L);
                when(stored.departmentCode()).thenReturn("OFFICE");
                when(stored.departmentName()).thenReturn("Stored Office");
                when(stored.organizationSource()).thenReturn("CONTRACT");
                var missing=mock(DeviceListProjection.class);
                when(missing.deviceId()).thenReturn(2L);
                when(missing.companyId()).thenReturn(null);
                when(missing.departmentId()).thenReturn(null);
                when(queries.getPage(any())).thenReturn(new PageResult<>(List.of(stored,missing),2L));
                var emptyNames=new DeviceListNameService.Names(null,null,null);
                when(names.resolve(any())).thenReturn(Map.of(1L,emptyNames,2L,emptyNames));
                var page=controller.getDevicePage(new DevicePageReqVO()).getData();
                assertEquals(2L,page.getTotal());
                var organization=page.getList().getFirst().organization();
                assertEquals(11L,organization.companyId());assertEquals("Stored Company",organization.companyName());
                assertEquals(22L,organization.departmentId());assertEquals("OFFICE",organization.departmentCode());
                assertEquals("Stored Office",organization.departmentName());assertEquals("CONTRACT",organization.source());
                var absent=page.getList().get(1).organization();
                assertNull(absent.companyId());assertNull(absent.companyName());assertNull(absent.departmentId());
                assertNull(absent.departmentCode());assertNull(absent.departmentName());assertNull(absent.source());
                verify(queries).getPage(any());verify(names).resolve(any());verifyNoMoreInteractions(queries,names);
            } finally { TenantContextHolder.clear(); }
        }
    }
}
