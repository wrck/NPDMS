package cn.iocoder.yudao.module.system.dal.mysql.user.query;

import java.util.Set;
import java.time.LocalDateTime;
import lombok.Builder;

/** 当前租户系统角色人员；仅正式经理按已有规则附加组织资格。 */
@Builder
public record ActiveUserSelectionQuery(Long tenantId, int pageNo, int pageSize,
        String keyword, Set<Long> userIds, String roleCode, Long companyId,
        Long departmentId, String departmentCode, String scopeRole, LocalDateTime effectiveAt) {
    public long offset() { return (long) (pageNo - 1) * pageSize; }
}
