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

默认初始化租户1，并启用原有多租户机制：后端 `yudao.tenant.enable=true`，
前端 `VITE_APP_TENANT_ENABLE`默认开启。前端从可用租户列表默认选择首项，
只有一项时隐藏登录租户字段及顶部切换框，多项时保留原选择能力。
V204仅默认停用精确识别的预置示例/验收租户，不删除历史或已有业务租户。
后端租户源码、数据库隔离、授权、后台任务和消息机制均保持上游实现；
隐藏界面不是服务端固定租户白名单。部署时不要再用`enable=false`关闭整套机制。

本平台未导入 AI、商城或交易业务模块，因此已通过现有官方开关关闭豆包、
混元、硅基流动、星火、百川、Midjourney、Suno、Web Search，以及交易订单
向微信小程序同步。微信 MP/MiniApp starter 未发现官方总开关，当前继续使用
非凭据 `disabled` 哨兵完成平台装配，【待确认】后续由平台升级或依赖裁剪处理。
Spring AI 自动配置、WebSocket、API 加密和 JustAuth 的影响边界未确认，本次
不修改。

## 首次启动

### 已排除退役对象的空库初始化入口

正式初始化以 [178 来源的 B530 发布包](../sql/releases/production-178-v530-r2/README.md)
为准：结构与选定正式配置直接来自 178 最终快照，不再从 B498 或历史 SQL 回放推导。
已有库仍沿用原迁移链。`compose.baseline.yaml` 仅用于本地空库演练，正式服务器
使用 `scripts/production/operate.sh` 的原生 Flyway 入口。

先执行以下纯文件操作，不连接数据库：

```powershell
python -B scripts/prepare_baseline_migrations.py
python -B scripts/prepare_baseline_migrations.py --check
```

产物在 `.run/baseline-migrations`，`manifest.json` 记录来源 SHA-256 和排除依据。
生成器核对冻结 B530 摘要、重复版本、已退役对象重新创建及输出目录混入其他文件；
原 SQL 按字节复制，只追加 V530 之后的迁移，不重新执行来源历史。
每次增加正式迁移后重新生成并检查。本地验证目标连接使用
`NPDMS_RELEASE_JDBC_URL`、`NPDMS_RELEASE_DB_USER`、`NPDMS_RELEASE_DB_PASSWORD`，
不提供默认目标或凭据。经目标库执行授权后才可使用
`docker compose -f compose.baseline.yaml run --rm migrate migrate`。

此入口仅供新空库及由此入口初始化的数据库后续升级。已有数据库继续各自原迁移链；
不得把 `compose.fresh.yaml` 的旧 fresh 链切换到此入口，不执行 `repair` 或改历史记录。
管理员执行新包的 `bootstrap-admin.sql`；文件存储与外部连接使用目标环境的实际配置，
不得复制 178 凭据。新包已有正式模板及其流程定义，不使用旧包的草稿发布说明或数量校验。
结构、配置及初始化验证见新包说明；正式环境的人员授权和浏览器业务验收仍须单独完成。

### 1. 启动基础设施（Docker）

```powershell
.\docker\scripts\new-local-env.ps1
docker compose config --quiet
docker compose up -d mysql redis migrate
docker compose ps
```

脚本只在本地生成被 Git 忽略的 `.env`，并拒绝覆盖已有文件。
`.env.example` 中的敏感项保持为空，不能直接用于启动。

默认 `FILE_SECURITY_SCAN_ENABLED=false`，后端不装配ClamAV Provider，
文件仍执行大小、摘要、扩展名、MIME、内容嗅探和策略校验，并以
`scanStatus=SKIPPED`记录未执行病毒扫描。启用病毒扫描时同时启动ClamAV并设置：

```powershell
$env:FILE_SECURITY_SCAN_ENABLED='true'
docker compose --profile security-scan up -d clamav
```

只开启后端扫描配置但ClamAV不可用时，上传严格失败关闭，不会降级为`SKIPPED`。

### 2. 启动后端（宿主机）

后端连接 Docker 中的 MySQL（`10.210.0.11:13306`）与 Redis（`10.210.0.11:16379`）。
请确保 `yudao-server` 的本地配置（`application-local.yaml` 或对应 profile）
指向上述宿主机端口，并监听 `58080`。

```powershell
# 构建后端（宿主机 JDK 25）
  powershell.exe -NoProfile -File scripts/worktree-build.ps1 -Target backend -MavenArguments '-DskipTests'
  # 默认58080环境启动（59191验收环境请使用下述统一入口）
java -jar yudao-server/target/yudao-server.jar --server.port=58080
```

当前工作树59191验收环境采用 `scripts/worktree-backend.ps1 -Manifest <发布清单> -ValidateOnly` 检查发布产物、当前运行基线及 `local,dac` 配置；取得本次明确重启批准后，改用 `-ApprovedRestart` 部署。构建入口输出 `.run/build/releases/<ID>/manifest.json`，不自动部署，也不自动选择其他会话的“最新target”。同一工作树各会话使用同一工具链、`.run/build/m2` 及独占锁；编译期间源码变动、产物改动或准备后运行包被其他部署替换均阻止继续部署。新的已授权功能可通过新发布清单及时部署；若修改受保护的已验证类，应先复核差异并更新批准基线，不能删减校验绕过。

源码一致性按构建目标的Maven工程分别检查：`backend`覆盖根POM声明的模块及共享框架，`dac`覆盖`device-ops-platform`声明的模块及其实际打入JAR的`device-ops-web/dist`。各模块POM、源码、资源、Maven配置及共享构建工具均纳入对应指纹；独立DAC工程、平台前端依赖清单的变更不阻止平台后端冻结，平台适配代码的变更仍阻止平台后端冻结。共享工具链、独占锁、冻结产物及已验证修复校验保持不变。

临时构建可通过同一入口的`-BuildOutputRoot F:/CodexTaskArtifacts/npdms-production-853c-20261101/build`显式放到F盘。后端与DAC的编译、测试报告和JAR分别进入该目录的`backend/<artifactId>`、`dac/<artifactId>`，冻结清单进入`releases/<ID>`；源码仍从当前工作树读取，固定工具链、工作树Maven依赖仓库和独占锁保持生效。未指定参数时沿用原输出位置，不迁移或清理已有产物。生产构建仍执行完整测试；临时环境变量`TEMP/TMP`、前端独立构建副本、日志和打包暂存目录也应指向本次F盘目录。

后端已退出时，修复包可通过构建入口显式传入`-AllowOfflineRuntime`冻结；仅当原PID已不存在且59191没有其他监听者时生成离线清单，仍执行全部已验证修复校验。该清单只用于已批准的`worktree-backend.ps1 -StartIfOffline -ApprovedRestart`启动；若已有新进程运行，必须重新核对运行基线，不能用离线清单覆盖。

- 后端健康检查：<http://10.210.0.11:58080/actuator/health>
- 后端接口文档：<http://10.210.0.11:58080/doc.html>

2026-11-03本地验证：Windows保留端口段覆盖59191时，受控入口可显式传 `-Port 58191`，仍校验PID、冻结产物及 `local,dac`。首次启动允许在无PID文件且所选端口空闲时使用 `-StartIfOffline`。可用 `-EnvironmentFile <现有本地配置>` 引用原配置，支持 `NPDMS_REDIS_PASSWORD`、`NPDMS_MYBATIS_ENCRYPTOR_PASSWORD` 键，不复制明文凭据。入口按Boot4当前类名落实local原有禁用Quartz意图，并关闭模拟登录；测试库没有 `projectContractProjectionJob` 记录，本次不补数据，不把部门筛选验收作为定时同步验收。前端 `scripts/worktree-frontend.ps1 -BackendUrl http://127.0.0.1:58191` 对齐后端。禁迁移和Wall限制保持生效。

### 3. 启动前端（宿主机）

```powershell
cd yudao-ui\yudao-ui-admin-vue3
pnpm install
pnpm dev
```

前端 Vite dev server 默认监听 `18081`，通过同源 `/admin-api` 代理到后端
`58080`。管理端访问地址：<http://10.210.0.11:18081>

## 迁移校验与重复启动

### 当前59191测试库限制（2026-10-25）

用户要求将当前工作树59191连接切换到 `172.17.90.178:13306/npdms`。固定后端脚本从被Git忽略的 `.run/backend-test-database.json` 读取Windows当前用户DPAPI加密凭据，不在命令行或日志输出明文。Redis改用本地DB14隔离原库缓存。重启仍须单独明确批准。

该测试库禁止运行Flyway、Liquibase、SQL初始化及任何迁移或表结构变更；固定入口关闭上述初始化，以及Flowable、Hibernate、Quartz和Batch自动建表，并启用Druid Wall拦截建表、改表、删表、重命名、清空、其他非基础语句和存储过程调用。该拦截仅覆盖本应用连接，不改变数据库账号权限。下方迁移命令仅适用于另行明确授权的本地数据库，**不得对178测试库执行**。缺表、缺列或版本不一致应报告，不执行DDL修复。切换不搬迁、覆盖或删除原库历史。

2026-10-25经用户明确批准，以冻结清单 `e23c8046-4ea7-4e65-8eff-f1d8aecfaf5a` 通过固定脚本启动59191，PID `45968`、`local,dac`，健康检查 `UP`。进程TCP连接与数据库会话匹配，确认实际连接178的 `npdms`；运行进程配置与Actuator有效属性来源交叉核对，确认禁迁移、禁自动建表及Wall配置生效。启动前后520张表、9490列、4218条索引元数据、241个触发器及22条Flyway历史指纹一致；校验仅执行SELECT及本地配置/健康读取，不执行迁移或DDL。证据：`.run/test178-switch-20261025/{start.log,runtime-settings.json,before.json,after.json,result.json}`。本次未启动或重启前端、DAC。

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
真实浏览器或宿主机应用验收固定使用独立端口组：主后端 `59280`、
跨租户负向后端 `59282`、Vite `19081`。开发环境的 `58080` 和 `18081`
不得被验收进程占用或停止；Compose 仍只承载 MySQL、Redis 和 Flyway。

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
Invoke-WebRequest http://10.210.0.11:58080/actuator/health
Invoke-WebRequest http://10.210.0.11:18081
Invoke-WebRequest http://10.210.0.11:18081/admin-api/system/auth/get-permission-info
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


## Windows 本机 DAC

现场安装方式与配置见 [DAC 客户端说明](../device-ops-platform/packaging/windows/README.md)。客户端包自带 Java，使用独立 `desktop` 配置并监听 `127.0.0.1:48182`；NPDMS 服务器地址（含端口）和网页来源由安装向导或“DAC 设置”指定。服务端需包含本次接口并经 Compose/Flyway 应用 V511。1.0.2 同时支持 HTTP/HTTPS，新安装默认后端 `http://10.210.0.11:59191`、网页 `http://10.210.0.11:19191`；内网 HTTP 由安装器为该网页来源配置 Chrome/Edge 本地网络访问授权，并维护自己新增条目的清理。此客户端不替代既有59191宿主机后端或服务器DAC的受控启停入口。

客户端编译与打包依次执行 `scripts/worktree-build.ps1 -Target dac`、`scripts/package-dac-desktop.ps1 -Manifest <冻结清单>`。后者只生成 MSI 和证据，不执行安装或服务操作。Windows 安装/升级会管理 NpdmsDac 服务，变更当前运行环境前仍须确认影响并取得本次授权。运行数据、主密钥和各服务器环境队列保存在 ProgramData，卸载不删除；不能用切换环境将旧任务改投新服务器。

## Word方案在线预览运行依赖

Windows本地环境执行 `./scripts/setup-office-preview.ps1`，将校验官方签名及固定校验值的LibreOffice 26.2.6解包至当前用户 `.cache/npdms/office-preview/26.2.6/runtime`，不安装全局Office或服务。工具缓存约需2GiB可用空间。后端默认从该目录调用program/soffice.com；其他运行环境通过 `pms.file.office-executable` 指向本机LibreOffice命令。无需对59191与48182改变既有启动方式；后端仍通过冻结发布脚本部署，转换工具不构成DAC就绪条件。

Word由本机Writer导出PDF，使用独立临时配置，宏安全级别Very High，链接更新Never。字体由服务器提供，应安装文档使用的字体以减少重排；转换失败不替换原文件，调用方可重试。参考官方[CLI参数](https://help.libreoffice.org/latest/en-US/text/shared/guide/start_parameters.html)与[PDF导出](https://help.libreoffice.org/latest/en-US/text/shared/guide/pdf_params.html)。
