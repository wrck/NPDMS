package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationAddressCompletionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.ProjectInstallationAddressQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class ProjectInstallationAddressCompletionService implements InstallationAddressCompletionApi {
    private final DeviceQueryApi devices;
    private final InstallationMapper installations;

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Fact lockAndInspect(Query query) {
        if (query == null || query.tenantId() == null || !query.tenantId().equals(TenantContextHolder.getTenantId())
                || query.projectId() == null || query.projectId() <= 0) throw new IllegalArgumentException("PROJECT_INSTALLATION_SCOPE_REQUIRED");
        var inventory = devices.lockProjectInstallationDevices(new ProjectDeviceInstallationQuery(query.tenantId(), query.projectId()));
        var latest = new HashMap<Long, InstallationDO>();
        for (var record : installations.selectProjectAddressRecordsForUpdate(new ProjectInstallationAddressQuery(query.tenantId(), query.projectId()))) {
            if (record.getEquipmentId() != null) latest.putIfAbsent(record.getEquipmentId(), record);
        }
        int addressed = 0;
        for (var device : inventory) {
            var record = latest.get(device.deviceId());
            if (record != null && present(record.getInstallLocation()) || present(device.locationSnapshot())) addressed++;
        }
        return new Fact(inventory.size(), addressed);
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
