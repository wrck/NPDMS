package cn.iocoder.yudao.module.pms.customer.service.customer;

public record CustomerPlatformUpdate(
        Long tenantId,
        Long customerId,
        String name,
        String shortName,
        String remark,
        String departmentCode,
        String departmentName,
        String marketCode,
        String marketName,
        String systemCode,
        String systemName,
        String expendCode,
        String expendName,
        String industryCode,
        String industryName,
        boolean updateName,
        boolean updateShortName,
        boolean updateRemark,
        boolean updateClassification,
        Long expectedVersion) {

    public CustomerPlatformUpdate(
            Long tenantId,
            Long customerId,
            String name,
            String shortName,
            String remark,
            boolean updateName,
            boolean updateShortName,
            boolean updateRemark,
            Long expectedVersion) {
        this(tenantId, customerId, name, shortName, remark,
                null, null, null, null, null, null, null, null, null, null,
                updateName, updateShortName, updateRemark, false, expectedVersion);
    }
}
