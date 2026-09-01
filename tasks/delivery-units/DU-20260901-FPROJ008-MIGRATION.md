# DU-20260901-FPROJ008-MIGRATION F-PROJ-008实施迁移

> DU状态：`INTEGRATED_PARTIAL`
> DU类型：`FEATURE`
> Feature协调：`F-PROJ-008=FEATURE_EXCLUSIVE`
> Task范围：`选择性集成Task 1-2；Task 3受Q-FPROJ-009阻断且未集成`
> Owner：`Codex本次master工程链调整会话`
> 分支：`codex/f-proj-008-stage-advance`
> Worktree：`M:/AICoding/CodexData/worktrees/7a76/NPDMS`
> 认领基线：`d5efff584aed475deeec1e9895b7c6169c27af5b`
> 认领提交：`SELF`
> 修改边界：`docs/baseline/prd-v1.8-amendment-009-project-bpm-definition-identity.md;docs/decisions/0043-project-stage-gate-evaluation-and-advance.md;docs/decisions/open-questions.md;docs/design/02d-cross-context-contracts.md;docs/design/09-database-design.md;docs/design/10-api-design.md;docs/design/15-cache-and-concurrency.md;docs/design/16-exception-and-idempotency.md;docs/superpowers/plans/2026-09-01-f-proj-008-project-stage-gate-and-forward-advance.md;docs/generated/branch-history-audit-2026-09-01-proj-integration.md;docs/traceability/**;pms-module-integration/**;pms-module-project/**;scripts/generate_requirement_traceability.py;scripts/validate_implementation_baseline_inventory.py;scripts/tests/test_implementation_baseline_inventory.py;specs/features/F-PROJ-008-*;tasks/implementation-baseline-inventory.json;tasks/features/F-PROJ-008.md;tasks/features/README.md;tasks/delivery-units/DU-20260901-FPROJ008-MIGRATION.md;tasks/delivery-units/README.md`
> 串行资源：`PRD revision-009;PROJ公共契约;Feature状态;Delivery Unit索引;追溯投影`
> 旧功能范围：`NONE`
> 验证：`Task 1聚焦11项PASS；Task 2聚焦10项PASS且真实MySQL 2项无跳过；pms-module-project,pms-module-integration package PASS；PRD与追溯治理校验PASS`
> 集成记录：`源0c7a96349678d9b2b3cc8c90b54b30044934de45选择性集成至master@db876b43f4cd12b87525f301384c6d94860363f4；源d69b3ff849354b3986ca9f03e765ea30caabdb81复核适配后集成至master@158118d0a085d0ee1dc08c7c74fe10936537752c`

## 审计结论

分支继承的COM-B/CUT/IMP提交不属于F-PROJ-008认领，F-INS也未进入本次选择范围。来源工作树中的F-SOL-003脏改动、运行PID删除和其他未提交内容均未迁入。master只接收Task 1的六类Owner/Flowable身份链和Task 2的readiness、流程启动、原子相邻推进；源Task 3提交`a3bd00438d8be9bdd18f90802c7370af4152efdd`及分支治理头`48175aa0e8185c54d08ee546daef3018f6fcfbd3`未集成。

## PROJ三分支时间线裁决

- `codex/v1-8-feature-revalidation-50eb@68db25b3c6bd6af8785fa54f018d5d54c504117f`只有一条独立裁决规则提交；`git cherry master`为负号，补丁已由master提交`29e9a415df9806eb2ca874b43bcd017eac6be6dd`等价包含，并由`0b0f9f9aa149cb6aad8635ccbf5e540756277f0d`继续修订。裁决：`ALREADY_IN_MASTER / SUPERSEDED`，不再合并。
- `codex/f-proj-001-atomic-alignment@8bbaf69ae12583343b935521c27969fb85b7851e`在2026-08-21 14:42～15:43仅形成六条旧链提交，旧Task停在中段且验收项未完成。master当前`F-PROJ-001`已依据后续V1.8锁定规格`975107a665f156ce527480e939ad89a614cd1a21`完成Task 0～9及AC 1～10，并在`b743afc5fa61f1c2a4b9a25ef4f46015645cd48e`关闭集成回归。裁决：`SUPERSEDED / DO_NOT_MERGE`，旧分支不得继续实施或作为新工程链起点。
- `codex/f-proj-008-stage-advance@48175aa0e8185c54d08ee546daef3018f6fcfbd3`按提交边界裁决：Task 1=`INTEGRATED`，Task 2=`INTEGRATED`，Task 3=`BLOCKED_BY_SPEC / NOT_INTEGRATED`。F-PROJ-008权威状态仍为`IN_PROGRESS`，不得从分支自报完成推导Feature Done。

## 集成回执

- master规格链：`d5efff584aed475deeec1e9895b7c6169c27af5b`。
- Task 1 master提交：`db876b43f4cd12b87525f301384c6d94860363f4`。
- Task 2 master提交：`158118d0a085d0ee1dc08c7c74fe10936537752c`。
- 未集成：Task 3 UI、分支PRD revision-011汇总候选、分支Task/索引/追溯投影、F-SOL脏改动、F-INS及所有继承的COM/CUT/IMP历史。
- 剩余：`Q-FPROJ-009`关闭后，从最新master新建有效DU实施Task 3并完成真实Chromium正向闭环；不得回到旧分支继续开发。
- 结论：`INTEGRATED_PARTIAL`；本DU写边界已释放，master允许保留当前可构建增量，但Feature Implementation Done仍为`NOT_STARTED`。
