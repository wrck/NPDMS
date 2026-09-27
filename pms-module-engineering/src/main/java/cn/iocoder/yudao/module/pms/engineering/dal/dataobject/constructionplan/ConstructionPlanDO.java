package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** SOL施工计划根与当前工期指针。 */
@TableName("sol_construction_plan")
@Data
@EqualsAndHashCode(callSuper = true)
public class ConstructionPlanDO extends BaseBusinessEntity {

    public static final String RECALCULATION_PENDING = "PENDING_RECALCULATION";
    public static final String RECALCULATED = "RECALCULATED";
    public static final String RECALCULATION_FAILED = "RECALCULATION_FAILED";

    private Long projectId;
    private Long currentDurationRevisionId;
    private Long pendingChangeId;
    private String planRecalculationStatusCode;
    private Long planRecalculationSourceRevisionId;

}
