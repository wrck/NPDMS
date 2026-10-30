package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollectionCenterSourceTest {
    @Test void revalidatesFrozenActorWithoutAmbientLoginAndNeverUsesNativeLoginSelection() {
        var auth=mock(CollectionAuthorization.class);var devices=mock(ProjectDeviceSelectionApi.class);
        var selection=new ProjectDeviceSelectionApi.AuthorizedSelection(1L,42L,7L,List.of(8L));
        when(devices.validateSelection(selection)).thenReturn(List.of(new SelectedProjectDevice(8L,"SN","设备",null,null,null)));
        when(auth.allowed(42L,"pms:device-collection:execute")).thenReturn(true);
        TenantContextHolder.setTenantId(1L);
        try {
            var result=new CollectionCenterSource(auth,devices).authorize(CollectionSourceAdapter.AuthorizationQuery.builder()
                    .tenantId(1L).actorId(42L).objectId(7L).deviceId(8L).access(CollectionSourceAdapter.Access.EXECUTE).build());
            assertEquals("设备",result.deviceName());
            verify(auth).project(42L,7L,true,true);verify(devices).validateSelection(selection);
            verify(devices,never()).validateSelection(7L,List.of(8L));
        } finally {TenantContextHolder.clear();}
    }
}
