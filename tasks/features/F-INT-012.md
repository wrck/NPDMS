# F-INT-012 设备连接与采集平台集成

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

## 代码事实按时间逐提交重放回执（2026-09-04）

> 本节仅记录提交代码事实；存在开放Gate时Feature继续保持IN_PROGRESS，代码接收不自动构成Implementation Done。

- 来源提交数：`10`
- 已接收或已确认主干等价路径数：`85`
- 仍需逐路径适配记录数：`9`
- 接收范围：三个来源分支中全部模块的文本代码、测试、构建配置与可安全迁移SQL；无模块级排除。
- 完整提交顺序和逐路径裁决：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。

来源提交：

- `148af0e859d4fc4086052516e7a07527f26c6397`
- `23dff6cdcd3c0d287bd0a26c03447522e5de257c`
- `35c8462d90842aee663a246981b9fe880ab1b2f5`
- `55dc8e4996b3cf424b0ccce642860cfc556c643f`
- `76928bf8593cdbe9354a90686c2a292482f28364`
- `8425805911703c3c75387ba7e9bea75dedd6f076`
- `c6fe303e2ed9a677b815e11bc37ad639a150bb74`
- `cb5098f36239f70db639f07e0564300033138f16`
- `cdfbd71a1722f9696c1dbb8713566de9e88ff97c`
- `d2d1765ffe14233d8041d4b10c871d246c4a9183`

## 代码事实时间序重放检查点（2026-09-04）

> 依据三个来源分支的实际提交代码逐项记录；代码接收不自动构成 Implementation Done。

- 来源分支：`codex/f-cut-001-matrices`, `prereq-parallel-check-kKiAdn`
- 代码事实记录：`104` 个提交-路径组合
- 重放顺序：全局提交时间、来源稳定顺序、分支拓扑顺序。
- 接收范围：全部模块；冲突只保留到具体文件或 hunk，不形成整提交、整模块或整分支拒绝。
- 详细清单：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv` 与稳定化报告。
