package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;

import java.util.Set;

public record DynamicFormSelectionRespVO(
        Long templateId, String templateCode, String templateName, String categoryCode,
        String description, Long currentPublishedRevisionId, Integer currentPublishedRevisionNo,
        String engineCode, String designerVersion, String rendererVersion,
        Integer templateVersion, Set<String> allowedActions) {

    public static DynamicFormSelectionRespVO of(DynamicFormViews.Selection value) {
        return new DynamicFormSelectionRespVO(value.templateId(), value.templateCode(), value.templateName(),
                value.categoryCode(), value.description(), value.currentPublishedRevisionId(),
                value.currentPublishedRevisionNo(), value.engineCode(), value.designerVersion(),
                value.rendererVersion(), value.templateVersion(), value.allowedActions());
    }
}
