package cn.iocoder.yudao.module.pms.service.framework;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionBusinessResultReceiver;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionTaskApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionCollectionReceiptDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionOnlineAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

/** Accepts immutable source facts only. A signed callback never advances the inspection task. */
@Component
@RequiredArgsConstructor
public class InspectionCollectionResultReceiver implements CollectionBusinessResultReceiver {
    private final InspectionExecutionMapper executions;
    private final InspectionTaskMapper tasks;
    private final InspectionCollectionReceiptMapper receipts;
    private final CollectionTaskApi collectionTasks;
    private final InspectionOnlineAuthorization authorization;
    @Override public Set<String> entries() { return Set.of("inspection"); }

    @Override @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void receive(Result result) {
        require(result != null && Objects.equals(result.tenantId(), TenantContextHolder.getRequiredTenantId())
                && "inspection".equals(result.entry()) && "SRV".equals(result.sourceContext())
                && "INSPECTION_EXECUTION".equals(result.sourceObjectType()) && result.resultVersion() != null
                && result.resultVersion() > 0 && result.resultVersion() <= Integer.MAX_VALUE && result.fileVersionId() != null && result.fileVersionId() > 0
                && result.executionId() != null && result.executionId() > 0 && result.platformTaskId() != null && !result.platformTaskId().isBlank()
                && result.externalStatus() != null && result.externalStatus().length() <= 32,
                "巡检采集回调来源无效");
        var initial = executions.selectById(result.objectId());
        require(initial != null && Objects.equals(initial.getTenantId(), result.tenantId()), "巡检执行记录不存在");
        var task = tasks.selectForUpdate(new InspectionTaskRows(result.tenantId(), initial.getTaskId()));
        var row = executions.selectById(result.objectId());
        var remote = collectionTasks.getTask(result.tenantId(), result.platformTaskId());
        require(task != null && row != null && remote != null && Objects.equals(task.getProjectId(), result.projectId())
                && Objects.equals(row.getDeviceId(), result.deviceId()) && Objects.equals(row.getSubmittedBy(), result.actorId())
                && Set.of("PRECHECK", "COLLECT").contains(row.getPurposeCode())
                && Objects.equals(row.getProtocol(), result.protocol()) && Objects.equals(remote.protocol(), result.protocol())
                && Objects.equals(remote.platformTaskId(), result.platformTaskId()) && Boolean.TRUE.equals(row.getCredentialSnapshotRecorded())
                && Objects.equals(row.getCollectionRequestKey(), remote.idempotencyKey())
                && "SRV".equals(remote.sourceContext()) && "INSPECTION_EXECUTION".equals(remote.sourceObjectType())
                && row.getId().toString().equals(remote.sourceObjectId()) && task.getProjectId().toString().equals(remote.projectId())
                && row.getDeviceId().toString().equals(remote.deviceId())
                && Objects.equals(authorization.profile(row).commandText(), result.commandText()), "巡检采集回调绑定不匹配");
        require(row.getCollectionExecutionId() == null || Objects.equals(row.getCollectionExecutionId(), result.executionId()), "平台采集执行身份发生变化");
        require(row.getCollectionTaskId() == null || Objects.equals(row.getCollectionTaskId(), result.platformTaskId()), "平台采集任务身份发生变化");
        var old = receipts.selectForExecution(new InspectionExecutionRows(result.tenantId(), row.getId())).stream()
                .filter(r -> Objects.equals(r.getResultVersion(), result.resultVersion())).findFirst().orElse(null);
        if (old != null) {
            require(Objects.equals(old.getCollectionExecutionId(), result.executionId()) && Objects.equals(old.getCollectionTaskId(), result.platformTaskId())
                    && Objects.equals(old.getFileVersionId(), result.fileVersionId()) && Objects.equals(old.getExternalStatus(), result.externalStatus())
                    && Objects.equals(old.getFailureCategory(), result.failureCategory()) && Objects.equals(old.getActorId(), result.actorId()),
                    "同一采集结果版本内容冲突");
            return;
        }
        require(Objects.equals(result.resultVersion(), remote.resultVersion()) && Objects.equals(result.fileVersionId(), remote.fileVersionId())
                && Objects.equals(result.externalStatus(), remote.externalStatus()) && Objects.equals(result.failureCategory(), remote.failureCategory()),
                "巡检采集回调结果版本不匹配");
        var receipt = new InspectionCollectionReceiptDO(); receipt.setTenantId(result.tenantId()); receipt.setVersion(1L);
        receipt.setTaskId(task.getId()); receipt.setExecutionId(row.getId()); receipt.setCollectionExecutionId(result.executionId());
        receipt.setCollectionTaskId(result.platformTaskId()); receipt.setResultVersion(result.resultVersion());
        receipt.setFileVersionId(result.fileVersionId()); receipt.setExternalStatus(result.externalStatus());
        receipt.setFailureCategory(result.failureCategory()); receipt.setActorId(result.actorId()); receipt.setReceivedAt(LocalDateTime.now());
        receipts.insert(receipt);
        // The selected result is frozen once. Later versions remain separate receipts and require a new execution.
        if (row.getCollectionResultVersion() == null) {
            row.setCollectionExecutionId(result.executionId()); row.setCollectionTaskId(result.platformTaskId());
            row.setCollectionResultVersion(result.resultVersion().intValue()); row.setCollectionFileVersionId(result.fileVersionId());
            row.setCollectionStatus(result.externalStatus()); row.setCollectionFailureCategory(result.failureCategory());
            require(executions.updateById(row) == 1, "巡检回调记录并发更新");
        }
    }
}
