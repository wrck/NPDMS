# 平台管理功能与安全边界

## 统一设计语言

管理页面统一复用 AppShell 与 Element Plus。共享 `src/styles/management.css` 使用现有 Element Plus 颜色、字号、间距及圆角变量，统一筛选、卡片标题、分页、代码展示；`ManagementStatus.vue` 用统一 `el-tag` 呈现中文状态和原始状态码。加载、空记录和失败重试复用 `RequestState`。文件导入使用 Element Plus 按钮驱动受限文件选择，不引入另一套控件主题。

## 使用入口

| 路径 | 职责 |
| --- | --- |
| `/overview` | 当前授权范围的采集统计和近期任务 |
| `/tasks` | 采集、独立解析任务及到期通知调度 |
| `/records` | 历史采集和关联语义证据 |
| `/scripts` | 具有授权采集关联的脚本版本 |
| `/settings` | 白名单身份、授权范围和生效能力 |

解析控制台复用现有日志类型、发布版本、验证、激活及运行状态接口。发布与激活是两个独立操作；历史任务仍使用原绑定版本。新增发布目录不会自动激活。

`GET /api/v1/parser-log-types/{logType}/active-release` 需要 `parser:release:read`，返回 `{logType, releaseId}`。已登记但未激活的类型返回 `releaseId: null`，未知类型返回 404。界面使用该绑定作为激活/撤销的并发前提，不能从 `PUBLISHED` 推断活动版本；读取失败时不允许盲目覆盖。

## 只读管理 API

基路径 `/api/v1/management`；需要 OAuth2 scope `device-ops:collections:read`。scope 不替代 namespace 与 project 数据授权。无项目采集按 namespace 授权；项目采集还需对应项目 claim。保存连接和凭据继续保持 subject 隔离。

### 采集摘要

`GET /collections`

查询参数：`namespace`、`project`、`device`、`status`、`from`、`to`、`page`、`size`。时间使用 ISO 8601 瞬时格式；页号从 0 开始，默认大小 20，上限 100。

响应包含 `items`、`total`、`page`、`size`。每项包含 collectionId、namespace、projectKey、externalRequestId、activityType、status、createdAt、targetCount、scriptSource、scriptKey、scriptVersion、scriptSha256。摘要不返回 stdout、脚本内容或秘密。详情继续通过已有采集查询接口访问。

`GET /overview` 使用同一授权范围和过滤条件，返回 `total` 与 `byStatus`。统计与列表不得跨权限范围聚合。

### 脚本版本

`GET /scripts` 接受 namespace、project、page、size。仅列出能通过授权采集关联证明归属的已注册版本，不开放全局脚本表。

每项包含 namespace、scriptKey、version、source、sha256、parserType、collectionId、contentReadable。

`GET /scripts/content?collectionId=...` 在读取内容前验证采集可见性及版本来源、摘要。只有受支持的 LOCAL_MANAGED 内容可读；无授权、未知归属或不匹配版本不暴露内容。

载入工作台需要明确操作。内容仅在内存中一次性交接，不放入 URL、localStorage 或自动发起设备执行。载入后的命令仍由用户审查并提交；不覆盖已注册历史版本。

### 设置

`GET /settings` 只返回明确定义的身份、授权与能力字段。不得使用该接口导出完整 Spring 配置、访问令牌、数据库地址、凭据、私钥、主密钥或本地路径。local 调试身份不是生产 OAuth2 验收结果。

## 易混淆的操作

- **停止查看**：停止前端轮询/输出订阅，不取消设备执行。
- **解析任务取消/终止**：仅使用现有解析状态机允许的操作，不等于回滚采集命令。
- **SCHEDULE_DUE**：到期通知，不是无人值守 SSH 定时采集。首次调度保持停用，是否启用受服务端能力及授权约束。
- **重新打开历史**：查询持久化证据，不自动重放命令。未保存的原始脚本和时间不得冒充完整原始证据。

## 部署与验证

先构建 `device-ops-web`，再运行平台 Maven 打包，避免 JAR 携带旧页面。运行 `build.ps1` 可执行已有前后端质量门；浏览器测试另行执行并注明模拟接口或真实测试后端。

开发验收必须使用隔离数据库、合成输入和受控设备模拟器。运行目录 `data/`、生产主密钥、活动 release、真实回调和设备不属于自动测试目标。真实 IdP、现场设备及目标网关仍需独立环境验收。
