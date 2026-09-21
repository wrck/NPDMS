package com.dp.deviceops.adapter.web;

import com.dp.deviceops.core.service.CollectionWorker;
import com.dp.deviceops.adapter.web.concurrency.KeyedCollectionDispatcher;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

/** Submits one immutable work item and converts executor rejection into a domain signal. */
@Component
public final class CollectionExecutionCoordinator {
    private final KeyedCollectionDispatcher dispatcher;
    public CollectionExecutionCoordinator(KeyedCollectionDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher);
    }
    public void submit(CollectionWorker.WorkItem item) {
        try { dispatcher.submit(item); }
        catch (RejectedExecutionException exception) { item.context().close(); throw new CollectionQueueFullException(exception); }
    }
}
