package com.dp.deviceops.server;

import com.dp.deviceops.adapter.persistence.jdbc.*;
import com.dp.deviceops.adapter.ssh.mina.MinaCommandExecutionAdapter;
import com.dp.deviceops.adapter.telnet.TelnetCommandExecutionAdapter;
import com.dp.deviceops.adapter.web.CollectionOutputStreamService;
import com.dp.deviceops.adapter.web.concurrency.KeyedCollectionDispatcher;
import com.dp.deviceops.adapter.web.config.CollectionExecutorProperties;
import com.dp.deviceops.adapter.web.parser.ConfiguredResultConsumerRegistry;
import com.dp.deviceops.adapter.web.parser.ParserControlProperties;
import com.dp.deviceops.adapter.web.parser.concurrency.ParserWorkerCoordinator;
import com.dp.deviceops.core.port.*;
import com.dp.deviceops.core.service.*;
import com.dp.deviceops.parser.runtime.port.*;
import com.dp.deviceops.parser.runtime.service.*;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Pattern;

@Configuration
public class DeviceOpsWiringConfiguration {
    @Bean Clock deviceOpsClock() { return Clock.systemUTC(); }
    @Bean ObjectMapper deviceOpsObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule()).registerModule(new SimpleModule()
                .addDeserializer(char[].class, new RequestSensitiveCharArrayDeserializer()));
    }
    @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }

    @Bean
    JdbcParserReleaseRepository jdbcParserReleaseRepository(
            JdbcClient jdbc, TransactionTemplate tx, ObjectMapper json, Clock clock) {
        return new JdbcParserReleaseRepository(jdbc, tx, json, clock);
    }
    @Bean
    JdbcParserPayloadStore jdbcParserPayloadStore(JdbcClient jdbc, Clock clock) {
        return new JdbcParserPayloadStore(jdbc, clock, () -> UUID.randomUUID().toString());
    }
    @Bean
    JdbcParseTaskRepository jdbcParseTaskRepository(JdbcClient jdbc, TransactionTemplate tx, ObjectMapper json) {
        return new JdbcParseTaskRepository(jdbc, tx, json, () -> UUID.randomUUID().toString(),
                () -> UUID.randomUUID().toString(), () -> UUID.randomUUID().toString());
    }
    @Bean
    JdbcWorkerCapabilityRepository jdbcWorkerCapabilityRepository(
            JdbcClient jdbc, TransactionTemplate tx, Clock clock) {
        return new JdbcWorkerCapabilityRepository(jdbc, tx, clock);
    }
    @Bean JdbcParseResultQueryAdapter jdbcParseResultQueryAdapter(JdbcClient jdbc, ObjectMapper json) {
        return new JdbcParseResultQueryAdapter(jdbc, json);
    }
    @Bean JdbcParserRuntimeObservationAdapter jdbcParserRuntimeObservationAdapter(JdbcClient jdbc) {
        return new JdbcParserRuntimeObservationAdapter(jdbc);
    }
    @Bean ConfiguredResultConsumerRegistry configuredResultConsumerRegistry(ParserControlProperties properties) {
        return new ConfiguredResultConsumerRegistry(properties);
    }
    @Bean ParserPlanCompiler parserPlanCompiler() { return new ParserPlanCompiler(); }
    @Bean ParserReleaseVerifier parserReleaseVerifier(Clock clock) { return new ParserReleaseVerifier(clock); }
    @Bean ParserReleaseService parserReleaseService(
            ParserReleaseRepository releases, WorkerCapabilityRepository workers,
            ParserPlanCompiler compiler, ParserReleaseVerifier verifier, Clock clock) {
        return new ParserReleaseService(releases, workers, compiler, verifier, clock);
    }
    @Bean ParseTaskService parseTaskService(
            ParserReleaseRepository releases, ParseTaskRepository tasks, ParseResultQueryPort results,
            ConfiguredResultConsumerRegistry consumers, Clock clock) {
        return new ParseTaskService(releases, tasks, results, consumers, clock,
                () -> UUID.randomUUID().toString());
    }

    @Bean
    JdbcCredentialStore credentialStore(
            JdbcClient jdbc,
            Clock clock,
            @Value("${device-ops.credentials.master-key:}") String masterKey) {
        return new JdbcCredentialStore(jdbc, new AesGcmCredentialCipher(masterKey), clock);
    }

    @Bean
    JdbcSavedConnectionStore savedConnectionStore(JdbcClient jdbc, JdbcCredentialStore credentialStore,
                                                  TransactionTemplate transactionTemplate, Clock clock) {
        return new JdbcSavedConnectionStore(jdbc, transactionTemplate, credentialStore, clock);
    }

    @Bean({"collectionJdbcRepository", "collectionRepository", "collectionTargetReferencePort"})
    CollectionJdbcRepository collectionJdbcRepository(JdbcClient jdbc, PlatformTransactionManager manager,
                                                       JdbcScriptArtifactRepository scripts) {
        return new CollectionJdbcRepository(jdbc, manager, scripts);
    }
    @Bean JdbcScriptArtifactRepository jdbcScriptArtifactRepository(JdbcClient jdbc) { return new JdbcScriptArtifactRepository(jdbc); }
    @Bean JdbcCollectionExecutionPersistencePort jdbcCollectionExecutionPersistencePort(
            JdbcClient jdbc, TransactionTemplate tx, ObjectMapper json, Clock clock,
            JdbcCollectionParserTaskAppender parserTasks) {
        return new JdbcCollectionExecutionPersistencePort(jdbc, tx, json, clock, parserTasks);
    }
    @Bean JdbcCollectionParserTaskAppender jdbcCollectionParserTaskAppender(
            JdbcClient jdbc, ObjectMapper json, ParserPayloadStore payloads,
            ParseTaskRepository tasks, Clock clock) {
        return new JdbcCollectionParserTaskAppender(jdbc, json, payloads, tasks, clock);
    }
    @Bean JdbcManagementQueryAdapter jdbcManagementQueryAdapter(JdbcClient jdbc, javax.sql.DataSource source) throws java.sql.SQLException {
        try (var connection = source.getConnection()) {
            return new JdbcManagementQueryAdapter(jdbc, "MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()));
        }
    }
    @Bean JdbcCollectionEvidenceQueryAdapter jdbcCollectionEvidenceQueryAdapter(JdbcClient jdbc, javax.sql.DataSource source) throws java.sql.SQLException {
        try (var connection = source.getConnection()) {
            return new JdbcCollectionEvidenceQueryAdapter(jdbc, "MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()));
        }
    }
    @Bean JdbcCollectionQueryAdapter jdbcCollectionQueryAdapter(JdbcClient jdbc, ObjectMapper json) { return new JdbcCollectionQueryAdapter(jdbc, json); }
    @Bean JdbcCollectionOutputEventQueryAdapter jdbcCollectionOutputEventQueryAdapter(JdbcClient jdbc) {
        return new JdbcCollectionOutputEventQueryAdapter(jdbc);
    }
    @Bean(name = "deviceOpsOutputStreamExecutor")
    ThreadPoolTaskExecutor deviceOpsOutputStreamExecutor(
            @Value("${device-ops.output-stream.core-size:2}") int coreSize,
            @Value("${device-ops.output-stream.max-size:16}") int maxSize,
            @Value("${device-ops.output-stream.queue-capacity:200}") int queueCapacity,
            @Value("${device-ops.output-stream.shutdown-await-seconds:10}") int shutdownAwaitSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("device-ops-output-stream-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(shutdownAwaitSeconds);
        return executor;
    }
    @Bean
    CollectionOutputStreamService collectionOutputStreamService(
            CollectionOutputEventQueryPort events,
            CollectionQueryPort collections,
            @Qualifier("deviceOpsOutputStreamExecutor") ThreadPoolTaskExecutor executor) {
        return new CollectionOutputStreamService(events, collections, executor);
    }
    @Bean JdbcCallbackOutboxPort jdbcCallbackOutboxPort(JdbcClient jdbc, TransactionTemplate tx) { return new JdbcCallbackOutboxPort(jdbc, tx); }
    @Bean JdbcInspectionSchedulePort jdbcInspectionSchedulePort(JdbcClient jdbc, TransactionTemplate tx, ObjectMapper json) {
        return new JdbcInspectionSchedulePort(jdbc, tx, json);
    }
    @Bean RemoteEndpointPolicy remoteEndpointPolicy() { return new SecureRemoteEndpointPolicy(); }
    @Bean(destroyMethod = "close") MinaCommandExecutionAdapter ssh2CommandExecutionAdapter(
            RemoteEndpointPolicy endpointPolicy,
            @Value("${device-ops.ssh.max-output-bytes:8388608}") int maxOutputBytes,
            @Value("${device-ops.ssh.event-chunk-bytes:8192}") int eventChunkBytes,
            @Value("${device-ops.ssh.command-prompt:}") String commandPromptOverride,
            @Value("${device-ops.ssh.max-pages:10000}") int maxPages) {
        return new MinaCommandExecutionAdapter(
                endpointPolicy, maxOutputBytes, eventChunkBytes,
                commandPromptOverride.isBlank() ? null : Pattern.compile(commandPromptOverride), maxPages);
    }
    @Bean
    CollectionRuntimeLifecycle collectionRuntimeLifecycle(
            KeyedCollectionDispatcher dispatcher,
            MinaCommandExecutionAdapter ssh2CommandExecutionAdapter,
            CollectionExecutorProperties properties) {
        return new CollectionRuntimeLifecycle(
                dispatcher,
                ssh2CommandExecutionAdapter,
                java.time.Duration.ofSeconds(properties.shutdownAwaitSeconds()),
                properties.shutdownGracefulPeriod());
    }
    @Bean ProtocolCommandExecutionAdapter telnetCommandExecutionAdapter(RemoteEndpointPolicy endpointPolicy,
                                                                        TelnetProperties properties) {
        return new TelnetCommandExecutionAdapter(
                endpointPolicy,
                properties.isEnabled(),
                properties.getMaxOutputBytes(),
                properties.getEventChunkBytes(),
                properties.getMaxPages());
    }
    @Bean CommandExecutionPort commandExecutionPort(List<ProtocolCommandExecutionAdapter> adapters) {
        return new ProtocolCommandExecutionRouter(adapters);
    }
    @Bean MeteredCommandExecutionPort meteredCommandExecutionPort(
            @Qualifier("commandExecutionPort") CommandExecutionPort command,
            MeterRegistry registry) {
        return new MeteredCommandExecutionPort(command, registry);
    }
    @Bean DeviceOpsConcurrencyMetrics deviceOpsConcurrencyMetrics(
            MeterRegistry registry,
            @Qualifier("collectionExecutor") ThreadPoolExecutor executor,
            KeyedCollectionDispatcher dispatcher,
            @Qualifier("collectionRejectedCount") AtomicLong rejectedCount) {
        return new DeviceOpsConcurrencyMetrics(registry, executor, dispatcher, rejectedCount);
    }
    @Bean ParserRuntimeMetrics parserRuntimeMetrics(
            MeterRegistry registry,
            ParserRuntimeObservationPort observations,
            WorkerCapabilityRepository workers,
            ObjectProvider<ParserWorkerCoordinator> coordinator,
            Clock clock) {
        return new ParserRuntimeMetrics(registry, observations, workers,
                () -> coordinator.getObject().availablePermits(), clock);
    }
    @Bean ConnectionTestService connectionTestService(
            MeteredCommandExecutionPort commands) {
        return new ConnectionTestService(commands);
    }
    @Bean SavedConnectionService savedConnectionService(SavedConnectionStore savedConnectionStore,
                                                        ConnectionTestService connectionTestService) {
        return new SavedConnectionService(savedConnectionStore, connectionTestService);
    }
    @Bean SubmitCollectionService submitCollectionService(CollectionRepository collections) {
        return new SubmitCollectionService(collections, () -> UUID.randomUUID().toString());
    }
    @Bean CollectionWorker collectionWorker(
                                             MeteredCommandExecutionPort command,
                                             CollectionExecutionPersistencePort persistence,
                                             Clock clock) {
        return new CollectionWorker(command, persistence, clock);
    }
    @Bean OutputParserRegistry outputParserRegistry(List<OutputParser> parsers) { return new OutputParserRegistry(parsers); }
}
