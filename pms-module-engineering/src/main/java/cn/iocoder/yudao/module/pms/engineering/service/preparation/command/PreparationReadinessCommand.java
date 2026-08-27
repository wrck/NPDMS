package cn.iocoder.yudao.module.pms.engineering.service.preparation.command;

public record PreparationReadinessCommand(
        Long preparationId,
        Integer expectedPreparationVersion,
        Integer expectedProjectVersion,
        String idempotencyKey) {}
