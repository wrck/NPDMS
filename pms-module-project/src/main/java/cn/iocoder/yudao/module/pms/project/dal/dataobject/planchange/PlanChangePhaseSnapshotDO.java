package cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 计划变更阶段快照 DO（FR-PROJ-020 / T-V2-PROJ-003）
 * <p>
 * 记录变更前后阶段计划时间，便于版本回溯和差异比较
 */
@TableName("pms_plan_change_phase_snapshot")
@Data
@EqualsAndHashCode(callSuper = true)
public class PlanChangePhaseSnapshotDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 变更申请编号
     */
    private Long changeRequestId;
    /**
     * 项目阶段编号
     */
    private Long phaseId;
    /**
     * 阶段名称（冗余）
     */
    private String phaseName;
    /**
     * 变更前计划开始时间
     */
    private LocalDateTime beforePlanStart;
    /**
     * 变更前计划结束时间
     */
    private LocalDateTime beforePlanEnd;
    /**
     * 变更后计划开始时间
     */
    private LocalDateTime afterPlanStart;
    /**
     * 变更后计划结束时间
     */
    private LocalDateTime afterPlanEnd;
    /**
     * 阶段变更说明
     */
    private String changeRemark;

}
