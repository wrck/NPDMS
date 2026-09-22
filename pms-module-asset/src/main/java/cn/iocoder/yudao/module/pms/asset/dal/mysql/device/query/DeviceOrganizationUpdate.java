package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

public record DeviceOrganizationUpdate(Long tenantId, Long deviceId, Long companyId, String companyName,
        Long departmentId, String departmentCode, String departmentName, String source) {}
