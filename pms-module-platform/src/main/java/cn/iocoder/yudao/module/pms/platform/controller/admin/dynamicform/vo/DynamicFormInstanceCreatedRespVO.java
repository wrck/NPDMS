package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;

import java.util.Set;

public record DynamicFormInstanceCreatedRespVO(
        Long instanceId, String instanceCode, Long templateId, Long templateRevisionId,
        Integer templateRevisionNo, Integer instanceVersion, Set<String> allowedActions) {
    public static DynamicFormInstanceCreatedRespVO of(DynamicFormViews.InstanceCreated value) {
        return new DynamicFormInstanceCreatedRespVO(value.instanceId(), value.instanceCode(), value.templateId(),
                value.templateRevisionId(), value.templateRevisionNo(), value.instanceVersion(),
                value.allowedActions());
    }
}
