# Phase 3运行事实盘点

> 日期：2026-08-13
> 规格仓库：当前工作树
> 实现仓库：`E:\AICoding\Projects\NPDMS`
> 实现提交：`856d05264ab4a4fb69b94896c172e4a1c29aae02`（master，工作树干净）
> 状态：`IN_REVIEW`

## 1. 已验证运行基线

| 项目 | 当前事实 | 证据边界 |
|---|---|---|
| 后端源码/制品 | 基础平台模块化单体，JDK 25基线，Maven 3.8.6；构建入口`mvn clean verify`及`mvn -pl yudao-server -am package -DskipTests` | 当前默认PATH为Java 21，不能作为验收证据；必须显式使用`C:\Program Files\Java\jdk-25.0.1+8` |
| 前端 | Vue 3响应式管理端，宿主机Node运行；`packageManager=pnpm@9.15.5`，Node引擎下限20.19.0 | 当前Node 24.11.1；在前端目录运行`corepack pnpm --version`为9.15.5。根目录运行得到全局11.17.0，不得使用 |
| MySQL | Docker `mysql:8.4`，当前运行基线记录8.4.10；本地端口13306 | Compose只证明当前开发/验收基础设施，不代表生产高可用 |
| Redis | Docker `redis:7.4-alpine`，当前运行基线记录7.4.9；本地端口16379 | 业务有效期不依赖Redis TTL；Redis不可用需按15分册降级 |
| 数据库迁移 | Docker `flyway/flyway:11.10.5-alpine`，应用不引入Flyway runtime | 只能前向迁移；Flyway对MySQL 8.4存在“高于已测试版本”提示，升级时需重跑空库/升级库验证 |
| 应用运行 | 前端和后端禁止放入Docker；后端宿主机58080、前端宿主机18081；Docker只承载MySQL/Redis/Flyway | 这是已确认的当前项目运行边界；生产可替换基础设施但不得改变应用契约 |
| Compose | 当前`docker compose config --quiet`通过 | 只验证配置可解析，不证明服务健康、迁移、应用或业务验收通过 |

## 2. 可复现开发/验收命令

### 2.1 基础设施与迁移

```powershell
docker compose config --quiet
docker compose up -d mysql redis migrate
docker compose run --rm migrate info
docker compose run --rm migrate validate
docker compose ps
```

启动顺序必须是MySQL/Redis健康→Flyway成功→后端→前端。`docker compose down`保留卷；`down --volumes`会删除本地持久卷，只允许在明确重建空库验收时使用，不是普通回退命令。

### 2.2 后端

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.1+8'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn clean verify
mvn -pl yudao-server -am package -DskipTests
java -jar yudao-server/target/yudao-server.jar --server.port=58080
```

环境变量必须在任务进程内设置，不改写全局Java。健康检查为`http://localhost:58080/actuator/health`。

### 2.3 前端

在`yudao-ui/yudao-ui-admin-vue3`目录执行：

```powershell
corepack pnpm --version
corepack pnpm install --frozen-lockfile
corepack pnpm build:prod
corepack pnpm dev
```

首条必须输出`9.15.5`。pnpm全局store可复用，但`node_modules`保留项目级链接/元数据，不能共享一个跨项目可写`node_modules`目录。

## 3. 发布与恢复已知边界

| 能力 | 当前证据 | Phase 3处理 |
|---|---|---|
| 构建可重复 | JDK25后端构建和pnpm9.15.5前端冻结安装/生产构建已有通过记录 | 18固化制品、校验和、构建号和releaseId；20重跑并保存证据 |
| 空库迁移 | MySQL 8.4上Flyway migrate/info/validate/重复migrate已有通过记录 | 18定义Expand/Backfill/Verify/Switch/Contract及升级库验证 |
| 应用回退 | 可切回兼容的旧JAR/静态制品；数据库不能回滚已执行迁移 | 18给出兼容窗口和前滚修复流程 |
| 文件/Redis卷 | 普通停止保留卷 | 18禁止把删除卷作为发布回退；文件引用/哈希另行复核 |
| 浏览器E2E | 锁定Playwright作为真实浏览器验收 | 20定义三浏览器四视口及业务动作路径 |
| 前端类型检查 | 2026-08-13在实际安装根执行`corepack pnpm ts:check`，pnpm 9.15.5，exit code 1、182项错误；其中PMS Engineering 85、Project 80、Service 9、Cutover 3、共享组件1、非PMS 4，逐错误见`frontend-ts-check-evidence.json` | 登记P3-E08；按公共契约/组件、PMS领域页面和上游兼容拆分工作包，任何前端Feature实现或发布前必须清零；禁止关闭检查或放宽TypeScript规则，`build:prod`通过不能覆盖此失败 |

### 3.1 实现仓库生产证据排查

2026-08-13在干净提交`856d05264ab4a4fb69b94896c172e4a1c29aae02`完成只读排查：

- `docker compose config --services`只有`mysql`、`migrate`、`redis`，镜像为MySQL 8.4、Redis 7.4和Flyway 11.10.5；它是开发/验收单节点基础设施，不包含生产入口、应用节点或HA编排。
- `docs/upstream-sources.md`明确生产可替换数据库、Redis、文件服务和入口网关，并将生产数据库高可用形态标为待确认。
- `tests/security`、`tests/performance`和`tests/infrastructure`提供本地健康与边界检查，不包含企业KMS、Telemetry后端、RPO/RTO恢复演练或独立近生产性能环境的Owner签署证据。
- 基础平台具有SkyWalking入口、运行日志、文件存储和Redis监控等通用能力，只能证明可集成能力存在，不能证明ADR-0004选定的企业生产设施已经登记或验收。

结论：实现仓库没有可将P3-E01～E06提升为`EVIDENCE_SUBMITTED`或`VERIFIED`的生产证据；开发Compose和基础平台功能不得复用为生产Gate证据。

## 4. 当前证据缺口

| 编号 | 缺口 | 阻塞范围 | 所需确认/证据 |
|---|---|---|---|
| P3-E01 | 已确认复用企业现有网关/LB、证书和网络区；实际域名、TLS终止、拓扑和Owner未登记 | 18生产部署、14信任边界 | 技术架构/运维提供生产拓扑和网络责任 |
| P3-E02 | 已确认企业托管MySQL/Redis HA；实际服务、节点规格和容量未登记 | 18/19及正式发布 | DBA/运维提供架构、规格和容量证据 |
| P3-E03 | 已确认业务Owner先批准RPO/RTO；具体数值、备份介质/频率/保留与恢复演练未确认 | 18发布恢复Gate | 业务Owner批准目标，DBA/运维完成设计和演练 |
| P3-E04 | 已确认企业KMS/Secrets Manager；具体设施、访问、轮换与应急Owner未登记 | 14安全、NFR-02发布门禁 | 安全/运维确认算法实现、密钥分离和轮换证据 |
| P3-E05 | 已确认OpenTelemetry统一采集并接企业后端；具体后端、访问、留存和告警未登记 | 17可观测及审计不可用策略 | 运维/安全确认采集后端、访问控制和留存策略 |
| P3-E06 | 已确认独立近生产性能环境；实际规格、迁移量、网络条件与测试账号未登记 | 19/20 NFR-01验收 | 测试、运维、数据Owner提供环境和数据集版本 |
| P3-E07 | 已确认平台级接口配置注册表及不可变版本引用；真实endpoint、认证引用、白名单和数值型timeout/retry未登记 | Feature联调与生产发布 | 各外部系统技术Owner逐接口登记；不阻塞Phase 3逻辑设计，阻塞具体Feature上线 |
| P3-E08 | 当前前端`ts:check`真实失败 | 任何前端Feature实现、浏览器验收和正式发布 | 前端Owner按错误清单修复契约/类型；保持现有严格度，重跑`ts:check`、lint、build及受影响页面真实浏览器回归 |
| P3-E09 | ADR-0030六表已进入当前DDL；字段目录、迁移映射、MySQL 8.4执行证据和证据包统一到`6B203BF3…75B`。当前2,079项、`DEFER=0`，正式独立复审已GO，状态为`MODEL_BASELINE_READY`；P3-E09不定义迁移批准哈希 | 历史迁移/切换只在对应Release中另行适用 | 当前可作为SDS/Feature数据模型输入。普通功能Release不受`AI-MIG-000`阻断；适用Release须验证范围、水位、程序、对账、回退和执行窗口，达到`VERIFIED`后只在批准窗口内执行 |

P3-E01～E06是部署、专项验收或生产发布证据，不阻断逻辑SDS基线；缺失时仍严格阻断其登记的下游门禁。P3-E07按具体Feature阻塞联调/上线；P3-E08阻塞前端Feature验收或发布；P3-E09已放行`DATA_MODEL_BASELINE`。`AI-MIG-000`只阻断被纳入Release范围的历史迁移或数据切换，不扩大阻断普通功能发布。
