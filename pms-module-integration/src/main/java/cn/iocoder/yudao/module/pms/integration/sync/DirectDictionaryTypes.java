package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryTypeMigrationApi;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectDictionaryTypeMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

@RequiredArgsConstructor
final class DirectDictionaryTypes implements SystemDictionaryTypeMigrationApi {
    private final DirectDictionaryTypeMapper types;
    private final PermissionApi permissions;
    private final DataSource dataSource;
    @Override
    public List<Result> preview(Request request){return execute(request,false);}
    @Override
    public List<Result> apply(Request request){requireRepeatableRead();return execute(request,true);}
    private List<Result> execute(Request request,boolean apply){
        validate(request);
        var results=new ArrayList<Result>();
        for(var entry:request.entries()){
            var incoming=entry.incoming();
            var existing=types.selectConflictingDefinitionsForUpdate(new DirectDictionaryTypeMapper.IdentityQuery(
                    entry.targetId(),incoming.type(),incoming.name()));
            if(entry.targetId()!=null){
                if(existing.size()!=1 || !Objects.equals(existing.getFirst().id(),entry.targetId())
                        || Boolean.TRUE.equals(existing.getFirst().deleted()) || !existing.getFirst().equals(entry.lastApplied())
                        || !sameDefinition(existing.getFirst(),incoming)) fail("既有定义、历史审计或来源绑定已变，禁止覆盖");
                results.add(new Result(entry.sourceKey(),"UNCHANGED",existing.getFirst(),existing.getFirst()));
            }else{
                if(!existing.isEmpty()) fail("类型编码或名称已存在，须核实同义关系，不自行接管");
                var after=new Type(apply?IdWorker.getId():null,incoming.name(),incoming.type(),incoming.status(),incoming.remark(),
                        incoming.creator(),incoming.createTime(),incoming.updater(),incoming.updateTime(),false,null);
                results.add(new Result(entry.sourceKey(),"CREATED",null,after));
            }
        }
        if(apply) for(var result:results) if("CREATED".equals(result.action())
                && types.insertHistorical(new DirectDictionaryTypeMapper.HistoricalInsert(result.after()))!=1) fail("正式字典类型写入失败");
        return List.copyOf(results);
    }
    private void validate(Request request){
        if(request==null || !Objects.equals(request.tenantId(),TenantContextHolder.getRequiredTenantId())
                || request.actorId()==null || request.actorId()<=0 || request.owner()==null
                || !request.owner().matches("integration:"+request.tenantId()+":[1-9][0-9]*")
                || request.entries()==null || request.entries().size()>2000) fail("迁移租户、操作者或所有权无效");
        if(!permissions.hasAnyPermissions(request.actorId(),"pms:integration:execute")
                || !permissions.hasAnyPermissions(request.actorId(),"system:dict:create")) fail("缺少迁移执行或系统字典创建权限");
        var keys=new HashSet<String>();var codes=new HashSet<String>();var names=new HashSet<String>();
        for(var entry:request.entries()){
            if(entry==null || !required(entry.sourceKey(),128) || !keys.add(entry.sourceKey()) || entry.incoming()==null) fail("来源身份无效或重复");
            var row=entry.incoming();
            if(!required(row.name(),100) || !required(row.type(),100) || row.id()!=null || !Integer.valueOf(0).equals(row.status())
                    || row.remark()!=null || !Boolean.FALSE.equals(row.deleted()) || row.deletedTime()!=null
                    || row.createTime()==null || row.updateTime()==null || row.createTime().getNano()!=0 || row.updateTime().getNano()!=0
                    || row.creator()!=null && !required(row.creator(),64) || row.updater()!=null && !required(row.updater(),64)
                    || !codes.add(row.type().toLowerCase(Locale.ROOT)) || !names.add(row.name().toLowerCase(Locale.ROOT))
                    || entry.targetId()!=null && (entry.targetId()<=0 || entry.lastApplied()==null))
                fail("正式11列、唯一名称/编码或历史审计不符合已裁决规则");
        }
    }
    private static boolean sameDefinition(Type before,Type incoming){
        return new Type(null,before.name(),before.type(),before.status(),before.remark(),before.creator(),before.createTime(),
                before.updater(),before.updateTime(),before.deleted(),before.deletedTime()).equals(incoming);
    }
    private static boolean required(String value,int max){return value!=null && !value.isBlank() && value.length()<=max;}
    private void requireRepeatableRead(){
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("直写字典需要活动事务");
        var connection=DataSourceUtils.getConnection(dataSource);
        try{if(connection.getTransactionIsolation()!=Connection.TRANSACTION_REPEATABLE_READ) fail("字典迁入需要REPEATABLE_READ保护并发唯一性");}
        catch(SQLException exception){throw new IllegalStateException("无法核实字典事务隔离",exception);}
        finally{DataSourceUtils.releaseConnection(connection,dataSource);}
    }
    private static void fail(String message){throw new IllegalArgumentException("DPPMS字典类型："+message);}
}
