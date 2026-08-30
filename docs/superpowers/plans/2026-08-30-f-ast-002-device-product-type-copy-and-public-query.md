# F-AST-002 设备产品类型受控副本与公开查询 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在AST内建立可独立验收的设备产品类型受控副本、来源映射、设备当前引用和两个模块内公开查询，使授权消费者能够取得可追溯、可降级且不泄露无权设备事实的产品类型结果。

**Architecture:** 新能力独立落在现有`pms-module-asset-api`与`pms-module-asset`的`producttype`包，不修改旧设备、Equipment、CONP或页面实现。三张AST自有表保存产品类型副本、来源映射与设备当前引用；受控导入通过平台命令执行契约原子完成业务写入、幂等和成功审计，拒绝事实使用通用操作审计。查询入口按ADR-0036由Inspection专用只读接口的AST实现建立固定进程内调用主体，AST校验当前租户、消费者和动作，设备查询再按服务端解析的委托用户范围一次性批量联查；CRM/MES连接器仅保留后续调用边界，不在本Feature实现。

**Tech Stack:** Java 25、Spring Boot 4.1、Spring Security、MyBatis-Plus/XML、MySQL 8.4、Flyway 11、Maven、JUnit 5、Mockito、Docker Compose。

**Locked Inputs:**

- 锁定规格提交：`a52b22b4`
- Requirement：`EQP-01@V1=PARTIAL`
- PRD变更：`CHG-PRD-2026-08-30-010`
- Feature Spec：`specs/features/F-AST-002-device-product-type-copy-and-public-query.md`
- Feature Ready：`READY / GO NPDMS-FAST002-FEATURE-READY-20260830-01`
- 当前Task：`tasks/features/F-AST-002.md`
- 正式SDS：`docs/design/04-module-design.md`、`07-authorization-design.md`、`08-data-model.md`、`09-database-design.md`、`10-api-design.md`、`15-cache-and-concurrency.md`、`16-exception-and-idempotency.md`、`20-test-design.md`
- 查询规范：`docs/coding/database-query-interface.md`

---

## 1. 实施边界

- 只实现`EQP-01@V1`中产品类型受控副本、来源证据、设备当前引用、受控导入、授权查询、降级和历史解释的合法子闭环。
- 公开查询固定为`AssetProductTypeApi.getByCodes(ProductTypeCodesQuery)`和`AssetProductTypeApi.getAuthorizedDeviceProductType(AuthorizedDeviceProductTypeQuery)`；不得增加自由CRUD或HTTP Controller。
- 不实现CRM/MES协议、认证、网络访问、调度、游标、批次拉取、重试、补偿或对账；不宣称`EQP-04`完成。
- 不修改`DeviceDO`、`DeviceMapper`、`DeviceQueryMapper.xml`、`DeviceController`、`EquipmentDO`、`EquipmentMapper`、旧设备接口、旧页面或CONP字段语义。
- 不修改Yudao基础平台。直接复用`PlatformCommandExecutionApi`和`OperationAuditApi`，不因既有通用实现中的措辞问题改动平台模块。
- 不创建产品类型人工维护页面、字典或未经权威来源确认的产品类型种子；只为已批准的受控导入维护入口增加独立功能权限与菜单授权种子，不默认授予任何角色。
- AST内部新增受控导入应用服务和最小管理入口，入口只接受当前已认证用户且要求`pms:asset-product-type:controlled-import`，租户与`actorId`从安全上下文取得；本Feature不提供外部连接器实现。
- Inspection消费边界增加`pms-module-service -> pms-module-asset-api`依赖、专用`InspectionAssetProductTypeApi`消费和架构边界验证；不实现规则发布、工程师选择、`INS-03`或`INS-09`业务。
- 新能力按可独立验证的逻辑单元推进；每个实现Task必须在本Task内完成最小实现、补充该单元定向测试、实际运行并通过后，才允许进入下一Task。
- 不把Task 1至Task 5的测试集中后置到统一测试Task；后续综合测试只补跨单元、真实数据库和消费契约，不替代逐单元验证。
- 每个逻辑单元验证通过后仅按用户明确要求提交；本计划不得自行提交。

## 2. 关键技术裁决

### 2.1 来源版本顺序

`sourceVersion`是来源提供的不透明等值键，不作字符串、数字或语义版本排序。`sourceUpdatedAt`是同一`tenantId + sourceSystem + sourceKey`下唯一用于判定先后的业务水位，必须由受控导入提供且不得为空。

比较规则固定为：

```text
incoming.sourceUpdatedAt < current.sourceUpdatedAt
  -> STALE_SOURCE，拒绝且不改写最近成功副本

incoming.sourceUpdatedAt = current.sourceUpdatedAt
  且 sourceVersion、payloadHash、目标稳定编码均相同
  -> IDEMPOTENT_REPLAY，不重复写

incoming.sourceUpdatedAt = current.sourceUpdatedAt
  但 sourceVersion、payloadHash或目标稳定编码任一不同
  -> SOURCE_CONFLICT，保留当前事实并记录待处理冲突摘要

incoming.sourceUpdatedAt > current.sourceUpdatedAt
  -> 允许作为新来源事实继续校验并原子写入
```

- 不允许`sourceVersion.compareTo(...)`、正则猜测版本格式或按接收顺序覆盖。
- 同一来源时间水位出现不同版本或不同载荷时失败关闭，不能用任意事件键打破平局。
- 后续连接器若提供经正式规格批准的单调序列，须由独立Feature修订SDS和本比较策略，F-AST-002不预埋多策略框架。

### 2.2 服务调用主体与租户

按ADR-0036，两个公开Query只承载业务筛选条件，不携带`tenantId`或`serviceIdentity`。实现必须同时满足：

1. 当前`TenantContextHolder`租户非空；
2. ADR-0036受控进程内调用主体存在，且上下文租户与当前租户一致；
3. 上下文消费者代码在AST私有注册表中存在；
4. 注册主体具备当前动作；
5. 授权设备查询携带由Inspection服务端解析的`subjectUserId`并按其设备范围过滤；缺少或非法委托用户失败关闭，已认证用户的空范围返回空。

动作码固定为：

```text
PRODUCT_TYPE_READ_CODES
DEVICE_PRODUCT_TYPE_READ
PRODUCT_TYPE_CONTROLLED_IMPORT
```

AST私有注册表使用`pms.asset-product-type.trusted-service-principals`配置前缀，默认空映射即不信任任何主体。每个消费者代码映射稳定主体ID和允许动作集合；注册表只负责授权映射，不承担认证。Task 1新增只允许固定Inspection消费者建立的进程内上下文，按栈恢复并默认不向异步线程传播；Task 8由Inspection专用只读适配器使用该入口。该边界只提供模块化单体内误用防护和审计归因，不宣称对同JVM恶意代码提供密码学隔离。

### 2.3 数据与冲突

- `ast_product_type`的`uk(tenant_id, type_code)`不包含`deleted`，稳定编码停用或软删除后仍被占用。
- `ast_product_type_source_mapping`的`uk(tenant_id, source_system, source_key)`固定一个来源键的当前映射记录。冲突时保留已有`product_type_id`，将`mapping_status`置为`CONFLICT`并保存冲突目标编码、冲突来源版本、冲突来源时间和冲突摘要；不得覆盖原目标。
- `ast_device_current_product_type`使用`current_marker=1`表示当前记录，关闭历史时置空；`uk(tenant_id, device_id, current_marker)`保证一个设备至多一个当前引用。
- `ast_product_type_source_mapping -> ast_product_type`、`ast_device_current_product_type -> ast_product_type/source_mapping`建立AST模块内、带租户列的复合外键，并由被引用表的`uk(tenant_id, id)`支撑。
- 现有`ast_device`只有主键`id`和`uk(tenant_id, sn)`，没有可供`(tenant_id, device_id)`引用的候选键；本Feature又明确不修改旧设备Schema，因此`ast_device_current_product_type.device_id`不得声明无效复合外键。设备存在性、同租户和锁定校验由导入事务通过AST自有Mapper完成，并由真实MySQL测试证明；不得为补外键修改旧迁移或给旧表夹带无关结构变更。
- 不得建立AST与Inspection之间的数据库外键。
- 未知、冲突或未解析设备事实允许`product_type_id/product_type_code`为空，并必须保存明确`resolution_status`；不得写`conpType`、旧字典、型号或自由文本推导值。

### 2.4 审计与降级

- 成功受控导入使用`PlatformCommandExecutionApi`，业务写入、幂等完成和成功审计同事务提交。
- 查询拒绝、旧来源、来源冲突和跨租户拒绝在业务写事务结束后使用独立事务记录；现有`OperationAuditApi`自身未声明`REQUIRES_NEW`，不能直接假定它可跨回滚保留。计划必须新增AST侧独立事务边界，再在该边界内调用公开审计API。
- 审计只保存主体ID、动作码、请求数量、编码/设备ID摘要、来源键摘要、稳定失败分类和相关版本水位；不保存完整来源载荷、客户/项目详情、设备敏感详情或Secret。
- `sync_status`使用既有SDS口径：`FRESH`、`STALE`、`FAILED`、`PENDING_MAPPING`、`NOT_AVAILABLE`。
- 来源失败或空响应只能更新同步状态、失败分类和尝试时间；已有显示名称、稳定编码、来源版本、来源成功时间及设备当前引用保持不变。
- `fromLastSuccessfulCopy=true`仅在当前查询结果来自保留副本且`sync_status`不是`FRESH`时返回。

## 3. 依赖图与实施顺序

```text
Task 1 公开API、服务身份与输入守卫 + 本单元测试
  -> Task 2 三表前向Schema与DO + Schema契约测试
  -> Task 3 DAL、锁查询与批量授权查询 + Mapper/范围测试
  -> Task 4 受控导入、来源顺序、冲突与审计 + 事务/幂等测试
  -> Task 5 两个公开查询实现 + API/查询契约测试
  -> Task 6 跨单元定向回归与遗漏补测
  -> Task 7 真实MySQL迁移、幂等与并发验证
  -> Task 8 Inspection消费边界
  -> Task 9 回归、追溯、自审与Implementation Done收口
```

Task 1至Task 5各自在单元内实现并测试，任何单元测试未通过不得继续下一Task。Task 6只做跨单元定向回归和遗漏补测，不承接前序本应完成的单元测试。Task 7关闭真实数据库风险。Task 8只证明跨模块消费边界，不提前实现F-INS-001。Task 9在最终合入状态统一验证。

## 4. 文件职责

| 路径 | 职责 |
|---|---|
| `pms-module-asset/pms-module-asset-api/.../api/producttype` | 两个公开查询及纯DTO，不暴露持久化类型 |
| `pms-module-asset-api/.../api/producttype/inspection` | 唯一供Inspection注入的专用只读接口；不暴露消费者、主体、动作或上下文设置器 |
| `pms-module-asset/.../service/producttype/security` | AST包级上下文栈、私有主体注册表、动作白名单和最终租户/动作校验 |
| `pms-module-asset/.../service/producttype/security/InspectionAssetProductTypeApiImpl` | 专用接口实现，与包级上下文持有器同包，固定Inspection消费者后调用通用AST API |
| `pms-module-asset/.../service/producttype` | 受控导入、来源失败记录、查询编排、审计与结果映射 |
| `pms-module-asset/.../dal/dataobject/producttype` | 三张AST产品类型表DO |
| `pms-module-asset/.../dal/mysql/producttype` | 场景化Query、Projection、Mapper与锁/批量查询声明 |
| `pms-module-asset/.../resources/mapper/producttype` | 动态集合、联表、锁查询和受控状态更新SQL |
| `sql/migrations/V<合入时下一个未占用版本>__fast002_asset_product_type.sql` | 三张表、约束和索引；无产品类型种子 |
| `pms-module-service/pom.xml` | Inspection物理模块仅增加`pms-module-asset-api`依赖 |
| `tasks/features/F-AST-002.md` | Technical Plan与Implementation Done唯一状态记录 |

---

### Task 1: 建立公开API、受信服务身份与输入守卫

**Files:**

- Create: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/producttype/AssetProductTypeApi.java`
- Create: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/producttype/dto/ProductTypeCodesQuery.java`
- Create: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/producttype/dto/ProductTypeCodeResult.java`
- Create: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/producttype/dto/AuthorizedDeviceProductTypeQuery.java`
- Create: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/producttype/dto/AuthorizedDeviceProductTypeResult.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/security/AssetProductTypeActionCodes.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/security/AssetProductTypeCaller.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/security/AssetProductTypeCallerContext.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/security/AssetProductTypeServicePrincipalProperties.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/security/TrustedAssetProductTypeServicePrincipalRegistry.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/security/AssetProductTypeRequestGuard.java`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/enums/ErrorCodeConstants.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/api/producttype/AssetProductTypeApiContractTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/producttype/security/AssetProductTypeCallerContextTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/producttype/security/AssetProductTypeRequestGuardTest.java`

- [ ] **Step 1: 创建纯Java公开契约**

固定接口：

```java
public interface AssetProductTypeApi {

    List<ProductTypeCodeResult> getByCodes(ProductTypeCodesQuery query);

    List<AuthorizedDeviceProductTypeResult> getAuthorizedDeviceProductType(
            AuthorizedDeviceProductTypeQuery query);
}
```

固定Query：

```java
public record ProductTypeCodesQuery(
        List<String> productTypeCodes) {
}
```

```java
public record AuthorizedDeviceProductTypeQuery(
        Long subjectUserId,
        List<Long> deviceIds) {
}
```

固定Result：

```java
public record ProductTypeCodeResult(
        String productTypeCode,
        boolean exists,
        boolean enabled,
        String displayName,
        String sourceSystem,
        String sourceVersion,
        String syncStatus,
        LocalDateTime lastSuccessfulSyncTime,
        boolean fromLastSuccessfulCopy) {
}
```

```java
public record AuthorizedDeviceProductTypeResult(
        Long deviceId,
        String productTypeCode,
        String displayName,
        boolean enabled,
        String sourceVersion,
        String resolutionStatus,
        String syncStatus,
        LocalDateTime lastSuccessfulSyncTime,
        boolean fromLastSuccessfulCopy) {
}
```

Query构造时复制集合，防止调用后修改；空集合由实现返回空。编码规范化只允许`trim`、去除空值和按首次出现去重，不改变大小写，不猜测别名。

- [ ] **Step 2: 建立动作受限服务主体注册表**

注册表值对象固定为：

```java
public record TrustedPrincipal(Long principalId, Set<String> allowedActions) {
}
```

`resolve(consumerCode, actionCode)`返回注册表中的稳定主体ID，并拒绝空消费者、未注册消费者、非正主体ID、未知动作和未授权动作。`principalId`只能由AST注册表解析，不进入Query、调用上下文或调用参数。注册表为AST包级非public组件，配置绑定完成后使用不可变映射且不暴露运行期替换入口；配置默认空，不在模板中写入真实主体或Secret。Inspection注册项只能包含两个只读动作，不得包含`PRODUCT_TYPE_CONTROLLED_IMPORT`。注册表只做授权映射，调用主体可信性来自后续专用适配器固定建立的上下文。

- [ ] **Step 3: 建立受控调用上下文与请求守卫**

`AssetProductTypeCaller`是AST业务模块内的包级record，只保存`consumerCode`和`tenantId`；`AssetProductTypeCallerContext`同样为包级final类，使用普通`ThreadLocal<Deque<AssetProductTypeCaller>>`，只向同包专用适配器实现提供包级`callAsInspection(Supplier<T>)`，固定消费者`INSPECTION`并从`TenantContextHolder`读取当前租户。缺失租户拒绝，`finally`弹栈，空栈删除ThreadLocal；不使用`TransmittableThreadLocal`，因此异步线程默认不继承。API模块不包含上下文类型、主体设置器或通用`runAs`入口。

`AssetProductTypeRequestGuard`负责：

```text
requireTrustedPrincipal(actionCode)
requireSubjectUser(subjectUserId)
```

`requireTrustedPrincipal`必须读取当前租户和调用上下文并校验租户、消费者、主体与动作，不接受Query覆盖。编码查询的跨租户主体拒绝并记录安全审计；设备查询在后续实现中把跨租户或不可见设备映射为空结果。设备查询缺少或非法`subjectUserId`时失败关闭；只有合法委托用户的数据范围为空时返回空，不把服务主体当作工程师数据范围。查询执行时先由`DeviceAccessScopeService.visibleProjectIds`取得项目范围，再按现有设备可见条件关联当前直接项目或有效项目关系；不得将`visibleProjectIds`误当作仅匹配`ast_device.project_id`。

- [ ] **Step 4: 分配AST产品类型错误码**

在现有AST错误码之后使用独立`1_015_005_xxx`段，至少覆盖：

```text
AST_PRODUCT_TYPE_INVALID_REQUEST
AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED
AST_PRODUCT_TYPE_SOURCE_STALE
AST_PRODUCT_TYPE_SOURCE_CONFLICT
AST_PRODUCT_TYPE_CODE_CONFLICT
AST_PRODUCT_TYPE_CROSS_TENANT_REFERENCE
AST_PRODUCT_TYPE_IDEMPOTENCY_CONFLICT
```

不修改既有错误码含义。

- [ ] **Step 5: 补充并运行本单元测试**

覆盖接口仅有两个规格方法、DTO不泄露持久化类型且Query不存在`tenantId/serviceIdentity`、Query空集合归一化、动作白名单、缺少/错误调用主体、未知/空动作、主体动作不匹配、租户缺失或不一致、非法委托用户、空设备输入、嵌套上下文恢复、调用结束清理和异步线程默认不传播；同时以反射/源码边界断言API模块不存在public主体设置器或通用`runAs`，上下文与注册表均为AST包级非public类型。合法委托用户设备范围为空返回空依赖Task 3/5的批量范围与查询实现，在对应Task验证，不提前宣称由Task 1关闭。

Run:

```powershell
mvn.cmd -pl pms-module-asset/pms-module-asset-api,pms-module-asset -am "-DskipITs=true" "-Dtest=AssetProductTypeApiContractTest,AssetProductTypeCallerContextTest,AssetProductTypeRequestGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected：三个测试类实际执行且PASS。失败时停留在Task 1整改，不进入Task 2。

- [ ] **Step 6: 编译公开契约和AST模块**

Run:

```powershell
mvn.cmd -pl pms-module-asset/pms-module-asset-api,pms-module-asset -am -DskipTests compile
```

Expected：Reactor成功，公开DTO不需要新增API模块依赖，Yudao基础模块无变更。

- [ ] **Step 7: 提交逻辑单元**

```powershell
git add pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/producttype pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/security pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/enums/ErrorCodeConstants.java
git commit -m "feat(asset): add product type public contract"
```

---

### Task 2: 创建三表前向Schema与数据对象

**Files:**

- Create: `sql/migrations/V<合入时下一个未占用版本>__fast002_asset_product_type.sql`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/producttype/AssetProductTypeDO.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/producttype/AssetProductTypeSourceMappingDO.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/producttype/DeviceCurrentProductTypeDO.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/dal/AssetProductTypeSchemaContractTest.java`

- [ ] **Step 1: 合入前串行确定Flyway版本**

执行数值版本扫描：

```powershell
Get-ChildItem sql/migrations -Filter 'V*.sql' | Sort-Object {[int]($_.BaseName -replace '^V(\d+).*','$1')} | Select-Object -Last 20 -ExpandProperty Name
```

首次实施时工作树最高版本为V131，原迁移使用V132；固定权威测试库随后证明V132～V145已被其他串行Feature实际执行，因此本Task按下一个空闲版本改为`V146__fast002_asset_product_type.sql`并同步Schema契约测试。禁止修改已执行迁移。

- [ ] **Step 2: 创建`ast_product_type`**

列至少包含：

```text
id bigint
tenant_id bigint
type_code varchar(64)
display_name varchar(128)
enabled bit
source_system varchar(32)
source_key varchar(128)
source_version varchar(128)
source_updated_at datetime(3)
payload_hash char(64)
sync_status varchar(32)
last_sync_attempt_at datetime(3)
synced_at datetime(3)
version int
creator/updater varchar(64)
create_time/update_time datetime(3)
deleted bit
```

约束：

```text
PRIMARY KEY(id)
UNIQUE KEY uk_ast_product_type_tenant_code(tenant_id, type_code)
UNIQUE KEY uk_ast_product_type_tenant_id(tenant_id, id)
CHECK(type_code <> '')
CHECK(display_name <> '')
CHECK(sync_status IN ('FRESH','STALE','FAILED','PENDING_MAPPING','NOT_AVAILABLE'))
```

不创建产品类型业务值种子。

- [ ] **Step 3: 创建`ast_product_type_source_mapping`**

列至少包含：

```text
id bigint AUTO_INCREMENT
tenant_id bigint
source_system varchar(32)
source_key varchar(128)
source_version varchar(128)
source_updated_at datetime(3)
payload_hash char(64)
product_type_id bigint null
mapping_status varchar(32)
conflict_product_type_code varchar(64) null
conflict_source_version varchar(128) null
conflict_source_updated_at datetime(3) null
conflict_payload_hash char(64) null
synced_at datetime(3) null
version int
creator/updater varchar(64)
create_time/update_time datetime(3)
deleted bit
```

约束：

```text
UNIQUE KEY uk_ast_product_type_mapping_source(tenant_id, source_system, source_key)
UNIQUE KEY uk_ast_product_type_mapping_tenant_id(tenant_id, id)
FOREIGN KEY(tenant_id, product_type_id) REFERENCES ast_product_type(tenant_id, id)
CHECK(mapping_status IN ('RESOLVED','CONFLICT','UNRESOLVED'))
CHECK((mapping_status = 'RESOLVED' AND product_type_id IS NOT NULL) OR (mapping_status = 'UNRESOLVED' AND product_type_id IS NULL) OR mapping_status = 'CONFLICT')
CHECK((mapping_status = 'CONFLICT' AND conflict_product_type_code IS NOT NULL AND conflict_source_version IS NOT NULL AND conflict_source_updated_at IS NOT NULL AND conflict_payload_hash IS NOT NULL) OR (mapping_status <> 'CONFLICT' AND conflict_product_type_code IS NULL AND conflict_source_version IS NULL AND conflict_source_updated_at IS NULL AND conflict_payload_hash IS NULL))
```

`deleted`不参与来源唯一键，软删除不释放来源键。

- [ ] **Step 4: 创建`ast_device_current_product_type`**

列至少包含：

```text
id bigint
tenant_id bigint
device_id bigint
product_type_id bigint null
product_type_code varchar(64) null
source_mapping_id bigint null
resolution_status varchar(32)
source_version varchar(128)
source_updated_at datetime(3)
effective_from datetime(3)
effective_to datetime(3) null
current_marker tinyint null
version int
creator/updater varchar(64)
create_time/update_time datetime(3)
deleted bit
```

约束：

```text
UNIQUE KEY uk_ast_device_product_type_current(tenant_id, device_id, current_marker)
UNIQUE KEY uk_ast_device_product_type_tenant_id(tenant_id, id)
-- device_id不声明数据库外键：现有ast_device不存在(tenant_id,id)候选键，导入事务必须按tenant_id+id锁定校验
FOREIGN KEY(tenant_id, product_type_id, product_type_code) REFERENCES ast_product_type(tenant_id, id, type_code)
FOREIGN KEY(tenant_id, source_mapping_id) REFERENCES ast_product_type_source_mapping(tenant_id, id)
FOREIGN KEY(tenant_id, source_mapping_id, product_type_id) REFERENCES ast_product_type_source_mapping(tenant_id, id, product_type_id)
CHECK(resolution_status IN ('RESOLVED','UNKNOWN','CONFLICT','UNRESOLVED'))
CHECK(effective_to IS NULL OR effective_to >= effective_from)
CHECK((resolution_status = 'RESOLVED' AND product_type_id IS NOT NULL AND product_type_code IS NOT NULL AND source_mapping_id IS NOT NULL) OR (resolution_status <> 'RESOLVED' AND product_type_id IS NULL AND product_type_code IS NULL))
```

迁移不回填`DeviceDO.conpType`或其他旧字段，不扫描附件猜测映射。不得为`device_id`外键修改既有`ast_device`迁移或追加与本Feature无关的候选键；设备引用完整性由同事务锁定校验、租户条件和测试保证。

- [ ] **Step 5: 创建三个Tenant DO**

三个DO继承`TenantBaseDO`，使用`@TableName`、`@TableId`和`@Version`，字段与迁移一一对应。`current_marker`是数据库生成列，DO仅以`@TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)`只读映射；不得把DO放入API模块或公开DTO。

- [ ] **Step 6: 补充并运行Schema契约测试**

断言三表、自增技术主键、唯一约束、AST内部有效复合外键、`device_id`无无效外键、`synced_at`统一命名、来源水位、映射状态空值约束、生成式当前标记、解析状态引用约束、冲突摘要和无业务种子；同时核对现有`ast_device`未被本Feature修改，并验证三个DO的Tenant继承、表名、主键、乐观锁及生成列只读映射。

Run:

```powershell
mvn.cmd -pl pms-module-asset -am "-DskipITs=true" "-Dtest=AssetProductTypeSchemaContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected：Schema契约测试实际执行且PASS。失败时停留在Task 2整改。

- [ ] **Step 7: 编译AST模块**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipTests compile
```

Expected：三个DO完成MyBatis映射，既有设备DO无变化。

- [ ] **Step 8: 提交逻辑单元**

```powershell
git add sql/migrations/V*__fast002_asset_product_type.sql pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/producttype
git commit -m "feat(asset): add product type controlled copy schema"
```

---

### Task 3: 实现DAL、锁查询与批量授权查询

**Files:**

- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/producttype/AssetProductTypeMapper.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/producttype/AssetProductTypeSourceMappingMapper.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/producttype/DeviceCurrentProductTypeMapper.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/producttype/query/ProductTypesByCodesQuery.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/producttype/query/ProductTypeSourceMappingLockQuery.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/producttype/query/AuthorizedDeviceProductTypesQuery.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/producttype/projection/AuthorizedDeviceProductTypeProjection.java`
- Create: `pms-module-asset/src/main/resources/mapper/producttype/AssetProductTypeMapper.xml`
- Create: `pms-module-asset/src/main/resources/mapper/producttype/AssetProductTypeSourceMappingMapper.xml`
- Create: `pms-module-asset/src/main/resources/mapper/producttype/DeviceCurrentProductTypeMapper.xml`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/dal/producttype/DeviceCurrentProductTypeMapperTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/dal/producttype/DeviceCurrentProductTypeMapperMySqlTest.java`

- [ ] **Step 1: 实现场景化Query**

```java
public record ProductTypesByCodesQuery(Long tenantId, Set<String> productTypeCodes) {
}
```

```java
public record ProductTypeSourceMappingLockQuery(
        Long tenantId,
        String sourceSystem,
        String sourceKey) {
}
```

```java
public record AuthorizedDeviceProductTypesQuery(
        Long tenantId,
        Set<Long> deviceIds,
        Set<Long> visibleProjectIds,
        LocalDateTime effectiveAt) {
}
```

集合必须由Service规范化并复制。空集合在Mapper入口直接返回空，不能省略条件后执行全量查询。

- [ ] **Step 2: 实现稳定唯一键与锁查询**

Mapper默认方法只用于主键或稳定复合唯一键的类型安全查询。来源映射`FOR UPDATE`、动态集合和联表全部进入XML，不使用SQL注解、`${}`、`.last(...)`或Service拼SQL。

锁顺序固定为：

```text
source mapping -> product type -> device current product type
```

并发导入必须按相同顺序取锁，避免反向锁序。

- [ ] **Step 3: 实现按编码批量查询**

`AssetProductTypeMapper.selectByCodes(ProductTypesByCodesQuery)`只访问`ast_product_type`，按租户和请求编码集合返回当前副本。Service负责补齐未知编码结果并保持请求首次出现顺序。

- [ ] **Step 4: 实现授权设备一次性联查**

`DeviceCurrentProductTypeMapper.selectAuthorizedCurrent(AuthorizedDeviceProductTypesQuery)`在一个XML查询中联接：

```text
ast_device
ast_device_current_product_type
ast_product_type
```

过滤必须同时包含：

```text
d.tenant_id = query.tenantId
d.id IN query.deviceIds
(d.project_id IN query.visibleProjectIds
 OR EXISTS 当前有效ast_device_project_relationship落入query.visibleProjectIds)
current.current_marker = 1
```

使用`LEFT JOIN`保留可见设备的`UNKNOWN/CONFLICT/UNRESOLVED`结果；设备可见性必须与现有`DeviceQueryMapper.visibleDeviceConditions`一致，同时覆盖`ast_device.project_id`当前投影和`ast_device_project_relationship`在`effectiveAt`时点有效的关系，不得只看当前投影字段。跨租户和不可见设备无结果。不得循环调用`DeviceAccessScopeService.assertVisible`形成N+1。

- [ ] **Step 5: 补充并运行Mapper与关联可见范围测试**

覆盖空`deviceIds`、空`visibleProjectIds`、当前`project_id`可见、仅有效`ast_device_project_relationship`可见、过期/未来关系不可见、跨租户不可见，以及UNKNOWN/CONFLICT/UNRESOLVED可见设备仍返回明确状态。

Run:

```powershell
mvn.cmd -pl pms-module-asset -am "-DskipITs=true" "-Dtest=DeviceCurrentProductTypeMapperTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected：Mapper测试实际执行且PASS。失败时停留在Task 3整改。

- [ ] **Step 6: 编译Mapper和XML**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipTests compile
```

Expected：MyBatis Mapper/XML加载成功；新增SQL只访问AST自有表。

- [ ] **Step 7: 提交逻辑单元**

```powershell
git add pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/producttype pms-module-asset/src/main/resources/mapper/producttype
git commit -m "feat(asset): add product type query persistence"
```

---

### Task 4: 实现受控导入、来源失败、冲突与审计

**Files:**

- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/command/ImportAssetProductTypeCommand.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/command/DeviceCurrentProductTypeInput.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/command/RecordAssetProductTypeSourceFailureCommand.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/command/ImportAssetProductTypeResult.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/AssetProductTypeImportService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/AssetProductTypeSourceOrder.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/AssetProductTypeAuditService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/AssetProductTypeConflictRecordService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/producttype/AssetProductTypeImportController.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/producttype/vo/ImportAssetProductTypeReqVO.java`
- Create: `sql/migrations/V<合入时下一个未占用版本>__fast002_product_type_import_permission.sql`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/producttype/AssetProductTypeSourceOrderTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/producttype/AssetProductTypeImportServiceTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/controller/admin/producttype/AssetProductTypeImportControllerTest.java`

- [ ] **Step 1: 定义受控导入命令**

命令只承载来源和业务载荷；`tenantId`和`actorId`从当前已认证用户安全上下文取得，不接受命令覆盖。Inspection专用只读适配器不开放本动作；连接器Feature未批准前，Task 4只允许要求独立`pms:asset-product-type:controlled-import`权限的AST管理入口调用。

```java
public record ImportAssetProductTypeCommand(
        String operationId,
        String idempotencyKey,
        String productTypeCode,
        String displayName,
        boolean enabled,
        String sourceSystem,
        String sourceKey,
        String sourceVersion,
        LocalDateTime sourceUpdatedAt,
        String payloadHash,
        List<DeviceCurrentProductTypeInput> devices) {
}
```

`payloadHash`由受控调用方对规范化业务载荷计算，AST校验为64位十六进制但不保存完整载荷。设备输入只包含`deviceId`、`resolutionStatus`和必要来源引用，不接受猜测名称或自由产品类型值。

- [ ] **Step 2: 建立受控导入维护入口与独立权限**

`AssetProductTypeImportController`只提供受控导入命令入口，使用`@PreAuthorize("@ss.hasPermission('pms:asset-product-type:controlled-import')")`。`AssetProductTypeImportService`的public应用方法必须再次调用`SecurityFrameworkService.hasPermission("pms:asset-product-type:controlled-import")`，并读取`SecurityFrameworkUtils.getLoginUserId()`和`TenantContextHolder.getRequiredTenantId()`；任一缺失或无权均在进入Mapper与平台幂等执行器前拒绝，使进程内直接注入Service也不能绕过。ReqVO不包含租户、actor、serviceIdentity或任意动作字段。前向迁移只登记独立权限菜单项，不绑定任何角色，不复用`pms:equipment:update`或普通设备维护权限；不得创建产品类型自由CRUD页面。

- [ ] **Step 3: 实现来源顺序纯规则**

`AssetProductTypeSourceOrder`只返回：

```text
NEWER
IDEMPOTENT_REPLAY
STALE_SOURCE
SOURCE_CONFLICT
```

比较只使用第2.1节规则。`sourceVersion`只参与相等判定，禁止排序。

- [ ] **Step 4: 使用平台命令执行契约编排成功导入**

幂等Scope固定为：

```text
scopeCode = AST:ASSET_PRODUCT_TYPE:CONTROLLED_IMPORT
key = command.idempotencyKey
actorId = 当前已认证且具备受控导入专用权限的用户ID
```

`requestDigest`覆盖租户、稳定编码、启停、来源系统、来源键、来源版本、来源时间、载荷摘要和按设备ID稳定排序后的设备输入。

事务内顺序：

```text
校验租户/动作/字段
-> 锁定来源映射
-> 判定来源顺序
-> 校验稳定编码与现有业务含义
-> 新建或更新产品类型副本
-> 新建或更新来源映射
-> 按设备ID顺序关闭旧当前引用并写新当前引用
-> 返回结果
-> PlatformCommandExecutionApi写幂等完成与成功审计
```

`REPLAY_COMPLETED`返回首次结果，不重复写三表或成功审计。`CONFLICT`与`IN_PROGRESS`映射稳定业务错误。

- [ ] **Step 5: 处理来源和稳定编码冲突**

- 同源多目标：业务写事务回滚并保持已有`product_type_id`；事务结束后由AST冲突记录服务在独立事务中重新按来源键读取当前映射，只更新`mapping_status=CONFLICT`及冲突摘要字段，不覆盖当前目标、来源成功水位或最近成功副本，然后向调用方返回拒绝。
- 同稳定编码但来源业务含义冲突：业务写事务不覆盖显示名称或来源证据；独立事务记录`AST_PRODUCT_TYPE_CODE_CONFLICT`摘要。
- 更旧来源：三表零业务写入；业务事务结束后独立记录摘要化拒绝审计。
- 同水位异事实：业务写事务回滚并保留当前事实；独立事务重新读取当前映射后写冲突摘要。不得在抛出异常前写入冲突字段并假设其不会回滚。
- 跨租户设备或产品类型引用：整个业务命令回滚，无半写入；拒绝审计在事务结束后独立提交。
- 独立冲突事务只允许追加/更新冲突元数据和操作审计，不得修改当前映射目标、产品类型业务值、设备当前引用或幂等成功状态；若当前映射已被更晚合法来源推进，仍保留冲突发生时的摘要和观察水位，不反向覆盖新事实。

- [ ] **Step 6: 实现来源失败记录**

`recordSourceFailure`只允许`PRODUCT_TYPE_CONTROLLED_IMPORT`动作，按来源键锁定现有映射和产品类型：

```text
存在成功副本 -> sync_status=FAILED，保留业务值与last_successful_sync_time
不存在成功副本 -> 不创建猜测副本，仅记录拒绝审计
空响应 -> 与来源失败相同，不清空当前事实
```

该方法是后续连接器调用边界，不包含网络、调度、重试或补偿。

- [ ] **Step 7: 实现安全审计**

操作码固定为：

```text
ASSET_PRODUCT_TYPE_CONTROLLED_IMPORT
ASSET_PRODUCT_TYPE_IMPORT_REJECTED
ASSET_PRODUCT_TYPE_SOURCE_CONFLICT
ASSET_PRODUCT_TYPE_SOURCE_FAILURE
```

成功事实由`PlatformCommandExecutionApi`写入。业务写方法置于独立Spring Bean并由`PlatformCommandExecutionApi`事务调用；外层编排捕获稳定业务冲突后，调用另一个AST Bean的`@Transactional(propagation = REQUIRES_NEW)`方法重新读取必要当前事实、更新允许的冲突摘要并调用`OperationAuditApi`通用聚合重载，待该独立事务提交后再向调用方抛出拒绝。不得同类自调用绕过事务代理，也不得在原事务尚持有来源映射行锁时启动冲突事务。

- [ ] **Step 8: 补充并运行维护入口、来源顺序、幂等与冲突事务测试**

覆盖未认证、Controller缺少专用权限、绕过Controller直接调用Service但缺少专用权限仍拒绝、ReqVO伪造身份字段不存在、已认证授权用户通过、权限迁移不绑定角色，以及更早/同水位同事实/同水位异事实/更晚水位、`sourceVersion`不排序、同源多目标、业务事务回滚、独立冲突事务保留摘要与拒绝审计、并发推进后不反向覆盖、同键重放和异摘要冲突。

Run:

```powershell
mvn.cmd -pl pms-module-asset -am "-DskipITs=true" "-Dtest=AssetProductTypeImportControllerTest,AssetProductTypeSourceOrderTest,AssetProductTypeImportServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected：两个测试类实际执行且PASS。失败时停留在Task 4整改。

- [ ] **Step 9: 编译受控导入实现**

Run:

```powershell
mvn.cmd -pl pms-module-asset -am -DskipTests compile
```

Expected：受控导入只依赖现有安全上下文、API模块和AST自有DAL；唯一HTTP代码是专用管理入口，不出现CRM/MES Client、Job、自由CRUD或外部连接器代码。

- [ ] **Step 10: 提交逻辑单元**

```powershell
git add pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/producttype pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/producttype pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/controller/admin/producttype sql/migrations/V<实际版本>__fast002_product_type_import_permission.sql
git commit -m "feat(asset): implement controlled product type import"
```

---

### Task 5: 实现两个公开查询

**Files:**

- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/AssetProductTypeQueryService.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/producttype/AssetProductTypeApiImpl.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/api/producttype/AssetProductTypeApiImplTest.java`

- [ ] **Step 1: 实现按编码逐项查询**

处理顺序：

```text
校验租户和PRODUCT_TYPE_READ_CODES动作
-> 规范化请求编码
-> 空集合直接返回空
-> 一次批量查询
-> 按请求首次出现顺序逐项映射
```

未知编码结果固定为：

```text
exists=false
enabled=false
displayName=null
sourceSystem=null
sourceVersion=null
syncStatus=NOT_AVAILABLE
lastSuccessfulSyncTime=null
fromLastSuccessfulCopy=false
```

停用类型返回`exists=true, enabled=false`，不得伪装不存在。非`FRESH`且有成功副本时返回实际业务值和`fromLastSuccessfulCopy=true`。

- [ ] **Step 2: 实现按授权设备查询**

处理顺序：

```text
校验当前租户、ADR-0036调用主体和DEVICE_PRODUCT_TYPE_READ动作
-> subjectUserId为空或非法时失败关闭
-> deviceIds为空直接返回空
-> DeviceAccessScopeService.visibleProjectIds
-> 空项目范围直接返回空
-> 以统一effectiveAt一次XML联查请求设备、项目范围和租户
-> 可见条件同时包含设备当前project_id和有效ast_device_project_relationship
-> 映射可见设备结果
```

跨租户、无权或不存在设备均无结果，不区分原因、不泄露存在性。可见设备但产品类型未知、冲突或未解析时返回设备ID和明确`resolutionStatus`，产品类型编码、名称可为空。

- [ ] **Step 3: 实现API适配器**

`AssetProductTypeApiImpl`只委托Query Service，不访问Mapper，不承担SQL或数据范围拼装。类使用现有模块的`@Service`与构造注入模式。

- [ ] **Step 4: 补充并运行公开查询契约测试**

覆盖逐编码结果、未知/停用/降级、空集合、服务动作拒绝、跨租户、当前项目与有效关联项目可见、空范围和无权设备统一为空，以及未解析状态不泄露。

Run:

```powershell
mvn.cmd -pl pms-module-asset/pms-module-asset-api,pms-module-asset -am "-DskipITs=true" "-Dtest=AssetProductTypeApiImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected：查询契约测试实际执行且PASS。失败时停留在Task 5整改。

- [ ] **Step 5: 编译正向闭环**

Run:

```powershell
mvn.cmd -pl pms-module-asset/pms-module-asset-api,pms-module-asset -am -DskipTests compile
```

Expected：两个公开查询、受控导入和三表实现完成编译；尚未以测试结果声明完成。

- [ ] **Step 6: 提交逻辑单元**

```powershell
git add pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/AssetProductTypeQueryService.java pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/api/producttype
git commit -m "feat(asset): implement product type authorized queries"
```

---

### Task 6: 执行跨单元定向回归与遗漏补测

**Files:**

- Modify if needed: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/api/producttype/AssetProductTypeApiContractTest.java`
- Modify if needed: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/api/producttype/AssetProductTypeApiImplTest.java`
- Modify if needed: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/producttype/AssetProductTypeSourceOrderTest.java`
- Modify if needed: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/producttype/AssetProductTypeImportServiceTest.java`
- Modify if needed: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/dal/AssetProductTypeSchemaContractTest.java`
- Modify if needed: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/dal/producttype/DeviceCurrentProductTypeMapperTest.java`

前序Task必须已经各自创建并通过对应测试；本Task只复核跨单元组合、补齐遗漏，不得把单元测试首次执行拖到此处。

- [ ] **Step 1: 复核公开契约测试**

断言：

```text
接口恰有两个规格方法
Query和Result均为record
DTO不引用DO、Mapper、Service或Controller类型
编码结果包含存在、停用和降级字段
设备结果不暴露SN、客户、项目、凭证或来源载荷
```

- [ ] **Step 2: 复核来源顺序、冲突事务和受控导入测试**

覆盖：

```text
首次导入
sourceVersion不透明且不排序
业务写事务回滚后，冲突摘要与拒绝审计由独立事务保留
独立冲突事务不覆盖并发推进后的当前映射目标和最近成功副本
同时间同版本同摘要幂等
同时间异版本冲突
同时间异摘要冲突
更早sourceUpdatedAt拒绝
更新sourceUpdatedAt允许更新
同源多目标不覆盖
稳定编码软删除后不可复用
跨租户设备引用全回滚
来源失败和空响应保留最近成功副本
PlatformCommandExecutionApi重放不重复写
同幂等键异摘要冲突
安全审计不含完整载荷或Secret
```

- [ ] **Step 3: 复核查询服务测试**

覆盖：

```text
每个请求编码均有结果
未知编码不猜名称
停用类型明确返回停用
空编码返回空
无服务动作拒绝
跨租户编码查询拒绝且不泄露
空设备集合返回空
空项目范围返回空
跨租户和无权设备返回空
未知/冲突/未解析设备返回明确状态
FAILED/STALE副本返回最近成功标记
```

- [ ] **Step 4: 复核Schema契约测试**

通过`V*__fast002_asset_product_type.sql`模式定位唯一迁移，断言三表、三项唯一约束、租户内引用、来源水位、同步状态、冲突摘要和乐观锁字段存在；断言迁移不包含产品类型业务值INSERT、CRM/MES连接器表或对旧迁移的修改。

- [ ] **Step 5: 运行定向测试**

Run:

```powershell
mvn.cmd -pl pms-module-asset/pms-module-asset-api,pms-module-asset -am "-DskipITs=true" "-Dtest=AssetProductTypeApiContractTest,AssetProductTypeApiImplTest,AssetProductTypeSourceOrderTest,AssetProductTypeImportServiceTest,AssetProductTypeSchemaContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected：Maven报告确认前序单元测试全部实际执行且PASS；另运行`DeviceCurrentProductTypeMapperTest`，不得因统一命令遗漏关联可见范围测试。

- [ ] **Step 6: 提交逻辑单元**

```powershell
git add pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/api/producttype pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/producttype pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/dal/AssetProductTypeSchemaContractTest.java
git commit -m "test(asset): cover product type controlled copy"
```

---

### Task 7: 完成真实MySQL迁移、幂等与并发验证

**Files:**

- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/producttype/AssetProductTypeMySqlIntegrationTest.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/producttype/AssetProductTypeConcurrencyMySqlTest.java`

- [ ] **Step 1: 补真实MySQL正向与降级测试**

覆盖：

```text
三表迁移成功
稳定编码停用/软删除后唯一键仍占用
受控导入建立副本、映射和设备当前引用
重复同源事实只保留一个当前副本
旧来源水位不覆盖
停用事实历史可查但新消费不可用
授权设备查询只返回项目范围内设备
来源失败保留最近成功业务值和时间
```

- [ ] **Step 2: 补真实MySQL并发测试**

使用独立事务和同步栅栏覆盖：

```text
并发同稳定编码最多一个首次插入成功
并发同来源键不同目标保留一个原映射并形成冲突
并发更新同设备最终至多一个current_marker=1
同幂等键并发最多一个NEW，其余为重放或处理中
```

测试不得通过降低唯一约束、移除租户过滤或串行化测试线程来规避竞态。

- [ ] **Step 3: 启动固定测试基础设施**

```powershell
.\scripts\test-infrastructure.ps1 start
.\scripts\test-infrastructure.ps1 status
$env:NPDMS_DB_NAME = "npdms_test"
$env:NPDMS_MYSQL_PORT = "23316"
$env:NPDMS_REDIS_PORT = "26379"
```

Expected：MySQL和Redis健康，应用仍在宿主机执行。

- [ ] **Step 4: 验证空库Flyway**

```powershell
.\scripts\test-infrastructure.ps1 reset
docker compose --project-name npdms-50eb-test run --rm migrate info
docker compose --project-name npdms-50eb-test run --rm migrate validate
docker compose --project-name npdms-50eb-test run --rm migrate migrate
docker compose --project-name npdms-50eb-test run --rm migrate validate
docker compose --project-name npdms-50eb-test run --rm migrate migrate
```

Expected：迁移和validate成功，第二次migrate无待执行迁移且无重复数据。

- [ ] **Step 5: 验证最近批准基线库升级**

在保留最近批准基线Schema的数据卷上执行：

```powershell
docker compose --project-name npdms-50eb-test run --rm migrate migrate
docker compose --project-name npdms-50eb-test run --rm migrate validate
```

Expected：前向升级成功，既有设备表和数据不被覆盖。

- [ ] **Step 6: 运行真实MySQL测试**

```powershell
mvn.cmd -pl pms-module-asset -am "-DskipITs=false" "-Dtest=AssetProductTypeMySqlIntegrationTest,AssetProductTypeConcurrencyMySqlTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected：两个真实MySQL测试类实际执行且全部PASS。

- [ ] **Step 7: 提交逻辑单元**

```powershell
git add pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/producttype
git commit -m "test(asset): verify product type mysql constraints"
```

---

### Task 8: 交付Inspection专用只读适配器并验证消费边界

**Files:**

- Create: `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/producttype/inspection/InspectionAssetProductTypeApi.java`
- Create: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/security/InspectionAssetProductTypeApiImpl.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/producttype/security/InspectionAssetProductTypeApiImplTest.java`
- Modify: `pms-module-service/pom.xml`
- Create: `pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/inspection/AssetProductTypeConsumerBoundaryTest.java`

- [ ] **Step 1: 建立Inspection专用只读接口与AST实现**

`InspectionAssetProductTypeApi`只声明与通用API相同的两个只读方法，不包含消费者代码、主体ID、动作、租户或上下文方法。`InspectionAssetProductTypeApiImpl`位于AST的`service.producttype.security`包，固定通过`AssetProductTypeCallerContext.callAsInspection(...)`调用通用`AssetProductTypeApi`；不接受任意消费者或动作，不暴露受控导入。

- [ ] **Step 2: 验证适配器固定边界**

测试断言：两个专用方法分别建立Inspection上下文并委托通用API；缺失租户拒绝；调用完成或异常后上下文清理；API模块不存在名称或签名可设置`consumerCode/principalId/actionCode`的public类型；`AssetProductTypeCallerContext`和`AssetProductTypeCaller`不是public；Service生产代码只允许引用`InspectionAssetProductTypeApi`，不直接引用通用`AssetProductTypeApi`。

- [ ] **Step 3: 增加API模块依赖**

仅增加：

```xml
<dependency>
    <groupId>cn.iocoder.boot</groupId>
    <artifactId>pms-module-asset-api</artifactId>
    <version>${revision}</version>
</dependency>
```

不得依赖`pms-module-asset`业务模块。

- [ ] **Step 4: 补消费边界测试**

测试只证明：

```text
pms-module-service可编译引用InspectionAssetProductTypeApi及DTO
Inspection生产包不直接引用通用AssetProductTypeApi，也不引用AST DO、Mapper、Service或ast_*表
API不可用、未知或停用事实可被消费者识别为失败关闭输入
```

不创建Inspection规则实现，不宣称发布或工程师选择已完成。

- [ ] **Step 5: 运行适配器与消费边界测试**

```powershell
mvn.cmd -pl pms-module-asset,pms-module-service -am "-DskipITs=true" "-Dtest=InspectionAssetProductTypeApiImplTest,AssetProductTypeConsumerBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected：测试实际执行并PASS；依赖树中Inspection只依赖`pms-module-asset-api`。

- [ ] **Step 6: 提交逻辑单元**

```powershell
git add pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/producttype/inspection pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/producttype/security/InspectionAssetProductTypeApiImpl.java pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/producttype/security/InspectionAssetProductTypeApiImplTest.java pms-module-service/pom.xml pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/inspection/AssetProductTypeConsumerBoundaryTest.java
git commit -m "feat(asset): add inspection product type adapter"
```

---

### Task 9: 回归、追溯、自审与Feature收口

**Files:**

- Modify: `tasks/features/F-AST-002.md`
- Modify if generated output changes: `docs/traceability/requirement-matrix.md`
- Modify if generated output changes: `docs/traceability/requirement-version-coverage.json`
- Modify if current index requires projection refresh: `specs/features/README.md`

- [ ] **Step 1: 运行AST与消费者模块回归**

```powershell
mvn.cmd -pl pms-module-asset,pms-module-service -am "-DskipITs=false" test
```

Expected：相关Reactor模块全部PASS，真实数据库测试未因命名规则被静默跳过。

- [ ] **Step 2: 运行测试分类门禁**

```powershell
mvn.cmd -Ppms-test-unit -DskipITs=true test
mvn.cmd -Ppms-test-contract -DskipITs=true test
mvn.cmd -Ppms-test-integration -DskipITs=false test
```

Expected：三个分类均PASS；另以Task 7显式命令作为两个MySQL类实际执行证据。

- [ ] **Step 3: 运行后端装配、lint等价检查和类型编译**

仓库后端未定义独立lint/typecheck脚本，以Maven编译、测试和装配构建作为Java静态与类型检查：

```powershell
mvn.cmd -pl yudao-server -am -DskipTests package
mvn.cmd verify -DskipTests
```

Expected：`yudao-server`装配成功，全Reactor编译成功。

- [ ] **Step 4: 运行规格、追溯与迁移校验**

先按仓库现有脚本入口生成并校验追溯，不手改生成覆盖值：

```powershell
python scripts/generate_requirement_traceability.py
python scripts/generate_domain_entity_migration_contract.py
python scripts/generate_phase2_contract_map.py
python scripts/validate_domain_entity_migration_alignment.py
python scripts/validate_sds_phase2.py
python -m unittest discover -s scripts/tests -p "test_*.py"
```

Expected：F-AST-002继续只投影`EQP-01@V1=PARTIAL`；三对象、API、代码和测试链可追溯；若全仓脚本仍有与本Feature无关的既有失败，必须逐项确认未由本差量引入并在Task证据中如实记录，不修改无关规则使其变绿。

- [ ] **Step 5: 执行范围与安全自审**

逐项检查：

```text
无CRM/MES Client、Job、游标、重试、补偿、对账实现
无产品类型页面、自由CRUD、字典或猜测种子
无DeviceDO、EquipmentDO、旧Controller或CONP语义改动
无Yudao基础平台改动
无SQL注解、${}、.last(...)或Service拼SQL
无空权限省略条件扩大查询
无跨模块业务表读取
无完整来源载荷、设备Secret或无权详情进入审计
sourceVersion未被排序
sourceUpdatedAt平局冲突失败关闭
```

- [ ] **Step 6: 发起独立Code Review**

评审输入必须包含：锁定Feature Spec、Technical Plan、完整差量、Flyway与真实MySQL结果、两个公开API契约测试、权限负向测试、幂等/并发结果、追溯结果和已知无关基线失败。高可信GO可直接接受；NO-GO只整改与F-AST-002正式契约相关的问题。

- [ ] **Step 7: 回写Implementation Done唯一状态**

仅在全部适用验证与独立评审GO后，将`tasks/features/F-AST-002.md`更新为：

```text
Feature实施状态：DONE
Technical Plan Gate：PASS / <本计划Gate编号>
Implementation Done Gate：PASS / <独立评审Gate编号>
```

记录最终提交、测试命令与结果、Flyway版本、真实MySQL证据、已知无关基线失败和明确排除项。不得把Feature Spec、索引或追溯矩阵变成第二状态源。

- [ ] **Step 8: 提交Feature收口**

```powershell
git add tasks/features/F-AST-002.md docs/traceability/requirement-matrix.md docs/traceability/requirement-version-coverage.json specs/features/README.md
git commit -m "docs(asset): close product type controlled copy feature"
```

只暂存实际变化的投影与任务文件，禁止提交Python缓存、临时输出、真实凭据或无关差量。

---

## 5. 验收映射

| 验收项 | 实现Task | 主要证据 |
|---|---:|---|
| `AC-FAST002-001`受控导入、同事实幂等、旧事实不覆盖 | 4、6、7 | Import Service单测、真实MySQL来源水位与幂等测试 |
| `AC-FAST002-002`稳定编码唯一、同源多目标待处理 | 2、4、7 | 唯一约束、冲突摘要、并发测试 |
| `AC-FAST002-003`按编码逐项返回完整事实 | 1、5、6 | API契约和Query Service测试 |
| `AC-FAST002-004`授权设备、空范围和不可见统一为空 | 3、5、6、7 | 批量XML、权限负向和真实MySQL查询测试 |
| `AC-FAST002-005`未知/冲突/未解析不猜测 | 2、4、5、6 | resolution状态测试和Schema约束 |
| `AC-FAST002-006`停用拒绝新消费、历史可解释 | 4、5、7 | 停用前后真实MySQL测试 |
| `AC-FAST002-007`来源失败保留最近成功副本 | 4、5、6、7 | 来源失败单测与真实MySQL降级测试 |
| `AC-FAST002-008`API模块与DTO边界 | 1、6、8 | 反射契约和Inspection消费边界测试 |
| `AC-FAST002-009`租户、服务身份和数据范围 | 1、3、5、6、7 | 守卫、空集合和跨租户负向测试 |
| `AC-FAST002-010`无连接器和越界完成声明 | 1—9 | 自审、差量审查和独立评审 |

## 6. 非目标与后续Gate

F-AST-002 Implementation Done后只解除F-INS-001关于AST产品类型公开契约的外部实施依赖。以下事项仍需各自Feature和Gate：

```text
EQP-04 CRM/MES连接器、调度、重试、补偿和对账
F-INS-001 巡检规则发布与工程师选择
INS-01/02/04/05/06/07/08
SIT、UAT、Deployment和Release
```

任何后续Feature不得直读本Feature三张表，必须消费`pms-module-asset-api`或经正式评审扩展的AST公开契约。
