package com.dp.deviceops.parser.runtime.port;

import com.dp.deviceops.parser.runtime.model.ParseWaitReason;
import com.dp.deviceops.parser.semantic.ParserCoordinate;

import java.time.Duration;

/** Low-cardinality lifecycle observations emitted by parser workers. */
public interface ParserRuntimeTelemetryPort {

    ParserRuntimeTelemetryPort NOOP = new ParserRuntimeTelemetryPort() { };

    default void taskSucceeded(ParserCoordinate coordinate, Duration duration, long unmappedUnits) { }

    default void taskFailed(ParserCoordinate coordinate, String errorCode, Duration duration) { }

    default void taskWaiting(ParserCoordinate coordinate, ParseWaitReason reason,
            String errorCode, Duration duration) { }

    default void leasesExpired(int count) { }

    default void releaseLoadFailed(ParserCoordinate coordinate, String reason) { }
}
