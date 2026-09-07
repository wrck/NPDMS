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

不接收pms-module-commerce、F-COM相关规格/Task/测试/迁移或共享文档中的COM差量。草案和未完成Task保留原状态；不新增生产Provider/Fake，不重放已执行SQL，不覆盖修订017或历史审批证据。

## 本次接收方式与内容

用户再次明确COM不要动后，已完整撤销临时整支预合并。当前从干净master按非COM白名单提取，不再载入COM差量，也不把源分支作为merge父提交；原来源历史仍保留在远程分支faed8387。

- 8个缺失文件：设备产品类型消费ADR、F-IMP-001就绪接口来源契约、F-IMP-003/004/005草案及其Task；不抬升Ready或Done。
- 共享SDS仅追加实施就绪、四类完成事实、CUT P4/P5、CUT-08引用/回调/幂等与F-CUT-007 KPI细则；原有COM段落保持不变。
- 补登当前Feature已引用的Q-FCUT003-001、Q-FCUT004-001、Q-FCUT009-001、Q-FIMP002-001/002，保留来源业务裁决，实施状态以master当前Task为准。
- 接收来源中20项到货迁移细化测试，并只将文件引用适配到现行V193～V202；不执行或修改旧SQL。
- F-IMP-004安装前置按修订017的冻结模板条件适用，不对未配置EXE-02的售前路径补造前置。
- 其他非COM代码已与master等价，或由master的后继修复覆盖；保留CUT时间戳、转换器装配、就绪输入校验、到货关联约束、Outbox事件白名单等修复，不回退。
