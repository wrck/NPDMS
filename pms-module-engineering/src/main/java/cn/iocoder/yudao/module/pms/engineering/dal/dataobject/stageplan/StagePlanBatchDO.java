package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 阶段施工计划批次 DO（PLN-01/04，Demo 3.1）。
 * <p>
 * 对应表 {@code sol_stage_plan_batch}。
 * 状态：0 草稿、1 审批中、2 已生效、3 已驳回；驳回后可调整重提。
 * 审批通过后经受控写入契约（ProjectStagePlanApi.applyPlanDates）回写阶段计划日期。
 */
@TableName("sol_stage_plan_batch")
@Data
@EqualsAndHashCode(callSuper = true)
public class StagePlanBatchDO extends TenantBaseDO {

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PENDING_APPROVAL = 1;
    public static final int STATUS_EFFECTIVE = 2;
    public static final int STATUS_REJECTED = 3;

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * PROJ 项目 ID
     */
    private Long projectId;
    /**
     * 状态：0草稿 1审批中 2已生效 3已驳回
     */
    private Integer status;
    /**
     * 推算基准工期版本 ID（空=按阶段建议时间）
     */
    private Long durationRevisionId;
    private java.time.LocalDate calculatedStart;
    private java.time.LocalDate calculatedEnd;
    private String inputSnapshot;
    private String taskPlansJson;

    /**
     * 备注
     */
    private String remark;
    /**
     * 审批流程定义 Key
     */
    private String bpmProcessKey;
    /**
     * 审批流程实例 ID
     */
    private String bpmProcessInstanceId;
    /**
     * 提交时间
     */
    private LocalDateTime submittedAt;
    /**
     * 提交人
     */
    private Long submittedBy;
    /**
     * 审批生效时间
     */
    private LocalDateTime effectiveAt;
    /**
     * 生效执行人（BPM 回调）
     */
    private Long effectiveBy;
    /**
     * 驳回原因
     */
    private String rejectReason;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

}
