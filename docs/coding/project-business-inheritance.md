# 项目业务直接继承主干

状态：默认继承链已通过隔离 SQL/MVC 与真实浏览器闭环；生产实体接入仍在推进。依据：[2026-10-07 用户确认目标及新增主干决定](../decisions/2026-10-07-unified-business-default-implementation.md)。本说明不表示所有生产业务已经迁移。

## 入口与依赖方向

业务 Controller → 业务 Service → 业务 Mapper → 本业务实体/表。

- `ProjectBusinessController<S,E>`：继承模型说明、列表、详情、创建、更新、删除、回执、表单/扩展以及交付件 API。
- `DefaultProjectBusinessService<M,E>`：直接持有本业务 Mapper，统一执行权限、项目范围、校验、事务、并发、幂等、审计及事件；Service 本身继承 `EntityFieldProvider`，不再为普通实体另写字段能力适配器。
- `BusinessMapper<E>`：继承简单业务分页与 MyBatis-Plus 持久化。复杂 SQL 由本业务 Mapper XML 扩展。
- `BaseProjectBusinessEntity`：继承项目、身份、租户、版本和审计字段。业务保留独立数据表。
- `ProjectBusinessPage`：仅绑定业务 API 基地址，复用列表、表单、CRUD 和交付件组件；客户端由 `createProjectBusinessApi` 创建。

普通业务不编写 ModelContributor、不注册操作分发器、不新增 Owner/权限/交付件适配器。实体上的稳定身份与字段注解用于本 Service 生成元信息，不要求先进入全局模型目录。

现有模型目录、模板执行、业务操作分发及 Host 保留。新主干共享已有审计、Outbox、项目范围、字段转换与安全持久化工具，但独立 CRUD 不调用旧分发器。跨业务交付件展示通过自动发现业务 Service 定位归属；这个查询索引不参与独立 CRUD。

## 普通业务的薄接入

下面是形状示例，业务编码、权限及表结构必须使用实际业务批准的定义，不按示例创建生产业务：

```java
@TableName("example_record")
@ProjectBusinessModel(ownerModule="EXAMPLE", entityType="record", stableCode="EXAMPLE_RECORD",
        name="示例记录", permissionPrefix="pms:example-record")
class RecordDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name="名称") @NotBlank
    private String name; // 按项目约定提供 getter/setter
}
interface RecordMapper extends BusinessMapper<RecordDO> { }
@Service
class RecordService extends DefaultProjectBusinessService<RecordMapper,RecordDO> { }
@RestController
@RequestMapping("/api/v1/pms/example-records")
class RecordController extends ProjectBusinessController<RecordService,RecordDO> { }
```

Mapper 按项目现有扫描约定放置或标注。业务表通过正式 Flyway 迁移建立，包含继承映射的列；继承不等于省略真实实体与表，也不等于自动授予用户权限。

```vue
<ProjectBusinessPage api-base="/api/v1/pms/example-records" deliverable-type="REPORT" />
```

## 默认接口

- `GET /model`：本业务字段和可执行操作。
- `POST /page`：pageNo、pageSize、filters；租户和可见项目集合由服务端生成。
- `GET /{id}`：当前业务详情，仅投影允许读取的字段。
- `POST /`：创建，values 与 idempotencyKey。
- `PUT /{id}`：按明确提交的 values 字段更新；version 与 idempotencyKey 必填，显式 null 保留清空语义。
- `DELETE /{id}`：version 参数与 Idempotency-Key 请求头；有关联交付件/历史引用时拒绝。
- `GET /receipts/{key}?operation=...`：查询结果，不重新执行。
- `GET /{id}/form`：读取已有布局、扩展定义与值；普通业务直接使用自身身份。
- `POST /{id}/save-form`：与普通保存同样的 version/idempotencyKey/values；固定字段、`$extensions` 与可选 `$binding` 同一事务保存，失败一起回滚。扩展包含 definitionRevisionId、expectedVersion、values；布局包含 expectedVersion、formRevisionId、extensionDefinitionRevisionId、fieldBindings、bindRemainingFields。未提交的扩展字段保留，业务版本只推进一次。
- `/{id}/deliverables...`：继承上传、读取、修改、删除、文件与完成判断。上传与完成判断核对 projectId、businessType、businessEntityKey、deliverableType。

`ProjectDeliveryCollection` 按项目/交付件类型展示，通过 `/api/v1/pms/business-deliverables` 公共接口读取；服务端逐业务校验权限和项目范围，不要求旧模型工作台权限。旧归集页面和旧入口保持原有权限。

交付件仍进入同一个 `plt_delivery_material`，没有新增第二张交付件业务表。新旧入口使用相同文件校验、归集、有效性、并发及历史引用保护。此版本没有完成全部旧原生材料的归集迁移。

## 特殊业务

- `beforeCreate`、`beforeUpdate`、`beforeDelete`、`validateBusiness` 等类型化钩子承担业务差异。
- 重载 `selectPage(BusinessReadQuery)` 调用自身 Mapper XML；XML 必须保留服务端 tenantId/projectIds 条件，空项目集合拒绝查询。
- 业务可新增专用 Controller 方法，直接调用对应 Service 方法；Service 可通过受保护的 `change(...)` 复用权限、锁、CAS、幂等与审计，再实施类型化实体变化。`businessOperations(...)` 仅说明该业务新增动作，不重复列出 CRUD。
- 业务变化钩子不得更改控制字段，不在钩子中绕过事务或直接执行不可撤销的外部动作。必要外部副作用沿用 Outbox 等已批准机制。
- 存量修订模型通过 `formTarget(E)` 重载指向原逻辑键/修订键，保留已有布局和扩展值；普通新业务无须重载。
- `copyDeliveries(sourceId,targetId,key)` 复用不可变文件版本，创建独立精确引用及统一交付件记录；不复制文件字节、不新增业务交付件适配器。
- 新页面提供 actions/details/business-fields 插槽，特殊交互只添加差异，不复制整套页面或每业务建立 CRUD API 模块。`operationAllowed` 只能进一步收紧服务端操作可用性，不能授予权限；后端仍校验状态与权限。

## 当前证据与未完成项

- 两个空 Service/Controller 的 Spring 泛型注入、独立 HTTP 创建/读取、特殊钩子、权限拒绝、回执重放、公共上传调用及错误实体类型拒绝已有单元证据；Mapper 在这些检查中使用测试替身，不能算真实 SQL 验收。
- 公共前端客户端和操作状态已有运行测试；真实传递依赖类型检查仍遇到四项旧文件错误，未称全量类型检查通过。
- `DirectBusinessCrudMySqlTest` 覆盖两业务 HTTP CRUD、显式 null、CAS、回执、统一材料及特殊 XML/API，真实 MySQL 已通过，包含只读删除与跨租户读取拒绝。
- 两业务实际默认页面及公共归集已通过浏览器，旧页面兼容通过。见 [验收结果及边界](../generated/direct-business-inheritance-20261007/README.md)。
- 全部生产实体迁入、生产登录/存储、模板连接及历史材料归集仍需分别验证，不以类数量或局部测试数代替完成结论。
