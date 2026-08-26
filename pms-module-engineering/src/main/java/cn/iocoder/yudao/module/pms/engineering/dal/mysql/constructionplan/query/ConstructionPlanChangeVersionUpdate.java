package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query;

import java.time.LocalDateTime;

/** 工期变更完整可变状态的版本CAS更新。 */
public record ConstructionPlanChangeVersionUpdate(
        Long tenantId,
        Long planId,
        Long changeId,
        Integer expectedVersion,
        String statusCode,
        String reasonTypeCode,
        String reasonDetail,
        Boolean customerEvidenceRequired,
        Long customerEvidenceFileId,
        Integer customerEvidenceFileVersion,
        String processDefinitionKey,
        String processInstanceId,
        LocalDateTime submittedAt,
        Long approverUserId,
        LocalDateTime approvedAt,
        String approvalOpinion) {
}
