package com.dp.deviceops.core.port;

import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.core.port.CommandExecutionPort.OutputStreamType;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Persistence boundary for one claimed target execution; credentials never cross this boundary. */
public interface CollectionExecutionPersistencePort {
    boolean claim(long targetId, String workerId, Instant startedAt, Instant leaseUntil);
    /**
     * Claims work with a short pending-stage recovery lease. Deadline-aware JDBC
     * implementations should calculate the lease after acquiring their connection,
     * so slow admission cannot persist an already-expired lease.
     */
    default boolean claimPending(
            long targetId,
            String workerId,
            Instant startedAt,
            Duration leaseDuration) {
        return claim(targetId, workerId, startedAt, startedAt.plus(leaseDuration));
    }
    void renewLease(long targetId, String workerId, Instant leaseUntil);
    /** Extends a pending-stage lease but must never shorten a longer active lease. */
    default void renewPendingLease(long targetId, String workerId, Instant leaseUntil) {
        renewLease(targetId, workerId, leaseUntil);
    }
    /**
     * Extends one heartbeat batch. Production implementations should use bounded
     * batch I/O; returned ids no longer belong to the supplied owner.
     */
    default List<Long> renewPendingLeases(List<ClaimedTarget> targets, Instant leaseUntil) {
        java.util.ArrayList<Long> leaseLost = new java.util.ArrayList<>();
        for (ClaimedTarget target : targets) {
            try {
                renewPendingLease(target.targetId(), target.workerId(), leaseUntil);
            } catch (com.dp.deviceops.core.service.ExecutionLeaseLostException ignored) {
                leaseLost.add(target.targetId());
            }
        }
        return List.copyOf(leaseLost);
    }
    /** Calculates a fresh full lease window after any implementation-specific blocking setup. */
    default List<Long> renewPendingLeases(List<ClaimedTarget> targets, Duration leaseDuration) {
        return renewPendingLeases(targets, Instant.now().plus(leaseDuration));
    }
    int failUnclaimedTargets(java.util.List<Long> targetIds, String reason);

    record ClaimedTarget(long targetId, String workerId) {
    }
    default long appendOutput(
            long targetId,
            String workerId,
            int commandIndex,
            OutputStreamType streamType,
            String content,
            long receivedBytes,
            int pageCount,
            boolean truncated,
            Instant createdAt) {
        throw new UnsupportedOperationException("incremental output persistence is not supported");
    }
    default void initializeCommandBlocks(long targetId, String workerId, List<CommandOutputBlock> commandBlocks) {
        // Optional until the command-output-block migration is installed.
    }
    default void updateCommandBlocks(long targetId, String workerId, List<CommandOutputBlock> commandBlocks) {
        // Optional until the command-output-block migration is installed.
    }
    default void updateOutputSnapshot(
            long targetId,
            String workerId,
            String stdout,
            String stderr,
            boolean truncated) {
        // Optional for persistence adapters that do not expose live execution snapshots.
    }
    void updateTarget(long targetId, String workerId, CollectionStatus status, String stdout, String stderr,
                      Integer exitCode, String outcome, boolean truncated, Map<String, String> parsedFacts);
    int failRecoveredTargets(Instant now, String reason);
    default int failExpiredTargets(Instant now, String reason) {
        return failRecoveredTargets(now, reason);
    }
    default int failRecoverableTargets(
            Instant now,
            Instant unclaimedBefore,
            String reason,
            int batchSize) {
        return failExpiredTargets(now, reason);
    }
    default void cancelClaimedTarget(long targetId, String workerId, String reason) {
        updateTarget(targetId, workerId, CollectionStatus.CANCELLED,
                "", "", null, reason, false, Map.of());
    }
}
