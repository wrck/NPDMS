package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.umc.UmcInspectionApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionReportRevisionDO;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionExecutionDO;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionViews;
import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

/** Encodes content owned by the existing report revision. Persistence stays with its existing Mapper/transaction. */
public final class InspectionUmcReportResults {
    private InspectionUmcReportResults() { }
    public record StoredLog(Long artifactId, Integer versionNo, String referenceKey, String sha256) { }
    public record Source(Long deviceId, String host, String expectedSerial, boolean dispatched,
                         UmcInspectionApi.Checkpoint checkpoint, StoredLog log, Long executionId) {
        public Source(Long deviceId, String host, String expectedSerial, boolean dispatched, UmcInspectionApi.Checkpoint checkpoint, StoredLog log) {
            this(deviceId, host, expectedSerial, dispatched, checkpoint, log, null);
        }
    }
    public record SourceUpdate(InspectionReportRevisionDO report, Source source) { }
    public record Completion(InspectionReportRevisionDO report, UmcInspectionApi.Result result) { }
    public record ExecutionResults(Long tenantId, Long taskId, List<InspectionExecutionDO> executions,
                                   List<InspectionReportRevisionDO> reports) { }

    /** The execution page consumes the same persisted report snapshot, without creating a second result store. */
    public static List<InspectionViews.UmcExecutionResultView> executionResults(ExecutionResults query) {
        var views = new ArrayList<InspectionViews.UmcExecutionResultView>();
        for (var report : query.reports()) {
            require(Objects.equals(query.tenantId(), report.getTenantId()) && Objects.equals(query.taskId(), report.getTaskId()), "巡检结果不属于当前任务");
            var source = source(report);
            if (source.executionId() == null) continue; // Historical reports with no execution binding remain in the report history.
            var execution = query.executions().stream().filter(e -> Objects.equals(e.getId(), source.executionId())).findFirst().orElse(null);
            require(execution != null && Objects.equals(execution.getTenantId(), query.tenantId())
                    && Objects.equals(execution.getTaskId(), query.taskId()) && Objects.equals(execution.getAttemptId(), report.getAttemptId())
                    && Objects.equals(execution.getDeviceId(), source.deviceId()) && Objects.equals(execution.getArtifactId(), source.log().artifactId())
                    && Objects.equals(execution.getFileVersionNo(), source.log().versionNo())
                    && Objects.equals(execution.getFileReferenceKey(), source.log().referenceKey())
                    && Objects.equals(execution.getFileSha256(), source.log().sha256()), "巡检结果与原执行日志关联不一致");
            UmcInspectionApi.Device device = null;
            if ("AVAILABLE".equals(report.getStatusCode())) {
                var detail = load(report);
                require(detail.devlist() != null && detail.devlist().size() == 1, "巡检结果设备数量不一致");
                device = detail.devlist().getFirst();
                require(source.host().equals(device.deviceip()) && source.expectedSerial().equalsIgnoreCase(Objects.toString(device.sn(), "")), "巡检结果设备身份不一致");
            }
            views.add(new InspectionViews.UmcExecutionResultView(report.getId(), report.getAttemptId(), execution.getId(),
                    execution.getDeviceId(), report.getStatusCode(), currentFailure(report), device));
        }
        return List.copyOf(views);
    }

    /** Older completed rows may retain a pre-recovery error; reading them must not rewrite their history. */
    public static String currentFailure(InspectionReportRevisionDO report) {
        return "AVAILABLE".equals(report.getStatusCode()) ? null : report.getFailureMessage();
    }

    public static Source source(InspectionReportRevisionDO report) {
        require("UMC".equals(report.getSourceCode()) && report.getSourceContextJson() != null, "UMC报告来源缺失");
        var source = JsonUtils.parseObject(report.getSourceContextJson(), Source.class);
        require(source != null && source.log() != null, "UMC报告日志来源缺失"); return source;
    }

    public static void updateSource(SourceUpdate update) {
        mutable(update.report());
        update.report().setSourceContextJson(JsonUtils.toJsonString(update.source()));
    }

    public static void complete(Completion command) {
        var report = command.report(); var result = command.result(); var detail = result.detail(); mutable(report);
        require(detail != null && detail.devlist() != null && result.checkpoint() != null
                && Objects.equals(detail.id(), result.checkpoint().reportId())
                && Objects.equals(detail.taskid(), result.checkpoint().taskId()), "UMC结果与恢复来源不一致");
        String snapshot = JsonUtils.toJsonString(detail);
        report.setResultJson(snapshot); report.setResultSha256(DigestUtil.sha256Hex(snapshot));
        var source = source(report);
        report.setSourceContextJson(JsonUtils.toJsonString(new Source(source.deviceId(), source.host(), source.expectedSerial(),
                true, result.checkpoint(), source.log(), source.executionId())));
    }

    public static UmcInspectionApi.Report load(InspectionReportRevisionDO report) {
        require("UMC".equals(report.getSourceCode()) && report.getResultJson() != null && report.getResultSha256() != null,
                "UMC报告结果尚未入库");
        var detail = JsonUtils.parseObject(report.getResultJson(), UmcInspectionApi.Report.class);
        require(detail != null && report.getResultSha256().equals(DigestUtil.sha256Hex(JsonUtils.toJsonString(detail))), "UMC报告结果摘要不一致");
        return detail;
    }

    private static void mutable(InspectionReportRevisionDO report) {
        require("UMC".equals(report.getSourceCode()) && "GENERATING".equals(report.getStatusCode())
                && report.getResultJson() == null && report.getResultSha256() == null, "已完成的报告不可覆盖");
    }
}
