package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_assessment")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverAssessmentDO extends BaseBusinessEntity {
    @TableField(value = "task_id")
    private Long cutoverTaskId;
    private Long assessmentVersion;
    @TableField(value = "assessment_status_code")
    private String assessmentStatus;
    private String questionnaireTemplateCode;
    private Long questionnaireTemplateVersion;
    @TableField(exist = false)
    private String answerSnapshot;
    @TableField(exist = false)
    private String contextSnapshot;
    @TableField(value = "manual_grade_code")
    private String manualGrade;
    @TableField(value = "simple_flow_flag")
    private Boolean simpleFlow;
    @TableField(value = "submit_user_id")
    private Long submittedBy;
    @TableField(value = "submit_time")
    private LocalDateTime submittedAt;
    @TableField(value = "invalidate_user_id")
    private Long invalidatedBy;
    @TableField(value = "invalidate_time")
    private LocalDateTime invalidatedAt;
    private String invalidationReason;
    private Integer currentMarker;


    /** 填写值：业务重要程度 */
    private String businessImportanceLevel;
    /** 填写值：操作复杂程度 */
    private String operationComplexityLevel;
    /** 填写值：隐患程度 */
    private String hiddenRiskLevel;
    /** 填写值：是否申请备件 */
    private Boolean sparePartAppliedFlag;
    /** 本次评估绑定的准入来源类型；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private String intakeSourceTypeCode;
    /** IMP不可变就绪快照ID */
    private Long implementationReadinessSnapshotId;
    /** IMP快照版本 */
    private Long implementationReadinessSnapshotVersion;
    /** 原就绪结论 */
    private String readinessDecisionCode;
    /** 来源项目ID；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long projectId;
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
}
