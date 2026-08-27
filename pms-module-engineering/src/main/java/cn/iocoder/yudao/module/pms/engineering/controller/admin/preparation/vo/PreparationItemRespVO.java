package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PreparationItemRespVO {
    private Long itemId;
    private Long sourceItemId;
    private String itemCode;
    private String itemName;
    private Integer sortOrder;
    private String applicability;
    private String confirmationStatus;
    private Boolean outsourced;
    private Long assigneeUserId;
    private LocalDateTime assigneeEffectiveFrom;
    private String notApplicableReason;
    private String siteResultCode;
    private String siteResultDetail;
    private String evidenceReferenceSnapshot;
    private String evidencePolicySnapshot;
    private String sourcePolicySnapshot;
    private String waiverPolicySnapshot;
    private List<PreparationSourceRespVO> sources = List.of();
    private List<String> allowedActions = List.of();
    private Integer version;
    private PreparationFormRespVO form;
}
