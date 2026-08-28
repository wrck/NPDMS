package cn.iocoder.yudao.module.pms.asset.api.device.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DeviceSummaryDTO(
        Long deviceId,
        Long tenantId,
        String sn,
        String productCode,
        String productModel,
        String productName,
        LocalDateTime shipmentTime,
        String packageNo,
        String contractNo,
        Long shipmentRecordId,
        Long projectId,
        Long projectAssignmentVersion,
        Long customerId,
        Long customerAssignmentVersion,
        LocalDate warrantyStartDate,
        LocalDate warrantyEndDate,
        String warrantyStatus,
        String conpVersion,
        String conpType,
        String conpSeries,
        String conpMark) {
}
