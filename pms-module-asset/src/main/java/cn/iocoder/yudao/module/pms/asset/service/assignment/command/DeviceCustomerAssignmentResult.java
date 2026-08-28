package cn.iocoder.yudao.module.pms.asset.service.assignment.command;

public record DeviceCustomerAssignmentResult(
        Long deviceId,
        Long customerId,
        Long assignmentVersion,
        String operationId,
        boolean replayed) {
}
