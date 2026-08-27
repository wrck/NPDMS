# Task 1 实施报告：Phase 1 当前门禁快照对齐

## 范围

- 按锁定源提交 `b7c9d2a8de04391637aef942bc200ff43aec2122`，将 Phase 1 当前门禁 README 与状态汇总纳入 NPDMS 规格快照。
- 仅允许 `docs/engineering/gates/phase-1/README.md` 和 `docs/engineering/gates/phase-1/gate-status.md` 作为门禁目录中的受管当前文件；过程证据、`input/` 与 `archive/` 仍被拒绝。

## 修改文件

- `docs/specification-baseline/allowlist.json`
- `docs/specification-baseline/README.md`
- `docs/specification-baseline/manifest.json`
- `docs/engineering/gates/phase-1/README.md`
- `docs/engineering/gates/phase-1/gate-status.md`
- `scripts/specification_baseline.py`
- `scripts/tests/test_specification_baseline.py`

## 需求覆盖

- R1：受管 allowlist 仅新增两个当前门禁文件。
- R2：allowlist、README 与测试计数均从 109 更新为 111。
- R3：新增回归测试，验证 `APPROVED`、`READY_FOR_PHASE_2`，并拒绝 README 的当前 `NOT_READY_FOR_PHASE_2` 表述。
- R4：通过现有 Git 对象快照工具从锁定提交预检并应用，重新生成 manifest。
- R5：Q2 实施证据清单和非当前审查/归档材料经 `git diff --quiet` 确认未改变。
- R6：第二次 dry run 为 `ADD=0 REPLACE=0 KEEP=111 CONFLICT=0 TOTAL=111`。
- R7：已完成校验、自审和原子提交准备。

## 命令与结果

```text
py -3.13 scripts/sync_specification_baseline.py ...
SUMMARY ADD=0 REPLACE=2 KEEP=109 CONFLICT=0 TOTAL=111

py -3.13 scripts/sync_specification_baseline.py ... --apply
APPLIED docs/specification-baseline/manifest.json

py -3.13 scripts/sync_specification_baseline.py ...
SUMMARY ADD=0 REPLACE=0 KEEP=111 CONFLICT=0 TOTAL=111

py -3.13 scripts/validate_specification_baseline.py
SUMMARY PASS snapshot matches manifest and allowlist

py -3.13 scripts/validate_repository_baseline_rules.py
SUMMARY PASS repository reads the locked specification baseline

py -3.13 -m unittest scripts.tests.test_specification_baseline scripts.tests.test_repository_baseline_rules
Ran 20 tests ... OK
```

## 提交

- 状态：已创建一个原子提交，未推送。
- 提交：`efec2347be28f1cd3d6344a724378e747216941e`（`docs(gate): 对齐 Phase 1 当前门禁快照`）。
- 提交内容：本报告所列七个版本控制文件；本报告位于任务计划的忽略目录，未纳入提交。

## 限制与后续

- 本任务只对齐既有锁定规格快照，不改变 PRD、SDS、API、数据库、授权或业务状态机实现。
- 当前门禁源文件保留源提交中的既有前端 TypeScript 债务说明；该债务不在本任务范围内。

## Fix round 1/5：apply 原子性与冲突保护

### 根因与修复

- 原实现仅保证单个 `_atomic_write` 的原子替换；第二个目标写入失败时，第一个已替换的文件不会恢复。
- 原实现只在 `plan_snapshot` 时检查冲突；预检完成后、写入前发生的本地目标漂移不会被拒绝。
- 修复为：保存所有受管目标和 manifest 的预检状态；每次写入前及完成后重新校验该批状态；写入异常时按逆序恢复已写文件，且发现目标在写入期间被外部修改时拒绝覆盖。

### 新增负向测试

- `test_apply_rolls_back_when_second_write_fails`：模拟第二个 `_atomic_write` 失败，断言第一个目标恢复且 manifest 不创建。
- `test_apply_refuses_target_drift_after_preflight`：在 `plan_snapshot` 返回后修改目标，断言 apply 拒绝并保留本地编辑。

### 命令与结果

```text
py -3.13 -m unittest scripts.tests.test_specification_baseline
Ran 17 tests ... OK

py -3.13 -m unittest discover -s scripts/tests
Ran 36 tests ... OK

py -3.13 scripts/validate_specification_baseline.py
SUMMARY PASS snapshot matches manifest and allowlist

py -3.13 scripts/validate_repository_baseline_rules.py
SUMMARY PASS repository reads the locked specification baseline

py -3.13 scripts/sync_specification_baseline.py ...
SUMMARY ADD=0 REPLACE=0 KEEP=111 CONFLICT=0 TOTAL=111
```

### Fix 提交

- 状态：已创建独立原子 fix 提交，未推送。
- 提交：`6f748112e34fa526a8914a8346dda3af30f86719`（`fix(snapshot): 保护批次应用原子性`）。

## Fix round 2/5：最终替换间隙与 manifest 冲突保护

### 根因与事务设计

- 仅在 `_ensure_target_states` 后调用 `os.replace` 不是比较交换：外部编辑可落在两者之间，且替换后的期望字节无法证明编辑未被覆盖。
- `manifest.json` 未由 `plan_snapshot` 管理，原实现会覆盖非等值本地脏 manifest。
- 本轮先对所有写入同目录暂存。提交既有文件时，先原子移走当前目标至唯一 backup，校验 backup 与预期旧版本一致，再以 `os.link` 在“目标必须不存在”的条件下发布暂存内容；新增文件同样仅以无覆盖创建发布。
- 初次 backup 校验后再次校验 backup；旧句柄写入 backup 时，以同一条件发布原语将该变化恢复到目标并终止事务，避免清理时丢失并发内容。每个已发布文件和 manifest 均进入逆序条件回滚。

### 新增/调整回归

- `test_apply_refuses_drift_immediately_before_replacement`：最终预检之后、原子移走目标之前注入外部编辑，断言编辑保留且批次不发布。
- `test_apply_restores_backup_write_after_initial_backup_check`：初次 backup 校验后、link/cleanup 前模拟旧句柄写入 backup，断言变化恢复且事务失败。
- `test_apply_refuses_dirty_manifest`：断言非等值本地脏 manifest 拒绝应用、不创建受管目标。
- 第二次写入失败测试改为模拟第二次条件发布失败，继续验证前缀回滚。

### 命令与结果

```text
py -3.13 -m unittest scripts.tests.test_specification_baseline
Ran 20 tests ... OK

py -3.13 -m unittest discover -s scripts/tests
Ran 39 tests ... OK

py -3.13 scripts/validate_specification_baseline.py
SUMMARY PASS snapshot matches manifest and allowlist

py -3.13 scripts/validate_repository_baseline_rules.py
SUMMARY PASS repository reads the locked specification baseline

py -3.13 scripts/sync_specification_baseline.py ...
SUMMARY ADD=0 REPLACE=0 KEEP=111 CONFLICT=0 TOTAL=111
```

### Fix 提交

- 状态：已创建独立原子 fix 提交，未推送。
- 提交：`1ecf54893c3f0527850faadd6b74514cd36a50b9`（`fix(snapshot): 防止替换窗口覆盖本地修改`）。


## Fix round 3/5：协作锁与普通发布错误恢复

### 范围与边界

- `apply_snapshot` 以仓库级 `.apply.lock` 协作锁包裹状态捕获、plan、暂存、发布、最终校验与逆序回滚；第二个遵守协议的 apply 明确报“already in progress”且不修改文件。
- 该锁是轻量的仓库内协作协议，不承诺拦截不遵守锁的进程，尤其不承诺处理已 rename 文件的旧句柄任意外部写入。

### 修复与回归

- `os.link(staged, path)` 的任意 `OSError` 现在都会恢复已移入 backup 的当前文件，再将错误交给外层执行已发布前缀的逆序回滚；暂存和 backup 不残留。
- 覆盖非首受管文件普通 `OSError`、manifest 发布普通 `OSError`、回滚再次失败的错误报告、锁覆盖 plan/publish/rollback，以及锁持有时第二次 apply 拒绝。
- 保留脏 manifest、预检后漂移和最终发布前漂移保护测试。

### 命令与结果

```text
py -3.13 -m unittest scripts.tests.test_specification_baseline
Ran 24 tests ... OK

py -3.13 -m unittest discover -s scripts/tests
Ran 43 tests ... OK

py -3.13 scripts/validate_specification_baseline.py
SUMMARY PASS snapshot matches manifest and allowlist

py -3.13 scripts/validate_repository_baseline_rules.py
SUMMARY PASS repository reads the locked specification baseline

py -3.13 scripts/sync_specification_baseline.py ...
SUMMARY ADD=0 REPLACE=0 KEEP=111 CONFLICT=0 TOTAL=111
```

### Fix 提交

- 状态：已创建独立原子 fix 提交，未推送。
- 提交：`4e6cf2c35ef0892a044e62b56d83ac639f59d079`（`fix(snapshot): 串行化协作快照应用`）。

## Fix round 4/5：发布后 backup 清理失败恢复

### 范围与实现

- 仅修改 snapshot apply 的 post-link cleanup 责任边界与对应恶意注入测试；未改变协作锁、条件发布或批次回滚模型。
- 既有目标的发布链接成功后若 `backup.unlink()` 抛普通 `OSError`，helper 会在返回前以 backup 恢复当前目标并抛出清理错误，外层随后回滚此前已登记的发布前缀。
- 若首次恢复同时失败，错误消息聚合 cleanup 与 recovery 两项原因，并在协作锁内重试恢复；负测验证最终旧内容完整且 staged、backup、lock 均无残留。
- 覆盖非首文件与 `manifest.json` 的 cleanup `OSError`，并保留 dirty manifest、条件发布、协作锁及普通 link `OSError` 回归。

### 文件与需求覆盖

- `scripts/specification_baseline.py`：关闭 link 成功到 backup 清理失败之间的事务日志责任缺口。
- `scripts/tests/test_specification_baseline.py`：新增非首文件、manifest、cleanup 与首次 recovery 同时失败三类负测。
- 门禁目标：apply 失败恢复整批旧状态，且不遗留 `.staged`、`.backup`、`.apply.lock` 事务文件。

### 命令与结果

```text
py -3.13 -m unittest scripts.tests.test_specification_baseline
Ran 27 tests ... OK

py -3.13 -m unittest discover -s scripts/tests
Ran 46 tests ... OK

py -3.13 scripts/validate_specification_baseline.py
SUMMARY PASS snapshot matches manifest and allowlist

py -3.13 scripts/validate_repository_baseline_rules.py
SUMMARY PASS repository reads the locked specification baseline

py -3.13 scripts/sync_specification_baseline.py ...
SUMMARY ADD=0 REPLACE=0 KEEP=111 CONFLICT=0 TOTAL=111
```

### Fix 提交与限制

- 状态：已创建独立原子 fix 提交，未推送。
- 提交：`f6b8deff4c88006f79c6e7e8689fbbba3f61fe6e`（`fix(snapshot): 恢复清理失败的已发布目标`）。
- 限制：协作锁仍只约束遵守该仓库锁协议的 apply；不扩展为跨平台文件系统事务，也不承诺抵御不遵守锁的外部进程。
