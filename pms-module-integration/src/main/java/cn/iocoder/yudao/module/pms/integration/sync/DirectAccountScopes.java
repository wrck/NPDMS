package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.system.api.migration.SystemUserScopeMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemAccountMigrationPreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountRows.ScopeRow;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper.DirectoryReference;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.UserScopeMigrationReferenceQuery;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.UserScopeMigrationTenantQuery;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.DataSourceUtils;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

@RequiredArgsConstructor
/** Approved rule copy from SystemUserScopeMigrationApiImpl; isolated direct persistence, no Owner invocation. */
final class DirectAccountScopes implements SystemUserScopeMigrationApi {
    private final DirectAccountScopeMapper relations;
    private final DataSource dataSource;

    @Override public List<Result> preview(Request request) {
        if(request==null)throw new IllegalArgumentException("范围迁移请求缺失");
        return previewAfterFoundation(new FoundationPreview(request,
                SystemAccountMigrationPreview.empty(request.tenantId(), request.owner())));
    }
    @Override public List<Result> previewAfterFoundation(FoundationPreview preview) {
        Request request = preview.request();
        preview.preceding().requireOwner(request.tenantId(), request.owner());
        validate(new Validation(request, false, preview.preceding()));
        return plan(request,relations.selectRelations(new UserScopeMigrationTenantQuery(request.tenantId())),false);
    }
    @Override     public List<Result> apply(Request request) {
        requireRepeatableRead();
        validate(new Validation(request, true, SystemAccountMigrationPreview.empty(request.tenantId(), request.owner())));
        List<Result> results=plan(request,relations.selectRelationsForUpdate(new UserScopeMigrationTenantQuery(request.tenantId())),true);
        Set<Long> saved=new HashSet<>();
        for (Result result:results) if (saved.add(result.after().id())) {
            if ("CREATED".equals(result.action()) && relations.insertHistoricalRelation(new DirectAccountScopeMapper.Write(entity(request.tenantId(),result.after()),result.after().audit(),null))!=1)
                throw new IllegalStateException("正式公司部门范围插入失败");
            if ("UPDATED".equals(result.action()) && relations.updateHistoricalRelation(new DirectAccountScopeMapper.Write(entity(request.tenantId(),result.after()),result.after().audit(),result.before().version()))!=1)
                throw new IllegalStateException("正式范围版本变化，未覆盖当前授权事实");
        }
        return results;
    }
    private record Validation(Request request, boolean lock, SystemAccountMigrationPreview preceding) {}
    private void validate(Validation validation) {
        Request request = validation.request(); boolean lock = validation.lock();
        if (request==null || !Objects.equals(TenantContextHolder.getRequiredTenantId(),request.tenantId())
                || request.owner()==null || request.owner().isBlank() || request.entries()==null || request.entries().size()>10000)
            throw new IllegalArgumentException("范围迁移租户、所有权或容量无效");
        Set<String> sources=new HashSet<>(), roleCodes=new HashSet<>();
        Set<Long> userIds=new HashSet<>(),companyIds=new HashSet<>(),departmentIds=new HashSet<>();
        for (Entry entry:request.entries()) {
            if (entry==null || !required(entry.object(),128) || !required(entry.sourceKey(),512)
                    || !sources.add(entry.object()+":"+entry.sourceKey()) || entry.scope()==null || entry.audit()==null)
                throw new IllegalArgumentException("范围来源身份、业务属性或历史审计缺失/重复");
            Scope scope=entry.scope(); Audit audit=entry.audit();
            if (scope.userId()==null || scope.companyId()==null || !required(scope.companyCode(),64) || !required(scope.companyName(),128)
                    || (scope.departmentId()==null)!=(scope.departmentCode()==null)
                    || scope.departmentId()!=null && (!required(scope.departmentCode(),64) || !required(scope.departmentName(),128))
                    || scope.departmentId()==null && scope.departmentName()!=null
                    || !required(scope.scopeRole(),32) || scope.primary()==null || scope.effectiveFrom()==null
                    || scope.effectiveTo()!=null && scope.effectiveTo().isBefore(scope.effectiveFrom())
                    || scope.status()==null || !Set.of(0,1).contains(scope.status())
                    || audit.createTime()==null || audit.updateTime()==null)
                throw new IllegalArgumentException("范围正式字段容量、部门配对、枚举或历史日期无效，不能猜配或补当前时间");
            userIds.add(scope.userId());companyIds.add(scope.companyId());roleCodes.add(scope.scopeRole());
            if (scope.departmentId()!=null)departmentIds.add(scope.departmentId());
            for (String actor:Arrays.asList(audit.creator(),audit.updater())) if (actor!=null && !actor.isEmpty()) {
                if (actor.matches("-[1-9][0-9]*") && actor.length() <= 20
                        && validation.preceding().plannedUserIds().contains(Long.valueOf(actor))) continue;
                if (!actor.matches("[1-9][0-9]*") || actor.length()>19)throw new IllegalArgumentException("范围历史操作者必须先关联正式账号");
                userIds.add(Long.valueOf(actor));
            }
        }
        userIds.removeAll(validation.preceding().plannedUserIds());
        var query=new UserScopeMigrationReferenceQuery(request.tenantId(),userIds,companyIds,departmentIds,roleCodes);
        if (!Set.copyOf(lock?relations.selectUserIdsForUpdate(query):relations.selectUserIds(query)).equals(userIds))
            throw new IllegalArgumentException("范围账号或审计人员不存在、已删除或跨租户");
        Map<Long,DirectoryReference> companies=index(lock?relations.selectCompaniesForUpdate(query):relations.selectCompanies(query));
        Map<Long,DirectoryReference> departments=index(lock?relations.selectDepartmentsForUpdate(query):relations.selectDepartments(query));
        Set<String> roles=new HashSet<>();
        (lock?relations.selectRolesForUpdate(query):relations.selectRoles(query)).forEach(role->roles.add(role.userId()+":"+role.code()));
        validation.preceding().plannedMemberships().forEach(role -> roles.add(role.userId()+":"+role.roleCode()));
        for (Entry entry:request.entries()) {
            Scope scope=entry.scope();DirectoryReference company=companies.get(scope.companyId()),department=departments.get(scope.departmentId());
            if (company==null || !company.code().equals(scope.companyCode()) || !company.name().equals(scope.companyName()))
                throw new IllegalArgumentException("范围公司ID、转换后的编码或正式名称不同，不依据部门反推公司");
            if (scope.departmentId()!=null && (department==null || !Objects.equals(department.code(),scope.departmentCode()) || !Objects.equals(department.name(),scope.departmentName())))
                throw new IllegalArgumentException("范围部门ID、业务编码或正式名称不同，未匹配不能只存旧码");
            if (!roles.contains(scope.userId()+":"+scope.scopeRole()))
                throw new IllegalArgumentException("范围角色尚未关联正式账号、已停用或编码不同，不能据范围自动授予角色");
        }
    }
    private static Map<Long,DirectoryReference> index(List<DirectoryReference> references) {
        Map<Long,DirectoryReference> result=new HashMap<>();references.forEach(reference->result.put(reference.id(),reference));return result;
    }
    static List<Result> plan(Request request,List<ScopeRow> current,boolean allocate) {
        Map<Long,Relation> byId=new HashMap<>();Map<History,List<Relation>> byHistory=new HashMap<>();
        for (var value:current) {
            Relation relation=snapshot(value);byId.put(relation.id(),relation);
            byHistory.computeIfAbsent(new History(relation.scope(),relation.audit()),ignored->new ArrayList<>()).add(relation);
        }
        Map<History,Relation> planned=new HashMap<>();Map<Long,Relation> written=new HashMap<>();List<Result> results=new ArrayList<>();
        for (Entry entry:request.entries()) {
            History key=new History(entry.scope(),entry.audit());Relation before;
            if(entry.targetId()!=null)before=byId.get(entry.targetId());
            else {
                List<Relation> matches=byHistory.getOrDefault(key,List.of());
                if(matches.size()>1)throw new IllegalArgumentException("完全同值的正式历史范围不唯一，不能任选关联");
                before=matches.isEmpty()?null:matches.getFirst();
            }
            if (entry.targetId()!=null && (before==null || !dimension(entry.scope()).equals(dimension(before.scope()))))
                throw new IllegalArgumentException("已承接范围删除或关联维度变化，不能重建或改绑");
            if (entry.targetId()!=null && (entry.lastApplied()==null || !before.equals(entry.lastApplied())))
                throw new IllegalArgumentException("正式范围自上次承接后被维护，不能覆盖当前业务或审计事实");
            if (entry.targetId()==null && before!=null && (!before.scope().equals(entry.scope()) || !before.audit().equals(entry.audit())))
                throw new IllegalArgumentException("已有同维度范围属性不同，需明确裁决，不擅自接管");
            boolean unchanged=before!=null && before.scope().equals(entry.scope()) && before.audit().equals(entry.audit());
            Relation after=unchanged?before:new Relation(before==null?(allocate?IdWorker.getId():null):before.id(),entry.scope(),entry.audit(),before==null?0:Math.incrementExact(before.version()));
            Relation alias=planned.putIfAbsent(key,after);
            if (alias!=null) {
                if (before!=null && !Objects.equals(alias.id(),before.id()))
                    throw new IllegalArgumentException("同值来源历史绑定指向不同正式关系，须裁决，不能改绑");
                after=alias;
            }
            if(after.id()!=null) {
                Relation already=written.putIfAbsent(after.id(),after);
                if(already!=null && !already.equals(after))throw new IllegalArgumentException("同一正式范围的多来源更新不同，须明确主来源");
            }
            results.add(new Result(entry.object(),entry.sourceKey(),before,after,before==null?"CREATED":unchanged?"UNCHANGED":"UPDATED"));
        }
        return List.copyOf(results);
    }
    private record Dimension(Long userId,Long companyId,Long departmentId,String role,java.time.LocalDateTime from) {}
    private record History(Scope scope,Audit audit) {}
    private static Dimension dimension(Scope value) {return new Dimension(value.userId(),value.companyId(),value.departmentId(),value.scopeRole(),value.effectiveFrom());}
    private static boolean required(String value,int size){return value!=null && !value.isBlank() && value.length()<=size;}
    private static Relation snapshot(ScopeRow value) {
        return new Relation(value.getId(),new Scope(value.getUserId(),value.getCompanyId(),value.getCompanyCode(),value.getCompanyName(),
                value.getDepartmentId(),value.getDepartmentCode(),value.getDepartmentName(),value.getScopeRole(),value.getIsPrimary(),
                value.getEffectiveFrom(),value.getEffectiveTo(),value.getStatus()),new Audit(value.getCreator(),value.getCreateTime(),value.getUpdater(),value.getUpdateTime()),value.getVersion());
    }
    private static ScopeRow entity(Long tenant,Relation value) {
        Scope scope=value.scope();Audit audit=value.audit();var result=new ScopeRow();
        result.setId(value.id());result.setTenantId(tenant);result.setUserId(scope.userId());result.setCompanyId(scope.companyId());
        result.setCompanyCode(scope.companyCode());result.setCompanyName(scope.companyName());result.setDepartmentId(scope.departmentId());
        result.setDepartmentCode(scope.departmentCode());result.setDepartmentName(scope.departmentName());result.setScopeRole(scope.scopeRole());
        result.setIsPrimary(scope.primary());result.setEffectiveFrom(scope.effectiveFrom());result.setEffectiveTo(scope.effectiveTo());
        result.setStatus(scope.status());result.setVersion(value.version());result.setCreator(audit.creator());result.setCreateTime(audit.createTime());
        result.setUpdater(audit.updater());result.setUpdateTime(audit.updateTime());result.setDeleted(false);return result;
    }
    private void requireRepeatableRead() {
        requireTransaction();
        Connection connection=DataSourceUtils.getConnection(dataSource);
        try {if(connection.getTransactionIsolation()!=Connection.TRANSACTION_REPEATABLE_READ)
            throw new IllegalStateException("范围迁移须使用REPEATABLE_READ，未执行授权写入");
        } catch(SQLException exception){throw new IllegalStateException("无法核实范围事务隔离",exception);}
        finally {DataSourceUtils.releaseConnection(connection,dataSource);}
    }
    private static void requireTransaction() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Direct account writes require the sync transaction");
    }
}
