package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;

import java.time.LocalDateTime;

public record DynamicFormPublishRespVO(Long templateId, Integer templateVersion,
                                       Long revisionId, Integer revisionNo, String status,
                                       Long publishedBy, LocalDateTime publishedAt, String availability) {
    public static DynamicFormPublishRespVO of(DynamicFormViews.PublishResult value) {
        return new DynamicFormPublishRespVO(value.templateId(), value.templateVersion(),
                value.revision().revisionId(), value.revision().revisionNo(), value.revision().status(),
                value.revision().publishedBy(), value.revision().publishedAt(), value.availability());
    }
}
