package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import cn.iocoder.yudao.module.pms.service.service.inspection.umc.InspectionUmcService;
import cn.iocoder.yudao.module.pms.service.service.inspection.umc.UmcLogDocument;
import cn.iocoder.yudao.module.pms.service.service.inspection.umc.InspectionUmcWorkflow;
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
    private final InspectionUmcService umc;
    private final InspectionUmcWorkflow workflow;
    public record Refresh(Long executionId) { }
    public record Abandon(Long executionId, String reason) { }
    private record UploadFingerprint(Long attemptId, Long deviceId, String fileName, String sha256, String commandHint, String sourceHost) { }
    private record BatchFingerprint(Long attemptId, String fileName, String sha256, List<InspectionUmcService.Host> sourceHosts) { }
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
            @RequestParam(required = false) String sourceHost,
            @RequestPart("file") MultipartFile file) throws IOException {
        InspectionException.require(file != null && file.getSize() > 0 && file.getSize() <= 52_428_800, "日志文件不能为空且不得超过50MB");
        var context = InspectionRequestContext.write(headers);
        if (sourceHost != null) UmcLogDocument.requireIpv4(sourceHost);
        var resolvedSourceHost = sourceHost != null ? sourceHost : workflow.inferUploadSource(
                new InspectionUmcWorkflow.NamedUpload(context, taskId, attemptId, file.getOriginalFilename()));
        workflow.validateUploadSource(new InspectionUmcWorkflow.UploadSource(context,taskId,attemptId,resolvedSourceHost!=null));
        byte[] content = file.getBytes();
        var fingerprint = new UploadFingerprint(attemptId, deviceId, file.getOriginalFilename(), DigestUtil.sha256Hex(content), commandHint, sourceHost);
        try {
            var uploaded = execute(new Write(context, taskId, "LOG_UPLOAD", fingerprint,
                    () -> service.upload(InspectionExecutionService.Upload.builder().context(context).taskId(taskId)
                            .attemptId(attemptId).deviceId(deviceId).fileName(file.getOriginalFilename()).content(content).commandHint(commandHint).build())));
            if (resolvedSourceHost != null) umc.fromOfflineLog(new InspectionUmcService.UploadedLog(
                    new InspectionRequestContext(context.tenantId(), context.actorId(), context.idempotencyKey(), context.correlationId(), uploaded.getData().getVersion()),
                    taskId, deviceId, resolvedSourceHost, fingerprint.sha256(), commandHint));
            return resolvedSourceHost == null ? uploaded : completeUpload(new Completion(context,taskId,uploaded.getData(),fingerprint.sha256()));
        } finally { java.util.Arrays.fill(content, (byte) 0); }
    }

    private record MultiFingerprint(Long attemptId, String digest, List<InspectionUmcService.Host> sourceHosts) { }

    @PostMapping(value = "/offline-log-batches", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<InspectionViews.TaskSummary> uploadFiles(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
            @RequestParam Long attemptId, @RequestParam(required = false) String sourceHosts,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws IOException {
        InspectionException.require(file == null || files == null || files.isEmpty(), "不能同时使用file和files上传日志");
        if (file != null) return uploadBatch(taskId, headers, attemptId, sourceHosts, file);
        InspectionException.require(files != null && !files.isEmpty() && files.size() <= 1024, "请选择1至1024份巡检日志");
        long total = 0;
        for (var item : files) {
            InspectionException.require(item != null && item.getSize() > 0, "日志文件不能为空");
            total += item.getSize();
            InspectionException.require(total <= 20 * 1024 * 1024, "本次巡检日志总量不得超过20MB");
        }
        var context = InspectionRequestContext.write(headers);
        var hosts = sourceHosts == null ? null : JsonUtils.parseArray(sourceHosts, InspectionUmcService.Host.class);
        if (hosts == null && files.size() == 1) hosts = workflow.inferBatchUploadSources(
                new InspectionUmcWorkflow.NamedUpload(context, taskId, attemptId, files.getFirst().getOriginalFilename()));
        if (hosts != null) {
            InspectionException.require(!hosts.isEmpty() && hosts.size() <= 100 && hosts.stream().allMatch(h -> h != null && h.deviceId() != null)
                    && hosts.stream().map(InspectionUmcService.Host::deviceId).distinct().count() == hosts.size(), "设备来源IP对应关系无效");
            hosts.forEach(h -> UmcLogDocument.requireIpv4(h.host()));
        }
        workflow.validateUploadSource(new InspectionUmcWorkflow.UploadSource(context, taskId, attemptId, hosts != null));
        var documents = new java.util.ArrayList<InspectionExecutionService.UploadFile>();
        try {
            for (var item : files) documents.add(new InspectionExecutionService.UploadFile(item.getOriginalFilename(), item.getBytes()));
            var digest = InspectionExecutionService.batchDigest(documents);
            var fingerprint = new MultiFingerprint(attemptId, digest, hosts);
            var request = new InspectionExecutionService.BatchFiles(context, taskId, attemptId, List.copyOf(documents), hosts);
            var uploaded = execute(new Write(context, taskId, "LOG_BATCH_FILES_UPLOAD", fingerprint, () -> service.uploadFiles(request)));
            if (hosts != null) umc.fromOfflineBatch(new InspectionUmcService.UploadedBatch(new InspectionRequestContext(context.tenantId(),
                    context.actorId(), context.idempotencyKey(), context.correlationId(), uploaded.getData().getVersion()), taskId, attemptId, digest, hosts));
            return hosts == null ? uploaded : completeUpload(new Completion(context, taskId, uploaded.getData(), digest));
        } finally { documents.forEach(d -> java.util.Arrays.fill(d.content(), (byte) 0)); }
    }

    public CommonResult<InspectionViews.TaskSummary> uploadBatch(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
            @RequestParam Long attemptId, @RequestParam(required = false) String sourceHosts, @RequestPart("file") MultipartFile file) throws IOException {
        InspectionException.require(file != null && file.getSize() > 0 && file.getSize() <= 20 * 1024 * 1024,
                "多设备日志不能为空且不得超过20MB");
        var context = InspectionRequestContext.write(headers);
        List<InspectionUmcService.Host> hosts = sourceHosts == null ? null : JsonUtils.parseArray(sourceHosts, InspectionUmcService.Host.class);
        if (hosts != null) {
            InspectionException.require(!hosts.isEmpty() && hosts.size() <= 100 && hosts.stream().allMatch(h -> h != null && h.deviceId() != null)
                    && hosts.stream().map(InspectionUmcService.Host::deviceId).distinct().count() == hosts.size(), "设备来源IP对应关系无效");
            hosts.forEach(h -> UmcLogDocument.requireIpv4(h.host()));
        }
        workflow.validateUploadSource(new InspectionUmcWorkflow.UploadSource(context,taskId,attemptId,hosts!=null));
        byte[] content = file.getBytes();
        var fingerprint = new BatchFingerprint(attemptId, file.getOriginalFilename(), DigestUtil.sha256Hex(content), hosts);
        try {
            var uploaded = execute(new Write(context, taskId, "LOG_BATCH_UPLOAD", fingerprint,
                    () -> service.uploadBatch(new InspectionExecutionService.BatchUpload(context, taskId, attemptId,
                            file.getOriginalFilename(), content, hosts))));
            if (hosts != null) umc.fromOfflineBatch(new InspectionUmcService.UploadedBatch(new InspectionRequestContext(context.tenantId(),
                    context.actorId(), context.idempotencyKey(), context.correlationId(), uploaded.getData().getVersion()), taskId, attemptId, fingerprint.sha256(), hosts));
            return hosts == null ? uploaded : completeUpload(new Completion(context,taskId,uploaded.getData(),fingerprint.sha256()));
        } finally { java.util.Arrays.fill(content, (byte) 0); }
    }

    @PostMapping("/actions/refresh-log-parse")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:execute')")
    public CommonResult<InspectionViews.TaskSummary> refresh(@PathVariable Long taskId, @RequestHeader HttpHeaders headers,
                                                            @RequestBody Refresh input) {
        var context = InspectionRequestContext.write(headers);
        var external = umc.refreshOfflineExecution(new cn.iocoder.yudao.module.pms.service.service.inspection.umc.InspectionUmcTransactions.ExecutionAccess(context, taskId, input.executionId()));
        if (external != null) return success(external);
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
    private record Completion(InspectionRequestContext context,Long taskId,InspectionViews.TaskSummary saved,String digest) { }
    private CommonResult<InspectionViews.TaskSummary> completeUpload(Completion command) {
        var original=command.context();var context=new InspectionRequestContext(original.tenantId(),original.actorId(),original.idempotencyKey(),original.correlationId(),command.saved().getVersion());
        return execute(new Write(context,command.taskId(),"UMC_CAPTURE_UPLOAD",command.digest(),
                ()->workflow.captureUploaded(new InspectionUmcWorkflow.Uploaded(context,command.taskId(),command.digest()))));
    }
}
