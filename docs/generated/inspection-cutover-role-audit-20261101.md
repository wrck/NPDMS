# 测试环境巡检与割接权限核查（2026-11-01）

范围：172.17.90.178，租户 1；直接角色—启用菜单权限，查询时刻快照。用户多角色权限取并集；超级管理员可能由系统代码特殊授权，不能以直接关联代表其全部能力。无有效关联不等于用户一定无权限。

## 启用权限逐项清单

| 域 | 权限 | 菜单名称 | 直接关联的启用角色 |
| --- | --- | --- | --- |
| 割接 | `pms:cutover-task:approve` | 处理P5审批 | `CUT_RND_APPROVER`、`CUT_SECOND_LINE_APPROVER`、`PROJECT_MANAGER`、`SERVICE_MANAGER`、`probRD`、`probSupporter` |
| 割接 | `pms:cutover-task:create` | 创建割接任务 | `PROJECT_MANAGER`、`SERVICE_MANAGER` |
| 割接 | `pms:cutover-task:download-plan` | 下载割接初稿 | `PROJECT_MANAGER`、`SERVICE_MANAGER` |
| 割接 | `pms:cutover-task:manage-spare` | 管理备件协同 | `PROJECT_MANAGER`、`SERVICE_MANAGER` |
| 割接 | `pms:cutover-task:query` | 割接任务列表、割接任务查询、割接工作台 | `CUT_RND_APPROVER`、`CUT_SECOND_LINE_APPROVER`、`PROJECT_MANAGER`、`SERVICE_MANAGER`、`probSupporter` |
| 割接 | `pms:cutover-task:query-approval` | 查询P5审批 | `CUT_RND_APPROVER`、`CUT_SECOND_LINE_APPROVER`、`PROJECT_MANAGER`、`SERVICE_MANAGER`、`probRD`、`probSupporter` |
| 割接 | `pms:cutover-task:query-closure` | 查询割接闭环 | `PROJECT_MANAGER`、`SERVICE_MANAGER`、`probSupporter` |
| 割接 | `pms:cutover-task:query-plan` | 查询割接方案 | `PROJECT_MANAGER`、`SERVICE_MANAGER` |
| 割接 | `pms:cutover-task:reassign-approval` | 改派P5审批 | 无直接关联 |
| 割接 | `pms:cutover-task:request-collection` | 发起割接设备采集 | 无直接关联 |
| 割接 | `pms:cutover-task:save-assessment` | 保存人工评估 | `PROJECT_MANAGER`、`SERVICE_MANAGER` |
| 割接 | `pms:cutover-task:save-checklist` | 保存割接采集清单 | `PROJECT_MANAGER`、`SERVICE_MANAGER` |
| 割接 | `pms:cutover-task:save-closure` | 保存割接闭环 | `PROJECT_MANAGER`、`SERVICE_MANAGER` |
| 割接 | `pms:cutover-task:save-plan` | 保存割接方案 | `PROJECT_MANAGER`、`SERVICE_MANAGER` |
| 割接 | `pms:cutover-task:submit-assessment` | 提交人工评估 | `PROJECT_MANAGER`、`SERVICE_MANAGER` |
| 割接 | `pms:cutover-task:submit-checklist` | 提交割接采集清单 | `PROJECT_MANAGER`、`SERVICE_MANAGER` |
| 割接 | `pms:cutover-task:submit-closure` | 提交割接闭环 | `PROJECT_MANAGER`、`SERVICE_MANAGER` |
| 割接 | `pms:cutover-task:submit-plan` | 提交割接方案 | `PROJECT_MANAGER`、`SERVICE_MANAGER` |
| 巡检 | `pms:inspection-feedback:analyze` | 误报反馈分析 | 无直接关联 |
| 巡检 | `pms:inspection-feedback:confirm` | 确认误报反馈 | 无直接关联 |
| 巡检 | `pms:inspection-feedback:details` | 误报反馈明细 | 无直接关联 |
| 巡检 | `pms:inspection-feedback:export` | 导出误报反馈 | 无直接关联 |
| 巡检 | `pms:inspection-feedback:query` | 误报反馈查询 | 无直接关联 |
| 巡检 | `pms:inspection-report:generate` | 生成与上传巡检报告 | `SERVICE_MANAGER` |
| 巡检 | `pms:inspection-rule:disable` | 巡检规则停用 | 无直接关联 |
| 巡检 | `pms:inspection-rule:manage` | 巡检规则维护 | 无直接关联 |
| 巡检 | `pms:inspection-rule:publish` | 巡检规则发布 | 无直接关联 |
| 巡检 | `pms:inspection-rule:query` | 巡检规则、巡检规则查询 | `SERVICE_MANAGER` |
| 巡检 | `pms:inspection-rule:security-review` | 巡检规则安全审核 | 无直接关联 |
| 巡检 | `pms:inspection-rule:select` | 巡检规则选择 | `SERVICE_MANAGER` |
| 巡检 | `pms:inspection-task:annotate` | 巡检问题标注 | `SERVICE_MANAGER` |
| 巡检 | `pms:inspection-task:archive` | 确认巡检归档 | `SERVICE_MANAGER` |
| 巡检 | `pms:inspection-task:assign` | 分配巡检负责人 | `SERVICE_MANAGER` |
| 巡检 | `pms:inspection-task:cancel` | 取消巡检任务 | `SERVICE_MANAGER` |
| 巡检 | `pms:inspection-task:create` | 手工创建巡检任务 | `SERVICE_MANAGER` |
| 巡检 | `pms:inspection-task:execute` | 巡检执行 | `SERVICE_MANAGER` |
| 巡检 | `pms:inspection-task:prepare` | 巡检准备 | `SERVICE_MANAGER` |
| 巡检 | `pms:inspection-task:query` | 巡检任务、巡检任务查询 | `PROJECT_MANAGER`、`SERVICE_MANAGER`、`probSupporter` |
| 巡检 | `pms:inspection-task:request-archive` | 申请巡检归档 | `SERVICE_MANAGER` |
| 巡检跟踪 | `pms:service-issue:confirm` | 巡检待办确认闭环 | `SERVICE_MANAGER` |
| 巡检跟踪 | `pms:service-issue:query` | 巡检待办查询 | `PROJECT_MANAGER`、`SERVICE_MANAGER` |
| 巡检跟踪 | `pms:service-issue:remediate` | 巡检待办处理 | `SERVICE_MANAGER` |

## 全部角色直接授权概览

| 角色 | 状态 | 割接任务权限数 | 巡检及跟踪权限数 |
| --- | --- | ---: | ---: |
| 管理员（`admin`） | 启用 | 0 | 0 |
| 办事处主任（`areaLeader`） | 启用 | 0 | 0 |
| 回访人员（`callbackPer`） | 启用 | 0 | 0 |
| 普通角色（`common`） | 启用 | 0 | 0 |
| 产品组件管理员（`componentAdmin`） | 启用 | 0 | 0 |
| 割接研发审批（`CUT_RND_APPROVER`） | 启用 | 3 | 0 |
| 割接二线审批（`CUT_SECOND_LINE_APPROVER`） | 启用 | 3 | 0 |
| 数据查询角色（`dataViewer`） | 启用 | 0 | 0 |
| 项目转包结算人员（`dispatchSettleStaff`） | 启用 | 0 | 0 |
| 工程管理部（`engineeManager`） | 启用 | 0 | 0 |
| 工程管理部主管（`engineeManagerLeader`） | 启用 | 0 | 0 |
| 工程管理部方案复审（`ENGINEERING_MANAGEMENT_REVIEW`） | 启用 | 0 | 0 |
| 财务AP（`financialAP`） | 启用 | 0 | 0 |
| 财务人员（`financialStaff`） | 启用 | 0 | 0 |
| 售前测试专员（`presalesStaff`） | 启用 | 0 | 0 |
| 技术公告管理员（`probAdmin`） | 启用 | 0 | 0 |
| 研发人员（`probRD`） | 启用 | 2 | 0 |
| 技术支持人员（`probSupporter`） | 启用 | 4 | 1 |
| 项目经理（`PROJECT_MANAGER`） | 启用 | 16 | 2 |
| 项目管理员（`projectAdmin`） | 启用 | 0 | 0 |
| 安服质量监督员（`projectAFQC`） | 启用 | 0 | 0 |
| 项目区域负责人（`projectAreaManager`） | 启用 | 0 | 0 |
| 项目成员（`projectMember`） | 启用 | 0 | 0 |
| 项目售前（`projectPreSales`） | 启用 | 0 | 0 |
| 子项目管理员（`projectSubAdmin`） | 启用 | 0 | 0 |
| 项目查阅员（`projectViewer`） | 启用 | 0 | 0 |
| 研发质量监督员（`projectYFQC`） | 启用 | 0 | 0 |
| 销售代表（`SALES_REPRESENTATIVE`） | 启用 | 0 | 0 |
| 服务经理（`SERVICE_MANAGER`） | 启用 | 16 | 15 |
| 超级管理员（`super_admin`） | 启用 | 0 | 0 |
| 普通用户（`user`） | 启用 | 0 | 0 |
| 用户管理员（`userManager`） | 启用 | 0 | 0 |
| 项目维保回访专员（`warrantyCallbacker`） | 启用 | 0 | 0 |

## 对照与裁决建议

- 项目经理：割接可查询、创建和办理相关阶段；巡检目前仅任务查询及跟踪查询。此前增配创建后发现创建路径仅检查项目查看范围，已撤销该增配；应先确定并修复创建权限边界，再恢复用户明确批准的创建权限。准备、执行、生成报告和申请归档仍需结合实际职责裁决；最终归档按正式规则由授权服务经理执行。
- 服务经理：巡检具有查询、创建、准备、执行、分配、取消、报告、标注和归档权限；割接具备任务办理相关权限。与项目经理不同的归档管理权限有正式规则依据。
- 技术支持人员：割接有任务查询、P5 查询与审核及闭环查询；巡检仅任务查询。巡检没有直接对应 P5 角色审批的同名权限；不得据此直接赋予执行或归档。
- 研发人员：割接仅有 P5 查询与审核；巡检无直接任务权限。巡检规则审核使用专用权限，研发角色与规则审核资格的对应关系尚无足够证据，不自动授权。
- 割接二线审批和研发审批角色有割接查询/审核；不自动映射巡检角色。巡检一线/管理员为规格角色类别，测试库中未发现明确同名角色，不能仅根据名称自行建立映射。
- 其他角色的直接授权见上表；零直接授权不排除合并其他角色或超级管理员语义。

## 数据范围差异与验收边界

- 本地 `InspectionTaskService.create` 调用 `InspectionAccessService.project(..., true)`；该服务的初查和项目模块 `lockAndRevalidate` 均使用 `ACTION_VIEW`。本地割接任务创建及项目选项使用 `ACTION_EDIT`。仅凭源码不能确认 178 的实际运行包版本，也不能断言已发生越权。
- 巡检任务已有对象级权限、负责人或服务经理身份、版本和终态检查。角色菜单授权不能替代项目范围及对象守卫。
- 使用 l04986 实际浏览器可进入新建并选择项目 16201426020402N-0，设备弹窗显示 15 条已发货设备；该账号兼具项目经理与服务经理角色，不能单独证明项目经理。未保存任务。
- 该快照在撤销项目经理创建授权之后生成；角色和任务数据可能随时间变化。
