package cn.iocoder.yudao.module.pms.asset.dal.dataobject.version;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("ast_device_network_version_event")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceNetworkVersionEventDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String deviceSn;
    private String sourceDeviceKey;
    private String sourceEventKey;
    private String conpVersion;
    private String conpType;
    private String conpSeries;
    private String conpMark;
    private String bootVersion;
    private String cpldVersion;
    private String pcbVersion;
    private Boolean customized;
    private LocalDateTime eventTime;
    private LocalDateTime receivedTime;
    private Boolean revoked;
    private String mappingStatus;
    private String sourceSystem;
    private String sourceVersion;
    private String syncStatus;
}
