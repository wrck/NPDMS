package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;

public record DynamicFormRevisionPatchedRespVO(
        Long revisionId, Integer revisionNo, String status, Integer revisionVersion) {

    public static DynamicFormRevisionPatchedRespVO of(DynamicFormViews.Revision value) {
        return new DynamicFormRevisionPatchedRespVO(value.revisionId(), value.revisionNo(), value.status(),
                value.revisionVersion());
    }
}
