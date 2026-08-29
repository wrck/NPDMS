package cn.iocoder.yudao.module.pms.commerce.controller.admin.order.vo;

import java.math.BigDecimal;

public record SalesOrderLineRespVO(
        Long id, Long orderId, String sourceSystem, String sourceVersion, String companyCode,
        String orderType, String orderNo, String lineNo, String itemCode, String itemDesc,
        String productCode, BigDecimal orderQty, BigDecimal openQty, BigDecimal deliveredQty,
        String unitCode, Integer unitScale, String quantityStatus, String status, Integer version) {
}
