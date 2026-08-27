# Task 1 Round 3 修复报告

## 结论

- 当前核心迁移 DDL 已收敛为 61 表、1,250 列、452 项 DDL 约束/索引、61 项表选项。
- DDL SHA-256：`4305DA939E094423CE91323AE0C24919D2A31F2DD660A316788684D8A58461B1`。
- 隔离 MySQL 8.4.10 执行 PASS：61 表、1,250 列、328 项主键/唯一键/同域外键/CHECK（PK 61、UK 127、FK 48、CHECK 92）。
- P3-E09 仍为 `OPEN/BLOCKED_BY_REVIEW`，未生成 `approvedDdlSha256`，未授权生产迁移或旧库写入。

## 用户决策落地

- 删除当前历史工单、历史工时空壳对象/表；PRD 8.2 只保留“历史业务事实不可删除”治理规则，后续须识别真实来源且由需求方确认迁移后再独立建模。
- 删除目录快照对象/表；INT-05/INT-09 复用基础平台现有用户/部门/岗位主数据、`plt_sync_batch`与`plt_external_key_mapping`，不新建替代表。
- `pm_project_maintenance` 仅在顶层 `excludedSources` 保留 `EXCLUDED/NO_MIGRATION` 审计，不挂在任何业务对象，`records` 中目标来源数为 0，字段级目标绑定为 0。行数/提取批次哈希未伪造，保持 `PENDING_EXTRACTION_AUDIT`。
- CUT-11 只由当前平台受控命令创建，不从排除表或字段相似性推断。

## 61 表范围审计

- 61/61 当前表均有精确的 V1/V2 Requirement 或 ADR-0022 已接受公共迁移技术规则回指；审计冲突清单为空。
- `srv_service_incident*` 回指 EQP-07/INT-02；`com_crm_*`及执行关系回指 INT-01/COM-01；公共文档回指 PLT-02；分析投影回指 ANA-01/RPT-02；三张迁移血缘/问题技术表回指 ADR-0022。
- 机器禁止清单及负向测试拒绝：历史工单/工时空壳、目录快照、通用工单/处理记录/SLA、工时申领/调整、续保、日报/周报、4 张 KNO V3 治理表进入 DDL 或对象表映射。

## 迁移和字段证据

- 领域迁移契约：85 对象、96 来源处置；全部字段绑定 40，V1.7 绑定 18，其中旧库绑定 14，覆盖 8 张来源表、19 个唯一来源字段。
- 目标字段目录：61 表、1,250 字段；V1.7 差量 11 表、185 字段，`missingBasisCount=0`，10 个字段有精确旧数据元坐标，其余设计字段使用 basisRefs 而不伪造数据元。
- 物理证据 3,931 行、3,908 规范字段；`invalidTargetCount=0`、`forbiddenTargetNameCount=0`。`pm_project_maintenance` 的 44 个物理字段只标记 `EXCLUDED`。
- 漂移/逐项寄存器、约束库、模型目录、MySQL 执行证据和 Phase 3 证据包均从当前同一 DDL 哈希重建。逐项寄存器 1,900 项，当前约束/索引库 452 项，Q08 候选索引 124 项。

## 验证结果

- 定点 scope/core 契约单测：26/26 PASS。
- 全量 `unittest discover`：140/140 PASS。
- Phase 2 契约、领域迁移契约、字段目录、模型目录、Phase 3 包的 `--check`：PASS。`generate_ddl_drift_review.py` 没有 `--check` 参数，已通过重生后的 core/domain/P3-E09 validator 验证其当前输出。
- core schema、database naming、AI-MIG-000 逐项寄存器、domain alignment、Phase 3 register、SDS Phase 2、SDS Phase 3 validator：全部 PASS。
- `git diff --check`：PASS。

## 已知限制

- 排除源的真实行数和提取批次 SHA-256 尚未采集，不影响“不迁移”决策，但排除审计仍为 `PENDING_EXTRACTION_AUDIT`。
- DDL 可执行只是候选证据，不等于 Reviewer 批准。P3-E09 继续 OPEN。
- 本轮未提交，等待独立复审。
