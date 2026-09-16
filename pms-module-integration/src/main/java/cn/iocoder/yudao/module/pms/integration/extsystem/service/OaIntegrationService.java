package cn.iocoder.yudao.module.pms.integration.extsystem.service;

import cn.iocoder.yudao.module.pms.integration.extsystem.dto.OaHealthDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.oa.OaTodoRequest;

/**
 * OA (致远 OA) integration service.
 *
 * <p>Provides operations to push pending todo items to OA when a Flowable
 * user task is created, complete the corresponding OA todo when the
 * Flowable task is completed, and transfer the handler when the task is
 * reassigned. The OAuth2 token is cached with ahead-of-expiry auto-renewal
 * (refreshed when less than 5 minutes remain). All calls are logged to
 * {@link cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog} with
 * {@code logType="OA"}.</p>
 */
public interface OaIntegrationService {

    /**
     * Obtain an OAuth2 access token (cached, auto-renewed when less than 5
     * minutes remain before expiry).
     *
     * @return the access token
     */
    String getAccessToken();

    /**
     * Push a todo item to OA.
     *
     * @param request the todo push request
     * @return {@code true} if the OA API returned a success status
     */
    boolean pushTodo(OaTodoRequest request);

    /**
     * Complete a todo item in OA.
     *
     * @param businessKey the OA todo business key (the Flowable task id /
     *                    process business key)
     * @return {@code true} if the OA API returned a success status
     */
    boolean completeTodo(String businessKey);

    /**
     * Transfer an OA todo to a new handler (sync a handler change to OA).
     *
     * @param businessKey      the OA todo business key
     * @param newHandlerUserId the new handler user id in OA
     * @return {@code true} if the OA API returned a success status
     */
    boolean transferTask(String businessKey, String newHandlerUserId);

    /**
     * Retry a previously logged OA call by log id. Re-dispatches the stored
     * request body to the stored request URL and updates the log status.
     *
     * <p>Called by {@link cn.iocoder.yudao.module.pms.integration.extsystem.service.RetryService#scheduledRetry()}
     * (the scheduled retry job) and by manual retry endpoints. Unlike the
     * primary {@code pushTodo}/{@code completeTodo}/{@code transferTask}
     * methods, this method is NOT annotated with {@code @Retry}/
     * {@code @CircuitBreaker}/{@code @Bulkhead} to avoid stacking an
     * additional Resilience4j retry layer on top of the scheduler-driven retry.</p>
     *
     * @param logId the integration log id
     * @return the refreshed log record after the retry attempt
     */
    IntegrationLog retry(Long logId);

    /**
     * Health check for the OA adapter.
     *
     * @return the OA health snapshot
     */
    OaHealthDto healthCheck();
}
