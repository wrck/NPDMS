# 本地开发运行说明

## 架构边界

Docker Compose 默认仅承载基础设施服务（MySQL、Redis、Flyway 迁移）；
ClamAV 只在显式启用 `security-scan` profile 时加载。
**前端（yudao-ui-admin-vue3）与后端（yudao-server）服务禁止运行在 Docker 中**，
必须在宿主机上分别通过 `pnpm dev` 与 `java -jar` 启动。

固化端口约定（禁止通过 `.env` 修改，避免测试过程中端口漂移）：

| 服务     | 端口   | 启动方式                                  |
| -------- | ------ | ----------------------------------------- |
| 后端     | 58080  | 宿主机 `java -jar yudao-server.jar`       |
| 前端     | 18081  | 宿主机 `pnpm dev`（Vite）                 |
| MySQL    | 13306  | Docker                                    |
| Redis    | 16379  | Docker                                    |
| ClamAV   | 13310  | Docker（可选 `security-scan` profile）    |

## 前置条件

- Docker Desktop（含 Docker Compose）——用于 MySQL、Redis、Flyway 迁移及可选ClamAV
- 宿主机 JDK 25（后端构建与运行）
- 宿主机 Node 20.19.6 + pnpm 9.15.5（前端构建与运行）

数据库使用 `mysql:8.4`，Redis 使用 `redis:7.4-alpine`，迁移由独立的
Flyway Open Source CLI 容器执行。应用本身不依赖 Flyway runtime。

本地验证优先复用已有镜像；只有镜像缺失或出现明确不兼容时才允许拉取，
不得仅为补丁标签重复下载。当前实际运行版本为 MySQL 8.4.10、Redis 7.4.9，
对应 image digest 记录在 `docs/upstream-sources.md`。

## 状态边界

Docker 基础设施运行基线已经建立并通过迁移、服务健康和连接验证。BPM 空库
业务闭环通过 `sql/migrations/V2__bpm_business_tables.sql`（来源
`sql/mysql/bpm-2025-10-04.sql`）纳入八张 `bpm_*` 业务表 DDL，T-CP-006 已
完成。

V1 默认采用单租户运行：后端全局 `yudao.tenant.enable=false`，前端
`VITE_APP_TENANT_ENABLE=false`。租户模块、表结构和 API 均保留；
后续启用多租户时必须同时将前后端开关改为 `true`，不得只改一端。

本平台未导入 AI、商城或交易业务模块，因此已通过现有官方开关关闭豆包、
混元、硅基流动、星火、百川、Midjourney、Suno、Web Search，以及交易订单
向微信小程序同步。微信 MP/MiniApp starter 未发现官方总开关，当前继续使用
非凭据 `disabled` 哨兵完成平台装配，【待确认】后续由平台升级或依赖裁剪处理。
Spring AI 自动配置、WebSocket、API 加密和 JustAuth 的影响边界未确认，本次
不修改。

## 首次启动

### 1. 启动基础设施（Docker）

```powershell
.\docker\scripts\new-local-env.ps1
docker compose config --quiet
docker compose up -d mysql redis migrate
docker compose ps
```

脚本只在本地生成被 Git 忽略的 `.env`，并拒绝覆盖已有文件。
`.env.example` 中的敏感项保持为空，不能直接用于启动。

默认 `NPDMS_FILE_SECURITY_SCAN_ENABLED=false`，后端不装配ClamAV Provider，
文件仍执行大小、摘要、扩展名、MIME、内容嗅探和策略校验，并以
`scanStatus=SKIPPED`记录未执行病毒扫描。启用病毒扫描时同时启动ClamAV并设置：

```powershell
$env:NPDMS_FILE_SECURITY_SCAN_ENABLED='true'
docker compose --profile security-scan up -d clamav
```

只开启后端扫描配置但ClamAV不可用时，上传严格失败关闭，不会降级为`SKIPPED`。

### 2. 启动后端（宿主机）

后端连接 Docker 中的 MySQL（`localhost:13306`）与 Redis（`localhost:16379`）。
请确保 `yudao-server` 的本地配置（`application-local.yaml` 或对应 profile）
指向上述宿主机端口，并监听 `58080`。

```powershell
# 构建后端（宿主机 JDK 25）
mvn -pl yudao-server -am package -DskipTests
# 启动后端（宿主机）
java -jar yudao-server/target/yudao-server.jar --server.port=58080
```

- 后端健康检查：<http://localhost:58080/actuator/health>
- 后端接口文档：<http://localhost:58080/doc.html>

### 3. 启动前端（宿主机）

```powershell
cd yudao-ui\yudao-ui-admin-vue3
pnpm install
pnpm dev
```

前端 Vite dev server 默认监听 `18081`，通过同源 `/admin-api` 代理到后端
`58080`。管理端访问地址：<http://localhost:18081>

## 迁移校验与重复启动

```powershell
docker compose run --rm migrate info
docker compose run --rm migrate validate
docker compose run --rm migrate migrate
docker compose restart mysql redis
docker compose ps
```

首个迁移 `sql/migrations/V1__yudao_platform.sql` 与锁定上游提交中的
`sql/mysql/ruoyi-vue-pro.sql` 字节一致。Flyway 将版本、执行状态与校验和
写入 `flyway_schema_history`。修改已执行迁移会使 `validate` 失败；后续数据库
变更必须增加新的 `V<版本>__<说明>.sql`，不得修改历史文件或手工改库。

## 固定测试验证环境

日常 MySQL/Redis/Flyway 集成验证固定复用 Compose 项目
`npdms-50eb-test`，数据库为 `npdms_test`，宿主机端口为 MySQL `23316`、
Redis `26379`。该环境与本地开发环境及按 Task 创建的临时环境隔离。

```powershell
# 首次创建；后续调用只启动并复用原容器和卷
.\scripts\test-infrastructure.ps1 start

# 查看包含已退出 Flyway 容器在内的完整状态
.\scripts\test-infrastructure.ps1 status
```

普通测试不得执行 `docker compose down`、`down --volumes`，也不得为每轮测试
创建新的 Compose 项目。只有明确需要从空数据状态重新验收迁移时，才执行：

```powershell
.\scripts\test-infrastructure.ps1 reset
```

`reset` 保留并复用原容器和卷，只删除并重建 `npdms_test` 数据库、清空测试
Redis，再重新启动原 Flyway 容器执行迁移。运行宿主机集成测试时，应将
`NPDMS_DB_NAME`、`NPDMS_MYSQL_PORT`、`NPDMS_REDIS_PORT` 分别设为
`npdms_test`、`23316`、`26379`；数据库用户和密码继续使用本地 `.env` 中的值。

## 验证

```powershell
.\tests\infrastructure\verify-docker-baseline.ps1
docker compose exec mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "SELECT installed_rank, version, description, success FROM flyway_schema_history;"'
docker compose exec redis sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning ping'
Invoke-WebRequest http://localhost:58080/actuator/health
Invoke-WebRequest http://localhost:18081
Invoke-WebRequest http://localhost:18081/admin-api/system/auth/get-permission-info
```

## 停止与清理

```powershell
# 仅停止基础设施
docker compose down
```

普通停止会保留 MySQL、Redis、文件与日志卷。仅在明确需要重新验证空库迁移且
确认本地数据可删除时，执行：

```powershell
docker compose down --volumes
docker compose up -d mysql redis migrate
```

## 已知 BPM 数据库边界

`sql/migrations/V2__bpm_business_tables.sql` 来自 `sql/mysql/bpm-2025-10-04.sql`，
仅包含八张 `bpm_*` 业务表 DDL，已通过 Flyway 迁移纳入空库启动流程。
不得依据数据对象猜造生产表结构；后续 BPM 表结构变更必须增加新的
`V<版本>__<说明>.sql` 迁移，不得修改历史文件或手工改库。
