package cn.iocoder.yudao.module.pms.asset.service.assignment.command;

public record DeviceProjectAssignmentResult(
        Long deviceId,
        Long oldProjectId,
        Long projectId,
        Long assignmentVersion,
        String operationId,
        boolean replayed) {
}
