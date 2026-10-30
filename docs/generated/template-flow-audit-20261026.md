# 五类正式模板配置与项目64557阻断核查

日期：2026-10-26。测试库 tenant=1，只读一致性快照；未修改业务配置、状态或历史。

## 结论

补充：用户进一步指出其他任务均展示前置阻断提示。本轮定位到真实提示缺陷：ProjectTaskQueryService.getWorkbench 仅对 APPROVAL、BUSINESS_OBJECT、BUSINESS_COMPONENT 返回 startAdmission，漏掉 PAGE；前端 ProjectFlowPanel 已有统一提示组件。因此前述准入阻断事实成立，但不能解释为提示正常。已在本地将 PAGE 纳入原有准入预览分支，复用权限裁剪及失败关闭规则，不改变模板、项目状态或安装完成要求。尚未提交、部署或重启，浏览器实测仍未完成。

验证：后端 ProjectTaskQueryServiceTest 22项通过（覆盖PAGE条件满足、不满足、未知及受限范围隐藏详情）；前端 flowPanel.runtime.spec.ts 34项通过，新增“配置调试待分配且交付件满足时仍展示硬件实施前置”的用例。初次测试暴露测试替身缺少PAGE路由和嵌入组件mock，补齐后重跑通过；没有将这些替身错误计为产品缺陷。后端沿用worktree-toolchain及同一构建锁，日志为 `.run/template-flow-audit-20261026/admission-tests.log`。

项目 `16201224031601N-0`（64557）使用普通类非直签发布v2，活动计划5349。配置调试未准入：其前置硬件实施仍为IN_PROGRESS。当前项目102台设备只有20台具有有效已完成安装记录，82台缺少；依据当前Owner规则，硬件实施不能完成。

配置调试交付件满足不是全部完成条件：五类模板均要求 TASK_NATIVE_STATUS(DONE) 与 D_CONFIGURATION 同时满足。当前代码将这个原生谓词解释为本轮已提交完成（submitted_at非空），不是循环要求任务预先DONE。项目64557未准入、未开始、未提交；普通直签84574两轮历史均有提交完成及完成规则MATCHED证据。

不能据此认定“非直签普通模板损坏”，也不能把82台缺失记录直接补成完成。需按真实安装情况补齐有效记录；若设备范围本身错误，需先按业务归属核实范围。

## 五套模板逐项配置核查

| 模板 | 发布版 | 阶段/任务/交付件/门禁/迁移边 | 配置调试准入 | 引用检查 |
|---|---:|---|---|---|
| 非直签－原厂督导 `TPL-CFG20261014-CHANNEL-SUPERVISION` | 2 | 7/13/6/5/7 | 无独立任务准入规则，仍受阶段准入控制 | 所检引用均存在 |
| 工程类－非直签 `TPL-CFG20261014-ENGINEERING-CHANNEL` | 2 | 7/13/6/5/7 | 硬件实施完成 | 所检引用均存在 |
| 工程类－直签 `TPL-CFG20261014-ENGINEERING-DIRECT` | 2 | 7/16/9/5/7 | 硬件实施完成 | 所检引用均存在 |
| 普通类－非直签 `TPL-CFG20261014-GENERAL-CHANNEL` | 2 | 7/13/6/5/7 | 硬件实施完成 | 所检引用均存在 |
| 普通类－直签 `TPL-CFG20261014-GENERAL-DIRECT` | 2 | 7/16/9/5/7 | 硬件实施完成 | 所检引用均存在 |

检查覆盖每套发布设计的阶段/任务、准入/完成/准出规则键、TASK/STAGE/DELIVERABLE引用、门禁、迁移边、业务视图发布状态和冻结快照、视图表单修订存在性。此为配置引用完整性核查，不能替代审批人员解析、文件下载或五类真实全流程验收。

普通直签和工程直签各有到货验收、初验、终验节点；普通非直签、工程非直签、原厂督导没有这三个节点。原厂督导的配置调试不设硬件实施前置，其余四类设有此前置。五类配置调试/业务联调均是PAGE绑定，完成规则均为原生提交与交付件的ALL组合。

## 受影响项目与已走通项目对照

| 证据 | 非直签普通64557 | 直签普通84574 |
|---|---|---|
| 配置调试轮次状态 | PENDING，未准入 | DONE，已完成历史 |
| 任务状态 | PENDING_ASSIGN | DONE |
| 本轮提交完成 | submitted_at为空 | 两轮均非空，最近2026-10-24 23:38:23.214 |
| 前置硬件实施 | IN_PROGRESS；102台中20台有效完成、82台缺少 | 历史已准入并完成配置调试 |
| 完成契约 | TASK_NATIVE_STATUS + D_CONFIGURATION | 历史结果两个条件均MATCHED |

用户反馈交付件已满足；本轮未通过页面/API重新执行交付件判定，不将用户反馈改写成已独立验证。阻断根因有冻结准入规则、节点状态及安装事实三方证据。界面“待分配”没有直接表达前置阻断，建议后续显示“等待硬件实施完成（20/102）”；本轮未修改UI。

## 验证边界与后续处理

- 本地源库Docker引擎未运行，源库本轮无法重读；2026-10-25源库对比报告仅作历史参考，不能证明当前双库全部一致。
- 浏览器插件模块加载被本地权限拒绝，未能进行本轮UI复核；没有改用直接状态写入代替验收。
- 未启动服务、重启、迁移、发布模板、改写旧计划、修改项目历史或推送提交。
- 按真实业务补齐82台设备的有效安装事实后，才能检验硬件实施完成和配置调试自动准入。配置调试还须按当前规则提交完成；若业务期望交付件满足即自动完成，属于需要明确调整完成规则的需求，不能通过删除校验来假装修好。

## 证据和代码

- `.run/template-flow-audit-20261026/inspect.py`、`target.json`：测试库五类发布设计、项目活动计划、节点、任务、业务链接。源库连接失败已明确记录。
- `.run/template-flow-audit-20261026/details.py`、`details.json`：执行契约、完成评价、表单修订、102台项目设备和20条安装记录，只读事务。
- `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/runtimegraph/ProjectTaskAdmissionService.java`：准入不满足返回，保持节点PENDING。
- `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskPlanCompletionService.java`：TASK_NATIVE_STATUS按round.submittedAt判定。
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/installation/InstallationBusinessResultSource.java`：项目设备必须全部具有当前有效已完成安装记录及位置。
- `pms-module-asset/src/main/resources/mapper/device/DeviceQueryMapper.xml`：安装设备范围按tenant_id/project_id/deleted筛选。
- 需求归属PM-03、PM-11；Feature F-PROJ-009、F-PROJ-008、F-PROJ-007。本报告不晋级Feature验收状态。

## 每类任务明细

### 非直签－原厂督导

匹配配置：`{"signingMethod": "CHANNEL_SIGN", "projectCategory": null, "majorProjectLevel": null, "implementationMethod": "SUPERVISION"}`。

| 节点 | 绑定 | 准入规则 | 完成规则 |
|---|---|---|---|
| 人员指派 `PROJECT_ASSIGNMENT` | BUSINESS_OBJECT | 阶段准入 | `FLOW_ASSIGNMENT_DONE` |
| 用户信息录入 `PRE_CONTACT` | PAGE | 阶段准入 | `END_USER_PRESENT_DONE_20261015` |
| 现场工勘 `PRE_SURVEY` | BUSINESS_COMPONENT | 阶段准入 | `PRE_SURVEY_DONE` |
| 需求分析 `PRE_REQUIREMENT` | BUSINESS_OBJECT | 阶段准入 | `PRE_REQUIREMENT_DONE` |
| 施工计划 `PLAN_CONSTRUCTION` | BUSINESS_OBJECT | 阶段准入 | `PLAN_CONSTRUCTION_DONE` |
| 实施方案 `PLAN_SOLUTION` | BUSINESS_OBJECT | 阶段准入 | `PLAN_SOLUTION_APPROVAL_DONE` |
| 硬件实施 `DEPLOY_HARDWARE` | BUSINESS_COMPONENT | 阶段准入 | `DEPLOY_HARDWARE_OWNER_DONE_20261014` |
| 配置调试 `DEPLOY_CONFIG` | PAGE | 阶段准入 | `DEPLOY_CONFIG_DONE` |
| 业务联调 `DEPLOY_JOINT` | PAGE | DEPLOY_JOINT_ENTER | `DEPLOY_JOINT_DONE` |
| 割接上线 `DEPLOY_CUTOVER` | BUSINESS_COMPONENT | DEPLOY_CUTOVER_ENTER | `DEPLOY_CUTOVER_OWNER_DONE_20261014` |
| 现场培训 `ACCEPT_TRAINING` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_TRAINING_OWNER_DONE_20261014` |
| 满意度调查 `ACCEPT_SATISFACTION` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_SATISFACTION_DONE` |
| 项目闭环 `PROJECT_CLOSURE` | PAGE | 阶段准入 | `PROJECT_CLOSURE_OWNER_DONE_20261014` |

关联业务视图：ACC_SATISFACTION, CUT_CUTOVER_APPROVAL_WORKBENCH, FPROJ009_E2E_S1S6_20260920_REQUIREMENT_ANALYSIS, FPROJ009_E2E_S1S6_20260920_SITE_SURVEY, IMP_PROJECT_INSTALLATION, IMP_TRAINING_CONFIRMATION, VISIO_DELIVERY_20260922_IMPLEMENTATION_SOLUTION, VISIO_DELIVERY_20260922_PROJECT_ASSIGNMENT, VISIO_DELIVERY_20260922_STAGE_PLAN。

所检引用问题：未发现缺失。

### 工程类－非直签

匹配配置：`{"signingMethod": "CHANNEL_SIGN", "projectCategory": "ENGINEERING", "majorProjectLevel": null, "implementationMethod": null}`。

| 节点 | 绑定 | 准入规则 | 完成规则 |
|---|---|---|---|
| 人员指派 `PROJECT_ASSIGNMENT` | BUSINESS_OBJECT | 阶段准入 | `FLOW_ASSIGNMENT_DONE` |
| 用户信息录入 `PRE_CONTACT` | PAGE | 阶段准入 | `END_USER_PRESENT_DONE_20261015` |
| 现场工勘 `PRE_SURVEY` | BUSINESS_COMPONENT | 阶段准入 | `PRE_SURVEY_DONE` |
| 需求分析 `PRE_REQUIREMENT` | BUSINESS_OBJECT | 阶段准入 | `PRE_REQUIREMENT_DONE` |
| 施工计划 `PLAN_CONSTRUCTION` | BUSINESS_OBJECT | 阶段准入 | `PLAN_CONSTRUCTION_DONE` |
| 实施方案 `PLAN_SOLUTION` | BUSINESS_OBJECT | 阶段准入 | `PLAN_SOLUTION_APPROVAL_DONE` |
| 硬件实施 `DEPLOY_HARDWARE` | BUSINESS_COMPONENT | 阶段准入 | `DEPLOY_HARDWARE_OWNER_DONE_20261014` |
| 配置调试 `DEPLOY_CONFIG` | PAGE | DEPLOY_CONFIG_ENTER | `DEPLOY_CONFIG_DONE` |
| 业务联调 `DEPLOY_JOINT` | PAGE | DEPLOY_JOINT_ENTER | `DEPLOY_JOINT_DONE` |
| 割接上线 `DEPLOY_CUTOVER` | BUSINESS_COMPONENT | DEPLOY_CUTOVER_ENTER | `DEPLOY_CUTOVER_OWNER_DONE_20261014` |
| 现场培训 `ACCEPT_TRAINING` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_TRAINING_OWNER_DONE_20261014` |
| 满意度调查 `ACCEPT_SATISFACTION` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_SATISFACTION_DONE` |
| 项目闭环 `PROJECT_CLOSURE` | PAGE | 阶段准入 | `PROJECT_CLOSURE_OWNER_DONE_20261014` |

关联业务视图：ACC_SATISFACTION, CUT_CUTOVER_APPROVAL_WORKBENCH, FPROJ009_E2E_S1S6_20260920_REQUIREMENT_ANALYSIS, FPROJ009_E2E_S1S6_20260920_SITE_SURVEY, IMP_PROJECT_INSTALLATION, IMP_TRAINING_CONFIRMATION, VISIO_DELIVERY_20260922_IMPLEMENTATION_SOLUTION, VISIO_DELIVERY_20260922_PROJECT_ASSIGNMENT, VISIO_DELIVERY_20260922_STAGE_PLAN。

所检引用问题：未发现缺失。

### 工程类－直签

匹配配置：`{"signingMethod": "DIRECT_SIGN", "projectCategory": "ENGINEERING", "majorProjectLevel": null, "implementationMethod": null}`。

| 节点 | 绑定 | 准入规则 | 完成规则 |
|---|---|---|---|
| 人员指派 `PROJECT_ASSIGNMENT` | BUSINESS_OBJECT | 阶段准入 | `FLOW_ASSIGNMENT_DONE` |
| 用户信息录入 `PRE_CONTACT` | PAGE | 阶段准入 | `END_USER_PRESENT_DONE_20261015` |
| 现场工勘 `PRE_SURVEY` | BUSINESS_COMPONENT | 阶段准入 | `PRE_SURVEY_DONE` |
| 需求分析 `PRE_REQUIREMENT` | BUSINESS_OBJECT | 阶段准入 | `PRE_REQUIREMENT_DONE` |
| 施工计划 `PLAN_CONSTRUCTION` | BUSINESS_OBJECT | 阶段准入 | `PLAN_CONSTRUCTION_DONE` |
| 实施方案 `PLAN_SOLUTION` | BUSINESS_OBJECT | 阶段准入 | `PLAN_SOLUTION_APPROVAL_DONE` |
| 到货签收 `DEPLOY_ARRIVAL` | BUSINESS_COMPONENT | 阶段准入 | `DEPLOY_ARRIVAL_ENTITY_DONE_20261019` |
| 硬件实施 `DEPLOY_HARDWARE` | BUSINESS_COMPONENT | DEPLOY_HARDWARE_ENTER | `DEPLOY_HARDWARE_OWNER_DONE_20261014` |
| 配置调试 `DEPLOY_CONFIG` | PAGE | DEPLOY_CONFIG_ENTER | `DEPLOY_CONFIG_DONE` |
| 业务联调 `DEPLOY_JOINT` | PAGE | DEPLOY_JOINT_ENTER | `DEPLOY_JOINT_DONE` |
| 割接上线 `DEPLOY_CUTOVER` | BUSINESS_COMPONENT | DEPLOY_CUTOVER_ENTER | `DEPLOY_CUTOVER_OWNER_DONE_20261014` |
| 现场培训 `ACCEPT_TRAINING` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_TRAINING_OWNER_DONE_20261014` |
| 满意度调查 `ACCEPT_SATISFACTION` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_SATISFACTION_DONE` |
| 初验 `ACCEPT_INITIAL` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_INITIAL_ENTITY_DONE_20261019` |
| 终验 `ACCEPT_FINAL` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_FINAL_ENTITY_DONE_20261019` |
| 项目闭环 `PROJECT_CLOSURE` | PAGE | 阶段准入 | `PROJECT_CLOSURE_OWNER_DONE_20261014` |

关联业务视图：ACC_SATISFACTION, CUT_CUTOVER_APPROVAL_WORKBENCH, FPROJ009_E2E_S1S6_20260920_ACCEPTANCE_REPORT, FPROJ009_E2E_S1S6_20260920_REQUIREMENT_ANALYSIS, FPROJ009_E2E_S1S6_20260920_SITE_SURVEY, IMP_ARRIVAL_RECEIPT, IMP_PROJECT_INSTALLATION, IMP_TRAINING_CONFIRMATION, VISIO_DELIVERY_20260922_IMPLEMENTATION_SOLUTION, VISIO_DELIVERY_20260922_PROJECT_ASSIGNMENT, VISIO_DELIVERY_20260922_STAGE_PLAN。

所检引用问题：未发现缺失。

### 普通类－非直签

匹配配置：`{"signingMethod": "CHANNEL_SIGN", "projectCategory": "GENERAL", "majorProjectLevel": null, "implementationMethod": null}`。

| 节点 | 绑定 | 准入规则 | 完成规则 |
|---|---|---|---|
| 人员指派 `PROJECT_ASSIGNMENT` | BUSINESS_OBJECT | 阶段准入 | `FLOW_ASSIGNMENT_DONE` |
| 用户信息录入 `PRE_CONTACT` | PAGE | 阶段准入 | `END_USER_PRESENT_DONE_20261015` |
| 现场工勘 `PRE_SURVEY` | BUSINESS_COMPONENT | 阶段准入 | `PRE_SURVEY_DONE` |
| 需求分析 `PRE_REQUIREMENT` | BUSINESS_OBJECT | 阶段准入 | `PRE_REQUIREMENT_DONE` |
| 施工计划 `PLAN_CONSTRUCTION` | BUSINESS_OBJECT | 阶段准入 | `PLAN_CONSTRUCTION_DONE` |
| 实施方案 `PLAN_SOLUTION` | BUSINESS_OBJECT | 阶段准入 | `PLAN_SOLUTION_APPROVAL_DONE` |
| 硬件实施 `DEPLOY_HARDWARE` | BUSINESS_COMPONENT | 阶段准入 | `DEPLOY_HARDWARE_OWNER_DONE_20261014` |
| 配置调试 `DEPLOY_CONFIG` | PAGE | DEPLOY_CONFIG_ENTER | `DEPLOY_CONFIG_DONE` |
| 业务联调 `DEPLOY_JOINT` | PAGE | DEPLOY_JOINT_ENTER | `DEPLOY_JOINT_DONE` |
| 割接上线 `DEPLOY_CUTOVER` | BUSINESS_COMPONENT | DEPLOY_CUTOVER_ENTER | `DEPLOY_CUTOVER_OWNER_DONE_20261014` |
| 现场培训 `ACCEPT_TRAINING` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_TRAINING_OWNER_DONE_20261014` |
| 满意度调查 `ACCEPT_SATISFACTION` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_SATISFACTION_DONE` |
| 项目闭环 `PROJECT_CLOSURE` | PAGE | 阶段准入 | `PROJECT_CLOSURE_OWNER_DONE_20261014` |

关联业务视图：ACC_SATISFACTION, CUT_CUTOVER_APPROVAL_WORKBENCH, FPROJ009_E2E_S1S6_20260920_REQUIREMENT_ANALYSIS, FPROJ009_E2E_S1S6_20260920_SITE_SURVEY, IMP_PROJECT_INSTALLATION, IMP_TRAINING_CONFIRMATION, VISIO_DELIVERY_20260922_IMPLEMENTATION_SOLUTION, VISIO_DELIVERY_20260922_PROJECT_ASSIGNMENT, VISIO_DELIVERY_20260922_STAGE_PLAN。

所检引用问题：未发现缺失。

### 普通类－直签

匹配配置：`{"signingMethod": "DIRECT_SIGN", "projectCategory": "GENERAL", "majorProjectLevel": null, "implementationMethod": null}`。

| 节点 | 绑定 | 准入规则 | 完成规则 |
|---|---|---|---|
| 人员指派 `PROJECT_ASSIGNMENT` | BUSINESS_OBJECT | 阶段准入 | `FLOW_ASSIGNMENT_DONE` |
| 用户信息录入 `PRE_CONTACT` | PAGE | 阶段准入 | `END_USER_PRESENT_DONE_20261015` |
| 现场工勘 `PRE_SURVEY` | BUSINESS_COMPONENT | 阶段准入 | `PRE_SURVEY_DONE` |
| 需求分析 `PRE_REQUIREMENT` | BUSINESS_OBJECT | 阶段准入 | `PRE_REQUIREMENT_DONE` |
| 施工计划 `PLAN_CONSTRUCTION` | BUSINESS_OBJECT | 阶段准入 | `PLAN_CONSTRUCTION_DONE` |
| 实施方案 `PLAN_SOLUTION` | BUSINESS_OBJECT | 阶段准入 | `PLAN_SOLUTION_APPROVAL_DONE` |
| 到货签收 `DEPLOY_ARRIVAL` | BUSINESS_COMPONENT | 阶段准入 | `DEPLOY_ARRIVAL_ENTITY_DONE_20261019` |
| 硬件实施 `DEPLOY_HARDWARE` | BUSINESS_COMPONENT | DEPLOY_HARDWARE_ENTER | `DEPLOY_HARDWARE_OWNER_DONE_20261014` |
| 配置调试 `DEPLOY_CONFIG` | PAGE | DEPLOY_CONFIG_ENTER | `DEPLOY_CONFIG_DONE` |
| 业务联调 `DEPLOY_JOINT` | PAGE | DEPLOY_JOINT_ENTER | `DEPLOY_JOINT_DONE` |
| 割接上线 `DEPLOY_CUTOVER` | BUSINESS_COMPONENT | DEPLOY_CUTOVER_ENTER | `DEPLOY_CUTOVER_OWNER_DONE_20261014` |
| 现场培训 `ACCEPT_TRAINING` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_TRAINING_OWNER_DONE_20261014` |
| 满意度调查 `ACCEPT_SATISFACTION` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_SATISFACTION_DONE` |
| 初验 `ACCEPT_INITIAL` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_INITIAL_ENTITY_DONE_20261019` |
| 终验 `ACCEPT_FINAL` | BUSINESS_COMPONENT | 阶段准入 | `ACCEPT_FINAL_ENTITY_DONE_20261019` |
| 项目闭环 `PROJECT_CLOSURE` | PAGE | 阶段准入 | `PROJECT_CLOSURE_OWNER_DONE_20261014` |

关联业务视图：ACC_SATISFACTION, CUT_CUTOVER_APPROVAL_WORKBENCH, FPROJ009_E2E_S1S6_20260920_ACCEPTANCE_REPORT, FPROJ009_E2E_S1S6_20260920_REQUIREMENT_ANALYSIS, FPROJ009_E2E_S1S6_20260920_SITE_SURVEY, IMP_ARRIVAL_RECEIPT, IMP_PROJECT_INSTALLATION, IMP_TRAINING_CONFIRMATION, VISIO_DELIVERY_20260922_IMPLEMENTATION_SOLUTION, VISIO_DELIVERY_20260922_PROJECT_ASSIGNMENT, VISIO_DELIVERY_20260922_STAGE_PLAN。

所检引用问题：未发现缺失。
