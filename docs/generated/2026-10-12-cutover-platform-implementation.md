# 割接专项平台内实现与验证记录

本记录对应需求方批准的《割接模块专项实施计划（V1＋V2，修订版）》，需求依据为 `docs/baseline/prd-v1.8.md` 第十章和已合并的修订007、008、014。按专项实施，不新增 Feature、Task、DU 或工程链门禁。2026-10-12需求方进一步确认：外部接口保留拓展口，继续实现平台内闭环和验证。

截至2026-10-14 13:51，已批准的结构化存储、统一命名和多人保障安排已实施，V474～V477已在指定共享实例执行，75张表、1333列及93处更名有隔离迁移和历史保持证据。A级样例已通过真实页面完成四节点审批、成功归档及两台资产正常回填，当前范围覆盖2/2；当前待办文件可读、办理后临时授权撤销也已验证。下文按时间保留实施过程中的失败和待办，最新结论见文末“恢复后的角色审批与归档验收”。Word已验证生成、绑定和下载发起，操作系统落盘尚未验证；外部系统和真实设备操作不在本次平台样例验收结论内。

## 实现范围

- 新增割接工作台和配置中心，保留原页面和历史入口；原业务行为保持，已授权的参数规范同步调整源接口、原服务和运行扩展。详情为 P2～P6 五步，D 级跳过 P3，已完成阶段允许历史只读查看。
- 装配生产任务、人工评估、清单、方案、分级审批和归档服务。自建及 ITR 不调用 IMP 实施就绪前置；项目事件在创建与执行时核对真实 EXE-06 事实。暂存保存冻结上下文，提交重新执行权限、版本和 Owner 事实校验。
- 复用平台用户、角色、权限、项目范围、文件、采集、BPM、通知、审计及命令幂等能力。按需求方2026-10-13确认，二线与研发使用既有probSupporter/probRD角色，不指定具体人员；不自动分配用户或扩大项目范围。
- 初始化10类割接类型、5类组网模式、97项双机检查（17＋25＋23＋24＋8）、12类调研、24项普通风险及双机整体风险类别。配置示例覆盖精确、部分限定、优先级、无匹配与停用；标准配置发布，示例配置保留草稿。
- 清单支持表格、选择、附件、条件子表和任务级自定义项。平台采集关联真实执行及日志；无日志的终止失败保留原执行身份，人工补录关联原失败，不伪造采集成功。凭证由平台采集体系处理。
- 标准方案支持上传和在线编制，D级采用操作及回退步骤；Word导出包含计划、设备、拓扑/配置文件版本、风险措施和保障安排。联系人消费 CUS 当前项目启用记录，并保存引用及联系信息快照。
- BPM 按 A/B/C/D 路由运行，CUT 保存五项评审、服务经理等级复核及冻结依据。通用 BPM 操作不能绕过 CUT 评审规则；BPM 详情根据原审批实例打开对应冻结内容。新二线/研发节点保存角色引用并使用原生候选组共享待办，由当前有效角色成员办理并记录实际人员，不冻结整组候选人员；当前有效待办只授权对应割接审批页及冻结资料，不扩大普通项目范围。多人不暂停、无有效候选不跳过。旧个人节点及既有不可变历史保持原规则。
- P6 成功和失败均提交即归档。只有成功结果提供项目/资产事实；资产 Owner 刷新当前设备并保留历史范围变化回执，IMP 聚合当前范围全部成功覆盖，PROJ 自行决定推进。增加 WorkBinding 的项目创建入口及独立范围覆盖视图，不改写已发布项目模板和执行快照。
- 首页统计、授权清单导出、导航规则和 A/B 提前时间快照沿用基线。站内通知失败进入重试，不回滚已完成的业务动作。启动时通过平台JobApi注册本专项通知与结果投递任务；已有Quartz触发器的暂停及执行状态保持有效。

## 浏览器暴露问题与修正

创建配置选项按实际 `code`、`name` 字段展示及取值；创建DTO按服务端白名单显式构造项目上下文与设备范围水位，不整体回传响应对象。无CRM客户参考的项目可创建，V460前向迁移放宽对应customer可空约束，项目、设备、权限和版本校验继续生效。

P3设备类型由工程师从任务冻结的CUT DeviceType字典选择，生成时冻结选择，草稿可重新匹配；选择尚未重新匹配时禁止提交。不以AST产品类型映射作为创建门槛。`CutoverCompleted` 已注册为PLT outbox支持事件。

真实Chrome进一步完成D级任务创建、P2草稿重开、P3跳过、P4草稿重开及P5两节点审批至 `APPROVED`。实际文件下载500（浏览器证据16）定位为缺少CUT原生文件来源；P6空白（证据22/23）定位为缺少query/save/submit closure三个权限种子。已修正CUT原生文件来源，并从 `FileDocumentSourceProvider` 源接口统一复用 `FileEvidenceApi.Document` 对象传参，提供方和调用方同步；V461、V462已按需求方确认完成共享迁移与部署。后续证据35～41已完成附件恢复、保存重开、D成功归档、归档后只读及两附件下载和版本历史；标准方案Word下载仍待验收。

浏览器证据30暴露附件竞态：第二附件已在PLT上传但尚未绑定就保存，重开仅一个附件，不代表两附件通过。前端闭环表单复用Uploader的 `isBusy`、`hasPendingFile` 及PLT元数据读取计数，选择、上传和绑定中禁止保存/归档；附件完成事件由父组件从最新content合并，避免覆盖，writing时表单不可编辑。未改PLT共享组件或后端规则，前端修复无需后端重启，50eb运行事实保持。证据31保存禁用已通过，重传引用冲突仍待恢复链路验证；证据32待新API部署后验收。

浏览器证据31确认上传/绑定中保存禁用通过，但正常重传出现引用冲突 `1010001019`，当时不能列为两附件通过。PLT新增只读 `FileCurrentReferenceController`/QueryService，按完整业务键读取既有绑定，复用 `pms:file:query`、租户及READ业务范围、`ExactFileReferenceQuery` 和原 `FileQueryService`；未改旧端点、表或历史。前端“读取已上传附件”仅把原工件版本加载至草稿，保存仍走原SAVE校验，待上传和元数据读取期间继续禁止保存。新API已随批准975f部署；证据36通过正常UI恢复原承诺书V1，保存并重开（证据37）后两份附件均保留，未重传覆盖、直接写绑定或降低冲突校验。

证据33确认PDF真实下载成功（1879 bytes）；证据34确认归档前版本历史显示数字版本1、可用、2KB。`FileVersionReferenceHistory` 复用既有 `dateFormatter`/`formatDate` 修正时间戳展示。证据38通过唯一提交正常归档D样例，任务ARCHIVED/v5、闭环SUBMITTED/v4、SUCCESS；39回首页重开及P2/P4/P5历史均只读，无业务提交或上传入口；40两份归档附件真实下载1879/1916 bytes，41两份版本历史均显示V1、可用及正常日期。NativeWord仍未通过，不将PDF下载代替Word验收。

## 参考V2界面整改（2026-10-13）

需求方指出原实现与 `3、割接平台界面（参考）V2.html` 存在明显偏差后，使用 `frontend-design` 重新核对首页和五步页面。参考HTML只作为字段、结构和视觉依据；真实数据、服务端允许操作、版本关系及正式基线继续生效，不复制演示任务、自动建议等级或多角色分别闭环。

- 首页默认展示四项KPI，并分为“割接任务待办”和“自建割接任务”两张表。两类列表分别服务端分页、筛选，复用CUT查询权限和项目范围；创建人读取持久化creator并经用户公开API解释，项目负责人经PROJ公开查询补充，不把任务owner冒充项目负责人。新增工作台查询的项目越权响应复用任务接口的403业务分类。
- 详情采用圆形五步进度条、浅灰页面及白色分区卡片。P2采用横向问卷单选行；P3按背景、业务调研、风险、双机和技术公告分区表格填写；P5以项目、采集分析、等级、方案和五项评审组织；P6采用八项纵向内容、成功/失败单选及一个结束流程按钮。D级跳过P3并保留历史只读，使用平台导航及公共文件组件。
- 标准方案补齐设备型号、在网版本、升级版本、备注、回退说明，并将操作项目、测试结果、步骤、业务影响时间、实施人员和操作时间拆为对应表格列。补充字段在CUT方案快照中保存，设备字段必须引用冻结设备范围；原设备身份和步骤正文保持。表格补充字段按章节与步骤序号关联，重开、派生修订、审批冻结和Word导出共同消费，不改写AST数据、已批准版本或历史。
- 方案附件选择、上传及元数据绑定期间禁止保存、生成或提交，写入时禁止编辑；生成方案先保存当前草稿，避免下载遗漏未保存内容。Word表格保留多行操作内容。详情加载失败显示错误和刷新入口并禁止办理；列表失败清空旧结果，防止误用旧页。

真实Chrome已取得D级P2、P4、P5、P6整改页面，最终截图位于 `.run/build/cutover-v2-alignment/`：`d-step1-final.png`、`d-step5-top-final.png`、`d-step5-bottom-final.png`、`d-step5-1024-final.png`。1440与1024宽度的document/body宽度均等于视窗，无全局横向溢出，返回、刷新、保存及结束流程入口可见。这轮截图仅导航、读取和布局验收，未保存、恢复附件或归档；标准流程及首页新查询尚需部署后真实浏览器验收。

新增表格字段的首次行为验证因旧步骤白名单拒绝details而出现预期 `invalid step keys`；早一次fixture缺少supportArrangements的失败不作为业务RED。修正后5类22项定向验证通过；最后补齐设备字段后，仅重跑受影响2类8项，通过；Word多行最终2项通过，与前述重复，不相加为独立总数。专用库 `npdms_cutover_verify_20261012_v2` 的A/B/C/D四流程均通过，跨Owner输入仍为受控事实，不等于真实浏览器完整验收。测试包装按已批准恢复的容器默认库核验环境，同时明确连接专用库，并保留测试类的专用库硬断言；未操作Compose、默认数据库或服务。

最终前端Vite production独立构建通过，输出 `.run/build/cutover-ui-v2-tables-dist`。vue-tsc仍退出2，本轮割接工作台、审批表单、文件引用API及文件历史组件无诊断，其他模块33条诊断未处理；沿用仍适用的36集合图标校验。日志为 `cutover-plan-table-green.log`、`cutover-plan-table-fields-green.log`、`cutover-plan-table-word-final.log`、`cutover-plan-table-flow.log`、`cutover-ui-v2-tables-build.log`、`cutover-ui-v2-tables-types.log`。

本轮使用官方构建脚本完成42个reactor模块，耗时2分22秒；22项既有修复基线通过，并按官方 `worktree-backend.ps1 -ValidateOnly` 验证冻结和 `local,dac` 配置，退出0。冻结清单为 [975f465c清单](E:/AICoding/Projects/NPDMS/.run/build/releases/975f465c-39d4-49c5-b278-9b0ead469d2f/manifest.json)，日志 `cutover-v2-tables-package.log`、`cutover-v2-tables-release-validation.log`。构建使用 `-DskipTests`，不作为测试执行。需求方回复“继续”确认后，按官方脚本部署975f完成，日志 `cutover-v2-tables-deploy-validation.log`、`cutover-v2-tables-deploy-approved.log`；59191为PID38184、创建于2026-10-13 11:31:40、`local,dac`，实际运行jar与975f SHA一致（5ed3e4…）。DAC48182仍由PID20696监听，未操作。

部署后首页证据35确认四项KPI和两张独立列表，两个工作台查询均200/code0。列表仅一行，不能据此声称跨页翻页已验收。证据42通过SN正常解析现有授权项目1001及设备1001，43创建C标准样例，44确认P2保存回开一致，45进入P3；背景明确标识平台验收样例、不连接或操作设备。标准方案、审批与Owner正常刷新继续验收。

资产回执另暴露实际缺陷：D任务2109718697440329728的项目992203060202、设备2109644845483008001、归属版本0及SN与当前AST完全相同，但消费读取的旧设备日志锁查询未投影project_assignment_version，DO该值为null，误记HISTORICAL_SCOPE_CHANGED。已取得只读数据库及SQL来源证据；不把此回执当作真实归属变化或正常资产刷新通过，不覆盖该已归档任务与既有资产历史。新增资产结果专用锁查询完整读取当前归属版本，旧设备日志查询保持；真实隔离MySQL修正前1 FAIL、修正后4项及既有单元4项通过。源码修复尚未部署，真实正常刷新仍待新割接结果验证。

浏览器证据42～46发现标准C级清单生成0项：新Runtime匹配上下文把人工等级写为MANUAL_GRADE，而已发布配置规则使用CUTOVER_LEVEL。修复只调整新Runtime的上下文字段；公开generate入口修正前1 FAIL，匹配4项及种子2项修正后通过。原项目1001样例保留，不覆盖空清单；证据47～48在原已授权项目992203060202创建独立C级样例2109856829645156352，P2保存重开正确，停在P3/v2，尚未生成清单。

## 角色共享审批修正（2026-10-13）

需求方先指定probSupporter/probRD，再确认“定义角色不指定具体人员，审批按角色进行”，并明确“按角色覆盖本租户全部割接审批”。新二线/研发节点冻结初始角色成员来源，一个角色对应一个共享待办，不要求普通项目VIEW范围；办理前不指定人员，决定后记录实际办理人及当前资格。当前角色资格不再有效的成员不能办理，新增成员不能直接使用旧冻结池；普通登录改变版本水位时，核验并锁定本次当前资格，保留初始候选来源。越权尝试不得写入全局HOLD或评审记录。角色节点不再支持个人改派，通知按有效冻结候选分别生成幂等收件记录。

BPM初始化发布新的角色任务定义，旧流程版本与实例保留；原生候选任务只能在CUT完成业务校验后同事务领取和决定。实际Flowable角色池、空候选不跳过、通用操作拦截及原个人审批3类16项通过；CUT资格、决定及通知3类25项通过，失败/错误/跳过均0。前端审批记录新增角色和实际人员展示，Vite production构建39.32秒通过，专项类型无诊断，其他模块既有类型诊断未修复。

专用MySQL四流程回归首次执行2 PASS、2 ERROR：原审批人CHECK只接受两个人员字段同时为空或同时为正数，拒绝角色节点original为空而current记录实际办理人。V463前向迁移对ROLE+对应二线/研发角色定义新约束，决定后必须有实际办理人；原个人约束及其他决定/历史保护保持。V463在原专用库的首次迁移因MySQL CHECK表达式类型3812失败，已保留失败历史、原约束和日志；表达式修正后改用新专用npdms_cutover_verify_20261013_v3继续验证，不repair或覆盖原失败记录，不向共享库执行迁移。

需求方随后明确“角色审批有待办任务时只能拥有当前审批项目的查看权限”，并确认只读割接审批页及冻结资料。临时读取资格仅在审批根和当前角色节点均PENDING、无HOLD、未被替换、操作者在冻结候选且当前角色仍有效时成立；未来节点、已办理节点、关闭或替换实例不提供此资格。具备其他既有项目权限的用户继续按原权限访问，不新增完整项目详情授权。首页仍以普通项目范围或精确待办任务ID限定，角色待办统一走approvalOnly页面；审批成功后关闭临时视图，不再请求已失去资格的详情，失败和冲突继续刷新核验。

文件读取复用现有PLT查询、下载和预览。CUT先核验当前待办，再用现有FileEvidenceApi按冻结工件、文件版本、SHA和完整业务引用键校验；不新建文件选择DTO、下载通道或生成文件缓存。PLT只对READ/DOWNLOAD/PREVIEW取消当前范围版本与引用登记范围版本必须相等的限制；当前Owner授权、功能权限、精确引用、固定文件版本、可用性和票据事务内重新校验保持。上传、引用写入、提交和归档的原范围版本校验继续生效。审批实例ID仅用于CUT读取授权的事务内复核，不改写文件登记范围和历史事实。在线方案Word沿用原生成及下载功能，当前审批附件授权只覆盖实际冻结的方案内容和清单文件引用。

V463为既有两角色补齐BPM待办入口、审批读取/办理和必要文件查询权限，已移除普通CUT工作台入口及query权限；V464补充下载/预览权限。最终集合为10项菜单/权限、两角色共20项幂等映射，不新增上传或引用写权限，不分配用户或修改role.data_scope、PROJ项目成员。最新只读预检匹配385份已执行来源，共享库为V462.1，恰好待执行V463/V464，Flyway validate退出0；共享迁移未执行。新菜单集合已在原专用v3库的独立临时验证表执行并重复验证，共享角色映射及迁移历史前后不变；原隔离库已执行迁移和失败证据均保留。日志cutover-role-current-menu-seed、cutover-role-minimal-migration-preview-final。

新租户角色及首页查询9类55项通过；后续冻结文件与历史读取3类15项通过（与55项有重复，不相加）。真实专用MySQL A/B/C/D四流程通过，含三种首页查询精确角色任务ID、双空范围、普通项目范围及非法节点更新3819保护。Vite最终生产构建40.61秒通过；专项类型无诊断，全仓36条其他模块诊断未修复。日志cutover-tenant-role-green、cutover-tenant-file-green、cutover-tenant-role-sql、cutover-tenant-role-ui-final-build及cutover-tenant-role-ui-types。

以上为此前阶段证据。最新最小方案中，PLT范围变化读取失败用例修正前出现3项预期失败，修正后20项FileArtifactApi和1项票据测试通过；CUT当前待办读取、冻结文件、查询及路由4类35项通过，原真实隔离MySQL A/B/C/D四流程重新验证通过。日志cutover-file-current-scope-red/green、cutover-role-todo-route-red、cutover-role-current-todo-green及cutover-role-current-todo-sql。前端最终Vite生产构建46.59秒通过，输出cutover-role-pending-ui-dist。全仓vue-tsc两次因内存失败，不能记作通过；3个修改页面及其依赖的定向类型检查完成，报告9处其他文件诊断，本次3个页面无诊断，整体检查仍为失败；日志cutover-role-pending-focused-types。未扩展修复其他页面。

最终官方backend构建于2026-10-13 16:54:16完成，42个reactor模块成功，22项既有修复基线通过，构建前后源码一致；构建使用-DskipTests，不计作测试执行。冻结清单为[a35dff1b清单](E:/AICoding/Projects/NPDMS/.run/build/releases/a35dff1b-ec30-4dcf-86bd-63f293b439da/manifest.json)，日志cutover-role-minimal-final-package。官方worktree-backend.ps1 -ValidateOnly核验最新运行包、冻结源码和local,dac配置通过，未改变服务；日志cutover-role-minimal-final-release-validation。V463/V464共享迁移与59191重启待需求方明确批准，DAC48182不在此次启停范围。批准后需重新检查来源及运行一致性，再完成角色审批和标准流程真实浏览器验收。

## 接口与方法参数规范

接口与方法优先复用已有业务实体、Command、Query、返回对象和受信上下文。明确的业务主键、稳定唯一键查询及有意义的业务重载保留；不设机械参数个数阈值，不为减少参数数量另造万能参数袋。

原 `CutoverTaskQueryService` 本体已调整，Runtime 保持继承并复用 `page`、`detail`，仅覆盖必要差异。`ProjectBusinessContextApi` 的 Query、Revalidation、Context 使用具名构造，提供方及跨模块调用同步调整。PLT采集授权与来源查询、IMP到货事实、CUT文件查询与审批及历史结果也从源头同步调整。

租户和权限来自服务端；HTTP字段、锁顺序、幂等摘要、状态与历史保护未因参数规范化改变。

## 数据库和运行入口

割接基础迁移为 **V449～V458**，客户参考可空修正使用前向迁移 **V460**。共享实例已有另一会话执行的 V439，故将本专项迁移版本整体后移；未修改共享库迁移历史。稳定种子 ID 和创建标识保留。

使用现有 Compose MySQL 的独立数据库 `npdms_cutover_verify_20261012_v2` 验证，从当前共享库复制表结构及字典、菜单、角色元数据；不复制用户、凭证和业务数据。隔离验证中的本专项迁移与受控测试均在该独立数据库执行，测试数据使用专用高段租户标识。

共享库 `npdms_domain_test` 已只读确认 V449～V458 全部为 `Success`，最新 Flyway 来源校验通过，已执行来源内容与校验和一致。既有V294合并与V295～V297删除标记按已批准历史解释；V331及V411～V439中保留在外部工作目录的已执行原件仅暂存用于比对，不恢复到主迁移链，本会话未对共享库重复执行 migrate 或 repair。暂存目录只包含已执行历史及本次V449～V458，不夹带其他未执行迁移。

V460已在专用验证库 `npdms_cutover_verify_20261012_v2` 完成 migrate、validate 和重复 migrate；共享库 `npdms_domain_test` 经需求方确认授权已应用V460，V449～V458此前已成功。

本次验证库迁移调用遗漏 `--no-deps`，导致共享MySQL容器在00:52:35重建，`MYSQL_DATABASE` 变为验证库。原volume `npdms-domain-test_npdms-mysql-data` 仍在，两个库均可查询；当时只读检查确认共享库 `cut_task` 为0且V460未应用。这些检查不代表完整数据一致性验证，恢复随后经需求方确认授权执行；后续测试未执行Compose；额外只读迁移校验使用Compose `--no-deps`，未启停基础设施，校验前后MySQL容器ID和启动时间未变。

本轮共享库只读Flyway校验确认381份有效历史来源一致，385条总记录验证成功；暂存目录 `cutover-live-v460-validation` 仅包含历史来源和V460，日志为该目录的 `validate.log`。只读校验全程使用 `--no-deps`，该只读校验本身不执行迁移；批准后的实际迁移仅应用V460，共享库版本从459到460，validate验证385条记录通过，history中V460为1条Success。迁移采用重新核验的381份历史来源与V460专用暂存目录，并使用 `--no-deps`；迁移前后基础容器ID和StartedAt未变。日志 `cutover-v460-live-migrate.log`、`cutover-v460-live-validate.log`。

恢复 `MYSQL_DATABASE` 配置已使用Compose `--dry-run up -d --no-deps mysql` 预演，确认仅由验证库改回 `npdms_domain_test`，镜像、命令、24306端口及原卷保持。预演前后容器ID与StartedAt一致。需求方确认后已实际恢复，日志 `cutover-mysql-restore-approved.log`：当前MySQL容器ID为 `db0029710d0c3166ddf14b8ffde2f653b64d1f22023aa2003d962301ae12d5e0`，StartedAt为 `2026-10-12T18:35:49.438645874Z`（本地10月13日02:35:49），`MYSQL_DATABASE=npdms_domain_test`、healthy，24306及原named volume保持，两个库存在已只读确认。

V461、V462只读迁移准备使用独立脚本 `prepare-cutover-live-document-sources.py` 与目录 `cutover-live-document-source-validation`；严格确认 `MYSQL_DATABASE=npdms_domain_test`，沿用历史CRC来源校验。实际匹配382份历史来源，仅额外暂存 `V461__cutover_native_document_sources.sql` 和 `V462__cutover_closure_permission_seed.sql`，`sources.json` 记录 `requestedMigrations` 数组。Compose `run --rm --no-deps migrate validate` 退出0，验证387条迁移记录；未执行migrate/repair或启停基础设施。前后MySQL及Redis的ID、StartedAt一致，记录见 `infrastructure-snapshot.json`，验证日志为该目录的 `validate.log`。

本轮V461/V462已在隔离验证库实际完成migrate、validate与重复migrate，日志为 `cutover-document-source-migration-validation` 下0/1/2步骤。六类CUT来源及已有disabled设置保持，三项closure权限种子补齐且原角色分配不变；MySQL/Redis的ID和启动时间未变。共享库V461/V462随后按需求方确认完成迁移，版本460→462，恰好2项迁移Success，validate387条通过；MySQL/Redis的ID与启动时间未变。

浏览器证据24确认重叠设备创建返回409，原D级任务保持不变。创建接口仅配置 `silentError:true`，向导对 `OPEN_EXISTING_TASK` 显示“设备已有活动割接任务，请返回工作台继续办理已有任务。”；其他响应显示服务端msg，未知结果提示同键重试，项目Operation路径保持原异常。未修改服务端规则。证据25已在真实Chrome重新提交相同设备及计划时间，仍返回原409业务分类，并实际显示上述中文提示；取消后重开原D任务仍为P6、版本4，未新增任务。

共享迁移脚本 `apply-cutover-document-source-migrations.py` 经确认实际执行V461/V462，版本460→462，恰好2项Success，validate387条通过；日志 `cutover-document-sources-live-migrate.log`、`cutover-document-sources-live-validate.log`。只读核验共享库六类CUT目录均enabled=1、tenant=1，三个closure权限ID970462001～970462003均status=0、parent=970445001，基础容器ID与StartedAt未变。

前端入口为 `/pms/cutover/workbench`、`/pms/cutover/configuration`；项目覆盖视图为 `/pms/cutover/project-coverage`。部署后由具备割接配置发布及 BPM 部署权限的管理员，在配置中心初始化固定审批流程。平台采集命令示例均为草稿，须由采集管理员按实际设备确认并发布。

后端构建、冻结和部署使用 `scripts/worktree-build.ps1`、`scripts/worktree-backend.ps1`，保持 59191 的 `local,dac` 配置。DAC 48182 独立启停。

## 已取得的验证证据

| 验证 | 实际结果与边界 |
| --- | --- |
| 隔离 MySQL 迁移 | 独立验证库 V449～V458迁移、校验、重复 migrate 均通过；共享库当前 V449～V458 全部为 `Success`，本会话未重复 migrate 或 repair |
| A/B/C/D数据库流程 | 在真实 MySQL、真实 CUT Mapper/事务/命令审计下完成创建、评估、清单或跳过、方案、各级审批、成功/失败归档、重复提交；跨 Owner 输入采用受控事实，BPM另行验证 |
| 配置匹配与种子 | 97项分类数量、调研/风险基准及组合匹配定向验证通过 |
| BPM | 使用真实 Flowable 引擎验证分级路由、业务提交保护和篡改流程拒绝；不等同于线上待办验收 |
| CUT生产装配和调度 | 实际CUT组件扫描、事务代理、各阶段控制器及路由注册通过；缺失调度注册、既有触发器状态保护、Quartz停用检查通过，共4项。仅隔离Mapper和跨模块公开API，不等同于全应用启动 |
| 权限、失败和历史 | 定向验证独立入口不调用IMP、未就绪拒绝、候选转交重新校验、联系人身份/版本、采集失败证据与重放、通知失败重试、归档历史读取、成功范围覆盖和Word内容 |
| 前端生产构建 | 本轮Iconify离线36集合校验通过，Vite生产构建55.55秒通过，独立输出 `.run/build/cutover-ui-browser-fixes-dist`；日志 `cutover-ui-browser-fixes-build.log` |
| 前端类型检查 | 本轮vue-tsc全仓检查报告32条其他模块诊断，本专项 `cutover/workbench` 无诊断；日志 `cutover-ui-browser-fixes-types.log` |
| 参数规范回归 | 当前源码相关定向验证已分批通过 BPM 5项、PLT 20项、PROJ 4项、IMP 39项、CUT 90项，共158项；证据来自分批日志，不是一次全仓测试。最终辅助方法收敛后另跑7类41项，失败、错误、跳过均为0；包括真实隔离MySQL A/B/C/D四条流程及两版审批决策。与前述存在重复覆盖，不相加为独立总数 |
| 本轮浏览器缺陷修正回归 | 10类49项通过，失败、错误、跳过均为0，含真实专用MySQL A/B/C/D四条流程；与原158项和41项存在重复覆盖，不相加为独立总数。日志 `cutover-browser-fixes-tests.log` |
| Outbox事件注册 | `CutoverCompleted` 新用例修正前1 ERROR，修正后整类11 PASS，包含未注册事件拒绝的失败保护用例；日志 `cutover-outbox-registration-red.log`、`cutover-outbox-registration-green.log`，不与其他回归项重复相加 |
| 已上传附件恢复前端验证 | Vite prod独立输出 `cutover-ui-attachment-recovery-dist`，40.34秒通过；vue-tsc全仓仍为32条其他模块诊断，workbench及本轮文件引用API/历史组件无诊断。沿用icons36结果，日志 `cutover-ui-attachment-recovery-build.log`、`cutover-ui-attachment-recovery-types.log` |
| 闭环附件竞态修正前端验证 | Vite prod独立输出 `cutover-ui-closure-upload-dist`，35.76秒通过；沿用仍适用的icons36通过结果。vue-tsc全仓32条其他模块诊断，`cutover/workbench` 无诊断；日志 `cutover-ui-closure-upload-build.log`、`cutover-ui-closure-upload-types.log` |
| 原生来源修正前端验证 | 本轮icons36集合校验通过，Vite prod独立输出 `cutover-ui-document-sources-dist`，36.45秒通过；vue-tsc全仓32条其他模块诊断，`cutover/workbench` 无诊断。日志 `cutover-ui-document-sources-build.log`、`cutover-ui-document-sources-types.log` |
| 原生文件来源回归 | CUT修正前来源发现用例1 FAIL（期望唯一来源，实际0）；修正后CUT7项、peer53项通过，失败、错误、跳过均为0。日志 `cutover-document-source-red.log`、`cutover-document-source-green.log`、`cutover-document-source-peers.log`。仓库缺少 `CompletionConfirmationGeneratedFilePolicyTest`，该测试未执行，不列为通过；Asset完成编译 |
| 50eb历史冻结 | 官方42个reactor模块构建成功，22项既有修复基线通过，官方 `-ValidateOnly` 退出0；[50eb冻结清单](E:/AICoding/Projects/NPDMS/.run/build/releases/50eb9120-cfcf-48c3-8eb0-7eb1a7e53c7f/manifest.json)。日志 `cutover-document-source-package.log`、`cutover-document-source-release-validation.log`。使用 `-DskipTests`，不作为测试执行；此前已按批准部署 |
| 上一轮已部署冻结 | 官方42个reactor模块构建成功，22项既有修复基线通过，官方 `-ValidateOnly` 退出0；[已部署清单](E:/AICoding/Projects/NPDMS/.run/build/releases/1dfb4401-c719-459e-8377-74cb77e0ebdb/manifest.json)。日志 `cutover-browser-fixes-package.log`、`cutover-browser-fixes-release-validation.log`；构建使用 `-DskipTests`，不作为测试执行；后端已按批准清单部署 |
| 本会话此前部署事实 | 975f曾按批准部署，PID38184创建于2026-10-13 11:31:40，local,dac、59191，日志cutover-v2-tables-deploy-approved。随后其他会话已更新运行实例；最新只读核验PID47284创建于16:17:54、同工作树jar及local,dac，不能再将975f视为当前运行产物。本轮尚未部署，DAC未操作，不宣称解析链路验收通过 |
| 真实浏览器阶段验收 | 975f部署后证据35：首页四KPI和两类列表正常；36～37原承诺书V1恢复、两附件保存重开；38唯一提交D成功归档；39首页重开及P2/P4/P5历史只读；40两附件实际下载1879/1916 bytes；41两版本历史V1、可用及日期正确。42～46标准清单0项暴露匹配缺陷，47～48新C样例P2保存重开。标准清单/方案Word/标准归档、角色审批及正常资产刷新仍待验收 |

定向日志位于 `.run/build/`，包括 `cutover-flow-mysql-tests.log`、`cutover-owner-tests.log`、`cutover-final-kernel-tests.log`、`cutover-final-flow-tests.log`、`cutover-production-assembly-tests.log`、`cutover-ui-build-final.log`、`cutover-source-object-contract-regression.log`、`cutover-source-object-contract-regression-resume.log`、`cutover-source-object-contract-regression-cut.log`、`cutover-backend-package-object-final.log`；本轮另见 `cutover-browser-fixes-tests.log`、`cutover-v460-*`、`cutover-ui-browser-fixes-*.log` 和 `cutover-outbox-registration-red.log`、`cutover-outbox-registration-green.log`。共享库此前只读校验为 `cutover-live-migration-validation/validate.log`，本轮为 `cutover-live-v460-validation/validate.log`；最终冻结与校验日志为 `cutover-browser-fixes-package.log`、`cutover-browser-fixes-release-validation.log`。浏览器初步检查证据为 `cutover-browser-acceptance/02-workbench-after-deployment.png` 与 `cutover-browser-acceptance/02-workbench-after-deployment.dom.txt`。

## 尚未完成的运行验收

2026-10-13需求方批准V463/V464共享迁移及59191部署后，环境预检发现其他会话已执行V465并更新运行包。确认源码仍与批准的a35dff1b一致后，复用相同产物重新冻结为e36cc243，保留V465历史；只对463/464使用Flyway outOfOrder执行。实际迁移、validate、重复migrate通过，新增20项角色菜单映射，仅清理10个对应权限派生缓存键，角色分配、范围、成员及MySQL/Redis容器前后不变。官方部署完成，59191为PID45080、17:03:21创建、local,dac，DAC20696/48182未操作。日志cutover-role-approved-shared-migration-final、cutover-role-approved-runtime-refreeze及cutover-role-approved-deploy。

真实Chrome首次加载暴露新阻塞：角色待办JSON_TABLE查询在TenantLineInnerInterceptor的SQL解析器内超时，首页统计和待办列表返回500。新增运行层租户解析测试修正前3 ERROR（TimeoutException）；只替换候选成员判定为JSON_CONTAINS/JSON_OBJECT后3 PASS，且n/i/t三个表的租户条件均保留。真实隔离MySQL四流程重新通过，补充旧项目角色候选的同对象allowed真假匹配，原快照在测试finally恢复。修正后42模块构建、22项修复基线及官方ValidateOnly均通过，新清单为fd0067fb-2a8a-426e-9b22-04bcc8bbc6e7，日志cutover-role-tenant-parser-red/green/package/release-validation、cutover-role-current-todo-sql。此修正尚未部署，需求方正在审阅完整查询SQL，尚未批准再次重启。

需求方指定二线c02407、研发b00106，并在原登录失败后明确授权重置两个账号。已从真实浏览器用户管理页面分别完成密码重置，两账号均通过真实Chrome登录并进入BPM待办页，未变更角色与数据范围。当前均无待办；二线账号尝试读取原D级已关闭审批实例时实际409/APPROVAL_NOT_PENDING。角色当前待办正向、冻结附件下载、未来节点及办理后撤销读取仍待上述修正部署后验收。准备中的项目992203060200样例创建向导停在填写任务，尚未创建新任务；原C、D任务未改。

当前记录不宣称平台内业务验收已完成。共享库已执行V463/V464并保留V465，e36cc243已按批准部署；SQL解析修正fd0067fb尚待再次重启批准。参考V2首页与D流程此前已完成附件恢复、成功归档、历史只读及真实附件下载。当前待办临时只读授权、角色审批正向及冻结附件、标准C清单/表格保存冻结/NativeWord下载/归档及正常资产刷新仍待新修正部署后的真实浏览器验收。已有项目模板不会被自动重写，应通过新模板修订显式绑定新的创建与范围覆盖契约。

按需求方最新指示，ITR外部入/出向传输、失败建单、备件回填、外部数据源、INT-10短信/邮件和INT-05钉钉保留公开适配端口，不以模拟响应或预留接口宣称真实对接完成。平台内部 ITR 来源命令及幂等规则仍在本次实现范围。

## 2026-10-13 结构化存储与角色审批后续实施

需求方已确认[完整表结构方案](../superpowers/specs/2026-10-13-cutover-structured-storage.md)，并要求“表字段的名称请统一规范，然后开始实施”。按ADR-0019先统一字段命名，再切换配置、P1～P6、备件及割接专用IMP/AST事实的结构化存储；统一主数据、统一结果根和既有修订保持，固定答案按业务结构拆分，真正自定义值保留明确JSON例外。

前述fd0067fb仅是旧候选快照查询修复证据，不再作为本轮拟部署产物；新角色审批使用真实角色引用及BPM原生候选组，当前有效成员只读当前待办所冻结的资料。历史部署和测试记录保留，不代表本轮新结构已验证。

当前实施顺序：统一字段及旧名映射；前向DDL和逐记录投影；分业务替换固定JSON读写及查询；接通配置/填写/审批页面；完成失败、历史、真实MySQL和浏览器验证后形成新的冻结产物。当前未执行本轮共享迁移或重启；阶段结果将在本节按实际证据补充。

2026-10-14阶段记录：P5已改用精确结果引用、冻结展示列、六项提前时间事实及原生角色组引用，14类89项定向测试通过（`cutover-structured-approval-tests.log`）。P6冻结设备范围、三个文件水位、采集回执及AST逐设备结果/文件均改为具名列和关系，7类26项通过（`cutover-structured-closure-tests.log`）。备件查询保留既有适用清单项来源语义，未伪造检查结果来源；3类9项通过（`cutover-structured-spare-tests.log`）。

外部加载已补齐受控设备版本、查询与实际参数、成功空结果、失败留痕、人工替代引用和公告反馈入口。外部调用在事务外执行；返回后复验负责人、范围及版本，失配时仅保留历史结果。反馈行引用首次原始公告，四个原值不重复写入反馈版本。新增13项失败及边界用例、既有5项清单回归共18项通过（`cutover-external-tests.log`）；这些是定向单元及Spring事务边界验证，不是实际外部服务联调。

配置的字段/选项、外部映射和绑定条件已改为结构化表单，P3界面已接入逐设备版本和外部加载/反馈状态。前端定向vue-tsc无本次CUT文件诊断，依赖链仍报告14条其他模块既有诊断（`cutover-external-ui-types.log`）。正式外部Provider仍未接通；界面与日志必须显示真实未接通状态。历史JSON投影、最终字段切换、隔离MySQL迁移、冻结构建以及重新部署后的真实浏览器验收尚未完成。本轮没有执行共享库写入或服务重启。

2026-10-14后续验证：V474/V475/V476已在从指定共享实例只读提取的全新隔离库`npdms_cut_structured_20261014_r6`执行成功，原28张表646条记录的全部原列值、JSON、状态、版本、时间逐一保持。目标结构为75张表1330列，93处规范更名及原约束的等价转换均已实际执行。保留早期失败库和日志，未覆写其迁移历史；共享`npdms_domain_test`尚未执行本轮迁移。

数据库保护验证先捕获两项失败：草稿子记录可改挂到已发布配置，以及合法清单失效被冻结约束误拦。修正父记录更新前后校验和既有失效字段白名单后，16项检查全部通过，覆盖旧JSON禁写/不可清空、发布/提交历史、结果与自定义值、角色身份、P6不可变及正常失效；尝试均在隔离库事务内回滚。记录见`.run/build/cutover-structured-migration-r6/guard-verification.json`和`preservation-verification.json`。

实际MySQL A/B/C/D四流程已于02:01全部通过，包含标准/简易路线、角色共享审批、精确首页范围、非法节点修改拒绝、P6成功/失败归档及结果API读取（`cutover-structured-mysql-flow.log`）。使用真实结构化Store/Mapper及受控跨模块Owner事实，不代表外部联调或真实浏览器验收。测试夹具补齐登录审计、实际乐观锁插件和匹配维度；非法操作断言分别核验原CHECK与新增不可变触发器，仍比较拒绝前后原行完全一致。

外部公告组件7项挂载交互测试通过，覆盖成功零条显式保存、失败保护、原始列只读、反馈更新、版本过期与刷新确认。P3刷新保留尚未提交的设备类型选择、普通答案及其他项反馈；提交重匹配后采用新冻结事实。SDS 08/09/10已引用正式结构方案和本次已批准的存储/API边界。最终冻结构建及经批准部署后的真实浏览器验收仍待完成。

最终部署前产物：真实CUT生产组件扫描与路由装配检查通过（`cutover-structured-assembly.log`）；前端production构建43.92秒完成（`cutover-structured-ui-build.log`）。官方后端构建于02:09完成42个reactor模块，耗时3分38秒，22项既有修复基线通过，构建前后源码一致；本次打包使用`-DskipTests`，不计作测试执行。冻结产物为[7e8232e2清单](E:/AICoding/Projects/NPDMS/.run/build/releases/7e8232e2-14a4-43bc-bd09-c86d506b3c60/manifest.json)，官方`worktree-backend.ps1 -ValidateOnly`确认冻结包、当前运行包及`local,dac`一致，退出0，未改变服务。

共享库只读预检确认仍与r6验证的28张表646条历史记录逐值一致，本次待执行恰为V474/V475/V476；无PENDING割接审批或在办CUT BPM任务。只暂存本次三份迁移，按Compose既有missing-source策略保留已执行历史，未做repair；预检不代表全仓其他历史SQL重新校验。日志及结果在`.run/build/cutover-structured-live-migration/`。本次部署协调将先核验冻结清单和来源，持工作树锁停止旧59191，备份并执行三段迁移、比对历史原值，再由官方入口以冻结清单启动新`local,dac`；DAC48182不在启停范围。实际共享迁移、重启和真实浏览器验收等待本次明确批准。

2026-10-14 10:03需求方回复“继续”，批准上述V474～V476迁移、59191部署及后续浏览器验收。冻结产物和22项修复基线再次核验通过，但部署在停止59191前被基础设施预检阻断：原MySQL容器报告退出，24306未监听；Docker数据盘`M:/DockerDisk/DockerDesktopWSL/disk/docker_data.vhdx`的挂载包含`emergency_ro`，内核持续报告`I/O error, dev sdd`，容器自身报告`read-only file system`。M盘只剩约0.54 GiB；尚不能仅凭空间不足断定I/O故障根因。Compose原实例`start mysql`退出0，但重新检查原容器仍未恢复，不能把命令退出码视为恢复成功。59191仍为原PID23916；本轮未执行共享迁移、后端重启或浏览器验收，也未操作DAC。证据见`.run/build/cutover-structured-approved-deploy.log`与`.run/build/cutover-structured-live-migration/storage-blocker.json`。已请求额外确认Docker Desktop重启，因为其影响范围包含运行中的其他容器；存储恢复后须重新核对原实例、端口、迁移历史及原值，不能直接沿用此前只读预检结论。

### 2026-10-14 恢复部署与真实页面复验

需求方随后明确批准Docker Desktop重启并继续。恢复后确认MySQL/Redis原容器、卷和端口未变，文件系统恢复可写；单独保存经批准的启动身份记录。V474～V476已通过官方Compose迁移到`npdms_domain_test`，迁移前备份保留，28张表646条原记录逐值比对通过，既有Flyway历史未改写（`.run/build/cutover-structured-live-migration/approved-result.json`）。此前“共享库尚未迁移”的阶段记录至此被后续执行结果取代。

第一次启动暴露真实PLT文件策略装配循环。生产装配回归重现后，仅在CUT文件端口注入处采用延迟依赖；21项相关回归通过。官方构建增加显式离线恢复选项，4项验证覆盖未授权离线冻结、端口占用、正常离线及运行包固定，未降低运行包核对或22项修复基线。冻结f3b9c668产物启动成功；后续方案请求修复经新一轮明确批准后，e7810f1c于11:15由官方入口部署为PID30052，保持`local,dac`。DAC原PID20696保持独立运行。

真实浏览器已验证迁移后的D级历史方案、两个人工审批节点及P6归档/文件仍只读可见。C级及A级分别生成43项DPX普通双机清单，通过实际控件填写34项直接答案、9项人工材料，刷新后恢复，再提交进入P4；空清单提交被业务校验拒绝。材料与回答均明确标注平台验收样例，没有执行真实设备命令或宣称外部服务已接通。对应证据位于`.run/build/cutover-structured-browser-acceptance/`。

标准方案页面发现并修复`rollbackDescription`请求白名单遗漏（先失败后通过），以及前端丢弃未完整保障行；18项后端回归和1项挂载回归通过。初次“部分草稿重开”的页面观察存在刷新竞争，后续以实际响应和落库纠正：未完整保障行仍被旧数据库CHECK拒绝，不能算保存成功。项目202既有客户记录缺电话，按需求方选择转用联系人资料及服务经理均完整的既有项目201。A级任务`2110208707121836032`的完整标准方案在真实浏览器保存成功，服务端`code=0`、版本2，三份方案附件已绑定；但Word生成被内容类型校验拒绝，未进入后续审批。

### 2026-10-14 保障人员多人明细与Word修复

需求方明确要求角色下拉、人员可新增删除，并确认沿用客户/DP一线/DP二线/DP研发四种角色、同角色允许多人。前后端已按明细ID保存、保留行顺序及逐行客户引用；草稿保留未填完整内容，提交仍检查四类保障覆盖及每行必填项/客户Owner事实，批准后仅允许原有逐行联系信息变更。相关Feature规则、API契约和完整字段清单已同步。

V477在既有隔离验证库执行成功，新增逐行客户引用ID/版本与顺序三列，解除角色唯一约束，草稿到位时间允许为空；既有36条保障、13条方案所有原字段逐值保持，最初28表646条历史记录再次验证不变。目标仍为75表，列数1333。真实MySQL A/B/C/D四流程通过，A级增加了同角色多人、未完整草稿保存/读取、缺项提交拒绝、角色修改、增删行、外来明细ID拒绝及事务回滚、提交后顺序冻结的验证（`cutover-support-mysql-flow.log`）。25项后端定向验证及3项前端挂载验证通过；前端production构建通过，当前方案范围vue-tsc没有CUT诊断，但依赖链存在6项其他模块诊断，未宣称全项目类型检查通过。

Word失败通过运行中的异常栈定位到PLT媒体类型检测，非文档正文损坏。按[Tika 3.3.1容器识别文档](https://tika.apache.org/3.3.1/detection.html)，增加同版本Microsoft容器检测模块并使用`TikaInputStream`读取已限长内容，保持声明类型、文件名与真实内容一致校验。正常POI生成DOCX先失败后通过，改名ZIP、改名Excel、截断容器继续拒绝，原PDF/大小/摘要/扫描失败用例保持；10项通过，真实ClamAV环境用例未启用而跳过1项（`cutover-word-media-red.log`、`cutover-word-media-green.log`）。

共享库V477目前仅完成只读预检：待执行恰为该迁移，涉及已有4条保障、3条方案、0条联系人变更，不新增业务记录；尚未应用。当前后端仍为e7810f1c，V477及本轮后端修复的新冻结产物需在明确批准的部署窗口应用，再验证页面多人保存/重开、Word下载和A级角色审批/P6正常资产回写。不能把隔离验证或构建成功写成这些浏览器环节已通过。

本轮官方后端42模块构建于11:59完成，耗时2分56秒，22项既有修复基线及构建前后源码一致性通过；冻结产物为[a23ccd06清单](E:/AICoding/Projects/NPDMS/.run/build/releases/a23ccd06-4401-4c5b-b519-2c11ee67e442/manifest.json)。官方`-ValidateOnly`通过，当前运行包仍精确匹配e7810f1c。部署协调已准备并通过语法检查；在收到本次V477及59191重启确认前不执行。

### 2026-10-14 多人配置真实验收与角色成员查询修复

需求方于12:10明确批准V477和59191部署。共享`npdms_domain_test`已执行V477，原4条保障、3条方案及既有Flyway记录原值逐一保持；备份和结果见`.run/build/cutover-support-live-migration/`。a23ccd06经官方入口启动为PID63880，配置`local,dac`，DAC原PID20696保持运行。

真实页面已完成新增同角色人员、部分字段留空保存、刷新重开、切换角色、删除保存和再刷新。服务端方案版本4保留5行及新增行空字段，版本5保留两名研发，版本6删除该行回到4人；最终版本7为5人（两名二线）。审批前未完整草稿不提供提交操作；提交后5行全部只读，新增/删除入口消失，姓名、顺序及逐行客户引用均保留。证据为浏览器目录14～17及21～22号文件。

“生成并下载方案”已通过真实按钮得到`code=0`及`割接方案-v1.docx`文件事实，PLT确认可读/可下载并签发下载票据；初稿在页面绑定为V1。此处已验证生成、绑定和下载发起，未把操作系统下载目录中落盘文件视为已验证（证据18号文件）。

首次提交被旧BPM定义拒绝后，通过割接配置中心正式初始化入口发布角色定义V3；旧V1/V2定义仍保留，原历史实例未改写。随后A级方案成功进入P5，发起人五项评审通过。服务经理向二线推进时返回系统异常，事务回滚，仍停留服务经理节点；不能记为审批闭环已完成。

异常栈定位到SYSTEM角色候选查询：已有导入成员关系的创建/更新时间均为空，`GREATEST`产生空水位，CUT适配时拆箱失败。按[MySQL空值语义](https://dev.mysql.com/doc/refman/8.4/en/comparison-operators.html#function_greatest)，查询使用已有成员更新时间、创建时间或角色更新时间计算水位，不回填历史、不跳过当前成员及停用校验；同时补齐候选列表中成员关系本身的租户限制。实际Mapper XML在隔离MySQL连接临时表中先复现3项失败，修复后3项集成检查及6项Owner API检查全部通过，覆盖旧空时间、锁查询、时间变更、撤销、停用/删除及跨租户成员拒绝（`cutover-role-watermark-red.log`、`cutover-role-watermark-green.log`）。这些用例不写入既有业务表。

官方42模块构建、22项修复基线及冻结清单核验通过后，需求方再次明确批准59191重启。[54e3976d清单](E:/AICoding/Projects/NPDMS/.run/build/releases/54e3976d-6944-4ecf-ac2a-0ffd46f0fa99/manifest.json)于12:28部署为PID64888；没有执行新迁移。随后真实浏览器服务经理通过返回`code=0`，当前节点成功进入二线。二线账户c02407在原生BPM待办看到唯一对应任务，并打开当前割接冻结页：方案5行均只读，审批接口返回`APPROVE/REJECT`，12份冻结文件均返回`READ/DOWNLOAD/PREVIEW`。证据见浏览器目录26～28号文件。此前研发未来节点只能读取平台流程元数据，CUT冻结业务页返回409/`APPROVAL_NOT_PENDING`，不能表述为所有BPM元数据均不可见。

该浏览器检查发现旧前端全局菜单权限指令隐藏了服务端已允许的角色审批、预览和下载按钮。仅移除新割接审批表单及新冻结版本文件组件的这四处重复判断，继续由`allowedActions`控制展示、由服务端现有授权和文件票据复验执行。旧割接页面及原文件组件未改。挂载回归先复现两项失败，修复后4项通过，覆盖无全局菜单缓存权限的角色办理、无动作时隐藏、文件票据拒绝不导航及只读文件不显示下载（`cutover-role-actions-red.log`、`cutover-role-actions-green.log`）。

继续浏览器验收时发现运行环境已离线：Docker Linux引擎管道不存在，19191/59191/48182均无监听，先前应用进程已不存在。未擅自启动或再次迁移；已请求恢复Docker原实例、19191前端和同一54e3976d后端的批准。尚未实际完成二线/研发决定、决定后的材料访问回收及本次A级P6正常资产回写；4项前端挂载验证不能替代这些浏览器验收。

### 2026-10-14 恢复后的角色审批与归档验收

13:40需求方明确批准恢复上述环境并继续验收。Docker恢复后，原MySQL/Redis容器ID、数据卷和24306/24379端口均核对一致，V474～V477执行历史均成功；未再次执行迁移。59191由官方入口使用同一54e3976d冻结包启动为PID11212，保持`local,dac`，19191前端恢复为PID64056。DAC48182未启动，本次验收没有执行真实设备采集。运行记录为`cutover-environment-approved-recovery.log`、`cutover-environment-recovered-identity.json`、`cutover-environment-approved-backend-start.log`及`cutover-environment-frontend.log`。

真实浏览器中的二线当前待办显示五项评审和冻结文件操作。PDF预览实际打开并显示验收样例内容，下载票据返回`code=0`且浏览器收到文件HTTP 200；未把浏览器未返回下载事件视为操作系统落盘成功。二线c02407提交后当前节点进入研发。保留在办理前打开的旧页面再次下载同一冻结文件，返回`1010001002`／无权访问该业务文件范围；重开割接审批数据返回409／`APPROVAL_NOT_PENDING`，临时文件和业务页读取资格均已收回。原生BPM流程元数据及审批历史仍按平台原有权限展示。证据为浏览器目录29～33号文件。

研发b00106通过真实登录进入原生BPM待办，仅看到对应研发任务。打开当前节点返回`code=0`、节点4和`APPROVE/REJECT`，五行保障安排只读。提交五项合理性评审及反馈后，重开原生流程确认“审批通过”，实际办理人为白梅、时间13:48；已办CUT冻结页返回409／`APPROVAL_NOT_PENDING`。管理员工作台随即进入P6。研发提交响应未在该次网络采集中保留，不将其写为已采集的`code=0`；终态由重开流程返回的`status=2`、历史记录及P6阶段共同确认。证据为34～37号文件。

A级任务`2110208707121836032`的P6真实验证包括：空缺必需附件时提交被阻止且保留草稿；填写检查、执行、测试、无回退及样例说明；上传并绑定两份V1材料；保存后重开，各项值及文件引用保持。最终提交返回`code=0`，任务`ARCHIVED`、闭环`SUBMITTED`，稳定结果为`CUTOVER_CLOSURE:2110246447804784640:3`。项目201两台设备`NATIVE-UI-20261012-DEVICE-02/03`均返回`REFRESHED`，当前要求2台、已覆盖2台、缺失0台。归档后重开P6及审批历史，业务输入全部只读，无保存、提交或审批入口，四个节点均保留实际人员、五项评审、反馈和时间。证据为38～44号文件，其中41号保存提交及Owner回执，42号保存归档重开，43号保存审批历史，44号为结果页面截图。

本次平台内主流程、角色临时读取回收和正常资产回填已完成真实页面验收。C级既有未完整草稿保留，未补造客户联系方式；历史D级只读结果保持。最新前端四处权限展示修复由4项挂载测试及本轮真实角色页面验证，未因文档更新重复构建或测试。仍保留的验证边界为：Word文件生成、绑定和下载发起已验证，但操作系统下载落盘未验证；外部ITR、公告Provider、短信/邮件、钉钉及真实设备操作未联调；全仓vue-tsc存在前文记录的其他模块诊断。所有业务样例均不代表真实割接、客户签署或外部送达。代码未提交、未推送。
