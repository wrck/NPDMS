package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PreparationReadinessSnapshotRespVO {

    private Long snapshotId;
    private Integer snapshotNo;
    private String result;
    private Integer ruleVersion;
    private Long projectScopeVersion;
    private Integer inputVersion;
    private Integer preparationVersion;
    private Integer readinessVersion;
    private String itemFacts;
    private String fileFacts;
    private String sourceFacts;
    private String waiverFacts;
    private String blockers;
    private Long evaluatedBy;
    private LocalDateTime evaluatedAt;
}
