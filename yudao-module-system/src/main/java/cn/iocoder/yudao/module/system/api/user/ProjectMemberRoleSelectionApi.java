package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi.MemberOption;

import java.util.Set;

/** 项目列表筛选的角色候选；不产生项目读取、成员或指派资格。 */
public interface ProjectMemberRoleSelectionApi {

    PageResult<MemberOption> page(Query query);

    /** roles由PROJ按固定field映射；不接受HTTP传入的角色、主体、租户或项目范围。 */
    record Query(int pageNo, int pageSize, String keyword, Set<String> roleCodes) { }

}
