package cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("proj_project_exit_record")
@Data @EqualsAndHashCode(callSuper = true)
public class NormalClosureExitRecordDO extends TenantBaseDO {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long projectId;
    private Long applicationId;
    private Long snapshotId;
    private Integer projectVersion;
    private Long stageInstanceId;
    private Long templateRevisionId;
    private Long scopeVersion;
    private Long gateSnapshotRef;
    private String sourceContext;
    private Long sourceRecordId;
    private Integer sourceRecordRevision;
    private String closureType;
    private String closedFromStage;
    private String beforeLifecycleStatus;
    private String afterLifecycleStatus;
    private Integer beforeProjectVersion;
    private Integer afterProjectVersion;
    private String processInstanceId;
    private String revalidationEvidence;
    private LocalDateTime closedAt;
}
