package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.DeviceListProjection;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceQueryServiceTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceAccessScopeService accessScopeService;
    private DeviceQueryService service;

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser();
        user.setId(7L);
        user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
        service = new DeviceQueryService(deviceMapper, accessScopeService);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldUseAuthenticatedTenantForPageWhenTenantContextIsDisabled() {
        VisibleDevicePageQuery request = new VisibleDevicePageQuery(null, null, "SN-8", null, null, null, 1, 20);
        VisibleDevicePageQuery query = new VisibleDevicePageQuery(1L, Set.of(10L), "SN-8", null, null, null, 1, 20);
        PageResult<DeviceListProjection> expected = new PageResult<>(List.of(), 1L);
        when(accessScopeService.visibleProjectIds(1L, 7L)).thenReturn(Set.of(10L));
        when(deviceMapper.selectVisibleDevicePage(query)).thenReturn(expected);

        assertSame(expected, service.getPage(request));
    }

    @Test
    void shouldUseAuthenticatedTenantForDetailWhenTenantContextIsDisabled() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        when(deviceMapper.selectById(8L)).thenReturn(device);

        assertEquals(8L, service.getDevice(8L).getId());
    }
}
