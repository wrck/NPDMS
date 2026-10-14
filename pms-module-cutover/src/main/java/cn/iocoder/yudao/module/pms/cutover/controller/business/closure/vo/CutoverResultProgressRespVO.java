package cn.iocoder.yudao.module.pms.cutover.controller.business.closure.vo;

import cn.iocoder.yudao.module.pms.asset.api.device.AssetCutoverResultApi;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.CutoverCoverageApi;
import java.time.LocalDateTime;
import java.util.List;

/** 对外只发布割接结果所需的 Owner 事实，不传播跨模块 API 类型。 */
public record CutoverResultProgressRespVO(List<Receipt> assetReceipts, Coverage projectCoverage) {
    public static CutoverResultProgressRespVO from(List<AssetCutoverResultApi.Receipt> receipts, CutoverCoverageApi.Coverage coverage) {
        return new CutoverResultProgressRespVO(receipts.stream().map(Receipt::from).toList(), Coverage.from(coverage));
    }
    public record Receipt(Long deviceId, String serialNumber, Long taskId, Long closureId, Long closureVersion,
                          String refreshStatus, LocalDateTime cutoverAt, LocalDateTime refreshedAt) {
        static Receipt from(AssetCutoverResultApi.Receipt value) {
            return new Receipt(value.deviceId(), value.serialNumber(), value.taskId(), value.closureId(), value.closureVersion(),
                    value.refreshStatus(), value.cutoverAt(), value.refreshedAt());
        }
    }
    public record Device(Long deviceId, String serialNumber, Long assignmentVersion) { }
    public record ResultReference(Long taskId, Long closureId, Long closureVersion) { }
    public record Coverage(String factVersion, List<Device> requiredDevices, List<Long> coveredDeviceIds,
                           List<Long> missingDeviceIds, List<ResultReference> successfulResults, boolean complete) {
        public static Coverage from(CutoverCoverageApi.Coverage value) {
            return new Coverage(value.factVersion(), value.requiredDevices().stream()
                    .map(device -> new Device(device.deviceId(), device.serialNumber(), device.assignmentVersion())).toList(),
                    List.copyOf(value.coveredDeviceIds()), List.copyOf(value.missingDeviceIds()), value.successfulResults().stream()
                    .map(result -> new ResultReference(result.taskId(), result.closureId(), result.closureVersion())).toList(), value.complete());
        }
    }
}
