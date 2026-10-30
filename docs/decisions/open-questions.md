# Open Questions

用于记录 PRD 无法支持、互相冲突或必须由业务/架构负责人确认的问题。

## Q-NORMAL-CLOSURE-SATISFACTION-REVALIDATION-20261025 — 结项回调与采集待办受派边界

- Status: RESOLVED（2026-10-25需求方明确采用受控内部重验；实施及浏览器复验状态见F-PROJ-001，不代表已结项）
- Area: 项目44637的正常闭环申请2114146990853451777；满意度结果已有签字、达标及归档事实，材料审核人不是采集待办受派人。
- Evidence: 服务经理审核已完成；材料审核通过触发SatisfactionTaskBusinessObjectProvider.lockAndRevalidate中的queries.get，报SATISFACTION_TASK_NOT_FOUND并回滚。只读inspect已按项目可见范围提供结果，采集工作队列get及既有锁定命令测试明确只允许受派人。不能为完成验收直接删除这项限制。
- Proposed decision: 保留采集待办查询/写入权限及通用锁定命令限制，仅为当前合格结项审批实例提供服务端内部重验，绑定租户、项目、冻结快照、关联对象及结果版本；验证原审批身份与项目结项上下文，不向审核人增加采集待办权限，不修改受派人或历史。
- Decision: 需求方确认“采用受控内部重验，保留满意度采集待办的受派人限制，不增加权限、不改历史”。通过既有公开跨模块Provider契约新增内部结项专用入口，ACC核验原申请/快照及BPM双人工审批证据，PROJ保留关联锁与结果版本/完整摘要检查；原通用入口不放宽。契约归`docs/design/10-api-design.md`正常闭环节。
- Evidence path: `.run/project-16200721122903N-20261025/59-closure-material-failure-rollback.json`；申请IN_REVIEW，工程材料任务END_TIME为空。

## Q-PROJECT-JOINT-DELIVERY-SOURCE-20261025 — 联调文件交付件的自动归集来源

- Status: RESOLVED（2026-10-25按需求方要求从联调任务重新上传日志，既有归集规则和文件有效性校验通过，无需新增业务规则）
- Area: 项目16200721122903N-0（178测试库ID44637）的D_JOINT联调交付件；不改冻结门禁、历史附件或既有批准版本。
- Evidence: 初查时联调记录通过、任务待验收99%，交付件30为FILE且历史为空；仅据automaticSource=null判断来源缺失并不充分。后续审计ProjectDeliverableRuleService确认冻结PAGE联调任务可通过originTaskId限定AST.DEVICE_CONFIGURATION_LOG来源。真实浏览器在业务联调任务的“上传日志 / 文件历史”重新上传同一合成日志，归档为v2，交付件自动归集99020573D222000007-v2.txt。点击重新检测并刷新后，交付件已验收、联调任务完成100%，项目10/16。
- Decision: 复用既有任务来源日志归集，不新增联调附件映射，不以状态通过替代有效文件，不改冻结门禁。此次追加v2，保留v1及原联调、审批历史。
- Decision owner: 需求方要求“再上传一次日志”；已按既有浏览器业务入口验证。
- Evidence path: `.run/project-16200721122903N-20261025/`；浏览器交付件30判定响应及联调记录见本轮验收证据。

## Q-CUT-BPM-ACTION-EXTENSION-20261020 — 原生 BPM 操作栏与割接办理接入边界

- Status: RESOLVED（2026-10-20需求方确认不得修改 BPM 模块功能，可以修改流程定义文件）
- Area: 恢复 BPM 通用操作栏；割接通过/驳回仍须完整五项评审，人员调整走 CUT 改派，其他流程保持原有办理方式。
- Evidence: `BpmProcessInstanceDetail` 仅通过 `formCustomViewPath` 嵌入业务表单；`ProcessInstanceOperationButton` 的 `handleAudit/handleTransfer/handleDelegate` 直接调用原 BPM API，没有业务操作委托回调。CUT 现有服务端守卫阻止通用接口绕过五项评审及改派授权，不得为接入而移除守卫或扩大 `bpm:task:update` 权限。
- Decision: 不增加 BPM 业务操作扩展点，不修改通用办理组件、接口或授权。使用现有 BPMN `buttonsSetting` 为割接四个节点关闭原生通过、驳回、转办、委派、加签和退回；通过/驳回继续使用割接五项评审，人员调整复用 CUT 改派表单及接口。原生操作栏恢复原实现，评论、抄送继续遵循原 BPM 权限及行为；其他流程不受割接定义配置影响。
- Current action: 前次整栏隐藏及临时按钮代理修改均已撤回，通用 BPM 两个页面文件无内容差异。割接定义文件和 CUT 改派入口已调整，未移除服务端守卫或扩大权限。经需求方明确批准，通过原生模型编辑页导入并发布v4，新实例 `2112495949790027776` 已绑定v4；浏览器验证改派及恢复、发起人、服务经理和技术支持五项评审提交成功，待办列表仅剩研发审核，无残留二线待办。Flowable定向14项通过；后续人员自动回填和改派表单调整后的相关前端78项通过。旧节点和评审内容保持不变；被替代旧实例仅由正常重提链追加替代实例指针并递增版本，不覆盖旧决定。

## Q-PROJECT-RESTART-RESULT-20261020 — 已结项项目新轮次的原对象重新办理证据

- Status: RESOLVED（2026-10-20需求方确认原字段保留供参考，必须重新办理确认；可独立建单的业务新建记录）
- Area: 六个指定存量项目回到S0并匹配最新发布模板；不包含需求方已暂缓的16200722010404N。
- Confirmed: 新一轮不得沿用旧完成结果；原业务、审批、结项历史保留；五套目标模板已获准发布。
- Question: 联系人、人员指派等复用同一项目对象的业务，是否保留原字段供参考、要求重新办理并确认后才产生本轮完成证据；可独立建单业务采用新记录？仅任意字段更新或项目版本递增不能自行解释为重新完成。
- Evidence: 当前六项目的生命周期均为NORMAL_CLOSED，但五项目仍各有1个ACTIVE阶段轮次及22个PENDING轮次；另一项目有10个DONE、2个ACTIVE和11个PENDING轮次。`ProjectReworkPlanner`只接收已结束节点，`ProjectReworkService`只接收ACTIVE项目；现有异常重开只接收EXCEPTION_CLOSED。`ProjectTaskBusinessAssociationService`排除旧轮次已关联对象，但这并未定义原项目对象重新办理的完成证据，也不能覆盖目标新绑定首次发现的旧对象。
- Decision: 同一项目对象保留原字段供参考，必须重新办理并确认后才计入本轮完成；可独立建单的业务新建记录；历史审批、结项记录只读保留。不得仅因任意字段变化或版本递增就认定本轮已完成。
- Scope decision: 2026-10-20需求方确认仅对六个指定项目执行一次，并进一步明确禁止项目白名单、固定模板映射、日期标记等功能硬编码。六项目范围由本次执行输入控制；复用现有受控重开、快照和计划能力，不新增日常重置页面或独立机制。保留旧执行、审批、结项与交付历史，并阻断旧成果自动计入新轮。
- Correction（2026-10-20后续需求方指令）: “重新办理并确认”不得解释为新增独立人工核对步骤。移除人员指派、安装的额外确认按钮、接口及凭证门槛；按当前执行轮次使用既有 Owner 事实和原完成校验，不继承旧任务状态，保留旧成果及历史隔离。上述 Decision 中的确认口径以本条纠正为准。
- Correction（2026-10-22需求方确认）: 整体重开与选择性返工共用业务对象关联和完成判定，不强制所有业务结果绑定新轮次。冻结绑定的 `REFERENCE_EXISTING` / `READ_ONLY_AGGREGATE` 允许本轮重新引用既有对象；这是对象解析策略，不替代结果证据策略。完成仍读取 Owner 当前事实及冻结规则；配置了结果订阅的节点继续执行 `REUSE_EXISTING` / `NEW_RESULT` / `PINNED_RESULT` 和有效性策略，其中新结果使用可信形成基线，指定结果不能自动替换。移除按整体重开时间统一筛选及人员指派/安装专用例外，不继承旧任务完成状态，不修改旧业务、审批、轮次证据和已发布配置。没有声明对象复用的创建类绑定保留既有旧关联隔离。此口径替代此前将“可独立建单”一概解释为必须新建的处理。
- Current action: 六项目新轮次已执行；2026-10-22按上述更正统一关联与完成判定并修复培训事实读取，随后经批准部署`05a0b906`，在项目`992203060191`通过真实浏览器受控刷新验证培训自动完成及历史保留。未直接写任务或生命周期状态、未覆盖既有历史，详见`docs/generated/2026-10-22-rework-result-policy.md`。

## Q-INS-AUTHORITATIVE-PRODUCT-DATA-20261015 — 巡检适配示例与验收的权威产品输入

- Status: OPEN（正式权威源数据待提供；2026-10-15用户已明确批准创建模拟记录，模拟业务链验收继续）
- Requirement IDs: EQP-01、INS-03、INS-09
- Area: 本次巡检专项的任务创建/保存重开、产品适配/不适配、发布/停用和完整业务验收样例。
- Question: 请提供可使用的CRM或其他权威产品类型清单及设备映射来源，或已有受控导入文件的位置。
- Evidence: F-AST-002规定巡检只消费AST受控产品类型契约；2026-10-15本次部署后只读核验 `24306/npdms_domain_test` 的 `ast_product_type`、`ast_product_type_source_mapping`、`ast_device_current_product_type` 总数及未删除数均为0。`InspectionTaskService.create`在创建事务内即核验并冻结设备产品类型，因此该缺口也阻断建单成功及后续保存重开，不仅影响执行。用户已询问“受控产品类型是什么”，主任务已解释其为权威类型编码及名称的受控副本，尚未收到来源输入；不能以设备型号、旧字典或 `conpType` 自行替代。
- Update: 用户随后明确答复“创建模拟记录进行验收”。已通过AST正式设备维护及产品类型受控导入接口新增两台`ACCEPT-INS-20261015-A/B`设备、两个`MOCK_INS_20261015_A/B`类型及各自映射，来源系统为`INSPECTION_MOCK_ACCEPTANCE`；当时三表各2条，仅绑定这两台新设备。读回设备、来源、有效映射及两条成功审计已核对，见`.run/build/inspection-deployment/mock-data-db-check.json`。用户进一步要求复用割接模拟项目和设备后，为既有`NATIVE-UI-20261012-DEVICE-02/03`通过同一Owner接口新增`MOCK_INS_CUT_REUSE_20261015`类型及两条映射；当前类型/来源/映射总数为3/3/4，既有项目、成员、设备和CUT记录核对不变，见`.run/build/inspection-deployment/cut-reuse-after-check.json`。这些是获准模拟来源，不是CRM或正式生产产品清单。
- Current action: V477.7十类未发布示例保持原样；另创建仅适配上述模拟类型的明确标识规则和合成离线日志，按正式校验、安全审核、发布及任务流程开展模拟数据验收。不得把合成日志称为真实设备采集、把模拟判定称为DPtech生产规则。原真实SQL验证仍是事务回滚的技术fixture。缺少的《DPtech设备巡检判定信息表》仍是正式生产规则包的来源缺口，不阻断已授权的模拟验收。
- Decision owner: 需求方/权威产品资料提供方。

### Q-BASE-P18-HISTORICAL-COMPANY-SCOPE-20261022 — 历史承接不要求操作者当前公司范围

- Status: RESOLVED / USER_RULE_CONFIRMED（2026-10-22）。
- 用户明确批准：“P18是历史数据承接，允许取消当前操作者公司范围的前置校验，保留同步执行权限、租户隔离、原公司＋合同号精确匹配及历史保护。先原样承接合同号，再统一回填引用，缺配不阻断；继续真实执行和重放”。本条仅适用于新`BASE_P18`，取代先前依历史方案沿用的当前公司范围前置；不修改原任务、原Owner API或当前合同访问规则，不授予账号任何公司范围。
- 后续用户明确：“原表字段都一一对应了，不是可以直接查询批量插入，最后关联新合同表回填合同ID”。据此新P18移除逐项目身份/MANAGE解析，直接使用原项目ID和来源查询已带出的公司编码；不更改公共API或当前项目业务入口的授权规则。保留正式登录、同步执行/合同关联权限、租户与原公司/合同号精确匹配、原文/审计/来源及独立绑定历史保护。一次后填仅使用本次已验证关系ID及其原公司分组，原公司NULL或正式公司不符不猜配，关系照原文保存NULL引用并提示。本次无新增DDL、Flyway或其他迁移，P22/P33实际写入及B稿继续暂缓，59191不操作。运行结果以新包真实执行和重放凭据为准。

### Q-BASE-P30-FORMAL-SN-IDENTITY-20261022 — 新P30首次非空输入的大小写SN及初始化快照

- Status: RESOLVED / USER_RULE_CONFIRMED（2026-10-22；新P30实现及非空真实验证继续）。
- Fresh evidence: 原发运4,283,326条、逐字节SN3,499,488个；正式SN排序规则对应3,486,084个身份。13,402组仅大小写不同，涉及33,154原行，额外逐字节键13,404个。新P30逐字节归组与正式列IN匹配不一致；既有0行结果不能作为非空设备验收。正式ast_device.sn为utf8mb4_unicode_ci，唯一键为tenant_id＋sn。
- Question: 是否按正式SN唯一键归组，使用最新发货时间、并列时最大正式发运ID对应的原SN及快照初始化一台设备，保留全部原始发运和原SN？既有P33裁决只处理已存在设备的原请求键匹配，不自动决定新设备主档的SN原文及快照。
- User decision: 用户明确选择“按正式SN键归组，取最新发运原文及快照继续（建议）”。确认仅新P30按正式SN唯一键归组，用最新发货时间、并列时最大正式发运ID对应的原SN及快照初始化一台设备；全部原始发运及SN不改写，不改既存设备SN。新BASE_P30容量由100万调整为500万，正式CHECK已支持，无DDL。
- Implementation boundary: 仅改独立新P30的归组/匹配及必要检查；复用一次来源查询携带的最新正式发运快照，少量产品目录仅查一次。最新发运属于其他来源、既存人工/已删除设备、独立绑定或指纹被改写仍拒绝；不修改原任务或AST约束。
- Evidence and boundary: [完整来源数量、原始记录、SQL、正式唯一键及方案](../generated/dppms-24-tasks-three-implementations-20261019/P30-formal-sn-key-audit-20261022.md)。本次来源、正式表只读；P15既有真实事务继续，P22/P33实际写入及B稿继续暂缓，不执行迁移，不操作59191。

## 状态

- OPEN
- BLOCKED_BY_SPEC
- RESOLVED
- REJECTED

### Q-DPPMS-VALID-PROJECT-SCOPE-20261015 — 只迁有效项目，排除历史无效项目

- Status: RESOLVED（范围、SQL与停用配置已落实；未执行迁移）
- User direction: “项目数据只保留有效项目，不把历史无用项目进行迁移”；“查询旧系统的相关sql，这些证据在第一次整理时就已经要求”。
- Source evidence: 旧Struts `create_tmp_tb_project` 的活动SQL使用 `ISNULL(ph.effectiveTo)`，旧 `invalid_project_header` 只写失效时间；SpringMVC列表固定 `disabled=false` 并落到Mapper的 `ph.disabled = ...`，合并转出写 `disabled=true`，失效入口同时写停用与失效时间。[原调用链、SQL片段及派生查询](../generated/2026-10-13-dppms-migration-mapping/production-tasks/project-scope.md)。
- Applied scope: 结合两代旧实现的失效表示，采用 `disabled=0 AND effectiveTo IS NULL`。沿用Q011“同类型同编码先取最大projectId”，再判断规范行有效性，不能先过滤再取最大值。旧ID自身也须有效且属于入选规范组；项目归属明细及各任务按项目限定的来源复用此集合，排除无效项目数据，不自动回挂到同码项目。projectState不作有效性过滤，已闭环而未失效/停用的项目仍在范围内。
- Scope of revision: P16同一次查询及事务处理主档与入选旧身份；P17旧记录保留历史并禁止新运行。按追加的同源UNION及聚合复用规则，37份来源组件收敛为27项，21份INT配置已保存到npdms_reverify_20261014且全部停用；6项沿已有材料/Owner入口，完整INT入口缺口保留。共享基础资料、可空正式引用、Owner和历史/授权保护保持。
- Verification boundary: 10月14日80,170条规范项目快照按新规则分类为79,429条有效、741条排除，仍为旧快照数量。32份新SQL已在MySQL5.7只读EXPLAIN核对，P16抽样一次读取主档及身份；Java/Python定向验证通过。新库配置保存前后155张业务及迁移记录表行数一致，没有发起预览/迁移或重算预计新增量；原库、现存项目与历史回执保持，原暂停决定继续有效。

### Q-LEGACY-PROJECT-ACTUAL-CREATION-SCOPE-20261013-026 — 本轮项目实际创建范围错误及后续处置

- Status: RESTORED / REPLAY_AND_BROWSER_VERIFIED（2026-10-14）
- Latest verification: 既有任务实际运行2110076997709062146恢复6,570个原ID，重放2110078518484000770全部UNCHANGED；85列逐条核对，除恢复标记、并发版本及已批准空名称补编码外均保持。身份任务2110083602026467330恢复6,577个原绑定，重放2110084859474612226及项目全字段保护通过。合同关系另获授权后，运行2110212031082381313按原ID恢复6,462条正式关系及6,470条来源绑定，重放2110212969792782338全部UNCHANGED；原业务、有效区间、审计及历史回执保持，6,408条ACTIVE和54条HISTORICAL分别按原状态恢复。普通项目详情的两类合同关系均已打开、刷新验证。
- Latest decision: 需求方明确批准“核对后恢复并复用原 ID”，随后单独批准恢复这批合同关系及原绑定。项目层仅覆盖先前撤回的6,570个项目；关系层仅覆盖6,462条原关系及6,470条原绑定，分别经过原任务及正式Owner核对来源、回执、撤回前值和当前全字段。原记录、审计、树身份、来源与旧回执保留，后续修改阻断，不另建同码项目或关系。本条早先“尚未撤回”的描述是当时记录；已核实38d2撤回回执与本任务当前库一致。没有授权创建其余七万余项目或写入其余关联层。
- Remaining creation inventory（2026-10-14续办）: 既有主档完整预览2110085474984529922的80,170行、48列与当前来源逐值一致；6,590个现存主档绑定均有有效目标，其余73,580个来源确无主档绑定，未执行新增。29个空名称沿已批准编码回退，28个原空办事处沿既有NULL裁决；完整待新增清单及来源复核为私有证据`resume-master-pending-inventory-20261014.json`。本项范围授权不能由其他基础资料补齐授权代替。
- Latest remaining-scope decision（2026-10-14收尾）: 用户明确选择“暂不新增，保留完整预览结果”。73,580个待新增项目维持只读预览，不创建主档、树结构或来源绑定，也不扩写其合同关系、实施范围及其他明细层。本批已授权恢复及其重放验证结果保留。
- Decision: 2026-10-13需求方明确指出“项目详情没有批准迁入为什么已经创建出来了空项目”。停止新增项目及依赖该扩大范围的迁入；字段映射、组织资格和整表读取规则的批准不作为本批实际创建范围的批准。
- Evidence: 本轮任务2108608417343934465/v5执行2109731721299230722实际CREATED6570，运行开始于2026-10-13 03:43；重放UNCHANGED6570。只读核查确认全部无成员、真实/租赁产品明细、阶段、任务和当前计划；其中3720个办事处为空。6385个项目已有6462条本轮新增合同关系，另有各6570条树版本和自身路径。[完整新增ID和现存引用](../generated/2026-10-13-dppms-project-creation-scope-audit.json)，全部实际主档列及旧前值冻结于其指向的仓库外证据。
- Cause: 续办将已批准的整表映射规则与“完成迁移同步”扩大理解为实际项目创建授权，分步执行时先落主档，详情尚未形成业务闭环。这是执行范围判断错误，不能归因于来源项目没有明细。
- Question: 对这6570个本轮新增项目及其本轮新增关联，是保留并停止新增，还是另行批准经领域Owner受控撤销？保留原10个项目、其他旧项目、来源证据、原审计事实及既有运行回执。当前尚未执行删除或撤销。
- Blocking scope: 新增项目、补写其项目详情及依赖扩大项目身份范围的实际迁入；其他已明确批准且不依赖本批新项目的工作不因本项取消其既有批准。
- Decision owner: 需求方与PROJ/COM数据Owner。

### Q-LEGACY-ORDER-LINE-MISSING-TARGETS-20261013-025 — 旧订单行绑定目标缺失及来源冲突

- Status: OLD_01_BINDINGS_CLEANED / OTHER_CONFLICTS_OPEN
- Latest decision and verification: 需求方已明确原01迁移记录按既有裁决清理；运行2109969877659475970仅删除236,259条无目标旧绑定，001等有效绑定及正式业务保持。该批不是待恢复业务，不再列作信息缺失。2026-10-14用服订单全量预览2110095270865473537完整读取467,235条；本范围剩余订单行问题为8条同版本字段差异（4个02正式行，依赖3个来源有冲突的父订单）及3条SO078894父订单多义，均保留，未正式写入。下文为清理前核查记录。
- Evidence: 全源380605条订单行中，236259条已有同步绑定指向指定任务库中不存在的目标。现有绑定仍保存原目标ID与最后回执；无法据此判断清理原因、原目标后续数量或历史事实。另40条原记录有不同内容的同业务键冲突、1579条没有唯一当前父订单；3条父订单碰撞已受Q023约束。9条已有目标的来源时钟不同，保留较新正式事实，不绕过正常版本规则。所有原16字段、独立来源指纹、原绑定及精确父订单/订单行匹配结果已冻结，见[完整核查索引](../generated/2026-10-13-dppms-order-line-gaps.json)。
- Question: 对236259条旧绑定目标缺失，是否暂缓其恢复并保留全部证据，或由原数据Owner提供可核实的清理原因、恢复范围及历史保护依据？不能因目标缺失直接重建、改绑、复活，不能把本轮整表迁移授权解释为丢失目标历史的恢复裁决。40条不同来源内容和1579条父订单缺失也需可核实的选取或关联依据。
- Blocking scope: 仅这些未获明确恢复/归属裁决的来源记录；50962条归属和前值完整的已有订单行、91753条没有旧绑定且父订单唯一的新来源记录继续使用现有COM保护和同步任务推进。没有原单位的记录继续保持NULL及PENDING_AUTHORITY，不制造有效交付数量。
- Dependency refinement: 运行预演进一步发现其中8条已有来源和10条新来源的父订单公司引用未就绪，涉及7个Q023已有表头冲突目标；完整字段及父订单前值另存order-lines-company-dependencies.json、order-lines-new-company-dependencies.json。保留这18条而不降低引用校验，实际独立执行范围为50954条已有行和91743条新来源；不重复请求Q023已提出的裁决。
- Decision owner: 需求方与原数据Owner。

### Q-LEGACY-MISSING-FORMAL-MASTERS-20261013-024 — 整表扩围后的合同及产品主档缺口

- Status: OPEN / BLOCKED_BY_SPEC
- Latest decision（2026-10-19补充，独立新套件）: 需求方明确“逻辑相同，合同客户根据订单类型选取对应最大订单号的所属客户”。在已批准正向订单优先的基础上，仅有退货订单（orderType=1）的合同同样取该类型最大订单号所属客户；不限定为两个合同特例。新BASE_P15预览和正式输入查询均按公司编码＋合同号处理，客户编码及名称取自同一选定订单，保留同类型最大号内的客户冲突校验。原A/B任务、历史及实际写入前置保持。
- Earlier decision（2026-10-19本轮首次裁决）: 同合同多个客户编码以正向销售订单（orderType=0）中订单号最大的一笔为准，并确认“最大”指订单号而非金额；此实现范围仅新虚基类套件。首次真实预览Run 2112158780395544577已完整读取4625378条原始历史，389组冲突消歧387组，剩余51020140121A、51020131018A没有正向订单；该次FAILED回执保留。[首次只读来源核查](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-maximum-positive-order-20261019/P15-customer-selection-source-audit.json)。
- Latest source verification（2026-10-19补充裁决后）: 91565条原订单归并81522个合同；80302组以最大正向订单号选客，1220组仅有退货订单、以最大退货订单号选客。原389组客户冲突全部消歧，未排除合同或修改来源。[按类型选客的完整只读核查](D:/CodexTaskArtifacts/project-detail-followup/base-sync-p15-order-type-customer-20261019/P15-customer-selection-source-audit.json)。本项A/B历史来源及正式主档补齐仍保留原边界，新套件真实预览终态另见本轮报告。
- Latest independent preview（2026-10-19）: 新BASE_P15的Run 2112161183148769281完整读取4706900条，PREVIEW_READY，合同客户sourceIssues=0。客户归属不再阻断新套件预览；实际写入正式输入尚未验证，未执行真实同步，原A/B任务及旧回执未重跑或改写。[本轮验证及隔离证据](../generated/dppms-24-tasks-three-implementations-20261019/base-sync-p15-order-type-customer-20261019.md)。
- Preview verification（2026-10-15，新验证库）: npdms_reverify_20261014 的P15 v6 / Run 2110493133556776962为PREVIEW_READY，完整读取3,848,169条。CONTRACT的80,517条完整来源证据中814条为CONTRACT_CUSTOMER_CONFLICT、79,703条无该源问题；保留明细的814个不同来源身份覆盖全部814 ISSUE。四份合同已批准特例参数保持，其他来源/客户冲突仍待裁决；本轮未正式迁入。见[修复预览报告](../generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.md)。
- Latest decision（2026-10-14）: 需求方授权信息缺失时调用对应业务来源的既有迁移任务补齐基础资料。产品目录按原4列及已裁决的正式目录/实体扩展映射先预览，再补齐合法缺项；未知来源或同键异值仍阻断，不将此授权解释成任意选择合同客户或伪造合同资料。
- Latest verification（2026-10-14）: 原目录任务2110070397648699394已正式补齐26,558个产品，8,418个来源编码UNCHANGED；重放2110077295412371457全部34,976个编码UNCHANGED。原4字段及正式扩展逐项一致，原8,447条目录的21列保持，真实产品选择器可查。产品缺口已关闭，本项仅剩合同来源/客户冲突；下文“尚未创建”为首次核查记录，不代表当前状态。
- Evidence: 已迁入项目范围内196条合同关系涉及194个缺失正式合同号；去掉旧合同生成任务的20000条采集上限后，125个合同号有ERP来源分组，但全部存在CONTRACT_CUSTOMER_CONFLICT，另69个没有ERP分组。不能取MIN客户号或按技术ID任选。完整26原关系字段、原订单16字段和分组结果已冻结。产品原表34976编码中，8418个已有正式产品已沿既有规则承接全部原名称/型号；26558个没有正式产品，尚未创建。完整原4字段及缺口清单见[核查索引](../generated/2026-10-13-dppms-missing-formal-masters.json)。
- Question: 对缺失合同提供何种可核实的主档来源及冲突客户归属？对新增26558个缺失产品，是否明确将本批已批准的缺失产品补齐及ACTIVE规则扩展到这份整表清单，或先暂缓其创建？既有五个产品的实际补齐结果不代表已经对其余新增实体完成批准和迁入。
- Blocking scope: 仅缺失主档及依赖它们的关联。6480条具备正式合同引用的来源关系、8418个已有正式产品的业务标签已独立完成执行和重放；不通过空主档、伪造客户或静默丢弃原字段使全量运行通过。
- Decision owner: 需求方与对应主档Owner。

### Q-LEGACY-ORDER-FULL-SOURCE-CONFLICTS-20261013-023 — 整表订单来源冲突及公司映射碰撞

- Status: OPEN / BLOCKED_BY_SPEC
- Latest independent-suite decision（2026-10-19）: 仅新BASE_P14已批准同来源、公司、类型、订单号取`MAX(orderExecNumber)`；169组执行单选值不再待决。只读核清及剩余明细问题见[当前两表核查](../generated/dppms-24-tasks-three-implementations-20261019/P14-split-unique-review-20261019.md)。本轮尚未修改产品代码或重跑真实预览；原A/B任务、其目标碰撞处置与历史回执均不扩围、不操作。
- Preview verification（2026-10-15，新验证库）: P14 v5 / Run 2110507977953607682完整预览467,190条，PREVIEW_READY，ISSUE 2,138。ORDER 90,466条来源中410条重复业务内容冲突；LINE 376,724条来源中1,728条父订单预检不通过，来源预检合计与ISSUE数量相等。运行保留的2,000条问题明细仅含410条重复冲突及1,590条父订单缺失，不能当作全部Owner明细。误带旧库恢复证明的配置已修正，Q023/Q025及原恢复/归属保护保持，未任选执行单或正式写入。见[修复预览报告](../generated/2026-10-13-dppms-migration-mapping/production-tasks/preview-repair-20261015.md)。
- Evidence: 完整91572条原订单只读核对发现169个同ERP／公司／类型／订单号业务键下的410条原记录内容不同；既有正式模板已将它们标为DUPLICATE_BUSINESS_KEY_CONFLICT。此外原11536（D365／01／0／SO078894）对应两个均未删除的正式订单2101599583586234873和2104958251101687810，公司原01与已转换001在正式映射后碰撞；已有整表裁决明确要求这种碰撞阻断并另列裁决。全部411条完整16原字段及相应正式行见[来源和目标证据](../generated/2026-10-13-dppms-order-conflicting-originals.json)。只读核对无正式写入，没有选择技术ID最大记录或重定向旧来源绑定。
- Latest field audit（2026-10-14）: 410条归并为169组后，原模板参与冲突判断的10项业务属性中，全部组仅orderExecNumber不同，其余九项一致。现有订单头execution_no是单值，订单行real_execution_no是实际执行单号，不能未经原关联核实互相替代；须继续核对来源视图与正式订单行。目标MySQL因宿主M盘空间耗尽中断，未因此修改字段映射、任选主执行单或覆盖现值。逐组差异见私有证据full-preview-repair-order-difference-groups-20261014.json。
- Original derivation verified（2026-10-14续办）: 已只读提取旧过程`splitSoleAgentLendOrderInfo`的订单头SELECT，165条ERP上游订单经69条总代借货来源关系展开，逐字段复现全部410条原订单，额外或缺失行均0。其中37组D365原订单号对应78条记录，132组SMS的`-L`订单号对应332条记录；差异来自原过程的执行单展开，不能直接当作普通版本重复。169组涉及1,728条原订单行，只有1组的头/行执行单集合完全一致，31组没有原订单行、32组有订单行执行单不在头集合内；因此不能用任意一条订单行或最大原ID确定单值主执行单。仅执行了原过程的SELECT，没有调用存储过程或写入来源库。完整来源、原过程、只读复现与逐组比较分别保存在私有证据`resume-preview-order-upstream-audit-20261014.json`、`resume-preview-order-original-routine-proof-20261014.json`、`resume-preview-order-execution-line-audit-20261014.json`；正式映射及Q023待裁决状态保持。
- Question: 这411条是否明确暂缓正式承接并保留逐条冲突证据，或提供169组不同内容的版本选择依据，以及SO078894两个当前目标的保留和关联处置规则？完整字段承接审批不等于允许任选冲突版本、合并两个当前身份或覆盖不可变来源回执。
- Blocking scope: 仅411条冲突来源及依赖它们的商务关联。其余91161条来源的已批准公司转换、缺失字段补承接和已确认原空白保留继续；普通同版本不同内容校验仍生效，不以取消冲突来使全量预览通过。
- Decision owner: 需求方。

### Q-LEGACY-COLLECTION-MISSING-AUDIT-ACCOUNTS-20261013-022 — 整表回款计划新增缺失审计账号

- Status: RESOLVED / FULL_PREVIEW_VERIFIED（2026-10-14）
- Latest verification: 既有回款计划任务运行2110077014557581314完成35,451条用服范围预览，拟新增30,152、未变化5,299，缺审计账号不再过滤来源。此次仅预览，没有正式写入全量计划或补建用户。
- Latest decision: 需求方明确“540 个都仅保留原审计编码”。通过既有任务的creatorUnlinkedCode/updaterUnlinkedCode映射逐编码授权，原creator/updater保存原值，不新建EHR账号、角色或范围。删除因这540个原编码缺少账号而过滤计划的查询条件；原已可关联的账号映射、时间及更新保护保持。不能扩大为任意未知账号默认放行。
- Evidence: 任务2108574253093683202/v4全量预览2109734220705955842因原审计账号w01204缺失而被既有校验拒绝，该失败运行未写业务计划；后续合法子集已承接5330条并重放保持。只读核对完整35,501条原计划，542个审计编码在原fnd_user_info、t_user及当前租户system_users均不存在，原始缺失统计为31,145条计划、61,658处审计值。复核既有整表裁决后剔除已获准的crmapp/CrmInterfaceUser共5,583处及原10725、14943、28926、31337的l01663共8处，真正新增待裁决为540个编码、30,171条计划、56,067处值；l01663在其他原ID的347处不自动获准。完整编码和使用数量见[整表清单](../generated/2026-10-13-dppms-collection-missing-audit-accounts.json)，所有记录完整16原字段已在其指向的独立证据文件冻结。
- Question: 是否对新增30,171条计划按逐条原字段冻结、目录缺失复核，保留原creator/updater编码为未关联历史字符串，不创建账号、不产生权限、不映射执行人；或暂缓这些计划的正式迁入？首次提问的31,141条范围据实际已有裁决收敛为本30,171条，不重复请求服务账户已批准规则。
- Blocking scope: 新增缺账号的计划及相关正式显示；两种已批准服务账户、四条l01663例外、原审计已唯一绑定或原审计为空的5,330条独立计划继续。连续刷新来源身份未获批准，仍仅ONCE受控历史迁移。
- Decision owner: 需求方。

### Q-LEGACY-PROJECT-NULL-NAME-20261013-021 — 二十八条原项目名称为NULL

- Status: RESOLVED / RESTORATION_AND_FULL_PREVIEW_VERIFIED（2026-10-14）
- Latest verification: 恢复范围内1,521个NULL/空白名称已按正式编码补齐，原来源不改写；全量主档运行2110085474984529922完整读取80,170条，名称约束不再阻断，拟新增73,580、未变化6,580、已有项目现值保护10。全量拟新增尚未实际创建。
- Latest decision: 需求方明确“项目名称为空用项目编码替代”。NULL、空串或全空白时，正式project_name使用经已裁决规则确定的正式project_code；其余名称原样承接，原37列和不可变证据保持原NULL/空值，不伪造来源名称。已补Owner与名称回退验证。
- Evidence: 既有主档任务2108608417343934465/v4在当前合法组织范围内预览2109726796813041665，被PROJ Owner以DPPMS_PROJECT_NAME_OR_CODE_INVALID拒绝；正式主档写入0。只读核对发现28个规范type=10项目的projectName均为原NULL，完整37原字段见[逐条来源](../generated/2026-10-13-dppms-project-null-name-originals.json)。原对话没有针对项目名称NULL的既有裁决。
- Question: 这28条是否明确暂缓正式迁入、保留来源并列为未完成，或由需求方提供可核实的正式名称承接规则？正式主档现有名称约束不能自行取消，不能把公司/部门原NULL的专用裁决扩大到名称，也不能默认为编号或客户名称。
- Recommendation: 暂缓这28条且保留完整原字段；已有名称约束继续生效。其他来源合法、组织授权满足的独立项目继续迁入。
- Blocking scope: 仅28条缺名称项目及依赖其正式项目身份的关联；实际错误不能计为已迁入或全量完成。
- Decision owner: 需求方。

### Q-LEGACY-PROJECT-MIGRATION-EXECUTOR-20261013-020 — 全量主档迁移的合法组织范围

- Status: OPEN / AUTHORIZATION_REQUIRED
- Evidence: 指定59282任务2108608417343934465/v3的全量预览2109721001660645378被既有PROJ Owner以DPPMS_PROJECT_CREATION_ORGANIZATION_SCOPE_REQUIRED拒绝，正式主档写入0。当前操作人1具有迁移执行和项目创建权限，但所需95个公司＋办事处组合仅覆盖001/162007一个；另外94组合的实际ID、名称和来源数量见[完整范围清单](../generated/2026-10-13-dppms-migration-organization-scope-required.json)。
- Question: 由哪个已有、具备相应公司＋办事处联合范围的合法执行身份完成余下迁移，或由权限Owner按何种既有正式授权流程补齐本次执行范围？不能把super_admin功能权限、公司级空部门资格或来源角色资料当作全部部门授权。
- Existing rule: SDS07组织授权要求公司与部门命中同一条当前有效UserCompanyDepartmentScope；普通创建与历史主档Owner均使用该校验。缺失原公司/部门的3748＋8条继续沿已批准的专用super_admin迁入规则，不扩大到非空组织。
- Blocking scope: 仅当前操作人没有精确组织范围的主档创建及依赖它们的后续关联；六条合同排除、既有关系重放、当前范围内或已批准空归属的主档、其他独立对象和浏览器验证继续。
- Decision owner: 需求方与权限Owner。

### Q-LEGACY-CONTRACT-MISSING-BRIDGES-20261013-019 — 整表新增六条缺组或缺成员合同记录

- Status: RESOLVED / SIX_ORIGINALS_EXCLUSION_APPLIED / REPLAY_AND_BROWSER_VERIFIED
- Evidence: [六条完整原字段](../generated/2026-10-13-dppms-unresolved-contract-originals.json)。2026-10-13按交接指定连接只读执行整表SQL，83,472行中83,454行有规范项目身份；其余18行中12行属于已批准的三条缺组排除及九条缺项目成员，另六条没有现行裁决。
- Question: 原11542（prj_gp11187_bak）及28588、28589、28590、28591（prj_gp27995）缺组和成员；原42501的组prj_gp41882存在但没有成员。是否按各自缺失原因明确排除这六条正式关系写入并保留原证据，或提供可核实的原项目关联？不能把Q006的三条具体排除自动扩展到本六条。
- Recommendation: 对确认无法补齐来源关联的记录逐ID排除，仅冻结完整26原字段且缺失状态未改变时允许SKIPPED，不绑定其他同合同项目。
- Decision: 2026-10-13需求方明确“排除这6条，保留原始证据”。仅原11542、28588、28589、28590、28591、42501获准；五条缺组沿既有明确排除动作，42501按“有组无成员、无法确定项目归属”排除。完整26原字段、缺失状态及未存在正式目标必须与冻结配置一致；原字段或关联恢复均阻断，不扩展到其他原ID。
- Configuration: [六条获批配置](../generated/2026-10-13-dppms-contract-exclusions-approved.json)，写入既有任务migrationDecision的LOOKUP；不将具体原ID写入生产Java或SQL。
- Verification: 既有任务2109363204544983041已使用冻结六条原26字段和精确缺失动作完成预览、正式执行及重放，三次均SKIPPED6；各条目标ID为空，未建立正式关系或来源目标绑定。旧项目、旧正式关系、旧绑定及旧运行回执逐值不变，原26字段证据逐条核对完整。真实浏览器查看正式执行及重放明细可见六条独立跳过原因；证据见本轮迁移进展及仓库外contract-exclusions-proof.json，未扩大为其他缺失记录的一般排除规则。
- Blocking scope: 六条获批排除已落实，不再阻断其正式任务配置；全历史迁入仍受合法项目身份、正式合同及Owner等独立校验约束。
- Decision owner: 需求方。

### Q-LEGACY-DEVICE-RELATION-TABLE-20261012-001 — 主附SN关系整表复用及自关联

- Status: WHOLE_TABLE_APPROVED / SELF_RELATION_EXCLUSION_APPROVED
- Evidence: [完整19既有列、8原字段、最新全量核查](../generated/2026-10-12-dppms-device-relationship-table-decision.md)。2026-10-12两表全量与此前指纹一致，65046业务值一致，360原自关联未被承接。
- Decision: 2026-10-12需求方答复“整表按建议”，批准复用实际ast_device_relationship、不新增表列或扩展；create_time取原createtime、update_time的原NULL取本条原createtime，原无操作者不编造旧用户。65046限定存量纠正另提逐键预览，不据整表审批直接写入。
- Separate exception: 需求方2026-10-12明确批准360条不迁入双设备关系，逐原ID列明“双方SN和产品相同，不构成主附设备关系事实”；保留原8字段及来源证据，禁止自关联约束保持。新迁移读取后明确SKIPPED，不在SQL静默过滤，新原ID或原字段变化阻断。
- Owner: 需求方；不阻断不依赖该规则的独立已批准工作。

### Q-LEGACY-DEVICE-RELATION-AUDIT-20261012-002 — 65046条存量日期限定纠正

- Status: RESOLVED / LIMITED_CORRECTION_APPLIED / FULL_REPLAY_VERIFIED
- Evidence: [逐键审计纠正方案](../generated/2026-10-12-dppms-device-relationship-audit-correction-decision.md)。65046条原8字段、同步绑定及成功回执完全匹配，完整19目标字段已冻结为本次保护预览；仅只读核对。
- Question: 是否仅通过迁移/同步及AST Owner恢复该清单的create_time、update_time？原NULL更新时间用本条原创建时间，所有业务值及操作者保持，执行前全字段及版本变化阻断，既有回执不覆盖。
- Boundary: 整表复用已经批准，不等于批准具体存量审计改写；360条自关联不在本清单。指定任务库65046条限定纠正已完成并逐条19列复核；旧绑定和回执保护通过，完整来源证据65406条。全量重放SUCCESS／UNCHANGED65046／SKIPPED360，19正式字段与执行后冻结值完全一致，未重复推进版本；真实非空迁移字段对照通过。普通设备详情非空关系仍依赖真实项目关联和授权，不扩大范围，不据此标为生产上线或其他历史领域完成。
- Decision owner: 需求方；2026-10-12明确答复“仅这65046条按上述保护条件纠正”。独立限定适配器及AST Owner实现只改两项日期并推进版本，普通同步保护保持。正式写入结果另记录，不以代码或测试通过代表落库完成。

### Q-LEGACY-CONTRACT-IDENTICAL-RELATIONS-20261012-003 — 完全相同原合同关系的全历史身份

- Status: RESOLVED / APPROVED_18_FROZEN_GROUPS / RULE_IMPLEMENTED / SOURCE_ASSOCIATION_PENDING
- Evidence: [既有17列与完整原表/桥表清单](../generated/2026-10-12-dppms-project-contract-relation-table-proposal.md)。原pm_project_contract存在18组39条，除id外6字段完全相同；另16组42条审计不同。当前原7字段已逐条与先前材料复核一致。
- Question: 是否仅18组完全相同记录按同一正式项目＋合同共用一条正式关系，每个原ID独立迁移绑定，完整保存各原业务/审计，不增加重复授权？原ID展开项目时仍使用精确组成员身份；不能忽略桥表差异。
- Decision: 2026-10-12需求方明确答复“仅18组完全相同记录按上述规则处理（建议）”。仅冻结18组39原ID，每个精确项目成员共用正式合同关系；6项原业务及审计全部同值承接，每个原ID＋成员ID独立绑定。相同创建时间下采用最小原ID仅稳定技术绑定，不据ID改变业务内容。新ID、原7字段变化、组缺行、桥差异、目标保护差异或现存接管均阻断，不扩展为通用去重规则。
- Verification: 原表按全部18个精确合同＋组业务键重新读取39条，无额外原ID，全部7列一致。Q006裁决后LEFT JOIN仍39行；16组32行精确关联16个原项目，4行明确关联历史原36645，3行缺组获准SKIPPED，完整来源保留。最新28项相关测试及完整41模块打包通过；真实32行共用16个Owner输入，32个完整26字段及17目标字段回执独立保留，重放和差异保护通过。17个相关原项目均无正式迁移绑定，业务/结构写入0、未创建新迁移运行或切换服务；4行历史Owner规则已由Q007批准并实现，实际迁入仍等待原36645正式绑定。
- Boundary: 第1、2组跨月关系的合并规则已由Q008统一批准，安服先行项目身份及范围见Q009；原桥表剩余业务属性及其他失效关系仍分别待裁决。本规则不批准追加历史项目迁入或接管现存关系，不新增表列，不删除原表/证据/回执。Q006只阻断缺关联7条，其他已授权独立工作继续。
- Decision owner: 需求方。

### Q-LEGACY-CONTRACT-DUPLICATE-TIMES-20261012-004 — 重复原关系的不同创建时间

- Status: RESOLVED / GENERAL_SAME_FACT_DUPLICATE_RULE_APPROVED / SOURCE_PROJECT_MIGRATION_PENDING
- Evidence: [16组42条完整7原字段清单](../generated/2026-10-12-dppms-project-contract-relation-table-proposal.md)。原81条重复记录重新读取与此前材料一致；16组42条仅createTime不同，除技术id外其余5字段完全相同。
- Question: 是否仅第3～16组秒级重复（14组37条）共用一条正式关系，创建时间取最早原值、每个原ID保留独立绑定；其他创建时间明确列为“不独立迁入正式审计：重复登记时间，不构成不同关系事实”？原字段及来源证据完整保留，不能将未选时间称为正式业务已承接。
- Decision: 2026-10-12需求方答复“仅14组秒级重复按上述规则处理（建议）”。仅冻结清单第3～16组37条共用正式关系，取各组最早createTime；各原ID独立绑定并保留完整原字段。其余23个createTime不独立迁入正式审计，原因“重复登记时间，不构成不同关系事实”。这不是按时间差自动合并的一般规则，新ID或原7字段变化必须阻断。
- Implementation: 现有INT适配器按冻结37条完整原7字段、同组完整性及桥关系核验后，每个精确组成员只调用既有COM Owner一次；各原ID＋成员ID分别输出完整26原字段、同一正式17列和绑定。缺行/原值变化/目标保护不同/未批准现存接管均阻断，其他重复仍被COM唯一性校验拒绝。21项针对性测试及完整宿主机应用打包通过；Q005明确选择原20317后，来源实际为37个唯一身份、14个原项目，原7字段保持不变；未选择项目的旧40行材料作为失败保护保留。14个原项目仍无正式绑定，此批无业务或结构写入、未切换59282运行服务，不标为真实迁入完成。
- Boundary: 第1、2组afxx跨月重复按Q008通用最早日期规则处理；原项目主体身份与本轮范围仍由Q009等待裁决，不能补造解绑/重新关联日期。无新增表列，不授权覆盖被修改的正式关系，也不直接授权扩大到全历史写入；正式项目绑定、合同公司资格、完整桥关系及17列保护仍必需。18组完全相同记录已由Q003独立批准，7条关联缺失由Q006处理。
- Decision owner: 需求方。

### Q-LEGACY-CONTRACT-DUPLICATE-PROJECT-20261012-005 — 第13组原项目编码多义

- Status: RESOLVED / EXPLICIT_PROJECT_ID_APPROVED / RULE_IMPLEMENTED / SOURCE_QUERY_VERIFIED
- Evidence: 第13组原20538、20539、20540，经原成员20223的projectCode=16200615060401N-0精确查询，pm_project_header存在7916、20317两条同码同名记录。全部原26关联字段及两个项目37列已读取；三处重复展开仅originalProjectId不同，不能构造不同成员ID或任选项目。
- Question: 这三条原合同关系应关联原项目7916还是20317？建议选20317（2018-01-02 15:33:07新建、旧状态100正常闭环；7916为2015创建、旧状态20不予跟踪），但时间及状态只作为裁决参考，不能代替真实外键依据。
- Decision: 2026-10-12需求方明确答复“三条关系关联原项目20317（建议）”。仅原关系20538/20539/20540＋成员20223精确关联原20317，来源LEFT JOIN仍完整读取原合同及组桥字段，丢失目标、改码或其他歧义继续阻断；不按更新时间普遍选取同码项目。
- Verification: 来源完整26字段查询核实37个唯一身份、原7字段与冻结值一致；三处originalProjectId均20317，其余关联未按日期选主。21项测试及完整应用打包通过，包含真实37行归并为14个Owner输入、37个独立回执，23个未选创建时间明确排除；旧40行内7916的展开在适配器中仍被明确拒绝。源及目标业务写入0，14个原项目无正式绑定，实际关系写入待后续项目迁入，不伪称已完成全历史承接。
- Boundary: 不去重其他来源行、不写正式关系、不调整两项目或其生命周期。正式项目迁移绑定仍必需，不自动扩大本批10项目写入范围；18组完全相同关系已由Q003单独批准，7条关联缺失另见Q006。无新表、列或扩展。具体来源查询与回归验证结果另记，不据批准宣称已实际迁入。
- Decision owner: 需求方。

### Q-LEGACY-CONTRACT-IDENTICAL-MISSING-PROJECT-20261012-006 — 获批合并记录的7条原项目关联缺失

- Status: RESOLVED / HIS_PROJECT_36645_APPROVED / THREE_MISSING_GROUP_EXCLUSIONS_APPROVED / RULE_IMPLEMENTED
- Evidence: [全部原7列、桥17列及缺项记录](../generated/2026-10-12-dppms-project-contract-relation-table-proposal.md)。完整LEFT JOIN读取39行，未过滤或丢弃缺项；原合同41325～41328指向成员37632、项目编码16200220110601N-0-his，原组存在但没有精确原项目。即使去掉-his也有36645、39370、39371三个同码同名原项目，不能自行选取或生成失效区间。42366～42368的原组prj_gp41755及成员均缺失，其他合同关系中的同合同号不构成这三条的项目关联证据。
- Question: 这7条是否本轮暂缓并保留待关联，列明“-his失效成员未能明确关联项目”及“原组和成员缺失”，其余已批准规则继续？若本轮处理，请分别指定实际原项目ID及有效/失效事实；不得自动去掉-his或借其他组挂靠。
- Decision: 2026-10-12需求方明确“-his都代表历史项目关联的合同关系”。据此确认历史关联业务含义，不因后缀排除记录，不去掉后缀后作为当前关系挂靠。该答复没有指定36645/39370/39371中的实际原项目、失效日期或另3条缺组归属，也没有批准暂缓/排除；这些部分继续等待具体裁决。
- Follow-up evidence: 原组37771及原项目36645的createTime都为2020-11-13 07:39:10；本4条关系创建于2021-04-14 15:10:56，另外两个同码项目39370/39371分别在2021-04-15 14:53:57/15:14:26新建。建议明确4条关联36645，但不按日期自动选择。现有ContractRelationCommandService及ProjectContractRelationMigrationService只支持当前ACTIVE关系，后者拒绝非ACTIVE及非空effective_to；既有当前查询亦限定ACTIVE。历史关系Owner和查询确有缺口，未擅自新增状态、放宽Owner、修改唯一键或设置不存在的失效日期；实际历史整表映射须在主体明确后提交。
- Final decision: 需求方随后明确“4条历史关系关联原项目36645”；仅原41325～41328＋成员37632采用原36645，不以通用REPLACE去除后缀。对于原42366～42368需求方回复“不存在的记录直接忽略”，按该问题限定为这3条缺组/成员关系不迁入，逐ID登记原因“原项目组及成员不存在，无法形成项目合同关联”，不扩展为其他孤立关系的一般排除规则。
- Implementation: 来源LEFT JOIN仅对上述4个原ID及成员37632精确解析36645，原-his值完整读取；裁决规则保护目标36645，其他同码目标阻断。INT适配器对3个获准缺组ID逐项保护完整7原字段、全部空桥/项目列及id:0来源键，输出SKIPPED和26原字段；关联恢复、原值变化、已有目标/绑定或接管要求均阻断，不借排除删除既有业务。4条历史关系已由Q007批准并接入正式HISTORICAL迁移Owner；原36645正式绑定不存在时仍拒绝写入，没有绕过状态、授权或按当前关系写入。
- Source verification: 查询实际仍39行/39个来源身份，17个原项目候选，无多义，4条均36645，仅已排除3条缺组/成员；39条原7字段与冻结值一致，17个原项目均无正式迁移绑定。业务/结构写入0；具体代码验证及打包见文末最新证据，不据来源查询完成宣称历史实体已承接。
- Boundary: 不新增表列，不排除原7字段、不删除来源或旧回执；缺关联记录继续完整进入来源证据，写入必须阻断。Q003合并规则已经批准，不重复确认；本问题不授权扩大历史项目写入，不放宽正式Owner校验。
- Decision owner: 需求方。

### Q-LEGACY-CONTRACT-HISTORICAL-OWNER-20261012-007 — 历史项目合同关系的既有17列业务规则

- Status: RESOLVED / WHOLE_TABLE_AND_HISTORY_RULES_APPROVED / CODE_AND_EMPTY_READ_VERIFIED / PROJECT_BINDING_PENDING
- Decision: 2026-10-12需求方明确答复“17列复用及上述历史规则整表按建议”，批准HISTORICAL、原日期及NULL规则、正式迁移Owner与受控历史只读查询、当前绑定与授权隔离；没有批准扩大原项目实际迁入。
- Evidence: [既有17列历史关系整表方案](../generated/2026-10-12-dppms-project-contract-relation-table-proposal.md)。Q006已明确4条历史关系及原36645。实际正常Owner、迁移Owner和当前查询仅支持ACTIVE；状态列虽为VARCHAR(32)，不能因物理列能存字符串而认定历史业务已实现。
- Proposal: 不新增表、列、扩展或索引，复用com_project_contract_relation全部17列。建议status增加具名业务值HISTORICAL（历史项目合同关联，仅保存已存在的原关联，不代表本次解除合同）；relation_role仍RELATED，effective_from取原关系createTime；effective_to原无值保持NULL，不编造解除日期。历史审计和NULL规则沿已批准映射，每个原ID独立绑定；迁移Owner仅对明确来源和历史主体复用相同租户、权限、公司、项目绑定/版本及17列保护，写入正式历史关系，不运行当前办理或授权动作。当前关联查询仍只认ACTIVE；历史查询按原项目独立只读并受项目与合同公司权限限制，不使历史关系成为当前合同授权依据。普通关联/项目创建不能把已有历史关系当作当前重放，不修改历史，发生相同身份冲突则阻断；当前唯一键保持，不构造多个关系角色绕过唯一性。
- Boundary: 新业务状态值及历史Owner/查询规则已批准；4条完整来源按获批历史规则承接，正式项目绑定不存在时仍阻断写入。Q003/Q006身份和排除不重复询问，本方案不授权扩大历史项目实际迁入。实际普通基础能力和历史只读须分别验证，不用迁移快照替代业务实体，不生成解绑或任务/审批完成事实。
- Decision owner: 需求方。

- Latest verification: 68项相关后端测试、6项真实MySQL临时表检查、3项前端组件测试及完整41模块宿主机打包通过。本任务59282／19082曾离线，按已有授权恢复，59191／48182未停止或改部署；按现存149条成员及其余完整保护表核对启动前后无变化。真实项目992203060198基础页面及历史查询返回code=0／空列表、无控制台错误，当前合同订单及2026-10-14结束日期保持。非空历史实际迁入依赖原项目36645正式绑定，该绑定仍不存在；不以测试或空页面成功宣称4条已落库。

### Q-LEGACY-CONTRACT-DUPLICATE-POLICY-20261012-008 — 所有同类项目合同重复记录统一规则

- Status: RESOLVED / GENERAL_RULE_APPROVED / RULE_IMPLEMENTED / AFXX_IDENTITY_PENDING
- Decision: 2026-10-12需求方对两组跨月原ID44210、45535、45538及42247、45177明确回复“同一项目＋合同共用一条正式关系，正式创建时间取各组最早值，所有相同情况都使用同样的规则”。同类原记录不再限定18个完全相同组或14个秒级组，也不设时间差阈值。
- Rule: 原合同号、项目组、创建人、更新时间、更新人相同，精确项目与完整桥及正式合同资格一致时，共用正式关系；原创建时间取最早，平时间用最小技术ID只稳定绑定；每个原ID＋成员ID独立绑定及完整来源保留。其他创建时间明确不独立迁入正式审计，原因“重复登记时间，不构成不同关系事实”。不同人员、更新事实、项目身份或桥属性不属于同类，继续阻断，不因同项目合同丢弃字段。
- Completeness: 来源查询先从完整pm_project_contract按上述五项精确分组计算duplicateGroupSize，这是读取证明，不是新增物理列或正式业务字段。适配器必须读齐同组原ID后才能计算最早值；部分筛选、拆页或重复身份阻断，不能使用部分记录作完整关系。既有冻结配置及旧回执不改写，新模板通过原迁移功能使用。
- Boundary: 不新增表、列、索引，不授权接管被修改关系或扩大原项目实际迁入。HISTORICAL仍沿Q007专用规则，不由重复规则产生解除日期、当前商务绑定或授权。
- Decision owner: 需求方。

### Q-LEGACY-CONTRACT-AFXX-SOURCE-PROJECT-20261012-009 — 两组跨月关系的真实安服先行主体

- Status: SQL_RULE_AUDIT_IN_PROGRESS / INDIVIDUAL_ID_QUESTION_SUPERSEDED
- Evidence: 原关系44210、45535、45538经成员43427的16030021090911N-0，在原pm_project唯一匹配42026（对外经贸大学2021年安全服务，afxx）；原42247、45177经成员41526的16201619041701N-0-his，去掉后缀仅用于候选核查，原pm_project存在同码同名40191／42902（国网四川省电力公司信通公司安全服务项目，afxx）。40191与原成员均于2021-06-09 13:45:42创建；42902于2021-10-28 13:54:22新建，不能用日期自行选主。真实pm_project_header视图只取projectType=10，来源缺关联并非原项目不存在。
- Question: 是否登记原三条精确关联42026，并明确两条历史关系关联40191或其他项目；afxx本轮只保留身份与迁移方案，还是启动项目主表整表规划裁决？仅Q008合并规则不等于批准新项目类型及全历史实际写入。
- Boundary: 完整37原字段和26关联字段保留。未回复前5条阻断，不自行转当前关系、不忽略、不按原组ID冒充项目ID。已批准通用合并、4条原36645历史及3条缺组排除继续独立实施；不重复确认。
- Decision owner: 需求方。

- Resolution 2026-10-12: 用户要求改用pm_project、约束类型，整表迁移而非10项目专用，并指示查项目列表SQL归纳关系，不逐ID裁决。本项旧逐ID问题已替代；完整原字段仍保留，多义规则继续追实际旧查询/失效/核销代码。整表身份唯一键另列Q011，未获批准不变更。

## 模板

### Q-LEGACY-WHOLE-TABLE-TASK-20261012-010 — 完整整表任务与正式展示

- Status: USER_DIRECTION_APPROVED / IMPLEMENTATION_IN_PROGRESS
- Decision: 用户明确全部迁移任务均为整表规划，最终完整迁移；10项目只是前期验证。使用pm_project并增加类型约束，关联以旧项目列表及对应SQL为参照；不再逐项目裁决归属、不在同步实现中固定SQL/项目ID/分组名单；不新增项目历史专用块，全部迁入数据承接正式业务表和正式展示区。
- Evidence: 实际83,550条37列、三类项目及全部85,472条组成员已只读核对。旧列表代码和实际数据不能支持“历史必有effectiveTo”的推断；该推断会丢失1,644个历史成员的匹配，明确拒绝。原同类型同码165组342条与正式租户＋编码唯一约束冲突。两个参数化查询实际覆盖83,550项目和86,780原合同关系；展开87,252条，保留多义关联。
- Implementation: 新增历史合同专用组件已撤回，正式合同区域同时显示当前与迁入关系；原固定34组81行名单已撤回，明确例外转入带完整26字段保护的任务配置。两初始SQL移出Java拼接、使用pm_project、范围默认不限定项目，任务sql/parameters沿现有编辑及版本冻结；不改既有冻结任务、来源证据或回执。
- Verification: 合同跨块归并已实现，70项后端、6项真实MySQL、2项真实来源查询、13项相关展示及41模块打包通过。59282已切换，其他服务保持；真实合同区展示原日期，产品清单独立进入普通业务入口，回款计划进入合同区；DPPMS关联资料入口撤回。非本批201原生联系人修改及CUS历史53保留，其他保护行保持。本轮迁移业务/结构写入0，未执行全表迁入。
- Remaining: 全表新项目创建、三类型状态映射、同码身份及索引、各域整表迁移入口和其余正式展示仍需完成。Q009的逐ID提问已被用户要求“查项目列表SQL、归纳规则”替代，不再等待具体40191/42902答复；多义类别继续查实际旧调用，不按日期猜配。生产Flyway基线保持未完成。
- Plan: [真实整表核对及纠正方案](../generated/2026-10-12-dppms-whole-table-task-correction.md)。无新增业务表/列；本轮整表核对业务写入0、结构写入0。
- Decision owner: 需求方。

### Q-LEGACY-PROJECT-CODE-NAMESPACE-20261012-011 — 整表同码身份与正式唯一键（原命名空间提案撤回）

- Status: RESOLVED / MAX_SOURCE_PROJECT_ID_RULE_APPROVED / IMPLEMENTATION_IN_PROGRESS
- Decision: 用户明确“同类型+同编码取最大projectId，迁移过程原项目编码不变”。撤回code_root_id命名空间唯一键方案；主档取同原类型、精确原编码下最大projectId记录。不按启停/有效期过滤后再选最大值，不改原编码。
- Evidence: 原83,550条对应83,373个主档：10为80,170，afss为2,508，afxx为695。当前10个验证项目全部已是最大ID；归并后仍有12组编码跨类型重复、24个主档。源查询只读，正式结构及业务写入0。
- Association: 子表原projectId必须经同类型同编码映射到规范主档；合同桥表按原组类型、去掉-his的编码取最大ID，-his的历史关系含义保持。所有原ID、原子表事实和已有不可变回执保留；先前个案原身份作为旧裁决证据，不再强制选择较小正式目标。Q009不再逐项目提问。
- Follow-up decision: 用户明确跨类型12组特殊处理，按分组ID大小在冲突时末尾序号加一；拒绝改为tenant_id＋project_type＋project_code唯一键，现有编码全局唯一约束保持。原编码不变规则仅对此12组采用已批准例外；原库不修改、原码保留追溯。
- Sorting resolved: 用户明确按规范projectId升序，较小保留原码，后续冲突尾号递增并跳过原表及正式库占用；重跑复用绑定编码。24主档的多组/无组不影响排序，不再按项目组ID猜配。
- Date decision: 用户接受仅历史初始化允许原关闭日期NULL；NORMAL_CLOSED/EXCEPTION_CLOSED仍沿已批准映射，NULL不填本次时间或其他审计日期。普通业务关闭校验保持。已修改专用历史策略，实际写入尚未执行。
- Verification: 新规范项目主档及合同查询2项真实原表测试通过。逐行相关MAX查询的慢查询失败保留；改为一次汇总类型＋原编码，派生表用完整二进制类型/编码组合精确关联，基本合同展开查询实测1.15秒。未改原库索引、未写正式业务。后续完整任务、新建项目及其他状态映射仍未完成。
- Implementation: 通用规划及Owner已支持结构化跨类型尾号分配，原ID和原码保留；真实全表规划12处调整，最终仅类型10执行范围无跨类型调整。正式项目创建及绑定查询已补，不造模板或当前任务，尚未部署新增后端或实际全量写入。
- Final scope: 用户明确三类型方案和设计完整实施，最终实际迁移仅用服售后。已批准用服30及安服10/31为ACTIVE/S0，安服32/40/50为ACTIVE/S4/S5/S6、100为NORMAL_CLOSED/S6；非闭环原关闭日期独立留project_close_time，不初始化closed_at，不生成完成事实。旧用服100/20/31/32裁决保持。

### Q-LEGACY-PROJECT-AUDIT-CODES-20261012-012 — 整表项目未迁入审计账号

- Status: RESOLVED / IMPLEMENTED_AND_DEPLOYED
- Evidence: 原表及正式账号/迁移绑定已只读核对。用服规范主档6605处creator为sys（fnd原ID550，未在已批准账号范围），安服5处updater为jiangyating；实际迁移参数仅10，后者仅影响完整设计。
- Proposal: 原编码精确保留到正式creator/updater，不新建账号、不授角色、范围或项目权限、不替换管理员，历史日期保持；其他人员经既有来源绑定及SYS身份精确关联。
- Decision: 用户确认sys/jiangyating原编码保留正式审计字段。已核查Yudao DefaultDBFieldHandler：默认回填登录user_id的字符串，不是用户名；迁移显式保存原值及NULL，不改变全局回填规则。两个已批准同人重复账号的全部原ID均绑定同一正式用户，不按ID随意选人。

### Q-LEGACY-PROJECT-IMPLEMENTATION-20261012-013 — 整表项目实施方式完整值域

- Status: RESOLVED / IMPLEMENTED_AND_DEPLOYED / DICTIONARY_BROWSER_VERIFIED
- Evidence: 原fnd_basic_data类型15明确0原厂直服、1原厂督导、2直服转包、3代理商/用户自服、4原厂集成；正式pms_implementation_method仅有0/1/3同义码。规范用服4有106条，2为0条，空字符串2918条，其他NULL已直接保持。
- Decision: 用户要求原字段NULL/空串时取原同名Readonly字段。旧struts查询实际使用columno12_readonly，3243条可回退0/1，8400条保持NULL；原37列保留原值，正式字段用获准回退值。用户先指出2已失效，后明确“批准2,4都承接表”，以最新裁决为准，2/4均承接，撤回2的排除。
- Implementation: 复用implementation_mode，2→DIRECT_SUBCONTRACT/直服转包，4→FACTORY_INTEGRATION/原厂集成；补既有字典，不新增表列。原2的effectiveTo为2015-12-28 10:20:32，沿已批准失效字典停用规则保留停用，不恢复为普通新增选项；4原有效，保持启用。V439种子不覆盖已有定义及审计，应用仍受生产基线约束。
- Boundary: 0～4及Readonly回退已批准；未知值仍阻断，不默认成直服。当前规范用服2为0条、4为106条。
- Verification: V439通过指定任务库既有Compose/Flyway隔离入口补齐两项；2106原字典行及旧回执保持。正式浏览器两项分别显示关闭／开启，重放临时克隆各插入0条。完整生产Flyway基线问题仍单独保留。
- Plan: [完整整表约束提案](../generated/2026-10-12-dppms-whole-table-task-correction.md)。
- Decision owner: 需求方。

### Q-LEGACY-PROJECT-NULL-ORGANIZATION-20261012-014 — 整表项目原公司或办事处缺失

- Status: RESOLVED / IMPLEMENTED_AND_DEPLOYED / WHOLE_TABLE_EXECUTION_PENDING
- Evidence: 规范用服3748条原办事处NULL、8条原公司NULL；非空公司外键未找到目录的数量为0。
- Decision: 用户采纳原NULL保留。仅既有super_admin且同时具备pms:integration:execute及pms:project:create的执行人可承接；不新增角色、范围、成员授权，普通创建及后续业务仍要求真实目录关系。
- Protection: 非空原键缺目录、未知公司码仍阻断；不将已有正式归属清空，也不将原NULL猜配为执行人的公司或部门。新根复用正式表可空列，不增加物理字段。首次创建、重放和权限失败已经定向及会话临时表验证并部署；未执行真实整表写入。新根正常树投影正在补齐，验证完成前不声明全量可执行。
- Latest dependency audit（2026-10-14）: 当前已迁入的3,720个空办事处项目，其原column001全部为NULL，另外28个原NULL项目仍在未创建清单。当前实施范围来源中3,638个这类项目涉及14,443行；不是现有目录漏同步，不能通过补基础目录或默认当前部门消除。实施范围已批准方案要求办事处四项必填，继续列为待关联，既有原NULL保留裁决及后续业务规则均不放宽。逐项目原值证据为`resume-scope-office-origin-audit-20261014.json`。

### Q-LEGACY-PROJECT-CONTRACT-GROUP-20261012-015 — 原合同组8字段的独立资料

- Status: RESOLVED / WHOLE_TABLE_RULE_APPROVED
- Evidence: [全部8原字段、旧写链及整表建议](../generated/2026-10-12-dppms-project-contract-relation-table-proposal.md)。旧组承担合同共享桥接；平台项目组合有战略／客户／区域／计划／专项用途，并非同义业务。1941个非空组名均为“总代借货项目合同合并”。
- Question: 是否仅用原组解析正式项目合同关系，原组名及四项独立组审计不迁入并逐列登记原因？技术ID和组编码保持来源追溯；类型复用正式项目类型及匹配约束。不建立项目组合或父子树，不新增表列或扩展。
- Decision: 用户答复“按该整表规则处理”。批准projectGroupName、createTime、createBy、updateTime、updateBy不独立迁入，分别记录旧商务组说明或独立组审计无同义实体的原因；技术身份追溯、精确合同展开及项目类型约束保留。原26列合同／桥材料仍完整采集和保护，不裁剪来源证据，不混写正式合同关系审计。
- Boundary: 组成员九字段仍单独规划，其他已批准的整表能力继续。该裁决不将技术证据称为组业务承接，不建立项目组合或父子树。
- Decision owner: 需求方。

### Q-LEGACY-PROJECT-CONTRACT-MEMBER-20261012-016 — 原合同组成员9字段完整承接

- Status: BUSINESS_RULES_RESOLVED / FORMAL_MAPPING_IMPLEMENTATION_IN_PROGRESS
- Evidence: [全部九列及整表建议](../generated/2026-10-12-dppms-project-contract-relation-table-proposal.md)。85,472原成员中4,132条商务编码不能由项目编码截断还原，4,832条无对应原执行单；规范项目22046有两个不同商务编码。拆分备注1条“上海节点”、2条空串。10条未匹配同类型同码原项目。
- Question: 是否批准通过正式项目实体扩展commercial_project_codes（JSON字符串数组）和split_name_suffix（TEXT）承接两项商业属性，四项纯桥成员审计不独立迁入，并逐项记录原因？技术追溯、正式项目合同关系和已批准历史规则保持，无新增物理表列，不覆写项目名称或原customInfo。
- Decision: 用户明确smsProjectCode是CRM执行单对应的原始项目编码，所有项目序号以其为依据；pm_project_group_relationship本身是旧项目合同关联主体。用户进一步明确：这不代表新系统以旧表为模型依据，承接必须根据新系统已有业务表规划。正式关联复用com_project_contract_relation，商务取值沿既有合同、订单、执行单关系核实；不移植旧组模型，不据旧序号规则修改正常项目编码分配。撤回commercial_project_codes、split_name_suffix两个项目扩展候选；未实施，也不将四项成员审计排除当作已批准。旧SQL用于证明来源关系及字段含义，不替代目标Owner、授权、唯一性和正式数据展示规则。
- Separate exclusion: 用户批准10条无原项目身份的组成员不生成正式关系，并逐原ID记“原项目身份不存在，不能挂靠其他类型或同名项目”。完整九原字段及类型已记录project-group-member-business-readonly-20261012T215344.json，不扩展排除条件。
- Audit decision: 用户批准合同关系审计为主、成员同名审计补缺；未选取的不同成员值明确列为“不独立迁入：同一正式关系只有一组审计，按裁决采用合同关系主值”。保留完整原字段及逐字段原因，不新增审计副本，不据更新时间伪造解除日期。既有已迁记录的17列及后续修改保护保持。
- Split decision: 用户答复“不迁入拆分，这部分就是新系统的项目拆分主子项目功能”。原mergeBranchMark不迁入，不写正式名称、备注、扩展或快照；不据旧备注重建项目父子树。正式主子项目功能保持。
- Boundary: 仅对应本源表，Q015原组裁决不代替本表；全部九原字段继续采集及保护。其他已批准整表任务继续。
- Decision owner: 需求方。

### Q-LEGACY-CONTRACT-UNLINKED-AUDITORS-20261012-017 — 正式合同关系审计原编码

- Status: RESOLVED / IMPLEMENTATION_IN_PROGRESS
- Evidence: project-contract-approved-audit-preflight-20261012T222512.json，按旧列表REPLACE历史标记及类型＋编码最大项目ID规则核对83,454条用服关系候选。sys共8,518处创建人和1,456处更新人；异常数据处理5处创建人，均没有正式平台账号。
- Decision: 用户批准“保留原编码到正式审计字段”。仅sys、异常数据处理在正式creator/updater保留原码；已能关联用户的人员仍保存user_id，原NULL处理沿既有规则。不新建账号、不授权限、不替换为管理员。
- Protection: 专用COM迁移Owner和INT适配器共用这两个明确代码的值域；其他未关联人员仍阻断。运行租户、操作者、执行/关联权限、项目管理权、合同公司资格及17列历史/并发保护保持，普通业务审计规则不改。

### Q-LEGACY-CONTRACT-FORMAL-IDENTITY-20261012-018 — 同一正式关系的当前/历史及审计冲突

- Status: RESOLVED / WHOLE_TABLE_RULE_APPROVED / SCOPED_MIGRATION_REPLAY_AND_BROWSER_VERIFIED / FULL_MIGRATION_PENDING
- Evidence: contract-formal-identity-candidate-audit-20261012T225609.json。按原类型＋同编码最大projectId及旧查询REPLACE历史标记展开83,454条用服候选，得到83,153个项目＋合同候选身份；262组563条多来源，其中156组345条选取业务/审计同值，106组218条不同；95组195条同时有ACTIVE与HISTORICAL，其余11组同状态但审计值不同。以上为来源候选，不替代正式合同唯一匹配、绑定及Owner资格。
- Existing model: com_project_contract_relation复用17列，唯一键仍为租户＋项目＋合同＋RELATED；不能为同一正式身份创建当前/历史副本或绕过唯一性。Q016合同审计主值/成员补缺只决定每行来源取值，不裁决多条合同原记录之间的冲突。
- Proposal: 95组有确切当前成员时以当前来源的正式关系状态及审计承接，历史原ID独立绑定同一关系；未选历史状态及审计逐字段明确不独立迁入原因，不伪造解绑区间、不改变项目生命周期。只有历史来源仍HISTORICAL。其他同状态多来源以最早合同记录的创建人/创建时间、最晚非空来源更新时间及对应更新人承接，逐行仍沿合同为主/成员补缺；时间并列但人员不同阻断，不能按技术ID任选。原未选不同审计列明原因；无明确更新时间仍按已批准创建时间初始化。
- Decision: 用户分别批准“采用当前来源优先规则”“采用最早创建、最近更新规则”。规则适用于整表同类候选，不逐项目裁决；未选状态/审计逐字段明确排除原因，保留来源和每个原ID独立绑定。时间并列且人员不同保持阻断。
- Verification: 2026-10-13已实现跨旧分组的完整身份SQL归并、逐字段处置、逐原ID独立绑定和跨块规范绑定保护；48项单元、4项真实MySQL边界、2项真实DPPMS源查询检查及41模块打包通过。完整来源候选仍为83,454行、83,153个身份、262个多来源组，同刻人员冲突0；来源候选不替代正式资格。新SQL及获批排除已进入指定任务并部署；6480条有正式引用的原记录完成执行和重放，6470个原ID新增独立绑定，共新建6462条正式关系，8条额外原记录共用目标；原25条正式关系、旧项目、旧绑定和回执保持。真实浏览器新项目的普通商务区显示原合同日期、订单、执行单及CRM原编码；范围及证据见[本轮进展](../generated/2026-10-13-dppms-migration-progress.md)。尚无合法项目或合同引用的其他来源保持未完成。
- Boundary: 不新增物理表/列/索引/扩展，不授权覆盖已迁关系或更改项目生命周期，不凭本规则执行全量关系迁入。已验证十项目不受这些源冲突影响；归并实现与其他独立整表任务继续。

### Q-XXXX

- Status:
- Requirement IDs:
- Area:
- Question:
- Why it blocks design/implementation:
- Options:
- Recommended technical default:
- Business decision required:
- Resolution:
- Decision owner:
- Decision date:

## 当前任务业务关联待决事项

### Q-TASK-BUSINESS-AUTO-LINK-001

- Status: RESOLVED（需求方 2026-09-30 裁决归属口径；Owner 接入契约落字与跨会话分工仍须执行，见 Resolution 2026-09-30）
- Requirement IDs: PM-03、PM-11
- Area: 任务业务记录自动归属
- Question: 2026-09-14需求方要求取消手动建立/解除关联并直接展示业务操作页；同项目存在多条业务记录时，哪些记录自动归属当前任务并进入完成判定？
- Evidence: `ProjectTaskBusinessService.getContext/inspectLinkedFactsSnapshot/lockAndRevalidateLinkedFacts`当前读取已保存的活动关联；唯一关系写入来自显式`link`命令。当前工勘样本同时存在草稿和已确认记录，同项目全部纳入会改变完成判定范围。
- Recommended technical default: 按任务冻结的业务范围及Owner明确归属解析，不仅按项目ID把所有同类型记录全部纳入；已有关系与完成历史不自动重写。具体归属规则待需求方确认。
- Blocking scope: 自动关联及依赖它的手动关联区撤除；不阻断任务说明布局、操作区合并和取消工勘详情自动弹窗。
- Decision owner: 需求方；PROJ与相应业务Owner落实契约
- Resolution: 2026-09-14需求方已确认统一方向：所有业务模块共用“业务完成→自动关联对应任务→按任务完成规则自动完成”，无需人工建立/解除关联或点击完成任务。此确认不是同项目全部记录无差别纳入；具体任务/业务范围解析及各Owner接入契约仍须统一落字，暂不关闭本问题。
- Implementation coordination: 本任务11:21后只读复核发现，上一轮结束后同工作树已出现其他来源的`TaskBusinessObjectProvider.lockCompletionFact`、`ProjectTaskBusinessService.lockCompletionFacts`、`ProjectTaskLifecycleService.completeFromBusinessResult`及需求分析Outbox接线（核心文件修改时间11:15～11:19）。现有自动完成仍读取已保存活动关联，且本轮检索仅发现需求分析实现无人值守完成事实接口，不等于统一能力已交付。master相关DU未定位到本增量的当前写入责任；须先明确接管/分工，不能并行改写同一接口或重复建设。
- Resolution 2026-09-30: 需求方裁决——采用A并附加纳入资格限定：同项目同类业务记录中"已确认或审批通过、归档通过"的记录全部纳入任务业务范围与完成判定（统一业务模型应有统一的业务定义），完成事实取最新完成的记录；草稿、已撤销等未定格记录不纳入。既有关系与完成历史不自动重写。各业务Owner接入契约仍须按此口径统一落字，跨会话并行实现的接管/分工按Implementation coordination执行，属实施协调不改变本语义裁决。
- 现状核对 2026-09-30（只读，不改共享接口；契约已落字 docs/design/04a-template-compilation-runtime.md §17）：自动归属唯一写入路径为 `ProjectTaskBusinessAssociationService.reconcile` 按 `associationCandidates` 分页同步。各 Owner 候选口径与裁决差异——SOL 工勘 `SiteSurveyEntityMapper.selectAssociationPage` status IN (0,1,3) 含草稿(0)；SOL 实施方案 `SolutionCompletionMapper.page` status NOT IN (5,6) 含未审批通过状态；ACC 验收报告按项目+类型全量、CUT cut_task 全量，均未按状态限定；PLN 阶段计划仅 status=2 生效批次（符合）。"完成事实取最新完成的记录"当前实现为逐活动关联读取并按规则聚合，未实现最新完成记录选取。以上差异为实施缺口，按 Implementation coordination 的接管/分工处理，本语义裁决不再变更。

## Phase 2历史资料承载决策

### Q-P2-001

- Status: RESOLVED
- Requirement IDs: PRD 3.4、8.2；WO-01～WO-06、WO-08～WO-10（V3演进）；AI-MIG-000
- Area: 历史工单、工时、附件、审批和操作证据的当前承载边界
- Question: V1/V2是否提供历史工单/工时的用户只读查询、导出和附件访问，还是仅由迁移门禁保存不可变归档/来源证据？
- Why it blocks design/implementation: 只有选择用户访问能力时才需要新增正式Requirement、Owner、领域对象、目标表、API、权限和迁移映射；在未批准这些内容前不得以“历史不可删除”反向创建当前产品能力。
- Options: A. 建设V1/V2只读查询/导出；B. 仅由`AI-MIG-000`在批准真实批次内保存不可变来源载荷或受限迁移归档证据，无用户入口。
- Recommended technical default: B；符合已确认的后置/排除优先原则，也避免为尚未识别的真实来源预建空壳对象和访问契约。
- Business decision required: 当前V1/V2不需要新增确认；A属于范围扩展，只有独立PRD/Feature变更获批后才能启动。
- Resolution: 采用B。V1/V2不提供历史工单/工时的菜单、查询、导出、附件访问、领域对象、目标表或迁移映射；`pm_project_maintenance`继续`EXCLUDED/NO_MIGRATION`。真实来源未来只有进入已批准`AI-MIG-000`批次时才保存不可变来源载荷或受限归档证据。若需用户访问，必须独立批准Owner、模型、API、项目/租户数据范围、附件权限、导出审计和来源映射。
- Decision owner: 需求方（当前范围）；真实迁移批次由业务Owner、数据Owner和迁移负责人共同确认
- Decision date: 2026-08-15
- Current-task exception: 本任务已获批的 `pm_project_maintenance` 44 字段正式维护服务历史承接，适用本文件后部“Q-P2-001 — 2026-10-08 本任务维保过程原表 44 字段的明确授权例外”；本段保留原范围裁决，不再作为该已批准批次的建设阻断或重复问询依据。

## Phase 3生产与发布门禁

### Q-P3-001

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: NFR-01、NFR-02、NFR-03
- Area: 生产拓扑与网络信任边界（P3-E01）
- Question: 生产入口、域名/TLS终止、网络区、前后端节点、对象存储、DAC执行区和外部系统流向由哪些平台和Owner承接？
- Why it blocks design/implementation: 不阻断当前逻辑设计；未实例化时不能验证目标环境的14信任边界和18生产部署/切换步骤，因此阻断该环境部署发布。
- Options: A. 由企业现有网关/LB、证书和网络区承接并提交现状拓扑；B. 为NPDMS新建独立入口和网络区并提交新拓扑。
- Recommended technical default: A；优先复用企业已运维平台，NPDMS只登记允许流量和责任边界。
- Business decision required: 是，需技术架构/运维确认平台和Owner。
- Resolution: A；复用企业现有网关/LB、证书和网络区。依据ADR-0018，实际平台、拓扑、流量和Owner在部署阶段由P3-E01确认，不作为当前SDS基线阻断项。
- Decision owner: 需求方（方向）；技术架构、运维（生产证据）
- Decision date: 2026-08-13

### Q-P3-002

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: NFR-01、NFR-03
- Area: MySQL/Redis生产形态（P3-E02）
- Question: 生产MySQL 8.4与Redis 7.4采用托管服务还是自建HA，节点规格、容量和故障切换证据是什么？
- Why it blocks design/implementation: 不能证明容量、数据一致性、缓存降级和发布恢复可执行。
- Options: A. 使用企业托管HA服务；B. 自建HA集群并由DBA/运维维护。
- Recommended technical default: A；如企业无可用托管服务再选B，且必须补齐故障切换与容量验证。
- Business decision required: 是，需DBA/运维确认服务形态和容量。
- Resolution: A；使用企业托管MySQL/Redis HA服务。实际产品、规格、容量和切换证据仍由P3-E02确认。
- Decision owner: 需求方（方向）；DBA、运维（生产证据）
- Decision date: 2026-08-13

### Q-P3-003

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: NFR-01、NFR-02、NFR-03
- Area: 备份恢复目标（P3-E03）
- Question: 平台生产RPO/RTO、备份频率/保留、文件和密钥引用恢复顺序及演练Owner是什么？
- Why it blocks design/implementation: 不阻断SDS基线；未形成恢复设计和演练证据时，阻断恢复验收与生产发布。
- Options: A. 业务Owner先批准RPO/RTO，再由DBA/运维设计并演练；B. 直接采用现有平台默认目标并由业务Owner签署适用性。
- Recommended technical default: A；业务目标先行，避免基础设施默认值与交付业务风险不匹配。
- Business decision required: 是。
- Resolution: A；业务Owner已批准RPO不超过1小时、RTO不超过4小时（ADR-0005），并批准日备35天、月备13个月、年备7年及连续日志策略（ADR-0012）；采用同城温备作为主要恢复路径、离线冷备兜底（ADR-0013）。DBA/运维据此设计并完成隔离恢复演练。
- Decision owner: 需求方（方向）；业务Owner、DBA、运维（目标与证据）
- Decision date: 2026-08-13

### Q-P3-004

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: NFR-02、INT-12
- Area: 凭证密钥托管（P3-E04）
- Question: DeviceCredential使用哪个企业KMS/Secrets Manager或等价密钥托管，轮换、吊销和应急Owner是谁？
- Why it blocks design/implementation: 不阻断当前密钥抽象和安全逻辑设计；具体设施未实例化时设备凭证能力不得部署上线。
- Options: A. 企业KMS/Secrets Manager；B. 应用信封加密且主密钥由独立受控设施托管。
- Recommended technical default: A；只有企业当前无可用KMS时才采用B，并必须实现密钥与数据库分离、版本化和轮换演练。
- Business decision required: 是，需安全/运维确认设施和Owner。
- Resolution: A；使用企业KMS/Secrets Manager。依据ADR-0018，具体设施、访问、轮换、吊销和应急证据在部署阶段由P3-E04确认，不作为当前SDS基线阻断项。
- Decision owner: 需求方（方向）；安全、运维（生产证据）
- Decision date: 2026-08-13

### Q-P3-005

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: NFR-01、NFR-02、NFR-03
- Area: Telemetry与安全事件（P3-E05）
- Question: 日志、指标、Trace、告警和安全事件分别进入哪个企业平台，访问角色、留存和采样如何批准？
- Why it blocks design/implementation: 不阻断17分册设计基线；具体后端未实例化时，阻断可观测验收、高风险审计生产验收与生产发布。
- Options: A. OpenTelemetry统一采集后接入企业现有后端；B. 各信号使用现有独立Agent/平台并通过correlationId关联。
- Recommended technical default: A；若现网平台限制采用B，但必须证明跨信号追溯和权限分离。
- Business decision required: 是。
- Resolution: A；OpenTelemetry统一采集并接入企业现有后端。留存已按ADR-0006～0010分层批准：业务事实/审批历史/明确留痕操作永久不可删除，网络与安全运行日志1年，普通Trace 90天、错误/高风险Trace 180天，原始指标90天、5分钟/小时聚合13个月，调试日志默认7天且专项最长30天。生产Trace采样按ADR-0011批准：普通成功请求10%，错误/高风险/审计失败/发布迁移100%。具体后端、访问角色和告警生产证据仍由P3-E05确认。
- Decision owner: 需求方（方向）；运维、安全、合规Owner（生产证据）
- Decision date: 2026-08-13

### Q-P3-006

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: NFR-01、NFR-03
- Area: 近生产性能环境（P3-E06）
- Question: 是否提供独立近生产性能环境，其规格、数据集、网络、测试账号和外部依赖如何与生产对齐？
- Why it blocks design/implementation: PRD的50并发、规模树、50MB、99%和60秒指标无法形成可信验收。
- Options: A. 建独立近生产环境；B. 使用缩小环境但提供经验证的缩放模型。
- Recommended technical default: A；B只作为资源确实受限时的过渡，且不能替代关键瓶颈的生产同级验证。
- Business decision required: 是，需测试/运维/数据Owner确认资源。
- Resolution: A；建设独立近生产性能环境。实际规格、数据集、网络和账号证据仍由P3-E06确认。
- Decision owner: 需求方（方向）；测试、运维、数据Owner（环境与证据）
- Decision date: 2026-08-13

### Q-P3-007

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: INT-01～INT-12适用项
- Area: 外部接口运行参数（P3-E07）
- Question: 是否按Feature建立接口配置档案，并由每个外部系统技术Owner逐操作确认endpoint引用、认证、白名单、timeout/retry和沙箱契约？
- Why it blocks design/implementation: 没有真实参数不能联调和上线，通用默认值可能造成重复副作用或误判成功。
- Options: A. 每个Feature进入开发前逐接口登记；B. 建平台级统一登记后由Feature引用具体版本。
- Recommended technical default: B；统一注册表治理，Feature引用不可变版本并在上线前验证实际操作。
- Business decision required: 是，需集成架构和外部Owner确认治理方式。
- Resolution: B；建立平台级统一接口配置注册表，Feature引用不可变版本并在上线前验证实际操作。
- Decision owner: 需求方（方向）；集成架构、各外部系统Owner（配置与证据）
- Decision date: 2026-08-13

### Q-P3-008

- Status: DECIDED_EVIDENCE_PENDING
- Requirement IDs: 全部需要历史数据迁移的V1/V2 Requirement
- Area: DDL漂移与迁移基线（P3-E09 / AI-MIG-000）
- Question: 当前DDL相对旧批准证据的差异采用`ACCEPT_CURRENT`、`RESTORE_APPROVED_BASELINE`、`AMEND_CURRENT`还是逐项`DEFER`？
- Why it blocks design/implementation: 历史迁移批次尚未形成，不能执行迁移程序或数据切换；不阻断当前SDS数据模型基线。
- Options: A. 先生成逐表/列/索引/约束差异，由数据架构和业务Owner逐项裁决；B. 整体恢复旧DDL后重新评审新增实体。
- Recommended technical default: A；当前领域模型已经演进，整体恢复可能丢失已确认能力，但未经逐项批准也不能接受当前DDL。
- Business decision required: 是，需批准每项漂移结论；执行程序只读生成差异，不授权生产迁移。
- Resolution: A；继续只读生成逐表/列/索引/约束差异，由数据架构和业务Owner逐项裁决，不整体恢复旧DDL。
- Confirmed naming decision: ADR-0019；业务表删除`pms_`，统一采用`<13领域编码>_<完整领域对象名称>`；表名默认使用完整英文词，仅允许`config`、`sn`两个已登记标准缩写；字段允许使用ADR登记且含义明确的受控缩写，并在无歧义时保持简短。52张表历史命名裁决已同步到当前核心迁移DDL和派生证据，并已纳入P3-E09模型基线。
- Confirmed project identity decision: ADR-0020；同一CRM项目的多合同/多订单不派生项目编码，只有独立交付边界才拆分子项目；项目编码租户内唯一，编码命名空间与可变项目层级分离，项目移动不得改码。该决策已同步DDL、字段目录和P3-E09逐项寄存器，并已纳入模型基线。
- Confirmed customer market classification decision: ADR-0021；市场部、系统部、拓展部、子行业四维分类归CUS，CRM组合目录落`cus_market_relation`；客户和项目直接保存四组编码/名称，禁止保存`relation_id`，也不推断为组织关系。该决策已同步DDL、字段映射和P3-E09逐项寄存器，并已纳入模型基线。
- Confirmed core migration schema decision: ADR-0022；当前DDL是迁移核心子集而非平台全量模型；4张技术公告治理表属于V3设计，不进入V1/V2核心DDL；跨领域使用逻辑引用；外部键映射支持目标角色和稳定顺序；当前唯一性使用生成标记；项目、合同、订单、SN及来源键不可复用；历史异常进入迁移问题并保留逐源证据。该决策已同步核心DDL、领域实体迁移策略、字段目录和P3-E09派生证据，并已纳入模型基线。
- P3-E09 requirement-owner model decisions: ADR-0023中的Q01～Q06业务语义仍有效；交付范围采用“项目节点—订单行当前唯一主记录+多条范围明细”，订单—执行单允许多个默认主执行单关系。当前DDL由ADR-0028历史清单与ADR-0030六表差量共同覆盖：逐项寄存器2,079项中994项`ACCEPT_CURRENT`、1,085项`AMEND_CURRENT`、0项`DEFER`。需求方逐项决策与当前哈希整体一致性复审均已关闭，P3-E09当前为`MODEL_BASELINE_READY`。P3-E09不定义迁移批准哈希，历史迁移或切换继续按Release范围受`AI-MIG-000`阻断。
- V1.7 DDL delta: ADR-0025，并由ADR-0027完成割接物理模型纠偏；10表V1.7差量保留为历史已接受证据。需求方 2026-08-13 确认`pm_project_maintenance`全表不迁移，只保留顶层表级排除审计；当前不预建历史工单/工时对象或空壳表。目录快照对象/表亦按需求方决策删除，INT-05/INT-09复用基础平台主数据、`plt_sync_batch`和`plt_external_key_mapping`。ADR-0030六表加入后当前模型为66表，P3-E09经整体一致性复审恢复为`MODEL_BASELINE_READY`。仅当Release包含历史迁移或数据切换时，`AI-MIG-000`才保持`BLOCKED`直至真实批次验证通过，并只在批准窗口内执行；普通功能Release为`NOT_APPLICABLE`，任何状态均不授权旧`dppms`写入。
- Cutover flow correction: ADR-0026；`CUT-01 / CutoverTask`是P1～P6唯一割接核心任务，`CUT-11`退出当前需求和CUT领域，`WO-06`后置为工单领域V3候选。原CUT-11三表及迁移映射必须从P3-E09候选删除后重新生成证据；该项为已确认变更，不再作为开放问题。
- Cutover physical model correction: ADR-0027；原CUT-11三表已删除，逐步骤执行与稳定观察不进入当前物理模型；P4保障人员安排与P6轻量闭环的物理项保留历史接受证据。ADR-0030新增CUT-03清单三表后当前候选为66表，哈希与隔离MySQL 8.4证据已重建但整体一致性待复审；不得把需求方接受或MySQL执行PASS解释为历史迁移、切换或生产批准。
- Decision owner: 需求方（方向）；数据架构、业务Owner、迁移负责人（逐项裁决与证据）
- Decision date: 2026-08-13

## 九月首发执行信息

以下问题来自`docs/superpowers/plans/2026-08-14-september-uat-go-live-project-plan.md`。它们不改变PRD V1.7业务范围，也不阻断52项首发Feature规格编制；未在最晚安全点关闭时，只阻断对应排期、联调、UAT、迁移或发布活动。

| ID | 待确认信息 | 当前是否必须确认 | 最晚安全点 | 阻断范围 | 需要确认人 | 状态 |
|---|---|---|---|---|---|---|
| Q-REL-001 | 三个交付组的人员名单、角色和投入比例 | 是；进入P1薄切片实施前必须确认 | P1启动 | 实施排期、并行切片承诺 | 项目经理、研发与测试负责人 | OPEN_EXECUTION |
| Q-REL-002 | UAT总负责人及各领域验收代表 | 否；可先编制UAT场景和用例 | P4 UAT准入评审前 | UAT准入、执行与签署 | 业务Owner、项目经理 | OPEN_UAT |
| Q-REL-003 | CRM、基础平台、设备连接与采集平台的联调Owner和可用时间 | 是；可先使用冻结契约和Mock开发，但必须明确真实联调窗口 | P1结束前确认窗口，P2结束前完成真实联调 | 对应Feature联调与发布 | 集成架构、各外部系统Owner | OPEN_INTEGRATION |
| Q-REL-004 | UAT环境和生产环境的运维负责人 | 否；不阻断Feature设计与开发 | P4环境准备开始前 | UAT环境、生产部署与发布 | 运维负责人 | OPEN_ENVIRONMENT |
| Q-REL-005 | 九月底采用全量发布还是限定组织/项目灰度发布 | 否；不阻断Feature设计与开发 | P4发布预演前 | 发布方案、Go/No-Go | 业务Owner、项目经理、运维 | OPEN_RELEASE |
| Q-REL-006 | 首发是否执行历史数据迁移；如执行，明确范围和业务对账人 | 是；Feature开发不受阻，但迁移程序和预演不得启动 | P1结束前确定是否迁移；P4前关闭AI-MIG-000适用批次 | 历史迁移、数据切换、上线准入 | 业务Owner、数据Owner、迁移负责人 | OPEN_MIGRATION |

处理原则：Q-REL-001、Q-REL-003、Q-REL-006是近期排期输入，需要优先确认；Q-REL-002、Q-REL-004、Q-REL-005按表中最晚安全点后置。未确认项不得用虚构人员、环境参数或迁移范围填充，但不影响无关Feature继续推进。

## Feature Ready问题

### Q-FPROJ-001

- Status: RESOLVED
- Requirement IDs: PM-01、PM-03
- Area: F-PROJ-001手动项目创建与模板初始化
- Question: PRD要求“无可用模板时项目保持创建草稿且不得进入S0”，但当前SDS只定义正式Project创建为`ACTIVE / S0`。创建草稿应采用独立`ProjectCreationDraft`聚合、给Project新增`DRAFT`状态，还是不持久化表单？
- Why it blocks design/implementation: 该选择会改变草稿业务身份、状态机、API和数据库；现已由需求方确认方案B并解除阻断。
- Options: A. 独立`ProjectCreationDraft`，提交后原子生成正式Project并保留草稿审计引用；B. 校验失败不持久化，只保留客户端表单；C. 给Project新增`DRAFT`生命周期。
- Recommended technical default: 历史推荐为A；需求方最终选择B，以避免新增草稿业务对象和Project状态。
- Business decision required: 已完成。方案B：失败时不持久化Project或创建草稿；当前页面可保留内存表单供修正，刷新后不保证恢复。
- Resolution: 方案B。批准依据为`CHG-PRD-2026-08-21-001`；不新增ProjectCreationDraft、Project DRAFT状态、草稿API、表或迁移。
- Blocking scope: 已解除。F-PROJ-001按失败无持久化语义继续Feature Spec和Technical Plan。
- Decision owner: 需求方（业务语义）；PROJ领域和数据架构负责人（SDS/物理契约回写）
- Decision date: 2026-08-21

### Q-FPROJ-002

- Status: RESOLVED
- Requirement IDs: PM-03、ACC-04
- Area: F-PROJ-001手动项目创建与模板初始化
- Question: PM-03要求项目创建时按模板加载交付件，但SDS明确交付件事实归ACC Context拥有、PROJ不得直接访问ACC Repository，且跨Context契约默认最终一致。项目创建成功是否必须等待ACC交付件实例全部落地？
- Why it blocks design/implementation: 该选择会改变`POST /projects`成功语义、事务/Outbox边界、初始化状态、重试补偿和AC-FPROJ-002/008/010的验收口径；现已由需求方确认同步全有或全无并解除阻断。
- Options: A. PROJ先提交并由ACC按Outbox事件最终一致初始化，存在`PENDING`；B. 同步编排但允许超时后保持可恢复处理中；C. 只冻结要求快照，进入阶段时再创建；D. 需求方选择：PROJ同步调用ACC内部应用接口，双方同库同Spring事务，要么全部提交，要么全部回滚，不产生中间状态。
- Recommended technical default: 历史推荐为A；需求方最终选择D，以项目创建完整性优先于跨库拆分和异步可用性。
- Business decision required: 已完成。创建成功必须同时证明ACC交付件实例全部落地；任一步失败则整体回滚。
- Resolution: 方案D，详见ADR-0032。PROJ不得直接访问ACC Repository；ACC内部应用接口必须参与调用方同一数据库事务，不得使用`REQUIRES_NEW`、异步消息、Saga、初始化`PENDING`或吞异常降级。
- Blocking scope: 已解除。F-PROJ-001按同步全有或全无语义继续Technical Plan；未来拆库/拆服务必须先批准业务语义变更。
- Decision owner: 需求方（创建完成语义和用户效果）；PROJ、ACC领域负责人（契约和补偿）
- Decision date: 2026-08-21

### Q-FCUS-001

- Status: RESOLVED
- Requirement IDs: CUS-03、INT-03
- Area: F-CUS-001 CRM字段级写入与本地物理契约
- Question: CRM权威字段、平台扩展字段、内部写API、来源版本、当前CRM映射唯一键、客户主表列约束和字段映射如何冻结？
- Why it blocks design/implementation: 已完成业务与架构决策并回写SDS/Feature，不再阻断。
- Options: A. CUS公开应用接口；B. 消费集成事件；C. 集成模块直写表。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: INT-03只调用`CustomerMasterDataApi`，不得直接写CUS表；CRM权威字段采用PRD明确范围，四维市场属性保存编码/名称；按`tenantId + crmCustomerId + sourceVersion`判定新旧，`eventId`防重复，同版本不同载荷进入冲突；客户编码软删除后仍占用；核心字段主表分列，来源映射、字段历史和同步快照独立追加保存。
- Decision owner: 需求方；CUS、集成与数据架构负责人
- Decision date: 2026-08-25

### Q-FCUS-002

- Status: RESOLVED
- Requirement IDs: CUS-03、EQP-01、PM-01
- Area: F-CUS-001跨域引用守卫与客户地点命令
- Question: 删除客户前哪些Owner参与引用守卫；客户Address/Site引用如何维护？
- Why it blocks design/implementation: 已完成业务与架构决策并回写SDS/Feature，不再阻断。
- Options: A. 统一批量引用守卫；B. 各域定制；C. CUS本地投影。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: 删除前检查项目、设备、联系人、领域任务和外部问题记录等全部有效业务引用；任一守卫未知、超时或不可用时失败关闭。各Owner实现统一`CustomerReferenceGuardApi`批量契约并返回类型、数量和最小摘要，CUS不得跨域查表。CUS只保存Address/Site稳定ID、类型、来源版本和有效区间，写引用前调用`AssetLocationApi`校验租户、对象类型、存在性和版本。
- Decision owner: 需求方；CUS、PROJ、AST及相关领域负责人
- Decision date: 2026-08-25

### Q-FCUS-003

- Status: RESOLVED
- Requirement IDs: CUS-03
- Area: F-CUS-001独立customer模块迁移与权限契约
- Question: 旧客户实现如何迁移，新API、API模块和权限如何冻结？
- Why it blocks design/implementation: 已完成业务与架构决策并回写SDS/Feature，不再阻断。
- Options: A. 一次性切换Owner；B. 短期双写；C. 长期兼容。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: 新建`pms-module-customer`和稳定`pms-module-customer-api`；以前向迁移一次性切换Owner，不设双写期。旧客户API立即退出，新路径固定为`/api/v1/pms/customers`。联系方式默认脱敏，具备敏感查看权限且命中数据范围时详情可见明文，导出另需权限并审计。权限拆分为查询、创建、更新、删除、恢复、敏感查看和导出。
- Decision owner: 需求方；CUS、PROJ、数据与权限负责人
- Decision date: 2026-08-25

### Q-FAST-001

- Status: RESOLVED
- Requirement IDs: EQP-01、EQP-04、INT-02、INT-04
- Area: F-AST-001官网信息与V1外部来源交付边界
- Question: 官网信息Owner、维护方式及外部同步未完成时的验收边界如何冻结？
- Why it blocks design/implementation: 已完成业务与架构决策并回写SDS/Feature，不再阻断。
- Options: A. KNO受控维护；B. 自动采集；C. MES提供。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: 官网信息归KNO，由授权人员受控维护来源URL、核验时间、摘要和版本，V1不建设自动爬取。AST通过KNO公开查询契约按产品/设备映射读取已发布版本，无记录显示未维护。`INT-02`、`INT-04`和`EQP-04`保持独立Feature；其未完成时F-AST-001允许使用已核验种子或受控替身验收主档消费和降级，但不得宣称外部同步完成。
- Decision owner: 需求方；AST、KNO与集成负责人
- Decision date: 2026-08-25

### Q-FAST-002

- Status: RESOLVED
- Requirement IDs: EQP-01、CUS-03
- Area: F-AST-001客户直接归属时态写契约
- Question: 设备客户归属Owner、命令、状态守卫和物理模型如何冻结？
- Why it blocks design/implementation: 已完成业务与架构决策并回写SDS/Feature，不再阻断。
- Options: A. AST单一Owner；B. CUS维护；C. 跟随项目。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: AST是设备当前客户直接归属、时态历史及租用/共管关系的单一Owner；稳定命令为`POST /api/v1/pms/devices/{id}/actions/assign-customer`。停用客户禁止新归属，已有关系与历史保留并进入待核对。物理模型采用当前唯一表加统一时态关系表；项目当前客户与设备当前客户不一致时保留双方事实并创建待核对项，不自动覆盖。
- Decision owner: 需求方；AST、CUS与数据架构负责人
- Decision date: 2026-08-25

### Q-FAST-003

- Status: RESOLVED
- Requirement IDs: EQP-01、EQP-02
- Area: F-AST-001字段级API、权限与机器物理契约
- Question: 详情DTO、权限、下载、来源状态及物理模型如何冻结？
- Why it blocks design/implementation: 已完成业务与架构决策并回写SDS/Feature，不再阻断。
- Options: A. 固定外壳和分Tab DTO；B. 单一大DTO；C. 动态Map。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: 设备详情采用固定摘要外壳和分Tab DTO，各切片统一返回`sourceSystem/sourceVersion/dataAsOf/syncStatus`。沿用现有资产查询/维护权限，仅新增项目归属、客户归属、冲突处置和配置Log下载高风险权限；Log查看同时校验设备查询和文件查看权限。下载链接默认5分钟、可配置并绑定用户。来源状态统一为`FRESH/STALE/FAILED/PENDING_MAPPING/NOT_AVAILABLE`。物理模型采用Device主表加MES/ITR/KNO等分来源表；序列号软删除后仍占用并沿用原deviceId恢复。
- Decision owner: 需求方；AST、KNO、权限与数据架构负责人
- Decision date: 2026-08-25

## PRD V1.8修订005待裁决项

### Q-PRD-005-01

- Status: RESOLVED
- Requirement IDs: PRE-04、SCH-01
- Area: 需求分析到实施方案的字段级章节映射
- Question: PRE-04的冗余需求、安全防护需求、运维需求和日志需求，应分别自动预填到SCH-01哪一个正式章节或结构化字段？
- Why it blocks design/implementation: 已由需求方裁决关闭，不再阻断设计与实现。
- Options: A. 需求方指定四类字段到现有SCH-01章节的逐项映射；B. 批准SCH-01新增明确章节/字段并定义版本、展示和验收规则；C. 明确四类字段只作为PRE-04版本附件/引用展示，不自动预填正文。
- Recommended technical default: 在需求方裁决前不执行这四类字段的自动章节映射；项目背景、项目目标、网络拓扑和IP规划继续按PRD已明确规则引用。
- Business decision required: 已完成。
- Resolution: 冗余、安全防护、运维、日志四类字段不要求全部映射到实施方案；仅按实施方案模板中已建立的字段级显式对应关系选择性预填，未映射字段不写入SCH-01，也不作为方案缺失项，继续保留在PRE-04版本中查询与追溯。
- Blocking scope: 已解除。
- Decision owner: 需求方；PRE/SOL领域负责人参与影响分析
- Decision date: 2026-08-29

## PRD V1.8 Requirement版本切片裁决记录

> 来源：`docs/reports/2026-08-29-PRD-V1.8-Requirement版本切片需求方裁决清单.md`。原8项阻断对应VS-001、VS-002、VS-003、VS-005、VS-006、VS-007、VS-008、VS-009，均已由需求方于2026-08-29裁决；本节按实际VS编号保留问题、选项和最终Resolution。VS-004、VS-010、VS-011属于审查中的补充边界确认，不曾作为规格阻断，完整结论保留在裁决清单和PRD修订007中。

### Q-PRD-VS-001

- Status: RESOLVED
- Requirement IDs: PM-08
- Area: V2服务经理自动指派
- Question: V2应直接生成并生效唯一主责指派，还是仅生成候选建议并继续由授权人员确认？
- Why it blocks design/implementation: 两种结果的责任生效时间、权限、异常状态和验收完全不同，现有“建议或结果”不能派生唯一Feature。
- Options: A. 唯一匹配自动生效，异常转人工；B. 只生成建议，始终人工确认；C. V2方向移至V3。
- Recommended technical default: A；符合“自动指派”名称，同时对无唯一结果保持失败关闭。
- Business decision required: 是，对应裁决VS-001。
- Resolution: 采用A。V2按已冻结并生效的匹配规则自动形成唯一主责指派；仅在唯一候选匹配时指派自动生效。无匹配或多匹配时不得自动选定，项目保持“待指派”，转由有权人员人工处理。
- Decision owner: 需求方；PROJ与组织权限Owner参与影响分析
- Decision date: 2026-08-29

### Q-PRD-VS-002

- Status: RESOLVED
- Requirement IDs: PM-11
- Area: V2甘特图与高级编排
- Question: “高级编排”具体包含哪些业务动作、依赖规则、状态影响、权限和验收？
- Why it blocks design/implementation: 当前只有方向性名称，无法判断甘特展示、依赖维护与其他编排能力是否属于同一可验收结果。
- Options: A. V2仅甘特展示和受控依赖维护；B. 补齐完整高级编排规格；C. 整体移至V3。
- Recommended technical default: A；先形成最小可验收业务结果，其余能力另行评审。
- Business decision required: 是，对应裁决VS-002。
- Resolution: 采用A。PM-11 V2仅交付甘特展示和受控依赖维护；依赖新增、更新、删除必须校验循环引用、跨项目非法关系和版本冲突。其他“高级编排”不属于当前V2承诺，须以明确Requirement重新评审。
- Decision owner: 需求方；PROJ Owner参与影响分析
- Decision date: 2026-08-29

### Q-PRD-VS-003

- Status: RESOLVED
- Requirement IDs: EXE-05
- Area: 单机风险V2自动识别、升级和分析
- Question: Q-15中的V2方向是否仍为当前正式承诺；若保留，其触发、规则、状态、权限和验收是什么？
- Why it blocks design/implementation: EXE-05正文和A.1只定义V1台账，无法为V2派生合法Feature与完成条件。
- Options: A. 转V3；B. 本次补齐V2规格；C. 删除该方向。
- Recommended technical default: A；避免未定义自动化进入V2承诺。
- Business decision required: 是，对应裁决VS-003。
- Resolution: 采用A。EXE-05保留V1风险台账与现有协同闭环；自动识别、自动升级和分析不进入V2，作为V3跨Requirement演进方向，重新启动前须补齐规则、权限和验收。
- Decision owner: 需求方；IMP与CRM集成Owner参与影响分析
- Decision date: 2026-08-29

### Q-PRD-VS-005

- Status: RESOLVED
- Requirement IDs: ACC-02、INT-05、INT-10
- Area: 满意度V1核心与V2增强边界
- Question: ACC-02的V2结果是否仅为自动触达，还是另有问卷、评分、整改重收或导出差量？
- Why it blocks design/implementation: 现有V1已完整包含后四类能力，§13.2的同名V2“增强”没有差量，机械拆分会重复完成事实。
- Options: A. V2仅自动触达；B. 补齐其他真实差量；C. 删除ACC-02 V2描述。
- Recommended technical default: A；保留V1完整业务事实，V2只增加可降级通道。
- Business decision required: 是，对应裁决VS-005。
- Resolution: 采用A。ACC-02 V1保留问卷模板、实例、评分判定、整改重收、客户签字和授权导出的完整核心闭环；ACC-02 V2只增加短信/邮件和钉钉自动触达，失败时回退二维码、外发链接或现场协助，不重复定义核心问卷事实。
- Decision owner: 需求方；ACC与通知集成Owner参与影响分析
- Decision date: 2026-08-29

### Q-PRD-VS-006

- Status: RESOLVED
- Requirement IDs: CUT-06
- Area: V2割接多角色填写
- Question: V2要由哪些角色分别填写哪些字段，字段Owner、提交顺序、冲突规则和最终归档权限是什么？
- Why it blocks design/implementation: PRD明确要求另行确认，当前无法建立字段、权限、状态和验收契约。
- Options: A. 转V3；B. 本次补齐并保留V2；C. 删除多角色方向。
- Recommended technical default: A；V1一线统一填写已形成完整闭环。
- Business decision required: 是，对应裁决VS-006。
- Resolution: 采用A。CUT-06仅保留V1一线统一填写、闭环、归档和经定义的ITR结果出向；多角色分工填写不进入V2，转为V3跨Requirement演进方向，重新启动前须明确字段Owner、角色权限、提交顺序和冲突规则。
- Decision owner: 需求方；CUT与权限Owner参与影响分析
- Decision date: 2026-08-29

### Q-PRD-VS-007

- Status: RESOLVED
- Requirement IDs: INT-02、INT-04、EQP-07、CUT-06
- Area: ITR入向/出向与跨版本Owner
- Question: INT-02@V1、INT-04@V2、EQP-07@V2和CUT-06的公告、故障、割接触发及结果回传责任应如何唯一分配？
- Why it blocks design/implementation: 当前INT-02@V1验收V2公告和问题单结果，并混入未定义出向，造成版本倒挂和重复Owner。
- Options: A. INT-02@V1保留版本/割接入向，INT-04@V2拥有公告，INT-02@V2拥有故障入向及定义后的CUT出向；B. INT-02仅V1，其余归消费需求；C. INT-02整体移V2。
- Recommended technical default: A；按事件类型和消费版本形成可独立验收结果。
- Business decision required: 是，对应裁决VS-007。
- Resolution: 采用A。INT-02@V1只承接设备版本历史和ITR割接任务入向；INT-04@V2唯一拥有技术公告完整同步；INT-02@V2承接故障入向供EQP-07消费，并承接PRD已定义的CUT结果出向。出向失败不回滚本地CUT归档，进入重试与对账。
- Decision owner: 需求方；AST、KNO、CUT与集成Owner共同裁决
- Decision date: 2026-08-29

### Q-PRD-VS-008

- Status: RESOLVED
- Requirement IDs: INT-03、CUS-03
- Area: CRM客户同步V2治理
- Question: INT-03 V2“扩展同步治理、对账和冲突处理工作台”相对V1已有幂等、冲突、重试和对账具体新增什么？
- Why it blocks design/implementation: 没有新增动作和验收，无法判断是否存在独立V2业务结果。
- Options: A. 只保留V1，工作台转V3；B. 本次补齐V2批量治理规则；C. 将V1部分治理义务后移V2。
- Recommended technical default: A；不因页面形态重复创建业务切片。
- Business decision required: 是，对应裁决VS-008。
- Resolution: 采用A。INT-03只保留V1客户同步、幂等、冲突、重试和对账；未定义的同步治理工作台不形成V2切片，作为V3跨Requirement演进方向重新评审。
- Decision owner: 需求方；CUS与CRM集成Owner参与影响分析
- Decision date: 2026-08-29

### Q-PRD-VS-009

- Status: RESOLVED
- Requirement IDs: NFR-02、INS-02、INS-03
- Area: 巡检命令超时后的后续命令策略
- Question: 当前命令超时后，剩余命令应一律继续、一律停止，还是按已发布巡检规则决定？
- Why it blocks design/implementation: NFR-02规则与验收互相冲突，会产生不同设备动作、安全结果和测试判定。
- Options: A. 按已发布巡检规则决定；B. 一律继续；C. 一律停止。
- Recommended technical default: A；与规则Owner和不同设备场景相符。
- Business decision required: 是，对应裁决VS-009。
- Resolution: 采用A。巡检命令超时后，当前命令必须终止并标记失败；剩余命令是否继续严格按任务冻结的已发布巡检规则决定，并记录采用的规则版本、决定和执行结果。
- Decision owner: 需求方；SRV、PLT采集与安全Owner共同裁决
- Decision date: 2026-08-29

## F-CUT-001 Feature Ready 待裁决项

### Q-FCUT001-001

- Status: RESOLVED
- Requirement IDs: CUT-07、CUT-09
- Area: 双机部署规范性检查项权威清单与发布数量校验
- Question: 参考附件中的双机检查项内容和行数是否参与业务口径裁决或Feature Ready判断？
- Why it blocks design/implementation: 已由需求方明确关闭；参考附件只辅助理解名称、字段与界面，不拥有业务语义或验收口径。
- Options: A. 仅作参考；B. 作为第二事实源参与裁决；C. 以附件覆盖正式需求。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: 采用A。PRD、SDS和Feature Spec决定业务语义与验收；XLSX/HTML可以查看和引用为实现参考，但其内容、数量或相互差异不形成需求裁决、不建立新的发布门禁，也不阻断Feature。CUT-07实现通用CRUD、版本和发布校验能力，初始化只落正式需求明确定义的字典及示例组合，不把附件行数硬编码为业务规则。
- Blocking scope: 已解除。
- Decision owner: 需求方；CUT领域负责人、测试负责人参与影响分析
- Decision date: 2026-08-29

### Q-FCUT001-002

- Status: RESOLVED
- Requirement IDs: CUT-07、CUT-04
- Area: 割接方案模板章节配置的领域与物理载体
- Question: CUT-07要求“按割接类型/级别配置方案模板章节”，应由哪个版本化对象和物理表承载？
- Why it blocks design/implementation: 已通过不增加Owner表的物理细化关闭，不改变PRD业务语义或ADR三表边界。
- Options: A. 扩展ADR/SDS，为CUT-07增加版本化方案模板章节子对象和独立物理表；B. 明确方案模板章节配置不属于CUT-07当前V1切片并修订PRD，由后续CUT-04 Feature单独拥有；C. 批准将章节Schema作为`CutoverConfigurationRevision`根内的结构化版本快照，并补齐字段、校验和消费契约。
- Recommended technical default: C。
- Business decision required: 已完成。
- Resolution: 采用C。方案模板章节以`CutoverConfigurationRevision.plan_template_section_snapshot`结构化JSON承载，随配置根统一草稿、发布、复制修订和停用；每章包含稳定章节键、标题、排序、适用割接类型代码、适用等级代码和必填标识。CUT-04只冻结所消费配置revision及章节快照，不把运行方案写回配置根。保持ADR-0031批准的三表模型，不新增第四张Owner表。
- Blocking scope: 已解除。
- Decision owner: 需求方；CUT领域负责人、数据架构负责人
- Decision date: 2026-08-29

### Q-FCUT001-003

- Status: RESOLVED
- Requirement IDs: CUT-03、CUT-07、CUT-09
- Area: 五类双机部署规范性检查发布与验收数量
- Question: PRD原“5类124行”与逐类17/25/23/24/8合计97项不一致时，当前正式发布基准是什么？
- Why it blocks design/implementation: CUT-09要求逐类精确数量校验，两个数值不能同时成立，且参考附件不得参与裁决。
- Options: A. 逐类合计97项；B. 总数124项并补充缺失27项；C. 取消数量校验。
- Recommended technical default: A。
- Business decision required: 已完成。
- Resolution: 采用A。VSM双机17项、静默双机25项、DRP双机23项、普通双机24项、集群8项，合计97项；原124不再作为CUT-07/CUT-09发布或验收数量。关于124历史组成的推测不产生业务规则。已形成`CHG-PRD-2026-08-30-008`。
- Blocking scope: 已解除。
- Decision owner: 需求方；CUT领域负责人、测试负责人参与影响分析
- Decision date: 2026-08-30

### Q-FCUT001-004

- Status: REJECTED
- Requirement IDs: CUT-07、CUT-09
- Area: 五类双机部署规范性检查正式初始化名称与说明
- Question: 正式发布数量已固定为VSM双机17项、静默双机25项、DRP双机23项、普通双机24项、集群8项，合计97项；但当前本地参考材料仅有15/24/21/23/8项具名明细，缺少`VSM 016-017`、`SILENT_DUAL 025`、`DRP_DUAL 022-023`、`NORMAL_DUAL 024`六项的正式业务名称与说明。完整正式初始化应采用哪六项名称与说明？
- Why it blocks design/implementation: 不阻断。该问题把参考材料的缺名误当成正式需求缺失，违反AC-FCUT001-015和已关闭的`Q-FCUT001-001`；参考材料不得参与需求、不一致、完成或阻断裁决。
- Options: A. 按正式PRD、SDS和Feature Spec实施并验收97项CRUD、分类计数和发布规则，参考材料仅用于已有名称与界面参考；B. 以参考材料反向要求补齐业务名称并阻断Feature。
- Recommended technical default: A；不创建缺少正式业务定义的生产占位种子，隔离验收数据可以使用明确标识的测试名称验证97项能力。
- Business decision required: 已完成；需求方再次确认参考材料不参与任何裁决或阻断。
- Resolution: 采用A，拒绝B。撤销`BLOCKED_BY_SPEC`；不再解析参考材料形成阻断项，也不因其缺名、缺行或数量差异改变Feature状态。生产初始化继续遵守`Q-FCUT001-001`，只落正式需求明确定义的字典和示例组合；97项完整能力使用隔离验收数据验证。
- Blocking scope: 无。
- Decision owner: 需求方；CUT领域负责人、测试负责人参与影响分析
- Decision date: 2026-08-30

## F-INS-001 裁决与实施阻断

### Q-FINS001-001

- Status: RESOLVED
- Requirement IDs: INS-02、INS-03、INS-09、NFR-02
- Area: 在线巡检单命令超时上限
- Resolution: 采用1～30秒硬上限，超过30秒拒绝；master统一引用`CHG-PRD-2026-09-02-011`，来源分支修订009只作历史证据。
- Decision owner: 需求方；SRV、采集与安全Owner参与影响分析
- Decision date: 2026-09-02

### Q-FINS001-002

- Status: RESOLVED
- Requirement IDs: EQP-01、INS-03、INS-09
- Area: AST产品类型公开查询外部依赖
- Resolution: Inspection只消费F-AST-002公开产品类型契约并保存稳定编码与发布名称快照，不使用`conpType`、旧字典或自由文本替代；master统一引用`CHG-PRD-2026-09-02-011`。
- Decision owner: 需求方；AST Owner、SRV/Inspection消费方
- Decision date: 2026-09-02

### Q-FINS001-003

- Status: RESOLVED
- Requirement IDs: INS-03、INS-09、NFR-02
- Area: 受控字典值、JDK正则子集与秘密扫描
- Resolution: 正式SDS冻结检测分类/严重度机器码、JDK 25受限正则预算、秘密模式和稳定字段错误码；master统一引用`CHG-PRD-2026-09-02-011`。
- Decision owner: 需求方、安全Owner、SRV Owner
- Decision date: 2026-09-02

### Q-FINS001-004

- Status: RESOLVED
- Requirement IDs: INS-03、INS-09
- Area: 巡检规则名称唯一性
- Resolution: 规则名称归属稳定身份并在租户内永久唯一；停用、软删除和新revision不释放名称，历史revision不可改名；master统一引用`CHG-PRD-2026-09-02-011`。
- Decision owner: 需求方、SRV Owner
- Decision date: 2026-09-02

### Q-FINS001-005

- Status: RESOLVED
- Requirement IDs: INS-03、INS-09
- Area: 同一revision与内容摘要的多次安全审核生效语义
- Question: 同一revision和内容摘要存在多条`PASSED/REJECTED`审核事实时，哪条事实决定发布资格，后续结论是否撤销或覆盖既有结论？
- Why it blocks design/implementation: 当前模型允许追加多条审核事实；选择“存在通过”或“最新结论”都会改变发布业务结果与审计解释，Implementation不得静默决定。
- Recommended technical default: 按`reviewed_at DESC, id DESC`最后一条事实为当前结论；内容变化或新revision必须重新审核，历史事实不覆盖。
- Resolution: 需求方于2026-09-02确认采用方案A，master以`CHG-PRD-2026-09-02-012`冻结：审核事实只追加，仅`DRAFT`可追加；同租户、同revision、同摘要按`reviewed_at DESC, id DESC`最后事实生效，最后`PASSED`允许发布、最后`REJECTED`阻断；摘要变化或新revision必须重审，权限撤销不追溯改写历史，需要撤销时追加`REJECTED`；审核与发布共享聚合锁/CAS，发布后纠正走新草稿revision。
- Blocking scope: 已解除审核事实选择语义阻断；`Q-FINS001-006`随后由master修订013关闭，当前剩余Task 8实现与验收缺口。
- Decision owner: 需求方、安全Owner、SRV Owner
- Decision date: 2026-09-02

### Q-FINS001-006

- Status: RESOLVED
- Requirement IDs: INS-03、INS-09
- Area: 巡检安全审核复用System现有布尔权限判定
- Question: 安全审核是否必须新增显式角色—菜单来源事实接口，还是复用现有布尔权限接口，并如何处理超级管理员、租户访问`skip`及`authorizationSourceId`？
- Why it blocks design/implementation: 生产审核入口必须区分租户访问上下文切换阶段的`skipPermissionCheck`与切换后的目标租户全新权限判定，同时确认超级管理员是否属于合法审核主体；否则Task 8无法确定守卫和审计合同。
- Recommended technical default: 已由需求方裁决覆盖；不得继续采用新增System显式事实API且排除超级管理员的来源方案。
- Resolution: 需求方于2026-09-02确认支持超级管理员，并认定System现有布尔权限接口满足需求。master以`CHG-PRD-2026-09-02-013`冻结：租户访问拦截器先完成目标租户上下文切换，Inspection再以当前审核人直接调用`PermissionApi.hasAnyPermissions(actorId, "pms:inspection-rule:security-review")`执行目标租户全新判定；显式角色—菜单授权或System超级管理员任一返回`true`均可审核。`SecurityFrameworkService`的`skipPermissionCheck`只服务租户切换，不能单独作为审核结论。无用户、无租户、返回`false`或System异常时失败关闭且不追加事实。审核记录使用`authorizationType=RBAC_PERMISSION`，现有布尔结果不提供贡献路径且可能来自超级管理员，故`authorizationSourceId`为空，不伪造角色—菜单来源。不新增或修改Yudao System接口、Mapper、表或权限语义。
- Blocking scope: 规格阻断已解除；允许从最新master新建DU实施Task 8的直接`PermissionApi`守卫、最后事实查询、审核入口和完整发布。现有`InspectionRuleExplicitAuthorizationApi`合同已被替代，必须在该实现切片删除或收口；Feature仍须完成代码、MySQL、浏览器和DoD验证后才能Done。
- Decision owner: 需求方、安全Owner、平台权限Owner
- Decision date: 2026-09-02

## F-PROJ-008 实施阻断

### Q-FPROJ-009

- Status: RESOLVED_BUSINESS / IMPLEMENTATION_REVALIDATION_REQUIRED（修订019替代原固定任务设计）
- Requirement IDs: PM-01@V1、PM-03@V1、PM-09@V2
- Area: 新建项目首次项目经理指派与S0准出
- Question: 新建项目尚无`PROJECT_MANAGER`时，应由哪个合法主体、通过哪个唯一命令完成首次项目经理指派，并与`T-ASSIGN-PM`完成及`assignment_status`转换形成一致业务结果？
- Why it blocks design/implementation: S0准出要求`T-ASSIGN-PM`完成，但现有`TASK_NATIVE COMPLETE`只允许当前项目经理执行；项目创建和现有公开指派命令只形成`SERVICE_MANAGER_L1/L2`，没有V1首次项目经理写入入口，形成循环依赖。
- Options: A. 当前一级服务经理通过`T-ASSIGN-PM`特殊完成命令选择目标项目经理，成员事实与任务同事务形成；B. 当前一级服务经理先调用独立项目经理确认命令，再由正式项目经理完成`T-ASSIGN-PM`。
- Recommended technical default: A；任务本身即首次项目经理指派业务动作，可避免第二条命令和中间状态，但必须冻结操作者、权限、项目范围、SYSTEM人员及组织Owner校验。
- Business decision required: 人员角色和业务归属已由PRD修订011及当前016的PM-01规则6/9明确；本轮不增加特殊审批人或改变通用Task完成权限。
- Historical resolution（2026-09-07，不再授权新实现）: 当时采用PM-01首次指派与T-ASSIGN-PM完成事实同事务的业务对象绑定方案；上列原问题、选项和建议仅保留历史，已被修订019替代，不能继续要求固定任务或唯一项目经理。
- Resolution（2026-09-08）: 用户明确成员操作是项目级能力，服务经理与项目经理可同次或先服务经理后单独指派；各阶段有相应权限即可调整。一个项目可有多个项目经理，同时保留一个当前主责；同一项目角色的操作权限相同，主责标识不附加权限。指派不创建/完成固定任务，不修改阶段、生命周期或冻结审批历史。正式依据为CHG-PRD-2026-09-08-019；仅候选范围另见Q-FPROJ-010，不能恢复单manager_id通用授权。既有Task 1/2和历史Done不回退，本结论不证明新接口或S0→S1已完成。
- Blocking scope: 只阻断F-PROJ-008 Task 3新建项目S0→S1正向Chromium链；已集成Task 1/2及不依赖该问题的工作台能力不回退。
- Decision owner: 需求方；PROJ、SYSTEM与权限Owner参与影响分析
- Decision date: 2026-09-07原设计；2026-09-08用户以修订019明确替代

### Q-FPROJ-010

- Status: RESOLVED_BUSINESS / IMPLEMENTATION_REVALIDATION_REQUIRED
- Requirement IDs: PM-01@V1；直接消费者PM-03@V1、PM-11@V1、PM-08@V1
- Area: 项目经理候选人员组织范围
- Confirmed: 项目级联合或分次指派、各阶段按权限调整、多名项目经理及一个当前主责均已确认；用户进一步确认“相同项目角色的操作权限相同”，主责与其他有效PROJECT_MANAGER不因主责标识而权限不同。权限问题已解决，不再列为阻断或重复询问。
- Question: 项目经理的候选人员允许来自哪些组织范围？
- Historical gap: 原PM-01未明确候选公司/部门范围，不能由PM-08规则外推；该业务缺口现已关闭，旧描述不再阻断。
- Resolution: 用户确认同公司、允许跨部门的有效在职人员，并要求上游提供按指定公司、指定角色查询用户有效授权的通用接口。SYSTEM拥有资格事实；PROJ传目标公司的稳定ID和所需角色，候选及提交重验共享同一规则，不以既有项目成员关系查询候选、不把全局角色与其他公司的范围拼接成组合授权。接口细则见SDS10，不建立PM专用权限真值。
- No inferred default: 不限制同办事处，不新增协同经理角色，不自动选择第一人为主责；同角色同权限保持已确认。
- Blocking scope: 无剩余业务裁决；具体接口/消费者实现与适用验证仍须完成，不从问题关闭推定Feature Ready/Done。
- Decision owner: 需求方；PROJ/SYSTEM及实际消费者参与设计
- Decision date: 2026-09-08（当前任务用户确认并补充通用接口要求）

### Q-FPROJ-011

- Status: OPEN / BLOCKED_BY_SPEC
- Requirement IDs: PM-01@V1、PM-08@V1；项目数据范围直接消费者
- Area: 新建未指派项目的首次指派授权来源
- Evidence: 2026-09-08固定测试库真实页面创建项目992203060002，创建者1只有VIEW；新候选接口被MANAGE范围校验拒绝（1014024033）。ProjectTreeScopeService仅从有效项目经理/服务经理成员或显式授权产生MANAGE，创建者只获得VIEW；ProjectAuthorizationGuard.assertCanAssign要求MANAGE。不能因具备功能权限就静默授予项目范围。
- Question: 对尚未完成双主责指派且没有现成MANAGE主体的新项目，谁可以完成首次服务经理/项目经理指派，数据范围从何取得？
- Recommendation（未批准、未实现）: 允许同时具备指派功能权限的项目创建者，在双主责未齐期间完成首次指派；保留受信租户、项目创建者/版本/生命周期及候选资格校验，不产生永久MANAGE授权，不扩大其他项目操作范围。是否采用由需求方裁决。
- Blocking scope: 仅阻断无MANAGE的新建未指派项目首次指派闭环；已有合法管理范围的联合/分次指派、增补、移除、主责切换已取得真实接口与浏览器证据。模板/阶段内业务不由本Q作额外准入。
- Decision owner: 需求方；PROJ负责落实已确认授权边界。

## 并行分支权威收敛阻断

### Q-GOV-20260901-001

- Status: CLOSED
- Requirement IDs: ACC-02、EQP-01、INS-02、INS-03、INS-09、NFR-02、PM-03
- Area: 并行PRD Change ID唯一性与master串行收敛
- Question: `CHG-PRD-2026-08-30-010`在ACC分支表示“ACC-02可配置问卷基础”，在INS/AST分支表示“EQP-01设备产品类型公开契约”；`CHG-PRD-2026-09-01-011`又在PROJ分支表示“并行基线收敛与BPM定义身份”，在INS分支`6719ab94`表示“巡检正则受限子集”。INS/AST分支还包含独立的巡检命令1～30秒修订009。应如何在不改写已批准历史的前提下为这些语义形成唯一Change ID和完整后续基线？
- Why it blocks: 修订010和011各自同时指向两套不同业务语义。PROJ候选011遗漏INS/AST的009/010及巡检正则；INS候选011遗漏ACC/PROJ的009/010与BPM身份。任何一条分支的PRD文件直接成为全局基线都会静默丢失另一线语义，并使全局Change ID和验收引用失真。该问题不表示F-INS与其他Feature存在实现、Owner或代码冲突。
- Options: A. 保留ACC修订010编号，以新的正式Change ID重新批准INS/AST两项语义；B. 保留INS/AST修订010编号，以新的正式Change ID重新批准ACC问卷语义；C. 发布新的并行基线收敛修订，完整引用两条原始提交与冲突编号，为每项语义给出唯一后续身份，并明确修订011是被替代的部分候选。
- Recommended technical default: C；保留原分支提交为不可变历史，不直接重编号或覆盖其文本，在新的收敛修订中为010/011全部语义给出唯一后续身份和完整输入清单。
- Business decision required: 是；需要需求方决定两项业务语义是否均保留及其正式Change ID，并重新形成完整PRD Baseline Gate。
- Resolution: 需求方于2026-09-02确认继续选择性集成。master以`CHG-PRD-2026-09-02-011`统一承接F-INS来源分支修订009～012，保留master既有PROJ修订009和COM修订010；所有来源编号只作不可变历史证据，不再作为master正式引用。该收敛不转记任何Feature Done；其后Q-FINS001-005由`CHG-PRD-2026-09-02-012`独立关闭，Q-FINS001-006由`CHG-PRD-2026-09-02-013`按现有布尔权限与超级管理员方向独立关闭，完整发布仍待Task 8实现和验证。
- Blocking scope: 已解除Change ID与全局基线阻断；F-INS完整发布仍受其Feature专属问题约束。
- Decision owner: 需求方/产品组；ACC、AST、INS、PROJ领域Owner参与影响分析
- Decision date: 2026-09-02

### Q-GOV-20260901-002

- Status: CLOSED
- Requirement IDs: COM-01
- Area: F-COM-001双实现与交付范围地点权威语义
- Question: COM-A以`F-COM-001-contract-order-association-and-delivery-scope-allocation.md`实现项目办事处部门快照和验收阶段范围绑定；COM-B以`F-COM-001-contract-order-and-delivery-scope.md`独立实现另一套AST站点/位置优先语义。哪一套Spec、公共契约和持久化模型是COM-01后续唯一权威？
- Why it blocks design/implementation: COM-A和COM-B分别变更157和142个实现路径，重叠仅4个文件；COM-A已在分支记录Done并被ACC-001/002消费，COM-B仍在CUT/PROJ共享线IN_PROGRESS。按“更新提交”或分支自报Done整体选择都会丢失另一线能力或引入与PRD不一致的地点Owner。
- Options: A. 以COM-A业务语义和Spec为权威，逐项审查并仅吸收COM-B中不冲突的迁移/公共契约；B. 以COM-B为权威，重新评审COM-A及ACC消费链；C. 基于当前唯一PRD重新形成一份合并Feature Spec和迁移计划，再按文件/表/API逐项选择实现。
- Recommended technical default: 已由需求方最新业务选择覆盖；历史审计方法仍用于证明两线并非继承关系和控制后续选择性集成边界。
- Business decision required: 是；必须确认交付范围地点、公司/部门快照、阶段触发与AST位置事实的唯一Owner和业务语义。
- Resolution: 需求方于2026-09-02确认COM-A与COM-B承载不同需求，要求按Requirement能力整体合并，不再二选一。统一F-COM-001以ERP权威副本、人工待核对与对账、合同/订单/项目显式关系、项目级范围版本、DeliveryScope命令/查询/冲突、ACC范围绑定组成单一闭环；COM地点唯一采用目标项目办事处发生时快照。COM-B的AST `site/location/text`迁入IMP/AST实施地点能力，不作为COM第二套地点真值；平台迁移证据拆为PLT能力。两条分支的历史Done或Gate不得相互转记，代码必须按能力选择性集成并重新验证。该决定关闭COM地点和来源选择冲突，但不自动产生Feature Done。
- Decision owner: 需求方/产品组；COM、ACC、PROJ、AST领域Owner参与影响分析
- Decision date: 2026-09-02

### Q-PR7-ACC-BASELINE-001

- Status: RESOLVED / AUTHORITY_REBINDING
- Requirement IDs: ACC-02、ACC-03、COM-01
- Area: PR7来源测试与当前唯一PRD基线的一致性
- Question: PR7接收的`test_prd_com_acceptance_scope_trigger.py`和`test_prd_satisfaction_questionnaire_configuration.py`依赖来源分支的验收修订009、问卷修订010及其精确业务条款，但当前正式009为BPM身份、010为COM需求合并，引用的两份修订文件不在当前仓库。是否及如何把剩余来源业务差量以正式变更接收？
- Why it blocks: 不能从来源测试反向增加当前PRD未包含的验收/问卷语义，也不能新建冲突编号的批准记录或删除失败测试制造全绿。Q-GOV-20260901-001/002既有裁决和已接收COM规则保持不变；本问题只针对仍未落入当前基线的差量。
- Evidence: `docs/baseline/prd-v1.8.md`的COM-01、ACC-02、ACC-03正文；上述两个测试的AMENDMENT路径及断言；2026-09-07 PR7治理回归失败。
- Resolution: 逐条复核当前PRD ACC-02/03、当前F-ACC-001/002及已批准ADR-0039～0042后，未发现上述配置、计分、活动完成与范围绑定要求存在业务语义冲突。答案Schema、确定计分和客户端字段限制是合法设计细化；旧修订编号只作历史来源。恢复已批准SDS/物理映射，并把测试绑定当前PRD业务规则与当前Feature/ADR技术细节，保留全部有效负向义务；不恢复已被替代的“平台不预置”断言，不导入PR1/PR2新业务范围。
- Blocking scope: 不再阻断现有ACC合并修复；Q-FCOM-002的退出/回退绑定处理仍保持其原有窄阻断。
- Business decision required: 否；本次为已有批准资料的恢复与错误阻断纠正，不改变PRD。
- Decision owner: 需求方/产品组

### Q-FCOM-002

- Status: RESOLVED（需求方 2026-09-30 裁决：A 回退即关闭解锁）
- Requirement IDs: COM-01、ACC-03、PM-10
- Area: 退出/回退验收阶段后的范围绑定关闭或解锁
- Question: 退出或回退验收阶段时，既有AcceptanceScopeBinding的关闭和解锁规则仍待业务明确。
- Blocking scope: 仅退出/回退时的绑定关闭或解锁设计；确认前不得自动写effective_to、关闭、解锁或改写既有绑定。阶段进入绑定与验收阶段内新版本绑定规则继续有效，不阻断报告版本、满意度或当前范围的既有正向路径。
- Evidence: 当前统一F-COM-001规格BR-FCOM001-005及第13节；ADR-0038/0039，恢复原有窄问题登记，不新增业务Gate。
- Business decision required: 是。
- Resolution: 需求方2026-09-30裁决采用A——退出或回退验收阶段即关闭既有AcceptanceScopeBinding并解锁（落effective_to至回退时点、解除资源绑定锁定）；回退路径的关闭与解锁写入自此获得授权实现，原"确认前不得自动写"限制针对本裁决情形解除；阶段进入绑定与验收阶段内新版本绑定正向规则不变。Decision date: 2026-09-30。
- Implementation 2026-09-30: 已实现回退路径关闭解锁——`AcceptanceScopeBindingApi.closeProjectBindings`（新增命令/结果 DTO，MANDATORY 事务，逐条落 effective_to 至调用时点，无活跃锁幂等返回）；权威回退路径 `ProjectGovernanceApplicationService.rollbackOnce` 在同事务调用（operationId 与阶段快照一致）。V385 放开 V160 的 `chk_acceptance_scope_effective`（binding_status 仍恒 LOCKED，活跃判定不变）。既有治理动作审批流 `approveGovernanceAction` 无任何调用方，未接线。

## CUT/IMP来源裁决补登

来源：`codex/f-cut-001-matrices@faed8387`。本节补齐既有非COM事实契约，不改变修订017、当前Feature状态或生产装配边界。

## F-CUT-003 Implementation 待裁决项

### Q-FCUT003-001

- Status: RESOLVED / PENDING_INTEGRATION_REVIEW
- Requirement IDs: CUT-03、CUT-07
- Area: P3动态清单的设备类型匹配输入Owner
- Question: 每台设备的`deviceTypeCode`应由哪个权威来源赋值并由哪个领域Owner维护，才能在割接任务创建时冻结为P3匹配输入？
- Why it blocks design/implementation: 来源分支时点未集成F-AST-002；当前master已接收其公开API，但写事务内期望版本锁定重验和生产接线仍须按当前Feature核对。该缺口不阻断CUT按预留接口和`src/test`受控替身推进内核，只阻断生产接线、真实浏览器证据和Implementation Done。
- Options: A. AST Device聚合拥有设备类型赋值，明确权威来源及创建/更新规则并经SYSTEM字典校验；B. 引用另一个已批准Owner的稳定设备类型Fact；C. 调整CUT-03，使V1不以设备类型作为匹配维度。
- Recommended technical default: B；复用F-AST-002的`AssetProductTypeApi.getAuthorizedDeviceProductType`，CUT预留最窄消费端口并冻结公开合同可证明的产品类型编码和来源版本，不扩展`DeviceScopeFactApi`。
- Business decision required: 已完成。需求方确认设备类型赋值来源为产品主数据。
- Resolution: F-AST-002的产品主数据受控副本与`AssetProductTypeApi`公开查询已由master后继接收。CUT不重复Owner实现，不向`DeviceScopeFactApi`追加类型字段；只预留消费端口并使用公开结果已有字段。精确边界见ADR-0037。
- Blocking scope: 生产Adapter、任务创建生产装配、真实浏览器证据和Implementation Done；CUT内核、REST/UI候选及`src/test`受控正向闭环不阻断。
- Decision owner: 需求方；AST、SYSTEM、CUT领域Owner参与裁决
- Decision date: 2026-08-31

## F-CUT-009 Feature Ready 待裁决项

### Q-FCUT009-001

- Status: RESOLVED / OPTION_A
- Requirement IDs: CUT-03@V2
- Area: P3清单提交后的可配置流程跳转语义
- Question: “更灵活的流程跳转配置”只决定提交成功后的界面导航，还是允许配置改变CUT业务状态迁移；其合法目标集合和触发时点是什么？
- Why it blocks design/implementation: PRD明确V2不改变清单版本、必填校验和D级跳过规则，但未给出跳转目标；API SDS只要求目标流程状态合法。若允许规则驱动状态迁移，将改变P3→P4状态机、下游P4/P5门禁和审计Owner；若只是界面导航，则必须禁止配置写状态。两者不能由Feature或代码静默选择。
- Options: A. 配置只产生提交成功后的服务端导航决定，目标限于当前权威任务阶段对应的既有工作台锚点或任务总览；F-CUT-003仍唯一执行`SURVEYING -> PLAN_DRAFTING`，规则不得改变状态。B. 配置可选择多个业务目标阶段并驱动状态迁移；须先正式补齐PRD/SDS状态机、允许目标、跳过门禁、回退和审计。C. V2只交付导出，暂不交付流程跳转配置；CUT-03@V2保持PARTIAL。
- Recommended technical default: A。它保留PRD明确的V1业务事实，只把“跳转配置优化”实现为可配置导航，避免无依据绕过P4/P5/P6。
- Business decision required: 已完成；独立Feature Ready裁决确认采用方案A。
- Resolution: 采用A。V2只允许一个无条件提交后导航目标：`CURRENT_STAGE_WORKBENCH`或`TASK_OVERVIEW`。规则只返回界面导航决定，不写CUT状态；F-CUT-003仍唯一执行`SURVEYING -> PLAN_DRAFTING`。冻结修订缺少规则或规则为null时确定性返回`CURRENT_STAGE_WORKBENCH`。
- Blocking scope: 已解除语义阻断；F-CUT-009仍须通过API/Physical Machine Contract最小整改复审及Feature Ready Gate。
- Decision owner: 需求方；CUT-03、CUT-04及状态机Owner参与影响分析
- Decision date: 2026-09-02

## F-CUT-004 Task 6 待裁决项

### Q-FCUT004-001

- Status: OPEN / BLOCKED_BY_SPEC
- Requirement IDs: CUT-04
- Area: 已批准方案职责变化后的阶段回退与重新审批
- Question: 任务已由CUT-05批准进入`P6/CLOSURE_IN_PROGRESS`后，角色或任务职责变化应由哪个物理Owner以什么状态迁移、历史触发器和事务边界返回P4并重新进入P5？
- Why it blocks design/implementation: 正式业务规则要求职责变化创建新DRAFT并重走P5，但当前状态机只定义F-CUT-004的P4→P5与P5来源失效→P4，以及F-CUT-005的P5驳回→P4、批准→P6；不存在P6→P4 Owner/触发器。CUT-05的`previousApprovalInstanceId`又只允许引用REJECTED或PAUSED事实，不能引用或改写APPROVED事实；当前也未定义在途CUT-06闭环及旧批准revision的current_marker处置。
- Options: A. 正式批准F-CUT-004拥有P6职责变化回退，并补齐task/plan/approval/closure CAS、历史触发器与新审批引用；B. 指定CUT-05或CUT-06拥有回退命令并向F-CUT-004提供稳定结果事实；C. 调整业务要求，不允许P6职责变化重新审批。
- Recommended technical default: B，由拥有P6闭环/审批协调语义的Owner先形成回退事实；F-CUT-004只在任务已权威返回P4后派生新DRAFT。
- Business decision required: 是；该选择改变P6状态迁移Owner、审批链及可能存在的闭环在途事实处置。
- Blocking scope: 仅`revise(reason=DUTY_CHANGED)`运行实现与对应REST动作；REJECTED、SOURCE_REPLACED、批准后联系人PATCH及其受控正向验证不阻断。
- Decision owner: 需求方；CUT-04、CUT-05、CUT-06领域Owner及数据Owner参与裁决
- Decision date: 待定

## F-IMP-002 Task 5B 裁决项

### Q-FIMP002-001

- Status: RESOLVED / 采用方案A
- Requirement IDs: EXE-01
- Area: 到货差异具体豁免的审批主体事实
- Question: V1执行`resolve-difference`的`EXEMPT`分支时，哪个正式公共事实或已定义项目内角色唯一证明调用人是PRD所述“豁免审批人”？
- Why it blocks design/implementation: 五项锁定功能权限只提供`pms:arrival-acceptance:resolve-difference`，`ProjectScopeApi.ACTION_EDIT`只证明数据范围；二者都不单独证明调用人具有业务审批资格。客户端提交`approvedBy/approvedAt`又会伪造审批事实。
- Options: A. V1由本人负责项目的当前`PROJECT_MANAGER`承接豁免审批，同时要求`resolve-difference + ACTION_EDIT`，批准人/时间由服务端写入；B. 引用另一个已锁定的项目内审批主体事实；C. 新增独立审批流程/角色契约并先回写PRD/SDS。
- Decision: 采用A。V1豁免审批人固定为调用当时本人负责该项目的current `PROJECT_MANAGER`，并同时要求`pms:arrival-acceptance:resolve-difference + ProjectScopeApi.ACTION_EDIT`；`ProjectParticipantFactApi`必须在写事务中锁定重验项目经理资格。`approvedBy`取受信actor，`approvedAt`取服务端时钟，客户端不得提交。权限键、数据范围或全局角色均不能单独替代审批主体事实。
- Decision rationale: PRD未要求审批人与项目经理职责分离；该收敛复用EXE-01已定义的项目经理职责，不新增角色、流程或表。
- Blocking scope: 已解除本问题自身对`EXEMPT`分支的阻断；原分支Task 5B复审回执保留为历史，当前实施进度以master的F-IMP-002 Task为准。
- Decision owner: 需求方；IMP、PROJ和权限Owner参与影响分析
- Decision date: 2026-08-30

### Q-FIMP002-002

- Status: RESOLVED / OPTION_B
- Requirement IDs: EXE-01
- Area: 到货签收已确认批次的后继DRAFT业务编码
- Question: `SUPPLEMENT`、`CORRECTION`、`DIFFERENCE_CLOSURE`和`EXEMPTION_INVALIDATION`创建关联后继DRAFT时，后继`batch_code`应继承哪个业务值，还是由哪个Owner按什么稳定规则生成新值？
- Why it blocks design/implementation: `imp_arrival_acceptance`已正式锁定租户内`project_id + batch_code`唯一；直接复制前驱编码必然违反唯一键，自行拼接后缀会臆造业务编码并可能突破64字符合同。当前Feature/REST机器契约只锁定`predecessor_acceptance_id`和`successor_reason`，没有给出后继编码来源、格式、长度溢出处置或幂等重放规则。
- Options: A. 明确由IMP服务端按正式稳定规则生成新的后继批次编码；B. 批准后继沿用业务批次编码并正式调整唯一键/引入独立实例序号；C. 引用另一个已锁定Owner提供的新批次编码。
- Recommended technical default: 原建议A已被正式裁决B替代。
- Business decision required: 是。该选择改变公开详情中的`batchCode`及数据库唯一性执行路径。
- Resolution: 采用B。successor原样继承直接前驱规范化`batch_code`，禁止后缀、截断或外部生成；新增服务端`batch_root_marker`，以`tenant+project+batch_code+batch_root_marker`只约束一个初始根，并以`tenant+predecessor_acceptance_id`保证每个前驱最多一个直接后继。平台重放返回同一后继，不同key不得创建兄弟节点。
- Blocking scope: 本问题自身已解除；相关代码和前向迁移已进入master的部分不回退，尚未完成的装配与运行范围以当前F-IMP-002 Task为准。
- Decision owner: 需求方；IMP领域Owner和数据Owner参与裁决
- Decision date: 2026-08-30

## 单一模板升级的存量运行切换

### Q-FPROJ009-001

- Status: OPEN / BLOCKED_BY_SPEC
- Requirement IDs: PM-03@V1；关联PM-01、PM-11
- Area: 无显式关系图的已有发布模板/项目升级
- Question: 在不保留双模板运行算法、不按sortOrder或S编号推图、不覆盖不可变历史的约束下，存量可写项目应依据哪份已批准关系图来源及哪个明确数据批次完成切换？
- Evidence: 2026-09-08对固定测试库23316/npdms_test只读核查：Flyway到V204，阶段定义图与实例图表均不存在；43个ACTIVE、1个NORMAL_CLOSED项目，11个PUBLISHED模板修订。项目分组为tenant0无模板引用33个ACTIVE及1个关闭、模板910001/v2有6个ACTIVE、910008/v1有1个ACTIVE；tenant1模板992203040001/v2有3个ACTIVE。来源只是现有记录，不推定可丢弃或批准了线性关系。
- Approved boundary: 需求方本轮批准直接升级单一模板并复用页面/动态表单，同时要求历史保护；不保留LEGACY/GRAPH双轨，不擅自清库、停用项目或把排序解释为真实前后置。
- Blocking scope: 仅存量运行图切换及要求所有旧可写项目继续推进的验收；视图注册、定义/图配置、显式新草稿发布和无依赖领域验证可继续。
- Resolution required: 由需求方确认受影响对象的权威关系来源与切换批次，或明确授权仅对指定隔离测试数据重建。不能从“计划批准”推导数据删除或历史转换授权。真实业务记录不得自动重建。
- Decision owner: 需求方及PROJ数据Owner
- Decision date: 未关闭；2026-09-08登记

### Q-LEGACY-CONTRACT-DUPLICATE-POLICY-20261012-008 — 所有同类项目合同重复记录统一规则

- Status: RESOLVED / GENERAL_RULE_APPROVED / RULE_IMPLEMENTED / AFXX_IDENTITY_PENDING
- Decision: 2026-10-12需求方对两组跨月原ID44210、45535、45538及42247、45177明确回复“同一项目＋合同共用一条正式关系，正式创建时间取各组最早值，所有相同情况都使用同样的规则”。同类原记录不再限定18个完全相同组或14个秒级组，也不设时间差阈值。
- Rule: 原合同号、项目组、创建人、更新时间、更新人相同，精确项目与完整桥及正式合同资格一致时，共用正式关系；原创建时间取最早，平时间用最小技术ID只稳定绑定；每个原ID＋成员ID独立绑定及完整来源保留。其他创建时间明确不独立迁入正式审计，原因“重复登记时间，不构成不同关系事实”。不同人员、更新事实、项目身份或桥属性不属于同类，继续阻断，不因同项目合同丢弃字段。
- Completeness: 来源查询先从完整pm_project_contract按上述五项精确分组计算duplicateGroupSize，这是读取证明，不是新增物理列或正式业务字段。适配器必须读齐同组原ID后才能计算最早值；部分筛选、拆页或重复身份阻断，不能使用部分记录作完整关系。既有冻结配置及旧回执不改写，新模板通过原迁移功能使用。
- Boundary: 不新增表、列、索引，不授权接管被修改关系或扩大原项目实际迁入。HISTORICAL仍沿Q007专用规则，不由重复规则产生解除日期、当前商务绑定或授权。
- Decision owner: 需求方。

### Q-LEGACY-CONTRACT-AFXX-SOURCE-PROJECT-20261012-009 — 两组跨月关系的真实安服先行主体

- Status: SQL_RULE_AUDIT_IN_PROGRESS / INDIVIDUAL_ID_QUESTION_SUPERSEDED
- Evidence: 原关系44210、45535、45538经成员43427的16030021090911N-0，在原pm_project唯一匹配42026（对外经贸大学2021年安全服务，afxx）；原42247、45177经成员41526的16201619041701N-0-his，去掉后缀仅用于候选核查，原pm_project存在同码同名40191／42902（国网四川省电力公司信通公司安全服务项目，afxx）。40191与原成员均于2021-06-09 13:45:42创建；42902于2021-10-28 13:54:22新建，不能用日期自行选主。真实pm_project_header视图只取projectType=10，来源缺关联并非原项目不存在。
- Question: 是否登记原三条精确关联42026，并明确两条历史关系关联40191或其他项目；afxx本轮只保留身份与迁移方案，还是启动项目主表整表规划裁决？仅Q008合并规则不等于批准新项目类型及全历史实际写入。
- Boundary: 完整37原字段和26关联字段保留。未回复前5条阻断，不自行转当前关系、不忽略、不按原组ID冒充项目ID。已批准通用合并、4条原36645历史及3条缺组排除继续独立实施；不重复确认。
- Decision owner: 需求方。

- Resolution 2026-10-12: 用户要求改用pm_project、约束类型，整表迁移而非10项目专用，并指示查项目列表SQL归纳关系，不逐ID裁决。本项旧逐ID问题已替代；完整原字段仍保留，多义规则继续追实际旧查询/失效/核销代码。整表身份唯一键另列Q011，未获批准不变更。

## 模板与独立验收修订018的后续契约

### Q-TPLACC-001

- Status: OPEN / BLOCKED_BY_SPEC
- Requirement IDs: PM-03@V1、PM-11@V1、ACC-03@V1、COM-01@V1；关联PM-01、PM-06、ACC-04、CLO-01/02
- Area: ACC独立实体创建/办理、Stage/Task来源关联、验收范围及受控触发的API/物理差量
- Question: 在保留项目/验收类型身份与历史的前提下，如何用明确公开契约承载不依赖固定ProjectTask或S5快照的验收创建、范围确认和结果消费，并保持权限、唯一、锁序及事务完成点？
- Why it blocks design/implementation: 现有V166要求project_task_id/execution_contract_id，initializer固定两组任务码，范围绑定只接受原阶段快照；这些旧约束与已批准的独立验收路径不相容，不能通过参数改空、假S5或复制Owner事实绕过。
- Approved business boundary: CHG-PRD-2026-09-07-018及ADR-0045已确认终验独立、业务规则配置化、触发与判定分离。无需再次裁决是否取消S5强制终验，也不新增多轮活动、角色或审批节点。
- Required design work: PROJ/ACC/COM在SDS Phase 2冻结独立创建/办理API、可选节点来源、范围绑定业务身份及版本、执行身份、幂等重放、稳定锁序、原子/独立命令完成点和前向Schema/唯一键；同时列明旧接口及历史来源的保留边界。
- Blocking scope: 仅新增独立触发、实体初始化、验收范围及依赖这些契约的运行接入。修订018业务/逻辑文档可以先收口；旧已实现独立切片不回退，Feature Ready/Done不自动恢复。
- Decision owner: PROJ、ACC、COM设计Owner；涉及新增业务语义时返回需求方
- Decision date: 未关闭；2026-09-07登记

#### 2026-09-20 专项业务判定确认

需求方在本次专项代码修复中确认：初验、终验任务分别以“报告已生效且结论为通过，并满足模板配置条件”判定；不新增人工审批，初终验前置关系由模板决定。本次按既定 `tenant+project+acceptanceType` 身份提供项目独立创建入口，保留旧任务来源及报告历史；独立报告使用固定的有效报告通过判定，节点附加条件使用项目已冻结模板规则。交付件通过既有项目交付件提交接口关联报告成果，不为独立验收伪造阶段、任务、执行契约或自动交付目标。

本确认消除本次“有效报告是否等于验收通过”的业务歧义；实现、权限、前向迁移及浏览器闭环证据仍需逐项验证。本记录不关闭此前 COM 范围、多目标归档及 BPM 扩展合同的剩余事项，不改写历史 NO-GO，也不宣布 Feature Ready/Done。

#### 2026-09-08 契约候选进展

需求方已确认书面逻辑设计，并补充“验收主要是项目层面的验收”。候选保持`tenant+project+acceptanceType`身份，节点和视图只引用项目验收；COM范围只在已发布规则要求时参与，不成为所有项目验收创建的统一前置。

具体候选位于`docs/superpowers/specs/2026-09-08-template-acceptance-phase2-contract-design.md`及同名`2026-09-08-template-acceptance-phase2-contract.json`。已列PROJ上下文/发布定义、ACC创建/完成/事实、COM阶段无关快照、范围变化回调、来源可空约束及新旧绑定身份。现有getAssignedScope缺少allocationVersion，候选扩展原COM验收锁API，不伪造阶段ID或混用全局/分配/授权版本。

本进展不关闭Q。先复核受影响Phase 1边界，再审阅具体API/物理合同并完成适用Schema验证；未形成通过证据前保持OPEN/BLOCKED_BY_SPEC。与本Q无依赖的模板基础配置仍可按自身契约推进，不扩大阻断。

#### 2026-09-08 独立技术复核与R1整改

独立任务`01a07ce8-42fa-7dd2-8991-094d0c15cc6c`对候选`c18e5959e70ea2eef17a815b56067e4612842d6f`返回NO-GO：新旧报告反向锁、完成后换版/配置化/零附件消费者缺口、ERP换ID及PM-02拆分保护缺口、多目标仍单值查询、BPM可靠捕获未定稿。项目级主身份、无S5强制、可选COM、触发/事实分离等已确认边界保持。

需求方确认仅整改上述五项再交同一任务复审。当前候选R1补齐统一锁序及范围参与者预锁、报告命令和状态矩阵、零附件NOT_REQUIRED与多目标查询/响应、COM可信前驱及全写路径守卫/刷新、BPM原事务捕获与提交后幂等消费。JSON/路径/类型检查仅为文档结构自审；独立复审待返回，不关闭本Q，不代替正式SDS、物理合同与适用Schema/P3-E09验证。

#### 2026-09-08 R1裁决与R2自审

同一独立任务对`a606e3c44ea2f437856aba5cb02efd983868803b`确认R1-01/02/03契约层已解决；多目标查询及BPM事务主要设计保留，但多目标共同archiveBatchId未定义、AAP eventId可能超过既有VARCHAR(64)，因此仍NO-GO。

需求方授权继续修订并要求先自审再提交复审。当前R2为新来源固定完整报告级共同归档命令，保留旧批次；BPM完整captureKey与首次新执行生成、同事务固化的36字符eventId分离，捕获请求摘要不含随机结果。一轮源码追踪、内存契约样例及50记录/16方法/10 REST结构自审已完成，定位见候选3.5、5.1和第10节；待原独立任务复审，Q及正式Gate仍不关闭。

#### 2026-09-08 R2技术GO及Phase 1接续

独立任务对`3d82db0ba0d12d99a334b91d4db1c03b4aa787fd`返回候选技术GO，两项剩余P2闭合，无新增必须整改项；接收记录为`docs/engineering/gates/phase-2/input/template-acceptance-r2-independent-review.md`。该结论不是本Q关闭或正式SDS/Schema放行。

需求方要求下一步，当前先开展修订018受影响Phase 1边界对齐/复核，输入见`docs/engineering/gates/phase-1/revision-018-template-acceptance-review.md`。已有全量检查提示Phase 2 carrier contract的PRD身份陈旧，须随正式API/物理合同差量审查处理，不能只替换身份；该技术待办与原Schema/P3-E09条件共同保留。本Q继续OPEN/BLOCKED_BY_SPEC，仍只限制依赖它的独立验收新实现及接入。

#### 2026-09-08 变更级审查政策生效后的阻断范围

需求方已批准取消Phase 1/2阶段全量阻断。上文中的纯阶段顺序、整阶段批准和PRD全文身份联动不再作为本Q的关闭前置；原审阅/失败记录保留历史，不能称已修复。当前只需针对独立验收这项变更，将已审契约落实到其实际权威API/物理设计及直接消费者，完成真正受影响的Schema/一致性检查并取得对应范围审阅结论；无需修改无关分册状态或重签全阶段。本Q不自动关闭，仍限制尚未明确或验证的实质契约及其依赖实现。

## 领域模块物理组织与跨域交互形态

### Q-ARCH-001

- Status: OPEN
- Requirement IDs: 无单一Requirement绑定；不改变PRD业务语义的工程结构问题，影响全部`pms-module-*`模块与跨域契约演进
- Area: 多领域模块物理组织与跨域交互形态
- Question: 跨域交互的接入税（跨域Fact API层、Provider接入数组、DTO映射及api模块维护成本）应通过物理合并领域模块降低，还是通过按交互性质分流交互形态降低？
- Evidence: 当前12个`pms-module-*`业务/公共模块与9个`pms-module-*-api`模块；`pms-module-platform`已具备幂等、审计、Outbox与业务授权；engineering已运行`PreparationSourceProviderRegistry`端口注册表模式，并已定义`ArtifactAcceptedMessage`、`ImplementationEvidencePublishedMessage`等事件消息；analytics为纯只读分析域。
- Options:
  - A. 维持现状：每域api+biz双模块，跨域交互继续按同步FactApi/Provider形态扩展。
  - B. 物理合并（2026-09-17讨论的8模块方向）：高频执行环领域合并为单一承载模块（如`pms-module-delivery`），域内跨域调用退化为模块内调用；逻辑领域保留，以包结构纪律补偿边界。
  - C. 三旋钮组合：把模块化拆为物理边界（Maven模块划分）、契约形态（接口由提供方api发布或由消费方端口定义）、运行时耦合（同步调用/事件/本地投影）三个可独立调节维度；物理边界维持多域不合并，只调节后两者——强一致命令（如`lockAndRevalidate`行锁重验）保留同步api；只读事实快照按消费频率与陈旧容忍度在FactApi与事件物化/本地投影间选择（analytics为天然试点）；多源适配推广端口+注册表既有模式；跨域长流程（五步检查、异步重评链等PRD既有流程）归编排层+LiteFlow，依赖图从网状收敛为星型；全域公共概念收敛共享内核platform。
- Recommended technical default: C。B一次性降低接入税但牺牲域边界与演进隔离，合并后难以回退；C复用既有基础设施（platform Outbox、Provider注册表、事件消息），按交互逐项演进、分阶段实施且单项可回退。
- Cost/risk note: C的事件物化新增投影维护、重建与对账成本，仅对高频且可容忍短暂陈旧的读启用；编排层不得拥有业务事实，防止演变为上帝模块；共享内核严格准入，只收全域真正共用概念，防止退化为公共堆积区。
- Why it blocks design/implementation: 不阻断当前Feature实施与验收；裁决前新跨域交互仍按现状同步FactApi/Provider形态实施，不得擅自切换事件物化、本地投影或新建编排层。
- Blocking scope: 后续新跨域交互的形态选择与模块结构演进；既有已实现api契约不因本Q变更。
- Business decision required: 是；需需求方裁决方向，架构负责人与各领域Owner参与影响分析。
- Resolution: 待定
- Decision owner: 需求方（方向）；架构负责人、各领域Owner（影响分析与落实）
- Decision date: 未关闭；2026-09-17登记

## 四级维度字段库内命名统一

### Q-MIG-DIM-001

- Status: CLOSED（2026-09-18裁决并落实，见末节落实回执）
- Requirement IDs: COM-01（合同回款/发货合同归属列）、PM-03/PM-10（CRM执行单列）；主数据治理事项，不改变PRD业务语义
- Area: dppms迁移目标库四级"市场-系统-拓展-行业"维度字段命名统一
- Question: 主档`cus_market_relation`与业务表（`com_crm_execution_order`、`com_contract_receivable`、`com_shipment_contract_reference`）对同一维度概念使用不同列名，统一到哪套命名、`*_department_source_key`血缘列是否保留？
- Evidence: 规格DDL（`project-order-physical-schema.mysql.sql`）内部分歧：主档用`market_code/system_code/expend_code/industry_code`（行业一致）；三张业务表用`marketing_department_code`、`system_department_code`（另有`system_department_source_key`）、`expansion_department_code`（另有`expansion_department_source_key`）。需求方2026-09-18指令：四级维度主档为`cus_market_relation`（dppms同步副本`pm_project_market_relations_from_sms`，529行），且这些字段在数据库内必须保持统一。
- Why it blocks design/implementation: 不阻断迁移映射梳理（报告按各表实际DDL列名如实记录）；阻断CRM执行单6张未建表的最终DDL定稿，及已建表（V258）列名演进。
- Options:
  - A. 业务表对齐主档命名：`marketing_department_*→market_department_*`或`market_*`、`expansion_department_*→expend_department_*`或`expend_*`，系统部对齐`system_*`；`*_department_source_key`作为CRM原始键血缘列保留。
  - B. 主档改为业务表命名（`marketing_department_code`等）；主档是CRM同步目录，改名偏离CRM来源字段语义。
- Recommended technical default: A。主档是权威来源且`industry_code`已一致，业务表向主档语义对齐改动面最小；`*_department_source_key`保留原值血缘符合既有裁定。
- Business decision required: 是；统一后目标列名（含`_id/_name`伴生列）需需求方确认，随后修订规格DDL并复核证据哈希，V258已建表以前向迁移落列名变更。
- Resolution: 业务表对齐主档命名（方向A）；行业划分四维列名按`cus_market_relation`（`market_/system_/expend_/industry_`），办事处取`department_`，创建时原值合并`original_division_values` JSON；2026-09-18落实于规格DDL修订与前向迁移（原V294/V295，后按需求方指令并入合并迁移V294，见Q-MIG-DIM-005回执后合并记录）。
- Decision owner: 需求方（目标命名）；F-COM-001/AI-MIG Owner（DDL修订与迁移落实）
- Decision date: 2026-09-18登记

#### 2026-09-18 需求方裁决

需求方裁决按`cus_market_relation`主档列名统一（方向A）：`market_code/market_name`、`system_code/system_name`、`expend_code/expend_name`、`industry_code/industry_name`；伴生解析列`<root>_department_id`、血缘列`<root>_source_key`（原值列`original_<root>_source_key`）；`office_department_*`与行业列维持现状。现行业务表DDL列名（`marketing_department_*`/`system_department_*`/`expansion_department_*`）定性为实现偏差，偏差对照见`docs/generated/dppms-core-data-mapping-2026-09-18.md`第0节。落实：CRM执行单6张未建表按裁定列名定稿；`com_contract_receivable`、`com_shipment_contract_reference`（V258已建表）以前向迁移更正列名并复核证据哈希。实施完成前本Q保持OPEN。

#### 2026-09-18 补充裁决（办事处对齐）

办事处是部门维度而非行业，编码/名称同样对齐主档风格：`office_department_code/name→office_code/office_name`；`office_department_id`已指向平台共享部门主档（`system_dept`），保持不变。行业列`industry_code/industry_name`维持现状。偏差对照已更新至`docs/generated/dppms-core-data-mapping-2026-09-18.md`第0节。

#### 2026-09-18 更正（办事处命名保留department）

办事处是部门而非四级组合成员，"部门是department"：`office_department_code/office_department_name/office_department_id`命名保留，不随`cus_market_relation`四级表（`market_code/system_code/expend_code/industry_code`）改名，现DDL无偏差。上一条"补充裁决（办事处对齐）"中`office_department_code/name→office_code/office_name`的更正作废。四级组合维度偏差对照（市场/系统/拓展/原值列）维持不变。

#### 2026-09-18 再更正（办事处编码/名称去重）

`office_`与`department_`语义重复：办事处编码/名称列裁定为`office_code/office_name`，现DDL`office_department_code/name`为实现偏差；`office_department_id`保留（指向平台共享部门主档`system_dept`，`department`在此有实义）。上一条"更正（办事处命名保留department）"中关于编码/名称列的部分作废，ID列结论不变。最终命名体系：编码/名称列一律`<root>_code/<root>_name`（市场/系统/拓展/办事处/行业），ID列一律`<root>_department_id`，血缘列`<root>_source_key`。偏差对照已更新至`docs/generated/dppms-core-data-mapping-2026-09-18.md`第0节。

#### 2026-09-18 最终命名裁定（部门列保留department后缀）

部门维度（市场/系统/拓展/办事处）编码/名称列统一为`<root>_department_code/<root>_department_name`，根名取主档`cus_market_relation`的`market/system/expend`（办事处根名`office`）；ID列`<root>_department_id`、血缘列`<root>_department_source_key`（原值列`original_<root>_department_source_key`）。行业不是部门：仅`industry_code/industry_name`，无`industry_department_id`。据此实际偏差收敛为两组根名错误：`marketing_department_*→market_department_*`（3列）、`expansion_department_*→expend_department_*`（4列及`original_expansion_department_source_key`）；`system_department_*`、`office_department_*`、`industry_*`、`original_system_department_source_key`与现DDL一致。此前关于`market_code/system_code/expend_code/office_code`命名的中间结论作废。偏差对照见`docs/generated/dppms-core-data-mapping-2026-09-18.md`第0节。

#### 2026-09-18 概念澄清与最终口径（行业划分非部门）

"行业划分"是市场/系统/拓展/行业四个维度的统称（不单指industry维度），四个维度都不是部门、与部门主档（`system_dept`）无关：列名一律按主档`cus_market_relation`——`market_code/market_name`、`system_code/system_name`、`expend_code/expend_name`、`industry_code/industry_name`，全部经组合目录解析，无`*_department_id`；CRM原始键血缘列`system_source_key`/`expend_source_key`（原值列`original_*_source_key`）。办事处是部门维度：列名`department_code/department_name/department_id`（`office_`与`department_`语义重复取`department_`），`department_id`解析到`system_dept`。据此现DDL偏差为：`marketing_department_code/name→market_code/market_name`、`system_department_*→system_code/name/source_key`、`expansion_department_*→expend_code/name/source_key`、`original_*_department_source_key→original_*_source_key`、`office_department_code/name/id→department_code/name/id`；`marketing_department_id`/`system_department_id`/`expansion_department_id`三列应删除（行业划分维度无部门主档ID）；`industry_code/name`一致。此前所有含`_department_`中缀的中间命名结论作废。偏差对照见`docs/generated/dppms-core-data-mapping-2026-09-18.md`第0节。

#### 2026-09-18 裁决（行业划分原值合并JSON字段）

行业划分创建时原值（回款源表`sms_ofst_contract_head_sap`的`systemid_o`/`expendid_o`/`industry_name_o`，当前81,547行全空）不再设`original_system_source_key`/`original_expend_source_key`/`original_industry_name`独立列，合并为单个JSON字段`original_division_values`统一存储：`{"systemSourceKey":...,"expendSourceKey":...,"industryName":...}`，按来源原值保留、不参与解析。现DDL`com_contract_receivable`的三个`original_*`列在规格DDL修订时以此JSON字段替代。背景：`_o`字段为回款记录创建时的行业划分归属原值，当前字段被调整后保留初始归属用于追溯。

#### 2026-09-18 落实回执（规格DDL修订+前向迁移，迁移文件现为合并V294第1段）

按上述裁决完成库内统一：(1) 规格权威DDL `specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql` 修订`com_contract_receivable`、`com_shipment_contract_reference`、`com_crm_execution_order`三表列名——行业划分四维改`market_code/market_name`、`system_source_key/system_code/system_name`、`expend_source_key/expend_code/expend_name`（industry两列原本一致），删除行业划分维度的三个`*_department_id`列，办事处改`department_id/department_code/department_name`（含执行单两个索引`idx_crm_execution_company_office(_code)`列引用），三个`original_*`列合并为`original_division_values JSON`；修订后SHA-256 `AF8C3BA5D44BDE9809E9F10AC70A60E0CA4DA43E9BFE7E6B1714759294BBB3D0`（修订前基线`6B203BF3B4CC860DFAEF1221977F2B48A620C0077638D857582FF7BB033E275B`）；同日按需求方要求将上述列注释精简为界面显示字段口径（市场部编码/系统部编码/拓展部编码/办事处编码等），语义不变。(2) 前向迁移（列名更正，现并入合并迁移`V294__migration_field_naming_and_comment_alignment.sql`第1段）对V258已建的`com_contract_receivable`、`com_shipment_contract_reference`执行同等更正（两表经核查均0行，合并不丢数据），2026-09-18经Flyway实际应用成功，实库列状态及注释已复核（注释精简亦在合并迁移第3段）。(3) 修订后规格DDL已在隔离MySQL 8.4容器完整执行验证，66张表全部建成、索引列正确。`com_crm_execution_order`等6张执行单表未在实库建表，建表时直接采用修订后规格DDL，无需迁移。本Q裁决均已落实，Q关闭。

## 办事处维度字段库内命名统一（续）

### Q-MIG-DIM-002

- Status: CLOSED（2026-09-18裁决并当日落实，见末节落实回执）
- Requirement IDs: COM-01（履约范围/拆分承接列）、PM-03/PM-10（项目拆分列）、CUT（快照键）
- Area: 办事处（部门维度）字段在履约范围、项目拆分与切换快照中的命名统一
- Question: Q-MIG-DIM-001裁决"办事处是部门维度取`department_`"后，实库仍有4张表带`office_department_*`列，对应Java字段、跨模块API契约`ProjectOfficeFact`及cutover审批快照JSON键是否一并统一？
- Evidence: 实库`com_delivery_scope`（office_department_id/code/name/version）、`fcom001_v70_com_delivery_scope_detail`、`proj_project_split_item`、`proj_project_split_scope`（各office_department_code）；代码引用横跨pms-module-commerce（履约范围DO/VO/服务/测试）、pms-module-project（拆分DO/服务/VO、`ProjectOfficeFact` API契约）、pms-module-cutover（审批快照编解码器、请求编解码器、VO/视图）；`CutoverProjectContextPort.ProjectContextFact`已提交为`departmentId/departmentCode/departmentName`，控制器等仍用旧名（分支处于改名中途）。
- Options:
  - A. 全部统一为`department*`：DB列、Java字段、API契约、快照新写键；历史快照旧键只读兼容。
  - B. 仅统一DB列，代码与快照键保留office命名——列与代码脱节，违背库内统一裁决。
- Recommended technical default: A。
- Business decision required: 是；已由需求方2026-09-18确认（含历史快照"新写新键、旧键只读兼容"方案）。
- Resolution: 采用A。4张表DB列`office_department_*→department_*`（原V296，现为合并V294第2段）；三模块代码与`ProjectOfficeFact`组件同步改名；cutover审批快照写入`departmentId/departmentCode/departmentName`新键，读取同时接受旧键`officeDepartmentId/officeCode/officeName`，不重写历史快照。`pms_project.office_id`（项目所属办公室，40行）同属办事处概念但属核心项目表既有约定，不在本次确认范围，保留并在此标记。
- Decision owner: 需求方（命名与兼容方案）；F-COM-001/FPROJ-002/CUT Owner（落实）
- Decision date: 2026-09-18登记并确认

#### 2026-09-18 落实回执

- DB：原`sql/migrations/V296__office_department_column_alignment.sql`（已并入合并迁移`V294__migration_field_naming_and_comment_alignment.sql`）完成4张表`office_department_*→department_*`（`com_delivery_scope`四列、`fcom001_v70_com_delivery_scope_detail`/`proj_project_split_item`/`proj_project_split_scope`各一列），并按界面显示口径补注（办事处ID/办事处编码/办事处名称/办事处版本），2026-09-18经Flyway应用成功（库版本v296），实库列名与注释已复核；列上无索引/外键引用。
- 代码：pms-module-commerce（`DeliveryScopeDO`、`DeliveryScopeDetailDO`遗留占位字段、`DeliveryScopeRespVO`、`DeliveryScopePreviewResult`、三个scope服务、`DeliveryScopeController`、`SplitScope*Command`、`CommerceAuthorityIngestService`及相关测试）；pms-module-project（`ProjectSplitItemDO`/`ProjectSplitScopeDO`、拆分草稿/预览/应用服务、拆分VO与控制器、`ProjectChildDraftFactory`、`ProjectOfficeFact`四组件改为`departmentId/departmentCode/departmentName/departmentVersion`及ApiImpl/测试）；pms-module-cutover（`CutoverTaskReqVO`、`CutoverCreateContextRespVO`、`CutoverTaskRequestCodec`请求键、`CutoverTaskViews.Summary`、`CutoverTaskController`，本分支`CutoverProjectContextPort`既有新名由此收口可编译）。
- 快照兼容：`CutoverApprovalSourceSnapshotCodec`写入新键`departmentId/departmentCode/departmentName`；解码按世代识别，旧键`officeDepartmentId/officeCode/officeName`只读兼容，新旧键混用拒绝，历史快照不重写。
- 验证：三模块`mvn compile`通过；聚焦测试通过——cutover 11/11（快照编解码5含2个新旧键兼容用例、控制器契约3、查询服务3）、commerce 18/18（范围命令服务16、公共API契约2）、project 8/8（ProjectOfficeFactApiImpl 6、公共API契约2）。`CommerceAuthorityIngestMySqlTest`未执行：需`npdms-50eb-test`隔离库凭据（既往核实1045不可用）。
- 范围保留：`orderOfficeDepartment*`（项目创建/手动建项目请求的订单办事处入参）与`pms_project.office_id`不在本裁决确认范围，维持原名，后续如需统一须需求方另行确认。

## 迁移目标列名跟源字段与来源系统前缀

### Q-MIG-DIM-003

- Status: CLOSED（2026-09-18裁决并当日落实，见末节落实回执）
- Requirement IDs: PM-03/PM-10（CRM执行单/配置列）
- Area: 迁移目标列名与源字段名及来源系统前缀的口径
- Question: 目标列名何时跟源字段原名、何时加来源系统前缀？（需求方2026-09-18指令：`applyType→application_type→apply_type`；`com_crm_execution_config.crm_project_code`直接用`project_code`；跨表同名时才追加来源系统前缀）
- Evidence: `core-field-mapping.jsonl`裁定`pm_project_property_from_sms.applyType→application_type`、`projectCode/Name→crm_project_code/name`、`pm_project_property_af_from_sms.projectType→crm_project_type`、`pm_project_product_af_from_sms/real_product_line.projectCode→com_crm_execution_config.crm_project_code`；规格DDL同名。执行单两表未在实库建表。
- Options:
  - A. 本表内列名直接跟源字段（蛇形），不加来源系统前缀；仅当目标表内与其他来源同名冲突时加前缀。
  - B. 维持`application_type`/`crm_project_*`语义化改写命名。
- Recommended technical default: A（需求方裁决）。
- Business decision required: 是；已裁决。
- Resolution: 采用A。`com_crm_execution_order`：`application_type→apply_type`、`crm_project_code→project_code`、`crm_project_name→project_name`、`crm_project_type→project_type`（源字段applyType/projectCode/projectName/projectType）；`com_crm_execution_config`：`crm_project_code→project_code`。两表内均无同名冲突，无需前缀；索引`idx_crm_execution_crm_project`随列更名为`idx_crm_execution_source_project`（`idx_crm_execution_project`已被同表`primary_project_id`既有索引占用，不能复用）。实库`imp_eng_*`表的`crm_*`列属集成推送域，不适用本口径。
- Decision owner: 需求方；AI-MIG实施Owner（落实）
- Decision date: 2026-09-18登记并落实

#### 2026-09-18 落实回执

规格DDL修订上述5列与索引（修订后SHA-256 `E425A2678D9A2EB7516709322A938996D6BB2EB79E4789BD8FEF91319C812412`），隔离MySQL 8.4容器复验66张表建成、新列名与`idx_crm_execution_source_project`索引到位、无`crm_project_code`/`application_type`残留；报告第1节字段表同步。执行单两表未建库，无需迁移；`core-field-mapping.jsonl`作为基线证据不回写，本Q为准。

### Q-MIG-DIM-004

- Status: CLOSED（2026-09-18裁决并当日落实，见末节落实回执）
- Requirement IDs: PM-10（CRM执行单配置列）；涉及平台血缘字段口径
- Area: 各表"本行来源系统"字段的统一命名
- Question: 行级来源系统字段存在`source_system`（约30张实库表及`plt_migration_source_record`既定口径）、`config_source`（仅`com_crm_execution_config`）与源字段`dataSource`（dppms各SMS源表，值'SMS'/'CRM'）三种命名，需统一（需求方2026-09-18指令）。
- Evidence: 规格`project-order-physical-schema.mysql.sql`中`source_system`约20处；实库`ast_*`/`com_*`/`cus_*`/`cut_task`/`pms_project`/`proj_*`/`fcom001_v70_*`均用`source_system`；jsonl将`dataSource` direct映射到`source_system`/`config_source`。限定语变体`master_source_system`/`order_source_system`/`candidate_source_system`语义为引用另一对象的来源系统，不属于本行来源，不在统一范围。
- Options:
  - A. 统一到`source_system`：仅改未建表的`com_crm_execution_config.config_source`，零迁移、零代码改动。
  - B. 统一到`data_source`（严格跟源字段名）：需重命名约30张已建实库表的`source_system`及全部消费代码与索引。
- Recommended technical default: A。
- Business decision required: 是；需求方已裁决选A。
- Resolution: 采用A。行级来源系统字段统一为`source_system`（平台血缘既定口径，语义"来源系统编码"而非镜像业务字段，不适用Q-MIG-DIM-003的源字段名口径）；`com_crm_execution_config.config_source→source_system`，唯一键`uk_crm_execution_config`第二列随之改为`source_system`。限定语变体保留。实库未建该表，无需迁移；`core-field-mapping.jsonl`不回写，本Q为准。
- Decision owner: 需求方；AI-MIG实施Owner（落实）
- Decision date: 2026-09-18登记并落实

#### 2026-09-18 落实回执

规格DDL修订上述1列及唯一键（修订后SHA-256 `B58870683C9C213D406740193D6E2072F093179C69D2CBB1B61B68DC213ADF51`），隔离MySQL 8.4容器复验66张表建成、`uk_crm_execution_config`列为`(tenant_id, source_system, source_config_key)`、无`config_source`残留；报告第1节字段表3处同步。Java代码无`configSource`引用，无需改码。

### Q-MIG-DIM-005

- Status: CLOSED（2026-09-18裁决并当日落实，见末节落实回执）
- Requirement IDs: 全域（迁移域所有表）
- Area: 此前裁决（列名统一、字段注释精简为界面显示口径）对全部表的适用
- Question: 需求方2026-09-18指令——此前裁决内容需对所有表适用，尤其是字段描述精简、字段名称统一。
- Evidence: 规格DDL 66张表1166条列注释中存在解释性尾缀（空值语义/时区/字典约束/单位/币种口径等）与表语境前缀（`CRM执行单镜像的`/`X主档的`/`合同回款的`等）两类冗长写法；列名口径经盘点已无`marketing_department_*`/`expansion_department_*`/`office_department_*`/`crm_project_*`/`application_type`/`config_source`残留。
- Options:
  - A. 按Q-MIG-DIM-001已确认的界面显示口径机械扩展到全部66张表：剥尾缀、剥表语境前缀、保留`：0否，1是`类值图例，不改变任何业务语义。
  - B. 逐表人工重写（不必要，口径已裁决）。
- Recommended technical default: A。
- Business decision required: 是；需求方已裁决适用全部表。
- Resolution: 采用A。字段名称统一：全部表已符合此前各裁决口径（市场/系统/拓展/行业四维、`department_`、源字段名蛇形、`source_system`），无残留。字段描述精简：规格DDL共410条注释精简（剥解释性尾缀与表语境前缀，保留值图例），SHA-256更新为`AC2D3C6E8F862A9A8DCF8632B3F495E7F57E085C9874674F2835C70A9ADC4833`。
- Decision owner: 需求方；AI-MIG实施Owner（落实）
- Decision date: 2026-09-18登记并落实

#### 2026-09-18 落实回执

规格DDL 410条注释精简，隔离MySQL 8.4容器复验66张表建成、无冗长尾缀/前缀残留；已建实库35张表464列经注释同步迁移（原V297，现为合并V294第3段；仅MODIFY注释，保留类型/字符集/排序/默认值/ON UPDATE；FK引用列以会话级`FOREIGN_KEY_CHECKS=0`执行，列定义不变），复验实库与规格注释差异0条。未建表（设备域`ast_device_sn`系、文档/组合/交付件等）随建表迁移自然落地。`core-field-mapping.jsonl`不回写，本Q为准。

#### 2026-09-18 迁移合并记录（需求方指令：正式执行前合并）

Q-MIG-DIM-001~005涉及的V294~V297四个迁移已在开发库实际应用但未正式执行，按需求方指令合并为单个`sql/migrations/V294__migration_field_naming_and_comment_alignment.sql`（净效果三段：列名更正与原值合并、办事处列名统一、全量注释精简），原四个文件删除。开发库经`flyway repair`对齐：合并V294校验和已更新，V295/296/297标记DELETE；validate全部通过。全新环境仅执行合并V294。上文各回执中的原迁移编号均指其并入合并V294的对应段落。

### Q-MIG-DIM-006

- Status: CLOSED（2026-09-18裁决并当日落实，见末节落实回执）
- Requirement IDs: COM-01@V1（F-COM-001合同主档）
- Area: 合同主档com_contract与回款/发货归属来源表的字段归并方式
- Question: 需求方2026-09-18两次指令——先要求com_contract_receivable与com_shipment_contract_reference合并字段、去除重复含义的内容形成合同主档；随后明确改判：主档com_contract直接包含全部信息，数据同步时更新对应业务最新值，不创建com_contract_summary视图。
- Evidence: F-COM-001第6章2026-09-17检查点曾批准只读视图com_contract_summary（主档优先、来源仅补缺、冲突不任选、source_conflict标记），并因"合同主档生成方式重新选择"暂停主档汇总规格与实现；本次指令解除暂停并废止视图方案。
- Options:
  - A. 只读视图com_contract_summary按2026-09-17契约实现（被需求方改判否决）。
  - B. 主档com_contract扩列直含全部合并字段，来源同步时以最新值更新主档列，不建视图（需求方裁决）。
- Resolution: B。com_contract新增17列：currency_name、contract_create_time、project_name、project_code、market_code、market_name、department_code、department_name、system_code、system_name、expend_code、expend_name、industry_code、industry_name、marketing_representative_code、marketing_representative_name、secondary_representative_code（类型与来源表同名列一致）。客户、合同类型、合同名称、合同金额、币种编码、生效/失效日期等ERP Owner既有列不重复增设。规格DDL SHA-256更新为`F9B29ED5D9EE651453AA11DBFFC771E9346D2F5B3B45374F9CEF21F075E996D6`。
- Decision owner: 需求方；AI-MIG实施Owner（落实）
- Decision date: 2026-09-18登记并落实

#### 2026-09-18 落实回执

前向迁移`sql/migrations/V300__contract_master_field_consolidation.sql`（17条ADD COLUMN，含AFTER锚定）已应用于开发库，validate 271个迁移通过。语义契约：来源表保持不变继续作为来源证据；"去除重复含义"指消费以主档列为唯一权威，不在主档外另建汇总视图；回款/发货归属同步入库时以该来源行非空值更新主档对应列（最新同步覆盖，两来源共用同义主档列）；ERP Owner字段仍由ERP主档同步负责，来源同步不覆盖。同步实现代码随后续接入任务交付，本Q不宣称同步已实现。

### Q-DEL-UI-001

- Status: OPEN（业务判定已确认；跟踪实现与运行验收）
- Requirement IDs: F-PROJ-009（模板配置中心与阶段/任务执行界面）；F-ACC-001、F-ACC-002（交付件满足与同步链）
- Area: 模板交付件在阶段/任务办理界面的上传与满足入口
- Question: 需求方2026-09-20指令要求模板定义的交付件在阶段/任务"直接展示、上传，业务相关的直接引用"。展示已按只读落地（实例视图阶段交付件块与任务展开行、任务工作台`ProjectTaskWorkbenchRespVO.deliverables`清单），但办理界面"直接上传"缺契约：交付件根状态`PENDING→ACCEPTED`目前由满足/验收链唯一驱动（满足任务/回执/结果服务与验收报告投影`acc_project_deliverable_source_*`，门禁事实提供方以`ACCEPTED`判定满足）。是否允许阶段/任务办理界面新增直接上传入口（上传→文件工件→满足事实→状态回写），还是维持满足/验收链为唯一满足来源？业务相关交付件"直接引用"（按`sourceObjectType/sourceObjectId`深链业务对象）的入口形态同样待定义。
- Evidence: `acc_project_deliverable`根status初始化为`PENDING`；`ProjectDeliverableStageGateFactProvider`以`"ACCEPTED".equals(row.getStatus())`作为门禁满足判定；满足链已有完整任务/回执/结果/归档/补偿服务；任务工作台当前仅透出只读清单，未提供上传或满足写入端点。
- Options:
  - A. 维持满足/验收链为唯一满足来源；办理界面仅展示应交清单与深链引用，不新增上传入口（最小改动，不动交付件状态机）。
  - B. 新增办理界面直接上传：需完整定义上传→文件工件→满足事实→状态回写契约（Owner、授权、幂等、与满足链的冲突裁决、审计），扩展F-ACC-002或立新Feature。
- Recommended technical default: A。在满足写入契约确认前不渲染上传按钮，避免不可用入口和绕过满足链的状态写入。
- Business decision required: 否；2026-09-20需求方已确认下述自动判定口径
- Decision owner: 需求方

## Q-CUT-ROLE-APPROVAL-20261013 — 二线与研发按角色办理

- Status: RESOLVED
- Confirmed scope: 需求方指定既有 `probSupporter`、`probRD`，随后明确“定义角色不指定具体人员，审批按角色进行”。新运行审批的二线与研发节点使用一个角色共享待办；审批前不指定人员，具备资格的成员办理后记录实际审批人。
- Resolution: 同一节点由符合冻结角色候选及当前授权范围的一名成员决定，不因候选人数大于1暂停；无有效候选仍暂停，不自动跳过。并发、版本、五项评审、服务经理等级复核、BPM受控办理、授权和租户校验保持。旧个人节点按原冻结角色和选定人员办理，已有批准版本与历史不改。
- Evidence: 当前会话2026-10-13两次明确答复；运行快照新增 `approvalMode=ROLE`、角色编码与候选范围，决定保存实际办理人；前向权限种子仅授予既有角色必要入口及审批读取/办理权限，不分配用户或修改项目范围。
- Decision owner: 需求方

## Q-CUT-ROLE-PROJECT-SCOPE-20261013 — 角色审批是否改变项目范围

- Status: RESOLVED
- Confirmed scope: 需求方先确认“按角色覆盖本租户全部割接审批”，随后收紧“角色审批有代办任务时只能拥有当前审批项目的查看权限。不扩大到所有范围”，并明确仅“只读割接审批页及冻结资料（最小范围）”。角色招募按本租户既有 `probSupporter`、`probRD`，读取资格只来自当前有效审批待办。
- Resolution: 只有未暂停、未替换的PENDING审批根及其当前PENDING角色待办，才临时授予该待办的审批页、冻结来源和明确引用文件的只读访问。未轮到、已决定、取消或暂停的节点不授予临时读取；不凭角色成员身份读取其他审批或完整项目详情。普通项目查询/创建、P4方案编制、P6归档及文件写操作保留原项目授权，不修改PROJ范围、项目成员或用户角色分配。需求方进一步纠正人员池设计：角色节点只保存候选角色/组引用，当前成员资格由SYSTEM提供，待办由BPM原生候选组维护，不保存或冻结整组候选人员；决定时记录实际办理人与其当时资格来源。既有不可变历史原件保留。
- Evidence: 当前会话2026-10-13需求方对角色范围的原答复、收紧指示及最小只读范围确认。
- Decision owner: 需求方
- Decision date: 2026-10-13

## Q-CUT-STRUCTURED-STORAGE-20261013 — 割接固定业务结构的持久化

- Status: RESOLVED
- Confirmed scope: 需求方明确“除了拓展字段不得使用json进行保存”，并确认整改整个割接模块的非拓展JSON存储。
- Resolution: 固定业务字段和关系使用明确的列或关联表；配置、任务、评估、清单、方案、审批、采集、备件及闭环均在范围内。复用已有设备、步骤、保障安排、评审及附件明细，不建立存储JSON路径和值的通用替代表，也不把固定业务结构命名为拓展字段。接口JSON传输格式与数据库JSON存储分别处理；既有不可变历史保留原件，通过前向迁移增加结构化事实，不能修改已执行迁移或丢失授权、版本、状态、失败和历史保护。角色候选语义按本页Q-CUT-ROLE-PROJECT-SCOPE-20261013。
- Evidence: 当前会话2026-10-13需求方对人员池设计的纠正及“整个割接模块的非拓展 JSON 存储”范围确认。
- Decision owner: 需求方
- Decision date: 2026-10-13

## Q-CUT-STORAGE-EXTENSION-20261013 — 动态字段定义的拓展边界

- Status: RESOLVED（方案与命名已批准，实施及验证进行中）
- Implementation authorization: 需求方已明确“表字段的名称请统一规范，然后开始实施”。按ADR-0019规范同义字段、时间、业务编码、布尔值和文件事实命名，前向DDL、DO映射及SQL同步调整。服务重启仍按现有规则另行批准；未执行的迁移、运行和业务验收不得写成完成。
- Approved design: 采用同一份[完整表结构方案](../superpowers/specs/2026-10-13-cutover-structured-storage.md)：统一采集项主数据、统一结果主表，按固定业务结构拆分答案子表；复用28张、新增47张，共75张。统一字段名称后，现有表中22张结构化调整、4张仅命名调整、2张直接复用。
- Resolution: 字段定义直接从属于现有采集项修订或任务自定义项；结果子表通过checklist_result_id复用统一结果版本，不新增独立修订。8张固定对象/单值表、5张固定业务表格行表及2张真正自定义值表承接实际答案；每条表格业务记录对应一行，各单元格值保存在具名业务列。只有真正自定义值可进入7处extension_values，固定业务、角色人员池、路由、文件和来源关系不得进入。41个既有固定JSON列保留历史原件、停止新业务写入；外部配置及当前/目标版本查询纳入实施，正式Provider缺失时保留真实失败和人工替代，不伪造接通。
- Evidence: 当前会话2026-10-13需求方最新要求“按统一主数据、统一结果主表、按固定业务结构拆分结果明细子表，重新调整完整表结构方案。复用现有修订，逐表列出全部字段、实际填写值的存放位置及需求对应，重新统计新增和复用表数量，确认后再实施。”
- Decision owner: 需求方

- Decision date: 2026-10-13；本会话最新实施授权。

#### 2026-09-20 专项修复决策与审查更正

需求方明确批准业务代码修复，并选择：“按模板规则自动判定：文件或业务成果有效且配置条件满足，即满足门禁，不新增人工审批”。阶段/任务办理界面可提交文件或关联本项目业务成果；PROJ提供当前生效计划冻结的交付件配置与条件求值，ACC保存来源/提交历史并判定，PLT核验文件。门禁重新核验当前证据，不以缓存的`ACCEPTED`或文件上传成功替代模板条件。项目权限、当前项目经理、计划/版本冲突、幂等、来源失效和不可变提交历史继续生效。

原登记中“满足链已有完整任务/回执/结果服务”的表述不准确：`Satisfaction*`是满意度业务，不是通用交付件满足审批链。保留其与验收报告既有自动来源及版本历史；通用入口不能覆盖这些业务拥有的来源关系。实际代码和浏览器验收证据按本次专项结果记录，本条不宣称项目全流程验收已通过。

### Q-SAT-DIRECT-001

- Status: OPEN（业务规则已裁决，跟踪实现与真实浏览器验收）
- Requirement IDs: F-ACC-002 / BR-FACC002-001；F-PROJ-009（业务操作与模板执行解耦）
- Question: 传统满意度仅在项目创建时冻结问卷、初验完成后初始化；未配置该任务的项目能否从满意度业务入口独立发起并关联结果？
- Decision owner / date: 需求方 / 2026-09-20。
- Resolution: 需求方明确选择“允许独立发起，复用现有评分、签字和权限”。已有任务触发路径继续有效；新增路径由具备满意度管理和项目编辑权限的用户选择当前已发布问卷，冻结题目、规则、阈值与项目权限版本，创建独立采集，不伪造项目任务或交付件绑定，不新增人工审批。评分、签字、结果文档、失效及整改重收复用既有能力；达标的当前有效结果可作为模板配置的业务成果，关联提交后仍执行交付件条件和门禁复验。
- Implementation boundary: 独立采集在项目 ACTIVE 时办理，达标使用既有 PASSED 状态；尚未关联交付件的结果不进入自动归档补偿。结果来源精确绑定采集轮次、结果版本及真实文件，不能以问卷页面访问或提交成功替代有效通过。项目关闭后保留历史查询，拒绝继续写入。
- Evidence: 本专项用户裁决与 `docs/generated/2026-09-20-project-template-special-repair.md`。本条不晋级工程 Feature/Task 状态。

### Q-TPL-FLOW-20260922-001

- Status: CLOSED（本会话流程图解释已裁决；各次配置、接入和验收边界见下方续记）
- Requirement IDs: PM-03 / F-PROJ-009（项目模板配置）；本次外部输入为 `需求/项目交付/项目交付流程 (2).vsdx`。
- Scope: 仅限新模板 `TPL-DELIVERY-FLOW-20260922`（ID `993009900014`），不改写其他模板、既有项目计划或 PRD 全局业务语义。
- Decision owner / date: 需求方 / 2026-09-22 当前会话。
- Resolution:
  1. 工前准备完成后，施工计划与实施方案并行办理；两者都完成后进入实施部署。
  2. “制定施工计划”页的“审核实施方案”按“审核施工计划”配置。
  3. 本模板严格按图，非直签项目跳过初验和终验，进入现场培训；该模板例外不外推为其他模板的验收规则。
  4. CRM 改单、物料申领、外采事前申请按独立流程处理，不设置统一回流，也不新增等待三条支线审批完成的汇合条件。
- Implementation boundary: 新模板已保存为 DRAFT。指派、工前分支判断、计划审批、方案审核、割接完成的模板可读依据仍有接入缺口；不能以手工提交、页面跳转、文件上传或外部请求成功代替相应业务完成。此处关闭的是已裁决的业务问题，不代表发布预检或项目全流程验收通过。
- Evidence: [配置与验证记录](../generated/2026-09-22-visio-delivery-template/README.md)；新模板 Designer 的 `sourceEvidence` 保留原图节点、四项用户裁决及映射。

2026-09-22 接入续记：按需求方后续授权，指派、施工计划审批、普通方案审核及割接成功归档已接入模板，规则字段名称从项目实体/公开响应元数据自动发现并支持参数管理。当前预检剩 S1 工前分支依据、S3 配置检查和重大项目适用复审；保留阶段缺失规则以阻止发布，未把普通方案批准解释为全部方案工作完成。证据见 [接入增量](../generated/2026-09-22-visio-delivery-template/integration/README.md)。

2026-09-22 后续裁决及落实：需求方确认忽略“配置工具自动检查”；工程管理部承担重大复审；项目经理填写并留痕的工前确认表提供三个物料判断；重大项目按 CRM 级别“办事处级重大项目”“市场部级重大项目”“公司级重大项目”命中。级别缺失继续遵守 SCH-05 的依据缺失阻断，不推定普通、不伪造 CRM 值。工前条件与复审路径已接入，独立支线仅配置判断和办理提示，不代表已启动外部流程。模板保持 DRAFT，源图 S0～S5 范围内配置预检通过；实际验证范围及当前版本见 [剩余内容交付记录](../generated/2026-09-22-visio-delivery-template/remaining/README.md)。以上续记替代前述“仍待来源/工具接入”的当前结论，保留原阶段记录作为历史。

2026-09-22 更正（审核分级对 NULL 手工项目的语义）：需求方当日在 S3 验收推进中明确改判——`SOLUTION_ORDINARY_REVIEW_ALLOWED` 不得以 `notNull` 叶子排除 NULL：手工项目按 PM-01 永久保存 `majorProjectLevel=NULL`（界面“不适用”），其审核分级按普通审核（服务经理一级审核）处理；模板规则改为 `OR(field=null, AND(field!='', NOT(field in 三类重大值)))`，空字符串仍保持阻断。原续记“级别缺失继续遵守 SCH-05 的依据缺失阻断，不推定普通”自本更正起废止；SCH-05“命中依据不完整→阻止”继续适用于规则冲突、字段不可用（UNKNOWN）与空字符串，不适用于 PM-01 定义的 NULL“不适用”语义。已发布修订（rev1～rev3）冻结快照不回改，修正随新修订发布；已冻结旧修订的项目不换绑（存量项目重建承接，历史项目 992203060021 停在 S3 保留为 S0～S2 证据）。

### Q-TPL-FLOW-20260922-002

- Status: RESOLVED（需求方 2026-09-30 裁决：不扩展，维持 S0～S5）
- Requirement IDs: PM-03 / F-PROJ-009；正常项目闭环。
- Question: 原图六页止于验收交维，是否另行补充既有 S6 项目闭环？当前会话已提出可选问题，尚未获得需求方答复。
- Implementation boundary: 当前按图配置 S0～S5；S5 终点不等于项目自动关闭。未配置额外闭环审批、归档或回款阈值；此问题只阻止超出原图的 S6 扩展，不阻止已确认部分的配置。
- Decision owner: 需求方。
- Evidence: [配置与验证记录](../generated/2026-09-22-visio-delivery-template/README.md)。
- Resolution: 需求方2026-09-30裁决不扩展S6，模板阶段域维持S0～S5，不新增闭环审批、归档或回款阈值配置。Decision date: 2026-09-30。

### Q-TPL-FLOW-20260922-003 — 工前判断并回原工勘来源

- 状态：CLOSED（需求方 2026-09-22 当前会话明确更正）。
- 更正：“工前确认表已经包含在工勘记录中，不需要单独填写”。取消先前独立工前表的输入与额外门禁，历史只读保留。
- 字段裁决：“8 上架加电是否需要原厂实施，9 是否需要导轨、托盘，就是”。按需求方指定分别作为原厂与领料/BOM 分支依据；物料环境项复用同份工勘原字段。不把这一映射推定为其他业务的普遍等价关系。
- 落实：工勘表单新修订通过配置维护条件，不新增输入字段、不硬编码字段名；S1 复用原四任务，三个独立支线不设统一回流。验证见 [工勘来源更正回执](../generated/2026-09-22-visio-delivery-template/survey-source/README.md)。

2026-09-22 Q-TPL-FLOW-20260922-003 后续明确：需求方要求“历史工前确认记录从代码中剔除”。删除该功能页面、专用接口及全部服务/数据访问/表单策略/规则兼容代码；此前的应用只读保留口径由本次要求替代，数据库历史与已执行迁移仅作归档。工勘原字段与支线配置继续沿用。

### Q-TPL-FLOW-20260922-004 — S4 割接任务完成依据运行时不可达

- Status: RESOLVED（需求方 2026-09-30 裁决：以最新模板过渡方案为准结案，历史项目不回刷）
- Requirement IDs: PM-03 / F-PROJ-009（模板规则已配置）；F-CUT-002（taskv2 入口未注册生产 Bean）
- Area: 交付模板 S4「割接上线」（DEPLOY_CUTOVER）完成事实的产生入口
- Question: rev6 模板（revision 993009900031）已把 `DEPLOY_CUTOVER_DONE` 冻结为 `BUSINESS_FACT(CUTOVER_COMPLETED)`，但当前运行系统没有任何合法入口能产生该事实——rev6 项目（含手工 NULL 项目）全部卡死在 S4，无法推进 S5。这是配置与运行时能力不匹配的接入边界缺口，如何处置？
- Evidence（2026-09-22 验收实测，项目 992203060026）:
  - 完成事实判定：`CutoverCompletionProvider.load` 要求 `cut_task.task_status=ARCHIVED` 且 `current_stage=P6`，并存在 `cut_closure` 行 `status_code=ARCHIVED`、`final_result_code=SUCCESS`、`submitted_at/archived_at/result_ref` 齐全。
  - 新域 `cut_task` 唯一写入点是 taskv2 `CutoverTaskApplicationService.create`；其 REST 入口 `CutoverTaskController`/`CutoverClosureController` 等无 `@RestController/@Component`，注释明确“正式Owner齐备前不注册生产Bean”，契约测试断言非组件。实测 `GET/POST /admin-api/api/v1/pms/cutover-tasks*` 全部 404。
  - 旧版 `POST /admin-api/pms/cut-task/create` 已 `@Deprecated` 退役（写退役表，不产生新域事实），按废弃路径约束不得承接新 Feature 业务。
  - 同任务模板描述文案仍是“【待接入完成依据】……当前保留空完成规则”，与 rev6 已配置的完成规则相互矛盾（文案未随规则接入更新）。
- Why it blocks: S5_ENTRY gate 引用 `S4_COMPLETED`；割接完成事实不可达则所有按模板创建的项目无法进入验收交维阶段，“根据流程图配置一个完整的项目模板”目标在 S4→S5 边界被阻塞。直接写库产生事实违反“不得绕过状态机”约束，验收中未采用。
- Options:
  - A. 授权注册 taskv2 REST 生产入口（代码变更，需按 Feature/Task 流程走）后走完整 P1～P6 割接闭环。
  - B. 修订模板规则，在割接 Owner 正式接入前为 DEPLOY_CUTOVER 配置可替代的合法完成依据（需需求方裁决语义）。
  - C. 维持现状，接受 S4→S5 为当前模板已知阻塞点并明确披露。
- Recommended technical default: A（与新平台演进方向一致）；短期验收按 C 披露。
- Business decision required: 是
- Decision owner: 需求方；CUT Owner（入口注册）、模板配置负责人（规则修订）参与
- Decision date: 2026-09-22 登记

2026-09-24 验收续记（不改变本问题状态）：rev7 起模板为 DEPLOY_CUTOVER 配置门禁 BPM 引用，部署过渡流程定义 `PMS_DELIVERY_CUTOVER_APPROVAL`（描述明示"割接完成外接接入前，审批通过即视为割接完成；后续替换为割接系统业务事实"）。验收项目 992203060028（直签）与 992203060032（渠道）均按"BPM 审批通过 → 事实 APPROVED → 任务完成 → S5 激活"走通，未绕过状态机或直写业务事实；正式处置（taskv2 生产入口注册或规则语义裁决）仍待需求方，过渡定义替换为真实割接业务事实前本问题保持 BLOCKED_BY_SPEC。证据见 [发布与全链验收记录](../generated/2026-09-22-visio-delivery-template/acceptance/README.md)。

2026-09-26 纯界面驱动复核（不改变本问题状态）：fresh 库（npdms_domain_test）rev10 模板项目 PJT2026000001 在"全部操作限于项目详情界面、不用脚本推进"的约束下复核本边界。S4 前五任务（DEPLOY_COORDINATE/ARRIVAL/HARDWARE/CONFIG/JOINT）全部经任务办理抽屉界面完成；DEPLOY_CUTOVER 确认无任何合法界面发起入口：①任务办理抽屉审批面板对 formType=20（业务表单）定义按设计拒绝渲染发起表单（`TaskApprovalForm.load` 拦截，提示"该审批使用原模块业务表单，请通过业务页面绑定办理"；自 9141716e2 起存在）；②rev10 模板 DEPLOY_CUTOVER 绑定仅 APPROVAL、无业务视图快照（工作台 businessView=null），项目详情内"割接上线 5.5"视图渲染 taskv2 界面但其 REST 生产 Bean 按契约测试刻意未注册，实测 `GET/POST /admin-api/api/v1/pms/cutover-tasks*` 仍全部 404；③旧版 `POST /admin-api/pms/cut-task/create` 维持退役，不承接新流程；④BPM 手工发起 `PMS_DELIVERY_CUTOVER_APPROVAL`（BPMN 无启动表单、节点 candidateStrategy=35 发起人自选）无法携带 `pmsTaskProjectId/pmsTaskId/pmsTaskExecutionId/pmsTaskContractId` 等身份变量，实例与任务作用域必然 IDENTITY_MISMATCH→UNKNOWN，不能作为完成依据。结论：在界面约束下 S4→S5 为当前模板已知阻塞点（选项 C 形态），项目停于 S4（5/6 任务 DONE）；不采用脚本/API 直推审批，正式处置仍待需求方。证据见 [2026-09-26 复制模板闭环验证记录](../generated/2026-09-26-visio-copy-closure/README.md)。

2026-09-30 查证与结案（需求方当前会话裁决）：最新模板修订 rev3（id 993009900015，template 993009900011，2026-09-30 00:51 发布）中 `DEPLOY_CUTOVER_DONE` 规则名为"割接审批通过即完成"，表达式 `CONSTANT true`，任务绑定 `pms-cutover-done-approval` 审批流——不再冻结 `BUSINESS_FACT(CUTOVER_COMPLETED)`，S4 经审批通过正常闭环；模板描述自述为过渡方案（"当前为割接完成外接接入前的临时依据，后续将替换为割接系统业务事实（CUTOVER_COMPLETED）"），定义快照缺口清单仍登记 `task:DEPLOY_CUTOVER` 缺业务完成事实；taskv2 REST 五个 Controller（Task/Plan/Approval/Checklist/Closure）经查证仍无 `@RestController/@Component`，未注册生产 Bean。需求方裁决采用过渡方案为准结案（选项 B 形态的当前落点），历史项目不回刷；"替换为割接系统业务事实 CUTOVER_COMPLETED + taskv2 REST 生产入口注册"登记为同一割接系统 Owner 的后续待办，不再作为本问题阻塞项。

### Q-TPL-FLOW-20260922-005 — 满意度结果来源投影与业务文档收集的 CURRENT 归属竞态

- Status: OPEN（技术让位已按需求方 2026-09-24 会话确认实施；证据归属语义待规格裁决）
- Requirement IDs: ACC-04；交叉 ACC-02、PM-03（交付件证据归档）
- Area: D_SATISFACTION 交付件 CURRENT 来源版本的双自动投影归属
- Question: 满意度结构化结果（ACC-04 SatisfactionResult 来源）与结果 PDF 业务文档自动收集（PM-03 ProjectDeliverableSubmission 来源）都会写 D_SATISFACTION 的 CURRENT 来源版本；两者调度无固定先后，最终 CURRENT 应归谁、落败方以什么关系状态留痕？
- Evidence（2026-09-24 验收实测，项目 992203060032 与 992203060028 对照）:
  - 028：结果投影先到（00:06:00 占 CURRENT），PDF 收集后到被收集侧让位跳过（`ProjectDeliverableSubmissionService.java:268` 对非本类型 CURRENT 直接 return），终态 CURRENT=SatisfactionResult 且 ARCHIVED。
  - 032：PDF 收集先到（02:12:30 占 CURRENT，交付件转 ACCEPTED），结果投影后到命中 `SATISFACTION_SOURCE_TYPE_CONFLICT`（修复前 `SatisfactionResultSourceProjectionService.java:123` 抛异常），outbox 事件 `sat-result:2102823061784588290:result-recorded` 每分钟无限重试，ACC-04 来源行不落库、结果归档停留在 PENDING_COMPENSATION。
  - 已知同类历史噪音：outbox 事件 `05afac02`（SatisfactionTaskCreated，09-21）重试 1300+ 次常驻。
- 已实施的技术修复（2026-09-24，需求方确认"修复为让位落库"）：`mayBecomeCurrent` 遇非 SatisfactionResult 类型 CURRENT 不再抛异常，按既有 SUPERSEDED 语义落库留痕并归档，不抢占 CURRENT；与收集侧让位行为对齐。单测 `foreignCurrentSourceDefersAndRecordsHistoryOnly` 覆盖。
- Why it remains open: 让位落库消除了互斥冲突和重试噪音，但"结构化结果与收集文档并存时谁应拥有 CURRENT"仍是业务语义问题——032 终态 CURRENT=PDF 提交行（NOT_REQUIRED 归档），028 终态 CURRENT=结构化结果（ARCHIVED 归档），两项目同类业务最终证据形态不一致。
- Options: A. 结构化结果优先（收集侧已让位，结果侧恢复抢占语义需处理已在途的收集行）；B. 先到先得+落败方 SUPERSEDED 留痕（当前实现）；C. 双来源并存、CURRENT 指向结构化结果并把 PDF 作为其附件。
- Recommended technical default: 待需求方裁决；当前实现为 B（最小对齐、不改既有归属）。
- Business decision required: 是（归属语义）；技术让位行为已按需求方本轮确认完成。
- Decision owner: 需求方；ACC Owner、PM 交付件 Owner 参与影响分析
- Decision date: 2026-09-24 登记

### Q-TPL-FLOW-20260922-006 — RA 有效版是否应自动满足 D_REQUIREMENT 交付件

- Status: RESOLVED（2026-09-24 裁决选 B；引擎能力、模板 rev10 发布与真实浏览器验收均已完成）
- Requirement IDs: PM-03 / F-PROJ-009（模板完成条件与交付件配置）；ACC-04（交付件满足链）
- Area: rev9 模板 S1「需求分析」任务（PRE_REQUIREMENT）完成条件中 DELIVERABLE(D_REQUIREMENT) 的自动满足来源
- Question: 需求方 2026-09-24 报告"需求分析已 FROZEN effective=true，但任务 PRE_REQUIREMENT 仍 IN_PROGRESS、交付件 D_REQUIREMENT 仍 PENDING，有效版未自动关联交付件/任务结果"。rev9（revision 993009900014，项目 992203060035）冻结契约：PRE_REQUIREMENT 完成条件 = `BUSINESS_FACT(REQUIREMENT_ANALYSIS_COMPLETED, ALL)` **AND** `DELIVERABLE(D_REQUIREMENT)`；D_REQUIREMENT 的 automaticSources 仅 `SOL.REQUIREMENT_DOCUMENT`（RA 表单附件文档），RA 冻结产生的 `REQUIREMENT_ANALYSIS_COMPLETED` 业务成果不在自动归集范围内。是否应把"RA 有效版成立"配置为 D_REQUIREMENT 的自动满足来源，或调整任务完成条件？
- Evidence（2026-09-24 浏览器实测，项目 992203060035）:
  - 设计内路径已走通：任务抽屉「提交与查看」→ 材料来源「关联业务成果」→ 业务成果类型「需求分析成果」→ 有效成果唯一候选（业务对象 2102942473917259777 · 成果 2102942473917259778 · V1，即 RA 当前有效版）→ 提交材料。响应 `satisfied=true, reason=DELIVERABLE_RULE_SATISFIED`，D_REQUIREMENT `PENDING→ACCEPTED`（version 1），PRE_REQUIREMENT 经 reevaluate 异步自动 `IN_PROGRESS→DONE`，UI 复核任务节点"完成 100%"、交付件"已验收"。截图与回执见 `docs/generated/2026-09-22-visio-delivery-template/browser-closure/t177-*`。
  - 后端 journal（通道 76 formation seq1 + 变更 seq2）、BusinessResultChannel outbox、交付件评估、任务重评链路均按设计工作，无投影缺陷。RA revision `execution_snapshot.execution=null`（创建时未带任务绑定）不影响 journal 按 projectId 路由的投影链路。
- Options:
  - A. 维持现状：RA 冻结不自动满足 D_REQUIREMENT，用户在任务抽屉关联业务成果或上传文件（当前 rev9 行为，已验证可用）。
  - B. 修订模板：把 `REQUIREMENT_ANALYSIS_COMPLETED` 业务成果加入 D_REQUIREMENT 的 automaticSources（需模板配置能力支持业务成果类型自动归集，当前 automaticSources 仅支持文档类来源）。
  - C. 修订模板：PRE_REQUIREMENT 完成条件去掉 `DELIVERABLE(D_REQUIREMENT)` 项，任务完成仅依赖业务事实。
- Recommended technical default: 待需求方裁决。A 不动模板且路径可用；B/C 均为模板冻结语义变更，需新修订发布，且 rev9 已有项目（035）不换绑。
- Resolution（2026-09-24 需求方裁决）: 采用 B。将 RA 冻结产生的 `REQUIREMENT_ANALYSIS_COMPLETED` 业务成果配置为 D_REQUIREMENT 的自动满足来源（rev9 现值 automaticSources 仅 `SOL.REQUIREMENT_DOCUMENT`，allowedSources 已含 BUSINESS_RESULT）。当前自动归集仅支持文档类来源，需先扩展业务成果类型的自动归集能力，再随模板新修订发布；rev9 存量项目（035）不换绑，维持手动关联路径。
- 2026-09-24 落实回执：引擎能力已扩展——`ProjectResultSubscriptionDelivery` BusinessResultChange 分支在同一持久投递内 `publisher.publishEvent(event)`；新增 `ProjectBusinessResultCollectionService`（formation 事件监听，Type 三点串 `ownerContext.entityType.resultType` 匹配 `rules.documentTargets` 后逐交付件归集）；`ProjectDeliverableSubmissionService.collectBusinessResult`（MANDATORY 传播、幂等键 `result-change:{eventId}`、sourceType=BUSINESS_RESULT、supersede 旧来源、revalidate 后冻结 decisionEvidence 并唤醒门禁/任务重评）。测试：`ProjectResultSubscriptionRecoveryTest` 24/24（新增已提交成果投递扇出用例）、`ProjectDeliverableSubmissionServiceTest` 19/19（新增 3 用例）、`ProjectBusinessResultCollectionServiceTest` 2/2。模板 rev10 已发布（revision 993009900037）：D_REQUIREMENT `automaticSources` 追加 `SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED`，与 rev9 全量叶子差异仅此配置及 PRE_CONTACT routePath（见 Q-007）。
- 2026-09-24 真实浏览器验收回执（验证项目 992203060038，rev10 实例化）：RA 冻结首轮被 `REQUIREMENT_ANALYSIS_CONTENT_INVALID` 拒绝，根因为 RA 表单必填字段项目目标/网络拓扑未填（`RequirementAnalysisEntityProvider.validateComplete` 去标签非空白校验），浏览器补填后冻结成功（非本次引擎改动引入）。浏览器「从查看版本创建草稿 → 保存 → 完成并冻结」触发 V3 冻结后：`PMS.BusinessResultChangeCommitted.v1`（formation=true）由载有新代码的后端投递，D_REQUIREMENT `PENDING→ACCEPTED`（version=2，submission `source_type=BUSINESS_RESULT`，request_key=`result-change:1c197af0-…`），PRE_REQUIREMENT「需求分析」同刻自动 `IN_PROGRESS→DONE`；交付件面板显示「已满足」，弹窗提交历史含「业务成果关联」条目。截图与回执：`browser-closure/rev10-25~32-*`、`rev10-step5~7-receipt.json`。
- 部署注意事项：验证环境中首轮冻结（V1/V2）由未含 `publishEvent` 的旧 jar 实例处理，事件被标记 DELIVERED 但归集未触发；重建部署后由新冻结周期验证全链。生产/测试环境发布本引擎改动时，存量未归集成果需按重放或新业务动作补齐，不影响引擎设计。
- rev10 模板杂项披露：相对 rev9 第三处叶子差异为 S0 `schedulePercentage` 0.01→None（rev4 孤立值，发布草稿中被清空；后端无运行时消费方，仅模板设计器展示），无业务影响，rev10 保持现值。
- Business decision required: 是；已裁决（选 B）。
- Decision owner: 需求方；PROJ 模板 Owner、ACC 交付件 Owner 参与影响分析
- Decision date: 2026-09-24 登记；2026-09-24 裁决

### Q-TPL-FLOW-20260922-007 — PRE_CONTACT 页面路由指向项目列表页，无法承接任务业务办理

- Status: RESOLVED（2026-09-24 裁决选 A；模板 rev10 路由更正与任务抽屉嵌入映射均已落实并完成真实浏览器验收）
- Requirement IDs: F-PROJ-009（模板任务定义）；PM-01（任务工作区 PAGE 绑定）
- Area: rev9 模板 S1「客户需求沟通」任务（PRE_CONTACT）的 PAGE 绑定 routePath
- Question: rev9 冻结的 PRE_CONTACT routePath 指向项目列表页而非工前业务页面（business_custom_create_path 同样指向项目列表），任务抽屉内嵌区无法落到实际业务界面，只能展示兜底提示。PAGE 路由应指向哪个工前业务工作台？
- Evidence（2026-09-24 浏览器实测，项目 992203060035）:
  - 任务抽屉渲染 PAGE 绑定兜底 alert（"本任务通过专用页面办理业务…"），显示 routePath 为项目列表页路径；内嵌映射表中无该路径对应组件。
  - PRE_CONTACT 任务最终按 TASK_NATIVE 状态操作提交完成（模板完成条件未依赖该页面业务事实），闭环未受阻；但"业务在工作区直接办理"的产品规则在该任务上不可满足。
- Options: A. 模板修订将 routePath 指向实际工前业务页面（需明确目标页面）；B. 改为 TASK_NATIVE 绑定（与当前实际完成方式一致）；C. 维持现状并在文档披露。
- Recommended technical default: 待需求方裁决（A 需先明确工前业务目标页面；B 语义最简）。
- Resolution（2026-09-24 需求方裁决）: 采用 A，需求方明确"用户联系人就是客户联系人界面"——PRE_CONTACT（客户需求沟通）的 PAGE routePath 应指向客户联系人工作台：菜单 18010（component `pms/customer/contacts/index`，父级 path `/customer-asset`），完整路由 `/customer-asset/customer-contact`。rev9 冻结现值 `routePath=/pms/project-management/projects`（项目列表页，已从 rev9 execution_snapshot 实证）确认为模板配置错误，随模板新修订发布更正；任务抽屉 pageEmbedComponents 需同步接入该路径的嵌入组件。
- 2026-09-24 落实回执：模板 rev10（revision 993009900037）已发布，`tasks[PRE_CONTACT].workBinding.parameters.routePath=/customer-asset/customer-contact`；前端 `ProjectFlowPanel.vue` pageEmbedComponents 接入该路径映射 `CustomerContactsWorkbench`（`pms/customer/contacts/index` 原生支持 `projectId` prop，零改动复用）。真实浏览器验收（项目 992203060038）：打开 S1「用户联系人获取」（PRE_CONTACT）任务抽屉，内嵌工作区渲染客户联系人工作台本体（客户/姓名/状态筛选与查询、重置、新增联系人、载入客户联系人、关联项目客户、变更历史），不再是项目列表页或兜底提示。截图与回执：`browser-closure/rev10-33/34-*`、`rev10-step8-receipt.json`。
- Business decision required: 是；已裁决（选 A，目标页面=客户联系人工作台 `/customer-asset/customer-contact`）。
- Decision owner: 需求方；PROJ 模板 Owner 参与影响分析
- Decision date: 2026-09-24 登记；2026-09-24 裁决

### Q-TPL-FLOW-20260922-008 — 验收报告嵌入视图缺 FILE_WRITE 动作，任务内附件上传永久禁用

- Status: OPEN（2026-09-24 需求方裁决选 A；动作集与前端契约改动已落地，嵌入上传的真实浏览器验收待有进行中验收活动的项目验证）
- Requirement IDs: ACC-02（验收报告版本与附件）；PM-01（任务工作区业务内嵌）
- Area: ACC_ACCEPTANCE_REPORT BusinessView 嵌入任务抽屉时的 allowedActions 与前端 canUpload 的契约
- Question: `AcceptanceTaskBusinessObjectProvider` 明确注释 FILE_WRITE 不是 ACC 动作集成员（附件走 PLT 独立权限），而前端 `ReportDraftEditor.canUpload` 要求 allowedActions 含 FILE_WRITE——嵌入视图内「上传并绑定」按钮永久 disabled；报告发布条件又要求附件非空，导致任务内嵌路径只能改稿、无法补附件，必须跳到「初验&终验」独立面板（非嵌入 allowedActions 限制路径）完成。这是缺陷还是"嵌入视图只读、办理走独立入口"的设计意图？
- Evidence（2026-09-24 浏览器实测，项目 992203060035）:
  - 任务抽屉内嵌报告编辑器：创建草稿成功（UPDATE/PUBLISH 在动作集）、附件「上传并绑定」disabled（`canUpload=false`，FILE_WRITE 不在 ownerActions）。
  - 同一活动在项目详情「初验&终验」独立面板：上传并绑定成功、发布成功（code=0），活动转 COMPLETED，D_INITIAL 自动归集 ACCEPTED。
  - 后端 `AcceptanceTaskBusinessObjectProvider.java:217` 注释："FILE_WRITE is not an existing ACC action; PLT retains its separate upload/reference policy."
- Options: A. 嵌入动作集补 FILE_WRITE（前端契约对齐，服务端仍按 PLT 权限校验）；B. 维持嵌入只读+独立入口办理（补文档与 UI 提示）；C. 前端嵌入视图隐藏上传区并提示"从独立入口上传"。
- Recommended technical default: 待需求方裁决（A 体验最顺；B/C 均为披露现状）。
- Resolution（2026-09-24 需求方裁决）: 采用 A。验收报告 BusinessView 嵌入任务抽屉的 allowedActions 补充 FILE_WRITE，使前端 `canUpload` 契约成立、任务内可直接「上传并绑定」；服务端附件上传仍按 PLT 独立权限校验，不降低授权边界。`AcceptanceTaskBusinessObjectProvider` 中 "FILE_WRITE is not an existing ACC action" 的契约注释同步更新为新裁决口径（嵌入动作集含 FILE_WRITE，文件操作授权仍归 PLT）。
- 2026-09-24 落实回执：`AcceptanceTaskBusinessObjectProvider` 任务绑定草稿命令分支随 `UPDATE` 同条件授予 `FILE_WRITE`，前端 `operationHost` 既有透传逻辑与 `ReportDraftEditor.canUpload`（`allowed('FILE_WRITE') && checkPermi(['pms:file:upload'])`）零改动即成立——文件操作仍由 PLT 端点按 `pms:file:upload` 鉴权。后端 `AcceptanceTaskBusinessObjectProviderTest` 25/25 通过（动作集断言按新契约更新，PUBLISH 仍仅限直接发起）；前端 `AcceptanceReportEmbedding.runtime.spec.ts` 14/14 通过（新增 Owner 授 FILE_WRITE 且 PLT 上传权限有效方可嵌入上传用例；既有 fail-closed 与"UPDATE 不隐含 upload"断言保持）。全量 `pnpm ts:check` 仅 3 个既有无关文件报错（`delivery-business/site-survey`、`engineering/installation`、`engineering/site-survey` 的 maintenance 可空告警），本次涉及文件无类型错误。
- Business decision required: 是；已裁决（选 A，嵌入补 FILE_WRITE）。
- Decision owner: 需求方；ACC Owner、前端 Owner 参与影响分析
- Decision date: 2026-09-24 登记；2026-09-24 裁决
## 项目列表筛选维度口径（2026-09-24 界面整治项）

### Q-LIST-FILTER-20260924-001

- Status: RESOLVED（需求方 2026-09-30 裁决：按推荐默认落地）
- Requirement IDs: 项目管理→项目列表（新链 `/pms/projects/page`）；本次界面整治项，未关联独立 Feature 编号
- Area: 项目列表筛选维度口径
- Question: 用户2026-09-24要求项目列表增加筛选维度：项目编号、项目名称、合同号、办事处、销售人员、项目状态、实施状态、流程状态、发货状态、重点项目级别、服务经理、项目经理、创建时间&闭环时间&刷新时间、实施方式、产品型号、所属公司、代理商/服务商、序列号、维保状态。其中"实施状态、流程状态、发货状态"三个维度在现链无已定义字段或派生口径，"维保状态"取值域未受控，如何定义？
- Evidence:
  - 实施状态：`proj_project`无独立字段；候选为`assignment_status`（UNASSIGNED/ASSIGNED 指派状态）或阶段任务进度，语义未定义。
  - 流程状态：BPM 审批流程状态无项目级单一取值；项目级闭环审批仅存在流程实例，列表无口径。
  - 发货状态：现链未定义；候选为按`ast_device.shipment_time`按项目派生（未发货/部分发货/全部发货，见`需求/项目交付/项目交付页面数据DemoV2.html`三态表述），需规格确认。
  - 维保状态：`ast_device.warranty_status`为 CRM 同步值（如`FAST001_WARRANTY_ACTIVE`），本地无字典与受控取值域（受权威来源映射约束，不得臆造取值）。
- Recommended technical default: 前三个维度不渲染筛选控件；维保状态按原文等值文本过滤临时实现（设备列表页同为原文展示），取值域待规格定义。
- Blocking scope: 仅阻止上述四个维度的最终口径；不阻断其余可解析维度的实现（项目编号/名称/状态/实施方式/项目经理/合同号/办事处/所属公司/销售人员/服务经理/重点项目级别/创建&闭环&刷新时间/产品型号/序列号/代理商服务商）。
- Decision owner: 需求方
- Resolution: 需求方2026-09-30裁决按推荐技术默认落地：实施状态、流程状态、发货状态三维度不渲染筛选控件（派生口径待规格定义后再纳入）；维保状态按`ast_device.warranty_status`原文等值文本过滤，取值域维持CRM同步原文展示，不臆造字典。其余已定义维度照常实现。Decision date: 2026-09-30。
- Updated resolution (2026-10-09, FB-03): 需求方当前会话明确“实施状态是当前的状态，流程状态当前不需要，发货状态是项目有没有已发货的序列号”。实施状态复用现有项目状态，流程状态不增加筛选；发货状态采用有/无已发货序列号的项目级口径，由资产Owner提供归属与发货事实，项目模块保留可见范围。该最新裁决替代此前“三个状态独立”及暂不渲染发货条件的反馈口径，未定义三态或发货比例。

### Q-SYNC-DEVICE-20260928-001 — 设备序列号同步任务的设备侧三步去留

- Status: RESOLVED（需求方 2026-09-28 当前会话裁决："直接纯复制，当前的速度太慢了"）
- Requirement IDs: DPPMS 迁移·设备序列号同步（int_sync_task 2102825819916980226）
- Area: 同步任务范围口径
- Question: 任务定义含三个更早存在的 ast_device 侧步骤——deviceMaster（主档 product_code/internal_serial_no 维护）、secondaryMaster（barcode2 附加SN建档）、deviceShipment（该SN最新发货投影 shipment_record_id/shipment_time/package_no/contract_no/secondary_sn/secondary_item）。用户 2026-09-28 质询"为啥还在这双写"，即该任务是否应只写两张承接表（ast_device_shipment/ast_device_relationship），设备侧步骤摘除或另立任务。
- Evidence:
  - event 步已是纯复制（updateColumns=[]、无 lookup、源字段直填），27 行×23 列抽查全一致。
  - `DeviceQueryMapper.xml` 设备列表直接 SELECT d.shipment_time/package_no/contract_no/shipment_record_id，为 deviceShipment 投影的消费方。
  - 2026-09-28 全清复制删除 64.6 万 data_sync 行后设备旧 shipment_record_id 全部悬空，deviceShipment 是唯一重建机制（运行中 10 分钟内更新 14.4 万台，全库 893,518 台设备、876,594 台挂引用）。
  - 吞吐对比：含设备更新综合 ~630/s；纯承接表单表 ~1000/s。
- Decision: 摘除 deviceMaster/secondaryMaster/deviceShipment 三步，任务与 device-sn 模板均改为只写两张承接表（ast_device_shipment/ast_device_relationship）。设备列表的发货投影列（shipment_record_id/shipment_time/package_no/contract_no）自本次裁决起不再由该任务维护，存量悬空引用与后续维护去向由需求方另行安排。
- Blocking scope: 仅阻止设备侧三步的长期去留；不阻断本次全清复制与承接表正确性验证。
- Decision owner: 需求方。

### Q-MIG-V331-20260929-001 — V331 交付件绑定修复迁移不支持全新空库构建

- Status: RESOLVED（需求方 2026-09-30 裁决：迁出主链）
- Requirement IDs: F-PROJ-009（V331__fproj009_deliverable_binding_restore.sql 为该需求的历史数据修复）；本次发现于"项目创建取值(合同单入口)"V361 验证环境搭建
- Area: 数据库迁移 / 全新环境供给
- Question: V331 是按生产库实况捕获快照生成的一次性外科修复迁移（restore_fproj009_deliverable_binding.py），其 before-guard 逐行比对 six DRAFT 模板 designer_document 的修复前状态；全新空库按 V1..V361 顺序构建时，guard 前置查询返回空/不一致，迁移在 V331 失败，导致全新环境（含 CI、新同事本地、演示环境）无法用完整迁移链初始化。
- Evidence:
  - 新建 Docker 卷执行 `flyway migrate`（./sql/migrations 全量 301 个）在 V331 报 before-guard 失败；同一文件集在持有历史数据的既有卷上通过。
  - V331 头部声明"只补本库仍缺失的表结构/修复指定环境数据"，未声明支持全新构建；V1..V330 无任何迁移产生其 guard 期望的 designer_document 状态。
  - 当前本地验证库改用排除 V331 的过滤目录（.run/flyway-no-v331）构建成功到 V361；未修改任何迁移文件、未削弱 guard。
- Recommended technical default: 若需求方确认 V331 仅面向特定既有环境，则把它改为条件执行（guard 不满足时跳过并留审计记录）或迁出主迁移链（移入一次性运维脚本目录+登记执行环境清单）；两者都会改动已执行环境的 flyway_schema_history 语义，需要明确决策。
- Blocking scope: 仅阻止"单一迁移链支持全新空库构建"这一能力；不阻断本机既有库、工作区库的升级，也不阻断当前 V361 业务验证（本地验证库已可构建）。
- Decision owner: 需求方（涉及迁移历史治理，非单一代码决定）。
- Resolution: 需求方2026-09-30裁决迁出主链——V331移入一次性运维脚本目录并登记已执行环境清单，主迁移链恢复全新空库可构建；已执行库的`flyway_schema_history`V331记录保留不动，文件迁出后的missing-migration处理随迁出实施一并配置。迁出实施（目录、清单、迁移链验证）为后续工程工作。Decision date: 2026-09-30。
- Implementation 2026-09-30: 迁出已执行——V331移入`sql/oneoff/`（README登记compose主库与fresh库两环境success=1及重放方式）；compose.yaml migrate服务加`FLYWAY_IGNORE_MIGRATION_PATTERNS: "*:missing"`；fresh lineage构建脚本移除331豁免并再生成（326文件含V384/V385）。主链空库构建验证（compose同版mysql:8.4+collation参数）：V1～V373全部成功，V331缺位不再阻断；构建现止于V374的既有跨表collation冲突，见下方新登记Q-MIG-COLLATION-20260930-001，与本次迁出无关（V331无DDL，不参与该比较）。

### Q-MIG-COLLATION-20260930-001 — 主链全新空库构建在 V374 因跨表 collation 混用失败（迁出 V331 验证时发现）

- Status: OPEN（BLOCKED_BY_SPEC：统一到哪个 collation 与整链对齐策略需需求方/DBA裁决）
- Requirement IDs: 无单一代码归属；发现于 Q-MIG-V331-20260929-001 迁出实施的"主迁移链验证"
- Area: 数据库迁移 / 全新空库构建 / 字符集治理
- Question: 主迁移链在 compose 同版 mysql:8.4.10（--collation-server=utf8mb4_unicode_ci）全新空库构建时，V1～V373 全部成功，V374 p06r 统一交付数据迁移的 INSERT…SELECT 跨表 JOIN 报 1267 Illegal mix of collations (utf8mb4_unicode_ci,IMPLICIT) 和 (utf8mb4_0900_ai_ci,IMPLICIT)。根源是链内建表语句的 collation 声明不一致：`DEFAULT CHARSET=utf8mb4` 不带 COLLATE（如 V166 acc_* 族）在 MySQL 8 落 charset 默认 utf8mb4_0900_ai_ci，不带 charset（如 V361 plt_delivery_* 族）落库默认 unicode_ci，V92 plt_file_reference 显式 unicode_ci；V374 的 acc_*×plt_file_reference/plt_delivery_* 比较触发混用。
- Evidence:
  - 2026-09-30 临时容器复现：全链 flyway migrate 于 V374 失败；同机实测 `CREATE TABLE … DEFAULT CHARSET=utf8mb4`（无 COLLATE）→ utf8mb4_0900_ai_ci，与库默认 unicode_ci 无关。
  - compose 主库（npdms_domain_test）447 张有 collation 的表 0 张 0900_ai_ci（历史成因未深究），故既有库不受影响、V374 在主库曾成功；fresh 库从未执行过 V372～V374，其下次升级到该区间将同样失败。
  - V166/V374 均已在既有库执行（success=1），按"不重写历史迁移文件"约束不能就地修补；collation 对齐迁移若编号新于 V374，全新构建仍会在 V374 先行失败（前向修复无法解锁链中段）。
- Options: A. 以既有主库为基准统一 utf8mb4_unicode_ci：新增插入在 V373 与 V374 之间的编号（如 V373.5）COLLATE 对齐迁移，既有主库无需执行（已统一）、fresh 构建按序执行后 V374 可通过；B. 统一到 utf8mb4_0900_ai_ci（方向相反，需同步处理 V92 显式 unicode_ci 与库默认），影响面更大；C. 只改 V374 语句加 COLLATE——需重写已执行迁移文件，违反迁移不可变约束，不可行。
- Recommended technical default: A（与既有库实况一致，既有库零动作）。
- Blocking scope: 阻断"单一主链支持全新空库构建"最终达成与 fresh lineage 后续升级到 V372～V374 区间；不阻断既有库日常升级、不回退 Q-MIG-V331-20260929-001 的迁出结论。
- Decision owner: 需求方（涉及整链字符集治理基线）。

## 施工计划批次再审批与已完成任务历史链接的解析口径（2026-09-29 S2 工作区 OWNER_FACT_UNAVAILABLE）

### Q-TASKBIZ-20260929-001

- Status: RESOLVED（需求方 2026-09-30 裁决：A 维持现状为最终口径）
- Requirement IDs: PLN-01/04（阶段施工计划 3.1）；PM-01（任务工作区业务内嵌）
- Area: 施工计划批次生命周期 × 任务业务链接（proj_task_business_link 冻结 object_id）的解析口径
- Question: 项目进入 S3 后，经 3.1「阶段施工计划」页面再次创建并审批新批次（2026-09-29 项目 992004000014 批次 12 于 15:58 生效，S2 已于 10:41 完成）——该再审批路径当前无阶段门槛校验。由此：(a) S2 关闭后是否允许不经门槛重开即再审批新批次？(b) 允许时，已完成 S2 任务的冻结链接（批次 11）在新批次引入不同工期基线后，其完成事实（CONSTRUCTION_PLAN_APPROVED）按历史批次评估可能转 false，工作区应展示历史真相还是当前基线结论？
- Evidence:
  - `StagePlanApprovalCompletionProvider.load` 原实现仅解析 `mapper.current`（最新生效批次）并要求等于链接 id，被顶替后抛原始 IllegalArgumentException，被 `ProjectTaskBusinessService.getContext` 吞为 OWNER_FACT_UNAVAILABLE，S2 工作区执行区整体不可用（2026-09-29 浏览器实测 + API 复现：task 2104517608604786693 链接批次 11，current 返回批次 12）。
  - 同构 `SolutionApprovalCompletionProvider` 读路径按链接 id 直接解析，历史方案链接不因新增方案失效——StagePlan provider 原实现偏离该契约。
  - 既有测试 `supersededBatchCannotBeReused` 钉住锁路径语义：完成依据锁定仅接受当前生效批次，防止用被顶替批次完成门禁。
- Applied technical default（已落实，2026-09-29）: 读路径（inspect）改为按链接 id 解析（`StagePlanCompletionMapper.byId`，保持 deleted=0 且 status=2 的"审批批次"资格），被顶替批次可只读回放；锁路径（lockCompletionFact/lockStageCompletionFact/lockAndRevalidate）维持仅当前生效批次语义不变。测试 6/6 通过（新增 readOnlyInspectResolvesTheLinkedBatchEvenAfterSupersession）。
- Options: A. 维持现状（3.1 再审批不受阶段门槛限制，历史链接只读回放）；B. S2 完成后再审批新批次须重开 S2 门槛/走变更流程（需定义重开机制）；C. 再审批允许但完成后自动把进行中/已完成任务的链接迁移到新批次（违反链接不可变历史，不建议）。
- Recommended technical default: A 为当前已实现口径；若需求方认为 S2 后再审批应受控，则 B，需补充规格定义重开与变更流程。
- Blocking scope: 仅阻止 (a)(b) 的最终裁决；不阻断读路径显示修复（已落地）与其他独立工作。
- Decision owner: 需求方；PLN Owner、工程实施 Owner 参与影响分析
- Resolution: 需求方2026-09-30裁决采用A为最终口径：(a) 3.1再审批不受阶段门槛限制，S2关闭后允许不经门槛重开即再审批新批次；(b) 已完成任务冻结链接按链接id只读回放历史批次（展示历史真相），锁路径维持仅当前生效批次语义。2026-09-29已落地的读路径修复即为该口径实现，测试6/6通过，无需额外实现。Decision date: 2026-09-30。
## 全库命名统一中设备型号字段族的归属口径（2026-09-29 产品/物料双读法全库统一裁决外溢）

### Q-NAMING-20260929-001

- Status: RESOLVED（需求方 2026-09-30 裁决：A 保留现名）
- Requirement IDs: 无独立 Feature 编号；衍生自 2026-09-29 "全库所有同含义的字段，都用统一的命名" 裁决（V378/V379 产品/物料双读法统一）
- Area: 数据库字段命名规范 × 设备维度快照字段
- Question: 产品/物料双读法规范名已定为 `product_code`/`product_name`/`product_model`/`product_desc`（V378/V379 已统一订单行、交付范围、到货行、领料、外采）。设备维度记录上的型号快照列——`imp_eng_risk.device_model`（设备型号）、`kno_announcement_check.device_model`（设备型号）、`plt_authorization.device_model`（设备型号）、`plt_collection_template.device_model`（无注释）、`pms_equipment_retired.model`（设备型号）——是否视为与 `product_model` 同含义一并更名？
- Evidence:
  - 设备主档 `ast_device` 已用规范名（`product_code`/`product_name`/`product_model`/`product_desc`），即设备的产品型号在主档即 `product_model`。
  - 上述 5 列均挂在业务记录上且与同表其他设备维度字段成族（如 risk 的设备关联字段族），只更名 `device_model` 一列会破坏同表字段族命名一致性；全部更名则波及 4 个模块的 DO/VO/前端。
  - 裁决原文只定义了产品/物料双读法，未提及"设备"读法；`imp_eng_material_exchange_serial.item_code` 先例表明同表不同事实可保留差异命名。
- Options: A. 保留 `device_model`/`model`（设备维度字段族命名，与产品/物料身份区分）；B. 全部更名 `product_model`（与 `ast_device` 主档对齐，接受字段族内命名不一致）；C. 更名且同步重命名同表设备字段族（`device_*`→`product_*`，波及面最大）。
- Recommended technical default: A（V379 未动这些列，仅登记）；若裁决为 B/C，另出迁移版本执行。
- Blocking scope: 仅阻止这 5 列的最终命名；不阻断已完成的四表统一（V379）与相关代码链。
- Decision owner: 需求方
- Resolution: 需求方2026-09-30裁决采用A——`imp_eng_risk.device_model`、`kno_announcement_check.device_model`、`plt_authorization.device_model`、`plt_collection_template.device_model`、`pms_equipment_retired.model`保留现名，定性为设备维度字段族命名，与产品/物料身份字段（product_*）区分；不另出更名迁移。产品/物料双读法统一（V378/V379）范围不变。Decision date: 2026-09-30。

## 2026-10-06 本机项目来源创建与 Struts 只读历史档案

- **BLOCKED_BY_SPEC：跨来源同号执行单**。同步销售订单头只保存 executionNo，没有 CRM 来源系统关联键；多个 sourceSystem 出现相同 executionNo 时无法证明唯一关系。本次只在唯一匹配时带入，歧义时拒绝，不按更新时间任选。后续需源系统明确关联键/映射。证据：CreationSourceResolver、DppmsOrderSyncAdapter 与本机取证报告 docs/generated/2026-10-06-project-creation-migration/audit.md。
- **已确认：历史先迁只读档案；BLOCKED_BY_SPEC：激活新流程映射**。用户 2026-10-06 明确“先迁只读历史档案”。保留旧状态、人员、合同、产品与组关系；不将旧状态直接投影新阶段/Gate，不将组关系猜成父子树或项目组合。未来激活必须另行批准人员/组织/模板/状态与完成事实映射。本轮离线脚本与合成验证不依赖该未决映射。
- **已确认：创建只取订单/执行单基本信息**。用户明确“创建项目不需要订单行ID，只需要订单和执行单的基本信息”。创建使用合同下同步销售订单头，唯一时自动带入，多条时按订单号选择；执行单由该订单 executionNo 关联，不独立选择。旧行 ID 仅作为旧系统取证事实，不加入创建请求。

## 2026-10-06 真实项目试迁的客户身份口径

### Q-PROJECT-CUSTOMER-20261006-001

- Status: RESOLVED（2026-10-07 用户明确裁决）
- Scope: 从真实旧项目中选择十项具有完整合同、执行单、订单及发货关系的代表项目，迁入本机隔离测试库并保持待指派。最初十项只是候选，不是锁定名单。此前只读档案是一般历史迁移默认路径；本次十项另有运行项目试迁授权，不把旧生命周期、人员和完成事实映射为新阶段或 Gate。
- Resolution: 项目客户沿用最终客户含义，独立选择并校验 CUS 主档；合同与销售订单购货方另行保留。用户确认“沿用最终客户，并另外保留购货方关联”。
- Identity evidence: CRM 执行单真实主键关联 new_account_id，再关联 AccountId/CUS 外部身份映射。购货方码和名称相等均不能替代最终客户身份。最初候选未解析身份的结论仅适用于该次候选；后续重选已取得可靠身份与来源关系。
- Legacy evidence correction: 旧 projectcreate.jsp:309–310 的 column013 是可编辑文本，其非空检查位于注释块；不能称为旧系统强制必填。ProjectServiceImpl.putProperties:418–445 不以购货方覆盖最终客户。
- Remaining execution boundary: 只用现有授权身份及公司＋办事处联合范围。不得扩大持久角色或组织范围，不绕过服务层权限；不足十项时报告实际合格数与最小现成身份需求。真实名单、原始业务快照及凭据不进入 Git。
- Decision owner: 需求方。试迁授权已明确，不重复请求该授权。
- Evidence: docs/generated/2026-10-06-project-creation-migration/README.md。

## 2026-10-08 项目关联原始数据的业务实体承接裁决

本节记录当前项目详情续作中的明确用户裁决及尚需裁决的原表业务承接契约。用户已要求：涉及承接的每张原表须逐列核对并列明映射；字段不能丢弃，迁移快照、绑定和审计证据不能代替业务实体承接。未决问题只阻止依赖该契约的业务写入，不阻止已明确的独立承接工作，也不授权改变旧状态、人员或完成事实。字段矩阵与运行证据归属 `docs/generated/`；真实来源值、序列号、凭据和裁决清单留在本任务私有证据中。

当前已批准范围包括公司编码转换、本批 16 台设备归属、正式维护服务历史、现目录按租户＋产品编码补齐 5 个缺失产品并保留已有名称及来源旧名，以及额外第 2/3/4/8/9 域（项目合同关联、主子设备关系、基础数据、项目人员、项目状态与计划任务）和第 15 域加密许可历史。范围批准不等于已完成结构审查、业务写入或验收，也不自动批准先前拟新增的镜像表列；这些条目只保留具体既有实体/历史属性复用、Owner、精确关系和安全访问的实施审查，不再询问是否启动或是否正式承接。其他额外域仍按下列未批准条目逐项待裁决。

### Q-P2-001 — 2026-10-08 本任务维保过程原表 44 字段的明确授权例外

- Status: RESOLVED（本任务授权例外；2026-08-15 原决策及历史文字保留）
- Scope: 本次已批准十个项目对应的 `pm_project_maintenance` 原表全部 44 字段。
- Resolution: 用户本次明确“来源维保信息没有承接”以及“提到的内容必须有业务实体表进行承接，不得以任何理由进行丢弃，或者迁移中间快照表当做承接内容”。因此，本批维保过程记录须由正式业务实体承接原字段，并通过现有项目、租户和数据范围授权查询；旧排除结论不能阻止本次明确授权的范围。本例外不将历史维保状态或人员直接激活为当前流程，也不授权其他未裁决历史工单、工时、附件、审批域的产品语义。
- Implementation boundary: 正式维护服务历史建设已明确批准，不再询问是否建设或是否把维保当作当前 ITR。44 字段仍须逐项核对既有 Owner、历史实体与属性的复用边界；现有新表/新列及迁移回执不自动证明结构必要性或完整业务验收。
- Evidence: 本任务完整原表审计 `pm_project_maintenance` 44 字段与用户当前会话指令；原政策见本文件前部 Q-P2-001。
- Decision owner: 需求方。Decision date: 2026-10-08。

### Q-SYNC-COMPANY-20261008-001 — 公司编码的统一转换

- Status: RESOLVED（用户明确裁决）
- Resolution: DPPMS 来源公司编码统一采用 `01→001、02→002、03→003、04→004`。非空未知码阻断，不按项目合同猜测公司。订单与公司主档必填公司缺失时阻断；既有契约允许为空的合同引用事实继续保留 NULL，不据此生成公司范围主档。来源原码保留；既有同步任务只能通过正式配置服务前向更新，旧版本、运行配置快照和证据不可改写。
- Blocking scope: 不再存在转换规则歧义；发布后实际配置升级和业务验证是实施事项，不能仅凭代码通过声明完成。
- Decision owner: 需求方。Decision date: 2026-10-08。

### Q-PROJECT-DEVICE-OWNER-20261008-001 — 本批 16 台设备的现有项目归属

- Status: RESOLVED（用户明确裁决）
- Resolution: 用户明确“全部转入各自来源项目”，仅适用于本任务私有 `native-followup-private/ownership-arbitration-v4.json` 所列 16 台设备及其逐项来源项目。通过既有正式项目指派动作执行，不直接更新归属表，不扩展为其他设备或一般自动转移规则。
- Evidence: 执行及复核保留于本任务私有 `native-followup-private/ownership-execution-v4.json`；公开文档不记录真实 SN。
- Decision owner: 需求方。Decision date: 2026-10-08。

### Q-LEGACY-CONTRACT-20261008-001 — 原项目合同关联与附加合同头修订的业务实体

- Status: APPROVED_SCOPE / IMPLEMENTATION_REVIEW_REQUIRED
- Sources: `pm_project_contract` 7 字段；既有同步实际读取的 `sms_ofst_contract_head_sap` 39 字段。
- Question: 原项目合同关联的 id、项目组码、创建/更新人和时间应由哪个业务关系实体承接？附加合同头同合同多行、批次和生效区间应如何在正式业务实体中保留原行身份和修订关系？
- Evidence correction: 六个现存同步任务未读取 `pm_project_contract`。SMS39字段已有 `com_contract_receivable`正式映射契约；不得继续把主合同聚合投影之外25字段表述为没有既有业务目标。现表0行、正式导入及历史关系尚未实施；拟新增8来源别名列缺必要性依据。
- Existing resolved mapping: `fb_contract` 原 12 字段已有 `com_shipment_contract_reference` 承接，十项目 120 项原值逐字段核对一致；contract_id 保存在 source_record_key，warranty 映射 warranty_flag。该结论不能冒用为上述两张来源表已承接。
- Blocking scope: 仅上述未明确业务实体及原行修订关系；不阻断已有合同引用事实、订单和回款计划承接。
- Decision owner: 需求方；不得在裁决前新增合同状态、审批或业务 Owner。

- 2026-10-09 scope correction: 用户已选额外领域2/3/4/8/9，本域是否启动已裁决。复用既有实体/历史属性及具体关联继续核实；不再因未建设完整新镜像表把范围登记为未经批准。

### Q-LEGACY-PRODUCT-ID-20261008-001 — 现有产品目录的业务身份

- Status: RESOLVED（用户明确裁决，实施与验证未完成）
- Resolution: 原自增id没有业务意义；复用现产品目录，业务唯一键为产品编码，支持租户时追加tenant_id。本批104编码中5个缺失已批准补齐ACTIVE；99个当前目录名称保留，30个不同旧名称及型号由正式目录来源/修订承接，不能在发货/维保实体反复复制整套目录。
- Implementation boundary: 原ID全部NULL的事实仍列入原字段审计；不据此批准两个新发货物料ID业务列。目录补齐及历史名称关系需要正式目录Owner，不得以项目指派权限写全局目录。
- Decision owner: 需求方。Decision date: 2026-10-09。

### Q-LEGACY-DEVICE-RELATION-20261008-001 — 原主子设备关系行

- Status: APPROVED_SCOPE / IMPLEMENTATION_REVIEW_REQUIRED
- Sources: `fb_shipment_barcode_relation` 8 字段（id、sn1/item1、sn2/item2、contract、createtime/updatetime）。
- Question: 原关系行如何在正式设备关系业务实体中保留双侧来源身份、合同和原时间，如何处理来源仅有编码而未形成可靠设备关联的行？
- Blocking scope: 原关系行身份及关联规则；本批零条来源记录不能代替 schema 和承接契约。
- Decision owner: 需求方。

- 2026-10-09 scope correction: 用户已选额外领域2/3/4/8/9，本域是否启动已裁决。复用既有实体/历史属性及具体关联继续核实；不再因未建设完整新镜像表把范围登记为未经批准。

- 2026-10-09 reuse evidence: 既有 `ast_device_relationship`及device-sn同步模板已映射全部8原字段；本批0源行不能替代非空验收。新建整套source relationship表的方案撤回，仍需保证不自动激活当前设备关系。

- 2026-10-11 full-source verification: 原65406条、正式19列65046条。已承接六项原值及原创建时间→effective_from全部一致；差360条全部同SN且同产品，被既有模板sn1<>sn2过滤，排除原因尚未获用户裁决。正式create_time均非原时间，15706条原非空更新时间未落正式update_time，另49340条已承接原更新时间为NULL。先前“全部8字段映射”不代表正式历史审计完整承接。当前原型ast_device_source_relationship实际不存在，禁止以该原型作为既有正式业务表。完整只读审查：`docs/generated/2026-10-11-dppms-device-relationship-original-audit.md`；状态仍为WHOLE_TABLE_PLAN_PENDING，未调整结构或记录。

### Q-LEGACY-APPROVAL-20261008-001 — 历史审批意见

- Status: OPEN / BLOCKED_BY_SPEC
- Sources: `fnd_act_hi_comment` 全部 12 字段。
- Question: 旧流程、任务和业务对象键及评论内容，应由哪个正式历史审批实体承接，并采用什么精确对象关联和用户查询范围？
- Blocking scope: 原审批事实的业务实体和对象关联。不得新建当前审批通过事实、重放流程或将旧评论当作新平台完成条件。
- Decision owner: 需求方。

### Q-LEGACY-DICTIONARY-20261008-001 — 来源基础数据原行

- Status: APPROVED_SCOPE / IMPLEMENTATION_REVIEW_REQUIRED
- Sources: `fnd_basic_data` 完整原表字段；当前矩阵含 10 个未映射字段及既有字典查用/视图派生字段。
- Question: 除既有显示名称查用外，原基础数据记录、分组、状态和审计信息是否对应可验证的系统字典/主数据身份，全部原字段应落入哪个业务实体？
- Blocking scope: 原记录业务身份和转换契约。显示文本已查出不能替代原字段承接，不臆造新取值域。
- Decision owner: 需求方。

- 2026-10-09 scope correction: 用户已选额外领域2/3/4/8/9，本域是否启动已裁决。复用既有实体/历史属性及具体关联继续核实；不再因未建设完整新镜像表把范围登记为未经批准。

- 2026-10-11 raw-table verification: fnd_basic_data完整12列532条、80分组；fnd_basic_data_type完整10列31条，49个项分组无原类型定义。原类型status表示前台管理标记，不可直接映射新字典启停。109项附加属性、有效期及原NULL审计尚无获准承接规则；现system_dict_type/data实际11/14列、无tenant_id。完整审查`docs/generated/2026-10-11-dppms-basic-data-original-audit.md`，只读、无新增结构或字典值，WHOLE_TABLE_PLAN_PENDING。原字段未被默认排除，也未激活当前流程。

- 2026-10-12 type-table decision: 用户要求system_dict_type“保留既有结构，我指定缺项处理”。保持11列，三个候选新增列停止；原管理标记及两个有效期承接点或排除、49个缺类型定义分组及审计初始化等待明确。已集中询问，不自行排除、不通过备注代替。system_dict_data整表裁决独立等待。完整清单见`docs/generated/2026-10-12-dppms-system-dictionary-table-proposal.md`。

- 2026-10-12 type-table resolution: 用户明确原管理标记及两个有效期不迁入，批准49个缺定义分组用原编码作为type/name、实际迁入审计；31原定义保留真实名称、历史时间，NULL更新时间用原创建时间，NULL人员保持。正式迁移任务2109351154645135362执行2109351965311774721为SUCCESS/CREATED80，重放2109352251665297409为SUCCESS/UNCHANGED80。完整381条、11列结构、80绑定及其他受保护数据核对通过，真实字典管理页验证原01和补齐trackStatus。类型表已闭合；system_dict_data三个候选列、补值及ID114排除继续USER_DECISION_PENDING，未实施。

- 2026-10-12 type-table resolution: 用户批准原status/effectiveFrom/effectiveTo不迁入，逐列记录“现结构无同义字段，用户明确保持11列并批准不迁入”。49个缺定义分组按原编码作为type/name并使用实际迁入审计；31个原定义保持真实名称及历史时间，NULL更新用原创建，NULL人员保持。类型表WHOLE_TABLE_APPROVED，开始SYSTEM Owner实施；数据表三个候选列、排序审计补值及ID114处理仍BLOCKED_BY_SPEC，不据此自动批准。

### Q-LEGACY-CALLBACK-20261008-001 — 历史回访与问卷关联

- Status: OPEN / BLOCKED_BY_SPEC
- Sources: `pm_cl_callback` 13 字段；`pm_cl_callback_quesnaire` 12 字段。
- Question: 历史回访记录及其问卷关联由哪个正式业务实体承接，旧回访、客户、人员和答卷之间采用什么精确关联键？
- Blocking scope: 两张原表的业务实体及关联契约。历史证据必须保留，但快照不能作为业务承接完成依据。
- Decision owner: 需求方。

### Q-LEGACY-QUESTIONNAIRE-20261008-001 — 原问卷模板、题目、选项与答卷

- Status: OPEN / BLOCKED_BY_SPEC
- Sources: `pm_cl_quesnaire_result_header` 10、`pm_cl_quesnaire_result_line` 13、`pm_cl_quesnaire_template_header` 14、`pm_cl_quesnaire_template_line` 15、`pm_cl_quesnaire_template_options` 13 字段。
- Question: 旧模板版本、题目、选项和答卷如何作为正式业务历史对象保存，并保持答案准确引用原题和原选项？是否及如何与新问卷实体建立明确关系？
- Blocking scope: 原问卷模型和来源关联契约。不得直接生成新平台有效调查结果、重写批准模板或丢弃旧答案。
- Decision owner: 需求方。

### Q-LEGACY-PROJECT-GROUP-20261008-001 — 原项目组关系

- Status: OPEN / BLOCKED_BY_SPEC
- Sources: `pm_project_group_relationship` 9 字段。
- Question: 原项目组与项目的关系，应由哪个正式业务关系实体承接原身份、有效关系和审计字段？
- Blocking scope: 原项目组关系模型。项目父子树与项目组合是独立模型，不能按组名称或层级猜测转换。
- Decision owner: 需求方。

### Q-LEGACY-MEMBER-20261008-001 — 历史项目人员

- 2026-10-14 latest decision / USER_APPROVED_PRODUCTION_TASK_RULE_UPDATED: 用户明确“历史最终客户联系人作为项目成员进行迁入，历史项目无法准确归集到最终客户”。P32的原`pm_project_member.memberRole=60`由PROJ成员Owner承接到`proj_project_member_assignment`，按原项目身份关联；不再要求准确最终客户、`FINAL_CUSTOMER`相关方或CUS联系人主档，不向`cus_project_customer_contact_relation`迁入新批次。复用成员表既有姓名、人员编码、电话、邮箱、登记来源、有效期及审计字段，保留原角色60的历史含义；不把其猜配为经理、销售或普通账号成员角色。使用既有`authorization_confirmed=false`，无精确账号时`user_id`可空，不创建账号、系统角色、当前访问授权、主责或客户联系人完成事实。P31/P32同Owner、来源互斥、按项目串行；P17及P32不以最终客户资料完成为前提。当前成员迁入代码仍排除角色60、原联系人入口仍依赖最终客户相关方，代码及全量任务入口调整待完成。原库已迁9条CUS联系人、来源绑定、不可变回执和原整表裁决作为历史保留，本次只调整后续生产任务，不授权搬移、删除或实际迁入。见[生产任务P32](../superpowers/plans/2026-10-14-dppms-production-migration.md#p32-历史最终客户联系人作为项目成员)。

- 2026-10-19独立新套件复核：新`BASE_P31`错误沿用CUS联系人分支，已仅在该新任务改为角色10/20/30/60全部承接到`proj_project_member_assignment`；角色60保持原角色含义及`authorization_confirmed=false`，不要求最终客户相关方，不写CUS联系人，不改当前授权或主责。原任务、旧实现、9条历史CUS联系人和旧绑定均保留。26项隔离、Mapper及原生临时表检查通过；真实预览2112116680425676801已越过旧依赖，当前因152条原始区间倒置失败，不能记为全量通过。日期问题独立登记于Q-BASE-P31-REVERSED-HISTORY-20261019。

- 2026-10-12 final contacts executed / PILOT_LOCAL_VERIFIED: 获准V436通过指定Compose/Flyway应用，现项目联系人29列；9条最终客户联系人经迁移功能和CUS Owner迁入，9 NEW＋9 REPLAY_COMPLETED。原全表17联系人及客户主档/联系人操作历史不变，全表26条；姓名、电话、空邮箱、原区间和NULL更新人完整承接，未造客户主档、主联系人、账号、授权或完成事件。9项目真实页面、普通主联系人编辑打开取消和闭环普通维护禁用验收通过；验收后145成员、项目状态、其余保护业务及原70行15列再次保持。62项后端（含7项MySQL临时表）、4项前端和41模块打包通过。整表报告已记录实际证据，本批9条不再待裁决；全历史和生产基线尚未关闭。

- 2026-10-12 remaining primary decision: 用户批准其余7项目按原主责补建正式成员授权和主责引用。52564/53649/50370/52443/53546服务及主项目经理均z01010；53614服务z01010、主项目经理y06496、liujiazhen_supp协作；69300仅服务经理z01010，原项目经理未通过资格不授角色或项目经理授权。全部10项目主责方向已裁决，逐项目执行前须重新预览并保护来源/资格/项目版本/全部成员字段。9条最终客户人员仍待CUS整表规划，未授权新增表列或属性。
- 2026-10-12 member pilot executed: 10项目经同一正式迁移Owner承接61条原成员，23条确认授权、38条未确认历史；6条管理员主责区间结束并保留历史。原84条除获准6条结束字段外保持，全表145条；10项目重复执行全部REPLAY_COMPLETED。原70行15列、生命周期和其他业务逐值保护通过。69300仅服务经理授权，未给原项目经理补角色或授权。合并证明为任务私有member-ten-project-final-proof-20261012.json；页面验收另在成员整表报告追加。
- 2026-10-12 CUS final contacts BLOCKED_BY_SPEC: 已提交2026-10-12-dppms-final-customer-contact-table-decision.md，实际原9行与现CUS三表全部字段核实。现Owner按购货方客户创建主档，不能自动作为最终用户归属；候选复用24列项目联系人表并新增project_party_id/contact_code/registration_source/effective_from/effective_to五列，仅经核实最终客户相关方关系允许客户/主档引用为空，其余正式字段直接承接、原3联系事实保护。全部结构、条件约束、审计NULL处理待用户整表裁决，未实施、不伪造客户主档、不丢弃。
- 2026-10-12 CUS final contacts adjudicated: 用户回复“整表按建议”，批准上述29列整表和有条件引用、原区间、审计NULL处理、非主联系人只读及不代替普通完成事实。改为IMPLEMENTING，未声明已迁入。原待答状态已解决，不重复确认5个新列。

- 2026-10-11 primary decision: 用户批准51304/53487/51922按旧主责承接；服务经理均z01010，主项目经理分别z01010/l05678/y06496，其他合格项目经理为协作。执行时结束6条管理员当前区间并保留历史，原已结束TEAM区间不调整。专用迁移Owner须保护来源、资格、项目版本与成员全部34列；无当前主责的其余项目正式主责引用仍等待同调用第1项答复，不据资格核验自行写入。

- 2026-10-11 whole-table decision: 用户确认proj_project_member_assignment既有29列＋5列整表方案，并允许专用迁移Owner对已闭环项目按同一账号/角色/适用公司规则核验后補建授权。普通ACTIVE入口不变；当前主责及成员冲突仍逐项裁决。9条最终客户联系人另按CUS整表审查。结构审批已解决，尚待真实写入、回滚/授权保护验证及冲突裁决。

- Status: APPROVED_SCOPE / IMPLEMENTATION_REVIEW_REQUIRED
- Sources: `pm_project_member` 15 字段；完整清单同时记录 `pm_project_related_party` 创建/更新人时间 4 字段的原生补承接进度。
- Question: 原项目人员的角色文本、联系方式、来源用户身份与历史任期如何由正式业务实体承接？2026-10-11已批准有效成员经正式Owner核验承接项目授权、过期区间保留历史，当前成员或主责冲突另交裁决；不得凭来源角色授予系统角色。
- Blocking scope: 原人员实体及历史关系的具体复用与实施契约，启动范围及有效成员授权方向已经批准；结构仍待整表裁决。`pm_project_related_party` 的 4 个来源审计字段同属已批准的完整字段审查范围，不因本问题排除；其存放位置须核对既有字段及正式历史属性，不能据范围批准认定新增 4 列方案已通过结构审查。过期或未通过Owner资格核验的人员不因此获得当前角色和项目权限。
- Decision owner: 需求方。

- 2026-10-09 scope correction: 用户已选额外领域2/3/4/8/9，本域是否启动已裁决。复用既有实体/历史属性及具体关联继续核实；不再因未建设完整新镜像表把范围登记为未经批准。

### Q-LEGACY-STATE-TASK-20261008-001 — 原项目状态与计划任务

- 2026-10-11 raw-table correction: pm_project_state实际9列、projectId唯一键，保存四类最新状态及更新时间，不是历史转换流水；pm_project_task实际28列129626条、本批37条，不能只按26个返回字段采集。本批原status均0但32条有实际完成日期；全源parentId等四列全NULL，不造WBS。现proj_project_task42列、执行记录25列、状态转换定义17列已完整核实；转换定义不是事件历史，废弃任务表不得承接新迁入。完整审查`docs/generated/2026-10-11-dppms-project-state-task-original-audit.md`；WHOLE_TABLE_PLAN_PENDING、未改结构或任务/生命周期事实。
- 2026-10-12 owner/source verification: 本批37条全部28字段与先前材料一致；旧editProjectPlan按项目结束旧有效区间，再逐事件/合同新增计划。款项日期、工程日期及实际完成日期独立保存。现有sol_stage_plan_batch23列、sol_stage_plan_item17列、sol_construction_plan13列全部核实，现有阶段DATE起止不能同义替代原合同事件DATETIME。原实施90真实含义为“维护阶段”，四类状态有业务与后置配置来源，不能一概认定可重算或映射当前生命周期。本次无结构/业务写入，承接方案仍待逐表裁决。
- 2026-10-12 existing-field verification: 51304、52443、53649原customInfo的实施状态、时间及比较标记已经随获准完整JSON进入proj_project.custom_info，逐值一致；但53649原pm_project_state实施时间比JSON早1秒，不能据已有JSON宣称所有原9字段已承接。其余旧状态属性、缓存时间及任务缺项仍待整表规划，不新增列或直接写生命周期。

- Status: APPROVED_SCOPE / IMPLEMENTATION_REVIEW_REQUIRED
- Sources: `pm_project_state` 9 字段；`pm_project_task` 28 字段。
- Question: 旧项目状态历程、计划任务及其人员、完成文本和审计时间，分别由哪个正式历史业务实体承接，任务的原父子/依赖关系如何表达？
- Blocking scope: 两域业务实体及来源关系。旧状态不得直接写当前生命周期或绕过状态机，旧任务完成不得直接满足新 Gate。
- Decision owner: 需求方。

- 2026-10-09 scope correction: 用户已选额外领域2/3/4/8/9，本域是否启动已裁决。复用既有实体/历史属性及具体关联继续核实；不再因未建设完整新镜像表把范围登记为未经批准。

### Q-LEGACY-SUPERVISION-20261008-001 — 历史监理

- Status: OPEN / BLOCKED_BY_SPEC
- Sources: `pm_project_supervision` 17 字段。
- Question: 历史监理的业务记录、人员、意见与状态，应由哪个正式业务实体承接，如何与来源项目及旧对象关联？
- Blocking scope: 历史监理业务实体契约，不据此新增当前审批节点。
- Decision owner: 需求方。

### Q-LEGACY-WEEKLY-20261008-001 — 历史周报内容与反馈

- Status: OPEN / BLOCKED_BY_SPEC
- Sources: `pm_project_weekly` 14、`pm_project_weekly_content` 9、`pm_project_weekly_feedback` 5 字段。
- Question: 周报头、内容和反馈应由哪个正式业务模型承接，原周期、父子关联、反馈人和原时间如何保留？
- Blocking scope: 三表业务实体及精确关系；本批零记录不授权丢弃字段。
- Decision owner: 需求方。

### Q-LEGACY-INSTRUCTION-NOTIFICATION-20261008-001 — 历史指导与通知

- Status: OPEN / BLOCKED_BY_SPEC
- Sources: `pm_project_instruction` 11 字段；`pm_project_notification` 6 字段。
- Question: 指导/反馈与通知记录分别落入哪个正式历史业务实体，旧通知的对象、内容、人员和送达标记如何保留？
- Blocking scope: 两域业务记录模型。保存旧通知事实不授权重新向人员发送，也不把送达当作业务完成。
- Decision owner: 需求方。

### Q-LEGACY-ITR-20261008-001 — 历史关联工单

- Status: OPEN / BLOCKED_BY_SPEC
- Sources: `pm_project_incident_table_from_itr` 37 字段。
- Question: 旧 ITR 关联工单的身份、类型、状态、处理信息及源项目关系，应由哪个正式业务实体承接，并保持什么历史只读访问范围？
- Blocking scope: 本域实体与来源关系；不是 Q-P2-001 中本次维保过程 44 字段例外的自动扩展，不激活新工单流程。
- Decision owner: 需求方。

### Q-LEGACY-LICENSE-20261008-001 — 历史许可记录与敏感字段

- Status: APPROVED_SCOPE / IMPLEMENTATION_REVIEW_REQUIRED（加密正式历史承接已批准；实施与验证未完成）
- Sources: `pm_project_license_info_from_license` 8 字段。
- Original question（保留历史）: 旧许可身份、许可范围和时间由哪个正式业务实体承接？如原字段属于 Secret，其受控引用、加密保存和访问契约如何确定？
- Resolution: 用户已批准额外第 15 域以加密方式由正式许可历史业务实体承接；不再询问是否建设、是否保留或是否采用加密。该授权不能解释为普通迁移快照可读取许可明文，也不自动批准未审查的实体、列或密钥设施方案。
- Implementation review boundary: 核对既有许可实体/历史属性的可复用字段及精确项目关系，落实业务 Owner、密文与密钥分离、受控读取和项目/租户授权契约。8 原字段仍须全部列明受控承接方式；不得暴露或持久化明文 Secret，不以脱敏快照替代正式承接。本条不宣称源码部署、密钥设施落实、真实迁入或验收已经完成。
- Evidence: 本任务当前会话的第 15 域明确裁决；已批准范围汇总见 [V4纠正审查](../generated/2026-10-09-dppms-v4-carry-correction.md)。
- Decision owner: 需求方。Decision date: 2026-10-09。

### Q-LEGACY-SUBCONTRACT-20261008-001 — 历史分包、报价与付款

- Status: OPEN / BLOCKED_BY_SPEC
- Sources: `pm_subcontract_project_header` 28、`pm_subcontract_project_price` 13、`pm_subcontract_project_payment` 13 字段。
- Question: 分包头、报价及付款分别由哪个正式业务实体承接，合同/供应商/人员身份、付款批次和原时间如何关联？旧付款事实与当前付款审批、余额的边界是什么？
- Blocking scope: 三表业务模型和原关联契约。不把旧付款文本激活为当前已审批或已支付状态，不以历史快照代替承接。
- Decision owner: 需求方。


## 2026-10-09 V4承接复核及结构纠正

### Q-LEGACY-V4-STRUCTURE-20261009-001 — 复用既有业务字段及历史属性边界

- Status: IMPLEMENTATION_REVIEW_REQUIRED；已批准业务范围保持，不重新询问公司、归属、目录、维护建设、加密许可历史及额外2/3/4/8/9域是否启动。
- Evidence: [V4纠正审查](../generated/2026-10-09-dppms-v4-carry-correction.md)、[799原字段清单](../generated/2026-10-09-dppms-v4-source-field-correction.json)、[114新增列及14新表](../generated/2026-10-09-dppms-v4-physical-change-review.json)。列表完整不等于全部字段语义和Owner审核完成。
- Resolution: 已确认V417四列重复。用户批准撤回依赖并经全表/数据库依赖核查后前向清理；V418已成功，仅清理该四列，原成功Flyway历史不变。其他有数据列/表先保留，不能用快照或已映射回执作为业务承接。
- Implementation corrections: 同10订单日期和2订单行的32项日期损坏已通过既有COM Owner补正；19原出厂记录中缺失的7条已进入既有正式实体；19包件已通过10个正式COM Owner命令补正承运商名称，原编码和来源名称保持不变。8条安装历史候选现已通过既有IMP Owner实际补齐，全部11补正命令重放无额外写入，原来源事实、安装行及当前设备事实保持。先前147字符专项技术键被平台128上限拒绝、Owner未执行且新增0的失败证据保留；修正键107字符，保留完整来源/快照/排序原键身份，不放宽平台限制或改结构。新正式页面消费者真实验收已通过，19承运商及8安装与Owner和业务表一致，原当前设备/安装保持。项目四属性已核正常旧写链为名称，现正式名称字段40/40同值，无补写缺口，重复来源列尚未授权清理。日期业务值与源/旧正确载荷一致，不需要再次业务裁决。
- Blocking scope: 真正不同的历史属性及关联需具体模型核实；确定同义字段、已确证错误和必要的并发保护继续修复。禁止扩大当前流程、权限或生命周期。
- Decision owner: 需求方。

### Q-LEGACY-SOFTWARE-ATTRIBUTES-20261009-001 — 既有软件事件的八个真实缺失属性

- Status: OPEN / BLOCKED_BY_SPEC（具体结构选择已向用户询问）
- Sources: 四个备份版本conpBak/cpldBak/bootBak/pcbBak；四个变化标志conpChange/cpldChange/bootChange/pcbChange。原659行中635已有正式事件、24尚未对应，6变更批次有独立事实且原引用无孤儿。
- Question: 复用既有事件，在系统正式业务扩展表保存8项、注册AST Owner/权限/读取接口；或先逐项审查8个正式业务列定义。不得先建完整28列镜像，不直接调用当前版本升级流程。
- Blocking scope: 八属性及相应批次/历史关系的结构实施。现事件和当前版本继续保护；未核24记录不被静默丢弃。
- Decision owner: 需求方。

### Q-LEGACY-TRACKING-REASON-20261009-001 — 旧不跟踪原因与当前项目属性含义

- Status: OPEN / BLOCKED_BY_SPEC（已提交具体裁决，其他独立纠正继续）。
- Evidence: 本批P06原pm_project_header.column008非空，与projectState=20/isback=40配套；旧ProjectServiceImpl在不跟踪动作写原因并同时写20/40。当前项目ACTIVE/S0；ProjectRules明确冻结当前notTrackReason；普通update DTO不提供此字段。
- Correction: 原“空字段可补齐”诊断撤回。客户项目名称另已核4有效值全一致、1空串按CRM规则归NULL，无补写缺口。
- Question: 推荐在已批准正式项目状态历史范围内关联原原因承接；或由用户明确授权当前not_track_reason存旧原因、仍保持现状态。不能依据无状态触发器就推断业务含义相同。
- Blocking scope: 仅该历史原因的具体业务承接位置及当前字段特许；不重新询问额外第9域是否启动。
- Decision owner: 需求方。

### Q-LEGACY-SOFTWARE-RECORD-NATURE-20261009-001 — 软件清单登记与升级执行事实

- Status: OPEN / BLOCKED_BY_SPEC（具体来源业务性质已提交裁决）。
- Evidence: 659×28来源实库与冻结数据一致。635现事件的来源身份及按既有空串归NULL、DATE至午夜转换的核心值相同，但其中600四标志全0、584版本全空。缺24分为22版本全空、1旧批次人工变更登记、1最新批次沿用；24观测实体原28字段已匹配，不等于正式业务闭环。
- Original behavior: 旧表单发送整套设备清单，头部日期可赋给所有行；实体注释明确无更新时executeTime无实际意义，flag由输入/备份差异产生，无独立设备执行确认。Bak可为旧备份->旧当前沿用链。
- Question: 推荐全部作为正式软件版本登记历史承接，复用既有历史及设备/批次关系，保留空值/链/原标志，不生成当前版本或升级完成事实；或由用户逐条裁定已执行记录，其余完整保留登记历史。
- Blocking scope: 登记性质与正式版本事件/当前版本的Owner映射；不得伪传current=false强造事件，现apply路径会激活当前版本而不能调用。旧635历史不删除不覆盖。
- Relation: 八属性具体存放方式仍由Q-LEGACY-SOFTWARE-ATTRIBUTES-20261009-001裁决，该结构选择不证明全部28字段或升级事实成立。
- Decision owner: 需求方。

### Q-LEGACY-PROJECT-DATES-20261009-001 — 原项目日期的事件含义与现生命周期

- Status: OPEN / BLOCKED_BY_SPEC（具体事件语义已询问；已批准额外第9域不重复确认）。
- Sources: `pm_project_header.projectStartTime/projectRefreshTime/projectCloseTime`；本批原值分别9/9/7非空，现正式对应日期10项目均NULL。
- Evidence: 旧Project.java注释将启动时间关联项目经理派工，原项目关闭不证明新平台当前ACTIVE/S0项目关闭；原刷新时钟也不能默认当本系统当前编辑时钟。新增source日期列及原值保存不等于当前日期正确。
- Question: 建议三类日期连同原状态、原人员及原事件含义由正式项目历史业务实体承接，当前生命周期保持原事实；或需求方逐项提供现业务字段裁决。具体复用/新增字段及Owner先核实后交用户审核，再实施结构，不自行新增三列或绕过冻结/状态机。
- Blocking scope: 三原项目日期的业务归属与当前事件解释；32COM日期损坏纠正已实际完成，不依赖本问题。
- Decision owner: 需求方。

### Q-LEGACY-V4-FOUR-NAME-CLEANUP-20261009-001 — 四个重复来源名称列的受控清理

- Status: SUPERSEDED_BY_INDIVIDUAL_DECISIONS（原四列合并询问未形成批准；2026-10-09 用户要求所有新增列和表逐项裁决，改由 C001–C004 分别记录）。
- Sources: proj_project.source_market_name/source_system_name/source_expend_name/source_industry_name；正式复用market_name/system_name/expend_name/industry_name。
- Evidence: 全136项目含tenant0并未过滤deleted，每列10非NULL和126NULL；40原文与canonical精确相同，无normalized-only差异、无canonicalNULL缺口、数据库依赖0。六个消费者及已有创建冻结边界已列。具体方案为`private/v4-project-four-source-name-cleanup-plan-20261008T235511034657Z.json`。
- Question: 是否批准撤回重复读写，以明确DPPMS绑定与原事实相等保护维持API读取兼容及其余126项目来源NULL语义，再在执行前重新核全表零差异/依赖后应用独立前向清理？这四个有数据列不在已批准V417四列范围。
- Blocking scope: 仅这四列的代码撤回及前向DROP；未经批准不改消费者、不迁出或删数据，不从现在名称伪造其他项目或跨期历史名称。旧来源、快照、回执和证据保留。既有32日期、7出厂、19承运商、8安装补正及页面验收不依赖本批准。
- Decision owner: 需求方。

### Q-LEGACY-INDIVIDUAL-STRUCTURES-20261009-001 — 全部新增列及表由需求方逐项裁决

- Status: USER_RULE_CONFIRMED / INDIVIDUAL_ITEMS_PENDING。
- User rule: “所有新增列和新增表由我逐个裁决”。
- Evidence: [结构逐项裁决清单](../generated/2026-10-09-dppms-individual-structure-decisions.md)及其[机器清单](../generated/2026-10-09-dppms-individual-structure-decisions.json)。V4 本批 114 个既有表新增列、14 个新表模型、373 个新表内列，共 501 个独立编号；4 项已有具体清理建议，其余 497 项尚需逐项语义/既有实体复核，不因已登记或落库视为批准。
- Resolution: 已批准业务范围继续有效；单项结构批准必须明确记录对象和处理方式，表批准不能代替列批准，合并询问和未回答不能产生授权。已有 V417 四列清理按先前明确批准完成，保留历史，不要求重批。后续新增结构同样先登记并逐项提交，不默认执行。
- First table batch: G001，proj_project全部37新增列[一并审查](../generated/2026-10-09-dppms-proj-project-table-decision.md)。原C001单列询问撤回；C001–C004有具体受控清理建议，其余33列全部列明差异和待核事项，未因此批准保留新增结构、清理或另建历史表。G001 已收到用户裁决，详见整表清单；身份迁移先行，代码、最终 SQL 基线及真实承接仍待落实。
- Blocking scope: 未取得单项裁决的结构新增/改造/清理保持待处理，现存数据和证据保留。只继续不依赖这些结构决定的独立工作；不猜测业务语义、不把中间快照充作业务承接。
- Decision owner: 需求方。

### Q-LEGACY-TABLE-BATCH-20261009-001 — 一个表的字段一起确认

- Status: USER_RULE_CONFIRMED / FIRST_TABLE_DECIDED_IMPLEMENTATION_PENDING。
- User clarification: “一个表的字段一起给我确认”。
- Resolution: 将此前单列逐次询问改为按表提交；同一表全部新增字段及既有承接点、差异、建议、未明项一次呈现，各字段的用户结论及例外仍单独记录。C001单列未答询问撤回，不产生批准。
- First table: [G001 proj_project 37列](../generated/2026-10-09-dppms-proj-project-table-decision.md)，四列有具体清理建议，其余33项待补齐各自审查；既有数据保留，不默认保留新列合理，不丢弃来源字段。未来新表同样将模型和所有字段放在同一份材料，不零散询问或推定批准。
- Blocking scope: 仅未明确裁决的结构处理；不影响已明确批准的业务范围和不依赖该决定的独立审查。
- Decision owner: 需求方。


### Q-LEGACY-IDENTITY-SYNC-20261009-001 — 用户角色先行及全量可复用迁移

- Status: USER_EXECUTION_RULE_CONFIRMED / TARGET_STRUCTURES_PENDING_REVIEW。
- User decision: 先完成 fnd_user_info、fnd_user_power、fnd_roles 迁移，再关联项目创建/更新人；全部过程使用迁移和同步功能，供全量历史复用。
- Authority: ADR-0002 与 platform-identity-access-migration.md；外部账号无伪造工号，服务范围不变成区域项目访问权，旧密码不迁入。
- Evidence: 2026-10-09 原库只读确认 880 用户、880 范围、18 角色，39 原字段；当前 system_users 缺账号类型/工号，外部服务范围实体尚不存在。
- Blocking scope: 未裁决新增结构不得实施；既有迁移链路复用、原字段审查、G001 已明确批准的独立纠正继续。
- Decision owner: 需求方；按目标业务表整表提交字段，不零散询问。


### Q-LEGACY-IDENTITY-COMBINED-20261009-002 — 七表联合规划及既有范围表复用

- Status: USER_OWNER_CHOICES_CONFIRMED / FIELD_AND_CONFLICT_DECISIONS_PENDING。
- User decisions: system_users结合t_role/t_user/t_user_info/t_user_role重新规划；角色以t_role核对；可服务范围使用system_user_company_department_scope，不新增pms_user_service_scope。
- Supersedes: 前一版仅fnd三表的8列用户、3列角色及18列服务范围新表提案；均未批准或实施。
- Evidence: 联合审查96原字段，旧创建链、公司角色与Shiro授权、EHR离职同步链已核。
- Proposed table correction: scope_type区分AUTHORIZATION与SERVICE_COVERAGE；服务覆盖公司的三列和scope_role可空，原授权查询显式排除服务覆盖；整表待裁决，不因选用已有表默认批准加列/改约束。
- Pending: 账号合并优先级和6条启停冲突、角色逐项映射/priority转换、人员资料与项目类型等缺口的正式Owner与字段。
- Scope: 全部执行经既有迁移与同步及SYSTEM Owner；当前没有IAM结构、账号或权限写入。

### Q-LEGACY-IDENTITY-RANGE-20261009-003 — 范围授权统一及跨公司集合

- Status: USER_RANGE_MEANING_CONFIRMED / COMPANY_SET_AND_EXTERNAL_PROJECT_ACCESS_PENDING。
- User decisions: “可服务范围”和“授权范围”是一个含义；fnd独有账号选择跨公司授权，具体公司范围由用户填写。
- Supersedes: Q-LEGACY-IDENTITY-COMBINED-20261009-002 的 scope_type 与四列可空提案撤回，未实施；复用 system_user_company_department_scope。
- Pending: 跨公司具体编码集合；外部账号是否能查看授权范围内未明确转派的项目。选择跨公司不等于授权所有公司，也不自动推导区域项目访问。
- Blocking scope: 依赖上述两项的权限写入与查询语义修改；不影响原字段联合审查。现有范围、人员、角色、历史证据保持，未执行IAM写入。
- Decision owner: 需求方；等待明确回复，未答不产生默认授权。

### Q-LEGACY-IDENTITY-RANGE-20261009-003 后续裁决

- Resolution: 用户填写fnd独有账号跨公司编码001、002，未授权其他公司；选择内部用户访问方式1、外部用户访问方式2，即内部可查看授权范围内项目，外部仍仅查看明确转派项目。scope_type提案维持撤回。
- Boundary: 访问语义与公司集合已明确；账号合并、分类、角色关联及全部待批准字段仍待核实，未执行IAM写入。

### Q-LEGACY-IDENTITY-ACCOUNT-CONFLICT-20261009-004 — 同人资料优先级及来源关联冲突

- Status: BLOCKED_BY_SPEC / WAITING_USER。
- Evidence: 当前原库显式非凭据列只读核对：19组t/fnd同登录名，6组启停不同；另两组fnd重复名均停用（c00733来源233/408、w02459来源118/119）；范围470外键473指向pms_tbpublic，冗余登录名tbpublic。旧密码值未读取。
- Questions: 核实同一人的账号是否t_user/t_user_info优先、fnd补缺；重复账号是否合并并保留全部来源与范围；范围用户名矛盾是否以明确外键为准。admin/sse名称与身份差异另列，不从普通同人规则推导合并或平台管理员权限。
- Blocking scope: 依赖上述冲突的账号与范围写入；字段审查及其他已明确独立工作继续。未答不产生默认处理。
- Decision owner: 需求方。

### Q-LEGACY-IDENTITY-ACCOUNT-CONFLICT-20261009-004 部分裁决

- User decision: 经核实同一人的账号以fnd为主、t_*补缺；六个启停冲突按fnd状态。该规则不授权覆盖既有正式账号、不自动合并admin/sse的身份差异。
- Pending: 两组重复fnd账号主记录及合并；范围470外键/冗余登录名冲突仍待回复。

### Q-LEGACY-IDENTITY-ACCOUNT-CONFLICT-20261009-004 重复账号裁决

- User decision: c00733（233、408）及w02459（118、119）逐组合并为停用账号，主资料取较新408及119；保留两个来源绑定和各自正式范围关系。
- Pending: 范围470关联冲突；其他身份及结构裁决不由本项合并授权推导。

### Q-LEGACY-IDENTITY-ACCOUNT-CONFLICT-20261009-004 范围关联裁决

- User decision: 范围470以fndUserId=473外键为准，关联pms_tbpublic；冗余tbpublic仅列明差异并保留来源证据，不改绑另一用户。三项账号冲突询问均已答复，admin/sse身份及其余字段另待裁决。

### Q-LEGACY-IDENTITY-ROLE-TABLE-20261009-005 — system_role整表字段与转换

- Status: BLOCKED_BY_SPEC / WAITING_WHOLE_TABLE_ADJUDICATION。
- Evidence: [完整整表审查](../generated/2026-10-09-dppms-system-role-table-decision.md)；结合t_role/fnd_roles全部22原列、现有15正式列，原库14/18条角色。现有角色创建默认data_scope=ALL，不能直接套用为历史角色授权。
- Proposal: 复用15既有列；priority→sort=100-priority；新增default_page、effective_from、effective_to三列；新增迁入角色CUSTOM=2且初始SELF=5，不自动授予菜单按钮，项目范围使用既有用户公司部门关系；现有角色权限保持。全部待整表回复，未实施。
- Separate pending: 具体角色语义关联、合并后的默认入口/有效期/审计差异、原角色15异常编码名称；本项字段批准不代替这些裁决。
- Blocking scope: 未批准字段及相应转换、写入；可继续只读审查，不添加DDL或业务数据。
- Decision owner: 需求方。

### Q-LEGACY-IDENTITY-ROLE-TABLE-20261009-005 结构裁决

- User decision: “保留既有结构，我指定缺失字段处理”。default_page/effective_from/effective_to三列新增提案撤回，未实施。
- Pending: 原默认首页和有效期的正式承接表字段，或需求方明确排除理由；未指定不丢弃、不以备注/快照代替。排序与角色权限转换及具体身份映射尚未明确批准，本次仅确定不新增结构。
- Blocking scope: 依赖缺失字段处理的角色迁移写入；原字段核对继续。等待用户答复，不默认处理。

### Q-LEGACY-IDENTITY-ROLE-TABLE-20261009-005 缺失字段裁决

- User decision: 默认首页、有效期不迁入。排除理由记录为用户明确裁决不迁入；无替代新列或备注承接，原来源证据保留。
- Pending: [32项来源角色完整映射与转换](../generated/2026-10-09-dppms-role-identity-mapping-decision.md)，新角色行及既有角色关联不从同名自动推导权限。该提案没有结构变更，仍待需求方裁决。

### Q-LEGACY-IDENTITY-ROLE-MAPPING-20261009-006 — 32项角色映射裁决

- Status: USER_APPROVED_WITH_EXCEPTIONS / IMPLEMENTATION_PENDING。
- User decision: 批准完整清单与转换，但t_role1超级管理员关联平台super_admin并授予平台超级管理员权限；停用角色不迁入。
- Result: 接收31项来源身份，对应28正式角色（复用3、新增25条记录）；fnd_roles15停用角色及对应关系按用户裁决排除。没有结构新增，不把元数据角色误等同平台管理员；本项平台管理员授权来自用户明确例外。
- Boundary: 当前尚未执行角色写入；用户身份和账号字段仍需整表确认，创建/更新人等依赖真实账号迁移。

### Q-LEGACY-IDENTITY-ROLE-CODE-20261009-007 — 源码业务编码替代按ID编造编码

- Status: USER_RULE_CONFIRMED / SOURCE_CODES_VERIFIED。
- User correction: fnd_roles代码中都有对应编码，不得用ID随意编码。
- Evidence: MessageUtil.java:201–269定义全部18个ROLE_*业务常量及其原表ID关联；32项映射清单已逐项加入源码编码和行号。
- Resolution: 独立fnd角色使用实际源码常量编码；已批准的t_role同义合并和平台正式角色关联保持明确映射。DPPMS_FND_ROLE_<ID>提案撤回、未执行；ID只用于外键和来源绑定，不作为业务编码。
- Exception: 源码ROLE_SALESPEOPLE对应15，但原表名称不同且停用，按用户停用不迁入裁决排除；不据此虚构销售或测试角色。

### Q-LEGACY-IDENTITY-ROLE-CODE-20261009-007 第二次纠正

- Status: BLOCKED_BY_SPEC / ACTUAL_STRING_VALUES_PENDING。
- User clarification: 不要用代码字段命名作为角色编码，存在对应字符串值。MessageUtil的整数常量名不是字符串编码，上一条其作为编码的结论撤回，未执行写入。
- Evidence: core/param/RoleConstant和PMS-springmvc/constant/RoleConstant有admin、user、projectAdmin、projectManager等真实字符串；仍需逐项证明与fnd角色关联，不能从相似变量名拼造其他值。
- Question: 请求用户指明字符串定义文件或一个对应示例；独立源码与字段核对继续。
- Blocking scope: 未证实字符串值的角色编码和相应关联写入；已明确语义/授权/停用排除裁决保留。

### Q-LEGACY-IDENTITY-ROLE-CODE-20261009-007 业务编码转换裁决

- Status: USER_CONVERSION_RULE_CONFIRMED / IMPLEMENTATION_PENDING。
- User decision: “按常量名转换为对应的编码，不要直接使用常量名”。不再要求全部转换结果是旧源码字符串字面量；采用与t_role既有编码一致的小驼峰业务码，明确记录整数符号与转换值。
- Resolution: 独立fnd角色如ROLE_PROJECT_VIEWER→projectViewer、ROLE_COMPONENT_ADMIN→componentAdmin；已批准平台角色/t_role关联保持原映射。完整18项转换与32项身份已同步；不以源码字段名直接填正式编码。
- Boundary: 超级管理员授权、停用排除、其他语义和权限裁决继续有效；尚无角色写入。

### Q-LEGACY-IDENTITY-USERS-TABLE-20261009-008 — 用户只新增工号

- Status: EMPLOYEE_NO_COLUMN_APPROVED / OTHER_SOURCE_HANDLING_PENDING。
- User decision: system_users只增加工号；employee_no VARCHAR(50) NULL，承接真实内部工号，不填外部人工用户名。其余七列新增提案撤回，没有结构实施。
- Pending: 英文名/座机/邮件偏好/customInfo/账号有效期的正式承接或排除；旧认证/首页/无值字段排除、原状态2映射停用等待整表回复，不默认从不同意加列推导允许丢弃来源数据。

### Q-LEGACY-IDENTITY-PROJECT-AUTHORIZATION-20261009-009 — 内外用户统一按项目授予

- Status: USER_RULE_CONFIRMED / IMPLEMENTATION_PENDING。
- User latest decision: “内部外部，可以授权范围不赋予对应的办事处权限，只按项目授予”。
- Supersedes: 此前内部按区域查看全部项目、外部按转派查看的区分撤回；不新增account_type权限区分列。
- Rule: 范围关系可用于人员资格及公司/部门候选约束；内外用户项目查看和操作均来自明确项目授权，空集合不能回退扩大成全部公司/办事处项目。功能/字段/租户约束继续生效；平台超级管理员角色沿既有明确裁决。
- Boundary: 仅确认规则，尚未执行账号、角色、范围和项目权限写入。

### Q-LEGACY-IDENTITY-USERS-TABLE-20261009-008 两列及账号属性最终裁决

- Status: TWO_COLUMNS_AND_ACCOUNT_ATTRIBUTES_APPROVED / IMPLEMENTATION_IN_PROGRESS。
- User decision: 新增employee_no、custom_info；英文名、座机、邮件偏好、账号有效期、旧首页与旧认证历史按列明业务属性存custom_info；完整保留原customInfo对象，旧密码不读取、不存JSON。
- Exclusions: 用户明确批准全空生日及列明无业务值预留字段不迁入，逐字段列明原值核验与裁决原因；全历史扩展不能直接套用本批无值结论。
- Status mapping: t独有原2锁定→正式停用，原锁定值同时存custom_info.legacyUserStatus；同人仍以fnd为主、t补缺。
- Boundary: V419仅包含两个获准字段，候选查询使用工号；尚未执行DDL、账号或权限数据写入。人员任职、多公司资料、无账号资料和项目类型准入的Owner问题仍独立待裁决。

### Q-LEGACY-IDENTITY-ROLE-AUDIT-20261009-010 — 原角色更新时间NULL

- Status: USER_RULE_CONFIRMED / CODE_IMPLEMENTED_RUNTIME_PENDING。
- User decision: 对原update_time为NULL的角色，以原create_time初始化正式update_time；原NULL在迁移证据列明，原空创建/更新人保持空值，不用本次运行时间或执行人替代。
- Implementation: SYSTEM拥有正式角色写入，事务与同步绑定/证据一起提交；角色表不新增字段，不修改已存在的角色权限。实际Java模板已在原表只读核验31条、22原列，没有缺列。

### Q-LEGACY-IDENTITY-USERS-EMPLOYMENT-20261009-011 — 任职和准入属性

- Status: USER_RULE_CONFIRMED / IMPLEMENTATION_IN_PROGRESS。
- User decision: empID、reportTo、wfreportTo、empStatus、jobStatus、empType、state及多公司任职作为custom_info.employmentAssignments业务属性；custom4作为projectTypeRestrictions，不变成项目访问授权。
- Boundary: 主管/人员引用须真实核实，3条无账号资料登记待关联，不伪造账号；任职历史不自动激活当前EHR状态或审批。

### Q-LEGACY-IDENTITY-ACCOUNT-ADOPTION-20261009-012 — 来源与现存账号身份

- Status: BLOCKED_BY_SPEC / USER_ADJUDICATION_PENDING。
- Evidence: 来源t/fnd admin名称分别为系统管理员/管理员，当前平台admin ID=1；来源sse名称分别为测试账号/sse；旧test t_user=999999名称测试账号，现存test ID=104名称测试号。
- Question: 请求明确admin合并及关联当前账号、sse两来源合并、test关联现存账号；保留当前凭据和既有权限，不仅以用户名相同推定同一身份。
- Blocking scope: 对应账号及依赖其身份的角色/项目审计写入；其余独立代码和原表核验继续。没有实际身份写入。

### Q-LEGACY-IDENTITY-NULL-COMPANY-20261009-013 — 五条无公司普通角色关系

- Status: BLOCKED_BY_SPEC / USER_ADJUDICATION_PENDING。
- Evidence: t_user_role271~275的comp_id为NULL，账号z04214、w04218、y04223、w04261、z04264，角色均user。
- Question: 建议正式system_user_role承接角色关系，保留原NULL来源证据，不生成未经指定的公司/办事处范围；或由需求方指定公司范围。
- Blocking scope: 这五条关系的范围承接；不把NULL理解为全部公司，不自动授予项目访问权。

## DPPMS-IAM-014 system_users历史更新时间（已裁决）

用户批准：所选主账号原update_time/updateTime为空时，仅用原创建时间初始化；原NULL列入来源证据，不用运行时间。原表t_user45条、fnd_user_info252条为空；不能把这些原始行数当作最终主账号写入数。

## DPPMS-IAM-015 system_users联系字段共用（BLOCKED_BY_SPEC）

原表核对及已裁决优先级初步合并后，27组邮箱、11组手机号由不同账号共用。建议将冲突联系值承接到已批准custom_info的明确联系人属性，未裁决正式归属前不写入冲突正式email/mobile；现存联系人保持。待用户裁决，不降低唯一规则、不合并身份、不丢弃原值。完整清单见docs/generated/2026-10-09-dppms-user-contact-and-login-decision.md。

## DPPMS-IAM-016 system_users历史登录名（BLOCKED_BY_SPEC）

317个不同来源登录名不满足当前4~30位英文字母数字规则，实际含中文、下划线、2/3位账号及1条U+00A0尾字符。待裁决保留原名并批准输入兼容，或指定新名并保留明确别名；不得自动放宽认证接口、改名或修剪/合并不可见尾字符记录。

## DPPMS-IAM-015 联系字段裁决更新

Status: USER_APPROVED_MIGRATION_ONLY_SHARED_CONTACTS。用户批准仅迁移过程允许重复邮箱、手机号，完整承接正式email/mobile，不改存JSON、不按联系人合并账号。普通新增及联系方式变更仍检查唯一性，未变更的历史共用值允许保留；手机号关联多个账号时短信登录/找回阻断，不任选账号。 此前JSON替代正式联系列的提案撤回。没有实际IAM写入。

## DPPMS-IAM-016 / Q-LEGACY-IDENTITY-ACCOUNT-ADOPTION-20261009-012 账号范围更新

Status: ACCOUNT_SCOPE_APPROVED / SELECTED_LOGIN_INPUT_PENDING。用户限定仅迁移首字母+工号、*_ss、*_supp；严格按原账号字符串筛选，不改名或修剪。原t_user329条、fnd_user_info866条，合并1176个账号、对应330条任职；304个选中账号的登录输入兼容仍待裁决。admin/sse/test账号迁入提案被最新范围裁决取代，旧admin历史审计关联另待指定。 原admin/sse/test资料迁入提案撤回；不把历史操作者关联当作迁入被排除的账号。

## DPPMS-IAM-017 被排除账号的历史操作者

Status: BLOCKED_BY_SPEC / WAITING_USER。99处选中账号审计引用旧admin；已询问是否仅以历史操作者身份关联当前admin ID1，保留当前资料、凭据和权限且不迁入旧角色或授权范围。未回答不关联。

## DPPMS-IAM-018 已选中账号输入兼容

Status: BLOCKED_BY_SPEC / WAITING_USER。304个*_ss/*_supp选中账号不符合当前输入规则；已询问是否保留原名并仅兼容账号字符规则，或只迁入历史身份暂不开放登录。迁移范围裁决本身不推导认证输入变更。

## DPPMS-IAM-019 选中账号邮箱格式

Status: BLOCKED_BY_SPEC / WAITING_USER。w30049（fnd117）邮箱缺少用户名，zhaoyangxi_supp（fnd862）邮箱尾部制表符。已请求指定正式邮箱或明确历史属性承接例外，以及确认制表符规范化。迁移允许重复不推导无效格式豁免；未回答不猜补、不丢弃。

## DPPMS-IAM-020 工号关联姓名差异

Status: BLOCKED_BY_SPEC / WAITING_USER。w00343（fnd王俊柯/EHR王俊珂）、j04650（fnd金燕铃/EHR金燕玲）、l02826（fnd及员工表乐征侨/EHR登录目录乐征桥），目录员工外键与工号已核实，尚不能擅自判定同人。已请求用户确认三组同人、姓名沿fnd且工号沿EHR外键，或逐项指定关联；未答不建立关联。完整差异见[公司与人员引用核对](../generated/2026-10-09-dppms-user-directory-reference-audit.md)。

### DPPMS-IAM-020 用户裁决

Status: USER_APPROVED_SAME_PERSON_EHR_NAME_AND_EMPLOYEE_NO。用户回复“同一人，用EHR的姓名和工号”。w00343→员工74、王俊珂、00343；j04650→员工4804、金燕玲、04650；l02826→员工2770、乐征侨、02826。正式nickname及employee_no以ehr_employee.name/workNo为准，保留实际登录目录外键与原姓名差异；不把冗余ehr_login.name当作员工主姓名。这三项例外不改变其他账号fnd主、t补缺规则或账号状态、联系方式及授权。尚无实际账号写入。

### DPPMS-IAM-017/018/019及公司为空角色关系最终裁决

状态：USER_APPROVED，覆盖同项旧待裁决记录。

- 保留选中*_ss、*_supp原登录名；登录与账号维护输入兼容这些形式。注册规则不在此裁决范围。
- 旧admin不迁入账号资料，仅将历史创建/更新人关联当前admin ID1；不导入旧角色或授权；目标缺失或绑定改变时阻断。
- 用户明确例外：原值@dptech.com直接承接正式email，不置空、不改写、不用custom_info替代。普通新建/修改邮箱格式校验保持。
- 正式email仅去除来源尾部制表符，原值完整留来源证据。
- z04214、w04218、y04223、w04261、z04264迁入正式用户角色关系，原公司NULL留证据；不新增公司或办事处授权。

## DPPMS-IAM-021 公司部门范围整表承接

状态：BLOCKED_BY_SPEC。现有20列无结构新增；范围历史时间、主范围标记及37个部门码的承接与转换待需求方裁决。完整核验见docs/generated/2026-10-09-dppms-user-company-department-scope-table-decision.md。只继续不依赖这些问题的账号和角色工作，尚未写入本表。

### DPPMS-IAM-021 历史时间与主范围裁决

状态：上述三项USER_APPROVED；缺失部门的处理和实际主范围冲突仍待裁决。用户批准三项：无关系日期用原账号创建时间初始化起始/创建时间；原关系NULL更新时间用原创建时间初始化；custom3/dpNo主范围，areapower/custom5非主范围。

## DPPMS-IAM-022 system_dept整表裁决

状态：BLOCKED_BY_SPEC。用户选择提交system_dept整表后，已核对16既有列、9原fnd列及24个EHR参考列。28项拟补正式部门、9项3112→1621明确映射；缺失业务属性、启停/名称冲突、无来源层级须裁决。清单见docs/generated/2026-10-09-dppms-system-dept-table-decision.md。未进行DDL或正式写入。

DPPMS-IAM-022裁决更新：16前缀父项160000，31前缀父项310000，不新增字段。原isparam及有效期如何处理、其余处理建议仍等待补确认；不自行排除。

DPPMS-IAM-022最后裁决：isparam及部门有效期不迁入并列明原因，280700挂280000、999999根级；system_dept无新增字段。正式目录同步尚未实施。

## DPPMS-IAM-023 用户主范围冲突

状态：BLOCKED_BY_SPEC。7个账号t/fnd自身范围不同，逐项见docs/generated/2026-10-09-dppms-user-scope-primary-conflicts.md。等待主来源以及另一范围有效性裁决，不默认扩大授权。

DPPMS-IAM-023裁决更新：用户回复“fnd为主、t补缺”，7项冲突以fnd自身范围为主、t不同范围作非主补充；状态和历史时间沿已确认规则，不新增公司或项目授权。

## DPPMS-IAM-024 无角色账号范围裁决

已裁决：konglinghui_supp范围不迁入；原角色为空，用户明确排除，不擅自生成角色或授权。账号资料仍按原账号迁移规则承接。

## DPPMS-IAM-025 空附加范围及孤立资料裁决

状态：USER_APPROVED_EXCLUSION。

- fnd_user_power选中账号109条areapower为空，用户明确“不迁入：空范围没有附加授权事实”。完整10原字段及日期、操作者按此列明排除原因，不写入system_users.custom_info、不生成公司或区域授权。
- t_user_info 1000186（test）、1000188/1000189（admin）没有user_id，用户明确不迁入，原因“无账号外键且不在已批准账号范围”。仅覆盖这三条已核实资料，其他未来孤立资料仍登记待关联，不自动排除或伪造账号。
- DPPMS-IAM-021/022/023/024同项原待裁决记录均被后续用户结论覆盖：20个范围字段及16个部门字段无新增列，部门父项、9项精确编码转换、主范围和历史时间均已确认。
- 验证边界：范围Owner5项、适配器7项、模板1项隔离检查通过；真实原表3275条只读展开9492条候选，用户绑定及28个新部门ID为模拟前置结果。正式账号、目录及范围迁移尚未执行，不能声明真实落库或授权验收完成。

## DPPMS-PROJ-026 项目状态、类型及历史更新时间补充裁决

状态：USER_APPROVED_IMPLEMENTATION_PENDING。

- 原projectState=100由正式迁移Owner初始化为NORMAL_CLOSED、S6；原20初始化为EXCEPTION_CLOSED，正式原因保留“不予跟踪”。关闭时间使用原projectCloseTime；不新增状态，不生成当前任务、审批完成事实。
- 原31/32只按可核实的历史项目人员关系承接指派，不能仅凭状态编造负责人。
- projectType=10复用proj_project.project_type，并在现有pms_project_type字典补入“用服售后项目”。来源全表80320条均为10；不得猜成网络集成等细分类，不新增列。
- 本批1条原updateTime为NULL，用同项目原createTime初始化正式update_time，原NULL列明；其他历史时间按原值。
- 用户已分别回复“按上述状态对应迁入”和“两项按上述处理”。本记录不表示正式项目落库、DDL或运行验收完成。


## DPPMS-AST-027 发货整表重复列撤回裁决

状态：USER_APPROVED_IMPLEMENTATION_IN_PROGRESS。

用户最终确认按整表建议1、2、3处理：主副产品技术ID不作为业务键，名称/型号由正式产品目录承接，发货更新时间复用source_updated_at；不新增列、不创建去前缀的重复字段。7列代码及当前SQL新增定义已撤回；全表值保护与运行纠正未完成，未删除真实库列。

## DPPMS-PRODUCT-028 正式产品目录实体扩展裁决

2026-10-14最新裁决（用户明确调整，生产任务规则已更新、代码改造待完成）：同产品编码名称不同时以CRM为准，不再用扩展承接DPPMS名称/型号。P12只通过AST正式Owner关联或补齐`ast_product_official_info`；缺失产品仍按既有批准映射创建并设可用，已有CRM正式字段保持。后续生产迁入不创建或写入`dppms_catalog_labels`，不要求产品扩展定义修订，也不将差异转存到其他字段。现有模板、适配器及Owner迁入/解析仍依赖扩展，需按此规则调整后重新验证。原库已有扩展、不可变定义、来源绑定及历史运行保留，本裁决不授权清理原库或执行迁移。见[生产迁移任务P12](../superpowers/plans/2026-10-14-dppms-production-migration.md#p12-dppms缺失产品补齐与编码关联)。

以下为2026-10-09原裁决及当时实施记录；产品扩展承接要求由上述最新裁决替代。

状态：USER_APPROVED_IMPLEMENTATION_IN_PROGRESS。

用户确认“复用正式实体扩展承接”：正式目录21列不增加字段，既有CRM名称、描述、状态保留；缺失5个产品按已批准规则补齐并设可用。原fb_items四列完整核对，id为无业务意义技术键，按裁决不迁入；item按租户+产品编码关联，itemname/describe_以dppms_catalog_labels的productCode/productName/productModel明确业务属性写入既有plt_entity_extension_value。AST提供正式Owner、PLT管理扩展定义和值，INT迁移/同步维护来源绑定和运行过程。未经批准拟建的ast_product_source_record路径撤回，源码留存于任务私有纠正证据；没有新增物理表、列。代码正在实施，尚未执行真实目录迁移及页面验收。


## DPPMS-COM-029 装箱单整表复用与承运商编码

状态：BLOCKED_BY_SPEC。

已核对com_shipment_package的19既有字段及3新增列、原fb_shipment8列、物流视图6列及fnd_basic_data12原列。建议撤回source_contract_key/source_carrier_name/carrier_code物理新增列，合同关系及名称复用正式字段，原承运商编码以既有正式实体扩展carrier_code业务属性承接。完整清单见docs/generated/2026-10-09-dppms-shipment-package-table-decision.md；等待用户整表裁决。其他基础词典业务属性仍登记在来源基础数据领域，不默认排除；未执行DDL或业务纠正。

DPPMS-COM-029裁决更新：用户确认其余整表建议，并指定carrier_code为物理列，与carrier_name分别保存原编码和名称；不实施承运商编码实体扩展提案。source_contract_key/source_carrier_name两列代码和当前SQL新增定义撤回，原合同键由正式归属关系核对。物理重复列清理及真实业务验收尚未完成。


### DPPMS-COM-030：com_sales_order 整表字段纠正

- 状态：BLOCKED_BY_SPEC，等待用户整表裁决。
- 完整审查：`docs/generated/2026-10-09-dppms-sales-order-table-decision.md`。
- 建议撤回5个镜像列，source_erp_system更名正式erp_system，既有source_project_name更名project_name；原表16列与既有34列全部列明。9条旧01订单已经逻辑删除，不需业务合并，不复活或覆盖历史。


DPPMS-COM-030 用户裁决更新：按第1项撤回5列，source_erp_system直接复用source_system，撤回第6个镜像列且不新增erp_system。项目名称更名未批准，保留既有source_project_name。正在实现及验证，无真实库写入。


### DPPMS-COM-031：com_sales_order_line 整表裁决

- 状态：BLOCKED_BY_SPEC。
- 完整41个正式字段、4个新增列及16个原列见`docs/generated/2026-10-09-dppms-sales-order-line-table-decision.md`。建议撤回4镜像列、实际ERP系统复用source_system，无新增物理表/列；105原行全部原字段已读，2条原空bundleCode现为NULL，建议补入原空字符串，其余业务列精确相符，原缺单位依裁决保持NULL。


DPPMS-COM-031 用户裁决更新：整表按建议；撤回4个镜像列、source_system保存实际ERP，source_updated_at保存原版本时钟，保留已有原公司码来源键与ID；原空组合码原样承接，单位可空。代码和当前SQL已纠正，正在验证，真实库尚未调整。

### DPPMS-COM-032：com_crm_execution_order 整表纠正

状态：USER_APPROVED_IMPLEMENTATION_IN_PROGRESS。用户回复“整表按建议”。60个既有字段承接原表32列，撤回legacy_record_id、source_company_code；原技术ID沿INT来源绑定，公司码沿已批准转换。3条channelName及1条customerProjectName原空字符串补入已有正式字段。系统部、拓展部键及CRM对象引用复用既有system_source_key、expend_source_key、source_object_id。公司与部门引用通过SYS正式接口精确匹配，停用历史部门不会据此获得授权；9项已批准部门别名之外不启用截断或父级替换。

代码及当前SQL定义已纠正。122项COM/PROJ定向测试通过，另实际MySQL/XML验证及其受影响回归50项通过，其中3项为连接内临时表验证；真实业务行和表结构未改写。实际迁移、列清理、部署与浏览器验收未完成。

### DPPMS-COM-033：回款计划实体整表与历史审计

状态：BLOCKED_BY_SPEC。完整清单见`docs/generated/2026-10-09-dppms-payment-plan-table-decision.md`。当前com_payment_plan是本任务先前新增26列，建议独立正式计划实体保留22列，撤回4个重复审计镜像并复用正式审计字段；可核实原人员关联SYS用户，CrmInterfaceUser、crmapp原服务操作者字符串处理等待本次裁决。原表全部16列已查，35501条原记录创建/更新时间均非NULL；本批10条正式审计日期仍非原值，需纠正。既有合同应收金额统计不能通过伪造必填金额承接事件计划。等待用户整表裁决，未实施本表纠正。

DPPMS-COM-033 命名裁决更新：用户最终指定`com_contract_collection_plan`；回款属于收款业务，撤回payment命名提案。当前21列结构建议去掉单项目绑定、将事件名称按event_name承接，字段及审计处理尚待整表裁决。完整清单见docs/generated/2026-10-09-dppms-contract-collection-plan-table-decision.md。已有10条数据及旧迁移凭据保留，未调整真实库。

DPPMS-COM-033 既有链路及原表复核更新：用户明确`com_contract_receivable`是合同回款信息，不是计划，不作为计划候选；阶段/任务acceptance_time及PAYMENT_PLAN_ACCEPTANCE既有Owner已核实，应复用其日期消费。原basicDataName通过旧CRM/SMS的money_item_name取值，为回款款项名称，撤回未批准的event_name建议，改提collection_item_name。原afterDaysNum为int(11)，撤回无依据的小数类型建议。现待裁决方案为19列、撤回7列；原技术ID仅用既有INT绑定，原业务来源由data_source完整承接，不新增source_*镜像。无法唯一关联合同时允许正式计划contract_id为空并登记待关联，以及未迁入服务操作者原审计字符串处理，均等待用户同表裁决。全部16原列、19候选列、7撤回原因及全量统计见同一整表清单；未改业务代码、SQL或实库。

DPPMS-COM-033 同步身份缺口：旧CRM任务删除重插并重置原表自增ID；合同号、批次、款项、参考事件及来源的组合有597组、1,253条重复。原id不是跨刷新稳定业务键，不能以组合唯一约束去重或自动重绑。历史基线内可复用既有INT绑定执行迁移，但扩大持续同步须取得稳定来源身份或明确裁决，仍为BLOCKED_BY_SPEC；现有隔离日期同步数据不计为本批历史计划迁入。

DPPMS-COM-033 用户整表裁决：回复“19列及上述处理整表按建议”。19列结构、撤回7列、正式计划合同引用为空待关联、CrmInterfaceUser/crmapp原审计字符串处理均已批准，结构问题转USER_APPROVED_IMPLEMENTATION_IN_PROGRESS；持续同步稳定身份问题仍等待第二项回复。19列实体及COM Owner/Mapper/SQL已实现，Owner 8项行为测试通过；INT适配器/模板验证中，旧原生入口和查询替换、实际库纠正、部署及业务验收未完成，不宣称已迁入。

DPPMS-COM-033 实施验证更新：批准19列已实现COM Owner、INT同步及事实核验、项目纠正凭据和正式查询；旧PaymentPlan直接写入已撤回。75项相关验证通过，原库只读模板保留全部35,501条和16原列。旧10条15项原业务/审计属性与原表无差异，三个原人员审计须先完成已批准SYS迁移；真实库切换、审计纠正、列清理及浏览器验收未执行。持续同步稳定身份仍为BLOCKED_BY_SPEC，等待用户第二项回复，不以测试通过宣称生产迁移完成。

### DPPMS-PROD-034：原项目产品整表与正式订单行复用

状态：BLOCKED_BY_SPEC。原表11列35.303万条，本批105条均唯一匹配正式订单行，合同/名称/数量全等值；既有实施分配范围无法承接原NULL项目数量。建议本任务新增的proj_project_product由21列整改为10列，仅关联正式项目和订单行，项目数量保留NULL，撤回12个技术/重复列；全表值和正式引用核验后才清理，来源ID用既有INT绑定，原单位复用可空正式字段。完整21/11/10列及处理见docs/generated/2026-10-09-dppms-project-product-table-decision.md；所有结构变更待用户整表裁决，未改实库。


### DPPMS-IAM-035：用户历史任职缺少8项正式部门

状态：BLOCKED_BY_SPEC。用户选择“补齐缺失目录，提交EHR完整字段确认”。8项、10账号；3项有fnd主记录、5项仅EHR。system_dept现16列保持不增列，EHR全部24原列及精确公司/父级已核对，t_department无本8项记录。父项、缺历史审计、主管关联、11项正式扩展属性及7项本批全NULL排除方案须同表裁决。清单docs/generated/2026-10-09-dppms-system-dept-eight-missing-table-decision.md。用户同步最新预览正确阻断，不清空部门、不任意按名称关联；8项尚未写入，账号迁入0。此前批准28部门已通过INT Owner正式同步SUCCESS并核对16列，task2108524501517955073/run2108524710973108226。

DPPMS-COM-033 实际运行更新：获准SYS两列和项目五列已准备；保留原10条ID和数据将计划表更名com_contract_collection_plan，正式类型已调整、7旧列暂留待承接核验。后端59282新代码健康与真实认证/页面通过；计划审计、绑定、项目纠正及列清理未执行，用户前置仍因新增8项部门缺失等待IAM-035裁决。Flyway原历史未改变，校验对齐未完成。

DPPMS-PROD-034 只读全量追查：已核实建档/合并/拆分/ERP刷新/展示；353030条中projectQuantity全部NULL仅描述本库，不否定独立项目数量；58条发货/未交数量不同于当前订单，417条对应1203技术来源候选但五项属性全部等值。原ID会被刷新重排，原10列提案不足且未批准，保持暂停。完整证据docs/generated/2026-10-09-dppms-project-product-purpose-audit.md。

DPPMS-IAM-035 用户补裁决：保持system_dept结构不变、不新增列、不用扩展字段承接。11项部门实体扩展方案撤回；是否明确排除11项及本8项7项全NULL字段、父项、缺历史审计和主管关联，已同表补请回复。未将拒绝扩展等同批准丢弃。8项和用户仍未迁入。

DPPMS-IAM-035 用户最终补裁决：“剩余按建议”。16列保持、11缺项及7项本批全NULL明确不迁入并逐列记原因；3项FND挂160000保留历史审计，5项EHR挂真实现有父级、采用实际迁入时间和操作者；原停用保持；主管director通过EHR员工→精确来源账号→SYS用户绑定关联leader_user_id，无绑定先待关联。转USER_APPROVED_IMPLEMENTATION_IN_PROGRESS，不使用实体扩展。采用独立EHR来源任务复用现有SYS部门Owner，原28项任务和不可变证据保持，尚无本8项业务写入。

DPPMS-IAM-035 实际运行更新：8项任务2108538095265562625、运行2108538252883312642为SUCCESS/CREATED=8，全部16列与原24列及裁决逐项核对通过，没有扩展写入。用户账号1176已由正式迁移任务迁入；部门主管补关联预览2108540153620250625为UPDATED=8，尚未执行。保留原历史状态描述作为过程记录，不将其视作当前未裁决。

### DPPMS-IAM-036：本次用户迁入生成了错误更新人

状态：USER_APPROVED_CORRECTION_IN_PROGRESS。原运行2108539862736879617创建1176个账号，其中557个原NULL更新人被框架自动填成“1”。逐条核实其他业务、审计日期、绑定均符合批准值；用户明确“仅这557处按上述条件纠正”。独立纠正运行经INT和SYSTEM Owner核实原成功运行、17个非凭据原字段、原绑定及完整前值，只恢复这557处updater=NULL，保留原回执；任何人工变化整批阻断。没有新表或新列。详细范围、失败保护及30项已通过检查见docs/generated/2026-10-09-dppms-user-null-updater-correction.md；实际纠正尚未执行。

DPPMS-PROD-034 用户追问与范围复核：用户指出项目产品与实施范围相似，默认全部订单、拆分/合并时保存分配数量。19列候选也未批准。正式实施范围42列及明细21列已只读核对，本批10个项目尚无分配记录；现成分配参与订单数量占用、版本与验收绑定。按业务主体优先复用COM范围，尚需明确默认覆盖与当前分配、历史数量的映射，不把旧清单自动变成当前分配动作。两版项目产品结构提案均暂停。

DPPMS-IAM-035 实际主管补关联完成：运行2108549492405899265为SUCCESS / UPDATED=8，全部16列含主管及历史审计再次核对通过；无扩展、权限或范围写入。重复预览和业务页面验收尚待完成。

DPPMS-IAM-036 审计纠正完成：首次恢复557处更新人后发现MySQL ON UPDATE同时修改日期，保留该回执；任务版本1、运行2108549172460195841在完整前值及明确原纠正运行时间窗保护下恢复同557处原历史日期。1176个新用户正式字段、JSON、岗位关系及审计值核对通过；重复预览2108549489075621889为UNCHANGED=557。32项受影响检查通过。普通用户任职引用补关联和业务页面验收尚未完成。大配置导致任务列表排序内存错误，正在修复分页查询，无新表或列。

DPPMS-PROD-034 建议更新：用户要求给出是否合并的建议。建议统一到既有实施范围，避免项目订单数量维护双份；NULL默认全量与拆分显式数量应保留明确业务含义。全历史58条旧交付数量差异、53组重复和当前有效分配必须整表列明待裁决，不调用现行分配动作、不去重、不清理105条。业务归属确认问题已提交，尚未回复。

DPPMS-PROD-034 业务归属已裁决：用户回复“合并到实施范围，提交整表方案”。独立proj_project_product提案撤回，正式承接核验前105条保留。com_delivery_scope现有42列＋候选4列、原11列映射及com_delivery_scope_detail现有21列完整清单已提交docs/generated/2026-10-09-dppms-delivery-scope-table-decision.md；结构未获批准，范围数据未写入。另核实3057条零订单数量及8232组多项目候选重叠，公司/ERP解析待完成，不能直接激活当前分配或去重。

DPPMS-IAM-036 列表故障修复：任务分页仅排序ID再读定义，不让大JSON进入MySQL排序缓存；真实MySQL2项检查及完整运行包通过，后端59282已切换，真实浏览器任务两页26条可读。部门重复预览2108553120462053377为UNCHANGED=8；用户任职引用补关联预览2108553118025162754待逐条复核后执行。角色真实自动填充上下文5项事务检查通过，未发现同类审计问题，未改角色业务代码。

DPPMS-PROD-034 三列裁决：用户要求已有实体列直接承接、新增列逐项列示。撤回order_quantity候选，复用正式order_qty；用户回复“三列按上述含义批准”，批准allocation_confirmed、delivered_quantity、open_quantity。现有8项条件可空及唯一槽位规则正在同表补裁决，未实施范围数据、SQL结构或列清理。明细21列拟不改、不伪造设备明细。

DPPMS-PROD-034 数量、办事处与分配结果补裁决：用户明确“旧范围直接按项目办事处，全订单数据进行填充”，撤回allocated_qty及办事处四项可空方案，projectQuantity为空时写正式订单行完整数量，显式拆分数量按原值，办事处四项取正式项目事实。对scope_status、allocation_version、effective_from，用户回复“按正式分配时应该产生的结果进行填充”，撤回三项可空方案。已核实正式Owner assign生成ACTIVE、项目scope_version+1、实际执行时间，且同时占用订单量、记录分配事件、推进范围版本并绑定验收。此前批准旧范围allocation_confirmed=false、不占用当前订单分配量；只生成这三项还是转为真正当前分配，存在业务含义冲突，已提交明确二选一问题并等待回复，标记BLOCKED_BY_SPEC。两条实现路径均未写入范围业务数据，不自行改为true或绕过分配总量、唯一性及验收规则。

DPPMS-PROD-034 分配效果最终补裁决：用户明确“仅填充三个字段，保留 false（建议）”。三字段按Owner生成规则填充，旧范围不占用当前数量，不调用assign触发分配或验收事件；原8项可空方案全部撤回。三个获准物理列及仅已确认分配生成当前唯一槽位的DDL已在指定任务库准备，现有18条范围全部原字段和审计值逐列不变、仍为confirmed=true，业务新增0；Flyway历史未修改。后续Owner迁入、消费者不计未确认数量及真实页面验收尚未完成，全历史零数量、身份重排及多来源关联仍独立登记，不擅自丢弃或启用持续刷新。

### DPPMS-IAM-037：本次角色迁入自动填充了原NULL审计人员

状态：CORRECTION_PLAN_IN_PROGRESS。角色同步task2108554583850524673/run2108559110330822657为SUCCESS，31条原角色迁入25个新角色及6条来源引用。实际落库核查发现13个新fnd角色的25处原NULL创建/更新人被框架填成当前管理员“1”；其他正式字段、历史日期、已有角色定义及角色菜单/用户角色/范围关系均未变化。原成功回执及原来源绑定保留，不普通重放覆盖现有审计差异。已在真实自动填充初始化顺序下复现（原测试注册顺序未实际激活RoleDO自动填充），显式SQL改读不可变原审计参数，修复后原5项事务检查通过。限原成功创建运行、13个完整前后值、同源绑定和全部11原列的独立INT/SYSTEM纠正路径正在准备，实际角色纠正0，待完成回归后提交用户裁决。没有新增角色表或列，不处理既有角色、不改定义或权限，原NULL与空字符串分别保护。

DPPMS-IAM-036 任职引用补关联：193个账号的898个来源回执引用均按原员工外键及1527来源绑定精确核实，原1566行完整读取；执行2108554375892738049为SUCCESS，1176账号正式资料/JSON/岗位及历史审计再次核对通过，原22账号业务及权限关系不变。重复预览仍判676个来源关系UPDATED但无业务字段差异；已复现JsonNode小Long与持久化后Int类型差异，适配器将JSON按实际持久化形式规范化，13项受影响检查通过。未据假更新再次写账号，正在部署后验证幂等。

DPPMS-IAM-037 纠正预览已就绪：SYSTEM事务9项及INT适配器4项通过，新增任务2108567804242235394/预览2108567804707803137为PREVIEW_READY，13条UPDATED；逐条核对仅creator/updater共25处从字符串1恢复NULL，所有其他字段和日期一致。完整角色清单docs/generated/2026-10-09-dppms-role-audit-correction-decision.md，纠正业务写入0，等待用户限定裁决。此前用户557处授权不适用于角色。后端59282已恢复，原角色运行及绑定保持。

DPPMS-PROD-034 消费者保护完成增量：范围查询页面可读未确认记录；当前数量统计、当前调整/释放、ERP范围释放及验收锁仅处理allocation_confirmed=true。真实MySQL临时表3项通过，覆盖重复未确认范围、不占当前唯一槽位、空权限返回空、租户/历史/删除边界及ERP不释放未确认范围；相关76项既有检查通过。Vue组件运行2项验证原数量、正式办事处及操作入口隔离。完整打包通过，后端59282已恢复；Owner迁入尚未执行，原105条仍保留，真实旧范围页面验收待迁入后执行。

DPPMS-IAM-037 已裁决并完成：用户明确批准限定13个角色25处纠正，执行2108568396289216514为SUCCESS/UPDATED13。实际核对25个新角色、31条原绑定及原15个角色全部正式字段，25处NULL和历史日期正确；角色菜单/用户角色/范围关系未变。重复预览2108568455621840898为UNCHANGED13，完整前后值一致。本问题的限定纠正已完成，不扩大到其他记录。

DPPMS 用户角色关系承接完成增量：任务2108568095503093761、执行2108569008816984065为SUCCESS，1709条原表完整资格记录展开1932条来源授权，实际形成1927个正式账号角色组合（完全同组合的5个来源引用复用同一正式关系）；51项按已批准账号范围及停用角色排除，原37个关系逐字段保持。角色、菜单、迁入账号及公司部门范围未变，当前操作者的登录时间由既有认证逻辑正常推进。1932条来源绑定逐条核对；重复预览2108569187448197122为UNCHANGED1932/SKIPPED51。既有SYSTEM事务4项及INT适配器5项验证前提保持有效，没有重复执行无关检查。公司部门范围任务2108569376783273985/预览2108569376992989186已就绪，原3275条完整资格记录展开9492条候选及1329项排除；尚未执行范围授权，执行前核查实际自动填充与全部来源规则。

DPPMS 公司部门范围审计保护：在实际框架自动填充环境中复现范围Mapper对DO原NULL操作者补管理员1，并令重放被历史保护正确阻断。生产范围尚未执行，未污染目标数据。历史审计SQL改读不可变Audit参数，保留显式原update_time；普通范围写入仍沿现有自动填充。SYSTEM5项事务及INT7项适配器/1项模板检查全部通过，涵盖真实自动填充对照、原NULL和日期、人工修改保护、失败回滚、跨租户及空集合不扩大授权。正在打包部署，本次不新增表/列，不放宽审计漂移规则。

DPPMS 公司部门范围承接完成：执行2108571476367917057为SUCCESS/CREATED9492/SKIPPED1329；9492条来源资格对应9477条正式历史关系，15条完全相同历史引用复用同一正式关系。全部20列、原NULL审计、历史日期和绑定逐行核对；原41条正式关系及角色、角色菜单、用户角色不变。重复预览2108571647373885441为UNCHANGED9492/SKIPPED1329，完整关系前后值一致。办事处资格仍不替代项目授予，不据此扩大外部用户项目访问。

DPPMS-COM-033 人员缺口裁决及正式执行：l01663在原t_user、fnd_user_info和当前system_users均不存在；用户批准仅原计划ID10725、14943、28926、31337的8处原编码保留正式creator/updater，不新增账号或授权。任务2108574253093683202以已审查配置显式限定这4条、2字段，其他未裁决、歧义或存在却未绑定账号仍阻断。执行2108576833605644289为SUCCESS/UPDATED10，19正式字段、16原字段、ID、历史时间和业务关系逐项核对；重复预览2108577146274230273为UNCHANGED10。10条计划位于4个项目，现有纠正入口追加COLLECTION_PLAN_V1证据并重复返回REPLAY_COMPLETED；原20批次、来源记录、外部映射、快照及计划业务数据逐字段保持。7旧列清理、浏览器项目详情及阶段/任务日期消费未完成；持续同步稳定来源身份仍为BLOCKED_BY_SPEC，不自动去重597组重复或重绑刷新后的原ID。

DPPMS-COM-033 实际19列及页面验收更新：4项目10条计划的真实列表与详情15项业务属性全部核对通过，修正创建/更新时间的毫秒数字显示，并重验原日期和l01663编码。执行获准的7列清理及4项VARCHAR容量调整后实际表19列，撤回uk_source/idx_project两个依赖技术列的索引，保留正式合同索引；所有正式值、来源绑定、原快照和记录、原20批次及映射逐字段不变。清理后10项目正式接口19字段核对、真实页面刷新和重开详情通过；正式同步重复预览2108586172005396481为PREVIEW_READY/UNCHANGED10，正式业务行及绑定保持。当前SQL直接是最终19列，不增加删除迁移，Flyway历史未改；阶段/任务日期消费、持续同步身份及基线校验对齐仍待处理。

## DPPMS-MAINT-001 维保服务过程的四类原业务主体

状态：BLOCKED_BY_SPEC。只读原表293430条、44列及当前业务表55列完整清单见docs/generated/2026-10-09-dppms-maintenance-business-subject-decision.md。旧代码明确10售后项目181525条、20售前项目12122条、30非业务38480条、40自定义61303条；售前全部匹配独立售前项目头，非业务和自定义projectId=-1不能当正式项目引用。售后仅85条未匹配原项目头，不将其他三类合算为孤立售后项目。用户先前已批准项目维保服务记录业务实体，但四类全历史的业务主体与领域承接尚未明确；建议保留全部正式服务过程、按原主体区分，等待用户决定是否统一服务记录或分域规划，再提交所有物理字段一起裁决。未批准结构不执行；不依赖此问题的回款、项目及实施范围承接继续。更新时间254109条NULL、创建人1条NULL、1处原项目名称差异及原问卷/交付件引用均列明，不自行覆盖或丢弃。

DPPMS 项目详情整改遗留故障：真实页面触发ProjectPartyMapper.selectCurrent生成SQL读取未获准且实库不存在的source_created_at/by、source_updated_at/by，导致项目详情500。撤回ProjectPartyDO这4个镜像属性及本任务新增、无调用方的ProjectPartySourceService/Record/Mapper/XML和镜像专用测试；不补业务表字段，不改既有参与方或历史数据。连接级真实MySQL测试2项覆盖既有正式审计列、当前关系、过期/删除及跨租户，最终客户9项、项目迁移13项、计划纠正5项通过；完整打包并恢复59282后，真实认证下项目详情接口成功，浏览器显示实际项目名称。来源参与方历史审计的正式同步仍待实施，不能把撤回镜像依赖视为已完成承接。


## DPPMS-MAINT-001 — 2026-10-10 用户最新裁决

状态：USER_DEFERRED。用户纠正 pm_project_maintenance 为维护记录，并明确“该项暂不迁入”；此前维保服务记录建议撤回。本轮不再新增或补写维护记录，不将来源证据或旧迁移成功回执作为本轮正式承接完成。已引入17条全部55列只读核对未变化，清单见 docs/generated/2026-10-09-dppms-maintenance-business-subject-decision.md；处置仍为 BLOCKED_BY_SPEC，等待用户选择保留或有保护条件的撤回，未删除数据或改变原不可变回执。真正保修/维保来源及其他批准工作继续。


## DPPMS-MAINT-001 — 已执行“仅保留裁决和迁移方案”

用户明确不保留本轮维护记录实现。核实17条全部55列未变化、无业务数据库依赖后，已撤回本任务引入的17条及 proj_project_maintenance_record 表、DO/Mapper/Service/XML、正式资料入口及当前V414定义。原来源记录、快照、旧迁移批次/来源载荷/映射前后比对不变；当前状态为USER_DEFERRED，不再据旧MAPPED回执宣称承接完成。仅保留裁决及未来迁移方案，未来重启仍须整表裁决。相关25项检查及完整打包通过；本任务后端59282恢复，真实页面核验继续。未改写Flyway历史，整体基线对齐仍待处理。

## DPPMS-PROJ-038 主档迁移预览后的实际修改

状态：ADJUDICATED。正式主档模板、INT适配器及PROJ初始化Owner相关54项检查和宿主机打包通过，无新增物理表、列。预览2108608417834668034为PREVIEW_READY/UPDATED10；后续版本复核发现来源51922、目标992203060198的project_end_date从2027-03-31变为2026-10-14，version12→13，updater及update_time随业务动作变化，其他列不变。未执行旧预览，未接管后续修改；完整输入、旧预览及当前基线已记录。用户明确“保留新结束日期，重新预览后迁入10个项目”，仅此项目初次接管版本改用13；仍按既有裁决承接原审计人和历史时间。Owner不写project_end_date，执行前继续逐字段与新基线比较。来源修订、其他业务或运行时变化不在本裁决授权内；本次也不批准任何新字段。


## DPPMS-SCOPE-039 历史零数量范围

状态：BLOCKED_BY_SPEC。本批105条均为正数量，不阻断已批准迁入；全历史3057条旧项目产品orderQuantity为0，正式com_delivery_scope.chk_scope_quantity要求allocated_qty>0。已向用户提交同表裁决：是否仅allocation_confirmed=false允许0，已确认分配仍>0；或保持约束暂缓零数量行并列明原因。回复前保持原约束，零/负数量不落库，不伪造为1，不丢弃来源，不触发实际分配或验收。无新增表/列，负数量未获裁决。


## DPPMS-SCOPE-039 用户裁决

状态：ADJUDICATED。用户明确“保持现有约束，allocated_qty按项目数量、订单数量进行优先级填充”。项目数量优先，NULL时取正式订单数量；不满足allocated_qty>0的零数量行不写入、不改为1，作为待处理列明。保留既有数据库和已确认分配的正数量约束，未批准零/负数量例外。本批105条均正数量，继续迁入。

## DPPMS-SCOPE-040 历史范围与正式分配版本唯一键

状态：BLOCKED_BY_SPEC。实际MySQL验证确认uk_scope_version仍约束tenant_id/order_line_id/project_id/allocation_version，包含allocation_confirmed=false。现已批准的旧范围填充项目当前版本+1、保留false且不推进当前分配，会占用后续正式分配的相同版本唯一键，并阻断历史重复行。已提交整表约束补裁决：建议用MySQL函数唯一索引仅对已确认分配保持原四项版本唯一性，历史false以既有source_evidence核验来源身份；不新增可见业务列，保留正数量、当前槽位以及已确认分配的全部版本唯一性。未回复前不改变此索引，不迁入105条范围，不去重、不改变版本生成规则；其他已批准整改继续。隔离测试只修改连接级临时克隆，不修改真实表或放松其约束。


## DPPMS-FACTORY-041 全历史同设备多版本

状态：BLOCKED_BY_SPEC。原fb_soft_version全部5列、本批19行及正式23列已复核一致；全历史165801行，392个序列号重复，其中288个有不同版本组合。原表无主键、时间或顺序，不能任意选最新或去重覆盖正式唯一设备主档。本任务新增19列历史记录表尚未获整表裁决，不据旧回执认定结构批准。完整字段及计数见docs/generated/2026-10-10-dppms-factory-version-table-audit.md。多版本业务模型/当前版本选择待提交完整承接方案后由用户裁决；未改原表、目标、代码或回执。本批既有正确版本不重写。


## DPPMS-SCOPE-040 用户裁决

状态：ADJUDICATED / PILOT_VERIFIED。用户明确批准版本唯一键仅约束allocation_confirmed=true；false仍逐条核验source_evidence，保留重复行、不占分配槽位。采用函数唯一索引，无新增业务列；正数量、当前槽位、已确认分配含关闭历史的原四项版本唯一性保持。此前两个同义问题的答复均一致，仅执行一次。105条正式范围已通过Owner迁入、105条再次预览UNCHANGED及10命令重放验证；原18条范围、明细和当前范围水位不变。真实浏览器显示正式数量及未确认标记，历史重开且无调整/释放入口。2026-10-10重复产品表、DO/Mapper及当前SQL定义已在完整承接与依赖核查后退出，删除后105条预览及10命令重放仍通过，详见同表报告新增验收段。全历史稳定身份和整体Flyway基线对齐仍未完成，不宣称上线完成。

## DPPMS-FACTORY-041 保留规则裁决

状态：RETENTION_RULE_ADJUDICATED / TABLE_STRUCTURE_BLOCKED_BY_SPEC。用户明确“完整保留正式版本记录，冲突不自动覆盖主档（建议）”。392组重复、288组异值均不能据返回顺序去重或认定最新；现有正确主档不重写。本次仅确定保留规则，不批准新表/列或现有19列记录表。项目关联只读核对发现19条中7条缺当前正式设备/合同发货查询链，不能直接删除项目引用后宣称页面完整。整表结构和关联的候选方案须提交用户裁决，未明确前不变更结构、不扩写、不丢弃记录或原始证据。

同表13列及六列撤回方案已获用户裁决“13列及上述处理整表按建议”：docs/generated/2026-10-10-dppms-factory-version-record-table-decision.md。实际19条已完成13列结构整理，五业务列同名化，project_id可空且只表示可核实的旧项目关联。全部原值、旧绑定、ID/审计及整张23列主档逐项保护；5个有数据项目的新迁移对账和重放、10个正式项目页及真实浏览器通过，7条缺少当前设备的记录仍可查询。没有新增业务列。全历史持续同步身份规则和Flyway基线对齐仍未完成。

## DPPMS-INSTALLATION-042 旧设备地址的业务归属

- Status: 整表结构、安装状态、日期及本批当前位置处理已裁决；BLOCKED_BY_SPEC 仅限全历史串货关系、空地址及超长值。
- Evidence: docs/generated/2026-10-10-dppms-installation-original-purpose-audit.md；948台各一个原地址，948正式设备和当前项目全部匹配。其中20台当前已完成安装地址与来源不同，928台没有当前设备位置。原表没有地址更新日期、安装状态或完成日期。
- Decision: 后续裁决取代最初仅保留关联的方案：复用既有imp_eng_installation原32列；有地址采用状态2，原createTime填install_time/effective_from，NULL更新时间用原创建时间，NULL更新人保持。保留20个较新位置，较早记录effective_to取较新安装生效时间；928个空位置通过AST位置Owner补齐。
- Question: 用户已批准复用既有imp_eng_installation原32列及全部21原字段整表方案（docs/generated/2026-10-10-dppms-existing-installation-table-decision.md），例外明确有安装地址表示已安装，status复用2且保持不可空。原NULL更新时间用原创建时间初始化，updater保持NULL。用户进一步明确“有创建时间，存在的记录都是有效记录”，撤回NULL生效日期及修改生成条件建议；已提交原createTime初始化安装/生效时间、20条较早历史按较新安装时间结束并保留较新位置、928个空位置是否通过正式Owner补齐的裁决。20条创建时间均早于当前安装生效时间，详见受控installation-creation-time-current-conflict-review-20261010.json。新增表/列均为0；答复前不执行安装或位置整改。全历史串货、空地址和超长值仍待独立裁决。
- Boundary: 不新增表、列；不生成当前任务或审批完成事实。只经正式迁移/同步及IMP/AST Owner落实已裁决事实；保留原库、不可变来源证据和旧运行回执。预览后当前记录变化必须阻断。撤回任务新增来源事实表及21个镜像列须在完整正式承接、保护、重放和依赖核查后执行。


2026-10-10补充裁决：用户批准“保留20个较新位置，补齐928个空位置”。原createTime用于install_time/effective_from；有地址按既有状态2，20条较早记录effective_to取现有较新安装effective_from，928条通过AST位置Owner填空。原NULL更新时间以原创建时间初始化，NULL更新人保持。既有32列和当前位置唯一约束不改，不再新增地址历史实体。当前实施中，正式承接与重放验收后清理任务新增来源事实表及21个镜像列；全历史串货、空地址及超长字段仍待裁决。

2026-10-10本批验收完成：948条正式安装保留ID/历史审计，20个较新位置保持、928个空位置补齐；298条其他安装及原位置历史不变。已核查撤回21列及来源事实表，最终复用32列。清理后全948正式查询、普通安装分页、UNCHANGED预览、6个REPLAY_COMPLETED及真实页面重开通过。上方旧Question所述日期/位置事项已答复，不再申请。全历史特殊值、首次迁入及整体Flyway基线仍未完成。详见docs/generated/2026-10-10-dppms-existing-installation-table-decision.md。

## DPPMS-REAL-PRODUCT-043 项目真实产品清单整表结构

2026-10-10整表裁决：ADJUDICATED / PILOT_VERIFIED。用户批准“19列及上述处理整表按建议”，唯一新增data_source，清单商务编码复用project_code，3项技术镜像撤回。审计用实际迁入人员/时间，51条零及2条负数量原样保留。整表问题已解决，19列/正式Owner/本批10个空结果/重放及浏览器验收完成；持续刷新身份、数量更新和删除规则及整体Flyway基线仍待独立处理。

原核查：pm_project_product_line_real全部12列、7848条；上游全部10列、9992条；7848条均唯一精确匹配上游，清单商务编码与旧项目主表编码全部不同。本批10项目为0条。原库及未形成项目关系的上游行不丢弃。

## DPPMS-WARRANTY-SOURCE-044 来源契约与真实原表不一致

状态：BLOCKED_BY_SPEC。2026-10-10按当前配置只读核查，原契约登记的view_warranty（36列）不存在；实际view_warranty_source有31列，缺少newId、agentName、finalCustomerName、itemName2、describe_2。不能把展示/派生字段伪称原列，也不能静默丢弃字段或自动替换来源。已询问用户指定真实来源，或批准按实际原表重新追查这5项来源并提交保修整表裁决。完整源表采集继续失败关闭；不修改既有不可变证据。真实产品两张已批准原表可以独立核查并继续其结构、Owner及本批空结果验收。

## DPPMS-LEASE-PRODUCT-045 项目租赁产品整表结构

状态：BLOCKED_BY_SPEC / WHOLE_TABLE_DECISION_PENDING。完整11原列、当前22列及最终19列候选见docs/generated/2026-10-10-dppms-lease-product-table-decision.md。原表454条，本批10项目0条，当前目标全表为空；建议不增加物理列，source_project_code更名project_code，撤回3项技术镜像，lease_duration纠正为DECIMAL(16,2)租赁月数、data_source纠正为VARCHAR(25)。完整原业务字段、25组重复行的31条额外原记录逐条承接，不只保存旧页面最大执行单结果；无原审计建议使用实际迁入人员和时间。已向用户提交同表19列及全部处理，答复前不改变结构或写入；无键行持续刷新身份、同一基础项目编码的多项目关联另行裁决。

## DPPMS-PRODUCT-CONFIGURATION-046 项目产品配置层级整表结构

状态：BLOCKED_BY_SPEC / WHOLE_TABLE_DECISION_PENDING。全部11原列、当前22列、19列候选及层级核对见docs/generated/2026-10-10-dppms-product-configuration-table-decision.md。实际736条，本批10项目0条，目标全表为空。建议不增加物理列，source_project_code更名project_code，撤回3项技术镜像，item_group纠正为INT、data_source纠正为VARCHAR(25)；无原审计用实际迁入人员/时间。旧3项派生路径从正式字段计算；10个缺父路径节点原样保留并列明，不补造父节点。已提交同表19列及全部处理，回复前不改变结构或写入；持续刷新及基础编码多项目关联另行裁决。

2026-10-10最新裁决：044用户要求“原表不变，按原来的材料证明继续推进”，保留原36列契约和既有不可变来源材料，不切换库、不替换为31列视图；承接方案继续按原材料核对。当前连接缺表的运行事实仍列明，新完整采集不能跳过校验。045用户指定productFirstName→productCategory、productName→productType，明细三项使用product_code/product_model/product_name；原建议product_group_name撤回，19列其余处理沿整表方案。046用户批准19列及全部处理。045/046状态均为WHOLE_TABLE_APPROVED / IMPLEMENTATION_IN_PROGRESS，不再重复申请整表批准。
## 2026-10-10 软件登记及批次整表复核

### DPPMS-SOFTWARE-RECORD-047 — 软件清单登记实体与全部原字段

- Status: NORMAL_RECORD_WHOLE_TABLE_APPROVED / IMPLEMENTATION_IN_PROGRESS；批次与限定纠正另受各自裁决约束。
- Evidence: [既有历史表32列、全部28原字段及12列候选](../generated/2026-10-10-dppms-existing-software-history-table-decision.md)。V414观察表于2026-10-09创建；既有V110软件事件表于2026-09-17创建。用户要求优先复用既有版本历史表，原观察表30列方案停止实施。完整旧保存过程及1570643行统计证明未变化清单不能等同升级完成；现存635历史和425主档保护。
- Decision: 最新回复“按原来的裁决建议，新增12列”，替代此前保持结构的回复，批准原44列整表方案及审计/日期规则。新增12列、保留custom_info、source_log_key更名change_batch_id并转BIGINT、source_data_state更名data_state；现有版本和审计正式字段直接复用，不更新当前主档或设备投影。
- Question: 结构不再重复确认。635条既有历史纠正和观察表撤回另经具体预览裁决；正式批次结构仍待048裁决。
- 最新用途核查: 用户指出当前软件更新没有写入本表；重新核实635条全为本任务DPPMS迁入，当前代码虽有apply实现但无生产业务入口调用。V110是来源事件模型，不能称为当前软件更新功能使用的现成历史表。见[来源核查](../generated/2026-10-10-dppms-software-history-provenance-audit.md)。已批准12列裁决保留；软件结构/数据切换暂停，历史登记承接与当前版本更新闭环的实施边界待用户明确。
- 最新裁决: 用户要求“先补齐功能，再承接历史”，明确ast_device_network_version_event不能作为正常业务权威表。本表退出承接目标，原44列方案停止；原12列批准不自动适用于另一张表。用户进一步明确软件版本与ITR无关，由项目人员手工维护或上传日志自动回填，BOOT取Basic Bootloader；版本来源已裁决，不再重复询问。
- Blocking scope: 635条现存历史纠正、观察表撤回及未裁决批次处理；已批准结构和Owner独立实施继续。
- 当前阻断范围: 本候选结构和写入停止；正式表及字段方案尚需重新整表确认；635条纠正、观察表撤回及V414批次处理仍待具体裁决。用户要求只有正式明确缺失后才实施，不凭推测建设。
- 跨工作区补充核查: 主工作区已有上传日志解析回填及解析历史，DeviceArchivedLogParsingService调用DeviceLogParsedFactService，写ast_device_log_parsed_fact并更新ast_device_network_version和ast_device，不调用来源事件Owner。本任务旧工作树缺少后续代码，不能认定整个平台没有功能。指定库只读核查解析事实50条、DEVICE_LOG当前版本14条，软件写入0。后续解析回传源码及V410在主工作区未提交，本任务不复制未交接代码另建Owner。既有功能复用，手工维护及原28字段映射仍先核实明确缺口，证据见上述来源报告补充及existing-log-version-function-readonly-20261010.json。
- 正常业务时间规则已裁决: 用户明确“按上述时间规则，保留较新版本”。采用人工登记/日志接收时间判断，旧日志保留解析历史而不回退较新当前值；后续新日志只回填其确实解析字段，缺项保留现值。不再重复询问此规则，不将此批准解释为新表/列批准。
- 正常手工维护整表用途最新裁决: 用户要求软件历史使用具名实体列，档案before_snapshot/after_snapshot方案撤回；旧软件历史仅一次性迁移，不持续同步，之后维护和升级由系统操作产生。用户明确Bak为变更前版本备份、Change为是否变更，不再将其业务含义标为未知。
- 整表讨论追溯: 用户质疑已有previous_record_id仍重复保留Bak/Change；39列整表未批准，现修订为[当前版本26列直接复用＋正式历史31列＋8项正式扩展候选](../generated/2026-10-10-dppms-software-version-maintenance-table-decision.md)。建议V414观察表物理整理为ast_device_software_version_record，复用/整理22列、撤回12镜像、8旧业务列转既有正式实体扩展、候选新增9列；正常操作只通过具名前记录比较，不重复写八项。指定原表659行最新只读核对有8行四组Bak均含版本串、原Change与直接比较结果不同，旧页面实际拼接链，不能重算覆盖。扩展用途/配置及全部31列结构、9新增、索引、初始记录仍待裁决，产品扩展批准不自动适用。业务写入0；048批次和635事件纠正不据此获准。
- 最新正式裁决: 用户已回复“31列及旧八项扩展整表按建议”，批准31列、全部九项新增、八扩展及整表处理/索引/初始记录规则，状态WHOLE_TABLE_APPROVED / IMPLEMENTATION_IN_PROGRESS，不再重复确认。当前26列保持；软件结构和功能开始实现，真实数据尚未写入。原批次048、635事件具体纠正及提供方跨工作区交接仍独立处理。
- Decision owner: 需求方。

### DPPMS-SOFTWARE-BATCH-048 — 软件变更批次整表

- Status: WHOLE_TABLE_APPROVED / IMPLEMENTATION_IN_PROGRESS。
- Evidence: [9原字段、23当前列及11列候选](../generated/2026-10-10-dppms-software-batch-table-decision.md)。原16874批次，本批6条；全历史logId无孤儿且无跨项目关系。
- Decision: 用户批准11列及整表处理；原9列完整映射，原NULL操作者保持NULL，NULL更新时间取原创建时间，撤回12个技术/审计镜像，不激活当前升级/任务。
- Implementation boundary: 现有6条逐值核查，完整承接后才清理；有后续修改阻断，635条事件纠正不包含在本次批准中。
- Decision owner: 需求方。

2026-10-10最新正式裁决：用户批准“11列及上述处理整表批准”；本问题状态为WHOLE_TABLE_APPROVED / IMPLEMENTATION_IN_PROGRESS。原9列完整映射、原NULL操作者、NULL更新时间取原创建时间、12镜像撤回及6条逐值保护均按已批方案实施。635事件纠正仍未批准。

### DPPMS-SHIPMENT-CONTRACT-YEARS-049 — 合同保修年数与0/1标志的单位冲突

- Status: ADJUDICATED / LOCAL_VERIFIED。
- Evidence: [29既有列、12原字段及全量单位统计](../generated/2026-10-10-dppms-shipment-contract-reference-table-decision.md)、[保修36字段复用核查](../generated/2026-10-10-dppms-warranty-formal-reuse-audit.md)。原warranty用于×12计算月份，现引用表warranty_flag定义为0/1，却保存了原0～10年值；同值不构成同义承接。
- Question: 是否保持29列，仅将warranty_flag改为warranty_years、VARCHAR→INT NULL并同步修改既有迁移/同步映射；或指定原年数的其他正式实体字段？原空白/NULL处理、现存120638引用及来源数量差1均按整表方案受控核对，不绕过后续变化保护。
- Decision: 用户回复“接受建议”，批准整表29列、原空白/NULL转NULL、warranty_years整数年数及同批映射切换，不新增表或列。120638正式引用已逐键匹配；多出1条来源全空合同键/号且无业务值，完整列明无法形成合同引用的原因。2条目标空白通过迁移/同步及COM Owner纠正，其他28列和审计保护。
- Verified scope: 本任务59282已切换；120638行正式29列warranty_years为INT NULL，2条空白通过限定COM Owner及迁移/同步纠正。预览2108801389591171074 UPDATED=2；执行2108801440837177346 SUCCESS UPDATED=2；重放2108801493274365953与最终INT类型重放2108801809084485634均SUCCESS UNCHANGED=2。6个现有任务版本受控切换、仍暂停；其余28字段/审计/索引/来源证据/Flyway执行历史保持。早先SQL解析失败回执完整保留，修复后真实租户拦截器/MySQL验证通过。
- Remaining scope: 其他保修日期、等级及过期日差整表方案仍待裁决；生产历史SQL基线与既有Flyway校验对齐尚未处理，不能将本地验收称为生产上线完成。原缓存计算日仅从已知公式推断，不能当已找到生成链。
- Decision owner: 需求方。


### DPPMS-EQUIPMENT-ASSOCIATION-050 — 保持设备清单逻辑并补齐迁入范围明细

- Status: ADJUDICATED / LOCAL_VERIFIED。
- Evidence: [既有范围明细21列、正常产品明细Owner与具体补关系方案及实际结果](../generated/2026-10-10-dppms-equipment-association-repair-decision.md)。纠正前本批105条已关联正式订单行但明细为0，原页面只从details关联数量/订单；该只读问题基线保留。现已新增105条配套明细，总明细134条。原多订单项目12条范围按产品汇总9行、同产品两个订单及数量正确。
- Decision: 用户要求原页面逻辑不变，仅补迁移关联；本轮前端加载器及列调整已撤回，其他页面改动保留。此前逐范围显示的38条验收不得当作当前方案已完成。
- 补充裁决: 用户明确此前已批准pm_project_product_line按全量实施范围承接；漏写配套明细是实现遗漏，不重复申请批准。沿正常无SN产品数量明细规则补齐105条false范围，21既有列复用，不产生当前分配或验收动作。
- Verified scope: COM Owner及既有迁移入口已补105条21列明细，123条父范围和29条旧明细保持；105条重放UNCHANGED、10回执REPLAY_COMPLETED。31项定向测试及完整后端包通过；任务59282更新，原页面单订单两页32产品、多订单9产品及详情重新打开均核验数量一致。原来源和旧回执保护，不新增结构、不制造SN、不覆盖原页面逻辑。
- Remaining scope: 原设备首批200条读取限制及范围外产品行混入已在后续修复中解决；全历史稳定身份及实际发货引用缺口仍未完成。详见051及原验收报告的后续核查段，不能以105条明细补齐宣称全部设备关联完成。
- Decision owner: 需求方。

### DPPMS-EQUIPMENT-BUNDLE-051 — 设备清单产品边界及发货引用缺口

- Status: DISPLAY_ADJUDICATED / LOCAL_VERIFIED；REFERENCE_CORRECTION_PENDING。显示口径不再BLOCKED_BY_SPEC；发货及设备引用缺口已查明，实际补正尚未执行。
- Evidence: [后续原表、正式关系、旧查询及实际页面核查](../generated/2026-10-10-dppms-equipment-association-repair-decision.md#设备台数与组合产品的后续核查)。992203060189的32组/699台分页已修复并真实逐行验收，原汇总规则保持。992203060198原产品和订单均仅2行，共4＋16；另5类20台来自原发货和正式设备，不在原产品清单中，无独立原订单数量。原关系表按合同0条，“已确认组合内设备”的定性撤回；来源组合序列号原值保留。
- Decision: 用户明确“按实施范围订单产品显示（建议）”。仅范围明细建立产品组，设备只补这些组的身份和台数，不再追加范围外产品行；数量仍取范围明细和订单行，不复制父产品量，也不从设备台数推导项目量。真实页面992203060198仅2行，4/4/0/4与16/16/0/16正确，序列号详情仍40台。
- Known mapping defects: 40条发货order_line_id为空；原INT行标识可与同订单D365、公司001下的原/正式inventTransId逐一核对，候选对应正式第1行24、第2行16。11台旧设备shipment_record_id非空却指向不存在的旧记录，29台引用一致。仅形成只读候选，未把候选称为正式Owner预览、执行或迁入。
- Boundary: 分页及显示修复无业务写入、DDL、重启；引用补正必须经迁移/同步及Owner，保持原字段、历史证据和后续变化保护。已批准范围和105条明细不重复裁决；显示问题已关闭，不以它阻断独立的引用补正。
- Decision owner: 需求方。


### 2026-10-10 软件日志正式材料依赖：V398

RESOLVED（2026-10-11）：用户批准仅应用既有V398的version BIGINT NOT NULL DEFAULT 0。Compose/Flyway成功执行，原35列及236条材料逐值一致，369条既有回执保持，新增version全为0。完整方案及实际核验：docs/generated/2026-10-10-software-log-material-schema-decision.md。

## 2026-10-11：旧软件事件整表处理

状态：RESOLVED / LOCAL_VERIFIED。635条旧DPPMS事件全部已经由正式历史承接，其中590条与自身不可变迁入材料的版本字段不一致。用户批准限定撤回635条，明确保留custom_info，仅清理source_log_key/source_data_state。635条已通过迁移入口及AST Owner撤回，最终30列保留custom_info；当前主档、原来源、不可变证据、旧回执及绑定保持。44项Owner测试、13项带登录上下文MySQL测试、完整包及真实页面读回通过；清理后5次撤回重放与5次原承接重放通过，659×31＋5272扩展及6×11批次字段独立核验一致。完整32列与实际结果见[整表裁决](../generated/2026-10-11-dppms-obsolete-software-event-decision.md)。生产历史基线对齐仍未完成。

## 2026-10-11：ast_device_warranty_record整表收敛

状态：BLOCKED_BY_SPEC / WHOLE_TABLE_DECISION_PENDING。已重新核对既有19列、V4追加31列、全149条记录、当前31条投影及完整原36列118条材料。代理商和syncTime均通过精确成功绑定/发货身份匹配已有正式字段118/118，消除此前候选歧义。建议19物理列保持，两个独立原区间日期用正式DATETIME扩展；两个计算基准不明确的缓存日差仅提出不迁入候选，未经用户批准不删除。等级/旧合同类型名称建议真实原码/名称复用既有字典。完整50列、原36列、各撤回原因及保护条件见[整表方案](../generated/2026-10-11-dppms-warranty-record-table-decision.md)。没有代码、DDL或业务写入，不阻断已批准装箱单等独立工作。

2026-10-11装箱单已批准整改执行：com_shipment_package保留19既有列＋carrier_code，共20列，撤回source_contract_key/source_carrier_name。原表19×8字段、正式合同关系/承运商及全156368行20个保留字段/审计核验一致；10个项目经既有迁移及COM Owner核验，清理后10次重放通过。没有覆盖正式业务值，原索引/Flyway历史保持。详见[整表执行结果](../generated/2026-10-09-dppms-shipment-package-table-decision.md)。

## 2026-10-11：项目阶段读取的来源依赖交接

状态：HANDOFF_PENDING，既有规则含义已由来源任务正式确认，不属于新业务语义猜测。项目992203060190的有效计划使用PROJECT_CONTACT_EXISTS，但本任务包缺少主工作区已实现的联系人规则增量，阶段列表因unregistered predicate失败；正确实例接口正常。未改冻结计划、状态或校验。具体最小接收范围及验证见[依赖交接方案](../generated/2026-10-11-project-stage-contact-dependency-handoff.md)。来源代码未提交，按npdms-change-delivery技能等待正式交接或用户批准本次选择性接收；此前日志特例不自动扩大。不阻断其他已批准迁移，保修整表问题仍独立待裁决。

2026-10-11联系人交接裁决：用户明确授权按清单选择性复用，覆盖此次先合master顺序，HANDOFF_AUTHORIZED。17个最小文件已接入；来源安装功能的无关增量排除。92项联系人事实、权限边界、事务回滚、发布及计划规则测试通过，待完整打包及真实阶段读取验收；冻结计划与来源工作区不修改。

2026-10-11维保整表裁决：用户明确维保信息由发货和保修期限计算，不独立同步，并进一步选择“现存记录及结构暂保留”。此前原区间扩展及149行结构整理建议停止，不删除118派生记录、不清理31镜像、不创建扩展。新迁移将原view_warranty材料列为DERIVED_WARRANTY_NOT_INDEPENDENTLY_MIGRATED，业务承接仅通过正式计算输入；既有成功回执、绑定、149条历史和31条当前投影保持。旧视图输入中的独立服务权利覆盖仍需原表整表方案，不能用计算结果不迁替代输入承接。完整36列清单及限定撤回只读预览保留于上述整表文档。

### DPPMS-LICENSE-RECORD-20261011 — 许可历史正式实体整表结构

2026-10-17独立P37空码裁决已明确：用户允许按原始空字符串认证加密保留原ID472122并登记问题，不填占位、不跳过记录。仅新独立P37使用该处理，NULL仍拒绝，原任务与原试点不变；按既有整表475468条范围完成真实预览，不执行迁移、不修改来源、保持自动迁移关闭、不操作59191。此前空码阻断已由本次明确裁决解决，不再列作待确认；实测进展见[P37记录](../generated/2026-10-13-dppms-migration-mapping/P37-direct-write.md)。

状态：WHOLE_TABLE_APPROVED / IMPLEMENTATION_IN_PROGRESS。用户明确回复“16列及上述处理整表批准”，批准ast_device_license_record及8原列完整映射，见[整表方案](../generated/2026-10-11-dppms-license-record-table-decision.md)。AST Owner代码、受控加密、迁移/同步入口和安全查询正在验证，尚未执行真实许可迁入。旧缓存刷新会清空重建自增ID，本轮历史迁入，持续刷新身份另行裁决；不据旧状态激活许可、不创建虚假对象，缺少当前设备/合同关系保留NULL及原业务值并登记。

密钥配置：此前私有DPAPI文件在当前Windows上下文通过Python及.NET均无法解封。没有覆盖或替换旧文件，没有读取真实许可码。已向需求方询问首次承接是否生成独立受控密钥，或指定原密钥的可用配置；真实加密采集和迁入等待答复。

实施进展：20项定向Java、4项真实MySQL临时表及4项Python测试通过，完整包完成；通过Compose/Flyway应用V433（V426已有其他业务回执，未使用），实际ast_device_license_record只有批准16列且0行，既有业务及回执保持。后端尚未切换，许可码未采集；真实迁入及浏览器验收仍等待密钥答复，不标为完成。

2026-10-11设备引用与联系人依赖关闭：上述050/051的当前批次引用补正已实际完成，40条发货order_line_id和11台失效shipment_record_id通过迁移及Owner纠正，一次NEW、两次REPLAY_COMPLETED，其他字段及审计保护；范围页导航携带真实项目及两种版本，真实浏览器读取2条范围4/16、false、ACTIVE、版本1。联系人17文件已完整打包部署并通过真实阶段读取，S0 DONE、S1 ACTIVE、联系人任务DONE，两条联系人及冻结计划保持。此前“REFERENCE_CORRECTION_PENDING”和“待完整打包及真实阶段读取”作为实施前记录保留，当前本批均LOCAL_VERIFIED；全历史身份与生产基线仍未完成。

2026-10-11许可查询部署：最新21项定向测试通过，后端59282已切换测试过的许可查询包，licenseQueryOnly=true。项目992203060189真实浏览器进入许可历史，接口total=0、空列表正常，正式表仍0行；此前“后端尚未切换”是部署前记录，现已被本段更新。没有配置密钥、读取许可码或执行许可迁入，153条带数据验收继续等待密钥配置回复。

2026-10-11生产迁移链只读校验：完整SQL/migrations的Compose/Flyway validate实际失败，15项校验和差异、9项目录内版本未执行，35条回执脚本名不在当前目录（含重复/改名，不能视为35个缺失版本）。全回执保持，没有repair/migrate/clean或业务写入；[完整分类及对齐边界](../generated/2026-10-11-dppms-production-migration-baseline-readonly.md)。单项V433成功不代表生产基线完成；先核对执行原文、结构及交接，不直接覆盖已裁决SQL或修回执。

2026-10-11合同关系独立核查：已核实pm_project_contract全部7列、86780条及本批10条，既有com_project_contract_relation实际17列、10条。原组为拆分/合并共享合同的关联键；当前关系的管理员及2026迁入审计未承接原历史。全量有34组重复（16组原审计不同）、缺组8条、缺成员9条，未去重或挂靠。仓库未跟踪ProjectContractHistoryDO不对应实际业务表，不能当作已有承接；现有项目组合用途也不能猜配。状态WHOLE_TABLE_PLAN_PENDING，缺项承接及审计转换在整表裁决前BLOCKED_BY_SPEC；[原字段、17既有列及明确边界](../generated/2026-10-11-dppms-contract-relation-original-audit.md)。未新增结构或写入，不阻断独立已批准工作，许可继续等待密钥答复。

2026-10-12合同关系整表提案：实际正式全表24条、本批10条；复用全部17列、新增表列/扩展0，原pm_project_contract全部7列逐项映射。用户已裁决“整表按上述处理（建议）”：原NULL创建/更新人保持NULL、原NULL更新时间用原创建时间，允许放宽两个既有审计人员非空约束，本批RELATED/ACTIVE及原创建时间作为effective_from，当前授权、外键、日期与唯一键保持。WHOLE_TABLE_APPROVED / IMPLEMENTATION_IN_PROGRESS；本批10条具体纠正另交受保护预览，尚未写入。旧组1941个名称均为“总代借货项目合同合并”；mergeBranchMark为拆分名称备注，不是状态。两个原组表全部17字段已列明，剩余业务属性/各自审计、34组重复及-his失效关系继续BLOCKED_BY_SPEC，不默认排除、不将技术桥当作完整业务承接。整表方案及本批日期规则见[17列提案](../generated/2026-10-12-dppms-project-contract-relation-table-proposal.md)。

2026-10-11项目人员独立核查：原15列435342条，本批70条；正式成员29列7条、项目联系人24列3条及主档/历史均已核实。53条有账号候选，8条销售（六个账号原t/fnd均不存在）及9条空编码最终客户人员无账号；不伪造账号、不将来源1/2当内部/外部、不调用当前指派或首联系人动作。联系方式、登记来源、联系人区间和原NULL时间的整表承接方式尚未明确，BLOCKED_BY_SPEC / WHOLE_TABLE_PLAN_PENDING；[完整15原字段、29既有列、真实缺口与Owner边界](../generated/2026-10-11-dppms-project-member-original-audit.md)。当前成员、三条联系人、状态和权限均保持，没有新表列或业务写入；许可仍等待密钥答复。

2026-10-11许可密钥与本批承接关闭：用户批准独立受控密钥、旧文件保留；DPAPI CurrentUser及限定ACL已应用，59282加载后通过迁移功能/AST Owner承接153条×16列，10 NEW及10 REPLAY_COMPLETED、原8列内存密文核验、受保护数据和来源不变。真实项目189许可页两页各20条、总40条及安全详情已验收。状态PILOT_LOCAL_VERIFIED，原0行/等待密钥是此前记录；全历史475468条、缓存刷新身份、生产Flyway基线及部署密钥保管恢复尚未完成，不宣称生产就绪。完整证据及缺关联登记见许可整表方案和任务私有license-pilot-final-proof-20261011.json。

2026-10-12项目合同关系限定纠正关闭：用户另行批准仅10条按原表、办理回执、绑定、项目版本及全17列保护条件纠正。任务2109363204544983041，执行2109365741029355522 SUCCESS/UPDATED10，重放2109365797644070913 SUCCESS/UNCHANGED10；五项历史字段及版本准确，另14条完整关系、原26字段、项目版本和10条不可变办理回执保持。17列无新增，仅两审计列允许原NULL；V437遗漏原注释已用V438恢复，已执行回执不改写。19项后端/真实MySQL、7项展示、完整打包和真实浏览器通过，本批PILOT_LOCAL_VERIFIED。34组全历史重复、-his失效桥和桥主体自身属性仍BLOCKED_BY_SPEC，生产基线未完成；[具体执行证据](../generated/2026-10-12-dppms-project-contract-relation-correction-decision.md)。

2026-10-12字典项整表补裁决：用户明确system_dict_data“保留既有结构，我指定缺项处理”。保持既有14列，停止attribute1/effective_from/effective_to三个物理新增候选；109条非空basicDataAttri1及两个原有效期未获承接或排除裁决，继续BLOCKED_BY_SPEC。已同表补问既有承接点或明确排除、33条NULL排序初始化、129条NULL创建时间/NULL更新时间处理及原ID114无业务值排除，等待回复，不新增字段、不写入字典项、不以快照当业务承接；[14列和完整12原字段](../generated/2026-10-12-dppms-system-dictionary-table-proposal.md)。类型表80条已独立承接并重放验证，不受字典项等待影响。

2026-10-12字典项剩余裁决解决：用户明确“三属性不迁入，其余按建议”，并批准仅ID542创建人system保留原编码至正式creator。system_dict_data保持14列，无新增表/列/扩展；basicDataAttri1/effectiveFrom/effectiveTo不迁入，逐列记“既有结构无同义字段，按用户裁决不承接”，原证据不当业务承接。空ID114明确排除；其余531条按原排序、键值、标签及审计规划，32个NULL排序取0、128个NULL创建时间取实际迁入时间，原NULL更新日期取创建时间，NULL人员保持；7条已结束有效期初始化停用，旧入口不执行。WHOLE_TABLE_APPROVED / IMPLEMENTATION_IN_PROGRESS，真实原12列及正式14列已重新读取，现1575条不接管；上述等待裁决是此前状态，当前进入实现及验证。

2026-10-12字典项执行关闭：任务2109371486114320385，预览2109371486424698881，执行2109371785830895618 SUCCESS/CREATED531/SKIPPED1，重放2109371924867878913 SUCCESS/UNCHANGED531/SKIPPED1。原1575条14列、类型381条11列及原532条12列保持，正式总2106条；ID542原system准确保存creator，原NULL人员和历史日期保持，128条初始化审计重放不改变。14项后端/真实MySQL、9项展示、41模块打包及真实字典页和迁移字段对照通过，无字典DDL。状态LOCAL_VERIFIED；[完整整表映射及执行证据](../generated/2026-10-12-dppms-system-dictionary-table-proposal.md)。同期另一个项目新增两条正常成员有独立业务审计，原145条保持，不撤回并行操作。生产基线和其余历史领域仍未完成。

2026-10-12主附设备关系具体待裁决：65046条原8列、现19列、绑定及成功回执的逐键预览已提交需求方，仅拟恢复原创建/更新时间并推进版本，操作者及关系业务值保持；逐块全字段/原表/回执/绑定保护，任何改变阻断回滚，不改旧回执。[具体纠正清单](../generated/2026-10-12-dppms-device-relationship-audit-correction-decision.md)。360条两侧SN及产品相同的自关联另问是否不迁入双设备关系并列明原因，未裁决前不跳过、不放宽自关联禁止。USER_DECISION_PENDING / BLOCKED_BY_SPEC，仅继续独立已授权工作。


## 2026-10-13 迁移映射续查登记

- Status: READ_ONLY_REVIEW；已确认执行/展示缺陷与尚缺业务映射分别登记，不提升为迁入完成或新结构批准。
- Evidence: [迁移映射续查结果](../generated/2026-10-13-dppms-migration-mapping/continued-review.md)。67个来源对象/1031列结构一致，446处显式物理引用存在；没有执行迁移、重放、DDL、授权或服务切换。
- 已确认缺陷：55条项目相关方四项历史审计未准确承接；正常详情返回20/30相关方而正常展示不匹配。原字典07明确10服务渠道、20出货渠道、30代理商，不与最终客户或账号角色混用。正常页面遗漏已由真实浏览器取证。原工程事件、过程状态、SMS合同回款信息、维保计算输入/人工变更仍按续查报告登记，不能通过复用当前流程完成事实或启用未批准原型来消除缺项。
- DPPMS-INSTALLATION-042补充，Status: BLOCKED_BY_SPEC（沿既有全历史串货未决范围）：旧代码确认串货1转出、0转入；30行展开15组，14组双端一致。原1160999实际projectId=669940，与同组声明的chProjectId=66994不一致。准确关联依据尚未确定，不猜测修正项目键、扩大现有安装Owner支持、改当前归属或自行排除该原行；原948普通安装批准范围保持。
- Q-LEGACY-ORDER-LINE-MISSING-TARGETS-20261013-025，Status: BLOCKED_BY_SPEC（引用已有整表异常清单）：本轮实时核对任务2101587859478949890的378976条LINE绑定，236259条指向不存在的正式行，142717条为同租户未删除有效目标引用。原异常登记及证据见[订单行缺项清单](../generated/2026-10-13-dppms-order-line-gaps.json)。本轮不重建目标、重绑或删除绑定；已执行142697条UNCHANGED不代表此缺口解决。
- 本条仅登记复核事实和未决点。19领域暂缓、派生维保不独立同步及现存维保记录/结构暂保留、已批准字段映射和具体纠正范围保持，不重复提出已批准裁决。后续写入仍沿已授权范围和现有Owner保护执行。


## 2026-10-13 剩余整表承接最终裁决与执行

- 需求方批准相关方正式承接、发货状态与执行状态使用正式项目实体扩展；追加批准两个状态更新时间属性及相关方两个既有审计人员字段可空，原NULL更新时间用原创建时间。
- 需求方要求合同回款信息完成迁移/同步，复用com_contract_receivable既有52列；原39列完整映射。原合同号NULL的7条按明确裁决过滤并逐ID列明，不创建新表列。
- 串货关系本轮不迁入；工程事件暂不迁入；审批过程缓存不迁入。已裁决排除不再当作漏迁或重复询问。
- 原订单行01的236259条无目标绑定全部清理，原01来源中已经映射001的123160有效绑定保护。最终原订单任务共有142717条有效绑定、缺失目标0；原02的8条有效绑定保持。Q-LEGACY-ORDER-LINE-MISSING-TARGETS-20261013-025此前将该批当作业务缺失的判断已纠正，这236259条不是待恢复业务；其他独立来源/父单冲突仍沿原登记。
- 相关方55条原11字段及正式20列独立复核无差异；46个NULL创建人、23个NULL更新人按原值保留。NULL更新人的框架回填缺陷已由显式审计参数纠正，原中间运行与回执保持。
- 回款10条原39字段及正式52列独立复核无差异；10项目四状态属性无差异。最终重放分别UNCHANGED=10、65，正常资料区真实浏览器验收通过。
- 正式任务2109967339937804289、2109968219214897153、2109968221270106114均复用可编辑整表SQL，原十项目/合同只在验证参数中限定，不新增辅助承接机制。
- 状态：APPROVED_SCOPE_LOCAL_VERIFIED。新增物理业务表、列均为0；V461仅调整已批准既有审计可空约束。已有Flyway历史差异及449～460基线整理未解决，6570越界项目不接管；不以本轮结果宣称全历史已写入或生产基线就绪。
- 完整59原字段、正式承接及排除理由、真实运行与验证证据：[剩余业务整表承接](../generated/2026-10-13-dppms-migration-mapping/remaining-carry-implementation.md)。

## 2026-10-13 续保原记录与维保计算整表复核

- 单位裁决已明确：用户确认`fb_service.warranty`为年数，转月数乘12；实际`begin_date/end_date`参与计算，月数只参考。已纠正F-AST-001对应错误单位，不重算原起止日期。
- 原库实际定义证明`view_warranty`为每日固化结果，`dailyWarrantyInfoTable`从`view_warranty_source`填表、补充目录/商务名称并交换表名。计算结果不独立同步的既有裁决保持，不能据此排除462,213条原续保。
- `Q-DPPMS-RENEWAL-RECORD-20261013`，状态`BLOCKED_BY_SPEC / WHOLE_TABLE_DECISION_PENDING`：建议复用既有`ast_device_warranty_record`全部50列，仅新增`is_latest BIT NULL`承接原最新选择标记；取消记录表的设备主档强制外键，原SN及15条空串独立保留；空参考年数转NULL，无原审计用实际迁入人员/时间。完整原10列、目标50列、唯一候选列及约束均见[整表复核](../generated/2026-10-13-dppms-renewal-and-warranty-table-proposal.md)。未经用户确认不实施结构和写入。
- 当前`ast_device_warranty`21列可复用为计算结果，不新增表列；项目按原合同/发货/SN关系展示，不以当前设备唯一主档解析为前置。现存149条记录及31条当前投影保持保护，本次未覆盖；V414空原型不计正式承接。
- 原等级目录7列及12条的正式目录接入仍未完成。`warranty_change_logs`55条的`updateType/warrantyTimes`含义未核实，不能把后者推断为月数或沿续保年数转换；全部8原列列入同一复核，不丢弃、不以原型代替正式承接。这两项保持`BLOCKED_BY_SPEC`，不阻断续保10列的独立规划。

2026-10-13同项最新裁决：用户明确“另建续保表，重新提交方案”。因此撤回上述复用`ast_device_warranty_record`的新续保方案，既有50列、149条及设备外键保持，不新增`is_latest`到旧表。修订候选为独立正式`ast_device_warranty_renewal_record`，17列及全部10原字段、来源复合身份、索引/日期/空SN约束在原复核文件中完整列示；不加来源镜像，不把原V414空原型视为获准承接。`Q-DPPMS-RENEWAL-RECORD-20261013`当前状态为`NEW_TABLE_DESIGN_REQUESTED / WHOLE_TABLE_DECISION_PENDING`。用户只确定独立实体方向，尚未批准这17列或实际写入。

2026-10-13续保整表裁决完成：用户明确“17列及处理整表批准”。`ast_device_warranty_renewal_record`按同表方案建立，原10列完整映射，复合来源身份使用既有INT绑定；无主档强制外键，空SN保存但不参与关联，原年数乘12且实际日期保持，原无审计采用实际迁入审计。旧维保记录50列/149条及当前31条保持。状态更新为`WHOLE_TABLE_APPROVED / IMPLEMENTATION_IN_PROGRESS`，不重复申请已批准结构；等级目录有效期和人工变更日志语义仍按各自问题待明确，不扩大本次数据范围。


### Q-DPPMS-RENEWAL-MULTI-CONTRACT-20261013 — 十项目交叉验证及迁移授权

- Status: RESOLVED（当前会话明确批准）。
- Decision: 再选10个用服售后项目，每个均有真实续保记录及多个合同关系；复用现有参数化整表任务验证。选中原ID为31339、44338、26040、46781、9957、25501、32945、20072、13383、29284。
- Authorization: 主档预览2110015502551789569实际因办事处范围被拒。需求方批准平台超级管理员通过迁移Owner，持既有项目创建与迁移执行权限，在本次任务限定的10项目范围内跨办事处迁入；不新增账号的办事处管理关系。普通创建、租户、来源身份、字段及并发保护保持。
- Boundary: 新规则仅用于正式迁移Owner；没有授权执行整表全量写入。原10项目和现存受保护维保事实不因选样回写。

### Q-DPPMS-RENEWAL-TEN-CONTRACT-DEPENDENCIES-20261013 — 新样本的四份合同主档

- Status: RESOLVED（当前会话逐项批准）。
- Evidence: 31020220620A23、31120220620A37在SAP和CRM回款头使用C00000013，在D365使用C00013；正式客户目录没有可证明两码同一身份的绑定。31020181224A12、31020211228A12只有旧项目合同关系，没有对应ERP订单、发货合同或CRM回款头。
- Decision: 两份客户编码冲突合同统一使用用户指定的C00013；各ERP订单原客户编码保持。另两份按用户“创建主档”裁决，仅承接从旧关系和项目公司精确取得的公司、合同号，其他主档业务值保持NULL。
- Execution: 沿既有合同迁移任务2107037031219654657的CONTRACT来源及正式com_contract映射；客户例外和允许补建的合同号均为任务参数。补建关系来源用独立带类型的来源键和DPPMS类别，不冒充ERP订单；采用既有合同目标默认PENDING_AUTHORITY，不新增表列、不生成合同批准或履约完成事实。已建立的来源身份及旧绑定保持。
- Boundary: 本次仅四份合同。其他缺来源或客户冲突不因本次裁决自动放行。关系的原审计仍由既有项目合同关系任务承接，不用关系创建时间冒充合同签订时间。


### Q-DPPMS-P04-UNMIGRATED-DEPARTMENT-20261016 — 合规账号引用 P01 范围外部门

- Status: RESOLVED / IMPLEMENTATION_CORRECTION_REQUIRED。
- Evidence: P04 直写任务 2110802629025939458 的真实预览 2110804955614785538 读取 5,015 条后，被本地组织身份校验阻断。合规账号任职资料引用 ehr_department.depID=391，编码 310500001，名称安全服务部，depGrade=3，compID=1，adminID=88。
- Conflict: 已审核 P01 基础部门限定 depGrade<=2 且编码长度为6，并仅补入指定部门；391 不在本轮正式部门与别名范围。其他所需公司别名和20项部门别名均已存在。
- Pending decision: 确定该任职资料应保留待处理、另行审核补入该部门，或明确批准映射到某一已迁入部门。未裁决前，不创建额外部门，不推断上级归属，不绕过账号归属校验。
- Boundary: 本次仅预览，未迁入 P04。原任务与绑定保持；唯一账号行变化来自核查期间 API 登录审计的 login_date/update_time，不是迁移写入。其他业务行未变。59191 未操作。

- 2026-10-16当前会话裁决：用户明确将账号原部门391归属到已迁入上级部门88。已仅修改新P04直写任务配置为显式ENUM映射，原始来源证据仍保留391，任务版本1；预览2110806235645063169进入范围阶段后被缺失范围部门阻断。
- 复核纠正：用户要求先查既有裁决。生产任务文档 `docs/generated/2026-10-13-dppms-migration-mapping/production-tasks/tasks/P02.md` 已明确批准29码（含163130），并注明不得只复制单码参数。本轮P01直写模板漏了28码，属于实施遗漏，不能继续作为未裁决问题请求用户放弃范围。29码及父级、两项EHR停用例外已进入直写修复；真实源只读验证返回29条且原生主键唯一。既有P01正式运行与历史不改写，当前仅允许预览，未补写部门。

### Q-BASE-P04-TYPED-EMPLOYMENT-20261018 — 新独立账号任务的任职关系承载

- Status: CORRECTED / EXISTING_APPROVAL_LOCATED。下列 Evidence/Missing contract/Implementation boundary 保留为首次判断历史，已由本条末尾复核纠正取代，不再作为当前阻断。
- Evidence: 新任务 2111504175751917570 的真实只读预检确认选中账号有330条 `t_user_info` 任职资料。旧 `DppmsUserCustomInfo.assemble` 将公司、部门、岗位、员工及汇报人员引用等组装成 `custom_info.employmentAssignments`；当前用户提供的 AGENTS 规则禁止用 JSON 保存固定业务关系。
- Missing contract: 尚未发现获批准且能完整承接这些任职关系的具名列或关系表。`system_user_post` 仅承接用户岗位，`system_user_company_department_scope` 是授权范围，不能冒充原始任职及汇报关系历史。需要正式的承载模型/映射以同时满足原字段保留与当前持久化规则；本轮不自行造表、丢关系或写回旧 JSON。
- Implementation boundary: `P04AccountSyncTask` 目前仅完成独立来源预检和明确阻断，不能记为账号导入实现完成。未读取源密码、未调用原任务/Owner、未写账号或权限、未执行迁移。不改变既定账号范围、391→88等既有裁决，不重问这些事项。

- 2026-10-18复核纠正：已追溯到 `Q-LEGACY-IDENTITY-USERS-TABLE-20261009-008` 两列及账号属性最终裁决，以及 `Q-LEGACY-IDENTITY-USERS-EMPLOYMENT-20261009-011`。用户已明确批准历史任职资料由 `system_users.custom_info.employmentAssignments` 承接；正式 `employee_no/custom_info` 列当前均存在。首次未追溯该具体裁决便要求新建任职表，判断不充分，现撤回整体规格阻断。新 BASE_P04 按既有具体裁决独立实现账号、历史任职、角色、成员关系及公司部门范围；不把任职历史解释为当前EHR任职、审批链或项目授权，不调用旧任务或Owner。

### Q-BASE-P04-SYSTEM-PRESET-20261018 — 原系统预设用户标志

- Status: USER_RULE_CONFIRMED / PREVIEW_VERIFIED。
- User decision: 本轮用户明确“`isSysUser` 为系统预设用户”。结合既有账号扩展属性承载批准，在新 BASE_P04 将原 `t_user.isSysUser` 原值写入 `system_users.custom_info.systemPresetUser`；NULL、0及其他原数值不擅自改写，不根据该标志转换账号范围或授予管理员/超级管理员权限。
- Boundary: 只修改新套件映射和本轮决定记录；旧字段清单仍保留其历史日期的未决事实。该属性不新增认证能力，不迁入源密码，不运行真实同步或迁移。真实预览结果在 `docs/generated/standalone-base-sync-20261017.md` 本轮复核中登记。

- 2026-10-18真实预览：新P04 Run 2111517837875126273 为PREVIEW_READY；1176个逻辑账号、330条任职进入独立规划，329个入选t_user的isSysUser原值均为0。四步read12632为候选行计数，未写入账号、角色、关系或来源绑定。执行仍受现有多来源目标唯一索引结构前置约束，本轮不执行迁移或真实同步。

### Q-BASE-P22-MISSING-DEPARTMENT-20261019 — 原部门空值登记待回填

- Status: ADJUDICATED。
- User decision: 对新P22的14293条正数量原部门编码缺项，用户明确选择“保留空值、登记缺项，继续预览（建议）”。原查询直接带入原项目`pm_project.column001`，NULL/空字符串保留原样；`department_id=-1`，无法派生的部门名称/版本保持待定，登记缺项且继续预览，不填占位部门、不跳过这些正数量记录。
- Boundary: 仅独立新BASE_P22。非空编码仍必须匹配同租户精确部门码；部门目录变更、已有身份和不可变来源保护保留。已有DPPMS-SCOPE-039零数量待处理裁决继续适用。已知部门仍按当前独立运行绑定统一后置回填，空编码保持待定。正式结构不支持待定值时仍阻止真实写入；本次不执行DDL、迁移、真实同步或原任务操作，不操作59191。
- 2026-10-22执行闭合：用户后续明确允许仅在`172.17.90.178:13306/npdms`执行B稿并完成新P22真实预览、执行及重放。B稿一条ALTER已VERIFIED，仅六列允许空值并移除`fk_scope_order_line`；上述2026-10-19预览边界作为历史保留。修复包三阶段Run依次为`2113199680318668802`、`2113202165225684994`、`2113209385740992514`，均读取343858；首次成功提交340882条范围及同量明细/绑定，重放340882条业务行未变。部门回填326589、原空部门14293待定，订单回填340580、缺配302保留`-1`，零数量2976沿用原裁决登记。业务/审计摘要及绑定身份/指纹保持，绑定运行元数据正常更新；早前失败尝试完整回滚、回执保留。P33实际继续暂缓，未执行其他DDL、Flyway或其他迁移，未改原任务或操作59191。[批准SQL、三阶段回执及终态保护证据](../generated/dppms-24-tasks-three-implementations-20261019/P22-approved-B-execution-and-replay-20261022.md)。

### Q-BASE-P31-REVERSED-HISTORY-20261019 — 原人员历史区间倒置

- Status: ADJUDICATED / RAW_VALUES_EXECUTION_AND_REPLAY_VERIFIED（2026-10-22）；以下2026-10-19预览限定作为历史决策保留。
- Evidence: 新P31的381195条来源中152条`effectiveTo < effectiveFrom`，角色60有145条、角色30有5条、角色10有2条。其中77条起止在同一日，75条结束日期早于开始日期；所有原日期精度均为秒，不是读取精度丢失。完整原ID、项目、角色和日期见工作树外`base-sync-seven-blockers-followup-20261019/P31-original-interval-audit.json`。
- Existing boundary: 角色60归入项目成员历史的既有批准没有批准改写原有效期；目标`chk_proj_member_dates`仍对全部成员校验结束不早于开始。没有交换日期、补结束时间、跳过来源或修改CHECK。
- Original pending: 首次发现时仅针对日期问题询问预览保留和登记规则，回复前未实施例外；没有重新询问角色60或其他已决规则。
- User decision: 用户选择“仅预览保留原值并登记，继续全量预览（建议）”。仅新BASE_P31预览允许原区间倒置时保留原日期，运行日志逐条登记原ID、原项目及起止日期，回执汇总待处理数量；真实写入仍拒绝，数据库CHECK、审计精度、当前授权和历史保护不变。不把此项扩展到其他任务或日期改写。
- Verification: 新P31 Run `2112123456126668802`全量381195条PREVIEW_READY，计划插入381195、业务写入0；回执`P31_SOURCE_INTERVAL_PENDING:152`。152条日志原ID/项目/日期与冻结来源全部匹配，原库角色和日期再只读核对不变，目标CHECK保持。28项定向检查通过，包含160条问题逐条登记、预览不写入、真实写入失败回滚及精度保护。完整证据见[本轮报告](../generated/dppms-24-tasks-three-implementations-20261019/base-sync-p31-original-interval-preview-20261019.md)。

- 2026-10-22 superseding user decision and verification: 用户后续明确原日期直接承接，并单独批准仅远端npdms移除chk_proj_member_dates；该唯一CHECK例外已执行，381195条真实写入及独立绑定完成，各阶段重放381195条不变，152条原日期/审计/角色逐条回读无差异。本节先前“真实写入仍拒绝”和CHECK保持为当时边界，已由本次明确授权及真实凭据更新。见[本轮闭合报告](../generated/dppms-24-tasks-three-implementations-20261019/P18-P31-raw-source-formal-prerequisites-20261022.md)。

### Q-BASE-VERIFY-CAPACITY-PACKAGE-20261019 — 专用验证库两项结构与三项容量例外

- Status: EXPLICITLY_AUTHORIZED / APPLIED。
- User decision: 用户明确“允许这两项补丁及三项容量调整（建议）”。仅对`npdms_reverify_20261014`按已有V481补`com_shipment_package.shipment_contract_source_key VARCHAR(128) NULL`，按已有V484将`int_base_sync_task.max_rows`的CHECK上限改为5000000；仅新`BASE_P15/P33/P35`的`max_rows`由1000000改为5000000。
- Execution: 2026-10-19 17:34两条ALTER及三项任务容量更新完成；其他列、索引和任务字段保留。结构及任务原值已加密备份，独立审阅SQL、授权记录及执行证明在工作树外`base-sync-seven-blockers-followup-20261019/capacity-and-package-proof.json`。环境维护材料不进入产品同步任务或自动迁移路径。
- Boundary: 本例外不授权其他DDL、Flyway、真实同步、正式父输入创建、原任务操作、测试库写入或59191操作。自动迁移和作业仍关闭。P35重跑2112116707562823682全量1586578条PREVIEW_READY；P15/P33越过结构和容量后暴露的真实缺项另列，不将本例外当作全任务放行。

### Q-BASE-SOURCE-PREVIEW-INPUTS-20261019 — 正式引用与原表预览的依赖区分

- Status: IMPLEMENTATION_CORRECTED / SOURCE_CONFLICTS_REMAIN_OPEN。
- Direction: 用户要求继续核清剩余五项，重点核实P15/P18/P33的正式输入依赖是否必要，允许按已有审核修复实现或配置，不执行迁移或真实同步。沿用原项目ID与原用户名承接、原任务隔离及已有来源冲突裁决；本条不批准新的数据消歧、地址规则或真实写入。
- P15: 正式P24完成是实际写入前置，不是四类原历史及合同来源只读预览前置。预览用三份单表查询按原规则归并合同，不创建正式输入。另核实81547条应收原`dataSource`均为CRM，原P15本地归并额外筛DPPMS是实现缺陷；改为保留同租户、同连接的独立BASE_P24绑定限定，不能跨任务取值。真实源核查发现389组合同仍有客户编码/名称冲突，继续受Q024约束，不取任意客户、不截断或静默排除。
- P18: 17列正式关系仍要求非空合同ID及同租户合同外键，不能以-1回填替代。仅预览可保留全部原关系，登记缺公司及合同引用，不生成目标ID或待插入/未变数。8个原项目48275至48282的公司业务码仍为真实外部缺项；非空公司继续核对当前授权，历史绑定异常仍阻断。
- P33: 正式设备及当前项目选择是写入前置。预览可登记缺设备，正式项目尚未存在时延后当前选择；已存在项目的当前授权/选择和既有安装保护保留。空地址、超过安装列255字符的地址仍受原安装规则未决约束，不能按引用缺失一起放宽；串货已批准暂缓，不重复询问或扩写转移功能。
- Boundary: 仅新BASE套件与真实只读预览；原任务、A/B历史回执、正式业务、来源绑定、自动迁移开关保持。证据与终态见[本轮报告](../generated/dppms-24-tasks-three-implementations-20261019/base-sync-five-input-dependencies-20261019.md)。
- Real verification: P15 Run `2112132307764813825`已越过正式P24前置，实际在发货合同原始键校验失败；原表120639行有1行`contract_id=''`且`contract_code=''`，不是Mapper丢键。不能由P37空许可码裁决推导本项空来源身份可写。三份合同原表查询已单独在真实MySQL5.7读源核查，389组客户冲突保持；该来源核查不是成功的五阶段任务回执。
- Authorization finding: P18 Run `2112132107189002241`仍为`P18_PROJECT_COMPANY_NOT_AUTHORIZED`。进一步按当前API的有效期、状态及租户条件核实：操作者1只有两条`DPTECH-DEMO`公司范围；82381个业务关系中001有80374、002有1804、003有195，合计82373条均未被该范围覆盖，另8条公司缺失。此前只列8条缺公司码不足以解释当前运行；不能自动扩权或在非空公司上跳过授权。需要覆盖001/002/003的既有正式授权上下文或有权主体完成正式授权，不能将本轮预览许可解释成公司授权。
- Source boundary verification: P33 Run `2112132166609707009`仍因原始地址基线失败，缺正式设备的预览处理尚未在该真实运行到达。缺设备、缺合同登记及真实写入继续拒绝的边界由本轮原生检查验证，不将其写成真实全量预览通过。首次三项终态均为FAILED，追加裁决及P15优化后的最新终态以本轮报告为准。
- P15 query correction: 原发货事件的两个关联均命中来源唯一索引，运行采样仍为195.2秒仅发送199787行，线程主要等待源端读取。将包裹3列与合同2列原始投影在步骤开始前各读一次，条码单表流式读取，按精确原键补入原发货时间/合同号；保持NULL关联、大小写及全部原条码范围，不创建正式输入或索引。旧独立预览`2112134682323939330`仅随已批准的59284重载中断，随后经既有恢复API登记`PREVIEW_INTERRUPTED_BEFORE_CURRENT_RUNTIME`并释放占用，不伪造完成回执，不在产品任务中新增维护或恢复代码。合同全量目标校验同时按原batchSize拆批，来源冲突继续阻断。
- P15 type correction: 单表优化后Run `2112137994540634114`已完整读完4283326条发货事件，约255秒；设备关系随后因原VARCHAR创建日期被JDBC直接映射为LocalDateTime而失败。全量65046条均为7位小数、低于毫秒的末4位全0，不是无效日期。新Source先保留原字符串，再精确解析；高于目标DATETIME(3)的有效精度仍拒绝，预览及实际写入均不截断。原更新时间字符串及其他字段不变。原生检查复现旧失败并验证7位无损解析及精度拒绝；最终真实重验及51项有效检查见报告。
- Prior decisions retained: 已核实31020220620A23、31120220620A37的既定客户码归一化后不在当前389组冲突内，不重复提出已决客户码问题。另两份限定原正式Owner补建的历史范围不自动扩张为新任务无来源主档创建。

- Final verification: 合同阶段Run `2112140895640039426`还发现栈溢出；目标500组OR改为等价的同租户复合键IN，保留空集合及锁定条件，并在租户拦截器/512KB栈下核验500组键。最终Run `2112144970750287873`用时327.901秒，四段完整读取120638/156368/4283326/65046，共4625378条；合同实际读入91565原订单、109578发货字段组、81540应收字段组，归并81522组并登记389组来源问题。最终`P15_CONTRACT_CUSTOMER_CONFLICT`，没有再发生日期或栈错误；Run保持FAILED/read=0，业务写入及绑定为0，不伪造完整通过或正式P24输入。
- Protected boundary verified: 当前24项仍19 PREVIEW_READY、5 FAILED；52项相关有效检查通过。原46任务、209运行、211绑定逐字段不变，180项计数/最大ID/原任务版本、33个触发器定义、测试库6734条项目身份保持；新套件非预览运行/绑定/活动运行均0，自动迁移及作业开关关闭，Flyway仍V473。只有59284按既有授权重载，未操作59191。

### Q-BASE-P15-EMPTY-SOURCE-IDENTITY-20261019 — 发货合同空原始身份

- Status: ADJUDICATED / SOURCE_FILTER_IMPLEMENTED。
- Evidence: 新P15发货合同来源`fb_contract`全量120639行，其中1行物理键`contract_id=''`、合同号`contract_code=''`；其余来源键唯一，原保修年限未发现格式/范围异常。实际Run `2112132307764813825`在`P15_RAW_SOURCE_KEY_MISSING`失败，未写业务或绑定。
- Original missing decision: 首次定位时尚无本项空来源身份的承接裁决；现有来源绑定拒绝空白键。原A/B的`invalid-source-key`证据摘要用于记录问题，不是已批准的正式业务身份，不由P37空许可码裁决推导本项规则。
- User decision: 用户明确“记录的`contract_id`和合同号均为空 的直接查询时过滤掉”。仅新P15的`fb_contract`单表来源查询增加两字段同时NULL/空白时的过滤；原始120639行中排除1行，其他120638行仍在范围。只缺其中一项的记录不被该条件排除，身份校验、客户冲突和其余来源范围不变。记录仍保留在源库，不删除数据、不生成占位身份；同一过滤适用于后续发货最小引用投影。
- Boundary: 保留原记录，现有校验不降级，不向真实迁移任务加入环境数据修复。合同389组客户冲突仍按Q024单独处理，P24正式完成仍是实际写入前置；不再把它写为本次原表预览的失败原因。

### 2026-10-19 剩余P10/P14/P18/P33逐项复核

- Status: EXTERNAL_INPUT_OR_NEW_DECISION_REQUIRED；四项真实预览均已重新运行并FAILED，24项当前20 PREVIEW_READY、4 FAILED。新P15按订单类型选客的4706900条PREVIEW_READY回执沿用，不回退或改写旧回执。本轮未发现已有审核内可修复的实现/配置缺陷，未改变产品代码或规则。
- P10: 实际CRM视图59601条，22组44个AccountId对应同码不同名称，35个身份缺名称、4个名称为130/131/131/143字符。ERP列已补，其他映射字段容量/版本/状态无新增问题。需来源Owner给出编码归属、权威名称及完整超长承载；客户名VARCHAR(128) NOT NULL和必填语义不能套用项目名称回退、截断或跳过。
- P14: 169个表头业务键的410条原记录仍仅执行单号不同，Q023正式单值/多关系承载未裁决。40个原订单行的订单号缺失；按同来源/公司/类型/实际执行单查表头，38条无候选，47615和101146分别只有SO079963、SO102972候选，候选不是已证明的原订单身份。需全40原ID的上游身份依据，不按P15客户选取规则任选执行单，不套用此前发货引用的40条纠正。
- P18: 001/002/003正式公司已存在；操作者1及全库19个用户38条当前有效范围均只覆盖DPTECH-DEMO，不存在可直接使用的已授权公司上下文。82373个关系需正式覆盖这三公司的执行上下文；另8个原项目48275～48282的compId本身为NULL，需真实公司归属。这8项在预览登记缺项的已有规则保持，不恢复为预览硬前置，不重新提请裁决。正式合同和多原来源目标索引保持为未来写入前置，不改权限/索引或创建父输入。
- P33: 完整1063717条中，24142条NULL地址、1849条一个空格、11条两个空格；6111条地址超过安装列255字符，最长571。需空地址安装含义/状态及超长完整承载新裁决；AST地址1024字符容量不能自动授权改变安装字段映射。29条串货仍按已决暂缓，不重复询问或扩写；当前36个正式设备SN与原来源匹配0，仅列未来写入前置。
- Real preview: P10 Run 2112165966710439937 / DUPLICATE_CUSTOMER_CODE；P14 Run 2112166044439281665 / P14_DUPLICATE_BUSINESS_KEY_CONFLICT；P18 Run 2112166146755133442 / P18_PROJECT_COMPANY_NOT_AUTHORIZED；P33 Run 2112166199544643585 / P33_SOURCE_BASELINE_INVALID。全部为新BASE预览，未执行原任务。
- Boundary and evidence: 仅恢复未运行的59284既有冻结包，自动迁移及作业关闭；无DDL、源/环境数据修正、授权写入、双线对照、迁移、真实同步或59191操作。180项保护和原46任务/209运行/211绑定逐字段保持，独立绑定及非预览Run为0。完整逐项来源身份、SQL、历史依据和新回执见[四项复核报告](../generated/dppms-24-tasks-three-implementations-20261019/base-sync-four-blockers-audit-20261019.md)。

### 2026-10-19 P10来源修正复核：同码问题已消除

- Status: P10-1 RESOLVED_BY_SOURCE_CORRECTION_VERIFIED；P10-2/P10-3仍待裁决。
- User update: 用户告知“已处理来源异常数据”，未将其解释为同码合并、自动选客户或预览放宽获批。
- Live source: 2026-10-19T21:28:49.049991+08:00同一SQL Server DPtech_v_account当前59579条、同码组0；原22组44个AccountId每组1个仍可见、1个不在当前视图。35个NULL名称（9启用/26停用）及4个130/131/131/143字符名称仍在，需继续逐项确认，不重复问同码事项。
- Boundary: 仅3份来源SELECT及目标列读取；未修改实现、原任务、任务配置、源/目标数据或权限，未执行DDL、迁移、真实同步、真实预览或59191操作；自动迁移配置未变。最近真实失败回执保持，不写成预览通过。
- Evidence: [P10来源修正后的原始数据与待决项](../generated/dppms-24-tasks-three-implementations-20261019/P10-source-correction-recheck-20261019.md)。

### 2026-10-19 P10来源过滤NULL复核：仅剩4条超长名称

- Status: P10-1/P10-2 RESOLVED_BY_SOURCE_CHANGE_VERIFIED；仅P10-3仍待裁决。
- User update: 用户明确“已过滤null值”。本次不将其解释为NULL预览例外获批，不新增任务过滤或占位逻辑。
- Live source: 2026-10-19T21:33:10.415099+08:00同一配置SQL Server DPtech_v_account当前59544条，同码组0、缺名0，原35个NULL AccountId在视图中可见数0；4个名称仍为130/131/131/143字符，正式列及新P10两处长度判定仍为128。
- Pending: 先确认P10-3完整原名的正式承接上限；建议255及对应新P10校验，尚未获批。本次不执行DDL、迁移、真实同步或真实预览，不修改原任务、数据或权限，自动迁移配置未变，不动59191。
- Evidence: [来源NULL过滤后的实查及4条超长名称全文](../generated/dppms-24-tasks-three-implementations-20261019/P10-null-filter-recheck-20261019.md)。

### 2026-10-19 P10名称容量裁决及P14执行单正式承接待决

- P10 decision: 用户选择“完整保留原名，上限调整为255（建议）”，批准255名称承接及相应新BASE_P10两处长度校验。P10-1/2来源问题均已消除，P10业务裁决闭合；本轮只记录规则，正式列及新P10仍128、尚未实施，不执行DDL、Flyway、迁移或真实同步，不记为预览通过。
- P14 fresh evidence: 2026-10-19T21:38:48.422020+08:00仅SELECT再次逐14字段复现169键410条原展开头（165原ERP头/69借货来源），缺失0/额外0；归并361个不同订单/执行单关系，49个原行共享已有关系。37组D365原ERP主号在展开集合内；132组SMS原ERP执行单号均不在借货展开集合，不能统一套用原ERP主号。
- P14 pending proposal: 一个订单头；D365取原主号，SMS无主次依据时主号先NULL；全部执行单用明确正式关联模型承接。最小表级设计与替代方案已列出，尚未获批，不自动新增关系、创建CRM执行单或任选主号。P15选客户规则保持，不扩成执行单主次规则。P14-2缺订单号40条后续单独确认。
- Boundary: 本轮未修改产品实现、任务配置、权限、业务数据、结构或原任务，未运行新真实预览，不动59191，自动迁移配置未变。
- Evidence: [P10裁决](../generated/dppms-24-tasks-three-implementations-20261019/P10-name-capacity-decision-20261019.md)；[P14原值、正式限制和最小关联设计](../generated/dppms-24-tasks-three-implementations-20261019/P14-execution-pending-decision-20261019.md)。

### 2026-10-19 P14决策信息补充：版本键、真实SQL及现有读取影响

- User feedback: 用户要求“给出相关查询sql，表名，以及业务说明，决策信息不足”。此前新增正式关联表及SMS主号NULL的选项未获批准，不据此实施。
- Live SQL evidence: 2026-10-19T21:57:41.495266+08:00在源dppms和npdms_reverify_20261014只读执行9+4条SELECT。确认pm_order_data_from_sap_source是视图，直接读物理表pm_order_data_from_erp_source；按合同JOIN借货来源并依据short差异生成-L/SMS，保持旧过程原条件。169键410原头/361完整关系中103组只有一个忽略版本短号、66组SMS有两个短号，须分别核版本承接及跨短号关系，不能全部套新增表方案。
- Formal constraints: 正式订单业务键不含执行单，execution_no为单值；现有ProjectCommerceMapper.selectExecutionOrders有精确关联头execution_no的分支，头NULL会影响该路径，新建表不会自动改变读取。明细不能完整还原头集合。历史merge_batch/member设计对应表当前专用库不存在，不能当作现成功能。
- Boundary: 本轮仅补决策依据，未改代码、任务、数据、权限、结构或服务，未调用旧过程、迁移、真实同步或新预览，自动迁移配置未变，不动59191。P10规则保持，P14-2缺订单号另行审，不重复问已决事项。
- Evidence: [真实表名、13条已核验SQL、逐例及全部169组、业务含义与读取影响](../generated/dppms-24-tasks-three-implementations-20261019/P14-decision-business-and-sql-20261019.md)。

### 2026-10-19 P14来源边界裁决：直接承接旧系统已拆分订单

- User decision: “数据同步来源不需要你回查总代借货来源，旧系统数据是已经拆分好的订单数据”。新P14业务来源仅pm_order_data_from_erp与pm_order_line_from_erp；不回查借货/未拆分ERP，不重做拆分或以其原主号覆盖已拆分值。
- Correction: 此前上游与短号追溯是额外核查，不是任务原有来源；撤回基于它们的主号重选及外部前置要求，103/66历史分析不再作为同步输入或预览门槛。新增关系表与SMS头NULL方案未获批准，不自动实施。
- Remaining: 仅按已拆分原行核清正式订单身份/唯一键与订单行关系承接；正式表不含执行单的业务唯一键保持，不能直接降低冲突保护，也不要求来源重新合并或消歧。P14-2缺订单号另按原行处理，不重复问P10已决规则。
- Verification and boundary: 只读检查P14来源XML，两条select分别只引用pm_order_data_from_erp及pm_order_line_from_erp，均无借货/未拆分ERP引用；本次未查源数据或修改产品、原任务、配置、权限、数据及结构。未执行迁移、真实同步或新预览，自动迁移配置未变，59191未操作。
- Evidence: [当前来源及已撤回的上游方案说明](../generated/dppms-24-tasks-three-implementations-20261019/P14-decision-business-and-sql-20261019.md)；[仅两张已拆分原表的查询](../generated/dppms-24-tasks-three-implementations-20261019/P14-current-split-source-20261019.sql)。

### 2026-10-19 P14最大执行单字段裁决及两表核清

- Status: MAX_EXECUTION_RULE_ADJUDICATED / SQL_VERIFIED / PRODUCT_IMPLEMENTATION_AND_REAL_PREVIEW_PENDING。
- User decision: 用户确认“按 max(orderExecNumber) 取最大执行单号”。同ERP来源、映射后公司、订单类型、订单号内选择最大完整执行单号；明细实际执行单保留原值。只读核查169组410条多执行单头的其他承接字段全部相同；正式表头业务唯一键保持，不要求新增执行单关联表或回查借货来源。此前未批准的上游选主/新增关系表方案不再作为当前建议。
- Read-only verification: 2026-10-19T22:38:15.765124+08:00完成来源8条、专用验证库5条SELECT；91,572条原头对应91,239个订单键，原始记录和逐组最大值见[两表核查材料](../generated/dppms-24-tasks-three-implementations-20261019/P14-split-unique-review-20261019.md)。原正式2头9行保持；无产品代码、任务配置、数据或结构写入，未重跑真实预览。
- Remaining: 40条原明细缺订单号，10组同键异值全部集中于此；尚无本项预览放行或过滤裁决。另1579条按现行订单键缺父头，其中1243条无同号原头、336条同来源同公司同号但类型不一致，继续核清实现匹配与正式关系依据，不全部归为外部缺数据。原先1430条有精确执行单候选及298条无精确候选都可对应批准的一单一头订单键，不再作为多头承载阻断。
- Boundary: 严格隔离原任务；不执行迁移、真实同步或DDL，不改变自动迁移开关，不操作59191。本条只关闭执行单选值裁决，不把SELECT核查写成任务预览通过，也不放宽缺订单号、跨类型或真实父头外键约束。

### 2026-10-19 P14来源订单号修正复核：40条缺项关闭

- Status: P14-2 RESOLVED_BY_SOURCE_CORRECTION_VERIFIED。
- User update and verification: 用户说明“已修正来源异常数据”。2026-10-19T23:09:04.772696+08:00只读实查原40个ID均仍存在，orderNumber均由NULL补齐；本轮读取的其他原字段不变，明细总数380605。全表空订单号0、同键异值组0，不新增NULL过滤或预览例外，不重新询问已消除的问题。
- Remaining parent evidence: 40条均为lineType=1，对应同来源/公司/订单号的头orderType=0；与前336条合计376条。另1243条在这两张原表内没有同来源/公司/订单号头。现行新套件和旧10项目解析均精确匹配类型；正式明细order_type又要求来自真实父头，尚无本批跨类型关系批准依据。数据库FK只限制真实同租户父头，不强制类型相同，不能误称为库结构缺陷。
- Pending handling: 仅新P14预览保留原行并逐条登记父头问题、实际写入拒绝，是待确认建议；不修改类型、不造父头、不用-1冒充订单引用，不虚构目标ID或可写计数。批准MAX(orderExecNumber)与本项来源修正均不等于批准跨类型归父。
- Boundary and evidence: [40条前后原值、当前父关系与查询SQL](../generated/dppms-24-tasks-three-implementations-20261019/P14-source-correction-recheck-20261019.md)。只读来源/结构和代码，无任务/数据/结构/权限/服务写入，未重跑真实预览，自动迁移配置未变，未动59191。

### 2026-10-20 P14头行独立业务键及引用前置纠正

- Status: USER_KEYS_CONFIRMED / IMPLEMENTATION_CORRECTED / REAL_PREVIEW_VERIFIED。
- User correction: 用户明确“这不该是约束项，已经明确的业务关联键”，并再次给出订单头`tenant_id＋source_system＋company_code＋order_type＋order_no`、订单行`tenant_id＋source_system＋company_code＋line_type＋order_no`。头、行分别保留各自类型，明细行号继续区分同组内的原明细；不把行类型改成候选头类型，不折叠不同行号。
- Correction: 前轮将376条类型不同记录要求新的预览放行裁决，属于把关联ID解析变成原表预览硬前置的实施误判；该问题撤回，不再作为BLOCKED_BY_SPEC或要求用户再次批准。新P14头查询仍用order_type，行目标查询改用line_type；缺精确父ID时只读预览保留原行、原类型和数量并逐条登记，不伪造目标ID或计划插入/未变数。实际写入仍校验真实父ID及既有绑定，不改正式外键或索引。
- Existing decisions implemented: 表头执行单取MAX(orderExecNumber)，仅将获批准的执行单差异移出同值冲突判定；其他业务字段冲突仍拒绝。拓展属性暂不承接，已有目标拓展值不覆盖。新来源source_record_key使用BASE_P14独立前缀，原任务代码/查询/绑定不修改。
- Verification so far: 原问题由失败测试真实复现为P14_MANDATORY_ORDER_MISSING；修复后30项相关检查通过（6项本轮MySQL临时表检查、7项原回归、15项隔离、2项Mapper装配）。临时表验证的正式计数/最大ID/原任务版本保持。候选包只含新P14类族及两个XML的12个差异条目。
- Runtime boundary: 按已有授权只重载59284并开始新BASE_P14全量真实预览，自动Flyway/Flowable结构更新、Quartz、Batch及事件驱动保持关闭；不执行迁移或真实同步，不操作原任务，不动59191。终态另随本轮结果补记，不提前宣称预览成功。

- Final P14 verification（2026-10-20）: 新BASE_P14 Run `2112220196368515074`于00:33:23自然完成PREVIEW_READY，读取92890头/387154行，共480044；规划插入478464，1580条仅登记后置引用，未伪造ID或可写计数。当前来源93223头/388207行，空订单号0；1580条原ID、类型、订单号、行号及数量逐条回查差异0。180项保护不变，正式绑定及非预览Run为0，24项最新21 PREVIEW_READY/3 FAILED（其余P10/P18/P33未重跑）。[完整本轮证据](../generated/dppms-24-tasks-three-implementations-20261019/P14-business-keys-preview-20261020.md)。


### Q-BASE-P10-P18-P33-FOLLOWUP-20261020 — 原名容量与未解析安装快照

- Status: P10/P33_SOURCE_PREVIEW_READY / P18_FORMAL_AUTHORIZATION_BLOCKED。
- User decisions: P10完整原名上限255及新任务两处校验的既定裁决已落实，不重复提问。用户本轮明确P33空地址全部过滤、不需要承接；非空安装不创建地址管理业务记录，未解析快照只填既有回显键fallbackLocation；再次明确快照及install_location都承接完整原文，列容量另行处理。不得改为仅快照、短列NULL，不截断，不补占位或解析多行来源。
- Implementation: 仅新BASE_P10/BASE_P33类与XML；P10增加未落实列容量的只读提示及真实执行拒绝。P33 SQL过滤NULL/普通空格，批内标准判空补齐2386条全角空格；保留UNRESOLVED、空地址ID/地址快照，不再调用地址maintain/getAddress。已批准安装时间、状态2、历史保护、当前选择授权和首次当前位置填空边界保持，本轮不执行任何业务写入。超容量及既定暂缓串货仅在预览逐条登记，实际仍拒绝。
- Real verification: 新P10 Run2112228159594336257 PREVIEW_READY/read59544；新P33 Run2112230315961561090 PREVIEW_READY/read1035329，原1063717中按裁决排除28388空地址；6111条超长原ID/长度逐项一致，2条串货维持暂缓。P18 Run2112228249985781762 FAILED/P18_PROJECT_COMPANY_NOT_AUTHORIZED，read0；源审计82676身份/82381业务组不冒充成功回执。
- External facts: 当前19用户、38有效范围仅DPTECH-DEMO，001/002/003共82373组关系缺合法执行上下文；8原项目48275～48282缺公司，正式合同0，既定多来源绑定目标索引容量仍为未来实际写入前置。P10正式name128未执行255单列DDL；P33正式安装位置255，6111原值最长571，容量依用户指示另行处理。正式设备未就绪仅登记缺项，不执行真实同步补输入。
- Validation and boundary: 39项相关检查通过，全角空白修正后17项原生检查再次通过；P08两项无关旧断言失败另记，不扩围。原46任务/209运行/211绑定全部字段摘要及180项保护无差异；仅按已有授权重载59284，自动迁移及作业关闭，无迁移、正式DDL、真实同步、测试库写入、原任务操作或59191操作。[完整证据与逐项处理](../generated/dppms-24-tasks-three-implementations-20261019/base-sync-P10-P18-P33-followup-20261020.md)。

### 2026-10-20 P18公司授权边界纠正与整表来源预览闭合

- Status: IMPLEMENTATION_DEFECT_CORRECTED / P18_SOURCE_PREVIEW_READY。没有新增待裁决业务规则；此前将未覆盖原公司一并视作来源预览必须阻断是实施分析结论，不是用户已裁决的新门槛，现纠正并保留失败历史。
- Formal basis: ADR-0038要求正式COM查询和关系维护使用SYSTEM当前公司范围，普通ContractRelationCommandService及原ProjectContractRelationMigrationService均实施此保护；并非十项目白名单。17列整表审核批准来源承接，同时保留当前授权、外键、日期和唯一键；不能据此生成001/002/003的当前公司权限。
- Code correction: 仅新BASE_P18的createParameters及syncDataInsertBefore。已审核来源预览在公司范围为空或不覆盖已知原公司时登记待授权，不查询该公司的正式合同/关系，不生成目标、合同ID或写入计划；真实执行仍拒绝。所有原项目仍调用现有身份及当前MANAGE核验，既有独立绑定、未知非空源公司及来源/目标历史保护保持，原Owner与原任务未改动。
- Exact gap: actor1两条范围均DPTECH-DEMO；全库19用户/38有效范围同样只覆盖DEMO。001/002/003分别80,374/1,804/195个业务关系待正式授权（合计82,373组、82,668原始身份）；原项目48275～48282共8组缺权威公司事实。com_contract有效行0，多来源独立绑定目标唯一索引仍限制实际执行。P10/P33物理列容量按用户指令另行处理，不运行DDL、不重复询问规则。
- Real verification: 新BASE_P18 Run2112256535977709570于02:56:29自然完成PREVIEW_READY，read82676，inserted/updated/unchanged/deleted均0，登记82,381组正式引用待核验；24项任务最新Run均PREVIEW_READY，仅本项本轮重跑。通过表示来源预览完成，不能当成正式写入就绪。
- Validation and boundary: 修复前失败测试复现P18_PROJECT_COMPANY_NOT_AUTHORIZED，修复后30项定向检查通过。按已有授权仅重载59284，候选仅10个P18类族条目不同，两个XML与其他包条目不变。原46任务/209运行/211绑定全字段摘要和180项数据保护无差异，新绑定/非预览Run为0，无在途任务；自动Flyway/Flowable结构更新和作业关闭。无迁移、DDL、真实同步、公司授权、测试库或59191操作。[代码、正式依据、缺失原记录和实际回执](../generated/dppms-24-tasks-three-implementations-20261019/P18-company-authorization-audit-preview-20261020.md)。

### 2026-10-20 P10/P33单列容量例外已授权并完成

- Status: EXPLICIT_TWO_COLUMN_EXCEPTION_APPLIED / BOTH_REAL_PREVIEWS_READY。用户明确允许仅在npdms_reverify_20261014执行此前两条审阅补丁：cus_customer_master.name扩至255、imp_eng_installation.install_location扩至1024，随后重跑两项真实预览。原文完整承接规则沿用，不新增裁决或同步代码。
- Actual operations: 两条固定ALTER已执行；字符集utf8mb4、排序utf8mb4_unicode_ci、可空/默认/注释保持，完整表定义只有批准的两个列定义变化，索引/外键/CHECK/触发器不改。执行前两表7条客户/34条安装已加密备份并回读，执行后所有字段摘要一致；无其他DDL、Flyway或迁移运行器。
- Real previews: 新BASE_P10 Run2112346849270747138 PREVIEW_READY/read59544，原名称容量告警消失；新BASE_P33 Run2112346937229496321 PREVIEW_READY/read1035329/写入计划0，原安装位置容量告警消失。P33继续登记1035329条正式设备引用待输入及2条既定暂缓串货，不据此推定实际写入就绪，不重复提问。
- Isolation: 原46任务/209运行/211绑定逐字段摘要和180项保护无差异，既有BASE回执保持，仅新增两条preview=true回执；新绑定和非预览Run均0，无在途任务。59284 PID25036保持，未改代码、任务配置或重启服务；自动Flyway/Flowable结构更新及作业关闭，测试库与59191未操作。[两条执行收据、真实终态与保护证明](../generated/dppms-24-tasks-three-implementations-20261019/P10-P33-capacity-applied-preview-20261020.md)。

### 2026-10-20 P33正式设备来源SN及比较边界只读核清

- Status: READ_ONLY_AUDIT_COMPLETE / FORMAL_DEVICE_INPUTS_MISSING。用户要求列来源SN、正式匹配SQL及缺失范围，区分实现和正式输入；两条串货继续暂缓，不授权清洗、建档或真实同步。本条是事实核查，不新增已决规则的确认问题。
- Exact range: 1,035,329条入选安装来源、1,018,498个不同原SN、14,135个原项目。实际P33租户1＋deleted=false＋SN IN原值分2037批查询，正式命中0；删除及跨租户诊断亦0。当前ast_device共39条，其中tenant1未删除36条。设备SN100字符，来源实际12～19，无SN容量问题或当前错过滤的设备。
- Producer facts: 新P15原fb_shipment_barcode有4,283,326条；1,018,466个原SN／1,035,296条安装有原文主SN。目标正式DPPMS发货为0，新P30的PREVIEW_READY/read0带FORMAL_DPPMS_SHIPMENTS_MISSING，不能当成主档已生成。本轮不运行P15/P30或原任务填数。
- Remaining identities: 26个SN带一个尾空格、影响27条，诊断去空格值均在上游主SN存在；另外原SN01100107D184000007仅在内部码出现，对应7个不同主SN；其余5个SN在本次barcode/barcode2/com_barcode均未见原文。完整32个SN与33个原ID见报告，未将“本次未见”推论为所有外部来源不存在。
- Separate implementation boundary: 26对实际尾空格/干净候选，在当前utf8mb4_unicode_ci下SQL相等、原文String键不同。P33 SQL返回d.sn、HashMap按s.barcode查键，未来干净正式设备存在时可能将这27条误报为无设备；当前正式查询实际0条，所以它不是全量缺项的直接原因。应统一既有SN匹配与内存核对语义，保留来源原文及历史保护；本轮只读未改代码，没有自动TRIM、别名归并、替换内部码或选择7个主SN之一。
- Deferred and boundary: sourceId1161000/SN020522970248000018、1161002/SN020522970248000016均在主发货可定位组，维持暂缓，不另加到总量。原46任务/209运行/211绑定及设备39/发货3/安装34的全部字段摘要、180项保护无变化；BASE运行回执无变化，无新预览或在途任务。自动迁移关闭，无DDL/DML、迁移、真实同步、来源修正、服务重启或59191操作。[完整SN、实际SQL、来源例外及只读证明](../generated/dppms-24-tasks-three-implementations-20261019/P33-formal-device-reference-audit-20261020.md)。

### 2026-10-20 24项正式执行前置分类与P33/P22实现缺陷闭合

- Status: IMPLEMENTATION_DEFECTS_CORRECTED / AFFECTED_REAL_PREVIEWS_READY。没有新增业务裁决问题；依已有审核纠正P33原请求SN与正式SN比较边界，以及P22用头类型计算明细绑定键。原SN、行类型、父头类型、来源指纹与当前授权保护保持；两条串货继续暂缓。
- Code and tests: P33返回requested_sn及正式id/sn，Java按原请求键定位并以正式身份核对当前选择；批量VALUES SQL保持单层派生、参数绑定、空集合返回空及实际锁定。P22独立SALES_LINE哈希使用line_type，实际父头关系仍核对order_type。修复前失败已复现，修复后52项定向/原生检查通过；写入及回放只作用单连接TEMPORARY影子表，没有正式后置UPDATE。
- Formal prerequisites: 13项有已识别结构、正式输入、授权或原日期有效性阻断；P30为空输入，其余10项未发现专属阻断。绑定目标唯一索引、P21两列非空及P22即时外键/非空快照属于正式结构兼容；P15/P31/P33/P35缺本套件正式完成输入；P18缺合法公司上下文及8个原公司事实。P14逐条回查1,580原ID，337条仅同号其他类型头、1,243条无同来源/公司/订单号头；原类型差异不阻断预览，真实父关联仍需权威依据。P37两个可空关联不列阻断，不回查旧试点Owner。详见逐项清单，不自动扩写迁移代码。
- 2026-10-22后续终态：上述为2026-10-20前置快照。依后续逐项裁决完成输入、承载及实现修复后，当前23项新独立任务的实际执行与重放已完成，P33实际继续暂缓。新P22结构前置已由用户本次明确批准的B稿一条ALTER补齐，同包三阶段成功；302条订单缺配及14293条原空部门依原裁决承接待填，不扩大匹配规则，也不阻断正常数据。没有运行Flyway或其他迁移，没有改原任务或操作59191。[最新24项矩阵及P22真实闭合](../generated/dppms-24-tasks-three-implementations-20261019/P22-approved-B-execution-and-replay-20261022.md)。
- Real verification: 新P33 Run2112436849851088898 PREVIEW_READY/read1035329，190.174秒；新P22 Run2112437662510071809 PREVIEW_READY/read343858。P33首次修复UNION版真实回执亦保留；最终VALUES版减少约99秒，仍比此前143秒单次结果慢约47秒，性能差异如实记录。当前正式设备全缺，27条尾空格来源成功匹配由真实MySQL夹具证明，不能宣称实际关联已补齐。其余22项沿用当日全量复验，最新24项均PREVIEW_READY，不能等同正式同步就绪。
- Isolation: 原46任务/209运行/211绑定全字段保护保持；169张业务/绑定/原任务表、473张表结构及180项保护无差异，既有143条BASE回执不变，新增3条均preview=true，任务配置不变，无在途任务。仅按已有授权重载59284两次，最终冻结包只有8个独立P33类族/P33与P22 XML条目不同。未执行永久业务DML、DDL、Flyway、迁移、真实同步、授权、来源修正、测试库或59191操作；自动迁移关闭。[24项逐项前置、代码、真实回执和完整保护证明](../generated/dppms-24-tasks-three-implementations-20261019/base-sync-execution-prerequisites-p33-sn-20261020.md)。

## DPPMS 同步来源空主键（2026-10-05 专项）

### Q-SYNC-SOURCEKEY-20261005-001

- Status: RESOLVED（需求方 2026-10-05 当前会话确认异常保留并跳过）
- Area: DPPMS 合同、装箱单与发货记录同步专项；不走工程实施链。
- Question: `fb_contract` 有 1 行 `contract_id` 为空字符串，是否保留该行原始异常证据、跳过业务写入并继续其余数据，或先由来源方修正主键？不得为该行补造业务主键。
- Evidence: 2026-10-05 对已配置 DPPMS 数据源只读查询，`fb_contract` 总数 120,639、NULL 主键 0、空字符串主键 1；`fb_shipment` 总数 156,368、NULL/空主键均为 0。运行 `2107037032293396481` 在 `SHIPMENT_CONTRACT` 来源校验失败，未执行业务写入。
- Resolution: 需求方选择“保留原始异常证据并跳过该行，其余数据继续同步”。原始 `contract_id` 保持为空，仅以原始行内容生成独立证据标识，并走既有来源问题机制；该行不得生成业务记录或目标绑定。
- Blocking scope: 空主键处理策略已解除阻塞；完整任务仍须按实际运行结果区分技术完成与全部业务落库，已有合同客户冲突规则保持生效。
- Decision owner: 需求方。

## 2026-10-06 流程图系统记录与文件要求澄清

### Q-TPL-FLOW-20261006-001 — 新模板中计划、方案与配置 Log 的文件要求

- Status: RESOLVED（2026-10-06 需求方已确认文件要求和配置 Log 最新范围；实现接入缺口另行保留）
- Requirement IDs: F-PROJ-009 配置实例；不修改 PRD、通用模板模型或既有已发布模板。
- Source: `需求/项目交付/项目交付流程 (2).vsdx` 六页及需求方 2026-10-06 当前会话：图中标注可能是系统记录，不应全部解释为文件交付件；重新梳理后新建模板。
- Confirmed: 工程交底书暂不处理；新模板不设置该项交付件或完成门槛。施工计划、实施方案“以系统记录和审批为准，不要求额外上传同名文件”。客户提供方案仍是原业务入口的条件输入。
- Resolution: 配置 Log 是设备日志文件，允许手动上传及设备采集回传多个入口；每次文件记录保留。需求方追加确认按同一设备跨入口取最新文件，“大日志文件需要按设备拆分成对应的小日志关联会对应的设备”。到货签收单和客户签署验收单仍单独识别为文件；审批、系统报告、培训记录和满意度结果保持系统记录/业务结果属性。
- Remaining implementation boundary: 文件业务口径已澄清，不再标为 BLOCKED_BY_SPEC。新草稿 `993009900029` 中联系人、安装、配置、联调、培训完成事实尚未接入；配置日志的多设备拆分、设备关联、跨入口最新文件校验及模板来源接入也未实现。完成规则及日志确认规则明确留空，Compiler 预检阻止发布；这些是实现缺口，不以任意文件上传、任务手工结束或采集技术成功填补。
- Evidence: `docs/generated/2026-10-06-visio-system-record-template/`；记录以本次运行采集的旧模板最新已发布 rev10 为来源，早期 rev9 记录不充当当前草稿真值。
- Implementation follow-up（2026-10-07）: 后续授权的DAC拆分、AST设备关联及跨入口最新文件代码已实施，见`docs/generated/2026-10-06-device-log-archive/README.md`；原文的“未实现”为模板创建时边界。V409迁移、实际MySQL与NPDMS浏览器验收仍未执行，模板日志确认规则及五项Owner完成依据仍未接通，草稿保持未发布。
- Decision owner: 需求方。

## Q-PROJECT66-LOG-DELIVERY-20261016 — 配置与联调日志自动交付归属

- Status: `RESOLVED`
- Area: 项目66真实业务验收，配置调试与业务联调日志自动形成交付件。
- Confirmed: 用户要求通过“上传日志 / 文件历史”上传包含软件版本的日志，并验证自动形成交付件；不能以新增业务记录中上传PDF再手工提交交付件代替。
- Question: 两个入口目前共用项目设备日志档案。同一份日志应仅归集到发起上传的当前任务交付件，还是允许同时归集配置及联调两个交付件？
- Evidence: `ProjectDeliverableRuleService.documentTargets`对`AST.DEVICE_CONFIGURATION_LOG`仅匹配配置调试路由；本项目联调交付件未设置`automaticSources`。通用上传请求目前不区分任务入口。
- Blocking scope: 自动交付归属变更；硬件查看错误和既定闭环契约识别修复独立继续。保留既有记录，不补写完成事实。
- Decision owner: 需求方，已在当前会话提问。
- Decision (2026-10-16): 需求方确认“按当前任务入口区分，仅形成对应交付件”。上传来源必须保留任务归属并由服务端核验；配置日志不同时形成联调交付件，反之亦然。此决定不改写既有不可变日志和批准历史。

## Q-DEVICE-LOG-VERSION-20261007-001 — 日志解析结果关联设备及在网版本来源

- Status: `RESOLVED`
- Requirement IDs: `F-INT-012` 直接日志解析增量、`F-IMP-004` 已授权日志归档增量、`F-AST-001`
- Area: 多设备日志归档、按SN关联结构化解析记录、在网软件版本投影
- Question: 需求方2026-10-07指出多设备上传拆解未做真实验收，解析结果未回传设备序列号且软件版本未更新。是否批准把设备日志增加为独立在网版本来源？可信采集时间缺失或旧于当前版本时是否仅保留解析候选、由人工确认而不自动覆盖？
- Evidence: `DeviceLogParsingService.observed`仅更新PLT技术任务状态并返回结果；`DeviceLogArchiveService`只拆分、关联SN和归档文件；现有`DeviceNetworkVersionService`没有日志解析调用方。`F-AST-001`当前将ITR在网版本列为ITR来源只读消费，不能伪造ITR来源身份。
- Runtime evidence: 当前59191归档latest接口500，日志显示`npdms_domain_test.ast_device_log_file`不存在；既有归档记录要求的V409尚未在本实例生效。真实三命令样本解析出SN `02051250D227000005`和软件版本`B311CM005D025P02PATCH75`，当前调用者可见设备查询及项目1002精确SN查询均为0，不能擅自新建设备或改写其他SN。
- Proposed scope: DAC只负责拆分/解析与证据；AST负责唯一SN、当前租户/项目鉴权、解析事实和当前在网版本；PLT/INT通过稳定API衔接，不跨模块写表。以设备最新日志的有效解析结果为来源，保留历史与幂等；日志接收时间用于排序证据，不冒充设备配置生效时间。
- Blocking scope: 业务来源规则已解决；多设备真实验收仍需原始文件和验收实例信息，不再以缺少设备采集时间阻止最新日志版本投影。
- Decision owner: 需求方
- Resolution: 需求方2026-10-07明确裁决“在网版本就是最新日志解析的结果，并不是来自ITR”。按此修正`F-AST-001`、`F-INT-012`和`F-IMP-004`对应契约并实施；不伪造ITR来源，不将旧日志重开或迟到解析回退成当前版本。多设备真实日志路径和验收实例仍待提供，本裁决本身不代表数据库迁移或真实设备档案已更新。

## Q-FEEDBACK-ACCEPTANCE-20261009-024 — 只上传报告与验收通过的边界

- Status: RESOLVED
- Area: FB-24 初验/终验简化，仅限制依赖该问题的验收完成规则修改。
- Question: 用户已确认“只上传、自动时间/操作人；保存即完成验收”。上传报告不再人工填写结论后，任何报告保存均完成验收，还是不通过报告仍不能完成？验收完成是否继续等同通过并参与后续闭环？
- Evidence: `IndependentAcceptancePolicy.SNAPSHOT`冻结`EFFECTIVE_REPORT_PASS`及`passingConclusion=PASS`；`reportPublished`仅PASS使活动COMPLETED，其余PENDING。`AcceptanceReportCommandService.requireCompleteContent`要求验收时间、结论及验收人。不能默认把未判定报告标PASS，也不能覆盖历史活动的规则快照。
- Decision owner: 需求方
- Current action: 已取得当前会话明确裁决；按新独立报告规则实施，保留不可变报告和历史规则快照。
- Tracking evidence: `.run/test-feedback-20261009/项目管理新系统测试反馈跟踪清单.md`，R2原失败保留，R3仅依据实际浏览器结果更新状态。

- Resolution: 需求方在当前会话确认“上传保存即完成，验收完成不再代表结论通过”。新独立报告采用上传完成规则，不默认PASS；既有不可变报告及冻结历史保留。`F-ACC-001`记录该批准差量，本问题不再阻断实施；运行部署和浏览器验收仍须实际完成。

## Q-FEEDBACK-OVERDUE-DATE-20261009-002 — 超期判断的权威日期映射

- Status: RESOLVED
- Area: FB-02 超期清单的节点范围、日期来源与完成边界。
- Question: 工期超期取工勘的项目结束日期还是已批准工期的完工日期？验收超期取指定阶段/任务的计划验收时间，还是新增项目级计划验收截止日期？
- Evidence: 当前项目主档`ProjectMasterDO.projectEndDate`由工勘提供，SOL施工计划保存版本化工期；`ProjectExecutionNodeDO.acceptanceTime`位于阶段/任务，`ProjectPaymentAcceptanceApiImpl`按合同和显式节点映射刷新。当前项目主档未承载项目级计划验收截止日期，不能自行选某个节点或取最大日期。
- Confirmed rules: 工期及验收分别按计划日期判定；分别在项目完工和终验完成时停止并保留各自完成时的超期情况；未完成另一类仍可出现在当前清单；同时命中显示两个标识。
- Decision owner: 需求方
- Current action: 节点范围、完成与日期边界已逐项确认，按明确口径实施并验证，不改变原有状态机和权限。
- Tracking evidence: `.run/test-feedback-20261009/项目管理新系统测试反馈跟踪清单.md`；本问题未解决前不登记FB-02回归通过。
- Resolution: 需求方2026-10-09确认“只要阶段和任务超期未完成，未验收，就属于超期”“节点完成即停止两类超期”，并要求集成现有阶段状态展示，追加计划结束及计划验收时间，阶段任务推进和施工计划更新同步刷新。当前规则按节点计划日期计算两类超期，完成停止两类，不新增项目级截止字段；同项目其他未完成超期节点仍列出，双标识保留。新完成事实随既有状态更新追加冻结；旧记录不回填或篡改。前述项目完工/终验分别停止的口径由此次明确节点级裁决细化替代；测试与部署仍需真实完成。
- Date boundary: 需求方同日明确“按日期，次日开始超期”，以当前日期零点和节点计划日期比较，截止当日不超期；不能将NULL计划日期推断为超期。


## Q-FEEDBACK-MATERIAL-EXCHANGE-20261009-005 — 换货分类与申请字段简化

- Status: RESOLVED
- Area: FB-05，旧功能范围：现有换货申请表和申请事实记录，不新增审批或CRM调用。
- Question: “产品类型”是否指现有换货类型，申请区简化后分类应如何处理？
- Resolution: 用户澄清“页面上没有这一项”，未授权删除既有子表换货产品选择；针对当前实际存在的“换货类型”，明确“隐藏，保留当前默认分类”。新申请沿用INCOMPATIBLE默认分类，界面不提供修改；历史原分类保留。申请区仅填写原因，物料选择仍单独保留；申请人和申请时间由系统自动记录。
- History boundary: 编辑草稿不能更换原申请人和申请时间；既有申请、快照、审批状态及历史记录保留，不把保存等同提交或审批通过。
- Evidence: 当前会话逐项确认，`.run/test-feedback-20261009/regression/repair-r8/`。运行部署和真实保存重开仍须完成，不能凭单测登记全项通过。


## Q-FEEDBACK-MATERIAL-PRODUCT-TYPE-20261009-005 — 物料选择的产品类型

- Status: RESOLVED
- Area: 仅FB-05物料选择的产品类型显示，不阻断已确认申请区修复和订单合同显示。
- Question: 原反馈“产品类型”表示订单产品名称/型号，还是设备类别（防火墙、交换机等）？用户此前只澄清当前页面没有该项，尚未裁决其业务口径。
- Evidence: 当前范围响应有产品编码/描述，明细设备类型可为空；不能将缺失值猜为某一设备类别。
- Current action: 用户已在当前会话确认“订单产品名称/型号”，按原订单描述展示，保持现有目标产品可选能力。代码与9项后端、7项前端验证完成，合同读取增量包待本次明确批准部署并完成真实浏览器回归。
- Decision owner: 需求方

- Resolution: 需求方明确“订单产品名称/型号”，不按防火墙、交换机等设备类别推断。合同号按每个所选订单实际合同展示，不能用项目主合同替代所有订单的合同。


## Q-ACC-COMPLETION-SATISFACTION-20261010 — 完工证明与满意度调查成套推送

- Status: RESOLVED
- Confirmed scope: 需求方明确一次完工对应一份满意度调查，完工证明和满意度调查两个页面一起推送给客户。不能仅按项目寻找任意证明或仅推送问卷。
- Question: 同一客户入口中，两个页面分别签字提交，还是一次签字统一确认完工证明并提交满意度？
- Recommendation: 同一受控入口呈现两个页面，分别保留完工确认与满意度答卷的签字、提交事实；送达不代表任一业务完成。一次签字统一提交须由需求方明确其适用对象与事务边界。
- Evidence: 当前满意度 `SatisfactionAccessGrantService.inspect` 仅返回冻结问卷与到期时间；旧完工证明具有独立客户确认状态机。既有 F-ACC-002 计划明确旧完工证明独立保留，故本次关系和成套入口须显式补充契约，不直接改写旧历史。
- Decision owner: 需求方

- Resolution（2026-10-10，Q-ACC-COMPLETION-SATISFACTION-20261010）: 需求方明确“一次签字统一提交两份表单”。同一受控入口展示本次完工证明及冻结满意度调查，签字共同适用于两份表单；答卷保存、签字事实关联和完工证明客户确认同事务成功或回滚。结果评分/生成沿用既有提交后判定及可重试机制，不把推送或低分等同未提交。

## Q-ACC-COMPLETION-ORIGINAL-SURVEY-20261010 — 自动生成的原表与评分规则

- Status: RESOLVED
- Confirmed scope: 完工证明界面保留原样；满意度填写使用需求方前面给定的DemoV2原表，自动推送不得随机选择已有示例问卷。
- Question: 本地已有两份已发布示例，均非原表；原表还未确定总分算法和达标阈值。
- Resolution: 需求方在当前会话先选择“使用原表，并补充评分规则”，随后明确“10项取平均分，满分100，80分及以上达标”。原表的10项评分等权平均，购买意愿与两项意见不计分。新增独立发布原表模板用于默认外发；不修改已有示例或任何冻结历史。
- Evidence: 当前会话两次明确回答；V430前向种子与原生问卷解析/评分验证；真实浏览器验收记录见 `docs/generated/2026-10-10-satisfaction-style/`。
- Decision owner: 需求方

## Q-ACC-COMPLETION-EQUIPMENT-20261010 — 保留原设备清单与新建证明数量

- Status: RESOLVED
- Question: 项目 `992203060198` 原设备清单有7类产品，但当前订单范围仅有主机4件、光模块16件；其余5类在完工证明中应按原清单设备数量回填，还是等待其他订单或配置清单来源？
- Resolution: 需求方明确“按原清单设备数量回填全部7类”。保留原完整产品集合，证明类型、型号和数量与原清单的设备数量一致；不再仅筛选当前有订单范围的产品。需求方同时指出订单号、项目数量、发货数量和未发货数量缺失，保留这四列及实际可读的订单数据。
- Evidence: 当前会话明确答复；原生项目设备API返回7类共40条记录，当前交付范围及订单行均为2行、20件。其余5类缺少订单数量来源，展示未知值；不将设备数量写回或推导成订单分配/发货数量。真实页面与保存重开证据见 `docs/generated/2026-10-10-completion-equipment-restore/`。
- Decision owner: 需求方

## Q-PROJ-SPLIT-SCOPE-OFFICE-20261012 — 同子项目同订单行的范围办事处

- Status: BLOCKED_BY_SPEC
- Area: 仅同一子项目、同一订单行按不同范围办事处再次细分的语义；不阻断合同多订单初始化、父范围同步和按订单行拆分。
- Question: F-PROJ-002 的“订单行、数量、办事处部门编码和序列号可任意组合”是否允许同一子项目、同一订单行形成多个范围办事处？
- Evidence: `specs/features/F-PROJ-002-project-split-tree-and-progress-aggregation.md` BR-FPROJ002-002 保留任意组合描述；`specs/features/F-COM-001-contract-order-association-and-delivery-scope-allocation.md` BR-FCOM001-002/003 同时规定一项目一订单行最多一条当前主范围、办事处取目标项目的 SYSTEM 权威快照。现行 PROJ 旧入参可带范围部门，但 COM 不接受其覆盖项目 Owner。
- Current action: 多订单行拆分按 `(clientItemKey, orderLineId)` 去重，保留同项目同订单行唯一约束及跨行 SN 去重；新面板只在子项目层选择办事处，移除无效的范围办事处控件。既有 DTO、草稿字段、已分配范围及历史不改。未决组合不自行放开、不修改 PRD。
- Decision owner: 需求方

## Q-TEMPLATE-S6-CLOSURE-TASK-20261014 — S6闭环任务完成与闭环申请前置条件

- Status: RESOLVED（业务口径已确认，接入已部署，项目实例全流程验收未执行）
- Area: 20261014清单中新建的四类常规模板S6“项目闭环”任务；不修改既有项目冻结模板或原厂督导S5闭环路径。
- Question: 现行正常闭环申请要求全部任务及末阶段完成。如果S6任务等待同一次闭环审批通过，申请前置检查与任务完成将形成循环依赖；该任务应记录闭环准备还是最终审批结果？
- Evidence: `NormalClosureCheckService`检查`STAGE_COMPLETION`与`ALL_TASKS_DONE`；来源模板`993009900016`的已发布V2/V3均无S6。既有模板`993009900000`的S6为手动完成的页面绑定，不能证明正常闭环审批完成。
- Resolution: 需求方在2026-10-14本次配置会话明确选择“审批通过后完成该任务，需要补齐闭环接入”。不能改为先手动完成S6，再申请闭环；也不能直接写项目终态或伪造审批结果。
- Current action: 已实现精确冻结闭环任务的申请前检查预判，以及正式审批通过事务中的任务状态机、末阶段和项目闭环接入，未通过审批不能完成该任务。经批准部署59191后，四类常规草稿已绑定`NORMAL_CLOSURE_APPROVED`，五类草稿全部通过Compiler且保持未发布；项目实例全流程验收尚未执行。
- Decision owner: 需求方。


## Q-TEMPLATE-OWNER-FACTS-20261014 — 相同任务的实体来源与多记录完成口径

- Status: RESOLVED
- Area: 本次五类未发布草稿的到货签收实体选择，以及到货、培训、割接任务的多记录完成量词；不阻断全设备安装和S6审批接入。
- Question: 到货复用当前到货签收实体的正式“已签收”结果，还是接入未生产装配的到货验收批次；同项目多条关联到货、培训、割接实体时要求全部达到对应条件还是至少一条？
- Evidence: 现行ArrivalServiceImpl通过签收命令形成status=1；ArrivalAcceptanceController仍为未启用的完整验收候选。清单明确割接任务审批通过、培训确认与所有设备安装完成，但未明确前三者的多记录量词。
- Decision: 需求方本轮明确选择复用当前到货签收实体，以正式“已签收”为准；到货、培训、割接均为至少一条关联记录达到对应条件（ANY）。硬件实施仍为当前范围内全部设备完成安装，S6仍为正常闭环审批通过后完成。
- Current action: 已按该口径新增Owner只读事实适配、正式完成点重评事件和S6审批事务接入。构建及定向失败保护通过，经批准部署59191；五类模板草稿已更新并全部通过在线Compiler预检，未发布。
- Decision owner: 需求方。

## Q-CUTOVER-ACTIVE-REVISION-20261015 — 推进后的主动修订与审批撤回边界

- Status: RESOLVED
- Area: 本次割接七项问题中的第1项，仅阻断主动修订涉及的状态转换；不阻断字段、匹配、上传入口及弹窗问题核查。
- Question: 审批中的任务是否允许任务负责人主动撤回后修订等级、采集清单或方案并重新审批；是否还包含审批通过、尚未执行的主动修订？
- Evidence: PRD CUT-03 核心规则5明确提交后修改须生成新清单版本并使下游未审批方案失效；CUT-04 核心规则5/6、CUT-05 核心规则5明确不可覆盖提交/审批历史及驳回后重新审批，但未定义发起人主动撤回当前审批的命令与适用阶段。真实浏览器确认已提交清单只读，当前缺少主动修订入口。
- Recommendation: 审批前允许受控修订；审批中由任务负责人撤回当前审批后生成新版本并重新审批；已批准内容沿用正式变更规则。所有旧版本及原审批意见保留。
- Resolution: 需求方2026-10-15确认“审批前可修订；审批中可由任务负责人撤回后修订、重新审批；批准后沿用正式变更规则”。修订保留已提交历史，修改等级或采集清单后受影响的下游内容须重新确认。
- Current action: 按本次明确裁决实现受控修订及撤回，不增加批准后自由修改入口。
- Decision owner: 需求方。

## Q-CUTOVER-MATCHING-SOURCE-20261015 — 风险项适用范围与原始0807明细差异

- Status: RESOLVED
- Area: 本次割接问题第3项的规则校正与全组合预期；不阻断匹配引擎、字段展示、上传入口和弹窗问题核查。
- Question: 动态表项、MTU、FEC、长连接、二次过设备、STP、F5及Adware共8项，应按0807原始逐项明细还是现行PRD概括后的适用范围验收？
- Evidence: `需求/售后平台（割接+巡检）/1、割接平台/4、割接平台设计-数据整理（0807).xlsx` 的 `3.调研及风险考察（清单+结果填写格式）` 第76～83行包含配置变更、不包含原厂新旧板卡替换，第82/83行只适用ADX。PRD CUT-09表17～24项概括为设备替换/版本升级/灾备演练，遗漏上述限定。2026-10-15只读接口核实任务2110205784723779584冻结的CUTOVER_STANDARD_V1修订1：8项均包含板卡替换、不包含配置变更，F5/Adware无DEVICE_TYPE限定。
- Resolution: 需求方2026-10-15明确“按0807原始明细校正，不同步修订PRD，给出差异清单”。本次实施和验收按该原始逐项明细；PRD原文保持不动，差异单独登记，不将未修订PRD误称为已同步。
- Current action: 编制差异清单，校正规则配置并补全组合核验；已提交及审批历史保留。配置生效和在途修订按正式服务执行，不能原位覆盖已发布配置。
- Decision owner: 需求方。

## Q-CUTOVER-MAJOR-PROJECT-20261015 — 重大项目备件匹配来源

- Status: RESOLVED
- Area: 0807原始明细第69行“重大项目备件申请”。
- Question: 重大项目由割接等级推断，还是读取项目主数据重大项目级别？
- Resolution: 需求方2026-10-15明确选择项目重大项目级别。通过PROJ正式接口读取`majorProjectLevel`，沿CRM字典映射；当前正式值为无、办事处级、市场级、公司级，后三者匹配，空值和无不匹配，未映射值不猜测归类。
- Current action: 新配置增加该维度，P3生成/重匹配锁定并冻结项目版本；提交时来源版本变化须重新匹配。既有配置和历史事实不覆盖。
- Decision owner: 需求方。

## Q-CUTOVER-CREATE-MINIMAL-FIELDS-20261016 — 新建卡片删除后的配置及补录位置

- Status: RESOLVED
- Area: 新建割接任务第一步“割接等级确认”。
- Confirmed: 需求方2026-10-16确认删除“割接任务信息”卡片，新建只保留项目信息、设备和等级评估问卷；原卡片字段不再要求在创建时填写，后续需要时补充。
- Confirmed placement: 需求方进一步明确：割接类型、组网模式、设备类型放到调研；计划割接时间在方案页使用单个输入框（以需求方随后纠正为准）；割接背景沿用已有结构化数据，不在等级确认重复填写。
- Resolution: 需求方明确：D级不需要割接类型、组网模式、设备类型；新任务自动采用唯一有效已发布配置，多个时停止并提示明确配置；需求方随后纠正：所有方案页使用单个计划割接时间输入框，审批直接采用该输入值，不取明细计划最早时间。
- Evidence: 创建服务当前冻结配置并要求类型、背景；调研匹配已有类型/组网更新入口；审批源组装和时限计算仍依赖任务计划时间，直接置空会破坏后续办理。
- Current action: 同步调整创建接口及存储约束，创建时允许调研字段及背景、时间为空；复用调研选择与结构化背景，D级不生成这三个字段；审批读取方案单个计划割接时间。既有任务字段与历史快照保留，不补造业务值。完整上传方案同样使用方案页单个时间框，补录方式已确认。
- Decision owner: 需求方。

## Q-CUTOVER-CONFIGURATION-ADOPTION-20261015 — 既有任务采用校正配置

- Status: RESOLVED
- Area: 既有任务主动修订时的配置采用策略。
- Question: 校正配置发布后，既有任务是否允许负责人主动修订时选择新配置，还是仅新建任务采用？
- Evidence: 既有任务保存精确configurationRevisionId，不能通过原位更改已发布定义让已提交清单隐式变化。
- Resolution: 需求方2026-10-15选择“负责人主动修订时可选择新配置，保留旧版本”。
- Current action: P2/P3修订入口提供同一配置当前有效的已发布版本，默认沿用原配置；选择新版本后重新匹配，仅沿用字段定义一致的填报与受权材料引用。旧评估、清单、方案及其配置保留历史；配置发布仍由正式发布操作完成。
- Decision owner: 需求方。

## Q-SUPERVISION-REQUIREMENT-SOLUTION-20261019 — 督导模板按需需求分析与必做实施方案

- Status: RESOLVED
- Area: 未发布督导模板993009900036的需求分析与实施方案依赖；不阻断已授权的割接入口及到货、初验、终验实体页面配置。
- Question: 需求分析按需、实施方案必做时，是否按方案办理方式决定需求分析的前置要求？
- Evidence: 当前草稿PRE_REQUIREMENT为按需，PLAN_SOLUTION为必做。SolutionSubmissionValidator的在线编制分支要求本项目有效FROZEN需求分析；完整客户方案分支先由SolutionCustomerDocumentService核验原件引用，再按既有上传路径办理。
- Recommendation: 保留需求分析按需。在线编制实施方案时先完成需求分析并引用有效版本；已有完整客户方案时沿用原件校验及既有提交审批路径。明确提示条件依赖，不自动补造需求分析、不降低在线提交校验。
- Current action: 已按2026-10-22裁决调整实施方案可选引用逻辑，保留需求分析任务按需属性；不改写已发布模板与既有项目历史。
- Decision owner: 需求方。

- Decision (2026-10-22): 需求方确认需求分析保持按需；有有效已完成需求分析时实施方案自动引入，没有时独立编制并允许正常提交。此裁决替代上述前置要求建议。已有引用保留项目归属、有效完成版本、版本变化及差异说明校验；其他提交校验和不可变来源历史不变。
- Validation (2026-10-22): 后端SolutionSubmissionValidatorTest、SolutionTieredReviewServiceTest共38项通过；前端solutionRequirementSources.runtime.spec.ts共16项通过。覆盖无引用独立提交、有有效版自动引入、草稿不引入、已有引用归属/有效性/版本差异及不可变来源保护。通过worktree-build.ps1构建并冻结7abffcbc-6f8d-408d-88e3-93b7caee46db，经批准使用worktree-backend.ps1部署59191（local,dac，PID 30916），保留22项既有部署修复基线。真实浏览器以杨凯添提交项目992203060196的隔离方案30057并重开审批详情，显示审批中；只读回查确认该项目需求版本数为0、方案无需求引用、审批记录RUNNING。另以正式草稿API准备项目992203060198的隔离方案30058，真实浏览器显示需求版本变化提示，提交时拒绝“PRE-04 尚未形成有效的已完成需求分析版本”，重开仍为草稿且无审批记录；原方案30055及既有需求版本保持不变。证据见.run/optional-requirement-browser/，本次仅验证提交，不代行审批通过。

## Q-DAC-DESKTOP-AUTH-20261021 — 本机 DAC 复用的浏览器认证入口

- Status: RESOLVED
- Area: F-INT-012 浏览器直连本机 DAC 的认证适配；不阻断已授权的 Windows 安装、自启动、环境配置及服务发现。
- Confirmed: 需求方要求客户端不绑定、不注册、不设独立账号，沿用现有浏览器认证和通信方式。
- Evidence: NPDMS 统一采集当前由服务端 Gateway 携带服务 JWT 与任务 HMAC 访问 DAC；DAC 独立网页另有 OIDC Authorization Code + PKCE 登录。两者并非同一浏览器登录态。
- Question: 复用 NPDMS 网页当前登录态，还是 DAC 独立页面现有 OIDC 登录？
- Recommendation: 复用 NPDMS 当前登录态，并保留服务端业务权限、项目范围和任务授权；服务级 JWT、HMAC 密钥不下发浏览器或安装包。
- Resolution: 需求方2026-10-21选择复用 NPDMS 网页当前登录态，随后补充现有 OIDC 也可使用。本次网页接入采用 NPDMS 当前登录态，既有独立 DAC 的 OIDC 能力保留；不增加客户端绑定或独立身份。
- Current action: NPDMS 在现有来源、权限和项目范围校验后签发任务/操作限定的短期票据；本机 DAC 向配置的 NPDMS 解析票据。完整登录 Token 与服务级共享密钥不交给本机浏览器端点，业务状态仍经原状态机和结果接收处理。
- Decision owner: 需求方。


## Q-ACC-COMPLETION-ARRIVAL-DATE-20261022 — 完工证明到货日期来源与显示

- Status: RESOLVED（2026-10-22，需求方确认）。
- Decision: 到货日期从当前项目已签收的到货记录取实际到货日期；多条可用记录取最新到货日期；无可用记录且选择“是”时允许手填到货日期，也可留空。不取登记、更新或完工日期，不跨项目/租户取值，不将到货验收批次替代当前到货签收实体。
- Display: 填写、查看、客户核对和打印保持一致。仅“完成到货验收”选“是”时显示“于……年……月……日”；选“否”“不涉及”或未选择时隐藏整个日期前缀。选“是”且无可用日期时保留空白日期位，不阻止原有提交。
- History: 草稿优先自动带出当前业务日期；无可用记录时允许手填并保存，有记录时自动日期只读且服务端不接受手填覆盖。填写、查看和打印采用同一有效日期；外发时与完工证明一起冻结。已外发/已签署的旧快照和已生成文件不回填、不改写。
- Supersedes: F-ACC-002 于2026-10-10 Word打印样张裁决中“未采集的到货日期保持空白，不推导或补造”的到货日期取值部分；客户联系方式、服务方签章及其他历史保护口径保持。

- 2026-10-22补充确认：无实际签收日期时开放手填，允许留空；新增可空手填日期列，不回填既有历史。

### 2026-10-22 P14父头类型补充裁决及P15合同来源一致性修复

- P14 decision: 用户再次明确“订单行匹配头必须追加本身行类型，有相同订单号的不同类型订单，不能直接根据订单号相同就认定是同一个”。关联必须同租户、source_system、company_code、order_no且头order_type等于原行line_type；唯一的其他类型头也不能回退使用。此前“类型差异不阻断预览”仅指原行仍完整读取和登记，不等于允许跨类型归父。
- Fresh source facts: 仅查询两张已拆分原表，93,223个物理头归并92,890头，388,207个物理行归并387,154行；精确父引用缺项1,580条。其中337条涉及90个订单，均line_type=1、唯一同号头order_type=0；另1,243条无同来源、公司、订单号头。337条应归为缺正确类型父头，不归为实现故障，不填占位或改类型。正式order_id可空但父order_type必填，未获规则不伪造承接。
- P14 implementation: 一次尚未部署的候选曾尝试唯一其他类型头回退，已按本次澄清完全撤回，未造成任何运行或数据写入。匹配仍保持完整业务键；补充原生检查覆盖同号不同类型不能归父、同号两类型精确匹配及跨公司或来源不能补父。
- P14 missing-reference decision: 用户随后明确“按给定条件匹配不到的数据该缺失就缺失”“特殊情况只记录并提示不要作为阻断项影响正常数据的同步”，并补充“未匹配行按缺失值写入”。新P14原明细仍完整写入并保存独立来源绑定，order_id及只能从父头取得的字段保持NULL，原订单号、行号、line_type和数量保留；order_type按给定匹配条件承接本行原line_type，不能改成其他类型。预览/实际均逐条登记并汇总警告，正常计入真实插入/未变，正常头行继续提交；不修改匹配规则、不造父头或占位ID、不执行DDL。已有目标仍执行绑定/删除/租户/来源版本保护，已有有效关系不自动清空；不自动修正环境数据或历史回填。此前“整批回滚”及未部署的“跳过未匹配行”处理均被本次裁决取代，真实结果另记。
- P15 implementation: 原实际阶段改读尚未落库的P14正式头，合同步骤读0但整体历史回执COMPLETED；这是把可延后的正式ID变成业务字段承接前置的实现依赖。新P15预览及执行统一复用既有三条原始查询、订单类型与最大订单号选客及字段优先级，P24同租户同连接正式完成前置、目标归属、绑定、数量/来源冲突及不可变历史保护保持。只替换新P15产品类，真实预览、执行和重放的终态见后续报告，不以定向检查替代真实成功。
- Scope: 仅172.17.90.178:13306/npdms及59284；P22/P33实际写入继续暂缓，B稿、Flyway和其他迁移不执行，原任务及59191不操作。原表字段与正式数据不自动修正，不重复请求已裁决事项。

- Final real verification 2026-10-22: 新P14预览2113085785393692674、实际2113086119482589186、重放2113090487393026050均成功；实际92,890头＋387,154行，其中1,580行order_id=NULL且原类型/数量/映射字段及绑定逐条核对一致，全量无错误非空父键，重放480,044未变。新P15实际2113076309290647554生成83,105份合同、既有4,625,378历史保持，重放2113081125584764930共4,708,483全未变。39项定向检查、47表结构/Flyway及隔离保护通过，活动Run0。当前20项执行及重放完成；P18正式授权/8条公司事实、P31的152条倒置日期仍是外部前置，P22/P33真实写入、B稿继续暂缓。匹配缺项不再是P14阻断，既有授权和日期规则不因此降低。[最终完整原记录、正式回执与保护证明](../generated/dppms-24-tasks-three-implementations-20261019/P14-strict-parent-and-P15-contract-followup-20261022.md)。

### 2026-10-22 P18/P31原始查询与原值承接追加指示

- User direction: “这类数据问题不需要你进行处理，原始数据如何就是如何”“不要自行创建不必要的阻断项”“空值填充，字符串操作，空值默认等都可以在原数据查询时进行修复”。P31取消原倒置日期的Java拒绝、判断及警告分支，原日期直接承接，不过滤或改日期；原仅预览放行规则由此更新。P18简单审计文本默认在SQL完成，移除Java重复非空处理，不创建账号或改公司/合同匹配规则。
- Evidence: 70项定向检查通过；P18原生creator NULL失败已通过源查询默认修复。完整原关系、既有正式限制和本轮真实预览见[报告](../generated/dppms-24-tasks-three-implementations-20261019/P18-P31-raw-source-formal-prerequisites-20261022.md)。正式当前公司授权和非空合同外键未绕过；用户已单独明确批准仅远端npdms的P31日期CHECK例外，实际执行与重放完成以本轮真实凭据为准。原任务、P22/P33实际、B稿、迁移及59191保持既定边界。

本轮P31闭合事实：P31已按用户明确批准仅在远端npdms移除chk_proj_member_dates，真实执行与重放均COMPLETED；381195条成员及独立绑定承接，152条倒置原日期、审计时间和角色逐项回读一致，重放计数与绑定指纹保持。仅该CHECK例外获准，未扩展至其他DDL；P18实际仍未执行，公司授权及正式合同缺引用边界保持。

P31性能收口：旧预览基线1280.963秒；A3首轮实际执行2058.91秒；A4批次500对现有381195条全量预览82.791秒，随后仅新P31批次在正式1..2000范围由500调至1000，A4批次1000全量预览67.624秒、该阶段重放175.953秒。批次调整已VERIFIED并保留审计及加密备份路径，其他任务配置、业务、原任务和Flyway保持；该调整非DDL，无代码改动或重启。没有重置数据，不能将优化后的预览或重放称为新包首次插入基准。 A5最新真实全量预览58.675秒、execute重放171.441秒，两Run均读取/不变381195，其余写入计数0，业务及绑定CRC保持，152原值回读通过。A5仅缩小全绑定批次的目标回读ID范围，新来源仍原查重，缓存同一旧指纹；租户、项目和历史保护保持。原A3首插2058.91秒、A4两轮预览和首轮优化重放175.953秒回执保留；A5仅复核现有381195行，不重置数据，不提供新包首插基准。 A6共享正式范围查询保持同一选中Run的task_id供分页复用，原筛选、投影、排序及LIMIT500不变，仅增加等价task_id条件并固定既有源键索引连接顺序；EXPLAIN为task常量→binding源键range→project主键，无filesort，无新增索引或DDL。最新70项检查；真实预览57.983秒、重放59.684秒，两Run均381195读取/不变且0业务及绑定变化，152条原值保留。A5历史预览58.675秒/重放171.441秒及全部A3/A4回执继续保留；A6不提供新包首次插入基准。 MANAGE每Run仍用Owner API一次，每批身份/最新树/root path校验保留；新绑定500条批量INSERT，更新仍条件WHERE，replayMap同5键。来源SQL无新增嵌套；既有索引EXPLAIN和3次jcmd仅作路径定位证据，不归因时间占比。唯一DDL仍日期CHECK；无新权限、原任务或59191操作。

### 2026-10-22 P18原合同号先承接与结构例外待答复

- 最新用户裁决：先原样承接合同号，引用字段后按原合同号回填，不能因正式合同缺配阻塞原始承接。缺合同只登记、不落关系的中间实现已被此裁决取代，不再重复确认承接规则。
- 最新候选：仅新P18及其目标XML，25项无数据库/无DDL检查通过；先写原文和NULL引用，全部分批写入后一次精确回填，缺配保留正式关系和两类独立绑定，原项目/公司/合同号键、历史指纹及既有授权保留。候选未加载，未提交新预览或实际Run。
- 真实结构缺项：com_project_contract_relation当前17列，没有contract_no，contract_id为NOT NULL。仅提出新增contract_no VARCHAR(64) NULL及contract_id可空的两条结构例外；异步问题待用户答复，当前未执行DDL或迁移。来源合同号最大18字符，1847缺配业务关系=1839缺合同+8原公司NULL，明细及SQL见[本轮记录](../generated/dppms-24-tasks-three-implementations-20261019/P18-raw-contract-number-carrying-20261022.md)。
- 授权依据纠正：ADR-0038直接针对当前访问/维护；历史迁入依据另为10月12日Q007已批准方案的公司资格等保护，旧正式迁移Owner将公司资格实现为操作者当前有效公司范围。不能把实现行为当作ADR对历史导入的直接条款，也不能把整表来源审核自动等同当前执行授权豁免。本轮没有授予范围或取消该项检查。

### 2026-10-22 P18两条承载DDL获准执行及真实预览

仅远端npdms正式关系表新增原contract_no及contract_id可空两DDL已明确批准并VERIFIED执行；owned59284新包真实预览Run 2113143505039691777 PREVIEW_READY。25项既有定向测试复用。读取82676原行，仅规划8条原公司NULL的raw关系；actor1范围0、其余82373业务关系仍授权pending。实际执行/重放/引用回填未提交，业务绑定0写入。原reviewSQL字节保留，其未授权旧注释为历史审核输入；新executed.sql记录实际仅两SQL。无其他DDL、迁移、原任务或59191操作。

本次已确认P18直接批量承接的真实结果：三个Run对现有82676原来源均unchanged，完整业务/绑定hash各阶段前后不变，1839+8未配原号保留；见[本次真实结果](../generated/dppms-24-tasks-three-implementations-20261019/P18-direct-batch-execution-and-replay-20261022.md)。

### 2026-10-23 P33普通安装历史放行、单属性地址快照与真实闭合

- 已有用户裁决：解除普通记录实际写入暂缓，两条串货仍暂缓；安装地址不创建地址实体，地址快照仅承接界面显示字段单属性JSON。沿用空地址过滤、原文完整保存、不扩大SN或项目匹配、正常记录不因特殊缺项阻断的规则，不新增待裁决项。
- 实现边界：仅新BASE_P33安装历史及其独立绑定；原查询直接生成JSON_OBJECT('fallbackLocation',installAddress)，同时保留install_location原文，UNRESOLVED及状态2，地址／站点／位置引用为空。不调用当前位置Owner、不修改设备主档；同步执行权限、租户、项目MANAGE及历史防接管和指纹校验保持。
- 真实结果：预览`2113332424638021633`、实际`2113335660941971458`、重放`2113346323445821441`均成功。读取1035329，新增1035322，重放同数未变、其他写入动作0；5条正式设备缺项及2条串货来源未创建安装行或绑定，不计为完成写入。
- 页面边界：P33地址显示的真实页面抽核未通过；设备现有可见性、较早GC压力期间的SQL解析超时及最后重放后只读页面复查分别记录。后次HTTP200、仍0台且未见超时提示，未取得响应体，不认定先前超时是持续旧查询缺陷；不以单样本推定全量不可见，不以数据库原文核验冒充页面通过，不修改旧设备查询或自动授予范围。
- 检查：141项有效定向检查通过；新套件24项72个有效阶段完成。P33首次实际SQL失败和所有旧失败回执完整保留，最终精确受跟踪；先修目标INSERT租户列写法，再仅为selectExisting清理跨批会话缓存；SQL正文、权限及匹配不改，租户解析器覆盖30个相关INSERT。21项最终保护检查通过，七张位置相关表、既有Run、原任务三表、结构／Flyway和默认配置保持。摘要边界及具体缺项见[完整证据](../generated/dppms-24-tasks-three-implementations-20261019/base-sync-review-fixes-and-runtime-20261022.md)。无DDL、迁移、原任务或59191操作。

### Q-PROJ-MEMBER-OVERLAP-CONFIRM-20261024 — 迁入成员确认的重叠区间

- 状态：BLOCKED_BY_SPEC；Decision owner：需求方。
- 用户已批准137273条启用账号/内部角色按用户名精确绑定并确认授权；实际安全提交137049条。剩余224条（32账号、107项目）存在同一用户/项目/角色区间重叠，其中15组30条还违反现有唯一键；均保留未确认原状。
- 待裁决：逐组明确哪些业务有效期/当前责任成立，以及重复来源如何保持历史追溯；不能猜选一行、改写原有效期、删除历史或取消唯一/重叠校验以满足数量。
- 证据：[成员确认执行记录](../generated/domain-migration-comparison-20261023/member-authorization-confirmation-20261024.md)，逐条前像与原因位于.run/permission-data-audit-20261024/member-confirmation/blocked-candidates.json。仅依赖该裁决的数据处理暂停；独立的大范围查询栈溢出代码修复及定向验证已完成，未部署、未重启。

## Q-AST-DEVICE-CONTRACT-SCOPE-20261024 — 设备合同分支扩大部门级组织范围

- Status: RESOLVED（2026-10-24需求方选择保留公司部门约束，裁决见本条Resolution）。
- Evidence: 2026-10-24需求方指定账号z01010。只读核查当前有效范围为公司001/002与部门178/470/94的组合，无公司级空部门授权；用户同时具有pms:device:query和pms:commerce:contract:query。真实浏览器默认设备列表1042435条，办事处“杭州”筛选79428条；设备02010502N224800206（ID 2104569064506609672）无直接项目，固化组织为company=1、department=253/162022，组织分支不命中，列表与详情均可读。其唯一正式合同31020220510A04所属公司001，与发货合同公司一致；当前ContractAccessService.currentCompanyCodes丢弃部门维度，并由设备授权OR分支放行。
- Existing contract: F-AST-001 2026-09-21批准组织/项目/合同独立OR；ADR-0038将合同管理员的合同目录范围定义为当前scope公司编码集合，部门不收窄。z01010实际拥有合同查询权限，不能通过删除功能权限或假定角色缺失修复，也不能把本现象误判为前端筛选失效。
- Question: 设备的合同分支是否仍需满足同一有效组织范围行的公司与部门，或完全取消设备通过合同授权？合同目录本身是否保持ADR-0038既定公司范围？
- Recommendation: 设备仍保留组织与明确项目授权；合同分支保留公司部门组合限制，公司级范围仍覆盖公司；不改变COM合同目录范围、不修改实际用户授权，不删除历史或设备归属。
- Current action: 已复现并保存证据.run/user-org-scope/z01010-device-detail.png及.txt；按本条Resolution实现设备专用范围收敛，验证记录归F-AST-001 Task，未再次部署。

- Resolution (2026-10-24): 需求方选择“采用建议，保留公司部门约束”。设备保留组织范围或明确项目授权；合同分支必须命中同一当前有效组织范围行的公司及可选部门，公司级范围仍覆盖公司。收敛仅适用于设备授权，不改变COM合同目录的既定公司范围，不靠修改实际用户角色/授权修复。

## Q-PLN-MANAGEMENT-MENU-20261024 — 施工计划独立菜单开放范围

- Status: RESOLVED（2026-10-24，需求方确认四类现有角色）。
- Resolution: 独立“阶段施工计划”菜单仅对super_admin、admin、engineeManager、engineeManagerLeader展示；多角色用户命中任一上述角色即可保留。项目经理、服务经理单独持有角色时隐藏入口。需求方同时要求检查隐藏入口对施工计划重排的影响。
- Boundary: 菜单展示收敛不撤销既有施工计划query/create/update/submit权限，不移除路由，不改变项目详情内重新倒排的项目范围、草稿状态、日期校验和批准历史保护。仅给现有三个管理角色补齐独立页面及父目录的菜单初始化授权，不创建新角色或自动授予项目范围。
- Evidence: 当前验收库原显式菜单角色为PROJECT_MANAGER/SERVICE_MANAGER，super_admin默认全菜单；三个管理角色原无该页面授权。实现及验证见docs/generated/2026-10-24-stage-plan-menu-visibility.md。

## Q-CUT-ROLE-FINAL-READINESS-20261024 — 角色共享终审的内部就绪重验授权

- Status: RESOLVED（2026-10-24，需求方选择“采用受控内部重验”）。
- Evidence: 项目74原割接任务CUT-2113678637070770176在真实浏览器完成发起人、服务经理及二线审批后，当前合格研发角色账号提交终审，服务端403，业务码1011005603，“无权读取该项目就绪快照”。该决定未写入，原三节点通过历史保留。终审调用CutoverExecutionReadinessPolicy.requireCurrent，ImplementationReadinessService.readSnapshot仍以当前审批人的通用项目查看范围校验。
- Existing contract: F-CUT-005当前角色共享审批不要求项目成员交集，只授予当前审批实例冻结资料及文件的临时只读范围；F-IMP-001要求CUT内部消费按受信租户、明确业务动作、项目/设备范围及快照版本校验，不能继承项目经理发起权限。现有内部就绪API尚未声明角色审批动作的受控重验授权。
- Question: 是否允许在保留P5/P6就绪门禁的前提下，为当前合格审批节点提供仅绑定审批实例、项目、设备和精确快照的服务端内部重验，不授予审批人通用项目查看权、不返回实时Owner资料？
- Resolution: 保留P5/P6就绪门禁；当前合格审批节点可在原决定事务内，对绑定本审批实例、任务、项目、设备及精确快照执行服务端内部重验。不得增加审批人的通用项目查看权限、继承发起人权限或向其返回实时Owner资料。租户、节点资格、版本、来源变化及设备归属校验继续失败关闭。
- Current action: 内部契约、实现及70项相关测试已完成，固定清单ca889a29-5e21-474c-9649-a815474c7e4c已部署；74原研发节点与78研发节点、75二线终审均浏览器通过，原历史保留。五项目最终正常闭环，证据见docs/generated/2026-10-23-five-template-browser-acceptance.md。

## Q-CUT-NO-CUSTOMER-LEVEL-20261025（已裁决）

- 来源：需求方在工作台新建入口反馈16200720111902N-0不能手选。真实浏览器读取项目2109733000842969090，客户未关联，customerId/customerCode均空，原响应UNAVAILABLE；同入口关联客户但未维护等级的隔离项目79可手选，不能混为同一已验场景。
- 裁决：2026-10-25需求方明确选择“允许无关联客户时手选”。仅当PROJ已确认客户ID及编码均空时，允许在本次评估中显式选择已启用字典等级；不写客户主档、不补客户关联、不伪造CUS读取成功。存在客户编码但无法解析、真实读取失败或冲突仍不允许手选。
- 落位：F-CUT-002客户等级手选差量。保留任务/评估版本、项目/设备范围、租户与权限、选择人/时间、提交冻结和不可变历史；不回填已提交记录。
