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
@RequestMapping("/api/v1/pms/service-issues")
public class ServiceIssueController {
    private final ServiceIssueService issues;
    private final InspectionCommandExecutor commands;
    public record Process(@NotBlank @Size(max = 8000) String content, @Size(max = 20) List<InspectionFiles.Reference> evidence) { }
    public record Closure(@NotBlank @Size(max = 8000) String description) { }
    private record Input(Long version, Object body) { }
    private record Write(InspectionRequestContext context, Long issueId, String action, Object input,
                         Supplier<InspectionViews.IssueView> execute) { }

    @GetMapping("/{issueId}")
    @PreAuthorize("@ss.hasPermission('pms:service-issue:query')")
    public CommonResult<InspectionViews.IssueDetail> detail(@PathVariable Long issueId) {
        return success(issues.detail(new ServiceIssueService.Query(InspectionRequestContext.current(), issueId)));
    }

    @PostMapping("/{issueId}/actions/record-process")
    @PreAuthorize("@ss.hasPermission('pms:service-issue:remediate')")
    public CommonResult<InspectionViews.IssueView> process(@PathVariable Long issueId, @RequestHeader HttpHeaders headers,
                                                         @Valid @RequestBody Process input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, issueId, "ISSUE_PROCESS", input, () -> issues.process(ServiceIssueService.Process.builder()
                .context(context).issueId(issueId).content(input.content()).evidence(input.evidence()).build())));
    }

    @PostMapping("/{issueId}/actions/request-closure")
    @PreAuthorize("@ss.hasPermission('pms:service-issue:remediate')")
    public CommonResult<InspectionViews.IssueView> request(@PathVariable Long issueId, @RequestHeader HttpHeaders headers,
                                                         @Valid @RequestBody Closure input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, issueId, "ISSUE_REQUEST_CLOSURE", input,
                () -> issues.requestClosure(new ServiceIssueService.Closure(context, issueId, input.description()))));
    }

    @PostMapping("/{issueId}/actions/confirm-closure")
    @PreAuthorize("@ss.hasPermission('pms:service-issue:confirm')")
    public CommonResult<InspectionViews.IssueView> confirm(@PathVariable Long issueId, @RequestHeader HttpHeaders headers,
                                                         @Valid @RequestBody Closure input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, issueId, "ISSUE_CONFIRM_CLOSURE", input,
                () -> issues.confirmClosure(new ServiceIssueService.Closure(context, issueId, input.description()))));
    }

    @PostMapping("/{issueId}/actions/return-closure")
    @PreAuthorize("@ss.hasPermission('pms:service-issue:confirm')")
    public CommonResult<InspectionViews.IssueView> returnClosure(@PathVariable Long issueId, @RequestHeader HttpHeaders headers,
                                                               @Valid @RequestBody Closure input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, issueId, "ISSUE_RETURN_CLOSURE", input,
                () -> issues.returnClosure(new ServiceIssueService.Closure(context, issueId, input.description()))));
    }
    private CommonResult<InspectionViews.IssueView> execute(Write request) {
        return success(commands.execute(InspectionCommandExecutor.Write.<InspectionViews.IssueView>builder()
                .context(request.context()).operation(request.action()).scope(request.issueId().toString())
                .input(new Input(request.context().requireVersion(), request.input())).responseType(InspectionViews.IssueView.class)
                .action(request.execute()).resourceKey(result -> result.getId().toString()).build()));
    }
}
