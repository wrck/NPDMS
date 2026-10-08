# 公共交付件缺口与合同协调（2026-10-06）

基线 `fix/visio-template-followup` / `fda3bb55b0fb8e1fbd6f44f44cf4dbc21d015721`。本文件是实施与验证记录，不改写业务规格。

| 公共缺口 | 当前结果 | 验收边界 |
|---|---|---|
| 按稳定业务键发现文件引用 | 已实现。`GET /api/v1/pms/file-references` 的 artifactId 可省略；其余五个稳定键字段必填。先检查当前 Owner READ 策略，复用 `selectExact`，无可读事实返回空；提供 artifactId 时仍严格匹配。 | 专属 MySQL/H2 12 项通过。保留 DETACHED/ARCHIVED 状态，查询不会重绑、登记材料或赋予下载权限。 |
| 共享上传大 ID 与大小提示 | 已实现字符串或安全整数 ID；HTTP 客户端拒绝已经舍入的数字。共享上传、引用列表、版本抽屉、槽位状态与交付上传保持字符串；提示统一为 50MiB。 | 相关前端运行测试 27 项通过（此前 24 项为不同快照）；Chromium 验证全页刷新、新版本、解绑和精确大 ID。原生叶子页面/DTO 的范围另行列出，不据此宣称所有存量页面完成。 |
| 公共 CAS 撤回 | WIP 已独立实施，完整联合验收未完成。新增 Java API 和独立撤回服务、V398材料版本列、SQL CAS；旧 HTTP/withdrawTrusted 语义保留。H2当前7/8通过，旧fulfillment过期对象重验失败，待补共享锁。 | 不把它标成 CAS/幂等完成。不自动撤回旧版本，不改写文件版本、业务成果修订、fulfillment 或提交历史。 |
| Owner 授权、原生用途与目录约束组合 | WIP 已独立实施，完整联合验收未完成。默认原生冻结路由保持；新增Owner显式purposeKind/validateCatalogUpload组合目录限制。5项通过，存量Owner新目录用途接入未完成。 | 禁止简单按目录命中覆盖原生用途。Owner 拒绝和重复校验器仍 fail closed。未改动受保护 `DeliveryEntityAccessPolicy`。 |
| KNO 公告非项目 Owner | 已核实阻塞：现有明确适配仅为租户内只读查询；未有交付写入/文件用途/生命周期授权适配。 | 不提供虚构 projectId，不套 ProjectScope，不把 tenant-read 权限视为交付写入。等待原生 Owner 合同/包。 |

## 已提出的撤回合同

建议新增 `PlatformDeliveryMaterialApi.withdrawMaterial`，接收材料 ID、期望材料版本、幂等键与原因；由服务端上下文取得租户/用户，校验公共交付操作权限和当前原生 Owner 写权限。新增前向迁移提供材料版本列，使用 SQL CAS；业务状态、账本、审计及 outbox 同事务。重放先检查当前 Owner，摘要不一致拒绝。

建议入口只允许无模板要求、未进入归档的 ACTIVE 材料。模板冻结、归档义务以及共享来源/fulfillment 必须保留其原生授权入口；仅改变材料参与计数状态，历史锚和提交快照保持。这个入口不授权任何原生保存动作自动撤回旧材料。父任务已于本轮明确确认该技术实现范围；首包字节下载失败，目前已独立落地 WIP；H2 当前 7/8 通过，fulfillment 的过期对象重验仍失败，不能视为验收通过。

## 已提出的 SPI 组合合同

建议 Owner 校验器显式区分原生冻结用途与类型目录用途，默认保留现有原生语义。冻结用途不因同名目录存在而改走目录。目录用途先经过原生 Owner 授权，再取 Owner 与目录允许类别、媒体类型的交集及更小大小上限；空交集拒绝，读历史与新上传分别沿用现有目录停用规则。范围版本和锁定重验仍由真实 Owner 决定。

父任务已确认该技术实现范围，现已独立落地 WIP，当前 5 项选择用例通过，未代表所有原生用途接入；不猜测原生用途分类，不改受保护策略，也不引入全局上传完成材料登记钩子。

## 浏览器及邻接验证

`tests/file-reference-browser/verify.mjs` 使用真实共享 Vue 组件和文件 HTTP 客户端，文件响应由 Playwright 明确提供。它验证文件完成后消费者拒绝、完整稳定键刷新恢复、字符串大 ID、新版本及已解绑状态；SQL/Owner 边界由另一个专属 MySQL 测试验证。它不证明原生材料登记、登录授权或外部存储。

同次较宽前端检查保留一项旧静态断言失败：`PmsFileArtifact.spec.ts` 要求旧项目工期表单含 `form.reasonType === 'CUSTOMER_DELAY'`。没有改该表单或断言。FileContractAndMapperTest 的精确方法白名单补入基线已存在的 selectByTenantAndId/selectIdentity/selectActiveSets，继续禁止通用 CRUD；其当前 7 项检查通过。

跨活动边界：配置日志资产服务与原生定位符由交付件活动负责；项目创建、详情、历史迁移为独立活动。生产库、真实历史迁移、部署、合并及新增提交推送均未实施。

## MIME 缺口及 WIP 实施

检查点共享组件以 application/octet-stream 替代缺失 MIME；初始化和三方一致性校验使日志/配置文本被拒绝，已由红测试复现。拟把空声明/未知声明当作待检测提示，按已支持的安全文本用途处理，并在完成阶段保留字节检测和用途白名单裁决；不能按扩展名信任，也不放开所有 octet-stream。现已增加仅 log/cfg/conf/txt + Owner text/plain 白名单 + Tika 字节 text/plain + 合法 UTF-8/控制字符检查的未知提示分支；伪扩展可执行/二进制、用途拒绝、明确不匹配、缺失 MIME/正常旧文本用例通过。未扩大 Owner 白名单，初始化 HTTP/存储及真实原生日志用途仍待联合验收。

本机最高迁移为 V397，未发现 V398 文件或迁移序号登记。材料版本迁移拟用 V398；须先核对未下载的首包 manifest，现已新增 V398，仅在本任务 H2 fixture 的现有材料表执行；未执行生产/全链迁移，历史 SQL 未改。

## 首包下载阻塞

目标 Library 文件为 libfile_24add5fa169481919733f29c51037b2e（npdms-native-delivery-fda3-20261006.zip）。完整 list 结果按 selection 000 交当前下载 helper，完整 companion 就位后请求报 hosted apps tools/list request failed: network，原样重试一次仍相同；本环境没有 ZIP 字节，未核验 ZIP/补丁实际哈希、未解压、未审查 41 文件重叠、未 git apply。预期 ZIP SHA256 为 941b627189a07d7b52d8a9837da78ca7c7e1530f97d19cb942af95a80e3d3e7b，仅为父任务提供的期望值。

按后续本轮明确指令，Library 首包失败仅阻塞整合，三项公共实现已独立推进，并新增用户授权的 Git 临时分支交接；已停止下载路径，没有改用 read/prepare_materialize、手动 URL 或猜测另环境目录。当前 [Library SKILL.md](skill://plugin_connector_1p_1b8ff8edfc1481918b252c8277e23125/library/SKILL.md) 同时要求：“On this route, never separately call read or prepare_materialize”。这是访问链路网络错误，不是自动审批拒绝；需要恢复该路径或由父任务明确改变交接路线。
