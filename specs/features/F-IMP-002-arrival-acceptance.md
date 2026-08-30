# F-IMP-002 到货签收与里程碑事实 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY / GO`（独立整改复审；锁定提交`4b5a2ac9`）
> Requirement：`EXE-01（V1/P0）`
> Requirement切片覆盖：`EXE-01@V1=FULL`
> Owner Context：`IMP（现场实施）`
> 消费Feature：`F-IMP-003`、`F-IMP-001`
> 外部输入依赖：COM `DeliveryScopeApi`、AST `T-FIMP001-AST-01`、PROJ `ProjectParticipantFactApi`/`ProjectScopeApi`、PLT `FileArtifactApi`
> 事件方向：IMP出向`ImplementationEvidencePublished`；ACC入向`ArtifactAccepted/Archived`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-imp-002-arrival-acceptance.md`（`PASS / GO`；锁定提交`e0184ac4`）
> REST/API机器契约：`specs/features/F-IMP-002-rest-api-contract.json`（`PASS / GO`；锁定提交`dbf62b8f`）

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

- `DRAFT/PARTIALLY_ACCEPTED/DIFFERENCE_PENDING/ACCEPTED/CONFIRMED`均为批次状态；项目里程碑不复用批次状态列，由`ArrivalAcceptanceFactApi`按全部当前范围返回`ACCEPTED/NOT_ACCEPTED/STALE`。
- 授权现场成员将DRAFT提交复核后，若存在未解决差异则进入DIFFERENCE_PENDING；无未解决差异且本批及已确认历史尚未覆盖全部项目应到范围时进入PARTIALLY_ACCEPTED；无未解决差异且候选累计范围已全部满足时进入ACCEPTED。上述两个ACCEPTED名称分别属于批次候选状态和项目事实契约，不得混写。
- 项目经理`confirm`只允许从PARTIALLY_ACCEPTED或ACCEPTED进入CONFIRMED；确认后该批业务字段和明细不可变。CONFIRMED只表示该批已由项目经理最终确认，不表示项目整体已签收；只有其中状态为ACCEPTED的明细和有效具体豁免参与项目事实计算。
- 明细状态固定为`NOT_ARRIVED/ACCEPTED/DIFFERENCE_PENDING/REJECTED`。批次必须保存物流单号、签收人快照、签收时间、设备或订单型号数量明细及至少一个有效证据revision。
- 授权现场成员只能创建和编辑本人未最终提交的批次及证据；项目经理在本人负责且`ProjectScopeApi.ACTION_EDIT`允许的项目内最终确认、提交差异处置和补签。客户端不能直接写生命周期状态。
- 最终确认使用`Idempotency-Key`和聚合`If-Match`；同键同规范化命令重放首次结果，同键异命令冲突。确认事务同时追加批次/明细版本、里程碑事实、审计和Outbox，不留下半完成事实。

### 3.2 差异、拒收、豁免与更正

- 数量不符、型号/SN不符、外观/质量问题或证据不完整形成独立`ArrivalDifference`；保存类型、范围、数量、原因、风险、证据和处理版本。
- 拒收是明细结果，不等于差异已解决；被拒设备/数量仍属于未满足范围。
- 豁免只能针对明确`deviceId`或`orderLineId + product/model + quantity + unit`，保存理由、风险、批准人、批准时间、有效期和证据；未知差异、模糊整项目豁免或过期豁免均不计入满足范围。豁免作为差异的`EXEMPTED`处置版本保存，不另造无Owner表。
- 每个`ArrivalDifference` revision的`scope_snapshot`使用严格判别联合：设备固定为`{"scopeType":"DEVICE","deviceId":正整数Long}`；数量固定为`{"scopeType":"ORDER_MODEL_QUANTITY","orderLineId":正整数Long,"productCode":String|null,"modelCode":String|null,"quantity":正BigDecimal,"unitCode":非空String}`。数量结构中`productCode/modelCode`规范化后至少一项非空，空值统一写JSON `null`；两种结构只能包含各自列出的精确键，禁止额外键、缺键、字符串数字、零/负数量、空白代码和未知`scopeType`。
- `EXEMPTED`累计只解析已确认批次中当前、未过期且批准人/批准时间/证据完整的revision；旧形状、未知形状、解析失败或越出当前应到范围的快照失败关闭且不得计入豁免，禁止从XLSX、reason文本或`arrival_line`当前值猜测。
- DIFFERENCE_PENDING只能在每个差异均追加`SUPPLEMENTED/REJECTED/EXEMPTED/CLOSED`处置后离开；补签使对应明细转入新版本ACCEPTED，拒收保持原范围未满足，豁免仅在有效期内满足明确范围。重算后进入PARTIALLY_ACCEPTED或ACCEPTED，再由项目经理确认。
- 已提交批次、差异处置、豁免和证据revision不可覆盖或删除。补签、差异关闭、豁免失效和签收信息纠正均创建关联原批次的后续DRAFT记录，原CONFIRMED批次不回退；普通`submit`只计算候选状态且不分配`factVersion`。首次确认和普通补签后继确认递增项目`factVersion`但`reopened=false`；更正、已确认历史差异关闭、重开或豁免失效的后继确认/独立失效revision递增版本并使新事实`reopened=true`，项目事实可从ACCEPTED变为NOT_ACCEPTED。
- 初始批次确认分配的`project_fact_version`只写`imp_arrival_acceptance`根；确认前已存在且未独立改变已发布项目事实的OPEN或处置revision，其`imp_arrival_difference.project_fact_version`在插入时为NULL并永久保持NULL，不得在确认时回填不可变历史。只有某个新追加差异revision本身构成已发布事实的更正、重开、失效或其他独立事实影响源时，才在创建该revision的同一事务分配非空版本，不能仅凭`resolution_status`推断。
- `ArrivalAcceptanceFactApi.reopened`只按当前最大`project_fact_version`的唯一权威来源类型推导：最大版本来自经完整资格校验的不可变差异事实影响revision时为`true`，来自无重开语义的普通确认根时为`false`；最大来源缺失、重复、损坏或后继根的重开语义无法由机器字段证明时失败关闭。不得从`factVersion>1`、`predecessor_acceptance_id`、处置状态、批次数量或当前判定猜测；旧期望版本在新重开版本形成后因`factVersion`变化返回`STALE`，重新读取当前版本可获得`reopened=true`。

## 4. DeliveryEvidence与ACC-04契约

- F-IMP-002在EXE-01支撑范围内拥有`imp_delivery_evidence/imp_delivery_evidence_revision`，只创建`sourceRequirement=EXE-01`且`sourceObjectType=ARRIVAL_ACCEPTANCE`的签收单证据；不宣称覆盖IMP-01其他实施交付件义务。签收证据通过PLT `FileArtifactApi`引用稳定`FileReference`，数据库不得保存或返回原始附件URL充当权威引用，也不得重复下载或复制二进制。
- `DeliveryEvidence`根保存证据身份、来源到货批次和当前ACC同步投影；上传或替换草稿时，从同一次PLT正式返回值追加revision并冻结`evidenceId/revision/artifactId/versionNo/referenceKey/FileFactVersion/scopeVersion/hash/sourceRecordId/sourceVersion`，旧revision不可覆盖。`FileFactVersion`只含非负整数`artifactVersion/referenceVersion/availabilityVersion`。项目经理确认批次时，在同一事务冻结当前revision引用并发布出向`ImplementationEvidencePublished`；首次发布把规范化命令`correlationId`原子写入根`acc_correlation_id`，此后同一证据发布/重试链必须原样继承且不可改写。ACC只建立索引、审核和归档引用，不复制或修改到货事实。
- IMP锁定重验文件时固定使用`ownerContext=IMP`、`objectType=ARRIVAL_ACCEPTANCE`、`objectId=来源到货批次ID`、`purposeCode=RECEIPT`和`requiredAction=READ`；这些策略字段不得由客户端传入。不得先读取当前PLT事实再把当前值冒充草稿冻结期望。
- 出向事件成功进入发送队列后，当前revision投影为`PUBLISHED_PENDING_ACC`。`ArtifactAccepted`和`ArtifactArchived`是ACC→IMP入向回执，必须回显`evidenceId/evidenceRevision/artifactId/fileVersion/reviewOrArchiveRecordId`。IMP按`eventId` Inbox幂等并按`evidenceId + evidenceRevision`拒绝旧序/错配回执：Accepted把当前revision推进为`ACCEPTED_PENDING_ARCHIVE`，Archived只允许从对应已接受revision推进为`ARCHIVED`。
- 发布失败、ACC暂不可用或Accepted回执超时/错配时，IMP转为`ARCHIVE_PENDING_RETRY`并按同一`evidenceId + revision`重发。已收到匹配Accepted后若Archived回执超时/丢失，则从`ACCEPTED_PENDING_ARCHIVE`转为`ARCHIVE_ACK_PENDING_RETRY`，保留已接受事实并重发同一revision；所有重发使用新`eventId`和服务端本次重试时间，但必须继承首次`acc_correlation_id`。匹配Archived可从上述等待态或归档回执重试态进入ARCHIVED，重复Accepted只作幂等确认且不退回发布重试。任何重试均不回滚签收真值、不重复revision，也不得提前返回“已归档”；旧revision迟到回执只记审计。
- 附件上传、格式/病毒校验或FileReference创建失败时禁止最终确认；该失败与最终确认后ACC索引失败严格区分。

## 5. API与公开事实契约

### 5.1 HTTP边界与服务端字段

- 对外路径固定为`/api/v1/pms/arrival-acceptances`；旧`/pms/eng-arrival`保持不变，不作新聚合的兼容或降级入口。
- `tenantId`、`actorUserId`、`status`、`allowedActions`、`approvedBy/approvedAt`、`projectFactVersion`、`factImpactType`、当前版本和各Owner水位均由服务端解析或分配，HTTP请求不得接收并信任这些字段。
- `PATCH`只能修改本人`DRAFT`的物流单号、签收时间、签收人快照、当前明细修订和PLT已返回的签收证据修订；不提供通用状态PATCH、删除或原始URL字段。明细和证据的更改均追加revision，不覆盖旧行。
- 列表页大小固定为`1..100`，按`arrivedAt DESC, id DESC`稳定排序；可见项目集合由服务端`ProjectScopeApi.ACTION_VIEW`解析，空集合返回空页，不省略权限条件。
- 详情统一返回批次根、当前明细、当前及历史差异revision、DeliveryEvidence摘要与当前revision、聚合版本和服务端`allowedActions`；不返回文件正文、持久下载URL或Owner DO。
- HTTP响应中的Java `long/Long`严格沿用Yudao `NumberSerializer`：值位于`(-9007199254740991, 9007199254740991)`时输出JSON number，落在边界或超出时输出十进制JSON string；前端不得把Snowflake字符串ID强转为JavaScript number。草稿尚未创建DeliveryEvidence根时，详情`evidence`明确为JSON null，不能用空ID和伪`NOT_PUBLISHED`状态冒充证据根。

### 5.2 命令、并发与追加历史

- 创建和全部action必须携带`Idempotency-Key`；`PATCH`和全部作用于已有聚合的action必须携带十进制非负聚合版本`If-Match`。同作用域同键同规范化请求重放首次结果；同键异请求永久冲突；首次命令仍处理中返回可重试冲突，不二次执行业务写。
- `raise-difference`只能在本人`DRAFT`上针对当前明细追加`OPEN`差异首revision；必须锁定聚合版本、明细ID/版本、基础平台启用的差异类型码、严格`scopeSnapshot`、原因、风险和PLT证据事实。范围必须属于该明细且不超出冻结COM/AST范围。
- `resolve-difference`使用严格判别联合`SUPPLEMENT/KEEP_REJECTED/EXEMPT/CLOSE/CORRECT_INFORMATION`，只接收分支所需的精确字段，不接收客户端`resolutionStatus`或包含大量可选字段的通用对象。前四个分支必须锁定当前差异revision/版本并追加新revision；`CORRECT_INFORMATION`仅允许当前项目经理针对`CONFIRMED`来源创建`CORRECTION` successor DRAFT，不回写原根。任何已提交历史均不回写。
- 未确认批次上的差异处置在原批次追加line/difference revision并重算候选状态；针对`CONFIRMED`历史的补签、信息纠正或差异关闭创建关联`predecessorAcceptanceId`的successor `DRAFT`，原批次不回退。
- 服务端依据已锁定的命令分支和当前已发布项目事实判定根`successorReason`及独立差异事实源`factImpactType`；客户端不得提交boolean、类型或`projectFactVersion`。非事实影响difference revision永久保持`factImpactType/projectFactVersion=NULL`；只有独立更正、重开或豁免失效revision在创建事务持有PROJ项目锁并分配项目事实版本。
- successor根必须保存服务端分配的`successorReason=SUPPLEMENT|CORRECTION|DIFFERENCE_CLOSURE|EXEMPTION_INVALIDATION`。`SUPPLEMENT`只表示新到范围的普通补签且确认后`reopened=false`；其余三类均明确发生在已发布历史之后，后继确认时由根分配新项目事实版本且`reopened=true`。后继仍为DRAFT时不提前发布事实。
- `Q-FIMP002-002`已裁决采用方案B：`batchCode`是业务到货批次的稳定身份，所有successor必须原样继承直接前驱已存储的规范化值，禁止后缀、截断或调用其他Owner生成新码；`CORRECT_INFORMATION`不得修改它。初始根以服务端`batch_root_marker=1`占用`tenant+project+batchCode`，successor的marker为NULL；每个前驱最多一个直接successor，创建前锁前驱并重验tenant/project。平台同键同载荷重放返回同一`successorAcceptanceId`，不同key/intent不得为同一前驱创建兄弟节点。
- 数量差异的`SUPPLEMENT`携带严格同一订单/型号单位身份和正数`supplementQuantity`。小于当前未满足量时，同一事务追加ACCEPTED line revision，并把当前差异追加为仅含精确剩余量的`OPEN` revision；等于未满足量时追加`SUPPLEMENTED` revision。DEVICE差异只能整项补签，不能用数量裁剪。任何补签不得超过当前差异剩余量或COM当前范围。
- 豁免到期由Task 5B内部`ExpireArrivalExemptionsCommand`处理，不由Fact查询产生副作用：按到期时间和稳定ID领取current `EXEMPTED` revision，逐项目取得PROJ权威锁，再锁根/明细/差异并重验COM/AST/PLT；同事务创建`EXEMPTION_INVALIDATION` successor DRAFT、追加事实影响差异revision、分配`projectFactVersion`并使旧事实陈旧。身份、版本、范围或证据无法重验时失败关闭并保留待重试，不从当前时间查询结果直接推导事实完成。

### 5.3 权限、allowedActions与错误

- 五项权限唯一映射：列表/详情使用`query`；创建使用`create`；PATCH与submit使用`edit-own-draft`；confirm使用`confirm`；raise/resolve共用`resolve-difference`。所有写入同时执行服务端ProjectScope和主体/当前项目事实守卫，全局角色或前端按钮不能替代。
- `allowedActions`封闭为`EDIT_DRAFT/SUBMIT/CONFIRM/RAISE_DIFFERENCE/RESOLVE_DIFFERENCE`，并与对应命令的功能权限、`ProjectScopeApi.ACTION_EDIT`、当前项目主体事实、批次状态、创建人和对象版本守卫逐项同构。`RESOLVE_DIFFERENCE`只对current `PROJECT_MANAGER`且状态为`DIFFERENCE_PENDING|CONFIRMED`的可处置对象返回；`EXEMPT`同样要求该项目经理资格。它是界面入口投影，每个命令仍必须在业务写前重验。
- 对越权或跨租户对象，详情和命令统一返回不可见/不存在，不泄露对象存在性；对已可见项目内主体不满足命令条件则返回授权拒绝。
- 到货专属错误必须区分参数校验、不可见/不存在、功能权限、数据范围、非法状态、聚合/明细/差异版本冲突、幂等冲突/处理中、COM/AST/PLT不可用、范围陈旧、项目阶段/资格业务门禁和证据无效；业务门禁与证据错误不得共用code。HTTP使用真实`400/403/404/409/422/503`语义，响应体仍使用Yudao `CommonResult{code,msg,data}`；`409/422/503`的`data`固定携带机器可读原因和恢复动作。Task 8以仅作用于该Controller的局部异常映射实现，不改写全局平台异常行为。
- Provider不可用统一归类为`OWNER_PROVIDER_UNAVAILABLE`，由`ownerContext/reasonCode`封闭区分PROJ、COM、AST、PLT；错误类别和每类`reasonCode`均以REST机器契约枚举为准，不接受任意字符串。

### 5.4 豁免审批主体裁决

- `Q-FIMP002-001`已裁决采用方案A：V1豁免审批人固定为调用当时本人负责该项目的current `PROJECT_MANAGER`，同时要求`resolve-difference + ACTION_EDIT`，并由`ProjectParticipantFactApi`在写事务中锁定重验。`approvedBy/approvedAt`只取受信actor和服务端时钟，客户端不得提交；权限键、数据范围或全局角色不能单独替代审批主体事实。

用户路径继承`/api/v1/pms`：

| 接口 | 操作 | 契约 |
|---|---|---|
| `/arrival-acceptances` | `GET/POST` | 按授权项目分页；创建草稿必填项目、物流单号、签收时间及应到范围版本 |
| `/arrival-acceptances/{id}` | `GET/PATCH` | 返回可见批次、明细、差异、证据revision和`allowedActions`；PATCH仅允许本人草稿业务字段并要求`If-Match` |
| `/arrival-acceptances/{id}/actions/submit` | `POST` | 授权现场成员提交本人DRAFT；锁定重验范围/证据并计算DIFFERENCE_PENDING/PARTIALLY_ACCEPTED/ACCEPTED候选状态 |
| `/arrival-acceptances/{id}/actions/confirm` | `POST` | 项目经理锁定重验范围、数量、证据和版本后最终确认；要求`Idempotency-Key/If-Match` |
| `/arrival-acceptances/{id}/actions/raise-difference` | `POST` | 追加明确差异，不覆盖明细历史 |
| `/arrival-acceptances/{id}/actions/resolve-difference` | `POST` | 追加补签、拒收保持或明确豁免处置；要求版本、权限和证据 |

机器契约：`specs/features/F-IMP-002-rest-api-contract.json`、`specs/features/F-IMP-002-arrival-fact-contract.json`。

`ArrivalAcceptanceFactApi.inspect/lockAndRevalidate`按受信租户、项目、设备/数量范围、期望`factVersion`和`scopeWatermark`读取或按稳定设备/订单行顺序锁定重验，返回`ACCEPTED/NOT_ACCEPTED/STALE`、稳定有序的`sourceAcceptanceIds`、已签/豁免/未满足范围、项目级单调`factVersion`及`reopened`。Owner锁定重验明确返回期望版本不一致时，IMP重新读取当前事实并返回`STALE`；Owner缺失、未知或不可用仍失败关闭，不得伪装为陈旧事实。多批事实不得压缩成一个伪造来源ID；它不返回Owner DO、签收人隐私、文件正文或持久下载地址。

## 6. 数据与迁移

机器物理契约：`specs/features/F-IMP-002-physical-contract.json`；旧实现审计：`specs/features/F-IMP-002-legacy-reuse-audit.md`。

- 到货Owner三表固定为`imp_arrival_acceptance`、`imp_arrival_line`、`imp_arrival_difference`；EXE-01证据支撑复用正式IMP Owner表`imp_delivery_evidence`、`imp_delivery_evidence_revision`。到货根保存批次、范围快照、项目级事实版本和迁移核对状态并引用证据revision；证据根保存ACC同步投影。所有版本分配路径必须在同一事务先持有对应`tenantId + projectId`的PROJ权威项目行锁，再取该项目`imp_arrival_acceptance.project_fact_version`与通过批次根关联的`imp_arrival_difference.project_fact_version`全部非NULL已分配值的MAX+1；不得直接读PROJ表或另造本地锁，唯一键只作最终冲突保护。普通提交保持NULL，首次确认及真正影响项目事实的来源才分配并写入对应不可变来源记录，不另造项目完成表。
- `pms_eng_arrival -> imp_arrival_*`执行`CURRENT_FORWARD`，只迁移可证明的身份、项目、批次编码、发生时间、操作者引用和原始说明。旧`equipment_id`须先映射到AST稳定设备；旧`attachment_url`须先转为有效FileReference；任一失败均保持待核对，不补默认值。
- 旧`status=0/1/2`、`inspection_result`、`exception_record`、单个`quantity`和测试种子均不足以证明当前应到范围、差异闭环、有效豁免或不可变证据。任何旧行不得仅凭tinyint直接产生`ACCEPTED`事实。
- 无法证明设备、数量、证据或完整性的新行保留旧记录并登记`PENDING_RECONCILIATION`迁移处置；不生成可供EXE-02/EXE-06消费的完成事实，不双写旧表。

## 7. 验收标准

- `AC-FIMP002-001`：多批和部分签收按当前DeliveryScope累计，只有全部设备/数量已签或有仍有效具体豁免时返回项目`ACCEPTED`。
- `AC-FIMP002-002`：超量、重复设备、跨租户、非本项目、范围版本过期、空范围、Owner不可用和无有效证据均失败关闭且不产生已确认事实。
- `AC-FIMP002-003`：DRAFT提交按差异和累计范围进入DIFFERENCE_PENDING/PARTIALLY_ACCEPTED/ACCEPTED，只有项目经理可转CONFIRMED；差异、拒收、补签和豁免形成不可覆盖链，未知或过期豁免不满足范围，更正/重开递增事实版本并使旧消费者水位失效。
- `AC-FIMP002-004`：现场成员只能编辑本人草稿；项目经理最终确认本人负责项目；越权、旧If-Match和同键异请求均无业务副作用。
- `AC-FIMP002-005`：文件上传失败阻止确认；上传/替换草稿追加EXE-01 DeliveryEvidenceRevision，确认事务冻结并发布当前revision；Accepted前失败进入`ARCHIVE_PENDING_RETRY`，Accepted成功但Archived回执丢失/超时进入`ARCHIVE_ACK_PENDING_RETRY`并可按同一revision幂等恢复至ARCHIVED；全程不重复revision、不回滚签收事实，旧序/错配回执无业务副作用。
- `AC-FIMP002-006`：旧状态1/2记录在缺少应到范围、设备映射或有效证据时不能迁为ACCEPTED；旧表、旧页面和旧接口保持不变。
- `AC-FIMP002-007`：真实MySQL验证数量/当前版本唯一性、追加历史、并发确认和事务回滚；真实浏览器验证批次、部分签收、差异/补签/豁免、权限和ACC待重试。受控替身不能替代生产Owner正向验收。

## 8. Feature Ready Gate

当前结论：`READY / GO`。独立整改复审已确认旧实现映射、应到范围、ArrivalAcceptanceFactApi、五表物理边界、ACC双向事件及批次/项目两层状态全部锁定，无剩余Feature Ready整改项。

已形成候选锁定输入：完整旧实现复用审计；逐字段、旧状态和不可迁行处置；COM应到范围与AST稳定设备组合水位；EXE-01最窄DeliveryEvidence两表及ACC-04双向事件；`ArrivalAcceptanceFactApi`机器契约；批次状态转换、项目里程碑判定；权限、并发、迁移和验收边界。

最近Gate转为F-IMP-002 Technical Plan。只允许生成一个当前有效计划；计划通过评审后才可实施。COM/AST/PLT/ACC生产契约未形成仍阻断Implementation Done和真实浏览器正向闭环，受控替身不得进入生产装配。
