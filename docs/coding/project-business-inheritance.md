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

## 公共内容版本与字段配置（2026-10-07 增量）

版本业务使用 `DefaultVersionedProjectBusinessService<M,E,RM,R>` 与 `VersionedProjectBusinessController<S,E>`。当前实体 E 和继承 E 的修订实体 R 各有独立业务表；R 实现 `MutableEntityRevision`。空 Service/Controller 即继承草稿、保存、冻结生效、复制、放弃、比较、历史与表单/扩展快照。普通 CRUD 和版本操作共享权限、项目范围、幂等回执、审计和 CAS。已有历史后禁止直接改当前正文或绕过版本直接改当前扩展。业务差异通过受保护钩子扩展。

字段配置有两层，均在默认实现内完成：

- 实体 `@BusinessModelField` 提供名称、读写、字典及 `displayOrder/listVisible/searchable/sortable` 默认值，父类字段自动继承。列表/对象集合不支持默认 SQL 查询和排序；非持久化字段也不会开放这两项能力。
- `ProjectBusinessPage` 自带“字段配置”，允许调整名称、顺序、列表显示、查询及排序开关。配置由 `plt_business_field_configuration` 按租户与真实业务身份持久化，带 CAS、审计；空配置恢复代码默认。它只保存展示元数据，业务数据仍在独立实体表，未引入通用业务关联存储。
- 默认 `GET /field-configuration/defaults`、`GET /field-configuration`、`PUT /field-configuration` 自动继承；保存使用默认生成的本业务 `permissionPrefix:configure` 权限，读取沿用本业务查询权限，无新增逐实体策略类。普通记录更新权限不授予租户级字段配置权。
- 配置只能收紧代码声明的查询/排序能力，不能授予字段读取或写入权限。新标签不改变字段键和数据库列名。服务端按有效配置验证过滤和排序，即使手工绕过页面也不能查询被关闭的字段。
- `POST /page` 支持 `sorts: [{fieldCode, direction: "ASC" | "DESC"}]`。字段键由服务端映射到 ORM 白名单列，枚举方向不拼接任意 SQL；默认增加 id 降序作为稳定分页尾序。复杂查询仍由业务 Mapper XML 实现并保持上述范围与受控字段约束。
- 字段配置对话框支持失败保留输入、取消、恢复默认、并发冲突拒绝、业务切换隔离及未保存离开确认。表单布局继续复用已有已发布动态表单；字段配置表不复制表单 schema 或扩展字段值。

部署本增量需执行前向迁移 `V400__business_field_configuration.sql` 和 `V401__business_field_configuration_permissions.sql`。后者仅登记两项存量业务可分配配置权限，不自动授予任何角色。截至本次增量提交，本地 H2 与组件运行检查已有结果；新版本的真实 MySQL、浏览器及 V400 MySQL 执行仍待完成，不能引用旧提交的通过结论替代。

### 交付件与内容版本的已确认边界

用户 2026-10-07 明确不增加冻结版本的独立交付件引用清单。业务交付件已在公共实体拥有业务类型和实体 ID，需要时直接按这些键读取上传历史；不为内容版本再复制一套清单、文件或关联表。

默认 `GET /{id}/deliverables/history` 从相同 `plt_delivery_material` 查询同一项目、业务类型和实体 ID 的历次记录，可按交付件类型筛选；返回原状态，页面历史模式只读。失效/已删除记录仅作历史展示，不进入当前完成判断，也不通过历史入口恢复文件下载或修改权限。当前完成判断继续走原有效列表、真实文件可用性检查及最新有效记录顺序。
