# 项目实施交付管理平台任务清单

> 阶段：TASKS（已确认，确认日期为2026-07-28）
> 上游计划：`tasks/plan.md`
> 规格基线：`specs/001-project-delivery-platform/`
> 执行状态：IMPLEMENT尚未开始；进入实现后必须按任务依赖和检查点执行。

## 1. 使用规则

- 按任务ID顺序和依赖执行，检查点未通过不得进入下一组。
- 每项任务必须先建立失败测试，再完成最小实现，再运行验证。
- 每项任务完成后更新本文件勾选状态，并在验收追溯矩阵登记测试证据。
- 每个自研任务最多涉及5个文件区域；机械上游快照导入按模块目录审查，不与自研逻辑混合。
- 每个逻辑任务独立提交，提交信息引用任务ID和主要FR。
- 发现规格歧义时先修改规格并重新确认，不在实现中自行选择口径。
- Yudao平台接口保持上游定义；新增PMS接口执行`api-design-specification.md`。
- V3任务不进入本清单，只维护第12节的数据演进门禁。

## 2. 路径标识

以下标识在任务表中代表确定的目标目录；进入实现后，任务只能在列出的目录内选择不超过5个具体文件，超出时拆分任务。

| 标识 | 目标路径 |
| --- | --- |
| `[ROOT]` | `pom.xml`、`.gitignore`、根工程说明 |
| `[UPSTREAM]` | `docs/upstream-sources.md` |
| `[SYSTEM]` | `yudao-module-system/` |
| `[INFRA]` | `yudao-module-infra/` |
| `[BPM]` | `yudao-module-bpm/` |
| `[SERVER]` | `yudao-server/` |
| `[SQL]` | `sql/` |
| `[PROJECT-BIZ]` | `pms-module-project/pms-module-project-biz/src/main/java/` |
| `[PROJECT-API]` | `pms-module-project/pms-module-project-api/src/main/java/` |
| `[PROJECT-TEST]` | `pms-module-project/pms-module-project-biz/src/test/java/` |
| `[ENG-BIZ]` | `pms-module-engineering/src/main/java/` |
| `[ENG-TEST]` | `pms-module-engineering/src/test/java/` |
| `[CUT-BIZ]` | `pms-module-cutover/src/main/java/` |
| `[CUT-TEST]` | `pms-module-cutover/src/test/java/` |
| `[SRV-BIZ]` | `pms-module-service/src/main/java/` |
| `[SRV-TEST]` | `pms-module-service/src/test/java/` |
| `[AST-BIZ]` | `pms-module-asset/src/main/java/` |
| `[AST-API]` | `pms-module-asset/pms-module-asset-api/src/main/java/`；仅在稳定调用方存在时创建 |
| `[AST-TEST]` | `pms-module-asset/src/test/java/` |
| `[OUT-BIZ]` | `pms-module-outsourcing/src/main/java/` |
| `[OUT-TEST]` | `pms-module-outsourcing/src/test/java/` |
| `[ANA-BIZ]` | `pms-module-analytics/src/main/java/` |
| `[ANA-TEST]` | `pms-module-analytics/src/test/java/` |
| `[INT-BIZ]` | `pms-module-integration/src/main/java/` |
| `[INT-TEST]` | `pms-module-integration/src/test/java/` |
| `[UI-API]` | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/` |
| `[UI-VIEW]` | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/` |
| `[UI-ROUTE]` | `yudao-ui/yudao-ui-admin-vue3/src/router/`及菜单初始化脚本 |
| `[E2E]` | `tests/e2e/` |
| `[PERF]` | `tests/performance/` |
| `[SEC]` | `tests/security/` |
| `[TRACE]` | `specs/001-project-delivery-platform/appendices/acceptance-traceability.md` |

## 3. 通用完成定义

每项任务必须同时满足：

- 规格：实现行为与所列FR、BR、DR、API和AC一致。
- 数据：迁移可从空库执行，公共字段、约束、索引和数据所有权明确。
- 服务端：权限、状态、租户上下文、业务唯一性、幂等和并发校验齐全。
- API：请求响应、错误码、契约编号和OpenAPI定义齐全。
- UI：存在界面任务时，按钮权限、响应式布局、加载、空态和错误反馈完整。
- 测试：聚焦测试、模块测试和构建通过；不得删除失败测试。
- 追溯：任务ID、FR、测试用例和提交记录写入`[TRACE]`。
- 安全：没有密码、设备凭据、密钥、生产地址或未脱敏数据。

通用验证命令：

```text
mvn -pl pms-module-project -am test
mvn -pl pms-module-engineering -am test
mvn -pl pms-module-cutover -am test
mvn -pl pms-module-service -am test
mvn -pl pms-module-asset -am test
mvn -pl pms-module-outsourcing -am test
mvn -pl pms-module-analytics -am test
mvn -pl pms-module-integration -am test
mvn clean verify
pnpm --dir yudao-ui/yudao-ui-admin-vue3 lint
pnpm --dir yudao-ui/yudao-ui-admin-vue3 build:prod
pnpm --dir tests/e2e test
```

执行任务时选择所属模块的聚焦测试命令；TASKS清单中的具体验证列优先于通用命令。

## 4. CP：基础工程与门禁

| 状态/ID | 任务与范围 | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-CP-001 | 固化工程决策PD-001至PD-006 | 数据库产品、迁移机制、前端仓库、E2E和本地基础设施选择形成决策记录；未决项具有责任人和阻塞范围 | 人工评审决策记录；确认没有未标识的实现假设 | 无 | `[UPSTREAM]`、`tasks/plan.md` | S |
| - [ ] T-CP-002 | 导入mini根工程、dependencies和framework快照 | 文件来源匹配mini锁定提交；共享文件未混入完整仓库版本；根revision保持`2026.06-jdk25-SNAPSHOT` | `git diff`核验来源；`mvn -N help:evaluate -Dexpression=revision -q -DforceStdout` | T-CP-001 | `[ROOT]`、`yudao-dependencies/`、`yudao-framework/`、`[UPSTREAM]` | M（机械快照） |
| - [ ] T-CP-003 | 导入system、infra和server快照 | 三个模块来源可追溯；根POM模块装配正确；不包含PMS业务改动 | `mvn clean package -DskipTests` | T-CP-002 | `[SYSTEM]`、`[INFRA]`、`[SERVER]`、`[ROOT]`、`[UPSTREAM]` | M（机械快照） |

### Checkpoint CP-A

- [ ] 上游提交、文件范围和共享文件优先级可追溯。
- [ ] mini基础工程可以完成后端编译。
- [ ] 人工评审通过后继续BPM和前端导入。

| 状态/ID | 任务与范围 | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-CP-004 | 从完整仓库导入BPM完整迁移单元 | BPM源码、依赖、SQL、菜单权限和前端增量来自同一锁定提交；根POM和server显式装配 | `mvn -pl yudao-module-bpm -am test`；启动后创建并查询测试流程定义 | T-CP-003 | `[BPM]`、`[SERVER]`、`[SQL]`、`[UI-VIEW]`、`[UPSTREAM]` | M（机械快照） |
| - [ ] T-CP-005 | 建立完整Vue3管理端基线 | 官方Vue3前端可安装、启动和构建；mini与BPM增量正确叠加；无PMS页面 | `pnpm install`；`pnpm lint`；`pnpm build:prod` | T-CP-001 | `yudao-ui/yudao-ui-admin-vue3/`、`[UPSTREAM]` | M（机械快照） |
| - [ ] T-CP-006 | 建立数据库迁移和本地配置基线 | 空库可自动初始化；重复执行有历史校验；配置模板无真实凭据；数据库、Redis和文件服务可连接 | 空库启动；重复启动；`mvn clean verify` | T-CP-003、T-CP-004 | `[SQL]`、`[SERVER]`、`docker/`、`docs/development.md` | M |

### Checkpoint CP-B

- [ ] 后端、前端、数据库、Redis和BPM可以在全新环境启动。
- [ ] 上游测试、迁移重复执行和基础登录通过。
- [ ] 人工评审通过后建立自研模块和测试骨架。

| 状态/ID | 任务与范围 | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-CP-007 | 创建V1业务模块骨架和依赖边界检查 | 创建project、engineering、cutover和service模块；无稳定调用方时不创建空`-api`；依赖符合DEC-013 | `mvn clean verify`；运行模块依赖边界测试 | T-CP-006 | `[ROOT]`、`pms-module-project/`、`pms-module-engineering/`、`pms-module-cutover/`、`pms-module-service/` | M |
| - [ ] T-CP-008 | 创建资产及V2业务模块骨架 | 创建asset、outsourcing、analytics和integration模块；分析和集成模块不拥有核心主数据 | `mvn clean verify`；运行模块依赖边界测试 | T-CP-007 | `[ROOT]`、`pms-module-asset/`、`pms-module-outsourcing/`、`pms-module-analytics/`、`pms-module-integration/` | M |

### Checkpoint CP-C1

- [ ] 全部计划内PMS模块可以参与统一构建。
- [ ] 模块边界检查未发现跨`-biz`依赖或空API模块。

| 状态/ID | 任务与范围 | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-CP-009 | 创建测试与追溯骨架 | 单元、集成、契约、E2E、性能和安全目录可执行；测试结果可关联任务和FR | 分别运行骨架健康测试；检查`[TRACE]`新增证据字段 | T-CP-008、T-CP-005 | `[E2E]`、`[PERF]`、`[SEC]`、`[TRACE]`、`pom.xml` | M |
| - [ ] T-CP-010 | 完成基础工程验收 | 全新检出后可按文档构建、迁移、启动、登录和停止；控制台无未处理错误 | `mvn clean verify`；前端lint/build；真实浏览器登录冒烟 | T-CP-009 | `docs/development.md`、`[E2E]`、`[TRACE]` | S |

### Checkpoint CP-C2

- [ ] CP-01、CP-02计划目标全部满足。
- [ ] 任何PMS业务代码尚未绕过规格和任务门禁。
- [ ] 人工确认后开始V1平台能力。

## 5. V1-IU-01：平台能力

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-PLT-001 | 本地认证、组织同步与角色权限；`FR-PLT-001`、`FR-PLT-002` | 用户可登录；组织和角色可配置；菜单、按钮和API权限服务端生效 | system模块测试；真实浏览器登录和无权按钮验证 | T-CP-010 | `[SYSTEM]`、`[SERVER]`、`[UI-ROUTE]`、`[E2E]`、`[TRACE]` | M |
| - [ ] T-V1-PLT-002 | 项目数据权限与敏感字段访问；`FR-PLT-003`、`FR-PLT-004` | 组织、区域、项目层级、成员和创建人范围可组合；敏感字段脱敏和访问审批生效 | 权限矩阵参数化测试；水平/垂直越权测试 | T-V1-PLT-001 | `[SYSTEM]`、`[PROJECT-BIZ]`、`[SEC]`、`[TRACE]` | M |
| - [ ] T-V1-PLT-003 | 操作和流程审计；`FR-PLT-005` | 状态动作、审批、导出、文件和权限变更记录操作者、前后值、业务键和时间 | 审计集成测试；页面检索审计记录 | T-V1-PLT-001 | `[INFRA]`、`[BPM]`、`[PROJECT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V1-PLT-A

- [ ] 登录、功能权限、数据权限、字段权限和审计形成闭环。
- [ ] 无权限调用不能依赖前端隐藏实现。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-PLT-004 | 可配置流程与业务状态动作；`FR-PLT-006` | BPM实例关联业务键；审批结果通过动作服务更新业务状态；重复回调幂等 | BPM集成测试；审批通过、驳回、撤回浏览器旅程 | T-V1-PLT-003 | `[BPM]`、`[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[E2E]`、`[TRACE]` | M |
| - [ ] T-V1-PLT-005 | 待办与通知中心；`FR-PLT-007` | 业务事件生成待办和通知；具备接收人、去重、重试和已读状态 | 事件重复投递测试；通知中心浏览器验证 | T-V1-PLT-004 | `[SYSTEM]`、`[INFRA]`、`[UI-VIEW]`、`[E2E]`、`[TRACE]` | M |
| - [ ] T-V1-PLT-006 | 文件、版本和归档基础能力；`FR-PLT-008` | 上传、预览、下载、版本、校验值和归档可用；越权文件访问被拒绝并审计 | infra文件测试；浏览器上传、刷新、下载验证 | T-V1-PLT-003 | `[INFRA]`、`[UI-API]`、`[UI-VIEW]`、`[SEC]`、`[TRACE]` | M |

### Checkpoint V1-PLT-B

- [ ] BPM、待办、通知和文件可由业务模块复用。
- [ ] 重复事件和重复回调不会生成重复业务结果。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-PLT-007 | PMS事件和集成基础契约；`FR-PLT-009` | Business、Internal、Integration和Event API分类落实；不默认开放Business API | 契约测试；OpenAPI和事件Schema检查 | T-V1-PLT-004 | `[PROJECT-API]`、`[INT-BIZ]`、`[INT-TEST]`、`[TRACE]` | M |
| - [ ] T-V1-PLT-008 | 性能、兼容和异步任务基线；`FR-PLT-010` | 建立NFR测量、异步导入导出状态和浏览器兼容基线 | 性能冒烟；Chrome/Edge目标版本冒烟；构建检查 | T-CP-009 | `[PERF]`、`[INFRA]`、`[E2E]`、`[TRACE]` | M |
| - [ ] T-V1-PLT-009 | 统一编码与数据字典；`FR-PLT-011` | 业务编码唯一可追溯；字典版本和启停受控；引用中编码不可物理删除 | 字典并发和引用测试；管理页面CRUD验证 | T-V1-PLT-001 | `[SYSTEM]`、`[PROJECT-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V1-PLT-C

- [ ] `FR-PLT-001`至`FR-PLT-011`具有实现和测试证据。
- [ ] 人工确认平台公共契约后冻结并进入项目核心。

## 6. V1-IU-02与IU-03：项目、层级、WBS和设备

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-PROJ-001 | 客户、联系人和客户资产全景；`FR-PROJ-005`、`FR-PROJ-007` | 客户与联系人生命周期受控；客户全景关联项目和设备；字段权限生效 | project模块测试；浏览器创建、编辑、失效和全景查询 | T-V1-PLT-009 | `[SQL]`、`[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-PROJ-002 | 项目主数据同步；`FR-PROJ-008` | 业务键幂等；来源和同步批次可追溯；冲突进入失败明细 | 同步重复和冲突集成测试 | T-V1-PLT-007、T-V1-PROJ-001 | `[PROJECT-BIZ]`、`[INT-BIZ]`、`[INT-TEST]`、`[TRACE]` | M |
| - [ ] T-V1-PROJ-003 | 项目分类、指派和团队；`FR-PROJ-010`、`FR-PROJ-012`、`FR-PROJ-013` | 分类和重大项目标识受规则控制；负责人指派和团队权限立即生效 | 规则和权限测试；浏览器指派、换人和越权验证 | T-V1-PROJ-001 | `[SQL]`、`[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V1-PROJ-A

- [ ] 客户、项目来源、分类、负责人和团队可在真实界面闭环。
- [ ] 项目数据权限与团队变化一致。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-PROJ-004 | 非固定项目树；`FR-PROJ-002`、`FR-PROJ-009` | 任意深度创建、排序和后代查询；无固定级别字段；循环和跨租户挂接被拒绝 | 项目树属性测试；10万项目查询性能测试 | T-V1-PROJ-003 | `[SQL]`、`[PROJECT-BIZ]`、`[PROJECT-API]`、`[PROJECT-TEST]`、`[PERF]` | M |
| - [ ] T-V1-PROJ-005 | 项目拆分、合并和子树移动；`FR-PROJ-003` | 操作为原子事务；权限继承和缓存重算；版本冲突不静默覆盖 | 拆分合并回滚测试；浏览器移动后刷新验证 | T-V1-PROJ-004 | `[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[UI-VIEW]`、`[SEC]`、`[TRACE]` | M |
| - [ ] T-V1-PROJ-006 | 非固定任务WBS；`FR-PROJ-004` | 任意深度WBS、移动、依赖和汇总可用；500万任务容量策略验证 | WBS属性和并发测试；全后代汇总性能测试 | T-V1-PROJ-004 | `[SQL]`、`[PROJECT-BIZ]`、`[PROJECT-API]`、`[PROJECT-TEST]`、`[PERF]` | M |

### Checkpoint V1-PROJ-B

- [ ] 项目树、任务WBS、拆分合并和权限继承专项测试通过。
- [ ] 层级契约冻结后才允许专业模块依赖。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-PROJ-007 | 阶段模板、计划和实际时间；`FR-PROJ-015`、`FR-PROJ-017` | 项目类型选择阶段模板；计划和实际时间版本化；阶段顺序来自确认配置 | 模板实例化测试；浏览器创建项目阶段 | T-V1-PROJ-003、业务阶段口径确认 | `[SQL]`、`[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-PROJ-008 | 阶段完成门禁和超期预警；`FR-PROJ-016`、`FR-PROJ-019` | 完成动作逐项校验任务、交付件、问题和审批；超期生成去重预警 | 门禁参数化测试；超期任务调度测试 | T-V1-PROJ-006、T-V1-PROJ-007 | `[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-PROJ-009 | 项目全景、进度和风险；`FR-PROJ-011`、`FR-PROJ-021`、`FR-PROJ-026` | 全景聚合项目、阶段、任务、设备、交付件和风险；风险处置可闭环 | 聚合查询和权限测试；浏览器全景刷新验证 | T-V1-PROJ-006、T-V1-PROJ-008 | `[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[UI-VIEW]`、`[PERF]`、`[TRACE]` | M |

### Checkpoint V1-PROJ-C

- [ ] V1项目域17项FR全部具有证据。
- [ ] 阶段、任务、设备和风险契约可供工程域使用。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-AST-001 | 设备和序列号档案；`FR-RES-001` | 序列号唯一；设备归属客户和项目；状态变化使用动作并审计 | 设备唯一性、权限和状态测试 | T-V1-PROJ-001 | `[SQL]`、`[AST-BIZ]`、`[AST-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-AST-002 | 设备版本和配置Log档案；`FR-RES-002`、`FR-RES-003` | 版本历史不可覆盖；配置Log与设备、来源、采集时间和文件关联 | 版本追加和越权下载测试；浏览器档案查询 | T-V1-AST-001、T-V1-PLT-006 | `[AST-BIZ]`、`[AST-TEST]`、`[UI-VIEW]`、`[SEC]`、`[TRACE]` | M |

### Checkpoint V1-AST

- [ ] 设备、序列号、版本和配置档案可被工程、割接和巡检引用。

## 7. V1-IU-04：工程实施

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-ENG-001 | 现场工勘；`FR-ENG-001` | 工勘表单、附件、结论和责任人完整；未确认工勘阻止受控后续动作 | 工勘门禁测试；浏览器填写、上传、确认、刷新 | T-V1-PROJ-008、T-V1-AST-001 | `[SQL]`、`[ENG-BIZ]`、`[ENG-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-ENG-002 | 需求分析和接口规划；`FR-ENG-004`、`FR-ENG-005` | 需求项和接口规划结构化；与项目、设备、任务和工勘关联 | 字段校验和权限测试；浏览器增删改查 | T-V1-ENG-001 | `[ENG-BIZ]`、`[ENG-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-ENG-003 | 实施方案编制和前序数据带入；`FR-ENG-011`、`FR-ENG-013` | 从工勘和需求生成方案草稿；带入数据保留来源；人工修改不反写来源 | 方案生成和来源追溯测试 | T-V1-ENG-002 | `[SQL]`、`[ENG-BIZ]`、`[ENG-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V1-ENG-A

- [ ] 工勘、需求和方案草稿形成可运行纵向旅程。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-ENG-004 | 方案分级审核和基线；`FR-ENG-016` | 审核级别由规则决定；通过后形成不可覆盖版本；驳回保留意见 | BPM审核和版本测试；浏览器审批旅程 | T-V1-ENG-003、T-V1-PLT-004 | `[ENG-BIZ]`、`[ENG-TEST]`、`[BPM]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-ENG-005 | 资源就绪和到货签收；`FR-ENG-018`、`FR-ENG-021` | 资源检查和签收关联设备、数量、异常和证据；未就绪阻止实施 | 就绪门禁和签收幂等测试 | T-V1-ENG-004、T-V1-AST-001 | `[ENG-BIZ]`、`[ENG-TEST]`、`[AST-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-ENG-006 | 硬件安装和配置调试；`FR-ENG-022`、`FR-ENG-023` | 安装、位置、照片和配置记录关联设备；状态动作原子更新 | 安装配置事务测试；浏览器保存刷新验证 | T-V1-ENG-005 | `[ENG-BIZ]`、`[ENG-TEST]`、`[AST-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V1-ENG-B

- [ ] 审核基线、资源、到货、安装和配置可追溯。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-ENG-007 | 业务联调；`FR-ENG-024` | 联调用例、参与方、结果、问题和证据完整；失败项不能静默通过 | 联调结果和门禁测试；浏览器联调旅程 | T-V1-ENG-006 | `[ENG-BIZ]`、`[ENG-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-ENG-008 | 实施问题整改和交付件归集；`FR-ENG-026`、`FR-ENG-027` | 问题识别、责任、整改、验证和关闭完整；交付件按阶段自动归集 | 问题状态机和归集测试；浏览器闭环验证 | T-V1-ENG-007、T-V1-PLT-006 | `[ENG-BIZ]`、`[ENG-TEST]`、`[PROJECT-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V1-ENG-C

- [ ] V1工程域13项FR全部具有证据。
- [ ] 工程完成后可按项目类型进入割接、巡检或验收。

## 8. V1-IU-05：割接

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-CUT-001 | 割接入口和多来源创建；`FR-CUT-001`、`FR-CUT-002` | 仅适用项目可创建；来源项目/任务可追溯；重复创建幂等 | 入口门禁和幂等测试 | T-V1-ENG-008、割接适用类型确认 | `[SQL]`、`[CUT-BIZ]`、`[CUT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-CUT-002 | 类型、组网、风险和信息采集；`FR-CUT-003`、`FR-CUT-004`、`FR-CUT-005` | 动态清单按类型和组网生成；风险、附件和责任人完整 | 动态清单规则测试；浏览器采集验证 | T-V1-CUT-001 | `[CUT-BIZ]`、`[CUT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-CUT-003 | 等级评估和方案编制；`FR-CUT-006`、`FR-CUT-008` | 评估规则可解释；方案包含前置检查、步骤、验证和回退 | 等级边界和方案完整性测试 | T-V1-CUT-002 | `[CUT-BIZ]`、`[CUT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V1-CUT-A

- [ ] 割接准备、评估和方案旅程可运行。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-CUT-004 | 方案评审和执行记录；`FR-CUT-009`、`FR-CUT-011` | 未评审或不在时间窗不能执行；执行项实时记录操作者和时间 | BPM评审、时间窗和并发执行测试 | T-V1-CUT-003、T-V1-PLT-004 | `[CUT-BIZ]`、`[CUT-TEST]`、`[BPM]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-CUT-005 | 停止和回退；`FR-CUT-012` | 停止、回退原因和步骤必填；回退为受控状态动作；不产生部分提交 | 回退事务和重复动作测试；浏览器异常旅程 | T-V1-CUT-004 | `[CUT-BIZ]`、`[CUT-TEST]`、`[UI-VIEW]`、`[E2E]`、`[TRACE]` | M |
| - [ ] T-V1-CUT-006 | 稳定观察、遗留项、归档和回写；`FR-CUT-013`、`FR-CUT-014` | 观察期和遗留项关闭后完成；结果回写项目、任务、设备和交付件 | 观察计时、遗留项和跨模块契约测试 | T-V1-CUT-004 | `[CUT-BIZ]`、`[CUT-TEST]`、`[PROJECT-API]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V1-CUT-B

- [ ] V1割接域12项FR全部具有证据。
- [ ] 正常完成和回退两条真实浏览器旅程通过。

## 9. V1-IU-06：巡检与维保状态

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-SRV-001 | 巡检创建、模式和账号检查；`FR-SRV-001`、`FR-SRV-002`、`FR-SRV-003` | 来源可追溯；在线/离线模式受控；账号有效性检查不泄露凭据 | 创建幂等、权限和凭据泄露测试 | T-V1-AST-002 | `[SQL]`、`[SRV-BIZ]`、`[SRV-TEST]`、`[SEC]`、`[TRACE]` | M |
| - [ ] T-V1-SRV-002 | 巡检规则库和在线执行；`FR-SRV-004`、`FR-SRV-006` | 规则版本可追溯；在线执行反馈进度、结果和失败原因 | 规则版本和执行状态测试 | T-V1-SRV-001 | `[SRV-BIZ]`、`[SRV-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-SRV-003 | 离线巡检文件解析；`FR-SRV-007` | 文件校验、解析、失败明细和重试完整；重复上传不生成重复结果 | 解析器样例、恶意文件和幂等测试 | T-V1-SRV-001、T-V1-PLT-006 | `[SRV-BIZ]`、`[SRV-TEST]`、`[INFRA]`、`[SEC]`、`[TRACE]` | M |

### Checkpoint V1-SRV-A

- [ ] 在线和离线两种巡检方式均可产生结构化结果。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-SRV-004 | 巡检报告和问题确认；`FR-SRV-008`、`FR-SRV-009` | 报告由固定结果快照生成；人工确认保留原结果和调整依据 | 报告一致性和确认审计测试 | T-V1-SRV-002、T-V1-SRV-003 | `[SRV-BIZ]`、`[SRV-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-SRV-005 | 整改待办和巡检闭环；`FR-SRV-011`、`FR-SRV-012` | 问题下发责任人并验证关闭；未结项策略按已确认规则执行 | 整改状态机和闭环门禁测试 | T-V1-SRV-004、巡检未结项策略确认 | `[SRV-BIZ]`、`[SRV-TEST]`、`[PROJECT-API]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-SRV-006 | 维保状态自动计算；`FR-SRV-018` | 维保状态由设备、合同日期和服务规则计算；手工覆盖受权限和审计控制 | 日期边界、时区和覆盖权限测试 | T-V1-AST-001 | `[SRV-BIZ]`、`[SRV-TEST]`、`[AST-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V1-SRV-B

- [ ] V1服务域11项FR全部具有证据。
- [ ] 巡检创建至整改闭环真实浏览器旅程通过。

## 10. V1-IU-07与IU-08：验收、闭环和发布

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-ACC-001 | 电子完工证明；`FR-ACC-002` | 客户确认主体、时间、内容、附件和校验值完整；确认方式符合已确认法律口径 | 签署权限、重放和证据完整性测试 | T-V1-ENG-008、客户确认方式确认 | `[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[UI-VIEW]`、`[SEC]`、`[TRACE]` | M |
| - [ ] T-V1-ACC-002 | 初验、终验和交付件完整性；`FR-ACC-004`、`FR-ACC-005` | 验收阶段、结论、意见和版本可追溯；缺失交付件阻止通过 | 验收状态机和交付件门禁测试 | T-V1-ACC-001、T-V1-PLT-006 | `[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[BPM]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-ACC-003 | 项目闭环审批；`FR-ACC-006` | 闭环校验阶段、验收、问题和审批；带遗留项按确认规则处理 | 闭环参数化和重开测试 | T-V1-ACC-002、遗留问题闭环规则确认 | `[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[BPM]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V1-ACC-A

- [ ] 完工证明、验收和闭环审批形成可运行旅程。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V1-ACC-004 | 交付资料归档和维护移交；`FR-ACC-009`、`FR-ACC-010` | 归档版本不可覆盖；项目转维护后资产、问题和责任人可追溯 | 归档只读和移交契约测试；浏览器刷新验证 | T-V1-ACC-003、T-V1-SRV-006 | `[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[SRV-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V1-REL-001 | V1全链路真实浏览器验收 | 从登录、项目、WBS、设备、工程、割接/巡检到验收闭环全程通过；刷新后数据持久 | `pnpm --dir tests/e2e test`；检查控制台、HTTP和业务响应 | 全部V1业务任务 | `[E2E]`、`[TRACE]`、`docs/acceptance/v1.md` | M |
| - [ ] T-V1-REL-002 | V1安全、性能和迁移验收 | 越权、敏感数据、审计、空库迁移和NFR-PERF-001至006通过 | `mvn clean verify`；安全套件；性能套件；空库迁移 | T-V1-REL-001 | `[SEC]`、`[PERF]`、`[SQL]`、`[TRACE]`、`docs/acceptance/v1.md` | M |

### Checkpoint V1-RELEASE

- [ ] 73项V1 FR全部为“已实现且证据通过”。
- [ ] API、页面、按钮、权限、迁移和测试清单一致。
- [ ] 业务负责人确认V1试点组织和项目类型。
- [ ] 冻结V1契约后才能开始V2并行任务。

## 11. V2：效率提升任务

### V2项目与工程

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-PROJ-001 | 项目组合和客户服务等级；`FR-PROJ-001`、`FR-PROJ-006` | 项目组合独立于项目树；服务等级规则作用于计划和预警 | 组合成员、权限和规则测试；浏览器组合视图 | T-V1-REL-002 | `[SQL]`、`[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-PROJ-002 | 人员批量变更和工期倒排；`FR-PROJ-014`、`FR-PROJ-018` | 批量变更逐条返回结果并重算权限；倒排给出冲突原因 | 批量部分失败和倒排边界测试 | T-V2-PROJ-001 | `[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-PROJ-003 | 计划变更审批、回退和关闭；`FR-PROJ-020`、`FR-PROJ-022` | 变更形成版本和审批；回退/关闭记录原因且不删除历史 | BPM、版本差异和状态机测试 | T-V2-PROJ-002 | `[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[BPM]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V2-PROJ-A

- [ ] 组合、计划和人员效率能力通过。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-PROJ-004 | 日报周报、售前模板和多节点视图；`FR-PROJ-023`、`FR-PROJ-024`、`FR-PROJ-025` | 报告来源可追溯；轻量模板不破坏通用阶段模型；多节点汇总权限正确 | 报告生成、模板和汇总测试 | T-V2-PROJ-003 | `[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-ENG-001 | 外包/领料/外采触发和换货协同；`FR-ENG-002`、`FR-ENG-003` | 触发条件可配置；申请和换货状态可追溯；重复触发幂等 | 条件规则和跨模块契约测试 | T-V1-REL-002 | `[ENG-BIZ]`、`[ENG-TEST]`、`[OUT-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-ENG-002 | 工程交底和准备动态表单；`FR-ENG-006`、`FR-ENG-007` | 交底书由基线数据生成；动态表单版本固定到项目实例 | 模板升级兼容和生成测试 | T-V2-ENG-001 | `[ENG-BIZ]`、`[ENG-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V2-ENG-A

- [ ] 项目效率、供应协同和动态表单能力通过。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-ENG-003 | 单机风险、公告和授权预检查；`FR-ENG-008`、`FR-ENG-009`、`FR-ENG-010` | 风险、停产停维、公告、授权和借货检查生成可解释结果 | 规则组合和版本命中测试 | T-V2-ENG-002 | `[ENG-BIZ]`、`[ENG-TEST]`、`[AST-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-ENG-004 | 客户方案导入、脚本解析和模板库；`FR-ENG-012`、`FR-ENG-014`、`FR-ENG-015` | 导入和解析失败有明细；模板版本可追溯；不覆盖方案基线 | 文件安全、解析器和模板版本测试 | T-V2-ENG-002 | `[ENG-BIZ]`、`[ENG-TEST]`、`[INFRA]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-ENG-005 | 方案通知、实施变更和版本对比；`FR-ENG-017`、`FR-ENG-019`、`FR-ENG-020` | 定稿通知去重；变更经审批生成新版本；差异可视 | BPM、通知幂等和版本Diff测试 | T-V2-ENG-004 | `[ENG-BIZ]`、`[ENG-TEST]`、`[BPM]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V2-ENG-B

- [ ] 风险预检、方案导入和变更闭环通过。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-ENG-006 | 自动配置采集、质量和安全检查；`FR-ENG-025`、`FR-ENG-028`、`FR-ENG-029` | 采集凭据安全；质量和安全项形成阶段门禁；失败可重试审计 | 凭据泄露、采集超时和门禁测试 | T-V2-ENG-003、T-V2-ENG-005 | `[ENG-BIZ]`、`[ENG-TEST]`、`[SEC]`、`[UI-VIEW]`、`[TRACE]` | M |

### V2割接与服务

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-CUT-001 | D级简易割接；`FR-CUT-007` | 仅确认的D级场景允许跳过指定节点；跳过原因和授权审计完整 | 分支矩阵和越级测试 | T-V1-REL-002、D级规则确认 | `[CUT-BIZ]`、`[CUT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-CUT-002 | 割接时效升级和重复割接保障；`FR-CUT-010`、`FR-CUT-015` | 超时预警升级去重；重复割接关联历史和后台保障资源 | 计时、重复任务和通知测试 | T-V2-CUT-001 | `[CUT-BIZ]`、`[CUT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-SRV-001 | 离线命令清单和规则反馈；`FR-SRV-005`、`FR-SRV-010` | 下载内容绑定规则版本；反馈可评审并形成新规则版本 | 版本签名、下载权限和反馈流程测试 | T-V1-REL-002 | `[SRV-BIZ]`、`[SRV-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V2-SRV-A

- [ ] 割接增强和巡检规则改进闭环通过。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-SRV-002 | 工单状态、操作和时效；`FR-SRV-013`、`FR-SRV-014` | 工单状态机可配置但受安全约束；SLA计时、暂停和超期正确 | 状态转换和SLA时钟测试 | T-V2-SRV-001 | `[SQL]`、`[SRV-BIZ]`、`[SRV-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-SRV-003 | ITR关联和项目数据双向更新；`FR-SRV-015`、`FR-SRV-017` | 映射唯一；双向更新有来源、幂等和冲突处理；无跨表直写 | 契约、重放、冲突和对账测试 | T-V2-SRV-002 | `[SRV-BIZ]`、`[INT-BIZ]`、`[INT-TEST]`、`[PROJECT-API]`、`[TRACE]` | M |
| - [ ] T-V2-SRV-004 | 过保与停产停维提示；`FR-SRV-016` | 规则命中设备和项目；提示可追溯到版本和公告；权限正确 | 日期边界和公告命中测试 | T-V2-SRV-002 | `[SRV-BIZ]`、`[SRV-TEST]`、`[AST-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V2-SRV-B

- [ ] 工单、ITR、项目和设备之间通过契约一致协作。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-SRV-005 | 维保检索、续保空间和任务；`FR-SRV-019`、`FR-SRV-020`、`FR-SRV-021` | 维保检索可导出审计；续保空间分层可解释；任务分派可跟踪 | 权限、导出和分层规则测试 | T-V2-SRV-004 | `[SRV-BIZ]`、`[SRV-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-SRV-006 | 回访问卷、续保分析和主动服务计划；`FR-SRV-022`、`FR-SRV-023`、`FR-SRV-024` | 问卷结果绑定客户和任务；指标口径固定；计划可下发执行 | 问卷权限、指标计算和计划状态测试 | T-V2-SRV-005 | `[SRV-BIZ]`、`[SRV-TEST]`、`[ANA-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |

### V2资产与外协

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-RES-001 | 设备位置照片、打卡和工作记录；`FR-RES-004`、`FR-RES-005`、`FR-RES-006` | 位置和照片权限受控；考勤来源可追溯；工作记录关联项目任务 | 文件、位置权限和重复同步测试 | T-V1-REL-002 | `[AST-BIZ]`、`[OUT-BIZ]`、`[AST-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-RES-002 | 工时审批和服务商档案权限；`FR-RES-007`、`FR-RES-008` | 工时按任务申报审批；服务商账号限时限项目授权 | 工时汇总、审批和外协越权测试 | T-V2-RES-001 | `[OUT-BIZ]`、`[OUT-TEST]`、`[BPM]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-RES-003 | 转包申请和审批；`FR-RES-009`、`FR-RES-010` | 转包关联项目范围和服务商；审批按金额/类型路由；重复提交幂等 | BPM路由、权限和幂等测试 | T-V2-RES-002 | `[OUT-BIZ]`、`[OUT-TEST]`、`[BPM]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V2-RES-A

- [ ] 工时、服务商和转包申请闭环通过。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-RES-004 | 合同订单、付款条件、分段付款和回访门禁；`FR-RES-011`、`FR-RES-012`、`FR-RES-013`、`FR-RES-014` | 外部状态可对账；付款条件和余额计算正确；回访未完成时门禁生效 | 金额精度、重复回写和门禁测试 | T-V2-RES-003、财务范围确认 | `[OUT-BIZ]`、`[OUT-TEST]`、`[INT-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-RES-005 | RMA批量申请和备件库策略；`FR-RES-015`、`FR-RES-016` | 批量逐条校验；备件库选择可解释；部分失败有明细 | 批量、库存并发和策略测试 | T-V2-RES-001 | `[AST-BIZ]`、`[AST-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-RES-006 | 好坏件、借用、补库和转移；`FR-RES-017`、`FR-RES-018`、`FR-RES-019` | 每个序列化件流转完整；交接双方确认；库存不出现负数 | 状态机、并发库存和交接测试 | T-V2-RES-005 | `[AST-BIZ]`、`[AST-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V2-RES-B

- [ ] 付款协同、RMA和备件流转闭环通过。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-RES-007 | RMA替换继承维保；`FR-RES-021` | 新旧设备关联；剩余维保规则正确继承；历史设备不被覆盖 | 替换链、日期和并发测试 | T-V2-RES-006、T-V2-SRV-005 | `[AST-BIZ]`、`[AST-TEST]`、`[SRV-BIZ]`、`[TRACE]` | M |
| - [ ] T-V2-RES-008 | 技术公告编制、版本范围和会签；`FR-RES-022`、`FR-RES-023`、`FR-RES-024` | 公告模板、影响版本、重叠校验和产品线会签完整 | 版本区间、重叠和BPM会签测试 | T-V2-RES-001 | `[AST-BIZ]`、`[AST-TEST]`、`[BPM]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-RES-009 | 公告检索、命中和治理工单；`FR-RES-025`、`FR-RES-026`、`FR-RES-027` | 公告按设备版本命中；治理工单优先级和责任人可追溯 | 命中算法、权限和工单创建测试 | T-V2-RES-008、T-V2-SRV-002 | `[AST-BIZ]`、`[AST-TEST]`、`[SRV-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V2-RES-C

- [ ] 公告编制、命中和治理工单闭环通过。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-RES-010 | 公告与ITR关联及处置统计；`FR-RES-028`、`FR-RES-029` | 双向关联幂等；统计口径固定并可追溯明细；分析不成为权威源 | 契约、重复回写和统计对账测试 | T-V2-RES-009、T-V2-SRV-003 | `[AST-BIZ]`、`[INT-BIZ]`、`[ANA-BIZ]`、`[AST-TEST]`、`[TRACE]` | M |

### V2验收、分析和集成

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-ACC-001 | 培训、评价和问卷规则；`FR-ACC-001`、`FR-ACC-003` | 培训和评价关联项目；问卷模板版本固定；判定规则可解释 | 模板升级、评分边界和权限测试 | T-V1-REL-002 | `[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-ACC-002 | 分段回访和遗留问题转维保；`FR-ACC-007`、`FR-ACC-008` | 回访按规则生成；遗留问题移交责任、期限和状态完整 | 回访调度、幂等和移交契约测试 | T-V2-ACC-001、T-V2-SRV-005 | `[PROJECT-BIZ]`、`[PROJECT-TEST]`、`[SRV-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-ANA-001 | 项目组合经营看板；`FR-ANA-001` | 指标口径、过滤维度和权限明确；可下钻权威明细；快照可重建 | 指标对账、权限和性能测试 | T-V2-PROJ-004 | `[ANA-BIZ]`、`[ANA-TEST]`、`[UI-VIEW]`、`[PERF]`、`[TRACE]` | M |

### Checkpoint V2-ANA-A

- [ ] 验收后服务和组合经营分析通过。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-ANA-002 | 工时与人效分析；`FR-ANA-002` | 工时口径、组织维度和排除项明确；结果可追溯至已审批工时 | 指标对账、脱敏和性能测试 | T-V2-RES-002 | `[ANA-BIZ]`、`[ANA-TEST]`、`[OUT-BIZ]`、`[UI-VIEW]`、`[TRACE]` | M |
| - [ ] T-V2-INT-001 | 外部系统权威源和映射模型 | 每个系统定义数据所有者、方向、业务键和授权；PMS核心数据不被误作外部副本 | 架构评审；映射唯一性测试 | 外部系统Owner确认 | `[INT-BIZ]`、`[INT-TEST]`、`docs/integration/`、`[TRACE]` | M |
| - [ ] T-V2-INT-002 | 同步批次、游标、重试和对账 | 同步可恢复、可重放、可对账；失败明细不含敏感数据 | 重放、断点、重复和对账测试 | T-V2-INT-001 | `[INT-BIZ]`、`[INT-TEST]`、`[SEC]`、`[UI-VIEW]`、`[TRACE]` | M |

### Checkpoint V2-INT-A

- [ ] 分析结果与权威明细对账通过。
- [ ] 外部系统映射、同步和补偿基础闭环通过。

| 状态/ID | 任务与FR | 验收标准 | 验证 | 依赖 | 文件区域 | 规模 |
| --- | --- | --- | --- | --- | --- | --- |
| - [ ] T-V2-INT-003 | Integration API和回调安全 | 仅登记接口可外部调用；认证、签名、重放防护、限流和审计完整 | 契约、安全和重放测试 | T-V2-INT-002 | `[INT-BIZ]`、`[INT-TEST]`、`[SEC]`、`[TRACE]` | M |
| - [ ] T-V2-REL-001 | V2跨域真实浏览器和对账验收 | 72项V2 FR全部有证据；项目、工程、服务、资产、外协、分析和集成旅程通过 | E2E、契约、对账、安全、性能、前后端构建全部通过 | 全部V2任务 | `[E2E]`、`[PERF]`、`[SEC]`、`[TRACE]`、`docs/acceptance/v2.md` | M |

### Checkpoint V2-RELEASE

- [ ] 72项V2 FR全部为“已实现且证据通过”。
- [ ] V1契约兼容性测试通过，没有静默破坏。
- [ ] 外部系统对账和失败补偿完成业务验收。
- [ ] V3能力没有混入V2发布范围。

## 12. V3演进门禁（不创建实现任务）

| FR | V2必须保留的数据与治理前提 | 进入未来TASKS前的评审 |
| --- | --- | --- |
| `FR-RES-020` | 库存流水、领用、归还、RMA、补库和预测结果反馈 | 数据完整性、预测价值和人工处置 |
| `FR-ANA-003` | 授权、脱敏的客户、项目、设备地理数据 | 隐私、地图服务依赖和展示权限 |
| `FR-ANA-004` | 配置基线、设备版本和可信采集数据 | 准确率、误报、凭据安全和人工复核 |
| `FR-ANA-005` | 方案版本、风险、评审意见、执行和回退结果 | 模型风险、责任边界和强制人工审批 |
| `FR-ANA-006` | 巡检、工单、告警、配置和处置结果 | 诊断可信度、证据引用和安全边界 |
| `FR-ANA-007` | 任务、时间、操作、交付件和人工修订记录 | 内容准确性、版权和人工确认 |
| `FR-ANA-008` | 项目、巡检、工单、回访和满意度数据 | 脱敏、总结口径和客户数据授权 |

## 13. TASKS阶段完成标准

- [x] T-CP、V1、V2任务均具备稳定ID。
- [x] 73项V1 FR全部且仅映射到明确任务。
- [x] 72项V2 FR全部且仅映射到明确任务。
- [x] 7项V3 FR只出现在演进门禁，不存在当前实现任务。
- [x] 每个任务包含验收、验证、依赖、文件区域和规模。
- [x] 自研任务文件区域不超过5个；机械快照例外已明确标识。
- [x] 每2至3项连续任务附近存在检查点。
- [x] 所有业务待确认项均作为对应任务的显式依赖。
- [x] 用户已于2026-07-28确认本清单，TASKS阶段完成并可进入IMPLEMENT。
