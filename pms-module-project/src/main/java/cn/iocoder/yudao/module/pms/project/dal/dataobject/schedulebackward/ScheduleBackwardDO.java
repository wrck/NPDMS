package cn.iocoder.yudao.module.pms.project.dal.dataobject.schedulebackward;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * PMS 工期倒排记录 DO（FR-PROJ-018）。
 * <p>
 * 对应表 {@code pms_schedule_backward}，承载一次工期倒排的目标日期、项目类型、状态与冲突汇总。
 * 阶段明细见 {@link ScheduleBackwardItemDO}。
 */
@TableName("pms_schedule_backward")
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduleBackwardDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 项目编号
     */
    private Long projectId;
    /**
     * 目标完工日期
     */
    private LocalDate targetDate;
    /**
     * 项目类型：DIRECT 直签 / INDIRECT 非直签
     */
    private String projectType;
    /**
     * 状态：0草稿 1已计算 2已应用 3已驳回
     */
    private Integer status;
    /**
     * 冲突汇总
     */
    private String conflictSummary;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

}
