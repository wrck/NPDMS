package cn.iocoder.yudao.module.system.api.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DepartmentMigrationMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.query.DepartmentMigrationTenantQuery;
import cn.iocoder.yudao.module.system.dal.mysql.dept.query.DepartmentMigrationLeaderQuery;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SystemDepartmentMigrationApiImpl implements SystemDepartmentMigrationApi {
    private final DepartmentMigrationMapper departments;
    private final DataSource dataSource;
    private final CacheManager caches;
    @Override public List<Department> getCatalogue(){return departments.selectCatalogue(new DepartmentMigrationTenantQuery(TenantContextHolder.getRequiredTenantId()));}
    @Override public List<Result> preview(Request request){validate(request);return plan(request,getCatalogue(),false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> apply(Request request) {
        requireRepeatableRead();validate(request);
        var results=plan(request,departments.selectCatalogueForUpdate(new DepartmentMigrationTenantQuery(request.tenantId())),true);
        Set<Long> saved=new HashSet<>();
        for(var result:results)if(saved.add(result.after().id())) {
            if("CREATED".equals(result.action()) && departments.insertHistoricalDepartment(request.tenantId(),result.after())!=1)
                throw new IllegalStateException("正式部门写入失败");
            if("UPDATED".equals(result.action()) && departments.completeHistoricalLeader(request.tenantId(),result.before(),result.after())!=1)
                throw new IllegalStateException("正式部门主管关联发生并发变化，未覆盖");
        }
        return results;
    }
    @Override public void refreshCaches(){var cache=caches.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);if(cache!=null)cache.clear();}
    private void validate(Request request) {
        if(request==null || !Objects.equals(TenantContextHolder.getRequiredTenantId(),request.tenantId())
                || request.owner()==null || request.owner().isBlank() || request.entries()==null || request.entries().size()>10000)
            throw new IllegalArgumentException("部门迁移租户、所有权或容量无效");
        Set<String> keys=new HashSet<>();
        for(var entry:request.entries())if(entry==null || !required(entry.object(),128) || !required(entry.sourceKey(),512)
                || !keys.add(entry.object()+":"+entry.sourceKey()) || !required(entry.code(),64) || !required(entry.name(),30)
                || entry.parentCode()!=null && !required(entry.parentCode(),64) || entry.status()==null || !Set.of(0,1).contains(entry.status())
                || entry.createTime()==null || entry.updateTime()==null || entry.createTime().getNano()!=0 || entry.updateTime().getNano()!=0
                || entry.creator()!=null && !required(entry.creator(),64) || entry.updater()!=null && !required(entry.updater(),64)
                || entry.leaderUserId()!=null && entry.leaderUserId()<=0)
            throw new IllegalArgumentException("部门来源身份、正式字段容量、状态或已裁决审计日期无效，禁止截断或自行补时间");
        Set<Long> leaders=new HashSet<>();request.entries().forEach(entry->{if(entry.leaderUserId()!=null)leaders.add(entry.leaderUserId());});
        if(!leaders.isEmpty() && !new HashSet<>(departments.selectLeaderUserIds(new DepartmentMigrationLeaderQuery(request.tenantId(),leaders))).equals(leaders))
            throw new IllegalArgumentException("部门主管用户不存在、跨租户或已删除，不能按原人员ID直接关联");
    }
    static List<Result> plan(Request request,List<Department> current,boolean allocate) {
        Map<String,Department> byCode=new HashMap<>();
        for(var department:current)if(department.code()!=null && byCode.put(department.code(),department)!=null)
            throw new IllegalArgumentException("当前租户部门编码重复，不能任选关联");
        Map<String,Department> planned=new HashMap<>();List<Result> results=new ArrayList<>();
        for(var entry:request.entries()) {
            Department before=byCode.get(entry.code());
            if(entry.targetId()!=null && (before==null || !entry.targetId().equals(before.id())))
                throw new IllegalArgumentException("已承接部门删除或业务编码变化，不能重建或改绑");
            if(entry.referenceOnly() && before==null)throw new IllegalArgumentException("原引用部门不存在，不能自行重建");
            if(before!=null) {
                if(entry.targetId()!=null && !entry.referenceOnly() && (entry.lastApplied()==null || !before.equals(entry.lastApplied())))
                    throw new IllegalArgumentException("新增承接部门在上次迁入后被维护，须裁决，不覆盖当前结构和审计");
                if(entry.leaderUserId()!=null && !Objects.equals(entry.leaderUserId(),before.leaderUserId())) {
                    if(entry.targetId()==null || entry.referenceOnly() || before.leaderUserId()!=null)
                        throw new IllegalArgumentException("主管补关联只能补齐本任务已承接且仍为空的主管，不能接管或覆盖当前负责人");
                    var after=new Department(before.id(),before.code(),before.name(),before.parentId(),before.sort(),entry.leaderUserId(),
                            before.phone(),before.email(),before.status(),before.version()+1,before.creator(),before.createTime(),entry.updater(),entry.updateTime());
                    results.add(new Result(entry.object(),entry.sourceKey(),before,after,"UPDATED"));continue;
                }
                // The approved directory decision explicitly preserves all existing formal definitions.
                results.add(new Result(entry.object(),entry.sourceKey(),before,before,entry.targetId()==null||entry.referenceOnly()?"REFERENCED":"UNCHANGED"));
                continue;
            }
            Department parent=entry.parentCode()==null?null:byCode.get(entry.parentCode());
            if(entry.parentCode()!=null && (parent==null || parent.status()!=0))throw new IllegalArgumentException("批准的父部门不存在、跨租户或停用，不能按前缀伪造");
            Department after=new Department(allocate?IdWorker.getId():null,entry.code(),entry.name(),parent==null?0L:parent.id(),0,entry.leaderUserId(),null,null,
                    entry.status(),0,entry.creator(),entry.createTime(),entry.updater(),entry.updateTime());
            Department alias=planned.putIfAbsent(entry.code(),after);
            if(alias!=null) {
                if(!sameDefinition(alias,after))throw new IllegalArgumentException("来源同码部门的名称、层级、状态或历史时间冲突");
                after=alias;
            }
            results.add(new Result(entry.object(),entry.sourceKey(),null,after,"CREATED"));
        }
        return List.copyOf(results);
    }
    private static boolean sameDefinition(Department first,Department second){return Objects.equals(first.code(),second.code()) && Objects.equals(first.name(),second.name())
            && Objects.equals(first.parentId(),second.parentId()) && Objects.equals(first.status(),second.status())
            && Objects.equals(first.createTime(),second.createTime()) && Objects.equals(first.updateTime(),second.updateTime())
            && Objects.equals(first.creator(),second.creator()) && Objects.equals(first.updater(),second.updater()) && Objects.equals(first.leaderUserId(),second.leaderUserId());}
    private static boolean required(String value,int size){return value!=null && !value.isBlank() && value.length()<=size;}
    private void requireRepeatableRead() {
        Connection connection=DataSourceUtils.getConnection(dataSource);
        try{if(connection.getTransactionIsolation()!=Connection.TRANSACTION_REPEATABLE_READ)throw new IllegalStateException("部门迁移须使用REPEATABLE_READ，未写入目录");}
        catch(SQLException exception){throw new IllegalStateException("无法核实部门事务隔离",exception);}
        finally{DataSourceUtils.releaseConnection(connection,dataSource);}
    }
}
