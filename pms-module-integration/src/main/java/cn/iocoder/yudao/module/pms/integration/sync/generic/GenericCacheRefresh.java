package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.GenericSyncJdbcStore;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncDisplayCacheMapper;
import cn.iocoder.yudao.module.pms.integration.sync.SyncDefinition;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.*;

/** The three approved display caches have snapshot-local rows, never cross-run business identities. */
public final class GenericCacheRefresh implements DataSyncAdapter {
    public static final String MODE="FULL_CACHE_REPLACE";
    public static final String ROW_KEY="cacheRowOrdinal";
    public static final Map<String,String> SOURCES=Map.of(
            "proj_project_lease_product","pm_project_product_lease_line_from_crm",
            "proj_project_product_configuration","pm_project_product_config_level_info_from_crm",
            "com_contract_collection_plan","pm_pb_plan_from_sms");
    private final SyncDefinition definition;
    private final GenericTargetCatalog catalog;
    private final GenericSyncJdbcStore store;
    private final cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncDisplayCacheMapper cacheMapper;

    GenericCacheRefresh(SyncDefinition definition,GenericTargetCatalog catalog,GenericSyncJdbcStore store,
            cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncDisplayCacheMapper cacheMapper) {
        this.definition=definition;this.catalog=catalog;this.store=store;this.cacheMapper=cacheMapper;validate(definition,catalog);
    }
    public static boolean requested(SyncDefinition definition){return MODE.equals(definition.loadingMode());}
    static void validate(SyncDefinition d,GenericTargetCatalog catalog) {
        if(!"TABLE_MAPPING".equals(d.adapter()) || !"DPPMS".equals(d.sourceSystem())
                || !Set.of("ONCE","SNAPSHOT").contains(d.mode()) || !"SNAPSHOT".equals(d.effectiveReadStrategy())
                || !d.clearBeforeLoad() || d.resetMappingsBeforeLoad() || !"RETAIN".equals(d.missingPolicy())
                || !"FULL".equals(d.effectiveEvidencePolicy()) || d.retryCount()!=0
                || d.taskKey()==null || !Set.of("project-display-cache","contract-plan-cache").contains(d.taskKey())
                || d.sources()==null || d.sources().isEmpty())
            throw new IllegalArgumentException("展示缓存仅支持完整快照、明确替换、手动执行和原始证据保留");
        var targets=new HashSet<String>();
        for(var source:d.sources()) {
            if(source.targets()==null || source.targets().size()!=1)fail();
            var step=source.targets().getFirst();var table=catalog.required(step.table());
            if(!SOURCES.containsKey(step.table()) || !targets.add(step.table()) || !table.writable()
                    || table.immutable() || !"tenant_id".equals(table.tenantColumn())
                    || !Objects.equals(SOURCES.get(step.table()),source.sourceObject())
                    || !Objects.equals(source.sourceObject(),source.table()) || !"TABLE".equals(source.readMode())
                    || source.sql()!=null || source.queryRef()!=null || source.groupKey()!=null || source.rowProjection()!=null
                    || source.evidenceAdapter()!=null || source.evidenceObject()!=null
                    || source.parameters()!=null && !source.parameters().isEmpty()
                    || source.filters()!=null && !source.filters().isEmpty()
                    || source.syncPrimaryKey() || source.sourceKeyColumns()!=null && !source.sourceKeyColumns().isEmpty()
                    || !ROW_KEY.equals(source.sourceKey()) || source.updatedAt()!=null || source.issueField()!=null
                    || source.columns()==null || source.columns().isEmpty() || source.columns().contains(ROW_KEY)
                    || step.keys()==null || !step.keys().isEmpty() || !"INSERT_ONLY".equals(step.mode())
                    || !"OVERWRITE".equals(step.nullPolicy()) || step.updateColumns()==null || !step.updateColumns().isEmpty()
                    || step.lookups()!=null && !step.lookups().isEmpty() || step.whenField()!=null
                    || step.newerBy()!=null || step.tieBreaker()!=null || step.sameVersionColumns()!=null
                    || step.mappings()==null || step.mappings().isEmpty())fail();
            var mapped=new HashSet<String>();
            for(var mapping:step.mappings()) {
                if(!mapped.add(mapping.target()) || !table.columns().contains(mapping.target())
                        || !source.columns().contains(mapping.source()) || mapping.constant()!=null || mapping.defaultValue()!=null
                        || !Set.of("STRING","LONG","DECIMAL","DATETIME","DATE","DIRECT").contains(mapping.conversion()))fail();
            }
            if(!mapped.equals(new HashSet<>(table.columns())))
                throw new IllegalArgumentException("展示缓存必须完整映射原业务字段，不能写入项目或合同ID");
        }
        var expected="project-display-cache".equals(d.taskKey())
                ? Set.of("proj_project_lease_product","proj_project_product_configuration")
                : Set.of("com_contract_collection_plan");
        if(!targets.equals(expected))fail();
    }
    private static void fail(){throw new IllegalArgumentException("缓存刷新须完整读取获准三表，禁止子集、关联覆盖或业务键去重");}
    @Override public Descriptor descriptor() {
        return new Descriptor("TABLE_MAPPING","展示缓存完整替换",definition.sources().stream().map(source->{
            var table=catalog.required(source.targets().getFirst().table());
            return new ObjectDescriptor(source.object(),source.object(),List.of(),table.context(),table.table(),table.table(),false);
        }).toList(),List.of("RETAIN"),List.of(MODE),true,false);
    }
    @Override public List<String> writeTables(){return definition.sources().stream().map(s->s.targets().getFirst().table()).sorted().toList();}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public void refreshCaches(){}
    @Override public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply) {
        if(!batch.full() || !batch.clearBeforeLoad() || !MODE.equals(batch.loadingMode())
                || batch.adoptExisting() || !batch.bindings().isEmpty())fail();
        if(apply && !TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("缓存替换必须处于单一事务");
        var sources=new HashMap<String,SyncDefinition.Source>();definition.sources().forEach(s->sources.put(s.object(),s));
        var schemas=new HashMap<String,Map<String,GenericSyncJdbcStore.Column>>();
        var records=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String name:writeTables()){schemas.put(name,store.columns(catalog.required(name)));records.put(name,new ArrayList<>());}
        var changes=new ArrayList<Change>();var keys=new HashSet<String>();long previewId=-1;
        for(var row:batch.rows()) {
            var source=sources.get(row.object());
            if(source==null || row.targetId()!=null || !keys.add(row.object()+":"+row.sourceKey()))fail();
            var step=source.targets().getFirst();var table=catalog.required(step.table());
            var values=new LinkedHashMap<String,Object>();
            for(var mapping:step.mappings()) {
                if(!row.fields().containsKey(mapping.source()))throw new IllegalArgumentException("来源缺少缓存列: "+mapping.source());
                values.put(mapping.target(),GenericSyncEngine.convert(row.fields().get(mapping.source()),mapping));
            }
            long id=apply?IdWorker.getId():previewId--;
            values.put("id",id);values.put("tenant_id",TenantContextHolder.getRequiredTenantId());values.put("deleted",false);
            if(!"com_contract_collection_plan".equals(table.table())) {
                values.put("creator","data_sync");values.put("updater","data_sync");
                var now=LocalDateTime.now();values.put("create_time",now);values.put("update_time",now);
            }
            store.validateValues(schemas.get(table.table()),values,true);
            records.get(table.table()).add(values);
            var after=new LinkedHashMap<>(values);
            after.put("_targets",List.of(Map.of("context",table.context(),"table",table.table(),"id",id)));
            changes.add(new Change(row.object(),row.sourceKey(),id,"CREATED",Map.of(),
                    Collections.unmodifiableMap(after),"完整快照展示缓存；技术ID不跨轮复用"));
        }
        // No clearing occurs before every row of every source has been converted and validated.
        if(apply) for(var entry:records.entrySet()) {
            var table=catalog.required(entry.getKey());
            var cache=switch(table.table()) {
                case "proj_project_lease_product" -> SyncDisplayCacheMapper.Cache.PROJECT_LEASE;
                case "proj_project_product_configuration" -> SyncDisplayCacheMapper.Cache.PROJECT_CONFIGURATION;
                case "com_contract_collection_plan" -> SyncDisplayCacheMapper.Cache.CONTRACT_PLAN;
                default -> throw new IllegalArgumentException("缓存目标未获准");
            };
            cacheMapper.clearCurrent(new SyncDisplayCacheMapper.Scope(TenantContextHolder.getRequiredTenantId(),cache));
            store.insertBatch(table,entry.getValue(),definition.effectiveChunkSize());
        }
        return List.copyOf(changes);
    }
}
