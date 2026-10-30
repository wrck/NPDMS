package cn.iocoder.yudao.module.pms.cutover.service.approval.command;

public record ReassignCutoverApprovalCommand(Long tenantId, Long taskId, Long expectedTaskVersion,
                                              Long approvalInstanceId, Long expectedApprovalVersion,
                                              Integer nodeNo, Long newApproverUserId, String reason,
                                              String idempotencyKey, String correlationId) { }
