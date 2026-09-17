package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 项目治理动作 DO（FR-PROJ-022 / T-V2-PROJ-003）
 * <p>
 * 动作类型：ROLLBACK 回退总部重新指派 / DIRECT_CLOSE 直接关闭
 * 状态机：0草稿 → 1已提交 → 2审批中 → 3已执行 → 4已驳回 → 5已撤回
 * 回退：执行时将项目状态置回待指派、清空项目经理
 * 关闭：执行时将项目状态置为已关闭
 */
@TableName("proj_project_governance_action")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectGovernanceActionDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 治理动作单号，全局唯一
     */
    private String actionNo;
    /**
     * 动作类型 ROLLBACK 回退总部 / DIRECT_CLOSE 直接关闭
     */
    private String actionType;
    /**
     * 回退/关闭原因
     */
    private String reason;
    /**
     * 证明材料文件URL列表（JSON数组）
     */
    private String proofFiles;
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
     * 执行前项目状态
     */
    private Integer beforeProjectStatus;
    /**
     * 执行后项目状态
     */
    private Integer afterProjectStatus;
    /**
     * 执行前项目经理
     */
    private Long beforeManagerUserId;
    /**
     * 执行后项目经理（回退时置空）
     */
    private Long afterManagerUserId;
    /**
     * 执行时间
     */
    private LocalDateTime executeTime;
    /**
     * 状态 0草稿 1已提交 2审批中 3已执行 4已驳回 5已撤回
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
