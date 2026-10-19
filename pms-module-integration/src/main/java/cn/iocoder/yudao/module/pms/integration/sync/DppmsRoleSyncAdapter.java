package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries;
import cn.iocoder.yudao.module.system.api.migration.SystemRoleMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DppmsRoleSyncAdapter implements DataSyncAdapter {
    private final SystemRoleMigrationApi roles;
    private final SyncTaskService tasks;
    private final SyncBindingMapper bindings;

    @Override public Descriptor descriptor() {
        List<Field> fields = List.of(new Field("sourceRoleId", "原角色主键（仅关联）", "LONG", true),
                new Field("sourceCode", "已裁决原角色业务编码", "STRING", true),
                new Field("name", "角色名称", "STRING", true), new Field("sort", "排序", "LONG", true),
                new Field("remark", "角色备注", "STRING", false), new Field("status", "角色状态", "LONG", true),
                new Field("creatorName", "原创建账号", "STRING", false), new Field("creatorSourceKey", "原创建账号主键", "STRING", false),
                new Field("updaterName", "原更新账号", "STRING", false), new Field("updaterSourceKey", "原更新账号主键", "STRING", false),
                new Field("createTime", "原创建时间", "DATETIME", true), new Field("updateTime", "原更新时间", "DATETIME", false));
        return new Descriptor("DPPMS_ROLES", "DPPMS正式角色迁移", List.of(
                new ObjectDescriptor("T_ROLE", "t_role角色", fields, "SYSTEM", "Role", "system_role", false),
                new ObjectDescriptor("FND_ROLE", "fnd_roles角色", fields, "SYSTEM", "Role", "system_role", false)),
                List.of("RETAIN"), List.of("UPSERT"), false);
    }
    @Override public boolean sharesTargetAcrossSources() { return true; }
    @Override public List<Change> preview(Batch batch) { return execute(new DppmsAccountFoundationContext.Execution(batch, false, null)); }
    @Override public List<Change> apply(Batch batch) { return execute(new DppmsAccountFoundationContext.Execution(batch, true, null)); }
    List<Change> afterAccounts(DppmsAccountFoundationContext.Execution execution) { return execute(execution); }
    @Override public void refreshCaches() { roles.refreshCaches(); }

    private List<Change> execute(DppmsAccountFoundationContext.Execution execution) {
        var batch = execution.batch(); boolean apply = execution.apply();
        if (execution.preceding() != null) execution.preceding().requireExecution(execution);
        if (batch.clearBeforeLoad() || !"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy()))
            throw new IllegalArgumentException("角色迁移只支持追加更新并保留来源缺失，不支持清空或重编号");
        long tenant = TenantContextHolder.getRequiredTenantId();
        var owner = batch.owner() == null ? new String[0] : batch.owner().split(":");
        if (owner.length != 3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))
            throw new IllegalArgumentException("角色迁移运行所有权无效");
        var task = tasks.required(Long.parseLong(owner[2]));
        if (!"DPPMS".equals(task.getSourceSystem()) || !DppmsConsolidation.accepts(task, "DPPMS_ROLES"))
            throw new IllegalArgumentException("角色迁移不能混用其他来源任务");
        Map<String, Binding> prior = new HashMap<>();
        batch.bindings().forEach(binding -> prior.put(binding.object() + ":" + binding.sourceKey(), binding));
        List<SystemRoleMigrationApi.Entry> entries = new ArrayList<>();
        Map<String, Long> actors = execution.preceding() == null ? actors(batch, tenant, task.getConnectionId())
                : currentActors(execution);
        for (Row row : batch.rows()) {
            var fields = row.fields();
            boolean fnd = "FND_ROLE".equals(row.object());
            if (!fnd && !"T_ROLE".equals(row.object())) throw new IllegalArgumentException("角色来源对象无效");
            String sourceCode = string(fields, "sourceCode");
            String sourceId = Objects.toString(fields.get("sourceRoleId"), null);
            if (!Objects.equals(sourceId, row.sourceKey())) throw new IllegalArgumentException("角色来源主键与提取身份不同");
            if (fnd && (!Objects.equals(DppmsRoleCodes.FND_CODES.get(sourceId), sourceCode) || "15".equals(sourceId)))
                throw new IllegalArgumentException("fnd角色编码未按常量语义裁决转换，或为明确排除的停用角色");
            if (!fnd && !Objects.equals(DppmsRoleCodes.T_SOURCE_CODES.get(sourceId), sourceCode))
                throw new IllegalArgumentException("t_role来源身份和业务编码不在已裁决清单，须补充裁决");
            String code = fnd ? DppmsRoleCodes.targetFnd(sourceCode) : Objects.toString(DppmsRoleCodes.T_CODES.get(sourceCode), null);
            if (code == null || fields.get("status") == null || Integer.parseInt(fields.get("status").toString()) != 0)
                throw new IllegalArgumentException("来源角色编码或状态未裁决，不能启用或生成猜测编码");
            boolean reference = DppmsRoleCodes.reference(row.object(), sourceCode);
            LocalDateTime created = time(fields.get("createTime"));
            LocalDateTime updated = time(fields.get("updateTime"));
            var definition = new SystemRoleMigrationApi.Definition(code, string(fields, "name"),
                    fields.get("sort") == null ? null : Integer.valueOf(fields.get("sort").toString()), string(fields, "remark"),
                    reference ? null : actor(row, "creator", actors), created,
                    reference ? null : actor(row, "updater", actors), updated == null ? created : updated);
            Binding previous = prior.get(row.object() + ":" + row.sourceKey());
            entries.add(new SystemRoleMigrationApi.Entry(row.object(), row.sourceKey(), definition, row.targetId(),
                    previous == null ? null : lastDefinition(previous.lastFields()), reference));
        }
        var request = new SystemRoleMigrationApi.Request(tenant, batch.owner(), entries, batch.adoptExisting());
        var results = apply ? roles.apply(request) : execution.preceding() == null ? roles.preview(request)
                : roles.previewAfterAccounts(new SystemRoleMigrationApi.AccountPreview(request, execution.preceding().preview()));
        Map<String, Row> sources = new HashMap<>();
        batch.rows().forEach(row -> sources.put(row.object() + ":" + row.sourceKey(), row));
        return results.stream().map(result -> {
            Row source = sources.get(result.object() + ":" + result.sourceKey());
            if (source == null) throw new IllegalStateException("角色Owner返回未知来源身份");
            Map<String, Object> after = fields(result.after());
            after.put("sourceCode", source.fields().get("sourceCode"));
            after.put("_sourceUpdatedAt", source.fields().get("_sourceUpdatedAt"));
            return new Change(result.object(), result.sourceKey(), result.after() == null ? null : result.after().id(),
                    result.action(), fields(result.before()), after, result.message());
        }).toList();
    }

    private Map<String,Long> currentActors(DppmsAccountFoundationContext.Execution execution) {
        Map<String,Long> result = new HashMap<>();
        for (var row : execution.batch().rows()) {
            if (DppmsRoleCodes.reference(row.object(), string(row.fields(), "sourceCode"))) continue;
            String table = "FND_ROLE".equals(row.object()) ? "fnd_user_info" : "t_user";
            for (String kind : List.of("creator", "updater")) {
                String name = string(row.fields(), kind+"Name");
                if (name == null || name.isEmpty()) continue;
                String key = string(row.fields(), kind+"SourceKey");
                if (key == null) throw new IllegalArgumentException("角色历史操作者来源身份不唯一");
                var reference = execution.preceding().user(table+":"+key, false);
                var profile = (Map<?,?>) reference.fields().get("profile");
                if (profile == null || !Objects.equals(name, profile.get("username")))
                    throw new IllegalArgumentException("角色历史操作者与同轮账号回执不同");
                result.put(table+":"+key, reference.id());
            }
        }
        return result;
    }

    private Map<String, Long> actors(Batch batch, long tenant, Long connectionId) {
        Map<String, Set<String>> keys = new HashMap<>();
        for (Row row : batch.rows()) {
            if (DppmsRoleCodes.reference(row.object(), string(row.fields(), "sourceCode"))) continue;
            String table = "FND_ROLE".equals(row.object()) ? "fnd_user_info" : "t_user";
            for (String kind : List.of("creator", "updater")) {
                String name = string(row.fields(), kind + "Name");
                if (name == null || name.isEmpty()) continue;
                String key = string(row.fields(), kind + "SourceKey");
                if (key == null) throw new IllegalArgumentException("角色原操作者不存在或同名未裁决，不能按名字猜配");
                keys.computeIfAbsent(table, ignored -> new HashSet<>()).add(key);
            }
        }
        Map<String, Long> resolved = new HashMap<>();
        for (var group : keys.entrySet()) {
            var values = bindings.selectIdentitySources(new SyncQueries.IdentitySources(tenant, "DPPMS", connectionId,
                    "DPPMS_USERS", group.getKey(), List.copyOf(group.getValue())));
            for (var binding : values) {
                String key = group.getKey() + ":" + binding.getSourceKey();
                if (binding.getTargetId() == null) throw new IllegalArgumentException("操作者来源绑定没有正式账号");
                Long previous = resolved.putIfAbsent(key, binding.getTargetId());
                if (previous != null && !previous.equals(binding.getTargetId()))
                    throw new IllegalArgumentException("操作者来源账号关联多个正式用户，需要重新裁决");
            }
            for (String key : group.getValue()) if (!resolved.containsKey(group.getKey() + ":" + key))
                throw new IllegalArgumentException("请先完成用户迁移及操作者绑定，再迁移角色审计字段");
        }
        return resolved;
    }
    private static String actor(Row row, String kind, Map<String, Long> actors) {
        String name = string(row.fields(), kind + "Name");
        if (name == null || name.isEmpty()) return name;
        String table = "FND_ROLE".equals(row.object()) ? "fnd_user_info" : "t_user";
        return Objects.toString(actors.get(table + ":" + string(row.fields(), kind + "SourceKey")), null);
    }
    private static SystemRoleMigrationApi.Definition lastDefinition(Map<String, Object> fields) {
        return new SystemRoleMigrationApi.Definition(string(fields, "code"), string(fields, "name"),
                fields.get("sort") == null ? null : Integer.valueOf(fields.get("sort").toString()), string(fields, "remark"),
                string(fields, "creator"), time(fields.get("createTime")), string(fields, "updater"), time(fields.get("updateTime")));
    }
    static Map<String, Object> fields(SystemRoleMigrationApi.Role role) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (role == null) return fields;
        var value = role.definition();
        fields.put("code", value.code()); fields.put("name", value.name()); fields.put("sort", value.sort()); fields.put("remark", value.remark());
        fields.put("creator", value.creator()); fields.put("createTime", value.createTime() == null ? null : value.createTime().toString());
        fields.put("updater", value.updater()); fields.put("updateTime", value.updateTime() == null ? null : value.updateTime().toString());
        fields.put("status", role.status()); fields.put("type", role.type()); fields.put("dataScope", role.dataScope());
        fields.put("dataScopeDeptIds", role.dataScopeDeptIds());
        return fields;
    }
    static SystemRoleMigrationApi.Role lastRole(Long id, Map<String, Object> fields) {
        Set<Long> departments = new HashSet<>();
        Object values = fields.get("dataScopeDeptIds");
        if (values instanceof Collection<?> collection)
            collection.forEach(value -> departments.add(SyncFieldMapper.primaryKey(value.toString())));
        else if (values != null) throw new IllegalArgumentException("角色数据范围证据格式无效");
        return new SystemRoleMigrationApi.Role(id, lastDefinition(fields),
                Integer.valueOf(fields.get("status").toString()), Integer.valueOf(fields.get("type").toString()),
                Integer.valueOf(fields.get("dataScope").toString()), Set.copyOf(departments));
    }
    private static String string(Map<String, Object> fields, String key) { return Objects.toString(fields.get(key), null); }
    private static LocalDateTime time(Object value) { return value == null ? null : SyncFieldMapper.sourceTime(value); }
}
