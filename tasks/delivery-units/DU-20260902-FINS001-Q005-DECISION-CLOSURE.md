# DU-20260902-FINS001-Q005-DECISION-CLOSURE F-INS-001审核事实生效裁决收口

> DU状态：`INTEGRATED_COMPLETE`
> DU类型：`GOVERNANCE`
> Feature协调：`F-INS-001=TASK_COORDINATED`
> Task范围：`只关闭Q-FINS001-005并同步PRD、SDS、Feature、Technical Plan、Task和追溯投影；不关闭Q-FINS001-006，不修改业务代码、迁移或Yudao System`
> Owner：`Codex本次master Q-FINS001-005裁决收口会话`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/master-governance/NPDMS`
> 认领基线：`505e52265bb815a48c2e34f7f262389221f642cb`
> 认领提交：`SELF`
> 修改边界：`docs/baseline/**;docs/decisions/open-questions.md;docs/design/08-data-model.md;docs/design/09-database-design.md;docs/design/14-security-design.md;docs/design/15-cache-and-concurrency.md;docs/design/20-test-design.md;docs/reports/*修订012*;specs/001-project-delivery-platform/domains/SRV-*;specs/features/F-INS-001*;specs/features/README.md;docs/superpowers/plans/*f-ins-001*;scripts/tests/test_fins001_plan_and_scope.py;tasks/features/F-INS-001.md;tasks/features/README.md;tasks/delivery-units/DU-20260902-FINS001-Q005-DECISION-CLOSURE.md;tasks/delivery-units/README.md`
> 串行资源：`master PRD下一修订号;Q-FINS001-005状态;F-INS-001当前Task阻断`
> 旧功能范围：`旧pms_srv_rule、旧接口与旧页面保持PRESERVE_EXISTING；本DU不在旧能力上实施新审核或发布逻辑`
> 验证：`PRD源/快照同哈希；PRD语义与基线；领域派生与Requirement追溯；F-INS规格一致性；Delivery Unit；git diff --check；五轴自审`
> 集成记录：`master修订012独立关闭Q-FINS001-005；Q-FINS001-006、Yudao System扩展、生产审核入口和完整发布保持未批准/未实现`

## 裁决

需求方选择方案A：同租户、同revision、同命令/正则内容摘要按`reviewed_at DESC, id DESC`最后审核事实生效。审核事实只追加且仅DRAFT可追加；最后`PASSED`允许发布，最后`REJECTED`阻断；内容变化或新revision重审；权限撤销不回写历史，需要撤销时追加`REJECTED`；审核与发布共享聚合锁/CAS，发布后纠正走新草稿revision。

## 集成边界

- 来源分支`1895a5e7`将Q-FINS001-005与Q-FINS001-006捆绑并授权Yudao System扩展，不能整提交接收。
- master只重建Q-FINS001-005语义为下一正式修订012，不接收Q-FINS001-006、System API、授权Provider或来源工作树未提交文件。
- 本DU不修改Java、SQL、TypeScript/Vue业务代码；现有安全审核Mapper仍在排序前过滤`PASSED`，不得作为最后事实实现证据。Feature保持`IMPLEMENTATION_IN_PROGRESS`，Task 8仍受Q-FINS001-006和Q005实现缺口阻断。

## 完成口径

- `Q-FINS001-005=RESOLVED`且所有下游规格使用同一排序、追加、重审、撤销与并发语义。
- `Q-FINS001-006=OPEN / BLOCKED_BY_SPEC`，不得因Q005关闭增加生产入口或声明Feature Done。
- PRD源文件与冻结快照字节一致，基线元数据、报告、Feature/Task矩阵和DU索引一致。
