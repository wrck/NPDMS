package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.projection;

public record InspectionRulePublicationLockProjection(
        Long ruleId,
        Integer ruleVersion,
        Long targetRevisionId,
        Integer targetRevisionVersion,
        String targetRevisionStatusCode,
        Long currentPublishedRevisionId,
        Integer currentPublishedRevisionNo,
        Integer currentPublishedRevisionVersion) {
}
