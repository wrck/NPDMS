package cn.iocoder.yudao.module.pms.project.controller.admin.projects;

import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectRespVO;

/** Overview-only rows use an allowlist, including after presentation enrichments. */
final class ProjectOverviewSanitizer {
    private ProjectOverviewSanitizer() { }

    static ProjectRespVO sanitize(ProjectRespVO row) {
        if (!"SUMMARY".equals(row.getVisibility())) return row;
        var summary = new ProjectRespVO();
        summary.setVisibility("SUMMARY");
        summary.setId(row.getId());
        summary.setProjectCode(row.getProjectCode());
        summary.setProjectName(row.getProjectName());
        summary.setLifecycleStatus(row.getLifecycleStatus());
        summary.setCurrentStage(row.getCurrentStage());
        summary.setStatus(row.getStatus());
        summary.setActiveStageNames(row.getActiveStageNames());
        return summary;
    }
}
