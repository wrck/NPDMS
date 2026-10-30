# 采集状态查询内嵌结构化语义解析结果 — 设计

日期：2026-09-19
状态：已确认（参数位置、返回内容、等待行为经用户确认；实现形态采用推荐方案）

## 背景与目标

采集状态查询 `GET /api/v1/collections/{collectionId}?namespace=...` 与项目变体
`GET /api/v1/projects/{projectKey}/collections/{collectionId}?namespace=...` 目前只返回目标输出、
传统 `parsedFacts` 和命令块。结构化语义解析结果必须另调
`GET /api/v1/collections/{collectionId}/semantic-results?namespace=...`。

外部调用方希望**一次状态查询就能拿到结构化解析结果**，减少往返。

## 用户确认的决策

1. **参数位置**：仅两个状态查询接口；提交接口与 SSE 不变。
2. **返回内容**：与 `/semantic-results` 完全一致的数组（每目标 targetId、taskId、state、
   waitReason、releaseId、coordinate、resultId、result envelope），内嵌在状态响应顶层。
3. **等待行为**：支持可选等待秒数——服务端最多等 N 秒，让语义解析任务到达终态后返回；
   超时返回当前状态。解析任务不存在（未启用语义解析）时立即返回空数组。

## API 设计

两个状态查询接口新增两个可选 query 参数（行为完全一致）：

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `includeSemanticResults` | boolean | `false` | 为 `true` 时状态响应顶层新增 `semanticResults` 数组；为 `false` 时响应与现状完全一致（字段不出现）。 |
| `semanticWaitSeconds` | int 0–30 | `0` | 仅在 `includeSemanticResults=true` 时允许，否则 400 `INVALID_REQUEST`。大于 0 时服务端轮询解析任务状态，直到该采集**全部**语义解析任务到达终态（`SUCCEEDED`/`FAILED`/`CANCELLED`）或超时；超时返回当前状态，调用方按 `state` 判断是否继续轮询。 |

不变量：

- 权限模型不变：`device-ops:collections:read` + namespace/project 授权。
- 默认响应逐字节不变（`semanticResults` 为 null 时 JSON 不出现该字段）。
- `/semantic-results` 独立端点保持不变；两个入口的数组内容一致。
- 语义解析未启用或尚未创建解析任务时，`semanticResults` 为空数组（`include=true` 时）。
- 长轮询不改变 SSE 30 秒连接约定；`semanticWaitSeconds` 上限 30 与之对齐。

## 实现形态

采用 **core 中立视图 + web 层填充**（仓库已有先例：`CollectionEvidenceQueryPort` 在 core
定义 parser 相关中立 record）：

- core `CollectionQueryPort.CollectionDetails` 增加可选组件
  `List<SemanticResultView> semanticResults`（`@JsonInclude(NON_NULL)`，compact 构造允许 null）。
  `SemanticResultView` 是 core 内的标记接口，保持 core 零外部依赖。
- web 层定义 `record CollectionSemanticResultView(...) implements CollectionQueryPort.SemanticResultView`，
  字段与 `/semantic-results` 的 `CollectionSemanticResult` 完全一致（复用同一 record，JSON 输出一致）。
- jdbc 适配器构造 `CollectionDetails` 时该字段传 `null`；web 控制器在参数生效时经
  `ParseResultQueryPort.listTaskResultsByRequestPrefix(namespace, "collection:{id}:target:")`
  查询并经 `withSemanticResults` 重建记录填充。
- 新增 web 服务 `CollectionSemanticResultsGatherer`：封装快照与等待轮询
  （间隔约 200ms；无任务返回空；全终态提前返回；超时返回当前态）。

被否决的备选：web 层 `@JsonUnwrapped` 包装（Jackson 对 record 支持有兼容坑、同一端点两种返回
类型）；web 层平铺复制记录（与 core 字段重复、演化漂移）。

## 错误处理

- `semanticWaitSeconds` 超出 0–30 或类型非法：400（Bean Validation）。
- `includeSemanticResults=false` 且 `semanticWaitSeconds>0`：400 `INVALID_REQUEST`。
- 采集不存在或越权：维持现状 404/403。
- 等待轮询期间解析任务从存在变为不可见等竞态：以最后一次快照返回，不视为错误。

## 测试

- web 单元测试：Gatherer 的无任务/立即快照/全终态提前返回/超时返回当前态。
- `ExternalCollectionHttpTest`：
  - 缺省（无参数）状态响应不含 `semanticResults` 字段；
  - `includeSemanticResults=true`（单设备与项目变体）内嵌数组与 `/semantic-results` 一致；
  - 语义解析禁用时 `include=true` 返回空数组；
  - `include=false` + `semanticWaitSeconds>0` 返回 400；`semanticWaitSeconds=31` 返回 400；
  - 等待场景：提交即查询带 `semanticWaitSeconds`，返回时任务已终态且含 result。
- `ExternalCollectionOpenApiConfigurationTest`：状态 GET 描述包含新参数说明。

## 文档

- `docs/external-collection-api.md`：接口表注记 + 状态小节新增参数说明与等待语义。
- `README.md`：外部采集 API 提要行补充。
- OpenAPI external-collection 分组：状态 GET 操作描述补充两个参数的行为。
