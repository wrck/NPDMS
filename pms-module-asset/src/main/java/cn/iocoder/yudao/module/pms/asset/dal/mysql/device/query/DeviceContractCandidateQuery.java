package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

/** 内部合同候选集，不作为可见设备结果返回，也不提前分页。 */
@lombok.Builder(toBuilder = true)
public record DeviceContractCandidateQuery(Long tenantId, Long deviceId, String sn, String productCode,
        Long projectId, Long customerId, String name, String status, java.util.List<DeviceOrganizationGrant> organizationGrants,
        java.util.Set<Long> matchingProjectIds, java.util.Set<Long> matchingCustomerIds, String departmentName,
        java.util.Set<String> authorizedContractNumbers) {

    public boolean isUnfilteredPage() {
        return deviceId == null && projectId == null && customerId == null
                && matchingProjectIds == null && matchingCustomerIds == null
                && empty(sn) && empty(productCode) && empty(name) && empty(status) && empty(departmentName);
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
