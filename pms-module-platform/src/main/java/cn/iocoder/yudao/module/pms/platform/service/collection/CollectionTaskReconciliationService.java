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
import java.util.Objects;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionReconciliationDueQuery;
import java.util.Set;

/**
 * DAC 对账轮询：以 DAC 状态查询补偿回调缺失（docs/design/12-integration-design.md 11.4）。
 *
 * 运行态仅恢复 RECONCILING 停泊；终态进入 RECONCILING 并记录外部状态原值，
 * 终态触发原事件重投，正式结果由签名回调经文件接收服务回填。
 * RECONCILING 且 DAC 无记录的任务保持停泊：临时秘密已消费，不可自动重发。
 * 显式取消形成的持久化提交阻止记录是唯一的未下发取消证明，可结束无外部映射的等待任务。
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
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private CollectionConnectionService connectionAuthorization;

    public String reconcileDue(Long tenantId) {
        DeviceOpsGatewayApi gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            return "Device Ops 网关未装配";
        }
        int checked = 0;
        long afterId = 0;
        int restored = 0;
        int observed = 0;
        int unknown = 0;
        while (true) {
            List<CollectionTaskDO> tasks = taskMapper.selectReconciliationDue(new CollectionReconciliationDueQuery(tenantId, afterId, 100));
            if (tasks.isEmpty()) break;
            checked += tasks.size();
            for (CollectionTaskDO task : tasks) {
                afterId = task.getId();
                DeviceOpsTaskSnapshot snapshot;
                try {
                    if (connectionAuthorization != null && !connectionAuthorization.remainsAuthorized(task)) {
                        gateway.cancel(task.getPlatformTaskId(), "CREDENTIAL_AUTHORIZATION_REVOKED");
                    }
                    snapshot = gateway.query(task.getPlatformTaskId());
                } catch (RuntimeException ex) {
                    log.warn("DAC 对账查询失败 platformTaskId={} reason={}",
                            task.getPlatformTaskId(), ex.getClass().getSimpleName());
                    unknown++;
                    continue;
                }
                if (applyUndispatchedCancellation(taskMapper, task, snapshot)) {
                    observed++;
                    continue;
                }
                if (snapshot == null || !task.getPlatformTaskId().equals(snapshot.platformTaskId())
                        || snapshot.externalTaskId() == null
                        || (task.getExternalTaskId() != null && !Objects.equals(task.getExternalTaskId(), snapshot.externalTaskId()))) {
                    unknown++;
                    continue;
                }
                String externalStatus = snapshot.externalStatus();
                if (externalStatus != null && (task.getExternalTaskId() == null || "CREATED".equals(task.getStatus()) || "AUTHORIZED".equals(task.getStatus()))
                        && (RUNNING_STATUSES.contains(externalStatus) || TERMINAL_STATUSES.contains(externalStatus))) {
                    String recoveredStatus = CollectionTaskStateMachine.acceptedDispatchStatus(task.getStatus());
                    int updated = taskMapper.updateDispatchState(new CollectionTaskDispatchUpdate(task.getTenantId(),
                            task.getPlatformTaskId(), task.getTechnicalStage(), recoveredStatus, "ACCEPTED",
                            snapshot.externalTaskId(), externalStatus, null));
                    if (updated != 1) { unknown++; continue; }
                    task.setStatus(recoveredStatus); task.setTechnicalStage("ACCEPTED"); task.setExternalTaskId(snapshot.externalTaskId());
                    restored++;
                }
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
                    try {
                        gateway.retryResultDelivery(task.getPlatformTaskId());
                    } catch (RuntimeException failure) {
                        log.warn("DAC 结果重投暂不可用 platformTaskId={} type={}",
                                task.getPlatformTaskId(), failure.getClass().getSimpleName());
                    }
                }
            }
            if (tasks.size() < 100) break;
        }
        return "DAC 对账轮询 检查 " + checked + " 条，恢复派发 " + restored
                + " 条，终态观察 " + observed + " 条，未知 " + unknown + " 条";
    }

    private int restoreDispatched(CollectionTaskDO task) {
        return taskMapper.updateReconciliationState(new CollectionTaskReconciliationUpdate(
                task.getTenantId(), task.getPlatformTaskId(), task.getStatus(),
                task.getLastCallbackSequence(), "ACCEPTED"));
    }

    static boolean applyUndispatchedCancellation(CollectionTaskMapper mapper, CollectionTaskDO task,
                                                 DeviceOpsTaskSnapshot snapshot) {
        if (snapshot == null || !task.getPlatformTaskId().equals(snapshot.platformTaskId())
                || snapshot.externalTaskId() != null || !"CANCELLED".equals(snapshot.externalStatus())
                || !"CANCELLED_BEFORE_DISPATCH".equals(snapshot.failureCategory())
                || !CollectionTaskStateMachine.canCancelBeforeDispatch(task.getStatus(), task.getTechnicalStage(), task.getExternalTaskId())) {
            return false;
        }
        return mapper.updateUndispatchedCancellation(
                new cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskUndispatchedCancellationUpdate(
                        task.getTenantId(), task.getPlatformTaskId(), task.getStatus(), task.getTechnicalStage())) == 1;
    }

    private int recordTerminalObservation(CollectionTaskDO task, String externalStatus) {
        return taskMapper.updateReconciliationObservation(
                new CollectionTaskReconciliationObservationUpdate(
                        task.getTenantId(), task.getPlatformTaskId(), task.getStatus(),
                        task.getTechnicalStage(), externalStatus));
    }
}
