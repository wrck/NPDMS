package com.dp.deviceops.server;

import com.dp.deviceops.adapter.web.concurrency.KeyedCollectionDispatcher;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

public final class DeviceOpsConcurrencyMetrics {

    public DeviceOpsConcurrencyMetrics(
            MeterRegistry registry,
            ThreadPoolExecutor executor,
            KeyedCollectionDispatcher dispatcher,
            AtomicLong rejectedCount) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(rejectedCount, "rejectedCount");

        Gauge.builder("device_ops_collection_active", executor, ThreadPoolExecutor::getActiveCount)
                .register(registry);
        Gauge.builder("device_ops_collection_queued", executor, value -> value.getQueue().size())
                .register(registry);
        Gauge.builder("device_ops_collection_queue_remaining", dispatcher,
                        KeyedCollectionDispatcher::remainingCapacity)
                .register(registry);
        Gauge.builder("device_ops_connection_waiting", dispatcher, KeyedCollectionDispatcher::waitingCount)
                .register(registry);
        Gauge.builder("device_ops_connection_keys_active", dispatcher, KeyedCollectionDispatcher::activeKeyCount)
                .register(registry);
        FunctionCounter.builder("device_ops_collection_rejected_total", rejectedCount, AtomicLong::get)
                .register(registry);
        FunctionCounter.builder("device_ops_connection_wait_timeout_total", dispatcher,
                        KeyedCollectionDispatcher::timeoutCount)
                .register(registry);
    }
}
