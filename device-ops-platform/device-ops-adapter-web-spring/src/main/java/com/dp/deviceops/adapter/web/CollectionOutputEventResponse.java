package com.dp.deviceops.adapter.web;

import java.time.Instant;

/** Public SSE contract for an already-redacted, durably stored output chunk. */
public record CollectionOutputEventResponse(
        long targetId,
        long sequence,
        Integer commandIndex,
        String stream,
        String content,
        long receivedBytes,
        int pageCount,
        boolean truncated,
        Instant createdAt) {
}
