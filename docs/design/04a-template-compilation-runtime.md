# SDS 04a：PM-03 模板设计态、发布编译与不可变运行快照

> 文档状态：`REVALIDATION_REQUIRED`
> 适用基线：PRD V1.8 PM-03（现行正式基线）
> Requirement ID：`PM-03`；直接消费者：`PM-01`、`PM-11`、`CLO-01`、`CLO-02`
> Owner：PROJ / Project Delivery
> 上游：`02-domain-model.md`、`04-module-design.md`、`07-authorization-design.md`
> 下游：`08-data-model.md`、`09-database-design.md`、`10-api-design.md`、`16-exception-and-idempotency.md`
> 需求方决策：2026-09-11确认用编译发布模型替代尚未闭环的DefinitionRevision运行解析链

## 1. 适用与替代关系

本补充分册只重写PM-03模板从设计到运行的技术模型，不改变PRD已经确认的模板版本化、可复用配置、显式阶段关系、工作绑定、权限、完成规则、历史冻结和Owner边界。

对PM-03而言，本分册是以下旧实现措辞的当前解释：

- `02-domain-model.md`中“ProjectTemplateVersion → Stage/Task Definition → WorkBinding/Permission/CompletionRule”的运行结构，改为设计态组合；
- `08-data-model.md`中`DeliveryConfigurationRevision`精确引用仍可作为共享设计资产，但**不再作为项目Runtime的活体依赖**；
- `09-database-design.md`中definition/references表保留历史与设计资产，不再是V2已发布模板的运行写真值；
- `10-api-design.md`现有`/project-templates`资源保持，新增Designer草稿语义与Compiler发布语义，不新建`/template-v2`资源。

既有已发布模板和Project冻结事实不被本设计自动迁移或重写。缺失的历史图、规则、Owner或版本信息不得通过排序、S编号、任务码或当前UI推导。

## 2. 核心架构

```text
ProjectTemplate                       单一模板业务身份
      │
      └── TemplateDesignerDocument    DRAFT设计态真值
                  │
                  │ validate / publish
                  ▼
             TemplateCompiler         唯一解析边界
                  │
                  ▼
          TemplateExecutionSnapshot   PUBLISHED运行真值
                  │
                  ▼
          ProjectRuntimeInitializer
          ├── ProjectStage
          ├── ProjectTask
          ├── ProjectStageTransition
          ├── Stage/Task ExecutionContract
          ├── DeliverableRequirement实例
          ├── Milestone
          └── Gate
```

禁止形成以下长期结构：

```text
Designer -> DefinitionRevision -> TemplateDefinitionContent
         -> DefinitionSnapshot -> Runtime再次解析DefinitionRevision
```

发布后Runtime只解释Snapshot；DefinitionRevision ID可以作为来源证据保留，但不能成为运行必填外键或二次查表条件。

## 3. TemplateDesignerDocument

### 3.1 定位

DesignerDocument是DRAFT版本的当前写真值，面向业务编排而不是数据库行。普通编辑器不得要求用户手工维护`definitionRevisionId`、`workBindingRevisionId`、`permissionPolicyRevisionId`、`completionRuleRevisionId`、Provider ID或原始Schema JSON。

设计态至少包含：

```text
TemplateDesignerDocument
├── schemaVersion
├── match
│   ├── signingMethod
│   ├── projectCategory
│   ├── implementationMethod
│   └── majorProjectLevel
├── processDefinitionKey?
├── closurePolicy?
├── stages[]
├── tasks[]
├── transitions[]
├── deliverables[]
├── milestones[]
├── gates[]
├── ruleAssets[]
└── layout
```

`layout`只服务设计器坐标、折叠和视图偏好，不参与发布判定或运行hash中的业务语义；实现若把layout排除在语义hash之外必须固定canonicalization规则。

### 3.2 Stage / Task

每个可执行Stage或Task只有一个主WorkBinding、一个PermissionRequirement和一个CompletionRule：

```text
StageNode / TaskNode
├── nodeKey               稳定模板内业务键
├── code / name
├── objective?
├── stageKey / parentTaskKey?  （Task）
├── start / terminal            （Stage）
├── workBinding
├── permission
├── completionRule
└── gateRef?
```

`nodeKey`不是数据库revision ID；同一模板revision内唯一，并在Compiler输出和Project冻结契约中保留，作为运行追溯的稳定来源键。

### 3.3 WorkBindingSpec

统一支持PRD六类绑定：

- `STAGE_NATIVE` / `TASK_NATIVE`
- `BUSINESS_OBJECT`
- `BUSINESS_COMPONENT`
- `DYNAMIC_FORM`
- `APPROVAL`
- `COMPOSITE`

非原生绑定必须携带足以解析Owner和目标的受控字段。BusinessView、DynamicForm和BPM只允许引用已发布版本/定义；任意前端代码、脚本或未经注册URL不得进入Designer或Snapshot。

Designer可以通过共享BindingPreset等资产导入配置，但保存时必须保留完整业务语义；共享资产停用只阻新编译，不破坏已发布Snapshot。

### 3.4 PermissionRequirement

模板只声明工作台所需动作或受控策略引用。Compiler冻结所需动作/策略快照；Runtime仍按用户、租户、项目树、目标业务对象、字段/文件范围和实时状态进行服务端授权。模板权限永远不能扩大Owner权限。

### 3.5 RuleAsset

CompletionRule、Transition条件、Gate条件统一使用Canonical Rule AST：

```text
Rule := Predicate | ALL(Rule...) | ANY(Rule...)
```

Predicate只能来自已注册事实目录。树形规则编辑器、决策表、条件面板只是同一个AST的不同投影，不保存可独立修改的第二套规则。

规则引用当前模板节点时使用稳定业务键/代码；引用Owner事实时保存明确的Owner、事实代码、目标解析和所需版本信息。未知、无权、停用或无法判定必须失败关闭；“未配置”与“配置但无法解析”不可混同。

## 4. 可复用配置资产

PRD PM-03要求阶段、任务、交付件、绑定、权限、完成规则、Gate和里程碑能够版本化发布并被多个模板复用。本设计保留该能力，但改变其角色：

- 共享资产是Designer的**authoring source / preset / fragment**；
- 选择共享资产时保存精确来源版本，Compiler据此读取并验证；
- Compiler把有效业务语义内联到ExecutionSnapshot；
- Snapshot可以记录`sourceAssetRevisionId`用于审计，但运行不得依赖来源资产仍处于可查询/启用状态；
- 修改共享资产必须发布新资产版本，并通过新模板revision重新编译才能影响新Project；
- 已有Project和旧Snapshot均不得被共享资产后续变更反向更新。

现有`proj_delivery_definition_revision`/reference模型因此从“Runtime强依赖”降为“共享设计资产+Legacy Import来源”。后续可以继续演进为`SharedRule`、`BindingPreset`、`TemplateFragment`等更贴近业务的资产，而不要求八种kind永久成为运行聚合。

## 5. TemplateCompiler

### 5.1 职责

Compiler只在validate/publish阶段工作，必须是确定性的纯业务编译器；Owner/注册表查询通过明确CompileContext输入，不能在Project运行时重新调用。

必做步骤：

1. 规范化Designer并检查schema版本；
2. 校验阶段/任务/交付件/Gate/里程碑稳定键与引用完整性；
3. 校验唯一start、至少一个可达terminal、全节点可达、无环/悬空、非terminal有出边；
4. 校验多出边的条件、优先级、默认分支可唯一解析；
5. 解析共享资产精确版本并校验同租户、类型和发布状态；
6. 解析BusinessView/DynamicForm/BPM引用并校验Owner/Provider/支持动作；
7. 验证每个可执行Stage/Task恰有一个主绑定；
8. 验证非原生绑定具有真实Owner完成依据，不允许`TASK_NATIVE_STATUS`冒充外部业务完成；
9. 验证PermissionRequirement不越Owner授权边界；
10. 规范化全部Rule AST并验证目标属于当前模板或合法Owner事实目录；
11. 验证ClosurePolicy、Gate和Deliverable引用；
12. 生成自包含ExecutionSnapshot、`compilerVersion`和`snapshotHash`。

### 5.2 失败语义

validate为dry-run，不改变DRAFT；publish必须在同一事务完成“锁定草稿→Compiler→插入PUBLISHED revision→更新模板状态/版本”。任一步失败：

- 不产生PUBLISHED半版本；
- 不清除用户草稿；
- 返回稳定`path/code/message`；
- 不通过兜底默认值、排序或旧任务码补造缺失业务事实。

### 5.3 确定性Hash

`snapshotHash`使用canonical JSON计算SHA-256。Canonicalization至少固定：

- 对象字段顺序；
- 集合中具有显式业务顺序的保留业务顺序；无业务顺序的集合按稳定key排序；
- 不包含数据库主键、发布时间、操作者、审计时间等非语义字段；
- 引用外部共享资产时包含已解析版本/业务内容，使依赖版本变化必然改变hash。

相同Designer语义和相同CompileContext必须得到相同hash。

## 6. TemplateExecutionSnapshot

Snapshot至少冻结：

```text
TemplateExecutionSnapshot
├── executionSchemaVersion
├── compilerVersion
├── matchSnapshot
├── processDefinitionKey?
├── closurePolicySnapshot?
├── stages[]
│   ├── nodeKey
│   ├── business fields
│   ├── bindingSnapshot
│   ├── permissionSnapshot
│   └── completionRuleSnapshot
├── tasks[]
│   ├── nodeKey / parent / stage
│   ├── business fields
│   ├── bindingSnapshot
│   ├── permissionSnapshot
│   ├── completionRuleSnapshot
│   └── gateRef?
├── transitions[]
│   ├── edgeKey / from / to
│   ├── priority / default
│   └── conditionSnapshot?
├── deliverables[]
├── milestones[]
├── gates[]
└── sourceEvidence?       只作审计，不作运行依赖
```

Snapshot发布后只读。读取已发布模板、创建Project、阶段推进和任务工作台不得重新调用`DeliveryDefinitionResolver`或按revision ID重建契约。

## 7. Project Runtime边界

### 7.1 保留

以下既有事实/聚合继续复用，不因模板V2重建：

- Project / ProjectStage / ProjectTask；
- Task tree/WBS与Task State Machine；
- Deliverable、Gate、Milestone实例；
- Owner业务实体和命令；
- BusinessView / DynamicForm Runtime；
- 权限、文件、审计、幂等和领域历史。

### 7.2 运行冻结

Project创建从ExecutionSnapshot直接投影实例：

- Stage/Task的`definition_revision_id`等字段降为可选来源证据；
- 每个Stage/Task ExecutionContract必须拥有稳定`source_node_key`及binding/permission/completion不可变快照；
- ProjectStageTransition必须拥有稳定`source_transition_key`；旧`source_transition_id`可以为空；
- Transition条件可只有`condition_snapshot`，不要求`condition_rule_revision_id`；
- 已有旧Project继续按旧契约读取，V2 Runtime Reader按`execution_schema_version`选择解释器，不用项目级长期feature flag。

### 7.3 不允许的兼容方式

- 同一新模板revision同时维护Designer JSON和一套可独立编辑的legacy row写真值；
- Project创建时再去Definition表解析binding/rule；
- 用伪造revision ID满足旧NOT NULL约束；
- 为了兼容把V2规则降级成TASK_NATIVE手工完成；
- 反向从已运行Project实例生成Designer并覆盖发布历史。

## 8. 数据契约

### 8.1 `proj_project_template_revision`

新增：

| 字段 | 语义 |
|---|---|
| `designer_schema_version` | Designer JSON schema版本 |
| `designer_document` | DRAFT当前真值；PUBLISHED为发布时冻结设计 |
| `execution_schema_version` | Snapshot schema版本；DRAFT可空 |
| `execution_snapshot` | PUBLISHED运行真值；DRAFT为空 |
| `compiler_version` | 生成Snapshot的Compiler版本 |
| `snapshot_hash` | canonical Snapshot SHA-256 |

Legacy行允许这些字段全部为空，不回填推断。

### 8.2 Runtime contract

`proj_project_stage_execution_contract`新增`source_node_key`、`permission_snapshot`、`completion_rule_snapshot`；四个legacy revision ID改为可空。

`proj_project_task_execution_contract`继续保存现有binding/permission/completion快照，并新增/使用`source_node_key`作为V2来源身份；revision ID为可选来源证据。

`proj_project_stage_transition`新增`source_transition_key`，`source_transition_id`允许为空；`condition_snapshot`可以独立存在，revision ID只作legacy来源证据。

Schema变更是前向加性/放宽约束，不删除历史列，不触发历史数据迁移。

## 9. API契约

模板仍只有一个资源：`/api/v1/pms/project-templates`。

| API | 语义 |
|---|---|
| `GET /{id}/draft` | 返回DesignerDocument及模板CAS版本 |
| `PUT /{id}/draft` | 保存DesignerDocument；只做可保存级结构检查，不发布 |
| `POST /{id}/actions/validate` | Compiler dry-run，返回结构化Issue |
| `POST /{id}/actions/publish` | Compiler + PUBLISHED artifact原子提交 |
| `GET /{id}/revisions/{revisionNo}` | 只读Designer + Snapshot元数据/必要投影 |
| `POST /{id}/actions/copy` | 从指定revision复制Designer，生成新的DRAFT，不复制运行实例 |

旧`PUT /{id}`的`content`字段可在迁移期保留为兼容adapter，但必须最终调用同一Designer保存/Compiler验证规则；不得形成第二套发布逻辑。

## 10. Legacy Reader与升级

兼容分三类：

1. **已有Project**：完全不迁移，继续使用已经冻结的Stage/Task/Transition/Contract；
2. **已有PUBLISHED模板且无ExecutionSnapshot**：Legacy Reader按旧精确版本读取；不自动重写；
3. **旧DRAFT/需要再次使用的旧PUBLISHED**：显式执行Legacy Import，把已有明确配置解析为DesignerDocument，Compiler重新校验后发布新revision。

Legacy Import只允许复制能证明的事实。缺图、缺rule、缺Owner、缺BusinessView版本等直接返回阻断Issue。不得按`sort_order`、S编号、历史最大ID或当前代码常量补全。

当新DRAFT已成功保存`designer_document`后，不再把legacy element rows作为写真值继续双写；旧行可以在后续清理版本中删除或转成只读投影，清理不属于本次运行切换前置。

## 11. 并发、权限与事务

- DRAFT保存使用模板CAS version；并发失败返回currentVersion；
- publish锁定模板与DRAFT，Compiler读取的共享资产/BusinessView版本必须固定在本次CompileContext；
- 租户从受信认证上下文取得，不接受Designer正文覆盖；
- create/publish/copy等命令继续遵守Idempotency-Key适用规则；
- Compiler无权直接写Owner业务对象；只读验证跨域契约；
- Project创建必须在一个事务写Project、匹配决策历史、Snapshot来源信息和全部实例事实，任一失败整体回滚。

## 12. 验证要求

在F-PROJ-009 Done前至少验证：

1. Designer结构/round-trip；
2. 图正向与循环、悬空、不可达、多默认失败；
3. Rule AST canonicalization和hash确定性；
4. 共享资产、BusinessView、Owner、权限和完成事实失效/越权失败；
5. publish并发与失败不产生半版本；
6. Snapshot读取不查询DefinitionRevision；
7. V2 Project创建冻结node key、binding/permission/completion及transition condition快照；
8. Legacy published/template/project读取无回归；
9. V228在MySQL 8.4前向执行、重复migrate和约束验证；
10. 当前模板UI真实保存、validate、publish、创建Project和工作区消费浏览器闭环。

通过代码存在、单个单测或Schema可执行均不能单独宣称Feature Done。
