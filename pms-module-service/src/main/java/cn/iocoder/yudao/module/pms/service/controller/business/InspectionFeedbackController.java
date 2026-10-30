package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
public class InspectionFeedbackController {
    private final InspectionFeedbackService feedback;
    private final InspectionCommandExecutor commands;
    public record Confirm(@NotNull Boolean valid, @NotBlank @Size(max = 2000) String reason) { }
    public record Missing(Long deviceId, @NotBlank @Size(max = 8000) String description) { }
    public record Optimize(@NotBlank @Size(max = 2000) String reason) { }
    private record Input(Long version, Object body) { }
    @GetMapping("/api/v1/pms/inspection-rules/feedback")
    @PreAuthorize("@ss.hasPermission('pms:inspection-feedback:query')")
    public CommonResult<InspectionFeedbackService.FeedbackPage> page(@Valid InspectionFeedbackService.PageQuery query) {
        return success(feedback.page(new InspectionFeedbackService.ScopeQuery(InspectionRequestContext.current(), query)));
    }
    @PostMapping("/api/v1/pms/inspection-rules/feedback/{annotationId}/actions/confirm")
    @PreAuthorize("@ss.hasPermission('pms:inspection-feedback:confirm')")
    public CommonResult<InspectionFeedbackService.Confirmation> confirm(@PathVariable Long annotationId, @RequestHeader HttpHeaders headers,
                                                                       @Valid @RequestBody Confirm input) {
        var context = InspectionRequestContext.write(headers);
        return success(commands.execute(InspectionCommandExecutor.Write.<InspectionFeedbackService.Confirmation>builder()
                .context(context).operation("CONFIRM_FEEDBACK").scope(annotationId.toString()).input(new Input(context.requireVersion(), input))
                .responseType(InspectionFeedbackService.Confirmation.class).action(() -> feedback.confirm(
                        new InspectionFeedbackService.Confirm(context, annotationId, input.valid(), input.reason())))
                .resourceKey(result -> result.annotationId().toString()).build()));
    }
    @PostMapping("/api/v1/pms/inspection-tasks/{taskId}/rule-feedback")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:prepare')")
    public CommonResult<InspectionViews.TaskSummary> missing(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
                                                            @Valid @RequestBody Missing input) {
        var context = InspectionRequestContext.write(headers);
        return success(commands.execute(InspectionCommandExecutor.Write.<InspectionViews.TaskSummary>builder()
                .context(context).operation("MISSING_RULE_FEEDBACK").scope(taskId.toString()).input(new Input(context.requireVersion(), input))
                .responseType(InspectionViews.TaskSummary.class).action(() -> feedback.reportMissing(
                        new InspectionFeedbackService.Missing(context, taskId, input.deviceId(), input.description())))
                .resourceKey(result -> result.getId().toString()).build()));
    }
    @PostMapping("/api/v1/pms/inspection-rules/feedback/{annotationId}/actions/create-rule-draft")
    @PreAuthorize("@ss.hasPermission('pms:inspection-rule:manage')")
    public CommonResult<cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleRevisionService.DraftResult> optimize(
            @PathVariable Long annotationId, @RequestHeader HttpHeaders headers, @Valid @RequestBody Optimize input) {
        var context = InspectionRequestContext.write(headers);
        return success(commands.execute(InspectionCommandExecutor.Write.<cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleRevisionService.DraftResult>builder()
                .context(context).operation("FEEDBACK_RULE_DRAFT").scope(annotationId.toString()).input(new Input(context.requireVersion(), input))
                .responseType(cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleRevisionService.DraftResult.class)
                .action(() -> feedback.createRuleDraft(new InspectionFeedbackService.Optimize(context, annotationId, input.reason())))
                .resourceKey(result -> result.revisionId().toString()).build()));
    }
}
