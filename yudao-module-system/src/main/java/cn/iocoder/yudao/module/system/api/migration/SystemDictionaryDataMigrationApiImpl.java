package cn.iocoder.yudao.module.system.api.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.dal.mysql.dict.DictionaryDataMigrationMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

@Service @RequiredArgsConstructor
public class SystemDictionaryDataMigrationApiImpl implements SystemDictionaryDataMigrationApi {
    private final DictionaryDataMigrationMapper data;
    private final PermissionApi permissions;
    private final DataSource dataSource;
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Result> preview(Request request){return execute(request,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Result> apply(Request request){requireRepeatableRead();return execute(request,true);}
    private List<Result> execute(Request request,boolean apply){
        validate(request);
        for(String code:request.entries().stream().map(e->e.incoming().dictType()).distinct().sorted().toList()){
            var types=data.selectTypesForUpdate(new DictionaryDataMigrationMapper.TypeQuery(code));
            if(types.size()!=1 || !code.equals(types.getFirst().type()) || !Integer.valueOf(0).equals(types.getFirst().status()))
                fail("正式字典类型缺失、多义、编码不同或停用，禁止猜配");
        }
        var results=new ArrayList<Result>();
        for(var entry:request.entries()){
            var row=entry.incoming();
            var existing=data.selectConflictingValuesForUpdate(new DictionaryDataMigrationMapper.IdentityQuery(entry.targetId(),row.dictType(),row.value()));
            if(entry.targetId()!=null){
                if(existing.size()!=1 || !Objects.equals(existing.getFirst().id(),entry.targetId())
                        || Boolean.TRUE.equals(existing.getFirst().deleted()) || !existing.getFirst().equals(entry.lastApplied())
                        || !withoutId(existing.getFirst()).equals(row)) fail("正式字典项或完整历史审计已变，禁止覆盖");
                results.add(new Result(entry.sourceKey(),"UNCHANGED",existing.getFirst(),existing.getFirst()));
            }else{
                if(!existing.isEmpty()) fail("字典类型及业务键已存在，不接管现存字典项");
                var after=new Item(apply?IdWorker.getId():null,row.sort(),row.label(),row.value(),row.dictType(),row.status(),
                        row.colorType(),row.cssClass(),row.remark(),row.creator(),row.createTime(),row.updater(),row.updateTime(),false);
                results.add(new Result(entry.sourceKey(),"CREATED",null,after));
            }
        }
        if(apply) for(var result:results) if("CREATED".equals(result.action())
                && data.insertHistorical(new DictionaryDataMigrationMapper.HistoricalInsert(result.after()))!=1) fail("字典项写入失败");
        return List.copyOf(results);
    }
    private void validate(Request request){
        if(request==null || !Objects.equals(request.tenantId(),TenantContextHolder.getRequiredTenantId())
                || request.actorId()==null || request.actorId()<=0 || request.owner()==null
                || !request.owner().matches("integration:"+request.tenantId()+":[1-9][0-9]*")
                || request.entries()==null || request.entries().size()>2000) fail("迁移租户、操作者或所有权无效");
        if(!permissions.hasAnyPermissions(request.actorId(),"pms:integration:execute")
                || !permissions.hasAnyPermissions(request.actorId(),"system:dict:create")) fail("缺少迁移执行或字典创建权限");
        var keys=new HashSet<String>();var values=new HashSet<List<String>>();
        for(var entry:request.entries()){
            if(entry==null || entry.sourceKey()==null || !entry.sourceKey().matches("[1-9][0-9]*")
                    || "114".equals(entry.sourceKey()) || !keys.add(entry.sourceKey()) || entry.incoming()==null) fail("来源身份无效、已裁决排除或重复");
            var row=entry.incoming();
            if(row.id()!=null || !required(row.label(),100) || !required(row.value(),100) || !required(row.dictType(),100)
                    || row.sort()==null || row.status()==null || !Set.of(0,1).contains(row.status()) || !Boolean.FALSE.equals(row.deleted())
                    || row.colorType()!=null || row.cssClass()!=null || row.remark()!=null
                    || row.createTime()==null || row.updateTime()==null || row.createTime().getNano()!=0 || row.updateTime().getNano()!=0
                    || row.creator()!=null && !required(row.creator(),64) || row.updater()!=null && !required(row.updater(),64)
                    || !values.add(List.of(row.dictType().toLowerCase(Locale.ROOT),row.value().toLowerCase(Locale.ROOT)))
                    || entry.targetId()==null && entry.lastApplied()!=null
                    || entry.targetId()!=null && (entry.targetId()<=0 || entry.lastApplied()==null)) fail("正式14列、业务键、历史审计或绑定不符合已裁决规则");
        }
    }
    private static Item withoutId(Item row){return new Item(null,row.sort(),row.label(),row.value(),row.dictType(),row.status(),
            row.colorType(),row.cssClass(),row.remark(),row.creator(),row.createTime(),row.updater(),row.updateTime(),row.deleted());}
    private static boolean required(String value,int max){return value!=null && !value.isBlank() && value.length()<=max;}
    private void requireRepeatableRead(){
        var connection=DataSourceUtils.getConnection(dataSource);
        try{if(connection.getTransactionIsolation()!=Connection.TRANSACTION_REPEATABLE_READ) fail("字典迁入需要REPEATABLE_READ保护业务键唯一性");}
        catch(SQLException exception){throw new IllegalStateException("无法核实字典事务隔离",exception);}
        finally{DataSourceUtils.releaseConnection(connection,dataSource);}
    }
    private static void fail(String message){throw new IllegalArgumentException("DPPMS字典项："+message);}
}
