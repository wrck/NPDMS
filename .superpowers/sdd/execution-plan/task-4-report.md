# Task 4 Report — T-CP-004 BPM 完整迁移单元

## Status

SUCCESS — 锁定版本 BPM 迁移单元已机械导入、装配、测试并提交。生产
BPM 自定义表 DDL 及依赖它的启动后流程定义创建/查询验收，按已确认路线
转入 T-CP-006 门禁。

## Implementation summary

- 从 `YunaiV/ruoyi-vue-pro` 锁定提交
  `a6558325b0f09017f531f1e5891613ef9b468132` 原样导入
  `yudao-module-bpm/` 250 个文件。
- 从同一提交原样导入
  `yudao-framework/yudao-spring-boot-starter-test/` 12 个文件，满足 BPM
  上游测试依赖。
- 原样导入 `sql/mysql/ruoyi-vue-pro.sql`，保留官方 BPM 菜单、权限和
  字典数据。
- 在 mini 基线的根 POM、framework、dependencies 和 server 中完成
  BPM 及测试 starter 的最小装配。
- 在 `docs/upstream-sources.md` 固化来源、哈希、验证结果和生产 DDL
  缺口；更新 `tasks/todo.md` 的 T-CP-004 状态。

## Commit

- `4df7ede build(upstream): 导入BPM平台迁移单元`
- 269 files changed, 27210 insertions, 8 deletions

## Main file ranges

- `pom.xml:16-20`
- `yudao-dependencies/pom.xml:402-413`
- `yudao-framework/pom.xml:20-29`
- `yudao-server/pom.xml:46-56`
- `yudao-module-bpm/**`
- `yudao-framework/yudao-spring-boot-starter-test/**`
- `sql/mysql/ruoyi-vue-pro.sql`
- `docs/upstream-sources.md:69-83,141-197`
- `tasks/todo.md:106`

## Verification evidence

1. Docker/JDK 25 focused test:
   `mvn -pl yudao-module-bpm -am test`
   - `BUILD SUCCESS`
   - BPM tests: 50 run, 0 failures, 0 errors, 6 skipped
   - 19 Reactor modules succeeded
2. Docker/JDK 25 server assembly:
   `mvn -pl yudao-server -am package -DskipTests`
   - `BUILD SUCCESS`
   - 21 Reactor modules succeeded
   - generated repackaged `yudao-server.jar`
3. Upstream consistency:
   - BPM: 250 source files, 0 SHA-256 mismatches
   - test starter: 12 source files, 0 SHA-256 mismatches
   - SQL source/target SHA-256:
     `1E78255B50C4AFE687FC60BDE7414E2AEFE4376E017801A93D909862E1C6F222`
4. `git diff --cached --check`: passed before commit.
5. Staged-scope audit: 269 intended files; no `yudao-ui` files and no
   credential/private-key path were included.

## Self-review

- Shared baseline files retain mini as authority; only the smallest BPM/test
  starter assembly changes were made.
- Imported BPM and test starter contents remain byte-identical to the locked
  full repository.
- No PMS business code or frontend code was added by this task.
- Existing upstream MapStruct/deprecation/Mockito dynamic-agent warnings do
  not fail the test or package gates.

## Concerns / next gate

- The public locked repository and its MySQL SQL file do not contain production
  `CREATE TABLE bpm_*` DDL. The official documentation points to a separately
  licensed BPM SQL attachment.
- T-CP-006 must obtain an authorized SQL package matching the locked code before
  empty-database startup and BPM create/query runtime acceptance. Flowable only
  auto-creates `ACT_`/`FLW_` engine tables; the eight mapped custom `bpm_*`
  tables must not be inferred from data objects.
- Six skipped tests are upstream-disabled cases, not new skips introduced here.
- The untracked `yudao-ui/` belongs to the parallel frontend task and was
  deliberately excluded from this commit.
