# ADR-0023：P3-E09业务键、精确比较与状态守卫策略

## 状态

ACCEPTED（Q01、Q02、Q04、Q05、Q06）；Q03、Q07、Q08继续待确认

## 日期

2026-08-13

## 适用范围

- Requirement：全部涉及业务身份、外部同步、当前唯一关系、状态守卫、设备发货/RMA迁移的V1/V2需求。
- 门禁：P3-E09 / AI-MIG-000。
- 前置决策：ADR-0001、ADR-0019、ADR-0020、ADR-0021、ADR-0022。
- 决策输入：`ddl-model-decision-catalog.md`中的P3-E09-Q01、Q02、Q04、Q05、Q06。

## 背景与证据

P3-E09当前核心迁移DDL包含49张表、1,048个字段及381项约束/索引。逐项证据复核发现：

1. 15组业务身份键需要统一确定删除、关闭或归档后的复用规则；否则历史审批、文档、外部回调和来源映射可能指向后续新对象。
2. 25个外部不透明键或摘要字段继承表级`utf8mb4_0900_ai_ci`，与ADR-0022要求的“按原值精确匹配”不一致。
3. `uk_project_company_department_role`和`uk_project_member_role`包含可空列。MySQL唯一键允许多行NULL，无法阻止重复当前关系。
4. 3个CHECK及5个当前唯一生成列表达式直接引用`ACTIVE`、`ENABLED`、`CONFIRMED`、`RESOLVED`等业务状态编码，与PRD“初始化可扩展状态定义+受控状态机”存在冲突。
5. `rma_marked`由RMA编号是否为空生成，只能证明历史记录携带RMA编号，不能表达业务动作方向和数量变化。

## 决策

### 1. 业务身份键永久占用

以下15组业务身份在租户及其声明的来源/公司维度内永久唯一：

|对象|永久业务身份|
|---|---|
|交付件模板|`tenant_id + template_code`|
|设备|`tenant_id + sn`|
|产品|`tenant_id + product_code`|
|合同|`tenant_id + company_code + contract_no`|
|CRM执行单|`tenant_id + source_system + execution_no`|
|销售订单|`tenant_id + source_system + company_code + order_type + order_no`|
|销售订单行|`tenant_id + order_id + line_no`|
|发货包|`tenant_id + source_system + package_no`|
|客户|`tenant_id + customer_code`|
|市场行业组合|`tenant_id + market_code + system_code + expend_code + industry_code`|
|业务文档|`tenant_id + document_code`|
|同步批次|`tenant_id + batch_no`|
|项目|`tenant_id + project_code`|
|项目组合|`tenant_id + portfolio_code`|
|服务事件|`tenant_id + incident_no`|

- 逻辑删除、停用、关闭、归档和迁移重跑均不得释放上述键位。
- 外部系统确实复用编号时，必须使用已确认的来源系统、所属公司或业务类型维度区分，不得覆盖已有业务事实。
- 历史重复记录进入`plt_migration_issue`并引用不可变来源记录；不得修改旧库、静默删除或临时把`deleted`加入唯一键。

### 2. 外部不透明键与摘要精确比较

- 表级默认字符集继续使用`utf8mb4`；名称、说明和中文展示文本继续使用`utf8mb4_0900_ai_ci`。
- 外部不透明ID、来源主键、来源业务键、来源记录键和来源对象ID使用`utf8mb4_0900_bin`，保持原值逐字节语义，不执行大小写归一化。
- 明确由十六进制或ASCII编码表示的摘要字段使用`ascii_bin`；若上游契约未限定ASCII格式，则使用`utf8mb4_0900_bin`。
- 平台受控编码如`source_system`仍按统一字典/编码规则写入；该规则不等于修改外部原始键。
- 唯一索引、幂等查询、对账和迁移映射必须使用相同的比较语义，禁止应用层与数据库层分别采用不同大小写规则。

### 3. 两类当前关系的NULL唯一性空洞

`effective_from`继续允许为空，以保存“来源未提供”的真实事实；不得使用迁移时间、创建时间或当前时间伪造业务生效时间。

#### 项目公司部门关系

- 历史粒度仍保存`project_id + company_code + department_code + relation_role + effective_from`。
- 当前唯一粒度为`project_id + company_code + 规范化部门键 + relation_role`。
- 部门为空时使用专用生成键表达“未指定部门”，使其也参与当前唯一约束；该生成键不回写或替代原`department_code`。
- 当前标记只由稳定事实`deleted = 0 AND effective_to IS NULL`计算，不依赖可扩展业务状态编码。

#### 项目成员任职关系

- 历史粒度仍保存`project_id + user_id + member_role + effective_from`。
- 当前唯一粒度为`project_id + user_id + member_role`。
- 当前标记只由稳定事实`deleted = 0 AND effective_to IS NULL`计算。

历史数据违反上述当前唯一性时进入迁移问题池，由Owner选择保留的当前关系；所有来源行和候选目标均需留痕。

### 4. 状态值域和状态触发守卫

移除以下直接引用固定业务状态码的数据库CHECK：

1. `chk_crm_execution_af`：AF证据状态固定为`CONFIRMED/UNKNOWN`；
2. `chk_scope_active`：`ACTIVE`状态下分配数量必填；
3. `chk_migration_issue_resolution`：`RESOLVED`状态下处理人和时间必填。

替代控制如下：

- AF证据状态由基础平台可配置字典及CRM同步映射校验；未知来源值保留原值并进入同步/迁移问题，不扩展DDL值域。
- DeliveryScope进入标准“生效”语义前，由受控状态迁移强制校验`allocated_qty`，保存状态机版本、校验结果、操作人和操作时间。
- MigrationIssue只能通过受控关闭动作进入标准“已解决”语义；同一事务强制写入`resolver`、`resolved_time`、`resolution_action`及必要证据。
- 当前唯一生成列不得直接依赖可扩展业务状态编码，应依赖稳定标准状态投影、有效期或专用当前标记。

Q03尚未确认5项当前唯一业务事实的完整口径，因此本ADR只批准“不得依赖扩展状态码”的设计原则，不据此自动批准或删除对应5个唯一约束；其最终表达式在Q03关闭后统一裁决。

### 5. RMA生成标记边界

- `business_action_code`、业务动作类型、方向及正负数量是物流/设备动作的权威事实。
- `rma_marked`只作为迁移兼容和查询索引投影，不作为RMA业务类型、动作方向或数量变更的判定依据。
- 历史字符串`null`仅作为已登记的迁移哨兵清洗规则处理；必须保留原值、清洗规则版本和迁移批次。
- 新业务写入禁止使用字符串`null`表达空值。

### 6. 本次未批准内容

- P3-E09-Q03：5项当前唯一业务事实的最终业务口径和生成列表达式。
- P3-E09-Q07：49主键、49租户复合引用键、47同域外键及稳定技术CHECK的批量签署。
- P3-E09-Q08：106个普通索引的候选/最终验收策略。
- 全量`approvedDdlSha256`、迁移Owner、源库水位、脏数据数量、对账结果、回退与切换证据。

上述项目继续保持`DEFER`，不得因本ADR部分批准而推定通过。

## 实施与验证要求

1. 先修改核心迁移DDL，再从DDL重新生成字段目录、约束清单、逐项登记和P3-E09证据包，禁止手工分别维护派生结果。
2. 新DDL必须在隔离MySQL 8.4实例完整执行，并记录DDL哈希、镜像摘要、表/列/约束数量和时间。
3. 增加负向测试：大小写不同来源键可共存、展示名称维持现有比较语义、两类重复当前关系被拒绝、历史NULL生效时间可保留、3个固定状态CHECK已不存在。
4. 状态守卫必须在对应Feature Spec/API/状态机测试中引用Requirement ID；通知成功或接口HTTP成功不能替代业务动作成功。
5. 迁移测试必须证明旧库保持只读、原始来源载荷不变、异常进入问题池且可追溯。

## 拒绝的替代方案

### 所有文本统一使用二进制排序规则

拒绝。会破坏中文名称和展示文本的正常查询体验，也没有业务收益。只对外部不透明键和摘要使用精确比较。

### 强制给历史`effective_from`补迁移时间

拒绝。迁移时间不是业务生效时间，会污染历史责任区间并制造虚假事实。

### 保留固定状态码CHECK并要求每次扩展状态修改DDL

拒绝。会使配置化状态定义失去意义，并造成状态字典、状态机和数据库约束漂移。

### 用`rma_marked`推导动作类型和数量方向

拒绝。RMA编号存在性无法区分发出、退回、返还或再次发放，也不能表达数量的正负方向。

## 影响

- 业务身份键的历史引用保持稳定，迁移重复数据将显式进入问题池。
- 来源幂等和对账采用数据库与应用一致的精确比较语义。
- 项目成员、公司部门当前关系不再因NULL绕过唯一性，但历史缺失时间仍可真实保留。
- 状态扩展不再要求修改固定状态值CHECK；核心业务守卫由受控状态动作执行并留痕。
- P3-E09仍为`OPEN/BLOCKED_BY_MODEL_DECISION`，直至Q03、Q07、Q08及Reviewer签署关闭。
