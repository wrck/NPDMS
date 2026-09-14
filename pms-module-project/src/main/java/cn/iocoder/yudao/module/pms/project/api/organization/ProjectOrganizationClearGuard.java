package cn.iocoder.yudao.module.pms.project.api.organization;

import cn.iocoder.yudao.module.system.api.organization.OrganizationClearGuard;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectOrganizationClearMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** PROJ retains project and member organization history; SYSTEM never reads PROJ tables. */
@Component @RequiredArgsConstructor
public class ProjectOrganizationClearGuard implements OrganizationClearGuard {
    private final ProjectOrganizationClearMapper mapper;
    public void check(Scope scope) {
        if(scope.companyIds().isEmpty()&&scope.departmentIds().isEmpty())return;
        long count=mapper.selectReferenceCount(scope);
        if(count>0)throw new IllegalArgumentException("不能清空公司/部门：项目主档、组织关系或成员快照仍有 "+count+" 条引用，需先由项目业务处理，不能改写历史快照");
    }
}
