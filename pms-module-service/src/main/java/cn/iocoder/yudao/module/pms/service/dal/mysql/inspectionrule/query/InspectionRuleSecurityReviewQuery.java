package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query;

public record InspectionRuleSecurityReviewQuery(
        Long tenantId,
        Long revisionId,
        String contentDigest) {
}
