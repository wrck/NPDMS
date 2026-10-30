package cn.iocoder.yudao.module.pms.bindings.backend;

import cn.iocoder.yudao.module.pms.bindings.dal.dataobject.InlineInstanceDO;
import cn.iocoder.yudao.module.pms.bindings.dal.mysql.InlineInstanceMapper;
import cn.iocoder.yudao.module.pms.bindings.service.ScenarioRunOutcome;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.ProcessDefinitionSnapshot;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.RuleVerdict;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.BusinessFactPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultFormationPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultRecord;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.ResultSemantics;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Objects;

/**
 * 执行后端一：同步直执。按请求即时取事实、判定并形成结果，
 * 以调用方幂等键去重；不消费事件，不依赖任何后台任务。
 */
@Service
@ConditionalOnProperty(name = "pms.bindings.backends.inline-sync.enabled",
        havingValue = "true", matchIfMissing = true)
public class InlineSyncBackend {

    public static final String BACKEND_ID = "bindings-inline-sync";

    private final InlineInstanceMapper instanceMapper;
    private final BusinessFactPort factPort;
    private final BusinessResultFormationPort formationPort;

    public InlineSyncBackend(InlineInstanceMapper instanceMapper, BusinessFactPort factPort,
                             BusinessResultFormationPort formationPort) {
        this.instanceMapper = instanceMapper;
        this.factPort = factPort;
        this.formationPort = formationPort;
    }

    public record RunCommand(ProcessDefinitionSnapshot snapshot, EntityRef ref, String idempotencyKey) {}

    @Transactional(rollbackFor = Exception.class)
    public ScenarioRunOutcome run(RunCommand command) {
        var snapshot = command.snapshot();
        var ref = command.ref();
        var idempotencyKey = command.idempotencyKey();
        if (!Objects.equals(snapshot.ownerModule(), ref.ownerModule())
                || !Objects.equals(snapshot.entityType(), ref.entityType())) {
            throw new BusinessContractException("EXECUTION_OBJECT_MISMATCH", "执行对象与定义不一致");
        }
        Optional<InlineInstanceDO> existing = instanceMapper.selectByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            InlineInstanceDO row = existing.get();
            if (!Objects.equals(row.getTenantId(), ref.tenantId())
                    || !Objects.equals(row.getDefinitionCode(), snapshot.definitionCode())
                    || !Objects.equals(row.getOwnerModule(), ref.ownerModule())
                    || !Objects.equals(row.getEntityType(), ref.entityType())
                    || !Objects.equals(row.getEntityId(), ref.entityId())) {
                throw new BusinessContractException("IDEMPOTENCY_DIGEST_CONFLICT", "幂等键已绑定其他定义或对象");
            }
            return new ScenarioRunOutcome(BACKEND_ID, row.getDefinitionCode(), row.getDefinitionVersion(),
                    row.getVerdict(), row.getResultId(), null, true);
        }
        RuleVerdict verdict = RuleSemanticEvaluator.evaluate(snapshot, ref, factPort, "bindings:inline-sync");
        String resultId = null;
        BusinessResultRecord result = null;
        if (Boolean.TRUE.equals(verdict.satisfied())) {
            // 形成依据是本次同步执行的身份（幂等键），重复执行按依据幂等。
            result = formationPort.form(snapshot.resultType(), ref, ResultSemantics.NEW_RESULT,
                    idempotencyKey, BACKEND_ID);
            resultId = result.resultId();
        }
        InlineInstanceDO row = new InlineInstanceDO();
        row.setTenantId(ref.tenantId());
        row.setDefinitionCode(snapshot.definitionCode());
        row.setDefinitionVersion(snapshot.definitionVersion());
        row.setOwnerModule(snapshot.ownerModule());
        row.setEntityType(snapshot.entityType());
        row.setEntityId(ref.entityId());
        row.setIdempotencyKey(idempotencyKey);
        row.setVerdict(verdictText(verdict));
        row.setResultId(resultId);
        instanceMapper.insert(row);
        return new ScenarioRunOutcome(BACKEND_ID, snapshot.definitionCode(), snapshot.definitionVersion(),
                verdictText(verdict), resultId, result, false);
    }

    public List<InlineInstanceDO> listInstances(String definitionCode) {
        return instanceMapper.selectByDefinition(definitionCode);
    }

    static String verdictText(RuleVerdict verdict) {
        if (verdict.satisfied() == null) {
            return "UNKNOWN";
        }
        return verdict.satisfied() ? "SATISFIED" : "UNSATISFIED";
    }
}
