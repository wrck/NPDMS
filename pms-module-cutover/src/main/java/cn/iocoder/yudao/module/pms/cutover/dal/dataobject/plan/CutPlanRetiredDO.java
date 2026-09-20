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
 * 对应表 {@code pms_cut_plan_retired}，承载割接方案编制与评审。
 * 评审通过后形成不可覆盖基线版本 {@link #baselineVersion}。
  * @deprecated 已随 pms_* 旧域退役：对应表已更名 pms_cut_plan_retired 且数据库侧仅允许查询（INSERT/UPDATE/DELETE 被触发器拒绝）；仅保留存量只读兼容，禁止新代码引用。
*/
@TableName("pms_cut_plan_retired")
@Data
@EqualsAndHashCode(callSuper = true)
@Deprecated
public class CutPlanRetiredDO extends TenantBaseDO {

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
