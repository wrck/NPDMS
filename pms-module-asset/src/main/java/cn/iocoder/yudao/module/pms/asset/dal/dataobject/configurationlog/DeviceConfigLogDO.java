package cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AST 设备配置日志 DO（ast_device_config_log，FR-RES-003，自 pms_equipment_config_log 承接）
 */
@TableName("ast_device_config_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceConfigLogDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 设备编号（ast_device.id） */
    private Long deviceId;
    /** 配置类型 */
    private String configType;
    /** 配置内容 */
    private String configContent;
    /** 来源系统 */
    private String sourceSystem;
    /** 采集时间 */
    private LocalDateTime collectedAt;
    /** 配置文件URL */
    private String fileUrl;
    /** 配置文件哈希 */
    private String fileHash;
    /** 备注 */
    private String remark;
    @Version
    private Integer version;
}
