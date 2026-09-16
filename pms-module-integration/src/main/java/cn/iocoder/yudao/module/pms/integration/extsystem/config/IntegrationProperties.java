package cn.iocoder.yudao.module.pms.integration.extsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Integration retry properties. Bound from the {@code integration.retry.*}
 * prefix in {@code application.yml}.
 */
@Data
@ConfigurationProperties(prefix = "integration.retry")
public class IntegrationProperties {

    /** Retry scheduler fixed delay in milliseconds (default 5 minutes). */
    private long interval = 300000L;

    /** Max retry times (default 3). */
    private int maxRetry = 3;

    /** Exponential backoff multiplier in minutes (default 2). */
    private int backoffMultiplier = 2;
}
