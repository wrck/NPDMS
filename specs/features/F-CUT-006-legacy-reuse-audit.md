# F-CUT-006 旧实现复用审计

> Requirement：`CUT-06@V1`
> 结论：旧执行/观察模型与P6一次性闭环语义冲突，只保留旧路径与来源证据，不直接复用运行模型。

| 资产 | 当前能力 | 结论 | F-CUT-006处置 |
|---|---|---|---|
| `pms_cut_execution` | 按`step_name`逐行记录执行状态、结果、异常和`evidence_url` | `PRESERVE_LEGACY / NOT_MIGRATABLE_AS_CLOSURE` | 不复制步骤，不把URL当FileArtifact；所有旧行保留，当前无行可证明为完整P6闭环 |
| `pms_cut_observation` | 观察起止、观察状态和遗留项状态机 | `PRESERVE_LEGACY / EXCLUDED` | 整表不迁移；不建设稳定观察或遗留项生命周期 |
| `pms_cut_task.actual_time/status/remark` | 混合旧任务生命周期和自由文本 | `NOT_SUFFICIENT` | 不推导四项结果、最终结果、提交人或归档事实 |
| `CutTaskServiceImpl/CutTaskController` | 旧CRUD和单级审核，写旧tinyint状态 | `PRESERVE_LEGACY / DO_NOT_REUSE_RUNTIME` | 新建P6应用服务和REST；旧副作用保持不变 |
| `CutStatusEnum/CutTaskStatusRules` | 包含执行中/稳定观察/完成/回退旧状态 | `NOT_REUSABLE` | 新路径只使用`P6/CLOSURE_IN_PROGRESS→P6/ARCHIVED` |
| 旧`cut-task`前端 | CRUD、审核按钮和旧字段 | `PRESERVE_LEGACY / COPY_THEN_ENHANCE` | 新P6面板挂入新工作台，不修改旧页面/路由/权限 |
| `pms_cut_execution.evidence_url` | 自由URL字符串 | `NOT_FILE_FACT` | 不迁入附件；新附件只存PLT不可变事实引用 |
| 旧测试与字典 | 证明旧CRUD和旧tinyint枚举 | `TEST_REFERENCE_ONLY` | 不作为CUT-06迁移、状态或验收事实 |

## 迁移结论

- 正式迁移契约保持`pms_cut_execution -> cut_cutover_closure / CURRENT_FORWARD`，但资格谓词要求同一旧来源能无歧义证明闭环级唯一身份、三项正常性、回退联合、最终结果和文件事实；当前旧表结构无法满足，故现有结构合法的旧步骤行在PLT迁移证据批次中分类为`RETAINED`，不插入新表。只有冻结来源身份或载荷损坏才追加`FCUT006_SOURCE_RECORD_INVALID`迁移问题，不能把“不满足新闭环资格”本身冒充数据损坏。
- 批次固定为`ownerContext=CUT`、`purpose=CUTOVER_CLOSURE_CURRENT_FORWARD`、`sourceSystem=NPDMS_LEGACY`、`sourceTable=pms_cut_execution`，来源键为旧行十进制`id`，规则版本为`FCUT006_LEGACY_V1`。原始旧行先由Release受控迁移导入器经PLT API冻结，CUT生产Bean不读取旧表/文件/第二数据源。CUT外层事务执行`STAGED_READY→claim(RECONCILING)→逐源RETAINED/issue→completeReconciliation(COMPLETED)`；当前`mappedCount=0`，`sourceCount=retainedCount+issueCount`。临时Provider/数据库失败回滚整批至可重领状态，不落永久issue。
- 禁止按`status=2/3/4`、`step_name`、`result`文本、`evidence_url`、`actual_time`或测试种子补造SUCCESS/FAILED及归档事实。
- `pms_cut_observation`、旧Controller/Service/UI/权限继续由旧路径拥有；新Feature不改写、不双写、不删除。
