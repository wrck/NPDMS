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
