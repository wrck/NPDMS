package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_approval_instance")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverApprovalInstanceDO extends BaseBusinessEntity {
    private Long taskId;
    private Long projectId;
    private Long planRevisionId;
    private Integer planRevisionNo;
    private Long assessmentId;
    private Long assessmentVersion;
    private Long checklistId;
    private Long checklistVersion;
    private String gradeCode;
    private Long initiatorUserId;
    private Long initiatorProjectScopeVersion;
    private Integer sourceSnapshotVersion;
    @TableField(exist = false)
    private String sourceSnapshot;
    @TableField(exist = false)
    private String routeSnapshot;
    @TableField(value = "lead_time_enabled_flag")
    private Boolean leadTimeEnabled;
    @TableField(exist = false)
    private String leadTimeSnapshot;
    private String statusCode;
    private String holdReasonCode;
    private Integer currentNodeNo;
    private Long previousApprovalInstanceId;
    private Long replacementApprovalInstanceId;
    @TableField(value = "decide_time")
    private LocalDateTime decisionAt;
    private String rejectionReason;
    private Long withdrawUserId;
    private LocalDateTime withdrawTime;
    private String withdrawReason;


    /** 提交时任务版本；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long sourceTaskVersion;
    /** 提交时方案并发版本；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long sourcePlanVersion;
    /** 当时割接类型；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String sourceCutoverTypeCode;
    /** 当时组网模式 */
    private String sourceNetworkModeCode;
    /** 当时计划时间；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private LocalDateTime sourceScheduleTime;
    /** 提前时间规则版本 */
    private String leadRuleVersion;
    /** 提前时间计算时区 */
    private String leadTimezoneId;
    /** 方案提交时间 */
    private LocalDateTime leadPlanSubmitTime;
    /** 原规则所需自然日数 */
    private Integer leadRequiredDays;
    /** 原实际自然日数 */
    private Integer leadActualNaturalDays;
    /** 原提前时间不足结论 */
    private Boolean leadLateSubmissionFlag;
    /** 来源项目版本；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long projectVersion;
    /** 来源项目编码；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String projectCode;
    /** 来源项目名称；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String projectName;
    /** 来源客户ID；原无客户时为空 */
    private Long customerId;
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
    /** 来源项目范围版本；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long projectScopeVersion;
}
