# F-IMP-005 业务联调配置收集

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY_BLOCKED`
> Feature Ready Gate：`NOT_READY`
> Requirement：`EXE-04@V1=FULL`
> Feature Spec：`specs/features/F-IMP-005-joint-debugging-configuration.md`

## 2026-10-27 TF-043 项目设备候选分页修复

- 旧功能范围：需求方要求继续跟进现有测试反馈，仅修复现有联调/配置等页面共用的专用ProjectDeviceSelect候选分页，不实施尚未Ready的联调配置收集新Feature。
- 旧实现审计：专用选择器调用既有archive-page(selectionProjectId)，服务端按当前项目及授权合同限定候选；其PmsEntitySelect宿主固定请求第1页50条，没有翻页入口，超过50条无法浏览其余设备。现有服务端范围和校验不改动。
- 最小实现：在专用ProjectDeviceSelect内承接分页和加载更多，沿用原props/事件和SN搜索；保留大ID字符串、显示查询失败、丢弃旧项目/关键词的晚到响应。通用PmsEntitySelect及其他实体选择器保持原行为。
- 定向验证：先复现分页、晚响应等5项失败；修复后10项运行时用例通过，覆盖130条末页、搜索复位、失败原页重试、空中间页、项目切换晚响应、19位ID精度及无效项目拒绝全量查询。定向vue-tsc与ESLint通过；记录为.run/feedback-20261026/tf043-selector-before.log、tf043-selector-after.log。
- 真实浏览器：19191/m06425在替代项目84459的新增联调入口完成50→100→130条加载，候选与项目档案130个ID集合一致；实际选中第51台和末台，modelValue保持字符串且SN正确。两次精确SN搜索各返回HTTP200/code0/唯一正确设备，旧候选不残留；取消表单，无业务写请求。证据.run/feedback-20261026/acceptance/tf043-current-browser-acceptance.json、tf043-cancel-no-write-check.json。仅此候选分页缺陷闭环，不晋级整个Feature；原反馈的具体缺失项目/设备仍需样本确认其他范围差异。本次未重启后端或执行数据变更。

## 2026-10-28 TF-043 业务联调设备按型号检索

- 旧功能范围：继续处理已批准的业务联调设备选择反馈，不实施未Ready的新Feature。`joint-test/index.vue` 与 `with-device-logs.vue` 共用的 `ProjectDeviceSelect` 原仅传SN；实际HTTP调用 `ProjectDeviceSelectionService.getPage→selectSelectionPage` Lambda分页，不能仅修改闲置XML。按需求方最终澄清，单搜索框输入SN或型号，不增加检索方式开关；本轮早期增加的开关及其测试已撤回，原50条分页修复保留。
- 最小修复：既有archive-page在selectionProjectId场景增加可选keyword，透传场景Query后使用已有XML候选分页，在项目/授权合同范围内整体AND括号包裹的SN OR当前型号条件；不以两次前端查询拼接或全量过滤。后续真实慢栈证明空keyword保留旧入口仍有瓶颈，故本次最终统一全部业务候选到UNION XML；无selectionProjectId普通档案入口不变，既有独立筛选继续AND收窄，无keyword独立productModel保持设备原字段语义。候选标签显示实际型号，单框更换关键词复用分页复位、晚响应丢弃及选中大ID保留。SDS10已补充最终字段/路由与≤1秒整页指标。
- 主档审计与优化：当前测试库已核实 `uk_product_official_tenant_code(tenant_id,product_code)` 唯一键、35004条主档无重复/首尾空格，79557/84459分别232/130台设备含DPPMS来源型号；直接按唯一主档键读取，保留设备原非空型号优先、设备编码trim、租户/删除及历史只读。共享当前型号SQL移除GROUP/MAX/COUNT/CAST及固定CRM/ACTIVE；冲突于本次明确规则的旧聚合/来源断言改为实际唯一约束拒绝重复键，保留原值/无写入、历史锁定、租户、范围和缺值失败断言。证据 `.run/feedback-20261028/acceptance/tf043-product-index-readonly.json`、`tf043-query-perf-readonly.json`；代表SQL为毫秒级，不能据此宣称解决整页慢或证明≤1秒。
- 定向验证：新增实际路由/关键词过滤先复现2项失败，修复后AST真实Mapper、服务、共享型号SQL及只读API共25项通过（含真实租户插件）；前端13项通过，覆盖单框型号命中/继续分页后选中、SN原行为、19位ID、换词晚响应、无结果/失败重试、切项目及无效项目保护。ESLint通过；日志 `.run/feedback-20261027/tf043-keyword-backend-before.log`、`tf043-keyword-backend-after.log`、`tf043-keyword-selector-after.log`。未执行外部数据库DDL、迁移或业务数据修复。
- 真实浏览器尚待从“项目详情→阶段→业务联调任务办理→新增联调”测量触发输入至实际结果可见，必须包含HTTP范围解析、防抖及50选项渲染；不能复用TF-048割接入口结论。当前单测/SQL证据不替代真实UI与≤1秒验收，本轮未提交、推送或重启服务。
- 本轮定向vue-tsc退出0，源码/测试/SDS当前规则交叉审核无整改项。固定构建起初被其他会话锁拒绝；只读定位并等待其正常完成，未删锁、停Owner或复用其他target顶替。随后本轮 `worktree-build.ps1` 固定42模块clean package于18:01:05成功（3:30），22项修复保护通过，冻结 `.run/build/releases/89196b38-d635-47ac-ad1f-353e882cbcb1/manifest.json`，SHA-256 `5831217df63651ace4aa5bf20f96da8123f0ac906a91baf0b79c4e18ad02c98f`；`worktree-backend.ps1 -ValidateOnly`通过，迁移禁用，未操作服务。
- 预检按实际运行基准确认：59191已由其他会话变为PID9672，运行SHA为`02f47d52cdf3ae00080bf14384e5aea7cbe59f530f5d73f760e08dc95032b266`（227a发布），本轮没有执行该重启。递归比较本次冻结包与当前运行包，仅39项Maven元数据不同，业务类/资源差异为0，未替换本次构建产物；证据 `.run/feedback-20261027/acceptance/backend-89196b38-frozen-vs-runtime-review-20261028.json`。根核对其他会话部署Owner后可基于当前运行代码直接验收，元数据差异无需重复重启；实际搜索与≤1秒结果仍以真实浏览器为准。
- 首次真实UI性能未达标：管理员从79557→S4→业务联调任务办理→新增表单，第一页archive-page 50/232约3756ms，打开下拉又重复同条件请求约3793ms，不记≤1秒通过。前端已确认immediate项目监听与ElSelect打开时空词remote重复发请求；最小补同组件同项目/同词在途去重及已成功加载空词结果复用，切项目/换词和失败重试仍走真实请求，不增加跨请求/全局授权缓存。新增失败例先复现预期1次实际2次，修复后选择器15项通过（含原分页/大ID/晚响应/失败保护），日志 `.run/feedback-20261028/tests/tf043-empty-search-before.log`、`tf043-empty-search-after.log`。本地已安装Element Plus明确支持debounce(Number、默认300ms)，本组件改100ms，为HTTP及实际渲染留下约900ms预算；尚不能据此判定整体达标，后端等待整链真实定位，不凭SQL毫秒级猜测。
- 空词慢路径根因与首轮SQL修复：HTTP连续11次线程快照显示旧 `selectSelectionPage→BaseMapperX.selectPage→Pagination.willDoQuery` 的MySQL等待；实际Lambda的项目/合同直接OR只使用租户索引前缀，估扫171万行，79557计数1575ms+列表2159ms、84459约3305ms，与页面吻合。最终业务选择入口包括空词统一复用候选ID UNION XML，项目/合同分支命中专用索引、估232行，主档unique eq_ref1行；无keyword独立productModel保持设备原字段AND筛选，无额外DTO开关。只读同库新XML空词79557为3.24+5.50ms、84459为2.39+3.75ms，型号/SN/无匹配/第二页约5～10ms；空词首50个ID及total与旧查询完全等价、分页不重叠。最终增量交叉审核无整改项，证据 `.run/feedback-20261028/acceptance/tf043-wrapper-count-readonly.json`、`tf043-final-xml-readonly.json`、`tf043-final-no-match-readonly.json`；这些为数据库路径证据，不替代新后端运行后的整页≤1秒验收。
- 后续需求方明确两个分支直接取所需设备字段，取消ID回表：分页现为项目/合同分支各自SELECT完整字段、当前型号及相同筛选，UNION去重后排序分页；仅total保留过滤后的ID UNION子查询计数，无ID集合JOIN设备表。合同Owner关联查询约4～5ms，保持原授权与业务关系。新增真实Mapper断言覆盖项目/合同双命中一行、全局倒序分页及当前型号。独立源XML只读复核8组结果等价，79557空词count2.12ms/list5.48ms、型号3.09/3.92ms，84459空词1.87/4.98ms，索引及主档唯一关联成立；证据 `.run/feedback-20261028/acceptance/tf043-direct-union-review-readonly.json`。旧18:23测试日志无最终结果，不计通过；受控启动Owner正常结束释放锁后，本轮固定工具链27项全部通过（19:27:09，24.206秒，0失败/错误/跳过），包括真实租户插件、范围、独立筛选AND、当前型号/原值、双命中去重、跨页及只读历史保护，日志 `.run/feedback-20261028/tests/tf043-direct-union-after.log`。随后独立取锁完成本轮42模块构建，冻结/预检结果见以下两项；未复用旧版本包或自行启停服务，真实≤1秒验收仍待完成。
- 最新直接字段版准备完成：固定42模块clean package于19:30:45成功（3:21），22项已部署修复保护通过；冻结 `.run/build/releases/f9efd2ab-bf84-49f0-803f-d1978fc55334/manifest.json`，SHA-256 `bb5218581d433125810ca2bc4e76ad340a7043cbf88ab30820337ec29c725a79`。固定后端入口ValidateOnly通过、迁移禁用，源码现状与manifest.source均为`bd58dc12f241aac4ccbbd4b3c9d892505369e2c8c3bd5c88ae0b6798eb8e79cd`，无source drift。
- 预检依据真实PID32416的0572发布 `application.jar`，不误用旧dac-route-server.jar；实际运行SHA为`3853cd18affc0c15ced68cd4034091597de206e21bdecce981964bb31791c242`。递归比较40项差异中39项为Maven元数据，唯一业务资源为`ProjectDeviceSelectionMapper.xml`的直接字段UNION，无其他业务类/资源变更；不能复用当前运行时作为最新实现验收。证据 `.run/feedback-20261028/acceptance/tf043-direct-union-frozen-vs-runtime.json`；构建与预检日志同测试目录的`tf043-direct-union-build.log`、`tf043-direct-union-validate.log`。本轮未部署、启停应用、迁移或修改运行数据；待需求方明确批准仅59191新包重启后，从任务入口复验首次/关键词/无结果/分页的整页≤1秒，不提前记为通过。

## 当前最小工作单元

- 审评F-IMP-004基线事实、INT-12/show tech模板契约、旧联调映射及`JointDebuggingCompletionFactApi`。
- Ready前不进入Technical Plan或实现。

## 2026-10-07 已授权的文件入口局部增量

- 用户要求设备日志操作回到项目详情的业务联调入口。已复制增强现有联调页及采集窗口，沿用原“命令采集与日志”操作，在窗口内接通上传、设备文件历史与来源文件查询；旧页面、原证据附件和业务状态动作保持原行为。
- 在`npdms_domain_test`项目1002通过真实浏览器上传授权派生日志，两台设备成功归档v4并分别完成解析回传；验证文件历史、来源/单设备内容读取、关闭重开及未匹配SN整批拒绝。基线关联、对比与完成事实没有实施，本Feature总体Ready/Done状态不因该文件入口增量晋级。
- 证据见[日志解析记录](../../docs/generated/2026-10-06-device-log-parsing.md)的项目详情入口补充。
- 需求方最终位置修正（2026-10-07）：项目级日志入口现与“新增联调”并列，记录行/详情的命令窗口只承接该设备及联调记录，项目全部设备文件同时在“配置Log”可见。真实浏览器验证按钮位置、项目与记录范围、共享文件历史及关闭重开；原业务单据不变，前述记录窗口内的项目上传页签已移除。


- 2026-10-17 业务联调日志定向归集修复与真实验收完成：项目侧边联调入口原未传originTaskId，可能按无任务来源误匹配配置交付件。现打开原“上传日志 / 文件历史”时复用有效计划PAGE绑定和任务定位接口确定唯一原任务，支持游标、完整大ID；任务工作区已传ID直接复用，不逐任务请求详情。无法确定归属时仅查看历史，要求从对应任务上传，不猜测任务或改服务端守卫。6项定向测试及局部ESLint通过。项目992203060198真实页面上传既有真实双设备原日志，原联调任务2107838747850678283被传入并保存，两份v2设备日志自动归集D_JOINT，显示已满足；D_CONFIGURATION原20项材料/历史不变，来源整批文件未混入交付件，重开和重复触发无重复通过。未办理业务联调通过、未人工写任务状态，无后端变更/重启。详见[验收记录](../../docs/generated/2026-10-17-joint-log-deliverable-association-acceptance.md)。

- 2026-10-17 后续任务完成链路已闭环：需求方确认对应交付件满足后自动完成，并明确批准本次部署与v7生效。复用PROJ当前计划求值、ACC事实重验及冻结状态机，仅联调PAGE的独立DELIVERABLE完成条件免人工提交，旧规则及原守卫保留。97项任务/计划测试和29项快照回归通过；冻结产物5ff6c227经正式入口恢复59191为PID18336，local,dac保持，DAC未操作。真实页面生效项目992203060198计划v7（2104870198458802263）后，任务2107838747850678283自动DONE、进度100%，本轮无人工提交，新增一次系统完成判定及审计。页面历史v7/v6和重开验收通过，原联调/配置材料、旧计划内容与已结束轮次不变，无重新上传或解析日志。详见[专项记录](../../docs/generated/2026-10-17-joint-task-auto-completion-acceptance.md)，不晋级整个Feature。

- 2026-10-17 模板配置与其他项目排查完成：需求方澄清按模板业务需要配置即可、其他模板可保留。实际来源模板993009900016已将联调完成条件单独配置为DELIVERABLE(D_JOINT)，Compiler通过，并在真实页面发布v4（993009900067）及重开冻结运行投影核验。其余37个模板、原38条已发布修订及所有现存项目计划/任务/交付件记录未改。只读检查6691个项目（285活动、70有当前计划），当前合同和交付件关系中没有已满足却仍进行中/待验收的联调任务；35个待分配且交付件未满足的同类任务保留既定配置。真实页面确认197未误完成、198仍完成100%，无上传/解析或业务写请求。旧示例模板593的草稿元数据兼容性保存失败已回滚并独立记录，未绕过约束。详见[模板与排查记录](../../docs/generated/2026-10-17-joint-template-configuration-and-project-audit.md)；本次无后端改动或重启，不晋级整体Feature。
