package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskDetailRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskTreeQueryReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskTreeRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskWorkbenchRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectWorkspaceRespVO;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskQueryService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskWorkbenchActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;

@Tag(name = "管理后台 - PMS 项目任务工作台")
@RestController
@RequestMapping("/api/v1/pms")
@Validated
@RequiredArgsConstructor
public class ProjectTaskWorkbenchController {

    private final ProjectTaskQueryService queryService;
    private final Environment environment;

    @GetMapping("/projects/{id}/workspace")
    @Operation(summary = "查询项目任务工作区")
    @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<ProjectWorkspaceRespVO> getWorkspace(@PathVariable("id") Long projectId) {
        return withTrustedTenant(() -> success(queryService.getWorkspace(projectId, actor())));
    }

    @GetMapping("/projects/{id}/tasks")
    @Operation(summary = "按模式查询项目任务树")
    @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<ProjectTaskTreeRespVO> getTasks(
            @PathVariable("id") Long projectId,
            @Valid @ModelAttribute ProjectTaskTreeQueryReqVO request) {
        return withTrustedTenant(() -> success(queryService.getTasks(projectId, request, actor())));
    }

    @GetMapping("/project-tasks/{id}")
    @Operation(summary = "查询项目任务详情")
    @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<ProjectTaskDetailRespVO> getTask(@PathVariable("id") Long taskId) {
        return withTrustedTenant(() -> success(queryService.getTask(taskId, actor())));
    }

    @GetMapping("/project-tasks/{id}/workbench")
    @Operation(summary = "查询项目任务工作台")
    @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<ProjectTaskWorkbenchRespVO> getWorkbench(@PathVariable("id") Long taskId) {
        return withTrustedTenant(() -> success(queryService.getWorkbench(taskId, actor())));
    }

    private TaskWorkbenchActor actor() {
        return new TaskWorkbenchActor(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString());
    }

    private <T> T withTrustedTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
