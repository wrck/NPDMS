package cn.iocoder.yudao.module.pms.commerce.controller.admin.scope.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DeliveryScopeRespVO(
        Long id, Long projectId, String projectCode, Long orderLineId, String orderNo, String lineNo,
        String itemCode, BigDecimal allocatedQuantity, String scopeStatus, Long allocationVersion,
        String allocationSource, String changeReason, Long officeDepartmentId, String officeDepartmentCode,
        String officeDepartmentName, Integer officeDepartmentVersion, LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo, Integer version, List<Detail> details) {

    public record Detail(Long id, Integer sequence, String serialNo, String productCode, String deviceTypeCode,
                         BigDecimal allocatedQuantity, String status) {
    }
}
