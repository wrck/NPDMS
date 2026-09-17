package cn.iocoder.yudao.module.pms.acceptance.api.satisfaction;

import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTemplateFact;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTemplateResolveQuery;

public interface SatisfactionQuestionnaireTemplateApi {
    SatisfactionTemplateFact resolvePublished(SatisfactionTemplateResolveQuery query);
}
