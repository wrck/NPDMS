# F-ACC-002 满意度问卷、达标判定与归档同步

> Feature实施状态：`IN_PROGRESS`<br>
> 总体工程阶段：`IMPLEMENTATION`<br>
> Feature Ready Gate：`SOURCE PASS / MASTER BLOCKED_BY_SPEC`（来源`145e4a61`；当前`Q-GOV-20260901-001`）<br>
> Technical Plan Gate：`PASS / GO`（`41f92526`）<br>
> Implementation Done Gate：`NOT_ESTABLISHED / BLOCKED_BY_SPEC`<br>
> 当前阻断：`Q-GOV-20260901-001 / BLOCKED_BY_SPEC；代码已选择性集成至master；当前master真实MySQL、Chromium与独立Done裁决未完成；历史分支Done只作证据`<br>
> 当前任务：`master@b3e7c76e代码回执后的运行复验与独立裁决`<br>
> Requirement ID：`ACC-02@V1=FULL`；`ACC-04@V1=PARTIAL_SATISFACTION_SOURCE_ONLY`<br>
> Feature Spec：`specs/features/F-ACC-002-satisfaction-questionnaire-result-and-deliverable-sync.md`<br>
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-acc-002-satisfaction-questionnaire-result-deliverable-sync.md`<br>
> 分支/工作树：`master` / `M:\AICoding\CodexData\worktrees\master-governance\NPDMS`<br>
> 独占端口：后端`59340`；前端`19340`

## 实施边界

- 实现模板冻结、满意度Task/Questionnaire/Response/Result、现场协助、整改重收、失效、满意度来源归档、历史下载和统一异步导出。
- PLT拥有唯一文件、ExportTask/ExportAudit和归档真值；ACC只提供满意度Owner事实与`ACC/SATISFACTION_RESULT`导出Provider；PROJ继续拥有ProjectTask/WorkBinding/ProjectScope。
- 仅覆盖ACC-04满意度来源切片；不实现其他来源、CLO/SUB消费者、INT连接器或统一批量下载。
- master以V171承载Feature结构；V172仅恢复被历史菜单ID冲突覆盖的既有任务指派权限载体；不修改已执行迁移、旧问卷/回访/电子完工证明或Yudao基础平台源码。
- 权限只落实五个最小键及服务端项目/责任人/字段/文件/租户控制点；验收身份通过正式配置取得所需权限。

## Task 1：共享契约、V133与后端纵向闭环

- [x] Step 1：确定聚焦验收边界；按用户要求不执行测试先行。
- [x] Step 2：实现API、DO/Mapper和领域服务最小闭环。
- [x] Step 3：接入PLT、PROJ和Outbox/Quartz。
- [x] Step 4：来源分支实现并验证V133；master选择性集成时重排为V171。
- [x] Step 5：运行聚焦后端验证和构建。
- [x] Step 6：提交Task 1并更新检查点。

## Task 2：前端与一次真实Chromium闭环

- [x] Step 1：按收益优先口径不执行前端测试先行，只锁定用户可见的正向闭环。
- [x] Step 2：实现API与页面最小闭环。
- [x] Step 3：运行前端聚焦静态检查和`build:local`。
- [x] Step 4：准备正式运行环境。
- [x] Step 5：运行一次真实Chromium纵向验收。
- [x] Step 6：形成Implementation Done整改候选。

Task精确文件、命令和验收条件以唯一Technical Plan为准。Task 1未通过不得进入Task 2；两个Task全部完成只允许申请一次Feature Implementation Done裁决。

## Task 3：master选择性集成与复验

- [x] 在F-ACC-001基础上集成满意度、归档和统一异步导出，不接收旧源推断或外部连接器；迁移收敛为V171～V172。
- [x] 在当前master复核迁移、权限、幂等/并发控制、后端、前端类型和生产构建。
- [ ] 在当前master完成真实MySQL与Chromium复验并申请独立Implementation Done裁决；`Q-GOV-20260901-001`关闭前不得晋级。
- [x] 更新Requirement矩阵和DU回执，保持`IN_PROGRESS / BLOCKED_BY_SPEC`。

> 检查点（2026-09-02）：代码回执=`b3e7c76e`；当前Gate=`IN_PROGRESS / BLOCKED_BY_SPEC`；已通过=Feature契约20项、27模块依赖构建、后端117项适用测试（8项MySQL跳过）、前端类型检查与生产构建；阻塞=`Q-GOV-20260901-001`、当前master真实MySQL/Chromium与独立Done裁决未完成；下一步=规格阻断关闭后补齐运行证据并申请独立裁决。
