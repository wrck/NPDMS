package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query;

public record ApprovalTodoPageQuery(Long tenantId, Long currentUserId, Integer offset, Integer pageSize) {
}
