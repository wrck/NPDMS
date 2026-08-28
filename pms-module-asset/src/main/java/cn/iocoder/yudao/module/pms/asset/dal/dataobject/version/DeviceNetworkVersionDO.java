package cn.iocoder.yudao.module.pms.asset.dal.dataobject.version;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("ast_device_network_version")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceNetworkVersionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String deviceSn;
    private String conpVersion;
    private String conpType;
    private String conpSeries;
    private String conpMark;
    private String bootVersion;
    private String cpldVersion;
    private String pcbVersion;
    private Boolean customized;
    private LocalDateTime effectiveFrom;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime syncedAt;
    private String syncStatus;
    @Version
    private Integer version;
}
