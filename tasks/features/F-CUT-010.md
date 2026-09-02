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
- 已完成规格、机器合同、复用审计、追溯、Feature Ready与Technical Plan；Task 1候选仅建立公共回调合同、INT/PLT消费端口和需求快照Codec，不注册生产Bean或Provider。

## Gate清单

- [ ] API/Physical/Legacy/SDS Machine Contract Gate。
- [x] Feature Ready独立复审（GO@c4b1a939）。
- [x] 唯一Technical Plan独立复审（GO@f840cbbc）。
- [x] Task 1公共合同、消费端口与需求Codec独立复审（GO@26c02ac2）。
- [ ] CUT领域、Schema、应用/REST、UI、受控MySQL正向闭环各Task Gate。
- [ ] 生产INT-06 Provider、唯一装配、真实浏览器与Implementation Done。

## Phase-switch checkpoint

基线26c02ac2；Task 1独立复审GO，公共回调、INT/PLT端口、需求Codec合同已锁定；无生产Provider/Bean。当前进入Task 2，仅实现三表Schema、DO与Mapper合同并做隔离MySQL验证，GO前不进入应用编排。
