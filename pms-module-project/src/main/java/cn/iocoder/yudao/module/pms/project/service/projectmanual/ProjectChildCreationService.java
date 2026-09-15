package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateSelectionService;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectChildCreationService {
    private final ProjectManualCreationService projectCreationService;
    private final DeptApi deptApi;
    private final ProjectTemplateSelectionService templates;
    private final OperationAuditApi audit;

    @Transactional(rollbackFor = Exception.class)
    public ProjectMasterDO create(ProjectMasterDO parent, ProjectSplitItemDO item,
                                  Long tenantId, Long splitRequestId, Long actorId, String correlationId) {
        if (!Objects.equals(parent.getTenantId(), tenantId))
            throw new IllegalArgumentException("子项目与父项目租户不一致");
        var selection = templates.select(parent, item.getTemplateRevisionId(), item.getTemplateSelectionReason(), actorId);
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
        ProjectMasterDO created = projectCreationService.createProject(draft, parent.getCompanyCode(), officeCode,
                selection.revision().getId(), null, null);
        audit.record(tenantId, actorId, correlationId, "PROJECT_CHILD_TEMPLATE_SELECT", created.getId(), "SUCCESS",
                Map.of("splitRequestId", splitRequestId, "parentProjectId", parent.getId(),
                        "templateRevisionId", selection.revision().getId(), "override", selection.override(),
                        "reason", item.getTemplateSelectionReason() == null ? "" : item.getTemplateSelectionReason()));
        return created;
    }
}
