package com.dp.deviceops.core.port;

import com.dp.deviceops.core.port.CommandExecutionPort.OutputStreamType;

import java.time.Instant;
import java.util.List;

/** Read boundary for replaying task-scoped, already-redacted output events. */
public interface CollectionOutputEventQueryPort {

    List<OutputEvent> findAfter(
            String namespace,
            String projectKey,
            String collectionId,
            long afterSequence,
            int limit);

    record OutputEvent(
            long targetId,
            long sequence,
            Integer commandIndex,
            OutputStreamType streamType,
            String content,
            long receivedBytes,
            int pageCount,
            boolean truncated,
            Instant createdAt) {
    }
}
