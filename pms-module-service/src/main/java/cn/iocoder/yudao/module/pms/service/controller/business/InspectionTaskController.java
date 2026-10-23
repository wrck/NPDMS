package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/inspection-tasks")
public class InspectionTaskController {
    private final InspectionTaskService tasks;
    private final InspectionTaskQueryService queries;
    private final InspectionCommandExecutor executor;

    public record Create(String taskName, @NotNull Long projectId,
                         @NotEmpty @Size(max = 100) List<Long> deviceIds, @NotBlank @Size(max = 8000) String background,
                         LocalDateTime plannedAt, @NotBlank String mode) { }
    public record Preparation(@NotBlank @Size(max = 282) String taskName, @NotBlank @Size(max = 8000) String background,
                              LocalDateTime plannedAt, @NotBlank String mode, List<InspectionTaskService.RuleChoice> rules) { }
    public record Reason(@Size(max = 2000) String reason) { }
    public record Assignment(@NotNull Long ownerUserId, @NotBlank @Size(max = 2000) String reason) { }
    private record Input(Long version, Object body) { }
    private record Write(InspectionRequestContext context, Long taskId, String action, Object body,
                         Supplier<InspectionViews.TaskSummary> execute) { }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:query')")
    public CommonResult<PageResult<InspectionViews.TaskSummary>> page(@Valid InspectionTaskQueryService.PageQuery query) {
        return success(queries.page(query));
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:query')")
    public CommonResult<InspectionViews.TaskDetail> detail(@PathVariable Long taskId) {
        return success(queries.detail(new InspectionTaskQueryService.Detail(InspectionRequestContext.current(), taskId)));
    }

    @GetMapping("/workbench")
    @PreAuthorize("@ss.hasAnyPermissions('pms:inspection-task:query','pms:service-issue:query')")
    public CommonResult<InspectionViews.Workbench> workbench() {
        return success(queries.workbench(InspectionRequestContext.current()));
    }

    @GetMapping("/{taskId}/attachments")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:query') and @ss.hasPermission('pms:file:query')")
    public CommonResult<List<InspectionViews.AttachmentView>> attachments(@PathVariable Long taskId) {
        return success(queries.attachments(new InspectionTaskQueryService.Detail(InspectionRequestContext.current(), taskId)));
    }

    @GetMapping("/project-options")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:query')")
    public CommonResult<List<InspectionTaskQueryService.ProjectOption>> projects(@RequestParam(required = false) String keyword) {
        return success(queries.projects(new InspectionTaskQueryService.ProjectOptions(InspectionRequestContext.current(), keyword)));
    }

    @GetMapping("/device-options")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:query')")
    public CommonResult<List<InspectionTaskQueryService.DeviceOption>> devices(@RequestParam Long projectId) {
        return success(queries.devices(new InspectionTaskQueryService.DeviceOptions(InspectionRequestContext.current(), projectId)));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:create')")
    public CommonResult<InspectionViews.TaskSummary> create(@RequestHeader HttpHeaders headers, @Valid @RequestBody Create input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, null, "TASK_CREATE", input, () -> tasks.create(InspectionTaskService.Create.builder()
                .context(context).taskName(input.taskName()).projectId(input.projectId()).deviceIds(input.deviceIds())
                .background(input.background()).plannedAt(input.plannedAt()).mode(input.mode()).build())));
    }

    @PutMapping("/{taskId}/preparation")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:prepare')")
    public CommonResult<InspectionViews.TaskSummary> prepare(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
                                                           @Valid @RequestBody Preparation input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, taskId, "TASK_PREPARE", input, () -> tasks.savePreparation(InspectionTaskService.Preparation.builder()
                .context(context).taskId(taskId).taskName(input.taskName()).background(input.background()).plannedAt(input.plannedAt())
                .mode(input.mode()).rules(input.rules()).build())));
    }

    @PostMapping("/{taskId}/actions/start-attempt")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    public CommonResult<InspectionViews.TaskSummary> start(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
                                                         @Valid @RequestBody Reason input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, taskId, "TASK_START", input, () -> tasks.start(InspectionTaskService.Start.builder()
                .context(context).taskId(taskId).reason(input.reason()).build())));
    }

    @PostMapping("/{taskId}/actions/assign")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:assign')")
    public CommonResult<InspectionViews.TaskSummary> assign(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
                                                          @Valid @RequestBody Assignment input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, taskId, "TASK_ASSIGN", input, () -> tasks.assign(
                new InspectionTaskService.Assignment(context, taskId, input.ownerUserId(), input.reason()))));
    }

    @PostMapping("/{taskId}/actions/cancel")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:cancel')")
    public CommonResult<InspectionViews.TaskSummary> cancel(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
                                                          @Valid @RequestBody Reason input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, taskId, "TASK_CANCEL", input, () -> tasks.cancel(
                new InspectionTaskService.Cancellation(context, taskId, input.reason()))));
    }

    private CommonResult<InspectionViews.TaskSummary> execute(Write request) {
        var context = request.context();
        Long version = request.taskId() == null ? null : context.requireVersion();
        return success(executor.execute(InspectionCommandExecutor.Write.<InspectionViews.TaskSummary>builder()
                .context(context).operation(request.action()).scope(request.taskId() == null ? "tasks" : request.taskId().toString())
                .input(new Input(version, request.body())).responseType(InspectionViews.TaskSummary.class)
                .action(request.execute()).resourceKey(result -> result.getId().toString()).build()));
    }
}
