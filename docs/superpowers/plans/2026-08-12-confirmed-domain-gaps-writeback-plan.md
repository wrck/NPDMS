# Confirmed Domain Gaps Writeback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with checkpoints.

**Goal:** 将已确认的15项补充需求、V3演进方向和排除边界回写到当前PRD，并从PRD重新生成可追溯的13份领域需求规格。

**Architecture:** PRD是唯一正文来源；15项新增需求使用领域型编号并保留原FR追溯。生成脚本从PRD正式需求索引提取正文、按Owner映射到领域；V3和OUT_OF_SCOPE只从PRD附录生成领域追溯表。备件业务只保留外部系统集成契约，工单时效不进入正式需求或V3。

**Tech Stack:** UTF-8 Markdown、Python 3.13、现有 `scripts/generate_prd_domain_requirements.py`、现有 `scripts/validate_prd_domain_generation.py`、Git 原生命令。

## Global Constraints

- 现有100项正式PRD编号和正文不改号、不删除；新增正式需求总数为115项。
- 新增正式需求编号为 `PLT-01`、`PLT-02`、`PROJ-12`、`ANA-01`、`COM-01`、`COM-02`、`SOL-01`、`IMP-01`、`IMP-02`、`RES-01`、`ACC-05`、`ACC-06`、`SRV-01`、`AST-01`、`AST-02`。
- 技术公告治理使用 `KNO-V3-01～08`，智能化使用 `ANA-V3-01～05`，主动服务使用 `SRV-V3-01`；客户与项目地图复用 `RPT-03`，备件库存告警与预测不进入平台V3。
- `FR-SRV-014`、项目日报周报、独立维保/续保经营需求和 `WO-07`、`WO-11`保持排除。
- 备件/RMA库存、发货、借还、补库和转移由外部系统拥有；平台只负责入口、业务映射、回调、门禁、对账和留痕。
- 其它已确认补充内容必须包含业务场景、核心规则、用户故事、可判定验收、数据字段、权限、异常降级和依赖。

---

### Task 1: Update PRD formal requirements and appendices

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`（新增需求章节、A.1/A.2/A.3/A.4附录）
- Reference: `docs/superpowers/specs/2026-08-12-confirmed-domain-gaps-writeback-design.md`
- Reference: `docs/reports/2026-08-10-PRD与13领域FR差异审查.md`

**Interfaces:**
- Produces the authoritative 115-item formal requirement set consumed by the generator and validator.

- [x] **Step 1: Add the 15 formal requirement blocks**

在PRD现有业务需求章节中按Owner增加15个完整需求块。每个需求块必须使用当前PRD格式，并在属性中记录原FR追溯。需求必须分别覆盖统一待办、统一文件、项目组合、组合看板、合同订单关系与回写、动态表单、质量/安全检查、服务商档案、持续服务交接、设备服务提示、RMA衔接和维保状态计算。

- [x] **Step 2: Update PRD indexes and counts**

更新 `A.1 V1/V2正式需求索引` 为115项；更新 `A.2 正式需求统计` 的版本、优先级和领域统计；新增 `A.3.1` 的14个V3编号；在 `A.4 OUT_OF_SCOPE索引` 增加 `FR-SRV-014`。

- [x] **Step 3: Reconcile exclusion and external-owner language**

全文统一表述：备件由外部系统承接；技术公告治理只进V3；工单时效不建设；`WO-01`的打卡补单3日规则不扩展成通用SLA；维保基本信息、状态计算和服务提示不构成独立维保经营模块。

- [x] **Step 4: Verify PRD before generation**

运行 `rg -n "PLT-01|PLT-02|PROJ-12|ANA-01|COM-01|COM-02|SOL-01|IMP-01|IMP-02|RES-01|ACC-05|ACC-06|SRV-01|AST-01|AST-02|KNO-V3-01|FR-SRV-014" "需求/PRD-项目实施交付管理平台.md"`；预期15个正式编号、14个V3编号和 `FR-SRV-014` 均能在PRD正文或附录找到，备件平台自建流程和通用工单SLA不得出现在正式需求中。

### Task 2: Extend generator ownership and V3 handling

**Files:**
- Modify: `scripts/generate_prd_domain_requirements.py`

**Interfaces:**
- Consumes the 115-item PRD index and requirement blocks.
- Produces exactly 13 domain Markdown files with unique formal ownership.

- [x] **Step 1: Add new formal requirement Owner mappings**

将新增编号加入显式Owner映射：`PLT-*→PLT`、`PROJ-*→PROJ`、`ANA-*→ANA`、`COM-*→COM`、`SOL-*→SOL`、`IMP-*→IMP`、`RES-*→RES`、`ACC-*→ACC`、`SRV-*→SRV`、`AST-*→AST`。保留既有PM/PRE/PLN/SCH/EXE/ACC/CLO/WO/INS/SUB/CUS/EQP/RPT/CUT/INT/NFR/AUT/CHG路由。

- [x] **Step 2: Add V3 rows from PRD**

让V3解析支持 `KNO-V3-*`、`ANA-V3-*`、`SRV-V3-*`，并在对应领域的V3表输出范围、依赖和“不进入当前开发验收”说明。不得把V3编号放入正式需求明细。

- [x] **Step 3: Preserve external-spare boundary**

对 `INT-06` 和 `CUT-08`的正文保留外部备件系统映射/回调/门禁内容；生成器不得根据旧FR标题生成平台内RMA、库存或发货流程。

- [x] **Step 4: Regenerate and inspect counts**

运行 `py -3.13 -B scripts/generate_prd_domain_requirements.py --prd "需求/PRD-项目实施交付管理平台.md" --output "specs/001-project-delivery-platform/domains"`；预期正式需求分布为：PLT 12、CUS 5、PROJ 13、COM 2、SOL 15、IMP 8、CUT 10、ACC 12、AST 10、RES 7、SRV 16、KNO 1、ANA 4，总计115。

### Task 3: Update validation gates

**Files:**
- Modify: `scripts/validate_prd_domain_generation.py`

- [x] **Step 1: Change formal count and expected new IDs**

将PRD正式需求期望值从100改为115，并校验15个新增编号全部存在且唯一归属。

- [x] **Step 2: Add V3 and exclusion assertions**

校验 `KNO-V3-01～08`、`ANA-V3-01～05`、`SRV-V3-01`存在于V3表；校验 `FR-SRV-014`存在于OUT_OF_SCOPE且不出现在正式需求明细；校验备件平台自建流程和通用工单SLA关键字不进入新增正式需求。

- [x] **Step 3: Add substantive-content assertions**

对15个新增需求分别检查业务规则、验收标准、权限、异常和来源追溯；检查新增需求中包含基础平台契约、外部备件Owner、持续服务交接和设备状态计算等关键边界。

### Task 4: Regenerate and review all 13 domain specifications

**Files:**
- Regenerate: `specs/001-project-delivery-platform/domains/*需求规格.md`
- Modify: `docs/reports/2026-08-12-原确认领域规格未承接需求清单.md`

- [x] **Step 1: Review new formal blocks by domain**

逐项确认15个新增编号只出现在指定领域，COM不再为空，AST/ACC/PLT/PROJ/SOL/IMP/RES/SRV/ANA均有对应新增内容，KNO正式需求仍只保留INT-04。

- [x] **Step 2: Review V3/exclusion boundaries**

确认技术公告、智能化和主动服务仅在V3表；确认备件业务仅出现外部系统集成契约；确认FR-SRV-014、日报周报和维保/续保经营仅在OUT_OF_SCOPE追溯。

- [x] **Step 3: Run independent semantic scan**

运行 `rg -n "自建库存|平台内RMA审批|通用工单SLA|工单超期升级|KNO-V3-|ANA-V3-|SRV-V3-|项目组合|持续服务交接|维保客观状态" specs/001-project-delivery-platform/domains`；预期外部备件和排除项只出现边界/排除语义，纳入项和V3项均有可追溯正文或范围表。

### Task 5: Final validation and delivery review

**Files:**
- Validate: `需求/PRD-项目实施交付管理平台.md`
- Validate: `specs/001-project-delivery-platform/domains/*需求规格.md`
- Validate: `docs/reports/2026-08-12-原确认领域规格未承接需求清单.md`

- [x] **Step 1: Run generation validator**

运行 `py -3.13 -B scripts/validate_prd_domain_generation.py --prd "需求/PRD-项目实施交付管理平台.md" --domains "specs/001-project-delivery-platform/domains"`；预期13个领域文件、115项正式需求、V3追溯、OUT_OF_SCOPE、唯一归属和已确认边界全部通过。

- [x] **Step 2: Run formatting and traceability checks**

运行 `git diff --check` 和 `rg -n "【待确认】|TODO|TBD|该功能不单独定义|legacy" "需求/PRD-项目实施交付管理平台.md" "specs/001-project-delivery-platform/domains"`；预期第一条通过，第二条无输出。

- [x] **Step 3: Review scope summary**

确认差异报告中的15项纳入、16项V3、5项外部备件承接和7项排除与PRD及13份领域文档一致；未修改用户工作区中无关的Excel、HTML和其他资料。
