# PRD V1.8修订011：并行基线收敛与BPM定义身份

> 修订编号：`CHG-PRD-2026-09-01-011`<br>
> 候选日期：2026-09-01<br>
> 状态：`PROPOSED_FOR_INDEPENDENT_REVIEW`<br>
> 输入一：`CHG-PRD-2026-08-30-008`（提交`61949f92`，CUT双机97项）<br>
> 输入二：`CHG-PRD-2026-08-30-010`（提交`c69a53a4`，包含2026-08-29修订008/009及修订010）

## 1. 合并原则

1. 两条输入均源自修订007，是并行基线而非线性覆盖；两个完整修订008 Change ID保留原编号和日期，不改写历史。
2. 修订011以修订010 PRD为底稿，叠加CUT并行修订008已批准的双机97项语义，再应用本次PM-03 BPM定义身份澄清。
3. 修订008～010既有说明、报告和来源提交原样保留；领域投影和追溯只从合并后的双PRD重新生成。

## 2. BPM定义身份结论

1. 项目模板冻结模板版本及BPM流程定义key引用，不保存或推导PMS独立流程版本。
2. 未显式指定定义ID时，BPM按key启动最新生效流程定义；授权发起人可查询同key历史定义并显式选择`processDefinitionId`。
3. 流程实例实际`processDefinitionId`和完整`taskDefinitionKey`是审批历史事实。PMS不得解析`taskDefinitionKey`中的版本信息，也不得建立`processDefinitionVersion`、`refVersion=vN`或等价版本接口。
4. 项目类型的阶段、任务、里程碑、交付件和门禁差异仍由冻结项目模板版本承载；BPM只承载模板引用的审批子流程，状态机和门禁继续决定项目生命周期推进。
5. 既有项目模板、项目和门禁表中的流程版本列仅保留历史值；新写入保持空值，不作为启动、节点解析或门禁输入，本修订不产生物理差量。

## 3. Gate边界

本候选只申请PRD Baseline Gate独立审批。GO前不得形成受影响SDS、Feature或Technical Plan结论，不修改产品代码、Flyway、BPM/Yudao基础平台或其他并行Feature实现。
