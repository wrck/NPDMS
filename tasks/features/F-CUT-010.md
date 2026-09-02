# F-CUT-010 割接备件系统协同

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION_TASK_2`
> Feature Ready Gate：`READY / GO@c4b1a939`
> Technical Plan Gate：`PASS / GO@f840cbbc`
> Implementation Done Gate：`NOT_READY`
> Requirement：`CUT-08@V2=FULL`
> Feature Spec：`specs/features/F-CUT-010-cutover-spare-system-coordination.md`
> 机器合同：`specs/features/F-CUT-010-api-contract.json`、`specs/features/F-CUT-010-physical-contract.json`
> 旧实现审计：`specs/features/F-CUT-010-legacy-reuse-audit.md`
> Technical Plan候选：`docs/superpowers/plans/2026-09-02-f-cut-010-cutover-spare-system-coordination.md`

## 当前最小工作单元

- 形成CUT-08完整纵向Feature：需求识别、外部申请发起/跳转、引用与状态回填、人工证据、P5展示共同承接，不拆成Provider或单页碎片。
- INT-06只预留生产端口；CUT实施阶段以`src/test`受控替身完成正常正向闭环，不实现第三方、COM或Yudao。
- 已完成规格、机器合同、复用审计、追溯、Feature Ready、Technical Plan与Task 1；Task 2候选仅建立CUT三表、DO、场景化Mapper/XML和权限种子，不注册生产Bean或Provider。

## Gate清单

- [ ] API/Physical/Legacy/SDS Machine Contract Gate。
- [x] Feature Ready独立复审（GO@c4b1a939）。
- [x] 唯一Technical Plan独立复审（GO@f840cbbc）。
- [x] Task 1公共合同、消费端口与需求Codec独立复审（GO@26c02ac2）。
- [ ] Task 2三表Schema、DO与Mapper合同独立复审（REVIEW_REQUIRED）。
- [ ] CUT领域、Schema、应用/REST、UI、受控MySQL正向闭环各Task Gate。
- [ ] 生产INT-06 Provider、唯一装配、真实浏览器与Implementation Done。

## Phase-switch checkpoint

基线bf1e016e；Task 2候选已实现V161三表、DO、场景化Mapper/XML和`manage-spare`权限种子；聚焦合同6/6通过，隔离MySQL 8.4空库迁移至V161并成功写入一组合法引用、状态revision与人工证据；等待独立复审，GO前不进入Task 3。
