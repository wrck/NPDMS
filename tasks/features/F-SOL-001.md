# F-SOL-001 项目工期基线与版本化变更审批

> Feature实施状态：`IMPLEMENTATION_COMPLETE`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / NPDMS-FSOL001-FEATURE-READY-20260826-01-R1`
> Implementation Done Gate：`PASS / 独立复审GO（2026-08-27）`
> Technical Plan Gate：`PASS / NPDMS-FSOL001-TECHPLAN-20260826-01`
> 当前阻断：`无；F-PLT-001已完成并解除PLT-02客户延期材料分支阻断`
> 当前任务：`Implementation Done追溯回写与规格基线同步`
> Requirement ID：`PRE-01（V1）`
> Feature Spec：`specs/features/F-SOL-001-project-duration-baseline-and-change-approval.md`
> Feature物理契约：`specs/features/F-SOL-001-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-26-f-sol-001-project-duration-baseline-and-change-approval.md`
> 锁定规格提交：`f2c563df978b7d7b3b1de9ad245b9c485bbdbae8`

## 实施边界

- 本Feature只实现PRE-01，不包含PRE-02、PLN-01、PLN-04、部署、SIT、UAT或Release。
- SOL以`sol_construction_plan`、`sol_construction_plan_revision`、`sol_construction_plan_change`承载当前事实，不创建`sol_construction_plan_item`。
- 首次工期直接生效；后续变更通过平台BPM单节点服务经理审批。审批通过后新工期成为唯一当前版本，计划仅标记待重算，不覆盖旧施工计划。
- 旧工期倒排和计划变更仅作历史只读证据，不迁移、不双写、不作为V1.8当前真值。
- 客户依据统一复用PLT FileArtifact固定版本事实；SOL不保存URL或自建文件真值。

## 任务跟踪

- [x] Task 1 建立SOL工期物理模型、配置种子和Feature工作单（PASS / `b2b019d` / 独立裁决GO）
- [x] Task 2 提供PROJ项目资格与当前参与人公共事实（PASS / `365d907` / 独立裁决GO）
- [x] Task 3 建立SOL持久化、状态值域和查询原语（PASS / `3e3bdf3` + `df3f3ce` / 独立裁决GO）
- [x] Task 4 实现首次工期录入与查询（PASS / `5cf50e8` / 独立裁决GO）
- [x] Task 5 实现工期变更草稿与部分更新（PASS / `59115fe` + `30bf273` / 独立裁决GO）
- [x] Task 6 冻结依据并提交平台BPM审批（PASS / `834c182` + `f4d3cca` + `34ce4df` + `eb42aa3` / 独立裁决GO）
- [x] Task 7 同步消费BPM终态并生效工期结果（PASS / `d659501` + `b0fdc23` + `34ce4df` + `eb42aa3` / 独立裁决GO）
- [x] Task 8 建设响应式项目工期界面并冻结旧PRE-01写入口（PASS / `668234d` / `NPDMS-FSOL001-TASK8-20260826-01`）
- [x] Task 9 完成真实MySQL、事务并发和BPM集成验证（PASS / `db394a0` + `7e9d6d3` + `34ce4df` + `eb42aa3` / 独立裁决GO）
- [x] Task 10 完成真实浏览器、独立复审和Feature Done回写（PASS / `4e9a0aa` + `cf2ff42` + `85b6d09` + `43110a7` / 独立裁决GO）

> 检查点（2026-08-26）：Task 1提交`b2b019d`经独立复审GO；V1～V91隔离MySQL迁移、根唯一键、种子幂等、迁移契约4/4、20模块Reactor编译及规格快照校验均PASS，允许推进Task 2。

> 检查点（2026-08-26）：Task 2已完成PROJ项目资格与当前参与人公共事实候选实现；公共API行为8/8、V1～V91隔离MySQL Mapper/锁定回归4/4及25模块Reactor均PASS，当前等待独立Implementation Done复审，尚未回写Task 2 PASS。

> 检查点（2026-08-26）：Task 2提交`365d9073f56ecbc5ad41cc98d2d03b78fd820169`经独立复审GO；公共契约、封闭角色值域、受信租户、项目行与主责区间锁顺序、版本/阶段守卫及范围边界均通过，允许推进Task 3。

> 检查点（2026-08-26）：Task 3已完成自然日工期规则、三表DO、封闭写接口、场景化锁定/CAS/稳定分页与流程实例查询原语；聚焦规则、Mapper接口契约及V1～V91隔离MySQL回归10/10、20模块Reactor均PASS，当前等待独立Implementation Done复审，尚未回写Task 3 PASS。

> 检查点（2026-08-26）：Task 3候选提交`3e3bdf3`及整改提交`df3f3cec5cb032dbeb72a128e2cf17a4c7c8914e`经独立复审GO；通用CRUD写入口已封闭，显式持久化原语、日期规则和真实MySQL证据均通过，允许推进Task 4。

> 检查点（2026-08-26）：Task 4提交`5cf50e813add00636cc10c24f5426f7c9ba00719`经独立复审GO；首次工期原子生效、受信权限与项目资格重验、平台幂等审计、只读历史查询及真实MySQL事务回滚证据均通过，允许推进Task 5。

> 检查点（2026-08-26）：Task 5实现提交`59115fe278e79771ff0b7403e10431ae75202503`及审计整改提交`30bf27330047fe2c70051f96a54fcbb1f84d66b1`经独立复审GO；变更草稿、候选revision、真实字段存在性PATCH、受信权限与项目资格重验、幂等回滚及创建/PATCH前后审计快照均通过，聚焦测试26/26、全新MySQL V1～V91与应用集成6/6、35模块装配构建均PASS，允许推进Task 6。

> 检查点（2026-08-26）：Task 6实现提交`834c1829f7d988483128574c2773040579412911`及审计整改`f4d3ccaa336b16f774e9237595c44a778a88432a`经独立复审GO；BPM提交、冻结审批人、标准变量、提交审计及BPM_APPROVAL守卫子项PASS。Task 6整体仍受`PLT-02 / FileArtifact`阻断，按计划转入可独立实施的Task 7。

> 检查点（2026-08-26）：Task 7实现提交`d6595013a22d1242da8ba73fa820aa8ff65beba4`及真实事务回归整改`b0fdc2326ee272bb5f430970c47426325d014acf`经独立复审GO；无材料的APPROVE、REJECT、CANCEL终态主线及三类授权失效共同回滚均由真实Flowable、MySQL、SOL Mapper和同步事务验证通过。仅将无材料终态主线登记PASS，材料分支继续保留`BLOCKED_BY_UPSTREAM_IMPLEMENTATION: PLT-02`，当前推进Task 8。

> 检查点（2026-08-26）：Task 8提交`668234dad63bb017b1a0a7fdffc1087b791639cc`经独立裁决`NPDMS-FSOL001-TASK8-20260826-01`确认GO；响应式项目工期主线、字段存在性PATCH/null清空、平台BPM跳转与申请人撤回、游标历史和旧V1.7写入口退役均通过，允许推进Task 9。Task 9/10、正式浏览器闭环及PLT-02材料分支未提前关闭。

> 检查点（2026-08-26）：Task 9主实现提交`db394a0`及真实PROJ参与事实整改`7e9d6d351dc405e83340fbef9517d58d108aa16c`经独立复审GO；V1～V91迁移/种子、真实SOL/PLATFORM/Flowable事务链、BPM_APPROVAL守卫及真实`ProjectParticipantFactApiImpl`项目版本重验均通过。仅登记Step 1～3／无材料主线PASS；Task 9整体继续保持`BLOCKED_BY_UPSTREAM_IMPLEMENTATION: PLT-02`，不得推进Task 10。

> 检查点（2026-08-27）：F-PLT-001 Task 6提交`34ce4dff8f380192e96aba679656cf4728527feb`及整改`eb42aa3ae4d1f5a4510e73c824e3cdc4d866b3ef`经独立复审GO；客户依据上传、固定版本冻结、三终态锁定重验和PROJ范围版本并发冲突均由真实MySQL、Flowable、PROJ、PLT、SOL验证通过，Task 6/7材料分支与Task 9材料场景阻断解除。Task 10仍等待F-PLT-001统一文件界面接入。

> 检查点（2026-08-27）：F-PLT-001 Task 10整改提交`38f36b1f0d345bc2653207302a37d2792362e58a`经独立复审GO；统一文件界面、客户延期材料上传冻结、单租户Flowable正向审批和清洁库浏览器链已闭合，`BLOCKED_BY_UPSTREAM_IMPLEMENTATION: PLT-02`正式解除。F-SOL-001现可进入Task 10正式浏览器与独立Feature Done复审，本检查点不提前将Task 10或Implementation Done回写PASS。

> 检查点（2026-08-27）：Task 10实现整改`85b6d09db65a7b02f4ad291aaafbe5ead4a06eb0`及浏览器证据提交`43110a7`经独立复审GO；公开UI/API已覆盖服务经理驳回、项目经理权限拒绝、项目版本冲突、刷新持久化和无成功副作用，变更历史候选版本投影已闭合。Task 10与F-SOL-001 Implementation Done正式PASS；本结论不包含Deployment、SIT、UAT或Release。
