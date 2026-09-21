package com.dp.deviceops.adapter.web.config;

import com.dp.deviceops.adapter.web.concurrency.KeyedCollectionDispatcher;
import com.dp.deviceops.adapter.web.callback.CallbackProperties;
import com.dp.deviceops.adapter.web.masterdata.MasterDataHttpProperties;
import com.dp.deviceops.adapter.web.schedule.ScheduleProperties;
import com.dp.deviceops.adapter.web.security.EmbedSecurityProperties;
import com.dp.deviceops.core.port.CollectionExecutionPersistencePort;
import com.dp.deviceops.core.service.CollectionWorker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({CollectionExecutorProperties.class, MasterDataHttpProperties.class, CallbackProperties.class, ScheduleProperties.class, EmbedSecurityProperties.class})
public class CollectionExecutorConfiguration {
    @Bean
    public AtomicLong collectionRejectedCount() {
        return new AtomicLong();
    }

    @Bean(destroyMethod = "shutdownNow")
    public ThreadPoolExecutor collectionExecutor(
            CollectionExecutorProperties properties,
            @Qualifier("collectionRejectedCount") AtomicLong rejectedCount) {
        properties.validate();
        BlockingQueue<Runnable> queue = properties.queueCapacity() == 0
                ? new SynchronousQueue<>()
                : new ArrayBlockingQueue<>(properties.queueCapacity());
        ThreadPoolExecutor.AbortPolicy abortPolicy = new ThreadPoolExecutor.AbortPolicy();
        return new ThreadPoolExecutor(
                properties.coreSize(),
                properties.maxSize(),
                60L,
                TimeUnit.SECONDS,
                queue,
                Thread.ofPlatform().name("device-ops-collector-", 0).factory(),
                (task, pool) -> {
                    rejectedCount.incrementAndGet();
                    abortPolicy.rejectedExecution(task, pool);
                });
    }

    @Bean(destroyMethod = "close")
    public KeyedCollectionDispatcher keyedCollectionDispatcher(
            @Qualifier("collectionExecutor") ThreadPoolExecutor executor,
            CollectionWorker worker,
            CollectionExecutionPersistencePort persistence,
            CollectionExecutorProperties properties,
            Clock clock,
            @Qualifier("collectionRejectedCount") AtomicLong rejectedCount) {
        properties.validate();
        return new KeyedCollectionDispatcher(
                executor,
                worker,
                persistence,
                clock,
                properties.connectionWaitTimeout(),
                properties.leaseHeartbeatInterval(),
                properties.shutdownRecoveryLease(),
                properties.perConnectionLimit(),
                properties.outstandingCapacity(),
                rejectedCount);
    }
}
