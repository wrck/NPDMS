package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query;

/** REQ-PROJ-004: bounded, trusted project candidates from the existing SOL survey aggregate. */
public record SiteSurveyTaskCandidateQuery(Long tenantId, Long projectId, int limit) {
    public SiteSurveyTaskCandidateQuery {
        if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0
                || limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("工勘候选范围或数量无效");
        }
    }
}
