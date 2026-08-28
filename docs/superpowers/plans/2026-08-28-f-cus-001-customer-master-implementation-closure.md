# F-CUS-001 客户主档整体实施收口计划

> **面向执行代理：** 必须使用 `executing-plans` 按本计划执行。步骤使用复选框（`- [ ]`）跟踪，但本 Feature 只形成一次整体 Implementation Done 候选，不形成中间 Task PASS，不在全量验证前提交。

**目标：** 保留已合并的客户主档实现，在不实现或伪装 CRM 同步的前提下，补齐平台/临时客户创建、删除与恢复、权限负向和真实浏览器证据，使 F-CUS-001 达到可独立复审的 Implementation Done。

**架构：** `pms-module-customer` 继续作为客户主档唯一当前写 Owner，PROJ、AST 只通过公开 API 提供摘要和引用守卫。新增高段、`creator=seed` 的透明验收目录与账号前置事实；所有客户业务事实仍由公开 UI/REST 创建和变更。现有实现只针对真实暴露的 UI、查询和幂等缺口做最小修复。

**技术栈：** Java 25、Spring Boot 4.1、MyBatis-Plus/MySQL 8.4、Flyway 11、Vue 3.5、Element Plus、pnpm 9.15、真实 Chrome。

**规格：** `specs/features/F-CUS-001-customer-master-and-local-lifecycle.md`

## 全局约束

- 当前总体阶段保持 `IMPLEMENTATION/开发阶段`；本计划不进入 Deployment、SIT、UAT 或 Release。
- 不实现 INT-03 的 CRM 连接、认证、同步、重试、对账或目录同步，不把种子或本地副本写成 CRM 成功事实。
- 不改旧 `pms_customer` 历史数据和只读页面，不双写、不迁移新增业务事实，不恢复旧写入口。
- 不修改受管规格快照；规格结论变化必须先进入规格仓库。当前计划只实现已锁定 Feature Spec。
- 新迁移使用当前下一个版本 `V123`，高段稳定 ID、`creator/updater=seed`、空库幂等；不得按测试环境条件分支。
- 测试基础设施固定使用 `npdms-50eb-test`：MySQL `23316`、Redis `26379`；测试应用固定使用后端 `59280`、跨租户后端 `59282`、Vite `19081`，不得占用开发端口 `58080/18081`。
- 不计算或比较哈希、校验和、指纹；Git 提交 ID 仅作已有证据关联。
- 复杂核心修复必须先运行聚焦失败测试确认 RED，再实施并复跑聚焦测试；正向闭环接通后才集中执行全量验证、真实浏览器、复审和整体提交。

---

## 一、当前实现审计与收口边界

### 直接保留

- `pms-module-customer-api`、`pms-module-customer` 及 `/pms/customers` 当前 Owner、命令、查询、历史、守卫、幂等、审计和 Outbox 实现。
- V106～V108 客户主档、权限切片、菜单权限前向迁移。
- PROJ/AST 客户摘要和引用守卫公开契约，旧 project 客户历史只读页面。
- 当前真实 MySQL 唯一性、CAS、软删除/恢复身份保持和权限切片测试。

### 必须补齐的四个产品缺口

1. `CustomerFormDrawer.vue` 暴露 `CRM_SYNC` 给业务用户，且未收集临时客户必填的 `temporaryReason/reconciliationPending`，导致真实临时客户创建不可用。
2. 客户分页 SQL 无条件 `deleted=b'0'`，页面又没有生命周期筛选，已删除客户永远无法在 UI 找到并执行恢复。
3. 创建、更新、停用、删除和恢复每次点击都生成新 `Idempotency-Key`；响应未知后重试会被当成新命令。
4. 当前库没有合法四级目录与无引用客户前置事实，导致创建、删除成功和恢复浏览器链无法执行；锁定规格允许使用不冒充 CRM 的受控测试种子。

### 不新增的能力

- 不新增 MarketRelation REST、CRM 字典工作台、自动级联选择器或 CRM 写身份。
- 不改变客户状态机、权限码、公开路径、跨域 Owner 或表的业务含义。
- 不把 F-AST-001、CUS-01、CUS-02、CUS-04 或 INT-03 并入本计划。

---

### Task 1：一次接通客户正向业务闭环

**Files:**

- Create: `sql/migrations/V123__fcus001_acceptance_seed.sql`
- Modify: `scripts/tests/test_fcus001_scope_migration.py`
- Modify: `pms-module-customer/src/main/resources/mapper/customer/CustomerMasterMapper.xml`
- Modify: `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/service/query/CustomerScopeSqlMySqlTest.java`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/customerInteraction.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/customerInteraction.spec.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/components/CustomerFormDrawer.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/pms/customer/index.vue`
- Modify: `scripts/tests/test_fcus001_customer_workbench.py`

**接口关系：**

- Consumes: 既有 `POST/PUT /pms/customers`、`actions/disable|delete|restore`、`CustomerApplicationService`、`CustomerMasterMapper.selectVisiblePage`。
- Produces: 可由业务页面创建 `PLATFORM_CREATED/PLATFORM_TEMPORARY` 客户、按 `DELETED` 查询并恢复、按完整用户意图稳定复用幂等键的闭环。

- [ ] **Step 1：编写并运行聚焦失败测试**

  在 Python/前端运行时测试中固定以下断言：业务表单不出现 `CRM_SYNC`；选择临时客户时必须提交非空 `temporaryReason` 且 `reconciliationPending=true`；平台客户不发送伪 CRM 字段；同一序列化意图在响应未知后返回同一 key，成功后释放，载荷变化生成新 key；生命周期筛选包含 `DELETED`。在 MySQL 测试中断言 `lifecycleStatus=DELETED` 只返回当前租户和当前授权切片内的软删除行，默认分页仍排除删除行。

  Run:

  ```powershell
  python -m unittest scripts.tests.test_fcus001_scope_migration scripts.tests.test_fcus001_customer_workbench
  mvn.cmd -pl pms-module-customer -am "-DskipITs=false" "-Dtest=CustomerScopeSqlMySqlTest" test
  corepack pnpm exec vitest run src/views/pms/customer/customerInteraction.spec.ts
  ```

  前端工作目录：`yudao-ui/yudao-ui-admin-vue3`。Expected：新增断言因上述四个目标行为缺失而 FAIL；失败原因不得是环境或语法错误。

- [ ] **Step 2：建立透明验收前置事实**

  `V123` 只插入 tenant 1 的一条高段 `cus_market_relation` 有效目录组合，以及执行浏览器正向/只读/无权/跨租户负向所需的稳定账号、角色、菜单权限和客户权限切片。目录 `source_version` 必须明确为 `FCUS001_ACCEPTANCE_SEED_V1`，名称包含“F-CUS-001 示例”，`creator/updater=seed`；不得插入 Customer、外部映射、历史、幂等、审计或 Outbox 业务事实。

- [ ] **Step 3：修复已删除客户查询与恢复入口**

  `CustomerMasterMapper.xml` 仅在 `lifecycleStatus == 'DELETED'` 时查询 `deleted=b'1'`，其他状态和默认查询保持 `deleted=b'0'`；租户和完整权限切片条件继续生效。页面增加生命周期筛选，删除行只显示“恢复”，不触发当前仅支持有效客户的详情/编辑读取。

- [ ] **Step 4：修复表单来源与临时客户语义**

  业务页面只提供“平台创建”和“平台临时”；临时客户显示并必填创建原因，提交时固定 `reconciliationPending=true`，平台创建固定为 `false`。不向业务页面提供 CRM 来源键、版本或 CRM_SYNC 选项；服务端既有 CRM 集成写边界保持不变。

- [ ] **Step 5：按完整用户意图复用幂等键**

  新建 CUS 本地 `customerInteraction.ts`，复制当前仓库已验证的 intent-store 行为：`key(intent)` 对同一稳定序列化意图返回同一 key，`complete(intent)` 只在成功后释放。创建、更新、停用、删除、恢复分别使用包含动作、对象/版本和完整载荷的意图；异常或未知响应不释放，用户修改载荷自然形成新意图。

- [ ] **Step 6：复跑聚焦测试确认 GREEN**

  重跑 Step 1 的三个命令。Expected：全部 PASS；默认分页、普通有效状态、现有更新/停用/删除守卫回归不变。

---

### Task 2：完成公开 UI/REST 的真实浏览器验收

**Files:**

- Create: `scripts/tests/run_fcus001_browser_acceptance.cjs`
- Create: `docs/engineering/evidence/f-cus-001-browser-evidence.json`
- Create: `output/f-cus-001-v18/browser-current/result.json`
- Create: `output/f-cus-001-v18/browser-current/screenshots/*.png`

**接口关系：**

- Consumes: Task 1 的 V123 前置事实和现有公开 UI/REST。
- Produces: AC-FCUS001-002～006、008、009、011 的当前候选真实浏览器证据；不形成 INT-03 证据。

- [ ] **Step 1：启动固定隔离环境**

  执行 `./scripts/test-infrastructure.ps1 reset`，确认 Compose 项目为 `npdms-50eb-test`。在宿主进程显式设置 `NPDMS_DB_NAME=npdms_test`、`NPDMS_MYSQL_PORT=23316`、`NPDMS_REDIS_PORT=26379`及本地 `.env` 测试凭据；后端监听 `59280`，跨租户负向后端监听 `59282`，Vite 监听 `19081`。

- [ ] **Step 2：经公开 UI/REST 完成正向闭环**

  脚本必须实际完成：平台客户创建；临时客户创建并显示“平台临时/待对账/原因”；刷新后持久化；平台字段更新；停用后不能用于新关系；对既有有引用客户删除被拒且零成功副作用；对本轮新建无引用客户删除成功；切换 `DELETED` 筛选后恢复原 ID/编码；同一响应未知意图重试不重复客户、历史、审计或 Outbox。

- [ ] **Step 3：完成权限、来源与租户负向**

  证明业务页面不能选择或伪造 `CRM_SYNC`；已有 CRM 映射客户的 CRM 权威字段只读，平台扩展字段仍可更新；获权只读主体无创建/更新/生命周期按钮且服务端拒绝直接命令；无权主体和第二租户查询/命令失败关闭，均无成功副作用。PROJ/AST 摘要不可用时显示 `available=false/dataAsOf`，不伪装为完整。

- [ ] **Step 4：完成四档响应式和运行时质量检查**

  在 320、768、1024、1440 宽度分别验证列表、创建/编辑抽屉、详情 Tab、删除/恢复筛选无页面级横向溢出；记录全部 HTTP 结果，断言 `consoleErrors/pageErrors/unexpectedResponses` 为空，并保存每档关键截图。

- [ ] **Step 5：生成结构化证据**

  `result.json` 记录测试端口、账号角色、创建客户 ID、生命周期版本、权限/租户负向、幂等重放、响应式和错误数组。总证据逐项映射 AC-FCUS001-001～012，明确 INT-03/CUS-01/02/04 与后续 Phase 未完成。

---

### Task 3：集中全量验证、复审与整体提交

**Files:**

- Modify only files required by failures directly attributable to F-CUS-001 current candidate.
- Modify: `tasks/features/F-CUS-001.md`

- [ ] **Step 1：运行当前候选的全量后端非环境测试**

  ```powershell
  mvn.cmd -pl pms-module-customer,pms-module-project,pms-module-asset,yudao-server -am "-DskipITs=true" test
  ```

  Expected：涉及平台、项目、资产、客户及装配依赖的 Reactor 全部 SUCCESS，0 failure/0 error。

- [ ] **Step 2：运行固定隔离 MySQL 验收**

  在 Task 2 的固定环境变量下执行：

  ```powershell
  mvn.cmd -pl pms-module-customer,pms-module-project,pms-module-asset -am "-DskipITs=false" "-Dtest=CustomerCommandMySqlTest,CustomerLifecycleMySqlTest,CustomerScopeSqlMySqlTest,AssetCustomerDeviceSummaryMySqlTest" test
  ```

  Expected：目标 IT 实际执行且非 SKIPPED；V1→V123 空库迁移、重复 reset 后种子唯一、客户创建/删除/恢复、租户/权限和摘要事实均 PASS。

- [ ] **Step 3：运行全量规格/契约、前端测试与构建**

  ```powershell
  python -m unittest discover -s scripts/tests -p 'test_*.py'
  corepack pnpm exec vitest run src/views/pms/customer/customerInteraction.spec.ts
  corepack pnpm ts:check
  corepack pnpm build:local
  mvn.cmd -DskipTests package
  ```

  pnpm 命令工作目录：`yudao-ui/yudao-ui-admin-vue3`。Expected：全量通过；Maven package 仅作为装配补充证据，不替代测试。

- [ ] **Step 4：运行真实浏览器并完成代码复审**

  执行 `node scripts/tests/run_fcus001_browser_acceptance.cjs`。按契约/架构、功能正确性、并发幂等、安全权限、数据迁移、前端可用性六轴审查本候选；只修复真实可达且属于 F-CUS-001 的问题，修改后重跑受影响测试及浏览器链。

- [ ] **Step 5：形成单一整体候选并提交**

  将 `tasks/features/F-CUS-001.md` 更新为 `IMPLEMENTATION_REVIEW_PENDING`，只保留一条不超过 300 字的检查点。显式暂存本计划及 F-CUS-001 相关实现、测试和证据；排除 `.codex-tmp/` 与其他 Feature 变化。执行 `git diff --check --cached` 后创建一个整体候选提交，不 push。

- [ ] **Step 6：独立 Implementation Done 送审**

  按工程链固定 17 字段格式提交独立评审。只有获得 GO 后才将 `Implementation Done Gate` 前向回写为 PASS；若 NO-GO，仅修当前唯一最小阻断，不重开 Feature Ready 或已通过的合并基线。

---

## Technical Plan Gate

本计划必须先取得独立 Technical Plan GO。GO 仅授权执行上述单一整体 Implementation 收口闭环，不代表 F-CUS-001 Implementation Done 或任何后续 Phase 通过。
