package cn.iocoder.yudao.module.pms.integration.extsystem.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.iocoder.yudao.module.pms.integration.extsystem.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Integration log entity. Tracks every call to an external system (D365, FP,
 * OA, SMS, EHR) and supports retry with exponential backoff.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("int_log")
public class IntegrationLog extends BaseEntity {

    /** External system type: D365, FP, OA, SMS, EHR. */
    private String logType;

    /** Business type: PURCHASE_RECEIPT, PURCHASE_ORDER, SETTLEMENT, INVOICE, etc. */
    private String businessType;

    /** Related business record id. */
    private String businessId;

    /** Request URL. */
    private String requestUrl;

    /** Request body (JSON). */
    private String requestBody;

    /** Response status: SUCCESS, FAILED, PENDING. */
    private String responseStatus;

    /** Response body (JSON). */
    private String responseBody;

    /** Error message when failed. */
    private String errorMessage;

    /** Current retry count. */
    private Integer retryCount;

    /** Max retry times. */
    private Integer maxRetry;

    /** Next retry time. */
    private LocalDateTime nextRetryTime;
}
