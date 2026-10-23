package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.DeviceListProjection;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectDeviceOrganizationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/** 只为已经授权的当前设备页批量补充名称，不参与可见范围判定。 */
@Service
@RequiredArgsConstructor
public class DeviceListNameService {
    private final ProjectDeviceOrganizationApi projects;
    private final CustomerQueryApi customers;
    public record Query(Long tenantId, List<DeviceListProjection> rows) {}
    public record Names(String projectName, String customerName, String projectCode) {}

    public Map<Long, Names> resolve(Query query) {
        if (query.rows().isEmpty()) return Map.of();
        var projectIds = query.rows().stream().map(DeviceListProjection::projectId).filter(Objects::nonNull).collect(Collectors.toSet());
        var customerIds = query.rows().stream().map(DeviceListProjection::customerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> projectNames = new HashMap<>();
        Map<Long, String> projectCodes = new HashMap<>();
        Map<Long, String> customerNames = new HashMap<>();
        if (!projectIds.isEmpty()) projects.getOrganizations(query.tenantId(), projectIds).stream()
                .filter(p -> projectIds.contains(p.projectId()))
                .forEach(p -> {
                    projectNames.put(p.projectId(), p.projectName());
                    projectCodes.put(p.projectId(), p.projectCode());
                });
        if (!customerIds.isEmpty()) customers.getCustomers(customerIds).stream()
                .filter(c -> Objects.equals(c.tenantId(), query.tenantId()) && customerIds.contains(c.id()) && c.name() != null)
                .forEach(c -> customerNames.put(c.id(), c.name()));
        Map<Long, Names> result = new HashMap<>();
        for (var row : query.rows()) result.put(row.deviceId(), new Names(projectNames.get(row.projectId()),
                customerNames.get(row.customerId()), projectCodes.get(row.projectId())));
        return result;
    }
}
