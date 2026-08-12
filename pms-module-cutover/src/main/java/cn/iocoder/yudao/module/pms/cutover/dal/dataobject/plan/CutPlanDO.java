package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 割接方案 DO（FR-CUT-008 / FR-CUT-009）。
 * <p>
 * 对应表 {@code pms_cut_plan}，承载割接方案编制与评审。
 * 评审通过后形成不可覆盖基线版本 {@link #baselineVersion}。
 */
@TableName("pms_cut_plan")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutPlanDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long taskId;
    private String code;
    private String name;
    private String preCheck;
    /**
     * 割接步骤，对应数据库字段 {@code procedure}（SQL 关键字，需 {@code @TableField} 显式映射）。
     */
    @com.baomidou.mybatisplus.annotation.TableField("`procedure`")
    private String procedure;
    private String verification;
    private String rollback;
    private String level;
    private Integer status;
    private Long approvedBy;
    private LocalDateTime approvedTime;
    private String approvalOpinion;
    private Integer baselineVersion;
    private String remark;
    @Version
    private Integer version;
}
