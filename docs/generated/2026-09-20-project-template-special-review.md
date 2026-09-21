# 项目模板专项功能审查与浏览器验收记录

日期：2026-09-20。依据：本次需求方明确要求的专项审查及 `FPROJ009_E2E_ACCEPT` 参考目标；不以工程实施链、历史 Gate 或 Feature 状态作为功能通过依据。

## 当前结论

**本轮“审查与可配置模板”已完成；模板配置的保存、回开和 Compiler 预检通过。尚未达到 S1～S6 真实业务闭环验收目标。**

代码中已有独立业务操作授权、业务结果事件、结果订阅、规则重评及正式状态转换机制；但接入范围没有覆盖参考模板全部业务，交付件满足状态与门禁之间存在缺口。已有正常闭环项目不能证明这些能力已通过验收。

本记录区分代码审查、现存数据观察、本次浏览器配置和本次业务验收。尚未执行的业务闭环不记为通过；不回写 Feature/Task、PRD、SDS 或历史审查状态。

## 环境与执行分工

- 当前分支本地 `.env` 已配置 `NPDMS_FRONTEND_PORT=18181`、`NPDMS_BACKEND_PORT=58181`、`COMPOSE_PROJECT_NAME=npdms-50eb-test`；`.gitignore` 忽略此文件，不要求提交。
- 实际数据库：`npdms`，MySQL 宿主端口 `13306`，Redis `16379`。这些是本次需求方确认的当前分支环境。
- 前端 `http://localhost:18181/`、后端 `http://localhost:58181/actuator/health` 均返回 200。
- 已核对前端进程属于当前工作树；后端 JVM 的工作目录和类路径解析为当前工作树的 `yudao-server/target/yudao-server.jar`。旧 `.run/backend.pid` 已失效，不作为运行来源证据。
- `gpt-5.6-sol` 负责真实浏览器配置与业务操作；主代理独立核对截图、操作记录、数据库和代码。`gpt-5.6-terra` 只处理浏览器通道及进程来源诊断。
- 内置 Browser 因受管理运行包版本缺失未能建立连接；之后使用独立 Playwright 驱动真实 Chrome。没有修补插件缓存，没有以 API/SQL 写入替代浏览器操作。

运行来源证据：[runtime-source-check-20260920.md](../../.run/template-special-20260920/runtime/runtime-source-check-20260920.md)。

## 参考模板与现存闭环项目

数据库直接读取：

| 对象 | 标识 | 实际状态 |
|---|---|---|
| 参考模板 | `993009001590` / `FPROJ009_E2E_ACCEPT` | ACTIVE |
| 最新发布修订 | `993009001596` / v2 | PUBLISHED |
| 参考正常闭环项目 | `992203060006` | NORMAL_CLOSED，当前阶段 S6 |

参考 v2 包含 7 个阶段、14 个任务、32 项交付件。7 个阶段使用 `STAGE_NATIVE`，14 个任务使用 `TASK_NATIVE`；完成条件分别为 `STAGE_NATIVE_STATUS`、`TASK_NATIVE_STATUS`，未配置结果订阅。交付件配置的来源均为 `UPLOAD`。

独立数据库证据显示，项目 `992203060006` 的 7 个阶段、14 个任务均为 DONE，12 个门禁均为 PASSED，但 ACC 权威交付件全部为 PENDING：21 项必选、11 项可选；交付件来源版本数量为 0，结果订阅数量为 0。这能证明原生执行契约下当前已闭环的存储状态，不能证明历史操作过程，也不能证明业务结果订阅、交付件关联提交和交付件门禁已经完成。

证据：[参考模板修订原文](../../.run/template-special-20260920/review/reference-template-revisions.jsonl)、[17:49 数据库快照](../../.run/template-special-20260920/review/database-20260920-174949.jsonl)。复查脚本 [capture-readonly.ps1](../../.run/template-special-20260920/review/capture-readonly.ps1) 只执行 SELECT，不改变数据库。

## 功能审查发现

### 1. 全流程现有业务入口及结果尚未全部接入模板

前端 `BusinessView` 的静态装载目录只提供：

- `SOL_SITE_SURVEY`：现场工勘。
- `PROJ_REQUIREMENT_ANALYSIS`：需求分析。
- `PLN_CONSTRUCTION_PLAN`：项目工期页面。
- `ACC_ACCEPTANCE_REPORT`：验收报告。
- 另有通用动态表单，但动态表单本身不能替代其他业务模块的真实结果。

未登记任意组件路径为可执行代码，是正确的边界；但也意味着不能仅填写旧页面 URL 就获得完整业务接入。页面跳转本身不产生模板任务完成事实。

| 阶段 | 参考业务 | 已发现的可装载页面 | 尚缺的模板接入 |
|---|---|---|---|
| S1 | 工勘、需求分析、工程启动会 | 工勘、需求分析 | 工程启动会 |
| S2 | 施工计划制定 | 项目工期 | 当前可选事实是工期基线生效，不能等同完整施工计划编制、审批和发布 |
| S3 | 实施方案编审 | 无对应静态适配 | 实施方案 |
| S4 | 到货验收、硬件安装、配置调试、业务联调 | 无对应静态适配 | 上述 4 类业务 |
| S5 | 培训、初验、满意度、终验 | 验收报告 | 培训、满意度的模板页面适配 |
| S6 | 项目闭环办理 | 存在独立闭环功能，未在该目录发现模板页面适配 | 模板内的闭环办理接入及防止循环依赖的完成语义 |

结果订阅提供方仅发现 3 类：`SOL/SITE_SURVEY/SURVEY_CONFIRMED`、`SOL/REQUIREMENT_ANALYSIS/REQUIREMENT_ANALYSIS_COMPLETED`、`ACC/ACCEPTANCE/REPORT_VERSION_PUBLISHED`。因此不能把其他阶段改为纯结果订阅后假定会自动产生对应事件。

验收页面的任务完成事实只有 `REPORT_EFFECTIVE`，其标签明确为“当前报告证据有效（不等同验收通过）”。验收提供方没有声明 `ACCEPTANCE_PASSED`；关联对象也只解析本项目既有初验/终验活动。配置页面入口不能补出独立验收活动创建、验收资格或通过结论。初验与终验应明确关联对应活动，不能因为同一报告页面可打开就认定已完成两类业务。

代码证据：[前端受控组件目录](../../yudao-ui/yudao-ui-admin-vue3/src/components/BusinessView/registry.ts)、[页面路径映射](../../yudao-ui/yudao-ui-admin-vue3/src/components/BusinessView/presentationRoute.ts)、[验收任务事实提供方](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/service/taskbusiness/AcceptanceTaskBusinessObjectProvider.java)、[工期任务事实提供方](../../pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/taskbusiness/ConstructionPlanTaskBusinessObjectProvider.java)。

### 2. 交付件成果归档与门禁要求的满足状态没有接通

`ProjectDeliverableStageGateFactProvider` 只有在 `acc_project_deliverable.status=ACCEPTED` 时返回满足。当前交付件初始化写入 PENDING；验收报告/满意度成果投影维护来源版本和 `archiveStatus`，归档完成更新 ARCHIVED。对应正式服务中未找到把交付件转换为 SUBMITTED/ACCEPTED 的通用办理入口。

这不是“上传成功即交付完成”的问题，而是来源关联、归档、提交、验收状态需要有明确且贯通的正式业务语义。不能直接修改数据库状态让门禁通过。

模板节点的交付件清单编辑器目前维护名称、编码、归属和必选标记；任务运行页只显示应交清单。该清单不能替代成果关联提交功能。旧闭环项目 21 项必选仍为 PENDING，是未满足本次目标的运行数据证据。

代码证据：[交付件门禁事实](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/service/acceptance/ProjectDeliverableStageGateFactProvider.java)、[报告成果投影](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/service/acceptancereport/AcceptanceReportSourceProjectionService.java)、[归档补偿](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/service/acceptancereport/AcceptanceReportArchiveCompensationService.java)、[交付件清单编辑器](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-templates/NodeDeliverableChecklist.vue)、[项目执行清单](../../yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectFlowPanel.vue)。

### 3. 正常闭环检查未覆盖纯结果订阅任务

`NormalClosureCheckService` 对已完成任务只接受业务绑定的重新核验，或显式 `TASK_NATIVE + TASK_NATIVE_STATUS` 契约。`RESULT_SUBSCRIPTION` 不在这两类中，会进入 `TASK_NATIVE_CONTRACT=false` 分支。审批绑定也需要单独核对接入覆盖。

结果订阅执行链已支持在证据满足时调用正式任务状态机；正常闭环检查必须消费同一冻结执行轮次的有效证据，否则前序任务可能正常完成，最终闭环仍被拒绝。本项为确定的代码覆盖缺口，尚未伪称完成端到端复现。

代码证据：[正常闭环检查](../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/normalclosure/NormalClosureCheckService.java)、[业务结果任务推进](../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskLifecycleService.java)。

### 4. 参考模板图形连线和门禁节点不等于 V2 的阶段约束

参考发布 v2 的所有阶段 `admissionRuleKey`、`exitRuleKey` 均为空。V2 阶段激活直接读取冻结准入规则；没有规则时按真值处理。阶段退出同样只执行显式退出规则；旧 `transitions` 和独立 ENTRY/EXIT 门禁不会自动补成这些规则。因此不能依据画面上的 S1→S2 连线或“准入配置”节点，认定已经约束顺序或阻断退出。

本次草稿已在阶段侧栏明确绑定前阶段完成条件及退出事实，并把必选交付件同时关联到退出门禁和阶段退出规则。这部分配置缺口已在新草稿中补齐；参考模板没有改写。

另一个执行边界是：参考阶段使用 `STAGE_NATIVE`，`ProjectStageCompletionService` 要求本轮真实提交后才继续完成判定。业务任务完成、页面配置和事件投递不会自动替代这一人工提交。此项是当前模板选择的执行语义，不应据此把系统所有阶段一概判断为不支持业务驱动。

代码证据：[阶段规则冻结](../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projecttemplate/TemplateCompiler.java)、[阶段准入](../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/runtimegraph/ProjectStageAdmissionService.java)、[阶段完成与退出](../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectplan/ProjectStageCompletionService.java)。

## 已核对的正确机制

- `ProjectIndependentOperationAdmission` 对独立业务入口重新检查当前项目数据范围，不以隐式当前任务作为普通业务操作的唯一许可来源；受控入口仍保留正式命令校验。
- `ProjectOperationDispatcher` 使用冻结计划和执行轮次选择操作版本，没有按最新版本替换旧回执。
- `EngineeringRuleReevaluationEvents`、`AcceptanceOperationResultBridge` 在业务事务内记录结果；提交后经 Outbox 投递、失败重试。
- `ProjectRuntimeCoordinator` 在规则重评时检查阶段准入、门禁、任务和阶段完成；`ProjectBusinessTaskCompletionService` 同步关联对象并通过真实完成事实推进任务。业务绑定任务不必仅靠新增纯结果订阅才能被重评。
- `ProjectOperationNodeResultProcessor`、`ProjectResultEvidenceProcessor` 重新读取 Owner 事实后调用正式任务/阶段推进服务，不以消息送达替代完成。
- `ProjectTaskLifecycleService.completeFromBusinessResult` 经过冻结状态机和完成条件；`ProjectGateRuleService` 重新评估冻结规则与实时事实。
- 已完成节点证据失效时，结果处理器记录返工审查要求并保留完成历史，不自动重置历史节点。

以上是本次代码审查结论，不能替代本次真实业务验收。

## 本次浏览器配置结果

已通过真实 UI 复制参考模板发布 v2，创建：

- 编码：`FPROJ009_E2E_S1S6_20260920`。
- 名称：`专项验收-S1~S6全流程-20260920`。
- 模板 ID：`993009001591`；草稿修订 ID：`993009001597`。
- 已删除 S0、原 S0→S1 连线、S0 所属交付件及失去适用条件的 S1 空准入门禁。保存并重开后为 6 个阶段、14 个任务、29 项交付件、11 个门禁；交付件为 20 项必选、9 项可选。
- 原参考模板和既有项目保持不变。

4 个本次专用 BusinessView 均通过真实浏览器完成登记、校验和发布，编码统一使用 `FPROJ009_E2E_S1S6_20260920_` 前缀。已配置 5 个业务任务，并由主代理在 17:41 独立 SQL 回读确认：

| 阶段/任务 | 页面组件 | 保存后的任务绑定 | 完成依据 |
|---|---|---|---|
| S1 现场工勘 | `SOL_SITE_SURVEY` | BUSINESS_COMPONENT | `SURVEY_CONFIRMED`，全部关联记录满足；不等同实施就绪 |
| S1 需求分析 | `PROJ_REQUIREMENT_ANALYSIS` | BUSINESS_OBJECT | `REQUIREMENT_ANALYSIS_COMPLETED`，全部关联记录满足 |
| S2 施工计划制定 | `PLN_CONSTRUCTION_PLAN` | BUSINESS_COMPONENT | `DURATION_EFFECTIVE`，仅当前有效工期基线生效 |
| S5 项目初验 | `ACC_ACCEPTANCE_REPORT` | BUSINESS_COMPONENT | `REPORT_EFFECTIVE`，仅报告证据有效 |
| S5 项目终验 | `ACC_ACCEPTANCE_REPORT` | BUSINESS_COMPONENT | `REPORT_EFFECTIVE`，仅报告证据有效 |

需求分析复用“需求分析标准模板·第 1 版”，修订 ID `992203020001`；表单版本参数已存入绑定。所有 5 个任务均使用 `REFERENCE_EXISTING`，按项目解析既有业务对象，未向模板写入某个项目实例 ID。初验和终验绑定同一个项目级报告视图，当前配置 UI 没有验收类型区分字段；实际项目需要明确关联对应活动，本次未验证初终验隔离。

其余 9 个任务保留参考模板的原生配置，不能视为业务接入完成。6 个阶段继续使用 `STAGE_NATIVE` 及本轮真实提交的完成语义。V2 阶段准入依据冻结的 `admissionRuleKey`，不以旧 `start` 元数据判定，因此保留原 S1，不重建阶段。

阶段规则已配置如下；退出条件均为 ALL，沿用参考模板已有退出任务要求并加入本阶段全部必选交付件。

| 阶段 | 准入条件 | 退出门禁和阶段退出规则中的必选交付件数 |
|---|---|---:|
| S1 | 无前阶段条件 | 4 |
| S2 | S1 已完成 | 2 |
| S3 | S2 已完成 | 2 |
| S4 | S3 已完成 | 5 |
| S5 | S4 已完成 | 5 |
| S6 | S5 已完成 | 2 |

S1 退出的任务要求保持为现场工勘、需求分析；工程启动会任务和其原有交付件保留，没有额外加入启动会任务完成约束。29 项交付件的原有必选/可选属性均保留。20 项必选交付件同时出现在对应 EXIT 门禁和阶段退出规则中；没有以缺少正式交付件满足状态为由移除要求。

配置证据：[最终草稿及规则回读](../../.run/template-special-20260920/review/configured-template-summary-20260920-175330.json)、[独立逐阶段核对结果](../../.run/template-special-20260920/review/final-configuration-verification.json)、[4 个页面的发布记录](../../.run/template-special-20260920/browser/business-view-registration-results.json)、[页面发布截图](../../.run/template-special-20260920/browser/18-business-views-published-list.png)。

浏览器操作记录：[operations.jsonl](../../.run/template-special-20260920/browser/operations.jsonl)。关键截图：[复制成功](../../.run/template-special-20260920/browser/05-template-copied.png)、[无 S0 草稿保存](../../.run/template-special-20260920/browser/09-s1s6-draft-saved.png)、[列表回读](../../.run/template-special-20260920/browser/10-s1s6-list-reopen-point.png)。主代理已结合截图及独立 SQL 核对创建结果。

## 本轮验证结果

- 浏览器保存并关闭、重新进入模板后，阶段、任务、交付件、业务绑定和规则均可回读：[回开核对](../../.run/template-special-20260920/browser/reopened-designer-verification.json)、[截图](../../.run/template-special-20260920/browser/24-reopened-designer-verified.png)。
- Compiler 预检通过：HTTP 200、业务 `code=0`、`data.valid=true`、`issues=[]`；[完整业务响应](../../.run/template-special-20260920/browser/compiler-precheck-result.json)、[预检截图](../../.run/template-special-20260920/browser/25-compiler-precheck-result.png)。预检不是发布或业务完成，正式发布仍会重新编译并校验依赖。
- 主代理在最终保存后独立读取数据库：恰为 S1～S6，无孤立任务、交付件或缺失节点规则；逐阶段准入匹配前阶段，退出 ALL 条件与门禁引用均覆盖既有任务要求和 20 项必选交付件；必选/可选属性与参考一致。
- 4 个 BusinessView 已发布；项目模板及修订仍为 DRAFT。没有发布本次项目模板，没有创建其项目实例，没有把真实业务闭环记为通过。

## 范围与未执行事项

目前只进行源码审查、只读数据库核对、环境核对和已授权的浏览器配置，没有修改业务源码、权限、状态机或数据库 Schema，没有提交或推送。无需为这些只读审查和配置重复运行全库编译或测试。

真实业务事件推进、交付件关联提交、门禁先拒绝后通过、最终闭环与历史保护的本次业务验收尚未完成。需求方在本次对话中明确选择“先完成审查与可配置模板”，因此本轮不进入源码修复，也不将不完整模板发布投用。后续需补齐已发现的实现缺口再开展业务闭环；不能通过把未接入业务换成人工完成、减少业务范围或直接改状态来补出通过结论。
