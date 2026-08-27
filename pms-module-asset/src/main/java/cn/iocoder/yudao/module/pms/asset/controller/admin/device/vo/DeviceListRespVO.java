package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DeviceListRespVO(
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
