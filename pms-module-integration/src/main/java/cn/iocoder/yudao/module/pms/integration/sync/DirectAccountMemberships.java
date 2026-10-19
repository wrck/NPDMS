package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.system.api.migration.SystemUserRoleMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemAccountMigrationPreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountMembershipMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.RoleMigrationTenantQuery;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.UserRoleMigrationQuery;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

@RequiredArgsConstructor
/** Approved rule copy from SystemUserRoleMigrationApiImpl; isolated direct persistence, no Owner invocation. */
final class DirectAccountMemberships implements SystemUserRoleMigrationApi {
    private final DirectAccountMembershipMapper relations;
    private final DataSource dataSource;
    private final CacheManager caches;
    @Override public List<Result> preview(Request request) {
        if(request==null)throw new IllegalArgumentException("角色关系迁移请求缺失");
        return previewAfterFoundation(new FoundationPreview(request,
                SystemAccountMigrationPreview.empty(request.tenantId(), request.owner())));
    }
    @Override public List<Result> previewAfterFoundation(FoundationPreview preview) {
        Request request = preview.request();
        preview.preceding().requireOwner(request.tenantId(), request.owner());
        validate(new Validation(request, false, preview.preceding()));
        return plan(request, relations.selectRelations(new RoleMigrationTenantQuery(request.tenantId())), false);
    }
    @Override     public List<Result> apply(Request request) {
        requireRepeatableRead();
        validate(new Validation(request, true, SystemAccountMigrationPreview.empty(request.tenantId(), request.owner())));
        // Same tenant catalogue lock protects absent user-role pairs, which have no unique index.
        List<Relation> current = relations.selectRelationsForUpdate(new RoleMigrationTenantQuery(request.tenantId()));
        List<Result> results = plan(request,current,true);
        Set<Long> saved = new HashSet<>();
        for (Result result : results) if ("CREATED".equals(result.action()) && saved.add(result.after().id()))
            if (relations.insertHistoricalRelation(new DirectAccountMembershipMapper.Write(request.tenantId(),result.after()))!=1) throw new IllegalStateException("正式用户角色关系写入失败");
        return results;
    }
    @Override public void refreshCaches() {
        var cache = caches.getCache(RedisKeyConstants.USER_ROLE_ID_LIST);
        if (cache!=null) cache.clear();
    }
    private record Validation(Request request, boolean lock, SystemAccountMigrationPreview preceding) {}
    private void validate(Validation validation) {
        Request request = validation.request(); boolean lock = validation.lock();
        if (request==null || !Objects.equals(TenantContextHolder.getRequiredTenantId(),request.tenantId())
                || request.owner()==null || request.owner().isBlank() || request.entries()==null || request.entries().size()>10000)
            throw new IllegalArgumentException("角色关系迁移租户、所有权或容量无效");
        Set<String> sources = new HashSet<>(); Set<Long> userIds = new HashSet<>(), roleIds = new HashSet<>();
        for (Entry entry : request.entries()) {
            if (entry==null || entry.object()==null || entry.object().isBlank() || entry.sourceKey()==null || entry.sourceKey().isBlank()
                    || !sources.add(entry.object()+":"+entry.sourceKey()) || entry.userId()==null || entry.roleId()==null || entry.expectedRoleCode()==null)
                throw new IllegalArgumentException("角色关系来源身份、正式用户或角色缺失/重复");
            userIds.add(entry.userId()); roleIds.add(entry.roleId());
        }
        userIds.removeAll(validation.preceding().plannedUserIds());
        roleIds.removeAll(validation.preceding().plannedRoleCodes().keySet());
        var query = new UserRoleMigrationQuery(request.tenantId(),userIds,roleIds);
        if (!Set.copyOf(lock?relations.selectUserIdsForUpdate(query):relations.selectUserIds(query)).equals(userIds))
            throw new IllegalArgumentException("关系引用账号不存在或跨租户");
        Map<Long,String> codes = new HashMap<>(); (lock?relations.selectRolesForUpdate(query):relations.selectRoles(query))
                .forEach(role -> codes.put(role.id(),role.code()));
        codes.putAll(validation.preceding().plannedRoleCodes());
        for (Entry entry : request.entries()) if (!entry.expectedRoleCode().equals(codes.get(entry.roleId())))
            throw new IllegalArgumentException("角色未迁入当前租户、停用或业务编码与批准映射不同");
    }
    static List<Result> plan(Request request,List<Relation> current,boolean allocate) {
        Map<String,Relation> existing = new HashMap<>();
        for (Relation relation : current) if (existing.put(relation.userId()+":"+relation.roleId(),relation)!=null)
            throw new IllegalArgumentException("正式用户角色关系重复，不能任选绑定");
        Map<String,Relation> planned = new HashMap<>(); List<Result> results = new ArrayList<>();
        for (Entry entry : request.entries()) {
            String pair = entry.userId()+":"+entry.roleId(); Relation before = existing.get(pair);
            if (entry.targetId()!=null && (before==null || !entry.targetId().equals(before.id())))
                throw new IllegalArgumentException("已承接角色关系删除或变化，不能重建或改绑");
            Relation after = before==null ? planned.computeIfAbsent(pair,ignored -> new Relation(allocate?IdWorker.getId():null,entry.userId(),entry.roleId())) : before;
            results.add(new Result(entry.object(),entry.sourceKey(),before,after,before==null?"CREATED":"UNCHANGED"));
        }
        return List.copyOf(results);
    }
    private void requireRepeatableRead() {
        requireTransaction();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (connection.getTransactionIsolation()!=Connection.TRANSACTION_REPEATABLE_READ)
                throw new IllegalStateException("角色关系迁移须使用REPEATABLE_READ，未执行授权写入");
        } catch (SQLException exception) { throw new IllegalStateException("无法核实角色关系事务隔离",exception); }
        finally { DataSourceUtils.releaseConnection(connection,dataSource); }
    }
    private static void requireTransaction() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Direct account writes require the sync transaction");
    }
}
