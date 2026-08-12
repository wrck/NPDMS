package cn.iocoder.yudao.module.pms.project.dal.dataobject.schedulebackward;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * PMS 工期倒排阶段明细 DO（FR-PROJ-018）。
 * <p>
 * 对应表 {@code pms_schedule_backward_item}，每条记录对应一个阶段在倒排后的计划日期与冲突信息。
 */
@TableName("pms_schedule_backward_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduleBackwardItemDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 倒排记录编号
     */
    private Long backwardId;
    /**
     * 项目阶段编号
     */
    private Long phaseId;
    /**
     * 阶段名称
     */
    private String phaseName;
    /**
     * 计划开始日期
     */
    private LocalDate plannedStartDate;
    /**
     * 计划结束日期
     */
    private LocalDate plannedEndDate;
    /**
     * 建议最晚日期
     */
    private LocalDate recommendedLatestDate;
    /**
     * 是否存在冲突
     */
    private Boolean hasConflict;
    /**
     * 冲突原因
     */
    private String conflictReason;
    /**
     * 阶段排序
     */
    private Integer sort;

}
