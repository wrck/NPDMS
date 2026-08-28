package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Set;

public record DynamicFormRevisionRespVO(
        Long revisionId, Long templateId, Integer revisionNo, String status, Long sourceRevisionId,
        JsonNode formConfJson, JsonNode formRulesJson, String engineCode, String designerVersion,
        String rendererVersion, Integer revisionVersion, Long publishedBy, LocalDateTime publishedAt,
        Set<String> allowedActions) {

    public static DynamicFormRevisionRespVO of(DynamicFormViews.Revision value) {
        return new DynamicFormRevisionRespVO(value.revisionId(), value.templateId(), value.revisionNo(),
                value.status(), value.sourceRevisionId(), value.formConfJson(), value.formRulesJson(),
                value.engineCode(), value.designerVersion(), value.rendererVersion(), value.revisionVersion(),
                value.publishedBy(), value.publishedAt(), value.allowedActions());
    }
}
