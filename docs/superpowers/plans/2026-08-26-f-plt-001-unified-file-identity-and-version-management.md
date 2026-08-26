# F-PLT-001 统一文件身份与版本管理 Implementation Plan

> **执行要求：** 使用 `superpowers:executing-plans` 按主线依赖推进；用户已明确禁用TDD，因此每个Task先完成当前最小实现，再执行与风险相称的自动化、真实MySQL、对象存储或浏览器验证。每个Task独立复审并按情况本地提交，不推送。

**Goal:** 以PLT拥有的FileArtifact、不可变FileVersion和固定版本FileReference建立统一文件业务真值，先闭合F-SOL-001客户延期材料的“上传—冻结—提交审批—终态重验”正向链，再补换版、解绑、失效和归档分支。

**Architecture:** `pms-module-platform`持有六张`plt_file_*`表、HTTP命令、业务API、幂等/审计/Outbox及文件Provider编排；`pms-module-platform-api`只暴露稳定文件事实和业务对象策略SPI；`yudao-module-infra`仅按已批准ADR-0035新增独立`FileStorageReceiptApi`技术回执适配，不改变既有`FileApi`、`FileClient`和`infra_file`语义；`pms-module-engineering`实现首个SOL业务Provider并冻结精确引用事实。模块依赖固定为PLATFORM→INFRA公开API、业务Owner→PLATFORM API契约，禁止跨模块表访问。

**Tech Stack:** Java 25、Spring Boot、MyBatis/MyBatis-Plus、MySQL 8.4/Flyway、Yudao FileClient、ClamAV INSTREAM安全扫描适配、Vue 3、TypeScript、Element Plus、Vitest、Docker Compose。

**Specification:** `specs/features/F-PLT-001-unified-file-identity-and-version-management.md`、`specs/features/F-PLT-001-physical-contract.json`、`docs/decisions/0035-file-storage-receipt-adapter-exception.md`；锁定规格提交`2efd8c476430d77ce2003c6e9fe300a335eac6a7`；Feature Ready裁决`NPDMS-FPLT001-FEATURE-READY-20260826-01-R2`。

## Global Constraints

- 受管规格快照只由同步工具维护；实施期间不直接修改`specs/`、`docs/specification-baseline/manifest.json`或已执行的V1～V91迁移。
- `specs/001-project-delivery-platform/`只作历史证据，不作为实施校验门禁；当前Feature只消费锁定V1.8快照、ADR和当前Task。
- 当前工作树内完成，不创建第二工作树，不带入其他工作树的Feature、数据库名、端口或计划参数。
- 不修改`yudao-framework/**`、既有`FileApi`/`FileClient`签名或`infra_file`DDL。ADR-0035例外只允许独立`FileStorageReceiptApi`及其INFRA内部适配实现。
- 现有Spring/Yudao Multipart正向上传继续复用；只把应用级`max-file-size`前向调整为50MB，并给`max-request-size`保留表单开销。PLT在完整分配前以50MB+1有界读取，不使用`MultipartFile.getBytes()`处理未知大小。
- 新增Mapper查询遵守`docs/coding/database-query-interface.md`：除主键/稳定唯一键外只接收单一场景Query；复杂、批量和锁定读进入XML；禁止长位置参数、Map、SQL注解、`${}`、`.last(...)`及空集合放宽。
- 受信租户沿用项目已批准的配置感知模式：已有上下文直接使用；仅`yudao.tenant.enable=false`且上下文为空时在HTTP调用范围建立tenantId=0；启用或配置缺失却无上下文时失败关闭。不得修改租户基础框架或接受请求自报tenantId。
- 所有文件写命令依次校验功能权限、业务Provider、用途策略、scopeVersion和文件CAS；前端按钮不是权限真值。Provider动作值域只接受锁定的九个稳定动作。
- 内容SHA-256是锁定业务事实；除既有幂等请求摘要、访问token摘要和受管基线外，不增加额外hash/checksum/fingerprint。
- 文件事件只允许`FileVersionCommitted/FileReferenceAttached/FileReferenceDetached/FileArchived`。业务事实与Outbox同事务；不得新增无消费者事件或把异步投递成功作为业务提交条件。
- 前端优先复用Yudao `UploadFile`、Table、Descriptions、Timeline、Dialog/Drawer和权限组件；不足时使用Element Plus结构和主题变量。支持320/768/1024/1440，不堆叠内联样式。
- 实施前只做一次针对性旧值差距核对：Owner、状态、权限、事务和API同时满足才拷贝复用；已有实现不自动视为完成。先完成正向主链，换版/解绑/失效/归档和单一异常分支后置，但任何阻断主线的数据约束、权限、事务或补偿必须在主线Task内闭合。
- 每个Task完成实现、计划内验证和自审后送独立Implementation Done复审；GO后回写Task为PASS并按情况本地提交。自测不能代替独立GO。

## Current Implementation Audit

1. `yudao-module-infra`已有`FileApi.createFile(byte[]) -> URL`、`presignGetUrl(URL)`和`FileService/FileClient`，可复用存储配置及客户端，但URL、path、configId均不能作为PLT业务身份；当前没有按稳定operation跨配置重放的回执接口。
2. `infra_file`只有主键，且master可运行时切换。ADR-0035要求保留path仅由`storageOperationId`生成，并在store/delete前跨全部配置查询；0条创建、1条复用冻结configId、多条失败关闭。
3. 当前应用Multipart为16MB/32MB；50MB内正向上传无需新协议或分片，只需应用配置前向调整和PLT有界读取。
4. `pms-module-platform`已有幂等、`plt_operation_audit`和Outbox。`PlatformCommandExecutionApi.SuccessFacts`目前最多携带一个事件，而首次上传需同事务写VersionCommitted和ReferenceAttached，必须做PMS平台内的向前兼容多事件加法，保留旧调用构造和访问语义。
5. PLATFORM当前没有文件DO、Mapper、HTTP或Provider注册表；新能力必须留在既有platform模块，不另建无消费者模块。
6. 仓库没有生产安全扫描Provider。采用窄的`FileSecurityScanProvider` SPI和PLT内ClamAV INSTREAM适配；未配置、超时、协议异常或非`PASSED`均失败关闭。正式实例和密钥仍属Deployment，本Feature只交付可配置适配及本地验收能力。
7. F-SOL-001已在`sol_construction_plan_change`和前后端DTO预留`customerEvidenceFileId/customerEvidenceFileVersion`，但缺少`referenceKey/fileFactVersion/scopeVersion`，且`requireFileArtifact()`仍固定失败关闭。必须以前向迁移和公共API接入替换该占位，不创建SOL文件表或URL兼容字段。
8. `ProjectDurationFormDrawer.vue`已完成响应式工期草稿主表单，CUSTOMER_DELAY目前只显示“尚未接入”警告。应嵌入共享文件组件并保留现有字段存在性PATCH，不重写工期审批UI。

## Mainline Dependency Order

`V92/V93物理基础 → INFRA技术回执 → PLT公共契约与持久化 → 上传会话 → 完成上传/精确引用 → SOL冻结并提交审批 → 查询与短时访问 → 分支治理 → 共享UI与全链验收`。

Task 1～6优先形成首个真实生产者和首个真实消费者；Task 7～8再补访问及换版/解绑/失效/归档。任何不影响上述正向链的异常处理只在其所属后置Task验证，不倒置主线。

---

### Task 1: 建立PLT文件物理模型、种子和Feature工作单

**Files:**
- Create: `sql/migrations/V92__fplt001_file_artifact.sql`
- Create: `sql/migrations/V93__fplt001_file_seed.sql`
- Create: `tasks/features/F-PLT-001.md`
- Modify: `yudao-server/src/main/resources/application.yaml`
- Modify: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/enums/ErrorCodeConstants.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactMigrationContractTest.java`

**Consumes:** 六表机器物理契约、三类版本事实、六项功能权限、文件类型/敏感级别/策略配置。

- [ ] **Step 1: 创建六张前向表**

V92按物理契约创建`plt_file_artifact/version/reference/upload_session/access_grant/archive_record`。所有PLT内部引用使用含`tenant_id`的复合键；`infra_file_id`、业务对象键不建跨模块外键。Artifact无currentVersion指针，Version无更新内容入口，Reference唯一键精确包含非空`reference_key`。

- [ ] **Step 2: 写确定性种子和应用上传配置**

V93幂等写文件类别、敏感级别、用途策略和六项权限菜单，不自动授予新角色；按锁定规格提供覆盖首个SOL用途及精确/部分限定、优先级让位、无匹配、停用不参与的示例策略。V93同时按既有`infra_job`种子模式幂等注册唯一启用的`fileOutboxDeliveryJob`，稳定handlerName为`fileOutboxDeliveryJob`、cron为`0/30 * * * * ?`，不新增第二套调度机制。将`application.yaml`单文件上限改为50MB、请求上限改为52MB，不修改Spring/Yudao解析链。

- [ ] **Step 3: 建立错误码与Feature工作单**

新增稳定错误码覆盖参数、Provider、策略、大小/类型/摘要/扫描、会话、版本、引用、访问、归档、存储冲突和补偿。`tasks/features/F-PLT-001.md`记录Feature Ready PASS、规格提交、Technical Plan状态、Task 1～10和F-SOL-001解除阻断点。

- [ ] **Step 4: 实施后验证并提交**

执行空库V1→V93、六表字段/索引/复合外键、精确Reference唯一键、50MB配置和种子幂等验证；断言`infra_job`中该handler恰一、status=1且Quartz可解析cron。Task 5创建Job Bean后再完成handler解析和自动触发验收。确认V1～V91未变。运行迁移契约测试、platform编译、`git diff --check`。

Expected: 物理契约与迁移PASS。提交：`feat(platform): 建立统一文件物理基础`

---

### Task 2: 实现已批准的INFRA技术存储回执适配

**Files:**
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/FileStorageReceiptApi.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/dto/FileStorageStoreCommand.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/dto/FileStorageReceipt.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/FileStorageReceiptApiImpl.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/mysql/file/query/FileStorageOperationLookupQuery.java`
- Modify: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/mysql/file/FileMapper.java`
- Create: `yudao-module-infra/src/main/resources/mapper/file/FileMapper.xml`
- Test: `yudao-module-infra/src/test/java/cn/iocoder/yudao/module/infra/api/file/FileStorageReceiptApiImplTest.java`
- Test: `yudao-module-infra/src/test/java/cn/iocoder/yudao/module/infra/api/file/FileStorageReceiptMySqlIntegrationTest.java`

**Consumes:** ADR-0035；复用`FileConfigService`、`FileClient`和`FileDO`，不经PLT直访INFRA内部。

- [ ] **Step 1: 固化窄公开契约**

`store(command)`输入非空有限content、规范文件名、mediaType和受信storageOperationId，输出operationId/infraFileId/name/mediaType/sizeBytes；`presignGet(infraFileId,expirationSeconds)`按记录冻结configId/path；`delete(storageOperationId)`只删除精确技术对象。DTO不出现tenantId、Artifact或业务权限。

- [ ] **Step 2: 实现跨配置确定性重放**

保留path只由operationId编码到专用目录，跨配置查询全部`infra_file`：0条冻结当前master并上传登记；1条核对回执后复用；多条抛稳定冲突并记录可检索错误，禁止任选。delete使用同一查询；先删除冻结configId上的对象，再删除对应技术记录。

- [ ] **Step 3: 实施后验证并提交**

验证首次store、同操作重放、首次成功后master A→B切换仍返回A、补偿切换后仍删除A、多记录失败关闭、presign使用冻结配置、对象/DB失败边界。确认`FileApi`、`FileClient`和`infra_file`DDL无变化。

Expected: INFRA API及真实存储配置回归PASS。提交：`feat(infra): 提供文件存储回执适配`

---

### Task 3: 定义PLT公共文件契约、Provider注册与持久化原语

**Files:**
- Modify: `pms-module-platform/pom.xml`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/FileArtifactApi.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/FileBusinessObjectPolicyProvider.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/FileSecurityScanProvider.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/dto/FileArtifactVersionQuery.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/dto/FileArtifactVersionRevalidationQuery.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/dto/FileArtifactVersionFact.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/dto/FileFactVersion.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/dto/FileBusinessObjectPolicyQuery.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/dto/FileBusinessObjectPolicyRevalidationQuery.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/dto/FileBusinessObjectPolicyFact.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/dto/FileSecurityScanCommand.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/dto/FileSecurityScanResult.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileBusinessObjectPolicyRegistry.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/file/FileArtifactDO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/file/FileVersionDO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/file/FileReferenceDO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/file/FileUploadSessionDO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/file/FileAccessGrantDO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/file/FileArchiveRecordDO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/file/FileArtifactMapper.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/file/FileVersionMapper.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/file/FileReferenceMapper.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/file/FileUploadSessionMapper.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/file/FileAccessGrantMapper.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/file/FileArchiveRecordMapper.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/file/query/ExactFileReferenceQuery.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/file/query/FileArtifactLockQuery.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/file/query/FileVersionLockQuery.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/file/query/FileReferenceLockQuery.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/file/query/FileUploadSessionLockQuery.java`
- Create: `pms-module-platform/src/main/resources/mapper/file/FileArtifactMapper.xml`
- Create: `pms-module-platform/src/main/resources/mapper/file/FileVersionMapper.xml`
- Create: `pms-module-platform/src/main/resources/mapper/file/FileReferenceMapper.xml`
- Create: `pms-module-platform/src/main/resources/mapper/file/FileUploadSessionMapper.xml`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileContractAndMapperTest.java`

**Consumes:** 公共API第4.1节、六表、数据库查询规则；本Task只建立可用原语，不提前开放写HTTP。

- [ ] **Step 1: 封闭API与动作值域**

定义`inspect`、`lockAndRevalidate`、业务Provider inspect/revalidate及扫描SPI。两类FileArtifact查询都要求同一非空referenceKey；重验输入必须包含`artifactId/versionNo/referenceKey/expectedFileFactVersion/expectedScopeVersion`。动作构造边界只接受九个稳定值。platform实现模块只新增对公开`yudao-module-infra`的依赖，不访问INFRA内部Service/Mapper/DO。

- [ ] **Step 2: 实现唯一Provider解析**

Registry按ownerContext+objectType精确匹配：0个、多个、未知动作、空范围、越租户和异常均失败关闭。Provider响应冻结用途策略、可变性和scopeVersion，不携带业务正文。

- [ ] **Step 3: 实现场景Mapper和锁顺序**

Mapper只显式暴露insert、稳定唯一查询、稳定游标、CAS及ForUpdate当前读；不继承可绕过不可变/CAS的通用写入口。锁序固定为Provider业务事实→Artifact→精确Version→完整稳定键Reference→Session，版本变化返回冲突。

- [ ] **Step 4: 实施后验证并提交**

覆盖精确槽位、多槽位、空referenceKey、空/多Provider、跨租户、空集合、版本组成变化、锁定顺序和Mapper方法集合。运行API/Mapper测试与platform-api依赖边界检查。

Expected: 公共契约和持久化原语PASS。提交：`feat(platform): 定义统一文件公共契约`

---

### Task 4: 实现上传初始化与内容校验/安全扫描链

**Files:**
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/file/FileArtifactController.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/file/vo/FileUploadInitReqVO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/file/vo/FileUploadInitRespVO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileUploadApplicationService.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/command/*Command.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/BoundedMultipartReader.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/ClamAvFileSecurityScanProvider.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileContentPolicyService.java`
- Modify: `compose.yaml`
- Modify: `yudao-server/src/main/resources/application-local.yaml`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileUploadInitializationServiceTest.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileContentValidationTest.java`

**Consumes:** Task 3 Provider/Session原语；初始化不返回直传凭据，完成上传尚不在本Task提交Version。

- [ ] **Step 1: 实现初始化正向链**

`POST /api/v1/pms/files:init-upload`解析受信tenant/actor、`pms:file:upload`、业务Provider `UPLOAD/REPLACE`、策略和可选expectedReferenceVersion，使用平台幂等创建Session。sessionId同时作为storageOperationId，只返回artifactId/sessionId/expiresAt。

- [ ] **Step 2: 实现50MB有界读取与内容事实**

从Multipart输入流按固定缓冲读取到50MB+1，超限在调用INFRA前拒绝。形成实际size、服务端SHA-256、声明/嗅探MIME和扩展名一致性；客户端摘要只比对。压缩包只按锁定策略允许的类型及界限检查，不实现大文件分片。

- [ ] **Step 3: 实现ClamAV生产适配**

通过配置的host/port/timeout调用INSTREAM并封闭解析`PASSED/REJECTED/ERROR`，返回providerCode/version/reasonCode。Compose只增加本地扫描基础设施用于实施验收；未配置或不可达失败关闭，不提供“默认通过”实现。

- [ ] **Step 4: 实施后验证并提交**

实现后覆盖初始化幂等/冲突、Provider失败、50MB成功、50MB+1前置拒绝、大小/摘要/MIME不符、扫描通过/EICAR拒绝/异常；确认失败没有Version/Reference/INFRA调用。

Expected: 会话与内容校验链PASS。提交：`feat(platform): 建立受控文件上传会话`

---

### Task 5: 闭合首次上传、版本提交和精确引用正向事务

**Files:**
- Modify: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/command/PlatformCommandExecutionApi.java`
- Modify: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/command/PlatformCommandExecutionApiImpl.java`
- Modify: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/outbox/PlatformOutboxDeliveryApiImpl.java`
- Modify: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileUploadApplicationService.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/file/vo/FileUploadCompleteRespVO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileArtifactApiImpl.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/event/FileEventFactory.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/event/FileVersionCommittedMessage.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/event/FileReferenceAttachedMessage.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/event/FileReferenceDetachedMessage.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/event/FileArchivedMessage.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/event/FileOutboxDeliveryJob.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/command/PlatformCommandMultipleEventsTest.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/outbox/PlatformOutboxDeliveryApiImplTest.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileOutboxDeliveryJobTest.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileUploadMySqlIntegrationTest.java`

**Consumes:** Tasks 2～4；这是首个真实生产者主线。

- [ ] **Step 1: 向前兼容平台多事件事实**

为`SuccessFacts`增加不可变`List<BusinessEvent>`。`FileEventFactory`是四类新文件事件eventId的唯一生成方：一次生成稳定eventId，并用同一值构造`BusinessEvent.eventId`与不可变payload；`PlatformCommandExecutionApiImpl`只校验并原样持久化，不为列表事件重新生成。保留旧七参数构造、`eventType()/eventPayload()`访问及旧单事件由实现生成eventId的既有行为，旧调用无需批量修改。实现按列表写0..N条Outbox，拒绝null、空类型/载荷、重复eventId和payload不一致。

- [ ] **Step 2: 实现完成上传同事务提交**

`POST /files/{artifactId}:complete-upload`锁Session并重验业务scope与Reference CAS，完成内容校验后调用`FileStorageReceiptApi.store`，再在PlatformCommand事务中创建/激活Artifact、Version 1、精确Reference、完成Session、幂等成功、完整安全审计和两个Outbox。两个eventId均由`FileEventFactory`生成一次并同时写入BusinessEvent和最终不可变payload。

- [ ] **Step 3: 实现公共inspect/revalidate**

`inspect`返回精确referenceKey及`artifactVersion/referenceVersion/availabilityVersion/scopeVersion`；`lockAndRevalidate`按固定锁序核验同一槽位的artifact/version/status/referenceVersion，任何变化失败关闭。只返回业务文件事实，不返回infraFileId、path或URL。

- [ ] **Step 4: 实现补偿边界**

INFRA成功而PLT回滚时保留Session与同一operationId供重试找回回执；仅在Session确认终止且没有任何已提交Version引用时调用delete补偿。不得在事务异常中删除可能已被已提交Version使用的对象。

- [ ] **Step 5: 建立四类文件事件生产投递链**

把四类锁定文件事件加入`PlatformOutboxDeliveryApiImpl.SUPPORTED_EVENT_TYPES`，不增加第五类。`FileOutboxDeliveryJob`沿用现有`JobHandler + @TenantJob + PlatformOutboxDeliveryApi`机制，并与Task 1 V93注册的稳定handlerName完全一致；Quartz每30秒触发后按封闭集合领取到期事件，逐类反序列化并核验eventId/tenantId/最小载荷，再发布对应本地不可变Message。发布成功才`markDelivered`，异常按现有指数退避调用`scheduleRetry`。Task 8只生产Detached/Archived并复用本链，不另建第二套投递器；本Feature不臆造通知、收件人或业务消费者。

- [ ] **Step 6: 实施后验证并提交**

真实MySQL+存储+扫描覆盖首次上传、同键重放、异载荷冲突、并发完成单胜、两个事件恰一、审计恰一、各故障点回滚/重试、master切换找回同一回执，以及inspect/revalidate精确槽位。事件验证至少包含空库迁移后Quartz无需人工调用即触发Job、发布失败→到期重领→使用同一eventId成功、业务文件事实不重复、旧单事件构造/领取/投递兼容和未知第五类仍被拒绝。

Expected: 首次上传至可冻结引用主线PASS。提交：`feat(platform): 提交文件版本与精确引用`

---

### Task 6: 接入首个SOL消费者并解除材料主线阻断

**Files:**
- Create: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/ConstructionPlanChangeFilePolicyProvider.java`
- Create: `sql/migrations/V94__fsol001_file_artifact_freeze.sql`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/constructionplan/ConstructionPlanChangeDO.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/constructionplan/ConstructionPlanChangeMapper.java`
- Modify: `pms-module-engineering/src/main/resources/mapper/constructionplan/ConstructionPlanChangeMapper.xml`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/constructionplan/DurationChangeApplicationService.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/vo/DurationChangeCreateReqVO.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/constructionplan/vo/DurationChangePatchReqVO.java`
- Test: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/constructionplan/DurationChangeFileArtifactMySqlIntegrationTest.java`

**Consumes:** F-SOL-001现有PROJ锁定重验/BPM事务和Task 5 FileArtifact API；不新增SOL审批接口或状态。

- [ ] **Step 1: 前向补齐SOL冻结字段**

V94对`sol_construction_plan_change`前向增加`customer_evidence_reference_key`、三段fileFactVersion和`customer_evidence_scope_version`可空字段；无材料记录保持NULL，材料必填提交前必须完整。不得修改V90～V93。

- [ ] **Step 2: 实现SOL业务Owner Provider**

Provider只支持`SOL/CONSTRUCTION_PLAN_CHANGE/CUSTOMER_DELAY_EVIDENCE`。复用现有ConstructionPlan/change和ProjectParticipantFact/ProjectScope事实，返回对象存在、项目范围、当前角色、用途单槽策略、MUTABLE/IMMUTABLE和scopeVersion；不写PRE-01状态。

- [ ] **Step 3: 替换固定失败占位并冻结文件事实**

草稿创建/PATCH接收artifactId/versionNo/referenceKey；提交CUSTOMER_DELAY时先inspect，再在既有PROJ→SOL锁序内调用`lockAndRevalidate`，把同一精确引用及file/scope版本冻结到change。已进入PENDING_APPROVAL后文件换版/解绑由Provider返回IMMUTABLE；BPM approve/reject/cancel继续走已有同步事务并对冻结文件事实重验，不改变既有三轴状态。

- [ ] **Step 4: 实施后验证并提交**

真实PROJ+SOL+PLT+MySQL+Flowable验证上传客户材料、创建/PATCH草稿、提交进入审批、审批通过/驳回/撤回；引用或范围变化时BPM与SOL共同回滚。确认无材料原因仍沿现有主线，PLT不持有审批状态，F-SOL Task 6/7/9材料阻断可关闭。

Expected: 首个真实消费链PASS。提交：`feat(engineering): 冻结工期变更文件事实`

---

### Task 7: 实现文件查询、版本历史和短时访问

**Files:**
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileQueryService.java`
- Modify: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/file/FileArtifactController.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/file/vo/*RespVO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileAccessTicketService.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileQueryAndAccessMySqlIntegrationTest.java`

**Consumes:** Task 5可用文件事实；访问URL只在响应中短时出现。

- [ ] **Step 1: 实现稳定查询**

完成Artifact详情、Version按`versionNo,id`游标、Reference按完整业务稳定键查询。功能权限与Provider READ实时收窄；空范围返回空，不返回infra定位。

- [ ] **Step 2: 实现下载/预览授权**

重验DOWNLOAD/PREVIEW、精确Reference、Version可用性和scopeVersion，创建短期AccessGrant，仅持久化token摘要，再调用INFRA presignGet返回短时URL。失效、过期、撤销或不支持预览失败关闭并写拒绝审计。

- [ ] **Step 3: 实施后验证并提交**

覆盖稳定分页、多槽位、权限变化、私有URL短时性、失效阻断、token不落明文及跨租户空结果。

Expected: 元数据、历史和访问主线PASS。提交：`feat(platform): 提供文件查询与短时访问`

---

### Task 8: 完成换版、解绑、删除、失效和归档分支

**Files:**
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileLifecycleApplicationService.java`
- Modify: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/file/FileArtifactController.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/command/*LifecycleCommand.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileLifecycleMySqlIntegrationTest.java`

**Consumes:** 同一Artifact追加版本与Reference CAS；不改变业务Owner审批状态。

- [ ] **Step 1: 实现草稿换版和重新绑定**

ADD_VERSION/REPLACE仍走Task 4～5上传链，同Artifact锁定分配递增versionNo；Reference使用If-Match单胜，DETACHED槽位按同一稳定键恢复ACTIVE，不创建第二槽位。旧Version不可变。

- [ ] **Step 2: 实现detach和未引用草稿删除**

Provider必须返回MUTABLE；detach只推进Reference状态并发`FileReferenceDetached`。删除仅允许没有活动/归档/冻结引用的DRAFT Artifact，逻辑删除且ID/版本不复用。

- [ ] **Step 3: 实现失效/不可用恢复和归档**

INVALIDATE递增artifactVersion或availabilityVersion并阻断新引用/访问；对象恢复只递增availabilityVersion，不改摘要。ARCHIVE按batch+artifact+version幂等追加记录、推进Reference并发`FileArchived`。不新增其他事件。

- [ ] **Step 4: 实施后验证并提交**

覆盖换版并发、不可变引用拒绝、detach/重绑、删除拒绝、失效/恢复和归档幂等；Detached/Archived事件通过Task 5唯一投递链验证失败退避、同一eventId重试成功及失败无重复业务事实，不新增本域投递入口。

Expected: 生命周期分支PASS。提交：`feat(platform): 完成文件版本治理分支`

---

### Task 9: 提供响应式共享文件组件并接入工期页面

**Files:**
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/platform/file/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/components/PmsFileArtifact/PmsFileUploader.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/components/PmsFileArtifact/PmsFileReferenceList.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/components/PmsFileArtifact/PmsFileVersionDrawer.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/components/PmsFileArtifact/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDurationFormDrawer.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDurationPanel.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/construction-plan/index.ts`
- Test: `yudao-ui/yudao-ui-admin-vue3/src/components/PmsFileArtifact/PmsFileArtifact.spec.ts`
- Test: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/ProjectDurationPanel.spec.ts`

**Consumes:** 稳定PLT HTTP契约和现有工期页面；不复用旧URL作为身份。

- [ ] **Step 1: 封装共享API和组件**

组件输入ownerContext/objectType/objectId/purposeCode/referenceKey，不输入tenantId或URL；上传进度与服务端可用状态分开。复用Yudao Upload结构和请求工具，支持历史、下载/预览、换版、解绑及稳定错误恢复。

- [ ] **Step 2: 接入CUSTOMER_DELAY正向表单**

客户延期显示单槽材料组件；草稿保存artifactId/versionNo/referenceKey，提交前展示已冻结文件。删除“尚未接入”警告；无材料原因不渲染必填槽位。保持字段存在性PATCH，清空时显式null。

- [ ] **Step 3: 实施后验证并提交**

实现后运行组件测试、全量`corepack pnpm ts:check`、定向ESLint/Stylelint和`build:local`；检查主题变量、键盘可达、长文件名及320/768/1024/1440布局。

Expected: 共享UI和工期接入PASS。提交：`feat(ui): 接入统一文件版本组件`

---

### Task 10: 完成真实基础设施、业务浏览器与Feature收口验证

**Files:**
- Create: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactEndToEndMySqlIntegrationTest.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/constructionplan/DurationChangeCustomerEvidenceEndToEndTest.java`
- Create: `docs/superpowers/evidence/f-plt-001/implementation-evidence.md`
- Modify: `tasks/features/F-PLT-001.md`
- Modify: `tasks/features/F-SOL-001.md`

**Consumes:** Tasks 1～9；本Task不包含Deployment、SIT、UAT或Release。

- [ ] **Step 1: 全新数据库和真实基础设施验证**

使用独立Compose空库执行V1→V94，装配真实MySQL、Redis、INFRA私有存储配置、ClamAV及应用。验证50MB正向上传、50MB+1拒绝、跨master重放/补偿、扫描拒绝、六表约束和无遗留对象。

- [ ] **Step 2: PLATFORM业务全链验证**

覆盖初始化→完成→Version/Reference→inspect/revalidate→访问，以及换版、detach、重绑、失效/恢复、归档；验证同租户/跨租户、权限负向、scope/CAS并发、幂等重放/冲突、审计，以及V93启用Job由Quartz自动触发后四类Outbox经`FileOutboxDeliveryJob`领取、发布、成功标记、失败退避、到期重领和同一eventId幂等；确认业务事实不因投递重试重复。

- [ ] **Step 3: SOL正向浏览器闭环**

优先使用内置浏览器；不可用时按用户既有授权使用外部浏览器。真实登录后完成CUSTOMER_DELAY草稿、50MB内材料上传、提交、服务经理审批及历史查看；同时验证权限负向、文件变化导致终态失败关闭、console/page error及无公网永久文件URL。

- [ ] **Step 4: 四档响应式和回归**

在320/768/1024/1440验证上传、长文件名、历史抽屉、下载/预览和工期面板，无页面级横向溢出，主题切换不丢样式。运行platform/engineering聚焦测试、必要Reactor构建、前端ts/lint/build及受管规格快照校验。

- [ ] **Step 5: 独立复审、回写与提交**

形成绑定提交、数据库、存储/扫描、浏览器、Node/pnpm和命令结果的证据；独立复审GO后将Task 10与F-PLT-001回写Implementation Done，并关闭F-SOL-001材料分支阻断。不得写Deployment/SIT/UAT/Release通过。

Expected: F-PLT-001 Implementation Done证据完整。提交：`docs(feature): 完成 F-PLT-001 实施追溯`

## Verification Matrix

| 需求 | 主验证 |
|---|---|
| Artifact/Version/Reference不可变与精确槽位 | V92约束、Mapper契约、MySQL并发 |
| 50MB普通上传 | Spring配置、50MB+1有界读取、真实multipart |
| INFRA回执重放/补偿 | master切换、0/1/多记录、私有存储 |
| 安全扫描 | ClamAV正常/EICAR/不可用，未配置失败关闭 |
| Provider权限和范围版本 | 0/1/多Provider、SOL真实事实、锁定重验 |
| 幂等/审计/Outbox | 同键重放、异载荷冲突、故障回滚、四事件重试 |
| F-SOL-001材料闭环 | 真实PROJ+SOL+PLT+Flowable+浏览器 |
| 响应式与主题 | 320/768/1024/1440、主题切换、无横向溢出 |

## Explicit Out of Scope

- 修改Yudao基础框架、既有`FileApi`/`FileClient`、`infra_file`DDL或Spring multipart机制；
- 大于50MB分片/直传、公开桶、永久URL、匿名分享、正文审计和历史附件迁移；
- 正式扫描/对象存储实例部署、密钥、容量、备份、Deployment、SIT、UAT和Release；
- PRE-01审批状态、PLN-01重算、其他业务域审核流程或为未来消费者预建空适配；
- 新文件事件、无消费者CHG Outbox、通用文档管理或采集原始结果存储。
