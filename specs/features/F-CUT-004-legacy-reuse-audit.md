# F-CUT-004 旧实现复用审计

> 状态：`REVIEW_REQUIRED`
> Requirement：`CUT-04@V1`
> 审计对象：后端、前端、配置、数据/迁移、状态、权限及测试

| 旧资产 | 事实 | 判定 | 处置 |
|---|---|---|---|
| `CutPlanServiceImpl/CutPlanController` | CRUD、物理删除、单级submit/approve/reject/terminate混合 | `PRESERVE_LEGACY / DO_NOT_REUSE_RUNTIME` | 保持旧入口；新建P4应用服务，审批交给CUT-05端口 |
| `CutPlanDO/CutPlanMapper` | 可变单行`pms_cut_plan`，无revision/来源/文件/保障人员 | `COPY_THEN_ENHANCE_CONCEPTS_ONLY` | 仅参考字段概念；新DO/Mapper写正式三表 |
| `CutPlanStatusRules/CutStatusEnum` | 草稿/待评审/通过/驳回/终止混合P4/P5 | `NOT_REUSABLE` | P4仅DRAFT/SUBMITTED/INVALIDATED；审批状态由CUT-05事实提供 |
| 旧`/pms/cut-plan`页面与API | 独立CRUD和审核按钮 | `PRESERVE_LEGACY` | 不修改；新P4嵌入CutoverTask工作台，样式/命名可参考 |
| `pms:cut-plan:*`权限和菜单 | 旧query/create/update/delete/audit | `PRESERVE_LEGACY` | 不复用为新权限；新增任务域最小权限 |
| `pms_cut_plan` | task/code/name/preCheck/procedure/verification/rollback/level等；旧审核字段不可证明分级审批 | `CURRENT_FORWARD_FIELD_LEVEL` | 合格内容形成只读LEGACY_FORWARD revision；审核字段不映射审批事实 |
| 保障人员历史 | `pms_cut_plan`无角色、姓名、任务、电话、到位时间列 | `NEW_ONLY` | 不迁移、不推断 |
| V19/V20测试种子 | 演示旧状态与内容 | `TEST_REFERENCE_ONLY` | 不作为生产迁移或新平台READY事实 |
| 旧自动化测试 | 未发现覆盖正式CUT-04的测试 | `NO_REUSE_EVIDENCE` | 为新服务补聚焦测试，旧回归保持原样 |

## 字段级迁移候选

- 旧表真实列固定为`id/task_id/code/name/pre_check/procedure/verification/rollback/level/status/approved_by/approved_time/approval_opinion/baseline_version/remark/version`及Yudao租户、审计、删除列；不得假定存在文件、方案来源、评估、清单或保障人员列。
- 可证明并需按序同时满足：`deleted=0`；租户、`id/task_id`有效；通过PLT已确认的`pms_cut_task -> cut_task`外部映射解析到同租户`LEGACY_FORWARD`任务；规范化`code`长度1..64、`name`长度1..128、`level`为A/B/C/D；`status`仅允许0..4原样保留；四个正文列至少一项非空；`version`和创建/更新审计完整。
- 根映射：`legacy_plan_id=pms_cut_plan.id`、`legacy_status_raw=status`、`legacy_source_version=version`、`legacy_mapping_version=FCUT004_LEGACY_V1`；精确`LegacyPlanSourceSnapshot`同时保存规范化`code/name/level/remark`并由PlanView返回。新平台编辑方式、等级事实、生命周期、current marker和审批身份全部为空；旧`level`只是来源字段，不成为CUT-02评估事实。
- 步骤映射：非空`pre_check -> PRE_OPERATION/1`、`procedure -> OPERATION/1`、`verification -> POST_BUSINESS_TEST/1`、`rollback -> ROLLBACK/1`；每列最多形成一个不可变步骤，不解析为运行执行状态。
- 禁止映射：`status/approved_by/approved_time/approval_opinion/baseline_version`到新审批状态、审批实例或锁定事实；这些字段只留在迁移来源证据。
- 受控Release导入器只通过`PlatformMigrationEvidenceApi`暂存不可变来源记录并推进至`STAGED_READY`；正常CUT Bean不得连接或直读遗留库。CUT迁移Job在外层事务中claim为`RECONCILING`，原子完成目标写、external mapping/issue/retained和最终计数核对；暂时Provider失败整体回滚到`STAGED_READY`。
- 不合格行、缺失任务映射和目标身份冲突保留旧表并登记稳定issue/retained；不得删除、双写、默认补齐或覆盖目标。最终`COMPLETED`批次不可重算；需要新目标尝试时创建引用原issue的新批次。
