# SDS Phase 2 显式需求契约映射

> 文档状态：`BASELINE`
> 适用基线：PRD V1.7（`docs/baseline/prd-v1.7.md`）
> Requirement ID：附录 A.1 全部 104 项 V1/V2 正式需求
> Owner：SDS Phase 2 追溯治理；具体业务 Owner 以 `requirement-matrix.md` 为准
> Phase 3验证注记状态：`IN_REVIEW`（不改变已批准的Phase 2契约基线）

本文件逐项声明可实施的数据对象、表、API、事件/集成/文件、工作流和授权落点。相同基础契约可被多个相关 Requirement 复用，但每个 Requirement 必须显式登记；`N/A` 必须说明为何该类契约不适用。

### PM-01

- 需求名称：项目创建与指派
- 数据对象：Project、ProjectMemberAssignment
- 数据表：proj_project、proj_project_member_assignment
- API：/projects、/projects/{id}/actions/assign-manager
- 事件：ProjectCreated
- 外部集成：CRM、ERP
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：项目创建、来源匹配与指派守卫
- 授权与数据范围：ProjectTreeScope；来源字段只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### PM-02

- 需求名称：主子项目管理与进度汇总
- 数据对象：ProjectHierarchy、ProjectAncestorProjection
- 数据表：proj_project、proj_project_tree_path、proj_project_tree_change
- API：/projects/{id}/tree、/projects/{id}/actions/move
- 事件：ProjectTreeChanged
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：无环移动、完整投影版本切换
- 授权与数据范围：ProjectTreeScope；后代范围服务端计算
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据

### PM-03

- 需求名称：项目模板与阶段门禁
- 数据对象：ProjectTemplate、ProjectStageSnapshot
- 数据表：proj_project_template_revision、proj_project_stage_snapshot
- API：/project-templates
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：模板发布、实例冻结和阶段门禁
- 授权与数据范围：项目模板维护权限；项目阶段范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录

### PM-04

- 需求名称：多级项目权限与拆分
- 数据对象：ProjectHierarchy、ProjectAncestorProjection
- 数据表：proj_project、proj_project_tree_path、proj_project_tree_change
- API：/projects/{id}/tree、/projects/{id}/actions/move
- 事件：ProjectTreeChanged
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：无环移动、完整投影版本切换
- 授权与数据范围：ProjectTreeScope；后代范围服务端计算
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据

### PM-05

- 需求名称：借货项目转销管理
- 数据对象：BorrowedProjectConversion、ConversionItem、ConversionDeviceDisposition
- 数据表：proj_project_conversion、proj_project_conversion_item、proj_project_conversion_device
- API：/project-conversions、/project-conversions/{id}/actions/retry-failed
- 事件：ProjectConversionCompleted、ProjectConversionPartiallyFailed
- 外部集成：CRM、ERP
- 文件契约：FileArtifact
- 工作流/状态：处理中→部分失败/待处理→已完成；全部成功后源项目只读归档
- 授权与数据范围：同时具备源/目标项目管理权限；原敏感对象权限继续生效
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试；转换部分失败、逐项重试、源项目只读归档测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录；转换批次、逐项结果及源/目标一致性清单

### PM-06

- 需求名称：多期项目合并管理
- 数据对象：MultiPhaseProjectGroup、MultiPhaseProjectMember、CrossPhaseContentReference
- 数据表：proj_multi_phase_project_group、proj_multi_phase_project_member、proj_project_cross_phase_reference
- API：/project-phase-groups、/project-phase-groups/{id}/actions/add-phase、/project-phase-groups/{id}/actions/derive-content
- 事件：ProjectPhaseGroupChanged
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：群组成员增删、唯一期次、无环和派生版本
- 授权与数据范围：同时有权的期次可维护；查询按各期权限裁剪
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；多期群组无环、唯一期次、跨期派生版本测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；群组树快照、派生来源与无环校验记录

### PM-07

- 需求名称：项目业务属性识别与分类
- 数据对象：Project
- 数据表：proj_project
- API：/projects/{id}/actions/classify
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：自动识别结果确认与留痕
- 授权与数据范围：项目管理范围；来源证据只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录

### PM-08

- 需求名称：服务经理自动指派
- 数据对象：ProjectMemberAssignment
- 数据表：proj_project_member_assignment
- API：/projects/{id}/actions/assign-manager
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：V1手动指派、V2规则候选确认
- 授权与数据范围：项目管理范围；仅PRD角色
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录

### PM-09

- 需求名称：人员批量变更
- 数据对象：ProjectMemberAssignment
- 数据表：proj_project_member_assignment
- API：/projects/{id}/members:batch-change
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：批量逐项变更并保留有效区间
- 授权与数据范围：项目管理范围；逐项目校验
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录

### PM-10

- 需求名称：项目回退与关闭
- 数据对象：Project、ProjectStageSnapshot
- 数据表：proj_project、proj_project_stage_snapshot
- API：/projects/{id}/actions/rollback、/projects/{id}/actions/close
- 事件：ProjectStageChanged、ProjectClosed
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：受控回退与闭环完成后关闭
- 授权与数据范围：ProjectTreeScope；状态命令权限与门禁
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据

### PM-11

- 需求名称：项目任务管理
- 数据对象：ProjectTask、TaskAncestorProjection、TaskDependency
- 数据表：proj_project_task、proj_task_tree_path、proj_task_dependency
- API：/projects/{id}/tasks、/project-tasks/{id}/actions/move
- 事件：TaskAssigned、TaskCompleted
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：任务任意层级移动、状态迁移和依赖守卫
- 授权与数据范围：ProjectTreeScope；任务数据范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；5万节点、2000直接子节点、深度30任务树查询/移动测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；任务树数据集版本与性能报告

### PRE-01

- 需求名称：工期管理与变更审批
- 数据对象：ConstructionPlan
- 数据表：sol_construction_plan、sol_construction_plan_revision、sol_construction_plan_change
- API：/construction-plans、/{id}/actions/{submit|approve|reject}
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：工期基线、变更申请与审批
- 授权与数据范围：ProjectStageScope；计划审批节点
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### PRE-02

- 需求名称：工勘分工信息采集
- 数据对象：Preparation、DynamicFormInstance
- 数据表：sol_preparation、sol_dynamic_form_instance
- API：/preparations、/{id}/actions/{submit|confirm|return}
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：工勘/需求填写、提交、确认和退回
- 授权与数据范围：ProjectStageScope；字段与文件权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### PRE-03

- 需求名称：物料换货流程
- 数据对象：Preparation
- 数据表：sol_preparation、ast_asset_sync_item
- API：/preparations
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：ERP
- 文件契约：FileArtifact
- 工作流/状态：换货申请、外部处理映射与恢复对账
- 授权与数据范围：ProjectStageScope；物料范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### PRE-04

- 需求名称：需求分析在线填写
- 数据对象：Preparation、DynamicFormInstance
- 数据表：sol_preparation、sol_dynamic_form_instance
- API：/preparations、/{id}/actions/{submit|confirm|return}
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：工勘/需求填写、提交、确认和退回
- 授权与数据范围：ProjectStageScope；字段与文件权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### PRE-05

- 需求名称：工程交底书自动生成
- 数据对象：Preparation、FileArtifact
- 数据表：sol_preparation、plt_file_artifact、plt_file_version
- API：/preparations、/files:init-upload
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：交底书生成、版本提交和确认
- 授权与数据范围：ProjectStageScope、FileBusinessScope
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### PLN-01

- 需求名称：施工计划自动推算
- 数据对象：ConstructionPlan
- 数据表：sol_construction_plan、sol_construction_plan_revision
- API：/schedules、/{id}/actions/{calculate|apply}
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：计划计算候选、预警和显式应用
- 授权与数据范围：ProjectStageScope；计划只读/维护分离
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录

### PLN-02

- 需求名称：工期紧张预警
- 数据对象：ConstructionPlan
- 数据表：sol_construction_plan、sol_construction_plan_revision
- API：/schedules、/{id}/actions/{calculate|apply}
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：计划计算候选、预警和显式应用
- 授权与数据范围：ProjectStageScope；计划只读/维护分离
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录

### PLN-03

- 需求名称：超期标红与统计
- 数据对象：ConstructionPlan
- 数据表：sol_construction_plan、sol_construction_plan_revision
- API：/schedules、/{id}/actions/{calculate|apply}
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：计划计算候选、预警和显式应用
- 授权与数据范围：ProjectStageScope；计划只读/维护分离
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录

### PLN-04

- 需求名称：施工计划审批
- 数据对象：ConstructionPlan
- 数据表：sol_construction_plan、sol_construction_plan_revision
- API：/construction-plans、/{id}/actions/{submit|approve|reject}
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：施工计划提交、审批、驳回与换版
- 授权与数据范围：ProjectStageScope；审批人按PRD
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### SCH-01

- 需求名称：实施方案在线编审
- 数据对象：Solution
- 数据表：sol_solution、sol_solution_revision、sol_solution_review
- API：/solutions、/{id}/revisions、/{id}/actions/{submit|approve|reject|publish}
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：方案导入/编审、重大复审和发布版本
- 授权与数据范围：ProjectStageScope；方案审批与文件权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### SCH-02

- 需求名称：客户方案导入识别
- 数据对象：Solution
- 数据表：sol_solution、sol_solution_revision、sol_solution_review
- API：/solutions、/{id}/revisions、/{id}/actions/{submit|approve|reject|publish}
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：方案导入/编审、重大复审和发布版本
- 授权与数据范围：ProjectStageScope；方案审批与文件权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### SCH-03

- 需求名称：配置脚本上传解析
- 数据对象：Solution、FileArtifact
- 数据表：sol_solution_revision、plt_file_artifact、plt_file_version
- API：/solutions、/files:init-upload
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：配置脚本上传、解析、发布和版本冻结
- 授权与数据范围：ProjectStageScope、FileBusinessScope；已发布模板使用权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### SCH-04

- 需求名称：标准化模板管理
- 数据对象：Solution
- 数据表：sol_solution、sol_solution_revision、sol_solution_review
- API：/solutions、/{id}/revisions、/{id}/actions/{submit|approve|reject|publish}
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：方案导入/编审、重大复审和发布版本
- 授权与数据范围：ProjectStageScope；方案审批与文件权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### SCH-05

- 需求名称：方案审核与重大复审
- 数据对象：Solution
- 数据表：sol_solution、sol_solution_revision、sol_solution_review
- API：/solutions、/{id}/revisions、/{id}/actions/{submit|approve|reject|publish}
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：方案导入/编审、重大复审和发布版本
- 授权与数据范围：ProjectStageScope；方案审批与文件权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### EXE-01

- 需求名称：到货签收
- 数据对象：ArrivalAcceptance
- 数据表：imp_arrival_acceptance、imp_arrival_line、imp_arrival_difference
- API：/arrival-acceptances
- 事件：ArrivalAccepted
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：草稿、差异处理、项目经理最终确认
- 授权与数据范围：ImplementationProjectBatchScope；项目经理确认
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### EXE-02

- 需求名称：硬件安装记录
- 数据对象：InstallationRecord
- 数据表：imp_installation_record、imp_installation_item、imp_installation_evidence
- API：/installation-records
- 事件：InstallationConfirmed
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：提交、项目经理确认/退回和整改
- 授权与数据范围：ImplementationProjectDeviceScope；设备归属与项目权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### EXE-03

- 需求名称：配置Log采集解析
- 数据对象：ConfigurationCollectionResult、DeviceComponentRelation、CollectionTask
- 数据表：imp_configuration_collection_result、imp_configuration_collection_parse_attempt、imp_configuration_component_candidate、ast_device_component_relation、plt_collection_task
- API：/configuration-results、/devices/{id}/component-relations、/collection-tasks
- 事件：ConfigurationParsed、DeviceComponentRelationChanged、CollectionResultConsumed
- 外部集成：现有采集平台子应用
- 文件契约：FileArtifact
- 工作流/状态：采集回调、框板解析、待匹配/人工绑定和业务消费确认
- 授权与数据范围：ImplementationProjectDeviceCollectionScope、BusinessObjectDeviceCredentialScope；设备关系维护权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### EXE-04

- 需求名称：业务联调配置收集
- 数据对象：JointDebuggingResult、CollectionTask
- 数据表：imp_joint_debugging_result、imp_joint_debugging_item、plt_collection_task
- API：/debugging-results、/collection-tasks
- 事件：JointDebuggingCompleted、CollectionResultConsumed
- 外部集成：现有采集平台子应用
- 文件契约：FileArtifact
- 工作流/状态：采集回调、联调结论和问题引用
- 授权与数据范围：ImplementationProjectDeviceCollectionScope、BusinessObjectDeviceCredentialScope
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### EXE-05

- 需求名称：单机风险标记
- 数据对象：ImplementationRisk
- 数据表：imp_risk、imp_risk_treatment
- API：/implementation-risks
- 事件：ImplementationRiskRaised/Closed
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：风险提出、处置和关闭
- 授权与数据范围：ImplementationProjectDeviceScope；风险范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### EXE-06

- 需求名称：割接上线门禁
- 数据对象：ImplementationReadinessSnapshot
- 数据表：proj_project_stage_snapshot
- API：/implementation-readiness/{projectId}
- 事件：ImplementationReadinessSnapshotPublished
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：就绪门禁汇总、快照发布与CUT消费
- 授权与数据范围：ImplementationProjectCutoverScope
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据

### ACC-01

- 需求名称：现场培训电子化
- 数据对象：Acceptance
- 数据表：acc_acceptance、acc_acceptance_item、acc_confirmation
- API：/acceptances
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：培训/报告提交、确认和问题留痕
- 授权与数据范围：ProjectStageScope、FileBusinessScope
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### ACC-02

- 需求名称：满意度调查电子化
- 数据对象：SatisfactionCollection
- 数据表：acc_satisfaction_collection_task、acc_satisfaction_questionnaire、acc_satisfaction_response、acc_satisfaction_result
- API：/satisfaction-tasks、/satisfaction-questionnaires/{token}/responses、/satisfaction-results
- 事件：SatisfactionTaskCreated、SatisfactionResultRecorded
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：冻结模板→指派→客户提交→判定→整改后新版本重收→归档
- 授权与数据范围：ProjectStageScope；客户一次性实例范围；答案/签字不可改写
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### ACC-03

- 需求名称：验收报告管理
- 数据对象：Acceptance
- 数据表：acc_acceptance、acc_acceptance_item、acc_confirmation
- API：/acceptances
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：培训/报告提交、确认和问题留痕
- 授权与数据范围：ProjectStageScope、FileBusinessScope
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### ACC-04

- 需求名称：交付件归档管理
- 数据对象：DeliveryArtifact
- 数据表：acc_delivery_artifact、acc_artifact_review、acc_archive_record
- API：/delivery-artifacts
- 事件：ArtifactAccepted/Archived
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：齐套检查、审核和归档分离
- 授权与数据范围：ProjectStageScope、FileBusinessScope；ACC归档
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CLO-01

- 需求名称：闭环条件校验
- 数据对象：ProjectClosure、ClosureGateSnapshot、SatisfactionCollection
- 数据表：acc_project_closure、acc_closure_gate_snapshot、acc_closure_review、acc_satisfaction_result
- API：/closure-gates/{projectId}、/project-closures
- 事件：ProjectClosureCompleted
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：门禁校验、冻结流程审批、整改和闭环；不创建回访节点
- 授权与数据范围：ProjectStageScope；全部后代项目门禁与审批范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CLO-02

- 需求名称：闭环审批流程
- 数据对象：ProjectClosure、ClosureGateSnapshot、SatisfactionCollection
- 数据表：acc_project_closure、acc_closure_gate_snapshot、acc_closure_review、acc_satisfaction_result
- API：/closure-gates/{projectId}、/project-closures
- 事件：ProjectClosureCompleted
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：门禁校验、冻结流程审批、整改和闭环；不创建回访节点
- 授权与数据范围：ProjectStageScope；全部后代项目门禁与审批范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-11

- 需求名称：割接保障任务
- 数据对象：CutoverSupportTask、ResponsibilityInterval
- 数据表：cut_cutover_support_task、cut_cutover_support_responsibility_interval、cut_cutover_support_history
- API：/cutover-support-tasks、/{id}/actions/{assign|start|takeover|transfer|suspend|resume|close}
- 事件：CutoverSupportTaskChanged、CutoverSupportClosed
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：状态机版本冻结；派发、处理、接管、转交、挂起、恢复和证据门禁关闭
- 授权与数据范围：CutoverTaskScope；当前责任区间；管理员不得绕过核心门禁
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### SUB-01

- 需求名称：转包申请管理
- 数据对象：SubcontractRequest
- 数据表：res_subcontract_request
- API：/subcontract-requests
- 事件：SubcontractApproved
- 外部集成：OA
- 文件契约：FileArtifact
- 工作流/状态：平台内转包审批、价格审批与版本冻结
- 授权与数据范围：OrganizationSupplierScope；项目/供应商范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### SUB-02

- 需求名称：转包流程配置
- 数据对象：SubcontractRequest
- 数据表：res_subcontract_request
- API：/subcontract-requests
- 事件：SubcontractApproved
- 外部集成：OA
- 文件契约：FileArtifact
- 工作流/状态：平台内转包审批、价格审批与版本冻结
- 授权与数据范围：OrganizationSupplierScope；项目/供应商范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### SUB-03

- 需求名称：转包付款满意度门禁
- 数据对象：PaymentGate、SatisfactionCollection
- 数据表：res_payment_gate、acc_satisfaction_result
- API：/payment-gates
- 事件：PaymentGateChanged
- 外部集成：财务系统
- 文件契约：FileArtifact
- 工作流/状态：付款前置满意度事实、批准版本和财务确认
- 授权与数据范围：OrganizationSupplierScope；付款门禁权限；满意度只读引用
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### SUB-04

- 需求名称：付款信息管理
- 数据对象：PaymentGate、SatisfactionCollection
- 数据表：res_payment_gate、acc_satisfaction_result
- API：/payment-gates
- 事件：PaymentGateChanged
- 外部集成：财务系统
- 文件契约：FileArtifact
- 工作流/状态：付款前置满意度事实、批准版本和财务确认
- 授权与数据范围：OrganizationSupplierScope；付款门禁权限；满意度只读引用
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### SUB-05

- 需求名称：转包价格审批
- 数据对象：SubcontractRequest
- 数据表：res_subcontract_request
- API：/subcontract-requests
- 事件：SubcontractApproved
- 外部集成：OA
- 文件契约：FileArtifact
- 工作流/状态：平台内转包审批、价格审批与版本冻结
- 授权与数据范围：OrganizationSupplierScope；项目/供应商范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### CUS-01

- 需求名称：用户资产库
- 数据对象：Customer、CustomerContact、CustomerRelationshipSnapshot
- 数据表：cus_customer、cus_customer_contact、cus_project_customer_contact_relation
- API：/customers、/customer-contacts、/customer-relationships
- 事件：CustomerMerged、MasterDataSynchronized
- 外部集成：CRM
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：客户同步、临时客户受控合并和联系人关系
- 授权与数据范围：OrganizationCustomerScope；CRM字段只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### CUS-02

- 需求名称：服务等级管理
- 数据对象：Customer、CustomerContact、CustomerRelationshipSnapshot
- 数据表：cus_customer、cus_customer_contact、cus_project_customer_contact_relation
- API：/customers、/customer-contacts、/customer-relationships
- 事件：CustomerMerged、MasterDataSynchronized
- 外部集成：CRM
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：客户同步、临时客户受控合并和联系人关系
- 授权与数据范围：OrganizationCustomerScope；CRM字段只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### CUS-03

- 需求名称：客户信息管理CURD
- 数据对象：Customer、CustomerContact、CustomerRelationshipSnapshot
- 数据表：cus_customer、cus_customer_contact、cus_project_customer_contact_relation
- API：/customers、/customer-contacts、/customer-relationships
- 事件：CustomerMerged、MasterDataSynchronized
- 外部集成：CRM
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：客户同步、临时客户受控合并和联系人关系
- 授权与数据范围：OrganizationCustomerScope；CRM字段只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### CUS-04

- 需求名称：项目联系人管理
- 数据对象：Customer、CustomerContact、CustomerRelationshipSnapshot
- 数据表：cus_customer、cus_customer_contact、cus_project_customer_contact_relation
- API：/customers、/customer-contacts、/customer-relationships
- 事件：CustomerMerged、MasterDataSynchronized
- 外部集成：CRM
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：客户同步、临时客户受控合并和联系人关系
- 授权与数据范围：OrganizationCustomerScope；CRM字段只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### EQP-01

- 需求名称：设备序列号档案
- 数据对象：Device、DeviceArchive、DeviceComponentRelation、DeviceCurrentAssignment
- 数据表：ast_device、ast_device_component_relation、ast_device_current_assignment、ast_device_assignment_history
- API：/devices、/devices/{id}/archive、/devices/{id}/component-relations、/devices/{id}/assignment-history
- 事件：DeviceAssigned、DeviceComponentRelationChanged
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：设备档案、配置Log引用、框板关系、扫码和唯一归属
- 授权与数据范围：ProjectDeviceScope；当前归属、框板关系维护与祖先范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### EQP-02

- 需求名称：配置Log管理
- 数据对象：Device、DeviceArchive、DeviceComponentRelation、DeviceCurrentAssignment
- 数据表：ast_device、ast_device_component_relation、ast_device_current_assignment、ast_device_assignment_history
- API：/devices、/devices/{id}/archive、/devices/{id}/component-relations、/devices/{id}/assignment-history
- 事件：DeviceAssigned、DeviceComponentRelationChanged
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：设备档案、配置Log引用、框板关系、扫码和唯一归属
- 授权与数据范围：ProjectDeviceScope；当前归属、框板关系维护与祖先范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### EQP-03

- 需求名称：设备档案库
- 数据对象：Device、DeviceArchive、DeviceComponentRelation、DeviceCurrentAssignment
- 数据表：ast_device、ast_device_component_relation、ast_device_current_assignment、ast_device_assignment_history
- API：/devices、/devices/{id}/archive、/devices/{id}/component-relations、/devices/{id}/assignment-history
- 事件：DeviceAssigned、DeviceComponentRelationChanged
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：设备档案、配置Log引用、框板关系、扫码和唯一归属
- 授权与数据范围：ProjectDeviceScope；当前归属、框板关系维护与祖先范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### EQP-04

- 需求名称：设备信息MES同步
- 数据对象：AssetSyncSnapshot、Device
- 数据表：ast_asset_sync_batch、ast_asset_sync_item、ast_device
- API：/devices
- 事件：MasterDataSynchronized
- 外部集成：MES
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：MES来源版本幂等同步与冲突隔离
- 授权与数据范围：ProjectDeviceScope；MES字段只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### EQP-05

- 需求名称：一码通扫码
- 数据对象：Device、DeviceArchive、DeviceComponentRelation、DeviceCurrentAssignment
- 数据表：ast_device、ast_device_component_relation、ast_device_current_assignment、ast_device_assignment_history
- API：/devices、/devices/{id}/archive、/devices/{id}/component-relations、/devices/{id}/assignment-history
- 事件：DeviceAssigned、DeviceComponentRelationChanged
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：设备档案、配置Log引用、框板关系、扫码和唯一归属
- 授权与数据范围：ProjectDeviceScope；当前归属、框板关系维护与祖先范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### EQP-07

- 需求名称：项目问题单页面
- 数据对象：Device、DeviceArchive、DeviceComponentRelation、DeviceCurrentAssignment
- 数据表：ast_device、ast_device_component_relation、ast_device_current_assignment、ast_device_assignment_history
- API：/devices、/devices/{id}/archive、/devices/{id}/component-relations、/devices/{id}/assignment-history
- 事件：DeviceAssigned、DeviceComponentRelationChanged
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：设备档案、配置Log引用、框板关系、扫码和唯一归属
- 授权与数据范围：ProjectDeviceScope；当前归属、框板关系维护与祖先范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### RPT-02

- 需求名称：项目状态统计
- 数据对象：MetricSnapshot
- 数据表：ana_metric_snapshot
- API：/analytics/metrics
- 事件：MetricSnapshotPublished
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：项目状态指标口径计算、快照发布和水位展示
- 授权与数据范围：OrganizationReportScope；字段级脱敏
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据

### CUT-01

- 需求名称：割接任务管理
- 数据对象：CutoverTask、CutoverAssessment
- 数据表：cut_task、cut_assessment
- API：/cutover-tasks、/cutover-tasks/{id}/assessment
- 事件：CutoverApproved
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：任务创建、分级评估、风险/调研矩阵
- 授权与数据范围：CutoverTaskScope；项目/设备范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-02

- 需求名称：割接分级评估
- 数据对象：CutoverTask、CutoverAssessment
- 数据表：cut_task、cut_assessment
- API：/cutover-tasks、/cutover-tasks/{id}/assessment
- 事件：CutoverApproved
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：任务创建、分级评估、风险/调研矩阵
- 授权与数据范围：CutoverTaskScope；项目/设备范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-03

- 需求名称：割接采集清单动态多维绑定生成
- 数据对象：CutoverPlan
- 数据表：cut_plan_revision、cut_step
- API：/cutover-tasks/{id}/plan-revisions
- 事件：CutoverApproved
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：动态清单、方案编审、分级审批和版本冻结
- 授权与数据范围：CutoverTaskScope；分级审批权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-04

- 需求名称：割接方案编审
- 数据对象：CutoverPlan
- 数据表：cut_plan_revision、cut_step
- API：/cutover-tasks/{id}/plan-revisions
- 事件：CutoverApproved
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：动态清单、方案编审、分级审批和版本冻结
- 授权与数据范围：CutoverTaskScope；分级审批权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-05

- 需求名称：割接分级审批
- 数据对象：CutoverPlan
- 数据表：cut_plan_revision、cut_step
- API：/cutover-tasks/{id}/plan-revisions
- 事件：CutoverApproved
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：动态清单、方案编审、分级审批和版本冻结
- 授权与数据范围：CutoverTaskScope；分级审批权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-06

- 需求名称：割接执行闭环
- 数据对象：CutoverExecution、CollectionTask
- 数据表：cut_execution、cut_execution_step、cut_observation、plt_collection_task
- API：/cutover-tasks/{id}/actions/start、/cutover-executions/{id}/steps/{stepId}/actions/{start|complete|fail}
- 事件：CollectionResultConsumed、CutoverCompleted
- 外部集成：现有采集平台子应用、ITR
- 文件契约：FileArtifact
- 工作流/状态：门禁、步骤执行、回退、观察和业务消费
- 授权与数据范围：CutoverTaskScope、BusinessObjectDeviceCredentialScope
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-07

- 需求名称：割接后台配置
- 数据对象：CutoverPlan
- 数据表：cut_plan_revision、cut_step
- API：/cutover-tasks/{id}/plan-revisions
- 事件：CutoverApproved
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：动态清单、方案编审、分级审批和版本冻结
- 授权与数据范围：CutoverTaskScope；分级审批权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-08

- 需求名称：割接备件集成
- 数据对象：CutoverTask
- 数据表：cut_task、ast_asset_sync_item
- API：/cutover-tasks
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：备件系统
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：备件申请映射、回调、门禁和对账
- 授权与数据范围：CutoverTaskScope；外部备件范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；脱敏请求响应、幂等键、重试/对账与降级记录

### CUT-09

- 需求名称：割接风险项关联矩阵
- 数据对象：CutoverTask、CutoverAssessment
- 数据表：cut_task、cut_assessment
- API：/cutover-tasks、/cutover-tasks/{id}/assessment
- 事件：CutoverApproved
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：任务创建、分级评估、风险/调研矩阵
- 授权与数据范围：CutoverTaskScope；项目/设备范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-10

- 需求名称：割接调研项关联矩阵
- 数据对象：CutoverTask、CutoverAssessment
- 数据表：cut_task、cut_assessment
- API：/cutover-tasks、/cutover-tasks/{id}/assessment
- 事件：CutoverApproved
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：任务创建、分级评估、风险/调研矩阵
- 授权与数据范围：CutoverTaskScope；项目/设备范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### INS-01

- 需求名称：巡检任务管理
- 数据对象：InspectionTask、CollectionTask
- 数据表：srv_inspection_task、srv_inspection_task_rule_snapshot、plt_collection_task
- API：/inspection-tasks、/collection-tasks
- 事件：InspectionDispatched、InspectionCompleted、CollectionResultConsumed
- 外部集成：现有采集平台子应用
- 文件契约：FileArtifact
- 工作流/状态：方式选择、预检/执行、业务消费和归档门禁
- 授权与数据范围：AssignedProjectDeviceScope、BusinessObjectDeviceCredentialScope
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### INS-02

- 需求名称：双巡检方式选择与执行
- 数据对象：InspectionTask、CollectionTask
- 数据表：srv_inspection_task、srv_inspection_task_rule_snapshot、plt_collection_task
- API：/inspection-tasks、/collection-tasks
- 事件：InspectionDispatched、InspectionCompleted、CollectionResultConsumed
- 外部集成：现有采集平台子应用
- 文件契约：FileArtifact
- 工作流/状态：方式选择、预检/执行、业务消费和归档门禁
- 授权与数据范围：AssignedProjectDeviceScope、BusinessObjectDeviceCredentialScope
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### INS-03

- 需求名称：巡检规则管理
- 数据对象：InspectionRule
- 数据表：srv_inspection_rule、srv_inspection_rule_revision
- API：/inspection-rules、/{id}/revisions
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：规则配置、发布版本和任务冻结
- 授权与数据范围：AssignedProjectDeviceScope；规则维护/使用分离
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录

### INS-04

- 需求名称：巡检连通性预检
- 数据对象：InspectionTask、CollectionTask
- 数据表：srv_inspection_task、srv_inspection_task_rule_snapshot、plt_collection_task
- API：/inspection-tasks、/collection-tasks
- 事件：InspectionDispatched、InspectionCompleted、CollectionResultConsumed
- 外部集成：现有采集平台子应用
- 文件契约：FileArtifact
- 工作流/状态：方式选择、预检/执行、业务消费和归档门禁
- 授权与数据范围：AssignedProjectDeviceScope、BusinessObjectDeviceCredentialScope
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### INS-05

- 需求名称：巡检报告生成
- 数据对象：InspectionReport
- 数据表：srv_inspection_report_revision
- API：/inspection-reports/{id}/versions
- 事件：InspectionCompleted
- 外部集成：UMC
- 文件契约：FileArtifact
- 工作流/状态：报告生成、回调校验、发布版本
- 授权与数据范围：AssignedProjectDeviceScope、FileBusinessScope
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### INS-06

- 需求名称：巡检问题标注
- 数据对象：ServiceIssue
- 数据表：srv_service_issue、srv_service_issue_remediation
- API：/service-issues
- 事件：InspectionIssueRaised/Closed
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：问题标注、误报、整改复核和关闭
- 授权与数据范围：AssignedProjectDeviceScope；问题责任范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### INS-07

- 需求名称：巡检闭环归档
- 数据对象：InspectionTask、CollectionTask
- 数据表：srv_inspection_task、srv_inspection_task_rule_snapshot、plt_collection_task
- API：/inspection-tasks、/collection-tasks
- 事件：InspectionDispatched、InspectionCompleted、CollectionResultConsumed
- 外部集成：现有采集平台子应用
- 文件契约：FileArtifact
- 工作流/状态：方式选择、预检/执行、业务消费和归档门禁
- 授权与数据范围：AssignedProjectDeviceScope、BusinessObjectDeviceCredentialScope
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### INS-08

- 需求名称：误报反馈机制
- 数据对象：ServiceIssue
- 数据表：srv_service_issue、srv_service_issue_remediation
- API：/service-issues
- 事件：InspectionIssueRaised/Closed
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：问题标注、误报、整改复核和关闭
- 授权与数据范围：AssignedProjectDeviceScope；问题责任范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### INS-09

- 需求名称：巡检规则配置字段
- 数据对象：InspectionRule
- 数据表：srv_inspection_rule、srv_inspection_rule_revision
- API：/inspection-rules、/{id}/revisions
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：规则配置、发布版本和任务冻结
- 授权与数据范围：AssignedProjectDeviceScope；规则维护/使用分离
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录

### INT-01

- 需求名称：CRM/ERP项目同步
- 数据对象：Project、Contract、SalesOrder
- 数据表：ast_asset_sync_batch、ast_asset_sync_item、com_contract、com_sales_order
- API：/projects、/contracts、/sales-orders
- 事件：MasterDataSynchronized
- 外部集成：CRM、ERP
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：双源同步、待映射、字段Owner裁决和对账
- 授权与数据范围：集成服务账号限Owner字段；工程管理部映射权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### INT-02

- 需求名称：ITR版本同步
- 数据对象：AssetSyncSnapshot
- 数据表：ast_asset_sync_batch、ast_asset_sync_item、ast_device
- API：/devices
- 事件：MasterDataSynchronized
- 外部集成：ITR
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：版本同步、来源冲突隔离和对账
- 授权与数据范围：集成账号限ITR字段；ProjectDeviceScope查询
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### INT-05

- 需求名称：钉钉/HR/OA集成
- 数据对象：Todo、DirectorySyncSnapshot
- 数据表：plt_todo、plt_directory_sync_snapshot
- API：/todos、/integration/hr/directory
- 事件：MasterDataSynchronized、TodoRequested、TodoCompleted
- 外部集成：钉钉、HR、OA
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：必要人员组织同步、待办链接和通知回执；不接入打卡/工时
- 授权与数据范围：TenantOrganizationProjectScope；目录身份不直接等于项目角色
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### INT-09

- 需求名称：LDAP/AD集成
- 数据对象：AuthorizationGrant
- 数据表：plt_authorization_grant、ast_asset_sync_item
- API：/authorization-grants
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：LDAP/AD
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：认证断言校验、目录映射和平台会话
- 授权与数据范围：平台RBAC/DataScope；目录组不直授项目角色
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；脱敏请求响应、幂等键、重试/对账与降级记录

### INT-12

- 需求名称：设备连接与采集平台集成
- 数据对象：DeviceCredential、CredentialGrant、CollectionTask、CollectionResultReference
- 数据表：plt_device_credential、plt_credential_grant、plt_collection_task、plt_collection_result_consumption
- API：/device-credentials、/collection-tasks、/internal/collection-tasks/{id}/actions/confirm-consumption
- 事件：CollectionTaskRequested、CollectionResultAvailable、CollectionResultConsumed、CollectionCompleted
- 外部集成：现有采集平台子应用
- 文件契约：FileArtifact
- 工作流/状态：授权校验→下发→回调→业务消费→完成/失败；独立中心按成功回调终态
- 授权与数据范围：BusinessObjectDeviceCredentialScope；创建人默认权限和五元组授权
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试；凭证五元组、创建人默认授权、临时明文不落库、保存为凭证原子切换测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录；密文/密钥版本抽查、秘密扫描零命中和DAC任务回调链证据

### INT-03

- 需求名称：CRM客户同步
- 数据对象：Customer、CustomerRelationshipSnapshot
- 数据表：cus_customer、ast_asset_sync_batch、ast_asset_sync_item
- API：/customers
- 事件：MasterDataSynchronized、CustomerMerged
- 外部集成：CRM
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：客户同步、临时客户合并与字典映射
- 授权与数据范围：OrganizationCustomerScope；CRM字段只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### INT-04

- 需求名称：ITR技术公告同步
- 数据对象：TechnicalNoticeReference
- 数据表：FEATURE_FORWARD_MIGRATION(INT-04)：逻辑对象`TechnicalNoticeReference`；物理表由INT-04 Feature前向迁移确定
- API：/technical-notices、/technical-notices/{id}/references
- 事件：TechnicalNoticeSynchronized
- 外部集成：ITR
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：公告镜像、版本同步和业务引用
- 授权与数据范围：ProductDeviceProjectScope；V2只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### INT-06

- 需求名称：备件/授权/UMC集成
- 数据对象：RMAReplacement、AuthorizationGrant、InspectionReport
- 数据表：ast_rma_replacement、plt_authorization_grant、srv_inspection_report_revision
- API：/rma-replacements、/authorization-grants、/inspection-reports/{id}/versions
- 事件：MasterDataSynchronized
- 外部集成：备件系统、授权系统、UMC
- 文件契约：FileArtifact
- 工作流/状态：外部申请/结果映射、回调和对账
- 授权与数据范围：业务对象范围；完整授权码不可见
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### INT-07

- 需求名称：财务集成
- 数据对象：PaymentGate
- 数据表：res_payment_gate、plt_integration_reconciliation
- API：/payment-gates
- 事件：PaymentGateChanged
- 外部集成：财务系统
- 文件契约：FileArtifact
- 工作流/状态：批准费用出向、结果查询和人工对账
- 授权与数据范围：OrganizationSupplierScope；财务结果复核
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### INT-10

- 需求名称：短信/邮件平台集成
- 数据对象：Todo
- 数据表：plt_todo、ast_asset_sync_item
- API：/todos
- 事件：NotificationRequested、NotificationDelivered/Failed
- 外部集成：短信/邮件、钉钉
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：节点通知、受理/送达分离和兜底
- 授权与数据范围：业务对象接收人范围；模板变量白名单
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### NFR-01

- 需求名称：平台性能、安全与兼容基线
- 数据对象：AuditRecord、MetricSnapshot
- 数据表：plt_operation_audit、ana_metric_snapshot
- API：/analytics/metrics
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：性能水位、安全审计和兼容性验证
- 授权与数据范围：TenantOrganizationProjectScope；服务端强制范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；50并发用户30分钟且不少于10000请求的性能测试；Chrome/Edge/Firefox四视口真实浏览器安全与兼容验收
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；P95/错误率/资源曲线性能报告；Playwright trace、截图/录像及浏览器控制台记录

### NFR-02

- 需求名称：设备凭证及巡检非功能需求
- 数据对象：DeviceCredential、CredentialGrant、CollectionTask、CollectionResultReference
- 数据表：plt_device_credential、plt_credential_grant、plt_collection_task、plt_collection_result_consumption
- API：/device-credentials、/collection-tasks、/internal/collection-tasks/{id}/actions/confirm-consumption
- 事件：CollectionTaskRequested、CollectionResultAvailable、CollectionResultConsumed、CollectionCompleted
- 外部集成：现有采集平台子应用
- 文件契约：FileArtifact
- 工作流/状态：授权校验→下发→回调→业务消费→完成/失败；独立中心按成功回调终态
- 授权与数据范围：BusinessObjectDeviceCredentialScope；创建人默认权限和五元组授权
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试；AES-256或同等强度、任务级短期取密、撤销与泄露处置测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录；密码学配置、密钥轮换演练、秘密扫描及授权拒绝报告

### NFR-03

- 需求名称：割接/巡检推送节点
- 数据对象：Todo
- 数据表：plt_todo、ast_asset_sync_item
- API：/todos
- 事件：NotificationRequested、NotificationDelivered/Failed
- 外部集成：短信/邮件、钉钉
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：节点通知、受理/送达分离和兜底
- 授权与数据范围：业务对象接收人范围；模板变量白名单
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；通知到达率不低于99%及项目进度60秒内可读测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；有效发送/送达明细、事件时间与项目状态版本延迟报告

### AUT-01

- 需求名称：授权申请管理
- 数据对象：AuthorizationGrant
- 数据表：plt_authorization_grant
- API：/authorization-grants
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：OA、授权系统
- 文件契约：FileArtifact
- 工作流/状态：申请、OA审批引用、外部授权确认和查询
- 授权与数据范围：申请对象/设备/产品范围；授权码脱敏
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### AUT-02

- 需求名称：授权信息查询
- 数据对象：AuthorizationGrant
- 数据表：plt_authorization_grant
- API：/authorization-grants
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：OA、授权系统
- 文件契约：FileArtifact
- 工作流/状态：申请、OA审批引用、外部授权确认和查询
- 授权与数据范围：申请对象/设备/产品范围；授权码脱敏
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### CHG-01

- 需求名称：项目变更申请电子流
- 数据对象：ChangeRequest
- 数据表：plt_change_request
- API：/change-requests
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：变更申请、审批、目标版本校验和执行
- 授权与数据范围：TenantOrganizationProjectScope；变更对象权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### PLT-01

- 需求名称：统一待办接入与状态一致性
- 数据对象：Todo
- 数据表：plt_todo
- API：/todos、/{id}/actions/complete
- 事件：TodoRequested、TodoCompleted
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：统一待办接入、完成回调Owner再校验
- 授权与数据范围：TenantOrganizationProjectScope；待办责任人范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据

### PLT-02

- 需求名称：统一文件身份与版本管理
- 数据对象：FileArtifact
- 数据表：plt_file_artifact、plt_file_version、plt_file_reference
- API：/files:init-upload、/files/{id}:complete-upload、/file-references
- 事件：FileVersionCommitted、FileReferenceAttached/Detached、FileArchived
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：初始化上传、内容校验、版本提交、引用和归档
- 授权与数据范围：FileBusinessScope；下载实时回源业务权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试；50MB文件、分片/直传、哈希校验、恶意内容和引用权限测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录；50MB样本、哈希、扫描、版本与下载权限证据

### PROJ-12

- 需求名称：项目组合管理
- 数据对象：ProjectPortfolio
- 数据表：proj_project_portfolio、proj_project_portfolio_member、proj_project_portfolio_revision
- API：/project-portfolios
- 事件：ProjectPortfolioPublished
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：组合成员、发布快照和下钻
- 授权与数据范围：ProjectTreeScope；组合汇总不扩权
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据

### ANA-01

- 需求名称：项目组合经营看板
- 数据对象：PortfolioView、MetricSnapshot
- 数据表：ana_portfolio_projection、ana_metric_snapshot
- API：/analytics/portfolios/{id}、/analytics/metrics
- 事件：MetricSnapshotPublished
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：组合指标快照生成与只读展示
- 授权与数据范围：OrganizationReportScope；下钻回项目权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据

### COM-01

- 需求名称：合同订单关联与范围分配
- 数据对象：Contract、SalesOrder、OrderLine、DeliveryScope、DeliveryScopeDetail
- 数据表：com_contract、com_sales_order、com_order_line、com_delivery_scope、com_delivery_scope_detail
- API：/contracts、/sales-orders、/order-lines、/delivery-scopes
- 事件：DeliveryScopeAssigned/Released
- 外部集成：ERP、CRM
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：ERP订单行同步、范围主记录及明细分配/释放、明细合计一致性和超分配门禁
- 授权与数据范围：ContractProjectScope；ERP核心字段只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### COM-02

- 需求名称：合同订单履约回写与对账
- 数据对象：FulfillmentSnapshot、ReconciliationRecord
- 数据表：com_fulfillment_snapshot、com_reconciliation_record
- API：/fulfillment-reconciliations
- 事件：FulfillmentSnapshotPublished
- 外部集成：CRM
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：履约回写、业务回执、差异确认和关闭
- 授权与数据范围：ContractProjectScope；交付事实与经营状态分域
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### SOL-01

- 需求名称：准备数据动态表单
- 数据对象：DynamicFormSchema、DynamicFormInstance
- 数据表：sol_dynamic_form_schema、sol_dynamic_form_schema_revision、sol_dynamic_form_instance
- API：/form-schemas、/form-instances
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：Schema发布后只读、实例按版本校验
- 授权与数据范围：ProjectStageScope；Schema维护与实例填写分离
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录

### IMP-01

- 需求名称：阶段质量检查表
- 数据对象：ImplementationQualityCheck
- 数据表：imp_quality_check、imp_quality_item、imp_quality_remediation、imp_quality_review
- API：/quality-checks
- 事件：QualitySafetyGateChanged
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：提交→复核→整改→再复核
- 授权与数据范围：ImplementationProjectBatchScope；复核权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### IMP-02

- 需求名称：现场工作安全检查
- 数据对象：ImplementationSafetyCheck
- 数据表：imp_safety_check、imp_safety_item、imp_safety_remediation、imp_safety_exemption
- API：/safety-checks
- 事件：QualitySafetyGateChanged
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：提交→复核→阻断→整改/豁免→再复核
- 授权与数据范围：ImplementationProjectBatchScope；安全复核/豁免权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### RES-01

- 需求名称：服务商档案与资质权限
- 数据对象：Supplier
- 数据表：res_supplier、res_qualification
- API：/suppliers
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：OA
- 文件契约：FileArtifact
- 工作流/状态：服务商档案、资质版本和可用状态
- 授权与数据范围：OrganizationSupplierScope；资质文件字段权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### ACC-05

- 需求名称：遗留问题转持续服务跟踪
- 数据对象：ServiceHandover、ProjectClosure
- 数据表：acc_service_handover、acc_handover_item、acc_handover_result
- API：/service-handovers
- 事件：ProjectClosureCompleted
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：遗留问题及持续服务交接、接收确认
- 授权与数据范围：ProjectStageScope；交接双方项目/服务范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### ACC-06

- 需求名称：项目闭环与持续服务交接
- 数据对象：ServiceHandover、ProjectClosure
- 数据表：acc_service_handover、acc_handover_item、acc_handover_result
- API：/service-handovers
- 事件：ProjectClosureCompleted
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：遗留问题及持续服务交接、接收确认
- 授权与数据范围：ProjectStageScope；交接双方项目/服务范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### SRV-01

- 需求名称：设备服务状态与停产停维提示
- 数据对象：MaintenanceFact、ServiceStatus
- 数据表：ast_maintenance_fact、srv_service_status
- API：/devices/{deviceId}/service-status
- 事件：ServiceStatusChanged
- 外部集成：CRM
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：客观维保/停产停维状态计算与提示
- 授权与数据范围：ProjectDeviceScope；客观事实只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### AST-01

- 需求名称：RMA替换与维保信息衔接
- 数据对象：RMAReplacement、MaintenanceFact
- 数据表：ast_rma_replacement、ast_maintenance_fact
- API：/rma-replacements、/devices/{deviceId}/service-status
- 事件：DeviceStatusSynchronized
- 外部集成：备件系统
- 文件契约：FileArtifact
- 工作流/状态：RMA替换、设备归属校验和维保事实衔接
- 授权与数据范围：ProjectDeviceScope；设备与来源范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### AST-02

- 需求名称：设备维保客观状态计算
- 数据对象：MaintenanceFact、ServiceStatus
- 数据表：ast_maintenance_fact、srv_service_status
- API：/devices/{deviceId}/service-status
- 事件：ServiceStatusChanged
- 外部集成：CRM
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：客观维保/停产停维状态计算与提示
- 授权与数据范围：ProjectDeviceScope；客观事实只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录
