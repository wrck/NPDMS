package cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command;

import java.time.LocalDate;

public record CreateDurationChangeCommand(
        Long planId, Integer expectedPlanVersion, Integer expectedProjectVersion,
        String calculationBasis, LocalDate startDate, LocalDate endDate, Integer durationDays,
        String reasonType, String reasonDetail, Long customerEvidenceFileId,
        Integer customerEvidenceFileVersion, String idempotencyKey, String requestDigest) {
}
