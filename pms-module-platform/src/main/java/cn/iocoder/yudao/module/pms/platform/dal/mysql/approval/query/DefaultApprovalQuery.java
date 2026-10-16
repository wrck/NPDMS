package cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.query;

/** Exact inherited entity and tenant; attemptId is present only for a retry or trusted callback. */
public record DefaultApprovalQuery(Long tenantId, String ownerModule, String entityType, Long entityId,
                                   String purpose, String backendId, String attemptId) { }
