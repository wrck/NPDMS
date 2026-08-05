# 表结构证据驱动的需求完善 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以现有 27 张目标表为证据，提炼可追溯的业务规则并完善项目交付平台 Markdown 需求事实源。

**Architecture:** 先建立逐表证据台账，将结构证据分为已确认、设计推导、业务待定和纯技术四类，再按唯一权威落位更新平台、项目、资产、分析与公共附录。领域需求只表达可观察业务语义，物理 DDL 保持输入证据身份；语义不清时才定向回查原始 Excel。

**Tech Stack:** Markdown、PowerShell、ripgrep、Git；仅在定向回查 Excel 时使用工作区内置 Python/openpyxl、spreadsheets skill 和 `codex-libreoffice:24.04` 容器镜像。

## Global Constraints

- 只使用当前工作区和当前对话中的项目资料，禁止使用项目记忆。
- 正式需求事实源仅为 `specs/001-project-delivery-platform/` 下的 Markdown 文件。
- 必须覆盖 `project-order-physical-schema.mysql.sql` 中全部 27 张表，每张表至少有一个处理结论。
- 优先使用现有结构化规格和表结构；只有语义不清、证据冲突或缺少业务依据时才回查原始 Excel。
- Excel 的结构化读取优先使用工作区内置 Python/openpyxl；需要查看合并单元格、公式显示或打印布局时，可使用 `codex-libreoffice:24.04` 容器渲染，源目录必须只读挂载，输出只写入任务临时目录。
- 保留全部现有 `REQ/FR/BR/DR/AC` 编号，不重排、不重命名；新增内容沿用既有领域编号体系。
- 默认不改变现有 148 条来源需求统计；新增独立 FR 必须先获得用户确认。
- 仅由评审草案 DDL 推导出的新规则标记 `【建议】`；影响业务行为但无法唯一解释的规则标记 `【待确认】`。
- 不把字段类型、长度、索引名、物理外键、约束名或数据库实现写入领域需求。
- 产品技术名称只在技术选型或技术约束章节出现；其他位置统一使用“基础平台”等稳定业务称谓。
- 不修改通用模板、团队成员示例、Word 文档、原始 Excel 和物理 DDL。
- 不暂存或提交 `docs/需求分析模版和示例.zip`、`需求/数据元.xlsx`、`需求/需求细节.md`。
- 每次 Git 提交前必须重新读取并遵循 `$git-commit` skill；禁止 `git add .`、`git add -A` 和自动推送。

---

## 文件结构

- Create: `specs/001-project-delivery-platform/appendices/table-structure-business-rule-traceability.md`
  - 保存 27 张表的证据、业务语义、证据等级、需求落位、Excel 回查和处理状态。
- Modify: `specs/001-project-delivery-platform/00-master-spec.md:180`
  - 补充跨领域数据身份、有效关系、数量守恒、血缘和可重建汇总规则。
- Modify: `specs/001-project-delivery-platform/01-platform-and-permission.md:170,310,589,729`
  - 完善隔离、审计、同步幂等、失败重试和业务唯一键规则。
- Modify: `specs/001-project-delivery-platform/02-project-initiation.md:47,117,193,547,616,761`
  - 完善组合、项目树、订单行拆分、实施范围、同步和关联业务全景。
- Modify: `specs/001-project-delivery-platform/07-assets-and-outsourcing.md:50,1023,1162,1302,1373`
  - 完善 SN 身份、发货事件、设备转移、RMA 和设备关系规则。
- Modify: `specs/001-project-delivery-platform/08-analytics-and-integration.md:8,29`
  - 明确分析与集成边界，以及汇总读模型的可重建和可追溯规则。
- Modify: `specs/001-project-delivery-platform/appendices/data-dictionary.md:3`
  - 增加表结构证据支持的业务对象、关系、业务键、历史和所有权。
- Modify: `specs/001-project-delivery-platform/appendices/state-machines.md:28`
  - 只补充有明确业务证据的实施范围、映射和设备归属状态规则。
- Modify: `specs/001-project-delivery-platform/appendices/acceptance-traceability.md:3`
  - 将新增 AC 补入既有来源需求到功能规格的追溯行。
- Modify: `specs/001-project-delivery-platform/README.md:3`
  - 登记证据追溯附录及其权威边界。
- Read only: `specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql`
- Read only: `specs/001-project-delivery-platform/appendices/project-order-target-schema-evidence.md`
- Read only: `specs/001-project-delivery-platform/appendices/project-order-migration-mapping.md`
- Read only as needed: `/需求` 下相关结构化 Excel。

### Task 1: 建立 27 张表的业务规则证据台账

**Files:**
- Create: `specs/001-project-delivery-platform/appendices/table-structure-business-rule-traceability.md`
- Read: `specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql`
- Read: `specs/001-project-delivery-platform/appendices/project-order-target-schema-evidence.md`
- Read: `specs/001-project-delivery-platform/appendices/project-order-migration-mapping.md`
- Read: `specs/001-project-delivery-platform/00-master-spec.md`
- Read: `specs/001-project-delivery-platform/01-platform-and-permission.md`
- Read: `specs/001-project-delivery-platform/02-project-initiation.md`
- Read: `specs/001-project-delivery-platform/07-assets-and-outsourcing.md`
- Read: `specs/001-project-delivery-platform/08-analytics-and-integration.md`

**Interfaces:**
- Consumes: 27 张表、现有 FR/BR/DR/AC、目标模型证据和迁移事实。
- Produces: `EVD-TSR-001` 起的逐表证据、证据等级、唯一需求落位和 Excel 回查清单；后续任务只引用该台账。

- [ ] **Step 1: 建立缺口基线**

  Run:

  ```powershell
  Test-Path specs/001-project-delivery-platform/appendices/table-structure-business-rule-traceability.md
  ```

  Expected: `False`，证明当前没有逐表业务规则追溯台账。

- [ ] **Step 2: 建立台账固定结构**

  创建以下章节：文档定位与证据等级、27 张表覆盖清单、业务规则证据矩阵、定向 Excel 回查、纯技术排除项、待确认事项、覆盖与追溯检查。矩阵固定表头为：

  ```markdown
  |证据编号|来源表|结构证据|提炼的业务含义|证据等级|目标 FR/BR/DR/AC|Excel 回查|处理状态|
  |---|---|---|---|---|---|---|---|
  ```

- [ ] **Step 3: 覆盖全部 27 张表**

  按 SQL 中出现顺序登记 `pms_project` 至 `pms_project_delivery_summary`。每张表至少一条 `EVD-TSR-###`，并明确属于“形成规则、强化规则、业务待定、纯技术排除”中的一种；同一表存在多个独立业务语义时可使用多条证据。

- [ ] **Step 4: 提炼并分级候选规则**

  至少覆盖：项目树与非树关系、组合成员有效期、合同所属公司内唯一、订单与合同多对多、订单行实施范围、数量分配与待确认状态、SN 身份与发货事件分离、设备归属历史、设备替换关系、执行单辅助证据、改单血缘、同步幂等、迁移问题及可重建汇总。

  仅由 DDL 推导且现有需求未确认的规则写为 `【建议】`；以下高风险语义若无其他来源必须列入 `【待确认】`：可分配数量口径、退货负数量处理、同一设备能否同时有效归属多个项目、发货业务动作字典、合同所属公司解析优先级。

- [ ] **Step 5: 必要时定向回查 Excel**

  只有 Step 4 的待确认项需要进一步定位时，加载 `$spreadsheets` skill，搜索与该项直接相关的工作簿、工作表、列名和数据样例。台账记录“工作簿相对路径、工作表名、列名或单元格范围、支持或冲突结论”；不得修改原始 Excel。

  当 openpyxl 无法准确表达合并单元格、公式显示值或打印布局时，使用已确认存在的 `codex-libreoffice:24.04` 镜像渲染目标工作簿。示例：

  ```powershell
  $excelSourceDir = (Resolve-Path '需求/项目交付').Path
  $excelQaDir = Join-Path $env:TEMP 'pms-table-evidence-excel-qa'
  New-Item -ItemType Directory -Force -Path $excelQaDir | Out-Null
  docker run --rm `
    --mount "type=bind,source=$excelSourceDir,target=/input,readonly" `
    --mount "type=bind,source=$excelQaDir,target=/output" `
    codex-libreoffice:24.04 `
    --convert-to pdf --outdir /output '/input/项目交付页面数据、逻辑、功能.xlsx'
  ```

  Expected: PDF 只生成在临时目录；原始 Excel 的哈希和修改时间保持不变。仅检查与待确认项有关的工作表和页面。

- [ ] **Step 6: 验证逐表覆盖**

  Run:

  ```powershell
  $sql = 'specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql'
  $ledger = 'specs/001-project-delivery-platform/appendices/table-structure-business-rule-traceability.md'
  $sqlTables = Select-String -Path $sql -Pattern '^CREATE TABLE\s+(pms_[a-z0-9_]+)' | ForEach-Object { $_.Matches[0].Groups[1].Value }
  $ledgerTables = Get-Content -Encoding UTF8 $ledger | ForEach-Object { if ($_ -match '^\|EVD-TSR-\d{3}\|`(pms_[a-z0-9_]+)`\|') { $Matches[1] } } | Sort-Object -Unique
  if ($sqlTables.Count -ne 27) { throw "SQL table count is $($sqlTables.Count), expected 27" }
  $diff = Compare-Object ($sqlTables | Sort-Object -Unique) $ledgerTables
  if ($diff) { $diff | Format-Table | Out-String | Write-Error; exit 1 }
  'PASS: all 27 tables have evidence conclusions'
  ```

  Expected: `PASS: all 27 tables have evidence conclusions`。

- [ ] **Step 7: 提交证据台账**

  先读取 `$git-commit` skill，再只暂存本任务文件并提交：

  ```powershell
  git add -- specs/001-project-delivery-platform/appendices/table-structure-business-rule-traceability.md
  $message = @'
  docs(spec): 建立表结构业务规则证据台账
  '@
  git commit -m $message
  ```

### Task 2: 完善项目、组合、订单行和实施范围需求

**Files:**
- Modify: `specs/001-project-delivery-platform/02-project-initiation.md:47-837`
- Read: `specs/001-project-delivery-platform/appendices/table-structure-business-rule-traceability.md`
- Read: `需求/需求细节.md`

**Interfaces:**
- Consumes: Task 1 中项目、组合、合同、订单、订单行、实施范围、执行单和改单血缘证据。
- Produces: 项目域新增 `BR-PROJ-017` 起的规则和对应 `AC-PROJ-*`，供 Task 5 更新数据字典与追溯矩阵。

- [ ] **Step 1: 记录现有需求缺口**

  Run:

  ```powershell
  rg -n "订单行实施范围|可分配数量|待确认数量|合同所属公司|执行单.*辅助证据|取消.*释放数量" specs/001-project-delivery-platform/02-project-initiation.md
  ```

  Expected: 无匹配或只有概括描述，尚未形成完整的数量、身份和血缘规则。

- [ ] **Step 2: 完善项目组合与项目关系**

  在 FR-PROJ-001 追加 `BR-PROJ-017`、`BR-PROJ-018`：组合成员关系具有来源和有效区间；静态或规则计算成员均不得改变项目父子关系。追加 `AC-PROJ-014`，验证成员失效后不再进入当前组合统计但历史仍可追溯。

  在 FR-PROJ-002 和 FR-PROJ-009 补充项目编码在租户范围内不可复用、树关系与扩容/续采/改造等非树关系相互独立；已有树移动原子性规则不重复定义。

- [ ] **Step 3: 完善订单行拆分和数量守恒**

  在 FR-PROJ-003 追加：

  - `BR-PROJ-034`：大型项目可按订单配置行和数量拆分为独立交付的子项目；主项目闭环仍受子项目闭环门禁约束。
  - `BR-PROJ-035`：生效实施范围必须已解析到订单行并具有明确分配数量。
  - `BR-PROJ-036`：同一订单行的全部当前有效分配数量不得超过确认的可分配数量；取消或转移后释放相应数量。
  - `BR-PROJ-037`：待映射或待确认数量记录不得计入完成率，也不得用整行数量重复填充多个项目。
  - `BR-PROJ-038`：同一项目与同一订单行只能有一条当前有效实施范围；调整必须保留历史和原因。
  - `BR-PROJ-039`：退货或负数量的方向语义在业务口径确认前标记 `【待确认】`，不得按普通正数量参与分配。

  追加 `AC-PROJ-034` 至 `AC-PROJ-036`，分别验证正常拆分、超量分配拒绝、待确认数据不进入进度汇总。

- [ ] **Step 4: 完善项目主数据同步**

  在 FR-PROJ-008 追加 `BR-PROJ-084` 至 `BR-PROJ-088`：来源记录具有不可变来源键；重复同步不新增重复对象；同步批次区分处理中、成功、部分成功和失败；失败明细可修复重试；旧新主键映射和人工解决过程必须可追溯。追加 `AC-PROJ-084`、`AC-PROJ-085` 验证重复同步幂等和部分失败可对账。

- [ ] **Step 5: 完善项目关联业务全景**

  在 FR-PROJ-011 追加：

  - `BR-PROJ-114`：合同身份由所属公司和合同号共同确定，合同号不得被假定为全局唯一。
  - `BR-PROJ-115`：项目与合同、合同与订单均允许多对多，关系需要角色、来源和有效期。
  - `BR-PROJ-116`：订单行属于唯一订单，项目实施范围以项目—订单行关系为权威依据。
  - `BR-PROJ-117`：CRM 执行单及其配置只作为来源辅助证据，不直接决定项目实施范围。
  - `BR-PROJ-118`：项目关联全景必须显示未解析、待确认和冲突状态，不得把不确定关系伪装为正式关系。
  - `BR-PROJ-119`：项目、订单行和设备的关联必须能够追溯到来源记录和生效区间。

  追加 `AC-PROJ-114`、`AC-PROJ-115`，验证多关系钻取和未解析状态可见性。

- [ ] **Step 6: 执行项目域边界与编号检查**

  Run:

  ```powershell
  rg -n "BR-PROJ-0(17|18|34|35|36|37|38|39|84|85|86|87|88)|BR-PROJ-11(4|5|6|7|8|9)|AC-PROJ-0(14|34|35|36|84|85)|AC-PROJ-11(4|5)" specs/001-project-delivery-platform/02-project-initiation.md
  rg -n "CREATE TABLE|UNIQUE KEY|FOREIGN KEY|uk_|idx_|fk_|BIGINT|VARCHAR|DECIMAL" specs/001-project-delivery-platform/02-project-initiation.md
  ```

  Expected: 第一条命令定位全部新增规则和验收编号；第二条命令无匹配。

- [ ] **Step 7: 提交项目域需求完善**

  先读取 `$git-commit` skill，再只暂存项目分册并提交：

  ```powershell
  git add -- specs/001-project-delivery-platform/02-project-initiation.md
  $message = @'
  docs(project): 完善订单行实施范围业务规则
  '@
  git commit -m $message
  ```

### Task 3: 完善设备 SN、发货、转移和 RMA 需求

**Files:**
- Modify: `specs/001-project-delivery-platform/07-assets-and-outsourcing.md:50-1442`
- Read: `specs/001-project-delivery-platform/appendices/table-structure-business-rule-traceability.md`

**Interfaces:**
- Consumes: Task 1 的设备身份、发货事件、项目设备归属和设备关系证据。
- Produces: 资产域新增 `BR-RES-*` 和 `AC-RES-*`，供 Task 5 更新数据字典、状态机和追溯矩阵。

- [ ] **Step 1: 记录现有资产需求缺口**

  Run:

  ```powershell
  rg -n "身份与发货事件分离|重复发货历史|当前有效归属|原设备与替换设备|不得关联自身" specs/001-project-delivery-platform/07-assets-and-outsourcing.md
  ```

  Expected: 无完整匹配，现有规则未明确设备身份、物流事件和归属历史边界。

- [ ] **Step 2: 完善设备 SN 身份规则**

  在 FR-RES-001 追加 `BR-RES-014` 至 `BR-RES-018`：租户范围内同一确认 SN 代表一个设备身份；设备主档不覆盖多次发货事件；旧库重复 SN 必须先归并事件或进入冲突问题；无法证明同一设备的记录不得强制合并；次级 SN 仅作为辅助检索，不替代主身份。追加 `AC-RES-014`、`AC-RES-015` 验证重复来源归并和冲突隔离。

- [ ] **Step 3: 完善发货与好坏件事件规则**

  在 FR-RES-017 追加 `BR-RES-174` 至 `BR-RES-177`：发货、退回、返还、再次发放均形成独立事件；事件保留来源键和发生时间；订单行未解析不阻止保留事件但必须显示待映射；业务动作字典未确认时使用未分类状态且不自动推导好坏件结论。追加 `AC-RES-174`、`AC-RES-175` 验证重复导入不重复建事件和未映射事件可追溯。

- [ ] **Step 4: 完善项目设备转移规则**

  在 FR-RES-019 追加 `BR-RES-194` 至 `BR-RES-197`：项目设备归属具有生效区间；转移关闭原归属并建立新归属；转移批次中的变更要么全部成功要么明确部分失败及补偿；同一设备能否同时有效归属多个项目标记 `【待确认】`。追加 `AC-RES-194`、`AC-RES-195` 验证转移历史和失败一致性。

- [ ] **Step 5: 完善 RMA 和设备关系规则**

  在 FR-RES-015 追加 `BR-RES-154` 至 `BR-RES-156`：RMA 申请必须保留原设备、申请记录和来源；批量申请逐条返回结果；缺少 RMA 号或业务动作时不得自动建立替换关系。

  在 FR-RES-021 追加 `BR-RES-214` 至 `BR-RES-218`：替换关系必须连接两个不同设备；保留原设备与替换设备双向追溯；维保继承需要合同或明确政策依据；关系生效后不得覆盖原设备历史；母子 SN 与 RMA 替换使用不同关系类型。追加 `AC-RES-214`、`AC-RES-215` 验证自关联拒绝和维保继承依据。

- [ ] **Step 6: 执行资产域边界与编号检查**

  Run:

  ```powershell
  rg -n "BR-RES-01(4|5|6|7|8)|BR-RES-15(4|5|6)|BR-RES-17(4|5|6|7)|BR-RES-19(4|5|6|7)|BR-RES-21(4|5|6|7|8)|AC-RES-01(4|5)|AC-RES-17(4|5)|AC-RES-19(4|5)|AC-RES-21(4|5)" specs/001-project-delivery-platform/07-assets-and-outsourcing.md
  rg -n "CREATE TABLE|UNIQUE KEY|FOREIGN KEY|uk_|idx_|fk_|BIGINT|VARCHAR|DECIMAL" specs/001-project-delivery-platform/07-assets-and-outsourcing.md
  ```

  Expected: 第一条命令定位全部新增规则和验收编号；第二条命令无匹配。

- [ ] **Step 7: 提交资产域需求完善**

  先读取 `$git-commit` skill，再只暂存资产分册并提交：

  ```powershell
  git add -- specs/001-project-delivery-platform/07-assets-and-outsourcing.md
  $message = @'
  docs(asset): 完善设备生命周期业务规则
  '@
  git commit -m $message
  ```

### Task 4: 完善公共平台、集成和分析规则

**Files:**
- Modify: `specs/001-project-delivery-platform/00-master-spec.md:180-188`
- Modify: `specs/001-project-delivery-platform/01-platform-and-permission.md:170-799`
- Modify: `specs/001-project-delivery-platform/08-analytics-and-integration.md:8-98`
- Read: `specs/001-project-delivery-platform/appendices/table-structure-business-rule-traceability.md`

**Interfaces:**
- Consumes: Task 1 的租户、业务键、来源记录、同步批次、映射问题和汇总读模型证据。
- Produces: 公共规则、平台规则和分析规则，供 Task 5 统一数据字典及验收追溯。

- [ ] **Step 1: 完善总规格公共业务规则**

  在“12. 公共业务规则”追加：业务对象同时具有系统标识和明确业务身份；外部来源记录保留来源系统及不可变来源键；有效关系变更不得覆盖历史；带数量的业务关系执行守恒和并发校验；分析汇总必须可从权威明细重建并标识统计时间点。仅由 DDL 推导的规则按证据台账保留 `【建议】` 标识。

- [ ] **Step 2: 完善数据权限和审计规则**

  在 FR-PLT-003 追加 `BR-PLT-034`、`BR-PLT-035`：所有业务关系继承租户隔离和项目数据范围；跨项目关系查询必须分别校验两端对象的数据权限。追加 `AC-PLT-034` 验证无任一端权限时不泄露关系详情。

  在 FR-PLT-005 追加 `BR-PLT-054` 至 `BR-PLT-056`：历史关系失效而不覆盖；来源映射人工调整记录前后值和依据；迁移问题的解决人、结果和时间不可缺失。追加 `AC-PLT-054` 验证历史与人工解决证据可审计。

- [ ] **Step 3: 完善开放集成与事件规则**

  用真实规则替换无业务内容的 `BR-PLT-094`，并追加至 `BR-PLT-098`：外部记录以来源系统和来源键执行幂等；同步批次区分处理结果；部分失败保留失败明细并支持重试；旧新主键映射可对账；集成模块不得绕过目标领域服务直接把来源快照升级为权威业务数据。追加 `AC-PLT-094` 至 `AC-PLT-096` 验证重复同步、部分失败和映射对账。

- [ ] **Step 4: 完善统一编码与业务唯一键规则**

  用真实规则替换无业务内容的 `BR-PLT-114`，并追加 `BR-PLT-115`、`BR-PLT-116`：业务唯一键必须包含必要作用域；合同身份包含所属公司和合同号；业务编码停用后不得被其他对象复用。追加 `AC-PLT-114`，验证不同公司可存在同号合同、同一公司不得重复。

- [ ] **Step 5: 完善分析与集成领域边界**

  在 `08-analytics-and-integration.md` 的领域边界明确：集成模块拥有同步批次、外部映射和迁移问题；分析模块只拥有可重建汇总或快照，不成为项目、合同、订单、设备的权威源。

  在 FR-ANA-001 追加 `BR-ANA-014` 至 `BR-ANA-017`：汇总标识统计时间点；结果可钻取到权威明细；待映射和待确认数量单独展示且不混入确定指标；汇总异常可重建而不回写业务主档。追加 `AC-ANA-014`、`AC-ANA-015` 验证明细追溯和重建一致性。

- [ ] **Step 6: 执行公共规则边界检查**

  Run:

  ```powershell
  rg -n "BR-PLT-03(4|5)|BR-PLT-05(4|5|6)|BR-PLT-09(4|5|6|7|8)|BR-PLT-11(4|5|6)|BR-ANA-01(4|5|6|7)|AC-PLT-0(34|54|94|95|96)|AC-PLT-114|AC-ANA-01(4|5)" specs/001-project-delivery-platform/01-platform-and-permission.md specs/001-project-delivery-platform/08-analytics-and-integration.md
  rg -n "CREATE TABLE|UNIQUE KEY|FOREIGN KEY|uk_|idx_|fk_|BIGINT|VARCHAR|DECIMAL" specs/001-project-delivery-platform/00-master-spec.md specs/001-project-delivery-platform/01-platform-and-permission.md specs/001-project-delivery-platform/08-analytics-and-integration.md
  ```

  Expected: 第一条命令定位全部新增规则和验收编号；第二条命令无匹配。

- [ ] **Step 7: 提交公共平台和分析需求完善**

  先读取 `$git-commit` skill，再只暂存本任务三个文件并提交：

  ```powershell
  git add -- specs/001-project-delivery-platform/00-master-spec.md specs/001-project-delivery-platform/01-platform-and-permission.md specs/001-project-delivery-platform/08-analytics-and-integration.md
  $message = @'
  docs(platform): 完善数据血缘与汇总业务规则
  '@
  git commit -m $message
  ```

### Task 5: 完善数据字典、状态机、追溯矩阵和规格索引

**Files:**
- Modify: `specs/001-project-delivery-platform/appendices/data-dictionary.md:3-43`
- Modify: `specs/001-project-delivery-platform/appendices/state-machines.md:28-39`
- Modify: `specs/001-project-delivery-platform/appendices/acceptance-traceability.md:3-155`
- Modify: `specs/001-project-delivery-platform/README.md:3-34`
- Read: Task 2 至 Task 4 修改后的领域分册
- Read: `specs/001-project-delivery-platform/appendices/table-structure-business-rule-traceability.md`

**Interfaces:**
- Consumes: 已落位的项目、资产、平台和分析 `BR/AC`。
- Produces: `DR-COM-015` 至 `DR-COM-029`、状态引用、更新后的验收映射和可导航规格索引。

- [ ] **Step 1: 扩充公共数据字典**

  新增以下对象编号：

  - `DR-COM-015` 项目组合；
  - `DR-COM-016` 项目非层级关系；
  - `DR-COM-017` 合同；
  - `DR-COM-018` 销售订单；
  - `DR-COM-019` 销售订单行；
  - `DR-COM-020` 项目订单行实施范围；
  - `DR-COM-021` 发货批次／装箱单；
  - `DR-COM-022` 设备发货事件；
  - `DR-COM-023` 项目设备归属；
  - `DR-COM-024` 设备关系；
  - `DR-COM-025` CRM 执行单辅助证据；
  - `DR-COM-026` 同步批次；
  - `DR-COM-027` 外部主键映射；
  - `DR-COM-028` 迁移问题；
  - `DR-COM-029` 项目交付汇总读模型。

  为每个对象写明业务定义、生命周期、业务身份、主要关系和唯一数据所有者，不复制物理字段类型。

- [ ] **Step 2: 增加关系、业务键和历史规则章节**

  在数据字典增加“对象关系与基数”“业务身份与唯一性”“有效期与历史保留”三个章节。明确项目组合不等于项目树、CRM 执行单不决定实施范围、设备身份不等于发货事件、汇总读模型不成为权威源。

- [ ] **Step 3: 完善状态机引用**

  在状态机附录增加：项目订单行实施范围的待映射、待确认数量、生效、取消；来源映射的问题状态；项目设备归属的生效、转移、失效。未从业务资料确认的转换标记 `【待确认】`，不创造自动转换条件。

- [ ] **Step 4: 更新验收追溯矩阵**

  在既有 REQ 到 FR 行中补充 Task 2 至 Task 4 新增的 AC，不新增虚构来源需求。至少更新 FR-PROJ-001、003、008、011，FR-RES-001、015、017、019、021，FR-PLT-003、005、009、011 和 FR-ANA-001 对应行。

- [ ] **Step 5: 更新规格索引**

  在 README 的规格文件清单中增加 `appendices/table-structure-business-rule-traceability.md`，并说明该文件是“表结构证据到业务规则的追溯来源”，不替代领域需求和物理 DDL。

- [ ] **Step 6: 验证数据对象、追溯和导航**

  Run:

  ```powershell
  $dictionary = 'specs/001-project-delivery-platform/appendices/data-dictionary.md'
  $dr = 15..29 | ForEach-Object { 'DR-COM-{0:D3}' -f $_ }
  foreach ($id in $dr) { if (-not (Select-String -Quiet -Path $dictionary -Pattern $id)) { throw "missing $id" } }
  rg -n "table-structure-business-rule-traceability.md" specs/001-project-delivery-platform/README.md
  rg -n "AC-PROJ-0(14|34|35|36|84|85)|AC-PROJ-11(4|5)|AC-RES-01(4|5)|AC-RES-17(4|5)|AC-RES-19(4|5)|AC-RES-21(4|5)|AC-PLT-0(34|54|94|95|96)|AC-PLT-114|AC-ANA-01(4|5)" specs/001-project-delivery-platform/appendices/acceptance-traceability.md
  ```

  Expected: 所有 `DR-COM-015` 至 `DR-COM-029` 存在，README 链接存在，新增 AC 均能在追溯矩阵定位。

- [ ] **Step 7: 提交公共附录更新**

  先读取 `$git-commit` skill，再只暂存本任务四个文件并提交：

  ```powershell
  git add -- specs/001-project-delivery-platform/README.md specs/001-project-delivery-platform/appendices/data-dictionary.md specs/001-project-delivery-platform/appendices/state-machines.md specs/001-project-delivery-platform/appendices/acceptance-traceability.md
  $message = @'
  docs(spec): 补全数据对象与规则追溯
  '@
  git commit -m $message
  ```

### Task 6: 执行全量一致性检查和独立评审

**Files:**
- Modify if required: Task 1 至 Task 5 涉及的 Markdown 文件
- Read only: `specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql`
- Read only: `docs/superpowers/specs/2026-08-05-table-structure-evidence-requirements-enrichment-design.md`

**Interfaces:**
- Consumes: 全部更新后的需求事实源和证据台账。
- Produces: 27 表覆盖、编号、标记、边界、链接和追溯均通过的最终文档集。

- [ ] **Step 1: 重新验证 27 表覆盖**

  重复 Task 1 Step 6 的覆盖命令。

  Expected: SQL 表数量为 27，证据台账无缺表。

- [ ] **Step 2: 检查未决标记和确定性验收冲突**

  Run:

  ```powershell
  rg -n "【建议】|【待确认】" specs/001-project-delivery-platform -g '*.md'
  ```

  Review: 每个 `【待确认】` 均说明影响和确认问题；对应 AC 不得预设确定结论。每个 `【建议】` 均可回溯到 `EVD-TSR-*`。

- [ ] **Step 3: 检查领域文档没有物理实现泄漏**

  Run:

  ```powershell
  rg -n "CREATE TABLE|UNIQUE KEY|FOREIGN KEY|CONSTRAINT|uk_|idx_|fk_|BIGINT|VARCHAR|DECIMAL|InnoDB" specs/001-project-delivery-platform/00-master-spec.md specs/001-project-delivery-platform/01-platform-and-permission.md specs/001-project-delivery-platform/02-project-initiation.md specs/001-project-delivery-platform/07-assets-and-outsourcing.md specs/001-project-delivery-platform/08-analytics-and-integration.md
  ```

  Expected: 无匹配。表名和结构证据只允许出现在证据、目标结构和迁移附录中。

- [ ] **Step 4: 检查编号定义唯一性**

  Run:

  ```powershell
  $files = Get-ChildItem specs/001-project-delivery-platform -Recurse -Filter '*.md'
  $definitions = foreach ($file in $files) {
    Get-Content -Encoding UTF8 $file.FullName | ForEach-Object {
      if ($_ -match '^## (FR-[A-Z]+-\d+)\s') { "$($Matches[1])|$($file.FullName)" }
      elseif ($_ -match '^- \*\*(BR-[A-Z]+-\d+)：\*\*') { "$($Matches[1])|$($file.FullName)" }
      elseif ($_ -match '^\| (DR-[A-Z]+-\d+) \|') { "$($Matches[1])|$($file.FullName)" }
      elseif ($_ -match '^- \*\*(AC-[A-Z]+-\d+)：\*\*') { "$($Matches[1])|$($file.FullName)" }
    }
  }
  $duplicates = $definitions | ForEach-Object { ($_ -split '\|')[0] } | Group-Object | Where-Object Count -gt 1
  if ($duplicates) { $duplicates | Format-Table | Out-String | Write-Error; exit 1 }
  'PASS: requirement definitions are unique'
  ```

  Expected: `PASS: requirement definitions are unique`。

- [ ] **Step 5: 检查来源需求统计和 Markdown 质量**

  Run:

  ```powershell
  rg -n "148条|148 条|需求数量" specs/001-project-delivery-platform/00-master-spec.md specs/001-project-delivery-platform/README.md
  rg -n "TBD|TODO|〈[^〉]+〉|(?:FR|BR|DR|AC)-X{3}" specs/001-project-delivery-platform
  git diff --check
  ```

  Expected: 来源需求统计仍为 148；无模板占位符；`git diff --check` 无输出。

- [ ] **Step 6: 执行独立需求评审**

  使用 `$requesting-code-review` skill，让独立评审者重点检查：表结构是否被误当作业务事实、规则是否有唯一权威落位、待确认项是否进入确定性 AC、是否遗漏 27 表、是否改动了非目标文件。所有高、中风险问题修复后重新运行 Steps 1 至 5。

- [ ] **Step 7: 提交最终校正**

  如果独立评审产生修改，先读取 `$git-commit` skill，只暂存修正文件并提交：

  ```powershell
  $reviewFiles = git diff --name-only -- specs/001-project-delivery-platform
  if (-not $reviewFiles) { throw 'No review fixes to commit' }
  git add -- $reviewFiles
  $message = @'
  docs(spec): 校正表结构证据需求追溯
  '@
  git commit -m $message
  ```

  如果没有修改，不创建空提交。

## 完成判定

- 证据台账覆盖全部 27 张表，并对每张表给出明确处理结论。
- 项目、资产、平台和分析分册新增规则均能回溯 `EVD-TSR-*` 和现有来源需求。
- 数据字典包含 `DR-COM-015` 至 `DR-COM-029`，且每个对象有业务定义、生命周期、身份、关系和所有者。
- 新增 AC 已进入既有需求追溯矩阵，没有新增虚构 REQ。
- 原始 Excel 只在台账记录的疑点上被定向读取且未被修改。
- 通用模板、示例、Word、DDL 和用户未跟踪资料均保持不变。
- 所有验证命令通过，独立评审无未解决的高、中风险问题。
