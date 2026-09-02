package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskDetailRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskAssigneeCandidateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskAssigneeCandidateRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskAssignReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskActionReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskCreateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskDependencyReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskMoveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskTreeQueryReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskTreeRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskUpdateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskWorkbenchRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectWorkspaceRespVO;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskQueryService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskAssignmentService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskCommandService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskProgressService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskWorkbenchActor;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.AddDependencyCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.AssignTaskCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.CreateTaskCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.MoveTaskCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.UpdateTaskCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.UpdateTaskProgressCommand;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_COMMAND_INVALID;

@Tag(name = "管理后台 - PMS 项目任务工作台")
@RestController
@RequestMapping("/api/v1/pms")
@Validated
@RequiredArgsConstructor
public class ProjectTaskWorkbenchController {

    private final ProjectTaskQueryService queryService;
    private final ProjectTaskCommandService commandService;
    private final ProjectTaskAssignmentService assignmentService;
    private final ProjectTaskLifecycleService lifecycleService;
    private final ProjectTaskProgressService progressService;
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

    @GetMapping("/project-tasks/{id}/assignee-candidates")
    @Operation(summary = "分页查询项目任务负责人候选")
    @PreAuthorize("@ss.hasPermission('pms:project-task:assign')")
    public CommonResult<PageResult<ProjectTaskAssigneeCandidateRespVO>>
            getAssigneeCandidates(@PathVariable("id") Long taskId,
                                  @Valid @ModelAttribute ProjectTaskAssigneeCandidateReqVO request) {
        return withTrustedTenant(() -> success(assignmentService.getAssigneeCandidates(taskId, request, actor())));
    }

    @PostMapping("/project-tasks/{id}/actions/assign")
    @Operation(summary = "指派或转派项目任务负责人")
    @PreAuthorize("@ss.hasPermission('pms:project-task:assign')")
    public CommonResult<TaskCommandResult> assignTask(
            @PathVariable("id") Long taskId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ProjectTaskAssignReqVO request) {
        int expectedVersion = parseIfMatch(ifMatch);
        return withTrustedTenant(() -> success(assignmentService.assign(new AssignTaskCommand(taskId,
                expectedVersion, request.getAssigneeUserId(), request.getReason(), idempotencyKey,
                digest(taskId + ":" + expectedVersion + ":" + JsonUtils.toJsonString(request))), actor())));
    }

    @PostMapping("/projects/{id}/tasks")
    @Operation(summary = "创建TASK_NATIVE项目任务")
    @PreAuthorize("@ss.hasPermission('pms:project-task:create')")
    public CommonResult<TaskCommandResult> createTask(
            @PathVariable("id") Long projectId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody ProjectTaskCreateReqVO request) {
        return withTrustedTenant(() -> success(commandService.create(new CreateTaskCommand(projectId,
                request.getTaskCode(), request.getName(), request.getStageCode(), request.getParentTaskId(),
                request.getBusinessLevelCode(), request.getPlanStartTime(), request.getPlanEndTime(),
                request.getPriority(), request.getSortOrder(), request.getDescription(), idempotencyKey,
                digest(projectId + ":" + JsonUtils.toJsonString(request))), actor())));
    }

    @PatchMapping("/project-tasks/{id}")
    @Operation(summary = "更新项目任务基础信息")
    @PreAuthorize("@ss.hasPermission('pms:project-task:update') or "
            + "@ss.hasPermission('pms:project-task:execute')")
    public CommonResult<TaskCommandResult> updateTask(
            @PathVariable("id") Long taskId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ProjectTaskUpdateReqVO request) {
        int expectedVersion = parseIfMatch(ifMatch);
        return withTrustedTenant(() -> {
            if (request.isProgressSubmitted()) {
                if (!request.isProgressOnly()) throw exception(PROJECT_TASK_COMMAND_INVALID);
                return success(progressService.updateProgress(new UpdateTaskProgressCommand(
                        taskId, expectedVersion, request.getProgress()), actor()));
            }
            return success(commandService.update(new UpdateTaskCommand(taskId,
                    expectedVersion, request.getName(), request.getBusinessLevelCode(), request.getPlanStartTime(),
                    request.getPlanEndTime(), request.getPriority(), request.getSortOrder(), request.getDescription(),
                    request.getSubmittedFields()), actor()));
        });
    }

    @PostMapping("/project-tasks/{id}/actions/move")
    @Operation(summary = "移动项目任务")
    @PreAuthorize("@ss.hasPermission('pms:project-task:move')")
    public CommonResult<TaskCommandResult> moveTask(
            @PathVariable("id") Long taskId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ProjectTaskMoveReqVO request) {
        int expectedVersion = parseIfMatch(ifMatch);
        return withTrustedTenant(() -> success(commandService.move(new MoveTaskCommand(taskId, expectedVersion,
                request.getTargetParentTaskId(), request.getExpectedTaskTreeVersion(), request.getReason(),
                idempotencyKey, digest(taskId + ":" + expectedVersion + ":" + JsonUtils.toJsonString(request))),
                actor())));
    }

    @PostMapping("/project-tasks/{id}/dependencies")
    @Operation(summary = "新增项目任务基础依赖")
    @PreAuthorize("@ss.hasPermission('pms:project-task:move')")
    public CommonResult<TaskCommandResult> addDependency(
            @PathVariable("id") Long taskId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ProjectTaskDependencyReqVO request) {
        int expectedVersion = parseIfMatch(ifMatch);
        return withTrustedTenant(() -> success(commandService.addDependency(new AddDependencyCommand(taskId,
                expectedVersion, request.getPredecessorTaskId(), request.getDependencyTypeCode(), idempotencyKey,
                digest(taskId + ":" + expectedVersion + ":" + JsonUtils.toJsonString(request))), actor())));
    }

    @PostMapping("/project-tasks/{id}/actions/{action}")
    @Operation(summary = "执行TASK_NATIVE任务动作")
    @PreAuthorize("@ss.hasPermission('pms:project-task:execute') or "
            + "@ss.hasPermission('pms:project-task:complete')")
    public CommonResult<TaskCommandResult> actTask(
            @PathVariable("id") Long taskId,
            @PathVariable("action") @Pattern(regexp = "start|submit|complete|cancel") String action,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ProjectTaskActionReqVO request) {
        int expectedVersion = parseIfMatch(ifMatch);
        return withTrustedTenant(() -> success(lifecycleService.act(new TaskActionCommand(taskId,
                expectedVersion, action, request.getReason(), request.getExecutionContractId(),
                request.getContractVersion(), request.getFactObjectKey(), request.getFactVersion(),
                request.getExpectedActivityVersion(), request.getExpectedReportVersion(),
                idempotencyKey, digest(taskId + ":" + expectedVersion + ":" + action + ":"
                + JsonUtils.toJsonString(request))), actor())));
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

    private int parseIfMatch(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("W/")) normalized = normalized.substring(2).trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            int version = Integer.parseInt(normalized);
            if (version < 0) throw new NumberFormatException();
            return version;
        } catch (NumberFormatException ex) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
    }

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
