package cn.iocoder.yudao.module.pms.asset.dal.dataobject.location;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("ast_location_source_mapping")
@Data
@EqualsAndHashCode(callSuper = true)
public class LocationSourceMappingDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String sourceSystem;
    private String objectType;
    private String sourceKey;
    private String sourceVersion;
    private Long addressId;
    private Long siteId;
    private Long siteLocationId;
    private String syncWatermark;
    private String matchStatus;
    private String locationResolutionStatus;
    private LocalDateTime lastSyncedAt;
    private Integer version;

}
