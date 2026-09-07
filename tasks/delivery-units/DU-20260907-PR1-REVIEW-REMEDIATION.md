# DU-20260907-PR1-REVIEW-REMEDIATION PR #1复核整改

> DU状态：`IN_PROGRESS`
> DU类型：`MULTI_FEATURE_SLICE`
> Feature协调：`F-PROJ-001=TASK_COORDINATED;F-PROJ-005=TASK_COORDINATED;F-PROJ-008=TASK_COORDINATED;F-COM-001=TASK_COORDINATED`
> Task范围：`用户确认的PM-01首次项目经理职责落位、参考Schema通用字段规范对齐、关闭状态摘要及PR #1与master冲突处理；仅规格与相关验证脚本，不实施应用功能`
> Owner：`用户授权的本次PR #1复核整改会话`
> 分支：`codex/pr1-review-remediation-20260907；提交后快进更新远程docs/prd-v1.8-p0-p1-fixes`
> Worktree：`M:/AICoding/CodexData/worktrees/6644/NPDMS/.run/pr1-repair`
> 认领基线：`c104ae0882e6bba517d6b10e6ca3f7c5a7b10e8d`
> 认领提交：`SELF`
> 修改边界：`docs/**;scripts/**;specs/**;需求/**;tasks/delivery-units/DU-20260907-PR1-REVIEW-REMEDIATION.md`
> 串行资源：`PR #1分支；本会话PR #7/8/9代码均已合入，暂停其旧认领路径写入，文档修订由本DU接续`
> 旧功能范围：`NONE`
> 验证：`Feature职责对照、参考Schema生成及隔离MySQL约束验证、相关脚本聚焦回归、冲突与主干应用代码保留检查`
> 集成记录：`NONE；当前仅登记认领，不批准PR合并或Feature Done`

保留master已合入的ERP来源、租户默认、服务经理请求、COM调整预览与权限错误修复。按数据库规范统一version/creator/updater/create_time/update_time/deleted；不改已执行Flyway，不覆盖历史证据，不新增审批门禁。用户接受业务整改方向不自动替代全部产物的独立复审。
