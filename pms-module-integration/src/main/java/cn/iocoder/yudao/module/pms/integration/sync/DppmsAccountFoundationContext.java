package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.system.api.migration.SystemAccountMigrationPreview;
import java.util.*;

/** One invocation's Owner receipts, never a cache or an alternative source binding store. */
final class DppmsAccountFoundationContext {
    record Execution(DataSyncAdapter.Batch batch, boolean apply, DppmsAccountFoundationContext preceding) {}
    record Reference(Long id, Map<String,Object> fields) {}
    private final String owner;
    private final boolean preview;
    private final Map<String,Reference> users = new HashMap<>(), roles = new HashMap<>();
    private final Map<String,Long> plannedUsers = new HashMap<>(), plannedRoles = new HashMap<>();
    private final Set<SystemAccountMigrationPreview.Membership> memberships = new HashSet<>();
    private long nextId = -1;

    DppmsAccountFoundationContext(DataSyncAdapter.Batch batch, boolean apply) {
        owner = batch.owner(); preview = !apply;
    }
    void requireExecution(Execution execution) {
        if (preview == execution.apply() || !Objects.equals(owner, execution.batch().owner()))
            throw new IllegalArgumentException("账号基础同轮上下文不能跨运行或读写模式复用");
    }
    void accounts(List<DataSyncAdapter.Change> changes) {
        for (var change : changes) {
            if (!(change.after().get("profile") instanceof Map<?,?> profile)) continue;
            String username = Objects.toString(profile.get("username"), null);
            Long id = identity(change.targetId(), username, plannedUsers);
            put(users, change.object()+":"+change.sourceKey(), new Reference(id, change.after()));
        }
    }
    void roles(List<DataSyncAdapter.Change> changes) {
        for (var change : changes) {
            if (change.after().get("code") == null) continue;
            String code = change.after().get("code").toString();
            Long id = identity(change.targetId(), code, plannedRoles);
            String table = switch(change.object()) {case "T_ROLE" -> "t_role";case "FND_ROLE" -> "fnd_roles";
                default -> throw new IllegalArgumentException("本轮角色回执来源未知");};
            put(roles, table+":"+change.sourceKey(), new Reference(id, change.after()));
        }
    }
    void memberships(List<DataSyncAdapter.Change> changes) {
        for (var change : changes) if (change.after().get("userId") instanceof Number user
                && change.after().get("roleCode") instanceof String code)
            memberships.add(new SystemAccountMigrationPreview.Membership(user.longValue(), code));
    }
    private Long identity(Long actual, String businessKey, Map<String,Long> planned) {
        if (actual != null) {
            if (actual <= 0) throw new IllegalArgumentException("Owner正式回执ID无效");
            return actual;
        }
        if (!preview || businessKey == null || businessKey.isBlank())
            throw new IllegalArgumentException("账号基础Owner未返回正式身份");
        return planned.computeIfAbsent(businessKey, ignored -> nextId--);
    }
    private static void put(Map<String,Reference> values, String source, Reference reference) {
        var previous = values.putIfAbsent(source, reference);
        if (previous != null && !previous.equals(reference))
            throw new IllegalArgumentException("本轮来源身份具有冲突Owner回执");
    }
    Reference user(String source, boolean authorization) {
        var result = users.get(source);
        if (result == null || authorization && Boolean.TRUE.equals(result.fields().get("_auditOnly")))
            throw new IllegalArgumentException("本轮来源账号未迁入或只有审计关联，不能生成授权");
        return result;
    }
    Reference role(String source) {
        var result = roles.get(source);
        if (result == null || !Objects.equals(0, result.fields().get("status")))
            throw new IllegalArgumentException("本轮来源角色未迁入或已停用");
        return result;
    }
    Map<String,Reference> users() { return Collections.unmodifiableMap(users); }
    SystemAccountMigrationPreview preview() {
        if (!preview) throw new IllegalStateException("正式执行不能构造预览引用");
        Map<Long,String> codes = new HashMap<>(); plannedRoles.forEach((code,id) -> codes.put(id,code));
        return new SystemAccountMigrationPreview(TenantContextHolder.getRequiredTenantId(), owner,
                Set.copyOf(plannedUsers.values()), codes, memberships);
    }
}
