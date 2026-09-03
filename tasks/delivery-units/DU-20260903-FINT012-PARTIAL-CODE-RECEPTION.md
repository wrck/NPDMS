# DU-20260903-FINT012-PARTIAL-CODE-RECEPTION F-INT-012已实现代码接收

> DU状态：`CLAIMED`
> DU类型：`FEATURE_PARTIAL_INTEGRATION`
> Feature协调：`F-INT-012=FEATURE_EXCLUSIVE`
> Requirement：`INT-12@V1=FULL`
> Owner：`Codex F-INT-012已实现代码接收会话`
> 目标分支：`codex/f-int-012-partial-reception-20260903`
> 认领基线：`master@bdcb0d396cf06f73f67cb5c483f607d58026e903`
> 来源分支：`prereq-parallel-check-kKiAdn`
> 来源提交：`8425805911703c3c75387ba7e9bea75dedd6f076`、`d2d1765ffe14233d8041d4b10c871d246c4a9183`、`cdfbd71a1722f9696c1dbb8713566de9e88ff97c`

## 目标

按“已完成代码可以进入master、Feature保持进行中”的口径，选择性接收F-INT-012中已经实现且不依赖未完成外部联调的独立代码切片。代码接收不等于Feature Implementation Done。

## 本DU接收范围

- INT Owner的`DeviceOpsGatewayApi`稳定合同及DTO独立API模块；
- PLT Owner的`DeviceCredential`、`CredentialGrant`、凭证加密保护；
- 一次性取密令牌与Redis原子消费实现；
- `CollectionBatch`、`CollectionTask`、业务状态机、幂等创建；
- Platform回调事实、任务/批次投影、结果事件与消费确认；
- 对应Controller、Mapper/XML、单元、合同和真实MySQL候选测试；
- 基于当前master迁移序列的新前向迁移；
- Feature Task状态统一登记为`IN_PROGRESS`并记录已集成切片。

## 明确排除

- 来源分支的`infra_file_artifact`、`infra_file_version`及Yudao Infra文件客户端修改；当前F-PLT-001继续是唯一文件Owner；
- INT签名HTTP/multipart回调接收、Receipt持久化、Provider配置、真实DispatchAttempt和对账Job；
- Device Ops生产Gateway实现、真实外部系统联调和浏览器闭环；
- 来源V104～V106旧Flyway编号；
- 任何Feature Done、SIT、UAT或Release状态转记。

## 完成判定

- [ ] 新API模块和PLT代码切片进入集成分支；
- [ ] 新迁移不创建第二文件Owner，编号位于master当前末尾之后；
- [ ] 现有master较新平台能力不被来源分支覆盖；
- [ ] Feature Task为`IN_PROGRESS`并准确列出已完成和剩余范围；
- [ ] 完成适用构建/测试或明确记录当前环境无法重跑的证据边界；
- [ ] PR合入master后将DU更新为`INTEGRATED_PARTIAL`。
