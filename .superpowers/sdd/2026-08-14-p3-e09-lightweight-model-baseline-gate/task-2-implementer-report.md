# Task 2 实现报告：P3-E09 机器契约与 Phase 3 状态同步

## READ 与影响

- 已核对 PRD V1.7 的历史事实/迁移边界、工程链 V1.8、文档分类规则、08/09 数据模型与数据库设计、轻量门禁计划、Task 1 最终实现状态。
- 本任务只调整 P3-E09 的 SDS 数据模型基线机器状态：不修改 PRD、60 表 DDL、领域/API/权限、状态机或业务流程。
- `approvedDdlSha256` 保持显式 `null`；历史数据迁移和数据切换仍保留为阻断范围。

## PLAN 与实现

- 生成器根据当前 DDL、`DEFER=0`、隔离 MySQL 8.4 执行证据和同步机器契约生成 `MODEL_BASELINE_READY`。
- 核心迁移契约与 1,883 项裁决寄存器均登记同一模型基线事实；逐项内容、Items SHA 和当前 DDL SHA 未改。
- P3-E09 模板删除四角色 `signoffs`/`attestationMethod` 字段，新增“不得用于授权历史数据迁移或数据切换”的明确限制。
- Phase 3 正式寄存器仅保留 `HISTORICAL_DATA_MIGRATION` 与 `DATA_CUTOVER` 两个 blocks，保持 `OPEN`，不生成迁移批准。

## 验证

- PASS：定点 `unittest`（生成器与同步器，3 项）。
- PASS：完整 `unittest discover -s scripts/tests -p "test_*.py" -v`（181 项）。
- PASS：生成器与同步器 `--check`。
- PASS：核心迁移契约、DDL 决策寄存器、Phase 3 evidence register 校验。
- PASS：相关脚本 `compileall` 与 `git diff --check`。

## 自审与范围

- `MODEL_BASELINE_READY` 未填充或复用 `approvedDdlSha256`；Q08 仍为 122 项候选索引，继续要求 Feature 查询计划和 P3-E06 性能验收。
- 未读取、修改或暂存两份受保护未跟踪资料；未修改 `progress.md`；未推送。
- Task 3 的正式文档统一不在本任务范围内。
