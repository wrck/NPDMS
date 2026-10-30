# 测试反馈代码定位批次 1

只读索引。以 `问题跟进.md` 当前纳入条目为准；不读取原始或修正版 Excel，不修改代码、主跟进文档、状态或数据库。下面的“代码现状”只描述本地源码和已有测试，不代表目标环境已部署或已验收。对涉及规则或数据的问题，列出仍需提供的具体事实。

## TF-005｜立项录入信息未同步到客户信息页

- **当前入口与数据链**：项目详情的“客户信息”区域在 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue:207-209` 引入 `components/ProjectCustomerOverview.vue`；该组件 `:95,136-155` 通过 `CustomerApi.getProjectCustomerOverview(project.id)` 按项目关系加载客户主档。项目客户联系人另由该组件引用的 `src/views/pms/customer/contacts/index.vue` 管理。最终用户/项目成员则在项目详情成员页使用 `src/views/pms/project/inheritance/members/ProjectMembersPanel.vue`，调用 `src/api/pms/project/unified-members/index.ts` 的成员接口；这两者不是同一个写入/回读对象。
- **代码现状**：代码按项目客户关联取客户主档，没有看到把项目成员/用户信息自动复制到客户主档联系人字段的写入链。此为对象边界事实，不能据此认定用户期望的同步规则。若输入实际是项目最终用户，客户信息页的客户联系人区不必然显示该成员。
- **已有测试**：`ProjectCustomerOverview.runtime.spec.ts` 覆盖按项目关联读取、无客户关联及拒绝读取；`ProjectMembersPanel.runtime.spec.ts` 和 `OrdinaryMemberForm.runtime.spec.ts` 覆盖项目成员的查询/维护路径。现有用例没有跨对象“立项用户信息→客户联系人”同步验收。
- **待补真实信息**：项目ID；录入入口和保存对象（客户联系人、项目联系人还是项目最终用户）；保存请求及回读请求/响应；目标客户ID、联系人ID和项目关联。缺少这些信息无法判断是不同对象、关联缺失、加载失败还是未实现预期映射。

## TF-007｜无法引用其他项目的已录入地点

- **当前入口与数据链**：新项目建档清单在 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/projects/index.vue:581-619` 可选择已维护站点，`1604-1606` 在有 `pms:asset-location:query` 权限时加载 `LocationApi.getSitePage({ pageNo: 1, pageSize: 100 })`；兼容地点则是自由文本输入。工勘表单 `src/views/pms/delivery-business/site-survey/index.vue:171-178` 使用 `PmsLocationSelector`，并设置 `project-scoped`。
- **代码现状**：建档站点选择没有传项目ID筛选，但只取前100条且受站点查询权限影响；工勘位置组件则明确是项目范围选择。不能把“跨项目共享地点”假设为所有入口共用规则，也未从静态代码确认目标地点是否因权限、分页、客户/项目范围而不可见。
- **已有测试**：`projectListInitialization.runtime.spec.ts` 覆盖有权限时取站点、无权限时不取站点；`ProjectPreparationPanel.spec.ts` 检查旧工勘页面复用 `PmsLocationSelector`；工勘有 `siteSurveyForm.spec.ts` / `siteSurveyProjectContext.runtime.spec.ts`。没有覆盖“项目A地点能否被项目B引用”的权限及范围组合。
- **待补真实信息**：录入入口和具体字段；来源项目/目标项目ID；地点ID/编码；两项目租户、客户/区域关系；当前账号及查询权限；站点是否在前100条、列表API响应和目标选择器请求。业务范围应由既有规则确认，不能仅凭反馈开放跨项目引用。

## TF-008｜电话、邮箱缺少输入校验

- **当前入口与数据链**：项目成员维护的外部最终用户输入位于 `src/views/pms/project/inheritance/members/OrdinaryMemberForm.vue:14-15`，电话/邮箱是可编辑文本框；`:131-142` 保存前要求最终用户姓名，但只对电话和邮箱做 `trim()`，请求传入 `externalPerson`。所选系统人员的手机号/邮箱在 `:31,34` 只读回填。
- **代码现状**：外部最终用户电话/邮箱没有前端格式校验规则；文本框有长度限制（64/255）。当前没有依据判断应该校验手机号、座机、邮箱格式或允许空值的范围，因此不能提出具体校验规则。
- **已有测试**：`OrdinaryMemberForm.runtime.spec.ts:47-78` 覆盖外部用户无需系统候选、姓名必填和外部成员提交；`:92-110` 覆盖所选服务经理显示通讯录信息。没有有效/空/异常电话邮箱的业务规则测试。
- **待补真实信息**：报障页面/字段（外部最终用户还是从系统目录选出的人员）；输入样例及保存请求结果；批准的电话/邮箱格式和是否可空规则。格式策略需有权威业务约定后才能判定是否缺陷。

## TF-011｜人员选择只能检索当前登录人

- **当前入口与数据链**：项目成员编辑 `OrdinaryMemberForm.vue` 的 `search`（约 `:88-109`）把角色、搜索关键词和页码传给 `getMemberCandidates`；API 在 `src/api/pms/project/unified-members/index.ts:62-65` 请求 `/api/v1/pms/projects/{id}/member-candidates`。另一个可见入口是项目列表筛选，在 `src/views/pms/project/projects/index.vue:73-82,195-218` 使用 `userOptions`，由 `UserApi.getSimpleUserList()` 加载（约 `:1881-1882`）。这两种候选链不能混为一谈。
- **代码现状**：成员维护候选有服务端分页/关键词输入；切换角色清空旧人选，且不会把旧角色迟到响应覆盖到新角色（代码约 `OrdinaryMemberForm.vue:90-115`）。项目列表筛选则用简单用户列表作本地选项。UI代码没有将搜索固定为当前登录人，但实际返回集合受服务端授权和数据范围影响。
- **已有测试**：`OrdinaryMemberForm.runtime.spec.ts:80-110,135-194` 覆盖人员选择、通讯录回填、候选重试、角色切换及迟到响应；`ProjectMembersPanel.runtime.spec.ts` 覆盖默认角色筛选。未见针对提报账号及其项目范围真实验证不同PM/服务经理/销售候选集合的用例。
- **待补真实信息**：具体是哪一个入口/角色字段；登录账号、目标候选账号和项目ID；候选API请求参数、HTTP状态及响应的total/list；该账号应有的授权和组织范围；若是列表筛选，还需 `/simple-list` 响应。没有响应证据不能判定是UI关键词失效还是后端范围过滤。

## TF-013｜项目成员联系方式缺失或未带出

- **当前入口与数据链**：项目成员表格在 `ProjectMembersPanel.vue:35-36` 读取成员行的 `mobile/email`。所选系统用户表单字段只读，取候选记录的 `selectedPerson?.mobile/email`（`OrdinaryMemberForm.vue:31,34`）；外部最终用户的电话、邮箱则由可编辑字段保存到 `externalPerson`（`:14-15,140-142`）。后端项目成员读取另有 `ProjectMemberSelectionApiImpl`，从用户目录或外部成员字段投影联系方式。
- **代码现状**：内部用户联系方式来自当前用户候选/成员响应，前端不会用本地缓存覆盖；外部最终用户联系方式可编辑并写入独立成员资料。历史迁移、目录人员主数据、保存后成员查询是不同数据来源，静态代码不能判断哪一项缺失。选中人员电话字段只读是当前页面行为，不是回填失败的证据。
- **已有测试**：`OrdinaryMemberForm.runtime.spec.ts` 覆盖服务经理候选通讯录展示与外部最终用户字段提交；`ProjectMembersPanel.runtime.spec.ts` 覆盖成员列表过滤；后端 `ProjectMemberSelectionApiImplTest` 覆盖外部成员联系方式读取。未发现覆盖提报的历史迁移记录、所有内部角色回填和真实列表回读组合的测试。
- **待补真实信息**：分别提供来源行19b/31/49/61对应的成员ID、角色、来源（迁移/系统目录/外部成员）、用户ID或外部成员ID；API响应中mobile/email字段；该用户当前目录联系方式；具体字段是否只读及当时选择候选的响应。不要把邮箱缺失、手机号未回填、外部成员不可编辑预设为同一根因。

## TF-015｜按客户名称无法搜索项目

- **当前入口与数据链**：当前项目列表在 `src/views/pms/project/projects/index.vue:45-48` 使用客户选择控件绑定 `query.customerCode`，展示列在 `:318-319`；请求参数由项目API发送到项目分页查询。查询对象 `pms-module-project/src/main/java/.../query/VisibleProjectPageQuery.java` 有 `customerCode` 字段；`pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:190-192` 对其执行客户编码相等过滤。当前筛选是选择客户主档，再按编码过滤项目，不是对任意输入字符串执行项目客户名称模糊搜索。
- **代码现状**：查询链支持按客户关联编码筛选；没有在该Mapper条件中看到 `customer_name LIKE`。客户选择器本身是否可用名称搜索，取决于它调用的客户候选API和响应，不能从项目筛选条件推出名称候选失效。用户所说“按客户名称搜索”是自由文本项目搜索还是客户下拉筛选，目前未明确。
- **已有测试**：`projectListInitialization.runtime.spec.ts` 覆盖项目列表初始化与保留筛选条件；`index.spec.ts` 主要是页面结构/表单合同；没有明确覆盖“客户名称匹配出项目”的API+页面断言。
- **待补真实信息**：搜索框/选择器截图；客户名称、编码和项目关联样例；输入名称后客户候选请求及结果、选中后的项目分页请求参数/响应。若需求是自由文本搜客户名，需先确认该查询规则；若是客户选择器无法列出客户，则需客户候选API响应。

## TF-018｜工勘保存后提示无权限或无法再编辑（仅来源22/E22纳入）

- **当前入口与数据链**：当前工勘页面 `src/views/pms/delivery-business/site-survey/index.vue` 可由独立工勘入口打开，也接受项目/任务/阶段上下文。`can` 按宿主 `allowedActions/readonly` 收窄操作（约 `:345-355`）；保存 `:717-730` 校验后生成保存意图，执行命令并读取回执（`:675-710`），再次打开时按当前项目和查询权限拉取对象（`:644-658`）。项目详情任务页的 `ProjectFlowPanel` 根据可信任务route映射内嵌组件；site-survey不在已读到的内嵌映射清单中，因此不能把该独立页面与项目详情内嵌页面视为同一入口。来源E25不在本次范围。
- **代码现状**：当前代码将写命令结果与回执重读分开处理；保存成功但回执读取失败会保留原请求并提示恢复读取。再次编辑还要求对象状态为可编辑及当前 `UPDATE` 权限。静态实现未显示无条件“保存后写成功即可编辑”的捷径。
- **已有测试**：`siteSurveyProjectContext.runtime.spec.ts:88-225,318-393` 覆盖执行上下文、同项目身份、权限 allowlist、写后回执重开、读取失败恢复和重试；`siteSurveyForm.spec.ts` 覆盖地点输入形态。用例不能证明具体用户/对象当前后端权限或保存事务成功。
- **待补真实信息**：E22对应工勘ID、项目ID、执行/任务/阶段上下文；保存、回执读取、再次编辑请求的URL、HTTP状态和响应业务码；对象保存前后状态/version；操作者在保存和重开时的实际授权投影。需区分“拒绝保存”“保存已成功但回执读取失败”“对象被完成/只读”三种时序。

## TF-020｜需求分析没有创建入口

- **当前入口与数据链**：新项目详情包含 `ProjectRequirementAnalysisPanel.vue`；顶部“创建需求分析草稿”由 `canCreateInitial` 控制，定义在该组件约 `:129-134`：没有 draft、没有 currentEffective，且服务端 overview `allowedActions` 中有 `CREATE_INITIAL_DRAFT` 或 `CREATE_DRAFT` 才显示。宿主 `allowedActions/readonly` 进一步收窄服务端动作（`:91-98`）；创建时请求 `RequirementAnalysisApi.createInitialDraft`（`:270-280`）。
- **代码现状**：按钮不是只按前端角色/本地模板判断；依赖项目工作区、任务模板绑定/运行态及服务端动作清单。代码有在宿主已允许创建但overview旧动作缺失时重新读取当前动作元数据（约 `:383-410`）。静态上存在创建草稿入口，无法从代码单独断定提报时该项目/用户应得到 `CREATE_DRAFT`。
- **已有测试**：`ProjectRequirementAnalysisPanel.spec.ts` 覆盖加载、刷新、动作来源及 PRE-04 路由合同；`:209-231` 检查服务端动作及草稿/有效版等状态逻辑；`requirementAnalysisInteraction.spec.ts` 和 `RequirementAnalysisDynamicForm.runtime.spec.ts` 覆盖交互/保存状态。未见目标项目和目标账号真实动作响应。
- **待补真实信息**：项目ID、登录角色/权限、所在页面（项目详情需求分析Tab还是阶段任务）、当前阶段/任务与模板绑定；`getCurrent` 响应的 draft/currentEffective/allowedActions；宿主传入的 `allowedActions/readonly`。这些字段足以区分按钮隐藏是预期动作门控还是权限投影/状态返回问题。

## TF-021｜Visio网络拓扑导入报错

- **当前入口与数据链**：新版项目详情需求分析面板将服务端返回的已绑定表单交给 `components/RequirementAnalysisDynamicForm.vue` 与动态表单运行时；平台组件 `src/views/pms/platform/dynamic-form/components/PmsFileArtifactField.vue:37,50` 负责动态文件字段上传。版本附件管理另有 `src/views/pms/delivery-business/requirement-analysis/entity/RevisionFiles.vue`。本地 `delivery-business/requirement-analysis/template-20261013.json` 是现有测试/样例输入，不据此认定它就是该项目当前绑定的服务端模板。
- **代码现状**：当前动态表单文件字段在 `PmsFileArtifactField.vue:37,50` 固定接受 `.pdf,.png,.jpg,.jpeg,.txt,.doc,.docx,.xls,.xlsx,.ppt,.pptx`，不含 `.vsd/.vsdx`；版本附件 `RevisionFiles.vue:29-34,43-50` 同样不含该扩展名。当前搜索未发现该需求分析页中的Visio解析/import适配器。可直接确认这两条当前文件上传控件的扩展名清单不接受Visio；不能据此确认提报者操作的是哪一条控件，或服务器拒绝是否还存在独立的MIME/解析失败。
- **已有测试**：`RevisionFiles.runtime.spec.ts` 覆盖附件控件挂载、必需附件呈现、上传活动和重试键；`RequirementAnalysisDynamicForm.runtime.spec.ts` 覆盖冻结动态表单渲染和PATCH；`PmsFileArtifactField.runtime.spec.ts` / `src/components/PmsFileArtifact/PmsFileArtifact.runtime.spec.ts` 覆盖文件字段与文件操作，但未见 `.vsd/.vsdx` MIME/解析测试。
- **待补真实信息**：实际页面URL/控件、文件扩展名与MIME、文件大小、上传请求/响应、后续解析请求及完整错误。若要确认Visio是否属于已批准输入类型，还需规格约定；不能仅由一次报错推导必须新增解析能力。

## TF-023｜工期变更审批后重新提交返回404

- **当前入口与数据链**：跟进来源B71为工作流程“审批中心→我的流程”。平台旧通用入口由 `sql/migrations/V1__yudao_platform.sql:2060-2061` 指向 `views/bpm/processInstance/index.vue`。通用页面 `:276-294` 根据流程定义 `formType` 分支：业务表单(20)导航至 `formCustomCreatePath` 并传 `businessKey`；流程表单(10)跳 `BpmProcessInstanceCreate` 并传 `processInstanceId`。流程表单重发页 `views/bpm/processInstance/create/index.vue:109,124-138` 重新GET原实例；API在 `src/api/bpm/processInstance/index.ts:84-86` 调 `/bpm/process-instance/get?id=...`。
- **代码现状**：新项目详情工期入口在 `ProjectSchedulePanel.vue:179,232-236` 内嵌 `ProjectDurationPanel`，走 `/api/v1/pms/construction-plans...`；它和“我的流程”通用重发按钮是两条不同UI链。源码无法判断目标审批实例属于formType 10还是20，也无法断言404发生在自定义路由导航还是后端请求。
- **已有测试**：工期组件有 `ProjectDurationPanel.spec.ts`、`ProjectDurationHistory.runtime.spec.ts`、`ProjectDurationContext.runtime.spec.ts`；未找到“BPM我的流程→重新发起”的PMS工期场景测试。
- **待补真实信息**：原流程实例ID/业务键（可脱敏）、流程定义ID和实际 `formType/formCustomCreatePath`、按钮点击后浏览器地址、404请求URL/方法/响应、业务状态及当前审批实例关联。按这些事实即可分辨错误发生在表单路由还是实例加载/其他请求。

## 判读边界

上述静态检查不能替代真实账号、项目、审批流程或服务端响应的复现。TF-007/008/011/013/015/018/020的最终判断依赖具体对象、权限及后端响应；TF-021已可确认当前文件选择器合同未声明Visio扩展名，但是否属于缺陷仍需确认实际入口与批准格式；TF-023可确认“我的流程”与项目详情工期面板是不同入口，404落点仍需Network证据。