# 一次性运维迁移脚本（不在主迁移链）

本目录存放面向特定既有环境的一次性外科修复迁移。它们按某环境的实况捕获快照生成，
其 guard 依赖该环境的历史数据状态，不支持全新空库构建，因此不放在 `sql/migrations`
主链中（Q-MIG-V331-20260929-001，需求方 2026-09-30 裁决"迁出主链"）。

## 使用方式

- 已执行过这些脚本的库：`flyway_schema_history` 中的记录保留不动；主链 `migrate`
  服务通过 `FLYWAY_IGNORE_MIGRATION_PATTERNS: "*:missing"` 容忍"历史有记录、目录无
  文件"，无需 Flyway repair。
- 需要在未执行过的既有库上重放时：把本目录临时挂为 Flyway location 单独执行
  （例如 `flyway -locations=filesystem:/flyway/sql-oneoff migrate`），不要放回主链。
- 全新空库：只使用 `sql/migrations` 主链（或 compose.fresh.yaml 的 fresh lineage），
  本目录脚本对新库没有意义，也无需执行。

## 已执行环境清单（2026-09-30 查证 flyway_schema_history）

| 环境 | 数据库 | V331 状态 |
| --- | --- | --- |
| compose 主库（npdms-domain-test-mysql-1） | npdms_domain_test | success=1，已随主链执行 |
| fresh 库（npdms-domain-fresh-mysql-1） | npdms_domain_test | success=1，历史记录保留 |

## 目录内脚本

- `V331__fproj009_deliverable_binding_restore.sql`：F-PROJ-009 交付件绑定修复，
  按 V209 删除事故后某既有库的实况快照重建六个 DRAFT 模板修订的交付件绑定；
  before-guard 逐行比对修复前 designer_document，任何环境差异都会整体拒绝。
