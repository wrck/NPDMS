package cn.iocoder.yudao.module.pms.integration.extsystem.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog;

import java.util.List;

/**
 * Service for {@link IntegrationLog}.
 */
public interface IIntegrationLogService extends IService<IntegrationLog> {

    /**
     * Save a log record.
     *
     * @param log the log to save
     * @return the saved log with id populated
     */
    IntegrationLog log(IntegrationLog log);

    /**
     * Mark a log as success with the response body.
     *
     * @param logId        the log id
     * @param responseBody the response body
     */
    void markSuccess(Long logId, String responseBody);

    /**
     * Mark a log as failed with the error message and schedule the next retry
     * using exponential backoff.
     *
     * @param logId        the log id
     * @param errorMessage the error message
     */
    void markFailed(Long logId, String errorMessage);

    /**
     * Get logs that need retry: status=FAILED, retryCount &lt; maxRetry and
     * nextRetryTime &lt;= now.
     *
     * @return list of logs pending retry
     */
    List<IntegrationLog> getPendingRetryLogs();

    /**
     * Increment the retry count of a log.
     *
     * @param logId the log id
     */
    void incrementRetryCount(Long logId);

    /**
     * Paginated integration log query with optional filters (logType,
     * businessType, businessId, responseStatus).
     *
     * @param page    page number (1-based)
     * @param size    page size
     * @param filters filter bean
     * @return the page of logs
     */
    Page<IntegrationLog> list(int page, int size, IntegrationLog filters);
}
