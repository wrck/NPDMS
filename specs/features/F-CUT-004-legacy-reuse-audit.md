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

- 可证明并需同时满足：未删除；`task_id/code/name`有效；任务可解析到同租户`cut_task`的LEGACY_FORWARD身份；内容至少一项非空；创建/更新审计完整。
- 可映射：`task_id`、规范化`code/name`、`pre_check/procedure/verification/rollback/level/remark`及审计时间；内容只作为不可变旧版快照。
- 禁止映射：`status/approved_by/approved_time/approval_opinion/baseline_version`到新审批状态、审批实例或锁定事实；这些字段只留在迁移来源证据。
- 不可迁行保留旧表，并在PLT迁移批次登记明确issue；不得删除、双写或默认补齐。
