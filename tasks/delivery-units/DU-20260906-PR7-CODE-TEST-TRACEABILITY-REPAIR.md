# DU-20260906-PR7-CODE-TEST-TRACEABILITY-REPAIR PR #7代码、测试与追溯修复

> DU状态：`IN_PROGRESS`
> DU类型：`MULTI_FEATURE_SLICE`
> Feature协调：`F-IMP-002=TASK_COORDINATED;F-COM-001=TASK_COORDINATED;F-ACC-001=TASK_COORDINATED;F-ACC-002=TASK_COORDINATED;F-INT-012=TASK_COORDINATED;F-CUT-001=TASK_COORDINATED;F-CUT-002=TASK_COORDINATED;F-CUT-003=TASK_COORDINATED;F-CUT-004=TASK_COORDINATED;F-CUT-005=TASK_COORDINATED;F-CUT-006=TASK_COORDINATED;F-CUT-007=TASK_COORDINATED;F-CUT-008=TASK_COORDINATED;F-CUT-010=TASK_COORDINATED`
> Task范围：`PR #7已知Mapper重复、测试契约回归、任务/Requirement投影纠正、临时工作流清理及必要回归；不实现未批准业务范围`
> Owner：`wrck授权的本次PR #7审查修复会话`
> 分支：`codex/code-fact-chronological-integration-acc-int-cut-20260904`
> Worktree：`GitHub Actions隔离检出与本次会话审查工作区`
> 认领基线：`220486237b9570ab3d2b0663df39c89be2a5ec69`
> 认领提交：`SELF`
> 修改边界：`.github/**;pms-module-engineering/**;pms-module-commerce/**;pms-module-cutover/**;pms-module-platform/**;pms-module-project/**;scripts/**;tasks/features/**;specs/features/**;docs/traceability/**;docs/engineering/gates/**;tasks/delivery-units/DU-20260906-PR7-CODE-TEST-TRACEABILITY-REPAIR.md;tasks/delivery-units/README.md;yudao-ui/yudao-ui-admin-vue3/**`
> 串行资源：`PR #7分支写入;Feature任务和Requirement生成投影;CI配置`
> 旧功能范围：`NONE`
> 验证：`Java 25全Reactor clean verify；Python治理回归；前端构建/类型/单测；相关MySQL真实集成测试；追溯再生成与check；最终head CI`
> 集成记录：`NONE；仅认领本次修复，不追认来源分支历史授权，不表示PR已合入master`

## 边界

本次用户要求全面审查并修复已知代码问题、任务追溯矩阵和必要测试。master只登记治理认领，不接收PR业务代码；修复候选仍留在PR #7。不得改写PRD/SDS业务语义、Owner或原始572条重放证据，不得跳过失败测试伪造成功，不将IN_PROGRESS Feature升级为Done，不激活F-IMP-002 Task 12待裁决的Controller/Job，不合并PR或自动批准Review。

当前审计起点：PR head `82c207824b9cc8a67668191c40002decc1070f1b`。本DU的验证与交接记录在执行后据实补充；没有执行的测试保持未验证。
