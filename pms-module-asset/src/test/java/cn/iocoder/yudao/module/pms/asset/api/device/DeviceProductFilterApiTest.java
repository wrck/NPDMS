package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceProductQuery;
import cn.iocoder.yudao.module.pms.asset.api.product.ProductFilterMatchMode;
import cn.iocoder.yudao.module.pms.asset.api.product.ProductFilterGroup;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceQueryService;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceProductFilterApiTest {
    final DeviceMapper mapper = mock(DeviceMapper.class);
    final DeviceQueryService queryService = mock(DeviceQueryService.class);
    final DeviceQueryApiImpl api = new DeviceQueryApiImpl(mapper, queryService);
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void invalidScopeAndEmptyFiltersCannotExpandTheQuery() {
        assertThrows(IllegalArgumentException.class, () -> api.findProjectDeviceIdsByProductFilter(new ProjectDeviceProductQuery(2L, 10L, List.of())));
        assertThrows(IllegalArgumentException.class, () -> api.findProjectDeviceIdsByProductFilter(new ProjectDeviceProductQuery(1L, null, List.of())));
        assertThrows(IllegalArgumentException.class, () -> api.findProjectDeviceIdsByProductFilter(new ProjectDeviceProductQuery(1L, 10L, List.of(), null)));
        assertTrue(api.findProjectDeviceIdsByProductFilter(new ProjectDeviceProductQuery(1L, 10L, List.of())).isEmpty());
        verifyNoInteractions(mapper, queryService);
    }
    @Test void defaultMatchingModeUsesOneScopedDeviceQueryWithoutLoadingProductsOrSummaries() {
        var group = new ProductFilterGroup(); group.setProductCodeLike("0201%");
        var query = new ProjectDeviceProductQuery(1L, 10L, List.of(group));
        assertEquals(ProductFilterMatchMode.MATCHING, query.matchMode());
        when(mapper.selectProjectDeviceIdsByProductFilter(query)).thenReturn(List.of(11L, 12L));
        assertEquals(List.of(11L,12L), api.findProjectDeviceIdsByProductFilter(query));
        verify(mapper).selectProjectDeviceIdsByProductFilter(query); verifyNoMoreInteractions(mapper);
        verifyNoInteractions(queryService);
    }
}
