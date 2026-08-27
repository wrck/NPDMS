# F-SOL-002 Deployment 候选记录

> deploymentCandidateId：`NPDMS-FSOL002-DEPLOYMENT-CANDIDATE-20260827-01`
> Gate裁决：`NPDMS-FSOL002-DEPLOYMENT-20260827-01 / BLOCKED_BY_EXTERNAL_INPUT`
> 状态：`NOT_DEPLOYED / BLOCKED`
> releaseId：`UNASSIGNED`
> buildId：`UNASSIGNED`
> 目标环境：`UNASSIGNED`
> Deployment Owner / 复核人：`UNASSIGNED`

## 冻结范围

- Requirement：`PRE-02（V1/P0）`；Feature：`F-SOL-002`。
- NPDMS候选提交：`993f59bb72bc674a3acf434837ed5cf9ade98a41`。
- 规格提交：`7c482d02154f0f967a4263e9d27fef2c77aa8bff`。
- 数据库前向迁移：`sql/migrations/V97__fsol002_preparation_seed.sql`。
- 运行依赖：MySQL、Redis、MinIO；MinIO保存文件对象，ClamAV仅为可选内容扫描基础设施。
- 不包含当前工作树中未提交的ClamAV增量，也不包含SIT、UAT或Release。

## 已有候选证据

- Implementation Done：`7243727f3e3410bd3b6ca965b1f5759e6ba5872a`及独立复审GO。
- 退回版本重提交修复：`062ca38c9250c4a0aa696df8dfd1716feb942e1b`。
- 浏览器、后端、前端、真实MySQL、V97空库迁移及MinIO证据：`docs/engineering/evidence/f-sol-002-browser-evidence.json`。
- 受管规格快照和仓库基线规则在`993f59bb`提交前均已验证通过。

以上只证明候选实现和本地开发/验收剖面可用，不构成目标环境部署结果。

## Deployment PASS 尚缺输入与运行证据

- [ ] 目标环境ID、Deployment Owner、复核人及环境准备结果。
- [ ] 正式`releaseId`、`buildId`，以及绑定`993f59bb`的不可变后端/前端制品记录。
- [ ] `configVersion`、秘密引用和目标环境差异校验结果。
- [ ] `migrationVersion`及目标环境Flyway `info/validate`与执行记录。
- [ ] 目标环境部署流水线或等价版本化运行记录。
- [ ] 部署后健康、日志/指标、数据库、Redis、MinIO技术探针。
- [ ] PRE-02最小业务探针、权限拒绝探针及回退/前滚处置记录。

上述项目未关闭前，Deployment Gate保持`BLOCKED`，不得进入SIT。
