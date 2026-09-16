package cn.iocoder.yudao.module.pms.integration.extsystem.service;

import cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog;

/**
 * Integration retry service. Periodically retries failed integration calls and
 * supports manual retry by log id.
 */
public interface RetryService {

    /**
     * Scheduled retry job. Picks up failed logs whose next retry time has
     * elapsed, increments their retry count and re-dispatches to the matching
     * integration adapter. After {@code maxRetry} attempts a log is left as
     * permanently failed (no further scheduling).
     */
    void scheduledRetry();

    /**
     * Manually retry a single integration log by id, regardless of its current
     * retry count. Dispatches to the matching adapter based on {@code logType}.
     *
     * @param logId the integration log id
     * @return the refreshed log record after the retry attempt
     */
    IntegrationLog retryLog(Long logId);
}
