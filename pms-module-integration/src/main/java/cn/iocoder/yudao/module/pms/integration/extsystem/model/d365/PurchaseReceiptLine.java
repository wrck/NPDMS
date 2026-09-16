package cn.iocoder.yudao.module.pms.integration.extsystem.model.d365;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * D365 purchase receipt line.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseReceiptLine {

    private String lineNumber;

    private String itemId;

    private BigDecimal quantityReceived;
}
