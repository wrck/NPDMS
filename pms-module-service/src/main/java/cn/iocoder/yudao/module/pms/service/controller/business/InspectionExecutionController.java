package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/inspection-tasks/{taskId}")
public class InspectionExecutionController {
    private final InspectionExecutionService service;
    private final InspectionCommandExecutor commands;
    public record Refresh(Long executionId) { }
    public record Abandon(Long executionId, String reason) { }
    private record UploadFingerprint(Long attemptId, Long deviceId, String fileName, String sha256, String commandHint) { }
    private record Input(Long version, Object body) { }
    private record Write(InspectionRequestContext context, Long taskId, String action, Object input,
                         Supplier<InspectionViews.TaskSummary> execute) { }

    @GetMapping("/commands")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:query')")
    public CommonResult<List<InspectionExecutionService.CommandView>> commandList(@PathVariable Long taskId,
                                                                                       @RequestParam Long deviceId) {
        return success(service.commands(new InspectionExecutionService.CommandList(InspectionRequestContext.current(), taskId, deviceId)));
    }

    @PostMapping(value = "/offline-logs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<InspectionViews.TaskSummary> upload(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
            @RequestParam Long attemptId, @RequestParam Long deviceId, @RequestParam(required = false) String commandHint,
            @RequestPart("file") MultipartFile file) throws IOException {
        InspectionException.require(file != null && file.getSize() > 0 && file.getSize() <= 52_428_800, "日志文件不能为空且不得超过50MB");
        var context = InspectionRequestContext.write(headers); byte[] content = file.getBytes();
        var fingerprint = new UploadFingerprint(attemptId, deviceId, file.getOriginalFilename(), DigestUtil.sha256Hex(content), commandHint);
        try {
            return execute(new Write(context, taskId, "LOG_UPLOAD", fingerprint,
                    () -> service.upload(InspectionExecutionService.Upload.builder().context(context).taskId(taskId)
                            .attemptId(attemptId).deviceId(deviceId).fileName(file.getOriginalFilename()).content(content).commandHint(commandHint).build())));
        } finally { java.util.Arrays.fill(content, (byte) 0); }
    }

    @PostMapping("/actions/refresh-log-parse")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    public CommonResult<InspectionViews.TaskSummary> refresh(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
                                                            @RequestBody Refresh input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, taskId, "LOG_REFRESH", input,
                () -> service.refresh(new InspectionExecutionService.Refresh(context, taskId, input.executionId()))));
    }

    @PostMapping("/actions/abandon-log-parse")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    public CommonResult<InspectionViews.TaskSummary> abandon(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
                                                           @RequestBody Abandon input) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, taskId, "LOG_ABANDON", input, () -> service.abandon(
                new InspectionExecutionService.Abandon(context, taskId, input.executionId(), input.reason()))));
    }

    @PostMapping("/actions/complete-execution")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    public CommonResult<InspectionViews.TaskSummary> finish(@PathVariable Long taskId, @RequestHeader HttpHeaders headers) {
        var context = InspectionRequestContext.write(headers);
        return execute(new Write(context, taskId, "EXECUTION_COMPLETE", null,
                () -> service.finish(new InspectionExecutionService.Finish(context, taskId))));
    }

    private CommonResult<InspectionViews.TaskSummary> execute(Write request) {
        return success(commands.execute(InspectionCommandExecutor.Write.<InspectionViews.TaskSummary>builder()
                .context(request.context()).operation(request.action()).scope(request.taskId().toString())
                .input(new Input(request.context().requireVersion(), request.input())).responseType(InspectionViews.TaskSummary.class)
                .action(request.execute()).resourceKey(result -> result.getId().toString()).build()));
    }
}
