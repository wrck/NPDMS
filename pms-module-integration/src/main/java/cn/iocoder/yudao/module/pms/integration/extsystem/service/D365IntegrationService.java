package cn.iocoder.yudao.module.pms.integration.extsystem.service;

import cn.iocoder.yudao.module.pms.integration.extsystem.dto.D365HealthDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.d365.PurchaseHeader;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.d365.PurchaseReceiptHeader;

/**
 * D365 (Microsoft Dynamics 365) integration service.
 *
 * <p>All methods log to {@link IntegrationLog} and support automatic retry.</p>
 */
public interface D365IntegrationService {

    /**
     * Obtain an OAuth2 access token (cached until near expiry). Maintains a
     * consecutive-failure counter; after 3 consecutive token failures an
     * {@code error} log is emitted.
     *
     * @return the access token
     */
    String getAccessToken();

    /**
     * Push a purchase receipt to D365.
     *
     * @param header the purchase receipt header
     * @return the D365 response body
     */
    String pushPurchaseReceipt(PurchaseReceiptHeader header);

    /**
     * Push a purchase order to D365.
     *
     * @param header the purchase order header
     * @return the D365 response body
     */
    String pushPurchaseOrder(PurchaseHeader header);

    /**
     * Retry a previously logged D365 call by log id. Re-posts the stored
     * request body to the stored request url and updates the log status.
     *
     * @param logId the integration log id
     * @return the refreshed log record
     */
    IntegrationLog retry(Long logId);

    /**
     * Sync purchase orders from D365 into the local table (upsert).
     *
     * @return the number of records synced
     */
    int syncPurchaseOrders();

    /**
     * Sync purchase receipts from D365, backfilling receipt data and setting
     * {@code push_status} on the corresponding local records.
     *
     * @return the number of records synced
     */
    int syncPurchaseReceipts();

    /**
     * Sync asset serial numbers from D365 and update the local
     * {@code pms_asset.serial_no}. The {@code AssetMapper} is looked up
     * reflectively at runtime to avoid a module dependency cycle.
     *
     * @return the number of records synced
     */
    int syncAssetSerialNumbers();

    /**
     * Sync invoices from D365 and update the local settlement invoice number.
     * The {@code SettlementMapper} is looked up reflectively at runtime to
     * avoid a module dependency cycle.
     *
     * @return the number of records synced
     */
    int syncInvoices();

    /**
     * Health check for the D365 adapter: token validity, reachability and
     * recent push/fail counts.
     *
     * @return the D365 health snapshot
     */
    D365HealthDto healthCheck();
}
