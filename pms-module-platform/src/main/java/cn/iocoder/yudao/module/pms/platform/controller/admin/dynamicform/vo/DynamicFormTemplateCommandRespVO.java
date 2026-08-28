package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;

import java.util.Set;

public record DynamicFormTemplateCommandRespVO(
        Long templateId, Integer templateVersion, String availability,
        Long currentPublishedRevisionId, Set<String> allowedActions) {

    public static DynamicFormTemplateCommandRespVO of(DynamicFormViews.Template value) {
        return new DynamicFormTemplateCommandRespVO(value.templateId(), value.templateVersion(),
                value.availability(), value.currentPublishedRevisionId(), value.allowedActions());
    }
}
