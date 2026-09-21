package com.dp.deviceops.adapter.web.parser.concurrency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("device-ops.parser.worker")
public class ParserWorkerProperties {
    private int coreSize = 2;
    private int maxSize = 8;
    private int queueCapacity = 32;
    private int claimBatchSize = 16;
    private int planCacheSize = 64;
    private Duration pollInterval = Duration.ofSeconds(1);
    private Duration lease = Duration.ofSeconds(30);
    private Duration heartbeatInterval = Duration.ofSeconds(10);
    private Duration recoveryInterval = Duration.ofSeconds(5);
    private int recoveryBatchSize = 100;
    private Duration shutdownGrace = Duration.ofSeconds(20);
    private Duration retryDelay = Duration.ofSeconds(5);

    public int coreSize() { return coreSize; }
    public int maxSize() { return maxSize; }
    public int queueCapacity() { return queueCapacity; }
    public int claimBatchSize() { return claimBatchSize; }
    public int planCacheSize() { return planCacheSize; }
    public Duration pollInterval() { return pollInterval; }
    public Duration lease() { return lease; }
    public Duration heartbeatInterval() { return heartbeatInterval; }
    public Duration recoveryInterval() { return recoveryInterval; }
    public int recoveryBatchSize() { return recoveryBatchSize; }
    public Duration shutdownGrace() { return shutdownGrace; }
    public Duration retryDelay() { return retryDelay; }
    public int outstandingCapacity() { return Math.addExact(maxSize, queueCapacity); }

    public void validate() {
        if (maxSize < coreSize) {
            throw new IllegalArgumentException("maxSize must be at least coreSize");
        }
        if (claimBatchSize > outstandingCapacity()) {
            throw new IllegalArgumentException("claimBatchSize must not exceed worker capacity");
        }
        if (lease.compareTo(heartbeatInterval.multipliedBy(3)) < 0) {
            throw new IllegalArgumentException("lease must be at least three heartbeat intervals");
        }
    }

    public void setCoreSize(int value) { coreSize = positive(value, "coreSize"); }
    public void setMaxSize(int value) { maxSize = positive(value, "maxSize"); }
    public void setQueueCapacity(int value) { queueCapacity = nonNegative(value, "queueCapacity"); }
    public void setClaimBatchSize(int value) { claimBatchSize = positive(value, "claimBatchSize"); }
    public void setPlanCacheSize(int value) { planCacheSize = positive(value, "planCacheSize"); }
    public void setPollInterval(Duration value) { pollInterval = positive(value, "pollInterval"); }
    public void setLease(Duration value) { lease = positive(value, "lease"); }
    public void setHeartbeatInterval(Duration value) { heartbeatInterval = positive(value, "heartbeatInterval"); }
    public void setRecoveryInterval(Duration value) { recoveryInterval = positive(value, "recoveryInterval"); }
    public void setRecoveryBatchSize(int value) { recoveryBatchSize = positive(value, "recoveryBatchSize"); }
    public void setShutdownGrace(Duration value) { shutdownGrace = nonNegative(value, "shutdownGrace"); }
    public void setRetryDelay(Duration value) { retryDelay = nonNegative(value, "retryDelay"); }

    private static int positive(int value, String field) {
        if (value < 1) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }
    private static int nonNegative(int value, String field) {
        if (value < 0) throw new IllegalArgumentException(field + " must not be negative");
        return value;
    }
    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
    private static Duration nonNegative(Duration value, String field) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }
}
