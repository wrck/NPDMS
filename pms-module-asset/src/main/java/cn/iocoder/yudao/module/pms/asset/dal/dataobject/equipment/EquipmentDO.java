package cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PMS 设备档案 DO
 */
@TableName("pms_equipment")
@Data
@EqualsAndHashCode(callSuper = true)
public class EquipmentDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 全局唯一序列号
     */
    private String serialNumber;
    /**
     * 设备名称
     */
    private String name;
    /**
     * 设备型号
     */
    private String model;
    /**
     * 所属客户编号
     */
    private Long customerId;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 状态：0在库 1在用 2故障 3维修中 4已报废
     */
    private Integer status;
    /**
     * 设备位置
     */
    private String location;
    /** 当前站点。 */
    private Long siteId;
    /** 当前站点内部位置。 */
    private Long siteLocationId;
    /** UNRESOLVED / RESOLVED。 */
    private String locationResolutionStatus;
    /** 当前位置发生时快照。 */
    private String locationSnapshot;
    /** 当前位置生效时间。 */
    private LocalDateTime locationEffectiveFrom;
    /** 当前位置来源安装记录。 */
    private Long locationSourceInstallationId;
    /**
     * 保修开始日期
     */
    private LocalDate warrantyStartDate;
    /**
     * 保修结束日期
     */
    private LocalDate warrantyEndDate;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁
     */
    @Version
    private Integer version;

}
