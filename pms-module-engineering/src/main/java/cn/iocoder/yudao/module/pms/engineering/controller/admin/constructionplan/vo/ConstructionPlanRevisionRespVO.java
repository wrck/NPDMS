package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ConstructionPlanRevisionRespVO {
    private Long revisionId;
    private Integer revisionNo;
    private String calculationBasis;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer durationDays;
    private Long sourceChangeId;
    private LocalDateTime frozenAt;
    private LocalDateTime effectiveAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Integer version;
    private Boolean current;
}
