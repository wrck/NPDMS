package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;

import java.util.Set;

public record DynamicFormTemplateCreatedRespVO(
        Long templateId, Integer templateVersion, String availability,
        Long draftRevisionId, Integer draftRevisionNo, Integer draftVersion,
        Set<String> allowedActions) {
    public static DynamicFormTemplateCreatedRespVO of(DynamicFormViews.Template value) {
        DynamicFormViews.RevisionSummary draft = value.currentDraft();
        return new DynamicFormTemplateCreatedRespVO(value.templateId(), value.templateVersion(),
                value.availability(), draft == null ? null : draft.revisionId(),
                draft == null ? null : draft.revisionNo(), draft == null ? null : draft.revisionVersion(),
                value.allowedActions());
    }
}
