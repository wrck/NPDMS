package cn.iocoder.yudao.module.pms.engineering.api.readiness.dto;

public record ReadinessItemFact(
        Long itemId,
        String itemCode,
        Integer itemVersion,
        String applicabilityCode,
        String confirmationStatusCode,
        Boolean outsourced,
        Long assigneeUserId,
        Long formInstanceId,
        String formCode,
        Integer formDefinitionVersion,
        Integer formInstanceVersion,
        String formStatusCode) {}
