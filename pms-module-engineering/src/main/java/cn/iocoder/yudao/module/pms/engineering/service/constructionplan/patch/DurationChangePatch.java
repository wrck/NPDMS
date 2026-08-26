package cn.iocoder.yudao.module.pms.engineering.service.constructionplan.patch;

import java.time.LocalDate;
import java.util.Set;

/** 工期草稿PATCH字段存在性快照。 */
public record DurationChangePatch(
        String calculationBasis, LocalDate startDate, LocalDate endDate, Integer durationDays,
        String reasonType, String reasonDetail, Long customerEvidenceFileId,
        Integer customerEvidenceFileVersion, String customerEvidenceReferenceKey,
        Set<String> submittedFields) {
    public DurationChangePatch {
        submittedFields = submittedFields == null ? Set.of() : Set.copyOf(submittedFields);
    }

    public DurationChangePatch(
            String calculationBasis, LocalDate startDate, LocalDate endDate, Integer durationDays,
            String reasonType, String reasonDetail, Long customerEvidenceFileId,
            Integer customerEvidenceFileVersion, Set<String> submittedFields) {
        this(calculationBasis, startDate, endDate, durationDays, reasonType, reasonDetail,
                customerEvidenceFileId, customerEvidenceFileVersion, null, submittedFields);
    }
}
