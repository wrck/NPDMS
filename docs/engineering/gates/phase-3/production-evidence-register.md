# Phase 3生产与发布证据登记规范

> 状态：`IN_REVIEW`
> 适用基线：PRD V1.7、SDS Phase 1/2 BASELINE、Phase 3 IN_REVIEW
> 机器状态：`phase3-evidence-register.json`
> 原则：本文件定义需要什么证据，不填充未经Owner确认的生产事实。

## 1. 通用登记规则

每项证据必须同时具备：

- 明确Owner和复核人；
- 适用环境、版本或releaseId；
- 仓库相对证据路径或受控外部证据ID，不保存密码、Token、私钥和生产连接串；
- 事实值、采集/演练时间、采集命令或操作步骤；
- 验证结果、未通过项和整改引用；
- 变更后重新验证条件。

ADR-0004记录的已批准方案方向可以写入`confirmedFacts.direction*`，但不得解释为设施事实。只有生产Owner签署、证据可复核且验证通过，状态才可由`OPEN`改为`VERIFIED`。

Owner应从`evidence-packet-templates/`复制对应模板到`submissions/<证据编号>/`，按环境或发布批次创建不可覆盖的新版本。模板生成器只管理空白模板，不得覆盖Owner提交。

## 2. 逐项证据要求

### P3-E01 生产入口与拓扑

Gate scope：`PRODUCTION_DEPLOYMENT / PRODUCTION_RELEASE`。目标环境部署时指定，不阻断SDS逻辑设计。

Required fields：环境标识、入口网关/LB、域名、TLS终止点与证书Owner、网络区、前后端节点、端口/流向、出向代理、对象存储、DAC执行区、外部系统流向、运维Owner、拓扑版本。

Acceptance：14的信任区和18的生产逻辑拓扑能逐节点对应；未登记流量默认禁止；证书更新、节点故障和入口切换有演练证据。

### P3-E02 MySQL与Redis生产形态

Gate scope：`PRODUCTION_DEPLOYMENT / PERFORMANCE_ACCEPTANCE / PRODUCTION_RELEASE`。实际服务和容量在部署与验收时登记，不阻断SDS逻辑设计。

Required fields：服务形态/版本、HA/故障切换方式、节点和规格、存储/容量、连接上限、备份依赖、Redis持久化/淘汰策略、监控Owner、容量假设和验证报告。

Acceptance：19的数据规模与50并发模型可以在登记容量上复现；故障切换不破坏数据库真值、业务有效期或幂等边界；Redis不可用按15分册降级。

### P3-E03 备份恢复与RPO/RTO

Gate scope：`RECOVERY_ACCEPTANCE / PRODUCTION_RELEASE`。业务策略已进入SDS，实际作业与演练在生产发布前验证。

Required fields：业务批准RPO/RTO、MySQL/文件/配置/密钥引用的备份介质、频率、保留、加密、恢复顺序、演练Owner、最近演练报告、实际RPO/RTO、差异和整改。

批准基线：ADR-0012规定每日备份35天、月度备份13个月、年度备份7年及连续日志满足RPO≤1小时；ADR-0013规定同城温备为RTO≤4小时的主要路径、离线冷备为极端灾难兜底；ADR-0015规定季度隔离恢复、年度完整温备切换；ADR-0017规定运维发起、业务确认，涉及安全事件时增加安全确认。

Acceptance：隔离环境完成一次可用恢复；业务探针、迁移版本、文件hash、事件/回调、权限和审计一致；不能只证明备份作业成功。

### P3-E04 密钥托管与轮换

Gate scope：`DEVICE_CREDENTIAL_RELEASE / PRODUCTION_RELEASE`。具体KMS在部署时指定，不阻断SDS逻辑设计。

Required fields：KMS/Secrets Manager或等价方案、算法/模式、主密钥与数据密钥分离、密钥版本、访问主体、审计接口、备份/吊销、轮换和泄露应急Owner、演练报告。

Acceptance：临时密码不落库；凭证密文不可被普通DB/日志读者解密；轮换后新任务使用新版本，撤销和在途任务行为符合14；秘密扫描零命中。

### P3-E05 Telemetry与安全事件后端

Gate scope：`OBSERVABILITY_ACCEPTANCE / PRODUCTION_RELEASE`。留存、采样和审计规则已进入SDS，具体后端在部署时指定。

Required fields：日志、指标、Trace、告警、安全事件后端，采集Agent/协议、访问角色、脱敏、留存、采样、仪表盘/告警/runbook、时间同步、Owner和测试触发证据。留存字段必须继承ADR-0006：业务事实、审批历史及PRD/SDS明确留痕操作为`PERMANENT_NON_DELETABLE`；继承ADR-0007：普通网络/安全运行日志在线180天、不可变冷存储185天；继承ADR-0008：普通Trace在线30天、冷存储60天，错误/高风险Trace在线30天、冷存储150天；继承ADR-0009：原始高精度指标90天，5分钟/小时聚合指标13个月；继承ADR-0010：调试日志默认7天，登记原因、负责人和到期时间后最长30天。采样字段必须继承ADR-0011：普通成功请求10%，错误/高风险/审计失败/发布迁移100%。

Acceptance：可从releaseId/correlationId/业务对象追到API、事件、外部调用、回调和审计；高风险审计不可用时fail closed；普通降级不伪报成功；业务界面、管理员、API、后台任务和存储生命周期均不能删除永久记录，冷迁移前后数量、关联、完整性和授权检索一致。

### P3-E06 近生产性能环境

Gate scope：`PERFORMANCE_ACCEPTANCE / PRODUCTION_RELEASE`。环境与实测结果阻断性能验收和发布，不阻断SDS逻辑设计。

Required fields：环境拓扑和规格、与生产差异/缩放模型、网络条件、数据集版本、迁移量、账号/权限分布、外部依赖桩或沙箱、负载脚本hash、监控、清理和Owner。

Acceptance：能够复现19中的50用户/30分钟/不少于10000请求、规模树、50MB和NFR-03场景；原始结果不可覆盖，失败轮次不可拼接成PASS。

### P3-E07 Feature外部接口配置档案

Gate scope：`FEATURE_INTEGRATION / FEATURE_RELEASE`。

Required fields：Requirement/Feature、系统Owner、方向、endpoint引用、认证方式、网络白名单、请求/响应映射版本、sourceKey/idempotencyKey、timeout/retry、补偿/对账/降级、沙箱契约结果和上线批准。

Acceptance：12分册每个实际启用操作均有一条配置档案；HTTP成功与业务成功分别验证；秘密只通过受控配置引用。

### P3-E08 前端类型质量门禁

Gate scope：`FRONTEND_FEATURE_ACCEPTANCE / FRONTEND_RELEASE`。

Required fields：实现提交、Node/pnpm/lockfile hash、`ts:check`命令和日志、错误基线、整改Owner、受影响页面、清零提交、lint/build及真实浏览器回归证据。

Acceptance：`ts:check` exit code 0；不得关闭检查、扩大`any`或放宽规则换取通过；build通过不能覆盖类型失败。

### P3-E09 数据模型基线与AI-MIG-000边界

Gate scope：`DATA_MODEL_BASELINE / HISTORICAL_DATA_MIGRATION / DATA_CUTOVER`。P3-E09只发布当前数据模型基线；迁移执行与切换证据继续在下游关闭。

Required fields：当前DDL hash、`ddl-item-decision-register.json`逐项漂移决策、目标字段目录/映射/校验hash、MySQL 8.4隔离执行结果、独立复审结论和Git基线提交。P3-E09不定义迁移批准哈希：未来历史迁移门禁按真实批次另行定义。当前不要求四角色外部附件、OA/电子签名、独立批准JSON、迁移批准状态机或双确认提交。

需求方确认入口：`specs/001-project-delivery-platform/evidence/migration/p3-e09-confirmation-packet.md`，按Q07～Q14及V1.7九组覆盖全部692项`DEFER`并绑定当前DDL哈希。`ddl-model-decision-catalog.md`和`ddl-item-decision-register.json`保留全部表、字段、表选项、主键、外键、索引、唯一键和CHECK定义及稳定编号；1,883项逐项决策已完成。独立复审只在`independent-review.md`复核候选制品的整体一致性，不逐项签署。

Acceptance（当前模型基线已满足的事实条件）：逐项登记的表、列、约束和表选项均有决策证据，`DEFER=0`；DDL、目录、映射、校验和隔离执行证据绑定同一当前DDL hash；正式制品形成Git基线提交。全部领域实体具有字段映射或批准终态；旧库只读、无跨库SQL；旧`passed=true`不复用。独立复审结论为`GO`，P3-E09为`MODEL_BASELINE_READY`并可作为SDS/Feature模型输入。`AI-MIG-000`、历史数据迁移和数据切换保持`OPEN`，未经真实批次验证不得执行。

## 3. 状态定义

| 状态 | 含义 |
|---|---|
| `OPEN` | 必填事实/Owner/证据尚未齐全 |
| `EVIDENCE_SUBMITTED` | 已提交证据，尚未完成复核 |
| `VERIFIED` | Owner与独立复核均通过，所有必填字段和验收断言成立 |
| `REJECTED` | 证据与设计/事实冲突，必须整改后新建证据版本 |
| `NOT_APPLICABLE` | 仅P3-E07允许按Feature不启用；必须由Requirement Owner批准并说明替代路径 |

SDS Phase 3基线只要求证据契约、Owner类型、验收标准和下游门禁归属完整，不要求尚未部署的环境事实提前`VERIFIED`。各项仍须在其`Gate scope`前关闭；`OPEN`不等于可以绕过下游门禁。
