package cn.iocoder.yudao.module.pms.engineering.service.implementationreadiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery;
import cn.iocoder.yudao.module.pms.cutover.api.result.CutoverResultApi;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.CutoverCoverageApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.Objects;

@Service @RequiredArgsConstructor
public class CutoverCoverageService implements CutoverCoverageApi {
    private final DeviceQueryApi devices;
    private final CutoverResultApi results;
    @Override @Transactional(rollbackFor = Exception.class)
    public Coverage inspect(Query query) { return read(query, false); }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Coverage lockAndInspect(Query query) { return read(query, true); }
    private Coverage read(Query query, boolean lock) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || query.projectId() == null || query.projectId() <= 0) throw new SecurityException("割接覆盖范围无效");
        var successes = results.successes(new CutoverResultApi.ProjectQuery(query.tenantId(), query.projectId()));
        if (lock) successes = successes.stream().map(result -> results.lockAndInspect(new CutoverResultApi.ResultQuery(
                query.tenantId(), query.projectId(), result.taskId(), result.closureId(), result.closureVersion()))).toList();
        var current = devices.lockProjectInstallationDevices(new ProjectDeviceInstallationQuery(query.tenantId(), query.projectId()))
                .stream().map(device -> {
                    var value = devices.getDevice(device.deviceId());
                    if (value == null || !Objects.equals(value.projectId(), query.projectId())
                            || !Objects.equals(value.tenantId(), query.tenantId())) throw new IllegalStateException("当前设备范围已变化");
                    return new Device(value.deviceId(), value.sn(), value.projectAssignmentVersion());
                }).toList();
        return CutoverCoverageRules.calculate(current, successes);
    }
}
