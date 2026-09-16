package cn.iocoder.yudao.module.pms.integration.extsystem.d365.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.iocoder.yudao.module.pms.integration.extsystem.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * D365 invoice entity. Tracks invoices synced from D365 and OCR results
 * returned by the FP OCR service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("int_d365_invoice")
public class D365Invoice extends BaseEntity {

    /** Invoice number. */
    private String invoiceNo;

    /** Related settlement number. */
    private String settlementNo;

    /** Invoice amount (excluding tax). */
    private BigDecimal amount;

    /** Tax amount. */
    private BigDecimal taxAmount;

    /** Total amount (including tax). */
    private BigDecimal totalAmount;

    /** Invoice date. */
    private LocalDateTime invoiceDate;

    /** Vendor name. */
    private String vendorName;

    /**
     * Push status to D365: PENDING, PUSHED, FAILED.
     */
    private String pushStatus;

    /** Timestamp of the last successful push to D365. */
    private LocalDateTime pushedAt;

    /** Identifier returned by D365 after a successful push. */
    private String d365InvoiceId;

    /**
     * OCR recognition status: PENDING, RECOGNIZED, FAILED.
     */
    private String ocrStatus;
}
