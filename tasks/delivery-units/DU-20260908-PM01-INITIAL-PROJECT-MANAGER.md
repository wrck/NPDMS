# DU-20260908-PM01-INITIAL-PROJECT-MANAGER 项目级成员指派准备

> DU状态：`CLAIMED`
> DU类型：`GOVERNANCE`
> Feature协调：`F-PROJ-001=TASK_COORDINATED;F-PROJ-005=TASK_COORDINATED;F-PROJ-007=TASK_COORDINATED;F-PROJ-008=TASK_COORDINATED`
> Task范围：`项目交付主线首个单元：按用户新确认落位项目级联合/单独指派与各阶段权限内调整；修订PRD/SDS/Spec/唯一计划及代码Task；撤下首次-only和固定T-ASSIGN-PM方案；本DU不写业务代码`
> Owner：`Codex本次PM-01首次项目经理指派会话`
> 分支：`master`
> Worktree：`E:/AICoding/Projects/NPDMS`
> 认领基线：`49d79d1eedd0f867d8f8f7f9c196607a42c682b8`
> 认领提交：`SELF`
> 修改边界：`tasks/delivery-units/DU-20260908-PM01-INITIAL-PROJECT-MANAGER.md;tasks/delivery-units/README.md;specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md;specs/features/F-PROJ-005-service-manager-manual-assignment.md;specs/features/F-PROJ-007-project-task-tree-and-native-workbench.md;specs/features/F-PROJ-008-project-stage-gate-and-forward-advance.md;docs/superpowers/plans/2026-08-23-v18-organization-location-foundation-and-fproj001-rework.md;docs/superpowers/plans/2026-08-25-f-proj-005-service-manager-manual-assignment.md;docs/superpowers/plans/2026-08-25-f-proj-007-project-task-tree-and-native-workbench.md;docs/superpowers/plans/2026-09-01-f-proj-008-project-stage-gate-and-forward-advance.md;tasks/features/F-PROJ-001.md;tasks/features/F-PROJ-005.md;tasks/features/F-PROJ-007.md;tasks/features/F-PROJ-008.md;docs/design/07-authorization-design.md;docs/design/08-data-model.md;docs/design/09-database-design.md;docs/design/10-api-design.md;docs/design/20-test-design.md;docs/decisions/open-questions.md;需求/PRD-项目实施交付管理平台.md;docs/baseline/prd-v1.8.md;docs/baseline/prd-v1.8-amendment-019-project-member-assignment.md;docs/baseline/README.md;docs/baseline/requirement-baseline.yaml;docs/baseline/change-log.md;docs/baseline/baseline-signoff.md;docs/design/05-state-machine.md;docs/design/06-workflow-design.md;docs/traceability/requirement-matrix.md;docs/traceability/requirement-version-coverage.json`
> 串行资源：`master的上述正式规格/计划与Task；不占用固定测试数据库、Redis或应用端口`
> 旧功能范围：`NONE`
> 验证：`PRD PM-01规则6/9与Q-FPROJ-009一致性、旧实现/直接消费者审计、DU排他边界、引用与差异检查；不运行无关Phase全量审计`
> 集成记录：`NONE`

## 目标与范围

2026-09-08用户进一步明确：服务经理和项目经理可以同时指派，也可以指派服务经理后单独指派项目经理；成员指派不是固定任务，各阶段有对应权限即可调整。本次将该批准语义以修订019落入源PRD/快照及相关契约。原首次-only和Task 11强制绑定草案尚未提交、未实施，停止沿用；下文原认领目的只保留为范围演进记录。项目经理候选组织范围不从PM-08服务经理规则类推，缺失处登记待确认。

用户2026-09-08要求“开始第一个项目交付主线任务”。按主线准备分析第5节，首先推进准备包A，不从CUT/INS旧工作树选取任务。

依据为PRD PM-01@V1规则6/9及已解决的Q-FPROJ-009。PROJ拥有首次PROJECT_MANAGER责任区间、项目负责人投影、双主责状态和该业务命令；SYSTEM拥有人员与公司/部门资格；F-PROJ-005只消费主责事实，F-PROJ-007承载绑定宿主，F-PROJ-008消费完成结果并执行原有阶段推进。不得新增角色、自动指派、批量改派、审批节点或第二套状态源。

本准备出口为可审阅的API/权限/事务/历史/测试合同及唯一代码Task归属。先形成独立可用的指派基础闭环，再接PM-01绑定和S0→S1；现有服务经理、TASK_NATIVE和冻结模板历史保持原行为。Q-TPLACC-001仅约束实际依赖独立验收合同的路径。

此DU激活只授权上述文档写入。业务代码、SQL、前端及运行环境操作必须在明确契约、适用审查和实现DU认领后进入，不由本准备状态自动授予。未决业务选择登记原open-questions并仅停止依赖部分；不重复索要已解决的首次指派主体裁决。

## 交接

- 认领激活：PLANNED已由master提交`5786b318`登记；本次激活提交进入master后才写入所列正式文档。
- 已完成：定位首个主线包及当前PRD/SDS/Feature/Task；审计现有服务经理应用服务、组织API、时态关系及授权入口。
- 剩余：完成直接消费者和模板/运行入口审计；细化契约、唯一计划与代码Task；范围内验证和审阅。
- 测试：未执行业务测试；本单元不声明运行成功。
- 已知失败：尚未新增实现，无本次引入的运行失败。
