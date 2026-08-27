# F-PLT-001 统一文件身份与版本管理 Feature Spec

> 文档状态：`BASELINE`
> Feature Ready：`READY / GO NPDMS-FPLT001-FEATURE-READY-20260826-01-R2`
> Requirement：`PLT-02（V1/P0，FR-PLT-008）`
> Owner Context：`PLT（基础平台 File Capability）`
> 前置能力：Yudao INFRA 文件存储配置、FileClient、私有对象存储和平台权限模型
> 适用基线：PRD V1.8及批准增量`CHG-PRD-2026-08-27-004`；SDS Phase 1/2/3 `BASELINE`
> 实施增量：原强制扫描实现已完成；`CHG-PRD-2026-08-27-004`可选扫描增量待NPDMS实施复验
> 边界裁决：`GO / NPDMS-FPLT001-BOUNDARY-20260826-01`
> INFRA架构例外：`GO / NPDMS-FPLT001-INFRA-EXCEPTION-20260826-01-R1`；`docs/decisions/0035-file-storage-receipt-adapter-exception.md`
> Technical Plan：Feature Ready独立GO且NPDMS锁定新规格提交后全新生成

## 1. 目标

建立PLT拥有的统一文件业务真值：`FileArtifact`提供稳定文件身份，`FileVersion`保存不可变内容版本，`FileReference`把业务对象固定到明确版本。上传、换版、访问、解绑、失效和归档均继承实时业务对象权限并留下平台审计；同一业务引用的历史审批不会因后续上传而改变。

本Feature首先解除F-SOL-001客户延期材料路径的上游阻断，并作为后续SOL、IMP、ACC、CUT、INS等领域的公共文件能力。Yudao INFRA继续负责技术存储，不拥有PLT业务文件身份、版本和业务引用。

## 2. Scope

### 2.1 包含

- tenant-aware的FileArtifact、不可变FileVersion、固定版本FileReference及稳定查询；
- 上传会话初始化、后端有界上传、完成校验、内容摘要、媒体类型嗅探、安全扫描和元数据提交；
- 新Artifact首版本、已有Artifact新版本、草稿引用CAS换版、解绑、未引用草稿逻辑删除；
- 文件版本失效、引用归档、短时下载/预览授权和拒绝审计；
- 业务对象权限实时回源SPI，未知Context/ObjectType、无Provider或Provider异常全部失败关闭；
- 平台幂等、版本CAS、对象存储/数据库补偿、`plt_operation_audit`和受信租户上下文；
- 文件类型与敏感级别字典校验、文件策略、50MB普通上传上限及私有存储；
- 可复用响应式文件上传、版本历史、引用和访问组件；
- 首个真实SOL消费者用途：`SOL / CONSTRUCTION_PLAN_CHANGE / CUSTOMER_DELAY_EVIDENCE`，只负责对象存在、项目范围和引用动作授权，不持有或推进PRE-01审批状态；
- 复用已批准的独立`FileStorageReceiptApi`技术回执例外，旧`FileApi`、`FileClient`方法和现有调用行为保持不变。

### 2.2 不包含

- PLT-01统一待办、INT-12采集编排、INT-11文档管理系统和历史附件迁移；
- SOL/IMP/ACC/CUT/INS的业务审核、齐套、批准、整改或归档结论；
- PRE-01审批状态、BPM流程、ConstructionPlan状态或其他业务域状态机；
- 大于50MB的采集原始结果上传；此类结果继续由外部受控存储持有并以后续独立来源注册契约接入；
- 公网永久URL、匿名永久分享、公开桶、文件正文内容审计或业务对象数据复制；
- 正式对象存储实例、扫描服务实例、密钥、容量、保留期限、Deployment、SIT、UAT和Release；
- 修改`yudao-framework`、PLT直访INFRA Mapper/DO/Service或在PMS重复实现对象存储客户端。

## 3. 业务规则

### BR-FPLT001-001 文件身份、版本和引用分离

- `FileArtifact`是租户内稳定业务身份，创建后不因换版改变；`infra_file_id`、storage path和URL均不是业务身份。
- 每次新内容生成同一Artifact下递增且不可变的`FileVersion`。`uk(tenant_id, artifact_id, version_no)`保证并发单胜，已提交版本没有内容、摘要、大小、媒体类型或存储定位更新入口。
- `FileReference`是业务对象用途槽位，固定到`artifactId+versionNo`。草稿允许在业务Owner授权后通过`If-Match`切换到新版本；已提交/审批/批准/发布/归档用途必须由业务Owner拒绝原位换版并通过新的业务revision引用新版本。
- 业务表冻结`artifactId+versionNo`，不得只保存URL；文件列表可从Artifact及版本历史查询当前内容和旧版本。

### BR-FPLT001-002 上传初始化与完成

- `POST /files:init-upload`必须携带`Idempotency-Key`，并提交业务Context、对象类型/ID、用途、引用键、文件类型、文件名、声明大小和媒体类型；tenantId、上传人和存储目录不得由请求自报。
- PLT先通过业务对象Provider验证对象存在、UPLOAD/REPLACE动作、项目范围、用途基数和允许策略，再提交`UploadSession`。初始化只返回artifactId、sessionId和expiresAt，不返回直传凭据或存储定位；sessionId同时作为受信`storageOperationId`。
- 完成上传以`sessionId+服务端实际SHA-256`幂等，通过后端`multipart/form-data`接收文件。复用Yudao/Spring现有MultipartFile解析和`MaxUploadSizeExceededException`处理，只把NPDMS应用级单文件配置从当前16MB前向调至50MB、请求上限调至容纳单文件及表单开销；不修改基础框架。PLT再以`50MB+1字节`有界读取，超过上限时在完整载入和调用INFRA前拒绝；不得调用`MultipartFile.getBytes()`读取未知大小对象。
- PLT始终校验实际大小、摘要、扩展名/声明MIME/内容嗅探类型和文件策略；客户端摘要只用于比对，不作为权威摘要。部署安全扫描默认关闭：关闭时不调用Provider并生成`SKIPPED`；开启时必须取得唯一Provider的`PASSED`。适用校验全部通过后，才把已验证的有限`byte[]`交给`FileStorageReceiptApi.store(...)`，并在PLT事务中创建FileVersion、创建或CAS切换FileReference、完成UploadSession、写幂等成功、审计和锁定文件事件Outbox。
- 普通上传单文件不超过50MB；未知大小、超限、可执行白名单外内容、压缩包越界、类型不一致、扫描`REJECTED/ERROR`或技术存储冲突均不得产生可引用FileVersion。
- `SKIPPED`只允许在部署关闭扫描时产生，Provider编码/版本为空，只表示未执行病毒扫描；开启扫描后Provider缺失、重复、异常、`ERROR/REJECTED`或未知结果均失败关闭，不得降级为`SKIPPED`。扫描开关变化不改写历史FileVersion。

### BR-FPLT001-003 换版、解绑、删除、失效和归档

- 草稿换版必须使用同一Artifact、业务Provider返回`MUTABLE`、引用`If-Match`命中并创建新FileVersion；不得覆盖旧内容或复用旧versionNo。
- `detach`只把允许解除的FileReference记为`DETACHED`，不删除Artifact、Version或业务审计。已冻结/审批引用由业务Provider返回`IMMUTABLE`并拒绝解绑；同一稳定用途槽位后续重新上传时，在Provider重新授权和`If-Match`命中后把该Reference绑定到新版本并恢复`ACTIVE`，不创建冲突的第二槽位。
- 逻辑删除只允许从未形成活动/归档引用且所有版本均未被业务冻结的草稿Artifact；删除后ID和版本历史不得重用，不物理删除已注册技术对象。
- 病毒、合规或内容缺失导致版本失效时记`INVALIDATED/UNAVAILABLE`并阻止新引用和访问；既有业务引用保留元数据和失效原因，不改写历史业务结论。
- 归档追加`FileArchiveRecord`并把目标引用记为`ARCHIVED`；归档重试按`archiveBatchId+artifactId+versionNo`幂等，归档失败不伪造成功。

### BR-FPLT001-004 业务对象权限与公共Provider

- `pms-module-platform-api`定义`FileBusinessObjectPolicyProvider`，业务Owner按`ownerContext+objectType`实现对象存在、动作权限、用途策略、引用可变性和业务范围事实；PLT仅调用公共Provider，不读异域表。
- 动作值域封闭为`UPLOAD/REFERENCE/READ/DOWNLOAD/PREVIEW/REPLACE/DETACH/ARCHIVE/INVALIDATE`。空动作、未知动作、无Provider、多个Provider命中、越租户、空业务范围或Provider异常均失败关闭。
- Provider只返回授权结论、`scopeVersion`和用途策略，不返回业务正文。PLT写命令在提交前用同一受信租户重验`scopeVersion`；范围变化返回版本冲突且无成功文件引用副作用。
- 首个SOL Provider只支持`CONSTRUCTION_PLAN_CHANGE/CUSTOMER_DELAY_EVIDENCE`，通过现有ProjectScope和SOL对象事实判断当前项目经理、主责服务经理及查询主体；它不写PRE-01状态，不把文件扫描通过解释为工期变更审批通过。

### BR-FPLT001-005 功能权限与短时访问

| 能力 | 功能权限码 | 业务Provider动作 | 约束 |
|---|---|---|---|
| 查看元数据和版本 | `pms:file:query` | `READ` | 实时业务对象范围；失效版本只返回受控元数据 |
| 上传和草稿换版 | `pms:file:upload` | `UPLOAD/REPLACE` | 用途策略、大小/类型/扫描、If-Match |
| 下载 | `pms:file:download` | `DOWNLOAD` | 私有对象、短时URL、版本状态和审计 |
| 预览 | `pms:file:preview` | `PREVIEW` | 仅支持的安全格式，转换失败不改变版本 |
| 解绑和未引用草稿删除 | `pms:file:manage` | `DETACH` | 业务可变性与引用状态重验 |
| 归档/失效 | `pms:file:archive` | `ARCHIVE/INVALIDATE` | 业务Owner授权；追加归档事实 |

- 前端按钮不构成权限真值；服务端依次校验受信租户、功能权限、业务对象Provider、用途策略、版本状态和CAS。
- 下载/预览生成短时、最小权限的访问授权；PLT持久化授权事实和不可逆token摘要，不持久化短时URL。对象存储桶保持私有，URL过期或撤销后重新授权。

### BR-FPLT001-006 INFRA复用与已批准例外

- 按ADR-0035及独立裁决`NPDMS-FPLT001-INFRA-EXCEPTION-20260826-01-R1`新增独立`FileStorageReceiptApi`；保留现有`FileApi`、`FileClient`、`infra_file`语义和全部既有调用行为，不修改`yudao-framework`。
- `store(FileStorageStoreCommand)`只接收受信`storageOperationId`和已由PLT完成上限、摘要、媒体类型及扫描校验的有限内容，返回`storageOperationId/infraFileId/name/mediaType/sizeBytes`；`presignGet`按infraFileId生成短时URL；`delete`只按storageOperationId补偿无已提交FileVersion引用的技术对象。
- 保留path仅由storageOperationId生成且跨配置一致。INFRA在store/delete前跨全部配置查询：0条时store才冻结当前master创建，1条按既有记录configId返回或删除，多条失败关闭并进入存储对账；禁止任选、重复创建或按当前master猜测。
- 新DTO不包含tenantId、业务对象、Artifact/Version/Reference或业务权限；PLT只保存`infraFileId`技术定位，URL只作为短时响应。PLT不得自行实现对象存储客户端或直访INFRA内部。

### BR-FPLT001-007 文件事件

- 封闭事件集合固定为`FileVersionCommitted`、`FileReferenceAttached`、`FileReferenceDetached`、`FileArchived`，不得静默删除或新增其他文件事件。
- 业务事实与对应Outbox在同一PLT事务写入；事件使用稳定eventId和operationId，载荷不可变且不含正文、短时URL、token、storagePath或业务详情。
- `FileVersionCommitted`最小载荷为`eventId/tenantId/artifactId/versionNo/sha256/scanStatus/occurredAt/operationId`；Attached/Detached最小载荷为`eventId/tenantId/referenceId/artifactId/versionNo/ownerContext/objectType/objectId/purposeCode/occurredAt/operationId`；`FileArchived`另携带`archiveBatchId/businessDecisionRef`。
- Outbox失败重试不回滚已提交文件业务事实；消费方按eventId幂等，重复、乱序或重放不得产生重复业务事实。业务事务失败时没有成功事件。

### BR-FPLT001-008 幂等、并发、补偿与审计

- 初始化上传、完成上传、创建/换版引用、解绑、草稿删除、归档和失效均使用平台幂等记录；同键同规范载荷重放原结果，同键异载荷冲突。
- Artifact新版本号通过Artifact行锁/版本CAS和唯一键分配；Reference换版通过`referenceVersion+If-Match`单胜；同会话只允许一个完成结果。
- INFRA成功而PLT事务失败时，已提交UploadSession保留同一storageOperationId；重试通过跨配置保留path找回原技术回执。确认不再重试后，只有证明该operation没有已提交FileVersion引用才按storageOperationId补偿；不得删除任何已有PLT Version使用的对象。
- 数据库已有Version而对象不可读时标记`UNAVAILABLE`并告警，不返回成功下载、不伪造空文件；恢复后只恢复可用状态，不改内容摘要。
- 成功和拒绝审计记录actor、artifact/version/reference/session、业务Context/object/purpose、动作、前后状态/版本、摘要、operationId、失败码和时间；不记录文件正文、上传URL或访问token明文。

## 4. API契约

所有HTTP路径继承`/api/v1/pms`前缀，返回平台统一`CommonResult`和稳定错误码。列表使用稳定游标；所有写请求拒绝tenantId、actorUserId、storagePath、configId和永久URL自报。

| 接口 | 操作 | 契约 |
|---|---|---|
| `/files:init-upload` | `POST` | Header必填`Idempotency-Key`；输入业务对象、用途、引用键、文件策略元数据和可选expectedReferenceVersion；返回artifactId、sessionId、expiresAt，不返回存储凭据 |
| `/files/{artifactId}:complete-upload` | `POST multipart/form-data` | Header必填`Idempotency-Key`；输入sessionId、file和可选客户端摘要；50MB前置与有界读取、服务端校验、扫描、技术回执、Version、Reference及事件同一业务提交 |
| `/files/{artifactId}` | `GET` | 返回Artifact元数据、可见当前引用摘要、artifactVersion和allowedActions；不返回storagePath或永久URL |
| `/files/{artifactId}/versions` | `GET` | 按`versionNo,id`稳定游标返回授权可见版本及可用/失效状态 |
| `/file-references` | `GET` | 按业务对象、用途和引用键查询；无范围返回空结果，不放宽到其他对象 |
| `/file-references/{id}` | `DELETE` | Header必填`If-Match`和`Idempotency-Key`；只执行经Provider授权的detach |
| `/files/{artifactId}/actions/delete-draft` | `POST` | 仅未引用草稿逻辑删除，幂等且不可重建复用ID |
| `/files/{artifactId}/actions/invalidate` | `POST` | 输入versionNo、稳定原因码和说明；阻断后续访问/引用并保留历史 |
| `/file-references/{id}/actions/archive` | `POST` | 输入archiveBatchId和业务决策引用；追加归档事实 |
| `/files/{artifactId}/access-tickets` | `POST` | 输入versionNo及`DOWNLOAD/PREVIEW`；实时重验并返回短时URL和expiresAt |

### 4.1 公共业务API

`pms-module-platform-api`新增稳定公共契约：

- `FileArtifactApi.inspect(FileArtifactVersionQuery)`：输入artifactId、versionNo、业务对象、用途、非空referenceKey和requiredAction；referenceKey与物理稳定键中的同名字段一致，按受信租户只检查该精确引用槽位，返回同一referenceKey、由`artifactVersion/referenceVersion/availabilityVersion`组成的`fileFactVersion`及业务`scopeVersion`；
- `FileArtifactApi.lockAndRevalidate(FileArtifactVersionRevalidationQuery)`：除inspect稳定键和同一referenceKey外必须输入`expectedFileFactVersion`和`expectedScopeVersion`。同一事务先调用业务Provider锁定重验scope，再依次锁定Artifact、精确FileVersion、按完整稳定键定位的精确FileReference；锁后验证该Reference的artifactId、versionNo、status和referenceVersion，再比较版本可用性、Artifact生命周期及scopeVersion。任一变化、缺失或越租户均返回版本冲突且无消费方成功副作用；referenceKey为空或未命中不得省略条件并扩大查询。
- `FileBusinessObjectPolicyProvider.inspect(...)`提供读取事实；`lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery)`按expectedScopeVersion锁定业务Owner事实并保持到调用事务结束，返回用途策略、引用可变性和当前scopeVersion；
- `FileSecurityScanProvider.scan(FileSecurityScanCommand)`：PLT内部技术Provider，只在部署启用扫描时装配和调用，返回`PASSED/REJECTED/ERROR`及非敏感扫描版本/原因码；启用后未配置、重复或异常时完成上传失败关闭。关闭时不调用Provider，由PLT策略层记录`SKIPPED`及空Provider事实。

`artifactVersion`随Artifact生命周期变化递增，`referenceVersion`随绑定版本、引用状态或持久化范围事实变化递增，`availabilityVersion`随精确FileVersion的可用/失效/恢复变化递增；内容字段保持不可变。返回事实不包含文件正文、storagePath、INFRA URL或访问token。SOL冻结`artifactId+versionNo+referenceKey+fileFactVersion+scopeVersion`，并在自身事务以同一referenceKey通过`lockAndRevalidate`重验；PLT不接收SOL审批结论。

### 4.2 模块与依赖方向

- 业务真值、应用服务、DO、Mapper和公共API归属`pms-module-platform`/`pms-module-platform-api`；业务消费者只依赖platform-api。
- `pms-module-platform`仅通过已批准的公开`FileStorageReceiptApi`调用`yudao-module-infra`技术存储能力，不访问INFRA Mapper、DO或Service；不得把`infra_file_id`暴露为业务文件ID。
- SOL等业务模块实现`FileBusinessObjectPolicyProvider`并只读本域事实；Provider不回调PLT写命令，避免依赖环。
- 依赖方向固定为`PLATFORM -> INFRA public FileStorageReceiptApi`；旧INFRA HTTP/API及`FileApi`继续兼容，例外不得扩大到FileClient或框架。

## 5. 数据与物理边界

机器契约：`specs/features/F-PLT-001-physical-contract.json`。

- 前向新建`plt_file_artifact`、`plt_file_version`、`plt_file_reference`、`plt_file_upload_session`、`plt_file_access_grant`、`plt_file_archive_record`；不修改既有`infra_file`语义。
- PLT表全部tenant-aware，跨表使用包含`tenant_id`的复合外键；`owner_context/object_type/object_id`和`infra_file_id`只作稳定跨Context/技术引用，不建跨模块数据库外键。
- Artifact不保存currentVersion指针；业务当前内容由FileReference固定的`artifact_id+file_version_no`表达，版本表不复制`is_current`。
- FileReference按`tenant_id+owner_context+object_type+object_id+purpose_code+reference_key`唯一，支持一个对象同用途的多个稳定槽位；换版更新该槽位并递增referenceVersion，审批历史仍冻结原artifact/version。
- UploadSession和AccessGrant有明确过期时间；过期只阻断继续使用，不物理删除Artifact、Version、Reference或审计。
- 新DDL使用NPDMS实施时下一个未占用Flyway版本，前向迁移且不修改已执行SQL。

## 6. UI

- 提供可复用文件上传/换版、版本历史、引用状态、下载/预览和错误恢复组件，业务页面传入ownerContext、objectType、objectId、purposeCode和referenceKey，不传租户或永久存储定位。
- 优先复用Yudao Upload、Table、Descriptions、Timeline、Dialog/Drawer和权限组件；无可复用时遵循Element Plus结构、主题变量和响应式断点，避免大量内联样式。
- 320/768/1024/1440无页面级横向溢出；窄屏使用列表/抽屉，长文件名可换行或省略并提供可访问完整名称。
- 上传进度不等于FileVersion完成；只有服务端完成全部适用校验后显示“可用”。UI/API必须区分`PASSED`与`SKIPPED`，不得把未扫描版本显示为扫描安全；失败显示稳定原因和重试入口，不伪造成功引用。

## 7. 验收标准

- `AC-FPLT001-001`：新文件产生稳定Artifact和Version 1；同Artifact新内容只追加版本，旧内容、摘要和审批冻结引用不变。
- `AC-FPLT001-002`：UploadSession初始化先校验真实业务对象权限；无Provider、多Provider、越租户、空范围、未知动作和scopeVersion变化失败关闭。
- `AC-FPLT001-003`：完成上传使用服务端实际大小、SHA-256和MIME嗅探；扫描关闭时不调用Provider且Version/审计/事件真实记录`SKIPPED`及空Provider事实，开启时只有`PASSED`成功。类型/摘要/大小不符、病毒、扫描错误、Provider缺失/重复/异常或对象缺失均不产生可引用Version，开启路径不得降级为`SKIPPED`。
- `AC-FPLT001-004`：草稿Reference用If-Match切换到新版本；并发只一方成功，旧版本仍可按权限查询；不可变业务引用拒绝换版/解绑。
- `AC-FPLT001-005`：未引用草稿可逻辑删除；存在活动/归档/业务冻结引用时拒绝，Artifact ID和版本号不重用。
- `AC-FPLT001-006`：失效/不可用版本不能新引用、下载或预览，已有业务引用和审计仍可追溯；恢复不改变内容摘要。
- `AC-FPLT001-007`：归档按批次幂等追加，归档失败不改变业务审核结论；FileArchiveRecord不可更新/删除。
- `AC-FPLT001-008`：下载/预览实时重验功能权限和业务对象范围，只返回短时URL；无权访问拒绝并留审计，PLT表不持久化短时URL/token明文。
- `AC-FPLT001-009`：对象已上传但元数据失败可按同session重试；最终补偿只删除无PLT引用对象，不影响已有版本；同键异载荷冲突。
- `AC-FPLT001-010`：首个SOL消费者能上传并冻结`artifactId+versionNo`，项目范围或文件事实变化时PRE-01提交失败且无BPM/SOL成功副作用；PLT不推进PRE-01状态。
- `AC-FPLT001-011`：已批准INFRA例外保留旧FileApi/FileClient方法和既有调用；PLT只持久化技术回执中的infraFileId，业务响应不暴露技术定位；master切换重试仍找回原回执，多记录失败关闭。
- `AC-FPLT001-012`：全新MySQL从V1迁移至实施版本，验证六表、复合外键、唯一键、CAS、幂等、回滚和字典/权限种子；已执行迁移不前向修改。
- `AC-FPLT001-013`：真实浏览器完成上传→服务端校验→引用→换版→历史查看→短时下载/预览，以及权限负向、失败重试、刷新持久和四档响应式；无当前功能控制台/失败HTTP异常。
- `AC-FPLT001-014`：不宣称PLT-01、INT-12、业务审核、历史迁移、Deployment、SIT、UAT或Release完成。
- `AC-FPLT001-015`：四类锁定文件事件与业务事实同事务进入Outbox，eventId重放不重复业务事实；业务失败无成功事件。
- `AC-FPLT001-016`：SOL冻结fileFactVersion三段及scopeVersion；Artifact生命周期、Reference绑定/状态、Version可用性或业务范围任一变化后锁定重验失败且无SOL/BPM成功副作用。
- `AC-FPLT001-017`：同一对象和purposeCode存在多个referenceKey时，inspect与lockAndRevalidate只命中请求指定的同一槽位；空referenceKey失败关闭且不返回其他槽位事实。

## 8. 测试与证据

本Feature按已确认的非TDD方式实施，先完成当前Task的正向闭环，再按风险补充异常、权限和并发验证。Feature Done至少需要：公共API/Provider契约测试、存储适配与旧FileApi兼容测试、内容校验/扫描测试、平台幂等审计、真实MySQL迁移与事务补偿、首个SOL真实消费链、真实私有对象存储和真实浏览器证据，以及独立代码复审。

## 9. Definition of Ready

| 项目 | 当前状态 |
|---|---|
| PLT-02与PLT-01/INT-12/业务审核流程拆分 | PASS（`NPDMS-FPLT001-BOUNDARY-20260826-01`） |
| 文件身份、版本、引用和技术存储分层 | PASS |
| 上传、访问、换版、失效、归档、权限、幂等与补偿 | PASS |
| 公共业务API、物理契约和首个SOL用途 | PASS |
| Yudao INFRA技术回执例外 | PASS（ADR-0035；`NPDMS-FPLT001-INFRA-EXCEPTION-20260826-01-R1`） |
| 独立Feature Ready裁决 | PASS（`NPDMS-FPLT001-FEATURE-READY-20260826-01-R2`） |

结论：`BASELINE / READY`。允许锁定新的规格提交并同步NPDMS受管基线；同步校验通过后创建全新Technical Plan。该GO不代表Technical Plan、Implementation、Deployment、SIT、UAT或Release通过。
