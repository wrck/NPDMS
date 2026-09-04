# F-ACC-001 初验/终验报告版本与交付件同步

> Feature实施状态：`IN_PROGRESS`<br>
> 总体工程阶段：`IMPLEMENTATION`<br>
> Feature Ready Gate：`READY / GO`（来源`bde0feac`；master修订011已关闭`Q-GOV-20260901-001`）<br>
> Technical Plan Gate：`PASS / GO`（`fca9626c`）<br>
> Implementation Done Gate：`NOT_ESTABLISHED`<br>
> 当前阻断：`代码已选择性集成至master；当前master真实MySQL、Chromium与独立Done裁决未完成；历史分支Done只作证据`<br>
> 当前任务：`master@e53f7243代码回执后的运行复验与独立裁决`<br>
> Requirement ID：`ACC-03@V1=FULL`；`ACC-04@V1=PARTIAL`<br>
> Feature Spec：`specs/features/F-ACC-001-acceptance-report-version-and-deliverable-sync.md`<br>
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-acc-001-acceptance-report-version-deliverable-sync.md`<br>
> 分支/工作树：`master` / `M:\AICoding\CodexData\worktrees\master-governance\NPDMS`

## 实施边界

- 实现初验/终验活动、草稿与不可变报告版本、终验守卫、PROJ任务同事务完成、既有应交根来源同步、PLT独立归档补偿和历史附件下载。
- 仅覆盖`ACC-04`的初验/终验报告来源切片；不实现其余来源、CLO业务、统一批量下载或Q-FCOM-002退出/回退规则。
- 不改写V17/V63历史数据；V17单行验收栈已标记废弃且禁止继续实施，V166～V170只作前向受管验收事实、调度和正式页面权限纠偏；第三方归档平台只保留接口，不实现连接器。
- 只冻结最小权限键和服务端控制点；正式验收身份通过授权配置取得全部相关权限，不固定角色模板、不删除鉴权或租户隔离。
- 本Worktree独占后端端口`59330`、前端端口`19330`；MySQL`23316`、Redis`26379`共享仓库基础设施。

## 来源候选Task 1：共享契约、前向迁移与后端正向闭环

- [x] Step 1：写聚焦失败测试并确认RED。
- [x] Step 2：实现PLT加性文件契约。
- [x] Step 3：实现ACC活动、报告和应交来源。
- [x] Step 4：接入PROJ创建与任务完成。
- [x] Step 5：来源分支实现并验证V128；master选择性集成时重排为V166。
- [x] Step 6：运行Task 1聚焦集合并提交。

## Task 2：公开UI、真实验收与Implementation Done候选

- [x] Step 1：以前端失败测试锁定页面行为。
- [x] Step 2：实现新API客户端和页面。
- [x] Step 3：运行目标前端验证。
- [x] Step 4：运行真实MySQL与F-COM直接回归。
- [x] Step 5：使用本分支独占端口完成一次Chromium闭环。
- [x] Step 6：规格检查、证据、提交并送Implementation Done独立审核。

Task精确文件、命令和验收条件以唯一Technical Plan为准。Task局部完成不得宣称Feature或Requirement完成；两个Task全部完成并通过验证后，只申请一次Feature Implementation Done裁决。

## Task 3：master选择性集成与复验

- [x] 排除COM-A祖先、来源工作树脏改动和旧Flyway编号，选择性集成全部F-ACC-001能力；迁移收敛为V166～V170。
- [x] 在当前master复核公共契约、旧功能废弃边界、迁移、权限、后端、前端类型和聚焦测试。
- [ ] 在当前master完成真实MySQL与Chromium复验并申请独立Implementation Done裁决。
- [x] 更新Requirement矩阵和DU回执，保持`IN_PROGRESS / NOT_ESTABLISHED`。

> 检查点（2026-09-02）：代码回执=`e53f7243`；当前Gate=`IN_PROGRESS / NOT_ESTABLISHED`；已通过=Feature契约12项、27模块依赖构建、后端44项适用测试（4项MySQL跳过）、前端3项测试与类型检查；阻塞=当前master真实MySQL/Chromium与独立Done裁决未完成；下一步=补齐运行证据并申请独立裁决。

## 代码事实按时间逐提交重放回执（2026-09-04）

> 本节仅记录提交代码事实；存在开放Gate时Feature继续保持IN_PROGRESS，代码接收不自动构成Implementation Done。

- 来源提交数：`40`
- 已接收或已确认主干等价路径数：`181`
- 仍需逐路径适配记录数：`46`
- 接收范围：三个来源分支中全部模块的文本代码、测试、构建配置与可安全迁移SQL；无模块级排除。
- 完整提交顺序和逐路径裁决：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。

来源提交：

- `0b1671cb93a81a6a5c9dbee774b2d32a7950a4c5`
- `1a9ca704422b275be9d19629d2d61af1782138c4`
- `1df9b3922a5c0a0ad5150cc5e33c14c7585c564c`
- `20bca44b9aa1a4c083685673cfe8536308aca9c9`
- `20f03ba316ca431a55f96aa9c3c97be54d08b4e0`
- `21c07181b6667f47e18a81ca3dee2114ab3d5074`
- `229e9f4b946c3933405b43ca2cd11e519d7d921c`
- `26531772ea6fe59befdbda4461fdb242c3c807a4`
- `293293c5cc187bdf2405b9638406e0f97f4c7cb2`
- `2cf427d6ccb6e0cef0cef3b1460eeaa95ddced53`
- `32092b115a32262070d62b60bb3d429da3e496c6`
- `338dcc978825436ac538d0e8c43282094d13d310`
- `369c92bd21e9c14b94fc653c08d9070e535dce22`
- `3a27eeffb1f081a0a70842b6326b66d90b9c95cf`
- `3b9e680a0ede81dd20c1075d8c0ad7982afc5073`
- `41a71649420edb7034b31b503eff8a6906c4d08d`
- `42c20d8707bf43d6837826d861ee8347db4dedea`
- `4ecc9d3bb3677ab7a7cbcd867b7ec29418479985`
- `505aaf78b749d86190df72440c4f0ddfca7c9cda`
- `553dbec0fdf612534153810e1f7015c4fa5493df`
- `5c1e1ff2498abf838310da607ae5d1426953b3ad`
- `5f4054e56799d3b0009b01e190c5648243e2f68f`
- `6490d44c035b74ec5b9e06377c2b32a1619a69ae`
- `6bd13e416a4c914e08851cf40f2a161daa0b9f6a`
- `700b659d7e11e6fbcbe88c05b02269a35b82084f`
- `88c322fa2306a88c88e00903a85a9acfd51897fa`
- `8d582aea1aa4e226fe483c58c83b70995c4801e4`
- `98e4ae22a7f2f7dd3056ce674cbadaf6f865eafe`
- `9f178dc322dbcd7412e6b6570ad7d9a7cc2ff26a`
- `a43ed4c1c7e7ecec2a2923aa3914fda566eb4e67`
- `ad5b401f0a0ff378bda7b03a5268437d3462f3ce`
- `afa37d66eb3478c8a915a6dbe723723d9ca249b8`
- `b17ae89f92b01488378aeb8c36a77a5b2d46ad29`
- `b9c0686a161547a1610b9e48f26023303b8ef784`
- `ba8a4def8583ee29da3652472eae3f0660c4ad9f`
- `c541126b644ff28d72ad8735534a6b63f859c729`
- `c714c38330f70d7fb77c72be51b885d385d482b2`
- `d67112d7b9b9a1ffd84faee3acccbc0ef30faa12`
- `e31f08b31fe20ee9bf80b2b65d4495a6d4410940`
- `fec7c69e3892115c8a402d78f223462c3ca81fa4`

## 代码事实时间序重放检查点（2026-09-04）

> 依据三个来源分支的实际提交代码逐项记录；代码接收不自动构成 Implementation Done。

- 来源分支：`codex/f-acc-001-sds`
- 代码事实记录：`99` 个提交-路径组合
- 重放顺序：全局提交时间、来源稳定顺序、分支拓扑顺序。
- 接收范围：全部模块；冲突只保留到具体文件或 hunk，不形成整提交、整模块或整分支拒绝。
- 详细清单：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv` 与稳定化报告。
