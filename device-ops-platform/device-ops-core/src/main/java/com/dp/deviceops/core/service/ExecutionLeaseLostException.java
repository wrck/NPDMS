package com.dp.deviceops.core.service;

/** Raised when a worker no longer owns the target it was executing. */
public final class ExecutionLeaseLostException extends RuntimeException {
    public ExecutionLeaseLostException(long targetId) { super("collection execution lease was lost for target " + targetId); }
}
