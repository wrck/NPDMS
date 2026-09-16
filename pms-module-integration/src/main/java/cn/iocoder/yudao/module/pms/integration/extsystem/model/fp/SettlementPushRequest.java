package cn.iocoder.yudao.module.pms.integration.extsystem.model.fp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * FP settlement push request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementPushRequest {

    private String settlementNo;

    private String agentName;

    private BigDecimal totalAmount;

    private BigDecimal taxAmount;

    private BigDecimal totalWithTax;

    private List<SettlementPushDetail> details;
}
