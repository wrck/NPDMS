# F-ACC-002 满意度问卷、达标判定与归档同步

> Feature实施状态：`IN_PROGRESS`<br>
> 总体工程阶段：`IMPLEMENTATION_TASK_2`<br>
> Feature Ready Gate：`PASS / GO`（`145e4a61`）<br>
> Technical Plan Gate：`PASS / GO`（`41f92526`）<br>
> Implementation Done Gate：`PENDING`<br>
> 当前阻断：无<br>
> 当前任务：`Task 2：前端与一次真实Chromium闭环`<br>
> Requirement ID：`ACC-02@V1=FULL`；`ACC-04@V1=PARTIAL_SATISFACTION_SOURCE_ONLY`<br>
> Feature Spec：`specs/features/F-ACC-002-satisfaction-questionnaire-result-and-deliverable-sync.md`<br>
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-acc-002-satisfaction-questionnaire-result-deliverable-sync.md`<br>
> 分支/工作树：`codex/f-acc-001-sds` / `M:\AICoding\CodexData\worktrees\fcom\NPDMS`<br>
> 独占端口：后端`59340`；前端`19340`

## 实施边界

- 实现模板冻结、满意度Task/Questionnaire/Response/Result、现场协助、整改重收、失效、满意度来源归档、历史下载和统一异步导出。
- PLT拥有唯一文件、ExportTask/ExportAudit和归档真值；ACC只提供满意度Owner事实与`ACC/SATISFACTION_RESULT`导出Provider；PROJ继续拥有ProjectTask/WorkBinding/ProjectScope。
- 仅覆盖ACC-04满意度来源切片；不实现其他来源、CLO/SUB消费者、INT连接器或统一批量下载。
- V133是唯一前向迁移；不修改已执行迁移、旧问卷/回访/电子完工证明或Yudao基础平台源码。
- 权限只落实五个最小键及服务端项目/责任人/字段/文件/租户控制点；验收身份通过正式配置取得所需权限。

## Task 1：共享契约、V133与后端纵向闭环

- [x] Step 1：确定聚焦验收边界；按用户要求不执行测试先行。
- [x] Step 2：实现API、DO/Mapper和领域服务最小闭环。
- [x] Step 3：接入PLT、PROJ和Outbox/Quartz。
- [x] Step 4：实现并验证V133。
- [x] Step 5：运行聚焦后端验证和构建。
- [x] Step 6：提交Task 1并更新检查点。

## Task 2：前端与一次真实Chromium闭环

- [ ] Step 1：编写前端失败测试并确认RED。
- [ ] Step 2：实现API与页面最小闭环。
- [ ] Step 3：运行前端聚焦验证和构建。
- [ ] Step 4：准备正式运行环境。
- [ ] Step 5：运行一次真实Chromium纵向验收。
- [ ] Step 6：形成Implementation Done候选。

Task精确文件、命令和验收条件以唯一Technical Plan为准。Task 1未通过不得进入Task 2；两个Task全部完成只允许申请一次Feature Implementation Done裁决。

> 检查点：基线=`5f4054e5`；当前Gate=Task 2 Step 1；已通过=Task 1后端、V133、模板/任务/答卷/Result/失效/归档/导出REST闭环及reactor编译；阻塞=无；下一步=实现前端公开正向链并做一次Chromium验收。
