# Phase 3生产与发布证据登记规范

> 状态：`IN_REVIEW`
> 适用基线：PRD V1.6、SDS Phase 1/2 BASELINE、Phase 3 IN_REVIEW
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

Required fields：环境标识、入口网关/LB、域名、TLS终止点与证书Owner、网络区、前后端节点、端口/流向、出向代理、对象存储、DAC执行区、外部系统流向、运维Owner、拓扑版本。

Acceptance：14的信任区和18的生产逻辑拓扑能逐节点对应；未登记流量默认禁止；证书更新、节点故障和入口切换有演练证据。

### P3-E02 MySQL与Redis生产形态

Required fields：服务形态/版本、HA/故障切换方式、节点和规格、存储/容量、连接上限、备份依赖、Redis持久化/淘汰策略、监控Owner、容量假设和验证报告。

Acceptance：19的数据规模与50并发模型可以在登记容量上复现；故障切换不破坏数据库真值、业务有效期或幂等边界；Redis不可用按15分册降级。

### P3-E03 备份恢复与RPO/RTO

Required fields：业务批准RPO/RTO、MySQL/文件/配置/密钥引用的备份介质、频率、保留、加密、恢复顺序、演练Owner、最近演练报告、实际RPO/RTO、差异和整改。

Acceptance：隔离环境完成一次可用恢复；业务探针、迁移版本、文件hash、事件/回调、权限和审计一致；不能只证明备份作业成功。

### P3-E04 密钥托管与轮换

Required fields：KMS/Secrets Manager或等价方案、算法/模式、主密钥与数据密钥分离、密钥版本、访问主体、审计接口、备份/吊销、轮换和泄露应急Owner、演练报告。

Acceptance：临时密码不落库；凭证密文不可被普通DB/日志读者解密；轮换后新任务使用新版本，撤销和在途任务行为符合14；秘密扫描零命中。

### P3-E05 Telemetry与安全事件后端

Required fields：日志、指标、Trace、告警、安全事件后端，采集Agent/协议、访问角色、脱敏、留存、采样、仪表盘/告警/runbook、时间同步、Owner和测试触发证据。

Acceptance：可从releaseId/correlationId/业务对象追到API、事件、外部调用、回调和审计；高风险审计不可用时fail closed；普通降级不伪报成功。

### P3-E06 近生产性能环境

Required fields：环境拓扑和规格、与生产差异/缩放模型、网络条件、数据集版本、迁移量、账号/权限分布、外部依赖桩或沙箱、负载脚本hash、监控、清理和Owner。

Acceptance：能够复现19中的50用户/30分钟/不少于10000请求、规模树、50MB和NFR-03场景；原始结果不可覆盖，失败轮次不可拼接成PASS。

### P3-E07 Feature外部接口配置档案

Required fields：Requirement/Feature、系统Owner、方向、endpoint引用、认证方式、网络白名单、请求/响应映射版本、sourceKey/idempotencyKey、timeout/retry、补偿/对账/降级、沙箱契约结果和上线批准。

Acceptance：12分册每个实际启用操作均有一条配置档案；HTTP成功与业务成功分别验证；秘密只通过受控配置引用。

### P3-E08 前端类型质量门禁

Required fields：实现提交、Node/pnpm/lockfile hash、`ts:check`命令和日志、错误基线、整改Owner、受影响页面、清零提交、lint/build及真实浏览器回归证据。

Acceptance：`ts:check` exit code 0；不得关闭检查、扩大`any`或放宽规则换取通过；build通过不能覆盖类型失败。

### P3-E09 AI-MIG-000迁移基线

Required fields：数据元Excel hash、源结构抽取hash/水位、当前DDL hash、`ddl-item-decision-register.json`逐项漂移决策、`approvedDdlSha256`、目标字段目录/映射/校验hash、生成器版本、release manifest、Owner签署和验证结果。

Acceptance：逐项登记的表、列、约束和表选项全部有决策与复核证据；DDL、目录、映射、校验和manifest引用同一批准hash；全部领域实体具有字段映射或批准终态；旧库只读、无跨库SQL；旧`passed=true`不复用。

## 3. 状态定义

| 状态 | 含义 |
|---|---|
| `OPEN` | 必填事实/Owner/证据尚未齐全 |
| `EVIDENCE_SUBMITTED` | 已提交证据，尚未完成复核 |
| `VERIFIED` | Owner与独立复核均通过，所有必填字段和验收断言成立 |
| `REJECTED` | 证据与设计/事实冲突，必须整改后新建证据版本 |
| `NOT_APPLICABLE` | 仅P3-E07允许按Feature不启用；必须由Requirement Owner批准并说明替代路径 |

Phase 3批准要求P3-E01～E06和P3-E09全部`VERIFIED`；P3-E08在任何前端Feature验收/发布前必须`VERIFIED`；P3-E07按实际Feature逐接口判定。
