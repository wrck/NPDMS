# 复制项目全流程模板 · 浏览器创建到闭环验证记录（2026-09-26）

## 目标与约束

- 目标：复制"项目全流程"流程图模板（rev10）修复配置后，在真实浏览器中完成项目创建到闭环（S0~S6）的全流程。
- 约束（用户指令）：不得使用脚本推进流程；所有界面操作必须在项目详情内；任务工作区配置的操作界面不得跳出任务办理区。
- 环境：docker compose `npdms-domain-fresh`（MySQL 25306 / Redis 25379，库 `npdms_domain_test`），后端 `127.0.0.1:59391`（JDK 25 jar），前端 `127.0.0.1:19391`。
- 项目：`PJT2026000001`（id 992004000002，流程图模板闭环验证项目，模板 #993009900011 v1，直签/原厂直服）。

## 结果总览

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| S0 项目立项与指派 | DONE | 服务经理+项目经理指派后自动完成 |
| S1 工前准备 | DONE | 工勘/需求分析/工期/联络 4 任务，D_END_USER、D_INTERFACE 等交付件提交验收 |
| S2 施工计划制定与审批 | DONE | 阶段施工计划审批（pms-sol-stage-plan）通过 + D_CONSTRUCTION_PLAN 验收 |
| S3 实施方案编制与审核 | DONE | 过程中定位并绕过一处表单缺陷（见"缺陷观察"），V2 方案走完审核形成基线 |
| S4 实施部署 | ACTIVE（5/6 任务 DONE） | 协调/到货/硬件/配置/联调 5 任务完成；**割接上线 DEPLOY_CUTOVER 阻塞**（见下） |
| S5 验收交维 | PENDING | 依赖 S4_COMPLETED 门禁，无法进入 |
| S6 闭环 | 未配置 | rev10 模板未配置 S6（Q-TPL-FLOW-20260922-002） |

终态任务清单：17 任务中 10 个 DONE，DEPLOY_CUTOVER 进行中 0%，S5 五任务 PENDING_ASSIGN。截图 `browser/final-task-list.png`。

## S4 阻塞点：割接过渡审批无界面发起入口（Q-TPL-FLOW-20260922-004）

`DEPLOY_CUTOVER` 为 APPROVAL 绑定（`PMS_DELIVERY_CUTOVER_APPROVAL`，冻结定义 `abd5acb7-b952-11f1-9554-00ff0b78abd7`）。在"仅界面操作、不脚本推进"约束下逐一路径核实，均无法发起：

1. **任务办理抽屉**：审批面板对 formType=20（业务表单）定义按设计拒绝渲染发起表单（`TaskApprovalForm.load` 拦截，提示"该审批使用原模块业务表单，请通过业务页面绑定办理；不能以空表单替代"，自 9141716e2 起存在）。截图 `browser/s4-cutover-drawer-blocked.png`。
2. **业务页面绑定**：rev10 模板该任务仅 APPROVAL 绑定、无业务视图快照（工作台 `businessView=null`）；项目详情内"割接上线 5.5"视图渲染 taskv2 界面，但其 REST 生产 Bean 按契约测试刻意未注册（"正式Owner齐备前不注册生产Bean"），实测 `GET/POST /admin-api/api/v1/pms/cutover-tasks*` 全部 404（`{"code":404,"msg":"请求地址不存在"}`）。截图 `browser/s4-cutover-view.png`。
3. **旧版割接模块**：`POST /admin-api/pms/cut-task/create` 已退役（写退役表、不产生新域事实），按废弃路径约束不得承接新流程。
4. **BPM 手工发起**：流程 BPMN 无启动表单（start→割接审批 userTask(candidateStrategy=35)→end），人工发起无法携带 `pmsTaskProjectId/pmsTaskId/pmsTaskExecutionId/pmsTaskContractId` 身份变量，`PmsNodeApprovalProcessOwner.instanceFact` 必然 IDENTITY_MISMATCH→UNKNOWN，反而使任务进入 UNKNOWN 循环，不能作为完成依据。

该边界即已登记的 [Q-TPL-FLOW-20260922-004](../../decisions/open-questions.md)（BLOCKED_BY_SPEC，决策人：需求方）。本次未采用脚本/API 直推审批（2026-09-24 验收 028/032 即经此途径走通），并在 Q-004 增补了 2026-09-26 续记。

## 处置路径（供需求方裁决）

- A. 按 Feature/Task 流程授权注册 taskv2 REST 生产入口（Q-004 推荐技术默认），之后割接审批可从"割接上线 5.5"业务页面发起。
- B. 修订模板：为 DEPLOY_CUTOVER 配置界面可发起的过渡完成依据（如改用流程表单 NORMAL 类型定义或 PAGE 绑定）。
- C. 维持现状并披露：S4→S5 为已知阻塞点（本次记录即此形态）。

## 缺陷观察（2026-09-27 专项任务修复，已浏览器复验）

1. **方案类型自由文本**：~~已修复~~ `SolutionChapterForm.vue`/`SolutionReviewChapterForm.vue` 的"方案类型"改为单选下拉（仅 `IMPLEMENTATION`，任务完成链 `SolutionCompletionMapper`/`SolutionReviewMapper` 固定值；扩充值域须先经规格裁决），新增表单默认 `IMPLEMENTATION`，`SolutionSaveReqVO` 增加 `@Pattern(regexp="IMPLEMENTATION")` 服务端校验（实测非法值返回 400"方案类型仅支持 IMPLEMENTATION"）。证据 `defect-fix/fix1-solution-type-*.png`。遗留：库中 1 条历史脏数据（id 30009 solution_type='实施方案'，本次误输入产物）保留作缺陷证据，只读场景禁用下拉回显原始码（已实测，`defect-fix/fix1-legacy-badrow-readonly.png`）；SERVICE/MIGRATION 历史行同样以原始码展示，且 update 路径同受 `@Valid @Pattern` 约束——历史行不改类型直接保存会被 400 拦截，须重选方案类型后方可保存。
2. **到货签收"签收人"下拉污染**：~~已修复~~ 选项由全量用户表收敛为项目 CURRENT 成员（`getMemberPage`，同项目成员页数据源），按 userId 去重；历史记录签收人不在当前成员时按值反查系统用户回显（如 userId=1 → 管理员）。设备测试账号（FCUS001/FAST001 等）不再混入。证据 `defect-fix/fix2-signer-*.png`。"关联设备"为可选字段，项目无设备时下拉为空属预期，未改。
3. **割接任务列表加载失败提示**：~~已修复~~ 文案改为与真实原因一致的"割接任务服务尚未接入（服务端点未注册，待割接业务 Owner 正式接入后开放），任务列表无法加载；这不代表项目没有割接任务。"证据 `defect-fix/fix3-cutover-banner.png`。

## 过程要点（界面驱动）

- S3 恢复：任务办理抽屉内新建 V2 实施方案（方案类型=IMPLEMENTATION）→ 提交审核 → 开始审核 → 服务经理通过（附审批意见）→ 自动关联业务对象 30010 → 刷新业务结果 → PLAN_SOLUTION DONE（11:53:31），S3 DONE、S4 门禁开启。
- S4 五任务：DEPLOY_COORDINATE 提交任务→校验并完成；DEPLOY_ARRIVAL 经内嵌"登记到货"（数量 6、2026-09-26 10:30、PDF 签收单、已签收）+ D_ARRIVAL 上传绑定提交（已满足）；DEPLOY_HARDWARE/CONFIG/JOINT 分别上传 D_INSTALL/D_CONFIGURATION/D_JOINT（`uploads/PJT2026000001-{install,config,joint}-record.pdf`）并提交验收→校验并完成；准入链（ARRIVAL←直签，HARDWARE←ARRIVAL，CONFIG←HARDWARE，JOINT←CONFIG，CUTOVER←JOINT）逐级自动激活，全部在任务办理抽屉内完成，未跳离。
- 交付件为"上传并绑定（V1 PROJECT_DELIVERABLE_DOCUMENT，版本历史/预览/下载）→ 提交材料（已满足）"两段式；文件经页面内 DataTransfer 设置后由界面按钮上传。

## 证据索引

- 浏览器截图：`browser/`（创建向导 01~12、S1/S3/S4 各环节、割接阻塞 `s4-cutover-drawer-blocked.png`、终态 `final-task-list.png`）。
- 缺陷修复复验截图：`defect-fix/`（方案类型下拉、签收人成员收敛与去重、割接 5.5 新文案）。
- 上传材料：`uploads/`（各阶段提交的 PDF/MD）。
- 模板快照：`snap.json`（含 DEPLOY_CUTOVER 绑定与完成规则 CONSTANT true 的过渡说明）。
- 修复记录：本次会话修复交付件提交乐观锁缺陷（11 处）、文件存储主配置（baidu 假配置→DB 存储）、客户模块编译断链（Integer→Long），均随重建部署后经浏览器复验。
- 关联登记：Q-TPL-FLOW-20260922-004（2026-09-26 续记）。
