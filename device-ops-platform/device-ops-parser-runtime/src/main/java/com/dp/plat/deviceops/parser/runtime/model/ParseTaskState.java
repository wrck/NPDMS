package com.dp.plat.deviceops.parser.runtime.model;

public enum ParseTaskState {
    QUEUED,
    RUNNING,
    WAITING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
