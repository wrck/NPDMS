# T-CP-008 实施报告：资产及 V2 业务模块骨架

> 2026-07-29 收口复核已提交：`8ccc650 fix(test): 解析PMS模块依赖声明`。POM 依赖检查已改为结构化 XML 解析，仅识别 `project/dependencies/dependency`；解析本 POM 与相对父 POM 的属性后再比较 artifactId，排除 `dependencyManagement`、`test` 和 `optional` 调用。夹具新增 dependencyManagement、本地/继承属性化跨域依赖拒绝，以及 Kotlin `public open class`、`public value class` 合法契约覆盖。RED 为 `public open class` 被误报 API 为空；GREEN 已通过 `verify-pms-module-boundaries-test.ps1` 和真实仓库 `verify-pms-module-boundaries.ps1`。

> 2026-07-29 relativePath 收口复核已提交：`e6d08a2 fix(test): 区分Maven相对父POM路径`。`<parent>` 未声明 `relativePath` 时才采用 Maven 默认 `../pom.xml`；显式空 `<relativePath/>` 不再继承相对父 POM。若父 POM 不可解析且直接依赖 artifactId 仍包含 `${...}`，检查器以 POM 路径和 artifactId 明确失败，避免静默跳过跨域依赖。RED 为显式空路径错误继承根属性并报跨域依赖；GREEN 已通过新增的显式空路径、缺失父 POM夹具、全部夹具和真实仓库检查。

> 2026-07-29 Maven 内置属性复核已提交：`445d892 fix(test): 支持Maven内置模型属性`。POM 模型属性现会解析 `project.*` 与 `pom.*` 的 groupId/artifactId/version，并以当前 `<parent>` 坐标或可解析父模型提供 `parent.*`；子 POM 缺失 groupId/version 时继承其父模型坐标。未知外部父属性仍保留直接依赖 fail-closed。RED 为合法 `${project.groupId}` 被拒绝；GREEN 覆盖全部九个内置别名可正常处理，以及 `${parent.artifactId}` 指向已知 PMS 模块仍被跨域规则拒绝。

## 状态

已完成并提交：`b127672 feat(pms): 创建资产及V2业务模块骨架`。

复核修复追加提交：`eb585e7 fix(test): 修复PMS模块边界检查`。

第二轮复核修复追加提交：`5048ebb fix(test): 收紧PMS模块契约检查`。

第三轮复核修复追加提交：`7ec72dc fix(test): 完善PMS数据所有权检查`。

## 实现内容

- 新建 `pms-module-asset`、`pms-module-outsourcing`、`pms-module-analytics` 和 `pms-module-integration` Maven 模块，以及各自的领域 `package-info.java`。
- 根 `pom.xml`、`yudao-server/pom.xml` 与 `docker/backend/Dockerfile` 均装配四个新增模块。
- 新增 `tests/infrastructure/verify-pms-module-boundaries.ps1`：校验全部 PMS 模块的 Reactor、服务端及 Docker 装配；拒绝空 `-api` 模块和直接依赖其他 PMS `-biz` 模块；确认分析与集成骨架未声明持久化实体。
- 分析模块仅声明只读分析边界；集成模块仅声明通过目标领域 API 写入业务数据的边界，二者没有创建客户、项目、设备等核心主数据实体或表。
- 将 `T-CP-008` 与 `CP-C1` 在 `tasks/todo.md` 中标记完成。

## TDD 证据

### RED

先新增并执行边界检查：

```powershell
& .\tests\infrastructure\verify-pms-module-boundaries.ps1
```

实现前预期失败，实际输出：

```text
PMS module is missing its Maven POM: ...\pms-module-asset\pom.xml
```

失败原因是目标模块尚未创建，符合测试意图。

### GREEN

完成最小模块和装配配置后重新执行：

```powershell
& .\tests\infrastructure\verify-pms-module-boundaries.ps1
```

结果：`PMS module boundary verification passed.`

## 测试与验证

| 命令 | 结果 |
| --- | --- |
| `& .\tests\infrastructure\verify-pms-module-boundaries.ps1` | 通过；验证 Reactor、`yudao-server`、Docker 上下文、无空 API、无 PMS `-biz` 直连及无分析/集成持久化实体。 |
| `docker run --rm --name pms-cp008-maven -v "<worktree>:/workspace" -w /workspace maven:3.9.11-eclipse-temurin-25 mvn -pl yudao-server -am verify -DskipTests` | 通过；Docker JDK 25 Reactor 29/29 `SUCCESS`，包含四个新增模块与 `yudao-server`；总耗时 06:14。 |
| `git diff --check` | 通过；无空白错误。 |

Maven 命令显式使用 `-DskipTests`，原因是该次验证的目标是 Docker/JDK 25 统一反应器构建；模块边界行为由上述 PowerShell 检查实际执行。

## 变更文件

- `pom.xml`
- `yudao-server/pom.xml`
- `docker/backend/Dockerfile`
- `pms-module-asset/`
- `pms-module-outsourcing/`
- `pms-module-analytics/`
- `pms-module-integration/`
- `tests/infrastructure/verify-pms-module-boundaries.ps1`
- `tasks/todo.md`

## 自审

- 四个模块均采用 `pms-module-*` 命名，未创建空 `-api` 子模块。
- 四个模块 POM 均未直接依赖任何 PMS `-biz` 模块。
- `analytics` 与 `integration` 仅为边界骨架，未新增 Java 持久化实体、Mapper、Service 或 SQL 表。
- `sql/mysql/*.sql` 既未修改也未暂存；`tests/e2e/` 未修改也未暂存。
- 暂存 diff 已检查，无凭据或敏感信息；提交后工作树仅保留任务开始前已有的未跟踪 SQL 与 `tests/e2e/`。

## 顾虑

- 当前模块是刻意保持最小的骨架。后续引入跨模块调用时，必须先冻结稳定契约，再按 DEC-013 创建对应 `-api` 子模块；不得以直接 `-biz` 依赖替代契约。
- 分析和集成将来可拥有指标快照、映射、同步批次等其领域数据；新增持久化前应把边界检查从“骨架无实体”演进为“禁止核心主数据所有权”的精确规则。

## 复核修复：模块边界检查

### 发现与修复

- 原检查会把任意 `${module}-api` 目录都视为违规。现仅拒绝没有 Java/Kotlin 契约源文件的空 API，或没有其他 PMS 模块依赖的 API；非空且有稳定调用方的 API 契约通过。
- 原检查只匹配 `-biz` artifactId。现从每个已知 PMS 模块的 Maven `dependency` 中提取 artifactId，并拒绝对其他平铺 PMS 模块的直接依赖，同时继续拒绝 `-biz` 依赖。
- 原检查只扫描 Java `@TableName`。现对 analytics/integration 扫描 Java、Kotlin、Mapper XML、模块 SQL，以及名称属于对应领域的根 `sql/migrations` 文件；拒绝客户、项目、任务、设备、资产、备件和 RMA 等核心主数据表或典型 DO/Entity/Mapper/Repository 定义，同时允许 `pms_analytics_snapshot`、`pms_integration_sync_batch` 等本域快照、映射与同步数据。

### 复核 TDD 证据

新增受控仓库夹具测试后，旧检查执行：

```powershell
& .\tests\infrastructure\verify-pms-module-boundaries-test.ps1
```

RED：`A parameter cannot be found that matches parameter name 'RepositoryRoot'.` 旧脚本无法在夹具中验证合法 API 或持久化边界。

最小修复后 GREEN：

```powershell
& .\tests\infrastructure\verify-pms-module-boundaries-test.ps1
& .\tests\infrastructure\verify-pms-module-boundaries.ps1
```

## 收口修复：未解析 scope / optional 属性必须 fail-closed

### 实现

- `Get-MavenDependencies` 现在分别解析 `scope` 与 `optional`，并在解析结果仍含 `${...}` 时明确失败；不会再将未知作用域误判为非 `test`，或将未知可选值误判为 `false`。
- 未声明 scope 时仍使用 `compile`，字面量 `test` 与 `optional=true` 的既有“非稳定调用方”语义保持不变。

### TDD 证据

RED（仅加入夹具，未修改检查器）：

```powershell
& .\tests\infrastructure\verify-pms-module-boundaries-test.ps1
```

预期失败，实际输出：

```text
Expected checker to reject fixture: unresolved Maven property in direct dependency scope
```

两个新夹具均使用不可见的外部父 POM，分别保留 `${api.scope}` 与 `${api.optional}`；旧实现将其错误视为稳定调用方。

GREEN：

```powershell
& .\tests\infrastructure\verify-pms-module-boundaries-test.ps1
& .\tests\infrastructure\verify-pms-module-boundaries.ps1
git diff --check
```

结果：夹具检查与真实仓库检查均通过，且无空白错误。

### 修改范围与自审

- 已修改：`tests/infrastructure/verify-pms-module-boundaries.ps1`、`tests/infrastructure/verify-pms-module-boundaries-test.ps1`。
- 未修改、不会暂存或提交：既有 `pom.xml`、`tests/e2e/`、`tests/performance/`、`tests/security/` 与 `sql/mysql/*.sql` 工作树改动。
- 自审确认：未解析 dependency artifactId、scope、optional 均 fail-closed；已解析的字面量 `compile`、`test`、`true` 继续按既有规则处理。
- 独立提交：`c1b041b fix(test): ????????`；未执行 push。PowerShell 原生管道在此工作树将中文提交说明替换为问号；任务禁止改写历史，因此未 amend。

结果依次包含：`PMS module boundary checker fixture tests passed.` 与 `PMS module boundary verification passed.`

夹具覆盖：合法非空且有调用方的 API、空 API、无稳定调用方 API、平铺跨域依赖、Java 实体、Mapper XML、根迁移核心主数据，以及允许的分析快照和集成同步批次。

## 第二轮复核修复：可消费 API 与迁移归属

### 发现与修复

- API 有效性现要求在 `src/main` 内存在带包名的 `public interface/class/record/enum` 契约；`package-info.java`、`src/test` 源和无包名的 public 声明均不构成可消费契约。
- 稳定调用方只接受其他 PMS 模块中非 `test` scope 且非 `optional` 的依赖；默认主作用域依赖通过。
- analytics/integration 的迁移归属固定为各模块自身的 `src/main/resources/db/migration/` 目录，不再依据根迁移文件名猜测领域。因此普通名称 `V010__create_sync_tables.sql` 仍会按其所在 integration 目录检查。
- 核心主数据所有权清单已明确为客户、联系人、项目、项目节点、项目 WBS、任务、设备/设备档案、资产、备件和 RMA；规则同时检查表名和典型 DO/Entity/Mapper/Repository 定义。

### 第二轮 TDD 证据

新增 package-info-only API 夹具后执行：

```powershell
& .\tests\infrastructure\verify-pms-module-boundaries-test.ps1
```

RED：旧实现输出 `Expected checker to reject fixture: pms-module-project-api must not be empty`，证明它错误接受 package-info-only API。

修复后 GREEN：

```powershell
& .\tests\infrastructure\verify-pms-module-boundaries-test.ps1
& .\tests\infrastructure\verify-pms-module-boundaries.ps1
```

结果：夹具与真实仓库检查均通过。夹具新增覆盖 package-info-only、src/test-only、无包名 public、test-scope-only、optional-only；合法 fixture 具有包名、public interface 和默认主作用域调用方。迁移夹具在 integration 专属目录以普通文件名创建允许的 `pms_integration_sync_batch`，并逐项拒绝上述核心主数据清单的全部表名。

## 第三轮复核修复：项目域完整映射与主实现扫描

### 发现与修复

- 根据 `module-boundary-and-naming.md` 的 PMS-PROJ 数据所有权，核心清单扩展为项目组合、项目、项目节点/层级、WBS、任务、里程碑、风险、问题、验收与闭环；并保持客户、联系人、设备/资产、备件、RMA。表名与类型名均精确映射，例如 `pms_project_portfolio` / `ProjectPortfolioDO`。
- API 顶层 public 声明现支持 Java 的 `final`、`abstract`、`sealed`、`non-sealed` class/interface 及 record/enum，也支持 Kotlin data/sealed/enum class/interface；仍要求包名和主作用域调用方。
- analytics/integration 所有权扫描限定为 `src/main/java`、`src/main/kotlin`、`src/main/resources/mapper` 与模块专属 `src/main/resources/db/migration`。`src/test`、test resources 和普通 XML 配置不参与扫描；主 Mapper、主实体和模块迁移继续受检查。
- 删除未使用的 `$domain` 变量。

### 第三轮 TDD 证据

夹具先加入 `src/test` 中的 `ProjectDO`/`ProjectMapper` 与普通 `application.xml`，旧检查在非持久化 `application.xml` 中错误命中 `pms_project`：

```text
pms-module-analytics must not own core master data: ...\\src\\main\\resources\\application.xml
```

修复后执行：

```powershell
& .\tests\infrastructure\verify-pms-module-boundaries-test.ps1
& .\tests\infrastructure\verify-pms-module-boundaries.ps1
```

结果：夹具与真实检查通过。数据驱动夹具逐项验证每个核心对象的模块迁移表名和主 Java DO 类型均被拒绝；另验证 `public final/abstract/sealed/non-sealed` Java 契约、record、enum 与 Kotlin data class 均可在默认主作用域调用下通过。
