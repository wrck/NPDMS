# F-PROJ-003 项目子树授权与统一数据范围

## 2026-10-26 项目经理办事处精确查询修订（已部署并完成限定范围真实浏览器验收）

需求方取消默认办事处概况，确认完整项目名称/合同号至少填写一项，双条件取交集；精确命中后开放完整项目详情及各业务页签只读，禁止办理和下载。复用当前Task及原组织范围API，不新建角色、成员、Grant、种子或数据库结构，不改既有业务Owner、流程受理人及不可变历史。默认列表、统计和树已恢复仅原完整范围；服务经理区域管理继续保留。

新增`GET /api/v1/pms/projects/office-readonly-search`遵循Business API路径；DAL只查询PROJ主档的名称、原主档合同号与同一有效公司部门行，空范围和空条件失败关闭，大小写及通配符按字面值完整比较。独立只读路由复用现有详情和业务视图。业务请求携带精确条件与单项目ID，服务端逐请求重验项目查询功能权限、启用身份、组织行和目标；请求内VIEW只含目标，不向后代传播，不提供EDIT/MANAGE，成功读取缓存按该上下文隔离。非GET、非JSON文件输出及文件访问凭证/下载/导出等请求在控制器前拒绝；只读响应剔除旧文件直链和办理凭证，不回写原记录。前端同时限制办理控件和请求，执行视图不创建操作客户端。

当前验证：后端首轮定向85项通过、无跳过；前端19项运行时/请求限制测试通过、完整vue-tsc通过、相关前端文件ESLint通过。H2测试显式适配其无长度BINARY仅一字节的方言差异；另在178库只读事务确认84574完整名称命中、部分和错误名称不命中，并回滚结束，无DDL或业务写入。旧后端下通过真实y06496会话检查独立只读路由、成员/客户/设备导航及操作限制，未发写入/下载且无脚本异常；旧样本出现客户关系不可用提示，因此仅记前端交互证据，不记业务权限闭环通过。首轮标准package及22项运行修复保护成功，冻结受受限CIM进程读取影响停止；随后自审归位Business API路径、补齐逐请求功能权限校验，最终标准构建重新执行中。没有部署、重启、执行迁移、提交Git或清理其他未提交内容。最终包与新后端真实浏览器验收结果待后续记录，不以本段晋级整个Feature完成。

后续构建事实（2026-10-27）：最终代码的标准Maven package及定向162项测试通过（SYSTEM 1、ACC 18、PROJ 103、ENG 10、SRV 12、AST 14、server 4，失败/错误/跳过均0）；前端19项测试、相关文件ESLint及完整vue-tsc通过，固定分页调整后项目列表6项重跑通过。真实浏览器另验证了精确查询弹窗空条件不发请求及390宽度展示；尚未提交真实精确查询或验收新后端。两次标准脚本均因构建期间其他会话修改后端源码而拒绝发布：先为OrdinaryProjectMemberService及测试，后为SolutionCompletionMapper及测试。Maven成功不等于冻结成功，本轮没有可用最终发布清单，不可复用历史20884等清单。并行改动原样保留，等待其本轮修改结束后重新构建冻结；没有部署、重启、执行迁移或业务数据写入。证据为.run/manager-permissions-20261026/office-build.log、office-typecheck-final.log、office-query-dialog-result.json；当前服务仍为上一已批准包。

后续运行与真实浏览器验收（2026-10-27）：需求方告知其他会话已重启后，本会话只读核对PID12100于02:09:46+08:00启动，59191 health为UP、19191为HTTP 200；运行JAR SHA256为a596ef286e6303fff38a925df089e88fcfa365fec1a7c6fd219f88c8b8bee986，匹配清单.run/build/releases/6c41e0a2-5f97-496e-a7a9-07ce5967be46/manifest.json，其source为6e5c5298a333e81a79adcb8f2512bdfd25fa357a91e981ea456351658fd42a55并与当时源码一致，包含本次office代码；22项修复保护通过。本会话没有为此重启。真实浏览器新会话y06496默认列表/统计均125，当前页100项全为FULL且无SUMMARY；84574普通请求拒绝，完整名称命中后详情与实例请求均code 0并带no-store。部分名、错误名和双条件错误合同号均无结果，空条件返回400且前端不发送请求。完成只读查询后普通84574仍拒绝；以84574为筛选时，另一个原可读84573也被拒绝；无筛选时84573可读。完整详情、6个概览页签及16个全流程页签有内容；下载ticket id为0的请求被拒绝，未办理或下载。z01010对区域内项目38936的普通详情返回0。1440/390宽度截图检查通过，移动端导航可滚动。回执为.run/manager-permissions-20261026/office-live-browser-result.json。当前范围内没有非空主档合同号；合同号来源已登记docs/decisions/open-questions.md并待用户选择，不臆造正向数据或扩展COM事实。

附件脱敏补丁与发布状态：最终安全复核确认旧物料、外包及采购响应VO的attachmentFiles承载逗号分隔下载URL字符串，原只读脱敏未覆盖；本次只在OfficeProjectReadResponseAdvice加入attachmentfiles并补充保护原对象的回归断言，6项定向测试全部通过。新冻结清单为.run/build/releases/8ec3fa76-807c-42eb-8a76-d955a48b50e7/manifest.json，sha256=f077c1910e930bbc27e866d8a1dd1ef401f528a8f9d0c6dc3ce33d7e7c5cfbef，source=2dc3242af02c86d11b085201fbb482ec06dc0f7840ba8535ef7c70583e5aa1dc并与构建源码一致；与当前运行包逐class对比仅OfficeProjectReadResponseAdvice改变。22项修复保护与ValidateOnly通过，未部署，等待本次明确重启批准。y06496读取三个旧业务接口均403，未获得可供检查附件的真实记录，不能将零字段视为在线脱敏验收通过；附件边界由真实URL字符串回归测试覆盖，部署后仍需复验。安全回执为.run/manager-permissions-20261026/office-attachment-release.json及office-attachment-before-result.json。保留原数据、历史和其他未提交改动，未提交Git。

维护计划标签只读抑制后复验（2026-10-27）：真实浏览器首次读取“阶段状态/工作区空白”因pane选择器和等待不足而无效；纠正后，stage-gates完整S0-S6、S0工作区与阶段历史均可读，instances、node-executions、workspace、context及gate-workbench请求均code 0且目标仅84574。确认此前误报来自顶部ProjectCurrentPlanTag触发维护计划GET；只在该标签v-if增加`!officeReadonly`，只读入口不再加载维护标签，普通入口保持不变，未放宽后端维护权限。相关文件ESLint及既有标签4项测试通过。fresh浏览器复验确认没有GET /plan及失败tag/toast，阶段和历史正常，无非GET请求，会话已注销；截图与回执为.run/manager-permissions-20261026/office-live-stage-status-fixed.png及office-live-browser-result.json中的fixedAfterReadonlyPlanBadgeSuppression。z01010列表和统计均3068。8ec附件补丁仍未部署；前端条件不属于backend Maven sourceScope，后端冻结清单仍有效。合同来源问题待用户答复，澄清后再合并最终部署与重启审批；未因此自动部署或再次重启。当前未取得附件直链真实记录在线验收证据，三个旧接口对y06496均403，不将零字段称为脱敏通过。

合同来源裁决、SQL正向验证与最终冻结（2026-10-27）：需求方确认合同号同时支持PROJ主档登记号及COM商务当前有效关联合同号，仍须处于同一有效公司部门范围；名称或合同号任选一个完整精确匹配，两项同时提供时取AND。新增COM公开Owner候选接口与参数化精确查询，消费者只用候选项目ID筛选、每次读取仍重新验证权限与组织范围，未跨域访问商务表。7个指定定向套件共12/12通过。只读样本回执office-linked-contract-samples.json中项目1818、1819、1820主档合同号为空但存在有效同公司部门商务合同号；只读事务实际执行新Mapper参数化SQL，三条事实路径的exact_search_candidate_hit均为true，未找到同合同范围外样本。该结果仅证明SQL正向候选，合同来源HTTP/浏览器验收仍待部署后进行，不宣称本Feature业务UI验收完成。冻结清单为.run/build/releases/802ea8a7-1486-4999-b669-e3cd7311f1b7/manifest.json，sha256=c39caae15ac8285f82199a4155c7fc8425d93e3ab1d72cdf563d15798cb3fe1f，source=9f6980022eda9a1595ad466b213b93576f24c9543b403775713e8bd24964bf23，runningArtifactSha256=a596ef286e6303fff38a925df089e88fcfa365fec1a7c6fd219f88c8b8bee986；包hash/source及运行基线一致。与当前运行JAR逐class/XML比较，新增ExactContractProjectQuery及ExactContractQuery类，变更COM合同查询API/实现、Mapper/XML及PROJ查询对象、服务、Mapper XML和响应脱敏类。22项修复保护及ValidateOnly通过，服务未变化；未部署、重启或迁移，等待本次部署/重启批准。


最终批准部署及验收（2026-10-27）：需求方批准本次重启后，统一入口部署清单 `.run/build/releases/802ea8a7-1486-4999-b669-e3cd7311f1b7/manifest.json`，运行 PID 40280 的 JAR SHA256 为 `c39caae15ac8285f82199a4155c7fc8425d93e3ab1d72cdf563d15798cb3fe1f`，与清单一致。隔离真实浏览器 y06496 默认列表与统计均 125，当前页无 SUMMARY；1818 普通详情拒绝。合同号分别精确命中 1818、1819、1820，完整名称及名称加合同号命中 1818，错误、部分及大小写不同合同号和双条件错误组合无结果；空条件 UI 不发请求、接口业务码 400。合同查询实际进入 1818 只读详情，详情、实例、项目树及阶段状态读取成功，限定头存在且响应 no-store；以 1818 条件访问 1819、无条件访问 1818、文件 ticket 探针均被拒绝。z01010 列表和统计均 3068、区域项目 38936 普通详情可读。390 宽度结果表横向滚动后只读入口可见并可进入。另对有执行计划的 84574 名称精确查询并进入只读页面，阶段状态及 S0 工作区有内容，context 与 gate-workbench 均业务码 0、no-store，未触发维护计划 GET 或业务非 GET；页面异常为空。回执 `.run/manager-permissions-20261026/office-contract-live-20261027.json`。未找到同合同跨部门真实反例，相关范围拒绝由 SQL 失败测试覆盖；三个旧附件接口对项目经理仍为 403，未取得真实附件记录的正向脱敏证据。此记录不以候选查询或 HTTP 健康判定整个 Feature 完成。

DAC 独立只读观察：本机 `127.0.0.1:48182` discovery HTTP 200、READY、protocol 2；服务器 `10.210.0.11:48182` readiness 与 `59192` callback health 连接拒绝（10061），对应端口无监听；后端背景存在 `DAC_DESKTOP_AUTHORIZATION_REJECTED` 轮询，原因未判定。仅检查连接和状态，未执行采集、设备命令或回调，未证明解析端到端成功。回执 `.run/manager-permissions-20261026/office-contract-dac-status-20261027.json`。

## 2026-10-26 服务经理功能权限包含项目经理（配置与巡检补丁已生效）

需求方在区域权限验收后明确“服务经理权限需要包括项目经理的所有权限，服务经理的定义类似区域负责人”。本次增量复用SYSTEM角色菜单分配接口，只对服务经理现有集合补项目经理有效菜单差集，不给用户追加角色，不扩大data_scope，不修改区域范围、Owner、审批节点或历史。现网预检差集为三个菜单ID及两个功能权限：竣工证明草稿删除、现场培训草稿删除。另修复SRV巡检两处只识别PROJECT_MANAGER的查询资格，使纯SERVICE_MANAGER及既有L1/L2身份可获得同等获权项目查询；办理Owner和版本/状态保护保持。前向V517仅作为源码初始化修正，不对178测试库执行Flyway、迁移或DDL；现网配置通过管理员正常角色菜单API增量应用并由既有事务/缓存失效生效。验收包括同租户差集归零、原权限与数据范围保留、纯服务经理查询/办理拒绝、种子幂等及历史保护。后端代码增量需要另行明确批准后部署，不复用此前启动批准。

实际配置：正常管理员认证后重新读取两角色快照，仅向SERVICE_MANAGER增加菜单1201“我的流程”、19102“完工证明删除”、993109180005“培训记录删除”。通过`/system/permission/assign-role-menu`合并现有集合，回读证明服务经理有效功能权限164→166，已包含项目经理全部138项，差集为空；原服务经理菜单无移除、项目经理菜单和两角色完整角色配置（含data_scope）不变。标准接口只插入差集且触发原事务/缓存失效；未给任何用户追加角色，未修改正式业务对象或Schema。证据`.run/manager-permissions-20261026/superset-applied.json`保留前后菜单ID及新增权限，不含Secret。

验证与发布：同一固定工具链/工作树锁下41项定向测试全部通过、无跳过（SYSTEM种子1、竣工证明18、培训10、巡检访问12）；H2种子执行证明同租户有效角色/菜单匹配、公司/项目授权无变更、重复执行无新增、停用/删除角色及菜单不提权、已撤销历史行保留。巡检新增测试证明纯服务经理可查完整获权项目，但不能凭角色代办他人任务、绕过managerOnly、扩大占位节点或取得项目外资料。原有两个项目经理测试继续通过，业务完成/审批/草稿删除状态保护未降低。未对178测试库执行Flyway或任何迁移、DDL；未在真实业务记录上执行删除。

真实浏览器：正常管理员登录角色管理、按SERVICE_MANAGER筛选、打开菜单权限并回读集合，两个新增删除按钮为已勾选；“我的流程”为父节点半选（子权限非全选），其菜单ID已由接口精确回读确认。仅展开查看并取消，没有二次提交配置；会话已注销。结果及截图为同目录`superset-role-browser-result.json`、`role-permission-browser.png`。新冻结清单`.run/build/releases/ca9f83f4-72e4-4bb6-b930-92341e9bfdc2/manifest.json`已通过22项既有部署修复保护与统一入口ValidateOnly，未部署或重启；当前59191仍运行上一轮区域范围修复包，菜单配置已在线生效，巡检角色识别代码需需求方本次批准后部署。不提交Git、不覆盖其他未提交改动、不晋级整个Feature完成状态。

本次后续部署：需求方明确“确认”后，通过统一入口按上述同一冻结清单部署并重启59191，当前PID42644，health=UP；部署产物SHA256与清单完全一致，22项已有部署修复保护继续通过，local,dac及测试库禁迁移/禁结构更新保持。重启后正常管理员回读仍为服务经理166项、项目经理138项，缺失差集为空；z01010/y06496正常登录均取得两项草稿删除功能权限，项目列表总数仍分别3068/2889，巡检列表HTTP/业务返回成功（当前两账号可见巡检记录均0，不据此宣称实际巡检任务办理已验收）。y06496概况项目完整详情仍返回1014003000，说明范围边界未因功能补齐扩大；测试会话均注销。纯服务经理巡检查询继承与Owner/状态拒绝由本包12项访问测试覆盖。回执`.run/manager-permissions-20261026/superset-runtime-result.json`，未执行Flyway、其他迁移、DDL或真实业务删除，既有业务/历史及其他未提交改动保留；此前待部署段为批准前事实。

## 2026-10-26 区域管理与办事处概况补齐（代码、部署及只读浏览器验收已完成）

依据需求方指定的`manager-permissions-legacy-comparison-20261026.md`及明确授权：服务经理管理授权区域全部项目；项目经理查看本办事处概况，只办理本人负责或明确授权项目。Owner为PROJ，SYSTEM继续拥有有效组织范围；直接消费者为项目列表/统计/树/授权维护及AST设备关联读取。复用现有组织API、范围解析、显式Grant和响应脱敏，不修改项目成员、业务历史、角色菜单、数据库Schema或PRD；旧系统跨区条码搜索、projectAreaManager角色批量转换不在本次范围。

实施顺序：接入同一有效公司部门行的服务经理完整范围；补齐项目经理概况及服务端白名单；统一关联SQL与页面入口；验证授权拒绝、同公司部门组合、显式跨区授权、失效范围和历史保护；最后准备可审阅发布包。既有完成记录保留，不用本次局部增量宣称Feature重新验收完成。部署与59191重启需本次明确批准，当前不操作测试库结构。

本次结果：PROJ统一范围已接入启用经理身份与对应scopeRole的同一有效公司部门行；服务经理的组织来源VIEW可覆盖关闭项目，EDIT/MANAGE仅覆盖ACTIVE，不按项目父子关系向区域外扩展。项目经理组织来源只进入列表/统计/树的概况，不进入完整范围、设备关联或办理授权。保留成员、创建人VIEW和显式Grant原来源；角色不替代业务Owner或审批受理人。概况行服务端白名单在展示增强后执行，私有字段筛选只查完整范围；页面概况弹窗不调用完整详情或成员接口。无DDL、种子、权限菜单或正式业务数据写入，不创建替代成员或改写历史。

实际验证：固定JDK25/Maven工具链与工作树锁下，最终后端定向114/114通过、无跳过（PROJ96、AST14、server4），覆盖公司部门成对命中、公司级范围、跨租户/空范围拒绝、区域内无成员、组织撤销后重读、跨区显式授权、关闭项目只读、概况脱敏/私有筛选及既有授权/树/设备读取；前端Vitest15/15及三个变更前端文件ESLint通过。SQL失败测试在进程内H2执行，未对172.17.90.178测试库建表或写入。首轮新增测试替身两处配置错误已修正，最终相关用例均通过；未降低业务校验。

发布事实：标准构建package成功、构建前后源码一致，22项既有部署修复保护通过；受限进程读取恢复后确认记录PID41316不存在且59191无监听，使用既有离线冻结模式生成`.run/build/releases/20884dd2-e847-4f97-a0f8-9c731a9a6787/manifest.json`。统一启动入口`-StartIfOffline -ValidateOnly`通过，验证local,dac及所有自动迁移/结构更新关闭；没有启动、部署或重启。明细见`.run/manager-permissions-20261026/result.json`与同目录日志。内置浏览器连接组件加载被当前受限环境以EPERM拒绝，未完成两角色真实浏览器验收；需在获批启用本包并恢复浏览器连接后，以z01010/y06496及区域内无成员样本复验。不提交Git、不覆盖其余未提交改动，不晋级整个Feature完成状态。

后续授权与运行验收：需求方明确回复“启动”后，统一入口用上述同一冻结清单、`-StartIfOffline -ApprovedRestart`启动59191（PID34424），健康UP，所有迁移/结构更新仍关闭。前端19191原先也离线，按既有`.env.env.local`及`pnpm run dev`启动。内置浏览器组件仍因沙箱EPERM不能加载，改用已安装Playwright及全新无持久会话Chromium完成两角色真实登录/页面验收；关闭“记住密码”，未导出浏览器会话或Token，结束均调用正常注销。

运行结果：z01010列表和统计均3068，首页10条均FULL；y06496列表和统计均2889，首页2条FULL、8条SUMMARY。SUMMARY实际非空字段仅含批准概况白名单；项目84574完整详情请求返回1014003000，页面无编辑/更正客户/成员按钮，概况弹窗不请求完整详情；1440与390宽度下弹窗内容和边界实际截图复核通过，无页面脚本异常。只读数据库事务另定位项目38936：EXCEPTION_CLOSED、公司/部门命中服务经理范围、非创建人、无当前有效已确认成员关系，且该主体有效显式项目Grant为0；z01010正常登录可读取完整详情及成员，证明区域只读范围确已生效。该无成员样本已关闭，不用于宣称实际写操作验收；ACTIVE写范围及关闭拒绝由本轮失败测试覆盖。整个运行验收未提交项目写命令、未修改业务数据或结构；认证登录/注销按正常系统审计留痕。回执与截图见同目录`browser-result.json`、`region-sample-result.json`及`*-project-overview*.png`，更新后的`result.json`记录最终状态。此前“待启动/浏览器受阻”段落为授权前事实，保留，不覆盖历史证据；不晋级整个Feature完成状态。

> Feature实施状态：`IMPLEMENTATION_DONE`
> 总体工程阶段：`IMPLEMENTATION_IN_PROGRESS`
> Feature Ready Gate：`PASS`
> Implementation Done Gate：`PASS`
> 当前阻断：无
> 当前任务：Feature闭环完成，等待工程链进入下一Feature
> Requirement ID：`PM-04`
> 关联契约：`AUT-01`、`AUT-02`的最小AuthorizationGrant载体
> Feature Spec：`specs/features/F-PROJ-003-project-subtree-authorization-and-unified-scope.md`
> Feature物理契约：`specs/features/F-PROJ-003-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-24-f-proj-003-project-subtree-authorization-and-unified-scope.md`
> 锁定规格提交：`69b2b48320a858f91d938ae6914146681c97fb0a`

## 事实边界

- PRD V1.8、工程链、已批准SDS和F-PROJ-003 Feature Spec是正式输入；不重新审核SDS总体门禁。
- `specs/001-project-delivery-platform/`只作历史参考，不参与实施校验。
- F-PROJ-002现有项目树和范围代码必须重新检查并改造，不能据此判断F-PROJ-003已实现。
- 用户已禁用测试驱动顺序，但每项任务仍需风险匹配的自动化、MySQL或真实浏览器验证。
- 当前处于Implementation，不准备Deployment、SIT、UAT或Release方案。

## 任务跟踪

- [x] Task 1 建立PLT模块并归位平台事实所有权
- [x] Task 2 实现PLT AuthorizationGrant物理模型与公开API
- [x] Task 3 发布ProjectScopeApi并重写统一范围计算
- [x] Task 4 实现项目授权命令、查询与越界守卫
- [x] Task 5 将当前PROJ入口统一到动作化ProjectScope
- [x] Task 6 补齐字典、菜单、示例授权和迁移验证
- [x] Task 7 实现响应式项目授权维护界面
- [x] Task 8 完成真实闭环验证与Implementation Done证据

## 验收跟踪

- [x] AC-FPROJ003-001 当前项目与后代范围
- [x] AC-FPROJ003-002 角色不自动扩大范围
- [x] AC-FPROJ003-003 授权不得越界
- [x] AC-FPROJ003-004 撤权与到期
- [x] AC-FPROJ003-005 项目移动后重算
- [x] AC-FPROJ003-006 幂等与并发
- [x] AC-FPROJ003-007 当前业务入口统一过滤
- [x] AC-FPROJ003-008 模块边界
- [x] AC-FPROJ003-009 真实浏览器与响应式

验收证据：`docs/acceptance/F-PROJ-003-project-subtree-authorization.md`。

## 已登记的非阻断边界

- 任务、设备、交付件、割接和巡检尚未实施的业务对象在其Feature中接入`ProjectScopeApi`；本Feature不提前创建这些页面或业务事实，也不据此阻断当前PROJ闭环。
- AUT-01/AUT-02完整OA申请、外部授权确认和文件正文属于后续范围；本Feature只实现PM-04所需同步授权事实。


## 2026-10-29 成员确认授权同步（TF-011隔离差量）

用户裁决未确认不授予，授权与同一成员有效区间一致。隔离候选新增明确授权成员查询，将普通FULL发现/计算与任务成员权限调用同步；事实/展示/阶段责任存在不变，不删公共Owner条件。真实Mapper红绿及受影响回归与TF-011同一证据，不改Grant/审批/角色配置或历史，不迁移、不应用或部署。权限/模块边界待独立复审，真实受限性能待验；本条不修改Feature状态。

- 补齐阶段启动、闭环检查、需求实体与现行动态表单操作准入，不全局过滤ParticipantFact。原事实读取与SQL条件保持；授权读取/锁通过专项扩展确认当前参与期。263项综合验证有效；D候选待独立复审，TF022共享Mapper/TaskQuery只按hunk叠加。
