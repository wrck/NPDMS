package cn.iocoder.yudao.module.pms.integration.extsystem.model.d365;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * D365 purchase order line.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseLine {

    private String lineNumber;

    private String itemId;

    private BigDecimal quantity;

    private BigDecimal unitPrice;
}
