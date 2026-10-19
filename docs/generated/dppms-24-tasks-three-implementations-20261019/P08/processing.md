# P08 前后置处理

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p08)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

前置：先核对原类型及使用但缺失的类型；继承明确的系统审计账号例外，不猜普通审计人员。

后置：类型→字典项，使用当次类型上下文；缓存按实现刷新；保留既有人工内容与来源基线。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原合并字典适配器保持原线身份。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

两份DirectDictionaryMapper独立持久化，缺类型由批准规则补齐。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

两表各读一次；当前缺w02611、x01861正式审计账号，不能自动填admin。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### createParameters

[P08DictionarySyncTask.java:16](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P08DictionarySyncTask.java:16)

```java
@Override protected Parameters createParameters(SyncContext<?> runtime){return new Parameters(new HashMap<>(),new ArrayList<>(),new ArrayList<>());}
```

### work

[P08DictionarySyncTask.java:20](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P08DictionarySyncTask.java:20)

```java
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
```

### syncDataInsertBefore

[P08DictionarySyncTask.java:39](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P08DictionarySyncTask.java:39)

```java
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
```

### syncDataInsertAfter

[P08DictionarySyncTask.java:106](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P08DictionarySyncTask.java:106)

```java
@Override protected void syncDataInsertAfter(SyncBatch<DictionarySource,Parameters> batch){
        var domain="DictionaryType".equals(batch.context().dataName())?"DICTIONARY_TYPE":"DICTIONARY_ITEM";
        batch.context().identities().saveAll(batch.rows().stream().filter(row->row.getSyncAction()==SyncWriteAction.INSERT)
                .map(row->new SyncIdentityStore.Save(domain,row.sourceKey(),row.getTarget().getId(),fingerprint(row,row.getTarget()),null)).toList());
    }
```

