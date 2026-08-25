# F-PROJ-006 项目回退、异常关闭与受控重开

> Feature实施状态：`IMPLEMENTATION_PLANNED`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Feature Ready Gate：`PASS / NPDMS-FPROJ006-FEATURE-READY-20260825-01`
> Implementation Done Gate：`NOT_STARTED`
> 当前阻断：P3-E08已`VERIFIED`；真实Chrome已完成四档响应式、主题持久化与守卫失败关闭验收。设备连接与采集中心的COLLECTION权威端点、响应契约和真实验收证据仍缺失，正向治理闭环无法可信验收，Task 5、Task 10及Feature Done保持未关闭
> 当前任务：`Task 10 完成响应式治理界面与Feature验收`
> Requirement ID：`PM-10`（V1）
> Feature Spec：`specs/features/F-PROJ-006-project-rollback-exception-close-and-reopen.md`
> Feature物理契约：`specs/features/F-PROJ-006-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-25-f-proj-006-project-rollback-exception-close-and-reopen.md`
> 锁定规格提交：`cb55c7478378ed769f0a4fd401fabb8840017242`

## 事实边界

- 旧`pms_project_governance_action`、旧CRUD和V1.7测试只是审计输入，不代表任何V1.8任务已完成。
- 回退保持ACTIVE并回S0/UNASSIGNED；异常关闭写EXCEPTION_CLOSED；重开只消费最近有效异常关闭快照。
- 正常闭环及NORMAL_CLOSED由CLO-02独占，本Feature不实现、不重开。
- 所有必需Provider必须给出版本化事实；缺失、超时、未知或并发变化均失败关闭。
- 当前只推进Implementation，不准备Deployment、SIT、UAT或Release材料。

## 任务跟踪

- [x] Task 1 建立共享快照与权限物理基础（PASS / `82e2193` / 独立裁决GO）
- [x] Task 2 实现共享快照持久化与Project原子更新（PASS / `4060039..2ca2f92` / 独立裁决GO）
- [x] Task 3 固化守卫公共契约与PROJ事实（PASS / `9256cb5..39f5260` / 独立裁决GO）
- [x] Task 4 提供CUT与INSPECTION守卫事实（PASS / `0341aa6` / 独立裁决GO）
- [ ] Task 5 提供BPM与COLLECTION集成守卫（BPM子项PASS / `4b4ba22` / COLLECTION权威契约待外部证据）
- [x] Task 6 签发并重验不透明守卫令牌（PASS / `f58115b..48a72d9` / 独立裁决GO）
- [x] Task 7 实现回退命令闭环（PASS / `1ea4411..0722afc` / 独立裁决GO）
- [x] Task 8 实现异常关闭与受控重开（PASS / `bc3d086` / 独立裁决GO）
- [x] Task 9 提供治理API与append-only历史查询（PASS / `e5e5687..1beb732` / 独立裁决GO）
- [ ] Task 10 完成响应式治理界面与Feature验收（UI/自动化/真实MySQL子项GO / `10e05fe`；四档响应式、主题持久化、守卫失败关闭PASS；正向浏览器闭环待COLLECTION）

> 检查点（2026-08-25）：Feature Ready正式GO；规格提交`cb55c747`已同步至NPDMS基线`8d6e7e7`；新Technical Plan基于V1.8差距审计生成，未复用旧计划；下一步执行Task 1。

> 检查点（2026-08-25）：Task 1已建立V85共享阶段快照和V86原因字典/稳定权限种子；迁移契约7/7、Project Reactor编译PASS；当前仅待独立Implementation Done裁决，不提前进入Task 2。

> 检查点（2026-08-25）：Task 1独立Implementation Done裁决GO，提交`82e2193`；已回写PASS并推进Task 2，不重开Feature Ready、PRD、SDS门禁。

> 检查点（2026-08-25）：Task 2完成审查整改：快照仅经受信租户上下文和动作校验追加，分页防御复制且上限200，项目行锁后以当前读重验消费；契约13/13、规则4/4、Reactor 277通过/22既有IT跳过、空库V1～V86及MySQL并发/越租户3/3通过；待独立裁决。

> 检查点（2026-08-25）：Task 2独立Implementation Done裁决GO，提交范围`4060039..2ca2f92`；已回写PASS并推进Task 3，不重开Feature Ready、PRD、SDS门禁。

> 检查点（2026-08-25）：Task 3公共守卫契约及PROJ事实经整改后独立Implementation Done裁决GO，提交范围`9256cb5..39f5260`；已回写PASS并推进Task 4，不重开Feature Ready、PRD、SDS门禁。

> 检查点（2026-08-25）：Task 4的CUT与INSPECTION本域守卫事实独立Implementation Done裁决GO，提交`0341aa6`；已回写PASS并推进Task 5，不重开Feature Ready、PRD、SDS门禁。

> 检查点（2026-08-25）：Task 5裁决GO：BPM子项提交`4b4ba22`达到Implementation Done；COLLECTION权威契约及真实验收待外部证据，整个Task 5保持未关闭并登记为Feature Done前阻断；允许继续Task 6～9，当前推进Task 6。

> 检查点（2026-08-25）：Task 6经整改后独立Implementation Done裁决GO，提交范围`f58115b..48a72d9`；版本化守卫令牌已封闭非规范Base64URL等价编码篡改，完整树及六Provider事实执行提交前重验；COLLECTION仍只消费设备连接与采集中心权威事实，外部契约阻断保持，当前推进Task 7。

> 检查点（2026-08-25）：Task 7经存量兼容整改后独立Implementation Done裁决GO，提交范围`1ea4411..0722afc`；回退命令以同一平台事务完成Project CAS、服务经理区间结束、append-only快照、幂等、审计和Outbox，`assignment_type IS NULL`沿用既有兼容主责语义；COLLECTION外部契约阻断保持，当前推进Task 8。

> 检查点（2026-08-25）：Task 8提交`bc3d086`经独立Implementation Done裁决GO；异常关闭与受控重开已闭合权限、状态、守卫、事务、快照消费及事件契约，聚焦测试21/21 PASS；COLLECTION权威契约仍为Feature Done前阻断，当前推进Task 9。

> 检查点（2026-08-25）：Task 9提交范围`e5e5687..1beb732`经独立Implementation Done裁决GO；治理API、append-only历史和前端调用契约已固化，单租户关闭租户模块时在调用范围建立受信tenantId=0，多租户缺失上下文失败关闭；COLLECTION权威契约阻断保持，当前推进Task 10。

> 检查点（2026-08-25）：基线`cb55c747`；Task 10 UI/MySQL子项独立GO（`10e05fe`，组件23/23、主题5/5、25模块/Project 323、空库V1～V87与MySQL 5/5）；Task 10未关闭，P3-E08 `OPEN/FAIL`阻断浏览器，COLLECTION权威契约缺失；下一步等待最近前置Gate闭环后继续正式浏览器验收。

> 检查点（2026-08-25）：P3-E08已由`478ecda`关闭为VERIFIED；真实Chrome四档响应式、主题刷新持久化及回退/异常关闭失败关闭PASS，零控制台/页面/HTTP错误。COLLECTION仍固定不可用，正向三动作与历史持久化未验收，Task 5/10不关闭；320px基础设置抽屉裁切登记为非本Feature问题，不修改基础框架。
