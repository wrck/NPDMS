package cn.iocoder.yudao.module.pms.project.api.satisfaction.dto;

public record SatisfactionTaskInitializationCommand(Long tenantId, Long projectId, Long projectTaskId,
        Integer expectedProjectTaskVersion, String sourceOwnerContext, String sourceObjectType,
        String sourceObjectId, Long sourceObjectVersion, String triggerOwnerContext, String triggerObjectType,
        String triggerFactId, Long triggerFactVersion, String operationId) {
}
