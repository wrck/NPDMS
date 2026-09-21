package com.dp.deviceops.parser.runtime.model;

public enum ParseWaitReason {
    ARTIFACT_UNAVAILABLE,
    NO_CAPABLE_WORKER,
    PAYLOAD_UNAVAILABLE,
    TRANSIENT_STORAGE_ERROR,
    RETRY_DELAY
}
