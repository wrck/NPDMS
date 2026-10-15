package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Read response: labels are joined from the existing product directory, never shipment columns. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceShipmentRespVO extends DeviceShipmentDO {
    private String productModel;
    private String productName;
    private String secondaryProductModel;
    private String secondaryProductName;
}
