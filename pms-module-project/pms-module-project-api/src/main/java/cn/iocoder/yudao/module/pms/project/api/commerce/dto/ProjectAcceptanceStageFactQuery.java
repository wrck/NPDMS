package cn.iocoder.yudao.module.pms.project.api.commerce.dto;

public record ProjectAcceptanceStageFactQuery(
        Long tenantId,
        Long projectId,
        Long expectedProjectVersion,
        String operationId) {
}
