# SDS Phase 1：领域 Owner 重确认记录

> 状态：`REVALIDATION_REQUIRED`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求
> 文档 Owner：需求方；本文件仅登记业务事实 Owner 映射，不替代实施责任人名册
> 依据：PRD V1.8 第四至第十三章、附录 A.1、核心业务对象和数据关联规则
> 领域规格：`specs/001-project-delivery-platform/domains/`由PRD V1.8生成；PRD仍是最高业务语义来源

## 1. Owner 判定规则

1. 需求的唯一 Owner 由其主要业务事实和聚合根决定，而不是由接口名称决定。
2. 外部系统集成需求归属于实际消费/拥有业务事实的领域；集成适配器只负责协议和同步证据。
3. 公共身份、授权、文件、待办、通知、变更和非功能约束归 PLT；设备业务事实归 AST，技术公告业务事实归 KNO。
4. 一个正式需求只保留一个 Owner；跨域协作在依赖、事件和引用关系中表达。
5. 一个领域可以包含多个 Bounded Context；Context 拆分用于澄清职责，不改变正式需求 Owner、领域编码或规格分册。

## 2. PRD-derived Owner 映射

| Owner | 正式需求编号 | 判定依据 |
|---|---|---|
| PROJ | PM-01～PM-11、PROJ-12、INT-01 | 项目、项目树、执行编排节点、工作绑定、完成规则和项目来源映射由项目治理拥有；绑定目标业务事实仍归目标领域 |
| SOL | PRE-01～PRE-05、PLN-01～PLN-04、SCH-01～SCH-05、SOL-01 | 工前、计划、方案及准备表单的业务事实 |
| IMP | EXE-01～EXE-06、IMP-01 | 到货签收、安装、配置采集、联调、阶段质量检查，以及实施阶段产生的交付件/实施证据上传；IMP-02不属于当前正式范围 |
| ACC | ACC-01～ACC-04、ACC-06、CLO-01～CLO-02 | 验收、满意度收集、交付件齐套校验、审核、统一归档、闭环和静态服务交接；ACC-05仅为V3演进方向 |
| CUT | CUT-01～CUT-10 | CUT-01核心任务及其问卷评估、P3动态清单与业务结果判定、调研、方案、分级审批、P6闭环和配置；不拥有WO-06工单语义，INT-12采集任务和原始回调由PLT拥有 |
| SRV | INS-01～INS-09、SRV-01 | 巡检任务、规则、报告、问题及设备服务状态；内部 Context 分为 Inspection、Service Operations |
| CUS | CUS-01～CUS-04、INT-03 | 客户、联系人和客户关系；CRM客户字段由外部Owner提供，平台保存必要同步副本 |
| AST | EQP-01～EQP-07、AST-01～AST-02、INT-02、INT-06 | 设备身份、档案、版本、RMA、维保基本信息和设备相关外部同步；平台保存必要同步副本 |
| COM | COM-01 | ERP合同、销售订单、订单行同步副本和项目交付范围分配；COM-02不属于当前正式范围 |
| RES | RES-01、SUB-01～SUB-05、INT-07 | 服务商、转包和付款门禁；财务系统拥有账务事实 |
| ANA | RPT-02、ANA-01 | 项目状态、组合视图和经批准的只读经营分析 |
| PLT | PLT-01～PLT-02、AUT-01～AUT-02、CHG-01、NFR-01～NFR-03、INT-05、INT-09、INT-10、INT-12 | 平台公共能力、身份、通知、凭证授权和采集任务编排；INT-12 的实现 Context 为 Device Access & Collection |
| KNO | INT-04 | 技术公告引用、版本影响和知识事实同步 |

## 3. 结果

- 覆盖正式需求：100/100。
- Owner 数量：13 个。
- 领域之间不存在同一正式需求的双 Owner。
- `INT-12` 的 Owner 是 PLT 公共采集编排；设备连接和原始采集执行仍由现有采集平台负责。
- `Device Access & Collection` 是正式 Bounded Context；V1 由现有采集平台子应用承载执行能力，后续可演进为内部模块。该 Context 不拥有 IMP/CUT/INS 业务结论、设备主档或外部采集引擎内部数据。
- SRV 内部 Context 映射为：INS-01～INS-09 → `Inspection`；SRV-01 → `Service Operations`。V1.7删除`Work Order & Time`当前Context；历史工单/工时只读迁移资料不形成正式需求Owner。
- IMP 上传实施过程中产生的来源证据或阶段交付件；ACC 不重复上传，而是对项目交付件进行汇总、齐套校验、审核和归档。
- `COM-01` 和 `INT-04` 明确属于正式需求；COM-02、IMP-02已退出当前正式需求池，ACC-05仅保留为V3追溯。

## 4. 后续确认

本记录已由需求方在本轮确认，作为 Phase 1 Owner 映射签署依据；确认来源不替代后续实现工作包中的责任人名册登记。

| 确认项 | 结果 | 证据 |
|---|---|---|
| 13 个领域 Owner 映射 | `REVALIDATION_REQUIRED` | V1.8差量修正后待独立复审；当前文件第 2 节 |
| 需求唯一 Owner | `PASS` | 100 项正式需求唯一归属，追溯矩阵校验通过 |
| Context 拆分不改变领域编码 | `PASS` | 本文件第 1 节第 5 条及 Context 整改复审 |
