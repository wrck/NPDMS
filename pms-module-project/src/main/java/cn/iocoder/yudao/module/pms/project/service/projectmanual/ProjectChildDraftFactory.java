package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The same prospective child is used by option listing, split preview and creation. */
@Component
@RequiredArgsConstructor
public class ProjectChildDraftFactory {
    private final DeptApi departments;

    public ProjectMasterDO create(ProjectMasterDO parent, ProjectSplitItemDO item) {
        var draft = new ProjectMasterDO();
        draft.setTenantId(parent.getTenantId());
        draft.setParentId(parent.getId());
        draft.setProjectName(item.getProjectName());
        draft.setBusinessLevelCode(item.getBusinessLevelCode());
        draft.setTreeSort(item.getTreeSort());
        draft.setCompanyId(parent.getCompanyId());
        draft.setCompanyCode(parent.getCompanyCode());
        draft.setCompanyName(parent.getCompanyName());
        String office = item.getDepartmentCode() == null ? parent.getDepartmentCode() : item.getDepartmentCode();
        var department = office == null ? null : departments.getDeptByCode(office);
        if (office != null && department == null) throw new IllegalStateException("项目拆分办事处权威数据不可用");
        draft.setDepartmentId(department == null ? parent.getDepartmentId() : department.getId());
        draft.setDepartmentCode(department == null ? parent.getDepartmentCode() : department.getCode());
        draft.setDepartmentName(department == null ? parent.getDepartmentName() : department.getName());
        ProjectRules.inheritChildAttributes(draft, parent);
        return draft;
    }
}
