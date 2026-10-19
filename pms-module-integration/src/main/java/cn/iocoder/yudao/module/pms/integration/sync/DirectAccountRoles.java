package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.system.api.migration.SystemRoleMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemAccountMigrationPreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountRows.RoleRow;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountRoleMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.RoleMigrationTenantQuery;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.RoleMigrationAuditActorQuery;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import cn.iocoder.yudao.module.system.enums.permission.RoleTypeEnum;
import cn.iocoder.yudao.module.system.enums.permission.DataScopeEnum;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

@RequiredArgsConstructor
/** Approved rule copy from SystemRoleMigrationApiImpl; isolated direct persistence, no Owner invocation. */
final class DirectAccountRoles implements SystemRoleMigrationApi {
    private final DirectAccountRoleMapper roles;
    private final CacheManager caches;
    private final DataSource dataSource;

    @Override public List<Result> preview(Request request) {
        validateRequest(request);
        return previewAfterAccounts(new AccountPreview(request,
                SystemAccountMigrationPreview.empty(request.tenantId(), request.owner())));
    }

    @Override public List<Result> previewAfterAccounts(AccountPreview preview) {
        Request request = preview.request();
        validateRequest(request);
        preview.preceding().requireOwner(request.tenantId(), request.owner());
        validateActors(request, preview.preceding().plannedUserIds());
        return plan(request, roles.selectListForMigration(new RoleMigrationTenantQuery(request.tenantId()))
                .stream().map(DirectAccountRoles::snapshot).toList());
    }

    @Override
        public List<Result> apply(Request request) {
        validateRequest(request);
        // No role code/name unique index exists in the approved structure. InnoDB inventory
        // next-key locks must cover absent catalogue entries as well as existing records.
        requireRepeatableRead();
        List<Role> current = roles.selectListForMigrationForUpdate(new RoleMigrationTenantQuery(request.tenantId()))
                .stream().map(DirectAccountRoles::snapshot).toList();
        validateActors(request, Set.of());
        List<Result> results = plan(request, current);
        if (results.stream().anyMatch(r -> "CONFLICT".equals(r.action())))
            throw new IllegalArgumentException("角色迁移发生编码、名称、绑定或历史字段冲突，整批未承接");
        Map<String, Role> saved = new HashMap<>();
        for (Result result : results) {
            if (!Set.of("CREATED", "UPDATED").contains(result.action())) continue;
            Role after = result.after();
            if (saved.containsKey(after.definition().code())) continue;
            long id = after.id() == null ? IdWorker.getId() : after.id();
            RoleRow entity = entity(request.tenantId(), id, after);
            int changed = "CREATED".equals(result.action())
                    ? roles.insertHistoricalRole(new DirectAccountRoleMapper.Write(entity, after.definition())) : roles.updateHistoricalDefinition(new DirectAccountRoleMapper.Write(entity, after.definition()));
            if (changed != 1) throw new IllegalStateException("角色承接目标发生变化");
            saved.put(after.definition().code(), new Role(id, after.definition(), after.status(), after.type(),
                    after.dataScope(), after.dataScopeDeptIds()));
        }
        return results.stream().map(result -> new Result(result.object(), result.sourceKey(), result.before(),
                saved.getOrDefault(result.after().definition().code(), result.after()), result.action(), result.message())).toList();
    }

    @Override public List<AuditCorrectionResult> previewAuditCorrection(AuditCorrectionRequest request) { throw new UnsupportedOperationException("Not a direct-task operation"); }
    @Override public List<AuditCorrectionResult> applyAuditCorrection(AuditCorrectionRequest request) { throw new UnsupportedOperationException("Not a direct-task operation"); }
    @Override public void refreshCaches() {
        var cache = caches.getCache(RedisKeyConstants.ROLE);
        if (cache != null) cache.clear();
    }

    private void requireRepeatableRead() {
        requireTransaction();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (connection.getTransactionIsolation() != Connection.TRANSACTION_REPEATABLE_READ)
                throw new IllegalStateException("角色迁移需要REPEATABLE_READ以保护既有目录及缺失编码，未执行角色写入");
        } catch (SQLException ex) {
            throw new IllegalStateException("无法核实角色迁移事务隔离，未执行角色写入");
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private void validateRequest(Request request) {
        if (request == null || !Objects.equals(TenantContextHolder.getRequiredTenantId(), request.tenantId())
                || request.owner() == null || request.owner().isBlank() || request.entries() == null
                || request.entries().size() > 10000)
            throw new IllegalArgumentException("角色迁移租户、所有权或容量无效");
    }

    private void validateActors(Request request, Set<Long> plannedUserIds) {
        Set<Long> ids = new HashSet<>();
        for (Entry entry : request.entries()) {
            if (entry.referenceOnly() || entry.definition() == null) continue;
            for (String actor : Arrays.asList(entry.definition().creator(), entry.definition().updater())) {
                if (actor == null || actor.isEmpty()) continue;
                try {
                    long id = Long.parseLong(actor);
                    if (id < 0 && plannedUserIds.contains(id) && Long.toString(id).equals(actor)) continue;
                    if (id <= 0 || !Long.toString(id).equals(actor)) throw new NumberFormatException();
                    ids.add(id);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("历史角色操作者必须先经来源账号绑定关联正式用户");
                }
            }
        }
        if (ids.isEmpty()) return;
        var resolved = roles.selectListHistoricalAuditActorIds(new RoleMigrationAuditActorQuery(request.tenantId(), ids));
        if (resolved.size() != ids.size() || !ids.equals(Set.copyOf(resolved)))
            throw new IllegalArgumentException("历史角色操作者未迁入当前租户；停用历史操作者仍可用于审计关联");
    }

    static List<Result> plan(Request request, List<Role> current) {
        Map<String, Role> byCode = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Role role : current) {
            String code = role.definition().code();
            String name = identity(role.definition().name());
            if ((code != null && !code.isBlank() && byCode.containsKey(identity(code)))
                    || names.containsKey(name))
                throw new IllegalArgumentException("当前租户角色编码或名称已重复，必须先裁决目录冲突");
            if (code != null && !code.isBlank()) byCode.put(identity(code), role);
            names.put(name, code);
        }
        Map<String, Entry> primaries = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (Entry entry : request.entries()) {
            if (entry.definition() == null || entry.object() == null || entry.sourceKey() == null
                    || entry.sourceKey().isBlank() || !keys.add(entry.object() + ":" + entry.sourceKey()))
                throw new IllegalArgumentException("角色迁移来源身份无效或重复");
            if (!entry.referenceOnly() && primaries.put(identity(entry.definition().code()), entry) != null)
                throw new IllegalArgumentException("同一角色存在多个主定义，不能用来源顺序覆盖");
        }
        Map<String, Result> primaryResults = new HashMap<>();
        for (Entry entry : primaries.values()) {
            Result result = planPrimary(new PrimaryInput(entry, byCode, names, request.adoptExisting()));
            primaryResults.put(identity(entry.definition().code()), result);
            if (!"CONFLICT".equals(result.action())) {
                byCode.put(identity(result.after().definition().code()), result.after());
                names.put(identity(result.after().definition().name()), result.after().definition().code());
            }
        }
        List<Result> results = new ArrayList<>();
        for (Entry entry : request.entries()) {
            if (!entry.referenceOnly()) {
                results.add(primaryResults.get(identity(entry.definition().code())));
                continue;
            }
            Role match = byCode.get(identity(entry.definition().code()));
            Result primary = primaryResults.get(identity(entry.definition().code()));
            if (match == null || primary != null && "CONFLICT".equals(primary.action())
                    || match != null && !Objects.equals(match.definition().code(), entry.definition().code())
                    || entry.targetId() != null && !Objects.equals(entry.targetId(), match.id())) {
                results.add(conflict(entry, match, "明确复用的角色缺失、主定义冲突或绑定已变化"));
            } else if (entry.targetId() == null && !request.adoptExisting() && primary == null) {
                results.add(conflict(entry, match, "关联当前角色须先完成明确接管预览"));
            } else if (!Integer.valueOf(0).equals(match.status())) {
                results.add(conflict(entry, match, "不能借历史迁移激活当前停用角色"));
            } else {
                results.add(new Result(entry.object(), entry.sourceKey(), match, match,
                        entry.targetId() == null ? "ADOPTED" : "UNCHANGED", "保留当前正式角色定义和全部权限"));
            }
        }
        return List.copyOf(results);
    }

    private record PrimaryInput(Entry entry, Map<String, Role> byCode, Map<String, String> names, boolean adoptExisting) {}
    private static Result planPrimary(PrimaryInput input) {
        var entry=input.entry();var byCode=input.byCode();var names=input.names();boolean adoptExisting=input.adoptExisting();
        Definition definition = entry.definition();
        Role before = byCode.get(identity(definition.code()));
        if (!textFits(definition.code(), 100) || !textFits(definition.name(), 30) || definition.sort() == null
                || definition.remark() != null && definition.remark().length() > 500
                || definition.createTime() == null || definition.updateTime() == null)
            return conflict(entry, before, "角色正式字段容量或历史时间缺失，禁止截断或补当前时间");
        if (RoleCodeEnum.isSuperAdmin(definition.code()))
            return conflict(entry, before, "平台超级管理员只能明确关联已有角色，不能创建或改写");
        if (before != null && !Objects.equals(before.definition().code(), definition.code()))
            return conflict(entry, before, "角色编码仅大小写不同，不能新增重复或擅自改写正式编码");
        String duplicate = names.get(identity(definition.name()));
        if (duplicate != null && !duplicate.equals(definition.code()))
            return conflict(entry, before, "正式角色名称已被另一个编码使用");
        if (entry.targetId() != null && (before == null || !entry.targetId().equals(before.id())))
            return conflict(entry, before, "已建立的角色绑定不能改指另一个目标或重建已删除角色");
        if (before == null) {
            return new Result(entry.object(), entry.sourceKey(), null,
                    new Role(null, definition, 0, RoleTypeEnum.CUSTOM.getType(), DataScopeEnum.SELF.getScope(), Set.of()),
                    "CREATED", "新建正式业务角色；不授予菜单、按钮或办事处项目权限");
        }
        if (entry.targetId() == null) {
            if (!adoptExisting) return conflict(entry, before, "同编码现存角色尚未批准接管");
            if (!Objects.equals(before.definition(), definition))
                return conflict(entry, before, "独立来源角色与现存定义不同，必须逐项裁决，不能覆盖");
            return new Result(entry.object(), entry.sourceKey(), before, before, "ADOPTED", "同义完整定义精确关联");
        }
        if (!Objects.equals(before.definition(), entry.lastApplied()))
            return conflict(entry, before, "正式角色定义在上次承接后被修改，不能覆盖当前事实");
        if (Objects.equals(before.definition(), definition))
            return new Result(entry.object(), entry.sourceKey(), before, before, "UNCHANGED", "来源重放不改历史或权限");
        if (!Integer.valueOf(RoleTypeEnum.CUSTOM.getType()).equals(before.type()))
            return conflict(entry, before, "当前角色已是内置角色，不能以历史承接修改其定义");
        return new Result(entry.object(), entry.sourceKey(), before,
                new Role(before.id(), definition, before.status(), before.type(), before.dataScope(), before.dataScopeDeptIds()),
                "UPDATED", "仅同步来源拥有的定义和历史审计，当前权限保持不变");
    }

    private static boolean textFits(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum;
    }
    private static String identity(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
    private static Result conflict(Entry entry, Role before, String message) {
        return new Result(entry.object(), entry.sourceKey(), before, before, "CONFLICT", message);
    }
    private static Role snapshot(RoleRow role) {
        return new Role(role.getId(), new Definition(role.getCode(), role.getName(), role.getSort(), role.getRemark(),
                role.getCreator(), role.getCreateTime(), role.getUpdater(), role.getUpdateTime()), role.getStatus(),
                role.getType(), role.getDataScope(), role.getDataScopeDeptIds() == null ? Set.of() : role.getDataScopeDeptIds());
    }
    private static RoleRow entity(Long tenantId, Long id, Role role) {
        Definition value = role.definition();
        RoleRow entity = new RoleRow().setId(id).setCode(value.code()).setName(value.name()).setSort(value.sort())
                .setRemark(value.remark()).setStatus(role.status()).setType(role.type()).setDataScope(role.dataScope());
        entity.setTenantId(tenantId);
        entity.setCreator(value.creator()); entity.setCreateTime(value.createTime());
        entity.setUpdater(value.updater()); entity.setUpdateTime(value.updateTime());
        return entity;
    }
    private static void requireTransaction() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Direct account writes require the sync transaction");
    }
}
