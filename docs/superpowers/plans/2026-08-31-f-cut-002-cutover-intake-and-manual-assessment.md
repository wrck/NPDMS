# F-CUT-002 割接任务接入与人工分级 Implementation Plan

> 计划 ID：`NPDMS-FCUT002-TECHPLAN-20260831-01`
> Technical Plan Gate：`REVIEW_REQUIRED`（最新有效裁决为NO-GO；`1875bb89`整体PASS回写无完整授权）
> Feature Ready：`PASS / cad8088a`
> Feature Spec：`specs/features/F-CUT-002-cutover-intake-and-manual-assessment.md`
> Physical Contract：`specs/features/F-CUT-002-physical-contract.json`
> REST Contract：`specs/features/F-CUT-002-rest-api-contract.json`

**Goal：** 一次交付“一线工程师按设备 SN 解析有权项目与权威上下文 → 自建唯一割接任务进入 P2 → 暂存并人工提交四项评估 → A/B/C 进入 P3、D 进入 P4”的最小完整业务闭环。

**Architecture：** CUT新建`cut_task`聚合及`cut_assessment`版本事实，保留旧`pms_cut_*`实现不变。CUT通过PROJ `ProjectScopeApi`取得用户范围，通过新增`ProjectCutoverContextFactApi`取得同一ProjectMaster版本下的项目/客户/部门发生时快照，再通过AST、CUS、IMP、PLT公共API取得其他Owner事实；写命令由`PlatformCommandExecutionApi`与CUT事务同成同败。生产Provider未到位前不注册Fake、不声明真实浏览器或Implementation Done。

**执行原则：** 先完成当前 Task 的全部正向能力，再执行实现完成后的单元测试和正向流程闭环；本计划不安排负向测试、不提前测试未实现能力，也不扩建与正向闭环无关的检验。本 Feature 只形成一个 Implementation Done 候选，中途不拆子 Gate、不反复提交审核。

## 1. 实施边界

- 覆盖 `CUT-01@V1=PARTIAL`、`CUT-02@V1=PARTIAL`，只推进 P1 接入、P2 人工分级及 P3/P4 入口。
- 实现用户 REST、自建命令、列表/详情、评估暂存/提交和内部 `CutoverTaskIntakeApi` Provider；不实现 ITR 连接器、项目事件 Producer 或第三方 HTTP。
- 不实现 P3 采集、P4 方案、P5/P6、自动判级、指派、取消、暂停、转派或 SLA。
- 不修改旧 `CutTaskController/CutTaskService/pms_cut_task`、旧 `cut-task` 页面及其菜单；新路径使用 `/api/v1/pms/cutover-tasks` 和 `pms:cutover-task:*`。
- 不实现 IMP、AST、CUS、PROJ Owner；不直接访问这些 Context 的 Service、Mapper、DO 或业务表。
- 不修改 Yudao 基础模块。Flyway 只用实施合入时的下一连续未占用版本，本文不预约 V146。

## 2. 模块与文件责任

| 责任 | 主要位置 | 处理 |
|---|---|---|
| CUT 公共入向契约 | 新建 `pms-module-cutover-api`，并同步根 `pom.xml`、`pms-module-cutover/pom.xml` | 放置 `CutoverTaskIntakeApi`、严格判别 Command/Result；不放 Producer、HTTP 或 Owner 实现 |
| CUT 聚合与命令 | `pms-module-cutover/src/main/java/.../taskv2/` | 新增领域规则、应用服务、Owner 消费端口、事实编排和错误分类；不复用旧任务状态机 |
| CUT 持久化 | `.../dal/dataobject/taskv2/`、`.../dal/mysql/taskv2/`、`src/main/resources/mapper/taskv2/` | 四张新表各自 DO/Mapper；联表、锁定、集合和 CAS 使用场景化 Query + XML |
| 用户 REST | `.../controller/admin/taskv2/` | 精确实现已锁定六条 REST、Header、Wire Long/时间和四权限；Controller 不拼业务状态 |
| 前向迁移 | `sql/migrations/V{next}__fcut002_*.sql`、`.../service/taskv2/migration/` | DDL/菜单与 CURRENT_FORWARD 批次消费分离；旧表只读，PLT 持有迁移证据 |
| 新工作台 | `yudao-ui/.../src/api/pms/cutover/cutover-task/`、`src/views/pms/cutover/cutover-task/` | 新建任务首页、创建向导、详情工作台和 P2 评估；不改旧 `cut-task` 页面 |
| 聚焦验证 | CUT 后端测试目录、前端同目录测试、迁移与 Chromium 脚本 | 实现后只验证成功Schema/Mapper绑定、成功状态迁移、正向幂等重放、正向迁移和P1→P2→P3/P4；不安排负向矩阵 |

`pms-module-cutover-api`包含真实稳定接口和 DTO，不是空模块；当前 Feature 只装配 CUT Provider。精确文件固定为根`pom.xml`新增模块、`pms-module-cutover-api/pom.xml`、`pms-module-cutover-api/src/main/java/cn/iocoder/yudao/module/pms/cutover/api/task/CutoverTaskIntakeApi.java`及同包`dto/CutoverTaskIntakeCommand.java`、`dto/CutoverTaskIntakeResult.java`；`pms-module-cutover/pom.xml`依赖该API并在业务模块实现Provider。ITR/PROJ Producer在其未来Feature中只依赖`pms-module-cutover-api`，本计划不提前实现Producer。

## 3. 核心实现决策

### 3.1 Owner 事实与锁序

只读解析顺序固定为 AST 设备事实 → PROJ 项目范围/上下文 → CUS 服务等级 → IMP 就绪事实；返回所有有权候选，不由服务端替用户选择项目。

写命令先以不加写锁的公开inspect和CUT只读投影取得稳定身份键：受信tenant/actor、明确projectId、规范化SN、稳定deviceId升序、taskId及currentAssessmentId；该步骤只构造已冻结期望，不产生业务写。随后所有自建、评估提交、内部接入和未来CUT继续命令统一执行以下锁序：

1. `ProjectScopeApi.lockAndRevalidate`锁定明确项目及期望`projectScopeVersion`；
2. `ProjectCutoverContextFactApi.lockAndRevalidate`锁定同一项目主档行及期望`projectVersion`；
3. `DeviceScopeFactApi.lockAndRevalidate`按稳定deviceId顺序锁定精确设备/归属版本；
4. `CustomerServiceLevelFactApi.lockAndRevalidate`重验完整`AVAILABLE|NOT_CONFIGURED`联合事实；
5. `ImplementationReadinessApi.lockAndRevalidate`重验精确snapshotId/version与设备水位；
6. CUT先按deviceId升序锁活动设备关系，再锁taskId任务根，最后锁currentAssessmentId当前评估；创建时锁同项目/设备的现有活动关系后插入新根，提交时从不可变设备范围只读投影取得deviceId后按同序加锁。

全部Owner和CUT锁取得后，命令再次比较tenant、project、device集合、task/assessment身份及期望版本，只有完整一致才写入。任何服务不得把task/assessment锁提前到Owner锁或device锁之前，也不得在不同入口改变上述顺序。

自建只接受 READY；客户等级 `NOT_CONFIGURED` 可创建和保存草稿，但不能提交。任一 Provider 未知、异常、身份或版本不一致时，CUT 四表及平台成功事实均零写入。

`ProjectScopeApi`只提供用户范围和树版本。PROJ另以`ProjectCutoverContextFactApi.inspect(tenantId,projectId)`返回同一项目主档版本下的项目编码/名称、发生时客户和`departmentId/departmentCode/departmentName`；CUT只在展示层将部门标注为办事处。写命令携带此前Fact的`expectedProjectVersion`，并在ProjectScope锁后调用`lockAndRevalidate(tenantId,projectId,expectedProjectVersion)`以`MANDATORY`锁定同一项目行；`projectVersion`与`treeVersion`独立。精确DTO、结果联合和失败见机器合同，CUT不得从PROJ/SYSTEM/CUS表或Summary拼接。

### 3.2 状态、评估与事务

- 自建同事务写 `cut_task`、设备范围、`P1_ACCEPTED` 历史和平台成功事实，服务端生成 taskNo，并直接形成 `P2/GRADE_CONFIRMING`。
- 首次暂存创建 `CUT_P2_MANUAL_ASSESSMENT@1` 的 revision 1 DRAFT；后续只在当前 DRAFT 上按 `Assessment-If-Match` 更新四项答案、人工等级及服务端刷新上下文，不推进任务。
- 提交先按3.1唯一顺序锁定重验四个Owner，再按deviceId→taskId→currentAssessmentId取得CUT锁；答案与等级完整后将评估置SUBMITTED，并原子更新任务和阶段历史：A/B/C → `P3/SURVEYING`，D → `P4/PLAN_DRAFTING`。
- 已提交评估、阶段历史和旧来源事实不可覆盖。后续失效入口只保留领域服务能力供未来 CUT 继续命令调用；本 Feature 不新增外部失效 REST。
- 自建和评估提交分别使用合同冻结的幂等作用域；同键同摘要重放已完成结果，异摘要或处理中返回稳定冲突。

### 3.3 查询、权限与错误

- Controller 使用四个最小权限键；Service 再以 `ProjectScopeApi`、设备范围、任务 Owner 和状态做服务端控制，不按角色名判断。
- 列表只查询 `resolveAllCurrent(PROJECT_VIEW)` 返回的项目集合，空集合返回空页；详情使用只读 Owner inspect 计算 `allowedActions`，不取写锁、不触发状态变化。
- 新增场景化错误响应适配，把契约中的 validation/permission/scope/state/version/readiness/customer/provider 分类映射为 400/403/404/409/422/503；不修改 Yudao 全局异常处理。
- 新平台任务按锁定字段完整返回；`LEGACY_FORWARD`只有只读身份投影，动作数组为空，不补造项目、设备、客户、IMP 或阶段事实。

### 3.4 物理与前向迁移

- Flyway 新建 `cut_task`、`cut_task_device_scope`、`cut_task_stage_history`、`cut_assessment` 的全部锁定列、检查、索引和唯一键；新增新菜单及四个权限按钮，不覆盖旧菜单或固定角色授权。
- `cut_assessment.current_marker`只标记当前版本；活动设备唯一标记只约束 NEW_PLATFORM 活动任务。
- 旧数据转换不在 Flyway 中绕过 PLT 证据 Owner。CUT 提供一次性 `CutoverTaskLegacyMigrationService`：只领取 `STAGED_READY` 的指定 `pms_cut_task` 批次，逐行按 `FCUT002_PMS_CUT_TASK_TO_CUT_TASK_V1` 分类，合格行写 `LEGACY_FORWARD/LEGACY_UNKNOWN` 只读目标并追加 mapping，不合格行只追加 issue；计数对账成功后完成批次。该服务无用户 REST、无启动时自动扫描、无双写。
- 转换服务由正式迁移编排显式调用；批次 claim、目标写、mapping/issue 和 reconciliation 加入同一事务。暂时失败整体回到 STAGED_READY，已完成批次只作幂等核对。
- 迁移查询固定使用`pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/taskv2/migration/LegacyCutoverReconciliationMapper.java`及同目录`query/LegacyCutoverReconciliationQuery.java`；XML固定为`pms-module-cutover/src/main/resources/mapper/taskv2/LegacyCutoverReconciliationMapper.xml`，其`namespace`必须等于该Mapper接口全限定名。接口只接收场景Query，游标、动态集合与锁查询全部在该XML实现。

## 4. Task 1：后端、数据与正向业务链

**Produces：** 可编译、可由受控测试装配执行的完整 CUT 自建与 P2 提交内核；生产代码不含 Fake，未具备的 ProjectContext Provider 不以跨表读取替代。

- [ ] 在`ProjectCutoverContextFactApi`公共合同独立GO后，由PROJ在既有API模块/业务模块实现唯一Provider；建立`pms-module-cutover-api`和`CutoverTaskIntakeApi` DTO/Provider，补CUT对platform/project/asset/customer/engineering公共API的单向依赖。
- [ ] 落四张新表的 DO、场景化 Query、Mapper/XML、状态规则和聚合应用服务；使用数据库唯一键与 CAS 保证来源、活动设备、当前评估和版本唯一。
- [ ] 实现 resolve-create-context、list、create、detail、save-assessment、submit-assessment 的应用服务与严格 Wire/Header/错误模型；Controller 只在 Task 2 生产 Owner 接通后注册。
- [ ] 实现同一自建编排供 SELF_CREATED 与内部 ITR/PROJECT_EVENT Provider 复用；内部来源只接受受信 engineer/source identity，不增加 Producer。
- [ ] SELF_CREATED、ITR/PROJECT_EVENT与评估提交全部调用3.1同一锁序组件；禁止Controller、内部Provider或评估Service各自重排Owner/CUT锁。
- [ ] 增加下一连续 Flyway：四表、索引/约束、新菜单与四权限；实现 CURRENT_FORWARD 批次消费服务，保留旧表和旧页面。
- [ ] 全部正向实现完成后再补聚焦后端验证：成功Schema/Mapper绑定、READY自建进入P2、草稿刷新、A/B/C进入P3、D进入P4、同键同载荷重放，以及合格旧行正向转换为只读投影。只运行CUT相关reactor构建及这些正向测试。

Task 1 结束时仍不申请独立 Gate、不回写 Feature 完成；进入 Task 2 继续接通正式页面。

## 5. Task 2：新工作台与一次正向验收

**Produces：** 生产 Owner 接通后，用户可从新页面完成自建和人工分级；形成一个 F-CUT-002 Implementation Done 候选。

- [ ] 核验IMP/AST/CUS Provider及`ProjectCutoverContextFactApi`真实PROJ Provider已合入；实现只调用正式API的生产Adapter，注册唯一应用服务和Controller。缺任一依赖即停止真实运行，不加fallback/Fake。
- [ ] 新建 `cutover-task` API client 和独立页面；首页展示来源、办事处、生成时间、状态与人工等级，不修改旧 `cut-task`。
- [ ] 创建向导按 SN 调用 context 解析，显式选择候选项目，展示设备、客户等级和 IMP READY 上下文后提交；客户端不生成 Owner、状态、等级或快照。
- [ ] 详情固定展示 P2～P6；P2 只按服务端 `allowedActions` 提供四项问卷、草稿和人工 A/B/C/D，不显示系统建议；P3/P4只显示已进入状态，P5/P6不伪造完成。
- [ ] 实现后补最小前端验证：候选选择、草稿不推进、A/B/C 与 D 两种提交响应、刷新持久化及 320/768/1440 基本可用；运行定向组件测试、`ts:check`和 `build:local`。
- [ ] 生产 IMP、AST、CUS、PROJ Provider 到位后，使用正式权限身份和真实 MySQL/Chromium完成一条主正向链“SN解析 → 选择项目 → READY自建 → P2草稿 → 人工A级提交 → P3”，并以定向后端正向测试证明 D → P4。浏览器只验证该成功链的展示与刷新事实。
- [ ] 更新唯一Task检查点和追溯，形成一个候选提交并申请一次独立Implementation Done审核；旧页面继续由“不修改旧运行面”的实现边界保护，不新增验证任务。

若生产 Owner Provider 尚未到位，Task 1和Task 2页面代码可完成，但状态保持 `IN_PROGRESS / BLOCKED_BY_DEPENDENCY`；不得用 Fake、手工 SQL 或测试种子替代浏览器正向证据。

## 6. 验证与完成口径

候选级验证只回答以下正向交付问题：

1. CUT新表、Mapper绑定和合格旧行迁移能否在真实MySQL成功落地；
2. 生产Owner匹配事实能否按唯一锁序完成重验并形成完整任务/评估/历史；
3. 正式工作台能否完成一条 P1 → P2 → P3 正向链，D 分支是否由聚焦服务验证进入 P4；
4. 刷新后任务、评估和上下文是否仍与数据库一致。

不重复Phase 1/2/3全仓门禁，不安排权限、Provider异常、冲突、跨租户或未来P3～P6的负向测试矩阵。运行守卫仍按已锁定合同实现，但不作为本计划的新增验证任务。

## 7. 风险与依赖

- **生产 Provider 未完成：** 只阻断 Task 2 最终真实浏览器和 Implementation Done，不授权 CUT 复制 Owner；继续跟踪 `F-IMP-001`、`F-PROJ-003`、`T-FIMP001-AST-01`。
- **共享 Flyway 竞争：** 实施落迁移前重新读取最高版本并取连续空闲号；若并行分支先占用，只前向改号，不改已执行迁移。
- **旧任务事实不完整：** 严格按已批准 CURRENT_FORWARD 分类；无法无损映射只留 issue，不为了提高迁移率补默认值。
- **旧新页面并存：** 菜单、路由、权限、表和写服务完全分离；项目详情旧入口不在本 Feature 中重定向。

## 8. Technical Plan Gate

当前结论：`REVIEW_REQUIRED`。最新有效独立裁决认定生产项目/部门（办事处）/客户发生时上下文公共Owner合同缺失；`14440e45`只关闭其他局部整改，`1875bb89`的整体PASS回写无完整授权。先完成`ProjectCutoverContextFactApi`合同独立复审，再返回同一计划Gate；当前不授权Implementation。
