package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;

public record DynamicFormRevisionCreatedRespVO(
        Long revisionId, Integer revisionNo, String status,
        Long sourceRevisionId, Integer revisionVersion) {

    public static DynamicFormRevisionCreatedRespVO of(DynamicFormViews.Revision value) {
        return new DynamicFormRevisionCreatedRespVO(value.revisionId(), value.revisionNo(), value.status(),
                value.sourceRevisionId(), value.revisionVersion());
    }
}
