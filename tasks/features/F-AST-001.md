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

## 2026-10-27 TF-039 项目设备清单与序列号导出

- 授权：需求方批准独立导出权限授予项目经理、服务经理，数据保持各自获授权项目范围。旧功能范围仅为项目详情既有设备清单/序列号页签的导出补强，不新增设备事实或改变订单数量口径。
- 复用审计：设备清单由COM当前ACTIVE交付范围关联订单行，去重订单行后汇总已发货/未发货数量；序列号由AST当前项目设备、DEVICE_LOG在网版本及续保记录投影；既有页面分页不能直接作为完整导出源。Excel复用平台ExcelUtils，跨模块只使用公开API。
- 实施：独立`pms:project-device:export`权限；COM/AST分别提供项目定界、游标分批的导出读取及固定列Excel响应。服务端复核当前租户、用户、设备查询/导出权限和PROJECT_VIEW完整项目范围；办事处只读通道拒绝文件接口。序列号保留当前SN精确、名称包含及产品编码精确筛选，数量未知保持空白。
- 验证计划：失败测试覆盖缺少导出权限、项目不在范围、跨租户、超过批次、订单行去重/未知数量、查询失败不返回部分文件；前端验证两个按钮、筛选和只读保护。新增权限种子仅准备，运行权限通过获批的正式SYSTEM接口配置；不运行178 Flyway/DDL。
- 实施结果：两个项目导出接口和前端入口、V518独立权限种子已完成；COM继续复用既有订单行公司/合同项目范围，不以分配关系放宽其读取权。22项后端检查通过（真实Mapper/租户拦截器、130/132行分批、服务501行、Excel内容、权限种子幂等与历史），12项前端运行时测试、定向类型检查及ESLint通过。先失败后修复日志位于.run/feedback-20261026/tf039-*-before.log，最终tf039-verified-tests.log与tf039-frontend.log。冻结ebf7348c-e211-433f-b463-904cc4731a38经2026-10-27 15:45单独批准启动59191，PID13304、local,dac、健康UP且运行哈希一致；正式SYSTEM接口配置后SM/PM分别209/179项，各仅新增导出权限。期间Number转换18位菜单ID造成SM12项原授权短暂缺失，已用字符串ID恢复208项并独立核验后再追加权限，异常和恢复证据完整保留；PM未受影响。未执行V518或其他迁移，不提升Feature总体状态。
- 浏览器补正与验收：新下载API补Business API baseURL，4项URL回归先失败后通过。84459经理真实下载15行设备清单（数量119）、130行序列全量及1行精确SN筛选，Artifact Tool/原始OpenXML独立只读核对通过；渲染器省略文本数字前导零为预览限制，原文件与读取值未丢失。实际响应缺少文件类型头，已在两个新增Controller写流前设置附件响应头；受影响后端检查通过。新补正包3f888267-6df9-4c00-86d7-c91f84c97086完整构建、22项保护和固定脚本预检通过，经需求方本次单独批准重启59191，16:46核验PID34676、local,dac、健康UP及运行哈希一致。两类新下载响应头均正确，15/1行文件与补正前逐格值和类型一致，三类下载验收完成；权限未再次调整。完整证据与操作异常见docs/generated/2026-10-26-test-feedback-triage/问题跟进.md。

## 2026-10-27 TF-048 当前型号只读补充

2026-10-28当前规则修订：需求方在TF-043明确复用正式主档物理唯一键，不默认限制来源/状态、不作归一产品聚合。当前共享读取已改按同租户 `UNIQUE(tenant_id,product_code)`、设备编码trim及未删除主档索引唯一读取，设备非空型号原样优先；无写回/历史保护不变。此前CRM/ACTIVE、逐字节归一冲突与分组说明保留为当时实施历史，不再作为当前规则。实现、25项直接消费者回归及尚待≤1秒真实验收统一记录于F-IMP-005的TF-043小节。

- 授权：需求方确认设备型号为空时按精确产品编码采用唯一有效CRM产品型号供展示和搜索，无匹配或多记录保持空值，不写回设备、不改历史快照。
- 旧实现审计：AST设备主档/设备锁定查询保存原始型号；ProjectDeviceShipmentMapper和ProjectDeviceSelectionMapper的列表及过滤直接读取该列。DeviceQueryApi当前详情由原设备查询授权后构造DTO，前端割接新建及设备表复用这些只读接口。CRM同步副本已提供itemCode/itemModel对应关系，现无设备查询回退。
- 实施顺序：先在AST提供统一当前型号SQL表达式及按设备ID批量只读查询，接入候选显示/检索和当前详情；所有写入、锁定查询、版本历史保留原始值。完成该局部增量后再推进TF-039导出；不并行扩展其他Feature。
- 验证计划：实际Mapper SQL验证唯一/多记录/空型号/停用/跨租户和计数分页一致，当前详情测试验证已有型号优先且DO未改写；按固定工具链和锁运行定向测试。部署后真实浏览器复验57302的型号展示与检索，不创建割接任务。
- 当前验证：原2项失败修复后相关35项通过，随后原文二进制匹配修正11项通过；但ebf7348c部署后的原账号/原项目真实型号搜索仍为code0/total0。只读DB确认318台编码含8个尾部填充空格，初始诊断trim输出掩盖了该格式。现按既有产品引用trim惯例逐字节比较并一次分组唯一CRM副本，已有值、大小写/内部空格差异、归一冲突和租户保护保持。先复现2项填充/冲突失败，最终16项受影响后端检查通过；其中H2仅以VARBINARY模拟MySQL无长度BINARY的完整字节语义，所有业务断言不变（[H2说明](https://h2database.com/html/datatypes.html#binary_type)、[MySQL说明](https://dev.mysql.com/doc/refman/8.0/en/cast-functions.html)）。另用真实MySQL READ ONLY事务、SELECT/CTE字面量验证未改写生产表达式的14个结果通过，无DDL/DML；实际318台按192/96/24/6匹配四型号，原字段未改，投影查询约40ms，不代表HTTP时延。新补正包3f888267经本次批准部署后，原账号y04503/原项目57302真实浏览器空关键字318行、SFP型号192行及第二页20行、切换ADX型号回第1页6行均符合当前数据，型号显示正确且无旧查询残留。保留原始填充编码，取消表单且无设备选择/任务保存。最终证据tf039-tf048-final-browser-acceptance.json、tf048-model-search-final.json及截图；不据此提升Feature总体Done状态。日志tf039-tf048-browser-regression-verified.log，证据tf048-current-model-database-{grouped,fixtures}.json。既有DeviceSourceFallbackTest的ITR与DEVICE_LOG来源不符失败保留，不降低规则。

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

## 2026-10-28 项目内设备当前分页复用与性能（旧功能范围）

- 需求方要求同步核查所有项目内设备查询。本增量复用现有当前列表SQL：受信租户、当前登录主体及目标项目 `PROJECT_VIEW` 完整范围均由PROJ确认时，直接使用原 `selectVisibleDevicePage` 按租户和目标项目索引分页；不准备全局项目/合同范围CTE。没有目标完整范围、仅占位或PROJ探测失败时，仍走原全局关联SQL的项目/合同/组织三路OR授权。姓名类关联筛选、SN/产品编码/状态/客户/部门条件、排序、投影及越界页真实总数保持。
- `deviceListColumns`的两个当前列表消费者统一引用既有 `currentProductModel`：设备自身非空型号优先，空值按本租户唯一产品编码补充；主档逻辑删除/跨租户不参与，缺失保持空，不写死产品来源或启用状态、不新增聚合、不写回设备。影响项目序列号、安装/工勘材料、方案/复核方案设备引用、完工证明及CUT清单空白补充等当前列表消费者，也影响全局设备当前列表；不可变设备版本、安装历史、割接冻结事实及原始主档不变。网络版本 `DEVICE_LOG` 来源与分页续保次数继续遵守既有SDS10契约。
- 失败与回归：原FULL分流失败用例先失败，32项修复后通过；随后缺本地型号用例在旧投影返回NULL时失败。统一投影后11套件55项通过，包含actor/tenant/VIEW、非FULL及Owner失败fallback、10种全列/排序/total分页等价、缺/已有/空白本地型号、缺/删除/越租户/停用/其他来源主档以及5000树SQL解析。所有后端检查持固定工具链和工作树独占锁，日志 `.run/feedback-20261028/tests/project-device-full-page-before.log`、`project-device-full-page-after.log`、`device-current-page-model-before.log`、`device-current-page-model-after.log`。
- 178库只读SQL对照：在已证明目标FULL的比较前提下，79557和84459原关联页约0.30～0.38秒，复用直查列表约3～5毫秒、COUNT约1～2毫秒，行/排序/total一致；这不是普通账号授权或HTTP性能验收。型号投影调整后两项目各100行全部列与原投影一致，约6.6/7.9毫秒；查询投影内模拟缺本地型号时使用 `uk_product_official_tenant_code` 单行 `eq_ref`，没有改实际设备或产品。证据 `.run/feedback-20261028/acceptance/project-device-full-scope-readonly.json`、`project-device-full-page-compare-readonly.json`、`device-current-page-model-readonly.json`。
- 尚未部署或完成指定项目详情阶段任务的真实浏览器复验；没有迁移、DDL、业务写入或服务启停，不将SQL毫秒值宣称为所有设备查询已满足1秒。

## 2026-10-28 当前设备摘要批量读取与项目快路（旧功能范围）

- 既有项目详情、CUT与巡检流程的当前设备摘要复用`getVisibleDeviceSummaries(DeviceSummaryBatchQuery)`。受信租户和actor必须等于当前登录上下文；空ID集合直接返回空，DTO仅增加可空name/locationSnapshot，型号继续复用唯一当前型号补全。单台详情同一受权直读，缺项保持AST_EQUIPMENT_NOT_EXISTS，不再先EXISTS再按ID回表。
- 可选projectId是查询提示。PROJ确认当前主体PROJECT_VIEW FULL后，AST以租户、当前直接项目和本次ID集合单表Lambda读取，不强加合同号、来源或状态；保留已命中行，仅缺项进入原组织/项目/有效参与关系/合同OR授权。无FULL不能通过提示获权。通用缺项分支复用PROJ/COM公开SQL，所有AST候选和最终记录先限定本次IDs，不再展开79,433项目或全合同集合。COM索引预筛只转换候选binary key，原binary精确比较、同号冲突、公司部门同一行和两Owner独立失败关闭均保留。
- HTTP批量入口沿用pms:device:query，仅接受deviceIds和可选projectId，tenant/actor由服务端取；前端WireLong保持number|string，不转Number。CUT消费者保留原逐项错误次序、缺项位置及后续归属/版本/发货校验；项目全集少任一受权设备仍整批失败。P2/P4历史迁出冻结行保持，当前补空仍受原资产权限；非草稿/已提交历史不以实时资料改写。
- 失败与验证：项目提示部分命中红测17项中1项按预期失败；修复后AST9类58个唯一用例已通过，覆盖当前actor/tenant/FULL、伪造项目提示、只缺项fallback、单表空范围、所有原授权分支、SQL结果/排序/总数与生产解析器/cache。CONVERT(candidate USING utf8mb4)通过5000树生产解析；原CHAR CHARACTER SET解析失败日志保留。HTTP请求字段旧枚举断言修正后所属5项重新通过；主体租户新守卫所属6项重新通过。TS批量API两项WireLong/可选提示测试通过。
- 消费者回归：CUT本轮94项中93通过，1条既有无客户等级用例与正式手选必填契约冲突而报错，保留原断言和日志，未放松生产规则。巡检6项通过。此前DeviceSourceFallbackTest的ITR/DEVICE_LOG既有失败同样单列，不计入本轮功能通过数。
- 178库只读证据：79557直接232行约23.7ms、84459直接130行约8.8ms（含20行空合同号），原项目集合全字段/排序一致，混入外项目/不存在ID不返回，跨租户为空。受限合同候选20行原始DO完全一致，索引预筛约9.9ms，idx_contract_no第二键生效，8组大小写/重音/Unicode/尾空格/通配字面量保持原二进制结果；这些是SQL诊断，不是普通账号HTTP或浏览器验收。
- 证据：`.run/feedback-20261028/tests/device-bounded-scope-project-before.log`、`device-bounded-scope-project-full.log`、`device-project-context-verified2.log`、`device-project-context-remaining.log`，以及acceptance目录下`device-project-ids-direct-readonly.json`、`device-summary-bounded-cte-readonly.json`、`device-summary-contract-convert-readonly.json`、`device-contract-convert-literals-readonly.json`。均未部署、重启、DDL、迁移或写入业务数据；真实浏览器验收由项目详情阶段任务入口另行完成。
