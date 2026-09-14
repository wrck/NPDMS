package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectTaskAdmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** One transaction per node: an unavailable Owner must not roll back a completed independent branch. */
@Service
@RequiredArgsConstructor
public class ProjectBusinessTaskCompletionService {
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectTaskLifecycleService commands;
    private final ProjectTaskBusinessAssociationService associations;
    private final ProjectTaskAdmissionService admission;

    public record Result(int activated, int completed, boolean unknown) { }

    public Result completeEligible(Long projectId, String correlationId) {
        var tasks = graph.selectTasks(new ProjectRuntimeGraphQuery(TenantContextHolder.getRequiredTenantId(), projectId));
        int completed = 0;
        int activated = 0;
        boolean unknown = false;
        for (var task : tasks) {
            try {
                var admitted = admission.activateEligible(projectId, task.getId(), correlationId);
                if (admitted.activated()) activated++;
                if (admitted.unknown()) {
                    unknown = true;
                    continue;
                }
                associations.synchronize(projectId, task.getId(), correlationId);
                var result = commands.completeFromBusinessResult(projectId, task.getId(), correlationId);
                if (result.completed()) completed++;
                unknown |= result.unknown();
            } catch (RuntimeException unavailable) {
                // The proxied node command has rolled back before continuing; Outbox owns the only retry loop.
                unknown = true;
            }
        }
        return new Result(activated, completed, unknown);
    }
}
