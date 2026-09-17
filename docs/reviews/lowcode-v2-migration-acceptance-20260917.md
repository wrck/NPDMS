# LowCode V2 与 workflow / integration 迁移适配验收记录

## 当前复验：前端错误收敛与指定保存消费链

- 日期：2026-09-17（CI 时间为 UTC）
- 仓库与分支：`wrck/NPDMS` / `migration-sync-research-f36p0S`
- 代码检查点：`006e17e0b42398c5a41abeedd327e5cca1306b92`
- 本次范围：完整前端类型检查、生产构建、全量现有前端测试，以及设计器、正式运行页、列表新增/编辑/详情保存链。
- 结论：本次前端收敛与指定消费链的分层回归通过；不等同于原始“V1 无回归 + V2 全链路可替换”的全部生产验收。

### 同一代码检查点的验证证据

| 检查 | 结果 | GitHub Actions 证据 |
|---|---|---|
| 完整 `pnpm run ts:check` | 通过，TypeScript diagnostic count: 0 | run `35189876851` / job `105099920108`；产物 `10483233666` |
| 完整 `pnpm run build:prod` | 通过 | run `35189876851` / job `105099920354` |
| 全量 Vitest，不加路径筛选 | 167 个文件、1021 项用例全部通过 | run `35189876829` / job `105099881377`；产物 `10482973915` |
| 五组 Node 原生前端测试 | 24/24 通过，0 跳过 | 同上，`frontend-native.log` |
| V1/V2 渲染器 Chromium 回归 | 14/14 通过 | run `35189877111` 的 Real renderer browser regression |
| 指定保存消费链 Chromium 回归 | 8/8 通过 | 同一 run 的 Saved consumer browser regression |
| 低代码控制器权限及 JDBC 持久化 | 8/8 通过，0 失败/错误/跳过（H2） | run `35189877111` / job `105099939935`；产物 `10484062119` |

47 项渲染器定向单测也通过；它们已包含在 1021 项全量 Vitest 内，不能重复相加。类型检查使用项目实际 Vite 插件生成声明后执行完整 vue-tsc，未通过缩小检查范围或放宽编译选项制造通过。构建通过不表示告警为零；保存链浏览器构建仍提示 previewData 的 const reactive 绑定被编译器转为 let，此为未阻断执行的告警。本次未运行独立的全库 ESLint、Stylelint 和 Prettier 检查。

扩大测试范围的首次结果为 Vitest 969 通过/35 失败、Node 23 通过/1 失败。修复了三个测试文件缺少隔离浏览器状态、旧认证协议替身，以及四处失效/过宽的源码断言；保留原有用例并增加 17 项测试。进一步修复了远程组件地址把伪装 localhost 误当本机的真实缺陷，以及新 Token 与旧权限混用、权限回填失败残留登录态的真实缺陷。

### 保存链的实际改动

设计器：校验和保存锁；发布前持久化当前编辑；新建成功后固定 ID 并更新地址，后续保存执行更新而不是重复新建；保留 rendererVersion、entityCode 等配置；验证 V1/V2 保存、重开、预览和发布。

正式运行页：通过 Facade 的校验/submit 契约保存；复用统一 HTTP 与当前项目认证/租户头；HTTP 200 中的业务失败仍按失败处理；校验与保存锁防止重复请求；监听类型、编码、mode 和 id，阻止旧异步响应覆盖新页面；缺少编辑 ID 不允许写入，详情保持只读。

列表消费方：新增、编辑、详情继续走统一路由；编码和 ID 安全编码；表单显式 entityCode 优先；保留原有增量回填，并将清空值映射为 null，避免编辑清空后重开恢复旧数据。

持久化配套：DynamicEntityController 适配当前 `ss.hasPermission`；新增主键通过同一连接的 KeyHolder 读取；更新允许明确的 null 清除，同时保留元数据字段白名单与 ID 保护。

### 本分支主要提交

| 提交 | 内容 |
|---|---|
| `f8d27434` | 已有类型修复增量，本次基于该最新状态复验并继续收敛 |
| `e139e600` | 独立完整类型检查与生产构建 CI |
| `dc10865e` | 经验证交付的设计器、运行页、列表请求及持久化配套源码 |
| `4955f326` | 排除低代码 DictSelect 的全局自动注册重名，保留运行时注册名称 |
| `31329e5c` | 全量前端回归与当前/旧 Token、租户头兼容测试 |
| `c587bd91` | 消费链 CI 改为只读复验实际提交，不再重放临时补丁或自动提交源码 |
| `eade448e` | 保留完整诊断，并逐项汇总失败而不是被大段源码差异淹没 |
| `e4dfbabe` | 隔离浏览器状态测试环境；强制执行生产 HTTP 拒绝断言 |
| `4604a989` | 主表/选择弹窗列独立断言；共享匹配、服务端动作与确认工勘只读约束 |
| `a9519fa0` | 按解析后的协议/主机名校验远程组件 URL；新增 14 项边界测试 |
| `006e17e0` | 登录权限回填失败清理；认证测试适配 accessToken/getPermissionInfo |

这些提交为分支的实际提交记录，未合入 master，未做生产发布或生产数据变更。下方首轮历史报告中的“V1 文件完全未修改”仅适用于 c919a40c 检查点；后续 f8d27434 存在一处等价模板条件调整，不能再声称当前 V1 与首轮字节完全一致。V1 没有被 FormCreate 替换；缺省配置继续使用 V1，显式 v2 才选择新实现。

### 证据边界与仍未宣称完成的事项

保存链浏览器测试使用真实业务页面、Vue、Element Plus、FormCreate、前端路由和实际请求/认证代码；服务端响应由 HTTP 契约夹具提供，并在场景内保留数据用于重开验证，不是部署中的 Spring/MySQL 服务。后端测试单独执行实际控制器、Spring 方法权限代理及 JDBC；数据库为隔离 H2，权限/元数据提供方使用测试替身。两层测试不能拼称为同一次“浏览器→生产后端→MySQL”的端到端验收。

原始总目标仍需实际部署环境的端到端验证、全部业务注册组件/字段组合与复杂嵌套消费链矩阵，以及 workflow/integration 的流程操作、OA 投递和日志重试持久化验证。本次不覆盖这些边界，也不把外部接口未接通扩大为内部功能不能开发。

### 复现命令

前端目录 `yudao-ui/yudao-ui-admin-vue3`：

```bash
pnpm install --frozen-lockfile
pnpm run ts:check
pnpm run build:prod
pnpm exec vitest run --config vitest.pms-file.config.ts
node --experimental-strip-types --test tests/theme-init.test.mjs src/components/PmsLocationSelector/locationSelector.spec.ts src/views/pms/asset/location/location-contract.spec.ts src/views/pms/engineering/installation/installationForm.spec.ts src/views/pms/project/projects/index.spec.ts
# PLAYWRIGHT_PACKAGE_ROOT 指向独立安装 playwright@1.58.2、已安装 Chromium 的目录
node tests/lowcode/browser/run.mjs
node tests/lowcode/consumers/run.mjs
```

仓库根目录（JDK 25）：

```bash
mvn -B -ntp -pl pms-module-lowcode -am -Dtest=DynamicEntityDataServiceTest,DynamicEntityConsumerTest -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test
```

---

<details>
<summary>首轮历史记录：c919a40c（保留当时的失败与待验收事实，不代表当前前端状态）</summary>

# LowCode V2 与 workflow / integration 迁移适配验收记录

- 日期：2026-09-17
- 仓库：wrck/NPDMS
- 分支：migration-sync-research-f36p0S
- 本轮起点：9ffc7f2e322477291b628e0934c754019467b721
- 代码检查点：c919a40c64314327436e3817177bf267f1320b23
- 验收标准：V1 无回归 + V2 全链路可替换
- 结论：**NOT ACCEPTED / 不得作为最终验收通过记录**。

本报告只记录当前用户指定的兼容性整改与实际证据，不修改 PRD、领域归属或业务功能范围。

## 已提交内容

| 顺序 | Commit | 内容 |
|---|---|---|
| 1 | e431af78 | 修复 V1 结构门禁的注释误报；不修改 V1 |
| 2 | 133635f6 | Facade 版本切换保留内部编辑值，外部回填保持增量语义 |
| 3 | fa6325c9 | V2 字段适配、实例局部组件、可变规则、上传兼容、布局状态 |
| 4 | 0d93821b | 前端契约测试与完整类型检查 CI |
| 5 | ca671e0b | 测试只隔离 HTTP / 下载边界，避免 API 导入启动路由 |
| 6 | 032c72b7 | OA 可选 SPI、业务键一致性、独立事务边界及十项测试 |
| 7 | 90fe81a6 | V2 校验规则及布局/API 类型边界整理 |
| 8 | 3e385441 | 14 项真实 Chromium 渲染器与嵌套消费方测试 |
| 9 | c919a40c | 修复浏览器测试发现的 V2 重置覆盖及测试定位问题 |

V1 原文件 `yudao-ui/yudao-ui-admin-vue3/src/components/LowCodeFormRenderer/index.vue` 未改动，Git blob 为 `f2c4e06c76cc9c72c76fb0840176406646f26d06`。源码不变不等于所有消费链运行行为已经无回归；Facade 改动仍需消费链验证。

## 验证证据

### 前端单元 / 结构 / Vue 状态生命周期

在代码检查点 90fe81a6 的 GitHub Actions run `35174422213`，job `105052863331`：6 套测试共 47 项通过。分布为字段规则 21、运行语义 5、Facade 生命周期 5、Schema 兼容 4、结构门禁 6、版本路由 6。

同一工作流随后执行完整 `vue-tsc` 失败。没有关闭或跳过该门禁。诊断涉及 V1、Facade/V2 及其他工程代码；未完成与起点的逐项差异归因，不得把所有错误都标记为“历史问题”。单元测试通过不等于类型检查通过。

c919a40c 的复测 run `35175184497` / job `105055228059` 亦确认契约测试步骤通过、完整类型检查步骤失败。

### 后端

代码检查点 032c72b7 的 run `35173859729`，job `105051127251`：24 个关联 Maven 模块构建成功；OaTaskListenerTest 5 项及 OaTodoPortAdapterTest 5 项通过。

测试覆盖可选适配器缺席、创建/完成业务键一致、七项 DTO 映射、服务返回 false、事务属性、受控异常后的代理提交，以及提交异常向外暴露。事务用例使用真实 Spring AOP 代理与模拟事务管理器，**不是数据库持久化或真实 OA 投递验收**。

### 浏览器

首次 run `35174858342` / job `105054184771` 在 3e385441：10/14 通过。失败发现了 V2 重置覆盖默认值，以及上传按钮和自定义组件的测试定位问题。c919a40c 已修复并触发复测。

复测 run `35175184518` / job `105055227998` 在代码检查点 c919a40c：**14/14 场景通过**，无测试捕获的运行时或 console.error 错误；结果产物 ID `10477474260`。复测未放宽任何字段数据、提交、校验和隔离断言。

浏览器测试使用项目真实 Vue、Element Plus、FormCreate、V1、V2、Facade、TabRenderer、RelatedPageRenderer；HTTP、下载、用户 Store 与内置组件注册表为明确的测试边界。自定义组件以两份同名注册项验证隔离。不能据此声称所有真实业务组件、权限、保存接口或外部系统已经通过。

## 消费链覆盖与待验收

| 链路 | 当前证据 | 仍需补齐 |
|---|---|---|
| V1 / V2 / Facade | 单元与浏览器契约测试；版本往返、校验、提交、重置、回填、禁用 | 全部字段类型、响应式布局、动态 Schema、真实业务注册组件的浏览器矩阵 |
| 设计器预览 | Facade 入口的结构门禁 | 完整设计器加载、编辑、保存、重开及 V1/V2 预览 |
| 正式低代码运行页 | Facade submit-before-save 的结构门禁 | 真实接口回填、校验失败禁止保存、保存成功、重进回显、错误提示 |
| 标签页嵌套表单 | 真实 TabRenderer + previewConfigs + 参数表达式浏览器验证 | 正式配置接口、动态 context 更新、权限、父子表单提交行为 |
| 关联区块嵌套表单 | 真实 RelatedPageRenderer + previewConfigs + 参数表达式浏览器验证 | 正式配置接口、关联业务数据及保存行为 |
| 列表进入表单 | 无直接耦合具体版本的结构门禁 | 新增 / 编辑 / 详情的完整路由和持久化链 |
| workflow / integration | SPI 与监听器定向单测，关联模块编译 | 当前项目的流程启动、任务审批、退回、变量回填、OA 真实投递和日志/重试数据库行为 |

## 运行命令

前端工作目录：`yudao-ui/yudao-ui-admin-vue3`。

```bash
pnpm install --frozen-lockfile
pnpm exec vitest run --config vitest.pms-file.config.ts src/components/LowCodeFormRenderer src/components/LowCodeFormRendererFacade src/components/LowCodeFormRendererV2
pnpm run ts:check
```

浏览器依赖由 `.github/workflows/lowcode-browser-compat.yml` 安装到独立目录，不修改应用 package.json / pnpm-lock.yaml。手工复现时，将 `PLAYWRIGHT_PACKAGE_ROOT` 指向安装了 `playwright@1.58.2` 的目录，在安装 Chromium 后执行：

```bash
node tests/lowcode/browser/run.mjs
```

JSON 结果及失败截图/trace 在 `artifacts/lowcode-browser/`；工作流使用 always 上传。后端在仓库根目录：

```bash
mvn -B -ntp -pl pms-module-workflow,pms-module-integration -am -Dtest=OaTaskListenerTest,OaTodoPortAdapterTest -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test
```

## 切换与回退边界

保持历史 FormConfig；未指定 V2 时沿用 V1。统一由 LowCodeFormRendererFacade 根据 rendererVersion 选择实现。显式 `rendererVersion: 'v2'` 才启用 V2；撤销版本覆盖或设回 v1 可回到 V1。Facade 保留数据快照，不能替代后端事务、业务幂等和持久化兼容验证。

不批量迁移或重写历史表单配置，不替换 V1 原文件，不修改原上传接口/鉴权，不把外部集成失败伪装成已成功落库。

## 最终放行条件

当前仍不满足完整验收。放行前必须收敛完整类型检查，并补齐上述真实消费链矩阵与 workflow / integration 应用级验证；每个剩余整改步骤继续独立提交。不得仅以 47 个测试通过或部分浏览器场景通过宣布“V1 无回归 + V2 全链路可替换”。

</details>
