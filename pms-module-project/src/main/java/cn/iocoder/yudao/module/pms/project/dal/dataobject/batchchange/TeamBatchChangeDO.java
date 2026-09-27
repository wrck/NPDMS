package cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

/**
 * PMS 团队批量变更批次 DO（FR-PROJ-014）。
 * <p>
 * 对应表 {@code proj_team_batch_change}，承载一次批量角色移交的批次元数据与汇总结果。
 * 明细记录见 {@link TeamBatchChangeItemDO}。
 */
@TableName("proj_team_batch_change")
@Data
@EqualsAndHashCode(callSuper = true)
public class TeamBatchChangeDO extends BaseBusinessEntity {

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
}
