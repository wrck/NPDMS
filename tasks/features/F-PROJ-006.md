# F-PROJ-006 项目回退、异常关闭与受控重开

> Feature实施状态：`IMPLEMENTATION_PLANNED`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Feature Ready Gate：`PASS / NPDMS-FPROJ006-FEATURE-READY-20260825-01`
> Implementation Done Gate：`NOT_STARTED`
> 当前阻断：无当前必停阻断；COLLECTION权威守卫来源证据在Task 5核验，缺失时失败关闭并登记为Feature Done前阻断
> 当前任务：`Task 2 实现共享快照持久化与Project原子更新（待独立裁决）`
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
- [ ] Task 2 实现共享快照持久化与Project原子更新
- [ ] Task 3 固化守卫公共契约与PROJ事实
- [ ] Task 4 提供CUT与INSPECTION守卫事实
- [ ] Task 5 提供BPM与COLLECTION集成守卫
- [ ] Task 6 签发并重验不透明守卫令牌
- [ ] Task 7 实现回退命令闭环
- [ ] Task 8 实现异常关闭与受控重开
- [ ] Task 9 提供治理API与append-only历史查询
- [ ] Task 10 完成响应式治理界面与Feature验收

> 检查点（2026-08-25）：Feature Ready正式GO；规格提交`cb55c747`已同步至NPDMS基线`8d6e7e7`；新Technical Plan基于V1.8差距审计生成，未复用旧计划；下一步执行Task 1。

> 检查点（2026-08-25）：Task 1已建立V85共享阶段快照和V86原因字典/稳定权限种子；迁移契约7/7、Project Reactor编译PASS；当前仅待独立Implementation Done裁决，不提前进入Task 2。

> 检查点（2026-08-25）：Task 1独立Implementation Done裁决GO，提交`82e2193`；已回写PASS并推进Task 2，不重开Feature Ready、PRD、SDS门禁。

> 检查点（2026-08-25）：Task 2完成审查整改：快照仅经受信租户上下文和动作校验追加，分页防御复制且上限200，项目行锁后以当前读重验消费；契约13/13、规则4/4、Reactor 277通过/22既有IT跳过、空库V1～V86及MySQL并发/越租户3/3通过；待独立裁决。
