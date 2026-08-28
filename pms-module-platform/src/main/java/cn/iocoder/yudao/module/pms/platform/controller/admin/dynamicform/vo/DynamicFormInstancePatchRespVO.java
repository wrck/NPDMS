package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;

import java.util.List;
import java.util.Set;

public record DynamicFormInstancePatchRespVO(Long instanceId, Integer instanceVersion,
                                             List<String> changedFieldKeys, Set<String> allowedActions) {
    public static DynamicFormInstancePatchRespVO of(DynamicFormViews.InstancePatchResult value) {
        return new DynamicFormInstancePatchRespVO(value.instanceId(), value.instanceVersion(),
                value.changedFieldKeys(), value.allowedActions());
    }
}
