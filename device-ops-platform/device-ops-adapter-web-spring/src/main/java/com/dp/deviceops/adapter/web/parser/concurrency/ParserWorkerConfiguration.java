package com.dp.deviceops.adapter.web.parser.concurrency;

import com.dp.deviceops.parser.runtime.model.WorkerCapability;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository;
import com.dp.deviceops.parser.runtime.port.ParserPayloadStore;
import com.dp.deviceops.parser.runtime.port.ParserReleaseRepository;
import com.dp.deviceops.parser.runtime.port.ParserRuntimeTelemetryPort;
import com.dp.deviceops.parser.runtime.port.WorkerCapabilityRepository;
import com.dp.deviceops.parser.runtime.service.ParserPlanCache;
import com.dp.deviceops.parser.runtime.service.ParserTaskExecutor;
import com.dp.deviceops.parser.semantic.DefaultDynamicSemanticParser;
import com.dp.deviceops.parser.semantic.DynamicSemanticParser;
import com.dp.deviceops.parser.semantic.plan.ParserExtension;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(ParserWorkerProperties.class)
public class ParserWorkerConfiguration {

    @Bean String parserWorkerId() { return "parser-" + UUID.randomUUID(); }

    @Bean Set<WorkerCapability> parserWorkerCapabilities() {
        Set<WorkerCapability> result = new LinkedHashSet<>();
        ParserPlanCompiler.SUPPORTED_ENGINE_VERSIONS.forEach(version ->
                result.add(new WorkerCapability(version, null, null)));
        ServiceLoader.load(ParserExtension.class).forEach(extension -> result.add(new WorkerCapability(
                ParserPlanCompiler.ENGINE_VERSION, extension.extensionId(), extension.extensionVersion())));
        return Set.copyOf(result);
    }

    @Bean DynamicSemanticParser dynamicSemanticParser() { return new DefaultDynamicSemanticParser(); }

    @Bean ParserPlanCache parserPlanCache(ParserWorkerProperties properties) {
        properties.validate();
        return new ParserPlanCache(properties.planCacheSize());
    }

    @Bean(name = "parserWorkerExecutor", destroyMethod = "")
    ThreadPoolExecutor parserWorkerExecutor(ParserWorkerProperties properties) {
        properties.validate();
        BlockingQueue<Runnable> queue = properties.queueCapacity() == 0
                ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(properties.queueCapacity());
        return new ThreadPoolExecutor(properties.coreSize(), properties.maxSize(), 60L, TimeUnit.SECONDS,
                queue, Thread.ofPlatform().name("device-ops-parser-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean ParserTaskExecutor parserTaskExecutor(@Qualifier("parserWorkerId") String parserWorkerId,
            ParserReleaseRepository releases, ParserPayloadStore payloads, ParseTaskRepository tasks,
            ParserPlanCompiler compiler, DynamicSemanticParser parser, ParserPlanCache cache,
            ParserWorkerProperties properties, Clock clock,
            ObjectProvider<ParserRuntimeTelemetryPort> telemetry) {
        return new ParserTaskExecutor(parserWorkerId, releases, payloads, tasks, compiler,
                parser, cache, clock, properties.retryDelay(),
                telemetry.getIfAvailable(() -> ParserRuntimeTelemetryPort.NOOP));
    }

    @Bean ParserWorkerCoordinator parserWorkerCoordinator(
            @Qualifier("parserWorkerId") String parserWorkerId,
            @Qualifier("parserWorkerCapabilities") Set<WorkerCapability> parserWorkerCapabilities,
            ParseTaskRepository tasks,
            ParserTaskExecutor taskExecutor,
            @Qualifier("parserWorkerExecutor") ThreadPoolExecutor executor,
            ParserWorkerProperties properties, Clock clock) {
        return new ParserWorkerCoordinator(parserWorkerId, parserWorkerCapabilities, tasks,
                taskExecutor, executor, properties, clock);
    }

    @Bean ParserWorkerMaintenance parserWorkerMaintenance(ParserWorkerCoordinator coordinator,
            ParseTaskRepository tasks, WorkerCapabilityRepository capabilities,
            ParserWorkerProperties properties, Clock clock,
            ObjectProvider<ParserRuntimeTelemetryPort> telemetry) {
        return new ParserWorkerMaintenance(coordinator, tasks, capabilities, properties, clock,
                telemetry.getIfAvailable(() -> ParserRuntimeTelemetryPort.NOOP));
    }
}
