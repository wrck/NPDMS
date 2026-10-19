package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

/** Approved global dictionary initialization, independently registered and bound. */
@Component @RequiredArgsConstructor
public class P08DictionarySyncTask extends AbstractSyncTask<P08DictionarySyncTask.DictionarySource,P08DictionarySyncTask.Parameters> {
    private final TargetMapper dictionaries;
    public record Parameters(Map<String,DictionaryTarget> plannedTypes,List<DictionarySource> types,List<DictionarySource> items){}
    @Override protected Parameters createParameters(SyncContext<?> runtime){return new Parameters(new HashMap<>(),new ArrayList<>(),new ArrayList<>());}
    @Override public String taskCode(){return "BASE_P08";}
    @Override public String taskName(){return "系统字典类型及字典项";}
    @Override public Set<String> requiredPermissions(){return Set.of("system:dict:create");}
    @Override public void work(SyncContext<Parameters> context){
        // Two original tables, each read once in the same read-only snapshot; missing groups are resolved in memory.
        try(var source=context.source().open(context.connectionId())){
            context.parameters().types().addAll(syncDataQueryBySql(source.sqlSession(),sourceQuery(context.forData("DictionaryType",false,false))));
            context.parameters().items().addAll(syncDataQueryBySql(source.sqlSession(),sourceQuery(context.forData("DictionaryItem",false,false))));
        }
        var known=new HashSet<String>();context.parameters().types().forEach(row->known.add(lower(row.getDataTypeCode())));
        var missing=new LinkedHashMap<String,DictionarySource>();
        for(var item:context.parameters().items())if(!known.contains(lower(item.getDataTypeCode())))
            missing.merge(lower(item.getDataTypeCode()),item,(left,right)->left.getRawId()<right.getRawId()?left:right);
        for(var first:missing.values())context.parameters().types().add(new DictionarySource().setRawId(first.getRawId())
                .setDataTypeCode(first.getDataTypeCode()).setDataTypeName(first.getDataTypeCode()).setDefinitionMissing(true).setDictionaryType(true));
        var types=syncData(context.forData("DictionaryType",false,false));
        for(var source:types)context.parameters().plannedTypes().put(source.getDataTypeCode(),source.getTarget());
        syncData(context.forData("DictionaryItem",false,false));
    }
    @Override protected List<DictionarySource> syncDataQuery(SyncContext<Parameters> context){
        return new ArrayList<>("DictionaryType".equals(context.dataName())?context.parameters().types():context.parameters().items());
    }
    @Override protected void syncDataInsertBefore(SyncBatch<DictionarySource,Parameters> batch){
        var context=batch.context();boolean types="DictionaryType".equals(context.dataName());
        var domain=types?"DICTIONARY_TYPE":"DICTIONARY_ITEM";
        var bindings=context.identities().findByKeys(new SyncIdentityStore.Query(domain,batch.rows().stream().map(DictionarySource::sourceKey).toList()));
        var codes=batch.rows().stream().map(DictionarySource::getDataTypeCode).distinct().toList();
        var names=types?batch.rows().stream().map(DictionarySource::getDataTypeName).toList():List.<String>of();
        var ids=bindings.values().stream().map(SyncIdentityStore.Identity::targetId).toList();
        var query=new TargetMapper.DefinitionsQuery(codes,names,ids,context.preview());
        var targets=types?dictionaries.selectTypes(query):dictionaries.selectItems(query);
        if(!types){
            for(var code:codes){
                var type=context.parameters().plannedTypes().get(code);
                if(type==null || type.getStatus()!=0 || Boolean.TRUE.equals(type.getDeleted()))fail("字典项对应本次已核实的类型缺失或停用");
            }
        }
        var keys=new HashSet<String>();var businessKeys=new HashSet<List<String>>();var labels=new HashSet<String>();
        for(var source:batch.rows()){
            source.setSyncAction(SyncWriteAction.UNCHANGED);
            if(source.getRawId()==null || source.getRawId()<=0 || !keys.add(source.sourceKey()))fail("字典原生来源身份缺失或重复");
            var binding=bindings.get(source.sourceKey());
            if(!types && source.getRawId()==114){
                if(!"23".equals(source.getDataTypeCode()) || original(source).subList(2,original(source).size()-2).stream().anyMatch(Objects::nonNull) || binding!=null)
                    fail("已裁决排除的空记录114发生变化");
                continue;
            }
            if(source.getDataTypeCode()==null || source.getDataTypeCode().isBlank())fail("原字典类型编码缺失");
            if(types && (source.getDataTypeName()==null || source.getDataTypeName().isBlank()))fail("原字典类型名称缺失");
            var matches=targets.stream().filter(row->types
                    ?source.getDataTypeCode().equalsIgnoreCase(row.getType()) || source.getDataTypeName().equalsIgnoreCase(row.getName())
                    :source.getDataTypeCode().equalsIgnoreCase(row.getDictType()) && Objects.equals(lower(source.getBasicDataId()),lower(row.getValue())))
                    .toList();
            DictionaryTarget old=binding==null?null:targets.stream().filter(row->Objects.equals(row.getId(),binding.targetId())).findFirst().orElse(null);
            if(binding==null && !matches.isEmpty() || binding!=null && (old==null || matches.size()!=1 || matches.getFirst()!=old
                    || Boolean.TRUE.equals(old.getDeleted()) || !Objects.equals(binding.fingerprint(),fingerprint(source,old))))fail("既有字典或原始来源已变化；禁止接管或覆盖");
            var row=new DictionaryTarget();row.setId(old==null?(context.preview()?null:IdWorker.getId()):old.getId());row.setDeleted(false);
            if(types){
                if(!businessKeys.add(List.of(lower(source.getDataTypeCode()))) || source.getDataTypeName()==null || source.getDataTypeName().isBlank()
                        || !labels.add(lower(source.getDataTypeName())))fail("字典类型编码或名称缺失、重复");
                row.setType(source.getDataTypeCode());row.setName(source.getDataTypeName());row.setStatus(0);
                if(source.isDefinitionMissing()){
                    row.setCreator(old==null?Long.toString(context.actorId()):old.getCreator());row.setUpdater(old==null?row.getCreator():old.getUpdater());
                    row.setCreateTime(old==null?context.startedAt().withNano(0):old.getCreateTime());row.setUpdateTime(old==null?row.getCreateTime():old.getUpdateTime());
                }else audit(source,row,null);
            }else{
                if(source.getBasicDataId()==null || source.getBasicDataId().isBlank() || source.getBasicDataName()==null || source.getBasicDataName().isBlank()
                        || !businessKeys.add(List.of(lower(source.getDataTypeCode()),lower(source.getBasicDataId()))))fail("原字典项业务键或标签缺失、重复");
                if(source.getEffectiveFrom()!=null && source.getEffectiveTo()!=null && source.getEffectiveTo().isBefore(source.getEffectiveFrom()))fail("原字典项有效期倒置");
                row.setDictType(source.getDataTypeCode());row.setValue(source.getBasicDataId());row.setLabel(source.getBasicDataName());
                row.setSort(source.getSortId()==null?0:source.getSortId());
                row.setStatus(old!=null?old.getStatus():source.getEffectiveFrom()!=null && context.startedAt().isBefore(source.getEffectiveFrom())
                        || source.getEffectiveTo()!=null && !context.startedAt().isBefore(source.getEffectiveTo())?1:0);
                audit(source,row,source.getCreateTime()==null?(old==null?context.startedAt().withNano(0):old.getCreateTime()):null);
            }
            for(var value:Arrays.asList(row.getType(),row.getName(),row.getDictType(),row.getValue(),row.getLabel()))checkTextLength(value,100);
            if(row.getCreateTime()==null || row.getUpdateTime()==null || row.getCreateTime().getNano()!=0 || row.getUpdateTime().getNano()!=0)fail("原字典审计时间缺失或精度不符");
            checkTextLength(row.getCreator(),64);checkTextLength(row.getUpdater(),64);
            if(old!=null && !targetValues(old).equals(targetValues(row)))fail("不可变字典定义或审计字段变化");
            source.setTarget(row);source.setSyncAction(old==null?SyncWriteAction.INSERT:SyncWriteAction.UNCHANGED);
        }
    }
    private static void audit(DictionarySource source,DictionaryTarget row,LocalDateTime fallback){
        row.setCreator(!source.isDictionaryType() && source.getRawId()==542 && "system".equals(source.getRawCreator())?"system":source.getResolvedCreator());
        row.setUpdater(source.getResolvedUpdater());row.setCreateTime(source.getCreateTime()==null?fallback:source.getCreateTime());
        row.setUpdateTime(source.getUpdateTime()==null?row.getCreateTime():source.getUpdateTime());
    }
    @Override protected SyncCounts syncDataCount(SyncBatch<DictionarySource,Parameters> batch){return SyncCounts.fromActions(batch.rows(),DictionarySource::getSyncAction);}
    @Override protected void syncDataInsert(SyncBatch<DictionarySource,Parameters> batch){super.syncDataInsert(batch.matching(row->row.getSyncAction()==SyncWriteAction.INSERT));}
    @Override protected void syncDataInsertAfter(SyncBatch<DictionarySource,Parameters> batch){
        var domain="DictionaryType".equals(batch.context().dataName())?"DICTIONARY_TYPE":"DICTIONARY_ITEM";
        batch.context().identities().saveAll(batch.rows().stream().filter(row->row.getSyncAction()==SyncWriteAction.INSERT)
                .map(row->new SyncIdentityStore.Save(domain,row.sourceKey(),row.getTarget().getId(),fingerprint(row,row.getTarget()),null)).toList());
    }
    static String lower(String value){return value==null?null:value.toLowerCase(Locale.ROOT);}
    static List<Object> original(DictionarySource row){return Arrays.asList(row.getRawId(),row.getDataTypeCode(),row.getDataTypeName(),row.getRawStatus(),row.getBasicDataId(),row.getBasicDataName(),row.getBasicDataAttri1(),row.getRawCreator(),row.getRawUpdater(),row.getSortId(),row.getCreateTime(),row.getUpdateTime(),row.getEffectiveFrom(),row.getEffectiveTo(),row.isDefinitionMissing(),row.isDictionaryType());}
    static List<Object> targetValues(DictionaryTarget row){return Arrays.asList(row.getId(),row.getName(),row.getType(),row.getLabel(),row.getValue(),row.getDictType(),row.getRemark(),row.getColorType(),row.getCssClass(),row.getCreator(),row.getUpdater(),row.getSort(),row.getStatus(),row.getCreateTime(),row.getUpdateTime(),row.getDeletedTime(),row.getDeleted());}
    static String fingerprint(DictionarySource source,DictionaryTarget target){return SyncFingerprint.of(Arrays.asList(original(source),targetValues(target)));}
    private static void fail(String message){throw new IllegalStateException("P08："+message);}
    @lombok.Data public static class DictionarySource implements SyncReferences.SourceAudit {
        private Long rawId;
        private String dataTypeCode;
        private String dataTypeName;
        private String rawStatus;
        private String basicDataId;
        private String basicDataName;
        private String basicDataAttri1;
        private String rawCreator;
        private String rawUpdater;
        private Integer sortId;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private LocalDateTime effectiveFrom;
        private LocalDateTime effectiveTo;
        private boolean definitionMissing;private boolean dictionaryType;private DictionaryTarget target;private SyncWriteAction syncAction;
        private String resolvedCreator;private String resolvedUpdater;
        public String sourceKey(){return (definitionMissing?"missing:":"")+rawId;}
        @Override public String getSourceCreator(){return !dictionaryType && rawId!=null && rawId==542 && "system".equals(rawCreator)?null:rawCreator;}
        @Override public String getSourceUpdater(){return rawUpdater;}
        @Override public boolean requiresResolvedAuditReferences(){return true;}
        @Override public boolean usesAuditCatalogue(){return true;}
        @Override public boolean requiresAuditReferences(){return !definitionMissing && !Objects.equals(rawId,114L);}
    }
    @lombok.Data public static class DictionaryTarget {
        private Long id;
        private String name;
        private String type;
        private String label;
        private String value;
        private String dictType;
        private String remark;
        private String colorType;
        private String cssClass;
        private String creator;
        private String updater;
        private Integer sort;
        private Integer status;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private LocalDateTime deletedTime;
        private Boolean deleted;
    }
    public interface SourceMapper{
        List<DictionarySource> selectDictionaryType(SourceQuery<Parameters> query);
        List<DictionarySource> selectDictionaryItem(SourceQuery<Parameters> query);
    }
    @Mapper public interface TargetMapper{
        record DefinitionsQuery(List<String> codes,List<String> names,List<Long> ids,boolean preview){}
        List<DictionaryTarget> selectTypes(@Param("query") DefinitionsQuery query);
        List<DictionaryTarget> selectItems(@Param("query") DefinitionsQuery query);
        int insertDictionaryType(SyncBatch<DictionarySource,Parameters> batch);
        int insertDictionaryItem(SyncBatch<DictionarySource,Parameters> batch);
    }
}
