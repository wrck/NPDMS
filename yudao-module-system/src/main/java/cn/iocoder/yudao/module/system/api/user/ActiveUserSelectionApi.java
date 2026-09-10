package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import java.util.Set;

/** 专项成员选择：调用方校验项目权限，SYSTEM按实际系统角色提供当前租户有效人员。 */
public interface ActiveUserSelectionApi {
    PageResult<User> page(Query query);

    record Query(int pageNo, int pageSize, String keyword, Set<Long> userIds,
                 String roleCode, Qualification qualification) { }
    record Qualification(Long companyId, Long departmentId, String departmentCode, String scopeRole) { }
    record User(Long id, String username, String nickname, Long deptId) { }
}
