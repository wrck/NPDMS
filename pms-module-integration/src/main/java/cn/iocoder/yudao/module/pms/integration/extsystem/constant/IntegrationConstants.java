package cn.iocoder.yudao.module.pms.integration.extsystem.constant;

/**
 * Constants for the integration module: log types, business types and response
 * statuses.
 */
public final class IntegrationConstants {

    private IntegrationConstants() {
    }

    /** Log type: Microsoft Dynamics 365. */
    public static final String LOG_TYPE_D365 = "D365";
    /** Log type: Financial Platform. */
    public static final String LOG_TYPE_FP = "FP";
    /** Log type: OA system. */
    public static final String LOG_TYPE_OA = "OA";
    /** Log type: SMS system. */
    public static final String LOG_TYPE_SMS = "SMS";
    /** Log type: EHR system. */
    public static final String LOG_TYPE_EHR = "EHR";

    /** Business type: D365 purchase order push. */
    public static final String BIZ_PURCHASE_ORDER = "PURCHASE_ORDER";
    /** Business type: D365 purchase receipt push. */
    public static final String BIZ_PURCHASE_RECEIPT = "PURCHASE_RECEIPT";
    /** Business type: FP settlement push. */
    public static final String BIZ_SETTLEMENT = "SETTLEMENT";
    /** Business type: invoice. */
    public static final String BIZ_INVOICE = "INVOICE";
    /** Business type: OA todo push. */
    public static final String BIZ_OA_TODO_PUSH = "TODO_PUSH";
    /** Business type: OA todo complete. */
    public static final String BIZ_OA_TODO_COMPLETE = "TODO_COMPLETE";
    /** Business type: OA todo transfer (handler change). */
    public static final String BIZ_OA_TODO_TRANSFER = "TODO_TRANSFER";
    /** Business type: FP invoice OCR. */
    public static final String BIZ_OCR_INVOICE = "OCR_INVOICE";
    /** Business type: FP payment callback. */
    public static final String BIZ_PAYMENT_CALLBACK = "PAYMENT_CALLBACK";

    /** Response status: success. */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** Response status: failed. */
    public static final String STATUS_FAILED = "FAILED";
    /** Response status: pending. */
    public static final String STATUS_PENDING = "PENDING";

    /** Push status: pushed to external system. */
    public static final String PUSH_STATUS_PUSHED = "PUSHED";
    /** Push status: pending push to external system. */
    public static final String PUSH_STATUS_PENDING = "PENDING";
    /** Push status: push to external system failed. */
    public static final String PUSH_STATUS_FAILED = "FAILED";

    /** OCR status: recognized. */
    public static final String OCR_STATUS_RECOGNIZED = "RECOGNIZED";
    /** OCR status: pending recognition. */
    public static final String OCR_STATUS_PENDING = "PENDING";
    /** OCR status: recognition failed. */
    public static final String OCR_STATUS_FAILED = "FAILED";
}
