# DU-20260903-FINT012-PARTIAL-CODE-RECEPTION F-INT-012已实现代码接收

> DU状态：`INTEGRATED_PARTIAL`
> DU类型：`FEATURE_PARTIAL_INTEGRATION`
> Feature协调：`F-INT-012=FEATURE_EXCLUSIVE`
> Requirement：`INT-12@V1=FULL`
> Owner：`Codex F-INT-012已实现代码接收会话`
> 分支：`codex/f-int-012-partial-reception-20260903`
> 认领基线：`master@bdcb0d396cf06f73f67cb5c483f607d58026e903`
> 权威认领提交：`886bc7ce7b47c6eefc82cc4b45fe5289adcaf83a`
> 来源分支：`prereq-parallel-check-kKiAdn`
> 来源提交：`8425805911703c3c75387ba7e9bea75dedd6f076`、`d2d1765ffe14233d8041d4b10c871d246c4a9183`、`cdfbd71a1722f9696c1dbb8713566de9e88ff97c`
> master集成：`PR #4 / 2df41a187268332ea38f01ac90ea5f8302df3f34`

## 目标

按“已完成代码可以进入master、Feature保持进行中”的口径，选择性接收F-INT-012中已经实现且不依赖未完成外部联调的独立代码切片。代码接收不等于Feature Implementation Done。

## 已接收至master

- 独立`pms-module-integration-api`及`DeviceOpsGatewayApi`稳定合同；
- PLT公开采集API、DTO、凭证、授权、加密保护和Redis一次性令牌；
- `CollectionBatch`、`CollectionTask`、状态机、幂等创建和临时秘密同步派发候选；
- Platform回调事实、顺序校验、任务/批次投影、结果事件和消费确认；
- 设备凭证REST、Mapper/XML、Controller合同测试、服务单元测试、Redis测试和来源真实MySQL候选测试；
- Maven reactor、PLT依赖及新迁移`V203__fint012_collection_platform_foundation.sql`；
- F-INT-012、F-IMP-001、F-IMP-002主状态统一为`IN_PROGRESS`，细分完成边界保留在实施子状态。

## 选择性适配

- 当前F-PLT-001继续是唯一文件Owner；未接收来源`infra_file_artifact`、`infra_file_version`和Yudao Infra文件客户端修改；
- 来源V104～V106没有进入master，PLT最终表按当前迁移序列合并重排为V203；
- 未创建来源分支的`int_device_ops_*`表，因为INT签名回调、Receipt和技术对账尚未完成；
- PLT不依赖INT业务模块，稳定Gateway合同置于独立API模块；
- 设备凭证新入口按仓库规则调整为`/api/v1/pms/device-credentials`；
- `TemporaryCollectionDispatchService`仅在存在生产`DeviceOpsGatewayApi` Bean时激活，当前不伪造fallback。

## 明确排除

- INT签名HTTP/multipart回调接收、Receipt持久化、Provider配置、真实DispatchAttempt和对账Job；
- F-PLT-001流式文件写入及扫描隔离生产适配；
- Device Ops生产Gateway实现、真实外部系统联调和浏览器闭环；
- EXE、CUT、INS和SRV消费方完整业务闭环；
- 任何Feature Done、SIT、UAT或Release状态转记。

## 完成判定

- [x] 新API模块和PLT代码切片进入集成分支；
- [x] 新迁移不创建第二文件Owner，编号位于master当前V202之后；
- [x] 现有master较新平台能力未被来源分支覆盖；
- [x] Feature Task为`IN_PROGRESS`并准确列出已完成和剩余范围；
- [x] 依赖方向、目录差量、API路径和迁移对象完成静态复核；
- [x] 来源单元、Redis、合同和真实MySQL候选测试随代码接收；
- [x] PR #4已合入master并登记合入提交；
- [ ] 当前执行环境无法拉取仓库，未重新运行Maven、MySQL、Redis、HTTP或浏览器测试；最终master复验继续作为Feature Done前置。

## 当前裁决

`INTEGRATED_PARTIAL / FEATURE_IN_PROGRESS`。已实现代码已经进入master并可继续迭代；F-INT-012保持`IN_PROGRESS`，未完成生产边缘与端到端闭环继续实施。
