# F-SOL-003 需求分析动态表单组合与版本冻结实施计划

> **面向实施代理：** 必须使用 `executing-plans` 执行本计划。F-SOL-003 按一个完整正向闭环实施，不拆分阶段性 PASS；完成全部后端、前端、迁移、种子和事实接口后，再集中执行整体测试、真实浏览器验收与独立评审。

**目标：** 在已完成的 F-PLT-002 共享动态表单基础上，实现 WorkBinding 自动冻结模板、项目经理填写并完成 PRE-04、创建下一版本、查看历史与对比，以及向 SCH-01 提供明确完成版本事实的完整闭环。

**架构：** PROJ 只拥有项目模板和 WorkBinding；PLATFORM 只拥有动态表单模板、不可变修订、实例 Schema/值与受控文件组合；SOL 只拥有 PRE-04 草稿/完成、有效版本、历史、对比和事实输出。SOL 外层命令持有唯一幂等、审计和事务，调用 PLT 的跨 Context 写入与持锁方法均为 `MANDATORY`，两侧同事务提交或回滚。

**技术栈：** JDK 25、Spring Boot、MyBatis/MySQL 8、Flyway、PLT `PlatformCommandExecutionApi`/F-PLT-001、Vue 3、Element Plus、FormCreate、pnpm 9.15.5、Vitest、Chromium。

**规格：** `specs/features/F-SOL-003-requirement-analysis-versioning.md`、`specs/features/F-SOL-003-physical-contract.json`、`specs/features/F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md`、`specs/features/F-PLT-002-physical-contract.json`；受管规格源提交 `9417079eda9e64ca5ecc23373c86431434800f83`，NPDMS 同步提交 `8837c273`。

## 全局约束

- 当前阶段是 `IMPLEMENTATION/开发阶段`；本计划通过前不得修改产品代码，本计划通过后也不得宣称 Deployment、SIT、UAT 或 Release 完成。
- 旧 `pms_eng_requirement` 后端、前端、CRUD、状态、菜单、数据和 `super_admin` 访问保持原样；旧 BPM、旧工程表单同样零修改。
- 已取消候选代码不删除：保留 `sol_preparation` 双轴版本、历史/对比、授权、幂等/审计、事实 API 和响应式工作区成果；停止新流程读写 `sol_requirement_analysis_section`、SOL 正文/附件快照及 `IN_SYNC/PENDING/UNKNOWN` 双真值。
- 任何增强进入新 API、Provider、Service、VO 或 SOL 包装组件；不得在旧需求分析类、页面、接口上原位改造。
- 项目模板管理员选择并冻结明确的 PRE-04 兼容 PLT 发布修订；项目用户没有模板选择步骤。
- 11 个核心富文本字段及其 11 个唯一 `{CORE_CODE}__ATTACHMENTS` 受控字段必须存在；三个核心富文本必填，附件槽位存在但附件值可为空。
- 用户可配置的完整 FormCreate 能力继续由 F-PLT-002 承载；SOL 不裁剪 Schema，也不把 iframe、函数、远程 API 或浏览器事件当作服务端完成事实。
- 所有新增或改造查询遵守 `docs/coding/database-query-interface.md`；跨模块只依赖公开 API，不依赖对方 Service、Mapper、DO 或业务表。
- 使用下一未占用 Flyway 版本 `V104/V105`；不得修改已执行的 `V1～V103`，不得自动迁移或双写旧候选/旧需求分析数据。
- 文件对象存储继续使用 MinIO；可选 ClamAV 只影响文件扫描事实，不改变 PRE-04 状态机。
- 实施期间只形成一个整体候选且不拆分 Gate、不提前提交；复杂核心能力先实际运行对应失败测试，确认因目标行为缺失而失败，再完成实现和重构，正向闭环接通后复跑受影响测试并进入集中整体验证。

## 设计前现有实现吸收映射

| 现有资产 | 决策 | 新责任 |
|---|---|---|
| F-PLT-002 FormCreate codec、renderer、`PmsFileArtifactField` | 直接复用 | 新 SOL 包装组件装配冻结 Schema/值/文件，不复制运行时 |
| `sol_preparation` 的草稿/有效双轴、业务版本、来源版本 | 复制增强 | 增加 `dynamic_form_instance_id`，保留 SOL 生命周期与 CAS |
| 当前 `RequirementAnalysisCommandService/QueryService` 的权限、幂等、审计、历史/对比骨架 | 复制后增强到新组合 Service | 改为调用 `DynamicFormBusinessInstanceApi`，不再访问章节真值 |
| `RequirementAnalysisFactApi` | 复制后增强 | 只输出/重验明确 COMPLETED SOL 根及完整 PLT 实例事实 |
| `ProjectRequirementAnalysisPanel`、历史/对比抽屉 | 复制后增强到新组件 | 保留项目工作区体验，替换章节卡片为通用动态表单包装 |
| `RequirementAnalysisCatalog`、`RequirementAnalysisSectionDO/Mapper`、章节 PATCH、附件快照同步 | 不作为新真值复用 | 文件和表保留，只停止新流程调用，不删除已执行迁移 |
| 旧 `pms_eng_requirement`、旧 BPM、旧工程表单 | 保持原样 | 仅作回归验证，不修改、不迁移、不双写 |

## 文件与责任边界

### 新增 PLATFORM 公共边界

- `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/dynamicform/DynamicFormBusinessAction.java`：封闭动作枚举。
- `.../api/dynamicform/DynamicFormBusinessInstanceApi.java`：修订用途、实例创建/读取/PATCH/克隆/锁定重验接口。
- `.../api/dynamicform/DynamicFormBusinessObjectPolicyProvider.java`：消费 Context 的修订兼容与实例动作策略接口。
- `.../api/dynamicform/dto/*.java`：Owner 键、修订事实、实例事实、受控文件事实及命令/重验载荷；DTO 不暴露 PLT DO。
- `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormBusinessObjectPolicyProviderRegistry.java`：按 `ownerContext/objectType` 唯一注册 Provider。
- `.../DynamicFormBusinessInstanceApiImpl.java`：公共 API 适配。
- `.../DynamicFormBusinessInstanceService.java`：Provider-first 锁序、Schema/值 CAS、克隆与完整事实比较。

### 调整 PLATFORM 实现

- `DynamicFormFilePolicyProvider.java`：手工实例仍使用既有权限；SOL 业务实例把文件动作映射为 `FILE_READ/FILE_WRITE` 并委托 Owner Provider。
- `DynamicFormSchemaService.java`：输出稳定字段描述、声明式校验结果和受控文件字段集合，保留完整 FormCreate 配置。
- `PlatformDynamicFormInstanceMapper.java`、`DynamicFormInstanceMapper.xml` 及场景 Query：支持按明确 Owner/实例稳定排序锁定、调用方预分配 ID、值 CAS 和克隆插入。
- `ExistingFileVersionAttachmentService.java`：复用既有 `attachExistingVersions` 与 `PlatformTransactionalOutboxWriter`；实际新引用一条事件，重放不增，失败同事务回滚。

### 调整 PROJ 与 SOL

- `RequirementAnalysisWorkBindingSchema.java`：升级为 schemaVersion=2，仅冻结 `dynamicFormTemplateId/dynamicFormTemplateRevisionId/dynamicFormRevisionNo/dynamicFormRevisionFactVersion`。
- `ProjectTemplateServiceImpl.java`、`TemplatePublishValidator.java`：发布时通过 PLT `REVISION_BINDING_PUBLISH` 校验明确启用发布修订；运行时冻结事实使用 `REVISION_FROZEN_USE`。
- `PreparationDO.java`、`RequirementAnalysisRootMapper.java/xml` 和场景 Query/Update：增加并维护非空 `dynamic_form_instance_id`，保留双轴唯一性和 SOL CAS。
- 新建 `RequirementAnalysisDynamicFormPolicyProvider.java`：实现 `SOL/REQUIREMENT_ANALYSIS` 的兼容、范围、当前经理、状态及动作策略。
- 新建 `RequirementAnalysisDynamicFormCommandService.java`：首次创建、普通值 PATCH、完成和下一草稿的唯一外层事务/幂等/审计编排。
- 新建 `RequirementAnalysisDynamicFormQueryService.java`：工作区、详情、阻断、历史和字段级对比装配。
- 调整 `RequirementAnalysisFactApiImpl.java`：只输出完成版本及 PLT 冻结事实，按 `PROJ→SOL→PLT→文件` 重验。
- 调整 `PreparationController.java` 及新 VO：新增 `/form` PATCH、双版本请求头、完成/创建草稿和当前查询契约；保留已取消候选的章节 PATCH 代码，但新工作区不调用且不把它作为新契约。
- `V104__fsol003_dynamic_form_composition.sql`：前向增加 SOL→PLT 稳定 ID、唯一索引和 PRE-04 检查约束，不触碰 V99 章节表。
- `V105__fsol003_dynamic_form_composition_seed.sql`：提供合法、不兼容、停用与无匹配 PRE-04 模板/WorkBinding 组合；使用高段 ID 和 `seed` creator。

### 调整前端与验收

- 新建 `RequirementAnalysisDynamicForm.vue`：复用 PLT runtime 渲染冻结 Schema、普通值和受控文件字段，管理 dirty/响应未知恢复。
- 调整 `ProjectRequirementAnalysisPanel.vue`、历史/对比抽屉及 `requirement-analysis/index.ts`：项目内无选模、双版本 CAS、有效版/草稿/历史/对比、保守 `allowedActions`。
- 保留 `RequirementAnalysisSectionCard.vue` 及旧 API 文件，不作为新流程入口；不得通过删除旧实现完成改造。
- 新建 `scripts/tests/run_fsol003_dynamic_form_browser_acceptance.cjs` 和 `docs/engineering/evidence/f-sol-003-dynamic-form-browser-evidence.json`：记录真实浏览器与公开 REST 证据。

---

### Task 1：一次完成 F-SOL-003 动态表单组合正向闭环

**Files:** 使用上方“文件与责任边界”列出的 PLATFORM、PROJ、SOL、迁移和前端文件；只在新组合边界调整当前候选，不修改三组旧实现。

**Interfaces:**

- Consumes：`ProjectScopeApi`、`ProjectParticipantFactApi`、`ProjectWorkBindingFactApi`、F-PLT-001 `FileArtifactApi`、F-PLT-002 现有 schema/runtime、`PlatformCommandExecutionApi`、`OperationAuditApi`。
- Produces：

```java
public enum DynamicFormBusinessAction {
    REVISION_BINDING_PUBLISH, REVISION_FROZEN_USE,
    CREATE, READ, PATCH, COMPLETE, CLONE_SOURCE, CLONE_TARGET,
    FILE_READ, FILE_WRITE
}

public interface DynamicFormBusinessInstanceApi {
    DynamicFormRevisionFact inspectRevisionForUsage(DynamicFormRevisionUsageQuery query);
    DynamicFormRevisionFact lockAndRevalidateRevisionForUsage(DynamicFormRevisionRevalidationQuery query);
    DynamicFormInstanceFact createBusinessInstance(DynamicFormInstanceCreateCommand command);
    DynamicFormInstanceFact inspectInstance(DynamicFormInstanceQuery query);
    DynamicFormInstanceFact patchInstanceValues(DynamicFormInstancePatchCommand command);
    DynamicFormInstanceFact cloneBusinessInstance(DynamicFormInstanceCloneCommand command);
    DynamicFormInstanceFact lockAndRevalidateInstance(DynamicFormInstanceRevalidationQuery query);
}

public interface DynamicFormBusinessObjectPolicyProvider {
    String ownerContext();
    String objectType();
    DynamicFormPolicyFact inspectRevisionCompatibility(DynamicFormRevisionPolicyQuery query);
    DynamicFormPolicyFact inspectInstanceOwnerPolicy(DynamicFormInstancePolicyQuery query);
    DynamicFormPolicyFact lockAndRevalidateInstanceOwnerPolicy(
            DynamicFormPolicyRevalidationQuery query);
}
```

- `DynamicFormInstanceFact` 必须包含 Owner、冻结模板/修订/引擎、完整 Schema、普通值、声明式校验、全部 ACTIVE 受控文件事实、实例版本和冻结动作。
- 所有写与持锁方法标注 `@Transactional(propagation = Propagation.MANDATORY)`；只读 inspect 不持锁、不写入。

- [ ] **Step 1：写完整失败测试集合并实际确认 RED**

新增/改造聚焦测试，测试名称直接对应 AC-FSOL003-001～015：PLT API/Provider 动作封闭与 MANDATORY、PROJ WorkBinding v2 发布冻结、SOL 原子初始化/PATCH/完成/克隆/历史/对比/Fact、文件事件和回滚、Controller 双请求头、前端 runtime/dirty/响应未知。测试使用最终接口签名，避免实现期间再猜 DTO；随后立即运行下列聚焦集合，逐项确认失败原因是目标接口/行为尚未实现，而不是测试装配、数据库或语法错误：

```powershell
mvn.cmd -pl pms-module-platform,pms-module-project,pms-module-engineering -am `
  "-Dtest=DynamicFormBusinessInstanceServiceTest,RequirementAnalysisWorkBindingSchemaTest,RequirementAnalysisDynamicFormCommandServiceTest,RequirementAnalysisDynamicFormQueryServiceTest,RequirementAnalysisFactApiImplTest,RequirementAnalysisControllerContractTest,RequirementAnalysisMigrationContractTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
corepack pnpm vitest run --config vitest.pms-file.config.ts `
  src/views/pms/project/project-master-detail/components/RequirementAnalysisDynamicForm.runtime.spec.ts
```

Expected：至少一项以缺失目标类型、接口或断言行为失败；若测试因环境或装配失败，先修正测试再重新确认 RED，不得把环境错误当作失败测试证据。

- [ ] **Step 2：实现 PLT 跨 Context 业务实例边界**

先由 Registry 对所有 Owner Provider 按完整稳定键排序执行 inspect/持锁重验，再获取 PLT template/revision/instance 锁，最后按全局 Artifact→Version→Reference 顺序调用 F-PLT-001；取得第一个 PLT 锁后不得回调 SOL。创建和克隆必须使用调用方预分配 ID；实例 Owner 唯一键冲突时仅同 Owner/同 ID/同冻结修订可重放，否则冲突。

- [ ] **Step 3：实现 PRE-04 WorkBinding v2 发布与运行时冻结**

移除新流程对 `catalogCode/catalogVersion/extensionItems` 的依赖。项目模板发布时读取明确 PLT 修订并执行 `REVISION_BINDING_PUBLISH`；冻结到项目任务执行合同时保存五个 v2 字段。项目运行时只执行 `REVISION_FROZEN_USE`，模板后续停用或 current pointer 变化不使历史项目失效。

- [ ] **Step 4：以前向迁移建立 SOL→PLT 组合关系与验收种子**

V104 增加 `dynamic_form_instance_id`，确保所有持久化 PRE-04 版本（包括首次及后续 `DRAFT` 根和 `COMPLETED` 根）均非空且 `(tenant_id,dynamic_form_instance_id)` 唯一；迁移契约和真实 MySQL 必须直接拒绝任一 PRE-04 空值行，同时保持 PRE-02 语义不变，不修改 V99/V100/V101。V105 使用已经发布的 PLT 动态表单结构建立 11 个核心富文本、11 个对应 `PmsFileArtifact` 字段及合法/缺字段/重复字段/停用/无匹配组合；不臆造普通业务角色授权。

- [ ] **Step 5：实现 SOL 唯一外层命令与查询编排**

首次创建预分配 `preparationId/dynamicFormInstanceId`，先插入携带非空实例 ID、version=1 的 SOL 根，再调用 PLT `CREATE`。PATCH 使用 `If-Match=PLT` 与 `X-SOL-If-Match=SOL`；完成先获得全部 PROJ/SOL owner 事实，再锁 PLT/文件完整向量，阻断为空才切换有效标记。创建下一草稿以 `CLONE_SOURCE/CLONE_TARGET` 克隆值与独立文件引用；任一步失败时 SOL 根、PLT 实例、引用、Outbox、成功幂等和成功审计共同回滚。

- [ ] **Step 6：实现历史、对比、SCH 事实与受控审计**

历史保持 `businessVersion DESC,id DESC` 游标分页；对比按稳定 `fieldKey` 即时比较 Schema 摘要、普通值和受控文件事实，不建差异表。Fact API 只允许 COMPLETED，历史完成版返回 `isCurrentEffective=false` 但仍可作为明确输入。成功审计只记录 ID、动作、前后版本/状态、变化字段键、文件摘要和 operationId，不记录富文本正文、函数源码、接口响应或文件内容。

- [ ] **Step 7：完成项目工作区动态表单交互**

新包装组件直接复用 PLT codec/renderer 和 `PmsFileArtifactField`；显示冻结模板/修订、三个核心及声明式阻断、当前有效版、草稿、历史和对比。项目用户不显示模板选择；未保存普通值阻止版本/路由切换；文件响应未知沿用原 slot/Idempotency-Key 并刷新权威事实；服务端 `allowedActions` 决定编辑/完成/创建草稿。

- [ ] **Step 8：完成初始化数据、接口说明并复跑受影响测试至 GREEN**

确认菜单/权限、配置和示例组合覆盖精确命中、不兼容、停用、无匹配；确认旧 `pms_eng_requirement`、旧 BPM、旧工程表单及 V99 章节路径没有产品 diff。复跑 Step 1 的后端和前端聚焦集合，全部通过后完成必要重构并再复跑受影响测试；这些结果只是 TDD 反馈，不形成阶段性 PASS、独立 Gate 或提交。至此才进入 Task 2。

### Task 2：集中执行整体测试、真实验收、复审与提交

**Files:** Task 1 的完整实现、全部自动化测试、浏览器脚本、结构化证据、截图和 `tasks/features/F-SOL-003.md` 单条检查点。

**Interfaces:** Consumes Task 1 完整候选；Produces 一个可独立审查的 F-SOL-003 Implementation Done 提交。

- [ ] **Step 1：一次运行后端聚焦与真实 MySQL 应用事务矩阵**

```powershell
docker compose up -d mysql redis migrate
mvn.cmd -pl pms-module-platform,pms-module-project,pms-module-engineering -am `
  "-Dtest=DynamicFormBusinessInstanceServiceTest,RequirementAnalysisWorkBindingSchemaTest,RequirementAnalysisDynamicFormCommandServiceTest,RequirementAnalysisDynamicFormQueryServiceTest,RequirementAnalysisFactApiImplTest,RequirementAnalysisControllerContractTest,RequirementAnalysisMigrationContractTest,RequirementAnalysisApplicationMySqlIntegrationTest" `
  "-DskipITs=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

使用 `docs/development.md` 的隔离 Compose MySQL 和本地 `.env` 凭据，测试前执行迁移、测试后由 `@AfterEach` 清除本用例高段数据并核对无残留。`-DskipITs=false` 必须使受 `@EnabledIfSystemProperty` 控制的真实 MySQL 测试实际执行而非 SKIPPED。真实 MySQL 直接断言：首次/克隆预分配 ID；所有持久化 PRE-04（含 DRAFT）实例 ID 非空；无外层事务失败；双版本任一陈旧零写；并发完成单胜；N 个新引用=N 个事件、重放事件不增、批量中途失败所有成功事实为零；动作替换、Provider 不可用、锁序反序、跨租户/无权均失败关闭。

- [ ] **Step 2：一次运行前端、类型和构建矩阵**

```powershell
corepack pnpm vitest run --config vitest.pms-file.config.ts `
  src/views/pms/project/project-master-detail/components `
  src/views/pms/platform/dynamic-form
corepack pnpm ts:check
corepack pnpm build:local
```

断言完整 FormCreate 往返、11 个核心及附件字段、`false/0/null/空字符串/空数组`、文件响应未知恢复、dirty 导航保护、历史/对比和服务端动作投影。

- [ ] **Step 3：运行全仓、Flyway 与规格基线检查**

```powershell
mvn.cmd -pl pms-module-platform,pms-module-project,pms-module-engineering -am `
  "-DskipITs=true" test
mvn.cmd -DskipTests package
python scripts/validate_specification_baseline.py
python scripts/validate_repository_baseline_rules.py
git diff --check
```

第一条命令必须实际执行 PLATFORM、PROJ、ENGINEERING 及其依赖模块的全部非环境测试，不得用 package 代替测试；第二条保留全仓打包证明。用 Docker Compose 权威 MySQL 从 V1 迁移到 V105，执行 Flyway `info/validate`，再由宿主机 JDK 25 应用运行聚焦验收；MinIO 为文件存储，分别保留 ClamAV PASSED 与扫描关闭 SKIPPED 两轮文件事实。

- [ ] **Step 4：运行真实 Chromium 公开 UI/REST 闭环**

```powershell
node scripts/tests/run_fsol003_dynamic_form_browser_acceptance.cjs
```

隔离项目依次验证：模板管理员选择兼容发布修订并发布 WorkBinding；项目用户无选模；项目经理创建并填写 V1、上传/换版/解绑文件、完成；创建 V2、修改并完成；历史与字段/文件对比；响应未知恢复；三个必填/Schema/文件事实失效阻断；获权成员只读、非经理拒绝、第二租户不可见；320/768/1024/1440 无页面级横向溢出，意外 console/page/request error 为零。

- [ ] **Step 5：执行契约与架构双轴代码复审**

逐项映射 AC-FSOL003-001～016，确认模块所有权、Provider-first 锁序、MANDATORY、双版本 CAS、审计摘要、文件事件和旧实现零修改。发现缺陷只做本 Feature 最小修复，然后重新运行受影响测试及整体关键矩阵；不得顺带实现 SCH-01 或重构旧功能。

- [ ] **Step 6：更新唯一检查点并创建整体候选提交**

`tasks/features/F-SOL-003.md` 只保留一条不超过 300 字的检查点：基线、当前 Gate、已通过证据、阻塞、下一步。显式暂存本计划范围文件，排除 `.codex-tmp/`，创建一个整体 Implementation 候选提交；不 push。随后按固定 17 字段格式送独立 Implementation Done 评审，未获 GO 不回写完成状态。

## 计划自审

- 规格覆盖：Task 1 对应 BR-FSOL003-001～010，Task 2 对应 AC-FSOL003-001～016；F-PLT-002 聚焦 API、文件事件和 MANDATORY 同时覆盖。
- 边界覆盖：PLT/PROJ/SOL/F-PLT-001 真值唯一；WorkBinding 在基础能力之后接入；项目用户无选模；旧功能、旧数据与已执行迁移保持不变。
- 接口一致：动作枚举、Provider 方法、双版本请求头、预分配实例 ID 和完整事实向量均与两份物理契约同名同义。
- 无占位项：本计划没有待定接口、未定义业务动作或后续补齐项；实施不得自行扩大到 SCH-01、Deployment、SIT、UAT 或 Release。
