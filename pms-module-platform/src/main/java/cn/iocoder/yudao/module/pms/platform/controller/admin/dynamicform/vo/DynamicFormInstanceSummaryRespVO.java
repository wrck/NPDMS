package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;

import java.time.LocalDateTime;
import java.util.Set;

public record DynamicFormInstanceSummaryRespVO(
        Long instanceId, String instanceCode, String instanceName, Long templateId,
        String templateCode, String templateName, Long templateRevisionId, Integer templateRevisionNo,
        Integer instanceVersion, Long createdBy, Set<String> allowedActions,
        LocalDateTime createTime, LocalDateTime updateTime) {
    public static DynamicFormInstanceSummaryRespVO of(DynamicFormViews.InstanceSummary value) {
        return new DynamicFormInstanceSummaryRespVO(value.instanceId(), value.instanceCode(), value.instanceName(),
                value.templateId(), value.templateCode(), value.templateName(), value.templateRevisionId(),
                value.templateRevisionNo(), value.instanceVersion(), value.createdBy(), value.allowedActions(),
                value.createTime(), value.updateTime());
    }
}
