package cn.iocoder.yudao.module.pms.cutover.controller.business.plan.vo;

import cn.iocoder.yudao.module.pms.customer.api.contact.ProjectContactReferenceApi;

public record CutoverContactChoiceRespVO(Long contactId, Integer contactVersion, Long projectId, Long sourceContactId,
                                       String name, String mobile, String phone, String department, String title) {
    public static CutoverContactChoiceRespVO from(ProjectContactReferenceApi.Reference value) {
        return new CutoverContactChoiceRespVO(value.contactId(), value.contactVersion(), value.projectId(), value.sourceContactId(),
                value.name(), value.mobile(), value.phone(), value.department(), value.title());
    }
}
