package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import org.quartz.CronExpression;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@Import(SpringJdbcStreamingReader.class)
public class SyncDefinitionValidator {
    private final Map<String,DataSyncAdapter> adapters;
    public SyncDefinitionValidator(List<DataSyncAdapter> providers) {
        Map<String,DataSyncAdapter> map=new LinkedHashMap<>();
        for(var p:providers) if(map.put(p.descriptor().key(),p)!=null) throw new IllegalStateException("适配器编码重复");
        adapters=Collections.unmodifiableMap(map);
    }
    public DataSyncAdapter adapter(String key) {
        var a=adapters.get(key);if(a==null) throw new IllegalArgumentException("业务适配器不存在");return a;
    }
    public List<DataSyncAdapter.Descriptor> descriptors(){return adapters.values().stream().map(DataSyncAdapter::descriptor).toList();}
    public void validate(SyncDefinition d) {
        if(d==null||d.connectionId()==null||d.sourceSystem()==null||!d.sourceSystem().matches("[A-Za-z0-9_-]{1,32}"))
            throw new IllegalArgumentException("连接或来源系统标识无效");
        var adapter=adapter(d.adapter());
        var descriptor=adapter.descriptor();
        String readStrategy=d.effectiveReadStrategy();
        if(!Set.of("SNAPSHOT","KEYSET_PAGING","STREAMING_CURSOR").contains(readStrategy))
            throw new IllegalArgumentException("来源读取策略无效");
        if("KEYSET_PAGING".equals(readStrategy) && (!Set.of("ONCE","SNAPSHOT").contains(d.mode()) || adapter.requiresAllBindings()
                || !"RETAIN".equals(d.missingPolicy()) || d.clearBeforeLoad() || d.resetMappingsBeforeLoad()))
            throw new IllegalArgumentException("主键游标分页仅支持独立记录的完整读取和保留策略，不支持整树、清空或重置映射");
        if("STREAMING_CURSOR".equals(readStrategy) && (!adapter.supportsStreaming()
                || !"RETAIN".equals(d.missingPolicy()) || d.clearBeforeLoad() || d.resetMappingsBeforeLoad()))
            throw new IllegalArgumentException("流式游标仅支持显式声明可分块重放的适配器和保留策略，不支持整树、清空或重置映射");
        if(!Set.of("RESTART_ALL","CHECKPOINT_KEY","NO_RESTART").contains(d.effectiveRestartPolicy()))
            throw new IllegalArgumentException("流式失败恢复策略无效");
        if("STREAMING_CURSOR".equals(readStrategy) && "NO_RESTART".equals(d.effectiveRestartPolicy()) && d.retryCount()>0)
            throw new IllegalArgumentException("流式 NO_RESTART 不允许配置自动重试");
        if(d.resetMappingsBeforeLoad()&&(d.clearBeforeLoad()||"INCREMENTAL".equals(d.mode())||d.retryCount()>0
                ||!"UPSERT".equals(d.loadingMode())))
            throw new IllegalArgumentException("仅重置映射不能同时截断目标；须使用完整快照、追加更新和手动执行，不能自动重试");
        if(d.clearBeforeLoad()&&(!descriptor.supportsTargetClear()||"INCREMENTAL".equals(d.mode())||d.retryCount()>0))
            throw new IllegalArgumentException("加载前清空仅限支持该操作的适配器、完整快照及手动执行，不能使用时间增量或自动重试");
        if(d.loadingMode()==null || !descriptor.loadingModes().contains(d.loadingMode()))
            throw new IllegalArgumentException("适配器不支持该加载策略");
        if(!Set.of("ONCE","SNAPSHOT","INCREMENTAL").contains(d.mode())||!descriptor.missingPolicies().contains(d.missingPolicy()))
            throw new IllegalArgumentException("同步方式或缺失策略无效");
        if(!CronExpression.isValidExpression(d.cron())||!CronExpression.isValidExpression(d.fullCron()))
            throw new IllegalArgumentException("Cron 表达式无效");
        if(d.overlapSeconds()<0||d.retryCount()<0||d.retryCount()>10||d.retryIntervalSeconds()<1
                ||d.maxRows()<1||d.maxRows()>10000||d.maxBytes()<1||d.maxBytes()>64L*1024*1024
                ||d.effectiveFetchSize()<1||d.effectiveFetchSize()>10000
                ||d.effectiveChunkSize()<1||d.effectiveChunkSize()>5000
                ||d.queryTimeoutSeconds()<0||d.queryTimeoutSeconds()>3600)
            throw new IllegalArgumentException("重试、窗口、流式参数或容量参数超出范围");
        if(d.sources()==null||d.sources().size()!=descriptor.objects().size()) throw new IllegalArgumentException("必须配置适配器全部对象");
        Set<String> objects=new HashSet<>(),sourceObjects=new HashSet<>();
        for(var s:d.sources()) {
            if(d.resetMappingsBeforeLoad()&&!s.syncPrimaryKey())
                throw new IllegalArgumentException("仅重置映射后追加更新，须对所有来源对象启用源主键同步");
            if(!objects.add(s.object())||s.sourceObject()==null||s.sourceObject().length()>64
                    ||s.sourceObject().isBlank()||!sourceObjects.add(s.sourceObject()))
                throw new IllegalArgumentException("来源对象身份重复或无效");
            var object=descriptor.objects().stream().filter(o->o.name().equals(s.object())).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("对象不属于适配器"));
            MysqlSyncReader.compile(s);
            if(s.syncPrimaryKey()&&!object.supportsSourcePrimaryKey())
                throw new IllegalArgumentException("该业务对象不支持同步源主键");
            if("INCREMENTAL".equals(d.mode())) MysqlSyncReader.identifier(s.updatedAt());
            if("STREAMING_CURSOR".equals(readStrategy)) MysqlSyncReader.identifier(s.sourceKey());
            if(s.mappings()==null) throw new IllegalArgumentException("缺少字段映射");
            Set<String> mapped=new HashSet<>();
            for(var m:s.mappings()) {
                if(!mapped.add(m.target())||object.fields().stream().noneMatch(f->f.name().equals(m.target())))
                    throw new IllegalArgumentException("未知或重复目标字段");
                if(!Set.of("DIRECT","STRING","TRIM","LONG","DECIMAL","BOOLEAN","DATETIME","ENUM","CONSTANT","REFERENCE").contains(m.conversion()))
                    throw new IllegalArgumentException("转换方式无效");
                if(!"CONSTANT".equals(m.conversion())) MysqlSyncReader.identifier(m.source());
            }
            for(var field:object.fields()) if(field.required()&&!mapped.contains(field.name()))
                throw new IllegalArgumentException("必填目标字段未映射: "+field.label());
        }
    }
}
