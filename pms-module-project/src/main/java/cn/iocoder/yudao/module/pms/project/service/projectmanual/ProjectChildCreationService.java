package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateSelectionService;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectChildCreationService {
    private final ProjectManualCreationService projectCreationService;
    private final ProjectChildDraftFactory drafts;
    private final ProjectTemplateSelectionService templates;
    private final OperationAuditApi audit;

    @Transactional(rollbackFor = Exception.class)
    public ProjectMasterDO create(ProjectMasterDO parent, ProjectSplitItemDO item,
                                  Long tenantId, Long splitRequestId, Long actorId, String correlationId) {
        if (!Objects.equals(parent.getTenantId(), tenantId))
            throw new IllegalArgumentException("子项目与父项目租户不一致");
        ProjectMasterDO draft = drafts.create(parent, item);
        draft.setCreationReason("PROJECT_SPLIT:" + splitRequestId);
        var selection = templates.select(draft, item.getTemplateRevisionId(), item.getTemplateSelectionReason(), actorId);
        ProjectMasterDO created = projectCreationService.createProject(draft, draft.getCompanyCode(), draft.getDepartmentCode(),
                selection.revision().getId(), null, null);
        audit.record(tenantId, actorId, correlationId, "PROJECT_CHILD_TEMPLATE_SELECT", created.getId(), "SUCCESS",
                Map.of("splitRequestId", splitRequestId, "parentProjectId", parent.getId(),
                        "templateRevisionId", selection.revision().getId(), "override", selection.override(),
                        "reason", item.getTemplateSelectionReason() == null ? "" : item.getTemplateSelectionReason()));
        return created;
    }
}
