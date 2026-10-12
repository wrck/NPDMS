package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.associated;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@TableName("com_shipment_package")
@Data
@EqualsAndHashCode(callSuper=true)
public class ShipmentPackageDO extends TenantBaseDO {
    @TableId private Long id;
    private Long shipmentContractRefId;
    private String packageNo;
    private java.time.LocalDateTime shipmentTime;
    private java.time.LocalDateTime warrantyStartTime;
    private java.time.LocalDateTime warrantyEndTime;
    private String receiverName;
    private String expressNo;
    private String carrierName;
    private String mappingStatus;
    private String sourceSystem;
    private String sourceRecordKey;
    private java.time.LocalDateTime sourceSyncTime;
    private String carrierCode;
}
