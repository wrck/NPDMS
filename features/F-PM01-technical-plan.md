# F-PM01 Technical Plan

> Feature ID：`F-PM01`（对应 Spec：`features/F-PM01-project-manual-creation.md`）
> 文档状态：`PLANNED`（待评审后进入 T1）
> 规格基线：快照 @ `28061c3`；需求矩阵 PM-01 Feature 列 → `F-PM01`
> 本计划不重新定义领域、权限或状态语义；业务规则以 F-PM01 Spec 第4节 BR-1～BR-9 为准。

## 1. 输入与边界回顾

- 需求：PM-01 手工创建部分（PRD V1.7 4.2.1）；消费 F-PM03 模板匹配（`TemplateMatcher`、PUBLISHED revision 只读契约）；
- 迁移契约：Project=`STRUCTURED`（gate=`AI-MIG-000`）→ 本 Feature 仅前向新建目标表，无历史迁移；旧 `pms_project` 冻结；
- 存量勘察结论：
  - 运行库无 `proj_project`（V52 仅建模板 7 表）；目标 DDL（appendices 物理模式）定义了 `proj_project`/`proj_project_member_assignment`/`proj_project_company_department_relation` 完整契约；
  - 旧链 `ProjectController`（`/pms/project`，7 端点）+ `project/index.vue`（项目总览 18011）为 PM-01 语义前实现（单字段 `projectType` 混载、无模板/实例化）；
  - 旧项目 API 被约 30 处旧页面以 `getProjectPage/getProject` 只读选择器消费（工程实施/验收等模块），全量退役会跨域扩散；
  - 实例表（阶段/任务/里程碑/交付件/门禁）不在核心 60 表 DDL 内，按 `FEATURE_FORWARD_MIGRATION` 先例由本 Feature 前向新建。

## 2. 目标数据模型（V57）

命名遵循 ADR-0019（`proj_` 前缀、uk 含 `tenant_id`、字符串状态码、乐观锁 `version`）。

### 2.1 表清单（11 张）

| 表 | 职责 | 关键约束 |
|---|---|---|
| `proj_project` | 项目主档+树邻接真值（按目标 DDL 落地，另见 2.2 前向扩列） | `uk(tenant_id, project_code)`；`uk(tenant_id, code_root_id, project_sequence)`；`chk_project_code_namespace`；自引用 FK（parent/code_root） |
| `proj_project_stage` | 阶段实例：阶段码、顺序、状态、冻结模板/流程引用 | `uk(tenant_id, project_id, stage_code)` |
| `proj_project_task` | 任务实例：任务码、父任务码、所属阶段、状态（初始待分配）、满意度时点快照 | `uk(tenant_id, project_id, task_code)`；`idx(project_id, parent_task_code)` |
| `proj_project_milestone` | 里程碑实例：里程碑码、所属阶段、时点、达成标准 | `uk(tenant_id, project_id, milestone_code)` |
| `proj_project_deliverable` | 交付件实例：交付件码、所属阶段/任务、必需标志、状态 | `uk(tenant_id, project_id, deliverable_code)` |
| `proj_project_gate` | 门禁实例：门禁码、类型(ENTRY/EXIT)、所属阶段、状态、冻结校验内容摘要 | `uk(tenant_id, project_id, gate_code)` |
| `proj_project_gate_reference` | 门禁实例引用行：(gate_id, ref_type(TASK/DELIVERABLE/STATUS), ref_code) 结构化三元组 | `uk(tenant_id, gate_id, ref_type, ref_code)` |
| `proj_project_member_assignment` | 成员角色区间（按目标 DDL 原样）：member_role、effective_from/to、快照字段 | `uk(tenant_id, project_id, user_id, member_role, effective_from)`；区间重叠应用层防重 |
| `proj_project_company_department_relation` | 项目组织关系（按目标 DDL 原样）：V1 承载下单办事处（relation_role=`ORDER_OFFICE`，is_primary=1） | `uk(tenant_id, project_id, company_code, department_code, relation_role, effective_from)` |
| `proj_project_code_sequence` | 平台编码序列：`code_namespace`（V1=`PLATFORM_ROOT`）、`next_value`；`SELECT ... FOR UPDATE` 原子分配 | `uk(tenant_id, code_namespace)` |
| `proj_idempotency_record` | API 命令幂等记录：command/actor/idempotency_key/请求摘要/响应摘要，重放返回原资源 | `uk(tenant_id, command, actor_id, idempotency_key)` |

### 2.2 `proj_project` 前向扩列（目标 DDL 之外，SDS 09 §12.1 前向扩列依据）

| 列 | 说明 |
|---|---|
| `signing_method` | 签约方式（字典 `pms_signing_method`，V52 已注册）；SDS 08 要求四维分别保存，目标 DDL 缺列，前向补齐 |
| `contract_no` | 手工登记合同号文本（正式商业关系随 INT-02 Commerce 对象接管；迁移问题池登记） |
| `implementation_location` | 实施地点文本（多地点拆分属 PM-02） |
| `creation_reason` | 手工创建原因（BR-2 必填） |
| `lifecycle_template_revision_no` | 冻结模板 revision 号（`lifecycle_template_id` 目标 DDL 已有） |
| `template_load_method` | 模板加载方式：`AUTO_DEFAULT`（唯一默认命中）/`MANUAL_SELECTED`（人工选择） |
| `process_definition_id` / `process_definition_version` | 冻结流程定义引用（取自绑定 revision，创建时快照） |

### 2.3 编码规则（ADR-0020，`code_rule_version='V1'` 冻结）

双重唯一键（ADR-0020 §4，数据库最终防重）：

- **项目编码**：`uk(tenant_id, project_code)` —— 租户内唯一；创建后默认不可变，软删除/关闭/归档均不释放；
- **编码序号**：`uk(tenant_id, code_root_id, project_sequence)` —— 序号在编码命名空间根项目内唯一；根项目 `project_sequence=0`（`code_root_id=id` 自建命名空间），子项目序号 `>0` 且不回收复用。

两套序号语义区分：

1. **平台编码序号**（本 Feature）：手工根项目编码 `PJT` + 年份(4) + 流水(6，零填充)，如 `PJT202600001`；流水自 `proj_project_code_sequence`（`code_namespace='PLATFORM_ROOT'`，租户级）行锁原子递增——用于保证编码租户内唯一，不是 `project_sequence`；
2. **命名空间序号**（PM-02 预留）：子项目编码 `<根项目编码>-SP<流水>`，流水按 `tenant_id + code_root_id` 在命名空间根内原子分配（同一序列表，`code_namespace='ROOT:<code_root_id>'` 行，本 Feature 不启用）；根项目固定 `project_sequence=0`。

分配冲突处理：数据库唯一键兜底，事务失败自动重试分配一次。

### 2.4 字典与菜单（V57 内登记）

- 字典：`pms_project_member_role`（PROJECT_MANAGER/SERVICE_MANAGER_L1/SERVICE_MANAGER_L2，仅 PRD 已定义角色）、`pms_company_relation_role`（PRIMARY/SALES/MARKET/SYSTEM/EXPANSION/IMPLEMENTATION/ORDER_OFFICE，前六个对齐目标 DDL 注释，ORDER_OFFICE 服务 PM-01 一级服务经理规则）、`pms_project_lifecycle_stage`（S0待开始…S6闭环/MAINT维护，对齐 SDS 05）、`pms_template_load_method`、实例状态字典（stage/task/milestone/deliverable/gate，初始值+可扩展）；
- 菜单（18067 段已核对空闲，V52 占用至 18066）：18067 项目列表（页面，挂 19261 项目管理组，sort=0 置顶）+ 按钮 18068 项目创建 `pms:project:create`、18069 项目更新 `pms:project:update`、18070 服务经理指派 `pms:project:assign`；查询权限沿用页面级 `pms:project:query`。
- 权限码复用旧语义码（`pms:project:query/create/update/assign`）：旧链端点退役后码由新链承接，与 F-PM03 复用 `pms:project-template:*` 先例一致。

## 3. API 契约（SDS 10 §5，admin 装配 `/pms` 前缀，复数新路由）

| 方法与路径 | 语义 | 权限 |
|---|---|---|
| `POST /pms/projects` | 手工创建（Header `Idempotency-Key`；body：名称/客户/合同号/办事处/实施地点/三维/创建原因/可选 templateId/可选服务经理）| `pms:project:create` |
| `GET /pms/projects/actions/match-templates` | 按三维+空级别返回命中生效模板列表（含 revision 概要，供表单选择） | `pms:project:create` |
| `GET /pms/projects/page` | 分页（名称/编码/状态/三维过滤） | `pms:project:query` |
| `GET /pms/projects/{id}` | 详情（基本信息+四维+模板绑定） | `pms:project:query` |
| `PUT /pms/projects/{id}` | 可编辑属性（名称/客户/合同号/实施地点等；状态、父节点、来源、模板绑定不可改） | `pms:project:update` |
| `GET /pms/projects/{id}/instances` | 实例视图（阶段→任务/里程碑/交付件/门禁+门禁引用行，按冻结版本只读） | `pms:project:query` |
| `GET /pms/projects/{id}/members` | 成员区间列表（当前有效+历史） | `pms:project:query` |
| `POST /pms/projects/{id}/actions/assign-manager` | 指派服务经理（memberRole/userId/effectiveFrom；旧区间关闭+新区间开启，留痕前后值） | `pms:project:assign` |

- 幂等：`Idempotency-Key` 作用域 tenant+command+actor；同键同摘要重放返回原资源（含实例化结果摘要）；同键异摘要 409 `PMS-COMMON-IDEMPOTENCY-0001`；幂等记录以 `proj_idempotency_record` 通用表承载（V57 一并新建，uk(tenant_id, command, actor_id, idempotency_key)，存响应摘要）。
- 错误码：新增 `PROJECT_TEMPLATE_NO_MATCH`（422，附冲突/无匹配清单）、`PROJECT_TEMPLATE_AMBIGUOUS`（422，附同优先级候选）、`PROJECT_CODE_EXHAUSTED`、`PROJECT_NOT_EXISTS`、`PROJECT_MEMBER_ROLE_INVALID` 等；沿用 16 分册错误分类。

## 4. 存量冻结（V58 + 代码收敛）

### 4.1 V58 迁移（只动菜单，不动业务表）

- 逻辑删除按钮 18050（项目创建）/18051（项目更新）（V49 登记，旁路入口阻断 BR-1）；
- 旧"项目总览"18011 隐藏（visible=0）：旧列表页不再作为入口；`pms_project` 数据冻结只读，待 AI-MIG-000；
- 18021（项目指派按钮，若仍挂接旧链 assign）一并逻辑删除。

### 4.2 后端收敛（不删除，冻结写面）

- `ProjectController` 退役写端点：`create/update/delete/classify/assign-manager`（PM-01/07/08 旧语义实现，防旁路）；保留 `get/page` 只读端点供约 30 处旧页面选择器过渡；
- `ProjectService` 对应写方法与 `ProjectSaveReqVO` 等写 VO 移除；`ProjectDO/ProjectMapper` 保留只读；
- 错误码清理：旧链独有写错误码。

### 4.3 前端收敛

- `views/pms/project/project/index.vue`：移除创建/编辑/指派/分类对话框与入口按钮（页面已隐藏，代码收敛防直链访问）；保留列表渲染；
- `project-detail/index.vue` 移除 `updateProject` 调用（编辑态剥离，详情只读）；
- 其余旧页面不动（选择器只读继续可用）。

### 4.4 守卫扩展（`validate_implementation_baseline_inventory.py`）

新增 `RETIRED_PROJECT_WRITE_PATTERNS` + 组合规则（T1 已落地，当前红、T5 转绿）：

| 类别 | 模式 | 说明 |
|---|---|---|
| route | `\bpms/project/(?:create|update|delete|classify|assign-manager)\b`（单数边界词，防误伤复数新路由 `/pms/projects`） | 完整字面写路由消失 |
| route composition | 同文件同时命中 `["'`]/pms/project["'`]`（单数基路由）与 `/(?:create|update|delete|classify|assign-manager)["'`]`（写路径片段） | 捕获 Java 注解/前端 baseUrl 拼接式写路由（字面完整路径不出现于源码的情形） |
| permission | `\bpms:project:(?:create|update|delete|assign)\b` 仅允许出现在新链白名单前缀：`controller/admin/projects/`、`api/pms/project/projects/`、`views/pms/project/projects/` | 权限码由新链承接；`delete` 永不白名单（新链无删除端点） |

T1 守卫红面（T5 收敛清单，共 4 处）：旧 `ProjectController.java`（路由组合+权限码）、旧 `api/pms/project/project/index.ts`（路由组合）、旧 `views/pms/project/project/index.vue`（权限码）。

## 5. 新前端

`views/pms/project/projects/`：

- 列表页：四维/状态/名称过滤，行进入详情；"创建项目"入口（v-hasPermi 18068）；
- 创建向导（对话框分步）：①基本信息+三维+办事处/实施地点/合同号/创建原因 → ②命中模板列表（实时 match-templates）+模板预览抽屉（阶段/任务/里程碑/交付件/门禁清单，复用 F-PM03 预览组件）→ ③确认（未选模板时提示唯一默认命中或冲突阻断）+可选服务经理指派 → 提交；
- 详情页：基本信息/四维/模板绑定（编码+版本+加载方式）/实例五要素视图（阶段分组）/成员区间视图 + 指派动作（18070）。

API 模块 `api/pms/project/projects`（新，复数）。

## 6. 任务分解（TDD：失败测试→最小实现→重构→验证）

| # | 任务 | 产出 | 验证 |
|---|---|---|---|
| T1 | 守卫先行：RETIRED_PROJECT_WRITE_PATTERNS 用例（红） | 测试更新 | unittest 红 |
| T2 | V57 DDL（11 表+字典+菜单）/ V58 冻结迁移 | 2 个迁移文件 | Flyway 本地库执行、迁移测试 |
| T3 | 领域层：DO×11/Mapper×11/`ProjectRules`（编码、四维、阻断）/`TemplateInstantiator`（冻结+实例化）/成员区间服务 | domain+service 包 | 单测：BR-2/3/4/5/8 |
| T4 | Controller + VO + 幂等记录 + 错误码 + 权限注解 | controller 包 | API 契约/幂等/权限拒绝测试 |
| T5 | 存量冻结：4.2/4.3 收敛 | 代码修改 | 编译+守卫绿+残留 grep |
| T6 | 新前端（列表/向导/详情/指派） | vue/ts | 类型检查+构建 |
| T7 | 集成验证：`mvn -pl pms-module-project -am test` + 全脚本套件 + 4 校验器 | 全绿 | CI 本地等价 |
| T8 | 真实浏览器 UI 验收（Trae 内置浏览器，逐菜单走查+截图；覆盖三维组合、唯一默认命中、多匹配阻断、无匹配阻断、幂等重放、指派） | 验收记录 | Spec 第5节逐条 |
| T9 | 清单/追溯回写：inventory 登记 `ProjectManualCreation`（CURRENT_57）；需求矩阵 PM-01 Feature 列 → `F-PM01-IMPLEMENTED`；示例数据核验（T8 产生的组合覆盖项目即示例数据，不足组合以种子补） | 清单+矩阵 | 校验器 PASS |

每任务完成形成一次聚焦提交。

## 7. 风险与对策

| 风险 | 对策 |
|---|---|
| 新旧路由仅单复数之差，守卫误报/漏报 | 守卫用单数边界词精确匹配旧路由；T1 先行 |
| 旧页面选择器在旧链写端点退役后行为变化 | 仅退役写端点；get/page 保留，选择器只读不受影响 |
| 编码并发分配冲突 | 行锁序列表+uk 兜底，事务失败自动重试一次 |
| 实例化与主档非原子（部分成功） | 单事务创建+实例化+指派；失败整体回滚，不留半实例化项目 |
| 模板匹配语义与 F-PM03 漂移 | 复用 `TemplateMatcher` 同一实现，仅加列表语义包装，不改匹配规则 |
| 菜单 ID 冲突 | 落笔前 `git grep '180[6-7]\d'` 已核对：18067+ 空闲 |

## 8. 完成定义（DoD）

1. Spec 第5节验收标准全部通过（含真实浏览器走查与组合覆盖）；
2. T1～T7 自动化验证全绿；守卫无残留命中；
3. V57/V58 本地 MySQL 8.4 可重复执行；
4. 清单与需求矩阵回写完成，`F-PM01` → `IMPLEMENTED`；
5. 工程链 DoD 第8条（初始化数据）：字典/菜单种子落 V57；示例项目数据覆盖关键组合（精确命中/唯一默认/多匹配人工选择/无匹配阻断/一级+二级指派），由 T8 验收数据构成，缺项以幂等种子迁移补充（creator='seed'、高段 ID）。
