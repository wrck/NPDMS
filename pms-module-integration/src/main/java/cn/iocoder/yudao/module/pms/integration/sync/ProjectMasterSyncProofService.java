package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.ProjectMasterSyncProofApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectMasterSyncTemplate.*;

@Service
@RequiredArgsConstructor
public class ProjectMasterSyncProofService implements ProjectMasterSyncProofApi {
    private final SyncTaskMapper tasks;
    private final SyncBindingMapper bindings;
    private final SyncRunMapper runs;
    private final ProjectMasterMigrationApi projects;

    @Override @Transactional(readOnly=true)
    public List<Proof> verify(Query query) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        if (query==null || !Objects.equals(query.tenantId(),tenant) || query.actorId()==null || query.actorId()<=0
                || query.sourceKeys()==null || query.sourceKeys().size()>1000
                || new HashSet<>(query.sourceKeys()).size()!=query.sourceKeys().size())
            throw new IllegalArgumentException("PROJECT_MASTER_PROOF_SCOPE_INVALID");
        query.sourceKeys().forEach(SyncFieldMapper::primaryKey);
        if (query.sourceKeys().isEmpty()) return List.of();
        var task=tasks.selectIdentity(new SyncQueries.TaskIdentity(tenant,"DPPMS",ADAPTER));
        if (task==null || !Objects.equals(task.getTenantId(),tenant) || !ADAPTER.equals(task.getAdapter())
                || !"DPPMS".equals(task.getSourceSystem())) throw new IllegalStateException("PROJECT_MASTER_SYNC_REQUIRED");
        var selected=bindings.selectSourceKeys(new SyncQueries.SourceKeys(tenant,task.getId(),OBJECT,OBJECT,query.sourceKeys()));
        Map<String,Proof> proofs=new LinkedHashMap<>();Set<Long> targetIds=new HashSet<>();
        var entries=new ArrayList<ProjectMasterMigrationApi.Entry>();
        for (var binding:selected) {
            if (!Objects.equals(binding.getTenantId(),tenant) || !Objects.equals(binding.getTaskId(),task.getId())
                    || !OBJECT.equals(binding.getObjectKey()) || !OBJECT.equals(binding.getSourceObject())
                    || !query.sourceKeys().contains(binding.getSourceKey()) || binding.getTargetId()==null
                    || binding.getTargetId()<=0 || !targetIds.add(binding.getTargetId()) || binding.getLastRunId()==null)
                throw new IllegalStateException("PROJECT_MASTER_PROOF_BINDING_INVALID");
            var run=runs.selectScoped(new SyncQueries.Id(tenant,binding.getLastRunId()));
            if (run==null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                    || !Boolean.FALSE.equals(run.getPreview()) || !"SUCCESS".equals(run.getStatus()))
                throw new IllegalStateException("PROJECT_MASTER_PROOF_RUN_INVALID");
            var fields=JsonUtils.parseMap(binding.getFieldsJson());
            if (fields==null || !(fields.get("originalFields") instanceof Map<?,?> original)
                    || !original.keySet().equals(new HashSet<>(ORIGINAL_COLUMNS)) || !(fields.get("project") instanceof Map<?,?> saved))
                throw new IllegalStateException("PROJECT_MASTER_PROOF_ORIGINAL_COLUMNS_MISSING");
            var formal=DppmsProjectMasterSyncAdapter.decode(saved);
            if (!Objects.equals(formal.id(),binding.getTargetId()) || !Objects.equals(formal.tenantId(),tenant))
                throw new IllegalStateException("PROJECT_MASTER_PROOF_TARGET_INVALID");
            Map<String,String> raw=new LinkedHashMap<>();
            ORIGINAL_COLUMNS.forEach(column->raw.put(column,Objects.toString(original.get(column),null)));
            if (!Objects.equals(binding.getSourceKey(),raw.get("projectId"))
                    || proofs.put(binding.getSourceKey(),new Proof(binding.getSourceKey(),formal.id(),Collections.unmodifiableMap(raw)))!=null)
                throw new IllegalStateException("PROJECT_MASTER_PROOF_SOURCE_INVALID");
            entries.add(new ProjectMasterMigrationApi.Entry(binding.getSourceKey(),formal.id(),null,raw,formal.companyCode(),
                    DppmsDepartmentCodeRules.normalize(raw.get("column001")),formal.creator(),formal.updater(),formal));
        }
        if (!proofs.keySet().equals(new HashSet<>(query.sourceKeys()))) throw new IllegalStateException("PROJECT_MASTER_SYNC_REQUIRED");
        var verified=projects.preview(new ProjectMasterMigrationApi.Request(tenant,query.actorId(),
                "integration:"+tenant+":"+task.getId(),entries,false));
        Set<String> checked=new HashSet<>();
        for (var result:verified) {
            var proof=proofs.get(result.sourceKey());
            if (proof==null || !checked.add(result.sourceKey()) || !"UNCHANGED".equals(result.action())
                    || result.after()==null || !Objects.equals(proof.targetId(),result.after().id())
                    || !Objects.equals(result.before(),result.after()))
                throw new IllegalStateException("PROJECT_MASTER_CURRENT_FACT_DIFFERS_FROM_SYNC");
        }
        if (!checked.equals(proofs.keySet())) throw new IllegalStateException("PROJECT_MASTER_OWNER_PROOF_INCOMPLETE");
        return query.sourceKeys().stream().map(proofs::get).toList();
    }
}
