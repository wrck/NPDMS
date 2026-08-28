package cn.iocoder.yudao.module.pms.engineering.api.readiness;

import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.SiteSurveyReadinessFact;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.SiteSurveyReadinessQuery;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.SiteSurveyReadinessRevalidationQuery;

/** PRE-02工勘就绪公共只读契约。 */
public interface SiteSurveyReadinessApi {

    SiteSurveyReadinessFact inspect(SiteSurveyReadinessQuery query);

    SiteSurveyReadinessFact lockAndRevalidate(SiteSurveyReadinessRevalidationQuery query);
}
