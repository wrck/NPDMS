# F-CUT-003 P3动态采集清单、直接填写与人工降级闭环

> Feature实施状态：`IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO@ea986d61`
> Technical Plan Gate：`PASS / GO@ac740458`
> Implementation Done Gate：`BLOCKED_BY_DEPENDENCY`
> Requirement：`CUT-03@V1=FULL`
> Feature Spec：`specs/features/F-CUT-003-p3-dynamic-checklist-and-manual-fallback.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-08-31-f-cut-003-p3-dynamic-checklist.md`
> master集成映射：`codex/f-cut-001-matrices@faed8387 -> master代码回执c9066332；来源V147/V148 -> master V178/V179`
> master复验：`CUT共享后端242项（跳过MySQL 27）与前端68项零失败；真实MySQL、生产Owner装配和真实浏览器未复验，状态保持IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_BY_DEPENDENCY`
> 前置Feature：`F-CUT-001`、`F-CUT-002`
> 外部硬依赖：F-AST-002公开产品类型能力已选择性进入master，但CUT生产写事务内原子重验合同与唯一装配尚未闭合；`src/test`受控替身只支撑CUT自身正向验证。生产API接线继续阻断生产Controller装配、真实浏览器和Implementation Done

## 当前最小工作单元

- 当前进入Task 2，在Task 1已通过基础上接通P3工作台，并继续补齐自定义项移出与采集请求。
- 正向收益优先：先打通A/B/C按冻结配置生成DRAFT，DIRECT/MANUAL/CUSTOM保存，并原子提交到P4；不建设权限、冲突、乱序、跨租户、Provider失败或重试测试矩阵。
- V147落文件前重新确认连续空闲迁移号；若已被并行迁移占用，只整体顺延编号，不改变已批准语义。

## Task 1：CUT清单内核与前向迁移

状态：`PASS / GO@372f6895`

- [x] 为`cut_task`增加冻结配置三元组，并按获批历史规则完成NEW_PLATFORM唯一补齐；LEGACY_FORWARD保持空。
- [x] 新建`cut_cutover_checklist`、`cut_cutover_checklist_item`、`cut_cutover_checklist_item_result`及最小权限菜单。
- [x] 实现按冻结revision读取、稳定匹配、GAP/CONFLICT、DIRECT/MANUAL/CUSTOM结果和Generate/Rematch/Save/Submit命令。
- [x] 复用F-CUT-002 Owner锁序、平台幂等与PLT公共文件事实；F-AST-002产品类型只保留CUT消费端口，使用`src/test`受控正向替身，不实现或注册跨模块生产Provider。
- [x] 验证历史唯一补齐、A/B/C正向生成与保存、提交后Checklist=SUBMITTED且Task=P4；CUT模块54/54及受影响Reactor构建通过。

完成口径：CUT内核与迁移具备可执行正向链后直接进入Task 2，不单独申请Implementation Done。

## Task 2：P3工作台与一次正向验收

状态：`IN_PROGRESS / FRONTEND_CONTROLLED_LOOP_PASS_GO@ec268ab9 / PRODUCTION_ACTIVATION_BLOCKED_BY_DEPENDENCY`

- [x] 已用显式测试装配实现CUT清单REST与文件策略候选；生产Owner未就绪时不注册生产Controller/Service/Fake，生产激活保持`BLOCKED_BY_DEPENDENCY`。
- [x] 在现有P3工作台接入Schema控件、冲突选择、DIRECT填写、CUSTOM增删、COLLECTION请求/刷新、MANUAL证据、暂存和提交；服务端`allowedActions`控制入口。
- [x] 使用真实Spring事务、MyBatis、MySQL 8.4与平台幂等/审计，配合仅存在于`src/test`的跨模块受控替身，完成A级P3生成→DIRECT+MANUAL→提交P4的数据库正向闭环；隔离空卷迁移至V156，聚焦MySQL测试1/1通过。
- [x] 补齐并运行挂载组件MANUAL证据选择→刷新后版本→提交P4正向交互；定向Vitest 5/5、`ts:check`和`build:local`通过，未用源码文本断言替代组件行为。
- [ ] 完成后运行定向前端验证，并以正式身份完成一次“A级P3→生成→DIRECT+MANUAL→暂存刷新→提交→P4”真实工作台闭环。
- [ ] 更新本Task与Feature追溯检查点，形成单一Implementation Done候选；不扩充异常Chromium矩阵。

Task 2增量状态：`23dff6cd`的CUSTOM移出、同一CollectionTask异步收敛、P3服务端动作投影及`CutoverChecklistItemResultLinked`同事务Outbox已独立复审`PASS / GO`；`d3161d9d`的真实MySQL受控正向闭环已独立复审`PASS / GO`。Controller/Service仍无生产注册，生产Owner装配与真实工作台验收尚未完成，Task继续`IN_PROGRESS / PRODUCTION_ACTIVATION_BLOCKED_BY_DEPENDENCY`。

## 完成边界

2026-10-16 获批设备版本来源增量：设备序列号表及项目已发货设备分页增加当前软件版本，复用 AST `conpVersion`；P3 型号及当前版本只读消费设备接口，升级目标版本保留 CUT 维护。提交在原事务中冻结版本，历史只读，设备版本变化拒绝沿用原公告上下文或人工覆盖。定向后端测试 34 项、前端组件测试 31 项通过；后端验证沿用工作树工具链和构建锁，资源受限时采用单 JVM 运行测试。真实浏览器确认设备管理及割接序列号表显示版本列、P3 型号和当前版本不可编辑；运行后端尚未部署本次增量，因此取值一致性及提交后重开历史的真实闭环未验收，不更新 Feature Done。

- Implementation Done只在两项Task完成、生产Owner真实接通、一次正式工作台正向链和数据库事实一致后申请。
- 本Task不包含INT-12/DAC Provider、V2导出、P4/P5/P6业务、旧`pms_cut_risk`改造或固定角色授权。

> 检查点：Task2正向REST/UI、CUSTOM/COLLECTION工作台最小整改已分别在`c8c75ce5`、`23dff6cd`独立复审`PASS / GO`；`d3161d9d`真实MySQL受控闭环与`ec268ab9`挂载组件MANUAL证据→刷新→提交P4交互均获独立裁决`GO`。最近Gate为跨模块生产Owner依赖关闭后的唯一生产装配；正式身份真实MySQL/浏览器和Done仍阻断。

### 2026-10-20 性能名称、人工证据直传及表格文本材料

- 用户明确要求设备性能四项前加“当前”、设备采集与日志可直接上传人工校验证据，诊断日志、运行版本、热补丁、License及配置备份允许表格和文本文件。展示统一使用当前并发连接数、当前新建连接数、当前CPU使用率、当前内存使用率，原字段键及数值、历史原件不修改。
- COLLECTION项复用既有MANUAL文件结果链，不要求先失败；原失败事实存在时仍保留。DIRECT填报不能用附件替代，EXTERNAL原失败查询条件不变。选择、提交、修订及审批引用同步处理：缺少有效附件仍拒绝提交/审批；修订不复活无附件历史结果，有附件且无失败来源的人工结果可通过PLT正式重绑沿用。
- 清单材料前端支持XLS/XLSX/CSV/TXT/LOG/CFG/CONF及既有文档图片。公共上传器处理Windows缺失MIME及CSV误报Excel；服务端仍按Owner允许类型、实际字节、名称、大小、摘要、扫描校验。LOG/CFG/CONF名称差异仅允许实际检测为text/plain且Owner允许文本的内容，改名PDF及Owner不允许类型均拒绝。
- 前端清单20项、冻结展示6项、公共文件7项通过，ESLint通过。PLT内容验证22项通过，真实ClamAV环境项未执行；CUT清单8项、审批来源7项、文件权限11项通过，修订调整后定向6项通过。管理员改派另记F-CUT-005。证据：.run/cutover-manual-evidence-ui-retest.log、.run/cutover-manual-evidence-labels-ui-tests.log、.run/cutover-upload-media-ui-tests.log、.run/cutover-manual-evidence-media-tests.log、.run/cutover-manual-evidence-final-tests.log、.run/cutover-manual-revision-final-tests.log。前两轮后端编译与旧规则测试失败均已在范围内修正，日志原件保留，不把失败的聚合执行标为通过。
- 真实浏览器任务2112439350652284928的调研清单四个表头已更新；当前属于已提交只读记录，本轮未上传或更改材料。用户此前选择暂不重启，本轮保持59191与48182，新增后端行为待部署及浏览器完整上传验收，不能记为已生效。
- 随后真实浏览器打开原调研草稿2110809009520820224，逐项读取实际文件选择器：诊断日志、运行版本、热补丁、License、配置备份五项及设备动态表项均包含XLS/XLSX/CSV/TXT/LOG/CFG/CONF且可选文件，诊断日志及动态表项显示直接人工校验证据入口。本轮未向业务任务写入测试材料或提交清单。

### 2026-10-20 设备采集项默认显示核查结论

- COLLECTION项的既有Schema表单改为默认显示，未取得采集结果时“检查是否正常”不选中且不可填写；有效采集后按既有权限填写，已填值与只读状态保留。未更改服务端采集证据、保存或提交校验。
- 新增6个挂载组件用例，修复前4个默认显示用例失败，修复后清单组件26项通过；ESLint及diff检查通过。日志：.run/cutover-collection-default-display-before.log、.run/cutover-collection-default-display-tests.log、.run/cutover-collection-default-display-eslint.log。
- 真实浏览器任务2112435383465652224的系统日志、诊断日志告警检查及设备动态表项共3项均显示未选中的核查控件，并保留设备采集、已有采集选择与人工证据入口；诊断项布局已截图查看。本轮未执行真实采集或写入业务任务，不涉及后端部署或重启。


### 2026-10-21 工作台及调研页面加载优化

- 按用户要求优化既有工作台和任务调研页。工作台待办、自建列表分别提交结果及结束各自加载状态，单侧失败不清空成功列表；负责人仅为实际展示该字段的待办行查询，按项目去重、按当前权限读取并在后台回填，不阻塞任务行。请求序号校验防止刷新前的旧响应覆盖新列表，不新增跨请求人员或授权缓存。
- 调研页在服务端已返回保存清单动作、P3已完成或明确选中历史清单时，并行读取清单和外部核查状态；未生成且没有已存在依据时仍先确认清单，避免空清单外部请求。保留每次服务端授权，固定本次请求的历史清单ID，拒绝不属于同一清单的外部响应；读取失败显示重试入口并禁止写入，成功重读后恢复。没有减少核查项、自动选择正常或跳过提交、版本、历史保护。
- 新建组件及方案、审批、闭环阶段组件按进入阶段加载；设备采集与日志继续复用同一业务组件，首次点击才加载和挂载，已打开的实例保留原关闭清密及刷新行为。既有原生卡片、上传区和已上传文件高度保护保持原实现。
- 工作台、调研和P1~P6集成40项通过；阶段/角色/方案相关22项通过，详情异步加载等待修正后5项通过，总有效67项。新增检查覆盖列表独立显示、无用项目查询、负责人慢请求不阻塞、部分失败保留成功列表、外部状态并行、未生成不请求、失败重试及跨清单拒绝；原采集接受、未保存输入和提交历史保护检查保留。初次失败为异步模块mock解包和测试未等待模块加载，明确读取default并等待动态导入完成后通过，没有删除业务断言。工作台/详情/调研及其导入链的定向vue-tsc通过，ESLint无错误，详情测试中既有两条mock属性类型警告保留，diff检查通过。日志：.run/cutover-load-performance-initial-tests.log、.run/cutover-load-performance-tests.log、.run/cutover-load-performance-stage-tests.log、.run/cutover-load-performance-detail-retest.log、.run/cutover-load-performance-all-types.log、.run/cutover-load-performance-eslint-final.log。
- 真实浏览器同一任务2112435383465652224：调研核心读取等待的单次本机对比约635ms→403ms，清单与外部状态从串行改为同时发出，36项全部保留，采集与日志可首次按需打开、关闭，未执行采集。工作台同一账号和分页的项目详情请求10→5，两张列表各显示10行，未打开的新建、方案/审批及采集组件不在工作台初始模块请求内。另复验新建空表单、2109853246593855488在线方案及2111084153208291328已归档闭环/调研；42个历史调研项无可编辑字段或保存按钮。观察窗口未发现割接任务业务写请求，未创建、保存、提交、审批或上传测试数据。证据：.run/cutover-load-performance-browser.json。数值是本地开发环境样本，不代表生产整体页面时延承诺；后端待办和统计接口本身仍约1秒，未修改其既有领域授权及判定，本轮没有后端/DAC部署或重启。

### 2026-10-21 调研序列号统一使用任务设备范围

- 用户要求设备部署位置规划自动带出割接设备，并将相关序列号统一来源；明确选择“按割接设备重建，清除不匹配行”。当前可编辑P3草稿的部署规划及性能基线按任务设备顺序逐台生成，清除不匹配/重复行，匹配行的名称、安装位置、性能数值等原填写保留。序列号只读，不能另增或删除设备行；其他Schema中的serialNumber字段改为从同一任务设备范围选择。原接口互联、IP/VLAN中的设备名称及对端接口仍按原业务语义填写，不擅自当作序列号改写。
- 任务设备范围沿用已有detail.devices及服务端cut_task_device_scope，设备采集/闭环已有选择和方案冻结设备清单继续复用其任务设备事实；软件版本表以任务设备ID及序列号确定身份，只在ID和SN均匹配时采用对应版本元数据。没有扩大到项目全部设备、没有新增资产查询或缓存，也不修改已提交方案、审批快照或历史清单。
- 自动重建只在可保存的当前DRAFT中执行，记录为待保存变更，保存仍走原授权、版本和幂等接口。服务端在DIRECT保存、自定义项初始答案及P3提交前，按相同固定任务设备范围拒绝清单外序列号；空范围不放宽校验。沿用已有Query对象和Mapper查询，无SQL、DDL或公共API变更。已提交及历史读取不经过重建/新写校验，不修改历史原件。
- 前端47项检查、后端22项检查及割接详情导入链定向vue-tsc通过；ESLint及diff检查通过。覆盖多设备、高位ID、空表、匹配事实保留、外部/重复行清理、非设备表及人工附件不改写、嵌套序列号选择、错误设备版本上下文不采用、历史只读、清单外写入拒绝及原P3流程/修订保护。首轮前端只读布尔属性的模拟表现、后端测试固定字符串Schema遮蔽新表格定义均在新用例中按实际契约修正，未放宽业务校验。日志：.run/cutover-device-source-frontend-final.log、.run/cutover-device-source-backend-retest.log、.run/cutover-device-source-types.log、.run/cutover-device-source-eslint.log。
- 浏览器在任务2112435383465652224保存并重新打开，实际GET清单结果的部署规划和性能基线均为任务SN 02051246N233000017，清单事实版本8；只暂存，未提交清单或执行设备命令。归档任务2111084153208291328的部署SN 02050003d521及性能历史手填jjjadsad仍原样只读，无保存按钮，未迁改历史。证据：.run/cutover-device-source-browser.json。本轮浏览器保存使用当时已运行的后端；新增服务端范围校验仍须冻结部署后实测，不将单测结果记作已生效。
- 首次完整编译成功，但构建期间其他工作修改了PLT FileContentPolicyService及其测试，源清单一致性检查按规则拒绝冻结；日志.run/cutover-device-source-build.log保留。未绕过锁、校验或覆盖运行包，重建排队等待工作树锁；59191和48182本轮尚未启停。
- 等待工作树锁释放后，本会话通过worktree-build.ps1重新完整构建成功，冻结发布`.run/build/releases/a449cfdd-dc37-4fe1-a51d-add33dfd233a/manifest.json`，22项已部署修复保护保持通过；worktree-backend.ps1 ValidateOnly预检通过，无服务变更。证据：.run/cutover-device-source-build-retry.log、.run/cutover-device-source-deploy-precheck.log。新增后端范围校验待本次明确重启批准后部署及浏览器复验；不复用上一轮后端性能部署的批准。
- 用户随后明确允许本次重启并复验，使用该冻结发布及worktree-backend.ps1完成部署，59191健康、PID 35020，48182保持原进程17780未启停。部署后真实浏览器保存及GET重开均200/code0，任务2112435383465652224保持DRAFT，部署规划及性能基线仍为SN 02051246N233000017。通过空字段编辑触发保存，首轮空白字符暂存后用正常键盘操作清除，再次保存并重开确认安装位置恢复空串，最终清单事实版本10，未填写虚构位置、提交清单或执行设备命令。
- 部署后归档任务2111084153208291328的原部署SN 02050003d521、性能历史手填jjjadsad仍原样只读，未出现保存操作；本次自建验收标签页已关闭，用户原标签页保留。浏览器输入端禁止改写序列号/新增设备行，未通过浏览器提交伪造序列号；清单外序列号及空权限范围拒绝的负向证据仍为此前22项后端检查中的失败用例，不将其写作真实HTTP负向验收。证据：.run/cutover-device-source-deploy.log、.run/cutover-device-source-deployed-browser.json。

### 2026-10-21 按目标列补齐设备清单可用资料

- 用户要求自动带出时根据目标块字段补齐设备清单可直接提供的内容。复用既有设备列表查询，按任务设备ID、SN及当前项目精确核对，只为当前可编辑草稿的缺项读取资料。明确映射设备名称/本端设备、安装位置、产品编码、产品型号、产品名称、当前软件版本；只写目标Schema实际存在的列，只补空值，不覆盖已有内容、数值0或false。位置复用设备列表的locationSnapshotText展示规则，不将内部位置JSON填入业务文本。设备类型、性能数值、端口/IP等没有直接来源的字段不推测，不以CONP类型代替设备类型。
- 部署规划/性能基线继续按固定任务设备范围生成行；已有序列号表格按SN定位，接口互联及IP/VLAN规划仅在空表时逐设备带入可获取的名称，不替换已有多行接口及地址规划。人工证据和非适用项保留原行为。既有方案设备型号、当前版本及调研目标版本自动填充继续沿用原实现，不改方案/审批冻结快照。
- 设备资料后台读取，不阻塞调研核心内容，不增加全局加载切换；保存会等待本轮资料读取完成，重新核对任务/清单请求序号，避免跨任务迟到响应触发写入。读取期间填写的非空值保留；来源失败或归属/身份不符只保留当前填写并允许刷新重试，不采用错误资料。目标字段已有值时不再请求设备列表，无跨请求授权缓存。本次仅前端修改，无新API、SQL、DDL或后端/DAC启停。
- 六组前端检查131项通过，覆盖目标列映射、位置格式、空值/已填值/0保护、名称表初始化、源身份与项目不符、读取失败、并发输入、历史/权限保护及原创建/方案流程；ESLint和diff检查通过。首轮新失败测试未等后台读取结束，改为等待实际错误状态后通过，未放宽业务断言。定向vue-tsc仍报告需求分析EntityForm.vue:285的availabilityStatus/referenceStatus两项外部字段类型错误，本次修改文件无类型报错，未将该类型检查标记通过或扩大修复范围。证据：.run/cutover-device-autofill-validation.log、.run/cutover-device-autofill-eslint-validation.log、.run/cutover-device-autofill-type-validation.log。
- 真实浏览器任务2112435383465652224的设备列表返回name=DPtech FW1000-GA-X 双电源AC主机、位置=白城联通14楼机房；部署规划自动填入一致值。正常保存及重开GET均200/code0，事实版本11、仍为DRAFT；原接口互联及IP/VLAN手填值22等保持不变，性能值未臆造。重开时目标字段已填，设备列表请求0次。归档任务2111084153208291328原FW/02050003d521/fw/wd仍原样只读，无保存操作、无设备列表请求。本次验收标签页已关闭，保留用户原页。证据：.run/cutover-device-autofill-browser.json。

### 2026-10-22 系统日志告警人工证据上传版本冲突

- 真实浏览器复现任务2113184905149976576的RISK_SYSTEM_LOG上传返回1010001019“文件业务引用版本已变化”。当前核查结果是COLLECTION/CONNECT_TIMEOUT，独立MANUAL_EVIDENCE槽位却已绑定工件2113187483379068929的V1；前端只根据MANUAL结果或已显示文件取工件/引用版本，遗漏该场景，重试继续完成原CREATE会话而发生冲突。
- 仅修复共享CutoverChecklistField：可编辑人工证据槽位独立读取PLT当前引用，ADD_VERSION使用实际工件及引用水位（含0）；读取期间或失败禁止上传，提供局部重试；显示已绑定但尚未进入核查结果的材料。引用异步响应按清单项身份与请求代数隔离，上传后推进游标，相同身份的结果刷新不重复清空引用，初始不可编辑后恢复也会读取。只读历史不查询当前槽位，不改服务端授权、乐观锁、采集失败事实或状态转换。
- 组件及受控全流程43项通过，覆盖采集失败后发现已有V1、读失败重试、清单版本切换迟到响应隔离、只读历史保护、编辑恢复及上传后引用推进；ESLint及diff检查通过。新增测试发现一次重复读取，已改为逐项watch来源后重跑通过；测试文件不完整的文件事实替身已补齐allowedActions，未放宽生产校验。
- 用户在热更新后的原页面重新选择“插槽show tech.txt”，真实初始化为ADD_VERSION/expectedReferenceVersion=0；complete-upload为200/code0并生成V2，manual-results为200/code0、清单版本31、结果版本7。重开页面显示人工证据V2，真实版本历史列出V1和V2均可用，旧V1保留。本次仅前端热更新，无后端/DAC启停，未填写正常结论或提交调研清单。证据：.run/cutover-alarm-reference-tests.log、.run/cutover-alarm-reference-browser.json。

### 2026-10-22 人工证据后的设备核查结论可填写

- 用户指出设备采集项“检查是否正常”仍不可选择；真实页面RISK_SYSTEM_LOG已是人工证据V2，两项radio仍disabled。根因为前端只认可成功COLLECTION，后端saveNew同样拒绝MANUAL来源的采集项结论，不能仅放开前端。
- 当前修复：已保存的人工附件使既有结论字段可编辑；未取得有效结果、只读或无保存权限继续禁止编辑，不自动选正常。保存通过既有清单PATCH式answers追加MANUAL结果、复用具名答案明细，锁定重验原PLT文件事实，复制附件和原采集失败引用；读接口保留file并提供analysis，修订清单复制时也带入已存结论。前端回填MANUAL分析，只发送变动答案，避免无关填写反复生成结果版本或提交附件为DIRECT。没有新API、DDL、状态、角色或固定字段JSON存储，既有历史不自动补值。
- 后端22项通过，覆盖人工结论追加后附件/原失败/历史保留、真实读取组装file+analysis、无证据拒绝、文件权限撤销前不写入、修订保留结论；前端46项通过，覆盖可选择/只读、结论回填与只提交变更值。ESLint及diff检查通过。首次编译变量重名和既有mock未提供answer的失败已修复后重跑。定向vue-tsc本次文件无错误，需求分析EntityForm.vue仍有3处外部类型错误（159、292行），不宣称类型检查全通过。
- 真实页面已确认两项radio启用且仍未勾选，原V2附件保留。用户明确实际结论自行填写，本会话不代填或保存。完整后端构建和22项已部署修复保护通过，冻结发布`.run/build/releases/64d94c73-95c0-4f1a-9b90-8993a69ced1a/manifest.json`，ValidateOnly预检通过；用户随后明确批准本次重启59191并复验，部署结果另行记录，不将自动化保存检查写作实际结论已提交。证据：.run/cutover-manual-analysis-tests.log、.run/cutover-manual-analysis-ui-tests.log、.run/cutover-manual-analysis-types.log、.run/cutover-manual-analysis-build.log、.run/cutover-manual-analysis-preflight.log。
- 本次批准后通过worktree-backend.ps1部署上述冻结包，59191健康、PID 37060、local,dac配置保留；DAC 48182独立健康检查通过，未启停。部署后在自建浏览器标签页重新加载任务2113184905149976576，RISK_SYSTEM_LOG“是/否”均可编辑且仍未选中，V2人工附件保留，真实版本历史显示V1/V2均可用，无页面错误提示。按用户自填实际结论的要求未勾选、保存或提交，本次真实浏览器仅确认可编辑和附件读取，保存/修订的结果保留由上述自动化检查覆盖，不宣称实际结论已完成保存重开。保留该待填写标签页，用户原页未导航。证据：.run/cutover-manual-analysis-deploy.log、.run/cutover-manual-analysis-deployed-browser.json。

### 2026-10-22 选择核查结论后人工证据上传中断

- 用户反馈选中“检查是否正常”后人工证据无法绑定，提示文件引用不存在。只读核对同一任务RISK_SYSTEM_LOG：PLT当前槽位已上传V6、引用水位5，业务当前MANUAL结果仍为V5、水位4。根因为saveManual在PLT上传推进引用后先调用saveDirect，待填结论仍按旧V5校验，严格文件校验正确拒绝；不是文件丢失。
- 仅调整共享前端：先通过原授权接口绑定新文件，再保存保留的变动答案；局部刷新后按相同任务及清单恢复待填输入，结论校验使用新MANUAL结果。已上传但未成为当前业务证据的槽位提供“重新绑定已上传材料”，读取实际工件/版本/水位后重试原绑定，不重复上传文件。只读、无保存权限、引用读取失败及并发版本校验保持生效，没有后端、数据库结构、状态机或文件授权放宽；工件ID保留字符串精度。
- 两个新增失败用例先复现旧顺序及缺失重绑入口，再修复后组件与受控流程48项通过；原未保存DIRECT输入用例按新顺序更新，仍断言填写保留、版本递进及局部刷新。ESLint无错误或警告，diff检查通过。定向vue-tsc本次文件无错误，仍有需求分析EntityForm.vue:159、292三处既有外部类型错误，不标记全量类型检查通过。证据：.run/cutover-manual-binding-failure-tests.log、.run/cutover-manual-binding-tests.log、.run/cutover-manual-binding-eslint.log、.run/cutover-manual-binding-types.log。
- 真实浏览器首次观察到用户已勾选“是”。热更新后在自建验收页恢复该原选择，再重新绑定已上传V6，页面显示V6及“是”，无需再次上传；只读数据库确认结果9为V6/MANUAL_EVIDENCE_SELECTED，结果10为V6/MANUAL_ANALYSIS_SAVED、check_normal_flag=1，旧结果8/V5及文件V1~V6均保留。没有另行判定日志正常，也未提交调研清单。
- 重开时遇到环境连接中断：MySQL和Redis容器均在23:52重新启动，容器内部健康且数据可读，后台对宿主机24306/24379连接失败、页面详情加载报错，健康请求超时。本会话未启停任何服务，不把重开回填记作通过；已完成真实绑定/保存，重开验收待运行环境连接恢复。证据：.run/cutover-manual-binding-browser.json、.run/cutover-manual-binding-db.txt。

### 2026-10-23 连接恢复及人工证据重开复验

- 用户授权继续检查并恢复MySQL/Redis，开始检查时两者宿主机链路已经恢复：后端健康UP、MySQL查询及Redis PING通过，不额外启停容器、59191或DAC。真实浏览器重新打开2113184905149976576，系统日志人工附件V8及用户原勾选“是”均正确回填；只读记录确认当前MANUAL_ANALYSIS_SAVED结果13对应文件V8、check_normal_flag=1，旧结果及V1~V8上传会话仍保留。上一轮受连接中断阻塞的重开核对已完成，未提交清单。
- 用户补充版本增加而名称不更新；只读来源确认V1=2.png，V2~V8=插槽show tech.txt。这是文件卡片读取工件初始名称导致，名称修复复用F-PLT-001公共文件能力；用户已上传原文件，本轮不再要求重复上传或代判结论。修复后部署和浏览器文件名验证另行记录。
- 证据：.run/file-version-name-restored-browser.json；名称实现和自动化检查见tasks/features/F-PLT-001.md同日记录。
- 用户批准后完成文件名修复部署。浏览器重开确认系统日志人工证据V8正确显示插槽show tech.txt，原“是”结论回填、上传区域可用，无文件引用错误；只读当前结果仍为MANUAL结果13/文件V8/check_normal_flag=1，文件版本总数仍为8。版本历史保留V1原名2.png及V2~V8原名插槽show tech.txt。完整关闭上一轮环境异常造成的重开验收缺口，没有重复上传、代改实际结论或提交清单。部署及证据见同日F-PLT-001记录。

### 2026-10-23 割接页面 1 秒目标、2 秒上限优化

- 用户要求割接各页目标1秒、最多2秒。本轮沿用现有工作台、P2～P6页面和权限/状态/历史规则，先用真实浏览器测量。工作台统计599ms、待办644ms，调研清单551ms、外部状态719ms；闭环项目覆盖结果5632ms，是明确超过上限的瓶颈。原始脱敏接口证据：`.run/cutover-2s-api-before.json`。这些是接口耗时，不冒充整页就绪时间。
- 资产模块增加当前项目设备摘要批量读取：可见项目是原单设备可见性OR规则的充分条件，只解析一次该范围；不在项目范围时仍逐台执行原组织/合同授权，任一拒绝整批失败。IMP保留原锁定设备范围、租户/项目/归属版本及成功覆盖算法，批量读取替换重复单设备授权。API契约见`docs/design/10-api-design.md`同日补充。
- 调研展示将系统字段定义按精确修订批量读取，自定义字段保持独立来源；外部状态复用与完整清单相同的任务/审批人员授权及当前/历史清单选择，仅省去无关字段与答案组装。不加跨请求权限缓存，不更改结果持久化、历史快照或业务完成规则。
- 50项后端定向检查最终通过：AST查询8、IMP覆盖服务及规则8、清单应用12、外部核查12、字段批量3、字段编解码7。包含跨租户、忽略租户、空范围、合同/组织授权路径、单设备拒绝整批失败、历史清单选择及混合修订/自定义字段前后一致；原授权拒绝、精确归属和历史结果不变。初次测试夹具的嵌套stubbing、空值集合筛选和未声明的void调用已修正，不降低断言。各有效结果分别见`.run/cutover-2s-targeted-test-rerun.log`、`.run/cutover-2s-targeted-test-final.log`和`.run/cutover-2s-asset-test-final.log`。
- 当前仍需冻结包构建、部署预检、用户本次重启批准及部署后的真实浏览器整页计时；尚未声明达到1秒目标或2秒上限。浏览器验收须把附件卡片加载纳入，覆盖工作台及各阶段进入/切换/刷新，并与优化前的项目覆盖结果逐字段对照。
- 完整构建及冻结发布校验通过（22项既有修复基线），发布清单`.run/build/releases/c525757c-e959-4467-ad20-6456135fcd43/manifest.json`，`worktree-backend.ps1 -ValidateOnly`通过。等待本次59191重启确认，尚未部署；DAC未操作。
- 补充前端返回路径优化：真实浏览器发现新建页返回工作台会重复发出两组TODO/SELF_CREATED/statistics。原因是路由已失活/卸载后，原returnToList仍主动刷新。仅为原工作台增加活动生命周期判断，失活/卸载后不再刷新，原位关闭新建表单及再次激活仍读取最新列表；不增加跨页面缓存。失败测试先复现卸载后多发2次列表请求，修复后5项定向运行测试通过（卸载/KeepAlive失活不刷新、原位返回刷新、列表/KPI正常刷新及KPI慢/失败不阻塞列表），局部ESLint通过。浏览器确认返回仅1组列表及统计，证据`.run/cutover-2s-workbench-return.json`。
- 整页探针采用内容/遮罩/附件DOM判断；探针的宿主机总时长包含浏览器自动化调用开销，不能替代浏览器原生点击到可操作的SLA指标。当前只确认请求去重，尚未以该探针宣称工作台或所有阶段达到2秒上限。后端冻结包不包含独立前端，此项局部前端修改不改变前述冻结清单。
- 用户明确批准本次重启并复验后，按冻结清单c525757c部署59191成功，PID43652，local,dac及健康检查通过；DAC48182未执行启停。部署后浏览器计时及数据对照继续进行。
- 首轮部署后的浏览器原生Tracing使用EventDispatch(click)到内容可用的原生单调时钟，并纳入附件aria-busy；文档进入使用NavigationStart。实测工作台刷新1225ms、P2切换527ms、方案1158ms；但闭环首次文档进入2735ms、审批2330ms，未达到2秒上限。项目覆盖请求从5632ms降至257ms，51台设备范围、覆盖结果、事实版本及资产回执逐字段相同；调研外部状态51ms、清单190ms，已定位剩余瓶颈为附件每次请求反复组装审批冻结来源。未将接口提速或部分页面通过宣称全部页面达标。证据`.run/cutover-2s-deployed-first.json`、`.run/cutover-2s-coverage-before.json`与`-after.json`。
- 补充优化仅在同一次HTTP请求内按租户/审批ID/审批版本复用已完整校验的不可变来源文本，读取、预览、下载的人员资格与文件事实仍逐次检查；锁内重验无条件重新读取，失败不保存，新请求及版本变化不复用，命中也验证当前租户/忽略租户状态。没有跨请求数据或权限缓存，没有跳过来源损坏、文件失效或角色撤销校验。失败测试先证实原来三次重复组装，修复后54项CUT授权/文件策略/清单/外部核查检查有效通过（最后一组30项覆盖补充租户与文件失效回归），证据`.run/cutover-2s-source-red.log`、`-green.log`、`-final.log`。
- 调研面板改为与方案/审批/闭环一致的按需加载，进入其他阶段不加载未选中的完整调研组件；任务详情5项运行测试及局部ESLint通过。人工上传区域补充已有引用读取的aria-busy状态，整页可用计时将纳入该等待，避免把仍禁用的上传区记为可操作。此轮补充包仍在构建，尚未获得本次后续重启批准或宣称全部页面达标。
- 补充包首轮完整构建在AST编译阶段因JVM本机内存分配失败终止，没有产生冻结清单；并行vue-tsc也未完成，不能标记类型检查通过。停止并行验证，改为原固定构建入口串行重试，并仅为本次构建设置2GB Maven堆上限；现行59191仍为首轮性能版本PID43652，未再次启停任何业务服务。失败证据`.run/cutover-2s-source-build.log`、`.run/cutover-2s-typecheck.log`。
- 串行重建成功，22项已部署修复基线校验及冻结一致性通过；补充发布清单`.run/build/releases/cc8975d4-55bd-4cef-bb8e-e1f189ad1f31/manifest.json`，部署预检通过。已单独请求本次后续重启许可，当前未再次部署；首轮PID43652仍提供服务。类型检查改为构建后串行执行，结果另记，未将此前内存失败记为通过。
- 用户明确批准后续重启后，补充包cc8975d4已按正式入口部署，59191 PID29336、local,dac及健康检查通过；DAC未操作。4GB串行vue-tsc达到自身堆上限终止，不能声明类型检查通过；构建已结束后改回项目既定8GB脚本单独执行，避免与Maven争用。浏览器性能终验在类型检查结束后执行，避免把验证进程负载计入页面比较。
- 终验环境纠正：后续实际读取发现本轮验收Tab8的自然视口为0×0，虽然DOM可读，但步骤点击和真实截图持续超时。尝试原生激活及临时1280×900视口后仍无有效截图，已清除临时设置；桌面窗口控制运行时不可用。当前原生UI计时样本（含首轮及补充包2.78秒样本）不作为有效的可见页面SLA通过/失败结论，原记录保留并以此条纠正；后端请求耗时及覆盖数据逐字段相同的事实不受影响。已请求用户展开浏览器面板后重新采样，未声明各页达标。
- 构建结束后单独运行项目既定8GB vue-tsc完成，报告工程实施未改文件`src/views/pms/engineering/solution-reviewed/solutionRequirementSources.runtime.spec.ts:41`的TS2769（Uploader必填props缺失），本次修改文件无类型报错；全量类型检查仍未通过，不扩大修复范围。最终独立日志`.run/cutover-2s-typecheck-serial-full.log`；较小堆限制和此前并行内存失败保留原记录，不以失败替代完成结果。
- 用户展开浏览器面板后，独立验收页有效视口946×892并可实际截图。阶段切换P2 495ms、P3 999ms、P4 638ms、P5 1569ms、P6 507ms，闭环文档重载1958ms。导航按钮的高层自动化动作出现约3秒固定等待，改用原生可访问性点击并以Tracing原生click计时，工作台进入1159ms、新建471ms、返回1142ms、任务列表623ms、可编辑调研进入1653ms；返回仅一组列表和统计。证据`.run/cutover-2s-visible-final.json`、`.run/cutover-2s-native-ax.json`。原3秒导航样本保留，不把工具等待当成页面就绪耗时。
- 首次文档加载仍未达标：工作台1935ms、闭环1930ms，但含较多材料的可编辑调研2278～2916ms；带渲染帧的原生时间线确认附件阶段仍有等待，不将阶段切换代替首次加载验收。并行测试期间审批样本也有2912ms，不声明稳定上限。11份审批附件的名称、版本、引用及allowedActions与优化前完整响应逐字段一致，证据`.run/cutover-2s-file-facts-source-after.json`；无上传、结论改写、保存或审批操作。
- 继续定位到PLT当前引用读取在同一请求内先授权并精确读取，随后委托getReference再次做同一授权和查询。补充优化保留原租户/主体/完整业务键校验、读取前Owner授权、空槽返回空、Owner异常传播及合法工件ID校验，直接复用原引用响应映射；上传写入的版本及锁校验不变，无跨请求缓存。8项定向检查通过，包含原拒绝和失败测试、V8/引用版本12原样回传及非法工件身份拒绝；失败用例先在原实现真实复现两次Owner调用。日志`.run/cutover-2s-reference-red-valid.log`、`-green.log`。完整冻结构建进行中，本次补充尚未部署，不能声明所有首次加载达到2秒上限。
- 本次完整编译成功，但构建期间PROJ的项目范围批量查询源码发生变化，正式入口拒绝冻结（.run/cutover-2s-reference-build.log）；后续稳定性检查一次发现平台源码仍变化，另一次未取得其他验证持有的工作树锁，没有绕过锁或发布未冻结target。用户明确回复同工作树“项目范围批量查询／AuthorizationReadScope”仍在进行，因此暂不重复完整打包。当前59191仍为cc8975d4/PID29336，补充当前引用优化尚未部署；待提供方源码稳定后统一完整构建、预检，并取得本次新的重启批准再继续2秒上限验收。独立DAC源码不在本次平台指纹中，DAC未操作。
