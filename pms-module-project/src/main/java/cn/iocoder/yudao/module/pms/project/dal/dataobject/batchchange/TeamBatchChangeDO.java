package cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 团队批量变更批次 DO（FR-PROJ-014）。
 * <p>
 * 对应表 {@code pms_team_batch_change}，承载一次批量角色移交的批次元数据与汇总结果。
 * 明细记录见 {@link TeamBatchChangeItemDO}。
 */
@TableName("pms_team_batch_change")
@Data
@EqualsAndHashCode(callSuper = true)
public class TeamBatchChangeDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 批次编号，全局唯一
     */
    private String batchNo;
    /**
     * 源用户编号
     */
    private Long sourceUserId;
    /**
     * 目标用户编号
     */
    private Long targetUserId;
    /**
     * 范围类型：ALL 全部项目 / SELECTED 指定项目
     */
    private String scopeType;
    /**
     * 变更原因
     */
    private String reason;
    /**
     * 状态：0处理中 1成功 2部分成功 3失败
     */
    private Integer status;
    /**
     * 总条数
     */
    private Integer totalCount;
    /**
     * 成功条数
     */
    private Integer successCount;
    /**
     * 失败条数
     */
    private Integer failureCount;
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
