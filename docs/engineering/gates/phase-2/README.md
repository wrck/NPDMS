# Phase 2 门禁与证据

Phase 2 用于审查数据、数据库、API、事件、集成、文件、缓存、并发、异常、幂等契约是否足以进入 Phase 3。

> 当前门禁：`REVALIDATION_REQUIRED / NOT_READY_FOR_PHASE_3_V1.8`<br>
> 当前范围：V1 53项、V2 47项、V1/V2正式需求100项；已编号V3 31项、跨需求演进方向2项；`OUT_OF_SCOPE` 9项<br>
> 迁移边界：84对象、95来源绑定、1排除源；P3-E09=`MODEL_BASELINE_READY`；真实迁移与数据切换仍由`AI-MIG-000`单独控制

## 当前文件

- [`gate-status.md`](gate-status.md)：Phase 2 当前门禁状态。
- [`implementation-fact-inventory.md`](implementation-fact-inventory.md)：实现仓库事实、漂移分类和前向纠正约束。
- [`self-review.md`](self-review.md)：V1.7 Phase 2 工程化自审与修正历史；V1.8重验证完成后更新。
- [`independent-review.md`](independent-review.md)：V1.7独立复审历史结论；不得作为V1.8放行依据。

## 归档规则

- 实现事实盘点、评审输入和生成校验证据放入本目录或 `input/`。
- 正式 08～16 SDS 分册只放入 `docs/design/`，不在本目录创建平行副本。
- 独立评审结果必须回指 Requirement ID、正式 SDS 或可重现命令。
