package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/pms/inspection-rules/feedback/exports")
@PreAuthorize("@ss.hasPermission('pms:inspection-feedback:export')")
public class InspectionFeedbackExportController {
    private final InspectionFeedbackExportService exports;
    private final InspectionCommandExecutor commands;
    public record Request(@NotBlank String format, @NotNull @Valid InspectionFeedbackService.PageQuery filter) { }
    @GetMapping public CommonResult<PageResult<InspectionFeedbackExportService.View>> page(@Valid PageParam query) {
        return success(exports.page(new InspectionFeedbackExportService.Page(InspectionRequestContext.current(), query)));
    }
    @GetMapping("/{id}") public CommonResult<InspectionFeedbackExportService.View> get(@PathVariable Long id) {
        return success(exports.get(new InspectionFeedbackExportService.Query(InspectionRequestContext.current(), id, false)));
    }
    @PostMapping public CommonResult<InspectionFeedbackExportService.View> request(@RequestHeader HttpHeaders headers, @RequestBody @Valid Request input) {
        var context = InspectionRequestContext.write(headers);
        return success(commands.execute(InspectionCommandExecutor.Write.<InspectionFeedbackExportService.View>builder()
                .context(context).operation("REQUEST_FEEDBACK_EXPORT").scope("feedback-export").input(input)
                .responseType(InspectionFeedbackExportService.View.class).action(() -> exports.request(new InspectionFeedbackExportService.Request(context, input.filter(), input.format())))
                .resourceKey(result -> result.getId().toString()).build()));
    }
    @PostMapping("/{id}/actions/generate") public CommonResult<InspectionFeedbackExportService.View> generate(@PathVariable Long id, @RequestHeader HttpHeaders headers) {
        var context = InspectionRequestContext.write(headers);
        try {
            return success(commands.execute(InspectionCommandExecutor.Write.<InspectionFeedbackExportService.View>builder()
                    .context(context).operation("GENERATE_FEEDBACK_EXPORT").scope(id.toString()).input(context.requireVersion())
                    .responseType(InspectionFeedbackExportService.View.class).action(() -> exports.generate(new InspectionFeedbackExportService.Query(context, id, true)))
                    .resourceKey(result -> result.getId().toString()).build()));
        } catch (RuntimeException failure) {
            if (!(failure instanceof InspectionException)) {
                try { exports.recordFailure(new InspectionFeedbackExportService.Query(context, id, true)); }
                catch (RuntimeException recordingFailure) { failure.addSuppressed(recordingFailure); }
            }
            throw failure;
        }
    }
}
