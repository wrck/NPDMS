package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 项目治理历史响应")
@Data
public class ProjectGovernanceHistoryRespVO {

    private Long id;
    private Long projectId;
    private String stageCode;
    private Integer snapshotNo;
    private String operationType;
    private String beforeStage;
    private String afterStage;
    private String beforeLifecycleStatus;
    private String afterLifecycleStatus;
    private String beforeAssignmentStatus;
    private String afterAssignmentStatus;
    private String reasonCode;
    private String reasonDetail;
    private String reassignmentRequirement;
    private String businessBasis;
    private String legacyItemsJson;
    private Long treeVersion;
    private String providerFactsJson;
    private Long relatedSnapshotId;
    private String operationId;
    private Long operatorUserId;
    private LocalDateTime operatedAt;
}
