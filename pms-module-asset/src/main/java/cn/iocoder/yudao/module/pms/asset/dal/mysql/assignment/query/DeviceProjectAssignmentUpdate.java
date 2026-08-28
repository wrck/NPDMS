package cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query;

public record DeviceProjectAssignmentUpdate(
        Long tenantId,
        Long deviceId,
        Long projectId,
        Long expectedAssignmentVersion,
        Long newAssignmentVersion) {
}
