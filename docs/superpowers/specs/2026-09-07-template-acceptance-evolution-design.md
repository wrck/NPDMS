# 模板业务规则配置化与独立终验演进设计

> 文档状态：`BASELINE`（需求方于2026-09-08确认书面逻辑设计；过程文档不替代SDS独立复审）
> 日期：`2026-09-07`
> 当前DU：`tasks/delivery-units/DU-20260907-TEMPLATE-ACCEPTANCE-EVOLUTION-DESIGN.md`
> PRD：`CHG-PRD-2026-09-07-018`
> 正式资产：`docs/baseline/prd-v1.8.md`、`docs/decisions/0045-template-business-rules-and-acceptance.md`及相关SDS分册
> Requirement：`PM-03`、`PM-11`、`ACC-03`、`ACC-04`、`CLO-01/02`；关联`PM-01`、`PM-06`、`COM-01`
> 原实现审计截点：`master@4fa4e386`；本文件不成为第二套Feature状态源

## 1. 目标与已确认边界

演进现有Template，而非替换。业务校验适用性、前置、必做/必传、阈值、完成与审批条件由版本化配置决定；终验不因S5或固定任务码强制出现。典型交付流程以预置配置保留。引擎持续强制授权、租户、引用/图有效、受控状态、事务及不可变历史。

| 对象 | 职责 | 不承担 |
|---|---|---|
| Stage | 项目流程关系、单一当前阶段、导航及阶段条件 | 不拥有验收实体，不因编码强制终验 |
| ProjectTask | 工作分解、责任、进度、受控任务状态 | 不成为ACC业务身份的唯一创建前提 |
| WorkBinding | 办理目标、实例解析、业务视图及上下文 | 不授予权限，不复制Owner正文 |
| BusinessView | 受控呈现与调用Owner允许的查询/命令 | 不建立第二套导航、审批或完成状态 |
| AcceptanceActivity | ACC拥有独立验收身份、报告及结果 | 不修改PROJ任务/阶段或COM数量事实 |
| CompletionRule/Gate | 引用、组合真实版本事实并决定节点放行 | 不在查询/求值中创建终验 |

Stage→Task是编排及导航结构，不是其他领域实体所有权链。需要办理终验时配置WorkBinding；只需要终验结果时配置事实引用，无需替换节点主绑定。一节点仍恰有一个当前主绑定，组合视图复用COMPOSITE。

## 2. 存量审计与复用决定

| 现有资产 | 结论 | 演进边界 |
|---|---|---|
| V52模板身份/修订及结构化定义表 | 直接复用身份与冻结原则 | 不是缺少TemplateVersion；发布定义行也是快照 |
| ProjectManualCreationServiceImpl / TemplateInstantiator | 复制增强受影响编排 | 保留项目创建原子性和历史模板ID＋修订号，不重新造实例化器 |
| 任务执行契约、状态机、完成判定 | 复用，按具体契约增强 | 不删除TASK_NATIVE，不用通用完成冒充专项业务 |
| project-templates现有表格编辑页 | 复制增强配置能力 | 先补齐缺失执行契约字段，再建设图/规则/绑定编辑；不是仅美化画布 |
| AcceptanceActivity / 报告版本 / PLT文件事实 | 复用ACC资产 | 新路径解除特定任务和S5创建依赖，保留来源、范围、权限与历史 |
| 固定NEXT_STAGE_CODES、S0～S3强制EXIT、固定终验任务码映射 | 不能作为新通用规则复用 | 改由冻结图和已注册配置决定，不删除稳定S0～S6编码 |
| Stage/Task业务视图注册与新范围触发 | 现行实现不完整 | 先锁契约再由真实Owner接入，不注册测试替身为生产能力 |
| 旧V17验收栈、退役模板入口、已执行Flyway | 不作新实现基础 | 原始数据、历史及迁移解释保留，本DU不修改 |

## 3. 触发、办理和结果引用

配置示例：某已注册审批事实成立 → ACC受控创建/关联终验 → 用户通过绑定视图办理 → ACC形成报告/完成/通过事实 → PROJ或CLO重算显式引用的条件。

- 先复用WorkBinding的引用既有、进入时、首次操作时及只读聚合策略。
- 审批/业务事件触发必须携带真实来源、版本、冻结配置和明确业务意图；同一意图重放幂等，不自动合并不同范围/不同意图。
- readiness、视图刷新、完成规则求值是读判定，不产生创建副作用；审批完成不是终验完成或通过。
- 拒绝“终验完成才允许进入”与“只在进入成功后创建终验”的相互等待。发布检查图和动作依赖的可执行性，不预建通用脚本/工作流引擎。
- 未配置终验条件不生成缺失项；已配置但缺失、失效、无权或Owner不可用不能当作未配置放行。

## 4. ACC独立性与范围协作

推荐复用现有ACC活动/报告，而非创建另一套FinalAcceptance模型。保留既有项目/验收类型身份及报告换版语义，不自行扩展多轮终验；Stage/Task是可追溯调用来源和办理关系，不是唯一业务身份。

PROJ提供可信项目与节点上下文，COM拥有数量和范围版本，ACC拥有验收范围保护事实。终验在S5外办理时不伪造阶段快照；报告不能反推范围，原A报告不能自动覆盖A+B。仅配置要求的适用范围和结果参与门禁；业务范围变化保留旧证据并重算当前依赖。

当前V166必填任务/契约ID和旧阶段快照绑定不足以承载新路径。Q-TPLACC-001必须在Phase 2落实：独立创建/办理API、来源关联、范围绑定身份与时点、执行身份、锁序、事务完成点和加性Schema/唯一键。这里记录设计义务，不把未设计的接口描述为已装配；不通过改空字段绕过身份或权限。

## 5. 已确认实施顺序

1. 模板基础配置：创建、编辑、校验、发布、停用、匹配预览、完整执行契约及实例化正向闭环；修复新增任务契约缺失和发布校验留痕/循环检查问题。
2. 可复用定义与冻结图：类型化版本引用、唯一开始/收口、可达/无环、条件/优先级/默认分支及唯一目标；支持预置售前S0→S4；不引入并行激活多个阶段。
3. 运行时接入：ACC独立业务基础闭环先完成，再接入Stage/Task WorkBinding、BusinessView、受控触发和完成判定；各Owner保持边界，消费者不倒置基础建设顺序。

每一项进入实现前使用原工程链的Feature Ready、单一Technical Plan及独立DU，不把本清单当作另一个任务状态源。当前只交付设计文档，不执行上述应用实现。

## 6. 技术基线、代码组织与约束

- 沿用本仓JDK 25/Maven、Vue3/TypeScript/Node/Corepack/pnpm；MySQL/Redis/Flyway仅由隔离Compose承载。无新增技术依赖。
- PROJ/ACC现有实现与公开契约位于pms-module-project及其-api；COM位于pms-module-commerce；PLT托管通用文件和受控视图注册。禁止跨Owner仓储直查，不修改Yudao基础平台。
- 查询遵守docs/coding/database-query-interface.md；业务配置选择已注册谓词/命令，不接受任意SQL、脚本、前端路径或URL。
- 配置、绑定、规则和报告版本不可覆盖；新配置默认只影响新项目。受控换模、历史迁移或生产操作不在本次授权内。

## 7. 验证与书面审阅

文档验证使用现有入口：

```powershell
git diff --check
git diff --no-index -- docs/baseline/prd-v1.8.md 需求/PRD-项目实施交付管理平台.md
python -B scripts/generate_prd_domain_requirements.py --prd docs/baseline/prd-v1.8.md --output specs/001-project-delivery-platform/domains
python -B scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --output docs/traceability/requirement-matrix.md --coverage-output docs/traceability/requirement-version-coverage.json
```

业务验收集合以SDS20修订018表为准：有S5无终验、无S5有终验、配置初验前置/无前置、重复触发、只读查询零写入、动作依赖环、范围变化、权限拒绝和旧项目冻结。上述应用用例均未在本DU运行，不将文档生成或历史测试当作通过。

书面设计已于2026-09-08经需求方确认。独立验收及其接入须先完成Q-TPLACC-001对应Phase 2差量及适用Gate；不依赖该问题的模板基础配置按自身Feature契约推进，不能被扩大阻断。具体实施计划仍须以相关已锁定规格为输入，本文件不批准Feature Ready、Implementation Done或Release。
