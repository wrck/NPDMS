package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import cn.iocoder.yudao.module.pms.service.service.inspection.umc.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/inspection-tasks/{taskId}/umc/actions")
public class InspectionUmcWorkflowController {
    private final InspectionUmcWorkflow workflow;
    private final InspectionUmcService umc;
    private final InspectionCommandExecutor commands;
    public record Explanation(String explanation) { }
    public record Fetch(Long executionId) { }
    private record Fingerprint(Long version, Object body) { }
    private record Write(InspectionRequestContext context, Long taskId, String operation, Object body, Supplier<InspectionViews.TaskSummary> action) { }
    @PostMapping("/start-offline")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    public CommonResult<InspectionViews.TaskSummary> start(@PathVariable Long taskId, @RequestHeader HttpHeaders headers, @RequestBody Explanation body) {
        var context = InspectionRequestContext.write(headers);
        return write(new Write(context, taskId, "UMC_START_OFFLINE", body, () -> workflow.start(new InspectionUmcWorkflow.Command(context, taskId, body.explanation()))));
    }
    @PostMapping("/fetch-result")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    public CommonResult<InspectionViews.TaskSummary> fetch(@PathVariable Long taskId, @RequestHeader HttpHeaders headers, @RequestBody Fetch body) {
        var context = InspectionRequestContext.write(headers); var request = new InspectionUmcWorkflow.Fetch(context, taskId, body.executionId());
        // Remote recovery completes before the short, idempotent result-registration transaction.
        var collection = workflow.collection(request);
        if (collection != null) umc.fromCollection(collection);
        else umc.run(workflow.source(request));
        return write(new Write(context, taskId, "UMC_CAPTURE_RESULT", body, () -> workflow.capture(request)));
    }
    @PostMapping("/complete-execution")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    public CommonResult<InspectionViews.TaskSummary> finish(@PathVariable Long taskId, @RequestHeader HttpHeaders headers) {
        var context = InspectionRequestContext.write(headers);
        return write(new Write(context, taskId, "UMC_COMPLETE_EXECUTION", "complete", () -> workflow.finish(new InspectionUmcWorkflow.Command(context, taskId, null))));
    }
    @PostMapping("/adopt-reports")
    @PreAuthorize("@ss.hasPermission('pms:inspection-report:generate')")
    public CommonResult<InspectionViews.TaskSummary> adopt(@PathVariable Long taskId, @RequestHeader HttpHeaders headers, @RequestBody Explanation body) {
        var context = InspectionRequestContext.write(headers);
        return write(new Write(context, taskId, "UMC_ADOPT_REPORTS", body, () -> workflow.adopt(new InspectionUmcWorkflow.Command(context, taskId, body.explanation()))));
    }
    private CommonResult<InspectionViews.TaskSummary> write(Write request) {
        return success(commands.execute(InspectionCommandExecutor.Write.<InspectionViews.TaskSummary>builder().context(request.context())
                .operation(request.operation()).scope(request.taskId().toString()).input(new Fingerprint(request.context().requireVersion(), request.body()))
                .responseType(InspectionViews.TaskSummary.class).action(request.action()).resourceKey(r -> r.getId().toString()).build()));
    }
}
