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

## 三分支按提交时间代码事实重放（2026-09-04）

> 状态以提交源码、测试、迁移、前端与构建文件为事实依据；Feature未关闭的Gate继续保留。

- 原实施状态记录：`> Feature实施状态：IN_PROGRESS<br>`
- 当前实施状态：代码已接收；未完成Feature保持 `IN_PROGRESS`。
- 已接收代码路径：`32`
- 已处理来源提交：`23`
- 来源分支：`codex/f-acc-001-sds`、`prereq-parallel-check-kKiAdn`、`codex/f-cut-001-matrices`。
- 接收原则：按提交时间逐提交重放；任何单文件或单hunk冲突均不阻断其他模块代码。
- 完整逐提交、逐文件记录：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。

- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/arrivalacceptance/ArrivalAcceptanceHttpException.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/arrivalacceptance/ArrivalAcceptanceDO.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/arrivalacceptance/ArrivalDifferenceDO.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/arrivalacceptance/DeliveryEvidenceDO.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/arrivalacceptance/DeliveryEvidenceRevisionDO.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalAcceptanceMapper.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalDifferenceMapper.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/ArrivalLineMapper.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/DeliveryEvidenceMapper.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/query/ArrivalPageQuery.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/arrivalacceptance/query/DeliveryEvidencePublishUpdate.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationService.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceCommandService.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/adapter/FileArtifactApiAdapter.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/DeviceScopeFactPort.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/FileArtifactFactPort.java`
- `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/port/OwnerFactVersionMismatchException.java`
- `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalAcceptanceMapper.xml`
- `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/ArrivalLineMapper.xml`
- `pms-module-engineering/src/main/resources/mapper/arrivalacceptance/DeliveryEvidenceMapper.xml`
- `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceApplicationServiceTest.java`
- `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/arrivalacceptance/ArrivalAcceptanceQueryServiceTest.java`
- `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/acceptanceactivity/dto/AcceptanceActivityCompletionCommand.java`
- `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/acceptancereport/AcceptanceReportVersionDO.java`
- `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/acceptancereport/ProjectDeliverableSourceVersionDO.java`
- `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/acceptancereport/AcceptanceActivityMapper.java`
- `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/acceptancereport/AcceptanceReportVersionMapper.java`
- `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/acceptancereport/ProjectDeliverableSourceVersionMapper.java`
- `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/acceptancereport/AcceptanceReportOutboxDeliveryJob.java`
- `pms-module-project/src/main/resources/mapper/acceptancereport/AcceptanceActivityMapper.xml`
- `pms-module-project/src/main/resources/mapper/acceptancereport/AcceptanceReportVersionMapper.xml`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/arrival-acceptance/arrivalAcceptanceInteraction.ts`
