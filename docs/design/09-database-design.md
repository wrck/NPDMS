# SDS Phase 2：数据库设计

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；表级 Owner 与需求范围继承 `08-data-model.md`，逐项链接见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 2 数据架构
> 前置设计：`08-data-model.md`、`08a-domain-entity-migration-alignment.md`
> 物理承载决策：ADR-0030（ProjectTask执行契约与CUT-03清单）、ADR-0031（客户服务等级与割接配置版本载体）
> 目标数据库：`npdms` / MySQL 8.4
> 实现基线：`E:\AICoding\Projects\NPDMS` @ `856d05264ab4a4fb69b94896c172e4a1c29aae02`

## 1. 物理设计原则

1. 目标业务表使用ADR-0019确认的`<domain_code>_<full_domain_object_name>`命名，数据库和运行配置统一使用`npdms`；旧库表名仅作为只读来源证据保留，不直接沿用为目标表名。
2. 任何已执行 Flyway 迁移均不可修改。所有纠正、补列、回填、索引和兼容视图使用下一个未占用版本的前向迁移。
3. 聚合根表保存当前事实；不可变 revision、状态历史、审批快照、同步批次、事件和审计使用追加表。
4. 跨 Context 只保存逻辑 ID，不建立跨 Context 级联更新/删除；同聚合内部可以使用外键或应用级强校验。
5. 业务唯一键包含 `tenant_id`。所有读取由服务端注入租户和数据范围，不信任客户端提交的租户、组织或项目范围。
6. 生命周期状态使用稳定字符串代码；字典负责显示和分类扩展，状态机负责合法迁移。
7. JSON 仅用于低频扩展快照或外部原文摘要，不承载必须唯一、关联、排序、过滤或参与门禁的核心字段。

### 1.1 数据元、旧库和目标 DDL 证据基线

物理设计必须先读取结构化证据，不重复凭印象解析 Excel 或复制旧表。当前采用以下证据层级：

| 层级 | 证据 | 设计约束 |
|---|---|---|
| 业务语义 | PRD V1.8、08数据模型、批准 ADR/决策 | 决定 Owner、聚合、不变量和排除范围 |
| 数据元 | `evidence/data-elements/manifest.json`、`semantic-elements.jsonl`、`schema-records.jsonl` | 决定已存在字段语义、来源坐标和旧结构候选；Excel 哈希变化时必须全量重建 |
| 迁移规则 | `evidence/migration/*mapping*.jsonl`、`appendices/project-order-migration-mapping.md`、`legacy-data-element-business-object-mapping.md` | 决定旧字段的`STRUCTURED/RELATION/LINEAGE/PAYLOAD`处置、来源血缘、异常分类和对账 |
| 物理实现 | 当前实现仓库未占用 Flyway 版本、批准目标 DDL | 决定最终表/列/索引/约束；不得编辑已执行迁移 |

结构化证据当前记录：197条语义来源、108个归并数据元；活动结构证据3,931行/3,908个唯一旧字段；18张核心旧表326字段曾全部有处置。以上数字只证明历史设计覆盖，不能代替当前迁移批次的逐行对账。

08a逐个覆盖Phase 2显式数据对象，`docs/traceability/domain-entity-migration-contract.json`进一步把每个对象拆为来源级显式处置。Feature表设计不得只处理项目—订单—设备核心链：凡契约来源为`CURRENT_FORWARD/STRUCTURED/RELATION/EXTERNAL_SYNC`的领域实体，必须提供字段映射、状态/字典映射、来源键、问题分类和对账；`REBUILD`必须给出重建水位；`NEW_ONLY/EXCLUDED`不得被无关旧表填充。对象级摘要中的复合策略不能直接生成迁移SQL。

### 1.2 DDL 漂移和实施门禁

`specs/001-project-delivery-platform/evidence/migration/ddl-drift-review.json`记录的DDL哈希、当前项数量及复核状态以本次V1.8校验产物为准；Q08的具体索引仍只是候选，必须经过Feature/P3-E06性能验证。隔离MySQL 8.4.10执行仅证明DDL可执行，不代表历史迁移或数据切换已获批准。因此：

ADR-0004已确认P3-E09采用只读生成逐表、逐列、逐索引/约束差异并逐项裁决的方向，不整体恢复旧DDL。ADR-0030六表差量已完成当前事实、机器校验和整体一致性独立复审，当前为`MODEL_BASELINE_READY`。P3-E09不定义迁移批准哈希，不建立Owner签署、外部附件或迁移批准流程。

ADR-0019已确认物理表按13领域编码划分，删除业务系统名称前缀`pms_`，并采用`<domain_code>_<full_domain_object_name>`；表名必须保留全部领域对象语义组件，默认使用完整英文词，仅允许ADR登记的`config`、`sn`两个表名标准缩写。字段可以在不产生业务歧义的前提下使用ADR登记的受控缩写、统一同义词并保持简洁。ADR-0019列出了当前52张物理表的逐表目标名称和首批同义字段裁决。该命名决策属于P3-E09模型输入，不等于批准旧DDL：本分册后续仍出现的`pms_*`仅表示尚待AI-MIG-000统一重建的当前证据名称，不再是目标命名。

ADR-0021在ADR-0019的52表命名基线上增加`cus_market_relation`。该表是CRM四维组合目录的CUS同步副本；`cus_customer`与`proj_project`直接保存市场部、系统部、拓展部、子行业各自编码和名称，不保存`relation_id`，也不以目录记录ID建立外键或历史链。

ADR-0022确认ADR-0019的52表是历史命名裁决范围，不是当前平台全量实施表清单。V1.8只保留PRD正式V1/V2需求映射出的目标表；COM-02、IMP-02和ACC-05（V3）不得通过数据库设计回流。机器禁止清单中的V3/OUT_OF_SCOPE表继续退出V1/V2核心DDL，跨领域引用不建立物理外键。INT-04的最小同步副本由对应Feature以前向迁移单独评审。

当前逐项登记见`ddl-item-decision-register.json`，为比较历史目录与当前DDL而保留新增、修改、移除的并集，共2,079项，覆盖当前和历史目录中表、列、约束/索引与表选项的并集事实。994项未变化字段按基线继承登记为`ACCEPT_CURRENT`，1,085项按既有ADR、ADR-0028历史清单及ADR-0030六表差量登记为`AMEND_CURRENT`，`DEFER=0`；当前DDL规模以66表、1,382列、489项DDL约束/索引和66项表选项为准。P3-E09不定义迁移批准哈希；正式独立复审已GO、模型基线已发布为`MODEL_BASELINE_READY`。完整当前入口为`ddl-item-decision-register.json`，ADR-0028确认包只作为未变化历史item的证据锚点。

- 本分册中的模型和约束是 SDS 目标契约；当前66表只是迁移核心子集，不代表平台全量模型，也不可直接作为生产迁移执行；
- `AI-MIG-000`不是通用Release门禁：只有发布包含历史数据迁移或数据切换时才适用并在验证前保持`BLOCKED`，普通功能发布记为`NOT_APPLICABLE`；适用时未经真实批次验证不得执行，且只允许在批准窗口内执行；当前不预建迁移批准状态机、批准JSON或双确认提交；
- 历史`migration-validation.json.passed=true`已过期，不得作为当前发布证据；
- 未关闭漂移前，可以实现不依赖争议 DDL 的领域代码和校验框架，但不得执行生产迁移或宣称数据切换 READY。

## 2. 基础平台字段与数据类型

### 2.1 通用字段

| 字段 | 类型 | 必填 | 规则 |
|---|---|---:|---|
| `id` | `bigint` | 是 | 基础平台分布式 ID 或既有主键策略；同一表内唯一 |
| `tenant_id` | `bigint` | 是 | 默认值不代表可绕过租户；唯一键和高频索引包含该字段 |
| `version` | `int` | 是 | 乐观锁，从 0 开始；有效更新使用 `where id=? and version=?` |
| `creator` / `updater` | `varchar(64)` | 是 | 服务端当前主体，禁止客户端伪造 |
| `create_time` / `update_time` | `datetime(3)` | 是 | 数据库或服务端统一时钟，精确到毫秒 |
| `deleted` | `bit(1)` | 是 | 仅用于允许逻辑删除的草稿/配置；历史证据表不提供普通删除 |

【建议】新建不可变历史表不使用 `deleted` 作为业务撤销手段；撤销通过新状态历史或反向业务记录表达。为兼容基础平台实体基类必须保留时，固定为 `0` 且不暴露删除 API。

### 2.2 推荐类型

| 语义 | 类型 | 说明 |
|---|---|---|
| 状态/类型/方向 | `varchar(32)` | 保存稳定代码，显示值来自字典 |
| 外部来源键 | `varchar(128)` | 与 `source_system`、`tenant_id` 组成幂等唯一键 |
| 业务编码 | `varchar(64)` | 只有明确业务编码的对象使用 |
| 数量/金额/数值 | `decimal(20,6)` | 禁止浮点；动作值同时保存方向和有符号值 |
| 日期 | `date` | 不含时间语义的起止日 |
| 时间点 | `datetime(3)` | 存储统一时区约定下的时间点；接口显式带时区 |
| 内容哈希 | `char(64)` | SHA-256 十六进制 |
| 结构化扩展 | `json` | 仅保存非核心扩展或不可变外部摘要 |
| 大文本 | `text` | 禁止存储密码、私钥、Token 或完整外部大结果 |

## 3. 命名、主键、唯一键和索引

### 3.1 命名

- 表：`<domain_code>_<full_domain_object_name>`，例如`plt_collection_task`；不得增加业务系统名称`pms`前缀。
- 主键：`pk_<table_short>`；唯一键：`uk_<table_short>_<business_semantics>`；普通索引：`idx_<table_short>_<query_semantics>`。
- 外部来源字段统一为 `source_system/source_key/source_version/source_updated_at/synced_at`。
- 项目生命周期不得统一压缩为一个 `status_code`：`current_stage` 使用 S0～S6，`lifecycle_status` 使用 ACTIVE/NORMAL_CLOSED/EXCEPTION_CLOSED，`assignment_status` 独立保存，`display_status` 只读派生；历史旧表的 `status` 不原地改义，新增映射列或兼容适配层。

### 3.2 索引顺序

1. 租户隔离列在业务查询索引首部。
2. 等值范围列在时间/排序列之前。
3. 列表索引最后包含稳定排序键 `id`，避免同时间值翻页重复或遗漏。
4. 不为低选择性 `deleted` 单独建索引；与租户、状态、业务范围组合。
5. 所有索引必须对应明确查询、唯一性或门禁，不以“可能有用”为由全字段建索引。
6. ADR-0023-Q08的“性能须下游验收、索引调整只允许前向迁移”原则继续有效；ADR-0028历史清单与ADR-0030差量共同形成当前130项候选索引，但不代表性能通过。Feature仍必须保存关键查询执行计划，P3-E06按近生产数据规模验收。

## 4. Project Delivery 表设计

适用 Requirement：PM-01～PM-11、PROJ-12、INT-01。

### 4.1 项目树

现有 `proj_project` 的 `parent_id/root_id/path/depth` 可继续承载当前邻接关系和兼容查询，但 `path/depth/root_id` 是派生字段，不得成为独立可写真值。

项目编码按ADR-0020与层级解耦：`project_code`租户内唯一且默认不可变；`code_root_id`和`project_sequence`冻结创建时的编码命名空间，项目移动时不得修改。CRM项目关联多个合同、执行单或订单时仍保持一个项目编码，商业关系通过Commerce关系对象及`DeliveryScope`表达。只有形成独立交付边界时才创建子项目，并由基础平台按`tenant_id + code_root_id`原子分配不可复用流水号。

【建议】新增 `proj_project_tree_path`：

| 字段 | 约束/索引 | 说明 |
|---|---|---|
| `tenant_id, ancestor_project_id, descendant_project_id` | 复合主键或唯一键 | 一个祖先到后代只有一条当前路径 |
| `distance` | `not null` | 自身为 0，直接子级为 1 |
| `tree_version` | 索引 | 结构变更批次/完整投影版本 |
| `project_id, parent_id_before, parent_id_after` | 记录在 `proj_project_tree_change` | 移动节点审计，不放在路径表 |

索引：

- `uk(tenant_id, ancestor_project_id, descendant_project_id)`；
- `idx(tenant_id, descendant_project_id, ancestor_project_id)` 用于反查祖先；
- `idx(tenant_id, ancestor_project_id, distance, descendant_project_id)` 用于子树分页。

移动节点事务：锁定被移动项目和目标父项目，校验目标父项目不在自身后代集合，更新邻接真值并生成 `tree_change_batch_id`；路径投影在同事务或可靠事件中切换到完整版本。禁止逐节点 HTTP 递归更新。

历史`pm_project_group`及`pm_project_group_relationship`只允许用于解析项目—合同技术关系，不迁移为父子树、项目组合或多期群组。旧项目没有明确父子证据时迁为独立根节点；不得按名称、地区、项目组或编号推断层级。

### 4.1.1 数据元核心字段落位

| 语义数据元 | 目标落位 | 物理约束/迁移规则 |
|---|---|---|
| 项目编码/名称/客户项目名称 | `proj_project.project_code/project_name/customer_project_name` | CRM创建默认沿用CRM项目编码；多合同/订单不改码；子项目使用命名空间永久流水号；历史空名称进入待补问题，不以编码伪造名称 |
| 客户 | `proj_project.customer_id` | 通过客户外部键解析；只按名称多匹配时生成迁移问题 |
| 市场行业四维分类 | `market_code/market_name/system_code/system_name/expend_code/expend_name/industry_code/industry_name` | CRM权威同步；项目直接保存八个快照字段，不保存`relation_id`，历史未知值进入迁移问题 |
| 实施方式/重大项目级别 | `implementation_mode_code/major_project_level_code` | 版本化字典映射；未知值进入待映射，不写默认值 |
| 办事处、公司、部门 | 项目组织关系表，字段统一`company_*`、`department_*` | 公司—部门作为同一关系行共同解析和对账；禁止继续生成`org_*`目标字段 |
| 项目状态与生命周期时间 | `current_stage/lifecycle_status/assignment_status`及独立发生时间字段 | `current_stage`仅允许S0～S6；`lifecycle_status`仅允许ACTIVE/NORMAL_CLOSED/EXCEPTION_CLOSED；`display_status`由服务端派生；旧时间不覆盖`create_time/update_time` |

项目主表不得以单一状态字段表达多个业务维度：

| 字段 | 类型/取值 | 约束 |
|---|---|---|
| `current_stage` | 稳定代码S0～S6 | 由阶段门禁命令迁移；正常闭环后保持S6 |
| `lifecycle_status` | ACTIVE/NORMAL_CLOSED/EXCEPTION_CLOSED | CLO-02唯一写入NORMAL_CLOSED；PM-10异常关闭写入EXCEPTION_CLOSED；不得由字典新增可执行值 |
| `assignment_status` | 基础平台字典映射的稳定代码 | 与项目经理/执行指派独立迁移，不改变生命周期 |
| `display_status` | 派生只读值 | 由上述字段及查询上下文计算，不落交易真值；不得被客户端写入 |

### 4.2 任务树与依赖

现有 `proj_project_task` 保存当前父关系；`proj_task_dependency` 只保存任务依赖，二者不得混用。

【建议】新增 `proj_task_tree_path`，结构和索引与项目路径表相同，并增加 `project_id` 作为高频过滤列。任务移动只修改任务父关系和路径投影，不自动创建/删除依赖。

ADR-0029定义工作绑定逻辑边界，ADR-0030进一步确认“模板定义、实例冻结、判定追加”三层最小物理模型；不把可执行绑定只塞入模板JSON，也不把目标业务正文复制到ProjectTask：

| 目标表 | 关键字段 | 约束与索引 |
|---|---|---|
| `proj_project_template_task_definition` | `template_revision_id/stage_definition_key/task_definition_key/parent_task_definition_key/name/sort_order/work_binding_type_code/target_context_code/target_object_type/target_object_key/component_key/dynamic_form_revision_id/approval_definition_key/binding_config/permission_policy_ref/completion_rule_type_code/completion_rule_config/gate_ref/definition_version` | `uk(tenant_id, template_revision_id, task_definition_key)`；`idx(tenant_id, template_revision_id, stage_definition_key, parent_task_definition_key, sort_order)`；模板revision发布后整行不可改 |
| `proj_project_task_execution_contract` | `project_task_id/template_task_definition_id/work_binding_type_code/target_context_code/target_object_type/target_object_key/component_key/dynamic_form_revision_id/approval_instance_id/binding_parameter_snapshot/permission_policy_ref/completion_rule_type_code/completion_rule_snapshot/gate_ref/source_definition_version/contract_version/effective_from/effective_to/current_marker/version` | `uk(tenant_id, project_task_id, contract_version)`；生成列`current_marker`仅在`effective_to is null`时取1，并以`uk(tenant_id, project_task_id, current_marker)`保证至多一个当前契约；`idx(tenant_id, target_context_code, target_object_type, target_object_key)`只服务获权反查 |
| `proj_project_task_completion_evaluation` | `project_task_id/execution_contract_id/task_version/contract_version/fact_context_code/fact_object_type/fact_object_key/fact_version/evaluation_result_code/unmet_item_snapshot/gate_snapshot_ref/command_id/idempotency_key/evaluated_by/evaluated_at` | 追加写；`uk(tenant_id, project_task_id, idempotency_key)`；`idx(tenant_id, project_task_id, evaluated_at, id)`；不得更新为另一结论或软删除 |

约束规则：

1. `TASK_NATIVE`行的目标Context、对象、组件、表单和审批字段必须为空；其他类型必须按绑定类型填写唯一受控目标，不允许任意前端路径、脚本或Repository名。`COMPOSITE`的子视图只保存在有Schema版本的`binding_config/binding_parameter_snapshot`中，并逐项引用受信任组件或对象，不能逃逸Owner API。
2. 数据库唯一键只能保证“至多一个当前执行契约”；项目任务创建、模板实例化和受控换绑事务必须在提交前保证“恰好一个”。换绑先校验旧版本、生成新契约并关闭旧有效区间，不原位覆盖。
3. WorkBinding、PermissionPolicy、CompletionRule和GateRef作为一个执行契约版本原子冻结。非`TASK_NATIVE`完成命令必须同时提交任务版本、执行契约版本、目标事实版本和幂等键；服务端重取Owner事实后写`completion_evaluation`，成功判定与ProjectTask状态迁移同事务完成。
4. `proj_project_task_completion_evaluation`保存事实引用、版本、结论和未满足项快照，不保存外域业务正文。事件只引用成功判定ID；通知、HTTP成功、组件渲染成功或DAC回调成功都不能代替完成判定。
5. 现有`pms_project_task`前向迁入`proj_project_task`后，对没有已批准业务绑定证据的存量任务生成`TASK_NATIVE`契约版本1；不得按任务名称、菜单、历史URL或模块名猜测其他绑定。模板任务定义和非原生绑定属于`NEW_ONLY`，由新模板发布或经批准的显式换绑命令产生。

本节只批准Phase 2目标物理契约。实际建表、存量回填、约束执行和查询计划必须以新Flyway差量进入P3-E09；当前DDL未变，不得把本节误写为Schema已实施。

### 4.3 项目版本与快照

| 目标表 | 作用 | 关键约束 |
|---|---|---|
| `proj_project_tree_change` | 项目移动批次、前后父节点、原因、操作者、结果 | 追加写；批次号唯一 |
| `proj_task_tree_change` | 任务移动批次 | 追加写；项目和任务范围必填 |
| `proj_project_stage_snapshot` | 项目阶段切换时的阶段、模板版本和项目状态快照 | `uk(tenant_id, project_id, stage_code, snapshot_no)`；由PROJ维护 |
| `imp_implementation_readiness_snapshot` | 实施就绪门禁输入、检查结果和来源版本 | `uk(tenant_id, project_id, readiness_type, snapshot_no)`；由IMP维护，PROJ只引用结果 |
| `proj_project_member_assignment` | 角色成员当前/历史有效期 | 同一项目/角色/用户的有效区间由应用服务防重叠 |
| `proj_project_template_revision` | 模板发布版本 | `uk(tenant_id, template_id, revision_no)`；发布后只读 |
| `proj_project_portfolio` | 项目组合身份、类型、状态和当前发布版本 | `uk(tenant_id, portfolio_code)`；不改变成员项目Owner |
| `proj_project_portfolio_member` | 组合成员、主组合标识、关系类型和有效区间 | 同组合/项目/关系有效区间不重叠；一个项目的默认主组合由受控唯一约束保证 |
| `proj_project_portfolio_revision` | 组合规则、成员快照和发布版本 | `uk(tenant_id, portfolio_id, revision_no)`；发布后不可变 |

### 4.4 PM-05 转销与 PM-06 多期关系

| 需求 | 表 | 关键字段 | 约束/索引 |
|---|---|---|---|
| PM-05 | `proj_project_conversion` | `source_project_id/target_project_id/formal_sales_business_id/status_code/idempotency_key/summary_json/version` | `uk(tenant_id, source_project_id, formal_sales_business_id)`；应用与状态机保证同一源项目只有一个生效目标 |
| PM-05 | `proj_project_conversion_item` | `conversion_id/source_context/source_object_type/source_object_id/source_version/handling_mode_code/target_object_id/result_code/failure_code` | `uk(tenant_id, conversion_id, source_context, source_object_type, source_object_id, source_version)`；逐项追加/重试，不覆盖成功项 |
| PM-05 | `proj_project_conversion_device` | `conversion_id/device_id/disposition_code/assignment_version_before/target_assignment_version/result_code` | `uk(tenant_id, conversion_id, device_id)`；设备归属由 AST 当前唯一表执行，结果只保存引用 |
| PM-06 | `proj_multi_phase_project_group` | `group_code/relation_type_code/name/version/status_code` | `uk(tenant_id, group_code)`；关系类型字典只扩展分类，不绕过关系守卫 |
| PM-06 | `proj_multi_phase_project_member` | `group_id/project_id/relation_type_code/phase_no/display_order/effective_from/effective_to/member_version` | 当前成员按 `tenant_id+relation_type_code+project_id` 唯一；群组内有效期次号唯一 |
| PM-06 | `proj_project_cross_phase_reference` | `group_id/source_project_id/source_object_type/source_object_id/source_version/target_project_id/derived_object_id/reference_mode_code` | 来源版本与目标项目唯一；派生对象必须记录来源，不级联修改源对象 |

`proj_project_conversion` 与对象项采用过程聚合+逐项结果：正式项目未创建成功不生成转销批次；转销完成与源项目只读归档由同一 Project Delivery 应用服务在门禁通过后提交。跨 Context 设备归属、文件/实施对象引用通过 Saga 保存确认，不使用跨库事务或直接更新外域表。

多期群组成员变更按 `group.version + memberVersion` 乐观锁校验；加入前检查同关系类型唯一群组、期次唯一和有向关系无环。移出只关闭有效区间，不删除项目和历史引用。

### 4.5 Preparation & Solution

适用 Requirement：PRE-01～PRE-05、PLN-01～PLN-04、SCH-01～SCH-05、SOL-01。

| 聚合 | 主表 | 版本/明细表 | 关键约束 |
|---|---|---|---|
| Preparation | `sol_preparation` | `sol_preparation_item`、`sol_dynamic_form_instance` | 项目+准备类型+业务版本唯一；提交冻结 formSchemaVersion |
| ConstructionPlan | `sol_construction_plan` | `sol_construction_plan_revision`、`sol_construction_plan_item`、`sol_construction_plan_change` | `uk(tenant_id, plan_id, revision_no)`；批准 revision 只读 |
| Solution | `sol_solution` | `sol_solution_revision`、`sol_solution_review` | 发布 revision 只读；文件仅保存 FileReference |
| DynamicFormSchema | `sol_dynamic_form_schema` | `sol_dynamic_form_schema_revision` | V2；schema revision 发布后不可覆盖 |

历史 `pms_eng_site_survey/requirement/resource_ready/briefing/solution/form_*` 可作为迁移来源；新应用服务按 Preparation/Solution Owner 访问，不允许表单引擎直接写 Project 状态。

## 5. Asset 设备归属与维保基本事实

适用 Requirement：EQP-01～EQP-05、EQP-07、AST-01～AST-02、INT-02、INT-06。

### 5.1 当前唯一归属与历史

新增或规范化以下表：

| 表 | 关键字段 | 约束 |
|---|---|---|
| `ast_device_current_assignment` | `device_id, project_id, assignment_type_code, assigned_at, assignment_version` | `uk(tenant_id, device_id)`，保证一个设备只有一个当前项目 |
| `ast_device_assignment_history` | `device_id, project_id, effective_from, effective_to, change_reason_code, change_batch_id` | `idx(tenant_id, device_id, effective_from)`；区间不得重叠 |
| `ast_device_project_ancestor` | `device_id, assigned_project_id, ancestor_project_id, distance, tree_version, assignment_version` | `uk(tenant_id, device_id, ancestor_project_id)`；可重建投影 |
| `ast_device_component_relation` | `chassis_device_id, slot_code, card_device_id, card_model_code, source_code, effective_from, effective_to, parse_revision_id` | 当前关系使用生成标记保证`tenant+chassis+slot`唯一；换板关闭旧区间并新增，解析/人工绑定均保留证据 |

归属变更事务按设备 ID 加锁：读取当前行和版本，关闭对应历史区间，插入新历史，更新当前行，然后通过 Outbox 请求重建祖先投影。项目树移动触发受影响子树内设备投影按批次重算。统计读取返回 `treeVersion/assignmentVersion` 水位，避免把投影延迟误报为真实归属变化。

### 5.2 MaintenanceFact

新增 `ast_maintenance_fact`，至少包括：

- `device_id`、可选 `project_id_snapshot`；
- `start_date/end_date/service_level_code`；
- `calculated_status_code/calculated_at/rule_version`；
- `source_system/source_key/source_version/synced_at`；
- `legacy_record_type/legacy_record_id/migration_quality_code` 用于历史迁移追溯。

禁止新写 `renew_years/renew_end_date/manual_override/override_by` 等独立续保动作字段。客观状态按日期和已批准规则重新计算；历史人工覆盖值不直接迁移为客观真值，需标记待对账。

### 5.3 设备身份与替换

现有 `ast_device`、`ast_device_version`、`ast_device_config_log` 可按 Device/DeviceArchive 目标模型兼容；新增 `source_*`、同步水位和字段 Owner 映射时使用前向迁移。

【建议】RMA 使用 `ast_rma_replacement`，保存 `old_device_id/new_device_id/replacement_at/reason_code/evidence_file_ref`，对原设备只追加替换关系，不修改序列号历史。

## 6. Implementation Execution 与 Acceptance 表设计

### 6.1 实施执行

| 聚合 | 主表 | 明细/历史表 | 关键数据库约束 |
|---|---|---|---|
| ArrivalAcceptance | `imp_arrival_acceptance` | `imp_arrival_line`、`imp_arrival_difference` | 批次内来源行唯一；数量非负；差异通过独立记录表达 |
| InstallationRecord | `imp_installation_record` | `imp_installation_item`、`imp_installation_evidence` | 设备/安装批次索引；历史记录不覆盖 |
| ConfigurationCollectionResult | `imp_configuration_collection_result` | `imp_configuration_collection_parse_attempt` | `uk(tenant_id, collection_task_id, result_type_code, result_version_no)`；根保存项目/设备快照、脚本/解析器版本、原始整机Log文件引用及哈希；解析尝试追加 |
| ConfigurationCollectionResult解析候选 | `imp_configuration_collection_result` | `imp_configuration_component_candidate` | 保存机框SN、槽位、板卡SN/型号、解析 revision、解析器版本、板卡配置引用和匹配状态；不能覆盖原始Log或直接改写已生效设备关系 |
| JointDebuggingResult | `imp_joint_debugging_result` | `imp_joint_debugging_item` | 业务任务 + 结果版本唯一 |
| ImplementationRisk | `imp_risk` | `imp_risk_treatment` | 状态迁移另记历史；不与 CUT risk 共表 |
| ImplementationQualityCheck | `imp_quality_check` | `imp_quality_item`、`imp_quality_remediation`、`imp_quality_review` | 整改与复核追加；当前状态由聚合根维护 |
| DeliveryEvidence | `imp_delivery_evidence` | `imp_delivery_evidence_revision` | `uk(tenant_id, evidence_id, revision_no)`；文件引用+哈希 |

旧 `pms_eng_*` 表按字段语义映射到新 Owner；物理模块无需立即拆库，但新 Repository 必须按 Context 包隔离。复用旧表时以兼容视图/适配器映射稳定状态代码，不直接重解释历史 tinyint。

### 6.2 验收与闭环

| 聚合 | 主表 | 支撑表 | 关键约束 |
|---|---|---|---|
| Acceptance | `acc_acceptance` | `acc_acceptance_item`、`acc_confirmation` | 验收 revision/客户确认追加；原始实施证据只引用 |
| SatisfactionCollection | `acc_satisfaction_collection_task` | `acc_satisfaction_questionnaire`、`acc_satisfaction_response`、`acc_satisfaction_result` | 任务冻结模板/阈值；答卷、签字和判定只追加；整改重收使用新任务和新问卷版本 |
| DeliveryArtifact | `acc_delivery_artifact` | `acc_artifact_review`、`acc_archive_record` | 文件 revision + 清单项唯一；归档记录不可覆盖 |
| ProjectClosure | `acc_project_closure` | `acc_closure_gate_snapshot`、`acc_closure_review` | 快照号唯一；完成后不提供更新接口 |
| ServiceHandover | `acc_service_handover` | `acc_handover_item`、`acc_handover_result` | V2静态交接快照；不含续保年限、续保结束日期、续保状态或持续跟踪对象 |

历史 `pms_acc_maintenance_transition` 不改表。前向迁移只把可以证明的交接字段映射到新表，并保存 `legacy_record_id`；续保字段不进入新模型。

## 7. Cutover、Inspection 与服务状态

| Context | 目标表组 | 关键约束与索引 |
|---|---|---|
| Cutover | `cut_task`、`cut_assessment`、`cut_plan_revision`、`cut_step`、`cut_cutover_support_arrangement`、`cut_cutover_closure`；CUT-07 Feature前向表见7.2 | 任务内计划revision唯一；步骤只属于批准方案内容；保障人员安排从属于方案且联系人类变更留审计、职责变更新建revision；P6闭环一任务一版本递增，提交后只读 |
| Inspection | `srv_inspection_task`、`srv_inspection_rule`、`srv_inspection_rule_revision`、`srv_inspection_task_rule_snapshot`、`srv_inspection_report_revision`、`srv_service_issue`、`srv_service_issue_remediation` | 在线/离线模式检查；任务规则快照唯一；报告 revision 只追加 |
| Service Operations | `srv_service_status`、`srv_service_handover_reference` | 客观服务状态按设备+来源唯一；不新建续保空间/续保率表 |

现有 `pms_srv_maintenance` 冻结为兼容来源，不新增菜单/API 写入；可证明的客观字段迁移到 `ast_maintenance_fact`。

现有通用工单与工时表不得继续作为当前写模型。需求方于 2026-08-13 确认 `pm_project_maintenance` 全表不迁移：不得依据字段相似性将其行分类为CUT闭环、未来WO-06工单、历史工单、历史工时或其他对象，不生成字段级绑定、状态映射或目标写入。机器证据仅保留该表的 `EXCLUDED/NO_MIGRATION` 排除审计；行数与提取批次 SHA-256 在未有可核验提取证据时保持待采集，不得伪造。

CUT-11、CutoverSupportTask及责任区间不属于当前模型，不建立对象、表、外键、索引或迁移映射。WO-06只保留为工单领域V3候选；未来须先重新确认Owner、创建入口、角色、状态、责任、证据、关闭和历史来源，再以独立变更建模、评审和执行。

`cut_cutover_support_arrangement`只是`cut_plan_revision`从属明细，不得拥有派单/接管/转单/挂起状态。联系人、联系方式、到位时间变化在原批准方案下更新并写业务审计；角色或任务职责变化不得直接覆盖，必须创建新方案revision并按原人工等级重新进入P5。

`cut_cutover_closure`保存P6闭环快照。P4操作/验证/回退步骤只存在于方案revision，不复制为执行步骤表；当前不建立`cut_execution_step`或`cut_observation`。旧实现字段仅在能逐字段证明属于P6结果时迁移到闭环记录，无法证明的步骤/观察字段不进入当前目标。

CUT-03使用CutoverTask从属的三张版本表，不把清单塞入`cut_plan_revision`，也不建立采集阶段、通用工单或结果中转页：

| 目标表 | 关键字段 | 约束与索引 |
|---|---|---|
| `cut_cutover_checklist` | `cutover_task_id/assessment_id/assessment_version/checklist_version/status_code/input_snapshot/input_snapshot_hash/config_revision_snapshot/match_trace/config_gap_snapshot/submitted_by/submitted_at/invalidated_at/invalidated_reason/current_marker/version` | `uk(tenant_id, cutover_task_id, checklist_version)`；生成列`current_marker`在`invalidated_at is null`时取1，`uk(tenant_id, cutover_task_id, current_marker)`保证一个当前版本；`status_code`只允许PRD已有草稿、已提交、已失效语义 |
| `cut_cutover_checklist_item` | `checklist_id/stable_item_key/item_definition_id/item_definition_version/item_type_code/item_name/item_description/interface_format_code/interface_schema_snapshot/display_condition_snapshot/work_mode_code/required_flag/source_code/device_id/command_template_id/matched_rule_id/matched_rule_version/applicable_flag/custom_creator_user_id/sort_order/version` | `uk(tenant_id, checklist_id, stable_item_key)`；`idx(tenant_id, checklist_id, item_type_code, applicable_flag, sort_order)`；系统必填项不可删除，自定义项在提交前只能由创建人移出适用范围 |
| `cut_cutover_checklist_item_result` | `checklist_item_id/result_version/result_source_code/answer_snapshot/fact_description/collection_task_id/collection_result_reference_id/collection_result_version/external_source_code/query_condition_snapshot/queried_at/load_failure_code/manual_evidence_file_reference/selection_started_at/selection_ended_at/selected_by/selection_reason_code/current_marker/created_by/created_at` | 结果正文追加且不可覆盖；`uk(tenant_id, checklist_item_id, result_version)`；生成列`current_marker`仅在`selection_ended_at is null`时取1，`uk(tenant_id, checklist_item_id, current_marker)`保证每项至多一个当前选择；`check(selection_ended_at is null or selection_ended_at >= selection_started_at)`；`idx(tenant_id, collection_task_id)`用于回调关联；人工降级和自动失败记录并存 |

`result_source_code`只表达CUT选择的直接填写、自动采集、外部加载或人工降级来源；本表不保存DAC的`mapped_status_code`、`external_status_raw`或调度状态副本。技术状态始终从`plt_collection_task`及结果引用读取，CUT只在验签、任务/清单/采集项/设备/结果版本匹配后选择一个结果版本，并按采集项规则解释是否满足。结果创建时写`selection_started_at`并成为当前选择；切换结果时在同一事务中锁定当前行、写旧行`selection_ended_at`并插入新结果版本。结果载荷、来源与证据字段不可更新，只有受控切换命令可以关闭选择区间。

### 7.2 CUT-07后台配置前向表

以下表名是ADR-0031批准的Feature目标，不属于当前核心DDL；物理表由CUT-07 Feature前向迁移确定。

| 目标表 | 关键字段 | 约束与索引 |
|---|---|---|
| `cut_cutover_configuration_revision` | `configuration_code/revision_no/status_code/effective_from/effective_to/published_by/published_at/disabled_at/dictionary_snapshot/version` | `uk(tenant_id, configuration_code, revision_no)`；发布版本不可覆盖；消费实例冻结revision和字典代码/名称快照 |
| `cut_cutover_checklist_item_definition_revision` | `configuration_revision_id/stable_item_key/item_definition_version/item_type_code/item_name/interface_format_code/interface_schema/work_mode_code/required_flag/external_source_ref/status_code/sort_order/version` | `uk(tenant_id, configuration_revision_id, stable_item_key, item_definition_version)`；稳定项键不可复用为不同含义 |
| `cut_cutover_checklist_binding_rule_revision` | `configuration_revision_id/item_definition_id/item_definition_version/rule_version/dimension_condition_snapshot/priority/status_code/version` | `uk(tenant_id, configuration_revision_id, item_definition_id, rule_version)`；维度条件必须可判定且引用启用的基础平台字典值 |

割接类型、组网模式、设备类型使用基础平台可配置字典，不另建业务主表。新表只能由CUT-07 Feature创建；不从`pms_cut_plan`、`pms_cut_risk`或旧清单推断历史配置版本。

草稿重匹配使用`checklist_version + input_snapshot_hash`。稳定`stable_item_key`继续适用时可保留当前答案；移出适用范围的项把`applicable_flag`置为0并留审计，不进入提交；新增项写入同一草稿。已提交清单不可原位重匹配，输入发生受控变化时创建新版本并将旧版标记失效。D级任务禁止创建这三类记录。

当前`pms_cut_risk`只作为可证明的旧风险/调研项候选来源：允许迁任务引用、原编码/名称/类型、原说明及可证明填写事实；不得推断采集项定义版本、界面Schema、绑定规则、必填性、CollectionTask、自动结果、业务通过或配置缺口。不能完成字段级证明的记录保留兼容来源证据或迁移问题。新清单根、匹配版本和结果引用由前向Feature产生。

本节完成Phase 2物理设计，不修改当前DDL。三张表及`proj_*`任务执行契约表进入正式Flyway前必须重新执行P3-E09，且不触发`AI-MIG-000`；只有Release同时包含历史迁移或数据切换时，`AI-MIG-000`才在Release前适用并限定批准窗口。

## 8. Customer、Commerce、Resource 与 Knowledge

### 8.1 外部主数据通用同步

每个同步 Owner Context 复用以下结构：

| 表 | 作用 | 关键约束 |
|---|---|---|
| `ast_asset_sync_batch` | 一次拉取/推送批次、水位、结果和计数 | `uk(tenant_id, source_system, interface_code, batch_key)` |
| `ast_asset_sync_item` | 单对象来源键、摘要、处理结果和错误码 | `uk(tenant_id, batch_id, source_key)` |
| `plt_integration_reconciliation` | 对账范围、差异、处理和最终结果 | 同一来源水位/范围幂等 |
| `plt_migration_source_record` | 一次性迁移的逐源行原值、来源键、抽取批次和校验和 | `uk(tenant_id, source_system, source_table, source_record_key, extract_batch_id)`；`source_payload`不可变 |
| `plt_external_key_mapping` | 旧主键/外部键到目标 Context、对象和 ID 的映射 | 一个来源键只能有一个当前有效目标；归并时保留全部来源键 |
| `plt_migration_issue` | 重复、多义、空键、关系孤儿、状态/字典未知和数量缺失 | 问题关闭必须引用处理人、规则版本和目标结果；未关闭问题不得静默计入有效业务 |
| `plt_migration_batch` | 抽取清单、输入哈希、规则/DDL版本、计数和状态 | 批次结果不可覆盖；重跑生成新批次并引用前批次 |

每条旧记录必须先写不可变来源证据，再满足“形成目标结构化事实”或“形成明确迁移问题”之一。`source_payload`不是业务字段缺失的替代方案；需要查询、关联、统计、权限、同步或审计的字段必须落正式列/关系表。

业务副本表仍保存在对应 Owner Context，不集中塞入集成表。

INT-05/INT-09 复用基础平台现有用户、部门、岗位主数据，已有 `plt_sync_batch` 承载同步批次/水位，`plt_external_key_mapping` 承载来源键映射。当前不建立独立目录快照表，也不因本决策新增替代表；若基础平台主数据不属于当前核心迁移 DDL，由相应 Feature 前向实现。

### 8.2 目标表组

| Context | 目标表组 | 关键约束 |
|---|---|---|
| Customer | `cus_customer`、`cus_market_relation`、`cus_customer_contact`、`cus_project_customer_contact_relation`、`cus_customer_relationship_snapshot`；CUS-02 Feature前向表见8.2.1 | CRM 对象按 `source_system+source_key` 唯一；临时客户另有 `origin_code`；四维组合目录与客户/项目八字段快照分离 |
| Commerce | `com_contract`、`com_sales_order`、`com_order_line`、`com_delivery_scope`、`com_delivery_scope_detail`、`com_fulfillment_snapshot`、`com_reconciliation_record` | ERP合同按所属公司+合同编号；订单头与合同为关系表语义，不能固化唯一合同；ERP订单/行按稳定业务键+来源版本唯一；CRM经营引用与履约回执单独存 source mapping；范围主记录至少含订单行、项目、`allocated_qty`、`scope_status`及来源证据，范围明细保存地点、产品/设备类型、数量和可选批次 |
| Resource | `res_supplier`、`res_qualification`、`res_subcontract_request`、`res_payment_gate` | 资质版本追加；财务结果只保存引用和回写状态 |
| Knowledge | `TechnicalNoticeReference`逻辑对象；物理表由INT-04 Feature前向迁移确定 | V2公告按ITR来源键+版本唯一，只保存同步副本和业务引用；4张V3治理表不进入核心迁移DDL |

`pms_eng_announcement`和`pms_eng_announcement_check`只作为历史来源证据保留。V1/V2新菜单和API只读取INT-04 Feature批准的ITR同步副本；本地创建记录不得混入外部主数据结果，也不得提前创建V3治理表。

### 8.2.1 CUS-02服务等级前向表

`cus_customer_service_level_revision`是ADR-0031批准的Feature目标，物理表由CUS-02 Feature前向迁移确定，不属于当前核心DDL。关键字段为`customer_id/service_level_code/policy_snapshot/effective_from/effective_to/current_marker/change_reason/evidence_ref/approver_id/version`；`uk(tenant_id, customer_id, version)`保存版本，生成列`current_marker`仅在`effective_to is null`时取1，`uk(tenant_id, customer_id, current_marker)`保证同一客户至多一个当前等级，并校验结束时间不早于开始时间。等级代码使用基础平台字典；无可靠历史来源，不从客户、联系人或关系快照反推等级。

### 8.3 项目—合同—订单行—设备迁移主链

历史数据结论对应到当前 Context 命名如下；Feature DDL 必须保存显式映射，不能因表名前缀调整丢失语义：

```text
proj_project
  -> com_delivery_scope(project_id, order_line_id, allocated_qty, scope_status)
       -> com_delivery_scope_detail(location, product/device type, allocated_qty, delivery_batch_no)
  -> com_order_line
  -> com_sales_order
  -> order-contract relation
  -> com_contract(company_code + contract_no)
```

强制规则：

1. 项目与合同、合同与订单均按多对多关系建模；合同号不能脱离所属公司作为全局唯一键。
2. 订单行实施范围是项目交付最小权威分配粒度。同一订单行拆给多个项目时，必须校验有效分配量合计；缺分配量使用待补数量状态，不进入完成率、交付量和验收。
3. 同一实际承接项目节点与同一订单行同一时点只有一条当前`com_delivery_scope`主记录；主记录可有多条`com_delivery_scope_detail`，明细数量合计必须等于主记录分配数量。独立交付边界应拆为子项目，不通过重复当前主记录表达。
4. CRM执行单、特殊合并批次和订单变更只保存辅助血缘；普通订单—执行单关系默认就是主执行单关系，但不要求唯一。特殊合并下单的实际订单可关联多个执行单号，批次上的来源主执行单不构成订单级唯一约束；`-L/-C/-his`后缀不能单独建立正式关系。
5. `fb_contract`映射发货合同归属，不生成合同主档；回款来源、ERP合同关系和发货归属分别保留。
6. SN 主档、发货事件、合同维度设备关系、RMA替换和项目归属分别落表；每条发货/生命周期源记录保留，不能因SN重复删除。
7. 设备当前归属由不重叠的历史区间计算；旧`pm_project_shipment`只有项目、SN、时间和转移证据可完整解析时才形成正式当前归属，否则生成迁移问题。

## 9. Device Access & Collection 关键表

适用 Requirement：INT-12、EXE-03～EXE-04、CUT-06、INS-02、INS-04、NFR-02。

### 9.1 `plt_device_credential`

| 字段 | 约束 | 说明 |
|---|---|---|
| `device_id` | 索引 | 只允许授权范围内设备 |
| `protocol_code` | 非空 | 可配置协议分类，不含密码 |
| `username_ciphertext` | 可空、密文 | 用户名如属敏感信息同样加密 |
| `secret_ciphertext` | 非空、密文 | 密码/私钥/Token 只保存密文或密钥服务引用 |
| `key_version` | 非空 | 支持密钥轮换 |
| `secret_fingerprint` | 索引可选 | 不可逆摘要，用于查重/轮换，不可还原 |
| `creator_user_id` | 非空 | 默认私有授权主体 |
| `credential_version` | 非空 | 轮换生成新版本，不覆盖使用历史 |
| `status_code` | 非空 | 只允许状态机改变 |

禁止字段：plaintext password、可回显私钥、完整 Token、可解密内容日志。若采用外部密钥服务，表中以 `secret_ref` 替代密文字段。

### 9.2 `plt_credential_grant`

字段包括 `credential_id/user_id/device_id/protocol_code/command_template_id/effective_from/effective_to/status_code`。唯一性覆盖凭证版本和五元组；创建人私有使用权由系统内建规则表达，不通过“空授权=全员”表达。

### 9.3 `plt_collection_task`

关键字段：

- `source_context/source_object_type/source_object_id`；
- `device_id/command_template_id/connection_mode_code`；
- `authentication_mode_code`；凭证模式保存 `credential_id/credential_version/grant_snapshot_hash`，临时输入模式三者为空；
- `temporary_username`，仅临时输入且未保存为凭证时保存，用于审计；临时密码没有数据库字段；
- `completion_mode_code/required_consumer_context/required_consumer_object_type/required_consumer_object_id`，任务创建后冻结；
- `consumed_result_version/consumed_at`，仅收到匹配的业务消费确认后写入；
- `idempotency_key`，`uk(tenant_id, source_context, idempotency_key)`；
- `external_task_id/external_status_raw/mapped_status_code`；
- `requested_by/requested_at/dispatched_at/completed_at`；
- `result_reference_id/version`。

临时登录用户名保存在 `temporary_username` 用于审计；临时密码没有数据库字段，只在受控同步调用链内存中存在。`saveAsCredential=true` 时，凭证、创建人默认授权与任务在一个业务命令中原子落库；任务改存新凭证 ID、版本及授权快照，`temporary_username` 为空。加密或凭证落库失败时不创建任务。

### 9.4 下发、回调与结果

| 表 | 唯一/幂等约束 | 保留内容 |
|---|---|---|
| `plt_dispatch_attempt` | `uk(tenant_id, collection_task_id, attempt_no)` | 请求摘要、外部任务号、超时/响应分类，不含秘密 |
| `plt_callback_record` | `uk(tenant_id, provider_code, callback_id)`；无 callbackId 时使用受控摘要键 | 外部状态原值、摘要、接收时间、处理结果和冲突原因 |
| `plt_collection_result_reference` | 任务+结果类型+版本唯一 | 外部对象键、FileArtifact 引用、哈希、大小和访问范围 |
| `plt_collection_result_consumption` | `uk(tenant_id, collection_task_id, consumer_context, consumer_object_type, consumer_object_id, result_version)` | 消费方、业务对象、结果版本、消费结论和时间；成功完成只认与任务冻结消费者匹配的记录 |

## 10. 文件、事件、幂等和状态历史支撑表

以下表是 Phase 2 公共实现支撑，不拥有业务聚合状态：

| 表 | 作用 | 核心约束 |
|---|---|---|
| `plt_file_artifact` | 稳定文件身份 | `uk(tenant_id, artifact_code)` |
| `plt_file_version` | 内容版本、哈希、存储键、扫描状态 | `uk(tenant_id, artifact_id, version_no)`；`content_hash` 索引 |
| `plt_file_reference` | 业务对象到文件版本的引用 | `uk(tenant_id, context_code, object_type, object_id, purpose_code, artifact_id, version_no)` |
| `plt_state_transition` | 状态前后值、命令、主体、原因和结果 | 追加写；按聚合 ID + 时间索引 |
| `plt_idempotency_record` | 接口幂等键、请求摘要、处理状态和响应引用 | `uk(tenant_id, scope_code, idempotency_key)` |
| `plt_outbox_event` | 事务内待发布事件 | `event_id` 全局唯一；按状态/下次重试时间索引 |
| `plt_inbox_message` | Consumer 去重和处理结果 | `uk(tenant_id, consumer_code, event_id)` |
| `plt_operation_audit` | 业务操作、权限决策和敏感动作审计 | 追加写；详情先脱敏再落库 |
| `plt_todo` | 统一待办身份、业务引用和同步状态 | 业务对象+节点+责任人+版本幂等；待办完成不能直接改业务状态 |
| `plt_authorization_grant` | 通用授权范围、有效期、撤销和来源 | 主体+资源+动作+范围+有效区间唯一；不代替 DAC 凭证授权 |
| `plt_change_request` | 项目变更申请、差异快照、审批引用和执行结果 | 申请 revision 只追加；变更执行按目标聚合版本幂等 |
| `ana_metric_definition` | 【建议】指标代码、口径版本、单位、粒度和来源 | 只有口径模型获批后创建；同一指标版本不可覆盖；不得从旧报表名称猜测公式 |
| `ana_metric_snapshot` | 指标代码、口径版本、水位、范围和结果快照 | `uk(tenant_id, metric_code, metric_version, scope_hash, snapshot_at)`；不可回写交易状态 |
| `ana_portfolio_projection` | 组合维度的可重建经营查询投影 | `uk(tenant_id, portfolio_id, metric_version, data_watermark)`；返回权限范围哈希 |

Word 文档正文不做内容级审计，但文件身份、版本替换、下载、归档和业务审批动作仍按业务要求留痕。

## 11. 状态代码与字典表边界

基础平台字典保存状态名称、颜色、排序和可见性；业务表保存稳定 `status_code`。状态机定义代码集合和合法迁移，不能通过新增字典项获得可执行迁移。

历史 tinyint 状态迁移方式：

1. 保留旧 `status`；
2. 新增 `status_code` 或建立目标新表；
3. 使用版本化映射回填，并保存 `legacy_status_value/mapping_version`；
4. 未知值进入 `LEGACY_UNKNOWN` 兼容状态，只读且必须对账；
5. 新写只使用稳定代码和 transition command。

## 12. 前向迁移与兼容发布

### 12.1 通用步骤

1. `EXPAND`：新增表、可空列、索引和兼容读取，不修改已执行迁移。
2. `BACKFILL`：按批次回填，记录来源、游标、成功/失败数和数据校验摘要。
3. `VERIFY`：校验总数、唯一性、孤儿引用、状态映射、哈希和关键业务抽样。
4. `DUAL-RUN`：必要时新旧读模型并行比对；双写只能由一个应用服务发起，禁止数据库触发器隐式改业务状态。
5. `SWITCH`：按 Feature Flag 切换新读写路径，保留可回退窗口。
6. `CONTRACT`：确认无旧流量后冻结旧菜单/API；删除表/列另立后续变更，不与首次切换同批执行。

### 12.2 四项已登记漂移

| 漂移 | 前向处理 | 禁止操作 |
|---|---|---|
| P2-DRIFT-01 维保/续保 | 新建 MaintenanceFact、ServiceHandover；可证明字段迁移，续保字段隔离 | 修改 V14/V17、继续新增续保入口 |
| P2-DRIFT-02 技术公告治理 | 新建 ITR 同步表；历史本地记录以来源类型隔离 | 将本地发布记录冒充 ITR 主数据 |
| P2-DRIFT-03 license_key | 先分类用途和 Owner；设备凭证只迁移可证明且能安全重加密的数据 | 直接复制到明文 credential 字段 |
| P2-DRIFT-04 状态字典 | 新增稳定状态代码和映射版本 | 用字典新增值绕过状态迁移 |

## 13. 数据完整性与拒绝场景

| 场景 | 数据库约束 | 应用服务校验 |
|---|---|---|
| 项目/任务形成环 | 路径唯一键辅助 | 移动前检查目标父节点不在后代集合 |
| 设备产生两个当前归属 | `uk(tenant_id, device_id)` | 设备锁 + assignmentVersion |
| 设备历史区间重叠 | 索引辅助 | 事务内检查前后区间并锁定设备 |
| 同一回调重复到达 | Inbox/Callback 唯一键 | 返回首次处理结果，不重复推进业务 |
| 同一 API 幂等键不同请求 | Idempotency 唯一键 | 请求摘要不一致返回冲突，不复用旧响应 |
| 越权引用项目/设备/凭证 | 不依赖外键代替权限 | 写入前校验租户、项目树、设备归属和授权五元组 |
| 已批准 revision 被修改 | revision 唯一/状态 | 拒绝更新，只允许新 revision |
| 外部同步版本倒退 | 来源唯一键和版本 | 忽略或隔离旧版本，记录对账异常 |

## 14. 备份、归档与数据保留边界

- 备份恢复、保留期限和灾备指标在 Phase 3 部署/运行设计确定，不在本分册臆造期限。
- 项目闭环不触发物理删除；已归档交付件、审批快照、状态历史、归属历史和审计仍可按授权查询。
- 敏感凭证轮换后旧密文版本按安全策略封存或销毁，但必须保留不含秘密的使用审计和版本指纹。
- 外部原始大结果优先保存受控对象存储引用；数据库保存哈希、大小、来源、版本和解析状态。

## 15. 数据库门禁结论

| 门禁项 | 结论 | 落位 |
|---|---|---|
| 数据 Owner 可物理隔离 | PASS | Context 前缀、逻辑引用、无跨域级联写 |
| 任意层级查询可实现 | PASS | 邻接真值 + project/task path 投影 |
| 设备唯一归属可约束 | PASS | current assignment 唯一键 + history + ancestor projection |
| 版本、快照、历史可实现 | PASS | revision、snapshot、transition、sync batch 表 |
| 敏感数据不明文持久化 | PASS | credential 密文/secretRef；临时密码无字段 |
| 数据元与历史迁移结论已纳入 | PASS | 第1.1、4.1.1、8.1～8.3节；结构化证据优先、来源载荷+业务列双层保存 |
| 漂移可前向纠正 | PASS-WITH-IMPLEMENTATION-GATE | 第1.2、12节；包含历史迁移或数据切换的发布须先完成`AI-MIG-000`，普通应用Schema前向迁移不因此转为历史迁移门禁 |

本分册达到 API、事件、集成和并发/幂等设计的数据库前置条件；实际应用Schema变更仍以实现仓库“下一个未占用 Flyway 版本”为准。只有发布包含历史迁移或数据切换时，才须由`AI-MIG-000`在Release前完成真实批次的范围、水位、程序、校验、演练、对账和回退验证，并在批准窗口内执行；普通功能发布不适用。P3-E09不定义迁移批准哈希。
