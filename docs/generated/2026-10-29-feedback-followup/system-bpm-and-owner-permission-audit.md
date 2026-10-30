# 服务经理完整项目经理权限：SYSTEM、BPM及跨模块审核

2026-10-29。依据用户本轮明确裁决：服务经理是区域负责人，在授权区域内具备项目经理完整功能；实施方案允许服务经理本人发起后本人作真实人工审批。该裁决不改变租户、项目范围、冻结历史、指定任务执行人、审批职责或业务状态机。本记录属于过程证据。

本轮后续按用户要求继续收敛为PROJ公开的统一项目管理资格能力。下列前轮红绿与独立审查记录属于收敛前过程证据；统一后的最终独立审核见文末，冻结产物和运行浏览器验收仍须分别记录。

## SYSTEM与前端

16:37只读178库有效角色及菜单对照：租户1 PROJECT_MANAGER角色993109110002有179个有效菜单、139个非空权限；SERVICE_MANAGER角色993109110001有209个有效菜单、167个非空权限。以同租户PM有效菜单ID逐项做差，服务经理缺失项为0。当前无启用的SERVICE_MANAGER_L1/L2角色。完整菜单、父菜单、路由、组件及权限映射见`service-manager-role-grants-readonly.json`。

该证据仅证明当前数据库授予，不证明每个账号登录缓存、运行接口、项目数据范围或Owner资格均通过。当前有效区域与服务经理账号均兼具PROJECT_MANAGER系统角色，没有可直接用于浏览器验证纯服务经理角色隔离的现成账号；盘点见`pure-service-manager-samples-readonly.json`。没有修改用户角色或授权来造样本。

- `PermissionServiceImpl.hasAnyPermissions`按当前启用角色与菜单权限求交，不自动把SERVICE_MANAGER等同PROJECT_MANAGER。此次当前菜单集合已包含，未改平台通用角色语义。
- 前端`hasPermi/checkPermi`使用登录返回的权限集合，动态菜单由登录角色路由生成；PMS页面未找到仅向PROJECT_MANAGER开放、同时排除SERVICE_MANAGER的前端角色按钮分支。
- `commerceMenuVisibility`对通用PROJECT_MANAGER/SERVICE_MANAGER同样隐藏两项商务独立工作台。`stagePlanMenuVisibility`对两者同样隐藏施工计划独立菜单，保留项目阶段入口。这是既有入口裁定，不是服务经理缺少PM功能。
- 工程管理/管理员专属模板配置、原始事实页等，对PM和SM均受同样限制，不因本轮“SM包含PM”自动扩展为管理员能力。
- V513/V517不能按当前文件名推断已执行内容：178历史V513为`project manager draft record delete`；历史V517实际为`restore default task state machine`。当前工作树同号V517权限包含种子不是该历史脚本。本轮未执行任何迁移。

## BPM：已修复的确定差异

原`BpmServiceManagerAccess.requireProcess`只给PROJECT_MANAGER提供SOL_REVIEW流程的项目范围阅读例外；纯服务经理即使对项目有FULL VIEW，也必须是流程参与人才能读。`restricted()`同时遗漏SERVICE_MANAGER_L1/L2别名，导致别名未进入与PM/SM相同的参与/办理限制。

最小修改仅在这两处复用`ProjectMemberRoles.MANAGEMENT_CODES`。同租户SOL_REVIEW业务键、流程projectId和当前PROJECT_VIEW FULL范围继续全部校验。其他流程仍遵守本人发起/参与规则；真实办理仍必须是同租户当前assignee；高级转派/加签/复制等仍按原PM/SM规则拒绝。未把角色包含变成任意流程读取或任意审批权。

- 失败测试：18项中5项准确复现（通用/历史SM的同项目流程阅读差异及历史别名参与限制漏接）。
- 修复后：18项全部通过，含长ID、无FULL范围、其他租户业务键、非法项目ID、非assignee、未领取任务、其他租户任务、高级动作和既有BPM管理权限保护。
- 使用固定`worktree-toolchain.ps1`及共同构建锁，仅做定向Mockito测试；没有连接测试数据库创建流程。
- 日志：`.run/feedback-20261029-bpm-service-manager/red.log`、`green.log`。`git diff --check`通过。
- repair代理已独立审阅相对before的两文件差异及红绿证据，无发现。未部署、迁移、重启或修改业务数据。

另独立复核工程代理追加的真实H2 Flowable自审用例：actor7发起普通方案，冻结serviceManagerReview候选/assignee7，仍处RUNNING等待人工；经过原guard真实完成后，历史任务assignee7、endTime及人工APPROVE局部变量存在，运行流程结束。该fixture缺平台终态监听器，不能将其表述为生产SOL最终业务状态已通过；没有伪造流程终态变量。生产BPM候选、网关和人工审批逻辑未改。

## PROJ缺口交接与业务事实边界

以下实际管理资格链已独立核对并交`project_permissions`代理成组修复：任务元数据编辑/生命周期管理与业务绑定按钮投影、阶段readiness与advance写锁重验、正常闭环发起及联系人Owner写上下文。原实现分别在PROJECT_MANAGE FULL成立后仍仅接受PM成员，闭环还要求操作者等于主表主责managerId。读投影与写命令必须同步，不能只恢复按钮。

修复只应接受已确认管理成员资格或当前区域服务经理Owner资格，保留功能权限、区域/租户FULL范围、项目版本、成员/区域来源锁重验和状态机。选定资格锁失败不能再fallback成另一资格。正常闭环Context中的真实主责PM仍需保留，不得把SM伪装写入managerId。

16:55独立复核PROJ最终实现及补正测试，无剩余发现：联系人读写统一使用已确认授权成员；指派候选只读不锁，写命令先选定成员来源项目及角色，再锁重验相同来源，失效不转区域授权；任务维护调用方同步使用写锁上下文。无指定执行人的当前区域服务经理可按PM同权办理，指定他人时继续拒绝。Controlled Operation在执行记录锁之后、真实Owner调用之前新增实际任务执行资格锁重验，原规划/指派Owner独立例外和状态校验保留。

已核读首轮161项、执行链155项及最后受影响六类54项日志，均通过；最后一轮包括Contact10、Regional3、OperationResolver8、ControlledExecutor13、Assignment11、Maintenance9，失败/错误/跳过均0、进程退出0。对应日志为`.run/proj-manager-tests-20261029.log`、`.run/proj-manager-execution-tests-20261029.log`、`.run/proj-manager-review-tests-20261029.log`，共同构建锁已释放。这些是源码与定向测试证据，不替代运行部署或真实浏览器验收。

以下属于业务事实，不是管理操作授权，不机械扩展：S0必须配置真实主责PM与服务经理；选择项目经理时使用正式公司资格候选；历史主责/负责人显示；指定任务执行人；服务经理审批节点候选和工程管理部复审授权；通知收件人的既定业务角色。即使SM可执行PM管理操作，也不因此改写这些事实或审批路由。

## 其余Owner链的已核对范围

| 范围 | 当前观察 | 本轮处理边界 |
|---|---|---|
| 巡检SERVICE | InspectionAccessService对PM及SERVICE_CODES使用相同管理角色读取路径，项目查询仍要求当前FULL范围；命令按实际task owner或本项目服务经理资格，写时重读任务并校验版本/归档状态 | 未发现“PM能做而SM因仅角色而不能做”的新增差异。不能把PM本来也不能办理的他人任务任意授给SM |
| 割接CUT | P2/P3/P4写命令按本租户真实负责人，项目范围和来源锁重验独立执行；新建负责人使用实际actor。服务经理与PM受相同Owner约束 | 保留负责人、清单、计划、已发货设备和状态机条件，不以角色包含绕过；服务经理审批节点仍按冻结职责和当前资格 |
| 割接保障候选 | DP_FIRST_LINE映射PROJECT_MANAGER系统候选，SERVICE_MANAGER审批职责也有独立候选来源 | 这是具名保障角色/审批候选资格，不能仅按管理权限裁决机械改成所有SM；本轮不改候选业务身份 |
| 商务COM | DeliveryScopeConflictNotifier选择PM为业务通知收件人，非actor授权；Owner操作通过项目/合同公开范围接口，不以PM系统角色字面做额外放行 | 保留来源/合同范围及通知角色，不把通知送达视为业务完成 |
| 资产AST | 后端使用既有功能权限与项目/合同范围；人工设备创建、归属调整、发货来源相互独立；现有菜单包含关系不生成发货事实 | 不改设备来源、不挪用真实设备、不改发货时间或跨项目范围 |
| 验收ACC | 正常闭环消费PROJ ProjectClosureContextApi；发现的PM-only发起限制在PROJ链已交接。验收模板管理员能力对PM/SM相同 | 不将角色包含用于替代签字/验收完成、主责人员、Owner批准历史 |
| 平台PLT | 通用entity/dynamic-form按功能权限与Owner策略；导入等超管操作同样不属于PM能力 | 不修改通用角色匹配或扩大管理员功能，业务Owner内的具体差异由对应模块修复 |

上述是已检查的授权入口、角色分支与共用Owner边界，不声称所有业务场景均已完成真实浏览器验收。工程需求分析、施工计划、方案等详细审计由repair代理另记；PROJ及其跨模块消费方的最终修复与验证由project_permissions代理另记。

## 继续核对实际消费链

本轮继续从实际Controller、查询及命令追到授权Owner，而非把角色字符串命中直接计为缺陷：

- AST的DeviceController、DeviceQueryService、设备日志下载及AssetCustomerDeviceSummaryApi消费DeviceAccessScopeService；项目FULL VIEW、合同公司／部门和设备组织授权各自保留。日志归档、解析事实和文件策略写操作使用项目EDIT范围；未见另加PM身份的actor分支。
- ACC的验收、完成证明、归档文档及交付清单CRUD消费NativeAcceptanceProjectAccess，写操作使用MANAGE FULL并锁重验。满意度指定任务详情仍限真实assignedToUserId，对PM、SM相同；管理和重采集走当前项目EDIT。正常闭环读取／提交消费PROJ已修补的ProjectClosureContextApi，审批仍走既有显式授权。
- COM交付范围命令的lockProject使用MANAGE FULL锁重验及ProjectOfficeFact；现行代码没有旧ProjectDeliveryScopeQualificationFactApi的生产实现或消费者，不能由旧SDS中的PM文字认定运行缺陷。CUT的OwnerAdapters将VIEW／EDIT交给ProjectScopeApi，TaskApplicationService预检与锁重验没有额外PM要求；清单、方案和总结继续核真实task owner。
- SERVICE的查询、普通写、UMC和在线采集路径均调用InspectionAccessService；PM和SERVICE_CODES共用管理阅读角色条件，写操作核真实owner或本项目服务经理，并锁任务、核版本及归档状态。InspectionOnlineAuthorization还核submittedBy、当前attempt、执行状态与独立采集凭据授权，不能据管理层级跳过。
- 前端PMS的角色分支主要为身份展示、候选和管理员专属配置。BPM done和ProcessInstanceOperationButton存在仅通用SM的ApprovalOnly显示条件，但withdraw、cancel、recreate及高级操作的实际后端同样调用requireAdvancedAction拒绝PM和SM；不把“PM显示但后端拒绝”的按钮误报为PM实际拥有而SM缺少的功能。

新增交repair的确证入口：PreparationController的PRE_04 GET真实调用RequirementAnalysisDynamicFormQueryService；原isManager只接受PM，导致相同项目范围的SM无法查看旧需求分析草稿或比较。已弃用固定章节RequirementAnalysisQueryService虽仍注入，但没有实际HTTP调用，未单独列缺陷。当前实体需求分析RequirementAnalysisAccess的已确认SM与区域资格分支亦已交repair统一修复，最终审核见下文。工程新增EngineeringManagementAccess与七个preparation服务相对before的生产差异已独立复核，未发现放宽范围、指定人或状态的情况。

17:01已核读准备项失败及最终日志：red-query为17项中3个SM等价角色失败，preparation-green-final为64项全部通过（0失败／错误／跳过、BUILD SUCCESS）。新helper只对明确无资格错误转区域事实，成员／区域锁失败和意外异常继续传播；角色、账号及组织来源由原Owner锁重验。准备豁免原申请人回避仍在requireDecision和动作投影两处；管理同权新增本人SUBMIT／WITHDRAW不构成允许本人APPROVE／REJECT。本人发起本人审批的用户裁决只适用于实施方案，未扩展到豁免。该批准备项源码和测试独立审核无发现。

17:14需求分析与到货最终独立审核无剩余发现：需求分析保留功能权限与FULL管理范围，普通actor使用确认的实际MANAGEMENT角色并锁重验；只有精确无资格码可转区域Owner，未知Owner失败和成员锁撤权不能fallback。原super_admin使用真实PM原始事实的独立路径及system observer契约保留。到货只有最终确认分支扩展为管理同权；创建草稿的inspect及现场成员提交分支不额外要求管理身份，真实PM事实、角色、factVersion、项目版本、S4与scope仍独立复核，未把操作者SM冒充为被冻结PM。

最终`.run/feedback-20261029-engineering-management/requirement-arrival-green.log`共40项通过（Access15、DynamicQuery16、Arrival9，0失败／错误／跳过，退出0）；对应red-requirement为34项中4个断言失败和3个SM草稿403。`.run/feedback-20261029-engineering-management/arrival-green.log`另有Arrival9与Waiver10共19项通过，包含真实执行SM申请人APPROVE命令被拒且不写状态的保护。到货审查曾发现新增管理人预检会收窄现场成员创建草稿，已撤回该新增限制并由最终测试覆盖inspect／非最终确认不调用管理helper。源码与测试已停止修改，待主线程固定冻结；本记录不声称已部署或已完成上述所有路径的真实浏览器验收。

## 统一管理资格的最终独立审核

17:47统一后生产源码与受影响测试审核完成，无剩余发现。PROJ公开ProjectManagementQualificationApi集中检查确认成员或区域服务经理，并将实际来源封装为Fact；lockAndRevalidate只重验原Fact，lockCurrent在MANDATORY事务边界内单次选源、按调用方已有项目版本立即锁同源。ACTIVE、主体、版本及区域并行阶段要求均实际校验。EngineeringManagementAccess、ProjectRegionalManagementAccess及后续纯参数适配ProjectManagementAccess均已删除，普通管理消费直接调用统一API；项目action scope与功能权限仍由业务Owner独立校验。

AUTHORIZATION用途接受已确认协作SM，真实assignmentType唯一时原值返回、混合时为null；Java及真实Mapper XML中PRIMARY责任约束只留FACT用途，未将协作SM造为主责。H2执行真实XML证明确认／有效区间／租户／删除／停用保护与FACT责任口径分开。工期Owner也已接入统一资格，返回真实PM、管理成员、区域三种basis；首次非PM操作者仍须存在原有效PM，未扩大具名审批职责。延期文件reader保留历史raw FACT路径，仅明确无资格才转统一能力；未知Owner错误及已选raw事实锁失败不能换源，统一来源锁传原Fact。

统一PROJ首轮326的7个失败逐项复核为测试夹具迁移问题：4个Mockito覆盖stub时的null占位、S0授权锁顺序替换、Lifecycle旧成员stub失效及多余stub。补正保留无evaluation／无状态写、指定assignee、真实PM／SM责任锁及Gate断言；另加跨项目确认SM来源成功／撤权两例，以及撤权后不得进入跨项目fallback查询。最终`.run/management-unification-project-recheck-20261029.log`107项全绿，原其余18类有效绿仍适用，去重22类328项；其中统一Owner13、Participant12、Duration11、真实XML H2 9共45项。

统一工程消费首轮`.run/feedback-20261029-unified-management/engineering-green.log`191项全绿；管理员rawPM明确无资格可继续使用自身真实统一资格，未知Owner故障保持拒绝，其2个精准失败用例后完成修复。最终`engineering-final.log`中File12与Query17有效绿，Access用例的旧throw覆盖需改用doThrow，原故障拒绝断言未减；`access-recheck.log`15项于17:47全绿。每类取最新有效结果，工程去重205项，不将重叠轮次相加。

全局覆盖索引见[global-service-manager-permission-review.md](global-service-manager-permission-review.md)。上述日志均已独立核读，源码和测试已停写，可进入获授权的固定冻结；冻结、部署、重启与真实浏览器验收不由这些源码／测试回执替代。

18:00补充server装配夹具独立审核：旧RegionalPlanningWorkbenchChainTest在全部功能权限均为true时仍要求区域SM任务元数据／关系只读，与用户新原则冲突。迁移仅改测试：默认明确禁任务create/update/move/assign，两套权限端口一致；保留原规划Owner、EDIT／ACTIVE、终态、结束轮次、冻结绑定、版本和范围负例；另增全任务功能同权正例与指定他人普通关系／执行拒绝，规划Owner独立准入保持。实际统一API／Regional Owner链参与夹具，未用恒真资格代替。

Contact两类保留闭环历史读取／写入边界并改为真实确认成员链；两工期夹具使用实际统一／区域Owner，首次工期拒绝额外核实组织锁及真实PM查询确实发生。server首轮57中唯一失败是反射实例漏注StagePlanBatchServiceImpl.executionGuard；补现有SPI后核精确PLN／STAGE_PLAN请求、原异常传播及items／batches不写入，未改生产。`.run/feedback-20261029-unified-management/planning-fixture-recheck.log`13项于18:00:06全绿，余Contact21／Customer16／Duration6／Initial1原有效绿保留，五类去重57项通过。夹具迁移未降低授权、状态或Owner准入校验，可继续固定重冻。
