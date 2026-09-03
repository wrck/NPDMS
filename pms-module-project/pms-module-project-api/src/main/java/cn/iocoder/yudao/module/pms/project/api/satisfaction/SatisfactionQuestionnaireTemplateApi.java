package cn.iocoder.yudao.module.pms.project.api.satisfaction;

import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTemplateFact;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTemplateResolveQuery;

public interface SatisfactionQuestionnaireTemplateApi {
    SatisfactionTemplateFact resolvePublished(SatisfactionTemplateResolveQuery query);
}
