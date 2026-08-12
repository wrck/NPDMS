package cn.iocoder.yudao.module.pms.project.dal.dataobject.deliverablechecklist;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 交付件完整性检查 DO
 * <p>
 * 状态机：0草稿 → 1已提交 → 2已通过 / 3已驳回
 * 交付件类型：REQUIRED 必交 / OPTIONAL 选交 / CONDITIONAL 条件
 * 用途：FR-ACC-005 验收通过前的交付件完整性门禁数据源
 */
@TableName("pms_acc_deliverable_checklist")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliverableChecklistDO extends TenantBaseDO {

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
     * 交付件编码，项目内唯一
     */
    private String code;
    /**
     * 交付件名称
     */
    private String name;
    /**
     * 关联验收编号
     */
    private Long acceptanceId;
    /**
     * 交付件类型 REQUIRED 必交 / OPTIONAL 选交 / CONDITIONAL 条件
     */
    private String deliverableType;
    /**
     * 交付件附件地址
     */
    private String deliverableUrl;
    /**
     * 检查人
     */
    private Long checkUserId;
    /**
     * 检查时间
     */
    private LocalDateTime checkTime;
    /**
     * 检查结果
     */
    private String checkResult;
    /**
     * 状态 0草稿 1已提交 2已通过 3已驳回
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
