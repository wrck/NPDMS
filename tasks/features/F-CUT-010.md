# F-CUT-010 割接备件系统协同

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION_TASK_4`
> Feature Ready Gate：`READY / GO@c4b1a939`
> Technical Plan Gate：`PASS / GO@f840cbbc`
> Implementation Done Gate：`NOT_READY`
> Requirement：`CUT-08@V2=FULL`
> Feature Spec：`specs/features/F-CUT-010-cutover-spare-system-coordination.md`
> 机器合同：`specs/features/F-CUT-010-api-contract.json`、`specs/features/F-CUT-010-physical-contract.json`
> 旧实现审计：`specs/features/F-CUT-010-legacy-reuse-audit.md`
> Technical Plan候选：`docs/superpowers/plans/2026-09-02-f-cut-010-cutover-spare-system-coordination.md`
> master集成映射：`codex/f-cut-001-matrices@faed8387 -> master代码回执c9066332；来源V161 -> master V192；只接收已完成Task 1～3，不把Task 4倒签完成`
> master复验：`CUT API 6项、CUT共享后端242项（跳过MySQL 27）与前端68项零失败；当前仍为Task 4正向实现，生产INT-06端口、真实MySQL与真实浏览器未闭合`

## 当前最小工作单元

- 形成CUT-08完整纵向Feature：需求识别、外部申请发起/跳转、引用与状态回填、人工证据、P5展示共同承接，不拆成Provider或单页碎片。
- INT-06只预留生产端口；CUT实施阶段以`src/test`受控替身完成正常正向闭环，不实现第三方、COM或Yudao。
- 已完成规格、机器合同、复用审计、追溯、Feature Ready、Technical Plan及Task 1～3；当前进入Task 4发起、刷新和人工证据应用服务正向实现，不注册生产Provider。

## Gate清单

- [ ] API/Physical/Legacy/SDS Machine Contract Gate。
- [x] Feature Ready独立复审（GO@c4b1a939）。
- [x] 唯一Technical Plan独立复审（GO@f840cbbc）。
- [x] Task 1公共合同、消费端口与需求Codec独立复审（GO@26c02ac2）。
- [x] Task 2三表Schema、DO与Mapper合同独立复审（GO@750ef1ab）。
- [x] Task 3详情查询、受控PLT投影与P5 FULL安全摘要独立复审（GO@831a85b9）。
- [ ] CUT领域、Schema、应用/REST、UI、受控MySQL正向闭环各Task Gate。
- [ ] 生产INT-06 Provider、唯一装配、真实浏览器与Implementation Done。

## Phase-switch checkpoint

基线831a85b9；Task 3独立复审GO，P2/P3需求、详情稳定投影、受控PLT展示名与P5 FULL脱敏摘要已通过15/15聚焦测试；当前进入Task 4，仅实现CUT写命令正向闭环，INT-06/PLT继续使用src/test受控替身。
