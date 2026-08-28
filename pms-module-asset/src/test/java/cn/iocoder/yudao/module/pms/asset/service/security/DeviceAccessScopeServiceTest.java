package cn.iocoder.yudao.module.pms.asset.service.security;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceVisibilityQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAccessScopeServiceTest {

    @Mock private ProjectScopeApi projectScopeApi;
    @Mock private DeviceMapper deviceMapper;

    @Test
    void shouldUseProjectOwnerScopeForDeviceVisibility() {
        DeviceAccessScopeService service = new DeviceAccessScopeService(projectScopeApi, deviceMapper);
        ProjectAllScopeQuery scopeQuery = new ProjectAllScopeQuery(1L, 7L, ProjectScopeApi.ACTION_VIEW);
        when(projectScopeApi.resolveAllCurrent(scopeQuery)).thenReturn(Set.of(10L, 11L));
        DeviceVisibilityQuery visibilityQuery = new DeviceVisibilityQuery(1L, 8L, Set.of(10L, 11L));
        when(deviceMapper.existsVisibleDevice(visibilityQuery)).thenReturn(true);

        service.assertVisible(1L, 7L, 8L);

        verify(deviceMapper).existsVisibleDevice(visibilityQuery);
    }

    @Test
    void shouldRejectBeforeDeviceQueryWhenProjectScopeIsEmpty() {
        DeviceAccessScopeService service = new DeviceAccessScopeService(projectScopeApi, deviceMapper);
        when(projectScopeApi.resolveAllCurrent(
                new ProjectAllScopeQuery(1L, 7L, ProjectScopeApi.ACTION_VIEW))).thenReturn(Set.of());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.assertVisible(1L, 7L, 8L));

        assertEquals(AST_EQUIPMENT_NOT_EXISTS.getCode(), error.getCode());
        verify(deviceMapper, never()).existsVisibleDevice(org.mockito.ArgumentMatchers.any());
    }
}
