# F-PLT-001 统一文件身份与版本管理 Feature Spec

> 文档状态：`IN_REVIEW`
> Feature Ready：`NOT_READY / PENDING_INDEPENDENT_REVIEW`
> Requirement：`PLT-02（V1/P0，FR-PLT-008）`
> Owner Context：`PLT（基础平台 File Capability）`
> 前置能力：Yudao INFRA 文件存储配置、FileClient、私有对象存储和平台权限模型
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> 边界裁决：`GO / NPDMS-FPLT001-BOUNDARY-20260826-01`
> Technical Plan：Feature Ready独立GO且NPDMS锁定新规格提交后全新生成

## 1. 目标

建立PLT拥有的统一文件业务真值：`FileArtifact`提供稳定文件身份，`FileVersion`保存不可变内容版本，`FileReference`把业务对象固定到明确版本。上传、换版、访问、解绑、失效和归档均继承实时业务对象权限并留下平台审计；同一业务引用的历史审批不会因后续上传而改变。

本Feature首先解除F-SOL-001客户延期材料路径的上游阻断，并作为后续SOL、IMP、ACC、CUT、INS等领域的公共文件能力。Yudao INFRA继续负责技术存储，不拥有PLT业务文件身份、版本和业务引用。

## 2. Scope

### 2.1 包含

- tenant-aware的FileArtifact、不可变FileVersion、固定版本FileReference及稳定查询；
- 上传会话初始化、短时直传、完成校验、内容摘要、媒体类型嗅探、安全扫描和元数据提交；
- 新Artifact首版本、已有Artifact新版本、草稿引用CAS换版、解绑、未引用草稿逻辑删除；
- 文件版本失效、引用归档、短时下载/预览授权和拒绝审计；
- 业务对象权限实时回源SPI，未知Context/ObjectType、无Provider或Provider异常全部失败关闭；
- 平台幂等、版本CAS、对象存储/数据库补偿、`plt_operation_audit`和受信租户上下文；
- 文件类型与敏感级别字典校验、文件策略、50MB普通上传上限及私有存储；
- 可复用响应式文件上传、版本历史、引用和访问组件；
- 首个真实SOL消费者用途：`SOL / CONSTRUCTION_PLAN_CHANGE / CUSTOMER_DELAY_EVIDENCE`，只负责对象存在、项目范围和引用动作授权，不持有或推进PRE-01审批状态；
- 对Yudao `FileApi`候选最小前向加法进行Feature Ready单独裁决，旧方法和现有调用行为保持不变。

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
- PLT先通过业务对象Provider验证对象存在、UPLOAD/REPLACE动作、项目范围、用途基数和允许策略，再创建`UploadSession`。初始化只返回短时上传凭据和会话ID，上传URL不得持久化为Artifact或Version身份。
- 完成上传以`sessionId+服务端实际SHA-256`幂等。PLT通过INFRA公共技术契约读取已上传对象，服务端校验实际大小、摘要、扩展名/声明MIME/内容嗅探类型、文件策略和安全扫描；客户端摘要只用于比对，不作为权威摘要。
- 只有安全扫描`PASSED`且所有校验通过，才注册INFRA技术文件回执并在PLT事务中创建FileVersion、创建或CAS切换FileReference、完成UploadSession、写幂等成功和审计。扫描`REJECTED/ERROR`、对象缺失或元数据冲突均不得产生可引用FileVersion。
- 普通上传单文件不超过50MB；未知大小、超限、可执行白名单外内容、压缩包越界或类型不一致失败关闭。

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

### BR-FPLT001-006 INFRA复用与候选例外

- 保留现有`FileApi.createFile(...)`和`presignGetUrl(String,...)`的签名与行为；现有`infra_file`仍是技术存储记录且保持`@TenantIgnore`，不得被PLT查询为业务真值。
- Feature Ready候选申请仅对`yudao-module-infra`公开`FileApi`做以下最小前向加法：
  1. `createUploadTicket(FileStorageUploadTicketRequest)`返回`configId/storagePath/uploadUrl/expiresAt`；
  2. `readStorageObject(FileStorageObjectQuery)`按`configId+storagePath`返回内容字节，限本Feature50MB完成校验；
  3. `registerStorageObject(FileStorageRegisterCommand)`返回`infraFileId/configId/storagePath/name/mediaType/size`稳定技术回执；
  4. `presignGetStorageObject(FileStorageObjectQuery, expirationSeconds)`按指定配置和path生成短时读取URL；
  5. `deleteRegisteredStorageObject(infraFileId)`仅供PLT完成失败后的受控技术补偿。
- 新DTO不包含tenantId、业务对象、Artifact/Version/Reference或业务权限；PLT不能传入永久公开ACL。`configId+storagePath`是技术定位，不是业务身份；URL只作为短时响应。
- 上述例外未获Feature Ready独立批准前不得修改INFRA。若拒绝该例外，本Feature保持`NOT_READY`，不得退化为URL真值或另造存储客户端。

### BR-FPLT001-007 幂等、并发、补偿与审计

- 初始化上传、完成上传、创建/换版引用、解绑、草稿删除、归档和失效均使用平台幂等记录；同键同规范载荷重放原结果，同键异载荷冲突。
- Artifact新版本号通过Artifact行锁/版本CAS和唯一键分配；Reference换版通过`referenceVersion+If-Match`单胜；同会话只允许一个完成结果。
- 对象已上传而PLT事务失败时，UploadSession保持可重试并引用同一技术对象；确认不再重试后由补偿按会话删除未注册/未引用对象。INFRA注册成功而PLT提交失败时按`infraFileId`补偿，不删除任何已有PLT Version使用的对象。
- 数据库已有Version而对象不可读时标记`UNAVAILABLE`并告警，不返回成功下载、不伪造空文件；恢复后只恢复可用状态，不改内容摘要。
- 成功和拒绝审计记录actor、artifact/version/reference/session、业务Context/object/purpose、动作、前后状态/版本、摘要、operationId、失败码和时间；不记录文件正文、上传URL或访问token明文。

## 4. API契约

所有HTTP路径继承`/api/v1/pms`前缀，返回平台统一`CommonResult`和稳定错误码。列表使用稳定游标；所有写请求拒绝tenantId、actorUserId、storagePath、configId和永久URL自报。

| 接口 | 操作 | 契约 |
|---|---|---|
| `/files:init-upload` | `POST` | Header必填`Idempotency-Key`；输入业务对象、用途、引用键、文件策略元数据和可选expectedReferenceVersion；返回artifactId、sessionId、短时上传票据、expiresAt |
| `/files/{artifactId}:complete-upload` | `POST` | Header必填`Idempotency-Key`；输入sessionId和可选客户端摘要；完成服务端校验、扫描、技术注册、Version及Reference提交 |
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

- `FileArtifactApi.inspect(FileArtifactVersionQuery)`：按受信租户、artifactId、versionNo、业务对象和用途检查固定版本可引用/可访问事实；
- `FileArtifactApi.lockAndRevalidate(FileArtifactVersionRevalidationQuery)`：锁定当前Version/Reference并比较`fileFactVersion/scopeVersion`，供业务写事务在提交前失败关闭；
- `FileBusinessObjectPolicyProvider.inspect(FileBusinessObjectPolicyQuery)`：由业务Owner提供实时对象权限、用途基数、文件策略、引用可变性和scopeVersion；
- `FileSecurityScanProvider.scan(FileSecurityScanCommand)`：PLT内部技术Provider，返回`PASSED/REJECTED/ERROR`及非敏感扫描版本/原因码；未配置或异常时完成上传失败关闭。

返回事实不包含文件正文、storagePath、INFRA URL或访问token。SOL提交只消费`artifactId+versionNo+fileFactVersion`，并在自身事务通过`lockAndRevalidate`重验；PLT不接收SOL审批结论。

### 4.2 模块与依赖方向

- 业务真值、应用服务、DO、Mapper和公共API归属`pms-module-platform`/`pms-module-platform-api`；业务消费者只依赖platform-api。
- `pms-module-platform`仅通过公开`FileApi`调用`yudao-module-infra`技术存储能力，不访问INFRA Mapper、DO或Service；不得把`infra_file_id`暴露为业务文件ID。
- SOL等业务模块实现`FileBusinessObjectPolicyProvider`并只读本域事实；Provider不回调PLT写命令，避免依赖环。
- Feature Ready批准INFRA最小例外后，依赖方向为`PLATFORM -> INFRA public FileApi`；旧INFRA HTTP/API继续兼容。若未批准，Feature不得实施。

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
- 上传进度不等于FileVersion完成；只有服务端完成校验和扫描后显示“可用”。失败显示稳定原因和重试入口，不伪造成功引用。

## 7. 验收标准

- `AC-FPLT001-001`：新文件产生稳定Artifact和Version 1；同Artifact新内容只追加版本，旧内容、摘要和审批冻结引用不变。
- `AC-FPLT001-002`：UploadSession初始化先校验真实业务对象权限；无Provider、多Provider、越租户、空范围、未知动作和scopeVersion变化失败关闭。
- `AC-FPLT001-003`：完成上传使用服务端实际大小、SHA-256、MIME嗅探和安全扫描；类型/摘要/大小不符、病毒、扫描错误或对象缺失均不产生可引用Version。
- `AC-FPLT001-004`：草稿Reference用If-Match切换到新版本；并发只一方成功，旧版本仍可按权限查询；不可变业务引用拒绝换版/解绑。
- `AC-FPLT001-005`：未引用草稿可逻辑删除；存在活动/归档/业务冻结引用时拒绝，Artifact ID和版本号不重用。
- `AC-FPLT001-006`：失效/不可用版本不能新引用、下载或预览，已有业务引用和审计仍可追溯；恢复不改变内容摘要。
- `AC-FPLT001-007`：归档按批次幂等追加，归档失败不改变业务审核结论；FileArchiveRecord不可更新/删除。
- `AC-FPLT001-008`：下载/预览实时重验功能权限和业务对象范围，只返回短时URL；无权访问拒绝并留审计，PLT表不持久化短时URL/token明文。
- `AC-FPLT001-009`：对象已上传但元数据失败可按同session重试；最终补偿只删除无PLT引用对象，不影响已有版本；同键异载荷冲突。
- `AC-FPLT001-010`：首个SOL消费者能上传并冻结`artifactId+versionNo`，项目范围或文件事实变化时PRE-01提交失败且无BPM/SOL成功副作用；PLT不推进PRE-01状态。
- `AC-FPLT001-011`：INFRA候选加法保留旧FileApi方法和既有调用；PLT只持久化技术回执中的`infraFileId/configId/storagePath`，业务响应不暴露它们。
- `AC-FPLT001-012`：全新MySQL从V1迁移至实施版本，验证六表、复合外键、唯一键、CAS、幂等、回滚和字典/权限种子；已执行迁移不前向修改。
- `AC-FPLT001-013`：真实浏览器完成上传→服务端校验→引用→换版→历史查看→短时下载/预览，以及权限负向、失败重试、刷新持久和四档响应式；无当前功能控制台/失败HTTP异常。
- `AC-FPLT001-014`：不宣称PLT-01、INT-12、业务审核、历史迁移、Deployment、SIT、UAT或Release完成。

## 8. 测试与证据

本Feature按已确认的非TDD方式实施，先完成当前Task的正向闭环，再按风险补充异常、权限和并发验证。Feature Done至少需要：公共API/Provider契约测试、存储适配与旧FileApi兼容测试、内容校验/扫描测试、平台幂等审计、真实MySQL迁移与事务补偿、首个SOL真实消费链、真实私有对象存储和真实浏览器证据，以及独立代码复审。

## 9. Definition of Ready

| 项目 | 当前状态 |
|---|---|
| PLT-02与PLT-01/INT-12/业务审核流程拆分 | PASS（`NPDMS-FPLT001-BOUNDARY-20260826-01`） |
| 文件身份、版本、引用和技术存储分层 | PASS |
| 上传、访问、换版、失效、归档、权限、幂等与补偿 | PASS |
| 公共业务API、物理契约和首个SOL用途 | PASS |
| Yudao INFRA FileApi最小例外 | PENDING_FEATURE_READY_DECISION |
| 独立Feature Ready裁决 | PENDING |

结论：`IN_REVIEW / NOT_READY`。当前候选仅用于Feature Ready复审；不得修改INFRA、创建Technical Plan、同步NPDMS或开始Implementation。独立裁决必须明确批准或拒绝BR-FPLT001-006的最小API例外。
