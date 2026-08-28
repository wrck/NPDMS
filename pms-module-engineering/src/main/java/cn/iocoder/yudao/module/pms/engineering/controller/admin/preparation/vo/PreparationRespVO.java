package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PreparationRespVO {
    private Long preparationId;
    private Long projectId;
    private String preparationType;
    private Integer businessVersion;
    private Boolean current;
    private Long templateId;
    private Long templateRevisionId;
    private Integer fixedFormCatalogVersion;
    private String status;
    private String readinessStatus;
    private Long latestReadinessSnapshotId;
    private Integer inputVersion;
    private Integer readinessVersion;
    private Boolean snapshotCurrent;
    private LocalDateTime submittedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime returnedAt;
    private String returnReason;
    private Integer version;
    private LocalDateTime createdAt;
    private List<String> allowedActions;
}
