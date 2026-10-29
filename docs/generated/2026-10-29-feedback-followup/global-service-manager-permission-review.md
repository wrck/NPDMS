# 服务经理包含项目经理能力：全局审查索引

运行状态更新（2026-10-29 18:20）：用户已明确批准463be0ba清单部署重启59191；固定脚本部署完成，当前PID35092、local,dac、健康UP、运行包哈希匹配且22保护通过。下文冻结时“未部署”属于原时点证据；最新部署回执见末段，真实浏览器业务验收仍未在本记录中宣称通过。

2026-10-29。此文件汇总本轮已核对入口、修复与证据，作为用户审阅材料；不充当PRD或新的权限规格。依据用户明确裁决：服务经理是区域负责人，管理授权区域内所有项目经理，具备项目经理可执行的管理能力。功能授权、当前租户/区域/项目FULL范围、来源撤权、版本、指定任务执行人、具名审批职责和业务状态机保留。实施方案本人发起后本人真实审批允许；该裁决不覆盖准备项豁免既有申请人回避。

## 统一能力与模块边界

用户进一步要求项目人员管理权限统一一个能力，因此暂停了首次冻结构建（中止退出1，无release、部署或重启）。现由PROJ公开ProjectManagementQualificationApi，统一选择已确认MANAGEMENT成员或真实区域服务经理来源；Fact保存实际来源，写锁只重验已选择来源，不在撤权后重新选一个宽松来源。PROJ原ProjectParticipantFactApi继续提供成员/真实责任事实，RegionalManagerFactApi继续提供区域Owner事实；业务模块不再各自组合这两种资格。

原本轮工程EngineeringManagementAccess已删除。准备项、到货管理确认、方案提交和需求分析管理路径直接依赖统一API；PROJ自身管理守卫也消费同一Owner能力。功能权限和项目action scope由各业务Owner保留先行检查；执行人、审批候选、真实主责PM、通知收件人是独立责任事实，不扩展成“所有管理人均是该责任人”。

## 真实入口与证据映射

| 已核范围 | 实际入口/Owner及本轮处理 | 证据 |
|---|---|---|
| SYSTEM授权与前端 | 当前有效SM菜单包含PM全部有效菜单；前端按钮按权限集合，未发现排除SM的PM专属业务能力。独立工作台隐藏裁定对两者相同；管理员能力不包含在PM能力内 | [SYSTEM/BPM/跨Owner实质审计](system-bpm-and-owner-permission-audit.md)、[当前角色菜单只读快照](service-manager-role-grants-readonly.json) |
| PROJ任务与阶段 | 查询/维护、业务绑定、生命周期、阶段readiness/advance、联系人及正常闭环的二次PM守卫已修正；统一管理资格、范围和来源锁，保留指定执行人及真实managerId。受控动作锁后实际执行资格重验 | 统一后22类328项有效绿；下述日志与分类清单可追溯 |
| 工程SOL/IMP | 准备7服务、实施方案、当前需求分析与旧草稿读取、到货确认；普通管理资格直接调用统一API，管理员真实PM和observer例外保留。到货现场录入权限不收窄，真实PM来源不混为SM身份 | [工程修复/测试/边界](engineering-service-manager-permission-audit.md)、[原84578请求与方案审批](tf031-service-manager-review-authorization.md) |
| BPM | SOL_REVIEW同租户FULL VIEW读取例外包含MANAGEMENT_CODES；历史SM别称受相同限制。真实办理仍要求当前assignee；高级动作限制保持。方案本人审批真实H2人工任务验证 | [独立BPM审计及18项日志](system-bpm-and-owner-permission-audit.md)；方案引擎用例另有60项回执，不冒充生产终态回写 |
| SERVICE巡检 | Controller→InspectionAccessService及在线授权；PM/SERVICE_CODES同管理读取路径；写命令仍核真实owner/本项目SM、版本和归档 | [已核实际消费链](system-bpm-and-owner-permission-audit.md#继续核对实际消费链) |
| CUT割接 | P2/P3/P4 Owner命令与TaskApplicationService范围链，未发现PM可实际办理而SM仅因角色被排除的额外分支；保留真实负责人/当前候选与来源门槛 | 同上；不把具名DP_FIRST_LINE候选当普通管理授权 |
| AST资产 | DeviceController/Query、日志及客户设备摘要→DeviceAccessScopeService；项目/合同/组织范围与设备来源独立 | 同上；未改设备来源、发货及归属 |
| COM商务 | 交付范围命令MANAGE FULL锁及ProjectOfficeFact；PM通知收件人为责任事实。旧交付资格API无生产实现/消费者不列缺陷 | 同上；保留合同/来源范围 |
| ACC验收 | NativeAcceptanceProjectAccess、指定满意度执行人、正常闭环消费PROJ；发现的管理发起差异在PROJ处理 | 同上；不替代签字、验收及批准历史 |
| PLT共享能力 | 通用entity/dynamic-form执行Owner策略；管理员独立配置/导入能力不是PM权限 | 同上；不改通用角色匹配以隐式扩大管理员能力 |

## PROJ统一Owner与最终覆盖

统一Owner内部复用既有授权成员/区域端口。ProjectParticipantFactApiImpl仅在AUTHORIZATION语义下不再误用PRIMARY责任标签；FACT查询与真实主责责任仍保留PRIMARY限定，确认标识、有效区间、租户和删除过滤不变。返回实际assignmentRoleCode，不把协作成员伪装主责。ProjectDurationOperatorApiImpl也委托统一Owner，basis保持真实三种身份：原确认PM、确认管理成员、区域SM；首次工期对非PM来源仍独立锁查真实当前confirmed PM。

统一后每类取最新有效结果，共22类328项通过；不把旧失败轮次写成整轮通过：`.run/management-unification-project-20261029.log`首轮326中7个测试夹具问题，四类在`.run/management-unification-project-recheck-20261029.log`补正107项全绿覆盖（含新增2项）。其余18类原有效绿保留。旧PRIMARY误用的精准失败测试见`.run/management-unification-red-20261029.log`。

- Owner4类45项：ManagementQualificationApiImpl13、ParticipantFactApiImpl12、DurationOperatorApiImpl11、ConfirmedProjectMemberScope9（真实隔离H2成员/范围保护）。
- 管理/展示18类283项：ContactContext10、NormalClosure2、StageAdvance22、StageReadiness15、TaskCommand22、TaskLifecycle50、TaskQuery21、TaskBusinessBindingHost10、TaskNativeBindingHost9、BusinessExecution23、OperationContextResolver8、TaskBusinessRefresh7、TaskBusiness38、TaskBusinessAccess5、TaskProgress6、ControlledOperationExecutor13、TaskAssignment13、TaskMaintenance9。

PROJ独立审核已核最终生产源码和日志，无剩余发现。旧before快照在`.run/management-unification-before-20261029`，只是已备份的消费文件，不宣称全量初始基线；新API在备份前已开始创建，Duration无独立before物理副本，依操作前源码读取记录追溯。没有据此覆盖原历史或执行迁移。

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

## 最终审核、冻结与验收边界

逐项实质审计结合Controller、查询/命令、共享权限与最终Owner，不把角色字符串命中机械计作缺陷。S0真实主责/公司候选、审批候选、具名任务执行人、通知角色及退役无消费者路径均明确区分。

旧批次PROJ161/155/54、工程准备64/需求到货40/保护64、BPM18均为对应时点有效证据；统一后的受影响路径重新测试，最终日志及冻结清单在收到回执后补入，不按相加计无重复总数。工程统一消费首轮191项全绿。管理员精确无资格fallthrough两用例在原路径准确失败；最终Query17与延期文件12全绿，Access新增测试覆盖旧抛异常mock时需doThrow，修正后`access-recheck.log`15项单独复测全绿（17:47:00）。每类取最新结果，工程最终205项有效绿，详细去重规则见工程证据。工期Owner独立资格遗漏已由PROJ统一并绿测；工程文件reader保留历史raw路径、无事实才统一Owner、未知失败与已选raw锁撤权均直接拒绝，其行为红为11中3失败/1错误。

独立review已核全局覆盖、工程205、PROJ328（含Owner45/H2）、BPM18及方案人工自审真实引擎边界，无剩余源码发现。后端固定构建与ValidateOnly均已完成，最终清单见下段。现已依明确批准仅部署重启59191，不声称新包真实浏览器验收通过。当前现成有效服务经理账号均兼系统PM，尚未做纯SM登录隔离验收；源码测试仍直接覆盖SM资格。没有修改角色造样本，没有改写已有项目/批准/流程历史。当前178角色授权快照不是所有会话缓存和所有运行接口的证明。未检查或未执行的场景不承诺通过；退役无消费者代码未恢复、未对178或运行环境执行数据库迁移或DDL。
