package cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto;

public record AcceptanceActivityInitializationCommand(
        Long tenantId,
        Long projectId,
        Long projectTaskId,
        String taskDefinitionKey,
        Long executionContractId,
        String acceptanceType,
        String deliverableCode,
        Integer templateRevision) {
}
