# F-PLT-001 可选安全扫描 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 默认不启动 ClamAV；安全扫描关闭时跳过病毒校验并以真实 `SKIPPED` 状态提交文件版本，开启时继续对扫描拒绝、异常和 Provider 缺失失败关闭。

**Architecture:** 先用 PRD V1.8 批准增量把“安全扫描是部署可选能力”写入权威规格，再同步 NPDMS 受管快照。实现层由 `pms.file.scan.enabled` 单一开关控制扫描策略和 ClamAV Bean 装配；关闭时不调用 Provider，开启时要求恰一 Provider。`plt_file_version.scan_status_code` 通过前向迁移增加 `SKIPPED`，审计、事件和查询透传真实状态，不以历史成功或默认值伪造 `PASSED`。

**Tech Stack:** Markdown/JSON 规格、Python 规格同步校验、Java 25、Spring Boot、MySQL 8.4/Flyway、Docker Compose、JUnit 5、Maven。

**Spec:** `docs/baseline/prd-v1.8-amendment-004-optional-file-security-scan.md`、`docs/design/13-file-design.md`、`docs/design/14-security-design.md`、`specs/features/F-PLT-001-unified-file-identity-and-version-management.md`、`specs/features/F-PLT-001-physical-contract.json`

## Global Constraints

- Requirement ID 保持 `PLT-02`，不新增业务 Owner、审批流、文件生命周期或公开 API。
- 默认 `pms.file.scan.enabled=false`；关闭时仍执行大小、摘要、扩展名、声明 MIME、内容嗅探和文件策略校验。
- 关闭时只产生 `SKIPPED`，`scan_provider_code/scan_provider_version` 必须为 `NULL`；不得写成 `PASSED`。
- 开启时必须装配恰一 `FileSecurityScanProvider`；Provider 缺失、重复、异常、`ERROR` 或未知结果失败关闭，`REJECTED` 拒绝上传。
- 已执行的 `V92__fplt001_file_artifact.sql` 不修改；只新增前向迁移。
- Compose 的 `clamav` 只属于 `security-scan` profile；默认 `docker compose up -d mysql redis migrate` 不创建或拉取 ClamAV。
- 不停止、不删除当前机器上已有的容器或数据卷。
- NPDMS 受管规格文件只允许通过 `scripts/sync_specification_baseline.py --apply` 从已提交的规格仓库 revision 同步。

---

### Task 1: 批准并锁定 `SKIPPED` 规格契约

**Files:**
- Create: `docs/baseline/prd-v1.8-amendment-004-optional-file-security-scan.md`
- Modify: `docs/baseline/change-log.md`
- Modify: `docs/engineering/00-engineering-chain.md`
- Modify: `docs/design/13-file-design.md`
- Modify: `docs/design/14-security-design.md`
- Modify: `docs/traceability/requirement-matrix.md`
- Modify: `specs/001-project-delivery-platform/domains/PLT-平台公共能力需求规格.md`
- Modify: `specs/features/F-PLT-001-unified-file-identity-and-version-management.md`
- Modify: `specs/features/F-PLT-001-physical-contract.json`
- Modify: `specs/features/README.md`
- Test: `scripts/tests/test_fplt001_feature_contract.py`

**Interfaces:**
- Consumes: 用户批准的策略：安全扫描默认关闭；关闭时不校验病毒；开启时保持 ClamAV 失败关闭。
- Produces: `CHG-PRD-2026-08-27-004`、封闭状态值域 `PASSED/SKIPPED`、可由实现仓库同步的正式规格提交。

- [x] **Step 1: 写 PRD 批准增量**

  新增 `CHG-PRD-2026-08-27-004`，明确默认关闭、关闭时跳过病毒校验并留痕 `SKIPPED`、开启时失败关闭、其他文件校验不受影响、历史版本不改写。

- [x] **Step 2: 更新 SDS 与领域规格**

  将上传流程写成“基础内容校验 → 按部署配置执行安全扫描或记录 `SKIPPED` → 提交 FileVersion”，并明确 `SKIPPED` 只表示未执行病毒扫描，不表示扫描安全。

- [x] **Step 3: 更新 Feature 与机器合同**

  机器合同使用以下封闭值域和成功策略：

  ```json
  {
    "scanStatusCodes": ["PASSED", "SKIPPED"],
    "successPolicy": "COMPLETED requires PASSED when security scanning is enabled, otherwise SKIPPED; both require registered INFRA receipt, committed FileVersion and committed FileReference create/CAS",
    "resultCodes": ["PASSED", "REJECTED", "ERROR"],
    "disabledPolicy": "disabled scanning invokes no provider and persists SKIPPED with null provider code/version"
  }
  ```

- [x] **Step 4: 更新追溯与规格索引**

  将 `CHG-PRD-2026-08-27-004` 加入变更索引和 PLT-02 追溯，保留此前 Implementation Done 证据为历史实现基线，同时标记本增量待 NPDMS 实施复验。

- [x] **Step 5: 运行规格校验**

  Run: `python scripts/validate_prd_baseline.py --prd docs/baseline/prd-v1.8.md --report docs/reports/2026-08-19-PRD-V1.8基线变更报告.md --expected-version V1.8 --expected-status 正式基线`

  Run: `python scripts/validate_sds_phase2.py`

  Run: `python scripts/validate_sds_phase3.py`

  Run: `python -m unittest scripts.tests.test_fplt001_feature_contract`

  Expected: 所有适用校验 `PASS`；若脚本参数与当前仓库入口不同，只使用仓库帮助信息修正调用，不降低校验范围。

- [ ] **Step 6: 保存规格提交边界**

  仅在用户明确要求提交后，暂存本 Task 的规格文件并创建一个本地规格提交；不得混入其他工作树改动。该提交 ID 是 Task 2 同步的唯一 revision。

### Task 2: 同步规格并实现可选扫描

**Files:**
- Modify by sync only: `docs/specification-baseline/manifest.json`
- Modify by sync only: `docs/baseline/**`、`docs/design/**`、`docs/engineering/**`、`docs/traceability/**`、`specs/**`
- Create: `sql/migrations/V98__fplt001_optional_security_scan.sql`
- Modify: `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/file/dto/FileSecurityScanResult.java`
- Modify: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/command/ValidatedFileContent.java`
- Modify: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileContentPolicyService.java`
- Modify: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/ClamAvFileSecurityScanProvider.java`
- Modify: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/FileUploadApplicationService.java`
- Modify: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/event/FileEventFactory.java`
- Modify: `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/file/event/FileOutboxDeliveryJob.java`
- Modify: `yudao-server/src/main/resources/application-local.yaml`
- Modify: `compose.yaml`
- Modify: `docs/development.md`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileContentValidationTest.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileUploadCompletionServiceTest.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileOutboxDeliveryJobTest.java`
- Test: `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/file/FileArtifactMigrationContractTest.java`

**Interfaces:**
- Consumes: Task 1 的已提交规格 revision。
- Produces: `NPDMS_FILE_SECURITY_SCAN_ENABLED=false|true`、持久化 `scan_status_code=PASSED|SKIPPED`、Compose `security-scan` profile。

- [ ] **Step 1: 预检并同步批准规格**

  Run:

  ```powershell
  $specRepo = 'M:\AICoding\CodexData\worktrees\09b5\项目交付平台'
  $specCommit = git -C $specRepo rev-parse HEAD
  python scripts/sync_specification_baseline.py --source-repo $specRepo --revision $specCommit --allowlist docs/specification-baseline/allowlist.json
  ```

  Expected: 只出现本变更涉及文件的 `REPLACE/ADD`，无 `CONFLICT`。

  Run: 同一命令追加 `--apply`，再运行 `python scripts/validate_specification_baseline.py`。

  Expected: manifest `source.commit` 等于 Task 1 提交，validator `PASS`。

- [ ] **Step 2: 写失败测试覆盖默认跳过和开启失败关闭**

  在 `FileContentValidationTest` 中使用新构造参数验证：

  ```java
  FileContentPolicyService disabled = new FileContentPolicyService(
          new BoundedMultipartReader(), List.of(), false);
  ValidatedFileContent skipped = disabled.validate(command(
          PDF, "evidence.pdf", "application/pdf", null, policy(1024L)));
  assertEquals("SKIPPED", skipped.scanStatusCode());
  assertNull(skipped.scanProviderCode());
  assertNull(skipped.scanProviderVersion());

  FileContentPolicyService enabled = new FileContentPolicyService(
          new BoundedMultipartReader(), List.of(), true);
  assertThrows(RuntimeException.class, () -> enabled.validate(command(
          PDF, "evidence.pdf", "application/pdf", null, policy(1024L))));
  ```

  Run: `mvn -pl pms-module-platform -am -DskipTests=false -Dtest=FileContentValidationTest -Dsurefire.failIfNoSpecifiedTests=false test`

  Expected: FAIL，原因是构造器和 `scanStatusCode` 尚未实现。

- [ ] **Step 3: 实现最小扫描策略和条件装配**

  `FileSecurityScanResult` 接受 `SKIPPED`；`ValidatedFileContent` 增加 `scanStatusCode`。`FileContentPolicyService` 关闭时直接返回 `new FileSecurityScanResult("SKIPPED", null, null, null)`，开启时保留当前恰一 Provider 和失败关闭规则。ClamAV Bean 使用：

  ```java
  @Component
  @ConditionalOnProperty(name = "pms.file.scan.enabled", havingValue = "true")
  public class ClamAvFileSecurityScanProvider implements FileSecurityScanProvider {
  }
  ```

  配置使用：

  ```yaml
  pms:
    file:
      scan:
        enabled: ${NPDMS_FILE_SECURITY_SCAN_ENABLED:false}
  ```

- [ ] **Step 4: 透传真实状态到版本、审计和事件**

  `FileUploadApplicationService` 从 `ValidatedFileContent.scanStatusCode()` 写入 FileVersion 和审计；`FileEventFactory.versionCommitted(...)` 接收该状态；`FileOutboxDeliveryJob` 只接受 `PASSED/SKIPPED`。任何路径不得硬编码成功状态为 `PASSED`。

- [ ] **Step 5: 增加前向迁移及迁移测试**

  新迁移只替换现有 CHECK：

  ```sql
  ALTER TABLE `plt_file_version`
      DROP CHECK `chk_plt_file_version_scan`,
      ADD CONSTRAINT `chk_plt_file_version_scan`
          CHECK (`scan_status_code` IN ('PASSED', 'SKIPPED'));
  ```

  测试断言 `SKIPPED` 可写、`ERROR` 仍被数据库拒绝，且 `scan_provider_code/scan_provider_version` 对 `SKIPPED` 为 `NULL`。

- [ ] **Step 6: 使 Compose 服务默认不加载**

  为 `clamav` 增加：

  ```yaml
  profiles: ["security-scan"]
  ```

  文档明确默认命令不包含 ClamAV；启用命令为：

  ```powershell
  $env:NPDMS_FILE_SECURITY_SCAN_ENABLED='true'
  docker compose --profile security-scan up -d clamav
  ```

  应用配置和 Compose profile 必须同时启用；只启用应用开关但 Provider 不可用时上传失败关闭。

- [ ] **Step 7: 运行聚焦测试**

  Run: `mvn -pl pms-module-platform -am -DskipTests=false -Dtest=FileContentValidationTest,FileUploadCompletionServiceTest,FileOutboxDeliveryJobTest,FileArtifactMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test`

  Expected: 所有聚焦测试 `PASS`。

### Task 3: 验证配置边界和回归

**Files:**
- Modify only if failures expose a defect in this change: Task 2 files and their focused tests.

**Interfaces:**
- Consumes: Task 2 实现。
- Produces: 默认无 ClamAV、启用后真实扫描、两种模式均有真实审计状态的验收证据。

- [ ] **Step 1: 验证 Compose 默认解析结果**

  Run: `docker compose config --services`

  Expected: 包含 `mysql`、`redis`、`migrate`，不包含 `clamav`。

  Run: `docker compose --profile security-scan config --services`

  Expected: 额外包含 `clamav`。

- [ ] **Step 2: 验证默认 Spring 装配**

  运行平台 Spring 上下文测试，断言默认配置不存在 `ClamAvFileSecurityScanProvider`，`FileContentPolicyService` 仍可创建并返回 `SKIPPED`。

- [ ] **Step 3: 验证启用 Spring 装配**

  使用 `pms.file.scan.enabled=true` 运行同一上下文测试，断言 ClamAV Provider 恰一；Provider 不可用时上传不创建 FileVersion、FileReference、成功审计或成功事件。

- [ ] **Step 4: 运行模块回归与差异检查**

  Run: `mvn -pl pms-module-platform -am test`

  Run: `git diff --check`

  Expected: Reactor `BUILD SUCCESS`，diff check 无输出；不要求停止现有 ClamAV 容器。

- [ ] **Step 5: 保存实施提交边界**

  仅在用户明确要求提交后，暂存 Task 2/3 文件并创建本地实现提交；不得暂存当前工作树已有的 engineering/UI 改动，不推送。
