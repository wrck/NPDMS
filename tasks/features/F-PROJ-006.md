# F-PROJ-006 项目回退、异常关闭与受控重开

> Feature实施状态：`IMPLEMENTATION_PLANNED`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Feature Ready Gate：`PASS / NPDMS-FPROJ006-FEATURE-READY-20260825-01`
> Implementation Done Gate：`NOT_STARTED`
> 当前阻断：无；`COLLECTION`仅预留公共接口，INT-12后续实施，不作为当前Feature前置
> 当前任务：`Task 10 完成正向治理浏览器闭环与Feature验收`
> Requirement ID：`PM-10`（V1）
> Feature Spec：`specs/features/F-PROJ-006-project-rollback-exception-close-and-reopen.md`
> Feature物理契约：`specs/features/F-PROJ-006-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-25-f-proj-006-project-rollback-exception-close-and-reopen.md`
> 锁定规格提交：`126ba19c4ab5a62b13248d0cd1bbdc15ae35b19b`

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
- [x] Task 5 提供BPM守卫并预留COLLECTION接口（BPM子项PASS / `4b4ba22`；COLLECTION后续由INT-12接入，不前置）
- [x] Task 6 签发并重验不透明守卫令牌（PASS / `f58115b..48a72d9` / 独立裁决GO）
- [x] Task 7 实现回退命令闭环（PASS / `1ea4411..0722afc` / 独立裁决GO）
- [x] Task 8 实现异常关闭与受控重开（PASS / `bc3d086` / 独立裁决GO）
- [x] Task 9 提供治理API与append-only历史查询（PASS / `e5e5687..1beb732` / 独立裁决GO）
- [ ] Task 10 完成响应式治理界面与Feature验收（UI/自动化/真实MySQL子项GO / `10e05fe`；四档响应式、主题持久化、守卫失败关闭PASS；正向浏览器闭环待COLLECTION）

> 检查点（2026-08-25）：规格基线`126ba19`；Task 1～9及Task 10 UI/MySQL子项已通过既有裁决，P3-E08已VERIFIED；COLLECTION接口预留且不属当前必需Provider，无阻塞；下一步完成Task 10正向治理真实浏览器闭环并送Feature Done复审。
