# F-CUS-001 客户主档与本地生命周期前向迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本计划在当前会话内联执行，不启用子代理。

**Goal:** 建立独立 CUS 客户主档模块，将存量客户按原 ID 前向迁移到新表，由 `/pms/customers` 承接全部当前写入，并将旧 cust/ast/customer 路径收敛为列表和详情历史只读入口。

**Architecture:** `pms-module-customer-api` 提供跨上下文客户查询、CRM 主数据写入和引用守卫契约，`pms-module-customer` 独占客户聚合、新物理表、生命周期、地点引用、审计和 Outbox。迁移不设置双写期；PROJ、AST 和前端调用方切换到新 API 后，旧表和旧路由仅保留历史读取，所有旧写请求稳定失败。

**Tech Stack:** Java 25、Spring Boot 4.1、MyBatis-Plus、MySQL 8.4、Flyway 11、JUnit 5、Mockito、Vue 3.5、TypeScript、Element Plus 2.13、pnpm 9.15。

**Spec:** `.spec-repo-f-ast-001/specs/features/F-CUS-001-customer-master-and-local-lifecycle.md`

**Design:** `docs/superpowers/specs/2026-08-25-f-cus-001-customer-master-forward-migration-design.md`

## Global Constraints

- 不使用子代理；所有任务在当前会话内按顺序执行。
- 实现前使用 `test-driven-development`，随后使用 `executing-plans` 按本计划推进。
- 新客户写路径固定为 `/pms/customers`，对外规范语义为 `/api/v1/pms/customers`。
- 旧 `pms/cust`、`pms/ast` 和现有 `/pms/customer` 仅保留列表与详情读取；任何非读取操作返回统一退役错误，不转发新写服务。
- 存量 `pms_customer` 数据迁移到 CUS 新表并保持原 ID；旧表保留历史读取，不修改 V1 至 V86 已执行迁移。
- 不设置双写、反向同步或旧写代理；新 CUS 表是唯一当前写 Owner。
- CRM 权威字段与平台字段分列保存，业务身份不可修改 CRM 权威字段，CRM 集成身份不可修改平台字段。
- 删除引用守卫失败关闭；有效引用、未知、超时或不可用均拒绝删除。
- CUS 仅保存 AST Address/Site 稳定引用，不维护 AST 地点实体。
- 新查询遵循 `docs/coding/database-query-interface.md`，分页、范围和锁查询使用场景 Query 对象及 Mapper XML。
- 不添加与本 Feature 无关的重构，不实现 INT-03、CUS-01、CUS-02 或 CUS-04。
- 每个任务完成后运行定向测试；最终必须运行后端测试、编译、前端类型检查、lint 和构建。
- 未经用户明确要求不执行 git commit；计划中的提交信息仅作为逻辑变更分组建议。

## 文件结构

| 单元 | 主要职责 |
|---|---|
| `pms-module-customer/pms-module-customer-api` | 稳定跨上下文 API、命令和 DTO，不暴露数据库实体 |
| `pms-module-customer` | 客户聚合、生命周期、权限、地点引用、守卫、历史和持久化 |
| `sql/migrations/V87__fcus001_customer_master.sql` | 新 CUS 表、约束及原 ID 存量迁移 |
| `sql/migrations/V88__fcus001_customer_menu_and_permissions.sql` | 新路径菜单权限和旧入口只读化种子 |
| `pms-module-project/.../customer` | 旧历史读取兼容层，移除写能力 |
| `yudao-ui/.../api/pms/customer` | 新 `/pms/customers` API 客户端 |
| `yudao-ui/.../views/pms/customer` | 新客户主档工作台 |
| `yudao-ui/.../views/pms/project/customer` | 旧历史只读页面 |

---

### Task 1: 建立 Customer API 与模块构建骨架

**Files:**
- Create: `pms-module-customer/pms-module-customer-api/pom.xml`
- Create: `pms-module-customer/pom.xml`
- Modify: `pom.xml`
- Modify: `yudao-server/pom.xml`
- Modify: `pms-module-project/pom.xml`
- Modify: `pms-module-asset/pom.xml`
- Test: `scripts/tests/test_fcus001_module_boundary.py`

**Interfaces:**

```java
public interface CustomerQueryApi {
    CustomerSummaryDTO getCustomer(Long customerId);
    List<CustomerSummaryDTO> getCustomers(Collection<Long> customerIds);
}

public interface CustomerMasterDataApi {
    CustomerMasterDataResult apply(CustomerMasterDataCommand command);
}

public interface CustomerReferenceGuardApi {
    CustomerReferenceGuardResult check(CustomerReferenceGuardQuery query);
}
```

- [ ] **Step 1: 编写模块边界失败测试**

创建 Python 测试，断言根 POM、server POM、customer API/实现 POM 存在，PROJ/AST 只能依赖 `pms-module-customer-api`，并禁止出现对 customer 实现模块的编译依赖。

- [ ] **Step 2: 运行测试确认失败**

Run: `python -m unittest scripts.tests.test_fcus001_module_boundary`

Expected: FAIL，提示 customer 模块或依赖尚不存在。

- [ ] **Step 3: 创建最小模块骨架**

API 模块只依赖公共 POJO 和 validation；实现模块依赖 customer API、platform API、project API、asset API、system、web、security、mybatis、validation 和 micrometer。根 POM 顺序为 customer API 后 customer 实现，server 引入 customer 实现，PROJ/AST 只引入 customer API。

- [ ] **Step 4: 运行边界测试和编译**

Run: `python -m unittest scripts.tests.test_fcus001_module_boundary`

Run: `mvn.cmd -pl pms-module-customer/pms-module-customer-api,pms-module-customer -am -DskipTests compile`

Expected: PASS。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`feat(customer): 建立客户模块边界`

---

### Task 2: 定义稳定公开 API 与生命周期类型

**Files:**
- Create: `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/query/CustomerQueryApi.java`
- Create: `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/masterdata/CustomerMasterDataApi.java`
- Create: `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/guard/CustomerReferenceGuardApi.java`
- Create: `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/query/dto/CustomerSummaryDTO.java`
- Create: `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/masterdata/dto/CustomerMasterDataCommand.java`
- Create: `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/masterdata/dto/CustomerMasterDataResult.java`
- Create: `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/guard/dto/CustomerReferenceGuardQuery.java`
- Create: `pms-module-customer/pms-module-customer-api/src/main/java/cn/iocoder/yudao/module/pms/customer/api/guard/dto/CustomerReferenceGuardResult.java`
- Test: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/api/CustomerApiContractTest.java`

**Required types:**

```java
public enum CustomerSourceType {
    CRM_SYNC, PLATFORM_CREATED, PLATFORM_TEMPORARY
}

public enum CustomerLifecycleStatus {
    ENABLED, DISABLED, DELETED
}

public enum CustomerReferenceGuardStatus {
    CLEAR, REFERENCED, UNKNOWN
}
```

- [ ] **Step 1: 编写公开 API 契约测试**

使用反射断言三个 API 的方法签名、命令中的 `tenantId/customerId/sourceKey/sourceVersion/operationId` 以及守卫结果的 `status/provider/referenceCount/dataAsOf`。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn.cmd -pl pms-module-customer -am -DskipITs=false -Dtest=CustomerApiContractTest test`

Expected: FAIL，类型不存在。

- [ ] **Step 3: 实现 API、DTO 和枚举**

DTO 使用明确字段，不使用万能 JSON 承载核心客户字段；敏感联系方式只在授权查询 DTO 中出现，守卫结果不携带业务明细全文。

- [ ] **Step 4: 运行测试和 API 编译**

Run: `mvn.cmd -pl pms-module-customer/pms-module-customer-api,pms-module-customer -am -DskipITs=false -Dtest=CustomerApiContractTest test`

Expected: PASS。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`feat(customer): 冻结客户公开契约`

---

### Task 3: 建立 CUS 物理模型并迁移存量客户

**Files:**
- Create: `sql/migrations/V87__fcus001_customer_master.sql`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/dataobject/customer/CustomerMasterDO.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/dataobject/customer/CustomerExternalMappingDO.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/dataobject/customer/CustomerFieldHistoryDO.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/dataobject/location/CustomerLocationReferenceDO.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/mysql/customer/CustomerMasterMapper.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/mysql/customer/CustomerExternalMappingMapper.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/mysql/customer/CustomerFieldHistoryMapper.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/mysql/location/CustomerLocationReferenceMapper.java`
- Test: `scripts/tests/test_fcus001_v18_migration.py`
- Test: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/dal/CustomerSchemaContractTest.java`

**Target tables:**

```text
cus_customer_master
cus_customer_external_mapping
cus_customer_field_history
cus_customer_location_reference
```

- [ ] **Step 1: 编写迁移结构失败测试**

断言 V87 创建四张表，客户编码唯一键包含 `tenant_id`，当前 CRM 映射唯一键限制 `tenant_id/source_system/source_key/current_flag`，主表包含 `version/deleted/source_type/sync_status/data_as_of`，并包含从 `pms_customer` 按原 `id` 复制的 SQL。

- [ ] **Step 2: 运行迁移测试确认失败**

Run: `python -m unittest scripts.tests.test_fcus001_v18_migration`

Expected: FAIL，V87 不存在。

- [ ] **Step 3: 编写 V87 前向迁移**

使用 `INSERT ... SELECT` 保持 `id/tenant_id/code/name/short_name/status/remark/creator/create_time/updater/update_time/deleted/version`；旧地址写入迁移快照字段，不创建虚假 AST 引用；使用 `NOT EXISTS` 保证重复执行不会产生第二份目标记录；源数据冲突通过唯一键显式失败。

- [ ] **Step 4: 对齐 DO 与 Mapper**

`CustomerMasterDO` 继承 `TenantBaseDO`，使用 `@Version`；外部映射和地点引用保存有效区间；字段历史只允许 insert，不提供覆盖更新服务。

- [ ] **Step 5: 运行迁移与 Schema 契约测试**

Run: `python -m unittest scripts.tests.test_fcus001_v18_migration`

Run: `mvn.cmd -pl pms-module-customer -am -DskipITs=false -Dtest=CustomerSchemaContractTest test`

Expected: PASS。

- [ ] **Step 6: 逻辑提交分组**

建议提交信息：`feat(customer): 建立客户主档物理模型`

---

### Task 4: 实现客户领域规则与命令模型

**Files:**
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/domain/customer/CustomerRules.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/domain/customer/CustomerFieldOwnershipRules.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/customer/command/CreateCustomerCommand.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/customer/command/UpdateCustomerCommand.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/customer/command/CustomerLifecycleCommand.java`
- Test: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/domain/customer/CustomerRulesTest.java`
- Test: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/domain/customer/CustomerFieldOwnershipRulesTest.java`

**Rules:**

```java
CustomerRules.validateTemporaryCustomer(sourceType, sourceKey, sourceVersion, reason, reconciliationPending);
CustomerRules.validateTransition(currentStatus, action);
CustomerFieldOwnershipRules.validateBusinessUpdate(changedFields, crmMapped);
CustomerFieldOwnershipRules.validateCrmUpdate(changedFields);
```

- [ ] **Step 1: 编写领域规则失败测试**

覆盖临时客户必须有原因和待对账、禁止 CRM 来源键、删除后只能恢复、业务身份不能修改 CRM 字段、CRM 身份不能修改平台字段。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn.cmd -pl pms-module-customer -am -DskipITs=false -Dtest=CustomerRulesTest,CustomerFieldOwnershipRulesTest test`

Expected: FAIL。

- [ ] **Step 3: 实现最小领域规则**

字段白名单显式列出 CRM 切片和平台切片；非法字段集合完整返回到异常上下文，不根据前端禁用状态放行。

- [ ] **Step 4: 运行领域测试**

Run: `mvn.cmd -pl pms-module-customer -am -DskipITs=false -Dtest=CustomerRulesTest,CustomerFieldOwnershipRulesTest test`

Expected: PASS。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`feat(customer): 实现客户生命周期领域规则`

---

### Task 5: 实现原子创建、更新、审计与 Outbox

**Files:**
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/customer/CustomerApplicationService.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/customer/CustomerApplicationServiceImpl.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/history/CustomerHistoryService.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/outbox/CustomerOutboxPayloadFactory.java`
- Modify: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/mysql/customer/CustomerMasterMapper.java`
- Create: `pms-module-customer/src/main/resources/mapper/customer/CustomerMasterMapper.xml`
- Test: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/service/customer/CustomerApplicationServiceTest.java`
- Test: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/service/customer/CustomerCommandMySqlTest.java`

**Transaction contract:**

```java
CustomerCommandResult create(CreateCustomerCommand command);
CustomerCommandResult update(UpdateCustomerCommand command);
```

每次成功事务写主档、字段历史、平台审计和 `CustomerUpdated` Outbox；幂等通过 `PlatformCommandExecutionApi` 统一执行。

- [ ] **Step 1: 编写应用服务失败测试**

覆盖首次创建、同键同载荷重放、同键异载荷冲突、编码冲突、CRM 映射冲突、字段 Owner 隔离、旧版本无副作用以及 Outbox 不携带敏感明文。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn.cmd -pl pms-module-customer -am -DskipITs=false -Dtest=CustomerApplicationServiceTest test`

Expected: FAIL。

- [ ] **Step 3: 实现原子命令服务**

创建前按租户查询编码和映射；更新使用 XML 中的 `WHERE id = ? AND tenant_id = ? AND version = ?` CAS；字段历史使用摘要；平台命令回调内部完成全部写入。

- [ ] **Step 4: 增加真实 MySQL 约束测试**

验证租户内编码唯一、跨租户同编码允许、当前 CRM 映射唯一、软删除不释放编码、并发更新只有一个成功。

- [ ] **Step 5: 运行单元与 MySQL 测试**

Run: `mvn.cmd -pl pms-module-customer -am -DskipITs=false -Dtest=CustomerApplicationServiceTest,CustomerCommandMySqlTest test`

Expected: PASS。

- [ ] **Step 6: 逻辑提交分组**

建议提交信息：`feat(customer): 实现客户原子写入`

---

### Task 6: 实现停用、删除、恢复和失败关闭守卫

**Files:**
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/guard/CustomerDeletionGuardService.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/guard/CustomerDeletionGuardResult.java`
- Modify: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/customer/CustomerApplicationService.java`
- Modify: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/customer/CustomerApplicationServiceImpl.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/customer/ProjectCustomerReferenceGuardApi.java`
- Create: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/customer/AssetCustomerReferenceGuardApi.java`
- Test: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/service/guard/CustomerDeletionGuardServiceTest.java`
- Test: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/service/customer/CustomerLifecycleMySqlTest.java`

- [ ] **Step 1: 编写守卫和生命周期失败测试**

覆盖全部 CLEAR 才允许删除；任一 REFERENCED、UNKNOWN、异常或超时均拒绝；停用不删除历史；恢复保留原 ID、编码和映射；并发删除/恢复只有一个成功。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn.cmd -pl pms-module-customer -am -DskipITs=false -Dtest=CustomerDeletionGuardServiceTest,CustomerLifecycleMySqlTest test`

Expected: FAIL。

- [ ] **Step 3: 实现失败关闭守卫**

聚合 PROJ、AST 和本模块已登记守卫；将异常、超时和空结果归一为 UNKNOWN；删除应用服务只接受最终 CLEAR。

- [ ] **Step 4: 实现 CAS 生命周期动作**

停用、删除、恢复都要求 `reason/expectedVersion/idempotencyKey`，使用条件更新并追加历史、审计和 Outbox。

- [ ] **Step 5: 运行生命周期测试**

Run: `mvn.cmd -pl pms-module-customer -am -DskipITs=false -Dtest=CustomerDeletionGuardServiceTest,CustomerLifecycleMySqlTest test`

Expected: PASS。

- [ ] **Step 6: 逻辑提交分组**

建议提交信息：`feat(customer): 实现客户生命周期守卫`

---

### Task 7: 实现 AST 地点引用和跨域摘要查询

**Files:**
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/location/CustomerLocationReferenceService.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/location/command/CustomerLocationCommand.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/summary/CustomerProjectSummaryService.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/summary/CustomerDeviceSummaryService.java`
- Extend: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/*`
- Extend: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/*`
- Test: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/service/location/CustomerLocationReferenceServiceTest.java`
- Test: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/service/summary/CustomerSummarySliceServiceTest.java`

- [ ] **Step 1: 编写地点和摘要失败测试**

覆盖 Address/Site 有效引用、SiteLocation 类型拒绝、跨租户拒绝、来源版本过期拒绝、Owner 摘要成功、Owner 不可用返回 `available=false/dataAsOf`。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn.cmd -pl pms-module-customer -am -DskipITs=false -Dtest=CustomerLocationReferenceServiceTest,CustomerSummarySliceServiceTest test`

Expected: FAIL。

- [ ] **Step 3: 实现地点引用维护**

调用现有 `AssetLocationApi` 校验引用；关闭旧有效区间后插入新引用；不调用 AST 管理写接口，不写 AST 表。

- [ ] **Step 4: 实现项目和设备摘要切片**

通过公开 API 分页查询；原样保留 provider、dataAsOf 和 available，禁止用缓存数量伪装完整真值。

- [ ] **Step 5: 运行测试**

Run: `mvn.cmd -pl pms-module-customer -am -DskipITs=false -Dtest=CustomerLocationReferenceServiceTest,CustomerSummarySliceServiceTest test`

Expected: PASS。

- [ ] **Step 6: 逻辑提交分组**

建议提交信息：`feat(customer): 接入客户地点与归属摘要`

---

### Task 8: 实现新 `/pms/customers` 控制器与权限裁剪

**Files:**
- Create: `sql/migrations/V88__fcus001_customer_classification_scope.sql`
- Modify: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/dataobject/customer/CustomerMasterDO.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/dataobject/classification/CustomerMarketRelationDO.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/dataobject/security/CustomerScopeSliceDO.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/mysql/classification/CustomerMarketRelationMapper.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/mysql/security/CustomerScopeSliceMapper.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/security/CustomerScopeResolver.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/CustomerController.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/vo/CustomerPageReqVO.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/vo/CustomerCreateReqVO.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/vo/CustomerUpdateReqVO.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/vo/CustomerLifecycleReqVO.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/vo/CustomerRespVO.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/query/CustomerQueryService.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/security/CustomerFieldMaskingService.java`
- Create: `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/mysql/customer/query/VisibleCustomerPageQuery.java`
- Test: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/service/security/CustomerScopeResolverTest.java`
- Test: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/controller/admin/customer/CustomerControllerContractTest.java`
- Test: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/service/query/CustomerQueryServiceTest.java`

**Permissions:**

```text
pms:customer:query
pms:customer:create
pms:customer:update
pms:customer:disable
pms:customer:delete
pms:customer:restore
pms:customer:sensitive-read
pms:customer:export
```

- [ ] **Step 1: 编写权限切片、控制器和查询失败测试**

断言 `departmentCode` 是唯一办事处关联字段；市场部、系统部、拓展部、子行业四级值精确命中合法组合；五个维度均支持复选且维度内 OR、维度间 AND、切片间 OR；不同切片不得展平产生交叉越权；管理员角色 `super_admin`、`tenant_admin`、`crm_admin` 无显式配置时默认全量，其他业务角色缺少任一维度授权时空范围。另断言 REST 路径、HTTP 方法、权限、幂等请求头、`If-Match`、空范围空页、联系方式明文/脱敏/隐藏三态。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn.cmd -pl pms-module-customer -am -DskipITs=false -Dtest=CustomerScopeResolverTest,CustomerControllerContractTest,CustomerQueryServiceTest test`

Expected: FAIL。

- [ ] **Step 3: 实现 V88 数据模型和合法组合校验**

前向迁移为客户主表增加 `department_code/department_name` 和市场行业八字段，创建 `cus_market_relation` 与客户权限切片表。授权切片保存用户或角色、五维范围模式和复选编码集合；不保存 `department_id`、`office_*` 或 `market_relation_id`。普通业务写入必须校验部门编码和四级合法组合，CRM 权威映射不得根据名称猜测。

- [ ] **Step 4: 实现权限范围解析与查询 SQL**

服务端合并用户和角色切片，同一切片内维度内 OR、维度间 AND，多个切片间 OR；通过 `cus_market_relation` 裁剪合法四级路径。分页 Query 携带租户和不可展平的场景化权限切片，Mapper XML 输出括号明确的动态条件；合法空范围直接返回空，不得省略条件。

- [ ] **Step 5: 实现新资源控制器与脱敏**

控制器只做请求转换与权限声明；应用服务读取 `Idempotency-Key` 和 `If-Match`；详情组合地点、项目、设备和历史切片。电话和邮箱按权限返回原值、掩码或 null；创建和更新校验目标部门与四级分类位于操作者有效切片。

- [ ] **Step 6: 运行测试**

Run: `mvn.cmd -pl pms-module-customer -am -DskipITs=false -Dtest=CustomerScopeResolverTest,CustomerControllerContractTest,CustomerQueryServiceTest test`

Expected: PASS。

- [ ] **Step 6: 逻辑提交分组**

建议提交信息：`feat(customer): 提供客户主档新资源接口`

---

### Task 9: 将旧 customer/cust/ast 路由收敛为历史只读

**Files:**
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/customer/CustomerController.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/customer/CustomerService.java`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/customer/CustomerServiceImpl.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/customer/LegacyCustomerWriteRetiredException.java`
- Modify or create matching legacy `pms/cust` and `pms/ast` customer controllers found during implementation audit
- Test: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/controller/admin/customer/LegacyCustomerReadOnlyContractTest.java`
- Test: `scripts/tests/test_fcus001_no_legacy_writes.py`

**Legacy behavior:**

```java
@GetMapping("/page")
@GetMapping("/get")

@PostMapping("/create")
@PutMapping("/update")
@DeleteMapping("/delete")
// all throw CUSTOMER_LEGACY_ROUTE_READ_ONLY with replacementPath=/pms/customers
```

- [ ] **Step 1: 编写旧路由失败测试**

断言列表和详情仍可读取 `pms_customer` 历史数据；创建、更新、删除、启停和恢复返回稳定错误；扫描旧 CustomerMapper 写调用只允许出现在迁移或测试夹具中。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn.cmd -pl pms-module-project -am -DskipITs=false -Dtest=LegacyCustomerReadOnlyContractTest test`

Run: `python -m unittest scripts.tests.test_fcus001_no_legacy_writes`

Expected: FAIL，旧写服务仍存在。

- [ ] **Step 3: 只读化旧控制器和服务**

保留 `getCustomer/getCustomerPage`；移除 Service 写接口或使兼容控制器直接抛退役错误；旧读取响应标记 `legacyReadOnly=true` 和 `replacementPath=/pms/customers`。

- [ ] **Step 4: 扫描并处置 cust/ast 写路由**

使用内容检索识别所有 `pms/cust`、`pms/ast` 客户写路由；保留列表和详情读取，其余按同一错误语义停止。若不存在对应路由，测试固定断言未来不能新增非 GET 写入口。

- [ ] **Step 5: 运行旧路由和架构测试**

Run: `mvn.cmd -pl pms-module-project,pms-module-asset -am -DskipITs=false -Dtest=LegacyCustomerReadOnlyContractTest test`

Run: `python -m unittest scripts.tests.test_fcus001_no_legacy_writes`

Expected: PASS。

- [ ] **Step 6: 逻辑提交分组**

建议提交信息：`refactor(customer): 停止旧客户写路由`

---

### Task 10: 切换 PROJ、AST 与服务端运行时依赖

**Files:**
- Modify: customer usages found under `pms-module-project/src/main/java`
- Modify: customer usages found under `pms-module-asset/src/main/java`
- Modify: `yudao-server/pom.xml`
- Modify: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectmanual/*`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/*`
- Test: `scripts/tests/test_fcus001_owner_boundary.py`
- Test: affected PROJ and AST service tests

- [ ] **Step 1: 编写 Owner 边界失败测试**

扫描 PROJ/AST，禁止导入 `pms.customer.service/dal/domain` 和直接写 `cus_*` 表；要求跨域客户查询使用 customer API；要求建立新项目或设备关系前查询客户可用状态。

- [ ] **Step 2: 运行测试确认失败**

Run: `python -m unittest scripts.tests.test_fcus001_owner_boundary`

Expected: FAIL，现有代码仍依赖旧 CustomerService/Mapper。

- [ ] **Step 3: 切换调用方**

将项目创建、项目详情、设备关联及其他客户读取改为 `CustomerQueryApi`；建立新关系前拒绝 DISABLED/DELETED 客户；不把客户字段复制成新的 Owner 真值。

- [ ] **Step 4: 运行边界和受影响测试**

Run: `python -m unittest scripts.tests.test_fcus001_owner_boundary`

Run: `mvn.cmd -pl pms-module-project,pms-module-asset,pms-module-customer,yudao-server -am -DskipITs=false test`

Expected: PASS。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`refactor(customer): 切换客户跨域调用方`

---

### Task 11: 新增菜单权限并迁移前端 API

**Files:**
- Create: `sql/migrations/V89__fcus001_customer_menu_and_permissions.sql`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/customer/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/customer/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/customer-contract.spec.ts`
- Test: `scripts/tests/test_fcus001_frontend_contract.py`

**New API client:**

```ts
const baseUrl = '/pms/customers'

export const getCustomerPage = (params: CustomerPageReqVO) =>
  request.get({ url: baseUrl, params })
export const getCustomer = (id: number) =>
  request.get({ url: `${baseUrl}/${id}` })
export const createCustomer = (data: CustomerCreateReqVO, idempotencyKey: string) =>
  request.post({ url: baseUrl, data, headers: { 'Idempotency-Key': idempotencyKey } })
```

- [ ] **Step 1: 编写前端契约失败测试**

断言新客户端使用 `/pms/customers`，生命周期动作使用 action 路径，更新发送 `If-Match` 和幂等键；旧客户端只导出 page/get，不导出 create/update/delete。

- [ ] **Step 2: 运行测试确认失败**

Run: `python -m unittest scripts.tests.test_fcus001_frontend_contract`

Expected: FAIL。

- [ ] **Step 3: 编写 V88 菜单与权限迁移**

新增新客户工作台菜单和八项权限；旧客户菜单改名为“客户历史（只读）”并指向旧页面；不复用旧写权限作为新写授权。

- [ ] **Step 4: 实现新客户端并只读化旧客户端**

新类型区分 CRM 字段、平台字段、来源状态、版本和切片可用性；旧客户端仅保留列表和详情。

- [ ] **Step 5: 运行契约与类型检查**

Run: `python -m unittest scripts.tests.test_fcus001_frontend_contract`

Run: `pnpm ts:check`

Working directory: `yudao-ui/yudao-ui-admin-vue3`

Expected: PASS。

- [ ] **Step 6: 逻辑提交分组**

建议提交信息：`feat(customer): 迁移客户菜单与前端接口`

---

### Task 12: 建设新客户工作台并只读化旧页面

**Files:**
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/components/CustomerFormDrawer.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/components/CustomerSourcePanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/components/CustomerLocationPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/components/CustomerRelationSummaryPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/components/CustomerHistoryPanel.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/customer/index.vue`
- Test: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/customer-workbench.spec.ts`

- [ ] **Step 1: 编写页面契约失败测试**

断言新页面包含来源、平台字段、地点、项目设备摘要、历史和生命周期操作；CRM 字段只读；旧页面无创建、编辑、删除按钮并展示新入口。

- [ ] **Step 2: 运行页面契约测试确认失败**

Run: `pnpm exec vitest run src/views/pms/customer/customer-workbench.spec.ts`

Working directory: `yudao-ui/yudao-ui-admin-vue3`

Expected: FAIL；若项目未配置 Vitest，则使用现有静态契约测试脚本并在本任务内记录替代命令。

- [ ] **Step 3: 实现新工作台**

列表支持编码、名称、行业、来源、状态和同步状态筛选；抽屉按 CRM/平台字段分区；临时客户显示待匹配；删除前展示服务端守卫切片；联系方式只消费服务端裁剪结果。

- [ ] **Step 4: 只读化旧页面**

移除所有写按钮、表单和写 API 导入；展示历史只读提示、数据截止时间和跳转新工作台链接。

- [ ] **Step 5: 运行定向测试、类型检查和 lint**

Run: `pnpm ts:check`

Run: `pnpm lint:eslint:check`

Run: `pnpm lint:style:check`

Working directory: `yudao-ui/yudao-ui-admin-vue3`

Expected: PASS。

- [ ] **Step 6: 逻辑提交分组**

建议提交信息：`feat(customer): 建设客户主档工作台`

---

### Task 13: 完成真实 MySQL、迁移一致性和浏览器验收

**Files:**
- Create: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/service/CustomerMigrationMySqlIntegrationTest.java`
- Create: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/service/CustomerTenantPermissionMySqlIntegrationTest.java`
- Create: `scripts/tests/run_fcus001_browser_acceptance.mjs`
- Create: `output/f-cus-001-v18/browser-acceptance.md`
- Create: `output/f-cus-001-v18/database-evidence.md`
- Create: `output/f-cus-001-v18/regression-summary.md`
- Create screenshots under: `output/f-cus-001-v18/screenshots/`

- [ ] **Step 1: 编写迁移一致性 MySQL 测试**

准备旧 `pms_customer` 种子，执行 V87 后断言源目标数量、ID 集合、租户、编码、名称、状态和版本一致；断言第二次迁移不会新增重复记录。

- [ ] **Step 2: 编写租户、权限和并发 MySQL 测试**

覆盖跨租户不可见、空范围空页、敏感字段权限、编码唯一、CRM 映射唯一、软删除恢复、幂等和并发 CAS。

- [ ] **Step 3: 运行 MySQL 集成测试**

Run: `mvn.cmd -pl pms-module-customer -am -DskipITs=false -Dtest=CustomerMigrationMySqlIntegrationTest,CustomerTenantPermissionMySqlIntegrationTest test`

Expected: PASS。

- [ ] **Step 4: 编写并运行浏览器验收**

脚本覆盖创建平台客户、临时客户、CRM 字段只读、平台字段更新、地点引用、停用、删除阻断、删除成功、恢复、旧页面只读和刷新持久化；在 320/768/1024/1440 视口截图并检查页面级横向溢出。

Run: `node scripts/tests/run_fcus001_browser_acceptance.mjs`

Expected: 生成完整截图和 JSON/Markdown 证据，所有断言 PASS。

- [ ] **Step 5: 汇总证据**

数据库证据记录迁移数量、唯一约束、版本冲突和守卫无副作用；回归摘要列出后端、前端、MySQL 和浏览器命令及结果。

- [ ] **Step 6: 逻辑提交分组**

建议提交信息：`test(customer): 完成客户主档验收证据`

---

### Task 14: 更新规格追踪和实现基线

**Files:**
- Create: `tasks/features/F-CUS-001.md`
- Modify: `docs/traceability/requirement-matrix.md`
- Modify: `docs/traceability/phase2-contract-map.md`
- Modify: `docs/traceability/domain-object-table-map.json`
- Modify: `docs/traceability/domain-entity-migration-contract.json`
- Modify: `docs/traceability/core-migration-schema-contract.json`
- Modify: `docs/specification-baseline/allowlist.json`
- Modify: `docs/specification-baseline/manifest.json`
- Modify: `tasks/implementation-baseline-inventory.json`
- Modify: `tasks/implementation-baseline-inventory.md`
- Test: `scripts/tests/test_specification_baseline.py`
- Test: `scripts/tests/test_implementation_baseline_inventory.py`

- [ ] **Step 1: 登记任务与追踪关系**

将 F-CUS-001 的规格、设计、迁移、模块、API、页面、测试和证据逐项映射；明确 INT-03、CUS-01、CUS-02、CUS-04 和 Release 未完成。

- [ ] **Step 2: 更新机器契约**

登记 `cus_*` 表、DO、Mapper 和 API；将旧 `pms_customer` 标记为历史只读载体，不删除历史映射。

- [ ] **Step 3: 重建并校验基线**

Run: `python scripts/specification_baseline.py --write`

Run: `python -m unittest scripts.tests.test_specification_baseline scripts.tests.test_implementation_baseline_inventory`

Expected: PASS。

- [ ] **Step 4: 逻辑提交分组**

建议提交信息：`docs(customer): 更新客户主档工程追踪`

---

### Task 15: 最终验证与缺陷修复

**Files:**
- Modify only files required by failures from the commands below.

- [ ] **Step 1: 运行后端定向测试**

Run: `mvn.cmd -pl pms-module-customer,pms-module-project,pms-module-asset,yudao-server -am -DskipITs=false test`

Expected: PASS。

- [ ] **Step 2: 运行后端编译**

Run: `mvn.cmd -pl pms-module-customer,pms-module-project,pms-module-asset,yudao-server -am -DskipTests compile`

Expected: PASS。

- [ ] **Step 3: 运行 Python 契约与基线测试**

Run: `python -m unittest scripts.tests.test_fcus001_module_boundary scripts.tests.test_fcus001_v18_migration scripts.tests.test_fcus001_no_legacy_writes scripts.tests.test_fcus001_owner_boundary scripts.tests.test_fcus001_frontend_contract scripts.tests.test_specification_baseline scripts.tests.test_implementation_baseline_inventory`

Expected: PASS。

- [ ] **Step 4: 运行前端完整静态检查**

Working directory: `yudao-ui/yudao-ui-admin-vue3`

Run: `pnpm icons:check`

Run: `pnpm ts:check`

Run: `pnpm lint`

Expected: PASS。

- [ ] **Step 5: 运行前端构建**

Run: `pnpm build:local`

Working directory: `yudao-ui/yudao-ui-admin-vue3`

Expected: PASS。

- [ ] **Step 6: 检查工作区与差异质量**

Run: `git diff --check`

Run: `git status --short`

Expected: 无空白错误；只包含 F-CUS-001 设计、计划、实现、迁移、测试和证据范围内文件。

- [ ] **Step 7: 更新任务状态**

只有在全部测试、lint、类型检查和构建通过后，将 `tasks/features/F-CUS-001.md` 标记为实现完成；若真实浏览器或 MySQL 环境不可用，保持对应验收项未完成并记录阻断原因。
