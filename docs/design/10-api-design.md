# SDS Phase 2：API 设计

> 文档状态：`REVALIDATION_REQUIRED`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；接口组在第 5～14 节回指具体 Requirement
> Owner：SDS Phase 2 应用与接口架构
> 前置设计：`07-authorization-design.md`、`08-data-model.md`、`09-database-design.md`

## 1. API 边界与版本

【建议】新契约使用逻辑基础路径 `/pms/v1`；网关或基础平台统一前缀不写入领域契约。现有 `/pms/*` Controller 作为兼容适配入口，逐项代理到新的应用命令/查询，不再直接形成第二套业务规则。

- 用户端、响应式 Web 和未来桌面端复用同一领域 API。
- 外部系统回调、内部 Context 调用和用户 API 使用不同认证入口与执行身份。
- API 版本只处理兼容演进，不用于绕过 Requirement 变更。
- V3 和 OUT_OF_SCOPE 能力不得通过隐藏接口提前发布。

## 2. 通用请求与响应

### 2.1 请求头

| Header | 场景 | 规则 |
|---|---|---|
| `X-Request-Id` | 全部 | 可由客户端传入；缺失时服务端生成并回传 |
| `Idempotency-Key` | 创建、状态命令、外部下发、回调重放 | 同一作用域内唯一；同键不同请求摘要返回冲突 |
| `If-Match` | 修改聚合、移动树节点、状态命令 | 值为当前聚合版本；不匹配拒绝覆盖 |
| `X-Source-System` | 受信任同步/回调 | 必须与执行身份绑定，不能由普通用户伪造 |

租户、用户、组织、项目数据范围和执行服务身份从服务端认证上下文取得，不接受请求体中的同名字段覆盖。

### 2.2 成功响应

在基础平台统一响应包装内，业务数据至少返回 `requestId`、资源 `id`、`version`、`statusCode`；异步命令返回 `operationId`、`acceptedAt` 和查询位置。批量操作逐项返回 `itemKey/status/errorCode`，不得用整体成功掩盖部分失败。

### 2.3 错误响应

| 字段 | 说明 |
|---|---|
| `code` | 稳定机器错误码 |
| `message` | 可展示、无秘密的说明 |
| `requestId` | 追踪标识 |
| `fieldErrors` | 输入字段错误，不回显秘密值 |
| `currentVersion/currentStatus` | 并发或状态冲突时返回 |
| `retryable` | 是否允许按原幂等键重试 |
| `detailsRef` | 受权用户查询详细证据的引用，不直接泄露内部报文 |

## 3. 命令、查询与状态迁移规范

- 查询使用 `GET`；新建草稿/事实使用 `POST`；普通可编辑字段使用 `PATCH` + `If-Match`。
- 状态变化统一使用 `POST /{resource}/{id}/actions/{command}`，例如 `actions/submit`、`actions/review`，不提供通用 `status` 更新接口。
- 删除仅适用于 PRD 允许删除的草稿或配置；已提交、批准、发布、执行、归档和审计事实使用撤销、作废、失效或新 revision。
- 树查询支持 `scope=self|children|descendants`，服务端强制数据范围；大型子树使用稳定游标和 `treeVersion`。
- 所有列表必须有确定性排序；默认按 `updateTime desc, id desc`，树路径按业务顺序和 ID 稳定排序。

## 4. 授权决策顺序

每个业务 API 依次校验：认证主体 → 租户 → 功能权限 → 数据范围 → 聚合当前状态 → 对象/设备/文件/凭证专用权限 → 并发版本 → 业务守卫。任何一步拒绝都不执行后续写入。

| 范围 | 典型接口 | 服务端证据 |
|---|---|---|
| ProjectTreeScope | 项目、任务、组合和阶段 | 当前项目树版本、主体项目角色和后代范围 |
| ProjectDeviceScope | 设备、实施、割接、巡检 | DeviceCurrentAssignment + 项目祖先投影水位 |
| ContractProjectScope | 合同订单范围 | 合同/订单授权 + DeliveryScope |
| BusinessObjectDeviceCredentialScope | 采集任务 | 来源业务对象、设备、协议、命令模板、用户和有效期五元组 |
| FileBusinessScope | 下载、预览、替换、归档 | FileReference 所指业务对象的实时权限 |

## 5. PROJ：项目治理 API

适用 Requirement：PM-01～PM-11、PROJ-12、INT-01。

| 路径 | 方法/命令 | 作用 | 关键约束 |
|---|---|---|---|
| `/projects` | `POST`, `GET` | 创建、分页查询项目 | 创建需幂等键；查询服务端过滤 ProjectTreeScope |
| `/projects/{id}` | `GET`, `PATCH` | 项目详情、可编辑属性 | `PATCH` 不能修改状态、父节点和来源权威字段 |
| `/projects/{id}/workspace` | `GET` | 项目概览六页签、Stage→ProjectTask导航和投影水位 | 不返回第二套导航真值；按ProjectTreeScope裁剪，任务子树按需加载 |
| `/projects/{id}/tree` | `GET` | 祖先、直接子级或全后代 | 返回 `treeVersion`；支持稳定游标 |
| `/projects/{id}/actions/move` | `POST` | 移动到目标父项目 | `If-Match`、无环校验、树变更批次 |
| `/projects/{id}/actions/classify` | `POST` | 项目级别识别/确认 | 类型字典可扩展，识别结果与人工确认留痕 |
| `/projects/{id}/actions/assign-manager` | `POST` | 指派项目经理/服务经理 | 仅使用 PRD 已定义角色和规则 |
| `/projects/{id}/actions/rollback` | `POST` | 受控阶段回退 | 保存原因、目标阶段和新门禁快照 |
| `/projects/{id}/actions/close` | `POST` | 接收闭环完成后的关闭命令 | 只能由闭环契约触发或满足相同门禁的授权入口 |
| `/projects/{id}/members:batch-change` | `POST` | 人员批量变更 | 逐项结果、有效期和历史，不覆盖原记录 |
| `/projects/{id}/tasks` | `POST`, `GET` | 创建/查询任意层级任务 | 任务父节点可空；不限制深度 |
| `/project-tasks/{id}` | `GET`, `PATCH` | 任务详情与可编辑属性 | 状态和父节点不可普通修改 |
| `/project-tasks/{id}/workbench` | `GET` | 返回任务通用基础信息、WorkBinding类型、允许操作和完成规则摘要；TASK_NATIVE不返回外部目标，其他类型返回必要的目标稳定引用、受信任组件键/表单/审批引用 | 每次按任务、项目树、绑定类型及适用的目标对象和状态重新授权；不返回任意脚本或跨域数据正文 |
| `/project-tasks/{id}/actions/move` | `POST` | 移动任务节点 | 无环、树版本、项目范围校验 |
| `/project-tasks/{id}/actions/{submit|start|complete|cancel}` | `POST` | 任务状态命令 | 按05状态机守卫执行；TASK_NATIVE按任务自身事实完成，其他绑定只接受携带目标事实/绑定/规则版本的受控完成请求，不能用通用按钮绕过目标业务 |
| `/project-templates` | CRUD + `actions/publish` | 项目/阶段/任务模板 | 已发布 revision 只读 |
| `/project-portfolios` | CRUD + `actions/publish` | 项目组合 | V2；成员项目只引用不改 Owner |

外部项目同步不直接暴露为普通用户 CRUD；由第 12 分册定义的 CRM/ERP 适配器调用内部 upsert 命令并保存来源版本。

### 5.1 PM-05 借货项目转销契约

| 路径 | 操作 | 输入/输出 | 业务守卫 |
|---|---|---|---|
| `/project-conversions` | `POST` | 输入 sourceProjectId、targetProjectId、formalSalesBusinessId、对象复用清单、逐台设备处置、Idempotency-Key；返回 conversionId、状态和逐项结果 | 调用人同时具备源/目标项目管理权限；目标为有效正式销售项目；同一源项目无其他生效目标；幂等键为源项目+正式销售业务ID |
| `/project-conversions/{id}` | `GET` | 返回源/目标、处理中/部分失败/待处理/已完成状态、成功/失败对象汇总、来源版本和设备处置结果 | 只返回同时满足源/目标项目数据范围的内容；敏感对象继续执行原对象权限 |
| `/project-conversions/{id}/actions/retry-failed` | `POST` | 输入 expectedVersion 和失败 itemIds；返回原批次的新版本及逐项结果 | 仅重试失败/待处理项；成功引用/副本不得重复生成；设备归属重新校验 assignmentVersion |

对象清单的 `handlingMode` 只能是 `READ_ONLY_REFERENCE` 或 `DERIVED_COPY`；默认前者。派生副本必须返回 `sourceObjectId/sourceVersion/derivedObjectId`。只有所有项成功后服务端才完成转销并归档源项目，不提供客户端直接设置完成/归档状态的接口。

### 5.2 PM-06 多期项目契约

| 路径 | 操作 | 输入/输出 | 业务守卫 |
|---|---|---|---|
| `/project-phase-groups` | `POST`, `GET` | 创建/查询群组；输入关系类型、名称、首期项目和期次号 | 关系类型来自字典；调用人具有涉及项目权限；跨租户禁止 |
| `/project-phase-groups/{id}/actions/add-phase` | `POST` | 输入 projectId、phaseNo、displayOrder、expectedVersion；返回 groupVersion/memberVersion | 同关系类型下项目未加入其他有效群组；期次唯一；关系无环 |
| `/project-phase-groups/{id}/actions/remove-phase` | `POST` | 关闭成员有效区间并返回新版本 | 不删除项目事实、历史引用或已发布汇总快照 |
| `/project-phase-groups/{id}/phases` | `GET` | 按期次返回独立项目状态、来源版本、设备分类和资料差异，附 completeScope 标识 | 只返回用户有权期次；缺失期次标记不完整，不按零值汇总 |
| `/project-phase-groups/{id}/actions/derive-content` | `POST` | 输入 sourceProjectId/sourceObjectType/sourceObjectId/sourceVersion/targetProjectId；返回派生对象和来源关系 | 只允许 PRD 指定的客户视图、拓扑、方案和设备视图复用；派生修改不回写来源 |

## 6. SOL：交付准备与方案 API

适用 Requirement：PRE-01～PRE-05、PLN-01～PLN-04、SCH-01～SCH-05、SOL-01。

| 资源 | 路径与命令 | 关键约束 |
|---|---|---|
| Preparation | `/preparations`, `/{id}/actions/{submit|confirm|return}` | 冻结表单 schemaVersion；退回生成历史而非覆盖提交证据 |
| ConstructionPlan | `/construction-plans`, `/{id}/revisions`, `/{id}/actions/{submit|approve|reject}` | 批准 revision 不可覆盖；计划变更保存前后差异 |
| Schedule | `/schedules`, `/{id}/actions/{calculate|apply}` | 计算结果是候选快照，只有 apply 命令改变计划 |
| Solution | `/solutions`, `/{id}/revisions`, `/{id}/actions/{submit|approve|reject|publish}` | 提交/批准/发布均需 If-Match 和文件引用校验 |
| Dynamic Form | `/form-schemas`, `/form-instances` | V2；schema 发布后只读，实例不允许任意脚本执行 |

## 7. IMP：现场实施 API

适用 Requirement：EXE-01～EXE-06、IMP-01。

| 聚合 | API | 状态命令/特殊约束 |
|---|---|---|
| ArrivalAcceptance | `/arrival-acceptances` | `confirm`、`raise-difference`、`resolve-difference`；最终确认按 PRD 由项目经理执行 |
| InstallationRecord | `/installation-records` | `submit`、`confirm`、`return`；确认/退回按 PRD 由项目经理执行 |
| ConfigurationCollectionResult | `/configuration-results`、`/devices/{id}/component-relations` | `consume-callback` 为内部命令；解析候选可待匹配/人工绑定；绑定通过AST命令结束旧关系并新增时态关系，不修改原始Log |
| JointDebuggingResult | `/debugging-results` | 关联 CollectionTask；记录联调结论和问题引用 |
| ImplementationRisk | `/implementation-risks` | `raise`、`treat`、`close`；不调用 CUT 风险状态接口 |
| ImplementationQualityCheck | `/quality-checks` | `submit`、`review`、`complete-remediation`、`re-review` |
| DeliveryEvidence | `/implementation-evidence`, `/{id}/versions` | 上传/替换草稿；ACC 审核归档，不由 IMP 调归档命令 |
| Readiness | `/implementation-readiness/{projectId}` | 只读门禁查询，返回快照版本与未满足项；供 CUT 执行前校验 |

到货、安装、质量和安全接口按聚合独立分页和状态；不得恢复为一个通用“现场执行单” CRUD。

## 8. ACC：验收与项目闭环 API

适用 Requirement：ACC-01～ACC-06、CLO-01～CLO-02。

| 路径 | 命令 | 约束 |
|---|---|---|
| `/acceptances` | create/update draft、`submit`、`confirm`、`return` | 客户确认和项目审核分别留痕；不覆盖 IMP 证据 |
| `/delivery-artifacts` | `check-completeness`、`review`、`archive` | 齐套、审核、归档是不同命令；文件版本固定 |
| `/closure-gates/{projectId}` | `GET` | 返回所有后代项目的门禁快照和水位 |
| `/project-closures` | `create`、`submit`、`review`、`complete` | complete 发布事件请求 Project 关闭，不直写 Project 表 |
| `/service-handovers` | create、`submit`、`accept` | 只做持续服务交接，不提供 renew/续保接口 |
| `/satisfaction-tasks` | create、assign、send、recollect、list/detail | 创建时冻结问卷模板/阈值；未达标只能整改后新建任务和问卷版本 |
| `/satisfaction-questionnaires/{token}/responses` | submit | 一次性实例、必答/签字校验和幂等提交；客户答案不可由内部用户修改 |
| `/satisfaction-results` | GET、export | 只读判定；导出按数据/字段/文件权限裁剪并生成导出审计 |

历史 `/pms/acc-maintenance-transition/*` 的 create/renew/activate 等入口必须在兼容切换后冻结，不映射为新 ServiceHandover 命令。

## 9. CUT：割接 API

适用 Requirement：CUT-01～CUT-10。

| 路径 | 命令/查询 | 关键约束 |
|---|---|---|
| `/cutover-tasks` | create、list、detail | 来源键幂等；项目/设备归属校验 |
| `/cutover-tasks/{id}/assessment` | save draft、submit | 一线提交问卷与人工等级；用服经理在P5复核，不新增P2审批 |
| `/cutover-tasks/{id}/checklist` | detail、save draft、submit | P3同一工作台返回匹配项、界面格式、直接填写值、采集状态/结果引用和重新匹配差异；D级不存在该资源 |
| `/cutover-tasks/{id}/checklist/actions/rematch` | POST | 维度或条件变化后按新输入快照预览/应用差异 | 保留稳定ID有效答案；移出项仅留历史，不进入当前提交；If-Match清单版本 |
| `/cutover-tasks/{id}/checklist/items/{itemId}/actions/request-collection` | POST | 为设备采集项创建DAC CollectionTask | 绑定任务、清单版本、采集项、设备和命令模板；DAC回调不直接判定采集项通过 |
| `/cutover-tasks/{id}/plan-revisions` | create、submit、approve、reject | 文件/安全/归属/人工确认校验；不强制解析全部模板字段 |
| `/cutover-tasks/{id}/support-arrangements` | update contacts / revise duties | 联系人、联系方式、到位时间变化留痕不重审；角色/职责变化必须生成新方案revision并重走P5 |
| `/cutover-tasks/{id}/actions/request-collection` | POST | 兼容非清单级采集入口 | 新P3采集项使用item级入口；均不读取凭证明文，不创建独立采集阶段 |
| `/cutover-tasks/{id}/approval-actions/{approve|reject}` | POST | 按人工等级和冻结路由校验节点；任一评审项为否必须驳回并填写原因 |
| `/cutover-tasks/{id}/closure` | save、submit、detail | 保存P6结果与INT-12证据引用；提交即归档；失败不发布CutoverCompleted |

## 10. SRV：巡检与服务状态 API

适用 Requirement：INS-01～INS-09、SRV-01。

| Context | API | 约束 |
|---|---|---|
| Inspection | `/inspection-rules`、`/{id}/revisions` | 发布 revision 只读；任务冻结规则版本 |
| Inspection | `/inspection-tasks`、`/{id}/actions/{precheck|dispatch|complete|archive}` | 在线通过 DAC；离线文件走受控上传；模式互斥 |
| Inspection | `/inspection-reports/{id}/versions` | 生成/发布报告版本，原始采集结果只引用 |
| Inspection | `/service-issues`、`/{id}/actions/{remediate|review|close|mark-false-positive}` | 问题闭环和误报留痕 |
| Service Operations | `/devices/{deviceId}/service-status` | V2 只读客观状态与来源，不提供续保空间/续保率接口 |

历史工单、工时及其附件在V1/V2不提供用户查询、导出或文件访问API。`AI-MIG-000`在已批准真实批次内保存的不可变来源载荷或受限迁移归档仅用于迁移对账、问题调查和来源审计，不是SRV业务API；未来用户访问能力必须通过独立PRD/Feature变更重新批准。

## 11. CUS、AST、COM、RES 与 KNO API

| Owner | Requirement | API | 关键边界 |
|---|---|---|---|
| CUS | CUS-01～CUS-04、INT-03 | `/customers`、`/customer-contacts`、`/customer-relationships` | CRM 权威字段只读；临时客户显式标记来源 |
| AST | EQP-01～EQP-05、EQP-07、AST-01～AST-02、INT-02、INT-06 | `/devices`、`/devices/{id}/archive`、`/devices/{id}/assignment-history`、`/rma-replacements` | 设备归属用 `actions/assign-project`；同一时点唯一；维保为客观基本信息 |
| COM | COM-01 | `/contracts`、`/sales-orders`、`/order-lines`、`/delivery-scopes` | ERP合同/订单/订单行核心字段只读；平台仅维护项目交付范围分配/释放；不建设履约对账业务API |
| RES | RES-01、SUB-01～SUB-05、INT-07 | `/suppliers`、`/subcontract-requests`、`/payment-gates` | 备件业务由外部系统承接；财务结果只回写引用 |
| KNO | INT-04 | `/technical-notices`、`/technical-notices/{id}/references` | V2 仅 ITR 同步查询与业务引用；无本地 publish/disable API |

设备归属命令 `POST /devices/{id}/actions/assign-project` 必须携带 `If-Match`、目标项目和原因；返回新的 `assignmentVersion` 和异步投影 `operationId`。上级项目统计读取设备祖先投影，不创建第二条归属。

## 12. ANA 与公共能力 API

| Owner | Requirement | API | 规则 |
|---|---|---|---|
| ANA | RPT-02、ANA-01 | `/analytics/metrics`、`/analytics/portfolios/{id}` | 返回 `metricVersion/dataWatermark/treeVersion`；只读 |
| PLT | PLT-01 | `/todos`、`/{id}/actions/complete` | 待办完成回调业务 Owner；不能自行宣告业务成功 |
| PLT | PLT-02 | `/files:init-upload`、`/files/{id}:complete-upload`、`/files/{id}/versions`、`/file-references` | 文件 API 详见 13；下载实时校验业务权限 |
| PLT | AUT-01～AUT-02 | `/authorization-grants` | 通用授权，不代替 DAC 凭证授权 |
| PLT | CHG-01 | `/change-requests`、状态命令 | 低优先级独立能力，按版本范围后置实施 |

周报/日报不提供独立 API；周期性展示复用指标快照。

## 13. Device Access & Collection API

适用 Requirement：INT-12、EXE-03～EXE-04、CUT-06、INS-02、INS-04、NFR-02。

### 13.1 凭证与授权

| 路径 | 操作 | 安全响应 |
|---|---|---|
| `/device-credentials` | `POST`, `GET list` | 创建请求可含秘密；响应只返回 ID、名称、协议、掩码、版本、创建人和状态 |
| `/device-credentials/{id}` | `GET` | 永不返回密码、私钥、Token 或可逆密文 |
| `/device-credentials/{id}/actions/rotate` | `POST` | 生成新版本；旧任务仍引用旧授权快照 |
| `/device-credentials/{id}/actions/revoke` | `POST` | 阻止新任务；运行中任务按实际停止点留痕 |
| `/device-credentials/{id}/grants` | `POST`, `GET` | 用户、设备、协议、命令模板、有效期五元组完整校验 |

### 13.2 创建采集任务

`POST /collection-tasks` 支持二选一认证：

```json
{
  "sourceContext": "IMP",
  "sourceObjectType": "ConfigurationCollectionResult",
  "sourceObjectId": "123",
  "deviceId": "456",
  "protocolCode": "SSH",
  "commandTemplateId": "789",
  "commandTemplateVersion": 3,
  "authentication": {
    "mode": "SAVED_CREDENTIAL",
    "credentialId": "101",
    "credentialVersion": 2
  }
}
```

或：

```json
{
  "sourceContext": "INS",
  "sourceObjectType": "InspectionTask",
  "sourceObjectId": "124",
  "deviceId": "456",
  "protocolCode": "SSH",
  "commandTemplateId": "789",
  "commandTemplateVersion": 3,
  "authentication": {
    "mode": "TEMPORARY_INPUT",
    "username": "write-only",
    "password": "write-only",
    "saveAsCredential": true,
    "credentialName": "explicit-name"
  }
}
```

临时密码是 write-only，请求日志、审计详情、错误、事件和任务响应均不出现。未选择保存时，任务以 `TEMPORARY_INPUT` 创建并保存 `temporaryUsername` 用于审计。`saveAsCredential=true` 时，平台在同一业务命令中先创建加密凭证及默认仅当前用户可用的授权，再以 `SAVED_CREDENTIAL` 创建本次任务；响应返回新 `credentialId`、`credentialVersion` 和任务所冻结的 `grantSnapshotId`。凭证创建失败则整个请求失败且不创建任务，不得静默按临时模式继续。

| 路径 | 操作 | 规则 |
|---|---|---|
| `/collection-tasks` | `POST`, `GET` | 创建需 Idempotency-Key；批量按设备产生独立任务 |
| `/collection-tasks/{id}` | `GET` | 返回外部状态原值、映射状态和结果引用，不返回秘密 |
| `/collection-tasks/{id}/actions/dispatch` | `POST` | 仅 DAC 执行身份；签发任务级短期执行授权 |
| `/collection-tasks/{id}/actions/retry` | `POST` | 创建引用原任务的新任务；临时密码必须重新输入 |
| `/internal/dac/callbacks/{provider}` | `POST` | 验签、来源身份、callback 幂等和顺序校验 |
| `/internal/collection-tasks/{id}/actions/confirm-consumption` | `POST` | IMP/CUT/Inspection 以 `consumerContext + consumerObjectType + consumerObjectId + resultVersion` 幂等确认消费；必须匹配任务冻结的必要消费者 |

任务响应必须返回 `completionMode`。业务入口只能创建 `BUSINESS_CONSUMPTION`：成功回调生成结果引用后保持 `RESULT_AVAILABLE`，直至匹配的消费确认到达。独立中心由服务端固定为 `CALLBACK_TERMINAL`，有效成功终态回调可直接完成；失败、取消和安全异常不得调用消费确认，也不得转换为成功完成。

## 14. 集成入口与降级 API

适用 Requirement：INT-01～INT-07、INT-09～INT-10、INT-12，以及对应领域 Requirement。

- 外部同步使用 `/internal/integrations/{system}/{object}:sync` 或受控消息 Consumer；普通用户不可调用。
- HR目录同步统一通过内部契约 `/integration/hr/directory` 接收必要人员、组织、岗位和任职状态的增量/全量批次；按来源键、来源版本与批次幂等，不返回为业务授权成功。
- 人工补录/平台记录是独立降级命令，必须保存来源和原因，不伪造外部 sourceKey。
- 重试沿用原同步批次/幂等范围；补偿和对账 API 只对集成运维角色开放。
- CRM 是统一系统名称；文档和 API 不再使用 SMS 表示另一套客户系统。
- HTTP 2xx 只表示请求被接收或传输成功，业务完成以领域回写、回调或对账结果为准。

## 15. 兼容、废弃与契约测试

### 15.1 历史接口处置

| 历史模式 | 目标处置 |
|---|---|
| 通用 `/create`、`/update` | 草稿兼容；状态字段在适配层移除/拒绝 |
| `/submit`、`/approve` 等状态接口 | 代理到新 command，并要求版本与幂等键 |
| `/delete` | 仅允许可删除草稿；历史事实返回不可删除错误 |
| `/acc-maintenance-transition/renew` | 明确废弃并冻结，不映射到 ServiceHandover |
| 本地技术公告 create/publish/disable | V1/V2 冻结；只保留受控历史查询 |

### 15.2 契约测试最低集合

每组 API 至少覆盖：正常、输入错误、功能权限拒绝、数据范围拒绝、非法状态、版本冲突、幂等重放、同键异请求、部分失败、外部超时和敏感字段不泄露。树移动另测成环拒绝与投影水位；设备归属另测并发唯一；DAC 另测临时密码不落库/不入日志/不入事件。

## 16. API 门禁结论

| 门禁项 | 结论 | 落位 |
|---|---|---|
| API 可追溯 Requirement | PASS | 第 5～14 节每个接口组明确 Requirement 范围 |
| 状态只能通过命令改变 | PASS | 第 3 节及各聚合 actions 接口 |
| 服务端授权不可绕过 | PASS | 第 4 节；租户/项目/设备/文件/凭证范围 |
| 幂等和并发输入明确 | PASS | Idempotency-Key、If-Match、错误响应 |
| 敏感信息不回显 | PASS | 第 13 节 write-only 临时秘密和掩码响应 |
| 已排除/后置能力无新接口 | PASS | 无续保、周报日报、工单时效、历史工单/工时用户访问和本地公告治理入口 |

本分册可进入事件、集成、文件和异常幂等交叉评审；接口正式发布前仍需生成 OpenAPI、契约测试和兼容清单。
