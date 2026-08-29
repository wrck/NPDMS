# F-ACC-001 初验/终验报告版本与交付件同步

> Feature实施状态：`IN_PROGRESS`<br>
> 总体工程阶段：`IMPLEMENTATION`<br>
> Feature Ready Gate：`PASS / GO`（`bde0feac`）<br>
> Technical Plan Gate：`PASS / GO`（`fca9626c`）<br>
> Implementation Done Gate：`NOT_SUBMITTED`<br>
> 当前阻断：`无；待Implementation Done整改复审`<br>
> 当前任务：`Task 2 Step 6：证据、提交与整改复审`<br>
> Requirement ID：`ACC-03@V1=FULL`；`ACC-04@V1=PARTIAL`<br>
> Feature Spec：`specs/features/F-ACC-001-acceptance-report-version-and-deliverable-sync.md`<br>
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-acc-001-acceptance-report-version-deliverable-sync.md`<br>
> 分支/工作树：`codex/f-acc-001-sds` / `M:\AICoding\CodexData\worktrees\fcom\NPDMS`

## 实施边界

- 实现初验/终验活动、草稿与不可变报告版本、终验守卫、PROJ任务同事务完成、既有应交根来源同步、PLT独立归档补偿和历史附件下载。
- 仅覆盖`ACC-04`的初验/终验报告来源切片；不实现其余来源、CLO业务、统一批量下载或Q-FCOM-002退出/回退规则。
- 不修改V17/V63/V124～V128和Yudao基础平台；V129～V132仅作前向受管验收事实、调度和正式页面权限纠偏；第三方归档平台只保留接口，不实现连接器。
- 只冻结最小权限键和服务端控制点；正式验收身份通过授权配置取得全部相关权限，不固定角色模板、不删除鉴权或租户隔离。
- 本Worktree独占后端端口`59330`、前端端口`19330`；MySQL`23316`、Redis`26379`共享仓库基础设施。

## Task 1：共享契约、V128/V129/V130/V131与后端正向闭环

- [x] Step 1：写聚焦失败测试并确认RED。
- [x] Step 2：实现PLT加性文件契约。
- [x] Step 3：实现ACC活动、报告和应交来源。
- [x] Step 4：接入PROJ创建与任务完成。
- [x] Step 5：实现并验证V128。
- [x] Step 6：运行Task 1聚焦集合并提交。

## Task 2：公开UI、真实验收与Implementation Done候选

- [x] Step 1：以前端失败测试锁定页面行为。
- [x] Step 2：实现新API客户端和页面。
- [x] Step 3：运行目标前端验证。
- [x] Step 4：运行真实MySQL与F-COM直接回归。
- [x] Step 5：使用本分支独占端口完成一次Chromium闭环。
- [x] Step 6：规格检查、证据、提交并送Implementation Done独立审核。

Task精确文件、命令和验收条件以唯一Technical Plan为准。Task局部完成不得宣称Feature或Requirement完成；两个Task全部完成并通过验证后，只申请一次Feature Implementation Done裁决。

> 检查点：基线=`3a27eeff`；当前Gate=Implementation Done整改复审；已通过=SUPERSEDED补偿、V132权限、单脚本Chromium与3来源归档/4事件投递；阻塞=无；下一步=提交候选并复审。
