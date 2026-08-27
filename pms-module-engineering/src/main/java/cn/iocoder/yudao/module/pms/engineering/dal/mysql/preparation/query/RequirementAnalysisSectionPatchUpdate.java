package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

public record RequirementAnalysisSectionPatchUpdate(
        Long tenantId, Long preparationId, Long sectionId, Integer expectedVersion,
        boolean updateValue, String valueSnapshot,
        boolean updateAttachments, String attachmentReferenceSnapshot, String updater) {
}
