# SDS Phase 2：数据库设计

> 文档状态：`REVALIDATION_REQUIRED`（修订017差量已回写；正式复审以当前Gate为准）
> 适用基线：PRD V1.8修订017（`docs/baseline/prd-v1.8.md`）；未受影响旧设计及历史证据保留
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
| 物理实现 | 本仓目标分支未占用 Flyway 版本、批准目标 DDL | 决定最终表/列/索引/约束；不得编辑已执行迁移 |

结构化证据当前记录：197条语义来源、108个归并数据元；活动结构证据3,931行/3,908个唯一旧字段；18张核心旧表326字段曾全部有处置。以上数字只证明历史设计覆盖，不能代替当前迁移批次的逐行对账。

08a逐个覆盖Phase 2显式数据对象，`docs/traceability/domain-entity-migration-contract.json`进一步把每个对象拆为来源级显式处置。Feature表设计不得只处理项目—订单—设备核心链：凡契约来源为`CURRENT_FORWARD/STRUCTURED/RELATION/EXTERNAL_SYNC`的领域实体，必须提供字段映射、状态/字典映射、来源键、问题分类和对账；`REBUILD`必须给出重建水位；`NEW_ONLY/EXCLUDED`不得被无关旧表填充。对象级摘要中的复合策略不能直接生成迁移SQL。

### 1.2 DDL 漂移和实施门禁

`specs/001-project-delivery-platform/evidence/migration/ddl-drift-review.json`记录的DDL哈希、当前项数量及复核状态以本次V1.8校验产物为准；Q08的具体索引仍只是候选，必须经过Feature/P3-E06性能验证。隔离MySQL 8.4.10执行仅证明DDL可执行，不代表历史迁移或数据切换已获批准。因此：

ADR-0004已确认P3-E09采用只读生成逐表、逐列、逐索引/约束差异并逐项裁决的方向，不整体恢复旧DDL。ADR-0030六表差量已完成当前事实、机器校验和整体一致性独立复审，当前为`MODEL_BASELINE_READY`。P3-E09不定义迁移批准哈希，不建立Owner签署、外部附件或迁移批准流程。

ADR-0019已确认物理表按13领域编码划分，删除业务系统名称前缀`pms_`，并采用`<domain_code>_<full_domain_object_name>`；表名必须保留全部领域对象语义组件，默认使用完整英文词，仅允许ADR登记的`config`、`sn`两个表名标准缩写。字段可以在不产生业务歧义的前提下使用ADR登记的受控缩写、统一同义词并保持简洁。ADR-0019列出了当前52张物理表的逐表目标名称和首批同义字段裁决。该命名决策属于P3-E09模型输入，不等于批准旧DDL：本分册后续仍出现的`pms_*`仅表示尚待AI-MIG-000统一重建的当前证据名称，不再是目标命名。

ADR-0021在ADR-0019的52表命名基线上增加`cus_market_relation`。该表是CRM四维组合目录的CUS同步副本；F-CUS-001前向表`cus_customer_master`与`proj_project`直接保存市场部、系统部、拓展部、子行业各自编码和名称，不保存`relation_id`，也不以目录记录ID建立外键或历史链。

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
- 项目生命周期不得统一压缩为一个 `status_code`：`current_stage` 使用 S0～S6，`lifecycle_status` 使用 ACTIVE/NORMAL_CLOSED/NO_TRACKING_CLOSED/EXCEPTION_CLOSED，`assignment_status` 独立保存，`display_status` 只读派生；历史旧表的 `status` 不原地改义，新增映射列或兼容适配层。

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

F-PROJ-002以前向迁移新增`proj_project_tree_version`、`proj_project_tree_path`和`proj_project_tree_change`：

| 表 | 关键字段 | 约束/索引与语义 |
|---|---|---|
| `proj_project_tree_version` | `id/root_project_id/tree_version/status/change_batch_id/node_count/path_count/activated_at/failed_reason/version` | `uk(tenant_id, root_project_id, tree_version)`；`idx(tenant_id, root_project_id, status, tree_version)`；状态只允许BUILDING/ACTIVE/FAILED，同一根查询只读取最后完整ACTIVE版本 |
| `proj_project_tree_path` | `tree_version/root_project_id/ancestor_project_id/descendant_project_id/distance` | `uk(tenant_id, root_project_id, tree_version, ancestor_project_id, descendant_project_id)`；自身distance=0，直接子级=1；只允许引用同一完整版本 |
| `proj_project_tree_change` | `change_batch_id/operation_type/project_id/parent_id_before/parent_id_after/base_tree_version/new_tree_version/actor_id/reason/occurred_at` | `uk(tenant_id, change_batch_id, project_id)`；`idx(tenant_id, project_id, occurred_at, id)`；追加写，不因后续移动覆盖 |

索引：

- `idx(tenant_id, root_project_id, tree_version, descendant_project_id, ancestor_project_id)` 用于反查祖先；
- `idx(tenant_id, root_project_id, tree_version, ancestor_project_id, distance, descendant_project_id)` 用于子树分页；
- 跨根移动以同一`change_batch_id`分别生成源根和目标根完整版本；任何BUILDING或FAILED版本不得成为授权、查询或汇总输入。

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
| 项目状态与生命周期时间 | `current_stage/lifecycle_status/assignment_status`及独立发生时间字段 | `current_stage`仅允许S0～S6；`lifecycle_status`仅允许ACTIVE/NORMAL_CLOSED/NO_TRACKING_CLOSED/EXCEPTION_CLOSED；`display_status`由服务端派生；旧时间不覆盖`create_time/update_time` |

项目主表不得以单一状态字段表达多个业务维度：

| 字段 | 类型/取值 | 约束 |
|---|---|---|
| `current_stage` | 稳定代码S0～S6 | 由阶段门禁命令迁移；三类退出后保留最后真实模板阶段；不要求S6 |
| `lifecycle_status` | ACTIVE/NORMAL_CLOSED/NO_TRACKING_CLOSED/EXCEPTION_CLOSED | CLO-02唯一写入NORMAL_CLOSED或NO_TRACKING_CLOSED；PM-10异常关闭写入EXCEPTION_CLOSED；不得由字典新增可执行值 |
| `assignment_status` | 基础平台字典映射的稳定代码 | 与项目经理/执行指派独立迁移，不改变生命周期 |
| `display_status` | 派生只读值 | 由上述字段及查询上下文计算，不落交易真值；不得被客户端写入 |

### 4.1.2 项目站点与地点降级

新增`proj_project_site`保存`project_id/site_id/primary_site/scope_status/effective_from/effective_to/site_code_snapshot/site_name_snapshot/address_snapshot/version`。同一项目—站点—生效时间唯一；同一项目至多一个当前主站点。跨Context只保存AST稳定ID、版本和必要快照，不建立到`ast_site`的物理外键。

`proj_project`增加`location_resolution_status`，仅允许`UNRESOLVED/RESOLVED`。现有`implementation_location`只作为站点未维护时的文本降级；结构化站点存在时不得形成第二权威。

### 4.1.3 PM-02拆分草稿与范围快照前向表

以下物理表由PM-02 Feature前向迁移确定，不改变Phase 1/2/3既有Gate状态。

F-PROJ-002以前向迁移新增：

| 表 | 关键字段 | 约束/索引与语义 |
|---|---|---|
| `proj_project_split_request` | `parent_project_id/status/draft_version/parent_version/scope_version/tree_version/template_revision_id/preview_hash/validation_status/validated_at/applied_change_batch_id/version` | `idx(tenant_id, parent_project_id, status, update_time, id)`；状态只允许DRAFT/APPLIED；APPLIED后不得再次生成项目 |
| `proj_project_split_item` | `split_request_id/client_item_key/project_name/business_level_code/tree_sort/office_department_code/item_status` | `uk(tenant_id, split_request_id, client_item_key)`；同一草稿内稳定关联预览和应用结果 |
| `proj_project_split_scope` | `split_item_id/order_line_id/allocated_qty/office_department_code/serial_no/source_scope_version/source_snapshot` | `uk(tenant_id, split_item_id, order_line_id, office_department_code, serial_no)`；数量大于0；`source_snapshot`只保存发生时必要摘要，不替代COM/AST当前真值 |

草稿校验失败仍保留DRAFT和逐项结果，不新增`proj_project`、DeliveryScope分配或树关系。确认命令锁定草稿、父项目、范围版本和树版本后重验；全部子项目、模板实例、范围分配、树版本、审计、幂等成功和Outbox在同一事务完成或整体回滚。

### 4.1.4 PM-02进度事实、策略与快照前向表

| 表 | 关键字段 | 约束/索引与语义 |
|---|---|---|
| `proj_project_progress_fact` | `project_id/fact_source_type/fact_source_id/fact_version/progress/source_watermark/occurred_at` | `uk(tenant_id, project_id, fact_source_type, fact_source_id, fact_version)`；追加写；progress为0～100，缺行表示无有效事实而不是0 |
| `proj_project_progress_policy_revision` | `parent_project_id/revision_no/status/policy_type/process_definition_key/process_instance_id/effective_from/effective_to/approved_by/approved_at/supersedes_revision_id/version` | `uk(tenant_id, parent_project_id, revision_no)`；`uk(tenant_id, process_instance_id)`；批准版本不覆盖，当前生效区间不得重叠 |
| `proj_project_progress_policy_item` | `policy_revision_id/child_project_id/weight/include_status_snapshot` | `uk(tenant_id, policy_revision_id, child_project_id)`；同一版本全部直接子项目权重合计100%，默认等权也固化为版本 |
| `proj_project_progress_snapshot` | `project_id/policy_revision_id/tree_version/source_watermark/snapshot_status/progress/missing_item_count/calculated_at` | `uk(tenant_id, project_id, policy_revision_id, tree_version, source_watermark)`；状态READY/PENDING；历史不追溯重算 |
| `proj_project_progress_snapshot_detail` | `snapshot_id/child_project_id/fact_version/child_progress/normalized_weight/contribution/missing_reason` | `uk(tenant_id, snapshot_id, child_project_id)`；解释一次直接子项目汇总，不保存未授权正文 |

V1.7已有`proj_project.progress/aggregation_weight/weight_source`仅保留为兼容读字段，不再是F-PROJ-002正式写真值。子项目关闭、撤销等状态是否进入分母固化在生效策略版本；任一必要直接子项目缺少有效事实时生成PENDING快照并禁止据此闭环。

### 4.2 任务树与依赖

现有 `proj_project_task` 保存当前父关系；`proj_task_dependency` 只保存任务依赖，二者不得混用。

【建议】新增 `proj_task_tree_path`，结构和索引与项目路径表相同，并增加 `project_id` 作为高频过滤列。任务移动只修改任务父关系和路径投影，不自动创建/删除依赖。

`proj_task_dependency`保存`project_id/predecessor_task_id/successor_task_id/dependency_type/version`及通用审计字段；同项目同方向同类型唯一，禁止自依赖。V2新增、更新、删除均锁定关系版本并在应用服务校验两端任务范围及受控无环规则；甘特查询只读取ProjectTask与该关系，不生成第二套计划或任务表。

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
| `proj_project_member_assignment` | 角色成员当前/历史有效期 | 同一项目/角色/用户的有效区间由应用服务防重叠；不增加授权范围列，角色只产生当前项目允许动作，后代范围由PLT显式授权承载 |
| `proj_project_template_revision` | 模板发布版本 | `uk(tenant_id, template_id, revision_no)`；发布后只读 |
| `proj_project_portfolio` | 项目组合身份、类型、状态和当前发布版本 | `uk(tenant_id, portfolio_code)`；不改变成员项目Owner |
| `proj_project_portfolio_member` | 组合成员、主组合标识、关系类型和有效区间 | 同组合/项目/关系有效区间不重叠；一个项目的默认主组合由受控唯一约束保证 |
| `proj_project_portfolio_revision` | 组合规则、成员快照和发布版本 | `uk(tenant_id, portfolio_id, revision_no)`；发布后不可变 |

### 4.4 PM-05 转销与 PM-06 同项目范围追加

| 需求 | 表 | 关键字段 | 约束/索引 |
|---|---|---|---|
| PM-05 | `proj_project_conversion` | `source_project_id/target_project_id/formal_sales_business_id/status_code/idempotency_key/summary_json/version` | `uk(tenant_id, source_project_id, formal_sales_business_id)`；应用与状态机保证同一源项目只有一个生效目标 |
| PM-05 | `proj_project_conversion_item` | `conversion_id/source_context/source_object_type/source_object_id/source_version/handling_mode_code/target_object_id/result_code/failure_code` | `uk(tenant_id, conversion_id, source_context, source_object_type, source_object_id, source_version)`；逐项追加/重试，不覆盖成功项 |
| PM-05 | `proj_project_conversion_device` | `conversion_id/device_id/disposition_code/assignment_version_before/target_assignment_version/result_code` | `uk(tenant_id, conversion_id, device_id)`；设备归属由 AST 当前唯一表执行，结果只保存引用 |

`proj_project_conversion` 与对象项采用过程聚合+逐项结果：正式项目未创建成功不生成转销批次；转销完成与源项目只读归档由同一 Project Delivery 应用服务在门禁通过后提交。跨 Context 设备归属、文件/实施对象引用通过 Saga 保存确认，不使用跨库事务或直接更新外域表。

PM-06使用`proj_contract_scope_append_request`；COM唯一项目水位为`com_delivery_scope_project_version`，不可变范围版本为`com_project_scope_revision`。新版本、数量占用、范围差异与ACC绑定共同提交；不建立或写入多期群组。字段、约束、前向迁移与历史处置见本分册修订017物理契约。

### 4.5 PM-07模板匹配决策历史前向表

- `proj_project`复用既有`signing_method`、`project_category`、`implementation_mode`、`major_project_level`，不新增同义业务属性列或分类/选模状态轴。
- `proj_project_template_match_history`物理表由PM-07 Feature前向迁移确定，至少保存`tenant_id/project_id/trigger_type/record_purpose/input_origin/snapshot_schema_version/before_attribute_snapshot/attribute_snapshot/attribute_owner_snapshot/source_owner/source_system/source_key/source_event_id/source_version/source_occurred_at/source_value_digest/mapping_version/matcher_version/match_result/candidate_digest/decision_mode/matched_template_id/matched_template_revision_id/frozen_template_revision_id/impact_result/operator_id/change_reason/operation_id/trace_id/audit_log_id/idempotency_key/request_digest/occurred_at/recorded_at`及通用审计字段。业务字段只插入不更新；`uk(tenant_id, project_id, idempotency_key)`防止重复决策，`uk(tenant_id, operation_id)`提供稳定业务关联；`trace_id/audit_log_id`可空且只作关联，不依赖异步系统日志保证事务完整性。
- `INITIAL_CREATE`使用`record_purpose=CREATE_DECISION`，前值为NULL、影响结论为`NOT_APPLICABLE`，决策方式为`AUTO_UNIQUE/EXPLICIT_SELECTION`；`UNIQUE`和经显式选择持久化的`MULTIPLE_MATCHES`均须保存最终模板及修订，`NO_MATCH`不产生Project级历史。
- `SOURCE_CORRECTION/MANUAL_ADJUSTMENT`使用`record_purpose=IMPACT_EVALUATION`且`decision_mode`必须为NULL，前值必填；结果为`UNIQUE`时新候选模板及修订必填，`NO_MATCH/MULTIPLE_MATCHES`时命中模板字段必须为NULL且候选摘要必填。来源修正另要求来源Owner/键/事件/版本/发生时间/原值摘要/映射版本；人工调整要求操作者和原因。
- `operator_id/change_reason`所有历史行有效：手工INITIAL_CREATE取认证用户稳定ID与必填非空白`create_reason`，自动INITIAL_CREATE取已注册服务主体稳定ID与必填非空白创建原因；SOURCE_CORRECTION将`service_identity`解析为已注册服务主体稳定ID并要求命令提交必填非空白`correction_reason`；MANUAL_ADJUSTMENT取认证用户稳定ID与必填非空白`adjustment_reason`。三类原因先trim，null、空字符串或纯空白在事务前拒绝；服务身份解析失败时整条业务命令失败，不写临时显示名或网络标识代替稳定主体。
- 首次唯一候选自动决定或从多候选显式选择的记录，与Project、模板冻结和全部实例要素同事务提交；创建后记录只保存只读重新评估结果，不更新冻结模板引用。
- 不新增`proj_project_business_attribute_history`；现有`system_operate_log`不具备属性维度结构化查询、事务内必达和已验证永久保留语义，公共审计增强须独立立项。
- 新前向迁移将`pms_project_category`候选收敛为`GENERAL/ENGINEERING`，移除`MAIN/SUB`候选但不修改旧迁移；手工项目的重大项目级别用数据库NULL表示、界面显示“不适用”。先清查存量错误值和手工来源非空重大级别，禁止自动映射或静默清空。
- 初始化数据只补字典/菜单/权限及不冒充CRM权威值的组合示例。

### 4.5.1 PM-08服务经理指派与通知幂等前向字段

- `proj_project_member_assignment`复用`employee_no/member_name/company_id/company_code/company_name/department_code/department_name/member_role/effective_from/effective_to`，前向新增`department_id/assignment_type/site_id/change_reason`；必填组织快照为`company_id + department_id + department_code + department_name`。
- V1仅支持服务端事务时间立即生效。同一项目节点的所有写入口先以`proj_project.version` CAS锁定，再在事务内检查同角色/站点范围重叠；不增加会破坏时间历史的“当前主责”物理唯一键，真实MySQL竞态测试证明同版本只有一个成功。
- V2唯一匹配自动指派复用相同成员表、版本锁和有效区间事务；冻结规则返回零个或多个候选时不写成员关系，项目保持待指派并进入V1人工流程，不新增候选确认状态或第二套指派表。
- `system_notify_message`前向新增可空`delivery_key varchar(128)`及`uk(tenant_id,user_type,delivery_key)`；既有调用留空不受影响。PM-08以Outbox `event_id`填入，重复一致请求返回首次消息ID，重复键但收件人、模板或参数摘要不一致时冲突。
- 不新增成员历史、通知历史或重试表；成员区间、`system_notify_message`和`plt_outbox_event`分别是责任历史、幂等投递和重试事实。

### 4.6 Preparation & Solution

适用 Requirement：PRE-01～PRE-05、PLN-01～PLN-04、SCH-01～SCH-05、SOL-01。

| 聚合 | 主表 | 版本/明细表 | 关键约束 |
|---|---|---|---|
| Preparation | `sol_preparation` | `sol_preparation_item`及各Feature专用明细 | 项目+准备类型+业务版本唯一；PRE-04以`dynamic_form_instance_id`逻辑引用唯一PLT业务实例，SOL只保存业务生命周期/版本/有效指针 |
| PreparationDynamicFormInstance | `sol_dynamic_form_instance` | F-SOL-002既有明细 | 继续服务已完成工勘能力，表、读写和数据原样保留；PRE-04不得复用或改写它 |
| RequirementAnalysisDynamicFormBinding | `sol_preparation.dynamic_form_instance_id` | PLT `plt_dynamic_form_instance` | SOL预分配两侧ID，根首次INSERT即写非空实例ID，PLT按同一外层事务插入该ID，无回填或额外版本递增；不建跨Context物理外键，不复制schema/值/附件；旧候选章节表不再作为当前真值 |
| ConstructionPlan | `sol_construction_plan` | `sol_construction_plan_revision`、`sol_construction_plan_item`、`sol_construction_plan_change` | `uk(tenant_id, plan_id, revision_no)`；批准 revision 只读 |
| Solution | `sol_solution` | `sol_solution_revision`、`sol_solution_review` | 发布 revision 只读；文件仅保存 FileReference |

历史 `pms_eng_site_survey/requirement/resource_ready/briefing/solution` 只在对应Feature明确批准时作为迁移来源。旧`pms_eng_form_template/pms_eng_form_instance`原样保留，不迁入、不双写新的PLATFORM动态表单真值；新应用服务按各自Owner访问，不允许表单引擎直接写Project或SOL业务状态。

### 修订019 PM-01成员物理影响

`proj_project_member_assignment`是多项目经理时态关系的复用载体；既有`proj_project.manager_id/manager_employee_no/manager_name`只能表达当前主责投影，不作为完整名单。不得新增“一个项目最多一条PROJECT_MANAGER”的唯一约束，也不得用数组列替代关系历史。主责与成员的事务约束、各读消费者兼容和实际DDL差量按新合同核对；Q-FPROJ-010业务范围已关闭；本段不是Schema验证通过或迁移授权。

## 5. Asset 地点、设备归属与维保基本事实

适用 Requirement：EQP-01～EQP-05、EQP-07、AST-01～AST-02、INT-02、INT-06。

### 5.0 地点主数据与区划映射

地点物理表全部由AST持有：

| 表 | 关键字段 | 约束 |
|---|---|---|
| `ast_address` | 国家/省/市/区县编码名称、`detail_address/full_address`、经纬度、标准化文本/候选指纹、状态、版本 | 指纹仅索引候选，不作自动合并唯一键；地址不物理删除 |
| `ast_site` | `code/name/customer_id/address_id/site_type/status/version` | `uk(tenant_id, code, deleted)`；`customer_id`可空且无跨Context外键；禁止公司/部门列；同址允许多站点 |
| `ast_site_location` | `site_id/parent_id/code/name/location_type/tree_path/tree_depth/tree_sort/status/version` | `uk(tenant_id, site_id, code, deleted)`；任意深度、无环、跨站点移动拒绝 |
| `ast_location_source_mapping` | 来源系统、对象类型、来源键/版本、Address/Site引用、同步水位、匹配状态 | `uk(tenant_id, source_system, object_type, source_key, deleted)`；状态限PENDING/MATCHED/CONFLICT/INVALID |
| `ast_area_department_mapping` | `area_code/area_level/mapping_type/department_code/effective_from/effective_to/status/version` | 同一映射同一时点唯一；`area_level`限COUNTRY/PROVINCE/CITY/DISTRICT；V1只精确匹配 |

所有表包含租户、审计、软删除和乐观锁版本。地点被引用后只允许受控修订；安装、迁移和拆除历史保存发生时快照。

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
| InstallationRecord | `imp_installation_record` | `imp_installation_item`、`imp_installation_evidence` | 保存可空`site_id/site_location_id`、解析状态、文本降级、位置快照和有效区间；设备/安装批次索引；同一设备同一时点仅一个当前有效安装位置；历史记录不覆盖 |
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
| Acceptance | `acc_acceptance` | `acc_acceptance_report_version`、`acc_acceptance_report_attachment`、`acc_confirmation` | 按ADR-0039/0040创建活动根和不可变报告版本，附件使用PLT公共事实；旧验收不迁成新当前报告 |
| SatisfactionCollection | `acc_satisfaction_collection_task` | `acc_satisfaction_questionnaire_template`、`acc_satisfaction_questionnaire_template_revision`、`acc_satisfaction_questionnaire`、`acc_satisfaction_access_grant`、`acc_satisfaction_response`、`acc_satisfaction_response_file`、`acc_satisfaction_result`、`acc_satisfaction_result_file`、`acc_satisfaction_remediation_fact` | 按ADR-0041冻结配置并由服务器判定；历史只追加、整改新建；旧问卷/评分只保留来源，不生成当前有效结果 |
| DeliveryArtifact | `acc_project_deliverable` | `acc_project_deliverable_source_version`、`acc_project_deliverable_source_attachment`、`acc_artifact_review`、`acc_archive_record` | 复用唯一应交根；来源版本和完整有序文件集合不可覆盖，不以旧名称或状态推断当前交付件 |
| ProjectClosure | `acc_project_closure` | `acc_closure_gate_snapshot`、`acc_closure_review` | 快照号唯一；完成后不提供更新接口 |
| ServiceHandover | `acc_service_handover` | `acc_handover_item`、`acc_handover_result` | V2静态交接快照；不含续保年限、续保结束日期、续保状态或持续跟踪对象 |

历史 `pms_acc_maintenance_transition` 不改表。前向迁移只把可以证明的交接字段映射到新表，并保存 `legacy_record_id`；续保字段不进入新模型。

## 7. Cutover、Inspection 与服务状态

| Context | 目标表组 | 关键约束与索引 |
|---|---|---|
| Cutover | `cut_task`、`cut_assessment`、`cut_plan_revision`、`cut_step`、`cut_cutover_support_arrangement`、`cut_cutover_closure`；CUT-07 Feature前向表见7.2 | 任务内计划revision唯一；步骤只属于批准方案内容；保障人员安排从属于方案且联系人类变更留审计、职责变更新建revision；P6闭环一任务一版本递增，提交后只读 |
| Inspection | `srv_inspection_task`、`srv_inspection_rule`、`srv_inspection_rule_revision`、`srv_inspection_rule_command_revision`、`srv_inspection_rule_product_type_revision`、`srv_inspection_rule_security_review`、`srv_inspection_task_rule_snapshot`、`srv_inspection_report_revision`、`srv_service_issue`、`srv_service_issue_remediation` | 在线/离线模式检查；规则稳定身份的检测ID和规则名称均在租户内永久唯一，软删除不释放；revision号在规则内唯一，revision名称快照必须与稳定身份一致且不可改名；DRAFT除稳定身份字段外允许不完整，PUBLISHED由Service全量校验；阈值类型只允许`NUMBER`；命令顺序在revision内唯一且连续；产品类型在revision内唯一；安全审核事实只追加，结论只允许`PASSED/REJECTED`并绑定revision及命令/正则内容摘要，当前结论由Mapper XML按同租户、同revision、同摘要以`reviewed_at DESC, id DESC`选择，只有最后一条为`PASSED`可发布；权限审计保存精确`permission_code`和`RBAC_PERMISSION`，现有System布尔接口不提供贡献路径，`authorization_source_id`保持`NULL`，不新增来源表、唯一约束或推断写入；同一规则最多一个当前发布revision；任务规则快照唯一；报告revision只追加 |
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

### 7.2 CUT-07/09/10后台配置前向表

以下表名是ADR-0031批准的Feature目标，不属于当前核心DDL；物理表由CUT-07 Feature前向迁移确定，并由同一Feature承接CUT-09/10首批配置基础，必须先于或不晚于首个消费能力交付。

| 目标表 | 关键字段 | 约束与索引 |
|---|---|---|
| `cut_cutover_configuration_revision` | `configuration_code/revision_no/status_code/effective_from/effective_to/published_by/published_at/disabled_at/dictionary_snapshot/navigation_rule_snapshot/version` | `uk(tenant_id, configuration_code, revision_no)`；V1承载动态模板/表单/风险调研匹配基础，CUT-03 V2才使用受控跳转规则；发布版本不可覆盖；消费实例冻结revision和字典代码/名称快照 |
| `cut_cutover_checklist_item_definition_revision` | `configuration_revision_id/stable_item_key/item_definition_version/item_type_code/item_name/interface_format_code/interface_schema/work_mode_code/required_flag/external_source_ref/status_code/sort_order/version` | `uk(tenant_id, configuration_revision_id, stable_item_key, item_definition_version)`；稳定项键不可复用为不同含义 |
| `cut_cutover_checklist_binding_rule_revision` | `configuration_revision_id/item_definition_id/item_definition_version/rule_version/dimension_condition_snapshot/priority/status_code/version` | `uk(tenant_id, configuration_revision_id, item_definition_id, rule_version)`；维度条件必须可判定且引用启用的基础平台字典值 |

割接类型、组网模式、设备类型使用基础平台可配置字典，不另建业务主表。新表只能由承接CUT-07/09/10配置基础的Feature创建；不从`pms_cut_plan`、`pms_cut_risk`或旧清单推断历史配置版本。

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

INT-05/INT-09复用基础平台用户、公司、部门和岗位主数据，已有`plt_sync_batch`承载同步批次/水位，`plt_external_key_mapping`承载来源键映射。前向实现增加`system_company`、`system_dept.code`及`system_user_company_department_scope`；公司与部门保持独立，Scope同一有效行保存公司和可选部门，禁止由部门推导公司。

### 8.2 目标表组

| Context | 目标表组 | 关键约束 |
|---|---|---|
| Customer | F-CUS-001前向表`cus_customer_master`、`cus_customer_external_mapping`、`cus_customer_field_history`、`cus_customer_location_reference`、`cus_customer_scope_slice`，以及`cus_market_relation`、`cus_customer_contact`、`cus_project_customer_contact_relation`、`cus_customer_relationship_snapshot`；CUS-02 Feature前向表见8.2.1 | CRM对象按`source_system+source_key`唯一；临时客户另有`origin_code`；四维组合目录与客户/项目八字段快照分离；五维权限切片显式保存且空范围不放大 |
| Commerce | `com_contract`、`com_sales_order`、`com_order_line`、`com_delivery_scope`、`com_delivery_scope_detail`、`com_fulfillment_snapshot`、`com_reconciliation_record` | ERP合同按所属公司+合同编号；订单头与合同为关系表语义，不能固化唯一合同；ERP订单/行按稳定业务键+来源版本唯一；CRM经营引用与履约回执单独存 source mapping；范围主记录至少含订单行、项目、`allocated_qty`、`scope_status`及来源证据，范围明细保存地点、产品/设备类型、数量和可选批次 |
| Resource | `res_supplier`、`res_qualification`、`res_subcontract_request`、`res_payment_gate` | 资质版本追加；财务结果只保存引用和回写状态 |
| Knowledge | `TechnicalNoticeReference`逻辑对象；物理表由INT-04 Feature前向迁移确定 | V2公告按ITR来源键+版本唯一，只保存同步副本和业务引用；4张V3治理表不进入核心迁移DDL |

`pms_eng_announcement`和`pms_eng_announcement_check`只作为历史来源证据保留。V1/V2新菜单和API只读取INT-04 Feature批准的ITR同步副本；本地创建记录不得混入外部主数据结果，也不得提前创建V3治理表。

### 8.2.1 CUS-02服务等级前向表

`cus_customer_service_level_revision`是ADR-0031批准的Feature目标，物理表由CUS-02 Feature前向迁移确定，不属于当前核心DDL。关键字段为`customer_id/service_level_code/policy_snapshot/effective_from/effective_to/current_marker/change_reason/evidence_ref/approver_id/version`；`uk(tenant_id, customer_id, version)`保存版本，生成列`current_marker`仅在`effective_to is null`时取1，`uk(tenant_id, customer_id, current_marker)`保证同一客户至多一个当前等级，并校验结束时间不早于开始时间。等级代码使用基础平台字典；无可靠历史来源，不从客户、联系人或关系快照反推等级。

### 8.2.2 F-PROJ-002使用的COM-01 DeliveryScope前向切片

本切片只批准F-PROJ-002稳定调用所需的订单行数量与范围分配载体，不表示COM-01合同/订单全量同步、人工补录、对账或管理页面已经完成。

| 表 | 关键字段 | 约束/索引与语义 |
|---|---|---|
| `com_order_line` | `source_system/source_key/source_version/order_id/line_code/item_code/quantity/unit_code/quantity_status/source_updated_at/synced_at/version` | `uk(tenant_id, source_system, source_key)`；ERP字段只读；quantity_status为CONFIRMED/PENDING_AUTHORITY，后者不计入可分配量 |
| `com_delivery_scope` | `order_line_id/project_id/allocated_qty/scope_status/allocation_version/source_evidence/effective_from/effective_to/version` | `uk(tenant_id, order_line_id, project_id, allocation_version)`；当前有效量不得使订单行超配；取消、退货和ERP减量必须产生受控冲突或释放事实 |
| `com_delivery_scope_detail` | `delivery_scope_id/office_department_code/serial_no/allocated_qty/detail_status/source_snapshot/version` | `uk(tenant_id, delivery_scope_id, office_department_code, serial_no)`；明细数量合计等于主记录；办事处只用部门稳定编码，不以地址ID推导 |
| `com_outbox_event` | `event_id/event_type/aggregate_type/aggregate_key/scope_version/payload/status/occurred_at/retry_count` | `uk(tenant_id, event_id)`；`idx(tenant_id, status, occurred_at, id)`；仅由COM事务发布DeliveryScopeAssigned/Released |

F-COM-001前向完成时，V70 `com_order_line.quantity_status`继续作为唯一数量权威字段（`CONFIRMED/PENDING_AUTHORITY`），不得改名或与`authority_status`双写。`source_updated_at`和DeliveryScopeDetail的`serial_no/detail_status/source_snapshot`均为V70既有列；统一目标新建`com_sales_order_line`并在DeliveryScope主记录冻结项目办事处部门ID/编码/名称/版本，明细只保存产品/设备类型、序列号、批次和数量。COM表不得新增或保留`site_id/site_location_id/location_text/location_resolution_status`第二地点真值。V70允许订单行quantity为NULL/0、scope.source_evidence为NULL及detail缺新维度；转换缺少必填Owner事实时整批失败或进入PLT迁移问题，不用默认值补造。

新增`com_authority_candidate`保存`PLATFORM_MANUAL`的合同/订单/行候选：不可变来源键、版本、payload和证据，状态限定`PENDING_RECONCILIATION/MATCHED/REJECTED`；MATCHED只引用已存在的CONFIRMED ERP Owner表/id/sourceVersion，不把候选晋升为权威主档。新增`com_delivery_scope_project_version`以`uk(tenant_id,project_id)`保存不可删除的项目`scope_version`，任何当前集合、返回载荷、冲突或清空变化在同一事务只递增一次。当前关系与当前范围分别通过显式current marker唯一键约束；SN明细数量固定为1，规范化SN在当前项目/订单行范围唯一。

F-COM-001统一物理差量如下；字段定义是Feature前向DDL的批准输入，不表示迁移已执行：

| 表 | 批准字段差量 | 约束说明 |
|---|---|---|
| `com_contract` | `master_source_version varchar(64) COLLATE utf8mb4_0900_bin NULL`；`source_updated_at datetime(3) NULL` | ERP来源版本与发生时间只由权威批次推进 |
| `com_sales_order` | `source_record_key varchar(128) COLLATE utf8mb4_0900_bin NULL`；`source_version varchar(64) COLLATE utf8mb4_0900_bin NULL`；`source_updated_at datetime(3) NULL` | `tenant_id/source_system/source_record_key`唯一 |
| `com_sales_order_line` | `source_record_key varchar(128) COLLATE utf8mb4_0900_bin NOT NULL`；`source_version varchar(64) COLLATE utf8mb4_0900_bin NOT NULL`；`unit_code varchar(32) NOT NULL`；`unit_scale tinyint unsigned NOT NULL`；`quantity_status varchar(32) NOT NULL`；`source_updated_at datetime(3) NULL`；`product_code varchar(64) NULL`；`order_qty/open_qty/delivered_qty decimal(18,6) NULL` | 数量、单位、产品及来源版本均来自ERP Owner；不得由itemCode推断productCode |
| `com_delivery_scope` | `allocated_qty decimal(18,6) NOT NULL`；`allocation_version bigint NOT NULL`；`office_department_id bigint NOT NULL`；`office_department_code varchar(64) NOT NULL`；`office_department_name varchar(255) NOT NULL`；`office_department_version int unsigned NOT NULL`；`source_evidence varchar(255) NULL`；`effective_from datetime(3) NOT NULL`；`effective_to datetime(3) NULL` | 办事处为PROJ发生时快照；不保存AST地点真值 |
| `com_delivery_scope_detail` | `serial_no varchar(128) NULL`；`detail_status varchar(32) NOT NULL`；`source_snapshot json NULL`；`version int unsigned NOT NULL DEFAULT 0`；`allocated_qty decimal(18,6) NOT NULL` | SN明细数量为1；无SN明细使用锁定ERP productCode |
| `com_order_contract_relation` | `source_system varchar(32) COLLATE utf8mb4_0900_bin NOT NULL`；`sales_order_source_key varchar(128) COLLATE utf8mb4_0900_bin NOT NULL`；`contract_source_key varchar(128) COLLATE utf8mb4_0900_bin NOT NULL`；`source_version varchar(64) COLLATE utf8mb4_0900_bin NOT NULL`；`source_evidence json NOT NULL` | 关系独立于订单头，不固化唯一合同 |
| `com_authority_candidate` | `object_type varchar(32) NOT NULL`；`candidate_source_system varchar(32) NOT NULL`；`candidate_source_key varchar(128) COLLATE utf8mb4_0900_bin NOT NULL`；`candidate_version varchar(64) COLLATE utf8mb4_0900_bin NOT NULL`；`candidate_payload json NOT NULL`；`evidence_reference json NOT NULL`；`candidate_status varchar(32) NOT NULL`；`matched_owner_type varchar(32) NULL`；`matched_owner_id bigint NULL`；`matched_owner_source_version varchar(64) COLLATE utf8mb4_0900_bin NULL`；`decision_reason varchar(512) NULL`；`version int unsigned NOT NULL DEFAULT 0` | 候选不可晋级为ERP Owner，只能关联已确认Owner |
| `com_delivery_scope_project_version` | `project_id bigint NOT NULL`；`scope_version bigint unsigned NOT NULL`；`payload_version int unsigned NOT NULL`；`last_change_type varchar(32) NOT NULL`；`version int unsigned NOT NULL DEFAULT 0` | `tenant_id/project_id`唯一；同一事务至多推进一次 |
| `acc_acceptance_scope_binding` | `id bigint NOT NULL`；`tenant_id bigint NOT NULL`；`project_id bigint NOT NULL`；`project_stage_snapshot_id bigint NOT NULL`；`delivery_scope_id bigint NOT NULL`；`scope_allocation_version bigint NOT NULL`；`binding_trigger varchar(32) NOT NULL`；`binding_status varchar(32) NOT NULL`；`effective_from datetime(3) NOT NULL`；`effective_to datetime(3) NULL`；`acceptance_fact_version int unsigned NOT NULL DEFAULT 1`；`version int unsigned NOT NULL DEFAULT 0`；`creator/updater varchar(64) NOT NULL DEFAULT ''`；`create_time/update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)`；`deleted tinyint NOT NULL DEFAULT 0` | 不含`acceptance_id`；由ACC Provider以项目阶段快照身份追加绑定 |

预览接口只加锁读取并返回权威`scopeVersion`，不写范围事实。确认接口按稳定订单行ID顺序锁定，校验期望版本、单位精度、总量和SN/办事处组合后一次写入全部分配及COM Outbox；任何一项失败整体回滚。PROJ只保存返回的稳定引用、版本和发生时摘要，不建立跨Context物理外键。

### 8.3 项目—合同—订单行—设备迁移主链

修订014补齐ERP接收链与既有V160列的一致性：订单头传递`sales_type/source_project_name/order_comment/order_create_time/customer_required_time`，订单行传递`line_type/bundle_code/profit_center/real_execution_no/warranty_month`；字段来源仅为`pm_order_data_from_erp/pm_order_line_from_erp`的已登记映射。新批次及同版本载荷比较必须包含这些字段，缺失值保持NULL，不从其他字段推断。`source_lifecycle_status`保留ERP的`ACTIVE/CANCELLED/RETURNED`，本地`status`使用`ENABLED/DISABLED`；取消、退货或本地停用均不得分配。办事处仍只冻结于`com_delivery_scope`，明细不再映射不存在的办事处列。无新增表列或历史迁移改写。

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
| `plt_dynamic_form_template` | 稳定模板身份、元数据、可用性和当前发布修订指针 | `uk(tenant_id, template_code)`；ENABLED供选时必须存在当前PUBLISHED修订；元数据CAS不改revision |
| `plt_dynamic_form_template_revision` | FormCreate完整配置/规则、引擎版本和发布事实 | `uk(tenant_id, template_id, revision_no)`；`uk(tenant_id, template_id, draft_marker)`保证至多一个草稿；PUBLISHED不可更新/删除 |
| `plt_dynamic_form_instance` | 手工实例冻结模板修订和普通字段JSON值 | `uk(tenant_id, instance_code)`、`uk(tenant_id, owner_context, object_type, object_id)`；模板修订不可切换，值按version CAS；FileArtifact字段不写入value_json |
| `plt_state_transition` | 状态前后值、命令、主体、原因和结果 | 追加写；按聚合 ID + 时间索引 |
| `plt_idempotency_record` | 接口幂等键、请求摘要、处理状态和响应引用 | `uk(tenant_id, scope_code, idempotency_key)` |
| `plt_outbox_event` | 事务内待发布事件 | `event_id` 全局唯一；按状态/下次重试时间索引 |
| `plt_inbox_message` | Consumer 去重和处理结果 | `uk(tenant_id, consumer_code, event_id)` |
| `plt_operation_audit` | 业务操作、权限决策和敏感动作审计 | 追加写；详情先脱敏再落库 |
| `plt_todo` | 统一待办身份、业务引用和同步状态 | 业务对象+节点+责任人+版本幂等；待办完成不能直接改业务状态 |
| `plt_authorization_grant` | `subject_type_code/subject_id/resource_context_code/resource_type_code/resource_id/action_code/scope_code/effective_from/effective_to/status_code/source_context_code/source_object_type/source_object_id/granted_by/granted_at/revoked_by/revoked_at/revoke_reason/version/current_marker` | `current_marker=1`占用当前授权键，撤权或已确认到期时置空；查询始终校验有效区间；唯一键为`(tenant_id, subject_type_code, subject_id, resource_context_code, resource_type_code, resource_id, action_code, scope_code, current_marker)`，并为主体、资源、动作、状态和有效区间建立组合索引；不代替DAC凭证授权 |
| `plt_migration_batch` | 迁移批次身份、manifest事实、状态、唯一来源分类计数和规则版本 | `uk(tenant_id, owner_context_code, purpose_code, release_id, source_system, source_table)`；只允许`IMPORTING/STAGED_READY/RECONCILING/COMPLETED/FAILED`，完成计数必须与来源总数相等 |
| `plt_migration_source_record` | 批次内不可变来源行、业务键、原始载荷和来源校验值 | `uk(tenant_id, batch_id, source_system, source_table, source_record_key)`；只允许在`IMPORTING`追加，游标按`tenant_id,batch_id,id`稳定读取 |
| `plt_external_key_mapping` | 来源行的`MAPPED`目标向量或`RETAINED`分类 | `uk(tenant_id, source_record_id, result_key)`；`MAPPED`必须有完整目标，`RETAINED`禁止携带目标，二者不得混合 |
| `plt_migration_issue` | 来源行确定性问题及追加式关闭事实 | `uk(tenant_id, source_record_id, issue_key)`；`OPEN`不得携带关闭事实，`CLOSED`必须携带处理人、规则版本、目标结果和完成时间 |
| `plt_change_request` | 项目变更申请、差异快照、审批引用和执行结果 | 申请 revision 只追加；变更执行按目标聚合版本幂等 |
| `ana_metric_definition` | 【建议】指标代码、口径版本、单位、粒度和来源 | 只有口径模型获批后创建；同一指标版本不可覆盖；不得从旧报表名称猜测公式 |

F-PLT-002只拥有上述三张动态表单表。用户REST创建的手工实例固定`owner_context=PLATFORM/object_type=MANUAL_DYNAMIC_FORM/object_id=instance_id`；F-SOL-003作为首个真实调用方，通过受信公共API以`SOL/REQUIREMENT_ANALYSIS/{preparationId}`创建业务实例。两类实例共用同一PLT真值，消费方不直读表。`PmsFileArtifact`业务键始终为`PLATFORM/DYNAMIC_FORM_INSTANCE/{instanceId}/FORM_FIELD_ATTACHMENT/{fieldKey}/{slotKey}`，普通值PATCH不得伪造文件向量。
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

本分册达到 API、事件、集成和并发/幂等设计的数据库前置条件；实际应用Schema变更仍以本仓目标分支“下一个未占用 Flyway 版本”为准。只有发布包含历史迁移或数据切换时，才须由`AI-MIG-000`在Release前完成真实批次的范围、水位、程序、校验、演练、对账和回退验证，并在批准窗口内执行；普通功能发布不适用。P3-E09不定义迁移批准哈希。

## 11. F-CUS-001与F-AST-001机器物理契约

| 表 | 关键字段 | 核心约束 |
|---|---|---|
| `cus_customer_master` | `customer_code/name/crm_level/crm_status/sales_owner/contact_phone/contact_email`、八个市场属性字段、平台扩展字段、来源元数据 | `uk(tenant_id, customer_code)`不随软删除释放；核心字段分列；旧`pms_customer`按原ID前向迁入 |
| `cus_customer_external_mapping` | `customer_id/source_system/source_key/source_version/event_id/is_current/payload_hash` | 当前CRM主映射按租户、来源和来源键唯一；同版本不同hash进入冲突 |
| `cus_customer_field_history` | `customer_id/field_code/before_digest/after_digest/source_system/source_version/operation_id/occurred_at` | 追加写，不保存敏感明文 |
| `cus_customer_location_reference` | `customer_id/location_type/location_id/location_version/reference_type/effective_from/effective_to` | location_type仅ADDRESS/SITE；同类型有效区间不得重叠 |
| `cus_customer_scope_slice` | `subject_type/subject_id/slice_no/market_codes/system_codes/expend_codes/industry_codes/office_codes/effective_from/effective_to` | 主体切片唯一；同维度OR、不同维度AND、多切片OR；普通角色无有效切片时查询为空 |
| `ast_device` | `serial_number`及AST平台字段 | `uk(tenant_id, serial_number)`不随软删除释放 |
| `ast_device_mes_snapshot` | `device_id/source_key/source_version/event_id/data_as_of/sync_status/payload_hash` | 来源事件幂等；一个有效MES对象映射一个Device |
| `ast_device_itr_snapshot` | `device_id/source_key/source_version/event_id/data_as_of/sync_status` | 按来源版本追加或受控更新当前副本 |
| `ast_device_current_customer_assignment` | `device_id/customer_id/relationship_version/assigned_at` | `uk(tenant_id, device_id)`；当前直接归属唯一 |
| `ast_device_customer_relationship` | `device_id/customer_id/relationship_type/effective_from/effective_to/source/operation_id` | 历史/租用/共管区间不得重叠；追加写 |

下载链接不落业务表；由文件能力按用户和文件生成默认5分钟、可配置的短期授权。具体列长、索引名和DDL由Technical Plan按本契约生成前向迁移。

## F-PROJ-008 P3-E09聚焦基线（GO）

结论：`NO_PHYSICAL_DELTA`。

阶段推进直接复用`proj_project`、`proj_project_stage`、`proj_project_gate`、`proj_project_gate_reference`、`proj_project_task`、`proj_project_milestone`、ACC唯一应交根、执行契约、`proj_project_stage_snapshot`及既有Outbox/审计载体。`proj_project_stage_snapshot`现有before/after stage、guard snapshot、provider facts、treeVersion、operationId、actor及唯一键足以承载`operation_type=STAGE_ADVANCE`。`ref_type`现有字符列加性使用受控`MILESTONE/APPROVAL`值不需要DDL。

APPROVAL/PROCESS不新建PMS映射表：`ref_code`只冻结Flowable `processDefinitionKey`；新写`ref_version`保持NULL，既有非空值仅保留历史且不得参与发布、启动、节点解析或门禁判断。`pms-module-integration`的流程Owner Provider在未显式选择定义ID时按key解析最新生效定义，显式选择历史`processDefinitionId`时验证其属于同一key且可启动，再以RuntimeService按实际定义ID启动；固定`businessKey=PROJECT_STAGE_GATE:{gateReferenceId}`并冻结tenantId、projectId、stageCode、gateId、gateReferenceId、refType、refCode、actor和实际processDefinitionId变量。启动时由服务端设置`PROCESS_START_USER_ID=actorUserId`、`PROCESS_STATUS=RUNNING(1)`及`_FLOWABLE_SKIP_EXPRESSION_ENABLED=true`，并通过Flowable `Authentication.setAuthenticatedUserId`在try/finally中设置、清除发起人；命令不接收可覆盖这些字段的客户端变量或自选审批人。事实Provider只按该businessKey、冻结变量和BPM实例实际定义ID读取Flowable运行/历史事实；没有实例、整数状态1运行中、3驳回、4撤回、2批准完成和未知分别按10/16分册判定。既有受管模板的历史`ref_version`不迁移、不覆盖，运行时按同一`ref_code`处理。禁止新增第二门禁结果表、阶段历史表、流程版本字段或修改旧Flyway；若实现期证明既有Flowable事实无法唯一承载上述关联，必须回到本Gate复审必要的加性事实，不得在Technical Plan静默补表。

## CUT-08、ACC-02与COM-01已批准前向载体

F-CUT-010以前向`NEW_ONLY`新增三张CUT-08表。`cut_spare_application_reference`按平台请求ID唯一保存任务、需求来源快照、外部系统、请求标识、可选跳转地址和外部申请号；外部申请身份按`tenant_id+external_system_code+external_application_no`唯一，多个合法申请以不同平台请求ID并存。`cut_spare_status_revision`按申请+正数外部状态版本只追加，当前标记只指向最高已接受版本，原始状态和只读JSON不得被CUT编辑。`cut_spare_manual_evidence`只保存PLT不可变文件事实引用、说明和操作审计，可关联任务或具体外部申请；人工证据不得生成外部申请号、状态版本或成功事实。三表不保存备件型号、数量、库存、审批、到货、领用或RMA明细，不迁移或双写旧`pms_cut_*`。精确列、可空联合、唯一键与锁序由`specs/features/F-CUT-010-physical-contract.json`锁定。

CutoverSpareApplicationReference物理表由CUT-08 Feature前向迁移确定；CutoverSpareStatusRevision物理表由CUT-08 Feature前向迁移确定；CutoverSpareManualEvidence物理表由CUT-08 Feature前向迁移确定。三者均为`NEW_ONLY`，不得从旧CUT、工程物料、URL、备注或状态文本反推外部申请、状态版本或人工证据。

统一导出使用ADR-0042已批准的PLT唯一Owner；ExportTask物理表由ACC-02 Feature前向迁移确定，不构成另一套ACC导出或审计事实。

| 表 | 关键字段 | 约束 |
|---|---|---|
| `plt_export_task` | `owner_context/export_type/operation_id/request_digest/actor_user_id/filter_snapshot/scope_snapshot/requested_fields_snapshot/include_files/scope_version/task_status/result_count`、公共文件事实、`expires_at/failure_code/failure_retryable/retry_count/version`及标准租户审计字段 | `uk(tenant_id, owner_context, export_type, actor_user_id, operation_id)`；REQUESTED→GENERATING→SUCCEEDED/FAILED/REJECTED，只有可重试FAILED可按version CAS回REQUESTED，只有SUCCEEDED可转EXPIRED；FAILED必须有失败码/可重试标记，retry_count从0递增；成功文件事实整组同时存在；不逻辑删除，不以`plt_operation_audit`替代 |
| `plt_export_audit` | `export_task_id/audit_sequence/action_code/actor_user_id/detail_snapshot/occurred_at`及租户/创建审计字段 | `uk(tenant_id, export_task_id, audit_sequence)`；REQUESTED/GENERATION_STARTED/SUCCEEDED/FAILED/REJECTED/RETRY_REQUESTED/DOWNLOADED/EXPIRED只追加；下载和TTL清理不得另建第二审计 |

AcceptanceScopeBinding物理表由COM-01 Feature前向迁移确定；复用本分册已声明的`acc_acceptance_scope_binding`。唯一键为`uk(tenant_id, project_id, project_stage_snapshot_id, delivery_scope_id, scope_allocation_version)`，只追加项目阶段快照与精确范围分配版本的绑定，不含`acceptance_id`，不从初验/终验报告推断历史绑定。Q-FCOM-002关闭前不自动关闭或解锁。

### F-COM-001合同管理员授权物理结论

当前统一规格已承接ADR-0038的公司范围规则：不新增合同授权表、关系表字段或SYSTEM物理变更；公司编码按原值精确相等，不做大小写折叠、名称映射或部门推导。scope ID/version仅进入既有AuditRecord授权快照，不复制到COM关系表。

本差量为`NO_PHYSICAL_DELTA`，不修改核心DDL、已发布Flyway、来源水位或历史批准记录。

ACC-01继续保留原核心模型已声明的`acc_acceptance_item`目标引用；它不承接F-ACC-001的新报告版本或附件真值。ACC-02直接复用ACC唯一应交根的`acc_project_deliverable_source_version`和`acc_project_deliverable_source_attachment`，不建立第二套来源历史或附件Owner。

## ACC-03/04/02已批准Feature-forward细则

以下恢复ADR-0039～0042已批准规则；文中V17/V63/V133为来源设计历史编号，当前执行迁移以仓库DU接收映射为准，不得重放或改写当前同号迁移。

F-ACC-001的P3-E09聚焦差量为`FEATURE_FORWARD_DELTA_REQUIRED`，不修改已执行V17、V63或当前核心DDL：

| 目标表 | 字段差量 | 约束与迁移边界 |
|---|---|---|
| `acc_acceptance` | `project_id/project_task_id/execution_contract_id/acceptance_type/activity_status/current_report_version_id/version`及标准租户审计字段 | `uk(tenant_id, project_id, acceptance_type)`、`uk(tenant_id, project_task_id)`；仅`PRELIMINARY/FINAL`与`PENDING/COMPLETED`；跨Context只存逻辑引用，不建PROJ外键 |
| `acc_acceptance_report_version` | `acceptance_id/report_version_no/report_status/acceptance_time/conclusion_code/conclusion_text/acceptor_name/previous_version_id/effective_from/effective_to/current_marker/uploader_user_id/upload_time`、`publisher_user_id bigint NULL`及标准租户审计字段 | `report_status`仅`DRAFT/EFFECTIVE/SUPERSEDED/REVOKED`；`current_marker`生成表达式为`case when report_status='EFFECTIVE' and effective_to is null then 1 else null end`；`uk(tenant_id, acceptance_id, report_version_no)`、`uk(tenant_id, acceptance_id, current_marker)`；DRAFT三时间/marker及`publisher_user_id`为空，发布时把服务端认证用户写入`publisher_user_id`，EFFECTIVE及其后继历史状态必须非空且不可改；有效版本四项非空且生效后不可更新/删除 |
| `acc_acceptance_report_attachment` | `report_version_id bigint NOT NULL`、`attachment_sequence int unsigned NOT NULL`、`file_artifact_id bigint NOT NULL`、`file_version_no int unsigned NOT NULL`、`reference_key varchar(64) COLLATE utf8mb4_0900_bin NOT NULL`、`artifact_version/reference_version/availability_version int unsigned NOT NULL`、`scope_version bigint NOT NULL`、`file_hash char(64) COLLATE ascii_bin NOT NULL`及标准租户审计字段 | `uk(tenant_id, report_version_id, attachment_sequence)`、`uk(tenant_id, report_version_id, reference_key)`、`uk(tenant_id, report_version_id, file_artifact_id, file_version_no)`；逐项保存PLT公共`FileArtifactVersionFact`，不保存PLT内部`FileVersion.id/FileReference.id`、正文或主附件推断 |
| `acc_project_deliverable` | 加性新增`current_source_version_id/archive_status` | 保持V63 `uk(tenant_id, project_id, deliverable_code)`；F-ACC-001只允许`D-INITIAL-REPORT/D-FINAL-REPORT`；当前来源指针可空，未完成归档不得写`ARCHIVED` |
| `acc_project_deliverable_source_version` | `deliverable_id/source_requirement_id/source_object_type/source_object_id/source_version/relation_status/archive_status/archive_failure_code/archive_retry_count/archive_time/current_marker`及标准租户审计字段 | `relation_status`仅`CURRENT/SUPERSEDED/REVOKED`；`current_marker`生成表达式为`case when relation_status='CURRENT' then 1 else null end`；`uk(tenant_id, deliverable_id, source_object_type, source_object_id, source_version)`、`uk(tenant_id, deliverable_id, current_marker)`；替换/撤销保留旧行 |
| `acc_project_deliverable_source_attachment` | `deliverable_source_version_id bigint NOT NULL`、`attachment_sequence int unsigned NOT NULL`、`file_artifact_id bigint NOT NULL`、`file_version_no int unsigned NOT NULL`、`reference_key varchar(64) COLLATE utf8mb4_0900_bin NOT NULL`、`artifact_version/reference_version/availability_version int unsigned NOT NULL`、`scope_version bigint NOT NULL`、`file_hash char(64) COLLATE ascii_bin NOT NULL`及标准租户审计字段 | `uk(tenant_id, deliverable_source_version_id, attachment_sequence)`、`uk(tenant_id, deliverable_source_version_id, reference_key)`、文件公共版本复合唯一；逐项等于Owner事件完整附件公共事实集合，不选择或推断主附件 |

### F-ACC-002满意度Feature-forward聚焦差量（ADR-0041）

| 表 | 字段/差量 | 约束与Owner规则 |
|---|---|---|
| `acc_satisfaction_questionnaire_template` | `template_code/name/status/current_revision_id/version`及标准租户审计字段 | ACC Owner；`uk(tenant_id, template_code)`；当前发布指针可空且不得指向草稿 |
| `acc_satisfaction_questionnaire_template_revision` | `template_id/revision_no/project_type/signing_mode/implementation_mode/business_purpose_code/applicable_timing_code/priority/frozen_question_json/frozen_threshold/rule_version/revision_status/effective_from/effective_to` | 只追加修订；`uk(tenant_id, template_id, revision_no)`；相同五维输入最高优先级并列视为歧义失败。`frozen_question_json`根固定`schemaVersion=1/questions/scoring`；题型仅`SINGLE_CHOICE/MULTIPLE_CHOICE/RATING/TEXT`，策略仅`SUM_V1/WEIGHTED_AVERAGE_V1`，舍入仅`HALF_UP/HALF_EVEN/DOWN`；`frozen_threshold/rule_version`必须分别等于配置内threshold/ruleVersion |
| `proj_project_task` | 增加`acc_satisfaction_template_id/template_revision_id/template_version/satisfaction_rule_version/satisfaction_threshold` | 仅`satisfaction_timing`非空任务可写；逐项来自`SatisfactionQuestionnaireTemplateApi`同一次解析Fact，不按任务名/码推断 |
| `acc_satisfaction_collection_task` | 增加`project_task_id/source_owner_context/source_object_type/source_object_id/source_object_version/trigger_owner_context/trigger_object_type/trigger_fact_id/trigger_fact_version/collection_key/task_revision_no/prior_task_id/assigned_by_user_id` | `uk(tenant_id, collection_key, task_revision_no)`保证链内revision唯一；`uk(tenant_id, project_task_id, trigger_owner_context, trigger_object_type, trigger_fact_id, trigger_fact_version)`保证同触发Fact幂等。首次`source*=trigger*`且revision=1；整改revision复制首任务`source*`，trigger固定为`ACC/SatisfactionRemediationFact`，不得形成第二来源真值 |
| `acc_satisfaction_questionnaire` | 增加`questionnaire_status/access_scope_version` | 状态仅ACTIVE/SUBMITTED/INVALIDATED/EXPIRED；规范化后完整复制已发布配置包及threshold/ruleVersion强一致投影，形成后不可改 |
| `acc_satisfaction_access_grant` | `questionnaire_id/grant_version/token_digest/effective_from/expires_at/grant_status/consumed_at`及审计字段 | token摘要唯一；状态ACTIVE/CONSUMED/REVOKED/EXPIRED；完整令牌永不落库 |
| `acc_satisfaction_response` | 使用`submit_channel/customer_contact_ref/assisted_by_user_id`替代泛化签字/附件JSON | `uk(tenant_id, questionnaire_id, response_no)`、`uk(tenant_id, questionnaire_id, request_id)`；答卷只追加 |
| `acc_satisfaction_response_file` | `response_id/file_role/file_sequence`及PLT公共`artifact_id/version_no/reference_key/artifact_version/reference_version/availability_version/scope_version/file_hash` | role仅SIGNATURE/ATTACHMENT；签字sequence=1且恰一条，附件顺序唯一；不保存PLT内部主键 |
| `acc_satisfaction_result` | 增加`collection_key/result_status/effective_from/effective_to/current_marker/archive_actor_user_id/deliverable_source_version_id/archive_failure_code/archive_retry_count/invalidated_by_user_id/invalidated_at/invalidation_reason_code/invalidation_reason_summary` | current_marker仅`result_status='EFFECTIVE' and passed=1 and effective_to is null`时为1；`uk(tenant_id, collection_key, current_marker)`；判定业务字段只追加，正式失效命令只允许当前有效达标Result一次性关闭区间并记录原因 |
| `acc_satisfaction_result_file` | `result_id/file_role/file_sequence`及完整PLT公共文件事实 | role仅RESULT_DOCUMENT/SIGNATURE/ATTACHMENT；结果文档恰一条，完整有序集合冻结 |
| `acc_satisfaction_remediation_fact` | `prior_result_id/remediation_revision_no/remediation_request_id/evidence_summary/evidence_file_fact_version/completed_by/completed_at/fact_version`及标准租户审计字段 | 只追加；`uk(tenant_id, prior_result_id, remediation_revision_no)`、`uk(tenant_id, prior_result_id, remediation_request_id)`；必须引用FAILED或INVALIDATED结果，形成后不可更新/删除，同requestId同载荷重放返回原Fact，异载荷冲突 |

配置包中`questions[]`公共字段固定为`code/title/type/required`。单选、多选与评分量表增加非空有序`options[{code,label,score}]`；MULTIPLE_CHOICE的`minSelections/maxSelections`均为必填非空整数并满足`1<=minSelections<=maxSelections<=options数量`，TEXT的`minLength/maxLength`均为必填非空整数并满足`0<=minLength<=maxLength`；不适用于当前题型的参数必须缺失，不能写null或依赖默认值。参与`WEIGHTED_AVERAGE_V1`的计分题增加正`weight`，`SUM_V1`禁止weight。`scoring`固定为`ruleVersion/strategy/scoreMin/scoreMax/precision/roundingMode/threshold`，V1的scoreMin必须为0；单选/量表题最大可达分为最大option score，多选题最大可达分为全部合法去重选择集合的option score算术平均最大值；SUM的scoreMax等于各计分题最大可达分之和，加权平均的scoreMax等于各计分题最大可达分的加权平均，threshold必须位于0..scoreMax。最低选2项、option分值100/0的多选题最大可达分为50，threshold=80必须拒绝发布。decimal以JSON字符串表达并必须可无损转换为`DECIMAL(7,2)`；precision仅0、1、2。发布校验失败不得写PUBLISHED状态或根current指针。

答卷`answer_snapshot`根固定为`answers`，元素固定`questionCode/value`：单选/评分为一个code，多选为去重code数组，文本为字符串；其他根字段、客户端score/passed/threshold/weight/strategy及任何未知/重复/类型不符值均在Response前拒绝。结构合法但缺必答时仍保存Response；未答计分题按0计入，文本不计分。`SUM_V1=sum(questionScore)`；`WEIGHTED_AVERAGE_V1=sum(questionScore*weight)/sum(weight)`；多选questionScore为已选option score算术平均。中间值不舍入，最终总分仅按冻结precision/roundingMode舍入一次并用舍入后值比较threshold。

本补充不改变当前表结构。已执行V133不可修改；下一前向迁移只按固定tenant/creator、高段根及revision身份处理V133三组PUBLISHED受管种子：追加revision 2完整配置包、关闭revision 1有效区间并更新根current_revision_id，旧revision保留；停用种子保持停用，普通业务行、部分命中或身份冲突不得进入种子分支。P3-E09=`NO_STRUCTURAL_DELTA / FORWARD_MANAGED_SEED_REVISION_REQUIRED`。

满意度来源投影直接复用`acc_project_deliverable`及其source_version/source_attachment，`source_object_type=SatisfactionResult`：投影先以Result冻结的`projectTaskId`向PROJ重验同租户同项目且`taskCode=T-SAT-SURVEY`，再按`tenant_id+project_id+deliverable_code=D-SAT-REPORT`精确锁定一行，并要求根的`task_code=T-SAT-SURVEY`。缺失、重复、项目/任务错配均在写来源前失败并保持`PENDING_COMPENSATION`；禁止按中文名称、其他交付件或任意一行推断。RECORDED置CURRENT前必须以Result ID/version重验ACC Owner：精确版本仍为EFFECTIVE且passed并且没有更新当前Result时才允许切换；已INVALIDATED或已有更新结果时，旧RECORDED只追加/保留非当前历史与历史归档资格。INVALIDATED把对应来源置REVOKED，并且仅当根当前指针仍精确指向该Result及版本时清空。两种事件任一乱序均不得恢复失效版本或清除更新来源。历史ACTIVE文件、归档记录和待补偿历史归档资格均保留。归档失败保持`PENDING_COMPENSATION`，不回滚Result。

上述Feature-forward目标不创建核心模型草案中的`acc_satisfaction_response.signature_ref/attachment_refs_json`或`acc_satisfaction_result.archive_artifact_id/archive_payload_sha256`；签字、附件、结果文档和交付件来源均以规范化PLT公共文件事实及来源版本ID承载。不得让Technical Plan在JSON引用与规范化子表之间自行选择。

已有当前V1时可以并存任意数量DRAFT，因为其生成`current_marker=NULL`。发布V2在单一ACC事务中按活动根→V1→V2锁定，先把V1置`SUPERSEDED/effective_to=now`，再把V2置`EFFECTIVE/effective_from=now`、把本次服务端认证用户冻结为`publisher_user_id`并更新活动当前指针；任一步失败整体回滚。撤销锁定活动和当前版本后置`REVOKED/effective_to=now`并把活动当前指针清空，不提升旧版本且不改原发布人。首次发布、替换和撤销都在唯一键检查与Outbox写入成功后提交。

交付件事件消费在单一ACC事务中处理根、来源关系和附件集合：首次生效创建CURRENT/PENDING_COMPENSATION关系并设置根指针；替换先把旧关系置SUPERSEDED并保留其归档结果，再创建新CURRENT关系与完整附件集合并切换根；撤销把旧关系置REVOKED/INVALID、清空根指针并把根归档摘要置INVALID。事件重放只能返回上述既有结果，不重复关系或附件。

ACC报告附件集合固定使用`ownerContext=ACC/objectType=ACCEPTANCE_REPORT_VERSION/objectId=reportVersionId十进制字符串/purposeCode=ACCEPTANCE_REPORT_ATTACHMENT`；归档集合只把purpose改为`ACCEPTANCE_REPORT_ARCHIVE`，同一文件复用服务端UUID `reference_key`。`scope_version`精确等于ACC Provider通过PROJ `ProjectScopeApi`取得的当前`treeVersion`。报告发布、活动完成与下载只重验附件ACTIVE集合；PLT归档在独立集合创建ARCHIVED引用并追加`FileArchiveRecord`，报告附件ACTIVE引用保持不变。ACC归档字段只保存索引/补偿投影，整组成功后方可写`ARCHIVED`。

新项目创建按“全部ProjectTask→非ACC执行契约→里程碑→既有ACC `ProjectDeliverableInitializationApplicationService`形成`acc_project_deliverable`应交根→`AcceptanceActivityInitializationApi.initialize`→ACC当前执行契约”的顺序在同一MySQL事务完成。PROJ为精确初验/终验任务预分配`execution_contract_id`，ACC校验自身应交根并返回`acceptance_id/activity_version`后，PROJ才插入`targetContextCode=ACC/targetObjectType=AcceptanceActivity/targetObjectKey=acceptanceId`的当前契约；PROJ不直接写ACC表，任一步失败整体回滚。

存量切换以同项目的`T-INITIAL-ACCEPT/T-FINAL-ACCEPT`精确任务对为最小单元：两项均不存在保持不变；部分、重复、缺精确应交根或当前契约非V63 `TASK_NATIVE`整批失败；两项均处`PENDING_ASSIGN/PENDING_START/IN_PROGRESS/PENDING_ACCEPT`时原子创建两个PENDING活动、关闭两条旧契约区间并追加ACC当前契约；两项均处`DONE/CLOSED`时该项目全部保持旧契约和历史且不创建活动；终态/非终态混合或未知状态整批失败。不得覆盖终态历史或为不可再次完成任务创建孤立活动。

V17 `pms_acc_acceptance`及旧交付清单/归档/完工证明缺少可证明的验收人、固定文件版本、活动绑定或当前版本关系，保持旧表和旧功能不变，不进入新当前真值。未来前向迁移不得从名称、审批状态、`approve_opinion`、URL、`D-ACCEPT-REPORT`或旧关项结果补造这些事实。

两张物理表由ACC-02 Feature前向迁移确定并由PLT Owner持有，ACC及其他消费Context不得直写。`ExportTaskExecutionJob`只按Task版本CAS领取REQUESTED；原申请actor的显式retry命令重验权限后才可把`FAILED + failure_retryable=1`恢复为REQUESTED并递增retry_count。结果文件目标固定`PLATFORM/EXPORT_TASK/{taskId}/EXPORT_FILE`。`ExportFileExpirationJob`只处理SUCCEEDED到期文件并追加审计，FAILED/REJECTED不得转EXPIRED，Task/Audit永久保留。

## 修订017物理契约与前向边界

本节索引中的创建时间列统一使用`create_time`，不保留旧`created_at`别名。

本节全部参考表的通用字段以2.1为准：version INT、creator/updater VARCHAR(64)、create_time/update_time DATETIME(3)、deleted BIT(1)。不可变历史保留deleted=0且不提供删除接口；已有同名表保留既有业务列，不以参考DDL重建。文件名中的016仅保留来源标识，当前基线由机器定义baseline字段标识为017。

本节冻结新的目标表、字段类型、空值、主键、唯一/检查约束与正确性索引，不把未来迁移描述成已经实施。机器定义唯一位于`docs/traceability/sds-revision-016-physical-contract.json`；`scripts/generate_sds_revision_016_schema.py`派生参考DDL `specs/001-project-delivery-platform/appendices/sds-revision-016-carriers.mysql.sql`。参考DDL仅在新建隔离schema执行，不是Flyway或存量升级脚本。

沿用ADR-0030/0031的FEATURE_FORWARD_MIGRATION边界：物理表由PM-03 Feature前向迁移确定；物理表由PM-06 Feature前向迁移确定；物理表由COM-01 Feature前向迁移确定；物理表由ACC-03 Feature前向迁移确定；物理表由CLO-02 Feature前向迁移确定。这里“确定”是实施时核对已有表/创建前向迁移，不允许再改变本节已冻结的Owner、字段语义或关键约束。已部署同名表只能兼容演进，禁止先删后建。

| 目标表 | Owner | 字段（类型与空值见机器定义） | 关键约束 |
|---|---|---|---|
| `proj_delivery_definition_revision` | PROJ | `id`、`tenant_id`、`definition_kind`、`definition_code`、`revision_no`、`revision_state`、`schema_version`、`payload`、`published_at`、`disabled_at`、`version` | PRIMARY KEY (`id`)；UNIQUE KEY `uk_pdd_revision` (`tenant_id`, `definition_kind`, `definition_code`, `revision_no`)；UNIQUE KEY `uk_pdd_tenant_id` (`tenant_id`, `id`)；CONSTRAINT `ck_pdd_kind` CHECK (definition_kind IN ('STAGE','TASK','DELIVERABLE','WORK_BINDING','COMPLETION_RULE','PERMISSION_POLICY','GATE','MILESTONE'))；CONSTRAINT `ck_pdd_version` CHECK (revision_no > 0 AND schema_version > 0)；CONSTRAINT `ck_pdd_state` CHECK (revision_state IN ('DRAFT','PUBLISHED'))；CONSTRAINT `ck_pdd_published` CHECK ((revision_state='DRAFT' AND published_at IS NULL) OR (revision_state='PUBLISHED' AND published_at IS NOT NULL))；CONSTRAINT `ck_pdd_payload` CHECK (JSON_TYPE(payload) = 'OBJECT') |
| `proj_delivery_definition_reference` | PROJ | `id`、`tenant_id`、`owner_revision_id`、`reference_key`、`target_revision_id` | PRIMARY KEY (`id`)；UNIQUE KEY `uk_pdr_slot` (`tenant_id`, `owner_revision_id`, `reference_key`)；KEY `idx_pdr_target` (`tenant_id`, `target_revision_id`)；CONSTRAINT `ck_pdr_not_self` CHECK (owner_revision_id <> target_revision_id)；CONSTRAINT `fk_pdr_source` FOREIGN KEY (`tenant_id`,`owner_revision_id`) REFERENCES `proj_delivery_definition_revision` (`tenant_id`,`id`)；CONSTRAINT `fk_pdr_target` FOREIGN KEY (`tenant_id`,`target_revision_id`) REFERENCES `proj_delivery_definition_revision` (`tenant_id`,`id`) |
| `proj_stage_transition_definition` | PROJ | `id`、`tenant_id`、`template_revision_id`、`transition_code`、`from_stage_code`、`to_stage_code`、`condition_rule_revision_id`、`priority`、`is_default`、`default_marker`、`revision_no` | PRIMARY KEY (`id`)；UNIQUE KEY `uk_std_code` (`tenant_id`, `template_revision_id`, `transition_code`)；UNIQUE KEY `uk_std_default` (`tenant_id`, `template_revision_id`, `from_stage_code`, `default_marker`)；KEY `idx_std_out` (`tenant_id`, `template_revision_id`, `from_stage_code`, `priority`)；CONSTRAINT `ck_std_flags` CHECK (is_default IN (0,1) AND revision_no > 0)；CONSTRAINT `ck_std_self` CHECK (from_stage_code <> to_stage_code) |
| `proj_project_stage_transition` | PROJ | `id`、`tenant_id`、`project_id`、`template_revision_id`、`source_transition_id`、`transition_revision`、`from_stage_id`、`to_stage_id`、`priority`、`is_default`、`default_marker`、`condition_snapshot`、`graph_version` | PRIMARY KEY (`id`)；UNIQUE KEY `uk_pst_edge` (`tenant_id`, `project_id`, `graph_version`, `source_transition_id`)；UNIQUE KEY `uk_pst_default` (`tenant_id`, `project_id`, `graph_version`, `from_stage_id`, `default_marker`)；KEY `idx_pst_out` (`tenant_id`, `project_id`, `graph_version`, `from_stage_id`, `priority`)；CONSTRAINT `ck_pst_versions` CHECK (transition_revision > 0 AND graph_version > 0)；CONSTRAINT `ck_pst_self` CHECK (from_stage_id <> to_stage_id)；CONSTRAINT `ck_pst_default` CHECK (is_default IN (0,1)) |
| `proj_project_stage_execution_contract` | PROJ | `id`、`tenant_id`、`project_id`、`stage_id`、`binding_version`、`binding_type`、`binding_snapshot`、`permission_policy_revision_id`、`completion_rule_revision_id`、`effective_from`、`effective_to`、`current_marker`、`version` | PRIMARY KEY (`id`)；UNIQUE KEY `uk_psec_version` (`tenant_id`, `stage_id`, `binding_version`)；UNIQUE KEY `uk_psec_current` (`tenant_id`, `stage_id`, `current_marker`)；KEY `idx_psec_project` (`tenant_id`, `project_id`, `stage_id`)；CONSTRAINT `ck_psec_version` CHECK (binding_version > 0)；CONSTRAINT `ck_psec_time` CHECK (effective_to IS NULL OR effective_to >= effective_from)；CONSTRAINT `ck_psec_type` CHECK (binding_type IN ('STAGE_NATIVE','BUSINESS_OBJECT','BUSINESS_COMPONENT','DYNAMIC_FORM','APPROVAL','COMPOSITE')) |
| `plt_business_view_revision` | PLT | `id`、`tenant_id`、`entity_type`、`view_key`、`revision_no`、`owner_context`、`component_key`、`component_version`、`context_schema`、`supported_actions`、`query_provider_key`、`command_provider_key`、`permission_provider_key`、`published_at`、`disabled_at` | PRIMARY KEY (`id`)；UNIQUE KEY `uk_bvr_identity` (`tenant_id`, `entity_type`, `view_key`, `revision_no`)；CONSTRAINT `ck_bvr_version` CHECK (revision_no > 0)；CONSTRAINT `ck_bvr_actions` CHECK (JSON_TYPE(supported_actions)='ARRAY')；CONSTRAINT `ck_bvr_schema` CHECK (JSON_TYPE(context_schema)='OBJECT') |
| `proj_contract_scope_append_request` | PROJ | `id`、`tenant_id`、`project_id`、`operation_id`、`request_digest`、`request_revision`、`expected_project_version`、`expected_scope_version`、`request_snapshot`、`impact_snapshot`、`bpm_process_definition_key`、`actual_process_definition_id`、`process_instance_id`、`approval_fact_ref`、`apply_state`、`applied_scope_version`、`applied_at`、`version` | PRIMARY KEY (`id`)；UNIQUE KEY `uk_csar_operation` (`tenant_id`, `project_id`, `operation_id`)；KEY `idx_csar_project` (`tenant_id`, `project_id`, `create_time`, `id`)；CONSTRAINT `ck_csar_revision` CHECK (request_revision > 0)；CONSTRAINT `ck_csar_apply` CHECK ((apply_state='NOT_APPLIED' AND applied_scope_version IS NULL AND applied_at IS NULL) OR (apply_state='APPLIED' AND applied_scope_version IS NOT NULL AND applied_scope_version > expected_scope_version AND applied_at IS NOT NULL AND approval_fact_ref IS NOT NULL)) |
| `com_delivery_scope_project_version` | COM | `id`、`tenant_id`、`project_id`、`scope_version`、`version` | PRIMARY KEY (`id`)；UNIQUE KEY `uk_cspv_project` (`tenant_id`, `project_id`) |
| `com_project_scope_revision` | COM | `id`、`tenant_id`、`project_id`、`scope_version`、`previous_scope_version`、`origin_context`、`origin_record_id`、`origin_revision`、`scope_snapshot`、`scope_digest`、`difference_snapshot` | PRIMARY KEY (`id`)；UNIQUE KEY `uk_cpsr_version` (`tenant_id`, `project_id`, `scope_version`)；UNIQUE KEY `uk_cpsr_source` (`tenant_id`, `origin_context`, `origin_record_id`, `origin_revision`)；CONSTRAINT `ck_cpsr_previous` CHECK (previous_scope_version IS NULL OR scope_version > previous_scope_version)；CONSTRAINT `ck_cpsr_json` CHECK (JSON_TYPE(scope_snapshot)='ARRAY' AND JSON_TYPE(difference_snapshot)='OBJECT') |
| `acc_acceptance_report` | ACC | `id`、`tenant_id`、`project_id`、`report_type`、`current_revision_id`、`version` | PRIMARY KEY (`id`)；UNIQUE KEY `uk_aar_type` (`tenant_id`, `project_id`, `report_type`)；UNIQUE KEY `uk_aar_tenant_id` (`tenant_id`, `id`)；CONSTRAINT `ck_aar_type` CHECK (report_type IN ('PRELIMINARY','FINAL')) |
| `acc_acceptance_report_revision` | ACC | `id`、`tenant_id`、`report_id`、`revision_no`、`project_scope_version`、`scope_revision_id`、`scope_digest`、`conclusion_code`、`accepted_at`、`acceptor_reference`、`file_artifact_id`、`file_version`、`file_digest`、`template_revision_id`、`initial_report_revision_id`、`source_evidence_refs`、`supersedes_revision_id` | PRIMARY KEY (`id`)；UNIQUE KEY `uk_aarr_version` (`tenant_id`, `report_id`, `revision_no`)；KEY `idx_aarr_scope` (`tenant_id`, `scope_revision_id`)；CONSTRAINT `ck_aarr_version` CHECK (revision_no > 0 AND file_version > 0)；CONSTRAINT `fk_aarr_report` FOREIGN KEY (`tenant_id`,`report_id`) REFERENCES `acc_acceptance_report` (`tenant_id`,`id`) |
| `proj_project_exit_record` | PROJ | `id`、`tenant_id`、`project_id`、`project_version`、`closure_type`、`closed_from_stage`、`stage_instance_id`、`source_context`、`source_record_id`、`source_record_revision`、`template_revision_id`、`scope_version`、`gate_snapshot_ref`、`closed_at` | PRIMARY KEY (`id`)；UNIQUE KEY `uk_per_project_version` (`tenant_id`, `project_id`, `project_version`)；UNIQUE KEY `uk_per_source` (`tenant_id`, `source_context`, `source_record_id`, `source_record_revision`)；CONSTRAINT `ck_per_type` CHECK ((closure_type IN ('NORMAL','NO_TRACKING') AND source_context='ACC') OR (closure_type='EXCEPTION' AND source_context='PROJ'))；CONSTRAINT `ck_per_stage` CHECK (closed_from_stage IN ('S0','S1','S2','S3','S4','S5','S6')) |

所有身份为BIGINT UNSIGNED，租户维度进入业务唯一键；业务编码使用二进制排序避免大小写归并；版本字段为BIGINT UNSIGNED，业务修订>0、COM范围初始水位可为0。日期为DATETIME(6)，统一业务时间来源。不可变版本没有通用删除或覆盖接口；只允许受控当前指针、停用时间和合法区间结束更新，且保存追加审计。JSON正文不是索引键；高频查询只使用项目、身份、类型、版本和来源列。

同Owner引用中定义引用边及报告版本采用含tenant的物理外键；跨Owner的Project、COM scope、FileArtifact及BPM只保存逻辑引用，提交时通过Owner API锁定重验。报告根current_revision_id必须指向同租户同report_id的版本，指针切换和引用重验同事务；数据库FK不能代替该业务检查。主绑定当前唯一使用生成标记；图唯一默认分支使用生成标记；无环/全可达/唯一开始/分支可判定在发布事务执行，数据库不靠一个CHECK宣称完成图校验。

### PM-03业务视图来源加性字段（2026-09-08）

按已批准页面/动态表单复用方案，`plt_business_view_revision`增加`view_source VARCHAR(16) NOT NULL`及`dynamic_form_revision_id BIGINT NULL`。PAGE要求表单修订为NULL；DYNAMIC_FORM要求非空正数表单修订，实际同租户/发布/用途兼容由PLT表单Owner API核对。`published_at`允许NULL以保存草稿；`disabled_at`仅能在已经发布且不早于发布时间时写入。已有Owner、组件、Provider、上下文Schema与动作列不删除，页面/表单只改变展示来源，不改变业务对象Owner。

机器合同是本次具体字段/约束的唯一来源；修改只涉及尚未部署的业务视图目标表，不回写其他载体的历史批准状态或PRD身份。参考DDL生成不等于Flyway升级验证。实际迁移、菜单/权限和API必须在相应写边界交接后实施，不以参考DDL直接修改运行库。

### PM-03模板精确组合与冻结闭包

沿用现有`proj_project_template_*`身份/明细表；本次只增加：

| 已有表 | 加性字段 |
|---|---|
| proj_project_template | version INT NOT NULL DEFAULT 0，身份/草稿/发布/停用等写入递增，用于复制源的If-Match；不改变业务状态 |
| proj_project_template_revision | definition_snapshot JSON NULL，发布时保存精确引用定义闭包；不是当前可修改定义库副本 |
| proj_project_template_stage_definition | definition_revision_id BIGINT NULL、start_node BIT(1) NULL、terminal_node BIT(1) NULL、work_binding_revision_id BIGINT NULL、permission_policy_revision_id BIGINT NULL、completion_rule_revision_id BIGINT NULL |
| proj_project_template_task_definition | definition_revision_id BIGINT NULL、work_binding_revision_id BIGINT NULL、permission_policy_revision_id BIGINT NULL、completion_rule_revision_id BIGINT NULL |
| proj_project_template_milestone_definition | definition_revision_id BIGINT NULL |
| proj_project_template_deliverable_definition | definition_revision_id BIGINT NULL |
| proj_project_template_gate_definition | definition_revision_id BIGINT NULL |

列NULL仅保留历史可解释性；新发布按SDS10要求解析实际定义与节点绑定，不把历史NULL当作有效规则。引用指向同租户且类型正确的精确DeliveryConfigurationRevision；场景级覆盖继续由现有明细承载，不能覆盖Owner或授权接口。旧已发布明细不回填推断引用或start/terminal；新图由显式定义保存，sort_order只作显示。历史运行切换限制见Q-FPROJ009-001，不由加列迁移自动完成。

### 原有载体复用与迁移处置

ProjectTemplateVersion复用`proj_project_template_revision`；ProjectStage复用`proj_project_stage`；TaskWorkBinding和TaskCompletionRule复用`proj_project_task_execution_contract`。可复用定义版本通过现有源引用及冻结快照与新增版本库关联，不能把sort_order推断为缺失的业务转移关系。既有项目换图必须显式审批并生成来源映射；不能从S0～S6编号自动回填缺失Stage或边。

ProjectScopeVersion复用已在COM正式设计定义的`com_delivery_scope_project_version`唯一水位，新增不可变revision只存历史；不新增第二个水位表。历史缺失scopeVersion、范围数量或Owner证据时保持待核对，不使用最大ID、默认数量或当前合同反写历史。旧MultiPhaseProjectGroup三类表不再是PM-06目标；历史原值只读保留，绝不自动转换为追加申请。

AcceptanceReportRevision不能从旧报告上传成功反推PASS，必须保留原结论、范围和证据。缺少范围或签字等必要事实时仅作历史来源，不能生成当前验收通过。ProjectExitRecord只能由CLO/PM-10合法关闭事务产生；对已闭环历史项目只在有真实阶段/来源证据时受控补映射，不假设S6，不把NORMAL推断为NO_TRACKING。

### 验证与发布

参考DDL必须通过生成漂移、对象/Owner/字段/约束检查和MySQL8.4隔离执行及唯一/越租户/默认分支/版本/退出来源负向用例。该证据只能证明所列新载体的Schema设计，不证明已有库升级、Provider实现或业务E2E。进入各Feature实施前仍需正式物理契约、前向Flyway、升级/兼容/回滚或前滚及业务并发测试。

历史核心DDL保持原字节，P3-E09的旧独立批准只对该未变CORE_MIGRATION_SUBSET有效。不得把未修改核心DDL解释为本节所有新表已部署或独立批准。AI-MIG-000仅在实际发布包含历史数据迁移/切换时执行；普通新功能发布不自动适用。独立复审和需求方批准的当前状态只由Gate记录，参考DDL执行不代替签署。

## 修订018：独立验收的物理差量边界

Requirement：PM-03、PM-11、ACC-03、ACC-04、COM-01。逻辑边界由ADR-0045确认；本节不提供已经通过P3-E09的新版DDL。

现有V166的acc_acceptance强制project_task_id/execution_contract_id，且初始化器固定两组任务编码；现有acc_acceptance_scope_binding依赖真实阶段快照。这些是旧实现约束，不能证明S5外独立终验已可运行，也不能直接将必填字段改空而丢失权限和来源。

Phase 2必须形成一份精确物理/API差量后再实施：

| 对象 | 必须保留 | 待定稿的技术落点 |
|---|---|---|
| AcceptanceActivity | ACC Owner、既有项目/类型身份、当前报告与不可变历史、业务范围和责任追溯 | 独立创建身份与可选Stage/Task来源的保存方式，不以固定任务码创建 |
| Stage/Task WorkBinding | 一节点一个当前主契约，版本及目标引用可追溯 | 延迟创建前后的目标解析及关联原子性，不伪造目标ID |
| AcceptanceScopeBinding | COM真实范围版本、ACC绑定Owner、已绑定范围保护、既有来源历史 | 非阶段触发的业务身份、范围确认时点、唯一/索引约束及新旧来源解释 |
| 受控触发 | 来源事实/版本、配置版本、明确业务意图和重放结果 | 复用现有命令幂等/Outbox的范围及事务完成点，不预建第二套执行框架 |

Q-TPLACC-001关闭前上述新增创建和范围路径BLOCKED_BY_SPEC。既有物理合同、参考Schema、已执行Flyway和历史批准原样保留，只证明原范围；本次不创建/改写表、约束或迁移。新配置发布不原位覆盖既有项目，数据切换另行授权。业务规则可配置不等于身份唯一、非空可信来源、引用完整性和历史保护可关闭。
