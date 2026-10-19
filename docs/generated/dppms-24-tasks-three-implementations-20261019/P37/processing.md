# P37 前后置处理

[返回本任务](README.md)

前置：专用受控读取，内存AES-256-GCM加密；正式密钥上下文、租户、执行人和INT权限；普通快照/日志不接明文。

后置：正式license_code_ciphertext字段保存认证加密封装，nonce随载荷、keyId等元数据位于该封装内；不虚构独立nonce/keyId列。缺设备/合同保留NULL和原业务码；空许可码按原始空串加密并登记问题，不占位不跳过。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

A仍是旧受控项目Owner路径，当前候选里的旧批准ID/Owner上下文参数未配置且无全量回执。整表批准已经存在；这是A尚未接入已批准整表边界的实现/配置差异，不要求重新批准整表或补造项目Owner。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

已移除整表任务的旧项目Owner遗留依赖；保留原试点路径，475468条完整预览及1条空码问题可追溯。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

同一批准整表范围，专用Java加密后批写；475468条预览通过，设备/合同关联均待后续明确需要时处理。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### createParameters

[P37LicenseHistorySyncTask.java:41](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P37LicenseHistorySyncTask.java:41)

```java
protected Parameters createParameters(SyncContext<?> runtime){return new Parameters();}
```

### work

[P37LicenseHistorySyncTask.java:44](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P37LicenseHistorySyncTask.java:44)

```java
public void work(SyncContext<Parameters> context){
        if(key==null)throw new IllegalStateException("LICENSE_MASTER_KEY_UNAVAILABLE");
        if(approvedFacts==null || !Files.isRegularFile(approvedFacts))throw new IllegalStateException("P37已批准非秘密来源清单未配置");
        syncData(context.forData("LicenseHistory",false,true));
        if(context.parameters().devicePending>0)context.runState().warning("P37_DEVICE_ASSOCIATION_PENDING:"+context.parameters().devicePending);
        if(context.parameters().contractPending>0)context.runState().warning("P37_CONTRACT_ASSOCIATION_PENDING:"+context.parameters().contractPending);
    }
```

### syncDataInsertBefore

[P37LicenseHistorySyncTask.java:66](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P37LicenseHistorySyncTask.java:66)

```java
protected void syncDataInsertBefore(SyncBatch<LicenseSource,Parameters> batch){
        var context=batch.context();var keys=batch.rows().stream().map(row->row.getSourceId().toString()).toList();
        var bindings=context.identities().findByKeys(new SyncIdentityStore.Query("LICENSE_HISTORY",keys));
        var targets=bindings.isEmpty()?List.<LicenseTarget>of():licenses.selectExisting(new TargetMapper.ExistingQuery(context.tenantId(),bindings.values().stream().map(SyncIdentityStore.Identity::targetId).toList(),context.preview()));
        var byId=new HashMap<Long,LicenseTarget>();for(var row:targets)if(byId.putIfAbsent(row.getId(),row)!=null)throw new IllegalStateException("P37目标身份重复");
        for(var source:batch.rows()){
            for(var value:Arrays.asList(source.getSn(),source.getItem(),source.getStatus()))checkTextLength(value,255);
            checkTextLength(source.getSpecModel(),256);checkTextLength(source.getContract(),32);checkTextLength(source.getContractType(),16);
            var binding=bindings.get(source.getSourceId().toString());var old=binding==null?null:byId.get(binding.targetId());
            if(binding!=null){
                if(old==null || old.getTenantId()!=context.tenantId() || Boolean.TRUE.equals(old.getDeleted())
                        || !Objects.equals(binding.fingerprint(),fingerprint(source,old))
                        || !source.getEnvelope().codeMac().equals(verifyStored(old.getLicenseCodeCiphertext(),context.tenantId(),source.getSourceId().toString()))
                        || !sourceValues(source).equals(business(old)))throw new IllegalStateException("P37不可变许可历史或来源绑定已变化");
                source.setTarget(old);source.setSyncAction(SyncWriteAction.UNCHANGED);continue;
            }
            var row=new LicenseTarget();row.setId(context.preview()?null:IdWorker.getId());row.setTenantId(context.tenantId());
            row.setDeviceSn(source.getSn());row.setProductModel(source.getSpecModel());row.setContractNo(source.getContract());
            row.setContractType(source.getContractType());row.setProductCode(source.getItem());row.setLicenseStatus(source.getStatus());
            // Nullable business IDs are intentionally deferred. Original SN/contract fields carry the complete history.
            context.parameters().devicePending++;context.parameters().contractPending++;
            row.setLicenseCodeCiphertext(JsonUtils.toJsonString(new Sealed("AES-256-GCM",source.getEnvelope())));
            row.setCreator(Long.toString(context.actorId()));row.setUpdater(row.getCreator());row.setCreateTime(context.startedAt());row.setUpdateTime(context.startedAt());row.setDeleted(false);
            source.setTarget(row);source.setSyncAction(SyncWriteAction.INSERT);
        }
    }
```

### syncDataInsertAfter

[P37LicenseHistorySyncTask.java:94](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P37LicenseHistorySyncTask.java:94)

```java
protected void syncDataInsertAfter(SyncBatch<LicenseSource,Parameters> batch){
        batch.context().identities().saveAll(batch.rows().stream().filter(row->row.getSyncAction()==SyncWriteAction.INSERT)
                .map(row->new SyncIdentityStore.Save("LICENSE_HISTORY",row.getSourceId().toString(),row.getTarget().getId(),fingerprint(row,row.getTarget()),null)).toList());
    }
```

