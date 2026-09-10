package cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.vo;

/** REST projection uses JSON objects, matching the shared FormCreate codec. */
public record SiteSurveyFormSchemaRespVO(Long revisionId, Integer revisionVersion,
                                       Object formConfJson, Object formRulesJson) {
    public static SiteSurveyFormSchemaRespVO from(
            cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionFact schema) {
        return new SiteSurveyFormSchemaRespVO(schema.templateRevisionId(), schema.revisionFactVersion(),
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(schema.formConfJson(), Object.class),
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(schema.formRulesJson(), Object.class));
    }
}
