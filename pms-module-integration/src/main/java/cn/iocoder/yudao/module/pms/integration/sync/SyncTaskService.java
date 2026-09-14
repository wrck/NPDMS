package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import lombok.RequiredArgsConstructor;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SyncTaskService {
    private final SyncTaskMapper tasks;
    private final SyncBindingMapper bindings;
    private final SyncConnectionService connections;
    private final SyncDefinitionValidator validator;
    public record Save(Long id,Integer expectedVersion,String name,SyncDefinition definition) {}
    public record View(Long id,String name,SyncDefinition definition,Integer version,Integer validatedVersion,
                       Boolean enabled,Long activeRunId,LocalDateTime checkpoint,LocalDateTime nextRunAt,LocalDateTime nextFullAt) {}
    public record ConfigurationCheck(boolean allowed,Long existingTaskId,String existingTaskName,String message) {}

    public ConfigurationCheck checkConfiguration(Save command) {
        validator.validate(command.definition());
        var d=command.definition();
        var existing=tasks.selectIdentity(new SyncQueries.TaskIdentity(tenant(),d.sourceSystem(),d.adapter()));
        if(existing!=null&&!Objects.equals(existing.getId(),command.id()))
            return new ConfigurationCheck(false,existing.getId(),existing.getName(),
                    "同一来源系统和适配器已有任务，请打开已有任务修改；更改任务名称不能创建第二个相同来源任务");
        if(command.id()!=null) {
            var current=required(command.id());
            if(!Objects.equals(current.getVersion(),command.expectedVersion()))
                return new ConfigurationCheck(false,current.getId(),current.getName(),"配置版本冲突，请重新打开任务");
            var previous=definition(current);
            boolean needsHistory=validator.adapter(previous.adapter()).requiresAllBindings()
                    ||d.sources().stream().anyMatch(SyncDefinition.Source::syncPrimaryKey);
            List<SyncBindingDO> mapped=needsHistory?bindings.selectTask(new SyncQueries.Task(tenant(),current.getId())):List.of();
            boolean hasMapped=needsHistory?!mapped.isEmpty():bindings.hasTaskBindings(new SyncQueries.Task(tenant(),current.getId()));
            if(hasMapped&&(!previous.sourceSystem().equals(d.sourceSystem())||!previous.adapter().equals(d.adapter())
                    ||!previous.connectionId().equals(d.connectionId())||!identities(previous).equals(identities(d))))
                return new ConfigurationCheck(false,null,null,"已建立映射，不能替换来源身份。请沿用原连接、来源系统、来源对象及源主键列");
            for(var source:d.sources()) if(source.syncPrimaryKey()&&!d.clearBeforeLoad()&&!d.resetMappingsBeforeLoad()) {
                for(var binding:mapped) if(source.object().equals(binding.getObjectKey())) {
                    boolean same;
                    try {same=Objects.equals(binding.getTargetId(),SyncFieldMapper.primaryKey(binding.getSourceKey()));}
                    catch(IllegalArgumentException ex){same=false;}
                    if(!same)return new ConfigurationCheck(false,null,null,
                            "现有映射的源主键 "+binding.getSourceKey()+" 与目标主键 "+binding.getTargetId()
                                    +" 不一致。请在加载策略选择仅重置映射后按源主键追加更新（保留目标记录，编码冲突仍校验），或截断目标重建；普通同步不能重编号");
                }
            }
        }
        return new ConfigurationCheck(true,null,null,null);
    }

    @Transactional(rollbackFor=Exception.class)
    public Long save(Save command) {
        if(command.name()==null||command.name().isBlank()||command.name().length()>128)
            throw new IllegalArgumentException("任务名称无效");
        var current=command.id()==null?null:locked(command.id());
        if(current!=null) {
            if(!Objects.equals(command.expectedVersion(),current.getVersion())) throw new IllegalArgumentException("配置版本冲突，请重新加载");
            if(current.getActiveRunId()!=null)throw new IllegalArgumentException("运行中的任务不能修改配置");
        }
        var check=checkConfiguration(command);
        if(!check.allowed())throw new SyncTaskConflictException(check.message());
        connections.required(command.definition().connectionId());
        var d=command.definition();var t=current==null?new SyncTaskDO():current;
        t.setTenantId(tenant());t.setName(command.name());t.setDefinition(JsonUtils.toJsonString(d));
        t.setAdapter(d.adapter());t.setSourceSystem(d.sourceSystem());t.setConnectionId(d.connectionId());
        t.setVersion(current==null?0:current.getVersion()+1);t.setValidatedVersion(null);
        t.setEnabled(false);t.setRetryAttempt(0);t.setNextRunAt(next(d.cron()));t.setNextFullAt(next(d.fullCron()));
        try { if(current==null)tasks.insert(t);else tasks.updateById(t); }
        catch(org.springframework.dao.DuplicateKeyException ex) {
            throw new SyncTaskConflictException("相同来源系统和适配器的任务已存在，请刷新列表并打开已有任务");
        }
        return t.getId();
    }
    @Transactional(rollbackFor=Exception.class)
    public void schedule(Long id,int version,boolean enabled) {
        var t=locked(id);
        if(t.getVersion()!=version)throw new IllegalArgumentException("配置版本冲突");
        if(enabled && !Objects.equals(t.getVersion(),t.getValidatedVersion()))
            throw new IllegalArgumentException("启用前必须完成当前版本的完整预览确认");
        if(enabled&&(definition(t).clearBeforeLoad()||definition(t).resetMappingsBeforeLoad()))
            throw new IllegalArgumentException("加载前截断或重置映射必须手动确认执行，不能启用自动调度");
        t.setEnabled(enabled);tasks.updateById(t);
    }
    public PageResult<View> page(SyncQueries.Page q) {
        q.setTenantId(tenant());var p=tasks.selectPage(q);
        return new PageResult<>(p.getList().stream().map(this::view).toList(),p.getTotal());
    }
    public View get(Long id){return view(required(id));}
    public SyncTaskDO required(Long id) {
        var t=tasks.selectScoped(new SyncQueries.Id(tenant(),id));
        if(t==null)throw new IllegalArgumentException("同步任务不存在");return t;
    }
    public SyncTaskDO locked(Long id) {
        var t=tasks.selectForUpdate(new SyncQueries.Id(tenant(),id));
        if(t==null)throw new IllegalArgumentException("同步任务不存在");return t;
    }
    public SyncDefinition definition(SyncTaskDO t){return JsonUtils.parseObject(t.getDefinition(),SyncDefinition.class);}
    private View view(SyncTaskDO t){return new View(t.getId(),t.getName(),definition(t),t.getVersion(),t.getValidatedVersion(),
            t.getEnabled(),t.getActiveRunId(),t.getCheckpoint(),t.getNextRunAt(),t.getNextFullAt());}
    public static Long tenant(){return TenantContextHolder.getRequiredTenantId();}
    public static LocalDateTime next(String cron) {
        try {
            var expression=new CronExpression(cron);expression.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            var time=expression.getNextValidTimeAfter(new Date());
            if(time==null)throw new IllegalArgumentException("Cron 无下一次执行时间");
            return LocalDateTime.ofInstant(time.toInstant(),ZoneId.of("Asia/Shanghai"));
        }catch(java.text.ParseException ex){throw new IllegalArgumentException("Cron 无效");}
    }
    private static List<String> identities(SyncDefinition d) {
        return d.sources().stream().map(s->s.object()+":"+s.sourceObject()+":"+s.sourceKey()).sorted().toList();
    }
}
