# ADR-0036：COM办事处快照、验收守卫与Feature-forward物理差量

> 状态：`ACCEPTED`<br>
> 日期：2026-08-29<br>
> Requirement：`COM-01@V1`<br>
> 前置批准：`CHG-PRD-2026-08-29-008`

## 背景

F-COM-001首轮Feature Ready复审确认三项阻断：物理差量未锁定且错误声明不影响Gate、ACC验收范围守卫没有Owner Provider、合同管理员数据范围没有权威来源。随后需求方明确订单范围地点是拆分时子项目所属办事处，PRD修订008已独立批准。

## 决策

1. COM的地点事实只保存在`DeliveryScope`主记录：目标项目ID及拆分或分配发生时SYSTEM办事处部门ID、编码、名称、版本均为非空快照。`DeliveryScopeDetail`不保存AST站点、地点文本或办事处第二真值。
2. COM通过PROJ `ProjectOfficeFactApi`获取并冻结项目办事处事实；PROJ负责项目—部门业务关联，SYSTEM仍拥有部门主档。COM不得调用`AssetLocationApi`或从地址、站点、名称、订单字段推断办事处。
3. 数量统一使用`decimal(18,6)`，订单行显式保存`unit_code + unit_scale(0..6)`；确认分配按该精度校验。来源记录保存稳定来源键和版本，订单行另存数量权威状态。
4. 当前范围以`tenant + project + orderLine + effective_to is null`唯一；`allocation_version`保存业务版本。调整必须关闭旧区间并追加新版本，历史和办事处快照不可覆盖。
5. ACC新增Owner事实`AcceptanceScopeBinding`及真实`AcceptanceScopeGuardApi` Provider。COM减量和ACC进入验收统一先锁COM范围、后读写ACC绑定；未知、超时或不可用时减量失败关闭。
6. V70逐字段转换和失败条件以`09-database-design.md`8.2.3为唯一正式规则：办事处编码只作来源证据；订单行`status`固定使用批准导入常量`ENABLED`；范围必填项目/订单快照从精确命中的Project/SalesOrderLine/SalesOrder复制，`allocation_source/status`固定为`LEGACY/ENABLED`；明细`detail_sequence`在冻结输入上按V70主键分组排序生成。任一必填来源、唯一键或输入水位冲突均整体阻断，不伪造业务维度，不长期双写。
7. 合同管理员首次合同可见性仍由`Q-FCOM-001`阻断。本ADR不选择租户全量、组织范围、项目范围或新授权表，也不放行Feature Ready。

## P3-E09差量结论

- 受影响对象：Contract、SalesOrder、SalesOrderLine、DeliveryScope、DeliveryScopeDetail、AcceptanceScopeBinding。
- 处置：`FEATURE_FORWARD_MIGRATION(COM-01)`；精确字段、类型、空值、唯一键、历史区间和转换规则已进入SDS，不修改当前核心DDL或P3-E09全局哈希。
- 当前Gate影响：`P3_E09_FEATURE_FORWARD_DELTA_READY`。整改提交`20f03ba316ca431a55f96aa9c3c97be54d08b4e0`已获独立复审GO；仍只允许回写Feature候选，不批准Flyway或实现。

## 明确排除

- 不修改Yudao基础平台或SYSTEM部门表。
- 不实现ERP第三方连接器，只保留受控写入接口。
- 不批准历史生产迁移、数据切换、AI-MIG-000或Release。
- 不批准合同管理员数据范围、Feature Ready、Technical Plan或产品代码。
