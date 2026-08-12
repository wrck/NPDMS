package cn.iocoder.yudao.module.pms.project.dal.dataobject.maintenancetransition;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 转维保 DO
 * <p>
 * 状态机：0草稿 → 1待生效 → 2生效中 → 3已过期 → 4已续保
 * 维护期：基于验收时间和维保年限自动生成设备维护期
 */
@TableName("pms_acc_maintenance_transition")
@Data
@EqualsAndHashCode(callSuper = true)
public class MaintenanceTransitionDO extends TenantBaseDO {

    /**
     * 主键编号
     */
    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 转维保编码，项目内唯一
     */
    private String code;
    /**
     * 转维保名称
     */
    private String name;
    /**
     * 设备编号
     */
    private Long equipmentId;
    /**
     * 关联验收编号
     */
    private Long acceptanceId;
    /**
     * 维保年限（年）
     */
    private Integer maintenanceYears;
    /**
     * 维保开始日期
     */
    private LocalDate startDate;
    /**
     * 维保结束日期
     */
    private LocalDate endDate;
    /**
     * 生效操作人
     */
    private Long activateUserId;
    /**
     * 生效时间
     */
    private LocalDateTime activateTime;
    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
    /**
     * 续保年限（年）
     */
    private Integer renewYears;
    /**
     * 续保结束日期
     */
    private LocalDate renewEndDate;
    /**
     * 状态 0草稿 1待生效 2生效中 3已过期 4已续保
     */
    private Integer status;
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
