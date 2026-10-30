package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 阶段施工计划明细 DO（PLN-01/04）。
 * <p>
 * 对应表 {@code sol_stage_plan_item}；批次内明细随批次整体重算/调整，不单独版本化。
 */
@TableName("sol_stage_plan_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class StagePlanItemDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 批次 ID
     */
    private Long batchId;
    /**
     * 项目阶段 ID（新域 proj_project_stage，经 ProjectStagePlanApi 提供；旧表 pms_project_phase_retired 已退役）
     */
    private Long phaseId;
    /**
     * 阶段编码快照
     */
    private String phaseCode;
    /**
     * 阶段名称快照
     */
    private String phaseName;
    /**
     * 阶段顺序
     */
    private Integer sort;
    /**
     * 建议开始时间快照
     */
    private LocalDate suggestedStart;
    /**
     * 建议结束时间快照
     */
    private LocalDate suggestedEnd;
    /**
     * 计划开始时间
     */
    private LocalDate planStart;
    /**
     * 计划结束时间
     */
    private LocalDate planEnd;
    /**
     * 备注
     */
    private String remark;

}
