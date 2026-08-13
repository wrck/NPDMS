# SDS Phase 3 工程化自审

> 日期：2026-08-13
> 状态：`IN_REVIEW`
> 结论：`NO-GO / NOT_READY_FOR_SDS_BASELINE`
> 边界：设计结构与自动校验已完成；生产环境、恢复、安全运行和性能证据未闭环，不生成SDS总册。

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
| Phase 3证据登记 | PASS-STRUCTURE；P3-E01～E09状态、Owner、事实、证据引用和阻塞范围可机器校验，当前正确保持NOT_READY |
| 脚本单测 | PASS，44/44；含Phase 2/3、领域实体迁移、逐Owner证据包、DDL逐项裁决、本地开发证据误升级拦截、证据提交与决策组合防漂移正反用例 |
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

| 编号 | 阻塞 | 影响 | 推荐关闭方式 |
|---|---|---|---|
| P3-E01 | 生产网关、域名/TLS、网络区和节点拓扑未登记 | 14/18生产信任边界和部署不可执行 | 推荐由技术架构+运维提供一张生产逻辑拓扑、端口/流向、证书Owner和节点责任表 |
| P3-E02 | MySQL/Redis生产HA、规格和容量未登记 | 18/19发布与容量判定缺少事实 | 推荐DBA/运维登记服务形态、版本、节点/规格、连接/容量上限及故障切换证据 |
| P3-E03 | 备份介质、频率、保留、RPO/RTO和恢复Owner未确认 | 无法证明发布后可恢复 | 推荐业务Owner先给RPO/RTO，再由DBA/运维形成备份矩阵并完成一次隔离恢复演练 |
| P3-E04 | KMS/等价密钥托管、轮换和应急Owner未登记 | NFR-02和凭证上线阻塞 | 推荐优先采用企业KMS/Secrets Manager；若暂用应用密钥封装，必须密钥与库分离、版本化、最小权限并有轮换演练 |
| P3-E05 | 日志/指标/Trace/告警/安全事件后端、访问和留存未登记 | 17不可执行，高风险审计降级无法验证 | 推荐统一OpenTelemetry采集，后端选企业现有平台；访问按运维/安全/业务审计分权，期限由合规Owner确认 |
| P3-E06 | 近生产性能环境、数据规模、网络和账号未登记 | NFR-01不能验收 | 推荐独立性能环境，数据库/Redis/节点规格与生产同级或给出缩放模型；数据按19分册版本化生成 |
| P3-E07 | 外部接口真实地址、认证、白名单、timeout/retry未逐接口登记 | 不阻塞通用设计，阻塞对应Feature联调/上线 | 推荐每个Feature进入实施前完成接口配置档案和真实沙箱契约验证 |
| P3-E08 | 前端`ts:check`失败 | 阻塞前端Feature实现验收、E2E和正式发布 | 推荐独立治理为实现仓库质量工作包；先修公共类型/生成契约，再按PMS页面分组清零，不放宽规则 |
| P3-E09 | 目标DDL与迁移目录/校验哈希漂移，裁决`DEFER` | 阻塞全部历史数据迁移与切换 | 推荐由数据架构+业务Owner先完成`AI-MIG-000`逐项裁决，再以批准哈希重建全部机器证据和release manifest |

## 5. 数据证据专项复核

1. 数据元日常读取以结构化JSON/JSONL为准，只有Excel哈希变化或结构化证据不足才回查原Excel。
2. 旧记录必须同时保留不可变来源载荷和可查询的结构化业务事实；`PAYLOAD`不能代替业务字段落位。
3. 项目—合同、合同—订单为多对多；订单行到项目的分配数量是实施范围核心，缺数量不得进入交付统计。
4. 旧项目组不推断项目树/组合；CRM执行单不替代ERP订单行范围；SN重复不删除生命周期事件。
5. 当前DDL与旧字段目录哈希不一致，历史`passed=true`无当前放行效力；`AI-MIG-000`是DDL与数据迁移的独立硬门禁。

## 6. 自审结论

Phase 3五份设计、115项验证映射和自动门禁已达到“可接受外部证据并继续评审”的状态，但尚未达到`BASELINE`。P3-E01～E06及P3-E09是Phase 3批准硬阻塞；P3-E07按Feature阻塞；P3-E08阻塞前端实现与发布。保持`IN_REVIEW / NO-GO`，不生成`00-system-detailed-design.md`，不进入下一工程阶段。
