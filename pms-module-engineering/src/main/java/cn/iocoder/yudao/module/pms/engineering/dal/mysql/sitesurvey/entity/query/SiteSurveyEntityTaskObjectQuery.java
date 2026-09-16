package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query;

/** REQ-PROJ-004: exact Owner identity, including tenant/project even for primary-key reads. */
public record SiteSurveyEntityTaskObjectQuery(Long tenantId, Long projectId, Long objectId) {
    public SiteSurveyEntityTaskObjectQuery {
        if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0
                || objectId == null || objectId <= 0) {
            throw new IllegalArgumentException("工勘对象范围无效");
        }
    }
}
