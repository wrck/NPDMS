# DU-20260906-PR7-CODE-TEST-TRACEABILITY-REPAIR PR #7代码、测试与追溯修复

> DU状态：`IN_PROGRESS`
> DU类型：`MULTI_FEATURE_SLICE`
> Feature协调：`F-IMP-001=TASK_COORDINATED;F-IMP-002=TASK_COORDINATED;F-COM-001=TASK_COORDINATED;F-ACC-001=TASK_COORDINATED;F-ACC-002=TASK_COORDINATED;F-INT-012=TASK_COORDINATED;F-CUT-001=TASK_COORDINATED;F-CUT-002=TASK_COORDINATED;F-CUT-003=TASK_COORDINATED;F-CUT-004=TASK_COORDINATED;F-CUT-005=TASK_COORDINATED;F-CUT-006=TASK_COORDINATED;F-CUT-007=TASK_COORDINATED;F-CUT-008=TASK_COORDINATED;F-CUT-010=TASK_COORDINATED`
> Task范围：`PR #7代码、测试、任务追溯修复；COM ERP来源字段；CUT独立流程；治理套件；用户确认的单租户1默认及前向迁移`
> Owner：`wrck授权的本次PR #7审查修复会话`
> 分支：`codex/code-fact-chronological-integration-acc-int-cut-20260904`
> Worktree：`GitHub Actions隔离检出与本次会话审查工作区`
> 认领基线：`220486237b9570ab3d2b0663df39c89be2a5ec69`
> 认领提交：`SELF`
> 修改边界：`.github/**;pms-module-engineering/**;pms-module-engineering-api/**;pms-module-commerce/**;pms-module-cutover/**;pms-module-platform/**;pms-module-project/**;scripts/**;tasks/features/**;specs/features/**;specs/001-project-delivery-platform/domains/**;docs/traceability/**;docs/design/**;docs/baseline/**;docs/development.md;docs/engineering/gates/**;docs/decisions/**;tasks/delivery-units/**;yudao-ui/yudao-ui-admin-vue3/**;yudao-server/src/**;yudao-framework/yudao-spring-boot-starter-biz-tenant/src/**;sql/migrations/**;需求/**`
> 串行资源：`PR #7分支写入;Feature任务和Requirement生成投影;CI配置;单租户默认与前向迁移`
> 旧功能范围：`NONE`
> 验证：`Java 25全Reactor clean verify；Python治理回归；前端构建/类型/单测；COM及CUT MySQL真实集成；单租户1及迁移负向验证；追溯再生成与check；最终head复审`
> 集成记录：`NONE；仅认领本次修复，不追认来源分支历史授权，不表示PR已合入master`

## 边界

master只登记治理认领，不接收PR业务代码；修复候选留在PR #7。不得改变未获用户授权的PRD/SDS语义、Owner或原始572条重放证据，不得跳过失败测试伪造成功，不将IN_PROGRESS Feature升级为Done，不激活F-IMP-002 Task 12待裁决的Controller/Job，不合并PR或自动批准Review。

初始审计起点：PR head `82c207824b9cc8a67668191c40002decc1070f1b`。验证与交接记录据实补充；没有执行的测试保持未验证。

## 2026-09-07 第一轮修复范围（历史）

`757ec7c8eb4df9b18658191176ae5e029f0fbcc5`起补EXE-06公开事实校验，未改变当时待裁决的租户语义；COM保留幂等和业务表均不写入的强断言，CUT重复桩去重。后续修复已进入`598d79659c30b69d049f9a4531b4bd5da925b866`，不追认Feature Done。

## 2026-09-07 当前用户裁决

1. 销售订单、订单行是ERP同步事实；只使用已登记旧表及字段映射，补齐COM数据库/DO/API不一致，不创建另一套ERP Owner。
2. CUT是独立业务模块；不得引入商务合同补录或外部可用性作为通用流程门槛。审批字段按CUT本域事实修复；保留权限、方案版本及真正业务前置，不伪造外部事实。
3. 修正治理套件与现行权威规格、后继映射和真实历史记录的差异，不通过删测试或修改历史批准记录制造通过。
4. 默认单租户ID明确为1；此前租户0仅作历史来源。新增运行默认、种子与迁移目标使用1。既有迁移文件和审计历史不批量改写，来源租户与目标租户区分保留；碰撞或无法证明来源归属时拒绝迁移。

本次裁决是上述有限规格修订的用户授权。先回写正式规格，再落代码与验证，不代表对历史数据生产切换、Flyway repair或所有Feature完成的授权。
