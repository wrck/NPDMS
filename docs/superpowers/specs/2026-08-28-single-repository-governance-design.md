# 规格与代码同库治理设计

## 1. 目标

将“项目交付平台”正式规格仓与 NPDMS 实现仓合并为一个 Git 历史、一个工作树事实源和一套变更门禁。合并后，PRD、工程链、SDS、Feature Spec、Technical Plan、Task、代码、迁移、测试和验收证据均在 NPDMS 主仓内完成追溯，不再通过外部仓库提交号、允许清单和快照同步维持一致性。

本次迁移以正式规格提交 `02f6360735980c4bbd9947844917feb0d4b4aecf` 和 NPDMS `master` 为输入。它只改变仓库治理和规格承载方式，不改变已经批准的业务语义、Feature Scope、权限、状态机、数据库模型或发布状态。

## 2. 决策

### 2.1 单一事实源

合并后的 NPDMS 主仓是唯一事实源，优先级保持：

```text
PRD > Engineering Constitution > SDS > Feature Spec
> Technical Plan > Task > Code > Test / Runtime Evidence
```

- `docs/baseline/` 与 `需求/` 承载批准需求和基线；
- `docs/engineering/00-engineering-chain.md` 承载工程治理；
- `docs/design/` 与 `docs/decisions/` 承载正式设计和决策；
- `specs/features/` 承载 Feature Spec；
- `docs/superpowers/plans/` 或当前 Feature 任务记录承载实施计划；
- `tasks/features/` 承载当前实施状态与证据引用；
- 业务模块、`sql/migrations/`、测试和验收记录承载实现事实。

设计或业务语义变化必须先修改同一分支中的上游规格，再修改下游实现。不得用代码、测试或浏览器结果反向覆盖 PRD/SDS/Feature Spec，也不得建立第二套规格副本或状态源。

### 2.2 历史保留

规格仓与代码仓没有共同根提交。迁移通过一个双父 Git merge 提交把正式规格提交接入 NPDMS 历史，merge 的树先保持 NPDMS 内容不变；后续独立提交再导入规格差异并切换治理规则。

这样可以同时满足：

- `git log --all` 可追溯原规格仓全部祖先；
- 不用 `--allow-unrelated-histories` 自动混合两个顶层树并制造大量不可审查冲突；
- 内容选择、旧机制删除和治理切换分别可审查、可回滚；
- 原规格工作树保留为只读历史参照，但不再是后续变更入口。

### 2.3 内容合并边界

正式规格提交对正式资产拥有优先权，NPDMS 对实现资产拥有优先权：

| 路径 | 合并规则 |
|---|---|
| `docs/baseline/`、`docs/design/`、`docs/decisions/`、`docs/engineering/`、`docs/traceability/` | 采用正式规格最新内容；保留 NPDMS 独有的开发、验收和运行文档 |
| `specs/features/` | 采用正式规格最新内容，作为 Feature Spec 唯一正式目录 |
| `scripts/` | 合入正式规格生成器、校验器及测试；保留 NPDMS 构建、实现和运行脚本 |
| `tasks/features/` | 保留 NPDMS 当前实施记录；不使用历史 `tasks/plan.md`、`tasks/todo.md` 驱动开发 |
| 业务模块、前端、`sql/migrations/`、`compose.yaml` | 完全保留 NPDMS 内容 |
| `__pycache__/`、`*.pyc` | 不作为合并输入，不因历史仓存在而新增缓存文件 |

已归档计划、评审和证据中对“双仓”“同步”“实现仓库”的描述是历史事实，不做批量改写。当前有效入口、Feature Spec 结论和校验规则不得继续要求外部同步。

### 2.4 移除双仓同步链

以下资产在治理切换提交中删除：

- `docs/specification-baseline/`；
- `scripts/sync_specification_baseline.py`；
- `scripts/specification_baseline.py`；
- `scripts/validate_specification_baseline.py`；
- 仅验证上述机制的测试。

`scripts/validate_repository_baseline_rules.py` 改为验证同库规则，而不是验证外部 `source.commit`：

- 根 `AGENTS.md` 声明单仓事实源和文档优先级；
- 不再存在 `docs/specification-baseline/manifest.json`；
- 当前任务直接引用 `specs/features/`，不得引用暂存规格目录；
- 规格、实现状态和证据的权威位置保持唯一；
- 历史文档允许保留旧流程描述，但不能成为当前入口。

### 2.5 Feature 与 Requirement 治理

仓库合并不改变已经批准的工程链结论：

- Feature 是唯一 Implementation Done 单元；
- Capability 只维护 Requirement、Feature、依赖和物理 Owner 映射，无状态、无 Gate、无证据副本；
- Requirement Implementation Coverage 按 `Requirement ID + Target Version` 从覆盖义务和必需 Feature 状态派生；
- Feature Ready 由 Feature Spec 维护，Implementation Status 由当前 Feature 任务记录维护；索引和追溯矩阵是投影；
- 公共契约和 Flyway 最终编号在 Feature 合入时串行收口，不新增 Gate。

同库后，规格修订、实现提交、追溯投影和证据引用可以在同一分支的原子提交序列中完成，但仍必须遵守上述权威边界。

## 3. 迁移顺序

1. 在 NPDMS 短期治理分支提交本设计与实施计划。
2. 将正式规格提交抓取为本地临时引用，验证源提交和工作树均干净。
3. 使用 `ours` merge 形成双父历史接点，不改变 NPDMS 文件树。
4. 导入正式规格中 NPDMS 尚缺失或已漂移的正式文件、生成器和测试。
5. 更新根治理入口和当前有效规范，删除外部快照同步链。
6. 执行规格生成器、规格校验器、同库治理测试和 `git diff --check`。
7. 进行合并后代码审查；无 Required/Critical 问题后合入 `master`。

## 4. 失败处理

- 正式规格提交不存在、源工作树有未提交正式文件或 NPDMS 不干净：停止迁移，不猜测来源。
- 正式规格与 NPDMS 在同一正式文件中存在无法由权威边界裁决的语义冲突：标记 `BLOCKED_BY_SPEC`，不自动择一。
- 导入生成器后无法复现受管文档：保留失败输出并修正生成器或输入，不手工伪造生成结果。
- 双父历史接点已提交但内容迁移失败：该接点仍是可回滚的独立提交；不得删除原规格工作树或改写历史。

## 5. 验证

最低验证包括：

- `git log --format=%P -1 <history-merge>` 显示代码与规格两个父提交；
- 正式规格 `02f63607…` 中应合入的非缓存文件在 NPDMS 有明确去向；
- 追溯生成器及相关规格契约测试通过；
- 同库治理测试证明外部 manifest 和同步入口已移除；
- 根治理入口、文档治理入口、Feature 索引和当前任务记录之间无状态权威冲突；
- `git diff --check` 通过，业务代码、迁移和运行配置没有被本治理迁移误改；
- 最终 `master` 工作区干净。

用户已明确本轮不要求 TDD，因此迁移采用“先固化设计与预期、分片修改、每片运行现有及新增治理回归”的验证方式，不创建为了形式而先失败的测试提交。

## 6. 非目标

- 不删除原规格仓目录或其工作树；
- 不推送远端，不重启前后端服务；
- 不修改业务功能、数据库结构、Flyway 文件或运行数据；
- 不重开已完成 Feature，不把 Feature Done 复制为 Requirement Done；
- 不批量改写历史计划、归档评审和既有证据中的旧仓库术语。
