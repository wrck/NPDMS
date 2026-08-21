# SDS Phase 2 显式需求契约映射

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：附录 A.1 全部 100 项 V1/V2 正式需求
> Owner：SDS Phase 2 追溯治理；具体业务 Owner 以 `requirement-matrix.md` 为准
> Phase 3验证注记状态：`READY_FOR_PHASE_3_V1.8`（仅表示SDS设计可进入Phase 3，不批准DDL、Feature或Release）

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
- Phase 3 PRD验收基线：WHEN CRM生成执行单或ERP生成销售订单并通过接口同步至平台；THEN 系统自动创建项目记录，生成唯一项目编码，状态初始化为"待开始（S0）"；AND 分别加载项目所属办事处、客户、合同、签约方式、实施方式和CRM重大项目级别，并初始化平台项目类别；AND 按四维业务属性匹配项目模板（PM-03），冻结模板及流程定义版本后加载阶段、任务、里程碑、交付件和门禁；AND 按办事处与实施地点规则自动指派服务经理（V1可手动确认，V2自动指派）；WHEN 工程管理部在平台内发起"手动创建项目"操作；THEN 系统提供项目创建表单，支持录入项目名称、客户、合同号、办事处、实施地点、签约方式、项目类别和实施方式；无CRM来源时重大项目级别保持空/不适用；AND 表单内提供“项目模板”选择项，展示四维条件和业务场景命中的启用模板列表（PM-03），支持预览模板的阶段、任务、里程碑、交付件清单；AND 必填字段校验通过后生成项目编码与项目记录，状态初始化为"待开始（S0）"；AND 按所选模板冻结模板及流程定义版本并实例化阶段、任务、里程碑、交付件和门禁；未选择时仅可使用四维条件唯一命中的默认模板；AND 按规则指派服务经理（与自动创建一致）；AND 后续接口恢复后可将手工创建项目与CRM执行单/ERP销售订单关联补录；WHEN 同一销售订单关联多个实施地点或多个办事处；THEN 系统按多省份节点规则指派一级服务经理（下单办事处）与二级服务经理（各实施地点）；AND 单省份内场景指派实施地点服务经理；WHEN CRM/ERP接口同步失败或数据不完整；THEN 系统记录失败原因并触发告警，支持人工补录与重试，或转手动创建
- Phase 3授权拒绝断言：越权按“ProjectTreeScope；来源字段只读”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“项目创建、来源匹配与指派守卫”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Project、ProjectMemberAssignment”及数据表“proj_project、proj_project_member_assignment”；事件边界为“ProjectCreated”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“CRM、ERP”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 服务经理或工程管理部对主项目发起拆分操作；THEN 系统支持按订单行、数量、办事处、序列号维度自由组合选择拆分维度；AND 生成子项目记录，子项目继承主项目模板与关键属性，保持独立项目编码；AND 系统不限制固定层级深度，用户可指定直接父项目及业务层级标签；结构层级深度由父子关系自动计算，不允许人工设置与父子关系冲突的深度；AND 系统应校验父子关系合法且不得形成循环引用；WHEN 用户查看多级项目结构；THEN 系统支持查询直接下级、全部后代、完整上级链和指定业务层级；项目树默认按需加载直接下级；AND 在实际迁移项目量两倍与20万项目取较大值、单项目树1万个节点、直接子项目2000个、测试深度30的规模下，权限过滤后的上述查询与树形页面响应时间满足≤2秒（P95）；AND 测试深度30只作为性能验收数据，不限制业务继续创建更深层级；超过测试深度时仍须保证关系正确并采用按需加载；WHEN 子项目进度状态发生变更（如阶段推进、任务闭环）；THEN 主项目按“Σ（直接子项目进度×子项目权重）”逐级汇总；同一父项目直接子项目权重合计必须为100%，未配置时按直接子项目等权分配；AND 设备数量只作为可选统计指标，不自动改变权重；权重或汇总口径变更必须审批并形成版本，历史进度快照按当时版本解释且不追溯重算；WHEN 主项目发起闭环申请；THEN 系统校验所有子项目均已闭环，存在未闭环子项目时驳回主项目闭环申请；AND 全部子项目闭环后主项目方可闭环；WHEN 指定父项目不存在、跨租户、属于当前项目的后代，或拆分范围超过父项目可分配范围；THEN 拆分申请保持草稿状态，不生成子项目和父子关系，并记录未通过的校验项
- Phase 3授权拒绝断言：越权按“ProjectTreeScope；后代范围服务端计算”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“无环移动、完整投影版本切换”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ProjectHierarchy、ProjectAncestorProjection”及数据表“proj_project、proj_project_tree_path、proj_project_tree_change”；事件边界为“ProjectTreeChanged”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据

### PM-03

- 需求名称：项目模板与阶段门禁
- 数据对象：ProjectTemplate、ProjectStageSnapshot
- 数据表：proj_project_template_revision、proj_project_template_task_definition、proj_project_stage_snapshot
- API：/project-templates
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：模板内StageDefinition/TaskDefinition发布、实例冻结、必填WorkBinding（默认TASK_NATIVE）/PermissionPolicy/CompletionRule/GateRef校验和阶段门禁
- 授权与数据范围：项目模板维护权限；项目阶段范围；非TASK_NATIVE绑定目标权限不得越权
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试
- Phase 3 PRD验收基线：WHEN 管理员在模板管理后台创建或编辑项目模板；THEN 可配置模板名称、签约方式/项目类别/实施方式/重大项目级别条件、适用业务场景、生命周期阶段（S0～S6）、各阶段任务清单、里程碑节点、交付件要求、准入条件和准出门禁；AND 每个可执行任务可配置工作绑定、权限策略、完成规则和门禁引用；模板预览按Stage→Task展示执行入口，不另建重复导航定义；AND 模板按四个独立业务维度配置适用条件，并分别引用可配置字典或CRM来源属性；WHEN 自动创建项目时（PM-01自动创建场景）；THEN 系统按四维属性唯一匹配对应模板，冻结模板及流程定义版本并加载阶段、任务、里程碑、交付件和门禁；AND 项目阶段推进时校验准出门禁（交付件齐备、任务闭环等），不满足则阻断推进；WHEN 手动创建项目时（PM-01手动创建场景）工程管理部选择项目模板；THEN 系统提供四维条件和业务场景命中的启用模板列表供选择，支持预览模板的阶段、任务、里程碑、交付件清单；AND 工程管理部选择模板后系统按所选模板加载阶段、任务、里程碑、交付件、门禁规则至项目实例；AND 若未显式选择模板则只使用唯一命中的默认模板；未匹配或多匹配时停止实例化并进入人工处理；WHEN 模板发生变更；THEN 已创建项目沿用原模板版本，新创建项目应用新模板，支持模板版本管理；WHEN 项目创建时没有匹配的生效模板、同一条件组合存在多个同优先级默认模板或模板引用了不存在的任务/交付件；THEN 项目保持创建草稿或模板保持草稿状态，不实例化阶段任务，并记录具体冲突项供管理员修正；WHEN WorkBinding缺失、完成规则无法解析、非TASK_NATIVE绑定目标未发布或门禁引用失效；THEN 模板不得发布，并逐项返回失败任务、绑定类型和失效引用；通用任务必须显式使用TASK_NATIVE，其他绑定任务不得以通用任务内容代替实际业务工作
- Phase 3授权拒绝断言：越权按“项目模板维护权限；项目阶段范围；非TASK_NATIVE绑定目标权限不得越权”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“模板内StageDefinition/TaskDefinition发布、实例冻结、必填WorkBinding（默认TASK_NATIVE）/PermissionPolicy/CompletionRule/GateRef校验和阶段门禁”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ProjectTemplate、ProjectStageSnapshot”及数据表“proj_project_template_revision、proj_project_template_task_definition、proj_project_stage_snapshot”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 服务经理在有管理权限的项目下创建第31层后代项目，或把项目移动到另一个有权父项目下；THEN 平台保存直接父项目、根项目、完整层级路径和实际结构深度，查询结果可返回完整上级链且不以测试深度限制业务创建；WHEN 无权访问平级项目B的项目A经理发起查询，或仅有“当前项目”权限的人员越权查询其子项目；THEN 平台返回无权结果且不返回项目名称、进度、任务、设备和交付件明细；被授予“当前项目及全部后代”后才按授权项目子树返回数据；WHEN 用户尝试把项目挂到自身、任一后代节点或其他租户项目下；THEN 平台拒绝移动并保持原父子关系、路径和授权结果不变，同时记录拒绝原因；WHEN 父项目汇总包含被多个层级引用的同一设备或交付对象；THEN 平台按业务对象唯一ID去重，并同时展示授权范围内的明细数量与去重汇总数量
- Phase 3授权拒绝断言：越权按“ProjectTreeScope；后代范围服务端计算”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“无环移动、完整投影版本切换”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ProjectHierarchy、ProjectAncestorProjection”及数据表“proj_project、proj_project_tree_path、proj_project_tree_change”；事件边界为“ProjectTreeChanged”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 有权人员选择借货项目和已生效的正式销售项目并确认设备处置及成果复用清单；THEN 平台创建唯一转销关系，逐项生成只读引用或带来源的派生草稿；全部成功后归档临时项目并展示对象处理汇总；WHEN 转销中部分附件引用或设备归属调整失败；THEN 平台把转销标记为“部分失败”，列出成功与失败对象，临时项目保持可处理状态且正式项目不把失败对象展示为已接收；WHEN 正式项目不存在、临时项目已有生效转销目标、设备归属冲突或用户无源/目标项目权限；THEN 平台拒绝发起或重复转销，保持两个项目及其业务对象原状态不变
- Phase 3授权拒绝断言：越权按“同时具备源/目标项目管理权限；原敏感对象权限继续生效”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“处理中→部分失败/待处理→已完成；全部成功后源项目只读归档”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“BorrowedProjectConversion、ConversionItem、ConversionDeviceDisposition”及数据表“proj_project_conversion、proj_project_conversion_item、proj_project_conversion_device”；事件边界为“ProjectConversionCompleted、ProjectConversionPartiallyFailed”，文件边界为“FileArtifact”，外部集成为“CRM、ERP”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 服务经理把有共同客户业务关系的多个项目按唯一期次加入同一群组；THEN 平台保存群组、关系类型和期次，展示各期独立状态以及客户、设备、拓扑、方案的来源版本和差异；WHEN 新期项目基于原期方案或拓扑创建可编辑内容；THEN 平台生成带来源项目与来源版本的派生版本，修改结果只属于新期项目且不改变原期资料；WHEN 项目已属于冲突群组、期次重复、关系形成循环或用户无任一期项目权限；THEN 平台拒绝关联并保持原群组与项目数据不变，同时返回具体冲突项目和关系
- Phase 3授权拒绝断言：越权按“同时有权的期次可维护；查询按各期权限裁剪”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“群组成员增删、唯一期次、无环和派生版本”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“MultiPhaseProjectGroup、MultiPhaseProjectMember、CrossPhaseContentReference”及数据表“proj_multi_phase_project_group、proj_multi_phase_project_member、proj_project_cross_phase_reference”；事件边界为“ProjectPhaseGroupChanged”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN CRM同步签约方式、实施方式和重大项目级别且来源映射有效；THEN 平台分别保存三个来源属性、来源版本与历史；重大项目命中时另行初始化平台项目类别为工程类；WHEN 工程管理部创建无CRM来源项目；THEN 平台允许选择签约方式、项目类别和实施方式，重大项目级别保持空/不适用并记录手工创建依据；WHEN 来源属性未映射、项目类别缺失或模板无匹配/多匹配；THEN 平台保持“待分类/待选模”状态，不实例化模板，记录冲突字段和人工处理结果；WHEN CRM后续修正重大项目级别或平台受控调整项目类别；THEN 平台分别形成来源历史和项目类别变更记录，不互相覆盖，并按PM-03/CHG-01生成影响清单
- Phase 3授权拒绝断言：越权按“项目管理范围；来源证据只读”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“自动识别结果确认与留痕”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Project”及数据表“proj_project”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN V1工程管理部人员为项目节点选择在职且属于适用办事处的服务经理；THEN 平台保存主责服务经理、责任项目节点和生效时间，将项目`assignment_status`从“待指派”更新为“已指派”；`current_stage`和`lifecycle_status`保持不变，并向被指派人发送通知；WHEN 项目涉及多个省份和多个实际项目节点；THEN 平台允许分别为统筹节点和实施节点人工指派主责人，不要求节点位于固定结构深度，并可从根项目查看各节点责任分布；WHEN 候选人已离职、办事处不匹配、实施地点缺失或项目节点已有生效中的主责人；THEN 平台拒绝直接覆盖，保持原责任关系或“待指派”状态，并提示补齐地点或执行改派流程
- Phase 3授权拒绝断言：越权按“项目管理范围；仅PRD角色”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“V1手动指派、V2规则候选确认”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ProjectMemberAssignment”及数据表“proj_project_member_assignment”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 有权人员筛选项目、选择合法目标人员并完成批量预检确认；THEN 平台生成批次并逐项目结束原责任区间、建立新责任区间，返回成功数、失败数和每项处理结果；WHEN 批次中部分项目已改派、无权、责任人已变化或目标人员不适用；THEN 平台仅执行仍满足冻结条件的项目，失败项目保留原责任人，批次标记“部分成功”并提供失败明细；WHEN 用户以过期预检清单提交或尝试把服务经理批量操作用于项目经理角色；THEN 平台拒绝批次执行并要求重新预检，所有项目保持原责任关系且不产生变更记录
- Phase 3授权拒绝断言：越权按“项目管理范围；逐项目校验”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“批量逐项变更并保留有效区间”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ProjectMemberAssignment”及数据表“proj_project_member_assignment”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录

### PM-10

- 需求名称：项目回退与关闭
- 数据对象：Project、ProjectStageSnapshot
- 数据表：proj_project、proj_project_stage_snapshot
- API：/projects/{id}/actions/rollback、/projects/{id}/actions/close
- 事件：ProjectStageChanged、ProjectClosed(lifecycleStatus=EXCEPTION_CLOSED)
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：受控回退保持ACTIVE；异常关闭置EXCEPTION_CLOSED；正常闭环仅由CLO-02产生NORMAL_CLOSED
- 授权与数据范围：ProjectTreeScope；状态命令权限与门禁
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试
- Phase 3 PRD验收基线：WHEN 服务经理对符合回退条件的项目填写原因并确认回退；THEN 平台保持`lifecycle_status=ACTIVE`，将`current_stage`回退至S0、`assignment_status`置为“待指派”，结束原服务经理责任区间，保留任务、设备、文档和历史进度，并通知工程管理部重新指派；WHEN 工程管理部关闭不存在在途审批、执行任务和未完成后代项目的项目；THEN 平台保存关闭依据和遗留事项快照，将`lifecycle_status`置为“异常关闭（EXCEPTION_CLOSED）”并进入只读归档，且不计入正常闭环项目数量；WHEN 项目存在未关闭后代、在途割接/采集/巡检等领域任务或进行中审批；THEN 平台拒绝关闭并列出阻断对象，不改变项目及关联对象状态；WHEN 有权人员重新开启已关闭项目；THEN 平台仅允许对EXCEPTION_CLOSED项目执行受控重开，将`lifecycle_status`恢复为ACTIVE并回到关闭前可恢复阶段，同时生成新的责任处理事项；NORMAL_CLOSED项目不得通过PM-10直接重开，且不自动恢复已终止的外部任务
- Phase 3授权拒绝断言：越权按“ProjectTreeScope；状态命令权限与门禁”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“受控回退保持ACTIVE；异常关闭置EXCEPTION_CLOSED；正常闭环仅由CLO-02产生NORMAL_CLOSED”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Project、ProjectStageSnapshot”及数据表“proj_project、proj_project_stage_snapshot”；事件边界为“ProjectStageChanged、ProjectClosed(lifecycleStatus=EXCEPTION_CLOSED)”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据

### PM-11

- 需求名称：项目任务管理
- 数据对象：ProjectTask、TaskWorkBinding、TaskCompletionRule、TaskCompletionEvaluation、TaskAncestorProjection、TaskDependency
- 数据表：proj_project_task、proj_project_task_execution_contract、proj_project_task_completion_evaluation、proj_task_tree_path、proj_task_dependency
- API：/projects/{id}/workspace、/projects/{id}/tasks、/project-tasks/{id}/workbench、/project-tasks/{id}/actions/move、/project-tasks/{id}/actions/{submit|start|complete|cancel}
- 事件：TaskAssigned、TaskCompleted
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：ProjectTask内必填WorkBinding/CompletionRule与Stage→ProjectTask工作台投影、任务任意层级移动；TASK_NATIVE按任务自身事实执行，其他类型回源绑定事实并追加完成判定后完成
- 授权与数据范围：ProjectTreeScope；TASK_NATIVE任务范围；其他类型由服务端合并目标业务对象权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；5万节点、2000直接子节点、深度30任务树查询/移动测试
- Phase 3 PRD验收基线：WHEN 用户创建或移动任务节点；THEN 可指定直接父任务和业务层级标签，系统自动计算结构层级深度，并拒绝循环引用及跨项目非法挂接；WHEN 用户查看或搜索任务树；THEN 支持直接下级、全部后代、完整上级链和指定业务层级查询，树形界面默认按需加载直接下级；AND 通过任务名称或编码定位节点时，返回目标任务并展示其完整层级路径；AND 上述任务层级查询在权限过滤后仍满足平台页面响应时间≤2秒（P95）；AND 性能验收按实际迁移任务量两倍与200万任务取较大值，并覆盖单任务树5万个节点、直接子任务2000个和测试深度30；AND 测试深度30不限制继续创建更深任务层级；超过测试深度时仍须保证父子关系、路径和权限结果正确并采用按需加载；WHEN 用户无权访问目标任务、尝试形成循环引用或把任务非法挂接到其他项目；THEN 平台拒绝查看或移动，保持原任务父子关系和路径不变，且不返回未授权任务业务明细；WHEN 授权管理员新增或调整任务扩展状态并提交发布；THEN 平台要求该状态配置标准状态映射、允许迁移、适用角色、进入/退出条件和状态机版本；发布后新建任务使用新版本，已创建任务继续使用创建时冻结的状态机版本；WHEN 状态配置缺少标准映射或合法迁移、尝试删除或改义核心状态，或者允许绕过完成/关闭门禁；THEN 平台拒绝发布并继续使用当前生效版本，不改变任何既有任务状态；WHEN 用户进入项目工作区；THEN 项目概览独立展示基本信息、项目树、团队成员、项目任务、设备清单、实施范围六个页签；业务导航按Stage→ProjectTask生成，深层任务按需展开且不受固定层级限制；WHEN 用户点击WorkBinding为TASK_NATIVE的ProjectTask；THEN 右侧任务详情工作台展示通用基础信息和本人获权的任务操作，按ProjectTask自身状态机及完成规则执行；WHEN 用户点击一个绑定业务对象、业务组件、动态表单、审批或组合视图的ProjectTask；THEN 右侧在保留通用任务基础信息的同时按绑定关系加载相应真实业务界面，并按服务端权限与对象状态返回查看、编辑、创建、填写或审批模式，不要求用户离开当前任务上下文再次查找入口；WHEN TASK_NATIVE任务自身事实或其他绑定任务的目标业务事实达到完成规则，或者用户尝试在规则未满足时完成任务；THEN 平台按任务版本、绑定版本、规则版本及适用的事实版本派生完成结果；满足时受控推进任务，不满足时返回具体未满足项，非TASK_NATIVE任务不能由通用完成操作直接绕过
- Phase 3授权拒绝断言：越权按“ProjectTreeScope；TASK_NATIVE任务范围；其他类型由服务端合并目标业务对象权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“ProjectTask内必填WorkBinding/CompletionRule与Stage→ProjectTask工作台投影、任务任意层级移动；TASK_NATIVE按任务自身事实执行，其他类型回源绑定事实并追加完成判定后完成”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ProjectTask、TaskWorkBinding、TaskCompletionRule、TaskCompletionEvaluation、TaskAncestorProjection、TaskDependency”及数据表“proj_project_task、proj_project_task_execution_contract、proj_project_task_completion_evaluation、proj_task_tree_path、proj_task_dependency”；事件边界为“TaskAssigned、TaskCompleted”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目经理首次录入合法的工期起止日期，或录入工期时长及计算起点；THEN 平台生成版本1，计算并展示统一的起止日期和时长，将该版本标记为当前生效工期并提供给PLN-01；WHEN 项目经理修改已生效工期并提交完整变更原因及规定附件；THEN 平台冻结变更快照并生成“待审批”版本；服务经理通过后切换当前版本并触发计划重算，驳回后原工期继续生效；WHEN 起止日期倒置、时长与起止日期不一致、客户延期说明缺失或已有变更审批进行中；THEN 平台拒绝提交新审批，列出冲突字段或缺失材料，且不改变当前生效工期和施工计划
- Phase 3授权拒绝断言：越权按“ProjectStageScope；计划审批节点”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“工期基线、变更申请与审批”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ConstructionPlan”及数据表“sol_construction_plan、sol_construction_plan_revision、sol_construction_plan_change”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目经理在S1工前准备阶段发起工勘分工信息采集；THEN 系统提供标准化工勘分工信息采集表单，包含供电、网口、光纤、机柜、网线、光模块等工勘项；AND 每个工勘项可填写确认状态（待确认/已确认/不适用）、负责人、确认结果、附件（照片/文档）；AND 工勘项支持标记为"上架加电外包"，并记录外包方信息与联系方式；WHEN 工勘项涉及领料或外采；THEN 系统支持发起OA领料/外采流程，流程单号回传至工勘记录；AND 工勘详情页展示OA流程状态（待审批/已审批/已发货/已到货），状态变更时同步更新；WHEN 全部适用工勘项已有确认结果和规定证据，人员、领料/外采、备件及批准材料均达到模板要求或已取得有效豁免；THEN 系统将实施就绪状态标记为“已就绪”，生成就绪快照并把工勘数据作为S4实施部署阶段的现场作业输入；AND 工勘数据、来源流程结果和豁免记录归档至项目档案，支持后续阶段查询与引用；WHEN 任一适用项仍为“待确认”、来源流程被驳回、规定证据缺失或豁免已过期；THEN 实施就绪状态保持“未就绪”，系统列出阻断项且不允许项目通过对应的S4准入门禁
- Phase 3授权拒绝断言：越权按“ProjectStageScope；字段与文件权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“工勘/需求填写、提交、确认和退回”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Preparation、DynamicFormInstance”及数据表“sol_preparation、sol_dynamic_form_instance”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目经理选择可换物料、填写合法数量和原因后提交；THEN 平台生成换货申请并占用对应数量；CRM返回业务单号后展示逐明细处理状态和最近回传时间；WHEN CRM仅完成部分换货明细；THEN 平台只更新已完成明细，释放被驳回/取消数量，申请状态标记“部分完成”并保留未完成项；WHEN 换货数量超过可用量、存在并发占用、CRM超时或回调物料与申请不一致；THEN 平台拒绝超量提交；集成失败保持待推送/待核对状态，不更新项目有效物料结果
- Phase 3授权拒绝断言：越权按“ProjectStageScope；物料范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“换货申请、外部处理映射与恢复对账”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Preparation”及数据表“sol_preparation、ast_asset_sync_item”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“ERP”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目经理在S1工前准备阶段进入需求分析填写页面；THEN 系统提供标准化需求分析模板，包含项目背景、项目目标、网络拓扑、传输需求、流量需求、业务需求、IP规划、冗余需求、安全防护、运维需求、日志需求共11项内容；AND 每项内容支持富文本填写与附件上传（拓扑图、网络图等）；AND 必填项校验（项目背景、项目目标、网络拓扑为必填）；WHEN 项目经理完成需求分析填写并提交；THEN 系统校验必填项完整性，校验通过后保存需求分析数据并标记为"已完成"；AND 需求分析数据自动流转至S3方案阶段，SCH-01实施方案模板填写时自动引用需求分析数据预填对应字段；AND 需求分析数据归档至项目档案，支持后续阶段查询与版本对比；WHEN 项目背景、项目目标或网络拓扑缺失，拓扑附件上传失败，或项目经理无该项目编辑权限；THEN 需求分析保持草稿状态，不生成已完成版本，也不向SCH-01提供新的有效输入，并记录未通过项
- Phase 3授权拒绝断言：越权按“ProjectStageScope；字段与文件权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“工勘/需求填写、提交、确认和退回”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Preparation、DynamicFormInstance”及数据表“sol_preparation、sol_dynamic_form_instance”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目设备类型均命中有效模板且项目、设备必填数据完整；THEN 平台生成交底书草稿，展示模板/数据来源和组合章节，项目经理确认后形成只读版本及可校验下载文件；WHEN 项目经理在已归档交底书后重新生成；THEN 平台创建新版本并保留旧版本、来源模板、设备清单快照和版本差异，现场任务仍引用其明确选定版本；WHEN 模板缺失/冲突、设备清单未确认、文件生成失败或用户无项目编辑权限；THEN 平台不生成正式版本，保留失败原因或草稿，且不替换当前有效交底书
- Phase 3授权拒绝断言：越权按“ProjectStageScope、FileBusinessScope”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“交底书生成、版本提交和确认”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Preparation、FileArtifact”及数据表“sol_preparation、plt_file_artifact、plt_file_version”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目进入S2施工计划阶段且工期数据（PRE-01）已录入；THEN 系统读取签约方式（直签/非直签）与工期数据；AND 直签项目：以合同验收时间为基准，按各阶段标准工期占比倒推S1~S6各阶段最迟完成时间；AND 非直签项目：以工期时长为基准，按各阶段标准工期占比倒推S1~S6各阶段最迟完成时间；AND 生成施工计划记录，展示各阶段计划开始时间、计划结束时间、最迟完成时间；WHEN 系统完成施工计划推算；THEN 联动PLN-02工期紧张预警：工期总时长不足3个月时自动触发预警，提示落实计划/确认发货/发起CRM发货提醒；AND 联动PLN-03超期标红：实际完成时间超过最迟完成时间的阶段自动标红，超期项目纳入单独统计；WHEN 项目经理调整施工计划并提交PLN-04审批；THEN 系统将调整后的施工计划提交服务经理钉钉审批，审批通过后施工计划生效；AND 施工计划生效后各阶段最迟完成时间作为后续超期判定基准；WHEN 项目缺少合同验收时间或工期时长、阶段占比合计不为100%，或调整后的阶段日期发生逆序/超出工期；THEN 施工计划保持“待推算”或草稿状态，不生成新的审批版本，并展示具体缺失字段或冲突阶段
- Phase 3授权拒绝断言：越权按“ProjectStageScope；计划只读/维护分离”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“计划计算候选、预警和显式应用”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ConstructionPlan”及数据表“sol_construction_plan、sol_construction_plan_revision”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 当前有效计划的结束时间早于开始时间加3个日历月；THEN 平台为该计划版本生成一条“待处理”工期紧张预警，展示阈值、实际工期和可选处理动作，并通知项目经理和服务经理；WHEN 项目经理选择“发起CRM发货提醒”且CRM成功返回业务单号；THEN 平台把业务单号、受理状态和发起时间关联到预警；若仅通知成功而CRM未受理，预警不得标记处理完成；WHEN 计划缺少有效起止日期、计算失败，或换版后工期已不再小于阈值；THEN 平台不生成错误新预警；计算失败标记待重算，已存在预警在重算确认后转为“已解除”并保留历史
- Phase 3授权拒绝断言：越权按“ProjectStageScope；计划只读/维护分离”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“计划计算候选、预警和显式应用”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ConstructionPlan”及数据表“sol_construction_plan、sol_construction_plan_revision”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 当前生效计划中的未完成阶段超过最迟完成时间；THEN 平台以当前时间计算超期天数，在项目列表、详情和阶段任务同步标红，并把该项目计入同口径超期统计；WHEN 阶段在最迟完成时间后首次有效完成，或新计划版本审批生效；THEN 平台以完成时间或新基准重新计算当前结果，同时保留原计划版本、原基准和原超期天数供追溯；WHEN 生效计划缺失最迟完成时间、时间格式异常或统计任务部分失败；THEN 平台将对象标记为“超期状态待计算”，不把未知对象计为正常，也不发布不完整汇总为正式统计；WHEN 上级项目查看后代超期汇总；THEN 平台只统计授权项目子树内对象并按唯一ID去重，统计数量可下钻核对到同一批明细
- Phase 3授权拒绝断言：越权按“ProjectStageScope；计划只读/维护分离”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“计划计算候选、预警和显式应用”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ConstructionPlan”及数据表“sol_construction_plan、sol_construction_plan_revision”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目经理完成施工计划填写与调整并提交审批；THEN 系统通过钉钉推送审批待办至服务经理，含项目编码、施工计划摘要、各阶段最迟完成时间；AND 服务经理可在钉钉或平台内查看施工计划详情并进行审批操作（通过/驳回）；WHEN 服务经理审批通过；THEN 施工计划状态变更为"已生效"，项目阶段推进至S3方案阶段；AND 仅服务经理具有施工计划的修改权限，项目经理无修改权限；WHEN 服务经理审批驳回；THEN 施工计划状态变更为"已驳回"，驳回意见回传至项目经理；AND 项目经理根据驳回意见修改后重新提交审批；WHEN 施工计划生效后需调整（如工期变更导致计划调整）；THEN 项目经理发起调整申请，服务经理审批通过后方可更新施工计划；AND 调整历史完整留存，支持查询施工计划变更轨迹
- Phase 3授权拒绝断言：越权按“ProjectStageScope；审批人按PRD”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“施工计划提交、审批、驳回与换版”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ConstructionPlan”及数据表“sol_construction_plan、sol_construction_plan_revision”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目进入S3方案阶段且PRE-04需求分析已完成；THEN 系统提供标准化实施方案在线编写模板，包含项目背景、项目目标、项目团队、设备清单、实施进度、网络拓扑、部署方案、接口规划、IP规划、版本信息、配置脚本共11项内容；AND 系统自动引用PRE-04需求分析数据预填项目背景、项目目标、网络拓扑、IP规划等对应字段，项目经理可在预填基础上修改补充；AND 设备清单字段自动引用EQP-01设备序列号档案数据，实施进度字段自动引用PLN-01施工计划各阶段时间；AND 每项内容支持富文本填写、附件上传与表格录入；V1配置脚本部分支持上传网络/业务/高可用/全量4类脚本文件并记录文件版本/哈希；V2启用SCH-03后可进一步引用其已发布脚本版本并展示解析结果；WHEN 项目经理完成实施方案填写并提交；THEN 系统校验必填项完整性（项目背景、项目目标、网络拓扑、部署方案、配置脚本为必填），校验通过后方案状态变更为"待审核"；AND 方案自动流转至SCH-05服务经理审核环节，方案版本号自动累加并保留历史版本，支持版本对比；WHEN 方案经SCH-05审核通过；THEN 方案状态变更为"已通过"，项目经理可下载方案文档归档；AND 方案数据自动流转至S4实施部署阶段引用（设备清单→硬件安装、配置脚本→配置Log采集、部署方案→割接上线）；WHEN 必填章节缺失、PRE-04未形成有效版本、配置脚本不存在，或来源数据更新后差异尚未处理；THEN 方案保持草稿状态，不生成待审核版本，并列出缺失项或待确认的输入差异
- Phase 3授权拒绝断言：越权按“ProjectStageScope；方案审批与文件权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“方案导入/编审、重大复审和发布版本”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Solution”及数据表“sol_solution、sol_solution_revision、sol_solution_review”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目经理上传受支持且通过安全校验的客户方案文件；THEN 平台生成解析任务，按字段展示原文位置、识别候选和待校对状态；项目经理确认后才创建带来源关系的SCH-01方案草稿；WHEN 同一文件哈希重复上传或新版本文件替换旧文件；THEN 平台分别返回既有解析记录或创建新解析版本，已确认方案保持原导入版本不变；WHEN 文件损坏/加密无法解析、识别冲突、解析超时或用户无项目权限；THEN 平台保持“解析失败/待校对”且不写入有效方案，允许重新上传、重试或人工录入并记录来源
- Phase 3授权拒绝断言：越权按“ProjectStageScope；方案审批与文件权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“方案导入/编审、重大复审和发布版本”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Solution”及数据表“sol_solution、sol_solution_revision、sol_solution_review”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 有权工程师上传合法脚本并选择类别、设备范围和所属方案；THEN 平台生成脚本版本和解析任务，展示带行号的脱敏解析结果；校对确认后SCH-01可引用该明确版本；WHEN 已批准方案引用的脚本出现新上传版本；THEN 平台保留原引用不变并标记“脚本输入已变化”，只有形成并批准新方案版本后S4才读取新脚本；WHEN 文件重复、编码无法识别、解析失败、检测到敏感内容或用户无下载权限；THEN 平台返回既有版本或保持待处理/解析失败，限制明文预览下载，且不把失败版本设为方案有效输入
- Phase 3授权拒绝断言：越权按“ProjectStageScope、FileBusinessScope；已发布模板使用权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“配置脚本上传、解析、发布和版本冻结”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Solution、FileArtifact”及数据表“sol_solution_revision、plt_file_artifact、plt_file_version”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 模板管理员发布通过占位符和适用范围校验的新模板版本；THEN 平台把该版本标为可引用并保留旧版本；项目经理引用后生成项目章节快照及明确来源版本；WHEN 模板发布新版本或停用，而项目已有引用章节；THEN 平台不改写既有章节；新引用按当前有效版本，显式迁移时展示模板和项目内容差异；WHEN 模板存在未解析必填占位符、默认范围冲突、已停用或用户无模板管理权限；THEN 平台拒绝发布或新引用，当前已发布版本和历史项目章节保持不变
- Phase 3授权拒绝断言：越权按“ProjectStageScope；方案审批与文件权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“方案导入/编审、重大复审和发布版本”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Solution”及数据表“sol_solution、sol_solution_revision、sol_solution_review”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目经理在SCH-01完成实施方案填写并提交审核；THEN 系统将方案推送至服务经理审核，方案状态变更为"待服务经理审核"；AND 服务经理可在平台内查看方案详情、添加审批意见与批注附件，进行审核操作（通过/驳回）；WHEN 服务经理审核通过且冻结流程版本要求二线/总部复审；THEN 系统按候选人规则生成分级复审待办，方案状态变更为“待分级复审”；角色组成员只有同时满足组织和项目范围才可处理；AND 分级复审通过且不存在其他强制节点后，方案变更为“已通过”并进入S4；驳回则保存意见并返回项目经理形成新版本；WHEN 服务经理审核通过且冻结流程版本不要求后续强制节点；THEN 方案状态变更为“已通过”，进入“可下载”并触发S4准入；WHEN 服务经理审核驳回；THEN 方案状态变更为"已驳回"，驳回意见回传至项目经理；AND 项目经理根据驳回意见修改方案后重新提交审核，方案版本号自动累加并保留历史版本
- Phase 3授权拒绝断言：越权按“ProjectStageScope；方案审批与文件权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“方案导入/编审、重大复审和发布版本”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Solution”及数据表“sol_solution、sol_solution_revision、sol_solution_review”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目经理在S4阶段进入到货签收环节；THEN 系统提供到货签收单上传页面，支持上传签收单扫描件/照片，并填写设备清单、签收人、签收时间、物流单号等关键信息；AND 系统记录到货签收时间，签收单数据同步至ACC-04交付件归档管理页面；WHEN 某批设备仅部分到货或存在短少、破损、型号不符等差异；THEN 系统分别保存已签收和差异明细，仅将已签收设备开放给EXE-02，项目到货里程碑保持“进行中”；AND 差异关闭或补签后更新对应明细，不覆盖原批次证据；WHEN 全部应到设备均已签收，或剩余设备已取得有效豁免；THEN 系统标记到货签收里程碑为“已签收”，保存完成时间和豁免范围，并准入EXE-02处理已签收设备
- Phase 3授权拒绝断言：越权按“ImplementationProjectBatchScope；项目经理确认”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“草稿、差异处理、项目经理最终确认”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ArrivalAcceptance”及数据表“imp_arrival_acceptance、imp_arrival_line、imp_arrival_difference”；事件边界为“ArrivalAccepted”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目进入EXE-02硬件安装环节（EXE-01到货签收已完成）；THEN 系统按EQP-01设备序列号清单展示待安装设备列表，支持逐台填写安装位置（机房/机柜/U位）、安装人、安装时间；AND 每台设备支持上传多角度安装照片（前/后/侧等），照片自动添加时间戳与地理位置水印（如设备支持）；WHEN 工程师完成单台设备安装位置填写与照片上传并提交；THEN 系统将该设备的安装位置数据同步至EQP-01设备序列号详情的位置字段；AND 安装记录归档至项目档案，支持按序列号/安装人/安装时间查询；WHEN 项目所有设备安装记录全部填写完成；THEN 系统标记硬件安装里程碑节点为"已完成"，准入EXE-03配置调试环节；WHEN 设备尚未签收、不属于当前项目安装范围，或安装位置/必需照片缺失；THEN 安装记录保持草稿或未完成状态，不更新设备当前位置，也不计入项目安装里程碑完成数量
- Phase 3授权拒绝断言：越权按“ImplementationProjectDeviceScope；设备归属与项目权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“提交、项目经理确认/退回和整改”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“InstallationRecord”及数据表“imp_installation_record、imp_installation_item、imp_installation_evidence”；事件边界为“InstallationConfirmed”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目进入EXE-03配置调试环节（EXE-02硬件安装已完成）且工程师选择在线采集；THEN 页面进入INT-12统一采集能力，工程师选择目标设备及认证方式；V1可手工提供/选择已批准采集命令或脚本，V2启用SCH-03后可选择其已发布脚本版本；认证可选择本人有权使用的设备凭证或临时输入登录用户名、密码；AND 系统完成业务权限、设备权限和命令模板权限校验；凭证模式追加凭证权限校验，临时模式按INT-12规则不保存密码，校验通过后创建采集任务并下发项目与设备上下文；AND 现有采集平台连接设备执行采集并通过INT-12回调日志文件或结果引用；任务、接口日志和回调均不得包含凭证明文；WHEN 工程师选择手动上传Log；THEN 平台直接接收Log文件并按手动上传方式建立配置Log记录，不调用采集子应用；WHEN 平台接收到日志文件；THEN 系统保存原始整机Log并形成解析版本；盒式设备按设备序列号归档，框式产品提取机框、槽位、板卡序列号/型号与板卡配置关系；AND 每次操作生成一条新记录，保留操作人、操作时间、采集方式（在线采集/手动上传）、设备连接端点、认证方式、凭证引用或临时登录用户名、统一采集任务编号和关联配置脚本版本等元数据，但不保存临时密码；AND 平台Log管理界面按项目/序列号/操作时间/操作人维度展示配置Log记录，支持日志下载、版本对比、关键字检索；WHEN 配置Log存储完成；THEN 系统将配置Log交由EQP-02管理，在EQP-01配置Log页签展示并由EQP-03版本历史关联，作为设备配置版本追溯依据；WHEN 板卡序列号无法识别或后续发生板卡更换；THEN 平台分别记录待匹配状态或结束旧关系并创建新关系，保留原始Log、自动匹配、人工修正和历史换板记录；AND 配置调试里程碑节点在所有目标设备均有至少一条配置Log记录后标记为"已完成"，准入EXE-04业务联调环节；WHEN 采集平台不可用、凭证无权使用、临时密码未重新输入、任务执行失败或回调文件解析失败；THEN 对应采集任务标记为失败且不计入里程碑，保留失败证据；工程师可重试或关联失败任务后人工上传Log形成新记录
- Phase 3授权拒绝断言：越权按“ImplementationProjectDeviceCollectionScope、BusinessObjectDeviceCredentialScope；设备关系维护权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“采集回调、框板解析、待匹配/人工绑定和业务消费确认”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ConfigurationCollectionResult、DeviceComponentRelation、CollectionTask”及数据表“imp_configuration_collection_result、imp_configuration_collection_parse_attempt、imp_configuration_component_candidate、ast_device_component_relation、plt_collection_task”；事件边界为“ConfigurationParsed、DeviceComponentRelationChanged、CollectionResultConsumed”，文件边界为“FileArtifact”，外部集成为“现有采集平台子应用”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目进入EXE-04业务联调环节（EXE-03配置调试已完成）且工程师选择在线采集；THEN 系统校验工程师对设备和已发布show tech命令模板的权限；选择已保存凭证时追加凭证权限校验，选择临时输入时按INT-12规则不保存密码，通过后创建并下发统一采集任务；AND 现有采集平台执行show tech采集并通过INT-12回调状态和结果；或工程师选择手动上传Log文件；AND 采集结果按设备维度归档至业务联调记录，记录设备序列号、采集时间、操作人、采集方式；WHEN 联调配置采集完成；THEN 系统将联调配置与EXE-03配置Log库中同序列号记录关联，支持联调前后配置对比；AND 业务联调里程碑节点在所有目标设备均有联调配置记录后标记为"已完成"，准入EXE-06割接上线环节；WHEN 采集平台不可用、认证未通过、show tech任务失败、回调无对应设备或缺少EXE-03对比基线；THEN 联调任务保持失败或待补充状态，不计入里程碑；工程师可重试或关联原任务人工上传结果，补齐基线后重新生成对比
- Phase 3授权拒绝断言：越权按“ImplementationProjectDeviceCollectionScope、BusinessObjectDeviceCredentialScope”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“采集回调、联调结论和问题引用”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“JointDebuggingResult、CollectionTask”及数据表“imp_joint_debugging_result、imp_joint_debugging_item、plt_collection_task”；事件边界为“JointDebuggingCompleted、CollectionResultConsumed”，文件边界为“FileArtifact”，外部集成为“现有采集平台子应用”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目经理为授权项目内设备填写完整风险信息并提交CRM协同；THEN 平台保存风险记录并发起推送；CRM返回唯一风险单号后状态更新为“跟进中”，同时保留请求与响应关联；WHEN CRM回传处理结果和证据；THEN 平台把风险更新为“待确认”，项目经理确认风险解除后才生成关闭结论并转为“已关闭”；WHEN 设备不属于授权项目、风险关键字段缺失、CRM请求超时或重复回传同一事件；THEN 平台拒绝无权提交；推送失败保持“待推送/推送失败”并可重试；重复事件不新增风险或重复状态记录
- Phase 3授权拒绝断言：越权按“ImplementationProjectDeviceScope；风险范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“风险提出、处置和关闭”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ImplementationRisk”及数据表“imp_risk、imp_risk_treatment”；事件边界为“ImplementationRiskRaised/Closed”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目经理在S4阶段进入EXE-06割接上线环节；THEN 系统对前序里程碑节点进行门禁校验：EXE-01到货签收"已签收"、EXE-02硬件安装"已完成"、EXE-03配置调试"已完成"、EXE-04业务联调"已完成"四项全部满足；AND 门禁校验通过后允许发起割接流程；任一前序里程碑未完成则禁止发起割接，并提示未完成节点；WHEN 门禁校验通过且项目经理发起割接；THEN 系统在统一平台内调起内嵌割接管理模块（CUT），通过界面接口深度集成方式衔接，非独立部署、非简单跳转；AND 割接流程直接引用平台项目编码、设备清单、配置Log和方案信息，无需跨系统传递或重复录入；WHEN 割接流程完成（割接验证通过）；THEN 割接结果通过内部业务事件更新项目档案，S4实施部署阶段标记为“已完成”；AND S4阶段完成后准入S5验收阶段，割接记录归档至交付件页面（ACC-04）；WHEN 任一前序里程碑未完成、批准方案或设备范围在校验后变化，或同一范围已有进行中的割接任务；THEN 系统阻止创建或继续割接任务，项目S4保持原状态，并返回未通过门禁和冲突任务编号；WHEN P6提交的最终结果为失败，或发生回退后未形成成功的最终结果；THEN 项目记录对应CUT闭环事实但S4不标记完成，不准入S5；后续处理须创建或继续受控的CUT任务，不得手工改写项目阶段结果
- Phase 3授权拒绝断言：越权按“ImplementationProjectCutoverScope”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“就绪门禁汇总、快照发布与CUT消费”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ImplementationReadinessSnapshot”及数据表“proj_project_stage_snapshot”；事件边界为“ImplementationReadinessSnapshotPublished”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目经理在S5验收阶段完成现场培训并进入ACC-01现场培训电子化环节；THEN 系统提供培训记录表单填写页面，包含培训主题、培训时间、培训地点、培训内容、参训人员清单、培训人等字段；AND 项目经理填写完成并提交后，系统自动生成培训记录的二维码图片与外发链接（V1基础能力），外发链接携带项目上下文与权限令牌；WHEN 项目经理将二维码或外发链接推送至客户（V1手动复制/扫码；V2短信/邮件自动推送并可复用钉钉通知）；THEN 客户通过手机访问外发链接进入培训记录详情页，可查看培训主题、培训时间、培训地点、培训内容、参训人员清单、培训人等完整培训信息；AND 客户可在移动端完成电子签字（支持手写签名或确认按钮签字两种方式），系统记录签字时间、签字设备信息、签字IP等元数据；WHEN 客户完成签字并提交回传；THEN 系统将签字数据回传至平台，培训记录状态变更为"客户已确认"；AND 培训记录数据（含培训内容、参训人员、客户签字）自动同步至ACC-04交付件归档管理页面归档为培训交付件，支持后续查看与下载；WHEN 外发链接已过期、令牌校验失败、记录版本已被替换或客户重复提交已确认版本；THEN 系统拒绝产生新的签字结果，保持原培训记录状态，并提示重新获取有效链接或返回首次确认结果
- Phase 3授权拒绝断言：越权按“ProjectStageScope、FileBusinessScope”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“培训/报告提交、确认和问题留痕”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Acceptance”及数据表“acc_acceptance、acc_acceptance_item、acc_confirmation”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；文件哈希、版本、扫描、引用与权限拒绝记录

### ACC-02

- 需求名称：满意度收集与结果管理
- 数据对象：SatisfactionCollection
- 数据表：acc_satisfaction_collection_task、acc_satisfaction_questionnaire、acc_satisfaction_response、acc_satisfaction_result
- API：/satisfaction-tasks、/satisfaction-questionnaires/{token}/responses、/satisfaction-results
- 事件：SatisfactionTaskCreated、SatisfactionResultRecorded
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：冻结模板→指派→客户提交→判定→整改后新版本重收→归档
- 授权与数据范围：ProjectStageScope；客户一次性实例范围；答案/签字不可改写
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3 PRD验收基线：WHEN 项目到达冻结模板配置的满意度收集时点；THEN 平台生成唯一领域任务和问卷实例，冻结模板、阈值、项目、业务对象及责任人；未指派时责任人为项目经理；WHEN 客户完成全部必答项、有效签字且评分达到冻结阈值；THEN 平台形成不可变的“满意度通过”结果，按来源归档至ACC-04，并可被CLO-01或SUB-03按规则引用；WHEN 答卷缺少必答项、签字无效、评分未达标或来源业务范围不一致；THEN 平台记录失败判定并保持满意度状态为“未通过”，阻断闭环及付款门禁，保存答卷、判定和阻断原因；WHEN 项目完成整改并重新收集；THEN 平台创建新的任务、问卷及判定版本，旧答卷和旧判定仍可追溯且不能被修改；WHEN 有数据权限但无敏感字段、文件或下载权限的用户申请导出；THEN 平台生成仅包含授权字段和记录的导出文件及导出审计记录，拒绝超范围内容并保存拒绝原因
- Phase 3授权拒绝断言：越权按“ProjectStageScope；客户一次性实例范围；答案/签字不可改写”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“冻结模板→指派→客户提交→判定→整改后新版本重收→归档”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“SatisfactionCollection”及数据表“acc_satisfaction_collection_task、acc_satisfaction_questionnaire、acc_satisfaction_response、acc_satisfaction_result”；事件边界为“SatisfactionTaskCreated、SatisfactionResultRecorded”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目经理在S5验收阶段进入初验环节并完成初验；THEN 系统提供初验报告上传页面，支持上传初验报告附件（Word/PDF等格式）并填写初验时间、初验结论、初验人等关键信息；AND 初验报告上传完成后数据自动同步至ACC-04交付件归档管理页面归档为初验交付件；WHEN 项目经理在S5验收阶段进入终验环节并完成终验；THEN 系统提供终验报告上传页面，支持上传终验报告附件并填写终验时间、终验结论、终验人等关键信息；AND 终验报告上传完成后数据自动同步至ACC-04交付件归档管理页面归档为终验交付件，初验/终验报告均保留历史版本支持版本管理；WHEN 初验报告不存在却提交终验、报告附件上传失败或验收时间/结论/验收人缺失；THEN 报告保持草稿或上传失败状态，不生成当前有效版本，也不计入ACC-04和CLO-01齐套结果
- Phase 3授权拒绝断言：越权按“ProjectStageScope、FileBusinessScope”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“培训/报告提交、确认和问题留痕”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Acceptance”及数据表“acc_acceptance、acc_acceptance_item、acc_confirmation”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目进入S5验收阶段且各业务环节产生对应交付件；THEN 系统自动汇总6类交付件至ACC-04交付件归档管理页面：到货签收单（EXE-01）、实施方案（SCH-01/05）、初验报告（ACC-03）、终验报告（ACC-03）、培训记录（ACC-01）、满意度调查（ACC-02）；AND 交付件归档页面支持按类别分类展示、按上传时间/项目编码查询、按类别批量下载；WHEN 项目经理/服务经理在交付件归档页面查看交付件；THEN 系统展示每类交付件的归档状态（已归档/未归档）、归档时间、归档来源业务环节、附件下载链接；AND 任一类别交付件未归档时系统给出提示，便于项目经理跟进归档进度，归档完成后作为CLO-01闭环条件校验的必传交付件数据来源；WHEN 来源记录未批准/未确认、来源版本失效、文件哈希校验失败或用户无下载权限；THEN 对应交付件保持未归档/失效或不可下载状态，不计入CLO-01齐套结果，并展示来源记录和失败原因
- Phase 3授权拒绝断言：越权按“ProjectStageScope、FileBusinessScope；ACC归档”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“齐套检查、审核和归档分离”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“DeliveryArtifact”及数据表“acc_delivery_artifact、acc_artifact_review、acc_archive_record”；事件边界为“ArtifactAccepted/Archived”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目满足冻结模板的全部条件且来源数据有效；THEN 平台生成通过快照并允许创建CLO-02申请；WHEN 满意度未达标、交付件未归档、设备位置缺失、后代项目未闭环或来源版本已失效；THEN 平台阻断闭环，列出对象、规则和缺失原因，且不得创建审批实例
- Phase 3授权拒绝断言：越权按“ProjectStageScope；全部后代项目门禁与审批范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“门禁校验、冻结流程审批、整改和闭环；不创建回访节点”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ProjectClosure、ClosureGateSnapshot、SatisfactionCollection”及数据表“acc_project_closure、acc_closure_gate_snapshot、acc_closure_review、acc_satisfaction_result”；事件边界为“ProjectClosureCompleted”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CLO-02

- 需求名称：项目闭环审批
- 数据对象：ProjectClosure、ClosureGateSnapshot、SatisfactionCollection
- 数据表：acc_project_closure、acc_closure_gate_snapshot、acc_closure_review、acc_satisfaction_result
- API：/closure-gates/{projectId}、/project-closures
- 事件：ProjectClosureCompleted
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：门禁校验、冻结流程审批、整改和闭环；不创建回访节点
- 授权与数据范围：ProjectStageScope；全部后代项目门禁与审批范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3 PRD验收基线：WHEN 项目经理使用最新有效CLO-01快照提交闭环申请；THEN 平台冻结流程和材料快照，按定义生成首个审批待办且不创建回访节点；WHEN 全部配置节点通过且快照持续有效；THEN 平台形成闭环记录，将项目生命周期状态更新为“正常闭环（NORMAL_CLOSED）”并发布闭环事件；WHEN 快照失效、审批人无权、重复提交或任一节点驳回；THEN 平台拒绝/终止本次推进并保留原因；整改后须形成新校验和新实例
- Phase 3授权拒绝断言：越权按“ProjectStageScope；全部后代项目门禁与审批范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“门禁校验、冻结流程审批、整改和闭环；不创建回访节点”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ProjectClosure、ClosureGateSnapshot、SatisfactionCollection”及数据表“acc_project_closure、acc_closure_gate_snapshot、acc_closure_review、acc_satisfaction_result”；事件边界为“ProjectClosureCompleted”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 服务经理选择有效合同、项目、合格服务商并提交额度内申请；THEN 平台冻结申请版本与资质快照，生成审批实例并把状态更新为“审批中”；WHEN 合同无可用额度、范围重复、服务商资质失效、字段缺失或用户无项目权限；THEN 平台拒绝提交，申请保持草稿并列出阻断项，不占用合同转包额度
- Phase 3授权拒绝断言：越权按“OrganizationSupplierScope；项目/供应商范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“平台内转包审批、价格审批与版本冻结”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“SubcontractRequest”及数据表“res_subcontract_request”；事件边界为“SubcontractApproved”，文件边界为“FileArtifact”，外部集成为“OA”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 管理员发布节点、角色、金额区间和门禁完整的流程版本；THEN 平台保存发布版本，新申请冻结命中版本并按顺序/会签规则生成待办；WHEN 流程缺少必需门禁、适用范围冲突、审批角色无法解析或用户无发布权限；THEN 平台拒绝发布/提交，旧发布版本继续有效，申请保持草稿并显示配置问题
- Phase 3授权拒绝断言：越权按“OrganizationSupplierScope；项目/供应商范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“平台内转包审批、价格审批与版本冻结”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“SubcontractRequest”及数据表“res_subcontract_request”；事件边界为“SubcontractApproved”，文件边界为“FileArtifact”，外部集成为“OA”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 适用付款阶段存在范围一致、有效且达标的ACC-02结果；THEN 平台仅解除该阶段门禁并允许SUB-04继续处理；WHEN 结果缺失、无效、未达标、版本/范围不一致或用户尝试手工放行；THEN 平台保持锁定，拒绝付款登记及财务下发，并记录门禁快照和拒绝原因；WHEN 整改后新问卷版本达标；THEN 平台以新版本重新判定，保留原不达标结果和整改历史
- Phase 3授权拒绝断言：越权按“OrganizationSupplierScope；付款门禁权限；满意度只读引用”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“付款前置满意度事实、批准版本和财务确认”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“PaymentGate、SatisfactionCollection”及数据表“res_payment_gate、acc_satisfaction_result”；事件边界为“PaymentGateChanged”，文件边界为“FileArtifact”，外部集成为“财务系统”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 财务建立总额等于各阶段之和且不超过批准金额的付款计划；THEN 平台保存计划版本；门禁满足且财务返回唯一付款单号后登记实际付款并更新阶段及累计余额；WHEN 金额超限、阶段合计不等、维护类门禁锁定、凭证号重复或用户无财务权限；THEN 平台拒绝计划/付款登记，保持批准余额和已支付记录不变并显示阻断原因
- Phase 3授权拒绝断言：越权按“OrganizationSupplierScope；付款门禁权限；满意度只读引用”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“付款前置满意度事实、批准版本和财务确认”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“PaymentGate、SatisfactionCollection”及数据表“res_payment_gate、acc_satisfaction_result”；事件边界为“PaymentGateChanged”，文件边界为“FileArtifact”，外部集成为“财务系统”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 指定审批人通过有效链接或平台待办查看冻结版本并审批通过；THEN 平台保存批准金额和审批证据，SUB-04读取该版本上限，并在财务返回业务单号后标记同步成功；WHEN 链接过期/重复使用、身份不符、版本已撤回、审批驳回或财务同步失败；THEN 平台拒绝处理或保持“已驳回/同步失败”，不生成批准金额或付款额度，允许平台内重发/重试
- Phase 3授权拒绝断言：越权按“OrganizationSupplierScope；项目/供应商范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“平台内转包审批、价格审批与版本冻结”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“SubcontractRequest”及数据表“res_subcontract_request”；事件边界为“SubcontractApproved”，文件边界为“FileArtifact”，外部集成为“OA”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### CUS-01

- 需求名称：用户资产库
- 数据对象：Customer、CustomerContact、CustomerRelationshipSnapshot
- 数据表：cus_customer、cus_customer_contact、cus_project_customer_contact_relation、cus_customer_relationship_snapshot
- API：/customers/{id}/panorama
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：CRM及平台内项目/设备/服务事实
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：按授权聚合客户全景；单卡片失败保留其他卡片并显示最近成功截止时间，不以0替代未知
- 授权与数据范围：OrganizationCustomerScope；敏感联系人、故障、配置和维保字段专项权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3 PRD验收基线：WHEN 有权用户打开客户资产库；THEN 平台按CRM客户ID返回权限过滤后的单位、联系人、项目、设备、故障和服务记录，展示各卡片数量、来源和截止时间；WHEN 同一设备被父子项目引用、部分来源异常或用户无某项目/敏感字段权限；THEN 平台按序列号去重，异常卡片标记数据延迟，无权数据不参与数量且不显示明细
- Phase 3授权拒绝断言：越权按“OrganizationCustomerScope；敏感联系人、故障、配置和维保字段专项权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“按授权聚合客户全景；单卡片失败保留其他卡片并显示最近成功截止时间，不以0替代未知”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Customer、CustomerContact、CustomerRelationshipSnapshot”及数据表“cus_customer、cus_customer_contact、cus_project_customer_contact_relation、cus_customer_relationship_snapshot”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“CRM及平台内项目/设备/服务事实”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；脱敏请求响应、幂等键、重试/对账与降级记录

### CUS-02

- 需求名称：服务等级管理
- 数据对象：Customer、CustomerContact、CustomerRelationshipSnapshot
- 数据表：cus_customer、cus_customer_contact、cus_project_customer_contact_relation、cus_customer_relationship_snapshot
- API：/customers/{id}/service-level-revisions
- 事件：CustomerServiceLevelChanged
- 外部集成：N/A（平台内部契约）
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：结束原有效区间并生成新等级版本；新业务动作冻结等级与策略版本，历史业务快照不回写
- 授权与数据范围：OrganizationCustomerScope；服务经理或管理层客户等级维护权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试
- Phase 3 PRD验收基线：WHEN 有权人员为客户设置有效等级、依据、生效时间和服务策略；THEN 平台结束原等级区间并生成新版本，后续项目/割接/服务动作保存命中等级与策略快照；WHEN 等级区间重叠、策略缺失、客户不存在或用户无客户管理权限；THEN 平台拒绝生效并保持原有效等级和既有业务动作不变
- Phase 3授权拒绝断言：越权按“OrganizationCustomerScope；服务经理或管理层客户等级维护权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“结束原有效区间并生成新等级版本；新业务动作冻结等级与策略版本，历史业务快照不回写”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Customer、CustomerContact、CustomerRelationshipSnapshot”及数据表“cus_customer、cus_customer_contact、cus_project_customer_contact_relation、cus_customer_relationship_snapshot”；事件边界为“CustomerServiceLevelChanged”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据

### CUS-03

- 需求名称：客户信息管理CURD
- 数据对象：Customer、CustomerContact、CustomerRelationshipSnapshot
- 数据表：cus_customer、cus_customer_contact、cus_project_customer_contact_relation、cus_customer_relationship_snapshot
- API：/customers
- 事件：CustomerMerged、MasterDataSynchronized
- 外部集成：CRM
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：CRM客户同步、临时客户受控创建与合并；权威字段不可被平台覆盖
- 授权与数据范围：OrganizationCustomerScope；CRM权威字段只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3 PRD验收基线：WHEN 服务经理在平台客户管理页面点击"创建客户"按钮，录入客户基本信息（客户编码、客户名称、行业、级别、联系方式、所属办事处等），客户编码已存在；THEN 平台校验客户编码唯一性失败，提示"客户编码已存在"，禁止创建；AND 客户编码校验通过后，平台保存客户档案，客户状态为"启用"，同时记录创建人、创建时间；WHEN 平台通过CRM接口（INT-03）执行客户信息同步任务且未发生来源键冲突；THEN 平台按客户编码匹配CRM客户数据：已存在则更新CRM字段（客户名称、行业、级别、联系方式等以CRM为准），不存在则创建新客户档案；AND 平台扩展字段（如自定义标签、服务等级）保持平台本地值不变，CRM同步数据与本地数据并存，同步任务执行结果（成功/失败/新增/更新数量）记录至同步日志；WHEN 服务经理在客户档案页面查看客户详情；THEN 页面展示客户基本信息、关联项目列表（项目编码/项目名称/派生展示状态/当前阶段/负责人）、关联设备列表（设备序列号/设备型号/在网状态），列表支持分页与排序；AND 在项目档案页面可查看所属客户信息，在设备序列号档案（EQP-01）页面可查看所属客户信息，实现客户与项目、客户与设备的双向映射；WHEN 服务经理对客户档案执行删除操作，且该客户存在关联项目、设备、联系人、领域任务或外部问题记录等业务数据；THEN 平台校验存在关联业务数据，提示"客户存在关联业务数据，禁止删除"，删除操作被拦截；AND 若客户无关联业务数据，平台执行软删除（标记为"已删除"状态），客户在客户列表中不再展示，但数据保留备查，支持有权限角色恢复
- Phase 3授权拒绝断言：越权按“OrganizationCustomerScope；CRM权威字段只读”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“CRM客户同步、临时客户受控创建与合并；权威字段不可被平台覆盖”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Customer、CustomerContact、CustomerRelationshipSnapshot”及数据表“cus_customer、cus_customer_contact、cus_project_customer_contact_relation、cus_customer_relationship_snapshot”；事件边界为“CustomerMerged、MasterDataSynchronized”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“CRM”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### CUS-04

- 需求名称：项目联系人管理
- 数据对象：Customer、CustomerContact、CustomerRelationshipSnapshot
- 数据表：cus_customer、cus_customer_contact、cus_project_customer_contact_relation、cus_customer_relationship_snapshot
- API：/customer-contacts、/projects/{id}/customer-contacts
- 事件：CustomerContactChanged
- 外部集成：CRM
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：联系人维护、项目角色时态关系和业务发生时联系信息快照
- 授权与数据范围：OrganizationCustomerScope、ProjectTreeScope；联系人字段专项权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3 PRD验收基线：WHEN 项目经理在项目档案页面点击"新增联系人"按钮，录入联系人信息（姓名、职务、联系电话、邮箱、所属客户单位等），并勾选"设为主联系人"，而该项目已存在主联系人；THEN 平台校验主联系人唯一性失败，提示"项目已存在主联系人，请先取消原主联系人主标识"，新增操作被拦截；AND 项目经理取消原主联系人主标识后，可设置新联系人为项目主联系人，主联系人字段同步至项目档案首页以显著标识展示；WHEN 项目经理对项目其他联系人执行修改或删除操作；THEN 平台保存修改或执行删除（联系人状态变更为"已删除"，数据保留备查），变更操作留存变更历史（含变更人、变更时间、变更字段、变更前值、变更后值）；AND 主联系人不受批量删除影响，删除主联系人时需先指定其他联系人为主联系人或确认项目暂无主联系人；WHEN 两名项目经理并发设置主联系人发生数据版本冲突；THEN 平台保留先成功的唯一主联系人并拒绝后提交操作，提示后提交者刷新联系人列表后重试
- Phase 3授权拒绝断言：越权按“OrganizationCustomerScope、ProjectTreeScope；联系人字段专项权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“联系人维护、项目角色时态关系和业务发生时联系信息快照”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Customer、CustomerContact、CustomerRelationshipSnapshot”及数据表“cus_customer、cus_customer_contact、cus_project_customer_contact_relation、cus_customer_relationship_snapshot”；事件边界为“CustomerContactChanged”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“CRM”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 服务经理在设备序列号档案页面输入设备序列号查询；THEN 平台按序列号聚合展示6类设备数据（出厂信息/官网信息/在网版本/技术公告/维保信息/配置Log），以Tab页签形式分模块展示；AND 各模块数据来源标识清晰（MES同步/官网/ITR同步/平台维护），数据更新时间显示，便于判断数据时效性；WHEN 设备序列号档案页面中"在网版本"或"技术公告"模块的数据通过ITR接口（INT-02/INT-04）同步更新；THEN 平台按同步规则（增量同步）拉取ITR最新版本信息与技术公告，同步后页面展示最新数据；AND 同步历史保留（含同步时间、同步来源、同步字段、同步结果），便于追溯设备数据变更；WHEN 工程师在设备序列号档案页面下载配置Log（关联EQP-02）；THEN 平台生成配置Log下载链接，工程师点击下载，下载操作记录下载人、下载时间、下载文件；AND 下载链接设置有效期，过期后需重新生成，避免链接泄露；WHEN 设备直接归属项目发生变更或设备同时参与其他项目；THEN 平台保证任一时点只有一个最具体项目直接归属；变更保留生效起止时间，其他项目仅建立带类型业务关联，祖先项目设备统计按设备标识去重；WHEN 新增设备的序列号发生重复冲突，或拟建立的项目/客户直接归属与现有有效期发生重叠；THEN 平台禁止创建重复设备或生效重叠归属，保留原档案与原归属，并生成待处理冲突记录供授权人员核对
- Phase 3授权拒绝断言：越权按“ProjectDeviceScope；当前归属、框板关系维护与祖先范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“设备档案、配置Log引用、框板关系、扫码和唯一归属”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Device、DeviceArchive、DeviceComponentRelation、DeviceCurrentAssignment”及数据表“ast_device、ast_device_component_relation、ast_device_current_assignment、ast_device_assignment_history”；事件边界为“DeviceAssigned、DeviceComponentRelationChanged”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN EXE-03/04回调的配置Log设备序列号与任务设备一致且文件哈希未存在；THEN 平台保存不可覆盖的原始文件，生成配置Log版本，关联采集任务、设备档案和项目归属快照，并展示解析状态；WHEN 有权工程师下载配置Log；THEN 平台生成短时有效下载链接，校验项目与敏感资料权限，记录下载人、设备、文件版本和时间，链接过期后不可继续访问；WHEN 序列号不一致、文件哈希重复、解析失败或无权用户请求预览/下载/删除；THEN 平台分别进入待核对、返回既有记录、保留原文件待补录，或拒绝访问；任何异常均不把文件错误挂接到其他设备；WHEN 配置Log已被版本历史、ITR问题记录、割接或巡检证据引用；THEN 普通工程师删除请求被阻止，管理员受控软删除后仍保留引用元数据、文件哈希和删除说明
- Phase 3授权拒绝断言：越权按“ProjectDeviceScope；当前归属、框板关系维护与祖先范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“设备档案、配置Log引用、框板关系、扫码和唯一归属”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Device、DeviceArchive、DeviceComponentRelation、DeviceCurrentAssignment”及数据表“ast_device、ast_device_component_relation、ast_device_current_assignment、ast_device_assignment_history”；事件边界为“DeviceAssigned、DeviceComponentRelationChanged”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 有权用户按序列号打开设备档案；THEN 平台返回七类信息的当前值、来源、版本和截止时间，并展示唯一当前归属及历史/参与项目关系；WHEN 来源序列号冲突、部分系统不可用、设备在同一时点出现多直接归属或用户无敏感权限；THEN 平台标记待核对/数据延迟，阻止冲突归属生效，并脱敏或隐藏无权配置Log、合同和故障正文
- Phase 3授权拒绝断言：越权按“ProjectDeviceScope；当前归属、框板关系维护与祖先范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“设备档案、配置Log引用、框板关系、扫码和唯一归属”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Device、DeviceArchive、DeviceComponentRelation、DeviceCurrentAssignment”及数据表“ast_device、ast_device_component_relation、ast_device_current_assignment、ast_device_assignment_history”；事件边界为“DeviceAssigned、DeviceComponentRelationChanged”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 收到合法MES设备事件；THEN 平台按MES设备ID/序列号更新只读基础字段，保存源版本和同步时间且不改变项目归属等平台字段；WHEN 事件重复乱序、序列号冲突、字段缺失或批次部分失败；THEN 平台幂等忽略旧事件或进入隔离队列，保留最近成功值并把批次标记“部分成功”
- Phase 3授权拒绝断言：越权按“ProjectDeviceScope；MES字段只读”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“MES来源版本幂等同步与冲突隔离”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“AssetSyncSnapshot、Device”及数据表“ast_asset_sync_batch、ast_asset_sync_item、ast_device”；事件边界为“MasterDataSynchronized”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“MES”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 工程师扫描可识别且有权设备码；THEN 平台返回权限裁剪后的设备档案、来源和截止时间，并记录扫码查询历史；WHEN 摄像头不可用、序列号不存在/重复或用户无设备/敏感字段权限；THEN 平台提供手工输入或标记待核对，拒绝无权字段且不改变设备档案
- Phase 3授权拒绝断言：越权按“ProjectDeviceScope；当前归属、框板关系维护与祖先范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“设备档案、配置Log引用、框板关系、扫码和唯一归属”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Device、DeviceArchive、DeviceComponentRelation、DeviceCurrentAssignment”及数据表“ast_device、ast_device_component_relation、ast_device_current_assignment、ast_device_assignment_history”；事件边界为“DeviceAssigned、DeviceComponentRelationChanged”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN ITR问题事件可映射到项目或项目设备；THEN 平台按问题单号更新只读记录，项目页面展示来源、同步时间和16项字段并支持下钻ITR；WHEN 事件重复乱序、项目/设备映射冲突、ITR不可用或用户无项目权限；THEN 平台忽略旧事件或标记待映射/数据延迟，不重复计数且不返回无权问题明细
- Phase 3授权拒绝断言：越权按“ProjectDeviceScope；当前归属、框板关系维护与祖先范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“设备档案、配置Log引用、框板关系、扫码和唯一归属”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Device、DeviceArchive、DeviceComponentRelation、DeviceCurrentAssignment”及数据表“ast_device、ast_device_component_relation、ast_device_current_assignment、ast_device_assignment_history”；事件边界为“DeviceAssigned、DeviceComponentRelationChanged”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 用户选择时间、办事处和项目统计粒度；THEN 平台返回状态分布、闭环分子/分母/比率和截止时间，点击数据点进入保留同一条件的项目列表；WHEN 当前阶段/生命周期状态缺失导致派生展示状态无法计算、批次部分失败、根/节点粒度未选择或用户无办事处权限；THEN 平台标记待核对/数据不完整并拒绝混合口径，且不显示无权项目数量与明细
- Phase 3授权拒绝断言：越权按“OrganizationReportScope；字段级脱敏”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“项目状态指标口径计算、快照发布和水位展示”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“MetricSnapshot”及数据表“ana_metric_snapshot”；事件边界为“MetricSnapshotPublished”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 一线工程师进入割接首页并办理ITR或平台项目来源任务；THEN 平台展示来源、项目、设备和负责人上下文，进入P2等级确认，并保存来源业务键和创建留痕；WHEN 一线工程师输入设备序列号创建自建割接任务；THEN 平台只展示本人有权项目和设备，确认后生成唯一CutoverTask并进入P2，不创建通用工单；WHEN D级任务完成P2，或A/B/C级任务完成P2；THEN D级直接进入P4，A/B/C级进入P3；任务详情五步工作台展示P2～P6当前步骤及历史完成步骤，P1接入事实在任务上下文中只读展示；WHEN 相同来源业务键被重复提交、用户无项目权限或设备上下文无法确认；THEN 重复请求返回既有任务；无权或上下文不完整的请求不创建任务，并记录拒绝原因和操作时间
- Phase 3授权拒绝断言：越权按“CutoverTaskScope；项目/设备范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“任务创建、分级评估、风险/调研矩阵”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“CutoverTask、CutoverAssessment”及数据表“cut_task、cut_assessment”；事件边界为“CutoverApproved”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 一线工程师打开处于P2的割接任务；THEN 平台展示项目上下文和等级问卷，允许填写问卷答案并选择最终A/B/C/D等级；WHEN 一线工程师提交完整问卷和人工等级；THEN 平台保存答案、上下文快照、最终等级和提交留痕；A/B/C级进入P3，D级直接进入P4；WHEN 用服经理在P5发现问卷或最终等级不合理并将相应评审项判定为不通过；THEN 当前审批节点驳回并填写原因，任务按驳回流程返回修改，不在P2生成第二个审批节点；WHEN 问卷必填项、最终等级缺失，用户无权，或项目设备上下文版本已失效；THEN 平台阻止提交并保持P2状态，展示缺失或失效原因，不生成自动建议等级作为替代
- Phase 3授权拒绝断言：越权按“CutoverTaskScope；项目/设备范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“任务创建、分级评估、风险/调研矩阵”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“CutoverTask、CutoverAssessment”及数据表“cut_task、cut_assessment”；事件边界为“CutoverApproved”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-03

- 需求名称：割接采集清单动态多维绑定生成
- 数据对象：CutoverTask、CutoverChecklist、CollectionTask
- 数据表：cut_task、cut_cutover_checklist、cut_cutover_checklist_item、cut_cutover_checklist_item_result、plt_collection_task
- API：/cutover-tasks/{id}/checklist、/cutover-tasks/{id}/checklist/actions/rematch、/cutover-tasks/{id}/checklist/items/{itemId}/actions/request-collection
- 事件：CollectionTaskRequested、CollectionResultAvailable、CutoverChecklistItemResultLinked
- 外部集成：现有采集平台子应用
- 文件契约：FileArtifact
- 工作流/状态：CutoverTask内P3清单版本在同一工作台动态匹配、人工填写/上传、采集任务下发、结果版本选择和配置缺口留痕；不复制DAC技术状态
- 授权与数据范围：CutoverTaskScope、BusinessObjectDeviceCredentialScope；服务端按清单项和设备范围裁剪
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3 PRD验收基线：WHEN 割接任务等级为A/B/C级（非简易流程），一线工程师进入P3调研及风险考察页面；THEN 页面展示割接信息采集表（含割接类型、组网模式、设备类型、当前版本、升级后版本字段），项目信息表和割接等级表在当前页面固定展示；AND 升级后版本字段仅当割接类型为"版本升级"时显示；WHEN 一线工程师在割接信息采集表中选择割接类型、组网模式、设备类型；THEN 系统根据动态多维绑定（割接类型×组网模式×设备类型×割接等级，可扩展）从统一采集项主数据（按采集项类型区分）中匹配并动态渲染对应的采集清单，界面按采集项类型分块显示（业务调研项区块/风险考察项区块/双机部署检查项区块）；AND 清单中每项展示采集项名称、描述、检查结果/反馈结果、是否必填等字段，必填项以标识区分；AND 文本、选择、表格、文件、业务引用和设备采集项在同一P3工作台按已发布界面格式显示；条件变化时自动重新匹配并显示项目差异，不跳转独立采集阶段；WHEN 一线工程师选择组网模式为5类高可靠性组网模式之一（如VSM双机/静默双机/DRP双机/普通双机/集群）；THEN 系统按所选组网模式动态展示对应的双机部署规范性检查表（VSM双机17项/静默双机25项/DRP双机23项/普通双机24项/集群8项）；AND 每项检查结果反馈"是否已执行"（下拉）+特殊情况备注；WHEN 统一采集项中风险考察项配置了外部数据源接口（如技术公告检查项）；THEN 所有情况必选加载当前版本外部数据（如技术公告列表，每条数据项显示编号/主题/描述/解决方案，补充本次是否涉及+采取措施）；AND 若割接类型为版本升级，同时加载当前版本和升级后版本外部数据；WHEN 一线工程师在已生成清单基础上自定义新增或删除调研项和风险项；THEN 系统允许新增自定义项（标记来源），禁止删除系统预置必选项；WHEN 一线工程师点击保存按钮；THEN 系统保存清单草稿、当前已填内容和填写进度，清单保持未提交状态（V1能力）；AND V1不支持导出清单（导出为V2子功能，来源R013），V2实现下载按钮导出清单（风险考察项+业务调研项）；AND V1内置基础跳转能力（采集清单保存后通过任务状态流转自动进入P4），独立流程跳转优化为V2子功能（来源R015）；WHEN A/B/C级任务没有命中适用规则，或命中规则存在定义冲突；THEN 平台展示配置缺口或冲突，允许一线补充任务级自定义项并标记配置缺口后继续；系统预置必填项缺失时仍阻止提交，D级任务按CUT-02直接进入P4；WHEN 外部数据源加载失败或超时；THEN 平台标记对应采集项“外部数据加载失败”，保存失败记录，并允许工程师上传人工查询证据后以“人工”来源填写结果；WHEN 一线工程师在设备采集项发起采集，或DAC返回执行结果；THEN 平台创建并展示与任务、清单版本、采集项和设备绑定的CollectionTask；回调结果在原采集项位置回填状态、摘要和证据引用，仍由CUT规则判断是否满足该项；WHEN 采集平台不可用、授权失败或回调超时；THEN 原项目保留下发失败状态和证据，允许重试或按权限采用手工填写/上传证据，不新增独立采集页面、不将失败任务改写为成功
- Phase 3授权拒绝断言：越权按“CutoverTaskScope、BusinessObjectDeviceCredentialScope；服务端按清单项和设备范围裁剪”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“CutoverTask内P3清单版本在同一工作台动态匹配、人工填写/上传、采集任务下发、结果版本选择和配置缺口留痕；不复制DAC技术状态”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“CutoverTask、CutoverChecklist、CollectionTask”及数据表“cut_task、cut_cutover_checklist、cut_cutover_checklist_item、cut_cutover_checklist_item_result、plt_collection_task”；事件边界为“CollectionTaskRequested、CollectionResultAvailable、CutoverChecklistItemResultLinked”，文件边界为“FileArtifact”，外部集成为“现有采集平台子应用”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-04

- 需求名称：割接方案编审
- 数据对象：CutoverPlan
- 数据表：cut_plan_revision、cut_step
- API：/cutover-tasks/{id}/plan-revisions
- 事件：CutoverApproved
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：方案编审、分级审批和版本冻结
- 授权与数据范围：CutoverTaskScope；分级审批权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3 PRD验收基线：WHEN 一线工程师在割接任务中发起方案编审；THEN 页面展示"是否已有割接方案"选项，风险考察结果从上一步P3流程带入展示，业务调研录入结果+风险考察录入结果+割接项目+用户资产库等信息字段可被引用和带入；WHEN 一线工程师选择"是否已有割接方案"为"是"；THEN 系统提供"上传完整方案"按钮，一线工程师直接上传完整方案文件，跳过模板填写环节；AND 平台校验文件有效性、安全性、方案归属和人工确认，不强制解析或补齐在线模板字段；校验通过后形成方案版本并流转至CUT-05；WHEN 一线工程师选择"是否已有割接方案"为"否"；THEN 系统展示方案模板，方案分为割接概述和执行操作两大章节；AND 割接概述含项目割接描述（带入）、计划表、割接前拓扑上传、割接后拓扑上传、设备清单（带入）、组网配置上传、割接保障人员安排表等子章节；AND 执行操作含预估风险及应对措施、割接前操作清单、执行操作清单、收尾收集清单、后业务测试表、回退方案说明、回退步骤、割接后保障等子章节；WHEN 一线工程师填写割接保障人员安排表；THEN 系统展示客户/迪普一线工程师/迪普二线工程师/迪普研发四类角色，并保存每类角色的姓名、任务描述、联系电话和到位时间；WHEN 一线工程师填写预估风险及应对措施；THEN 系统加载CUT-03中所有结果为“否”的风险项，保存每个风险项与应对措施的一一关联及未填写状态；WHEN 一线工程师点击"下载割接初稿"按钮；THEN 系统生成并返回当前方案版本的初稿文件，记录下载人、下载时间和方案版本；WHEN 割接任务等级为D级（简易流程）；THEN 系统仅展示割接各阶段操作步骤与回退步骤填写窗口，不加载A/B/C级完整方案章节；AND 一线工程师填写完毕提交后，方案按D级简易审批层级（发起人→用服经理）流转至CUT-05分级审批；WHEN 一线工程师填写完毕点击"下一步"；THEN 系统将方案状态更新为“待审批”，保存提交版本并创建CUT-05分级审批实例；WHEN A/B/C级方案缺少必需章节、存在未填写应对措施的风险项、上传文件校验失败或引用的采集清单版本已失效；THEN 平台阻止提交并保持方案草稿状态，展示缺失章节、未处置风险、文件错误或失效来源，不创建CUT-05审批实例
- Phase 3授权拒绝断言：越权按“CutoverTaskScope；分级审批权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“方案编审、分级审批和版本冻结”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“CutoverPlan”及数据表“cut_plan_revision、cut_step”；事件边界为“CutoverApproved”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-05

- 需求名称：割接分级审批
- 数据对象：CutoverPlan
- 数据表：cut_plan_revision、cut_step
- API：/cutover-tasks/{id}/plan-revisions
- 事件：CutoverApproved
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：方案编审、分级审批和版本冻结
- 授权与数据范围：CutoverTaskScope；分级审批权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3 PRD验收基线：WHEN 一线工程师提交割接方案（含采集清单）至审批环节；THEN 系统按割接等级自动生成对应审批节点（A级4级串行：发起人→用服经理→二线→研发；B级3级串行：发起人→用服经理→二线；C/D级2级串行：发起人→用服经理）；AND 审批页面汇总引用项目信息（11字段）、采集信息分析、风险考察项（风险项1-4）、业务调研项（调研项1-4）、割接等级（5个评估维度）、割接方案等前序页面数据；AND 首节点审批人收到审批待办并通过推送通知（站内+邮件+IM多通道）收到提醒（接口预留（INT-10），V1实现优先级最低，不可用时通过站内消息替代）；WHEN 割接等级为A/B级；THEN V1审批页面采集信息分析中展示割接操作时间字段，但不启用提前时间自动合规判断（提前时间合规判断为V2子功能，来源R034）；AND V2版本系统启用提前时间合规判断，按割接类型匹配提前时间阈值（10类规则表），计算割接操作时间与方案提交时间差，自动判断"是否未按规定提前时间提交"并在采集信息分析中带出结果（是/否）；WHEN 割接等级为C/D级；THEN 系统不启用提前时间合规判断，"是否未按规定提前时间提交"字段不展示或置空（V1/V2均不启用）；WHEN 当前审批节点审批人查看评审表单；THEN 页面展示5项合理性评审项（割接前准备工作是否合理/业务测试内容是否合理/割接执行步骤是否合理/割接回退步骤是否合理/其他是否合理），每项单选是/否，选"否"时展示不合理原因输入框；WHEN 当前审批节点审批人执行通过操作并填写反馈意见；THEN 流转至下一审批节点（若有），全部节点通过后方案状态变更为"已锁定"，跳转至P6割接跟踪与闭环（CUT-06执行闭环）；AND 审批意见、审批人、审批时间完整记录，审批历史可追溯；WHEN 当前审批节点审批人执行驳回操作并填写反馈意见（必填）；THEN 方案退回一线工程师修改，方案状态变更为"已驳回"，跳转至P4割接方案修改；AND 一线工程师修改后重新提交，从首节点开始重新审批；WHEN 审批人无当前节点权限、方案或采集清单版本已失效、评审项缺失，或任一合理性评审为“否”却选择通过；THEN 平台阻止审批并保持当前节点状态，展示权限、版本或评审校验失败原因，不锁定方案且不创建下一节点待办
- Phase 3授权拒绝断言：越权按“CutoverTaskScope；分级审批权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“方案编审、分级审批和版本冻结”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“CutoverPlan”及数据表“cut_plan_revision、cut_step”；事件边界为“CutoverApproved”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-06

- 需求名称：割接跟踪与闭环
- 数据对象：CutoverClosure、CollectionTask
- 数据表：cut_cutover_closure、plt_collection_task
- API：/cutover-tasks/{id}/closure
- 事件：CollectionResultConsumed、CutoverCompleted
- 外部集成：现有采集平台子应用、ITR
- 文件契约：FileArtifact
- 工作流/状态：P6闭环填写、INT-12证据引用、提交归档和结果回流；不建立逐步骤执行或稳定观察
- 授权与数据范围：CutoverTaskScope、BusinessObjectDeviceCredentialScope
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3 PRD验收基线：WHEN CUT-05全部节点通过，一线工程师进入P6；THEN 平台展示批准方案引用、四类结果字段、附件、遗留项和INT-12连接/采集入口，不生成逐步骤执行或稳定观察任务；WHEN 一线工程师通过INT-12下发采集任务并收到成功或失败回调；THEN 平台按任务ID和事件ID保存证据；失败时允许关联原任务上传人工结果，且保留原失败事实；WHEN 一线工程师填写闭环结果并点击提交；THEN 平台保存闭环单和归档时间，任务直接进入已归档并结束流程；遗留项随闭环快照保存但不阻止归档；WHEN 最终结果为失败，或ITR出方向接口暂不可用；THEN 失败闭环仍归档；平台保留ITR待回传/待建单状态和重试记录，不把通知或HTTP成功当作业务完成
- Phase 3授权拒绝断言：越权按“CutoverTaskScope、BusinessObjectDeviceCredentialScope”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“P6闭环填写、INT-12证据引用、提交归档和结果回流；不建立逐步骤执行或稳定观察”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“CutoverClosure、CollectionTask”及数据表“cut_cutover_closure、plt_collection_task”；事件边界为“CollectionResultConsumed、CutoverCompleted”，文件边界为“FileArtifact”，外部集成为“现有采集平台子应用、ITR”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### CUT-07

- 需求名称：割接后台配置
- 数据对象：CutoverPlan
- 数据表：cut_plan_revision、cut_step
- API：/cutover-config/types、/cutover-config/network-modes、/cutover-config/checklist-items、/cutover-config/binding-rules
- 事件：CutoverConfigurationPublished
- 外部集成：基础平台字典、可选外部动态数据源
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：草稿→已发布→已停用；发布前校验稳定编码、版本、动态维度、引用启用状态和条件可判定性，已生成实例继续按消费版本解释
- 授权与数据范围：系统管理员配置权限；已发布版本和历史业务实例不可覆盖
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3 PRD验收基线：WHEN 管理员在后台维护割接类型（10类）和组网模式（5类）；THEN 系统支持新增/编辑/启用/停用/排序割接类型与组网模式，配置项包含编码、名称、描述、启用状态；AND 配置发布后仅对生效时间后的新任务可选，已创建任务继续显示其原配置版本；WHEN 管理员在后台维护设备类型（主字典数据）；THEN 系统支持新增/编辑/启用/停用设备类型（已知示例FW/SW/ADX，非穷举，支持扩展新增）；AND 设备类型由系统管理员维护，作为CUT-03动态多维绑定的维度之一；WHEN 管理员在后台维护统一采集项配置（合并原业务调研项配置+风险考察项配置+双机部署规范检查表配置为1个统一采集项配置模块）；THEN 系统支持通过"采集项类型"字段区分业务调研项/风险考察项/双机部署检查项，三类共用相同的配置字段结构：采集项ID（主键）、采集项类型（枚举）、项命名（文本）、项含义（文本）、界面格式（单选项/多选框/下拉框/输入框/表格/表单/文件上传按钮等）、界面格式动态数据查询（传入上下文信息调接口获取数据）、反馈结果内容（如：是/否+特殊情况备注；自定义文本；文件上传等）、数据关联关系（动态多维绑定，已知4维可扩展）、外部数据源接入（可选，配置外部数据来源接口）、所属子表（可选，双机部署检查项关联5类组网模式检查表）、启用状态、排序、操作（新增/编辑/保存/删除）；AND 此表格支持后台维护编辑，各字段的界面格式与内容用于采集信息分析与结果页面的填写说明；WHEN 管理员在后台为统一采集项配置外部数据源接入能力（所有采集项类型均可配置，实际应用中风险考察项使用较多）；THEN 系统支持配置数据来源接口（传入上下文信息调接口获取数据），支持动态数据查询；AND 接口加载的数据项显示编号/主题/描述/解决方案，补充本次是否涉及+采取措施；WHEN 管理员在后台配置动态多维绑定关系；THEN 系统支持配置"割接类型×组网模式×设备类型×割接等级"（已知4维）的动态多维绑定关系，并支持扩展新增维度；AND 绑定关系新版本发布后由新生成清单使用，已生成清单继续按原绑定版本解释；WHEN 管理员在后台维护双机部署检查项的"所属子表"关联（采集项类型=双机部署检查项）；THEN 系统支持5类组网模式共124行检查项的CRUD（VSM双机17项/静默双机25项/DRP双机23项/普通双机24项/集群8项），通过"所属子表"字段关联对应组网模式检查表；AND 各类检查表主数据按5类分别维护；WHEN 配置存在重复编码、无效引用、互斥绑定、评分区间重叠/缺口，或动态维度没有取值来源；THEN 平台阻止发布并保持配置草稿状态，逐项展示校验失败位置，不生成可被CUT-01～04消费的新版本
- Phase 3授权拒绝断言：越权按“系统管理员配置权限；已发布版本和历史业务实例不可覆盖”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“草稿→已发布→已停用；发布前校验稳定编码、版本、动态维度、引用启用状态和条件可判定性，已生成实例继续按消费版本解释”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“CutoverPlan”及数据表“cut_plan_revision、cut_step”；事件边界为“CutoverConfigurationPublished”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“基础平台字典、可选外部动态数据源”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### CUT-08

- 需求名称：割接备件系统集成
- 数据对象：CutoverTask
- 数据表：cut_task、ast_asset_sync_item
- API：/cutover-tasks
- 事件：N/A（同步命令或查询，无跨 Context 业务事件）
- 外部集成：备件系统
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：备件申请映射、回调、门禁和对账
- 授权与数据范围：CutoverTaskScope；外部备件范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3 PRD验收基线：WHEN V2中有权一线工程师对需要备件的割接任务发起外部申请；THEN 平台携带已授权的割接、项目和设备上下文进入备件系统，返回后保存外部业务号及来源；WHEN 外部备件系统返回状态更新；THEN 平台按外部申请号幂等更新只读状态快照，记录来源原值和同步时间，不产生本地库存或到货业务记录；WHEN 外部系统不可用、未返回申请号或字段映射失败；THEN CUT保持原流程数据，记录失败并允许后续重试或上传人工证据，不伪造外部申请成功
- Phase 3授权拒绝断言：越权按“CutoverTaskScope；外部备件范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“备件申请映射、回调、门禁和对账”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“CutoverTask”及数据表“cut_task、ast_asset_sync_item”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“备件系统”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 管理员在后台维护25+项风险考察项的内容定义；THEN 系统支持配置风险项ID、风险项名称（项命名-风险项）、风险含义（项含义）、界面格式（检查结果支持多种数据类型）、界面格式动态数据查询、反馈结果内容、外部数据源接入配置、操作（新增/编辑/保存/删除）；AND 此表格支持后台维护编辑，各字段的界面格式与内容用于采集信息分析与结果页面的填写说明；WHEN 管理员在后台配置风险考察项的动态多维绑定关系；THEN 系统支持为每项风险项配置"割接类型×组网模式×设备类型×割接等级"（已知4维，可扩展）的绑定关系，配置界面以矩阵形式展示；AND 矩阵新版本发布后由新生成的CUT-03清单使用，已生成清单继续按原矩阵版本展示；WHEN 管理员在后台配置双机部署规范性检查表（5类组网模式124行）；THEN 系统分别保存VSM双机17项、静默双机25项、DRP双机23项、普通双机24项和集群8项检查表版本，并展示各表当前项目数量；AND 各类检查表主数据按5类分别维护；WHEN 管理员在后台配置风险考察项的外部数据源接入能力（如技术公告检查项）；THEN 系统支持配置数据来源接口（传入上下文信息调接口获取数据），支持动态数据查询；AND 接口加载的数据项显示编号/主题/描述/解决方案，补充本次是否涉及+采取措施；WHEN 同一维度组合出现重复或互斥绑定、必选风险项存在覆盖缺口，或绑定引用已停用的割接类型/组网模式/设备类型；THEN 平台阻止矩阵发布并保持草稿状态，展示冲突行、缺口组合和无效引用，不影响当前已发布矩阵版本
- Phase 3授权拒绝断言：越权按“CutoverTaskScope；项目/设备范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“任务创建、分级评估、风险/调研矩阵”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“CutoverTask、CutoverAssessment”及数据表“cut_task、cut_assessment”；事件边界为“CutoverApproved”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 管理员在后台维护业务调研项的内容定义；THEN 系统支持配置调研项ID、调研项名称（项命名-调研项）、调研含义（项含义）、界面格式（支持多种数据类型）、界面格式动态数据查询、反馈结果内容、操作（新增/编辑/保存/删除）；AND 此表格支持后台维护编辑，各字段的界面格式与内容用于采集信息分析与结果页面的填写说明；WHEN 管理员在后台配置业务调研项的动态多维绑定关系；THEN 系统支持为每项调研项配置"割接类型×组网模式×设备类型×割接等级"（已知4维，可扩展）的绑定关系，配置界面以矩阵形式展示；AND 绑定关系配置变更实时生效，CUT-03采集清单动态生成时按最新绑定关系联动展示；WHEN 管理员在后台配置调研项的必填项标识；THEN 平台保存每项调研项在特定绑定关系下的必填/选填标识，并在生成清单时展示和校验该标识；WHEN 管理员在后台配置割接背景子表；THEN 系统支持配置割接背景子表字段（是否解决网上问题/问题工单号/工单处理人/是否二次割接/首次割接保障人/割接背景表述）及条件显示逻辑；AND "是否解决网上问题"选"是"时展示问题工单号/工单处理人，"是否二次割接"选"是"时展示首次割接保障人；WHEN 相同调研项和有效期存在重复绑定、同优先级规则冲突或外部动态数据查询失败；THEN 平台拒绝发布冲突矩阵或把清单标记为“生成失败/待处理”，不生成缺少必填项的正式割接清单；WHEN 管理员发布新矩阵版本而已有割接清单正在执行；THEN 既有清单继续关联原配置快照；重新生成时平台展示新增、删除、必填变化差异并由有权人员确认
- Phase 3授权拒绝断言：越权按“CutoverTaskScope；项目/设备范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“任务创建、分级评估、风险/调研矩阵”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“CutoverTask、CutoverAssessment”及数据表“cut_task、cut_assessment”；事件边界为“CutoverApproved”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 平台发布有效巡检事件或工程师提交完整自建任务；THEN 巡检服务生成唯一“待准备”任务，保存来源事件、客户、项目和设备清单快照；WHEN 任务完成规则/方式选择并满足对应前置条件；THEN 平台按在线或离线路径更新到待预检/巡检中，并记录状态流转和执行版本；WHEN 来源事件重复、设备与项目不匹配、必填数据缺失或用户无项目权限；THEN 平台返回既有任务或拒绝创建/流转，保持当前业务对象状态不变
- Phase 3授权拒绝断言：越权按“AssignedProjectDeviceScope、BusinessObjectDeviceCredentialScope”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“方式选择、预检/执行、业务消费和归档门禁”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“InspectionTask、CollectionTask”及数据表“srv_inspection_task、srv_inspection_task_rule_snapshot、plt_collection_task”；事件边界为“InspectionDispatched、InspectionCompleted、CollectionResultConsumed”，文件边界为“FileArtifact”，外部集成为“现有采集平台子应用”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 一线工程师在巡检任务中选择"在线巡检"方式；THEN 系统进入INT-12统一入口并带入巡检任务、项目和设备上下文，展示本人有权使用的设备凭证、临时账号密码输入方式与INS-03已发布命令模板；AND INS-04预检通过后创建在线巡检采集任务，现有采集平台连接设备执行命令并回调巡检结果与日志文件，SRV接收并流转至INS-05报告生成；AND 临时输入模式只保存登录用户名，不保存密码；巡检任务、接口调用记录、回调、日志和导出内容均不得包含密码/密钥明文；WHEN 在线巡检命令执行时间超过30秒（可配置）；THEN 系统自动终止该命令执行并标记为"执行失败"，巡检结果中记录超时失败项；AND 一线工程师可在巡检结果中查看超时失败项详情并决定是否重试；WHEN 一线工程师在巡检任务中选择"离线巡检"方式；THEN 平台根据巡检任务关联的巡检规则自动生成巡检脚本（包含所有勾选检测项的执行命令），一线工程师可下载脚本至本地或现场终端；AND 脚本中包含设备序列号、检测项ID、执行命令、预期结果正则、超时时间等字段（关联INS-09配置字段）；WHEN 一线工程师现场执行巡检脚本后上传结果文件；THEN 平台解析并保存标准化结果文件（XML/JSON/CSV）的设备信息、检测项结果、严重级别和文件哈希；AND 解析后的结果流转至INS-05报告生成环节，解析失败时提示一线工程师结果文件格式错误并支持重新上传；WHEN 平台调用UMC解析结果或生成报告；THEN 平台只发送不含凭证明文的结果引用和上下文，并保存UMC返回结果或失败记录；WHEN 预检失败、凭证无权、下发/回调超时、离线文件不匹配或工程师切换方式；THEN 平台保留本次执行为失败记录且任务不进入待报告；修正后可重试或创建新的离线执行尝试
- Phase 3授权拒绝断言：越权按“AssignedProjectDeviceScope、BusinessObjectDeviceCredentialScope”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“方式选择、预检/执行、业务消费和归档门禁”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“InspectionTask、CollectionTask”及数据表“srv_inspection_task、srv_inspection_task_rule_snapshot、plt_collection_task”；事件边界为“InspectionDispatched、InspectionCompleted、CollectionResultConsumed”，文件边界为“FileArtifact”，外部集成为“现有采集平台子应用”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 一线工程师在巡检任务中浏览巡检规则库；THEN 系统按10大检测分类（①基础检测②运行状态检测③日志检测④业务状态检测⑤冗余性检测⑥路由检测⑦安全检测⑧转发通道检测⑨负载均衡设备专用⑩流量清洗设备专用）展示规则库；AND 每条规则展示检测ID、检测项目、严重级别（一般/严重/致命）、适用产品类型等字段，支持按分类/严重级别/产品类型筛选；WHEN 一线工程师勾选若干检测项并提交；THEN 系统后台自动关联每条勾选规则对应的巡检执行命令，生成巡检命令清单；AND 巡检命令清单作为INS-02双巡检方式的输入（在线巡检作为INT-12采集任务的已发布命令模板，离线巡检打包至巡检脚本）；WHEN 管理员在后台维护巡检规则库；THEN 系统支持新增/编辑/启用/停用/排序巡检规则，每条规则可配置检测ID/检测项目/巡检执行命令/严重级别/适用产品类型等字段（详见INS-09）；AND 发布形成新规则版本，新任务展示当前有效版本，历史任务继续关联原版本；WHEN 规则命令未通过安全审核、适用范围冲突、规则已停用或用户无发布权限；THEN 平台拒绝发布或选择，旧版本保持有效且任务不生成未授权命令清单
- Phase 3授权拒绝断言：越权按“AssignedProjectDeviceScope；规则维护/使用分离”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“规则配置、发布版本和任务冻结”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“InspectionRule”及数据表“srv_inspection_rule、srv_inspection_rule_revision”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 一线工程师在巡检任务中选择在线巡检方式和目标设备，并选择本人有权使用的设备凭证或临时输入登录用户名、密码；THEN 系统按认证方式完成权限或输入校验后通过INT-12下发预检任务，由现有采集平台校验TELNET/SSH端口连通性与账号有效性；AND 预检结果实时展示（通过/失败），失败时提示具体原因（端口不可达/账号无效/密码错误/权限不足等）；WHEN 连通性预检通过；THEN 平台把预检状态更新为“通过”并流转至INS-02在线巡检执行环节，通过INT-12下发巡检采集任务；AND 预检通过状态记录至巡检任务，预检时间与预检人完整记录；WHEN 连通性预检失败；THEN 巡检任务保持“待预检”并保存失败分类，一线工程师修正连接端点或认证后可重新预检；AND 预检失败记录（含失败原因、失败时间、设备连接信息）保存至任务历史，便于追溯；WHEN 预检结果已过期、端点/认证已变化、回调验签失败或用户无凭证权限；THEN 平台拒绝进入在线巡检并要求重新预检，不复用旧通过结果且不展示密码/密钥明文
- Phase 3授权拒绝断言：越权按“AssignedProjectDeviceScope、BusinessObjectDeviceCredentialScope”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“方式选择、预检/执行、业务消费和归档门禁”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“InspectionTask、CollectionTask”及数据表“srv_inspection_task、srv_inspection_task_rule_snapshot、plt_collection_task”；事件边界为“InspectionDispatched、InspectionCompleted、CollectionResultConsumed”，文件边界为“FileArtifact”，外部集成为“现有采集平台子应用”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN INS-02巡检执行完成并汇总所有检测项结果；THEN SRV创建唯一巡检报告业务对象，记录报告状态、生成方式和输入结果引用，并在正常路径调用UMC解析结果、生成结构化报告文件；AND 报告包含设备信息、巡检概览、检测项明细、问题清单、严重级别统计等章节；UMC返回结果作为报告输入和证据，不直接控制报告业务状态；WHEN UMC或平台基础模板完成报告生成；THEN 平台保存PDF/DOC/XML文件版本和哈希，一线工程师可下载三种格式的报告；AND PDF格式适用于归档与正式分享，DOC格式适用于二次编辑，XML格式适用于系统间数据交换；WHEN UMC接口解析失败或报告生成失败；THEN SRV记录失败原因和调用证据并支持受控重试，一线工程师可在任务详情中查看失败原因；AND 具备报告生成权限的工程师可选择平台基础模板生成或人工上传报告；人工上传需记录文件、上传人、上传时间和说明，且不得覆盖UMC失败证据或原始巡检结果；AND 人工上传至少包含一份PDF、DOC或XML格式文件，系统仅展示实际上传格式且不要求自动转换为其他格式；AND 任一生成方式完成必填校验并形成有效报告后，SRV将报告状态更新为“已生成”，巡检任务流转至INS-06问题标注环节；未形成有效报告时停留在“待报告”；WHEN 输入结果不完整、文件安全校验失败、用户无报告权限或生成服务超时；THEN 报告保持“生成失败/不可用”，巡检任务停留“待报告”，不得进入问题标注并支持受控重试
- Phase 3授权拒绝断言：越权按“AssignedProjectDeviceScope、FileBusinessScope”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“报告生成、回调校验、发布版本”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“InspectionReport”及数据表“srv_inspection_report_revision”；事件边界为“InspectionCompleted”，文件边界为“FileArtifact”，外部集成为“UMC”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 巡检报告生成完成并展示问题清单；THEN 平台为每个检出项提供误报/不关注/确认跟踪标注并保存标注状态；AND 标注时支持填写标注说明（如误报原因、不关注理由、确认跟踪的处理建议）；WHEN 一线工程师将检出问题标注为"确认跟踪"并提交；THEN 系统自动生成待办项（含问题描述、严重级别、责任人、计划解决时间），流转至INS-07闭环归档环节；AND 待办项关联巡检任务编号与检出问题ID，支持从待办项跳转至巡检报告详情；WHEN 一线工程师将检出问题标注为"误报"；THEN 误报项汇总至INS-08误报反馈机制，支持导出分析；AND 误报项在报告中保留记录但标记为"误报"，不进入待办；WHEN 一线工程师将检出问题标注为"不关注"；THEN 不关注项在报告中保留记录但标记为"不关注"，不进入待办；AND 不关注项不进入INS-08误报反馈机制；WHEN 任一检出项未标注、理由缺失、责任人无权或待办/误报联动失败；THEN 巡检任务保持“待标注/联动失败”，平台阻止完成标注并允许修正后幂等重试
- Phase 3授权拒绝断言：越权按“AssignedProjectDeviceScope；问题责任范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“问题标注、误报、整改复核和关闭”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ServiceIssue”及数据表“srv_service_issue、srv_service_issue_remediation”；事件边界为“InspectionIssueRaised/Closed”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN INS-06确认跟踪的问题生成待办项并分配至责任人；THEN 责任人收到待办通知，可记录处理过程、处理结果、处理时间等跟进记录；AND 待办项支持多次跟进记录，每次跟进记录含跟进人、跟进时间、跟进内容；WHEN 责任人提交闭环申请；THEN 一线工程师或服务经理确认闭环，闭环确认后待办状态变更为"已闭环"；AND 闭环确认记录（闭环人、闭环时间、闭环说明）保存至待办详情；WHEN 巡检任务中所有确认跟踪问题均已闭环；THEN 巡检任务可归档，归档时通过内部客户资产服务将巡检结果关联至CUS-01；AND 归档内容包含巡检任务基础信息、巡检报告、问题清单与标注记录、待办处理记录、闭环确认记录；WHEN 巡检任务归档完成；THEN 任务状态变更为"已归档"，归档时间与归档人完整记录；AND 平台客户资产库中可查看该客户的历次巡检记录；WHEN 存在未闭环跟踪项、报告不可用、标注未完成、资产引用失败或用户无归档权限；THEN 平台拒绝归档并保持“待办跟踪中/归档失败”，列出阻断项且不在CUS-01展示为已归档
- Phase 3授权拒绝断言：越权按“AssignedProjectDeviceScope、BusinessObjectDeviceCredentialScope”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“方式选择、预检/执行、业务消费和归档门禁”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“InspectionTask、CollectionTask”及数据表“srv_inspection_task、srv_inspection_task_rule_snapshot、plt_collection_task”；事件边界为“InspectionDispatched、InspectionCompleted、CollectionResultConsumed”，文件边界为“FileArtifact”，外部集成为“现有采集平台子应用”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 一线工程师在INS-06中将检出问题标注为"误报"；THEN 误报项自动汇总至误报反馈库，含检测项ID、检测项目、执行命令、执行结果、误报原因、标注人、标注时间、所属巡检任务等字段；AND 误报反馈库支持按检测分类、严重级别、设备型号、时间范围等维度筛选与统计；WHEN 管理员在后台查看误报反馈库并导出误报清单；THEN 平台生成Excel/CSV导出记录和文件，保存筛选条件、样本数、数据截止时间及下载权限；AND 导出的误报清单支持离线分析，便于管理员识别高频误报规则；WHEN 管理员基于误报分析结果优化巡检规则（调整正则/阈值/命令/停用）；THEN 系统支持在后台直接修改INS-09配置字段（预期结果正则/结果阈值/命令列表等），修改后规则版本更新；AND 规则优化后系统跟踪该规则的误报率变化，展示优化前后误报率对比；WHEN 管理员停用某条巡检规则；THEN 该规则在INS-03规则库中标记为"停用"，一线工程师前端不再展示该规则；AND 历史巡检任务中已执行该规则的结果保留不变；WHEN 误报统计不完整、规则修订发布失败、用户无规则权限或导出超时；THEN 平台保持当前发布规则不变，标记统计/导出失败并允许修正后重试，不改写历史任务结果
- Phase 3授权拒绝断言：越权按“AssignedProjectDeviceScope；问题责任范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“问题标注、误报、整改复核和关闭”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ServiceIssue”及数据表“srv_service_issue、srv_service_issue_remediation”；事件边界为“InspectionIssueRaised/Closed”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 管理员在后台巡检规则配置页面新增或编辑巡检规则；THEN 系统提供8个配置字段（规则名称/描述/命令列表/执行顺序/超时时间/预期结果正则/结果阈值/适用产品类型）的录入界面；AND 每个字段含输入校验（如规则名称必填且唯一、超时时间为正整数≤30、预期结果正则为合法正则表达式、适用产品类型关联产品类型库）；WHEN 管理员配置命令列表与执行顺序；THEN 系统支持单条或多条命令配置，多条命令按执行顺序依次执行；AND 命令格式校验通过并发布后保存版本，INS-02在线巡检时按顺序通过INT-12下发、离线巡检时按顺序打包至巡检脚本；WHEN 管理员配置预期结果正则与结果阈值；THEN 系统校验正则表达式合法性，校验阈值格式（比较运算符+数值）；AND INS-02巡检执行后按正则与阈值判定检测结果（通过/异常），异常项按严重级别（INS-03）标注；WHEN 管理员配置适用产品类型；THEN 一线工程师前端勾选规则时，平台按设备清单的产品类型筛选并展示适用规则；AND 不适用规则在前端置灰或隐藏，避免误选；WHEN 管理员保存规则配置；THEN 平台保存草稿；通过校验与安全审核并发布后生成新规则版本，历史版本保留可回溯；AND INS-03规则库展示最新配置，INS-08误报反馈机制可基于误报分析优化这8个字段；WHEN 正则存在语法/超时风险、阈值单位冲突、命令顺序重复、适用产品缺失或用户无发布权限；THEN 平台拒绝发布并保持旧版本有效，返回字段级错误供管理员修正后重新校验
- Phase 3授权拒绝断言：越权按“AssignedProjectDeviceScope；规则维护/使用分离”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“规则配置、发布版本和任务冻结”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“InspectionRule”及数据表“srv_inspection_rule、srv_inspection_rule_revision”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN CRM中新增或变更项目、客户或销售执行信息；THEN 平台通过CRM接口在准实时窗口内同步至项目档案；AND CRM权威字段按来源更新，平台扩展字段不被覆盖；WHEN ERP系统中产生与项目关联的销售订单；THEN 平台同步合同与销售订单数据，按项目编码、合同号或销售订单号建立CRM与ERP映射；AND 来源冲突进入待裁决状态，未经审批不得覆盖权威字段；WHEN CRM/ERP接口不可用；THEN 平台允许手工创建项目并标记来源待补录；AND 接口恢复后执行关联补录，仅补充空缺字段或经审批裁决的冲突字段，并保留补录记录；WHEN 同一来源事件重复到达、来源业务键已映射，或一个来源对象匹配多个平台项目发生冲突；THEN 平台对重复事件返回既有映射且不创建重复对象；对多匹配记录标记“待映射”并保留候选列表，不自动覆盖项目权威字段
- Phase 3授权拒绝断言：越权按“集成服务账号限Owner字段；工程管理部映射权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“双源同步、待映射、字段Owner裁决和对账”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Project、Contract、SalesOrder”及数据表“ast_asset_sync_batch、ast_asset_sync_item、com_contract、com_sales_order”；事件边界为“MasterDataSynchronized”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“CRM、ERP”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN ITR系统中设备版本发生变更；THEN 平台通过ITR接口实时接收版本信息，按设备序列号写入设备档案并保留来源；AND 历史版本记录完整保留（含版本号、变更时间、变更原因、变更人），不覆盖历史数据；WHEN ITR系统中产生技术公告；THEN 平台知识域接收并展示ITR公告，平台产生的受控反馈按接口契约回传ITR；WHEN ITR系统中产生设备故障记录；THEN 平台同步故障记录并关联设备序列号与项目，故障记录在EQP-07项目问题单页面可见；WHEN ITR系统中产生与割接相关的故障/变更工单且满足割接触发条件；THEN 平台自动生成割接任务（关联CUT-01），预填客户信息、设备序列号、故障/变更背景等字段；WHEN ITR接口不可用；THEN 版本信息/技术公告降级为手工录入，故障记录与割接触发降级为人工创建任务；AND 接口恢复后自动批量补录同步；WHEN ITR版本事件重复到达、事件迟到，或设备序列号缺失/重复导致映射冲突；THEN 平台对重复事件返回既有处理结果；迟到事件追加历史并按发生时间重算当前版本；映射冲突事件保持“待设备映射”且不写入其他设备档案
- Phase 3授权拒绝断言：越权按“集成账号限ITR字段；ProjectDeviceScope查询”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“版本同步、来源冲突隔离和对账”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“AssetSyncSnapshot”及数据表“ast_asset_sync_batch、ast_asset_sync_item、ast_device”；事件边界为“MasterDataSynchronized”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“ITR”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录

### INT-05

- 需求名称：钉钉/HR/OA集成
- 数据对象：Todo
- 数据表：plt_todo、plt_sync_batch、plt_external_key_mapping
- API：/todos、/integration/hr/directory
- 事件：MasterDataSynchronized、TodoRequested、TodoCompleted
- 外部集成：钉钉、HR、OA
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：必要人员组织同步复用基础平台主数据、已有同步批次和来源键映射；待办链接和通知回执不接入打卡/工时
- 授权与数据范围：TenantOrganizationProjectScope；目录身份不直接等于项目角色
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3 PRD验收基线：WHEN HR人员、组织、岗位或在离职状态发生变化；THEN 平台按来源业务键幂等同步必要主数据并更新身份映射；既有审批和操作历史保持可追溯；WHEN V2工程师/服务经理在平台发起领料或外采事前审批；THEN 平台调用OA接口创建对应流程并保存OA流程实例ID，平台页面展示OA办理链接；AND OA流程状态实时同步，流程完成后结果回填对应物料申请；WHEN 平台审批节点触发（割接审批/变更审批/转包审批等）；THEN 平台通过钉钉推送审批通知，通知含审批标题、申请人、审批人、审批链接、截止时间；AND 审批人在平台链接内完成审批，钉钉只回传通知送达/阅读状态，不得更新平台审批结果；WHEN 转包流程（SUB-01~05）发起；THEN 平台在平台内独立审批，OA仅接收代办链接通知（不在OA内审批）；WHEN 钉钉/HR/OA接口不可用；THEN 平台继续使用最近一次成功同步的人员组织快照，OA流程降级为线下审批后补录，审批通知降级为站内消息；不得把通知失败判为业务失败或审批成功；WHEN 同一人员事件或通知请求重复到达、HR人员无法唯一匹配，或钉钉回调试图携带审批结果；THEN 平台对重复请求返回既有记录；人员保持待映射且不进入候选人列表；忽略外部审批结果字段并记录协议异常，不改变平台审批状态
- Phase 3授权拒绝断言：越权按“TenantOrganizationProjectScope；目录身份不直接等于项目角色”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“必要人员组织同步复用基础平台主数据、已有同步批次和来源键映射；待办链接和通知回执不接入打卡/工时”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Todo”及数据表“plt_todo、plt_sync_batch、plt_external_key_mapping”；事件边界为“MasterDataSynchronized、TodoRequested、TodoCompleted”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“钉钉、HR、OA”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 用户通过企业账号登录平台；THEN 平台校验LDAP/AD单点登录断言后创建平台会话，记录用户ID、登录时间和认证协议并加载当前角色/数据范围；AND SSO支持Kerberos/NTLM/SAML等标准协议，与企业现有身份认证体系对接；WHEN LDAP/AD中账号信息新增或变更（账号/姓名/工号/邮箱/部门/岗位）；THEN 平台实时同步账号信息至用户档案，保持与LDAP/AD一致；WHEN LDAP/AD中组织架构信息变更（部门层级/部门负责人/部门成员）；THEN 平台实时同步组织架构信息，支撑数据权限与角色权限配置；WHEN LDAP/AD中账号状态变更（如离职禁用）；THEN 平台自动同步用户状态，离职账号自动禁用，无法登录平台；WHEN LDAP/AD接口不可用；THEN 普通用户登录保持失败并展示统一认证不可用状态，平台仅允许按受控流程启用预置应急管理员账号，组织架构以最近一次成功同步版本只读展示；AND 接口恢复后自动批量补录同步账号与组织架构变更；WHEN 目录不可变标识缺失/重复、账号映射冲突，或已离职账号携带未失效平台会话；THEN 平台将同步记录标记“待映射”且不创建重复用户；对禁用/离职账号拒绝新请求并撤销现有会话和未使用任务授权
- Phase 3授权拒绝断言：越权按“平台RBAC/DataScope；目录组不直授项目角色”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“认证断言校验、目录映射和平台会话”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“AuthorizationGrant”及数据表“plt_authorization_grant、ast_asset_sync_item”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“LDAP/AD”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 用户从EXE-03/04、CUT-06或独立中心发起V1采集任务；THEN 系统自动或人工确定项目、设备、用途和业务上下文，展示用户有权使用的凭证、临时账号密码输入方式和已发布命令模板；AND 权限校验通过后创建统一采集任务，向现有采集平台下发并保存外部任务号和受理结果；WHEN V2用户从INS-02/04发起在线巡检或预检；THEN 系统复用同一认证方式、任务下发和回调契约，不新增巡检专用凭证副本或采集执行引擎；WHEN 用户选择“临时输入账号密码”并直接发起任务；THEN 采集任务保存认证方式和登录用户名用于审计，密码仅经受保护同步链路传至已认证采集执行进程，不写入任何持久化介质；AND 页面刷新、任务重试或再次执行时，系统不得自动回填原密码，必须要求用户重新输入；WHEN 用户在临时输入模式显式选择“保存为设备凭证”；THEN 系统按凭证规则保存用户名并加密保存密码，创建默认仅当前用户可用的新凭证，本次任务记录该凭证ID及授权快照；AND 未选择“保存为设备凭证”时，系统不得自动创建凭证或以缓存、草稿、历史参数等形式保留密码；WHEN 非创建人未获得显式凭证授权，或用户缺少业务、设备、模板任一权限；THEN 对已保存凭证禁止下发任务；临时输入模式仍须校验业务、设备和模板权限，并记录不含密码/密钥明文的拒绝原因和审计记录；WHEN 现有采集平台返回任务状态、日志文件或结果引用；THEN 系统完成调用方认证、验签、幂等和顺序校验，保留外部状态原值和原始证据，并按业务上下文通知IMP/CUT/SRV消费；AND 重复回调不得生成重复业务结果，乱序回调不得使已完成任务回退到非终态；WHEN 独立中心任务未关联具体IMP/CUT/SRV业务单据且收到有效终态回调；THEN PLT按状态映射将通用任务更新为相应终态，保存设备级结果、结果文件引用和原始证据，并在独立中心按项目、设备、创建人、时间和状态可查询；WHEN 用户通过页面、查询接口、导出、审计日志或异常信息访问设备凭证；THEN 系统仅返回凭证ID、账号脱敏值和授权状态，阻止查看、复制或导出密码/密钥明文并记录访问结果；WHEN 现有采集接口必须接收已保存凭证解密值或临时输入密码字段；THEN 明文仅通过受保护同步链路进入已认证采集执行进程，相关请求体不记录正文日志，执行结束后清除内存中的密码或密钥；WHEN 凭证授权在任务执行前或执行中撤销；THEN 系统按本需求第7项规则终止或收敛执行，记录实际停止点，撤销后不得发起新连接、重试或继续剩余命令；WHEN 子应用不可用、下发超时、设备级部分失败或回调异常；THEN 系统保留原任务和原始证据，展示设备级结果并允许基于原任务创建新的受控重试任务；AND 实施场景可按EXE-03/04手动上传Log，割接场景可按CUT-06关联原失败任务人工上传结果，巡检场景可按INS-02切换离线方式；任何降级结果均不得覆盖原任务失败证据
- Phase 3授权拒绝断言：越权按“BusinessObjectDeviceCredentialScope；创建人默认权限和五元组授权”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“授权校验→下发→回调→业务消费→完成/失败；独立中心按成功回调终态”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“DeviceCredential、CredentialGrant、CollectionTask、CollectionResultReference”及数据表“plt_device_credential、plt_credential_grant、plt_collection_task、plt_collection_result_consumption”；事件边界为“CollectionTaskRequested、CollectionResultAvailable、CollectionResultConsumed、CollectionCompleted”，文件边界为“FileArtifact”，外部集成为“现有采集平台子应用”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 平台收到包含CRM客户ID、数据版本和核心字段的新增或更新事件；THEN 平台按CRM客户ID新增或更新只读权威字段，保存来源版本、同步时间和映射状态，同时保留平台交付扩展字段不变；WHEN 客户管理员维护交付联系人、服务偏好或交付标签；THEN 平台只更新扩展字段及其版本，不生成CRM客户主数据更新请求，也不修改CRM同步字段；WHEN 收到重复/旧版本事件、未知字典值、CRM客户合并停用，或T+1批次部分记录失败；THEN 平台分别执行幂等忽略、进入待映射、保留旧ID历史映射，或把批次标记为“部分成功”；不得覆盖新值、删除历史项目或宣称全量成功；WHEN CRM接口不可用且业务人员查看客户资料；THEN 平台展示最近一次成功值、数据截止时间和“同步异常”标识，允许维护平台扩展字段但禁止编辑CRM权威字段；涉及数据：；CRM客户ID、原CRM客户ID、客户编码、客户名称、客户等级、客户状态、归属销售、CRM产品编码/名称、源数据版本、源更新时间、来源事件ID、平台交付联系人、服务偏好、交付标签、项目服务备注、字典映射状态、同步状态、同步批次号、数据截止时间、失败原因、创建/更新时间
- Phase 3授权拒绝断言：越权按“OrganizationCustomerScope；CRM字段只读”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“客户同步、临时客户合并与字典映射”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Customer、CustomerRelationshipSnapshot”及数据表“cus_customer、ast_asset_sync_batch、ast_asset_sync_item”；事件边界为“MasterDataSynchronized、CustomerMerged”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“CRM”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 平台收到合法且版本较新的ITR公告事件；THEN 平台按ITR公告ID新增或更新只读公告，保存源版本、同步时间和产品/版本映射状态；WHEN 事件重复乱序、字典未映射、ITR不可用或用户无公告范围权限；THEN 平台忽略旧事件或标记待映射/同步异常，沿用最近成功值且不返回无权正文；涉及数据：；ITR公告ID、公告编号、标题、正文、公告级别、适用产品、适用版本、发布时间、生效时间、失效状态、源版本、来源事件ID、映射状态、同步状态、数据截止时间、失败原因
- Phase 3授权拒绝断言：越权按“ProductDeviceProjectScope；V2只读”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“公告镜像、版本同步和业务引用”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“TechnicalNoticeReference”及数据表“FEATURE_FORWARD_MIGRATION(INT-04)：逻辑对象`TechnicalNoticeReference`；物理表由INT-04 Feature前向迁移确定”；事件边界为“TechnicalNoticeSynchronized”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“ITR”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 备件、授权或报告业务分别提交完整请求并获外部系统受理；THEN 平台按各自业务键保存外部单号、处理状态和回调版本，不把三类结果混为同一状态；WHEN 任一接口超时、回调验签失败、业务键冲突或用户无对应项目/设备权限；THEN 对应业务保持失败/待核对并采用其专属人工补偿，其他业务不受影响且不重复设备采集；涉及数据：；集成类型、项目ID、设备ID、备件申请/RMA号、授权申请ID、授权ID掩码、巡检报告任务ID、UMC任务号、请求ID、来源事件ID、外部状态、平台状态、回调版本、失败原因、补偿方式、重试次数
- Phase 3授权拒绝断言：越权按“业务对象范围；完整授权码不可见”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“外部申请/结果映射、回调和对账”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“RMAReplacement、AuthorizationGrant、InspectionReport”及数据表“ast_rma_replacement、plt_authorization_grant、srv_inspection_report_revision”；事件边界为“MasterDataSynchronized”，文件边界为“FileArtifact”，外部集成为“备件系统、授权系统、UMC”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 已批准且付款门禁满足的费用版本提交财务系统并返回业务单号；THEN 平台保存财务业务单号、受理状态和同步时间，将该费用版本标记为同步成功；WHEN 金额/供应商/合同不一致、接口超时、重复请求或用户无财务权限；THEN 平台保持待核对/同步失败，不重复入账；可查询后重试或导出人工对账并等待核验回填；涉及数据：；费用单ID、批准版本、项目编码、合同号、供应商ID、付款阶段、费用明细、金额、币种、发生时间、请求ID、财务业务单号、受理状态、同步时间、对账文件哈希、失败原因
- Phase 3授权拒绝断言：越权按“OrganizationSupplierScope；财务结果复核”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“批准费用出向、结果查询和人工对账”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“PaymentGate”及数据表“res_payment_gate、plt_integration_reconciliation”；事件边界为“PaymentGateChanged”，文件边界为“FileArtifact”，外部集成为“财务系统”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 已配置节点产生有效业务事件和接收人；THEN 平台按事件、接收人和渠道生成唯一通知，保存模板版本、受理及送达状态；WHEN 渠道不可用、回执超时、变量含敏感明文、接收人无权或事件重复；THEN 平台拒绝敏感内容/幂等去重，失败通知保留站内消息并按策略重试或钉钉兜底；涉及数据：；通知ID、业务事件ID、业务对象ID、节点编码、状态版本、模板ID/版本、接收人ID、脱敏联系方式、渠道、发送状态、渠道消息ID、受理时间、送达时间、重试次数、失败原因
- Phase 3授权拒绝断言：越权按“业务对象接收人范围；模板变量白名单”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“节点通知、受理/送达分离和兜底”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Todo”及数据表“plt_todo、ast_asset_sync_item”；事件边界为“NotificationRequested、NotificationDelivered/Failed”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“短信/邮件、钉钉”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 50个并发登录用户在规定数据集上持续30分钟执行不少于10000次核心页面加载与业务交互；THEN 平台记录错误率不高于0.5%，页面加载和主要交互响应时间≤2秒（P95）；AND Chrome/Edge/Firefox验收版本的功能、权限和业务结果一致，关键字段、操作及错误提示无遮挡；WHEN 用户上传割接方案/承诺书/采集清单等文件；THEN 50MB文件成功上传并可下载，上传前后文件哈希一致；超过50MB文件被明确拒绝且不生成有效附件记录；AND 上传中断不产生业务可见的残缺文件，重试后仅形成一个有效文件版本；WHEN 割接高峰期50人以上并发操作；THEN 平台在50并发基线内保持错误率≤0.5%且页面响应≤2秒（P95），资源监控无内存溢出或持续不可恢复增长；AND 超过基线的压力测试记录容量拐点和降级表现，不以超基线结果替代50并发发布门禁；WHEN 按实际迁移量两倍与最低20万项目、200万任务取较大值准备数据，并覆盖单项目树1万、单任务树5万、直接子节点2000和测试深度30；THEN 平台执行并记录直接下级、全部后代、完整上级链、指定业务层级和节点定位查询，权限过滤后的响应时间均满足≤2秒（P95）；AND 测试深度30不构成业务限制，超过该深度的合法结构仍能按需加载且关系、路径、汇总与权限结果正确；WHEN 用户通过企业账号登录；THEN 平台通过LDAP/AD单点登录验证后创建平台会话并加载该用户角色与数据范围，不要求用户再次输入平台密码；AND RBAC权限控制按角色分配模块权限，数据隔离按项目权限规则执行；WHEN 用户在平台执行任何操作；THEN 操作审计日志完整记录（操作人/操作时间/操作类型/操作对象/操作前后值）；AND 审计日志支持按时间/人员/类型查询与追溯；WHEN 在规定四类视口和三款浏览器执行项目树、交付、割接、文件与审批核心流程；THEN 页面保存相同业务结果，导航/表单/操作区完成响应式重排，除数据表外无页面级横向溢出且关键操作可达；WHEN 未授权用户访问跨租户、平级项目、后代项目、设备或文件资源；THEN 平台拒绝全部越权请求并记录拒绝审计，响应中不返回敏感字段、文件内容或凭证信息
- Phase 3授权拒绝断言：越权按“TenantOrganizationProjectScope；服务端强制范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“性能水位、安全审计和兼容性验证”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“AuditRecord、MetricSnapshot”及数据表“plt_operation_audit、ana_metric_snapshot”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：V1发布门禁（设备凭证安全与采集公共基线）：；WHEN 用户在Chrome/Edge/Firefox浏览器中访问设备连接与采集公共页面；THEN 平台记录50并发场景的页面响应统计，P95≤2秒且错误率满足NFR-01基线；AND 单点登录、RBAC权限、数据隔离、审计日志、浏览器兼容均满足NFR-01同等标准；WHEN 用户创建或更新设备凭证；THEN 系统以AES-256或不低于同等强度的算法保存密文和密钥版本，不保存可查询的明文副本；WHEN 非创建人未获得显式授权，或授权超出设备、协议、命令模板或有效期范围；THEN 系统拒绝使用凭证；管理员、项目成员和任务参与人身份均不得绕过该校验；WHEN 授权用户通过页面、接口、导出或审计查询设备凭证；THEN 系统仅返回凭证标识、账号脱敏信息和授权状态，不返回密码或密钥明文；WHEN 现有采集平台执行已授权设备任务；THEN 明文仅在受保护同步链路和已认证采集执行进程内存中短暂使用，请求体不记录正文日志，执行完成、失败、超时或会话关闭后清除内存凭证；AND 数据库、异步消息、持久化队列、日志、回调、异常堆栈和导出文件中扫描不到凭证明文；WHEN 用户选择临时账号密码方式发起设备连接；THEN 系统保存认证方式、登录用户名和任务审计信息，并在持久化及浏览器存储检查中保持密码记录数为0；AND 页面刷新、任务重试或再次执行时必须重新输入密码；原密码不得从缓存、草稿或历史参数恢复；WHEN 用户显式选择将临时输入保存为设备凭证；THEN 系统按凭证安全规则加密保存密码、记录密钥版本和创建审计，新凭证默认仅创建人可使用；未显式选择时不得创建凭证；WHEN 使用唯一标记测试密码/密钥完成成功、失败、超时、重试和撤销场景后执行全链路扫描；THEN 浏览器存储、数据库、缓存、消息/持久队列、网关/应用/采集日志、回调、异常堆栈、导出和任务结果中的明文命中数为0，并保存扫描范围与结果报告；WHEN 任务结束、失败、超时、撤销或执行授权到期后再次请求取用同一凭证；THEN 系统拒绝取密并记录授权失效原因，不创建新连接或继续剩余命令；V2巡检启用门禁（不作为V1发布条件）：；WHEN 一线工程师执行在线巡检命令；THEN 巡检命令执行超时≤30秒（超时时间可由管理员后台配置）；AND 超时后自动终止该命令并标记执行失败，不影响后续命令执行
- Phase 3授权拒绝断言：越权按“BusinessObjectDeviceCredentialScope；创建人默认权限和五元组授权”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“授权校验→下发→回调→业务消费→完成/失败；独立中心按成功回调终态”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“DeviceCredential、CredentialGrant、CollectionTask、CollectionResultReference”及数据表“plt_device_credential、plt_credential_grant、plt_collection_task、plt_collection_result_consumption”；事件边界为“CollectionTaskRequested、CollectionResultAvailable、CollectionResultConsumed、CollectionCompleted”，文件边界为“FileArtifact”，外部集成为“现有采集平台子应用”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 测试覆盖割接/巡检各五个节点和全部启用渠道的有效通知样本；THEN 渠道已送达数除以有效发送数达到99%以上，并可按节点下钻到发送与回执记录；WHEN 巡检节点产生项目进度事件；THEN 项目在1分钟内读取对应状态版本，重复事件不重复推进，乱序旧事件不回退进度；WHEN 渠道或内部事件消费失败、接收人无权或消息重复；THEN 平台保留站内兜底/进度待同步状态并支持补偿重试，不改变已成功业务节点；涉及数据：；业务事件ID、专项任务ID、项目ID、节点编码、状态版本、事件时间、通知ID、渠道、有效发送数、已送达数、到达率、消费时间、进度更新时间、延迟秒数、重试次数、失败原因
- Phase 3授权拒绝断言：越权按“业务对象接收人范围；模板变量白名单”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“节点通知、受理/送达分离和兜底”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Todo”及数据表“plt_todo、ast_asset_sync_item”；事件边界为“NotificationRequested、NotificationDelivered/Failed”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“短信/邮件、钉钉”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 售前人员对授权项目设备首次申请同类型临时授权且资格校验通过；THEN 平台生成免审批申请并向授权系统下发，返回授权ID后标记生效且记录首次资格已使用；WHEN 同一资格再次申请；THEN 平台冻结申请并生成OA审批实例，全部通过且授权系统返回结果后才标记生效；WHEN 设备/项目不匹配、申请人无权、OA/授权系统失败或请求重复；THEN 平台拒绝或保持待审批/待下发，幂等返回既有申请且不展示任何密码、密钥或完整License；涉及数据：；申请ID、申请人ID、项目ID、设备ID/序列号、业务场景、授权类型、权限范围、有效期、首次资格键、是否免审批、OA审批单号/状态、授权ID掩码、授权状态、来源版本、失败原因、申请/生效时间
- Phase 3授权拒绝断言：越权按“申请对象/设备/产品范围；授权码脱敏”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“申请、OA审批引用、外部授权确认和查询”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“AuthorizationGrant”及数据表“plt_authorization_grant”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“OA、授权系统”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 工程师按授权设备或合同查询正式授权；THEN 平台返回授权ID/授权码掩码、类型、状态、期限及有权关联对象，并显示授权系统数据截止时间；WHEN 授权管理员提交设备、合同、客户关系一致的找回证据；THEN 平台建立历史授权与当前对象映射并记录核验人，不创建新授权或修改期限；WHEN 查询无权、授权冲突、证据不足或授权系统不可用；THEN 平台拒绝敏感结果或保持待核对，沿用最近状态但不认定未知授权有效；涉及数据：；授权ID、授权码掩码、授权类型、授权状态、开始/结束时间、设备ID/序列号、合同号、项目ID、客户ID、源版本、数据截止时间、找回申请ID、证据附件、核验人/时间、映射状态、失败原因
- Phase 3授权拒绝断言：越权按“申请对象/设备/产品范围；授权码脱敏”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“申请、OA审批引用、外部授权确认和查询”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“AuthorizationGrant”及数据表“plt_authorization_grant”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“OA、授权系统”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 工程师基于当前批准基线提交完整变更、影响分析和可验证回退方案；THEN 平台冻结申请版本并按四级链路生成审批；全部通过后状态为“已批准/待执行”，原基线仍保持有效；WHEN 执行结果全部通过验证或触发回退并验证成功；THEN 平台分别生成新生效基线并标记已完成，或保留原基线并标记已回退，记录逐项结果；WHEN 批准基线已变化、审批驳回/撤回、影响项缺失、执行失败或回退失败；THEN 平台阻止执行/关闭并保持待补充、已驳回、已撤回或待人工处置状态，不把失败变更标记完成；涉及数据：；变更申请ID/版本、项目ID、变更对象类型/ID、批准基线ID/版本、变更前值、目标值、变更原因、影响项目/设备/业务、风险等级、回退条件/步骤/验证/责任人、审批实例/节点/意见、执行结果、回退结果、状态、生效时间、失败原因
- Phase 3授权拒绝断言：越权按“TenantOrganizationProjectScope；变更对象权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“变更申请、审批、目标版本校验和执行”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ChangeRequest”及数据表“plt_change_request”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 业务模块发布待处理事件；THEN 平台按幂等键生成待办并展示来源对象、当前节点、责任人和操作入口；WHEN 责任人完成或转派待办；THEN 原业务对象状态同步更新并写入操作人、时间、前后状态及意见；WHEN 原业务状态已被其他操作改变；THEN 平台拒绝过期待办动作并提示刷新，保留冲突记录
- Phase 3授权拒绝断言：越权按“TenantOrganizationProjectScope；待办责任人范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“统一待办接入、完成回调Owner再校验”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Todo”及数据表“plt_todo”；事件边界为“TodoRequested、TodoCompleted”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 用户上传同一业务对象的新文件；THEN 平台生成新版本并保留旧版本、校验摘要和版本说明；WHEN 审批单引用文件；THEN 审批记录保存文件版本ID，文件后续更新不影响历史审批；WHEN 无权用户访问文件；THEN 平台拒绝下载并记录拒绝原因
- Phase 3授权拒绝断言：越权按“FileBusinessScope；下载实时回源业务权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“初始化上传、内容校验、版本提交、引用和归档”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“FileArtifact”及数据表“plt_file_artifact、plt_file_version、plt_file_reference”；事件边界为“FileVersionCommitted、FileReferenceAttached/Detached、FileArchived”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 管理员创建组合并关联多个项目；THEN 系统校验权限和循环关系，保存主组合及关系类型；WHEN 查询组合进度；THEN 按配置规则汇总子项目里程碑、任务、风险和成本，并可下钻到原始项目；WHEN 修改组合关系；THEN 新统计从生效时间起采用新关系，历史快照保持不变
- Phase 3授权拒绝断言：越权按“ProjectTreeScope；组合汇总不扩权”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“组合成员、发布快照和下钻”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ProjectPortfolio”及数据表“proj_project_portfolio、proj_project_portfolio_member、proj_project_portfolio_revision”；事件边界为“ProjectPortfolioPublished”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 用户选择组合、区域、客户、签约方式、项目类别、实施方式或重大项目级别筛选条件；THEN 看板按统一指标口径刷新并展示统计时点；WHEN 用户点击异常指标；THEN 系统下钻到授权范围内的项目、任务或风险清单；WHEN 数据刷新失败；THEN 页面提示数据时点并保留最近一次成功快照，不显示为最新数据
- Phase 3授权拒绝断言：越权按“OrganizationReportScope；下钻回项目权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“组合指标快照生成与只读展示”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“PortfolioView、MetricSnapshot”及数据表“ana_portfolio_projection、ana_metric_snapshot”；事件边界为“MetricSnapshotPublished”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据

### COM-01

- 需求名称：合同订单关联与范围分配
- 数据对象：Contract、SalesOrder、OrderLine、DeliveryScope、DeliveryScopeDetail
- 数据表：com_contract、com_sales_order、com_order_line、com_delivery_scope、com_delivery_scope_detail
- API：/contracts、/sales-orders、/order-lines、/delivery-scopes
- 事件：DeliveryScopeAssigned/Released
- 外部集成：ERP（合同订单权威）；CRM仅提供项目/客户上下文
- 文件契约：N/A（不产生或不持有文件正文）
- 工作流/状态：ERP订单行同步、范围主记录及明细分配/释放、明细合计一致性和超分配门禁
- 授权与数据范围：ContractProjectScope；ERP核心字段只读
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；外部集成映射、超时/重试/对账/降级测试
- Phase 3 PRD验收基线：WHEN ERP合同、销售订单或订单行数据可用（接口同步或经授权人工补录待核对）；THEN 平台按ERP来源业务键关联项目并展示权威字段及来源状态；接口不可用不阻断项目内部流程，但未取得ERP权威数量前不得将待核对数量视为最终可分配量；WHEN 项目经理分配订单行到项目；THEN 系统校验数量、地点和权限，生成可追溯的范围分配记录；WHEN 分配数量超过可用数量；THEN 系统拒绝保存并提示已分配明细
- Phase 3授权拒绝断言：越权按“ContractProjectScope；ERP核心字段只读”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“ERP订单行同步、范围主记录及明细分配/释放、明细合计一致性和超分配门禁”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Contract、SalesOrder、OrderLine、DeliveryScope、DeliveryScopeDetail”及数据表“com_contract、com_sales_order、com_order_line、com_delivery_scope、com_delivery_scope_detail”；事件边界为“DeliveryScopeAssigned/Released”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“ERP（合同订单权威）；CRM仅提供项目/客户上下文”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 项目进入准备阶段；THEN 系统按项目冻结模板加载生效表单模板版本并生成实例；WHEN 用户填写条件字段并提交；THEN 系统执行字段校验、保存版本并进入配置的评审状态；WHEN 模板被更新；THEN 已提交实例继续引用旧版本，新项目使用新版本
- Phase 3授权拒绝断言：越权按“ProjectStageScope；Schema维护与实例填写分离”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“Schema发布后只读、实例按版本校验”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“DynamicFormSchema、DynamicFormInstance”及数据表“sol_dynamic_form_schema、sol_dynamic_form_schema_revision、sol_dynamic_form_instance”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录

### IMP-01

- 需求名称：阶段质量检查表
- 数据对象：ImplementationQualityCheck
- 数据表：imp_quality_check、imp_quality_item、imp_quality_remediation、imp_quality_review
- API：/quality-checks
- 事件：ImplementationQualityGateChanged
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：提交→复核→整改→再复核
- 授权与数据范围：ImplementationProjectBatchScope；复核权限
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3 PRD验收基线：WHEN 项目进入配置阶段；THEN 系统按类型和阶段生成检查表实例；WHEN 检查项判定为不合格；THEN 必须填写问题、责任人并提交整改任务，阶段门禁显示阻断；WHEN 整改复核通过；THEN 检查项关闭并保留原始结果、整改前后证据
- Phase 3授权拒绝断言：越权按“ImplementationProjectBatchScope；复核权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“提交→复核→整改→再复核”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ImplementationQualityCheck”及数据表“imp_quality_check、imp_quality_item、imp_quality_remediation、imp_quality_review”；事件边界为“ImplementationQualityGateChanged”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 管理员新增或更新服务商资质；THEN 系统校验证书类型、有效期和附件版本，形成审核记录；WHEN 项目经理选择服务商承接任务；THEN 系统只展示授权范围和有效期内的服务商；WHEN 资质过期；THEN 禁止新分派并提示责任人，历史任务可查询
- Phase 3授权拒绝断言：越权按“OrganizationSupplierScope；资质文件字段权限”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“服务商档案、资质版本和可用状态”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“Supplier”及数据表“res_supplier、res_qualification”；事件边界为“N/A（同步命令或查询，无跨 Context 业务事件）”，文件边界为“FileArtifact”，外部集成为“OA”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；脱敏请求响应、幂等键、重试/对账与降级记录；文件哈希、版本、扫描、引用与权限拒绝记录

### ACC-06

- 需求名称：项目闭环与服务交接
- 数据对象：ServiceHandover、ProjectClosure
- 数据表：acc_service_handover、acc_handover_item、acc_handover_result
- API：/service-handovers
- 事件：ProjectClosureCompleted
- 外部集成：N/A（平台内部契约）
- 文件契约：FileArtifact
- 工作流/状态：交接门禁、静态交接快照和接收确认；不创建持续服务跟踪项
- 授权与数据范围：ProjectStageScope；交接双方项目/服务范围
- Phase 3测试类别：业务规则/聚合单元测试；API契约与输入边界测试；服务端授权拒绝测试；状态/异常恢复测试；幂等与并发冲突测试；数据库约束与迁移测试；事件Outbox/Inbox、重复/乱序/重放测试；文件上传/下载/版本/恶意内容与权限回源测试
- Phase 3 PRD验收基线：WHEN 项目经理发起服务交接；THEN 系统逐项校验交接门禁并展示缺口及责任人；WHEN 所有交接门禁通过且服务经理确认接收；THEN 平台生成服务交接单和交接快照，并将其提供给CLO-01/02作为闭环材料；项目生命周期状态保持ACTIVE，直至CLO-02最终通过后才变为NORMAL_CLOSED；WHEN 客户或服务经理拒绝交接，或仍存在未关闭的阻断性遗留问题；THEN 服务交接保持待整改，项目`lifecycle_status`保持ACTIVE并记录拒绝/阻断原因和整改待办
- Phase 3授权拒绝断言：越权按“ProjectStageScope；交接双方项目/服务范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“交接门禁、静态交接快照和接收确认；不创建持续服务跟踪项”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“ServiceHandover、ProjectClosure”及数据表“acc_service_handover、acc_handover_item、acc_handover_result”；事件边界为“ProjectClosureCompleted”，文件边界为“FileArtifact”，外部集成为“N/A（平台内部契约）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN CRM或产品主数据同步服务状态；THEN 设备档案展示状态、来源、生效日期和影响说明；WHEN 设备处于停止服务状态；THEN 向授权责任人生成提示并可关联现有SRV服务提示/任务；ACC-05持续服务跟踪仅在V3启用后可作为进一步关联对象；WHEN 来源状态被撤回或更正；THEN 平台按新版本更新当前状态，历史状态和通知记录保留
- Phase 3授权拒绝断言：越权按“ProjectDeviceScope；客观事实只读”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“客观维保/停产停维状态计算与提示”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“MaintenanceFact、ServiceStatus”及数据表“ast_maintenance_fact、srv_service_status”；事件边界为“ServiceStatusChanged”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“CRM”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 外部RMA回调替换关系；THEN 平台校验RMA单号、原设备和新设备，生成替换关系并更新档案；WHEN 新设备已归属其他项目或序列号重复；THEN 拒绝自动变更并进入人工核对；WHEN 替换成功；THEN 原设备历史可查，新设备显示继承字段、来源和生效时间
- Phase 3授权拒绝断言：越权按“ProjectDeviceScope；设备与来源范围”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“RMA替换、设备归属校验和维保事实衔接”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“RMAReplacement、MaintenanceFact”及数据表“ast_rma_replacement、ast_maintenance_fact”；事件边界为“DeviceStatusSynchronized”，文件边界为“FileArtifact”，外部集成为“备件系统”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
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
- Phase 3 PRD验收基线：WHEN 设备维保起止日期或权威服务状态变更；THEN 系统按生效规则重新计算当前状态并记录规则版本；WHEN 查询设备档案或项目设备清单；THEN 展示当前状态、计算时点、数据来源和缺失字段提示；WHEN 维保信息缺失或日期矛盾；THEN 状态标记为数据待核实，不得推断为已过保或在保
- Phase 3授权拒绝断言：越权按“ProjectDeviceScope；客观事实只读”拒绝，不返回未授权业务事实且不产生业务副作用
- Phase 3业务守卫断言：按“客观维保/停产停维状态计算与提示”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变
- Phase 3副作用断言：成功仅按契约写入/引用数据对象“MaintenanceFact、ServiceStatus”及数据表“ast_maintenance_fact、srv_service_status”；事件边界为“ServiceStatusChanged”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“CRM”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝/失败审计和已有事实不变的结果。
- Phase 3证据类型：自动化测试报告（用例ID、业务对象ID、断言与结果）；数据库迁移/约束验证记录；事件消息ID、Outbox/Inbox及消费水位证据；脱敏请求响应、幂等键、重试/对账与降级记录
