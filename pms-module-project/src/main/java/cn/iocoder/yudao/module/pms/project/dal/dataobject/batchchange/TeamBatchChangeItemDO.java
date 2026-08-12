package cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 团队批量变更明细 DO（FR-PROJ-014）。
 * <p>
 * 对应表 {@code pms_team_batch_change_item}，每条记录对应一次项目团队成员的变更结果。
 */
@TableName("pms_team_batch_change_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class TeamBatchChangeItemDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 批次编号
     */
    private Long batchId;
    /**
     * 项目编号
     */
    private Long projectId;
    /**
     * 项目名称（冗余）
     */
    private String projectName;
    /**
     * 团队成员编号
     */
    private Long teamMemberId;
    /**
     * 变更前角色编码
     */
    private String beforeRole;
    /**
     * 变更后角色编码
     */
    private String afterRole;
    /**
     * 状态：0待处理 1成功 2失败
     */
    private Integer status;
    /**
     * 失败原因
     */
    private String errorMessage;

}
