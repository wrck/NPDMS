# 项目文档治理规则

本目录采用“正式资产与过程证据分离”的工程化文档结构。任何新增文档都必须先判断它属于哪一类，再放入对应目录；不得为了方便把评审稿、计划稿、临时分析或外部输入直接放入正式设计目录。

## 1. 文档分类与归属

| 文档类型 | 目录 | 是否可作为工程基线 | 处理规则 |
|---|---|---:|---|
| 业务需求与冻结范围 | `需求/`、`docs/baseline/` | 是 | PRD 与基线快照保持版本、哈希和追溯一致 |
| 正式系统设计 | `docs/design/` | 是 | 只保留规划中的正式 SDS 分册；不为一次评审新建临时设计副本 |
| 已批准决策、待确认问题 | `docs/decisions/` | 决策文件是 | 重要决策使用 ADR；问题关闭后回写正式 PRD/SDS |
| 编码与实现约束 | `docs/coding/` | 是 | 记录所有后续开发必须遵守的工程编码规则；变更规则时同步更新仓库级入口 |
| Feature规格、实施任务、Delivery Unit与追溯投影 | `specs/features/`、`tasks/features/`、`tasks/delivery-units/`、`docs/traceability/` | Feature Spec、Feature Task和`DU-*.md`分别是各自维度权威；README和生成矩阵否 | Ready以Feature Spec为权威，Implementation状态以Feature Task为权威，认领/写边界/集成回执以master的Delivery Unit为权威，索引与矩阵只作投影 |
| 阶段门禁、评审结论、评审输入 | `docs/engineering/gates/<phase>/` | 仅当前门禁状态可作为放行依据 | 当前汇总、输入证据和历史归档分层保存 |
| 工程计划、规格草案 | `docs/superpowers/specs/`、`docs/superpowers/plans/` | 否 | 必须指向目标正式资产；完成后更新正式文档，计划本身不替代 SDS |
| 脚本生成的报告或快照 | `docs/generated/` 或对应门禁目录 | 否 | 标注生成时间、脚本版本和输入哈希；禁止伪装成正式设计 |
| 外部资料、历史规格、原始分析 | `docs/reference/` 或门禁 `input/` | 否 | 保留来源和获取时间；不得直接驱动实现 |
| 临时草稿、调试输出 | 仓库外或被 `.gitignore` 忽略的临时目录 | 否 | 不得提交到正式文档目录 |

## 2. 正式目录的准入规则

### `docs/design/`

- 文件名必须对应工程链规定的 SDS 分册，或使用已登记的补充分册编号；禁止使用 `draft`、`review`、`tmp`、`copy`、`final2` 等临时后缀。
- 文档必须说明适用 PRD 版本，并列出对应 Requirement ID；推导内容标记 `【建议】`，未知内容标记 `【待确认】`。
- 评审意见只允许作为修订依据，不能以“评审报告”形式混入设计正文。修订后直接更新正式分册，并在门禁记录中引用差异。
- 同一主题只能有一个当前正式文件；需要试验替代方案时放入 `docs/superpowers/specs/` 或门禁 `input/`，不得复制出第二份 SDS。

### `docs/engineering/gates/`

- 每个阶段必须有一个 `gate-status.md` 作为当前门禁汇总。
- `input/` 保存收到的原始评审材料，原则上不改写。
- `archive/` 保存被替代的历史材料，只读追溯，不得作为当前放行依据。
- 评审结论要能回指 Requirement ID、正式设计文件、门禁编号或决策编号；无证据的结论不能关闭门禁。

## 3. 文档状态与晋级

推荐使用以下状态：

`DRAFT` → `IN_REVIEW` → `BASELINE` → `SUPERSEDED` / `ARCHIVED`

- `DRAFT`：正在形成，不能作为实现输入。
- `IN_REVIEW`：正在评审，不能绕过门禁进入下一阶段。
- `BASELINE`：已批准并纳入当前工程链，可作为下游输入。
- `SUPERSEDED`：被新版本替代，保留历史链接和替代关系。
- `ARCHIVED`：仅作历史证据，不得恢复为当前依据而不经过重新评审。

状态变更必须在文档头部、对应门禁汇总和必要的变更记录中同步；不能只修改文件名表达“已通过”。

## 4. 新增、修订和归档流程

1. 先分类：确认目标目录和文档状态，避免把中间稿写入正式目录。
2. 再引用：记录来源 PRD 版本、Requirement ID、相关决策和上游文档。
3. 后评审：评审材料进入 `docs/engineering/gates/<phase>/`，不复制正式 SDS。
4. 结论回写：通过后修订正式 SDS/PRD/ADR，并在 `gate-status.md` 记录证据；未通过则保留阻塞状态。
5. 再归档：被替代的评审稿移动到 `archive/`，保留原文件内容、日期和替代关系。

## 5. 提交前自检

- `docs/design/` 是否只包含正式分册和已登记补充分册？
- 是否误把 `review`、`draft`、`analysis`、`plan`、`input` 文件放进正式目录？
- 所有结论是否能追溯到需求、设计、决策或门禁证据？
- 被替代文件是否归档而不是覆盖？
- 生成文件是否记录输入、脚本和版本，且没有冒充基线？
