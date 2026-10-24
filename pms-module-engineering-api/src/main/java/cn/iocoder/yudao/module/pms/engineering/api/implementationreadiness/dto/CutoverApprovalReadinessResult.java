package cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto;

import java.util.List;

/** 内部审批重验只返回决定和绑定身份，不返回实时Owner资料。 */
public record CutoverApprovalReadinessResult(String decision, Long projectId, Long snapshotId,
                                           Long snapshotVersion, List<Long> deviceIds) {
    public CutoverApprovalReadinessResult { deviceIds = List.copyOf(deviceIds); }
}
