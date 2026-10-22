package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.projection.ProjectStatistics;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStatisticsQuery;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW;

@Service
@RequiredArgsConstructor
public class ProjectStatisticsService {
    private final ProjectTreeScopeService scope;
    private final ProjectMasterMapper projects;

    public ProjectStatistics getStatistics(ProjectAccessActor actor) {
        var visibleIds = scope.resolveAllFullProjectIds(actor.tenantId(), actor.actorId(), ACTION_VIEW);
        if (visibleIds.isEmpty()) return new ProjectStatistics();
        return projects.selectStatistics(new ProjectStatisticsQuery(actor.tenantId(), visibleIds, LocalDateTime.now()));
    }
}
