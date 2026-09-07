# PR #7 ACC / INT / CUT 整合 Gate

> 适用 PR：#7  
> 目标分支：`master`  
> 源分支：`codex/code-fact-chronological-integration-acc-int-cut-20260904`  
> Gate 原则：GitHub `mergeable=true` 只表示文本级可合并；本文件定义真正可合并所需的工程证据。

## 1. P0 数据与代码修复

| 风险 | 修复 | 证据 |
|---|---|---|
| V204～V219 以新版本号重复激活 master 已有/已裁决 DDL | 从活动 Flyway 序列删除；F-INT-012 继续以 V203 六张 PLT 表为唯一接收边界 | `b27d1789`；终态重放 Gate |
| 第二套 `infra_file_artifact/version` Owner | 删除 INFRA Artifact API/DTO/DO/Mapper/Service/Test；恢复通用 FileClient | `b27d1789`；Owner 扫描 |
| CUT 页面重复 handler | 恢复单一 `handleApprovalWorkspaceChanged` | `b27d1789`；前端构建与 occurrence Gate |
| IMP 到货验收交互重复导出 | 恢复单一 write barrier / guarded write / intent 实现 | `b27d1789`；前端构建与 occurrence Gate |
| Platform policy validator 重复方法 | 恢复 ACC 来源分支的单一规范实现 | `78254a3f`；Java 25 clean verify |
| Java 25 注解处理 | 显式启用 `maven.compiler.proc=full` | `.mvn/maven.config`；Java 25 clean verify |

## 2. 代码合并自动化检查

`PR #7 integration gates` 保留三个直接验证实现的 job：

1. **Migration boundary, empty schema, and V160 upgrade**：保护已执行迁移，仅允许已审查的 V161 修正与新增 V204；在 MySQL 8.4 验证空库与升级路径。
2. **Backend clean verify**：Java 25 编译和后端测试，不以跳过测试代替成功。
3. **Frontend typecheck and production build**：前端类型检查与生产构建。

`Integration code regression` 保留后端、前端单元/组件及真实 MySQL 业务回归；权限、事务、幂等、并发和 Owner 回调仍需通过。

文档关键字、固定统计数量、历史重放台账及生成投影检查不再作为每次代码修改的自动阻断条件。相关脚本保留供文档或来源接收范围变更时按需使用，不要求为普通代码修复重写历史证据、补齐无关文档或调整换行。实际改变需求、领域边界的内容仍按现有权威规格处理。

## 3. 原始层与终态层语义

原始重放 JSON/CSV 是不可修改的取证记录，保留：

- cherry-pick 返回码；
- `CONFLICTING_HUNKS_PENDING`；
- 原始 Feature `UNMAPPED`；
- metadata 排除和逐路径接收事实。

终态层不回写这些历史事实，而是给每个来源 commit/path 增加明确的整合决策：

- `EXCLUDED_SUCCESSOR_MIGRATION`：由 master 已批准 successor 迁移取代；
- `EXCLUDED_OWNER_BOUNDARY`：违反既定数据 Owner，明确不接收；
- `RESOLVED_CANONICAL_MIGRATION_SELECTED`：选择最终活动迁移链；
- `RESOLVED_REGENERATED_METADATA`：由最终 Feature/Requirement 状态重新生成；
- `RESOLVED_FINAL_TREE_SELECTED`：选择最终整合树，不声称与来源 hunk 字节或语义等价。

因此，终态台账归零只代表**每项已有明确裁决**；正确性仍由构建、测试、迁移和 Review 独立证明。

## 4. 人工 Gate

自动化全绿后仍需：

- PR 从 Draft 转为 Ready；
- 按实际修改范围完成相关 Owner 审查，不要求每次修改都召集所有领域责任人；
- 所有阻断 review thread 关闭；
- head 更新后在新的 merge SHA 上重新获得全绿结果。

仓库当前若未启用 branch protection/ruleset，自动化结果仍可能被绕过；合并责任人必须按本文件执行，不得仅依据 Merge 按钮状态。

## 5. GO 判定

```text
GO =
  迁移修改限定在已审查范围
  AND MySQL 8.4 空库及升级迁移通过
  AND 业务回归通过
  AND Java 25 clean verify 通过
  AND ts:check 与 build:prod 通过
  AND 人工 Owner Review 完成
  AND PR 非 Draft
```
