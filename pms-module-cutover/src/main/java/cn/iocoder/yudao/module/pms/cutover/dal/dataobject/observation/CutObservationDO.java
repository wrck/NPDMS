package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.observation;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 稳定观察与遗留项 DO（FR-CUT-013 / FR-CUT-014）。
 * <p>
 * 对应表 {@code pms_cut_observation}，承载割接后稳定观察、遗留项跟踪与归档。
 */
@TableName("pms_cut_observation")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutObservationDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long taskId;
    private String code;
    private LocalDateTime observationStart;
    private LocalDateTime observationEnd;
    private Long observerUserId;
    private String leftoverItems;
    private Integer leftoverStatus;
    private String conclusion;
    private Integer status;
    private String remark;
    @Version
    private Integer version;
}
