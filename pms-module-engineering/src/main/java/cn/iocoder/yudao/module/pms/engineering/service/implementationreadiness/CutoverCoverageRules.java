package cn.iocoder.yudao.module.pms.engineering.service.implementationreadiness;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.api.result.CutoverResultApi;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.CutoverCoverageApi.*;
import java.util.*;

public final class CutoverCoverageRules {
    private CutoverCoverageRules() { }
    public static Coverage calculate(List<Device> current, List<CutoverResultApi.Result> successes) {
        var required = current.stream().sorted(Comparator.comparing(Device::deviceId)).toList();
        var covered = new TreeSet<Long>();
        var references = new ArrayList<ResultReference>();
        for (var result : successes) {
            boolean contributes = false;
            for (var frozen : result.devices()) {
                if (required.stream().anyMatch(device -> Objects.equals(device.deviceId(), frozen.deviceId())
                        && Objects.equals(device.assignmentVersion(), frozen.assignmentVersion())
                        && device.serialNumber().equalsIgnoreCase(frozen.serialNumber()))) {
                    covered.add(frozen.deviceId()); contributes = true;
                }
            }
            if (contributes) references.add(new ResultReference(result.taskId(), result.closureId(), result.closureVersion()));
        }
        references.sort(Comparator.comparing(ResultReference::closureId));
        var missing = required.stream().map(Device::deviceId).filter(id -> !covered.contains(id)).toList();
        String version = DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(required, references)));
        // 空范围绝不隐含 NOT_APPLICABLE；只有正式冻结不适用依据可作为其他完成事实。
        return new Coverage(version, required, List.copyOf(covered), missing, references, !required.isEmpty() && missing.isEmpty());
    }
}
