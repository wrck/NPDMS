package cn.iocoder.yudao.module.pms.integration.extsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Parsed FP OCR result for an invoice image.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceOcrResult {

    /** Invoice number recognized by FP OCR. */
    private String invoiceNo;

    /** Invoice amount (excluding tax). */
    private BigDecimal amount;

    /** Tax amount. */
    private BigDecimal taxAmount;

    /** Total amount (including tax). */
    private BigDecimal totalAmount;

    /** Vendor name. */
    private String vendorName;

    /** Raw OCR response body. */
    private String rawResponse;
}
