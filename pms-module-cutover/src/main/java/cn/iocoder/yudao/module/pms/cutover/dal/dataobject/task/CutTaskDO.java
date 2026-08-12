package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 割接任务 DO（FR-CUT-001 / FR-CUT-002 / FR-CUT-003 / FR-CUT-006）。
 * <p>
 * 对应表 {@code pms_cut_task}，承载割接任务主流程与状态机。
 * 割接任务编码 {@code code} 在项目内唯一。
 */
@TableName("pms_cut_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutTaskDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long projectId;
    private String code;
    private String name;
    private String cutoverType;
    private String networkMode;
    private String sourceType;
    private Long sourceId;
    private String riskLevel;
    private LocalDateTime scheduledTime;
    private LocalDateTime actualTime;
    private Integer status;
    private String approvalOpinion;
    private String remark;
    @Version
    private Integer version;
}
