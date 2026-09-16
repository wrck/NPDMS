package cn.iocoder.yudao.module.pms.integration.extsystem.model.d365;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * D365 purchase order header.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseHeader {

    private String purchaseOrderId;

    private String vendorAccountNumber;

    private List<PurchaseLine> lines;
}
