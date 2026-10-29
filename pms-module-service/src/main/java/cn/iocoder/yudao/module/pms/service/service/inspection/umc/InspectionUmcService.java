package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.iocoder.yudao.module.pms.integration.api.umc.UmcInspectionApi;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InspectionUmcService {
    private final InspectionUmcTransactions transactions;
    private final InspectionCommandExecutor commands;
    private final UmcInspectionApi umc;
    public record Host(Long deviceId, String host) { }
    public record UploadedLog(InspectionRequestContext context, Long taskId, Long deviceId, String host, String digest, String commandHint) { }
    public record UploadedBatch(InspectionRequestContext context, Long taskId, Long attemptId, String digest, List<Host> hosts) { }
    private record Fingerprint(Long version, Long deviceId, String host, String digest, String commandHint) { }
    private record CollectionFingerprint(Long version, Long executionId) { }
    public InspectionUmcTransactions.View fromCollection(InspectionUmcTransactions.ExecutionAccess request) {
        var reference=commands.execute(InspectionCommandExecutor.Write.<InspectionUmcTransactions.Reference>builder().context(request.context())
                .operation("UMC_PREPARE_COLLECTION").scope(request.taskId()+":"+request.executionId())
                .input(new CollectionFingerprint(request.context().requireVersion(),request.executionId()))
                .responseType(InspectionUmcTransactions.Reference.class).action(()->transactions.prepareCollection(request))
                .resourceKey(r->r.reportId().toString()).build());
        return run(new InspectionUmcTransactions.ReportAccess(request.context(),request.taskId(),reference.reportId()));
    }
    public InspectionViews.TaskSummary refreshOfflineExecution(InspectionUmcTransactions.ExecutionAccess request) {
        var reference = transactions.findExecutionReport(request);
        if (reference == null) return null;
        run(new InspectionUmcTransactions.ReportAccess(request.context(), request.taskId(), reference.reportId()));
        return transactions.executionSummary(request);
    }
    public List<InspectionUmcTransactions.View> fromOfflineBatch(UploadedBatch command) {
        var devices = transactions.uploadedDevices(new InspectionUmcTransactions.BatchLogs(command.context(), command.taskId(), command.attemptId(), command.digest()));
        return devices.stream().map(deviceId -> {
            var host = command.hosts().stream().filter(h -> Objects.equals(h.deviceId(), deviceId)).findFirst()
                    .orElseThrow(() -> new InspectionException(InspectionException.Reason.INVALID_REQUEST, "设备来源IP缺失"));
            return fromOfflineLog(new UploadedLog(command.context(), command.taskId(), deviceId, host.host(), command.digest(), null));
        }).toList();
    }

    /** Consumes the existing offline execution's already-redacted log; never accepts a second upload. */
    public InspectionUmcTransactions.View fromOfflineLog(UploadedLog command) {
        UmcLogDocument.requireIpv4(command.host());
        var access = new InspectionUmcTransactions.Access(command.context(), command.taskId(), command.deviceId());
        var reference = commands.execute(InspectionCommandExecutor.Write.<InspectionUmcTransactions.Reference>builder()
                .context(command.context()).operation("UMC_PREPARE").scope(command.taskId() + ":" + command.deviceId())
                .input(new Fingerprint(command.context().requireVersion(), command.deviceId(), command.host(), command.digest(), command.commandHint()))
                .responseType(InspectionUmcTransactions.Reference.class)
                .action(() -> transactions.prepare(new InspectionUmcTransactions.Prepare(access, command.host(), command.digest(), command.commandHint())))
                .resourceKey(r -> r.reportId().toString()).build());
        return run(new InspectionUmcTransactions.ReportAccess(command.context(), command.taskId(), reference.reportId()));
    }

    /** No database transaction is held while the external process is running. */
    public synchronized InspectionUmcTransactions.View run(InspectionUmcTransactions.ReportAccess request) {
        var existing = transactions.detail(request);
        if ("AVAILABLE".equals(existing.statusCode())) return existing;
        var invocation = transactions.begin(request);
        UmcInspectionApi.Result result = null;
        try {
            result = umc.inspect(new UmcInspectionApi.Request(new UmcInspectionApi.Scope(request.context().tenantId(),
                    request.taskId().toString(), invocation.attemptId().toString()), "report-" + request.reportId(),
                    List.of(new UmcInspectionApi.OfflineLog(invocation.uploadName(), invocation.log())), invocation.state().checkpoint(), invocation.queryOnly()));
            return transactions.complete(new InspectionUmcTransactions.Complete(request, result));
        } catch (UmcInspectionApi.Failure failure) {
            return transactions.fail(new InspectionUmcTransactions.Failed(request, failure.checkpoint(), failure.code()));
        } catch (RuntimeException failure) {
            return transactions.fail(new InspectionUmcTransactions.Failed(request, result == null ? null : result.checkpoint(), "PLT_ARCHIVE_FAILED"));
        }
    }
}
