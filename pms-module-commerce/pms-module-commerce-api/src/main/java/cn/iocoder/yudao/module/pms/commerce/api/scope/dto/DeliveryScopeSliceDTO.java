package cn.iocoder.yudao.module.pms.commerce.api.scope.dto;

import java.math.BigDecimal;

public record DeliveryScopeSliceDTO(Long orderLineId, BigDecimal availableQuantity, String unitCode,
                                    Long scopeVersion, String quantityStatus) {
}
