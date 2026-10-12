package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemRoleMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemRoleMigrationApi.*;

/** A separately adjudicated correction; ordinary role sync keeps its manual-change guard. */
@Component @RequiredArgsConstructor
public class DppmsRoleAuditCorrectionAdapter implements DataSyncAdapter {
    public static final String ADAPTER = "DPPMS_ROLE_AUDIT_CORRECTION";
    static final String PROOF = "migrationAuditCorrection";
    static final List<String> SOURCE_COLUMNS = List.of("id", "roleName", "defaultPage", "status", "effectiveFrom",
            "effectiveTo", "createBy", "createTime", "updateBy", "updateTime", "roleRemark");
    record Proof(Long originalTaskId, Long originalRunId, Long targetId, String generatedActor,
                 String expectedFieldsJson, String expectedSourceFieldsJson) {}
    private final SystemRoleMigrationApi roles;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    private final SyncBindingMapper bindings;

    @Override public Descriptor descriptor() {
        var columns = new ArrayList<>(SOURCE_COLUMNS); columns.add(PROOF);
        return new Descriptor(ADAPTER, "DPPMS已裁决角色空审计人员纠正", List.of(new ObjectDescriptor("fnd_roles",
                "原角色及明确纠正证据", columns.stream().map(c -> new Field(c, c, "STRING", false)).toList(),
                "SYSTEM", "Role", "system_role", false)), List.of("RETAIN"), List.of("UPSERT"), false);
    }
    @Override public boolean sharesTargetAcrossSources() { return true; }
    @Override public void refreshCaches() { roles.refreshCaches(); }
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ, rollbackFor=Exception.class)
    public List<Change> preview(Batch batch) { return execute(batch, false); }
    @Override public List<Change> apply(Batch batch) { return execute(batch, true); }

    private List<Change> execute(Batch batch, boolean apply) {
        if (!batch.full() || !batch.adoptExisting() || batch.clearBeforeLoad() || !"RETAIN".equals(batch.missingPolicy())
                || !"UPSERT".equals(batch.loadingMode()) || batch.rows().isEmpty())
            throw new IllegalArgumentException("角色审计纠正须明确范围、受控接管的整批运行，禁止清空或分块");
        long tenant = TenantContextHolder.getRequiredTenantId();
        String[] owner = batch.owner() == null ? new String[0] : batch.owner().split(":");
        if (owner.length != 3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))
            throw new IllegalArgumentException("角色审计纠正所有权失效");
        var task = tasks.required(SyncFieldMapper.primaryKey(owner[2]));
        var run = task.getActiveRunId() == null ? null : runs.selectScoped(new SyncQueries.Id(tenant, task.getActiveRunId()));
        if (!ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem()) || run == null
                || !Objects.equals(run.getTaskId(), task.getId()) || !Objects.equals(run.getConfigVersion(), task.getVersion())
                || !Objects.equals(run.getPreview(), !apply) || !(apply ? "APPLYING" : "VALIDATING").equals(run.getStatus()))
            throw new IllegalArgumentException("角色纠正任务、运行身份、阶段或版本失效");
        Map<String, Proof> proofs = new LinkedHashMap<>();
        for (var row : batch.rows()) {
            if (!"fnd_roles".equals(row.object()) || !row.fields().keySet().containsAll(SOURCE_COLUMNS)
                    || !Objects.equals(row.sourceKey(), Objects.toString(row.fields().get("id"), null))
                    || !(row.fields().get(PROOF) instanceof String text) || text.isBlank())
                throw new IllegalArgumentException("角色原字段、来源身份或明确证据缺失");
            Proof proof = JsonUtils.parseObject(text, Proof.class);
            if (proof == null || proof.originalTaskId() == null || proof.originalRunId() == null || proof.targetId() == null
                    || proof.targetId() <= 0 || proof.generatedActor() == null || proof.generatedActor().isBlank()
                    || proof.expectedFieldsJson() == null || proof.expectedSourceFieldsJson() == null
                    || row.targetId() != null && !Objects.equals(row.targetId(), proof.targetId())
                    || proofs.put(row.sourceKey(), proof) != null)
                throw new IllegalArgumentException("角色纠正目标或证据无效/重复");
            var expected = JsonUtils.parseMap(proof.expectedSourceFieldsJson());
            for (String column : SOURCE_COLUMNS) {
                if (!expected.containsKey(column)) throw new IllegalArgumentException("纠正未核对全部角色原字段");
                Object actual = row.fields().get(column), previous = expected.get(column);
                boolean date = Set.of("effectiveFrom", "effectiveTo", "createTime", "updateTime").contains(column);
                if (!Objects.equals(date && actual != null ? SyncFieldMapper.sourceTime(actual) : Objects.toString(actual, null),
                        date && previous != null ? SyncFieldMapper.sourceTime(previous) : Objects.toString(previous, null)))
                    throw new IllegalArgumentException("原角色在裁决后变化，停止纠正");
            }
        }
        var first = proofs.values().iterator().next();
        if (proofs.values().stream().anyMatch(p -> !Objects.equals(p.originalTaskId(), first.originalTaskId())
                || !Objects.equals(p.originalRunId(), first.originalRunId()) || !Objects.equals(p.generatedActor(), first.generatedActor())))
            throw new IllegalArgumentException("角色纠正不能混合原任务、运行或生成操作者");
        var original = tasks.required(first.originalTaskId());
        var receipt = runs.selectScoped(new SyncQueries.Id(tenant, first.originalRunId()));
        if (!"DPPMS_ROLES".equals(original.getAdapter()) || !"DPPMS".equals(original.getSourceSystem())
                || !Objects.equals(original.getConnectionId(), task.getConnectionId()) || original.getActiveRunId() != null
                || receipt == null || !Objects.equals(receipt.getTaskId(), original.getId())
                || !Boolean.FALSE.equals(receipt.getPreview()) || !"SUCCESS".equals(receipt.getStatus())
                || !Objects.equals(receipt.getCreator(), first.generatedActor()) || receipt.getResultJson() == null)
            throw new IllegalArgumentException("原角色创建运行或同源连接无法核实");
        var changes = JsonUtils.parseArray(receipt.getResultJson(), Change.class);
        var originalBindings = bindings.selectSourceKeys(new SyncQueries.SourceKeys(tenant, original.getId(),
                "FND_ROLE", "fnd_roles", List.copyOf(proofs.keySet())));
        var byKey = new HashMap<String, cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO>();
        for (var binding : originalBindings) if (byKey.put(binding.getSourceKey(), binding) != null)
            throw new IllegalArgumentException("角色原绑定重复");
        var corrections = new ArrayList<AuditCorrection>(); var targetKeys = new HashMap<Long, String>();
        for (var pair : proofs.entrySet()) {
            var proof = pair.getValue(); var binding = byKey.get(pair.getKey());
            var expected = JsonUtils.parseMap(proof.expectedFieldsJson());
            if (binding == null || !Objects.equals(binding.getTargetId(), proof.targetId())
                    || !Objects.equals(binding.getLastRunId(), proof.originalRunId())
                    || !Objects.equals(JsonUtils.parseMap(binding.getFieldsJson()), expected))
                throw new IllegalArgumentException("角色绑定目标、原运行或完整业务审计已变化");
            var created = changes.stream().filter(c -> "FND_ROLE".equals(c.object()) && pair.getKey().equals(c.sourceKey())).toList();
            if (created.size() != 1 || !"CREATED".equals(created.getFirst().action())
                    || !Objects.equals(created.getFirst().targetId(), proof.targetId()) || !Objects.equals(created.getFirst().after(), expected))
                throw new IllegalArgumentException("目标不是原成功运行中明确新建的角色");
            var restored = DppmsRoleSyncAdapter.lastRole(proof.targetId(), expected); var audit = restored.definition();
            if (!Objects.equals(audit.code(), DppmsRoleCodes.targetFnd(Objects.toString(DppmsRoleCodes.FND_CODES.get(pair.getKey()), null)))
                    || targetKeys.put(restored.id(), pair.getKey()) != null
                    || audit.creator() != null && audit.updater() != null)
                throw new IllegalArgumentException("角色编码、原NULL审计字段或目标唯一性不符合纠正范围");
            var source = JsonUtils.parseMap(proof.expectedSourceFieldsJson());
            if (audit.creator() == null && source.get("createBy") != null || audit.updater() == null && source.get("updateBy") != null)
                throw new IllegalArgumentException("原审计操作者不是NULL，禁止恢复NULL");
            var before = new Role(restored.id(), new Definition(audit.code(), audit.name(), audit.sort(), audit.remark(),
                    audit.creator() == null ? first.generatedActor() : audit.creator(), audit.createTime(),
                    audit.updater() == null ? first.generatedActor() : audit.updater(), audit.updateTime()),
                    restored.status(), restored.type(), restored.dataScope(), restored.dataScopeDeptIds());
            corrections.add(new AuditCorrection(before, restored));
        }
        var request = new AuditCorrectionRequest(tenant, batch.owner(), first.generatedActor(), corrections);
        return (apply ? roles.applyAuditCorrection(request) : roles.previewAuditCorrection(request)).stream().map(result ->
                new Change("fnd_roles", targetKeys.get(result.after().id()), result.after().id(), result.action(),
                        DppmsRoleSyncAdapter.fields(result.before()), DppmsRoleSyncAdapter.fields(result.after()),
                        "仅恢复明确新角色原NULL审计人员；原运行" + first.originalRunId() + "及日期、定义、权限保持")).toList();
    }
}
