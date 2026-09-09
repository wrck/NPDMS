package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query;

/** FR-ENG-001：锁定原行的身份、版本和状态，禁止请求体替换写入目标。 */
public record SiteSurveyWriteCondition(Long tenantId, Long id, Long projectId, String code,
                                      Integer expectedVersion, Integer expectedStatus, String updater) {
}
