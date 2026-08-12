package cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 项目计划变更审批 DO（FR-PROJ-020 / T-V2-PROJ-003）
 * <p>
 * 状态机：0草稿 → 1已提交 → 2审批中 → 3已通过 → 4已驳回 → 5已撤回 → 6已终止
 * 通过后生成新基线版本号；未通过恢复为可修订状态（草稿）
 */
@TableName("pms_plan_change_request")
@Data
@EqualsAndHashCode(callSuper = true)
public class PlanChangeRequestDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 变更单号，全局唯一
     */
    private String changeNo;
    /**
     * 变更标题
     */
    private String title;
    /**
     * 变更类型 PLAN_ADJUST 计划调整 / SCOPE_CHANGE 范围变更 / DATE_SHIFT 工期顺延 / OTHER 其他
     */
    private String changeType;
    /**
     * 变更原因
     */
    private String reason;
    /**
     * 客户证明材料文件URL列表（JSON数组）
     */
    private String customerProofFiles;
    /**
     * 申请人编号
     */
    private Long applicantUserId;
    /**
     * 申请时间
     */
    private LocalDateTime applyTime;
    /**
     * 审批人编号
     */
    private Long approverUserId;
    /**
     * 审批时间
     */
    private LocalDateTime approveTime;
    /**
     * 审批意见
     */
    private String approveOpinion;
    /**
     * 审批动作 PASS / REJECT / RETURN / TRANSFER / COUNTERSIGN
     */
    private String approveAction;
    /**
     * 当前基线版本号
     */
    private Integer baselineVersion;
    /**
     * 审批通过后生成的新基线版本号
     */
    private Integer newBaselineVersion;
    /**
     * 状态 0草稿 1已提交 2审批中 3已通过 4已驳回 5已撤回 6已终止
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

}
