package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RequirementAnalysisVersionRespVO {
    private Long preparationId;
    private Long projectId;
    private Integer businessVersion;
    private Long sourcePreparationId;
    private String status;
    private Boolean currentDraft;
    private Boolean currentEffective;
    private Integer contentVersion;
    private Integer version;
    private Long templateId;
    private Long templateRevisionId;
    private Long completedBy;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private List<String> allowedActions;
    private List<RequirementAnalysisSectionRespVO> sections;
}
