package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractCollectionPlanMigrationApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.ContractCollectionPlanSyncProofApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsContractCollectionPlanSyncTemplate.*;

@Service
@RequiredArgsConstructor
public class ContractCollectionPlanSyncProofService implements ContractCollectionPlanSyncProofApi {
    private final SyncTaskService tasks;
    private final SyncBindingMapper bindings;
    private final ContractCollectionPlanMigrationApi plans;

    @Override
    @Transactional(readOnly=true)
    public List<Proof> verify(Query query) {
        long tenant = TenantContextHolder.getRequiredTenantId();
        if (query == null || !Objects.equals(query.tenantId(),tenant) || query.actorId() == null || query.actorId() <= 0
                || query.taskId() == null || query.taskId() <= 0 || query.sourceKeys() == null || query.sourceKeys().size() > 1000
                || new HashSet<>(query.sourceKeys()).size() != query.sourceKeys().size())
            throw new IllegalArgumentException("COLLECTION_PLAN_PROOF_SCOPE_INVALID");
        query.sourceKeys().forEach(SyncFieldMapper::primaryKey);
        var task = tasks.required(query.taskId());
        if (!Objects.equals(task.getTenantId(),tenant) || !ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem()))
            throw new IllegalArgumentException("COLLECTION_PLAN_PROOF_TASK_INVALID");
        if (query.sourceKeys().isEmpty()) return List.of();
        var selected = bindings.selectSourceKeys(new SyncQueries.SourceKeys(tenant,query.taskId(),OBJECT,OBJECT,query.sourceKeys()));
        var entries = new ArrayList<ContractCollectionPlanMigrationApi.Entry>();
        Map<String,Proof> proofs = new LinkedHashMap<>();
        Set<Long> targetIds = new HashSet<>();
        for (var binding : selected) {
            if (!Objects.equals(binding.getTenantId(),tenant) || !Objects.equals(binding.getTaskId(),query.taskId())
                    || !OBJECT.equals(binding.getSourceObject()) || !OBJECT.equals(binding.getObjectKey())
                    || !query.sourceKeys().contains(binding.getSourceKey()) || binding.getTargetId() == null || binding.getTargetId() <= 0
                    || !targetIds.add(binding.getTargetId()))
                throw new IllegalStateException("COLLECTION_PLAN_PROOF_BINDING_INVALID");
            var fields = JsonUtils.parseObject(binding.getFieldsJson(),Map.class);
            if (fields == null || !(fields.get("originalFields") instanceof Map<?,?> raw)
                    || !raw.keySet().equals(new HashSet<>(ORIGINAL_COLUMNS)))
                throw new IllegalStateException("COLLECTION_PLAN_PROOF_ORIGINAL_COLUMNS_MISSING");
            var formal = DppmsContractCollectionPlanSyncAdapter.decode(fields.get("plan"));
            if (!Objects.equals(formal.id(),binding.getTargetId()) || !Objects.equals(formal.tenantId(),tenant))
                throw new IllegalStateException("COLLECTION_PLAN_PROOF_TARGET_INVALID");
            Map<String,String> original = new LinkedHashMap<>();
            ORIGINAL_COLUMNS.forEach(name -> original.put(name,Objects.toString(raw.get(name),null)));
            if (!Objects.equals(binding.getSourceKey(),original.get("id"))
                    || proofs.put(binding.getSourceKey(),new Proof(binding.getSourceKey(),formal.id(),Collections.unmodifiableMap(original))) != null)
                throw new IllegalStateException("COLLECTION_PLAN_PROOF_SOURCE_DUPLICATE_OR_INVALID");
            entries.add(new ContractCollectionPlanMigrationApi.Entry(binding.getSourceKey(),formal.id(),null,formal,formal));
        }
        if (!proofs.keySet().equals(new HashSet<>(query.sourceKeys())))
            throw new IllegalStateException("COLLECTION_PLAN_SYNC_REQUIRED");
        var verified = plans.preview(new ContractCollectionPlanMigrationApi.Request(tenant,query.actorId(),
                "integration:"+tenant+":"+query.taskId(),entries,false));
        Set<String> checked = new HashSet<>();
        for (var result : verified) {
            var proof = proofs.get(result.sourceKey());
            if (proof == null || !checked.add(result.sourceKey()) || !"UNCHANGED".equals(result.action())
                    || result.after() == null || !Objects.equals(proof.targetId(),result.after().id())
                    || !Objects.equals(result.before(),result.after()))
                throw new IllegalStateException("COLLECTION_PLAN_CURRENT_FACT_DIFFERS_FROM_SYNC");
        }
        if (!checked.equals(proofs.keySet())) throw new IllegalStateException("COLLECTION_PLAN_OWNER_PROOF_INCOMPLETE");
        return query.sourceKeys().stream().map(proofs::get).toList();
    }
}
