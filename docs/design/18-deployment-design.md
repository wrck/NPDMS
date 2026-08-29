# SDS Phase 3：部署、迁移与回退设计

> 文档状态：`IN_REVIEW`
> 适用基线：PRD V1.8修订007、SDS Phase 1/2 BASELINE
> Requirement ID：NFR-01、NFR-02、NFR-03及全部100项正式Requirement、111个目标版本切片的构建、配置、迁移、发布和恢复保障
> Owner：SDS Phase 3发布架构；生产基础设施Owner待P3-E01～E03登记
> 已冻结运行证据基线：`E:\AICoding\Projects\NPDMS` @ `856d05264ab4a4fb69b94896c172e4a1c29aae02`（不等同于当前实现HEAD）
> 前置设计：09、12、13、14、17分册

Phase 1/2 V1.8修订007基线已就绪；本分册正在按111个目标版本切片复核，尚未重新纳入SDS V1.8基线，也不构成当前发布放行或生产环境事实。生产参数仍按P3-E01～E07在部署/专项验收阶段登记。

## 1. 部署边界

### 1.1 已验证开发/验收剖面

```text
Browser -> Frontend dev/static server on host :18081
             -> Backend JAR on host :58080
                   -> MySQL 8.4 in Docker :13306
                   -> Redis 7.4 in Docker :16379
Flyway 11.10.5 CLI in Docker -> MySQL before backend starts
```

- 前端和后端禁止放入Docker；Docker只承载MySQL、Redis和Flyway基础设施。
- 后端显式使用JDK 25.0.1和Maven 3.8.6；默认PATH当前为Java21，不是发布构建证据。
- 前端在实际安装根`yudao-ui/yudao-ui-admin-vue3`通过Corepack使用pnpm 9.15.5；根目录全局pnpm11不是构建入口。
- 当前剖面可用于开发、集成和验收复现，不自动等于生产拓扑。

### 1.2 生产逻辑拓扑

生产必须提供：TLS入口/反向代理、静态前端发布点、JDK25后端运行节点、MySQL 8.4兼容数据库、Redis 7.4兼容缓存、文件存储、密钥服务、Telemetry后端、Flyway迁移执行身份和外部接口网络通道。ADR-0004已确认复用企业现有网关/LB、证书和网络区，并使用企业托管MySQL/Redis HA；实际服务、节点数量、域名、网络拓扑、高可用形态、规格和Owner仍待P3-E01/E02证据，不得将本地端口和单节点Compose照搬为生产事实。

## 2. 发布制品与版本清单

一次releaseId绑定且不可修改：

| 制品 | 必须记录 | 验证 |
|---|---|---|
| 后端JAR | Git提交、JDK/Maven、依赖锁定结果、SHA-256、构建日志 | JDK25下`mvn clean verify`和package通过；启动版本与哈希一致 |
| 前端静态制品 | Git提交、Node、pnpm9.15.5、lockfile hash、构建配置、SHA-256 | frozen install、type/lint/build门禁通过；部署后静态文件hash一致 |
| Flyway迁移 | 迁移版本、文件名、checksum、databaseTarget、预期前后schema版本 | info/validate、空库和升级库验证；已执行文件不修改 |
| 配置清单 | 配置schema版本、非秘密值摘要、秘密引用、环境差异 | 必填缺失fail fast；无明文秘密；配置审批/发布审计 |
| 文件/字典/模板种子 | 版本、来源、幂等键、hash和适用租户 | 重复执行无重复有效事实；不覆盖已发布业务版本 |
| SBOM/依赖证据 | 后端/前端依赖清单、审计结果和批准例外 | 无未处置生产可达严重/高危问题 |

发布证据清单必须包含PRD版本、SDS版本、Requirement范围、releaseId、buildId、Git提交、制品hash、migrationVersion、configVersion、测试报告ID、审批人和发布时间。

## 3. 配置与秘密

- 环境变量、数据库名、Compose project、配置前缀统一使用`npdms`；Java包、`pms_`表和Requirement ID不因产品名调整而重命名。
- `.env.example`只保存占位符；真实密码、Token、证书私钥、数据库/Redis/MyBatis加密密钥和DAC密钥从受控环境/密钥服务注入，缺失即失败。
- 前端构建期只注入公开配置；服务端秘密不得打包进JS、source map或静态配置。
- URL、回调地址、CORS来源、TLS和外部认证按环境注册；普通业务管理员无权修改生产连接与安全边界。
- 生产配置变更先校验schema和引用，再生成configVersion；应用启动输出脱敏配置摘要，不输出秘密值。

## 4. 构建与开发/验收启动

### 4.1 基础设施

```powershell
docker compose config --quiet
docker compose up -d mysql redis migrate
docker compose run --rm migrate info
docker compose run --rm migrate validate
docker compose ps
```

MySQL/Redis健康后执行Flyway；迁移成功后才允许启动后端。Compose解析成功不等于服务/迁移/业务健康。

### 4.2 后端

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.1+8'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn clean verify
mvn -pl yudao-server -am package -DskipTests
java -jar yudao-server/target/yudao-server.jar --server.port=58080
```

构建证据必须保存`java -version`、`mvn -version`、命令、exit code和制品hash。健康检查至少验证进程、数据库、Redis、迁移版本和只读基础查询；不能只以端口监听为健康。

### 4.3 前端

在前端安装根执行：

```powershell
corepack pnpm --version
corepack pnpm install --frozen-lockfile
corepack pnpm ts:check
corepack pnpm lint
corepack pnpm build:prod
```

首条必须输出9.15.5。`node_modules`为项目级链接/元数据，依赖内容复用pnpm全局store；禁止共享跨项目可写`node_modules`。type/lint当前是否通过必须以本release重跑为准，生产构建成功不能覆盖type失败。

## 5. 数据库前向迁移

任何数据库演进遵循：

```text
Expand -> Backfill -> Verify -> Switch -> Contract
```

| 阶段 | 允许动作 | Gate |
|---|---|---|
| Expand | 新表/可空列/兼容索引/双读所需结构 | 旧应用仍可运行；DDL锁影响已评估 |
| Backfill | 按稳定游标分批、幂等回填，保存批次/水位/失败项 | 不覆盖不可变历史；可暂停续跑 |
| Verify | 数量、唯一性、外键语义、hash、Owner字段和业务抽样对账 | 差异为0或有批准的隔离清单 |
| Switch | 新应用切读/写并观察，必要时双写/对账 | 旧制品仍兼容扩展schema |
| Contract | 后续独立发布移除旧列/兼容路径 | 无旧消费者、备份/恢复已演练、单独批准 |

- 不得修改已执行迁移或任何已执行Flyway文件；纠正使用下一版本迁移。
- 迁移脚本不跨数据库直接读取历史库；数据迁移通过受控导出/校验/导入或应用迁移作业。
- Release范围不包含历史迁移或数据切换时，`AI-MIG-000`记为`NOT_APPLICABLE`且不得阻断普通功能发布；包含任一项时，必须先由`AI-MIG-000`完成真实批次的范围、水位、程序、校验、演练、对账和回退验证，并且只能在批准窗口内执行。P3-E09不定义迁移批准哈希且不构成执行许可；旧`migration-validation.json.passed=true`因DDL哈希漂移不具备当前放行效力。
- 迁移前保存schema/version/checksum、数据量、长事务/锁风险和备份证据；迁移后运行`info/validate`及业务校验。
- 应用回退不执行破坏性数据库down migration；在兼容窗口内切回旧制品，数据库错误使用新前向迁移纠正。

## 6. 发布序列

1. **冻结范围**：确认releaseId、Requirement、PRD/SDS版本、变更/迁移/配置/外部接口清单和Owner。
2. **构建验证**：JDK25后端、pnpm9.15.5前端、依赖审计、制品hash、SBOM、自动测试全部完成。
3. **环境预检**：TLS/网络/密钥/数据库/Redis/文件/Telemetry/外部接口配置和容量证据有效。
4. **备份与恢复准备**：完成P3-E03批准的备份并验证可读；回退/前滚负责人在线。
5. **先扩展迁移**：执行Flyway，完成Verify；失败时停止应用发布，不修改迁移历史。
6. **部署后端/前端**：使用批准制品和配置；功能开关仅控制启用，不绕过schema/权限。
7. **技术探针**：健康、迁移、日志/指标/Trace、数据库/Redis/文件和外部依赖状态。
8. **业务探针**：登录、项目查询、权限拒绝、文件、关键状态命令、事件/回调和本release核心路径。
9. **分阶段启用**：阶段、范围、观察时长和Owner按发布单登记；每阶段比较基线错误率/P95/业务结果和安全告警，不在SDS臆造比例。
10. **完成/保持/回退**：依据第7节触发条件决策并记录；发布完成后继续观察并关闭临时开关。

## 7. 失败判定与回退/前滚

### 7.1 立即停止或回退

- 数据完整性、跨租户/权限、安全秘密泄露或制品/迁移校验异常；
- 核心场景NFR门禁失败，或新版本相对已批准基线出现无法解释的错误/P95恶化；
- 不可变审计不可用导致高风险操作无法留痕；
- 健康/业务探针失败、事件/回调产生重复副作用、数据库迁移处于未知状态。

除PRD明确阈值外，相对基线回退阈值由本次发布单和19压测证据确定，不能直接照搬通用百分比。

### 7.2 回退路径

| 变更 | 回退/恢复 |
|---|---|
| 前端静态制品 | 切回上一批准hash并清理/刷新静态缓存；验证业务接口兼容 |
| 后端JAR | 在Expand兼容窗口切回上一JAR/配置；验证数据库、事件Consumer和外部幂等 |
| 功能启用 | 关闭有Owner/到期日的开关；不回写已经发生的业务事实 |
| 数据库 | 不修改/回滚已执行迁移；停止新写，使用批准前向修复或在灾难恢复流程恢复整库；发布包含历史数据切换时另受`AI-MIG-000`及其批准窗口约束 |
| 外部请求/事件 | 停止Producer/Consumer领取，核对Outbox/Inbox/外部业务号，恢复后按原幂等键重放 |
| 文件 | 保留原版本，撤销错误引用/发布新版本；按hash核验，不物理覆盖证据 |

回退后必须验证：版本/hash、迁移版本、权限拒绝、核心读写、事件队列、外部对账、文件和Telemetry。未确认外部副作用时状态保持待对账，不宣称恢复完成。

## 8. 备份、恢复与灾难演练

【业务目标已确认：P3-E03】ADR-0005批准RPO不超过1小时、RTO不超过4小时。ADR-0012批准每日备份保留35天、月度备份保留13个月、年度备份保留7年，并以连续日志或等价增量机制满足RPO。ADR-0013批准同城温备作为4小时恢复的主要路径，离线冷备作为极端灾难兜底。DBA和运维仍须在恢复验收/生产发布前登记实际介质、加密、恢复顺序、演练频率和责任人并完成演练；这些实例与运行证据不阻断逻辑部署设计基线。

ADR-0015要求每季度至少一次隔离恢复演练、每年至少一次完整同城温备切换演练。ADR-0017规定切换由运维负责人发起、业务负责人确认；涉及安全事件时增加安全负责人确认。发起、确认、拒绝、执行、失败、回切和业务验证永久留痕。

恢复演练至少证明：

- 在隔离环境恢复MySQL、文件内容/元数据、配置/密钥引用和必要Redis可重建状态；
- Flyway历史/checksum、业务数量、文件hash、项目/任务树、设备唯一归属、审计/事件水位一致；
- 应用以指定releaseId启动，核心读写、权限拒绝和外部对账可执行；
- 实测RPO/RTO与批准目标比较，失败保留原报告并整改复验。

## 9. 发布证据与权限

- 构建者、发布审批者、迁移执行者、生产运维和审计人员职责分离；个人账号操作，不共享管理员。
- 每一步记录主体、时间、环境、命令/动作、输入版本、输出hash、结果、request/trace/correlation/releaseId和证据引用。
- 生产数据库、配置、密钥和Telemetry访问按最小权限；人工补偿调用受控command，不直接改状态或审计表。

## 10. Phase 3部署门禁

| 门禁 | 当前结论 |
|---|---|
| 当前开发/验收剖面可复现 | PASS |
| 制品、配置、前向迁移、发布和应用回退逻辑 | PASS |
| 生产拓扑/高可用/规格 | BLOCKED_BY_EVIDENCE（P3-E01、P3-E02） |
| 备份、RPO/RTO和恢复演练 | BLOCKED_BY_EVIDENCE（P3-E03） |
| 安全密钥与Telemetry生产依赖 | BLOCKED_BY_EVIDENCE（P3-E04、P3-E05） |

逻辑部署设计、配置契约、发布/迁移/回退步骤和验收规则完整后，本分册可进入SDS基线评审。P3-E01～E05按各自部署、专项验收和生产发布门禁关闭，不因目标环境尚未创建而前置阻断。
