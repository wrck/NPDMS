# 割接模块实现收敛与旧模型处置

2026-10-14 用户批准完整清单，初始范围仅限割接模块、割接前端、菜单和接口合同。独立任务列表、重复实现合并、taskv2 / planv2 / Runtime 过渡命名清理、旧模型代码清除均在本次范围。旧模型历史数据按用户要求不再保留；实际数据处置先提交预览，服务重启另行确认。补充验收发现冻结文件下载缺陷后，用户另行批准仅扩展CUT与PLT历史版本访问契约，并批准对应部署重启。

## 代码实施结果

本次代码收敛、165条旧记录处置、残留查询及方案保护触发器修复均已完成，历次59191部署和环境恢复均按用户确认执行。D级完成P1、P2、P4、P5、P6及归档，P3按既定规则跳过。随后补齐的A、B、C真实浏览器流程均已完成P1～P6和归档，B包含驳回、修订、重新审批。验收发现的P3未保存答案丢失、P4修订文件上传冲突已在割接前端修复，111项前端测试、构建及实际页面回归通过；额外C级草稿保护回归任务也已归档。B旧审批V1下载失败随后按批准范围修复，部署后从原旧审批和新审批实际取得V1、V2，各自与原上传文件逐字节及文本一致，旧驳回历史和归档事实未变。本次A/B/C主流程、两处前端修复及历史文件下载回归通过；全仓类型错误及提示警告等验证边界仍按下文保留。

- 现行任务、清单、方案、审批、闭环、配置、统计及适配只保留一套实现；`taskv2`、`planv2`、`CutoverRuntime*`、`ProjectCutoverRuntime*` 和 `framework.runtime` 过渡命名退出生产代码及打包产物。
- 旧 `CutTask`、`CutPlan`、`CutRisk` 的实体、读写接口、状态机、Mapper/XML、核对迁移运行类、Job、旧页面及客户端已清除。割接治理和客户引用查询仅使用现行模型。
- `/pms/cutover/tasks` 提供独立任务列表，支持项目、阶段、状态、分类、分页及归档记录；工作台和列表复用任务查询及详情组件。详情和审批分别使用 `/pms/cutover/tasks/:taskId`、`/pms/cutover/approvals/:approvalId`。
- 审批队列、站内及外部通知按审批实例定位；角色审批资格独立于普通项目查看权限，审批专属列表行保留受限投影。既有 BusinessView 标识和冻结页面元数据保持原件，只更新割接当前组件映射。
- 割接资源使用物理前缀 `/api/v1/pms`，在割接模块内接入既有身份、租户、鉴权和访问日志实现。任务统计、配置修订、审批动作及闭环采集关联路径已同步机器合同。
- 配置写入、P2草稿保存、P3外部操作及P6执行关联补齐版本和幂等约束；HTTP响应使用明确VO。界面保留同一未确定意图的键、版本及载荷，刷新失败时暂停后续写入，并隔离任务或审批切换后的迟到响应。

现行配置、批准方案、审批、来源证据和审计的不可变约束保留。已应用Flyway原件未修改，P6职责变化回退的既有规格阻塞保持有效。

## 首轮数据处置及部署（已执行）

用户确认后，执行了[原数据处置SQL](2026-10-14-cutover-consolidation-preview.sql)。环境为`npdms-domain-test-mysql-1 / 127.0.0.1:24306 / npdms_domain_test`。执行前复核Compose项目标签、映射端口、行数、字段、约束、菜单及调度快照，与获批预览一致；确认59191运行进程及冻结产物身份后停机执行，再通过正式入口启动。原预览文件保留批准时的内容，本记录补充实际结果。

| 退役表 | 实际删除行数 | 结果 |
|---|---:|---|
| pms_cut_task_retired | 36 | 表及原记录已删除 |
| pms_cut_plan_retired | 30 | 表及原记录已删除 |
| pms_cut_risk_retired | 30 | 表及原记录已删除 |
| pms_cut_execution_retired | 35 | 表及原记录已删除 |
| pms_cut_observation_retired | 34 | 表及原记录已删除 |

共删除165条旧记录、5张退役表及其15个只读触发器。现行任务和方案上的15个空`legacy_*`列、2个旧唯一索引已移除，9项CHECK已按预览替换。处置后保留原有5条现行任务和3条现行方案，现行配置、批准版本、来源证据及审计没有被删除或改写。详见[执行后核验](../../.run/build/cutover-consolidation-db-applied.json)及[SQL执行日志](../../.run/build/cutover-consolidation-db-apply.log)。

菜单已按批准结果调整：`992602050001`改为独立任务列表并保留15个按钮及授权；配置中心接收原配置菜单的4个按钮；15个旧菜单/按钮及14条旧授权关系逻辑下线。新登录浏览器中已显示工作台、配置中心、任务列表，旧菜单退出。

调度配置已收敛为正式handler：2条旧模型核对任务及1条重复通知任务下线；审批通知和结果投递任务在启动后均有实际Quartz调度，查询状态为`WAITING`；外部提醒保持原暂停状态。任务日志和审计保留。详见[运行核验](../../.run/build/cutover-consolidation-runtime-check.json)。

首轮运行包为[67c78c5f冻结清单](../../.run/build/releases/67c78c5f-0a7d-404c-af04-d5d8c0a35efe/manifest.json)，由`scripts/worktree-backend.ps1`以`local,dac`启动，PID为`78804`；[部署日志](../../.run/build/cutover-consolidation-backend-deploy.log)记录启动及健康检查成功。本次没有操作48182。已应用Flyway原件及历史表未改写；本次获批SQL没有加入自动执行的Flyway路径。

## 真实浏览器结果及发现的遗漏

实际使用`19191`前端、`59191`后端登录并操作页面。内置浏览器导航故障后，使用独立Chromium执行真实UI操作；没有用API注入登录状态或业务结果。证据位于[浏览器记录目录](../../.run/build/cutover-consolidation-browser/)。

- 独立列表可展示归档记录，状态、阶段及项目筛选和重置有效；每页条数切换请求有效。当前记录不足一页，尚未验收跨页翻页。
- 详情直接刷新、浏览器前进后退、不同任务间切换有效；已归档A/D任务的审批历史、冻结清单和方案可读取，A类归档的稳定结果标识及2台设备结果仍在。
- 工作台当前待办、归档等计数与所见列表对应；配置中心3条既有配置（1条草稿、2条已发布）可读取。本轮没有发布配置。
- 首轮通过P1页面创建任务`2110342206055227392`（`割接收敛验收-20261014-D-独立流程`），进入P2。点击保存时服务端返回系统异常，重开后无评估草稿保存。当时保留该任务供修复部署后继续验收；共享库因此从5条现行任务增加到6条，方案当时仍为3条。

P2异常来自`CutoverTaskMapper.selectForUpdate`仍选择6个已删除的旧模型字段；同类审查发现`CutoverCompletionMapper.one`也有相同遗漏。已只删除这两处SELECT中的旧列，保留租户、项目、主键、未删除条件及锁语义。直接以实际Mapper SQL验证，修复前5个场景均报未知字段，修复后正确范围可查、错误租户/项目返回空；验证事务只读并回滚，没有业务写入。

复制当前DDL到隔离数据库时，还发现`trg_cut_structured_43_u`引用已删除的9个方案旧字段。对现有167个CUT触发器的`NEW`/`OLD`列引用核查后，仅此触发器存在失效引用。这是本次清理遗漏，首轮构建和名称扫描未覆盖到实际SQL及触发器列依赖。

## 触发器补丁及第二次部署（已执行）

[触发器修复SQL](2026-10-14-cutover-consolidation-trigger-fix-preview.sql)只重建`cut_plan_revision`上的`trg_cut_structured_43_u`：移除9个已删除字段的比较及已不可达的`LEGACY_FORWARD`条件，保留现行48处不可变字段比较、原JSON保护、冻结状态判断及拒绝信号。SQL不更新业务行、文件、审计或Flyway历史。

该SQL先在专用隔离库`npdms_cut_consolidation_20261014_r2`原样执行（仅替换目标库名）。执行后触发器正文与预览一致，8条隔离方案记录逐字节未变；四类流程均验证冻结方案修改返回`CUT_IMMUTABLE_HISTORY`且原值不变。详见[隔离补丁验证结果](../../.run/build/cutover-consolidation-trigger-preview-validation.json)。

用户再次确认后，执行前比较当前触发器正文与批准预览一致，再停止已核验的59191进程并执行原SQL。执行后共享库3条原方案记录逐字节不变、其他触发器定义不变、失效的CUT触发器列引用为0，48处现行不可变比较保留。详见[实际执行结果](../../.run/build/cutover-consolidation-trigger-fix-applied.json)。随后正式入口以`local,dac`启动[16985496冻结包](../../.run/build/releases/16985496-114f-4df8-820d-386ab2d5b210/manifest.json)，该次PID为`79184`；[部署日志](../../.run/build/cutover-consolidation-correction-backend-deploy.log)记录健康检查通过。

第二次部署后的真实页面操作：

- 同一任务P2保存成功，直接重开后问卷和D级仍在；提交后按D级规则跳过P3进入P4。
- 创建第1版简易方案，保存操作及回退步骤，生成并绑定DOCX，再提交进入P5。新方案为`2110354313135955968`，共享库现为6条任务、4条方案。
- 发起人节点及服务经理节点均通过五项评审；服务经理还完成等级复核。第二个节点在审批实例`2110354764992520192`的独立页面办理，重开后两条时间、反馈与冻结方案均在，已无审批写入口。
- P6填写测试事实后尝试缺附件提交，页面仅保存草稿并要求补齐两项附件，未归档。当时闭环为`2110355750364553216`、版本0，任务为`CLOSURE_IN_PROGRESS`。草稿保存按契约不保存最终成功/失败选择，重开需重新选择，这属于预期行为。
- 按用户要求，后续浏览器执行已交给`gpt-6.1-sol / low`。独立只读验收确认上述P6和审批状态；既有C级P3页面完整显示43项，但多项事实和材料未填，本轮未补造核查结果或推进该任务。控制台存在Vue既有警告，没有页面异常。

对应截图为`13-p2-saved-after-correction.png`、`14-d-plan-saved-generated.png`、`15-approval-instance-service-manager.png`及`readonly-*.png`。第二次部署至P6的请求记录保存在[浏览器请求日志](../../.run/build/cutover-consolidation-browser/correction-to-p6-network.json)。当时两份本轮PDF测试样本仅完成生成与检查，实际上传在第三次部署后完成。

## P6遗留项回显修复（已部署并完成浏览器验收）

实际P6保存后，`legacy_items`原文已进入数据库，但`CutoverClosureMapper.xml`的`autoMapping=false`结果映射缺少`legacy_items → legacyItems`，导致重开详情显示为空，继续保存可能覆盖原文。这里的`legacy_items`是现行“割接遗留项”，不属于待删除的旧模型字段。发现后暂停该任务写入、恢复原映射行，待修复部署并确认原文回显后才继续办理，原始内容没有丢失。

回归使用非空遗留项贯穿创建草稿、Mapper读取、以读取值再次保存及归档后的读取：修复前A/B/C/D四项都在读取断言处失败（实际为null），修复后四项全部通过。不是只检查XML文字。另核查49条使用显式结果映射的查询列，修复后未发现同类未映射列。该补丁不需要数据库DDL或业务数据修复。

## 验证记录

| 验证 | 实际结果及证据 |
|---|---|
| 后端割接测试 | 407项：381通过、0失败、0错误、26项MySQL条件测试跳过；[测试日志](../../.run/build/cutover-consolidation-test-19.log) |
| 前端割接测试 | 最新20个文件、111项全部通过，含P1～P6组件流程、列表、详情刷新/切换、审批实例、Snowflake ID、幂等重试及刷新屏障，以及本轮P3草稿保留/P4文件修订回归；[最新测试日志](../../.run/build/cutover-abc-browser/ui-suite-after.log) |
| 首轮后端构建及部署 | 官方脚本、固定JDK/Maven及独占锁；42模块构建成功，22项既有修复基线通过；按批准部署；[构建日志](../../.run/build/cutover-consolidation-backend-build.log) |
| 残留列查询修复 | 5个实际SQL场景通过，包含错误租户/项目返回空；[修复前](../../.run/build/cutover-consolidation-sql-red.json)、[修复后](../../.run/build/cutover-consolidation-sql-green.json) |
| 受影响服务及Mapper测试 | 11项全部通过；[测试日志](../../.run/build/cutover-consolidation-sql-fix-tests.log) |
| 隔离MySQL全流程 | A/B/C/D 4项全部通过，覆盖P1～P6、成功/失败归档、角色范围、无权节点拒绝及冻结方案保护；最新补充遗留项保存、读取、再次保存及归档保留值；[修复前失败](../../.run/build/cutover-consolidation-closure-mapping-red.log)、[修复后通过](../../.run/build/cutover-consolidation-closure-mapping-green.log)。使用真实MySQL和受控Owner事实，不能替代真实浏览器 |
| 角色测试夹具相关回归 | 19项全部通过；只修正测试夹具中无效角色别名，没有放宽生产鉴权；[测试日志](../../.run/build/cutover-consolidation-role-fixture-tests.log) |
| 触发器SQL | 隔离库8条方案及获批执行时共享库3条方案前后完全一致；其他触发器定义保留；[共享库结果](../../.run/build/cutover-consolidation-trigger-fix-applied.json) |
| 第二次部署完整构建 | 官方脚本42模块构建成功，生产及测试源码编译通过，22项既有修复基线通过，打包时不重复执行测试；[构建日志](../../.run/build/cutover-consolidation-correction-build.log) |
| 第二次发布产物校验 | 构建前后源码清单一致；正式部署入口校验通过，按批准部署；冻结JAR中的两处Mapper已无旧列且保留锁查询；[产物检查](../../.run/build/cutover-consolidation-correction-jar-check.json) |
| 结果映射核查 | 49条显式结果映射查询，修复后未发现同类列遗漏；[核查结果](../../.run/build/cutover-consolidation-result-mapping-audit-green.json) |
| 遗留项修复包 | 官方脚本42模块构建成功，22项既有修复基线通过，构建前后源码清单一致；[构建日志](../../.run/build/cutover-consolidation-closure-mapping-build.log) |
| 遗留项修复产物校验 | [934c1cc8冻结清单](../../.run/build/releases/934c1cc8-f627-4a2d-89d4-e81442dcccad/manifest.json)通过正式入口`-ValidateOnly`，服务未变；冻结JAR的遗留项映射与源码一致，两处查询修复及任务锁保留；[部署前校验](../../.run/build/cutover-consolidation-closure-mapping-release-validation.log)、[产物检查](../../.run/build/cutover-consolidation-closure-mapping-jar-check.json) |
| 前端正式构建 | 本轮按`build:local`的图标检查及Vite `env.local`构建成功，独立输出到`.run/build/cutover-abc-ui-dist`；[最新构建日志](../../.run/build/cutover-abc-browser/ui-build-after.log) |
| 全仓前端类型检查 | 最新检查未通过，共40项错误，割接`views`和`api`目录无报错；其他模块错误不在本次修复范围，未扩展修改；[最新日志](../../.run/build/cutover-abc-browser/typecheck-after.log) |
| 旧实现残留 | 首轮冻结JAR内6个正式业务Controller均存在，旧类匹配数为0；浏览器暴露的两处残留旧列已修复，不以名称扫描替代实际SQL验收 |
| 共享库及浏览器 | 两份获批SQL、历次部署及获批环境恢复已执行；A/B/C/D均实际归档，B含驳回修订及重新审批；P3草稿保护另用C级任务完成回归并归档。历史版本修复部署后，B旧V1与新V2均实际下载且内容核对通过，详见文末 |
| 历史文件访问修复 | 7个测试类共60项定向测试、24项真实MySQL只读核验、42模块正式构建通过；获批部署7503e3f7后，浏览器下载V1/V2成功，B的21项归档检查通过，16组前后核验字段一致；[文件内容核验](../../.run/build/cutover-historical-file/download-content-check.json)、[历史不变核验](../../.run/build/cutover-historical-file/history-unchanged-check.json) |
| 归档结果只读核验 | 任务`ARCHIVED`/版本5，闭环`SUBMITTED`/`SUCCESS`/版本2；原始遗留项、两份V1附件、两台冻结设备及已批准实例保留，稳定结果标识与版本一致；[核验结果](../../.run/build/cutover-consolidation-final-archive-check.json) |

首轮测试的26项MySQL条件测试为当时跳过的结果，后续单独执行的4项全流程不等于26项全部补跑。隔离库只导入当前结构并创建专用测试事实，未复制共享库业务记录；失败尝试的独立测试库没有擅自清理。

59191当前运行`7503e3f7`产物，已包含遗留项读取映射和历史版本访问修复，PID为`23388`、固定`local,dac`。此前补充A/B/C验收期间曾按批准恢复`934c1cc8`，该恢复实例PID为`25028`；其后历史版本补丁的部署另获明确批准。

验证边界：授权/租户保护、状态与版本冲突、当前资格撤销后拒绝重放、文件/采集证据保护等失败测试保留并通过；未做大规模待办统计性能验收，现行统计仍复用各阶段动作资格查询。工作区其他模块的并行改动保留，本轮未提交或推送。

## 第三次部署及后续验收

用户明确“允许”后，使用包含P6遗留项读取映射修复的[934c1cc8冻结包](../../.run/build/releases/934c1cc8-f627-4a2d-89d4-e81442dcccad/manifest.json)，通过`scripts/worktree-backend.ps1`对59191执行第三次部署，固定`local,dac`。22项既有修复基线通过，运行进程和冻结清单校验通过，健康检查通过，当次PID为`75176`。详见[部署日志](../../.run/build/cutover-consolidation-closure-mapping-backend-deploy.log)。此补丁只恢复读取映射，没有新增数据库处置，也没有操作48182。

重启后只读检查确认两个正式割接调度任务均为`WAITING`，旧任务保持下线。6.1 sol轻度模型使用既有登录会话完成以下真实页面操作，随后关闭浏览器：

- P6原始遗留项完整回显；通过页面上传两份明确标注`TESTFIXTURE`的PDF，保存并整页刷新后，两附件V1、三项检查、无回退选择及遗留项原文均保留。
- 重新选择成功后提交，任务于2026-10-14 21:20:25归档，稳定结果为`CUTOVER_CLOSURE:2110355750364553216:2`。页面显示项目设备覆盖2/2，两台设备均已记录割接服务事实。
- 从独立列表重开、直接地址刷新、按已归档筛选后再次重开均成功；筛选返回新D、旧A、旧D共3项。归档详情只读，遗留项、两附件及稳定结果不变。
- 第1版D级方案、两个审批节点及原反馈保持只读；两附件版本历史均为V1可用。页面明确显示附件未执行安全扫描，本次没有将其报告为扫描通过。

新任务为`2110342206055227392`；两台冻结设备SN为`NATIVE-UI-20261012-DEVICE-02`及`NATIVE-UI-20261012-DEVICE-03`。这是平台流程验收，任务原文和附件均明确没有实际设备操作、客户签字或真实客户承诺。独立数据库只读核验确认任务版本5、闭环版本2、原始遗留项及上述关联一致，没有直接写入业务表。

证据包括[成功归档截图](../../.run/build/cutover-consolidation-browser/third-deploy-D-P6-archived-success.png)、[刷新后历史](../../.run/build/cutover-consolidation-browser/third-deploy-direct-refresh-P6-history.png)、[归档筛选](../../.run/build/cutover-consolidation-browser/third-deploy-archived-filter-three-tasks.png)以及[本轮浏览器请求记录](../../.run/build/cutover-consolidation-browser/third-deploy-network.json)。该会话33条割接请求没有HTTP失败，最终提交响应业务码为0，成功归档另由页面及数据库状态核实；少数响应未捕获业务码，没有仅凭HTTP 200判定完成。

[跨重启会话控制台记录](../../.run/build/cutover-consolidation-browser/third-deploy-console.json)包含连接拒绝、Network Error及Vue警告，日志没有时间戳，不能确认连接错误发生的具体时点，也不宣称控制台全程无错。上述归档及重开操作实际成功，没有发现新的业务阻断。C级任务的缺失事实未修改；本轮标准流程P3写入和不足一页情况下的跨页翻页未验收，既有类型检查及条件测试边界见上表。

## A/B/C补充真实浏览器验收

用户明确要求“验收A/B/C级流程，不能单测D类”，并允许多个浏览器任务并行。按此前模型约束，实际浏览器操作均由`gpt-6.1-sol / low`执行，使用19191前端、59191后端及原测试库。新建任务、材料、评审反馈及遗留项均注明`TESTFIXTURE`，只验证平台业务流程，没有执行真实设备命令或声称取得真实客户签字。以下结果包含获批恢复环境后的实际续办和归档。

| 级别及任务 | 已完成的真实业务路径 | 归档结果 |
|---|---|---|
| A：`2110368582657314816` | P1/P2保存重开；P3完成43项（34项DIRECT、9项MANUAL）；P4上传专用完整方案V1；P5发起人、服务经理、二线、研发四节点各完成五项评审；P6两附件及两设备结果 | `ARCHIVED`，稳定结果`CUTOVER_CLOSURE:2110371126062288896:2`；重开后冻结方案、清单、评审、遗留项及设备回执均保留 |
| B：`2110368707416887296` | 独立项目及单SN；P3完成17项（6项DIRECT、11项MANUAL）；P4完整方案V1进入P5并被驳回；创建第2修订、上传同一工件V2，保存重开后重新提交；发起人、服务经理、二线三节点批准；P6两附件及单设备结果 | `ARCHIVED`，稳定结果`CUTOVER_CLOSURE:2110389555766960128:2`；旧第1修订、V1及驳回理由保留；历史访问修复后V1、V2实际下载及内容核验均通过 |
| C：`2110364217699667968` | P1/P2保存重开；P3完成43项（34项DIRECT、9项MANUAL）；P4在线标准方案，覆盖六章节、四类保障人员、拓扑和组网附件并生成DOCX；P5两节点各完成五项评审；P6两附件及两设备结果 | `ARCHIVED`，稳定结果`CUTOVER_CLOSURE:2110368235196977152:2`；直接地址重开、归档筛选及冻结历史均通过 |
| C草稿回归：`2110372306423320576` | 未保存四处DIRECT答案后上传人工材料，保存请求先于材料绑定且整页重开后四值保留；完成17项（8项DIRECT、9项MANUAL）、专用完整方案V1、两节点审批及P6 | `ARCHIVED`，稳定结果`CUTOVER_CLOSURE:2110389997053878272:2`；本任务仅一台设备，项目累计覆盖2/2不等于本任务处理两台 |

A级四节点由三个实际账号办理：`admin`办理发起人和服务经理，`c02407`办理二线，`b00106`办理研发。普通项目详情访问资格与角色审批资格分开验证：二线账号无项目详情权限时得到403，仍可在其有权的独立审批页面办理；admin没有后续二线写入口，二线办理完成后没有研发写入口。没有修改账号角色或绕过权限。A级提前期展示为要求2个自然日、实际7日、未超期，与只读事实一致。

关键证据：[A级四节点历史](../../.run/build/cutover-consolidation-browser/abc-A-four-actors-approved-history.png)、[A级两设备回执](../../.run/build/cutover-consolidation-browser/abc-A-archive-device-receipts-visible.png)、[C级标准方案重开](../../.run/build/cutover-consolidation-browser/abc-C-standard-plan-saved-reopened.png)、[C级两设备回执](../../.run/build/cutover-consolidation-browser/abc-C-archive-device-receipts-visible.png)、[A/C归档后的列表](../../.run/build/cutover-consolidation-browser/abc-archive-filter-actual-list.png)。数据库仅作SELECT核验：[A的18项](../../.run/build/cutover-abc-browser/A-archive-check.json)、[B的21项](../../.run/build/cutover-abc-browser/B-archive-check.json)、[C的15项](../../.run/build/cutover-abc-browser/C-archive-check.json)、[C草稿回归的15项](../../.run/build/cutover-abc-browser/C-draft-guard-archive-check.json)均通过，覆盖任务、清单、审批节点、附件、闭环版本及资产侧稳定结果引用；这些数据一致性检查不包含文件内容下载，不能覆盖后述V1下载失败。

失败路径也已实际操作：活动割接占用的设备不能再次建单；C级缺必填清单项提交返回409、仍留P3；不完整标准方案提交被422拒绝；B级将评审项选为“不合理”但未填该项原因时，页面阻止提交，补齐原因后才形成真实驳回。缺必填项的409在界面上仅显示通用错误文字，这是仍保留的提示体验问题，业务拦截有效，本轮未扩大修改公共HTTP拦截器。请求记录还包含角色切换期间的一次会话401及预期403，不宣称全程无错误响应。

### 验收暴露的两处前端问题

1. **P3人工材料上传清空其他未保存答案。** 原逻辑只在EXTERNAL项人工兜底时保存DIRECT答案；普通MANUAL/COLLECTION材料绑定后刷新整张清单，覆盖未保存输入。新C回归任务`2110372306423320576`独立复现：仅上传一项人工材料，另外四处未保存答案全部消失，且请求中没有清单PUT保存。[上传前](../../.run/build/cutover-consolidation-browser/abc-draft-guard-before-manual-upload.png)、[上传后](../../.run/build/cutover-consolidation-browser/abc-draft-guard-unfixed-after-manual-upload.png)、[请求和DOM记录](../../.run/build/cutover-abc-browser/draft-guard-unfixed-diagnostics.json)。修复后所有人工材料绑定均先通过既有草稿保存，再使用刷新后的清单版本绑定材料；该过程暂停输入，保存未确认成功时不继续绑定。
2. **P4驳回修订无法替换完整方案文件。** 原上传入口未传已有`artifactId`及`expectedReferenceVersion`，同一业务引用再次执行创建，触发“文件业务引用版本已变化”。[B级驳回](../../.run/build/cutover-abc-browser/B/06-B-P5-rejected.png)、[修订上传冲突](../../.run/build/cutover-abc-browser/B/07-B-P4-revision-upload-conflict.png)。修复在割接组件内携带已有文件身份、引用键和版本，向原文件追加版本；在线方案的三个材料槽及建草稿前再次换文件也采用相同参数。Snowflake ID原样透传，旧方案冻结文件事实不改写。共享上传器现有ID类型声明较窄，割接调用处仅作类型断言，没有数值转换或修改共享模块。

修改仅涉及割接P3/P4组件及对应测试，不改后端、数据库、鉴权或状态机。新增回归在原实现上出现6项失败，修复后两个组件测试文件26项全部通过，割接前端全套20个文件111项通过，正式前端构建通过：[修复前失败](../../.run/build/cutover-abc-browser/ui-regression-before.log)、[定向修复后](../../.run/build/cutover-abc-browser/ui-regression-after.log)、[全套回归](../../.run/build/cutover-abc-browser/ui-suite-after.log)、[构建](../../.run/build/cutover-abc-browser/ui-build-after.log)。实际页面回归另见下文。共享上传器的`artifactId`运行时仍声明Number，透传字符串ID会出现Vue类型警告；本轮实际上传、追加版本和保存成功，未作Number转换，但不宣称控制台无警告。

### 环境中断、获批恢复与续办位置

修复后重开浏览器时，19191返回连接拒绝，原Chromium会话已不存在；只读检查确认19191、59191、24306无法连接，原后端PID不存在，Docker Desktop Linux引擎管道不存在。没有据此推断具体停机原因，确认前未操作服务。原`934c1cc8`冻结包通过正式入口`-StartIfOffline -ValidateOnly`校验：[离线校验日志](../../.run/build/cutover-abc-browser/backend-offline-validation.log)。按`.run/AGENTS.md`每次重启须明确批准的约束，请求恢复原验收环境。

用户明确答复“允许恢复并继续验收”后启动Docker Desktop。原`npdms-domain-test`下MySQL、Redis容器恢复健康，仍使用原命名数据卷及24306/24379端口，没有重建容器、卷或执行数据迁移。通过`scripts/worktree-backend.ps1 -StartIfOffline -ApprovedRestart`启动同一`934c1cc8`冻结后端，固定`local,dac`，PID为`25028`、健康状态`UP`；通过`scripts/worktree-frontend.ps1`恢复19191前端，PID为`14192`。详见[后端恢复日志](../../.run/build/cutover-abc-browser/backend-recovery.log)、[前端恢复日志](../../.run/build/cutover-abc-browser/frontend-recovery.log)。恢复后A/C归档只读核验仍全部通过；48182保持未启动，实际业务验收继续由浏览器完成。

### 恢复后的修复回归与归档结果

B在原第2修订上传专用REV2材料成功，同一工件`2110371011125522433`产生V2；保存并重开后仍为V2，原第1修订冻结事实仍指向V1。新审批实例`2110388983382876160`由admin办理发起人及服务经理，`c02407`正常登录后在独立审批页完成二线评审。该账号普通任务详情仍返回403，未修改权限。P6保存、整页重开、提交、独立列表筛选及归档重开均成功，单SN为`IT-DEFAULT-20261012-DEVICE-001`。两份P6附件均通过实际下载取得文件。证据：[V2保存重开](../../.run/build/cutover-abc-browser/B/09-B-P4-V2-saved-reopened.png)、[版本历史](../../.run/build/cutover-abc-browser/B/10-B-P4-V1-V2-history.png)、[二线批准](../../.run/build/cutover-abc-browser/B/15-B-c02407-approved.png)、[归档](../../.run/build/cutover-abc-browser/B/17-B-P6-archived.png)、[归档后重开](../../.run/build/cutover-abc-browser/B/19-B-history-reopened.png)、[请求记录](../../.run/build/cutover-abc-browser/B/network.json)。

C草稿保护回归仍使用原任务：重新填入两个“否”和两个`TESTFIXTURE-DRAFT-GUARD`文本值，未点草稿保存即上传另一项人工材料。浏览器请求显示清单PUT成功后才发出人工材料POST，两者业务码均为0；上传后及整页重开后四值均保留，最终冻结清单也保留原值。随后通过页面补齐17项、提交专用完整方案、办理两个审批节点、上传两份P6材料并归档，本任务单SN为`NATIVE-UI-20261012-DEVICE-02`。证据：[请求顺序](../../.run/build/cutover-abc-browser/draft-guard-fixed-diagnostics.json)、[四值实际可见](../../.run/build/cutover-consolidation-browser/abc-draft-guard-background-values-visible.png)、[冻结后四值](../../.run/build/cutover-consolidation-browser/abc-draft-guard-four-values-visible-frozen.png)、[两节点历史](../../.run/build/cutover-consolidation-browser/abc-draft-guard-two-approved-history.png)、[单设备归档回执](../../.run/build/cutover-consolidation-browser/abc-draft-guard-archive-single-device-visible.png)。

最终只读运行核验显示现行任务共10条、其中7条归档，本轮新建A/B/C及C回归任务均已结束；两个正式割接调度任务仍为`WAITING`，既有旧调度保持下线。详情见[最终运行核验](../../.run/build/cutover-consolidation-runtime-check.json)。本轮没有直接更新业务表，也没有提交或推送代码；48182未启动。

### 修复前失败证据：旧审批方案V1无法下载

B归档后重开旧驳回审批`2110371117422022656`，页面仍显示第1修订、文件V1、原“不合理”评审及原因，数据库冻结事实也保持不变。但实际点击V1下载时，`POST /admin-api/api/v1/pms/files/2110371011125522433/access-tickets`返回HTTP 200、业务码`1010001018`（文件业务引用不存在），浏览器未获得文件。因此旧V1元数据可见不等于旧文件可下载。[旧审批冻结内容](../../.run/build/cutover-abc-browser/B/22-B-old-rejected-V1-frozen.png)、[下载失败截图](../../.run/build/cutover-abc-browser/B/23-B-old-V1-download-no-reference.png)、[请求记录末尾](../../.run/build/cutover-abc-browser/B/network.json)保留该失败证据。

修复前代码定位：文件平台的[FileArtifactApiImpl.requireReference](../../pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileArtifactApiImpl.java)同时要求请求工件和版本与当前ACTIVE引用相同；[FileAccessTicketService](../../pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileAccessTicketService.java)在签发下载凭据前调用该检查并加锁复核。割接修订沿用同一引用并追加V2后，旧审批请求的V1不再等于引用当前版本，因而被拒绝。原业务授权查询不携带所请求的工件/版本，追加引用接口也不支持CUT目标，无法仅在割接页面用原有授权接口补齐历史版本访问。将下载参数改为V2会取错历史内容，放宽所有版本相等校验则缺少对历史版本的逐项授权，均不是正确修复。

据此提出仅扩展这一处CUT与PLT文件访问契约：由CUT根据当前用户权限及冻结方案/审批事实确认准确工件、版本和业务引用，PLT据此办理该历史版本的预览/下载，并保留租户、权限、锁内复核、文件可用性及审计检查；写入和提交消费仍要求当前精确引用。用户随后明确答复“允许扩展并修复历史版本访问”。该授权覆盖以下契约和实现修复，服务重启仍须另行确认。

### 历史版本访问修复（已部署并完成实际下载验收）

[文件设计13分册](../design/13-file-design.md)已补充批准的契约。PLT新增默认拒绝的精确冻结版本策略入口；只有`DOWNLOAD/PREVIEW`在当前ACTIVE引用仍属于同一工件时才可请求该授权。`READ`参与业务提交消费，仍与写入、替换、挂接一样要求当前精确版本。原Access Ticket继续执行两次权限检查、锁内事实水位重验、文件可用性检查和审计，凭据仍绑定请求的旧版本，不替换为新版本。

CUT复用现有项目范围及角色审批资格：普通项目访问通过关系表确认同租户、同任务已提交或已失效方案中的完整文件或固定槽位文件；角色审批访问额外匹配当前有权审批实例冻结的工件及版本，不扩展到该任务其他历史。未冻结的草稿、错误任务/租户/引用、已删除记录及资格撤销均拒绝。没有修改批准/驳回快照或直接补写业务表，也不需要数据库迁移。

定向测试覆盖7个测试类、共60项：首轮59项通过，随后默认拒绝错误码和锁内正向路径补充后，重跑受影响两类14项全部通过，其余结果仍适用。[首轮日志](../../.run/build/cutover-historical-file/targeted-tests.log)、[补充回归](../../.run/build/cutover-historical-file/proof-tests-after.log)。新增生产XML查询在指定MySQL完成24项只读核验，包含不持久化的CTE场景及真实B的V1/V2、C标准方案三个槽位：[SQL核验](../../.run/build/cutover-historical-file/frozen-file-sql-check.json)。SQL核验脚本首次解析多行列名失败，补充固定结果列别名后24项全部通过；没有据此修改生产查询规则。

正式入口已完成42模块构建，生产及测试源码编译通过，22项既有修复校验通过；生成[7503e3f7冻结清单](../../.run/build/releases/7503e3f7-3fc6-4943-9e3a-4016b5fbc3ae/manifest.json)，并通过正式部署入口`-ValidateOnly`。[构建日志](../../.run/build/cutover-historical-file/backend-build.log)、[部署前校验](../../.run/build/cutover-historical-file/release-validation.log)。用户随后明确答复“允许部署并重启59191”，通过`scripts/worktree-backend.ps1 -ApprovedRestart`部署该冻结产物，固定`local,dac`，健康检查通过，PID为`23388`。[部署日志](../../.run/build/cutover-historical-file/backend-deploy.log)。本次无数据库迁移，没有启动48182。

部署后，`gpt-6.1-sol / low`在新隔离浏览器中正常登录admin，从原B旧驳回审批`2110371117422022656`点击冻结完整方案V1下载，取得24,453字节PDF；从新已批准审批`2110388983382876160`点击V2下载，取得24,740字节PDF。两个下载凭据响应业务码均为0，且分别收到实际文件内容。下载后独立核对确认两份文件各自与原上传样本逐字节及PDF文本一致，两版本内容不同，只有V2包含第2修订及补充回退步骤，未将旧V1替换为当前V2。[V1页面DOM](../../.run/build/cutover-historical-file/browser/01-old-V1-dom.txt)、[V2页面DOM](../../.run/build/cutover-historical-file/browser/02-new-V2-dom.txt)、[原V1下载文件](../../.run/build/cutover-historical-file/browser/downloads/B-frozen-V1.pdf)、[原V2下载文件](../../.run/build/cutover-historical-file/browser/downloads/B-current-V2.pdf)、[文件内容核验](../../.run/build/cutover-historical-file/download-content-check.json)。

随后从原B任务地址重开归档详情，仍为成功、只读，稳定结果`CUTOVER_CLOSURE:2110389555766960128:2`及唯一SN回执保留。[归档页面DOM](../../.run/build/cutover-historical-file/browser/03-archived-unchanged-dom.txt)、[归档截图](../../.run/build/cutover-historical-file/browser/03-B-archived-unchanged.png)。数据库仅作SELECT核验，B的21项归档检查全部通过；与修复前快照比较的16组核验字段（含业务快照与检查结果）完全一致，涵盖任务、清单、方案、审批、评审、闭环、附件、设备及资产结果。[本次归档核验](../../.run/build/cutover-historical-file/B-archive-check.json)、[前后事实比较](../../.run/build/cutover-historical-file/history-unchanged-check.json)。旧失败请求和截图保留在原目录，新证据使用独立目录。

本次浏览器未执行任务业务写入，验收结束已关闭隔离浏览器。[请求日志](../../.run/build/cutover-historical-file/browser/network.json)记录的业务响应均成功；[控制台](../../.run/build/cutover-historical-file/browser/console.json)仍有路由`next()`弃用及组件属性继承警告，没有将其表述为零警告。本次实际浏览器范围为旧V1、新V2下载及归档重开；预览、其他固定材料槽历史访问和资格撤销由定向测试及SQL核验覆盖，未宣称这些场景均已逐项完成浏览器操作。未提交或推送代码。

2026-10-15提交补记：用户另行授权将本次割接收敛及历史版本访问修复提交到本地，排除其他并行改动，不推送。提交范围包含上述代码、接口合同、两份获批SQL及本报告；工程实施、项目页面和其他任务文档等并行改动保留。提交前仅清理新割接清单组件的17处行尾空格，非空白内容未变，沿用上述已完成的构建、测试及真实浏览器验收结果。
