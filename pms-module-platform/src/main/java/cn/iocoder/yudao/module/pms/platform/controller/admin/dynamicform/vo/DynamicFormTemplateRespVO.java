package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;

import java.time.LocalDateTime;
import java.util.Set;

public record DynamicFormTemplateRespVO(
        Long templateId, String templateCode, String templateName, String categoryCode,
        String description, String availability, Integer templateVersion,
        Long currentPublishedRevisionId, DynamicFormViews.RevisionSummary currentDraft,
        DynamicFormViews.RevisionSummary currentPublished, Set<String> allowedActions,
        LocalDateTime createTime, LocalDateTime updateTime) {

    public static DynamicFormTemplateRespVO of(DynamicFormViews.Template value) {
        return new DynamicFormTemplateRespVO(value.templateId(), value.templateCode(), value.templateName(),
                value.categoryCode(), value.description(), value.availability(), value.templateVersion(),
                value.currentPublishedRevisionId(), value.currentDraft(), value.currentPublished(),
                value.allowedActions(), value.createTime(), value.updateTime());
    }
}
