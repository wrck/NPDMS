# P01 前后置处理

[返回本任务](README.md)

前置：限定基础EHR部门、FND部门及已审核补全部门；完整校验公司、父部门、同码及人工维护边界。

后置：写入公司/部门后维护来源身份；主管须正式账号，未就绪保持待关联；按实现刷新组织缓存。

这些是现有实现中的流程。2026-10-19只生成报告，没有调用任何前后置处理。公共权限、事务、预览抑制和失败回滚见[公共流程](../shared-framework.md)。

## 原通用引擎 / Owner适配器

原组织归并保持原线来源与163130裁决；主管需本线P04；不得借DIRECT绑定。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| completeHistoricalLeader | update | 否 | [DepartmentMigrationMapper.xml:16](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/resources/mapper/dept/DepartmentMigrationMapper.xml:16) |
| deleteCompanies | delete | 否 | [ManagedOrganizationMapper.xml:33](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/resources/mapper/organization/ManagedOrganizationMapper.xml:33) |
| deleteDepartments | delete | 否 | [ManagedOrganizationMapper.xml:34](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/yudao-module-system/src/main/resources/mapper/organization/ManagedOrganizationMapper.xml:34) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 独立直写任务

专用DirectOrganizationMapper；29码及父级范围沿直写裁决；历史P01确有非预览SUCCESS，不属于本次执行。

配置控制：mode=`ONCE`，readStrategy=`SNAPSHOT`，loadingMode=`UPSERT`，missingPolicy=`RETAIN`，clearBeforeLoad=`False`。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| completeLeader | update | 否 | [DirectOrganizationMapper.xml:35](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/sync/DirectOrganizationMapper.xml:35) |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

## 新虚基类套件

四份原表各读一次；已有正式定义仅引用、不接管；8条负责人登记P01_DIRECTOR_ASSOCIATION_PENDING。

| 实际声明/动态模板 | 分类 | 多表JOIN | 原件 |
|---|---|---|---|
| 未定位到独立关联更新SQL | Java/Owner/API或不回填，见实际代码 | — | 见任务README方法索引 |

Mapper声明存在不等于此运行调用或正式执行；SQL原件、动态分支和目标绑定见本方式.sql与-mappings.json。

### work

[P01OrganizationSyncTask.java:25](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java:25)

```java
public void work(SyncContext<Parameters> c){c.parameters().warnings.forEach(c.runState()::warning);syncData(c.forData("Organization",false,false));}
```

### createParameters

[P01OrganizationSyncTask.java:26](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java:26)

```java
protected Parameters createParameters(SyncContext<?> c){
  var query=new SourceQuery<Void>(Math.min(c.maxRows(),10000)+1,c.tenantId(),c.preview(),null);
  List<Company> companies;List<Department> departments;List<Fnd> fnd;List<CompanyAlias> aliases;
  try(var source=c.source().open(c.connectionId())){
   var m=source.mapper(SourceMapper.class);companies=m.selectCompanies(query);departments=m.selectDepartments(query);fnd=m.selectFnd(query);aliases=m.selectCompanyAliases(query);
  }
  if(companies.size()+departments.size()+fnd.size()+aliases.size()>10000)fail("RAW_DIRECTORY_CAPACITY");
  var current=new HashMap<String,Row>();var targetQuery=new CatalogueQuery(c.tenantId(),c.preview());
  var formal=new ArrayList<>(mapper.selectCompanies(targetQuery));formal.addAll(mapper.selectDepartments(targetQuery));
  for(var row:formal)if(row.code!=null && current.putIfAbsent(row.kind+":"+row.code,row)!=null)fail("FORMAL_CODE_AMBIGUOUS");
  var planned=plan(new Input(companies,departments,fnd,aliases,c));
  return new Parameters(planned,current,c.runState().warnings());
 }
```

### syncDataInsertBefore

[P01OrganizationSyncTask.java:80](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java:80)

```java
protected void syncDataInsertBefore(SyncBatch<Source,Parameters> b){
  var c=b.context();var byCode=new LinkedHashMap<String,Source>();for(var s:b.rows())byCode.put(s.kind+":"+s.code,s);
  var bindings=new HashMap<String,SyncIdentityStore.Identity>();
  for(var kind:List.of("COMPANY","DEPARTMENT")){var selected=b.rows().stream().filter(s->kind.equals(s.kind)).map(s->s.code).toList();if(!selected.isEmpty())bindings.putAll(c.identities().findByKeys(new SyncIdentityStore.Query(kind,selected)).entrySet().stream().collect(java.util.stream.Collectors.toMap(e->kind+":"+e.getKey(),Map.Entry::getValue)));}
  var ordered=new ArrayList<Source>();var visited=new HashSet<String>();
  for(var s:b.rows())resolve(new Resolution(s,c,byCode,bindings,visited,new HashSet<>(),ordered));
  b.rows().clear();b.rows().addAll(ordered);
 }
```

### syncDataInsertAfter

[P01OrganizationSyncTask.java:111](C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P01OrganizationSyncTask.java:111)

```java
protected void syncDataInsertAfter(SyncBatch<Source,Parameters> b){b.context().identities().saveAll(b.rows().stream().filter(s->s.action==SyncWriteAction.INSERT).map(s->new SyncIdentityStore.Save(s.kind,s.code,s.target.id,fingerprint(s.target),null)).toList());}
```

