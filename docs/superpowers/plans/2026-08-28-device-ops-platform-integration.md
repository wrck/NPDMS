# Device Ops 采集平台集成 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本计划禁止使用子代理；未经用户明确要求不得执行 Git commit。

**Goal:** 将 Device Ops 作为集成域维护的独立执行子应用接入 NPDMS，完成平台采集任务、集成适配、完整日志回调、基础平台文件保存、业务文件引用和统一入口闭环。

**Architecture:** `pms-module-platform` 拥有凭证授权、批次、设备任务、业务状态和结果事件；`pms-module-integration` 拥有 Device Ops 协议适配、下发尝试、回调 Receipt、技术重试和对账；Device Ops 保持独立进程、数据库和执行事实；`yudao-module-infra` 基础平台负责底层文件存储及 `FileArtifact/FileVersion` 记录，其他模块只保存 `fileVersionId` 和本域关系。

**Tech Stack:** Java 25、Spring Boot 4.1、MyBatis-Plus、MySQL 8、Flyway 风格 SQL、JUnit 5、Spring Test、Device Ops Maven Reactor、H2/Flyway、Vue 3、TypeScript、Vite、Vitest、Element Plus、pnpm。

---

## 计划边界

本计划分为四条可以独立验证但按顺序交付的工作流：

1. 正式契约与数据库基线。
2. NPDMS Platform、Integration、基础文件平台后端闭环。
3. Device Ops 完整日志制品与可靠回调改造。
4. 统一入口、首个业务消费者和端到端验收。

不实施以下内容：Device Ops 并入 NPDMS 主进程、共享数据库、完整日志写入业务表大字段、真实厂商设备兼容承诺、Device Ops H2 多副本、未经用户要求的 Git commit。

## 文件结构映射

### 新增模块/API

- Create: `pms-module-integration/pms-module-integration-api/pom.xml`
- Create: `pms-module-integration/pms-module-integration-api/src/main/java/cn/iocoder/yudao/module/pms/integration/api/deviceops/DeviceOpsGatewayApi.java`
- Create: `pms-module-integration/pms-module-integration-api/src/main/java/cn/iocoder/yudao/module/pms/integration/api/deviceops/dto/*.java`
- Modify: `pom.xml`
- Modify: `pms-module-integration/pom.xml`
- Modify: `pms-module-platform/pom.xml`

### 基础平台文件能力

- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/ArtifactFileApi.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/dto/ArtifactFileCreateCommand.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/dto/ArtifactFileVersionDTO.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/dataobject/file/FileArtifactDO.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/dataobject/file/FileVersionDO.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/mysql/file/FileArtifactMapper.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/mysql/file/FileVersionMapper.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/service/file/ArtifactFileService.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/service/file/ArtifactFileApiImpl.java`
- Test: `yudao-module-infra/src/test/java/cn/iocoder/yudao/module/infra/service/file/ArtifactFileServiceTest.java`

### Platform DAC

- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/collection/CollectionTaskApi.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/collection/CollectionCallbackApi.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/collection/dto/*.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/collection/*.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/collection/*.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/collection/*.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/collection/*.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/collection/*.java`

### Integration Device Ops 适配

- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/config/*.java`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/client/*.java`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/callback/*.java`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/dal/*.java`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/reconcile/*.java`
- Test: `pms-module-integration/src/test/java/cn/iocoder/yudao/module/pms/integration/deviceops/**/*.java`

### Device Ops

- Modify: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/port/CallbackOutboxPort.java`
- Create: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/model/TerminalLogArtifact.java`
- Create: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/service/TerminalLogArtifactService.java`
- Modify: `device-ops-platform/device-ops-adapter-persistence-jdbc/src/main/java/com/dp/deviceops/adapter/persistence/jdbc/JdbcCollectionExecutionPersistencePort.java`
- Create: `device-ops-platform/device-ops-adapter-persistence-jdbc/src/main/resources/db/migration/V13__add_terminal_log_artifacts.sql`
- Modify: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/callback/CallbackDispatcher.java`
- Create: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/callback/CallbackSignatureService.java`
- Create: `device-ops-platform/device-ops-adapter-persistence-jdbc/src/test/java/com/dp/deviceops/adapter/persistence/jdbc/TerminalLogArtifactPersistenceTest.java`
- Create: `device-ops-platform/device-ops-adapter-web-spring/src/test/java/com/dp/deviceops/adapter/web/callback/CallbackDispatcherTest.java`
- Create: `device-ops-platform/device-ops-adapter-web-spring/src/test/java/com/dp/deviceops/adapter/web/callback/CallbackSignatureServiceTest.java`

### SQL、前端与文档

- Create: `sql/migrations/V104__device_ops_integration_foundation.sql`
- Create: `sql/migrations/V105__device_ops_menu_and_permissions.sql`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/platform/collection/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/platform/device-collection/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/platform/device-collection/index.spec.ts`
- Modify: `docs/design/02c-data-ownership-matrix.md`
- Modify: `docs/design/03-system-architecture.md`
- Modify: `docs/design/04-module-design.md`
- Modify: `docs/design/05-state-machine.md`
- Modify: `docs/design/08-data-model.md`
- Modify: `docs/design/09-database-design.md`
- Modify: `docs/design/10-api-design.md`
- Modify: `docs/design/11-event-design.md`
- Modify: `docs/design/12-integration-design.md`
- Modify: `docs/design/13-file-design.md`
- Modify: `docs/traceability/requirement-matrix.md`

---

### Task 1: 回写正式架构、Owner、状态和完整日志契约

**Files:**
- Modify: `docs/design/02c-data-ownership-matrix.md`
- Modify: `docs/design/03-system-architecture.md`
- Modify: `docs/design/04-module-design.md`
- Modify: `docs/design/05-state-machine.md`
- Modify: `docs/design/08-data-model.md`
- Modify: `docs/design/09-database-design.md`
- Modify: `docs/design/10-api-design.md`
- Modify: `docs/design/11-event-design.md`
- Modify: `docs/design/12-integration-design.md`
- Modify: `docs/design/13-file-design.md`
- Modify: `docs/traceability/requirement-matrix.md`

- [ ] **Step 1: 将模块边界冻结为四层**

在正式 SDS 中统一写明：PLT 拥有业务任务；INT 维护 Device Ops 产品并拥有 NPDMS 侧集成证据；Device Ops 独立运行并拥有原始执行事实；基础平台拥有底层文件和文件记录。

- [ ] **Step 2: 冻结记录命名和唯一键**

使用以下名称，不再混用 `CallbackRecord`：

```text
INT  IntegrationCallbackReceipt  unique(provider_code, callback_id)
INT  DispatchAttempt             unique(platform_task_id, attempt_no, operation_type)
PLT  CollectionCallbackRecord    unique(platform_task_id, callback_id)
INFRA FileArtifact               stable artifact identity
INFRA FileVersion                immutable content version
```

- [ ] **Step 3: 冻结主状态和技术阶段**

主状态只使用：

```text
CREATED AUTHORIZED DISPATCHED EXECUTING CALLBACK_PROCESSING
RESULT_AVAILABLE CONSUMED COMPLETED FAILED CANCELLED SECURITY_EXCEPTION
```

技术阶段只使用：

```text
PENDING_DISPATCH DISPATCHING ACCEPTED RUNNING TIMED_OUT
DISPATCH_FAILED RECONCILING RESULT_FILE_QUARANTINED
```

- [ ] **Step 4: 冻结完整日志规则**

所有设备任务终态必须上传完整脱敏日志；正常文件必须形成 `fileVersionId`；扫描隔离形成 `quarantineEvidenceId`；业务域只持引用。

- [ ] **Step 5: 更新追溯矩阵**

为 INT-12、EXE-03、EXE-04、CUT-03、CUT-06、INS-02、INS-04 分别指向本计划 Task 3、5、7、8、10、12、13。

- [ ] **Step 6: 执行文档一致性检查**

Run:

```powershell
rg -n "日志文件或结果引用|Device Ops.*平台域|CallbackRecord" docs/design docs/traceability
```

Expected: 不再出现与新裁决冲突的旧表述；保留的历史兼容表述必须明确标记为历史读取。

---

### Task 2: 建立 Integration API 子模块和依赖方向

**Files:**
- Modify: `pom.xml`
- Modify: `pms-module-integration/pom.xml`
- Create: `pms-module-integration/pms-module-integration-api/pom.xml`
- Create: `pms-module-integration/pms-module-integration-api/src/main/java/cn/iocoder/yudao/module/pms/integration/api/deviceops/DeviceOpsGatewayApi.java`
- Create: `pms-module-integration/pms-module-integration-api/src/main/java/cn/iocoder/yudao/module/pms/integration/api/deviceops/dto/DeviceOpsDispatchCommand.java`
- Create: `pms-module-integration/pms-module-integration-api/src/main/java/cn/iocoder/yudao/module/pms/integration/api/deviceops/dto/DeviceOpsDispatchResult.java`
- Create: `pms-module-integration/pms-module-integration-api/src/main/java/cn/iocoder/yudao/module/pms/integration/api/deviceops/dto/DeviceOpsTaskSnapshot.java`
- Modify: `pms-module-platform/pom.xml`

- [ ] **Step 1: 写模块依赖测试脚本断言**

在计划执行记录中先运行：

```powershell
mvn -pl pms-module-platform,pms-module-integration -am dependency:tree `
  -Dincludes=cn.iocoder.boot:pms-module-platform,cn.iocoder.boot:pms-module-integration
```

Expected: 当前尚无 integration-api，命令不能显示目标依赖。

- [ ] **Step 2: 创建 integration-api POM**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>cn.iocoder.boot</groupId>
        <artifactId>yudao</artifactId>
        <version>${revision}</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <artifactId>pms-module-integration-api</artifactId>
</project>
```

- [ ] **Step 3: 定义稳定 Gateway API**

```java
public interface DeviceOpsGatewayApi {
    DeviceOpsDispatchResult dispatch(DeviceOpsDispatchCommand command);
    DeviceOpsTaskSnapshot query(String platformTaskId);
    void cancel(String platformTaskId, String reason);
}
```

`DeviceOpsDispatchCommand` 必须包含 `platformTaskId`、`batchId`、`tenantId`、项目设备快照、协议端点、模板内容/版本/哈希、凭证模式、一次性取密令牌、临时秘密 write-only 载体和 callback provider；不得包含业务 Service 或 DO 类型。

- [ ] **Step 4: 更新父 Reactor 和模块依赖**

父 `pom.xml` 在 platform-api 后加入 integration-api；platform biz 依赖 integration-api；integration biz 依赖 integration-api、platform-api 和 yudao-module-infra。

- [ ] **Step 5: 验证依赖无环**

Run:

```powershell
mvn -pl pms-module-platform,pms-module-integration/pms-module-integration-api,pms-module-integration -am test -DskipTests
```

Expected: BUILD SUCCESS；platform 不依赖 integration biz，integration 不依赖 platform biz。

---

### Task 3: 扩展基础平台 FileArtifact/FileVersion 流式保存能力

**Files:**
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/ArtifactFileApi.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/dto/ArtifactFileCreateCommand.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/api/file/dto/ArtifactFileVersionDTO.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/dataobject/file/FileArtifactDO.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/dataobject/file/FileVersionDO.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/mysql/file/FileArtifactMapper.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/dal/mysql/file/FileVersionMapper.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/service/file/ArtifactFileService.java`
- Create: `yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/service/file/ArtifactFileApiImpl.java`
- Test: `yudao-module-infra/src/test/java/cn/iocoder/yudao/module/infra/service/file/ArtifactFileServiceTest.java`

- [ ] **Step 1: 写失败测试**

覆盖：相同 `sourceSystem + idempotencyKey + sha256` 返回同一 FileVersion；摘要冲突拒绝；超过 50MB 拒绝；扫描失败返回隔离结果；基础平台记录哈希、大小、MIME、storageKey，调用者只能得到 DTO。

```java
@Test
void shouldReplaySameFileVersionForSameDigest() {
    ArtifactFileVersionDTO first = service.store(command("cb-1", "aa"), stream("log"));
    ArtifactFileVersionDTO replay = service.store(command("cb-1", "aa"), stream("log"));
    assertEquals(first.fileVersionId(), replay.fileVersionId());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
mvn -pl yudao-module-infra -Dtest=ArtifactFileServiceTest test
```

Expected: FAIL，类型尚不存在。

- [ ] **Step 3: 定义 API**

```java
public interface ArtifactFileApi {
    ArtifactFileVersionDTO store(ArtifactFileCreateCommand command, InputStream content);
    ArtifactFileVersionDTO getVersion(Long fileVersionId);
}
```

`ArtifactFileCreateCommand` 字段固定为：tenantId、sourceSystem、sourceArtifactKey、idempotencyKey、name、contentType、declaredSize、declaredSha256、directory、accessScope。

- [ ] **Step 4: 实现流式落盘和幂等记录**

实现中使用固定大小 buffer 流式计算 SHA-256，不调用 `readAllBytes()`；先写临时路径，大小、哈希、类型和扫描通过后晋级正式 storageKey，再同事务保存 FileArtifact/FileVersion。数据库冲突后按唯一键读取并比较哈希。

- [ ] **Step 5: 运行测试**

Run:

```powershell
mvn -pl yudao-module-infra -Dtest=ArtifactFileServiceTest test
```

Expected: PASS。

---

### Task 4: 创建数据库基线

**Files:**
- Create: `sql/migrations/V104__device_ops_integration_foundation.sql`

- [ ] **Step 1: 编写迁移验证测试查询**

迁移后必须存在：

```text
infra_file_artifact
infra_file_version
plt_device_credential
plt_credential_grant
plt_collection_batch
plt_collection_task
plt_collection_callback_record
int_device_ops_dispatch_attempt
int_device_ops_callback_receipt
int_device_ops_reconcile_batch
```

- [ ] **Step 2: 编写 V104**

关键唯一约束：

```sql
UNIQUE KEY uk_infra_artifact_source (source_system, source_artifact_key)
UNIQUE KEY uk_infra_version_digest (artifact_id, content_sha256)
UNIQUE KEY uk_plt_collection_idempotency (tenant_id, idempotency_key)
UNIQUE KEY uk_plt_callback (platform_task_id, callback_id)
UNIQUE KEY uk_int_dispatch_attempt (platform_task_id, operation_type, attempt_no)
UNIQUE KEY uk_int_callback_receipt (provider_code, callback_id)
```

所有业务表包含 tenant_id、creator、create_time、updater、update_time、deleted；秘密字段只允许 `encrypted_secret` 或 `kms_reference`，禁止 password/private_key/token 明文列。

- [ ] **Step 3: 执行迁移检查**

Run:

```powershell
mvn -pl yudao-server -am -DskipTests package
```

Expected: BUILD SUCCESS，Flyway 文件命名不冲突。

Run:

```powershell
rg -n "password|private_key|passphrase|access_token" sql/migrations/V104__device_ops_integration_foundation.sql
```

Expected: 仅允许约束说明或加密元数据，不出现明文秘密列。

---

### Task 5: 实现 Platform 批次、设备任务和状态机

**Files:**
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/collection/CollectionTaskApi.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/collection/dto/CollectionBatchCreateCommand.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/collection/dto/CollectionTaskDTO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/collection/CollectionTaskService.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/collection/CollectionTaskStateMachine.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/collection/CollectionBatchDO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/collection/CollectionTaskDO.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/collection/CollectionBatchMapper.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/mysql/collection/CollectionTaskMapper.java`
- Create: `pms-module-platform/src/main/resources/mapper/collection/CollectionBatchMapper.xml`
- Create: `pms-module-platform/src/main/resources/mapper/collection/CollectionTaskMapper.xml`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/collection/CollectionTaskStateMachineTest.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/collection/CollectionTaskMySqlTest.java`

- [ ] **Step 1: 写状态机失败测试**

```java
@Test
void shouldRequireConsumptionBeforeBusinessTaskCompletes() {
    assertEquals(RESULT_AVAILABLE, machine.onTechnicalSuccess(task(BUSINESS_CONSUMPTION)));
    assertEquals(COMPLETED, machine.onConsumed(task(RESULT_AVAILABLE), matchingConsumer()));
}

@Test
void shouldRejectOldResultVersionConsumption() {
    assertThrows(IllegalStateException.class,
            () -> machine.onConsumed(task(RESULT_AVAILABLE, 2), consumer(1)));
}
```

- [ ] **Step 2: 运行失败测试**

```powershell
mvn -pl pms-module-platform -Dtest=CollectionTaskStateMachineTest test
```

Expected: FAIL。

- [ ] **Step 3: 实现批次原子创建**

同一事务校验所有设备请求，创建一个 batch 和 N 个 task；任何本地校验失败都不落库。每个设备任务分配独立 platformTaskId、设备级幂等键和 completionMode。

- [ ] **Step 4: 实现主状态和技术阶段守卫**

禁止：COMPLETED 回退、FAILED 原地恢复、旧 resultVersion 消费、错误 consumerObject 完成任务、未知外部状态映射成功。

- [ ] **Step 5: 实现 MySQL 并发测试**

两个并发事务创建相同幂等任务，断言只有一个赢家，另一个重放同一批次；摘要不同返回冲突。

- [ ] **Step 6: 运行测试**

```powershell
mvn -pl pms-module-platform -Dtest=CollectionTaskStateMachineTest,CollectionTaskMySqlTest test
```

Expected: PASS。

---

### Task 6: 实现平台凭证授权和一次性取密令牌

**Files:**
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/collection/DeviceCredentialService.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/collection/CredentialTokenService.java`
- Create: platform API DTO and controller files.
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/collection/CredentialTokenServiceTest.java`

- [ ] **Step 1: 写失败测试**

覆盖令牌绑定 task/device/protocol/template/audience/expiry/jti；首次消费成功；第二次消费失败；撤销后失败；超时响应不得再次消费同一 jti。

- [ ] **Step 2: 运行失败测试**

```powershell
mvn -pl pms-module-platform -Dtest=CredentialTokenServiceTest test
```

Expected: FAIL。

- [ ] **Step 3: 实现加密存储和令牌消费**

凭证服务只持密文或 KMS reference。取密 API 返回瞬时 char[]/byte[]，调用完成后清零；日志、审计 detailSnapshot、异常和 Outbox 不允许包含秘密。

- [ ] **Step 4: 实现临时秘密路径**

临时密码任务先提交不含秘密的 PENDING_DISPATCH 任务，再同步调用 integration Gateway；明确拒绝转 FAILED/DISPATCH_FAILED；网络未知转 RECONCILING；禁止后台重放秘密。

- [ ] **Step 5: 运行测试和秘密扫描**

```powershell
mvn -pl pms-module-platform -Dtest=CredentialTokenServiceTest test
rg -n "getPassword\(|password.*log|privateKey.*log|passphrase.*log" pms-module-platform/src/main/java
```

Expected: 测试 PASS；无秘密日志。

---

### Task 7: 实现 Platform 回调事实、结果事件和消费确认

**Files:**
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/collection/CollectionCallbackApi.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/collection/dto/CollectionCallbackCommand.java`
- Create: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/collection/dto/CollectionConsumptionCommand.java`
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/collection/CollectionCallbackService.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/collection/CollectionCallbackServiceTest.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/service/collection/CollectionCallbackMySqlTest.java`

- [ ] **Step 1: 写失败测试**

覆盖 callbackId 幂等、序号乱序、externalTaskId 不匹配、成功/部分成功/失败/取消/安全异常映射、fileVersionId 必填、quarantineEvidenceId 分支、Outbox 原子提交、重复消费确认。

- [ ] **Step 2: 实现 API 命令**

`CollectionCallbackCommand` 只接收 receiptId、callbackId、sequence、platformTaskId、externalTaskId、externalStatus、resultVersion、fileVersionId、quarantineEvidenceId、failureCategory、startedAt、completedAt、traceId；不接收文件二进制或通用文件元数据。

- [ ] **Step 3: 实现事务**

同一事务：插入 CollectionCallbackRecord、推进 CollectionTask、更新 batch 投影、写 PlatformOutboxEvent。重复 callbackId 返回既有结果，不重复事件。

- [ ] **Step 4: 实现事件映射**

```text
SUCCEEDED/PARTIAL_SUCCESS -> CollectionResultAvailable(fileVersionId)
FAILED/TIMED_OUT          -> CollectionFailed(fileVersionId)
CANCELLED                 -> CollectionCancelled(fileVersionId)
SECURITY_EXCEPTION        -> CollectionSecurityFailed(fileVersionId or quarantineEvidenceId)
```

- [ ] **Step 5: 运行测试**

```powershell
mvn -pl pms-module-platform -Dtest=CollectionCallbackServiceTest,CollectionCallbackMySqlTest test
```

Expected: PASS。

---

### Task 8: 实现 Integration Device Ops Client、DispatchAttempt 和对账

**Files:**
- Modify: `pms-module-integration/pom.xml`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/config/DeviceOpsProperties.java`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/client/DeviceOpsGatewayApiImpl.java`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/client/DeviceOpsHttpClient.java`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/dal/dataobject/DeviceOpsDispatchAttemptDO.java`
- Create: Mapper files.
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/reconcile/DeviceOpsReconcileJob.java`
- Test: `pms-module-integration/src/test/java/cn/iocoder/yudao/module/pms/integration/deviceops/client/DeviceOpsGatewayApiImplTest.java`

- [ ] **Step 1: 写失败测试**

使用 JDK 内置 HttpServer 或项目已有 Mock 工具覆盖：202 受理、409 幂等重放、4xx 不重试、5xx/超时先查询、临时秘密超时不重放、未知状态保留原值。

- [ ] **Step 2: 运行失败测试**

```powershell
mvn -pl pms-module-integration -Dtest=DeviceOpsGatewayApiImplTest test
```

Expected: FAIL。

- [ ] **Step 3: 实现 Client**

只允许配置中的 baseUri；固定 connect/read timeout；认证头和请求秘密不入日志；每次 submit/query/cancel/reconcile 创建独立 DispatchAttempt。

- [ ] **Step 4: 实现对账**

按 platformTaskId 查询 Device Ops；将查询结果通过 CollectionCallbackApi 或专用 reconcile 命令提交 platform；integration 不直接写 PLT 表。

- [ ] **Step 5: 运行测试**

```powershell
mvn -pl pms-module-integration -Dtest=DeviceOpsGatewayApiImplTest test
```

Expected: PASS。

---

### Task 9: 实现 Integration 签名 multipart 回调和 Receipt 状态机

**Files:**
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/callback/DeviceOpsCallbackController.java`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/callback/CallbackManifest.java`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/callback/CallbackSignatureVerifier.java`
- Create: `pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/deviceops/callback/CallbackReceiptService.java`
- Create: Receipt DO and Mapper files.
- Test: `pms-module-integration/src/test/java/cn/iocoder/yudao/module/pms/integration/deviceops/callback/CallbackSignatureVerifierTest.java`
- Test: `pms-module-integration/src/test/java/cn/iocoder/yudao/module/pms/integration/deviceops/callback/DeviceOpsCallbackControllerTest.java`

- [ ] **Step 1: 写固定签名向量测试**

固定 UTF-8 RFC 8785 manifest、两个 part、timestamp、nonce、path、key，断言 canonical content hash 和 HMAC-SHA256 精确等于固定十六进制结果。

- [ ] **Step 2: 写 Receipt 状态测试**

覆盖：新 callback、同摘要整包重传、不同摘要冲突、COMPLETED 重放、REJECTED retryable 重新进入 RECEIVING、QUARANTINED 稳定失败、202 状态查询。

- [ ] **Step 3: 实现流式 multipart**

限制总大小 50MB、partCount 16；所有 part 同请求到齐；按 partNumber 流式计算每卷哈希和组合哈希；禁止 readAllBytes；manifest 中声明与实际不一致时拒绝。

- [ ] **Step 4: 调用基础平台**

校验通过后调用 ArtifactFileApi.store；只保存返回的 fileVersionId；扫描隔离时保存 quarantineEvidenceId，不伪造 FileVersion。

- [ ] **Step 5: 提交 Platform 回调事实**

基础文件保存后调用 CollectionCallbackApi。只有 FileVersion 成功且 platform 接受后 Receipt 才 COMPLETED。

- [ ] **Step 6: 实现 HTTP 结果**

```text
200 COMPLETED   -> callbackId, receiptId, fileVersionId, accepted=true
202 PROCESSING  -> callbackId, receiptId, status
409 CONFLICT    -> same callbackId different digest
422 QUARANTINED -> quarantineEvidenceId
4xx             -> stable non-retryable error
5xx             -> retryable infrastructure error
```

- [ ] **Step 7: 运行测试**

```powershell
mvn -pl pms-module-integration -Dtest=CallbackSignatureVerifierTest,DeviceOpsCallbackControllerTest test
```

Expected: PASS。

---

### Task 10: 改造 Device Ops 终态日志制品和 Outbox 原子性

**Files:**
- Create: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/model/TerminalLogArtifact.java`
- Create: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/service/TerminalLogArtifactService.java`
- Modify: `device-ops-platform/device-ops-core/src/main/java/com/dp/deviceops/core/port/CallbackOutboxPort.java`
- Modify: `device-ops-platform/device-ops-adapter-persistence-jdbc/src/main/java/com/dp/deviceops/adapter/persistence/jdbc/JdbcCollectionExecutionPersistencePort.java`
- Create: `device-ops-platform/device-ops-adapter-persistence-jdbc/src/main/resources/db/migration/V13__add_terminal_log_artifacts.sql`
- Test: `device-ops-platform/device-ops-adapter-persistence-jdbc/src/test/java/com/dp/deviceops/adapter/persistence/jdbc/TerminalLogArtifactPersistenceTest.java`

- [ ] **Step 1: 写失败测试**

终态事务后必须同时存在：终态 target、不可变日志制品元数据、Callback Outbox；stdout/stderr 不再出现在 Outbox JSON payload；重复终态不产生第二制品版本。

- [ ] **Step 2: 定义日志格式**

生成 UTF-8 JSON Lines 或 UTF-8 文本归档，包含执行上下文、模板版本、命令边界、完整脱敏 stdout/stderr、退出状态、分页、超时、取消、截断、解析事实、解析器版本和质量报告。

- [ ] **Step 3: 实现本地制品原子写入**

先写临时文件，fsync/close 后原子 rename；数据库事务保存 artifact path、size、sha256、contentType、resultVersion 和 Outbox manifest。Outbox 只保存制品 ID、manifest 和哈希。

- [ ] **Step 4: 实现恢复扫描**

启动时发现终态但无 Outbox、Outbox 指向缺失文件或哈希不符时进入隔离，禁止发送不完整回调。

- [ ] **Step 5: 运行测试**

```powershell
mvn -f device-ops-platform/pom.xml -pl device-ops-adapter-persistence-jdbc -am `
  -Dtest=TerminalLogArtifactPersistenceTest test
```

Expected: PASS。

---

### Task 11: 改造 Device Ops 签名 multipart Dispatcher 和 ACK 查询

**Files:**
- Modify: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/callback/CallbackDispatcher.java`
- Create: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/callback/CallbackSignatureService.java`
- Create: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/callback/CallbackReceiptClient.java`
- Modify: `device-ops-platform/device-ops-adapter-web-spring/src/main/java/com/dp/deviceops/adapter/web/callback/CallbackProperties.java`
- Test: `device-ops-platform/device-ops-adapter-web-spring/src/test/java/com/dp/deviceops/adapter/web/callback/CallbackDispatcherTest.java`
- Test: `device-ops-platform/device-ops-adapter-web-spring/src/test/java/com/dp/deviceops/adapter/web/callback/CallbackSignatureServiceTest.java`

- [ ] **Step 1: 写失败测试**

覆盖 multipart 字节、RFC 8785 manifest、HMAC headers、200 完成、202 查询、retryable REJECTED 同 callbackId 整包重传、QUARANTINED 死信、本地制品保留和成功后清理。

- [ ] **Step 2: 实现签名和 multipart**

使用与 Integration 固定向量完全相同的 canonicalization；part 按 partNumber 升序；日志超过 50MB 时不发送，标记 `RESULT_FILE_TOO_LARGE`。

- [ ] **Step 3: 实现 ACK 语义**

仅 `200 + accepted=true + matching callbackId` 调用 markDelivered；202 查询 Receipt；任意普通 2xx 不再自动视为成功。

- [ ] **Step 4: 运行测试**

```powershell
mvn -f device-ops-platform/pom.xml -pl device-ops-adapter-web-spring -am `
  -Dtest=CallbackDispatcherTest,CallbackSignatureServiceTest test
```

Expected: PASS。

---

### Task 12: 实现 Platform 管理 API 和统一工作台

**Files:**
- Create: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/collection/CollectionTaskController.java`
- Create: controller VO files.
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/pms/platform/collection/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/platform/device-collection/index.vue`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/platform/device-collection/index.spec.ts`
- Create: `sql/migrations/V105__device_ops_menu_and_permissions.sql`

- [ ] **Step 1: 写前端失败测试**

覆盖：批量创建多设备任务、状态列表、技术阶段、fileVersionId 下载、失败证据、显式重试、临时密码不进入 storage、Device Ops 子工作台同源入口。

- [ ] **Step 2: 实现管理 API**

```text
POST /pms/platform/collection-batches
GET  /pms/platform/collection-batches/{batchId}
GET  /pms/platform/collection-tasks/{taskId}
POST /pms/platform/collection-tasks/{taskId}/cancel
POST /pms/platform/collection-tasks/{taskId}/retry
POST /pms/platform/collection-tasks/{taskId}/consume
```

- [ ] **Step 3: 实现页面**

页面只调用 Platform API；不直接调用 Device Ops。临时密码绑定表单内存，提交完成立即清空，不使用 localStorage/sessionStorage。

- [ ] **Step 4: 实现菜单权限 SQL**

至少创建页面权限和按钮权限：query、create、cancel、retry、download、consume。菜单 route 指向 `pms/platform/device-collection/index`。

- [ ] **Step 5: 运行前端测试**

```powershell
Set-Location yudao-ui/yudao-ui-admin-vue3
pnpm exec vitest run src/views/pms/platform/device-collection/index.spec.ts
pnpm ts:check
pnpm lint
pnpm build:prod
```

Expected: 全部成功。

---

### Task 13: 实现首个 IMP 消费者和 AST 文件引用链

**Files:**
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/configuration/ConfigurationService.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/configuration/ConfigurationServiceImpl.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/configuration/ConfigurationDO.java`
- Modify: `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/configuration/ConfigurationMapper.java`
- Create: `pms-module-engineering/src/test/java/cn/iocoder/yudao/module/pms/engineering/service/configuration/ConfigurationCollectionResultConsumerTest.java`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/equipmentconfiglog/EquipmentConfigLogService.java`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/service/equipmentconfiglog/EquipmentConfigLogServiceImpl.java`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/equipmentconfiglog/EquipmentConfigLogDO.java`
- Modify: `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/equipmentconfiglog/EquipmentConfigLogMapper.java`
- Create: `pms-module-asset/src/test/java/cn/iocoder/yudao/module/pms/asset/service/equipmentconfiglog/EquipmentConfigLogReferenceTest.java`

- [ ] **Step 1: 写 IMP 消费失败测试**

收到 `CollectionResultAvailable` 后，IMP 按 task/resultVersion 幂等创建 ConfigurationCollectionResult，只保存 fileVersionId 和业务解析；不得把技术成功直接标记为业务通过。

- [ ] **Step 2: 写 AST 引用失败测试**

IMP 发布 ConfigurationLogPublished 后，AST 建立同一 fileVersionId 的 ConfigurationLog 业务身份、设备关联和不可变解析版本；不得复制文件二进制或新建第二 FileVersion。

- [ ] **Step 3: 实现消费者和消费确认**

IMP 成功形成业务结果后调用 CollectionTaskApi.consume，必须携带 consumerContext、consumerObjectType、consumerObjectId 和 resultVersion。

- [ ] **Step 4: 运行测试**

```powershell
mvn -pl pms-module-engineering,pms-module-asset -am test
```

Expected: PASS。

---

### Task 14: 同步和验证 Device Ops 集成域工程归属

**Files:**
- Verify: `device-ops-platform/**`
- Modify: deployment/readme paths only when needed.

- [ ] **Step 1: 固定来源 SHA**

记录来源：

```text
NPDP 49c6cd2f313b5ae0c74fdb61d8765e6354232f73
```

- [ ] **Step 2: 对比文件清单**

```powershell
git -C M:\AICoding\CodexData\worktrees\48b2\NPDP ls-tree -r --name-only `
  49c6cd2f313b5ae0c74fdb61d8765e6354232f73 -- device-ops-platform |
  Sort-Object | Set-Content $env:TEMP\device-ops-source.txt

git ls-files device-ops-platform | Sort-Object |
  Set-Content $env:TEMP\device-ops-target.txt

Compare-Object (Get-Content $env:TEMP\device-ops-source.txt) `
  (Get-Content $env:TEMP\device-ops-target.txt)
```

Expected: 只有本 Feature 明确新增的 V13、日志制品、multipart 回调和配置差异；无 node_modules、data、target、秘密。

- [ ] **Step 3: 保持独立 Reactor**

确认根 `pom.xml` 不添加 `device-ops-platform` 模块；产品/代码维护归 INT，但运行时仍是独立 JAR、数据库和进程。

---

### Task 15: 后端全链路契约与端到端测试

**Files:**
- Create: `pms-module-integration/src/test/java/cn/iocoder/yudao/module/pms/integration/deviceops/DeviceOpsEndToEndTest.java`
- Create or modify Device Ops acceptance test fixtures.

- [ ] **Step 1: 建立模拟执行链**

使用 Device Ops 进程内 SSH server，提交一批包含成功、PARTIAL_SUCCESS、失败设备的任务；生成完整脱敏日志并回调 NPDMS Integration。

- [ ] **Step 2: 断言文件链**

基础平台只创建一个 FileArtifact/FileVersion；INT Receipt、PLT CallbackRecord、IMP/AST 只保存同一 fileVersionId；业务数据库无文件二进制。

- [ ] **Step 3: 断言异常链**

覆盖：重复 callback、不同摘要冲突、乱序、超时后查询、回调丢失对账、扫描隔离、文件过大、撤销、临时秘密不重放。

- [ ] **Step 4: 运行后端测试**

```powershell
mvn -pl yudao-module-infra,pms-module-platform,pms-module-integration,pms-module-engineering,pms-module-asset -am verify
```

Expected: BUILD SUCCESS。

---

### Task 16: 执行 Device Ops、NPDMS、前端质量门禁

**Files:**
- No implementation files unless a failing gate exposes a defect.

- [ ] **Step 1: Device Ops 全量验证**

```powershell
.\device-ops-platform\build.ps1
```

Expected: Vue test、ts:check、lint、build 和 Maven clean verify 全部成功，生成 `device-ops-server.jar`。

- [ ] **Step 2: NPDMS Maven 全量验证**

```powershell
mvn clean verify
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: NPDMS 前端全量验证**

```powershell
Set-Location yudao-ui/yudao-ui-admin-vue3
pnpm ts:check
pnpm lint
pnpm build:prod
```

Expected: 全部成功。

- [ ] **Step 4: 敏感信息和大字段扫描**

```powershell
rg -n "password|privateKey|passphrase|Authorization" `
  pms-module-platform/src/main pms-module-integration/src/main device-ops-platform `
  -g "*.java" -g "*.xml" -g "*.sql"

rg -n "stdout|stderr|byte\[\]|longblob|mediumblob" `
  pms-module-platform/src/main pms-module-integration/src/main sql/migrations/V104__device_ops_integration_foundation.sql
```

Expected: 秘密仅出现在 write-only/瞬时处理代码；PLT/INT 表和 DTO 不保存完整日志二进制或 stdout/stderr。

- [ ] **Step 5: 工作树检查**

```powershell
git status --short
git diff --check
```

Expected: 无空白错误；仅包含本 Feature 计划内文件；不执行 commit。

---

## 实施检查点

每完成以下阶段必须暂停检查，不跨阶段掩盖失败：

1. Task 1～4：正式契约、模块依赖、基础文件平台和数据库基线。
2. Task 5～9：Platform 与 Integration 闭环。
3. Task 10～11：Device Ops 日志制品和可靠回调。
4. Task 12～14：统一入口、消费者、代码归属。
5. Task 15～16：端到端和全量质量门禁。

## 完成判定

- Device Ops 产品/工程归集成域维护，但仍独立运行、构建和持久化。
- Platform 只拥有业务任务和状态，不写 Integration、Device Ops 或文件平台数据。
- Integration 只拥有技术接入事实，不写 Platform 业务表。
- 基础平台是 FileArtifact/FileVersion、底层对象和通用文件记录的唯一写入方。
- 所有业务域只持 fileVersionId 和本域关系。
- 所有设备终态回传完整脱敏日志；正常结果有 fileVersionId，隔离结果有 quarantineEvidenceId。
- 重复、乱序、冲突、超时、撤销、文件过大、扫描隔离和回调丢失均有自动化证据。
- Device Ops、NPDMS 后端和 NPDMS 前端的测试、类型检查、Lint 和构建全部通过。
