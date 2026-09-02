package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query;

public record ApprovalInstanceLockQuery(Long tenantId, Long approvalInstanceId,
                                        Long taskId, Long planRevisionId) {
}
