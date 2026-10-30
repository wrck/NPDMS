# F-INT-012 设备连接与采集平台集成

2026-09-08开发准入复验：以下PLT核心和Task 7已进入master，历史实现事实保留。当前Spec唯一明示的允许实施切片仍是已完成Task 7；INT边缘、文件适配、Gateway及消费者剩余清单尚须在唯一有效Technical Plan中落实下一未完成Task、Owner/依赖版本、精确文件边界和逐Task验收，再登记开发DU。不能从剩余清单任选接口直接开写，也不以未执行全库MySQL/浏览器或Phase 1/2批准作为设计/计划工作的前置。详见[开发准入复验](../delivery-units/DU-20260908-DEVELOPMENT-ADMISSION-REVALIDATION.md)。

> Feature实施状态：`IN_PROGRESS`
> 实施子状态：`PLATFORM_CORE_IMPLEMENTED / MASTER_COMPILE_AND_FOCUSED_TEST_PASS / INT_EDGE_AND_E2E_PENDING`
> 总体工程阶段：`IMPLEMENTATION_PARTIAL`
> Feature Ready Gate：`READY / MASTER_REVALIDATION_PARTIAL_PASS`
> Implementation Done Gate：`NOT_READY`
> Requirement：`INT-12@V1=FULL`
> 关联Requirement：`EXE-03`、`EXE-04`、`CUT-03`、`CUT-06`、`INS-02`、`INS-04`、`NFR-02`；不宣称关联Requirement完成
> Feature Spec：`specs/features/F-INT-012-device-ops-collection-integration.md`
> 接收DU：`tasks/delivery-units/DU-20260903-FINT012-PARTIAL-CODE-RECEPTION.md`
> 来源分支：`prereq-parallel-check-kKiAdn`
> 来源实现：`8425805911703c3c75387ba7e9bea75dedd6f076`、`d2d1765ffe14233d8041d4b10c871d246c4a9183`、`cdfbd71a1722f9696c1dbb8713566de9e88ff97c`
> 代码接收：`PR #4 / 2df41a187268332ea38f01ac90ea5f8302df3f34`
> 主干适配验证：`PR #5 / Actions 33733891015 / SUCCESS`

## 状态口径

已完成的独立代码切片允许进入master；Feature在INT边缘接入、生产装配、真实联调和最终Gate完成前保持`IN_PROGRESS`。不得因为Feature尚未Done而把已存在代码回退为`NOT_STARTED`，也不得因为代码已接收而倒签Feature完成。

## 已实现并进入master

### 稳定合同

- 独立`pms-module-integration-api`模块；
- `DeviceOpsGatewayApi`、下发命令、下发结果和任务快照DTO；
- PLT采集批次、任务、回调和消费确认公开API及稳定DTO。

### PLT物理Owner实现

- `DeviceCredential`、`CredentialGrant`和受认证加密保护；
- Redis一次性取密令牌、绑定校验、原子消费和秘密清零；
- `CollectionBatch`、`CollectionTask`、任务状态机和平台幂等创建；
- 已保存凭证与临时秘密两类任务的独立派发服务，其中外部Gateway不存在时不激活派发Bean；
- Platform回调事实、顺序校验、任务/批次投影、结果事件和业务消费确认；
- 设备凭证管理REST入口`/api/v1/pms/device-credentials`；
- Mapper/XML、Controller合同测试、服务单元测试、Redis测试和来源真实MySQL候选测试。

### 数据库

- 当前master迁移：`V203__fint012_collection_platform_foundation.sql`；
- 只创建`plt_device_credential`、`plt_credential_grant`、`plt_collection_batch`、`plt_collection_task`、`plt_collection_callback_record`和`plt_collection_result_consumption`；
- 来源V104～V106未直接接收，避免低版本迁移和第二文件Owner。

### 已完成的master适配验证

- Java 25下`pms-module-integration-api`与`pms-module-platform`及其依赖编译通过；
- Collection、Credential和Device Ops聚焦非IT测试通过；
- V203只包含六张PLT Owner表的自动边界检查通过；
- 未接收第二文件Owner、旧Infra文件客户端和未完成`int_device_ops_*`持久化的自动检查通过；
- 验证工作流：`.github/workflows/f-int-012-partial-reception.yml`。

## 明确排除

- 来源分支的`infra_file_artifact`、`infra_file_version`以及Yudao Infra文件客户端修改；F-PLT-001继续是唯一正式文件Owner；
- INT签名HTTP/multipart回调Controller、验签、nonce/replay、不可变Receipt、Provider配置和技术对账Job；
- 当前F-PLT-001流式文件写入适配和扫描隔离生产闭环；
- Device Ops生产Gateway实现、真实外部任务查询/取消/重试和独立运行端联调；
- EXE-03、EXE-04、CUT、INS或SRV消费方的完整业务闭环；
- 真实浏览器、SIT、UAT、Deployment和Release结论。

## 剩余实施任务

- [x] 基于当前master完成`pms-module-integration-api`与`pms-module-platform`受影响模块Java 25编译和聚焦非IT测试；
- [ ] 在当前master迁移链执行V1～V203真实MySQL空库和升级路径复验；
- [ ] 执行真实Redis一次性令牌、并发消费和故障恢复复验；
- [ ] 以当前F-PLT-001实现INT流式文件写入与扫描隔离适配；
- [ ] 实现INT签名multipart回调、Receipt、重放防护、顺序校验和ACK；
- [ ] 实现Device Ops生产Gateway、查询/取消/对账及故障恢复；
- [ ] 接通EXE-03/04、CUT-06等首批V1消费方；
- [ ] 完成真实HTTP/multipart、并发、故障恢复和真实浏览器闭环；
- [ ] 基于最终master完成独立Code Review和Implementation Done裁决。

## 当前裁决

`IN_PROGRESS / IMPLEMENTED_CODE_ACCEPTED_PARTIALLY / MASTER_COMPILE_AND_FOCUSED_TEST_PASS`。已实现代码已经进入主干并通过主干适配编译与聚焦测试；未完成部分继续实施，不改变Feature未Done事实。

## 2026-09-21 专项本地增量：统一设备连接与命令采集

依据本次需求方直接授权，复用指定 DAC 分支已有连接验证、加密凭证、脚本制品和执行引擎，补齐公共命令模板管理、保存连接精确授权和统一采集应用服务。独立工作台及配置调试、业务联调接入已在当前工作树完成并部署到隔离开发实例；割接/巡检只保留来源适配契约，未实现其业务模块。上文是历史主干接收事实，本节不改写历史排除项、Gate 或 Feature Done。

- Requirement 覆盖：`INT-12` 公共集成、`NFR-02` 秘密及授权保护；`EXE-03`/`EXE-04` 仅设备采集和明确日志关联，不宣称两项业务需求整体完成。
- 代码：DAC `NpdmsResourceController`、INT `DeviceOpsResourceApi`/`DacDeviceOpsResourceGateway`、PLT `CollectionApplicationService`/`CollectionTemplateService`/`CollectionConnectionService` 与来源 SPI、IMP `ImplementationCollectionSources`、前端公共 `CollectionDialog` 和 `pms/platform/device-collection`；V328 前向迁移复制旧采集关系并初始化菜单/草稿模板。
- 验证：NPDMS 61、DAC 11 个去重聚焦用例通过；受影响修复复验、打包和部署完成；真实 MySQL 并发同请求只有一条任务；V328 升级及重复零迁移、旧历史零差异、新 DAC 凭证在 PLT 无本地秘密。
- 业务验收：真实浏览器完成三个入口、日志预览下载、显式关联、回复丢失查询恢复、取消和撤销授权后的重试；真实设备 `10.253.1.22:5555` 三条 `show` 命令成功，下载日志 215,339 字节，终态停止刷新。联调/配置状态未被技术成功自动推进。
- 限制：全量前端类型检查仍受安装页面既有两处缺失 `DeviceArchiveApi` 引用影响；本次范围 lint、相关类型及真实浏览器通过。未合入 master、未作独立审查/生产发布，不晋级 Feature 完成状态。

完整需求、复用决策、实现边界和可核对证据集中在 [统一设备连接与命令采集专项](../../docs/generated/2026-09-21-unified-device-collection.md)。

### 后续授权：业务实体自动日志回传

需求方明确要求日志自动回传对应业务，失败/取消部分日志也保留。已补公共 `CollectionBusinessResultReceiver`、既有 Outbox 自动投递/重试、IMP 实体接收表与分页接口、配置/联调详情日志区域；成功接收后才确认 PLT 消费，原业务状态及手工附件不变。V329 已升级且重复零迁移。

当前本地增量验证：46 个聚焦用例及打包通过；真实 DAC/回调/MySQL 自动回传成功、失败、取消日志，不依赖浏览器或手动关联；两业务详情预览/下载通过；四请求并发重放仍一条接收记录，跨实体下载拒绝。最终包已部署。前端全量类型检查仍有安装页既有两处错误；不晋级历史 Feature Done，不外扩 CUT/SRV。详细证据见专项记录的“后续批准与交付”。


### 2026-09-21 命令与模板解耦专项增量

- 需求方确认独立采集、配置调试、业务联调均可直接输入命令，模板可选；保存凭证不要求模板。PLT/IMP 现有调用链原位调整，DAC 原生执行接口无需修改，不实现巡检/割接业务。
- 新增不限模板连接授权以 `*` 明确记录；旧模板限定、旧空值及注册请求摘要不迁移扩大。源权限、租户、设备、协议、有效期、撤销、连接版本校验和业务日志回传保留。
- 本地宿主机服务已更新（10.210.0.11:59191）；32 项针对性测试及后端构建通过，改动前端 ESLint/Prettier 通过。
- 真实浏览器 + DAC + Telnet 模拟设备：无模板保存连接，三个入口以保存凭证执行 show version/show run/show tech、日志预览、配置/联调自动回传通过，无页面异常，业务状态未变。另验证旧模板授权拒绝手工命令、模板执行保持发布命令、撤销与重叠授权拒绝。验收连接均已停用，保留日志证据。
- 证据：`.run/dac-host/decoupled-build.log`、`decoupled-result.json`、`decoupled-authorization-result.json`、`decoupled-{center,joint-test,configuration}.png`。本增量尚未提交，不晋级整体 Feature 状态。

- 全量前端类型检查：默认 Node 堆不足；以 8 GB 堆重跑完成，仅存既有 installation/index.vue:340、341 的 DeviceArchiveApi 未定义错误，本次改动无新增类型错误。

## 2026-10-21 Windows 本机 DAC 客户端实施

需求方授权将此前试打包推进为可运行客户端：自带 Java、Windows 服务自启动、指定 NPDMS 环境与端口、网页发现及拉起本机服务；明确不增加客户端绑定。当前增量在原 F-INT-012 内串行实施，保留其他未提交变更。

- 复用审计：DAC 的采集引擎、H2 独立存储、AES-GCM 凭据、幂等提交及 Callback Outbox 可直接复用；新增 desktop 运行配置及原生安装/启动辅助程序。既有 npdms 服务端部署入口和 OIDC 页面保持原行为。
- 安装边界：Windows x64、精简 Java 25、WinSW、系统 .NET Framework；最终用户无 Node.js、Python、MySQL 安装要求。运行数据与程序分离；本机主密钥由 DPAPI 加密并限制文件访问。
- 已实现：安装配置和自动服务、精确来源的本机状态探测和协议启动、NPDMS 登录态签发60秒操作票据、任务独占认领、本机秘密与保存连接、原任务恢复、接受状态上报、合作取消及离线原结果补传。
- 认证裁决已关闭，见 Q-DAC-DESKTOP-AUTH-20261021；既有 OIDC 部署保持可用。本机运输路径复用原来源授权、命令冻结、幂等、回调及消费规则，不引入客户端注册。
- 新环境复用现有1.4.0解析包，经原验证/发布/激活流程一次性初始化；不覆盖已有日志类型、发布版本或激活选择。本次不新增业务角色、菜单、字典或枚举值域。
- 交付产物：`C:\Users\user\Documents\Codex\Artifacts\NPDMS-DAC-20261021-delivery\NPDMS-DAC-1.0.0-x64.msi`，90,502,223字节，Windows x64；未做组织代码签名。安装包包含精简Java25、服务/配置辅助程序、DAC及原1.4.0解析包。随包保留`package-result.json`和`package-files.json`。此前失败打包载荷及未部署的中间后端发布包保留在C盘`NPDMS-DAC-BuildIntermediates-20261021`，未删除原件内容。
- 冻结清单：DAC `.run/build/releases/03efc7a2-cafc-4371-b681-efa2a322eacd/manifest.json`；NPDMS后端 `.run/build/releases/b858dc87-7f95-4f9e-938a-6ee96fe45d70/manifest.json`，后端包包含生成时当前工作树后端构建范围内的源码变更，不代表其他未提交功能均已验收。
- 已执行验证：锁定工具链下后端42模块构建、51项采集/授权/网关定向测试及22项既有修复发布校验通过；DAC10模块构建、21项本机认证/持久化/HTTP/CORS/原回调相关测试及既有发布校验通过；前端2文件13项测试通过。新的完整MockMvc/H2用例验证设备执行不依赖上报在线、恢复后投递同一原始Outbox及匹配回执，验证新环境原解析包初始化和重复启动不改写。此处设备执行端口为测试替身，不是真实设备或浏览器验收。
- 安装包验证：原生辅助程序编译、URL/协议输入限制、环境隔离及Windows DPAPI加密往返检查通过；WiX标准ICE校验通过，MSI只读解包251个文件与载荷逐一一致，自带Java版本为25.0.1+8。未绕过安装校验。测试日志为`.run/build/npdms-desktop-release-final-20261021.log`、`dac-desktop-release-delivery-20261021.log`、`dac-desktop-ui-tests-final-20261021.log`及`dac-desktop-package-delivery-20261021.log`。
- 验证边界：全量前端类型检查完成但未通过，其他模块仍有10个类型诊断（`dac-desktop-vue-typecheck-final-20261021.log`）；本次本机接入文件无该轮诊断。V511未应用到运行MySQL，新增MySQL表清单断言已维护但本轮未执行MySQL集成测试。管理员ACL持久化、MSI安装/升级/卸载、开机自启、协议实际拉起、真实浏览器和业务闭环均待实机验收。本次未安装、启动或重启任何现有服务，未提交或推送，不将此增量或整体Feature晋级为生产验收完成。

### 安装失败修复（1.0.1）

- 用户反馈“安装包有问题”。Windows MsiInstaller 11722及临时安装日志证实：1.0.0的`InitializeClient`接收`10.210.0.11:19191`后因缺少协议退出；后续`http://10.210.0.11:19191`重试也被既有非回环HTTP限制拒绝。安装界面只检查非空，且初始化辅助程序未向安装界面返回具体原因，最终显示笼统1722。19191为网页端口，59191为后端端口，不能混用。
- 修复仅涉及Windows包装层：复用同一URL校验，在配置页点击安装时显示具体原因并保留输入供修改；静默安装在`InstallInitialize`之前同样校验。明确后端API与网页地址字段，保留HTTPS及本机HTTP边界，未绕过或放宽认证、来源和地址检查。
- 新包：`C:\Users\user\Documents\Codex\Artifacts\NPDMS-DAC-20261021-v1.0.1\NPDMS-DAC-1.0.1-x64.msi`，90,822,018字节，未签名；复用上述未变的DAC冻结清单。原1.0.0及中间文件保留作问题证据，后续安装使用1.0.1。
- 验证：真实MSI会话加载包内校验动作，覆盖原错误输入、LAN HTTP提示、纠正后通过、地址规范化、本机开发地址、修改后清除旧校验结果、错误不回显凭据和静默安装拒绝；原生配置检查、WiX标准ICE及251文件解包核对通过。实际配置页观察到缺少协议提示，返回修改为完整本机地址后`Valid=1`并退出配置页；该验证窗口只运行对话框，没有执行安装序列。日志为`.run/build/dac-desktop-package-v1.0.1-20261021.log`及产物目录`logs/installer-validation*.log`。完整安装和服务启动仍未由本次修复执行。

### 内网 HTTP 接入（1.0.2）

- 需求方明确批准当前内网HTTP环境，取消客户端非回环HTTP限制：后端`http://10.210.0.11:59191`、网页`http://10.210.0.11:19191`。原1.0.1限制和验证记录为当时事实，本增量按新授权调整；不改任务票据、来源授权、回环监听或业务状态。
- 安装器与DAC配置接受HTTP/HTTPS，保留协议、主机、端口、来源路径及秘密参数校验；新安装预填上述两个独立地址，既有配置仍保留。网页不再因非安全上下文直接阻止探测，保留用户点击的固定启动协议及就绪轮询。
- 内网HTTP浏览器接入使用Chrome/Edge官方`LocalNetworkAccessAllowedForUrls`配置，仅登记配置的网页来源及端口；不关闭全局安全功能、不覆盖组织策略。安装器及设置界面记录自己新增的条目，切换环境、首次安装回滚或卸载仅清理仍归自己所有的条目；已有匹配或后来被外部修改的条目保留。
- 产物：`C:\Users\user\Documents\Codex\Artifacts\NPDMS-DAC-20261021-v1.0.2\NPDMS-DAC-1.0.2-x64.msi`，90,822,421字节，未签名。DAC重新构建并冻结至`.run/build/releases/fe31f44a-dd19-42bd-9939-3eacb341023a/manifest.json`。未复用1.0.1的旧DAC运行包。
- 已验证：锁定工具链下DAC10模块构建和21项相关测试通过，包括实际HTTP来源的完整MockMvc/H2配置、错误来源拒绝及离线原结果回传；网页3文件15项测试通过。原生隔离注册表用例覆盖精确来源、重复安装、已有匹配条目、外部修改保护、环境变更和清理；真实MSI会话验证默认地址动作、LAN HTTP及静默安装校验。WiX标准ICE及251文件只读解包核对通过。日志为`.run/build/dac-desktop-http-release-20261021.log`、`dac-desktop-http-ui-tests-20261021.log`和`dac-desktop-package-v1.0.2-20261021.log`。
- 浏览器已观察：在`http://10.210.0.11:19191`使用已有登录态，从配置调试现有记录打开采集窗口，显示“未连接到本机DAC”“启动DAC”链接及“重新检测”；未再出现强制HTTPS阻断，没有提交设备命令或改变业务状态。
- 实机边界：尚未安装1.0.2、写入真实浏览器策略或启停任何服务；管理员ACL持久化、HTTP网页发现已就绪、协议拉起及完整安装验收待独立安装/启停授权。上述自动化及页面观察结果不替代完整真实浏览器验收，不晋级整体Feature完成状态。

### 获批实机安装及 HTTP 发现、拉起验收（1.0.3）

- 需求方随后明确授权安装及本机服务启停验收。1.0.2实际安装退出码0；发现LocalService无法自行注册Windows Application事件源，安装器补齐事件源注册后生成1.0.3，并实际升级成功（退出码0）。1.0.3启动、停止信息已写入Windows事件日志，原环境配置保留。
- 最终安装包：`C:\Users\user\Documents\Codex\Artifacts\NPDMS-DAC-20261021-v1.0.3\NPDMS-DAC-1.0.3-x64.msi`，90,822,660字节，未签名。复用未变的HTTP版DAC冻结清单`fe31f44a-dd19-42bd-9939-3eacb341023a`；原生编译、真实MSI配置验证、WiX标准ICE及251个载荷文件核对通过，不重复运行前述仍适用的21项DAC及15项网页测试。
- 实机安全与配置：服务使用LocalService、自动启动类型且仅监听`127.0.0.1:48182`；管理员模式的DPAPI持久化和凭据ACL检查通过，普通用户没有凭据文件读取权限。Chrome/Edge真实注册表均仅登记网页来源`http://10.210.0.11:19191`。正确Origin状态探测200，错误Origin及无票据保存连接请求拒绝；普通用户通过Windows注册协议能够启动服务。
- HTTP网页验收：先明确停止本机服务，23:03:16确认Stopped；内置浏览器23:03:28产生正确的协议导航，但未实际启动服务，此结果不计作拉起成功。随后由需求方在日常Chrome/Edge浏览器点击“启动DAC”并确认页面“本机DAC已就绪”；Windows事件日志记录23:04:43服务启动，23:05:19核对Running及回环监听，验收页面同时显示就绪。此次为用户协助的真实浏览器拉起验收，没有将命令行启动冒充网页结果。
- 运行边界：19191前端、59191后端及原`10.210.0.11:48182`服务器DAC保持运行；本机DAC与原DAC监听地址不同。本次未执行设备命令、修改业务记录或直接变更MySQL迁移状态；开机重启、卸载和本机采集业务全流程未在本增量验收，不晋级整体Feature完成状态。
- 证据：最终产物目录内`acceptance-result.json`、`browser-http-dac-ready.png`、`logs/upgrade-actual-20261021.log`、`logs/stop-before-browser-launch-visible.json`及`logs/service-after-user-browser-launch.json`；管理员DPAPI/ACL证据保留在1.0.2产物目录的`installation-audit.json`。打包记录中的`installedOrStarted=false`仍表示打包步骤自身未执行安装，不覆盖后续实机记录。

### 保存连接调用入口完整性审计（2026-10-23）

- 需求方要求先核对所有调用入口，暂不部署或启停服务。本次沿用代码质量审查检查调用数据、授权与失败分支，不以单个配置调试入口代表所有入口完成实机验收。

| 入口 | 来源编号与设备来源 | 项目、协议与操作校验 |
| --- | --- | --- |
| center 设备连接与采集 | 页面已选择的项目；窗口内选择设备，未选时允许项目级连接查询/保存，但禁止执行命令 | 项目经来源授权；协议默认SSH且可选TELNET；中心选设备经ASSET验证 |
| configuration 配置调试（旧页、新页、包装组件） | 已保存配置记录ID；设备从服务端业务记录取得，空调用方设备不覆盖来源设备 | 项目和版本来自来源；失效设备或缺项目在调用前/业务层明确拒绝 |
| joint-test 业务联调（旧页、新页、表单入口） | 已保存联调记录ID；设备从服务端记录取得 | 新增未保存记录不发起采集；项目/设备/版本沿用来源授权 |
| cutover-checklist 调研清单 | 清单项ID；外层冻结设备选择传入；单设备来源也可解析默认设备 | 设备由CUT适配器验证在任务范围内；只读标志透传 |
| cutover-pre-check 割接前检查 | 闭环记录ID；外层选择设备 | 项目取割接任务；设备经CUT范围校验；只读时只查看 |
| cutover-execution 执行核查 | 闭环记录ID；外层选择设备 | 同上，entry固定映射EXECUTION环节 |
| cutover-test 业务测试 | 闭环记录ID；外层选择设备 | 同上，entry固定映射TEST环节 |
| cutover-rollback 回退核查 | 闭环记录ID；外层选择设备 | 同上，entry固定映射ROLLBACK环节 |
| cutover-post-collection 割接后采集 | 闭环记录ID；外层选择设备 | 同上，entry固定映射POST_COLLECTION环节 |

- 已修复：空设备字符串归一化并回退来源设备；保存票据使用保存表单冻结的设备/协议；缺项目、操作、协议、执行ID在服务层明确拒绝，HTTP元数据增加必填/正数/版本校验且保留合法版本0；加载失败不再向Vue事件冒泡。首次下发校验实际设备、连接和保存连接版本；没有原任务时不调用prepare/resume。
- 已修复割接只读标志未向内层采集窗口传递的两处调用；重新执行按原任务设备回填，防止多设备来源的null覆盖目标，并清空旧临时密码。TASK_READ继续只需原执行ID，resume继续原平台任务，均不误套新连接保存的设备/协议必填要求。
- 验证：五个前端测试文件106项通过，覆盖九类entry参数化数据传递、无设备中心、缺项目/无效编号、加载失败、冻结目标、空继续任务、六个CUT重新执行和只读透传；平台来源与请求校验6项、INT票据/任务7项通过。证据`.run/dac-entry-audit-frontend-tests-20261023.log`、`.run/dac-entry-audit-platform-tests-20261023.log`。
- 本轮未部署、未重启、未写业务数据或执行设备命令。上述为代码/调用契约与定向测试结论，九类入口仍须升级后逐项真实浏览器验收；不能承诺所有运行数据都已实测。后端代码又有校验增量，上一轮`84f1d72b-c824-477c-8563-c65a15058dcb`冻结包已过期，部署前须重新构建冻结；DAC端代码与1.0.6-scoped包未变。

### 本机保存连接按设备优先匹配（2026-10-23，1.0.6）

- 需求方授权重建、部署和本机升级后，最新后端经统一构建入口完成，源码一致性及22项既有修复保护通过，冻结清单`.run/build/releases/07383373-42b7-4d90-a73a-ae2e0decf8e1/manifest.json`；统一部署入口以`local,dac`启动59191，PID29260，健康检查通过。本机1.0.6-scoped安装退出码0，状态READY、协议2，原服务器和网页地址保留；前端19191已恢复，1.0.6下载链接HEAD返回200、90,835,012字节。证据`.run/dac-nine-entry-backend-build-20261023.log`、`.run/dac-nine-entry-backend-deploy-20261023.log`、`.run/dac-nine-entry-upgrade-retry-20261023.log`。
- 九类入口真实验收进行中：配置已完成记录保持只读，显示本机已就绪；采集中心无设备下发显示明确选设备提示。浏览器发现保存连接缺字段时表单校验拒绝仍冒泡，已就地处理保存前校验、保存表单校验和下发表单校验的预期拒绝，保留原必填规则；定向34项测试通过，浏览器缺字段提示复验未新增控制台异常，证据`.run/dac-nine-entry-validation-test-20261023.log`。真实设备保存连接正在等待用户直接输入密码，尚未宣称九类入口保存、重开回显全部通过。
- 用户确认已保存连接后，真实调研采集重开自动选中本机连接SN（SSH、10.253.1.22:5555），保存连接模式不呈现密码输入。用户反馈失败后重试报错，浏览器定位到重试固定切回临时认证并残留字段错误，已改为重新按原设备与协议匹配本机连接，并清除旧校验；不自动补密码、不改写原执行。相关35项测试通过（`.run/dac-nine-entry-retry-test-20261023.log`）。设备对原`show verison`返回`% Unknown command`，属于真实命令拒绝；修复后通过失败记录的“重新执行”仅下发一次正确只读命令`show version`，2026-10-23 12:12:21新执行显示日志已回传、已回传业务，原两条失败及原成功记录保留，关闭重开仍匹配SN。浏览器证据`.run/dac-retry-browser-20261023.txt`；此结果不代替其他八类入口的完整实测。
- 用户进一步澄清，实际剩余故障是已有失败历史后直接填写新命令下发，并非点击“重新执行”。在当前任务`2113184905149976576`、调研项`2113186182932107264`实机复现：前一执行更新来源版本后，窗口继续携带旧版本，prepare返回`1010005000 / 割接记录已变化或不可采集，请刷新并选择设备`，不是历史失败记录本身禁止新下发。已在新下发确认后重新读取并校验当前来源，再提交当前版本；项目、固定设备、权限和版本缺失仍阻止派发，不重试原任务、不降低服务端并发校验。新失败测试修复前6项失败、修复后相关41项全部通过，覆盖SERVER/DESKTOP版本刷新及权限撤回、项目/设备变化、缺版本。证据`.run/dac-stale-source-before-20261023.log`、`.run/dac-stale-source-after-20261023.log`。
- 当前任务真实浏览器以本机已保存Telnet连接sds完成连续新下发：同一窗口、不关闭、不点“重新执行”，12:19:21及12:19:39两次正确只读`show version`均日志已回传且已回传业务，原`show veri`拒绝命令和SSH超时历史保留。新执行ID分别为`2113485402546839554`、`2113485475821330434`，prepare均code=0；证据`.run/dac-direct-dispatch-browser-20261023.json`。此项替代此前仅验证另一记录“重新执行”路径的不足；九类入口整体仍未宣称完成，P6五类入口等待可办理的负责人/草稿记录，未绕过Owner限制。
- 继续剩余入口真实验收时，通过列表筛选发现另一条当前账号可办理且已有草稿的P6任务`2110653642904092672`（UI验收-详情式新建-20261015，吉林移动互联互通系统防火墙建设项目），冻结设备`02010481p232000202`，无需切换负责人或推进业务阶段。该设备同项目已有待开始配置`30038`；无联调记录，通过正常UI新增明确标注仅作DAC采集验收、不代表联调通过的记录`30037`，状态仍待联调，未提交业务完成/闭环结果。
- 剩余8类入口已完成真实缺项检查：采集中心无设备显示明确选择设备提示；配置、联调及P6五环节缺连接字段时显示表单提示，无新增控制台异常。配置、联调另以网络观察确认缺项未发出执行请求；P6五类均由外层选择冻结设备/环节进入，未伪造来源数据。现有其他设备的本机连接未误匹配至本设备。证据`.run/dac-remaining-entries-browser-20261023.json`。已在P6本机保存窗口填写非秘密目标与名称`DAC-P6-验收20261023`并交给用户直接输入密码；保存成功、重开回显与连续下发尚等待此项输入，不将缺项通过记为正向闭环完成。本轮未提交、推送或提交割接流程。
- 用户确认保存成功后补齐正向验收。实测发现P6平台回调误将发起人ID写入自动回调证据，违反既有`chk_cut_collection_union`的系统身份0约束；仅修正该新接入路径的`recordedBy(0L)`，保持下发证据的真实发起人、平台回执及历史不变，不修改数据库约束。回归测试先以expected 0 / actual 9复现失败，修复后8项CUT定向测试通过，覆盖成功/失败平台回调和重复回放。最新后端统一构建、源码一致性和22项既有修复保护通过，冻结清单`.run/build/releases/0ceece80-524d-4331-8459-33e7abb45f7b/manifest.json`；用户另行明确批准后部署59191，PID8512。配置回传同时发现既有服务器DAC与回调网关未运行；在用户同次批准下，经统一入口启动已验证冻结DAC清单`0a12f881-f35e-4247-927f-bc5fc5da797a`，服务器DAC PID27664、网关PID27776，本机DAC保留运行。P6与配置原结果均补传成功，未为补传重执行设备命令。
- 剩余8类入口真实验收已完成，以下结果替代上述等待密码/正向待验时点。连接`DAC-P6-验收20261023`由用户直接输入密码保存；各入口均核对同设备匹配、关闭重开自动选中且密码输入不回显，并在同一窗口连续下发两次只读`show version`。有设备按设备/协议匹配，另一个冻结设备和Telnet不误用该SSH连接，切回原设备/SSH重新命中；采集中心无设备时按项目/协议命中，仍阻止无目标设备下发。原失败历史不删除，未操作业务通过或提交闭环，P6最终仍为草稿，成功/失败结果均未选择。修复后验收页未新增控制台错误。

| 剩余入口 | 实际来源 | 连续下发结果 |
| --- | --- | --- |
| center 采集中心 | 项目992203060198，设备2104186883493924866 | 两条执行完成 |
| configuration 配置调试 | 30038 | 原一条补传成功；同窗另两条已回传业务，配置仍待开始 |
| joint-test 业务联调 | 30037 | 同窗两条已回传业务，未操作联调通过 |
| cutover-pre-check 割接前检查 | 闭环2112512667354619904 | 两条已回传业务，第一条为原结果补传 |
| cutover-execution 割接执行 | 同一闭环 | 同窗两条已回传业务 |
| cutover-test 业务测试 | 同一闭环 | 同窗两条已回传业务 |
| cutover-rollback 回退 | 同一闭环 | 同窗两条已回传业务 |
| cutover-post-collection 割接后采集 | 同一闭环 | 同窗两条已回传业务 |

- 证据：`.run/dac-remaining-entries-browser-20261023.json`逐入口状态与历史，`.run/dac-p6-callback-before-20261023.log`、`dac-p6-callback-after-20261023.log`回归，`.run/dac-p6-callback-backend-build-20261023.log`、`dac-p6-callback-backend-deploy-20261023.log`和`dac-server-restore-20261023.log`构建/部署/独立服务恢复。结合此前调研清单入口SSH与Telnet实机结果，九类入口的本轮范围验收已补齐；不据此晋级整个Feature为Done。未提交、推送，其他改动与临时文件保留。

- 需求方明确设备优先：有设备时按设备与当前协议匹配，没有设备时按当前项目与协议匹配；保留环境、租户命名空间及本人隔离。单条重开自动选中，多条不猜选，密码不回显；历史无关联连接不自动补归属。
- PLT复用来源适配器验证设备归属，INT将设备/协议范围签入短期票据；本机新建V26关联表，与连接保存同事务提交，读取与执行均校验范围。旧通用连接、加密凭据和历史记录不修改。旧服务端缺范围的票据拒绝使用，协议2要求更新1.0.6客户端与网页，不降级到协议1未限定范围的列表。
- 前端27项相关测试通过；平台3项来源范围测试、INT7项票据/任务测试通过；本机安全回归覆盖设备不回退项目、无设备项目筛选、协议/用户/命名空间隔离、历史不参与和跨设备拒绝执行。初次新增用例发现枚举与字符串协议比较问题，已修复；测试夹具连接名称唯一约束冲突已纠正，未降低原约束。
- 真实浏览器以来源/票据/连接元数据替身验证首次打开和关闭后重新打开自动选中，显示名称、地址、端口、用户名，保存模式无密码字段；替身只存在测试标签，已清除并关闭，没有写业务数据或执行设备命令。证据`.run/dac-scope-browser-preview-20261023.txt`及PNG，不冒充已部署业务验收。
- 服务端构建冻结清单`.run/build/releases/84f1d72b-c824-477c-8563-c65a15058dcb/manifest.json`，打包沿用此前定向测试结果而不重复全量测试；该产物包含构建时工作树已有改动，部署前须明确快照范围。客户端最终协议2安装包及实机验收另按本轮结果补记。本轮尚未启停或部署服务，运行环境保存/重开闭环仍待部署验收；MySQL版V26尚未实库执行。
- 最终协议2 DAC冻结清单`.run/build/releases/0a12f881-f35e-4247-927f-bc5fc5da797a/manifest.json`，DAC13项、平台10项、前端27项定向测试共50项通过。服务端冻结产物已通过`worktree-backend.ps1 -ValidateOnly`及22项已部署修复基线校验，没有部署或重启服务。
- 最终安装包在`C:/Users/user/Documents/Codex/Artifacts/NPDMS-DAC-20261023-v1.0.6-scoped/NPDMS-DAC-1.0.6-x64.msi`，WiX、真实MSI会话及251项载荷校验通过；下载入口随之更新为1.0.6。`backend-uncommitted-paths.txt`列出后端工作树相对HEAD的已有改动范围，供部署批准时审阅。另一个早期1.0.6构建目录为未发布中间产物，不作为本轮升级入口。
- 最后尝试HEAD复核1.0.6下载链接时19191端口拒绝连接；安装包已复制到public目录，但不记作当前HTTP下载验收通过。检查期间本机DAC状态由Stopped变为Running，并非本轮操作；未擅自启动网页或重启其他会话的服务。

### 本机DAC探测失败的安装包下载入口（2026-10-22）

- 需求方批准在探测失败时提供安装包下载、版本不兼容时提供更新；仍区分“未连接”与“未安装”，保留启动、重新检测和原票据约束。
- `LocalDacStatus`复用现有探测结果；同源下载1.0.5 MSI，新增`publish-dac-download.ps1`发布入口，安装包载荷放前端public目录并忽略入Git。正式静态部署需先发布载荷再构建网页。
- 9项定向测试通过；真实已登录业务窗口验证READY不显示下载，单独测试标签临时阻断发现请求后显示下载链接、提示与禁用下发；未停止任何DAC或执行设备命令，已清除测试阻断并关闭测试标签。
- 下载地址HTTP 200，完整下载90822724字节且与原安装包SHA256一致，证据`.run/dac-download-result-20261022.json`、`.run/dac-download-unavailable-20261022.txt`。浏览器点击下载的事件采集超时，不声称浏览器文件保存验收通过。

### 安装后本机状态页（2026-10-22，1.0.5）

- 需求方批准：交互式安装成功后自动打开本机只读状态页，静默安装不弹浏览器。
- 复用现有desktop状态接口；仅开放GET `/desktop-status`，显示就绪状态与已配置的环境地址，不开放通用旧页面或设备命令接口；回环Host限制和任务票据保持生效。
- 安装器在成功返回后的交互界面序列调用启动器，等待页面可访问后使用默认浏览器打开；静默安装、卸载、失败与取消不执行打开动作。
- 锁定工具链构建及5项HTTP安全测试通过，覆盖页面可读、非回环Host拒绝、POST拒绝、业务接口和旧页面继续拒绝匿名访问。首次测试JVM启动失败、无测试执行；重试通过，冻结清单`.run/build/releases/48c91cda-1e64-401e-ba1f-60dcbf1206d2/manifest.json`。
- 浏览器以真实HTML和模拟状态响应验证启动中、已就绪、连接失败及重新检测恢复，证据`.run/dac-status-preview-20261022.txt`与对应PNG；这是页面预览验证，不冒充已安装服务验收。
- 1.0.5安装包已生成，WiX校验、真实MSI会话验证和251项载荷比对通过；MSI表验证打开动作仅在交互UI序列且位于ExecuteAction之后，静默执行序列没有该动作。未升级或重启现有服务，实际安装后自动打开及静默安装不弹窗仍待授权实机验收。
- 需求方随后明确批准升级本机DAC并重启验收。实际交互升级（UILevel=4）退出码0，注册版本1.0.5，本机服务恢复Running，回环状态接口READY，服务器及网页地址保留；原10.210.0.11:48182服务器DAC继续监听，未重启59191后端。安装日志确认OpenDesktopStatus在安装成功返回后执行；真实浏览器另行打开已安装页面，显示“本机DAC已就绪”和原环境地址。证据在`C:/Users/user/Documents/Codex/Artifacts/NPDMS-DAC-20261022-v1.0.5/`（upgrade-result.json、logs/upgrade-actual-20261022.log、status-installed-ready.txt及PNG）。默认浏览器实际自动弹出的用户确认仍待补齐，不将另行导航算作自动打开验收；未进行额外静默重装。
- 需求方随后确认：“已自动打开，显示已就绪”。结合安装日志和已安装页面实机检查，交互式安装成功后自动打开本机状态页验收通过；静默安装仍以MSI执行序列检查为验证边界，未进行额外静默重装。

### 本机下发及原结果回传修复（2026-10-22，1.0.4）

- 用户反馈下发报错，59191实际日志确认`plt_collection_request.dispatch_mode`缺失；运行库`npdms_domain_test`停在V510。通过原Compose项目`npdms-domain-test`执行定向Flyway，保留422个可解析已执行迁移及既有`*:missing`策略，仅新增V511，校验442项后成功迁至511。原110条采集请求、109条派发记录数量不变，原记录均保持SERVER运输语义；没有手工改表或改写历史迁移记录。
- 真实浏览器沿用用户当前填写的`show version`执行一次，本机DAC返回202并完成真实设备执行，任务`bcac3ccd-b3fb-4da4-b69c-644fc21051a1`、collection`f36b3e5d-8b77-4e8d-9a2c-331b4a80dfc7`，状态SUCCEEDED。平台回传仍被`DAC_CALLBACK_IDEMPOTENCY_CONFLICT`拒绝：本机target=1/attempt=1的事件编号与2026-09-21旧服务器回执重合。设备成功不记为平台或业务回传完成，不再次执行设备命令。
- 仅desktop运输层将回调编号按命名空间、平台任务号及原事件编号稳定派生，保留原Outbox编号、正文和已有回执；确认回执仍须匹配派生编号，成功后才标记原事件投递完成。原服务器运输路径不变，升级后的待投递原结果可继续补传。
- 失败测试先复现两个独立数据库的事件编号重合；修复后覆盖不同任务隔离、原编号ACK拒绝、重复补传编号稳定及原载荷不变。锁定工具链构建及22项相关测试通过（server14项、adapter8项），冻结清单`.run/build/releases/fc508758-b1b6-4b60-ad63-4d0967596cea/manifest.json`。证据在`.run/dac-desktop-dispatch-fix-20261021/`，实际升级及补传验收另据后续实机结果记录。
- 需求方明确批准本次升级及原结果补传验收；1.0.4实际升级退出码0（2026-10-22 00:55:43），NpdmsDac恢复Running。00:56:34原结果自动取得新回调编号的有效Receipt，00:56:35追加至`configuration/30044`业务日志：原任务状态COMPLETED、technicalStage=RESULT_RECEIVED、resultVersion=1、fileVersionId=850、consumedResultVersion=1。真实HTTP网页重新打开原记录显示“日志已回传”“已回传业务”，查看日志读到原SUCCEEDED与show version设备输出。配置业务状态仍为0，旧冲突Receipt保留；升级验收未再次执行设备命令。
- 最终包`C:\Users\user\Documents\Codex\Artifacts\NPDMS-DAC-20261022-v1.0.4\NPDMS-DAC-1.0.4-x64.msi`，90,822,660字节，未签名；真实MSI配置校验、WiX ICE及251文件核对通过。实机证据为产物目录`logs/upgrade-actual-20261022.log`、`dispatch-fix-result.json`、`browser-original-result-delivered.png`及仓库忽略目录`original-result-delivered.txt`。未重启59191或原服务器DAC，未提交或推送；本结果限定本次真实命令与补传链路，不晋级整体Feature完成状态。

### 2026-10-22 本机DAC原任务恢复修复（需求方已批准）

已复现同一EXECUTE票据在无网页登录身份时返回项目不存在，有网页登录身份时成功。修复边界：采集中心到AST/PROJ显式传递原任务发起人并保留范围复核，前端不再用原任务尚未确认掩盖明确失败原因；保留原任务和本机执行去重。验证无网页登录身份下的授权复核、越租户/越项目/设备范围失败及恢复请求的原标识。真实原任务恢复需要新后端包部署；每次59191重启仍依运行规则单独授权。

本轮定向验证：平台/项目/资产/集成28项测试及DAC本机HTTP/任务持久化6项测试通过；前端21项测试通过。原任务继续请求不调用新建任务API，本机真实执行协调链测试再次POST原任务返回相同collectionId与existing=true、命令执行器调用仍为1次；未获取或持久化设备密码。首次完整打包被割接模块并行改动中的snapshot重复变量阻断，随后该并行修改已自行修正，本次未修改割接源文件，继续等待构建锁并重打包。原任务验收脚本`.run/verify-dac-original-resume.py`只沿用f7dee74b-f59b-46b8-b2b1-2c7cce83b73d，通过原resume及本机执行入口验真；尚未对实际设备执行，未重启本轮后端。

### 2026-10-22 本机DAC原任务恢复实机验收完成

需求方确认其他会话暂停，并明确批准本次59191重启。完整构建、源码一致性及22项已部署修复保护通过；冻结清单`.run/build/releases/211bce1d-a51e-45b8-b1ea-459c665992ba/manifest.json`。通过worktree-backend.ps1以local,dac部署，PID20924，后端健康及服务器DAC独立readiness正常；未更新或重启本机DAC。

原EXECUTE票据不带网页登录Authorization已正常解析。真实浏览器沿“设备连接与采集→V2并行隔离验收-工程直签-20261022→执行历史→继续下发”恢复原任务`f7dee74b-f59b-46b8-b2b1-2c7cce83b73d`，原执行记录`2113284136744345601`保持不变。原show version执行成功并回传，平台COMPLETED/RESULT_RECEIVED、resultVersion=1、fileVersionId=990，浏览器历史仅1条且能打开原日志。

随后通过原resume入口刷新授权，向同一本机任务入口发出两次重复请求，均返回existing=true及同一collectionId `3b78d35f-8e89-4ba4-8d40-a9f4a607cfd4`。原targetId=3、命令输出块1个，项目执行记录仍仅1条；没有调用新建任务或“重新执行”入口。结合本机HTTP去重测试中命令执行器调用保持1次，验证重复请求沿用原执行、不再派发设备命令。未改写历史、取消或替换原任务。

验收证据：`.run/dac-original-resume-evidence.json`、`.run/verify-dac-original-resume.py`、本次构建日志及浏览器截图。此前“暂不打包/尚未部署”的时点限制由本次结果替代；34项后端/DAC及21项前端定向测试证据保持适用。

### 候选端口、首次配对和双安装器（2026-10-30，代码与比较包阶段）

- 需求方批准使用 8280～8289，首次人工核对安装身份公钥指纹，后续自动挑战验证。九类入口经同一显式环境请求层发现；签名绑定随机 nonce、实际端口、运行实例和环境。未配对或身份异常不发送业务票据和设备密码，执行超时不自动重发设备命令。
- 本机 Windows 宿主持有实例锁，按上次成功端口/完整候选集合启动，仅遇明确端口占用退出码才换端口。安装后页面及设置读取实际运行记录；独立服务器 DAC、原 H2/DPAPI/连接范围/Outbox 路径不变。身份私钥作为新文件由原 AES-GCM 主密钥加密，不更新或覆盖历史。
- 版本 1.1.0、发现协议 3；提供原 MSI 与独立 Inno EXE，共用同一冻结 JAR、JRE、WinSW 和 251 项载荷。跨安装器直接覆盖被阻止，切换时先卸载原类型且保留 ProgramData。Inno 具备静默参数、原服务停止/恢复、失败新服务清理与卸载重试的幂等处理；这些原生服务操作尚未实机验收。
- 验证：前端发现/配对/UI/九类入口 80 项通过（`.run/dac-ports-front-nine-20261030.log`）；全量 vue-tsc 在 8 GiB 堆限制下通过（`.run/dac-ports-typecheck-final-20261030.log`）。第一次默认 4 GiB 检查因内存不足退出，已保留日志；后续检查发现并修正四处本次测试类型错误。
- DAC 完整 Maven 构建及 96 个报告的 518 项测试通过、无跳过，使用统一工具链和工作树锁；其中 MySQL 集成为隔离测试容器，未操作共享测试数据库。原完整构建清单 `.run/build/releases/919b1313-0798-4a29-938d-a8d540db624f/manifest.json`，完整日志 `.run/dac-port-full-build-20261030.log`。
- MSI 在真实 Windows Installer 会话中只运行只读配置校验动作，标准 WiX 验证及 251 项载荷解包哈希校验通过；原生 URL/候选顺序/协议/DPAPI 往返和隔离注册表测试通过。管理员令牌下的配置持久化与 ACL 测试未运行，安装序列未运行。Inno 编译及全部共享输入载荷哈希核对通过，不声称完成 Inno 解包或实机安装验证。
- 最终比较包目录：`.run/build/dac-desktop-1.1.0-20261030-paired`、`.run/build/dac-inno-1.1.0-20261030-paired`；结果及比较见 `docs/generated/2026-10-30-dac-installers/comparison.md`。旧中间包保留，仅最终目录作为本轮比较入口。
- 遵守本轮边界：未部署、未发布下载载荷、未安装/升级 DAC、未启动/停止/重启产品服务、未提交或推送。端口真实占用切换、原数据升级、两种安装器成功/取消/失败/卸载及九入口真实浏览器业务验收留待单独授权；Feature 不据此改为完成。Linux 仅复用 Java/网页契约，systemd、主密钥保护及 Linux 安装器未实现。

- 安全复核增量：原生启动器也必须向端点发送随机挑战，并使用随包 Java 的独立只读验证入口核对签名、公钥和实际端口，不能仅比对公开字段；验证入口不初始化 Spring、数据库或应用服务。新增后20项 Desktop 定向测试通过，冻结清单更新为 `.run/build/releases/37fe9c5a-5beb-4cfb-b67b-2b55b8397919/manifest.json`，日志 `.run/dac-port-java-verifier-build-20261030.log`。使用随包精简JRE与实际可执行JAR验证浏览器库生成的合法签名及错误nonce，分别返回0/1（`.run/dac-native-proof-process-20261030.log`）。此前518项完整测试结果与本次20项定向检查分别记录，不累加为538项。

### 安装前自动卸载旧版（2026-10-30，1.1.1，代码/比较包）

- 需求方要求支持自动卸载，按安装新版本时自动卸载旧版实现，包含 MSI/Inno 双向切换及 Inno 自身升级；不操作当前已安装 DAC，不部署或重启。
- MSI 同类升级沿用 MajorUpgrade；跨 Inno 使用嵌入式延迟提权动作，先只读校验本产品 HKLM64 安装记录、版本、受限目录及卸载入口，再在 InstallInitialize 之后、InstallFiles 之前静默卸载。卸载失败、需重启、旧注册记录或服务残留均中止；跨类型强制复制全部载荷，防止相同版本 JRE 文件在旧卸载后被新 MSI 跳过。修复/卸载已有 MSI 不触发跨类型清理，多安装器冲突不擅自选择。
- Inno 在开始写入新文件前校验配置，重新确认旧版版本/身份；按固定 UpgradeCode 枚举 MSI ProductCode 调用系统 msiexec，或调用经目录/文件名校验的本产品 Inno 卸载入口。仅退出码0并确认旧记录和服务清除后继续；3010 不自动重启。降级、未知服务来源、异常卸载入口和并发状态变化均拒绝继续。
- 保留原 ProgramData 配置、主密钥、连接、配对身份、任务历史及 Outbox。跨类型和 Inno 的卸载/新安装不属于同一事务：旧程序卸载后新安装失败，数据保留，但不承诺自动恢复旧程序，可重新运行完整新安装器；未伪造回滚成功。
- 失败测试：原比较包的真实只读 MSI 会话在缺少 `PlanRemovePreviousInno` 时失败（`.run/dac-auto-remove-before-20261030.log`）。新包元数据与只读会话验证动作顺序、延迟提权/失败中止属性和强制重复制规则通过，未调用任何真实卸载动作。原生策略测试覆盖版本/路径/命令注入、失败退出码1/1603、重启码3010、退出0但旧记录/服务残留及不重复调用；全部通过。管理员 ACL 与真实安装/自动卸载/故障恢复仍未运行。
- 前端下载链接更新1.1.1后80项定向测试通过（`.run/dac-auto-remove-frontend-20261030.log`）；Java/端口/配对及业务语义未变，复用37fe9c5a冻结产物及前轮结果，不重复跑完整后端测试。
- 最终包目录 `.run/build/dac-desktop-1.1.1-20261030-autoremove-release`、`.run/build/dac-inno-1.1.1-20261030-autoremove-release`；两包共享251项载荷。日志 `.run/dac-auto-remove-msi-release-20261030.log`、`.run/dac-auto-remove-inno-release-20261030.log`。比较记录见 `docs/generated/2026-10-30-dac-installers/comparison-autoremove.md`。旧1.1.0比较包及中间产物保留，不作为此增量入口。
- 本轮未部署、未发布下载、未安装或卸载本机DAC、未启停/重启产品服务、未提交或推送；跨安装器真实验收留待授权。

### 1.1.1 实机安装失败调查与1.1.2修复准备（2026-10-30）

- 用户报告安装失败。只读系统事件及Inno日志显示：18:57:30～18:58:00旧MSI1.0.6已卸载成功；18:58:14～18:58:20 WinSW创建NpdmsDacHost.exe被Windows拒绝访问，服务未成功启动；Inno18:58:09先执行了打开状态页，随后18:58:44才报告初始化失败，留下1.1.1安装登记。用户随后18:59:29～18:59:39尝试MSI并得到1603，具体失败动作尚无完整安装日志，不能与Inno问题混为已证实同一根因。
- Windows权限阻止普通用户读取服务日志，已通过Windows管理员确认只读导出相关错误行，未读凭据库或修改服务；证据`.run/dac-install-failure-readonly-20261030.txt`、用户临时目录`Setup Log 2026-10-30 #003.txt`及对应系统事件。只读调用旧版卸载Plan动作当前通过（`.run/build/dac-readonly-install-plan-20261030/plan.log`），未调用真实Remove动作，不据此声称MSI卸载通过。
- 1.1.2：安装程序目录显式增加LocalService读取/执行权限，不增加写权限、不移除既有拒绝规则、不进入ProgramData；拒绝越出程序树的重解析点。WinSW工作目录固定安装目录。原生安装错误只记录动作、异常类型及Win32错误码，不记录参数或秘密。
- Inno服务初始化移至最后程序文件的AfterInstall，先初始化/注册/启动成功，再进入后续注册和Run；打开状态页同时受ServiceReady与skipifsilent限制，消除初始化失败还先打开浏览器及过早登记成功的已观察缺陷。实际服务进程仍需实机恢复验收，显式ACL不能代替实际LocalService启动结论。
- 隔离目录真实ACL测试通过，确认宿主/JRE获LocalService读执行而无新增写权限，旁边数据文件内容及ACL不变。此前自动卸载/浏览器策略/URL/DPAPI测试、MSI只读会话及251项解包哈希通过；前端80项回归通过。日志`.run/dac-install-fix-msi-release-20261030.log`、`.run/dac-install-fix-frontend-20261030.log`。
- 1.1.2两包共用原37fe9c5a Java冻结载荷，Java未改动；最终目录`.run/build/dac-desktop-1.1.2-20261030-install-fix-release`、`.run/build/dac-inno-1.1.2-20261030-install-fix-release`；Inno日志`.run/dac-install-fix-inno-release-20261030.log`。本轮仅准备修复代码/比较包，没有实际安装、卸载或启停产品服务，没有部署、提交或推送；当前失败安装残留状态未擅自清理。真实恢复需用户另行批准。

### 1.1.2服务已就绪后的启动器报错与1.1.3（2026-10-30）

- 用户自行安装1.1.2后反馈“DAC操作失败”。只读确认20:31:24服务成功运行，1.1.2安装登记成功，端口8280、协议3、状态READY。与1.1.1服务创建拒绝访问是不同问题，不继续误判成服务安装失败。
- 普通用户Process.HasExited访问LocalService进程句柄抛Win32Exception(5)已复现。1.1.3取消这项不必要的跨账户进程查询，保留运行记录/公钥/实例/实际端口/随机挑战签名及READY校验，不授予普通用户服务进程权限。
- 新增显式只读DesktopStatusLauncherTests，先复现旧权限拒绝，再经实际运行服务验证新路径成功；日志`.run/dac-status-permission-regression-20261030.log`。实际执行新产物启动器--open-status，默认浏览器打开动作执行成功、退出码0，未安装/重启服务。网页内容未据此宣称完整业务验收。
- 两种1.1.3比较包完成：`.run/build/dac-desktop-1.1.3-20261030-status-fix`、`.run/build/dac-inno-1.1.3-20261030-status-fix`，共用251项载荷。原生/真实MSI只读会话/解包及普通用户实机签名回归通过，前端80项通过；日志`.run/dac-status-fix-msi-20261030.log`、`.run/dac-status-fix-inno-20261030.log`、`.run/dac-status-fix-frontend-20261030.log`。比较证据见`docs/generated/2026-10-30-dac-installers/status-launcher-fix.md`。
- 当前安装仍为用户自行操作的1.1.2，本轮只读取运行状态并执行新产物只读打开页面动作，没有改写安装目录、安装/卸载/启停服务、部署、提交或推送。用户新提免配对码体验，已提出客户端握手方案等待体验确认；当前1.1.3仍有手动配对，不将新诉求冒充已实现。

### 首次点击连接、免输码（2026-10-30）

- 用户选择“首次点一次连接本机 DAC，免输码，以后自动检测”。正常网页及本机状态页不再提供人工配对码输入/展示；既有按环境保存的安装公钥继续使用。首次通过已安装的原生协议启动器核验受保护运行记录和签名，并以短时服务端中转结果回到原网页，不依赖系统默认浏览器与业务浏览器相同。
- Redis握手记录仅含租户、发起用户、nonce哈希、预期环境与公开签名状态，90秒自动过期，原子比较替换保留TTL；不新增业务表、不读取或改写连接/任务历史、不传递登录令牌或设备密码。错误签名、不同环境/用户/租户、取消、过期和并发覆盖均拒绝，原业务票据及范围校验不变。
- 网页提前准备链接，只有实际点击才调用系统协议；首次连接停止的客户端可由该用户动作启动，轮询不唤起应用。关闭/取消中止等待并取消当前记录，迟到确认不写安装公钥；旧客户端提示更新或连接超时，不盲信任候选端口。
- 1.1.4比较包：`.run/build/dac-desktop-1.1.4-20261030-connect/NPDMS-DAC-1.1.4-x64.msi`（90,364,405字节，SHA256 `33e11845f70ace8a22e6fda3f2d3fab9f5c679b7ba1e671dcbeab590cca59487`）；`.run/build/dac-inno-1.1.4-20261030-connect/NPDMS-DAC-1.1.4-x64-inno.exe`（87,796,104字节，SHA256 `67ae42e0c972c005dbb88a0e8e12c5d6e37a54fcb97864f820a375cc465c8775`）。两包共用251项载荷，通过真实MSI只读校验与解包哈希核验；本机1.1.2的普通用户只读签名状态回归通过，未调用启停/安装动作。日志`.run/dac-connect-msi-20261030.log`、`.run/dac-connect-inno-20261030.log`。
- DAC冻结清单`.run/build/releases/fe8576dc-8948-45bc-a0bd-80d55c4a9c1e/manifest.json`，20项Desktop定向测试通过（`.run/dac-connect-core-build-20261030.log`）；前端92项（含共享九类入口保存、重开回显、连续下发及新增确认/取消/超时/错误身份，含截止时间后才到达的确认）通过（`.run/dac-connect-frontend-20261030-deadline.log`）。完整vue-tsc在8GiB堆下通过（`.run/dac-connect-typecheck-20261030-verified.log`）。
- 网页认证接口归入Admin Controller，原生proof独立保持无admin-api前缀，显式使用CommonResult响应。补齐实际前缀和响应封装HTTP测试、匿名拦截与原用户/租户所有权测试；最终9项后端定向测试通过，当前工作树完整后端构建成功并冻结为`.run/build/releases/f05f63dc-7672-43ab-a0c5-a697d33b63ef/manifest.json`（`.run/dac-connect-backend-build-20261030-final.log`）。这些为构建/定向测试结果，不代表后端全量业务测试或真实浏览器验收。早期e4916bc4冻结包不含最终路由修正，不作为本次部署候选；未部署任何包。
- 尚未部署新后端/网页、发布下载载荷、升级安装或启停服务，未提交/推送，未执行数据库迁移或DDL。免输码端到端真实浏览器验收尚未执行，须配套新服务端和客户端后进行，不能仅凭这些测试宣称已上线。

### 1.1.4授权部署与保留验收（2026-10-30，网页免输码验收待继续）

- 需求方明确授权部署最新后端/网页、升级本机DAC并重启相关服务，不提交/推送。后端使用f05f63dc冻结清单经worktree-backend校验后部署，PID28760、local,dac、59191健康UP；网页经worktree-frontend启动19191（PID34788）。MySQL仍为既有受控测试库，自动迁移/DDL保持禁用；独立服务器DAC48182及59192回调网关未启停。日志`.run/dac-connect-backend-deploy-20261030.log`。
- 使用已校验Inno1.1.4升级原Inno1.1.2，安装器退出0、不要求Windows重启；实际登记1.1.4，251项安装载荷与冻结打包清单全部一致。本机8280 READY，安装公钥与升级前相同。实际网页下载MSI为90,364,405字节，SHA256与本节前述1.1.4 MSI一致，未返回SPA页面。证据`.run/dac-connect-runtime-20261030.json`及`.run/dac-connect-inno-upgrade-20261030.log`。
- 第一次升级后复制运行中H2文件被文件锁拒绝，初次脚本报告failed发生在安装成功之后，不能解释为安装失败。需求方随后单独明确批准再停启一次做一致性比对；已完成并恢复服务，未访问运行中的H2文件或执行设备命令。只读查询针对保存在管理员/System专用ACL目录的离线副本，报告只输出数量和摘要，不输出凭据内容。
- 升级前后7张表摘要全部一致：5条保存连接、4条范围关联、5条凭据密文、52条desktop任务、52条采集、52条目标与52条尝试。desktop任务摘要仅排除允许正常变化的轮询时间/结果确认标记，其余任务字段及其他表全列参与比对。安装身份公钥相同。证据`.run/dac-connect-upgrade-20261030.json`（含升级前基线及原始文件锁失败记录）、`.run/dac-connect-retention-after-20261030.json`（allUnchanged=true、serviceRestored=true）。
- 应用内真实浏览器已看到新版状态页“本机DAC已就绪”及“无需输入配对码”，截图`.run/dac-connect-status-20261030.png`。配置页面当前登录王前成，点击手工命令/日志返回没有该操作权限，未打开采集窗口；此前2114117553322168320割接任务在当前库无法加载。已请需求方提供有采集权限的账号或可用项目/记录，未改权限、伪造来源或绕过授权。因此首次点击免输码、后续自动检测及保存连接网页回显尚未验收通过；不得以运行就绪或数据摘要代替这些UI结果。外部Chrome在初始连接后断连，诊断发现native-host允许来源不完整，未自行修复插件，改用可连接的应用内浏览器。

### 自动检测等待优化（2026-10-30，验收反馈）

- 用户反馈就绪需5秒以上。发现层原先3个worker分批扫描10个固定候选端口，每个端口2秒超时；即使实际端口已READY仍等其余结果，最坏累计约8秒。新增慢端口回归先在旧实现失败，再改为10个固定端口同时探测，网络等待收敛到同一轮2秒超时，不提前跳过签名/环境/多实例校验。
- 新增迟到的第二个有效签名实例保护测试，确保不因记住端口而提前放行业务。发现、免输码、组件和九类入口共享回归共94项通过；日志`.run/dac-discovery-speed-before-20261030.log`（失败复现）、`.run/dac-discovery-speed-final-20261030.log`（通过）。本机Node并发HTTP读取10端口实测约41毫秒，仅为网络诊断，不冒充用户浏览器耗时。
- 只改网页发现层；19191 Vite已提供更新后的并发逻辑，无需再次安装DAC或重启后端/服务。保留当前免输码及后续自动检测的真实浏览器验收待办，尚未宣称用户浏览器已达到立即就绪。未提交、推送或更改数据和权限。

### 首个有效候选立即返回（2026-10-30，需求方进一步明确）

- 用户明确要求“检测到一个就直接返回，不等待所有接口校验完成”，替换上一节等待完整结果判断多实例的发现策略。首个通过随机挑战签名、精确环境、实际端口和既有公钥检查的候选立即返回，并中止其他探测；第一次无已确认公钥时仍为connection-required，不能发送票据/密码。错误身份的快速响应不抢占正确安装，迟到结果不覆盖已选结果；未找到有效候选才汇总错误，调用方取消立即取消全部探测。
- 原有“第二个有效实例迟到仍须等待并拒绝”的断言按本次明确需求更新为首个结果保持稳定；身份、nonce、端口、环境错误不发送设备密码及执行POST不自动重试等安全/业务失败测试保留。新增立即命中取消挂起探测、快速错误身份后命中正确安装、调用方取消等回归。失败复现日志`.run/dac-first-hit-before-20261030.log`，修复后95项共享入口/发现/免输码/UI测试通过（`.run/dac-first-hit-after-20261030.log`）。仅网页变更，不再次安装或重启DAC/后端，不提交/推送；真实浏览器整体验收仍按前述账号/记录条件继续。
- 完整vue-tsc检查通过（8GiB堆，`.run/dac-first-hit-typecheck-20261030.log`）；实际19191返回的模块已含首个结果返回及取消其余请求逻辑。
