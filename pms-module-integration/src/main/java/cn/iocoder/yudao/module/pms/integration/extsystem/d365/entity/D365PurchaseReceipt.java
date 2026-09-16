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
 * D365 purchase receipt entity. Tracks receipts synced from D365 (and pushed
 * to D365 from local), including the push lifecycle status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("int_d365_purchase_receipt")
public class D365PurchaseReceipt extends BaseEntity {

    /** Local receipt number. */
    private String receiptNo;

    /** Related purchase order number. */
    private String poNo;

    /** Related asset id. */
    private Long assetId;

    /** Asset serial number recorded on the receipt. */
    private String sn;

    /** Received quantity. */
    private BigDecimal quantity;

    /** Date the goods were received. */
    private LocalDateTime receivedDate;

    /**
     * Push status to D365: PENDING, PUSHED, FAILED.
     */
    private String pushStatus;

    /** Timestamp of the last successful push to D365. */
    private LocalDateTime pushedAt;

    /** Identifier returned by D365 after a successful push. */
    private String d365ReceiptId;
}
