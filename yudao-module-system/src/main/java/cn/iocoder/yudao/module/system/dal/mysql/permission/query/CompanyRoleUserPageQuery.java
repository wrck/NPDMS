package cn.iocoder.yudao.module.system.dal.mysql.permission.query;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Set;

/** PM-01 / INT-09：同一有效公司角色授权行上的用户查询。 */
@Value
@Builder
public class CompanyRoleUserPageQuery {
    Long companyId;
    String roleCode;
    Set<Long> userIds;
    String keyword;
    Integer enabledStatus;
    LocalDateTime currentTime;
    Long offset;
    Integer limit;
}
