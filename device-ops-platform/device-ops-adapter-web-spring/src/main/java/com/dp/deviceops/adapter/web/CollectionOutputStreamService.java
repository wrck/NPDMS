package com.dp.deviceops.adapter.web;

import com.dp.deviceops.core.model.CollectionStatus;
import com.dp.deviceops.core.port.CollectionOutputEventQueryPort;
import com.dp.deviceops.core.port.CollectionQueryPort;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * Replays persisted output events. The database cursor is the only continuation source; closing this HTTP stream
 * never affects collection execution.
 */
public final class CollectionOutputStreamService {

    private static final long EMITTER_TIMEOUT_MILLIS = 30_000L;
    private static final int BATCH_SIZE = 200;
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10);

    private final CollectionOutputEventQueryPort events;
    private final CollectionQueryPort collections;
    private final ThreadPoolTaskExecutor executor;

    public CollectionOutputStreamService(CollectionOutputEventQueryPort events,
                                         CollectionQueryPort collections,
                                         ThreadPoolTaskExecutor executor) {
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.collections = Objects.requireNonNull(collections, "collections must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public SseEmitter open(String namespace, String projectKey, String collectionId, long afterSequence) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        StreamSession session = new StreamSession();
        emitter.onCompletion(session::close);
        emitter.onTimeout(session::close);
        emitter.onError(ignored -> session.close());
        try {
            session.attach(executor.submit(
                    () -> stream(emitter, session, namespace, projectKey, collectionId, afterSequence)));
        } catch (TaskRejectedException rejected) {
            session.close();
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "output stream capacity is exhausted", rejected);
        }
        return emitter;
    }

    private void stream(SseEmitter emitter, StreamSession session, String namespace, String projectKey,
                        String collectionId, long afterSequence) {
        long cursor = afterSequence;
        long nextHeartbeat = System.nanoTime() + HEARTBEAT_INTERVAL.toNanos();
        try {
            while (session.isOpen()) {
                var batch = events.findAfter(namespace, projectKey, collectionId, cursor, BATCH_SIZE);
                if (!batch.isEmpty()) {
                    cursor = sendBatch(emitter, session, batch, cursor);
                    continue;
                }

                CollectionStatus status = findCollection(namespace, projectKey, collectionId)
                        .map(CollectionQueryPort.CollectionDetails::status)
                        .orElseThrow(() -> new IllegalStateException("collection disappeared while streaming"));
                if (terminal(status)) {
                    var trailingBatch = events.findAfter(
                            namespace, projectKey, collectionId, cursor, BATCH_SIZE);
                    if (!trailingBatch.isEmpty()) {
                        cursor = sendBatch(emitter, session, trailingBatch, cursor);
                        continue;
                    }
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data(Map.of("status", status.name(), "lastSequence", cursor)));
                    emitter.complete();
                    return;
                }

                long now = System.nanoTime();
                if (now >= nextHeartbeat) {
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data(Map.of("lastSequence", cursor)));
                    nextHeartbeat = now + HEARTBEAT_INTERVAL.toNanos();
                }
                Thread.sleep(POLL_INTERVAL.toMillis());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException failure) {
            if (session.isOpen()) {
                emitter.completeWithError(failure);
            }
        } finally {
            session.close();
        }
    }

    private static long sendBatch(SseEmitter emitter, StreamSession session,
                                  List<CollectionOutputEventQueryPort.OutputEvent> batch,
                                  long currentCursor) throws IOException {
        long cursor = currentCursor;
        for (CollectionOutputEventQueryPort.OutputEvent event : batch) {
            if (!session.isOpen()) {
                return cursor;
            }
            emitter.send(SseEmitter.event()
                    .name("output")
                    .id(Long.toString(event.sequence()))
                    .data(toResponse(event)));
            cursor = event.sequence();
        }
        return cursor;
    }

    private Optional<CollectionQueryPort.CollectionDetails> findCollection(
            String namespace, String projectKey, String collectionId) {
        if (projectKey == null || projectKey.isBlank()) {
            return collections.find(namespace, collectionId);
        }
        return collections.find(namespace, projectKey, collectionId);
    }

    private static CollectionOutputEventResponse toResponse(CollectionOutputEventQueryPort.OutputEvent event) {
        return new CollectionOutputEventResponse(
                event.targetId(),
                event.sequence(),
                event.commandIndex(),
                event.streamType().name(),
                event.content(),
                event.receivedBytes(),
                event.pageCount(),
                event.truncated(),
                event.createdAt());
    }

    private static boolean terminal(CollectionStatus status) {
        return switch (status) {
            case SUCCEEDED, PARTIAL_SUCCESS, FAILED, TIMED_OUT, CANCELLED -> true;
            default -> false;
        };
    }

    private static final class StreamSession {
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicReference<Future<?>> worker = new AtomicReference<>();

        boolean isOpen() {
            return open.get();
        }

        void attach(Future<?> future) {
            if (!worker.compareAndSet(null, future)) {
                future.cancel(true);
                return;
            }
            if (!open.get()) {
                future.cancel(true);
            }
        }

        void close() {
            if (open.compareAndSet(true, false)) {
                Future<?> future = worker.get();
                if (future != null) {
                    future.cancel(true);
                }
            }
        }
    }
}
