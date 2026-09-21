# 项目交付件修复与新项目浏览器验收

本记录按用户授权的专项修复和实际业务操作生成，不晋级工程链状态，不修改历史项目或已发布模板。浏览器配置、业务办理由其他模型执行（前段 gpt-5.6-sol，后段及机械执行 gpt-5.6-luna）；主代理审查实现、原始请求与截图，并以只读数据库查询独立核验。

最终结果：本报告列明的新项目及场景通过。四类业务文件自动归集、S1～S6、14 个任务、11 个门禁和 20 项必需交付件已通过，项目已正式 NORMAL_CLOSED。闭环后四入口实际弹窗均只读，手工材料和自动满意度原 PDF 均已通过浏览器下载。自动归集仍存在下述异步延迟限制。

## 本次对象

| 对象 | 身份 |
| --- | --- |
| 原模板 | `993009001591` / `FPROJ009_E2E_S1S6_20260920` |
| 新复制模板 | `993009001593` / `FPROJ009_DELIVERABLE_FIX_20260921` |
| 新模板发布版本 | v1 / `993009001603` |
| 新建项目 | `992203060011` / `PJT2026000009` |
| 项目名称 | `FPROJ009_DELIVERABLE_FIX_20260921_全流程验收` |
| 唯一生效计划 | `2101955003840139266` |

新模板 29 项交付件采用有效文件存在及最小数量判定，不以任务完成替代材料。四项自动来源为 S1_D4 需求文档、S5_D2 初验报告、S5_D3 满意度结果文档、S5_D4 终验报告。其余阶段、任务、门禁、里程碑、转换、规则、规则程序、闭环策略和匹配条件这九部分与原模板一致。

## 实现修复

- 项目业务中心、生命周期、工程交付件和验收交付件入口复用同一模板交付件办理能力，支持选文件、上传绑定、逐项提交、历史查看与下载。写权限依据项目权限、数据范围及生命周期，不额外要求操作者等于项目经理。
- 新建交付件默认文件存在判定；模板草稿支持按组调整已有交付件。文件数量、可用性和原始引用仍由服务端校验。
- 业务文件通过原 Owner、对象、用途和项目归属映射到冻结计划声明的来源，保留原始文件版本，不要求用户在交付件页面重复上传。事件重放幂等，既有手工材料保留。
- 业务结果变更唤醒正常规则执行器，任务和阶段继续通过已有状态机推进。
- 浏览器复验发现自动目标查询错误排除了 `source_definition_id` 为空的真实模板实例，已修正为冻结计划声明的交付件编码范围查询，保留租户、项目、删除状态及空集合隔离。
- 浏览器复验发现满意度生成 PDF 未发出文件事件，已在原生成事务中补充 `FileVersionCommitted`、`FileReferenceAttached`；已存在引用的幂等重放不补发历史事件，事务失败不留下成功完成的上传会话。
- 正式闭环复验发现旧图解析器强制终态阶段仍为 ACTIVE，错误拒绝了规则已完成的 S6。现仅在配置模板的闭环检查中接受终态 DONE、无 ACTIVE、各阶段为 DONE/SKIPPED 的完成图；普通推进和旧图仍保持原约束。退出时要求阶段与当前计划轮次状态一致，已完成轮次不再改写，审批、来源复检、版本及原子退出逻辑保留。
- 闭环后下载复验发现满意度文档策略允许 DOWNLOAD、却拒绝详情查询所需的 READ，使文件接口返回成功但 `data:null`。已补 READ 并复用原项目 VIEW 范围，明确拒绝跨租户及项目范围外读取；不扩大 PREVIEW 或写权限，也不转换 Snowflake ID 为可能丢失精度的 JavaScript Number。

关键代码位于 `pms-module-acceptance` 的交付件服务/来源注册、`pms-module-engineering` 的需求文档来源、`pms-module-platform` 的文件证据/事件/生成文件服务、`pms-module-project` 的冻结配置/业务结果日志，以及前端项目交付件组件和模板规则编辑器。

## 独立核验的业务证据

| 场景 | 实际结果与证据 |
| --- | --- |
| 手工材料不依赖任务完成 | 尚未指派项目经理、14 个任务未完成时，真实上传并提交 S1_D0 成功；空文件时不能提交。四入口指向同一交付件及历史。`manual-before-task-independent-check.json` |
| 业务文档不依赖业务完成 | 需求分析修订仍为 DRAFT、对应任务 IN_PROGRESS 时，原页面上传的文件自动归入 S1_D4。`automatic-before-task-independent-check.json` |
| 任务完成不能替代文件 | S1 任务全部完成，但 D3、D5 缺失时，阶段及门禁保持未通过；补齐文件后正常推进。`task-completion-does-not-replace-files.json` |
| 业务可先于模板阶段办理 | S3 尚未激活、任务待分派时，独立业务页面已完成方案审批。`business-before-stage-independent-check.json` |
| 有文件与业务通过分别判定 | 初验 V1 为 FAIL 时，原报告文件已使 D2 ACCEPTED，但初验任务仍 IN_PROGRESS。`fail-report-file-presence-independent-check.json` |
| 业务事件推进任务 | 初验 V2 PASS 后 0.780 秒、终验 PASS 后 0.934 秒，对应任务分别自动 DONE；初验 V1 FAIL 历史保留。`preliminary-pass-event-independent-check.json`、`final-pass-event-independent-check.json` |
| 生成文件自动归集及阶段推进 | 新办满意度结果 `2101976907993042946` 生成 PDF，原始引用 `70` 自动归入 D3；S5 于 18:15:32.247 被 `project-rules` 完成，S6 于 18:15:32.319 激活。`satisfaction-generated-auto-progression-independent-check.json` |
| 四类原始文件有效性 | 四项自动交付件共五个原始文件引用，Owner 项目归属、artifact/version、非空文件、有效引用及文件可用性全部符合。`992203060011-satisfaction-current-independent-check.json` |
| 正式审批及退出 | 申请 `2101983463165870082`，流程 `1014878d-b5a8-11f1-9de7-00ff0b78abd7`，服务经理 103 与材料审核人 1 分别在原 BPM 节点 APPROVE；18:36:22.131 产生 NORMAL_CLOSED 退出记录 `2101983869585539074`。`992203060011-closed-independent-check.json` 的 17 项检查全部通过。 |
| 已完成历史保护 | 闭环前后 6 个阶段、20 个办理轮次、1 个冻结计划逐行内容完全一致，新增两级审批记录和正式退出记录。`closure-history-and-approvals-independent-check.json` |
| 四入口关闭后只读 | 四个入口均实际打开项目 011 的 S1_D0 弹窗，详情 ID 均为 `992004200222`，`writable=false`，有明确只读文案且无上传、提交、重新检测按钮。`closed-four-entry-independent-check.json`、`browser/execution/closed-four-dialog-readonly.json` |
| 关闭后实际下载 | 正常 UI 下载的 S1_D0 为 1440 字节、S6_D0 为 1787 字节、自动 S5_D3 为 750 字节，三者均为真实 `%PDF-` 文件。主代理查看自动文件卡片截图并独立读取下载文件字节；自动文件仍使用原始 artifact `2101976911239434243` / reference `70`。`closed-file-download-bytes-independent-check.json` |

本模板含 5 个 BUSINESS_FACT 任务、7 个 PAGE 任务和 2 个 TASK_NATIVE 任务；不将全部 14 个任务表述为自动完成。材料存在判定不会取消业务结果、任务办理、门禁和正式闭环审批。

## 验证和限制

- 后端按最新测试类结果去重，共 17 类、129 项（首次 80、消费者 9、自动目标回归 2、生成文件事件 5、闭环 27、满意度文件策略 6），全部通过。最后回归轮次 60 项，打包成功；`backend-test-independent-check.json` 保存逐类日志索引。
- 前端定向测试 15 项（画布 8、清单 7），类型检查通过。未执行全仓测试及生产前端构建，不以这些检查替代浏览器业务验收。
- 原模板及全部三个版本、既有项目 `992203060008/009/010` 的 14 类计划/执行/交付件/闭环表按原始行内容对比；不是仅比较行数。不做历史迁移、回填或 SQL 业务写入。
- 自动归集存在可见异步延迟：需求文档约 115 秒、初验文件约 195 秒、生成满意度 PDF 到归集 407.291 秒。已证实最终归集及正常推进；没有据此宣称实时性达标。具体调度延迟原因尚未定位，不能将推测写成确定根因。
- 验收中遇到的真实失败保留证据，修复后使用正常新业务操作复验；没有直接写项目/任务/门禁状态。

## 证据与复核命令

原始证据目录：`.run/template-deliverable-fix-20260921/`。`browser/` 保存操作、请求响应和截图；`review/` 保存独立只读快照与检查；测试日志包括 `package-tests.log`、`consumer-tests-confirmed.log`、`auto-target-fix-tests.log`、`generated-file-event-tests.log`、`normal-closure-fix-tests.log`、`file-readability-fix-tests.log`。

项目快照由 `review/capture-project.ps1` 和 `review/capture-owner-facts.ps1` 生成；终态由 `review/verify-new-project.ps1 -Closed` 核验，自动文件由 `review/verify-automatic-files.ps1 -ExpectedDeliverables 4` 核验，历史保护由 `review/verify-preservation.ps1` 核验。报告反映这些指定对象和场景，不替代所有模板的全量验收。
