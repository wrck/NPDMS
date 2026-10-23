# F-CUT-002 割接任务接入与人工分级

> Feature实施状态：`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO`（锁定基线`cad8088a`）
> Technical Plan Gate：`PASS / GO@8eb36222`
> Implementation Done Gate：`BLOCKED_BY_DEPENDENCY`
> `pms_cut_task -> cut_task` Migration Contract Gate：`PASS`（`36d1b37f`）
> API/Physical Machine Contract Gate：`PASS / b7f49166`
> Requirement：`CUT-01@V1=PARTIAL；CUT-02@V1=PARTIAL`
> Feature Spec：`specs/features/F-CUT-002-cutover-intake-and-manual-assessment.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-08-31-f-cut-002-cutover-intake-and-manual-assessment.md`
> master集成映射：`codex/f-cut-001-matrices@faed8387 -> master代码回执c9066332；来源PLT V144/CUT V146/V149 -> master V176/V177/V180`
> master复验：`PLT 28项（跳过MySQL 4）、CUT API 6项、CUT 242项（跳过MySQL 27）、CUT前端68项均零失败；生产Owner、真实MySQL与真实浏览器未在本DU复验，状态保持IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY`
> 外部硬依赖：PROJ、IMP、AST、CUS、PLT生产Provider（仅阻断生产装配、真实浏览器和Implementation Done）
> 既有复用基线：`F-PROJ-003`已交付的`ProjectScopeApi`；其余跨模块能力仅预留消费端口并在`src/test`受控模拟，不由CUT实现Owner Provider

## 当前最小工作单元

- Task 1：`PASS / COMPLETE@9b1a613e`（独立整体状态关闭`GO`）。
- Task 2：`PASS / GO@d80ace31 / IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY`。CUT自有新工作台、创建向导、P2人工分级和P4只读阶段投影已通过独立复审（`9f791d64 + 0c18ed0f`）；真实Spring事务、MyBatis、平台幂等/审计与MySQL 8.4下的受控Owner正向链覆盖A级进入P3、D级进入P4及提交重放，并通过独立Backend/MySQL Gate。生产Adapter、唯一应用装配、真实浏览器及Implementation Done继续等待PROJ/IMP/AST/CUS/PLT正式Owner事实。

## 已完成

- 接受独立裁决：EXE-06拆为独立IMP Feature；CUT-01与CUT-02覆盖均为`PARTIAL`。
- 已固定一线自建P1→P2问卷人工判级→A/B/C进入P3、D进入P4的最小业务闭环。
- 已明确来源幂等、活动设备范围唯一性、IMP快照重验、权限、API、数据和UI边界。
- 已完成本Feature的后端、前端、配置、运行数据/迁移、状态机、权限和测试复用审计；结论为`CURRENT_FORWARD / COPY_THEN_ENHANCE / PRESERVE_LEGACY`。
- 已纠正CUT物理Owner为`cut_task/cut_assessment`，并将`CutoverAssessment`与旧`pms_cut_risk`解耦为`NEW_ONLY`。
- `pms_cut_task -> cut_task`字段、旧状态只读化、完整性资格和不可迁行处置的机器合同已在`36d1b37f`通过独立迁移Contract Gate；旧类型/组网仅存legacy raw，新路径事实保持空，Owner暂时失败与确定性不匹配分流，PLT批次事务和11组生成投影已锁定。
- 独立裁决已确认F-IMP-003～005未Ready不应永久阻断CUT；跨模块接口冻结后，Feature Ready可允许CUT在非生产装配中使用受控模拟完成自身正向闭环。
- ITR/项目事件Producer、P3以后、V2/V3、自动指派和通用工单动作均排除。

## 依赖边界

- F-CUT-002完整API/物理机器合同已通过，不再阻断Feature Ready。
- AST的`DeviceScopeFactApi`公开合同、Owner Provider及IMP消费适配已分别通过独立Gate；该项不再是F-CUT-002规格阻断，但生产装配与真实依赖闭环仍按各Owner任务状态判定。
- `ImplementationReadinessApi`与CUS `CustomerServiceLevelFactApi`公共机器合同均已通过。生产Provider继续只阻断生产装配、真实浏览器和Implementation Done，不授权CUT重复建设。

## 验收分层

- CUT单元/集成测试可使用受控`ImplementationReadinessApi`替身验证消费边界。
- CUT隔离真实MySQL单元/集成可使用`src/test`受控正向模拟；真实浏览器、生产装配和Implementation Done必须使用生产Owner事实，替身、手工SQL、附件或测试种子不得替代。

> 检查点：Task 2受控正向闭环Backend/MySQL Gate已在`d80ace31`独立复审`PASS / GO`；隔离MySQL 8.4空卷执行152个迁移至V156后2/2通过，另有CUT-002应用正向单元3/3通过。当前最近Gate为生产Owner依赖满足后的Adapter与唯一装配Entry Gate；Feature保持`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY`，不得用替身形成生产装配或真实浏览器证据。


### 2026-10-21 后端待办及统计读取优化

- 用户要求继续优化后端耗时并保留权限和业务判定。待办分类和统计改为调用各阶段的动作存在性读取，不再为判断动作组装完整页面DTO。任务、方案、闭环均与详情共用原有租户/项目/精确审批可见性读取；每次仍读取当前范围，不缓存授权，不改Mapper、SQL、角色共享待办范围或分页语义。
- P3继续使用原allowedActions和修订状态机，只省去该阶段动作判定不用的设备、实施和展示快照。P2保持完整来源及资格判定。P4通过原CutoverP4ActionPolicy先判断新建/保存是否已足以进入待办；仍保留审批事实身份核验。仅需提交/修订才能进入待办的情况继续走原来源可比、完整性、审批、后继修订判定，DOWNLOAD_DRAFT不计入待办。完整详情和真正保存/提交/改派接口保持原业务规则。P6保留原来源、证据和完整性判断，只省去展示内容/证据DTO映射。统计分组仍保留范围越界、重复候选及空范围拒绝规则。
- 9组定向后端检查51项通过；另在用例内完成P3 192组、P4 64组、P6 8组，共264组原详情动作与新待办资格等价对照，并覆盖P2、完整方案仅提交、来源变化、驳回/来源失效重提、租户/项目越权、审批只读及权限变化。性能回归断言确认P3不读取展示快照，P4保存资格不调用来源完整性、步骤、保障及生成文件查询。首轮仅新测试中已无用途的readiness模拟触发严格mock错误，删除该无用模拟后全组重跑通过，未放宽业务断言。证据：.run/cutover-backend-performance-tests.log、.run/cutover-backend-performance-retest.log。
- 部署前在当前真实浏览器记录同一账号工作台：统计约1014ms、待办约1077ms、自建约240ms；冻结待办及自建第一页编号、总数和KPI供部署后对照，见.run/cutover-backend-performance-before.json。本段不把未部署代码记为运行性能改善，也不覆盖此前页面优化证据。
- 构建前E盘仅约1MB，持有工作树锁核对所有Java运行工件后，将未被任何进程作为运行工件的yudao-server/target/yudao-server.jar备份移至C:/Users/user/AppData/Local/Temp/npdms-build-output-9779fe0c-b8a9-4b00-923c-02da4c69ad35/yudao-server.jar，未删除冻结发布、历史或当前运行包。另一构建占锁时等待其结束，未绕过互斥锁；随后沿用固定工具链和m2完成定向测试。后续部署仍需本次明确重启批准。
- 完整后端构建成功，冻结发布为`.run/build/releases/bc090e39-e771-442f-bdee-2bbfbb903660/manifest.json`，22项已部署修复保护及ValidateOnly预检通过。用户随后明确批准“允许重启并复验”，通过`worktree-backend.ps1 -ApprovedRestart`部署成功，59191健康、PID 28592；48182保持原进程17780监听，未启停DAC。部署日志：`.run/cutover-backend-performance-deploy.log`。
- 同一管理员账号、相同默认分页在真实浏览器连续刷新三轮：TODO响应头耗时694.276/859.081/618.122ms，中位694.276ms，相比部署前1077.198ms降低约35.5%；统计647.292/807.572/571.538ms，中位647.292ms，相比1014.325ms降低约36.2%。自建列表218.733/261.206/202.989ms。三轮均200，TODO总数11、自建总数26及各自第一页10个编号/顺序与基线一致，统计11/17/3/0一致；仅统计生成时间按新请求变化。本地单账号三轮样本不作为生产容量结论。证据：`.run/cutover-backend-performance-after.json`。
- 浏览器另只读打开P3任务2112435383465652224，36项调研、已有图片及保存/下一步操作正常；P4任务2109853246593855488在线标准草稿、原附件、已有设备及保存/生成操作正常；P6任务2111084153208291328归档结果、附件版本和只读状态正常，无保存操作。未保存或提交业务数据，已关闭本次自建验收标签页，保留用户原标签页。未运行MySQL集成测试，不将受控测试或当前浏览器样本写成全量生产验收。
