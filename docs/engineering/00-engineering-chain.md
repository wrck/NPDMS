# 项目实施交付管理平台工程化实施链 V1.8

> 文档状态：`BASELINE`<br>
> 唯一正式入口：`docs/engineering/00-engineering-chain.md`<br>
> 适用基线：`docs/baseline/prd-v1.8.md`（已合并修订001—011）；批准依据与审计追溯见`docs/baseline/prd-v1.8-amendment-001-no-manual-project-draft.md`至`docs/baseline/prd-v1.8-amendment-011-inspection-regex-subset.md`<br>
> 基线快照：`docs/baseline/prd-v1.8.md`<br>
> 需求追溯：`docs/traceability/requirement-matrix.md`<br>
> 结构化覆盖：`docs/traceability/requirement-version-coverage.json`<br>
> 替代版本：V1.7（归档于`docs/engineering/archive/00-engineering-chain-v1.7.md`）<br>
> 文档治理：遵循[`docs/README.md`](../README.md)

## 1. 目标与适用原则

将PRD转换为可设计、可实现、可测试、可验证、可发布的工程资产，并让项目在明确但不过度的Gate下推进。

当前工程范围为PRD V1.8附录A.1的100项V1/V2正式需求（主版本V1 53项、V2 47项），按附录A.1.1派生111个正式目标版本切片（V1 53个、V2 58个）；31项已编号V3、5项跨需求演进方向与9项`OUT_OF_SCOPE`仅保留演进或排除追溯，不得进入当前实现。需求基线、领域归属和下游资产状态以冻结快照、结构化覆盖和追溯矩阵为准；V1.7形成的SDS和阶段门禁必须完成V1.8差量重验证后才能恢复放行状态。

本工程链统一遵循：

1. PRD决定做什么，SDS决定如何正确实现，代码和测试不得反向改变业务语义；
2. 设计风险前置，实施事实后置，不可逆操作在真正执行前独立授权；
3. 门禁放在最晚且仍能避免风险的位置，不因未来可能需要而前置阻断；
4. 优先复用Git、CI、自动化测试和正式文档已有事实，不建设重复元数据或证据体系；
5. 每项门禁、模板、状态和自动化必须直接提升业务能力、交付质量、返工控制、不可逆风险控制或验证效率；无法说明收益则不建设；
6. 采用满足当前风险控制的最小方案，真实风险出现后再增量增强，不以形式完整代替业务和功能价值。
7. **收益驱动的工程动作准入与最小防御**：新增或执行哈希、校验、指纹、契约、测试、扫描或防御性代码前，必须说明其防止的具体故障及会改变的后续决策；无法说明、结果不影响行动或已有机制已覆盖的，拒绝执行。确有必要时，只采用与真实风险相称的最小方案；强制项存疑时交Owner裁决。

## 2. 工程权威与文档治理

### 2.1 权威顺序

1. `docs/baseline/`：唯一业务语义基线；
2. 本工程治理基线：定义阶段、资产边界、门禁位置和质量标准，不产生业务规则；
3. `docs/design/`：通过评审的正式SDS；
4. `docs/decisions/`：已批准ADR和待确认问题，不能取代PRD或SDS正文；
5. Feature Spec、Technical Plan和Task：拆解正式设计，不得重新定义领域、权限或核心状态语义；
6. 代码、数据库迁移、API Schema与测试：实现资产，必须追溯到上层正式设计。

冻结快照用于证明PRD未漂移，追溯矩阵用于定位`Requirement -> SDS -> Feature -> Code -> Test`关系；二者均不得产生新业务规则。13份领域需求规格由PRD派生，与PRD冲突时必须重新生成，不允许直接改写派生文件掩盖差异。

### 2.2 文档分类

- 正式业务基线：`docs/baseline/`；
- 正式SDS：`docs/design/`，同一主题只保留一个当前文件；
- ADR与待确认问题：`docs/decisions/`；
- 阶段门禁、评审输入和结论：`docs/engineering/gates/<phase>/`；
- 计划和设计提案：`docs/superpowers/plans/`、`docs/superpowers/specs/`，不得代替正式SDS；
- 外部输入、生成报告和临时材料按`docs/README.md`分类，不得混入正式设计目录。
- 需求来源文档：`需求/`，业务基线PRD版本变更，需要同步变更该目录下PRD文件

每个阶段只允许一个`gate-status.md`表达当前状态。业务语义变化必须回写PRD/CHG；设计结论必须回写SDS，具有长期影响的取舍同时记录ADR；`gate-status.md`只同步放行结果和证据，不能单独替代正式资产。被替代版本必须归档，禁止维护`draft`、`final2`等平行现行副本。

### 2.3 追溯要求

每个Feature、API、数据库变更、事件、工作流和测试必须引用一个或多个Requirement ID。变更后同步更新`docs/traceability/requirement-matrix.md`；无法追溯到PRD或已批准公共技术规则的实现不得进入开发。

Requirement覆盖使用`Requirement ID + 目标版本切片`作为最小键，映射对应Capability分组、纵向业务Feature、Feature依赖、Domain Owner和物理Owner。Capability只用于把同一Requirement切片内的业务义务组织成可检查的覆盖关系，不拥有Ready、Done、Gate、实施证据或独立生命周期，也不得成为Task、代码或发布的完成单元。

Feature是唯一实施和Implementation Done单元。一个Requirement切片可以由多个Feature共同覆盖，一个Feature也可以覆盖多个Requirement切片；Feature完成只关闭自身Scope，不能复制为Requirement完成。Requirement实施覆盖必须从覆盖映射和所需Feature的Implementation Done事实派生：全部业务义务均已映射且全部必需Feature完成时为`COMPLETE`，部分完成或仍有未覆盖义务时为`PARTIAL`，尚无覆盖Feature完成时为`NOT_STARTED`。Deployment、SIT、UAT和Release按发布范围分别记录，不并入Requirement Implementation Coverage。

覆盖映射的机器可读权威位于对应Feature Spec的`Requirement切片覆盖`行，格式为`Requirement@V1|V2=FULL|PARTIAL`；多项使用中文分号分隔。`FULL`表示该Feature完整覆盖该切片，`PARTIAL`表示只覆盖合法子闭环。关联Requirement、支撑需求、依赖和历史说明均不自动产生覆盖。Implementation Done只从`tasks/features/F-*.md`的Feature实施状态读取；缺少对应任务记录时不得仅凭Feature Spec中的实施说明派生完成。`scripts/generate_requirement_traceability.py`据此生成结构化覆盖JSON和Markdown矩阵，禁止再维护脚本人工完成覆盖值。

状态权威按维度唯一：Feature Spec记录Feature Ready；当前实施任务记录Feature Implementation Done并引用Git、CI、测试和运行证据；Technical Plan或其当前任务记录保存Task认领与交接事实。Feature索引、追溯矩阵、CI结果和浏览器证据只作投影或证据，不得成为同一状态的第二来源。

需求缺失、歧义或冲突时不得猜测：将受影响事项标记为`BLOCKED_BY_SPEC`，记录到`docs/decisions/open-questions.md`，并继续推进不依赖该问题的独立工作。业务语义问题必须经确认回写PRD/CHG；实现设计问题回写SDS并在需要时形成ADR；对应`gate-status.md`同步证据后才能解除阻断。

### 2.4 任务最小阅读集

修改设计或代码前，至少读取PRD基线、本工程链、`docs/README.md`、相关SDS、对应Feature Spec和当前Task。只读取与任务有关的章节和证据，不为形式完整重复加载或复制无关材料。

## 3. 总工程链与门禁定位

```text
PRD Baseline
-> SDS Phase 1
-> SDS Review 1
-> SDS Phase 2
-> SDS Review 2
-> SDS Phase 3
-> SDS Baseline
-> Requirement ID + 目标版本切片覆盖映射（Capability无状态）
-> 多个纵向业务Feature并行进入Feature Ready
-> 每个Feature一个当前有效Technical Plan
-> Feature内多个Task在独立分支/Worktree并行实施
-> Feature内Task集成
-> 公共契约、Flyway最终编号和共享文件跨Feature串行合入
-> Feature适用验证与Code Review
-> 单一Feature Implementation Done
-> 选择多个已Done Feature组成Release Candidate
-> Deployment
-> SIT（跨Feature长业务链）
-> UAT
-> Release
```

主链描述平台软件从设计到发布的顺序。满足实施前提且没有相互硬依赖的Feature可以同时推进；同一Feature内按一个当前有效Technical Plan拆分的Task也可以并行。并行开发不改变收口单位：Task先在Feature内集成，公共契约、Flyway最终编号和共享文件只在合入阶段串行收口，每个Feature在最终合入状态完成适用验证后独立产生一次Implementation Done。

Release Candidate是从已Done Feature中选择本次发布范围的集合，不是新Gate或Feature状态；Feature Done只表示具备被选择的资格，不自动进入Deployment。历史数据迁移不是所有版本的固定阶段：只有发布包含历史迁移或数据切换时，`AI-MIG-000`才作为Release前置门禁并在批准窗口内执行。平台业务中的生产网络割接不是软件发布阶段，其任务、方案、审批和闭环遵循CUT领域业务流程；实际设备配置变更另行执行高影响操作授权。

任何未确定事项按以下顺序判断：

1. 是否会改变领域边界、数据模型、API、权限、状态机、一致性策略或核心安全模型；
2. 提前关闭是否能显著降低确定性返工；
3. 最晚在哪个阶段仍能避免不可接受风险。

|风险|默认门禁位置|
|---|---|
|PRD语义不清|需求澄清/PRD变更|
|领域与聚合边界错误|SDS Phase 1|
|数据、API、权限或集成契约错误|SDS Phase 2|
|NFR与运行保障设计缺失|SDS Phase 3|
|Feature实现契约缺失|Feature Ready|
|环境实例未准备|Deployment|
|真实系统联调未通过|SIT|
|业务验收未通过|UAT|
|历史迁移运行证据不足|Migration|
|生产割接条件不足|Cutover|
|生产发布条件不足|Release|

生产实例、最终IP、正式KMS、监控空间、部署窗口、迁移水位及尚未产生的运行报告，在不改变设计时不得前置阻断SDS。

### 3.1 最小Gate契约

|Gate|必需输入|关闭结果|唯一记录位置|允许的下一步|
|---|---|---|---|---|
|PRD Baseline|正式PRD、快照、范围与哈希校验|`BASELINE`或`BLOCKED`|`docs/baseline/`|SDS Phase 1|
|SDS Phase 1/2/3|对应正式分册、Open Question处理结果、必要测试或复审|`READY`或`BLOCKED`|对应`docs/engineering/gates/phase-<n>/gate-status.md`|下一SDS阶段或SDS Baseline|
|Feature Ready|Feature Spec、Requirement版本切片追溯、适用设计契约、依赖与物理Owner、无相关阻断问题|`READY`或`NOT_READY`|Feature Spec|生成一个当前有效Technical Plan并进入并行Implementation|
|Implementation Done|全部Task交付、最终合入代码、适用测试、Code Review、公共契约与迁移验证、追溯更新|`PASS`或`BLOCKED`|当前Feature实施任务记录；Git/CI/测试只作引用证据|进入Release Candidate候选池|
|Deployment|可部署制品、配置契约、应用Schema前向迁移制品及验证结果、环境准备结果|`PASS`或`BLOCKED`|部署流水线或版本化部署记录|SIT|
|SIT/UAT|环境、用例、缺陷和复测结果|`PASS`或`BLOCKED`|测试或发布记录|下一验证阶段|
|AI-MIG-000|真实批次、范围、水位、程序、校验、演练、对账、回退和执行授权|`VERIFIED`或`BLOCKED`|批次迁移计划及运行证据|仅允许该批次迁移/切换|
|Release|DoD、UAT及本次发布适用的部署、迁移和运行条件|`APPROVED`或`BLOCKED`|版本化发布记录|生产发布|

Gate Owner不在本基线中虚构固定角色；必须在对应`gate-status.md`、Feature Spec、迁移计划或发布记录中登记。Feature协调责任是单个Feature实施期内的协作责任，不是新增组织角色或审批权。关闭证据优先引用Git、CI、测试和运行系统自然产生的记录，不要求复制成统一证据包。

## 4. SDS三阶段

### 4.1 Phase 1：总体与业务结构

输出：

- requirement traceability；
- domain model、context map与aggregate boundary；
- system architecture与module design；
- state-machine design与workflow design；
- authorization design与data ownership。

Gate：

- V1/V2/V3/`OUT_OF_SCOPE`边界正确；
- bounded context、核心聚合和唯一Owner清晰；
- workflow与state machine职责分离；
- 功能、数据、操作、字段和临时授权均有落点；
- 不产生PRD外业务角色、审批节点、状态、阈值、门禁或数据Owner。

Phase 1不要求生产实例、生产配置、迁移批次、生产审批记录或运行报告。

### 4.2 Phase 2：实现契约

输出：

- data model与domain entity migration alignment；
- database、API、event和integration design；
- file、exception/idempotency、cache/concurrency design。

Gate：

- 数据Owner、核心字段、关系、约束、版本、快照、历史与审计规则可实现；
- API可追溯Requirement ID，状态变化通过command/transition实现；
- event明确producer、consumer和幂等契约；
- 权限规则能落到查询、command和数据范围；
- 外部集成明确系统Owner、方向、权威字段、映射、来源键、幂等键、timeout、retry、compensation、reconciliation、degradation和audit。

外部HTTP成功或通知送达不能自动解释为业务完成。主数据需要本地查询、关联或历史追溯时，应定义受控同步副本、水位、幂等、对账和降级规则，不能机械地全部改为实时接口调用。

### 4.3 Phase 3：运行与发布保障设计

输出：

- security design；
- audit and observability；
- deployment design；
- performance design；
- test design；
- release and rollback design；
- 当前Phase 3 gate status、自审和必要的独立复审。

Gate：

- NFR具有可实现的技术设计和验证方案；
- 安全、审计、可观测性、发布和回退不存在设计缺口；
- 测试覆盖正常、异常、权限拒绝、幂等和并发；
- 每个运行类门禁说明风险、验收方式和最晚关闭点；
- `READY_FOR_SDS_BASELINE`只表示设计契约足以进入SDS基线，不表示真实环境、性能、迁移、切换或生产发布已经通过。

实际设施名称、环境参数、容量和运行报告只在对应部署、SIT、UAT、迁移或发布阶段关闭。专门证据只有在Git、CI、测试或运行系统无法证明关键风险时才能新增。

## 5. 数据模型基线与P3-E09

### 5.1 数据模型范围

数据模型属于高返工成本资产。进入SDS Baseline前必须冻结逻辑实体、对象到表映射、表、字段、主外键、唯一与检查约束、业务正确性所需索引、候选性能索引、关键表选项、数据类型和空值策略。

当前表结构只允许承载V1/V2正式需求和已确认公共技术能力。V3、`OUT_OF_SCOPE`、明确排除或已后置的对象不得因历史表存在、迁移方便或未来可能使用而提前建表。历史数据来源用于完善字段语义和迁移边界，不能反向创造当前业务对象。

旧库数据元中已存在且含义明确的字段优先复用规范语义；同义字段统一命名。无法证明的旧字段含义必须保留原始值和来源追溯，不得推断映射。

### 5.2 P3-E09边界

P3-E09是“数据模型基线一致性门禁”，只回答当前DDL是否忠实实现已确认的数据模型。

阻断SDS数据模型基线的情况包括：表或字段缺失、语义漂移、关键类型或空值策略不一致、关系与约束缺失、已确认索引集合出现未解释差异、V3/排除/后置对象进入当前DDL，以及无法追溯到正式设计的DDL变更。

通过条件：

- 当前DDL、目标字段目录、映射和校验清单绑定同一哈希；
- 逐项DDL寄存器无`DEFER`；
- MySQL 8.4隔离执行证据与当前DDL同哈希；
- 需求方决策和独立复审结论已经写回正式ADR或Gate；
- 正式制品通过机器校验并形成Git基线提交。

Git保存commit ID、作者、时间和diff，不再建立四角色外部附件、批准JSON、双哈希审批或第二套Git元数据。Q08候选索引仍须在Feature查询计划和P3-E06性能验收中验证。

### 5.3 迁移隔离

P3-E09通过、SDS Baseline、DDL哈希、Git提交、ADR或Schema执行成功均不授权历史迁移、数据切换、生产发布或网络割接。

`AI-MIG-000`独立控制历史迁移与数据切换，但不是所有Release的固定前置项。发布范围不包含历史迁移或数据切换时，本门禁记为`NOT_APPLICABLE`，不得阻断普通功能发布；发布范围包含任一项时，本门禁必须在Release前达到`VERIFIED`，并且迁移或切换只能在该批次已批准的执行窗口内进行。当前不建设通用迁移审批系统；真实迁移批次形成后，再按该批次的源范围、水位、程序版本、校验、演练、对账、回退、执行责任和窗口设计最小门禁。运行事实尚未形成不阻断SDS。

## 6. Feature研发循环

```text
多个Feature并行
├─ Feature A：Feature Ready -> 单一Technical Plan -> Task A1/A2/A3并行
├─ Feature B：Feature Ready -> 单一Technical Plan -> Task B1/B2并行
└─ Feature C：Feature Ready -> 单一Technical Plan -> Task C1/C2/C3并行

每个Feature内部
Task排他认领 -> 独立分支/Worktree实施 -> Task测试/提交/交接
-> Feature内Task集成 -> Feature候选

跨Feature共享资源
Feature候选 -> 更新目标分支 -> 公共契约/Flyway/共享文件串行收口
-> 最终合入状态的Feature适用验证 -> Code Review
-> 单一Feature Implementation Done
```

Feature必须形成可独立验收的业务闭环，相关内容集中，避免跨领域混杂。任何Feature只受与其直接相关的Open Question阻断，无关领域的环境、迁移或运行事实不得使其停摆。

### 6.1 多Feature并行

- Requirement覆盖映射必须先登记Feature依赖、Domain Owner和物理Owner；没有相互硬依赖且各自达到Feature Ready的Feature可以同时实施。
- Feature间存在硬依赖时，后置Feature可以完成不依赖该结果的计划、编码和受控替身验证，但不得在前置契约或事实尚未形成时声明自身Implementation Done。
- 一个Feature完成不等待无关Feature；多个已Done Feature可以进入同一或不同Release Candidate。
- 公共契约的物理Owner只裁决该契约的最终形态和合入顺序，不把公共契约变成新的Capability Gate。

### 6.2 Feature内多Task协调式并行

每个Feature只能有一个当前有效Technical Plan。被替代计划必须归档或明确标记`SUPERSEDED`，不得由多个会话或人员维护互相竞争的现行计划。多人或多个会话可以共同参与一个Feature，但实际排他认领发生在Task层：

- 每个活动Feature在当前Technical Plan或任务记录中登记一个协调责任，负责Task划分、依赖协调、交接和最终集成，不得修改Feature Scope、业务规则或验收标准；
- 一个Task同一时刻只允许一个主要执行者；结对、评审和测试参与者可以并行，但不能形成第二个写入Owner；
- 每个并行写入Task使用独立分支或Worktree，禁止多个执行者同时写同一工作树；
- Task必须声明Feature ID、Task ID、执行者或会话、分支/Worktree、修改边界、依赖、公共契约与数据库影响、验收输出和测试；
- Task移交必须记录最后提交、已完成范围、剩余工作、测试结果和已知失败，再转移唯一执行权；
- Task完成只表示可交给Feature集成，不得更新Requirement覆盖、宣称Feature Done或以局部浏览器证据代替Feature闭环。

Task认领和交接复用当前Technical Plan、任务系统或实施任务记录，不新建Feature认领台账、Task状态平台或第二套Git元数据。

### 6.3 两级集成与串行收口

第一级在Feature内部完成：Task按依赖顺序合入Feature集成分支或等价隔离候选，统一处理接口、模型、权限、状态流和UI衔接，并运行Feature候选的聚焦验证。

第二级在目标分支完成：多个Feature候选仍可等待和准备，但影响公共契约、Flyway版本、共享配置或生成文件的写入必须逐个串行合入。每个候选在合入前更新到最新目标分支，由物理Owner确认公共契约，使用下一个未占用Flyway版本确定最终文件名，完成共享文件更新后再执行最终验证。不得建立Flyway版本预约台账；版本只在实际合入时确定。

每个任务执行：

```text
READ -> PLAN -> IMPLEMENT -> TEST -> SELF-REVIEW -> REPORT
```

实施前报告修改文件、Requirement ID、领域/API/数据库/权限/状态机影响、测试和风险；实施后报告范围、文件、需求覆盖、测试结果、已知限制和后续任务。每个可独立验证的阶段或任务完成后形成一次聚焦提交，不混入无关修改，不自动推送。

## 7. Definition of Ready

Feature进入开发前必须明确：

- Requirement ID、Scope、Out of Scope和业务价值；
- Requirement目标版本切片、Capability覆盖关系及本Feature负责的业务义务；
- Feature Spec、Business Rules和业务验收标准；
- 涉及的Domain、物理Owner、依赖Feature、State、Permission、API、Data Change和Integration Contract；
- 无会改变本Feature设计的阻塞型Open Question。

不涉及的API、数据库、状态机或外部集成明确标记为`N/A`，不得为了模板完整虚构设计。未满足时标记`NOT_READY`。

## 8. Definition of Done

按Feature实际影响范围至少满足：

- 当前Technical Plan中的全部Task已交付并完成Feature内集成，不存在第二个现行计划或未交接写入者；
- Feature候选已更新到最新目标分支，公共契约、共享配置和生成文件已按物理Owner完成最终收口；
- Build、静态检查和适用的Unit/Integration Test通过；
- 业务规则、权限拒绝、异常、幂等和并发场景得到验证；
- API Contract和数据库Migration Test在涉及变更时通过；
- 涉及Flyway时，迁移已使用合入时最终编号，并在最终合入内容上通过空库`migrate/info/validate`、从最近批准基线升级、升级后`validate`和重复`migrate`；
- 审计、日志、Secret保护及数据范围满足设计；
- UI变更通过真实浏览器验证，外部集成通过真实契约或受控替身验证；
- Requirement Traceability和必要文档已更新；
- 最终合入状态重新完成本Feature适用验证，并在唯一实施任务记录中登记Implementation Done及证据引用；
- 不通过降低校验、放宽权限或绕过状态机使测试变绿。

上述每一项均按Feature实际影响适用；不适用时必须标记`N/A`并给出一句理由，不得为了模板完整虚构测试，也不得无理由跳过适用验证。

DoD只证明Feature工程实现完整并具备进入Release Candidate的资格，不等于Requirement全部业务义务、Deployment、SIT、UAT、生产发布、历史迁移或网络割接已经完成或批准。

### 8.1 Feature、Requirement与Release完成边界

- Feature是唯一Implementation Done单元；任何Task、Capability、接口、表、Provider、适配器、测试证据或局部页面均不得单独产生Feature Done。
- Requirement实施覆盖按`RequirementImplementationCoverage(Requirement ID, Target Version)`派生。所有业务义务均已映射且全部必需Feature完成时为`COMPLETE`；部分完成或存在未覆盖义务时为`PARTIAL`；尚无覆盖Feature完成时为`NOT_STARTED`。
- Requirement存在业务语义缺失或冲突时另行记录`BLOCKED_BY_SPEC`，不得用人工完成值覆盖阻断。
- Feature索引和追溯矩阵只展示权威来源的投影；浏览器、CI、Git提交和评审记录只提供证据。任何投影与权威来源冲突时，先纠正投影，不能反向修改权威事实。
- Release Candidate只是本次选择的已Done Feature集合。Deployment、SIT、UAT和Release在版本化发布记录中独立推进，不把通过结果复制回Feature或Capability状态。

### 8.2 实现不可绕过边界

- 生命周期状态只能通过已定义的command/transition变更，不直接写状态字段；
- 服务端授权和数据范围校验不可由前端可见性或客户端参数替代；
- bounded context不得直接访问其他Context的Repository，应通过已定义契约协作；
- 历史、快照、批准版本、审计记录和来源证据不得覆盖更新；
- 明文设备密码、私钥、Token和Secret不得持久化或出现在日志、文档和提交中；
- 通知发送成功、外部HTTP成功或任务被受理，不得在契约未定义时解释为业务完成；
- 不得通过降低校验强度、放宽权限或关闭质量检查来获得通过结果。

## 9. 变更与不可逆操作治理

- 改变Scope、Business Rule、权限语义、业务状态语义或业务验收标准：回到PRD/CHG流程；
- 不改变业务语义但改变数据模型、API、模块边界、状态实现、权限实现或技术架构：通过SDS/ADR变更；
- 不改变正式设计的内部重构和查询优化：通过正常Code Review，不升级为架构治理；
- 独立、低优先级且不影响功能和架构的变更应后置，不阻断当前业务闭环。

状态只表达真实业务生命周期或本工程链明确规定的最小工程Gate。Feature Ready和Implementation Done各有唯一权威来源；Requirement实施覆盖由机器从版本切片覆盖映射和Feature事实派生；Capability不拥有状态。不得通过直接编辑投影视图晋级，也不得为证据、Git确认、Feature认领、Flyway版本预约或尚无真实批次的迁移准备建立状态机。

生产数据迁移、大规模数据修复、数据切换、破坏性DDL、网络设备配置变更、网络割接、生产发布和安全密钥替换属于高影响操作，必须在执行前取得与具体对象和批次绑定的独立授权。任何上游批准不得跨语义继承。

这里的“网络割接授权”特指对生产网络设备实际执行配置变更的授权，不等同于平台中割接任务的业务审批、方案评审或闭环记录；两者不得通过名称相同而互相替代。

## 10. 第一条Vertical Slice选择原则

优先跑通平台骨架和真实业务闭环，而不是先建设全部表或Controller。以下为当前候选范围，不直接构成实现授权：

```text
认证/登录
-> 客户基础数据
-> 手动创建项目
-> 选择项目模板
-> 实例化阶段/里程碑/任务/交付件
-> 人工指派服务经理
-> 项目详情与项目树
-> 权限
-> 审计
```

验证链：

```text
UI -> API -> Application -> Domain -> Repository -> DB -> Permission -> Audit -> Test
```

该Vertical Slice用于验证架构、权限、模板实例化、数据模型和工程链是否真实可实现，不代表全部平台功能完成。

进入实施前必须形成独立Feature Spec，从追溯矩阵选取并列出准确Requirement ID、排除范围和验收标准；如候选范围过大，应按可独立验收的业务闭环拆分，而不是为保持本列表完整而跨域实施。

## 11. 自动化、效果与明确不建设事项

优先自动化Requirement版本切片覆盖、Feature与Task引用、Schema一致性、DDL漂移、Flyway版本唯一性、API兼容性、状态迁移、权限负向测试、Secret扫描、静态检查、单元/集成/迁移测试和审计字段检查。人工评审集中在业务语义、Feature闭环、架构取舍、权限边界、高风险数据模型变化和不可逆操作判断。

工程治理只保留可从现有工具低成本采集的指标：

|指标|最小口径|来源|统计时点|
|---|---|---|---|
|需求追溯覆盖率|已形成`SDS -> Feature -> Code -> Test`链路的当前需求数/当前实施需求数|追溯矩阵|每个Feature完成及阶段复盘|
|设计返工率|因已批准设计缺失或错误而重新打开的任务数/已完成任务数|任务与变更记录|每月或阶段复盘|
|缺陷逃逸数|SIT之后发现且可归因于设计或实现的缺陷数|缺陷系统|每次发布|
|Feature Lead Time|从`READY`到`Done`的自然时间|任务系统|每个Feature及月度汇总|
|无效门禁阻塞数|复盘确认既不改变设计、也不降低不可逆风险的阻断次数|gate-status与复盘记录|每个阶段|

指标维护责任人在对应阶段`gate-status.md`中登记。无法从现有工具稳定取得、采集成本高于改进收益或连续两个阶段不产生行动的指标应删除或简化。

在没有新的真实风险触发前，不建设：

1. 独立工程治理平台或通用SDS审批系统；
2. 第二套Git元数据仓库、通用双哈希审批框架或Git确认状态机；
3. 通用生产证据包系统或与CI、测试、Git重复的证据采集；
4. 尚无真实批次的迁移审批流程、迁移状态机或复杂责任矩阵；
5. 为未来可能需求预建的表、接口、流程和工程状态；
6. 仅为了形式完整而与当前业务功能、系统设计和实现无关的治理资产。
7. Capability Ready/Done、重复Feature状态源、独立Feature认领平台或Flyway版本预约台账。

## 12. V1.8生效与存量资产处理

自V1.8基线提交生效起，V1.7工程链进入`SUPERSEDED`，本文件成为唯一正式工程链。参考稿、实施计划和评审报告不因本文件发布自动晋级为正式设计。

V1.8不自动关闭任何当前Gate。现有Phase 3文档、生成器、validator或模板中如仍把四角色外部签署、迁移批准哈希或尚未产生的迁移运行事实作为SDS模型基线前置条件，应按本工程链完成最小化修订、全量验证和独立复审后，再更新`gate-status.md`。P3-E09不定义迁移批准哈希；在修订完成前保持当前门禁状态，不得直接改成`READY`或`VERIFIED`。

现有业务需求、SDS、DDL和逐项数据模型裁决不因工程链升级而改变；V1.8只调整工程治理方式和阻断位置。任何业务语义或数据结构变化仍必须分别经过PRD或SDS/ADR变更。

存量Feature按以下规则收口：

- 已完成且自身闭环的Feature保留原ID、Implementation Done和证据，不因Requirement仍为`PARTIAL`而重开；
- 已完成的合法子闭环保持Feature Done，Requirement按目标版本切片派生为部分覆盖；
- 在途Feature的目标业务闭环未变时沿原ID和单一当前Technical Plan整体收口；目标闭环已改变时先回到Feature Spec，不允许多个计划并行竞争；
- 未开始Feature按可独立验收的纵向业务结果切分；纯接口、表、Provider、适配器或局部页面并入真实消费者Feature；
- 能独立使用、独立验收且拥有明确公共业务结果的公共能力可以保留为Feature；其消费者接入仍由消费者Feature负责；
- “双轨”等术语只允许描述存量收口期，存量完成后必须删除，不能形成长期并行状态体系。

修订007已建立完整的111个Requirement目标版本切片输入、Feature Spec覆盖声明、Feature Task状态读取、结构化JSON投影和回归校验。追溯矩阵仍是生成投影，不是新的状态权威；任何覆盖调整必须先修改对应Feature Spec，任何实施状态调整必须先修改对应Feature任务记录，再由生成器重建，禁止直接编辑矩阵或JSON晋级状态。
