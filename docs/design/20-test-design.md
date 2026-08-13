# SDS Phase 3：测试设计

> 文档状态：`IN_REVIEW`
> 适用基线：PRD V1.6、SDS Phase 1/2 BASELINE及Phase 3分册
> Requirement ID：附录A.1全部115项V1/V2正式需求；重点NFR-01～03
> Owner：SDS Phase 3质量架构；具体Feature测试由Requirement Owner负责
> 前置设计：01～19正式分册

## 1. 测试原则

1. Requirement→SDS→Feature→Code→Test→Evidence链必须可导航；编译/静态检查/文档校验不替代业务验收。
2. 每个有副作用的接口覆盖正常、异常、权限拒绝、幂等、并发和恢复；不得删测试或放宽校验使实现通过。
3. UI必须使用真实浏览器执行用户动作；静态HTML、组件挂载或API直调不算页面验收。
4. 数据库必须验证空库、升级库、迁移重复执行、checksum和前向纠正；不得修改已执行迁移。
5. 外部HTTP/消息/通知成功不等于业务完成，测试须验证业务回执、消费、对账或门禁。
6. V1与V2门禁分离；V3和OUT_OF_SCOPE不进入当前发布验收。

## 2. 测试层次与Owner

| 层次 | 目标 | 主要Owner | 证据 |
|---|---|---|---|
| 单元/值对象 | 计算、校验、状态守卫、脱敏、映射 | 开发 | 测试类/用例/结果 |
| 聚合/应用服务 | 命令、版本、权限、幂等和事务 | Feature团队 | 聚合测试、Mock仅限外边界 |
| 数据库/迁移 | DDL、唯一/索引、租户、历史、空库/升级库 | DBA/后端 | Flyway info/validate、集成测试、SQL证据 |
| API契约 | Schema、错误、If-Match、幂等、DataScope | 后端/消费者 | OpenAPI/contract测试和请求响应摘要 |
| 事件/集成 | Producer/Consumer、Inbox/Outbox、回调、重试/对账 | 集成Owner | contract测试、模拟/沙箱及真实联调证据 |
| 文件/安全 | 版本/hash/扫描/权限/秘密防泄露 | 安全/后端 | 文件与秘密扫描报告 |
| 浏览器E2E | 登录、页面操作、响应式、跨浏览器和状态流 | QA/前端 | Playwright trace、截图/录像、业务记录ID |
| 性能/容量 | NFR-01～03量化门禁 | 性能/运维 | 原始结果、环境/数据/脚本/reportId |
| 发布/恢复 | 制品、配置、迁移、探针、回退/前滚、恢复 | 发布/DBA/运维 | release evidence manifest、演练报告 |

## 3. 测试用例最小结构

每个用例登记：`testCaseId/requirementId/releaseId/environmentId/dataSetVersion/precondition/subject+roles+dataScope/input+idempotencyKey+expectedVersion/action/expectedBusinessResult/expectedPersistence+event+audit/cleanup/evidenceRef/result`。

用例前置状态由API/fixture/迁移创建，不通过直接更新status制造；清理不删除不可变审计、事件和已批准版本。

## 4. 副作用接口统一矩阵

| 类别 | 操作 | 断言 |
|---|---|---|
| 首次成功 | 合法主体、状态、版本和输入 | 单一业务事实、状态/历史/Outbox/审计一致 |
| 同键同请求 | 重放相同Idempotency-Key和摘要 | 返回首次结果，无重复对象/事件/外部副作用 |
| 同键异请求 | 相同键改变业务输入 | `IDEMPOTENCY_CONFLICT`，原事实不变 |
| 权限拒绝 | 跨租户/项目/设备/文件/凭证或撤销权限 | 100%拒绝，不泄露敏感存在性，不产生成功幂等事实 |
| 版本冲突 | 并发使用过期If-Match | 返回当前版本/状态，后到请求不覆盖 |
| 非法状态 | 从不允许状态执行命令 | STATE/BUSINESS_GATE，状态和历史不变 |
| 事务故障 | 聚合提交前/Outbox提交边界注入失败 | 原子回滚或可恢复Outbox，不出现半事实 |
| 外部超时实际成功 | 超时后外部已创建业务单 | 先查询/对账，原幂等键不重复创建 |
| 批量部分失败 | 多对象中部分非法/冲突 | 逐项结果和总状态准确，成功不伪装全成功 |
| 恢复/补偿 | 依赖恢复、重试、人工接管 | 原关联ID/证据保留，反向事实不删除历史 |

## 5. 领域关键测试

| 范围 | 必测业务与异常 |
|---|---|
| Project | 任意层级树、无环、后代权限、投影完整版本；PM-05同源转销/部分失败/设备处置；PM-06唯一期次/冲突群组/派生不回写 |
| SOL | 工期/计划/方案提交审批、版本冻结、动态表单schema版本、文件引用和退回历史 |
| IMP | 到货部分签收/差异、安装确认/退回、配置解析、联调、风险、质量整改、安全阻断/豁免、就绪门禁 |
| ACC | 培训/调查/验收报告、交付件齐套/审核/归档、全部后代闭环门禁、持续服务交接且无续保经营 |
| CUT | 任务/评估/方案/审批/执行/回退/观察、动作类型+方向+正负值、DAC消费，不含平台通用割接时效 |
| SRV | 工单/工时原值调整、巡检在线/离线、预检/报告/问题闭环/误报、设备客观服务状态，不含工单时效/续保报表 |
| CUS/AST/COM/RES | CRM客户Owner、ERP合同订单Owner、设备唯一归属、RMA/维保事实、范围超分配、服务商/转包/付款门禁 |
| PLT/KNO/ANA | 待办不代业务成功、文件身份、授权、变更、ITR公告只读、指标/组合不回写交易事实 |

## 6. 数据库与迁移测试

- MySQL 8.4执行空库`migrate/info/validate`、重复migrate和从最近批准版本升级；保存Flyway版本/checksum/耗时。
- 每个新增唯一/索引/约束验证成功、重复/冲突、并发和查询计划；租户必须包含在业务唯一键和高频索引。
- Project/Task路径投影、Device当前归属/历史、DeliveryScope、Inbox/Outbox、文件版本、PM-05/06和DAC消费确认执行真实数据库并发测试。
- Backfill验证批次/水位/暂停续跑、数量/hash/业务抽样和失败隔离；Switch前后旧/新应用兼容。
- 历史数据迁移先验证`AI-MIG-000`的`approvedDdlSha256`、数据元/DDL/映射/manifest哈希一致，再执行源行覆盖、问题分类、数量金额、关系和抽样对账；旧`passed=true`不得复用。
- 已执行迁移文件发生checksum漂移必须失败；修复只能新增前向迁移。

## 7. 事件与外部集成测试

每个事件验证Schema版本、Producer/Consumer、分区/顺序、首次/重复/乱序/缺序、Consumer崩溃恢复、永久失败隔离和敏感字段黑名单。

每个12分册操作验证：请求/响应字段映射、sourceKey/version、旧版、同版冲突、timeout、retry、外部实际成功、业务拒绝、部分失败、补偿、对账、降级和审计。Feature上线前使用真实endpoint/认证/网络白名单完成consumer/provider contract及沙箱/联调证据；未登记接口不可判READY。

INT-12专项：

- 临时模式保存用户名、密码全链路0持久化；保存为凭证后本次任务切换新凭证及默认授权；
- 五元组、有效期、创建人/被授权人、任务级短期取密；撤销前/执行中实际停止点；
- 下发超时、回调伪造/重复/乱序、结果hash、BUSINESS_CONSUMPTION必要消费者；
- 独立中心只在有效成功终态回调完成，失败/取消/安全异常不发布完成。

## 8. 文件与安全测试

| 场景 | 通过标准 |
|---|---|
| 50MB/超限/中断/重复 | 50MB hash一致；超限/中断无有效引用；重试一个有效版本 |
| MIME/魔数/路径/宏/外部实体 | 非法隔离或拒绝，不执行内容，不形成可下载业务引用 |
| 越权下载/预览/归档 | 100%拒绝；短期令牌主体/操作/版本绑定 |
| XSS/CSRF/CORS/安全头 | 外部文本不执行；跨站状态请求拒绝；CORS/生产头符合登记配置 |
| SSO/会话 | 合法LDAP/AD、过期/重放/错误audience、应急账号和退出/撤销 |
| 秘密扫描 | 唯一标记秘密在浏览器/DB/缓存/消息/日志/Trace/回调/异常/导出/结果命中0 |
| 依赖/制品 | 锁定安装；无未处置生产可达严重/高危问题；制品/hash/SBOM一致 |

## 9. 并发与恢复测试

- 项目/任务交叉移动、成环、树投影切换；PM-05同源转销/失败重试；PM-06并发加期/循环。
- 设备并发归属、项目树移动与祖先投影；订单行并发分配和ERP减量超分配。
- 同一状态双命令、审批重复/过期回调、文件同时替换、DAC回调/撤销并发。
- Redis、Broker、对象存储、外部系统、回调、Telemetry故障；验证降级、积压、恢复、对账和高风险审计fail closed。
- 发布中断、迁移失败、旧JAR切回、前向修复、事件/外部副作用核对和恢复演练。

## 10. 真实浏览器与响应式矩阵

### 10.1 环境矩阵

- Chrome、Edge、Firefox：验收时记录当前稳定版。
- 视口：1920×1080、1440×900、1366×768、1024×768。
- V1响应式Web为门禁；移动端/桌面客户端不是V1/V2交付范围。

### 10.2 核心动作

每款浏览器执行登录→项目/任务树→创建/编辑/保存→刷新验证持久化→返回列表/详情→权限拒绝→文件上传/下载→审批/状态流转→实施/割接或巡检代表路径。必须点击真实按钮、填写真实表单并检查网络/console/DOM/业务记录；API预置可建立前置数据但不能代替用户动作。

除明确允许横向滚动的数据表外，不得页面级横向溢出；导航、表单和操作区在窄视口重排，关键字段、按钮和错误提示无遮挡、可键盘到达。页面和接口业务结果跨三浏览器一致。

## 11. NFR验收

### NFR-01

- 19分册环境/数据下50登录用户持续30分钟、≥10000有效请求；服务端错误率≤0.5%，页面/主要交互P95≤2秒。
- 项目/任务规模公式、1万/5万树、2000直接子节点、深度30及更深合法结构正确性。
- 50MB完整性、三浏览器四视口、LDAP/AD、RBAC/DataScope、全量关键审计。

### NFR-02

- V1：AES-256或同等强度、密钥分离、五元组、任务授权、秘密扫描0命中、撤销/轮换/失败。
- V2巡检：单命令默认30秒，可配置；超时终止当前命令并按规则决定后续，保留实际结果。不得把V2专项反向阻塞V1。

### NFR-03

- 割接/巡检五节点、全部启用渠道；delivered/valid sent≥99%，可按节点下钻。
- 巡检事件至项目读取对应状态版本≤60秒；重复不推进、乱序不回退、失败可重试/对账。

## 12. 构建、发布和证据门禁

最低自动命令：

```powershell
# 规格仓库
py -3.13 -B scripts\validate_prd_semantics.py --prd docs\baseline\prd-v1.6.md
py -3.13 -B scripts\validate_sds_phase2.py
py -3.13 -B scripts\validate_domain_entity_migration_alignment.py
py -3.13 -B scripts\validate_phase3_evidence_register.py
py -3.13 -B scripts\validate_sds_phase3.py
py -3.13 -B -m unittest discover -s scripts\tests -p "test_*.py"

# 实现仓库后端（显式JDK25）
mvn clean verify

# 实现仓库前端安装根
corepack pnpm install --frozen-lockfile
corepack pnpm ts:check
corepack pnpm lint
corepack pnpm build:prod

# 基础设施/迁移
docker compose config --quiet
docker compose run --rm migrate info
docker compose run --rm migrate validate
```

任何失败必须登记原命令、环境、exit code和日志引用；生产build通过不能覆盖`ts:check`/lint失败。当前实现仓库的真实结果在每个release重新执行，不沿用旧报告中的PASS标记。

## 13. 115项覆盖方式

`docs/traceability/phase2-contract-map.md`已为每项登记Phase 3测试类别和证据类型，并以`Phase 3验证注记状态：IN_REVIEW`与已批准的Phase 2契约字段隔离；相同聚合可以复用测试fixture，但每个Requirement必须能定位到专属业务用例/参数和验收断言，不能用一个“领域测试”占位。

自动校验至少检查：115项ID集合一致；每项有正常/异常或适用性说明、权限/数据范围、幂等/并发适用性、测试层次和证据类型；NFR-01～03链接到本分册具体章节。

## 14. 缺陷、豁免与退出

- Critical/High业务完整性、安全、跨租户、秘密泄露、迁移/恢复失败不允许豁免上线。
- 其他缺陷必须有Requirement、影响、复现、Owner、版本和批准结论；不得关闭为“无法复现”而无环境/日志证据。
- 测试数据、临时账号、外部沙箱任务和文件按授权清理；不可变审计/报告保留，秘密测试值撤销/轮换。

## 15. Phase 3测试门禁

| 门禁 | 当前结论 |
|---|---|
| 正常/异常/权限/幂等/并发测试设计 | PASS |
| 数据库、事件、集成、文件、安全和浏览器设计 | PASS |
| NFR-01～03量化验收设计 | PASS |
| 115项逐项运行验证映射 | PASS-DESIGN；115/115均有测试类别和证据类型 |
| 前端类型检查 | FAIL（P3-E08）；2026-08-13真实执行`corepack pnpm ts:check`退出码1，生产构建通过不能覆盖 |
| 生产/性能环境、恢复、迁移和真实接口证据 | BLOCKED_BY_EVIDENCE（P3-E01～E07、P3-E09/AI-MIG-000） |

自动映射、环境证据和实际测试未完成前，本分册不得转为`BASELINE`。
