package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query;

import java.util.Set;

/** DRAFT工期变更实际提交字段的场景化CAS更新。 */
public record ConstructionPlanChangeDraftUpdate(
        Long tenantId, Long planId, Long changeId, Integer expectedVersion,
        String reasonTypeCode, String reasonDetail, Long customerEvidenceFileId,
        Integer customerEvidenceFileVersion, String customerEvidenceReferenceKey,
        Set<String> submittedFields) {

    public ConstructionPlanChangeDraftUpdate(
            Long tenantId, Long planId, Long changeId, Integer expectedVersion,
            String reasonTypeCode, String reasonDetail, Long customerEvidenceFileId,
            Integer customerEvidenceFileVersion, Set<String> submittedFields) {
        this(tenantId, planId, changeId, expectedVersion, reasonTypeCode, reasonDetail,
                customerEvidenceFileId, customerEvidenceFileVersion, null, submittedFields);
    }
}
