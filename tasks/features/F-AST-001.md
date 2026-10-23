# F-AST-001 设备序列号档案与时态归属

> Feature实施状态：`REVALIDATION_REQUIRED`
> 总体工程阶段：`IMPLEMENTATION_AUDIT`
> Feature Ready Gate：`PASS / SPEC-FAST001-FEATURE-READY-20260825-01`
> Technical Plan Gate：`HISTORICAL_RECORD_NOT_IN_MASTER_TASK_CHAIN`
> Implementation Done Gate：`PENDING_MASTER_REVALIDATION`
> 当前阻断：`历史实现证据存在，但此前缺少master Feature Task，不能由Spec或证据反推Done`
> 当前任务：`建立独立Delivery Unit，逐项复核a9f8b7c5证据与master当前代码后申请Done`
> Requirement ID：`EQP-01（V1）`
> Feature Spec：`specs/features/F-AST-001-device-serial-archive-and-temporal-assignment.md`
> 历史实现证据：`a9f8b7c5`

## 权威状态纠偏

- Feature Spec继续唯一维护Ready和业务Scope，不再维护Implementation状态。
- `a9f8b7c5`、自动化、MySQL、查询计划和浏览器记录均保留为历史候选证据；本任务建立前没有权威Feature Task，因此当前不得派生Implementation Done或EQP-01覆盖完成。
- 重新裁决必须基于master当前代码、完整DoD、旧设备写边界和全部适用证据，不重做无关测试，也不修改已冻结业务语义。

## 待完成

- [ ] 在master创建F-AST-001复核Delivery Unit并声明精确边界。
- [ ] 核对历史提交、当前master实现、Feature Spec和适用测试是否一致。
- [ ] 复核普通角色旧写退役、`super_admin`旧模型受控写及无AST代理/双写边界。
- [ ] 在master最终状态执行适用验证与Code Review。
- [ ] 只有独立Done Gate通过后，才把本文件更新为`IMPLEMENTATION_COMPLETE`并重新生成追溯投影。

本记录只补齐缺失权威链，不否定或覆盖历史证据，也不提前宣布Feature完成。

## 2026-10-08 序列号配置Log抽屉增量

- 按需求方明确要求，项目序列号行的配置Log直接打开当前设备抽屉，保持序列号工作台和页面地址不变；以既有AST接口合并读取归档版本及已有旧日志，沿用原下载鉴权。
- 项目配置Log取消按新旧存储区分的两个平行页签；旧历史只在当前项目有记录时折叠显示，没有旧记录时隐藏。当前项目1002有4条旧日志且配置附件上传入口仍存在，保留可查；不新增、删除或迁移历史。
- 已通过真实浏览器的设备隔离、版本展示、关闭重开、内容读取、空日志、查询失败/刷新恢复及无旧日志项目隐藏验证。组件编译和相关lint通过；详细范围与证据见[日志解析记录](../../docs/generated/2026-10-06-device-log-parsing.md)的设备抽屉补充。本次不变更上述Feature总体状态。

## 2026-10-09 设备管理分页性能修复

- 按用户授权处理现有工作台加载慢；仅优化权限SQL执行顺序与无筛选扫描索引路径，V416新增覆盖索引，不修改PRD语义、业务记录或授权范围。
- 已部署59191并完成8项权限回归和真实浏览器分页、SN筛选、无匹配、重置验收；首页接口由10～12秒降低至约1.7秒。详见[性能验收记录](../../docs/generated/2026-10-09-device-page-performance-acceptance.md)。本增量不改变上述Feature总体状态。

## 2026-10-09 序列号详情批量展示性能修复

- 按用户要求移除40台设备121次逐台补齐请求，AST分页返回版本/维保统计，IMP通过授权的项目当前安装地址批量接口供消费。保留模块表Owner及历史读功能。
- 后端13项、前端4项回归及真实40台设备浏览器验收通过：2个业务请求，约0.7秒；详见[批量验收](../../docs/generated/2026-10-09-serial-batch-acceptance.md)。本增量不改变Feature总体状态。

## 2026-10-09 当前安装地址快照投影统一

- 按用户确认复用locationSnapshot.fallbackLocation，AST位置生效时统一补齐当前快照的完整文本；安装及位置历史保留原始来源，手工地址不臆造站点引用。移除独立installationAddress读写依赖；已执行V420以V421前向修正并撤除冗余列。
- 后端13项、前端4项回归及真实浏览器通过，40台序列号只需一次分页业务请求；历史迁移仅补齐1台手工地址当前快照，24台当前安装投影一致，旧历史不改。页面保存新地址后返回序列号页立即更新；详见[快照投影验收](../../docs/generated/2026-10-09-installation-snapshot-projection-acceptance.md)。本增量不改变Feature总体状态。

## 2026-10-22 设备管理页面加载与名称查询（需求方已批准）

本次范围：项目、客户、办事处作为常用名称包含筛选并在列表显示名称，隐藏当前页新增按钮；收紧筛选布局，设备名称/产品编码放入更多筛选，保留所有原表格业务字段。项目和客户ID继续作为后台关系标识保留，界面不展示ID。办事处仍使用设备固化组织归属，不从当前项目重算；名称仅通过PROJ/CUS既有公开API批量读取已授权当前页，不跨模块联表、不逐行查详情；原三路设备可见范围、租户、逻辑删除、SN精确匹配和分页排序不变。名称匹配候选为空时直接空页，合同候选解析提前应用同一名称限制；有名称条件时允许优化器选择原索引。没有新增索引、迁移或字典/菜单。

首屏只加载列表与筛选，原详情面板/弹窗移动至按需加载的DeviceWorkbenchDialogs，打开时再导入，原详情、编辑、状态与关系功能保留。设备SN单行展示，日期按可读日期格式展示，窄屏改为单列筛选、表格内部横向浏览。新增过期请求防护，防止旧查询覆盖新条件结果。内嵌项目范围支持真实Long字符串ID，避免Number安全整数校验将项目范围丢弃；重置保留该精确范围。

验证：固定工具链、依赖仓库及工作树独占锁下，后端首批20项通过（DeviceQueryServiceTest 4、DeviceListNameServiceTest 2、DeviceNameFilterSqlTest 1、DeviceSerialPageSqlTest 1、DeviceVisibilitySqlTest 1、DeviceListQueryContractTest 3、DeviceArchiveVisibilityTest 4、DeviceOrganizationProjectionServiceTest 4）；新增Owner查询的ProjectDeviceNameQueryTest及CustomerNameQueryTest各1项通过，验证当前租户和空白查询不放大范围。SQL单测覆盖原合同/组织/项目可见性、新名称交集、逻辑删除、空匹配与分页记录的版本/维保事实。前端运行测试5项通过（首屏不装载详情/无新增按钮、三名称查询及重置、按需打开、Long项目范围、过期请求）；定向ESLint零错误。全量类型检查仍被本次未修改的验收、需求分析、配置日志及任务详情等文件错误阻断，设备相关文件没有错误，不宣称全量通过。

真实MySQL仅使用现有npdms_domain_test数据及只读事务，源XML生成的名称组合查询返回6条，与明确条件的独立SQL计数一致；空匹配、无办事处及跨租户为0，分页6条、单次列表SQL约2.89毫秒，执行计划使用原项目/关联索引。证据 `.run/device-name-mysql.json`。此定向SQL不代表完整HTTP请求或全部权限闭环。

真实浏览器旧版基线：19191/customer-asset/devices首屏4.888秒，列表API约3.325秒；当前租户约135万条未删除设备，主要成本为大范围计数及合同候选解析。只读performance_schema及EXPLAIN证据 `.run/device-candidates-explain.json` 显示合同候选约0.5秒，取消原覆盖索引未改善，因此未改变该索引策略或引入缓存。新版前端已验证隐藏新增、常用筛选/名称列头、首屏不请求详情组件、点击后装载并打开原设备详情抽屉，桌面和390px截图已检查，无浏览器错误。多次单次页面耗时约3.6～5.1秒，含后端旧版本及同时构建负载，不能据此宣布默认全量页面稳定提速。新后端名称补全/筛选的完整浏览器闭环仍待部署；脚本与截图在 `.run/device-page-ui.py`、`.run/device-page-after-desktop.png`、`.run/device-page-after-mobile.png`。

受控完整打包成功；冻结清单 `.run/build/releases/e9df8ca7-9c3e-41da-8d5a-bb8e57cd7263/manifest.json`，部署前ValidateOnly通过，22项运行修复基线及local,dac保留。包包含当前整个工作树已有改动及此前项目列表优化，尚未部署/重启/提交/推送。依据AGENTS.md，取得本次明确重启59191批准后再完成新接口的真实浏览器端到端验收；不改变本Feature的总体Done状态。

浏览器补验：精确Long项目ID 2109733095671988226 原样进入请求，返回4559条当前项目设备，当前页每条projectId一致；重置范围单测通过。最终桌面/窄屏及详情按需加载无浏览器错误，证据 `.run/device-page-ui.json`。

### 2026-10-23 设备列表移除维保月数及关联

按需求方明确授权，列表响应、内部投影和前端列表类型移除 warrantyMonths；分页SQL删除 ast_device_warranty 关联，维保开始/结束日期及状态仍直接读取 ast_device，其余权限、筛选、排序和延保次数不改。详情维保月数保留。DeviceSerialPageSqlTest 在不建维保当前表时执行分页，验证主表维保值、版本和延保次数，并覆盖其他租户、未授权项目及软删除设备排除；DeviceQueryServiceTest、DeviceAccessScopeServiceTest 均通过。真实浏览器现有运行环境列表、详情和项目范围回归通过，无控制台错误；后端新SQL仅完成定向测试，尚未部署或重启。

### 2026-10-23 设备列表延保计数改为当前页聚合

按批准替换 selectVisibleDeviceList：device_page 保留原字段、版本关联、权限/筛选片段、倒序及 LIMIT；去重当前页 SN 后在同租户内聚合未删除且 extended=1 的维保记录，再 LEFT JOIN 并将无记录次数补0。取消 SELECT 列中的逐行相关计数子查询，其余 Mapper 查询逐字比对无改动。为兼容H2参数类型推断，租户参数保持在聚合JOIN条件，不单独投影为CTE参数列。

定向 16 项测试通过（分页SQL、名称筛选、可见范围、查询服务和权限范围服务）；补充首/次/空页、零延保、空权限、空集合筛选及精确SN回归。实际MySQL只读事务中，新旧SQL七种场景结果逐行一致，包含分页偏移、空筛选集合、空权限和其他租户；执行计划已记录 .run/device-no-correlated-count-mysql.json。初次H2类型推断失败已修复并重跑通过。不宣称整体接口性能提升，未重启、未部署服务。

### 2026-10-23 设备组织信息并入分页投影

按需求方批准，DeviceListProjection 和 device_page 增补 ast_device 固化的 company_id/company_name/department_id/department_code/department_name/organization_source；列表控制器直接组装原 organization 响应，移除 DeviceOrganizationService 依赖与分页后 resolve 补查。未重新解析归属，不改其他入口组织查询、响应结构及权限语义；源码比对确认SQL除新增六个投影列外其余内容不变。

24 项定向测试通过（含控制器组织字段/空值映射、端点权限契约、分页SQL、名称筛选、可见范围、查询服务及名称批量服务）。新增控制器测试首次受 Mockito 包装数字默认0影响，显式构造null场景后重跑通过；未修改生产规则。实际MySQL只读事务七种分页/空集合/空权限/跨租户场景比对，既有字段及行顺序与替换前完全一致；8行组织字段逐列与 ast_device 原值一致，证据 .run/device-organization-page-mysql.json。无UI布局变更，未部署、未重启服务；不声明运行接口已使用新SQL。

### 2026-10-23 设备及合同侧授权读取去重

按需求方授权，仅 DeviceQueryService.getPage 显式开启同步 AuthorizationReadScope，正常返回、提前返回或异常均自动清理；下一次列表建立新作用域，不向其他线程传播，不启用跨请求缓存。SYSTEM.getActiveScopes 在作用域内按当前租户、租户忽略模式及用户复用原始只读行，每次重新转换DTO，避免消费者修改污染后续调用。PROJ.resolveAllCurrent 按上下文租户、租户忽略模式及完整ProjectAllScopeQuery（租户、主体、动作）复用。未开启作用域的既有入口继续每次读取；异常不缓存，保留调用方原失败关闭、审计和重试行为。AST及COM继续分别通过Owner API获取并裁剪范围，未改变SQL、授权规则、合同候选交集、筛选、排序或分页。

36 项定向测试通过，包含新增common作用域测试和yudao-server装配层调用链测试（测试依赖仅test scope）：真实AST→COM→SYSTEM/PROJ实现配合持久层替身，验证成功组织查询和项目解析各一次，下一次列表再次查询，租户/用户/动作隔离，DTO修改隔离，空范围，失败重试及异常后清理；既有合同授权、设备权限与SQL回归通过。验证证明代码调用次数与规则回归，不等同已部署接口时延或真实浏览器验收。日志及结果见 .run/device-permission-reuse-tests.log、.run/device-permission-reuse-results.json。未部署、未重启服务。

### 2026-10-23 普通用户全部项目权限改为批量树事实读取

按需求方授权，ProjectTreeScopeService.resolveAllFullProjectIds 保留候选发现（成员、创建人及Owner授权分页）和既有三种动作判定，普通用户不再逐树调用 resolve 查库。新增场景Query及Mapper XML：按租户/根集合以ROW_NUMBER选最新ACTIVE版本（tree_version、id倒序），按精确根/版本对批量取根路径，按每棵树自身的授权锚点集合批量取后代路径；空根/树/锚点集合失败关闭。候选版本发现后批量检查根项目，再批量复核版本，保留失配/缺失的失败行为。发现阶段不冻结最终授权：成员、创建人均在授权阶段再批量读取；权限动作、有效时点、VIEW附加MANAGE授权、EDIT映射MANAGE、仅显式后代授权扩展及租户边界保持。完整ID集合不计算无消费者的路径占位节点；单树resolve/resolveCurrent及写入lockAndRevalidate原路径不变。

固定工具链与工作树构建锁下55项相关测试通过：18项原单树权限测试、5项批量权限测试、1项真实Mapper SQL测试及31项设备/合同/装配链回归。多树三动作与原单树结果逐棵对照一致；覆盖跨树锚点隔离、无活动版本、版本变化、缺失根、Owner失败、发现后成员/创建人撤销、角色复核和空集合。MySQL只读验证3棵实际树及35条根/后代路径，新旧查询结果一致，4种空集合返回空结果。首次测试等待其他构建释放锁，未绕过锁；最新源文件已编译并完成重跑。日志见 .run/project-scope-batch-tests.log、.run/project-scope-batch-retest.log，结果见 .run/project-scope-batch-results.json、.run/project-scope-batch-mysql.json。未修改业务数据，未部署、未重启服务；不以这些证据宣称运行接口已切换或整体时延已提升。

### 2026-10-23 测试站点默认设备分页查询

针对需求方报告的172.17.90.178/customer-asset/devices，真实登录复现默认第一页无筛选查询：两次接口分别耗时27.479、26.642秒，均成功返回0条。仅对既有测试库npdms只读核查：3,486,084台未删除设备均无项目/公司投影，120,638条发货合同引用均无公司编码；COM仍执行既有未知公司/冲突保护。admin拥有PROJ全部当前项目范围，不等于AST未归属设备全表可见；其3,431个满足COM条件的合同与设备合同原文无精确交集。原值承接记录全部保留，不增加同步阻断、不补造归属、不改变权限规则。

查询现场捕获2,128,659字符的合同授权SQL及1,090,435字符的设备计数SQL。默认页改用COM现有完整授权接口，再以获授权合同限定AST候选查询，保留Java原文精确交集；有业务筛选的查询仍用原小候选路径。PROJ授权仅与AST实际已有项目键求交，避免无设备项目产生巨大IN；项目候选包含未来/过期关系，最终可见查询仍校验有效期。查询只访问各自Owner的表，未增加字符集转换、DDL或跨请求缓存。

3项失败用例先复现后修复；11类37项定向检查通过，覆盖租户、三路权限并集、Owner失败关闭、合同大小写精确交集、空集合、有效期、筛选与既有分页。测试库等价SQL只读探测合计约2.1秒；新增项目候选查询使用idx_ast_device_project索引、约2.5毫秒，不代表完整HTTP耗时。证据：D:/CodexTaskArtifacts/project-detail-followup/device-list-test-diagnosis-20261023/diagnosis.md；原110项未提交文件SHA全部不变。修复尚未发布至远端、未进行修复后真实浏览器验收；未执行业务回填、原任务操作、DDL、迁移或服务重启，59191未动。

### 2026-10-23 默认设备页取消重复合同候选枚举

按需求方“本地修复、连接测试库复验SQL效率”的本轮范围，默认页直接使用COM既有正式授权集合，最终仍逐设备按原合同号精确条件匹配；取消耗时约9.30秒的AST合同候选预枚举。有筛选查询、设备详情、权限并集与Owner保护均保留，未增加字符集转换。测试库当前数据上，串行完整SQL链合计15.2338秒降至5.5470秒；总数3,154,458及首页10条全部投影/顺序一致，额外4,544个授权键按原条件命中设备0台。新回归先失败后修复，7类29项检查通过。SYSTEM/PROJ请求内成功读取复用仍各一次，下一请求重读。

以上为真实MyBatis XML绑定SQL的只读实测，不含生产拦截器、HTTP或页面渲染；未加载本轮修复至59284或远端，不声明页面性能已验收。未执行DDL、迁移、同步或业务写入，原任务、59191及19191不动。详见[SQL实测与验证边界](../../docs/generated/device-page-performance-20261023/report.md)。不改变Feature总体完成状态。

### 2026-10-23 设备清单项目与合同授权改为SQL关联

按需求方要求，项目和合同授权通过各Owner维护的固定公开XML契约在数据库内关联；复用PROJ候选根发现、根校验及当前版本复核，最终成员/创建人/PLT有效授权重新读取。默认页不枚举项目ID或合同号，计数、当前页和续保聚合复用同一授权CTE，保持组织配对、原合同号唯一性、公司冲突、精确匹配及空范围规则。有筛选时裁剪原文候选，同号冲突仍检查完整正式合同表。

真实MyBatis参数绑定＋租户拦截器＋Connector/J只读复验：默认查询12个参数/4,564字符；SQL链5.8627秒降至单次关联4.2923秒，第二页3.8277秒。79,429个项目授权ID集合、3,154,458总数及第一页10条完整投影/顺序一致；24个原生作业与12类62项不同定向检查通过。源码稳定性检查捕获一次并行P14测试新增，保留其改动后补跑通过。无DDL、迁移、同步、重启或59191/19191操作，修复未加载至59284或远端，未声称页面性能已验收。详见[SQL关联复验记录](../../docs/generated/device-page-sql-join-20261023/report.md)。不改变Feature总体完成状态。

## 2026-10-24 设备合同分支收敛组织范围（z01010反馈）

需求方确认Q-AST-DEVICE-CONTRACT-SCOPE-20261024的建议口径：设备合同关联必须匹配同一有效公司部门范围行，公司级授权仍覆盖公司；组织与明确项目授权继续独立OR，COM合同目录保持原公司编码范围。复用COM既有合同/发货合同组织事实聚合及SYSTEM范围，不修改实际用户授权、历史或设备归属，无DDL。原实现真实浏览器复现：z01010默认1042435条，“杭州”79428条；无项目设备02010502N224800206（合同31020220510A04、公司001、部门253/162022）列表及详情可读，部门授权实际为178/470/94。证据.run/user-org-scope/z01010-device-detail.png、.txt。

实现：COM设备ReadScope由公司编码集合改为成对组织范围；公开XML聚合唯一合同和发货合同组织事实并按组合授权，保留合同原文、同号唯一及公司冲突保护，去除项目合同关系对合同分支的独立扩大。详情/档案/日志/下载共用getVisibleContractNumbers在原合法合同集合上再取组织授权交集；明确设备项目授权独立保留。前端不靠过滤隐藏数据。

验证：固定工具链与工作树锁下9类61项通过（SYSTEM6、COM17、AST34、装配链4），包含同公司跨部门拒绝、同一行配对、公司级范围、明确项目授权、合同冲突及Owner失败关闭。实际MySQL只读连接执行生产XML：z01010当前组织范围查询杭州SN02010502N224800206为0、长春SN02230029D239025060为1；仅在内存改为公司级范围后杭州SN为1，不写实际授权/设备/合同。日志.run/device-scope-20261024/full-tests.log。新增合同服务测试首次发现候选Set在查询后被retainAll改变，已改为传入不可变候选副本并重跑通过。最初定向测试被其他构建锁阻止，等待原锁，未并行写target；未将未执行的失败测试标为通过。本会话未再次部署或重启；共享运行环境已被其他会话更新，当前e280a1b8发布清单源码指纹f087551c5539730197d0adb880dec536d9500d8ac36b7e6d9c873071818060c6与当前后端源码一致。该包只作为现有运行环境证据，不作为本会话构建产物。真实浏览器当前连接拒绝，尚未完成修复后页面验收；原59191范围维护包的批准不自动覆盖后续重启授权。

后续收口：本会话使用worktree-build.ps1重新完整构建并冻结448d14f2-70e8-4da8-bba2-a59386b57692（源码指纹同f087551c），22项保护修复校验及worktree-backend.ps1 -ValidateOnly通过，未再次重启。浏览器恢复后z01010实际页面查询SN02010502N224800206为0条，SN02230029D239025060为1条，默认页面当前38754条（不将共享数据变化全部归因本修复）。证据.run/device-scope-20261024/z01010-denied-after.png、.txt及z01010-allowed-after.png、.txt。修复后详情/档案/日志/下载的拒绝由服务共用路径及定向测试覆盖，未逐入口执行真实浏览器拒绝验收。
