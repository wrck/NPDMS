package cn.iocoder.yudao.module.pms.service.dal.dataobject.srvmaintenance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 维保状态 DO
 */
@TableName("pms_srv_maintenance")
@Data
@EqualsAndHashCode(callSuper = true)
public class SrvMaintenanceDO extends TenantBaseDO {

    /**
     * 维保记录编号
     */
    @TableId
    private Long id;
    /**
     * 设备编号
     */
    private Long equipmentId;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 维保记录编码，设备内唯一
     */
    private String code;
    /**
     * 维保开始日期
     */
    private LocalDate startDate;
    /**
     * 维保结束日期
     */
    private LocalDate endDate;
    /**
     * 维保状态
     *
     * 枚举 0未生效 1生效中 2即将过期 3已过期 4已续保
     */
    private Integer maintenanceStatus;
    /**
     * 服务等级
     */
    private String serviceLevel;
    /**
     * 是否自动计算
     */
    private Boolean autoCalculated;
    /**
     * 是否手工覆盖
     */
    private Boolean manualOverride;
    /**
     * 覆盖人
     */
    private Long overrideBy;
    /**
     * 覆盖时间
     */
    private LocalDateTime overrideTime;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁版本号
     */
    private Integer version;

}
