# 当前有效及在用基础配置专项比对

日期：2026-10-25（Asia/Shanghai）。本轮按用户收窄后的口径重新读取两库，只读核查。项目模板仅纳入 `TPL-CFG20261014-*` 五个 ACTIVE 正式模板的最新 PUBLISHED 修订；旧项目模板、旧模板定义明细、未发布草稿不作为需补齐模板。

**结论：五套正式项目模板已齐且比较内容一致；当前启用/生效基础配置尚未全部在测试环境存在，不能出具“全部齐备”结论。**

## 口径与可复查范围

- 源库 `127.0.0.1:24306/npdms_domain_test`；测试库 `172.17.90.178:13306/npdms`。租户 1、未删除记录；两端各自只读一致性快照。
- 动态表单按 availability_code=ENABLED，问卷按 PUBLISHED 和生效区间，业务视图按已发布且未停用，采集按 PUBLISHED，割接按 PUBLISHED 和生效区间。巡检额外保留已有业务引用的停用修订，单独解释。名称含 QA/EXAMPLE 的记录只要仍启用/发布，就仍计入差异，不因名称自动排除。
- 扫描数据库中 PMS 业务表的表单、问卷、视图、打印、规则和配置修订引用列，引用来源及去重计数保存在 active-comparison.json 的 usage。该扫描包含历史引用，不能把所有引用都称为未完成业务仍在运行；禁用旧表单及旧模板仅作历史保护，不混入启用配置缺口。JSON 内引用另由五套正式模板的规范化 Designer 比较与业务视图完整性检查覆盖，未声称已穷举所有任意 JSON 引用。
- ID、审计时间和发布人不作为业务内容差异；动态表单允许迁移后修订编号不同，以表单内容比对。项目模板比较匹配维度、优先级及规范化 Designer；不是一次全业务流程执行验收。
- 文档模板额外比较父模板编码、当前版本章节及覆盖/排除内容。生效时间属于实际配置，割接 effective_start_time 的差异保留，不掩盖。

## 分类对比

| 类别 | 源端纳入 | 测试端候选 | 源端缺项 | 已存在但内容不同 |
|---|---:|---:|---:|---:|
| 启用动态表单（含打印） | 14 | 11 | 3 | 0 |
| 已发布未停用/在用业务视图 | 29 | 17 | 12 | 0 |
| 发布采集模板 | 3 | 0 | 3 | 0 |
| 发布/在用巡检规则修订 | 3 | 0 | 3 | 0 |
| 生效问卷修订 | 3 | 1 | 2 | 0 |
| 当前生效割接配置 | 1 | 1 | 0 | 1 |
| 发布普通表单 | 8 | 8 | 0 | 0 |
| 发布文档模板 | 6 | 6 | 0 | 0 |
| 交付类型 | 21 | 21 | 0 | 0 |
| 发布平台过程定义 | 0 | 0 | 0 | 0 |
| 启用 PMS 字典类型 | 94 | 117 | 1 | 0 |
| 启用 PMS 字典项 | 553 | 481 | 72 | 0 |
| 最新五个正式业务类型项目模板 | 5 | 5 | 0 | 0 |

平台过程定义本轮两端 PUBLISHED 候选均为 0，不据此声明该功能或 BPM 审批配置全部通过。普通表单、文档模板按 status=1；状态机最新发布修订虽然源 v1/测试 v2，8 条迁移语义一致。

## 必须明确的缺项

### 启用动态表单（含打印）

- `CONFIRM_QA_1789910001`，源 ID `2101660990524624897`：测试环境无对应有效配置。
- `SOL_PRE04_DUPLICATE_CORE_EXAMPLE`，源 ID `992203010003`：测试环境无对应有效配置。
- `SOL_PRE04_MISSING_CORE_EXAMPLE`，源 ID `992203010002`：测试环境无对应有效配置。

### 生效问卷修订

- `SAT_MANUAL_EXAMPLE|1`，源 ID `992209210002`：测试环境无对应有效配置。
- `SAT_VISUAL_ACCEPTANCE_0921|3`，源 ID `2101717346254004226`：测试环境无对应有效配置。

### 发布采集模板

- `DEVICE-SHOW-5f6af2`，源 ID `2101908917691748353`：测试环境无对应有效配置。
- `QA-SUPERVISION64-SYSTEM`，源 ID `2102027086221778948`：测试环境无对应有效配置。
- `QA-SUPERVISION64-DIAGNOSTIC`，源 ID `2102027086221778949`：测试环境无对应有效配置。

### 发布/在用巡检规则修订

- `【模拟验收】离线合成延迟检测|1`，源 ID `2110600966717652993`：测试环境无对应有效配置。
- `【模拟验收】离线合成延迟检测|2`，源 ID `2110607611698970626`：测试环境无对应有效配置。
- `【模拟验收】仅类型A适配|1`，源 ID `2110657332224290817`：测试环境无对应有效配置。

### 已发布未停用/在用业务视图

- `siteSurvey|IT_DEFAULT_20261012_DIRECT_BUSINESS_SOL_SITE_SURVEY|1`，源 ID `2102312818482794498`：测试环境无对应有效配置。
- `requirementAnalysisRevision|IT_DEFAULT_20261012_DIRECT_BUSINESS_SOL_REQUIREMENT_ANALYSIS_REVISION|1`，源 ID `2102312818482794499`：测试环境无对应有效配置。
- `constructionPlan|IT_DEFAULT_20261012_DIRECT_BUSINESS_SOL_CONSTRUCTION_PLAN|1`，源 ID `2102312818482794500`：测试环境无对应有效配置。
- `projectContact|IT_DEFAULT_20261012_DIRECT_BUSINESS_CUS_PROJECT_CONTACT|1`，源 ID `2102312818482794501`：测试环境无对应有效配置。
- `stagePlanBatch|IT_DEFAULT_20261012_DIRECT_BUSINESS_SOL_STAGE_PLAN_BATCH|1`，源 ID `2102312818482794502`：测试环境无对应有效配置。
- `solution|IT_DEFAULT_20261012_DIRECT_BUSINESS_SOL_SOLUTION|1`，源 ID `2102312818482794503`：测试环境无对应有效配置。
- `arrival|IT_DEFAULT_20261012_DIRECT_BUSINESS_IMP_ARRIVAL|1`，源 ID `2102312818482794504`：测试环境无对应有效配置。
- `installation|IT_DEFAULT_20261012_DIRECT_BUSINESS_IMP_INSTALLATION|1`，源 ID `2102312818482794505`：测试环境无对应有效配置。
- `configuration|IT_DEFAULT_20261012_DIRECT_BUSINESS_IMP_CONFIGURATION|1`，源 ID `2102312818482794506`：测试环境无对应有效配置。
- `jointTest|IT_DEFAULT_20261012_DIRECT_BUSINESS_IMP_JOINT_TEST|1`，源 ID `2102312818482794507`：测试环境无对应有效配置。
- `acceptanceActivity|IT_DEFAULT_20261012_DIRECT_BUSINESS_ACC_ACCEPTANCE_ACTIVITY|1`，源 ID `2102312818482794508`：测试环境无对应有效配置。
- `training|IT_DEFAULT_20261012_DIRECT_BUSINESS_IMP_TRAINING|1`，源 ID `2102312818482794509`：测试环境无对应有效配置。

巡检上述 3 个修订中，源库实际 2 条 PUBLISHED、1 条 DISABLED 且仍被业务记录引用；不能称为 3 条发布规则。停用引用只代表历史解释需要，不能擅自重新启用。

## 已存在的配置及时间差异

- 五个正式业务类型项目模板：5/5 的匹配优先级、签约方式、项目类别、实施方式、重大项目维度和 Designer 内容相同。测试五套最新版本都有 execution_snapshot，业务视图冻结绑定检查未发现缺项。
- 源库 14 套启用动态表单中，测试存在 11 套；这 11 套当前内容和 availability 一致。此前“共有 12 套”包括 1 套禁用的 PROJECT_PREPARATION_CONFIRMATION，本专项启用口径不将其计入。
- 默认 COMPLETION_SATISFACTION_STANDARD 问卷内容一致；缺少的另外两套虽为示例/验收名称，但源库当前确实 PUBLISHED 且已生效，因此仍列缺项。
- 8 套普通表单、6 套文档模板、21 种交付类型对应内容一致。
- CUTOVER_STANDARD_V1 v2 两端均生效；唯一主配置字段差异是 effective_start_time：源 2026-10-15 14:00:44，测试 2026-10-24 13:49:53。其余主配置业务字段及内嵌配置快照一致；时间差异不会阻止当前时点生效。
- 当前割接关联行数两端相同：字典值 27、维度 5、计划节次 6、绑定规则 133、清单项定义 133。计数相同不单独构成所有子字段等价证明；本轮未执行割接匹配组合验收。

## 基础字典差异

启用 PMS 字典类型缺 `pms_requirement_analysis_extension_demo`；启用 PMS 字典项缺 72 个。来源含历史模块枚举、治理动作及示例，完整编码如下；不能据此批量导入旧模块或自动改变生效业务规则。

- `pms_project_status|0`
- `pms_project_status|1`
- `pms_project_status|2`
- `pms_project_status|3`
- `pms_task_status|0`
- `pms_task_status|1`
- `pms_task_status|2`
- `pms_task_status|3`
- `pms_task_status|4`
- `pms_task_status|5`
- `pms_task_status|6`
- `pms_plan_change_status|0`
- `pms_plan_change_status|1`
- `pms_plan_change_status|2`
- `pms_plan_change_status|3`
- `pms_cutover_risk_status|0`
- `pms_cutover_risk_status|1`
- `pms_cutover_risk_status|2`
- `pms_cutover_risk_status|3`
- `pms_cutover_exec_status|0`
- `pms_cutover_exec_status|1`
- `pms_cutover_exec_status|2`
- `pms_cutover_exec_status|3`
- `pms_cutover_exec_status|4`
- `pms_cutover_observation_status|0`
- `pms_cutover_observation_status|1`
- `pms_cutover_observation_status|2`
- `pms_leftover_status|0`
- `pms_leftover_status|1`
- `pms_leftover_status|2`
- `pms_inspection_mode|ONLINE`
- `pms_inspection_mode|OFFLINE`
- `pms_srv_task_status|0`
- `pms_srv_task_status|1`
- `pms_srv_task_status|2`
- `pms_srv_task_status|3`
- `pms_srv_task_status|4`
- `pms_srv_rule_type|ONLINE`
- `pms_srv_rule_type|OFFLINE`
- `pms_srv_rule_status|0`
- `pms_srv_rule_status|1`
- `pms_srv_report_type|STANDARD`
- `pms_srv_report_type|DETAIL`
- `pms_srv_report_type|EXCEPTION`
- `pms_srv_report_type|SUMMARY`
- `pms_srv_report_status|0`
- `pms_srv_report_status|1`
- `pms_srv_report_status|2`
- `pms_srv_issue_severity|1`
- `pms_srv_issue_severity|2`
- `pms_srv_issue_severity|3`
- `pms_srv_issue_status|0`
- `pms_srv_issue_status|1`
- `pms_srv_issue_status|2`
- `pms_srv_issue_status|3`
- `pms_maint_transition_status|0`
- `pms_maint_transition_status|1`
- `pms_maint_transition_status|2`
- `pms_maint_transition_status|3`
- `pms_maint_transition_status|4`
- `pms_srv_level_status|0`
- `pms_srv_level_status|1`
- `pms_srv_level_status|2`
- `pms_srv_level_status|3`
- `pms_project_phase_status|0`
- `pms_project_phase_status|1`
- `pms_project_phase_status|2`
- `pms_project_phase_status|3`
- `pms_governance_action_type|ROLLBACK`
- `pms_governance_action_type|DIRECT_CLOSE`
- `pms_requirement_analysis_extension_demo|STANDARD`
- `pms_requirement_analysis_extension_demo|ENHANCED`

## 系统参数与流程依赖

- 系统参数按 config_key 与值摘要比对：源 18、测试 12；共有 12 项值一致。缺少 system.user.init-password、test4 及 url.druid/url.skywalking/url.spring-boot-admin/url.swagger。未读取或输出明文参数值；这些缺项不自动归为项目模板故障，尤其不擅自复制初始密码参数。
- 五个正式模板 Designer 直接引用的流程 key 为 PMS_MINIMAL_NORMAL_CLOSURE，两端均有未挂起的部署定义。测试另有割接、方案审核、工期变更、阶段计划等 6 个未挂起 key。源端另外 IT_DEFAULT_BUSINESS_20261012 和 xx 不属于这五套模板直接引用，未列为必补项目模板依赖。这里只确认部署 key 存在；BPMN 资源等价与审批执行未验。

## 当前在用与历史保护

两库活动计划及不可变历史仍保持原状。测试 84572 已完成的人员指派旧冻结绑定缺少视图快照是已知历史项，不修改旧记录来让比对通过，也不把它混作最新五个模板的缺项。

## 未完成的确保项

缺项尚未补齐。本轮用户指令为数据库比对和确认，未实施发布、停用、数据迁移、建表或权限变更。若要做到“全部存在且可用”，需按上述源状态准备定向配置同步，映射依赖 ID，保留测试现有版本/数据/历史，并在同步后重跑同口径比较；不能复制整库或直接覆盖冻结数据。

BPM 部署资源/流程 key、全部割接关联字段、所有系统参数以及全角色权限的完整闭环，不由本表计数证明；这些仍沿用原总报告的未验边界。本轮未修改应用代码，未进行发布或业务写入验收。

## 证据

- `.run/template-full-audit-20261025/active.py`：本轮筛选、数据库引用扫描、自然键及内容比对脚本。
- `.run/template-full-audit-20261025/active-comparison.json`：逐条标识、存在性、差异字段、状态机与割接子配置计数。
- `.run/template-full-audit-20261025/dependencies.py` / `dependencies.json`：系统参数值摘要比较、流程部署 key 与五套模板直接引用检查。
- `.run/template-full-audit-20261025/audit.json`、`details.json`：本轮重新读取的结构和配置摘要。
- 本报告细化用户最新口径；原 `report.md` 保留首次全表盘点事实，其中巡检“3 条发布”类推断以本专项的 2 条发布+1 条历史引用为准。
