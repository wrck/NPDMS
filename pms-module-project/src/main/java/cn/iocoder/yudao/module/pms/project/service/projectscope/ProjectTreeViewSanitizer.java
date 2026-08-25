package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ProjectTreeViewSanitizer {

    public ProjectTreeNodeView sanitize(ProjectMasterDO project, ProjectTreeScopeService.Visibility visibility) {
        if (visibility == ProjectTreeScopeService.Visibility.NONE) return null;
        if (visibility == ProjectTreeScopeService.Visibility.PATH_PLACEHOLDER) {
            return new ProjectTreeNodeView(project.getId(), null, null, null, null, visibility.name());
        }
        return new ProjectTreeNodeView(project.getId(), project.getProjectName(), project.getLifecycleStatus(),
                project.getCurrentStage(), project.getProgress(), visibility.name());
    }

    public record ProjectTreeNodeView(Long projectId, String projectName, String lifecycleStatus,
                                      String currentStage, BigDecimal milestoneProgress, String visibility) {}
}
