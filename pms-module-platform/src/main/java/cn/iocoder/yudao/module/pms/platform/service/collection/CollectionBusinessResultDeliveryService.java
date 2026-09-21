package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionConsumptionCommand;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionRequestMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionBusinessResultDeliveryService {
    static final Set<String> EVENTS = Set.of("CollectionResultAvailable", "CollectionFailed", "CollectionCancelled");
    private final PlatformOutboxDeliveryApi outbox;
    private final CollectionRequestMapper requests;
    private final CollectionTaskMapper taskMapper;
    private final CollectionTaskApi tasks;
    private final CollectionCallbackApi callbacks;
    private final List<CollectionBusinessResultReceiver> receivers;
    private final PlatformTransactionManager transactions;

    @TransactionalEventListener
    public void afterLogCommitted(CollectionLogReady event) {
        try {
            deliver(event.tenantId(), event.platformTaskId());
        } catch (RuntimeException failure) {
            // Callback receipt is already committed. Its Outbox will retry independently of the browser.
            log.warn("采集日志业务回传待重试 task={} reason={}", event.platformTaskId(), failure.getClass().getSimpleName());
        }
    }

    public String deliverDue() {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var dueAt = LocalDateTime.now();
        int delivered = 0, retried = 0;
        for (var event : outbox.claimDue(new PlatformOutboxClaimQuery(dueAt, 50, EVENTS))) {
            try {
                if (!tenant.equals(event.tenantId())) throw new IllegalStateException("COLLECTION_RETURN_TENANT_MISMATCH");
                var payload = JsonUtils.parseTree(event.payload());
                String taskId = payload.get("platformTaskId").asText();
                var task = tasks.getTask(tenant, taskId);
                if (task == null || !Objects.equals(task.resultVersion(), payload.get("resultVersion").asLong())) {
                    throw new IllegalStateException("COLLECTION_RETURN_RESULT_MISMATCH");
                }
                var file = payload.get("fileVersionId");
                if (!Objects.equals(task.fileVersionId(), file == null || file.isNull() ? null : file.asLong())) {
                    throw new IllegalStateException("COLLECTION_RETURN_FILE_MISMATCH");
                }
                deliver(tenant, taskId);
                outbox.markDelivered(event.eventId(), event.retryCount());
                delivered++;
            } catch (RuntimeException failure) {
                outbox.scheduleRetry(event.eventId(), event.retryCount(), dueAt.plusSeconds(30));
                retried++;
                log.warn("采集日志业务回传待重试 event={} reason={}", event.eventId(), failure.getClass().getSimpleName());
            }
        }
        return "业务日志回传 " + delivered + " 条，待重试 " + retried + " 条";
    }

    public void deliver(Long tenant, String taskId) {
        if (!Objects.equals(tenant, TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalStateException("COLLECTION_RETURN_TENANT_MISMATCH");
        }
        // AFTER_COMMIT still has bound transaction resources. Receive and acknowledge in a fresh transaction.
        var tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(ignored -> {
            var task = tasks.getTask(tenant, taskId);
            if (task == null) throw new IllegalStateException("COLLECTION_RETURN_TASK_MISSING");
            if (!"BUSINESS_CONSUMPTION".equals(task.completionMode()) || task.fileVersionId() == null) return;
            if (!Objects.equals(task.sourceContext(), task.consumerContext())
                    || !Objects.equals(task.sourceObjectType(), task.consumerObjectType())
                    || !Objects.equals(task.sourceObjectId(), task.consumerObjectId())) {
                throw new IllegalStateException("COLLECTION_RETURN_CONSUMER_MISMATCH");
            }
            if ("SECURITY_EXCEPTION".equals(task.status()) || task.resultVersion() == null) {
                throw new IllegalStateException("COLLECTION_RETURN_UNSAFE_RESULT");
            }
            if (!Set.of("RESULT_AVAILABLE", "COMPLETED", "FAILED", "CANCELLED").contains(task.status())) {
                throw new IllegalStateException("COLLECTION_RETURN_RESULT_PENDING");
            }
            var row = requests.findByTask(tenant, taskId);
            if (row == null || !row.getObjectId().toString().equals(task.sourceObjectId())) {
                throw new IllegalStateException("COLLECTION_RETURN_SOURCE_MISSING");
            }
            var matches = receivers.stream().filter(r -> r.entries().contains(row.getEntry())).toList();
            if (matches.size() != 1) throw new CollectionOperationException("业务日志接收入口尚未接入");
            matches.getFirst().receive(new CollectionBusinessResultReceiver.Result(tenant, row.getEntry(), row.getObjectId(),
                    task.sourceContext(), task.sourceObjectType(), Long.valueOf(task.projectId()), Long.valueOf(task.deviceId()),
                    row.getId(), row.getActorId(), taskId, task.resultVersion(), task.fileVersionId(), task.protocol(),
                    task.externalStatus(), task.failureCategory(), row.getCommandText(), row.getTemplateName()));
            // The business lock can wait for another delivery. Refresh under lock before acknowledging.
            var current = taskMapper.selectByTenantAndPlatformTaskIdForUpdate(tenant, taskId);
            if (current == null || !Objects.equals(task.resultVersion(), current.getResultVersion())
                    || !Objects.equals(task.fileVersionId(), current.getFileVersionId())) {
                throw new IllegalStateException("COLLECTION_RETURN_RESULT_CHANGED");
            }
            if ("RESULT_AVAILABLE".equals(current.getStatus())) {
                callbacks.confirmConsumption(new CollectionConsumptionCommand(taskId, task.consumerContext(),
                        task.consumerObjectType(), task.consumerObjectId(), task.resultVersion(), row.getRequestKey()));
                row.setConsumedResultVersion(task.resultVersion());
                if (requests.updateById(row) != 1) throw new IllegalStateException("COLLECTION_RETURN_ACK_CONFLICT");
            }
        });
    }
}
