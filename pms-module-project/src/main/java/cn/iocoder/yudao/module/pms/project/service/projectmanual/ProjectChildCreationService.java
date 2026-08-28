package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectChildCreationService {
    private final ProjectManualCreationService projectCreationService;
    private final DeptApi deptApi;

    public ProjectMasterDO create(ProjectMasterDO parent, ProjectSplitItemDO item,
                                  Long tenantId, Long splitRequestId) {
        ProjectMasterDO draft = new ProjectMasterDO();
        draft.setTenantId(tenantId);
        draft.setParentId(parent.getId());
        draft.setProjectName(item.getProjectName());
        draft.setBusinessLevelCode(item.getBusinessLevelCode());
        draft.setTreeSort(item.getTreeSort());
        draft.setCreationReason("PROJECT_SPLIT:" + splitRequestId);
        draft.setCompanyId(parent.getCompanyId());
        draft.setCompanyCode(parent.getCompanyCode());
        draft.setCompanyName(parent.getCompanyName());
        String officeCode = item.getOfficeDepartmentCode() == null
                ? parent.getDepartmentCode() : item.getOfficeDepartmentCode();
        DeptRespDTO department = officeCode == null ? null : deptApi.getDeptByCode(officeCode);
        if (officeCode != null && department == null) {
            throw new IllegalStateException("项目拆分办事处权威数据不可用");
        }
        if (department != null) {
            draft.setDepartmentId(department.getId());
            draft.setDepartmentCode(department.getCode());
            draft.setDepartmentName(department.getName());
        } else {
            draft.setDepartmentId(parent.getDepartmentId());
            draft.setDepartmentCode(parent.getDepartmentCode());
            draft.setDepartmentName(parent.getDepartmentName());
        }
        return projectCreationService.createProject(draft, parent.getCompanyCode(), officeCode,
                null, null, null);
    }
}
