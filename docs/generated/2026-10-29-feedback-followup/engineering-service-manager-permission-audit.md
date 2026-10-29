# 工程模块服务经理同权修复与审查范围

运行状态更新（2026-10-29 18:20）：用户已明确批准463be0ba清单部署重启59191；固定脚本部署完成，当前PID35092、local,dac、健康UP、运行包哈希匹配且22保护通过。下文冻结时“未部署”属于原时点证据；最新部署回执见末段，真实浏览器业务验收仍未在本记录中宣称通过。

2026-10-29。本记录是过程证据，不替代规格或运行验收。依据用户明确要求：服务经理在当前授权区域内拥有项目经理的管理操作能力；功能权限、租户及项目FULL范围、实时撤权、当前版本、指定执行人、状态机、冻结历史与真实主责事实继续生效。实施方案可本人发起后本人作人工审批；准备项豁免原申请人回避不变。

## 已修复的实际消费链

| 实际入口及Owner | 旧缺陷 | 最小修复与保留边界 |
|---|---|---|
| 方案审批、刷新及复制修订 → SolutionTieredReviewService | 通过MANAGE后仍只接受本项目PM | 已确认MANAGEMENT_CODES或现有RegionalManager Owner；以选定来源锁重验，锁失败不切其他来源。功能、范围、方案版本、前置和BPM状态不变。详见[方案权限证据](tf031-service-manager-review-authorization.md) |
| 准备项查询及写命令 → PreparationQueryService、PreparationItemApplicationService、PreparationInitializationService、PreparationReadinessService、PreparationReviewService、PreparationSourceService、PreparationWaiverService | 各处二次硬PM资格使SM读写/动作投影不等价 | 直接调用统一PROJ ProjectManagementQualificationApi；不再在工程组合成员与区域Owner，不创建假PM事实。各业务Owner继续核功能、范围、状态、版本、绑定和具名审批角色 |
| 当前独立需求分析 → RequirementAnalysisAccess / RequirementAnalysisManagerFacts | 同项目确认SM但无独立区域资格被PM守卫排除 | 普通actor直接查询、锁定统一PROJ管理资格；不在工程重新判断成员/区域角色。超管仍取真实主责PM的raw FACT并用原锁端口，system observer读取保留；真实区域Owner来源保留。只有明确无资格码转区域，未知Owner失败不转区域 |
| PreparationController PRE_04 GET → RequirementAnalysisDynamicFormQueryService | 兼容旧需求分析草稿workspace/detail/compare只认PM | 在原功能及MANAGE FULL之后接受确认MANAGEMENT或真实区域Owner。旧内容及PLATFORM文件/表单读取、绑定、版本和动作条件不变 |
| 到货确认 → ProjectQualificationApiAdapter.lockAndRevalidate(requireActorAsProjectManager=true) | 原把actor当PM，SM确认无法通过且可能混淆冻结事实版本 | 独立锁actor管理资格，再查询并锁真实负责PM；返回及比较仍是原PM身份/角色/factVersion。ACTIVE/S4、EDIT FULL、scopeVersion保持。inspect与false分支不额外要求管理资格，授权现场成员可继续录入/提交 |

工期Owner和客户延期文件属于本轮补修：ProjectDurationOperatorApi已收归统一资格，确认项目SM、真实区域SM及PM均由同一Owner判断；首次真实主责PM、冻结审批人和责任事实仍保留。文件reader已补正为明确无raw事实才走统一API，原历史读取保留，已选来源锁失败及未知Owner故障不切换资格。最终Owner45及文件12项验证见下段与全局索引。

用户随后要求收归统一能力：新增ProjectManagementQualificationApi由PROJ独占资格判断与来源锁，工程本轮EngineeringManagementAccess及测试已删除。准备项、到货、方案和需求分析直接调用公开API。统一Fact保留确认成员或区域来源，lockAndRevalidate只重验该Fact来源；功能权限及FULL范围仍由各业务Owner先行校验。统一Owner内部复用原成员授权端口和区域端口，不以未确认关系授予参与权限。区域来源保持真实账号、角色、公司/部门、ACTIVE及版本，来源锁失败直接拒绝。

准备项豁免仅申请/撤回管理能力与PM一致；冻结approvalRoleCode、非申请人条件与requireDecision自批拒绝保持。新增真实execute(APPROVE)本人负例，断言拒绝且不更新豁免。没有扩大用户仅对方案BPM所作的本人审批裁决。

## 已检查但未新增改动的工程入口

- 施工计划Application/Query及勘查实体WriteAccess：消费功能权限、ProjectScope与ProjectBusinessExecution Owner，不另加PM系统身份。执行人和阶段条件交原PROJ Owner执行。
- 安装、配置调试、联调Controller及Service：功能权限和既有项目/设备范围、受控任务执行链没有额外PM-only actor分支。设备选择、安装者、采集提交人、文件来源和状态规则保留。这里记录权限差异审计，不宣称每条业务已真实浏览器验收。
- RequirementAnalysisDynamicFormPolicyProvider已无组件注册、DynamicFormCommand无当前HTTP消费者，固定章节Query没有当前HTTP调用；不据PM字面命中扩展退役写路径。RequirementAnalysisFilePolicyProvider为已弃用历史章节只读文件解释，未恢复其写入。
- 到货REQUIRED_PROJECT_ROLES的PM不是actor授权，而是真实负责PM资格及不可混淆的批次来源事实，故保留。

## 失败测试、回归及独立审核

共同证据目录：`.run/feedback-20261029-engineering-management/`；修改前原件在`before/`（文件名唯一）。所有测试经`worktree-toolchain.ps1`和共同排他锁；未跑MySQL集成、DDL或迁移。

| 检查 | 结果与具体故障 |
|---|---|
| red-query.log | 17项中3项SM/L1/L2动作差异准确失败 |
| preparation-green-final.log | 64项全绿：准备服务、文件策略和管理helper；含确认角色、区域缺失、撤权、未知Owner失败 |
| red-arrival.log | 19项中新增SM独立确认/真实PM来源用例在原生产实现准确失败；其余通过 |
| arrival-green.log | 19项全绿，含Waiver 10项（新增真实command本人自批拒绝） |
| red-requirement.log | 34项中4个断言与3个SM草稿403准确失败；到货9项通过 |
| requirement-arrival-green.log | Access15、DynamicQuery16、Arrival9，共40项全绿；含实际角色锁后撤权、区域有/无、未知Owner不fallback、管理员原路径、现场录入和非管理分支不调用管理helper |
| remaining-protection.log | 64项全绿：到货应用21/命令17/规则与状态机2、Waiver10、需求管理员及其他Owner/兼容保护14；与上一批重叠项目不重复计总数 |

前轮独立review已审核准备7服务/helper、到货和需求3文件的精确before差分及红绿证据。用户新增统一能力要求后，这些日志保留为修复过程证据；统一后的最终结果见下段。工程精确改动`diff --check`通过；全工作树另有非本批次文件的EOF空行提示，不代改并行文件。方案权限/BPM的60项及独立BPM18项另见对应证据，不混作此批总数。

## 用户要求统一能力后的验证

前轮实现原件已冻结在`.run/feedback-20261029-unified-management/before/`对应仓库路径。工程生产中的成员/区域分支全部移除：准备7服务、到货、方案、当前需求分析和兼容草稿读取直接依赖ProjectManagementQualificationApi。旧RequirementAnalysisManagerFacts恢复原契约，只继续服务超管真实PM特殊路径和退役兼容代码。

`engineering-green.log`固定工具链/互斥锁共191项通过：准备57（含豁免10）、到货Owner9、需求Access14/Query16/独立创建24/修订14/受控操作10、方案权限20/提交内容27。原功能/范围、执行身份、任务前置、审批和历史/版本保护均保留。角色/区域资格矩阵转由PROJ统一Owner测试，工程测试校验同一Fact/命令版本透传及业务边界，未用重复资格实现验证自身。

管理员rawPM精确无资格fallthrough新增两例：`admin-red.log`原路径1断言失败/1草稿403；修正只精确捕SCOPE_DENIED继续统一Owner，未知故障仍拒绝。最终`engineering-final.log`的Query17、File12有效绿；Access新增用例覆盖原抛异常mock时由Mockito直接抛旧异常，改用doThrow后`access-recheck.log`15项全部通过（17:47:00，退出0）。不把44项含一夹具错误轮次描述成整轮通过。

文件策略的`duration-file-red.log`11项中3断言失败/1错误准确命中；修复仅在raw事实缺失/明确无资格时调用统一管理API，未知Owner异常不转来源，选中raw事实后锁失败直接拒绝，原历史raw读取及mutation/冻结状态不变。新增原Fact锁正例后12项全绿。

统一后工程每类取最新结果去重205项：191首轮中的Access14→15、Query16→17，再加文件12。独立review已核全部最终生产差异及红绿日志，无剩余发现；PROJ统一Owner、Duration、授权确认成员及H2范围保护详见[全局索引](global-service-manager-permission-review.md)。固定后端构建与ValidateOnly已完成，清单及回执见下段；尚未部署或浏览器验收。

## 冻结构建发现的装配夹具及修正

第一次固定clean package通过所有生产模块编译，但server测试编译发现5个旧构造夹具7处未同步统一API，因此脚本按保护退出1、没有release；没有改用跳过测试编译。仅修测试装配依赖，未改生产规则或降低原断言。

五类真实Service装配测试按每类最新有效结果57项全绿：ProjectContactClosedWriteBoundary21、ProjectCustomerReadWriteBoundary16、RegionalDurationAccess6、RegionalInitialPmPrerequisite1、RegionalPlanningWorkbenchChain13。前四类见`.run/feedback-20261029-unified-management/server-fixtures.log`；Planning首次执行发现原反射夹具缺执行guard依赖，注入后`.run/feedback-20261029-unified-management/planning-fixture-recheck.log`13项全绿（18:00:06，退出0）。前轮不是整轮通过，不重复运行其他44项。

Contact通过真实统一Owner→已确认成员链；Duration通过真实统一Owner→Regional链；Initial负例核真实组织锁和PM查询；Planning保留原功能、范围、版本、轮次、绑定与Owner保护，补全权限SM同权正例和指定他人通用执行拒绝、独立规划Owner保持的分离场景。真实规划保存核exact WriteRequest；guard拒绝原异常传播且两表无update，未绕执行守卫。

五份差异及最后缺注补正均获独立审核，无生产变更、无剩余发现。before分别位于`.run/server-fixture-unification-before-20261029`及`.run/feedback-20261029-unified-management/before/yudao-server/`。固定clean package已重新完成并冻结，清单及ValidateOnly回执见下段；未部署、未重启。

## 最终冻结清单与运行基线

2026-10-29 18:04，本轮固定构建成功（18:03:53、3分09秒），发布清单：`.run/build/releases/463be0ba-48b9-4a9d-85f9-96b40abf25b6/manifest.json`，同目录不可变`application.jar`为229,909,456字节。

- 产物SHA-256：`8b48e0fb2421dddd16ad380b1002e4662082587b2a502c124732f7e42bbafb17`。
- backend源指纹：`41bf6574effa4ceb246a90d32ac13ce051e075b6c39d08bc1bbb0824f4257416`；固定脚本构建前后匹配，冻结后另行读取当前源再次匹配。
- 固定JDK25.0.1+8、Maven3.8.6、工作树依赖库、共同锁及`local,dac`。执行`scripts/worktree-build.ps1 -Target backend -MavenArguments @('-DskipTests')`，不重复已有效功能测试；全部生产与测试编译均保留。构建和冻结通过22项修复保护。
- 执行`scripts/worktree-backend.ps1 -Manifest <该清单> -ValidateOnly`退出0：不可变包、当前运行包基线、受保护测试库及UMC配置检查通过，没有服务变更。禁用迁移/自动建表的固定配置保持，未连接测试库写数据或运行迁移。
- 另做16项包内检查：统一API及唯一PROJ实现存在、工程/PROJ两份重复helper均不在包内，12个工程生产客户端字节码直接引用统一API。
- 冻结前后59191均PID44368、`local,dac`、健康UP；仍运行旧包SHA`5c607cb451ae75f0a4e98cc110626551bd85323c428a2cbb35595afa9875f161`，与清单runningArtifactSha256一致。此包未部署，未重启59191或其他服务。

完整日志/回执在`.run/feedback-20261029-unified-management/`：`backend-build-final.log`、`backend-precheck.log`、`backend-freeze-receipt.json`、`frozen-package-check.json`、`runtime-before-freeze.json`、`runtime-after-freeze.json`。首次失败的`backend-build.log`及server夹具修正过程保留。

源码/测试与覆盖独立审核均通过；最终冻结清单也已独立通过：review重新计算完整源指纹、实际包大小/SHA，直接读嵌套包17项全过（另确认临时ProjectManagementAccess也不在包），亲自执行固定ValidateOnly退出0并通过22保护；PID44368、local,dac、UP及旧包SHA再次匹配。运行部署须另得用户明确批准，新包真实浏览器验收尚未进行，不能把冻结或ValidateOnly描述为业务验收完成。

## 用户批准后的59191部署回执

用户明确批准冻结清单`463be0ba-48b9-4a9d-85f9-96b40abf25b6`后，先重新核当前完整backend源、不可变包、PID44368/local,dac/旧包SHA及健康UP，均匹配冻结运行基线；固定ValidateOnly与22项保护再次通过。没有漂移，也没有绕过检查。

随后仅执行固定入口`scripts/worktree-backend.ps1 -Manifest <该清单> -ApprovedRestart`，退出0。新进程35092于2026-10-29 18:19:25启动，18:20:39复核健康UP、59191唯一listener35092、local,dac。实际运行包SHA-256为`8b48e0fb2421dddd16ad380b1002e4662082587b2a502c124732f7e42bbafb17`，与批准冻结包一致；当前源仍为`41bf6574effa4ceb246a90d32ac13ce051e075b6c39d08bc1bbb0824f4257416`。对实际运行JAR再次执行22项修复保护，通过。

48182只读前后listener PID34308/8900一致，未操作该服务或其他服务。固定运行入口的禁迁移/自动建表保护保持，没有执行迁移、DDL或直接数据修补。部署成功不等同于项目业务或浏览器验收完成，后续由主线程在正常项目阶段/任务入口复验。

日志与结构化证据：`.run/feedback-20261029-unified-management/approved-deployment-before.json`、`approved-deployment-precheck.log`、`approved-deployment.log`、`approved-deployed-repairs.log`、`approved-deployed-runtime.json`。本段补充原冻结时点之后的批准部署事实，不改写原冻结/失败历史。

## 交付边界

上述修复已依明确批准部署重启59191，尚无本记录的新包真实浏览器业务验收。只读样本84578证明旧请求的权限冲突与真实成员/区域事实，不能代替新包验证。后续冻结清单固定来源和产物，用户另行批准59191重启后方可从真实项目阶段/任务入口复验。不得操作旧批准历史、伪造业务状态、执行迁移或DDL。
