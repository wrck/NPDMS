package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.GenericSyncTargetsChanged;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;

class DeviceOrganizationSyncListenerTest {
    @Test void syncRefreshesBothPreviousAndNewContractsAndDeviceDeltaId() {
        var owner=mock(DeviceOrganizationProjectionApi.class);
        new DeviceOrganizationSyncListener(owner).targetsChanged(new GenericSyncTargetsChanged(1L,List.of(
                new GenericSyncTargetsChanged.Target("com_contract",Map.of("contract_no","OLD"),Map.of("contract_no","NEW")),
                new GenericSyncTargetsChanged.Target("ast_device",Map.of("id",8L),Map.of("contract_no","NEW")))));
        verify(owner).refresh(new DeviceOrganizationProjectionApi.Refresh(1L,Set.of(8L),Set.of(),Set.of("OLD","NEW")));
    }
}
