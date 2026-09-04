# PR #7 代码事实重放有效状态

日期：2026-09-04  
范围：ACC / INT / CUT 三个来源分支的 572 个唯一来源提交。

## 状态模型

本次修复保留两层数据：

| 层 | 用途 | 是否改写 |
|---|---|---|
| 原始层 | 保存 572 次来源重放的 cherry-pick、路径、冲突和 metadata 事实 | 否 |
| 终态层 | 给每个来源 commit/path 追加 Feature 映射与最终整合裁决 | 由生成器确定性派生 |

原始层中存在的 677 个 `CONFLICTING_HUNKS_PENDING` 和 47 个未映射提交不会被删除或伪装成原始成功；终态层通过显式裁决将其转换为可审计结果。

## 已执行的 P0 修复

- `b27d1789`
  - 删除活动序列中的 V204～V219 重复/越界迁移；
  - 删除第二套 INFRA 文件 Artifact Owner；
  - 精确恢复通用 FileClient；
  - 消除 CUT 与 IMP 前端重复实现；
  - 显式启用 Java 25 注解处理。
- `78254a3f`
  - 删除 `FileBusinessObjectPolicyRegistry` 重复 validator；
  - 恢复 ACC 来源分支的单一规范实现。

## 终态派生规则

权威策略：

`docs/traceability/code-fact-chronological-resolution-policy-2026-09-04.json`

生成与校验：

`scripts/validate_pr7_integration_reception.py`

生成物：

- `code-fact-chronological-effective-replay-2026-09-04.json`
- `code-fact-chronological-effective-replay-2026-09-04.csv`
- `code-fact-chronological-effective-replay-2026-09-04.md`

Feature 映射优先保留原始合法映射；仅对 `UNMAPPED` 使用来源分支和 Feature 身份规则派生。INT 统一受 F-INT-012 接收边界约束；ACC 区分 F-ACC-001/002；CUT 根据明确 Feature 编号和业务主题映射 F-CUT-001～010。

## 关键裁决

### INT / PLT

- V203 是 F-INT-012 唯一活动接收迁移；
- `infra_file_artifact/version` 不进入 INFRA Owner；
- `int_device_ops_*` 未完成持久化不进入 master；
- 来源 V104～V106 的可接受 PLT 基础由 V203 successor 承载。

### ACC / COM 历史迁移

历史功能已由 master 的 V160～V176 及其后续迁移承载，不得通过 V207～V219 新版本号再次执行。终态层保留来源事实，但活动 Flyway 序列只保留 canonical migration。

### CUT / IMP 前端

发生重复拼接的文件选择来源分支中最后一个完整、单一实现。终态选择由 TypeScript check 和生产构建验证，不把“最终树被选中”描述为来源 hunk 等价。

## 独立 Gate

终态数据只有在以下证据全部通过后才可作为合并依据：

- Java 25 后端 clean verify；
- TypeScript check；
- 前端 production build；
- MySQL 8.4 空库迁移；
- master 迁移历史不可变；
- Requirement 追溯生成物无漂移；
- 人工 Owner Review。

详见 `docs/engineering/gates/pr-7-integration-gate.md`。
