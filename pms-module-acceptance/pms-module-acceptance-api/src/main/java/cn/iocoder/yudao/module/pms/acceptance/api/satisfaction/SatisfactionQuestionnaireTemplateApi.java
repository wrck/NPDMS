package cn.iocoder.yudao.module.pms.acceptance.api.satisfaction;

import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTemplateFact;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTemplateResolveQuery;

public interface SatisfactionQuestionnaireTemplateApi {
    SatisfactionTemplateFact resolvePublished(SatisfactionTemplateResolveQuery query);
    /** 明确选择当前发布修订；来源由ACC校验，PROJ只冻结权威Fact。 */
    default SatisfactionTemplateFact inspectPublished(Long templateId, Long revisionId) {
        throw new UnsupportedOperationException("Published questionnaire inspection unavailable");
    }
}
