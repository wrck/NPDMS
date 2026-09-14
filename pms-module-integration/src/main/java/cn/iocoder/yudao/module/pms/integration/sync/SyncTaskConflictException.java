package cn.iocoder.yudao.module.pms.integration.sync;

/** A configuration conflict, not an external database connectivity failure. */
public class SyncTaskConflictException extends IllegalArgumentException {
    public SyncTaskConflictException(String message) { super(message); }
}
