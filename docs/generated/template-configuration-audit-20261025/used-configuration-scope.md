# 五类正式业务模板实际使用与迁移范围判定

日期：2026-10-25。按最新用户指令，优先判断“最新五类模板配置、实际业务或验收是否使用”，不再把启用/PUBLISHED 本身当成迁移理由。本轮只读，未迁移或发布配置。

## 结论

1. 五类正式业务模板在源环境均有配置和项目使用证据，且每类都有 1 个 NORMAL_CLOSED 项目记录。测试环境有五套配置，但只有普通类直签的项目使用及关闭记录，不能声明测试环境五类均已完成验收。
2. 五套模板直接引用的 11 个业务视图自然键一致；3 个表单修订的自然键及内容摘要一致，测试均已存在。先前清单中的 3 个启用示例表单、12 个旧 direct-business 视图，不再仅因启用就列为必迁。
3. 割接确有配置和持久化内容使用证据；CUTOVER_STANDARD_V1 v2 在两库都有，主要配置快照摘要一致，维度 5、计划节次 6、清单定义与绑定规则各 133。无须再复制一套割接配置。
4. 五类项目在源/测试均无关联巡检任务，不存在与该范围关联的巡检规则选择、执行快照或检测结果；巡检规则及巡检 QA 采集模板不纳入这次迁移。源库另有 11 条其他项目的巡检任务，不当成五类模板的验收证据。
5. 已确认仍在使用且测试缺少的配置为 SAT_MANUAL_EXAMPLE 问卷 v1：源项目 992203060193 的满意度任务存在 PENDING_COLLECTION。不能凭名称是 EXAMPLE 排除它。其他未知依赖不自动列成已证实缺项，也不假称全量功能验收完成。

## 五类模板：配置与已有使用证据

| 模板编码 | 源最新发布 | 测试最新发布 | 源活动计划引用最新修订的项目数 | 源关闭项目记录 | 测试关闭项目记录 |
|---|---:|---:|---:|---|---|
| `TPL-CFG20261014-GENERAL-DIRECT` | 2 | 2 | 141 | 2110018733130579984 | 84574 |
| `TPL-CFG20261014-ENGINEERING-DIRECT` | 2 | 2 | 9 | 2110018733130579985 | 无 |
| `TPL-CFG20261014-GENERAL-CHANNEL` | 2 | 2 | 70 | 2110018733130579986 | 无 |
| `TPL-CFG20261014-ENGINEERING-CHANNEL` | 2 | 2 | 4 | 2110018733130579987 | 无 |
| `TPL-CFG20261014-CHANNEL-SUPERVISION` | 3 | 2 | 9 | 2110018733130579988 | 无 |

源项目集合按 lifecycle_template_id 或当前活动计划的 source_template_revision_id 解析，与五类模板匹配的项目共 240 个（235 ACTIVE、5 NORMAL_CLOSED）；其中一部分项目当前仍引用旧修订，因此不能把 240 都说成最新修订验收。上表明确按最新修订统计。NORMAL_CLOSED 是数据库已有业务结果证据，不替代本轮重新进行的浏览器全流程验收。

## 五套模板直接表单与业务视图依赖

业务视图共 11 个，两端集合一致：

- `ACCEPTANCE|FPROJ009_E2E_S1S6_20260920_ACCEPTANCE_REPORT|1`
- `ARRIVAL|IMP_ARRIVAL_RECEIPT|1`
- `CUTOVER_APPROVAL|CUT_CUTOVER_APPROVAL_WORKBENCH|1`
- `IMPLEMENTATION_SOLUTION|VISIO_DELIVERY_20260922_IMPLEMENTATION_SOLUTION|1`
- `INSTALLATION|IMP_PROJECT_INSTALLATION|1`
- `PROJECT_ASSIGNMENT|VISIO_DELIVERY_20260922_PROJECT_ASSIGNMENT|1`
- `REQUIREMENT_ANALYSIS|FPROJ009_E2E_S1S6_20260920_REQUIREMENT_ANALYSIS|1`
- `SATISFACTION|ACC_SATISFACTION|1`
- `SITE_SURVEY|FPROJ009_E2E_S1S6_20260920_SITE_SURVEY|1`
- `STAGE_PLAN|VISIO_DELIVERY_20260922_STAGE_PLAN|1`
- `TRAINING|IMP_TRAINING_CONFIRMATION|1`

直接引用表单修订：

- `PROJECT_PREPARATION_CONFIRMATION` v1：两端内容摘要相同。
- `SITE_SURVEY_BUSINESS_UI` v2：两端内容摘要相同。
- `SOL_PRE04_REQUIREMENT_ANALYSIS_V1` v1：两端内容摘要相同。

PROJECT_PREPARATION_CONFIRMATION 当前禁用，但仍在模板内保留历史修订引用，测试也已有相同修订。本轮不重新启用、不删除历史引用。

## 割接内容与验收证据

| 证据 | 源 | 测试 |
|---|---:|---:|
| 关联割接任务 | 21 | 1 |
| 清单 | 17 | 0 |
| 计划修订 | 25 | 1 |
| 引用文件 ID 的计划修订 | 4 | 0 |
| 填写结果 | 1360 | 0 |

- 源结果来源：DIRECT 1112、MANUAL 162、COLLECTION 86；结构化结果中有业务描述、背景、检查反馈、设备位置、接口/IP 规划等实际持久化记录，具体数量见 usage-scope-summary.json。仅证明存在填报/采集记录，不核实内容是否来自真实生产现场，也未验证文件可下载性。
- 测试项目 84574 有 ARCHIVED 割接任务及 1 个 SUBMITTED 计划修订，但未查到关联清单/填写结果；content_snapshot 为空且无文件 ID。不能只凭 ARCHIVED 和项目关闭就声称测试库割接实际内容链完整验收。此项是验收证据边界，不擅自修改状态或生成结果。
- 两端 CUTOVER_STANDARD_V1 v2 的字典、维度、节次、导航主配置快照摘要相同，先前差异仅为生效起始时间。子表计数相同不替代逐字段及全匹配组合验收。

## 采集和巡检配置：本次排除

关联五类项目的配置调试、业务联调、割接、巡检入口逐项解析归属后，源命中 35 条 cutover-checklist 采集请求，均 template_revision_id=NULL；清单项也未找到非空 command_template_id。不能把这些采集请求解释成使用了 3 条发布采集模板。请求存在不代表外部设备执行成功，结果来源计数单独记录。

因此 DEVICE-SHOW-5f6af2、QA-SUPERVISION64-SYSTEM、QA-SUPERVISION64-DIAGNOSTIC 以及巡检规则修订，不因“已发布”进入本次必迁清单。巡检任务在源库其他项目存在，但不属于五类模板关联项目；不迁移无关验收/运行记录。

## 满意度及培训：按实际引用决定

- 相关项目的问卷实例：源 COMPLETION_SATISFACTION_STANDARD 8 条、SAT_MANUAL_EXAMPLE 2 条；测试只有默认问卷 1 条。默认问卷已有且比较内容一致。
- SAT_MANUAL_EXAMPLE v1 在源项目 992203060193 下，一条采集任务 FAILED、一条 PENDING_COLLECTION；问卷实例状态均 ACTIVE。后者是有效在用证据，因此该问卷需保留在待补齐清单。问卷阈值、问题、匹配维度必须原样核对后按正式机制导入，不凭名称删除，也不复制业务实例。
- SAT_VISUAL_ACCEPTANCE_0921 未在该五类项目的问卷实例中找到引用，排除本次迁移。
- 相关培训实际引用 TRAINING_CUSTOMER_CONFIRMATION v2、TRAINING_FORM_20260920 v2；测试前者同修订存在，后者有内容摘要相同的 v1。应记录源 v2→测试 v1 的等价映射，不能因版本号不同再复制并覆盖已有版本。

## 当前迁移清单的处置

| 项目 | 处置 | 理由 |
|---|---|---|
| 最新五类正式项目模板 | 复用测试已有配置 | 已存在；源监督类 v3/测试 v2 需保留既有发布历史，版本编号不作为覆盖理由 |
| 11 个直接业务视图、3 个直接表单修订 | 复用 | 对应自然键/内容已确认 |
| 已使用培训确认与打印内容 | 复用并记录修订映射 | 对应内容已存在 |
| CUTOVER_STANDARD_V1 v2 | 复用 | 真实使用且主要配置内容已有 |
| SAT_MANUAL_EXAMPLE v1 | 确认的待补齐项 | 有待采集任务引用，测试缺失 |
| 启用但未见五类项目使用的 3 个示例动态表单、12 个旧视图 | 不因启用自动迁移 | 未建立本次范围依赖 |
| SAT_VISUAL_ACCEPTANCE_0921、3 个发布采集模板、无关巡检规则 | 排除本次迁移 | 未发现本次五类项目配置/验收引用 |
| 上轮 72 个字典项及其他全库差异 | 不作为必迁清单直接执行 | 仍须按具体表单/规则的依赖定位；未证明需要，不能整批复制，也不宣称每项已证明无用 |

本轮只完成范围判断，未执行迁移。除已确认待补齐问卷外，不能将其他启用数量差异继续当作迁移需求；也不能把未核实依赖标成已经完成验收。

## 证据

- `.run/template-full-audit-20261025/usage.py`、`usage-evidence.json`：当前数据库快照、项目/计划归属、模板节点引用、割接/巡检/采集/问卷/培训关系。
- `usage-scope-summary.json`：五类源关闭项目、两端直接依赖比较、真实割接内容计数。
- 源码归属依据：CutoverCollectionSources.owner（清单入口 objectId 是清单项，闭环入口 objectId 是闭环记录，不能直接与 taskId 比较）；ImplementationCollectionSources（配置调试/业务联调入口归属）。
- 本专项替代以“当前启用就全部迁移”为前提的迁移推断；早前全库盘点与有效配置数量事实保留。
