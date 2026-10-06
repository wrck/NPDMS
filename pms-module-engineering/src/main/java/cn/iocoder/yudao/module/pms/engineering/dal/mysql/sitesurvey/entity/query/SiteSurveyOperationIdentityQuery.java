package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query;

/** Tenant and native target are mandatory; a client project never establishes identity. */
public record SiteSurveyOperationIdentityQuery(Long tenantId, Long objectId) {
    public SiteSurveyOperationIdentityQuery {
        if (tenantId == null || tenantId < 0 || objectId == null || objectId <= 0)
            throw new IllegalArgumentException("SITE_SURVEY_IDENTITY_INVALID");
    }
}
