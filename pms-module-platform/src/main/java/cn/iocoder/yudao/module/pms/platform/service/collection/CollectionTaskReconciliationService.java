package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsGatewayApi;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsTaskSnapshot;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskReconciliationObservationUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskReconciliationUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * DAC 对账轮询：以 DAC 状态查询补偿回调缺失（docs/design/12-integration-design.md 11.4）。
 *
 * 运行态仅恢复 RECONCILING 停泊；终态进入 RECONCILING 并记录外部状态原值，
 * 正式结果回填（证据文件 -> fileVersionId -> handleCallback）由证据接入切片完成。
 * RECONCILING 且 DAC 无记录的任务保持停泊：临时秘密已消费，不可自动重发。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionTaskReconciliationService {

    private static final Set<String> RUNNING_STATUSES = Set.of(
            "QUEUED", "CONNECTING", "EXECUTING", "PARSING");
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "TIMED_OUT", "CANCELLED");

    private final CollectionTaskMapper taskMapper;
    private final ObjectProvider<DeviceOpsGatewayApi> gatewayProvider;

    public String reconcileDue(Long tenantId) {
        DeviceOpsGatewayApi gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            return "Device Ops 网关未装配";
        }
        List<CollectionTaskDO> tasks = taskMapper.selectReconciliationDue(tenantId);
        int restored = 0;
        int observed = 0;
        int unknown = 0;
        for (CollectionTaskDO task : tasks) {
            DeviceOpsTaskSnapshot snapshot;
            try {
                snapshot = gateway.query(task.getPlatformTaskId());
            } catch (RuntimeException ex) {
                log.warn("DAC 对账查询失败 platformTaskId={} reason={}",
                        task.getPlatformTaskId(), ex.getMessage());
                unknown++;
                continue;
            }
            String externalStatus = snapshot.externalStatus();
            if (externalStatus == null || externalStatus.equals("UNKNOWN")) {
                unknown++;
                continue;
            }
            if (RUNNING_STATUSES.contains(externalStatus)) {
                if ("RECONCILING".equals(task.getTechnicalStage())
                        && restoreDispatched(task) == 1) {
                    restored++;
                }
                continue;
            }
            if (TERMINAL_STATUSES.contains(externalStatus)
                    && recordTerminalObservation(task, externalStatus) == 1) {
                observed++;
            }
        }
        return "DAC 对账轮询 检查 " + tasks.size() + " 条，恢复派发 " + restored
                + " 条，终态观察 " + observed + " 条，未知 " + unknown + " 条";
    }

    private int restoreDispatched(CollectionTaskDO task) {
        return taskMapper.updateReconciliationState(new CollectionTaskReconciliationUpdate(
                task.getTenantId(), task.getPlatformTaskId(), task.getStatus(),
                task.getLastCallbackSequence(), "DISPATCHED"));
    }

    private int recordTerminalObservation(CollectionTaskDO task, String externalStatus) {
        return taskMapper.updateReconciliationObservation(
                new CollectionTaskReconciliationObservationUpdate(
                        task.getTenantId(), task.getPlatformTaskId(), task.getStatus(),
                        task.getTechnicalStage(), externalStatus));
    }
}
