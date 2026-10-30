# F-CUT-005 P5分级审批

> Feature实施状态：`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO @ 2e3fdba3`
> Technical Plan Gate：`PASS / GO @ 912d0cdb`
> Implementation Done Gate：`BLOCKED_BY_DEPENDENCY`
> Requirement：`CUT-05@V1=FULL`
> Feature Spec：`specs/features/F-CUT-005-p5-graded-approval.md`
> 机器合同：`specs/features/F-CUT-005-api-contract.json`、`specs/features/F-CUT-005-physical-contract.json`、`specs/features/F-CUT-005-approval-owner-contract.json`、`specs/features/F-CUT-005-candidate-owner-contract.json`
> 旧实现审计：`specs/features/F-CUT-005-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-01-f-cut-005-p5-graded-approval.md`
> master集成映射：`codex/f-cut-001-matrices@faed8387 -> master代码回执c9066332；来源V153/V154 -> master V184/V185`
> master复验：`CUT API 6项、CUT共享后端242项（跳过MySQL 27）与前端68项零失败；生产PROJ/SYSTEM Owner与真实验收未闭合，状态保持IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY`

## 当前最小工作单元

- 2026-10-24本轮浏览器验收修复：项目74原研发终审暴露通用项目读取权限与角色共享审批冲突。需求方已批准Q-CUT-ROLE-FINAL-READINESS-20261024的受控内部重验；实现及失败验证限定当前审批实例、任务、项目、设备和精确快照，保留P5/P6门禁及三节点既有历史。进展与浏览器结果统一记录于`docs/generated/2026-10-23-five-template-browser-acceptance.md`，不据本轮局部修复改写总体Feature Done状态。

- Feature Ready已在锁定提交`2e3fdba3`独立复审GO；唯一Technical Plan已在`912d0cdb`独立复审GO，Task 1～10均已独立GO。CUT受控正向闭环已完成；Task 11/12生产Owner支撑仍按依赖边界阻断，不因测试替身解除。
- PROJ/SYSTEM生产候选Provider缺失不阻断受控替身规格与后续内核实现，但阻断生产完整装配、真实浏览器和Implementation Done。

## Gate清单

- [x] API/Physical/Candidate/Legacy Machine Contract Gate。
- [x] Feature Ready最终裁决：`GO @ 2e3fdba3`。
- [x] 唯一Technical Plan独立复审：`PASS / GO @ 912d0cdb`。
- [x] Task 1 CUT领域合同、消费端口与快照Codec：`PASS / GO @ e6dac9fe`。
- [x] Task 2 五表Schema、DO、Mapper与锁查询：`PASS / GO @ 367438e6`。
- [x] Task 3 审批启动、来源冻结与公开FactApi Provider候选：`PASS / GO @ df406b0c`。
- [x] Task 4 通过/驳回状态机与任务P4/P6原子迁移：`PASS / GO @ 6dae8751`。
- [x] Task 5 详情、本人待办、改派队列与管理员改派：`PASS / GO @ 0142e01e`。
- [x] Task 6 站内通知提交后投递与暂停Job：`PASS / GO @ e5aff408`。
- [x] Task 7 六路由REST、严格请求Codec与错误合同：`PASS / GO @ eefb40ec`。
- [x] Task 8 P5审批工作台与组件交互：`PASS / GO @ ac8a6e5f`。
- [x] Task 9 字典、权限、通知模板与暂停Job种子：`PASS / GO @ 6be558c4`。
- [x] Task 10 真实MySQL受控正向闭环：`PASS / GO @ a7e5b7b3 / IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`。

## 最近检查点

- `5e3ce44c`方向成立但Feature Ready未通过；不得进入Technical Plan或实现。
- `2efad8ce`已关闭动作判别、候选交集和通知边界；快照可空性与管理员改派入口仍需定点复审。
- `2e3fdba3`关闭剩余两项并获Feature Ready正式GO；最近Gate为唯一Technical Plan独立复审。
- `1a45569f` Technical Plan首轮NO-GO仅指出生产Owner产出路径缺失与测试顺序冲突；当前候选只整改这两项。
- `912d0cdb`关闭Owner合同/SYSTEM阻断表达与实施顺序残留，Technical Plan正式GO；最近实施单元为Task 1。
- Task 1只新增CUT领域规则、三类消费端口、Owner异常、精确快照Codec与`src/test`受控正向事实；无生产Bean、DDL、COM或Yudao修改。
- `e6dac9fe`关闭冻结快照跨子事实身份一致性与COLLECTION可选引用正数约束，Task 1正式GO；最近Gate为Task 2 Schema/迁移与隔离MySQL正向验证。
- Task 2候选使用串行V153创建五张CUT Owner表，补齐DO、场景化Query、Mapper XML、节点CAS与通知领取；静态合同5/5及隔离MySQL 8.4全量迁移和正向约束验证通过，等待独立Gate。
- `367438e6`统一Yudao审计字段物理类型与本人待办稳定排序，Task 2正式GO；最近Gate为Task 3启动/冻结/FactApi正向实现审查。
- `df406b0c`关闭通知/暂停物理约束、业务摘要、路由快照及公开事务异常边界，Task 3正式GO；最近Gate为Task 4通过/驳回及P4/P6原子推进。
- `6dae8751`关闭Task 4锁序、冻结Owner事实重验、当前候选交集、根挂起及业务错误分类，独立复审正式GO；最近Gate为Task 5查询、allowedActions与改派正向实现。
- `0142e01e`关闭Task 5完整候选交集、hold恢复、通知版本键及待办投影身份一致性，独立复审正式GO；最近Gate为Task 6站内通知提交后投递与暂停Job。
- `e5aff408`以独立`REQUIRES_NEW` Provider Bean隔离SYSTEM通知事务，证明Provider失败回滚不影响CUT外层持久化`PENDING_RETRY`，独立复审正式GO；最近Gate为Task 7六路由REST与严格请求合同。
- `0f98d527`关闭审批成功后Owner刷新与结构化错误合同问题；`eefb40ec`进一步冻结实际决定节点并使同键同载荷重放直接复用平台结果，Task 7独立复审正式GO；最近Gate为Task 8实施前边界核验。
- Task 8候选已完成P5完整/最终结果/管理员改派三种投影、本人待办与改派队列入口、五项评审及服务经理复核、合法驳回和统一写后刷新屏障；跨模块Owner仅使用正式接口与测试模拟，无生产Fake/fallback。当前等待独立Code Review／组件交互Gate。
- `327d1477`首轮Task 8独立复审为`NO-GO`；整改已改为审批专用抽屉直达三种投影，补齐冻结P2五维和风险/业务项正文，使用`REFRESH_APPROVAL/REFRESH_SOURCES`恢复动作，并按任务、节点与改派版本重置本地状态。39项正向组件交互和低并发原命令`build:local`均通过，等待最小整改复审。
- `ac8a6e5f`关闭审批专用入口、冻结依据展示、正式恢复动作与组件身份重置四项阻断，独立复审正式`GO`；Task 8 Gate回写`PASS`，最近实施单元为Task 9字典、菜单、权限、通知模板与暂停Job种子。
- Task 9候选以串行`V154`新增P5封闭字典、既有割接工作台下三个权限、`CUT_APPROVAL_PENDING`站内信模板及初始暂停的`cutoverApprovalNotificationJob`；不写角色授权、不修改旧菜单/权限、不同步Quartz。迁移合同5/5通过；隔离MySQL 8.4从空库全量至V154并重复执行原脚本后，8类字典/27项数据、3项权限、0角色授权、1模板和1个PAUSED Job保持稳定，等待独立Gate。
- `6be558c4`的8类字典、27项字典值、三个按钮权限、站内信模板和初始暂停通知Job通过独立迁移证据复审并正式`GO`；最近实施单元为Task 10真实MySQL受控正向闭环。
- Task 10候选在独立MySQL 8.4空卷全量执行150个迁移至V154后，以CUT真实方案、审批、FactApi、MyBatis、平台幂等/审计/Outbox和通知服务完成6项闭环：A/B/C/D四级分别按4/3/2/2节点到P6并各形成唯一`CutoverApproved`事件；`FULL_FILE_UPLOAD`按精确三键内容冻结并完成P4提交、P5四节点审批至P6，不混入模板子行；驳回回P4后新修订重新审批，旧方案快照与评审历史保持不可变；多候选交集稳定选中唯一用户；待发通知经`src/test` SYSTEM发送替身全部推进SENT。联跑同时修复P4提交事务内审批冻结读取、方案联合分支及子表完整快照、wire-long身份和审批实例显式CAS。`9e812f9c`首轮复审仅指出上传方案联合缺口；`a7e5b7b3`关闭该唯一阻断并获独立正式`GO`。结果保持`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`，不作为生产Owner、真实浏览器或Implementation Done证据。

## 物理Owner支撑Task

- `T-FCUT005-PROJ-01`：PROJ拥有`ProjectCutoverServiceManagerFactApi`公开事实、锁定实现和合入顺序；当前仅预留合同，Provider未实施。
- `T-FCUT005-SYSTEM-01`：SYSTEM角色/成员/用户状态保持权威来源；当前`PermissionApi/RoleApi/AdminUserApi`不足以形成版本化锁定事实，Task自起点`BLOCKED_BY_DEPENDENCY`。只有SYSTEM物理Owner获明确授权并交付公开合同/Provider Gate后才恢复，禁止PMS平台自造版本、修改Yudao或直读其表。
- 两项Provider缺失不阻断Feature Ready后的CUT内核及`src/test`正向闭环，持续阻断完整生产装配、真实浏览器和Implementation Done。

## 依赖边界

- F-CUT-002/003/004为业务来源；当前允许在F-CUT-005单元/集成中使用已锁定合同的受控事实。
- PROJ当前服务经理与SYSTEM二线/研发候选均作为本Feature的物理Owner支撑Task预留正式端口，不建立纯Provider Feature；不得跨模块读表、修改Yudao或注册生产Fake/fallback。
- V2提前时间与外部通知、CUT-06闭环和`Q-FCUT004-001`均排除。


## 2026-10-20 用户批准的审批人固定规则修正

- 个人节点在每次提交审批时确定办理人；审批期间不因项目整体版本、成员范围或当前服务经理变化而撤销已分配待办。账号有效性、租户、审批权限及当前节点身份仍校验。
- 角色节点仅保存角色引用，不冻结成员名单。根据用户随后确认的恢复要求，共享待办恢复直接填写五项评审并提交，不再提供独立领取步骤；提交决定时锁定当前角色资格，在同一事务完成原生领取及审批决定，记录实际办理人并保护并发。既有已领取节点仍按固定办理人办理或改派，不改写历史。
- 已分配个人及已领取角色节点更换办理人，只能通过有权限的显式改派；保留来源角色、原审批人、改派人、原因和历史，停用账号不可办理。
- 驳回重新提交创建新的审批实例并重新确定该轮人员；既有实例、方案快照、评审、决定、改派历史不覆盖。
- 原“办理时重新核验项目版本/当前服务经理/已领取者实时角色资格”的断言由本次批准替换；非候选领取、抢领、跨租户、停用账号、越权决定及历史保护失败检查必须保留。

### 本次实现与验证边界

以下 141/35 项及显式领取的浏览器记录属于首次固定审批人修复版本；其后直接办理恢复版本的验证单独记录于本节末尾。

- CUT/BPM 共用固定分配事实；共享节点当前恢复直接办理，撤销独立领取 API 与页面步骤。既有已领取角色节点纳入既有改派及改派队列，来源角色保持不变。当前固定办理人的冻结材料只读权限随待办生效，不因原项目范围或角色变化被撤销。
- 定向 Java 回归实际通过 141 项（含原生 Flowable 13 项）；4 项需 `skipITs=false` 的 MySQL 正向闭环未启用。前端审批组件 35 项通过，包括领取后开放五项表单、领取成功后读取失败只重试读取。
- 已新增/更新驳回重提新实例、保留原决定与来源、服务经理/项目版本变化、领取前撤销资格、领取后移出角色、他人抢办、停用账号及显式改派的验证。
- V507 已经 Compose/Flyway 应用于 `npdms_domain_test` 并再次 validate；迁移前后 63 条终态节点及 225 条评审记录逐行内容一致。迁移只调整可领取节点的约束，不重写审批历史。
- 扩大选集时发现既有 `CutoverApprovalFactApiContractTest` 的枚举断言未包含 `WITHDRAWN`，本次未改动该无关断言；定向复跑排除该类后通过。
- 2026-10-20 经用户本次批准，59191 已部署冻结发布 `bc4073b9-981b-4e18-94ba-5f53f8fc9c2c`，保持 `local,dac`，未操作 DAC 48182。真实浏览器在审批实例 `2112471222878953472` 验证：服务经理通过（原 `APPROVER_FACT_STALE` 未复现）→ `w04287` 领取二线待办 → 重新打开仍固定为王薇 → 五项评审提交业务码 0。CUT 与 Flowable 办理人一致；服务经理、二线各新增五条评审，二线结束，研发共享待办生成；审批中心仅显示对应研发审核，无残留二线待办。未继续领取或办理研发节点。
- 上轮驳回实例 `2112466652169723904` 的实例行、4 条节点、15 条评审及改派历史，在本轮技术支持办理前后逐行内容一致。账号停用、角色成员变更与改派的负向/并发保护来自上述受控 Java/Flowable 测试，未改动真实账号角色来模拟。

### 2026-10-20 角色共享待办恢复直接办理

- 取消前次新增的独立领取接口、命令及页面按钮；二线和研发共享待办直接开放五项评审，提交决定时继续使用既有同事务资格核验、原生领取及审批决定链。
- 个人分配固定、已有领取分配及历史审批记录保留，未修改数据库或恢复旧迁移。既有非候选、重复提交、并发与通用 BPM 绕过保护继续生效。
- Java 定向回归 74 项通过（含原生 Flowable 13 项）；前端审批组件 35 项通过，其中二线和研发分别验证直接打开评审并一次提交完整五项数据。
- 经用户本次批准，59191 已部署恢复版本 `d7e1863b-dec1-482a-b6e1-f7274e9dd8dd`，部署预检及健康检查通过。真实浏览器从 BPM 待办办理入口复验当前研发共享节点：直接显示五项评审，无领取按钮；填写反馈后审批通过按钮启用，清空测试反馈后恢复待填写状态。刷新响应业务码 0，服务端动作是 `APPROVE/REJECT`。当前研发待办在 CUT/Flowable 均保持未分配、待处理，未提交研发审批决定；两轮审批的 2 条实例、8 条节点及 30 条评审在部署及界面复验前后内容一致。

### 2026-10-20 通用操作栏恢复与流程定义配置

- 需求方明确禁止修改 BPM 模块功能，允许调整流程定义文件。撤回整栏隐藏和临时业务按钮代理；通用详情页及操作组件恢复原实现，没有新增 BPM 扩展点或接口，其他流程继续使用原有办理方式。
- 仅在 `PMS_CUTOVER_APPROVAL.bpmn` 四个用户节点配置现有 `buttonsSetting`：关闭原生通过、驳回、转办、委派、加签、退回，保留抄送配置及原权限。割接通过和驳回仍在五项评审表单提交；服务端绕过保护不变，不扩大 `bpm:task:update`。
- CUT 完整审批投影新增有权限可见的“审批改派”入口，复用已有改派组件、接口、版本校验和写后刷新；未领取的角色共享节点不进入个人改派列表。只读、无改派权限及终态审批不显示该入口。
- 原生 Flowable 定向测试14项通过，覆盖四节点原生按钮配置、既有守卫和共享角色办理；前端审批组件37项通过，新增完整投影改派、权限与未领取角色排除检查；三个改动前端文件 ESLint 通过。证据：`.run/cutover-definition-toolbar-tests.log`、`.run/cutover-definition-frontend-tests.log`、`.run/cutover-definition-eslint.log`。
- 当前浏览器中的实例 `2112471222878953472` 已在研发环节驳回，原生审批意见和前三个已通过节点仍可见；当前没有可用于新定义办理的待处理节点。本次未提交审批决定、未发布流程定义，也未重启59191。定义文件变更尚未应用到运行环境；需通过既有模型管理发布新版本后，在新实例上进行按钮和办理复验，保留旧定义、旧实例和历史记录。

### 2026-10-20 获准发布v4及真实浏览器复验

- 上节“未发布”的记录为批准前状态。需求方随后明确允许发布并复验；在管理员正常登录后，通过既有模型编辑页导入文件、保存及发布，发布业务码0。新定义为 `PMS_CUTOVER_APPROVAL:4:11e4f961-cc73-11f1-b370-00ff0b78abd7`，模型、部署资源与工作树已验证的定义内容一致。本次未修改BPM通用功能，未重启59191或48182。
- 任务 `2112439350652284928` 正常驳回重提生成实例 `2112495949790027776`、流程 `aa5e7b82-cc73-11f1-b370-00ff0b78abd7`，绑定v4。真实BPM详情保留原生评论/抄送原权限；定义关闭六个不适用于CUT的原生按钮，五项评审及改派在CUT页面办理。
- 完整投影的改派入口实际完成发起人从管理员到周文成、再恢复管理员两次操作，CUT与Flowable实际办理人同步，追加两条原因明确的改派历史。随后管理员发起人、周文成服务经理（含P2等级复核）、w04287技术支持分别提交五项评审成功，新增15条评审；技术支持共享待办无独立领取步骤，无权限错误，研发共享待办生成。研发保持未分配待处理，未提交研发决定。
- 用户反馈改派界面风格及填写问题后，CUT组件移除手填节点序号、用户ID和重复明细表，改为有效节点选择、当前审批人姓名、可搜索人员选择、多行原因及取消/确认操作；同人、未知用户、无效节点、空原因、人员读取失败及忙碌状态阻止提交，失败可只重试人员读取。模态框挂到body，宽640px，窄屏单列。真实浏览器确认姓名搜索、同人禁选、取消、390px宽度下无横向溢出；前端相关78项通过，改派文件ESLint通过。
- 发布前29条旧实例、76条旧节点、255条旧评审作为对比基线。正常重提仅对被替代实例 `2112471222878953472` 追加 `replacement_approval_instance_id` 并将版本5递增到6；旧审批决定、来源快照、节点和评审未覆盖。证据：`.run/cutover-definition-history-before.json`、`.run/cutover-definition-browser-result.json`、`.run/cutover-approval-support-ui-final-tests.log`、`.run/cutover-reassignment-ui-eslint.log`。


### 2026-10-20 共享审批办理页补充资料越界请求修复（已部署复验）

- w04287打开实例2112495949790027776，审批详情读取业务码0；页面因具备普通任务查询菜单权限而额外请求原清单及匹配选项，被普通项目范围正确拒绝，出现“割接任务不存在”。问题为审批页混用了普通任务查询链，不是待办任务丢失。
- FULL审批响应追加只读sourcePresentation，服务端在既有审批授权成功之后，读取本次审批引用的同租户同任务清单、原配置及精确字段定义；不读取当前结果、不改项目范围、不修改BPM功能。说明读取失败保留审批事实并提示重试，受限视图和无权请求不读取元数据。
- 前端37项、后端34项通过，覆盖无普通项目范围的审批读取、定义版本不一致、跨租户/任务拒绝、失败降级及旧请求不能覆盖新审批；ESLint通过。证据：.run/cutover-approval-source-ui-tests.log、.run/cutover-approval-source-backend-tests.log、.run/cutover-approval-source-eslint.log。部署及浏览器闭环见下方本轮最终结果。

- 用户批准本轮重启后，冻结清单63cf0dc8-98bc-4822-9f65-4b147727da0b部署59191成功，既有22项修复基线与就绪检查通过，local,dac配置；未操作48182。真实w04287从工作台办理、刷新、完整重开均无任务不存在或元数据读取提示，无普通清单请求。审批业务码0，55项原字段说明完整返回，DPX框式设备正常显示。进入及重开时审批版本5、任务版本12、节点与冻结来源一致；研发待办保持未分配，三步已通过及15条评审保留，本轮未提交决定。详见docs/generated/2026-10-20-cutover-approval-source-presentation.md及.run/cutover-approval-source-browser-result.json。


### 2026-10-20 完整任务只读及上传方案在线预览（进行中）

- 用户批准审批人查看完整割接任务，并确认实际办理过的人员继续只读查看；未领取的角色成员仅当前共享待办有效。复用现有任务P2~P6组件及五项评审；历史实际决定按decisionActorUserId/原办理人、明确决定时间及APPROVED/REJECTED事实授权，不因角色变化或驳回重提覆盖历史。普通写权限、项目范围和任务负责人不变。
- 审批FULL页增加完整任务只读入口；任务、清单、方案、闭环读取逐次按租户和精确任务校验，附件复用PLT引用和版本授权。只读任务使用CUT保存的项目、设备及前序就绪引用，避免再次误调用普通Owner接口。后端approvalReadOnly与空写动作共同约束前端。
- 用户确认Word须保留分页、图片和版式，公共文件预览增加本机LibreOffice转PDF，复用同一文件组件，不向公共Office服务传文档或建立CUT独立预览页。原件、引用和批准快照不改写。已完成DOC/DOCX各两页及图片的实际转换检查，待最终构建与浏览器复验。

### 2026-10-20 审批页面提示精简

按用户页面标注，移除审批入口的“请在下方完成五项评审…”及公共审批组件的固定版本评审说明两条静态提示，随组件删除原占位，不影响错误反馈、状态和五项评审。两文件 ESLint 通过；真实浏览器打开审批2112495949790027776，确认两条提示消失，来源内容及审批记录正常显示。本次仅前端热更新，未重启服务或提交审批决定。

### 2026-10-20 服务经理改派候选与拒绝提示

用户确认 admin 将服务经理节点改给 admin 报 REASSIGN_TARGET_NOT_OPEN，并选择保留服务经理资格限制。新增精确实例的当前服务经理候选读取，管理员选择框仅显示PROJ当前唯一合格人选；无候选、读取失败禁止提交，失败可重试，不使用冻结旧人选或全部用户兜底。命令原服务经理资格锁定、版本、状态和历史规则不变；错误响应保留409、reasonCode、recoveryAction并显示具体中文原因。23项后端、40项前端及ESLint通过；补充管理员不合格目标零写入、合格目标追加历史的定向2项测试通过。日志：.run/cutover-reassignment-qualified-candidate-tests.log、.run/cutover-reassignment-qualified-candidate-ui-tests.log、.run/cutover-reassignment-admin-rejection-tests.log。用户仍选择暂不重启，未部署后端、未实际改派，运行验收未完成。

### 2026-10-20 割接详情统一底部操作栏

按用户要求，返回、刷新、修订从顶部及步骤正文移至唯一的底部操作栏；各步骤既有保存、下载、提交、审批决定和改派按钮通过同一组件挂载到该栏，权限、禁用条件、保存后离开及确认对话框不变。单独复用子组件且未提供操作栏容器时仍原位显示；统一刷新同时刷新当前审批内容。修订影响说明保留在确认对话框，不再另占步骤上方空间。

现有交互测试覆盖83项，其中首轮82项通过、受控全流程一项因测试宿主被响应式代理而残留旧按钮失败；操作栏宿主改用shallowRef后，受影响的完整P1~P6流程两项及详情14项通过，未降低断言。ESLint无错误（闭环组件既有4条属性换行警告）。真实浏览器926px及390px页面确认一条底栏，返回、刷新、修订、下载完整可见，顶部无操作按钮；闭环页面确认返回、刷新、保存、提交处于同一栏且保存按钮唯一，原提交禁用状态保留。本次只前端热更新，未操作后端或提交修订、审批、闭环结果。日志：.run/cutover-bottom-actions-ui-tests.log、.run/cutover-bottom-actions-integration-final.log、.run/cutover-bottom-actions-detail-final.log、.run/cutover-bottom-actions-final-eslint.log。

用户后续指定返回、刷新放左侧：底栏导航按钮组使用自动右外边距，其余修订和步骤动作保持右侧。真实浏览器审批详情及可编辑方案草稿均确认左右分组；详情与方案聚焦47项、类型和ESLint检查通过，记录见F-CUT-004同日增量。

### 2026-10-21 项目经理发起人节点缺少审批动作权限

- 项目992203060197的割接任务2112790614812622848、审批2112791534678016000停留在INITIATOR/PENDING，SERVICE_MANAGER/WAITING。真实浏览器当前发起人杨凯添读取审批成功，但allowedActions为空；当前个人分配和账号有效性未失效。只读数据库核对其PROJECT_MANAGER角色具有submit-plan/query-approval，但缺少approve；服务经理周文成具有approve，但尚未轮到该节点，不能代替发起人直接办理。
- 按既有F-CUT-005首节点五项评审合同，V510只为租户1有效PROJECT_MANAGER角色补齐现有pms:cutover-task:approve，不增加BPM通用办理、改派或项目数据范围，不修改个人固定分配、状态机和历史。真实数据库事务试验首遍新增1行、第二遍0行、回滚后0行残留。未执行Flyway迁移或实际授权写入。
- 复用固定工具链及工作树独占锁，审批查询、个人决定、角色决定三组共40项通过；包含无approve权限不显示动作、有权限当前发起人显示两项动作、发起人通过激活服务经理、幂等、驳回、终态和停用账号保护。日志：.run/cutover-initiator-permission-tests.log、.run/cutover-initiator-permission-migration-check.log。
- 当前浏览器为项目经理账号，无角色授权管理权限，已请求切换管理员以通过既有角色授权界面应用并复验；未提交该业务审批决定，未重启59191或48182。运行修复及浏览器闭环尚未完成。

#### 发起人授权生效及BPM待办复验

- 后续用户要求自行处理账号切换。确认有效会话仍为y06496，旧标签的管理员显示不能代表当前授权。通过项目既有Compose/Flyway入口只挂载V510，当前库从509迁移到510，实际新增1项授权，validate通过；不调用项目经理无权访问的角色管理写接口，不修改账号或项目范围，不清理登录会话缓存。
- 正常退出后使用用户提供的发起人凭据登录，取消“记住我”。真实审批页及BPM待办的“办理”入口均返回FULL和APPROVE/REJECT，五项评审完整可见，反馈填写后通过按钮启用。复验输入已清空，未实际提交审批决定。对应流程bf59a172-cd17-11f1-ad83-00ff0b78abd7、待办bf5ab300-cd17-11f1-ad83-00ff0b78abd7。审批仍为PENDING/currentNodeNo=1/version=0，两节点版本均0、评审0条；无需重启服务。证据：.run/cutover-initiator-permission-flyway-*.log、.run/cutover-initiator-permission-history-after.txt。

### 2026-10-21 管理员审批详情展示完整内容

- 用户明确要求审批详情显示完整内容。审批查询在既有租户及精确实例校验后，为同时具备query-approval、reassign-approval及该项目ACTION_VIEW的管理员返回FULL，复用既有项目、调研风险、等级、方案、历史记录组件；管理员未被指派到当前节点时仍没有通过/驳回动作。项目范围不足或仅有改派权限时继续返回元数据，不扩大项目数据范围；改派命令上下文及响应保持REASSIGNMENT_ONLY。
- 改派通过独立按钮及挂到body的弹窗进入，受限元数据页也先显示任务及节点状态，不再默认展示填写表单；沿用资格、版本、并发及历史保护。未修改BPM通用功能或另建审批页面版本。
- 前端审批及任务详情45项通过、两文件ESLint通过；新增管理员完整查看与无项目范围仍受限的后端检查，后端发布及管理员真实浏览器验证待后续执行。

- 后端共62项定向检查覆盖查询投影、控制器边界、附件授权、个人与角色决定。首轮新用例因既有Fixture模拟解码器默认返回null失败，补齐仅该用例的真实解码行为并显式声明重载参数类型后，查询14项最终通过，其余48项结果仍适用。没有降低来源字段断言或授权规则。证据：.run/cutover-administrator-full-read-tests.log、.run/cutover-administrator-full-read-query-final.log；前端证据：.run/cutover-initiator-admin-ui-tests.log。

- 完整后端构建及22项既有修复基线通过，冻结发布53b57e53-b505-41cd-bdfa-5c7c2f7512a3；worktree-backend.ps1 -ValidateOnly通过，保持local,dac，尚未部署或重启。日志：.run/cutover-administrator-full-read-build.log、.run/cutover-administrator-full-read-preflight.log。管理员完整内容运行验收等待本轮明确重启批准；实际发起人权限生效和BPM待办表单验证不依赖此次后端部署。

- 用户随后提供测试账号登录约定及管理员凭据，已通过正常登录页自动切换管理员，关闭“记住我”，未将凭据写入文件。真实管理员页面确认受限响应先显示任务和节点状态，点击“审批改派”才打开独立弹窗，取消后关闭，未执行改派。完整冻结内容仍等待上述后端包部署，不将账号切换指令视为重启批准。

#### 获准部署及真实浏览器复验完成

- 用户明确批准本轮重启后，worktree-backend.ps1部署冻结发布53b57e53-b505-41cd-bdfa-5c7c2f7512a3成功，59191健康，PID43828，保持local,dac；未操作DAC48182。证据：.run/cutover-administrator-full-read-deploy.log。
- 真实管理员读取当前D级审批2112791534678016000，业务码0、FULL，项目、等级、上传方案与审批记录完整显示；allowedActions为空，未因管理员查看获得通过/驳回动作。审批改派按钮打开独立弹窗，取消关闭；项目992203060197内嵌割接详情也显示完整资料，无默认改派表单。
- 真实管理员读取A级审批2112495949790027776，业务码0、FULL，47项风险考察、8项业务调研及在线方案全部章节可见，无来源读取错误、无决定按钮，原历史内容未重写。
- 自动正常登录发起人y06496，从BPM待办列表进入流程bf59a172-cd17-11f1-ad83-00ff0b78abd7、待办bf5ab300-cd17-11f1-ad83-00ff0b78abd7。审批返回FULL及APPROVE/REJECT，五项评审可填写，反馈填写后通过按钮启用；测试内容已清空，当前登录保留发起人账号。
- 全程未提交实际审批决定或改派。当前实例仍PENDING/currentNodeNo=1/version=0，发起人PENDING、服务经理WAITING，两节点版本0、决定时间空、评审0条。管理员完整读取和发起人可办理均已运行验证；实际业务决定由指定办理人完成。证据：.run/cutover-administrator-full-read-browser-result.json、.run/cutover-administrator-deployed-history-after.txt。

### 2026-10-21 服务经理按项目权限查看割接审批

- 真实周文成z01010在项目992203060197内打开割接2112790614812622848，任务读取成功但审批请求404/APPROVAL_NOT_VISIBLE，页面显示审批数据无法读取。当前实例2112791534678016000停留发起人，服务经理节点等待；此前完整读取限定当前审批人、发起人或管理员，误把项目查看与待办身份耦合。
- 用户明确澄清：具有项目权限的服务经理理应从项目查看割接，不只从待办。最终规则为query-approval与该项目当前ACTION_VIEW共同授权FULL冻结内容，审批中及终态均适用，不依赖是否在审批人名单、节点顺序或待办关系；原当前办理人与实际办理历史的独立读取资格保留。仅改派权限者仍为元数据投影。没有新增角色授权、扩大项目范围、改BPM或迁移数据。
- 决定动作继续按当前有效节点和固定办理人校验。新增未分配项目查看者在PENDING/APPROVED/REJECTED读取完整快照且无决定动作、等待中的服务经理只读、无query-approval、无项目范围及跨租户拒绝；补充服务经理提前通过/驳回均NOT_CURRENT_NODE，节点、评审、通知及阶段历史零写入。最终52项后端定向检查通过，含查询20、控制器9、决定12、附件策略11。日志：.run/cutover-project-view-approval-tests.log；真实修复前响应与记录：.run/cutover-project-view-approval-before.json、.run/cutover-waiting-reviewer-history-before.txt。
- 按用户澄清取消仅限已分配等待节点的中间构建，未发布该中间产物；最终代码已重新完整构建中。服务经理真实修复后验收尚待本轮部署，不将前次管理员/发起人浏览器结果用于代替此场景验收。

- 最终完整构建成功，22项既有修复基线通过，冻结发布86e9c07b-1cb6-43fa-af92-9ec8a2f0f50b；worktree-backend.ps1 -ValidateOnly通过，local,dac不变，未重启59191或操作48182。本轮部署需新的明确重启批准。日志：.run/cutover-project-view-approval-build.log、.run/cutover-project-view-approval-preflight.log。

#### 获准部署及服务经理项目入口复验完成

- 用户明确批准本轮重启后，部署冻结发布86e9c07b-1cb6-43fa-af92-9ec8a2f0f50b成功，59191健康，PID37620，保持local,dac；未操作48182。日志：.run/cutover-project-view-approval-deploy.log。
- 真实服务经理周文成z01010直接从项目992203060197任务树打开割接2112790614812622848，未经过BPM待办。原审批404/APPROVAL_NOT_VISIBLE恢复为HTTP200/业务码0/FULL，项目、等级、上传方案及审批记录完整显示；当前D级按既有规则跳过调研，不伪造不存在的调研资料。
- 项目内局部刷新再次HTTP200且无审批读取错误；在用户原855px窄屏项目标签页完整重载、通过“选择任务→S4→割接上线”重新打开，也返回FULL且正常显示方案及记录。原错误页面已恢复为正常项目内割接详情，当前服务经理会话保留。
- 三条路径的allowedActions均为空或决定按钮不存在，服务经理仍等待、发起人仍审批中，未执行实际审批或改派。复验前后实例、节点状态/版本、决定时间及评审条数逐行一致。证据：.run/cutover-project-view-approval-browser-result.json、.run/cutover-project-view-history-preacceptance.txt、.run/cutover-project-view-history-postacceptance.txt。

#### 发起人与服务经理实际审批闭环复验

- 用户明确授权继续用发起人和服务经理提交本条审批。通过正常登录自动切换y06496、z01010，均取消“记住我”；从真实BPM待办的“办理”进入割接专用五项评审，未调用通用通过接口或直接改写业务状态。
- 发起人杨凯添于2026-10-21 18:39:53提交成功（HTTP200/业务码0），INITIATOR变为APPROVED，SERVICE_MANAGER由WAITING进入PENDING；五项评审保存5条。发起人待办重开为空，BPM新建服务经理任务c04863be-cd3b-11f1-abb2-00ff0b78abd7，办理人固定为周文成。
- 服务经理周文成从自己的待办进入，完整资料及发起人记录可见；五项评审与P2等级合理复核于18:40:58提交成功（HTTP200/业务码0）。审批2112791534678016000变为APPROVED，两节点均APPROVED，实际办理人、反馈、时间及10条评审明细保留；服务经理待办中本流程移除，另一条无关待办保留。
- 只读核对Flowable：两项历史任务均正常结束，流程bf59a172-cd17-11f1-ad83-00ff0b78abd7于18:40:58结束，当前运行任务为0。割接2112790614812622848同步进入P6/CLOSURE_IN_PROGRESS/version4；未保存或提交闭环数据。
- 重开独立审批页及用户原窄屏项目页，均可见已通过状态、两位办理人的反馈及五项明细，完整内容读取正常，下载方案按钮保留。当前登录保留服务经理；本轮无需修改功能代码、重启服务或改派。证据：.run/cutover-initiator-manager-flow-before.txt、.run/cutover-initiator-manager-flow-after-initiator.txt、.run/cutover-initiator-manager-flow-after-manager.txt、.run/cutover-initiator-manager-flow-result.json。

### 2026-10-21 审批记录姓名不受用户下拉数据范围影响

- 真实服务经理z01010读取审批2112791534678016000时，通用/system/user/simple-list成功但仅返回周文成本人，无法解析发起人杨凯添，页面回退为“用户2108539871029019609”。编号为正确字符串，并非精度丢失。
- 审批Controller在既有授权投影成功后，复用SYSTEM AdminUserApi按该投影节点的currentApproverUserId去重批量读取昵称，FULL及改派元数据节点增加currentApproverName；仅返回授权节点关联姓名，不查询全量人员，不增加权限或更改通用用户列表范围。历史及冻结快照不写入；缺失或Owner读取失败保留原审批事实及编号回退。前端共享审批组件优先使用该展示字段，项目内嵌与独立审批入口共同生效。
- 后端41项定向检查通过（Controller12、查询20、角色查询9），包含授权拒绝不读取人员、限定节点人员、姓名缺失/故障保留事实、未指派共享角色/仅结果视图不查询用户。前端43项通过，包括人员列表仅本人或加载失败时仍展示授权姓名、元数据展示不增加决定按钮；修改文件ESLint通过。证据：.run/cutover-approval-participant-name-before.json、.run/cutover-approval-participant-name-tests.log、.run/cutover-approval-participant-name-ui-tests.log、.run/cutover-approval-participant-name-eslint.log。
- 后端冻结包构建、部署预检与部署后真实浏览器复验尚待完成；目前运行后端仍未包含此增量，本轮未重启59191或48182。
- 首次完整编译成功，但构建期间另有设备采集相关源文件及前端依赖清单变更，worktree-build一致性保护拒绝冻结，未产生发布包。保留外部改动，正在基于最新完整工作树重新构建；不降低来源校验或使用该次未冻结产物。
- 第二、三次完整编译仍成功，发布均因构建期间设备采集源文件持续变更而被来源一致性校验拒绝；期间另一次启动因共享构建锁占用直接退出。未使用其他会话产物，未降低校验或修改并行文件；三次均无可部署的冻结清单，未执行部署预检或重启。日志：.run/cutover-approval-participant-name-build.log、.run/cutover-approval-participant-name-build-retry.log、.run/cutover-approval-participant-name-build-final.log。当前代码及定向验证完成，实际页面修复待工作树稳定、重新冻结并按本次批准部署后复验；已请求确认并行设备采集开发是否结束。
- 用户澄清DAC独立包不应影响平台打包。审计确认编译入口本已分离，错误来自源码指纹扫描整仓库。按Target改为各自Maven工程声明的模块POM/源码/资源，加共享工具链与配置；DAC另含实际复制进JAR的Web dist。平台内DAC适配代码仍属于平台指纹，构建锁、冻结包哈希、运行基线及既有修复校验不变。新增6项失败/隔离检查先在旧实现失败，修复后全部通过，覆盖两工程互不影响、前端依赖清单不影响Java包、共享工具仍影响两者、平台源码/资源变更仍拒绝、嵌套模块及缺失模块失败关闭。日志：.run/worktree-artifact-scope-before.log、.run/worktree-artifact-scope-tests.log。
- 修正构建目标范围后，平台完整构建与22项已部署修复基线校验通过，成功冻结发布2c47d182-04cf-4eb4-95d1-4b89691db306，sourceScope=backend-maven-reactor-v1。worktree-backend.ps1 -ValidateOnly通过，local,dac不变；不再等待独立DAC源码稳定。平台自身源码变更、共享构建工具、产物及运行基线校验仍生效。本轮尚未重启59191或48182，审批姓名的真实修复后复验仍需本次部署批准。日志：.run/cutover-approval-participant-name-build-scoped.log、.run/cutover-approval-participant-name-preflight.log。

#### 获准部署与姓名显示浏览器复验完成

- 用户明确批准本次重启。首次部署在备份前因E盘空间不足退出，原服务未停止；在共享构建锁内核对target临时JAR与其已冻结副本b858dc87-7f95-4f9e-938a-6ee96fe45d70完全一致后，仅删除可再生成的target临时副本，保留全部冻结发布包、备份、源文件及业务数据，释放229359680字节。未使用该副本替代本轮产物。
- 继续同一次已授权部署，worktree-backend.ps1部署本轮冻结发布2c47d182-04cf-4eb4-95d1-4b89691db306成功，59191健康，PID46496，保持local,dac；未操作48182。证据：.run/cutover-approval-participant-name-space-recovery.json、.run/cutover-approval-participant-name-deploy.log、.run/cutover-approval-participant-name-deploy-retry.log。
- 真实服务经理z01010重开独立审批2112791534678016000，HTTP200/业务码0/FULL，发起人显示杨凯添、服务经理显示周文成；通用用户列表仍只返回周文成本人，证明未扩大用户列表范围，审批姓名已与下拉候选列表解耦。
- 在用户原项目992203060197页面完整重载，从S4割接上线打开审批历史，两个姓名同样正确，用户编号回退及审批读取错误均消失；下载方案按钮保留。两个入口均APPROVED、allowedActions为空，未增加终态决定权限。
- 浏览器复验前后只读核对审批根、两节点的状态/办理人/版本/决定时间、10条五项评审和本流程0条运行待办，逐行完全一致。本轮未提交审批、改派或闭环数据；保留原项目页及服务经理会话，关闭临时复验页。证据：.run/cutover-approval-participant-name-browser-result.json、.run/cutover-approval-participant-name-history-before.txt、.run/cutover-approval-participant-name-history-after.txt。
