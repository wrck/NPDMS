# 三分支代码事实按时间逐提交重放报告

- 基线：`refs/remotes/origin/master` / `220486237b9570ab3d2b0663df39c89be2a5ec69`
- 重放Head：`1980b62eebc5f6c9c84ec5b9e7ba88e3b9d47350`
- 截止时间：`2026-08-21T00:00:00Z`
- 来源：`codex/f-acc-001-sds@58576666af682bed1a5ea8e40043ff77dde4b2c7`、`prereq-parallel-check-kKiAdn@cdfbd71a1722f9696c1dbb8713566de9e88ff97c`、`codex/f-cut-001-matrices@faed8387d09a82c018f5f03efbbf4b148ffbac69`
- 排序：提交时间优先，同时强制父提交先于子提交。
- 接收：每个来源提交均生成一条重放提交；没有代码净变化时生成空回执提交。
- 模块：不排除任何PMS/Yudao模块；仅不复制旧分支治理文档、运行证据和触发型workflow。
- 冲突：单文件或单hunk冲突不阻断同提交、同模块或同分支的其他代码。

## 提交统计

- 唯一来源提交：`572`
- 重放提交：`572`
- 逐路径裁决：`2436`
- 拒绝hunk/迁移候选记录：`286`

| 来源分支 | 覆盖的唯一来源提交 |
|---|---:|
| `codex/f-acc-001-sds` | 142 |
| `codex/f-cut-001-matrices` | 426 |
| `prereq-parallel-check-kKiAdn` | 4 |

## 路径裁决统计

| 决策 | 数量 |
|---|---:|
| `APPLIED_DELETE` | 3 |
| `APPLIED_FAST_FORWARD` | 59 |
| `APPLIED_MIGRATION_RENUMBERED` | 21 |
| `APPLIED_MIGRATION_UPDATE` | 5 |
| `APPLIED_NEW_FILE` | 56 |
| `APPLIED_PARTIAL_HUNKS` | 38 |
| `APPLIED_SOURCE_SUPERSET` | 7 |
| `APPLIED_THREE_WAY` | 143 |
| `BINARY_SKIPPED` | 39 |
| `CONFLICT_ADAPT_REQUIRED` | 550 |
| `NOOP_CURRENT_SUPERSET` | 321 |
| `NOOP_EXACT` | 1166 |
| `NOOP_MIGRATION_EQUIVALENT` | 28 |

## Feature代码事实

| Feature | 来源提交 | 已接收/主干等价路径 | 冲突或适配 |
|---|---:|---:|---:|
| F-ACC-001 | 40 | 181 | 46 |
| F-ACC-002 | 30 | 189 | 55 |
| F-COM-001 | 35 | 164 | 87 |
| F-CUT-001 | 18 | 36 | 15 |
| F-CUT-002 | 13 | 70 | 28 |
| F-CUT-003 | 16 | 60 | 31 |
| F-CUT-004 | 25 | 71 | 35 |
| F-CUT-005 | 28 | 110 | 62 |
| F-CUT-006 | 24 | 115 | 56 |
| F-CUT-007 | 8 | 35 | 4 |
| F-CUT-008 | 9 | 37 | 7 |
| F-CUT-010 | 5 | 41 | 7 |
| F-IMP-001 | 3 | 8 | 0 |
| F-IMP-002 | 39 | 127 | 89 |
| F-INT-012 | 10 | 85 | 9 |

## 构建配置规范化

- 根POM重复模块登记移除数：`0`

## 仍需适配的代码片段

逐路径记录见CSV；源hunk和不宜直接激活的迁移候选保存在：

`docs/traceability/code-fact-chronological-rejected-hunks-2026-09-04.patch`

这些记录不构成模块、提交或分支级拒绝；其他代码已经继续重放。
