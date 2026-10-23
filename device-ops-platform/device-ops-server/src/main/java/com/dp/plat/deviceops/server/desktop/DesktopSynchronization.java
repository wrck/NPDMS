package com.dp.plat.deviceops.server.desktop;

import com.dp.plat.deviceops.adapter.persistence.jdbc.JdbcDesktopTaskStore;
import com.dp.plat.deviceops.adapter.web.callback.*;
import com.dp.plat.deviceops.adapter.web.concurrency.KeyedCollectionDispatcher;
import com.dp.plat.deviceops.core.port.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.*;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Offline collection uses the existing engine; only observations and immutable outbox delivery wait for NPDMS. */
@Component
@Profile("desktop")
public class DesktopSynchronization {
    private static final Logger log = LoggerFactory.getLogger(DesktopSynchronization.class);
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "TIMED_OUT", "CANCELLED");
    private final JdbcDesktopTaskStore tasks;
    private final CollectionRepository repository;
    private final CollectionQueryPort queries;
    private final CallbackOutboxPort outbox;
    private final KeyedCollectionDispatcher dispatcher;
    private final DesktopServerClient server;
    private final ObjectMapper json = new ObjectMapper();

    public DesktopSynchronization(JdbcDesktopTaskStore tasks, CollectionRepository repository, CollectionQueryPort queries,
            CallbackOutboxPort outbox, KeyedCollectionDispatcher dispatcher, DesktopServerClient server) {
        this.tasks = tasks; this.repository = repository; this.queries = queries; this.outbox = outbox;
        this.dispatcher = dispatcher; this.server = server;
    }

    @Scheduled(fixedDelayString="${device-ops.callback.poll-interval:5s}")
    public void synchronize() {
        for (var task : tasks.pending()) {
            char[] token = tasks.token(task);
            try { observe(new DesktopServerClient.TaskAuthorization(task, new String(token))); }
            catch (org.springframework.web.client.ResourceAccessException offline) { break; }
            catch (RuntimeException failure) { log.debug("Desktop observation pending task={} type={}", task.platformTaskId(), failure.getClass().getSimpleName()); }
            finally { Arrays.fill(token, '\0'); tasks.polled(task.key()); }
        }
        String worker = "desktop-" + UUID.randomUUID();
        // One leased event at a time: slow/offline HTTP cannot consume another event's lease.
        for (int index = 0; index < 20; index++) {
            Instant now = Instant.now();
            var due = tasks.claimDue(new JdbcDesktopTaskStore.Due(worker, now, now.plusSeconds(60), 1));
            if (due.isEmpty()) break;
            var event = due.getFirst();
            try {
                if (deliver(event)) outbox.markDelivered(event.eventId(), worker, Instant.now());
                else retry(new Retry(event, worker, "acknowledgement pending"));
            } catch (Exception failure) {
                retry(new Retry(event, worker, "delivery pending"));
                if (failure instanceof org.springframework.web.client.ResourceAccessException) break;
            }
        }
    }

    private void observe(DesktopServerClient.TaskAuthorization authorization) {
        var task = authorization.task();
        var execution = repository.findByIdempotencyKey(task.namespace(), task.platformTaskId());
        if (execution.isEmpty()) {
            if (server.control(authorization).cancelRequested() && repository.cancelBeforeSubmission(task.namespace(), task.platformTaskId())) {
                server.cancelledBeforeSubmission(authorization);
                tasks.acknowledge(task.key());
            }
            return;
        }
        if (!task.platformTaskId().equals(execution.get().externalRequestId())) throw new IllegalStateException("desktop task binding differs");
        var collection = queries.find(task.namespace(), task.projectId(), execution.get().taskId()).orElseThrow();
        tasks.attach(new JdbcDesktopTaskStore.Attachment(task.key(), collection.collectionId()));
        var control = server.observe(new DesktopServerClient.Observation(authorization, collection.collectionId(), collection.status().name(), false));
        if (control.cancelRequested()) {
            for (var target : collection.targets()) if (!TERMINAL.contains(target.status().name())) dispatcher.cancel(target.targetId());
        }
        if (control.redeliveryRequested() && TERMINAL.contains(collection.status().name())) {
            outbox.redeliverTerminalResult(collection.collectionId());
            server.observe(new DesktopServerClient.Observation(authorization, collection.collectionId(), collection.status().name(), true));
        }
    }

    private boolean deliver(CallbackOutboxPort.Event event) throws Exception {
        if (!server.callbackUrl().equals(event.destination())) throw new IllegalStateException("desktop callback destination differs");
        var payload = json.readTree(event.payload());
        var task = tasks.find(new JdbcDesktopTaskStore.TaskKey(payload.path("namespace").asText(), payload.path("externalRequestId").asText())).orElseThrow();
        if (!Objects.equals(task.collectionId(), payload.path("collectionId").asText())) throw new IllegalStateException("desktop callback binding differs");
        char[] token = tasks.token(task);
        try {
            String capability = new String(token);
            // Acceptance must be persisted before the ordinary business callback can be ingested.
            observe(new DesktopServerClient.TaskAuthorization(task, capability));
            var properties = new CallbackProperties();
            properties.setNpdmsDestination(server.callbackUrl());
            properties.setNpdmsSigningKey(capability);
            String suffix = "-" + task.tenantId();
            if (!task.namespace().endsWith(suffix)) throw new IllegalStateException("desktop namespace differs");
            properties.setNpdmsNamespacePrefix(task.namespace().substring(0, task.namespace().length() - suffix.length()) + "-");
            // Local target counters repeat across DAC databases. Scope only the transport identity;
            // the original outbox ID and payload remain unchanged for retries and evidence.
            String callbackId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    ("desktop\n" + task.namespace() + "\n" + task.platformTaskId() + "\n" + event.eventId())
                            .getBytes(StandardCharsets.UTF_8)));
            var delivery = new CallbackOutboxPort.Event(callbackId, event.destination(), event.payload(), event.attemptCount());
            boolean delivered = NpdmsCallbackSender.send(delivery, properties, server.callbackClient(capability));
            if (delivered) tasks.acknowledge(task.key());
            return delivered;
        } finally { Arrays.fill(token, '\0'); }
    }

    private void retry(Retry retry) {
        int attempts = Math.min(Integer.MAX_VALUE - 1, retry.event().attemptCount()) + 1;
        long delay = Math.min(300, 5L << Math.min(6, attempts - 1));
        // An outage never exhausts the result. Reuse the original event identity and bytes after reconnect.
        outbox.reschedule(retry.event().eventId(), retry.worker(), attempts, Instant.now().plusSeconds(delay), retry.safeError());
    }
    private record Retry(CallbackOutboxPort.Event event, String worker, String safeError) { }
}
