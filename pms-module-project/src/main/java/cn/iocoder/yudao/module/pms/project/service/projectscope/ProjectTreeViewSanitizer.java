package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ProjectTreeViewSanitizer {

    public ProjectTreeNodeView sanitize(ProjectMasterDO project, ProjectTreeScopeService.Visibility visibility) {
        return sanitize(project, visibility, null);
    }

    public ProjectTreeNodeView sanitize(ProjectMasterDO project, ProjectTreeScopeService.Visibility visibility, Long parentId) {
        if (visibility == ProjectTreeScopeService.Visibility.NONE) return null;
        if (visibility == ProjectTreeScopeService.Visibility.PATH_PLACEHOLDER) {
            return new ProjectTreeNodeView(project.getId(), null, null, null, null, visibility.name(), parentId, null, null, null, null);
        }
        return new ProjectTreeNodeView(project.getId(), project.getProjectName(), project.getLifecycleStatus(),
                project.getCurrentStage(), project.getProgress(), visibility.name(), parentId, project.getProjectCode(), null, null, null);
    }

    public record ProjectTreeNodeView(Long projectId, String projectName, String lifecycleStatus,
                                      String currentStage, BigDecimal milestoneProgress, String visibility,
                                      Long parentId, String projectCode, BigDecimal projectProgress, String progressStatus,
                                      java.time.LocalDateTime progressRecordedAt) {
        public ProjectTreeNodeView withProgress(cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeProgressRow progress) {
            if (progress == null || !"FULL".equals(visibility)) return this;
            return new ProjectTreeNodeView(projectId, projectName, lifecycleStatus, currentStage, milestoneProgress,
                    visibility, parentId, projectCode, progress.progress(), progress.status(), progress.recordedAt());
        }
    }
}
