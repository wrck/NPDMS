package cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command;

import java.time.LocalDate;

/** 首次工期生效命令；租户和操作者只来自受信上下文。 */
public record CreateInitialDurationCommand(
        Long projectId,
        String calculationBasisCode,
        LocalDate startDate,
        LocalDate endDate,
        Integer durationDays,
        Integer expectedProjectVersion,
        String idempotencyKey,
        String requestDigest) {
}
