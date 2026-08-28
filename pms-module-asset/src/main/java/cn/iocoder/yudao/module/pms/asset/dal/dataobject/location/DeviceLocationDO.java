package cn.iocoder.yudao.module.pms.asset.dal.dataobject.location;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("ast_device_location")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceLocationDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String deviceSn;
    private Long siteId;
    private Long siteLocationId;
    private String resolutionStatus;
    private String locationSnapshot;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Long installationId;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private Integer version;
}
