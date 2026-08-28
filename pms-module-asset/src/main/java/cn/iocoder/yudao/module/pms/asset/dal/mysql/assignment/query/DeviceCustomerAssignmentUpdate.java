package cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query;

public record DeviceCustomerAssignmentUpdate(
        Long tenantId,
        Long deviceId,
        Long customerId,
        Long expectedAssignmentVersion,
        Long newAssignmentVersion) {
}
