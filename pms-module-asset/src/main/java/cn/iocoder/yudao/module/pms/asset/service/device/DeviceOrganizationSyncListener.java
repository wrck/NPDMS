package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.GenericSyncTargetsChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DeviceOrganizationSyncListener {
    private final DeviceOrganizationProjectionApi projection;

    @EventListener
    public void targetsChanged(GenericSyncTargetsChanged event) {
        Set<Long> devices = new HashSet<>();
        Set<String> contracts = new HashSet<>();
        for (var target : event.targets()) {
            if ("ast_device".equals(target.table())) {
                Object id = target.after().getOrDefault("id",target.before().get("id"));
                if (id != null) devices.add(Long.valueOf(id.toString()));
            } else if (Set.of("com_contract","com_shipment_contract_reference").contains(target.table())) {
                for (var row : List.of(target.before(),target.after())) {
                    Object number = row.get("contract_no");
                    if (number != null && !number.toString().isBlank()) contracts.add(number.toString());
                }
            }
        }
        if (!devices.isEmpty() || !contracts.isEmpty()) {
            projection.refresh(new DeviceOrganizationProjectionApi.Refresh(event.tenantId(),devices,Set.of(),contracts));
        }
    }
}
