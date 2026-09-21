package com.dp.deviceops.core.port;

import java.time.Instant;
import java.util.List;

/** Durable callback delivery boundary; payloads are immutable snapshots. */
public interface CallbackOutboxPort {
    List<Event> claimDue(String workerId, Instant now, Instant leaseUntil, int limit);
    void markDelivered(String eventId, String workerId, Instant deliveredAt);
    void reschedule(String eventId, String workerId, int attemptCount, Instant nextAttemptAt, String safeError);
    void markDeadLetter(String eventId, String workerId, int attemptCount, String safeError, Instant failedAt);
    DeliveryState findDeliveryState(String eventId);
    /** Requeue failed delivery of the same immutable terminal event, never execute a collection again. */
    default int redeliverTerminalResult(String collectionId) {
        throw new UnsupportedOperationException("terminal result redelivery is not supported");
    }
    record Event(String eventId, String destination, String payload, int attemptCount) { }
    record DeliveryState(String state, int attemptCount, Instant nextAttemptAt, Instant deliveredAt, Instant deadLetteredAt, String safeError) { }
}
