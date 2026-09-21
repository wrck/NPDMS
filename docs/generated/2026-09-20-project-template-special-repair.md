# 项目模板专项修复与闭环验收

本记录承接 [专项审查](2026-09-20-project-template-special-review.md)。需求方随后明确授权业务代码修复，本次不进入工程实施链，不改写审查阶段或历史项目的验收结论。

## 最终业务闭环结果（2026-09-21）

专用项目 `PJT2026000005`（`992203060007`）已于 **00:31:11 正常闭环，NORMAL_CLOSED / version 11**。复用模板 `FPROJ009_E2E_S1S6_20260920` 已发布 v2 `993009001599`，仅含 S1～S6。项目保留创建时发布 v1 的冻结引用，并以独立生效计划 v2 `2101671740756459522` 完成满意度成果来源调整，未改写公共模板历史。

[主代理最终独立核验](../../.run/template-special-20260920/review/final-closure-independent-check.json)通过：6 个阶段和 14 项任务均 DONE，20 个执行轮次均保留完成结果，11 个门禁 PASSED，20 项必交付件全部 ACCEPTED 且关联真实来源。初验和终验分别有当前 EFFECTIVE / PASS 报告，独立满意度有评分、签字及结果文档；共 21 条交付件提交包含保留的旧 FAIL 来源提交。9 项可选交付件未提交不影响本模板判定。

正常闭环检查快照 `2101705414776156162` 的 72 项条件全部通过。实际申请 `2101705632108212225` 状态 APPROVED，BPM `d5e8c4c4-b50d-11f1-adbb-00ff0b78abd7` 留存 serviceManagerReview、materialReview 两个实际批准节点；退出记录 `2101710774958198786` 与申请及终态阶段结果一致。第二节点首次因业务身份不一致被拒绝，修复后通过原待办完成，没有重建申请或补写审批结果。

[最终历史核对](../../.run/template-special-20260920/review/final-history-independent-check.json)确认：计划升级前的 16 条 DONE 执行完整记录逐字段不变；参考项目 `992203060006` 的项目、阶段、任务、门禁、交付件、结果与退出事实均保留。公开模板 v1 不变且 v2 只有已批准的 D3 两项来源调整，复用[发布版本独立核对](../../.run/template-special-20260920/review/public-v2-final-independent-check.json)结果。

[关闭后的实际页面](../../.run/template-special-20260920/repair/execution/closed-project-s6-history.png)显示正常闭环、14/14 任务完成，S6 与前序阶段均完成；[交付件历史](../../.run/template-special-20260920/repair/execution/closed-project-file-readonly.png)保留版本、预览和下载，已无材料提交及重新检测入口。01:01，gpt-5.6-sol 通过页面下载并[实际预览](../../.run/template-special-20260920/repair/execution/closed-project-s6-d0-preview.png)同一 S6 D0 原文件 `2101700708188270594` v1，两次授权均 code 0、PDF 响应 HTTP 200。[主代理独立检查](../../.run/template-special-20260920/review/final-file-independent-check.json)确认下载内容为 24,199 bytes，与冻结记录及实际存储长度一致，PDF 首尾标记及页面内容正确；未保存短时票据或签名 URL。

[实际读取边界核验](../../.run/template-special-20260920/review/receipt-read-boundary-independent.json)五项通过：未知票据、缺失票据，以及同一文件的公开原路径、大小写变体、重音变体均返回 404；未知和缺失票据响应禁止缓存。未为使材料可读而放开公开访问。

浏览器配置、业务操作和实际审批由 gpt-5.6-sol 执行，满意度独立入口及定界的修复由 gpt-5.6-terra 执行；主代理负责修复整合、独立审查、构建以及与浏览器独立的只读数据库/文件证据核验。审批身份修复构建 130 项、随后文件读取修复构建 31 项针对性测试分别通过，未宣称全库测试、全部生产 MySQL 集成矩阵或 COM 扩展场景完成。

当前前端 18181 临时连接获授权的后端 59181，01:26 构建的宿主机新包 PID 22248 健康 UP，见[最终运行身份](../../.run/template-special-20260920/review/final-runtime-identity.json)。浏览器将本次专用 DB 文件配置 `36` 的域名调整为 `http://localhost:59181` 并重新打开核对；[主代理数据库回读](../../.run/template-special-20260920/review/file-storage-domain-independent.json)一致，原文件和项目证据保持不变。分支忽略的 `.env` 默认仍为前端 18181、后端 58181、`npdms-50eb-test`；未提交、未推送。以下按专题保留过程和原始失败证据，早期未闭环状态均为对应时点记录。

## 已确认的业务规则

- 交付件：文件或业务成果有效，并满足项目冻结模板配置的来源、数量和判定条件，即自动满足门禁；不新增人工审批。
- 初验与终验：分别要求当前报告生效、结论为 PASS、附件有效，并满足模板条件。初终验的前置关系由模板决定。
- 满意度：允许选择当前已发布问卷独立发起，复用现有评分、签字、结果文档和权限；传统项目任务触发方式继续保留（Q-SAT-DIRECT-001）。
- 专项模板仅含 S1～S6；现有业务入口可配置到阶段和任务，业务对象及其操作保持独立。参考模板 `FPROJ009_E2E_ACCEPT` 和旧项目保持历史可追溯。
- 关联需求：PM-03、PM-11、ACC-03/04、CLO-01/02；业务决策补充于 Q-DEL-UI-001 和 Q-TPLACC-001。COM 范围、多目标自动归档及 BPM 扩展不因本次通过而自动完成。

## 入口配置与实际推进方式

2026-09-21 01:08～01:12 按需求方重申的专项范围再次只读核验。[当前发布配置](../../.run/template-special-20260920/review/user-request-current-template-check.json)确认 v2 仍为 PUBLISHED，v1 未变，S1～S6 共 14 个任务；5 项业务结果绑定由 4 个 BUSINESS_COMPONENT 和 1 个 BUSINESS_OBJECT 构成，另有 7 个 PAGE 入口和 2 个 TASK_NATIVE 任务。各任务完成条件、S2～S6 的前阶段准入条件、全部阶段退出规则，以及 20 项必交付件的阶段退出与 EXIT 门禁引用均已配置。

| 阶段 | 已配置的业务操作或办理入口 | 模板实际完成依据 |
| --- | --- | --- |
| S1 | 现场工勘、需求分析、工程启动会 | 工勘确认、需求分析完成的业务事实自动完成对应任务；启动会保留本轮原生提交 |
| S2 | 施工计划制定中的工期入口 | 当前有效工期业务事实自动完成任务 |
| S3 | 实施方案编审页面 | 页面入口与模板执行分离，任务按本轮提交及配置条件完成 |
| S4 | 到货验收、硬件安装、配置调试、业务联调页面 | 业务可独立办理；对应 PAGE 任务保留本轮提交及配置条件 |
| S5 | 初验、终验、满意度页面、现场培训 | 初终验分别依据 EFFECTIVE / PASS 及配置条件自动完成；满意度入口独立采集，有效结果关联 D3 满足门禁；满意度与培训任务保留本轮提交 |
| S6 | 项目正常闭环页面 | 办理任务保留本轮提交；最终项目及 S6 阶段由真实闭环检查、既有 BPM 审核和正式退出记录收口 |

[当前执行证据核对](../../.run/template-special-20260920/review/user-request-current-behaviour-check.json)确认：5 项业务结果绑定任务均 DONE 且没有原生提交时间/提交人；其余 9 项任务均保留真实本轮提交。实施方案 20:45:58 已审批完成，S3 到 21:39:01 才激活，证明业务操作可以先于其模板阶段独立办理。后续阶段的实际激活时间均不早于前阶段完成时间。PAGE 导航本身不作为业务完成事实，原生阶段仍遵守模板配置的提交要求。

代码核对：[独立入口准入](../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/operation/ProjectIndependentOperationAdmission.java)复验业务调用策略、当前身份和项目范围；[规则协调器](../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectplan/ProjectRuntimeCoordinator.java)协调准入、任务、门禁和阶段；[任务状态转换](../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/taskworkbench/ProjectTaskLifecycleService.java)分别校验业务事实或本轮提交；[交付件提交与重评](../../pms-module-acceptance/src/main/java/cn/iocoder/yudao/module/pms/acceptance/service/acceptance/ProjectDeliverableSubmissionService.java)在来源与配置满足后更新判定并发出规则重评事件。[当前闭环断言](../../.run/template-special-20260920/review/user-request-current-closure-check.json)再次通过；原构建和全流程实操证据继续适用。

01:36，gpt-5.6-sol 完成修复后的单次[真实浏览器复验](../../.run/template-special-20260920/review/user-request-recheck/post-fix-template-readback.json)：首屏、查询、详情、版本历史及发布只读投影均无错误通知；历史模板只读实际发布修订，不再请求不存在的草稿。目标模板仍显示生效、6 阶段 / 14 任务，[版本历史](../../.run/template-special-20260920/review/user-request-recheck/post-fix-template-version-history.png)正确显示 Exec v3 / `template-version-3`。25 条模板业务响应均为 code 0，全程未执行保存、发布、复制或创建草稿。

主代理实际查看首屏、版本历史与冻结投影截图，并将浏览器读取的 14 项入口、绑定、完成谓词、阶段与版本字段逐项对照独立数据库证据，[12 项核对全部通过](../../.run/template-special-20260920/review/post-fix-template-independent-check.json)。本轮还保留[已关闭项目的当前只读页面回读](../../.run/template-special-20260920/review/user-request-recheck/closed-project-current-readback.json)：NORMAL_CLOSED、14/14、S1～S6 完成、闭环申请已批准，交付件历史可查且不再显示提交或重新检测入口。上述读取修复不改变原闭环业务事实，原实际办理证据继续复用。

## 修复内容

满意度独立入口已补充：创建时冻结发布问卷及项目版本，来源为 DIRECT，不伪造项目任务/交付件目标；达标使用既有 PASSED 状态，未关联交付件前不触发归档补偿。原结果判定、失效、整改重收和文件策略继续生效。新增确切结果来源 `ACC / SATISFACTION / SATISFACTION_PASSED`，通过结果与失效事件进入既有结果日志及规则重评通道，交付件门禁复验原结果文档和签字文件。

专项新增验证：[满意度后端回归](../../.run/template-special-20260920/repair/independent-satisfaction-tests.log) 42 项通过；[事件/失效补充构建](../../.run/template-special-20260920/repair/independent-satisfaction-package.log) 5 项通过；[整改/受控链接构建](../../.run/template-special-20260920/repair/independent-satisfaction-package-2.log) 3 项通过，整包构建成功。以上有重复覆盖，不合并为不重复总数。[前端运行测试](../../.run/template-special-20260920/repair/independent-satisfaction-ui-tests-2.log) 22 项通过，[类型检查](../../.run/template-special-20260920/repair/independent-satisfaction-types.log) 退出码 0。首次前端测试未指定仓库测试配置，CSS 加载失败；改用现有 `vitest.pms-file.config.ts` 后通过，未修改断言绕过失败。[V308 迁移](../../.run/template-special-20260920/repair/v308-migrate.log)成功，兼容原任务来源；恢复 Docker 后再次 validate 通过。

项目计划升级前，[主代理只读差异核对](../../.run/template-special-20260920/review/plan-draft-independent-diff.json)确认草稿仅增加 S5 D3 的 BUSINESS_RESULT 来源及对应输出类型，未更改判定条件、数量、其他阶段/任务或已完成结果。

1. 模板交付件支持配置来源、数量和判定规则，编译时检查引用及交付件依赖自身阶段完成的必然循环。页面路由与同类型节点的真实提交规则可组合使用，保存、回开和发布校验保持一致。
2. 项目交付件提供附件提交、业务成果关联、重新判定和原始提交历史。服务端使用冻结计划配置校验真实文件或确切业务成果版本，提交标识用于重放，同一标识不能更换材料；旧业务自动投影不能被手工来源覆盖。
3. 门禁重新核对真实材料，不单凭缓存的 ACCEPTED 状态放行。闭环检查覆盖已访问阶段的 EXIT 门禁，使用原始结果证据重验，并保留历史执行轮次和提交记录。
4. 新增项目独立初验/终验创建入口，保持 `tenant + project + acceptanceType` 唯一身份，不伪造任务、阶段、执行契约或交付目标。报告冻结创建时的判定策略，复用版本、附件和权限；新入口允许通过后的换版或撤销，旧任务来源保留原接口行为。
5. 分别提供 `PRELIMINARY_ACCEPTANCE_PASSED`、`FINAL_ACCEPTANCE_PASSED`，两者不能互相满足。独立报告发布、换版、撤销在原事务内进入现有业务结果通道；关联交付件时重验原报告附件。活动创建和事件投递成功均不被视为任务完成。
6. 验收业务对象按项目和模板配置的初验/终验类型自动关联；后台规则评估锁定当前执行轮次、真实报告及附件。发布校验要求类型通过条件与关联类型一致。工期计划兼容现有目录的 `PROJECT_CONSTRUCTION_PLAN` 关联键，保留原 `CONSTRUCTION_PLAN` 键。
7. 补齐交付件文件策略的引用集合检查和事务内重验接口，覆盖平台文件上传完成阶段；继续限制项目经理、项目状态、冻结模板允许来源及不可变文件引用。
8. 实操发现设备档案人工新增缺少非自增主键和来源字段。修复生成平台 ID，并落实既有人工补录规则：`PLATFORM_MANUAL`、待对账标记、必填原因和证据，证据写入追加的 CREATE 历史，不伪造 MES 来源键或版本。
9. 首次工期生效移除固定 S1 前置，继续要求 ACTIVE 项目、当前项目经理、管理数据范围及预期项目版本；阶段位置由模板决定。生效事务追加规则重评事件，驱动配置为工期有效事实的任务。
10. 修复验收报告日期传输：编辑器按后端统一毫秒时间戳契约提交，详情和历史按日期格式展示，保留旧版本原值。浏览器原 V1 的 1970 年错误日期不回写，通过新 V2 验证修复。
11. 原生阶段提交只原子记录本轮证据及既有 Outbox 事件，整图推进交由提交后事件执行，避免下游节点异常回滚有效提交。节点异常诊断只记录异常类型和代码位置，不记录自由文本证据或 SQL 参数。
12. PAGE 绑定任务的生命周期白名单和自动完成路径与既有原生任务一致；尚未提交的 IN_PROGRESS 任务不能仅凭页面业务办结自动完成，PENDING_ACCEPT 任务继续校验冻结模板规则和本轮提交证据。
13. S5 D0 重新检测的真实 UI 失败定位到交付件状态变化事件缺少载荷 `eventId`，被平台 Outbox 契约校验拒绝并回滚。补齐事件身份；保留文件、规则、事务和历史保护，不修改平台校验。回归覆盖满足、重复检测及文件失效后撤回满足状态，只有状态变化才追加对应事件。
14. 需求分析事实复验补齐独立来源：复用创建时冻结的 Owner 配置，保持项目/租户、模板与表单版本、当前权限、冻结内容及真实文件复验，不为独立结果伪造任务/阶段身份。原绑定来源继续执行原来的节点核验。旧接口强制要求来源任务导致的真实闭环错误 `1011002010` 被保留为失败证据。
15. 正常闭环专用图解析按新版模板冻结的终态标志判定，仍核验当前项目、图契约、阶段与门禁；不再把旧图“开始阶段必须 S0”的限制套到新版无 S0 模板。原阶段流转接口和旧图校验保持原契约。
16. 正常闭环审批通过时，在原有事务内结束当前终态阶段执行轮次，校验租户、项目、生效计划、阶段、当前标记及 ACTIVE 状态；版本冲突阻止项目关闭。结果快照使用真实闭环申请、检查快照、审批实例和复验证据，不补造原生提交；无执行计划的旧入口保留。此项由 gpt-5.6-terra 实施，主代理审查并统一验证。
17. 闭环复验验收报告附件时，原业务适配器将名称、大小、类别和媒体类型置空后传给要求完整事实一致的 PLT 接口，导致有效文件也出现版本冲突。修复为先读取完整事实再锁定复验，同时继续对比原冻结附件的 ID、版本、引用键、范围版本和哈希；没有降低文件有效性或原始来源校验。
18. 最终 BPM 审批区分申请人与实际审核人：原申请人的当前 PM、管理范围、版本及项目授权继续复验；业务 Owner 的查询和锁定复验使用当前认证审核人，并要求与冻结的材料审核人及租户一致。原有业务读取权限、文件有效性、事实摘要一致及失败回滚继续执行，不切换登录身份、不追加权限，也不使用历史完成快照替代当前事实。初次检查和提交的原入口仍使用申请人身份。由 gpt-5.6-terra 实施，主代理审查并补齐 API 说明与测试导入后统一验证。
19. 已关闭项目取回交付材料时，数据库文件客户端不支持存储侧签名而抛出 `UnsupportedOperationException`。在既有 PLT 权限、业务范围和文件版本复验之后，为 DB 客户端增加短时受控读取；沿用冻结文件配置与既有授权时长，Redis 仅保存随机令牌摘要和冻结元数据。新读取端点不接受文件 ID 或存储路径，校验有效期、元数据及实际内容长度，不记录原始令牌，原 S3 签名路径保留。旧公开文件端点拒绝回执保留目录，并检查数据库返回的真实路径，覆盖本环境 `utf8mb4_unicode_ci` 对大小写和重音变体的等价匹配。普通文件仍可读取，未新增 Schema。由 gpt-5.6-terra 实施，主代理独立审查并提出真实路径、预览 MIME 参数及过期检查的修正。
20. 模板修订响应补齐已有的 Designer Schema、Execution Schema、Compiler 与快照哈希字段。数据库发布版本原本为 Designer 2 / Exec 3 / `template-version-3`，缺少响应字段导致页面误标 Legacy。只修复读取投影，不修改数据库、Compiler 或冻结内容；当前 Compiler 不生成新哈希，原空值继续为空。
21. 列表摘要先读取模板修订清单，有 DRAFT 才读取草稿，否则读取最新 PUBLISHED 版本的兼容投影；无版本时不显示摘要。真实读取失败保留原错误处理。原首屏历史模板 `992203040001` 只有发布版本，没有草稿，后台盲读草稿触发的通知延续到专项模板详情；[按首屏、查询、详情分段的网络记录](../../.run/template-special-20260920/review/user-request-recheck/template-list-timeline-diagnostic.json)确认该原因。未给历史模板补造草稿，未屏蔽 Axios 业务错误，也未修改显式草稿编辑行为。

## 当前验证证据

- [模板版本响应构建](../../.run/template-special-20260920/repair/template-revision-metadata-package.log)：2026-09-21 01:26 完整打包成功，19 项针对性测试通过，覆盖修订列表和详情字段映射、空哈希保留、控制器契约及旧版运行边界。
- [模板列表摘要与 API 契约测试](../../.run/template-special-20260920/repair/template-summary-tests.log)：12 项通过，覆盖实际草稿、无草稿时选择最新发布版、无版本、读取失败传播及原接口契约；[前端类型检查](../../.run/template-special-20260920/repair/template-summary-types.log)退出码 0。[主代理数据库核验](../../.run/template-special-20260920/review/template-version-metadata-independent.json)确认两条发布版本均保留 Designer 2 / Exec 3 / `template-version-3` 及空哈希，首屏历史模板仍只有发布版本。
- [后端构建与 89 项测试](../../.run/template-special-20260920/repair/independent-acceptance-package.log)：通过，覆盖报告有效性、验收类型隔离、旧入口、独立创建、权限、前置关系、撤销和原生发布校验。
- [交付件事务与事件测试](../../.run/template-special-20260920/repair/delivery-transaction-event-tests.log)：14 项通过。Spring 事务代理使用独立 H2 写入账本验证 Outbox 失败时根状态、前一来源、附件及提交记录一起回滚；该项不替代生产 MySQL Mapper 的浏览器验收。
- [前端 25 项测试](../../.run/template-special-20260920/repair/acceptance-ui-tests-3.log)：通过，包含项目切换、权限、只读与原生规则配置。前端类型检查另行保留执行日志。
- [自动关联与发布校验构建](../../.run/template-special-20260920/repair/automatic-acceptance-package-2.log)：完整构建通过，验收事实/关联 24 项、模板发布规则 20 项、工期关联 2 项测试通过。[绑定参数测试](../../.run/template-special-20260920/repair/acceptance-binding-ui-tests.log) 9 项通过；本次前端类型检查退出码 0。以上是不同范围的验证记录，不将重复覆盖项相加作为独立测试总数。
- [V306 迁移](../../.run/template-special-20260920/repair/migration.log)、[V307 迁移](../../.run/template-special-20260920/repair/migration-v307.log)：当前指定容器迁移成功；V307 迁移前两条旧验收来源满足兼容约束。
- [交付件文件策略构建](../../.run/template-special-20260920/repair/deliverable-file-policy-package.log)：19 项通过，含引用集合策略 3 项、权限 4 项、提交及回滚 12 项；完整打包成功。
- [阶段提交与规则事件构建](../../.run/template-special-20260920/repair/stage-submit-package-2.log)：17 项通过，覆盖原生证据、权限、旧轮次拒绝、独立节点失败和事件恢复。首次运行发现事件领取测试仍只列旧的 3 类事件，补入现有业务结果订阅的 7 类事件，保持精确事件范围与通知隔离断言后重跑通过。
- [报告日期前端回归](../../.run/template-special-20260920/repair/report-date-tests.log)：14 项通过；[真实浏览器往返](../../.run/template-special-20260920/repair/acceptance-reports/preliminary-fail-v2-result.json)确认 UI、POST、草稿 GET、生效 GET 时间一致。
- [PAGE 生命周期回归](../../.run/template-special-20260920/repair/page-lifecycle-package.log)：74 项通过，覆盖绑定契约、角色与状态机、原生任务提交、无提交拒绝完成，以及冻结计划规则；报告日期[类型检查](../../.run/template-special-20260920/repair/report-date-types.log)退出码 0。
- [设备人工新增修复构建](../../.run/template-special-20260920/repair/device-manual-create-package.log)：6 项通过，覆盖身份约束、人工来源与创建历史、缺少原因/证据拒绝写入、重复序列号拒绝新增。前端类型检查首次因 Node 默认约 4 GiB 堆上限退出，增加本次进程堆上限后[重跑](../../.run/template-special-20260920/repair/device-manual-create-types-2.log)退出码 0。
- [工期阶段解耦修复构建](../../.run/template-special-20260920/repair/duration-decoupling-package.log)：22 项通过，含首次生效及规则事件、查询权限、项目参与者校验；固定 S1 限制解除后，项目经理/生命周期/版本校验仍执行。
- [7 个业务页面入口配置](../../.run/template-special-20260920/repair/template-rules/page-route-summary.md)、[交付件规则回读](../../.run/template-special-20260920/review/configured-template-summary-20260920-191207.json)：由 gpt-5.6-sol 执行浏览器操作，主代理独立读取配置并核对。

## 运行与浏览器闭环

- [交付件事件修复构建](../../.run/template-special-20260920/repair/deliverable-event-identity-package.log)：14 项交付件/门禁测试、2 项平台事务事件测试通过，完整打包成功；[原始异常位置](../../.run/template-special-20260920/review/d0-event-identity-error.log)与真实 UI 的 500 响应对应。
- 公共模板已于 23:36 发布 v2 `993009001599`。[发布版本独立核对](../../.run/template-special-20260920/review/public-v2-final-independent-check.json)确认 v1 `993009001598` 与 20:13 原始快照完全一致，v2 精确只有 D3 允许业务成果和对应输出类型两处变化，其他规则保留。
- [需求分析事实复验](../../.run/template-special-20260920/repair/independent-requirement-closure-tests.log)：30 项通过，包含独立来源无任务身份、完整内容变化、文件不可用、租户/项目/模板错配拒绝，以及既有业务对象接口回归。
- [正常闭环接入构建](../../.run/template-special-20260920/repair/normal-closure-integration-package.log)：2026-09-21 00:02 完整打包成功，本轮 56 项通过（PROJ 26、需求分析 30）；覆盖无 S0 终态识别、旧图限制保留、闭环执行轮次匹配及冲突、已完成原计划结果保护、独立需求分析与旧入口。与上条存在重复覆盖，不汇总为独立总数。
- [报告附件完整事实复验构建](../../.run/template-special-20260920/repair/closure-report-file-facts-package.log)：2026-09-21 00:08 完整打包成功，本轮 62 项通过（ACC 46、PROJ 16）；完整事实正常锁定，同时拒绝与原冻结附件不符的文件版本及内容。
- [审批身份与最终复验构建](../../.run/template-special-20260920/repair/closure-reviewer-context-package.log)：2026-09-21 00:29 完整打包成功，本轮 130 项通过（真实 Flowable / H2 12、ACC 30、PROJ 45、工程业务事实 43）。覆盖申请人和审核人不同、原检查入口同身份、错误审核人/租户/缺登录拒绝、Owner 复验失败及摘要变化阻止关闭；复用各业务事实的查询/权限测试和实际流程验证。未运行全库测试或生产 MySQL 全量集成矩阵。
- [数据库文件受控读取构建](../../.run/template-special-20260920/repair/receipt-db-access-package.log)：2026-09-21 00:58 完整打包成功，本轮 31 项通过（Infra 20、PLT 11）。验证 DB 回退、原签名读取、未知/过期令牌、冻结路径及配置变更、缺失或长度异常内容、带参数的安全预览类型、读取失败和公开端点旁路保护；沿用 PLT 授权及文件事实测试。原下载失败见[脱敏错误证据](../../.run/template-special-20260920/review/file-download-presign-error.log)。
- 00:09:53，真实 UI 闭环检查成功，快照 `2101705414776156162`；[主代理独立回读](../../.run/template-special-20260920/review/closure-check-passed-independent.log)确认 72 项条件全部通过。00:10:45 创建申请 `2101705632108212225`，真实 BPM 实例 `d5e8c4c4-b50d-11f1-adbb-00ff0b78abd7`。第一审批节点通过；第二节点触发最终业务复验时因传入申请人而当前登录人为审批人，被业务 Owner 身份校验拒绝。[失败后独立核对](../../.run/template-special-20260920/review/closure-approval-rejected-independent.log)确认项目仍 ACTIVE / S6 / version 10、申请 IN_REVIEW，未写入退出记录，未把 HTTP 200 中的业务 403 宣称为通过。

当前分支 `.env` 被 Git 忽略，前端 18181、后端 58181、Compose 项目 `npdms-50eb-test`；环境文件不提交。2026-09-20 21:15 含工期阶段解耦修复的包在宿主机启动，PID 35556，健康状态 UP，未把前后端放入容器。

验收模板为 `FPROJ009_E2E_S1S6_20260920`，模板 ID `993009001591`。gpt-5.6-sol 已于 20:11:02 经浏览器预检并发布版本 1，发布修订 ID `993009001598`，Compiler 无问题。主代理独立回读发布版本：S1～S6、14 项任务、29 项交付件、11 个门禁，无 S0、孤立要素、空门禁或缺失节点规则；见 [发布操作](../../.run/template-special-20260920/repair/execution-prep/template-publish.json) 和 [独立回读](../../.run/template-special-20260920/review/published-template-check.log)。

专用项目已创建：`PJT2026000005`，名称 `FPROJ009_REPAIR_20260920_全流程验收`，项目 ID `992203060007`，冻结计划 ID `2101646104495247361`，引用发布修订 `993009001598`。主代理回读确认 S1 激活、后续五阶段待执行、14 项任务、20 项必交付件和 11 项门禁，初始交付件尚未满足。

实操期间 Docker Desktop 引擎退出，原 MySQL/Redis 容器停止，导致页面导航超时。已恢复原 `npdms-50eb-test` 容器，未重建卷或修改业务状态；[恢复后独立回读](../../.run/template-special-20260920/review/project-after-runtime-recovery.log)确认上述项目与计划数据保留。当前 admin 不具项目公司的项目经理系统角色和组织资格，候选接口返回空是正确限制；浏览器验收使用已有资格账号继续办理，不降低资格校验。

截至 2026-09-20 23:09，[主代理恢复后直接读取 MySQL](../../.run/template-special-20260920/review/pre-satisfaction-check-recovered.log)确认项目仍为 ACTIVE / S5，S1～S4 已完成、14 项任务中 12 项完成。初验 V3 `2101672008936062978` 与独立终验 V1 `2101673455245328385` 均为当前 EFFECTIVE / PASS，分别形成于 21:56:59 和 22:02:26；旧初验 FAIL 版本保留。全部关联文件的实际存储内容长度与冻结记录一致。尚未形成完整闭环通过结论。

满意度专项问卷已由 gpt-5.6-terra 通过 UI 创建并发布：模板 `2101674324112179201`、修订 `2101674336615399426`，复用仓库验收样例的 Q1 20/100 分、SUM_V1、阈值 80；这只是本专项合成数据，不定义全系统统一阈值。随后独立创建采集任务 `2101696099474464770`、问卷 `2101696099474464771`，缺签字 UI 预检被拦截；使用明确标注为内部合成验收的签字完成答卷/结果 `2101696112078348290`，评分 100、阈值 80、EFFECTIVE / PASSED。新浏览器页面回读一致。主代理[数据库及实际文件核对](../../.run/template-special-20260920/review/satisfaction-complete-check.log)确认 DIRECT 身份、无伪造项目任务/交付件绑定、签字文件 `2101696112950763522` 和结果 PDF `2101696116599808002` 均 AVAILABLE / ACTIVE 且真实内容完整，未发起额外审批或外部消息。

项目 D3 独立计划 `2101671740756459522` 已通过真实 UI 生效为 v2；[历史独立比对](../../.run/template-special-20260920/review/plan-history-independent-check.json)确认原 16 个 DONE 执行（4 阶段、12 任务）的完整记录逐字段保持不变，包括原计划、轮次、提交/结束时间及结果快照。公共模板草稿预检曾显示“模板草稿版本不存在”；重新打开后同一 GET 草稿成功、Compiler `valid=true`，数据库确认草稿一直存在，因此没有据该一次 UI 错误新增冗余升版接口。公共模板[草稿差异](../../.run/template-special-20260920/review/public-v2-independent-diff.json)同样仅包含 D3 的两项来源调整。

23:47，D0 修复后首次及重复重新检测均通过，S5 原生提交成功；[主代理独立核验](../../.run/template-special-20260920/review/s5-complete-independent-check.log)确认 S5 DONE、项目进入 S6。随后 S6 任务及 D0/D2 的真实 PDF 提交完成，[23:56 独立回读](../../.run/template-special-20260920/review/s6-before-closure-independent-check.log)确认 14 项任务完成、20 项必交付件满足、11 项门禁通过，S6 保留 ACTIVE 等待正常闭环审批。最初闭环复验仍因独立需求分析来源及旧 S0 约束失败；未将任务完成率 100% 宣称为项目已闭环。

环境恢复记录：Docker Desktop 恢复后，Windows/Hyper-V 将 58181 纳入动态保留区间 58118～58217，IPv4/IPv6 实际绑定返回 10013。需求方最初选择自行解除；随后已存在健康的本分支 59181 实例，需求方明确批准“使用现有 59181 实例继续验收”。主代理核对该实例使用 22:56 最新构建、同一 13306 / 16379 基础设施，并以仅进程环境 `VITE_BASE_URL=http://localhost:59181` 启动前端 18181；实际读取前端变换后的模块确认连接 59181，后端 health 为 UP。根 `.env` 和前端 `.env.env.local` 的默认 58181 均保留，仍为 gitignored；容器仍为 npdms-50eb-test，未调整其他项目容器或系统网络保留区间。

[旧项目保护核对](../../.run/template-special-20260920/review/original-project-preserved-2309.log)：参考项目 `992203060006` 仍为 NORMAL_CLOSED，原计划 `2101585163774709761`、7 阶段、14 个 DONE 任务和 12 个 PASSED 门禁保持；未新增交付件提交，也未追写历史业务证据。

已独立核验的中间结果：

- 工勘确认、需求版本冻结分别自动完成 S1 两项任务，执行轮次没有人工提交时间；启动会通过原生“提交任务”完成，当前轮次保存提交人 103 和时间 20:51:48.831。三种完成依据分别留存，未用进度百分比替代提交或业务事实。
- 方案 `30009` 已通过、到货 `30011` 已签收，此时 S3/S4 尚未进入，证明这些业务操作可独立办理；模板任务仍按自身配置推进。
- 首份 PDF 上传暴露隔离环境默认文件配置指向非 S3 地址，返回 HTTP 405。浏览器新增数据库存储配置 `36`（`FPROJ009_REPAIR_20260920_DB`，域名 `http://localhost:58181`）并设为主配置，旧配置保留。随后 S1 D3 上传、提交、自动判定通过，提交 ID `2101656893943836674`、文件 ID `2101656885345513473`；[独立回读](../../.run/template-special-20260920/review/s1-first-deliverable-check.log)确认真实来源和 ACCEPTED 状态。
- S1 四项必交付件满足后，EXIT 门禁自动通过；浏览器提交本轮阶段办理结果，S1 完成、S2 自动激活。[21:04 独立回读](../../.run/template-special-20260920/review/s1-complete-check.log)确认项目阶段为 S2，未直接写入生命周期状态。
- S2 阶段原生提交初次反复回滚；拆开提交事务与下游规则推进后，真实 UI 一次提交成功。[独立回读](../../.run/template-special-20260920/review/s2-after-transaction-fix.log)确认 S2 本轮提交于 21:38:59.063、完成于 21:39:01.803，S3 自动激活。随后安全诊断定位原事务内异常为 PAGE 生命周期白名单漏项，已补齐并针对性验证。
- 专用设备 `2101660756688084994` 创建成功，[独立回读](../../.run/template-special-20260920/review/manual-device-check.log)确认人工来源、待对账标记及 CREATE 历史中的真实测试说明。
- 安装 `30012`、配置 `30013` 已完成，联调 `30014` 通过，均复用项目专用设备；业务完成时对应模板阶段尚待激活。
- 初验活动 `2101662119639654402` 独立创建，报告 V1 保留为 SUPERSEDED；V2 `2101666751556362241` 为 EFFECTIVE/FAIL，实际验收时间 2026-09-20 21:36:06。[数据库独立回读](../../.run/template-special-20260920/review/initial-fail-v2-check.log)确认活动仍 PENDING；尚未将“报告生效”视为验收通过。
- 后续发布初验 PASS V3 后任务自动完成；但 D2 原来关联的 FAIL 版本仍被服务端以 `DELIVERABLE_BUSINESS_RESULT_INVALID` 拒绝，旧提交保留 PENDING。重新关联确切 PASS 版本后 D2 才满足；独立终验 PASS 与 D4 关联也已完成。此负向/正向结果证明不会用新版 PASS 替代原提交引用的 FAIL。
- S2 原工期入口因固定 S1 校验返回范围错误；修复后当前 PM 在同一入口成功生效工期，计划/版本均为 `1`，日期为 2026-12-02～2026-12-31、30 天。交付件操作前任务已自动完成；[事件证据](../../.run/template-special-20260920/review/duration-event-evidence.jsonl)确认原事务后规则事件投递。工期根仍为 `PENDING_RECALCULATION`，本项证明有效工期基线与模板任务推进，不将其等同于所有计划排程重算完成。

未提交、未推送；不将编译或单元测试通过等同于浏览器闭环验收通过。
