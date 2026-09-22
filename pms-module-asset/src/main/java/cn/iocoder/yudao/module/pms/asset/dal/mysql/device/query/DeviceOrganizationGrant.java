package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

/** 同一授权记录内配对的公司、部门范围；部门为空表示公司全范围。 */
public record DeviceOrganizationGrant(Long companyId, Long departmentId, String departmentCode) {}
