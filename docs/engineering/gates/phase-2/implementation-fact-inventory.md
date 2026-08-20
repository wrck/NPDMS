# Phase 2 实现事实盘点

> 文档类型：Phase 2 门禁输入证据
> 盘点基线：PRD V1.8、SDS Phase 1 `REVALIDATION_REQUIRED`
> 实现仓库：`E:\AICoding\Projects\NPDMS`
> 实现提交：`856d05264ab4a4fb69b94896c172e4a1c29aae02`
> 状态：`REVALIDATION_REQUIRED`

## 1. 盘点目的与判定规则

本盘点只回答“当前实现中已经存在什么”，不以旧代码、旧表或过时 `specs` 改写业务语义。Phase 2 正式契约仍按 `PRD > Engineering Constitution > SDS > Feature Spec > Implementation Plan > Task > Code` 判定。

| 分类 | 含义 | Phase 2 处理 |
|---|---|---|
| `ALIGNED` | 业务归属和核心语义与 PRD/SDS 一致 | 可进入详细约束复核，不代表可原样发布 |
| `REUSABLE_WITH_CHANGE` | 结构可复用，但边界、状态、权限或字段语义需要调整 | 在 08～16 分册明确目标模型和兼容策略 |
| `CONFLICT_REQUIRES_FORWARD_MIGRATION` | 与已确认范围或业务规则冲突 | 禁止修改已执行迁移；通过新迁移、兼容读取和下线策略纠正 |
| `UNMAPPED` | 当前实现未发现相应持久化或契约 | 按正式 SDS 新建设计，不以“当前没有”为删除需求依据 |

## 2. 可复现事实

| 事实 | 结果 | 证据位置 |
|---|---:|---|
| `pms_*` 业务表 | 69 个唯一表名 | `E:\AICoding\Projects\NPDMS\sql\migrations\` |
| PMS Controller | 59 个 | 实现仓库各 `pms-module-*` 模块 |
| PMS Data Object | 65 个 | 实现仓库各 `pms-module-*` 模块 |
| 项目层级 | 已有 `parent_id/root_id/path/depth` | `V7__pms_project_hierarchy.sql` |
| 任务层级与依赖 | 层级字段和依赖关系分表保存 | `V8__pms_task_plan_risk.sql` |
| 实现运行边界 | 后端、前端在宿主机运行；MySQL、Redis 使用 Docker | 实现仓库 `compose.yaml`、`docs/upstream-sources.md` |

数量只用于确认现状规模；表、Controller 或 DO 的存在不等于 Requirement 已满足。

## 3. 按业务域的现状分类

| Phase 1 Context / Owner | 现有实现事实 | 分类 | Phase 2 处理重点 |
|---|---|---|---|
| Project Delivery / PROJ | 项目、项目树、任务树、依赖、阶段、模板、组合、计划变更和治理动作已有表 | `REUSABLE_WITH_CHANGE` | 验证任意层级查询、项目树后代权限、任务依赖与层级正交、设备当前归属统计和状态迁移守卫 |
| Preparation / PRE | 工勘、需求、资源就绪、交底等对象混在 engineering 模块 | `REUSABLE_WITH_CHANGE` | 按 Preparation 聚合重新界定 Owner；模块共存可以保留，禁止跨 Context 直连 Repository |
| Solution / SOL | 方案、方案来源、模板已有表 | `REUSABLE_WITH_CHANGE` | 区分方案版本、评审结论和不可变发布版本；附件统一引用文件资产 |
| Implementation Execution / IMP | 到货、安装、配置、联调、风险、交付件已有部分表 | `REUSABLE_WITH_CHANGE` | 对齐到货、安装、配置、联调、风险、IMP-01质量检查和交付件；IMP-02不进入当前对象/表/API |
| Acceptance & Closure / ACC | 验收、交付清单、归档、完工证明、闭环已有表 | `REUSABLE_WITH_CHANGE` | 对齐 Acceptance、DeliveryArtifact、ProjectClosure、ServiceHandover；移除“续保”业务语义 |
| Cutover / CUT | 计划、任务、风险、执行、观察已有表 | `REUSABLE_WITH_CHANGE` | 对齐P1～P6评估、审批、方案和P6闭环快照；逐步骤状态与稳定观察不进入当前业务聚合 |
| Work Order & Time / SRV | 通用服务任务、规则、执行等对象已有表 | `CONFLICT_REQUIRES_FORWARD_MIGRATION` | 不进入当前V1/V2对象、表、API或文件入口；历史事实仅按AI-MIG-000受控保存来源证据 |
| Inspection / SRV | 巡检任务、规则、报告、问题已有表 | `REUSABLE_WITH_CHANGE` | 接入 Device Access & Collection 的凭证/临时明文入口、任务下发和回调，不重建采集引擎 |
| Service Operations / SRV | 存在独立维保表 | `CONFLICT_REQUIRES_FORWARD_MIGRATION` | 独立维保、续保空间和续保报表不进入目标模型；客观维保信息归入设备档案 `MaintenanceFact` |
| Customer / CUS | 客户、联系人、服务等级已有表 | `REUSABLE_WITH_CHANGE` | CRM 是客户主数据 Owner；平台保存必要同步副本，避免运行期全部远程查询 |
| Asset / AST | 设备、配置日志、版本已有表 | `REUSABLE_WITH_CHANGE` | 补齐设备当前归属与归属历史、维保基本事实、RMA/替换事实、外部同步快照 |
| Commerce / COM | 未发现合同、销售订单、订单行、交付范围和履约快照表 | `UNMAPPED` | 以 CRM/外部商务事实为权威，平台保存履约所需同步副本并支持对账 |
| Resource / RES | 外采、物料、外协申请已有表，但边界混在 engineering 模块 | `REUSABLE_WITH_CHANGE` | 备件业务由外部系统承接；仅保留供应商、转包与付款门禁所需事实和集成引用 |
| Analytics / ANA | 有分析模块和部分组合结构，未发现正式指标快照模型 | `UNMAPPED` | 建立只读指标快照、口径版本和来源水位，不从业务表临时拼接不可追溯指标 |
| Platform / PLT | 基础平台能力存在；未发现统一文件引用、Outbox/Inbox 和完整采集任务模型 | `UNMAPPED` | 复用基础平台身份、权限、工作流和文件基础能力，补充业务级引用、审计和可靠消息契约 |
| Device Access & Collection / PLT | 外部平台已可用，当前业务库未发现凭证授权、任务下发、回调记录完整模型 | `UNMAPPED` | 作为独立 Context/子应用纳入；平台持有凭证、授权、任务和回调，采集引擎仍由现有平台执行 |
| Knowledge Reference / KNO | 历史实现存在本地技术公告发布、停用和处置 | `CONFLICT_REQUIRES_FORWARD_MIGRATION` | V2 只做 ITR 基础同步、查询和业务引用；本地治理增强属于 V3，不得提前启用 |

## 4. 已确认的实质性漂移

### 4.1 独立维保与续保语义超出范围

- `V14__pms_service_tables.sql` 的 `pms_srv_maintenance` 包含“已续保”和人工覆盖。
- `V17__pms_acceptance_tables.sql` 的 `pms_acc_maintenance_transition` 包含续保年限、续保结束日期和“已续保”状态。
- 已确认范围排除维保档案、续保空间管理、续保率报表和过保空间报表，但保留设备档案中的客观维保基本信息。

处理结论：旧表保留为兼容事实，不修改 V14/V17；目标模型将设备维保起止日期、服务等级、来源和客观计算状态归入 AST 的 `MaintenanceFact`，将验收后的持续服务交接归入 ACC 的 `ServiceHandover`。续保动作和报表不进入 V1/V2 API、菜单或状态机。

### 4.2 技术公告被实现为本地主数据治理

- `V30__pms_eng_risk_announcement_authorization.sql` 的 `pms_eng_announcement` 支持本地创建、修改、发布和停用。
- `pms_eng_announcement_check` 支持本地处置与忽略。
- 当前正式范围是 INT-04 的 ITR 基础同步、查询和引用；KNO 治理增强为 V3。

处理结论：V1/V2 目标模型只保存外部公告标识、同步字段、来源版本/水位和业务引用快照。历史本地发布/停用能力不得作为 V1/V2 功能入口；如保留数据，应以来源类型和兼容状态隔离。

### 4.3 授权密钥字段需要安全重新定界

`pms_eng_authorization.license_key` 是无明确密文、掩码和用途边界的字符串字段。它可能表示设备授权材料，也可能表示软件许可，不能直接等同于 DeviceCredential，更不能原样复用为设备连接密码。

处理结论：Phase 2 将业务授权材料与设备连接凭证分模。设备连接凭证必须按凭证范围、创建人默认私有、授权五元组、密文存储、运行时受控解密和审计设计；临时用户名/密码允许单次明文传入外部采集平台但不落库，用户可明确选择“保存为凭证”。软件许可材料如仍有正式 Requirement，再以文件/密文引用建模；否则标记为未映射历史实现。

### 4.4 状态字典不能替代状态机

V44 中存在大量可配置状态字典，且部分旧状态与当前 PRD 不一致。已确认原则允许状态显示值初始化并可扩展，但生命周期迁移、终态、守卫和权限不能仅靠字典配置。

处理结论：字典保存可扩展业务类型和展示含义；正式状态代码、迁移命令、守卫和终态由状态机契约控制。禁止通用“修改状态”接口直接写生命周期字段。

## 5. 当前缺口与非阻塞结论

当前未发现足够证据证明下列目标契约已经存在：

- Commerce 的合同、订单、订单行、交付范围、履约快照和对账记录；
- Device Access & Collection 的凭证、授权、采集任务、下发尝试、回调和回调幂等；
- Implementation Execution 的质量检查、安全检查、整改、复核和豁免/阻断完整事实；
- 设备在项目树中的当前唯一归属、归属历史和跨层级统计投影；
- 统一 `FileArtifact/FileReference/FileVersion` 业务引用；
- 跨 Context 的 Outbox/Inbox、集成对账和补偿记录；
- 可追溯的指标快照与口径版本。

这些是 Phase 2 的设计输入，不是 `BLOCKED_BY_SPEC`。当前没有发现必须新增业务角色、审批节点、阈值或状态才能继续的数据设计阻塞。

## 6. 对 08～16 分册的约束

1. 每个事实只允许一个写 Owner；其他 Context 通过 ID 引用、不可变快照或正式契约消费。
2. 所有旧迁移均视为已执行，禁止原地修改；差异使用新版本前向迁移和可回滚的兼容发布步骤。
3. 主数据采用“权威系统 + 必要本地同步副本”，不得把所有读取降级为运行期接口调用。
4. 项目和任务均不得固定层级；路径、闭包或投影方案必须支持后代范围查询和异步重建。
5. 设备同一时点只有一个当前归属项目；同时保存归属历史，并通过项目祖先链汇总到任意上级项目。
6. 外部 HTTP 成功、消息投递成功或通知送达均不等于业务完成；必须以回调、对账或业务状态确认闭环。
7. 敏感信息不得明文持久化或出现在日志、事件、审计详情和错误响应中。

## 7. P2-01 结论

`PASS`：实现漂移已按事实分类，已明确三类前向纠正事项和七类主要缺口。可以进入 08 数据模型与 09 数据库设计；上述分类将在正式分册中逐项转化为 Owner、表、约束、兼容和迁移契约。
