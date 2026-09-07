# DU-20260907-CUT-FEATURE-FILE-RECONCILIATION

> DU状态：`IN_PROGRESS`
> DU类型：`MULTI_FEATURE_SLICE`
> Feature协调：`F-IMP-001=TASK_COORDINATED;F-PROJ-006=TASK_COORDINATED`
> Task范围：`用户要求逐文件处理源分支代码及Feature差异；补接遗漏的就绪API说明与实施快照Owner边界`
> Owner：`用户授权的本次CUT逐文件补合会话`
> 分支：`codex/cut-feature-file-reconciliation-20260907`
> Worktree：`M:/AICoding/CodexData/worktrees/6644/NPDMS/.run/cut-non-com-integration`
> 认领基线：`bf8744c37a8830f27f882d26654ac74f8dda838c`
> 认领提交：`SELF`
> 修改边界：`specs/features/F-IMP-001-implementation-readiness-snapshot.md;specs/features/F-IMP-001-physical-contract.json;tasks/features/F-IMP-001.md;specs/features/F-PROJ-006-project-rollback-exception-close-and-reopen.md;本DU`
> 串行资源：`本会话此前PR1/PR7/PR10写入已结束；本次仅认领上述四个文件，不承接其他会话实现`
> 旧功能范围：`既有公开接口与快照Owner的规格补接；不修改旧代码或数据`
> 验证：`逐文件差异、JSON及公开契约引用、Feature状态保留；不重跑未变动业务代码测试`
> 集成记录：`NONE`

来源固定为`codex/f-cut-001-matrices@faed8387`；依据现行PRD EXE-06、SDS02d和SDS09。不修改COM内容、生产代码、SQL、权限、状态机或换行；不把历史候选状态覆盖为当前Feature完成。
