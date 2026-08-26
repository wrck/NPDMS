package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PreparationItemRespVO {
    private Long itemId;
    private String itemCode;
    private String itemName;
    private Integer sortOrder;
    private String applicability;
    private String confirmationStatus;
    private Boolean outsourced;
    private Long assigneeUserId;
    private LocalDateTime assigneeEffectiveFrom;
    private String siteResultCode;
    private String siteResultDetail;
    private String evidenceReferenceSnapshot;
    private String evidencePolicySnapshot;
    private String sourcePolicySnapshot;
    private String waiverPolicySnapshot;
    private Integer version;
    private PreparationFormRespVO form;
}
