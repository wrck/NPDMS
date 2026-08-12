package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectclosure;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 项目闭环审批 DO
 * <p>
 * 状态机：0草稿 → 1待审批 → 2审批中 → 3已通过 → 4已驳回 → 5已归档
 * 闭环类型：NORMAL 正常闭环 / CONDITIONAL 带条件移交
 * 门禁：闭环通过（pass 2→3）前校验 阶段完成 + 验收通过 + 问题关闭 + 审批完成
 * 【待确认：遗留问题闭环规则】允许带条件移交（CONDITIONAL），具体移交条件由业务规则补充，本实现承载流程数据。
 */
@TableName("pms_acc_project_closure")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectClosureDO extends TenantBaseDO {

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
     * 闭环编码，项目内唯一
     */
    private String code;
    /**
     * 闭环名称
     */
    private String name;
    /**
     * 闭环类型 NORMAL 正常闭环 / CONDITIONAL 带条件移交
     */
    private String closureType;
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
     * 遗留问题摘要
     */
    private String legacyIssueSummary;
    /**
     * 归档时间
     */
    private LocalDateTime archiveTime;
    /**
     * 状态 0草稿 1待审批 2审批中 3已通过 4已驳回 5已归档
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
