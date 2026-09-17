package cn.iocoder.yudao.module.pms.integration.extsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.iocoder.yudao.module.pms.integration.extsystem.config.IntegrationProperties;
import cn.iocoder.yudao.module.pms.integration.extsystem.constant.IntegrationConstants;
import cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog;
import cn.iocoder.yudao.module.pms.integration.extsystem.mapper.IntegrationLogMapper;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.IIntegrationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementation of {@link IIntegrationLogService}.
 */
@Service
@RequiredArgsConstructor
public class IntegrationLogServiceImpl
        extends ServiceImpl<IntegrationLogMapper, IntegrationLog>
        implements IIntegrationLogService {

    /** Truncate error messages to the column size. */
    private static final int MAX_ERROR_LENGTH = 1000;

    private final IntegrationProperties integrationProperties;

    @Override
    public IntegrationLog log(IntegrationLog integrationLog) {
        if (integrationLog.getRetryCount() == null) {
            integrationLog.setRetryCount(0);
        }
        if (integrationLog.getMaxRetry() == null) {
            integrationLog.setMaxRetry(integrationProperties.getMaxRetry());
        }
        if (integrationLog.getResponseStatus() == null) {
            integrationLog.setResponseStatus(IntegrationConstants.STATUS_PENDING);
        }
        this.save(integrationLog);
        return integrationLog;
    }

    @Override
    public void markSuccess(Long logId, String responseBody) {
        IntegrationLog log = this.getById(logId);
        if (log == null) {
            return;
        }
        log.setResponseStatus(IntegrationConstants.STATUS_SUCCESS);
        log.setResponseBody(responseBody);
        log.setErrorMessage(null);
        log.setNextRetryTime(null);
        baseMapper.updateDeliveryOutcome(log);
    }

    @Override
    public void markFailed(Long logId, String errorMessage) {
        IntegrationLog log = this.getById(logId);
        if (log == null) {
            return;
        }
        log.setResponseStatus(IntegrationConstants.STATUS_FAILED);
        log.setErrorMessage(truncate(errorMessage));
        int maxRetry = log.getMaxRetry() == null ? integrationProperties.getMaxRetry() : log.getMaxRetry();
        Integer retryCount = log.getRetryCount() == null ? 0 : log.getRetryCount();
        if (retryCount >= maxRetry) {
            // Reached max retry: stop scheduling (permanently failed).
            log.setNextRetryTime(null);
        } else {
            // Exponential backoff: (retryCount + 1) * backoffMultiplier minutes.
            long delayMinutes = (long) (retryCount + 1) * integrationProperties.getBackoffMultiplier();
            log.setNextRetryTime(LocalDateTime.now().plusMinutes(delayMinutes));
        }
        baseMapper.updateDeliveryOutcome(log);
    }

    @Override
    public List<IntegrationLog> getPendingRetryLogs() {
        LambdaQueryWrapper<IntegrationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntegrationLog::getResponseStatus, IntegrationConstants.STATUS_FAILED)
                // retry_count must be below this log's own max_retry column.
                .apply("retry_count < max_retry")
                .isNotNull(IntegrationLog::getNextRetryTime)
                .le(IntegrationLog::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(IntegrationLog::getNextRetryTime);
        return this.list(wrapper);
    }

    @Override
    public void incrementRetryCount(Long logId) {
        IntegrationLog log = this.getById(logId);
        if (log == null) {
            return;
        }
        Integer retryCount = log.getRetryCount() == null ? 0 : log.getRetryCount();
        log.setRetryCount(retryCount + 1);
        this.updateById(log);
    }

    @Override
    public Page<IntegrationLog> list(int page, int size, IntegrationLog filters) {
        LambdaQueryWrapper<IntegrationLog> wrapper = new LambdaQueryWrapper<>();
        if (filters != null) {
            wrapper.eq(filters.getLogType() != null, IntegrationLog::getLogType, filters.getLogType())
                    .eq(filters.getBusinessType() != null, IntegrationLog::getBusinessType, filters.getBusinessType())
                    .eq(filters.getResponseStatus() != null, IntegrationLog::getResponseStatus, filters.getResponseStatus())
                    .like(filters.getBusinessId() != null, IntegrationLog::getBusinessId, filters.getBusinessId());
        }
        wrapper.orderByDesc(IntegrationLog::getCreateTime);
        return this.page(new Page<>(page, size), wrapper);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
