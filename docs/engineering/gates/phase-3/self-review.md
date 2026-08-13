# SDS Phase 3 工程化自审

> 日期：2026-08-13
> 状态：`IN_REVIEW`
> 结论：`NO-GO / NOT_READY_FOR_SDS_BASELINE`（仅P3-E09数据模型裁决待确认）
> 边界：逻辑设计与证据契约已完成；生产环境、恢复、安全运行和性能证据保留为部署/专项验收/发布门禁，不代表生产就绪。

> 方向决策：ADR-0004已批准`A、A、A、A、A、A、B、A`；本结论仅消除方案选择歧义，不关闭P3-E01～E07/P3-E09证据门禁。

## 1. 审查范围

- 正式分册：`14-security-design.md`、`17-audit-and-observability.md`、`18-deployment-design.md`、`19-performance-design.md`、`20-test-design.md`。
- 上游：PRD V1.6、SDS Phase 1/2 BASELINE、实现仓库`E:\AICoding\Projects\NPDMS`提交`856d052`。
- 追溯：115项`phase2-contract-map.md`的Phase 3测试类别与证据类型。
- 运行事实：JDK/Node/pnpm、MySQL/Redis/Flyway、宿主机应用边界、构建/类型检查和生产证据缺口。
- 数据设计补充：结构化数据元、核心历史字段映射、项目—合同—订单行—设备迁移结论及DDL漂移门禁。

## 2. 自动校验结果

| 校验 | 结果 |
|---|---|
| PRD语义 | PASS，0 semantic issues |
| 13领域 | PASS，formal=115、V3=22、OUT_OF_SCOPE=9 |
| Phase 2 | PASS，115项契约与追溯 |
| 领域实体迁移对齐 | PASS，全部显式数据对象均有来源与迁移策略 |
| Phase 3 | PASS，5份分册、NFR精确阈值、115项测试/证据映射 |
| Phase 3证据登记 | PASS；P3-E01～E09状态、Owner、事实、证据引用和最晚安全门禁可机器校验，`READY_FOR_SDS_BASELINE`不等于生产就绪 |
| 脚本单测 | PASS，88/88；含Phase 2/3、领域实体迁移、领域编码数据库命名、项目编码身份与永久命名空间、CUS市场行业四维分类且禁止`relation_id`、目标字段目录、P3-E09具体裁决清单、逐Owner证据包、DDL逐项裁决、恢复目标、分层备份、温备/冷备及演练授权、永久业务审计、统一导出审计与24小时文件生命周期、Telemetry分层期限与采样、门禁作用域防漂移和本地开发证据误升级拦截正反用例 |
| 业务命名 | PASS |
| `git diff --check` | PASS |
| 实现仓库前端`ts:check` | FAIL，exit code 1，登记P3-E08 |

可复现命令：

```powershell
py -3 -B scripts\validate_prd_semantics.py --prd docs\baseline\prd-v1.6.md
py -3 -B scripts\validate_prd_domain_generation.py --prd docs\baseline\prd-v1.6.md --domains specs\001-project-delivery-platform\domains
py -3 -B scripts\validate_sds_phase2.py
py -3 -B scripts\validate_domain_entity_migration_alignment.py
py -3 -B scripts\validate_phase3_evidence_register.py
py -3 -B scripts\validate_sds_phase3.py
py -3 -B -m unittest discover -s scripts\tests -p "test_*.py"
py -3 -B scripts\check_business_naming.py
git diff --check
```

## 3. 设计实质性核对

| 维度 | 自审结论 | 关键证据/边界 |
|---|---|---|
| 安全 | PASS-DESIGN | LDAP/AD只建立身份；服务端RBAC/DataScope；凭证五元组、AES-256或同等强度、密钥分离、临时密码不落库、文件/SSRF/回调/秘密扫描和高风险fail-closed均有控制与负向验收 |
| 审计与可观测 | PASS-DESIGN | 业务审计、运行日志、指标、Trace和安全事件分离；关联ID、RED/USE、领域水位、告警/runbook及NFR-03指标明确；未猜测PRD外阈值 |
| 发布/迁移/回退 | PASS-DESIGN | JDK25、pnpm9.15.5、宿主机应用边界、制品/hash/releaseId、Expand→Backfill→Verify→Switch→Contract、应用回退与数据库前滚修复明确 |
| 性能 | PASS-DESIGN | 50用户/30分钟/≥10000请求/P95≤2秒/错误率≤0.5%、20万项目/200万任务、1万/5万树、2000直接子节点、深度30、50MB、99%/60秒均转为可执行口径 |
| 测试 | PASS-DESIGN | 正常、异常、权限拒绝、幂等、并发、集成、事件、文件、安全、三浏览器四视口和发布恢复矩阵完整；115/115均有测试类别和证据类型 |
| 数据/迁移 | PASS-DESIGN-WITH-GATE | 08/09已吸收结构化数据元和历史迁移结论；旧验证哈希漂移明确，`AI-MIG-000`前不得宣称DDL/迁移可执行 |

## 4. 当前阻塞与影响

| 编号 | 待补证据 | 实际阻断点 | 推荐关闭方式 |
|---|---|---|---|
| P3-E01 | 生产网关、域名/TLS、网络区和节点拓扑未登记 | 目标环境部署/生产发布 | 推荐由技术架构+运维在部署时提供生产逻辑拓扑、端口/流向、证书Owner和节点责任表 |
| P3-E02 | MySQL/Redis生产HA、规格和容量未登记 | 目标环境部署、性能验收和生产发布 | 推荐DBA/运维登记服务形态、版本、节点/规格、连接/容量上限及故障切换证据 |
| P3-E03 | 实际备份介质、作业和恢复演练未提交 | 恢复验收和生产发布 | 已批准RPO/RTO及备份策略；由DBA/运维完成隔离恢复演练 |
| P3-E04 | 具体KMS、轮换和应急Owner未登记 | 设备凭证能力及生产发布 | 按ADR-0018在部署时指定，禁止明文或配置占位符降级 |
| P3-E05 | Telemetry具体后端、访问和告警Owner未登记 | 可观测验收和生产发布 | 留存/采样策略已批准；部署时接入企业后端并实测 |
| P3-E06 | 近生产性能环境、数据规模、网络和账号未登记 | 性能验收和生产发布 | 推荐独立性能环境，数据库/Redis/节点规格与生产同级或给出缩放模型；数据按19分册版本化生成 |
| P3-E07 | 外部接口真实地址、认证、白名单、timeout/retry未逐接口登记 | 不阻塞通用设计，阻塞对应Feature联调/上线 | 推荐每个Feature进入实施前完成接口配置档案和真实沙箱契约验证 |
| P3-E08 | 前端`ts:check`失败 | 阻塞前端Feature实现验收、E2E和正式发布 | 推荐独立治理为实现仓库质量工作包；先修公共类型/生成契约，再按PMS页面分组清零，不放宽规则 |
| P3-E09 | 目标DDL与迁移目录/校验哈希漂移，裁决`DEFER` | SDS数据模型基线、历史迁移实施与切换 | 自动归并业务影响项并由需求方确认；纯技术一致性由数据架构按已确认模型裁决 |

## 5. 数据证据专项复核

1. 数据元日常读取以结构化JSON/JSONL为准，只有Excel哈希变化或结构化证据不足才回查原Excel。
2. 旧记录必须同时保留不可变来源载荷和可查询的结构化业务事实；`PAYLOAD`不能代替业务字段落位。
3. 项目—合同、合同—订单为多对多；订单行到项目的分配数量是实施范围核心，缺数量不得进入交付统计。
4. 旧项目组不推断项目树/组合；CRM执行单不替代ERP订单行范围；SN重复不删除生命周期事件。
5. 当前DDL与旧字段目录哈希不一致，历史`passed=true`无当前放行效力；`AI-MIG-000`是DDL与数据迁移的独立硬门禁。
6. CRM市场行业组合目录归CUS并落`cus_market_relation`；客户与项目保存四组编码/名称快照，禁止保存`relation_id`，旧`pm_project.column004～007`按已证实含义落项目名称/行业编码字段。

## 6. 自审结论

Phase 3运行保障设计与证据契约已完成，P3-E01～E08均落在最晚安全门禁。P3-E09会改变数据模型，需先完成业务影响裁决，因此当前仅由P3-E09保持`NO-GO / NOT_READY_FOR_SDS_BASELINE`。裁决关闭并通过独立复审后，再标记正式`BASELINE`并生成SDS总册。
