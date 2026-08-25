package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** PM-03、PM-10、EXE-06共享项目阶段快照。 */
@TableName("proj_project_stage_snapshot")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectStageSnapshotDO extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
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
    private String guardSnapshotJson;
    private Long treeVersion;
    private String providerFactsJson;
    private Long relatedSnapshotId;
    private String operationId;
    private Long operatorUserId;
    private LocalDateTime operatedAt;
}
