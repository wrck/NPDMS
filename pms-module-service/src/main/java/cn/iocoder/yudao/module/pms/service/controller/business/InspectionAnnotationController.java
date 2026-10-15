package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/inspection-tasks/{taskId}")
public class InspectionAnnotationController {
    private final InspectionAnnotationService annotations;
    private final InspectionCommandExecutor commands;
    public record Annotate(@NotNull Long reportId, @NotNull Long resultId, @NotBlank String kind,
                           @NotBlank @Size(max = 2000) String reason, @Size(max = 2000) String changeReason,
                           Long responsibleUserId, LocalDateTime dueAt) { }
    private record Input(Long version, Object body) { }
    private record Write(InspectionRequestContext context, Long taskId, String action, Object input,
                         Supplier<InspectionViews.TaskSummary> execute) { }

    @PostMapping("/annotations")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:annotate')")
    public CommonResult<InspectionViews.TaskSummary> annotate(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
                                                            @Valid @RequestBody Annotate input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, taskId, "ANNOTATE_RESULT", input, () -> annotations.annotate(
                InspectionAnnotationService.Annotate.builder().context(context).taskId(taskId).reportId(input.reportId())
                        .resultId(input.resultId()).kind(input.kind()).reason(input.reason()).changeReason(input.changeReason())
                        .responsibleUserId(input.responsibleUserId()).dueAt(input.dueAt()).build())));
    }

    @PostMapping("/actions/complete-annotations")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:annotate')")
    public CommonResult<InspectionViews.TaskSummary> complete(@PathVariable Long taskId, @RequestHeader HttpHeaders headers) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, taskId, "ANNOTATIONS_COMPLETE", null,
                () -> annotations.complete(new InspectionAnnotationService.Complete(context, taskId))));
    }
    private CommonResult<InspectionViews.TaskSummary> execute(Write request) {
        return success(commands.execute(InspectionCommandExecutor.Write.<InspectionViews.TaskSummary>builder()
                .context(request.context()).operation(request.action()).scope(request.taskId().toString())
                .input(new Input(request.context().requireVersion(), request.input())).responseType(InspectionViews.TaskSummary.class)
                .action(request.execute()).resourceKey(result -> result.getId().toString()).build()));
    }
}
