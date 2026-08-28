package cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command;

import java.time.LocalDate;

public record CreateDurationChangeCommand(
        Long planId, Integer expectedPlanVersion, Integer expectedProjectVersion,
        String calculationBasis, LocalDate startDate, LocalDate endDate, Integer durationDays,
        String reasonType, String reasonDetail, Long customerEvidenceFileId,
        Integer customerEvidenceFileVersion, String customerEvidenceReferenceKey,
        String idempotencyKey, String requestDigest) {

    public CreateDurationChangeCommand(
            Long planId, Integer expectedPlanVersion, Integer expectedProjectVersion,
            String calculationBasis, LocalDate startDate, LocalDate endDate, Integer durationDays,
            String reasonType, String reasonDetail, Long customerEvidenceFileId,
            Integer customerEvidenceFileVersion, String idempotencyKey, String requestDigest) {
        this(planId, expectedPlanVersion, expectedProjectVersion, calculationBasis, startDate,
                endDate, durationDays, reasonType, reasonDetail, customerEvidenceFileId,
                customerEvidenceFileVersion, null, idempotencyKey, requestDigest);
    }
}
