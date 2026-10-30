package cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity.dto;

public record AcceptanceActivityCompletionCommand(
        Long tenantId,
        Long projectId,
        Long projectTaskId,
        Integer expectedProjectTaskVersion,
        Long executionContractId,
        Long acceptanceId,
        Long expectedActivityVersion,
        Integer expectedReportVersion,
        String operationId) {
}
