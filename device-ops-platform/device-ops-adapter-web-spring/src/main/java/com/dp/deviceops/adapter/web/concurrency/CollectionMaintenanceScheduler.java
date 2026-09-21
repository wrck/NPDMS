package com.dp.deviceops.adapter.web.concurrency;

import java.time.Duration;

/** Registers collection maintenance on the dispatcher-owned shutdown boundary. */
@FunctionalInterface
public interface CollectionMaintenanceScheduler {

    boolean scheduleWithFixedDelay(Duration interval, Runnable maintenance);
}
