### Task 2: 修复领域迁移证据的冻结提交绑定

**Files:**
- Modify: `scripts/generate_domain_entity_migration_contract.py`
- Modify: `scripts/validate_domain_entity_migration_alignment.py`
- Modify: `scripts/tests/test_validate_domain_entity_migration_alignment.py`
- Regenerate only when required: `docs/traceability/domain-entity-migration-contract.json`
- Regenerate only when required: `docs/traceability/domain-entity-migration-contract.md`

**Interfaces:**
- Consumes: 契约登记的冻结 implementation commit 与该提交中的 `sql/migrations` Git blobs。
- Produces: 不依赖 NPDMS 当前 HEAD 的可重复迁移证据生成和校验。

- [ ] **Step 1: 写三类冻结提交测试**

  覆盖：HEAD 前进但相关 SQL 未变仍 PASS；生成器读取契约登记的提交而非工作树；将冻结提交改为含不兼容 SQL 的提交必须 FAIL。

- [ ] **Step 2: 运行定点测试确认现状失败**

  Run: `python -m unittest scripts.tests.test_validate_domain_entity_migration_alignment -v`

- [ ] **Step 3: 使用 Git 对象读取替代 HEAD 精确相等**

  实现按登记 commit 执行等价于 `git show <commit>:<path>` 的只读内容获取；验证 commit 存在、目标文件存在、内容与契约一致。禁止把当前工作树或当前 HEAD 当作冻结证据。

- [ ] **Step 4: 重生成并验证**

  Run: `python scripts/generate_domain_entity_migration_contract.py --check`

  Run: `python scripts/validate_domain_entity_migration_alignment.py`

  Run: `python -m unittest scripts.tests.test_validate_domain_entity_migration_alignment -v`

  Expected: 全部 PASS，NPDMS HEAD 可前进而冻结证据仍可复现。

- [ ] **Step 5: 自审并提交**

  确认无实现仓写操作、无迁移执行、无当前 HEAD 偶然依赖。读取 `$git-commit` skill后显式提交。

---

