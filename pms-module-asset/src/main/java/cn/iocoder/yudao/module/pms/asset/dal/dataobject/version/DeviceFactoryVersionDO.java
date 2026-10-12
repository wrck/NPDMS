package cn.iocoder.yudao.module.pms.asset.dal.dataobject.version;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.*;
import java.math.BigDecimal;
@TableName("ast_device_factory_version")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceFactoryVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private String deviceSn;
    private String conpVersion;
    private String conpType;
    private String conpSeries;
    private String conpMark;
    private String bootVersion;
    private String cpldVersion;
    private String pcbVersion;
    private Boolean customized;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime syncedAt;
    private String syncStatus;
    private Integer version;
}
