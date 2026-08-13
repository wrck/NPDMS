# SDS Phase 3 Runtime and Release Assurance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 依据 PRD V1.6、Phase 1/2 SDS 基线和 NPDMS 实现事实，形成可验证的安全、审计可观测、部署、性能和测试设计，并通过 Phase 3 独立门禁。

**Architecture:** 五份正式分册各自只承担一个运行保障主题，通过统一的发布批次、构建号、Requirement ID、traceId、环境证据和测试报告ID串联。当前宿主机应用 + Docker基础设施只作为已验证开发/验收运行剖面；生产拓扑、容量和恢复目标若缺少Owner证据，必须作为明确门禁而非用建议值伪装完成。

**Tech Stack:** JDK 25、Spring Boot/基础平台、Vue 3、Node.js ≥20.19、pnpm 9.15.5、MySQL 8.4、Redis 7.4、Docker Compose基础设施、Flyway CLI 11.10.5、Playwright、Maven、Python 3.13校验脚本。

## Execution Status（2026-08-13）

- Tasks 1～7：已完成设计、115项验证映射、自动校验和负向测试；全部规格门禁当前通过。
- Task 8 Step 1：自审已完成，结论`NO-GO / IN_REVIEW`；P3-E01～E06、P3-E09是批准阻塞，P3-E07按Feature阻塞，P3-E08阻塞前端实现/发布。
- Task 8 Step 2～4：待外部证据关闭后执行独立评审；禁止提前生成SDS总册或把分册转`BASELINE`。
- 用户新增的数据迁移要求已形成`08a-domain-entity-migration-alignment.md`，覆盖全部显式领域数据对象；字段级迁移仍受`AI-MIG-000`约束。
- PRD V1.7的P3-E09候选DDL已按ADR-0025补齐13张差量表，并在隔离MySQL 8.4.10以同一DDL哈希执行通过；证据包已重建。该项仍为`BLOCKED_BY_REVIEW`，不得把可执行性当作Reviewer批准或生产迁移授权。

## Global Constraints

- 业务语义优先级：PRD V1.7 > 工程链 > SDS > Feature > Plan > Task > Code。
- NFR-01：50并发登录用户持续30分钟、累计不少于10000次有效请求、服务端错误率≤0.5%、核心页面与交互P95≤2秒。
- NFR-01：项目量取 `max(迁移量×2, 20万)`、任务量取 `max(迁移量×2, 200万)`；覆盖单项目树1万、单任务树5万、直接子节点2000、测试深度30，深度不是业务上限。
- NFR-01：50MB文件完整上传和哈希一致；超过50MB明确拒绝且不产生有效附件。
- NFR-01：Chrome/Edge/Firefox稳定版与1920×1080、1440×900、1366×768、1024×768视口执行同一核心用例。
- NFR-02：凭证使用AES-256或不低于同等强度的批准算法，密钥与业务数据分离；唯一标记测试秘密在全链路明文命中数必须为0。
- NFR-02：V2在线巡检命令默认超时30秒，可配置；V1不以巡检专项门禁阻塞发布。
- NFR-03：割接/巡检节点通知到达率≥99%；巡检内部项目进度更新时间≤1分钟。
- 当前已验证运行剖面：前后端宿主机运行；Docker只承载MySQL、Redis和Flyway；前端不得放入容器。
- 不修改已执行Flyway迁移；发布与回退均使用前向迁移和兼容发布策略。
- 不持久化或输出设备密码、私钥、Token、完整授权码和认证头。
- 本计划不授权Git提交；提交仅在用户明确要求时执行。

---

### Task 1: Phase 3事实盘点与门禁初始化

**Files:**
- Create: `docs/engineering/gates/phase-3/README.md`
- Create: `docs/engineering/gates/phase-3/runtime-fact-inventory.md`
- Create: `docs/engineering/gates/phase-3/gate-status.md`

**Interfaces:**
- Consumes: PRD NFR-01～03、Phase 2 BASELINE、`E:\AICoding\Projects\NPDMS\docs\development.md`、`docs/upstream-sources.md`、`compose.yaml`。
- Produces: 运行版本、启动/构建/迁移命令、已证实边界、生产未决项和Phase 3 Gate编号。

- [ ] **Step 1:** 记录JDK/Node/pnpm/MySQL/Redis/Flyway、前后端宿主机和Docker基础设施边界，不把本地剖面写成生产事实。
- [ ] **Step 2:** 登记构建、迁移、健康检查、停止和保留卷命令，以及当前已验证/未验证状态。
- [ ] **Step 3:** 将生产数据库高可用、备份恢复目标、入口网关、证书、密钥服务、日志/指标后端和容量规格登记为证据门禁并指定确认角色，不填伪数值。
- [ ] **Step 4:** 建立P3-01～P3-05分册状态与Gate检查表，初始结论为`IN_REVIEW / NOT_READY_FOR_SDS_BASELINE`。

### Task 2: 安全设计

**Files:**
- Create: `docs/design/14-security-design.md`

**Interfaces:**
- Consumes: 07授权、10 API、12集成、13文件、16异常；NFR-01/02、INT-09、INT-12。
- Produces: 信任区、认证会话、服务身份、数据分类、凭证/密钥、接口/文件/浏览器安全、威胁控制和安全验收矩阵。

- [ ] **Step 1:** 定义浏览器、入口代理、后端、数据库/Redis/对象存储、外部系统和DAC执行进程的信任边界及允许数据流。
- [ ] **Step 2:** 明确LDAP/AD认证只建立平台身份，业务RBAC/DataScope由服务端再次判定；回调和服务间调用必须认证、验签、防重放。
- [ ] **Step 3:** 将凭证生命周期、AES-256或同等强度、密钥版本、轮换、创建人默认授权、任务级短期取密和明文禁止面落实为控制与失败处理。
- [ ] **Step 4:** 定义输入校验、输出编码、CSRF/CORS、安全响应头、文件类型/哈希/扫描、SSRF/远程端点校验、限流和错误脱敏边界。
- [ ] **Step 5:** 给出NFR-01/02逐条安全验收、秘密扫描范围、100%权限拒绝用例和高风险操作在审计不可用时的fail-closed规则。

### Task 3: 审计与可观测设计

**Files:**
- Create: `docs/design/17-audit-and-observability.md`

**Interfaces:**
- Consumes: 11事件、12集成、15并发、16异常；NFR-01～03。
- Produces: 审计事件模型、日志/指标/追踪、告警、看板、采样与敏感字段规则、SLO证据字段。

- [ ] **Step 1:** 分离不可变业务审计、运行日志、指标、Trace和安全事件，定义各自Owner、字段、关联ID与查询权限。
- [ ] **Step 2:** 覆盖登录、权限/角色、项目层级、审批、凭证、文件、集成补偿、状态迁移、发布迁移和回退审计；Word正文不做内容审计。
- [ ] **Step 3:** 定义页面/API延迟P50/P95/P99、错误率、并发、树投影延迟、Outbox/Inbox、回调乱序、缓存、DB、文件和通知到达率指标。
- [ ] **Step 4:** 定义高风险审计链路不可用时阻断、普通低风险操作的受控缓冲、告警恢复和补偿核验。
- [ ] **Step 5:** 给出traceId/correlationId/businessId/releaseId贯穿API、事件、外部调用、回调和审计的验证方法。

### Task 4: 部署、迁移与回退设计

**Files:**
- Create: `docs/design/18-deployment-design.md`

**Interfaces:**
- Consumes: NPDMS已验证构建/运行事实、09数据库、12集成、13文件、14安全、17可观测。
- Produces: 制品、配置、环境、启动顺序、数据库迁移、发布检查、回退/前滚、灾难恢复证据边界。

- [ ] **Step 1:** 固化可复现制品：后端JAR、前端静态构建、Flyway SQL、配置清单、SBOM/校验和、Requirement和releaseId；禁止把开发服务命令冒充生产部署。
- [ ] **Step 2:** 记录已验证开发/验收剖面的Docker基础设施和宿主机应用启动顺序、健康检查、停止及卷保留命令。
- [ ] **Step 3:** 定义Expand→Backfill→Verify→Switch→Contract前向迁移，禁止修改已执行迁移；数据库变更与应用版本建立兼容窗口。
- [ ] **Step 4:** 定义发布前检查、灰度/滚动逻辑、失败判定、应用回退、数据库前滚修复、外部事件/任务对账和文件完整性复核。
- [ ] **Step 5:** 将生产高可用、RPO/RTO、备份介质、恢复演练、域名证书、密钥服务和运维Owner列为正式发布前证据；未闭环时Phase 3保持阻塞。

### Task 5: 性能与容量设计

**Files:**
- Create: `docs/design/19-performance-design.md`

**Interfaces:**
- Consumes: NFR-01/02/03、08/09数据模型、15并发、17指标、18环境剖面。
- Produces: 场景模型、数据模型、负载模型、测量边界、容量拐点、诊断和发布判定。

- [ ] **Step 1:** 把50用户/30分钟/10000请求/P95≤2秒/错误率≤0.5%转换为可执行负载阶段和统一统计口径。
- [ ] **Step 2:** 定义项目/任务规模、树形形状、权限分布、设备/文件/事件/集成数据集生成规则及数据集版本。
- [ ] **Step 3:** 定义页面端到端和服务端API分别测量，冷启动、50MB传输和外部系统耗时独立统计。
- [ ] **Step 4:** 定义树查询、列表、权限过滤、文件上传、DAC任务、通知事件等场景的阈值、资源指标和失败诊断证据。
- [ ] **Step 5:** 定义超过50并发的探索性容量测试只报告拐点和降级，不替代50并发发布门禁。

### Task 6: 测试设计与Requirement覆盖

**Files:**
- Create: `docs/design/20-test-design.md`
- Modify: `docs/traceability/phase2-contract-map.md`（由生成器扩展为Phase 2/3运行验证链接时生成）
- Modify: `scripts/generate_phase2_contract_map.py`

**Interfaces:**
- Consumes: PRD 115项、14/17/18/19和全部Phase 1/2分册。
- Produces: 测试层次、环境/数据、正常/异常/权限拒绝/幂等/并发矩阵、浏览器和发布验收、Requirement到测试类别映射。

- [ ] **Step 1:** 定义单元、聚合、数据库迁移、API契约、事件、集成、文件、安全、性能、浏览器E2E和发布恢复测试边界。
- [ ] **Step 2:** 每个有副作用接口覆盖首次成功、同键重放、同键异摘要、权限拒绝、版本冲突、非法状态、外部超时实际成功和恢复对账。
- [ ] **Step 3:** 定义真实浏览器四视口×三浏览器核心路径，必须执行登录、点击、填写、保存、刷新、返回和状态流转。
- [ ] **Step 4:** 为NFR-01～03给出准确场景、数据、指标、通过判定和报告字段；V1/V2门禁分离。
- [ ] **Step 5:** 扩展显式契约映射，为115项增加Phase 3测试类别和证据类型，校验无空项。

### Task 7: Phase 3自动校验与负向测试

**Files:**
- Create: `scripts/validate_sds_phase3.py`
- Create: `scripts/tests/test_validate_sds_phase3.py`

**Interfaces:**
- Consumes: 14/17/18/19/20正式分册、Phase 3 Gate、115项契约映射。
- Produces: 可重复的结构/语义门禁和负向失败证据。

- [ ] **Step 1:** 先写负向测试：缺NFR阈值、缺安全秘密扫描、缺部署回退、缺权限拒绝/幂等/并发测试、缺Requirement运行验证链接时必须失败。
- [ ] **Step 2:** 实现校验器，检查五分册元数据、NFR精确阈值、运行事实与生产未决项分离、115项测试类别和真实链接。
- [ ] **Step 3:** 运行 `py -3.13 -B -m unittest scripts.tests.test_validate_sds_phase3`，预期正例和全部负例通过。
- [ ] **Step 4:** 运行Phase 1/2/3、PRD、领域、业务命名和`git diff --check`全量门禁。

### Task 8: 自审、独立复审与SDS总册

**Files:**
- Create: `docs/engineering/gates/phase-3/self-review.md`
- Create after independent review: `docs/engineering/gates/phase-3/independent-review.md`
- Create only after GO: `docs/design/00-system-detailed-design.md`
- Modify only after GO: `docs/engineering/gates/phase-3/gate-status.md`

**Interfaces:**
- Consumes: 五分册、自动校验、实现事实、生产发布证据和独立只读复审。
- Produces: Phase 3 GO/NO-GO与SDS总册索引。

- [ ] **Step 1:** 自审逐项核对NFR实现/验证、发布迁移回退、安全审计、正常/异常/权限/幂等/并发覆盖。
- [ ] **Step 2:** 独立评审验证文档内容实质和可执行性，不以脚本PASS替代语义审查。
- [ ] **Step 3:** 若存在Required，保持`IN_REVIEW`并逐项修复后定点复审；不得提前生成基线总册。
- [ ] **Step 4:** 仅在GO后将14/17/18/19/20转为`BASELINE`，生成只含导航、边界、关键决策和追溯入口的`00-system-detailed-design.md`。

## Self-Review Result

- Spec coverage：覆盖工程链Phase 3五项输出和四项Gate；NFR-01～03均有设计与验证任务。
- Placeholder scan：没有用占位语句代替实施动作；生产缺失事实明确作为证据门禁。
- Interface consistency：14安全、17可观测、18部署、19性能、20测试按顺序消费前序输出；总册只在独立GO后生成。
