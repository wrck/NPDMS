# Domain-Coded Database Naming Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将ADR-0019批准的领域表名和同义字段规则安全应用到当前DDL及全部迁移证据，同时保持旧库来源字段原样可追溯。

**Architecture:** 新增一份机器可读命名契约作为ADR-0019的可执行投影，由单一生成器对DDL、目标字段目录和目标引用进行确定性转换；独立校验器检查领域前缀、名称唯一性、表名缩写白名单、字段同义词和证据哈希。旧库`sourceTable/sourceColumn/sourceDefinition`永不改名，只有新平台目标表、目标字段和相关约束引用发生变化。

**Tech Stack:** Python 3、MySQL 8.4 DDL、JSON/JSONL、Markdown、`unittest`、Git原生校验。

## Global Constraints

- PRD V1.6是业务语义最高依据；本计划不新增角色、状态、审批、门禁或数据Owner。
- 表名格式为`<13领域编码>_<完整领域对象名称>`，删除业务系统前缀`pms_`。
- 表名只允许`config`、`sn`两个标准缩写；`rel/ref/map`必须写为`relation/reference/mapping`。
- 同义字段统一命名；字段允许ADR-0019登记的受控缩写，并在无歧义时尽可能简短。
- 旧库及数据元来源字段保持原名、原类型、原说明和原证据坐标；禁止旧库DDL/DML和跨库SQL。
- 不修改已执行的Flyway迁移；实施仓库后续只能新增前向迁移。
- 本计划只关闭“命名裁决及证据一致性”，不自动批准尚未裁决的索引、外键、CHECK、表选项或生产迁移。
- 当前工作树包含其他未提交变更；每次暂存或提交必须限定到本计划文件，并在用户明确要求提交后执行。

---

### Task 1: 建立机器可读命名契约和负向校验

**Files:**
- Create: `docs/traceability/database-naming-contract.json`
- Create: `scripts/validate_database_naming_contract.py`
- Create: `scripts/tests/test_validate_database_naming_contract.py`
- Modify: `docs/decisions/0019-domain-coded-database-naming.md`

**Interfaces:**
- Consumes: ADR-0019的52张表映射、NAM-001～NAM-006字段映射和13领域编码。
- Produces: `load_contract(path) -> dict`、`validate_contract(root) -> list[str]`，供后续生成器和门禁校验复用。

- [ ] **Step 1: 写失败测试，覆盖契约边界**

```python
def test_rejects_duplicate_target_table(self):
    contract = self.valid_contract()
    contract["tables"][1]["target"] = contract["tables"][0]["target"]
    self.assertIn("duplicate target table", "\n".join(validate_payload(contract)))

def test_rejects_unapproved_table_abbreviation(self):
    contract = self.valid_contract()
    contract["tables"][0]["target"] = "com_order_contract_rel"
    self.assertIn("unapproved table abbreviation", "\n".join(validate_payload(contract)))

def test_accepts_config_and_sn_table_abbreviations(self):
    contract = self.valid_contract()
    contract["tables"].extend([
        {"source": "pms_device_sn", "target": "ast_device_sn", "owner": "AST"},
        {"source": "pms_crm_execution_config", "target": "com_crm_execution_config", "owner": "COM"},
    ])
    self.assertEqual([], validate_payload(contract))
```

- [ ] **Step 2: 运行定点测试并确认失败**

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m unittest scripts.tests.test_validate_database_naming_contract -v
```

Expected: FAIL，提示校验模块或函数尚不存在。

- [ ] **Step 3: 创建命名契约**

契约固定包含：

```json
{
  "schemaVersion": 1,
  "status": "ACCEPTED",
  "decisionRef": "ADR-0019",
  "domainCodes": ["ACC", "ANA", "AST", "COM", "CUS", "CUT", "IMP", "KNO", "PLT", "PROJ", "RES", "SOL", "SRV"],
  "allowedTableAbbreviations": {"configuration": "config", "serial_number": "sn"},
  "forbiddenTableTokens": ["rel", "ref", "map"],
  "tables": [],
  "fields": []
}
```

`tables`逐项登记ADR中的52个`source/target/owner`；`fields`逐项登记NAM-001～NAM-006的源表、源字段、目标表、目标字段和证据依据。

- [ ] **Step 4: 实现契约校验器**

校验器必须检查：52个源表和目标表唯一、领域前缀与Owner一致、目标表无`pms_`、只允许`config/sn`表名缩写、禁止`rel/ref/map`、字段映射引用已登记表、ADR表格与JSON契约逐项一致。

- [ ] **Step 5: 运行定点测试和真实契约校验**

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m unittest scripts.tests.test_validate_database_naming_contract -v
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/validate_database_naming_contract.py
```

Expected: 全部PASS，输出`tables=52 fields=6`。

### Task 2: 实现确定性命名转换器并重写DDL

**Files:**
- Create: `scripts/apply_database_naming_contract.py`
- Create: `scripts/tests/test_apply_database_naming_contract.py`
- Modify: `specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql`

**Interfaces:**
- Consumes: `database-naming-contract.json`。
- Produces: `transform_ddl(text, contract) -> str`、CLI `--check`和默认写入模式。

- [ ] **Step 1: 写失败测试，证明转换是词法安全且幂等的**

```python
def test_transforms_identifiers_but_preserves_comments(self):
    ddl = "CREATE TABLE pms_order_contract_rel (config_id BIGINT COMMENT 'pms_order_contract_rel来源说明');"
    result = transform_ddl(ddl, self.contract)
    self.assertIn("CREATE TABLE com_order_contract_relation", result)
    self.assertIn("config_id BIGINT", result)
    self.assertIn("'pms_order_contract_rel来源说明'", result)

def test_transform_is_idempotent(self):
    once = transform_ddl(self.ddl, self.contract)
    self.assertEqual(once, transform_ddl(once, self.contract))
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m unittest scripts.tests.test_apply_database_naming_contract -v
```

Expected: FAIL，转换器尚不存在。

- [ ] **Step 3: 实现DDL标识符转换**

转换器只处理DDL标识符位置：`CREATE TABLE`、外键引用表、列定义、索引/约束列引用和生成列表达式；不得替换注释、字符串常量或旧库证据文本。NAM-001～NAM-006按`table+column`精确匹配，不能全局替换`quantity`或`resolution`。

- [ ] **Step 4: 生成新DDL并验证解析结果**

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/apply_database_naming_contract.py
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/apply_database_naming_contract.py --check
```

Expected: 52张表、1,076列；目标表全部命中契约；6个字段映射全部应用；第二次运行无差异。

- [ ] **Step 5: 运行DDL解析与漂移生成器单测**

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m unittest scripts.tests.test_apply_database_naming_contract scripts.tests.test_generate_ddl_drift_review -v
```

Expected: PASS。

### Task 3: 从新DDL重建字段目录和所有目标引用

**Files:**
- Create: `scripts/generate_target_field_catalog.py`
- Create: `scripts/tests/test_generate_target_field_catalog.py`
- Modify: `specs/001-project-delivery-platform/evidence/migration/target-field-catalog.jsonl`
- Modify: `specs/001-project-delivery-platform/evidence/migration/target-field-catalog-summary.json`
- Modify: `specs/001-project-delivery-platform/evidence/migration/core-field-mapping.jsonl`
- Modify: `specs/001-project-delivery-platform/evidence/migration/core-field-mapping-summary.json`
- Modify: `specs/001-project-delivery-platform/evidence/migration/legacy-physical-field-canonical.jsonl`
- Modify: `specs/001-project-delivery-platform/evidence/migration/legacy-physical-field-mapping.jsonl`
- Modify: `specs/001-project-delivery-platform/evidence/migration/schema-business-element-mapping.jsonl`
- Modify: `specs/001-project-delivery-platform/evidence/migration/semantic-data-element-canonical.jsonl`
- Modify: `specs/001-project-delivery-platform/evidence/migration/semantic-data-element-mapping.jsonl`
- Modify: `specs/001-project-delivery-platform/evidence/migration/complete-migration-summary.json`
- Modify: `specs/001-project-delivery-platform/evidence/migration/migration-validation.json`

**Interfaces:**
- Consumes: 新DDL、旧目标字段目录的`domain/fieldClass/dataElementRefs`、命名契约。
- Produces: `build_catalog(ddl, prior_catalog, contract) -> list[dict]`及全部目标引用一致的新证据。

- [ ] **Step 1: 写失败测试，锁定来源证据保护规则**

```python
def test_source_coordinates_are_not_renamed(self):
    row = {"sourceTable": "pm_project_property_from_sms", "sourceColumn": "submitTime", "targets": ["pms_crm_execution_order.submit_time"]}
    updated = rewrite_target_references(row, self.contract)
    self.assertEqual("pm_project_property_from_sms", updated["sourceTable"])
    self.assertEqual("submitTime", updated["sourceColumn"])
    self.assertEqual(["com_crm_execution_order.submit_time"], updated["targets"])

def test_catalog_metadata_survives_rename(self):
    item = self.build_item("pms_sales_order_line", "order_qty")
    renamed = remap_catalog_item(item, self.contract)
    self.assertEqual(item["dataElementRefs"], renamed["dataElementRefs"])
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m unittest scripts.tests.test_generate_target_field_catalog -v
```

Expected: FAIL，生成器尚不存在。

- [ ] **Step 3: 实现DDL单向字段目录生成和目标引用重写**

生成器以新DDL为字段事实，以旧目录仅补充`domain/fieldClass/dataElementRefs`。目标引用支持`table.column`、`targets[]`、`targetBindings[]`和单值`target`；任何无法映射的旧目标引用必须报错，不得静默保留`pms_*`。

- [ ] **Step 4: 重建全部字段和迁移证据**

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/generate_target_field_catalog.py
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/generate_target_field_catalog.py --check
```

Expected: 52表、1,076列；所有目标引用使用新表名；所有`sourceTable/sourceColumn/sourceRefs/evidenceRefs`保持原值；汇总哈希与新DDL一致。

- [ ] **Step 5: 增加并运行残留引用负向检查**

Run:

```powershell
rg -n '"target(s|Bindings)?".*pms_|"tableName":\s*"pms_' specs/001-project-delivery-platform/evidence/migration -g '*.json' -g '*.jsonl'
```

Expected: 无匹配；`rg`退出码1表示检查通过。

### Task 4: 同步领域对象—表契约和正式SDS

**Files:**
- Modify: `docs/traceability/domain-object-table-map.json`
- Modify: `docs/traceability/domain-entity-migration-contract.json`
- Modify: `docs/traceability/domain-entity-migration-contract.md`
- Modify: `docs/design/08-data-model.md`
- Modify: `docs/design/08a-domain-entity-migration-alignment.md`
- Modify: `docs/design/09-database-design.md`
- Modify: `scripts/generate_domain_entity_migration_contract.py`
- Modify: `scripts/validate_domain_entity_migration_alignment.py`
- Modify: `scripts/tests/test_validate_domain_entity_migration_alignment.py`

**Interfaces:**
- Consumes: 命名契约、新DDL和正式82实体Owner映射。
- Produces: 精确的`DomainEntity -> Owner -> targetTables`契约；任何表名错误归属可被负向测试拦截。

- [ ] **Step 1: 写失败测试，禁止对象契约继续引用旧前缀或错误领域**

```python
def test_rejects_legacy_system_prefix(self):
    self.object_map["Project"]["targetTables"] = ["pms_project"]
    self.assertTrue(any("legacy system prefix" in error for error in self._validate()))

def test_rejects_wrong_domain_prefix(self):
    self.object_map["Project"]["targetTables"] = ["ast_project"]
    self.assertTrue(any("owner prefix" in error for error in self._validate()))
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m unittest scripts.tests.test_validate_domain_entity_migration_alignment -v
```

Expected: 新增负向用例FAIL。

- [ ] **Step 3: 更新对象—表映射和正式SDS引用**

52张当前DDL表按ADR-0019映射；其余已设计但尚未进入当前DDL的目标表同样删除`pms_`并使用Owner领域前缀，且完整对象语义不缩短。Device Access & Collection对象Owner仍为PLT，因此目标表使用`plt_`而不是`dac_`。

- [ ] **Step 4: 重新生成82实体迁移契约**

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/generate_domain_entity_migration_contract.py
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/generate_domain_entity_migration_contract.py --check
```

Expected: 82实体、92来源保持不变；只改变目标表名称和与新DDL绑定的哈希/引用。

- [ ] **Step 5: 运行领域迁移对齐校验**

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/validate_domain_entity_migration_alignment.py
```

Expected: PASS，错误Owner、错误表名、旧`pms_`前缀负向用例均可拦截。

### Task 5: 重建P3-E09证据并执行全量复核

**Files:**
- Modify: `specs/001-project-delivery-platform/evidence/migration/ddl-drift-review.json`
- Modify: `specs/001-project-delivery-platform/evidence/migration/ddl-drift-review.md`
- Modify: `specs/001-project-delivery-platform/evidence/migration/ddl-current-constraint-inventory.json`
- Modify: `specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json`
- Modify: `specs/001-project-delivery-platform/evidence/migration/ddl-model-decision-catalog.md`
- Modify: `docs/engineering/gates/phase-3/evidence-packet-templates/p3-e09-submission.json`
- Modify: `docs/engineering/gates/phase-3/phase3-evidence-register.json`
- Modify: `docs/engineering/gates/phase-3/gate-status.md`
- Modify: `docs/engineering/gates/phase-3/self-review.md`
- Modify: `scripts/tests/test_validate_ddl_item_decision_register.py`

**Interfaces:**
- Consumes: 新DDL、新字段目录、新对象—表契约和ADR-0019。
- Produces: 与新DDL哈希一致的P3-E09审查包；命名项有明确决策，未确认约束仍为`DEFER`。

- [ ] **Step 1: 重建DDL漂移、约束和逐项裁决输入**

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/generate_ddl_drift_review.py
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/generate_ddl_model_decision_catalog.py
```

Expected: 52表、1,076列、约束和表选项数量可复核；当前DDL哈希在全部派生证据中一致。

- [ ] **Step 2: 将已确认命名项登记为有证据决策**

仅ADR-0019直接覆盖的表名和NAM-001～006可登记`AMEND_CURRENT`并引用ADR-0019；索引、外键、CHECK和表选项继续`DEFER`，除非另有明确业务或数据架构裁决。最终`approvedDdlSha256`保持空值，直到全部1,602项完成Owner和Reviewer签署。

- [ ] **Step 3: 运行P3-E09和Phase 3校验**

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/validate_database_naming_contract.py
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/validate_ddl_item_decision_register.py
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/validate_phase3_evidence_register.py
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/validate_sds_phase3.py
```

Expected: 全部PASS；P3-E09仍为`OPEN/BLOCKED_BY_MODEL_DECISION`，但阻断原因缩小为尚未确认的约束、表选项及剩余模型项。

- [ ] **Step 4: 运行全量单测和格式检查**

Run:

```powershell
& 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -m unittest discover -s scripts/tests
git diff --check
```

Expected: 全部测试PASS，`git diff --check`无输出。

- [ ] **Step 5: 完成自审并等待独立复审**

自审必须明确：表名规则已机器化、旧库来源未改写、字段目录与DDL同哈希、对象Owner前缀一致、P3-E09未被提前关闭。只有独立复审确认无Critical/Required且全部剩余模型项有Owner证据时，才允许生成`approvedDdlSha256`。

## Self-Review Result

- ADR-0019的领域前缀、完整对象语义、`config/sn`例外和字段同义词均有对应任务。
- 旧数据元/旧库来源保护、DDL、字段目录、对象表映射、迁移证据和P3门禁形成闭环。
- 计划不把命名确认扩大为约束、表选项或生产迁移批准。
- 所有生成步骤均有`--check`或独立校验；没有依赖人工逐文件替换。
