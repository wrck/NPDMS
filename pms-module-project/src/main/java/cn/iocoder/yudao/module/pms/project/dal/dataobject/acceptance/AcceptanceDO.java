package cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 初验/终验 DO
 * <p>
 * 状态机：0草稿 → 1待提交 → 2审批中 → 3已通过 → 4已驳回 → 5已归档
 * 验收类型：PRELIMINARY 初验 / FINAL 终验
 * 门禁：验收通过（pass 2→3）前校验交付件完整性（FR-ACC-005）
 *
 * @deprecated 旧V17历史载体；不得作为F-ACC-001及后续能力的实现基础。
 */
@TableName("pms_acc_acceptance")
@Data
@EqualsAndHashCode(callSuper = true)
@Deprecated(since = "F-ACC-001", forRemoval = false)
public class AcceptanceDO extends TenantBaseDO {

    /**
     * 主键编号
     */
    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 验收编码，项目内唯一
     */
    private String code;
    /**
     * 验收名称
     */
    private String name;
    /**
     * 验收类型 PRELIMINARY 初验 / FINAL 终验
     */
    private String acceptanceType;
    /**
     * 验收日期
     */
    private LocalDate acceptanceDate;
    /**
     * 关联交付计划编号
     */
    private Long planId;
    /**
     * 申请人
     */
    private Long applicantUserId;
    /**
     * 申请时间
     */
    private LocalDateTime applyTime;
    /**
     * 审批人
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
     * 归档时间
     */
    private LocalDateTime archiveTime;
    /**
     * 状态 0草稿 1待提交 2审批中 3已通过 4已驳回 5已归档
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
