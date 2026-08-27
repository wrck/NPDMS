package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** Project WorkBinding公开事实当前允许精确查询的目标四元组。 */
public record ProjectWorkBindingTarget(
        String workBindingTypeCode,
        String targetContextCode,
        String targetObjectType,
        String targetObjectKey) {

    public static final ProjectWorkBindingTarget SITE_SURVEY_PREPARATION = new ProjectWorkBindingTarget(
            "BUSINESS_OBJECT", "SOL", "SITE_SURVEY_PREPARATION", "PRE_02_SITE_SURVEY");

    public static final ProjectWorkBindingTarget REQUIREMENT_ANALYSIS = new ProjectWorkBindingTarget(
            "BUSINESS_OBJECT", "SOL", "REQUIREMENT_ANALYSIS", "PRE_04_REQUIREMENT_ANALYSIS");

    public boolean isSupported() {
        return SITE_SURVEY_PREPARATION.equals(this) || REQUIREMENT_ANALYSIS.equals(this);
    }
}
