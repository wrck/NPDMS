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
