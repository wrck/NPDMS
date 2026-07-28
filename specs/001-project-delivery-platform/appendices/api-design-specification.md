---
status: Review Baseline
system: 项目实施交付管理平台
title: API Design Specification
version: v1.0
decision: DEC-014
---

# 附录G：API设计规范

## 1. 文档目的

本文定义项目实施交付管理平台的API分类、URI、契约编号、请求响应、错误码、幂等、并发、事件、版本及评审规则。

目标：

- 统一PMS业务API设计方式；
- 隔离Yudao平台能力与PMS业务能力；
- 支持模块化单体和未来微服务演进；
- 保证接口契约稳定、可治理、可追踪和可测试。

## 2. API分类

| 类型 | 说明 | 主要载体 |
| --- | --- | --- |
| Platform API | Yudao平台基础能力 | 完全遵循Yudao上游既有接口定义，不由本规范重新定义 |
| Business API | 新增PMS模块提供的业务领域能力 | `/api/v1/pms/...` |
| Internal API | PMS模块间同步调用契约 | `pms-module-{domain}-api` |
| Integration API | 外部系统交换和回调 | `/api/v1/pms/integrations/...` |
| Event API | 模块间异步领域事件 | 版本化事件契约 |

本规范不为同一业务能力默认生成“管理端接口”和“开放接口”两套路径。是否允许外部系统调用，由Integration API清单、认证授权和数据范围单独定义。

## 3. API架构原则

### 3.1 平台与业务分离

Yudao基础能力以`yudao-boot-mini master-jdk25`为集成起点：通过`yudao-framework`提供Web、安全、数据访问、缓存、消息、日志和校验等公共技术框架，通过system、infra提供用户、角色、权限、组织、数据权限、文件和审计能力；BPM从`YunaiV/ruoyi-vue-pro master-jdk25`按DEC-015引入。

Yudao平台接口的路径、鉴权、请求对象、响应结构、错误码和版本策略完全以上游平台实现与文档为准。本规范不得重命名、重新版本化或包装Yudao平台接口。新增`pms-module-*`模块必须执行本规范，不得将项目、任务、设备等PMS业务对象包装成平台通用对象。

### 3.2 面向业务能力

- API围绕领域、资源和动作建模。
- API不暴露数据库模型或内部实现类。
- 一个模块不得通过API绕过另一个模块的数据所有权。
- API不绑定具体页面、按钮或单一客户端。
- 所有状态变化必须由显式Action触发。

### 3.3 非固定层级

项目树和任务WBS不设置固定业务深度。树查询允许使用`maxDepth`、`pageSize`、游标等单次请求保护参数，但这些参数不得转化为一级、二级、三级等业务层级限制。

## 4. API组织与契约编号

API按以下模型组织：

```text
Domain → Resource → Type → Sequence
```

契约编号格式：

```text
{DOMAIN}-{RESOURCE}-{TYPE}-{SEQ}
```

TYPE取值：

| TYPE | 说明 |
| --- | --- |
| `QUERY` | 查询 |
| `CMD` | 创建、修改或删除命令 |
| `ACTION` | 审批、提交、关闭等业务动作 |
| `EVENT` | 领域事件 |

示例：

```text
PMS-PROJ-PROJECT-QUERY-001
PMS-PROJ-PROJECT-CMD-001
PMS-CUT-CUTOVER-ACTION-001
PMS-AST-EQUIPMENT-EVENT-001
```

现有`API-PROJ-001`等编号作为需求追溯ID继续保留；正式接口设计和OpenAPI文件使用上述契约编号。

## 5. URI规范

### 5.1 基础格式

新增PMS业务接口：

```text
/api/v1/{domain}/{resources}
```

PMS的domain固定为`pms`：

```text
/api/v1/pms/projects
```

其他资源示例：

```text
/api/v1/pms/project-tasks
/api/v1/pms/site-surveys
/api/v1/pms/cutover-tasks
/api/v1/pms/assets/spare-parts
```

Yudao平台接口不套用上述PMS路径，继续使用平台自身定义。

### 5.2 命名规则

- 路径全部使用小写。
- 单词使用`kebab-case`。
- 资源使用复数名词。
- 禁止使用下划线和驼峰路径。
- 查询参数和JSON字段使用`camelCase`。
- URI不出现页面名、按钮名、数据库表名或实现类名。

## 6. HTTP方法与业务动作

| 操作 | HTTP方法 | 说明 |
| --- | --- | --- |
| 查询 | `GET` | 读取资源，不产生业务副作用 |
| 创建 | `POST` | 创建资源 |
| 整体更新 | `PUT` | 更新可编辑属性 |
| 删除 | `DELETE` | 执行领域允许的逻辑删除；不代表物理删除 |
| 业务动作 | `POST` | 状态变化、审批、关闭、拆分、合并等显式动作 |

示例：

```text
POST /api/v1/pms/projects
GET  /api/v1/pms/projects/{projectId}
PUT  /api/v1/pms/projects/{projectId}
POST /api/v1/pms/projects/{projectId}/approve
```

禁止通过普通更新直接修改状态：

```text
PUT /api/v1/pms/projects/{projectId}
{"status":"APPROVED"}
```

已发生审批、执行、验收、审计或财务影响的对象不得物理删除；不满足删除规则时返回稳定错误码。

## 7. 契约对象隔离

API契约允许使用：

- Request DTO、Response DTO；
- Command、Query；
- 稳定Enum；
- Event DTO。

API契约禁止暴露：

- DO、Entity；
- Mapper、Repository；
- 数据库表结构；
- 模块内部Service和实现类。

字段必须描述业务语义，不能要求调用方理解数据库外键、逻辑删除字段或内部状态编码。

## 8. 请求与响应

### 8.1 JSON字段

JSON使用`camelCase`：

```json
{
  "projectId": 1001,
  "projectName": "测试项目",
  "version": 5
}
```

日期时间采用ISO 8601并携带时区；金额必须同时明确币种；枚举必须在契约中声明允许值。

### 8.2 PMS业务成功响应

新增PMS Business API和Integration API成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

### 8.3 PMS业务失败响应

新增PMS Business API和Integration API失败响应使用稳定语义错误码：

```json
{
  "code": "PMS_PROJ_PROJECT_STATUS_INVALID",
  "message": "项目状态不允许执行该操作",
  "traceId": "01J...",
  "details": {}
}
```

不得在错误消息、详情或日志中返回密码、设备凭证、密钥、内部SQL或无权限对象的敏感信息。

Yudao Platform API的成功和失败响应均保持平台自身定义，不受本节约束。

## 9. 错误码规范

语义错误码格式：

```text
{DOMAIN}_{RESOURCE}_{ERROR}
```

示例：

```text
PMS_PROJ_PROJECT_NOT_FOUND
PMS_PROJ_PROJECT_STATUS_INVALID
PMS_AST_ASSET_ALREADY_LOCKED
PMS_CUT_CUTOVER_WINDOW_EXPIRED
```

错误码必须：

- 全局唯一且稳定；
- 与可变的中文消息分离；
- 能追溯到所属领域和资源；
- 在接口契约测试中覆盖；
- 不复用HTTP状态码表达业务原因。

HTTP状态码仍应正确表达协议层结果，例如参数错误、未认证、无权限、资源不存在、冲突和服务异常。

## 10. 写接口治理

所有写接口必须定义：

| 治理项 | 最低要求 |
| --- | --- |
| 权限校验 | 服务端校验功能权限和数据范围 |
| 状态校验 | 校验状态机和阶段门禁 |
| 幂等策略 | 明确幂等键、业务唯一键或不可重放规则 |
| 事务边界 | 只能原子修改本模块拥有的数据 |
| 并发控制 | 使用版本号、唯一约束或领域锁 |
| 审计记录 | 记录操作者、来源、业务键、前后值和时间 |
| 事件发布 | 明确事务成功后发布及失败补偿策略 |

## 11. 幂等规范

创建、提交、审批、同步、支付、导入和回调必须具备幂等策略。

请求头：

```text
Idempotency-Key: <uuid>
```

服务端要求：

- 同一调用方、同一幂等键和同一业务动作只执行一次；
- 重复请求返回首次执行结果或明确的处理中状态；
- 幂等记录具有合理有效期并可审计；
- 幂等键相同但请求摘要不同时必须拒绝。

## 12. 并发与乐观锁

项目、任务、资产、合同、方案和状态流转对象使用`version`进行乐观锁校验：

```json
{
  "id": 100,
  "version": 5
}
```

并发冲突返回稳定错误码，不允许静默覆盖。树节点移动、项目拆分合并和批量状态动作还必须校验整棵受影响子树的版本或业务锁。

## 13. 查询规范

### 13.1 分页

```text
GET /api/v1/pms/projects?pageNo=1&pageSize=20
```

```json
{
  "list": [],
  "total": 100
}
```

`pageSize`必须配置上限。稳定的大数据遍历使用游标分页，不提供`/all`接口。

### 13.2 树查询

```text
GET /api/v1/pms/projects/tree?parentId=100&maxDepth=2
GET /api/v1/pms/projects/{projectId}/children
GET /api/v1/pms/projects/{projectId}/descendants?cursor=...
```

要求：

- 支持按父节点加载、全后代查询和路径校验；
- `maxDepth`只是单次响应保护，不是业务层级上限；
- 防止循环引用和越权枚举；
- 大子树使用分页、游标、汇总接口或异步任务。

### 13.3 汇总查询

组合、项目树和任务WBS汇总使用专用只读接口，不要求客户端拉取全部明细后自行计算。汇总结果必须声明统计口径、数据时间和权限范围。

## 14. 导入与导出

超过10万行或预计超过同步超时阈值的导入导出必须异步：

```text
创建任务 → 查询状态/进度 → 获取结果或失败明细 → 下载文件
```

禁止通过同步`GET /export`返回百万级数据。下载链接必须具有权限、有效期和审计记录。

## 15. Internal API

正确依赖：

```text
pms-module-engineering-biz
        ↓
pms-module-project-api
        ↓
pms-module-project-biz
```

Internal API必须：

- 使用稳定DTO、Command、Query或接口；
- 由数据所有模块执行权限、状态和业务规则；
- 明确同步超时、错误语义和幂等；
- 通过契约测试验证调用方和提供方兼容性。

禁止调用目标模块`-biz`、内部Service、Mapper或数据库。

## 16. Event API

事件结构：

```json
{
  "eventId": "uuid",
  "eventType": "pms.project.created.v1",
  "schemaVersion": 1,
  "occurredAt": "2026-07-28T10:00:00+08:00",
  "aggregateId": "1001",
  "traceId": "01J...",
  "data": {}
}
```

事件命名：

```text
pms.{resource}.{action}.v{version}
```

示例：

```text
pms.project.created.v1
pms.equipment.installed.v1
pms.cutover-task.completed.v1
```

事件必须定义生产者、消费者、顺序要求、重复消费策略、失败重试、死信处理和兼容性策略。

## 17. API版本管理

版本管理覆盖：

- URI；
- Request DTO和Response DTO；
- Enum和错误码；
- Event Schema；
- OpenAPI或AsyncAPI文档。

兼容性新增可以在同一版本演进；删除字段、改变类型、收紧必填、改变枚举语义或改变业务结果属于破坏性变更。

破坏性变更必须：

- 发布新版本；
- 提供迁移说明和并行期；
- 发送废弃通知；
- 监控旧版本调用量；
- 在约定窗口后下线。

## 18. 安全与审计

- 所有接口必须完成认证、功能权限和数据权限校验。
- 项目层级权限由服务端计算，不能信任客户端传入的数据范围。
- 批量查询、导出、文件下载和敏感字段访问必须审计。
- 外部回调必须验证签名、时间戳和重放窗口。
- 日志只记录必要摘要，不记录密码、Token、设备凭证或完整敏感报文。
- 限流、熔断和重试必须区分查询与写操作，写操作不得无幂等重试。

## 19. 契约交付物

每个API在进入开发前至少具有：

- 契约编号和追溯ID；
- 所属领域、资源、版本和负责人；
- URI、HTTP方法和权限标识；
- Request/Response Schema；
- 错误码；
- 幂等、并发、事务和审计策略；
- OpenAPI示例；
- 单元、集成和契约验收场景。

## 20. API评审清单

- [ ] 是否属于正确领域和数据所有者？
- [ ] 是否按资源和业务动作建模？
- [ ] URI、契约编号和事件是否版本化？
- [ ] 是否隔离DTO与DO？
- [ ] 是否定义权限和数据范围？
- [ ] 写接口是否定义幂等、事务、并发和审计？
- [ ] 错误码是否唯一、稳定、可追踪？
- [ ] 树查询是否保持非固定业务层级？
- [ ] 大数据查询和导出是否避免全量同步返回？
- [ ] 是否具备OpenAPI、契约测试和演进策略？

## 21. 验收标准

- 接口清单中的每个接口域同时具有追溯ID和正式契约编号前缀。
- Yudao平台接口与上游定义一致，不存在PMS侧重命名、重新版本化或响应包装。
- 新增PMS Business API符合`/api/v1/pms/...`规则，不默认生成第二套开放路径。
- 只有明确列入Integration API清单的能力才允许作为外部系统接口。
- 业务模块间不存在直接依赖目标模块`-biz`或数据库的接口实现。
- 所有写接口具备权限、幂等、并发、事务和审计规格。
- 非固定层级树接口不存在固定一级、二级、三级的契约字段或业务限制。
- 破坏性接口变更具有新版本、迁移期和废弃通知。
