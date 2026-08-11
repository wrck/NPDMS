# PRD 语义质量整改 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 撤回当前仅结构合格的 PRD V1.4 形式基线，以现有证据逐项重写 100 项 V1/V2 需求的业务规则、业务验收、权限数据范围、异常降级留痕和空泛数据字段，建立可阻止模板化内容再次发布的语义门禁，并在用户最终确认后重新发布正式基线。

**Architecture:** PRD 仍是唯一正式正文；新建独立语义校验器并接入现有基线校验器，使结构门禁和反模板门禁同时生效。正文按 V1 P0、其余 V1、V2 业务域分批整改，每项内容均从当前对话确认、PRD具体描述、最新原始资料和领域FR中取证；机器门禁只负责识别模板痕迹和最低可判定性，业务正确性由逐批证据核对和独立读者测试负责。

**Tech Stack:** Markdown、Python 3.13 标准库、`unittest`、Git、SHA-256。

## Global Constraints

- 禁止使用项目记忆；只使用当前工作树和当前对话中的确认结论。
- 禁止使用 `create-software-spec-docs` 技能；保留当前 PRD 文档结构，不套用新的通用模板。
- PRD细节以当前PRD需求描述为第一业务正文依据；其他资料只能补足、校验或指出冲突，不能静默覆盖已确认口径。
- 推导性增强标记 `【建议】`，证据不足且会影响范围或行为的内容标记 `【待确认】` 并向用户提问；正式发布前不得存在活动中的 `【待确认】`、`TBD`、`TODO`。
- V1、V2逐项给出详细规格；V3只保留范围与演进方向，不补写本轮验收承诺。
- 所有可能变化或扩展的业务类型、标记、级别、原因优先使用基础平台可配置字典；字典定义含义，业务规则定义行为。
- 除技术选型外统一称“基础平台”；PMS项目数据属于本平台内部核心数据；SMS统一称CRM。
- 项目和任务均不限制固定层级；设备在同一时点只直接归属一个最具体项目节点，父级按后代项目去重汇总。
- 不恢复日报周报、独立维保/续保经营、平台通用割接时效、WO-07、WO-11及其他已明确 `OUT_OF_SCOPE` 内容；设备档案继续保留维保基本信息。
- 设备连接与采集复用现有采集平台；本平台负责凭证、临时账号密码输入、任务下发和数据回调。临时密码不保存，用户可显式转存为加密凭证；未明确授权的凭证仅创建人可用。
- 工作树中用户已有的Excel、HTML和分析文档变更不纳入本计划的提交，除非用户另行明确授权。
- 每批只提交计划明确列出的文件；提交前运行 `git diff --check` 和对应门禁，不以脚本通过代替业务内容审阅。

---

## 文件结构

- Create: `scripts/validate_prd_semantics.py`：解析100项正式需求，执行反模板、重复段落、验收可判定性、权限具体性、异常闭环和数据字段具体性检查；支持按需求编号筛选。
- Create: `scripts/tests/test_validate_prd_semantics.py`：用最小PRD片段验证已知模板必失败、实质内容可通过、重复段落可定位、筛选编号有效。
- Modify: `scripts/validate_prd_baseline.py`：在原33项结构校验之后调用语义校验器；语义失败时正式基线不得通过。
- Modify: `需求/PRD-项目实施交付管理平台.md`：先改为“基线整改中”，再按批次重写100项正式需求和9项空泛数据字段，最终经确认后恢复“正式基线”。
- Modify: `docs/reports/2026-08-10-PRD与13领域FR差异审查.md`：同步整改状态、语义审查结论、统计、SHA-256和最终发布结论。
- Reference: `docs/superpowers/specs/2026-08-11-prd-semantic-remediation-design.md`：实质内容标准、证据优先级和完成条件。
- Reference: `specs/001-project-delivery-platform/domains/*.md`：领域FR及业务规则证据；只读，不在本计划中修改。
- Reference: `需求/**/*.xlsx`、`需求/**/*.html`：最新原始结构化数据和界面证据；只读，不在本计划中提交。

### Task 1: 建立失败优先的PRD语义门禁

**Files:**
- Create: `scripts/validate_prd_semantics.py`
- Create: `scripts/tests/test_validate_prd_semantics.py`
- Modify: `scripts/validate_prd_baseline.py`
- Test: `需求/PRD-项目实施交付管理平台.md`

**Interfaces:**
- Consumes: `validate_text(text: str, requirement_ids: set[str] | None = None) -> list[SemanticIssue]`。
- Produces: `SemanticIssue(req_id, field, code, detail)`；CLI `--prd` 和可重复的 `--requirement` 参数；无问题退出0，有问题退出1。
- Baseline integration: `validate_prd_baseline.py`调用全量语义校验，增加一项“V1/V2语义质量”检查并输出失败需求编号摘要。

- [ ] **Step 1: 写入已知模板必须失败的单元测试**

  测试夹具必须包含当前已知空泛句式：

  ```python
  GENERIC_RULE = "本需求的状态、字段、门禁、审批、同步频率和版本边界，以本条业务场景、已确认事项及验收标准中明确的内容为准。"
  GENERIC_PERMISSION = "仅本条“用户角色”及其被明确授权人员可执行；查看、编辑和审批范围遵循第二章角色权限及数据隔离规则。"
  GENERIC_EXCEPTION = "前置依赖或外部能力不可用时，执行本条或关联集成需求已明确的降级路径。"
  GENERIC_ACCEPTANCE = "依赖条件满足且有权用户在本需求适用场景发起业务操作，产生以下已定义业务结果。"
  ```

  断言每个句式分别产生 `GENERIC_RULE`、`GENERIC_PERMISSION`、`GENERIC_EXCEPTION`、`GENERIC_ACCEPTANCE` 问题码；两个需求复制同一字段时产生 `DUPLICATE_FIELD`。

- [ ] **Step 2: 运行测试并确认失败**

  Run: `py -3.13 -B -m unittest discover -s scripts/tests -p "test_validate_prd_semantics.py" -v`

  Expected: FAIL，原因是 `validate_prd_semantics` 尚不存在。

- [ ] **Step 3: 实现字段解析与确定性反模板规则**

  校验器必须执行以下确定性规则：

  ```python
  REQUIRED_FIELDS = ("核心业务规则", "业务验收标准", "权限与数据范围", "异常、降级及留痕要求", "涉及数据字段")
  MIN_ACCEPTANCE_SCENARIOS = 2
  MIN_DATA_TOKENS = 3
  ```

  - 支持“验收标准/业务验收标准”和“涉及数据/涉及数据字段”两个现有标题变体。
  - 禁止上述已知模板句及“处理结果关联原业务对象”“必填数据、角色权限、数据范围或前置门禁不满足”等机械附加句。
  - 归一化空白和编号后，任意两个不同需求的五类字段全文完全相同即失败并列出全部需求编号。
  - 验收至少包含两个 `WHEN/THEN` 场景：一个正常场景，一个明确的边界、权限不足或失败场景；THEN必须包含可观察的业务状态、记录、数量、去向或“不改变原状态”。
  - 权限段必须出现具体角色或责任主体、至少一个操作动词（查看/创建/编辑/提交/审批/授权/导出/执行）和明确数据范围或敏感字段边界。
  - 异常段必须同时出现具体失败触发、失败后的业务状态或禁止结果、重试/补偿/人工兜底之一，以及可追溯记录。
  - 数据字段至少有3个可区分数据项，禁止“相关信息”“业务所需字段”“以实际为准”等占位表达。

- [ ] **Step 4: 增加一组实质内容通过测试和编号筛选测试**

  通过夹具使用“项目经理提交拆分申请—父项目保持原状态—审批通过后生成子项目—记录拆分前后关系”的具体规则；权限写明项目经理可编辑本项目、上级项目经理只读后代汇总、平级项目默认不可见；异常写明权重和不等于100%时阻止生效并保留申请。断言全量无问题，且 `--requirement PM-01` 不返回PM-02问题。

- [ ] **Step 5: 接入现有基线校验器并运行测试**

  Run: `py -3.13 -B -m unittest discover -s scripts/tests -p "test_validate_prd_semantics.py" -v`

  Expected: PASS。

  Run: `py -3.13 -B -m py_compile scripts/validate_prd_semantics.py scripts/validate_prd_baseline.py`

  Expected: PASS。

- [ ] **Step 6: 在当前PRD上证明门禁会失败**

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md"`

  Expected: FAIL，至少定位98项重复核心规则、100项重复权限、100项重复异常及机械验收；不得输出“语义合格”。

- [ ] **Step 7: 提交语义门禁**

  Stage exactly: `scripts/validate_prd_semantics.py`、`scripts/tests/test_validate_prd_semantics.py`、`scripts/validate_prd_baseline.py`

  Commit subject: `test(spec): 增加PRD反模板语义门禁`

### Task 2: 撤回形式基线并公开整改状态

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`
- Modify: `docs/reports/2026-08-10-PRD与13领域FR差异审查.md`

**Interfaces:**
- Consumes: Task 1语义门禁失败证据和整改设计第2、6、9节。
- Produces: 文档状态“基线整改中”；明确原33项仅为结构门禁、不得作为语义合格或正式发布证据。

- [ ] **Step 1: 更新PRD元数据、修订记录和签署页结论**

  保留版本号 `V1.4` 以维持追溯；将文档状态从“正式基线”改为“基线整改中”。新增修订记录：撤回原因是四类规格大面积重复模板和空泛数据字段；整改期间不可作为开发验收基线。删除“33项机器门禁、两轮读者复核证明正式生效”的现行结论，历史提交事实只在修订记录中保留。

- [ ] **Step 2: 更新差异报告首页和结论**

  写明当前100项需求的范围统计仍用于整改追溯，但语义质量未通过，不能据此宣称“业务验收覆盖率100%”。登记已核实数据：核心规则98/100完全相同、权限100/100相同、异常100/100相同、68项含通用验收骨架、9项数据字段空泛。

- [ ] **Step 3: 验证撤回状态和语义失败同时可见**

  Run: `rg -n "基线整改中|98/100|100/100|68项|9项" "需求/PRD-项目实施交付管理平台.md" "docs/reports/2026-08-10-PRD与13领域FR差异审查.md"`

  Expected: PRD和报告均明确整改状态，报告包含量化问题；PRD不再把当前内容称为正式生效基线。

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md"`

  Expected: FAIL，表明撤回状态没有掩盖尚未整改的语义问题。

- [ ] **Step 4: 提交基线撤回声明**

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`、`docs/reports/2026-08-10-PRD与13领域FR差异审查.md`

  Commit subject: `docs(spec): 撤回PRD形式基线进入语义整改`

### Task 3: 重写V1 P0项目治理与现场实施需求

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`
- Reference: `specs/001-project-delivery-platform/domains/PROJ-项目治理需求规格.md`
- Reference: `specs/001-project-delivery-platform/domains/SOL-交付准备与方案需求规格.md`
- Reference: `specs/001-project-delivery-platform/domains/IMP-现场实施需求规格.md`

**Interfaces:**
- Consumes requirement IDs: `PM-01`、`PM-02`、`PM-03`、`PRE-02`、`PRE-04`、`PLN-01`、`PLN-04`、`SCH-01`、`SCH-05`、`EXE-01`、`EXE-02`、`EXE-03`、`EXE-04`、`EXE-06`。
- Produces: 14项无通用模板、可直接用于业务评审和验收设计的详细规格。

- [ ] **Step 1: 建立14项证据清单**

  对每项记录：PRD现有具体描述和已确认事项、差异报告映射FR、领域规格对应FR、最新Excel/HTML补充字段。冲突时按“当前对话确认 > PRD具体描述 > 最新原始资料 > 领域FR”处理；无法消解的冲突暂停该项并列入提问，不自行补齐。

- [ ] **Step 2: 重写项目治理与准备规则**

  对PM-01/02/03、PRE-02/04、PLN-01/04、SCH-01/05逐项写清触发人、状态转换、模板或审批版本、门禁阻断条件、数据归属和失败后的原状态。PM-02必须保留不限层级、直接子项目权重汇总、历史快照不追溯重算；PRE-02必须写清实施就绪聚合、适用项配置和豁免审批。

- [ ] **Step 3: 重写现场实施规则**

  EXE-01写明多批到货、部分签收、差异/拒收/补签、已签收设备才可安装和豁免条件；EXE-02写明安装位置与照片证据；EXE-03/04写明采集任务和人工上传边界；EXE-06写明割接上线所依赖的方案、审批、设备和执行条件。

- [ ] **Step 4: 为每项补足可判定验收、具体权限、异常与字段**

  每项至少包含正常场景和一个边界/权限/失败场景；验收结果必须指向具体状态、记录、数量或禁止变化。权限写出角色可执行动作和项目树数据范围；异常写出阻断、重试/人工处理和审计证据；数据字段写业务键、关联键、状态、输入、结果和追溯字段。

- [ ] **Step 5: 运行本批语义门禁**

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md" --requirement PM-01 --requirement PM-02 --requirement PM-03 --requirement PRE-02 --requirement PRE-04 --requirement PLN-01 --requirement PLN-04 --requirement SCH-01 --requirement SCH-05 --requirement EXE-01 --requirement EXE-02 --requirement EXE-03 --requirement EXE-04 --requirement EXE-06`

  Expected: PASS；全量门禁仍可因未处理批次失败。

- [ ] **Step 6: 提交本批整改**

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`

  Commit subject: `docs(spec): 重写V1项目治理与现场实施规格`

### Task 4: 重写V1 P0验收、客户设备与工时需求

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`
- Reference: `specs/001-project-delivery-platform/domains/ACC-验收与项目闭环需求规格.md`
- Reference: `specs/001-project-delivery-platform/domains/CUS-客户与服务关系需求规格.md`
- Reference: `specs/001-project-delivery-platform/domains/AST-资产管理需求规格.md`
- Reference: `specs/001-project-delivery-platform/domains/RES-资源与外包需求规格.md`

**Interfaces:**
- Consumes requirement IDs: `ACC-01`、`ACC-02`、`ACC-03`、`ACC-04`、`CLO-01`、`CLO-02`、`WO-01`、`CUS-03`、`CUS-04`、`EQP-01`。
- Produces: 10项实质规格；设备档案维保信息只作为设备基本信息，不引入续保经营。

- [ ] **Step 1: 重写验收与闭环6项**

  写清培训、满意度、验收报告、交付件的生成/外发/回收/归档状态；CLO-01逐项定义关闭门禁和未满足项，CLO-02定义申请、审批、驳回、重新提交和关闭生效。二维码/外发链接是V1基础，多通道接口增强按V2边界表达。

- [ ] **Step 2: 重写WO-01**

  明确钉钉同步、平台自主记录、人工补录和接口失败降级并存；超过3天时保留原始证据，工程师填写原因，服务经理一次审批决定是否计入有效工时，驳回后证据保留但工时不计入。

- [ ] **Step 3: 重写客户、联系人和设备档案3项**

  CUS-03明确CRM权威字段只读与平台扩展字段维护边界；CUS-04明确联系人项目范围和外发对象选择；EQP-01明确序列号唯一性、同一时点唯一直接项目归属、父项目去重统计、设备维保基本信息展示以及跨项目参与关系。

- [ ] **Step 4: 运行本批语义门禁并提交**

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md" --requirement ACC-01 --requirement ACC-02 --requirement ACC-03 --requirement ACC-04 --requirement CLO-01 --requirement CLO-02 --requirement WO-01 --requirement CUS-03 --requirement CUS-04 --requirement EQP-01`

  Expected: PASS。

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`

  Commit subject: `docs(spec): 重写V1验收客户设备与工时规格`

### Task 5: 重写V1 P0割接需求

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`
- Reference: `specs/001-project-delivery-platform/domains/CUT-变更切换与稳定治理需求规格.md`
- Reference: `需求/售后平台（割接+巡检）/1、割接平台/**/*.xlsx`
- Reference: `需求/售后平台（割接+巡检）/1、割接平台/**/*.html`

**Interfaces:**
- Consumes requirement IDs: `CUT-01`、`CUT-02`、`CUT-03`、`CUT-04`、`CUT-05`、`CUT-06`、`CUT-07`、`CUT-09`。
- Produces: 8项覆盖任务、分级、动态清单、方案、审批、执行闭环、配置和风险矩阵的实质规格。

- [ ] **Step 1: 按割接页面和领域证据重写CUT-01～05**

  明确任务状态、A/B/C/D分级输入与确认、D级跳过P3进入P4简化方案、清单动态生成依据、方案版本、分级审批责任和驳回回退点；不恢复平台通用割接时效，仅保留CUT-05已确认的A/B级专项提前时间边界。

- [ ] **Step 2: 重写CUT-06执行闭环**

  写明执行前门禁、设备凭证或临时账号密码入口、采集任务下发、实时步骤记录、停止/回退触发、稳定观察窗口、结果回调、失败证据和人工上传兜底；现有采集平台执行连接与采集，本平台不自建执行引擎。

- [ ] **Step 3: 重写CUT-07和CUT-09**

  配置项写明Owner、版本、生效范围、停用和历史解释；风险矩阵写明维度、命中依据、版本、适用范围及其对清单/方案/审批的作用，不以“可配置”代替业务规则。

- [ ] **Step 4: 运行本批语义门禁并提交**

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md" --requirement CUT-01 --requirement CUT-02 --requirement CUT-03 --requirement CUT-04 --requirement CUT-05 --requirement CUT-06 --requirement CUT-07 --requirement CUT-09`

  Expected: PASS。

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`

  Commit subject: `docs(spec): 重写V1割接业务规格`

### Task 6: 重写V1 P0集成与非功能需求

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`
- Reference: `specs/001-project-delivery-platform/domains/PLT-平台公共能力需求规格.md`

**Interfaces:**
- Consumes requirement IDs: `INT-01`、`INT-02`、`INT-05`、`INT-09`、`INT-12`、`NFR-01`、`NFR-02`。
- Produces: 7项明确系统Owner、同步边界、失败状态、补偿、凭证安全和量化验收的详细规格。

- [ ] **Step 1: 重写五项P0集成**

  每项明确源系统/目标系统、数据Owner、触发和频率、业务键、幂等键、成功状态、失败状态、重试上限或人工补偿入口、对账和审计。INT-01区分CRM项目/销售执行信息和ERP合同订单；INT-05区分V1钉钉/HR与V2 OA；INT-09写明SSO失败的受控账号兜底边界。

- [ ] **Step 2: 重写INT-12凭证与采集契约**

  明确已保存凭证和临时账号密码两种方式、临时仅保存用户名不保存密码、显式转存加密凭证、创建人默认权限、任务绑定短期授权、回调验签/幂等/重试、失败人工上传以及实施/割接V1和巡检V2入口边界。

- [ ] **Step 3: 重写NFR-01/02验收**

  保留PRD已有性能、安全、响应式浏览器、审计和凭证量化指标；每个指标写明测量对象、负载/样本、阈值和判定方式。密码/密钥明文只在可信采集执行进程内短暂存在，禁止落库、日志、消息和回调；刷新或再次执行必须重新输入临时密码。

- [ ] **Step 4: 运行本批语义门禁并提交**

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md" --requirement INT-01 --requirement INT-02 --requirement INT-05 --requirement INT-09 --requirement INT-12 --requirement NFR-01 --requirement NFR-02`

  Expected: PASS。

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`

  Commit subject: `docs(spec): 重写V1集成与非功能规格`

### Task 7: 重写其余V1需求

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`

**Interfaces:**
- Consumes requirement IDs: `PM-04`、`PM-07`、`PM-08`、`PM-10`、`PM-11`、`PRE-01`、`PLN-02`、`PLN-03`、`EXE-05`、`CLO-03`、`EQP-02`、`CUT-10`、`INT-03`。
- Produces: 13项V1 P1详细规格，保留同编号内明确的V2增强边界。

- [ ] **Step 1: 重写项目树、指派、工期与风险需求**

  PM-04/11必须覆盖任意层级授权、平级默认不可见、后代查询和指定业务层级查询；PM-08明确V1手动、V2自动；PRE-01/PLN-02/03写明工期基准、变更审批、逾期计算时点和统计口径；EXE-05写明风险台账事实Owner及CRM协同不替代平台闭环。

- [ ] **Step 2: 重写其余业务与集成需求**

  PM-07写明分类输入、字典和生效规则；PM-10写明回退/关闭条件和影响对象；CLO-03写明直签项目特殊回访；EQP-02写明Log版本、设备关联和敏感数据；CUT-10写明调研矩阵对清单生成的依据；INT-03写明CRM权威字段只读、平台扩展字段和同步失败标记。

- [ ] **Step 3: 运行13项门禁和全量V1门禁**

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md" --requirement PM-04 --requirement PM-07 --requirement PM-08 --requirement PM-10 --requirement PM-11 --requirement PRE-01 --requirement PLN-02 --requirement PLN-03 --requirement EXE-05 --requirement CLO-03 --requirement EQP-02 --requirement CUT-10 --requirement INT-03`

  Expected: PASS；结合Tasks 3～6后，52项V1需求全部通过。

- [ ] **Step 4: 提交其余V1整改**

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`

  Commit subject: `docs(spec): 完成其余V1需求语义整改`

### Task 8: 重写V2项目、方案、闭环与工单需求

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`

**Interfaces:**
- Consumes requirement IDs: `PM-05`、`PM-06`、`PM-09`、`PRE-03`、`PRE-05`、`SCH-02`、`SCH-03`、`SCH-04`、`CLO-04`、`CLO-05`、`CLO-06`、`WO-02`、`WO-03`、`WO-04`、`WO-05`、`WO-06`。
- Produces: 16项V2详细规格。

- [ ] **Step 1: 重写项目、准备和方案8项**

  明确借货转销、多期关联、人员批量变更、换货、交底书、方案导入、脚本解析和模板管理中的源对象/新对象关系、版本、审批、不可逆动作和失败补偿；不把关联复制写成未定义的“自动处理”。

- [ ] **Step 2: 重写闭环与工单8项**

  明确问卷类型字典、自动通过/驳回条件、导出数据范围；工单类型、扫码关联、工时审批、同步项目和割接保障必须写出状态、人员、设备/项目归属、计入统计条件和接口失败降级。

- [ ] **Step 3: 运行本批门禁并提交**

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md" --requirement PM-05 --requirement PM-06 --requirement PM-09 --requirement PRE-03 --requirement PRE-05 --requirement SCH-02 --requirement SCH-03 --requirement SCH-04 --requirement CLO-04 --requirement CLO-05 --requirement CLO-06 --requirement WO-02 --requirement WO-03 --requirement WO-04 --requirement WO-05 --requirement WO-06`

  Expected: PASS。

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`

  Commit subject: `docs(spec): 重写V2项目闭环与工单规格`

### Task 9: 重写V2转包、客户设备与分析需求

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`

**Interfaces:**
- Consumes requirement IDs: `SUB-01`、`SUB-02`、`SUB-03`、`SUB-04`、`SUB-05`、`CUS-01`、`CUS-02`、`EQP-03`、`EQP-04`、`EQP-05`、`EQP-07`、`RPT-01`、`RPT-02`、`RPT-04`。
- Produces: 14项V2详细规格。

- [ ] **Step 1: 重写转包5项**

  逐项定义申请、流程配置、付款回访门禁、付款信息、价格审批的责任角色、状态、金额/数量口径、审批版本、付款阻断和财务接口失败处理。

- [ ] **Step 2: 重写客户设备与分析9项**

  CUS-01只建设客户资产全景，不引入维保续保经营；CUS-02服务等级使用字典但必须定义服务行为；EQP-03/04/05/07写明设备Owner、MES同步、二维码和问题单关联；RPT-01/02/04写明指标定义、统计粒度、时间口径、去重规则、权限过滤和数据延迟展示。

- [ ] **Step 3: 运行本批门禁并提交**

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md" --requirement SUB-01 --requirement SUB-02 --requirement SUB-03 --requirement SUB-04 --requirement SUB-05 --requirement CUS-01 --requirement CUS-02 --requirement EQP-03 --requirement EQP-04 --requirement EQP-05 --requirement EQP-07 --requirement RPT-01 --requirement RPT-02 --requirement RPT-04`

  Expected: PASS。

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`

  Commit subject: `docs(spec): 重写V2转包资产与分析规格`

### Task 10: 重写V2割接与巡检需求

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`
- Reference: `specs/001-project-delivery-platform/domains/SRV-服务运营需求规格.md`
- Reference: `需求/售后平台（割接+巡检）/**/*.html`

**Interfaces:**
- Consumes requirement IDs: `CUT-08`、`INS-01`、`INS-02`、`INS-03`、`INS-04`、`INS-05`、`INS-06`、`INS-07`、`INS-08`、`INS-09`。
- Produces: 10项V2割接备件和巡检闭环详细规格。

- [ ] **Step 1: 重写CUT-08和巡检任务/规则**

  CUT-08定义备件请求、占用/领用结果和接口失败；INS-01/02/03/04定义任务状态、在线/离线方式、规则版本、设备连接入口、凭证权限、连通性预检、采集任务下发和失败后的方式切换。

- [ ] **Step 2: 重写巡检报告、问题与归档**

  INS-05/06/07/08/09定义报告生成版本、问题确认、误报反馈、跟踪项全部闭环后归档、规则字段字典和历史任务按原规则版本解释；不复制V1采集执行能力。

- [ ] **Step 3: 运行本批门禁并提交**

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md" --requirement CUT-08 --requirement INS-01 --requirement INS-02 --requirement INS-03 --requirement INS-04 --requirement INS-05 --requirement INS-06 --requirement INS-07 --requirement INS-08 --requirement INS-09`

  Expected: PASS。

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`

  Commit subject: `docs(spec): 重写V2割接备件与巡检规格`

### Task 11: 重写V2集成、权限与变更需求

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`

**Interfaces:**
- Consumes requirement IDs: `INT-04`、`INT-06`、`INT-07`、`INT-10`、`NFR-03`、`AUT-01`、`AUT-02`、`CHG-01`。
- Produces: 8项V2详细规格，至此100项正式需求全部整改。

- [ ] **Step 1: 重写集成与推送5项**

  INT-04/06/07/10分别写明公告、备件/授权/UMC、财务、短信邮件的数据Owner、业务键、方向、失败状态和补偿；NFR-03写明割接/巡检推送节点、去重、失败状态和站内消息兜底。

- [ ] **Step 2: 重写授权和变更3项**

  AUT-01/02明确申请人、审批人、凭证/License可见字段和项目范围；CHG-01明确变更对象、批准基线、影响项、审批前后状态和失败/撤回留痕。任何用户都不能查看或导出设备密码/密钥明文。

- [ ] **Step 3: 运行本批和全量语义门禁**

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md" --requirement INT-04 --requirement INT-06 --requirement INT-07 --requirement INT-10 --requirement NFR-03 --requirement AUT-01 --requirement AUT-02 --requirement CHG-01`

  Expected: PASS。

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md"`

  Expected: PASS，100项需求无已知模板、无跨需求完整重复字段、无空泛数据字段。

- [ ] **Step 4: 提交V2收口**

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`

  Commit subject: `docs(spec): 完成V2集成权限与变更规格整改`

### Task 12: 执行跨需求一致性复核并同步报告

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`
- Modify: `docs/reports/2026-08-10-PRD与13领域FR差异审查.md`

**Interfaces:**
- Consumes: 100项已通过单项语义门禁的PRD正文。
- Produces: 跨需求一致的对象Owner、状态、版本、统计口径、权限、集成边界和整改报告。

- [ ] **Step 1: 复核八类横向规则**

  逐项对照并修正：项目/任务任意层级；设备唯一直接归属和父级去重；CRM/ERP/平台数据Owner；可配置字典；设备凭证和临时密码；采集平台职责；V1/V2边界；OUT_OF_SCOPE排除。任何横向规则不得通过把相同大段文字复制到100项实现，公共规则应引用章节并补充本项具体动作和例外。

- [ ] **Step 2: 复核100项索引、优先级和版本统计**

  重新从正文计算100项、V1 52项、V2 48项、P0 39项、P1 60项、P2 1项；若正文真实变化导致统计不同，以实际解析结果修订附录和报告，不手工维持旧数字。

- [ ] **Step 3: 同步差异报告的整改结论**

  把问题量化保留为整改前证据，新增整改后结果、仍存在的`【建议】`和已关闭问题。当前状态仍为“基线整改中”，不得提前写“正式发布”。

- [ ] **Step 4: 计算并登记候选文档SHA-256**

  Run: `Get-FileHash -Algorithm SHA256 -LiteralPath "需求/PRD-项目实施交付管理平台.md"`

  Expected: 把当前哈希登记为“整改候选稿SHA-256”，不得称正式基线哈希。

- [ ] **Step 5: 运行全部机器门禁并提交候选稿**

  Run: `py -3.13 -B -m unittest discover -s scripts/tests -p "test_validate_prd_semantics.py" -v`

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md"`

  Run: `py -3.13 -B scripts/validate_prd_baseline.py --prd "需求/PRD-项目实施交付管理平台.md" --report "docs/reports/2026-08-10-PRD与13领域FR差异审查.md" --expected-version V1.4 --expected-status 基线整改中`

  Run: `git diff --check`

  Expected: 全部PASS；输出中的语义检查必须是独立项目，不再只有旧33项结构检查。

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`、`docs/reports/2026-08-10-PRD与13领域FR差异审查.md`

  Commit subject: `docs(spec): 收口PRD语义整改候选稿`

### Task 13: 独立读者测试和用户问题闭环

**Files:**
- Modify if needed: `需求/PRD-项目实施交付管理平台.md`
- Modify if needed: `docs/reports/2026-08-10-PRD与13领域FR差异审查.md`

**Interfaces:**
- Consumes: Task 12全绿候选稿。
- Produces: 新读者仅依赖当前工作树可得出的业务答案、问题清单和修订记录。

- [ ] **Step 1: 执行分层抽样读者测试**

  至少抽查20项：每个业务域至少1项，且覆盖V1 P0、V1 P1、V2、集成、NFR。读者必须回答每项的触发角色、允许动作、数据范围、正常结果、一个失败结果、留痕证据和版本边界，并指出答案所在段落。

- [ ] **Step 2: 执行关键横向场景测试**

  读者仅依赖PRD回答：多级项目和任务如何查询；设备如何归属和父级统计；到货部分签收如何阻断安装；割接D级如何流转；采集平台不可用如何处理；临时密码是否保存；打卡逾期如何计入；巡检何时归档；维保信息与续保经营如何区分。

- [ ] **Step 3: 汇总所有待确认问题并向用户逐项提问**

  每个问题必须提供影响需求编号、当前证据、互斥选项和推荐方案。用户确认后写回具体条款并标记问题关闭；不得用`【建议】`替代必须由业务决策的问题。

- [ ] **Step 4: 修复读者歧义并重新运行全部门禁**

  只修复有证据的歧义、遗漏和交叉冲突；不得扩展V3或恢复排除能力。重复Task 12全部命令，Expected: PASS。

- [ ] **Step 5: 提交读者测试修订**

  仅在存在修订时Stage exactly: `需求/PRD-项目实施交付管理平台.md`、`docs/reports/2026-08-10-PRD与13领域FR差异审查.md`

  Commit subject: `docs(spec): 修复PRD独立读者测试问题`

### Task 14: 用户确认后重新发布正式基线

**Files:**
- Modify: `需求/PRD-项目实施交付管理平台.md`
- Modify: `docs/reports/2026-08-10-PRD与13领域FR差异审查.md`

**Interfaces:**
- Consumes: 全部语义门禁、结构门禁、读者测试和用户待确认问题均关闭的候选稿。
- Produces: 经用户明确最终确认后生效的PRD V1.4正式基线。

- [ ] **Step 1: 向用户提交发布前证据包**

  给出100项整改覆盖、模板命中0、重复字段0、空泛数据字段0、机器门禁结果、读者测试结果、待确认问题0、工作树未纳入文件清单和候选SHA-256；在用户明确确认前保持“基线整改中”。

- [ ] **Step 2: 用户确认后更新正式状态**

  将PRD和报告状态改为“正式基线”，修订记录写明确认日期和语义整改范围；不虚构审核人姓名或签字。

- [ ] **Step 3: 重新计算正式SHA-256并运行最终门禁**

  Run: `Get-FileHash -Algorithm SHA256 -LiteralPath "需求/PRD-项目实施交付管理平台.md"`

  Run: `py -3.13 -B scripts/validate_prd_baseline.py --prd "需求/PRD-项目实施交付管理平台.md" --report "docs/reports/2026-08-10-PRD与13领域FR差异审查.md" --expected-version V1.4 --expected-status 正式基线`

  Run: `py -3.13 -B scripts/validate_prd_semantics.py --prd "需求/PRD-项目实施交付管理平台.md"`

  Run: `git diff --check`

  Expected: 全部PASS，报告登记的正式SHA-256与当前PRD完全一致。

- [ ] **Step 4: 提交正式基线**

  Stage exactly: `需求/PRD-项目实施交付管理平台.md`、`docs/reports/2026-08-10-PRD与13领域FR差异审查.md`

  Commit subject: `docs(spec): 重新发布PRD V1.4语义基线`

## Self-Review结果

- Spec coverage: 整改设计中的100项四类内容、9项数据字段、基线撤回、语义门禁、分批顺序、报告/hash、独立读者测试和重新发布条件均有对应Task。
- Requirement coverage: 39项V1 P0分布于Tasks 3～6；13项其余V1在Task 7；48项V2分布于Tasks 8～11；合计100项且无重复编号。
- Placeholder scan: 计划不含待填写步骤；文中 `TODO/TBD/【待确认】` 仅作为禁止规则或正式发布门禁说明。
- Boundary check: 不修改13领域规格、原始Excel/HTML或V3详细范围；不恢复已排除能力；不把机器规则当作业务正确性证明。
