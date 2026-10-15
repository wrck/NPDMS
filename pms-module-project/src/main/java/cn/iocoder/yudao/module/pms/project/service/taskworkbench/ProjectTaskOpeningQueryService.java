package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessPageQueryApi;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskWorkbenchRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectWorkspaceRespVO;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectOperationCapabilities;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectOperationCapabilityQueryService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectOperationContextResolver;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Resolves one task once, then passes the authorized observation to its projections and Owner. */
@Service
@RequiredArgsConstructor
public class ProjectTaskOpeningQueryService {
    private final ProjectTaskQueryService tasks;
    private final ProjectTaskBusinessService businesses;
    private final ProjectOperationContextResolver executionContexts;
    private final ProjectOperationCapabilityQueryService capabilities;
    private final TaskBusinessBindingHostProvider businessBinding;

    public record Page(BusinessPageQueryApi.View view, BusinessPageQueryApi.Page data) { }
    public record Opening(ProjectWorkspaceRespVO workspace, ProjectTaskWorkbenchRespVO workbench,
            TaskBusinessContext businessContext, ProjectOperationCapabilities capabilities, Page businessPage) { }

    public Opening open(Long taskId, TaskWorkbenchActor actor) {
        var read = tasks.openTaskRead(taskId, actor);
        var workspace = tasks.getWorkspace(read);
        if (read.contract() == null || !TaskBusinessBindingHostProvider.TYPES.contains(read.contract().getWorkBindingTypeCode()))
            return new Opening(workspace, tasks.openingWorkbench(read, null), null, null, null);
        var execution = executionContexts.resolve(read);
        var context = businesses.open(read, execution.selection() == null ? null : execution.selection().task());
        TaskBindingInspection binding;
        try { binding = businessBinding.inspect(read, context); }
        catch (RuntimeException unavailable) { binding = TaskBindingInspection.failed(read.contract().getWorkBindingTypeCode(), "BINDING_PROVIDER_UNAVAILABLE"); }
        var workbench = tasks.openingWorkbench(read, binding);
        var objectId = context.links().isEmpty() ? null : context.links().getFirst().objectId();
        var operations = capabilities.inspectResolved(execution, objectId, context.businessView());
        return new Opening(workspace, workbench, context, operations, null);
    }
}
