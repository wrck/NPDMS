# 迁移适配问题修复与验收记录（第二轮）

> 仓库：`wrck/NPDMS`；分支：`migration-sync-research-f36p0S`。
> 日期：2026-09-17；CI 时间均为 UTC。
> 本轮起点：`959b66fb3ac4200a49d9fc612854182fab088d9f`。
> 本轮代码检查点：`e3c673a351209df801d44197f8dac412ef3d4c90`。

本报告承接 `lowcode-v2-migration-acceptance-20260917.md`。只更新本轮实际修复与验证的状态，不替代未完成的生产环境总验收，不修改需求、领域归属或历史表单配置。

## 一、验收结论

**本轮已修复项的功能与分层回归验收通过；全库静态质量及原始生产环境总验收仍为 NOT ACCEPTED（未通过），不得最终放行。**

同一代码检查点 `e3c673a3` 上，完整类型检查、生产构建、1025 项 Vitest、24 项 Node 测试、14 项渲染器与 20 项保存消费链浏览器场景均通过；17 项 OA/MySQL/Flowable 测试及 8 项既有低代码 H2 持久化测试通过。完整 ESLint 已由 11 个错误降为 0 个错误，保留 125 条告警。

全库 Stylelint 仍有 1683 条错误，Prettier 仍有 687 个差异文件；部署级端到端和真实 OA 集成未覆盖，详见第七节。没有通过取消规则、跳过失败断言或把测试替身描述为生产服务来放行。

## 二、实际修复

### 2.1 前端：`59e6852f6f082ac6d8deeeb09ef3b261bbaa9dfa`

| 问题 | 修复与回归约束 |
|---|---|
| 设计器预览模型使用 const reactive 双向绑定，引起编译告警 | 改为 ref 模型；预览前创建独立值快照。预览编辑不再共用 Schema 中的嵌套默认值，0、false、空字符串保留 |
| 重复复制同一字段导致多个控件使用同一个答案键 | 分配未使用的 `name_copy`、`name_copy2` 等 prop；新增字段也避开已加载的 id/prop；验证保存重开后的唯一性 |
| 注册中心多个自定义组件使用相同列表 key | key 优先使用 componentName，而不是所有自定义组件共用 custom 类型 |
| 预览提交结果将用户输入拼接为 HTML | 使用 Vue 文本节点展示 JSON；通过真实预览按钮验证带 HTML 标记的输入只显示为文字、不生成对应 DOM |
| 列表删除的 HTTP 200 业务失败被当作成功 | 同源低代码接口走统一请求封装，旧接口验证业务码；失败保留记录、不显示删除成功；同一删除操作进行中拦截重复点击 |
| 导出接口返回 JSON 错误却下载成 Excel | 检查 JSON Blob 错误并显示消息，不触发下载；没有其他工具栏按钮时也可显示已配置的导出入口 |
| 查询响应乱序：旧成功/旧失败覆盖新查询 | 查询序号保护成功、异常与 loading 收尾；配置变化和组件卸载时使旧请求失效；分别验证延迟成功和延迟失败 |
| 配置的 pageSize 被 props 默认值 20 覆盖 | 按显式 props、配置、默认值的顺序取值；浏览器验证配置 pageSize=1 的请求与实际记录数 |
| 编辑不存在的记录显示空表单，仍保留保存入口 | 数据返回 null 时转为不存在状态，不允许保存；V1/V2 均验证 |
| 完整 ESLint 的 11 个错误 | 修复非 void 标签的自闭合写法，以及 readonly 局部变量与自动导入的冲突；同步内部绑定断言，不更改只读业务契约 |

新增 `designerState.spec.ts` 四项单测。保存消费链在原有八项上新增十二项 Chromium 场景，总计二十项，原有十四项渲染器场景保留。权限服务异常不读取配置/数据的负向场景也加入回归；该行为是本次新增证据，不重复算作本轮新实现。

### 2.2 OA 日志与持久化：`e00a6123ed30dd749ee90f0a263d4a91818a514d`

| 已复现问题 | 修改 |
|---|---|
| 成功重试仍保留旧 error_message / next_retry_time | Mapper 对明确的 null 执行 SQL 清除，不依赖默认非空字段更新策略 |
| 达到最大重试次数后旧重试时间仍留在库中 | markFailed 使用相同的显式清除路径 |
| 成功响应明确为空时仍保留旧 response_body | 明确清空响应列，避免历史内容伪装为本次响应 |
| HTTP 302 等非 2xx 先被记为 SUCCESS，再向调用方报告失败 | 在 markSuccess 前验证 HTTP 状态；非 2xx 进入既有异常/失败日志路径 |

只修改 `IntegrationLogMapper.java`、`IntegrationLogServiceImpl.java`、`OaIntegrationServiceImpl.java` 三个生产源码文件；不修改全局字段更新策略、数据库迁移、重试次数规则或外部请求结构。

## 三、失败复现与修复后复验

### 3.1 MySQL 先失败、再通过

修复前代码 `98999cf66cf600156b6f8a6ab8cd8b3fb65a3fa6`，run `35200704549`，job `105134490093`，产物 `10487632646`。

七项新增数据库测试实际执行，四项断言失败、三项通过，零执行错误、零跳过。另有十项原有监听器/端口测试通过。四项失败分别为：成功重试后错误信息不为空、重试耗尽后时间不为空、非 2xx 日志为 SUCCESS、显式空响应未清除旧响应。

数据库元数据确认为 **MySQL 8.4.11**。测试使用仓库 V284 迁移中原有 int_log DDL、实际 MyBatis Mapper、实际 Spring 事务代理和本机 HTTP 端点，不是内存 Map 模拟持久化。

首次修复后 run `35200957124` 在生产修复提交 `e00a6123` 上：七项新增测试和十项原有测试全部通过。保持测试断言不变，仅改变三个生产源码文件。

最终检查点 `e3c673a3` 的 run `35201897413` / job `105138378498` 再次通过；产物 `10487739635`。结果为 `OaDeliveryPersistenceTest` 7/7、`OaTodoPortAdapterTest` 5/5、`OaTaskListenerTest` 5/5，合计 **17/17，零失败、零执行错误、零跳过**。数据库仍为 MySQL 8.4.11。

产物 SHA-256：`7eeb58ab96e4539659b3c2d14c2b730b9a7007811b60a064ed183e91c44d9dfd`。

### 3.2 同一最终代码检查点的前端验收

最终代码检查点 `e3c673a351209df801d44197f8dac412ef3d4c90`：run `35201897575` / job `105138411310`，工作流所有步骤完成且结论 SUCCESS；产物 `10488009830`。已下载并核对产物中的 source-commit、JUnit、Node 日志、浏览器 JSON、类型检查及构建日志，而非只依据任务绿色状态。

| 检查 | 实际结果 |
|---|---|
| 新辅助代码与本轮修改断言的 Prettier 检查 | 通过；独立全库格式检查仍未通过 |
| 全量 src ESLint | 0 错误、125 告警 |
| 完整 `pnpm run ts:check` | 通过，未发现 TypeScript 错误 |
| 完整 `pnpm run build:prod` | 通过；本轮处理的 previewData const reactive 编译告警已消失，不宣称所有构建告警清零 |
| Vitest 不加路径过滤 | 168 个测试文件、1025 项用例全部通过，零失败/错误/跳过 |
| 五组 Node 原生前端测试 | 24/24，零失败/取消/跳过 |
| 原有渲染器 Chromium 场景 | 14/14，所有场景通过 |
| 保存消费链及新增失败场景 | 20/20，所有场景通过 |

产物 SHA-256：`0c5532150a3388ae0f9e750e75eb690f1a367f1e9bee3c3f9870f16fd838bdd1`。

同一检查点的低代码消费链工作流 run `35201897451` / job `105138464349` 亦全部通过；产物 `10488845356`。其实际控制器、方法安全代理与 JDBC 用例 `DynamicEntityDataServiceTest` 5/5、`DynamicEntityConsumerTest` 3/3，共 8/8，零失败/错误/跳过，数据库为 H2，不混称为 MySQL。该工作流重复执行的 90 项定向单测、14/20 浏览器场景不再次计入总数。

低代码产物 SHA-256：`60258e9252dde5d643cb7a600e250291c7f6e28d741b6c080b839786dbb15b8a`。

### 3.3 完整静态质量检查

同一最终检查点 `e3c673a3`，run `35201897419` / job `105138378685`，产物 `10488750120`。

| 检查 | 本轮起点诊断 | 最终诊断 | 判定 |
|---|---|---|---|
| ESLint | 11 错误、125 告警 | **0 错误、125 告警** | 当前 ESLint 命令通过，但不是零告警 |
| Stylelint | 167 个文件、1683 条错误 | **167 个文件、1683 条错误** | 未通过 |
| Prettier | 687 个文件有差异 | **687 个文件有差异** | 未通过 |

本轮中间版本曾新增两处测试文件格式差异，均已修正；最终与起点相比没有新增格式差异文件。新 helper 与本轮修改断言另有严格格式检查，不替代全库 Prettier 失败结果。

Stylelint 主要规则包括空行 626、属性顺序 613、单行声明数量 240；还包含需要逐项核查的其他取值/语法规则。本报告不将全部静态诊断一概视为无害。没有关闭规则、缩小全库扫描范围或通过忽略退出码制造全绿。

完整原始报告和按文件、行号、规则归一化的 `remaining-quality-findings-e3c673a3.json` 留存在证据包中。静态产物 SHA-256：`48470b233b389668e64409ceb90e8d8bdff6baee3512d5a96ba5bbae4fa9e588`。

## 四、测试证明与不能证明的边界

**浏览器层。** 真实 Vue、Element Plus、FormCreate、设计器、运行页、列表、路由、请求和认证代码已参与测试。服务端使用场景内保留状态的 HTTP 契约测试服务；原有注册表和用户展示边界仍按测试夹具隔离。不能称为浏览器连接部署中 Spring/MySQL 的整站端到端验收。

**数据库层。** 新增七项用例通过真实 MySQL、MyBatis、Spring 事务和 RestTemplate 执行。OAuthTokenCache 是测试替身；远端 OA 是本机 HTTP 服务。没有验证真实 OA 凭据、Redis 令牌刷新、Resilience4j 完整装配、服务端全局安全过滤器或租户隔离。

**工作流层。** 真实 Flowable 在 MySQL 上部署测试专用 BPMN，执行创建→审批退回路径→重新处理→再次审批→完成，检查变量回填与历史结束记录。实际 OaTaskListener 与端口代理参与；OA 连续返回 503 时流程仍完成，六条失败日志可查询。该验证不等于全部项目生产流程、审批权限和发布版本矩阵验收。

**事务层。** 外层业务事务回滚后，独立事务的 OA 失败日志仍存在。不能据此承诺数据库自身不可用时也能落库，也没有声称实现 exactly-once 投递。

**兼容性。** 本轮没有更改 LowCode V1 渲染器实现、应用依赖及锁文件、PRD、历史表单配置或数据库迁移。未自动切换全部配置到 V2，未合入 master，未发布生产环境。部署级最终放行仍须补齐完整证据。

## 五、关键提交与验证过程

| 提交 | 内容 |
|---|---|
| `e1a4f923` | 建立完整静态诊断基线 |
| `59e6852f` | 前端实际修复和新增回归，经全部功能步骤验证后提交 |
| `00324d45` | 新增 MySQL/Flowable/OA 验收用例及隔离工作流 |
| `46048bf1` | 只读最终提交复验工作流；修正临时 MySQL 端口上下文及 Stylelint 产物输出 |
| `98999cf6` | 纠正新验收代码中的构造器 Supplier 重载歧义，使数据库测试可执行 |
| `e00a6123` | 修复 MySQL 已复现的四项结果持久化/HTTP 状态问题 |
| `3746c170` / `e3c673a3` | 收敛本轮测试格式差异，补充隔离数据库操作说明与先失败后通过证据；生产源码与断言含义不变 |

首次前端修复验证 run `35198662568` 未通过：本轮机械标签编辑误改了一处微流程模板，且一处运行时测试仍引用旧内部变量名。两处均已纠正；未通过降低规则或跳过断言放行。纠正后 run `35199455758` 全部功能步骤通过，产物 `10487645629` 指向正式前端提交 `59e6852f`。本轮一次性交付脚本已随该提交移除。

首次 OA 工作流存在端口上下文配置错误，新测试还出现两处 Supplier 编译错误；它们属于本轮验收设施问题，不作为生产缺陷的复现证据。真正的四项持久化复现以 run `35200704549` 的七项实际执行结果为准。

## 六、复现方式

前端工作目录：`yudao-ui/yudao-ui-admin-vue3`。验收 CI 使用 Node 22、pnpm 9.15.5 和冻结锁文件，不改变开发环境基线。

```bash
pnpm install --frozen-lockfile
pnpm exec eslint ./src
pnpm run ts:check
pnpm exec vitest run --config vitest.pms-file.config.ts
node --experimental-strip-types --test tests/theme-init.test.mjs \
  src/components/PmsLocationSelector/locationSelector.spec.ts \
  src/views/pms/asset/location/location-contract.spec.ts \
  src/views/pms/engineering/installation/installationForm.spec.ts \
  src/views/pms/project/projects/index.spec.ts
pnpm run build:prod
pnpm exec stylelint './src/**/*.{vue,less,postcss,css,scss}'
pnpm exec prettier --check 'src/**/*.{js,ts,json,tsx,css,less,scss,vue,html,md}'
```

浏览器工具独立安装，不修改应用 package.json。将 PLAYWRIGHT_PACKAGE_ROOT 指向安装了 playwright@1.58.2 的工具目录，并安装对应 Chromium 后执行：

```bash
node tests/lowcode/browser/run.mjs
node tests/lowcode/consumers/run.mjs
```

后端固定使用 JDK 25。优先通过 `.github/workflows/oa-mysql-acceptance.yml` 的独立 CI 环境复验。手工执行必须显式提供专用环回 MySQL 数据库 URL、用户和密码，且数据库名必须为 `npdms_migration_acceptance`。测试会删除并重建该专用库的 int_log，禁止指向开发/共享/生产库。没有环境变量时本地默认跳过；验收工作流设置 require-mysql，并强制检查所有七项执行、零跳过，缺失环境不能被算为通过。

```bash
mvn -B -ntp -pl pms-module-integration -am \
  -Dtest=OaTaskListenerTest,OaTodoPortAdapterTest,OaDeliveryPersistenceTest \
  -Dnpdms.oa.require-mysql=true \
  -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test
```

## 七、未关闭项

| 未关闭项 | 当前状态与关闭条件 |
|---|---|
| 全库 Stylelint / Prettier | 167 个文件的 1683 条 Stylelint 错误及 687 个文件的格式差异尚未整改；需分批修复、审查差异并再次执行全量检查和回归 |
| ESLint 零告警目标 | 尚有 125 条告警，需要按实际规则逐项处理；当前命令的通过不能当作零告警 |
| 部署级整站端到端 | 尚未执行浏览器→部署后端→MySQL 的完整业务链和实际权限/跨租户负向矩阵 |
| 全部低代码字段与注册组件 | 当前新增/保留场景不是全部真实业务组件、响应式布局、复杂嵌套、动态 Schema 与全部保存组合的穷尽矩阵 |
| 真实 OA 与生产工作流 | OAuth、Redis、Resilience4j、真实远端协议和项目实际流程的启动/审批/退回/重试仍需部署环境证据；测试专用 BPMN 和本机 HTTP 服务不代替这些验收 |

这些未关闭项继续保留，不得把本轮功能回归通过改写为“全部问题修复”“全部流水线全绿”或“V2 已可在所有生产链替代 V1”。真实外部接口未接通也不意味着平台内部功能不能继续开发。
