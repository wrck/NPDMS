# P22 前后置处理

当前C实现按[订单行延后与原部门编码报告](../base-sync-p22-order-line-preview-20261019.md)及[实际XML](../base-sync-p22-order-line-preview-20261019.sql)执行；A/B下文保留其历史流程。

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。正式订单行缺失：首条源记录id=1的精确订单号/行号/产品码在目标匹配0条，完整项目合同关联也为0条。343858条范围中14525条缺原部门编码，其余非空编码均可按租户匹配。需正式订单输入及缺失部门业务键；数值部门ID用-1不替代缺失编码。

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p22)；本页早期流程描述按该修订解释。

[返回本任务](README.md)

C当前前置：原项目身份保护、原部门编码/租户目录、原数量精度及授权约束；正式订单行先-1，后置按业务键及独立绑定处理。A/B历史前置见各方式段落。

后置：范围及数量明细同事务，必要父ID真实生成；不确认分配、消耗或提升当前范围版本。

上文保留历史实现，下方C实现已按最新决策刷新。实际预览与未执行写入的边界见本轮报告。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原ProjectProductScope Owner及完整已发布范围。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

DirectProductScopeMapper；343858条全为ISSUE。

配置控制：mode=`ONCE`，readStrategy=`STREAMING_CURSOR`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

原查询直接带入p.column001，部门/公司少量目录各读取一次；原项目数量优先、NULL才取原订单数量，零数量按DPPMS-SCOPE-039列待处理。原值按批写入，两个数值引用ID先-1，不预查正式订单或合同。

| 后处理SQL | 约束及范围 | 调用时点 |
|---|---|---|
| backfillDepartmentIds | 同租户/精确部门码及版本、department_id=-1、同连接BASE_P22当前运行的PRODUCT_SCOPE绑定 | 基础批次全部结束后一次，预览不调用 |
| backfillOrderLineIds | order_line_id=-1、当前P22绑定、同租户/连接的新P14已完成非预览SALES_LINE绑定、精确原业务键且唯一目标、未确认分配 | 部门后处理后一次；缺失/歧义保持-1登记，预览不调用 |

真实结构尚不能承载待定订单引用；仅阻止真实写入，预览继续读源。不可变原值/授权/正数量/已有正数ID保护保留；派生ID后填保留update_time和重放基线。当前完整SQL：[XML原文](../base-sync-p22-order-line-preview-20261019.sql)。

### work

[P22ProductScopeSyncTask.java:23](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P22ProductScopeSyncTask.java:23)

```java
@Override public void work(SyncContext<Parameters> c){
  if(c.preview()){c.runState().warning("SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED");c.runState().warning("P22_DEPARTMENT_ID_POSTFILL_PENDING:-1");c.runState().warning("P22_ORDER_LINE_ID_POSTFILL_PENDING:-1");}
  if(mapper.pendingOrderSchemaReady()!=1){if(!c.preview())fail("EXECUTION_SCHEMA_REQUIRED:order_line_pending_reference");c.runState().warning("P22_EXECUTION_SCHEMA_REQUIRED:order_line_pending_reference");}
  try(var source=c.source().open(c.connectionId())){
   for(var company:source.mapper(SourceMapper.class).selectCompanies(new SourceQuery<>(c.maxRows()+1,c.tenantId(),c.preview(),c.parameters())))
    if(c.parameters().companies.putIfAbsent(company.id,company.code)!=null||c.parameters().companies.size()>c.maxRows())fail("SOURCE_COMPANY_AMBIGUOUS_OR_CAPACITY");
  }
  for(var d:mapper.selectDepartments(new DepartmentCatalogueQuery(c.tenantId())))if(d.code==null||d.id==null||d.id<=0||c.parameters().departments.putIfAbsent(d.code,d)!=null)fail("DEPARTMENT_CODE_AMBIGUOUS");
  syncData(c.forData("ProductScope",false,true));
  if(c.parameters().zeroQuantityPending>0)c.runState().warning("P22_ZERO_QUANTITY_PENDING:"+c.parameters().zeroQuantityPending);
  if(!c.preview()){
   var q=new CurrentRunQuery(c.tenantId(),c.connectionId());
   mapper.backfillDepartmentIds(q);
   if(mapper.countPendingDepartments(q)!=0)fail("DEPARTMENT_POSTFILL_UNRESOLVED");
   mapper.backfillOrderLineIds(q);
   long pending=mapper.countPendingOrderLines(q);if(pending>0)c.runState().warning("P22_ORDER_LINE_POSTFILL_UNRESOLVED:"+pending);
  }
 }
```

### syncDataInsertBefore

[P22ProductScopeSyncTask.java:41](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P22ProductScopeSyncTask.java:41)

```java
@Override protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  // DPPMS-SCOPE-039: retain positive-quantity constraints; enumerate zero rows as pending, without target/binding writes.
  for(var iterator=b.rows().iterator();iterator.hasNext();){var s=iterator.next();
   if(s.sourceId==null||s.sourceId<=0||!"10".equals(s.projectType))fail("SOURCE_IDENTITY_INVALID");
   var quantity=s.projectQuantity==null?s.orderQuantity:s.projectQuantity;
   if(quantity!=null&&quantity.signum()==0){b.context().parameters().zeroQuantityPending++;iterator.remove();}
  }
  if(b.rows().isEmpty())return;
  var c=b.context();var keys=b.rows().stream().map(s->s.sourceId.toString()).toList();
  var bindings=c.identities().findByKeys(new SyncIdentityStore.Query("PRODUCT_SCOPE",keys));
  var references=projects.resolve(new BaseSyncProjectReferences.Query(c.tenantId(),c.actorId(),c.connectionId(),b.rows().stream().map(s->s.canonicalProjectId.toString()).distinct().toList(),c.preview()));
  var projectIds=references.values().stream().map(BaseSyncProjectReferences.Identity::id).distinct().toList();var snapshots=new HashMap<Long,Snapshot>();
  for(var p:mapper.selectSnapshots(new ProjectsQuery(c.tenantId(),projectIds,c.preview())))if(snapshots.putIfAbsent(p.projectId,p)!=null)fail("PROJECT_SNAPSHOT_AMBIGUOUS");
  for(var s:b.rows()){
   if(s.sourceId==null || s.sourceId<=0 || !"10".equals(s.projectType))fail("SOURCE_IDENTITY_INVALID");
   var p=references.get(s.canonicalProjectId.toString());if(p==null || !s.projectType.equals(p.projectType()))fail("PROJECT_IDENTITY_CHANGED");
   if(p.version()!=null&&!Objects.equals(p.projectCode(),s.originalProjectCode))fail("PROJECT_CODE_DIFFERS_FROM_SOURCE");
   for(var v:Arrays.asList(s.orderNumber,s.lineNum,s.itemCode))if(v==null || v.isBlank())fail("ORDER_IDENTITY_MISSING");
   s.formalProjectId=p.id();
  }
  var evidence=b.rows().stream().map(Source::evidence).toList();var existing=new HashMap<String,ScopeRow>();
  for(var row:mapper.selectScopes(new EvidenceQuery(c.tenantId(),evidence,c.preview())))if(existing.putIfAbsent(row.sourceEvidence,row)!=null)fail("SCOPE_IDENTITY_AMBIGUOUS");
  var scopeIds=existing.values().stream().map(ScopeRow::getId).toList();var details=new HashMap<Long,List<DetailRow>>();
  if(!scopeIds.isEmpty())for(var d:mapper.selectDetails(new ScopesQuery(c.tenantId(),scopeIds,c.preview())))details.computeIfAbsent(d.deliveryScopeId,k->new ArrayList<>()).add(d);
  // Only already-resolved references need a replay check. New raw rows never query formal order lines.
  var oldOrderKeys=existing.values().stream().filter(r->r.orderLineId!=null&&r.orderLineId>0).map(r->new OrderKey(r.projectCompanyCode,r.orderNo,r.lineNo,r.productCode)).distinct().toList();
  var ownedLines=new HashMap<OrderKey,OrderLine>();
  for(int i=0;i<oldOrderKeys.size();i+=60)for(var line:mapper.selectOwnedOrderLines(new OwnedLinesQuery(c.tenantId(),c.connectionId(),oldOrderKeys.subList(i,Math.min(i+60,oldOrderKeys.size())),c.preview())))
   if(ownedLines.putIfAbsent(new OrderKey(line.companyCode,line.orderNo,line.lineNo,line.productCode),line)!=null)fail("OWNED_ORDER_LINE_AMBIGUOUS");
  for(var s:b.rows()){
   var p=snapshots.get(s.formalProjectId);var identity=references.get(s.canonicalProjectId.toString());
   if(p!=null&&!Objects.equals(p.projectVersion,identity.version()))fail("PROJECT_VERSION_CHANGED");
   if(p==null)p=new Snapshot().setProjectId(s.formalProjectId).setProjectCode(s.originalProjectCode).setProjectName(s.originalProjectName).setScopeVersion(0L);
   var rawCompany=s.originalCompanyId==null?null:c.parameters().companies.get(s.originalCompanyId);
   if(rawCompany!=null&&!rawCompany.isBlank()){
    var company=DppmsCompanyCodeConverter.fromSource(rawCompany);
    if(p.companyCode!=null&&!p.companyCode.equals(company))fail("PROJECT_COMPANY_DIFFERS_FROM_SOURCE");p.companyCode=company;
   }
   String code=s.originalDepartmentCode;if(code!=null&&code.matches("31120[2-9]|311210"))code="1621"+code.substring(4);
   var department=c.parameters().departments.get(code);
   if(department==null||department.version==null||department.name==null||department.name.isBlank())fail("DEPARTMENT_CODE_UNRESOLVED:"+Objects.toString(code,"NULL"));
   // Match the source office by tenant and exact code. The numeric id is derived in one post-process.
   p.setDepartmentCode(department.code).setDepartmentName(department.name).setDepartmentVersion(department.version);
   var quantity=s.projectQuantity==null?s.orderQuantity:s.projectQuantity;if(quantity==null||quantity.signum()<=0)fail("POSITIVE_QUANTITY_REQUIRED:"+s.sourceId);
   var old=existing.get(s.evidence());var binding=bindings.get(s.sourceId.toString());s.previous=binding==null?null:binding.fingerprint();s.action=SyncWriteAction.UNCHANGED;
   if(old!=null){
    var ds=details.getOrDefault(old.id,List.of());if(binding==null || binding.targetId()!=old.id || Boolean.TRUE.equals(old.deleted) || ds.size()!=1 || !binding.fingerprint().equals(fingerprint(old,ds.getFirst())))fail("EXISTING_SCOPE_PROTECTED");
    if(!Objects.equals(old.departmentCode,department.code)||old.departmentId==null||old.departmentId!=-1L&&!Objects.equals(old.departmentId,department.id))fail("DEPARTMENT_IDENTITY_CHANGED");
    if(old.orderLineId==null||old.orderLineId!=-1L&&old.orderLineId<=0)fail("ORDER_LINE_IDENTITY_CHANGED");
    if(old.orderLineId>0){var line=ownedLines.get(new OrderKey(old.projectCompanyCode,old.orderNo,old.lineNo,old.productCode));
     if(line==null||!Objects.equals(old.orderLineId,line.id)||!Objects.equals(old.orderSourceSystem,line.sourceSystem)||!Objects.equals(old.orderCompanyCode,line.companyCode)||!Objects.equals(old.orderCompanyName,line.companyName)||!Objects.equals(old.orderType,line.orderType))fail("ORDER_LINE_IDENTITY_CHANGED");}
    if(!Objects.equals(old.projectCompanyCode,p.companyCode)||!Objects.equals(old.orderNo,s.orderNumber)||!Objects.equals(old.lineNo,s.lineNum))fail("ORDER_BUSINESS_KEY_CHANGED");
    if(!Objects.equals(old.projectId,s.formalProjectId) || !Objects.equals(old.productCode,s.itemCode) || !Objects.equals(old.productDesc,s.itemName) || old.allocatedQty.compareTo(quantity)!=0 || !same(old.deliveredQuantity,s.deliverQuantity) || !same(old.openQuantity,s.openQuantity))fail("IMMUTABLE_SOURCE_CHANGED");
    s.target=old;s.detail=ds.getFirst();continue;
   }
   if(binding!=null)fail("BOUND_SCOPE_MISSING");
   var row=build(new Plan(s,p,quantity,c));var detail=new DetailRow();detail.id=c.preview()?null:IdWorker.getId();detail.tenantId=c.tenantId();detail.deliveryScopeId=row.id;detail.detailSequence=1;
   detail.productCode=row.productCode;detail.productName=row.productDesc;detail.allocatedQty=row.allocatedQty;detail.sourceRecordKey=s.evidence()+":PRODUCT";detail.detailStatus="ACTIVE";detail.version=0;detail.deleted=false;
   detail.creator=row.creator;detail.updater=row.updater;detail.createTime=row.createTime;detail.updateTime=row.updateTime;s.target=row;s.detail=detail;s.action=SyncWriteAction.INSERT;
  }
 }
```

### syncDataInsert

[P22ProductScopeSyncTask.java:119](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P22ProductScopeSyncTask.java:119)

```java
@Override protected void syncDataInsert(SyncBatch<Source,Parameters> b){var fresh=b.matching(s->s.action==SyncWriteAction.INSERT);super.syncDataInsert(fresh);if(!fresh.rows().isEmpty()&&mapper.insertDetails(fresh)!=fresh.rows().size())fail("DETAIL_INSERT_CONFLICT");}
```

### syncDataInsertAfter

[P22ProductScopeSyncTask.java:120](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P22ProductScopeSyncTask.java:120)

```java
@Override protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){b.context().identities().saveAll(b.rows().stream().map(s->new SyncIdentityStore.Save("PRODUCT_SCOPE",s.sourceId.toString(),s.target.id,fingerprint(s.target,s.detail),s.previous)).toList());}
```
