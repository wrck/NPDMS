package cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

@TableName("proj_project_exit_record")
@Data @EqualsAndHashCode(callSuper = true)
public class NormalClosureExitRecordDO extends BaseBusinessEntity {
    private Long projectId;
    private Long applicationId;
    private Long snapshotId;
    private Long projectVersion;
    private Long stageInstanceId;
    private Long templateRevisionId;
    private Long scopeVersion;
    private Long gateSnapshotRef;
    private String sourceContext;
    private Long sourceRecordId;
    private Long sourceRecordRevision;
    private String closureType;
    private String closedFromStage;
    private String beforeLifecycleStatus;
    private String afterLifecycleStatus;
    private Long beforeProjectVersion;
    private Long afterProjectVersion;
    private String processInstanceId;
    private String revalidationEvidence;
    private LocalDateTime closedAt;
}
