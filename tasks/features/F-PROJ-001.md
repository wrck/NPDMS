# F-PROJ-001 手动项目创建与模板初始化

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Implementation Done Gate：`NOT_READY`（修订019新增量在途；原创建范围PASS保留历史）
> 当前阻断：历史已完成范围不回退；新增语义以当前Feature Spec重验证范围为准，依赖独立验收的路径受Q-TPLACC-001约束
> 当前任务：Task 10查询与角色权限增量已合入master；本分支已接收联合成员事务和UI，已有MANAGE范围的成员操作可用；新建项目首次管理角色范围及完整Feature验收仍待闭环
> Requirement ID：`PM-01`、`PM-03`
> Feature Spec：`specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md`
> 历史创建规格SHA-256：`f7051c41ba5b1214fc9cc82fe72e801d8c8f90e9b43bd21e85d357a8b32aa8bb`（不作当前新增量准入）
> Technical Plan：`docs/superpowers/plans/2026-08-23-v18-organization-location-foundation-and-fproj001-rework.md`
> 锁定规格提交：`975107a665f156ce527480e939ad89a614cd1a21`

## 事实边界

- 2026-09-08主线准备检查：原“转入F-PROJ-002”的下一步属于历史记录，不再驱动当前派发。Q-FPROJ-009已解决业务设计，首次项目经理指派应独立落实到相应Spec/计划/实现复验；Q-TPLACC-001只限制依赖其独立验收合同的新增路径。当前Ready仍只由Spec维护，不以本条准备说明恢复整个Feature Ready或扩张历史Done。详见[主线准备分析](../../docs/superpowers/plans/2026-09-08-project-delivery-mainline-preparation.md)。

- 本 Feature 已从 PRD V1.8 首个 Feature 重新审计并改造，没有根据 V1.7 存量实现推定完成。
- `specs/001-project-delivery-platform/`仅作历史参考，不参与当前门禁校验。
- `tasks/plan.md`、`tasks/todo.md`及 2026-08-21 的旧 F-PROJ-001 计划均不作为当前实施输入。
- 当前受管快照已回写 F-PROJ-001、PM-01、PM-03 的实施完成状态与 Code/Test 证据，规格仓库与 NPDMS 进度一致。
- F-PROJ-001 的局部实施计划完成不表示总体 Implementation Phase 完成。按 PRD 顺序，PM-01/PM-03 之后首个尚未实施的 P0 Requirement 是 PM-02；形成其正式 Feature Spec 是下一正常任务，不构成阻断。Feature Spec 提交并同步前不得开始 PM-02 代码改造，所有当前范围 Feature 完成前不得进入 Deployment。

## 任务完成情况

### 当前主线增量

- Task 10：项目级联合/分次指派、多项目经理与主责管理，`IN_PROGRESS`；上游通用查询增量已合入，代码与固定测试库证据见[查询DU](../delivery-units/DU-20260908-PM01-COMPANY-ROLE-QUERY.md)。成员写入/主责切换、联合事务与UI已接入本分支，完整验收义务仍按[当前计划](../../docs/superpowers/plans/2026-08-23-v18-organization-location-foundation-and-fproj001-rework.md#项目级成员指派增量2026-09-08)推进。
- 本轮原首次-only/固定T-ASSIGN-PM草案已撤下；不设强制绑定Task 11，不用单manager_id代表所有经理。
- 有效PM角色事实及阶段权限直接消费增量已交付，见[角色权限DU](../delivery-units/DU-20260908-PM01-MANAGER-ROLE-FACT.md)；指定用户不再仅凭主责指针授权，完整成员写入/UI仍未完成。
- [ ] AC-FPROJ-011：已有MANAGE范围下的联合/分次成员管理UI及后端取得证据；新建项目首次管理角色范围和完整各阶段/消费者验收未闭环。

#### 2026-09-09 接收已有联合事务与UI增量

按用户“继续完成UI及联合服务经理事务”接收master已有`d31f7903`中的projectmember服务/Controller/测试、members前端API及三个成员组件和详情页入口，SDS10同步对应既定接口。来源为master已提交`tasks/delivery-units/DU-20260908-PM01-MEMBER-CLOSURE.md`，不是另起同职责实现；未接收来源提交中的创建响应修复、项目列表标签、COM测试或已被后续澄清撤销的业务待决表述。原服务经理页面保持，新增成员管理支持多选增补、移除、主责选择和同时指派服务经理，历史刷新沿旧查询。联合请求复用两类资格及审计/Outbox，任一失败整体回滚。

本工作树验证：`mvn.cmd -o -q -pl pms-module-project -am '-Dtest=ProjectManagerMemberMySqlTest,ProjectManagerCandidateServiceTest,ProjectManagerAssignmentApplicationServiceTest' '-DskipITs=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-javaagent:D:/Maven/Repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar' test`退出0，成员MySQL11/11、候选2/2、旧服务经理7/7。仅使用固定23316/npdms_test，权限/范围为测试替身。前端`pnpm exec vitest run --config vitest.pms-file.config.ts src/views/pms/project/project-master-detail/components/ProjectMemberForm.runtime.spec.ts src/views/pms/project/project-master-detail/components/ProjectServiceManagerPanel.spec.ts src/api/pms/project/members/index.spec.ts`为9/9，`pnpm run ts:check`退出0。

相关事务、组件、依赖锁文件、请求配置和直接消费者与来源一致；按工程链6.3复用来源DU的构建和真实浏览器证据（联合服务经理1→100及PM1/100、增补103并切换主责、拦截移除主责未指定接任者、显式接任后刷新历史、320px与键盘验证）。本轮未另启应用或浏览器，不把复用证据说成本轮重跑。接收自审未改变已验证源码，不新增独立审阅任务。

剩余限制按master来源最新交接：首次指派缺失工程管理部/项目管理员角色管理范围是实现漏接，不是需要用户新增创建者例外授权；该范围及共享入口由模板/授权Owner衔接，本次未越界改动。已有MANAGE项目可用不代表所有新建项目可首次指派，也不宣称通知送达、Task 10或Feature Done。本增量按用户授权在feat/zcode-no-chain-impl提交，未推送、未改master状态。

#### 2026-09-09 项目经理成员写入后端提交

PM-01 / Task 10，Owner为PROJ；复用SYSTEM公司角色资格API、既有指派授权与平台幂等/审计/Outbox，不修改上游职责。实现`ProjectManagerMemberController`及`ProjectManagerMemberApplicationService`，新增经理集合增补/移除/主责切换命令；Mapper仅操作`proj_project`和`proj_project_member_assignment`，保留成员历史区间、主责投影和项目版本，不改变阶段或生命周期。缺失权威工号时清空主责工号，不冒用username。

2026-09-08验证：`mvn.cmd -o -q -pl pms-module-project -am '-Dtest=ProjectManagerMemberMySqlTest,ProjectParticipantFactApiImplTest' '-DskipITs=false' '-Dsurefire.failIfNoSpecifiedTests=false' test`首轮角色10项通过，成员6项中仅Outbox失败注入测试未触发；修正测试代理的字段设置方式后定向重跑成员类6/6通过。随后补旧工号清空断言，仅重跑`ProjectManagerMemberMySqlTest#addsMultipleManagersAndChangesPrimaryWithoutRewritingMembershipHistory`，1/1通过。覆盖多人、主责切换、历史、资格/授权拒绝、版本、幂等、并发及事务回滚；使用固定23316/npdms_test，无新环境。权限服务及项目范围守卫有测试替身，不能据此声称真实HTTP全链路授权验收。

本次提交复用上述有效证据；自审确认主责与成员集合分离、租户/版本条件及事务保护保留。未执行UI/真实HTTP验收，未实现联合服务经理事务或通知投递，不晋级Task 10/Feature Done。本分支提交不等于master集成，既有[成员DU](../delivery-units/DU-20260908-PM01-MANAGER-MEMBERS.md)认领边界不在此释放。实现与验证约30分钟（20:42:40～21:13:10），效率目标未达成；后续等待分支提交确认不计入实现耗时。

下面Task 0～9、历史AC与PASS证据继续覆盖原完成范围，不因修订019进入IN_PROGRESS而撤销历史结论；顶部状态表示当前Feature新增量。当前Ready以Spec为准，查询DU完成不等于Task 10或Feature Done。

### 已完成的历史范围

| 任务 | 结果 |
| --- | --- |
| Task 0 锁定 V1.8 正式规格输入 | COMPLETE |
| Task 1 公司、部门编码与组织范围 | COMPLETE |
| Task 2 AST 地点核心模型 | COMPLETE |
| Task 3 地址、站点、位置树与区划映射 API | COMPLETE |
| Task 4 项目多站点与服务经理人工指派 | COMPLETE |
| Task 5 工勘、安装与设备当前位置事实 | COMPLETE |
| Task 6 组织与地点管理页面 | COMPLETE |
| Task 7 项目、工勘、安装与设备页面闭环 | COMPLETE |
| Task 8 MySQL、模块边界与全量回归 | COMPLETE |
| Task 9 真实浏览器业务验收 | COMPLETE |

## 验收跟踪

- [x] AC-FPROJ-001 候选与预览
- [x] AC-FPROJ-002 显式模板创建
- [x] AC-FPROJ-003 唯一默认模板
- [x] AC-FPROJ-004 WorkBinding 完整性
- [x] AC-FPROJ-005 V1 人工确认服务经理
- [x] AC-FPROJ-006 幂等与并发
- [x] AC-FPROJ-007 权限负向
- [x] AC-FPROJ-008 原子失败
- [x] AC-FPROJ-009 真实界面闭环
- [x] AC-FPROJ-010 创建失败无持久化

AC-FPROJ-007 原阻断已由 V1.8 组织与地点基础改造关闭：公司、部门编码、同一行公司—部门授权范围、AST 地址/站点/位置、项目多站点、区划—办事处建议和人工指派均已形成稳定 API、服务端校验、负向测试及真实浏览器证据。站点不绑定公司或办事处；自动建议只按行政区划编码和办事处部门编码映射，最终由授权人员人工确认。

## 验证证据

- 规格快照校验：PASS。
- PMS 模块边界校验：PASS。
- 后端全量回归：30 个 Reactor 模块全部 SUCCESS。
- 真实 MySQL：V1～V68 空库、重复迁移、validate 及 V63→V68 通过；定向场景 12/12 通过。
- 前端合同测试：15/15 通过。
- 前端类型检查：0 错误。
- 前端本地构建：PASS；仅保留既有 legacy CSS `*zoom` 非阻断警告。
- 真实浏览器：组织与地点、多站点项目、人工指派、工勘、安装及设备位置历史场景刷新后均通过。
- 独立规格与质量复审：当前实施计划 `PLAN COMPLETE GO`。

详细证据：

- `output/f-proj-001-v18/database-evidence.md`
- `output/f-proj-001-v18/browser-acceptance.md`
- `output/location-v18/mysql-acceptance.md`
- `output/location-v18/browser-acceptance.md`
- `output/location-v18/regression-summary.md`

## 当前门禁与下一步

代码、Schema、测试、浏览器证据、评审及受管追溯均已满足本 Feature 的工程实现完成条件，Implementation Done Gate 为 `PASS`。该结论不代表 Deployment、SIT、UAT、Release 或治理 GO。

当前阻断为无。PM-02 正式 Feature Spec 已达到 `BASELINE / READY` 并同步到锁定提交 `b453cb0b80804e288be360b50ee0bfef6809b798`。总体 Implementation Phase 继续转入 F-PROJ-002 全新 V1.8 Technical Plan；旧 F-PM02 Spec、Technical Plan 与现有代码只作存量审计输入，不得据此判断已实现。

## 2026-08-25 集成回归关闭

F-PROJ-004 浏览器验收暴露出 F-PROJ-001 与后续统一 ProjectTreeScope 集成后的回归：
根项目创建成功时未同步建立首版树投影，且创建人基础查看范围未被统一范围服务合并。
本次已在原规格边界内修复：根项目创建事务同步发布版本 1 与自路径，创建人仅获得
`VIEW` 基础范围，不新增成员角色或显式授权。定向单测 18/18、MySQL 13/13、项目模块
完整回归 256 项均通过；真实页面创建项目 `992002000102` 后首次进入及刷新详情均通过。
独立裁决结论为 `GO`。详细证据见
`docs/acceptance/F-PROJ-001-root-creation-scope-regression.md`。

## 2026-09-07 修订018影响记录

模板业务规则配置化与独立验收设计已获需求方确认，正文及当前Feature Spec已登记`CHG-PRD-2026-09-07-018`。上文Implementation状态、提交、测试及历史Done保持原范围，不自动覆盖新语义；当前Ready以Feature Spec的REVALIDATION_REQUIRED为准。Q-TPLACC-001限制新增实体创建/范围绑定及其消费者路径，须先完成Phase 2差量再更新唯一实施计划，不从本次文档确认派生Task/Feature完成。
