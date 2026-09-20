package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.risk;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 割接风险与调研清单 DO（FR-CUT-004 / FR-CUT-006）。
 * <p>
 * 对应表 {@code pms_cut_risk_retired}，按割接任务维度维护风险/调研项。
  * @deprecated 已随 pms_* 旧域退役：对应表已更名 pms_cut_risk_retired 且数据库侧仅允许查询（INSERT/UPDATE/DELETE 被触发器拒绝）；仅保留存量只读兼容，禁止新代码引用。
*/
@TableName("pms_cut_risk_retired")
@Data
@EqualsAndHashCode(callSuper = true)
@Deprecated
public class CutRiskRetiredDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long taskId;
    private String code;
    private String name;
    private String riskType;
    private String description;
    private String impact;
    private String mitigation;
    private Long ownerUserId;
    private Integer status;
    private String remark;
    @Version
    private Integer version;
}
