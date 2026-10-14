package cn.iocoder.yudao.module.pms.cutover.framework.runtime;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverCollectionPort;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 关联统一采集窗口已经持久化的执行，不在 CUT 中收取或保存连接密码。 */
@Component
@RequiredArgsConstructor
public class CutoverChecklistCollectionAdapter implements CutoverCollectionPort {
    private final ObjectProvider<CollectionApplicationApi> collection;
    private final ObjectProvider<CollectionExecutionQueryApi> executions;

    @Override
    public RequestReceipt request(Request request) {
        var execution = collection.getObject().findByRequestKey(CollectionApplicationApi.SourceQuery.builder()
                .entry(CutoverCollectionSources.CHECKLIST)
                .objectId(request.checklistItemId())
                .actorId(request.actorId())
                .build(), request.idempotencyKey());
        if (execution == null) throw new CollectionOperationException("请先通过设备采集窗口下发，再关联已记录的执行");
        requireIdentity(execution, request);
        return new RequestReceipt(execution.id(), status(execution));
    }

    @Override
    public CollectionFact inspect(Inspection request) {
        var execution = executions.getObject().get(new CollectionExecutionQueryApi.Query(request.tenantId(), request.actorId(),
                CutoverCollectionSources.CHECKLIST, request.checklistItemId(), request.collectionTaskId()));
        requireIdentity(execution, request);
        return fact(execution);
    }

    public static CollectionFact fact(CollectionApplicationApi.Execution execution) {
        var task = execution.task();
        var status = status(execution);
        if (status == TechnicalStatus.COMPLETED) {
            if (task.fileVersionId() == null || task.resultVersion() == null) throw new CollectionOperationException("采集结果尚无完整的日志版本");
            return new CollectionFact(execution.id(), status, task.fileVersionId(), task.resultVersion(),
                    JsonUtils.toJsonString(Map.of("platformTaskId", task.platformTaskId(), "fileVersionId", task.fileVersionId(),
                            "resultVersion", task.resultVersion(), "templateName", execution.templateName(),
                            "externalStatus", task.externalStatus())), null);
        }
        String failure = status == TechnicalStatus.FAILED ? task.failureCategory() : null;
        if (status == TechnicalStatus.FAILED && (failure == null || failure.isBlank())) failure = task.status();
        return new CollectionFact(execution.id(), status, null, null, null, failure);
    }

    private static TechnicalStatus status(CollectionApplicationApi.Execution execution) {
        var task = execution.task();
        if (Set.of("RESULT_AVAILABLE", "COMPLETED").contains(task.status())
                && Set.of("SUCCESS", "SUCCEEDED").contains(Objects.toString(task.externalStatus(), ""))) return TechnicalStatus.COMPLETED;
        if (Set.of("FAILED", "CANCELLED", "SECURITY_EXCEPTION", "RESULT_AVAILABLE", "COMPLETED").contains(task.status())) return TechnicalStatus.FAILED;
        return Set.of("CREATED", "AUTHORIZED").contains(task.status()) ? TechnicalStatus.ACCEPTED : TechnicalStatus.RUNNING;
    }

    private static void requireIdentity(CollectionApplicationApi.Execution execution, ExecutionIdentity expected) {
        if (!Objects.equals(execution.task().projectId(), String.valueOf(expected.projectId()))
                || !Objects.equals(execution.task().deviceId(), String.valueOf(expected.deviceId()))
                || !Objects.equals(execution.task().templateId(), String.valueOf(expected.commandTemplateId()))) {
            throw new CollectionOperationException("采集执行与本次项目、设备或模板不一致");
        }
    }
}
