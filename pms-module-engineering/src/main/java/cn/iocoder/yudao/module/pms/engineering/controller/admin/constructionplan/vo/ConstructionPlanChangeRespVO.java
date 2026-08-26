package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConstructionPlanChangeRespVO {
    private Long changeId;
    private Long baseRevisionId;
    private Long candidateRevisionId;
    private ConstructionPlanRevisionRespVO candidateRevision;
    private String status;
    private String reasonType;
    private String reasonDetail;
    private Boolean customerEvidenceRequired;
    private Long customerEvidenceFileId;
    private Integer customerEvidenceFileVersion;
    private String processDefinitionKey;
    private String processInstanceId;
    private LocalDateTime submittedAt;
    private Long applicantUserId;
    private Long approverUserId;
    private LocalDateTime approvedAt;
    private String approvalOpinion;
    private LocalDateTime createdAt;
    private Integer version;
}
