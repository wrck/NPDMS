package cn.iocoder.yudao.module.pms.project.api.reference.dto;

public record ProjectDeviceAssignmentGuardQuery(
        Long tenantId,
        Long projectId,
        Long actorId) {
}
