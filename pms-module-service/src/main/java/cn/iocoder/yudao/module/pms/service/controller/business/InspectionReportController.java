package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/inspection-reports")
public class InspectionReportController {
    private final InspectionReportService reports;
    private final InspectionCommandExecutor commands;
    public record Generate(@NotNull Long taskId, @Size(max = 2000) String explanation) { }
    public record Manual(@NotNull Long taskId, @NotBlank @Size(max = 2000) String explanation,
                         @NotEmpty @Size(max = 4) List<InspectionReportService.ManualFile> files) { }
    private record Input(Long version, Object body) { }
    private record Write(InspectionRequestContext context, Long taskId, String action, Object input,
                         Supplier<InspectionViews.TaskSummary> execute) { }

    @PostMapping("/actions/generate")
    @PreAuthorize("@ss.hasPermission('pms:inspection-report:generate')")
    public CommonResult<InspectionViews.TaskSummary> generate(@RequestHeader HttpHeaders headers, @Valid @RequestBody Generate input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, input.taskId(), "REPORT_GENERATE", input,
                () -> reports.generate(new InspectionReportService.Generate(context, input.taskId(), input.explanation()))));
    }

    @PostMapping("/actions/submit-manual")
    @PreAuthorize("@ss.hasPermission('pms:inspection-report:generate')")
    public CommonResult<InspectionViews.TaskSummary> manual(@RequestHeader HttpHeaders headers, @Valid @RequestBody Manual input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, input.taskId(), "REPORT_MANUAL", input,
                () -> reports.manual(new InspectionReportService.Manual(context, input.taskId(), input.explanation(), input.files()))));
    }

    private CommonResult<InspectionViews.TaskSummary> execute(Write request) {
        return success(commands.execute(InspectionCommandExecutor.Write.<InspectionViews.TaskSummary>builder()
                .context(request.context()).operation(request.action()).scope(request.taskId().toString())
                .input(new Input(request.context().requireVersion(), request.input())).responseType(InspectionViews.TaskSummary.class)
                .action(request.execute()).resourceKey(result -> result.getId().toString()).build()));
    }
}
