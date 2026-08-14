# SDS Phase 2：文件设计

> 文档状态：`BASELINE`
> 适用基线：PRD V1.7（`docs/baseline/prd-v1.7.md`）
> Requirement ID：PLT-02，以及 PRE-02/04/05、SOL、EXE-01～04、IMP-01～02、ACC-01～06、CLO、CUT、INS、RES/SUB、INT-06/07/12 等使用文件和证据的正式需求
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
→ 安全扫描
→ 提交为FileVersion
→ 绑定业务引用
→ 业务审核/批准
→ 归档或失效引用
```

技术状态和业务状态分离：安全扫描通过只表示内容技术可用，不表示交付件审核通过；ACC 归档不改变历史 FileVersion 内容。

## 4. 上传契约

### 4.1 初始化

`POST /pms/v1/files:init-upload` 请求包含业务上下文、对象、用途、文件名、大小、媒体类型、可选 SHA-256。服务端先校验业务对象存在、上传权限、文件策略和配额，再返回 UploadSession 与短时上传凭据。

### 4.2 完成

`POST /pms/v1/files/{artifactId}:complete-upload` 需携带 sessionId、服务端对象存储结果和幂等键。服务端校验实际大小、内容哈希、媒体嗅探类型、分片完整性和扫描状态；成功才创建不可变 FileVersion。

### 4.3 限制

- NFR-01 定义单文件上传基线不超过 50MB；更大原始采集结果由外部受控存储保存引用，不通过普通文件上传接口。
- 文件扩展名、声明 MIME 和内容嗅探必须一致或进入隔离。
- 压缩包限制层数、展开后总大小和文件数，防止压缩炸弹；可执行内容按白名单策略处理。
- 前端不能直接指定永久 storageKey、租户目录或可公开访问权限。

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
| Preparation/Solution | 工勘照片、需求附件、计划、交底书、方案 revision | SOL 拥有提交/批准状态；文件服务拥有内容版本 |
| Implementation Execution | 签收证据、安装照片、配置/联调结果、质量安全证据、DeliveryEvidence | IMP 上传和发布证据 revision |
| Acceptance & Closure | 培训、问卷、验收报告、齐套清单、归档包、交接证据 | ACC 审核、批准和归档引用；不覆盖 IMP 原版本 |
| Cutover | 方案、脚本引用、执行输出、验证和回退证据 | CUT 冻结批准方案版本和执行证据 |
| Inspection | 离线脚本/结果、报告和整改附件 | Inspection 规则/报告版本不可覆盖；外部结果保留来源 |
| Asset/Resource | 厂商凭证、RMA、服务商资质、转包/付款证据 | 业务 Owner 保存用途和有效期；敏感字段受控 |
| Integration | 对账导出、人工回填、UMC报告、采集结果 | 保存接口批次、来源键、哈希和回调证据 |

实施交付件链：`IMP DeliveryEvidenceRevision → FileVersion → ACC DeliveryArtifactReview → FileArchiveRecord`。任何环节都不复制二进制形成不可追溯副本。

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
| 对象存在、数据库提交失败 | 补偿删除未引用对象或重新提交；按 session 幂等 |
| 数据库存在、对象丢失 | 标记不可用、告警、从存储版本/备份恢复；不伪造空文件 |
| 预览转换失败 | 原文件仍可按权限下载；预览显示失败，不改变审核状态 |
| 归档失败 | 业务保持待归档；已审核结论不伪装为已归档 |

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
| 保留期限和灾备数值 | IN_REVIEW | Phase 3 按组织制度与部署方案登记 |

本分册满足 Phase 2 文件契约要求；Feature 实施前需为具体文件用途补充允许格式、敏感等级和存储策略配置清单。
