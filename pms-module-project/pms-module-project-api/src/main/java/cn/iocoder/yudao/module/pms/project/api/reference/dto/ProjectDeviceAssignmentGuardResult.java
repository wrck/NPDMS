package cn.iocoder.yudao.module.pms.project.api.reference.dto;

public record ProjectDeviceAssignmentGuardResult(
        Long projectId,
        Long tenantId,
        Long customerId,
        Long rootProjectId,
        Long treeVersion,
        boolean assignable,
        String rejectionCode) {
}
