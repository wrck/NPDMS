# 上游来源与基础工程决策

> 状态：已接受
> 决策日期：2026-07-28
> 对应任务：`T-CP-001`

## 1. 锁定的上游来源

| 来源 | 分支 | 锁定提交 | 用途 |
| --- | --- | --- | --- |
| [yudao-boot-mini](https://gitee.com/yudaocode/yudao-boot-mini) | `master-jdk25` | `e6d814cb59cfc204f02aa2516799073382aba801` | 根工程、`yudao-dependencies`、`yudao-framework`、`yudao-module-system`、`yudao-module-infra`、`yudao-server`及其共享配置 |
| [YunaiV/ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro) | `master-jdk25` | `a6558325b0f09017f531f1e5891613ef9b468132` | mini 缺失的 Yudao 模块及其最小配套增量，首个目标为 BPM |
| [yudao-ui-admin-vue3](https://gitee.com/yudaocode/yudao-ui-admin-vue3) | `master` | `2d028c8f7a14dd2e532ac1a76d1fdf58840dc621` | 响应式 Vue 3 管理端完整源码 |

远端分支在决策日仍分别指向上述提交。导入后的代码不跟随分支自动漂移；
升级必须重新锁定提交、生成差异清单并通过回归验证。前端 Gitee 与
GitHub 镜像在决策日的`master`提交不同，本项目以 mini README 明示且同属
`yudaocode`组织的 Gitee 仓库为权威源，不混用 GitHub 镜像。

## 2. PD-001 至 PD-006

| 编号 | 已接受方案 | 理由与约束 |
| --- | --- | --- |
| PD-001 上游导入 | 当前仓库作为主仓，按锁定提交导入源码快照，不合并上游 Git 历史 | 便于审查、离线构建和精确追溯；每次导入按文件范围登记来源 |
| PD-002 前端仓库 | 响应式 Vue 3 管理端与后端同仓，目标目录为 `yudao-ui/yudao-ui-admin-vue3`；来源为本文件锁定的 Gitee 提交 | 前后端在宿主机构建和运行，与 Docker 基础设施联合验收；移动端、桌面客户端不进入 V1/V2 |
| PD-003 关系型数据库 | MySQL 8.4 LTS | mini 默认配置、SQL 和 Docker 编排以 MySQL 8 为主；LTS 版本降低基础平台漂移风险 |
| PD-004 数据库迁移 | 使用 Docker 中的 Flyway Open Source CLI 执行版本化 SQL；应用不新增 Flyway 运行时依赖 | 迁移在应用启动前独立执行，`flyway_schema_history`提供顺序、校验和与重复执行证据 |
| PD-005 浏览器自动化 | 使用 Playwright，在宿主机对宿主机前后端执行真实浏览器验收 | 覆盖登录、点击、填写、保存、刷新、返回及状态流转；浏览器与项目 Playwright 依赖保持兼容 |
| PD-006 本地基础设施 | Docker Compose 是 MySQL、Redis 和 Flyway 的唯一权威本地运行入口 | 后端与前端在宿主机运行，Docker 仅承载基础设施；文件服务先复用基础平台本地存储 |

参考依据：

- mini `README.md`声明默认技术组合为 MySQL、MyBatis Plus、Redis 和 Redisson，并支持 MySQL 8。
- mini `script/docker/docker-compose.yml`提供 MySQL 8、Redis、后端和管理端编排样例。
- [MySQL 8.4 Reference Manual](https://dev.mysql.com/doc/refman/8.4/en/)将 8.4 定义为 LTS 系列。
- [Flyway Docker 官方文档](https://documentation.red-gate.com/flyway/reference/usage/flyway-docker)说明 Open Source 用户可使用 `flyway/flyway`镜像和项目目录执行迁移。
- [Playwright Docker 官方文档](https://playwright.dev/docs/docker)要求浏览器镜像与项目 Playwright 版本一致，并建议固定镜像版本。

## 3. 快照优先级与导入规则

1. 根 POM、依赖管理、framework、system、infra、server 和共享 SQL 以 mini 锁定提交为准。
2. 完整仓库只提供 mini 不存在的模块，以及该模块运行所必需的根 POM、server、SQL、菜单权限和前端差异。
3. 同一路径文件不能直接用完整仓库版本覆盖；必须先比较，提取最小兼容补丁，并在本文件追加来源记录。
4. PMS 自研代码不得混入上游机械快照提交。
5. 上游平台 API 不重命名、不改路径、不包一层自定义“管理端业务 API”；PMS 新接口按本项目 API 规范实现。

## 4. 宿主机应用与 Docker 基础设施边界

目标拓扑：

```text
browser/e2e
     |
admin (host Node.js + responsive web, 18081)
     |
server (host JDK 25, 58080)
     |------ redis (Docker)
     |------ file volume
     |
migrate (Docker Flyway CLI) ---> mysql 8.4 LTS (Docker)
```

- 数据库健康后才能执行 `migrate`，`migrate`成功后才能启动宿主机 `server`。
- Maven 使用宿主机本地仓库；前端通过 Corepack 锁定 pnpm `9.15.5`，并复用全局 store，`node_modules` 仅保留项目级链接与必要元数据。
- Compose 中的镜像必须使用明确版本线，不使用 `latest`；本地优先复用已有
  镜像，仅在缺失或明确不兼容时拉取，实际补丁版本与 digest 作为验证证据。
- `.env.example`只提供开发默认值和变量说明，真实凭据由部署环境注入。
- 生产部署可替换数据库、Redis、文件服务和入口网关，但不得改变应用契约。

## 5. 已识别的待确认项

| 事项 | 状态 | 阻塞范围 | 处理方式 |
| --- | --- | --- | --- |
| 生产环境文件服务产品 | 【待确认】本地验证不需要外部对象存储 | 生产部署方案，不阻塞 CP-01/CP-02 | V1 发布设计时根据容量、合规和备份要求选择 |
| 生产数据库高可用形态 | 【待确认】 | 生产部署方案，不阻塞 Docker 本地闭环 | V1 发布设计时确定主从、备份、恢复目标与运维责任 |
| BPM 自定义业务表 MySQL DDL | 锁定的公开源码仓库不提供生产 DDL；官方文档要求另行下载受许可约束的 BPM SQL 附件 | 阻塞 T-CP-006 空库初始化与 BPM 运行验收，不阻塞 T-CP-004 源码机械导入 | 使用已获授权且与锁定代码匹配的官方 BPM SQL；不得根据 DO 猜测生产表结构 |

## 6. 当前导入批次

| 批次 | 来源提交 | 文件范围 | 状态 |
| --- | --- | --- | --- |
| `T-CP-002` | mini `e6d814c...` | `pom.xml`、`LICENSE`、`lombok.config`、`yudao-dependencies/`、`yudao-framework/` | 已导入；393个文件通过SHA-256逐文件一致性校验 |
| `T-CP-003` | mini `e6d814c...` | `yudao-module-system/`、`yudao-module-infra/`、`yudao-server/` | 已导入；795个文件通过SHA-256逐文件一致性校验 |
| `T-CP-004` | full `a655832...` | `yudao-module-bpm/`、BPM测试starter、`sql/mysql/ruoyi-vue-pro.sql`中的官方 BPM 菜单与字典、根 POM 和`yudao-server`最小装配 | 已导入；BPM模块及测试starter共262个文件逐文件保持上游一致；生产 DDL 缺口转入 T-CP-006 |
| `T-CP-005` | frontend `2d028c8...` | 完整 Vue 3 管理端 | 已导入；在实施基线 `1a93fad...` 中纳入，并在 `3c54ee1...` 锁定宿主机 pnpm 构建入口 |

## 7. 已验证的上游构建特性

mini 根 POM 在`dependencyManagement`中导入同仓的
`yudao-dependencies` BOM。全新 Maven 缓存尚未安装该 BOM 时，
`mvn -N help:evaluate`会在根 POM 模型解析阶段失败；这一行为已在未修改的
mini 锁定提交中复现，不是本项目快照差异。

因此`T-CP-002`使用以下 Docker/JDK 25 命令校验 BOM 中的 revision，并由
`T-CP-003`的完整 reactor 构建继续验证根工程装配：

```shell
docker run --rm \
  -v pms-maven-repo:/root/.m2 \
  -v "$PWD:/workspace" \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-25 \
  mvn -N -f yudao-dependencies/pom.xml \
  help:evaluate -Dexpression=revision -q -DforceStdout
```

验证结果：`2026.06-jdk25-SNAPSHOT`。

完成`T-CP-003`后，以下命令在全新 Docker/JDK 25 构建环境中成功，首次
构建耗时441.6秒，后续构建复用`pms-maven-repo`具名缓存卷：

```shell
docker run --rm \
  -v pms-maven-repo:/root/.m2 \
  -v "$PWD:/workspace" \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-25 \
  mvn clean package -DskipTests
```

截至`T-CP-003`，`git diff --check`会报告 mini 锁定提交中继承的17处
空白格式诊断：`yudao-framework`有9处，system/infra有8处，合计2处行尾
空格及15处文件末尾空行。为保持机械快照的逐文件哈希一致性，本批次原样
保留；本项目新增和修改的文档已单独通过`git diff --check`，后续自研代码
不得新增此类诊断。

## 8. 上游来源的可复现性

`._codex_work/upstream/` 下的三个浅克隆是导入期的临时缓存，已完成逐文件
校验，但不属于当前实施仓库基线，当前工作树中不存在这三个目录。
可复现性以本文件第 1 节的三个锁定提交、第 6/9 节的导入清单和 Git 中
已提交的目标文件为准。需重新核验上游时，按锁定提交重建临时克隆，
在克隆内设置仓库级 `core.longpaths=true`，不修改用户全局 Git 配置。

## 9. T-CP-004 BPM 导入清单

### 9.1 文件来源与兼容补丁

| 当前路径 | 来源 | 处理 |
| --- | --- | --- |
| `yudao-module-bpm/**` | full `a6558325b0f09017f531f1e5891613ef9b468132`同路径 | 250个文件机械导入，不修改上游源码 |
| `yudao-framework/yudao-spring-boot-starter-test/**` | full `a6558325b0f09017f531f1e5891613ef9b468132`同路径 | 12个文件机械导入，为BPM上游测试提供同仓测试基座 |
| `sql/mysql/ruoyi-vue-pro.sql` | full `a6558325b0f09017f531f1e5891613ef9b468132`同路径 | 机械导入；SHA-256为`1E78255B50C4AFE687FC60BDE7414E2AEFE4376E017801A93D909862E1C6F222`，包含该提交已有的BPM菜单、权限和字典数据 |
| `pom.xml` | mini `e6d814cb59cfc204f02aa2516799073382aba801` | 仅取消`yudao-module-bpm`模块注释 |
| `yudao-server/pom.xml` | mini `e6d814cb59cfc204f02aa2516799073382aba801` | 仅取消`yudao-module-bpm`依赖注释 |
| `yudao-dependencies/pom.xml` | mini `e6d814cb59cfc204f02aa2516799073382aba801`为基线；兼容条目来自full `a6558325b0f09017f531f1e5891613ef9b468132` | 保留mini的Flowable `8.0.0`管理，仅增加BPM POM所需的同仓`yudao-spring-boot-starter-test`版本管理 |
| `yudao-framework/pom.xml` | mini `e6d814cb59cfc204f02aa2516799073382aba801`为基线 | 仅装配新增的`yudao-spring-boot-starter-test`模块 |

没有导入完整仓库的其它共享 POM、framework、system、infra 或 server
文件，也没有在本任务导入前端或 PMS 业务代码。

### 9.2 官方生产 DDL 追溯结论

锁定提交及同版本标签`v2026.06(jdk25)`的
`sql/mysql/ruoyi-vue-pro.sql`包含 BPM 菜单、权限与字典数据，但不包含
`CREATE TABLE bpm_*`。官方`feature/bpm`分支的公开 SQL 同样未提供这些
生产表结构。BPM 模块测试资源只提供 H2 测试用的
`bpm_user_group`、`bpm_category`和`bpm_form`三张简化表，不能作为
MySQL 生产迁移。

当前锁定代码存在8个BPM自定义业务表映射：

- `bpm_category`
- `bpm_form`
- `bpm_user_group`
- `bpm_process_definition_info`
- `bpm_process_expression`
- `bpm_process_listener`
- `bpm_process_instance_copy`
- `bpm_oa_leave`

官方文档<https://doc.iocoder.cn/bpm/>明确将 BPM SQL 作为单独附件提供，
并声明许可约束；同时说明 Flowable 启动时仅自动创建`ACT_`和`FLW_`
引擎表。因此，T-CP-006 在获得与上述锁定代码匹配且已授权的官方 BPM SQL
前，不得宣称空库 BPM 运行闭环通过，也不得根据 DO 自行推导生产 DDL。

### 9.3 导入验证

2026-07-28 在 `maven:3.9.11-eclipse-temurin-25` 容器中执行：

```text
mvn -pl yudao-module-bpm -am test
```

结果为 `BUILD SUCCESS`：BPM 测试共执行 50 个，0 失败、0 错误、6 个
上游禁用用例跳过；包含 BPM 的 19 个 Reactor 模块全部成功。随后通过
`mvn -pl yudao-server -am package -DskipTests` 验证服务端装配。

此外，BPM 模块 250 个文件、测试 starter 12 个文件均通过逐文件
SHA-256 一致性校验，官方 SQL 文件的源端与目标端 SHA-256 均为
`1E78255B50C4AFE687FC60BDE7414E2AEFE4376E017801A93D909862E1C6F222`。

## 10. T-CP-006 宿主机应用与 Docker 基础设施基线

### 10.1 运行时与迁移来源

| 用途 | 锁定版本或来源 | 说明 |
| --- | --- | --- |
| 后端构建/运行 | 宿主机 JDK `25.0.1`、Maven `3.8.6` | Maven 构建与 JDK 25 运行均在宿主机执行 |
| 前端构建/运行 | 宿主机 Node.js `24.11.1`、Corepack 锁定 pnpm `9.15.5` | 构建和 Vite 开发服务均在宿主机执行；引擎下限为 Node.js `20.19.0` |
| 数据库 | `mysql:8.4` | 优先复用本机已有镜像；实际运行 8.4.10 |
| 缓存 | `redis:7.4-alpine` | 优先复用本机已有镜像；实际运行 7.4.9 |
| 数据库迁移 | `flyway/flyway:11.10.5-alpine` | 独立 CLI 服务；应用不引入 Flyway runtime |
| 初始迁移 | `sql/mysql/ruoyi-vue-pro.sql` | 机械复制为 `sql/migrations/V1__yudao_platform.sql` |

初始迁移的源文件与目标文件 SHA-256 均为
`1E78255B50C4AFE687FC60BDE7414E2AEFE4376E017801A93D909862E1C6F222`。
Flyway 首次迁移、`info`、`validate` 以及重复 `migrate` 均已在 MySQL 8.4
容器中执行成功。Flyway 11.10.5 会提示 MySQL 8.4 高于其已测试到的 8.1，
当前验证未发现迁移或校验失败，后续升级 Flyway 时需重新执行空库与升级库
两类回归。

按用户确认的本地镜像策略，不为锁定补丁标签重复下载：本地已有镜像优先，
仅在镜像缺失或出现明确不兼容时才拉取。当前验证所用镜像证据：

- MySQL 8.4.10：
  `mysql@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d`
- Redis 7.4.9：
  `redis@sha256:6ab0b6e7381779332f97b8ca76193e45b0756f38d4c0dcda72dbb3c32061ab99`

### 10.2 宿主机应用兼容补丁

锁定上游 system 模块中的微信公众号配置类在运行时引用 Apache
HttpClient 4 API，而上游 starter 将客户端依赖声明为 `provided`。实际
宿主机启动的历史验证中曾出现
`NoClassDefFoundError: org/apache/http/conn/HttpClientConnectionManager`。
本项目在 `yudao-module-system/pom.xml` 中显式增加运行时依赖
`org.apache.httpcomponents:httpclient:4.5.14`，不修改上游接口和业务逻辑。

宿主机运行配置使用非敏感的禁用占位值满足上游微信组件装配，不保存可用
应用凭据。数据库、Redis 和 MyBatis 加密器密钥均只从运行环境注入；
`.env.example` 的敏感字段保持为空。

宿主机管理端使用相对 `VITE_API_URL=/admin-api`，Vite 开发服务通过
`VITE_PROXY_TARGET=http://localhost:58080` 代理后端，前端端口固定为 `18081`。

V1 项目兼容配置将默认 `yudao.tenant.enable` 与宿主机前端
`VITE_APP_TENANT_ENABLE` 同时设为 `false`。这符合官方“两端开关必须一致”
的约束，并保留租户模块以便后续同步启用。未导入的 AI 提供商及交易订单
向微信小程序同步使用已有 `enable` 开关关闭；其余边界不清的集成不修改。

### 10.3 BPM 数据库边界复核

服务端可启动，Flowable 自动创建了 `ACT_*` 与 `FLW_*` 引擎表；公开 SQL
中缺失的八张 `bpm_*` 自定义业务表仍未出现。该结果再次证明引擎自动建表
不能替代 BPM 业务表迁移。在取得已授权且与锁定代码匹配的官方 BPM SQL
前，不将 BPM 流程定义、表单和用户组等业务操作计入 Docker 基线通过范围。

因此当前状态必须拆分表述：宿主机应用与 Docker 基础设施运行基线已经建立；T-CP-006
整体及 BPM 空库业务闭环尚未完成，依赖它的 T-CP-010 仍受阻。只有取得
已授权的官方八张 `bpm_*` 表 DDL 并完成流程创建、查询验收后，才能更新
任务勾选。
