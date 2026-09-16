package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectRespVO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStatusQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectStatusPresentationService {
    private final ProjectMemberAssignmentMapper members;
    private final ProjectStageInstanceMapper stages;

    /** Only enrich rows already authorized by the project query; no lifecycle writes. */
    public void populate(List<ProjectRespVO> projects) {
        if (projects.isEmpty()) return;
        var query = new ProjectStatusQuery(TenantContextHolder.getRequiredTenantId(),
                projects.stream().map(ProjectRespVO::getId).collect(Collectors.toSet()), LocalDateTime.now());
        var assignments = members.selectStatusAssignments(query);
        var activeStages = stages.selectActiveForStatus(query);
        for (var project : projects) {
            var current = assignments.stream().filter(row -> Objects.equals(row.getProjectId(), project.getId())).toList();
            project.setServiceManagerAssigned(current.stream().anyMatch(row ->
                    ProjectMemberRoles.isServiceManager(row.getMemberRole())
                            && (row.getAssignmentType() == null || "PRIMARY".equals(row.getAssignmentType()))));
            project.setProjectManagerAssigned(project.getManagerId() != null && current.stream().anyMatch(row ->
                    ProjectMemberRoles.PROJECT_MANAGER.equals(row.getMemberRole())
                            && Objects.equals(row.getUserId(), project.getManagerId())));
            project.setActiveStageNames(activeStages.stream().filter(row -> Objects.equals(row.getProjectId(), project.getId()))
                    .map(row -> row.getName()).filter(Objects::nonNull).distinct().toList());
        }
    }
}
