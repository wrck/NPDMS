package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo;

import lombok.Data;

import java.util.List;

@Data
public class ConstructionPlanRespVO {
    private Long planId;
    private Long projectId;
    private ConstructionPlanRevisionRespVO currentRevision;
    private ConstructionPlanChangeRespVO pendingChangeSummary;
    private String planRecalculationStatus;
    private Long planRecalculationSourceRevisionId;
    private Integer planVersion;
    private List<String> allowedActions = List.of();
}
