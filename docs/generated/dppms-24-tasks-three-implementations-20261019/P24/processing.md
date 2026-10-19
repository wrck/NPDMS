# P24 前后置处理

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

前置：原39字段全部承接；7条空合同号沿已决排除；原时间/金额精度；审计引用和现存正式字段/前值保护。

后置：每批合同号一次集合匹配，空/多义保持NULL且不清旧引用；不猜公司、不改合同金额事实。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原Owner已批量合同查询；真实回填和当前候选全量未验收。

配置控制：mode=`ONCE`，readStrategy=`KEYSET_PAGING`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateReceipt | update | 否 | [ContractReceivableMigrationMapper.xml:26](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-commerce/src/main/resources/mapper/migration/ContractReceivableMigrationMapper.xml:26) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectReceivableMapper；已有79182条预览。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateReceipt | update | 否 | [DirectReceivableMapper.xml:18](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectReceivableMapper.xml:18) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

81547原行读取，7条排除；目录每运行一次，合同查询分段避免SQL解析栈溢出；执行结果才供新P15正式输入。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| updateReceivable | update | 否 | [P24TargetMapper.xml:18](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P24TargetMapper.xml:18) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### work

[P24ReceivableSyncTask.java:25](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P24ReceivableSyncTask.java:25)

```java
@Override public void work(SyncContext<Void> context) { syncData(context.forData("Receivable",false,true)); }
```

### syncDataInsertBefore

[P24ReceivableSyncTask.java:37](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P24ReceivableSyncTask.java:37)

```java
@Override protected void syncDataInsertBefore(SyncBatch<ReceivableSyncSource,Void> batch){
        var context=batch.context();
        var eligible=batch.rows().stream().filter(row->row.getContractNo()!=null).toList();
        var sourceIds=eligible.stream().map(row->Objects.toString(row.getSourceId())).toList();
        var bindings=context.identities().findByKeys(new SyncIdentityStore.Query("RECEIVABLE",sourceIds));
        var keys=eligible.stream().map(row->new TargetMapper.SourceKey(row.getSourceSystem(),Objects.toString(row.getSourceId()))).toList();
        var existing=new HashMap<TargetMapper.SourceKey,ReceivableSyncRow>();
        // Bound the OR-expression depth as well as the row count: production SQL interceptors serialize its AST.
        for(int offset=0;offset<keys.size();offset+=100){
            var selected=keys.subList(offset,Math.min(offset+100,keys.size()));
            for(var old:receipts.selectExisting(new TargetMapper.ExistingQuery(context.tenantId(),selected,context.preview()))){
                var key=new TargetMapper.SourceKey(old.getSourceSystem(),old.getSourceRecordKey());
                if(old.getTenantId()!=context.tenantId() || !selected.contains(key) || existing.putIfAbsent(key,old)!=null)
                    throw new IllegalStateException("回款正式来源身份不唯一或与原文不一致");
            }
        }
        for(var source:batch.rows()){
            source.setSyncAction(SyncWriteAction.UNCHANGED);
            if(source.getContractNo()==null)continue;
            if(source.getSourceId()==null || source.getSourceId()<=0) throw new IllegalArgumentException("回款原ID无效");
            var row=convert(source);
            row.setTenantId(context.tenantId());row.setDeleted(false);
            row.setCreator(source.getResolvedCreator());
            row.setUpdater(source.getResolvedUpdater());
            var key=new TargetMapper.SourceKey(row.getSourceSystem(),row.getSourceRecordKey());
            var old=existing.get(key);var binding=bindings.get(row.getSourceRecordKey());
            if(old!=null && (Boolean.TRUE.equals(old.getDeleted()) || binding==null || binding.targetId()!=old.getId()
                    || !Objects.equals(binding.fingerprint(),fingerprint(old))))
                throw new IllegalStateException("回款正式记录或来源基线已变化，禁止接管或覆盖");
            if(old==null && binding!=null) throw new IllegalStateException("回款来源绑定目标缺失");
            if(old==null){
                row.setId(context.preview()?null:IdWorker.getId());
                row.setContractId(null); // Optional association is deferred; preserve the original contract number.
                row.setMappingStatus("PENDING_COMPANY");
            }else{
                row.setId(old.getId());row.setContractId(old.getContractId());row.setCompanyId(old.getCompanyId());
                row.setCompanyCode(old.getCompanyCode());row.setCompanyName(old.getCompanyName());
                row.setCompanyResolutionSource(old.getCompanyResolutionSource());row.setMappingStatus(old.getMappingStatus());
                row.setDepartmentId(old.getDepartmentId());row.setSystemCode(old.getSystemCode());row.setSystemName(old.getSystemName());
                row.setExpendCode(old.getExpendCode());row.setExpendName(old.getExpendName());
            }
            validate(row);
            source.setTarget(row);source.setPreviousFingerprint(binding==null?null:binding.fingerprint());
            if(old!=null && values(old).equals(values(row)))continue;
            source.setSyncAction(old==null?SyncWriteAction.INSERT:SyncWriteAction.UPDATE);
        }
    }
```

### syncDataInsertAfter

[P24ReceivableSyncTask.java:91](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P24ReceivableSyncTask.java:91)

```java
@Override protected void syncDataInsertAfter(SyncBatch<ReceivableSyncSource,Void> batch) {
        batch.context().identities().saveAll(batch.rows().stream()
                .filter(row->row.getSyncAction()!=SyncWriteAction.UNCHANGED).map(this::identity).toList());
    }
```

