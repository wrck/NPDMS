# ADR-0035：文件技术存储回执适配例外

> 状态：`ACCEPTED`
> 日期：2026-08-26
> 适用基线：PRD V1.8；SDS Phase 1/2/3 `BASELINE`
> Requirement：`PLT-02（V1/P0，FR-PLT-008）`
> 关联Feature：`F-PLT-001`
> 批准裁决：`GO / NPDMS-FPLT001-INFRA-EXCEPTION-20260826-01-R1`

## 背景

锁定API设计规范把Yudao INFRA文件接口归为Platform API，要求完全遵循上游既有定义。现有`FileApi.createFile(...)`只返回访问URL，`presignGetUrl(...)`也以URL为输入，不能向PLT提供稳定的技术存储记录ID；`infra_file`又没有`config_id+path`唯一约束，不能由PLT根据path重建可靠身份。直接把URL、path或INFRA记录当作`FileArtifact`业务身份，会破坏PLT-02的稳定文件身份、不可变版本和固定版本引用。

原F-PLT-001候选提出“先预签名直传，再通过INFRA整段读取并由PLT完成校验”。该方案还存在明确边界缺陷：当前`FileClient.getContent(...)`返回完整`byte[]`，调用方只能在内存分配完成后检查50MB上限，无法在完整载入前拒绝超限对象。为此扩展所有存储客户端的HEAD或流式能力会扩大到Yudao文件基础实现，不是PLT-02首个正向闭环所必需。

## 决策

### 1. 例外范围

批准候选新增一个独立的Yudao INFRA技术接口`FileStorageReceiptApi`，作为`yudao-module-infra`对PMS模块开放的最小存储适配契约。该例外只允许新增接口、DTO和INFRA内适配实现：

- 不修改现有`FileApi`的签名、默认方法、行为或调用方；
- 不修改`FileClient`及其Local、DB、FTP、SFTP、S3实现；
- 不修改`yudao-framework`；
- 不允许PLT访问INFRA Mapper、DO、Service或`infra_file`表；
- 不把tenantId、业务对象、Artifact、Version、Reference、业务权限或永久公开ACL传入INFRA。

该接口仅提供三个场景化方法：

1. `store(FileStorageStoreCommand)`：接收由PLT生成的稳定`storageOperationId`，以及已完成大小、摘要、媒体类型和安全扫描校验的非空`byte[] content`、文件名和媒体类型。INFRA在专用保留目录下仅由`storageOperationId`生成跨配置一致的确定性path；每次调用先在INFRA内部按该保留path跨全部存储配置查询`infra_file`。结果为0条时才冻结本次取得的当前master并复用现有FileClient上传、登记；结果为1条时按该记录冻结的`configId`返回已有回执，不重复创建技术对象；结果超过1条时失败关闭并登记存储对账，禁止任选一条或向当前master再创建。回执至少包含`storageOperationId/infraFileId/name/mediaType/sizeBytes`。PLT只持久化`infraFileId`作为技术定位，不保存URL、configId或storagePath。PLT必须以已提交的UploadSession行锁保证同一`storageOperationId`单飞，不能把该ID开放为普通HTTP自报字段。
2. `presignGet(FileStorageAccessQuery)`：按`infraFileId+expirationSeconds`读取INFRA自身记录并生成短时访问URL，返回`url/expiresAt`；URL只存在于本次响应。
3. `delete(FileStorageDeleteCommand)`：按`storageOperationId`生成同一保留path并跨全部存储配置查询；0条视为补偿完成，1条按记录冻结的`configId+path`幂等删除，超过1条失败关闭并登记存储对账，禁止任选一条。该方法只允许PLT在确认没有已提交FileVersion引用后用于本次上传失败补偿。

`FileStorageReceiptApi`不成为PLT业务API，也不改变`infra_file`的技术存储记录语义。PLT的`FileArtifact/FileVersion/FileReference`仍是唯一业务真值。

### 2. 上传上限与内容校验

F-PLT-001 V1不采用浏览器直传后再整文件回读。上传改为PMS业务HTTP入口接收后端`multipart/form-data`：

- 服务端multipart配置把单文件上限固定为50MB，请求在进入业务服务前拒绝超限；
- PLT从`MultipartFile`输入流按不超过`50MB+1字节`有界读取，超过50MB立即拒绝，不先调用`getBytes()`分配完整未知对象；
- 只有读取完成且实际大小、服务端摘要、扩展名、声明MIME、内容嗅探MIME和安全扫描全部通过后，才把已验证且不超过50MB的`byte[]`交给`FileStorageReceiptApi.store(...)`；
- 未知长度、截断、读取异常、超限、类型冲突或扫描失败均不调用INFRA存储。

因此技术存储边界只接收已经受控的有限内容，不新增`readStorageObject`，也不要求调用方在完整载入存储对象后才判断上限。大于50MB和外部直传继续不在本Feature范围内。

### 3. 原子性、重放与补偿

- 初始化命令先提交UploadSession；`sessionId`同时作为本次受信`storageOperationId`。实际上传命令锁定该会话、完成内容校验，再声明平台幂等命令并调用`store`；FileVersion提交前只存在技术回执，不存在可用业务文件事实。
- `store`的跨配置确定性path、全配置查询与UploadSession行锁共同保证同一操作顺序重试返回同一INFRA技术记录；master在首次成功与重试之间切换时仍命中原记录冻结的configId。平台同一幂等键重放已成功结果时直接返回已保存的PLT结果，不再次调用`store`。
- INFRA成功而PLT事务失败时，已提交的UploadSession仍保留`storageOperationId`。再次提交先由`store`找回同一回执；确认不再重试时，PLT只在证明该operation未被任何已提交FileVersion使用后调用`delete(storageOperationId)`。补偿重试幂等，不能删除其他上传或既有版本对象。
- 进程在INFRA成功而PLT事务尚未提交时异常退出，不会丢失补偿定位：过期UploadSession对账按其`storageOperationId`检查无FileVersion引用后执行同一删除补偿。该对账不把技术对象解释为业务文件。
- 短时访问每次实时重验PLT权限、业务范围和版本可用性后才调用`presignGet`；短时URL不进入PLT表、审计快照或事件。

### 4. 依赖方向

依赖固定为：

`pms-module-platform -> yudao-module-infra-api FileStorageReceiptApi -> yudao-module-infra existing FileService/FileClient`

INFRA不依赖PLT，不理解租户文件业务、用途、审批或业务范围。SOL等消费者只依赖`pms-module-platform-api`，不得直接调用INFRA技术接口。

## 备选方案

### 继续扩展既有FileApi

不采用。既有接口是上游Platform API；直接追加PLT专用票据、读取、注册和补偿方法会让原接口同时承载URL式旧语义与PLT技术回执语义，并形成上位规范冲突。

### 预签名直传后整文件读取

不采用。现有客户端只能返回完整`byte[]`，不能在完整载入前执行50MB上限；补齐所有存储实现的流式/HEAD能力超出当前正向闭环。

### PLT直访infra_file或自行实现对象存储客户端

不采用。前者违反模块所有权，后者重复Yudao存储配置和客户端能力。

### 只保存URL

不采用。URL可变、可过期且可能带签名，不能承担文件业务身份或稳定技术定位。

## 后果与门禁

- 本ADR已由`NPDMS-FPLT001-INFRA-EXCEPTION-20260826-01-R1`独立批准；该GO只允许F-PLT-001引用本例外继续Feature Ready，不代表Feature Ready或Implementation通过。
- F-PLT-001 Feature Spec和物理契约必须引用本ADR，删除原`FileApi`前向加法及直传后回读方案，明确后端有界上传、技术回执、短时访问和补偿。
- Technical Plan必须验证：现有`FileApi`兼容不变；50MB以内正向上传；超过上限在调用INFRA前拒绝；store成功但PLT回滚时仅补偿无引用对象；首次store成功后切换master，重试仍返回原configId回执且补偿仍删除原对象；同一保留path出现多条记录时store/delete均失败关闭并进入对账；短时访问不持久化URL。
- 该例外不批准修改`infra_file`表结构、增加跨模块外键、Deployment、SIT、UAT或Release。
