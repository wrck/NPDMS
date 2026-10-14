package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverTaskDO extends BaseBusinessEntity {
    private Long projectId;
    private Long previousTaskId;
    private String taskNo;
    private String taskName;
    private String background;
    @TableField(value = "cutover_type_code")
    private String cutoverType;
    @TableField(value = "network_mode_code")
    private String networkMode;
    @TableField(value = "schedule_time")
    private LocalDateTime scheduledTime;
    @TableField(value = "task_origin_code")
    private String taskOrigin;
    @TableField(value = "intake_source_type_code")
    private String intakeSourceType;
    private String sourceSystem;
    private String sourceBusinessNo;
    private String businessEventId;
    @TableField(value = "current_stage_code")
    private String currentStage;
    @TableField(value = "task_status_code")
    private String taskStatus;
    private Long ownerUserId;
    private Long customerId;
    private Long implementationReadinessSnapshotId;
    private Long implementationReadinessSnapshotVersion;
    private Long projectScopeVersion;
    @TableField(exist = false)
    private String projectContextSnapshot;
    @TableField(exist = false)
    private String deviceScopeWatermark;
    @TableField(exist = false)
    private String customerContextSnapshot;
    @TableField(exist = false)
    private String readinessContextSnapshot;
    @TableField(value = "manual_grade_code")
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


    /** 来源项目版本；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long projectVersion;
    /** 来源项目编码；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String projectCode;
    /** 来源项目名称；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String projectName;
    /** 来源客户编码 */
    private String customerCode;
    /** 来源客户名称 */
    private String customerName;
    /** 来源部门ID */
    private Long departmentId;
    /** 来源部门编码 */
    private String departmentCode;
    /** 来源部门名称 */
    private String departmentName;
    /** 原客户等级事实状态；原无事实时不造默认状态 */
    private String customerFactStatusCode;
    /** 客户服务等级修订引用 */
    private Long serviceLevelRevisionId;
    /** 来源服务等级编码 */
    private String serviceLevelCode;
    /** CustomerLevelFact.factVersion；保留long类型 */
    private Long customerFactVersion;
    /** 来源等级生效时间 */
    private LocalDateTime serviceLevelEffectiveStartTime;
    /** 来源等级失效时间 */
    private LocalDateTime serviceLevelEffectiveEndTime;
    /** 所引用就绪快照的原结论；独立准入不造READY */
    private String readinessDecisionCode;
}
