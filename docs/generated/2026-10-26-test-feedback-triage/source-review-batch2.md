# 当前代码源审计（批次2）

范围：TF-024/025/026/031/032/034/038/041/043/044/045。只读当前工作树代码与测试；未运行检查、未做浏览器验收。结论是代码路径证据，不代表反馈已业务复现。

## TF-024 产品清单引用字段缺失

- 入口：`yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/solution/SolutionChapterForm.vue:119-143` 的“引用到正文”按钮调用 `scopeProductRows()`；实现位于 `:486-496`，源 `scopeRows` 为交付范围产品编码、设备类型和分配数量（`:428`）。映射只填产品编码、型号（deviceTypeCode）和项目数量，描述、发货/未发货数量、序列号明确置空。方案正文按这些字段生成（`:690`）。
- 旁路产品/设备清单装载位于 `yudao-ui/yudao-ui-admin-vue3/src/api/pms/commerce/projectEquipment.ts:34-111`，汇总产品名称、型号、订单数量及已交/未交数量；但 `SolutionChapterForm.vue:486-496` 的正文引用没有接入该结果。部署/软件表的单品选择会带入名称、订单数量，见 `solutionEquipment.ts:4-6`、`SolutionEquipmentTable.vue:41-46`。
- 现状判定：代码路径可确认“引用到正文”不会带入描述、发货量、未发货量、SN；与反馈方向一致，属于可定位的前端映射缺口。产品型号是否应取 deviceTypeCode 还是源产品型号、各数量字段的正式口径仍需规格/来源确认；未据此擅定业务映射。未发现针对该正文引用映射的组件测试。
- 缺少数据：同项目交付范围产品编码、设备/订单行、源清单字段值以及点击引用后的正文/文档样本，确定具体期望字段与缺失表现。

## TF-025 生成文档拓扑仅保留链接

- 入口：`SolutionChapterForm.vue:152,172` 上传字段写 `topology.asIsUrl`、`topology.toBeUrl`；文档HTML拼装在 `:693,697`，将URL转成“已上传：<URL>”文本；下载生成HTML在 `:726-743,748-761`。
- 现状判定：该导出实现没有读取文件内容或插入图片标签，只输出链接文本，足以解释此实现下“文档未嵌入图片”。没有针对导出拓扑图片策略的测试。上传格式、预览转换、浏览器/Word打开效果尚未由真实样本验证。
- 缺少数据：用户上传的文件后缀/脱敏样本、生成文档文件及其打开方式；确认需求是HTML/Word内嵌图片还是可点击链接即可。若要求内嵌，尚需确定受支持格式及转换契约。

## TF-026 生成文档缺少公司 LOGO

- 入口：同一 `SolutionChapterForm.vue:726-743` 构建的HTML仅含标题、章节和内联样式，未引用品牌图片/页眉模板资源。
- 现状判定：当前构建函数本身没有LOGO元素，能解释当前HTML产物无LOGO；但没有核实锁定模板/正式品牌要求，故不能仅凭反馈断言业务缺陷。未发现LOGO模板测试。
- 缺少数据：适用方案模板/批准品牌规范、预期LOGO文件或模板位置及实际导出样本。

## TF-031 驳回后无法修改原方案

- 入口：`SolutionServiceImpl.java:93-100` 更新仅允许状态0草稿；拒绝时状态变为已驳回（方法 `:169` 起）。前端 `yudao-ui/.../engineering/solution/index.vue:53-56,196` 将非草稿只读，列表有“新增方案”入口（`:31`）。新建草稿 `SolutionServiceImpl.java:61-85` 强制状态0并生成新编号。
- 现有修订能力：`SolutionTieredReviewService.java:174-184` 有 `revise(Selection)`，需已结束审批状态并复制来源方案创建新方案；目前在方案列表页面没有看到该修订入口（只显新增/查看等，`:53-107`）。服务端旧方案内容与历史保留。
- 现状判定：驳回原记录不能原位改是状态机的现有设计；存在复制为新版本的服务端方法，但UI缺少明显修订入口迹象，可解释“只能另增方案”。是否必须同一原任务继续修订、是否有其他调用方需全链核实；不能把状态机限制自行改成可直接编辑。
- 缺少数据：原方案ID/项目/审批实例与状态、报错页面/账号权限；核对是否本应由原任务触发 `revise`，并确定新方案如何关联原任务。

## TF-032 工程管理部复审授权未配置

- 入口：`SolutionReviewPolicyService.java:24-45,80-94` 按项目字段规则判定重大/普通复审；提交时缺规则或冲突会 fail closed。`SolutionTieredReviewService.java:100-108` 启动实际BPM复审实例；候选授权流程需要从流程定义/授权配置追踪，不能只据方案代码确定谁应持有。
- 现状判定：当前服务有复审适用判定，不足以证明具体项目已配置规则或复审审批持有人；截图描述更像配置/流程路由事实，不能据代码判定为实现缺陷。未找到可对应这两个项目的运行配置/实数记录。
- 缺少数据：两个反馈对应项目ID、方案ID/版本、错误原文/请求时间、适用规则评估结果、流程定义ID及候选/授权绑定记录；还要确认该项目是否适用复审。

## TF-034 新方案审批通过但任务不完成

- 入口：任务完成只认当前项目实施方案批准事实。`SolutionApprovalCompletionProvider.java:139-153` 对状态3、批准人/时间、基线版本与当前版本相等进行验证；复审级别1还要求 `tieredReviews.approved`。测试 `SolutionApprovalCompletionProviderTest.java:48-57` 覆盖批准、复审、驳回状态及版本不一致。
- 修订入口 `SolutionTieredReviewService.java:174-184` 创建新方案；前端列表/详情可查看方案，但是否把新方案关联回原工作任务，当前不能从反馈文字单独确定。批准新方案不会自动把旧方案记录状态改成批准。
- 现状判定：已有任务完成事实校验且有单测，不能证明反馈实例闭环正确；可能涉及任务引用仍指旧方案、复审未结束、基线/版本不一致或任务实例失效。尚无证据认定代码正确或缺陷，也未做浏览器/运行数据验收。
- 缺少数据：项目、原任务ID及执行版本、原/新方案ID与版本、审批实例最终状态（含重大复审）、任务对象绑定及准出错误。需按该链逐步核对。

## TF-038 发货状态或数量不完整

- 入口：设备档案列表查询在 `pms-module-asset/src/main/resources/mapper/device/DeviceQueryMapper.xml:210-235` 返回设备级 `shipment_time`、shipment_record_id 等；`DeviceListRespVO.java` / `DeviceController.java:225` 映射设备字段。方案正文交付范围映射 `SolutionChapterForm.vue:486-496` 把发货数、未发货数置空；产品清单汇总器 `yudao-ui/.../api/pms/commerce/projectEquipment.ts:106-111` 可按订单行返回已交/未交数量。
- 现状判定：当前设备列表表达的是单设备最新发货时间，没有产品/License批次状态及数量列；方案产品清单引用路径又未填充发货量。可确认方案引用存在数量空值路径；反馈中“License/设备状态”是否同一入口、源订单行/发货记录是否齐全，尚不能确认。`ProjectDeviceShipmentMapper.xml:16-35` 另有联调选择候选仅纳入有非空SN且已发货设备的查询，但不等于设备档案或方案统计实现。
- 缺少数据：具体产品编码/SN、License产品类别、项目及订单行、发货记录/批次、页面入口和期望状态/数量口径。需区分设备逐台发货状态、订单行部分交货、License授权/发货。

## TF-041 安装地址/物理位置无法更新

- 入口：`ProjectInstallationWorkbench.vue:9-23` 提供按设备“维护安装地址”；`InstallationServiceImpl.java:124-141` 可更新未完成安装记录并应用位置关系；但对 status=2 的已完成记录明确拒绝编辑（`:130-131`）。页面安装历史从 `:37-40` 仅可查看历史。
- 现状判定：已完成记录被禁止原位改写，并保留历史；当前工作台仍有维护入口，无法据此判断用户是想修正未完成记录、改已完成安装历史还是产生新的位置变更记录。未发现足以断言普遍无法维护地址的缺陷。
- 缺少数据：目标设备SN、安装记录ID/status、编辑/批量维护入口、实际操作角色与授权、错误信息、是否需要新位置生效及原位置历史保留方式。

## TF-043 业务联调设备候选少于项目设备清单

- 入口：联调表单 `yudao-ui/.../engineering/joint-test/with-device-logs.vue:145-146` 使用 `ProjectDeviceSelect`；组件 `yudao-ui/.../components/ProjectDeviceSelect/index.vue:2-18` 发送 `selectionProjectId`；后端 `ProjectDeviceSelectionService.java:34-43` 根据项目及关联合同号查询候选；SQL `pms-module-asset/src/main/resources/mapper/device/ProjectDeviceSelectionMapper.xml:4-27` 仅纳入当前项目设备或合同号关联设备，再按设备/型号等过滤。提交时仍由 `JointTestServiceImpl.java:56` 校验所选设备属于可选项目范围。
- 现状判定：候选不是任意项目档案全量设备；它受项目设备关联和合同范围限定。是否遗漏实际项目设备，需比较同项目设备主档、合同关联与搜索条件，不能用“项目设备清单”一词推断应全选所有档案设备。相关候选范围SQL有MySQL合同测试，未覆盖反馈项目的真实关联。
- 缺少数据：反馈项目ID、缺失设备SN/ID、项目设备清单导出、对应合同及发货/项目关联、联调查询关键词与账号权限。

## TF-044 show tech-s 日志版本信息未识别

- 入口：后端 `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/DacDeviceLogParsingGateway.java:36-49` 把日志文本送至DAC `/api/v1/log-parses/actions/normalize`，验证响应绑定/字段后接收命令块、序列号和warnings；Java代码本身没有实现设备版本解析。`DacDeviceLogParsingGatewayTest.java` 仅测HTTP/响应契约，不覆盖解析器识别厂商格式。
- 现状判定：缺少真实/脱敏输入和DAC输出，不能确认其是支持格式缺陷、人工构造格式不合契约、还是DAC解析服务问题；仓库此调用链无该人工样本的失败用例。
- 缺少数据：脱敏原始文件（含文件格式/编码）、日志来源设备厂商与型号、完整show tech-s版本段、DAC解析请求与响应/告警、期望版本字段及对照真实设备日志。

## TF-045 日志含序列号仍报块缺少序列号

- 入口：与TF-044相同的DAC解析边界；前置`DacDeviceLogParsingGateway.normalize`只检验响应结构，不执行块/设备匹配。设备归属和解析结果后续由DAC解析接入链完成。
- 现状判定：从“第四份日志”描述无法验证切块边界、序列号落在哪块、四份导入次序或DAC返回serialNumbers；代码审计未定位到能单独证实的本地解析缺陷。
- 缺少数据：四份脱敏文件、上传顺序与单次/分批上传方式、每份块边界附近序列号文本、错误响应/日志块ID、目标设备SN及DAC解析结果。取得这些即可区分日志分块、正则识别与文件上传顺序问题，无需原始Excel补充。

## 审计范围限制

本批未读取原Excel/截图、未连接业务运行数据、未执行测试，也未验证浏览器行为。对TF-025/026的代码结论只针对当前HTML生成实现；TF-032/034/038/041/043及日志解析项需要各自列出的实际样本才能定性。

## 补查增量：确认项目内嵌实际入口（修正本批初稿）

前述第一次审计先看了 `engineering/solution/index.vue` 与同名表单；它不是项目详情当前的内嵌入口。项目详情 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/index.vue:591,639` 将 `solution` 页签装配到 `engineering/solution-reviewed/index.vue`。该组件 `solution-reviewed/index.vue:5-10,57-74` 在项目上下文渲染 `SolutionReviewChapterForm.vue`，所以以下结论以这条内嵌链路为准；前述旧表单现状只能视为独立/旧入口实现，不能作为项目内嵌验收入口。

### TF-024（当前项目内嵌路径）

- 实际正文“从产品信息清单引用”按钮在 `solution-reviewed/SolutionReviewChapterForm.vue:101`，当前引用来源由 `:748-765` 加载项目交付范围及其 details；`scopeRows` 只有 `productCode`、`deviceTypeCode`、`allocatedQuantity`（`:758-764`）。`scopeProductRows()` 在 `:472-481` 仅映射这三项，且 `desc`、`shippedQty`、`unshippedQty`、`sn` 均置空。正文HTML表格在 `:836` 输出这七列。
- 该实现并未调用前述独立旧表单使用的 `loadProjectEquipmentList()`。`specs/001-project-delivery-platform/domains/SOL-交付准备与方案需求规格.md:565-566,598` 要求清单引用EQP-01设备序列号档案，字段明列设备清单（序列号/型号/数量）；当前按钮来源却是交付范围details，而且明确不带SN。故确认有当前路径的来源/映射不一致迹象；数量、型号的具体映射口径仍需和正式交付范围/资产记录对照，不能用字段名猜。
- 缺：反馈项目ID、具体设备SN及对应EQP-01记录、交付范围明细/产品行、引用前后的实际正文和导出文档。可用这些对照判断究竟是来源没装载、details没SN，还是渲染漏映射。

### TF-025（当前项目内嵌文档不是HTML文件）

- `SolutionReviewChapterForm.vue:818-846` 构造HTML字符串，2.1/3.1只插入“已上传：URL”文本（`:840,844`），没有`<img>`或加载文件字节；下载按钮调用 `downloadSolution()`（`:10-12`），`:896-907` 将HTML传给 `prepareGeneratedSolutionWord()` 并生成`.docx`。辅助器 `solution/prepareGeneratedSolutionWord.ts:4-25` 调用`@turbodocx/html-to-docx` 转换成docx；所以当前实际导出是Word文档，且已调用转换器。旧 `engineering/solution/SolutionChapterForm.vue` 的HTML文件结论不适用于这个当前入口。
- 正式 SCH-01 规格 `SOL-交付准备与方案需求规格.md:565,569,598,589-594` 要求拓扑附件随方案/批准版本并可下载归档，且含拓扑图附件；当前正文的链接文本不等于嵌入图像。当前生成HTML未提供`img`来源，调用的转换器没有接收附件/图片字节，因此存在可定位的导出映射缺口。docx生成与上传是否成功仍需真实文件验收。
- 缺：当前已保存方案中拓扑附件的真实引用、文件类型/样本、导出DOCX及用Word打开结果。文件接受格式规格为PPT/PPTX/VSD/VSDX及PNG/JPG/JPEG/GIF/BMP/WEBP（同一SOL规格:569）；尚需确认不同格式怎样在导出里展示。

### TF-026（当前项目内嵌LOGO）

- 当前方案DOCX导出经 `SolutionReviewChapterForm.vue:888-892` 拼出标题、章节和CSS，`:872`只是售后文字，HTML内无图片；无论调用DOCX转换器，当前构造内容未引入LOGO。
- `rg`命中现有LOGO资产 `yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/completion-certificate/dptech-print-v2.png`，由 `acceptance/completion-certificate/WordPrintFrameV2.vue:4,17` 等验收证书/打印组件引用；未见方案组件引用。SCH-01正式规格上述段落和字段表没有“公司LOGO”要求，故现阶段只能确认方案导出代码没有LOGO、另有非方案场景的资产；缺少规范依据判断这是否应改。
- 缺：批准的方案品牌模板/LOGO摆放要求及实际导出样本。不得仅因证书有该PNG就当作方案LOGO的批准版本/用法。

### TF-031（当前项目内嵌修订调用链）

- 当前入口 `solution-reviewed/index.vue:37-43` 对非草稿显示“审批记录”，点击调用 `tieredReview.open(form)`；`SolutionTieredReviewDialog.vue:29` 在审批结束（非RUNNING）时显示“复制为新方案版本”；`:94-101` 点击后明确调用 `ReviewApi.revise(solution.projectId, solution.id)` 并刷新列表。API在 `yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/solution-review/index.ts` 的 `revise` 函数。
- 后端可复用能力 `SolutionTieredReviewService.java:174-184` 验证审批已结束，再复制原方案内容创建新草稿；`SolutionServiceImpl.java:61-85` 将新草稿初始化为草稿而不带原批准元数据，原记录不被原位改写。正式规则 `SOL-交付准备与方案需求规格.md:569,572,817-818` 明确驳回修订必须形成保留历史的新版本，因此该方向符合规则。
- 修正前述判断：项目内嵌UI已有“复制为新方案版本”链路，并非缺入口迹象；TF-031当前代码已有可复用处理能力。若用户反馈仍无法操作，需查具体审批记录是否加载/结束、按钮权限及点击请求结果；尚不据此判为当前入口缺陷，也未做浏览器验收。
- 缺：被驳回方案ID/项目ID、审批记录状态、账号权限、按钮可见情况与点击后的请求错误；还需现场确认生成的新草稿是否按预期回到方案选择器并能编辑。


## 补查增量：TF-048型号候选来源；TF-025/026图片生成复用能力

### TF-048｜项目57302设备选择器型号为空

- 证据 `.run/feedback-20261026/browser/tf048-project57302-device-inspection.json`：账号`y04503`在项目ID `57302`（项目编码`16030023110809N-0`）下跨16页检查318条设备候选，所有页`nonempty_model_count=0`；未选设备、未保存，控制台/失败请求为空。该证据证明实际候选的model字段为空，没证明应该搜索的目标型号、也没从该记录证明型号的真实期望值。
- 选择器入口当前支持`productModel`：`yudao-ui/yudao-ui-admin-vue3/src/components/ProjectDeviceSelect/index.vue:1-18`传项目筛选；`pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/device/ProjectDeviceSelectionService.java:34-43`转入`productModel`查询；`pms-module-asset/src/main/resources/mapper/device/ProjectDeviceSelectionMapper.xml:20-29`直接对`ast_device.product_model`做LIKE并返回该列。因此搜索失效与“设备候选型号为空”是数据投影问题，当前查询实现已有型号条件。
- 型号字段的两条现有来源不是同一对象：设备SN同步 `pms-module-integration/src/main/resources/sync/generic-templates.json:924-1045`从DPPMS发货条码用`item`写`ast_device.product_code`，且该设备target mappings无`product_model`；CRM产品同步同文件`:848-880`以`itemCode`映射`ast_product_official_info.product_code`、以`itemModel`映射`ast_product_official_info.product_model`。也就是说官方产品副本已有“按产品编码关联型号”的数据链，但设备SN投影当前只保留型号列，没有把该型号带到选择器读取的`ast_device.product_model`。
- 可复用关系：项目设备行有`product_code`，官方产品副本也有`product_code`，其CRM `itemModel`映射已有；按具体项目设备产品编码核对两表，可验证缺失是否能从官方产品副本补齐。资产产品API `pms-module-asset/pms-module-asset-api/src/main/java/cn/iocoder/yudao/module/pms/asset/api/product/AssetProductOfficialApi.java:7-13`及实现`.../AssetProductOfficialApiImpl.java:18-31`返回`ProductOfficialSnapshot(productCode, productName, productModel)`，但仅按产品ID批量取，设备DO当前无productId且该接口不接产品编码。不能直接把订单型号、`deviceTypeCode`或产品名称猜成设备型号；项目内可追溯订单行字段也需单独核对。方案产品清单工作台将`deviceTypeCode`当model，而交付范围转换服务`pms-module-commerce/.../AssignedDeliveryScopeQueryService.java:83-95`也是该映射，故该值域不能不加验证地代替实物型号。
- 判定：存在明确的字段装载缺口迹象（设备候选全空且同步配置未写型号），同时已发现官方产品副本可按产品编码追溯的重用关系；尚缺57302的318行`product_code`与`ast_product_official_info`命中结果，无法确认每条实际应显示什么、是否全部唯一/活跃命中，或缺失来自CRM产品记录本身。修复需依正式来源规则决定是读取官方副本关系还是改同期投影映射，不能写死型号/手工补值。

### TF-025/026｜DOCX图片和LOGO的现有能力

- 项目内嵌路径已确认：项目详情装配 `solution-reviewed/index.vue`，其`SolutionReviewChapterForm.vue:818-850,896-907`构造HTML并调用`prepareGeneratedSolutionWord`输出DOCX。HTML内拓扑仍以链接文本输出（`:840,844`）；未放`img`。
- 当前锁定转换器`@turbodocx/html-to-docx@1.23.1`（`yudao-ui/yudao-ui-admin-vue3/package.json:46`）本身具有HTML图像转DOCX能力；其随包README在`:122-141`给出`<img src="https://...">`和`imageProcessing`示例，类型在`node_modules/@turbodocx/html-to-docx/index.d.ts:98-108`包含下载重试/超时/大小等配置。目前调用器`solution/prepareGeneratedSolutionWord.ts:16-23`确实调用同一转换器，但选项仅指定`svgHandling: 'native'`；没有额外的拓扑图请求/转换。故无需再引文档库也有转换基础，当前缺口是HTML生成器未将附件图片作为`img`提供。
- URL转DataURL工具已有：`yudao-ui/yudao-ui-admin-vue3/src/utils/filt.ts:38-57`的`urlToBase64(url,mimeType)`用`Image`+canvas转换，现有`downloadByOnlineUrl`只在同文件内部调用（`:73-74`），方案生成链没有调用。它适用于浏览器可读/允许跨源的图片；实现无`img.onerror`处理，跨域canvas也可能因CORS失败，私有附件是否需授权取流不能假设适用。
- 已有品牌PNG为`yudao-ui/yudao-ui-admin-vue3/src/views/pms/acceptance/completion-certificate/dptech-print-v2.png`，证书打印通过`WordPrintFrameV2.vue:4,17`直接import作HTML `<img>`；该导入图路径可作为静态资源复用方式参考。将静态图`img`放入DOCX输入HTML后由现有转换器处理在技术上有既有能力，但产品方案是否应使用该品牌图仍需批准模板/规范支撑：SCH-01正式规格中未发现方案LOGO要求，因此本核查不将资产存在视为业务要求。
- 判定：TF-025有成熟可复用的DOCX图片转换工具和URL→dataURL工具；后者并非方案链现有能力且有CORS/错误处理限制。TF-026可复用已打包PNG及静态资源导入方式，但是否放入正式方案DOCX仍待模板/品牌规则证实。


### TF-048｜官方产品副本按编码补型号的唯一性与查询影响补查

- `sql/migrations/V110__fast001_device_shipments_and_software_versions.sql:67-92` 中 `ast_product_official_info` 同租户仅对 `(tenant_id, source_system, source_key)` 唯一；`(tenant_id, product_code, product_model, deleted, id)` 是普通索引，`product_code` 没有唯一约束。表同时保存 `status`、`sync_status`、`deleted`，但没有专门的历史/当前版本标记。故相同租户+产品编码可有多个来源记录或不同型号；仅按 `product_code` 直接JOIN不能保证候选一行对应一行，COUNT和分页可能被重复行放大。当前迁移结构也没有规定“ACTIVE优先”或来源优先级，不能自行挑选一条。
- 在已检索的资产 Mapper/XML 中未发现 `ast_device` 到 `ast_product_official_info` 的既有按编码关联；设备产品码当前在 `DeviceDO.productCode`，而选择器 XML 返回 `d.product_code,d.product_model`。官方产品 API 仅按产品ID取快照，设备没有该ID，API不提供按编码查找（前文接口引用）。尚未以57302实际318个产品码核验官方表命中、重复、状态及型号冲突，因此当前只能确认可追溯字段关系，不能确认安全候选结果。
- 若规格后续批准按官方副本取值，候选SQL需先有明确的同租户、未删除、同步成功、业务状态及重复/来源裁决规则；裁决结果须保证每台设备至多一条型号记录。随后在 `ProjectDeviceSelectionMapper.xml:19-38` 的共同 `candidateScope` 中按该关系投影型号，并让 `productModel` LIKE 搜索过滤同一裁决结果；count及page必须基于同一个一行一设备的集合，保留现有租户、deleted、项目/合同候选边界、排序与LIMIT/OFFSET。`selectSelectionPage`（Mapper.java:14-32）是另一条wrapper查询，若实际UI走此路也要统一其型号投影、筛选和分页总数语义。保存验证 `:39-54` 仍只按设备ID及项目/合同范围锁定设备，不因只读型号补充扩大可选范围。
- 可复用验证：`ProjectDeviceSelectionMysqlTest.java:33-77` 加载真实生产XML并在限定隔离MySQL schema内回滚夹具，适合验证联接后租户/逻辑删除/重复型号/总数/页边界；但启用前仍需确认隔离环境。`ProjectDeviceSelectionMapperTest.java:20-65` 执行真实Mapper XML并已有候选总数和分页断言，可扩展为重复产品记录不会重复设备及型号过滤/投影的快速SQL回归。`AssetProductOfficialApiImplTest.java:37-60` 只验证ID批量取ACTIVE快照，不覆盖产品编码查找及重复裁决，不能替代前两项查询验证。
- 结论：按产品编码补型号码存在字段关系，但同租户产品码不具数据库唯一性，且状态、同步及重复记录优先规则未由当前证据确定；TF-048若实施SQL补查必须先明确规则并取得57302实际关联核对，当前判断为 `BLOCKED_BY_SPEC`，不建议直接JOIN或伪造唯一型号。
