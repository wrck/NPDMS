package cn.iocoder.yudao.module.pms.cutover.api.approval;

import java.util.List;

/** 仅供Owner在原审批写事务内核验当前节点；不授予项目/设备读取权限，不开放HTTP入口。 */
public interface CutoverApprovalReadinessAccessApi {
    void requireCurrent(Query query);

    record Query(Long tenantId, Long taskId, Long taskVersion, Long approvalInstanceId,
                 Long approvalVersion, Integer nodeNo, Long projectId, Long snapshotId,
                 Long snapshotVersion, List<Long> deviceIds) {
        public Query { deviceIds = deviceIds == null ? List.of() : List.copyOf(deviceIds); }
    }
}
