# F-IMP-002 到货签收与里程碑事实 Feature Spec

> 文档状态：`DRAFT / READY_REVIEW_CANDIDATE`
> Feature Ready：`REVIEW_REQUIRED`
> Requirement：`EXE-01（V1/P0）`
> Requirement切片覆盖：`EXE-01@V1=FULL`
> Owner Context：`IMP（现场实施）`
> 消费Feature：`F-IMP-003`、`F-IMP-001`
> 外部依赖：COM `DeliveryScopeApi`、AST `T-FIMP001-AST-01`、PROJ `ProjectScopeApi`、PLT `FileArtifactApi`、ACC `ImplementationEvidencePublished`消费者

## 1. 业务目标与范围

以项目当前有效DeliveryScope为应到订单/型号/数量范围，以其中明确SN经AST解析后的稳定设备事实为序列化范围，按到货批次保存部分签收、补签、差异、拒收、具体设备/数量豁免和不可覆盖证据。只有全部应到范围已签收或存在仍有效的明确豁免时，形成`ACCEPTED`权威里程碑事实并供EXE-02和EXE-06消费。

本Feature包含到货批次草稿、授权现场成员录入、项目经理最终确认、差异/拒收/补签/豁免链、证据版本、ACC-04引用发布、Owner事实查询与锁定重验、旧记录前向迁移。它不实现安装、割接、ACC归档Owner、COM订单范围维护、AST设备归属写入或外部连接器。

## 2. Owner与应到范围

### 2.1 Owner边界

- COM唯一拥有`DeliveryScope`分配事实。`DeliveryScopeApi.getAssignedScope(projectId, expectedScopeVersion)`返回当前有效订单行、分配数量、单位、产品/型号维度、明确SN集合及`scopeVersion`；`PENDING_AUTHORITY`、取消、退货或已释放数量不进入应到范围。
- AST通过`T-FIMP001-AST-01`的`DeviceScopeFactApi`将明确SN解析为稳定`deviceId/sn/currentProjectId/projectAssignmentVersion`。AST只证明身份和当前直接归属，不证明应到数量或签收完成。
- IMP在每个批次和里程碑事实中保存`ArrivalExpectedScopeSnapshot`，冻结COM `scopeVersion`、订单行/维度/数量与AST设备归属版本向量。`scopeWatermark`是上述结构化版本向量，不用哈希替代业务字段。
- DeliveryScope数量、SN集合或AST归属变化后，旧快照重验为`STALE`；IMP重新计算未满足项并追加事实版本，不覆盖历史。
- IMP不得直接读COM/AST表，也不得把发货、装箱、设备当前归属或项目参与关系单独解释为已到货。

### 2.2 数量与设备口径

- 有明确SN的范围按稳定设备逐台签收；同一设备在同一有效范围只能被一个当前已确认明细覆盖。
- 无明确SN的范围按`orderLineId + product/model + unit`和数量签收；累计确认数量不得超过当前未签数量，单位精度遵循COM范围。
- 同一项目允许多批、部分签收和后续补签。部分签收只向EXE-02开放已确认设备，不把整个项目误报为ACCEPTED。
- 空应到范围、范围Owner不可用、版本过期、重复SN、跨租户、非本项目设备、超量或无法识别的订单行均阻止最终确认；草稿和失败审计可保留。

## 3. 状态、命令与业务规则

### 3.1 批次和明细

- 批次状态沿用正式SDS：`DRAFT/PARTIALLY_ACCEPTED/DIFFERENCE_PENDING/ACCEPTED/CONFIRMED`；差异或拒收明细未闭环时不得形成项目ACCEPTED里程碑。
- 明细状态固定为`NOT_ARRIVED/ACCEPTED/DIFFERENCE_PENDING/REJECTED`。批次必须保存物流单号、签收人快照、签收时间、设备或订单型号数量明细及至少一个有效证据revision。
- 授权现场成员只能创建和编辑本人未最终提交的批次及证据；项目经理在本人负责且`ProjectScopeApi.ACTION_EDIT`允许的项目内最终确认、提交差异处置和补签。客户端不能直接写生命周期状态。
- 最终确认使用`Idempotency-Key`和聚合`If-Match`；同键同规范化命令重放首次结果，同键异命令冲突。确认事务同时追加批次/明细版本、里程碑事实、审计和Outbox，不留下半完成事实。

### 3.2 差异、拒收、豁免与更正

- 数量不符、型号/SN不符、外观/质量问题或证据不完整形成独立`ArrivalDifference`；保存类型、范围、数量、原因、风险、证据和处理版本。
- 拒收是明细结果，不等于差异已解决；被拒设备/数量仍属于未满足范围。
- 豁免只能针对明确`deviceId`或`orderLineId + model + quantity`，保存理由、风险、批准人、批准时间、有效期和证据；未知差异、模糊整项目豁免或过期豁免均不计入满足范围。豁免作为差异的`EXEMPTED`处置版本保存，不另造无Owner表。
- 已提交批次、差异处置、豁免和证据revision不可覆盖或删除。补签、差异关闭、豁免失效和签收信息纠正均追加新记录并递增`factVersion`；被更正版本保留来源链。

## 4. 文件与ACC-04契约

- 签收证据通过PLT `FileArtifactApi`保存稳定`FileReference`，由IMP建立不可变`DeliveryEvidenceRevision`；数据库不得保存或返回原始附件URL充当权威引用。
- 项目经理最终确认后，IMP在同一业务提交中发布`ImplementationEvidencePublished`，至少冻结`evidenceId/revision/sourceRequirement=EXE-01/sourceRecordId/sourceVersion/fileReference/hash/status`；ACC只建立索引、审核和归档引用，不复制或修改到货事实。
- ACC消费失败或引用尚未成功时，IMP保存`ARCHIVE_PENDING_RETRY`及重试审计；签收真值不回滚，但不得返回“已归档”。同一`evidenceId + revision`重试幂等。
- 附件上传、格式/病毒校验或FileReference创建失败时禁止最终确认；该失败与最终确认后ACC索引失败严格区分。

## 5. API与公开事实契约

用户路径继承`/api/v1/pms`：

| 接口 | 操作 | 契约 |
|---|---|---|
| `/arrival-acceptances` | `GET/POST` | 按授权项目分页；创建草稿必填项目、物流单号、签收时间及应到范围版本 |
| `/arrival-acceptances/{id}` | `GET/PATCH` | 返回可见批次、明细、差异、证据revision和`allowedActions`；PATCH仅允许本人草稿业务字段并要求`If-Match` |
| `/arrival-acceptances/{id}/actions/confirm` | `POST` | 项目经理锁定重验范围、数量、证据和版本后最终确认；要求`Idempotency-Key/If-Match` |
| `/arrival-acceptances/{id}/actions/raise-difference` | `POST` | 追加明确差异，不覆盖明细历史 |
| `/arrival-acceptances/{id}/actions/resolve-difference` | `POST` | 追加补签、拒收保持或明确豁免处置；要求版本、权限和证据 |

机器契约：`specs/features/F-IMP-002-arrival-fact-contract.json`。

`ArrivalAcceptanceFactApi.inspect/lockAndRevalidate`按受信租户、项目、设备/数量范围、期望`factVersion`和`scopeWatermark`读取或按稳定设备/订单行顺序锁定重验，返回`ACCEPTED/NOT_ACCEPTED/STALE`、稳定有序的`sourceAcceptanceIds`、已签/豁免/未满足范围、项目级单调`factVersion`及`reopened`。多批事实不得压缩成一个伪造来源ID；它不返回Owner DO、签收人隐私、文件正文或持久下载地址。

## 6. 数据与迁移

机器物理契约：`specs/features/F-IMP-002-physical-contract.json`；旧实现审计：`specs/features/F-IMP-002-legacy-reuse-audit.md`。

- Owner表固定为`imp_arrival_acceptance`、`imp_arrival_line`、`imp_arrival_difference`。根保存批次、范围快照、项目级事实版本、迁移核对状态和ACC同步状态；明细保存设备或订单型号数量及不可覆盖版本；差异表保存差异、拒收处置和具体豁免版本。每个影响项目里程碑的提交按项目分配单调`factVersion`并写入来源记录，不另造项目完成表。
- `pms_eng_arrival -> imp_arrival_*`执行`CURRENT_FORWARD`，只迁移可证明的身份、项目、批次编码、发生时间、操作者引用和原始说明。旧`equipment_id`须先映射到AST稳定设备；旧`attachment_url`须先转为有效FileReference；任一失败均保持待核对，不补默认值。
- 旧`status=0/1/2`、`inspection_result`、`exception_record`、单个`quantity`和测试种子均不足以证明当前应到范围、差异闭环、有效豁免或不可变证据。任何旧行不得仅凭tinyint直接产生`ACCEPTED`事实。
- 无法证明设备、数量、证据或完整性的新行保留旧记录并登记`PENDING_RECONCILIATION`迁移处置；不生成可供EXE-02/EXE-06消费的完成事实，不双写旧表。

## 7. 验收标准

- `AC-FIMP002-001`：多批和部分签收按当前DeliveryScope累计，只有全部设备/数量已签或有仍有效具体豁免时返回项目`ACCEPTED`。
- `AC-FIMP002-002`：超量、重复设备、跨租户、非本项目、范围版本过期、空范围、Owner不可用和无有效证据均失败关闭且不产生已确认事实。
- `AC-FIMP002-003`：差异、拒收、补签和豁免形成不可覆盖链；未知或过期豁免不满足范围，更正/重开递增事实版本并使旧消费者水位失效。
- `AC-FIMP002-004`：现场成员只能编辑本人草稿；项目经理最终确认本人负责项目；越权、旧If-Match和同键异请求均无业务副作用。
- `AC-FIMP002-005`：文件上传失败阻止确认；确认后ACC索引失败不回滚签收且仅显示`ARCHIVE_PENDING_RETRY`，重试不重复证据revision。
- `AC-FIMP002-006`：旧状态1/2记录在缺少应到范围、设备映射或有效证据时不能迁为ACCEPTED；旧表、旧页面和旧接口保持不变。
- `AC-FIMP002-007`：真实MySQL验证数量/当前版本唯一性、追加历史、并发确认和事务回滚；真实浏览器验证批次、部分签收、差异/补签/豁免、权限和ACC待重试。受控替身不能替代生产Owner正向验收。

## 8. Feature Ready Gate

当前结论：`REVIEW_REQUIRED`，不得生成Technical Plan或实施。

已形成候选锁定输入：完整旧实现复用审计；逐字段、旧状态和不可迁行处置；COM应到范围与AST稳定设备组合水位；ACC-04事件引用；`ArrivalAcceptanceFactApi`机器契约；三张正式Owner表物理契约；权限、并发、迁移和验收边界。

最近Gate为F-IMP-002 Feature Ready独立复审。复审GO前，Feature和Task继续保持DRAFT/NOT_STARTED；GO后才可生成Technical Plan并使用受控依赖替身，COM/AST/PLT/ACC生产契约未形成仍阻断Implementation Done和真实浏览器正向闭环。
