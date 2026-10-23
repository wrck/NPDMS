package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/inspection-tasks/{taskId}/online-executions")
public class InspectionOnlineController {
    private final InspectionOnlineExecutionService service;
    private final InspectionCommandExecutor commands;
    public record Reserve(Long deviceId, String purpose, Long precheckExecutionId, InspectionOnlineExecutionService.Connection connection) { }
    public record Authentication(@com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY) char[] password) {
        @Override public String toString() { return "Authentication[credential material omitted]"; }
    }
    public record Reason(String reason) { }
    private record Input(Long version, Reserve body) { }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    public CommonResult<InspectionOnlineExecutionService.Reserved> reserve(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
                                                                          @RequestBody Reserve input) {
        var context = InspectionRequestContext.write(headers);
        return success(commands.execute(InspectionCommandExecutor.Write.<InspectionOnlineExecutionService.Reserved>builder().context(context)
                .operation("ONLINE_RESERVE").scope(taskId.toString()).input(new Input(context.requireVersion(), input))
                .responseType(InspectionOnlineExecutionService.Reserved.class).action(() -> service.reserve(new InspectionOnlineExecutionService.Reserve(
                        context, taskId, input.deviceId(), input.purpose(), input.precheckExecutionId(), input.connection())))
                .resourceKey(r -> r.execution().getId().toString()).build()));
    }

    @PostMapping("/{executionId}/actions/dispatch")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<InspectionOnlineExecutionService.Reserved> dispatch(@PathVariable Long taskId, @PathVariable Long executionId,
            @RequestHeader HttpHeaders headers, @RequestBody Authentication authentication) {
        try {
            var context = InspectionRequestContext.write(headers);
            return success(service.dispatch(new InspectionOnlineExecutionService.Dispatch(context, taskId, executionId, authentication.password())));
        } finally { if (authentication.password() != null) java.util.Arrays.fill(authentication.password(), '\0'); }
    }

    @PostMapping("/{executionId}/actions/refresh")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    public CommonResult<InspectionViews.TaskSummary> refresh(@PathVariable Long taskId, @PathVariable Long executionId, @RequestHeader HttpHeaders headers) {
        return success(service.refresh(new InspectionOnlineExecutionService.Refresh(InspectionRequestContext.write(headers), taskId, executionId)));
    }

    @PostMapping("/{executionId}/actions/cancel")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    public CommonResult<InspectionViews.TaskSummary> cancel(@PathVariable Long taskId, @PathVariable Long executionId,
                                                          @RequestHeader HttpHeaders headers, @RequestBody Reason input) {
        return success(service.cancel(new InspectionOnlineExecutionService.Cancel(InspectionRequestContext.write(headers), taskId, executionId, input.reason())));
    }

    @GetMapping("/{executionId}/log-download")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:query')")
    public CommonResult<String> download(@PathVariable Long taskId, @PathVariable Long executionId) {
        return success(service.download(new InspectionOnlineExecutionService.Refresh(InspectionRequestContext.current(), taskId, executionId)));
    }
}
