package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.business;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
@Data @EqualsAndHashCode(callSuper=true) @TableName("sol_construction_plan")
@ProjectBusinessModel(ownerModule="SOL",entityType="constructionPlan",stableCode="SOL_CONSTRUCTION_PLAN",name="项目工期",permissionPrefix="pms:construction-plan",nativeEntityType="ConstructionPlan")
public class DurationBusinessDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name="当前工期版本",writable=false) private Long currentDurationRevisionId;
    @BusinessModelField(name="待审批变更",writable=false) private Long pendingChangeId;
    @BusinessModelField(name="计划重算状态",writable=false) private String planRecalculationStatusCode;
    private Long planRecalculationSourceRevisionId;
    @TableField(exist=false) @BusinessModelField(name="计算口径",searchable=false,sortable=false) private String calculationBasis;
    @TableField(exist=false) @BusinessModelField(name="开始日期",searchable=false,sortable=false) private LocalDate startDate;
    @TableField(exist=false) @BusinessModelField(name="结束日期",searchable=false,sortable=false) private LocalDate endDate;
    @TableField(exist=false) @BusinessModelField(name="自然日工期",searchable=false,sortable=false) private Integer durationDays;
    @TableField(exist=false) private boolean validatedInitial;
    @TableField(exist=false) @com.fasterxml.jackson.annotation.JsonIgnore
    private cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO currentRead;
}
