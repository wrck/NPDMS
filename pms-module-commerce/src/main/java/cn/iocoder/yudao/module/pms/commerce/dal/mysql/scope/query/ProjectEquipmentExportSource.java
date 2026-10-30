package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

import java.math.BigDecimal;

public record ProjectEquipmentExportSource(Long scopeId, Long orderLineId, String productCode,
        String productDesc, String productModel, String productName, String orderNo, BigDecimal allocatedQuantity,
        Long matchedLineId, String lineProductCode, BigDecimal deliveredQuantity, BigDecimal openQuantity) { }
