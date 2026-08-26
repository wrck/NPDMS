package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** SOL施工计划根与当前工期指针。 */
@TableName("sol_construction_plan")
@Data
@EqualsAndHashCode(callSuper = true)
public class ConstructionPlanDO extends TenantBaseDO {

    public static final String RECALCULATION_PENDING = "PENDING_RECALCULATION";
    public static final String RECALCULATED = "RECALCULATED";
    public static final String RECALCULATION_FAILED = "RECALCULATION_FAILED";

    @TableId
    private Long id;
    private Long projectId;
    private Long currentDurationRevisionId;
    private Long pendingChangeId;
    private String planRecalculationStatusCode;
    private Long planRecalculationSourceRevisionId;
    @Version
    private Integer version;

}
