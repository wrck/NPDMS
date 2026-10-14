package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.implementationreadiness;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

/** 仅追加；来源变化派生下一快照，绝不回写已被 CUT 引用的版本。 */
@Data @EqualsAndHashCode(callSuper = true)
@TableName("imp_cutover_readiness_snapshot")
public class ImplementationReadinessSnapshotDO extends TenantBaseDO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long projectId;
    private String deviceScopeKey;
    private Integer snapshotNo;
    @TableField(value = "readiness_decision_code")
    private String decision;
    @TableField(exist = false)
    private String contextJson;
    @TableField(exist = false)
    private String unmetCodesJson;
    @TableField(value = "evaluate_user_id")
    private Long evaluatedBy;

    /** 冻结项目范围版本；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long projectScopeVersion;
    /** 原已批准实施计划ID；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long approvedPlanId;
    /** 原计划版本；历史原件缺失时可空，新业务按既有来源/提交规则必填 */
    private Long approvedPlanVersion;
}
