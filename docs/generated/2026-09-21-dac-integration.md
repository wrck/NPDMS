# DAC 专项接入记录

2026-09-21，按用户专项授权完成代码对接及手工命令业务集成，不运行工程实施链、不晋级 Feature/Task 状态。
用户确认补齐 DAC 适配、保留 NPDMS 现有业务契约；后续明确先按命令手工下发，不以命令模板发布为前置，割接和巡检暂不接入。
关联范围为配置调试 EXE-03 / FR-ENG-023、外部采集 INT-12 及安全 NFR-02。

## 实现结果

- 从 NPDP `codex/crt-persistence-context` 提交 `2d8a66d5197736ee612e879660bf0255b968bc86`
  导入 `device-ops-platform/` 的 586 个受版本管理文件，保留独立构建、数据库、进程及原生前端。
  来源工作区的数据库、密钥、依赖和运行数据未导入。
- 选择性复用 `codex/liteflow-remediation` 的 `c90daf62e` 网关、下发映射和对账实现；没有整体合入其业务改动。
  原增量 V255 与本仓库冲突，改为 V325。
- DAC 补齐任务级签名授权、租户命名空间、精确查询、真实取消、不可变结果重投及签名 multipart 回传。
  只有匹配的业务 ACK 才完成回传；HTTP 200 不等于业务完成。Telnet 支持执行中的中断取消。
- 配置调试新增“手工命令 / 日志”：输入 SSH/Telnet 目标、临时用户名/密码及逐行命令，下发后查看执行历史、
  请求取消、下载日志并关联结果。保留原编辑、开始、完成和手工上传日志功能；结果关联不自动完成配置调试。
- 新增 V326 `imp_configuration_collection`，保存配置记录、操作者、平台任务、请求摘要和消费版本。
  业务关联记录与平台任务先提交，再发外部请求；重复请求不重复执行；并发派发先以 CAS 占用任务。
  下发结果不确定进入对账，不能自动重用密码重发。
- 服务端复用配置调试权限、租户和当前项目范围；事务内锁定配置并重新验证范围；校验设备归属、版本及允许状态。
  日志消费保持既有状态机及幂等语义，下载委托文件平台的权限和项目范围检查。
- 密码不写业务表；请求结束清空字符数组。页面提交后清空密码，关闭窗口清空命令。
  开发访问日志和异常证据遵循 `@ApiAccessLog(requestEnable=false)`，不记录该请求体。
  文件存储回执不持久化预签名 URL 的令牌查询串，下载时重新取得短时地址。
- PLT 校验、扫描和归档回传日志，生成不可变文件版本及引用；异常内容进入隔离。
  DAC JSON 原文字节以 `.txt` / `text/plain` 归档，摘要不变，兼容既有内容嗅探规则。
- 默认不启用 DAC profile；复用已有菜单、权限和配置调试数据，未引入新角色或命令模板种子。

接口、独立部署及环境变量见 [模块说明](../../device-ops-platform/NPDMS.md)。主要入口为
`ConfigurationCollectionService`、`ManualCollectionDialog.vue`、`DacDeviceOpsGateway` 和 `NpdmsCollectionController`。
业务 API 为 `/admin-api/api/v1/pms/implementation/configurations/{configurationId}/collections`。

## 验证结果

### 定向测试与构建

基础适配阶段的 102 项定向测试通过：NPDMS 46 项、DAC 适配/队列 25 项、DAC JDBC 26 项、
既有 PLT MySQL 回调事务与并发 5 项。后续修改重跑受影响测试，不将重复执行累计为新增数量。

手工集成最终测试报告确认：配置业务服务 11 项、临时凭证派发 6 项、文件接收 4 项、DAC 网关 13 项、
敏感请求日志 2 项、存储回执 9 项均通过。既有配置生命周期 5 项已通过。
NPDMS 宿主机应用和独立 DAC 均打包成功，DAC 原生前端构建通过。
新增前端文件 ESLint/Prettier 检查通过。

全量 `vue-tsc` 未通过：配置调试旧设备选择字段、安装和业务联调页面合计 8 个既有类型错误，
涉及 `string | number` 和缺失 `DeviceArchiveApi`；新增弹窗/API 无报错。未借本次集成修改这些并行范围。

### 固定测试库迁移

在 `npdms-50eb-test-mysql-1` 的 `npdms_test` 中，通过隔离 Flyway 目录复用截至 V308 的既有迁移，
校验 252 项并成功应用 V325、V326。没有 repair、历史改写或账号授权变更。
本次未应用无关 V309～V324；这不是全仓库迁移完成，后续该测试库执行这些较低版本迁移时需显式处理顺序。

### 真实 HTTP 与浏览器

隔离运行 NPDMS 后端 59280、前端 19081、DAC 48182；使用真实登录、固定测试 MySQL/Redis、真实业务 API、
真实 DAC Telnet 执行和签名 multipart 回调。设备端为本机虚拟网卡上的 Telnet 模拟服务，存储为隔离 S3 模拟服务，
未连接业务设备或更改现有文件存储配置。DAC 使用隔离 local 身份模式，未验证部署环境 OIDC。

通过以下路径：

- 手工命令执行 → RESULT_AVAILABLE → 文件落库 → 业务消费 → COMPLETED；配置调试自身状态保持不变。
- 相同请求重放返回同一执行记录；重复消费幂等；日志下载含模拟设备输出且不含测试密码。
- 取消得到真实 CANCELLED 回调；另验证执行开始 2 秒后取消，未误判为成功。
- 目标连接失败得到 FAILED 日志；非法目标被拒绝；运行日志不包含测试密码。
- Chromium 真实页面完成下发、刷新、关联、下载、关闭重开历史；密码/命令清理符合预期，无页面异常。
  修正了执行时间显示为原始时间戳的问题，并回看验证日期及已关联状态。

测试创建专用标识的配置调试记录，保留其历史。模拟存储随验证进程退出释放，其下载内容和截图另存本地证据。
本次测试进程结束后停止，未替换现有开发实例。

主要本地证据（不纳入版本管理）：
`.run/dac-manual-e2e-result.json`、`.run/dac-manual-e2e-log.txt`、`.run/dac-manual-running-cancel.json`、
`.run/dac-manual-browser-result.json`、`.run/dac-manual-browser.png`、`.run/dac-manual-browser-history.log`、
`.run/dac-manual-flyway-scoped.log`、`.run/dac-manual-final-build.log`、各模块 `target/surefire-reports/`。
基础适配证据为 `.run/dac-focused-tests.log`、`.run/dac-integration-final-tests.log`、
`.run/dac-adapter-tests.log`、`.run/dac-persistence-tests.log`、`.run/dac-mysql-tests.log`。

## 交付边界

代码及模拟设备/模拟存储的业务闭环已完成并自审。尚未执行真实设备、真实对象存储或部署 OIDC 验收；
正式使用需注入服务身份/签名密钥、配置网络及持久存储，并按部署环境应用迁移。
已保存凭证取密、命令模板管理、割接和巡检不在当前授权范围。
未提交或推送代码，也未部署替换现有实例；本记录不代表生产发布或全库验收通过。


## 运行实例 404 修复（2026-09-21）

用户报告配置 `2005` 的 collections 地址不存在。复现确认当前 `19191` 前端指向 `59191` 后端，
该后端运行 `.run/satisfaction-verified-server.jar`，其工程模块不含 `ConfigurationCollectionController`。
源码及此前隔离验证中的路由正确，此次属于当前实例未更新，未更改接口前缀。

重新构建当前工作区成功，保留旧包，使用 `.run/dac-route-server.jar` 更新 `59191` 实例；
启动入口记录为 `.run/dac-route-start.ps1`，沿用原数据库、Redis、登录及 local 配置。
在该实例的 `npdms_domain_test` 库用 Flyway 校验 270 项，应用 V325、V326。
未执行无关 V324，后续该库应用 V324 时需显式处理顺序；未 repair 或改写历史。

登录后的记录 `2005` 历史接口返回 `code=0`、空列表；Chromium 在实际 `19191` 页面找到该记录，
打开手工命令弹窗、显示历史并刷新，均通过，无页面异常。未修改该记录或向设备下发命令。
证据为 `.run/dac-route-api-result.json`、`.run/dac-route-browser-result.json`、`.run/dac-route-browser.png`、
`.run/dac-route-flyway.log`、`.run/dac-route-recovery-build.log`。

当前 `59191` 实例仍沿用原 local profile，尚未配置启用 DAC 下发服务。
本次修复恢复业务路由和历史查询，不代表已配置实际设备执行服务。


## 用户授权启用下发服务（2026-09-21）

后续用户要求启用 DAC，并指定使用本机网卡 IP。当前运行配置已统一使用 `10.210.0.11`：
前端 19191、后端 59191、HTTPS DAC 48182、HTTPS 回调及服务公钥网关 59192。
`59191` 已启用 `local,dac`，DAC 使用原生 OAuth2 Resource Server 的 RS256 JWT 验证，未使用免登录 local 模式。
服务身份限制为采集读/执行及 `npdms-1`，任务与回调仍使用既有 HMAC 契约。
加密私钥和 DPAPI 密钥位于被忽略的 `.run/dac-host/`，独立 H2 文件数据库持久保存执行结果。
没有导出明文密钥到源码、命令行或日志，没有修改系统证书信任。

已验证：无凭证 401、其他命名空间 403、受授权查询、手工下发、签名日志回传、日志落库、重复请求去重；
真实浏览器在本机 IP 页面看到“日志待关联”，页面无异常。自检使用临时 Telnet 模拟设备，现已停止；
新增自检配置 30016，原配置 2005 未改写。DAC、回调网关、NPDMS 和前端保持运行。

下载检查暴露当前环境的既有存储限制：主存储 DBFileClient 未实现短时 presignGetUrl，下载请求返回 500。
这不影响下发与回传，但当前环境尚不能下载日志；没有将其写成下载验收通过，也未擅自更换全局存储。
运行端点、启动/令牌续期方法和证据见 `.run/dac-host/README.md`、`runtime.json`、`verification.json`、
`browser-result.json`。服务令牌初次到期为 2026-10-21，需按运行说明续期。


## 本机 IP 页面下发卡住修复与真实设备验证（2026-09-21）

用户在 HTTP 本机 IP 页面确认下发后按钮持续进行中、无执行信息。
检查时当前业务库只有此前模拟设备自检任务，没有该设备的下发记录。
根因是弹窗在 `try/finally` 之外调用 `crypto.randomUUID()`：浏览器在非安全上下文的网卡 IP HTTP 页面
不提供此函数，抛错后既未请求后端，也未恢复 busy。

修复复用仓库既有 `generateUUID()` 兼容工具，并把请求键生成移入异常处理范围；
下发失败给出固定、不含凭证的提示，finally 清空密码并恢复按钮。
执行历史对运行中的任务每 2 秒刷新；终态、关闭弹窗、卸载或请求失败时停止轮询，
使用请求序号防止较早的异步响应覆盖新结果。

已验证 UUID 兼容回归 5 项、变更文件 ESLint/Prettier 通过。
真实 Chromium 在 `http://10.210.0.11:19191` 上确认 `isSecureContext=false` 且原生 UUID 不可用，
修复后仍能成功下发，执行期间自动刷新，完成后显示“日志待关联”；无页面异常、按钮恢复、密码清空。

测试设备由用户提供：`10.253.1.22:5555`，探测确认为 SSH。
首次只读 `display version` 回显 Unknown command，不能将其技术执行终态当作版本查询成功。
用户随后指定 `show run`，从真实页面再次下发；DAC 状态 SUCCEEDED、退出码 0，
实际返回 12514 字符 / 441 行配置，未截断、不是 Unknown command，NPDMS 达到 RESULT_AVAILABLE。
验收专用配置记录 30017，平台任务 `c6bd3e28-f2c6-4a3a-b14d-0d1a5c53ec6f`，归档文件版本 12。
验证没有执行配置写入命令，未修改原配置记录 2005；没有把设备凭证或配置原文附入本文。
设备密码经无回显交互输入，仅在验证进程内使用，未写入测试脚本或应用日志。

证据：`.run/dac-host/device-show-run-result.json`、`.run/dac-host/device-show-run-browser.png`、
`.run/dac-host/uuid-test.log`。此前 DBFileClient 短时下载票据缺口仍存在，本次不将下载标为通过。

## 后续修复与完整手工下发验收（2026-09-21）

用户随后要求修复全部问题，本节记录上述 DAC 手工下发链路的后续结果。
前述下载 500 为当时的历史事实，现已修复并更新宿主机运行实例；没有切换全局文件存储。

- Infra 为数据库存储回执补充短时随机访问票据，复用 PLT 文件权限和项目范围授权后签发。
  Redis 保存票据摘要、文件 ID、原配置和原路径绑定；下载响应禁用缓存和 referrer。
  缺失、伪造、过期票据返回 404，旧公开文件路径及数据库大小写/重音等价路径均无法绕过票据。
- 配置调试弹窗新增日志预览，显示设备原始输出和错误输出；关闭后清空内容。
  采集 DTO 透传外部终态和受控失败原因，页面显示设备不可达、认证失败、命令被拒绝等提示。
- DAC 对 SHELL 的明确 Unknown command 首响应判为 COMMAND_REJECTED，保留原始字节和技术退出码。
  不改写旧任务；旧日志预览仍明确提示命令错误。失败结果不开放“关联日志”操作。

实际验证：

1. 后端针对性测试覆盖存储回执、下载票据、文件权限、采集、日志入库和业务关联，34 项通过；
   最后新增路径等价绕过测试后重新执行 Infra 16 项通过。两个数字有重叠，不累计。
   NPDMS 构建成功；DAC 命令分类、工作器和回调原因码 9 项通过，独立构建成功。
2. 本机 IP 上匿名消费已授权票据返回 200，取回原始 show run 日志；缺失、伪造、过期票据及旧公开路径返回 404。
   授权与项目范围仍在签发入口校验。应用日志没有下载原始票据或服务密钥。
3. 真实 Chromium 从 `http://10.210.0.11:19191` 向 `10.253.1.22:5555` 通过 SSH 下发 `show run`。
   任务 `dc666529-fc35-487a-b901-990cea05233d`，配置 30017，文件版本 16，
   原生 SUCCEEDED，返回 12505 字符 / 441 行。页面自动刷新、密码清空、按钮恢复、预览、下载、
   关联、重复关联及重新打开历史均通过；平台任务达到 COMPLETED，配置调试生命周期未自动完成。
4. 配置 30018 使用本机临时 Telnet 模拟设备验证 show run 返回 Unknown command 和关闭端口不可达，
   分别得到 FAILED/COMMAND_REJECTED、FAILED/UNREACHABLE；实际页面原因与失败日志预览均通过。
   模拟设备已停止，原配置 2005 未修改。真实设备没有执行配置变更命令。
5. 变更前端文件 ESLint/Prettier 和变更差异检查通过，自审完成；没有独立评审或全库验收。
   全量 vue-tsc 仍有既有 8 项错误（配置/安装/联调页面的选择器类型、安装与联调遗留 DeviceArchiveApi 引用），
   本次新增弹窗和 collection API 无类型错误，未修改并行任务的页面改造。

证据位于被忽略的 `.run/dac-host/`：`repair-device-result.json`、`repair-device-browser.png`、
`repair-download-result.json`、`repair-failure-result.json`、`repair-failure-browser-result.json`，
以及 `repair-backend-build.log`、`repair-final-build.log`、`repair-dac-build.log`、`repair-typecheck.log`。
报告与截图不包含设备配置原文、密码、下载票据或服务令牌。当前 DAC、网关、后端和前端保持运行，未提交或推送。

## 多命令时限、服务连接恢复与命令展示（2026-09-21）

用户要求修复长命令/多命令超时、显示实际命令，并明确“重连”指 NPDMS 与 DAC 服务连接；
要求先核对 DAC 实现再确定接入方式。本轮未修改 DAC 设备执行器或其原生 SSE。

源码核对结论：GenericCollectionController 提交返回 202；CollectionSubmissionCoordinator 将已持久化任务
放入异步执行器，并按命名空间、幂等键及无秘密请求指纹去重。CollectionOutputStreamService 将 SSE
单次连接限制为约 30 秒，断开不取消采集；原生 CollectionTaskPanel 按最后 sequence 退避重连，
并独立轮询任务详情。MinaCommandExecutionAdapter 的执行 deadline 则覆盖整个命令组。
用户原任务 `701b22d2-7777-4372-9ef8-8aca5fc31f31` 在约 31 秒后以 EXECUTION_TIMEOUT 失败，
属于整组执行预算耗尽，不是 SSE 重连问题；保留其历史状态和已接收日志。

接入改动：

- PLT 将已保存的业务来源传入 INT，配置调试手工执行采用独立整组预算，默认 600 秒，
  环境变量 `NPDMS_DAC_MANUAL_EXECUTION_TIMEOUT_SECONDS`；其他来源的 30 秒限制不变。
  提交体与 HMAC 任务授权使用同一个实际预算。
- 同步请求连接/读超时后，INT 按原 platformTaskId 重连查询一次。找到原任务则恢复映射；
  重连失败或结果仍未知时保留 RECONCILING，由已有 Quartz 对账查询并重投原签名终态事件。
  不重发设备命令，也不持久保存密码供重试。
- 新增配置范围内的 by-request-key 查询，沿用查询权限、租户及当前项目范围。
  浏览器响应超时后按原请求标识找回任务；首次查询失败也继续轮询，取得终态后停止。
  未确认期间提示避免重复下发，密码清空，后端对账不依赖页面保持打开。
- V327 增加只在首次下发时写入的 command_text 快照，实际发送的规范化命令和展示一致。
  关联日志不更新快照；旧记录保持 NULL，日志预览从原 commandBlocks 读取已有命令。
  命令栏仍禁止填写秘密，连接密码不进入该字段。

验证结果：

1. NPDMS 构建及 44 项针对性测试通过，包含超时恢复成功、不成功进入对账、来源时限隔离、
   授权签名预算一致、秘密清理、命令快照重放保护及精确查询的项目隔离。
2. Docker Compose 的 Flyway 服务仅新增 V327，当前实例从 326 到 327，复跑零迁移；
   无关 V324 仍未执行，旧命令记录无回填。
3. 真正的浏览器对 `10.253.1.22:5555` 经 SSH 顺序执行 show version、show run、show tech：
   任务 `ce953a2a-aabc-48f5-af0e-df05c842114a`，专用配置 30020，日志版本 21。
   三个命令块均 SUCCEEDED，命令执行历时 37.055 秒（超过旧 30 秒限制），总计 100688 字符 / 2670 行，
   未截断。逐行命令展示、自动刷新、日志查看/下载/关联、幂等关联和历史重开通过。
4. 临时本机 Telnet 设备每条命令延迟 12 秒，浏览器模拟丢失实际提交响应，且第一次精确查询也超时：
   配置 30019，任务 `71d1bd7c-28af-48e8-83fe-589b957d89ba` 最终 RESULT_AVAILABLE。
   浏览器仅 POST 一次，经 20 次原请求状态查询自行恢复，命令展示和密码清理通过，无页面异常。
   模拟设备已停止，没有向真实设备发送额外配置命令；原配置 2005 未修改。
5. 变更文件 ESLint/Prettier 与差异检查通过，运行密钥未出现在应用日志。
   全量 vue-tsc 与修复前相同，仍有配置/安装/联调旧页面的 8 项类型错误，新增命令展示及恢复代码无类型错误；
   DAC 与后端健康检查通过，运行实例保持可用。

运行证据：`.run/dac-host/multi-command-build.log`、`migrate-327.log`、`multi-command-device-result.json`、
`multi-command-device-browser.png`、`async-recovery-result.json` 和 `async-recovery-browser.png`。
代码、数据迁移及运行实例已更新，自审完成；没有提交或推送。

## 请求取消后持续刷新修复（2026-09-21）

用户反馈请求取消后页面无限刷新。运行日志定位到 11:32 的取消中断了执行线程，
该线程同时写入文件 H2，导致 ClosedChannelException、数据库关闭及取消持久化失败，
后续查询和回调无法完成。已停止故障 DAC，在备份本地数据库和日志后部署修复并恢复运行；
没有替换数据库、删除历史或通过 SQL 直接更新任务生命周期。

- KeyedCollectionDispatcher 对运行任务改用协作取消，不调用 Future.cancel(true)。
  ExecutionConnectionContext 将取消信号传到 SSH/Telnet 的命令发送和读取循环；
  CollectionWorker 保留已收到输出、释放连接和临时凭证，再由调度器持久化取消终态。
- 页面自动查询使用静默刷新，避免每两秒显示整表加载遮罩。收到终态自动停止；
  未决请求可暂停/继续页面自动刷新，后台对账照常，手动刷新保留暂停选择。
- 故障前任务 186d7593-f16f-4489-a8a5-096f19633e3e 已通过恢复和回调到达 FAILED，
  保留故障事实，未改写为成功或取消。故障期间的另一次请求
  7df91f10-c91c-42a1-98d0-b125efd4bc5b（配置 30020）在 DAC 精确查询返回 404，
  当前仍为 CREATED/RECONCILING/UNKNOWN；无法据此证明执行或取消，不重发命令、不伪造终态。

验证：DAC 独立构建成功，CollectionWorker 5 项、MinaCommandExecutionAdapter 27 项、
KeyedCollectionDispatcher 22 项，共 54 项通过，包含取消不打断持久化线程及后续任务可执行回归。
真实 Chromium 对本机临时 Telnet 模拟设备及用户提供的 SSH 设备分别点击取消，任务
eefd6d96-ae45-4a20-9fc2-cd7f3e93147f（配置 30021，文件版本 29）和
6af9b40e-89b5-4b76-83e5-06961478de31（配置 30022，文件版本 30）均收到 CANCELLED，
取消日志可查看，终态后观察 6 秒均无历史轮询请求，页面无异常。Telnet 模拟器已停止。
DAC 和 NPDMS 健康检查通过，修复后未再出现数据库关闭或取消持久化错误。
变更弹窗 ESLint/Prettier 通过；本轮未重跑已有失败的全量 vue-tsc，也未声明全库验收。
真实浏览器另验证配置 30020 的暂停后无轮询、暂停期间手动刷新不恢复自动刷新、继续后重新查询、
后台刷新无加载遮罩、关闭后停止，以及配置 30022 已取消历史不再显示自动刷新操作，均通过且无页面异常。

证据：`.run/dac-host/cancel-build.log`、`cancel-browser-result.json`、`cancel-ssh-result.json`、
`cancel-refresh-browser-result.json`、对应浏览器截图及 `cancel-recovery.log`。
代码和运行实例已更新，自审完成；没有提交或推送。

## 未确认请求无法结束的后续修复（2026-09-21）

用户反馈上一节遗留记录持续提示避免重复执行且无法结束。上一轮暂停页面查询没有解决此记录的恢复缺口。
本轮为原取消接口补齐“尚未接收”的持久化取消证明，不以普通 404 或 HTTP 成功冒充业务终态。

- DAC V21 增加 `(namespace, idempotency_key)` 唯一提交保护记录，创建执行任务与提交保护在同一事务内。
  取消先持久化则拒绝迟到同键提交；提交先持久化则保留原有执行器取消和回调流程。
  既有执行记录初始化为已接收，不改原任务、输出或批准历史；取消操作可安全重试。
- 未找到请求的取消及证明查询要求命名空间和全项目服务权限；NPDMS 业务入口保留编辑权限及项目范围校验。
  精确查询返回 HTTP 410 与指定证明头后，INT 才报告 CANCELLED_BEFORE_DISPATCH。
  PLT 经状态机校验以及状态、技术阶段、外部映射和结果为空的数据库并发条件转到 CANCELLED；
  已接收或已有结果任务不会被此路径改写，取消证明也可由后台对账恢复。
- 已从真实浏览器对原配置 30020 的任务 `7df91f10-c91c-42a1-98d0-b125efd4bc5b` 请求取消，
  原记录现在为 CANCELLED / CANCELLED_BEFORE_DISPATCH，页面显示“已取消，命令未下发”。
  重复取消通过，终态后观察 6.5 秒无轮询；命令快照、其他历史、记录数量不变，没有生成虚假执行日志。
  没有直接 SQL 更新状态，也没有重新下发原设备命令。

DAC 构建及 49 项针对性测试通过（持久化 24、控制器 3、调度器 22）；
NPDMS 构建及 48 项通过（网关 19、对账 10、临时下发 6、配置集成 13）。
覆盖并发提交/取消单一胜者、取消后迟到提交、重复取消、跨命名空间与受限项目拒绝、
缺少指定证明头的 410 不作终态证明以及已接收/终态保护。权限收口后复跑控制器 3 项通过，不重复累计。
V21 在本地 DAC 仅新增执行一次；NPDMS 本轮无数据库迁移。弹窗 ESLint/Prettier 通过。
服务已部署并保持运行，未提交或推送；未重跑已有失败的全量 vue-tsc 或无关模块验收。

证据：`.run/dac-host/fence-dac-build.log`、`fence-backend-build.log`、`fence-dac-final-build.log`、
`fence-browser-result.json`、`fence-browser.png` 及部署日志。

最终运行复核：DAC 再次启动时 V21 已应用、零新增迁移；原请求继续返回持久化取消证明，原生重复取消返回 202。
原三命令成功任务仍为 SUCCEEDED，100688 字符输出保留。新建临时 Telnet 模拟任务
`7c9cfcf7-2de8-4f82-ba7c-23a915ef5db7`（配置 30023）通过真实页面执行中取消，
签名回调形成 CANCELLED 和文件版本 31，日志可查看、终态后 6 秒无轮询，模拟器已关闭。
当前非终态采集任务为 0；DAC 与后端健康，未出现数据库关闭或取消持久化错误。
补充证据：`fence-provider-result.json`、`fence-running-cancel-result.json`、`fence-running-cancel-browser.png`。
