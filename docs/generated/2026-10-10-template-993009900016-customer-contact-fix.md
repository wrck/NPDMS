# 模板 993009900016 用户联系人绑定修正

2026-10-10 按用户要求，通过现有模板设计器修改并保存 Designer 草稿。环境：前端 localhost:19191，后端 10.210.0.11:59191。模板名称为“项目交付全流程V2”，编码 TPL-DELIVERY-FLOW-20260924。

- S1 的 PRE_CONTACT（用户联系人获取）原错误绑定 PROJ_PROJECT_ASSIGNMENT / PROJECT_ASSIGNMENT，完成规则误用 PROJECT_ASSIGNMENT_COMPLETED。
- 办理入口改为 PAGE，routePath=/customer-asset/customer-contact，复用已有支持 projectId 的客户联系人工作台，维护项目关联客户的联系人。
- 按既有人工提交设计，任务改用独立 TASK_NATIVE_STATUS / requiredStatus=DONE 完成规则，避免人员指派导致联系人任务自动完成。原规则保留，其他节点不受影响；没有新增客户联系人自动完成事实。
- 保存后读取服务端 draft 响应确认 code=0；前后差异仅为 PRE_CONTACT 的 workBinding、completionRuleKey 及新增独立完成规则。其他任务、阶段、权限、准入和退出条件未变。
- 真实浏览器 Compiler 预检返回 code=0、valid=true、issues=[]，页面显示预检通过。

修改只保存为草稿，未发布，未改已有项目运行快照或历史版本。因此本次验证覆盖模板配置保存和编译预检，不声称已在项目任务运行中生效或完成联系人业务验收。未修改应用源码，未重启服务，未提交或推送 Git。

## 已有项目运行版本修复

同日用户授权修复已有项目运行版本。通过现有 `/api/v1/pms/projects/{projectId}/plan` 创建项目独立草稿、保存、影响预览和正式生效，不直接写表，不发布公共模板，不覆盖原有效版本。认证沿用本地已保存会话并通过正式刷新接口续期，凭据仅用于内存请求，修复脚本与回执不保存凭据。

- 共20个关联项目：13个ACTIVE、7个已关闭。所有项目原联系人任务均绑定 PROJ_PROJECT_ASSIGNMENT。接口确认无既有项目草稿。
- 9个项目正式生效到独立计划v2：992203060177、992203060180、992203060181、992203060183、992203060184、992203060185、992203060187、992203060188、992203060197。唯一直接节点修改为 task:PRE_CONTACT；所属及下游节点由正式预览列为重新判定，没有交付件、里程碑或门禁定义修改。
- 生效命令逐个携带完整最新expectedPreview，由服务端重验版本与影响；逐个回读有效计划确认客户联系人PAGE绑定及独立TASK_NATIVE_STATUS完成规则。原v1仍保留，未直接推进任务状态。
- 真实浏览器打开项目992203060197的S1用户联系人获取：实际渲染客户联系人工作台，客户为中国联合网络通信有限公司吉林省分公司，列表提供客户编号、客户名称、姓名、部门、职务、主联系人等字段，显示暂无联系人。S1尚未准入，新增及载入按钮保持禁用；没有为了验收推进阶段或创建业务联系人。本次浏览器验证覆盖运行入口及项目客户上下文，不声称完成联系人新增/提交闭环。
- 4个ACTIVE项目的联系人任务已DONE，改版预览返回STARTED_BINDING_IMMUTABLE：992203060037（PJT2026000030）、992203060190（16200722010404N-0）、992203060198（16200723010901N-0）、992203060199（PJT2026000054）。已保存修复草稿，未生效，原运行版本与完成历史保留。
- 只读返工预览显示上述4个项目可以为PRE_CONTACT新建执行轮次；992203060198还须为已完成的stage:S1新建轮次。预览均无blockers；尚未执行返工，需确认是否重新打开这些已完成任务及该阶段后再生效修复。
- 7个已关闭项目保持只读：992203060189、992203060191、992203060192、992203060193、992203060194、992203060195、992203060196。

本地明细回执：`.run/template-contact-plans-audit.json`、`.run/template-contact-plans-preview.json`、`.run/template-contact-plans-apply.json`、`.run/template-contact-rework-preview.json`。只读数据库复核确认9个项目有效计划为v2，其他项目仍为v1；PRE_CONTACT原状态和起止时间保持不变。未重启服务，未提交或推送Git。

## 已完成任务的事务修复准备

用户随后明确确认4个项目的选择性返工，以及992203060198的S1阶段返工。执行前发现：返工原接口提交后立即异步重判，错误的PROJECT_ASSIGNMENT_COMPLETED规则可能抢先把新轮次再次完成，不能依赖两次独立请求的时间差。尝试仅先替换完成规则、保留旧业务绑定的分步预览，被编译器以OWNER_FACT_REQUIRED / RULE_NATIVE_BINDING_UNAVAILABLE拒绝；没有执行返工或计划生效，4个修复草稿已恢复正确PAGE绑定，版本为3。

新增 `ProjectPlanReworkActivationService` 与独立 `ProjectPlanReworkController`，保留原返工、生效入口及编译器不变。新接口为 `POST /api/v1/pms/projects/{projectId}/plan/draft/{draftId}/rework-apply`，先核对完整修复预览及原计划/项目版本，只暂存选中节点binding的已开始不可变问题，再调用原返工服务、重新编译并调用原计划生效服务，外层事务整体提交或回滚。双权限和当前项目范围均继续生效，幂等重放不再次返工，重判事件只在整个事务提交后派发。契约已登记到 `docs/design/10-api-design.md`。

固定JDK25、Maven3.8.6、工作树依赖库与互斥锁下，4组定向测试共46项全部通过：事务修复12项、原返工服务11项、原计划生效10项、原影响分析13项。新增隔离H2事务测试确认生效失败回滚返工，并确认两个提交后事件均只能看见修复计划及新轮次；没有将测试写入真实项目数据库。

4个正式请求的完整修复预览、返工执行版本、原因及固定幂等键已在请求前保存至 `.run/template-contact-atomic-rework.json`，状态为PREPARED，尚未发出修复写请求。已结束轮次的原结果、计划、起止时间和提交证据只读基线保存在 `.run/template-contact-history-before.jsonl`，用于修复后逐条比对。发布构建与部署校验完成前不重启后端；当前4个项目仍使用原运行版本，9个此前修复项目继续使用独立计划v2。

19:29发布包已冻结，清单为 `.run/build/releases/e737a14a-9bcd-46e6-806c-48e111e0aa28/manifest.json`。构建前后源文件清单一致，22项已部署修复字节基线全部保持。通过 `scripts/worktree-backend.ps1 -Manifest <上述清单> -ValidateOnly` 完成冻结包、当前运行包及local,dac配置预检，输出明确为no service changed。前两次编译均成功，但其他验收流程分别写入flow-result.json与final-result.json，冻结保护拒绝发布；第三次稳定构建正常冻结，没有降低或绕过保护。

上述19:29节点为历史准备状态，后续执行结果如下。

## 授权重启与运行修复结果

用户明确授权“安全修复完成后运行重启”。联系人事务修复与割接审批安全适配完成后，使用当前工作树入口构建并冻结联合发布清单 `.run/build/releases/f43cd7b8-a9df-45a4-aae8-dde842d6a11e/manifest.json`，22项已部署修复基线保持。ValidateOnly通过后，以ApprovedRestart重启59191，运行配置local,dac，PID19724，backend healthy。日志 `.run/contact-cutover-backend-build.log`。DAC未执行启停，独立readiness返回HTTP200；本次未执行文档解析业务。

- 4个已完成任务的事务返工及修复计划均正式生效：992203060037、992203060190、992203060198、992203060199。PRE_CONTACT新建第2轮，992203060198的S1也新建第2轮；只读回查5个新轮次均ACTIVE，没有被旧项目指派事实自动完成。已有9个修复项目合计13个ACTIVE项目完成联系人绑定修复，7个关闭项目未改。
- 第一个请求已生效，初次回读脚本因服务端显式返回null可选字段而误报，修正脚本比较逻辑后使用原幂等键重放确认，没有重复返工。992203060190的旧预览因其他节点实际完成导致版本变化被服务端拒绝，没有产生返工；复核有效计划、原草稿版本及授权范围不变后，重新生成剩余3个最新预览并生效。旧请求证据保留 `.run/template-contact-atomic-rework-superseded.json`，最终回执 `.run/template-contact-atomic-rework.json`。
- 修复前23条已结束执行记录按id逐条比对，原计划、状态、轮次、起止时间、提交字段及result_snapshot全部一致。基线及结果 `.run/template-contact-history-before.jsonl`、`.run/template-contact-history-after.jsonl`；原完成历史未覆盖。
- 真实浏览器打开992203060198的用户联系人获取，显示进行中及第2轮对应新开始时间；工作台固定客户为中国移动通信集团吉林有限公司，显示客户联系人字段，新增/载入按钮可用，项目目前没有联系人。没有代用户新增联系人、提交任务或推进其他阶段。
- 992203060198随后通过独立正常计划修订改绑割接审批，有效计划为2104870198458802217；PRE_CONTACT的PAGE入口及native完成规则保留。公共模板只更新草稿，未发布。

验证边界：46项联系人定向测试通过；已在真实浏览器验证客户上下文及办理入口，没有执行真实客户联系人新增/提交业务。未提交或推送Git。
