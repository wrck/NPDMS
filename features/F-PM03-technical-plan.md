# F-PM03 Technical Plan

> Feature ID：`F-PM03`（对应 Spec：`features/F-PM03-project-template-foundation.md`）
> 文档状态：`PLAN_READY`
> 规格基线：快照 @ `f604ef2`（SDS Baseline `b71b5e3` + PM-03 Feature 登记）
> 本计划不重新定义领域、权限或状态语义；业务规则以 F-PM03 Spec 第4节 BR-1～BR-8 为准。

## 1. 输入与边界回顾

- 需求：PM-03（PRD V1.7 4.2.3），供给端（模板定义/发布/匹配），消费端（项目创建实例化）属 F-PM01；
- 迁移契约：ProjectTemplate=`NEW_ONLY`、gate=`FEATURE_RELEASE` → 前向迁移新建目标表，无历史数据迁移；
- 存量：旧模板双面（project-template + phase-template）无已证明来源，整体退役冻结，参照 V50/V51 先例；
- 关键耦合勘察结论：
  - `ProjectPhaseServiceImpl` 是旧阶段模板唯一后端消费方（create 校验 + `instantiate-from-template`），随退役一并移除其模板耦合后，旧 DO/Mapper 可全删；
  - 前端消费点2处：`project/index.vue`（从模板建项目对话框）、`project-phase/index.vue`（阶段模板选择器），随退役剥离模板入口，旧页面保留手动操作路径。

## 2. 目标数据模型（V52）

命名遵循 ADR-0019（`<domain_code>_<full_domain_object_name>`，完整英文词，uk 含 `tenant_id`，字符串状态码）。

### 2.1 表清单（7张）

| 表 | 职责 | 关键约束 |
|---|---|---|
| `proj_project_template` | 模板身份：编码、名称、状态(DRAFT/ACTIVE/RETIRED)、匹配优先级、描述 | `uk(tenant_id, code)`；系统保留编码标志 |
| `proj_project_template_revision` | 版本头：revision_no、状态(DRAFT/PUBLISHED)、四维匹配条件（4个字典引用列）、流程定义ID+版本引用、发布人/时间、校验结果摘要 | `uk(tenant_id, template_id, revision_no)`；PUBLISHED 行应用层只读 |
| `proj_project_template_stage_definition` | 阶段定义：阶段码(S0..S6)、名称、顺序、准入/准出条件文本 | `uk(template_revision_id, stage_code)` |
| `proj_project_template_task_definition` | 任务定义：任务码、名称、父任务码、所属阶段、优先级、排序、预估工时、满意度适用时点 | `uk(template_revision_id, task_code)` |
| `proj_project_template_milestone_definition` | 里程碑定义：里程碑码、名称、所属阶段、时点、达成标准 | `uk(template_revision_id, milestone_code)` |
| `proj_project_template_deliverable_definition` | 交付件定义：交付件码、名称、所属阶段/任务、必需标志 | `uk(template_revision_id, deliverable_code)` |
| `proj_project_template_gate_definition` | 门禁定义：门禁码、类型(ENTRY/EXIT)、所属阶段、引用任务/交付件/状态码（结构化引用行）、流程引用 | `uk(template_revision_id, gate_code)` |

### 2.2 设计要点

1. 草稿即版本：每个模板至多一个 DRAFT revision（模板创建时生成，可编辑）；发布冻结为 PUBLISHED 并递增 revision_no，此后只读（BR-3）；
2. 门禁对任务/交付件/状态的引用存结构化引用行（gate_definition 子行或引用列组），发布校验据此逐项存在性检查（BR-2），不使用 JSON 承载（09 原则7）；
3. 四维匹配条件独立4列，分别引用字典：签约方式/项目类别/实施方式/重大项目级别（BR-1），列为 NULL 表示该维不限；
4. 匹配查询基于 ACTIVE 模板的最新 PUBLISHED revision 条件 + 模板优先级（BR-4）；
5. V52 同时登记四维字典类型与数据（`pms_signing_method`、`pms_project_category`、`pms_implementation_method`、`pms_major_project_level`，取值以 PRD A.2 字典附录为准）与新菜单（父 18000 下，申请 18060+ 段，落笔前核对占用）。

## 3. API 契约（遵循 SDS 10-api：`/project-templates` CRUD + `actions/*`）

| 方法与路径（`/pms` 前缀，平台 admin 装配） | 语义 | 权限 |
|---|---|---|
| `GET /pms/project-templates/page` | 分页（状态/编码/名称过滤） | `pms:project-template:query` |
| `POST /pms/project-templates` | 创建模板（同时生成 DRAFT revision） | `pms:project-template:create` |
| `PUT /pms/project-templates/{id}` | 编辑模板身份与草稿内容（仅 DRAFT） | `pms:project-template:update` |
| `DELETE /pms/project-templates/{id}` | 删除（仅无 PUBLISHED 版本且非系统保留） | `pms:project-template:delete` |
| `GET /pms/project-templates/{id}` | 详情（含当前草稿/最新发布版本） | `pms:project-template:query` |
| `POST /pms/project-templates/{id}/actions/publish` | 发布校验+冻结版本+转 ACTIVE | `pms:project-template:publish` |
| `POST /pms/project-templates/{id}/actions/disable` | 停用（RETIRED，只阻新项目） | `pms:project-template:disable` |
| `GET /pms/project-templates/{id}/revisions/{revisionNo}` | 已发布版本只读查询 | `pms:project-template:query` |
| `POST /pms/project-templates/actions/match-preview` | 四维匹配预演：唯一命中或冲突清单 | `pms:project-template:query` |

注意：新路由用复数 `project-templates`（SDS 契约），与被退役的旧单数路由 `/pms/project-template` 在守卫规则中可区分。

## 4. 存量退役（V53 + 代码删除）

### 4.1 V53 迁移（只动菜单，不动业务表）

逻辑撤销（V50/V51 语义）：菜单 ID `18015`（阶段模板页）、`18034`（阶段模板管理按钮）、`18042`（项目模板管理页）、`18043`（项目模板维护按钮）。旧表 `pms_project_template`、`pms_project_phase_template` 及 `pms_project.template_id` 列冻结保留，待 AI-MIG-000 证据判定。

### 4.2 后端删除清单（17 文件 + 1 端点 + 错误码）

- `controller/admin/projecttemplate/**`：Controller + 4 VO（含 `ProjectCreateFromTemplateReqVO`）＝5 文件；
- `service/projecttemplate/**` + `dal/dataobject/projecttemplate/**`（含 `TemplateSnapshot`）+ `dal/mysql/projecttemplate/**` ＝5 文件；
- `controller/admin/phasetemplate/**`：Controller + 3 VO ＝4 文件；
- `service/phasetemplate/**` ＝2 文件、`dal/dataobject/phasetemplate/ProjectPhaseTemplateDO` ＝1 文件、`dal/mysql/phasetemplate/ProjectPhaseTemplateMapper` ＝1 文件；
- `ProjectPhaseController` 移除 `POST /instantiate-from-template` 端点；`ProjectPhaseServiceImpl` 移除模板存在性校验与 `instantiateFromTemplate`（含 `ProjectPhaseTemplateMapper` 注入）；
- 错误码清理：`PROJECT_PHASE_TEMPLATE_NOT_EXISTS` 及 projecttemplate 相关错误码。

### 4.3 前端删除/剥离清单

- 删除：`api/pms/project/project-template`、`api/pms/project/project-phase-template`、`views/pms/project/project-template`、`views/pms/project/project-phase-template`（4 路径）；
- 剥离：`views/pms/project/project/index.vue` 移除从模板建项目对话框与 `TemplateApi` 引用（保留手动创建）；`views/pms/project/project-phase/index.vue` 移除阶段模板选择器与 `PhaseTemplateApi` 引用（保留手动建阶段）。

### 4.4 门禁守卫扩展（`validate_implementation_baseline_inventory.py`）

新增 `RETIRED_TEMPLATE_PATTERNS`（扫描根已含 project 模块与前端）：

| 类别 | 模式 | 说明 |
|---|---|---|
| type | `\bProjectPhaseTemplate\w*\b` | 旧阶段模板概念整体消失 |
| type | `\bProjectCreateFromTemplate\w*\b` | 旧消费端 VO |
| route | `\bpms/project-template\b`（单数，边界词防误伤复数新路由） | 旧路由 |
| route | `\bphase-template\b` / `\binstantiate-from-template\b` | 旧阶段模板路由与实例化旁路 |
| permission | `\bpms:phase-template:[\w:*-]+` | 旧权限（`pms:project-template:*` 由新模型沿用，不做守卫） |
| table | `\bpms_project_template\b` / `\bpms_project_phase_template\b`（仅运行时源码根） | 运行时不得再引用旧表 |

## 5. 新前端（模板管理后台）

`views/pms/project/project-templates/`：列表页（状态/优先级/四维列）+ 详情抽屉（草稿编辑：阶段-任务-里程碑-交付件-门禁结构化行编辑、四维条件、流程引用）+ 发布/停用动作 + 匹配预演对话框（冲突清单展示）。API 模块 `api/pms/project/project-templates`。

## 6. 任务分解（TDD：失败测试→最小实现→重构→验证）

| # | 任务 | 产出 | 验证 |
|---|---|---|---|
| T1 | 守卫先行：测试新增 RETIRED_TEMPLATE_PATTERNS 用例（红） | 测试更新 | unittest 红 |
| T2 | V52 DDL + V53 退役迁移 | 2 个迁移文件 | Flyway/本地库执行、迁移测试 |
| T3 | 领域层：DO×7/Mapper×7/Service（发布校验、版本冻结、四维匹配、停用、系统保留编码） | service 包 | 单测：BR-1/2/3/4/5 |
| T4 | Controller + VO + 错误码 + 权限注解 | controller 包 | API 契约/权限拒绝测试 |
| T5 | 存量退役：4.2/4.3 清单删除与剥离 | 代码删除 | 编译+守卫绿+残留 grep |
| T6 | 新前端页面与 API 模块 | vue/ts | 类型检查+构建 |
| T7 | 集成验证：`mvn -pl pms-module-project -am test` + 全脚本套件 + 4 校验器 | 全绿 | CI 本地等价 |
| T8 | 真实浏览器 UI 验收（Trae 内置浏览器，逐菜单走查+截图） | 验收记录 | Spec 第5节验收标准逐条 |
| T9 | 清单/追溯回写：inventory 登记 Template 新旧条目、需求矩阵 Feature 列 → `IMPLEMENTED`（规格仓库→同步） | 清单+矩阵 | 校验器 PASS |

每任务完成形成一次聚焦提交（工程链第12节）。

## 7. 风险与对策

| 风险 | 对策 |
|---|---|
| 新旧路由/权限命名接近导致守卫误报/漏报 | 守卫用单数边界词与旧专属概念（phase-template、CreateFromTemplate），T1 先行验证 |
| 旧 WBS 剥离模板耦合后行为变化（建阶段无模板校验） | 保留手动路径；变更仅限模板入口，属退役预期语义 |
| 门禁引用行结构过深导致实现膨胀 | 引用行仅存（gate_id, ref_type, ref_code）三元组，不做多级嵌套 |
| 菜单 ID 冲突 | 落笔前 `git grep '180\d\d' sql/migrations` 核对，V52 使用核对后空闲段 |

## 8. 完成定义（DoD）

1. Spec 第5节验收标准全部通过（含真实浏览器走查）；
2. T1～T7 自动化验证全绿；守卫无残留命中；
3. V52/V53 在本地 MySQL 8.4 可重复执行；
4. 清单与需求矩阵回写完成，`F-PM03` → `IMPLEMENTED`。
