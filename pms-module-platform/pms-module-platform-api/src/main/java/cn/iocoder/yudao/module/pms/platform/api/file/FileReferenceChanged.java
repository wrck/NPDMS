package cn.iocoder.yudao.module.pms.platform.api.file;

/** Published from the committed, tenant-validated file Outbox; consumers must be idempotent. */
public record FileReferenceChanged(String eventId, Long tenantId, Long referenceId) { }
