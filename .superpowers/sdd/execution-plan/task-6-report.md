# Task 6 Report — T-CP-006 数据库迁移、本地配置与 Docker 基线

## Status

INFRASTRUCTURE_BASELINE_READY / T-CP-006_BLOCKED — Docker 基础设施运行基线
已建立：平台 SQL 迁移、重复迁移、JDK 25 后端、Vue 管理端、MySQL、Redis
与文件持久卷均已验证。但 T-CP-006 整体还要求 BPM 空库业务闭环，公开上游
缺少八张 BPM 自定义业务表 DDL，因此 T-CP-006 及依赖它的 T-CP-010 均保持
未完成。容器可启动不代表 BPM 业务已验收。

## Implementation summary

- 新增 `compose.yaml`，装配 MySQL 8.4.10、Redis 7.4.9、独立 Flyway CLI、
  JDK 25 后端及 Node 20 前端，并为各服务配置启动依赖与健康检查。
- 新增后端、前端 Dockerfile 和 Docker 专用配置；前端沿用完整 Vue3
  管理端，未新增 PMS 页面。
- 将锁定官方 SQL 字节级复制为
  `sql/migrations/V1__yudao_platform.sql`，应用本身不引入 Flyway。
- 新增无真实凭据的 `.env.example` 和仅在本地生成随机凭据的
  `docker/scripts/new-local-env.ps1`；生成的 `.env` 被 Git 忽略。
- 新增静态验收脚本，校验镜像版本、敏感字段为空、迁移哈希一致、
  应用无 Flyway 依赖，以及 Compose 配置门禁。
- 补充 `docs/development.md`、`docs/upstream-sources.md`；Docker 基线
  通过，但 `tasks/todo.md` 中 T-CP-006 因 BPM DDL 阻塞而保持未勾选。
- 为锁定上游 system 模块补充 Apache HttpClient 4.5.14 运行时依赖，
  解决 Docker 启动时微信公众号配置类缺失
  `HttpClientConnectionManager` 的实际兼容问题。

## Runtime versions

- Java：Temurin OpenJDK `25.0.1`
- Maven 构建镜像：`maven:3.9.11-eclipse-temurin-25`
- Node：`20.19.6`；pnpm：`9.15.5`
- MySQL：`8.4.10`
- Redis：`7.4.9`
- Flyway OSS：`11.10.5`

## Commit

- `68b2b4c build(docker): 建立基础平台运行基线`
- 14 files changed, 5750 insertions, 1 deletion
- 修复轮次 1：`63d5aa8 fix(docker): 修复管理端同源 API 代理`
- 6 files changed, 87 insertions, 11 deletions

## Verification evidence

1. 静态基线：
   `.\tests\infrastructure\verify-docker-baseline.ps1`
   - 通过：`Docker baseline static verification passed.`
   - `.env.example` 敏感字段为空且不能直接启动。
   - 本地随机 `.env` 的 Compose 配置校验通过。
2. 官方迁移一致性：
   - 源端与 V1 迁移 SHA-256 均为
     `1E78255B50C4AFE687FC60BDE7414E2AEFE4376E017801A93D909862E1C6F222`。
   - Flyway 首次 `migrate`、`info`、`validate` 和重复 `migrate` 均成功。
   - 历史记录：版本 `1`、描述 `yudao platform`、校验和
     `-405251116`、`success=1`。
3. 完整后端校验：
   - Docker/JDK 25 执行 `mvn clean verify`。
   - `BUILD SUCCESS`，22/22 Reactor 模块成功，总耗时 8 分 14 秒。
   - BPM 测试 50 个：0 失败、0 错误、6 个上游禁用用例跳过。
4. 首次启动与服务连接：
   - MySQL、Redis、server、frontend 均为 `healthy`；
     migrate 为 `Exited (0)`。
   - Redis 返回 `PONG`。
   - 后端 `/actuator/health` 返回 HTTP 200；前端返回 HTTP 200。
   - server 容器对 `/data/pms/files` 完成写入、读取和清理探针。
5. 保留持久卷的完整重启：
   - 执行 `docker compose down`（未使用 `--volumes`）后再次
     `docker compose up -d`。
   - 所有长期服务重新达到 `healthy`，migrate 再次 `Exited (0)`。
   - Flyway 日志显示 schema 当前版本为 1 且
     `No migration necessary`；历史记录仍只有同一条成功迁移。
   - 重启后后端与前端再次返回 HTTP 200。

## Runtime findings and fixes

1. 首次服务启动在 WxJava 自动配置阶段报告 Apache HttpClient 4 类缺失。
   锁定上游 starter 将该客户端声明为 `provided`，但 system 配置类存在
   直接运行时引用。显式增加 4.5.14 运行时依赖后，服务启动并通过健康检查。
2. 直接排除 WxJava 自动配置会导致上游 system bean 缺失，因此 Docker
   profile 改用非敏感的禁用占位配置满足装配；不保存可用微信凭据。
3. 前端健康检查最初使用 Node `fetch` 后未显式退出，导致 Docker 超时。
   改为按 HTTP 结果调用 `process.exit` 后稳定达到 `healthy`。

## Review fix round 1

评审发现原 Docker 前端将浏览器 API 写死为
`http://localhost:48080/admin-api`，且 `/admin-api` 请求在 Vite preview
中回退为 SPA HTML。根因是 Docker 构建环境提供了绝对 `VITE_BASE_URL`，
同时 preview 未配置代理。

修复内容：

- 浏览器基址改为空，API 固定使用同源相对路径 `/admin-api`。
- Vite preview 将 `/admin-api` 原路径代理到 Compose 内部
  `http://server:48080`，不依赖宿主 `PMS_SERVER_PORT` 或访问者主机名。
- 按用户确认的策略复用本机已有 `mysql:8.4` 与
  `redis:7.4-alpine`，仅在镜像缺失或明确不兼容时才拉取；实际运行版本
  和 image digest 作为验证证据。
- 修正 POM 扫描对 `._codex_work` 的 Windows/Unix 跨分隔符排除正则。
- 统一文档与任务状态：Docker 基础设施运行基线已建立，T-CP-006 和
  T-CP-010 仍受 BPM 官方 DDL 缺口阻塞。

修复验证证据：

1. RED：增加完整镜像标签、同源 API、preview 代理与跨分隔符断言后，静态
   脚本首先按预期报告缺少新约束；用户随后确认本地已有镜像优先，测试同步
   调整为版本线标签，不再要求补丁标签下载。
2. GREEN：`verify-docker-baseline.ps1` 通过，`docker compose config
   --quiet` 通过。
3. 前端以既有 `node:20.19.6-bookworm`、pnpm 9.15.5 重新构建成功，
   Vite 8.1.4 构建耗时 46.19 秒；未引入新运行时服务器镜像。
4. 全栈启动后 MySQL、Redis、server、frontend 均为 `healthy`，
   migrate 为 `Exited (0)`。
5. 前端同源
   `/admin-api/system/auth/get-permission-info` 返回
   `application/json`，响应
   `{"code":401,"data":null,"msg":"账号未登录"}` 与后端直连完全一致；
   构建产物不再包含 `localhost:48080/admin-api`。
6. 使用 `PMS_SERVER_PORT=49080` 执行 Compose 配置渲染时，宿主发布端口
   变为 49080，而容器内部后端目标仍为 48080，证明宿主端口不会进入浏览器
   API 地址。
7. Flyway `validate` 成功；重复 `migrate` 显示 schema 版本 1 且
   `No migration necessary`。
8. 最终 Compose 复用本机已有 `mysql:8.4` 与 `redis:7.4-alpine`：
   实际运行版本分别为 8.4.10 和 7.4.9，image digest 分别为
   `sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d`
   与
   `sha256:6ab0b6e7381779332f97b8ca76193e45b0756f38d4c0dcda72dbb3c32061ab99`。
9. 本轮未改动后端文件，因此按评审要求没有重复执行全量 Maven verify；
   前一轮 22/22 Reactor 的成功证据保持有效。

## Fix round 2 — 单租户登录配置

### Root cause

运行态可稳定复现：
`POST /admin-api/system/auth/login` 未携带 `tenant-id`，HTTP 200、
业务 `code=400`，消息为“请求的租户标识未传递，请进行排查”；
`TenantSecurityWebFilter:90` 记录同一路径未传递租户编号。

锁定上游后端默认 `yudao.tenant.enable=true`，前端公共 `.env` 默认
`VITE_APP_TENANT_ENABLE=true`。本项目因安全约束未导入包含凭据的上游
`.env`，但 Docker 前端配置没有补回其中非敏感的租户开关，形成后端
`true`、前端缺失（等效未启用）的配置漂移。前端因而隐藏租户字段、跳过
租户编号查询，Axios 请求拦截器也不写 `tenant-id`。官方多租户文档
<https://doc.iocoder.cn/saas-tenant/> 明确要求前后端开关保持一致。

只给同一无效账号请求增加 `tenant-id: 1` 后，请求不再命中租户缺失，
而进入正常认证并返回业务码 `1002000000`，验证单一根因假设成立。

### RED / GREEN

- RED 1：新增 `verify-single-tenant-login.ps1` 后，首先因 Docker 前端缺少
  显式单租户开关失败。
- RED 2：补配置但尚未重建时，运行验证仍因旧镜像返回租户缺失而失败。
- 修复：全局 `application.yaml` 设置 `yudao.tenant.enable=false`；
  Docker profile 不重复覆盖；前端 Docker 设置
  `VITE_APP_TENANT_ENABLE=false`。租户模块、表和 API 均保留。
- GREEN：重建并启动后的验证结果追加于本轮最终运行证据。

### Optional configuration check

- 已关闭：未导入的豆包、混元、硅基流动、星火、百川、
  Midjourney、Suno、Web Search，以及交易订单向微信小程序同步；均使用
  已存在的 `enable` 开关。
- 保持开启：Flowable、文件、任务、认证和权限相关能力。
- 【待确认】微信 MP/MiniApp starter 未提供官方总开关，保留非凭据
  `disabled` 哨兵；Spring AI 自动配置、WebSocket、API 加密与 JustAuth
  的影响边界未确认，本轮不修改。

### Final GREEN evidence

- Docker/JDK 25 后端重新构建：21 个 package reactor 模块成功，
  `BUILD SUCCESS`，50.820 秒；未重复执行全量 Maven verify。
- Node 20.19.6 / pnpm 9.15.5 前端重新构建成功，Vite 构建耗时 1 分 15 秒。
- `verify-docker-baseline.ps1` 与
  `verify-single-tenant-login.ps1` 均通过。
- 不带 `tenant-id` 的错误账号登录不再返回租户缺失，改为正常认证业务码
  `1002000000`（账号密码不正确）；未使用或输出真实管理员密码。
- 重启后 MySQL、Redis、server、frontend 全部 `healthy`，migrate
  `Exited (0)`；前端根页面 HTTP 200，同源 API HTTP 200 且为
  `application/json`。
- 新 server 容器日志中没有“未传递租户编号”或“请求的租户标识未传递”。
- 修复提交：`94e9e40 fix(auth): 对齐单租户登录配置`（6 个文件，
  106 行新增、11 行删除）；未推送。

## Self-review

- 所有镜像使用明确版本，不使用 `latest`。
- 数据库、Redis 和 MyBatis 加密器密钥只由部署环境注入；提交范围不含
  `.env` 或任何真实凭据。
- V1 为官方 SQL 的机械副本；后续只能新增迁移，不能修改历史迁移。
- 数据库迁移由独立 Flyway CLI 负责，应用 POM 未新增 Flyway 依赖。
- 普通停止不删除持久卷；空库清理命令在文档中附有显式数据删除警告。

## Known boundary

- Flyway 11.10.5 提示 MySQL 8.4 高于其已测试到的 8.1；本次迁移、校验及
  重启均成功。后续升级 Flyway 时需重新执行空库与升级库回归。
- Flowable 已自动创建 `ACT_*`、`FLW_*` 引擎表，但公开上游 SQL 仍缺少
  `bpm_category`、`bpm_form`、`bpm_user_group`、
  `bpm_process_definition_info`、`bpm_process_expression`、
  `bpm_process_listener`、`bpm_process_instance_copy` 和
  `bpm_oa_leave`。取得与锁定版本匹配且授权可用的官方 BPM SQL 前，
  不猜造生产 DDL，也不宣称 BPM 业务闭环通过。
