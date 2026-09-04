# F-ACC-002 满意度问卷、达标判定与归档同步

> Feature实施状态：`IN_PROGRESS`<br>
> 总体工程阶段：`IMPLEMENTATION`<br>
> Feature Ready Gate：`READY / GO`（来源`145e4a61`；master修订011已关闭`Q-GOV-20260901-001`）<br>
> Technical Plan Gate：`PASS / GO`（`41f92526`）<br>
> Implementation Done Gate：`NOT_ESTABLISHED`<br>
> 当前阻断：`代码已选择性集成至master；当前master真实MySQL、Chromium与独立Done裁决未完成；历史分支Done只作证据`<br>
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
- [ ] 在当前master完成真实MySQL与Chromium复验并申请独立Implementation Done裁决。
- [x] 更新Requirement矩阵和DU回执，保持`IN_PROGRESS / NOT_ESTABLISHED`。

> 检查点（2026-09-02）：代码回执=`b3e7c76e`；当前Gate=`IN_PROGRESS / NOT_ESTABLISHED`；已通过=Feature契约20项、27模块依赖构建、后端117项适用测试（8项MySQL跳过）、前端类型检查与生产构建；阻塞=当前master真实MySQL/Chromium与独立Done裁决未完成；下一步=补齐运行证据并申请独立裁决。

## 代码事实按时间逐提交重放回执（2026-09-04）

> 本节仅记录提交代码事实；存在开放Gate时Feature继续保持IN_PROGRESS，代码接收不自动构成Implementation Done。

- 来源提交数：`30`
- 已接收或已确认主干等价路径数：`189`
- 仍需逐路径适配记录数：`55`
- 接收范围：三个来源分支中全部模块的文本代码、测试、构建配置与可安全迁移SQL；无模块级排除。
- 完整提交顺序和逐路径裁决：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。

来源提交：

- `0b832c37d0bf152af72e5a5012e46d39c97a2f4f`
- `0f3769755f59a7a76c0ceec715c67f3af3bf134c`
- `0ffaebe3d1c32733abe9955f2733b6fe7cd02349`
- `120605575c50667d93fa4b39fda200050f9ea19d`
- `1cb0461f3424a2ae7fd573d5dd43d0cd8252fe54`
- `338dcc978825436ac538d0e8c43282094d13d310`
- `486727a3a856fe5de19683f3e7eef1d38b88f6a0`
- `4a84f6f9e6491c621ff69e0b6b00edba8dfb5eda`
- `54ec4e00789bc676d6569de65c99e0f3db82ac70`
- `57b2dcd223b9cf685e2aff648003b54f03e82de8`
- `59c34505e7d3d5bfa49d03c165989f521c7d5c6c`
- `5f4054e56799d3b0009b01e190c5648243e2f68f`
- `6ec1b2459a436f16d9a87bf03358ab98e0af4bfc`
- `8ed75093f6ca63292388075e070fd1c7eb9babf7`
- `98e4ae22a7f2f7dd3056ce674cbadaf6f865eafe`
- `98feb4564a8c6cbdae37c4c340fc92fa6827958b`
- `9f178dc322dbcd7412e6b6570ad7d9a7cc2ff26a`
- `a276347d44fa062ee6ee10d95a8e83e3e3a73d4d`
- `a43ed4c1c7e7ecec2a2923aa3914fda566eb4e67`
- `a55567ce12c91ce086a8923e28e8ba6dd387f415`
- `b7231e73eacd0697b7c597a9ed494a5bbfd407a4`
- `b98d0caafb724a13433aec382bafa30c02d30091`
- `b9c0686a161547a1610b9e48f26023303b8ef784`
- `c1e7354c4738be42d2792b70222f1a369b82583b`
- `d00501f486afe5e3b8c73046eda64439ff496449`
- `d8b847e018bf756ad6c05ff4d8c79bb5d9197026`
- `e2b321e4bdc79c3075f28ff9059b39542e7ee826`
- `e3cc9eed198272c8cc56291d81c73da6ac837835`
- `e83cda3ff05dc97bbaa145871adb8148faee5790`
- `fab9e06f98d446acc138499c14eae908037f3d9c`
