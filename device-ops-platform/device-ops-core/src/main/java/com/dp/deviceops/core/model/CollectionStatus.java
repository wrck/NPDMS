package com.dp.deviceops.core.model;

public enum CollectionStatus {
    QUEUED,
    CONNECTING,
    EXECUTING,
    PARSING,
    SUCCEEDED,
    PARTIAL_SUCCESS,
    FAILED,
    TIMED_OUT,
    CANCELLED
}
