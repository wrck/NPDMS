# F-PROJ-009 项目交付模板配置中心

> 文档状态：`IN_REVIEW`
> Feature Ready：`NOT_READY`（V2编译发布链、运行消费者与历史兼容验证待收口）
> Requirement：`PM-03`
> Requirement切片覆盖：`PM-03@V1=PARTIAL`
> Owner Context：`PROJ`
> Implementation：`tasks/features/F-PROJ-009.md`
> Technical Plan：`docs/superpowers/plans/2026-09-08-template-business-view-foundation.md`

## 目标与权威

以PRD V1.8 PM-03及现行SDS的Owner、状态、授权、数据/API约束为权威，直接演进现有`ProjectTemplate`身份，不建立第二模板根。2026-09-11确认后的目标结构为：

```text
ProjectTemplate
  └─ TemplateDesignerDocument        设计态真值
         │ publish
         ▼
     TemplateCompiler                发布期唯一解析/校验边界
         ▼
     TemplateExecutionSnapshot       已发布运行真值（不可变、自包含）
         ▼
     Project Runtime                 Stage / Task / Transition / Deliverable / Gate
```

本Feature不再把“模板运行时逐次解析八类DefinitionRevision”作为目标架构。可复用阶段、任务、交付件、绑定、权限、完成规则、门禁和里程碑仍可作为配置中心的**设计态资产**被多个模板复用，满足PM-03复用要求；但精确引用只在发布编译时解析并冻结，项目创建与阶段推进只消费`TemplateExecutionSnapshot`，不得再次查询或拼装设计态DefinitionRevision。

## Scope

- 现有`ProjectTemplate`身份、四维匹配、草稿、版本、复制、校验、发布、停用和版本查询入口继续复用。
- `TemplateDesignerDocument`成为新草稿的业务设计态真值，直接表达阶段、真实任务、显式转移图、交付件、里程碑、Gate、WorkBinding、PermissionRequirement和RuleAsset；普通编辑不暴露内部revision/provider/schema标识。
- `TemplateCompiler`在发布事务内一次性执行图校验、引用解析、Owner/BusinessView校验、权限需求校验、规则AST校验、Closure校验及运行契约归一化。
- `TemplateExecutionSnapshot`为已发布版本的唯一运行输入，必须自包含阶段/任务执行契约、转移条件快照、交付件/Gate/里程碑和闭环策略；发布后不可原位修改。
- 可复用配置资产保留创建、复制、发布、停用和精确版本能力，但只作为Designer的可选导入/复用来源；Runtime不得对其做活体依赖。
- 显式StageTransition仍是唯一关系真值；前置/后置、流程画布和决策表只是同一图/Rule AST的不同编辑视图。
- PAGE/DYNAMIC_FORM及其他业务视图继续通过PLT受控注册表解析；领域事实、命令和最终授权仍归各Owner。
- Project/ProjectStage/ProjectTask/Deliverable、任务状态机、Owner业务服务、BusinessView/DynamicForm Runtime、权限与审计域保持原业务Owner，不因模板V2重建第二套运行域。

## Out of Scope

- 不重写Project、Stage、Task、Deliverable等业务实例聚合和其Owner事实。
- 不实现S0～S6各领域业务，不重做组织、客户、AST、COM、文件、表单或BPM。
- 不自动迁移已有Project或重写其冻结Stage/Task/Transition/ExecutionContract历史。
- 不从`sortOrder`、S编号、任务码或当前页面结构推导历史缺失图、规则或Owner事实。
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

Compiler是设计态进入发布态的唯一边界：

1. 校验唯一开始节点、收口可达、全图可达、无环/悬空、非终点有出边及分支唯一性；
2. 解析共享配置资产与BusinessView精确版本并验证同租户、Owner、Provider和支持动作；
3. 校验每个可执行Stage/Task只有一个主WorkBinding，非原生绑定不得退化为手工`TASK_NATIVE_STATUS`假完成；
4. 把PermissionPolicy转为运行所需动作快照，不把模板权限声明当最终授权；
5. 把CompletionRule/TransitionRule/Gate规则统一为Canonical Rule AST并冻结；
6. 校验Deliverable、Milestone、Gate、Closure引用目标均存在且属于当前模板；
7. 生成确定性`TemplateExecutionSnapshot`、`compilerVersion`和`snapshotHash`。相同Designer输入和相同已解析依赖版本必须生成相同hash；
8. 任一校验或Owner解析失败则发布整体失败，草稿保持可编辑，不产生半发布版本。

### 3. TemplateExecutionSnapshot

Snapshot是已发布模板的运行真值：

- 自包含所有运行所需绑定、规则、权限需求、节点、图和闭环快照；
- 可以保留`source*RevisionId`等来源追溯，但这些字段只用于审计/迁移解释，运行不得再据此查询DefinitionRevision；
- 项目创建只读取Snapshot并投影为现有ProjectStage/ProjectTask/Deliverable/Gate/Transition实例；
- Stage/Task执行契约允许DefinitionRevision来源ID为空，必须以编译后的稳定node key和不可变binding/permission/completion快照解释；
- Transition条件允许只有`condition_snapshot`而无`condition_rule_revision_id`；运行判定直接解释冻结规则；
- 新模板版本只影响之后创建的项目；已有Project保持创建时冻结事实。

## 业务与领域规则

1. 只发布实际配置的阶段，不要求S0～S6全量；售前等裁剪场景仍由显式图表达。
2. 每个节点一个当前主WorkBinding；PAGE/DYNAMIC_FORM是视图来源，不是新的业务状态。
3. 非原生完成消费Owner事实；配置不授予权限。缺失/未知/停用引用与明确未配置严格区分。
4. 不强制模板级BPM Key；只有实际审批引用才校验；新写不保存PMS流程版本。
5. 发布冻结Designer和Execution Snapshot；发布后两者不可覆盖，停用只阻新项目选择。
6. 项目创建、匹配决策历史、图/节点/交付要求/绑定实例化同事务，失败无Project或半成品。
7. 历史已发布模板没有V2 Snapshot时通过Legacy Reader保持原解释；只有显式复制/升级成V2时才重新编译，不静默回填。
8. 历史缺图不得从sortOrder补造，不擅自停用可写项目；缺失事实只阻断依赖该事实的新运行切换。
9. Designer/Compiler/Snapshot是单向链；禁止发布后回读Runtime实例反向修改Designer，禁止长期Designer+legacy definition rows双写真值。

## 业务化任务与编辑规则（需求方2026-09-09确认，V2继续生效）

- S0表示项目基本管理入口与真实准出事实，不为创建、属性维护、指派或团队维护重复生成ProjectTask、上传记录或人工核对任务。
- S1～S6按可独立负责且具有业务交付结果的工作建任务。施工计划制定与提交/审批、实施方案编制与审核分别在同一业务任务内办理；BPM审批任务不复制成ProjectTask。
- 上传文件、采集Log、生成报告、查看版本、就绪/闭环检查等是所属任务或项目的功能，不因存在接口/按钮就成为任务。
- 不默认每个任务必须上传文件，不额外制造阶段资料包或人工里程碑；V2义务不进入V1默认任务。
- 普通编辑只展示阶段、真实任务、业务目标、办理视图、实体关联方式、完成依据和必要交付件；技术来源ID/Provider/原始Schema只在高级诊断中出现。
- 模板只保存实体类型与实例解析规则，不把尚未创建的项目实例ID写成固定模板目标；无Owner完成事实的能力必须明确未接入。

## 兼容与迁移

- `TemplateDefinitionContent`、旧模板元素表、`DeliveryConfigurationRevision`和`TemplateDefinitionReferenceAssembler`进入Legacy/Import边界：用于读取历史已发布版本、把既有草稿解析成一次性的V2 Designer输入和保留来源证据，不再是新Runtime的核心模型。
- V2发布后不再为同一版本继续生成一套可独立修改的legacy element rows；如为兼容读取生成投影，必须标识为可重建Projection且不能作为写真值。
- 既有Project不迁移；既有已发布模板默认只读并按旧冻结契约解释。需要再次用于新项目时，可显式复制为V2草稿并经过Compiler发布。
- Legacy→V2转换只能复制已有明确事实；缺图、缺Rule、缺Owner、缺BusinessView版本直接形成阻断项，不做推断。

## API契约

保持单一模板资源，不增加`/template-v2`：

- `GET /project-templates/{id}/draft`：返回DesignerDocument；
- `PUT /project-templates/{id}/draft`：整体保存DesignerDocument并执行结构级校验，不发布；
- `POST /project-templates/{id}/actions/validate`：执行Compiler dry-run，返回按`path/code/message`定位的问题；
- `POST /project-templates/{id}/actions/publish`：编译并原子写入PUBLISHED Designer + Execution Snapshot；
- `GET /project-templates/{id}/revisions/{revisionNo}`：返回只读Designer、Snapshot元数据与兼容内容投影；
- 现有`PUT /project-templates/{id}`中的`content`只作为迁移期兼容适配入口，必须委托同一Designer/Compiler规则，不形成第二套业务规则。

## 数据落位

`proj_project_template_revision`新增并拥有：

- `designer_schema_version`、`designer_document`；
- `execution_schema_version`、`execution_snapshot`；
- `compiler_version`、`snapshot_hash`。

已有`definition_snapshot`和模板元素表保留历史兼容，不回填猜测。Project运行表新增编译节点键及权限/完成规则快照，旧DefinitionRevision ID降为可空来源追溯。

## 复用边界

- 直接复用ProjectTemplate身份/Revision、四维匹配器、权限、平台幂等审计、Project/Stage/Task实例、Task状态机、ACC交付件Owner API及PLT BusinessView。
- 新增Designer/Compiler/Snapshot，不新增模板根、项目状态机或平行工作区。
- 现有LogicFlow阶段画布与RuleDecisionDesigner继续使用，但其数据源改为DesignerDocument，而不是运行时DefinitionRevision。
- 各领域Owner命令保持原边界；Compiler只能验证/冻结引用，不能替Owner创建完成事实。

## 验收

- AC-01：新草稿以DesignerDocument保存；普通业务配置不需要八类revision ID即可表达完整模板，旧content入口只做兼容适配。
- AC-02：Compiler对标准S0～S6、售前S0→S4及条件分支产生确定Snapshot；循环、悬空、不可达、多默认或无法唯一解析的规则拒绝。
- AC-03：相同Designer与相同解析依赖产生同一snapshot hash；任一依赖换版只通过重新发布形成新revision。
- AC-04：Stage/Task绑定已注册页面或动态表单后，Snapshot自包含运行契约；项目运行不查询DeliveryConfigurationRevision。
- AC-05：项目创建从Snapshot冻结实际图及节点契约，任意初始化失败整体回滚；新模板发布不回写旧实例。
- AC-06：Legacy已发布模板/Project读取保持原行为；缺失历史图不推导，显式升级失败时给出具体阻断项。
- AC-07：并发、版本、幂等、授权、MySQL/Flyway、后端定向测试和真实浏览器闭环通过后才可将Feature提升为Done。
