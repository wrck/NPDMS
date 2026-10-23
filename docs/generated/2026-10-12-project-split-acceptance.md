# 主子项目专项优化与验收记录

日期：2026-10-12；现场验收完成于2026-10-13 00:04（Asia/Shanghai）。本次约定的合同建项、多订单范围初始化、建项后同步、子项目拆分与三级项目树链路已通过真实浏览器验收。前期父项目手工分配入口已被合同同步流程替换。下方历史记录保留各阶段证据，不能用其中旧的待部署结论判断当前状态。

## 后续复查：长ID子项目详情（2026-10-13）

用户指定主项目`992203060151 / PJT2026000033 / 吉林市中级人民法院安全改造项目`复查拆分后“项目不存在”。其既有子项目`2109733194783391746 / PJT2026000033-SP000001 / 吉林市中级人民法院安全改造项目-沈阳`在数据库中正常存在且为ACTIVE，父根关系与模板v3均完整。

真实浏览器先复现失败：地址栏保留`2109733194783391746`，但详情、实例和联系人请求被详情页的`Number(route.query.projectId)`转换成`2109733194783391700`。项目ID超过JavaScript安全整数范围，服务端按错误ID返回`1014003000 / 项目不存在`。前期12位项目ID样例没有覆盖此边界。

本次仅调整`project-master-detail/index.vue`的详情、实例、联系人与风险四处加载入口，复用仓库既有的正十进制Long校验及无损Owner ID传递方式，与继承项目详情入口保持一致。前端19191热更新生效，无后端重启、无项目数据修补或重建。

修复后通过正常页面登录和操作验证：子项目标题、详情及模板实例可见；刷新重进正常；子项目树、无下级进度空态及SO058302订单范围正常；返回指定父项目后，其项目树显示同一现有子项目。所有相关请求均保留完整19位ID，详情、实例、树、进度策略和风险查询返回业务成功，无页面JavaScript异常。

独立既有问题：主项目与子项目的联系人接口均返回`1014001003 / 所属客户不存在或不可用于新项目关系`，两者继承的客户编码同为`C00015`；本次未修改客户主数据或关联规则。另观察到既有组件class属性继承警告，未把控制台整体标为无告警。

复现与回归脚本：`.run/project-split-acceptance/inspect-project-id-browser.py before|after`；证据为同目录`project-long-id-before.json/png/txt`、`project-long-id-after.json/png/txt`、`project-long-id-child-scope.png`和`project-long-id-parent-tree.png`。其中after包含真实页面断言，原始失败请求与修复后响应均已保留。未提交或推送Git。

## 前一轮运行验收结果（2026-10-13 00:04）

前端19191，后端59191，运行库`127.0.0.1:24306 / npdms_domain_test`。用户先后明确授权当前代码构建接管、V449–V459迁移及本次增量重启；11个前向迁移由Docker Compose成功执行，未修复Flyway历史或回写旧版本。最终后端由固定脚本从冻结清单`7f7d750e-b64a-4a81-821e-f117045f8bb3`部署，使用`local,dac`，PID 39612。48182未操作。未提交或推送Git。

所有本轮项目使用用户指定模板`TPL-DELIVERY-FLOW-20260924`的已发布v3（精确revisionId `993009900054`），未修改发布模板。根项目为`992203060212 / PJT2026000059 / 合同多订单主子项目验收 20261012`。样例合同和订单使用`CSCOPE-QA-20261012`前缀，与真实商业来源分离。

| 浏览器场景 | 实际结果 |
|---|---|
| 选择合同并创建根项目 | A、B两个订单的三行范围自动生成10/6/4，范围版本1；无父范围手工分配操作 |
| 超量与草稿恢复 | 11/10预览被拒绝，无子项目残留；刷新重开后杭州/上海办事处、4/1/3数量及各自v3模板选择完整恢复 |
| 同一子项目承接多个订单行 | 一区`992203060214`承接A/10=4、B/20=1；二区`992203060215`承接B/10=3；真实确认创建成功，可从结果链接进入独立项目 |
| 拆分后的父范围 | A/10=6、B/10=3、B/20=3，根项目范围版本2；原10/6/4数量以历史版本保留 |
| 建项后订单增长和新增订单 | 来源通过COM正常导入API更新A数量10→14，并新增C/10=5；来源导入本身不改项目范围。随后真实页面预览并确认A父量6→10、C父量0→5，B两行保持3/3，整批只推进至范围版本3 |
| 同步保护已分配范围 | 对比同步前后快照，两个新建子项目的范围ID、数量、版本及生效时间完全一致；父A历史页面完整展示10→6→10 |
| 子项目继续拆分 | 一区再将A数量2分给站点`992203060216 / PJT2026000059-SP000001-SP000003`；站点深度2、根项目不变、模板仍为v3，浏览器项目树显示三级关系 |
| 多级拆分后重新同步预览 | 四个订单行均无变化，确认按钮禁用；A的其他项目占用为一区2＋站点2，根项目仍为10，未重复占用；范围版本仍为3 |
| 进度页与非法权重 | 根项目显示两个直接子项目、默认等权50%/50%及“待计算”；站点项目显示无下级空态；人工权重49%＋50%被拒绝，未保存策略 |
| 既有数据保护 | 操作前后30个既有子项目的编码/父根关系/深度/模板/办事处字段一致，20条既有验收报告版本的状态、结论及生效和更新时间一致 |

最终当前范围守恒：A/10为根10＋一区2＋站点2＝14；B/10为根3＋二区3＝6；B/20为根3＋一区1＝4；C/10为根5＝5。根项目在下级拆分及再次预览前后，范围记录与版本完全一致。

新面板只提供合同同步、查看和历史入口，子项目没有整合同同步按钮；不生效的范围办事处输入已移除，归属办事处由子项目选择。真实多行预览暴露旧COM仅按子项目标识去重的缺陷，现按“子项目＋订单行”联合去重；相同子项目相同订单行重复、跨行SN重复仍拒绝。

## 定向验证与证据

- COM同步19项、真实MySQL集成5项通过；建项应用最新22项通过；原拆分/树范围/办事处事实32项通过；多订单行兼容服务11项通过。多行新增的两个正向回归先失败复现，再修复通过；授权、重复范围/SN、版本、事务回滚及历史保护检查保留。
- 前端同步、草稿状态和范围分页13项通过；此前拆分、模板恢复、进度和异步状态的16项验证仍适用。全量类型检查仍有38项本次文件之外的诊断，未将全库检查标为通过。
- 最终固定脚本构建成功，部署修复保护22项通过。共享当前代码中4个类文件的字节差异已做反汇编语义复核及41项相关回归；只登记精确的后继产物，旧基线和缺失/未知产物拒绝规则保留。
- 数据快照：`.run/project-split-acceptance/contract-evidence-{before-split,after-split,after-source-import,after-sync,after-grandchild,final}.json`；只读脚本为同目录`read-contract-evidence.py`。来源增量批次为`contract-scope-qa-20261012-growth`，正常Owner API导入，未直接写项目生命周期或范围表。
- 浏览器截图：同目录`contract-initial-scope.png`、`contract-split-draft-reopened.png`、`contract-split-created.png`、`contract-sync-preview.png`、`contract-sync-completed.png`、`contract-scope-history.png`、`contract-grandchild-created.png`、`contract-project-tree-three-levels.png`、`contract-root-progress-pending.png`、`contract-weight-invalid.png`、`contract-final-no-change.png`。
- 日志：同目录`contract-scope-tests.log`、`contract-scope-mysql-tests.log`、`contract-creation-tests.log`、`backend-tests.log`、`multi-line-before-fix.log`、`multi-line-after-fix.log`、`multi-line-package.log`、`multi-line-deploy.log`、`approved-migration-final.log`。

## 当前验证边界

本次通过的是已约定的合同范围与主子项目操作链路，不代表S0至项目关闭所有业务均已验收。2026-10-13只读复核`ACT_RE_PROCDEF`，`project-progress-policy`定义仍为0条，权重审批成功链路未执行；没有编造审批配置。相同子项目、相同订单行、不同范围部门的旧PROJ/COM语义冲突已登记`Q-PROJ-SPLIT-SCOPE-OFFICE-20261012 / BLOCKED_BY_SPEC`，保留一项目一订单行一个当前主范围的约束，不影响本次多订单行拆分。

以下为各阶段当时的记录，包含已被本节运行结果替代的待部署与交接结论。

## 前一阶段实施与部署交接（23:05，历史记录）

用户已明确批准：从项目—合同—订单关系汇总合同下全部订单行，合同建项同事务初始化父范围；建项后预览并同步父项目剩余数量；移除父项目手工分配入口，保留子项目范围及历史。对应PRD、Feature和API设计已作本次批准范围内的增补，没有增加审批节点或改变新增合同的PM-06边界。

- COM新增 `ProjectContractScopeApi`、`ProjectContractScopeService`、`ContractProjectScopeMapper` 和 `ProjectContractScopeController`。所有关联订单按订单行分别计算，剩余量扣除所有其他项目已分配数量；减少涉及验收锁定、序列号、超量或子范围冻结时整批拒绝。每批实际变更只推进一次范围版本，旧范围及明细保留，审计和Outbox与业务同事务。
- PROJ合同建项在原合同绑定后重新锁定读取初始化后的项目版本，调用范围初始化；任意失败向外抛出，由原建项事务统一回滚。
- 新预览及同步接口为 `POST /api/v1/pms/project-contract-scopes/{projectId}/actions/preview|synchronize`。要求项目更新和COM范围查询权限；实际新增、调整、释放逐类复核原COM写权限，并校验当前根项目MANAGE范围、项目活动状态、合同可见性、树/项目/范围版本和来源摘要。幂等成功重试不会重新写入。
- 父范围抽屉只保留查看、搜索、历史与“同步合同订单”；同步差异展示订单有效量、其他项目占用、当前父量与同步后量，数量只读。子项目不显示整合同同步入口。同步成功使旧拆分预览失效，并在重新预览时更新草稿水位。
- 真实MySQL测试发现并修复关联生效时间精度问题：新查询使用 `CURRENT_TIMESTAMP(3)`，避免刚绑定的毫秒关系被秒精度查询短暂漏读。

已完成验证：COM同步19项单测；PROJ建项及原拆分29项定向测试；前端同步、拆分状态和范围分页13项测试；真实MySQL5项集成测试（全部订单/多关系去重、平台真实幂等重放、第二行审计失败整批回滚及重试、取消/解绑来源仍可见、父范围更新保留子范围与旧数量）。后端测试全部使用固定工具链和工作树互斥锁。前端全量类型检查仍有38项本次文件之外的诊断，本次新增和修改组件无诊断。

日志：`.run/project-split-acceptance/contract-scope-tests.log`、`contract-scope-mysql-tests.log`、`contract-scope-types.log`。MySQL测试使用每次生成的独立来源前缀及ID，结束清理仅限自身夹具。

初始化样例：前向幂等种子 `sql/migrations/V459__project_contract_scope_examples.sql`；已在明确的 `127.0.0.1:24306 / npdms_domain_test` 单独执行两次验证幂等。合同 `CSCOPE-QA-20261012 / 993101200301`，订单A/B为 `993101200302/303`，三个独立订单行 `993101200311/312/313` 数量为10/6/4，均标记 `project_contract_scope_qa`，并非真实商业来源。本次未批量执行其他会话的待迁移版本，也未写项目、子范围或验收事实。

**当前运行边界：59191仍是旧包，真实浏览器调用新增preview返回404。** 已在真实页面确认手工分配/调整/释放入口移除，原验收项目203的10→9→10三版历史仍可查看。新合同建项自动范围、建项后同步和子项目创建整链路必须等本轮代码部署后继续，尚不能标记通过。

沿用用户此前安排：现有后端会话统一构建部署，本会话不接管或重启59191。部署必须包含上述新COM实现和PROJ建项接入，以及前期 `ProjectSplitDraftService` / `ProjectSplitApplicationService` 两处修复，使用 `scripts/worktree-build.ps1` 生成冻结清单，再按 `.run/AGENTS.md` 的独立授权与 `local,dac` 入口部署。前端19191已通过HMR载入新界面。

待运行验收：真实页面用样例合同创建根项目→自动出现2订单3行范围→拆分两个子项目→源订单增量与新增订单经COM受控导入更新→根项目差异预览及确认同步→旧子范围和分配历史不变→重新打开拆分草稿、继续下一级拆分→项目树与进度页联动。订单来源变更不直接改项目范围或生命周期。

## 前期验收记录

## 本次调整

- 新增拆分工作区、模板选择器和进度汇总组件，在主项目详情及继承项目详情接入；原组件保留。
- 拆分按“编辑方案 → 校验范围 → 创建子项目”组织；订单行从父项目有效范围选择，复用现有范围管理抽屉；办事处使用后端 `departmentCode` 契约。
- 草稿重开保留办事处、数量、序列号组合及模板版本，模板名称按精确修订恢复；修改方案使旧预览失效；失败重试复用应用幂等键，成功后停止重复创建。
- 进度页区分无子项目、读取失败和待计算；完整读取子项目分页后才允许调整权重；树正在更新或分页版本不一致时停止汇总。
- 新界面按内容容器宽度调整字段和按钮。390px 手机视口中，主项目详情全流程导航高度由 751px 降到 58px；拆分区域 `clientWidth = scrollWidth = 348px`，底部按钮可完整操作。
- 修复草稿读取使用历史树版本进行授权的问题：读取按当前树权限，预览与应用仍检查草稿水位。
- 修复创建后范围转移使用旧项目版本的问题：阶段初始化已推进数据库版本，拆分服务现在同事务锁定读取初始化后的实际版本交给 COM，保留 COM 严格版本检查。

## 实际验证

运行环境：前端 19191，后端 59191；独立验收数据位于 `npdms_domain_test`。未重启服务，未提交或推送 Git。

独立样例：主项目 `992203060203 / PJT2026000058 / 主子项目专项验收 20261012`；订单 `SPLIT-QA-20261012`，订单行 `993101200111`，数量 10。订单为本专项带 `project_split_qa` 来源标记的合成样例，项目创建与订单范围分配均通过真实页面完成。

| 场景 | 实际结果 |
|---|---|
| 项目创建 → 订单范围预览 → 确认分配 | 通过；父项目有效范围为 10 |
| 父项目 10、拆分数量 11 | 被服务端预览拦截；页面显示超量原因，创建按钮禁用 |
| 两子项目草稿保存、刷新、重新进入 | 通过；杭州/上海办事处、4/3 数量及独立模板修订保留 |
| 无子项目进度页 | 正常空态；不请求非法叶子汇总，不允许调整权重 |
| 手机宽度及默认桌面宽度 | 已用真实浏览器检查；手机表单无横向溢出，操作按钮完整展示 |
| 后端草稿当前权限、初始化后版本与拒绝路径 | 32 项定向测试通过；两处回归均先验证失败再修复 |
| 前端草稿、范围分页、模板恢复、进度与异步竞态 | 16 项定向测试通过 |
| 前端全量类型检查 | 未通过：仍有 38 项本次修改文件之外的诊断；本次文件无诊断 |
| 子项目批量创建 → 范围转移 → 树及进度联动 | 待部署后端版本修复后继续真实验收 |

测试命令：`.run/project-split-acceptance/run-tests.ps1 -TestNames 'ProjectSplitDraftServiceTest,ProjectSplitApplicationServiceTest,ProjectTreeScopeServiceTest,ProjectOfficeFactApiImplTest'`；前端使用 `vitest.pms-file.config.ts` 运行 `workspace.spec.ts`、`useProjectSplitWorkspace.spec.ts`、`ProjectSplitTemplatePicker.spec.ts`、`ProjectProgressSummaryPanel.spec.ts`。后端测试持有 `worktree-toolchain.ps1` 的同一构建锁。

浏览器证据：`.run/project-split-acceptance/split-workspace.png`、`split-preview-full.png`、`split-mobile.png`、`split-mobile-actions.png`、`progress-leaf.png`。测试及构建日志位于同目录。

## 尚未闭合的运行条件

1. 当前后端仍使用初始化前的子项目版本，COM 返回 `PROJECT_OFFICE_FACT_INVALID`，因此真实创建回滚。已确认无子项目残留，父项目范围仍为 10。修复后需经批准部署到 59191 再验收。
2. 本地“直签-工程类”发布模板包含 `AFTER_INITIAL_ACCEPTANCE` 满意度依赖，但租户 1 未配置此时点的已发布问卷；创建报 `SATISFACTION_TEMPLATE_NOT_UNIQUE`。没有修改已发布模板或问卷规则；后续验收使用另一个已发布的“项目交付全流程V2 v3”独立模板。
3. 本地 BPM 未检出配置键 `project-progress-policy` 对应流程定义。权重审批成功链路不能据源码或单测宣称通过，具体审批角色与节点由既有配置决定。

后端整包两次编译均成功，但构建期间并行会话修改了割接源码，冻结检查均拒绝生成发布包；没有绕过来源一致性检查。本专项没有生成可部署的冻结清单。

## 交接现有后端会话

用户已明确选择：由现有后端会话统一带上本次修复构建部署。本会话不再争用整包构建与部署；当前连接未提供跨会话发送工具，本文为共享工作树交接入口。

需要带上的两处后端修复均已落在当前工作树：

1. `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/ProjectSplitDraftService.java`：`getDraft` 按当前父项目及当前完整树校验权限，保留草稿的历史版本水位。
2. `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/projectsplit/ProjectSplitApplicationService.java`：子项目初始化后再次同事务锁定读取实际版本，再调用 COM `applySplit`。

对应测试为同目录下的 `ProjectSplitDraftServiceTest`、`ProjectSplitApplicationServiceTest`；连同树范围、办事处事实测试共 32 项通过。后端原始创建返回 draft 的版本可能为 0，而 `ProjectStageAdmissionService → ProjectCurrentStageService.synchronize` 已将数据库版本推进到 1，这正是现场 COM 拒绝范围转移的原因。不要降低 COM 版本检查。

构建部署仍使用 `scripts/worktree-build.ps1` 与 `scripts/worktree-backend.ps1`，先形成有效冻结清单；59191 固定 `local,dac`，重启仍须具备本次部署的明确批准。此交接不构成其他服务的启动或重启授权。

部署后的现场续验入口：

- 页面：`http://localhost:19191/pms/project-management/project-master-detail?projectId=992203060203`，进入“拆分方案”。
- 草稿：ID `1`，状态 `DRAFT`。父项目订单范围界面续验后，最新 `draftVersion=3`、`parentVersion=1`、`scopeVersion=3`、`treeVersion=1`；无子项目残留，父项目有效订单范围为 10。原 `draftVersion=2 / scopeVersion=1` 为前次交接时点。
- 子项目一区：杭州示例办事处、数量 4、默认业务全流程 v1；子项目二区：上海办事处 `310420`、数量 3、项目交付全流程V2 v3，已填写独立模板选择原因。
- 重新预览后确认创建，核对两个独立编码、模板快照和部门；有效范围应为父项目 3、一区 4、二区 3，历史范围保留。
- 核对项目树即时刷新、从结果进入子项目、三级关系与移动后编码不变；验证父项目缺少子项进度时保持待计算，权重草稿完整覆盖直接子项目且合计 100%。
- 权重审批流程当前未部署，审批成功不可写成通过；不得临时编造审批人或节点。全部后代闭环守卫及相关授权失败路径仍需记录实际验收结果。

只有现场创建及上述联动实际验证后，才能将本专项记录更新为全链路通过。

## 父项目订单范围界面补齐

用户指出父项目订单范围未完成界面优化后，新增 `ProjectParentScopePanel.vue`、`ProjectParentScopeEditor.vue` 与 `useProjectParentScopeEditor.ts`，仅接入本专项的 `ProjectSplitPanel.vue`。原 COM 交付范围列表、编辑器、历史抽屉保持原样。

- 订单、行号、产品、分配数量和办事处组成可阅读的明细；项目 ID、内部版本水位不再占用业务界面主区域。办事处按后端 `departmentName/departmentCode` 展示，时间戳格式化为日期时间，状态使用中文名称。
- 同一抽屉内完成查看、搜索、分配表单、调整和历史查看；保留服务端权限、项目/范围/订单来源版本、幂等键和确认前预览。
- 新增分配按订单号查询，订单选项支持继续加载；历史完整分页。加载失败显示可重试错误，不伪装成无数据。
- 范围发生变更后清除旧拆分预览，将草稿标记为待保存；再次生成预览先保存以刷新范围水位。
- 复核发现的保存后关闭守卫未释放问题已修复，并以真实 Vue 组件卸载顺序作回归。

本轮真实浏览器结果：

1. 正确显示办事处与生效时间；订单关键字查找及无结果状态可用。
2. 调整数量为 11 被服务端预览拒绝，显示超量原因且不能确认；新分配到已有订单行也被重复分配及超量规则拒绝。
3. 通过正常界面将专用样例范围从 10 调整为 9，再恢复 10；列表即时更新。历史保留第 1 版 10、第 2 版 9、第 3 版 10，当前仅第 3 版有效；保存后抽屉可关闭并重新打开。
4. 列表在 320、768、1024、1440 像素宽度无横向溢出；分配表单在 320 像素宽度无横向溢出，390 像素截图包含可操作的预览与确认按钮。
5. 范围调整后重新生成拆分预览，草稿已更新至 `draftVersion=3 / scopeVersion=3`，仍未创建子项目。
6. 释放确认已在用户追问业务逻辑时取消，没有提交释放命令；新版新增分配的成功路径由定向测试覆盖，本轮没有新增第二份范围。

本轮 4 个测试文件共 13 项通过：`parent-scope.spec.ts`、`useProjectParentScopeEditor.spec.ts`、`ProjectParentScopeEditor.spec.ts`、`useProjectSplitWorkspace.spec.ts`。全量类型检查仍为 38 项本轮范围外错误，本轮新增及修改文件无诊断。日志为 `.run/project-split-acceptance/parent-scope-types-final.log`。

截图：`.run/project-split-acceptance/parent-scope-desktop.png`、`parent-scope-mobile.png`、`parent-scope-mobile-form.png`、`parent-scope-history.png`。

用户追问“将订单范围分配给父项目后，即可在拆分方案中划给子项目”的依据：`F-PROJ-002` 第 94 行规定子项目拆分范围之和不得超过父项目当前可分配范围；COM 规格区分 ERP 订单数量与项目承接范围，没有规定每次拆分前必须重新人工分配。当前 `ProjectCommerceSourceServiceImpl.bindProjectCommerceSource` 仅绑定项目—合同及执行单，没有自动生成交付范围。原提示把范围前置事实表述成固定操作步骤，已改为“查看本项目承接的订单明细与数量，拆分时在此范围内划分子项目”。本轮未变更业务规则，也未自行新增建项或同步自动分配规则。

组件行为参考 [Element Plus Drawer](https://element-plus.org/en-US/component/drawer.html)、[Select](https://element-plus.org/en-US/component/select.html) 和 [Vue 异步副作用清理](https://vuejs.org/guide/essentials/watchers.html#side-effect-cleanup)；业务约束以仓库规格与服务端接口为准。
