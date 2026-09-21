package com.dp.deviceops.core.port;

import com.dp.deviceops.core.model.CollectionTask;

import java.util.Objects;
import java.util.Optional;

/**
 * Persistence boundary for collection tasks owned by the device operations platform.
 */
public interface CollectionRepository {

    Optional<CollectionTask> findByIdempotencyKey(String namespace, String key);

    SaveResult saveOrGetExisting(CollectionTask candidate);

    /** Atomically prevent a missing request from ever being submitted. False means it already exists. */
    default boolean cancelBeforeSubmission(String namespace, String key) {
        throw new UnsupportedOperationException("submission cancellation is unavailable");
    }

    default boolean isCancelledBeforeSubmission(String namespace, String key) { return false; }

    /**
     * The outcome of atomically persisting a candidate or returning the concurrent winner.
     */
    record SaveResult(CollectionTask task, boolean created) {

        public SaveResult {
            task = Objects.requireNonNull(task, "task must not be null");
        }
    }
}
