# 统一设备连接与命令采集专项

依据本任务需求方确认：统一配置调试、业务联调、巡检、割接所需的设备连接命令采集公共能力；同时补齐命令模板管理和保存凭证，优先复用 DAC。不得实现巡检、割接业务模块。沿用专项直接实施方式，不晋级历史 Feature 状态。

## 需求与边界

| 入口 | 本次接入 | 命令来源 | 结果职责 |
|---|---|---|---|
| 独立采集工作台 | 公共能力完整入口 | 已发布模板 | PLT 保存任务与日志，成功回调完成技术任务 |
| 配置调试 EXE-03 | 复用统一组件与服务，保留旧历史和接口 | 已发布模板或本任务已批准手工命令 | IMP 确认日志关联，原业务完成动作不变 |
| 业务联调 EXE-04 | 统一采集组件与入口适配 | 已发布模板 | IMP 确认结果关联；不以采集成功替代联调通过 |
| 割接 CUT-03/06 | 只定义来源授权、冻结上下文与消费契约 | CUT 发布模板 | 未来由 CUT 解释；不实现工作台、清单、流程和业务判定 |
| 巡检 INS-02/04 | 只定义同一调用契约 | SRV 发布模板 | 未来由 SRV 解释；不实现计划、预检流程、规则、报告或问题闭环 |

公共需求：SSH/Telnet、项目设备范围校验、单次临时秘密与已保存连接选择、显式加密保存、创建人默认使用与用户/设备/协议/模板/有效期精确授权、撤销/停用、模板草稿维护/不可变发布/停用/新版本、命令快照、任务幂等、历史查询、取消、超时后原请求查询、后台对账、日志查看/下载、明确业务消费、重新执行产生新任务并引用原任务。秘密不进入查询、日志、缓存、事件或导出；永久秘密仅由 DAC 加密保管。

对应 Requirement：INT-12、NFR-02、EXE-03、EXE-04；CUT/INS 仅是公共契约消费者，不宣称其业务需求完成。接口沿用 `/api/v1/pms/...` 和租户/项目授权。

## 已有实现逐项复用

| 已有能力 | 核对代码 | 决定 |
|---|---|---|
| 连接验证、保存连接、凭证替换、乐观版本 | DAC SavedConnectionController / SavedConnectionService / JdbcSavedConnectionStore | 复用；增加 NPDMS 安全接入与幂等引用适配 |
| 凭证 AES-GCM、主密钥版本、所有者/命名空间隔离 | DAC JdbcCredentialStore / AesGcmCredentialCipher | 直接复用，不在 NPDMS 新建存密引擎 |
| 脚本制品和不可变版本 | DAC ScriptArtifact / JdbcScriptArtifactRepository | 复用登记与不可变约束；其原生编辑器不是业务模板发布治理 |
| SSH/Telnet、命令块、流式输出、队列、协作取消 | DAC 原执行链路 | 直接复用 |
| 任务状态、签名日志、文件访问、结果消费、重连与对账 | PLT/INT 已接入实现 | 直接复用；增加统一应用层与来源授权端口 |
| 用户/设备/协议/模板/有效期显式授权 | PLT CredentialGrant / DeviceCredentialService | 复用授权表和精确匹配；新增 DAC 保存连接引用，保留旧加密凭证历史 |
| 配置调试手工下发 | ConfigurationCollectionService / ManualCollectionDialog | 抽取公共能力并保留兼容入口；历史数据不可覆盖 |

DAC 的身份/命名空间权限不能代替业务对象和项目授权；公共服务只能调用已注册的来源适配器，未知或尚未接入的 CUT/SRV 来源必须拒绝。浏览器不得指定权威项目、模板内容、凭证引用、完成模式或消费者来绕过服务器冻结值。

## 实施与验证顺序

1. 补齐 DAC 已保存连接与不可变脚本登记的 NPDMS 接入，验证签名绑定及秘密清零。
2. PLT 提供统一模板、连接授权、采集请求与来源端口；IMP 只提供配置/联调授权和结果关联。
3. 公共工作台、模板/连接管理与共用弹窗；旧配置入口转接，联调接入，不修改 CUT/SRV 模块。
4. 前向迁移与菜单/示例模板初始化；验证幂等、授权隔离、版本与历史保护、取消/失败、保存连接复用以及真实浏览器多入口闭环。

## 已交付实现

公共入口：`http://10.210.0.11:19191/pms/device-collection`。当前主机 NPDMS 为 `10.210.0.11:59191`，独立 DAC 为 `https://10.210.0.11:48182`；前后端、DAC 和 TLS 回调网关已按现有隔离开发配置启动。配置调试、业务联调的“命令采集与日志”复用同一组件。

| 层次 | 实现与职责 | 主要文件 |
|---|---|---|
| DAC | 增加幂等资源接入；调用原有验证保存服务、加密凭证库和脚本制品库；签名及执行快照绑定保存连接版本 | `NpdmsResourceController`、`SavedConnectionService`、`JdbcSavedConnectionStore`、`NpdmsCollectionController`、`ConnectionRequestMapper` |
| INT | 保存连接/查询非秘密元数据/登记发布脚本端口；沿用原网关认证、TLS、签名、超时恢复 | `DeviceOpsResourceApi`、`DacDeviceOpsResourceGateway`、`DacDeviceOpsGateway` |
| PLT | 来源 SPI、统一执行应用服务、模板发布、连接授权、任务/结果/日志和授权撤销对账 | `CollectionSourceAdapter`、`CollectionApplicationService`、`CollectionTemplateService`、`CollectionConnectionService`、`CollectionTaskReconciliationService` |
| IMP | 根据原配置/联调权限、项目范围、对象版本和设备绑定授权；旧配置 REST 入口转接；不自动改变业务状态 | `ImplementationCollectionSources`、`ConfigurationCollectionController`、`JointTestCollectionOwnerMapper` |
| UI | 独立工作台、模板维护/新版本/发布/停用、验证保存/授权/撤销/停用；共用下发、历史、取消、重试、日志组件 | `src/components/DeviceCollection/CollectionDialog.vue`、`src/views/pms/platform/device-collection/`、原配置/联调页面 |
| 数据 | V328 新增模板/执行关系，扩充 DAC 引用与注册恢复元数据，复制旧配置关系及命令；补齐菜单权限和草稿示例模板 | `sql/migrations/V328__unified_device_collection.sql` |

业务约束及 API 见 [集成设计 11.6](../design/12-integration-design.md)、[统一采集 API](../design/10-api-design.md) 和 [F-INT-012 当前授权补充](../../specs/features/F-INT-012-device-ops-collection-integration.md)。新增凭证的永久秘密仅存在 DAC 原加密库，PLT 保存外部引用；未替换旧凭证格式或历史。发布开始即冻结模板内容，外部响应丢失后只能以原版本重试登记。保存连接使用稳定注册标识，未确认时可继续原保存请求。

## 实际验证（2026-09-21）

| 检查 | 实际结果与证据 |
|---|---|
| NPDMS 聚焦测试及打包 | 61 个去重用例通过：统一应用 6、授权 2、模板 2、日志权限 1、派发 8、对账 10、旧配置服务 13、DAC 网关 19；增量修复后重跑受影响应用用例并打包、部署。日志 `.run/dac-host/unified-app-final-validation.log`、`unified-recovery-final.log`、`unified-concurrency-fix.log` |
| DAC 聚焦测试及打包 | 11 个用例通过，覆盖已验证连接保存/并发幂等/命名空间隔离、签名引用与版本、取密时版本变化、脚本不可变；显式路径参数修复后重跑 Web 6 个用例并部署。日志 `unified-native-tests.log`、`unified-native-web-final.log` |
| 数据迁移与历史 | Compose Flyway 在既有隔离库从 V327 升级 V328，重复执行为零迁移；旧配置任务关系/命令/请求摘要逐条比对零差异。新 DAC 引用凭证在 PLT 存有本地秘密的记录数为 0。`migrate-328.log`、`unified-data-result.json`。未执行无关 V324，未宣称全库空库验收 |
| 真实浏览器三入口 | Chromium 实际登录、发布模板、验证保存连接、独立执行、联调日志关联、配置手工执行与取消、日志预览；关闭重开后查询真实数据。测试设备为独立 Telnet 模拟器，NPDMS/DAC/MySQL/回调/文件均为真实运行链。`unified-browser-result.json` 通过，浏览器异常为空 |
| 响应丢失与取消收敛 | 浏览器在 POST 已到达服务端后中断响应；页面按原请求查询恢复，只发起一次 POST。取消后保留部分日志，终态连续 6 秒没有新的轮询；配置与联调业务状态仍为 0，不因日志成功自动完成 |
| 同请求并发 | 两个同时提交的真实 HTTP 请求返回相同执行 ID/任务 ID，数据库只有一条任务。修复 MySQL REPEATABLE READ 下等待来源锁后仍使用旧快照导致的“采集任务绑定异常”。`unified-concurrency-result.json` |
| 真实 SSH 设备 | `10.253.1.22:5555`，由界面验证保存连接后，使用已发布模板执行 `show version` / `show run` / `show tech`；任务 `e614627d-d871-41db-be63-de15cc75fd90` 为 `COMPLETED`，日志版本 38，预览 98,300 字符，下载 215,339 字节，终态停止轮询且无浏览器异常。`unified-real-result.json`、`unified-real-device.png`、`unified-real-log.png` |
| 执行中撤销与重试 | 撤销正在运行的保存连接授权，后台对账 25.09 秒后收敛为 `CANCELLED` 并保留日志版本 40；运行任务直接重试被拒绝。重新授权后通过真实浏览器“重新执行”创建新 ID，引用原任务，成功结束；原取消状态及日志不变，设备总共仅执行两次命令。`unified-revoke-retry-result.json`、`unified-revoke-retry.png` |
| 边界和失败路径 | 未注册割接/巡检入口拒绝；无来源授权、已迁移项目历史、非发布模板、撤销/过期/停用/不匹配凭证不能越权执行；秘密清零、保存响应丢失重试、发布冻结和原历史保护由上述聚焦用例覆盖 |
| 前端与自审 | 改动范围 Prettier、ESLint 通过，最后重试竞态修复后 ESLint 和对应真实浏览器复验通过。全量 `vue-tsc` 仍有安装页面既有两处 `DeviceArchiveApi` 未定义（340/341 行），本次采集/配置/联调改动无类型报错；未扩大修改安装页面。差异检查通过 |

最后一次重试验收还修复了组件加载竞争：协议变更的监听器先开始加载，再等待当前重试的模板与连接加载结果，避免重新执行时模板未选中、保存连接列表为空。真实 SSH 首次自动化脚本误匹配旧的完成行，随后以目标设备及本次执行 ID 重新打开页面验证日志和终态；未重复向真实设备下发命令。

## 交付边界

本次完成公共能力和配置调试/业务联调的采集接入；割接和巡检仅可在将来实现各自 `CollectionSourceAdapter` 后接入，未实现其页面、状态机、规则或报告。模板初始化为草稿，发布和设备授权仍由有权限用户显式操作，没有新增角色或自动授予权限。

结论为当前工作树、当前隔离开发实例的局部功能验收与自审；未提交、推送或作生产发布，未进行独立审查，不修改 Feature 的历史主干结论、Implementation Done 或关联业务模块完成状态。前端全量类型检查的上述既有两处错误仍未解决。

## 后续批准与交付：日志自动回传到业务实体

需求方指出采集日志须关联并回传对应业务，并明确选择自动回传，包括失败/取消的部分日志。审计确认上一增量的“关联日志”仅写 PLT 消费事实，未在 IMP 业务实体保存接收记录；本增量补齐此缺口，替代配置/联调须点击后才关联日志的交互，保留前述历史验证事实。

- 公共接收端口 `CollectionBusinessResultReceiver` 供业务 Owner 实现；PLT 仅发送冻结来源、任务/执行、项目/设备、命令、技术结果、文件/结果版本，不跨模块访问 IMP 表。
- IMP 的 `ImplementationCollectionLogService` 按租户+任务+结果版本追加 `imp_collection_log`，并验证实体当前项目/设备。不会覆盖配置日志上传字段、联调证据附件或业务生命周期。失败/超时/取消日志保留真实结果；隔离文件不回传。
- 回调提交后触发接收，原采集 Outbox 和原对账任务负责失败重试；业务接收和成功消费确认在同一事务完成。接收失败回滚，不能提前标记成功回传；浏览器关闭不影响执行。来源锁后使用当前读核对接收记录与任务状态，避免并发回调/重试读取旧快照。
- 新业务查询接口 `/api/v1/pms/implementation/{entry}/{objectId}/collection-logs` 使用原配置/联调查询权限和项目范围。两页详情新增同一个 `BusinessCollectionLogs` 区域，可直接预览、下载；原手动关联接口兼容为“重试回传”，无需点击才显示业务日志。日志保存不可变文件版本，不持久化带令牌的临时下载 URL。
- V329 已在同一隔离库通过 Compose 升级，重复执行零迁移；无需新角色/菜单，不伪造示例文件。既有有效 Outbox 通过接收器补回对应业务实体，保留原任务、日志及历史证据。

验证结果：

| 范围 | 结果 |
|---|---|
| 聚焦测试/打包 | 46 个通过：应用 6、自动回传 7、回调 9、对账 10、Outbox 10、IMP 接收 4。覆盖接收后才确认、接收失败回滚/重试、并发已确认、隔离/租户/来源拒绝、不可变重放及原业务状态保护。`business-return-build-final.log` |
| 自动回传真实链路 | DAC Telnet 模拟设备、真实服务/回调/MySQL/文件：配置 `30030` 自动收到成功、连接中断、取消三条日志（文件版本 46/48/49），联调 `30019` 收到成功日志（版本 47）；未打开浏览器、未调用手动关联即可完成回传，两业务状态均仍为 0。`business-return-browser.log`、`business-return-result.json` |
| 业务实体浏览器验收 | 配置/联调详情直接显示已回传命令、结果、时间；预览真实设备输出及下载通过。首次浏览器验证发现关闭事件的内联多语句经格式化后产生 Vue 运行编译错误，已改为具名处理器；复验无浏览器异常。`business-return-browser-resume.log`、`business-return-configuration.png`、`business-return-joint-test.png` |
| 幂等/隔离 | 重复回传不新增记录，跨业务实体下载被拒绝；最终部署后四个并发重试均返回同一完成任务，业务接收记录仍只有一条，文件版本不变。`business-return-concurrency-result.json` |
| 前端/迁移 | 相关 ESLint、Prettier 通过；全量类型检查仍仅安装页面原两处 `DeviceArchiveApi` 缺失，未扩大修改。V329 一次迁移、重复零迁移。`business-return-types.log`、`migrate-329.log` |

上述日志和截图位于 `.run/dac-host/`。最终修复已重新打包、部署，后端及 DAC 健康。没有实现巡检/割接业务，没有提交或推送，不更改 Feature Done 结论。
