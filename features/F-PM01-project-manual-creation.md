# F-PM01 项目手工创建 Feature Spec

> Feature ID：`F-PM01`
> 文档状态：`SPEC_READY`（DoR 自评通过，待 Technical Plan 评审）
> Requirement ID：PM-01（PRD V1.7 4.2.1，手工创建部分）；消费 PM-03 模板匹配能力
> Owner：PROJ 模块（`pms-module-project`）
> 规格基线：`docs/specification-baseline/manifest.json` @ `28061c3`
> 迁移契约：`domain-entity-migration-contract.json` Project=`STRUCTURED`（gate=`AI-MIG-000`，历史数据迁移不在本 Feature）

## 1. 业务价值

交付工程链第一条 Vertical Slice 的核心入口（工程链第10节）：工程管理部在接口不可用、紧急立项和内部项目场景下手工创建项目，通过四维业务属性匹配并冻结生效模板版本，一次性实例化阶段、任务、里程碑、交付件和门禁，并完成 V1 手动服务经理指派。项目从此进入统一生命周期管理（S0），不再产生绕过模板的旁路项目。

## 2. Scope

1. **项目身份与编码**：`proj_project` 目标表落库；手工项目按 ADR-0020 使用平台编码生成能力（`code_rule_version` 冻结），`project_sequence=0` 自建命名空间；项目编码租户内唯一、创建后不可变，编码序号在命名空间根项目内唯一（根=0，子项目序号属 PM-02）。
2. **四维业务属性**：签约方式、项目类别、实施方式三维护录（受控字典选择），重大项目级别手工场景保持空（`NULL`=不限/不适用）；四维独立保存，禁止混载单字段。
3. **模板消费与实例化**：按四维条件查询命中生效模板列表（复用 F-PM03 匹配器）；用户选择并预览模板，或未选择时使用四维唯一命中的默认模板；冻结模板 revision 及其引用的流程定义版本；按冻结版本实例化阶段（S0～S6）、任务、里程碑、交付件、门禁（含门禁引用行）。
4. **服务经理指派（V1 手动）**：创建时可选指派，或创建后通过 assign-manager 动作指派；一级服务经理（下单办事处）/二级服务经理（实施地点）以 `proj_project_member_assignment` 有效区间记录，支持一级+二级并存。
5. **项目查询**：分页/详情/实例视图（阶段-任务-里程碑-交付件-门禁）/成员视图。
6. **创建幂等**：`POST /pms/projects` 要求 `Idempotency-Key`（tenant+command+actor 作用域），重复提交返回原资源。
7. **留痕**：创建方式（MANUAL）、创建原因、模板加载方式（唯一默认命中/人工选择）、绑定模板编码及版本、流程定义版本、指派前后值（成员区间关闭+新区间）、操作人/时间。

## 3. Out of Scope

- **自动创建**（CRM执行单/ERP销售订单 → 项目、来源映射、幂等 upsert、接口失败补偿）：INT-01 Feature；
- **来源补关联**（接口恢复后将来源单据补关联到现有手工项目）：随 INT-01；
- **主子项目拆分、树移动、进度汇总**（PM-02/PM-04）：后续 Feature；本 Feature 仅支持创建独立根项目（`parent_id=NULL`）；
- **项目业务属性识别与分类**（PM-07 classify 命令）、**服务经理自动指派**（PM-08 V2）、**项目回退/关闭**（PM-10）；
- **阶段推进与门禁执行**（阶段迁移命令、准出门禁校验、`proj_project_stage_snapshot` 生成）：本 Feature 只完成实例化并置初始状态 S0；
- **`ProjectCreated` 集成事件发布**：核心 DDL 无 Outbox/Inbox 基座且 SOL/IMP/ACC/ANA 消费方未建设（工作区规则：无稳定跨模块调用方不建空契约），推迟至首个消费方或事件基座 Feature，追溯矩阵登记 DEFERRED；
- **ProjectTreeScope 深度数据范围**（按树版本/项目角色的服务端过滤）：随 PM-04 权限 Feature 深化；本 Feature 落实功能权限 + 租户隔离服务端校验；
- **合同/订单商业关系对象**（Commerce/DeliveryScope）：合同号在手工表单仅做文本登记（前向列），正式关系随 INT-02；
- 旧 `pms_project` 全量运行面及依赖它的工程实施/验收等旧页面退役：属各自需求 Feature 的 `REVIEW_PER_FEATURE_BEFORE_REUSE` 范围。

## 4. Business Rules（本 Feature 执行的 PRD 规则）

| 规则 | 内容 | 来源 |
|---|---|---|
| BR-1 | 手工与自动创建生成同一种项目业务对象，统一进入 S0；不得形成只存在于手工台账的旁路项目 | PM-01 规则1 |
| BR-2 | 手工创建必须记录创建原因和创建人；受控选择签约方式/项目类别/实施方式三维，重大项目级别保持空或不适用 | PM-01 规则3、场景描述 |
| BR-3 | 项目创建必须绑定一个生效模板；模板按四维独立匹配；手工创建允许从命中模板中选择并预览；未选择时仅可使用四维唯一命中的默认模板；无匹配或同优先级多匹配且未人工选择时不得实例化 | PM-01 规则4、PM-03 规则4 |
| BR-4 | 阶段、任务、里程碑、交付件、门禁及流程定义均以创建时绑定版本实例化或冻结引用；模板后续新版本不影响已建项目 | PM-01 规则4、PM-03 规则3 |
| BR-5 | V1 由工程管理部确认或手动指定服务经理；多省份记录一级（下单办事处）+二级（实施地点）服务经理，单省份记录实施地点服务经理 | PM-01 规则5 |
| BR-6 | 签约方式、项目类别、实施方式、重大项目级别使用独立字典/来源映射，禁止单一"项目类型"混载 | PM-01 规则6、场景描述 |
| BR-7 | 没有可用模板时不得进入 S0（表单阻断并返回冲突/无匹配清单）；组织数据无法确定服务经理时允许暂存后人工指派 | PM-01 异常段 |
| BR-8 | 项目编码按 ADR-0020：项目编码租户内唯一（`uk(tenant_id, project_code)`）、创建后默认不可变，软删除/关闭/归档不释放；编码序号在编码命名空间根项目内唯一（`uk(tenant_id, code_root_id, project_sequence)`，根项目=0、子项目>0 不回收复用）；`code_root_id/project_sequence/code_rule_version` 冻结创建时事实 | ADR-0020 |
| BR-9 | 权限：工程管理部可手工创建、选模板、指派服务经理；服务经理只读查看被指派项目；普通成员无权创建；查询按租户过滤，服务端校验功能权限 | PM-01 权限段 |

## 5. 验收标准

- **WHEN** 工程管理部发起"手动创建项目"
- **THEN** 表单支持录入项目名称、客户、合同号、办事处、实施地点、签约方式、项目类别、实施方式、创建原因；重大项目级别固定为空/不适用且不可编辑
- **AND** 表单内"项目模板"选择项按已录入三维条件实时返回命中启用模板列表，支持预览模板的阶段、任务、里程碑、交付件、门禁清单
- **WHEN** 选择模板并提交（必填校验通过）
- **THEN** 生成平台项目编码，创建项目记录，状态初始化为 S0（待开始），记录创建方式=MANUAL、创建原因、创建人
- **AND** 冻结所选模板 revision 及流程定义版本，实例化阶段、任务、里程碑、交付件、门禁；实例与模板版本绑定，模板后续修改不影响实例
- **AND** 可选同时指派服务经理（一级/二级），或创建后在详情页指派；指派历史以成员区间保留
- **WHEN** 未显式选择模板提交
- **THEN** 仅当三维+空级别条件唯一命中一个生效模板时自动绑定（模板加载方式=唯一默认命中）；无匹配或同优先级多匹配时创建被阻断，返回具体冲突项，不落库不实例化
- **WHEN** 携带同一 `Idempotency-Key` 重复提交创建请求
- **THEN** 返回首次创建的项目资源，不重复生成项目
- **WHEN** 无创建权限用户调用创建/指派接口
- **THEN** 服务端返回权限拒绝（非仅前端隐藏）
- **WHEN** 查看项目详情
- **THEN** 可见基本信息、四维属性、模板绑定（编码/版本/加载方式）、阶段/任务/里程碑/交付件/门禁实例视图、成员（服务经理）列表
- **AND** 以上操作均留痕（操作人/时间、指派前后值）

## 6. 设计契约引用

| 维度 | 契约 | 本 Feature 落地 |
|---|---|---|
| Domain | 02/04：Project 聚合根（身份、四维分类、生命周期、来源映射）；ProjectMemberAssignment 时态关系；08 §4：四维分别保存 | `proj_project` 落库；实例表归 Project 聚合内（PROJ） |
| State | 05：Project 状态 S0～S6+维护；ProjectTask 待分配/待开始/进行中/待验收/完成/关闭 | 创建置 S0；任务实例初始=待分配；阶段推进不在本 Feature |
| Permission | 07：功能权限+租户隔离；一级/二级服务经理角色；ProjectTreeScope | 服务端 `@PreAuthorize` 三级权限（query/create/update/assign）；ProjectTreeScope 深化随 PM-04 |
| API | 10 §5：`POST /projects`（幂等键）、`GET /projects`、`PATCH /projects/{id}`（不改状态/父节点）、`actions/assign-manager`；admin 装配 `/pms` 前缀 | 新路由 `/pms/projects*`（复数），与旧单数 `/pms/project` 守卫可区分 |
| Data | 09 §4.1/4.3：`proj_project`（ADR-0020 编码列）、`proj_project_member_assignment`（区间防重叠）；§12.1 前向扩列 | V57 新建 10 表（见 Technical Plan §2）；`signing_method` 等前向扩列 |
| Event | 11 §5：`ProjectCreated`（Producer PROJ，Consumer SOL/IMP/ACC/ANA） | N/A（本 Feature 不发布，Outbox 基座与消费方未建，DEFERRED） |
| Integration | 12：N/A（CRM/ERP 适配属 INT-01） | N/A |
| 文件 | 13：N/A | N/A |
| 并发/幂等 | 15/16：`Idempotency-Key`（tenant+command+actor）；`IDEMPOTENCY_CONFLICT` 409 | 创建命令幂等记录与重放 |

## 7. 数据变更与存量处置

1. V57 前向迁移新建目标表 11 张（明细见 Technical Plan §2）：`proj_project`（含 `signing_method/contract_no/implementation_location/creation_reason` 等前向扩列与模板冻结引用列）、实例表 5 张（阶段/任务/里程碑/交付件/门禁）+门禁引用行表、`proj_project_member_assignment`、`proj_project_company_department_relation`（下单办事处）、平台编码序列表、API 幂等记录表；字典与菜单登记。
2. V58 冻结旧项目主档运行面：逻辑退役旧"项目总览"创建/更新入口（菜单 18050/18051）并隐藏旧列表菜单，后端仅保留旧链只读端点供存量旧页面选择器过渡；`pms_project` 表数据冻结保留，待 AI-MIG-000 证据判定。
3. 历史迁移文件不改；旧表 `pms_project` 不迁移不删除。
4. 存量依赖面（约 30 处旧页面以 `ProjectApi.getProjectPage` 作选择器）保持只读可用，随各自需求 Feature 评审退役。

## 8. 测试要求（P2 契约 Phase 3 类别，按本 Feature 裁剪）

- 业务规则/聚合单元测试：编码生成与唯一性、四维独立校验、模板冻结不可变、实例化完整性与模板版本绑定、无匹配/多匹配阻断、指派区间；
- API 契约与输入边界测试：创建/查询/详情/实例/成员/assign-manager、三维参数边界、幂等键冲突（409）；
- 服务端授权拒绝测试：无创建权/无指派权/跨租户；
- 状态/异常恢复测试：模板并发停用时创建阻断、幂等重放；
- 幂等与并发冲突测试：并发编码分配、同幂等键并发；
- 数据库约束与迁移测试：uk 约束、迁移可重复执行；
- 事件/外部集成类别：N/A（DEFERRED，登记理由）；
- 真实浏览器验证：创建向导全闭环（三维→模板列表→预览→提交→详情实例视图→指派），按项目 UI 验收要求逐菜单走查截图。

## 9. Open Questions

无阻塞性开放问题。以下为已裁决的设计决策（详见 Technical Plan，评审时可复议）：

1. 办事处以 `proj_project_company_department_relation`（relation_role=`ORDER_OFFICE`）承载；实施地点 V1 以前向列 `implementation_location` 登记文本（多地点拆分属 PM-02）；
2. 合同号 V1 以前向列 `contract_no` 文本登记（正式商业关系随 INT-02 Commerce 对象接管）；
3. 平台编码规则 V1：`PJT` + 年份 + 命名空间流水（`code_rule_version='V1'` 冻结）。

## 10. DoR 自评

| DoR 项 | 状态 |
|---|---|
| Requirement ID / Scope / Out of Scope / 业务价值 | 已明确（第1～3节） |
| Business Rules 与业务验收标准 | 已明确（第4～5节） |
| Domain / State / Permission / API / Data Change / Event / Integration | 已明确或标记 N/A/DEFERRED 并给出理由（第6节） |
| 阻塞型 Open Question | 无（第9节） |

结论：`READY`（进入 Technical Plan）。
