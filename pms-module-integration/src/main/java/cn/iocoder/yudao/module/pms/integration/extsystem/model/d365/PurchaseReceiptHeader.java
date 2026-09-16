package cn.iocoder.yudao.module.pms.integration.extsystem.model.d365;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * D365 purchase receipt header.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseReceiptHeader {

    private String purchaseOrderId;

    private LocalDate receiptDate;

    private List<PurchaseReceiptLine> lines;
}
