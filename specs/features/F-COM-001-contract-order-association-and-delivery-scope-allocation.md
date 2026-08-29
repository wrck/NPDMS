# F-COM-001 合同订单关联与交付范围分配 Feature Spec

> 文档状态：`CANDIDATE`
> Feature Ready：`NOT_READY（待独立评审）`
> 实施状态：`NOT_STARTED`
> Requirement：`COM-01（V1）`
> Requirement切片覆盖：`COM-01@V1=FULL`
> 关联Requirement：`PM-01`、`PM-02`、`PM-04`、`EQP-01`、`INT-01`、`ACC-01`；不宣称关联Requirement完成
> Owner Context：`COM（合同订单履约）`
> 目标实现载体：`pms-module-commerce`与稳定`pms-module-commerce-api`
> 适用基线：PRD V1.8修订007；SDS Phase 1/2/3 `BASELINE`
> Technical Plan：仅在本Feature达到`BASELINE / READY`后生成

## 1. 业务目标

合同管理员和获授权项目经理可以从ERP权威合同、销售订单和订单行本地副本建立项目交付范围，按项目、订单行、产品或设备类型、数量、实施地点和生效区间分配、调整或释放，并在并发、ERP改单和验收锁定场景下保持数量不超分、历史不覆盖、来源可追溯。

本Feature形成一个可独立验收闭环：

```text
权威副本或待核对人工依据
-> 合同/订单/订单行查询与关联
-> 可分配量预览
-> 范围分配或调整
-> 数量、地点、权限、版本和验收守卫
-> 当前范围与不可变历史查询
-> ERP取消/减量/变更冲突冻结
```

## 2. Scope

### 2.1 包含

- ERP合同、销售订单、订单行本地只读副本及来源状态展示；
- 合同—订单、项目—合同、项目—订单行交付范围的显式关系；
- 合同管理员维护项目关联，项目经理维护本人授权项目的范围；
- 订单行有效数量、已分配数量、可分配数量和分配明细查询；
- 按产品或设备类型、数量、结构化AST站点或受控文本地点、批次形成范围明细；
- 范围预览、分配、调整、释放及ERP取消/减量/变更后的冲突冻结；
- 已进入验收范围的减量守卫，通过ACC公开守卫读取事实；
- 幂等、乐观锁、订单行锁、审计、历史和`DeliveryScopeAssigned/Released` Outbox；
- 对F-PROJ-002既有`DeliveryScopeApi`行为保持兼容；
- 前向迁移到SDS已批准的COM物理模型及V70存量切片受控转换；
- 合同订单与范围管理页面、权限负向和真实浏览器闭环。

### 2.2 外部集成拆分

| 数据/协作 | Owner | F-COM-001职责 | 本Feature不实现 |
|---|---|---|---|
| 合同、销售订单、订单行、产品、数量、金额 | ERP | 冻结本地只读副本、来源键/版本、写入端口、旧版本守卫和降级展示 | ERP认证、HTTP协议、调度、游标、重试、补偿、对账连接器 |
| 项目与客户/销售执行上下文 | PROJ/CRM | 保存稳定引用或只读上下文；CRM不能覆盖ERP商务事实 | CRM适配器、CRM合同审批或回写 |
| Address/Site/SiteLocation | AST | 通过公开API校验结构化站点引用、租户和版本 | AST地点维护、地址树或自动办事处推断 |
| 验收范围锁定 | ACC | 分配减量前调用公开守卫；未知或不可用时禁止减量 | ACC验收流程、审批和归档 |

ERP连接器未完成时，只允许受控种子、受控文件导入端口或经授权人工依据验证本地闭环。人工记录始终保持`PENDING_AUTHORITY`，不得由平台角色改成ERP已确认；只有后续ERP权威版本可确认或纠正。审批意见仅作为PRD要求的审计依据，不新增业务审批节点。

### 2.3 Out of Scope

- ERP、CRM或其他第三方平台的网络适配器与运行闭环；
- CRM合同页面、CRM合同审批、回款、开票、付款或财务统计；
- COM-02及任何V3、`OUT_OF_SCOPE`能力；
- 历史源库批次迁移、真实切换和`AI-MIG-000`授权；
- 项目创建、项目拆分、设备主档、地点主档或验收流程实现；
- 修改Yudao CRM、BPM、系统权限、租户等基础平台实现；
- 以待核对人工数量完成最终范围锁定、验收或正式站点统计。

## 3. 业务规则

### BR-FCOM001-001 权威身份与字段Owner

- 合同业务身份为`tenantId + companyCode + contractNo`；销售订单为`tenantId + sourceSystem + companyCode + orderType + orderNo`；订单行为`tenantId + orderId + lineNo`。逻辑删除、关闭或归档不释放身份键。
- ERP拥有合同、订单、订单行、产品、数量和金额；COM本地仅保存只读副本及来源元数据。业务角色、CRM上下文和范围命令均不能修改ERP Owner字段。
- 同一来源键的旧版本、重复版本幂等返回当前事实；同版本异内容或乱序冲突不覆盖当前副本，并记录待处理证据。
- ERP不可用时展示最近成功副本及截止时间；没有已确认数量时显示待核对，不把人工数量或空值作为最终可分配量。

### BR-FCOM001-002 关联与范围粒度

- 合同—订单和项目—合同均为显式多对多，不从编号后缀、名称或CRM执行单猜测关系。
- 同一实际承接项目节点与同一订单行同一时点至多一条当前`DeliveryScope`主记录；地点、产品/设备类型、数量及批次进入多条`DeliveryScopeDetail`。
- 当前明细数量合计必须等于主记录分配数量；每条明细至少有产品编码或设备类型编码，并有实施地点。
- 形成独立交付责任边界时由PROJ创建独立子项目，COM只把范围分配到稳定项目ID，不创建或移动项目。

### BR-FCOM001-003 地点与数据范围

- 结构化地点保存`siteId/siteLocationId`及来源版本，并在写入时调用AST公开契约校验存在、租户和版本。
- 站点未维护时允许保存文本地点并标记`UNRESOLVED`；该文本不参与正式站点统计、结构化数据权限或自动组织推断。
- 合同管理员只能按被授予的合同关联权限维护关系；项目经理只能维护本人负责或明确授权项目。空项目权限集合返回空，不省略条件扩大范围。
- 查询结果先执行租户、项目范围和字段权限裁剪；无权请求不得通过错误明细泄露合同、订单、分配项目或数量。

### BR-FCOM001-004 可分配量与并发

- 可分配量=`ERP当前有效订单数量 - 其他当前有效分配数量`；待核对、取消、冲突或无权范围不计为可分配。
- 预览不写业务事实；确认必须重新读取订单行来源版本、范围版本、项目权限、AST地点版本和ACC守卫。
- 确认按稳定订单行ID顺序加锁，在同一事务写当前范围、历史、幂等完成点、审计和Outbox；并发超分配最多一个命令成功。
- 整数计量单位拒绝小数；其他单位精度不得超过物理契约的四位小数。单位不明或待核对时禁止分配。

### BR-FCOM001-005 调整、释放与冲突冻结

- 增量、减量和释放必须要求原因、期望版本和幂等键；成功动作关闭原有效区间并新增版本，不覆盖历史。
- 已进入验收的范围不得静默减少。ACC返回已锁定时拒绝普通减量；ACC未知、超时或不可用时失败关闭。
- ERP取消、退货、减量或改单使现有总分配超过有效数量时，保留既有历史并将受影响当前范围投影为冲突冻结，阻止新分配；不得自动删除、按比例削减或把通知送达视为处置完成。
- 冲突解除只能基于新的ERP权威版本和授权范围调整命令，记录来源版本、调整前后数量、原因和意见。

### BR-FCOM001-006 人工降级、审计与事件

- 经授权人工补充至少记录业务键、输入值、依据、原因、操作者和时间，并明确标记`PENDING_AUTHORITY`；不能伪造ERP来源事件、版本或确认状态。
- ERP事实到达后按业务键对账：一致则由ERP事实建立确认副本，不一致则保留人工依据和差异，不静默覆盖历史。
- 创建关联、预览失败、分配、调整、释放、来源变更和冲突处置均记录操作者、来源版本、前后数量、意见、operationId和traceId。
- 成功分配或释放与`DeliveryScopeAssigned/Released`同事务进入COM Outbox；投递失败不回滚已提交范围，消费者按`eventId + scopeVersion`幂等。

## 4. 状态语义

状态编码来自可配置业务字典，代码不得以DDL CHECK固化扩展值；受控命令必须投影下列标准语义：

| 对象 | 标准语义 | 进入守卫 | 允许动作 |
|---|---|---|---|
| ERP副本 | `PENDING_AUTHORITY` | 人工依据或未取得权威数量 | 查询、补充依据、等待ERP对账；禁止正式分配 |
| ERP副本 | `CONFIRMED` | 具有当前ERP业务键、版本及必要数量/单位 | 查询、建立关联、参与可分配量 |
| DeliveryScope | `EFFECTIVE` | 数量为正、明细合计一致、地点合法、权限和版本通过 | 调整、释放、被下游消费 |
| DeliveryScope | `RELEASED` | 受控释放关闭有效区间 | 只读历史 |
| DeliveryScope | `CONFLICT_FROZEN` | ERP取消/减量/变更导致现有范围冲突 | 查询、授权处置；禁止新增分配和静默减量 |

当前唯一性只依赖`deleted=0 AND effectiveTo IS NULL`，不依赖可扩展业务状态编码。

## 5. API与跨Context契约

所有业务REST路径继承`/api/v1/pms`前缀。

| 接口 | 操作 | 契约 |
|---|---|---|
| `/contracts` | `GET` | 按公司、合同号、客户、状态和来源状态分页查询；ERP字段只读 |
| `/contracts/{id}` | `GET` | 返回合同、关联订单、项目关系、来源版本与截止时间 |
| `/contracts/{id}/project-relations` | `POST` | 合同管理员建立显式项目—合同关系；要求幂等键和依据 |
| `/sales-orders` | `GET` | 按公司、订单号、类型、客户、状态分页查询 |
| `/order-lines` | `GET` | 按订单或业务键返回订单行权威数量、已分配/可分配量及来源状态 |
| `/delivery-scopes` | `GET` | 按有权项目/订单行查询当前或历史范围及明细 |
| `/delivery-scopes/actions/preview` | `POST` | 校验但不写入，返回可分配量、占用明细和版本 |
| `/delivery-scopes/actions/assign` | `POST` | 原子分配，要求幂等键、期望来源/范围版本及地点版本 |
| `/delivery-scopes/{id}/actions/adjust` | `POST` | 增减范围；减量前调用ACC守卫 |
| `/delivery-scopes/{id}/actions/release` | `POST` | 关闭当前有效区间并保留历史 |

稳定内部契约：

- `CommerceAuthorityWriteApi`：仅供受信集成ACL或受控导入入口写ERP副本；调用者提供来源系统、来源键、来源版本和发生时间，COM执行Owner及乱序守卫。只预留接口，不在本Feature实现第三方适配器。
- 既有`DeliveryScopeApi`：保持F-PROJ-002的可用切片查询、拆分预览和原子应用语义；实现迁移到目标模型后返回结构与错误兼容，不要求PROJ访问COM表。
- `AssetLocationApi`和`AcceptanceScopeGuardApi`：分别校验地点和验收锁定；未知或不可用时对结构化写入/减量失败关闭。

## 6. 数据与物理Owner

机器可读契约见`specs/features/F-COM-001-physical-contract.json`。目标模型以已批准SDS DDL为上限：

- `com_contract`、`com_sales_order`、`com_sales_order_line`；
- `com_order_contract_relation`、`com_project_contract_relation`；
- `com_delivery_scope`、`com_delivery_scope_detail`；
- COM幂等、审计和Outbox事实沿用模块统一技术契约。

`com_contract_receivable`、发货包、设备物流、CRM执行单合并和历史迁移问题不属于本Feature闭环。实施使用新的前向Flyway迁移，不修改已执行迁移。V70的`com_order_line/com_delivery_scope/com_delivery_scope_detail/com_outbox_event`仅服务F-PROJ-002既有切片；Technical Plan必须锁定一次性受控转换和切换方案，转换前后数量、当前范围和事件语义一致，禁止长期双写或建立第二Owner。

## 7. 旧实现复用边界

详细判定见`specs/features/F-COM-001-legacy-reuse-audit.md`：

- 既有`DeliveryScopeApi`契约和项目拆分回归用例`DIRECT_REUSE`；
- 既有范围服务、DO、Mapper、V70表和前端缺失部分按新包/类及前向迁移`COPY_THEN_ENHANCE`；
- Yudao CRM合同CRUD、CRM审批、权限模型和页面`DO_NOT_REUSE`，全部保持不变；
- 当前不存在可直接复用的合同/销售订单/订单行COM管理页面或ERP适配器。

## 8. UI

- 新增PMS合同订单列表/详情和项目交付范围工作台，不复用或修改Yudao CRM合同路由、权限码、表单和审批页面。
- 列表与详情明确区分ERP权威字段、平台范围字段、来源状态和截止时间；权威字段只读，待核对/冲突冻结有显著状态提示。
- 分配工作台展示订单行数量、已分配、可分配、占用项目明细、地点解析状态和预览版本；服务端拒绝时刷新权威结果。
- 320/768/1024/1440宽度无页面级横向溢出；真实浏览器覆盖查询、关联、预览、分配、超量拒绝、减量守卫、冲突冻结和权限负向。

## 9. 验收标准

- `AC-FCOM001-001`：合同、订单、订单行按批准业务键幂等；旧版本、同版本冲突和跨租户写入不覆盖当前权威副本。
- `AC-FCOM001-002`：ERP字段对合同管理员、项目经理和CRM上下文只读；人工依据明确待核对，不能成为正式可分配量。
- `AC-FCOM001-003`：一个项目可关联多个订单，同一订单行可分配多个项目；当前总分配不超过ERP有效数量，超量返回占用明细且零副作用。
- `AC-FCOM001-004`：主范围与明细合计一致；结构化站点经AST校验，文本降级为`UNRESOLVED`且不进入正式站点统计或结构化权限。
- `AC-FCOM001-005`：合同管理员仅维护有权关联，项目经理仅维护授权项目；空范围、跨租户和无权请求返回空或拒绝且不泄露商务明细。
- `AC-FCOM001-006`：同幂等键同请求重放不重复范围、历史、审计或事件；同键异请求、旧版本和并发超分配只有合法请求成功。
- `AC-FCOM001-007`：调整或释放关闭原有效区间并追加新事实；已验收、ACC未知或不可用时减量拒绝，历史不变。
- `AC-FCOM001-008`：ERP取消/减量/变更造成超分配时范围进入冲突冻结，新分配被阻止；通知结果不改变冲突业务状态。
- `AC-FCOM001-009`：F-PROJ-002既有`DeliveryScopeApi`全部回归通过；V70转换到目标模型前后可用数量、项目范围和事件语义一致且无长期双写。
- `AC-FCOM001-010`：真实MySQL验证身份唯一、当前唯一、明细合计事务守卫、锁竞争和前向升级；查询计划绑定批准候选索引并满足SDS性能基线。
- `AC-FCOM001-011`：真实浏览器完成完整闭环和四档响应式；刷新后事实保持，控制台和网络无未解释错误。
- `AC-FCOM001-012`：本Feature完成不宣称ERP/CRM适配器、INT-01运行闭环、PM/AST/ACC实现、历史迁移、Deployment、SIT、UAT或Release完成。

## 10. 验证与证据计划

- 业务规则单元测试：身份、字段Owner、状态守卫、可分配量、地点、验收锁定和冲突冻结；
- API契约测试：REST、`CommerceAuthorityWriteApi`、兼容`DeliveryScopeApi`及外部Provider失败；
- 真实MySQL：空库迁移、从当前基线升级、重复迁移、唯一约束、锁、幂等、审计/Outbox事务和V70转换对账；
- 权限负向：合同关联、项目范围、空范围、跨租户、敏感商务字段和错误泄露；
- 集成替身：AST存在/失效/超时、ACC锁定/未锁定/未知、ERP新旧/乱序/取消/减量版本；
- 回归：F-PROJ-002项目拆分与既有Commerce测试保持通过，Yudao CRM合同页面/API零修改回归；
- 真实浏览器：第8节完整闭环及四档视口；
- 最终代码质量复审和独立Implementation Done裁决。

## 11. Definition of Ready

| DoR项 | 证据 | 状态 |
|---|---|---|
| Requirement、Scope、Out of Scope和业务价值 | 第1～2节 | PASS |
| 业务规则、状态和权限 | 第3～4节 | PASS |
| API、外部接口和Owner边界 | 第5节 | PASS |
| 数据变化、物理Owner和存量转换 | 第6～7节及机器契约/复用审计 | PASS |
| 验收、验证与真实浏览器 | 第8～10节 | PASS |
| 相关Open Question | 无改变COM-01业务语义、Owner、权限或状态模型的未关闭问题 | PASS |
| 独立Feature Ready裁决 | 待独立裁决官审查本候选提交 | NOT_READY |

结论：`NOT_READY`，仅因独立Feature Ready裁决尚未完成。人工补充保持待核对且只能由ERP权威版本确认，不新增PRD未定义审批节点；第三方平台只冻结接口边界。裁决GO后方可回写`BASELINE / READY`并生成唯一Technical Plan。

检查点：基线=master@259b2612；当前Gate=F-COM-001 Feature Ready；已通过=候选规格及追溯/Phase2/Phase3校验；阻塞=裁决任务读写工具不可用，尚未送审；下一步=工具恢复后读取裁决任务并送审，接受意见后回写Gate。

## 12. 追溯

| Requirement | 本Feature规则/AC | SDS | 实施声明 |
|---|---|---|---|
| COM-01@V1 | BR-FCOM001-001～006；AC-FCOM001-001～012 | COM领域SDS；05/07/08/09/10/11/15/16/19/20；Phase2 COM-01；ADR-0023 | 完整Feature闭环 |
| PM-02 | BR-FCOM001-002/004/006；AC-FCOM001-003/006/009 | F-PROJ-002公开契约 | 只保持DeliveryScope消费契约，不宣称PM-02覆盖 |

## 13. Open Questions

当前无会改变本Feature业务语义、Owner、权限或状态模型的未关闭问题。Java类型、最终Flyway编号、页面组件和查询实现由Feature Ready后Technical Plan基于当前分支锁定；ERP认证和协议细节属于后续INT-01集成Feature，不是本Feature输入。
