package cn.iocoder.yudao.module.pms.bindings.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.bindings.backend.EventDrivenBackend;
import cn.iocoder.yudao.module.pms.bindings.backend.InlineSyncBackend;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.ProcessDefinitionPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.ProcessDefinitionSnapshot;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultRecord;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景执行编排：按定义把运行请求交给目标执行后端；后端未装配或缺能力时显式失败，
 * 证明执行实现可替换、平台语义不内嵌于任一后端。
 */
@Service
public class ScenarioExecutionService {

    private final ProcessDefinitionPort definitionPort;
    private final BusinessResultPort resultPort;
    private final ObjectProvider<InlineSyncBackend> inlineBackend;
    private final ObjectProvider<EventDrivenBackend> eventDrivenBackend;

    public ScenarioExecutionService(ProcessDefinitionPort definitionPort, BusinessResultPort resultPort,
                                    ObjectProvider<InlineSyncBackend> inlineBackend,
                                    ObjectProvider<EventDrivenBackend> eventDrivenBackend) {
        this.definitionPort = definitionPort;
        this.resultPort = resultPort;
        this.inlineBackend = inlineBackend;
        this.eventDrivenBackend = eventDrivenBackend;
    }

    /** 同步运行入口只承载同步直执后端；事件驱动后端由事件触发，不经此入口调用。 */
    public ScenarioRunOutcome run(String definitionCode, Long entityId, String idempotencyKey,
                                  String backendId) {
        ProcessDefinitionSnapshot snapshot = definitionPort.findPublished(definitionCode)
                .orElseThrow(() -> new BusinessContractException("DEFINITION_NOT_PUBLISHED",
                        "定义不存在或未发布: " + definitionCode));
        if (backendId != null && !backendId.isBlank() && !InlineSyncBackend.BACKEND_ID.equals(backendId)) {
            throw new BusinessContractException("EXECUTION_BACKEND_UNAVAILABLE",
                    "同步运行入口只承载同步直执后端，请求后端: " + backendId);
        }
        InlineSyncBackend backend = inlineBackend.getIfAvailable();
        if (backend == null) {
            throw new BusinessContractException("EXECUTION_BACKEND_UNAVAILABLE",
                    "同步直执后端未装配，无法同步执行: " + definitionCode);
        }
        return backend.run(snapshot, TenantContextHolder.getRequiredTenantId(), entityId, idempotencyKey);
    }

    /** 实例清单：两个后端各自的自持实例合并展示，来源以后端标识区分。 */
    public List<ScenarioRunOutcome> listInstances(String definitionCode) {
        requirePublished(definitionCode);
        List<ScenarioRunOutcome> outcomes = new ArrayList<>();
        inlineBackend.ifAvailable(backend -> backend.listInstances(definitionCode).forEach(row ->
                outcomes.add(new ScenarioRunOutcome(InlineSyncBackend.BACKEND_ID, row.getDefinitionCode(),
                        row.getDefinitionVersion(), row.getVerdict(), row.getResultId(), null, false))));
        eventDrivenBackend.ifAvailable(backend -> backend.listInstances(definitionCode).forEach(row ->
                outcomes.add(new ScenarioRunOutcome(EventDrivenBackend.BACKEND_ID, row.getDefinitionCode(),
                        row.getDefinitionVersion(), row.getVerdict(), row.getResultId(), null, false))));
        return outcomes;
    }

    /** 结果清单：读统一结果端口，只看该定义声明的结果类型。 */
    public List<BusinessResultRecord> listResults(String definitionCode) {
        ProcessDefinitionSnapshot snapshot = requirePublished(definitionCode);
        return resultPort.inventory(new BusinessResultQuery(snapshot.resultType(), null, null, false));
    }

    private ProcessDefinitionSnapshot requirePublished(String definitionCode) {
        return definitionPort.findPublished(definitionCode)
                .orElseThrow(() -> new BusinessContractException("DEFINITION_NOT_PUBLISHED",
                        "定义不存在或未发布: " + definitionCode));
    }
}
