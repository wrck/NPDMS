# NPDMS 默认业务实现完整交接

更新：2026-10-08 18:07 UTC。用途：供接手会话直接实施。本文区分已确认需求、已发布代码、已验证结果和未完成候选；不是整体完成声明。

## 1 接手结论与第一步

**完整默认实现尚未完成。不得把工勘样例通过、86 项测试通过或继承 BaseBusinessEntity 当作完整框架通过。**

- 仓库：https://github.com/wrck/NPDMS ，唯一目标分支 `fix/visio-template-followup`。
- 本交接开始时远端代码 HEAD：`cbbeb300098be54241ddef46867896b7ffd61a8d`，tree `4152c1a8d1e315477731d0fd56685797b71e8c02`。这是明确标记 WIP 的命名配置接入保全提交，未编译、未验收。
- 上一个已构建且完成 86 项回归的代码：`33e22ed4f062560a13c7c7a21eab6f35c8c54604`，tree `97c64eef9c5f1443d52624a7bb9e4ad7ec6bad60`。该版普通 PUT 保存真实浏览器未完成。
- 最后完整实机验证的工勘样例及错误诊断：`be854960285fc6d70bbb76228a5cf51c0fd52efd`。只覆盖本文列出的场景。
- 当前任务已停止新功能改动，等待交接。没有待运行的实现工作；本机验收服务保留。
- 接手先读第 2–5 节锁定目标和钩子，再核对 HEAD、工作树、环境与 WIP。先完成默认薄实体的完整链，再接工勘与需求分析，不再从业务特例反推默认框架。
- 本文随后提交的文档提交不会改变上述生产代码基线；以 `git log` 和本文件的代码 SHA 区分文档 HEAD 与最后代码 HEAD。

## 2 历史讨论与后续补充

### 2.1 原始材料和优先级

按最新明确要求优先，旧材料用作追溯，不恢复已经纠正的方向：

1. `docs/decisions/2026-10-07-unified-business-default-implementation.md`：用户已确认目标、允许薄业务类、直接继承主干及 10 月 8 日补充。
2. `docs/reference/unified-business-local-plans-20261006/统一业务模型与公共能力执行计划.md`：9 月 23 日原执行计划，含独立公共能力、继承、无逐实体适配器、引擎可替换、全量范围及验收要求。
3. 同目录 `现状核对与设计方案.md`：原始现状、字段/交付件/规则/审批方案。部分早期操作分发设计后来被“新增直接继承主干”纠正，不能原样作为新主干。
4. `docs/coding/project-business-inheritance.md`：当前实现指南。其“已经有某能力”的文字必须结合本交接缺口核验，不能视为完整链已交付。
5. `docs/generated/default-business-runtime-20261008/README.md`：本轮逐次修复、失败、测试和真实浏览器证据。
6. 同目录 `default-chain-gap-review.md`：2026-10-08 根会话亲自复核的完整链缺口，包含后续 PUT 收敛和命名配置 WIP 状态。
7. `docs/generated/default-business-review-20261008/README.md`、`docs/generated/direct-business-inheritance-20261007/`：此前审查与直接继承增量证据。

用户最初给出的本机历史目录也应保留：
- `M:\AICoding\CodexData\worktrees\4600\NPDMS\.run\unified-business-capabilities-20260922`
- `M:\AICoding\CodexData\worktrees\4600\NPDMS\.run\unified-business-model-plan-20260923`

仓库已有上述历史计划的归档，不要求接手从聊天重新猜需求。若需要原目录中的未归档附件，仅只读检查，不覆盖原件。

### 2.2 从最初要求到当前方向

1. 最初要求全量检查所有在用业务实体是否接入统一模型、视图、交付件，优先解决工勘源错位和交付权限。后来明确：继承 `BaseBusinessEntity` 不等于接入完成，必须拥有真实公共能力。
2. 统一交付件被明确简化：公共上传组件和 CRUD API，保存到同一交付件实体，四键为项目 ID、业务类型、业务实体键、交付件类型。按四键判断最新有效上传；按项目和类型归集，按业务类型和键精确回显。不要求每业务再写上传归集适配器。
3. 用户要求完整默认框架覆盖实体、权限、API、服务、数据层、SQL 和前端；新增实体正常映射后，通过继承即可使用。声明式与否不重要，以达到目标的最简单方式为准。
4. 旧系统参考是 `https://github.com/wrck/PMS.git` 的 `PMS-springmvc` 继承默认接口/实现思路。简单业务动态生成 CRUD，复杂 SQL 继续使用 MyBatis XML。旧“通用关联数据”仅适合低频临时接入，长期业务必须独立实体/表。
5. 用户纠正“不能新增专用 Controller/Service”：**允许薄 Controller、Service、Mapper、前端入口；禁止重复实现公共能力**。专业逻辑通过重载或新增业务操作实现。
6. 用户纠正“最复杂的现成业务即标准”：标准按新系统完整业务目标定义，现有复杂业务可能本身就是特例。
7. 用户要求保留原目录/操作分发/模板推进方案，但新增直接继承主干。新 Controller 直接调用本业务 Service，Service 直接使用实体与 Mapper；普通业务不能再以 ModelContributor、Owner/权限/交付件适配器和旧 operationCode 分发为必需接入条件。
8. 原有项目模板运行时可以消费同一公共能力；独立业务不依赖活动任务、流程令牌或特定引擎。规则、运行时、审批引擎通过公共边界替换，不把旧运行时内部状态塞回业务核心。
9. 用户要求本任务由当前实施者亲自完成，不向其他会话下派实现。此前其他会话已有代码可提交合并；本机子会话后来只获准执行测试和取证，不写源码/断言。

### 2.3 10 月 8 日及后续补充

- 旧字段配置、新扩展字段读写、版本化均应是默认公共能力，不能业务逐个补。
- 不新增冻结版本的额外交付件引用清单；按现有业务类型与实体键查询上传历史。历史保护继续生效。
- 模板任务通过真实业务结果判断完成：例如业务已确认，并且指定类型文件已上传，就满足相应条件；不得伪造状态或调用人工完成冒充自动完成。
- 真实验收必须在本机浏览器完成。用户指定本机目录 `M:\AICoding\CodexData\worktrees\4600\NPDMS`。
- 任务办理区展示须保持原来效果；列表不要自动弹详情。
- 工勘基线是“现场工勘完整采集与分工”已发布模板，不是未绑定模板的基础双列表单。模板 ID `993109090006`，原当前发布修订 `992209220346`。
- 不同模板是字段配置的命名配置。列表、表单、关联子表 Tab 通过配置展示，也可以加载专用界面；CRUD、扩展、交付件与完成判断仍走同一默认链。
- 业务完成规则传入业务实体及判断条件，由公共规则能力计算。不要每新增一个实体就写新的布尔结果 Provider。
- 工勘期限同步项目是业务特例：在工勘继承服务中重载并调用公共保存后追加，或放在明确的事务内后置钩子。默认实现不认识工勘期限字段、不硬编码该同步。
- 用户最后要求先梳理默认职责和前后钩子，再完成完整链；使用最高可用思考深度，停止反复偏离。当前会话未能自行切换或核验 MAX 设置，不能对接手者宣称已切换。
- 用户随后要求完整交接并包含历史讨论、后续补充；最后要求全部待提交代码提交推送。云端本次全部代码已推送为 `cbbeb300`，未验证部分明确 WIP。

关键追溯消息：最初直接继承与薄 Controller 纠正见上述 decisions 文档；最新主会话 `Sentinel_838c9684aa6c8191be84d0c14df21f0b`（职责/钩子/全链）、`Sentinel_82ba74eb8aa481919677fd9f9ad25e04`（MAX/文档依据/亲自实现）、`Sentinel_01bf3cb969f081918a7992e8701ea1b3`（交接）、`Sentinel_1614399819148191b15c7e7801be50e3`（全部提交）、`Sentinel_8cc7e8ea6318819196ec1d053be33dce`（历史与补充）。本机最新补充消息 `01a11c88-0567-76ce-b5c8-5cb26d9e4c5b`、`01a11c91-e4a1-7477-b992-6b6920be5590`、`01a11ca2-d083-72b1-92b8-23c82b661083`。

## 3 默认实现应该包含什么

以下是依据已确认要求整理的目标，不代表当前都已存在。

| 层次 | 默认职责 | 业务只提供的差异 |
|---|---|---|
| 实体与映射 | 稳定身份、租户、项目、版本、审计字段；聚合根/明细/关系元数据；独立表映射 | 实体专业字段、正常持久化映射、关系定义 |
| 权限与范围 | 查询/写入权限、项目范围、字段读写白名单、文件访问；入口限制只能收紧 | 有依据的业务状态与特殊授权限制 |
| API | 标准 CRUD、分页/查询/排序、配置、扩展、交付件、完成判定、回执；统一错误语义 | 薄路由类和真实业务操作，不复制 CRUD |
| 服务与事务 | 一次保存主体、所属明细、扩展值及必要绑定；并发、幂等、审计、事件 | 前后钩子中的专业校验、计算、跨模块业务调用 |
| 数据与 SQL | 简单单表查询动态实现；关联/明细按声明和聚合归属处理 | 复杂场景 Query 对象及 MyBatis XML |
| 字段与命名配置 | 同一字段定义用于列表、表单、详情、关联 Tab；命名配置选择/默认值；不改变权限 | 专业控件或专用展示组件 |
| 扩展字段 | 自动接收、校验、保存、加载、首写定义解析、版本控制；空/false/0/数组不丢失 | 特有校验，不能再写另一份扩展存储 |
| 交付件 | 公共上传 CRUD、四键定位、同一实体保存、历史、项目归集、最新有效上传判定 | 合法的生命周期限制和专业操作 |
| 完成判定 | 实体/关系/扩展事实与条件输入；复用规则能力，返回结果/缺失原因/版本；上传事实公共读取 | 确有特殊算法时重载，普通字段条件无需专用结果类 |
| 前端 | 通用 API、列表/表单/详情/Tab/操作栏/上传/历史；加载、错误、并发、未保存保护 | 局部 slots/组件或完整专业展示，不重复公共状态链 |
| 按需能力 | 内容版本、审批、导入导出等沿已有公开契约复用，不强迫普通实体启用 | 启用配置及领域规则；不扩造审批节点/状态 |
| 运行时接入 | 独立业务先闭环；模板任务使用同一事实与规则，事件唤醒重算 | 一次性引擎接入，不随每个业务增加适配器 |

长期业务正文不进入万能关联表。关联明细服从父聚合事务/权限，不自动开放独立 CRUD。默认框架不创造审批、业务状态、期限规则或任务完成捷径。

## 4 前后钩子与事务契约

以下为实施接续的钩子契约整理。优先复用现有对应方法；新增名称只是建议名称，不得谎称代码已经具备。

| 阶段 | 前置扩展 | 后置扩展 | 约束 |
|---|---|---|---|
| 模型与展示 | 定义字段、关系、操作、默认命名配置 | 调整展示投影 | 不提升字段权限，不另写每实体注册器 |
| 查询 | beforeQuery / 场景 selectPage | afterQuery / afterRead | 框架先限定租户/项目；业务不能移除范围 |
| 保存共用 | beforeSave、normalizeInput、validateBusiness | afterSave | 通用事务包住整个聚合；没有扩展也不需业务处理 |
| 创建 | beforeCreate | afterCreate | 后置业务逻辑应看到主体、明细、扩展均已写入；仍在事务内 |
| 更新 | beforeUpdate | afterUpdate | 比较前后快照、CAS；后置不能只改内存而不持久化 |
| 删除 | beforeDelete | afterDelete | 框架先保护归档/历史/引用；按既有删除语义，不永久清除不可变证据 |
| 自定义命令 | beforeOperation / inBusinessOperation | afterOperation / afterChange | 复用同一权限、幂等、事务与回执 |
| 扩展与关系 | validateExtensions / validateRelations | 保存后读取组合 | 默认负责实际读写；只有真实业务约束才覆盖 |
| 交付件 | validateDelivery / beforeUpload | afterDeliveryChanged | 统一文件/交付实体，不各业务上传后再归集 |
| 完成判断 | 构造允许读取的事实、补充专业事实 | afterEvaluate（只读结果整理） | 条件求值不直接写任务状态，不泄露隐藏值 |
| 提交后 | 无 | afterCommit / 既有 Outbox 消费 | 外部通知在提交后；失败重试，不能伪装数据库事务可回滚外部系统 |

必须落实的顺序：

1. 解析可信身份与模型 → 授权/项目范围 → 幂等请求核验。
2. 开启/加入一个事务 → 锁定主体与并发版本 → 规范化主体/明细/扩展输入。
3. 前置钩子与公共/业务校验 → 保存主体、所属明细、扩展及绑定。
4. 后置业务钩子 → 最终一致性核验 → 审计、回执、Outbox → 提交。
5. 提交后消费事件，按相同版本事实重新计算任务条件。只有正式生命周期服务执行状态变化。

失败规则：任何事务内后置业务逻辑失败，主体/明细/扩展/回执一并回滚。重放已完成幂等请求返回原回执，不再次执行业务前后钩子。未知结果先查回执，不能重新生成请求制造重复业务。

`super` 扩展方式：业务可在自己的重载方法内调用默认保存后追加专业逻辑，但必须由外层事务覆盖两者。当前部分公共 CRUD 为 `final`，只允许钩子；接手须明确并实现可重载接口与受保护执行模板的分工，不能用 final 限制替代用户要求。若采用后置钩子，钩子在默认事务内调用即可，不需要把工勘专有参数和计算写入公共类。

目前实际已有：beforeCreate/afterCreate、beforeUpdate/afterUpdate、beforeDelete/afterDelete、afterRead、validateBusiness、inBusinessOperation、afterChange、validateDelivery、beforeFormWrite 等。**当前 afterCreate 在初始表单绑定之前调用**，职责含业务明细保存；应审查并统一“主体保存后”与“完整聚合保存后”的边界，不能把它直接当成上述目标 afterSave 已实现。

## 5 当前代码的确切缺口

1. `BusinessFieldConfigurationApi.Configuration` 只有 version/fields；Field 只有 label/order/list/search/sort。现 UI 是单份列表配置，不是完整命名列表/表单/关联 Tab。
2. 已有 `EntityPresentationApi`、动态表单发布、扩展定义和值、绑定能力，但默认读取/选择/保存还未统一。WIP /form-options 仅开始接通目录。
3. `SurveyFullCaptureForm.vue` 仍承担默认布局读取、绑定比较和扩展差异拼装。应上收公共组件，只留下专业采集展示/领域命令。
4. `33e22ed4` 已让普通 PUT 支持主体/绑定/扩展/领域命令；前端不再切换 save-form。86 项过，但该版真实普通 PUT 浏览器尚未验收。
5. OBJECT_LIST 默认显示 JSON 文本框，不等于关联子表 Tab。工勘由 SiteSurveyDetails 手工承接旧明细；通用关系读写与配置展示仍待闭合。
6. 默认 runtimeFacts 仅提供 BUSINESS_RECORD_SAVED，runtimeHandlingCompleted 默认为 true；特殊实体覆写布尔事实。尚未完成普通“实体+条件”的公共完成计算接入。
7. 规则能力不是空白：检查 `pms-module-execution-bindings/.../backend/FieldConditionEvaluator.java`、`RuleSemanticEvaluator.java`、`InlineSyncBackend.java`、`EventDrivenBackend.java`。应复用契约，不再建一套判断引擎，不反向依赖具体执行实现。
8. 工勘结构化地点仍在独立区域，不能宣称界面位置完全复原。需求分析仍是通用页面包装，原版本工作区、正文/附件与操作栏未整体验收。
9. 没有完成所有在用实体全量接入审计/迁移；也没有证明一个全新普通实体无需公共专用代码即可走完全部目标能力。

## 6 代码导航

相对仓库路径：
- `pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/business/`：ProjectBusinessService、ProjectBusinessController、DefaultProjectBusinessService、DefaultVersionedProjectBusinessService、BusinessDefaults、BusinessEntityBinding、BusinessFormData、WIP BusinessFormOption。
- `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/entity/`：EntityFormApi、EntityExtensionApi、EntityPresentationApi、实体/修订引用。
- `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/entity/`：EntityFormService、EntityExtensionService、EntityPresentationService、EntityProviderRegistry。
- `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/business/`：DirectBusinessOwners、DirectBusinessRuntimeService、BusinessFieldConfigurationService。
- `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/businessmodel/BusinessModelContractAdvice.java`：公共 409/403/400 映射，现已覆盖继承 Controller。
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/sitesurvey/business/SiteSurveyBusinessService.java`：工勘专业覆盖；原表单/明细能力在相邻 sitesurvey/entity 包。
- `.../engineering/service/requirement/business/RequirementRevisionBusinessService.java`：需求分析存量修订实体接入；原展示选择 RequirementAnalysisPresentations。
- 前端根 `yudao-ui/yudao-ui-admin-vue3/src/`：`components/ProjectBusiness/`、`components/BusinessEntity/`、`components/BusinessView/`、`api/pms/platform/business/`、`views/pms/business/site-survey/`、`views/pms/business/requirement-analysis/`。
- 原专业展示在 `views/pms/delivery-business/site-survey/` 与 `views/pms/delivery-business/requirement-analysis/entity/`。

新业务允许新增薄 Entity/Mapper/Service/Controller/入口，默认框架复用现有独立 SUPPORT；复杂 SQL 用 XML。遵守 AGENTS.md、docs/coding/database-query-interface.md、docs/development.md。禁止任意反射写表、SQL 拼接、跨模块访问私有表。

## 7 已提交改动与验证边界

| 远端提交 | 内容 | 已知结果 |
|---|---|---|
| 07239df1 | 列表初始不自动开详情 | 真实浏览器通过 |
| 6fa3675f | 完整工勘模板复用；原子 createForm；默认表单/展示上下文 | 79 后端测试、构建；完整模板保存/期限/离开保护通过，发现列表确认竞态 |
| 220f871f | 列表确认不依赖异步表单加载 | 24 前端测试/TS/build；取消/双击/确认及交付件自动完成通过 |
| 7baa48c5 | 首个扩展值从已保存绑定解析定义 | 81 后端测试；扩展首存/更新/失败保值/冻结绑定保持通过 |
| be854960 | 默认继承 Controller 公共错误诊断 | 3 MVC；真实 HTTP409/1010006001、原值不变；DONE保持 |
| e62b80f9 | 验收及完整链缺口文档 | 文档提交 |
| 33e22ed4 | 普通 CRUD PUT 统一接收表单/扩展输入 | 86 后端回归、JAR通过；前端25/TS/build通过；真实 PUT 浏览器未做 |
| cbbeb300 | 命名配置 WIP 全部保全 | 未编译/未类型检查/未浏览器验收，不是稳定版本 |

更早的迁移、交付权限、只读事务修复及失败记录在 runtime README，不重写历史失败为成功。

### cbbeb300 的 WIP 内容和接续注意

包含 13 个文件：EntityPresentationApi 新 TypeQuery/listForType；EntityPresentationService 新按类型与项目授权目录；DefaultProjectBusinessService 新 formOptions 与可覆盖类别/字段映射；默认 Controller 新 GET /form-options；BusinessFormOption；工勘映射覆盖；两组测试；前端 API/布局字段类型；新 ProjectBusinessForm.vue；缺口文档。

**新 ProjectBusinessForm.vue 尚未接入 ProjectBusinessPage 或 SurveyFullCaptureForm。** 它尝试处理配置选择、保留输入和绑定载荷，但没有测试，不应直接当可用实现。明确待查：
- 父子 loading/disabled 是否造成加载条件错误或循环；失败能否重新加载。
- 用户只切换配置而不改值时，选择是否实际进入保存载荷。
- 切换配置是否丢失未展示的扩展值；未知新定义与已有定义能否正确衔接。
- false/0/null/数组、只读历史、切换业务/项目时的迟到响应。
- 原工勘专业组件重用与默认公共输入拼装拆分是否真正完成。

此 WIP 是按用户“全部提交推送”保全，不是为了通过测试而临时认定完成。

## 8 环境和数据保护

### 云端本会话

路径 `/workspace/scratch/58df8966edc0/NPDMS-design-review`。代码通过 GitHub 接口提交，因此云端本地 commit ID 与远端 ID 不同，已逐次验证 tree 和文件一致。交接时不要按提交 SHA 差异误判代码未推送；最好在新的干净工作树基于远端分支接续。

最近本地 WIP commit `1b1e647c58275a3bd43e1aaba4715bbcde382364` 对应远端 cbbeb300，tree 相同。云端只有 JDK21；后端基线需 JDK25，所以后端实际编译/测试在本机。前端依赖在上述 UI 目录，根侧前端检查已执行。

### 用户 Windows 本机

- 电脑 W02611-PC。本机正常与连接服务可用是两回事；有多次 offline/exec-server transport disconnected/AppServerBackendRequestError，恢复后必须先实际终端验证，不凭 connected 声称已运行。
- 原工作树 `M:\AICoding\CodexData\worktrees\4600\NPDMS`：原 HEAD 27e04716dd26bc6864e1b47c71396fc0910b4849，已有 34 项改动被完整保留，另有本地提交。**它们不是本次云端 WIP，未混入上述提交。不要 reset/clean/stash 或覆盖。** 接手先查看当前状态，是否整合另按任务边界处理。
- 独立验收工作树 `M:\AICoding\CodexData\worktrees\4600\NPDMS\.run\acceptance-313a63df-20261008`，最后实际运行代码 33e22ed4，detached HEAD；接手重新核验。
- 本机验收会话 ID `01a11ad9-0254-7264-b403-23f78b9a10eb`，仅测试取证权限；当前没有继续执行新业务验收。最后结果 turn `01a11ca5-e416-75eb-a865-381517ca3c4d`。
- 最后报告 Boot PID30152 / 27480；Vite PID28388 / 27481。PID/端口仅为当时快照，接手须验证进程归属，不随意停止其他服务。
- 隔离 MySQL27461、Redis27469；数据库 `npdms_acceptance_delivery_b2e748e6`。容器使用 tmpfs，曾因退出丢失较早夹具；持续保存证据，不假定可恢复。
- 用户原库 `npdms_domain_test` 在另一套原环境（此前 MySQL24306/Redis24379），本次未修改原10项目，不访问/写入生产或 DPPMS。
- 正确浏览器项目路由：`http://127.0.0.1:27481/pms/project-management/project-master-detail?projectId=...`，通过“项目任务”进入办理区。27481 是完整应用，不是测试挂载页。
- Q: 短路径映射是验收期间临时创建的，清理待核查；未证明归属和无依赖前不要删除。
- 启动脚本、环境参数、测试日志在验收工作树 `.run`。曾因缺 NPDMS_REDIS_PORT、Vite mode/变量不匹配启动失败，须沿用已验证配置；本文不抄凭据。

### 可定位样本

| 项目/实体 | 用途/状态 |
|---|---|
| 项目992203060002 / 工勘2108184000184877057 / 任务2108183998603624449 | 较早确认+正确交付件自动DONE，历史样本保护 |
| 项目992203060007 / 工勘2108214968673329154 / 任务2108214967700250626 | 完整模板确认+指定ATTACHMENT自动DONE，版本2/100% |
| 项目992203060008 / 工勘2108219566104469506 | 原完整模板对照；原日期2026-11-30/版本2；不要当新入口通过证据 |
| 工勘2108241489689518081 | 原子创建、首次扩展失败样本，随后已确认，不可改回草稿复试 |
| 项目992203060010 / 工勘2108244823045529601 | 新扩展首写/更新/失败保持及冻结绑定证据 |
| 项目992203060011 / 工勘2108252792462180354 | 33e22ed4 已保留可编辑样本；真实 PUT UI 保存尚未执行 |

合成扩展模板：2108240824003141634，曾发布修订2108240824019918849，字段 extra_acceptanceNote，定义2108241783286603778；后来为只读不升级测试发布了新修订，接手从现存 API 读取当前值，不猜 ID。未改原默认模板。

项目创建/DPPMS迁移是独立任务：旧 insertProject 全链审计、合同→执行单→销售订单关系、十项目迁移、最终客户与购货方双关联、TPL-DELIVERY-FLOW-20260924 等不能与本框架工作混合提交或宣称已完成。

## 9 证据和可复跑入口

### 已回传的证据包

- `libfile_98b495e6f27c819198d904bdfea4aa95`：较早真实 Boot+浏览器公共交付自动DONE。
- `libfile_cb4032fae9ec8191a4abf3021d34a48d`：6fa 完整模板、79测试、确认竞态失败。
- `libfile_cd5ea656f38881919f51869a10d153e1`：220恢复后稳定DONE、原子新建、扩展首存失败。
- `libfile_8b5853f28ec88191a88e53c80160bc6d`：7baa 81测试、新JAR、扩展首存/更新/非法和陈旧保值、历史绑定保持。
- `libfile_d694ea867ed48191a67b4abd937f20e2`：be85 3项MVC、实际409、原值不变、列表/历史只读/DONE保持。

33e 的86测试及JAR证据仍在本机 `.run`，当前交接未取得新的 Library 包；不能将其他提交证据冒充最新 WIP 证据。既有包在主会话已作为附件发送。所有失败过程保留，包括脚本漏必填地点、错误模块未运行测试、服务未就绪等，不计入通过。

### 测试命令模式

这些是可复跑命令模式，不是声称最新 WIP 已运行；先按仓库 docs/development.md 检查工具链和正确模块。

```powershell
# JDK 25，在独立验收工作树；PowerShell 中 -Dtest 列表整体加引号
mvn -pl ':pms-module-platform-support,:pms-module-engineering,:pms-module-platform' -am '-DskipTests=false' '-Dmaven.test.skip=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=DirectBusinessInheritanceTest,SiteSurveyInheritedBusinessTest,RequirementInheritedBusinessTest,DirectVersionedBusinessTest,BusinessModelContractAdviceTest' test
mvn -pl ':yudao-server' -am '-DskipTests' install
```

33e 的已执行计数：16+26+15+26+3=86；cbb WIP 又增加目录用例，不能仍用86作为该版完整预期。WIP还应补 EntityPresentationServiceTest、新命名配置前端测试和真实浏览器。

```bash
# UI目录
node node_modules/vitest/vitest.mjs run --config vitest.pms-file.config.ts src/components/ProjectBusiness/ProjectBusinessPage.runtime.spec.ts src/components/ProjectBusiness/useProjectBusiness.spec.ts src/views/pms/business/site-survey/SurveyFullCaptureForm.runtime.spec.ts
node --max_old_space_size=6144 node_modules/vue-tsc/bin/vue-tsc.js --noEmit
node --max_old_space_size=6144 node_modules/vite/bin/vite.js build --mode test
```

TS 与 Vite 建议串行；曾并行导致 TS 被杀退出137，后续串行退出0。不能将137计为通过。最终还需浏览器及实际 HTTP/数据库读回，不能停在 build。

## 10 接手实施顺序和最终验收

### 实施顺序

1. 对照本交接与原 decisions/计划固定职责和钩子；不重新发明业务规则。
2. 先核查 cbb WIP 可编译性，决定保留/修正哪些候选；全部已提交，有来源，不必从头找。
3. 以默认薄实体贯通元数据→权限→CRUD→主从/扩展→命名列表/表单/Tab→公共上传→条件完成→回执/事件。新增普通实体不改框架分支、不写专属 Provider/Adapter。
4. 明确后置钩子事务和 super 方式，加入失败回滚、幂等只执行一次测试；业务特例不进入默认类。
5. 启用内容版本验证冻结、复制、历史；既有业务/交付件历史不可变，不新增冻结交付清单。
6. 接入第二个此前没有的普通实体，证明纯继承复用；再迁入工勘、需求分析专用展示，只留业务差异。
7. 接入现有模板运行时消费同一实体条件/交付事实，验证独立使用及运行时切换边界。
8. 全量复核在用实体，列明已接入/未接入和专用重载理由，最后更新完成结论。

### 必须逐项有证据的清单

- 新实体正常部署后，薄 Controller/Service/Mapper 可用；框架没有样例编码分支。
- 默认新增/更新同时保存主体、所属明细和扩展；首写、再次写、空值、false/0/数组、失败回滚、重放均正确。
- 命名配置用于列表/表单/关联 Tab，配置变化不授予权限；切换不丢未展示字段和未保存输入。
- 前后钩子实际执行顺序正确；后置失败整体回滚；幂等重试不重复专业动作。
- 公共 API 拒绝跨租户/项目、未开放字段、伪造关联、陈旧版本，错误可识别。
- 公共上传保存同一交付实体，四键隔离；失败/删除/失效不算完成；项目归集与实体回显一致。
- 公共条件求值读取实体、扩展、关系，支持既有 ANY/ALL/COUNT/UNKNOWN 语义；缺失或不可读不误判通过；不写专用完成布尔 Provider。
- 正文条件与交付件条件可组合；保存后未确认不完成、确认但错误类型不完成、正确类型上传自动完成；无人工完成捷径。
- 冻结只读、发布新展示配置不篡改历史绑定、历史文件仍受权限/有效性保护。
- 原工勘完整模板和需求分析工作区视觉/交互保留；列表不自动弹详情；取消离开、重入、重复点击、并发与迟到响应正确。
- 独立业务不需要活动任务；运行时只能消费公共能力，不成为实体核心的必需装配。
- 所有既有未提交用户改动、原十项目、DPPMS和不可变证据保留。

最终报告必须按“默认链已过/实体接入已过/可选能力已过/未完成”分开。没有整个链条的真实证据，不再宣布默认实现已完成。

## 11 可直接给接手会话的任务

> 在 wrck/NPDMS 的 fix/visio-template-followup 上接续。先完整阅读本交接、2026-10-07 decisions 与归档的统一业务模型执行计划。用户目标已经明确：默认框架提供全链公共能力，业务薄继承并通过前后钩子或重载增加差异；不要逐实体补适配器，不要用工勘特例定义标准。当前代码基线 cbbeb300 含未验证的命名配置 WIP，上一个已构建回归基线33e22ed4，完整框架未完成。先梳理并固定默认职责/钩子/事务顺序，再实现默认薄实体的完整链，第二普通实体证明复用，最后接工勘与需求分析。由你亲自实施，使用可配置的最高思考深度；不要声称未实际设置的MAX。保护原工作树34项改动与原项目；本机真实验收使用现有独立工作树和隔离库，先检查状态、回执及服务，不重复未知操作。逐项给证据，禁止将样例或单测通过当作完整框架完成。
