package cn.iocoder.yudao.module.pms.asset.service.warranty;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRecordMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.query.DeviceWarrantyRecordPageQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceWarrantyQueryServiceTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceWarrantyMapper warrantyMapper;
    @Mock private DeviceWarrantyRecordMapper recordMapper;
    private DeviceWarrantyQueryService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        service = new DeviceWarrantyQueryService(deviceMapper, warrantyMapper, recordMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReadWarrantyWithAuthenticatedTenantWhenTenantContextIsDisabled() {
        TenantContextHolder.clear();
        LoginUser user = new LoginUser();
        user.setId(7L);
        user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        when(recordMapper.selectPage(new DeviceWarrantyRecordPageQuery(1L, "SN-8", 1L, 20L)))
                .thenReturn(PageResult.empty());

        service.get(1L, 8L, 1L, 20L);

        verify(recordMapper).selectPage(new DeviceWarrantyRecordPageQuery(1L, "SN-8", 1L, 20L));
    }

    @Test
    void shouldReturnCurrentFactAndPagedRecords() {
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        DeviceWarrantyDO current = new DeviceWarrantyDO();
        current.setDeviceSn("SN-8");
        current.setWarrantyStatus("ACTIVE");
        when(warrantyMapper.selectByTenantAndDeviceSn(1L, "SN-8")).thenReturn(current);
        DeviceWarrantyRecordDO record = new DeviceWarrantyRecordDO();
        record.setDeviceSn("SN-8");
        record.setWarrantyMonths(12);
        when(recordMapper.selectPage(new DeviceWarrantyRecordPageQuery(1L, "SN-8", 1L, 20L)))
                .thenReturn(new PageResult<>(List.of(record), 1L));

        DeviceWarrantyResult result = service.get(1L, 8L, 1L, 20L);

        assertEquals("ACTIVE", result.current().getWarrantyStatus());
        assertEquals(12, result.records().getList().getFirst().getWarrantyMonths());
        assertEquals(1L, result.records().getTotal());
    }

    @Test
    void shouldRejectCrossTenantQueryBeforeReadingDevice() {
        ServiceException error = assertThrows(ServiceException.class, () -> service.get(2L, 8L, 1L, 20L));

        assertEquals(AST_EQUIPMENT_NOT_EXISTS.getCode(), error.getCode());
        verify(deviceMapper, never()).selectByTenantAndId(2L, 8L);
        verify(warrantyMapper, never()).selectByTenantAndDeviceSn(2L, "SN-8");
        verify(recordMapper, never()).selectPage(new DeviceWarrantyRecordPageQuery(2L, "SN-8", 1L, 20L));
    }

    @Test
    void shouldUseDefaultPageBoundary() {
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        when(recordMapper.selectPage(new DeviceWarrantyRecordPageQuery(1L, "SN-8", 1L, 20L)))
                .thenReturn(PageResult.empty());

        service.get(1L, 8L, null, null);

        verify(recordMapper).selectPage(new DeviceWarrantyRecordPageQuery(1L, "SN-8", 1L, 20L));
    }

    @Test
    void shouldRejectInvalidPageBoundary() {
        assertThrows(IllegalArgumentException.class, () -> service.get(1L, 8L, 0L, 20L));
        assertThrows(IllegalArgumentException.class, () -> service.get(1L, 8L, 1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> service.get(1L, 8L, 1L, 201L));
        verify(recordMapper, never()).selectPage(new DeviceWarrantyRecordPageQuery(1L, "SN-8", 1L, 20L));
    }

    private DeviceDO device() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        device.setSn("SN-8");
        return device;
    }
}
