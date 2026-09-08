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
> 集成记录：`本次master文档提交落位修订019及唯一Task 10准备；仅项目经理候选组织范围待Q-FPROJ-010确认；未实施业务代码或晋级Feature Done，本DU释放文档写边界`

## 目标与范围

本轮继续：用户已确认项目经理候选限定同公司、允许跨部门的有效在职人员，Q-FPROJ-010业务待决关闭。本次复用已有文档边界完成裁决回写、集合API/事务/消费者契约及Task 10准备；代码写入仍须另有实现DU。原认领范围及计划已在master登记，本次重新激活进入master后才修改正式资产。既有验证可复用处不重跑，不增加hash门禁。

2026-09-08用户进一步明确：服务经理和项目经理可以同时指派，也可以指派服务经理后单独指派项目经理；成员指派不是固定任务，各阶段有对应权限即可调整。本次将该批准语义以修订019落入源PRD/快照及相关契约。原首次-only和Task 11强制绑定草案尚未提交、未实施，停止沿用；下文原认领目的只保留为范围演进记录。项目经理候选组织范围不从PM-08服务经理规则类推，缺失处登记待确认。

用户2026-09-08要求“开始第一个项目交付主线任务”。按主线准备分析第5节，首先推进准备包A，不从CUT/INS旧工作树选取任务。

依据为PRD PM-01@V1规则6/9及已解决的Q-FPROJ-009。PROJ拥有首次PROJECT_MANAGER责任区间、项目负责人投影、双主责状态和该业务命令；SYSTEM拥有人员与公司/部门资格；F-PROJ-005只消费主责事实，F-PROJ-007承载绑定宿主，F-PROJ-008消费完成结果并执行原有阶段推进。不得新增角色、自动指派、批量改派、审批节点或第二套状态源。

本准备出口为可审阅的API/权限/事务/历史/测试合同及唯一代码Task归属。先形成独立可用的指派基础闭环，再接PM-01绑定和S0→S1；现有服务经理、TASK_NATIVE和冻结模板历史保持原行为。Q-TPLACC-001仅约束实际依赖独立验收合同的路径。

此DU激活只授权上述文档写入。业务代码、SQL、前端及运行环境操作必须在明确契约、适用审查和实现DU认领后进入，不由本准备状态自动授予。未决业务选择登记原open-questions并仅停止依赖部分；不重复索要已解决的首次指派主体裁决。

## 交接

### 修订019当前结果

- 用户确认已落源PRD/快照及SDS/Spec/唯一计划：联合/分次指派、各阶段项目级调整、多名项目经理及一个当前主责、同角色同操作权限。不绑定固定任务；原未提交首次-only/Task 11草案已撤下。
- 当前唯一业务待决为Q-FPROJ-010候选组织范围；不把PM-08服务经理的公司+办事处规则外推给PM-01。权限待决已由用户关闭。
- 主责ID/姓名/工号可作为责任投影保留，完整成员关系才是经理角色集合；集合授权、当前主责显示和其他消费者仍须实际实现/验证。本轮无业务源码、SQL、测试代码或运行数据改动。
- 只读审阅确认新方向正确，并发现旧DoR/旧接口测试项/下游当前说明三处残留；已修正，旧Task/GO保留明确历史范围。该审阅不授予实现Ready/Done。
- 最后差量复核已确认三处整改及“同角色同权限、Q仅剩候选范围”在范围内无待改文档矛盾；不重复审查已确认事实。
- 验证：本次PM-01/03/08/09/11范围语义检查通过；源PRD和快照无差异；Requirement投影已按新源生成；DU排他与差异格式检查通过。完整语义/基线检查仍有ACC-03第7个THEN的既有UNOBSERVABLE_ACCEPTANCE（基线检查72通过、1失败）；已从变更前82a60786源码复现，未改ACC或将全量结果写成PASS。
- 未执行：业务编译、Unit/Integration、MySQL、Schema迁移及浏览器；原因是本轮仅契约准备、候选规则仍未决，且未占用运行窗口。旧MySQL支持类带表级失败触发器，后续必须先确认专用Schema，不默认隔离。
- 用户要求减少不必要hash校验：复用仍适用的语义/差异/源快照一致性证据，不再重复哈希或指纹检查，不增加新门禁；既有基线元数据已随最终PRD更新，仅保留追溯用途。本条不修改全局规则或技能。

### 原开工记录（历史）

- 认领激活：PLANNED已由master提交`5786b318`登记；本次激活提交进入master后才写入所列正式文档。
- 已完成：定位首个主线包及当前PRD/SDS/Feature/Task；审计现有服务经理应用服务、组织API、时态关系及授权入口。
- 剩余：完成直接消费者和模板/运行入口审计；细化契约、唯一计划与代码Task；范围内验证和审阅。
- 测试：未执行业务测试；本单元不声明运行成功。
- 已知失败：尚未新增实现，无本次引入的运行失败。
