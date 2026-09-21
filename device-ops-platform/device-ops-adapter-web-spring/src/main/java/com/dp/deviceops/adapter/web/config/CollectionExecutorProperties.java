package com.dp.deviceops.adapter.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("device-ops.executor")
public class CollectionExecutorProperties {
    private int coreSize = 2;
    private int maxSize = 8;
    private int queueCapacity = 100;
    private int shutdownAwaitSeconds = 30;
    private Duration shutdownGracefulPeriod = Duration.ofSeconds(15);
    private Duration shutdownRecoveryLease = Duration.ofSeconds(45);
    private int perConnectionLimit = 1;
    private Duration connectionWaitTimeout = Duration.ofMinutes(30);
    private Duration leaseHeartbeatInterval = Duration.ofSeconds(5);
    private Duration recoveryInterval = Duration.ofSeconds(30);
    private Duration recoveryUnclaimedGrace = Duration.ofMinutes(2);
    private int recoveryBatchSize = 100;
    public int coreSize() { return coreSize; }
    public int maxSize() { return maxSize; }
    public int queueCapacity() { return queueCapacity; }
    public int shutdownAwaitSeconds() { return shutdownAwaitSeconds; }
    public Duration shutdownGracefulPeriod() { return shutdownGracefulPeriod; }
    public Duration shutdownRecoveryLease() { return shutdownRecoveryLease; }
    public int perConnectionLimit() { return perConnectionLimit; }
    public Duration connectionWaitTimeout() { return connectionWaitTimeout; }
    public Duration leaseHeartbeatInterval() { return leaseHeartbeatInterval; }
    public Duration recoveryInterval() { return recoveryInterval; }
    public Duration recoveryUnclaimedGrace() { return recoveryUnclaimedGrace; }
    public int recoveryBatchSize() { return recoveryBatchSize; }
    public int outstandingCapacity() { return Math.addExact(maxSize, queueCapacity); }
    public void validate() {
        if (maxSize < coreSize) {
            throw new IllegalArgumentException("maxSize must be at least coreSize");
        }
        Duration totalAwait = Duration.ofSeconds(shutdownAwaitSeconds);
        if (shutdownGracefulPeriod.compareTo(totalAwait) > 0) {
            throw new IllegalArgumentException("shutdownGracefulPeriod must not exceed shutdownAwaitSeconds");
        }
        if (shutdownRecoveryLease.compareTo(leaseHeartbeatInterval.multipliedBy(3)) < 0) {
            throw new IllegalArgumentException(
                    "shutdownRecoveryLease must be at least three leaseHeartbeatIntervals");
        }
        Duration shutdownSafetyWindow = totalAwait.plus(leaseHeartbeatInterval.multipliedBy(2));
        if (shutdownRecoveryLease.compareTo(shutdownSafetyWindow) < 0) {
            throw new IllegalArgumentException(
                    "shutdownRecoveryLease must cover shutdownAwaitSeconds plus two leaseHeartbeatIntervals");
        }
    }
    public void setCoreSize(int value) { coreSize = positive(value, "coreSize"); }
    public void setMaxSize(int value) { maxSize = positive(value, "maxSize"); }
    public void setQueueCapacity(int value) { queueCapacity = nonNegative(value, "queueCapacity"); }
    public void setShutdownAwaitSeconds(int value) { shutdownAwaitSeconds = nonNegative(value, "shutdownAwaitSeconds"); }
    public void setShutdownGracefulPeriod(Duration value) { shutdownGracefulPeriod = nonNegative(value, "shutdownGracefulPeriod"); }
    public void setShutdownRecoveryLease(Duration value) { shutdownRecoveryLease = positive(value, "shutdownRecoveryLease"); }
    public void setPerConnectionLimit(int value) { perConnectionLimit = positive(value, "perConnectionLimit"); }
    public void setConnectionWaitTimeout(Duration value) { connectionWaitTimeout = positive(value, "connectionWaitTimeout"); }
    public void setLeaseHeartbeatInterval(Duration value) { leaseHeartbeatInterval = positive(value, "leaseHeartbeatInterval"); }
    public void setRecoveryInterval(Duration value) { recoveryInterval = positive(value, "recoveryInterval"); }
    public void setRecoveryUnclaimedGrace(Duration value) { recoveryUnclaimedGrace = positive(value, "recoveryUnclaimedGrace"); }
    public void setRecoveryBatchSize(int value) { recoveryBatchSize = positive(value, "recoveryBatchSize"); }
    private static int positive(int value, String field) { if (value < 1) throw new IllegalArgumentException(field + " must be positive"); return value; }
    private static Duration positive(Duration value, String field) { if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(field + " must be positive"); return value; }
    private static Duration nonNegative(Duration value, String field) { if (value == null || value.isNegative()) throw new IllegalArgumentException(field + " must not be negative"); return value; }
    private static int nonNegative(int value, String field) { if (value < 0) throw new IllegalArgumentException(field + " must not be negative"); return value; }
}
