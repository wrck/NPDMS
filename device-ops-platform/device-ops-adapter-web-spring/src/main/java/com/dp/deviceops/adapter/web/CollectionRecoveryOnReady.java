package com.dp.deviceops.adapter.web;

import com.dp.deviceops.core.port.CollectionExecutionPersistencePort;
import com.dp.deviceops.adapter.web.concurrency.CollectionMaintenanceScheduler;
import com.dp.deviceops.adapter.web.config.CollectionExecutorProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;

/** Transient credentials are never durable, so interrupted work is failed rather than replayed. */
@Component
public final class CollectionRecoveryOnReady implements ApplicationListener<ApplicationReadyEvent> {
    public static final String TRANSIENT_CREDENTIAL_LOST = "TRANSIENT_CREDENTIAL_LOST";
    private final CollectionExecutionPersistencePort persistence;
    private final Clock clock;
    private final CollectionExecutorProperties properties;
    private final CollectionMaintenanceScheduler maintenance;
    public CollectionRecoveryOnReady(
            CollectionExecutionPersistencePort persistence,
            Clock clock,
            CollectionExecutorProperties properties,
            CollectionMaintenanceScheduler maintenance) {
        this.persistence = Objects.requireNonNull(persistence);
        this.clock = Objects.requireNonNull(clock);
        this.properties = Objects.requireNonNull(properties);
        this.maintenance = Objects.requireNonNull(maintenance);
    }
    @Override public void onApplicationEvent(ApplicationReadyEvent event) {
        maintenance.scheduleWithFixedDelay(properties.recoveryInterval(), this::recoverBatch);
    }

    private void recoverBatch() {
        var now = clock.instant();
        persistence.failRecoverableTargets(
                now,
                now.minus(properties.recoveryUnclaimedGrace()),
                TRANSIENT_CREDENTIAL_LOST,
                properties.recoveryBatchSize());
    }
}
