# PRD V1.8批准修订009：项目BPM定义身份主线收敛

> 修订编号：`CHG-PRD-2026-09-01-009`<br>
> 批准日期：2026-09-01<br>
> 状态：`APPROVED`<br>
> 前置基线：`CHG-PRD-2026-08-30-008`<br>
> 选择来源：PROJ分支已批准`CHG-PRD-2026-09-01-011`（`ef1f49f2`）的BPM定义身份段

## 1. 主线收敛边界

1. master只选择来源修订011中的项目BPM定义身份语义，不导入该并行分支承载的COM、ACC或问卷业务修订。
2. 来源修订011及其Change ID继续作为分支历史事实保留；master使用当前线性基线的下一编号009登记选择结果，不改写来源提交。
3. 本修订不关闭其他分支重复使用修订010/011的问题；未被本修订逐项选择的并行PRD仍不得直接晋级或整支合入。

## 2. 批准结论

1. 项目模板冻结模板版本及BPM流程定义key引用，不保存或推导PMS独立流程版本。
2. 未显式指定定义ID时，BPM按key启动最新生效流程定义；授权发起人可查询同key历史定义并显式选择`processDefinitionId`。
3. 流程实例实际`processDefinitionId`和完整`taskDefinitionKey`是审批历史事实。PMS不得解析`taskDefinitionKey`中的版本信息，也不得建立`processDefinitionVersion`、`refVersion=vN`或等价版本接口。
4. 不同项目类型的阶段、任务、里程碑、交付件和门禁差异仍由冻结项目模板版本承载；BPM只承载模板引用的审批子流程，状态机和门禁继续决定项目生命周期推进。
5. 既有项目模板、项目和门禁表中的流程版本列仅保留历史值；新写入保持空值，不作为启动、节点解析或门禁输入，本修订不产生物理差量。

## 3. Gate边界

本修订仅将已批准的BPM身份语义选择进入master正式PRD。F-PROJ-008的SDS、Feature Ready、Technical Plan、Implementation与Done仍分别由其下游权威文件和证据裁决；本修订不自动批准代码、Flyway、Yudao基础平台修改或其他Feature。
