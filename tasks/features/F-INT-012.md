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
