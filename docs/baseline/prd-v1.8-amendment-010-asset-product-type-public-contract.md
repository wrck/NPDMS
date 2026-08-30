# PRD V1.8批准修订010：AST设备产品类型受控副本与公开查询契约

> 修订编号：`CHG-PRD-2026-08-30-010`<br>
> 批准日期：2026-08-30<br>
> 状态：`APPROVED`<br>
> 前置基线：`CHG-PRD-2026-08-30-009`<br>
> 当前快照SHA-256：`436CC05DFCC2F80AF3940FA280049B02ED3D0C0DCD2DD92F8F39F5757EB69EF5`<br>
> 关联裁决：`Q-FINS001-002`方案A

## 1. 权威来源

- 前置正式底稿：PRD V1.8修订009。
- 既有业务语义：EQP-01以设备序列号聚合MES来源的产品编码、产品名称、设备型号等设备事实；INS-03、INS-09要求按设备产品类型筛选适用巡检规则。
- 既有Owner边界：CRM/MES拥有产品和设备来源事实，AST保存平台业务所需的受控副本，Inspection不得维护第二套产品类型主数据或直读AST业务表。
- 工程裁决：采用`Q-FINS001-002`方案A，由AST独立Feature Spec与当前Task冻结并交付产品类型公开查询契约。

## 2. 批准结论

1. EQP-01的设备数据底座包含设备当前可解析的产品类型受控副本。每个产品类型至少提供稳定编码、显示名称、存在事实、启用/停用事实、来源系统、来源键、来源版本和最近成功同步时间。
2. 产品类型稳定编码和显示名称只能来自已核验的CRM/MES来源映射或保留来源证据的受控导入；不得使用`conpType`、旧字典、自由文本或猜测值替代。
3. AST提供模块内公开查询契约：按产品类型编码批量查询存在/停用事实；按授权设备查询当前产品类型编码和显示名称。查询必须执行租户、设备和数据范围校验，未知编码不返回猜测名称，无权设备不泄露存在性。
4. Inspection只保存规则引用的稳定产品类型编码及发布时显示名称快照。规则草稿可在AST暂不可用时继续编辑，但发布和工程师按设备选择规则必须重新校验AST当前事实并失败关闭。
5. 产品类型停用后不得供新规则发布或新任务选择；历史规则revision及历史任务继续按已冻结编码和名称快照解释，不反向覆盖。
6. 产品类型来源同步、协议适配、网络连接、调度、游标、重试、补偿和对账仍由EQP-04或后续独立连接器Feature承载。本修订和F-AST-002只冻结并交付AST本地受控副本与公开查询闭环，不实现CRM/MES连接器。
7. 不新增Requirement、业务角色、审批节点、生命周期状态或产品类型示例种子，不改变正式需求数量、优先级、目标版本和领域Owner。

## 3. Requirement与影响边界

- 直接细化：`EQP-01@V1`的设备数据底座合法子闭环、`INS-03@V2`和`INS-09@V2`的产品类型适用范围消费契约。
- 关联但不宣称覆盖：`EQP-04@V2`的MES同步运行闭环。
- 独立Feature：`F-AST-002`覆盖EQP-01产品类型受控副本与公开查询子闭环；`F-INS-001`继续覆盖Inspection规则配置与消费验收。
- 不影响：设备连接与采集平台、设备凭证、INT-12任务下发、巡检九状态流程、报告和问题闭环。

## 4. 验收边界

- 已核验来源或受控导入形成产品类型受控副本后，AST可按编码批量返回稳定编码、显示名称、存在/停用事实和来源版本。
- 有权调用方可按授权设备取得当前产品类型；跨租户、无设备范围、未知映射和停用事实按契约拒绝或明确返回不可用，不泄露无权设备存在性。
- Inspection发布时遇到未知、停用、来源证据缺失或AST契约不可用的产品类型必须失败关闭；旧发布revision继续有效。
- 不以替身、手工自由值、旧字典、`conpType`或直接数据库读取作为F-AST-002交付证据。
- 本修订完成不代表EQP-04连接器、外部联调、Deployment、SIT、UAT或Release完成。

## 5. 基线关系与下游落位

本修订合并至`需求/PRD-项目实施交付管理平台.md`，并冻结为`docs/baseline/prd-v1.8.md`。两份文件必须保持一致。

下游正式落位：

- 权限与失败关闭：`docs/design/07-authorization-design.md`。
- AST受控副本、设备当前产品类型引用及Inspection快照物理边界：`docs/design/09-database-design.md`。
- EQP-01、EQP-04、INS-03、INS-09的API、数据、授权、状态与证据追溯：`docs/traceability/phase2-contract-map.md`。
- F-AST-002与Inspection消费者契约、负向和边界验证：`docs/design/20-test-design.md`。
- 模块、领域模型与API主契约：`docs/design/04-module-design.md`、`docs/design/08-data-model.md`、`docs/design/10-api-design.md`。
- Feature与实施状态来源：`specs/features/F-AST-002-device-product-type-copy-and-public-query.md`、`tasks/features/F-AST-002.md`；Inspection消费边界见`specs/features/F-INS-001-inspection-rule-version-and-field-configuration-foundation.md`。
