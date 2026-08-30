package cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto;

public record AcceptanceActivityCompletionCommand(
        Long tenantId,
        Long projectId,
        Long projectTaskId,
        Integer expectedProjectTaskVersion,
        Long executionContractId,
        Long acceptanceId,
        Integer expectedActivityVersion,
        Integer expectedReportVersion,
        String operationId) {
}
