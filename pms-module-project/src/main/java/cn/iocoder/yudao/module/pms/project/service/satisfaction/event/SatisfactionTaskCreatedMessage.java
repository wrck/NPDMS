package cn.iocoder.yudao.module.pms.project.service.satisfaction.event;

import java.math.BigDecimal;

public record SatisfactionTaskCreatedMessage(
        String eventId, Long tenantId, Long projectId, Long projectTaskId, Integer projectTaskVersion,
        String taskCode, Long taskId, String collectionKey, Integer taskRevisionNo, Long priorTaskId,
        String sourceOwnerContext, String sourceObjectType, String sourceObjectId, Long sourceObjectVersion,
        String triggerOwnerContext, String triggerObjectType, String triggerFactId, Long triggerFactVersion,
        Long questionnaireId, Long templateRevisionId, Integer templateVersion, String ruleVersion,
        BigDecimal threshold, Long assigneeUserId) {
}
