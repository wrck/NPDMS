# SDS Phase 2 Review

> 审查状态：`REVALIDATION_REQUIRED`<br>
> 当前依据：PRD V1.8修订001—012、014—015正式基线，Phase 1当前为`REVALIDATION_REQUIRED`<br>
> 上次批准：PRD V1.8修订007，`APPROVED / READY_FOR_PHASE_3_V1.8`（历史证据）<br>
> 当前结论：`BLOCKED_BY_PRD_DELTA`<br>
> 机器门禁：`NOT_RUN_FOR_REVISION_015`<br>
> 需求方批准：`PENDING_DELTA_REVIEW`<br>
> 适用修订：`PRD_V1.8_REVISION_015`<br>
> 当前范围：正式Requirement 100项；111个目标版本切片（V1 53个、V2 58个），数量不变但部分语义义务已变化

## 1. 状态说明

修订007的Phase 2批准只证明旧基线的数据、API、事件、集成、权限、异常和迁移契约。修订008—015引入或改变了可执行合同，尤其是通用阶段编排、三类闭环终态、项目内范围追加和全集统计，因此当前不能继续使用旧批准证明修订015的实现契约完整。

## 2. 必须重验证的Phase 2契约

| 范围 | 当前状态 | 关闭条件 |
|---|---|---|
| 模板和阶段编排 | REVALIDATION_REQUIRED | 定义`ProjectTemplateVersion`、`StageDefinition`、`StageTransitionDefinition`、Task/Deliverable定义、图发布校验、实例快照和目标阶段唯一解析；不生成阶段适用性状态 |
| WorkBinding与业务视图 | REVALIDATION_REQUIRED | 定义已注册实体/视图契约、实例解析幂等、权限、完成事实和UI上下文；禁止任意脚本、SQL和未注册URL |
| 项目状态/闭环API | REVALIDATION_REQUIRED | 数据/API/事件覆盖`NO_TRACKING_CLOSED`、`closure_type`、`closed_from_stage`、从最后真实阶段闭环及唯一Writer；历史兼容和枚举迁移明确 |
| 条件性验收和服务交接 | REVALIDATION_REQUIRED | 直签初验+终验、非直签终验；模板不含S5不校验终验；适用时`ACC-06 → CLO-01 → CLO-02`，快照失效可重算 |
| PM-06范围追加 | REVALIDATION_REQUIRED | 定义合同引用、订单行分配、`ProjectScopeVersion/Item`、可用数量、幂等键、影响分析、补充任务/门禁、原事实不继承和原子事务 |
| S4割接聚合 | REVALIDATION_REQUIRED | CUT结果按冻结范围聚合，成功并集完整覆盖当前需割接范围；部分成功、失败和范围变更不放行 |
| BPM身份 | REVALIDATION_REQUIRED | 模板保存definition key；实例保存实际`processDefinitionId`和完整`taskDefinitionKey`；历史定义选择、候选人、审计和重试一致 |
| RPT-02读模型 | REVALIDATION_REQUIRED | 同一项目单一display status，阶段/超期/三类终态/正常与业务闭环率、父子粒度、快照、下钻和导出同口径 |
| 数据模型/P3-E09 | IMPACT_ASSESSMENT_REQUIRED | 明确上述变化是否需要新表、字段、枚举、索引和前向迁移；有物理变化时重做P3-E09差量，不自动沿用旧模型通过 |

## 3. 关闭动作

1. Phase 1差量边界先得到确认；
2. 更新08～16相关SDS及必要ADR；
3. 输出数据模型、API、事件、权限、幂等、并发、异常、补偿和事务差量；
4. 对既有数据和已闭环项目给出兼容/迁移策略，不用默认值伪造新终态或新范围事实；
5. 更新受影响Feature Spec的完整覆盖义务并运行Phase 2校验；
6. 将证据写入本文件后，才可恢复`READY_FOR_PHASE_3_V1.8`。

## 4. 当前放行边界

当前不批准受影响DDL、API、事件或Feature按修订015宣称Ready/Done。未受影响的独立契约可以继续实施，但必须与重开范围隔离。历史迁移、数据切换、SIT/UAT和Release仍由后续门禁独立控制。
