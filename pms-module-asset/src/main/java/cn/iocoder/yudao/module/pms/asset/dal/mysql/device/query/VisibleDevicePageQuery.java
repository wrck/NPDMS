package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import java.util.Set;

@lombok.Builder(toBuilder = true)
public record VisibleDevicePageQuery(
        Long tenantId,
        Set<Long> visibleProjectIds,
        String sn,
        String productCode,
        Long projectId,
        Long customerId,
        Integer pageNo,
        Integer pageSize,
        String name,
        String status,
        Set<String> visibleContractNumbers,
        java.util.List<DeviceOrganizationGrant> organizationGrants,
        String projectName,
        String customerName,
        String departmentName,
        Set<Long> matchingProjectIds,
        Set<Long> matchingCustomerIds,
        DevicePageReadScope readScope) {

    public long offset() {
        return (long) (pageNo - 1) * pageSize;
    }

    public boolean hasDeviceFilters() {
        return projectId != null || customerId != null || matchingProjectIds != null || matchingCustomerIds != null
                || present(sn) || present(productCode) || present(name) || present(status) || present(departmentName);
    }

    private static boolean present(String value) { return value != null && !value.isEmpty(); }
}
