# F-PROJ-009 项目交付模板配置中心

> 文档状态：`IN_REVIEW`
> Feature Ready：`NOT_READY`（代码重写已收敛；本轮Maven/前端/CI/真实浏览器验证尚未实际执行）
> Requirement：`PM-03`
> Requirement切片覆盖：`PM-03@V1=PARTIAL`
> Owner Context：`PROJ`
> Implementation：`tasks/features/F-PROJ-009.md`
> Technical Plan：`docs/superpowers/plans/2026-09-08-template-business-view-foundation.md`

## 目标与权威

以PRD V1.8 PM-03及现行SDS的Owner、状态、授权、数据/API约束为权威，直接演进现有`ProjectTemplate`身份，不建立第二模板根。2026-09-12专项重写后的正式结构为：

```text
ProjectTemplate
  └─ TemplateDesignerDocument        设计态真值
         │ publish
         ▼
     TemplateCompiler                发布期唯一解析 / 校验边界
         ▼
     TemplateExecutionSnapshot       已发布运行真值（不可变、自包含）
         │
         ├─ schema / compiler / semantic hash verification
         ▼
     Project Runtime                 Stage / Task / Transition / Deliverable / Gate
```

本Feature不再把“模板运行时逐次解析DefinitionRevision”作为目标架构。可复用阶段、任务、交付件、绑定、权限、完成规则、门禁和里程碑仍可作为配置中心的**设计态资产**被多个模板复用；精确引用只在发布编译时解析并冻结。新项目创建与V2运行只消费经过完整性校验的持久化`TemplateExecutionSnapshot`，不得再次查询Designer或当前DefinitionRevision改变已发布语义。

## Scope

- 复用现有`ProjectTemplate`身份、四维匹配、草稿、版本、复制、校验、发布、停用和版本查询入口。
- `TemplateDesignerDocument`作为新草稿的业务设计态真值，直接表达阶段、真实任务、显式转移图、交付件、里程碑、Gate、WorkBinding、PermissionRequirement和RuleAsset；普通编辑不暴露内部revision/provider/schema标识。
- `TemplateCompiler`在发布事务内一次性执行图校验、引用解析、Owner/BusinessView校验、权限需求校验、规则AST校验、Closure校验及运行契约归一化。
- `TemplateExecutionSnapshot`作为V2已发布版本的唯一运行输入，自包含阶段/任务执行契约、转移条件快照、交付件/Gate/里程碑和闭环策略；发布后不可原位修改。
- 发布记录必须同时保存`execution_schema_version`、`compiler_version`和`snapshot_hash`，读取时对Snapshot内容做一致性校验。
- 可复用配置资产保留创建、复制、发布、停用和精确版本能力，但只作为Designer的可选导入/复用来源；Runtime不得对其做活体依赖。
- 显式StageTransition仍是唯一关系真值；前置/后置、流程画布和决策表只是同一图/Rule AST的不同编辑视图。
- PAGE/DYNAMIC_FORM及其他业务视图继续通过PLT受控注册表解析；领域事实、命令和最终授权仍归各Owner。
- Project/ProjectStage/ProjectTask/Deliverable、任务状态机、Owner业务服务、BusinessView/DynamicForm Runtime、权限与审计域保持原业务Owner，不因模板V2重建第二套运行域。

## Out of Scope

- 不重写Project、Stage、Task、Deliverable等业务实例聚合和其Owner事实。
- 不实现S0～S6各领域业务，不重做组织、客户、AST、COM、文件、表单或BPM。
- 不自动迁移已有Project或重写其冻结Stage/Task/Transition/ExecutionContract历史。
- 不从`sortOrder`、S编号、任务码或当前页面结构推导历史缺失图、规则或Owner事实。
- 不把Legacy发布版本通过当前Compiler即时重编译为V2；Legacy升级必须显式复制/升级后重新发布。
- 不以模板配置/编译完成宣称PM-03 FULL或相关既有Feature Done。

## 三层模型

### 1. TemplateDesignerDocument

设计态模型面向业务编排，至少包含：

- 四维匹配条件、BPM定义key引用和ClosurePolicy；
- `StageNode[]`：稳定业务键、名称、start/terminal、业务目标、主WorkBinding、PermissionRequirement、CompletionRule；
- `TaskNode[]`：稳定业务键、所属阶段、父子关系、业务目标、主WorkBinding、PermissionRequirement、CompletionRule及可选GateRef；
- `Transition[]`：稳定边键、from/to、条件RuleAsset、优先级和默认分支；
- `DeliverableRequirement[]`、`Milestone[]`、`Gate[]`；
- `RuleAsset[]`：只允许已注册谓词与`ALL/ANY`组合；树形规则、决策表只是同一AST的编辑投影；
- `DesignerLayout`：纯设计器布局，不参与运行判定。

Designer可以引用已发布共享资产作为“导入/复用来源”，但保存的新V2文档必须能冻结形成自包含发布快照。技术来源ID属于迁移/高级诊断元数据，不是普通业务配置字段。

### 2. TemplateCompiler

Compiler是设计态进入发布态的唯一解释边界：

1. 校验唯一开始节点、收口可达、全图可达、悬空关系及分支唯一性；
2. 解析共享配置资产与BusinessView精确版本并验证同租户、Owner、Provider和支持动作；
3. 校验每个可执行Stage/Task只有一个主WorkBinding，非原生绑定不得退化为手工`TASK_NATIVE_STATUS`假完成；
4. 把PermissionPolicy转为运行所需动作快照，不把模板权限声明当最终授权；
5. 把CompletionRule/TransitionRule/Gate规则统一为Canonical Rule AST并冻结；
6. 校验Deliverable、Milestone、Gate、Closure引用目标均存在且属于当前模板；
7. 生成确定性`TemplateExecutionSnapshot`、`compilerVersion`和`snapshotHash`；
8. `snapshotHash`由当前execution schema定义的**固定语义投影**计算，不依赖未来Compiler重新解释Designer；
9. 任一校验或Owner解析失败则发布整体失败，草稿保持可编辑，不产生半发布版本。

当前execution schema v2使用唯一`TemplateExecutionSnapshotHasher`计算SHA-256；Compiler发布和Runtime读取验证共用该实现，禁止维护两套可漂移的hash算法。

### 3. TemplateExecutionSnapshot

Snapshot是V2已发布模板的运行真值：

- 自包含所有运行所需绑定、规则、权限需求、节点、图和闭环快照；
- 可以保留`source*RevisionId`等来源追溯，但这些字段只用于审计/迁移解释，运行不得再据此查询DefinitionRevision；
- 项目创建只读取通过完整性校验的Snapshot，并投影为现有ProjectStage/ProjectTask/Deliverable/Gate/Transition实例；
- `nodeKey`是编译节点规范身份；当前60-bit `runtimeNodeId`只用于未改造Long契约的兼容投影，不允许作为跨模板全局查询键；
- Stage/Task执行契约允许DefinitionRevision来源ID为空，必须以稳定node key和不可变binding/permission/completion快照解释；
- Transition条件允许只有`condition_snapshot`而无`condition_rule_revision_id`；运行判定直接解释冻结规则；
- 新模板版本只影响之后创建的项目；已有Project保持创建时冻结事实。

### 4. 发布Snapshot完整性

V2发布记录满足以下不变量：

1. `execution_schema_version`必须精确等于当前受支持的Snapshot schema；当前为`2`，不得用`>=2`把未知未来schema当作兼容。
2. `execution_snapshot.executionSchemaVersion`必须与行`execution_schema_version`一致。
3. `execution_snapshot.compilerVersion`必须与行`compiler_version`一致。
4. `snapshot_hash`必须等于对Snapshot schema-v2 canonical semantic projection计算的SHA-256。
5. Runtime读取、兼容revision内容投影和新项目模板匹配都必须经过同一完整性校验。
6. PUBLISHED行一旦出现V2发布元数据却缺少完整ExecutionSnapshot，视为损坏/半发布数据，必须fail closed；不得退回Designer或Legacy解释路径。
7. 完整性校验只验证已发布Snapshot自身，不允许通过当前Compiler重新编译Designer进行“修复”或比较。

## 业务与领域规则

1. 只发布实际配置的阶段，不要求S0～S6全量；售前等裁剪场景仍由显式图表达。
2. 每个节点一个当前主WorkBinding；PAGE/DYNAMIC_FORM是视图来源，不是新的业务状态。
3. 非原生完成消费Owner事实；配置不授予权限。缺失/未知/停用引用与明确未配置严格区分。
4. 不强制模板级BPM Key；只有实际审批引用才校验；新写不保存PMS流程版本。
5. 发布冻结Designer和Execution Snapshot；发布后两者不可覆盖，停用只阻新项目选择。
6. 项目创建、匹配决策历史、图/节点/交付要求/绑定实例化同事务，失败无Project或半成品。
7. 真正Legacy已发布版本可通过Legacy Reader用于历史配置查看；历史Project继续按其已冻结Contract/Transition事实运行。Legacy revision没有V2 Snapshot时不得作为新项目runtime输入，也不得进入新项目候选；只有显式复制/升级成V2并重新发布后才能用于新项目。
8. PUBLISHED行只要带有Designer/V2发布元数据即视为V2语义域；若缺完整Snapshot则拒绝读取为V2兼容内容，不允许“半V2退回Legacy”。
9. 历史缺图不得从sortOrder补造，不擅自停用可写项目；缺失事实只阻断依赖该事实的新运行切换。
10. Designer/Compiler/Snapshot是单向链；禁止发布后回读Runtime实例反向修改Designer，禁止长期Designer+legacy definition rows双写真值。
11. 新项目四维匹配只暴露ACTIVE模板的**最新PUBLISHED且完整性校验通过的V2 revision**；最新版本为Legacy、半V2或hash损坏时该模板不进入候选，不允许“预览成功、创建失败”。

## 业务化任务与编辑规则（需求方2026-09-09确认，V2继续生效）

- S0表示项目基本管理入口与真实准出事实，不为创建、属性维护、指派或团队维护重复生成ProjectTask、上传记录或人工核对任务。
- S1～S6按可独立负责且具有业务交付结果的工作建任务。施工计划制定与提交/审批、实施方案编制与审核分别在同一业务任务内办理；BPM审批任务不复制成ProjectTask。
- 上传文件、采集Log、生成报告、查看版本、就绪/闭环检查等是所属任务或项目的功能，不因存在接口/按钮就成为任务。
- 不默认每个任务必须上传文件，不额外制造阶段资料包或人工里程碑；V2义务不进入V1默认任务。
- 普通编辑只展示阶段、真实任务、业务目标、办理视图、实体关联方式、完成依据和必要交付件；技术来源ID/Provider/原始Schema只在高级诊断中出现。
- 模板只保存实体类型与实例解析规则，不把尚未创建的项目实例ID写成固定模板目标；无Owner完成事实的能力必须明确未接入。

## 兼容与迁移

- `TemplateDefinitionContent`、旧模板元素表、`DeliveryConfigurationRevision`和`TemplateDefinitionReferenceAssembler`进入Legacy/Import边界：用于读取真正Legacy历史版本、把既有草稿解析成一次性的V2 Designer输入和保留来源证据，不再是新Runtime的核心模型。
- V2新项目链中`TemplateDefinitionContent`只允许作为`TemplateExecutionSnapshot.toRuntimeContent()`产生的进程内兼容DTO，用于复用稳定Project实例化代码；它不是发布真值，也不得触发DefinitionRevision回查。
- V2发布后不再为同一版本继续生成一套可独立修改的legacy element rows；如为兼容读取生成投影，必须从**已验证Snapshot**可重建，不能作为写真值。
- 既有Project不迁移；既有Legacy已发布模板默认只读，历史Project按已经冻结的Stage/Task/Transition/ExecutionContract继续解释。需要再次用于新项目时，显式复制为V2草稿并经过Compiler发布。
- `ProjectRuntimeGraphFreezer`的新写路径只接受V2 Snapshot；Legacy freezer写入分支已退役。`ProjectRuntimeGraphResolver`仅为已有历史Project读取已经冻结的legacy事实保留兼容逻辑。
- Legacy→V2转换只能复制已有明确事实；缺图、缺Rule、缺Owner、缺BusinessView版本直接形成阻断项，不做推断。

## API契约

保持单一模板资源，不增加`/template-v2`：

- `GET /project-templates/{id}/draft`：返回DesignerDocument；
- `PUT /project-templates/{id}/draft`：整体保存DesignerDocument，不发布；
- `POST /project-templates/{id}/actions/validate`：执行Compiler dry-run，返回按`path/code/message`定位的问题；
- `POST /project-templates/{id}/actions/publish`：编译并原子写入PUBLISHED Designer + Execution Snapshot + schema/compiler/hash元数据；
- `GET /project-templates/{id}/revisions/{revisionNo}`：V2发布版先验证Snapshot完整性，再返回兼容内容投影；真正Legacy revision继续走历史只读兼容；半V2发布版fail closed；
- 新项目创建内部只使用`ProjectTemplateService#getExecutionSnapshot(templateId, revisionNo)`取得V2运行输入；该读取拒绝Legacy、半V2、schema/compiler/hash不一致的发布版本；
- 现有`PUT /project-templates/{id}`中的`content`只作为迁移期兼容适配入口，必须委托同一Designer规则，不形成第二套业务规则。

## 数据落位

`proj_project_template_revision`新增并拥有：

- `designer_schema_version`、`designer_document`；
- `execution_schema_version`、`execution_snapshot`；
- `compiler_version`、`snapshot_hash`。

数据不变量：

- DRAFT：Designer可编辑，ExecutionSnapshot为空；
- V2 PUBLISHED：Designer和ExecutionSnapshot同时冻结，schema/compiler/hash元数据完整且不可变；
- Legacy PUBLISHED：V2字段可全部为空，只用于历史兼容，不作为新项目匹配候选；
- 半V2 PUBLISHED（存在任一V2发布元数据但缺完整Snapshot）为非法状态，读取与新项目匹配均fail closed；
- `snapshot_hash`是Snapshot schema对应的canonical semantic hash，不包含Legacy来源ID和发布行审计元数据；
- 已有`definition_snapshot`和模板元素表保留历史兼容，不回填猜测。

Project运行表继续复用既有实例结构，并保存编译节点键及权限/完成规则快照；旧DefinitionRevision ID降为可空来源追溯。`templateTaskDefinitionId`等Long兼容字段不具备跨模板全局身份语义。

## 复用边界

- 直接复用ProjectTemplate身份/Revision、四维匹配器、权限、平台幂等审计、Project/Stage/Task实例、Task状态机、ACC交付件Owner API及PLT BusinessView。
- 新增Designer/Compiler/Snapshot，不新增模板根、项目状态机或平行工作区。
- 现有LogicFlow阶段画布与RuleDecisionDesigner继续使用，但其数据源改为DesignerDocument，而不是运行时DefinitionRevision。
- 各领域Owner命令保持原边界；Compiler只能验证/冻结引用，不能替Owner创建完成事实。

## 验收

- AC-01：新草稿以DesignerDocument保存；普通业务配置不需要八类revision ID即可表达完整模板，旧content入口只做兼容适配。
- AC-02：Compiler对标准链、裁剪链及条件分支产生确定Snapshot；循环、悬空、不可达、多默认或无法唯一解析的规则拒绝。
- AC-03：相同Designer与相同解析依赖产生同一snapshot hash；Compiler发布hash与Runtime校验hash使用同一schema-v2 canonical hasher；任一依赖换版只通过重新发布形成新revision。
- AC-04：Stage/Task绑定已注册页面或动态表单后，Snapshot自包含运行契约；项目运行不查询DeliveryConfigurationRevision。
- AC-05：项目创建与PRE-02初始化从`getExecutionSnapshot()`取得运行输入；RuntimeGraph freezer新写只接受V2 Snapshot；任意初始化失败整体回滚；新模板发布不回写旧实例。
- AC-06：Legacy发布版不被当前Compiler静默重编译，不进入新项目候选；显式升级失败时给出具体阻断项。历史Project继续从其已冻结事实读取，不要求回迁Snapshot。
- AC-07：V2 Snapshot若schema/compiler/hash与发布记录不一致，读取和新项目匹配fail closed；半V2发布版不得退回Designer/Legacy解释。
- AC-08：`nodeKey`为规范节点身份；Long runtime identity只作兼容投影，不存在按其跨模板全局反查revision的运行路径。
- AC-09：并发、版本、幂等、授权、MySQL/Flyway、后端定向测试、前端类型/构建和真实浏览器闭环由本轮实际执行并有证据后，才可将Feature提升为Done。

## 当前验证状态

截至2026-09-12专项规格落库：代码级review与回归测试代码已经提交，但当前分支没有open PR、HEAD无GitHub combined status，也无可重跑的PR-triggered workflow run；当前执行环境无法直接联网clone仓库。因此**本轮尚无实际Maven、前端测试/构建、CI或真实浏览器通过证据**。该限制只影响Feature Ready/Implementation Done Gate，不回退已经明确的架构和数据契约。
