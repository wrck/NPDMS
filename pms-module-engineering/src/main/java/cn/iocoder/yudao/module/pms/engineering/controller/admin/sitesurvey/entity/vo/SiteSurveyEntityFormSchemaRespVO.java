package cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo;

/** REST projection uses JSON objects, matching the shared FormCreate codec. */
public record SiteSurveyEntityFormSchemaRespVO(Long revisionId, Integer revisionVersion,
                                       Object formConfJson, Object formRulesJson, java.util.Map<String, String> fieldBindings,
                                       java.util.List<cn.iocoder.yudao.module.pms.platform.api.entity.EntityField> fieldCatalog) {
    public static SiteSurveyEntityFormSchemaRespVO from(
            cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionFact schema, java.util.Map<String, String> fieldBindings) {
        return new SiteSurveyEntityFormSchemaRespVO(schema.templateRevisionId(), schema.revisionFactVersion(),
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(schema.formConfJson(), Object.class),
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(schema.formRulesJson(), Object.class), fieldBindings,
                cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.SiteSurveyEntityProvider.FIELDS.fields());
    }
}
