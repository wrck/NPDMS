package cn.iocoder.yudao.module.pms.cutover.service.approval.command;

public record ReassignCutoverApprovalCommand(Long tenantId, Long taskId, Integer expectedTaskVersion,
                                              Long approvalInstanceId, Integer expectedApprovalVersion,
                                              Integer nodeNo, Long newApproverUserId, String reason,
                                              String idempotencyKey, String correlationId) { }
