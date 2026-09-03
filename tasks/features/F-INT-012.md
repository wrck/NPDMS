# F-INT-012 设备连接与采集平台集成

> Feature实施状态：`SOURCE_BACKEND_IMPLEMENTATION_EXISTS / CURRENT_MASTER_ADAPTATION_REQUIRED`
> 总体工程阶段：`IMPLEMENTATION_PARTIAL / QUARANTINED_SOURCE`
> Feature Ready Gate：`READY（来源规格） / MASTER_REVALIDATION_REQUIRED`
> Implementation Done Gate：`NOT_READY`
> Requirement：`INT-12@V1=FULL`
> 关联Requirement：`EXE-03`、`EXE-04`、`CUT-03`、`CUT-06`、`INS-02`、`INS-04`、`NFR-02`；不宣称关联Requirement完成
> Feature Spec：`specs/features/F-INT-012-device-ops-collection-integration.md`
> 来源分支：`prereq-parallel-check-kKiAdn`
> 来源实现：`8425805911703c3c75387ba7e9bea75dedd6f076`、`d2d1765ffe14233d8041d4b10c871d246c4a9183`、`cdfbd71a1722f9696c1dbb8713566de9e88ff97c`

## 已实际实现于来源分支

- Device Ops稳定网关API、下发命令和任务快照DTO；
- PLT采集批次、设备任务、状态机、幂等和一次性取密基础；
- 设备凭证、授权、加密保护、Redis令牌存储和临时密码同步派发；
- 回调事实、任务/批次投影、结果消费确认、Outbox语义；
- Mapper、单元测试及来源MySQL并发/回调测试；
- 来源迁移V104～V106。

这些内容证明Feature并非实际未开始，后续不得再以缺少旧Task为由忽略其代码资产。

## 当前master不直接接收代码的原因

- 来源V104同时创建`infra_file_artifact/infra_file_version`，而当前master已由F-PLT-001拥有正式`FileArtifact/FileVersion`模型；直接接收将形成第二文件Owner；
- 来源将Integration改造成新的`pms-module-integration-api`子模块，而当前master仍是单一`pms-module-integration`业务模块，须先重构依赖方向，避免PLT↔INT循环；
- 来源V104～V106已低于master当前V202，必须拆分并重新编号；
- 来源没有完整INT HTTP/multipart接收、Device Ops独立运行端、当前文件平台流式适配、生产装配、真实Redis/HTTP/浏览器闭环；
- 来源规格和实现需要基于master当前PRD、F-PLT-001、F-CUT-003/006及F-INS-001重新验证。

## 后续选择性迁移边界

1. 以当前F-PLT-001为唯一文件Owner，删除旧`infra_file_*`实现和迁移；
2. 冻结PLT调用INT的无循环API模块边界，再迁入Device Ops Gateway DTO；
3. 将PLT凭证、批次、任务和回调表拆分为master新迁移；
4. 迁入采集任务、凭证、回调和消费确认代码，并按当前平台幂等、审计和Outbox合同适配；
5. 单独实现INT HTTP/multipart、验签、Receipt、文件流转及Device Ops联调；
6. 完成真实MySQL、Redis、HTTP、并发、故障恢复和浏览器证据后再申请Implementation Done。

## 当前裁决

`SOURCE_IMPLEMENTATION_RECOGNIZED / NO_DIRECT_CODE_MERGE`。本次把规格与实际来源状态登记进master，确保代码资产可追溯；旧架构代码在完成Owner和模块适配前不得整支合入，也不得被误记为`NOT_STARTED`。
