# 迁移项目模板分配基础同步任务

日期：2026-10-24。任务编码：`BASE_P38`；名称：迁移项目模板分配。已按本次授权完成专用测试后端 59284 校验、重载、注册及真实浏览器预览。预览读取 79,429 个迁移项目，6,956 个 ACTIVE 项目全部唯一匹配，72,473 个已关闭项目跳过；实际执行的后续结果见下节。

最新执行状态：用户随后授权“执行”，已提交 1 次真实执行；Run `2113744705170100226` 于 05:29:45.021 至 05:29:46.401 失败，事务回滚，实际分配仍为 0。原因排查发现当前租户没有已发布任务状态机，只有 V502 恢复的第 1 版草稿；这是初始化命令的必要前置，模板匹配成功不能替代此项。

## 执行失败与待确认前置

- 执行前再次核对：任务无活动 Run、配置仍为每批 500/上限 1,000,000、冻结包未变、79,429 个迁移项目仍未绑定模板，其中 ACTIVE 6,956。
- 唯一一次执行请求通过正式 `/api/v1/pms/integration/base-sync/execute` 入口提交。Run 终态 FAILED，read/insert/update/unchanged 均为 0，摘要为 `同步读取或写入失败：ServiceException`。日志未保留原业务异常码，以下缺项由数据库和初始化服务契约共同核实。
- `ProjectTemplateInitializationService.initializeOnce` 必须读取当前已生效的 PUBLISHED 任务状态机；返回空时在写模板绑定前拒绝。当前租户 1 仅有 ID `996610195020063`、revision_no 1、version 0、状态 DRAFT，来源 `migration-v502-59299-config`，没有 PUBLISHED 版本。
- 已读取现有草稿的 8 条转换：分配、开始、提交、完成，以及待分配/待开始/进行中/待验收四种状态的取消；与现有核心转换定义对应。没有修改草稿、角色或条件，没有调用发布入口。
- 失败后项目绑定、版本和状态汇总与执行前一致；六张运行表仍为 0 行，任务占用已释放。旧预览回执保留，未重复提交执行。

下一步建议：通过现有 `POST /api/v1/pms/project-task-state-machines/996610195020063/actions/publish` 发布该第 1 版草稿，使用 `If-Match: 0` 及独立幂等键，再重试 BASE_P38。发布会使该租户后续新任务使用此流转规则，范围超过本次模板分配执行，待用户确认；不直接更新状态字段或临时绕过已发布版本校验。

原始证据：`execute-preflight.json`、`execute-request.json`、`execute-response.json`、`execute-runs.json`、`execute-after-failure.json`、`initialization-diagnosis.json`，均位于本文末尾原始证据目录。

## 真实预览结果

| 项目 | 结果 |
|---|---:|
| 注册任务 ID | 2113735279960838146 |
| 预览 Run ID | 2113735375741964289 |
| 终态 | PREVIEW_READY |
| 读取迁移项目 | 79,429 |
| 唯一匹配、计划更新 | 6,956 |
| 已关闭、保持不变 | 72,473 |
| 无匹配 / 匹配冲突 | 0 / 0 |
| 实际模板分配 | 0 |

运行时间：2026-10-24 04:52:40.700 至 04:56:10.508（Asia/Shanghai），约 3 分 30 秒；每批 500，读取上限 1,000,000。跳过回执为 `PROJECT_TEMPLATE_NOT_ACTIVE:72473`，对应 NORMAL_CLOSED 20,674 和 EXCEPTION_CLOSED 51,799。

浏览器在 59285 基础同步页点击 BASE_P38 行的“预览”，页面显示“迁移项目模板分配 · 全部阶段 · 预览”，读取 79,429、插入 0、更新 6,956、未变 72,473、删除 0。这里“更新”是预览规划数量，不是已写入数量。数据库仅有该任务的 1 条预览 Run，没有执行 Run；任务占用已释放。

预览前后，79,429 个迁移项目的绑定/版本/状态汇总校验值均为 2886414739，三类状态的模板绑定数仍均为 0。计划版本、阶段、任务、门禁、里程碑和交付件六张运行表在租户 1 下均保持 0 行。未修改模板规则、授权范围或项目状态。

结构化结果见 [真实预览证据](migrated-project-template-preview-20261024.json)。原始证据目录中的 `preview-run.json`、`preview-before.json`、`preview-after.json`、`browser-registered.txt` 和 `browser-preview-result.txt` 分别记录回执、前后保护核对及浏览器结果。

## 现有能力审计与复用

- `ProjectTemplateV2ServiceImpl.matchPreview`：使用 ACTIVE 模板的最新可用已发布修订、冻结规则程序和既有优先级选择；不另建匹配算法，不退回旧四维匹配。
- `ProjectRuleFields.creationFacts`：复用项目创建事实字段。
- `ProjectTemplateInitializationService.initialize`：复用授权、项目锁、版本校验、可用修订检查、锁内重新匹配、幂等命令、模板快照、计划及阶段/任务/里程碑/门禁/交付件初始化与准入激活。任务不直接更新模板绑定或生命周期状态。
- `ProjectScopeApi` 与 `ProjectPlanDraftService.authorize`：先限定当前执行人的可编辑项目集合，逐项目校验，写入时由既有初始化命令再次校验。

## 本次实现

新增项目模块内的 `MigratedProjectTemplateSyncTask`、对应 Mapper XML 和专项测试。实现 `DefaultSyncTask`，通过已有基础同步注册、预览、执行入口使用；没有新增公共 API，没有跨模块调用项目 Service。

读取当前租户、未删除、`source_type=MIGRATION` 且在执行人授权集合内的项目。权限集合为空返回零候选，不省略权限条件。按配置批量拆分权限 ID，跨批次累计读取上限。

只有 ACTIVE、尚未绑定模板/计划且没有既有运行结构的项目参加匹配。唯一匹配时调用原初始化命令；以下情况保持原件并汇总跳过原因：已初始化、非 ACTIVE、已有运行结构、无匹配、同优先级多匹配。预览只计算变更计数，不调用初始化写入。实际执行沿用基础同步外层事务，后续项目失败时整体回滚。

任务编码沿用注册配置的 `BASE_P[0-9]{2}` 约束；仓库检索未发现既有 `BASE_P38`，不扩大通用注册校验。

## 初始化配置

新增任务通过现有注册接口保存配置；没有新表或 DDL。本次实际注册配置如下：

```json
{"taskCode":"BASE_P38","connectionId":2112881203220676609,"batchSize":500,"maxRows":1000000}
```

连接引用取自当前 P16 注册，只满足既有基础同步配置要求；本任务从本地项目表读取，不打开外部源查询。注册仍要求原配置权限及有效连接，运行还要求 `pms:project-plan:manage` 和项目数据范围。注册入口：`POST /api/v1/pms/integration/base-sync/registrations`。

## 验证与限制

首轮 42 项定向测试：41 通过、1 失败。新增专项 9 项全部通过，原初始化服务 14 项和 V2 匹配资格 10 项通过；原规则求值器 9 项中 1 项失败。失败为 `rejectsRuntimeOnlyFieldsEvenWhenCallerProvidesThem`：期望 UNKNOWN，实际 MATCHED。独立只运行该原测试仍失败，其实现及测试均未在本次修改；未降低校验或修复无关规则逻辑。

专项覆盖租户/来源/删除/授权过滤、预览零写入、版本与命令幂等、重放保护、无匹配/冲突、关闭项目和部分历史绑定保护、空或失效权限、跨批次上限、后续命令失败整体回滚及最大批量参数绑定。使用隔离 H2 数据库，不写正式业务数据。

任务编码修正为 `BASE_P38` 后，单独重跑新增 9 项测试全部通过，源码前后指纹一致。结果位于 `final-task-test/test-run.json` 与 `final-task-test/test-report.xml`；注册编码与现有配置正则一致性检查见 `registration-contract-check.json`。

只读数据盘点：迁移项目 ACTIVE 6,956、NORMAL_CLOSED 20,674、EXCEPTION_CLOSED 51,799，模板绑定计数均为 0。这是状态分布，不是唯一匹配数量或分配承诺；关闭项目不纳入初始化。

原始日志与数据盘点：`D:/CodexTaskArtifacts/project-detail-followup/migrated-project-template-sync-20261024`。实际批量分配失败并回滚；主实例 59191 未操作。

## 初次构建与运行阻塞记录

最终源码通过 `scripts/worktree-build.ps1 -Target backend -AllowOfflineRuntime -MavenArguments @('-Dmaven.test.skip=true')` 的 Maven 全量编译打包与源码前后一致性检查，见 `build-final.log`。随后发布冻结失败：当前工作树缺少 `.run/dac-host/verify-deployed-repairs.py`；未生成可部署的冻结清单，整个构建入口退出码为 1，不能记为发布通过。

初次构建的 Maven 打包也通过，但构建期间修正任务编码导致源码一致性拒绝；已以上述最终源码重新构建，初次产物未用于部署。没有降低校验、复制其他工作树的修复基线或伪造运行 PID。

当时任务注册和真实浏览器预览尚未执行。随后用户明确授权继续处理校验阻塞、重载 59284、注册并预览，且明确禁止实际分配；本次按以下专用运行边界完成。

## 59284 专用校验与重载

核实主入口的 `.run/dac-host` 修复基线绑定 59191 历史产物，与 59284 当前冻结包不是同一运行基线。未复制另一工作树的基线、伪造 PID 或降低 59191 的修复校验；主实例及其发布入口保持原状，本次不能据此宣称 59191 正式发布校验通过。

针对获准重载的 59284，使用本工作树已完整编译的整个后端 JAR，持有同一工作树构建锁生成专用不可变清单。源码指纹与最终专项测试及构建保持一致。对该实例旧冻结包内的 394 项基础同步条目逐项核验：393 项可执行类及资源字节一致；唯一差异为 clean 构建清除未被任何同模块类引用的旧 `P16ProjectSyncTask$Actor` 内部类，当前 P16 可执行类均字节一致。没有拼接来自不同工作树的新模块。

候选 SHA-256：`da051d3687d1079c62c11a0ccd2447875a373c78002d20054343497f3386902d`；清单：原始证据目录 `candidate/manifest.json`。源码指纹：`f3338abbd9b454d89d906f75f5a07eec754142737579188ce491fb749b4f0445`。

重载前核对无活动同步 Run，确认旧 PID 24076 对应原 59284 冻结包后停止；新 PID 7100 使用 `local,dac`，沿用已有受保护连接凭据、禁用 Flyway、自动作业及自动建表，启动预检与健康检查通过。注册使用既有正式账号及配置权限，没有授权扩容。该预览阶段未执行分配或重放；后续执行失败回执见本文首节。
