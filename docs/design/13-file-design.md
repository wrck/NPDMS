# SDS Phase 2：文件设计

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8及批准增量`CHG-PRD-2026-08-27-004`
> Requirement ID：PLT-02，以及 PRE-02/04/05、SOL、EXE-01～04、IMP-01、ACC-01～04、ACC-06、CLO、CUT、INS、RES/SUB、INT-06/07/12 等使用文件和证据的正式需求
> Owner：基础平台 File Capability；业务含义、审核和归档状态仍由引用该文件的 Owner Context 持有
> 前置设计：`08-data-model.md`、`09-database-design.md`、`10-api-design.md`

## 1. 设计边界

- `FileArtifact` 是稳定文件身份，`FileVersion` 是不可变内容版本，`FileReference` 是业务对象对某版本的用途引用。
- 文件服务拥有二进制内容、存储定位、哈希、扫描状态和技术生命周期；IMP/ACC/CUT/INS 等领域拥有文件的业务含义、齐套、审核、批准和归档结论。
- 相同内容可以去重存储，但不同业务文件身份、版本和权限不能因哈希相同而合并。
- 业务表不得只保存可变 URL；保存 `artifactId + versionNo`，下载时生成短时受权地址。
- Word 文档正文不做内容级审计；文件上传、版本替换、引用、下载、审核、归档和失效等业务动作仍按权限留痕。
- 历史工单、工时及其附件不形成V1/V2业务文件入口。只有已纳入批准`AI-MIG-000`批次的真实来源，才可把原附件引用及校验信息保存为不可变来源载荷或受限迁移归档证据；不创建FileReference业务挂接、下载/预览/导出入口或新的文件权限。未来开放访问必须先经独立PRD/Feature变更批准。

## 2. 核心对象

| 对象 | 关键字段 | 不变量 |
|---|---|---|
| FileArtifact | artifactId、tenantId、name、categoryCode、ownerContext、creator | 稳定身份；创建后不因替换内容改变 |
| FileVersion | artifactId、versionNo、storageKey、hash、size、mediaType、scanStatus、creator | 内容不可变；版本号递增；哈希由服务端校验 |
| FileReference | context、objectType、objectId、purposeCode、artifactId、versionNo | 引用固定版本；实时继承业务对象权限 |
| UploadSession | sessionId、expected size/hash/type、expiresAt、parts、status | 过期后不可完成；只能绑定一个待提交版本 |
| FileAccessGrant | subject、artifact/version、operation、expiresAt、businessScopeHash | 短期、最小权限、不可转授权 |
| FileArchiveRecord | artifact/version、archiveBatch、archivedAt、businessDecisionRef | 追加写；归档版本不可替换 |

## 3. 文件生命周期

```text
初始化上传
→ 上传中
→ 已上传待校验
→ 哈希/大小/类型校验
→ 按部署配置执行安全扫描或记录SKIPPED
→ 提交为FileVersion
→ 绑定业务引用
→ 业务审核/批准
→ 归档或失效引用
```

技术状态和业务状态分离：`PASSED`只表示已执行且通过安全扫描，`SKIPPED`只表示部署未启用扫描；二者均不表示交付件审核通过。扫描开关变更不改写历史FileVersion；ACC归档不改变历史FileVersion内容。

## 4. 上传契约

### 4.1 初始化

`POST /pms/v1/files:init-upload` 请求包含业务上下文、对象、用途、文件名、大小、媒体类型、可选 SHA-256。服务端先校验业务对象存在、上传权限、文件策略和配额，再返回 UploadSession 与短时上传凭据。

### 4.2 完成

`POST /pms/v1/files/{artifactId}:complete-upload` 需携带 sessionId、服务端对象存储结果和幂等键。服务端校验实际大小、内容哈希、媒体嗅探类型和分片完整性；部署启用安全扫描时必须取得`PASSED`，关闭时不调用Provider并记录`SKIPPED`。满足适用校验后才创建不可变FileVersion。

### 4.3 限制

- NFR-01 定义单文件上传基线不超过 50MB；更大原始采集结果由外部受控存储保存引用，不通过普通文件上传接口。
- 文件扩展名、声明 MIME 和内容嗅探必须一致或进入隔离。
- 压缩包限制层数、展开后总大小和文件数，防止压缩炸弹；可执行内容按白名单策略处理。
- 前端不能直接指定永久 storageKey、租户目录或可公开访问权限。
- 安全扫描是平台级部署配置，默认关闭；关闭时Provider编码和版本为空，不得把`SKIPPED`展示或推导为`PASSED`。开启后Provider缺失、重复、异常、`ERROR/REJECTED`或未知结果均不得创建FileVersion。

## 5. 下载、预览与外发

下载/预览流程：认证 → 功能权限 → FileReference 对应业务对象的数据范围 → 文件版本状态 → 字段/敏感级别 → 生成短时单次或受限 URL。对象存储桶默认私有。

| 场景 | 规则 |
|---|---|
| 在线预览 | 仅允许支持的安全格式；转换产物是派生版本/缓存，不替代原文件 |
| 下载 | 返回 Content-Disposition 与原始哈希；记录业务对象、版本和结果 |
| 外发链接 | 短时令牌、指定对象/版本/操作，可撤销；访问仍校验身份或明确外部分享策略 |
| 回调文件 | 校验来源签名、业务幂等键、大小、哈希和任务范围 |
| 敏感附件 | 按字段/文件级权限，列表只展示掩码元数据 |

禁止返回对象存储永久公网 URL，禁止通过猜测 artifactId 绕过项目/设备/客户范围。

## 6. 版本、替换和失效

- 草稿业务文件可以通过“创建新 FileVersion”替换当前草稿引用；旧版本仍保留。
- 已提交/审核/批准/发布/归档引用不可原位替换；必须创建新业务 revision 并指向新 FileVersion。
- `detach` 只解除允许解除的业务引用，不删除 FileArtifact/FileVersion。
- 错传、病毒或合规问题通过隔离/失效状态阻止访问，并保留受控证据；不得覆盖内容以掩盖历史。
- 内容哈希相同的重传仍可形成新业务版本，但底层物理对象可安全去重。

## 7. 业务域文件规则

| Context | 文件用途 | Owner 与状态 |
|---|---|---|
| Platform Dynamic Form | `PmsFileArtifact`动态字段的上传、换版、解绑、预览和下载；业务Owner实例的归档/失效由F-PLT-001文件管理入口发起 | 手工实例由PLT校验创建者与功能权限，且不允许`ARCHIVE/INVALIDATE`；业务实例先委托消费Context的`DynamicFormBusinessObjectPolicyProvider`按`FILE_WRITE`校验Owner动作/scopeVersion，再进入F-PLT-001锁，F-PLT-001仍独立要求`pms:file:archive`。F-PLT-001拥有精确文件事实；普通上传URL/JSON不是受控证据 |
| Preparation/Solution | 工勘照片、需求附件、计划、交底书、方案 revision | SOL 拥有提交/批准状态；文件服务拥有内容版本 |
| Implementation Execution | 签收证据、安装照片、配置/联调结果、质量安全证据、DeliveryEvidence | IMP 上传和发布证据 revision |
| Acceptance & Closure | 培训、问卷、验收报告、齐套清单、归档包、交接证据 | ACC 审核、批准和归档引用；不覆盖 IMP 原版本；验收报告附件只冻结PLT稳定公共文件事实，不保存PLT内部主键 |
| Cutover | 方案、脚本引用、执行输出、验证和回退证据 | CUT 冻结批准方案版本和执行证据 |
| Inspection | 离线脚本/结果、报告和整改附件 | Inspection 规则/报告版本不可覆盖；外部结果保留来源 |
| Asset/Resource | 厂商凭证、RMA、服务商资质、转包/付款证据 | 业务 Owner 保存用途和有效期；敏感字段受控 |
| Integration | 对账导出、人工回填、UMC报告、采集结果 | 保存接口批次、来源键、哈希和回调证据 |

实施交付件链：`IMP DeliveryEvidenceRevision → FileVersion → ACC DeliveryArtifactReview → FileArchiveRecord`。任何环节都不复制二进制形成不可追溯副本。

F-ACC-001报告附件集合固定键为`ACC/ACCEPTANCE_REPORT_VERSION/{reportVersionId}/ACCEPTANCE_REPORT_ATTACHMENT`，归档集合固定使用`ACCEPTANCE_REPORT_ARCHIVE`；同一附件在两集合使用同一服务端UUID `referenceKey`。ACC Provider通过报告版本取得项目并调用`ProjectScopeApi`，把当前`treeVersion`作为`scopeVersion`：附件读/下载使用`PROJECT_VIEW`，上传/引用/替换/解绑使用`PROJECT_EDIT`，归档集合只允许ACC补偿消费者。报告表和事件仅保存附件ACTIVE公共事实。

上传、集合重验和下载复用PLT现有上传REST、`inspectReferenceSets/lockAndRevalidateReferenceSets`和Access Ticket REST；`attachExistingVersions`的目标白名单加性支持上述唯一ACC附件键并保留既有目标。归档由`archiveReferenceSets`显式携带发布时冻结的`publisherActorUserId`，PLT按该用户重验`pms:file:archive`及同租户FileBusinessScope，再重验完整附件ACTIVE集合，在独立归档集合创建ARCHIVED引用并追加`archivedBy`记录；报告附件引用持续保持`ACTIVE`供历史下载。后台不得伪造登录上下文或借用调度线程用户。PLT `FileArchiveRecord`是文件归档真值，ACC `archive_status`只是来源索引补偿投影。

## 8. 采集与巡检文件

- DAC 的原始大结果优先由现有采集平台/受控对象存储持有，平台保存 result reference、大小、hash、版本和访问范围。
- 需要纳入交付/报告的结果，经过来源校验后注册为 FileArtifact/FileVersion；注册不等于 IMP 解析或 Inspection 报告完成。
- 临时密码、凭证明文、认证头和私钥不得写入脚本文件、结果文件元数据、文件名、哈希旁路字段或转换日志。
- 离线巡检脚本下载冻结规则版本；上传结果绑定 taskId、ruleRevision 和设备范围，跨任务文件拒绝。

## 9. 存储键与完整性

【建议】物理 storageKey 使用不可猜测 ID 和版本，不包含客户名、项目名、用户名、设备密码或其他敏感业务文本。业务目录仅为展示元数据。

- 服务端计算 SHA-256；客户端哈希仅作提前校验。
- 对外部结果同时保存来源 hash 和平台校验 hash；不一致进入隔离。
- 对象存储开启服务端加密、私有访问、版本保护和传输 TLS；具体密钥、保留期和灾备在 Phase 3 安全/部署设计落位。
- 数据库提交 FileVersion 后若对象写入失败，版本保持不可用并由补偿任务清理/重试；不得返回成功。

## 10. 文件幂等与并发

| 场景 | 幂等/并发规则 |
|---|---|
| 初始化上传重试 | 同业务对象+purpose+Idempotency-Key 返回原 UploadSession |
| 完成上传重试 | sessionId + content hash 复用原 FileVersion；不同 hash 返回冲突 |
| 同时替换草稿 | FileReference version + If-Match，后到请求冲突 |
| 回调重复文件 | provider callbackId/resultVersion/hash 去重 |
| 归档重试 | archiveBatch + artifact/version 幂等 |
| 下载链接重放 | 按令牌用途和有效期限制；敏感下载可单次使用 |

## 11. 异常与补偿

| 异常 | 处理 |
|---|---|
| 上传中断/会话过期 | 分片在宽限期清理；不创建 FileVersion |
| 大小/哈希不符 | 隔离并返回稳定错误码，保留脱敏摘要 |
| 病毒/不安全类型 | 拒绝业务绑定；仅安全管理员可查看隔离证据 |
| 扫描关闭 | 继续大小、摘要、类型和策略校验；成功版本记录`SKIPPED`及空Provider事实，不表示扫描安全 |
| 扫描开启但Provider不可用 | 完成上传失败关闭，不降级为`SKIPPED` |
| 对象存在、数据库提交失败 | 补偿删除未引用对象或重新提交；按 session 幂等 |
| 数据库存在、对象丢失 | 标记不可用、告警、从存储版本/备份恢复；不伪造空文件 |
| 预览转换失败 | 原文件仍可按权限下载；预览显示失败，不改变审核状态 |
| 归档失败 | 业务保持待归档；已审核结论不伪装为已归档 |

### 11.1 F-ACC-002满意度文件边界

满意度Response使用两个持续ACTIVE集合：`ACC/SATISFACTION_RESPONSE/{responseId}/SATISFACTION_SIGNATURE`与`SATISFACTION_ATTACHMENT`；Result使用持续ACTIVE的`SATISFACTION_RESULT_DOCUMENT`和独立ARCHIVED的`SATISFACTION_ARCHIVE`。ACC只保存`artifactId/versionNo/referenceKey/artifactVersion/referenceVersion/availabilityVersion/scopeVersion/sha256`公共事实，历史下载始终重验ACTIVE集合并走Access Ticket。

`SATISFACTION_RESULT_DOCUMENT`只能由ACC通过`FileArtifactApi.createGeneratedBusinessFile`创建。PLT按Result形成时冻结责任人重验`pms:file:upload`和FileBusinessScope，复用现有FileUploadSession、内容策略、扫描、私有对象存储、Artifact/Version/Reference及审计；同Result恰一条ACTIVE结果文档。对象存储先行写入而外层事务回滚时，稳定operation会话保留重放/补偿入口：重试复用同一存储回执，放弃后删除未引用对象，不把孤立对象或第二文档暴露为业务事实。

统一导出文件目标固定为`PLATFORM/EXPORT_TASK/{taskId}/EXPORT_FILE`，只由`ExportTaskExecutionJob`在业务Provider完成实时权限与字段裁剪后生成。文件自成功起24小时有效；Access Ticket只发给原申请actor且下载时再次调用业务Provider重验。到期只删除内容并推进Task文件状态，FileArtifact公共事实、ExportTask及ExportAudit永久保留；不得把浏览器本地文件、同步响应流或ACC私有文件表作为第二导出文件真值。

客户受控链接上传不伪造用户。ACC以同一grant和最终提交`requestId`通过`PlatformCommandExecutionApi`一次性预留并重放唯一`responseId`；每个文件初始化由PLT返回服务端`fileSlotKey/fileSequence`。ACC策略Provider按grant→Questionnaire→Task验证授权版本、ACTIVE/有效期、预留Response、用途和项目范围，并从grant创建时`creator`冻结正数`grantIssuerUserId`。PLT仍执行内容大小、类型、扫描、存储、Artifact/Version/Reference和补偿；完成及最终提交前通过grant专用锁定重验实际文件事实，客户端句柄不能直接落库。PLT仅用issuer填充既有文件审计责任字段，detail明确`subjectType=BUSINESS_GRANT`及grant/response/slot身份，不建立SecurityContext、不把grant当用户或解释为客户拥有上传权限。现场协助使用现有认证上传。完整访问令牌、签字内容和文件正文不得进入日志。

Result归档复用F-ACC-001的双集合模型：结果文档、签字和附件ACTIVE引用保持可下载，归档集合创建同一公共文件事实的ARCHIVED引用和`FileArchiveRecord`。归档actor为Result形成时冻结并在执行时重验权限/范围的责任人；失败保持`PENDING_COMPENSATION`，不回滚或覆盖Result。

## 12. 审计、隐私和保留

审计上传初始化/完成、版本创建、替换、引用/解绑、下载/预览、分享、审核、归档、失效和管理操作；记录主体、对象、版本、业务上下文、结果、IP/客户端、requestId，不记录文件正文。

Word 文件不要求内容审计；Markdown 作为当前工程开发资料的主格式，其 Git 版本历史属于工程仓库治理，不由业务平台 FileArtifact 自动替代。业务文件的具体保留期限、法律保全和销毁策略需在 Phase 3 按组织制度登记，未登记前不得批量物理删除。

## 13. 文件门禁结论

| 门禁项 | 结论 | 落位 |
|---|---|---|
| 文件身份、版本和引用分离 | PASS | 第 2、6 节 |
| IMP 上传与 ACC 审核归档边界 | PASS | 第 7 节 |
| 权限不依赖可猜 URL | PASS | 第 5 节 |
| 外部/采集文件可校验 | PASS | 第 8、9 节 |
| 幂等、并发和补偿明确 | PASS | 第 10、11 节 |
| 保留期限和灾备数值 | DEFERRED_TO_PHASE_3 | Phase 3 按组织制度与部署方案登记；不构成 Phase 2 未决项 |

本分册满足 Phase 2 文件契约要求；Feature 实施前需为具体文件用途补充允许格式、敏感等级和存储策略配置清单。
