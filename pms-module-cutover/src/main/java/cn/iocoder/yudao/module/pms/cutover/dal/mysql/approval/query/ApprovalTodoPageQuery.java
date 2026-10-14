package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query;

import lombok.Builder;
import java.util.Set;

@Builder
public record ApprovalTodoPageQuery(Long tenantId, Long currentUserId, Integer offset, Integer pageSize,
                                    Set<Long> candidateRoleIds) {
}
