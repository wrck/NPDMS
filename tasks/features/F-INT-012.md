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
