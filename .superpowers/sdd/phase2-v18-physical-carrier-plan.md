# Phase 2 V1.8 Physical Carrier Closure Plan

**Goal:** 关闭 ADR-0029 留下的 WorkBinding/CompletionRule 与 CUT-03 清单结果物理承载缺口，使 Phase 2 能形成可实现、可追溯且不越过 P3-E09 的正式契约。

**Scope:** PM-03、PM-11、CUT-03、INT-12。仅修订 SDS、机器契约和门禁，不修改正式 DDL、不执行历史迁移或数据切换。

## Task 1：锁定物理模型与迁移处置

- [ ] 为 ProjectTemplate 任务定义、ProjectTask 执行契约、完成判定事实定义表、字段、唯一、索引和不可变规则。
- [ ] 为 CutoverChecklist、ChecklistItem、ChecklistItemResult 定义表、字段、唯一、索引和版本规则。
- [ ] 明确现有项目任务前向补齐 TASK_NATIVE；旧割接风险/调研记录只做可证明字段映射，不推断配置版本、答案格式或业务通过。

## Task 2：同步接口、事件、并发和异常契约

- [ ] 完成命令强制携带 task/binding/rule/fact version，并追加完成判定事实。
- [ ] CUT-03 草稿、重匹配、提交和采集结果关联按 checklistVersion/inputSnapshotHash 幂等。
- [ ] 禁止复制 DAC 技术状态为 CUT 业务状态，禁止新增采集阶段、通用工单或结果中转页。

## Task 3：机器契约与回归门禁

- [ ] Phase 2 map 不再出现 BLOCKED_BY_DESIGN。
- [ ] 对象表映射和迁移契约显式登记新增对象及目标表。
- [ ] 新增负向测试，覆盖缺表、错误Owner、TASK_NATIVE绕过、DAC状态复制和无迁移处置。
- [ ] 重生成派生产物并通过现有生成器/validator。

## Task 4：自审、独立复审与Gate

- [ ] 全量脚本测试和正式 validator 通过。
- [ ] fresh-context 独立复审无 Critical/Required。
- [ ] 仅在复审 GO 后把 Phase 2 晋级 BASELINE / READY_FOR_PHASE_3_V1.8。
- [ ] P3-E09 实际 DDL 差量保留为下一阶段，不把 Phase 2 设计完成误写为 DDL 已实现。
