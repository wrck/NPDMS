package cn.iocoder.yudao.module.pms.integration.extsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FP payment callback payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCallbackDto {

    /** Settlement number to update. */
    private String settlementNo;

    /** Payment status returned by FP (e.g. PAID, FAILED). */
    private String paymentStatus;

    /** Time the payment was processed. */
    private LocalDateTime paymentTime;

    /** Amount paid. */
    private BigDecimal paymentAmount;
}
