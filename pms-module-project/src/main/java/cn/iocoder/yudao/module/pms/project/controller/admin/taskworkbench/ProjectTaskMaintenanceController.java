package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskMaintenanceService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskMaintenanceService.*;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskWorkbenchActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @Validated @RequiredArgsConstructor @RequestMapping("/api/v1/pms")
public class ProjectTaskMaintenanceController {
    private final ProjectTaskMaintenanceService service;
    private final cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskCommandService commands;
    public record RoleRequest(@NotNull Role role, @NotNull Long userId, @NotBlank @Size(max=500) String reason) { }
    public record DescriptionRequest(@NotNull @Size(max=cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskRichText.MAX_LENGTH) String html) { }
    public record NativeTaskRequest(@NotNull @Valid cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskCreateReqVO task,
                                    @Size(max=cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskRichText.MAX_LENGTH) String descriptionHtml) { }
    @PostMapping("/projects/{id}/tasks/native") @PreAuthorize("@ss.hasPermission('pms:project-task:create')")
    public CommonResult<?> create(@PathVariable Long id, @RequestHeader("Idempotency-Key") @NotBlank @Size(max=128) String key,
                                   @Valid @RequestBody NativeTaskRequest body) {
        var input = body.task();
        if (input.getDescription() != null) throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_COMMAND_INVALID);
        return success(commands.create(new cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.CreateTaskCommand(
                id,input.getTaskCode(),input.getName(),input.getStageCode(),input.getParentTaskId(),input.getBusinessLevelCode(),
                input.getPlanStartTime(),input.getPlanEndTime(),input.getPriority(),input.getSortOrder(),body.descriptionHtml(),
                cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskRichText.HTML,key,
                cn.hutool.crypto.digest.DigestUtil.sha256Hex(id + ":" + cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(body))),actor()));
    }
    @GetMapping("/project-tasks/{id}/maintenance") @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<View> get(@PathVariable Long id) { return success(service.get(id, actor())); }
    @GetMapping("/projects/{id}/task-members") @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<?> members(@PathVariable Long id, @RequestParam(required=false) String keyword,
                                  @RequestParam(defaultValue="1") @Min(1) int pageNo,
                                  @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return success(service.candidates(id, keyword, pageNo, pageSize, actor()));
    }
    @GetMapping("/project-tasks/{id}/role-history") @PreAuthorize("@ss.hasPermission('pms:project-task:query')")
    public CommonResult<HistoryPage> history(@PathVariable Long id, @RequestParam Role role,
                                           @RequestParam(defaultValue="1") @Min(1) int pageNo,
                                           @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return success(service.history(id, role, pageNo, pageSize, actor()));
    }
    @PutMapping("/project-tasks/{id}/roles") @PreAuthorize("@ss.hasPermission('pms:project-task:assign')")
    public CommonResult<?> role(@PathVariable Long id, @RequestHeader("If-Match") @Min(0) Integer version,
                                @RequestHeader("Idempotency-Key") @NotBlank @Size(max=128) String key, @Valid @RequestBody RoleRequest body) {
        return success(service.changeRole(new RoleChange(id,version,body.role(),body.userId(),body.reason(),key),actor()));
    }
    @PutMapping("/project-tasks/{id}/description") @PreAuthorize("@ss.hasPermission('pms:project-task:update')")
    public CommonResult<?> description(@PathVariable Long id, @RequestHeader("If-Match") @Min(0) Integer version,
                                       @RequestHeader("Idempotency-Key") @NotBlank @Size(max=128) String key, @Valid @RequestBody DescriptionRequest body) {
        return success(service.saveDescription(new DescriptionChange(id,version,body.html(),key),actor()));
    }
    private TaskWorkbenchActor actor() { return new TaskWorkbenchActor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString()); }
}
