# 项目实施交付管理平台 PRD 正式基线化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 PRD 整理为 V1.4 正式需求基线，使 V1/V2 逐项具备完整业务规格与验收标准，V3 和排除项边界独立、统计与追溯一致。

**Architecture:** 以现有 PRD 为唯一正文载体，先建立自动化基线检查，再按“元数据与范围—V1/V2需求规格—V3及排除项—附录—差异报告与哈希—独立读者验证”的顺序收敛。正文中的业务规则以现行 PRD 和已确认差异报告为依据，不把数据库、接口、类、算法或部署方案写入需求基线。

**Tech Stack:** Markdown、Python 3 标准库、Git、SHA-256。

## Global Constraints

- 目标版本固定为 `V1.4`；整理和内部校验期间状态为“基线候选”，独立读者测试和用户最终确认后改为“正式基线”。
- V1、V2属于正式承诺范围；每项必须包含完整业务规格和可观察、可判断的业务验收标准。
- V3只保留编号、名称、业务目标、适用范围、前置条件和演进方向，不进入当前开发、排期、验收和正式需求数量。
- 已排除项统一标记 `OUT_OF_SCOPE`，只保留追溯信息，不得重新进入需求池、版本规划或待建设能力。
- 推导性增强必须标记 `【建议】`；正式基线不得存在活动中的 `【待确认】`、`TBD` 或 `TODO`。
- “待确认”作为工勘项确认状态、割接评估状态等业务枚举值时允许保留，不能被误判为需求未决标记。
- `PMS`是本平台核心项目数据，不得作为外部系统；`SMS`与`CRM`是同一系统，统一称为`CRM`。
- `yudao masterjdk25`只在技术选型中出现，其他业务文档统一使用“基础平台”。
- 不恢复独立维保档案、续保空间管理、续保率报表、过保空间报表、WO-11、日报周报或平台通用割接时效管控；设备档案继续保留维保基本信息。
- 不修改 `specs/001-project-delivery-platform/domains/*.md`、领域迁移矩阵和现有生成脚本。

---

## 文件结构

- Create: `scripts/validate_prd_baseline.py`：解析 PRD 与差异报告，验证版本、状态、编号、范围分区、验收覆盖、统计、禁用内容及哈希一致性。
- Modify: `需求/PRD-项目实施交付管理平台.md`：正式基线正文、V1/V2逐项规格、V3演进范围、排除清单、附录和签署页。
- Modify: `docs/reports/2026-08-10-PRD与13领域FR差异审查.md`：同步 V1.4 口径、正式需求数、V3/排除项说明、优先级统计和最终 SHA-256。

### Task 1: 建立 PRD 基线自动校验门禁

**Files:**
- Create: `scripts/validate_prd_baseline.py`
- Read: `docs/superpowers/specs/2026-08-11-prd-formal-baseline-design.md`
- Test: `需求/PRD-项目实施交付管理平台.md`
- Test: `docs/reports/2026-08-10-PRD与13领域FR差异审查.md`

**Interfaces:**
- Consumes: `--prd`、`--report`、`--expected-version`、`--expected-status` 四个命令行参数。
- Produces: 每项规则的 `PASS/FAIL`、失败位置和汇总退出码；全部通过时退出码为0，否则为1。

- [ ] **Step 1: 编写当前必然失败的校验规则**

  规则必须检查：版本与状态；正式需求编号唯一；V1/V2条目具备用户角色、业务场景、业务规则、验收标准、数据、权限/数据范围、异常留痕和依赖；V3不含详细验收承诺；排除项不进入正式索引；`WO-07`、`WO-11`、日报周报和续保经营只允许出现在排除说明；活动未决标记为零；正文与附录统计一致；CRM/PMS边界正确；报告哈希与 PRD 文件一致。

- [ ] **Step 2: 在现有 PRD 上运行校验并保存失败摘要**

  Run: `py -3.13 scripts/validate_prd_baseline.py --prd "需求/PRD-项目实施交付管理平台.md" --report "docs/reports/2026-08-10-PRD与13领域FR差异审查.md" --expected-version V1.4 --expected-status 基线候选`

  Expected: FAIL，至少报告当前 `V1.3/基线`、WO-01活动待确认、Q-20性能基线未写回、附录109/108冲突、WO-11误入索引、INT-12遗漏、错误优先级统计、SMS/PMS集成边界及哈希未同步。

- [ ] **Step 3: 验证校验器自身可定位规则**

  Run: `py -3.13 -m py_compile scripts/validate_prd_baseline.py`

  Expected: PASS；脚本只使用 Python 标准库，不修改输入文件。

- [ ] **Step 4: 提交校验门禁**

  Stage exactly: `scripts/validate_prd_baseline.py`

  Commit subject: `test(spec): 新增PRD基线一致性门禁`

### Task 2: 收敛文档治理、范围和已确认业务规则

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`
- Reference: `docs/reports/2026-08-10-PRD与13领域FR差异审查.md:60`

**Interfaces:**
- Consumes: Q-01～Q-28已确认结论，尤其是Q-19延期补单和Q-20树性能规模。
- Produces: V1.4“基线候选”正文骨架、统一范围定义和无活动未决项的业务规则。

- [ ] **Step 1: 更新文档元数据与修订记录**

  将版本改为V1.4、状态改为“基线候选”，新增V1.4修订记录；保留历史记录但明确历史术语不代表现行口径，不填写不存在的审核人或批准人姓名。

- [ ] **Step 2: 增加正式范围声明和阅读指引**

  在产品范围和版本规划入口明确 V1/V2正式承诺、V3演进、OUT_OF_SCOPE 三类范围及计数规则，并声明正式基线生效后的变化走 CHG-01。

- [ ] **Step 3: 将Q-19写回WO-01**

  超过3天的原始打卡证据继续保留；工程师填写逾期原因并提交服务经理审批；通过后计入有效工时，驳回后保留证据但不计入；同步更新用户故事、验收标准、字段、状态和依赖，不再使用“自动删除/不可补齐”的旧口径。

- [ ] **Step 4: 将Q-20写回项目树和任务树规则**

  性能规模取实际迁移量两倍与最低基线的较大值；最低20万项目、200万任务、单项目树1万、单任务树5万、直接子节点2000、测试深度30；深度30仅为测试规模，不是业务层级上限。查询覆盖直接下级、全部后代、完整上级链和指定业务层级。

- [ ] **Step 5: 运行局部门禁**

  Run: `rg -n "【待确认】|TBD|TODO|超3天自动失效|PMS.*外部|SMS" "需求/PRD-项目实施交付管理平台.md"`

  Expected: 不存在活动未决标记和现行错误口径；正常业务状态值的“待确认”仍可见且上下文明确。

- [ ] **Step 6: 提交治理和规则收敛**

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`

  Commit subject: `docs(spec): 收敛PRD基线范围与已确认规则`

### Task 3: 补齐V1/V2逐项业务规格和验收

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`

**Interfaces:**
- Consumes: PRD现有108项需求及第13章版本规划；已确认V3条目在本任务中只标记迁移，不扩写。
- Produces: 所有V1/V2正式需求的统一规格块和业务验收标准。

- [ ] **Step 1: 建立V1/V2正式需求清单**

  逐一标记需求编号、优先级、目标版本和所属章节；把整体安排至V3的编号从正式清单移出；对于同一编号内跨版本的增强项，保留V1/V2基础需求，将V3增强方向拆入演进索引但不新造正式需求编号。

- [ ] **Step 2: 补齐第四至第七章需求**

  对 PM、PRE、PLN、SCH、EXE、ACC、CLO 的每项V1/V2需求补齐：用户角色、业务场景、核心业务规则、用户故事、WHEN/THEN验收、涉及数据、权限与数据范围、异常/降级/留痕、前置/后续/关联依赖。

- [ ] **Step 3: 补齐第八至第十一章需求**

  对 WO、SUB、CUS、EQP、RPT、CUT、INS 的每项V1/V2需求使用相同结构；维保仅作为设备基本信息，巡检与割接的设备连接统一复用INT-12，不把采集执行引擎写成本平台自建能力。

- [ ] **Step 4: 补齐第十二章需求**

  对 INT、NFR、AUT、CHG 的每项V1/V2需求补齐相同结构；外部依赖不可用时必须有业务可观察的降级结果，临时设备密码不得保存，显式保存时转为加密凭证。

- [ ] **Step 5: 运行验收覆盖检查**

  Run: `py -3.13 scripts/validate_prd_baseline.py --prd "需求/PRD-项目实施交付管理平台.md" --report "docs/reports/2026-08-10-PRD与13领域FR差异审查.md" --expected-version V1.4 --expected-status 基线候选`

  Expected: V1/V2结构与验收覆盖相关规则全部 PASS；报告哈希和最终附录规则仍可失败。

- [ ] **Step 6: 提交逐项规格**

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`

  Commit subject: `docs(spec): 补齐V1V2需求规格与业务验收`

### Task 4: 重建V3、排除清单和附录追溯

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`

**Interfaces:**
- Consumes: 正文V1/V2清单、现有V3编号、差异报告中的7项领域演进项和8项范围排除结论。
- Produces: 分区明确、统计可复算的版本规划与附录。

- [ ] **Step 1: 重写V3演进范围**

  现有V3编号只保留名称、业务目标、适用范围、前置条件和演进方向；无编号建议继续标记`【建议】`；明确不进入当前排期、开发、验收和正式需求数量。

- [ ] **Step 2: 建立OUT_OF_SCOPE清单**

  至少登记WO-07、WO-11、FR-PROJ-023、FR-SRV-019～023，并说明日报周报派生能力、独立维保/续保经营及平台通用割接时效均不建设；同时声明设备维保基本信息、CUT-05 A/B级专项提前时间规则不在排除范围。

- [ ] **Step 3: 重建附录A需求索引和验收覆盖矩阵**

  分别列出V1/V2正式需求、V3演进项和OUT_OF_SCOPE项；从正文重新计算正式需求总数、V1/V2数量、P0～P3数量、模块数和验收覆盖率，确保WO-11不进入正式索引且INT-12存在。

- [ ] **Step 4: 重建附录B集成清单**

  CRM只出现一次；PMS不列为外部系统；ITR入/出方向按一个系统计数；UMC只承担结果解析和报告生成；现有采集平台作为平台组件/子应用单列，不与外部业务系统数量混算。

- [ ] **Step 5: 重建附录C和签署页**

  核心实体必须与正文3.3/3.4一致并包括项目任务、设备凭证和采集任务；签署页保留空白审核/批准信息，文档结语使用V1.4及复算后的统计，不虚构签署结果。

- [ ] **Step 6: 运行正文与附录一致性检查**

  Run: `py -3.13 scripts/validate_prd_baseline.py --prd "需求/PRD-项目实施交付管理平台.md" --report "docs/reports/2026-08-10-PRD与13领域FR差异审查.md" --expected-version V1.4 --expected-status 基线候选`

  Expected: 除报告版本和哈希外全部 PASS。

- [ ] **Step 7: 提交范围和附录**

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`

  Commit subject: `docs(spec): 重建PRD版本范围与追溯附录`

### Task 5: 同步差异报告并完成机器校验

**Files:**
- Modify: `docs/reports/2026-08-10-PRD与13领域FR差异审查.md`
- Verify: `需求/PRD-项目实施交付管理平台.md`
- Verify: `scripts/validate_prd_baseline.py`

**Interfaces:**
- Consumes: PRD V1.4基线候选的最终正文、统计和文件哈希。
- Produces: 与PRD完全一致的差异报告和全绿机器门禁。

- [ ] **Step 1: 计算最终PRD哈希**

  Run: `git hash-object "需求/PRD-项目实施交付管理平台.md"`

  Run: `Get-FileHash -Algorithm SHA256 -LiteralPath "需求/PRD-项目实施交付管理平台.md"`

  Expected: 获得Git对象哈希和SHA-256；差异报告登记SHA-256值。

- [ ] **Step 2: 更新差异报告**

  将当前基线改为V1.4基线候选，同步正式V1/V2需求数、V3演进项数、OUT_OF_SCOPE数、优先级分布、验收覆盖结论、集成边界和SHA-256；保留原始外部PRD哈希作为历史来源证据。

- [ ] **Step 3: 运行全部机器门禁**

  Run: `py -3.13 scripts/validate_prd_baseline.py --prd "需求/PRD-项目实施交付管理平台.md" --report "docs/reports/2026-08-10-PRD与13领域FR差异审查.md" --expected-version V1.4 --expected-status 基线候选`

  Expected: PASS。

  Run: `git diff --check`

  Expected: PASS。

- [ ] **Step 4: 提交差异报告同步**

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`、`docs/reports/2026-08-10-PRD与13领域FR差异审查.md`

  Commit subject: `docs(spec): 同步PRD基线差异与追溯证据`

### Task 6: 独立读者测试和正式基线发布

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`
- Modify: `docs/reports/2026-08-10-PRD与13领域FR差异审查.md`

**Interfaces:**
- Consumes: 通过机器门禁的V1.4基线候选。
- Produces: 独立读者可正确解释、用户确认后的V1.4正式基线。

- [ ] **Step 1: 执行独立读者测试**

  让未参与正文编辑的读者仅依据PRD回答：V1/V2正式范围是什么、V3是否承诺、排除项有哪些、设备维保信息边界是什么、项目/任务层级与性能基线是什么、WO-01逾期补单如何处理、设备连接与采集由谁执行。任何回答与确认结论不一致均视为文档失败。

- [ ] **Step 2: 修复读者测试发现的歧义**

  只修复导致误读的章节、交叉引用或术语，不扩大需求范围；修复后重新执行全部机器门禁。

- [ ] **Step 3: 请求用户最终基线确认**

  提供最终统计、校验结果、读者测试结论和变更摘要；在用户确认前保持“基线候选”。

- [ ] **Step 4: 发布正式基线**

  用户确认后将状态改为“正式基线”，在修订记录中写明确认日期，重新计算SHA-256并同步差异报告，再运行全部门禁。

- [ ] **Step 5: 提交正式基线**

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`、`docs/reports/2026-08-10-PRD与13领域FR差异审查.md`

  Commit subject: `docs(spec): 发布PRD V1.4正式基线`
