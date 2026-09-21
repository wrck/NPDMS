package com.dp.deviceops.parser.runtime.port;

import com.dp.deviceops.parser.runtime.model.ParseTaskState;
import com.dp.deviceops.parser.runtime.model.ParseWaitReason;

import java.time.Instant;
import java.util.Map;

public interface ParserRuntimeObservationPort {
    RuntimeSnapshot snapshot(Instant now);

    record RuntimeSnapshot(
            Map<ParseTaskState, Long> taskCounts,
            Map<ParseWaitReason, Long> waitingCounts,
            long oldestQueuedSeconds,
            long pendingResultDeliveries) {
        public RuntimeSnapshot {
            taskCounts = Map.copyOf(taskCounts);
            waitingCounts = Map.copyOf(waitingCounts);
            if (oldestQueuedSeconds < 0 || pendingResultDeliveries < 0) {
                throw new IllegalArgumentException("runtime counters must not be negative");
            }
        }
    }
}
