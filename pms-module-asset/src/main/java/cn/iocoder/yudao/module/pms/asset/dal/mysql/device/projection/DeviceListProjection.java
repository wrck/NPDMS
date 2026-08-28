package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DeviceListProjection(
        Long deviceId,
        String sn,
        String productCode,
        String productModel,
        String productName,
        LocalDateTime shipmentTime,
        String packageNo,
        String contractNo,
        Long shipmentRecordId,
        Long projectId,
        Long customerId,
        LocalDate warrantyStartDate,
        LocalDate warrantyEndDate,
        String warrantyStatus,
        String conpVersion,
        String conpType,
        String conpSeries,
        String conpMark,
        String syncStatus) {
}
