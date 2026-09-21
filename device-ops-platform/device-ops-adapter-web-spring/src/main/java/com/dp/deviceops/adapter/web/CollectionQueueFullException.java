package com.dp.deviceops.adapter.web;

/** A caller can surface this explicit capacity signal instead of silently losing collection work. */
public final class CollectionQueueFullException extends RuntimeException {
    public CollectionQueueFullException(Throwable cause) { super("device collection executor is full", cause); }
}
