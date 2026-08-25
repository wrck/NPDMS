# F-PROJ-005 服务经理人工指派与责任分布 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY / GO NPDMS-FPROJ005-FEATURE-READY-20260825-01`
> Requirement：`PM-08（V1）`
> 关联Requirement：`PM-01`、`PM-04`、`PM-11`；不宣称关联Requirement完成
> Owner Context：`PROJ（项目治理）`
> 前置Feature：`F-PROJ-001`、`F-PROJ-002`、`F-PROJ-003`、`F-PROJ-004`均已完成
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`；批准修订`CHG-PRD-2026-08-23-002`
> Technical Plan：Feature Ready独立GO且NPDMS锁定新规格提交后重新生成；既有V1.7实现只作复用审计证据
> 实施状态：`IMPLEMENTATION_COMPLETE / GO`
> 实施证据：NPDMS `25230ce` Task 1～6、自动化、全新MySQL V1～V84、单/多租户运行态、真实浏览器四档响应式与独立整改复审

## 1. 目标

工程管理部按订单办事处、实施地点及当前有效组织范围筛选候选人，在任意实际项目节点上人工指派或改派主责/协同服务经理。系统保存不覆盖历史的生效区间，提供项目树责任分布，并可靠通知被指派人。V1不自动决定服务经理。

## 2. Scope

### 2.1 包含

- 候选人员查询：仅返回当前有效、与项目公司及确认办事处部门处于同一有效组织范围的用户；
- `area_code + area_level`到`system_dept.code`的映射只建议办事处部门，授权用户必须人工确认；
- 对任意实际项目节点指派主责或协同服务经理，不以树深度推导角色；
- 主责改派要求原因，以服务端同一事务时间关闭原有效区间并新增关系，禁止覆盖历史；
- 根项目范围内的服务经理责任分布查询；
- 按当前有效主责事实重算`assignment_status`；
- 权限、租户、ProjectTreeScope、幂等、版本并发、审计和通知重试；
- 复用Yudao/Element Plus组件的响应式管理界面。

### 2.2 不包含

- PM-08 V2自动推荐、自动指派或规则管理；
- PM-11项目经理指派实现；
- PM-05/PM-06/PM-09及订单、计划、任务执行改造；
- 固定两级项目树、自动继承到子孙节点或用成员关系授予树权限；
- 新通知表、短信/邮件渠道、独立审批流；
- Deployment、SIT、UAT和Release。

## 3. 业务规则

### BR-FPROJ005-001 候选人与办事处

- 办事处就是组织架构部门，稳定编码只使用`system_dept.code`；不得新增`office_code`或同义当前值。
- 站点不绑定公司或办事处。地点以AST站点的国家、省、市、区、详细地址及`area_code/area_level`承接；地点可在工勘、安装阶段维护。
- 单省项目以实施站点区划映射办事处候选；多省根/统筹节点以订单办事处候选，属地节点以其站点区划候选。映射缺失或冲突时不回退到其他办事处，由授权人员补齐/确认后再指派。
- PROJ不得直查SYSTEM表。候选查询通过SYSTEM公开应用API，条件为租户、项目公司、确认的部门编码、启用用户和同一有效公司-部门范围；当前基线未定义“服务经理资格”主数据，因此不得臆造全局角色作为过滤条件。
- 候选列表只提供合法集合，最终人选始终由有权限的工程管理人员确认。

### BR-FPROJ005-002 责任关系

- `member_role=SERVICE_MANAGER_L1`表示订单办事处/统筹责任，`SERVICE_MANAGER_L2`表示实施地点/属地责任；L1/L2不是项目树深度。
- `assignment_type=PRIMARY/COLLABORATOR`区分主责与协同。每个项目节点、服务层级及责任站点在任一时刻最多一个有效主责；协同可多名。
- 根项目、子项目或更深节点均按实际`project_id`保存关系；不得自动生成子孙节点成员关系，也不得由责任关系推导ProjectTreeScope。
- 初次指派与改派均记录非空原因。V1只支持立即生效：客户端不提交`effectiveFrom`，服务端生成唯一事务时间，改派用该时点同时写原主责`effective_to`与新关系`effective_from`；预约生效后置。旧行不可更新除结束生效区间和技术审计字段外的业务快照。
- 被指派用户、公司、部门编码、层级、类型、站点及责任说明保存当时快照。离职、停用、跨公司部门、站点不属目标节点或范围缺失时拒绝新指派；已有历史关系保留。

### BR-FPROJ005-003 assignment_status唯一口径

- `ASSIGNED`当且仅当当前项目节点同时存在有效`PRIMARY`服务经理和有效项目经理；否则为`UNASSIGNED`。
- 仅补齐服务经理而项目经理未有效指派时保持`UNASSIGNED`；项目经理已有效指派且本次补齐主责服务经理时才变为`ASSIGNED`。
- `COLLABORATOR`不参与判定。初次指派、改派、撤销或生效区间变化后均按当前有效事实重算。
- 本Feature不实现PM-11，只复用已存在的有效项目经理事实；不得修改`current_stage`或`lifecycle_status`。

### BR-FPROJ005-004 权限、并发与失败

- 查询候选、指派和改派要求`pms:project:assign`及目标节点`MANAGE`范围；服务经理可看其当前负责项目和授权树范围，但不能自助转派。
- 所有请求按当前租户隔离；空候选或空权限范围返回空/拒绝，不能省略过滤条件扩大范围。
- 写命令要求`Idempotency-Key`和`If-Match`。同键同请求重放，同键不同请求冲突；Project版本CAS和事务内冲突关系查询串行化同一节点写入。
- 任一合法性、版本或持久化失败保持原关系、状态、审计及事件不变。

### BR-FPROJ005-005 审计与通知

- 指派事务原子写成员关系、生效区间、Project版本/状态、幂等成功事实、平台操作审计和`ProjectServiceManagerAssigned` Outbox事件。
- 审计至少包含项目节点、前后主责、层级、类型、站点、部门编码、生效区间、原因、操作者和时间。
- 事件payload在指派事务内冻结`assignmentId/projectId/recipientUserId/templateCode/templateParamsSnapshot/assignmentType/levelCode/effectiveFrom`；`templateParamsSnapshot`是构造站内信所需的不可变参数对象，不含秘密或可变实体引用。重试只能读取该payload，禁止根据当前Project、当前成员关系或当前用户重新推导收件人、模板及内容。
- Outbox处理器以`eventId`作为`deliveryKey`调用SYSTEM `NotifyMessageSendApi`发送站内信；`system_notify_message`以前向迁移增加可空`delivery_key`及`uk(tenant_id,user_type,delivery_key)`。现有调用可继续留空，本Feature必须非空。
- 同一租户、用户类型和`deliveryKey`重复请求若收件人、模板和参数摘要一致，SYSTEM返回首次消息ID；不一致返回投递键冲突。这样即使“消息已创建、Outbox尚未标记成功”时进程崩溃，重试也不会生成第二条站内信。
- Outbox成功后标记已投递，失败增加重试次数并设置下次重试时间。通知失败不回滚已提交的指派。不新建通知历史/重试表；`plt_outbox_event`是持久重试事实，`system_notify_message`是幂等投递事实。

## 4. API契约

所有路径继承`/api/v1/pms`前缀。

| 接口 | 操作 | 契约 |
|---|---|---|
| `/projects/{id}/service-manager-candidates` | `GET` | 参数`siteId/departmentCode/keyword/pageNo/pageSize`；先校验节点和站点，再通过SYSTEM公开API返回同公司部门有效用户；精确范围无候选时返回空页 |
| 既有`/projects/{id}/actions/assign-manager` | `POST` | Header必填`Idempotency-Key/If-Match`；请求含`userId`、`levelCode=L1/L2`、`assignmentType`、可选`siteId`、`departmentCode`和非空`changeReason`；生效时间由服务端生成；响应含关系ID、`effectiveFrom`、Project新版本和重算后的`assignmentStatus` |
| `/projects/{rootId}/service-manager-responsibilities` | `GET` | 在调用方ProjectTreeScope内分页返回实际节点、站点/部门、当前主责、协同列表和节点`assignmentStatus`；不生成隐式责任关系 |

候选SYSTEM API明确为既有`OrganizationScopeApi`的加法方法：

- `PageResult<OrganizationUserCandidateRespDTO> pageActiveUsers(OrganizationUserCandidatePageReqDTO request)`；
- 请求必填`companyId/departmentId/departmentCode/pageNo/pageSize`，`keyword`可空；`pageNo>=1`，`1<=pageSize<=100`。租户取受信任调用上下文，不接受调用方覆盖；
- 返回项仅含`userId/username/nickname/employeeNo/companyId/departmentId/departmentCode/departmentName`，并返回`total`；
- SYSTEM按查询时点校验部门ID/编码一致、用户启用且存在同一有效`companyId + departmentId`组织范围。合法范围无人员返回空页，不做父部门、其他公司或其他办事处回退；参数非法返回`INVALID_ARGUMENT`，组织主数据不可用或ID/编码冲突返回`ORG_SCOPE_INVALID`；
- 指派提交不得信任候选查询旧结果，PROJ必须再次校验租户、Project公司、确认部门ID/编码、用户启用状态及有效公司—部门关系；任一变化返回业务校验错误且不写关系。

该API是模块间公开应用接口，只暴露候选所需字段；PROJ不得依赖SYSTEM的Service、Mapper、Repository或业务表。

## 5. 数据与物理边界

机器契约：`specs/features/F-PROJ-005-physical-contract.json`。

- 复用`proj_project_member_assignment`，新增显式`department_id`、`assignment_type`、`site_id`和`change_reason`；用户快照复用`employee_no/member_name`，组织快照复用`company_id/company_code/company_name/department_code/department_name`并补齐`department_id`。
- 不新增成员历史表；同表的时间区间即权威历史。`responsibility`只保留展示性说明，不再承载必须检索的类型、站点或原因。
- 复用`proj_project.assignment_status`，不新增分类状态轴。
- 同一Project行版本CAS使同一节点写入串行；事务内按场景Query读取重叠主责关系并锁定，禁止长参数Mapper、SQL注解及跨模块直表访问。
- 迁移只新增前向版本，不修改已执行Flyway文件。

## 6. UI

- 复用项目列表/详情、Yudao用户选择、表格、表单、抽屉和权限组件；无可复用项时使用Element Plus结构与主题变量。
- 页面展示订单办事处、实施地点、映射建议、候选人、主责/协同、层级、生效时间和原因；映射建议与最终人工选择明确区分。
- 责任分布支持树节点筛选和分页；320/768/1024/1440宽度无页面级横向溢出，减少内联样式并支持统一主题切换。

## 7. 验收标准

- `AC-FPROJ005-001`：候选只包含当前租户、项目公司、确认部门范围内的启用用户；停用、离职、跨公司部门、映射缺失或跨租户均不进入候选且服务端拒绝强行提交。
- `AC-FPROJ005-002`：区划映射只提示部门，授权用户人工确认后才可指派；站点不产生公司/办事处绑定。
- `AC-FPROJ005-003`：任意深度实际项目节点可保存L1统筹或L2属地的主责/协同关系；不会自动复制到子孙节点或扩大权限。
- `AC-FPROJ005-004`：同一责任范围并发主责指派只有一个成功；V1请求不能预约生效，改派要求原因并由服务端同一时点关闭原区间、开启新区间，历史快照仍可查，失败保持原事实。
- `AC-FPROJ005-005`：仅主责服务经理有效而项目经理缺失时状态仍为`UNASSIGNED`；两项主责均有效时为`ASSIGNED`；协同不影响状态，改派后重新计算且不改变阶段/生命周期。
- `AC-FPROJ005-006`：根节点责任分布在ProjectTreeScope内展示实际节点、当前主责、协同、站点/部门和状态；越权节点及跨租户数据不可见。
- `AC-FPROJ005-007`：相同幂等请求不产生重复关系/审计/事件；同键异请求、旧版本和权限失败无有效副作用。
- `AC-FPROJ005-008`：指派提交后Outbox处理器仅用事务内冻结的事件payload和eventId投递一次站内信；指派后再改派或用户资料变化不改变旧事件的收件人、模板或参数。模拟“消息已创建但Outbox未标成功”及普通通知失败时指派仍成功，重试返回同一消息ID且只有一条`delivery_key`事实。
- `AC-FPROJ005-009`：真实MySQL验证时间区间、并发唯一主责、状态重算和Outbox重试；真实浏览器验证候选、指派、改派、责任分布、刷新持久化、权限负向和四类响应式视口。
- `AC-FPROJ005-010`：不宣称PM-08 V2、PM-11项目经理指派、PM-05/06/09、Deployment、SIT、UAT或Release完成。

## 8. 测试与证据

本Feature按已确认的非TDD方式实施，不把失败测试作为实现前置；每个Task完成后按风险补齐自动化回归。Feature完成证据至少包括服务/Controller自动化、真实MySQL、通知失败重试、权限负向、真实浏览器响应式闭环和独立代码评审。

## 9. Definition of Ready

| 项目 | 当前状态 |
|---|---|
| PM-08 V1 Scope与V2边界 | PASS |
| 候选、办事处及地点口径 | PASS |
| 主责/协同、时间区间及任意节点责任 | PASS |
| `assignment_status`与PM-11依赖 | PASS |
| API、物理契约、权限、通知与验收 | PASS |
| 独立Feature Ready裁决 | PASS（`NPDMS-FPROJ005-FEATURE-READY-20260825-01`） |

结论：`IMPLEMENTATION_COMPLETE / GO`。Feature Ready保持既有GO；Implementation Done整改复审已GO，原租户阻断已闭环且不重开Feature Ready/SDS。该结论不代表PM-08 V2自动指派、PM-11、Deployment、SIT、UAT或Release完成。
