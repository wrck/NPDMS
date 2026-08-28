# V1.8需求基线

本目录保存可供工程实施读取的不可变PRD快照及其元数据。业务语义以当前正式基线为准：

```text
需求/PRD-项目实施交付管理平台.md
版本：V1.8
当前修订：CHG-PRD-2026-08-29-007
状态：正式基线
```

## 基线文件

| 文件 | 用途 |
|---|---|
| `prd-v1.8.md` | PRD V1.8修订007的当前内容快照，已合并修订001—007，供SDS、Feature和测试读取；不得直接编辑 |
| `prd-v1.8-amendment-001-no-manual-project-draft.md` | 已批准增量：手动项目创建失败不保留草稿 |
| `prd-v1.8-amendment-002-organization-and-asset-location.md` | 已批准增量：组织主数据与AST地点所有权 |
| `prd-v1.8-amendment-003-pm07-template-match-decision-history.md` | 已批准增量：PM-07模板匹配决策历史与影响识别 |
| `prd-v1.8-amendment-004-optional-file-security-scan.md` | 已批准增量：文件病毒扫描作为可选部署能力 |
| `prd-v1.8-amendment-005-audit-decisions-and-formal-consolidation.md` | 已批准修订：59项需求方裁决与正式整理 |
| `prd-v1.8-amendment-006-selective-mapping-and-baseline-repair.md` | 已批准修订：PRE-04选择性映射、修订004回写与基线一致性修复 |
| `prd-v1.8-amendment-007-requirement-version-slicing-and-derived-coverage.md` | 已批准修订：111个Requirement目标版本切片、配置基础前置规则及自动派生覆盖 |
| `prd-v1.7.md` | 已被V1.8替代的历史快照；仅用于差异和审计追溯 |
| `requirement-baseline.yaml` | 版本、Requirement/切片数量、哈希、范围分类和校验命令 |
| `baseline-signoff.md` | 基线批准、适用范围和签署记录；不虚构签署人 |
| `change-log.md` | 各正式基线及CHG-01变更索引 |

## 使用规则

1. 生成或更新快照前，必须确认源PRD版本为V1.8且状态为“正式基线”。
2. 快照与源PRD必须使用同一SHA-256；哈希不一致时标记`STALE`，不得作为下游输入。
3. PRD变化必须先更新源文件，再重新生成快照、元数据和追溯矩阵；禁止只改快照。
4. V3和`OUT_OF_SCOPE`只保留追溯，不得进入V1/V2开发承诺。
5. 基线生效后的业务变化必须通过CHG-01记录影响、批准和新版本；当前快照已合并修订001—007，增量文件只作为批准依据和审计追溯，不得在读取快照后重复叠加。
6. 111个正式目标版本切片由PRD附录A.1/A.1.1派生；`requirement-version-coverage.json`和`requirement-matrix.md`必须由生成器同步重建，不得直接编辑状态。
