# DU-20260907-CUT-MATRICES-NON-COM-INTEGRATION

> DU状态：`IN_PROGRESS`
> DU类型：`MULTI_FEATURE_SLICE`
> Feature协调：`F-CUT-001=TASK_COORDINATED;F-CUT-002=TASK_COORDINATED;F-CUT-003=TASK_COORDINATED;F-CUT-004=TASK_COORDINATED;F-CUT-005=TASK_COORDINATED;F-CUT-006=TASK_COORDINATED;F-CUT-007=TASK_COORDINATED;F-CUT-008=TASK_COORDINATED;F-CUT-009=TASK_COORDINATED;F-CUT-010=TASK_COORDINATED;F-IMP-001=TASK_COORDINATED;F-IMP-003=TASK_COORDINATED;F-IMP-004=TASK_COORDINATED;F-IMP-005=TASK_COORDINATED`
> Task范围：`用户要求将远程codex/f-cut-001-matrices除COM相关内容全部合入；已有等价内容不重复、主干后继修复不回退`
> Owner：`用户授权的本次非COM整合会话`
> 分支：`codex/cut-matrices-non-com-integration-20260907`
> Worktree：`M:/AICoding/CodexData/worktrees/6644/NPDMS/.run/cut-non-com-integration`
> 认领基线：`97f27074ca3d420f777b649ab53c7d9032c2bf62`
> 认领提交：`SELF`
> 修改边界：`源分支faed8387的非COM差量：docs/**;specs/**;tasks/**;scripts/**;output/**;pms-module-cutover/**;pms-module-engineering/**;pms-module-engineering-api/**;pms-module-platform/**;pms-module-asset/**;pms-module-project/**;yudao-ui/**;pom.xml；共享文件仅接收非COM片段`
> 串行资源：`本会话此前PR #1/7/8/9已合入并停止写入；当前DU独占此次非COM差量接收及索引投影`
> 旧功能范围：`既有CUT/IMP来源接收与迁移解释；保留废弃标记，不重新开放旧写入口`
> 验证：`来源差量完整性、COM排除、后继修复保留、必要聚焦检查及PR远端CI`
> 集成记录：`NONE`

不接收pms-module-commerce、F-COM相关规格/Task/测试/迁移或共享文档中的COM差量。草案和未完成Task保留原状态；不新增生产Provider/Fake，不重放已执行SQL，不覆盖修订017或历史审批证据。源分支历史会作为merge父提交保留，COM排除以最终内容树为准。
