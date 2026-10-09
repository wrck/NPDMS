# 项目业务直接继承主干

状态：本文说明直接继承接口与实现方式，不单独作为全链验收结论。2026-10-09 增量补齐聚合明细、命名展示、条件计算及专业页面；实际通过范围和待验证项见 [本轮证据](../generated/default-business-completion-20261009/README.md)。依据：[2026-10-07 用户确认目标及新增主干决定](../decisions/2026-10-07-unified-business-default-implementation.md)。本说明不表示所有生产业务已经迁移。

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

### 2026-10-09 默认链增量

普通 `POST /` 与 `PUT /{id}` 统一接收主体字段、所属明细、`$binding`、`$extensions` 和业务声明的 `$business`。前端统一由 `ProjectBusinessForm` 读取和切换已发布布局、拼装绑定及扩展差异；专业组件只提供展示、读取值及专业命令。内容修订编辑也复用该组件，冻结修订只读取已保存绑定。

所属明细使用业务自己的表、Mapper 和类型：

```java
class ExampleLine extends OwnedBusinessDetailEntity {
    @BusinessModelField @NotBlank private String description;
}
interface ExampleLineMapper extends BusinessDetailMapper<ExampleLine> { }
// 聚合根中的字段；正常提供 getter/setter。
@BusinessModelField
@TableField(exist = false)
@BusinessOwnedDetails(mapper = ExampleLineMapper.class)
private List<ExampleLine> lines;
```

明细表包含继承列及 `aggregate_type`、`aggregate_id`、`aggregate_revision_id`、`detail_order`；归属键由服务端生成，不开放明细独立写接口。整份明细列表随聚合保存，已有行携带 `id/version`，遗漏的旧行按逻辑删除处理。伪造父键、其他聚合的明细 ID、重复 ID 和陈旧版本均被拒绝。内容版本复制生成新明细身份，不能复用冻结行写入当前版本。

公共保存先授权与幂等核验，在同一事务内完成锁定/并发检查及 `beforeCreate/beforeUpdate`、`beforeSave`、专业表单命令。主体、所属明细、绑定及扩展全部写入后，才执行 `afterCreate/afterUpdate`、`afterSave`，随后形成审计/回执/Outbox。`afterBodyPersisted` 是主体与明细保存后的专用位置，不代表完整聚合已经就绪；创建和更新的扩展写入时点不同，但均受同一事务保护。任何后置异常都回滚全部写入。自定义 `change(...)` 也执行公共保存钩子。重放既有回执不重复运行钩子。

公共 CRUD 可被业务重载；在 `super` 调用后追加数据库动作时，外层重载方法必须使用覆盖两者的 `@Transactional(rollbackFor = Exception.class)`。执行模板保持受保护边界。公共实例方法不可随意改为 `final`，否则 CGLIB 代理调用可能绕过已初始化目标对象。

字段配置新增 `profiles` 与 `defaultProfile`。每个配置包含稳定 `key`、名称、字段覆盖以及可选已发布 `formRevisionId`；字段覆盖可控制 `listVisible/formVisible/tabVisible`，不能提升读取、写入、查询或排序权限。`GET /model?configurationName=...` 与分页请求的 `configurationName` 使用同一配置。旧单份 JSON 配置仍可读取；旧保存接口保留命名配置。

新建内容可采用所选配置关联的布局；已有内容优先读取保存的绑定，不随默认模板发布自动升级。切换布局保留隐藏的扩展值，合并定义时拒绝同名不同类型；`false`、`0`、`null`、数组均按明确提交值处理。通用 `ProjectBusinessDetails` 根据明细字段元数据呈现 Tab，隐藏 Tab 不删除其数据。

首次保存前跨布局输入的扩展通过 `$binding.definitionSources` 声明原已发布布局及其字段绑定；服务端逐份验证租户、发布状态和本业务适用性，再生成不可变的合并定义。客户端不能传入任意字段 schema。切回原布局但仍带有新增扩展值时，也须保留该来源；数组控件采用空数组初值，同时保留原值基线以正确判断修改。

`POST /{id}/evaluate` 接收 `BusinessCondition`，从授权后的已保存主体、所属明细、扩展值和公共交付件读取事实。支持 `FIELD/DELIVERY/ALL/ANY/NOT/MEMBER_ALL/MEMBER_ANY/COUNT`，返回 verdict、缺失/不满足原因及 factVersion。条件只判断，不直接推进生命周期。未声明完成条件的普通业务保存后不会自动视为完成；可在 `@ProjectBusinessModel(completionCondition = "...")` 声明既定条件，`GET /{id}/completion` 和运行时 `BUSINESS_CONDITION_SATISFIED` 使用同一求值器。既有同步与事件后端的字段条件也委托该求值器，普通实体不新增完成布尔 Provider。

通用独立页面为 `/pms/business/records/:stableCode`，先选择可访问项目，再加载该业务直接继承 API；使用不依赖活动任务。测试装配示例见 `DefaultBusinessAcceptanceConfiguration` 与 `default-business-acceptance.sql`，仅用于指定隔离库，不代表新增生产业务。

专业表单通过公共包装层接收 `api/current/scopeProjectId` 等上下文，并只负责渲染和读取输入。项目任务中的列表式办理页仍展示本项目授权记录；`initialEntityId` 不应在弹窗编辑或工作区模式下筛掉后来新增的记录，服务端项目范围始终生效。

### 先前轮次证据（保持原验证边界）

- 两个空 Service/Controller 的 Spring 泛型注入、独立 HTTP 创建/读取、特殊钩子、权限拒绝、回执重放、公共上传调用及错误实体类型拒绝已有单元证据；Mapper 在这些检查中使用测试替身，不能算真实 SQL 验收。
- 公共前端客户端和操作状态已有运行测试。2026-10-08 的前一轮复核已消除全量主 tsconfig 的 60 项错误并完成类型检查、175 项前端测试及构建，见 [复核报告](../generated/default-business-review-20261008/README.md)；后续运行时增量须单独验证，不沿用旧结果作验收。
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

部署本增量需执行前向迁移 `V400__business_field_configuration.sql` 和 `V401__business_field_configuration_permissions.sql`。后者仅登记两项存量业务可分配配置权限，不自动授予任何角色。本增量的适用隔离 MySQL、版本化真实浏览器及 V400/V401 首次/幂等执行已完成，准确来源、复验范围和夹具边界见[增量验收结果](../generated/direct-business-version-20261007/README.md)。这不代表生产部署或所有存量实体迁入完成。

### 交付件与内容版本的已确认边界

用户 2026-10-07 明确不增加冻结版本的独立交付件引用清单。业务交付件已在公共实体拥有业务类型和实体 ID，需要时直接按这些键读取上传历史；不为内容版本再复制一套清单、文件或关联表。

默认 `GET /{id}/deliverables/history` 从相同 `plt_delivery_material` 查询同一项目、业务类型和实体 ID 的历次记录，可按交付件类型筛选；返回原状态，页面历史模式只读。失效/已删除记录仅作历史展示，不进入当前完成判断，也不通过历史入口恢复文件下载或修改权限。当前完成判断继续走原有效列表、真实文件可用性检查及最新有效记录顺序。

## 模板运行时默认能力（2026-10-08 增量）

- 继承 Service 自动提供配置目录与只读业务事实。普通实体默认 `BUSINESS_RECORD_SAVED`；通用版本业务默认 `BUSINESS_CONTENT_EFFECTIVE`。真实“确认”等语义通过受保护状态钩子重载，不另写逐实体 Provider。
- 保存后的可信项目变化与公共交付件变化，使用现有事务 Outbox 唤醒规则重算。不会直接把节点写为完成。
- `BUSINESS_DELIVERY_UPLOADED:REPORT` 表示当前精确业务身份下已有 `REPORT` 类型有效上传。公共完成接口与运行时共用校验，无另一次交付件提交或新增冻结引用清单。业务条件与交付件条件按模板的规则组合，其他门禁照常生效。
- 已部署继承 Controller 自动提供 `DIRECT_BUSINESS_<稳定编码>` 默认业务视图；嵌入页面限定项目与可选实体，宿主授权只能收紧操作，保留未保存内容的离开保护。原生已发布视图不被悄悄替换。
- 本次覆盖业务条件及上传完成判断，不新增一套结果日志，也不声称已为所有新实体实现正式 `NEW_RESULT`/历史精确结果订阅。原有专有结果来源继续生效。
- 本次代码、测试和本机浏览器验收边界见 [运行时接入复核](../generated/default-business-runtime-20261008/README.md)。

### 正文状态与交付件写入分别判断

默认页面的交付件沿用服务端 UPDATE/save 功能权限、宿主允许动作和项目可写范围，但不复用正文 UPDATE 的状态钩子。`operationAllowed('delivery', current)` 可进一步限制交付件生命周期；后端 `validateDelivery` 独立实施相同限制。工勘草稿及已确认状态允许公共交付件写入，确认后正文仍不可修改；驳回、归档状态继续禁止交付件写入。历史引用、归档材料及文件权限仍由公共服务校验，不因页面入口可见而绕过。


### 表单初建与展示组合

默认 Controller 的创建接口接受固定字段及可选 $binding / $extensions，在同一默认写事务和幂等记录中保存主体、表单绑定与扩展值；不能先创建再由页面补交一次隐式写入。form-defaults 按项目只读授权返回业务覆盖提供的已发布模板建议，不自动改写已有绑定。form 的 context 为只读展示上下文，不加入可写字段目录。业务需在同一保存事务执行既有领域命令时，可重载 beforeFormWrite 接收显式 $business 参数；默认实现拒绝非空命令，版本化保存目前也显式拒绝未支持的命令，不能静默忽略。前端通过可替换表单组件复用旧展示，CRUD、并发、权限、交付件及离开保护仍由 ProjectBusinessPage 承担。
