package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverTaskDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long projectId;
    private Long previousTaskId;
    private String taskNo;
    private String taskName;
    private String background;
    private String cutoverType;
    private String networkMode;
    private LocalDateTime scheduledTime;
    private String taskOrigin;
    private String intakeSourceType;
    private String sourceSystem;
    private String sourceBusinessNo;
    private String businessEventId;
    private String currentStage;
    private String taskStatus;
    private Long ownerUserId;
    private Long customerId;
    private Long implementationReadinessSnapshotId;
    private Long implementationReadinessSnapshotVersion;
    private Long projectScopeVersion;
    private String projectContextSnapshot;
    private String deviceScopeWatermark;
    private String customerContextSnapshot;
    private String readinessContextSnapshot;
    private String manualGrade;
    private Long currentAssessmentId;
    private Long configurationRevisionId;
    private String configurationCode;
    private Integer configurationRevisionNo;
    private Long legacyTaskId;
    private String legacyCutoverTypeRaw;
    private String legacyNetworkModeRaw;
    private Integer legacyStatusValue;
    private Integer legacySourceVersion;
    private String legacyMappingVersion;
    @Version
    private Integer version;
}
