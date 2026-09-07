# F-CUT-005 旧实现复用审计

> Requirement：`CUT-05@V1`
> 结论：旧单级审核只能保留兼容，不能升级为分级审批真值。

| 资产 | 当前能力 | 结论 | F-CUT-005处置 |
|---|---|---|---|
| `CutPlanServiceImpl/CutPlanController` | 单行`PENDING_REVIEW→APPROVED/REJECTED`，单审核人/意见 | `PRESERVE_LEGACY / DO_NOT_REUSE_RUNTIME` | 旧入口原样保留；新建P5应用服务和REST |
| `CutTaskServiceImpl/CutTaskController` | 单级任务approve/reject并直接写旧tinyint状态 | `PRESERVE_LEGACY / DO_NOT_REUSE_RUNTIME` | 不接入新P5，不作为任务阶段Owner |
| `pms_cut_plan`审核字段 | `approved_by/time/opinion/status/baseline_version` | `NOT_MIGRATABLE_TO_CUT05` | 只作旧来源证据；不推导路由、节点、五项评审或批准事实 |
| `pms_cut_task.approval_opinion/status` | 单意见、混合生命周期 | `NOT_MIGRATABLE_TO_CUT05` | 不推导P5阶段、审批实例或历史 |
| 旧前端割接任务/方案页 | CRUD与单approve/reject按钮 | `PRESERVE_LEGACY / COPY_THEN_ENHANCE` | 旧路由不改；新P5面板独立挂入新工作台 |
| `CutStatusEnum/CutTaskStatusRules` | 旧tinyint任务/方案状态 | `NOT_REUSABLE` | 新审批使用封闭字符串状态和新任务P5迁移 |
| Yudao BPM/通用审批 | 通用流程能力，未冻结CUT等级路由、评审项与来源快照 | `INFRA_REFERENCE_ONLY` | 不改Yudao；F-CUT-005自有实例/节点，复用平台幂等、审计、Outbox和站内信API |
| 旧测试种子/综合测试 | 仅证明旧CRUD | `TEST_REFERENCE_ONLY` | 不作为新审批数据、迁移或验收事实 |

## 审计结论

- 后端、前端、状态、权限、表和测试均不存在可直接承接CUT-05的完整实现。
- 新审批五表为`NEW_ONLY`；旧数据保留可查，不改写、不双写、不迁移为正式批准事实。
- 旧Controller、Service、页面、权限和表继续由旧路径拥有，F-CUT-005不得为复用而改变其副作用。

## 代码事实实施状态（2026-09-04三分支重放）

- 已接收代码路径：`19`。
- 已处理来源提交：`11`。
- 实施状态：已实现切片进入集成分支；未关闭Gate时Feature保持 `IN_PROGRESS`。
- 追溯明细：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。
