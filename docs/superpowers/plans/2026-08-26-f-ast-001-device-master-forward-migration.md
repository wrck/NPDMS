# F-AST-001 设备主档前向迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有资产模块内建立以租户内唯一 SN 为业务身份的 AST 设备主档，将旧设备按原 ID 前向迁移到新 Owner，交付高性能设备列表、六类详情、来源降级、软件版本匹配、时态归属、装配、位置、维保和安全配置 Log 下载；旧写代码仅通过普通角色权限撤销限制使用并保留 `super_admin`，同时补齐 CUS 客户详情的 AST 设备摘要读取链路。

**Architecture:** `pms-module-asset` 继续作为 AST 实现模块，`pms-module-asset-api` 提供稳定跨模块设备契约。`ast_device` 保存稳定身份和高频当前事实投影，完整发货、版本、归属、装配、位置、维保和核对事实独立保存；跨模块状态解释由已实现 Owner 的 `-api` 完成，尚未实现的外部系统或业务模块只保留公开接口、消费端口和 `NOT_AVAILABLE` 降级，不创建伪实现或越权直读。管理端实际路径使用 `/pms/asset/devices`，正式 `/api/v1/pms/devices` 语义由当前网关统一增加 `/api/v1` 前缀，不创建重复 Controller；CUS 继续经 `AssetCustomerDeviceSummaryApi` 读取 AST 轻量设备摘要。

**Tech Stack:** Java 25、Spring Boot 4.1、Spring Security、MyBatis-Plus、MySQL 8.4、Flyway 11、JUnit 5、Mockito、Python unittest、Vue 3.5、TypeScript、Element Plus、pnpm 9.15、Chrome DevTools。

**Locked Inputs:**

- Feature Spec：`.spec-repo-f-ast-001/specs/features/F-AST-001-device-serial-archive-and-temporal-assignment.md`
- Feature Spec commit：`92726b8c60c48a5c4923b6d5addeb5314a94bb97`
- 补充设计：`docs/superpowers/specs/2026-08-26-f-ast-001-device-master-forward-migration-design.md`
- 补充设计 commit：`d036bcb`

## 1. 实施边界

- 当前在代码仓工作树实施；正式 PRD、SDS、allowlist、manifest 和受管快照回写留到后续合并阶段。
- `.spec-repo-f-ast-001` Feature Spec 与代码仓补充设计共同作为本次锁定输入；`specs/001-project-delivery-platform` 仅用于核验历史字段来源。
- 不修改已执行迁移 V1～V89；开始数据库任务前重新扫描迁移号，当前计划预留 V90～V98。
- 不建立双写、触发器双写、旧接口写代理或反向同步；新 `ast_device` 是唯一当前设备写 Owner。
- `deviceId` 和 SN 创建后不可变；租户内 SN 永久唯一，软删除不释放。
- 普通列表只读取 `ast_device` 轻量显式投影，不使用 `SELECT *`，不关联 `ast_device_shipment`，不读取 `product_desc`、`location_snapshot`。
- KNO、MES、ITR 的外部连接与同步运行闭环不属于本 Feature；未接入的来源切片返回 `NOT_AVAILABLE`，受控种子不得被声明为外部同步完成。
- 当前代码库尚未实现的外部系统或业务模块只保留已冻结的公开接口、消费端口和 `NOT_AVAILABLE` 降级；不新增伪实现、空业务模块、本地替代业务表、模拟同步完成状态或跨模块直读。
- `ast_product_official_info` 和 `ast_product_official_version` 只作为 KNO 已发布事实的 AST 本地只读消费副本，不提供 AST 业务写入口，不改变 KNO Owner。
- 旧 `/pms/equipment` 控制器、Service、前端 API 和页面写代码保持不变；迁移只撤销普通业务角色的旧新增、更新、删除和状态变更权限，`super_admin` 继续通过平台全菜单语义保留旧写能力。
- CUS 客户详情补缺只修改 `AssetCustomerDeviceSummaryApi` 的 AST 实现查询来源：从 `pms_equipment` 切换为 `ast_device` 当前客户投影及必要关系事实；公开 DTO、CUS 客户主档、客户写入、CRM 同步、权限模型和其他详情切片不变。
- 配置 Log 下载不直接返回对象存储预签名 URL；使用绑定租户、用户、设备、日志和短有效期的一次性应用下载授权，下载时重新鉴权并消费授权。
- 未经用户明确批准，不修改 `yudao-framework`、`yudao-module-*`、根构建基线或其他基础框架文件；优先复用现有公开 API。若现有能力无法闭合验收，停止对应任务并单独提交基础框架变更方案，不得绕过审批实施。
- 所有新增查询遵循 `docs/coding/database-query-interface.md`；联表、动态集合、递归、锁和执行计划敏感 SQL 进入 Mapper XML。
- 每项生产实现必须先有失败测试并观察到预期失败，再写最小实现。
- 未经用户再次明确要求，不提交实施代码；每个任务中的提交信息只表示建议逻辑分组。

## 2. 实施依赖图

```text
Task 1 本地输入与静态门禁
  -> Task 2 跨模块公开契约与依赖
  -> Task 3 主档、来源、发货、版本 Schema
  -> Task 4 归属、装配、位置、维保、下载 Schema
  -> Task 5 旧设备明确字段前向迁移
  -> Task 6 设备身份与冲突闭环
  -> Task 7 轻量列表与固定详情外壳
  -> Task 8 发货、版本、CONP 与来源降级
  -> Task 9 项目/客户时态归属与祖先投影
  -> Task 10 装配、位置、维保与投影重建
  -> Task 11 配置 Log 元数据与安全下载
  -> Task 12 新工作台与外部切片接口降级
  -> Task 13 旧写普通角色权限撤销与超管保留
  -> Task 14 初始化与性能夹具
  -> Task 15 完整验证
  -> Task 16 CUS 客户设备摘要读取补缺与受影响回归复跑
```

## 3. 文件职责

| 路径                                                        | 职责                            |
| --------------------------------------------------------- | ----------------------------- |
| `pms-module-asset/pms-module-asset-api/.../device`        | 设备查询、范围和归属结果稳定 DTO，不暴露 DO     |
| `pms-module-project/pms-module-project-api/.../reference` | PROJ 解释项目存在、租户、状态、树版本和可归属性    |
| `pms-module-asset/.../device`                             | 设备身份、主档、列表、详情和来源聚合            |
| `pms-module-asset/.../shipment`                           | 完整发货事实和主档四字段投影                |
| `pms-module-asset/.../version`                            | 软件版本继承、CONP 解析和公告匹配           |
| `pms-module-asset/.../assignment`                         | 项目/客户时态归属、CAS、核对、Outbox 和祖先投影 |
| `pms-module-asset/.../assembly`                           | 任意深度装配、拔出、替换和环检测              |
| `pms-module-asset/.../warranty`                           | 客观维保当前事实和续保记录                 |
| `pms-module-asset/.../configurationlog`                   | 元数据裁剪、一次性下载授权和下载审计            |
| `sql/migrations/V90...V98`                                | 新表、明确字段迁移、菜单权限、种子和性能夹具        |
| `yudao-ui/.../asset/device`                               | 新设备工作台、六 Tab 和关联抽屉            |
| `scripts/tests/test_fast001_*`                            | 静态迁移、Owner、查询、旧入口和前端契约        |

***

### Task 1: 锁定本地实施输入和静态门禁

**Files:**

- Create: `scripts/tests/test_fast001_implementation_input.py`
- Create: `scripts/tests/test_fast001_owner_boundary.py`
- Create: `scripts/tests/test_fast001_query_contract.py`
- [ ] **Step 1: 编写实施输入失败测试**

测试必须读取规格仓 HEAD 并断言等于完整提交 `92726b8c60c48a5c4923b6d5addeb5314a94bb97`；检查 Feature Spec 包含统一时态表名、CONP 四字段、200 万/400 万规模、续保客观记录和旧入口只读；检查补充设计包含 17 张目标表。

- [ ] **Step 2: 编写 Owner 边界失败测试**

扫描后续新增的 `device/shipment/version/assignment/assembly/warranty/configurationlog` 包和 Mapper XML：允许访问 AST 自有 `ast_*` 表以及公开 `-api`，禁止依赖其他模块 `service/dal/repository` 或直接出现 `proj_`、`cus_`、`cut_`、`imp_`、`kno_` 业务表。

- [ ] **Step 3: 编写查询规范失败测试**

断言 `DeviceQueryMapper.xml` 存在 `selectVisibleDevicePage`，显式选择轻量字段，不包含 `SELECT *`、`JOIN ast_device_shipment`、`${}`、`product_desc` 或 `location_snapshot`；新增设备包禁止 SQL 注解和 `.last(...)`。

- [ ] **Step 4: 运行测试确认 RED**

Run:

```powershell
python -m unittest scripts.tests.test_fast001_implementation_input scripts.tests.test_fast001_owner_boundary scripts.tests.test_fast001_query_contract
```

Expected：实施输入测试 PASS；Owner 和查询测试因新实现尚不存在而 FAIL。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`test(asset): 建立F-AST-001实施门禁`

***

### Task 2: 闭合跨模块公开契约和 Maven 依赖

**Files:**

- Modify: `pms-module-asset/pom.xml`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/reference/ProjectDeviceAssignmentGuardApi.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/reference/dto/ProjectDeviceAssignmentGuardQuery.java`
- Create: `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/reference/dto/ProjectDeviceAssignmentGuardResult.java`
- Create: `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/reference/ProjectDeviceAssignmentGuardApiImpl.java`
- Create: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/DeviceQueryApi.java`
- Create: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/dto/DeviceSummaryDTO.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/api/device/DeviceApiContractTest.java`
- Create: `pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/reference/ProjectDeviceAssignmentGuardApiImplTest.java`

**Required contract:**

```java
public interface ProjectDeviceAssignmentGuardApi {
    ProjectDeviceAssignmentGuardResult validate(ProjectDeviceAssignmentGuardQuery query);
}
```

```java
public record ProjectDeviceAssignmentGuardResult(
        Long projectId,
        Long tenantId,
        Long customerId,
        Long rootProjectId,
        Long treeVersion,
        boolean assignable,
        String rejectionCode) {
}
```

- [ ] **Step 1: 编写公开契约失败测试**

反射断言 PROJ 守卫输入包含 `tenantId/projectId/actorId`，结果包含项目、客户、根项目、树版本、是否可归属和拒绝码；设备 API 只暴露稳定摘要，不暴露 DO。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
mvn.cmd -pl pms-module-project,pms-module-asset -am -DskipITs=false -Dtest=ProjectDeviceAssignmentGuardApiImplTest,DeviceApiContractTest test
```

Expected：FAIL，契约不存在。

- [ ] **Step 3: 实现最小契约与依赖**

`pms-module-asset` 增加 `pms-module-project-api`、`pms-module-platform-api` 和 `yudao-module-infra` 依赖。PROJ 实现使用自己的 Mapper/Service 判断项目存在、租户、当前状态、实际节点、管理范围和树版本；AST 只调用 API。

- [ ] **Step 4: 运行边界测试和模块编译**

Run:

```powershell
mvn.cmd -pl pms-module-project/pms-module-project-api,pms-module-project,pms-module-asset/pms-module-asset-api,pms-module-asset -am -DskipITs=false -Dtest=ProjectDeviceAssignmentGuardApiImplTest,DeviceApiContractTest test
python -m unittest scripts.tests.test_fast001_owner_boundary
```

Expected：PASS。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`feat(asset): 冻结设备跨模块契约`

***

### Task 3: 建立主档、来源、发货和软件版本 Schema

**Files:**

- Create: `sql/migrations/V90__fast001_device_master_and_source_facts.sql`
- Create: `sql/migrations/V91__fast001_device_shipments_and_software_versions.sql`
- Create: `scripts/tests/test_fast001_migration_contract.py`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/dal/DeviceSchemaContractTest.java`

**V90 tables:**

```text
ast_device
ast_device_factory_info
```

**V91 tables:**

```text
ast_device_shipment
ast_device_factory_version
ast_product_official_info
ast_product_official_version
ast_device_network_version
ast_device_network_version_event
```

- [ ] **Step 1: 编写迁移失败测试**

断言主档 `uk_ast_device_tenant_sn(tenant_id,sn)` 不含 deleted；主档包含发货、项目、客户、位置、维保和 CONP 投影；软件版本表一行保存完整组合；官网表只有来源和已发布只读事实字段，不包含 AST 发布状态命令字段。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
python -m unittest scripts.tests.test_fast001_migration_contract
```

Expected：FAIL，V90/V91 不存在。

- [ ] **Step 3: 实现 V90 和 V91**

从补充设计提取 DDL；来源时间统一 `source_updated_at/synced_at`；CONP 统一 `conp_version/conp_type/conp_series/conp_mark`；`boot/conboot` 目标只使用 `boot_version`。

- [ ] **Step 4: 运行静态 Schema 测试**

Run:

```powershell
python -m unittest scripts.tests.test_fast001_migration_contract
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceSchemaContractTest test
```

Expected：PASS。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`feat(asset): 建立设备主档与版本事实`

***

### Task 4: 建立归属、关系、位置、维保和下载授权 Schema

**Files:**

- Create: `sql/migrations/V92__fast001_device_temporal_assignments.sql`
- Create: `sql/migrations/V93__fast001_device_relationship_location_warranty.sql`
- Create: `sql/migrations/V94__fast001_device_download_grant.sql`
- Modify: `scripts/tests/test_fast001_migration_contract.py`

**V92 tables:**

```text
ast_device_project_relationship
ast_device_project_ancestor
ast_device_customer_relationship
ast_device_assignment_reconciliation
```

**V93 tables:**

```text
ast_device_assembly
ast_device_relationship
ast_device_location
ast_device_warranty
ast_device_warranty_record
```

**V94 table:**

```text
ast_device_download_grant
```

- [ ] **Step 1: 扩展迁移失败测试**

断言不存在 `_current` 和 `_history` 表；当前关系使用生成列唯一约束；装配使用 `parent_device_sn/child_device_sn`；维保只使用 `warranty_*`；下载授权包含 token 摘要、tenant/user/device/log、过期和消费时间，不保存明文 token。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
python -m unittest scripts.tests.test_fast001_migration_contract
```

Expected：FAIL，V92～V94 不存在。

- [ ] **Step 3: 实现 V92～V94**

时态表保留有效区间和来源；装配当前父节点唯一；下载授权使用 token SHA-256 摘要，默认五分钟，一次消费，撤权通过下载时实时设备/文件权限检查实现。

- [ ] **Step 4: 运行迁移测试**

Run:

```powershell
python -m unittest scripts.tests.test_fast001_migration_contract
```

Expected：PASS。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`feat(asset): 建立设备时态关系与下载授权`

***

### Task 5: 前向迁移代码仓明确存在的旧设备字段

**Files:**

- Create: `sql/migrations/V95__fast001_legacy_equipment_forward_migration.sql`
- Modify: `scripts/tests/test_fast001_migration_contract.py`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/migration/DeviceForwardMigrationMySqlTest.java`

**Frozen mapping:**

```text
pms_equipment.id                         -> ast_device.id
pms_equipment.serial_number              -> ast_device.sn
pms_equipment.name                       -> ast_device.name
pms_equipment.model                      -> ast_device.product_model
pms_equipment.project_id                 -> ast_device.project_id + project relationship
pms_equipment.customer_id                -> ast_device.customer_id + customer relationship
pms_equipment.site_id                    -> ast_device.site_id
pms_equipment.site_location_id           -> ast_device.site_location_id
pms_equipment.location_resolution_status -> ast_device.location_resolution_status
pms_equipment.location                   -> ast_device.location_snapshot
pms_equipment.warranty_start_date        -> ast_device.warranty_start_date
pms_equipment.warranty_end_date          -> ast_device.warranty_end_date
pms_equipment.status 0/1/2/3/4           -> IN_STOCK/IN_USE/FAULT/REPAIRING/RETIRED
```

- [ ] **Step 1: 编写前向迁移失败测试**

断言原 ID、租户、SN 和明确字段映射；不确定字段为空；项目/客户关系按迁移时点生成；`NOT EXISTS` 保证幂等；不修改 V6、不创建触发器、不迁移不存在的发货或软件版本来源。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
python -m unittest scripts.tests.test_fast001_migration_contract
```

Expected：FAIL，V95 不存在。

- [ ] **Step 3: 实现 V95**

先迁主档，再迁项目、客户、位置和维保明确事实；来源标记 `LEGACY_PMS`；无法确定的来源键、产品编码、发货和版本字段保持空；冲突插入核对表，不覆盖目标记录。

- [ ] **Step 4: 运行真实 MySQL 测试**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceForwardMigrationMySqlTest test
```

Expected：PASS，迁移前后 ID 和数量一致，幂等重跑不新增目标记录。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`feat(asset): 前向迁移旧设备主档`

***

### Task 6: 实现设备身份、来源应用和冲突闭环

**Files:**

- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/domain/device/DeviceIdentityRules.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/device/DeviceMasterDataService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/device/command/ApplyDeviceMasterDataCommand.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/device/command/CreateManualDeviceCommand.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/device/DeviceDO.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/device/DeviceMapper.java`
- Create: `pms-module-asset/src/main/resources/mapper/device/DeviceCommandMapper.xml`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/domain/device/DeviceIdentityRulesTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/device/DeviceMasterDataServiceTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/device/DeviceIdentityMySqlTest.java`
- [ ] **Step 1: 编写身份失败测试**

覆盖 SN 缺失、同租户重复、跨租户相同 SN、软删除不释放、ID/SN 不可变、同来源同版本同摘要幂等、同版本不同摘要冲突、人工补录必须有原因和待对账。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceIdentityRulesTest,DeviceMasterDataServiceTest test
```

Expected：FAIL。

- [ ] **Step 3: 实现最小领域规则和应用服务**

来源应用只写来源 Owner 字段，人工补录只写平台允许字段；冲突创建核对项并返回稳定错误，不覆盖既有设备；不实现外部网络同步。

- [ ] **Step 4: 运行测试和真实唯一约束测试**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceIdentityRulesTest,DeviceMasterDataServiceTest,DeviceIdentityMySqlTest test
```

Expected：PASS。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`feat(asset): 实现设备身份与来源冲突治理`

***

### Task 7: 实现轻量列表和固定六类详情外壳

**Files:**

- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/device/query/VisibleDevicePageQuery.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/device/projection/DeviceListProjection.java`
- Create: `pms-module-asset/src/main/resources/mapper/device/DeviceQueryMapper.xml`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/device/DeviceQueryService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/device/DeviceDetailService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/device/DeviceQueryApiImpl.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/device/DeviceController.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/device/vo/DevicePageReqVO.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/device/vo/DeviceListRespVO.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/device/vo/DeviceDetailRespVO.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/device/vo/DeviceSourceSliceRespVO.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/dal/DeviceListQueryContractTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/controller/admin/device/DeviceControllerContractTest.java`
- [ ] **Step 1: 编写列表和 HTTP 失败测试**

断言管理端路径 `/pms/asset/devices`，网关后正式语义为 `/api/v1/pms/devices`；列表返回 SN、产品、发货四字段、当前项目/客户、维保和 CONP 四字段；六切片统一包含来源字段；空权限范围返回空结果。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceListQueryContractTest,DeviceControllerContractTest test
```

Expected：FAIL。

- [ ] **Step 3: 实现轻量查询和详情外壳**

列表使用显式 XML 投影。出厂、官网、在网、公告、维保和配置 Log 六切片均返回固定来源外壳；尚未接入的切片返回 `NOT_AVAILABLE`，不伪造数据。

- [ ] **Step 4: 运行查询门禁和 Controller 测试**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceListQueryContractTest,DeviceControllerContractTest test
python -m unittest scripts.tests.test_fast001_query_contract scripts.tests.test_fast001_owner_boundary
```

Expected：PASS。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`feat(asset): 提供设备列表与详情外壳`

***

### Task 8: 实现发货事实、软件版本、CONP 匹配和来源降级

**Files:**

- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/shipment/DeviceShipmentService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/shipment/command/ApplyDeviceShipmentCommand.java`
- Create: `pms-module-asset/src/main/resources/mapper/shipment/DeviceShipmentMapper.xml`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/domain/version/SoftwareVersion.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/domain/version/FactorySoftwareVersion.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/domain/version/OfficialSoftwareVersion.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/domain/version/NetworkSoftwareVersion.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/domain/version/TargetSoftwareVersion.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/domain/version/TechnicalNoticeMatcher.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/version/DeviceNetworkVersionService.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/shipment/DeviceShipmentServiceTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/domain/version/TechnicalNoticeMatcherTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/version/DeviceNetworkVersionServiceTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/device/DeviceSourceFallbackTest.java`
- [ ] **Step 1: 编写发货和版本失败测试**

覆盖发货四字段同源、迟到旧记录不回退、作废当前记录后重选、重复来源事件幂等；覆盖完整版本组合、`boot/conboot -> bootVersion`、原始 CONP 不被解析覆盖、type/series/mark 精确和范围匹配、信息不足返回 `UNDETERMINED`。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceShipmentServiceTest,TechnicalNoticeMatcherTest,DeviceNetworkVersionServiceTest test
```

Expected：FAIL。

- [ ] **Step 3: 实现完整事实和主档投影**

发货写入与主档四字段更新同事务；在网版本写入完整组合并同源更新主档 CONP 四字段；KNO 官网/公告只读副本无业务写入口；来源失败保留最近成功事实。

- [ ] **Step 4: 运行服务和投影测试**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceShipmentServiceTest,TechnicalNoticeMatcherTest,DeviceNetworkVersionServiceTest,DeviceSourceFallbackTest test
```

Expected：PASS。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`feat(asset): 实现设备发货与软件版本事实`

***

### Task 9: 实现项目和客户时态归属及祖先投影

**Files:**

- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/assignment/DeviceProjectAssignmentService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/assignment/DeviceCustomerAssignmentService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/assignment/DeviceAncestorProjectionService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/assignment/command/AssignDeviceProjectCommand.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/assignment/command/AssignDeviceCustomerCommand.java`
- Create: `pms-module-asset/src/main/resources/mapper/assignment/DeviceAssignmentMapper.xml`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/device/DeviceController.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/assignment/DeviceProjectAssignmentServiceTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/assignment/DeviceCustomerAssignmentServiceTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/assignment/DeviceAncestorProjectionServiceTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/assignment/DeviceAssignmentConcurrencyMySqlTest.java`
- [ ] **Step 1: 编写归属失败测试**

覆盖项目守卫拒绝、客户不存在/停用、关闭旧区间和新增新区间、主档投影更新、项目客户不一致核对、同键同摘要重放、同键不同摘要冲突、旧 If-Match、跨租户和越权无副作用。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceProjectAssignmentServiceTest,DeviceCustomerAssignmentServiceTest test
```

Expected：FAIL。

- [ ] **Step 3: 实现 CAS、幂等、审计和 Outbox**

使用 `ProjectDeviceAssignmentGuardApi`、`CustomerQueryApi` 和 `PlatformCommandExecutionApi`。同一事务关闭旧关系、插入新关系、CAS 更新主档、写核对项、审计和 `DeviceAssigned` Outbox。

- [ ] **Step 4: 实现祖先投影消费者和水位**

按 `eventId` 幂等消费 `DeviceAssigned`；保存 `treeVersion/assignmentVersion/operationId`；投影失败不回滚归属真值，允许同 operation 重试；详情读取当前真值，统计返回水位。

- [ ] **Step 5: 运行单元与并发测试**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceProjectAssignmentServiceTest,DeviceCustomerAssignmentServiceTest,DeviceAncestorProjectionServiceTest,DeviceAssignmentConcurrencyMySqlTest test
```

Expected：PASS，并发最多一个成功。

- [ ] **Step 6: 逻辑提交分组**

建议提交信息：`feat(asset): 实现设备时态归属`

***

### Task 10: 实现装配、位置、维保和投影对账

**Files:**

- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/domain/assembly/DeviceAssemblyRules.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/assembly/DeviceAssemblyService.java`
- Create: `pms-module-asset/src/main/resources/mapper/assembly/DeviceAssemblyMapper.xml`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/location/DeviceLocationEffectiveService.java`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/equipment/EquipmentLocationEffectiveService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/warranty/DeviceWarrantyQueryService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/projection/DeviceProjectionReconciliationService.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/domain/assembly/DeviceAssemblyRulesTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/location/DeviceLocationEffectiveServiceTest.java`
- Modify: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/equipment/EquipmentLocationEffectiveServiceTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/warranty/DeviceWarrantyQueryServiceTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/projection/DeviceProjectionReconciliationServiceTest.java`
- [ ] **Step 1: 编写失败测试**

覆盖自引用、跨租户、多当前父、间接环、拔出和替换历史；位置命令幂等和 CAS；维保/续保分页；发货、项目、客户、位置、维保和 CONP 投影漂移检测与重建。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceAssemblyRulesTest,DeviceLocationEffectiveServiceTest,DeviceWarrantyQueryServiceTest,DeviceProjectionReconciliationServiceTest test
```

Expected：FAIL。

- [ ] **Step 3: 实现装配、位置和维保**

装配递归查询检测环；`AssetLocationApi.effectEquipmentLocation` 签名不变，内部切换新位置事实和主档投影；维保查询返回当前事实和记录分页，不创建续保经营对象。

- [ ] **Step 4: 实现幂等对账和重建**

重建只更新主档投影并记录核对结果，不改写完整事实；无来源记录或同源字段不一致生成可审计异常。

- [ ] **Step 5: 运行测试**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceAssemblyRulesTest,DeviceLocationEffectiveServiceTest,DeviceWarrantyQueryServiceTest,DeviceProjectionReconciliationServiceTest,EquipmentLocationEffectiveServiceTest test
```

Expected：PASS。

- [ ] **Step 6: 逻辑提交分组**

建议提交信息：`feat(asset): 实现设备装配位置与维保`

***

### Task 11: 实现配置 Log 元数据和用户绑定安全下载

**Files:**

- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceConfigurationLogQueryService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceConfigurationLogDownloadService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceConfigurationFileContentClient.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/configurationlog/DeviceDownloadGrantDO.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/configurationlog/DeviceDownloadGrantMapper.java`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/device/DeviceController.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceConfigurationLogQueryServiceTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/configurationlog/DeviceConfigurationLogDownloadServiceTest.java`

**基础框架门禁：** 本任务不得修改 `yudao-module-infra`。内容读取优先在 AST 内部通过现有 `FileApi.presignGetUrl` 获得仅供服务端使用的短时地址，再由 `DeviceConfigurationFileContentClient` 使用 JDK HTTP 客户端读取并直接返回响应流，短时地址不得返回浏览器、写库或记录日志。若真实文件客户端无法支持该路径，停止 Task 11，提交独立基础框架 API 扩展方案并等待用户明确批准。

- [ ] **Step 1: 编写下载失败测试**

覆盖设备不可见、缺少下载权限、日志不属于设备、文件不存在、token 过期、其他用户转发、重复消费、权限撤销；断言元数据不包含 `configContent` 和持久 URL。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceConfigurationLogQueryServiceTest,DeviceConfigurationLogDownloadServiceTest test
```

Expected：FAIL。

- [ ] **Step 3: 使用现有公开 API 实现一次性授权**

生成随机 token，只持久化 SHA-256 摘要；授权绑定租户、用户、设备和日志，默认五分钟。下载请求重新校验设备与文件权限，以 CAS 标记消费；服务端使用现有 `FileApi.presignGetUrl` 取得不超过一分钟的内部地址，由 `DeviceConfigurationFileContentClient` 限制协议、响应大小和超时后读取内容并返回，不向客户端暴露对象存储 URL。

- [ ] **Step 4: 运行安全下载测试**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=DeviceConfigurationLogQueryServiceTest,DeviceConfigurationLogDownloadServiceTest test
```

Expected：PASS。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`feat(asset): 增加配置Log安全下载`

***

### Task 12: 建设新设备工作台并固化未实现模块接口降级

**Files:**

- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/asset/device/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceSummaryPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceSourceStatus.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceFactoryPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceOfficialInfoPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceNetworkVersionPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceTechnicalNoticePanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceWarrantyPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceConfigurationLogPanel.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceAssignmentHistoryDrawer.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceCustomerRelationshipDrawer.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceAssemblyTreeDrawer.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceAssignProjectDialog.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceAssignCustomerDialog.vue`
- Create: `scripts/tests/test_fast001_frontend_contract.py`
- [ ] **Step 1: 编写前端失败契约**

断言新客户端使用 `/pms/asset/devices`，归属请求发送 `If-Match` 和 `Idempotency-Key`；固定六 Tab；CONP 四字段；归属历史、客户关系、装配树、维保记录和待核对标识；下载接口不接收持久 URL。MES、KNO 技术公告、EQP-02 配置 Log 文件、CUT 目标版本等当前代码库未实现的 Owner 不得出现本地伪实现或跨模块直读，页面只消费固定来源外壳并将对应切片显示为 `NOT_AVAILABLE`。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
python -m unittest scripts.tests.test_fast001_frontend_contract
```

Expected：FAIL。

- [ ] **Step 3: 实现工作台**

列表先加载轻量投影；详情和抽屉按需加载；已落 AST 本地事实的来源失败保留最近成功值并显示状态；当前未实现 Owner 的切片只渲染接口返回的 `NOT_AVAILABLE`，不新增本地替身数据、伪同步状态或直连其他模块；冲突后刷新服务端真值；无权限不显示操作按钮，但后端仍独立鉴权。

- [ ] **Step 4: 运行前端质量门禁**

Run:

```powershell
python -m unittest scripts.tests.test_fast001_frontend_contract
corepack pnpm --dir yudao-ui/yudao-ui-admin-vue3 exec vue-tsc --noEmit
corepack pnpm --dir yudao-ui/yudao-ui-admin-vue3 lint
corepack pnpm --dir yudao-ui/yudao-ui-admin-vue3 build
```

Expected：PASS。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`feat(asset): 建设设备主档工作台`

***

### Task 13: 撤销普通角色旧写权限并保留超管旧写能力

**Files:**

- Create: `sql/migrations/V96__fast001_device_menu_permissions_and_legacy_access.sql`
- Create: `scripts/tests/test_fast001_legacy_write_permissions.py`
- Test: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/controller/admin/equipment/EquipmentControllerContractTest.java`

**Do not modify:**

- `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/equipment/EquipmentController.java`
- `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/equipment/EquipmentService.java`
- `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/equipment/EquipmentServiceImpl.java`
- `yudao-ui/yudao-ui-admin-vue3/src/api/pms/asset/equipment/index.ts`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/equipment/index.vue`

- [ ] **Step 1: 编写旧写权限失败测试**

断言旧 `/pms/equipment` 的 create/update/delete/status-change HTTP 映射和对应前端写代码仍存在，迁移不得删除、隐藏或改写旧权限菜单；V96 只逻辑撤销 `system_role_menu` 中非 `super_admin` 角色对 `19002/19003/19004/19005` 的授权。断言查询和版本权限 `19001/19006` 不受影响；断言 `PermissionServiceImpl` 的 `super_admin` 全菜单语义保持生效，因此无需向 `system_role_menu` 人工插入超管授权。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
python -m unittest scripts.tests.test_fast001_legacy_write_permissions
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=EquipmentControllerContractTest test
```

Expected：FAIL，V96 和权限契约尚未闭合；旧 Controller 合同保持 PASS。

- [ ] **Step 3: 实现仅权限撤销迁移**

V96 新增设备工作台菜单和归属、冲突、配置 Log 下载等高风险权限。对旧写按钮只更新非 `super_admin` 角色的 `system_role_menu.deleted`；不修改 `system_menu` 的旧写权限项，不删除旧 Controller 映射、Service 方法、前端 API 或页面按钮，不把旧写请求转发到 AST。迁移 SQL 通过 `system_role.code <> 'super_admin'` 限定普通角色，并保持幂等。

- [ ] **Step 4: 运行后端、前端和迁移契约**

Run:

```powershell
python -m unittest scripts.tests.test_fast001_legacy_write_permissions scripts.tests.test_fast001_frontend_contract scripts.tests.test_fast001_migration_contract
mvn.cmd -pl pms-module-asset -am -DskipITs=false -Dtest=EquipmentControllerContractTest test
```

Expected：PASS；普通业务角色不再获得旧写权限，`super_admin` 仍可调用旧写接口，旧查询和详情继续可用。

- [ ] **Step 5: 逻辑提交分组**

建议提交信息：`feat(asset): 撤销普通角色旧设备写权限`

***

### Task 14: 增加受控验收种子和性能数据生成器

**Files:**

- Create: `sql/migrations/V97__fast001_device_acceptance_seed.sql`
- Create: `scripts/generate_fast001_performance_data.py`
- Create: `scripts/verify_fast001_query_plan.py`
- Modify: `scripts/tests/test_fast001_migration_contract.py`
- [ ] **Step 1: 编写种子和性能工具失败测试**

断言 V97 使用 `FAST001_` 来源键、`fast001_seed` creator 和高段 ID，覆盖租户内唯一、跨租户同 SN、发货当前/迟到/停用、项目/客户不一致、位置解析、维保、CONP 精确/范围/未知、装配多层和五种来源状态。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
python -m unittest scripts.tests.test_fast001_migration_contract
```

Expected：FAIL，V97 和工具不存在。

- [ ] **Step 3: 实现幂等验收种子**

不得臆造外部权威值域；外部来源使用 `FAST001_TEST_MES/ITR/KNO` 明确替身；停用记录不参与主档当前投影。

- [ ] **Step 4: 实现可重复性能数据生成器**

工具参数固定支持 `--devices 2000000 --shipments 4000000 --tenant-id 1 --batch-size 10000`，使用确定性 SN 和分布；提供 `--cleanup` 只删除 `FAST001_PERF_` 前缀数据，不影响业务数据。

- [ ] **Step 5: 实现查询计划校验器**

校验 SN 精确查询命中 `uk_ast_device_tenant_sn`，项目/客户分页命中主档组合索引，列表计划不出现发货表；输出 JSON 证据并在关键索引或扫描阈值不满足时非零退出。

- [ ] **Step 6: 运行静态测试**

Run:

```powershell
python -m unittest scripts.tests.test_fast001_migration_contract
```

Expected：PASS。

- [ ] **Step 7: 逻辑提交分组**

建议提交信息：`test(asset): 增加设备验收与性能夹具`

***

### Task 15: 完整验证和真实浏览器验收

**Files:**

- Create: `scripts/tests/run_fast001_browser_acceptance.mjs`
- Create: `sql/migrations/V98__fast001_browser_acceptance_users.sql`
- [ ] **Step 1: 建立固定浏览器夹具**

V98 建立明确测试角色和权限组合，使用现有本地测试账号模式；固定新设备菜单、测试 SN、项目、客户、配置 Log、无权限用户和只读用户。不得包含真实凭据。

- [ ] **Step 2: 运行全部静态契约**

Run:

```powershell
python -m unittest discover -s scripts/tests -p "test_fast001_*.py"
```

Expected：PASS。

- [ ] **Step 3: 执行 Flyway 与后端完整测试**

Run:

```powershell
docker compose run --rm migrate validate
docker compose run --rm migrate migrate
mvn.cmd -pl pms-module-asset,pms-module-project,yudao-module-infra -am -DskipITs=false test
mvn.cmd -pl yudao-server -am -DskipTests package
```

Expected：PASS。

- [ ] **Step 4: 执行前端完整门禁**

Run:

```powershell
corepack pnpm --dir yudao-ui/yudao-ui-admin-vue3 exec vue-tsc --noEmit
corepack pnpm --dir yudao-ui/yudao-ui-admin-vue3 lint
corepack pnpm --dir yudao-ui/yudao-ui-admin-vue3 build
```

Expected：PASS。

- [ ] **Step 5: 执行近生产规模查询计划验证**

Run:

```powershell
python scripts/generate_fast001_performance_data.py --devices 2000000 --shipments 4000000 --tenant-id 1 --batch-size 10000
python scripts/verify_fast001_query_plan.py --tenant-id 1 --output build/fast001-query-plan.json
```

Expected：列表不访问 `ast_device_shipment`；SN 唯一索引、项目和客户主档索引命中；证据文件生成。

- [ ] **Step 6: 启动宿主机应用并执行真实浏览器验收**

基础设施使用 Docker，后端和前端在宿主机运行：

```powershell
mvn.cmd -pl yudao-server -am package -DskipTests
java -jar yudao-server/target/yudao-server.jar --server.port=58080
corepack pnpm --dir yudao-ui/yudao-ui-admin-vue3 dev
node scripts/tests/run_fast001_browser_acceptance.mjs
```

浏览器脚本覆盖：SN 精确查询、轻量列表、六 Tab、来源 `STALE/FAILED/NOT_AVAILABLE`、CONP 组合、项目/客户归属、旧版本冲突、装配树、维保记录、配置 Log 无权限与一次性下载、刷新持久化，以及 320/768/1024/1440 四档响应式；收集控制台和失败网络请求。对当前未实现 Owner 的页面验证仅检查接口级 `NOT_AVAILABLE` 和其他切片不受影响，不启动伪外部服务或把种子声明为同步成功。

另验证旧设备入口：普通业务角色看不到或无权执行旧写操作，`super_admin` 仍能通过旧 `/pms/equipment` 写接口完成受控回归，旧列表和详情均可读取；旧写不得代理到 `ast_device`。

Expected：全部 PASS，无未解释控制台或网络错误；权限差异符合普通角色撤权、超管保留。

- [ ] **Step 7: 清理性能数据并复核工作树**

Run:

```powershell
python scripts/generate_fast001_performance_data.py --cleanup --tenant-id 1
git diff --check
git status --short
```

Expected：只保留 F-AST-001 计划和实施变更；不覆盖既有 `semantic-elements.jsonl` 和其他用户工作树修改；规格仓 PRD/SDS 保持未修改。

- [ ] **Step 8: 逻辑提交分组**

建议提交信息：`test(asset): 完成F-AST-001全链路验收`

***

### Task 16: 补齐 CUS 客户详情的 AST 设备摘要读取并复跑受影响验收

**Files:**

- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/customer/AssetCustomerDeviceSummaryApiImpl.java`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/device/DeviceMapper.java`
- Modify: `pms-module-asset/src/main/resources/mapper/device/DeviceQueryMapper.xml`
- Move: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/equipment/query/CustomerDeviceSummaryPageQuery.java` to `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/device/query/CustomerDeviceSummaryPageQuery.java`
- Modify: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/api/customer/AssetCustomerApiImplTest.java`
- Modify: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/service/query/CustomerDetailServiceTest.java`
- Modify: `scripts/tests/test_fast001_owner_boundary.py`

**Boundary:** 保持 `AssetCustomerDeviceSummaryApi`、`CustomerDeviceSummaryQuery`、`CustomerDeviceSummaryItem` 和 `CustomerDeviceSummarySlice` 公开契约不变。只补齐 CUS 已有 `/customers/{id}/devices` 所需的读取来源；不修改客户主档、客户写入、CRM 同步、客户权限模型或客户详情其他切片。

- [ ] **Step 1: 编写 AST 摘要读取失败测试**

将 `AssetCustomerApiImplTest` 的摘要场景改为 mock `DeviceMapper`，断言按 `tenantId + customerId` 分页读取 `ast_device`，映射现有轻量字段：`deviceId=id`、`deviceCode=sn`、`deviceName=name`、`status=status`。增加空页断言，确保返回 `available=true`、空 items 和 total=0；客户侧详情测试保持仅消费公开接口。

- [ ] **Step 2: 运行测试确认 RED**

Run:

```powershell
mvn.cmd -pl pms-module-asset,pms-module-customer -am -DskipITs=false -Dtest=AssetCustomerApiImplTest,CustomerDetailServiceTest test
python -m unittest scripts.tests.test_fast001_owner_boundary
```

Expected：FAIL，摘要实现仍依赖 `EquipmentMapper` 和 `pms_equipment`。

- [ ] **Step 3: 实现场景化 AST 客户摘要查询**

将 `CustomerDeviceSummaryPageQuery` 迁入 `dal.mysql.device.query`。在 `DeviceMapper` 增加只接收该 Query 对象的分页方法；在 `DeviceQueryMapper.xml` 先按 `ast_device.customer_id` 取得当前直接归属设备，再合并 `ast_device_customer_relationship` 中同租户、同客户、未删除且当前有效的关系设备，按设备 ID 去重后回到 `ast_device` 显式选择 `id/sn/name/status`，按 `id DESC` 分页。空候选集返回空页；不得访问 `pms_equipment`、CUS 业务表或其他模块 Mapper。

- [ ] **Step 4: 切换公开接口实现并删除旧摘要查询依赖**

`AssetCustomerDeviceSummaryApiImpl` 注入 `DeviceMapper`，保持 provider=`AST` 和公开 DTO 字段不变。删除 `EquipmentMapper.selectCustomerSummaryPage` 及其旧 Query import；`AssetCustomerReferenceGuardApiImpl` 的删除守卫不在本补缺范围内，除非失败测试证明它必须同步切换才能避免当前引用漏判，此时只按同一 AST Owner 边界做最小修订并补测试。

- [ ] **Step 5: 运行模块与边界验证**

Run:

```powershell
mvn.cmd -pl pms-module-asset,pms-module-customer -am -DskipITs=false -Dtest=AssetCustomerApiImplTest,CustomerDetailServiceTest,CustomerSummarySliceServiceTest test
python -m unittest scripts.tests.test_fast001_owner_boundary scripts.tests.test_fast001_query_contract
```

Expected：PASS；CUS 客户详情设备摘要来自 AST，空范围返回空结果，公开契约不变，代码和 SQL 不再为该摘要读取 `pms_equipment`。

- [ ] **Step 6: 运行完整验证的补充回归**

在 Task 15 的后端模块测试、Python 静态契约和真实浏览器验收中加入 CUS 客户详情设备摘要场景：当前直接归属命中、有效历史/租用/共管关系命中、重复设备去重、无关系空页、AST 接口异常时 CUS 显示 unavailable 且客户其他切片继续可用。

- [ ] **Step 7: 逻辑提交分组**

建议提交信息：`fix(asset): 补齐客户设备摘要AST读取`

## 4. Requirement 与任务覆盖

| 验收标准                                           | 实施任务与验证                                                        |
| ---------------------------------------------- | -------------------------------------------------------------- |
| `AC-FAST001-001` 设备身份、租户内 SN 永久唯一、不可变和冲突待处理    | Task 3、5、6；`DeviceIdentityRulesTest`、`DeviceIdentityMySqlTest` |
| `AC-FAST001-002` 六类详情、来源元数据、失败隔离和最近成功值         | Task 7、8、11、12、15；Controller、接口级 `NOT_AVAILABLE` 降级和浏览器测试             |
| `AC-FAST001-003` 来源 Owner 只读和人工补录边界            | Task 1、6、8；Owner 静态门禁和主档服务测试                                   |
| `AC-FAST001-004` 轻量列表、发货四字段同源和无 N+1            | Task 3、7、8、14、15；SQL 静态门禁和执行计划校验                               |
| `AC-FAST001-005` 项目唯一当前归属和不重叠时态区间              | Task 4、9；项目归属单元与 MySQL 并发测试                                    |
| `AC-FAST001-006` 跨项目参与、祖先去重和投影水位               | Task 9、12；祖先投影消费者和浏览器统计验证                                      |
| `AC-FAST001-007` 客户唯一当前归属、不一致核对及租用/共管历史        | Task 4、9、12、16；客户归属、关系抽屉和 CUS 设备摘要 AST 读取验证                    |
| `AC-FAST001-008` CAS、越权、跨租户和失败无副作用             | Task 2、9；归属单元与真实 MySQL 并发测试                                    |
| `AC-FAST001-009` 幂等重放、Outbox 和事件重复消费           | Task 2、9；平台命令执行和祖先投影测试                                         |
| `AC-FAST001-010` 统一软件版本实体继承和完整版本组合             | Task 3、8；版本领域测试                                                |
| `AC-FAST001-011` 主档与在网 CONP 四字段同源              | Task 3、8、10；版本服务和投影对账测试                                        |
| `AC-FAST001-012` CONP 主匹配、附加条件和 `UNDETERMINED` | Task 8、14；`TechnicalNoticeMatcherTest` 和受控种子                   |
| `AC-FAST001-013` 任意深度装配、有效区间和环检测               | Task 4、10、12；装配规则和浏览器树验证                                       |
| `AC-FAST001-014` 客观维保投影、续保分页和历史月数映射            | Task 4、5、10、12；迁移、查询和浏览器验证                                     |
| `AC-FAST001-015` 投影幂等对账、重建和可审计异常               | Task 10、14；`DeviceProjectionReconciliationServiceTest`         |
| `AC-FAST001-016` 配置 Log 元数据权限和用户绑定一次性下载        | Task 4、11、12、15；安全下载单元与浏览器负向验证                                 |
| `AC-FAST001-017` 真实 MySQL 与 200 万/400 万执行计划证据  | Task 5、6、9、14、15；MySQL 测试和计划 JSON 证据                           |
| `AC-FAST001-018` 原 ID 前向迁移和旧写访问收敛               | Task 5、13、15；迁移测试、普通角色撤权、`super_admin` 保留及无 AST 代理验证          |
| `AC-FAST001-019` 四档响应式及完整真实浏览器闭环               | Task 12、15；Chrome DevTools/CDP 验收脚本                            |
| `AC-FAST001-020` 不扩大 Feature 完成声明              | Task 1、15；输入门禁和最终完成条件复核                                        |

## 5. 计划完成条件

- 计划中不允许出现未定义的生产类型、测试类或命令。
- 所有跨模块调用只经过公开 `-api`；未实现模块只保留接口级 `NOT_AVAILABLE`，没有伪实现、空模块、本地替代业务表或越权直读。
- 所有新生产行为都有先失败后通过的测试证据。
- V90～V98 在执行前确认未被其他工作占用。
- 旧 `/pms/equipment` 写代码与权限菜单保持存在；普通业务角色旧写授权已撤销，`super_admin` 仍按平台全菜单语义保留旧写能力，且旧写不代理到 AST。
- `AssetCustomerDeviceSummaryApi` 公开契约不变，CUS 客户详情设备摘要已从 `pms_equipment` 切换到 `ast_device`，空范围返回空结果。
- 所有 Java 定向测试、完整模块测试、后端构建、前端类型检查、lint、build、Flyway、真实 MySQL、查询计划和浏览器验收通过。
- F-AST-001 完成不宣称 EQP-02、EQP-03、EQP-04、INT-02、INT-04、Deployment、SIT、UAT 或 Release 完成。
