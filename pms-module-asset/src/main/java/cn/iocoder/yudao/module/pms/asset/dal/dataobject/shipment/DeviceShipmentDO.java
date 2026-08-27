package cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("ast_device_shipment")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceShipmentDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String deviceSn;
    private LocalDateTime shipmentTime;
    private String packageNo;
    private String contractNo;
    private String eventType;
    private LocalDate warrantyStartDate;
    private Integer warrantyMonths;
    private String rmaNo;
    private String relatedDeviceSn;
    private String sourceSystem;
    private String sourceKey;
    private String sourceVersion;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime syncedAt;
    private String syncStatus;
    @Version
    private Integer version;
}
