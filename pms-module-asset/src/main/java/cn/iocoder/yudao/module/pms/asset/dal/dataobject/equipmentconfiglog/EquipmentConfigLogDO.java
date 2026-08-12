package cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipmentconfiglog;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 设备配置日志 DO（FR-RES-003）
 */
@TableName("pms_equipment_config_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class EquipmentConfigLogDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 设备编号
     */
    private Long equipmentId;
    /**
     * 配置类型
     */
    private String configType;
    /**
     * 配置内容
     */
    private String configContent;
    /**
     * 来源系统
     */
    private String sourceSystem;
    /**
     * 采集时间
     */
    private LocalDateTime collectedAt;
    /**
     * 配置文件URL
     */
    private String fileUrl;
    /**
     * 配置文件哈希
     */
    private String fileHash;
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
