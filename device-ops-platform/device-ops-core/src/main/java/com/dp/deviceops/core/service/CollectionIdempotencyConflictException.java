package com.dp.deviceops.core.service;

/** A collection submission key is already bound to a different or unverifiable request. */
public final class CollectionIdempotencyConflictException extends IllegalStateException {

    public CollectionIdempotencyConflictException() {
        super("collection submission conflicts with an existing request");
    }
}
