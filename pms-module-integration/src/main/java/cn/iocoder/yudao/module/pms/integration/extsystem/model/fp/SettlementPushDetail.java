package cn.iocoder.yudao.module.pms.integration.extsystem.model.fp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * FP settlement push detail (line item).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementPushDetail {

    private String itemName;

    private BigDecimal workQuantity;

    private String unit;

    private BigDecimal unitPrice;

    private BigDecimal amount;
}
